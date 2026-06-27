# QE-V2-Retroactive — QE V2 = floor automatique

> Source complète : github.com/theermite/Shinzo · `07-Methode/Regles/QE-V2-Retroactive.md`

**Niveau** : BLOCKING — floor automatique, jamais une option à activer.

**Règle** : Applique les standards QE V2 à TOUT artefact — neuf ou existant,
pré-4.0.0 inclus. Pas de grandfather clause. Application automatique : ni Jay
ni Takumi n'a à la demander.

**Pourquoi** : un floor qu'il faut réclamer n'est pas un floor. Qualité
intrinsèque (Monozukuri), pas ajoutée à la demande.

**Déclencheurs** :

| Quand | Action |
|-------|--------|
| Session start (projet existant) | Vérifier CDC §7 Risk Classification + §9 Human Quality Gates, PET §6 Roadmap, anti-circular sur paths critiques, feedback widget. Manque → signaler, proposer MAJ. **Ne PAS auto-fixer.** |
| Modif code existant | Dans le périmètre : assertions défensives (≥2/fn critique), 5 métriques de test, détection PII, type coverage 100% neuf. Corriger dans la même brique. |
| `/audit` | Cross-check checklist QE V2 (25 décisions), rapport priorisé par risque (Critical d'abord). |

**Défaut** : signaler les écarts, proposer, jamais auto-fixer au session start.
Jay décide timing et priorité.

**Détail complet** (tables de check colonne par colonne, table priorité projets) → Shinzo.
