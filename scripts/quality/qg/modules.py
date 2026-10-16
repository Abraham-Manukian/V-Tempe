"""Module map of the V-Tempe Gradle build and the checks each kind of change needs.

Source of truth for the graph is settings.gradle.kts and the `project(":x")` dependencies in
each module's build.gradle.kts; arch.py verifies that the edges below still match the build, so
this table cannot silently drift.
"""
from __future__ import annotations

import fnmatch

MODULES = ("shared", "ui", "core-designsystem", "app-android", "app-ios", "server")

# module -> modules it depends on (project dependencies). Verified against the build by arch.py.
ALLOWED_PROJECT_DEPS: dict[str, set[str]] = {
    "shared": set(),
    "core-designsystem": set(),
    "server": set(),
    "ui": {"shared", "core-designsystem"},
    "app-android": {"shared", "ui"},
    "app-ios": {"shared", "ui", "core-designsystem"},
}

# Files outside server/ that the server build reads (Dockerfile COPY + ExerciseCatalogDriftTest).
SERVER_EXTRA_INPUTS = (
    "testdata/*",
    "shared/src/commonMain/kotlin/com/vtempe/shared/domain/exercise/ExerciseLibrary.kt",
    "shared/src/commonMain/kotlin/com/vtempe/shared/domain/exercise/ExerciseBrowseCatalog.kt",
)

ROOT_BUILD_FILES = (
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    "gradle/libs.versions.toml", "gradle/wrapper/*", "gradlew", "gradlew.bat",
)

# Gradle tasks that compile and unit-test one module on a developer machine (no device, no Mac).
STANDARD_TASKS: dict[str, list[str]] = {
    "shared": [":shared:compileCommonMainKotlinMetadata", ":shared:testDebugUnitTest"],
    "ui": [":ui:compileCommonMainKotlinMetadata", ":ui:testDebugUnitTest"],
    "core-designsystem": [":core-designsystem:compileCommonMainKotlinMetadata",
                          ":core-designsystem:testDebugUnitTest"],
    "app-android": [":app-android:testDebugUnitTest"],
    "server": [":server:test"],
    "app-ios": [],  # needs macOS + Xcode; reported separately as NOT RUN
}

FULL_TASKS = [
    ":shared:compileCommonMainKotlinMetadata", ":shared:testDebugUnitTest",
    ":ui:compileCommonMainKotlinMetadata", ":ui:testDebugUnitTest",
    ":core-designsystem:compileCommonMainKotlinMetadata", ":core-designsystem:testDebugUnitTest",
    ":app-android:testDebugUnitTest", ":app-android:assembleDebug",
    ":server:test",
]

CODE_EXTENSIONS = (".kt", ".kts", ".java", ".sq", ".sqm", ".gradle")
CONFIG_PATTERNS = (
    ".claude/*", "CLAUDE.md", "scripts/quality/*", ".github/workflows/*",
    "Dockerfile", ".dockerignore", "cloudbuild.yaml", ".gitignore",
)


def _match(path: str, patterns) -> bool:
    return any(fnmatch.fnmatchcase(path, p) for p in patterns)


def module_of(path: str) -> str | None:
    head = path.split("/", 1)[0]
    return head if head in MODULES else None


def dependents(module: str) -> set[str]:
    """Modules that (transitively) depend on `module`."""
    result: set[str] = set()
    frontier = {module}
    while frontier:
        nxt = {m for m, deps in ALLOWED_PROJECT_DEPS.items() if deps & frontier} - result
        result |= nxt
        frontier = nxt
    return result


def is_build_affecting(path: str) -> bool:
    """True when the change can alter what Gradle compiles or tests."""
    if _match(path, ROOT_BUILD_FILES):
        return True
    mod = module_of(path)
    if mod is None:
        return _match(path, SERVER_EXTRA_INPUTS)
    if "/src/" in path or path.endswith((".kts", ".pro")):
        return True
    return False


def affected_modules(paths: list[str]) -> set[str]:
    """Modules whose compile/test result can change, including downstream dependents."""
    affected: set[str] = set()
    for p in paths:
        if _match(p, ROOT_BUILD_FILES):
            return set(MODULES)
        if _match(p, SERVER_EXTRA_INPUTS):
            affected.add("server")
        mod = module_of(p)
        if mod and is_build_affecting(p):
            affected.add(mod)
    for mod in list(affected):
        affected |= dependents(mod)
    return affected


def category(path: str) -> str:
    """'code' needs compile/tests, 'config' needs the static checks, 'other' needs nothing."""
    if path.endswith(CODE_EXTENSIONS) or is_build_affecting(path):
        return "code"
    if _match(path, CONFIG_PATTERNS):
        return "config"
    return "other"


def required_profile(paths: list[str]) -> str | None:
    """Minimal gate profile a set of edits needs before work is reported done."""
    cats = {category(p) for p in paths}
    if "code" in cats:
        return "standard"
    if "config" in cats:
        return "quick"
    return None


PROFILE_RANK = {"quick": 1, "ci": 1, "standard": 2, "full": 3}
