"""Helpers shared by the V-Tempe Claude Code hooks (guard.py, post_edit.py, stop_gate.py).

Hooks read one JSON event on stdin and answer with an exit code and optional JSON on stdout
(https://code.claude.com/docs/en/hooks). Every hook here is fast, offline, and never builds a
shell command from event data.
"""
from __future__ import annotations

import json
import os
import sys
import time
from pathlib import Path

HOOK_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = HOOK_DIR.parent.parent
sys.path.insert(0, str(PROJECT_ROOT / "scripts" / "quality"))
sys.dont_write_bytecode = True


def read_event() -> dict:
    raw = sys.stdin.buffer.read().decode("utf-8", "replace")
    try:
        data = json.loads(raw) if raw.strip() else {}
    except json.JSONDecodeError:
        data = {}
    return data if isinstance(data, dict) else {}


def disabled(name: str) -> bool:
    """VTEMPE_QG_DISABLE=guard,post_edit,stop_gate turns individual hooks off (set it in
    .claude/settings.local.json `env` for a reviewed, temporary reason)."""
    flags = {f.strip() for f in os.environ.get("VTEMPE_QG_DISABLE", "").split(",")}
    return name in flags or "all" in flags


def state_dir(*parts: str) -> Path:
    d = PROJECT_ROOT / ".claude" / "state"
    for p in parts:
        d = d / p
    d.mkdir(parents=True, exist_ok=True)
    return d


def emit(payload: dict) -> None:
    sys.stdout.write(json.dumps(payload, ensure_ascii=False))
    sys.stdout.flush()


def log(name: str, line: str) -> None:
    try:
        with open(state_dir() / f"{name}.log", "a", encoding="utf-8") as fh:
            fh.write(f"{time.strftime('%Y-%m-%dT%H:%M:%S')} {line}\n")
    except OSError:
        pass


def safe_session_id(event: dict) -> str:
    sid = str(event.get("session_id") or "unknown")
    return "".join(ch for ch in sid if ch.isalnum() or ch in "-_")[:80] or "unknown"
