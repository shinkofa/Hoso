#!/usr/bin/env python3
"""Unified Write|Edit PostToolUse guard — all checks in one script.

RECOVERY PRINCIPLE: Every BLOCKED/WARNING message MUST include
a concrete recovery action so Takumi knows what to do next.
"""

import json
import os
import re
import subprocess
import sys


def read_input():
    raw = sys.stdin.read()
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        data = {}
    return data


def get_file_path(data):
    file_path = data.get("file_path", "")
    if not file_path:
        return None
    return file_path.replace("\\\\", "/").replace("\\", "/")


def check_file_size(file_path):
    """WARNING at 300 lines, BLOCKING at 500 lines.
    Exempt: .md, .json, type schemas, configs, tests, migrations, generated.
    """
    ext = os.path.splitext(file_path)[1].lstrip(".")

    exempt_extensions = ("md", "json", "lock", "svg", "csv", "sql")
    if ext in exempt_extensions:
        return None

    exempt_patterns = (
        ".test.", ".spec.", ".stories.",
        "migrations/", "migration/", "seed/", "fixture/",
        "config.", ".config.",
        "/dist/",
        "validation",
        ".d.ts",
        "schema", "types/",
    )
    if any(p in file_path for p in exempt_patterns):
        return None

    if not os.path.isfile(file_path):
        return None

    try:
        with open(file_path, "r", encoding="utf-8", errors="replace") as f:
            lines = sum(1 for _ in f)
    except OSError:
        return None

    if lines > 500:
        return (
            f"BLOCKED: File {file_path} has {lines} lines (max 500). "
            "RECOVERY: Split this file into smaller, cohesive modules. "
            "Extract logical sections into separate files, update imports. "
            "Principle: readability over size — each file = one concept."
        )
    if lines > 300:
        return (
            f"WARNING: File {file_path} has {lines} lines (ideal: <300). "
            "ACTION: Consider if this file can be split into more cohesive modules. "
            "If the file is logically cohesive (one concept, low complexity), continue. "
            "BLOCKING threshold is 500 lines."
        )
    return None


def check_utf8_bom(file_path):
    try:
        with open(file_path, "rb") as f:
            header = f.read(3)
    except OSError:
        return None

    if header == b"\xef\xbb\xbf":
        return (
            f"BLOCKED: UTF-8 BOM detected in {file_path}. "
            "RECOVERY: Re-write the file content without BOM. "
            "Use Write tool with the same content (Claude Code writes UTF-8 without BOM by default)."
        )
    if header[:2] in (b"\xff\xfe", b"\xfe\xff"):
        return (
            f"BLOCKED: Non-UTF-8 encoding detected in {file_path} (UTF-16). "
            "RECOVERY: Re-write the file using the Write tool (outputs UTF-8 by default)."
        )
    return None


def check_utf8_validity(file_path):
    text_extensions = (
        "ts", "tsx", "js", "jsx", "py", "sh", "md",
        "json", "yaml", "yml", "toml", "css", "scss", "html", "sql",
    )
    ext = os.path.splitext(file_path)[1].lstrip(".")
    if ext not in text_extensions:
        return None

    try:
        with open(file_path, "rb") as f:
            content = f.read()
        content.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        return (
            f"BLOCKED: Invalid UTF-8 sequences in {file_path}. "
            "RECOVERY: Read the file, identify non-UTF-8 characters, "
            "replace with proper UTF-8 equivalents, re-write the file."
        )
    except OSError:
        pass
    return None


def _flatten_keys(obj, prefix=""):
    keys = set()
    if isinstance(obj, dict):
        for k, v in obj.items():
            full = f"{prefix}.{k}" if prefix else k
            if isinstance(v, dict):
                keys |= _flatten_keys(v, full)
            else:
                keys.add(full)
    return keys


def check_i18n_locales(file_path):
    normalized = file_path.replace("\\", "/")
    match = re.search(r"(.+/locales/)(fr|en|es)/(.+\.json)$", normalized)
    if not match:
        return None
    base_dir, _, filename = match.group(1), match.group(2), match.group(3)

    locales = {}
    for locale in ("fr", "en", "es"):
        locale_file = f"{base_dir}{locale}/{filename}"
        if os.path.isfile(locale_file):
            try:
                with open(locale_file, "r", encoding="utf-8") as f:
                    locales[locale] = _flatten_keys(json.load(f))
            except (json.JSONDecodeError, OSError):
                pass

    if len(locales) < 2:
        return None

    all_keys = set()
    for keys in locales.values():
        all_keys |= keys

    missing = {}
    for locale, keys in locales.items():
        diff = all_keys - keys
        if diff:
            missing[locale] = sorted(diff)[:5]

    if missing:
        details = "; ".join(
            f"{loc} missing: {', '.join(keys)}" for loc, keys in missing.items()
        )
        return (
            f"WARNING: i18n key mismatch in {filename}. {details}. "
            "ACTION: Add missing keys to all 3 locales (FR/EN/ES)."
        )
    return None


def check_console_log(file_path):
    ext = os.path.splitext(file_path)[1].lstrip(".")
    if ext not in ("ts", "tsx", "js", "jsx"):
        return None

    skip_patterns = (".test.", ".spec.", ".stories.")
    if any(p in file_path for p in skip_patterns):
        return None

    try:
        with open(file_path, "r", encoding="utf-8", errors="replace") as f:
            content = f.read()
        count = len(re.findall(r"console\.log", content))
        if count > 0:
            return (
                f"WARNING: {count} console.log found in {file_path}. "
                "ACTION: Remove all console.log statements "
                "(replace with proper logger if debug output is needed). "
                "Fix these before committing."
            )
    except OSError:
        pass
    return None


def main():
    data = read_input()
    file_path = get_file_path(data)
    if not file_path or not os.path.isfile(file_path):
        sys.exit(0)

    blockers = [
        check_utf8_bom(file_path),
        check_utf8_validity(file_path),
    ]
    for msg in blockers:
        if msg:
            print(msg, file=sys.stderr)
            sys.exit(2)

    size_msg = check_file_size(file_path)
    if size_msg and size_msg.startswith("BLOCKED"):
        print(size_msg, file=sys.stderr)
        sys.exit(2)

    warnings = [size_msg, check_console_log(file_path), check_i18n_locales(file_path)]
    for msg in warnings:
        if msg:
            print(msg, file=sys.stderr)

    sys.exit(0)


if __name__ == "__main__":
    main()
