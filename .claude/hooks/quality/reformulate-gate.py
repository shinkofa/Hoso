#!/usr/bin/env python3
"""Reformulate gate — PreToolUse Write|Edit.

Enforces Workflows.md "Reformulate before coding": when a non-trivial change
(>1 file in the current turn) is in progress, Takumi MUST have output a
reformulation in the current conversation turn before the 2nd file write.

A reformulation is observable: numbered or bulleted list mentioning
"compris/understood" + "fichiers/files" or similar pattern, OR explicit
"REFORMULATION" marker.

Counts Write|Edit tool calls in the current turn (since last user message).
If count >= 2 AND no reformulation marker found in recent assistant text -> BLOCK.

Trivial changes (single file) are always allowed.
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

HOOK_DIR = Path(__file__).resolve().parent
LIB_DIR = HOOK_DIR.parent / "lib"
sys.path.insert(0, str(LIB_DIR))

from common import block, get_file_path, pass_through, read_hook_input  # type: ignore
from transcript_reader import iter_assistant_text, iter_entries, iter_tool_calls  # type: ignore

# Files exempt from the gate (methodology, docs, configs)
SKIP_PATH_PARTS = (
    "/.claude/state/",
    "\\.claude\\state\\",
    "/node_modules/",
    "\\node_modules\\",
    "/dist/",
    "\\dist\\",
    "/build/",
    "\\build\\",
    "/.next/",
    "\\.next\\",
)

# Reformulation patterns — observable evidence in assistant output
REFORMULATION_PATTERNS = (
    re.compile(r"\bREFORMULATION\b", re.IGNORECASE),
    # Numbered list with understanding + files keywords
    re.compile(
        r"(compris|understood|j'ai\s+lu|je\s+vais)[^\n]{0,200}(fichiers?|files?|touche|touch)",
        re.IGNORECASE | re.DOTALL,
    ),
    # Explicit plan announcement
    re.compile(r"(plan|s[ée]quence|steps?)[^\n]{0,100}(fichiers?|files?|hooks?|commits?)", re.IGNORECASE),
)


def count_writes_this_turn(transcript_path: str) -> int:
    """Count Write|Edit calls since last user message."""
    if not transcript_path:
        return 0
    count = 0
    for entry in iter_entries(transcript_path):
        msg = entry.get("message") or entry
        if not isinstance(msg, dict):
            continue
        role = msg.get("role")
        if role == "user":
            return count
        if role != "assistant":
            continue
        content = msg.get("content")
        if not isinstance(content, list):
            continue
        for blk in content:
            if not isinstance(blk, dict):
                continue
            if blk.get("type") == "tool_use" and blk.get("name") in ("Write", "Edit"):
                count += 1
    return count


def has_reformulation_marker(transcript_path: str) -> bool:
    """Check recent assistant text for reformulation pattern (current turn only)."""
    if not transcript_path:
        return False
    # Scan back to last user message
    texts: list[str] = []
    for entry in iter_entries(transcript_path):
        msg = entry.get("message") or entry
        if not isinstance(msg, dict):
            continue
        role = msg.get("role")
        if role == "user":
            break
        if role != "assistant":
            continue
        content = msg.get("content")
        if not isinstance(content, list):
            continue
        for blk in content:
            if isinstance(blk, dict) and blk.get("type") == "text":
                t = blk.get("text", "")
                if t:
                    texts.append(t)
    blob = "\n".join(texts)
    return any(p.search(blob) for p in REFORMULATION_PATTERNS)


def should_skip(file_path: str) -> bool:
    if not file_path:
        return True
    norm = file_path.replace("\\", "/").lower()
    for part in SKIP_PATH_PARTS:
        if part.replace("\\", "/").lower() in norm:
            return True
    return False


def main() -> None:
    _, data = read_hook_input()
    file_path = get_file_path(data)
    if should_skip(file_path):
        pass_through()

    transcript_path = data.get("transcript_path") or os.environ.get("CLAUDE_TRANSCRIPT_PATH", "")
    writes_so_far = count_writes_this_turn(transcript_path)

    # writes_so_far does not include the current attempt
    # If this is the 1st write of the turn -> pass
    # If this is the 2nd or later -> require reformulation
    if writes_so_far < 1:
        pass_through()

    if has_reformulation_marker(transcript_path):
        pass_through()

    block(
        "BLOCKED: Non-trivial change (2+ files this turn) without reformulation. "
        f"Target: {file_path}. RECOVERY: Output a brief reformulation BEFORE retrying — "
        "state (1) what you understood, (2) what you'll do, (3) what you won't touch, "
        "(4) files impacted. Use the keyword REFORMULATION or a numbered list mentioning "
        "files. See rules/Workflows.md 'Reformulate before coding'."
    )


if __name__ == "__main__":
    main()
