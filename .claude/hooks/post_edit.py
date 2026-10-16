"""PostToolUse checks after Edit/Write: fast, per-file, no build.

- Records the edited file for this session (the Stop hook uses it to decide whether the change
  still needs a quality gate run).
- Blocking problems (secrets, conflict markers, invalid JSON/TOML/frontmatter, dynamic
  dependency versions, broken migration numbering) are returned as `decision: block`, which
  puts the reason next to the tool result so Claude fixes it immediately.
- Reminders (interface implementations, file size, logging, schema/build risk) go to
  additionalContext.
Checks live in scripts/quality/qg/editcheck.py (unit-tested).
"""
from __future__ import annotations

import hashlib
import sys
from pathlib import Path

from _hooklib import PROJECT_ROOT, disabled, emit, read_event, safe_session_id, state_dir

from qg import editcheck  # noqa: E402
from qg.gitutil import to_rel  # noqa: E402


def _new_fragment(tool_input: dict) -> str:
    if "new_string" in tool_input:
        return str(tool_input.get("new_string") or "")
    if "content" in tool_input:
        return str(tool_input.get("content") or "")
    if isinstance(tool_input.get("edits"), list):
        return "\n".join(str(e.get("new_string", "")) for e in tool_input["edits"] if isinstance(e, dict))
    return str(tool_input.get("new_source") or "")


def _unseen(seen_file: Path, reminders: list[str]) -> list[str]:
    """Each reminder text is shown once per session; blocking problems are never deduplicated."""
    seen = set(seen_file.read_text(encoding="utf-8").splitlines()) if seen_file.exists() else set()
    keys = {r: hashlib.sha256(r.encode("utf-8")).hexdigest()[:16] for r in reminders}
    fresh = [r for r in reminders if keys[r] not in seen]
    if fresh:
        with open(seen_file, "a", encoding="utf-8") as fh:
            fh.write("".join(keys[r] + "\n" for r in fresh))
    return fresh


def main() -> int:
    event = read_event()
    if disabled("post_edit"):
        return 0
    tool_input = event.get("tool_input") or {}
    target = tool_input.get("file_path") or tool_input.get("notebook_path")
    if not target:
        return 0
    rel = to_rel(PROJECT_ROOT, str(target))
    if rel is None or rel.startswith(".claude/state/"):
        return 0
    edits_log = state_dir("sessions") / f"{safe_session_id(event)}.edits"
    with open(edits_log, "a", encoding="utf-8") as fh:
        fh.write(rel + "\n")

    findings = editcheck.check_file(PROJECT_ROOT, rel, _new_fragment(tool_input))
    findings.reminders = _unseen(state_dir("sessions") / f"{safe_session_id(event)}.reminded", findings.reminders)
    payload: dict = {}
    if findings.blocking:
        payload["decision"] = "block"
        payload["reason"] = ("V-Tempe post-edit check found problems that must be fixed now:\n- "
                             + "\n- ".join(findings.blocking))
    if findings.reminders:
        payload["hookSpecificOutput"] = {"hookEventName": "PostToolUse",
                                         "additionalContext": "V-Tempe: " + "\nV-Tempe: ".join(findings.reminders)}
    if payload:
        emit(payload)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:  # non-blocking: the edit already happened; surface the failure
        print(f"V-Tempe post-edit hook failed: {type(exc).__name__}: {exc}", file=sys.stderr)
        sys.exit(1)
