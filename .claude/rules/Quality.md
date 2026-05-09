# Quality — BLOCKING Gates

> Every rule in this file is BLOCKING. Zero derogation. Zero exception. Zero "we'll fix it later."

## Test-Driven Generation (TDG)

Write tests BEFORE code. Always. A binary test is the clearest goal for AI.

```
1. Write the test (what should happen)
2. Run it (it fails — red)
3. Write the code (make it pass — green)
4. Refactor when code smells appear (duplication, unclear naming, excessive complexity)
5. Verify tests still pass
```

## Test Strategy (3 levels)

| Level | Tool | What | When |
|-------|------|------|------|
| Unit | Vitest (TS) / pytest (Python) / ExUnit (Elixir) / cargo test (Rust) | Functions, logic, edge cases | Every commit |
| Integration | Playwright (TS) / pytest (Python) / ExUnit + Ecto.Sandbox (Elixir) / cargo test (Rust) | API routes, DB queries, auth flows | Every PR |
| E2E + Anti-regression | Playwright | Critical user paths | Pre-deploy |

### Test Execution Commands (by stack — BLOCKING)

The agent MUST know and use the correct test command for the project stack. "All tests pass" means running the actual command and verifying exit code 0.

| Stack | Unit Tests | Coverage | Notes |
|-------|-----------|----------|-------|
| TypeScript | `pnpm run test` or `npx vitest run` | `npx vitest run --coverage` | Mandatory pool config in vite.config.ts (see Test Runtime Hygiene) |
| Python | `pytest` | `pytest --cov=src --cov-report=term` | pytest-cov for coverage |
| Elixir | `mix test` | `mix test --cover` | ExCoveralls for CI. Ecto.Sandbox for DB isolation. |
| Rust | `cargo test` | `cargo tarpaulin --out Html --timeout 300` | cargo-tarpaulin recommended. `#[ignore]` without reason = BLOCKING. |

### Coverage Floors (BLOCKING)

| Scope | Minimum | Context | Consequence |
|-------|---------|---------|-------------|
| Global | 80% | All code | Pre-deploy blocked |
| Critical paths (see definition below) | 95% | No exceptions | Pre-deploy blocked |
| New features | 90% | Must ship with tests | Commit blocked |
| Lighthouse score | 90 | Performance audit | Pre-deploy blocked |
| axe violations (AA) | 0 | Accessibility | Pre-deploy blocked |
| Critical/High CVEs | 0 | Security | Pre-deploy blocked |

#### Critical Paths — exhaustive definition (BLOCKING)

Under literal reading (Opus 4.7), "critical paths" MUST be treated as the following enumerated set, and nothing else. A file or function qualifies as a critical path if AND ONLY IF it matches at least one of:

**By directory / path match** (case-insensitive, substring):
- `**/auth/**`, `**/authentication/**`, `**/authorization/**`, `**/sessions/**`, `**/oauth/**`, `**/jwt/**`, `**/passwords/**`, `**/2fa/**`, `**/mfa/**`
- `**/payment/**`, `**/payments/**`, `**/billing/**`, `**/subscription/**`, `**/subscriptions/**`, `**/stripe/**`, `**/invoices/**`, `**/refunds/**`, `**/checkout/**`
- `**/db/migrations/**`, `**/prisma/migrations/**`, `**/alembic/versions/**`, `**/priv/repo/migrations/**` (Ecto)
- `**/security/**`, `**/crypto/**`, `**/encryption/**`
- `**/rgpd/**`, `**/gdpr/**`, `**/user-data-export/**`, `**/user-data-delete/**`
- `**/webhooks/**` receiving payment / auth callbacks (explicit)

**By explicit tag**:
- Functions / classes decorated with `@critical` (Python), `/** @critical */` JSDoc (TS), `@tag :critical` (ExUnit), or `// @critical` comment (Rust)
- Files listed in a project-level `docs/critical-paths.md` registry (project opt-in)

**NOT critical paths** (standard 80% floor applies): UI components, content routes, blog pages, analytics, telemetry, A/B test helpers, dev tools, scripts, seed data, fixtures, docs generators, admin dashboards NOT touching auth/payment, feature flags NOT touching auth/payment.

Ambiguous case: if a file imports from a critical path module AND mutates state derived from it, treat it as critical. Otherwise standard floor. If still ambiguous → apply the MORE RESTRICTIVE (95%) per Escalation Over Assumption.

### Test Rules
- Name tests: `should_[action]_when_[condition]` (TS/Python), `test "action when condition"` (Elixir), `fn test_action_when_condition()` (Rust)
- Real database for integration tests (no mocks for DB — proven failure risk). Elixir: use `Ecto.Sandbox` with `:auto` mode.
- Mutation testing: Stryker 9.5+ (TS), mutmut (Python), Mutant.ex (Elixir), cargo-mutants (Rust). Monthly audit.
- Contract testing with Pact for cross-service APIs
- AI/LLM outputs: test structure and constraints, not exact content
- **Elixir-specific**: `ExUnit.Case` for unit, `async: true` only when no DB state shared. StreamData for property-based testing.
- **Rust-specific**: `#[test]` macro for unit tests, `proptest!` for PBT. Never `#[ignore]` without documented reason.

### Test Reliability Metrics (5 numbers, not 1)

A single coverage % is misleading. Five metrics tell the truth:

| Metric | Target | What it catches |
|--------|--------|-----------------|
| Line coverage | ≥ 80% (95% critical paths) | Unexercised code |
| Empty tests | 0 | Tests without assertions give false confidence |
| Trivial tests | < 10% of total | `assert x is not None` doesn't verify behavior |
| Mock:Assert ratio | < 3:1 per test | If mocks > assertions by 3:1, you're testing the mock, not the code |
| Type coverage (mypy/tsc strict) | 100% new code | Catches type-level bugs statically |
| Type specs (Elixir @spec + Dialyzer) | 100% new public functions | Type-level bugs caught statically (WARNING — objective, not BLOCKING yet) |

**Detection rules**:
- A test with zero `assert` or `pytest.raises` = empty test = BLOCKING
- A test with only identity checks (`is not None`, `isinstance`) = trivial = WARNING
- A test with > 20 lines for a < 10-line function = probably over-mocked = WARNING

A strict compiler is the first poka-yoke: it catches errors at build time. Subsequent gates become safety nets, not primary defense.

## Quality Pyramid V2

6 levels + Foundation. Full details: `mnk/06-Quality.md`.

| Level | Name | Measures | Automatable? |
|-------|------|----------|-------------|
| Foundation | Stack | Stable foundations with AI code gen? | Evaluation |
| L0 | Process | CI, hooks, gates | Entirely |
| L1 | Structural | Complexity, maintainability, security | Entirely |
| L2 | Functional | Tests pass, API contracts | Entirely |
| L3 | Cognitive | Mental effort (charge cognitive) | Partially |
| L4 | Emotional | Trust, frustration, energy cost | Mostly human |
| L5 | Adaptive | Quality for THIS user (morphic) | Partially |

Technical quality (L0-L2) is the floor. Human quality (L3-L5) is the ceiling.

The **Foundation (Stack)** is not a level measured at each deploy — it is an architecture decision that conditions the stability of ALL levels above. The compiler is the primary poka-yoke (Gate 1 of supervision); linters, tests, and reviews are secondary safety nets.

## Anti-Circular Testing Protocol (BLOCKING on critical paths)

Same AI writing code AND tests = circular validation. Three defense layers:

| Layer | Method | Tools | Mandatory? |
|-------|--------|-------|-----------|
| 1 — Algorithmic | Formal properties + fault injection | PBT (fast-check/Hypothesis), Mutation (StrykerJS), Fuzzing (Schemathesis) | Yes — always |
| 2 — Different Context | Separate session reviews + hidden tests | Writer/Reviewer sessions, Agent Test Auditor, Holdout tests (`__holdout__/`) | Yes — critical paths |
| 3 — Different Model | Another LLM reviews | Koshin/DeepSeek, future fine-tuned local model | Recommended |

Full protocol: `mnk/06-Quality.md`.

## 4-Level Risk Classification

| Level | Scope | Coverage | MC/DC? |
|-------|-------|----------|--------|
| Critical | auth, payment, crypto, encryption | 95% | Yes (conditions 4+) |
| Sensitive | user data, RGPD, config, webhooks | 90% | No |
| Standard | UI, content, analytics, admin | 80% | No |
| Tooling | scripts, dev tools, fixtures, seeds | 60% | No |

Takumi proposes classification per module. Jay decides.

## 5 Human Quality Gates (BLOCKING on public platforms)

| Gate | Key Metric | Threshold |
|------|-----------|-----------|
| Cognitive Load | Decision points per common task | <= 5 (BLOCKING) |
| Sensory Comfort | prefers-reduced-motion coverage | 100% (BLOCKING) |
| Error Resilience | Auto-save on forms > 3 fields | Required (BLOCKING) |
| Adaptation | Preference persistence between sessions | Required (BLOCKING) |
| Dignity | Données sans impact visible sur UX utilisateur + dark patterns + ton condescendant | 0 (BLOCKING) |

Full framework (HECQ, design by neurotype): `mnk/15-Human-Quality.md`. Tests de conformité détaillés : `rules/Dignity.md`.

### Test Runtime Hygiene (BLOCKING)

Test runners under jsdom (vitest, jest) leak memory across files when module isolation is weak. Unbounded runs in agentic loops (Claude Code relaunching `npx vitest` without cleanup) caused VPS OOM saturation on 2026-04-23 (3 vitest workers × 4 GiB = swap thrashing, load avg 113, 20+ containers degraded). Prevention is configuration, not discipline.

#### Vitest — mandatory config (TypeScript projects)

Every Shinkofa project using vitest MUST set the following in `vite.config.ts` (or `vitest.config.ts`):

**Vitest 4.x** (top-level pool options — `poolOptions` removed):
```ts
test: {
  pool: 'forks',
  forks: { maxForks: 2, minForks: 1 },
  isolate: true,
  maxConcurrency: 5,
  testTimeout: 10_000,
  hookTimeout: 10_000,
}
```

**Vitest 1.x–3.x** (nested poolOptions):
```ts
test: {
  pool: 'forks',
  poolOptions: { forks: { maxForks: 2, minForks: 1 } },
  isolate: true,
  maxConcurrency: 5,
  testTimeout: 10_000,
  hookTimeout: 10_000,
}
```

Rationale:
- `pool: 'forks'` — each test file runs in its own OS process. `threads` keeps modules alive across files, memory creeps.
- `maxForks: 2` — caps parallel workers. Default = CPU count; 8 forks × 2 GiB = OOM on a shared VPS.
- `isolate: true` — fresh module graph per file.
- Timeouts prevent runaway hooks from holding memory indefinitely.

#### Memory cap — mandatory in test scripts

Every `test` and `test:coverage` script in `package.json` MUST set a memory cap:

```json
"test": "cross-env NODE_OPTIONS=--max-old-space-size=2048 vitest run",
"test:coverage": "cross-env NODE_OPTIONS=--max-old-space-size=2048 vitest run --coverage"
```

`cross-env` is required for Windows compatibility (`VAR=value command` is Unix-only syntax). Install as devDependency: `npm install --save-dev cross-env`.

2048 MiB × 2 forks = 4 GiB worst case. A runner exceeding that fails loudly instead of silently swapping.

#### Agentic loop safety (BLOCKING)

When Claude Code (or any AI agent) runs tests in a session that may relaunch them:

1. Before spawning a new test run, verify no stale runner from the same project is alive: `pgrep -f "vitest.*<project>" | xargs -r kill -TERM`
2. A test command MUST NOT be spawned while a previous one from the same project is still alive.
3. On session end: kill any test runner started in the session.

Violation (stale runners piling up on shared infra) = `-10` session score on Reliability.

## Adopted Principles (QE V2 — 2026-04-23)

### Rebuild Over Fix

When foundations are unstable, rebuilding on solid ones is faster than patching. Evaluate rebuild when ANY of:
- 3+ sessions fixing the same module without lasting resolution
- Exponential technical debt detected (each fix introduces new fragility)
- Module architecture contradicts current conventions

The REAL goal: build correctly NOW so rebuild is never needed again. Proofs: Kakusei (4 days), Michi V1→V2 (<1 week, 4x more features), Hibiki (1 week audit, bugs persisted).

### Let It Crash (Erlang/BEAM Philosophy)

Bugs must be isolated, never propagated. Crashes must be rapidly identifiable. Design systems where individual errors CANNOT cascade. This is architecture, not just error handling.

### Rigueur Over Vitesse

AI development time is massively lower than real time. In 2 days of work, one can develop an application that would take weeks. This means there is NO excuse for cutting corners. Rigor always wins over speed.

### Documentation as Quality Pillar

Documentation changes the quality of code absolutely. It is our means of communication, evolution, and development. Write easily, write quickly, from anywhere, anytime.

### Beyonce Rule

"If you liked it, then you shoulda put a test on it." — Google. If you care about a behavior, put a test on it. Responsibility through testing.

### Kill Fast — REJECTED

The Supercell "kill fast" approach was explicitly REJECTED. "The mindset should NOT be 'if it doesn't please, we kill it.' If it doesn't please, find the way to MAKE it please." Adapt the HOW, never kill the WHY (L3 vision). The product's destiny is shaped by HOW it is presented, not by early metrics.

### Security as Fundamental Quality Principle

"I must not be able to read users' confidential data." Code must not allow it, even for the creator. Security protects VPS, architecture, machines, projects, and user data.

### Three Levels of Automation

| Level | Scope | Principle |
|-------|-------|-----------|
| Algorithms | Everything deterministic | MUST be algorithmic. No debate. |
| AI | Human-digital relationship | Facilitates, evolves, reflects, reduces execution time |
| Human | Instinct, vision, architecture | Irreplaceable (for now) |

### Feedback Widget = Architectural Necessity (Decision #25)

With fault isolation (Elixir/BEAM), a bug doesn't cascade — but it remains invisible if the user doesn't report it. Every public platform MUST include:
- 2 clicks max to report a bug
- Automatic context capture (page, action, timestamp, browser)
- Zero personal data collected
- Verified active in post-deploy checks

### ND Adaptation Button (Direction C)

Cumulative modifications, not binary on/off. Users check boxes and the platform adapts. Onboarding asks and adapts first — NOT a settings menu buried deep. No competitor does multi-dimensional ND adaptation → strategic advantage.

### Playtesting Principle (Direction F)

Platforms must NOT be heavy, unpleasant, corporate. The human being grows and evolves through play. Intelligent gamification without creating dependency — benevolent and constructive. Micro-rewards YES, punitive streaks NO.

### SQuBOK

Software Quality Body of Knowledge — Japanese framework for systematic software quality management. Integrates process, product, and human dimensions. Reference alongside Jidoka/Poka-yoke/Monozukuri in Quality Terminology.

## Performance (BLOCKING)

### Core Web Vitals 2026 (Shinkofa targets — stricter than Google "Good")

| Metric | Target | Google "Good" |
|--------|--------|---------------|
| LCP | < 2.0s | < 2.5s |
| INP | < 100ms | < 200ms |
| CLS | < 0.05 | < 0.1 |

### Mandatory Optimizations
- Lazy loading for images and below-fold content
- Bundle splitting (no single JS bundle > 200KB gzipped)
- HTTP/3 + 103 Early Hints when supported
- `uuidv7()` for PostgreSQL table IDs (sortable, performant)

## Accessibility (BLOCKING)

### WCAG 2.2 AA Compliance
- Zero axe-core violations (automated check pre-deploy)
- Color contrast ratio ≥ 4.5:1 (text) / ≥ 3:1 (large text)
- All interactive elements keyboard-accessible
- All images have alt text
- Focus indicators visible
- `prefers-reduced-motion` respected

### ND-Friendly UX (8 principles)

| Principle | Implementation |
|-----------|---------------|
| Predictability | Consistent layout, no surprise popups |
| Low cognitive load | One primary action per screen. Progressive disclosure. |
| Sensory control | Dark/light/high-contrast themes. Reduced motion option. |
| Clear typography | Min 16px body. 1.5 line-height. Max 75ch width. |
| Forgiving interactions | Undo, confirm destructive actions, auto-save |
| Time flexibility | No countdown timers. No session expiry without warning. |
| Minimal distractions | No auto-play. No blinking. Quiet notifications. |
| Customization | Let users choose theme, font size, notification level |

## Shinkofa Lego Library — Build Once, Reuse Forever (BLOCKING)

> Every reusable element is built in `Shinkofa-Shared/packages/` FIRST, then imported. Never code a reusable component directly in a project. Coding a duplicate of something that exists or belongs in the library = BLOCKING violation.

### @shinkofa/ui — Component Inventory (79 components)

| Category | Components |
|----------|-----------|
| **Primitives** (8) | Button, Input, Textarea, Badge, Card, Skeleton, Modal, EmptyState |
| **Shared Interactive** (6) | ThemeProvider, ThemeToggle, BackToTop, RevealOnScroll, LanguageSwitcher, CookieConsent |
| **Forms & Input** (5) | TagInput, DictationButton, CollapsibleCard, CollapsibleSection, PromptDialog |
| **Feedback** (2) | SaveIndicator, ConfirmModal |
| **Media** (1) | SafeImage |
| **BodyGraph HD** (4) | BodyGraph, BodyGraphCenter, BodyGraphChannel, BodyGraphLegend |
| **SEO** (9) | StructuredData, ArticleSchema, BreadcrumbSchema, FAQSchema, ReviewSchema, PortfolioSchema, PortfolioItemSchema, PortfolioListSchema, ServiceSchema |
| **Planner** (14) | EnergySlider, DayScore, KiGauge, KiBudgetGauges, KiCheckIn, SportTracker, MealTracker, TaskCard, SleepTracker + 5 sub-components |
| **Dashboard** (7) | KiBudgetMini, SleepSummaryCard, EnergyTrendChart, EnergyPixelMap, TodayTasksList, QuickActionGrid, ProfileChipBar |
| **Toast** (2) | ToastProvider, Toast |
| **FilePicker** (6) | FilePicker, FilePickerUploadZone, FilePickerBrowseGrid, FilePickerPreview, ImagePicker, ImageBrowserModal |
| **Navigation** (3) | NavShell, NavLink, NavGroup |
| **Settings** (3) | SettingsSection, RevealToggle, PasswordChangeForm |
| **Avatar** (2) | AvatarUpload, AvatarCropModal |
| **Questionnaire** (8) | QuestionRenderer, ProgressTracker, LoadingStepper, PhaseCard, LikertOptions, SingleChoice, MultiChoice, OpenText |
| **Gaming** (4) | DodgeMaster, SkillshotTrainer, MultiTask, ImagePairs |

All components: tested (324+ tests), themed (5 themes), accessible (WCAG 2.2 AA), framework-agnostic.

### @shinkofa/i18n — Internationalization Keys

Structured FR/EN/ES keys in `Shinkofa-Shared/packages/i18n/`. **Never hardcode UI text in a component. Always use i18n keys.**

#### i18n Integration Workflow

**Architecture**: Components accept labels via props (framework-agnostic). The consumer project connects @shinkofa/i18n to component props.

**Step 1 — Use `useTranslation` in the consumer page/container:**
```tsx
import { useTranslation } from '@shinkofa/i18n';
import { PasswordChangeForm } from '@shinkofa/ui';

function SettingsPage() {
  const { t } = useTranslation('settings');
  return (
    <PasswordChangeForm
      labels={{
        title: t('password_change.title'),
        currentPassword: t('password_change.current'),
        newPassword: t('password_change.new'),
        submit: t('password_change.submit'),
      }}
      onSubmit={handleSubmit}
    />
  );
}
```

**Step 2 — Key format** (`namespace:dotted.path`):
```
common:actions.save        → "Enregistrer"
auth:login.welcome_back    → "Bon retour, {{name}} !"
settings:password_change.title → "Changer le mot de passe"
```

20 namespaces: `common`, `auth`, `coaching`, `neurodiversity`, `gaming`, `family`, `content`, `notifications`, `ai`, `settings`, `landing`, `errors`, `admin`, `onboarding`, `legal`, `payment`, `consulting`, `marketing`, `wellness`, `community`.

**Step 3 — Adding new keys** (during feature development):
1. Add FR key in `Shinkofa-Shared/packages/i18n/src/locales/fr/{namespace}.json`
2. Add EN + ES translations in corresponding files
3. All 3 locales must have the key — incomplete = BLOCKING

**Fallback strategy**: FR (source of truth) → EN → ES. If a key is missing in EN/ES, i18next falls back to FR.

**Locale-aware formatting**:
```tsx
import { useFormattedDate, useFormattedCurrency } from '@shinkofa/i18n';
const { formatDate } = useFormattedDate();    // Intl.DateTimeFormat
const { formatCurrency } = useFormattedCurrency(); // Intl.NumberFormat (EUR default)
```

**Hook-enforced**: `write-guard.py` warns on hardcoded user-facing strings in `.tsx/.jsx` files.

### @shinkofa/types — Shared Types

Shared TypeScript types (Ki, Task, Priority, Wellness, etc.) in `Shinkofa-Shared/packages/types/`. **Never duplicate types between frontend and backend. Import from @shinkofa/types.**

### Enforcement Rules (BLOCKING)

1. **Before coding ANY UI element**: check the inventory above. If it exists → import from `@shinkofa/ui`. Violation = commit blocked.
2. **New reusable component**: code it in `Shinkofa-Shared/packages/ui/` FIRST (with tests + Storybook story), THEN import into the project. Never code a reusable component directly in a project.
3. **All user-facing text**: must use @shinkofa/i18n keys (FR/EN/ES). No hardcoded strings.
4. **All shared types**: must come from @shinkofa/types. No local type duplicates of shared models.
5. **New project bootstrap**: import ThemeProvider, ThemeToggle, BackToTop, LanguageSwitcher, CookieConsent, Skeleton from @shinkofa/ui. Create a project CSS theme file. Connect @shinkofa/i18n. This is day-one setup, not optional.

### Status

Phases 0-9 COMPLETE. Every new platform = assembly of existing bricks + business logic only.

Remaining integration work: replace coupled components in The Ermite and Michi-Shinkofa with library imports.

### Lego Library V2 Vision

Components → functional modules. Not just UI bricks but complete features (e.g., a full calendar with all options, integrable by selecting which options to include). Absolutely modular.

## Static Analysis Stack (BLOCKING)

> Full details: `docs/Static-Analysis.md`. Templates: `templates/static-analysis/`.

**Principle**: One linter is never enough. Each project type gets a standardized multi-tool stack with chiffered thresholds.

**Pre-commit** (fast, <5s): Ruff (Python), Biome (TS), ShellCheck (Bash).
**CI** (thorough): Pylint, Bandit, Vulture, Radon, mypy, Madge, Knip, Trivy, Semgrep, Gitleaks.

**Zero tolerance (BLOCKING)**: Ruff errors, Biome errors, tsc errors, Bandit HIGH, circular deps, HIGH/CRITICAL CVEs, Gitleaks findings, Semgrep HIGH/CRITICAL.

## Maintainability (BLOCKING)

**Principle: readability over size.** A file must be cohesive (one concept), decoupled (no hidden dependencies), and structured (short functions, clear naming). Size is a consequence of quality, not a goal.

- Max 30 lines per function (excluding tests)
- Cyclomatic complexity ≤ 10 per function
- File length: WARNING at 300 lines, BLOCKING at 500 lines (code source only)
- Exempt from file length: `.md`, `.json` (i18n), type schemas, configs
- A 400-line file with CC 3 is better than a 200-line file with CC 15
- No function with more than 4 parameters (use objects)

## Observability Principles (BLOCKING)

### Errors Are Data

Every exception is debugging information. Swallowing it throws away the only clue.

- `try/except/pass` (Python) or empty `catch {}` (TS) = BLOCKING on critical paths, WARNING elsewhere
- Every caught exception must be logged at the appropriate level:

| Category | Log level | Example |
|----------|-----------|---------|
| Critical path error | WARNING | DB connection failure caught by retry logic |
| Expected fallback | DEBUG | Optional feature unavailable, using default |
| User-triggered | INFO | Invalid input rejected by validation |

### The Knob Footgun

If a configuration option has only ONE correct value, it must be a constant, not a knob. Only expose settings where multiple values are legitimate.

- Before adding any user-facing setting: "Can the user break the system by changing this?"
- If yes → constant, not config
- If multiple values are valid → expose with validation that rejects invalid values

## Responsive Excellence (BLOCKING on public platforms)

Desktop is not "mobile but wider." Each breakpoint earns its space.

| Breakpoint | Principle | Implementation |
|------------|-----------|----------------|
| Mobile (375px+) | One column, one action. Touch-first. | Stack layout, 44x44px touch targets, bottom-nav |
| Tablet (768px+) | Two columns where useful. Sidebar optional. | Adaptive grid, collapsible sidebar |
| Desktop (1024px+) | Use the space intelligently. Zero dead margins. | Multi-column, data density increases, keyboard shortcuts |
| Wide (1440px+) | Content stays readable. Max-width on prose. | max-width 75ch on text, side panels for metadata |

**Rules**:
- No "desktop = mobile stretched to 1440px" — that wastes screen real estate
- Information density adapts to viewport: mobile shows summaries, desktop shows full data
- Navigation adapts: bottom-nav on mobile, sidebar on desktop
- Empty space must be intentional (breathing room), never accidental (forgot to fill)

## Morphic Adaptation (BLOCKING on public platforms)

Every public-facing Shinkofa platform adapts to the user's holistic profile. This is not personalization (cosmetic) — it is morphic adaptation (structural).

| Layer | What adapts | Based on |
|-------|------------|----------|
| Sensory | Theme, contrast, motion, font size, density | User preferences + `prefers-*` media queries |
| Cognitive | Information density, progressive disclosure, navigation depth | Neurodiversity profile (ND-friendly defaults) |
| Temporal | Session length suggestions, break reminders, energy-aware scheduling | Energy cycles (Ki model) |
| Content | Language, tone, examples, complexity level | Profile + interaction history |

**Minimum viable adaptation** (day one):
- Theme (dark/light/high-contrast) + reduced motion + font size
- ND-friendly defaults (low cognitive load, predictable layout)
- Language (FR/EN/ES with locale-aware formatting)

**Full adaptation** (progressive):
- Human Design profile integration (energy type, authority, strategy)
- Sensory preferences (visual density, notification level, sound)
- Learning style adaptation (visual/textual/interactive)

## Universal Project Checklist

Every Shinkofa project MUST have from day one:

- [ ] Dark + light + high-contrast themes
- [ ] `prefers-reduced-motion` support
- [ ] Mobile-first (375px+) with responsive excellence per breakpoint
- [ ] Trilingual FR/EN/ES (i18n from start)
- [ ] Password field reveal toggle
- [ ] Back-to-top button
- [ ] Error boundaries with user-friendly messages
- [ ] Loading states (skeleton, not spinner)
- [ ] Touch targets ≥ 44x44px on mobile
- [ ] Feedback Widget integrated in main layout (WF-035)
- [ ] Morphic adaptation: sensory defaults (theme + motion + font size)

