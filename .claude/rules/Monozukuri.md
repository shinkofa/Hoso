# Monozukuri — Le métier comme identité

> Source complète : github.com/theermite/Shinzo · `07-Methode/Regles/Monozukuri.md`
> Philosophie chapeau. BLOCKING. Source de la rigueur ; les autres règles en sont
> les manifestations opérationnelles.

**Le principe** : Monozukuri (ものづくり) — l'art de faire, où la qualité n'est pas
une étape mais l'identité de celui qui produit. 3 implications :
1. **Qualité intrinsèque, pas ajoutée** — chaque geste porte déjà la qualité. Si ce
   n'est pas propre maintenant, ça ne le sera pas après.
2. **L'artisan répond du résultat dans le temps** — le travail doit tenir 6 mois,
   2 ans. La fiabilité est une caractéristique de l'artisan.
3. **Le métier se transmet** — la trace écrite (commit, doc, rapport) permet au
   suivant de reprendre. Code sans trace = méthodologie cassée.

**Les 6 comportements opérationnels (BLOCKING)** :

| # | Comportement | Trace observable |
|---|--------------|------------------|
| 1 | Chaque brique parfaite | git diff sans TODO / FIXME / console.log / code commenté |
| 2 | Rigueur > Vitesse | pas de skip de gate ; tests AVANT le code |
| 3 | L'erreur est une donnée | logs lus AVANT toute hypothèse ; pas de try/except/pass |
| 4 | Documentation = matière première | rapport de session, commits explicatifs, Why dans le code |
| 5 | La preuve, jamais l'affirmation | « ça devrait marcher » interdit ; on exécute, on montre |
| 6 | L'artisan répond du temps long | décision évaluée sur sa tenue à 6 mois ; sécurité jour 1 |

**Test de conformité (fin de session)** — répondre OUI aux 6 : commits achevés ?
rigueur tenue (pas de skip) ? erreurs lues avant correction ? trace suffisante pour
reprise ? chaque « ça marche » prouvé ? choix tiennent à 6 mois ? Une réponse
« non » non justifiée = -10 Reliability + flag.

**Anti-Quick-Fix Marker (BLOCKING — hook-enforced)** : tout commit `fix:` /
`hotfix:` (scopes inclus, case-insensitive) exige le marqueur :

```
[ROBUSTNESS]
- 6 mois: <pourquoi ça tient dans 6 mois>
- cause racine: <oui — laquelle | non — symptôme assumé car ...>
- alternative durable: <aucune valable | voici X mais reportée car Y>
```

Les 3 lignes obligatoires. Skip légitime :
`[ROBUSTNESS-SKIP] motif: <typo|revert|test-fix|lint-fix|formatting|comment-only>`
(enum fermé). 3 couches : enum fermé · sujet sensible (regression / recurring /
again / Revert) · compteur 3 SKIP consécutifs → block.

**Détail** (lien avec les autres règles, pourquoi cette règle existe, sources) → Shinzo.
