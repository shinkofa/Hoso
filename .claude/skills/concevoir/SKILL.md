---
name: concevoir
description: Full project conception workflow. Blueprint, POUR QUOI, research, CDC, PET, HTML presentation.
model: opus
---

# /concevoir — Design a Project or Feature

Execute these steps IN ORDER. No skipping. Wait for Jay's validation at step 12.

## Steps

1. **BLUEPRINT DETECT**: Identify project type from signals (see `mnk/10-Blueprints.md`).
2. **SKB (our brain)**: Search SKB (Shinkofa Knowledge Base) for ALL relevant domains — vision (MasterPlan), coaching, neurodiversity, marketing, gaming. Not just technical knowledge. SKB IS our collective brain.
3. **POUR QUOI (3 Layers)**: Define the WHY through all 3 layers: L3 — Does this serve Shinkofa's vision (invisible ecosystem respecting individuality)? L2 — How will this be PRESENTED to create magnetic visibility? L1 — What's the first step given current energy? All 3 must be documented.
4. **RESEARCH + VEILLE**: Search in 7 languages (EN, FR, ZH, JA, KO, DE, RU). State-of-art < 14 days. When evaluating architecture: consider the tri-layer direction (TypeScript visible + Elixir/Phoenix backend + Rust critical modules) as validated direction, POC pending. **CRITICAL**: Verify ALL technology recommendations, architecture patterns, and best practices via web. Training data is months stale — every recommendation based on stale knowledge costs time and money downstream.
5. **NON-TECH AGENTS**: Invoke UX, Brand, Pedagogy, Content, Gaming agents to review the concept.

6. **BLUEPRINT** (BLOCKING): Write or update `docs/Blueprint.md`.
   - **Risk Classification** (BLOCKING): classify EVERY module/component as Critical / Sensitive / Standard / Tooling per `mnk/06-Quality.md`. This drives coverage floors (95% / 90% / 80% / 60%) for all subsequent development.
   - **Simplified FMEA** (BLOCKING on Critical modules): for each module classified Critical, document 3 most probable failure modes with severity and mitigation.
   - Blueprint score must reach >= 95% against the matching Blueprint template (`mnk/10-Blueprints.md`).

7. **CDC** (BLOCKING): Write `docs/CDC.md` via interactive Q&A with Jay.
   - **Human Quality Gates checklist** (BLOCKING on public-facing projects):
     - [ ] Cognitive Load: budget per common task (<= 5 decision points, zero synonyms for same concept)
     - [ ] Sensory Comfort: 16px min, 1.5 lh, 75ch max, reduced motion, zero autoplay, dark/light/high-contrast
     - [ ] Error Resilience: auto-save on forms > 3 fields, zero-blame messages with recovery path, confirmation on destructive actions
     - [ ] Adaptation: preference persistence between sessions, onboarding adaptation capture
     - [ ] Dignity: 0 dark patterns, 0 condescension, 0 data without visible UX impact, informed consent, depart in ≤ 2 clicks (see `rules/Dignity.md`)
   - **Feedback Widget** (BLOCKING on public projects and all Elixir projects): 2 clicks max, automatic context capture, zero PII. Reference: `mnk/06-Quality.md` + D25.
   - **Deviations**: any deviation from the Universal Project Checklist (`rules/Quality.md`) MUST be documented with explicit justification in the CDC. No silent omissions.

8. **PET** (BLOCKING): Write `docs/PET.md` (Plan d'Execution Technique). The PET MUST contain ALL of the following sections. Use `mnk/templates/PET-Template.md` as structural reference.

   | # | Section | Content |
   |---|---------|---------|
   | 1 | Principe | Brick-by-brick, TDG, backup cadence (tag every 3-4 commits) |
   | 2 | Risk Classification (D20) | Table: module → risk level → coverage floor → MC/DC yes/no |
   | 3 | Simplified FMEA (Gate 1) | 3 failure modes per Critical module with severity + mitigation |
   | 4 | Human Quality Gates (D13) | Measurable criteria per gate, thresholds, verification method. N/A with justification for non-public projects. |
   | 5 | Anti-Circular Testing Protocol | 3 layers: Layer 1 (PBT + mutation + fuzzing — mandatory), Layer 2 (writer/reviewer + test auditor + holdout — mandatory on critical), Layer 3 (cross-model review — recommended). Tools and timing per layer. |
   | 6 | Bidirectional Traceability (Gate 3) | Matrix: requirement → test(s), test → requirement(s) |
   | 7 | 5 Test Reliability Metrics | Targets: line coverage, empty tests (0), trivial tests (<10%), mock:assert ratio (<3:1), type coverage (100% new) |
   | 8 | Defensive Assertions (Gate 4) | List of critical functions + >= 2 assertions each |
   | 9 | PII Detection (Gate 7) | Configuration: tools, scope, automated vs manual |
   | 10 | Deviations | Table: what is deviated → justification → compensating measure |
   | 11 | Brick Overview | High-level ordered list of all implementation bricks |
   | 12 | Brick Details | Per brick: scope, files, impact analysis, tests, verification criteria |
   | 13 | Quality Gates (BLOCKING) | Final checklist: coverage met, lint clean, security clean, a11y clean, CWV targets |
   | 14 | Post-Deploy Verification (Gate 8) | Health checks, smoke tests, human quality gates verification, feedback widget active |
   | 15 | Risks and Mitigations | Known risks with probability, impact, mitigation strategy |
   | 16 | Decisions | Architectural and technical decisions made during conception |

   A PET missing any of sections 1-13 is BLOCKING — do not proceed to step 8.5.

8.5. **VERIFY PET** (BLOCKING): Cross-check the PET before presenting:
   - [ ] Cross-check against `mnk/06-Quality.md` (Quality Pyramid V2, enriched gates, anti-circular protocol, risk classification, 5 metrics)
   - [ ] Cross-check against `mnk/15-Human-Quality.md` (4 human gates, HECQ, ND-friendly design) — N/A only if project is explicitly non-public AND non-user-facing
   - [ ] Cross-check against `mnk/improvements/004-QE-V2-Composition-Brief.md` (25 decisions)
   - [ ] List any gaps found and correct them before proceeding
   - If any gap is found: fix the PET, then re-verify. Do not proceed with gaps.

9. **PRESENT**: Generate HTML presentation (standalone file in docs/).
   - Include slide "Quality Framework": Quality Pyramid V2, risk classification table, human quality gates summary.
   - Include slide "Documented Deviations" if any deviations exist.

10. **OBSIDIAN SYNC**: Create `01-Projets/[project].md` in Obsidian (flat structure post 2026-04-11, one file per project) with sections: Notes, Décisions, Bugs, Prochaines étapes, Connexions. Add an entry to `_Index.md`. The old nested `02-Projets/[project]/*.md` structure is LEGACY — never create there.
11. **VALIDATE**: Wait for Jay's explicit approval before ANY coding.

## Questionnaire (6 questions for Jay)

1. What is this project? (describe like telling a friend)
2. Who is it for? (you / family / public / clients)
3. What does the user see? (website / phone app / bot / desktop / terminal)
4. Do people create accounts? (yes/no)
5. Do people pay for it? (yes/no)
6. Anything special? (coaching / gaming / AI / voice / streaming / ND-friendly)

After Jay's 6 answers, suggest ALL technical choices. Jay validates.

## Rules

- Gate 1 must pass before proceeding.
- Everything is potentially sellable — visibility-first.
- Non-tech agents intervene BEFORE coding decisions, not after.
- QE V2 is the floor — never generate a PET without the 16 mandatory sections.
- Human Quality Gates apply to ALL public-facing projects. Non-public projects may mark them N/A with justification.

See `mnk/05-Workflows.md` WF-03 and `mnk/09-Skills.md` for full details.
