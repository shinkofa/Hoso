# /rollback-last

Safely rollback the last commit or changes.

## Steps
1. Show current state: `git log --oneline -5` + `git status`
2. Ask Jay: rollback last commit or unstaged changes?
3. If last commit: `git revert HEAD` (creates new commit, preserves history)
4. NEVER use `git reset --hard` without explicit Jay approval
5. If Docker involved: rebuild and redeploy
6. Verify: tests pass after rollback
