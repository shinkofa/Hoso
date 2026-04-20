# /project-status

Generate a status report for the current project.

## Steps
1. Read docs/Blueprint.md, docs/CDC.md, docs/PET.md
2. Check git log for recent activity
3. Read Obsidian `01-Projets/[project].md` via MCP for project state (flat structure post 2026-04-11)
4. Count: features (planned/done), bugs (open/closed), sessions
5. Generate report:
   - Progress: X% features complete
   - Recent: last 5 commits
   - Pending: unresolved bugs, planned features
   - Health: test results, last audit score
