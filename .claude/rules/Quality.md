# Quality — BLOCKING Gates

> Source complète : github.com/theermite/Shinzo · `07-Methode/Regles/Quality.md`
> Toute règle de ce fichier est BLOCKING. Zéro dérogation.

## TDG — Test-Driven Generation

Tests AVANT le code, toujours. (1) écrire le test → (2) le lancer (rouge) →
(3) coder (vert) → (4) refactor sur code smell → (5) tests toujours verts.

## Test Strategy

3 niveaux : Unit (Vitest / pytest / ExUnit / cargo test, chaque commit) ·
Integration (Playwright / pytest / ExUnit+Ecto.Sandbox, chaque PR) · E2E +
anti-régression (Playwright, pre-deploy).
Commandes : TS `pnpm run test` · Python `pytest` · Elixir `mix test` · Rust
`cargo test`. « Tous les tests passent » = lancer la vraie commande, exit code 0.
DB réelle pour l'integration (pas de mock DB). Tests nommés `should_[action]_when_[condition]`.

**Coverage Floors (BLOCKING)** :

| Scope | Min |
|-------|-----|
| Global | 80% |
| Critical paths | 95% |
| Nouvelles features | 90% (commit bloqué sinon) |
| Lighthouse | 90 |
| axe violations (AA) | 0 |
| Critical/High CVEs | 0 |

**Critical paths** = auth, authentication, authorization, sessions, oauth, jwt,
passwords, 2fa/mfa · payment, billing, subscription, stripe, invoices, refunds,
checkout · DB migrations · security, crypto, encryption · rgpd/gdpr, data
export/delete · webhooks paiement/auth. + fonctions taguées `@critical` +
`docs/critical-paths.md`. NON critiques (floor 80%) : UI, contenu, analytics, dev
tools, scripts, fixtures. Ambigu → le plus restrictif (95%).

**5 métriques de fiabilité (pas 1)** : line coverage ≥80% (95% critical) · tests
vides = 0 (BLOCKING) · tests triviaux <10% · ratio mock:assert <3:1 · type coverage
100% code neuf (tsc/mypy strict). Test sans assert = vide = BLOCKING.

## Anti-Circular Testing (BLOCKING sur critical paths)

Même IA qui écrit code ET tests = validation circulaire. 3 couches : (1)
Algorithmique — PBT + mutation + fuzzing (toujours). (2) Contexte différent —
sessions Writer/Reviewer séparées, holdout tests (critical paths). (3) Modèle
différent — autre LLM relit (recommandé).

## 4-Level Risk Classification

Critical (auth/payment/crypto) 95% + MC/DC · Sensitive (user data/RGPD/config/
webhooks) 90% · Standard (UI/contenu/analytics) 80% · Tooling (scripts/fixtures)
60%. Takumi propose, Jay décide.

## 5 Human Quality Gates (BLOCKING plateformes publiques)

Cognitive Load (≤5 points de décision / tâche) · Sensory Comfort
(prefers-reduced-motion 100%) · Error Resilience (auto-save formulaires >3 champs) ·
Adaptation (préférences persistées entre sessions) · Dignity (0 donnée sans impact
UX + 0 dark pattern + 0 ton condescendant). Détail → Dignity.md.

## Performance (BLOCKING) — Core Web Vitals 2026 Shinkofa

LCP <2.0s · INP <100ms · CLS <0.05. Lazy loading · bundle splitting (aucun JS
>200KB gzip) · HTTP/3 + Early Hints · `uuidv7()` pour les IDs PostgreSQL.

## Accessibility (BLOCKING) — WCAG 2.2 AA

0 violation axe-core · contraste ≥4.5:1 (texte) · tout interactif au clavier · alt
sur images · focus visible · prefers-reduced-motion respecté. ND-friendly :
prévisibilité, charge cognitive basse (1 action/écran), contrôle sensoriel, typo
claire (≥16px, 1.5 line-height, ≤75ch), interactions pardonnantes (undo, auto-save),
zéro timer, distractions minimales, personnalisation.

## Maintainability (BLOCKING)

Lisibilité > taille. Fonction ≤30 lignes (hors tests) · complexité cyclomatique ≤10
· fichier WARNING 300 / BLOCKING 500 lignes (code source ; exempts : .md, .json
i18n, schemas, configs) · ≤4 paramètres par fonction (sinon objet).

## Observability (BLOCKING)

**Errors are data** : `try/except/pass` (Python) ou `catch{}` vide (TS) = BLOCKING
sur critical paths, WARNING ailleurs. Toute exception capturée est loggée au bon
niveau (WARNING critical path, DEBUG fallback attendu, INFO user-triggered).
**The Knob Footgun** : une option qui n'a qu'une seule bonne valeur = constante, pas
un réglage. Exposer un réglage seulement si plusieurs valeurs sont légitimes.

## Static Analysis (BLOCKING)

Un linter ne suffit jamais. Pre-commit (<5s) : Ruff (Python), Biome (TS), ShellCheck
(Bash). CI : Pylint, Bandit, Vulture, Radon, mypy, Madge, Knip, Trivy, Semgrep,
Gitleaks. Zéro tolérance : erreurs Ruff/Biome/tsc, Bandit HIGH, deps circulaires,
CVE HIGH/CRITICAL, findings Gitleaks, Semgrep HIGH/CRITICAL.

## Test Runtime Hygiene (BLOCKING)

Vitest : `pool: 'forks'`, `maxForks: 2`, `isolate: true`, `maxConcurrency: 5`,
timeouts 10s (sinon OOM VPS). Scripts package.json : `NODE_OPTIONS=--max-old-space-size=2048`
via cross-env (Windows). Boucle agentique : tuer tout runner stale du même projet
avant d'en relancer un ; tuer en fin de session. Config exacte → Shinzo.

## Lego Library — Build Once, Reuse Forever (BLOCKING)

Avant de coder TOUT élément UI : vérifier l'inventaire `@shinkofa/ui` (79
composants). S'il existe → importer. Sinon → coder dans `Shinkofa-Shared/packages/ui/`
d'abord (tests + story), puis importer. Tout texte via `@shinkofa/i18n` (FR/EN/ES,
FR source). Tous types partagés via `@shinkofa/types`. Coder un doublon = BLOCKING.
Inventaire complet + workflow i18n → Shinzo.
**A10 — alimentation continue** : dès qu'un réutilisable est créé/repéré, l'extraire
via `/extract-lego` AVANT de le réutiliser.

## Morphic Adaptation (BLOCKING plateformes publiques)

Adaptation structurelle au profil holistique (pas cosmétique). Couches : sensoriel
(thème/contraste/motion/font/densité), cognitif (densité info, disclosure
progressif), temporel (Ki), contenu (langue/ton).
**Design for the Reference Profile First** : défaut = riche/spatial/dense (cerveau
HPI/multipotentiel/hypersensible de Jay). Le moteur morphique RÉDUIT/calme pour les
profils qui en ont besoin — il n'impose pas l'état réduit à tous. « Riche par
défaut, le morphique gère le reste. » Garde-fous : ergonomique jamais décoratif ;
spatialité ≠ animation (motion opt-in, prefers-reduced-motion) ; densité oui, chaos non.

## Principes adoptés (QE V2)

Rebuild over Fix (3+ sessions sur le même module → évaluer rebuild) · Let It Crash
(isoler les fautes, jamais propager) · Rigueur over Vitesse · Documentation = pilier
· Beyonce Rule (si tu tiens à un comportement, mets un test) · Kill Fast = REJETÉ ·
Sécurité = principe qualité fondamental · Feedback Widget = nécessité architecturale
(2 clics, contexte auto, 0 PII).

## Jidoka sans hooks — Portability Bridge (BLOCKING — A9)

Quand les hooks sont indisponibles (autre harnais), appliquer Jidoka (stop on
defect) et Poka-yoke par compréhension : émettre soi-même les marqueurs falsifiables
([VEILLE], [ROBUSTNESS]) et dérouler les checklists. L'IA EST son propre Jidoka. Un
vérificateur externe (humain, 2e modèle) reste le seul garant dur.

## Universal Project Checklist

Tout projet dès le jour 1 : thèmes dark/light/high-contrast · prefers-reduced-motion
· mobile-first 375px+ responsive · trilingue FR/EN/ES · reveal password · back-to-top
· error boundaries · loading skeletons · touch ≥44×44px · Feedback Widget · GlitchTip
câblé · morphic (thème+motion+font) · onboarding adaptatif (choix sensoriel AVANT identité).

**Détail** (Quality Pyramid V2, inventaire 79 composants, configs vitest verbatim,
critical paths liste exhaustive, exemples i18n, Responsive par breakpoint, Three
Levels of Automation, SQuBOK, sources) → Shinzo.
