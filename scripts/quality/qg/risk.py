"""Risk level and reviewer selection for a change, from scripts/quality/risk-rules.toml.

The selection is a deterministic starting point. The orchestrating agent may add a reviewer
with a stated reason, or skip one only when the risk matrix allows it, and says so in the report.
"""
from __future__ import annotations

import fnmatch
import re
import tomllib
from dataclasses import dataclass, field
from pathlib import Path

LEVELS = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
ALWAYS_HIGH = ["qa-test-engineer", "regression-reviewer", "adversarial-code-reviewer"]


@dataclass
class RiskAssessment:
    level: str = "LOW"
    triggers: list[str] = field(default_factory=list)
    specialists: list[str] = field(default_factory=list)  # in order of first appearance
    reviewers: list[str] = field(default_factory=list)  # what the level calls for
    unmatched: list[str] = field(default_factory=list)


def load_rules(root: Path) -> dict:
    return tomllib.loads((root / "scripts" / "quality" / "risk-rules.toml").read_text(encoding="utf-8"))


def _raise(a: RiskAssessment, level: str) -> None:
    if LEVELS.index(level) > LEVELS.index(a.level):
        a.level = level


def _add(a: RiskAssessment, names: list[str]) -> None:
    for n in names:
        if n not in a.specialists:
            a.specialists.append(n)


def assess(rules: dict, paths: list[str], added_lines: list[str]) -> RiskAssessment:
    a = RiskAssessment()
    rule_hits: dict[str, list[str]] = {}
    # Higher-risk rules first so their specialists lead the list.
    path_rules = sorted(rules.get("path_rule", []), key=lambda r: -LEVELS.index(r["risk"]))
    ordered_rules = rules.get("path_rule", [])
    for p in paths:
        rule = next((r for r in ordered_rules if any(fnmatch.fnmatchcase(p, g) for g in r["paths"])), None)
        if rule is None:
            a.unmatched.append(p)
            continue
        rule_hits.setdefault(rule["id"], []).append(p)
    for rule in path_rules:
        if rule["id"] in rule_hits:
            _raise(a, rule["risk"])
            files = rule_hits[rule["id"]]
            more = f" (+{len(files) - 3} more)" if len(files) > 3 else ""
            a.triggers.append(f"{rule['risk']}: {rule['id']} - {rule['reason']} [{', '.join(files[:3])}{more}]")
            _add(a, rule.get("reviewers", []))
    text = "\n".join(added_lines)
    for rule in sorted(rules.get("content_rule", []), key=lambda r: -LEVELS.index(r["risk"])):
        m = re.search(rule["pattern"], text)
        if m:
            _raise(a, rule["risk"])
            a.triggers.append(f"{rule['risk']}: {rule['id']} - {rule['reason']} (added line contains '{m.group(0)}')")
            _add(a, rule.get("reviewers", []))
    a.reviewers = reviewers_for(a.level, a.specialists)
    return a


def reviewers_for(level: str, specialists: list[str]) -> list[str]:
    """LOW: none. MEDIUM: the top specialist + QA. HIGH: all specialists + QA, regression,
    adversarial. CRITICAL: HIGH plus a mandatory security review."""
    if level == "LOW":
        return []
    if level == "MEDIUM":
        top = [s for s in specialists if s not in ("regression-reviewer",)][:1]
        return top + ["qa-test-engineer"]
    chosen = list(specialists)
    if level == "CRITICAL" and "security-reviewer" not in chosen:
        chosen.insert(0, "security-reviewer")
    for r in ALWAYS_HIGH:
        if r not in chosen:
            chosen.append(r)
    return chosen


def added_lines_of(diff: str) -> list[str]:
    return [ln[1:] for ln in diff.splitlines() if ln.startswith("+") and not ln.startswith("+++")]
