---
name: pre-commit-review
description: Check the current uncommitted V-Tempe changes before a commit - scope, secrets, unrelated edits, debug leftovers, tests, quality gate - and propose a commit message. Use whenever the user asks to commit, or before suggesting a commit.
---

# /pre-commit-review

## Steps
1. `git status --short` and `git diff HEAD --stat`. Separate the files of this task from
   pre-existing local changes and untracked owner files (screenshots, docs, TODO.md): never stage
   those unless the owner asks. Stage by explicit path, never `git add -A`/`.`/`-f`.
2. Read the full diff of the task's files (`git diff HEAD -- <paths>`, plus new files). Check:
   - unrelated or accidental changes, debug prints, commented-out code, TODOs without context;
   - secrets/keys/tokens, `local.properties`, keystores, `google-services.json`, `.env*` (the gate's
     `secrets.changed` and `diff.hygiene` checks back this up);
   - every changed interface has all implementations updated;
   - tests exist for the changed behaviour.
3. `python scripts/quality/quality_gate.py --profile standard` (or `quick` if only docs/config).
   A FAIL blocks the commit; INCOMPLETE must be stated in the commit discussion with the owner.
4. Risk check: `python scripts/quality/review_bundle.py --base HEAD`. If the computed level is HIGH/CRITICAL
   and the required reviews (`--status`) are missing for this fingerprint, say so before committing
   (run them via `/code-review` if the owner wants).
5. Propose a commit message in English, conventional style used in this repo
   (`fix(scope): ...`, `feat(scope): ...`, `docs(...)`, `test(...)`), body explains why; reference
   audit ids (VT-AUD-NNN) when relevant. No AI attribution lines.
6. Commit only if the owner asked for a commit. Never push (pushing needs the owner's explicit command).

## Report
Files to commit / files left out (and why), gate verdict + exit code, review status, findings that
block, proposed message.
