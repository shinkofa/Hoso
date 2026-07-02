# PET — Hōsō (放送)

> **Plan d'Exécution Technique** = journal d'exécution vivant. Mis à jour à chaque brique (avant + après).
> Architecture 2-documents (méthodo v6.0.0) : `CDC-Hoso.md` (intention) + ce PET (exécution).
> Cross-référence asymétrique : le PET référence le CDC. Le CDC ne référence pas le PET.
> Référence audit : `02-Hoso/Audits/Audit-Hoso-2026-06-09.md`.

**Version** : 0.1.1 (audit cohérence) | **Date** : 2026-06-11 | **CDC source** : `CDC-Hoso.md` v1.1.0

> **Journal des versions**
> - 0.1.1 (2026-06-11) — Audit de cohérence CDC/PET (Jay). Corrections : traçabilité §3 (B-005→B-004), fork exclu de la couverture §4, B-001 doté d'une preuve exécutable, B-010 élargi (minimums accessibilité jour-1 + nettoyage code mort), backlog morphique ajouté, ADR chiffrement B-009.
> - 0.1.0 (2026-06-09) — Création.

---

## §1 — Principe d'exécution

- **Brique par brique** : une brique = un changement cohérent, achevé, testé, committé. Pas de « on finira après ».
- **TDG (Test-Driven Generation)** : le test est écrit AVANT le code, il échoue (rouge), puis le code le fait passer (vert).
- **Commits atomiques** : un changement logique par commit. Convention `type(scope): description`.
- **Cadence backup** : tag `backup/<sujet>-<date>` tous les 3-4 commits ou avant toute brique à risque.
- **Trace continue** : chaque brique remplit son entrée au §7 (scope, veille, tests, preuves, commit) pendant le travail, pas après.
- **Brownfield** : l'app est déjà fonctionnelle et publiée (v0.1.0). Chaque brique préserve le comportement existant — test anti-régression obligatoire sur tout module Sensitive touché.

---

## §2 — Anti-Circular Testing Protocol

> Même IA écrit le code ET les tests = validation circulaire. Trois couches de défense.

| Couche | Méthode | Application Hōsō | Obligatoire ? |
|---|---|---|---|
| 1 — Algorithmique | Propriétés formelles + injection de fautes | Property-based testing sur `mixInPlace` (PCM bornes), `AudioGains` (clamping), parser IRC (déjà dense) | Oui — toujours |
| 2 — Contexte différent | Session d'écriture ≠ session de revue + tests cachés | Modules Sensitive (mixeur, capture, clients réseau) : revue en session séparée | Oui — modules Sensitive |
| 3 — Modèle différent | Un autre LLM relit | DeepSeek/Koshin en revue sur le mixeur audio (bug racine historique) | Recommandé |

**Note Hōsō** : pas de chemin Critical (ni auth compte, ni paiement, ni crypto de sécurité). La couche 2 s'applique aux modules Sensitive cœur — fiabilité du stream = valeur L3.

---

## §3 — Traçabilité bidirectionnelle (CDC → bricks → tests)

> Correction 2026-06-11 : les tests P1-P6 sont portés par **B-004** (outillage couverture + tests), pas B-005 (CI). Numérotation alignée sur le §6.

| CDC §3 / besoin | Brique(s) | Tests de preuve |
|---|---|---|
| F-003 Mixeur audio (Sensitive, bug racine non testé) | B-004 | `MixedAudioSourceTest.mixInPlace` (P1), `AudioGainsTest` (P2) |
| F-001/F-002 Capture + presets (persistance) | B-004 | `DestinationPresetSerializationTest` (P3), `AudioSourceMigrationTest` (P4) |
| F-007 Chat Twitch (TLS, reconnect) | B-004 | `TwitchIrcClientHandleLineTest` (P5) |
| F-008 Bridge Streamer.bot (auth, WebSocket) | B-004 | `StreamerBotClientRouteFrameTest` (P6) |
| F-004/F-005 Overlay commande + HUD | B-001, B-010 | Smoke test appareil réel : overlay isolé (stream non cassé) — voir B-001 §7 |
| F-014 Crash reporting GlitchTip (opt-in) | B-006 | Vérif opt-out par défaut ; capture test (event envoyé seulement si activé) |
| F-015 Feedback widget natif | B-007 | Test : 2 taps, zéro donnée perso dans le payload |
| F-016 Lien de don externe | B-011 | Test : lien externe, aucun Play Billing, aucune pression |
| CDC §6 Transport chiffré (RTMPS, NSC scopé) | B-003 | Test config : défaut RTMPS ; trafic clair restreint aux domaines ingest |
| CDC §6 Confidentialité (clé chiffrée) | B-009 | Test : clé de stream lue/écrite via stockage chiffré |
| CDC §9 Minimums accessibilité jour-1 (mouvement réduit, thème sombre, 44×44, confirmation Stop) | B-010 | Vérif : mouvement réduit respecté, cibles ≥ 44×44 px, confirmation sur Stop |
| CDC §3 F-020 Adaptation morphique (avancée) | Backlog évolution | Délégué à [[Module-Morphique]] — hors périmètre publication |
| CDC §10 Trilingue FR/EN/ES | B-008 | Vérif ressources `values-fr/en/es` complètes (3 langues, zéro clé manquante) |
| CDC §4 Dette maintenabilité (God Classes) | B-010 | Suite existante verte après découpage (zéro régression) |

---

## §4 — 5 métriques de fiabilité des tests

| Métrique | Cible | Statut actuel (audit 2026-06-09) |
|---|---|---|
| Couverture de ligne | Sensitive 90% / Standard 80% — **hors `streampack-fork`** (exclu du calcul, CDC §7) | 🔴 non mesurée (ni JaCoCo ni Kover) — B-004 |
| Tests vides | 0 | ✅ 0/39 |
| Tests triviaux | < 10% | ✅ 0/39 |
| Ratio mock:assert | < 3:1 par test | ✅ 0:123 (zéro mock aujourd'hui) |
| Couverture de type (compilateur Kotlin strict) | 100% code neuf | ✅ compilateur seul ; detekt/ktlint à ajouter (B-004) |

> **Outillage couverture (B-004)** : configurer l'exclusion du module `streampack-fork` dans JaCoCo/Kover. On mesure le code de l'app + notre patch fork, jamais l'usine upstream. Sinon la barrière de couverture est structurellement impossible à passer.

---

## §5 — Assertions défensives (modules critiques/Sensitive)

> Chaque fonction Sensitive cœur porte ≥ 2 assertions de garde.

| Fonction | Module | Assertions cibles (≥ 2) |
|---|---|---|
| `mixInPlace()` | MixedAudioSource | (1) range de mix indexé par `capacity()` jamais `position()` ; (2) somme PCM clampée aux bornes Int16 (pas d'overflow/clip) |
| gain apply | AudioGains | (1) gain clampé dans `[min, max]` connus ; (2) conversion permil→float bornée, jamais NaN/négatif |
| `configureAndStart()` | StreamLauncher | (1) config codec non nulle avant start ; (2) token MediaProjection présent sinon abort propre |
| `runReconnectLoop()` | ScreenRecordService | (1) backoff borné (pas de boucle serrée) ; (2) état `isRunning` cohérent avant chaque tentative |
| `parse()` | IrcMessage | (1) ligne non vide ; (2) tags malformés ne lèvent pas — retour sûr (déjà couvert par 15 vecteurs) |
| `routeFrame()` | StreamerBotClient | (1) corrélation id connue avant dispatch ; (2) état d'auth validé avant action |

---

## §6 — Roadmap (bricks) — VIVANT, mis à jour à chaque brique

> Statuts : ⬜ Pending · 🔧 En cours · ✅ Done · ⏸ Bloqué

### Phase A — Publication Play Store (lever le blocker + gates)

| Brique | Scope | Risque | Priorité | Statut |
|---|---|---|---|---|
| **B-001** | Sortir OverlayService du FGS « specialUse » (bound service de ScreenRecordService, ou sans FGS) | 🔴 Blocker review | P0 | ⬜ |
| **B-002** | targetSdk 35→36, aligner cible JVM 17 partout (app vs fork), affiner ProGuard `keep` | Conformité 2026-08-31 | P1 | ⬜ |
| **B-003** | Hygiène release : `isShrinkResources=true`, signing CI-safe (échoue si secret absent), NetworkSecurityConfig scopé, défaut RTMPS | HIGH | P1 | ⬜ |
| **B-004** | Outillage couverture (JaCoCo ou Kover, **fork exclu**) + detekt/ktlint + tests P1-P6 (mixeur audio P1 en tête) | 🔴 Sensitive non testé | P1 | ⬜ |
| **B-005** | Pipeline CI `release.yml` (`bundleRelease` + injection secrets) | HIGH | P1 | ⬜ |
| **B-006** | Crash reporting GlitchTip opt-in (F-014) + privacy policy + formulaire Data Safety | Publication | P1 | ⬜ |
| **B-007** | Feedback widget natif in-app (F-015) — 2 taps, zéro donnée perso | Publication | P2 | ⬜ |
| **B-008** | Trilingue FR/EN/ES — ressources `values-fr/en/es` | Publication | P2 | ⬜ |
| **B-009** | Chiffrement clé de stream + password Streamer.bot — **outil à confirmer par veille** (EncryptedSharedPreferences possiblement déprécié) | MED défensif | P2 | ⬜ |

### Phase B — Dette de maintenabilité + confort jour-1

| Brique | Scope | Risque | Priorité | Statut |
|---|---|---|---|---|
| **B-010** | Découper OverlayService (1402 L) + MainActivity (690 L) sous le seuil 500 · nettoyage (2 fonctions mortes, 3 couleurs en dur → constantes) · minimums accessibilité jour-1 (mouvement réduit, thème sombre, cibles 44×44, confirmation sur Stop) | Dette BLOCKING taille + confort sensoriel | P2 | ⬜ |

### Phase C — Commercialisation

| Brique | Scope | Risque | Priorité | Statut |
|---|---|---|---|---|
| **B-011** | Lien de don externe (F-016) — hors app, aucun Play Billing, aucune pression | Commercialisation | P3 | ⬜ |

### Backlog Évolution (non ordonnancé — abstractions d'abord)

| Élément | Dépend de |
|---|---|
| Abstraire source caméra (via StreamPack) | socle F-011 (facecam), F-017 (caméra seule), F-018 (podcast) |
| Abstraire source audio (entrées multiples) | socle F-019 (Bluetooth) |
| **F-020 Intégration Module d'Adaptation Morphique** (adaptation cognitive/sensorielle) | [[Module-Morphique]] — aligné écosystème, point d'intégration couche UI (CDC §4) |
| F-012 Multi-stream simultané | — |
| F-013 Chat overlay v2 (emotes, badges, mention) | — |

---

## §7 — Détail par brick

> Rempli AU FUR ET À MESURE. Chaque brique : scope · veille préalable · tests TDG · analyse d'impact · implémentation · tests post (preuves) · erreurs · décisions in-flight · commit SHA.

### B-001 — Sortir OverlayService du FGS specialUse
- **Statut** : ⬜ Pending
- **Scope** : retirer `foregroundServiceType="specialUse"` d'OverlayService. Option retenue à la conception : bound service de ScreenRecordService (capture déjà FGS légitime `mediaProjection`) OU overlay sans FGS via `WindowManager`. Décision technique finale à la première ligne de code (analyse d'impact sur le cycle de vie).
- **Veille préalable** : (à faire au démarrage de la brique) — règles foreground service Android 14/15, `TYPE_APPLICATION_OVERLAY`.
- **Tests TDG / preuve exécutable** : un changement de cycle de vie de service ne se prouve pas en test unitaire pur. **Preuve = smoke test sur appareil réel, documenté** (procédure + résultat capturés dans cette entrée à l'exécution) :
  1. Lancer un stream, vérifier l'overlay présent et fonctionnel.
  2. Provoquer une erreur overlay (ex. fermeture fenêtre) → le stream **continue** (isolation G6.2 préservée).
  3. Vérifier qu'aucun service ne déclare plus `specialUse` (`grep` manifest) sauf justification finale documentée.
  - Si une partie est isolable sans Android (logique de bind/état) → test unitaire Robolectric en complément (dépend de l'outillage B-004 ; non bloquant pour la preuve principale).
- *Reste à remplir à l'exécution.*

### B-002 → B-011
- *Entrées créées à l'ouverture de chaque brique. Non démarrées.*

---

## §8 — Détection PII (données personnelles)

| Axe | Configuration |
|---|---|
| Surface de collecte | **Zéro compte, zéro donnée serveur** (design CDC §10). Risque PII quasi nul par conception. |
| Clé de stream | Donnée sensible utilisateur (pas une PII d'identité, mais un secret) → chiffrement B-009. |
| Crash reporting (GlitchTip) | Opt-in désactivé par défaut. Payload à auditer : aucun identifiant, aucune clé de stream, aucun chemin perso. Scrubbing avant envoi. |
| Feedback widget | Capture de contexte technique uniquement (page, action, état) — zéro donnée perso (B-007). |
| Logs en release | Auditer : `sendRaw` loggue `PASS` IRC (audit LOW #5) → masquer ; `assumenosideeffects` sur `Log.v/d/i` en release. |
| Outil | Revue manuelle du payload GlitchTip + grep statique des `Log.*` sur secrets, à chaque brique réseau. |

---

## §9 — Quality Gates pré-commit (checklist par brique)

| Gate | Vérification |
|---|---|
| Couverture | Sensitive ≥ 90% / Standard ≥ 80% sur le code touché (mesurable dès B-004, fork exclu) |
| Lint | detekt/ktlint zéro erreur (dès B-004) ; compilateur Kotlin sans warning |
| Tests | `./gradlew test` vert — unit + anti-régression sur module Sensitive touché |
| Sécurité | zéro secret en dur, transport chiffré respecté, payload sans PII |
| Accessibilité | cibles 44×44 px, mouvement réduit respecté (modules UI) |
| Veille | marqueur `[VEILLE]` posé avant tout ajout de dépendance ou version |
| Confidentialité | aucune donnée perso écrite dans code/log/commit |
| Taille | aucun fichier touché ne dépasse 500 L après la brique (objectif B-010) |

> Cross-browser / Core Web Vitals : N/A (app native Android, cf. CDC §10).

---

## §10 — Vérification post-déploiement

> « Déploiement » Hōsō = publication d'un build (Play Store / GitHub Releases / IzzyOnDroid), pas un service VPS.

| Check | Comment |
|---|---|
| Build produit | AAB signé release (jamais debug-signé) — vérif via CI B-005 |
| Smoke install | Installer l'AAB/APK sur appareil réel, lancer un stream test → son + vidéo confirmés (Twitch Inspector) |
| Crash-free | GlitchTip remonte les sessions opt-in ; seuil ≥ 99% (CDC §11) |
| Feedback widget | Présent et fonctionnel (2 taps) après publication (B-007) |
| Permissions | MediaProjection : consentement frais par session confirmé sur appareil |
| Régression OEM | Test sur ColorOS (appareil Jay) + 1 autre constructeur — service non tué silencieusement |

---

## §11 — Risques rencontrés en exécution

> Différent du FMEA du CDC (anticipé). Ici : risques DÉCOUVERTS pendant le travail.

| Date | Risque découvert | Brique | Mitigation |
|---|---|---|---|
| — | *(vide à la création — rempli à l'exécution)* | — | — |

---

## §12 — Décisions architecturales (ADR-light)

> Décisions in-flight non présentes dans le CDC.

| Date | Décision | Raison | Brique |
|---|---|---|---|
| 2026-06-09 | OverlayService : viser un bound service de ScreenRecordService plutôt qu'un FGS dédié | Évite la justification « specialUse » invalide ; la capture porte déjà un FGS `mediaProjection` légitime | B-001 |
| 2026-06-09 | Couverture via JaCoCo **ou** Kover — choix à la brique selon compat AGP 8.13 | Outil le mieux supporté par la version de build | B-004 |
| 2026-06-11 | `streampack-fork` exclu du calcul de couverture (mesure du patch uniquement) | Code vendu upstream ; 90% inatteignable fausserait la barrière qualité | B-004 |
| 2026-06-11 | Outil de chiffrement de la clé de stream **non figé** — veille avant code | EncryptedSharedPreferences candidat mais possiblement déprécié 2026 ; éviter une dette dès le jour 1 | B-009 |
| 2026-06-11 | Accessibilité avancée déléguée au Module d'Adaptation Morphique (F-020) ; seuls les minimums jour-1 en B-010 | Alignement écosystème ; publication Play Store ne peut pas attendre l'intégration morphique | B-010 / backlog |

---

## §13 — Déviations vs CDC

| Date | Déviation | Permanent ? → MAJ CDC | Brique |
|---|---|---|---|
| — | *(vide à la création)* | — | — |

---

## §14 — Journal de session

| Date | Session | Scope PET |
|---|---|---|
| 2026-06-09 | `docs/Sessions/Session-2026-06-09-001.md` | Audit + CDC v1.0.0 |
| 2026-06-09 | `docs/Sessions/Session-2026-06-09-002.md` | Vérif cohérence CDC + création PET v0.1.0 |
| 2026-06-11 | (session courante) | Audit cohérence CDC/PET — 6 corrections (PET v0.1.1 / CDC v1.1.0) |

---

*PET — 14 sections. Roadmap : 11 bricks (Phase A publication, B maintenabilité + confort jour-1, C commercialisation) + backlog évolution (dont F-020 morphique). CDC source : `CDC-Hoso.md` v1.1.0. Audit : `02-Hoso/Audits/Audit-Hoso-2026-06-09.md`.*
