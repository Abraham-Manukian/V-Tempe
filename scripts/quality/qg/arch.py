"""Architecture boundary checks derived from the project's actual structure.

Rules (each one verified against the codebase when it was written; known exceptions live in
scripts/quality/arch-baseline.txt so only NEW violations fail the gate):

  MOD  Gradle project dependencies follow modules.ALLOWED_PROJECT_DEPS and have no cycles.
  DOM  shared domain (com.vtempe.shared.domain) does not import shared data.
  KMP  commonMain code of shared/ui/core-designsystem does not import android.*, java.*, javax.*.
  SRV  server feature domain packages do not import Ktor, Exposed, JDBC or their feature's
       data/api layers.
  ISO  :server does not import client packages, and clients do not import server packages.
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from . import modules

IMPORT_RE = re.compile(r"^\s*import\s+([\w.]+)", re.MULTILINE)
PROJECT_DEP_RE = re.compile(r"""project\(\s*["']:([\w\-]+)["']\s*\)""")


@dataclass(frozen=True)
class Violation:
    rule: str
    path: str
    detail: str

    def key(self) -> str:
        return f"{self.rule} {self.path} -> {self.detail}"


def _kotlin_files(root: Path, subdir: str) -> list[Path]:
    base = root / subdir
    return sorted(base.rglob("*.kt")) if base.exists() else []


def _rel(root: Path, p: Path) -> str:
    return p.relative_to(root).as_posix()


def _imports(p: Path) -> list[str]:
    try:
        return IMPORT_RE.findall(p.read_text(encoding="utf-8", errors="replace"))
    except OSError:
        return []


def project_dependencies(root: Path) -> dict[str, set[str]]:
    graph: dict[str, set[str]] = {}
    for mod in modules.MODULES:
        build = root / mod / "build.gradle.kts"
        if not build.exists():
            continue
        text = build.read_text(encoding="utf-8", errors="replace")
        # Lines that only reference another project's build dir (app-ios resources) are not deps.
        deps = set()
        for line in text.splitlines():
            if "implementation(" in line or "api(" in line or "compileOnly(" in line:
                deps |= set(PROJECT_DEP_RE.findall(line))
        graph[mod] = deps
    return graph


def check_module_graph(root: Path) -> list[Violation]:
    out: list[Violation] = []
    graph = project_dependencies(root)
    for mod, deps in graph.items():
        allowed = modules.ALLOWED_PROJECT_DEPS.get(mod, set())
        for dep in sorted(deps - allowed):
            out.append(Violation("MOD", f"{mod}/build.gradle.kts", f":{mod} depends on :{dep} (not allowed)"))
    # cycle detection
    state: dict[str, int] = {}

    def visit(node: str, stack: list[str]) -> None:
        state[node] = 1
        for nxt in graph.get(node, ()):
            if state.get(nxt) == 1:
                cycle = " -> ".join(stack[stack.index(nxt):] + [nxt]) if nxt in stack else f"{node} -> {nxt}"
                out.append(Violation("MOD", f"{node}/build.gradle.kts", f"dependency cycle {cycle}"))
            elif state.get(nxt) is None:
                visit(nxt, stack + [nxt])
        state[node] = 2

    for n in graph:
        if state.get(n) is None:
            visit(n, [n])
    return out


def check_imports(root: Path) -> list[Violation]:
    out: list[Violation] = []
    domain_dir = "shared/src/commonMain/kotlin/com/vtempe/shared/domain"
    for p in _kotlin_files(root, domain_dir):
        for imp in _imports(p):
            if imp.startswith("com.vtempe.shared.data"):
                out.append(Violation("DOM", _rel(root, p), imp))

    for mod in ("shared", "ui", "core-designsystem"):
        for p in _kotlin_files(root, f"{mod}/src/commonMain"):
            for imp in _imports(p):
                if imp.startswith(("android.", "java.", "javax.")):
                    out.append(Violation("KMP", _rel(root, p), imp))

    features = root / "server/src/main/kotlin/com/vtempe/server/features"
    if features.exists():
        for domain in sorted(d for d in features.glob("*/domain") if d.is_dir()):
            for p in sorted(domain.rglob("*.kt")):
                for imp in _imports(p):
                    if (imp.startswith(("io.ktor", "org.jetbrains.exposed", "java.sql", "javax.sql"))
                            or re.match(r"com\.vtempe\.server\.features\.\w+\.(data|api)\b", imp)):
                        out.append(Violation("SRV", _rel(root, p), imp))

    for p in _kotlin_files(root, "server/src"):
        for imp in _imports(p):
            if imp.startswith(("com.vtempe.shared", "com.vtempe.ui", "com.vtempe.core.designsystem")):
                out.append(Violation("ISO", _rel(root, p), imp))
    for mod in ("shared", "ui", "core-designsystem", "app-android", "app-ios"):
        for p in _kotlin_files(root, f"{mod}/src"):
            for imp in _imports(p):
                if imp.startswith("com.vtempe.server"):
                    out.append(Violation("ISO", _rel(root, p), imp))
    return out


def load_baseline(root: Path) -> set[str]:
    path = root / "scripts" / "quality" / "arch-baseline.txt"
    if not path.exists():
        return set()
    return {ln.strip() for ln in path.read_text(encoding="utf-8").splitlines()
            if ln.strip() and not ln.startswith("#")}


def run(root: Path) -> tuple[list[Violation], list[Violation], list[str]]:
    """Returns (new violations, baselined violations, stale baseline entries)."""
    violations = check_module_graph(root) + check_imports(root)
    baseline = load_baseline(root)
    new = [v for v in violations if v.key() not in baseline]
    known = [v for v in violations if v.key() in baseline]
    stale = sorted(baseline - {v.key() for v in violations})
    return new, known, stale


def oversized_kotlin(root: Path, rel_paths: list[str], limit: int = 300) -> list[str]:
    """Changed Kotlin files above the project's soft size limit (advisory, never fails)."""
    out = []
    for rel in rel_paths:
        if not rel.endswith(".kt"):
            continue
        p = root / rel
        try:
            n = sum(1 for _ in p.open(encoding="utf-8", errors="replace"))
        except OSError:
            continue
        if n > limit:
            out.append(f"{rel}: {n} lines (soft limit {limit})")
    return out
