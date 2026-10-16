"""Build supply-chain hygiene: version catalog, inline versions, repositories, Gradle wrapper.

Static and offline. Vulnerability lookups are a separate check (osv.py) because they need the
network.
"""
from __future__ import annotations

import re
import tomllib
from dataclasses import dataclass, field
from pathlib import Path

from . import modules

TRUSTED_REPO_HOSTS = (
    "https://maven.pkg.jetbrains.space/public/p/compose/dev",  # Compose dev builds (settings.gradle.kts)
)
INLINE_DEP_RE = re.compile(r"""["']([\w.\-]+):([\w.\-]+):([^"'$]+)["']""")
MAVEN_URL_RE = re.compile(r"""maven\(\s*(?:url\s*=\s*)?(?:uri\()?["']([^"']+)["']""")


@dataclass
class DepsReport:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    coordinates: list[tuple[str, str]] = field(default_factory=list)  # (group:artifact, version)
    unversioned: list[str] = field(default_factory=list)  # BOM-managed, not checkable statically


def _is_dynamic(version: str) -> bool:
    v = version.strip()
    return (v.endswith("+") or v.startswith(("latest.", "[", "(")) or "SNAPSHOT" in v.upper())


def check_catalog(root: Path, report: DepsReport) -> None:
    path = root / "gradle" / "libs.versions.toml"
    if not path.exists():
        report.errors.append("gradle/libs.versions.toml missing")
        return
    try:
        data = tomllib.loads(path.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError as exc:
        report.errors.append(f"gradle/libs.versions.toml does not parse: {exc}")
        return
    versions = data.get("versions", {})
    for key, ver in versions.items():
        if isinstance(ver, str) and _is_dynamic(ver):
            report.errors.append(f"libs.versions.toml: dynamic version {key} = {ver}")
    for alias, lib in data.get("libraries", {}).items():
        if isinstance(lib, str):
            parts = lib.split(":")
            if len(parts) == 3:
                report.coordinates.append((f"{parts[0]}:{parts[1]}", parts[2]))
            continue
        module = lib.get("module") or (f"{lib.get('group')}:{lib.get('name')}" if lib.get("group") else None)
        version = lib.get("version")
        if isinstance(version, dict):
            version = versions.get(version.get("ref", ""), version.get("strictly") or version.get("require"))
        if not module:
            report.errors.append(f"libs.versions.toml: library {alias} has no module/group")
        elif version is None:
            report.unversioned.append(module)
        elif _is_dynamic(str(version)):
            report.errors.append(f"libs.versions.toml: dynamic version for {alias}: {version}")
        else:
            report.coordinates.append((module, str(version)))
    for alias, plugin in data.get("plugins", {}).items():
        version = plugin.get("version") if isinstance(plugin, dict) else None
        if isinstance(version, dict):
            version = versions.get(version.get("ref", ""))
        if isinstance(version, str) and _is_dynamic(version):
            report.errors.append(f"libs.versions.toml: dynamic plugin version {alias}: {version}")


def check_build_scripts(root: Path, report: DepsReport) -> None:
    scripts = [root / "build.gradle.kts", root / "settings.gradle.kts"]
    scripts += [root / m / "build.gradle.kts" for m in modules.MODULES]
    for script in scripts:
        if not script.exists():
            continue
        rel = script.relative_to(root).as_posix()
        text = script.read_text(encoding="utf-8", errors="replace")
        for lineno, line in enumerate(text.splitlines(), start=1):
            stripped = line.strip()
            if stripped.startswith("//"):
                continue
            for url in MAVEN_URL_RE.findall(line):
                if url.startswith("http://"):
                    report.errors.append(f"{rel}:{lineno}: insecure repository {url}")
                elif url not in TRUSTED_REPO_HOSTS:
                    report.errors.append(f"{rel}:{lineno}: repository not on the reviewed list: {url} "
                                         f"(add it to deps.TRUSTED_REPO_HOSTS after review)")
            for group, name, version in INLINE_DEP_RE.findall(line):
                if "." not in group:
                    continue  # not a Maven coordinate (e.g. a task path)
                coord = f"{group}:{name}"
                if _is_dynamic(version):
                    report.errors.append(f"{rel}:{lineno}: dynamic version {coord}:{version}")
                else:
                    report.warnings.append(f"{rel}:{lineno}: version outside the catalog {coord}:{version}")
                    report.coordinates.append((coord, version))


def check_wrapper(root: Path, report: DepsReport) -> None:
    props = root / "gradle" / "wrapper" / "gradle-wrapper.properties"
    if not props.exists():
        report.errors.append("gradle-wrapper.properties missing")
        return
    text = props.read_text(encoding="utf-8")
    if not re.search(r"^distributionSha256Sum=[0-9a-f]{64}\s*$", text, re.MULTILINE):
        report.errors.append("gradle-wrapper.properties: distributionSha256Sum missing (wrapper download unverified)")
    m = re.search(r"^distributionUrl=(.+)$", text, re.MULTILINE)
    if not m or not m.group(1).replace("\\:", ":").startswith("https://services.gradle.org/distributions/"):
        report.errors.append("gradle-wrapper.properties: distributionUrl is not an official https Gradle URL")


def run(root: Path) -> DepsReport:
    report = DepsReport()
    check_catalog(root, report)
    check_build_scripts(root, report)
    check_wrapper(root, report)
    return report
