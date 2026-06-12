#!/usr/bin/env python3
"""Veille / SKB Evidence guard — PreToolUse Write|Edit.

Enforces Workflows.md "Veille/SKB Evidence Protocol" with 3 hardening layers
(2026-05-19 — Option C):

  Layer A — Closed enum for SKIP motifs
    [VEILLE-SKIP] motif: <enum> where enum is one of:
      typo, internal-refactor-no-new-deps, hotfix-known-root-cause,
      test-only, methodology-edit, generated-artifact
    Any other motif text -> BLOCK.

  Layer B — Diff-aware: force REAL veille when content is sensitive
    Triggers (read the target file content / Edit diff):
      - Target is a dependency manifest (package.json, pyproject.toml,
        mix.exs, Cargo.toml, go.mod, requirements*.txt, Gemfile, ...)
      - New non-relative / non-stdlib import added vs old_string
      - Version pin pattern present in the diff (@X.Y.Z, ^X.Y, ~= X.Y)
    When triggered: ONLY [VEILLE] <techno>@<version> verifie <date> via <source>
    is accepted. SKB and VEILLE-SKIP are refused.

  Layer C — Session skip counter
    State file .claude/state/veille-skips-<session>.json tracks
    consecutive VEILLE-SKIP markers. The 3rd consecutive SKIP -> BLOCK
    even for trivial changes; a real [VEILLE] or [SKB] resets the counter.
    A given marker is counted ONCE (hashed) — repeated tool calls under
    the same marker do not re-increment.

Markers (case-sensitive, line-start or whitespace prefix):
  [VEILLE] <techno>@<version> verifie <date> via <source>
  [SKB] consulte: <paths>
  [VEILLE-SKIP] motif: <enum>

Hook exit codes:
  0 = pass
  2 = block (stderr message printed)
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

from common import find_repo_root  # noqa: E402
from session_state import read_state, write_state  # noqa: E402
from transcript_reader import iter_tool_calls  # noqa: E402


# --- Configuration -----------------------------------------------------------

CODE_EXT = {"ts", "tsx", "js", "jsx", "py", "ex", "exs", "rs", "go"}

# Paths that do NOT require veille evidence
SKIP_PATH_PARTS = (
    "/.claude/",
    "/node_modules/",
    "/dist/",
    "/build/",
    "/.next/",
    "/__pycache__/",
    "/coverage/",
    "/.venv/",
    "/venv/",
    "/target/",
    "/_build/",
    "/deps/",
    "/docs/",
    "/mnk/",
    "/rules/",
)

# Filename patterns that do NOT require veille evidence
SKIP_FILENAME_PATTERNS = (
    r"\.test\.",
    r"\.spec\.",
    r"\.stories\.",
    r"__tests__",
    r"conftest\.py",
    r"setup\.py",
    r"setup\.cfg",
)

# Layer A — closed enum of acceptable SKIP motifs
ALLOWED_SKIP_MOTIFS = {
    "typo",
    "internal-refactor-no-new-deps",
    "hotfix-known-root-cause",
    "test-only",
    "methodology-edit",
    "generated-artifact",
}

# Layer B — dependency manifest filenames
DEPENDENCY_MANIFESTS = {
    "package.json",
    "package-lock.json",
    "pnpm-lock.yaml",
    "yarn.lock",
    "pyproject.toml",
    "uv.lock",
    "poetry.lock",
    "requirements.txt",
    "requirements-dev.txt",
    "Pipfile",
    "Pipfile.lock",
    "mix.exs",
    "mix.lock",
    "Cargo.toml",
    "Cargo.lock",
    "go.mod",
    "go.sum",
    "Gemfile",
    "Gemfile.lock",
    "composer.json",
    "composer.lock",
}

# Layer B — version pin patterns (caught on new diff lines)
VERSION_PIN_RE = re.compile(
    r"""
    (?: @ \d+\.\d+(?:\.\d+)? )            # @1.2.3 npm scoped
  | (?: \^ \d+\.\d+ )                     # ^1.2
  | (?: ~= ?\d+\.\d+ )                    # ~= 1.2
  | (?: ~> ?\d+\.\d+ )                    # ~> 1.2 (mix.exs)
  | (?: >= ?\d+\.\d+(?:,\s*<\s*\d+)? )    # >=1.2,<2 (Python)
    """,
    re.VERBOSE,
)

# Python stdlib names (3.10+ exposes sys.stdlib_module_names)
PY_STDLIB = set(getattr(sys, "stdlib_module_names", ()))

# Marker scan
MARKER_RE = re.compile(
    r"(?:^|\s)\[(VEILLE|SKB|VEILLE-SKIP)\][^\n]+",
    re.MULTILINE,
)
SKIP_MOTIF_RE = re.compile(
    r"\[VEILLE-SKIP\]\s+motif\s*:\s*([a-zA-Z0-9_\-]+)",
)
TRANSCRIPT_SCAN_LIMIT = 200
SKIP_COUNT_THRESHOLD = 3

# Lines that are clearly our own recovery / block messages — never scan them
# for markers (otherwise the hook re-matches its own template strings and
# produces cascading false blocks). Jay 2026-05-31 bug report.
RECOVERY_LINE_HINTS = ("BLOCKED:", "RECOVERY:")

# Chantier D — proof of web veille. A [VEILLE] marker on a SENSITIVE change
# must be backed by a REAL web tool call in the session, not just the text.
# Match known web tools by exact name + substring (alias-tolerant per the
# 2026-06-08 cross-project lesson: never bind to a single literal tool name).
WEB_TOOL_NAMES_EXACT = {"WebSearch", "WebFetch"}
WEB_TOOL_SUBSTRINGS = (
    "websearch", "web_search", "webfetch", "web_fetch",
    "searxng", "tavily", "brave",
)


# --- Input -------------------------------------------------------------------


def read_input() -> dict:
    try:
        return json.loads(sys.stdin.read())
    except (json.JSONDecodeError, ValueError):
        return {}


def get_tool_input(data: dict) -> dict:
    return data.get("tool_input") or data


def get_file_info(data: dict) -> tuple[str, str, str]:
    ti = get_tool_input(data)
    file_path = (ti.get("file_path", "") or "").replace("\\", "/")
    filename = os.path.basename(file_path)
    _, ext = os.path.splitext(filename)
    return file_path, filename, ext.lstrip(".").lower()


def get_new_content(data: dict) -> str:
    ti = get_tool_input(data)
    return ti.get("content") or ti.get("new_string") or ""


def get_old_content(data: dict) -> str:
    ti = get_tool_input(data)
    return ti.get("old_string") or ""


# --- needs_evidence (path-based skip) ---------------------------------------


def needs_evidence(file_path: str, filename: str, ext: str) -> bool:
    """Source code in a non-skip path requires evidence."""
    if ext not in CODE_EXT:
        return False
    path_norm = file_path.lower()
    for part in SKIP_PATH_PARTS:
        if part in path_norm:
            return False
    for pat in SKIP_FILENAME_PATTERNS:
        if re.search(pat, filename, re.IGNORECASE):
            return False
    return True


# --- Layer B detection -------------------------------------------------------


def file_is_dep_manifest(filename: str) -> bool:
    return filename in DEPENDENCY_MANIFESTS


def new_lines(old: str, new: str) -> list[str]:
    """Return lines present in `new` but not in `old`. Naive but sufficient
    for our purpose (we don't need true line-level diff)."""
    old_set = set(old.splitlines())
    return [line for line in new.splitlines() if line not in old_set]


PY_IMPORT_RE = re.compile(r"^\s*(?:from\s+([a-zA-Z_][\w.]*)|import\s+([a-zA-Z_][\w.]*))")
JS_IMPORT_RE = re.compile(r"""(?:^|;)\s*import\s+(?:[^;'"]+\s+from\s+)?['"]([^'"]+)['"]""")
JS_REQUIRE_RE = re.compile(r"""require\(\s*['"]([^'"]+)['"]\s*\)""")


def has_new_external_import(diff_lines: list[str], ext: str) -> bool:
    """True if a new external import is present in diff_lines."""
    for line in diff_lines:
        if ext == "py":
            m = PY_IMPORT_RE.match(line)
            if m:
                mod = (m.group(1) or m.group(2) or "").split(".")[0]
                if not mod:
                    continue
                if mod.startswith("_"):
                    continue
                if PY_STDLIB and mod in PY_STDLIB:
                    continue
                return True
        elif ext in {"ts", "tsx", "js", "jsx"}:
            for m in JS_IMPORT_RE.finditer(line):
                spec = m.group(1)
                if not spec.startswith((".", "/", "~", "@/")):
                    return True
            for m in JS_REQUIRE_RE.finditer(line):
                spec = m.group(1)
                if not spec.startswith((".", "/", "~", "@/")):
                    return True
    return False


def has_version_pin(diff_lines: list[str]) -> bool:
    return any(VERSION_PIN_RE.search(line) for line in diff_lines)


def sensitive_change(file_path: str, filename: str, ext: str, old: str, new: str) -> str | None:
    """Return a short reason string if Layer B is triggered, else None."""
    if file_is_dep_manifest(filename):
        return f"target is dependency manifest ({filename})"
    diff = new_lines(old, new) if old else new.splitlines()
    if has_version_pin(diff):
        return "version pin pattern in diff"
    if ext in {"py", "ts", "tsx", "js", "jsx"} and has_new_external_import(diff, ext):
        return f"new external import detected ({ext})"
    return None


# --- Transcript scan ---------------------------------------------------------


def extract_text(entry) -> str:
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
    """Return (marker_type, marker_line, hash) of the most recent marker, or None."""
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
            text = extract_text(entry)
        except (json.JSONDecodeError, ValueError):
            text = raw
        # Filter out our own recovery / block messages (they contain literal
        # marker templates that would otherwise be re-matched).
        filtered_lines = [
            ln for ln in text.splitlines()
            if not any(h in ln for h in RECOVERY_LINE_HINTS)
        ]
        text = "\n".join(filtered_lines)
        # Filter out matches that are obviously placeholders (e.g.
        # "[VEILLE] <techno>@<version> ...") — real markers have concrete
        # content, not angle-bracket templates or Python set repr.
        matches = [
            m for m in MARKER_RE.finditer(text)
            if "<" not in m.group(0) and "{" not in m.group(0)
        ]
        if matches:
            m = matches[-1]
            marker_type = m.group(1)
            marker_line = m.group(0).strip()
            digest = hashlib.sha1(marker_line.encode("utf-8")).hexdigest()[:16]
            return marker_type, marker_line, digest
    return None


# --- Counter state -----------------------------------------------------------


STATE_NAME = "veille-skips"


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


# --- Main --------------------------------------------------------------------


def has_web_veille_call(transcript_path: str) -> bool:
    """True if a real web tool call (WebSearch/WebFetch/MCP web) happened.

    Scope is session-wide on purpose: under plan mode (Chantier B) the veille
    is performed in the plan phase and the code is written in a later turn, so
    a per-turn scan would false-block legitimate plan execution. A real tool
    call cannot be fabricated by writing marker text — that is the proof.
    """
    if not transcript_path:
        return False
    try:
        for call in iter_tool_calls(transcript_path):
            name = call.get("name") or ""
            if name in WEB_TOOL_NAMES_EXACT:
                return True
            low = name.lower()
            if any(sub in low for sub in WEB_TOOL_SUBSTRINGS):
                return True
    except Exception:
        return False
    return False


def block(msg: str) -> None:
    print(msg, file=sys.stderr)
    sys.exit(2)


def main() -> None:
    data = read_input()
    file_path, filename, ext = get_file_info(data)
    if not file_path:
        sys.exit(0)

    is_dep = file_is_dep_manifest(filename)

    if not is_dep and not needs_evidence(file_path, filename, ext):
        sys.exit(0)

    new_content = get_new_content(data)
    old_content = get_old_content(data)
    sensitive_reason = sensitive_change(file_path, filename, ext, old_content, new_content)

    transcript_path = data.get("transcript_path") or os.environ.get("CLAUDE_TRANSCRIPT_PATH", "")
    session_id = data.get("session_id") or os.environ.get("CLAUDE_SESSION_ID", "")
    repo_root = find_repo_root()

    latest = latest_marker(transcript_path)
    counter = load_counter(session_id, repo_root)

    if latest is None:
        msg = (
            "BLOCKED: Veille / SKB evidence missing before writing source code.\n"
            f"Target: {file_path}\n"
            "RECOVERY: Output one of the strict markers BEFORE retrying:\n"
            "  [VEILLE] <techno>@<version> verifie <YYYY-MM-DD> via <source>\n"
            "  [SKB] consulte: <chemin1>, <chemin2>\n"
            f"  [VEILLE-SKIP] motif: <one of {sorted(ALLOWED_SKIP_MOTIFS)}>\n"
            "See rules/Workflows.md -> 'Veille/SKB Evidence Protocol'."
        )
        block(msg)

    marker_type, marker_line, marker_hash = latest

    # Layer B: sensitive content requires REAL veille
    if sensitive_reason and marker_type != "VEILLE":
        msg = (
            "BLOCKED: Sensitive change detected — real [VEILLE] required.\n"
            f"Target: {file_path}\n"
            f"Trigger: {sensitive_reason}\n"
            f"Latest marker: [{marker_type}] (insufficient for sensitive change)\n"
            "RECOVERY: Output a real veille marker BEFORE retrying:\n"
            "  [VEILLE] <techno>@<version> verifie <YYYY-MM-DD> via <source>\n"
            "Layer B refuses [SKB] and [VEILLE-SKIP] on sensitive content."
        )
        block(msg)

    # Chantier D: a [VEILLE] marker on a sensitive change must be backed by a
    # REAL web tool call this session — proof, not just text.
    if sensitive_reason and marker_type == "VEILLE" and not has_web_veille_call(transcript_path):
        msg = (
            "BLOCKED: [VEILLE] marker present but no web veille was actually performed.\n"
            f"Target: {file_path}\n"
            f"Trigger: {sensitive_reason}\n"
            "No WebSearch / WebFetch (or MCP web) tool call found in this session.\n"
            "RECOVERY: actually run the veille — WebSearch/WebFetch the registry "
            "(hex.pm, npmjs, pypi, crates.io...) to confirm the current version, "
            "THEN re-emit [VEILLE] <techno>@<version> verifie <YYYY-MM-DD> via <source>.\n"
            "The marker text alone is not proof; the tool call is."
        )
        block(msg)

    # Layer A: VEILLE-SKIP motif must be in enum
    if marker_type == "VEILLE-SKIP":
        m = SKIP_MOTIF_RE.search(marker_line)
        motif = m.group(1).lower() if m else ""
        if motif not in ALLOWED_SKIP_MOTIFS:
            msg = (
                "BLOCKED: VEILLE-SKIP motif is not in the closed enum.\n"
                f"Target: {file_path}\n"
                f"Motif found: '{motif or '(empty)'}'\n"
                f"RECOVERY: use one of {sorted(ALLOWED_SKIP_MOTIFS)}\n"
                "Or emit a real [VEILLE] / [SKB] marker instead."
            )
            block(msg)

        # Layer C: counter
        if counter["last_marker_hash"] != marker_hash:
            counter["skip_count"] += 1
        if counter["skip_count"] >= SKIP_COUNT_THRESHOLD:
            msg = (
                "BLOCKED: VEILLE-SKIP threshold reached.\n"
                f"Consecutive SKIPs this session: {counter['skip_count']} "
                f"(threshold {SKIP_COUNT_THRESHOLD}).\n"
                f"Target: {file_path}\n"
                "RECOVERY: Emit a real [VEILLE] or [SKB] marker — the counter "
                "resets only with verified evidence, not with another SKIP."
            )
            save_counter(session_id, repo_root, counter["skip_count"], marker_hash)
            block(msg)

        save_counter(session_id, repo_root, counter["skip_count"], marker_hash)
        sys.exit(0)

    # VEILLE or SKB: reset the skip counter on new marker
    if counter["last_marker_hash"] != marker_hash:
        save_counter(session_id, repo_root, 0, marker_hash)

    sys.exit(0)


if __name__ == "__main__":
    main()
