# /breaking-changes-check

Check for breaking changes in dependencies.

## Steps
1. Detect project type
2. If TypeScript: `npm outdated --json` — flag major version jumps
3. If Python: `pip list --outdated --format=json` — flag major jumps
4. For each major update: search for breaking changes in changelog
5. Report: safe updates vs breaking changes
6. Suggest update order (least risk first)
