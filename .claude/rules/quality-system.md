---
paths:
  - ".claude/**"
  - "scripts/quality/**"
  - "CLAUDE.md"
---

# Rules for changing the quality system itself

- Documentation of the system: `docs/CLAUDE_ENGINEERING_SYSTEM.md`. Keep it in sync with every change.
- Reviewer agents stay read-only (`tools: Read, Grep, Glob` plus web tools where justified). The
  `config.claude` gate check fails on a reviewer with Edit/Write/Bash/PowerShell/Agent.
- Every agent declares `tools` explicitly; unknown frontmatter fields fail the gate (Claude Code
  silently ignores them).
- Hooks never build shell commands from event data, never use `|| true` to hide failures, and keep
  their logic in `scripts/quality/qg/` where it is unit-tested. The guard fails closed (exit 2).
- A check that did not execute is NOT RUN, never PASS. Do not add a "skip" that reports PASS.
- After any change here: `python -B -m unittest discover -s scripts/quality/tests -t scripts/quality`
  and `python scripts/quality/quality_gate.py --profile quick`.
- Adding a known architecture violation to `scripts/quality/arch-baseline.txt` needs the owner's decision.
