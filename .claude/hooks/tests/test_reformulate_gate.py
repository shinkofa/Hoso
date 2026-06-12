"""reformulate-gate.py — PreToolUse Write|Edit reformulation gate.

Behavior under test:
- 1st write of the turn -> always pass
- 2nd+ write without a reformulation marker AND without an approved plan -> BLOCK
- 2nd+ write WITH a reformulation marker in assistant text -> pass (regression)
- 2nd+ write WITH an approved plan (ExitPlanMode tool_result, is_error=False)
  anywhere in the session -> pass (Chantier C)
- A REJECTED plan (ExitPlanMode tool_result is_error=True) does NOT count
- An IN-FLIGHT plan (ExitPlanMode tool_use with no result yet) does NOT count

Transcript shape mirrors the Claude Code JSONL: top-level {role, content}.
Real user messages carry string (or non-tool_result) content and act as the
turn boundary; tool_result deliveries are role=user but carry tool_result
blocks and must NOT cut the turn short.
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

HOOK = Path(__file__).resolve().parents[1] / "quality" / "reformulate-gate.py"


# --- Transcript builders -----------------------------------------------------


def _user(text: str) -> dict:
    return {"role": "user", "content": text}


def _assistant_text(text: str) -> dict:
    return {"role": "assistant", "content": [{"type": "text", "text": text}]}


def _assistant_tool(name: str, tid: str, inp: dict | None = None) -> dict:
    return {
        "role": "assistant",
        "content": [{"type": "tool_use", "name": name, "id": tid, "input": inp or {}}],
    }


def _tool_result(tid: str, is_error: bool = False) -> dict:
    return {
        "role": "user",
        "content": [{"type": "tool_result", "tool_use_id": tid, "is_error": is_error}],
    }


def _write_transcript(tmp_path: Path, *entries: dict) -> Path:
    tmp_path.mkdir(parents=True, exist_ok=True)
    transcript = tmp_path / "transcript.jsonl"
    transcript.write_text(
        "\n".join(json.dumps(e) for e in entries) + "\n", encoding="utf-8"
    )
    return transcript


def _run(transcript: Path, file_path: str = "/repo/src/foo.py") -> subprocess.CompletedProcess:
    payload = {
        "tool_name": "Write",
        "tool_input": {"file_path": file_path, "content": "x = 1"},
        "transcript_path": str(transcript),
    }
    return subprocess.run(
        [sys.executable, str(HOOK)],
        input=json.dumps(payload).encode("utf-8"),
        capture_output=True,
        timeout=10,
    )


# A completed, non-error Write earlier in the turn -> makes this attempt the 2nd.
def _one_prior_write() -> list[dict]:
    return [
        _user("go"),
        _assistant_tool("Write", "w1", {"file_path": "/repo/src/a.py", "content": "a"}),
        _tool_result("w1", is_error=False),
    ]


# --- 1st write always passes -------------------------------------------------


def test_first_write_passes(tmp_path):
    transcript = _write_transcript(tmp_path, _user("go"))
    r = _run(transcript)
    assert r.returncode == 0
    assert r.stderr == b""


def test_skip_path_passes(tmp_path):
    # A file under .next/ is exempt regardless of count.
    transcript = _write_transcript(tmp_path, *_one_prior_write())
    r = _run(transcript, file_path="/repo/.next/server/x.js")
    assert r.returncode == 0


# --- 2nd write blocks without reformulation or plan --------------------------


def test_second_write_blocks_without_marker_or_plan(tmp_path):
    transcript = _write_transcript(tmp_path, *_one_prior_write())
    r = _run(transcript)
    assert r.returncode == 2
    assert b"BLOCKED" in r.stderr
    assert b"reformulation" in r.stderr.lower()


# --- 2nd write passes with a reformulation marker (regression) ---------------


def test_second_write_passes_with_reformulation_text(tmp_path):
    entries = _one_prior_write() + [
        _assistant_text("REFORMULATION: j'ai compris, fichiers touches: a.py b.py"),
    ]
    transcript = _write_transcript(tmp_path, *entries)
    r = _run(transcript)
    assert r.returncode == 0, f"reformulation text should pass: {r.stderr!r}"


# --- 2nd write passes with an APPROVED plan (Chantier C) ---------------------


def test_second_write_passes_with_approved_plan(tmp_path):
    entries = _one_prior_write() + [
        _assistant_tool("ExitPlanMode", "p1", {"plan": "do bricks 1-3"}),
        _tool_result("p1", is_error=False),
    ]
    transcript = _write_transcript(tmp_path, *entries)
    r = _run(transcript)
    assert r.returncode == 0, f"approved plan should pass: {r.stderr!r}"
    assert r.stderr == b""


# --- A rejected plan does NOT count ------------------------------------------


def test_rejected_plan_does_not_count(tmp_path):
    entries = _one_prior_write() + [
        _assistant_tool("ExitPlanMode", "p1", {"plan": "do bricks"}),
        _tool_result("p1", is_error=True),
    ]
    transcript = _write_transcript(tmp_path, *entries)
    r = _run(transcript)
    assert r.returncode == 2, "rejected plan must not satisfy the gate"


# --- An in-flight plan (no result yet) does NOT count ------------------------


def test_inflight_plan_does_not_count(tmp_path):
    entries = _one_prior_write() + [
        _assistant_tool("ExitPlanMode", "p1", {"plan": "do bricks"}),
        # no tool_result for p1
    ]
    transcript = _write_transcript(tmp_path, *entries)
    r = _run(transcript)
    assert r.returncode == 2, "in-flight plan must not satisfy the gate"


# --- Robustness: empty / malformed input -------------------------------------


def test_empty_stdin_passes():
    r = subprocess.run(
        [sys.executable, str(HOOK)],
        input=b"",
        capture_output=True,
        timeout=10,
    )
    assert r.returncode == 0
