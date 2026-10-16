"""Check results, overall verdict and persisted gate state.

Statuses are deliberately not interchangeable: a check that did not execute is NOT RUN, a tool
the project has not set up is NOT CONFIGURED, a check irrelevant to the change is NOT APPLICABLE.
Only PASS means the check ran and found nothing blocking.
"""
from __future__ import annotations

import json
import time
from dataclasses import asdict, dataclass, field
from pathlib import Path

PASS, FAIL, NOT_RUN, NOT_CONFIGURED, NOT_APPLICABLE = "PASS", "FAIL", "NOT RUN", "NOT CONFIGURED", "NOT APPLICABLE"
STATUSES = (PASS, FAIL, NOT_RUN, NOT_CONFIGURED, NOT_APPLICABLE)

EXIT_PASS, EXIT_FAIL, EXIT_USAGE, EXIT_INCOMPLETE = 0, 1, 2, 3


@dataclass
class CheckResult:
    id: str
    title: str
    status: str
    required: bool = True
    details: list[str] = field(default_factory=list)
    command: str = ""
    exit_code: int | None = None
    duration_s: float = 0.0

    def __post_init__(self) -> None:
        if self.status not in STATUSES:
            raise ValueError(f"unknown status {self.status}")


def overall(results: list[CheckResult]) -> str:
    """FAIL if anything failed; INCOMPLETE if a required check did not run; else PASS."""
    if any(r.status == FAIL for r in results):
        return "FAIL"
    if any(r.required and r.status in (NOT_RUN, NOT_CONFIGURED) for r in results):
        return "INCOMPLETE"
    return "PASS"


def exit_code_for(verdict: str) -> int:
    return {"PASS": EXIT_PASS, "FAIL": EXIT_FAIL, "INCOMPLETE": EXIT_INCOMPLETE}[verdict]


def render(results: list[CheckResult], verdict: str, header: str, max_details: int = 12) -> str:
    lines = [header, ""]
    width = max(len(r.id) for r in results) if results else 10
    for r in results:
        req = "" if r.required else " (optional)"
        timing = f" [{r.duration_s:.1f}s]" if r.duration_s >= 0.1 else ""
        lines.append(f"{r.status:<15} {r.id:<{width}}  {r.title}{req}{timing}")
        if r.command:
            lines.append(f"{'':<15} {'':<{width}}  $ {r.command}" + (f"  (exit {r.exit_code})" if r.exit_code is not None else ""))
        for d in r.details[:max_details]:
            lines.append(f"{'':<15} {'':<{width}}  - {d}")
        if len(r.details) > max_details:
            lines.append(f"{'':<15} {'':<{width}}  - ... {len(r.details) - max_details} more (see JSON report)")
    lines += ["", f"QUALITY GATE: {verdict}"]
    return "\n".join(lines)


def state_dir(root: Path) -> Path:
    d = root / ".claude" / "state" / "quality-gate"
    d.mkdir(parents=True, exist_ok=True)
    return d


def save(root: Path, payload: dict) -> Path:
    d = state_dir(root)
    out = d / "last.json"
    tmp = d / "last.json.tmp"
    tmp.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    tmp.replace(out)
    history = d / "history.jsonl"
    with open(history, "a", encoding="utf-8") as fh:
        fh.write(json.dumps({k: payload[k] for k in ("timestamp", "profile", "verdict", "fingerprint")}) + "\n")
    return out


def load_last(root: Path) -> dict | None:
    path = root / ".claude" / "state" / "quality-gate" / "last.json"
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None


def payload(profile: str, verdict: str, fingerprint_start: str, fingerprint_end: str,
            base: str, results: list[CheckResult]) -> dict:
    return {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "profile": profile,
        "verdict": verdict,
        "fingerprint": fingerprint_start,
        "fingerprint_end": fingerprint_end,
        "tree_changed_during_run": fingerprint_start != fingerprint_end,
        "base": base,
        "checks": [asdict(r) for r in results],
    }
