#!/usr/bin/env python3
"""V-Tempe quality gate.

Profiles (each includes the checks of the previous one where it says so):
  quick     static checks of the current change: diff hygiene, secrets in changed files,
            architecture boundaries, build hygiene, Claude config, gate self-tests. Seconds.
  standard  quick + compile and unit-test the Gradle modules the change affects (and their
            dependents). The default before reporting a code change as done.
  full      whole repository: all unit tests + debug APK, Android Lint, secrets in every file,
            OSV vulnerability lookup of direct and resolved dependencies, optional tools.
  ci        the static part of `full` for CI (Gradle runs as separate workflow steps).

Exit codes: 0 PASS, 1 FAIL, 2 usage error, 3 INCOMPLETE (a required check did not run).
Results: printed table + .claude/state/quality-gate/last.json (read by the Stop hook).

Usage:
  python scripts/quality/quality_gate.py --profile standard [--base main]
         [--skip CHECK_ID --skip-reason "why"] [--list]
"""
from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from qg import arch, configcheck, deps, gitutil, gradle, modules, osv  # noqa: E402
from qg import report as rp  # noqa: E402
from qg import secrets as sec  # noqa: E402
from qg.report import FAIL, NOT_APPLICABLE, NOT_CONFIGURED, NOT_RUN, PASS, CheckResult  # noqa: E402

PROFILES = ("quick", "standard", "full", "ci")
CONFLICT_RE = re.compile(r"^(<<<<<<< |>>>>>>> |=======$)", re.MULTILINE)
MAX_NEW_FILE_BYTES = 2 * 1024 * 1024


class Context:
    def __init__(self, root: Path, profile: str, base: str):
        self.root = root
        self.profile = profile
        self.base_ref = base
        self.base_commit = gitutil.resolve_base(root, base)
        self.changes = gitutil.changed_files(root, self.base_commit)
        self.paths = [c.path for c in self.changes if not c.deleted]
        self.all_paths = [c.path for c in self.changes]
        self.log_dir = rp.state_dir(root)

    @property
    def whole_tree(self) -> bool:
        return self.profile in ("full", "ci")


def _timed(fn):
    def wrapper(ctx: Context) -> CheckResult:
        start = time.time()
        result = fn(ctx)
        result.duration_s = time.time() - start
        return result
    wrapper.__name__ = fn.__name__
    return wrapper


@_timed
def check_diff_hygiene(ctx: Context) -> CheckResult:
    r = CheckResult("diff.hygiene", "Whitespace errors, conflict markers, forbidden and oversized files", PASS)
    if not ctx.changes and not ctx.whole_tree:
        r.status = NOT_APPLICABLE
        r.details.append("no changes against base")
        return r
    proc = subprocess.run(["git", "-c", "core.quotepath=off", "diff", "--check", ctx.base_commit],
                          cwd=ctx.root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    r.command = f"git diff --check {ctx.base_ref}"
    r.exit_code = proc.returncode
    if proc.returncode != 0:
        out = proc.stdout.decode("utf-8", "replace").strip().splitlines()
        r.status = FAIL
        r.details += out[:40]
    for c in ctx.changes:
        if c.deleted:
            continue
        p = ctx.root / c.path
        if sec.is_forbidden_file(c.path):
            r.status = FAIL
            r.details.append(f"{c.path}: file type must never be committed (secret/keystore/config)")
        try:
            size = p.stat().st_size
        except OSError:
            continue
        if c.status == "A" and size > MAX_NEW_FILE_BYTES:
            r.status = FAIL
            r.details.append(f"{c.path}: staged new file of {size // 1024} KiB (> {MAX_NEW_FILE_BYTES // 1024} KiB)")
        elif c.status == "?" and size > MAX_NEW_FILE_BYTES:
            r.details.append(f"advisory: untracked {c.path} is {size // 1024} KiB; do not commit it without a reason")
        if c.status == "?" and size < MAX_NEW_FILE_BYTES and gitutil.is_probably_text(p):
            text = p.read_text(encoding="utf-8", errors="replace")
            if CONFLICT_RE.search(text):
                r.status = FAIL
                r.details.append(f"{c.path}: merge conflict marker")
    return r


def _scan_files(root: Path, rel_paths: list[str]) -> tuple[list[sec.Finding], int]:
    allow = sec.load_allowlist(root)
    findings: list[sec.Finding] = []
    scanned = 0
    for rel in rel_paths:
        if sec.path_allowed(rel, allow):
            continue
        p = root / rel
        try:
            if p.stat().st_size > MAX_NEW_FILE_BYTES or not gitutil.is_probably_text(p):
                continue
        except OSError:
            continue
        scanned += 1
        findings += sec.scan_text(p.read_text(encoding="utf-8", errors="replace"), rel)
    return findings, scanned


@_timed
def check_secrets(ctx: Context) -> CheckResult:
    if ctx.whole_tree:
        r = CheckResult("secrets.tree", "Secret patterns in every tracked and untracked file", PASS)
        targets = sorted(set(gitutil.tracked_files(ctx.root)) | set(gitutil.untracked_files(ctx.root)))
    else:
        r = CheckResult("secrets.changed", "Secret patterns in changed files and added lines", PASS)
        targets = ctx.paths
        if not targets:
            r.status = NOT_APPLICABLE
            r.details.append("no changed files")
            return r
    findings, scanned = _scan_files(ctx.root, targets)
    for rel in targets:
        if sec.is_forbidden_file(rel) and (ctx.root / rel).exists():
            findings.append(sec.Finding(rel, 0, "forbidden-file", "file type must not be in the repo", ""))
    r.details.append(f"scanned {scanned} text files")
    if findings:
        r.status = FAIL
        r.details += [f.render() for f in findings]
    gitleaks = shutil.which("gitleaks")
    r.details.append("gitleaks: " + ("available, run in tools.gitleaks" if gitleaks else "not installed (pattern scan only)"))
    return r


@_timed
def check_architecture(ctx: Context) -> CheckResult:
    r = CheckResult("arch.boundaries", "Module graph and layer import rules (new violations fail)", PASS)
    new, known, stale = arch.run(ctx.root)
    if new:
        r.status = FAIL
        r.details += [f"NEW {v.key()}" for v in new]
    if known:
        r.details.append(f"{len(known)} known violation(s) tracked in scripts/quality/arch-baseline.txt")
    for s in stale:
        r.details.append(f"baseline entry no longer violated (remove it): {s}")
    sized = arch.oversized_kotlin(ctx.root, ctx.paths)
    r.details += [f"advisory: {s}" for s in sized]
    return r


@_timed
def check_build_hygiene(ctx: Context) -> CheckResult:
    r = CheckResult("deps.hygiene", "Version catalog, repositories, wrapper checksum", PASS)
    rep = deps.run(ctx.root)
    if rep.errors:
        r.status = FAIL
        r.details += rep.errors
    r.details += [f"warning: {w}" for w in rep.warnings]
    r.details.append(f"{len(rep.coordinates)} pinned coordinates, {len(rep.unversioned)} BOM-managed")
    return r


@_timed
def check_config(ctx: Context) -> CheckResult:
    r = CheckResult("config.claude", "Claude agents/skills/rules/settings/hooks, CLAUDE.md", PASS)
    rep = configcheck.run(ctx.root)
    if rep.errors:
        r.status = FAIL
        r.details += rep.errors
    r.details.append(f"{len(rep.agents)} agents, {len(rep.skills)} skills validated")
    r.details += rep.notes
    return r


@_timed
def check_workflows(ctx: Context) -> CheckResult:
    r = CheckResult("config.workflows", "GitHub workflow YAML parses and has jobs", PASS)
    changed = any(p.startswith(".github/workflows/") for p in ctx.paths)
    if not changed and not ctx.whole_tree:
        r.status = NOT_APPLICABLE
        r.details.append("no workflow changes")
        return r
    errors, parsed = configcheck.check_workflows(ctx.root)
    if not parsed:
        r.status = NOT_RUN
        r.details.append("PyYAML is not installed here; CI (quality-gate.yml) validates the workflows")
        return r
    if errors:
        r.status = FAIL
        r.details += errors
    return r


@_timed
def check_selftest(ctx: Context) -> CheckResult:
    r = CheckResult("python.selftest", "Self-tests of the gate and hooks (unittest)", PASS)
    touched = any(p.startswith(("scripts/quality/", ".claude/hooks/")) for p in ctx.all_paths)
    if not touched and not ctx.whole_tree:
        r.status = NOT_APPLICABLE
        r.details.append("gate/hook code unchanged")
        return r
    cmd = [sys.executable, "-B", "-m", "unittest", "discover", "-s", "scripts/quality/tests", "-t", "scripts/quality"]
    r.command = " ".join(["python", *cmd[1:]])
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE="1")
    proc = subprocess.run(cmd, cwd=ctx.root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env)
    r.exit_code = proc.returncode
    out = proc.stdout.decode("utf-8", "replace").strip().splitlines()
    summary = [ln for ln in out if re.match(r"^(Ran \d+ tests|OK|FAILED)", ln)]
    r.details += summary
    if proc.returncode != 0:
        r.status = FAIL
        r.details += [ln for ln in out if ln.startswith(("FAIL:", "ERROR:"))][:20]
    return r


def _gradle_check(ctx: Context, check_id: str, title: str, tasks: list[str], label: str) -> CheckResult:
    r = CheckResult(check_id, title, PASS)
    r.command = "gradlew --continue " + " ".join(tasks)
    res = gradle.run_tasks(ctx.root, tasks, ctx.log_dir, label)
    r.exit_code = res.exit_code
    r.duration_s = res.duration_s
    r.details.append(res.summary())
    if res.note:
        r.details.append(res.note)
    r.details.append(f"log: {res.log_path.relative_to(ctx.root).as_posix()}")
    if res.exit_code is None:
        r.status = NOT_RUN
    elif res.exit_code != 0 or res.failures or res.errors:
        r.status = FAIL
        r.details += [f"failed test: {t}" for t in (res.failed_tests or [])[:20]]
        r.details += gradle.log_tail(res.log_path, 20)
    return r


def check_gradle_affected(ctx: Context) -> CheckResult:
    affected = modules.affected_modules(ctx.all_paths)
    tasks = [t for m in sorted(affected) for t in modules.STANDARD_TASKS.get(m, [])]
    if not tasks:
        r = CheckResult("gradle.affected", "Compile + unit tests of affected modules", NOT_APPLICABLE)
        r.details.append("no Gradle module affected by the change")
        return r
    r = _gradle_check(ctx, "gradle.affected", "Compile + unit tests of affected modules", tasks, "affected")
    r.details.insert(0, f"affected modules: {', '.join(sorted(affected))}")
    if os.environ.get("VTEMPE_PG_TEST_URL") is None and "server" in affected:
        r.details.append("PostgresIntegrationTest skipped (VTEMPE_PG_TEST_URL not set) - DB paths not exercised")
    return r


def check_gradle_full(ctx: Context) -> CheckResult:
    r = _gradle_check(ctx, "gradle.all", "All unit tests, commonMain metadata, debug APK", modules.FULL_TASKS, "full")
    if os.environ.get("VTEMPE_PG_TEST_URL") is None:
        r.details.append("PostgresIntegrationTest skipped (VTEMPE_PG_TEST_URL not set)")
    return r


def check_android_lint(ctx: Context) -> CheckResult:
    return _gradle_check(ctx, "android.lint", "Android Lint (app-android debug)", [":app-android:lintDebug"], "lint")


def check_ios(ctx: Context) -> CheckResult:
    affected = modules.affected_modules(ctx.all_paths) if not ctx.whole_tree else set(modules.MODULES)
    r = CheckResult("ios.compile", "iOS targets compile (Kotlin/Native + Xcode)", NOT_APPLICABLE, required=False)
    if "app-ios" not in affected:
        r.details.append("iOS code not affected")
        return r
    r.status = NOT_RUN
    r.details.append("requires macOS with Xcode; not verified on this machine" if sys.platform != "darwin"
                     else "not automated yet: run ./gradlew :app-ios:linkDebugFrameworkIosSimulatorArm64 manually")
    return r


@_timed
def check_osv(ctx: Context) -> CheckResult:
    r = CheckResult("deps.osv", "Known vulnerabilities (OSV) in direct + resolved dependencies", PASS)
    coords = set(deps.run(ctx.root).coordinates)
    sources = ["version catalog"]
    if ctx.profile == "full":
        for label, task in (("server runtime", [":server:dependencies", "--configuration", "runtimeClasspath"]),
                            ("android release runtime", [":app-android:dependencies", "--configuration", "releaseRuntimeClasspath"])):
            res = gradle.run_tasks(ctx.root, task, ctx.log_dir, "deps-" + label.split()[0])
            if res.exit_code == 0:
                coords |= osv.parse_gradle_tree(res.log_path.read_text(encoding="utf-8", errors="replace"))
                sources.append(label)
            else:
                r.details.append(f"could not resolve {label} tree (exit {res.exit_code}); only direct deps checked")
    try:
        vulns = osv.query(coords)
    except osv.OsvUnavailable as exc:
        r.status = NOT_RUN
        r.details.append(str(exc))
        return r
    r.details.insert(0, f"{len(coords)} coordinates from: {', '.join(sources)}")
    serious = [v for v in vulns if v.severity in ("CRITICAL", "HIGH") or v.severity.startswith("CVSS")]
    if serious:
        r.status = FAIL
    rank = {"CRITICAL": 0, "HIGH": 1, "MODERATE": 2, "MEDIUM": 2, "LOW": 3}
    r.details += [v.render() for v in sorted(vulns, key=lambda v: (rank.get(v.severity, 1 if v.severity.startswith("CVSS") else 4), v.coordinate))]
    if vulns and not serious:
        r.details.append("only MODERATE/LOW/UNKNOWN advisories: review, not blocking")
    return r


def check_tool(tool: str, ctx: Context) -> CheckResult:
    exe = shutil.which(tool)
    title = {"gitleaks": "gitleaks secret scan", "semgrep": "semgrep SAST scan"}[tool]
    r = CheckResult(f"tools.{tool}", title, NOT_CONFIGURED, required=False)
    if not exe:
        r.details.append(f"{tool} is not installed; see docs/CLAUDE_ENGINEERING_SYSTEM.md (optional tools)")
        return r
    if tool == "gitleaks":
        cmd = [exe, "dir", ".", "--no-banner", "--redact"] if ctx.whole_tree else [exe, "git", ".", "--no-banner", "--redact", f"--log-opts={ctx.base_commit}..HEAD"]
    else:
        cmd = [exe, "scan", "--config", "auto", "--error", "--quiet", "server/src/main", "shared/src", "ui/src", "app-android/src"]
    r.command = " ".join(cmd)
    start = time.time()
    proc = subprocess.run(cmd, cwd=ctx.root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    r.duration_s = time.time() - start
    r.exit_code = proc.returncode
    r.status = PASS if proc.returncode == 0 else FAIL
    r.details += proc.stdout.decode("utf-8", "replace").strip().splitlines()[-30:]
    return r


def not_configured(check_id: str, title: str, how: str) -> CheckResult:
    return CheckResult(check_id, title, NOT_CONFIGURED, required=False, details=[how])


def plan(profile: str) -> list[tuple[str, object]]:
    """(check id, function) in execution order. Ids are known up front so --skip never runs
    the skipped check."""
    secrets_id = "secrets.tree" if profile in ("full", "ci") else "secrets.changed"
    static = [("diff.hygiene", check_diff_hygiene), (secrets_id, check_secrets),
              ("arch.boundaries", check_architecture), ("deps.hygiene", check_build_hygiene),
              ("config.claude", check_config), ("config.workflows", check_workflows),
              ("python.selftest", check_selftest)]
    tools = [("tools.gitleaks", lambda c: check_tool("gitleaks", c)),
             ("tools.semgrep", lambda c: check_tool("semgrep", c))]
    if profile in ("quick", "ci"):
        return static
    if profile == "standard":
        return static + [("gradle.affected", check_gradle_affected), ("ios.compile", check_ios)] + tools
    return static + [("gradle.all", check_gradle_full), ("android.lint", check_android_lint),
                     ("deps.osv", check_osv), ("ios.compile", check_ios)] + tools + [
        ("lint.detekt", lambda c: not_configured("lint.detekt", "detekt static analysis",
                                                 "plugin declared in libs.versions.toml but applied to no module")),
        ("lint.ktlint", lambda c: not_configured("lint.ktlint", "ktlint formatting",
                                                 "plugin declared in libs.versions.toml but applied to no module")),
    ]


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--profile", choices=PROFILES, default="standard")
    ap.add_argument("--base", default="HEAD", help="HEAD = uncommitted changes (default); a branch = merge-base with it")
    ap.add_argument("--skip", action="append", default=[], metavar="CHECK_ID", help="report this check as NOT RUN")
    ap.add_argument("--skip-reason", default="", help="required with --skip; recorded in the report")
    ap.add_argument("--list", action="store_true", help="list the checks of the profile and exit")
    args = ap.parse_args(argv)
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")

    if args.skip and not args.skip_reason.strip():
        print("--skip requires --skip-reason", file=sys.stderr)
        return rp.EXIT_USAGE
    try:
        root = gitutil.repo_root(Path(__file__).resolve().parent)
        fp_start = gitutil.fingerprint(root)
        ctx = Context(root, args.profile, args.base)
    except gitutil.GitError as exc:
        print(f"quality gate: {exc}", file=sys.stderr)
        return rp.EXIT_USAGE

    steps = plan(args.profile)
    if args.list:
        for check_id, _ in steps:
            print(check_id)
        return rp.EXIT_PASS
    unknown_skips = set(args.skip) - {cid for cid, _ in steps}
    if unknown_skips:
        print(f"--skip ids not in profile {args.profile}: {', '.join(sorted(unknown_skips))} "
              f"(see --list)", file=sys.stderr)
        return rp.EXIT_USAGE

    results: list[CheckResult] = []
    for check_id, fn in steps:
        if check_id in args.skip:
            result = CheckResult(check_id, "skipped on request", NOT_RUN, True,
                                 [f"skipped on request: {args.skip_reason}"])
        else:
            result = fn(ctx)
        results.append(result)
        print(f"  {result.status:<15} {result.id}", file=sys.stderr, flush=True)

    verdict = rp.overall(results)
    fp_end = gitutil.fingerprint(root)
    header = (f"V-Tempe quality gate | profile={args.profile} | base={args.base} ({ctx.base_commit[:9]}) | "
              f"changed files={len(ctx.changes)} | fingerprint={fp_start}")
    print(rp.render(results, verdict, header))
    if fp_start != fp_end:
        print("WARNING: the working tree changed while the gate ran; results describe a mixed state. Re-run.")
    out = rp.save(root, rp.payload(args.profile, verdict, fp_start, fp_end, args.base, results))
    print(f"report: {out.relative_to(root).as_posix()}")
    return rp.exit_code_for(verdict)


if __name__ == "__main__":
    sys.exit(main())
