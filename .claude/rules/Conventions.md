# Conventions

> Full source: github.com/theermite/Shinzo · `07-Methode/Regles/Conventions.md`

**Language**: code (variables, functions, comments) = English ; docs / interactions /
content = French ; commit type/scope = English, description EN or FR ; i18n keys EN,
values FR (source) / EN / ES.

**Encoding**: UTF-8 without BOM, ALL files no exception. French accents preserved.
Hook-enforced. `.editorconfig` required (charset utf-8, lf). Git `core.autocrlf = input`.

**Naming**:

| Context | Convention | Ex |
|---------|-----------|-----|
| Markdown docs | Title-Kebab-Case.md | Session-Report.md |
| Directories | Title-Kebab-Case/ | Platform-Blueprints/ |
| `.claude/agents`, `skills` | lowercase-kebab-case | code-quality-master.md |
| `.claude/rules` | Title-Kebab-Case.md | Quality.md |
| Python | snake_case.py | auth_service.py |
| JS/TS utils | camelCase.ts | formatDate.ts |
| React | PascalCase.tsx | UserProfile.tsx |
| Bash | kebab-case.sh | run-backup.sh |
| CSS modules | kebab-case.module.css | user-card.module.css |

Exceptions: README, LICENSE, CHANGELOG, CLAUDE.md, SKILL.md, src/, docs/, tests/.

**Git**: branches `type/description-kebab-case` (feature/fix/hotfix/refactor/docs).
Conventional commits + `Co-Authored-By` mandatory. Types: feat, fix, refactor, docs,
chore, test, perf, ci, style.

**Atomic commits**: one logical change per commit. Hook-enforced.

**Stack — selection criterion (BLOCKING)**: stable, robust languages / platforms,
easy for the AI to maintain and debug. Priority = endurance over time (Monozukuri #6),
not fashion or raw performance.

**Stack defaults**:
- Backend: **Elixir/Phoenix** (default for all new backends, D24). FastAPI reserved for AI/ML pipelines.
- Desktop: **Tauri 2.x** by default ; Electron ONLY for heavy 3D.
- Frontend: Next.js 16+ / React 19 / TailwindCSS 4.
- DB: PostgreSQL 18. Cache: Redis 8. Job queue: Oban (Elixir).
- Package managers: pnpm (TS) / uv (Python) / mix (Elixir).
- Tri-Layer: TS (visible) + Elixir/Phoenix (backend) + Rust NIFs (critical modules) + Python (AI/ML).

**Schema source of truth**: Zod (front) / Ecto changesets (Elixir) / Pydantic
(Python). Never duplicate types — share via `@shinkofa/types`.

**Zero Dogma**: preferred stack, not mandatory. If a project needs something else,
justify and document.

**Detail** (full version table, extended Tri-Layer, Tauri vs Electron light/heavy 3D,
Quality Terminology, Naming Registry acronyms) → Shinzo.
