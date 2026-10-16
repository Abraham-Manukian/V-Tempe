# Claude Code engineering quality system (V-Tempe)

Built for Claude Code 2.1.299 (desktop app, Windows) on 2026-10-16. Everything here is plain
files in the repository; nothing is installed globally.

## 1. Architecture

| Layer | Files | Enforced by | What it guarantees |
| --- | --- | --- | --- |
| Permanent rules | `CLAUDE.md` | the model (instructions) | project map, commands, process, architecture/security/testing rules, definition of done |
| Contextual rules | `.claude/rules/*.md` (`paths:` frontmatter) | the model; loaded only when matching files are read/edited | server, KMP client, database, build/CI, tests, the quality system itself |
| Shared procedures | `.claude/engineering/` | referenced by skills and agents | risk matrix + orchestration, review protocol + output format, report template |
| Workflows | `.claude/skills/<name>/SKILL.md` | the model when invoked (`/name` or automatically) | repeatable procedures (section 3) |
| Independent reviewers | `.claude/agents/*.md` | Claude Code tool restrictions | 10 read-only specialists (section 2) |
| Automatic enforcement | `.claude/settings.json` hooks + `.claude/hooks/` | Claude Code runtime | guard before dangerous actions, checks after edits, gate before finishing (section 4) |
| Quality gate | `scripts/quality/` | the scripts' exit codes | real checks with honest statuses (section 5) |
| CI | `.github/workflows/quality-gate.yml` | GitHub Actions | static gate + server tests on every PR and push to main |

Model-followed instructions (CLAUDE.md, rules, skills) shape behaviour but can be ignored by a
model; hooks, permission rules, the gate's exit codes and CI are enforced mechanically. The
report always says which kind of guarantee a claim rests on.

## 2. Agents (all read-only)

Every reviewer has `tools: Read, Grep, Glob` (dependency-reviewer also `WebFetch, WebSearch`), no
shell and no write tools, so parallel reviewers cannot conflict. They read the review bundle
(`python scripts/quality/review_bundle.py --base main` -> `.claude/state/review/`) instead of
running git. Output format, evidence rules and severities: `.claude/engineering/review-protocol.md`.

| Agent | Model | Use for |
| --- | --- | --- |
| `architecture-reviewer` | sonnet | module boundaries, layering, Koin DI, contracts, needless complexity |
| `security-reviewer` | opus | authn/authz, IDOR, injection, secrets/PII, payments/webhooks, races, OWASP/ASVS |
| `performance-reviewer` | sonnet | complexity, blocking calls, coroutines, N+1, pools, retries/timeouts; measured vs potential |
| `clean-code-reviewer` | sonnet | readability, duplication, size, encapsulation, error handling - project conventions only |
| `qa-test-engineer` | sonnet | coverage of changed behaviour, missing scenarios, TEST PLAN (does not write tests) |
| `database-reviewer` | sonnet | SQLDelight migrations, Exposed transactions, integrity, concurrency, indexes |
| `api-contract-reviewer` | sonnet | server/client DTO compatibility, status codes, validation, idempotency, route auth |
| `dependency-reviewer` | sonnet | catalog, versions, OSV results, transitive risk, licenses, wrapper, Docker/CI supply chain |
| `regression-reviewer` | sonnet | callers, user flows, stored data, deployed clients, test strength |
| `adversarial-code-reviewer` | opus (effort high) | final independent attempt to break HIGH/CRITICAL changes |

Invoke explicitly ("use the security-reviewer subagent on the bundle") or let a skill do it.
Which agents run is decided by the risk level (section 6), not by habit.

## 3. Skills

| Skill | Purpose | Example |
| --- | --- | --- |
| `/engineering-task` | full cycle Research -> Plan -> Implement -> Test -> Review -> Verify | `/engineering-task add pagination to GET /sync/diary` |
| `/bug-investigation` | reproduce, root cause, fix, fail-before/pass-after regression test | `/bug-investigation chat swap ignores injuries` |
| `/test-generator` | contract-based tests in the right source set, run them | `/test-generator YooKassaWebhookService refunds` |
| `/quality-gate` | run and interpret the gate | `/quality-gate standard` |
| `/pre-commit-review` | scope, secrets, gate, commit message before a commit | `/pre-commit-review` |
| `/code-review` | risk-based multi-agent review vs a base branch | `/code-review main` |
| `/security-audit` | threat model + security-reviewer on a diff or an area | `/security-audit server/src/main/kotlin/com/vtempe/server/features/payments` |
| `/architecture-review` | arch.boundaries + architecture-reviewer | `/architecture-review shared` |
| `/performance-audit` | measure, then review, then fix with numbers | `/performance-audit sync PATCH latency` |
| `/regression-check` | impact map + affected tests + regression-reviewer | `/regression-check` |
| `/full-project-audit` | whole-repo audit, only on explicit request (`disable-model-invocation`) | `/full-project-audit security` |

The project `/code-review` replaces the bundled generic `/code-review`; the bundled one is still
available as `/review`.

## 4. Hooks (automatic)

Configured in `.claude/settings.json`, launched through `.claude/hooks/run.sh` (finds a real
Python 3, skips the Windows Store `python3` stub, only runs the three known scripts). Logic lives
in `scripts/quality/qg/` and is unit-tested.

| Event | Script | Behaviour |
| --- | --- | --- |
| PreToolUse (Bash, PowerShell, Edit, Write, MultiEdit, NotebookEdit) | `guard.py` | **deny**: force-push to main/master, recursive delete of repo/home/drive root, `--dangerously-skip-permissions`, disk formatting. **ask**: any `git push`, history rewrites, `reset --hard`, `clean -f`, discarding checkouts/restores, gcloud/firebase/docker/kubectl/terraform changes, secret reads (`.env`, keystores, `local.properties`, `gcloud secrets ... access`), env dumps, uploads, `curl ... \| sh`, non-local databases, `--no-verify`/hooksPath, edits to deploy config, workflows, Dockerfile, wrapper, `.claude/settings*.json`, `.claude/hooks/`. In **bypassPermissions** mode irreversible "ask" actions are turned into **deny** (see section 9). Fails closed: if Python is missing or the guard crashes, the call is blocked with the cause. |
| PostToolUse (Edit, Write, MultiEdit, NotebookEdit) | `post_edit.py` | records the edited file for the session; **blocks** (reason shown next to the tool result) on secrets, conflict markers, invalid JSON/TOML/frontmatter, dynamic dependency versions, insecure repos, non-contiguous SQLDelight migrations; **reminds** (once per session) about interface implementations, files over ~300 lines, `println` in server code, `GlobalScope`/`runBlocking`, schema/build risk |
| Stop | `stop_gate.py` | if this session edited code/config that still differs from HEAD and no gate of the required profile ran on the current fingerprint, blocks the stop **once** with the exact command. Respects `stop_hook_active` (no loops); a gate result for the same fingerprint is reused, so nothing re-runs needlessly. Never runs Gradle itself. |

Permission rules in `settings.json` back the guard: `deny` reads of `.env*`, keystores,
service-account keys; `ask` for `local.properties`, `git push`, `gcloud run deploy|builds submit|sql|secrets`,
`docker push`, `firebase deploy`, history rewrites.

## 5. Quality gate

`python scripts/quality/quality_gate.py --profile <quick|standard|full|ci> [--base <ref>]`
(`python3` on macOS/Linux). Exit codes: 0 PASS, 1 FAIL, 3 INCOMPLETE, 2 usage error.
Statuses: PASS, FAIL, NOT RUN, NOT CONFIGURED, NOT APPLICABLE. A required check that did not run
makes the verdict INCOMPLETE - never PASS. Results: `.claude/state/quality-gate/last.json`.

| Check | quick | standard | full | ci | What it does |
| --- | --- | --- | --- | --- | --- |
| `diff.hygiene` | x | x | x | x | `git diff --check`, conflict markers, forbidden files (keystores, `.env`, `local.properties`), staged files > 2 MiB |
| `secrets.changed` / `secrets.tree` | x | x | tree | tree | provider key formats, private keys, JWTs, entropy-gated `secret = "..."` |
| `arch.boundaries` | x | x | x | x | module graph vs allowed edges, layer import rules, baseline of known violations |
| `deps.hygiene` | x | x | x | x | pinned catalog versions, reviewed repositories, wrapper checksum |
| `config.claude` | x | x | x | x | agents/skills/rules frontmatter, read-only reviewers, references, hooks wiring, CLAUDE.md size |
| `config.workflows` | if changed | if changed | x | x | workflow YAML parses and has jobs (needs PyYAML: NOT RUN locally, runs in CI) |
| `python.selftest` | if changed | if changed | x | x | 39 unittest cases of the gate and hooks |
| `gradle.affected` |  | x |  |  | compile + unit tests of changed modules and their dependents |
| `gradle.all` |  |  | x |  | all unit tests, commonMain metadata, debug APK |
| `android.lint` |  |  | x |  | `:app-android:lintDebug` |
| `deps.osv` |  |  | x |  | OSV lookup of catalog + resolved server/Android runtime deps (network; NOT RUN offline) |
| `ios.compile` |  | optional | optional |  | NOT RUN off macOS |
| `tools.gitleaks` / `tools.semgrep` |  | optional | optional |  | run when installed, else NOT CONFIGURED |
| `lint.detekt` / `lint.ktlint` |  |  | optional |  | NOT CONFIGURED (declared in the catalog, applied to no module) |

Gradle environment is set by the gate: JDK 17+ (`QG_JAVA_HOME` or auto-detected), and on Windows
`-Djdk.net.unixdomain.tmpdir=~/.gradle/uds` (without it Gradle fails with "Unable to establish
loopback connection"). Extra Gradle flags: `QG_GRADLE_ARGS="--offline --max-workers=2"`.
PostgreSQL integration tests run inside `:server:test` only when `VTEMPE_PG_TEST_URL` is set; the
gate says so when it is not.

Optional tools worth adding later (not installed now): `gitleaks` (git-history secret scan) and
`semgrep` (SAST) - the gate picks them up automatically from PATH; detekt/ktlint need a Gradle
change and a baseline, which is an owner decision (it touches every module).

## 6. How a task flows

Risk levels and reviewer sets: `.claude/engineering/risk-matrix.md`; computed suggestion:
`python scripts/quality/review_bundle.py --base main` (rules in `scripts/quality/risk-rules.toml`:
first matching path rule per file, plus content signals such as `Mutex`, `transaction {`,
`MessageDigest` in added Kotlin lines).

**Ordinary (LOW/MEDIUM) task:** classify -> read the code and tests -> short plan -> implement
(PostToolUse catches mechanical problems) -> tests -> `quality_gate.py --profile standard` -> for
MEDIUM one specialist + `qa-test-engineer` -> short report. The Stop hook refuses to end the turn
until a gate ran on the final code (once; then Claude must report NOT RUN explicitly).

**HIGH / CRITICAL task (`/engineering-task`):** as above, plus: CRITICAL starts with a threat model
(`/security-audit`); after the gate all required specialists run in parallel on the bundle; fixes;
gate again; `adversarial-code-reviewer` alone on the final diff; reviews recorded per fingerprint
(`review_bundle.py --record`, `--status`) so nothing is re-run on unchanged code; report with
remaining risks; push/deploy only on the owner's command.

**Full audit:** `/full-project-audit` (runs `--profile full`, then area reviews in parallel, then
a consolidated report). Takes minutes of Gradle and many agent tokens - on request only.

## 7. Disabling a check safely

- One gate check for one run: `--skip <id> --skip-reason "<why>"` -> reported NOT RUN, verdict
  INCOMPLETE. There is no way to turn a skipped check into PASS.
- A hook, temporarily, on one machine: add to `.claude/settings.local.json`
  `{"env": {"VTEMPE_QG_DISABLE": "stop_gate"}}` (comma list of `guard`, `post_edit`, `stop_gate`,
  or `all`). `config.claude` reports it as a note. Remove it when done. Note that
  `.claude/settings.local.json` is tracked in this repository - do not commit such a change.
- A known architecture violation: a line in `scripts/quality/arch-baseline.txt` (owner decision).
- A reviewed secret false positive: `qg-allow-secret` on the same line, or a glob in
  `scripts/quality/secrets-allowlist.txt`.
- Never: `disableAllHooks`, `--dangerously-skip-permissions`, `--no-verify` (the guard asks/denies).

## 8. Extending

**New agent:** create `.claude/agents/<name>.md` with `name` (= file name), a precise
`description` (when to use), an explicit `tools` list (reviewers: `Read, Grep, Glob` only),
`model`, and a prompt that points to `.claude/engineering/review-protocol.md`. Add it to the
relevant `reviewers` lists in `scripts/quality/risk-rules.toml` and to the risk matrix. Run the
quick gate: `config.claude` validates fields, tools and references. A brand-new
`.claude/agents/` directory is only picked up after restarting Claude Code; new files in an
existing directory within seconds.

**New skill:** `.claude/skills/<name>/SKILL.md`, `description` <= 1536 chars, reference shared docs
instead of copying them, real commands only. Run the quick gate.

**New gate check:** a function in `scripts/quality/quality_gate.py` returning `CheckResult` with an
honest status, an entry in `plan()`, and a test in `scripts/quality/tests/`.

## 9. Verified behaviour and limits (2026-10-16, Claude Code 2.1.299, bypassPermissions session)

- Hooks hot-reloaded in the running session after `settings.json` was written: the guard logged
  decisions (`.claude/state/guard.log`) and PostToolUse context appeared on the next edits.
- `deny` from the guard blocked a force-push live. A hook `ask` and a settings `ask` rule
  (`Bash(git push *)`) did **not** show a prompt in bypassPermissions mode - the commands ran
  (they were harmless test commands against a non-existent directory/remote). Therefore the guard
  converts irreversible `ask` decisions into `deny` when `permission_mode` is `bypassPermissions`;
  reversible ones (amend, rebase, editing tracked config files) stay `ask`. In Ask/Accept-edits/auto
  modes a real prompt appears. To push from Claude, switch the session out of bypass mode and
  approve the prompt, or run the push yourself.
- The guard parses commands conservatively; it is protection against mistakes, not a sandbox
  (e.g. a Python script that deletes files is not inspected). Permission rules and code review
  remain necessary.
- Agents and skills were validated statically (`config.claude`) and are listed by Claude Code
  only after a restart, because `.claude/agents/` and `.claude/skills/` were new directories.
  Headless `claude -p` is not logged in on this machine, so routing could not be exercised from a
  script; check it in a new session with "/agents"-style questions or by invoking a skill.
- Workflow YAML is parsed only where PyYAML exists (CI installs it); locally that part is NOT RUN.
- iOS targets are never compiled on Windows; the gate reports `ios.compile` NOT RUN.

## 10. Diagnosing configuration problems

| Symptom | Check |
| --- | --- |
| Hook never fires | `.claude/settings.json` valid JSON (`python -m json.tool .claude/settings.json`); `claude doctor`; Git Bash present (hooks use `shell: bash`); run `bash .claude/hooks/run.sh guard.py <<< '{"tool_name":"Bash","tool_input":{"command":"git push"}}'` |
| Every tool call blocked by "guard crashed" | read the stderr cause; run the unit tests; temporary escape: `VTEMPE_QG_DISABLE=guard` in settings.local.json `env` |
| Agent/skill not listed | restart Claude Code (new directory); `python scripts/quality/quality_gate.py --profile quick` -> `config.claude` errors; `claude --debug` and search for "does not recognize or apply" |
| Gradle "Unable to establish loopback connection" | the gate sets `JAVA_TOOL_OPTIONS`; for manual runs add `-Djdk.net.unixdomain.tmpdir=<short dir>` |
| Gradle `ClassFormatError` in the Kotlin compiler | corrupted jars in `~/.gradle/caches/modules-2` (happened 2026-10-10); verify CRCs |
| Stop hook keeps asking | run the gate on the current code; edits after the gate change the fingerprint by design |
| Gate result looks stale | `.claude/state/quality-gate/last.json` -> `fingerprint`, `tree_changed_during_run` |

## 11. Practical examples

The scenarios are illustrative walk-throughs of the process, not reports of real defects.

**1. Ordinary bug: "the workout timer shows minutes for bike in seconds".**
`/bug-investigation bike calibration unit` -> reproduce with a presenter test in
`ui/src/androidUnitTest` -> root cause in the shared calibration mapping (`path:line`) -> fix ->
prove the test fails on the old code (backup/restore, no `git checkout`) and passes now ->
`quality_gate.py --profile standard` runs `:shared` + `:ui` + `:app-android` tests -> risk MEDIUM:
`review_bundle.py` suggests `clean-code-reviewer` or `architecture-reviewer` + `qa-test-engineer`,
both run in parallel -> short report. No adversarial review, no security review.

**2. New API: "GET /me/plans/history with pagination".**
`/engineering-task ...` -> risk HIGH (http-api rule) -> research the route registration, the
auth plugin scope, the Exposed tables and the client network layer -> plan with DTOs on both sides
and backward compatibility -> implement route + repository + client call -> tests: Ktor
`testApplication` for 200/400 (bad cursor)/401 (no token)/other user's data not visible, page
boundaries, empty history -> gate standard -> bundle -> `api-contract-reviewer`,
`security-reviewer`, `database-reviewer` (+ `performance-reviewer` if queries are new), then
`qa-test-engineer` and `regression-reviewer` in parallel -> fixes -> gate -> `adversarial-code-reviewer`
-> report with the compatibility statement.

**3. Authorization vulnerability: "sync PATCH accepts a diary owned by another user".**
`/security-audit server/.../features/sync` -> threat model (asset: other users' diaries; actor:
signed-in user; boundary: route -> repository) -> `security-reviewer` + `database-reviewer` trace
the id from the request to the UPDATE -> fix: subject from `call.attributes[UserIdKey]` only,
ownership enforced in the `WHERE` of the guarded update -> negative tests: other user's id ->
404, no write happened; concurrent PATCH from two devices still merges -> gate standard (and
`PostgresIntegrationTest` with the local container, because the fix is in SQL) -> CRITICAL
process: `adversarial-code-reviewer` on the final diff -> report lists the threat model, tests,
remaining risks; no exploit details are written into repository docs; push only on the owner's command.
