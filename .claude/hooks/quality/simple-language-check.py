#!/usr/bin/env python3
"""Simple Language check — Stop hook (WARN + state persistence for next-turn injection).

Enforces the 8 Simple Language constraints from rules/Honesty.md
on Takumi's most recent assistant response.

Scans the latest assistant text from the transcript (the response
Jay just read) and flags violations:

  1. Sentence > 25 words (constraint 5)
  2. Paragraph > 3 sentences (constraint 5)
  3. More than 1 acronym per paragraph (constraint 2)
  4. More than 1 acronym per sentence (constraint 6 — Jay 2026-05-31)
  5. Response > 3 prose paragraphs (constraint 8 — Jay 2026-06-07, short by default)

Code blocks (``` and `inline`) are stripped before analysis —
jargon inside code is allowed (variable names, error messages).
Tables, lists, blockquotes are NOT counted as prose paragraphs.

Output: WARNING on stderr if violations detected (Jay sees it).
        Plus persists to state file simple-language-violations-<session>.json
        so the UserPromptSubmit companion hook (simple-language-inject.py)
        can re-inject the violations into Takumi's NEXT context — Option A
        equivalent-of-BLOCKING for a Stop hook (Jay 2026-06-01).
        Exit 0 always (never blocks the response that was already emitted).

Status: Functional BLOCKING via Option A (next-turn injection). Promoted
        from pure WARN 2026-06-01 after recurring drift despite written rule.

Source: rules/Honesty.md "Simple Language — Anti-Jargon Rule"
        + Jay frustrations #2/#3/#4 (2026-05-31, cadre Expert
        Monozukuri / collaborateur non-technique).
        + Jay 2026-06-01 — WARN alone insufficient, need observable cost.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

HOOK_DIR = Path(__file__).resolve().parent
LIB_DIR = HOOK_DIR.parent / "lib"
sys.path.insert(0, str(LIB_DIR))

from common import pass_through, read_hook_input  # noqa: E402
from session_state import write_state  # noqa: E402
from transcript_reader import iter_assistant_text  # noqa: E402


MAX_WORDS_PER_SENTENCE = 25
MAX_SENTENCES_PER_PARAGRAPH = 3
MAX_ACRONYMS_PER_PARAGRAPH = 1
MAX_ACRONYMS_PER_SENTENCE = 1
MAX_PROSE_PARAGRAPHS_PER_RESPONSE = 3

# Match fenced code blocks (```...```) and inline code (`...`)
_CODE_BLOCK_RE = re.compile(r"```.*?```", re.DOTALL)
_INLINE_CODE_RE = re.compile(r"`[^`]*`")

# Acronym = 2+ uppercase letters in a row (jargon proxy)
_ACRONYM_RE = re.compile(r"\b[A-Z]{2,}[A-Z0-9]*\b")

# Sentence splitter: ., !, ? followed by whitespace or EOL
_SENTENCE_SPLIT_RE = re.compile(r"[.!?]+(?:\s+|$)")

# Acronyms universal enough to skip flagging
_ACRONYM_ALLOWLIST = {
    "OK", "URL", "API", "CLI", "UI", "UX", "ID", "OS", "FAQ",
    "PDF", "HTML", "CSS", "JSON", "YAML", "TIME",
}


def _strip_code(text: str) -> str:
    """Remove fenced and inline code blocks."""
    text = _CODE_BLOCK_RE.sub("", text)
    text = _INLINE_CODE_RE.sub("", text)
    return text


def _split_paragraphs(text: str) -> list[str]:
    """Split text into non-empty paragraphs (double newline)."""
    return [p.strip() for p in re.split(r"\n\s*\n", text) if p.strip()]


def _split_sentences(paragraph: str) -> list[str]:
    """Split a paragraph into non-empty sentences."""
    parts = _SENTENCE_SPLIT_RE.split(paragraph)
    return [s.strip() for s in parts if s.strip()]


def _count_words(sentence: str) -> int:
    """Count words by splitting on whitespace, ignoring markdown markers."""
    cleaned = re.sub(r"[*_#>|\-`]+", " ", sentence)
    return len([w for w in cleaned.split() if w])


def _count_acronyms(paragraph: str) -> int:
    """Count non-allowlisted acronyms in paragraph."""
    found = _ACRONYM_RE.findall(paragraph)
    return sum(1 for a in found if a not in _ACRONYM_ALLOWLIST)


def _is_structural(para: str) -> bool:
    """True if paragraph is mostly a table / list / blockquote (not prose)."""
    lines = para.split("\n")
    structural = sum(
        1 for line in lines
        if line.lstrip().startswith(("|", "-", "*", ">"))
        or re.match(r"^\s*\d+\.\s", line)
    )
    return structural >= max(1, len(lines) // 2)


def _analyze(text: str) -> list[str]:
    """Return list of human-readable violations."""
    text = _strip_code(text)
    violations: list[str] = []
    paragraphs = _split_paragraphs(text)

    prose_count = 0
    for i, para in enumerate(paragraphs, 1):
        # Skip tables and lists (mostly structural, not prose)
        if _is_structural(para):
            continue
        prose_count += 1

        sentences = _split_sentences(para)
        if len(sentences) > MAX_SENTENCES_PER_PARAGRAPH:
            violations.append(
                f"Paragraphe {i}: {len(sentences)} phrases "
                f"(max {MAX_SENTENCES_PER_PARAGRAPH})"
            )

        for j, sent in enumerate(sentences, 1):
            n_words = _count_words(sent)
            if n_words > MAX_WORDS_PER_SENTENCE:
                preview = sent[:60].replace("\n", " ")
                violations.append(
                    f"Paragraphe {i} phrase {j}: {n_words} mots "
                    f"(max {MAX_WORDS_PER_SENTENCE}) - \"{preview}...\""
                )

            # Constraint 6 — densite jargon par phrase (Jay 2026-05-31)
            n_acronyms_sent = _count_acronyms(sent)
            if n_acronyms_sent > MAX_ACRONYMS_PER_SENTENCE:
                preview = sent[:60].replace("\n", " ")
                violations.append(
                    f"Paragraphe {i} phrase {j}: {n_acronyms_sent} acronymes "
                    f"dans la meme phrase (max {MAX_ACRONYMS_PER_SENTENCE} "
                    f"- Honesty.md contrainte 6) - \"{preview}...\""
                )

        n_acronyms = _count_acronyms(para)
        if n_acronyms > MAX_ACRONYMS_PER_PARAGRAPH:
            violations.append(
                f"Paragraphe {i}: {n_acronyms} acronymes non gloses "
                f"(max {MAX_ACRONYMS_PER_PARAGRAPH} - cf. Honesty.md "
                f"contrainte 2)"
            )

    # Constraint 8 — plafond longueur reponse (Jay 2026-05-31)
    if prose_count > MAX_PROSE_PARAGRAPHS_PER_RESPONSE:
        violations.append(
            f"Reponse: {prose_count} paragraphes de prose "
            f"(max {MAX_PROSE_PARAGRAPHS_PER_RESPONSE} - Honesty.md "
            f"contrainte 8). Couper sauf si Jay a demande un audit / brief."
        )

    return violations


def main() -> None:
    _, data = read_hook_input()
    transcript_path = data.get("transcript_path") or ""
    session_id = data.get("session_id") or ""
    if not transcript_path:
        pass_through()

    try:
        chunks = list(iter_assistant_text(transcript_path, limit=1))
    except Exception:
        pass_through()

    if not chunks:
        pass_through()

    text = chunks[0]
    violations = _analyze(text)

    if violations:
        # 1. WARN on stderr (Jay's console)
        sys.stderr.write(
            "[SIMPLE LANGUAGE WARN] Honesty.md contrainte violee dans la "
            "derniere reponse Takumi:\n"
        )
        for v in violations[:8]:
            sys.stderr.write(f"  - {v}\n")
        if len(violations) > 8:
            sys.stderr.write(f"  (+{len(violations) - 8} autres)\n")
        sys.stderr.write(
            "Reformuler au prochain tour: phrases courtes, jargon glose, "
            "tableau si comparaison, analogie si concept abstrait.\n"
        )
        sys.stderr.flush()

        # 2. Persist for next-turn injection (Option A — Jay 2026-06-01).
        # The companion UserPromptSubmit hook simple-language-inject.py
        # reads this state and injects the violations into Takumi's next
        # context, then clears `pending`. Equivalent-of-BLOCKING for a
        # Stop hook (which cannot rewrite an already-emitted response).
        try:
            write_state(
                "simple-language-violations",
                {"pending": True, "violations": violations[:20]},
                session_id=session_id or None,
            )
        except Exception:
            # State write is best-effort. Failure must not break the session.
            pass

    pass_through()


if __name__ == "__main__":
    main()
