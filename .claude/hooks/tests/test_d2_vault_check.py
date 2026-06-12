"""D2 — pre-deploy-vault-check.py

Tests the PreToolUse Bash hook that blocks deploys when env vars required
by `docs/architecture/env-vars.md` are missing from the local Vault.

The hook reads the repo's env-vars.md (parsed for `VAR_NAME -> vault/path`
or markdown table entries) and runs `vault kv get <path>` for each entry.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

HOOK = Path(__file__).resolve().parents[1] / "guards" / "pre-deploy-vault-check.py"

import pytest  # noqa: E402

# Linux-only integration suite. These tests stub the POSIX CLI the hook shells
# out to (`vault`) via a PATH stub. On Windows, subprocess cannot intercept a
# bare `vault` call through a PATH stub: shutil.which won't match an
# extension-less file and CreateProcess won't run a .cmd/.bat for a list-form
# call. The hook then falls through to pass-through and the assertions fail for
# an environment reason, not a logic bug. The hook itself is cross-platform
# Python. Proven green on Linux (VPS) 2026-06-12: 19 passed.
pytestmark = pytest.mark.skipif(
    sys.platform == "win32",
    reason="POSIX `vault` stub not interceptable via PATH on Windows; "
    "Linux-only integration suite. Hook is cross-platform; proven green on Linux VPS.",
)


def _make_repo(tmp_path: Path, env_vars_md: str | None) -> Path:
    """Create a fake repo with optional docs/architecture/env-vars.md."""
    repo = tmp_path / "repo"
    (repo / ".git").mkdir(parents=True)
    if env_vars_md is not None:
        arch = repo / "docs" / "architecture"
        arch.mkdir(parents=True)
        (arch / "env-vars.md").write_text(env_vars_md, encoding="utf-8")
    return repo


def _make_vault_stub(stub_dir: Path, *, present_paths: list[str]) -> None:
    """Create a `vault` stub that returns 0 for known paths, 1 otherwise."""
    stub_dir.mkdir(parents=True, exist_ok=True)
    stub = stub_dir / "vault"
    presence = "|".join(present_paths) if present_paths else "__NONE__"
    stub.write_text(
        "#!/usr/bin/env bash\n"
        "PATH_ARG=\"$3\"\n"
        f"KNOWN='{presence}'\n"
        "IFS='|' read -ra KNOWN_ARR <<< \"$KNOWN\"\n"
        "for p in \"${KNOWN_ARR[@]}\"; do\n"
        "  if [ \"$p\" = \"$PATH_ARG\" ]; then exit 0; fi\n"
        "done\n"
        "echo 'Error: no value found' >&2; exit 1\n"
    )
    stub.chmod(0o755)


def _run(payload: dict, *, cwd: Path, extra_path: str) -> subprocess.CompletedProcess:
    env = {**os.environ, "PATH": f"{extra_path}:{os.environ['PATH']}"}
    return subprocess.run(
        [sys.executable, str(HOOK)],
        input=json.dumps(payload).encode("utf-8"),
        capture_output=True,
        timeout=10,
        cwd=str(cwd),
        env=env,
    )


# --- Non-triggers (silent) ---------------------------------------------------


def test_silent_on_non_deploy_command(tmp_path):
    repo = _make_repo(tmp_path, env_vars_md=None)
    stub_dir = tmp_path / "bin"
    _make_vault_stub(stub_dir, present_paths=[])
    payload = {"tool_name": "Bash", "tool_input": {"command": "ls -la"}}
    r = _run(payload, cwd=repo, extra_path=str(stub_dir))
    assert r.returncode == 0
    assert r.stderr == b""


def test_silent_on_empty_stdin():
    r = subprocess.run([sys.executable, str(HOOK)], input=b"", capture_output=True, timeout=10)
    assert r.returncode == 0


def test_silent_when_env_vars_md_missing(tmp_path):
    """No env-vars.md → cannot enforce → pass through."""
    repo = _make_repo(tmp_path, env_vars_md=None)
    stub_dir = tmp_path / "bin"
    _make_vault_stub(stub_dir, present_paths=[])
    payload = {
        "tool_name": "Bash",
        "tool_input": {"command": "docker compose up -d"},
    }
    r = _run(payload, cwd=repo, extra_path=str(stub_dir))
    assert r.returncode == 0


def test_silent_when_vault_cli_unavailable(tmp_path):
    """No `vault` binary on PATH → cannot enforce → pass through."""
    env_vars = "DATABASE_URL -> kv/secret/kobo/database_url\n"
    repo = _make_repo(tmp_path, env_vars_md=env_vars)
    empty_bin = tmp_path / "empty"
    empty_bin.mkdir()
    payload = {
        "tool_name": "Bash",
        "tool_input": {"command": "docker compose up -d"},
    }
    env = {**os.environ, "PATH": f"{empty_bin}:/usr/bin:/bin"}
    r = subprocess.run(
        [sys.executable, str(HOOK)],
        input=json.dumps(payload).encode("utf-8"),
        capture_output=True,
        timeout=10,
        cwd=str(repo),
        env=env,
    )
    assert r.returncode == 0


# --- Triggers (BLOCK on missing) --------------------------------------------


def test_blocks_when_required_var_missing(tmp_path):
    env_vars = (
        "# Env vars required\n"
        "DATABASE_URL -> kv/secret/kobo/database_url\n"
        "STRIPE_KEY -> kv/secret/kobo/stripe_key\n"
    )
    repo = _make_repo(tmp_path, env_vars_md=env_vars)
    stub_dir = tmp_path / "bin"
    _make_vault_stub(stub_dir, present_paths=["kv/secret/kobo/database_url"])
    payload = {
        "tool_name": "Bash",
        "tool_input": {"command": "docker compose up -d"},
    }
    r = _run(payload, cwd=repo, extra_path=str(stub_dir))
    assert r.returncode == 2, f"expected BLOCK exit 2, got {r.returncode}, stderr={r.stderr!r}"
    assert b"STRIPE_KEY" in r.stderr
    assert b"BLOCKED" in r.stderr


def test_passes_when_all_vars_present(tmp_path):
    env_vars = (
        "DATABASE_URL -> kv/secret/kobo/database_url\n"
        "REDIS_URL -> kv/secret/kobo/redis_url\n"
    )
    repo = _make_repo(tmp_path, env_vars_md=env_vars)
    stub_dir = tmp_path / "bin"
    _make_vault_stub(stub_dir, present_paths=[
        "kv/secret/kobo/database_url",
        "kv/secret/kobo/redis_url",
    ])
    payload = {
        "tool_name": "Bash",
        "tool_input": {"command": "docker compose up -d"},
    }
    r = _run(payload, cwd=repo, extra_path=str(stub_dir))
    assert r.returncode == 0
    assert r.stderr == b""


def test_blocks_on_systemctl_restart(tmp_path):
    """`ssh host systemctl restart` also counts as deploy."""
    env_vars = "API_TOKEN -> kv/secret/kobo/api_token\n"
    repo = _make_repo(tmp_path, env_vars_md=env_vars)
    stub_dir = tmp_path / "bin"
    _make_vault_stub(stub_dir, present_paths=[])
    payload = {
        "tool_name": "Bash",
        "tool_input": {"command": "ssh vps systemctl restart kobo"},
    }
    r = _run(payload, cwd=repo, extra_path=str(stub_dir))
    assert r.returncode == 2
    assert b"API_TOKEN" in r.stderr


def test_parses_markdown_table_format(tmp_path):
    """env-vars.md as markdown table: | VAR | path |."""
    env_vars = (
        "| Variable | Vault path |\n"
        "|----------|------------|\n"
        "| DATABASE_URL | kv/secret/kobo/database_url |\n"
        "| STRIPE_KEY | kv/secret/kobo/stripe_key |\n"
    )
    repo = _make_repo(tmp_path, env_vars_md=env_vars)
    stub_dir = tmp_path / "bin"
    _make_vault_stub(stub_dir, present_paths=["kv/secret/kobo/database_url"])
    payload = {
        "tool_name": "Bash",
        "tool_input": {"command": "docker compose up -d"},
    }
    r = _run(payload, cwd=repo, extra_path=str(stub_dir))
    assert r.returncode == 2
    assert b"STRIPE_KEY" in r.stderr
