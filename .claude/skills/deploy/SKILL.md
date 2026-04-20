---
name: deploy
description: Production deployment. CDC check, veille, security, build, backup, deploy, health, smoke tests, TK log.
model: opus
---

# /deploy — Deploy to Production

Execute these steps IN ORDER. Gates 5 + 6 must pass. Fix = Deploy.

## Steps

1. **CDC CHECK**: Deployment matches CDC scope.
2. **VEILLE**: Verify no breaking changes in dependencies. **CRITICAL**: Before building, verify dependency versions are real and current via web. A phantom version in package.json = failed build = failed deploy.
3. **SECURITY**: Full scan. Verify CSP/headers don't break features. No critical/high CVEs.
4. **BUILD**: `--no-cache` after any fix.
5. **BACKUP**: Database backup BEFORE deploy (BLOCKING — no backup = no deploy).
6. **DEPLOY**: Blue-green when possible. Container running.
7. **HEALTH CHECK**: Verify all endpoints respond.
8. **SMOKE TESTS**: Critical user paths verified.
9. **DEPLOY LOG**: Record deployment in session report and Obsidian project notes.

## Rules

- Gate 5: Tests pass, security clean, Blueprint score >= 95%.
- Gate 6: Health check passed, monitoring active, smoke tests green.
- Fix = Deploy: "done" means deployed AND verified. Non-negotiable.
- Always backup before deploy. No exceptions.

See `mnk/05-Workflows.md` WF-07 for full details.
