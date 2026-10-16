#!/usr/bin/env python3
"""Prepares the input for the read-only reviewer agents and tracks which reviews are current.

Reviewers have no shell, so they cannot run git. This script writes everything they need into
.claude/state/review/ (diff, changed files, risk assessment, last gate result) and prints the
risk level and the reviewers that level calls for.

  python scripts/quality/review_bundle.py [--base main]        build the bundle
  python scripts/quality/review_bundle.py --status             reviews recorded for this exact code
  python scripts/quality/review_bundle.py --record security-reviewer --verdict FINDINGS --findings 2

A review recorded for fingerprint X is reused while the working tree still has fingerprint X;
any edit changes the fingerprint, so a reviewer is never skipped on code it has not seen.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from qg import gitutil, modules, risk  # noqa: E402
from qg import report as rp  # noqa: E402

VERDICTS = ("PASS", "FINDINGS", "BLOCKED")
APP_CODE_EXTENSIONS = (".kt", ".kts", ".java", ".sq", ".sqm")


def bundle_dir(root: Path) -> Path:
    d = root / ".claude" / "state" / "review"
    d.mkdir(parents=True, exist_ok=True)
    return d


def load_records(root: Path) -> list[dict]:
    path = bundle_dir(root) / "records.json"
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return []


def build(root: Path, base: str) -> int:
    base_commit = gitutil.resolve_base(root, base)
    changes = gitutil.changed_files(root, base_commit)
    paths = [c.path for c in changes]
    # Content signals only make sense in product code, not in docs or in the gate's own rules.
    code_paths = [c.path for c in changes if c.path.endswith(APP_CODE_EXTENSIONS) and not c.deleted
                  and not c.path.startswith(("scripts/", ".claude/"))]
    tracked_diff = gitutil.diff_text(root, base_commit)
    # Untracked files are not in `git diff`; show them as whole-file additions.
    untracked_parts: dict[str, str] = {}
    for c in changes:
        if c.status != "?" or modules.category(c.path) == "other":
            continue
        p = root / c.path
        if p.stat().st_size > 512 * 1024 or not gitutil.is_probably_text(p):
            continue
        body = p.read_text(encoding="utf-8", errors="replace").splitlines()
        untracked_parts[c.path] = (f"--- /dev/null\n+++ b/{c.path}\n@@ -0,0 +1,{len(body)} @@\n" +
                                   "\n".join("+" + ln for ln in body))
    diff = tracked_diff + ("\n".join(untracked_parts.values()) + "\n" if untracked_parts else "")
    tracked_code = [p for p in code_paths if p not in untracked_parts]
    code_diff = gitutil.diff_text(root, base_commit, tracked_code) if tracked_code else ""
    added = risk.added_lines_of(code_diff) + [ln for path, part in untracked_parts.items()
                                              if path in code_paths for ln in risk.added_lines_of(part)]
    assessment = risk.assess(risk.load_rules(root), paths, added)
    fp = gitutil.fingerprint(root)
    last_gate = rp.load_last(root)

    d = bundle_dir(root)
    (d / "diff.patch").write_text(diff, encoding="utf-8")
    (d / "changed-files.txt").write_text(
        "\n".join(f"{c.status}\t{c.path}" for c in changes) + "\n", encoding="utf-8")
    gate_line = "none recorded"
    if last_gate:
        current = "current" if last_gate.get("fingerprint") == fp else "STALE (code changed since)"
        gate_line = f"{last_gate['verdict']} ({last_gate['profile']}, {last_gate['timestamp']}, {current})"
    summary = [
        "# Review bundle",
        "",
        f"- Base: `{base}` ({base_commit[:9]})",
        f"- Fingerprint: `{fp}`",
        f"- Changed files: {len(changes)} (list: `.claude/state/review/changed-files.txt`)",
        f"- Diff: `.claude/state/review/diff.patch` (untracked text files included as additions)",
        f"- Last quality gate: {gate_line}",
        f"- Risk level (computed): **{assessment.level}**",
        "",
        "## Triggers",
        *[f"- {t}" for t in assessment.triggers or ["none"]],
        "",
        "## Reviewers this level calls for",
        *[f"- {r}" for r in assessment.reviewers or ["none (LOW): main agent self-review of the diff"]],
    ]
    if assessment.unmatched:
        summary += ["", "## Files no risk rule covers (classify manually)",
                    *[f"- {p}" for p in assessment.unmatched[:30]]]
    (d / "summary.md").write_text("\n".join(summary) + "\n", encoding="utf-8")
    (d / "bundle.json").write_text(json.dumps({
        "base": base, "base_commit": base_commit, "fingerprint": fp, "level": assessment.level,
        "reviewers": assessment.reviewers, "specialists": assessment.specialists,
        "triggers": assessment.triggers, "files": paths,
    }, indent=2), encoding="utf-8")
    print("\n".join(summary))
    print(f"\nbundle written to {d.relative_to(root).as_posix()}/")
    _print_status(root, fp)
    return 0


def _print_status(root: Path, fp: str) -> None:
    current = [r for r in load_records(root) if r.get("fingerprint") == fp]
    if current:
        print("\nReviews already recorded for this exact code (do not re-run them):")
        for r in current:
            print(f"- {r['agent']}: {r['verdict']} ({r['findings']} findings, {r['timestamp']})")
    else:
        print("\nNo reviews recorded for this exact code yet.")


def record(root: Path, agent: str, verdict: str, findings: int) -> int:
    agents_dir = root / ".claude" / "agents"
    if not (agents_dir / f"{agent}.md").exists():
        print(f"unknown agent '{agent}' (no .claude/agents/{agent}.md)", file=sys.stderr)
        return 2
    fp = gitutil.fingerprint(root)
    records = [r for r in load_records(root) if not (r["agent"] == agent and r["fingerprint"] == fp)]
    records.append({"agent": agent, "verdict": verdict, "findings": findings, "fingerprint": fp,
                    "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S")})
    (bundle_dir(root) / "records.json").write_text(json.dumps(records[-200:], indent=2), encoding="utf-8")
    print(f"recorded {agent}: {verdict} ({findings} findings) for fingerprint {fp}")
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base", default="main", help="branch/commit to review against (default: main)")
    ap.add_argument("--status", action="store_true")
    ap.add_argument("--record", metavar="AGENT")
    ap.add_argument("--verdict", choices=VERDICTS)
    ap.add_argument("--findings", type=int, default=0)
    args = ap.parse_args(argv)
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")
    try:
        root = gitutil.repo_root(Path(__file__).resolve().parent)
        if args.record:
            if not args.verdict:
                print("--record requires --verdict", file=sys.stderr)
                return 2
            return record(root, args.record, args.verdict, args.findings)
        if args.status:
            _print_status(root, gitutil.fingerprint(root))
            return 0
        return build(root, args.base)
    except gitutil.GitError as exc:
        print(f"review bundle: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
