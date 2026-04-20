# /test-coverage

Run tests and generate coverage report.

## Steps
1. Detect project type and test framework
2. If TypeScript: `npx vitest run --coverage`
3. If Python: `pytest --cov --cov-report=term-missing`
4. Compare against thresholds:
   - Global: >= 80%
   - Auth/Payment/DB: >= 95%
5. Report: coverage %, uncovered files, failing tests
6. BLOCKING: below threshold = must add tests before commit
