---
name: dev
description: Feature development with gates. Research, Blueprint check, non-tech PREPARE, TDG, code, non-tech VALIDATE, Obsidian sync.
model: opus
---

# /dev — Develop a Feature

Execute these steps IN ORDER. No skipping. Atomic commits throughout.

## Steps

1. **RESEARCH**: **3-Layer check**: Before coding, verify this feature serves L3 (Shinkofa vision) and L2 (visibility). Consult SKB for domain alignment. Search SKB + web. Verify stack is current. Check Blueprint/CDC/PET alignment. Classify the feature's risk level (Critical/Sensitive/Standard/Tooling) per `mnk/06-Quality.md` Risk Classification — this determines coverage requirements for TDG. **Simplified FMEA** (Gate 1 enrichment): identify 3 most probable failure modes for the feature. **CRITICAL**: Verify ALL technology versions via web (npm/pypi). Training data is months stale. Never assume a version exists or doesn't exist without web confirmation.
2. **BLUEPRINT CHECK**: Gate 2 — CDC exists, Blueprint read, Obsidian project notes consulted, SKB searched. **Impact analysis** (Gate 2 enrichment): what breaks if this feature goes wrong? What other modules depend on this?
3. **NON-TECH PREPARE**: UX, Brand, Accessibility agents review BEFORE code starts. Decisions documented.
4. **TDG**: Write tests FIRST. Tests must fail (red) before writing implementation. Coverage target depends on risk classification: Critical 95% + MC/DC, Sensitive 90%, Standard 80%, Tooling 60%. If feature touches a critical path: apply Anti-Circular Testing Protocol Layer 1 (PBT + mutation testing). **Bidirectional traceability** (Gate 3 enrichment): each requirement has a test, each test traces to a requirement. **Defensive assertions** (Gate 4 enrichment): >= 2 per critical function. See `mnk/06-Quality.md`.
5. **CODE**: Implement. Atomic commits every logical unit. Backup tag every 3-4 commits. Compiler strictness = first poka-yoke: ensure `strict: true` (TS) or `mypy --strict` (Python). Errors at compile time > errors at runtime.
6. **LINT**: Zero lint errors (Biome/Ruff). No exceptions.
7. **SECURITY**: Scan. Verify CSP doesn't block features. Test auth flows. **Automated PII detection** (Gate 7 enrichment): verify outputs do not leak personal data.
8. **I18N**: FR/EN/ES translations from start.
9. **VISIBILITY**: SEO meta, structured data, GEO-friendly content (if public-facing).
10. **TESTS**: All tests pass (unit + integration + e2e + anti-regression). Verify **5 test reliability metrics**: empty tests (0), trivial tests (<10%), mock:assert ratio (<3:1), type coverage (100% new code), line coverage per risk level. **MC/DC** for complex conditions on critical paths.
11. **NON-TECH VALIDATE**: UX, Accessibility, Brand review the result. On public-facing features: verify 4 Human Quality Gates (Cognitive Load ≤ 5 decision points, Sensory Comfort = `prefers-reduced-motion` 100%, Error Resilience = auto-save on forms > 3 fields, Adaptation = preference persistence). Verify **Feedback Widget** is present and functional (BLOCKING on public platforms). See `mnk/15-Human-Quality.md`.
12. **DOCS**: Update Blueprint, CDC, PET to reflect reality. Document any **deviations** from Universal Project Checklist with justification.
13. **OBSIDIAN SYNC**: Update decisions, notes, bugs, next steps in Obsidian `01-Projets/[project].md` (flat structure post 2026-04-11, one file per project). The old nested `02-Projets/[project]/*.md` is LEGACY — never update there.

## Rules

- Gate 2 + Gate 3 must pass.
- Pre-existing errors: fix them, don't ignore them.
- Commit = commit + push (non-negotiable).
- Quality gates are BLOCKING — zero derogation.
- For critical paths: recommend a dedicated Test Auditor session (Anti-Circular Layer 2). Propose to Jay, do not auto-launch.

See `mnk/05-Workflows.md` WF-04 and `mnk/09-Skills.md` for full details.
