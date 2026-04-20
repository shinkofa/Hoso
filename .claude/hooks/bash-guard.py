#!/usr/bin/env python3
"""Unified Bash PreToolUse guard — all checks in one script.

RECOVERY PRINCIPLE: Every BLOCKED/WARNING message MUST include
a concrete recovery action so Takumi knows what to do next.
"""

import json
import re
import subprocess
import sys


def read_input():
    raw = sys.stdin.read()
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        data = {}
    command = data.get("command", "")
    return raw, command


def check_secrets(raw):
    patterns = [
        r"sk-[a-zA-Z0-9]{20,}",
        r"sk_live_[a-zA-Z0-9]+",
        r"sk_test_[a-zA-Z0-9]+",
        r"pk_live_[a-zA-Z0-9]+",
        r"ghp_[a-zA-Z0-9]{36}",
        r"gho_[a-zA-Z0-9]{36}",
        r"github_pat_[a-zA-Z0-9]+",
        r"Bearer [a-zA-Z0-9._-]+",
        r"PRIVATE KEY",
    ]
    for pattern in patterns:
        if re.search(pattern, raw, re.IGNORECASE):
            return (
                f"BLOCKED: Secret pattern detected ({pattern}). "
                "RECOVERY: Replace the secret with an environment variable "
                "reference (e.g., $VAR or process.env.VAR). "
                "Store the value in .env (gitignored). Retry without the secret."
            )
    if re.search(r'password\s*[:=]\s*"[^$][^"]{3,}"', raw, re.IGNORECASE):
        return (
            "BLOCKED: Hardcoded password detected. "
            "RECOVERY: Move password to .env file, "
            "reference via environment variable, then retry."
        )
    return None


def check_destructive(raw):
    if re.search(r"rm -rf|rm -fr|rmdir /s", raw):
        safe = re.search(
            r"(node_modules|\.next|__pycache__|\.cache|\.pytest_cache|dist/\.)",
            raw,
        )
        if not safe:
            return (
                "BLOCKED: rm -rf on non-cache directory. "
                "RECOVERY: Use 'mv <target> <target>-backup' instead. "
                "If deletion is truly needed, ask Jay for explicit confirmation."
            )
    if re.search(
        r"DROP (TABLE|DATABASE|SCHEMA)|TRUNCATE |DELETE FROM .* WHERE 1|DELETE FROM [a-z]+ *;",
        raw,
        re.IGNORECASE,
    ):
        return (
            "BLOCKED: Destructive SQL detected. "
            "RECOVERY: Run pg_dump backup first, then ask Jay for confirmation. "
            "Never execute destructive SQL without a verified backup."
        )
    return None


def check_no_verify(command):
    stripped = re.sub(r'"[^"]*"', "", command)
    stripped = re.sub(r"'[^']*'", "", stripped)
    if re.search(r"--no-verify", stripped):
        return (
            "BLOCKED: --no-verify bypasses all pre-commit hooks. "
            "RECOVERY: Remove --no-verify from your command. "
            "If a hook is failing, fix the underlying issue. "
            "Run the commit without --no-verify."
        )
    return None


def check_git_add_broad(command):
    if re.search(r"git add (\.|--all|-A)(\s|\"|\;|&&|\||\)|$)", command):
        return (
            "BLOCKED: Broad git add detected. "
            "RECOVERY: Use 'git add <specific files>' instead. "
            "List the files you intend to commit and add them by name. "
            "This prevents accidentally staging .env, credentials, or large binaries."
        )
    return None


def check_force_push_main(command):
    if not re.search(r"git push.*(--force|--force-with-lease|-f\b)", command):
        return None
    if re.search(r"\b(main|master)\b", command):
        return (
            "BLOCKED: Force push to main/master is forbidden. "
            "RECOVERY: Use a feature branch. "
            "Update main via regular merge or rebase workflow."
        )
    return None


def check_force_push_warn(command):
    if not re.search(r"git push.*(--force|--force-with-lease|-f\b)", command):
        return None
    if re.search(r"\b(main|master)\b", command):
        return None
    return (
        "WARNING: Force push on non-main branch. "
        "ACTION: Verify the remote branch can be safely overwritten."
    )


def check_conventional_commit(command, raw):
    if "git commit" not in command or "-m" not in command:
        return None
    if re.search(r"--amend", command):
        return None
    types = r"(feat|fix|refactor|docs|chore|test|perf|ci|style)"
    if not re.search(types + r"(\([a-zA-Z0-9_-]+\))?:", raw):
        return (
            "WARNING: Commit message not in Conventional Commits format. "
            "ACTION: Use 'type(scope): description'. "
            "Types: feat, fix, refactor, docs, chore, test, perf, ci, style."
        )
    return None


def check_co_authored_by(command, raw):
    if "git commit" not in command or "-m" not in command:
        return None
    if re.search(r"--amend", command):
        return None
    if "Co-Authored-By:" not in raw:
        return (
            "WARNING: Commit missing Co-Authored-By line. "
            'ACTION: Add \'Co-Authored-By: Takumi "IA Dev Partner"\' '
            "at the end of the commit message."
        )
    return None


def check_refactor_file_count(raw):
    if "git commit" not in raw:
        return None
    msg_match = re.search(
        r"(feat|fix|refactor|docs|chore|test|perf|ci|style)(\([a-zA-Z0-9_-]+\))?:.*",
        raw,
    )
    if not msg_match or "refactor" not in msg_match.group(0).lower():
        return None
    try:
        result = subprocess.run(
            ["git", "diff", "--cached", "--name-only"],
            capture_output=True, text=True, timeout=5,
        )
        staged = len([f for f in result.stdout.strip().split("\n") if f])
        if staged > 3:
            return (
                f"WARNING: Refactor commit touches {staged} files (max: 3). "
                "ACTION: Split into smaller commits. "
                "Use 'git reset HEAD <files>' to unstage excess, "
                "commit first batch (max 3 related files), then commit the rest."
            )
    except (subprocess.TimeoutExpired, FileNotFoundError):
        pass
    return None


def check_deploy(raw):
    deploy_patterns = [
        r"scp .* vps|rsync .* vps",
        r"ssh .*(deploy|restart|systemctl)",
        r"docker compose.*up.*-d.*vps|docker-compose.*up.*-d.*vps",
    ]
    is_deploy = any(re.search(p, raw, re.IGNORECASE) for p in deploy_patterns)
    if is_deploy:
        return (
            "WARNING: VPS deploy detected. "
            "ACTION: Before proceeding, verify: (1) all tests pass, "
            "(2) backup exists (pg_dump or git tag), (3) no uncommitted changes. "
            "If all checks pass, continue. If not, run the missing checks first."
        )
    return None


def check_db_migration(raw):
    if re.search(r"alembic upgrade|prisma migrate|prisma db push", raw, re.IGNORECASE):
        return (
            "WARNING: DB migration detected. "
            "ACTION: Run pg_dump backup BEFORE the migration. "
            "If already done in this session, continue. If not, run: "
            "pg_dump -Fc <dbname> > backup-$(date +%Y%m%d-%H%M).dump, then retry."
        )
    return None


def check_dependency_version(raw):
    if re.search(r"npm install [a-z@]|pnpm add [a-z@]|pip install [a-z]|yarn add [a-z@]", raw, re.IGNORECASE):
        if re.search(r"@[0-9]+\.[0-9]+|==[0-9]+\.[0-9]+|>=[0-9]+\.[0-9]+", raw):
            return (
                "WARNING: Specific version in install command. "
                "ACTION: Verify this version exists via npm/pypi/web "
                "(training data is months stale). If already verified, continue."
            )
    return None


def main():
    raw, command = read_input()

    for msg in [check_secrets(raw), check_destructive(raw), check_no_verify(command), check_git_add_broad(command), check_force_push_main(command)]:
        if msg:
            print(msg, file=sys.stderr)
            sys.exit(2)

    for msg in [check_refactor_file_count(raw), check_deploy(raw), check_db_migration(raw), check_dependency_version(raw), check_conventional_commit(command, raw), check_co_authored_by(command, raw), check_force_push_warn(command)]:
        if msg:
            print(msg, file=sys.stderr)

    sys.exit(0)


if __name__ == "__main__":
    main()
