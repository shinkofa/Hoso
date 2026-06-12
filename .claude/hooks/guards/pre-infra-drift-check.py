#!/usr/bin/env python3
"""D3-infra — PreToolUse Bash guard: run Shinkofa-Infra registry drift check
before any infra-affecting (deploy-class) command, and BLOCK on drift.

Why
---
The `/deploy` skill already runs `Shinkofa-Infra/registry/scripts/check-drift.py`
at step 5. But deploys done by hand (`ssh vps ... docker ...`, `systemctl
restart`) bypass the skill. This hook makes the drift check AUTOMATIC on every
deploy-class command, wherever it is issued — so the infra registry can never
silently diverge from the live VPS again.

Trigger
-------
PreToolUse on a Bash command matching a deploy verb (same family as
pre-deploy-registry-check.py / smoke-test-required.py).

Action
------
- Locate `<workspace>/Shinkofa-Infra/registry/scripts/check-drift.py` as a
  sibling of the current repo (or `~/apps/Shinkofa-Infra/...` on the VPS).
- Not found → pass (project not opted in / no registry available).
- Run it (read-only SSH probe inside, ~60s timeout):
    exit 0 → registry matches VPS → pass.
    exit 1 → DRIFT → BLOCK (exit 2) with the drift report.
    exit 2 → VPS probe failed (unreachable) → WARN, do NOT block ops.
    other / timeout → WARN, do NOT block.

Reference: Conception-Globale-Refonte §3 registre infra + CLEANUP-TODO
"mécanique anti-dérive". Companion of pre-deploy-registry-check.py (code registry).
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "lib"))
from common import (  # noqa: E402
    block,
    find_repo_root,
    format_block,
    format_warn,
    get_command,
    looks_like_deploy,
    pass_through,
    read_hook_input,
    warn,
)


_PROBE_TIMEOUT = 75  # check-drift does one ssh round-trip (its own 60s timeout)


def _find_check_drift(repo_root: Path) -> Path | None:
    """Locate the infra registry check-drift script across machines.

    Candidates, in order:
      1. sibling of the current repo: <repo>/../Shinkofa-Infra/...
      2. VPS layout: ~/apps/Shinkofa-Infra/...
    """
    rel = Path("Shinkofa-Infra") / "registry" / "scripts" / "check-drift.py"
    candidates = [
        repo_root.parent / rel,
        Path.home() / "apps" / rel,
    ]
    for c in candidates:
        if c.is_file():
            return c
    return None


def main() -> None:
    _, data = read_hook_input()
    cmd = get_command(data)
    if not cmd or not looks_like_deploy(cmd):
        pass_through()

    repo_root = find_repo_root()
    script = _find_check_drift(repo_root)
    if script is None:
        pass_through()

    try:
        r = subprocess.run(
            [sys.executable, str(script)],
            capture_output=True,
            text=True,
            timeout=_PROBE_TIMEOUT,
            cwd=str(script.parent),
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        warn(format_warn(
            reason=f"infra registry drift check could not run ({exc})",
            action="run `python Shinkofa-Infra/registry/scripts/check-drift.py` "
                   "manually before deploying; verify VPS reachability.",
            reference="registre infra / check-drift.py",
        ))
        pass_through()

    report = (r.stdout or "").strip()

    if r.returncode == 0:
        pass_through()

    if r.returncode == 1:
        sample = "; ".join(report.splitlines()[:6]) or "drift detected"
        block(format_block(
            reason=f"infra registry drift vs live VPS — deploy aborted ({sample})",
            recovery="reconcile `Shinkofa-Infra/registry/infra.yaml` (or projects.yaml) "
                     "with the live VPS, regenerate views (`gen-views.py`), commit, "
                     "then re-run. If the live change is intended, declare it in the "
                     "registry first.",
            reference="registre infra / check-drift.py",
        ))

    # exit 2 (probe failed) or any other code → WARN but do not block ops.
    warn(format_warn(
        reason=f"infra registry drift check inconclusive (exit {r.returncode}) — "
               f"VPS may be unreachable",
        action="verify VPS reachability and run check-drift.py manually before deploy.",
        reference="registre infra / check-drift.py",
    ))
    pass_through()


if __name__ == "__main__":
    main()
