# /security-scan

Run a security scan on the current project.

## Steps
1. Detect project type (TypeScript/Python)
2. If TypeScript: `npm audit --json`
3. If Python: `pip-audit --format=json`
4. Check for secrets in codebase: grep for API keys, tokens, passwords
5. Verify .env.example exists alongside any .env files
6. Check security headers if web project
7. Report: findings by severity (critical/high/medium/low)
8. BLOCKING: critical or high findings must be fixed before deploy
