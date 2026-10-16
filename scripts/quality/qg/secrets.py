"""Pattern-based secret detection for diffs, files and hook payloads.

High-signal patterns only (provider key formats, private keys) plus a generic
`secret = "..."` rule gated by an entropy check, so ordinary code and test fixtures do not fire.
Matches are never echoed in full: reports carry a redacted preview.

Suppress a reviewed false positive with the marker `qg-allow-secret` on the same line, or list a
path glob in scripts/quality/secrets-allowlist.txt (one glob per line, # comments).
"""
from __future__ import annotations

import fnmatch
import math
import re
from dataclasses import dataclass
from pathlib import Path

ALLOW_MARKER = "qg-allow-secret"


@dataclass(frozen=True)
class Pattern:
    id: str
    regex: re.Pattern
    description: str
    needs_entropy: bool = False


PATTERNS: tuple[Pattern, ...] = (
    Pattern("private-key", re.compile(r"-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP |ENCRYPTED )?PRIVATE KEY(?: BLOCK)?-----"),
            "private key block"),
    Pattern("google-api-key", re.compile(r"\bAIza[0-9A-Za-z_\-]{35}\b"), "Google API key"),
    Pattern("google-oauth-secret", re.compile(r"\bGOCSPX-[0-9A-Za-z_\-]{28}\b"), "Google OAuth client secret"),
    Pattern("openrouter-key", re.compile(r"\bsk-or-v1-[0-9a-f]{32,}\b"), "OpenRouter API key"),
    Pattern("anthropic-key", re.compile(r"\bsk-ant-[a-z0-9]{2,8}-[A-Za-z0-9_\-]{40,}"), "Anthropic API key"),
    Pattern("openai-key", re.compile(r"\bsk-(?!or-|ant-)(?:proj-)?[A-Za-z0-9_\-]{40,}\b"), "OpenAI-style API key", True),
    Pattern("github-token", re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{60,})\b"), "GitHub token"),
    Pattern("aws-access-key", re.compile(r"\b(?:AKIA|ASIA)[0-9A-Z]{16}\b"), "AWS access key id"),
    Pattern("slack-token", re.compile(r"\bxox[baprs]-[0-9A-Za-z\-]{10,}\b"), "Slack token"),
    Pattern("stripe-live", re.compile(r"\b(?:sk|rk)_live_[0-9A-Za-z]{24,}\b"), "Stripe live key"),
    Pattern("yookassa-key", re.compile(r"\b(?:live|test)_[A-Za-z0-9_\-]{40,}\b"), "YooKassa-style secret key", True),
    Pattern("jwt", re.compile(r"\beyJ[A-Za-z0-9_\-]{15,}\.eyJ[A-Za-z0-9_\-]{15,}\.[A-Za-z0-9_\-]{20,}"), "JSON Web Token"),
    Pattern("generic-assignment", re.compile(
        r"""(?i)\b(?:password|passwd|pwd|secret|client[_-]?secret|api[_-]?key|access[_-]?token|auth[_-]?token|private[_-]?key|store[_-]?password|key[_-]?password)\b["']?\s*[:=]\s*["']([^"'\s$<>{}]{16,})["']"""),
            "hard-coded credential assignment", True),
)

PLACEHOLDER_HINTS = ("example", "placeholder", "changeme", "your", "dummy", "xxxx", "test-", "fake", "sample", "redacted")

# Files that must never be committed, whatever their content.
FORBIDDEN_FILE_GLOBS = (
    "*.jks", "*.keystore", "*.p12", "*.pfx", "*.pem", "*.key",
    ".env", "*/.env", ".env.*", "*/.env.*",
    "gcp-sa-key.json", "*/gcp-sa-key.json", "*service-account*.json",
    "app-android/google-services.json", "local.properties", "*/local.properties",
)
FORBIDDEN_FILE_EXCEPTIONS = ("*.env.example", "*/.env.example", ".env.example")


@dataclass(frozen=True)
class Finding:
    path: str
    line: int
    pattern_id: str
    description: str
    preview: str

    def render(self) -> str:
        return f"{self.path}:{self.line}: {self.description} [{self.pattern_id}] {self.preview}"


def shannon_entropy(s: str) -> float:
    if not s:
        return 0.0
    counts: dict[str, int] = {}
    for ch in s:
        counts[ch] = counts.get(ch, 0) + 1
    return -sum(c / len(s) * math.log2(c / len(s)) for c in counts.values())


def _looks_random(token: str) -> bool:
    low = token.lower()
    if any(h in low for h in PLACEHOLDER_HINTS):
        return False
    classes = sum(bool(re.search(p, token)) for p in (r"[a-z]", r"[A-Z]", r"[0-9]"))
    return classes >= 2 and shannon_entropy(token) >= 3.5


def redact(token: str) -> str:
    if len(token) <= 8:
        return "*" * len(token)
    return f"{token[:4]}...{token[-2:]} (len {len(token)})"


def scan_text(text: str, path: str = "<text>", line_offset: int = 0) -> list[Finding]:
    findings: list[Finding] = []
    for idx, line in enumerate(text.splitlines(), start=1):
        if ALLOW_MARKER in line or len(line) > 4000:
            continue
        for pat in PATTERNS:
            for m in pat.regex.finditer(line):
                token = m.group(1) if m.groups() else m.group(0)
                if pat.needs_entropy and not _looks_random(token):
                    continue
                findings.append(Finding(path, idx + line_offset, pat.id, pat.description, redact(token)))
    return findings


def scan_added_lines(diff: str) -> list[Finding]:
    """Scan only lines a unified diff adds; reports the new-file line numbers."""
    findings: list[Finding] = []
    current = "<diff>"
    new_line = 0
    for raw in diff.splitlines():
        if raw.startswith("+++ "):
            current = raw[6:] if raw.startswith("+++ b/") else raw[4:]
            continue
        if raw.startswith("@@"):
            m = re.search(r"\+(\d+)", raw)
            new_line = int(m.group(1)) if m else 0
            continue
        if raw.startswith("+"):
            for f in scan_text(raw[1:], current):
                findings.append(Finding(f.path, new_line, f.pattern_id, f.description, f.preview))
            new_line += 1
        elif not raw.startswith("-"):
            new_line += 1
    return findings


def is_forbidden_file(rel_path: str) -> bool:
    name_matches = lambda globs: any(fnmatch.fnmatchcase(rel_path, g) for g in globs)
    return name_matches(FORBIDDEN_FILE_GLOBS) and not name_matches(FORBIDDEN_FILE_EXCEPTIONS)


def load_allowlist(root: Path) -> list[str]:
    path = root / "scripts" / "quality" / "secrets-allowlist.txt"
    if not path.exists():
        return []
    return [ln.strip() for ln in path.read_text(encoding="utf-8").splitlines()
            if ln.strip() and not ln.lstrip().startswith("#")]


def path_allowed(rel_path: str, allowlist: list[str]) -> bool:
    return any(fnmatch.fnmatchcase(rel_path, g) for g in allowlist)
