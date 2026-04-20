---
name: commit
description: Pre-commit review + CDC alignment + atomic check + stage + commit + push.
model: opus
---

# /commit — Commit Changes

Execute these steps IN ORDER. Gate 4 must pass.

## Steps

1. **NOTES CHECK**: Verify Obsidian project notes are synced with current decisions.
2. **QUALITY**: Run Code-Quality-Master agent review.
3. **CDC ALIGN**: Verify changes match the CDC scope.
4. **ATOMIC CHECK**: Is this a single logical change? If not, split into multiple commits.
5. **STAGE**: Stage specific files (`git add file1 file2`). Never `git add .` blindly.
6. **COMMIT**: Conventional commit message with Co-Authored-By.
7. **PUSH**: Immediate. Commit = commit + push. Non-negotiable.

## Rules

- Gate 4: Quality check passed, lint zero, no secrets, atomic change.
- No secrets in staged files (hook-enforced).
- No console.log in production code (hook warns).
- Backup tag every 3-4 commits.

See `mnk/05-Workflows.md` WF-06 for full details.
