"""Known-vulnerability lookup for Maven coordinates through the public OSV API (api.osv.dev).

Only package coordinates and versions are sent - no source code. Any network problem is reported
as an error so the caller can mark the check NOT RUN instead of PASS.
"""
from __future__ import annotations

import json
import re
import urllib.error
import urllib.request
from dataclasses import dataclass

OSV_BATCH = "https://api.osv.dev/v1/querybatch"
OSV_VULN = "https://api.osv.dev/v1/vulns/{}"
TIMEOUT_S = 30
BATCH_LIMIT = 500
MAX_DETAILS = 80

# "+--- io.ktor:ktor-server-core-jvm:2.3.12 -> 2.3.13 (*)" / "\--- group:name -> 1.2 (c)"
TREE_LINE_RE = re.compile(r"[+\\]---\s+([\w.\-]+):([\w.\-]+)(?::([\w.\-+]+))?(?:\s+->\s+([\w.\-+]+))?")


class OsvUnavailable(RuntimeError):
    pass


@dataclass
class Vuln:
    coordinate: str
    version: str
    id: str
    severity: str
    summary: str

    def render(self) -> str:
        return f"{self.coordinate}:{self.version} {self.id} [{self.severity}] {self.summary}"


def parse_gradle_tree(text: str) -> set[tuple[str, str]]:
    coords: set[tuple[str, str]] = set()
    for m in TREE_LINE_RE.finditer(text):
        group, name, declared, resolved = m.groups()
        version = resolved or declared
        if version and not version.endswith("+"):
            coords.add((f"{group}:{name}", version))
    return coords


def _post(url: str, payload: dict) -> dict:
    req = urllib.request.Request(url, data=json.dumps(payload).encode(), method="POST",
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_S) as resp:
            return json.loads(resp.read().decode())
    except (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError) as exc:
        raise OsvUnavailable(f"OSV request failed: {exc}") from exc


def _get(url: str) -> dict:
    try:
        with urllib.request.urlopen(url, timeout=TIMEOUT_S) as resp:
            return json.loads(resp.read().decode())
    except (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError) as exc:
        raise OsvUnavailable(f"OSV request failed: {exc}") from exc


def _severity(detail: dict) -> str:
    sev = (detail.get("database_specific") or {}).get("severity")
    if isinstance(sev, str) and sev:
        return sev.upper()
    for s in detail.get("severity") or []:
        if s.get("type", "").startswith("CVSS"):
            return f"CVSS {s.get('score', '?')}"
    return "UNKNOWN"


def query(coords: set[tuple[str, str]]) -> list[Vuln]:
    ordered = sorted(coords)
    hits: list[tuple[str, str, str]] = []
    for start in range(0, len(ordered), BATCH_LIMIT):
        chunk = ordered[start:start + BATCH_LIMIT]
        body = {"queries": [{"package": {"name": c, "ecosystem": "Maven"}, "version": v} for c, v in chunk]}
        result = _post(OSV_BATCH, body)
        for (coord, ver), res in zip(chunk, result.get("results", [])):
            for vuln in res.get("vulns") or []:
                hits.append((coord, ver, vuln["id"]))
    vulns: list[Vuln] = []
    for i, (coord, ver, vid) in enumerate(hits):
        if i < MAX_DETAILS:
            detail = _get(OSV_VULN.format(vid))
            summary = (detail.get("summary") or detail.get("details") or "").strip().splitlines()
            vulns.append(Vuln(coord, ver, vid, _severity(detail), summary[0][:160] if summary else ""))
        else:
            vulns.append(Vuln(coord, ver, vid, "UNKNOWN", "(details not fetched: limit reached)"))
    return vulns
