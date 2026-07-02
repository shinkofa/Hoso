# Hōsō — Préparation Play Console (Phase C légal)

> Doc de prép Takumi. À recopier dans les formulaires Play Console par Jay.
> Source : session 2026-06-14. App v1.0.0 (versionCode 2), `com.theermite.hoso`.
> Politique publique : `PRIVACY.md` racine repo → https://github.com/shinkofa/Hoso/blob/main/PRIVACY.md

---

## 1. Privacy policy (champ Play Console)

- **URL** : `https://github.com/shinkofa/Hoso/blob/main/PRIVACY.md`
- Accessible aussi **in-app** (ligne pied de page sous « Quitter ») — exigence Google
  pour les permissions sensibles (micro + capture écran). ✅ livré cette session.

---

## 2. Data safety form

Réalité du code : l'app **ne collecte rien**, ne partage rien, aucun analytics, aucun
crash reporting (GlitchTip décidé mais **pas implémenté** → ne ship pas → ne pas le
déclarer). Tout reste en stockage privé local.

| Question Play Console | Réponse |
|-----------------------|---------|
| Votre app collecte-t-elle des données utilisateur ? | **Non** (rien n'est transmis vers nous ; tout reste sur l'appareil) |
| Votre app partage-t-elle des données avec des tiers ? | **Non** |
| Les données sont-elles chiffrées en transit ? | N/A — aucune donnée collectée par nous |
| L'utilisateur peut-il demander la suppression ? | Oui — effacer les données de l'app ou désinstaller (données locales) |
| Compte requis ? | **Non** |

**Note clé de stream** : elle est sensible mais **fournie par l'utilisateur**, stockée
localement, envoyée **uniquement** au serveur RTMP qu'il choisit (Twitch/YouTube/Kick/
custom). Elle n'est jamais « collectée » par le développeur → ne se déclare pas dans
Data safety (qui ne couvre que les données quittant l'appareil vers nous/des tiers).

---

## 3. Déclarations Foreground Service (App content → FGS)

Deux types déclarés, sur `ScreenRecordService` (`mediaProjection|microphone`). Justifs
à coller :

| Type FGS | Justification |
|----------|---------------|
| `mediaProjection` | Capture l'écran de l'utilisateur pour le diffuser en direct vers le serveur RTMP qu'il configure. Initié par l'utilisateur, consentement système redemandé à chaque démarrage de stream. |
| `microphone` | Capte la voix de l'utilisateur pour l'inclure dans le stream en direct. Actif uniquement pendant le stream. |

### Script vidéo démo (Google exige une vidéo montrant l'usage du FGS)

YouTube **non répertorié** (~30-45 s), à enregistrer par Jay :

1. Ouvrir Hōsō → montrer l'écran de réglages (destination Twitch configurée).
2. Taper « PASSER EN DIRECT » → la **boîte de consentement capture écran Android**
   apparaît → accepter.
3. Montrer la **notification de stream actif** + la bulle overlay flottante (preuve
   que le FGS tourne).
4. Basculer vers Twitch (navigateur/app) → montrer le **stream en direct** quelques
   secondes (preuve que mediaProjection + micro servent au streaming).
5. Revenir, arrêter le stream → la notification disparaît.

But : prouver que les types FGS servent exactement à ce qui est déclaré (streaming
live), pas à un usage caché.

---

## 4. Classification du contenu (questionnaire IARC)

- **Catégorie app** : Outils / Utilitaire (streaming).
- Violence / sexe / drogue / jeu d'argent dans l'app : **aucun**.
- **Partage / diffusion de contenu utilisateur** : OUI — l'app diffuse l'écran de
  l'utilisateur vers des plateformes tierces (Twitch/YouTube/Kick). Répondre
  honnêtement « les utilisateurs peuvent partager/diffuser du contenu en ligne ».
- Interaction entre utilisateurs **dans l'app** : non (le chat Twitch est lu seul, en
  anonyme ; pas de messagerie in-app).
- Localisation partagée : non.
- **Rating attendu** : tout public / PEGI 3, possiblement avec descripteur « contenu
  diffusé non modéré » selon les réponses sur le partage en ligne. Suivre les réponses
  réelles du questionnaire — ne pas forcer un rating.

---

## 5. Rationale des permissions (pour la fiche + review éventuel)

| Permission | Raison utilisateur |
|------------|--------------------|
| `RECORD_AUDIO` | Capter la voix pour le stream |
| Capture écran (MediaProjection) | Capter l'écran pour le stream |
| `SYSTEM_ALERT_WINDOW` | Afficher la bulle de contrôle + le chat par-dessus le jeu |
| `FOREGROUND_SERVICE` (+ mediaProjection + microphone) | Maintenir le stream actif en arrière-plan |
| `POST_NOTIFICATIONS` | Notification d'état du stream (live / reconnexion) |
| `INTERNET` | Envoyer le stream au serveur choisi |

---

## 6. App access (instructions pour le reviewer)

- **Aucun login requis.** Toutes les fonctions sont accessibles sans identifiants.
- Pour tester le stream, le reviewer doit fournir sa propre URL RTMP + clé (ou utiliser
  un serveur de test) — l'app ne fournit pas de compte.
- Indiquer dans « App access » : « All functionality is available without an account.
  Streaming requires the reviewer to enter their own RTMP/RTMPS ingest URL and stream
  key for a destination of their choice. »

---

## 7. Ce qui reste à faire (Jay)

- [ ] Enregistrer la vidéo démo FGS (script §3) → YouTube non répertorié → URL dans
      Play Console.
- [ ] Remplir Data safety (§2), FGS (§3), classification (§4), App access (§6).
- [ ] Coller l'URL privacy policy (§1).
- [ ] (Optionnel, plus tard) Migrer la politique vers GitHub Pages / theermite.com.
- [ ] Vérif device (groupée avec Phase B) : APK release stream OK + lien privacy ouvre
      bien la politique.

Phase D (assets store) et E (soumission) ensuite.
