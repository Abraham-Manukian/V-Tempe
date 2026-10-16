"""End-to-end tests in a throwaway git repository: the real hook scripts read real event JSON on
stdin, and the real gate distinguishes PASS, FAIL and INCOMPLETE by exit code. Nothing touches
the working repository; every fixture lives in a temporary directory."""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
GOOGLE_KEY = "AIza" + "Sy" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q"


def _git(root: Path, *args: str) -> None:
    subprocess.run(["git", *args], cwd=root, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def _real_bash() -> str | None:
    """Git Bash / Linux bash; never the WSL launcher in System32 (it cannot read Windows paths)."""
    candidates = [shutil.which("bash")]
    if os.name == "nt":
        candidates += [r"C:\Program Files\Git\bin\bash.exe", r"C:\Program Files\Git\usr\bin\bash.exe"]
    for c in candidates:
        if c and Path(c).exists() and "system32" not in c.lower():
            return c
    return None


class Fixture:
    def __init__(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name) / "repo"
        self.root.mkdir()
        shutil.copytree(REPO / "scripts" / "quality", self.root / "scripts" / "quality",
                        ignore=shutil.ignore_patterns("__pycache__"))
        shutil.copytree(REPO / ".claude" / "hooks", self.root / ".claude" / "hooks",
                        ignore=shutil.ignore_patterns("__pycache__"))
        (self.root / ".gitignore").write_text(".claude/state/\n__pycache__/\n", encoding="utf-8")
        (self.root / "CLAUDE.md").write_text("# fixture\n", encoding="utf-8")
        wrapper = self.root / "gradle" / "wrapper"
        wrapper.mkdir(parents=True)
        (wrapper / "gradle-wrapper.properties").write_text(
            "distributionSha256Sum=" + "0" * 64 + "\n"
            "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-bin.zip\n", encoding="utf-8")
        (self.root / "gradle" / "libs.versions.toml").write_text('[versions]\nk = "1.0"\n', encoding="utf-8")
        _git(self.root, "init", "-q")
        _git(self.root, "-c", "user.name=t", "-c", "user.email=t@t", "add", "-A")
        _git(self.root, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "-m", "fixture")

    def write(self, rel: str, text: str) -> Path:
        p = self.root / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding="utf-8")
        return p

    def gate(self, *args: str) -> subprocess.CompletedProcess:
        return subprocess.run([sys.executable, "-B", str(self.root / "scripts/quality/quality_gate.py"), *args],
                              cwd=self.root, capture_output=True, text=True, encoding="utf-8")

    def hook(self, name: str, event: dict, env: dict | None = None) -> subprocess.CompletedProcess:
        return subprocess.run([sys.executable, "-B", str(self.root / ".claude/hooks" / name)],
                              input=json.dumps(event), capture_output=True, text=True, encoding="utf-8",
                              cwd=self.root, env={**os.environ, **(env or {})})

    def close(self):
        self.tmp.cleanup()


class GateExitCodes(unittest.TestCase):
    def setUp(self):
        self.fx = Fixture()

    def tearDown(self):
        self.fx.close()

    def test_clean_change_passes(self):
        self.fx.write("docs/notes.md", "plain notes\n")
        res = self.fx.gate("--profile", "quick")
        self.assertEqual(0, res.returncode, res.stdout + res.stderr)
        self.assertIn("QUALITY GATE: PASS", res.stdout)
        last = json.loads((self.fx.root / ".claude/state/quality-gate/last.json").read_text(encoding="utf-8"))
        self.assertEqual(("quick", "PASS"), (last["profile"], last["verdict"]))

    def test_planted_secret_fails(self):
        self.fx.write("server/src/main/kotlin/Leak.kt", f'const val KEY = "{GOOGLE_KEY}"\n')
        res = self.fx.gate("--profile", "quick")
        self.assertEqual(1, res.returncode, res.stdout)
        self.assertIn("FAIL", res.stdout)
        self.assertIn("google-api-key", res.stdout)
        self.assertNotIn(GOOGLE_KEY, res.stdout)

    def test_skipped_required_check_is_incomplete_not_pass(self):
        self.fx.write("docs/notes.md", "plain notes\n")
        res = self.fx.gate("--profile", "quick", "--skip", "secrets.changed", "--skip-reason", "test")
        self.assertEqual(3, res.returncode, res.stdout)
        self.assertIn("NOT RUN", res.stdout)
        self.assertIn("QUALITY GATE: INCOMPLETE", res.stdout)

    def test_skip_without_reason_is_usage_error(self):
        self.assertEqual(2, self.fx.gate("--skip", "secrets.changed").returncode)

    def test_new_architecture_violation_fails(self):
        self.fx.write("shared/src/commonMain/kotlin/com/vtempe/shared/domain/X.kt",
                      "package com.vtempe.shared.domain\nimport com.vtempe.shared.data.repo.Repo\n")
        res = self.fx.gate("--profile", "quick")
        self.assertEqual(1, res.returncode, res.stdout)
        self.assertIn("NEW DOM", res.stdout)


class HookBehaviour(unittest.TestCase):
    def setUp(self):
        self.fx = Fixture()

    def tearDown(self):
        self.fx.close()

    def _decision(self, res):
        return json.loads(res.stdout)["hookSpecificOutput"]["permissionDecision"] if res.stdout.strip() else None

    def test_guard_decisions(self):
        bash = lambda c: {"hook_event_name": "PreToolUse", "tool_name": "Bash", "tool_input": {"command": c}}
        self.assertIsNone(self._decision(self.fx.hook("guard.py", bash("git status"))))
        self.assertEqual("ask", self._decision(self.fx.hook("guard.py", bash("git push origin x"))))
        self.assertEqual("deny", self._decision(self.fx.hook("guard.py", bash("git push --force origin main"))))
        write = {"tool_name": "Write", "tool_input": {"file_path": str(self.fx.root / "cloudbuild.yaml")}}
        self.assertEqual("ask", self._decision(self.fx.hook("guard.py", write)))
        res = self.fx.hook("guard.py", bash("git push"), env={"VTEMPE_QG_DISABLE": "guard"})
        self.assertEqual(("", 0), (res.stdout, res.returncode))

    def test_guard_blocks_irreversible_asks_in_bypass_mode(self):
        def ev(cmd, mode):
            return {"tool_name": "Bash", "tool_input": {"command": cmd}, "permission_mode": mode}
        self.assertEqual("deny", self._decision(self.fx.hook("guard.py", ev("git push origin x", "bypassPermissions"))))
        self.assertEqual("ask", self._decision(self.fx.hook("guard.py", ev("git push origin x", "default"))))
        self.assertEqual("ask", self._decision(self.fx.hook("guard.py", ev("git push origin x", "auto"))))
        # reversible local actions stay "ask" (git can undo them; the push is the real gate)
        self.assertEqual("ask", self._decision(self.fx.hook("guard.py", ev("git commit --amend", "bypassPermissions"))))
        write = {"tool_name": "Edit", "permission_mode": "bypassPermissions",
                 "tool_input": {"file_path": str(self.fx.root / "Dockerfile")}}
        self.assertEqual("ask", self._decision(self.fx.hook("guard.py", write)))

    def test_guard_ignores_garbage_input(self):
        res = subprocess.run([sys.executable, "-B", str(self.fx.root / ".claude/hooks/guard.py")],
                             input="not json", capture_output=True, text=True)
        self.assertEqual((0, ""), (res.returncode, res.stdout))

    def test_post_edit_blocks_secret_and_records_edit(self):
        p = self.fx.write("server/src/main/kotlin/A.kt", f'val k = "{GOOGLE_KEY}"\n')
        ev = {"session_id": "s1", "tool_name": "Write", "tool_input": {"file_path": str(p), "content": "x"}}
        res = self.fx.hook("post_edit.py", ev)
        self.assertEqual(0, res.returncode, res.stderr)
        out = json.loads(res.stdout)
        self.assertEqual("block", out["decision"])
        self.assertNotIn(GOOGLE_KEY, res.stdout)
        edits = (self.fx.root / ".claude/state/sessions/s1.edits").read_text(encoding="utf-8")
        self.assertIn("server/src/main/kotlin/A.kt", edits)

    def test_stop_gate_blocks_once_then_respects_gate_result(self):
        p = self.fx.write("docs/x.md", "doc\n")
        self.fx.hook("post_edit.py", {"session_id": "s2", "tool_name": "Write", "tool_input": {"file_path": str(p)}})
        self.assertEqual("", self.fx.hook("stop_gate.py", {"session_id": "s2"}).stdout, "docs need no gate")

        k = self.fx.write(".claude/agents/x.md", "---\nname: x\ndescription: d\ntools: Read\n---\nP\n")
        self.fx.hook("post_edit.py", {"session_id": "s2", "tool_name": "Write", "tool_input": {"file_path": str(k)}})
        blocked = self.fx.hook("stop_gate.py", {"session_id": "s2", "stop_hook_active": False})
        self.assertEqual("block", json.loads(blocked.stdout)["decision"])
        self.assertIn("--profile quick", blocked.stdout)

        again = self.fx.hook("stop_gate.py", {"session_id": "s2", "stop_hook_active": True})
        self.assertEqual("", again.stdout, "must not block twice in a row (loop safety)")

        self.fx.gate("--profile", "quick")
        after_gate = self.fx.hook("stop_gate.py", {"session_id": "s2", "stop_hook_active": False})
        self.assertEqual("", after_gate.stdout, "a gate run on the same fingerprint satisfies the hook")

        self.fx.write(".claude/agents/x.md", "---\nname: x\ndescription: changed\ntools: Read\n---\nP\n")
        stale = self.fx.hook("stop_gate.py", {"session_id": "s2", "stop_hook_active": False})
        self.assertIn("different code", json.loads(stale.stdout)["reason"])

    def test_stop_gate_without_session_edits_is_silent(self):
        self.assertEqual("", self.fx.hook("stop_gate.py", {"session_id": "none"}).stdout)


@unittest.skipIf(_real_bash() is None, "no POSIX bash (Git Bash) available to run the launcher")
class LauncherTest(unittest.TestCase):
    """The exact command line from .claude/settings.json, through bash, with a path containing
    spaces and Windows backslashes."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name) / "dir with spaces"
        shutil.copytree(REPO / "scripts" / "quality", self.dir / "scripts" / "quality",
                        ignore=shutil.ignore_patterns("__pycache__"))
        shutil.copytree(REPO / ".claude" / "hooks", self.dir / ".claude" / "hooks",
                        ignore=shutil.ignore_patterns("__pycache__"))
        settings = json.loads((REPO / ".claude" / "settings.json").read_text(encoding="utf-8"))
        self.commands = {e: g[0]["hooks"][0]["command"] for e, g in settings["hooks"].items()}

    def tearDown(self):
        self.tmp.cleanup()

    def run_hook(self, event_name: str, event: dict) -> subprocess.CompletedProcess:
        env = {**os.environ, "CLAUDE_PROJECT_DIR": str(self.dir)}
        return subprocess.run([_real_bash(), "-c", self.commands[event_name]], input=json.dumps(event),
                              capture_output=True, text=True, encoding="utf-8", env=env)

    def test_settings_commands_run_the_hooks(self):
        res = self.run_hook("PreToolUse", {"tool_name": "Bash", "tool_input": {"command": "git push"}})
        self.assertEqual(0, res.returncode, res.stderr)
        self.assertEqual("ask", json.loads(res.stdout)["hookSpecificOutput"]["permissionDecision"])
        self.assertEqual(0, self.run_hook("Stop", {"session_id": "none"}).returncode)

    def test_unknown_script_name_is_rejected(self):
        res = subprocess.run([_real_bash(), str(self.dir / ".claude/hooks/run.sh"), "--fail-closed", "../../evil.py"],
                             capture_output=True, text=True)
        self.assertEqual(2, res.returncode)


if __name__ == "__main__":
    unittest.main()
