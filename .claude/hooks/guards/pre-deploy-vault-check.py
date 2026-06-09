#!/usr/bin/env python3
"""D2 — PreToolUse Bash guard: block deploys with missing Vault secrets.

Trigger
-------
PreToolUse on a Bash tool call whose command matches a deploy verb
(docker compose up, systemctl restart, deploy.sh, vercel --prod, etc.).

Action
------
1. Locate `docs/architecture/env-vars.md` in the repo root. If absent →
   pass through (cannot enforce).
2. Parse env var → vault path mappings (line format `VAR -> path` OR
   markdown table `| VAR | path |`).
3. If the `vault` CLI is unavailable → pass through.
4. For each (VAR, path), run `vault kv get <path>`. If exit != 0 →
   collect as missing.
5. If any missing → exit 2 (BLOCK) with recovery instructions.

Reference: Plan Phase D2.
"""

from __future__ import annotations

import re
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "lib"))
from common import (  # noqa: E402
    block,
    find_repo_root,
    format_block,
    get_command,
    pass_through,
    read_hook_input,
)

# --- Deploy detection (mirrors smoke-test-required.py) ----------------------

_DEPLOY_PATTERNS = [
    re.compile(r"\bdocker\s+(?:compose\s+)?up\b"),
    re.compile(r"\bdocker\s+stack\s+deploy\b"),
    re.compile(r"\bdeploy\.sh\b"),
    re.compile(r"\bvercel\s+(?:--prod|deploy)\b"),
    re.compile(r"\bfly\s+deploy\b"),
    re.compile(r"\bnetlify\s+deploy\b"),
    re.compile(r"\brailway\s+up\b"),
    re.compile(r"\bgh\s+workflow\s+run\s+deploy\b"),
    re.compile(r"\bssh\s+\S+\s+.*?(?:docker|deploy|systemctl)\b"),
    re.compile(r"\bansible-playbook\b"),
    re.compile(r"\bkubectl\s+apply\b"),
    re.compile(r"\bhelm\s+upgrade\b"),
    re.compile(r"\bsystemctl\s+restart\b"),
    re.compile(r"\bgit\s+pull\b.*\b(?:docker|systemctl|restart)\b"),
]


def _looks_like_deploy(cmd: str) -> bool:
    return any(p.search(cmd) for p in _DEPLOY_PATTERNS)


# --- env-vars.md parsing ----------------------------------------------------

_ARROW_RE = re.compile(
    r"^\s*([A-Z][A-Z0-9_]{2,})\s*-+>\s*([A-Za-z0-9_/\-\.]+?)\s*$",
    re.MULTILINE,
)
_TABLE_ROW_RE = re.compile(
    r"^\s*\|\s*([A-Z][A-Z0-9_]{2,})\s*\|\s*([A-Za-z0-9_/\-\.]+?)\s*\|",
    re.MULTILINE,
)


def _parse_env_vars(content: str) -> list[tuple[str, str]]:
    """Return list of (VAR_NAME, vault_path) tuples in declaration order."""
    pairs: list[tuple[str, str]] = []
    seen: set[str] = set()

    for m in _ARROW_RE.finditer(content):
        name, path = m.group(1), m.group(2)
        if name not in seen:
            pairs.append((name, path))
            seen.add(name)

    for m in _TABLE_ROW_RE.finditer(content):
        name, path = m.group(1), m.group(2)
        if "/" not in path:
            # Filter header row (e.g. "Variable | Vault path") — no slash.
            continue
        if name not in seen:
            pairs.append((name, path))
            seen.add(name)

    return pairs


# --- Vault probe ------------------------------------------------------------


def _vault_has(path: str) -> bool:
    """Return True if `vault kv get <path>` returns 0."""
    try:
        r = subprocess.run(
            ["vault", "kv", "get", path],
            capture_output=True,
            timeout=10,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return False
    return r.returncode == 0


def _vault_available() -> bool:
    return shutil.which("vault") is not None


# --- Main -------------------------------------------------------------------


def main() -> None:
    _, data = read_hook_input()
    cmd = get_command(data)
    if not cmd or not _looks_like_deploy(cmd):
        pass_through()

    repo_root = find_repo_root()
    env_vars_md = repo_root / "docs" / "architecture" / "env-vars.md"
    if not env_vars_md.exists():
        pass_through()

    if not _vault_available():
        pass_through()

    try:
        content = env_vars_md.read_text(encoding="utf-8")
    except OSError:
        pass_through()

    pairs = _parse_env_vars(content)
    if not pairs:
        pass_through()

    missing: list[tuple[str, str]] = [(n, p) for n, p in pairs if not _vault_has(p)]
    if not missing:
        pass_through()

    var_list = ", ".join(name for name, _ in missing)
    first_name, first_path = missing[0]
    block(format_block(
        reason=f"deploy aborted — required env var(s) missing from Vault: {var_list}",
        recovery=(
            f"create the missing secrets, e.g. `vault kv put {first_path} "
            f"{first_name.lower()}=<value>` (repeat for each), then re-run the deploy."
        ),
        reference="docs/architecture/env-vars.md",
    ))


if __name__ == "__main__":
    main()
