"""Risk classification of shell commands and file writes for the PreToolUse guard hook.

Returns a Decision: "deny" (never through Claude; the owner runs it by hand), "ask" (needs the
owner's explicit approval in the permission prompt) or None (no objection). The parser is a
conservative approximation of bash/PowerShell: when in doubt it asks. It is a guard against
mistakes, not a sandbox; permission rules in .claude/settings.json back it up.
"""
from __future__ import annotations

import fnmatch
import re
import shlex
from dataclasses import dataclass

DENY, ASK = "deny", "ask"


@dataclass(frozen=True)
class Decision:
    decision: str
    reason: str
    # False for actions git can undo locally (amend, rebase, staging, editing a tracked file).
    # The guard hard-blocks only irreversible/outward-facing "ask" actions in bypassPermissions
    # mode, where Claude Code would otherwise run them without showing a prompt.
    irreversible: bool = True


SECRET_FILE_GLOBS = (
    "*.env", ".env", ".env.*", "*.jks", "*.keystore", "*.p12", "*.pfx", "*.pem", "*.key",
    "local.properties", "gcp-sa-key.json", "*service-account*.json", "google-services.json",
    "id_rsa*", "id_ed25519*", "*.credentials.json", ".credentials.json", ".git-credentials", ".netrc",
)
SECRET_DIR_PARTS = (".ssh",)
SECRET_FILE_EXCEPTIONS = ("*.env.example", "*.env.sample", "*.env.template", ".env.example")
SECRET_ENV_RE = re.compile(r"\$(?:env:)?\{?\w*(KEY|SECRET|TOKEN|PASSWORD|PASSWD|CREDENTIAL)\w*\}?", re.IGNORECASE)

PROTECTED_WRITE_GLOBS = {
    "cloudbuild.yaml": "production deploy configuration (Cloud Build -> Cloud Run)",
    ".github/workflows/*": "CI/CD workflow (a push to main deploys the server)",
    "Dockerfile": "production server image",
    ".dockerignore": "production image build context",
    ".claude/settings.json": "Claude Code hooks and permissions (the enforcement layer itself)",
    ".claude/settings.local.json": "local Claude Code hooks and permissions",
    ".claude/hooks/*": "Claude Code hook scripts (the enforcement layer itself)",
    "gradle/wrapper/*": "Gradle wrapper (supply chain)",
}

SAFE_DELETE_PARTS = {"build", ".gradle", "__pycache__", ".pytest_cache", "node_modules", ".kotlin",
                     "tmp", "temp", ".tmp", "outputs", "intermediates"}
SAFE_DELETE_PREFIXES = ("/tmp/", "$tmp", "${tmp", "$temp", "${temp", "$env:temp", ".claude/state", "./.claude/state")
CATASTROPHIC_TARGETS = {"", "/", "/*", "~", "~/*", "$home", "${home}", "$home/*", "${home}/*", ".", "./", "..",
                        "../", ".git", "./.git", "$env:userprofile", "%userprofile%"}

GIT_GLOBAL_OPTS_WITH_VALUE = {"-C", "-c", "--git-dir", "--work-tree", "--namespace"}
GCLOUD_MUTATING = {
    "deploy", "delete", "update", "create", "patch", "submit", "import", "export", "restore", "failover",
    "reset-password", "set-iam-policy", "add-iam-policy-binding", "remove-iam-policy-binding", "enable",
    "disable", "rollback", "promote-replica", "resize", "stop", "start", "restart", "set", "unset",
    "add-invoker-policy-binding", "replace", "execute", "cancel", "connect", "ssh", "scp",
}
SHELLS = {"bash", "sh", "zsh", "dash", "pwsh", "powershell", "cmd"}
HTTP_CLIENTS = {"curl", "wget", "http", "https", "invoke-webrequest", "iwr", "invoke-restmethod", "irm"}
UPLOAD_ARG_RE = re.compile(r"^(@.+|\S*=@.+)$")
UPLOAD_FLAGS = {"-T", "--upload-file", "--post-file", "-InFile", "-infile"}
REMOTE_EXEC_RE = re.compile(r"(curl|wget|iwr|irm|Invoke-WebRequest|Invoke-RestMethod)\b[^|]*\|\s*(sudo\s+)?(sh|bash|zsh|python3?|iex|Invoke-Expression)\b", re.IGNORECASE)
DB_URL_RE = re.compile(r"\b(?:postgres(?:ql)?|mysql)://(?:[^@/\s'\"]*@)?([^:/\s?'\"]+)", re.IGNORECASE)
LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1", "[::1]", "host.docker.internal"}
SETTINGS_WRITE_RE = re.compile(
    r"(?:>>?|\b(?:tee|sed\s+-i|Set-Content|Add-Content|Out-File|cp|mv|copy|move|Copy-Item|Move-Item)\b)[^|;&\n]*"
    r"\.claude[\\/](?:settings(?:\.local)?\.json|hooks)", re.IGNORECASE)
SEGMENT_SPLIT_RE = re.compile(r"&&|\|\||;|\||\n|\$\(|`|\(|\)")
MESSAGE_ARG_RE = re.compile(r"""(\s(?:-m|--message|--title|--body)\s+)("(?:[^"\\]|\\.)*"|'[^']*')""")
HEREDOC_RE = re.compile(r"<<-?\s*(['\"]?)(\w+)\1[^\n]*\n(.*?)\n\s*\2\b", re.DOTALL)
INTERPRETERS_RE = re.compile(r"\b(bash|sh|zsh|python3?|psql|mysql|pwsh|powershell|node|ruby|perl)\b")


def _strip_text_payloads(cmd: str) -> str:
    """Drop commit messages and heredoc bodies that only become file content or messages, so
    prose that mentions 'git push' or 'rm -rf' is not mistaken for a command. Heredocs fed to an
    interpreter (bash, python, psql...) are kept and analysed."""
    cmd = MESSAGE_ARG_RE.sub(r'\1""', cmd)

    def heredoc(m: re.Match) -> str:
        line_start = cmd.rfind("\n", 0, m.start()) + 1
        prefix = cmd[line_start:m.start()]
        if re.search(r"\b(cat|tee|git\s+commit)\b", prefix) and not INTERPRETERS_RE.search(prefix):
            return m.group(0)[: m.group(0).index("\n") + 1] + m.group(2)
        return m.group(0)

    return HEREDOC_RE.sub(heredoc, cmd)


def _tokens(segment: str) -> list[str]:
    lexer = shlex.shlex(segment, posix=True)
    lexer.whitespace_split = True
    lexer.escape = ""  # keep Windows backslashes literal
    lexer.commenters = ""
    try:
        return list(lexer)
    except ValueError:
        return segment.split()


def _program(tokens: list[str]) -> tuple[str, list[str]]:
    """Skip env assignments and wrappers; return (program name, its arguments)."""
    wrappers = {"sudo", "command", "exec", "nohup", "time", "env", "nice", "xargs", "builtin", "&", "call", "start"}
    i = 0
    while i < len(tokens):
        t = tokens[i]
        if re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", t) or t.lower() in wrappers:
            i += 1
            continue
        if t.startswith("-") and i > 0 and tokens[i - 1].lower() in wrappers:
            i += 1
            continue
        break
    if i >= len(tokens):
        return "", []
    prog = re.split(r"[\\/]", tokens[i].strip("'\""))[-1].lower()
    prog = re.sub(r"\.(exe|cmd|bat|ps1)$", "", prog)
    return prog, tokens[i + 1:]


def _is_secret_path(token: str) -> bool:
    t = token.strip("'\"").replace("\\", "/")
    name = t.rsplit("/", 1)[-1]
    if any(fnmatch.fnmatchcase(name, g) for g in SECRET_FILE_EXCEPTIONS):
        return False
    if any(part in SECRET_DIR_PARTS for part in t.split("/")[:-1]):
        return True
    return any(fnmatch.fnmatchcase(name, g) for g in SECRET_FILE_GLOBS)


def _delete_target_verdict(target: str) -> Decision | None:
    t = target.strip("'\"").replace("\\", "/")
    low = t.lower().rstrip("/") if t not in ("/",) else "/"
    if (low in CATASTROPHIC_TARGETS or re.fullmatch(r"[a-z]:/?\*?", low) or re.fullmatch(r"/[a-z](/\*)?", low)
            or re.fullmatch(r"(/[a-z]|[a-z]:)/users(/[^/]+)?(/\*)?", low) or low.endswith("/.git")):
        return Decision(DENY, f"recursive delete of '{target}' would destroy the repository, a home directory "
                              "or a drive; run it yourself if it is really intended")
    parts = {p.lower() for p in t.split("/") if p}
    if parts & SAFE_DELETE_PARTS or low.startswith(SAFE_DELETE_PREFIXES) or "/temp/claude/" in low \
            or "/appdata/local/temp/" in low:
        return None
    return Decision(ASK, f"recursive/bulk delete of '{target}'")


def _worst(decisions: list[Decision | None]) -> Decision | None:
    found = [d for d in decisions if d]
    for kind in (DENY, ASK):
        for d in found:
            if d.decision == kind:
                return d
    return None


def _check_rm(args: list[str]) -> Decision | None:
    short = "".join(a[1:] for a in args if a.startswith("-") and not a.startswith("--"))
    recursive = "r" in short.lower() or "--recursive" in args or "-recurse" in (a.lower() for a in args)
    targets = [a for a in args if not a.startswith("-")]
    return _worst([_delete_target_verdict(t) for t in targets
                   if recursive or t.strip("'\"") in ("*", "*.*", "/*", "./*")])


def _check_windows_delete(args: list[str]) -> Decision | None:
    lowered = [a.lower() for a in args]
    recursive = any(a in ("-recurse", "-r", "/s") for a in lowered)
    if not recursive:
        return None
    targets = [a for a in args if not a.startswith("-") and not re.fullmatch(r"/[a-zA-Z]", a)]
    return _worst([_delete_target_verdict(t) for t in targets]) if targets else Decision(ASK, "recursive delete")


def _git(args: list[str]) -> Decision | None:
    i = 0
    globals_seen: list[str] = []
    while i < len(args) and args[i].startswith("-"):
        globals_seen.append(args[i])
        if args[i] in GIT_GLOBAL_OPTS_WITH_VALUE and i + 1 < len(args):
            globals_seen.append(args[i + 1])
            i += 1
        i += 1
    if any("hookspath" in g.lower() for g in globals_seen):
        return Decision(ASK, "git with a replaced core.hooksPath bypasses repository hooks")
    if i >= len(args):
        return None
    sub, rest = args[i], args[i + 1:]
    flags = {a for a in rest if a.startswith("-")}
    shorts = "".join(a[1:] for a in flags if not a.startswith("--"))
    positional = [a for a in rest if not a.startswith("-")]

    if "--no-verify" in flags or (sub == "commit" and "n" in shorts):
        return Decision(ASK, f"git {sub} --no-verify skips the repository's verification hooks")
    if sub == "push":
        force = bool(flags & {"-f", "--force", "--mirror"}) or "f" in shorts or \
            any(a.startswith("--force-with-lease") for a in flags) or any(p.startswith("+") for p in positional)
        refs = [p.lstrip("+") for p in positional[1:]]
        to_main = not refs or any(re.search(r"(^|:|/)(main|master)$", r) for r in refs)
        if force and to_main:
            return Decision(DENY, "force-push to main/master rewrites shared history and triggers a production "
                                  "deploy; only the owner may do this, by hand")
        if force:
            return Decision(ASK, "force-push rewrites remote history")
        if flags & {"--delete", "-d"} or any(r.startswith(":") for r in refs):
            return Decision(ASK, "git push deletes a remote ref")
        return Decision(ASK, "git push publishes commits (a push to main deploys the server); "
                             "the owner pushes only on an explicit command")
    if sub == "reset" and "--hard" in flags:
        return Decision(ASK, "git reset --hard discards uncommitted work")
    if sub == "clean":
        force = "--force" in flags or "f" in shorts
        dry = "--dry-run" in flags or "n" in shorts
        if force and not dry:
            return Decision(ASK, "git clean deletes untracked files (this checkout keeps untracked owner files)")
    if sub == "checkout" and ("--" in rest or "." in positional or flags & {"-f", "--force"}):
        return Decision(ASK, "git checkout of paths/--force discards uncommitted changes")
    if sub == "restore":
        staged_only = bool(flags & {"--staged", "-S"}) and not flags & {"--worktree", "-W"}
        if not staged_only:
            return Decision(ASK, "git restore discards working-tree changes")
    if sub == "stash" and positional[:1] and positional[0] in ("drop", "clear"):
        return Decision(ASK, f"git stash {positional[0]} deletes stashed work")
    if sub == "branch" and ("-D" in flags or flags >= {"--delete", "--force"}):
        return Decision(ASK, "git branch -D deletes an unmerged branch", False)
    if sub == "rebase" and not flags & {"--abort", "--continue", "--skip", "--quit"}:
        return Decision(ASK, "git rebase rewrites history", False)
    if sub == "commit" and "--amend" in flags:
        return Decision(ASK, "git commit --amend rewrites the last commit", False)
    if sub in ("filter-branch", "filter-repo"):
        return Decision(ASK, f"git {sub} rewrites the whole history")
    if sub == "update-ref" and "-d" in flags:
        return Decision(ASK, "git update-ref -d deletes a ref")
    if sub == "reflog" and positional[:1] == ["expire"]:
        return Decision(ASK, "git reflog expire removes recovery points")
    if sub == "gc" and any(a.startswith("--prune") for a in flags):
        return Decision(ASK, "git gc --prune removes unreachable objects (recovery points)")
    if sub == "worktree" and positional[:1] == ["remove"] and flags & {"--force", "-f"}:
        return Decision(ASK, "git worktree remove --force discards that worktree's changes")
    if sub == "config":
        reading = flags & {"--get", "--list", "-l", "--get-all", "--get-regexp", "--show-origin"}
        if any("hookspath" in a.lower() for a in rest) and not reading:
            return Decision(ASK, "git config changes the hooks path")
        if flags & {"--global", "--system"} and not reading:
            return Decision(ASK, "git config changes global/system settings", False)
    if sub == "add" and (flags & {"-f", "--force"} or "f" in shorts):
        return Decision(ASK, "git add --force stages ignored files (ignored files are often secrets)", False)
    if sub == "add" and any(_is_secret_path(p) for p in positional):
        return Decision(ASK, "git add of a secret/credential file", False)
    return None


def _gcloud(args: list[str]) -> Decision | None:
    words = [a.lower() for a in args if not a.startswith("-")]
    if "secrets" in words and "access" in words:
        return Decision(ASK, "gcloud secrets access prints a production secret into the transcript")
    if {"print-access-token", "print-identity-token"} & set(words):
        return Decision(ASK, "prints a live Google Cloud credential")
    hit = sorted(set(words) & GCLOUD_MUTATING)
    if hit:
        return Decision(ASK, f"gcloud {' '.join(words[:4])} changes production cloud resources ({hit[0]})")
    return None


def _segment_decision(prog: str, args: list[str], segment: str, depth: int) -> Decision | None:
    if prog in SHELLS:
        for flag in ("-c", "-command", "/c", "-encodedcommand", "-ec"):
            lowered = [a.lower() for a in args]
            if flag in lowered:
                idx = lowered.index(flag)
                if flag in ("-encodedcommand", "-ec"):
                    return Decision(ASK, "encoded PowerShell command cannot be inspected")
                inner = " ".join(args[idx + 1:])
                return classify_command(inner, depth + 1) if depth < 3 else Decision(ASK, "deeply nested shell")
        return None
    if prog == "git":
        return _git(args)
    if prog == "rm":
        return _check_rm(args)
    if prog in ("remove-item", "ri", "del", "erase", "rd", "rmdir"):
        return _check_windows_delete(args)
    if prog == "find" and ("-delete" in args or ("-exec" in args and any(a in ("rm", "rmdir") for a in args))):
        return Decision(ASK, "find with -delete/-exec rm deletes files in bulk")
    if prog in ("format", "format-volume", "mkfs", "diskpart", "clear-disk") or \
            (prog == "dd" and any(a.startswith("of=/dev") for a in args)):
        return Decision(DENY, f"'{prog}' can destroy a disk")
    if prog in HTTP_CLIENTS and (UPLOAD_FLAGS & set(args) or any(UPLOAD_ARG_RE.match(a) for a in args)):
        return Decision(ASK, f"{prog} uploads a local file to a remote endpoint")
    if prog == "gcloud":
        return _gcloud(args)
    if prog == "gsutil" and args[:1] and args[0] in ("rm", "rsync", "cp", "mv", "rb"):
        return Decision(ASK, f"gsutil {args[0]} changes cloud storage")
    if prog == "firebase" and any(a.startswith(("deploy", "database:set", "database:remove", "firestore:delete",
                                                "auth:import", "hosting:disable")) for a in args):
        return Decision(ASK, "firebase command changes production")
    if prog == "docker" and args[:1] and (args[0] == "push" or args[:2] in (["system", "prune"], ["volume", "rm"],
                                                                           ["volume", "prune"])):
        return Decision(ASK, f"docker {' '.join(args[:2])} publishes an image or deletes data")
    if prog in ("kubectl", "helm") and args[:1] and args[0] in ("apply", "delete", "scale", "edit", "rollout", "patch",
                                                                "replace", "install", "uninstall", "upgrade"):
        return Decision(ASK, f"{prog} {args[0]} changes a cluster")
    if prog == "terraform" and args[:1] and args[0] in ("apply", "destroy", "import", "state"):
        return Decision(ASK, f"terraform {args[0]} changes infrastructure")
    if prog in ("psql", "mysql", "pg_dump", "pg_restore"):
        hosts = [args[i + 1] for i, a in enumerate(args[:-1]) if a in ("-h", "--host")]
        hosts += [a.split("=", 1)[1] for a in args if a.startswith("--host=")]
        if any(h.lower() not in LOCAL_HOSTS for h in hosts):
            return Decision(ASK, f"{prog} to a non-local host (production data)")
    if prog in ("cat", "type", "head", "tail", "less", "more", "get-content", "gc", "bat", "xxd", "od", "base64",
                "strings", "cp", "copy", "copy-item", "scp", "rsync", "certutil", "keytool", "openssl", "nano", "vi", "vim") \
            and any(_is_secret_path(a) for a in args):
        return Decision(ASK, f"{prog} on a secret/credential file would expose it")
    if prog == "printenv" or (prog == "env" and not args) or \
            (prog in ("get-childitem", "gci", "dir", "ls") and any(a.lower().startswith("env:") for a in args)):
        return Decision(ASK, "dumps environment variables (may contain secrets)")
    if prog in ("echo", "printf", "write-output", "write-host") and SECRET_ENV_RE.search(segment):
        return Decision(ASK, "prints a secret-looking environment variable")
    if prog in ("gradlew", "gradle") and any(a in ("publish", "publishToMavenCentral") or a.startswith(":publish")
                                             or a.endswith(":publish") for a in args):
        return Decision(ASK, "Gradle publish uploads artifacts")
    return None


def classify_command(command: str, depth: int = 0) -> Decision | None:
    if not command or not command.strip():
        return None
    if re.search(r"--(allow-)?dangerously-skip-permissions", command):
        return Decision(DENY, "disabling Claude Code permission checks is not allowed")
    text = _strip_text_payloads(command)
    decisions: list[Decision | None] = []
    if "disableAllHooks" in text:
        decisions.append(Decision(ASK, "touches disableAllHooks (turns every safety hook off)"))
    if SETTINGS_WRITE_RE.search(text):
        decisions.append(Decision(ASK, "modifies Claude Code settings/hooks from the shell"))
    if REMOTE_EXEC_RE.search(text):
        decisions.append(Decision(ASK, "pipes a downloaded script into an interpreter"))
    if any(h.lower() not in LOCAL_HOSTS for h in DB_URL_RE.findall(text)):
        decisions.append(Decision(ASK, "connects to a non-local database (production data)"))
    for segment in SEGMENT_SPLIT_RE.split(text):
        tokens = _tokens(segment.strip())
        if not tokens:
            continue
        prog, args = _program(tokens)
        if prog:
            decisions.append(_segment_decision(prog, args, segment, depth))
    return _worst(decisions)


def classify_write(rel_path: str | None) -> Decision | None:
    """Edit/Write targets inside the repo: protected production/enforcement files and secret
    files need approval."""
    if rel_path is None:
        return None
    if _is_secret_path(rel_path):
        return Decision(ASK, f"writes a secret/credential file ({rel_path})", False)
    for glob, why in PROTECTED_WRITE_GLOBS.items():
        if fnmatch.fnmatchcase(rel_path, glob):
            return Decision(ASK, f"edits {rel_path}: {why}", False)
    return None
