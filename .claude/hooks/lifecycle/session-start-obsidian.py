#!/usr/bin/env python3
"""Obsidian sync reminder — SessionStart hook.

Non-blocking reminder of the 4 mandatory Obsidian project notes Takumi
MUST sync at every session start (per Workflows.md "Sync Obsidian project
notes"). The hook does NOT call the MCP itself (hooks are external
processes with no MCP access) — it surfaces the reminder so Takumi
loads the right files via mcp__obsidian-vault__get_note.

The 4 files:
  1. 01-Projets/_Cross-Project.md
  2. 01-Projets/_Index.md
  3. 01-Projets/<current-project>.md
  4. 01-Projets/<current-project>-Notes-Jay.md

Current project is auto-detected from the repo basename.

Exit code: 0 always (reminder is non-blocking, printed on stderr so
Takumi sees it; companion hook obsidian-mandatory-read.py blocks the
first mutating tool if the reads aren't done).
"""

from __future__ import annotations

import sys
from pathlib import Path

HOOK_DIR = Path(__file__).resolve().parent
LIB_DIR = HOOK_DIR.parent / "lib"
sys.path.insert(0, str(LIB_DIR))

try:
    from common import (  # type: ignore
        canonical_project_name,
        find_repo_root,
        read_hook_input,
    )
except Exception:
    def read_hook_input():
        try:
            raw = sys.stdin.read()
        except Exception:
            raw = ""
        return raw, {}

    def find_repo_root():
        return Path.cwd()

    def canonical_project_name():
        return Path.cwd().name


def _detect_project_name() -> str:
    # Canonical name stays correct inside a git worktree (branch-named dir).
    name = canonical_project_name()
    return name or "<project>"


def main() -> None:
    try:
        read_hook_input()
    except Exception:
        pass

    project = _detect_project_name()
    msg = (
        "OBSIDIAN SYNC reminder (SessionStart) — load these 3 mandatory files "
        "via mcp__obsidian-vault__get_note before any mutating tool:\n"
        f"  1. 01-Projets/_Cross-Project.md\n"
        f"  2. 01-Projets/_Index.md\n"
        f"  3. 01-Projets/{project}.md\n"
        f"  (bonus: 01-Projets/{project}-Notes-Jay.md if it exists)\n"
        "Companion hook obsidian-mandatory-read.py will BLOCK the first "
        "Edit/Write/Bash until the 3 mandatory patterns are read."
    )
    sys.stderr.write(msg + "\n")
    sys.stderr.flush()
    sys.exit(0)


if __name__ == "__main__":
    main()
