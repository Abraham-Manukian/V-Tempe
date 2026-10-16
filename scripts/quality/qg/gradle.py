"""Runs Gradle tasks with the environment this project needs and summarizes JUnit results.

Local quirks handled here (documented in FIX_PROGRESS.md):
- JDK 17+ is required; the default `java` on the dev machine is 8. JAVA_HOME is used when it
  points at 17+, otherwise QG_JAVA_HOME, otherwise a JDK 17/21 found in the usual install dirs.
- On Windows Gradle fails with "Unable to establish loopback connection" unless
  -Djdk.net.unixdomain.tmpdir points at a short directory; it is added to JAVA_TOOL_OPTIONS.
Extra Gradle arguments (e.g. --offline, --max-workers=2) come from QG_GRADLE_ARGS.
"""
from __future__ import annotations

import glob
import os
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

DEFAULT_TIMEOUT_S = 45 * 60


@dataclass
class GradleResult:
    exit_code: int | None  # None: could not start
    duration_s: float
    log_path: Path
    tests: int = 0
    failures: int = 0
    errors: int = 0
    skipped: int = 0
    failed_tests: list[str] | None = None
    note: str = ""

    def summary(self) -> str:
        if not (self.tests or self.failures or self.errors):
            return f"exit={self.exit_code} time={self.duration_s:.0f}s"
        return (f"exit={self.exit_code} tests={self.tests} failures={self.failures} "
                f"errors={self.errors} skipped={self.skipped} time={self.duration_s:.0f}s")


def _java_major(home: Path) -> int | None:
    release = home / "release"
    if release.exists():
        m = re.search(r'JAVA_VERSION="(\d+)(?:\.(\d+))?', release.read_text(encoding="utf-8", errors="replace"))
        if m:
            major = int(m.group(1))
            return int(m.group(2)) if major == 1 and m.group(2) else major
    return None


def find_java_home() -> Path | None:
    candidates: list[str] = []
    for var in ("QG_JAVA_HOME", "JAVA_HOME"):
        if os.environ.get(var):
            candidates.append(os.environ[var])
    if os.name == "nt":
        for pattern in (r"C:\Program Files\Eclipse Adoptium\jdk-17*", r"C:\Program Files\Eclipse Adoptium\jdk-21*",
                        r"C:\Program Files\Java\jdk-17*", r"C:\Program Files\Java\jdk-21*",
                        r"C:\Program Files\Microsoft\jdk-17*", r"C:\Program Files\Android\Android Studio\jbr"):
            candidates += sorted(glob.glob(pattern))
    else:
        candidates += sorted(glob.glob("/usr/lib/jvm/*17*")) + sorted(glob.glob("/usr/lib/jvm/*21*"))
        candidates += sorted(glob.glob("/Library/Java/JavaVirtualMachines/*/Contents/Home"))
    for c in candidates:
        home = Path(c)
        major = _java_major(home)
        if major is not None and major >= 17:
            return home
    return None


def build_env(root: Path) -> tuple[dict[str, str], str]:
    env = dict(os.environ)
    notes = []
    java_home = find_java_home()
    if java_home:
        env["JAVA_HOME"] = str(java_home)
        notes.append(f"JAVA_HOME={java_home}")
    else:
        notes.append("no JDK 17+ found (set QG_JAVA_HOME)")
    if os.name == "nt" and "jdk.net.unixdomain.tmpdir" not in env.get("JAVA_TOOL_OPTIONS", ""):
        uds = Path(env.get("QG_UDS_TMPDIR", Path.home() / ".gradle" / "uds"))
        uds.mkdir(parents=True, exist_ok=True)
        env["JAVA_TOOL_OPTIONS"] = (env.get("JAVA_TOOL_OPTIONS", "") +
                                    f" -Djdk.net.unixdomain.tmpdir={uds.as_posix()}").strip()
    return env, "; ".join(notes)


def _junit_results(root: Path, tasks: list[str]) -> tuple[int, int, int, int, list[str]]:
    """Reads the JUnit XML of the requested test tasks. Gradle rewrites a test task's result
    directory whenever the task runs; an UP-TO-DATE task keeps the results of identical inputs,
    so both cases describe the current code."""
    tests = failures = errors = skipped = 0
    failed: list[str] = []
    xml_files: list[Path] = []
    for task in tasks:
        parts = task.strip(":").split(":")
        if len(parts) == 2 and "test" in parts[1].lower() and "compile" not in parts[1].lower():
            xml_files += list((root / parts[0] / "build" / "test-results" / parts[1]).glob("TEST-*.xml"))
    for xml_path in xml_files:
        try:
            suite = ET.parse(xml_path).getroot()
        except (OSError, ET.ParseError):
            continue
        tests += int(suite.get("tests", 0))
        failures += int(suite.get("failures", 0))
        errors += int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
        for case in suite.iter("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                failed.append(f"{case.get('classname')}.{case.get('name')}")
    return tests, failures, errors, skipped, failed


def run_tasks(root: Path, tasks: list[str], log_dir: Path, label: str,
              timeout_s: int = DEFAULT_TIMEOUT_S) -> GradleResult:
    log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_dir / f"gradle-{label}.log"
    env, note = build_env(root)
    extra = shlex.split(os.environ.get("QG_GRADLE_ARGS", ""))
    if os.name == "nt":
        cmd = ["cmd.exe", "/d", "/c", str(root / "gradlew.bat")]
    else:
        cmd = ["sh", str(root / "gradlew")]
    cmd += ["--console=plain", "--continue", *extra, *tasks]
    start = time.time()
    try:
        with open(log_path, "w", encoding="utf-8", errors="replace") as log:
            log.write(f"$ {' '.join(cmd)}\n# {note}\n")
            log.flush()
            proc = subprocess.run(cmd, cwd=str(root), env=env, stdout=log, stderr=subprocess.STDOUT,
                                  timeout=timeout_s)
        exit_code: int | None = proc.returncode
    except subprocess.TimeoutExpired:
        exit_code = None
        note += f"; timed out after {timeout_s}s"
    except OSError as exc:
        exit_code = None
        note += f"; could not start Gradle: {exc}"
    t, f, e, s, failed = _junit_results(root, tasks)
    up_to_date = re.findall(r"^> Task (\S+) UP-TO-DATE", log_path.read_text(encoding="utf-8", errors="replace"),
                            re.MULTILINE) if log_path.exists() else []
    test_up_to_date = [u for u in up_to_date if u in tasks]
    if test_up_to_date:
        note += f"; up-to-date (inputs unchanged since a previous run): {', '.join(test_up_to_date)}"
    return GradleResult(exit_code, time.time() - start, log_path, t, f, e, s, failed, note)


def log_tail(path: Path, lines: int = 25) -> list[str]:
    try:
        content = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return []
    keep = [ln for ln in content if "Picked up JAVA_TOOL_OPTIONS" not in ln]
    return keep[-lines:]
