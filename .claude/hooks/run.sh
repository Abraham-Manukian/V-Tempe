#!/usr/bin/env bash
# Launches a Claude Code hook script with a real Python 3 interpreter.
#
#   run.sh [--fail-closed] <hook-script.py>
#
# The hook's stdin (the event JSON), stdout and exit code pass through unchanged (exec).
# Only the hook scripts that live next to this file can be launched; the name is matched
# against a fixed list, never interpolated into a command.
# Windows: `python3` is often the Microsoft Store stub (WindowsApps), which is skipped.
# --fail-closed: when no interpreter exists, exit 2 (block the tool call) instead of 1
# (non-blocking error). Used for the PreToolUse guard so a broken setup cannot disable it.
set -u

fail_code=1
if [ "${1:-}" = "--fail-closed" ]; then
  fail_code=2
  shift
fi

name="${1:-}"
case "$name" in
  guard.py|post_edit.py|stop_gate.py) ;;
  *) echo "run.sh: unknown hook script '${name}'" >&2; exit "$fail_code" ;;
esac
shift

hook_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

for candidate in python3 python py; do
  exe="$(command -v "$candidate" 2>/dev/null)" || continue
  case "$exe" in
    *WindowsApps*) continue ;;
  esac
  if [ "$candidate" = "py" ]; then
    exec "$exe" -3 -B "$hook_dir/$name" "$@"
  fi
  exec "$exe" -B "$hook_dir/$name" "$@"
done

echo "V-Tempe hook '${name}' cannot run: no Python 3 interpreter on PATH (install Python 3.11+)." >&2
exit "$fail_code"
