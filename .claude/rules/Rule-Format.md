# Rule-Format — Comment écrire une règle MNK-GoRin

> Le standard d'écriture de TOUTE règle de cette méthodologie.
> But : qu'une règle soit comprise ET suivie par n'importe quelle IA, avec ou sans hooks.
> Portée : appliqué quand on CRÉE ou MODIFIE une règle. Pas une règle de session ; ne fait pas partie du MANDATORY FIRST READ.
> Source : décision Jay 2026-06-23, fondée sur veille instruction-following / prompting 2025-2026.

## Le principe (POURQUOI ce format existe)

Une règle écrite ne s'applique pas toute seule. La recherche 2025-2026 le prouve : un LLM **récite parfaitement la règle qu'il vient de violer**, et le raisonnement **dégrade** le suivi des règles strictes (il "rationalise" l'écart). Donc une règle doit être écrite pour viser le fonctionnement réel des modèles, pas pour faire joli. Ce format encode ce qui marche, et reste portable hors Claude Code (OpenCode, Gemini, tout chatbot).

## Le format — 6 champs, ordre fixe

| Champ | Rôle | Obligatoire ? |
|-------|------|---------------|
| **Niveau** | `BLOCKING` / `DÉFAUT` / `GUIDE` — un seul marqueur, jamais empilé | oui |
| **Règle** | l'énoncé au POSITIF, impératif, déterministe ("fais X") | oui |
| **Pourquoi** | 1 ligne — la raison (augmente l'adhérence + gère les cas-limites) | oui |
| **Déclencheur** | QUAND elle s'applique, nommé précisément (jamais "si pertinent") | oui |
| **Preuve** | l'artefact falsifiable à produire (marqueur daté, test, sortie) — JAMAIS une coche | si vérifiable |
| **Sans hook** | ce que l'IA fait quand aucun code ne l'applique (émettre la preuve / répondre la question dans sa réponse) | si portable |

## Géométrie variable (anti-fatigue d'instructions)

Au-delà de ~30 instructions actives, la conformité des LLM chute. On n'alourdit donc que ce qui compte. Cinq règles vraiment suivies valent mieux que cinquante récitées.

| Niveau | Champs requis |
|--------|---------------|
| **BLOCKING** | les 6 |
| **DÉFAUT** | Règle + Pourquoi + Déclencheur |
| **GUIDE** | Règle + Pourquoi |

## La clé : preuve falsifiable, jamais une coche

Une case "✓ j'ai vérifié" est cochée par le modèle comme un mot plausible, pas comme la preuve d'un acte. Elle est non-falsifiable : on ne peut pas détecter le mensonge. Un artefact daté et sourcé, lui, est vérifiable.

| Forme | Fiabilité | Pourquoi |
|-------|-----------|----------|
| "✓ fait" | ❌ faible | non-falsifiable |
| `[VEILLE] react@19 vérifié 2026-06-23 via react.dev` | ✅ forte | daté, sourcé, "greppable" |
| "Pose 3 questions, réponds SANS relire ton brouillon, puis conclus" | ✅ forte | force le vrai travail (CoVe, +23% mesuré) |

Règle d'or : **demander un artefact vérifiable, jamais une auto-attestation.**

## Exemple — la règle Veille au format

> **Niveau** : BLOCKING
> **Règle** : Avant d'écrire du code touchant une version ou une dépendance, vérifie la version stable du jour sur le web, puis émets le marqueur de preuve.
> **Pourquoi** : le dataset du modèle est périmé ; une version supposée fausse casse en prod ou introduit une faille.
> **Déclencheur** : toute écriture sur un fichier source ou un manifeste de dépendances.
> **Preuve** : `[VEILLE] <techno>@<version> vérifié <date> via <source>` — daté, sourcé, vérifiable.
> **Sans hook** : l'IA émet le marqueur dans sa réponse AVANT le code ; pas de marqueur = pas de code.

## Les règles d'écriture de chaque champ

| Règle d'écriture | Pourquoi |
|------------------|----------|
| **Positif > négatif** ("fais X", pas "ne fais pas Y") ; si négatif obligatoire, ajoute l'alternative positive | le négatif est plus dur à suivre, gaspille des tokens de raisonnement |
| **Pourquoi obligatoire** sur chaque règle | le rationale augmente l'adhérence, pas que la compréhension |
| **Un seul marqueur d'emphase** (pas "CRITICAL + MUST + BLOCKING") | l'emphase empilée sur-déclenche / dilue |
| **Déclencheur nommé**, jamais "si pertinent" / "si besoin" | si l'IA doit juger la pertinence, le raisonnement dégrade la règle |
| **Jargon glosé** la 1re fois | portabilité : un modèle plus faible ne devine pas |
| Pour un fichier de règles : **BLOCKING en tête ET rappelé en fin** | l'info au milieu d'un long contexte est déprioritisée (lost-in-the-middle) |

## Pourquoi chaque champ tient (fondement IA, vérifié)

| Champ | Mécanisme IA ciblé | Source (2025-2026) |
|-------|--------------------|--------------------|
| Niveau non empilé | l'emphase agressive sur-déclenche | Anthropic (Claude 4.5/4.6) |
| Règle au positif | le négatif est plus dur à suivre | Anthropic + OpenAI |
| Pourquoi | le rationale ↑ l'adhérence | Anthropic ; constitution Claude "reason-based" jan 2026 |
| Déclencheur nommé | le raisonnement dégrade les règles strictes | "When Thinking Fails", arXiv 2505.11423 |
| Preuve falsifiable | recall ≠ compliance ; CoT fidèle ~25% | "Models Recall What They Violate" ; Anthropic |
| Sans hook | l'écrit seul ne garantit rien | findings hybrides 2025 |

## Portabilité — le champ "Sans hook" décline en 3 paliers

| Palier | Cible | Le champ "Sans hook" |
|--------|-------|----------------------|
| **Code-enforced** | Claude Code, OpenCode (hooks TS), Kōbō | un hook applique la Preuve ; le champ sert de filet |
| **Écrit structuré** | Gemini/Perplexity/chat | l'IA émet la Preuve dans sa réponse, sans filet code |
| **Micro** | custom instructions ≤1500 car | seul l'esprit + 2-3 BLOCKING tiennent ; le reste tombe |

## La limite honnête (à ne jamais oublier)

Ce format **augmente la probabilité** de conformité ; il ne la **garantit** pas. La seule dureté vient d'un vérificateur externe (hook, humain, ou 2e modèle qui relit). Sur un palier sans code, même la Preuve repose sur l'honnêteté du modèle. D'où : hooks pour le binaire critique, format écrit-falsifiable pour le reste, 2e modèle pour ce qui compte le plus.
