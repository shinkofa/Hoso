"""pre-code-veille-check.py — PreToolUse Write|Edit veille/SKB evidence guard.

Focus of this suite: Chantier D — a SENSITIVE change (dependency manifest,
new external import, version pin) backed by a [VEILLE] marker must ALSO be
backed by a REAL web tool call (WebSearch/WebFetch) somewhere in the session.
The marker alone (text) is no longer sufficient proof on the sensitive path.

Unchanged behaviors locked here as regressions:
- sensitive change + [SKB] marker -> BLOCK (Layer B: only [VEILLE] accepted)
- non-sensitive code + [SKB] marker -> pass (no web proof required off the
  sensitive path)
- missing marker -> BLOCK

Transcript shape mirrors Claude Code JSONL: top-level {role, content}.
"""

from __future__ import annotations

import json
import subprocess
import sys
import uuid
from pathlib import Path

HOOK = Path(__file__).resolve().parents[1] / "guards" / "pre-code-veille-check.py"


# --- Transcript builders -----------------------------------------------------


def _veille_marker(text: str = "[VEILLE] phoenix@1.8.8 verifie 2026-06-12 via hex.pm") -> dict:
    return {"role": "assistant", "content": [{"type": "text", "text": text}]}


def _skb_marker() -> dict:
    return {"role": "assistant", "content": [{"type": "text", "text": "[SKB] consulte: 11-Communication/Voice-Tone.md"}]}


def _web_call(name: str = "WebSearch") -> dict:
    return {
        "role": "assistant",
        "content": [{"type": "tool_use", "name": name, "id": "s1", "input": {"query": "phoenix latest version"}}],
    }


def _write_transcript(tmp_path: Path, *entries: dict) -> Path:
    tmp_path.mkdir(parents=True, exist_ok=True)
    transcript = tmp_path / "transcript.jsonl"
    transcript.write_text("\n".join(json.dumps(e) for e in entries) + "\n", encoding="utf-8")
    return transcript


def _run(transcript: Path | None, *, file_path: str, content: str,
         old_string: str = "") -> subprocess.CompletedProcess:
    payload: dict = {
        "tool_name": "Write",
        "tool_input": {"file_path": file_path, "content": content},
        "session_id": f"test-{uuid.uuid4().hex[:12]}",
    }
    if old_string:
        payload["tool_input"]["old_string"] = old_string
    if transcript is not None:
        payload["transcript_path"] = str(transcript)
    return subprocess.run(
        [sys.executable, str(HOOK)],
        input=json.dumps(payload).encode("utf-8"),
        capture_output=True,
        timeout=10,
    )


# --- Chantier D: sensitive + VEILLE needs a real web call --------------------


def test_sensitive_veille_passes_with_web_call(tmp_path):
    transcript = _write_transcript(tmp_path, _veille_marker(), _web_call("WebSearch"))
    r = _run(transcript, file_path="mix.exs", content='{:phoenix, "~> 1.8.8"}')
    assert r.returncode == 0, f"sensitive+VEILLE+web should pass: {r.stderr!r}"


def test_sensitive_veille_passes_with_webfetch(tmp_path):
    transcript = _write_transcript(tmp_path, _veille_marker(), _web_call("WebFetch"))
    r = _run(transcript, file_path="mix.exs", content='{:phoenix, "~> 1.8.8"}')
    assert r.returncode == 0, f"WebFetch should count: {r.stderr!r}"


def test_sensitive_veille_blocks_without_web_call(tmp_path):
    # Marker present but NO web tool call anywhere -> blocked (Chantier D).
    transcript = _write_transcript(tmp_path, _veille_marker())
    r = _run(transcript, file_path="mix.exs", content='{:phoenix, "~> 1.8.8"}')
    assert r.returncode == 2, "VEILLE text without a real web call must block"
    assert b"BLOCKED" in r.stderr


# --- Unchanged behaviors (regression locks) ----------------------------------


def test_sensitive_skb_still_blocks(tmp_path):
    # Layer B: [SKB] is insufficient for a sensitive change, regardless of web.
    transcript = _write_transcript(tmp_path, _skb_marker(), _web_call("WebSearch"))
    r = _run(transcript, file_path="mix.exs", content='{:phoenix, "~> 1.8.8"}')
    assert r.returncode == 2
    assert b"VEILLE" in r.stderr


def test_nonsensitive_skb_passes_without_web(tmp_path):
    # Plain source edit, no new import / version pin -> [SKB] is enough, no web proof.
    transcript = _write_transcript(tmp_path, _skb_marker())
    r = _run(
        transcript,
        file_path="lib/app/foo.ex",
        content="def hello, do: :world\n",
    )
    assert r.returncode == 0, f"non-sensitive [SKB] should pass: {r.stderr!r}"


def test_missing_marker_blocks(tmp_path):
    transcript = _write_transcript(tmp_path, {"role": "assistant", "content": [{"type": "text", "text": "no marker"}]})
    r = _run(transcript, file_path="lib/app/foo.ex", content="def hello, do: :world\n")
    assert r.returncode == 2
    assert b"BLOCKED" in r.stderr
