"""PreToolUse guard: dangerous shell commands and protected file writes need the owner.

Event: PreToolUse, matcher Bash|PowerShell|Edit|Write|MultiEdit|NotebookEdit.
Answers with hookSpecificOutput.permissionDecision "deny" (blocked, the owner must run it by
hand) or "ask" (permission prompt with the reason); prints nothing for safe calls.
Classification lives in scripts/quality/qg/commandrisk.py (unit-tested).
"""
from __future__ import annotations

import sys

from _hooklib import PROJECT_ROOT, disabled, emit, log, read_event

from qg import commandrisk  # noqa: E402  (path set up by _hooklib)
from qg.gitutil import to_rel  # noqa: E402


def main() -> int:
    event = read_event()
    if disabled("guard"):
        return 0
    tool = event.get("tool_name", "")
    tool_input = event.get("tool_input") or {}
    if tool in ("Bash", "PowerShell"):
        decision = commandrisk.classify_command(str(tool_input.get("command", "")))
    elif tool in ("Edit", "Write", "MultiEdit", "NotebookEdit"):
        target = tool_input.get("file_path") or tool_input.get("notebook_path") or ""
        rel = to_rel(PROJECT_ROOT, target) if target else None
        decision = commandrisk.classify_write(rel)
    else:
        return 0
    if decision is None:
        return 0
    reason = f"V-Tempe guard: {decision.reason}."
    verdict = decision.decision
    if verdict == commandrisk.ASK and decision.irreversible and event.get("permission_mode") == "bypassPermissions":
        # Verified on Claude Code 2.1.299: in bypassPermissions an "ask" runs without a prompt, so
        # an irreversible action would get no separate approval. Block it instead.
        verdict = commandrisk.DENY
        reason += (" Blocked: this session is in bypass-permissions mode, where no approval prompt can be shown."
                   " Ask the owner; they run it manually or switch the session to Ask/Accept-edits mode and"
                   " approve the prompt.")
    elif verdict == commandrisk.DENY:
        reason += " Blocked for Claude; if it is really needed, the owner runs it manually."
    log("guard", f"{verdict.upper()} {tool} [{event.get('permission_mode', '?')}]: {decision.reason}")
    emit({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": verdict,
        "permissionDecisionReason": reason,
    }})
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:  # a crashing guard must not silently allow: block with the cause
        print(f"V-Tempe guard crashed ({type(exc).__name__}: {exc}); the call was blocked. "
              f"Fix .claude/hooks/guard.py or set VTEMPE_QG_DISABLE=guard temporarily.", file=sys.stderr)
        sys.exit(2)
