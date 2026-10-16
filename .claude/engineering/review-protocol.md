# Review protocol (all reviewer agents)

## Input

The caller gives you the original requirement and points you to the review bundle:

- `.claude/state/review/summary.md` - base, fingerprint, risk level, triggers, last gate result
- `.claude/state/review/changed-files.txt` - status and path of every changed file
- `.claude/state/review/diff.patch` - the diff (untracked text files appear as additions)

Read the summary first, then the diff, then the **full current version** of every changed file you
judge, then the callers/callees you need (Grep/Glob). The diff alone is never enough evidence.
If the bundle is missing or its fingerprint is not the one you were given, answer `BLOCKED`.

## Evidence rules

1. Every finding cites `path:line` in the current code and quotes or precisely paraphrases it.
2. Show the execution path for behavioural claims: entry point -> ... -> defect, each hop with
   `path:line`. "This could be a problem" without a path is not a finding.
3. Label each finding `CONFIRMED` (you traced it in the code) or `PLAUSIBLE` (depends on something
   you could not verify; say what). Never present PLAUSIBLE as CONFIRMED.
4. You cannot run code. Never claim a test passes/fails or a command output you did not see; say
   "not executed" and name the test that would prove it.
5. Do not invent files, functions, configuration or library behaviour. If unsure how a library
   behaves, say so.
6. Stay in your area. Mention an out-of-area problem once, as INFO, for the right reviewer.
7. Respect existing project conventions (CLAUDE.md, `.claude/rules/`); do not flag a documented
   owner decision (e.g. `AI_PAID_ACCESS=everyone` during testing) as a defect - list it as INFO.
8. Do not reveal secret values you come across; report location and type only.

## Output format (exactly this, no preamble)

```
REVIEWER: <agent name>
FINGERPRINT: <from summary.md>   BASE: <base>   RISK: <level>
SCOPE: <what you reviewed, in one or two lines>
VERDICT: PASS | FINDINGS | BLOCKED (<reason>)

FINDINGS:
1. [CRITICAL|HIGH|MEDIUM|LOW|INFO] <one-line title>
   Location: <path:line>
   Evidence: <quote / call path with path:line hops>
   Impact: <concrete failure scenario: input/state -> wrong result>
   Recommendation: <specific fix>
   Status: CONFIRMED | PLAUSIBLE (<what is unverified>)

NOT CHECKED:
- <area you could not cover and why>
```

`FINDINGS:` may be `none`. Severity definitions: `.claude/engineering/risk-matrix.md`. Order
findings by severity. Keep it short: no restating the diff, no praise, no generic advice.
