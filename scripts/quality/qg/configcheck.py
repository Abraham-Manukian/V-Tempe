"""Validation of the Claude Code engineering system itself (.claude/, CLAUDE.md, workflows).

Catches the failure modes Claude Code does not report: unknown frontmatter fields (silently
ignored), agents without an explicit tool list (they inherit every tool), reviewers that can
write, skills that reference agents or files that do not exist, broken hook wiring.
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path

from . import frontmatter as fm

AGENT_FIELDS = {
    "name", "description", "tools", "disallowedTools", "model", "permissionMode", "maxTurns",
    "autoCompactWindow", "skills", "mcpServers", "hooks", "memory", "background", "omitClaudeMd",
    "effort", "isolation", "color", "initialPrompt", "experimental",
}
SKILL_FIELDS = {
    "name", "description", "when_to_use", "argument-hint", "arguments", "disable-model-invocation",
    "user-invocable", "allowed-tools", "disallowed-tools", "model", "effort", "context", "agent",
    "background", "hooks", "paths", "shell", "metadata", "license", "compatibility",
}
RULE_FIELDS = {"paths"}
MODELS = {"sonnet", "opus", "haiku", "fable", "inherit"}
EFFORTS = {"low", "medium", "high", "xhigh", "max"}
COLORS = {"red", "blue", "green", "yellow", "purple", "orange", "pink", "cyan"}
WRITE_CAPABLE_TOOLS = {"Edit", "Write", "MultiEdit", "NotebookEdit", "Bash", "PowerShell", "Agent"}
KNOWN_TOOLS = {"Read", "Grep", "Glob", "WebFetch", "WebSearch", "Skill", "LSP", "TodoWrite"} | WRITE_CAPABLE_TOOLS
HOOK_EVENTS = {
    "SessionStart", "Setup", "UserPromptSubmit", "UserPromptExpansion", "PreToolUse",
    "PermissionRequest", "PermissionDenied", "PostToolUse", "PostToolUseFailure", "PostToolBatch",
    "Notification", "MessageDisplay", "SubagentStart", "SubagentStop", "TaskCreated",
    "TaskCompleted", "Stop", "StopFailure", "TeammateIdle", "InstructionsLoaded", "ConfigChange",
    "CwdChanged", "DirectoryAdded", "FileChanged", "WorktreeCreate", "WorktreeRemove", "PreCompact",
    "PostCompact", "PreModelSwitch", "PostModelSwitch", "Elicitation", "ElicitationResult", "SessionEnd",
}
PERMISSION_RULE_RE = re.compile(r"^[A-Za-z_][\w]*(?:\(.+\))?$")
AGENT_REF_RE = re.compile(r"\b([a-z]+(?:-[a-z]+)*-(?:reviewer|engineer))\b")
PATH_REF_RE = re.compile(r"`((?:\.claude|scripts/quality|docs)/[^`\s*]+?)`")
MAX_SKILL_DESCRIPTION = 1536
MAX_CLAUDE_MD_LINES = 200


@dataclass
class ConfigReport:
    errors: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)
    agents: dict[str, dict] = field(default_factory=dict)
    skills: dict[str, dict] = field(default_factory=dict)


def _is_read_only_role(name: str) -> bool:
    return name.endswith("-reviewer") or name == "qa-test-engineer"


def check_agents(root: Path, rep: ConfigReport) -> None:
    agents_dir = root / ".claude" / "agents"
    if not agents_dir.exists():
        rep.notes.append(".claude/agents: none")
        return
    for path in sorted(agents_dir.glob("*.md")):
        rel = path.relative_to(root).as_posix()
        try:
            meta, body = fm.load(path.read_text(encoding="utf-8"))
        except (fm.FrontmatterError, UnicodeDecodeError) as exc:
            rep.errors.append(f"{rel}: {exc}")
            continue
        name = meta.get("name")
        for key in sorted(set(meta) - AGENT_FIELDS):
            rep.errors.append(f"{rel}: unknown frontmatter field '{key}' (Claude Code ignores it)")
        if not name or not isinstance(name, str):
            rep.errors.append(f"{rel}: missing 'name'")
            continue
        if ":" in name:
            rep.errors.append(f"{rel}: name must not contain ':'")
        if name != path.stem:
            rep.errors.append(f"{rel}: name '{name}' differs from file name '{path.stem}'")
        if name in rep.agents:
            rep.errors.append(f"{rel}: duplicate agent name '{name}'")
        if not str(meta.get("description", "")).strip():
            rep.errors.append(f"{rel}: missing 'description'")
        if "tools" not in meta:
            rep.errors.append(f"{rel}: no explicit 'tools' (the agent would inherit every tool)")
        tools = fm.as_list(meta.get("tools"))
        for tool in tools:
            base = tool.split("(", 1)[0]
            if base not in KNOWN_TOOLS and not base.startswith("mcp__"):
                rep.errors.append(f"{rel}: unknown tool '{tool}'")
        if _is_read_only_role(name):
            writable = sorted({t.split("(", 1)[0] for t in tools} & WRITE_CAPABLE_TOOLS)
            if writable:
                rep.errors.append(f"{rel}: read-only reviewer has write-capable tools {writable}")
        model = meta.get("model")
        if model is not None and model not in MODELS and not str(model).startswith("claude-"):
            rep.errors.append(f"{rel}: unsupported model '{model}'")
        if meta.get("effort") is not None and meta["effort"] not in EFFORTS:
            rep.errors.append(f"{rel}: unsupported effort '{meta['effort']}'")
        if meta.get("color") is not None and meta["color"] not in COLORS:
            rep.errors.append(f"{rel}: unsupported color '{meta['color']}'")
        if not body.strip():
            rep.errors.append(f"{rel}: empty system prompt")
        rep.agents[name] = {"tools": tools, "model": model, "path": rel}


def _check_refs(root: Path, rel: str, body: str, rep: ConfigReport) -> None:
    for agent in sorted(set(AGENT_REF_RE.findall(body))):
        if agent not in rep.agents:
            rep.errors.append(f"{rel}: references unknown agent '{agent}'")
    for ref in sorted(set(PATH_REF_RE.findall(body))):
        target = ref.split(" ", 1)[0].rstrip("/.,:;")
        # Generated at runtime (.claude/state) or a placeholder like .claude/agents/<name>.md.
        if target.startswith(".claude/state") or any(ch in target for ch in "<>{}$"):
            continue
        if not (root / target).exists():
            rep.errors.append(f"{rel}: references missing path '{target}'")


def check_skills(root: Path, rep: ConfigReport) -> None:
    skills_dir = root / ".claude" / "skills"
    if not skills_dir.exists():
        rep.notes.append(".claude/skills: none")
        return
    for skill_dir in sorted(d for d in skills_dir.iterdir() if d.is_dir()):
        path = skill_dir / "SKILL.md"
        rel = path.relative_to(root).as_posix()
        if not path.exists():
            rep.errors.append(f"{skill_dir.relative_to(root).as_posix()}: missing SKILL.md")
            continue
        try:
            meta, body = fm.load(path.read_text(encoding="utf-8"))
        except (fm.FrontmatterError, UnicodeDecodeError) as exc:
            rep.errors.append(f"{rel}: {exc}")
            continue
        for key in sorted(set(meta) - SKILL_FIELDS):
            rep.errors.append(f"{rel}: unknown frontmatter field '{key}' (Claude Code ignores it)")
        name = meta.get("name", skill_dir.name)
        if name != skill_dir.name:
            rep.errors.append(f"{rel}: name '{name}' differs from directory '{skill_dir.name}'")
        desc = f"{meta.get('description', '')} {meta.get('when_to_use', '')}".strip()
        if not desc:
            rep.errors.append(f"{rel}: missing 'description'")
        elif len(desc) > MAX_SKILL_DESCRIPTION:
            rep.errors.append(f"{rel}: description+when_to_use is {len(desc)} chars (> {MAX_SKILL_DESCRIPTION}, truncated in listing)")
        if body.count("\n") > 500:
            rep.errors.append(f"{rel}: SKILL.md over 500 lines; move detail to supporting files")
        if meta.get("context") == "fork" and not meta.get("agent"):
            rep.notes.append(f"{rel}: context fork without agent uses the general-purpose agent")
        rep.skills[name] = {"path": rel, "model_invocable": not meta.get("disable-model-invocation", False)}
        _check_refs(root, rel, body, rep)


def check_rules(root: Path, rep: ConfigReport) -> None:
    rules_dir = root / ".claude" / "rules"
    if not rules_dir.exists():
        return
    for path in sorted(rules_dir.rglob("*.md")):
        rel = path.relative_to(root).as_posix()
        text = path.read_text(encoding="utf-8")
        if not text.startswith("---"):
            continue  # unconditional rule
        try:
            meta, body = fm.load(text)
        except fm.FrontmatterError as exc:
            rep.errors.append(f"{rel}: {exc}")
            continue
        for key in sorted(set(meta) - RULE_FIELDS):
            rep.errors.append(f"{rel}: unknown frontmatter field '{key}'")
        if "paths" in meta and not fm.as_list(meta["paths"]):
            rep.errors.append(f"{rel}: 'paths' is empty")
        _check_refs(root, rel, body, rep)


def check_settings(root: Path, rep: ConfigReport) -> None:
    path = root / ".claude" / "settings.json"
    if not path.exists():
        rep.notes.append(".claude/settings.json: none")
        return
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        rep.errors.append(f".claude/settings.json: invalid JSON: {exc}")
        return
    if data.get("disableAllHooks") is True:
        rep.errors.append(".claude/settings.json: disableAllHooks is true (every hook is off)")
    for name in ("settings.json", "settings.local.json"):
        try:
            env = json.loads((root / ".claude" / name).read_text(encoding="utf-8")).get("env") or {}
        except (OSError, json.JSONDecodeError, AttributeError):
            continue
        if env.get("VTEMPE_QG_DISABLE"):
            rep.notes.append(f".claude/{name}: hooks disabled via VTEMPE_QG_DISABLE={env['VTEMPE_QG_DISABLE']}")
    for event, groups in (data.get("hooks") or {}).items():
        if event not in HOOK_EVENTS:
            rep.errors.append(f".claude/settings.json: unknown hook event '{event}'")
        if not isinstance(groups, list):
            rep.errors.append(f".claude/settings.json: hooks.{event} must be a list")
            continue
        for group in groups:
            for hook in group.get("hooks", []) if isinstance(group, dict) else []:
                if hook.get("type") != "command" or not isinstance(hook.get("command"), str):
                    rep.errors.append(f".claude/settings.json: hooks.{event}: expected type=command with a command string")
                    continue
                for script in re.findall(r"(\.claude/hooks/[\w.\-]+)", hook["command"]):
                    if not (root / script).exists():
                        rep.errors.append(f".claude/settings.json: hooks.{event} runs missing {script}")
    perms = data.get("permissions") or {}
    for kind in ("allow", "ask", "deny"):
        for rule in perms.get(kind, []):
            if not isinstance(rule, str) or not PERMISSION_RULE_RE.match(rule):
                rep.errors.append(f".claude/settings.json: malformed permissions.{kind} rule {rule!r}")


def check_claude_md(root: Path, rep: ConfigReport) -> None:
    path = root / "CLAUDE.md"
    if not path.exists():
        rep.errors.append("CLAUDE.md missing")
        return
    n = len(path.read_text(encoding="utf-8").splitlines())
    if n > MAX_CLAUDE_MD_LINES:
        rep.errors.append(f"CLAUDE.md has {n} lines (> {MAX_CLAUDE_MD_LINES}, adherence drops)")
    _check_refs(root, "CLAUDE.md", path.read_text(encoding="utf-8"), rep)


def check_workflows(root: Path) -> tuple[list[str], bool]:
    """Returns (errors, parsed). parsed=False when PyYAML is unavailable."""
    wf_dir = root / ".github" / "workflows"
    files = sorted(wf_dir.glob("*.yml")) + sorted(wf_dir.glob("*.yaml")) if wf_dir.exists() else []
    try:
        import yaml  # type: ignore
    except ImportError:
        return [], False
    errors = []
    for f in files:
        rel = f.relative_to(root).as_posix()
        try:
            doc = yaml.safe_load(f.read_text(encoding="utf-8"))
        except yaml.YAMLError as exc:
            errors.append(f"{rel}: invalid YAML: {exc}")
            continue
        if not isinstance(doc, dict) or not isinstance(doc.get("jobs"), dict) or not doc["jobs"]:
            errors.append(f"{rel}: no 'jobs' section")
    return errors, True


def run(root: Path) -> ConfigReport:
    rep = ConfigReport()
    check_agents(root, rep)
    check_skills(root, rep)
    check_rules(root, rep)
    check_settings(root, rep)
    check_claude_md(root, rep)
    return rep
