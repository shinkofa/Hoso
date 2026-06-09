#!/usr/bin/env python3
"""Anti-quick-fix gate — PreToolUse Bash.

Trigger
-------
A `git commit -m "<msg>"` whose subject starts with `fix:` / `fix(...):`
or `hotfix:` / `hotfix(...):` (Conventional Commits style — case-insensitive).
Other commit types (feat, refactor, docs, chore, test, perf, ci, style) are
not gated by this hook — they are not claims of resolving a defect.

Why this hook exists
--------------------
"fix:" is a claim of resolution. Without an explicit reflection on durability,
root cause, and alternatives, fixes accumulate as symptom-patches: the same
defect resurfaces in a different form weeks later. The Monozukuri principle
"l'artisan repond du temps long" (rules/Monozukuri.md, comportement #6) is
satisfied only when each fix is consciously validated against three questions
BEFORE the commit is created. The marker forces the reflection to leave a
trace in the conversation, so future-Takumi (or future-Jay) can audit why a
given fix was deemed durable.

Marker — strict format
----------------------
Accepted forms in the recent transcript (latest occurrence wins):

  [ROBUSTNESS]
  - 6 mois: <pourquoi cette correction tient dans 6 mois>
  - cause racine: <oui — quelle racine | non — symptome assume car ...>
  - alternative durable: <aucune valable | voici X mais reportee car Y>

OR (legitimate skip, closed enum):

  [ROBUSTNESS-SKIP] motif: <typo|revert|test-fix|lint-fix|formatting|comment-only>

Layer model (mirrors pre-code-veille-check.py)
----------------------------------------------
- Layer A : closed SKIP enum (6 motifs) — any other text -> BLOCK.
- Layer B : if the commit message body itself contains "regression" or
  "revert" keywords AND no [ROBUSTNESS-SKIP] motif=revert is present, the
  full [ROBUSTNESS] is required (no skip allowed). Reverts are explicit
  symptom acknowledgements; regressions are root-cause-mandatory.
- Layer C : session counter on consecutive SKIPs — at the 3rd SKIP in a
  row, the hook BLOCKS until a real [ROBUSTNESS] marker resets it.

Hook exit codes
---------------
  0 = pass
  2 = block (stderr message printed with RECOVERY)
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import sys
from pathlib import Path

HOOK_DIR = Path(__file__).resolve().parent
LIB_DIR = HOOK_DIR.parent / "lib"
sys.path.insert(0, str(LIB_DIR))

from common import (  # noqa: E402
    find_repo_root,
    format_block,
    get_command,
    pass_through,
    read_hook_input,
)
from session_state import read_state, write_state  # noqa: E402


# --- Configuration -----------------------------------------------------------

# Conventional Commit subject prefixes that constitute a claim of resolution.
# Pattern matches: `fix:`, `fix(scope):`, `hotfix:`, `hotfix(scope):`
# Anchored to the SUBJECT only (first -m argument), case-insensitive.
_FIX_SUBJECT_RE = re.compile(r"^\s*(fix|hotfix)(?:\([^)]*\))?\s*:", re.IGNORECASE)

# Extract the -m / --message argument (single-quoted, double-quoted, or bare token).
# We only need the SUBJECT line, so we capture the first message arg.
_MESSAGE_ARG_RE = re.compile(
    r"""
    (?:-m|--message)            # -m or --message flag
    (?:\s+|=)                   # space or equals
    (?:
        ' ([^']*) '             #  'single-quoted'
      | " ((?:[^"\\]|\\.)*) "   #  "double-quoted with escapes"
      | (\S+)                   #  bare token (no quotes)
    )
    """,
    re.VERBOSE,
)

# Detect `git commit` (any form) excluding rewrites and informational subcommands.
_COMMIT_CMD_RE = re.compile(r"\bgit\s+commit\b")
_AMEND_RE = re.compile(r"\bgit\s+commit\b[^&;|]*\b--amend\b")
_GIT_INFO_RE = re.compile(r"\bgit\s+(log|status|diff|show)\b")

# Marker scan (transcript)
_MARKER_RE = re.compile(
    r"(?:^|\s)\[(ROBUSTNESS|ROBUSTNESS-SKIP)\][^\n]*",
    re.MULTILINE,
)
_SKIP_MOTIF_RE = re.compile(
    r"\[ROBUSTNESS-SKIP\]\s+motif\s*:\s*([a-zA-Z0-9_\-]+)",
)

# Layer A — closed enum of acceptable SKIP motifs.
# Each motif corresponds to a commit class where the 3-question reflection adds
# no durability value (no logic change, or already an explicit acknowledgement).
ALLOWED_SKIP_MOTIFS = {
    "typo",            # pure typo / whitespace / wording fix, zero logic change
    "revert",          # `git revert` of an earlier commit
    "test-fix",        # adjusting a flaky / outdated test, not production code
    "lint-fix",        # formatter / linter auto-fix, no behavior change
    "formatting",      # whitespace, code style, import order
    "comment-only",    # docstring / comment edit, no code change
}

# Layer B — keywords in the SUBJECT that force full [ROBUSTNESS] (no skip allowed
# except `motif: revert`, since a revert is an explicit symptom acknowledgement).
_REGRESSION_RE = re.compile(r"\b(regression|recurr|recurring|same\s+bug|again)\b", re.IGNORECASE)
_REVERT_SUBJECT_RE = re.compile(r"^\s*Revert\s+", re.IGNORECASE)

# Layer A — body required fields (3 dashed lines after [ROBUSTNESS] line).
# We look for presence of the three labels, not their content (content is for humans).
_REQUIRED_BODY_LABELS = (
    re.compile(r"6\s*mois\s*:", re.IGNORECASE),
    re.compile(r"cause\s+racine\s*:", re.IGNORECASE),
    re.compile(r"alternative\s+durable\s*:", re.IGNORECASE),
)

TRANSCRIPT_SCAN_LIMIT = 60
SKIP_COUNT_THRESHOLD = 3
STATE_NAME = "robustness-skips"


# --- Command parsing ---------------------------------------------------------


def is_gated_commit(cmd: str) -> tuple[bool, str]:
    """Return (is_gated, subject). subject = first -m message subject line, or ''.

    Not gated when: not a commit, --amend, info subcommand, no -m message,
    subject not starting with fix/hotfix.
    """
    if not cmd:
        return False, ""
    if not _COMMIT_CMD_RE.search(cmd):
        return False, ""
    if _AMEND_RE.search(cmd):
        return False, ""
    if _GIT_INFO_RE.search(cmd):
        return False, ""

    m = _MESSAGE_ARG_RE.search(cmd)
    if not m:
        # No -m: this could be `git commit` (opens editor). Out of scope —
        # the hook cannot read the editor buffer. Pass through.
        return False, ""

    raw_msg = m.group(1) or m.group(2) or m.group(3) or ""
    subject = raw_msg.splitlines()[0] if raw_msg else ""

    if not _FIX_SUBJECT_RE.match(subject):
        return False, ""

    return True, subject


# --- Transcript scan ---------------------------------------------------------


def _extract_text(entry) -> str:
    chunks: list[str] = []

    def walk(node):
        if isinstance(node, str):
            chunks.append(node)
        elif isinstance(node, dict):
            for _, v in node.items():
                walk(v)
        elif isinstance(node, list):
            for item in node:
                walk(item)

    walk(entry)
    return "\n".join(chunks)


def latest_marker(transcript_path: str) -> tuple[str, str, str] | None:
    """Return (marker_type, block_text, hash) of the most recent marker, or None.

    `block_text` is the marker line PLUS the next few lines (so Layer A body
    validation can see the 3 required dashed fields).
    """
    if not transcript_path or not os.path.isfile(transcript_path):
        return None
    try:
        with open(transcript_path, "r", encoding="utf-8", errors="replace") as f:
            lines = f.readlines()
    except OSError:
        return None
    recent = lines[-TRANSCRIPT_SCAN_LIMIT:]
    for raw in reversed(recent):
        raw = raw.strip()
        if not raw:
            continue
        try:
            entry = json.loads(raw)
            text = _extract_text(entry)
        except (json.JSONDecodeError, ValueError):
            text = raw
        matches = list(_MARKER_RE.finditer(text))
        if not matches:
            continue
        m = matches[-1]
        marker_type = m.group(1)
        # Capture the marker line + up to 8 following lines for body validation.
        start = m.start()
        tail = text[start:]
        block_lines = tail.splitlines()[:9]
        block_text = "\n".join(block_lines)
        digest = hashlib.sha1(block_text.encode("utf-8")).hexdigest()[:16]
        return marker_type, block_text, digest
    return None


# --- Counter state -----------------------------------------------------------


def load_counter(session_id: str | None, repo_root: Path) -> dict:
    data = read_state(STATE_NAME, session_id, repo_root)
    return {
        "skip_count": int(data.get("skip_count", 0)),
        "last_marker_hash": str(data.get("last_marker_hash", "")),
    }


def save_counter(session_id: str | None, repo_root: Path, skip_count: int, marker_hash: str) -> None:
    write_state(
        STATE_NAME,
        {"skip_count": skip_count, "last_marker_hash": marker_hash},
        session_id,
        repo_root,
    )


# --- Validation --------------------------------------------------------------


def body_has_three_labels(block_text: str) -> tuple[bool, list[str]]:
    """Return (ok, missing_labels). The 3 required labels must ALL be present."""
    missing: list[str] = []
    labels = ["6 mois", "cause racine", "alternative durable"]
    for rx, label in zip(_REQUIRED_BODY_LABELS, labels):
        if not rx.search(block_text):
            missing.append(label)
    return (len(missing) == 0), missing


def subject_demands_full_marker(subject: str) -> str | None:
    """Layer B trigger — return a reason string when SKIP is refused, else None."""
    if _REGRESSION_RE.search(subject):
        return "subject mentions a regression / recurring bug"
    if _REVERT_SUBJECT_RE.search(subject):
        return "subject starts with 'Revert ' (explicit symptom acknowledgement)"
    return None


# --- Main --------------------------------------------------------------------


def _block(msg: str) -> None:
    print(msg, file=sys.stderr)
    sys.exit(2)


def main() -> None:
    _, data = read_hook_input()
    cmd = get_command(data)
    gated, subject = is_gated_commit(cmd)
    if not gated:
        pass_through()

    transcript_path = data.get("transcript_path") or os.environ.get("CLAUDE_TRANSCRIPT_PATH", "")
    session_id = data.get("session_id") or os.environ.get("CLAUDE_SESSION_ID", "")
    repo_root = find_repo_root()

    latest = latest_marker(transcript_path)
    counter = load_counter(session_id, repo_root)

    if latest is None:
        _block(format_block(
            reason=f"`fix:` / `hotfix:` commit without [ROBUSTNESS] marker (subject: {subject!r})",
            recovery=(
                "Output the marker BEFORE retrying the commit:\n"
                "  [ROBUSTNESS]\n"
                "  - 6 mois: <pourquoi cette correction tient dans 6 mois>\n"
                "  - cause racine: <oui -- quelle racine | non -- symptome assume car ...>\n"
                "  - alternative durable: <aucune valable | voici X mais reportee car Y>\n"
                "Or, for trivial commits, emit:\n"
                f"  [ROBUSTNESS-SKIP] motif: <one of {sorted(ALLOWED_SKIP_MOTIFS)}>"
            ),
            reference="rules/Monozukuri.md > Anti-Quick-Fix Marker",
        ))

    marker_type, block_text, marker_hash = latest

    # Layer B: subject mentions regression / revert -> SKIP refused (except motif=revert
    # when the subject is a Revert).
    layer_b_reason = subject_demands_full_marker(subject)
    if layer_b_reason and marker_type == "ROBUSTNESS-SKIP":
        m = _SKIP_MOTIF_RE.search(block_text)
        motif = m.group(1).lower() if m else ""
        # Special allowance: `motif: revert` on a `Revert ` subject is legitimate.
        if not (motif == "revert" and _REVERT_SUBJECT_RE.search(subject)):
            _block(format_block(
                reason=f"[ROBUSTNESS-SKIP] refused -- {layer_b_reason}",
                recovery=(
                    "Emit the full [ROBUSTNESS] marker (3 lines) BEFORE retrying. "
                    "A regression or revert demands explicit reflection on durability "
                    "and root cause; a skip motif is not sufficient."
                ),
                reference="rules/Monozukuri.md > Anti-Quick-Fix Marker (Layer B)",
            ))

    if marker_type == "ROBUSTNESS-SKIP":
        # Layer A: motif must be in enum
        m = _SKIP_MOTIF_RE.search(block_text)
        motif = m.group(1).lower() if m else ""
        if motif not in ALLOWED_SKIP_MOTIFS:
            _block(format_block(
                reason=f"[ROBUSTNESS-SKIP] motif '{motif or '(empty)'}' not in closed enum",
                recovery=(
                    f"Use one of {sorted(ALLOWED_SKIP_MOTIFS)} "
                    "or emit a full [ROBUSTNESS] marker instead."
                ),
                reference="rules/Monozukuri.md > Anti-Quick-Fix Marker (Layer A)",
            ))

        # Layer C: counter (count once per unique marker hash)
        if counter["last_marker_hash"] != marker_hash:
            counter["skip_count"] += 1
        if counter["skip_count"] >= SKIP_COUNT_THRESHOLD:
            save_counter(session_id, repo_root, counter["skip_count"], marker_hash)
            _block(format_block(
                reason=(
                    f"[ROBUSTNESS-SKIP] threshold reached "
                    f"(consecutive skips: {counter['skip_count']}, threshold {SKIP_COUNT_THRESHOLD})"
                ),
                recovery=(
                    "Emit a full [ROBUSTNESS] marker (3 lines) -- the counter "
                    "resets only with a real reflection, not with another SKIP."
                ),
                reference="rules/Monozukuri.md > Anti-Quick-Fix Marker (Layer C)",
            ))

        save_counter(session_id, repo_root, counter["skip_count"], marker_hash)
        sys.exit(0)

    # marker_type == "ROBUSTNESS" -> verify the 3 required body labels are present.
    ok, missing = body_has_three_labels(block_text)
    if not ok:
        _block(format_block(
            reason=f"[ROBUSTNESS] marker is missing required label(s): {missing}",
            recovery=(
                "The marker MUST include all three lines:\n"
                "  - 6 mois: ...\n"
                "  - cause racine: ...\n"
                "  - alternative durable: ...\n"
                "Re-emit the marker with the missing line(s) before retrying."
            ),
            reference="rules/Monozukuri.md > Anti-Quick-Fix Marker",
        ))

    # Real marker accepted -> reset the skip counter on a new hash.
    if counter["last_marker_hash"] != marker_hash:
        save_counter(session_id, repo_root, 0, marker_hash)

    sys.exit(0)


if __name__ == "__main__":
    main()
