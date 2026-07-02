# CDC — Hōsō (放送)

> **Cahier des Charges** = intention figée. Modifié uniquement quand l'intention change.
> Architecture 2-documents (méthodo v6.0.0) : ce CDC (intention) + `PET-Hoso.md` (exécution).
> Référence audit : `02-Hoso/Audits/Audit-Hoso-2026-06-09.md`.
> Type : app Android brownfield (existante, fonctionnelle). But : production-ready + publication Play Store + évolution + commercialisation.

**Version** : 1.1.0 — validé | **Date** : 2026-06-11 | **Archétype** : Mobile App

> **Journal des versions**
> - 1.1.0 (2026-06-11) — Audit de cohérence CDC/PET (Jay). Corrections : couverture du fork sortie du calcul global (§7), accessibilité déléguée au Module d'Adaptation Morphique (§9, §3 F-020), outil de chiffrement marqué à confirmer par veille (§5), Bluetooth marqué cible évolution (§6).
> - 1.0.0 (2026-06-09) — Validation initiale section par section.

---

## §1 — POUR QUOI (les 3 couches) ✅ validé

### L3 — La destination (vision Shinkofa)
Le digital s'adapte à l'humain. Hōsō diffuse sans friction : aucun compte, aucune donnée extraite. La fiabilité et l'ergonomie non-invasive sont la preuve concrète du respect de l'utilisateur.

### L2 — Le chemin (visibilité magnétique)
Jay est la personne qui fait des applications hors du commun — qui fonctionnent vraiment, respectent l'humain, s'adaptent à l'utilisateur, et sont enfin agréables. Hōsō en est une preuve vivante (l'open-source est un moyen, pas l'identité). Un streamer mobile fiable et agréable devient une référence, et une référence attire par invitation (design Projector).

### L1 — Le prochain pas (énergie actuelle)
Écrire ce CDC puis le PET. Premières briques ensuite : sortir OverlayService du FGS specialUse (blocker review), hygiène de build/release, GlitchTip opt-in + privacy policy, tests du mixeur audio.

---

<!-- Sections suivantes ajoutées au fil de la validation section par section -->

## §2 — Utilisateurs cibles ✅ validé

**Cible cœur** : le streamer mobile solo. Il joue sur son téléphone et diffuse vers Twitch. Il veut un outil fiable et simple, pas une usine à gaz.

| Persona | Profil | Besoin clé | Frustration actuelle |
|---|---|---|---|
| **Le streamer mobile solo** (cœur) | Joue sur mobile, diffuse Twitch, souvent débutant | Stream fiable écran + son jeu + voix, en 2 taps | Streamlabs cher + watermark ; Larix moche + technique |
| **Jay — persona zéro** (dogfood) | Projector splénique, hypersensible | Ergonomie non-invasive, overlay de commande discret | Apps lourdes, intrusives, qui fatiguent |
| **Le streamer neurodivergent** | Surcharge sensorielle, besoin de prévisibilité | Faible charge cognitive, zéro pop-up surprise | Interfaces chargées, notifications agressives |

**Principe transverse** : l'adaptation neurodivergente profite d'abord à ces profils, mais le message reste universel. Zéro catégorisation au premier contact.

---

## §3 — Features ✅ validé

### Existant (livré, fonctionnel)

| ID | Feature | Statut |
|---|---|---|
| F-001 | Capture écran + diffusion RTMP | ✅ Livré |
| F-002 | Multi-destination par presets (Twitch, YouTube, Kick, RTMP custom) | ✅ Livré |
| F-003 | Mixeur audio mic + son du jeu, gains réglables en live | ✅ Livré |
| F-004 | Overlay flottant de commande (start/stop, déplaçable) | ✅ Livré |
| F-005 | HUD live dans l'overlay (débit, durée, état) | ✅ Livré |
| F-006 | Mode masque / confidentialité | ✅ Livré |
| F-007 | Overlay de chat Twitch (bulle libre, anonyme) | ✅ Livré |
| F-008 | Bridge Streamer.bot (actions, événements) | ✅ Livré (partiel) |
| F-009 | Reconnexion auto + auto-fade de l'overlay | ✅ Livré |
| F-010 | Persistance des presets et préférences | ✅ Livré |

### Évolution (à venir)

| ID | Feature | Phase / Note |
|---|---|---|
| F-011 | Caméra en incrustation du stream de jeu (facecam sur gameplay) | Évolution |
| F-012 | Multi-stream simultané (plusieurs destinations en même temps) | Évolution |
| F-013 | Chat overlay v2 (emotes, badges, surlignage mention) | Évolution |
| F-014 | Crash reporting GlitchTip (opt-in, réglage dédié) | Publication |
| F-015 | Feedback widget in-app (2 taps, zéro donnée perso) | Publication |
| F-016 | Lien de don externe | Commercialisation |
| F-017 | Mode caméra seule (sans jeu) : « chat » / « live stream » (IRL) | Évolution |
| F-018 | Mode podcast : vidéo (caméra) + audio seul | Évolution |
| F-019 | Prise en charge casques + micros Bluetooth | Évolution — touche pipeline audio (Sensitive), contraintes Android connues |
| F-020 | Intégration Module d'Adaptation Morphique — adaptation cognitive/sensorielle (charge cognitive, densité, confort) | Évolution — délégué à [[Module-Morphique]], aligné écosystème |

---

## §4 — Architecture ✅ validé

**Forme actuelle** : app Android mono-module Kotlin + fork StreamPack en build composite Gradle. Le moteur de capture/encodage vit dans le fork ; la logique métier dans l'app.

| Couche | Composants | Rôle |
|---|---|---|
| UI | MainActivity, écrans de config | Réglages app + stream |
| Services premier plan (FGS) | ScreenRecordService, OverlayService, ChatBubbleService, StreamerBotService | Capture, overlay, chat, bridge |
| Pipeline audio | MixedAudioSource, AudioGains | Mix mic + jeu, gains live |
| Clients réseau | TwitchIrcClient, StreamerBotClient | Chat anonyme, bridge WebSocket |
| Persistance | StreamConfig, DestinationPreset | Presets + préférences (local) |
| Moteur (fork) | StreamPack 3.1.2 patché | RTMP, encodage, pipeline |

**Overlay 3 fenêtres** : commande, masque plein écran, bulle de chat. Découplé du flux de stream (une erreur overlay ne casse pas le stream — acquis G6.2).

### Évolutions architecturales requises (issues de l'audit)

| Changement | Pourquoi |
|---|---|
| Sortir OverlayService du FGS « specialUse » | Blocker review Play Store |
| Découper OverlayService (1402 L) + MainActivity (690 L) | Dette maintenabilité (seuil 500) |
| Abstraire la source caméra (via StreamPack) | Socle pour F-011, F-017, F-018 |
| Abstraire la source audio (entrées multiples) | Socle pour F-019 (Bluetooth) |
| Point d'intégration adaptation morphique (couche UI) | Socle pour F-020 ([[Module-Morphique]]) |

---

## §5 — Stack technique ✅ validé

| Couche | Techno | Version | Veille | Note |
|---|---|---|---|---|
| Langage | Kotlin | 2.2.21 | 2026-06-09 | confirmé |
| Build | Android Gradle Plugin | 8.13.1 | 2026-06-09 | supporte 16KB |
| Build | Gradle | 8.13 | 2026-06-09 | wrapper |
| SDK | compileSdk 36 / targetSdk 35→**36** / minSdk 29 | — | 2026-06-09 | targetSdk 36 requis avant 2026-08-31 |
| Moteur stream | StreamPack (fork) | 3.1.2 + 1 patch | 2026-06-09 | fork local, 1 commit |
| Muxer | komuxer | 0.3.4 | 2026-06-09 | pur-JVM, zéro `.so` |
| Crash report | GlitchTip via SDK sentry-android | SDK à confirmer par veille (B-006) | 2026-06-09 | self-hosted, opt-in |
| Réseau | OkHttp (WebSocket) | à confirmer par veille (B-005) | — | utilisé par le bridge |
| Tests | JUnit 4 + org.json | présent | 2026-06-09 | runner OK |
| Tests à ajouter | JaCoCo ou Kover, MockK, Robolectric | — | 2026-06-09 | couverture + mocks |
| Qualité à ajouter | detekt ou ktlint | — | 2026-06-09 | analyse statique absente |
| Sécurité à ajouter | Chiffrement clé de stream — **outil à confirmer par veille** (B-009) | — | à faire | EncryptedSharedPreferences candidat MAIS possiblement déprécié 2026 → vérifier alternative (Tink, DataStore + crypto) avant d'écrire la ligne |

> OkHttp + SDK GlitchTip + outil de chiffrement : versions/choix non figés — marqueur `[VEILLE]` obligatoire à l'ouverture de la brique concernée (pas d'invention).

---

## §6 — Exigences non-fonctionnelles ✅ validé

| Catégorie | Exigence | Cible mesurable |
|---|---|---|
| Fiabilité | Stream stable, jamais coupé en silence | Reconnexion auto < 5s ; zéro crash propagé (overlay isolé) |
| Performance | Capture/encodage à faible surcoût | Aucun drop de frame à 720p30 ; débit stable |
| Latence audio | Mix mic + jeu sans décalage perçu | Latence de mix < 100 ms |
| Batterie | Service premier plan efficace | Consommation raisonnable sur 1h de stream |
| Compatibilité | Android 10+ multi-constructeur | minSdk 29 ; testé ColorOS + 1 autre |
| Bluetooth (cible évolution F-019) | Casques et micros Bluetooth gérés | Canal audio dédié pris en charge — livré avec F-019, pas au lancement |
| Ergonomie | Overlay non-invasif, démarrage rapide | Go Live en ≤ 2 taps ; overlay déplaçable |
| Accessibilité | Cibles tactiles, contraste, mouvement réduit | 44×44 px ; respect du mouvement réduit (minimums jour-1) ; adaptation avancée via [[Module-Morphique]] (F-020) |
| Confidentialité | Zéro compte, tout en local | Clé de stream chiffrée (cible) |
| Transport chiffré | Flux et trafic réseau protégés en transit | RTMPS par défaut (RTMP = protocole de stream, S = sécurisé) ; trafic en clair restreint aux seuls domaines d'ingest nécessaires |
| Mémoire | Pas de fuite sur les fenêtres overlay | Zéro fuite détectée |

> Cibles de départ — affinées par mesures réelles dans le PET.

---

## §7 — Classification risque ✅ validé

| Module | Niveau | Couverture cible |
|---|---|---|
| ScreenRecordService (capture + stream) | **Sensitive** | 90% |
| MixedAudioSource + AudioGains (mixeur) | **Sensitive** | 90% |
| StreamLauncher (config codec + start) | **Sensitive** | 90% |
| StreamerBotClient (auth + WebSocket) | **Sensitive** | 90% |
| TwitchIrcClient (socket TLS chat) | **Sensitive** | 90% |
| streampack-fork (moteur) | **Sensitive — surveillance** | **patch propriétaire uniquement** (fork exclu du calcul de couverture global — voir note) |
| OverlayService, ChatBubbleService, MainActivity | **Standard** | 80% |
| StreamConfig, DestinationPreset, StreamerBotService | **Standard** | 80% |
| StreamerBotProtocol, adapters | **Standard** | 80% |
| scripts (signing, icônes) | **Tooling** | 60% |

**Aucun module Critical** aujourd'hui (pas d'auth compte, pas de paiement, pas de crypto). Si un achat in-app est ajouté → ce module deviendra Critical (95%).

> **Note couverture fork (correction audit 2026-06-11)** : `streampack-fork` est un moteur vendu (forké d'upstream, ~milliers de lignes non écrites par nous). Exiger 90% de couverture dessus est inatteignable et fausserait la barrière qualité. On mesure et garantit uniquement **notre patch** (le delta d'1 commit). Le fork reste « Sensitive » au sens surveillance (test anti-régression sur la race audio + suivi divergence upstream), mais sort du calcul de pourcentage global. Le PET §4 exclut le fork de l'outil de couverture.

---

## §8 — FMEA (modes de défaillance) ✅ validé

> Pas de module Critical. FMEA appliquée aux modules Sensitive cœur car la fiabilité = valeur L3.

### Pipeline capture + stream
| Mode de défaillance | Sévérité | Mitigation |
|---|---|---|
| Token MediaProjection invalide en cours de session | Élevé | Consentement frais par session (conforme) + reconnexion + notification |
| Race audio RTMP (csd-0) → pas de son sur Twitch | Élevé | Patch fork (fait) + test anti-régression + check Twitch Inspector |
| Service tué par le constructeur (ColorOS) → coupure silencieuse | Élevé | Type de service correct + guide batterie + reconnexion + notification |

### Mixeur audio
| Mode de défaillance | Sévérité | Mitigation |
|---|---|---|
| Bug position vs capacity du ByteBuffer → mix corrompu/muet | Élevé | Test P1 + indexation par capacity |
| Micro Bluetooth pas prêt (canal dédié) → pas de voix | Moyen | F-019 : vérif d'état + repli + message |
| Gain hors borne → distorsion/clipping | Moyen | Clamping AudioGains + test P2 |

### Fork StreamPack
| Mode de défaillance | Sévérité | Mitigation |
|---|---|---|
| Divergence upstream au merge → patch perdu → race audio revient | Élevé | Patch isolé (1 commit) + test anti-régression + PR upstream |

---

## §9 — Human Quality Gates ✅ validé

| Gate | Exigence | Cible / Statut |
|---|---|---|
| Charge cognitive | Une action primaire, démarrage minimal | Go Live ≤ 2 taps ; ≤ 5 décisions par tâche — largement acquis |
| Confort sensoriel | Overlay discret, mouvement réduit | Auto-fade (acquis) ; minimums jour-1 (mouvement réduit, thème sombre, cibles 44×44) formalisés en B-010 ; adaptation cognitive/sensorielle avancée déléguée à [[Module-Morphique]] (F-020, évolution) |
| Résilience erreur | Sauvegarde auto + messages sans blâme | Config persistée (acquis) ; confirmation sur Stop (B-010) ; reconnexion |
| Adaptation | Préférences gardées entre sessions | Presets, gains, position overlay persistés — acquis ; adaptation morphique = couche supérieure future (F-020) |
| Dignité | Zéro dark pattern, consentement informé | Dons externes non poussés ; permissions expliquées ; zéro guilt-trip |
| Feedback widget | Signaler un bug en 2 taps | F-015 — capture de contexte, zéro donnée perso — à construire (B-007) |

> **Décision accessibilité (2026-06-11, Jay)** : l'adaptation sensorielle et cognitive avancée de Hōsō n'est pas recodée à la main — elle passe par l'intégration du **Module d'Adaptation Morphique** (F-020, [[Module-Morphique]]), pour rester aligné avec tout l'écosystème Shinkofa. Seuls les **minimums jour-1** indispensables à la soumission Play Store (mouvement réduit, thème sombre, cibles tactiles, confirmation sur Stop) sont traités en B-010, car la publication ne peut pas attendre l'intégration morphique complète.

---

## §10 — Hors scope + déviations ✅ validé

### Hors scope explicite
| Hors scope | Raison |
|---|---|
| Compte utilisateur, backend, données serveur | Design assumé |
| Achat in-app / Play Billing | Dons externes uniquement |
| Version iOS | Android seulement |
| Companion web ou desktop | Hors périmètre |
| Sync cloud | Sauf premium futur éventuel |

### Déviations vs checklist universelle (orientée web)
| Item | Décision | Justification |
|---|---|---|
| @shinkofa/ui, i18n, types | N/A (lib) | Librairies React web ; Hōsō est natif Android Kotlin |
| Cross-navigateur, Core Web Vitals | N/A | App native, pas de navigateur |
| Feedback widget | Version native | Le widget web ne s'applique pas |

### Langues
| Item | Décision | Mécanisme |
|---|---|---|
| Trilingue FR/EN/ES | ✅ dès le lancement | Ressources Android (`values-fr/en/es`), contenu d'@shinkofa/i18n recopié manuellement (lib web non importable en natif) |
| Autres langues | Évolution future | — |

---

## §11 — Métriques de succès ✅ validé

| Dimension | Métrique | Cible |
|---|---|---|
| Publication | App publiée + review passée | Live sur Play Store |
| Stabilité | Taux de sessions sans crash, parmi les sessions où le report est activé (GlitchTip opt-in) | ≥ 99% |
| Fiabilité stream | Coupures non récupérées automatiquement | 0 sur sessions test |
| Qualité code | Couverture Sensitive / Standard (hors fork, voir §7) | 90% / 80% atteints |
| Adoption | Installs + streamers actifs + rétention J30 | Référence early (à mesurer post-lancement) |
| Réputation | Note Play Store | ≥ 4.3 |
| Référence | Étoiles dépôt public + mentions | Croissance continue |
| Commercialisation | Dons reçus | Signal de soutien, jamais sous pression |

> Dignité : les dons sont un signal, jamais un objectif de pression.
> Note : la métrique sans-crash ne couvre que les utilisateurs ayant activé le report (désactivé par défaut, cf. §13) — elle mesure une tendance, pas la population entière.

---

## §12 — Visibilité ✅ validé

| Levier | Action |
|---|---|
| Canaux clés | Dépôt public + fiche Play Store + démos vidéo (YouTube/TikTok) + blog The Ermite + Reddit (r/Twitch, r/streaming) |
| ASO (fiche Play Store) | Mots-clés : streaming mobile, Twitch mobile, capture écran ; captures + vidéo démo |
| GEO (moteurs génératifs) | README + comparatif vs Streamlabs/Larix → repris par les assistants IA |
| Distribution | Play Store + IzzyOnDroid + F-Droid (open-source) |
| Pipeline contenu | Blog The Ermite → LinkedIn/Discord/Telegram (déjà actif) |
| Capture | Pas d'email (zéro compte) : étoiles dépôt + communauté Discord + page de don |

> Différenciateur magnétique : un streamer mobile Android fiable, gratuit, non bridé — là où Streamlabs est cher et Larix austère.

---

## §13 — Anti-patterns projet ✅ validé

| Anti-pattern | Pourquoi on l'interdit |
|---|---|
| Watermark / limite de temps / cap de débit sur le gratuit | Le palier gratuit doit rester pleinement utilisable |
| Compte forcé | Zéro friction, zéro extraction de données |
| Télémétrie sans opt-in | Crash report désactivé par défaut |
| Overlay invasif, nag screens | Ergonomie non-invasive = cœur de valeur |
| Dark pattern sur les dons | Dignité : le don est un signal, pas une pression |
| Abus de service premier plan « specialUse » | Cause du blocker review actuel |
| Laisser grossir les God Classes | Dette maintenabilité (OverlayService, MainActivity) |
| Module Sensitive sans test | Leçon du mixeur audio (bug racine non testé) |
| Coupure de stream silencieuse | Toujours notifier + reconnecter |
| Secret en dur dans le code | Pipeline signing vault-backed déjà propre |
| Couverture forcée sur du code vendu (fork) | Fausse la barrière qualité — on mesure notre patch, pas l'usine upstream |

---

*CDC complet — 13 sections. Référence audit : `02-Hoso/Audits/Audit-Hoso-2026-06-09.md`. Document d'exécution : `02-Hoso/PET/PET-Hoso.md`.*
