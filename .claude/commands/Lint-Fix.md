# /lint-fix

Lint and auto-fix the current project.

## Steps
1. Detect project type (TypeScript or Python)
2. If TypeScript: run `npx biome check --fix .` or `npx eslint --fix .`
3. If Python: run `ruff check --fix .` and `ruff format .`
4. Report: files fixed, remaining issues
5. If zero errors: ready to commit
