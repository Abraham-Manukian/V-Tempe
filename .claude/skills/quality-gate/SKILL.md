---
name: quality-gate
description: Run the V-Tempe quality gate (diff hygiene, secrets, architecture boundaries, build hygiene, Claude config validation, gate self-tests, Gradle compile + unit tests of affected modules, and for the full profile Android Lint and OSV dependency vulnerabilities) and report statuses truthfully. Use before declaring any code change done, before commits, and when the Stop hook asks for it.
argument-hint: "[quick|standard|full] [--base main]"
allowed-tools: Bash(python scripts/quality/quality_gate.py *) Bash(python3 scripts/quality/quality_gate.py *)
---

# /quality-gate

Arguments: $ARGUMENTS (default profile: `standard`, default base: `HEAD` = uncommitted changes).

## Choose the profile
| Profile | Runs | Use when |
| --- | --- | --- |
| `quick` | static checks only (seconds) | only docs/config/`.claude` changed, or a fast pre-check |
| `standard` | quick + compile & unit tests of affected modules and dependents | any code/build change (default) |
| `full` | all modules' tests, debug APK, Android Lint, whole-tree secrets, OSV for direct + resolved deps | dependency/build changes, before releases, `/full-project-audit` |
| `ci` | static checks on the whole tree | used by `.github/workflows/quality-gate.yml` |

Use `--base main` to check everything the branch adds (commits + uncommitted) instead of only uncommitted work.

## Run
```
python scripts/quality/quality_gate.py --profile <profile> [--base <ref>]
```
(`python3` where `python` is not Python 3.11+.) Gradle steps take minutes: run them in the
background when the session allows and wait for completion; never report before it finishes.
List the checks of a profile with `--list`.

## Interpret
- Exit 0 = PASS, 1 = FAIL, 3 = INCOMPLETE (a required check could not run), 2 = usage error.
- Statuses: PASS, FAIL, NOT RUN, NOT CONFIGURED, NOT APPLICABLE. Optional checks (gitleaks,
  semgrep, detekt, ktlint, iOS) never make the verdict PASS or FAIL on their own, but they are listed.
- On FAIL: read the details and the Gradle log path printed in the table; fix the cause; re-run.
  Do not edit the gate, the baseline or the allowlists to get green without the owner's decision.
- To skip a check legitimately (e.g. the owner said no Gradle now):
  `--skip gradle.affected --skip-reason "<why>"` - it is reported NOT RUN and the verdict becomes
  INCOMPLETE. Never describe an INCOMPLETE run as passed.
- Results persist in `.claude/state/quality-gate/last.json` with the code fingerprint; the Stop hook
  accepts the run only for the same fingerprint.

## Report (exact, short)
`Quality gate <profile>: <VERDICT> (exit <code>, fingerprint <fp>)` followed by one line per
non-PASS check with its reason, test counts from the Gradle line, and the log path for failures.
