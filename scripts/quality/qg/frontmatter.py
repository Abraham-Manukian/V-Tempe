"""Strict parser for the YAML frontmatter subset used by Claude Code agents, skills and rules.

Supported: `key: scalar`, quoted strings, booleans, integers, inline lists `[a, b]` and block
lists (`key:` followed by `  - item` lines). Anything else is reported as an error instead of
being guessed, because Claude Code silently ignores fields it cannot read.
"""
from __future__ import annotations

import re

KEY_RE = re.compile(r"^([A-Za-z_][\w\-]*):(?:\s+(.*))?$")


class FrontmatterError(ValueError):
    pass


def _scalar(raw: str):
    raw = raw.strip()
    if not raw:
        return ""
    if raw[0] in "\"'":
        if len(raw) < 2 or raw[-1] != raw[0]:
            raise FrontmatterError(f"unterminated quoted value: {raw}")
        return raw[1:-1]
    if raw.startswith("["):
        if not raw.endswith("]"):
            raise FrontmatterError(f"unterminated inline list: {raw}")
        inner = raw[1:-1].strip()
        return [_scalar(x) for x in inner.split(",")] if inner else []
    if raw in ("|", ">", "|-", ">-") or raw.startswith(("{", "&", "*", "!")):
        raise FrontmatterError(f"unsupported YAML construct: {raw}")
    low = raw.lower()
    if low in ("true", "yes", "on"):
        return True
    if low in ("false", "no", "off"):
        return False
    if re.fullmatch(r"-?\d+", raw):
        return int(raw)
    if " #" in raw:
        raw = raw.split(" #", 1)[0].rstrip()
    return raw


def split(text: str) -> tuple[str, str]:
    """Returns (frontmatter block, body). Frontmatter must start on the first line."""
    if not text.startswith("---"):
        raise FrontmatterError("file does not start with '---' frontmatter")
    lines = text.splitlines()
    for i in range(1, len(lines)):
        if lines[i].strip() == "---":
            return "\n".join(lines[1:i]), "\n".join(lines[i + 1:])
    raise FrontmatterError("frontmatter is not closed with '---'")


def parse(block: str) -> dict:
    data: dict = {}
    current_list_key: str | None = None
    for lineno, line in enumerate(block.splitlines(), start=2):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if line.startswith((" ", "\t")):
            item = line.strip()
            if current_list_key is None or not item.startswith("- "):
                raise FrontmatterError(f"line {lineno}: unexpected indentation: {line.strip()}")
            data[current_list_key].append(_scalar(item[2:]))
            continue
        m = KEY_RE.match(line)
        if not m:
            raise FrontmatterError(f"line {lineno}: not a 'key: value' line: {line}")
        key, value = m.group(1), m.group(2)
        if key in data:
            raise FrontmatterError(f"line {lineno}: duplicate key '{key}'")
        if value is None or value.strip() == "":
            data[key] = []
            current_list_key = key
        else:
            data[key] = _scalar(value)
            current_list_key = None
    return data


def load(text: str) -> tuple[dict, str]:
    block, body = split(text)
    return parse(block), body


def as_list(value) -> list[str]:
    """Tools/paths fields accept a comma-separated string or a list."""
    if value is None:
        return []
    if isinstance(value, list):
        return [str(v).strip() for v in value if str(v).strip()]
    return [v.strip() for v in str(value).split(",") if v.strip()]
