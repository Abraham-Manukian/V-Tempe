"""Git access for the quality gate: changed files, diffs and a working-tree fingerprint.

All paths returned are repository-relative with forward slashes. Commands are run with argument
lists (never through a shell) and `-z` output where paths are involved, so spaces and non-ASCII
file names are handled.
"""
from __future__ import annotations

import hashlib
import os
import subprocess
from dataclasses import dataclass
from pathlib import Path

_GIT = ["git", "-c", "core.quotepath=off"]
_MAX_HASHED_UNTRACKED_BYTES = 5 * 1024 * 1024


class GitError(RuntimeError):
    pass


def run_git(args: list[str], cwd: Path, check: bool = True) -> bytes:
    proc = subprocess.run(_GIT + args, cwd=str(cwd), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if check and proc.returncode != 0:
        raise GitError(f"git {' '.join(args)} failed ({proc.returncode}): "
                       f"{proc.stderr.decode('utf-8', 'replace').strip()}")
    return proc.stdout


def repo_root(start: Path | None = None) -> Path:
    start = start or Path.cwd()
    out = run_git(["rev-parse", "--show-toplevel"], start).decode("utf-8").strip()
    return Path(out)


def head_sha(root: Path) -> str:
    return run_git(["rev-parse", "HEAD"], root).decode().strip()


def resolve_base(root: Path, base: str) -> str:
    """Commit to diff against. `HEAD` means uncommitted work only; a branch name means its
    merge-base with HEAD (everything this branch adds, committed or not)."""
    if base == "HEAD":
        return head_sha(root)
    try:
        return run_git(["merge-base", base, "HEAD"], root).decode().strip()
    except GitError as exc:
        raise GitError(f"cannot resolve base '{base}': {exc}") from exc


@dataclass(frozen=True)
class ChangedFile:
    path: str
    status: str  # A, M, D, R, C, T, U or '?' for untracked

    @property
    def deleted(self) -> bool:
        return self.status == "D"


def _split_z(data: bytes) -> list[str]:
    return [p for p in data.decode("utf-8", "replace").split("\0") if p]


def changed_files(root: Path, base_commit: str, include_untracked: bool = True) -> list[ChangedFile]:
    """Files that differ between `base_commit` and the working tree (staged or not), plus
    untracked files that are not ignored."""
    fields = _split_z(run_git(["diff", "--name-status", "-z", "--find-renames", base_commit], root))
    result: dict[str, ChangedFile] = {}
    i = 0
    while i < len(fields):
        status = fields[i]
        code = status[0]
        if code in ("R", "C"):
            new_path = fields[i + 2]
            result[new_path] = ChangedFile(new_path, code)
            i += 3
        else:
            result[fields[i + 1]] = ChangedFile(fields[i + 1], code)
            i += 2
    if include_untracked:
        for path in untracked_files(root):
            result.setdefault(path, ChangedFile(path, "?"))
    return sorted(result.values(), key=lambda c: c.path)


def untracked_files(root: Path) -> list[str]:
    return _split_z(run_git(["ls-files", "--others", "--exclude-standard", "-z"], root))


def tracked_files(root: Path) -> list[str]:
    return _split_z(run_git(["ls-files", "-z"], root))


def diff_text(root: Path, base_commit: str, paths: list[str] | None = None) -> str:
    args = ["diff", "--no-color", "--find-renames", base_commit]
    if paths:
        args += ["--", *paths]
    return run_git(args, root).decode("utf-8", "replace")


def fingerprint(root: Path) -> str:
    """Identifies the exact state of the working tree: HEAD, every tracked change (staged or
    not) and the content of untracked, non-ignored files. Two runs with the same fingerprint saw
    the same code, so their results can be reused instead of re-running the checks."""
    h = hashlib.sha256()
    h.update(head_sha(root).encode())
    h.update(run_git(["diff", "HEAD", "--binary", "--no-color", "--no-ext-diff"], root))
    for rel in sorted(untracked_files(root)):
        h.update(b"\0untracked\0" + rel.encode("utf-8"))
        p = root / rel
        try:
            st = p.stat()
        except OSError:
            continue
        if st.st_size > _MAX_HASHED_UNTRACKED_BYTES:
            h.update(f"{st.st_size}:{st.st_mtime_ns}".encode())
            continue
        try:
            h.update(hashlib.sha256(p.read_bytes()).digest())
        except OSError:
            continue
    return h.hexdigest()[:16]


def is_probably_text(path: Path, sniff: int = 8192) -> bool:
    try:
        with open(path, "rb") as fh:
            chunk = fh.read(sniff)
    except OSError:
        return False
    return b"\0" not in chunk


def to_rel(root: Path, file_path: str) -> str | None:
    """Repository-relative forward-slash path, or None when the file is outside the repo."""
    try:
        # realpath also expands Windows 8.3 short names (C:\Users\ABRAHA~1) and symlinks, so
        # two spellings of the same location compare equal.
        rel = os.path.relpath(os.path.realpath(file_path), os.path.realpath(root))
    except ValueError:  # different drive on Windows
        return None
    if rel.startswith(".."):
        return None
    return rel.replace(os.sep, "/")
