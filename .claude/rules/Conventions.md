# Conventions

> Source complète : github.com/theermite/Shinzo · `07-Methode/Regles/Conventions.md`

**Langue** : code (variables, fonctions, commentaires) = anglais ; docs /
interactions / contenu = français ; commit type/scope = anglais, description EN ou
FR ; i18n keys EN, values FR (source) / EN / ES.

**Encoding** : UTF-8 sans BOM, TOUS les fichiers sans exception. Accents FR
préservés. Hook-enforced. `.editorconfig` requis (charset utf-8, lf). Git
`core.autocrlf = input`.

**Naming** :

| Contexte | Convention | Ex |
|----------|-----------|-----|
| Docs Markdown | Title-Kebab-Case.md | Session-Report.md |
| Dossiers | Title-Kebab-Case/ | Platform-Blueprints/ |
| `.claude/agents`, `skills` | lowercase-kebab-case | code-quality-master.md |
| `.claude/rules` | Title-Kebab-Case.md | Quality.md |
| Python | snake_case.py | auth_service.py |
| JS/TS utils | camelCase.ts | formatDate.ts |
| React | PascalCase.tsx | UserProfile.tsx |
| Bash | kebab-case.sh | run-backup.sh |
| CSS modules | kebab-case.module.css | user-card.module.css |

Exceptions : README, LICENSE, CHANGELOG, CLAUDE.md, SKILL.md, src/, docs/, tests/.

**Git** : branches `type/description-kebab-case` (feature/fix/hotfix/refactor/docs).
Commits Conventional + `Co-Authored-By` obligatoire. Types : feat, fix, refactor,
docs, chore, test, perf, ci, style.

**Atomic commits** : un changement logique par commit. Hook-enforced.

**Stack — critère de sélection (BLOCKING)** : langages / plateformes stables,
robustes, faciles à maintenir et débuguer par l'IA. Priorité = tenue dans le temps
(Monozukuri #6), pas la mode ni la perf brute.

**Défauts stack** :
- Backend : **Elixir/Phoenix** (défaut tout nouveau backend, D24). FastAPI réservé aux pipelines IA/ML.
- Desktop : **Tauri 2.x** par défaut ; Electron UNIQUEMENT si 3D lourde.
- Frontend : Next.js 16+ / React 19 / TailwindCSS 4.
- DB : PostgreSQL 18. Cache : Redis 8. Job queue : Oban (Elixir).
- Package managers : pnpm (TS) / uv (Python) / mix (Elixir).
- Tri-Layer : TS (visible) + Elixir/Phoenix (backend) + Rust NIFs (modules critiques) + Python (IA/ML).

**Schema source of truth** : Zod (front) / Ecto changesets (Elixir) / Pydantic
(Python). Jamais dupliquer les types — partager via `@shinkofa/types`.

**Zero Dogma** : stack préférée, pas imposée. Si un projet a besoin d'autre chose,
justifier et documenter.

**Détail** (table versions complète, Tri-Layer étendu, Tauri vs Electron 3D
légère/lourde, Quality Terminology, Naming Registry acronymes) → Shinzo.
