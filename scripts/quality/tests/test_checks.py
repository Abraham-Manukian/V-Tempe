"""Unit tests of the static checks: secrets, frontmatter, config validation, risk, modules,
build hygiene, per-edit checks. Fixtures that look like secrets are assembled at runtime so this
file itself never contains one."""
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from qg import configcheck, deps, editcheck, frontmatter, modules, risk  # noqa: E402
from qg import secrets as sec  # noqa: E402

GOOGLE_KEY = "AIza" + "Sy" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q"  # 4 + 35 chars, like a real key
OPENROUTER_KEY = "sk-or-v1-" + "0123456789abcdef" * 4
PRIVATE_KEY_HEADER = "-----BEGIN " + "RSA PRIVATE KEY-----"


def write(root: Path, rel: str, text: str) -> Path:
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(textwrap.dedent(text).lstrip("\n"), encoding="utf-8")
    return p


class SecretsTest(unittest.TestCase):
    def test_detects_provider_keys_and_redacts(self):
        text = f'val k = "{GOOGLE_KEY}"\nOPENROUTER={OPENROUTER_KEY}\n{PRIVATE_KEY_HEADER}\n'
        findings = sec.scan_text(text, "x.kt")
        ids = {f.pattern_id for f in findings}
        self.assertTrue({"google-api-key", "openrouter-key", "private-key"} <= ids, ids)
        for f in findings:
            self.assertNotIn(GOOGLE_KEY, f.render())
            self.assertNotIn(OPENROUTER_KEY, f.render())

    def test_generic_assignment_needs_entropy(self):
        self.assertEqual([], sec.scan_text('password = "changeme-placeholder-value"', "a.kt"))
        self.assertEqual([], sec.scan_text('val apiKey = System.getenv("OPENROUTER_API_KEY")', "a.kt"))
        hits = sec.scan_text('client_secret = "' + "Zx8Qw3Lp0Rt7" + 'Vb2Nm5Kd9Hs4"', "a.kt")
        self.assertEqual(["generic-assignment"], [f.pattern_id for f in hits])

    def test_allow_marker_and_added_lines(self):
        self.assertEqual([], sec.scan_text(f'"{GOOGLE_KEY}" // {sec.ALLOW_MARKER}', "a.kt"))
        diff = f"+++ b/a.kt\n@@ -1,2 +1,3 @@\n keep\n+val k = \"{GOOGLE_KEY}\"\n-gone\n"
        found = sec.scan_added_lines(diff)
        self.assertEqual([("a.kt", 2)], [(f.path, f.line) for f in found])

    def test_forbidden_files(self):
        for rel in ("vtempe-release.jks", "server/.env.local", ".env", "gcp-sa-key.json", "local.properties"):
            self.assertTrue(sec.is_forbidden_file(rel), rel)
        for rel in ("server/.env.example", "docs/env.md", "app-android/build.gradle.kts"):
            self.assertFalse(sec.is_forbidden_file(rel), rel)


class FrontmatterTest(unittest.TestCase):
    def test_parses_supported_subset(self):
        meta, body = frontmatter.load("---\nname: a\ntools: Read, Grep\nmaxTurns: 5\nflag: true\n"
                                      "paths:\n  - \"server/**\"\n  - x\n---\nBody\n")
        self.assertEqual({"name": "a", "tools": "Read, Grep", "maxTurns": 5, "flag": True,
                          "paths": ["server/**", "x"]}, meta)
        self.assertEqual("Body", body.strip())

    def test_rejects_unsupported_constructs(self):
        for bad in ("---\ndescription: >\n  folded\n---\n", "---\nname: a\nname: b\n---\n", "no frontmatter",
                    "---\nname: a\n"):
            with self.subTest(bad=bad), self.assertRaises(frontmatter.FrontmatterError):
                frontmatter.load(bad)


class ConfigCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        write(self.root, "CLAUDE.md", "# x\nSee `.claude/engineering/missing.md`.\n")

    def tearDown(self):
        self.tmp.cleanup()

    def test_reviewer_with_write_tools_and_unknown_field_fail(self):
        write(self.root, ".claude/agents/security-reviewer.md",
              "---\nname: security-reviewer\ndescription: d\ntools: Read, Edit, Bash\ncolour: red\n---\nPrompt\n")
        write(self.root, ".claude/agents/helper.md", "---\nname: helper\ndescription: d\n---\nPrompt\n")
        rep = configcheck.run(self.root)
        joined = "\n".join(rep.errors)
        self.assertIn("read-only reviewer has write-capable tools ['Bash', 'Edit']", joined)
        self.assertIn("unknown frontmatter field 'colour'", joined)
        self.assertIn("helper.md: no explicit 'tools'", joined)
        self.assertIn("references missing path '.claude/engineering/missing.md'", joined)

    def test_valid_agent_and_skill_pass(self):
        write(self.root, "CLAUDE.md", "# x\n")
        write(self.root, ".claude/agents/qa-test-engineer.md",
              "---\nname: qa-test-engineer\ndescription: d\ntools: Read, Grep, Glob\nmodel: sonnet\n---\nPrompt\n")
        write(self.root, ".claude/skills/demo/SKILL.md",
              "---\nname: demo\ndescription: Demo skill\n---\nUse qa-test-engineer.\n")
        write(self.root, ".claude/settings.json", '{"hooks": {"Stop": [{"hooks": [{"type": "command", "command": "x"}]}]}}')
        rep = configcheck.run(self.root)
        self.assertEqual([], rep.errors)
        self.assertIn("qa-test-engineer", rep.agents)

    def test_skill_referencing_unknown_agent_and_bad_settings_fail(self):
        write(self.root, "CLAUDE.md", "# x\n")
        write(self.root, ".claude/skills/demo/SKILL.md", "---\ndescription: d\n---\nRun ghost-reviewer.\n")
        write(self.root, ".claude/settings.json",
              '{"disableAllHooks": true, "hooks": {"Stopp": []}, "permissions": {"ask": ["Bash(git push *"]}}')
        rep = configcheck.run(self.root)
        joined = "\n".join(rep.errors)
        self.assertIn("unknown agent 'ghost-reviewer'", joined)
        self.assertIn("unknown hook event 'Stopp'", joined)
        self.assertIn("disableAllHooks is true", joined)
        self.assertIn("malformed permissions.ask rule", joined)


class ModulesAndRiskTest(unittest.TestCase):
    def test_affected_modules_include_dependents(self):
        self.assertEqual({"shared", "ui", "app-android", "app-ios"},
                         modules.affected_modules(["shared/src/commonMain/kotlin/A.kt"]))
        self.assertEqual({"server", "shared", "ui", "app-android", "app-ios"},
                         modules.affected_modules(["shared/src/commonMain/kotlin/com/vtempe/shared/domain/exercise/ExerciseLibrary.kt"]))
        self.assertEqual(set(modules.MODULES), modules.affected_modules(["gradle/libs.versions.toml"]))
        self.assertEqual(set(), modules.affected_modules(["docs/x.md", "README.md"]))

    def test_required_profile(self):
        self.assertEqual("standard", modules.required_profile(["server/src/main/kotlin/A.kt"]))
        self.assertEqual("quick", modules.required_profile([".claude/agents/x.md"]))
        self.assertIsNone(modules.required_profile(["docs/notes.md"]))

    def test_risk_levels(self):
        rules = risk.load_rules(Path(__file__).resolve().parents[3])
        pay = risk.assess(rules, ["server/src/main/kotlin/com/vtempe/server/features/payments/yookassa/api/Y.kt"], [])
        self.assertEqual("CRITICAL", pay.level)
        self.assertIn("security-reviewer", pay.reviewers)
        self.assertIn("adversarial-code-reviewer", pay.reviewers)
        low = risk.assess(rules, ["docs/x.md", "server/src/test/kotlin/T.kt"], [])
        self.assertEqual(("LOW", []), (low.level, low.reviewers))
        med = risk.assess(rules, ["ui/src/commonMain/kotlin/com/vtempe/ui/presenter/HomePresenter.kt"], [])
        self.assertEqual("MEDIUM", med.level)
        self.assertEqual(2, len(med.reviewers))
        conc = risk.assess(rules, ["ui/src/commonMain/kotlin/X.kt"], ["val m = Mutex()"])
        self.assertEqual("HIGH", conc.level)
        self.assertIn("performance-reviewer", conc.reviewers)


class DepsTest(unittest.TestCase):
    def test_dynamic_versions_and_http_repos_fail(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            write(root, "gradle/libs.versions.toml", '[versions]\nktor = "2.+"\n[libraries]\n'
                  'a = { module = "x.y:z", version.ref = "ktor" }\nb = { module = "q.r:s", version = "1.0" }\n')
            write(root, "gradle/wrapper/gradle-wrapper.properties",
                  "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-bin.zip\n")
            write(root, "settings.gradle.kts", 'repositories { maven("http://evil.example/repo") }\n')
            rep = deps.run(root)
            joined = "\n".join(rep.errors)
            self.assertIn("dynamic version ktor = 2.+", joined)
            self.assertIn("insecure repository http://evil.example/repo", joined)
            self.assertIn("distributionSha256Sum missing", joined)
            self.assertIn(("q.r:s", "1.0"), rep.coordinates)

    def test_real_project_build_is_clean(self):
        rep = deps.run(Path(__file__).resolve().parents[3])
        self.assertEqual([], rep.errors)


class EditCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_secret_and_conflict_block(self):
        write(self.root, "server/src/main/kotlin/A.kt", f'val k = "{GOOGLE_KEY}"\n<<<<<<< HEAD\n')
        res = editcheck.check_file(self.root, "server/src/main/kotlin/A.kt")
        self.assertEqual(2, len(res.blocking), res.blocking)

    def test_interface_change_lists_implementations(self):
        write(self.root, "ui/src/commonMain/kotlin/HomePresenter.kt",
              "interface HomePresenter {\n    fun refresh()\n}\n")
        write(self.root, "ui/src/androidMain/kotlin/HomeViewModel.kt",
              "class HomeViewModel(x: Int) : ViewModel(), HomePresenter {\n}\n")
        res = editcheck.check_file(self.root, "ui/src/commonMain/kotlin/HomePresenter.kt", "    fun refresh()")
        self.assertEqual([], res.blocking)
        self.assertTrue(any("ui/src/androidMain/kotlin/HomeViewModel.kt" in r for r in res.reminders), res.reminders)

    def test_migration_gap_and_bad_json_block(self):
        for n in (1, 2, 4):
            write(self.root, f"shared/src/commonMain/sqldelight/db/{n}.sqm", "ALTER TABLE x ADD y INTEGER;\n")
        res = editcheck.check_file(self.root, "shared/src/commonMain/sqldelight/db/4.sqm")
        self.assertTrue(any("not contiguous" in b for b in res.blocking), res.blocking)
        write(self.root, ".claude/settings.json", "{ broken")
        self.assertTrue(editcheck.check_file(self.root, ".claude/settings.json").blocking)

    def test_dynamic_dependency_blocks(self):
        write(self.root, "server/build.gradle.kts", 'dependencies { implementation("io.ktor:ktor-core:2.+") }\n')
        res = editcheck.check_file(self.root, "server/build.gradle.kts")
        self.assertTrue(any("dynamic dependency version" in b for b in res.blocking), res.blocking)

    def test_clean_kotlin_has_no_blocking(self):
        write(self.root, "server/src/main/kotlin/B.kt", "fun ok() = 1\n")
        self.assertEqual([], editcheck.check_file(self.root, "server/src/main/kotlin/B.kt").blocking)


if __name__ == "__main__":
    unittest.main()


class GradleResultsTest(unittest.TestCase):
    def test_junit_failures_are_counted_per_requested_task(self):
        from qg import gradle
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            write(root, "server/build/test-results/test/TEST-A.xml",
                  '<testsuite tests="3" failures="1" errors="0" skipped="1">'
                  '<testcase classname="A" name="ok"/><testcase classname="A" name="bad"><failure/></testcase>'
                  '<testcase classname="A" name="skip"><skipped/></testcase></testsuite>')
            write(root, "shared/build/test-results/testDebugUnitTest/TEST-B.xml",
                  '<testsuite tests="5" failures="0" errors="0" skipped="0"/>')
            tests, failures, errors, skipped, failed = gradle._junit_results(root, [":server:test"])
            self.assertEqual((3, 1, 0, 1, ["A.bad"]), (tests, failures, errors, skipped, failed))
            self.assertEqual(8, gradle._junit_results(root, [":server:test", ":shared:testDebugUnitTest"])[0])
            self.assertEqual(0, gradle._junit_results(root, [":app-android:lintDebug"])[0])
