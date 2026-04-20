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
| Unit | Vitest (TS) / pytest (Python) | Functions, logic, edge cases | Every commit |
| Integration | Playwright / pytest | API routes, DB queries, auth flows | Every PR |
| E2E + Anti-regression | Playwright | Critical user paths | Pre-deploy |

### Coverage Floors (BLOCKING)

| Scope | Minimum | Context | Consequence |
|-------|---------|---------|-------------|
| Global | 80% | All code | Pre-deploy blocked |
| Auth, Payment, DB | 95% | Critical paths — no exceptions | Pre-deploy blocked |
| New features | 90% | Must ship with tests | Commit blocked |
| Lighthouse score | 90 | Performance audit | Pre-deploy blocked |
| axe violations (AA) | 0 | Accessibility | Pre-deploy blocked |
| Critical/High CVEs | 0 | Security | Pre-deploy blocked |

### Test Rules
- Name tests: `should_[action]_when_[condition]`
- Real database for integration tests (no mocks for DB — proven failure risk)
- Mutation testing with Stryker 9.5+ (monthly audit)
- Contract testing with Pact for cross-service APIs
- AI/LLM outputs: test structure and constraints, not exact content

### Test Reliability Metrics (5 numbers, not 1)

A single coverage % is misleading. Five metrics tell the truth:

| Metric | Target | What it catches |
|--------|--------|-----------------|
| Line coverage | ≥ 80% (95% critical paths) | Unexercised code |
| Empty tests | 0 | Tests without assertions give false confidence |
| Trivial tests | < 10% of total | `assert x is not None` doesn't verify behavior |
| Mock:Assert ratio | < 3:1 per test | If mocks > assertions by 3:1, you're testing the mock, not the code |
| Type coverage (mypy/tsc strict) | 100% new code | Catches type-level bugs statically |

**Detection rules**:
- A test with zero `assert` or `pytest.raises` = empty test = BLOCKING
- A test with only identity checks (`is not None`, `isinstance`) = trivial = WARNING
- A test with > 20 lines for a < 10-line function = probably over-mocked = WARNING

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

