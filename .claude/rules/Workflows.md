# Workflows — Behavioral Rules & Platform Standards

> Full workflow details: `mnk/05-Workflows.md`. This file = behavioral rules and platform minimums.

## Automatic Quality Protocol (BLOCKING — applies to ALL code, not just /dev)

Every time code is written or modified — whether via `/dev`, a simple request, or a bug fix — the quality protocol applies **automatically**. Jay should never have to ask for it. `/dev` adds ceremony (agent orchestration, formal Blueprint scoring); the protocol below is the **floor**, always active.

> "Chaque brique est parfaitement posée et vérifiée. C'est ce qui permet de créer un mur parfait. Chaque mur parfait crée un édifice parfait. Un édifice parfait offre une expérience qualitative et fluide à l'utilisateur, au point qu'il ne se rende pas compte du travail fourni pour en arriver là."

### The 8 Automatic Gates

| # | Gate | What | When | Enrichment (QE V2) |
|---|------|------|------|-------------------|
| 1 | **Context** | Check Blueprint/CDC if they exist. If neither exists → propose a plan before coding. If the task involves a framework/library, verify current stable version via web BEFORE coding (veille is not optional). **Observable evidence required — see "Veille/SKB Evidence Protocol" below.** | Before first line of code | + Simplified FMEA (3 failure modes) + Veille check |
| 2 | **Reformulate** | State what you understood, what you'll do, what you won't touch, files impacted. Wait for validation on non-trivial changes. | Before first line of code | + Impact analysis |
| 3 | **TDG** | Write tests FIRST — for ALL stacks in the project (TS: Vitest, Python: pytest, Elixir: ExUnit, Rust: cargo test). They must fail (red) before implementation. Identify impacted tests before writing new ones (dependency-aware targeting). | Before implementation | + Bidirectional traceability + Defensive assertions (>=2/critical fn) |
| 4 | **Code** | Implement. Atomic commits. Backup tag every 3-4 commits. | Implementation | Unchanged |
| 5 | **Lint** | Zero lint errors. Run linter after changes. | After code | Unchanged |
| 6 | **Tests** | All tests pass — unit + integration + anti-regression. Run the actual test command (`npm run test`, `pytest`, `mix test`, `cargo test`). No "it should work." | After code | + MC/DC for complex critical conditions |
| 7 | **Security** | No secrets, no injection, no weak patterns. Hooks catch most; verify the rest. | After code | + Automated PII detection |
| 8 | **Verify** | Prove it works. Evidence over assertion. On UI: run dev server and test in browser. On bug fix: demonstrate root cause (symptom → cause → correction → proof). Show the failing test that now passes. "Fixed" without proof = not verified. | Before reporting done | + Post-deploy verification + Root cause proof |

**`/dev` adds** (on top of the 8 gates): 3-Layer strategic check, SKB/veille research, non-tech PREPARE agents, i18n/visibility/SEO, non-tech VALIDATE agents, formal Blueprint/CDC/PET update, Obsidian sync.

**The 8 gates are non-negotiable and automatic. Jay never needs to invoke them.**

### Veille/SKB Evidence Protocol (BLOCKING — hook-enforced, 3 layers)

Gate 1 (Context) requires observable evidence, not silent mental verification. Before any Write/Edit on source code files (`.ts`, `.tsx`, `.js`, `.jsx`, `.py`, `.ex`, `.exs`, `.rs`, `.go`) or dependency manifests, Takumi MUST have output one of the three markers below in the current conversation turn. The hook applies three independent hardening layers (2026-05-19 — Option C) so the protocol resists conformity-of-surface skipping.

**The three markers** :

```
[VEILLE] <techno>@<version> verifie <YYYY-MM-DD> via <source>
[SKB] consulte: <chemin1>, <chemin2>, ...
[VEILLE-SKIP] motif: <enum>
```

Example VEILLE : `[VEILLE] Phoenix@1.8.2 verifie 2026-05-15 via hex.pm`
Example SKB : `[SKB] consulte: 11-Communication/Voice-Tone.md, 05-Neurodiversite/HPI.md`
Example SKIP : `[VEILLE-SKIP] motif: methodology-edit`

#### Layer A — Closed enum for SKIP motifs

`[VEILLE-SKIP] motif:` accepts ONLY one of these six values. Any other text -> BLOCK.

| Motif | When it applies |
|-------|-----------------|
| `typo` | Pure typo / whitespace fix, zero logic change |
| `internal-refactor-no-new-deps` | Move / rename inside the project, no new external dependency |
| `hotfix-known-root-cause` | Documented root cause, already-known fix path |
| `test-only` | Edit limited to `*.test.*`, `*.spec.*`, `__tests__/`, `conftest.py` |
| `methodology-edit` | Edit limited to `.claude/`, `mnk/`, `docs/`, `rules/` |
| `generated-artifact` | Build/dist/coverage output, regenerated from sources |

NOT legitimate : "I already know this", "no time", "obvious", "trivial" (with no further specification). The enum is the closed set — invent a new motif and the hook blocks.

#### Layer B — Diff-aware enforcement (no SKIP / no SKB on sensitive content)

The hook reads the target file content and the Edit diff. When the change is sensitive — defined below — ONLY `[VEILLE]` is accepted. `[SKB]` and `[VEILLE-SKIP]` are refused.

Sensitive triggers :

- Target is a dependency manifest (`package.json`, `pyproject.toml`, `mix.exs`, `Cargo.toml`, `go.mod`, `requirements*.txt`, `Gemfile`, `composer.json`, and their lockfiles).
- The diff adds a new external import (Python: non-stdlib top-level module ; TS/JS: import / require where the spec does not start with `.`, `/`, `~`, `@/`).
- The diff contains a version pin pattern (`@1.2.3`, `^1.2`, `~= 1.2`, `~> 1.2`, `>= 1.2,< 2`).

Why : these changes have external impact (a new dependency, a version pinning a known CVE, etc.). A vague SKB consultation or a SKIP motif cannot substitute for a real veille. The compiler is the primary poka-yoke ; here, the hook is the poka-yoke for stale or invented version information.

#### Layer C — Session SKIP counter

State file `.claude/state/veille-skips-<session>.json` tracks consecutive `VEILLE-SKIP` markers. At the 3rd consecutive SKIP, the hook BLOCKS regardless of motif and demands a real `[VEILLE]` or `[SKB]`. A given marker is counted once (hashed) — repeated tool calls under the same marker do not re-increment. Emitting a real `[VEILLE]` or `[SKB]` resets the counter to 0.

Why : even legitimate per-call SKIPs accumulate into a session-wide pattern that bypasses the protocol's intent. The counter forces Takumi to break the SKIP chain with verified evidence at least every 3 trivial edits.

#### Hook enforcement summary

`.claude/hooks/guards/pre-code-veille-check.py` runs at every PreToolUse Write|Edit. It :

1. Skips files that don't require evidence (tests, configs, methodology, generated artifacts, `.claude/` paths).
2. Reads the diff and detects Layer B triggers.
3. Scans the recent transcript for the latest marker.
4. Validates the marker against Layers A + B + C above.
5. Updates the counter state.

Every BLOCK message includes the exact recovery action.

#### Why the format is strict

Prose like "I checked the docs" is unverifiable and easy to fabricate. The marker format is greppable, datable, and forces Takumi to know what he actually consulted. The three layers exist because Layer A alone (presence of marker) was bypassed by inventing motifs ; Layer B closes the bypass on sensitive content ; Layer C closes the bypass via accumulation.

It is the operational floor of the Monozukuri principle "la preuve, jamais l'affirmation" (`rules/Monozukuri.md`).

#### Cost of the protocol

1-2 lines per code-writing session. The cost of NOT having it (silent skips, stale info, repeated mistakes) was documented in Jay Notes 2026-04-26 / 2026-05-13 and the 2026-05-19 hardening session.

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
- **ZERO rm -rf on work directories (BLOCKING)** — NEVER `rm -rf` on dist/, build/, output/, data/, or any directory containing work. `rm -rf` bypasses the recycle bin = IRREVERSIBLE LOSS. Always `mv x x-backup` or ask Jay BEFORE deletion. **RM-OK token (Jay 2026-06-14)** : when Jay has EXPLICITLY authorized a specific deletion in the conversation, append `# RM-OK: <reason>` to the command — the `bash-guard.py` hook then allows that ONE command (one deletion per token, reason mandatory). The token NEVER overrides a catastrophic target (root `/`, home, project root, `.git`, system dir) — those stay blocked. Every grant is logged to `.claude/state/rm-overrides.log` (auditable). The token removes the friction where the guard blocked even an authorized deletion.
- **Sync Obsidian project notes (BLOCKING)** — **4 files, not 21.** At session start: load `_Cross-Project.md` + `_Index.md` + current project file + `[project]-Notes-Jay.md`. Additional files on demand only. At session end: write only to files touched by the session (current project + Notes-Jay + `_Cross-Project.md` if cross-project decisions + `Contenu.md` if visibility candidates). If MCP unreachable: STOP and escalate.
- **Takumi-generated docs → Obsidian SKB, NEVER apps/<project>/docs/ (BLOCKING)** — tous les documents générés par Takumi (audits, rapports de session, blueprints, CDC, PET, briefs, research, mockups specs) sont écrits **directement dans Obsidian** sous `02-<Project>/` avec sous-dossiers `Sessions/`, `Audits/`, `CDC/`, `PET/`, `Briefs/`, `Research/`. Plus jamais dans `apps/<project>/docs/`. **Why** : Obsidian = brain cross-projet cross-machine RAG-able. `apps/<project>/docs/` = pollution repo code avec artefacts de travail. **Migration CDC/PET** : quand mature et stabilisé → déplacé dans dossier projet pertinent. **Décision Jay 2026-05-27** (Kōbō pilote, propagation autres projets via `/sync-repo`).
- **Notes-Jay processing (BLOCKING)** — Jay's async feedback channel. Each project has `[project]-Notes-Jay.md` in Obsidian. At session start: count unseen items (no marker). During session: update markers immediately when items are treated (`👀 Lu` = seen, `🔧 En cours` = in progress, `✅ date — résumé` = done). At session end: verify all treated items have updated markers. Full protocol: `mnk/05-Workflows-Session.md`.
- **Dedicated test/audit sessions** — for critical code, run a separate session with a Test Auditor agent for independent verification. Verification (agent) / Validation (Jay).
- **Work environment = quality criterion** — MCPs, tools, documentation, session management, proactive capabilities (veille, security audits, maintenance) are part of the quality stack, not optional extras.
- **Rigueur over Vitesse** — AI development time is massively lower than real time. There is NO excuse for cutting corners. Rigor always wins over speed.
- **Rebuild over Fix** — when a module has had 3+ sessions of corrections without lasting resolution, evaluate rebuild vs continued patching. Rebuilding on solid foundations (Lego Library + methodology) is often faster and more reliable than incremental fixes on unstable code. See `rules/Quality.md` for criteria.
- **Kill fast = REJECTED** — never kill the WHY (L3 vision). If something doesn't resonate, adapt the HOW (presentation, UX, communication). The product's destiny is shaped by how it is presented. See `rules/Strategic-Context.md`.
- **Feedback Widget = architectural necessity** — every public platform MUST include a Feedback Widget (2 clicks max, automatic context capture, zero PII). Promoted from checklist item (WF-035) to architectural requirement. With fault isolation, bugs don't cascade but remain invisible without user reporting.
- **Dignity-first (BLOCKING)** — chaque écran de collecte, chaque copy, chaque CTA, chaque message d'erreur, chaque notification, chaque flow de vente et de départ passe le test : "Est-ce qu'on respecte l'intelligence de cette personne ?" Voir `rules/Dignity.md`.

## Plan Mode par brique (BLOCKING quand un PET existe)

Quand un PET existe avec des briques non démarrées, l'implémentation passe par le mode plan natif de Claude Code. Le plan approuvé remplace la reformulation par-action : il EST la reformulation ET l'autorisation (voir `Interpretation-Protocol.md` → « Approved plan »).

**Déclencheur** : session validée par Jay ET un `docs/PET.md` (ou Obsidian `02-<Projet>/PET`) contient au moins une brique non terminée. Sur un projet sans PET (LITE_MODE, méthodologie) : ne s'applique pas, la reformulation par-action reste la règle.

**Flux** :

| Étape | Action |
|-------|--------|
| 1 | Lire le PET §6 Roadmap, identifier la/les prochaine(s) brique(s) non démarrée(s) |
| 2 | Entrer en mode plan (EnterPlanMode) |
| 3 | Présenter le plan : quelles briques, fichiers, tests, ordre. Plusieurs briques liées peuvent être planifiées ensemble. |
| 4 | Jay approuve (ExitPlanMode) — c'est le mot de validation |
| 5 | Implémenter en suivant les 8 gates. **Un commit atomique par brique**, jamais un commit géant pour N briques. |
| 6 | À la fin : mettre à jour le PET (briques cochées) + docs + Obsidian au `/session-end` |

**Garde-fou atomicité** : planifier N briques d'un coup est permis ; les implémenter dans un seul commit ne l'est pas. La granularité du plan (large) et celle du commit (atomique) sont indépendantes.

**Ce que le plan NE waive PAS** : TDG, veille, tests, lint, sécurité, anti-quick-fix s'appliquent à chaque action. Le plan autorise le périmètre, jamais la qualité. Le hook `reformulate-gate.py` reconnaît un plan approuvé comme satisfaisant la reformulation.

## Compaction & Context Reset

Claude Code v2.0.64+ (February 2026) handles automatic compaction natively and instantly. Preservation of identity rules and recent context is reliable. The methodology adds ONE valuable layer on top: a handoff brief written to disk on PreCompact, surfaced on PostCompact, so Takumi can recall files modified, decisions, and in-progress work after compression.

**What is automatic** (no action required from Takumi or Jay):
- Auto-compact triggers around 64-95% of the 200K-token context window
- Identity rules (CLAUDE.md + `.claude/rules/*`) are preserved via system re-injection on every turn
- Handoff brief written by `pre-compact-handoff.py` to `.claude/state/handoff-<session>.md`
- Path to the brief surfaced via stderr by `post-compact-recheck.py` after compaction (non-blocking WARN)

**What requires Takumi awareness**:
- If a task after compaction needs explicit recall of prior work → read the handoff brief
- If Takumi notices quality degradation (circular failures, forgotten constraints) → see Context Reset below

**What requires Jay action** (optional, recommended by Anthropic):
- `/compact` — manual compaction at a natural break point
- `/clear` — fresh session between unrelated tasks (preferred over let-it-fill)
- `/context` — visualize current context load

**Why this section is short**: prior version (pre 2026-05-19) added heuristic warnings at 60%/80%/85% based on turn/read/tool counts, a hard BLOCK after compaction until CLAUDE.md was re-Read, and two prompt-type hooks duplicating native behavior. All of that fought the native auto-compact instead of cooperating with it. Removed 2026-05-19 (Session-2026-05-19-002).

### Post-Compact Continuity Rule (BLOCKING — behavioral)

After an auto-compact, Takumi MUST NOT propose `/session-end`, write a session report, or suggest closing the session, **unless Jay has explicitly asked for it in the post-compact turn**.

**Why** : the compaction summary highlights "tasks completed" and often ends with an "Optional Next Step" pointing toward closure. Read literally, this biases Takumi toward proposing session-end even when the session is mid-flow. Jay 2026-05-31 observation : pattern occurred multiple times over recent days.

**How to apply** :

- After compaction, treat the resumption as a continuation, not a wrap-up
- The last validated action before compaction (e.g., "commit et push oui") is a step, not a final point
- If unclear what to do next, ASK Jay an open question ("Quelle est la suite ?"), do not default to closure proposal
- The only acceptable triggers for session-end after compaction are : explicit Jay request, hard context saturation (`/context` > 95%), or end-of-day signal from Jay

**Hook reinforcement** : `lifecycle/post-compact-recheck.py` injects a reminder of this rule via stderr after every PostCompact event.

**Violation cost** : `-10` session score on Process (Workflows compliance) per occurrence.

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
Claude Code auto-compact (v2.0.64+) is instant and preserves identity rules. Methodology adds: PreCompact hook writes a handoff brief, PostCompact hook surfaces its path. Full details: see "Compaction & Context Reset" section above.

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
| **Stale storage regression** | localStorage/sessionStorage schemas not broken by auth/session change | When deploy changes auth schema, JWT shape, cookie names, or session keys: explicitly check that existing browsers with OLD storage do not break the app. Test with a stale-storage browser profile OR ship a migration/cleanup script. |
| **Session lifetime regression** | Cookie Max-Age / refresh-token TTL not silently shortened | After any auth/session config change, verify cookie Max-Age and refresh token TTL match documented values. A user session that was 7 days yesterday and is 15 minutes today = BLOCKER. |
| **Inter-service connectivity (end-to-end)** | The deployed service can actually reach every downstream it depends on | When deploy changes anything that affects host resolution (docker→native, IP/port, DNS alias, namespace, env var like `*_HOST` or `*_URL`): probe the full chain end-to-end, not just `/api/health`. Example: a Phoenix service whose `/api/health` returns 200 but whose downstream MCP fleet probe (`/api/mcp/status`) returns 0/N connected = deploy is broken regardless of what `/health` says. |

**Timing**: within 5 minutes of deploy. **Failure**: rollback or hotfix immediately — do not leave a broken deploy live.

**Anti-pattern — shallow health check (BLOCKING)**: a `/api/health` endpoint that returns 200 because the HTTP server is up tells you nothing about whether the service can reach its dependencies. The smoke test MUST probe the critical path end-to-end. If the service depends on N downstreams, the smoke test MUST verify that N/N are reachable from the deployed service in its current network mode. Documenting "we tested /health" while the actual feature path is broken does not satisfy this rule.

**Origin**: Session 2026-05-08 audit revealed 2 services (Takumi Companion + Video Pipeline) running without auth on public internet for 3+ weeks undetected. Post-deploy smoke tests would have caught this on day one. Session 2026-05-08-002 added the two regression checks above after an auth deploy temporarily broke WS history via stale localStorage, then a cookie Max-Age regression broke sessions at 15 min (Takumi-Notes-Jay). Session 2026-05-30 added the "Inter-service connectivity (end-to-end)" row and the shallow-health-check anti-pattern after Kobo Migration C (docker→native) silently kept `MCP_TOWER_IP` pointing to the old Tailscale target — `/api/health` stayed green while the entire MCP fleet (23/23) was unreachable for 3 days.

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

