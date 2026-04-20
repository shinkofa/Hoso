---
name: dev
description: Feature development with gates. Research, Blueprint check, non-tech PREPARE, TDG, code, non-tech VALIDATE, Obsidian sync.
model: opus
---

# /dev — Develop a Feature

Execute these steps IN ORDER. No skipping. Atomic commits throughout.

## Steps

1. **RESEARCH**: **3-Layer check**: Before coding, verify this feature serves L3 (Shinkofa vision) and L2 (visibility). Consult Eichi for domain alignment. Search Eichi + web. Verify stack is current. Check Blueprint/CDC/PET alignment. **CRITICAL**: Verify ALL technology versions via web (npm/pypi). Training data is months stale. Never assume a version exists or doesn't exist without web confirmation.
2. **BLUEPRINT CHECK**: Gate 2 — CDC exists, Blueprint read, Obsidian project notes consulted, Eichi searched.
3. **NON-TECH PREPARE**: UX, Brand, Accessibility agents review BEFORE code starts. Decisions documented.
4. **TDG**: Write tests FIRST. Tests must fail (red) before writing implementation.
5. **CODE**: Implement. Atomic commits every logical unit. Backup tag every 3-4 commits.
6. **LINT**: Zero lint errors (Biome/Ruff). No exceptions.
7. **SECURITY**: Scan. Verify CSP doesn't block features. Test auth flows.
8. **I18N**: FR/EN/ES translations from start.
9. **VISIBILITY**: SEO meta, structured data, GEO-friendly content (if public-facing).
10. **TESTS**: All tests pass (unit + integration + e2e + anti-regression).
11. **NON-TECH VALIDATE**: UX, Accessibility, Brand review the result.
12. **DOCS**: Update Blueprint, CDC, PET to reflect reality.
13. **OBSIDIAN SYNC**: Update decisions, notes, bugs, next steps in Obsidian `01-Projets/[project].md` (flat structure post 2026-04-11, one file per project). The old nested `02-Projets/[project]/*.md` is LEGACY — never update there.

## Rules

- Gate 2 + Gate 3 must pass.
- Pre-existing errors: fix them, don't ignore them.
- Commit = commit + push (non-negotiable).
- Quality gates are BLOCKING — zero derogation.

See `mnk/05-Workflows.md` WF-04 and `mnk/09-Skills.md` for full details.
