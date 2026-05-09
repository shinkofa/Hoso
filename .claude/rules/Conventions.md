# Conventions

## Language

| Context | Language | Examples |
|---------|----------|----------|
| Code (variables, functions, classes, comments) | English | `getUserProfile()`, `is_valid`, `AuthService` |
| Documentation, interactions, content | French | Session reports, Obsidian notes, commit bodies |
| Commit type/scope | English | `feat(auth):`, `fix(hooks):` |
| Commit description | English or French | Both accepted |
| i18n keys | English | `settings.password_change.title` |
| i18n values | FR/EN/ES | Trilingual, FR = source of truth |

AI-generated code quality is highest in English. Documentation and interactions stay in French — no impact on code quality, better for Jay's native thinking.

## Encoding

UTF-8 without BOM. Always. French accents preserved in content. Hook-enforced. `.editorconfig` required in every project (`charset = utf-8`, `end_of_line = lf`). Git: `core.autocrlf = input` on all machines.

## Naming

| Context | Convention | Example |
|---------|-----------|---------|
| Markdown docs | Title-Kebab-Case.md | `Session-Report.md` |
| Directories | Title-Kebab-Case/ | `Platform-Blueprints/` |
| `.claude/agents/` | lowercase-kebab-case.md | `code-quality-master.md` |
| `.claude/skills/` | lowercase-kebab-case/ | `session-start/SKILL.md` |
| `.claude/rules/` | Title-Kebab-Case.md | `Quality.md` |
| Python | snake_case.py | `auth_service.py` |
| JS/TS utilities | camelCase.ts | `formatDate.ts` |
| React components | PascalCase.tsx | `UserProfile.tsx` |
| Bash scripts | kebab-case.sh | `run-backup.sh` |
| CSS/SCSS modules | kebab-case.module.css | `user-card.module.css` |

Exceptions: README.md, LICENSE, CHANGELOG.md, CLAUDE.md, SKILL.md, src/, docs/, tests/

## Git Branches

`type/description-kebab-case` — Types: feature/, fix/, hotfix/, refactor/, docs/

## Git Commits

Conventional Commits. Always include Co-Authored-By.

```
type(scope): concise description

Body if needed.

Co-Authored-By: Takumi "IA Dev Partner"
```

Types: feat, fix, refactor, docs, chore, test, perf, ci, style

## Atomic Commits

One logical change per commit. Hook-enforced. If you changed auth AND UI in the same session, that's 2 commits minimum.

## Technology Stack (2026)

| Layer | Technology | Version |
|-------|-----------|---------|
| Frontend | Next.js | 16+ |
| UI | React | 19 |
| Styling | TailwindCSS | 4.x |
| Backend API (principal) | Elixir/Phoenix | 1.19+ / 1.8+ |
| Backend AI/ML pipelines | FastAPI | 0.136+ |
| Backend (legacy, migration) | FastAPI | 0.136+ |
| Validation (Elixir) | Ecto changesets | 3.13+ |
| Validation (backend Python) | Pydantic | 2.12+ |
| Validation (frontend) | Zod | 3.x |
| ORM | Ecto (Elixir) / SQLAlchemy (Python) / Prisma (TS) | 3.13+ / 2.x / 7.x |
| Job queue | Oban (Elixir) | 2.22+ |
| HTTP server | Bandit (Elixir) | 1.11+ |
| Database | PostgreSQL | 18 |
| Cache | Redis | 8.x |
| Desktop | PySide6 (NEVER tkinter) | 6.9+ |
| Desktop (JS) | Electron | 40+ |
| Linting | Biome 2.4+ (TS) / Ruff 0.15+ (Python) | |
| Testing | Vitest 4.0+ (TS) / pytest (Python) / ExUnit (Elixir) / cargo test (Rust) / Playwright 1.58+ (E2E) | |
| Coverage | c8/vitest (TS) / pytest-cov (Python) / ExCoveralls (Elixir) / cargo-tarpaulin (Rust) | |
| Mutation testing | Stryker 9.5+ (TS) / mutmut (Python) / Mutant.ex (Elixir) / cargo-mutants (Rust) | |
| Linting (Elixir) | Credo 1.7+ (strict) + mix format | |
| Security (Elixir) | Sobelow 0.14+ | |
| Package managers | pnpm (TS) / uv (Python) / mix (Elixir) | |
| AI local | Ollama + qwen3:8b-nothink | |
| AI cloud | Claude Opus 4.7 / Sonnet 4.6 / Haiku 4.5 / DeepSeek-V3 | |
| Critical modules | Rust via NIFs in Elixir (Rustler) | 1.87+ |

**Zero Dogma**: This stack is preferred, not mandatory. If a project needs something else, justify and document.

### Tri-Layer Architecture (Decision #24)

Elixir AND Rust are complementary, not alternatives:

| Layer | Role | Technology |
|-------|------|-----------|
| Visible | UI, UX, ND adaptation | TypeScript/React + @shinkofa/ui |
| Backend API | Fault isolation, real-time, orchestration | Elixir/Phoenix (progressive migration via Strangler Fig) |
| Critical modules | Auth, crypto, validation | Rust via NIFs in Elixir (Discord uses this in prod) |
| AI/ML | Training pipelines, embeddings | Python (ecosystem irreplaceable for training) |

## Schema Source of Truth

- Frontend: Zod schemas → derive TypeScript types
- Backend Elixir: Ecto schemas + changesets → validation at the boundary
- Backend Python: Pydantic models → derive API schemas
- Never duplicate types manually between frontend and backend. Generate or share via @shinkofa/types.

## Quality Terminology

| Term | Definition | GoRin Equivalent |
|------|-----------|-----------------|
| Jidoka (自働化) | Autonomation — stop on defect | Hooks (Ring 0) |
| Poka-yoke (ポカヨケ) | Error-proofing by design | Validation + compiler |
| Monozukuri (ものづくり) | Art of making — quality as identity | GoRin philosophy |
| SQuBOK | Software Quality Body of Knowledge — Japanese holistic quality framework | Quality reference framework |
| Devil's Advocate Protocol (formerly Ipcha Mistabra, איפכא מסתברא) | "What if the opposite is true?" — challenge every assertion | Attacker stories |
| Dignity (尊厳) | Respecter l'intelligence de celui qui ne sait pas encore — l'utilisateur n'est jamais le produit | Foundational posture (BLOCKING) |

## Naming Registry

| Acronym | Full Name | Function |
|---------|-----------|----------|
| **SKS** | Shinkofa Shared Knowledge Store | Shared memory cross-projects cross-machines |
| **SKB** | Shinkofa Knowledge Base | Domain knowledge base (formerly Eichi KB) |
| **Nagare (technical)** | Associative Idea Engine | Capture, connection, growth, idea generation |
| **Nagare (user-facing)** | Nagare — The Idea Engine | Product name |

**Naming principle**: Internal technical systems use transparent English names for LLMs (+ Shinkofa prefix + acronym). User-facing products can have custom names. Philosophical concepts keep their original names.

## Cross-Platform

- Commands: bash (Linux + Git Bash Windows)
- Hooks: Python only
- Paths: forward slashes in docs
