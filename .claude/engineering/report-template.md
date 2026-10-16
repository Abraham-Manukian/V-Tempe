# Engineering report

Use after MEDIUM/HIGH/CRITICAL work. For LOW work three lines are enough: what changed, which
tests/gate ran (command + result), what is not verified. The report to the owner is in Russian;
code, commits and repository texts stay in English.

```
## Implementation
What was done and why (2-5 lines). Risk level: <LOW|MEDIUM|HIGH|CRITICAL> - <reason>.

## Files changed
- path - one-line purpose

## Architecture
Decisions and why; contracts touched; backward compatibility.

## Security
Trust boundaries crossed, threats considered, result of security review (or "not required: <why>").

## Performance
What was measured (with numbers) vs. what was only reasoned about.

## Testing
| Command | Result | Exit code |
| --- | --- | --- |
New/changed tests and the behaviour each one pins (regression, boundary, negative, concurrency).

## Independent review
| Reviewer | Verdict | Findings (severity) | Resolution |
| --- | --- | --- | --- |
Findings: severity, location, evidence, what was done.

## Quality gate
Profile, verdict (PASS/FAIL/INCOMPLETE), fingerprint, each non-PASS check with its reason.

## Remaining risks
Unfixed findings, NOT RUN / NOT CONFIGURED checks, untested platforms (iOS, device), owner decisions needed.

## Final status
PASS | PARTIAL | BLOCKED - one line why.
```

Rules:
- PASS only when the gate verdict is PASS for the final code, required reviews have no open
  CRITICAL/HIGH findings, and nothing required is NOT RUN.
- PARTIAL when something required could not run (state what and why) or an accepted risk remains.
- BLOCKED when a CRITICAL/HIGH finding is open or the change does not work.
- Never write a result you did not observe. Quote exit codes and test counts from real output.
