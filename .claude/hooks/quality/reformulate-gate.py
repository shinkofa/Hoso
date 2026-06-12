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


def _is_real_user_message(msg: dict) -> bool:
    """True for a human-typed user turn; False for tool_result deliveries.

    Tool results are delivered as role=user messages carrying tool_result
    blocks. They must NOT be treated as a turn boundary, otherwise a blocked
    attempt (which produces an error tool_result) would cut the window short.
    """
    if not isinstance(msg, dict) or msg.get("role") != "user":
        return False
    content = msg.get("content")
    if isinstance(content, str):
        return True
    if isinstance(content, list):
        for blk in content:
            if isinstance(blk, dict) and blk.get("type") == "tool_result":
                return False
        return True
    return False


def count_writes_this_turn(transcript_path: str) -> int:
    """Count COMPLETED, non-error Write|Edit calls since the last real user turn.

    Only writes whose tool_result is present AND not an error count. This
    excludes (a) the current in-flight attempt (no result yet) and (b) blocked
    attempts (is_error result). Without this, the gate counted its own and other
    hooks blocked attempts and falsely fired on legitimate single-file edits.
    """
    if not transcript_path:
        return 0
    write_ids: list[str] = []
    result_error: dict[str, bool] = {}
    for entry in iter_entries(transcript_path):
        msg = entry.get("message") or entry
        if not isinstance(msg, dict):
            continue
        role = msg.get("role")
        if role == "user":
            if _is_real_user_message(msg):
                break
            content = msg.get("content")
            if isinstance(content, list):
                for blk in content:
                    if isinstance(blk, dict) and blk.get("type") == "tool_result":
                        tid = blk.get("tool_use_id")
                        if tid is not None:
                            result_error[tid] = bool(blk.get("is_error"))
            continue
        if role != "assistant":
            continue
        content = msg.get("content")
        if not isinstance(content, list):
            continue
        for blk in content:
            if (
                isinstance(blk, dict)
                and blk.get("type") == "tool_use"
                and blk.get("name") in ("Write", "Edit")
            ):
                tid = blk.get("id")
                if tid is not None:
                    write_ids.append(tid)
    return sum(1 for tid in write_ids if result_error.get(tid) is False)


def has_reformulation_marker(transcript_path: str) -> bool:
    """Check assistant text since the last real user turn for a reformulation.

    Boundary is the last REAL user message; tool_result deliveries (role=user)
    do not cut the window short, so a reformulation emitted before a blocked
    attempt is still honored on retry.
    """
    if not transcript_path:
        return False
    texts: list[str] = []
    for entry in iter_entries(transcript_path):
        msg = entry.get("message") or entry
        if not isinstance(msg, dict):
            continue
        role = msg.get("role")
        if role == "user":
            if _is_real_user_message(msg):
                break
            continue
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


def has_approved_plan(transcript_path: str) -> bool:
    """True if an approved plan exists anywhere in the session.

    A plan presented via Claude Code's plan mode (ExitPlanMode tool_use) is
    "approved" when its tool_result is present and not an error. Once approved,
    the plan IS the reformulation + authorization for the actions it describes
    (Interpretation-Protocol 'Approved plan'). Scope is session-wide, not the
    current turn, because a plan approved earlier pre-authorizes later writes.

    A rejected plan (is_error=True) or an in-flight plan (no result yet) does
    NOT count. Detection is intentionally lenient on the approval signal
    (is_error=False) — the quality gates still fire on every write, so a false
    positive here only removes a redundant reformulation prompt, never a gate.
    """
    if not transcript_path:
        return False
    plan_ids: list[str] = []
    result_error: dict[str, bool] = {}
    for entry in iter_entries(transcript_path):
        msg = entry.get("message") or entry
        if not isinstance(msg, dict):
            continue
        role = msg.get("role")
        content = msg.get("content")
        if not isinstance(content, list):
            continue
        if role == "user":
            for blk in content:
                if isinstance(blk, dict) and blk.get("type") == "tool_result":
                    tid = blk.get("tool_use_id")
                    if tid is not None:
                        result_error[tid] = bool(blk.get("is_error"))
        elif role == "assistant":
            for blk in content:
                if (
                    isinstance(blk, dict)
                    and blk.get("type") == "tool_use"
                    and blk.get("name") == "ExitPlanMode"
                ):
                    tid = blk.get("id")
                    if tid is not None:
                        plan_ids.append(tid)
    return any(result_error.get(tid) is False for tid in plan_ids)


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

    # An approved plan (plan mode) IS the reformulation for the actions it
    # describes — see Interpretation-Protocol 'Approved plan'.
    if has_approved_plan(transcript_path):
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
