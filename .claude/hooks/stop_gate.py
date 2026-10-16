"""Stop hook: code edited in this session must have a quality gate result for its final state.

Blocks the end of the turn ONCE when files edited in this session (recorded by post_edit.py)
need a gate profile that has not run on the current working-tree fingerprint. It never runs
Gradle itself (that would stall every turn); it tells Claude the exact command.

Loop safety: when Claude Code is already continuing because of a stop hook
(`stop_hook_active`), this hook lets the turn end. Claude Code also caps consecutive stop-hook
continuations. A gate result for the same fingerprint is reused, so nothing re-runs needlessly.
"""
from __future__ import annotations

import sys
import time

from _hooklib import PROJECT_ROOT, disabled, emit, log, read_event, safe_session_id, state_dir

from qg import gitutil, modules  # noqa: E402
from qg import report as rp  # noqa: E402

SESSION_FILE_MAX_AGE_S = 14 * 24 * 3600


def _cleanup(sessions_dir) -> None:
    now = time.time()
    for f in [*sessions_dir.glob("*.edits"), *sessions_dir.glob("*.reminded")]:
        try:
            if now - f.stat().st_mtime > SESSION_FILE_MAX_AGE_S:
                f.unlink()
        except OSError:
            pass


def main() -> int:
    event = read_event()
    if disabled("stop_gate") or event.get("stop_hook_active"):
        return 0
    sessions = state_dir("sessions")
    _cleanup(sessions)
    edits_file = sessions / f"{safe_session_id(event)}.edits"
    if not edits_file.exists():
        return 0
    edited = sorted({ln.strip() for ln in edits_file.read_text(encoding="utf-8").splitlines() if ln.strip()})
    # Only files that still differ from HEAD matter (an edit that was reverted needs nothing).
    try:
        changed = {c.path for c in gitutil.changed_files(PROJECT_ROOT, gitutil.head_sha(PROJECT_ROOT))}
    except gitutil.GitError:
        return 0
    pending = [p for p in edited if p in changed]
    needed = modules.required_profile(pending)
    if needed is None:
        return 0
    fingerprint = gitutil.fingerprint(PROJECT_ROOT)
    last = rp.load_last(PROJECT_ROOT)
    if last and last.get("fingerprint") == fingerprint and \
            modules.PROFILE_RANK.get(last.get("profile"), 0) >= modules.PROFILE_RANK[needed]:
        return 0  # the gate already ran on exactly this code; its verdict is Claude's to report
    code_files = [p for p in pending if modules.category(p) != "other"]
    shown = ", ".join(code_files[:6]) + (f" (+{len(code_files) - 6} more)" if len(code_files) > 6 else "")
    stale = ""
    if last and last.get("fingerprint") != fingerprint:
        stale = f" The last gate run ({last.get('profile')}, {last.get('verdict')}) was on different code."
    reason = (
        f"V-Tempe quality gate: this session changed {shown}, and no '{needed}' (or stronger) gate has run on the "
        f"current code.{stale} Run `python scripts/quality/quality_gate.py --profile {needed}` and report its "
        f"result. If it cannot run here (or the owner said not to), say explicitly in your final message that the "
        f"gate was NOT RUN and why - never present unverified work as verified."
    )
    log("stop_gate", f"BLOCK once: needs {needed} for {len(code_files)} file(s), fingerprint {fingerprint}")
    emit({"decision": "block", "reason": reason})
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:  # never trap the session in a loop because of a hook bug
        print(f"V-Tempe stop hook failed: {type(exc).__name__}: {exc}", file=sys.stderr)
        sys.exit(1)
