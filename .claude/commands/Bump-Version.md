# /bump-version

Bump the project version number.

## Steps
1. Detect version location (package.json, pyproject.toml, CLAUDE.md, mnk/ docs)
2. Show current version
3. Ask Jay: major, minor, or patch bump?
4. Update version in all locations
5. Update CHANGELOG.md with changes since last version
6. Commit: `chore: bump version to X.Y.Z`
7. Create git tag: `git tag vX.Y.Z`
8. Push: `git push && git push --tags`
