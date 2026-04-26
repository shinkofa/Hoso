# QE V2 Retroactive Audit Rule

> Tout artefact cree avant MNK-GoRin 4.0.0 est potentiellement non-conforme aux standards QE V2.
> L'application des nouveaux standards est AUTOMATIQUE. Ni Jay ni Takumi ne demande explicitement leur prise en compte.
> QE V2 = floor. Automatique. Non-negociable.

## Principle

QE V2 standards (25 decisions, enriched gates, human quality gates, anti-circular protocol, risk classification) apply to ALL artefacts — new and existing. Pre-4.0.0 artefacts have no "grandfather clause."

## Triggers

### 1. Session Start (existing project)

When opening a session on a project with existing Blueprint/CDC/PET, verify:

| Check | What | Action if missing |
|-------|------|-------------------|
| Risk Classification | Blueprint contains module → level table | Signal to Jay, propose update |
| Human Quality Gates | CDC contains 4 gates checklist (public projects) | Signal to Jay, propose update |
| PET QE V2 sections | PET contains 16 mandatory sections | Signal to Jay, propose update |
| Anti-Circular | PET references 3-layer protocol on critical paths | Signal to Jay, propose update |
| Feedback Widget | CDC/Blueprint mentions feedback widget (public projects) | Signal to Jay, propose update |

Do NOT auto-fix. Signal gaps, propose update, wait for Jay's decision.

### 2. Code Modification (existing code)

When modifying code in an existing project, verify in the scope of the change:

| Check | What | Action if missing |
|-------|------|-------------------|
| Defensive assertions | Critical functions have >= 2 assertions | Add in the same brick/PR |
| 5 test metrics | Tests respect: no empty, <10% trivial, mock:assert <3:1 | Fix in the same brick/PR |
| PII detection | Outputs checked for personal data | Configure if missing |
| Type coverage | New code has 100% type coverage (tsc strict / mypy strict) | Enforce in the same brick/PR |

These corrections are part of the work, not separate tasks.

### 3. Audit (/audit)

When running `/audit` on any project:

- Cross-check against full QE V2 checklist (25 decisions)
- Produce conformity report with actions
- Prioritize by risk classification (Critical modules first)

## Conformity Priority

| Priority | Projects | Rationale |
|----------|----------|-----------|
| 1 | Active with users (Kakusei, Michi-Shinkofa) | User impact |
| 2 | In development (Kobo, Koshin) | Build correctly from now |
| 3 | Maintenance (Hibiki, Michi-Niwa) | Opportunistic on next change |
| 4 | Archived | Exempt — no retroactive update |

## Behavioral Default

- QE V2 is the floor for ALL work, not an option to enable
- Existing artefacts are brought up to standard progressively, per session, per project
- Jay decides timing and priority — Takumi signals gaps and proposes, never forces
