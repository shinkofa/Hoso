#!/usr/bin/env python3
"""D3 — PreToolUse Bash guard: block deploys when `docs/registry/` is dirty.

Trigger
-------
PreToolUse on a Bash tool call whose command matches a deploy verb (same
set as D2 / smoke-test-required.py).

Action
------
The original spec asks to run `/update-registry` then `git diff --exit-code
docs/registry/`. A hook cannot invoke Claude Code skills, so the observable
proxy applied here is:

- If `docs/registry/` does not exist → pass (project not opted in).
- If `git status --porcelain docs/registry/` reports ANY entry (modified,
  added, deleted, untracked) → exit 2 (BLOCK). The registry was regenerated
  and not committed, OR new code lacks its registry entries.
- Otherwise → pass.

The user-facing recovery instructs them to run `/update-registry`, commit
the result, and re-run the deploy.

Reference: Plan Phase D3.
"""

from __future__ import annotations

import re
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

# Mirror D2 deploy-detection patterns.
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


def _registry_dirty(repo_root: Path) -> str | None:
    """Return the porcelain status block for docs/registry/ or None if clean."""
    try:
        r = subprocess.run(
            ["git", "status", "--porcelain", "docs/registry/"],
            capture_output=True,
            text=True,
            cwd=str(repo_root),
            timeout=10,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return None
    if r.returncode != 0:
        return None
    return r.stdout.strip() or None


def main() -> None:
    _, data = read_hook_input()
    cmd = get_command(data)
    if not cmd or not _looks_like_deploy(cmd):
        pass_through()

    repo_root = find_repo_root()
    registry_dir = repo_root / "docs" / "registry"
    if not registry_dir.exists():
        pass_through()

    dirty = _registry_dirty(repo_root)
    if not dirty:
        pass_through()

    # Show up to first 3 dirty entries in the message.
    sample_lines = dirty.splitlines()[:3]
    sample = "; ".join(line.strip() for line in sample_lines)
    block(format_block(
        reason=(
            "deploy aborted — `docs/registry/` has uncommitted changes "
            f"({sample})"
        ),
        recovery=(
            "run `/update-registry` to regenerate, review the diff, commit "
            "(`git add docs/registry && git commit -m 'chore(registry): sync'`), "
            "then re-run the deploy. If the registry is stale, regenerate "
            "and commit first."
        ),
        reference="Plan Phase D3 / docs/registry/",
    ))


if __name__ == "__main__":
    main()
