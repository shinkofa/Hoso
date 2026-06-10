#!/usr/bin/env python3
"""Obsidian mandatory-read gate — PreToolUse(Edit|Write|Bash).

Companion to session-start-obsidian.py reminder. Workflows.md declares
Obsidian sync BLOCKING at session start: project notes MUST be loaded
via the Obsidian MCP read tool before any state-mutating tool.

The Obsidian MCP server has exposed this read tool under more than one
name across versions (`vault_read`, `get_note`). To stay robust to that
drift, this hook matches ANY known alias instead of a single hardcoded
name — per the cross-project lesson (2026-06-08): a gate that depends on
an external tool name must match several aliases, never a single literal.

This hook scans the transcript for those read tool calls in this session
and verifies the 3 MANDATORY files have been read (by name substring):
  1. _Cross-Project        (cross-project blockers + decisions)
  2. _Index                (vault index)
  3. <project>             (current project file)

A 4th file (<project>-Notes-Jay) is RECOMMENDED when it exists in the
vault, but not blocking — some repos (e.g. MNK-GoRin methodology itself)
don't have a Notes-Jay sibling.

After all 3 mandatory patterns are detected, a state marker is written
and subsequent mutating tools pass through silently.

Skip cases:
  - Tool is not Edit/Write/Bash (Read/Glob/Grep/etc. always pass)
  - Read-only Bash commands (ls, cd, git status, etc.)
  - State marker already set this session
"""

from __future__ import annotations

import sys
from pathlib import Path

HOOK_DIR = Path(__file__).resolve().parent
LIB_DIR = HOOK_DIR.parent / "lib"
sys.path.insert(0, str(LIB_DIR))

from common import (  # noqa: E402
    block,
    find_repo_root,
    format_block,
    get_command,
    pass_through,
    read_hook_input,
)
from session_state import mark_once  # noqa: E402
from transcript_reader import iter_tool_calls  # noqa: E402


MUTATING_TOOLS = {"Edit", "Write", "Bash", "NotebookEdit"}
# The Obsidian MCP read tool has shipped under several names across versions.
# Match ANY of these aliases so the gate is satisfiable whichever one the
# active MCP server exposes. Add new aliases here, never replace.
OBSIDIAN_TOOL_NAMES = (
    "mcp__obsidian-vault__vault_read",
    "mcp__obsidian-vault__get_note",
)

# Read-only Bash prefixes that don't need the Obsidian gate
READ_ONLY_BASH = (
    "ls", "cd", "pwd", "cat", "head", "tail", "grep", "find", "echo",
    "which", "wc", "git status", "git log", "git diff", "git branch",
    "git show", "git remote",
)


def _collect_vault_read_paths(transcript_path: str) -> list[str]:
    """Return paths read via any known Obsidian MCP read-tool alias."""
    if not transcript_path:
        return []
    paths: list[str] = []
    aliases = set(OBSIDIAN_TOOL_NAMES)
    try:
        # No tool_name filter: scan all tool_use blocks and keep those whose
        # name matches any alias. Robust to the MCP server renaming the tool.
        for call in iter_tool_calls(transcript_path):
            if call.get("name") not in aliases:
                continue
            input_data = call.get("input") or {}
            path = input_data.get("path") or input_data.get("file_path") or ""
            if path:
                paths.append(path)
    except Exception:
        return []
    return paths


def _missing_patterns(read_paths: list[str], required: list[str]) -> list[str]:
    """Return required patterns that don't match any read path (case-insensitive)."""
    joined = " ".join(read_paths).lower()
    return [p for p in required if p.lower() not in joined]


def main() -> None:
    raw, data = read_hook_input()
    tool_name = data.get("tool_name") or ""

    # Non-mutating tools always pass
    if tool_name not in MUTATING_TOOLS:
        pass_through()

    # Read-only Bash passes
    if tool_name == "Bash":
        cmd = get_command(data).strip()
        if any(cmd.startswith(p) for p in READ_ONLY_BASH):
            pass_through()

    session_id = data.get("session_id", "") or "no-session"
    transcript_path = data.get("transcript_path", "")
    repo = find_repo_root()
    project = repo.name if repo else "<project>"

    # 3 mandatory patterns: cross-project, index, project file
    required = ["_Cross-Project", "_Index", project]

    # One-shot per session
    if not mark_once("obsidian-sync-checked", "checked", session_id=session_id):
        pass_through()

    read_paths = _collect_vault_read_paths(transcript_path)
    missing = _missing_patterns(read_paths, required)

    if missing:
        # Remove marker so the gate re-fires until satisfied
        from session_state import read_state, write_state  # local import
        st = read_state("obsidian-sync-checked", session_id=session_id, repo_root=repo)
        st["seen"] = []
        write_state("obsidian-sync-checked", st, session_id=session_id, repo_root=repo)

        listing = (
            f"\n  1. 01-Projets/_Cross-Project.md"
            f"\n  2. 01-Projets/_Index.md"
            f"\n  3. 01-Projets/{project}.md"
            f"\n  (4. optional bonus: 01-Projets/{project}-Notes-Jay.md if it exists)"
        )
        loaded = len(required) - len(missing)
        block(format_block(
            f"OBSIDIAN SYNC not satisfied — {loaded}/{len(required)} mandatory notes loaded",
            "Call the Obsidian MCP read tool (vault_read / get_note) for the mandatory files "
            f"before any Edit/Write/Bash:{listing}\n"
            f"Missing patterns: {', '.join(missing)}\n"
            "These are Takumi's context for the current project. Without them, "
            "the session works in the dark.",
            reference="Workflows.md 'Sync Obsidian project notes'",
        ))

    pass_through()


if __name__ == "__main__":
    main()
