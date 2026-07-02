# Hoso — Plan d'action publication Play Store

> Audit + plan chiffré. Créé 2026-06-14 (Takumi).
> Source veille : developer.android.com + support.google.com/googleplay (consulté 2026-06-14).
> État features : roadmap G1→G7 livrée. Reste uniquement la mise en conformité publication.

## Légende effort

| Code | Durée approx. |
|------|---------------|
| XS | < 30 min |
| S | ~1 h |
| M | ~1 séance (demi-journée) |
| L | plusieurs séances |

## Légende statut

⬜ À faire · 🟡 En cours · ✅ Fait · ⏸️ Bloqué/différé

---

## Constats veille Google Play (2026-06-14)

| Constat | Impact |
|---------|--------|
| `specialUse` accepté mais justification + vidéo démo validée à la main | Risque élevé sur 2 services |
| Tous les FGS (capture, micro, specialUse) = déclaration Play Console (description + vidéo) | 4 déclarations à produire |
| Privacy policy obligatoire : URL publique **ET** lien/texte dans l'app, doit déclarer l'audio | Bloquant |
| Data safety form obligatoire, dépend de la privacy policy | Bloquant |
| Policy "Photo & Video" 2025 : **non applicable** (pas d'accès médias stockés) | RAS |

---

## Phase A — Réduire le risque technique (code, AVANT les déclarations)

> Pourquoi d'abord : moins de services `specialUse` = moins de déclarations risquées à justifier. On change le code avant de figer les déclarations.

| # | Tâche | Effort | Statut |
|---|-------|--------|--------|
| A1 | Évaluer si `ChatBubbleService` peut se lier au FGS du stream (comme OverlayService) au lieu d'être un FGS `specialUse` | M | ⬜ |
| A2 | Évaluer si `StreamerBotService` peut faire pareil (binding au stream, plus de FGS propre) | M | ⬜ |
| A3 | Si A1/A2 OK : refactor + retrait des 2 `specialUse` + permission manifest | M | ⬜ |
| A4 | Si A1/A2 impossible (ex : services doivent vivre hors stream) : documenter la justification pour la déclaration Google | S | ⬜ |

**Résultat visé** : idéalement 0 service `specialUse`. Sinon, justification béton prête.

---

## Phase B — Conformité technique build

| # | Tâche | Effort | Statut |
|---|-------|--------|--------|
| B1 | Remplacer `usesCleartextTraffic=true` global par une network-security-config restreinte (autoriser cleartext seulement hôtes RTMP + Streamer.bot local) | S | ⬜ |
| B2 | Passer `versionName` en `1.0.0` et fixer `versionCode` de release | XS | ⬜ |
| B3 | Vérifier l'icône adaptive complète (foreground + background présents) | XS | ⬜ |
| B4 | Build AAB release signé (keystore), tester sur device : minify/ProGuard ne cassent rien | S | ⬜ |

---

## Phase C — Légal & déclarations Play Console (pas de code)

| # | Tâche | Effort | Statut |
|---|-------|--------|--------|
| C1 | Rédiger la privacy policy (déclare : micro, capture écran, chat Twitch public, Streamer.bot local ; aucune donnée envoyée à un backend Hoso) | M | ⬜ |
| C2 | Héberger la privacy policy sur une URL publique stable | S | ⬜ |
| C3 | Ajouter un lien/texte privacy policy **dans l'app** (écran réglages ou à propos) | S | ⬜ |
| C4 | Remplir le Data safety form (dépend de C1) | S | ⬜ |
| C5 | Déclarations FGS (1 par type restant : mediaProjection, microphone, + specialUse si subsistants) : description + **vidéo démo** par type | M | ⬜ |
| C6 | Questionnaire de classification du contenu | XS | ⬜ |

---

## Phase D — Assets fiche store (métier de Jay — graphisme)

| # | Tâche | Effort | Statut |
|---|-------|--------|--------|
| D1 | Icône haute résolution 512×512 px | S | ⬜ |
| D2 | Feature graphic 1024×500 px | S | ⬜ |
| D3 | Captures d'écran téléphone (min. 2, idéalement en situation de stream) | M | ⬜ |
| D4 | Description courte + description longue (FR + EN) | S | ⬜ |

---

## Phase E — Soumission

| # | Tâche | Effort | Statut |
|---|-------|--------|--------|
| E1 | Compte Play Console actif (25 $ une fois) — vérifier s'il existe déjà | XS | ⬜ |
| E2 | Créer la fiche app + uploader l'AAB sur un track de test interne | S | ⬜ |
| E3 | Test interne réel (installer depuis le Play Store interne, vérifier permissions) | S | ⬜ |
| E4 | Soumettre en review production | XS | ⬜ |

---

## Ordre recommandé

1. **Phase A** (réduire specialUse) — décisif pour la suite, change ce qu'on déclare
2. **Phase B** (conformité build) — rapide, débloque l'AAB
3. **Phase C** (légal) — la privacy policy est le plus long, à lancer tôt en parallèle
4. **Phase D** (assets) — terrain de Jay, peut se faire en parallèle de A/B/C
5. **Phase E** (soumission) — quand tout le reste est prêt

## Estimation globale

| Phase | Charge cumulée approx. |
|-------|------------------------|
| A | 1–2 séances |
| B | ~1 séance |
| C | 1–2 séances (privacy policy = le gros) |
| D | 1 séance (Jay) |
| E | ~1 séance |

**Total : ~5–7 séances** selon l'issue de la Phase A (refactor specialUse) et la rédaction privacy policy.

## Risques ouverts

- **Phase A incertaine** : on ne sait pas encore si chat + Streamer.bot peuvent abandonner `specialUse`. À trancher par A1/A2 avant de chiffrer fermement.
- **Vidéos démo FGS** : Google exige une vidéo par type de service. Prévoir captures d'écran du téléphone en action.
- **Review specialUse** : si on garde un `specialUse`, le délai de review Google peut s'allonger (validation manuelle).

---

## Avancement

### 2026-06-14 — Phase A ✅ TERMINÉE

Les deux services `specialUse` éliminés. Plus aucun `specialUse` dans l'app.

| Tâche | Résultat |
|-------|----------|
| A1 (chat sans `specialUse`) | ✅ Chat lié au FGS du stream (Option 1, compagnon de live) — commit `3e3def6` |
| A2/A3 (Streamer.bot) | ✅ Retiré entièrement plutôt que refactoré — commit `32ae79a` |
| A4 (justification `specialUse`) | ❌ Sans objet — plus aucun `specialUse` à justifier |

Bonus hors-plan : P0 stream depuis overlay résolu (`a8f23fa`) + anneau live (`0c001a2`).

**Prochaine étape : Phase B** (conformité build).
