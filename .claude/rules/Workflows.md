# Workflows — Behavioral Rules & Platform Standards

> Full workflow details: `mnk/05-Workflows.md`. This file = behavioral rules and platform minimums.

## Automatic Quality Protocol (BLOCKING — applies to ALL code, not just /dev)

Every time code is written or modified — whether via `/dev`, a simple request, or a bug fix — the quality protocol applies **automatically**. Jay should never have to ask for it. `/dev` adds ceremony (agent orchestration, formal Blueprint scoring); the protocol below is the **floor**, always active.

> "Chaque brique est parfaitement posée et vérifiée. C'est ce qui permet de créer un mur parfait. Chaque mur parfait crée un édifice parfait. Un édifice parfait offre une expérience qualitative et fluide à l'utilisateur, au point qu'il ne se rende pas compte du travail fourni pour en arriver là."

### The 8 Automatic Gates

| # | Gate | What | When | Enrichment (QE V2) |
|---|------|------|------|-------------------|
| 1 | **Context** | Check Blueprint/CDC if they exist. If neither exists → propose a plan before coding. | Before first line of code | + Simplified FMEA (3 failure modes) |
| 2 | **Reformulate** | State what you understood, what you'll do, what you won't touch, files impacted. Wait for validation on non-trivial changes. | Before first line of code | + Impact analysis |
| 3 | **TDG** | Write tests FIRST — for ALL stacks in the project (TS: Vitest, Python: pytest, Elixir: ExUnit, Rust: cargo test). They must fail (red) before implementation. Identify impacted tests before writing new ones (dependency-aware targeting). | Before implementation | + Bidirectional traceability + Defensive assertions (>=2/critical fn) |
| 4 | **Code** | Implement. Atomic commits. Backup tag every 3-4 commits. | Implementation | Unchanged |
| 5 | **Lint** | Zero lint errors. Run linter after changes. | After code | Unchanged |
| 6 | **Tests** | All tests pass — unit + integration + anti-regression. Run the actual test command (`npm run test`, `pytest`, `mix test`, `cargo test`). No "it should work." | After code | + MC/DC for complex critical conditions |
| 7 | **Security** | No secrets, no injection, no weak patterns. Hooks catch most; verify the rest. | After code | + Automated PII detection |
| 8 | **Verify** | Prove it works. Evidence over assertion. On UI: run dev server and test in browser. | Before reporting done | + Post-deploy verification |

**`/dev` adds** (on top of the 8 gates): 3-Layer strategic check, SKB/veille research, non-tech PREPARE agents, i18n/visibility/SEO, non-tech VALIDATE agents, formal Blueprint/CDC/PET update, Obsidian sync.

**The 8 gates are non-negotiable and automatic. Jay never needs to invoke them.**

## Behavioral Rules

- **Reformulate before coding (BLOCKING)** — when the change is non-trivial per Interpretation-Protocol (>1 file, externally-visible, or irreversible): state (1) what you understood, (2) what you will do, (3) what you won't touch, (4) files impacted. Wait for Jay's explicit approval word. For trivial changes (single-file, internal, reversible): proceed after a one-line announcement, no wait required.
- **Deduce before asking** — check git history, logs, and code first. Ask Jay only what cannot be found.
- **Follow the trace** — recent commits → error message → most likely location. Direct path, no circling.
- **Context reset** — after 2 failed corrections on same issue → `/clear` or new conversation.
- **Writer/Reviewer** — for critical code, use two separate sessions (write + review).
- **Flag uncertainty explicitly** — say "I'm not certain" when unsure. Uncertainty acknowledged is trusted; uncertainty hidden erodes trust.
- **Scope** — state what you will/won't touch. Inform Jay if scope changes.
- **Consult SKB first** — SKB (Shinkofa Knowledge Base) is our collective brain. Search it for ALL domains (vision, coaching, tech, marketing, gaming, neurodiversity) before web research, before any decision.
- **Verify before claiming** — training data is months stale. Check SKB + web for versions, features, best practices, architecture patterns before any recommendation that influences a decision.
- **3 Layers filter** — every decision passes: L3 (Shinkofa vision respected?) → L2 (serves visibility/revenue?) → L1 (doable now?). See `rules/Strategic-Context.md`.
- **Research in 7 languages** — EN, FR, ZH, JA, KO, DE, RU for thorough coverage. Queries MUST be written in native script (汉字, 漢字/仮名, 한글, кириллица, etc.) — never in romanization, pinyin, romaji, or transliteration. Full protocol: `Eichi-Shinkofa/docs/Research-Protocol.md`.
- **Visibility-first** — everything is potentially sellable. SEO, GEO, copywriting from day one.
- **Fix pre-existing errors** — if tests fail at session start, fix them. They are your responsibility.
- **Write session reports** — mandatory after every session. Stored in `docs/Sessions/`.
- **Detect environment** — OS, machine (local/VPS), paths, shell at session start.
- **Atomic commits** — one logical change per commit. Hook-enforced.
- **Lego Library First (BLOCKING)** — before coding ANY UI element, check `@shinkofa/ui` inventory in `rules/Quality.md`. If it exists → import. If not → code in `Shinkofa-Shared/packages/ui/` first (with tests + story), then import. All text via `@shinkofa/i18n` keys (FR/EN/ES). All shared types via `@shinkofa/types`.
- **Anti-overengineering** — only make changes directly requested or clearly necessary. No extras, no abstractions for one-time ops, no hypothetical futures. Three similar lines > premature abstraction.
- **ZERO rm -rf on work directories (BLOCKING)** — NEVER `rm -rf` on dist/, build/, output/, data/, or any directory containing work. `rm -rf` bypasses the recycle bin = IRREVERSIBLE LOSS. Always `mv x x-backup` or ask Jay BEFORE deletion.
- **Sync Obsidian project notes (BLOCKING)** — **4 files, not 21.** At session start: load `_Cross-Project.md` + `_Index.md` + current project file + `[project]-Notes-Jay.md`. Additional files on demand only. At session end: write only to files touched by the session (current project + Notes-Jay + `_Cross-Project.md` if cross-project decisions + `Contenu.md` if visibility candidates). If MCP unreachable: STOP and escalate.
- **Notes-Jay processing (BLOCKING)** — Jay's async feedback channel. Each project has `[project]-Notes-Jay.md` in Obsidian. At session start: count unseen items (no marker). During session: update markers immediately when items are treated (`👀 Lu` = seen, `🔧 En cours` = in progress, `✅ date — résumé` = done). At session end: verify all treated items have updated markers. Full protocol: `mnk/05-Workflows-Session.md`.
- **Dedicated test/audit sessions** — for critical code, run a separate session with a Test Auditor agent for independent verification. Verification (agent) / Validation (Jay).
- **Work environment = quality criterion** — MCPs, tools, documentation, session management, proactive capabilities (veille, security audits, maintenance) are part of the quality stack, not optional extras.
- **Rigueur over Vitesse** — AI development time is massively lower than real time. There is NO excuse for cutting corners. Rigor always wins over speed.
- **Rebuild over Fix** — when a module has had 3+ sessions of corrections without lasting resolution, evaluate rebuild vs continued patching. Rebuilding on solid foundations (Lego Library + methodology) is often faster and more reliable than incremental fixes on unstable code. See `rules/Quality.md` for criteria.
- **Kill fast = REJECTED** — never kill the WHY (L3 vision). If something doesn't resonate, adapt the HOW (presentation, UX, communication). The product's destiny is shaped by how it is presented. See `rules/Strategic-Context.md`.
- **Feedback Widget = architectural necessity** — every public platform MUST include a Feedback Widget (2 clicks max, automatic context capture, zero PII). Promoted from checklist item (WF-035) to architectural requirement. With fault isolation, bugs don't cascade but remain invisible without user reporting.
- **Dignity-first (BLOCKING)** — chaque écran de collecte, chaque copy, chaque CTA, chaque message d'erreur, chaque notification, chaque flow de vente et de départ passe le test : "Est-ce qu'on respecte l'intelligence de cette personne ?" Voir `rules/Dignity.md`.

## Context Awareness Protocol (BLOCKING)

| Signal | Threshold | Action |
|--------|-----------|--------|
| ~40 exchanges or ~15 file reads | ~60% | Announce: "Contexte à ~60%. Priorise les tâches restantes." |
| ~60 exchanges or compaction triggered | ~80% | STOP + announce + write handoff brief (done/next/decisions) |
| Quality degradation (circular, forgotten) | Any | IMMEDIATE: "Dégradation détectée. Session fraîche nécessaire." |

## Non-Tech Agents: BEFORE and AFTER (NOT During)

```
PREPARE PHASE (non-tech agents: UX, Brand, Pedagogy, Gaming, Content)
  → Framework choice, UX patterns, copy, i18n decisions
  → Output: validated technical decisions
     ↓
BUILD PHASE (tech agents only)
  → TDG → Code → Lint → Tests → Atomic commits
     ↓
VALIDATE PHASE (non-tech + tech agents)
  → Blueprint scoring, CDC alignment, UX review
  → Verify security doesn't block features
```

## Context Engineering

### Compaction
Long conversations trigger automatic context compaction (compression). PreCompact and PostCompact hooks (in settings.json) handle state preservation and recovery. Takumi saves task state before compaction and restores it after.

### Prompt Caching
1-hour cache TTL enabled globally (`ENABLE_PROMPT_CACHING_1H`). Pauses up to 1h keep the full context warm. Beyond 1h, context is reloaded from scratch — normal and expected.

### Agent Concurrency
Max 4 sub-agents running simultaneously. More dilutes quality and risks context overflow.

**Observable protocol (required under literal reading)**: Before spawning sub-agent N, Takumi MUST state in the response: "Spawning sub-agent N of max 4 (currently running: [list agent descriptions])". If N would exceed 4, Takumi MUST announce: "Queueing — max 4 concurrent reached" and sequence the batch instead. The count is reset at each user turn.

### Context Reset
Degraded context causes circular failures. After 2 attempts to fix the same symptom that both fail, Takumi MUST announce in the response: "Context reset recommended — 2 failed attempts on [symptom X]. Suggest `/clear` or a new conversation." and stop proposing further fixes in the current conversation until the user decides.

**Observable trigger**: a "same symptom" means the same error message, the same failing test, or the same observable defect — counted within the current conversation, not across sessions.

## Debug Escalation (3 levels)

| Level | Trigger | Action |
|-------|---------|--------|
| L1 | First attempt | LOGS FIRST. Recent commits → error → most likely location. |
| L2 | L1 failed | SKB consult + web research (8 languages). |
| L3 | L2 failed | **STOP.** Generate detailed report. Return to Jay for brainstorming. |

## Post-Block Recovery Protocol (BLOCKING)

After ANY block (hook, system rule, tool refusal): **(1)** parse the full block message → **(2)** identify exact cause → **(3)** adapt → **(4)** retry once → **(5)** escalate with cause + alternative + question → **(6)** NEVER stay passive, NEVER deliver degraded silently. Violation = `-20` session score.

## PR Upstream Review Gate (BLOCKING)

> Before submitting ANY pull request to an external/upstream repo (not our own), ALL checks below must pass. Added 2026-04-03 after 3 PRs submitted to The-Vibe-Company/companion with avoidable errors.

| Check | What | Why |
|-------|------|-----|
| **Import resolution** | Every import/require in changed files must resolve against the TARGET repo, not our fork | 2 test files imported modules that only existed in our fork |
| **Mock-call parity** | For every mock in tests, count the actual calls in source — mocks must match exactly | A 3-mock setup for a 2-call function shifted all assertions |
| **Security self-review** | On security code: check OWASP basics (spoofing, bypass, injection) | Rate limiter trusted X-Forwarded-For blindly |
| **Clean fork check** | No fork-specific code (features, routes, configs) leaks into upstream PR | Multi-node code leaked into upstream tests |
| **CI dry-run** | Run the target repo's test suite locally before pushing | Would have caught all 3 issues |

Violation of this gate is BLOCKING.

## Platform Minimums

| Platform | Non-negotiable |
|----------|---------------|
| Web | Mobile-first 375px+, WCAG 2.2 AA, dark/light/high-contrast, reduced-motion, Core Web Vitals, FR/EN/ES, ND-friendly, **cross-browser (Chrome/Firefox/Safari/Edge)** |
| Desktop | Dark/light themes, keyboard shortcuts, responsive resize, non-blocking UI |
| Mobile | Touch 44x44px, offline-first, <200KB initial, TTI <3s on 3G |
| CLI | `--help`, exit codes, JSON output, `--no-color` |
| Content | Factual, Jay's voice, GDPR-compliant, no raw AI published |

## Pre-RAG Audit (BLOCKING)

Any (re)indexation of a knowledge base toward a RAG must be preceded by `/pre-rag-audit`. CRITICAL findings must be resolved. WARNINGS must be documented. Violation = RAG poisoning = `-10` session score. Run at minimum every 30 days on SKB.

## Code Registry

Run `/update-registry` after adding, removing, or renaming classes/functions. The skill generates `docs/registry/` (created on first run). CI can verify: `git diff --exit-code docs/registry/`. For `@shinkofa/*` packages, registry progressively replaces the manual inventory in `rules/Quality.md`.

## Marketing Automation Gate (BLOCKING on public platforms)

Every public-facing feature ships with its visibility pipeline. Building the tool without building the distribution is building in a vacuum.

| Gate | What | When |
|------|------|------|
| SEO/GEO | Meta tags, structured data, Open Graph, AI-optimized content | Before deploy |
| Auto-publish | Content pipeline connected (blog → LinkedIn/Discord/Telegram minimum) | Before launch |
| Analytics | Privacy-first tracking active (no PII, cookie-consented) | Before launch |
| Capture | Email capture or CTA present on public pages | Before launch |

This is not about marketing as a task — it is about marketing as infrastructure. Build the pipes now, so content flows forever. A platform without distribution is invisible, and invisible contradicts L2 (visibility).

## Post-Deploy Smoke Test (BLOCKING on live apps)

Every deployment MUST include a smoke test that verifies:

| Check | What | How |
|-------|------|-----|
| **Auth integrity** | Authentication is not broken or bypassed | Hit a protected endpoint without token → expect 401/403. Hit with valid token → expect 200. |
| **API connections** | All external API integrations respond | Health-check each connected service (DB, Redis, external APIs). Log response status. |
| **Critical paths** | Core user flows still work | Automated or manual check of login, main feature, payment (if applicable). |
| **Reverse proxy** | nginx/Caddy routes correctly | Verify public URL returns expected response, not 502/504. |

**Timing**: within 5 minutes of deploy. **Failure**: rollback or hotfix immediately — do not leave a broken deploy live.

**Origin**: Session 2026-05-08 audit revealed 2 services (Takumi Companion + Video Pipeline) running without auth on public internet for 3+ weeks undetected. Post-deploy smoke tests would have caught this on day one.

## Nginx Maintenance Pages (BLOCKING on exposed services)

Every service exposed via nginx reverse proxy MUST have custom error pages for downtime scenarios:

| Error | Page | Content |
|-------|------|---------|
| 502 Bad Gateway | `/var/www/maintenance/502.html` | "Service en maintenance. Retour imminent." |
| 503 Service Unavailable | `/var/www/maintenance/503.html` | "Service temporairement indisponible." |
| 504 Gateway Timeout | `/var/www/maintenance/504.html` | "Le service met trop de temps a repondre." |

**nginx config** (per vhost):
```nginx
error_page 502 /502.html;
error_page 503 /503.html;
error_page 504 /504.html;
location = /502.html { root /var/www/maintenance; internal; }
location = /503.html { root /var/www/maintenance; internal; }
location = /504.html { root /var/www/maintenance; internal; }
```

**Rules**:
- Pages are static HTML (no JS dependency, no external CSS CDN)
- Branded with Shinkofa identity (logo, colors) — Dignity-compliant (no "Oops!", no guilt-trip)
- Include estimated return time if known, or "retour imminent" if not
- Mobile-responsive (the user might be on their phone)
- Deployed to VPS ONCE, shared by all vhosts

## Cross-Browser Compatibility (BLOCKING on public platforms)

Every public-facing platform MUST work on all major browsers. "Works on Chrome" is not shipped.

### Target Browsers

| Browser | Minimum Version | Engine |
|---------|----------------|--------|
| Chrome / Edge | Last 2 major versions | Blink |
| Firefox | Last 2 major versions | Gecko |
| Safari (macOS + iOS) | 15.4+ | WebKit |
| Samsung Internet | Last 2 major versions | Blink |

### Mandatory Practices

- **`.browserslistrc`** in every web project root: `defaults, iOS >= 15.4, Safari >= 15.4`
- **No API without fallback**: `crypto.randomUUID()`, `AbortSignal.timeout()`, `Array.at()`, `structuredClone()` — all require feature detection + polyfill/fallback
- **CSS with fallbacks**: `color-mix()`, `oklch()`, `backdrop-filter` — always provide RGB/hex fallback BEFORE the modern declaration (CSS cascade)
- **Vendor prefixes**: `-webkit-backdrop-filter` for Safari. Use autoprefixer in build pipeline.
- **Image formats**: WebP/AVIF with JPEG/PNG fallback (`<picture>` element or canvas feature detection)
- **Testing**: test on Safari (real device or BrowserStack) before any public deploy. Chrome DevTools mobile emulation does NOT catch WebKit issues.

### Pre-Deploy Cross-Browser Checklist

| Check | Tool | Blocking? |
|-------|------|-----------|
| `.browserslistrc` present | File check | Yes |
| No unsupported APIs without fallback | ESLint `compat` plugin or manual review | Yes |
| CSS fallbacks before modern properties | Stylelint or manual review | Yes |
| autoprefixer in build pipeline | Build config check | Yes |
| Safari manual test on critical paths | Real device / BrowserStack | Yes (public platforms) |

**Origin**: Session 2026-05-06 — Kakusei and Shizen both broken on Safari mobile. 11 files fixed across 2 projects. `color-mix()`, `crypto.randomUUID()`, `AbortSignal.timeout()` had zero fallbacks.

## Fix = Deploy

On live apps: a fix is NOT done until it's deployed AND verified. Non-negotiable.

## Scoring V2

Session score measures three dimensions, not just process compliance.

### Dimensions

| Dimension | Weight | What it measures |
|-----------|--------|-----------------|
| **Value** | 40% | Did the session produce something deployable, publishable, or usable by Jay? |
| **Reliability** | 30% | How clean was execution? Rework count, regressions introduced, corrections needed. |
| **Process** | 30% | Were the methodology gates respected? (Obsidian, TDG, atomic commits, reformulation) |

### Value Scale (0-100)

| Score | Criteria |
|-------|----------|
| 90-100 | Shipped feature, deployed fix, published content, or completed significant design |
| 70-89 | Meaningful progress toward a deliverable (e.g., half a feature, research complete) |
| 50-69 | Foundational work (setup, scaffolding, propagation, methodology improvement) |
| 30-49 | Partial progress with blockers or scope reduction |
| 0-29 | Session produced no tangible output (stuck, circular, or meta-only) |

### Reliability Scale (0-100)

| Score | Criteria |
|-------|----------|
| 90-100 | Zero rework, zero regressions, all changes correct on first pass |
| 70-89 | Minor corrections needed (1-2), no regressions |
| 50-69 | Multiple corrections (3+) or 1 regression fixed in-session |
| 30-49 | Significant rework or regression that escaped the session |
| 0-29 | Circular failures, repeated same mistake, or data loss |

### Process Scale (0-100)

| Score | Criteria |
|-------|----------|
| 100 | All gates passed, zero violations, zero warnings |
| Per violation | -10 (Obsidian skip, TDG skip, no reformulation on >2 files) |
| Per warning | -2 (minor process deviation) |

### Final Score

`Score = (Value × 0.4) + (Reliability × 0.3) + (Process × 0.3)`

A session with perfect process (100) but no value (30) scores: 30×0.4 + 100×0.3 + 100×0.3 = **72**. Process alone is not enough.

A session with high value (95) and minor process issues (80) scores: 95×0.4 + 90×0.3 + 80×0.3 = **89**. Value matters most.

### In Session Reports

Report all three dimensions separately, then the weighted total:

```
| Dimension | Score | Notes |
|-----------|-------|-------|
| Value | 85 | Feature X deployed and verified |
| Reliability | 95 | 1 minor correction, zero regressions |
| Process | 100 | All gates passed |
| **Total** | **92** | |
```

