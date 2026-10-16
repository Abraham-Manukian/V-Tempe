"""V-Tempe quality gate library (stdlib only, Python 3.11+).

Shared by scripts/quality/*.py (the gate, the review bundle) and by the Claude Code hooks in
.claude/hooks/. Nothing here talks to production systems; the only network call is the optional
OSV vulnerability lookup in osv.py.
"""
