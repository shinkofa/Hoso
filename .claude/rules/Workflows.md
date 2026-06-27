# Workflows — Behavioral Rules & Platform Standards

> Source complète : github.com/theermite/Shinzo · `07-Methode/Regles/Workflows.md`

## Automatic Quality Protocol (BLOCKING — tout code, pas juste /dev)

Le protocole qualité s'applique automatiquement à chaque écriture de code. Jay n'a
jamais à le demander.

**Les 8 gates** :

| # | Gate | Quand |
|---|------|-------|
| 1 | Context | Blueprint/CDC si existants ; sinon proposer un plan. Veille version AVANT de coder (marqueur obligatoire). |
| 2 | Reformulate | Compris / fais / ne touche pas / fichiers. Attendre validation sur non-trivial. |
| 3 | TDG | Tests d'abord (tous stacks), rouge avant le code. |
| 4 | Code | Implémenter. Commits atomiques. Tag backup tous les 3-4 commits. |
| 5 | Lint | Zéro erreur. |
| 6 | Tests | Tous verts (vraie commande). Pas de « ça devrait marcher ». |
| 7 | Security | Zéro secret, injection, pattern faible. |
| 8 | Verify | Prouver que ça marche (test qui passe, navigateur sur UI, smoke test sur deploy). |

## Veille/SKB Evidence Protocol (BLOCKING — hook-enforced)

Avant tout Write/Edit sur du code source (`.ts/.tsx/.js/.py/.ex/.exs/.rs/.go`) ou un
manifeste de deps, avoir émis un marqueur :

```
[VEILLE] <techno>@<version> verifie <YYYY-MM-DD> via <source>
[SKB] consulte: <chemin1>, <chemin2>
[VEILLE-SKIP] motif: <enum>
```

Enum SKIP (fermé) : `typo` · `internal-refactor-no-new-deps` · `hotfix-known-root-cause`
· `test-only` · `methodology-edit` · `generated-artifact`. Tout autre motif → BLOCK.
3 couches : A enum fermé · B diff sensible (manifeste, nouvel import, version pin) →
SEUL [VEILLE] accepté · C compteur 3 SKIP consécutifs → BLOCK. **Le dataset du
modèle est présumé périmé par défaut** : toute version/API/CVE/best-practice est
vérifiée à la date du jour, jamais depuis la connaissance interne.

## Behavioral Rules

- **Reformulate before coding (BLOCKING)** sur non-trivial (>1 fichier, visible
  externe, irréversible). Trivial : pré-annonce 1 ligne, pas d'attente.
- **Deduce before asking** : git, logs, code d'abord. Demander à Jay seulement l'introuvable.
- **LOGS FIRST** sur tout bug. Puis commits récents → erreur → localisation.
- **Verify before claiming** : dataset périmé, vérifier SKB + web. Preuve, jamais affirmation.
- **Consult SKB first** (tous domaines, avant le web).
- **3 Layers filter** : L3 vision → L2 visibilité → L1 action.
- **Anti-overengineering** : seulement le demandé ou clairement nécessaire. Nuance :
  cible = minimum VIABLE dans le temps long (contexte+stabilité+sécurité), pas le
  strict minimum. La solidité anticipée sur un axe stabilité/sécurité n'est PAS de la suringénierie.
- **ZERO rm -rf sur du travail (BLOCKING)** : toujours `mv x x-backup` ou demander
  Jay. Jeton `# RM-OK: <raison>` débloque UN rm autorisé par Jay (jamais sur cible
  catastrophique : racine, home, projet, .git ; journalisé).
- **Lego Library First (BLOCKING)** : vérifier `@shinkofa/ui` avant tout UI ; texte
  via i18n ; types via `@shinkofa/types`.
- **Sync Obsidian (BLOCKING)** : 4 fichiers (_Cross-Project + _Index + projet
  courant + [projet]-Notes-Jay). Plus à la demande. MCP injoignable → STOP et escalader.
- **Notes-Jay processing (BLOCKING)** : compter les items non vus au début ; mettre
  à jour les marqueurs (👀 Lu / 🔧 En cours / ✅ date — résumé) quand traités.
- **Docs : CDC/PET → repo du projet (`docs/`) ; savoir/vision/décisions/archi/infra/
  écosystème/audits → Shinzo ; rapports de session → `docs/Sessions/`** (décision Jay 2026-06-27).
- **Atomic commits** · **Fix pre-existing errors** (tests rouges au démarrage = à
  corriger) · **Write session reports** (après chaque session).

## Plan Mode par brique (BLOCKING quand un PET existe)

Si un PET a des briques non démarrées, l'implémentation passe par le mode plan
natif. Le plan approuvé = la reformulation + l'autorisation. Un commit atomique par
brique (jamais un commit géant pour N briques). Le plan autorise le scope, jamais la
qualité (TDG/veille/tests/lint/sécurité s'appliquent).

## Context Engineering

Max 4 sous-agents simultanés (annoncer « sub-agent N of max 4 » ; au-delà, queue).
Context reset : après 2 corrections échouées sur le même symptôme → annoncer
« Context reset recommandé » et stopper les fixes jusqu'à décision de Jay.

## Post-Compact Continuity (BLOCKING — behavioral)

Après auto-compact, NE PAS proposer /session-end ni écrire un rapport sauf demande
explicite de Jay. Traiter la reprise comme une continuation, pas un wrap-up. Si
flou → demander « Quelle est la suite ? ».

## Debug Escalation (3 niveaux)

L1 : LOGS FIRST — sur app branchée GlitchTip, consulter GlitchTip EN PREMIER
(erreurs prod centralisées), puis logs locaux → commits → erreur. L2 : SKB + web
(8 langues). L3 : STOP, rapport détaillé, retour à Jay.

## Post-Block Recovery (BLOCKING)

Après tout blocage (hook, règle, refus outil) : (1) lire le message complet → (2)
cause exacte → (3) adapter → (4) retry une fois → (5) sinon escalader (cause +
alternative + question). Jamais passif, jamais dégradé en silence. Violation = -20.

## Post-Deploy Smoke Test (BLOCKING sur app live)

Dans les 5 min : auth (endpoint protégé sans token → 401/403, avec → 200) ·
connexions API (health-check chaque downstream) · GlitchTip (erreur test remonte +
zéro pic post-deploy) · critical paths · reverse proxy (pas de 502/504) · régression
stale storage · régression durée de session (cookie Max-Age / refresh TTL) ·
connectivité inter-services end-to-end. Anti-pattern : un `/health` à 200 ne prouve
rien sur les downstreams — sonder le chemin critique de bout en bout (N/N joignables).
Échec → rollback/hotfix immédiat. **Fix = Deploy** : un fix n'est fini que déployé ET vérifié.

## Scoring V2

3 dimensions : Value 40% (livrable/publiable/utilisable) · Reliability 30% (rework,
régressions) · Process 30% (gates respectés). `Score = Value×0.4 + Reliability×0.3 +
Process×0.3`. Rapporter les 3 séparément + total.

## Documentation & Suivi (BLOCKING — A8)

Maintenir la doc à jour PENDANT le travail : notes Obsidian, README, suivi
(CDC/PET/rapports), état infra/archi. Une doc périmée casse la transmission (Monozukuri #4).

## Agents — Orchestration (A10)

Déléguer à l'agent dont c'est le métier ; les faire se concerter sur un sujet croisé
(2+ domaines) ; viser leur expertise max. La réponse cite quel(s) agent(s) ont
contribué. Max 4 concurrents.

**Détail** (protocoles développés, PR Upstream Gate, Marketing Automation Gate,
Deploy Layout Convention, Nginx Maintenance Pages, Cross-Browser, Pre-RAG Audit,
Code Registry, Platform Minimums, Non-Tech Agents BEFORE/AFTER, origines/historique) → Shinzo.
