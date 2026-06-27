# Rule-Format — Comment écrire une règle

> Source complète : github.com/theermite/Shinzo · `07-Methode/Regles/Rule-Format.md`
> Standard d'écriture de toute règle. Appliqué à la création / édition de règles,
> pas à chaque session.

**Le principe** : une règle écrite ne s'applique pas seule. Un LLM récite
parfaitement la règle qu'il vient de violer, et le raisonnement dégrade le suivi
des règles strictes. Écrire pour le fonctionnement réel des modèles, pas pour faire
joli. Reste portable hors Claude Code.

**Le format — 6 champs, ordre fixe** :

| Champ | Rôle | Obligatoire ? |
|-------|------|---------------|
| Niveau | BLOCKING / DÉFAUT / GUIDE — un seul marqueur | oui |
| Règle | énoncé au POSITIF, impératif, déterministe | oui |
| Pourquoi | 1 ligne — la raison | oui |
| Déclencheur | QUAND, nommé précisément (jamais « si pertinent ») | oui |
| Preuve | artefact falsifiable (marqueur daté, test) — JAMAIS une coche | si vérifiable |
| Sans hook | ce que l'IA fait quand aucun code n'applique | si portable |

**Géométrie variable** (au-delà de ~30 instructions actives, la conformité chute) :
BLOCKING = 6 champs ; DÉFAUT = Règle + Pourquoi + Déclencheur ; GUIDE = Règle + Pourquoi.

**La clé : preuve falsifiable, jamais une coche.** Une case « ✓ vérifié » est
non-falsifiable (le modèle la coche comme un mot plausible). Un artefact daté et
sourcé est vérifiable. Règle d'or : demander un artefact vérifiable, jamais une
auto-attestation.

**Anti-négation** : écrire chaque règle au positif (« fais X »). C'est de la
fiabilité, pas du style — un modèle lit mal la négation, le token saillant reste X,
la consigne peut produire l'inverse. Si une interdiction est inévitable, coller
l'alternative positive juste après (« jamais rm -rf sur du travail ; à la place
mv x x-backup »).

**Règles d'écriture clés** : un seul marqueur d'emphase (pas BLOCKING+MUST+CRITICAL
empilés) ; déclencheur nommé ; jargon glosé la 1re fois ; pour un fichier de règles,
BLOCKING en tête ET rappelé en fin (lost-in-the-middle).

**Règles formes portables** :
- **P1** — définir la source unique (Shinzo) une fois en haut avec son chemin
  complet (github.com/theermite/Shinzo), jamais le nom seul (un modèle traduit
  心臓 = « cœur » et perd le repo).
- **P2** — chiffres = plafonds assortis d'une intention (« le plus court possible,
  max 1500 car »), jamais cibles seules (un modèle littéral remplit le plafond).

**Portabilité — le champ « Sans hook » en 3 paliers** : Code-enforced (Claude
Code / Kobo, un hook applique la preuve) ; Écrit structuré (Gemini / Perplexity /
chat, l'IA émet la preuve sans filet) ; Micro (≤1500 car, seul l'esprit + 2-3
BLOCKING tiennent).

**La limite honnête** : ce format augmente la probabilité de conformité, ne la
garantit pas. La seule dureté vient d'un vérificateur externe (hook, humain, 2e modèle).

**Détail** (fondement IA par champ avec sources arXiv / Anthropic, exemples
développés) → Shinzo.
