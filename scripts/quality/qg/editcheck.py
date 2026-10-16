"""Fast per-file checks run by the PostToolUse hook after every Edit/Write.

Each check is cheap (no Gradle, no network). Results are split into blocking problems (Claude
must fix them now) and reminders (context for the next step).
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path

from . import frontmatter as fm
from . import secrets as sec

CONFLICT_RE = re.compile(r"^(<<<<<<< |>>>>>>> |=======\s*$)", re.MULTILINE)
INTERFACE_RE = re.compile(r"^\s*(?:(?:public|internal|private|sealed|fun|expect)\s+)*interface\s+(\w+)", re.MULTILINE)
ABSTRACT_FUN_RE = re.compile(r"^\s*(?:suspend\s+)?fun\s+(?:<[^>]+>\s*)?(\w+)\s*\([^)]*\)\s*(?::\s*[\w<>?,.\s*]+)?\s*$", re.MULTILINE)
SERVER_PRINT_RE = re.compile(r"^\s*(println\(|print\(|System\.(out|err)\.print|\w+\.printStackTrace\(\))", re.MULTILINE)
DYNAMIC_VERSION_RE = re.compile(r"""["'][\w.\-]+:[\w.\-]+:[^"']*(\+|latest\.)[^"']*["']|=\s*["'][^"']*(\+|latest\.(release|integration))["']""")
KOTLIN_SOFT_LIMIT = 300


@dataclass
class EditFindings:
    blocking: list[str] = field(default_factory=list)
    reminders: list[str] = field(default_factory=list)


def _implementers(root: Path, names: list[str], exclude: Path) -> dict[str, list[str]]:
    found: dict[str, list[str]] = {n: [] for n in names}
    patterns = {n: re.compile(r"(class|object)\s+\w+[^{\n]*[:,]\s*(\w+\.)*" + re.escape(n) + r"\b") for n in names}
    for mod in ("shared", "ui", "core-designsystem", "app-android", "app-ios", "server"):
        base = root / mod / "src"
        if not base.exists():
            continue
        for p in base.rglob("*.kt"):
            if p == exclude:
                continue
            try:
                text = p.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            for n, rx in patterns.items():
                if n in text and rx.search(text):
                    found[n].append(p.relative_to(root).as_posix())
    return found


def check_kotlin(root: Path, rel: str, path: Path, text: str, new_fragment: str, out: EditFindings) -> None:
    lines = text.count("\n") + 1
    if lines > KOTLIN_SOFT_LIMIT:
        out.reminders.append(f"{rel} has {lines} lines (> {KOTLIN_SOFT_LIMIT}, the project's soft limit): "
                             "consider extracting state/logic/sections instead of growing it")
    interfaces = INTERFACE_RE.findall(text)
    if interfaces and ABSTRACT_FUN_RE.search(new_fragment or ""):
        impls = _implementers(root, interfaces, path)
        listed = "; ".join(f"{n}: {', '.join(v[:8]) or 'no implementers found'}" for n, v in impls.items())
        out.reminders.append(f"{rel} declares interface(s) and the edit adds an abstract member. Update EVERY "
                             f"implementation in the same change (Android VM, iOS presenter, fakes in tests). {listed}")
    if rel.startswith("server/src/main/") and SERVER_PRINT_RE.search(new_fragment or ""):
        out.reminders.append(f"{rel}: println/printStackTrace in server code bypasses SLF4J and can log PII; "
                             "use the class logger and never log profile/health data or tokens")
    if "GlobalScope" in (new_fragment or ""):
        out.reminders.append(f"{rel}: GlobalScope leaks work past its owner's lifecycle; use a structured scope")
    is_test = re.search(r"/src/\w*[Tt]est\w*/", rel) is not None
    if re.search(r"\brunBlocking\b", new_fragment or "") and not is_test:
        out.reminders.append(f"{rel}: runBlocking in production code blocks a thread (Netty event loop / UI thread)")


def check_sql(rel: str, root: Path, out: EditFindings) -> None:
    if rel.endswith(".sqm"):
        mig_dir = root / Path(rel).parent
        nums = sorted(int(p.stem) for p in mig_dir.glob("*.sqm") if p.stem.isdigit())
        if nums and nums != list(range(nums[0], nums[0] + len(nums))):
            out.blocking.append(f"SQLDelight migrations in {Path(rel).parent.as_posix()} are not contiguous: {nums}")
    out.reminders.append(f"{rel}: schema change. A new client DB version needs a new N.sqm migration (never edit a "
                         "shipped one), and MigrationEquivalenceTest (:shared:testDebugUnitTest) must pass; "
                         "classify the task HIGH (database-reviewer)")


def check_build_file(rel: str, text: str, out: EditFindings) -> None:
    for m in DYNAMIC_VERSION_RE.finditer(text):
        out.blocking.append(f"{rel}: dynamic dependency version {m.group(0)} (pin an exact version in libs.versions.toml)")
    if re.search(r"""maven\(\s*(url\s*=\s*)?(uri\()?["']http://""", text):
        out.blocking.append(f"{rel}: insecure http:// Maven repository")
    out.reminders.append(f"{rel}: build/dependency change - run the quality gate (standard) and treat it as HIGH risk "
                         "(dependency-reviewer)")


def check_structured(rel: str, path: Path, text: str, out: EditFindings) -> None:
    if rel.endswith(".json"):
        try:
            json.loads(text)
        except json.JSONDecodeError as exc:
            out.blocking.append(f"{rel}: invalid JSON: {exc}")
    elif rel.endswith(".toml"):
        try:
            import tomllib
        except ImportError:
            return
        try:
            tomllib.loads(text)
        except tomllib.TOMLDecodeError as exc:
            out.blocking.append(f"{rel}: invalid TOML: {exc}")
    elif rel.startswith(".claude/") and rel.endswith(".md") and (rel.startswith((".claude/agents/", ".claude/skills/"))
                                                               or text.startswith("---")):
        try:
            fm.load(text)
        except fm.FrontmatterError as exc:
            out.blocking.append(f"{rel}: frontmatter: {exc}")
        else:
            out.reminders.append("Claude config changed: run `python scripts/quality/quality_gate.py --profile quick` "
                                 "(config.claude validates agents/skills/rules)")


def check_file(root: Path, rel: str, new_fragment: str = "") -> EditFindings:
    out = EditFindings()
    path = root / rel
    try:
        if path.stat().st_size > 2 * 1024 * 1024 or not path.is_file():
            return out
        raw = path.read_bytes()
    except OSError:
        return out
    if b"\0" in raw[:8192]:
        return out
    text = raw.decode("utf-8", "replace")
    if CONFLICT_RE.search(text) and not rel.endswith((".md", ".txt")):
        out.blocking.append(f"{rel}: contains merge conflict markers")
    allow = sec.load_allowlist(root)
    if not sec.path_allowed(rel, allow):
        for f in sec.scan_text(text, rel):
            out.blocking.append(f"possible secret: {f.render()} - remove it (use env/Secret Manager/local.properties) "
                                f"or mark a reviewed false positive with `{sec.ALLOW_MARKER}`")
    if sec.is_forbidden_file(rel):
        out.blocking.append(f"{rel}: this file type must not live in the repository (keystore/secret/local config)")
    if rel.endswith((".kt", ".kts")) and not rel.endswith("build.gradle.kts"):
        check_kotlin(root, rel, path, text, new_fragment, out)
    if rel.endswith((".sq", ".sqm")):
        check_sql(rel, root, out)
    if rel.endswith((".gradle.kts", "libs.versions.toml", "gradle.properties")):
        check_build_file(rel, text, out)
    check_structured(rel, path, text, out)
    return out
