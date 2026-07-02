# Hōsō — Fiche Play Store (Phase D assets)

> Doc de prép Takumi. Descriptions à coller dans Play Console + specs des visuels.
> Session 2026-06-14. App v1.0.0. `com.theermite.hoso`.

---

## 1. Descriptions (à coller dans Play Console)

### Courte (max 80 caractères)

| Langue | Texte                                                                | Car. |
| ------ | -------------------------------------------------------------------- | ---- |
| FR     | Diffuse ton écran et ta voix en direct vers Twitch, YouTube ou Kick. | 67   |
| EN     | Stream your screen and voice live to Twitch, YouTube or Kick.        | 60   |
| ES     | Transmite tu pantalla y tu voz en directo a Twitch, YouTube o Kick.  | 66   |

### Longue (max 4000 caractères)

#### FR

```
Hōsō transforme ton téléphone en studio de streaming. Diffuse ton écran de jeu, ta voix et le son du jeu en direct vers Twitch, YouTube, Kick, ou n'importe quel serveur RTMP de ton choix — directement depuis ton mobile, sans PC.

CE QUE HŌSŌ FAIT
• Capture ton écran et le diffuse en direct
• Mixe ta voix (micro) et le son du jeu, avec deux volumes indépendants
• Bulle de contrôle flottante : démarre, arrête, coupe le micro, passe en pause sans quitter ton jeu
• Anneau rouge sur la bulle quand tu es en direct — tu sais toujours si ça tourne
• Bulle de chat Twitch par-dessus le jeu, pour lire ton audience en temps réel
• Plusieurs destinations enregistrées (Twitch, YouTube, Kick, RTMP perso)
• Réglages simples : résolution, bitrate, images par seconde

RESPECTUEUSE DE TA VIE PRIVÉE
• Aucune collecte de données. Aucun analytics. Aucune pub. Aucun compte.
• Tes réglages, dont ta clé de stream, restent sur ton appareil.
• Le seul trafic réseau va vers le serveur que TU choisis.

LIBRE ET OUVERTE
Hōsō est open source (licence Apache 2.0). Le code est entièrement visible.

Hōsō est une app perso, faite pour streamer simplement depuis un mobile. Pas de superflu, pas de friction.
```

#### EN

```
Hōsō turns your phone into a streaming studio. Broadcast your game screen, your voice and the game sound live to Twitch, YouTube, Kick, or any RTMP server you choose — straight from your phone, no PC needed.

WHAT HŌSŌ DOES
• Captures your screen and streams it live
• Mixes your voice (mic) and game sound, with two independent volumes
• Floating control bubble: start, stop, mute, pause without leaving your game
• Red ring on the bubble when you're live — you always know if it's running
• Twitch chat bubble over your game, to read your audience in real time
• Multiple saved destinations (Twitch, YouTube, Kick, custom RTMP)
• Simple settings: resolution, bitrate, frame rate

PRIVACY-RESPECTING
• No data collection. No analytics. No ads. No account.
• Your settings, including your stream key, stay on your device.
• The only network traffic goes to the server YOU choose.

FREE AND OPEN
Hōsō is open source (Apache 2.0 license). The code is fully visible.

Hōsō is a personal app, built to stream simply from a phone. No bloat, no friction.
```

#### ES

```
Hōsō convierte tu teléfono en un estudio de streaming. Transmite la pantalla de tu juego, tu voz y el sonido del juego en directo a Twitch, YouTube, Kick o cualquier servidor RTMP que elijas — directamente desde tu móvil, sin PC.

LO QUE HACE HŌSŌ
• Captura tu pantalla y la transmite en directo
• Mezcla tu voz (micrófono) y el sonido del juego, con dos volúmenes independientes
• Burbuja de control flotante: inicia, detén, silencia, pausa sin salir del juego
• Anillo rojo en la burbuja cuando estás en directo — siempre sabes si está activo
• Burbuja de chat de Twitch sobre el juego, para leer a tu audiencia en tiempo real
• Varios destinos guardados (Twitch, YouTube, Kick, RTMP propio)
• Ajustes simples: resolución, bitrate, fotogramas por segundo

RESPETA TU PRIVACIDAD
• Sin recopilación de datos. Sin analíticas. Sin anuncios. Sin cuenta.
• Tus ajustes, incluida tu clave de transmisión, permanecen en tu dispositivo.
• El único tráfico de red va al servidor que TÚ eliges.

LIBRE Y ABIERTA
Hōsō es de código abierto (licencia Apache 2.0). El código es totalmente visible.

Hōsō es una app personal, hecha para transmitir de forma sencilla desde un móvil. Sin relleno, sin fricción.
```

---

## 2. Visuels — specs et état

| Asset | Spec Play Store | État | Qui |
|-------|-----------------|------|-----|
| **Icône haute résolution** | 512×512 px, PNG 32 bits, < 1 Mo, carré plein | ✅ **brouillon généré** : `docs/store/icon-512.png` (downscale Lanczos du `Logo-Hoso-fnl.png`, fond navy #0F1729). 55 Ko. Remplace si tu veux. | Takumi (à valider Jay) |
| **Feature graphic** | 1024×500 px, PNG ou JPG, pas de transparence | ❌ à créer. Suggestion : logo Hōsō + tagline « Stream mobile → Twitch » sur dégradé navy. Base possible : `docs/screenshots/hero.svg`. | **Jay** |
| **Captures téléphone** | 2 à 8, ratio 9:16, 1080×1920 conseillé, PNG/JPG | ❌ vraies captures device préférées. Mockups SVG dispo (`docs/screenshots/screen-*.svg`) en secours, à exporter en PNG. | **Jay** |

### Captures suggérées (ordre du storytelling)

1. Écran principal — réglages destination + stream (mockup `screen-main.svg`)
2. Bulle overlay repliée avec **anneau rouge live** (`screen-overlay-collapsed.svg`)
3. Bulle overlay dépliée — HUD live (`screen-overlay-expanded.svg`)
4. Bulle de chat Twitch par-dessus le jeu (`screen-chat.svg`)
5. Réglage du mix audio micro + jeu (`screen-audio-mix.svg`)
6. (option) Configuration du stream (`screen-stream-config.svg`)

Pourquoi cet ordre : on montre d'abord la simplicité (1), puis la valeur unique —
contrôle sans quitter le jeu (2-3), puis l'engagement (4) et la qualité audio (5).

---

## 3. Autres champs Play Console

- **Catégorie** : Outils (ou Vidéo & montage). À trancher selon le rangement Play.
- **Tags** : streaming, RTMP, Twitch, screen recorder.
- **Email contact dev** : à fournir par Jay (Confidentiality — Takumi ne le renseigne pas).
- **Site web** (optionnel) : repo GitHub ou theermite.com.

---

## 4. Reste à faire (Jay)

- [ ] Valider ou remplacer `docs/store/icon-512.png`.
- [ ] Créer le feature graphic 1024×500.
- [ ] Produire 4-6 captures (device réel ou export des mockups SVG).
- [ ] Coller les descriptions FR/EN/ES dans Play Console.
- [ ] Renseigner catégorie + email contact.

## Note ménage (5S — Seiri)

`docs/screenshots/png$1.png` et `png$name.png` (1800×400) ressemblent à des artefacts
d'export ratés (noms cassés). À supprimer si confirmé — je ne touche pas sans ton OK.

Phase E (soumission Play Console) ensuite.
```


---

## MAJ 2026-06-14 — Visuels générés et à jour

Tous les visuels sont produits et reflètent l'app actuelle (Streamer.bot retiré).

| Asset | Fichier repo | Spec | État |
|-------|-------------|------|------|
| Icône 512 | `docs/store/icon-512.png` | 512×512 PNG | ✅ |
| Feature graphic | `docs/store/feature-graphic.png` | 1024×500, logo + tagline + 2 téléphones | ✅ |
| Captures (6) | `docs/store/screenshots/01..06.png` | 1205×2143 (9:16), HD | ✅ |

Captures, dans l'ordre : 01 accueil · 02 overlay en jeu · 03 contrôles · 04 chat ·
05 mix audio · 06 stream. Rendues depuis les SVG sources (corrigés) via cairosvg.

**Corrections appliquées aux mockups** : retrait des cartes/pastilles Streamer.bot,
caret déroulant `▾`→`▼` (tofu cairosvg), retrait du kanji 放送 du hero (pas de police
CJK). Commit `b3dbd23`.

**Note README** : le README GitHub utilise encore les PNG périmés
(`docs/screenshots/png/*.png`) qui montrent Streamer.bot. Les SVG sources sont
maintenant corrigés → on peut régénérer ces PNG pour aligner le README. À faire si Jay
valide (sujet séparé du Play Store).

**Reste Jay** : valider les visuels, coller les descriptions, fournir l'email contact.
Les vraies captures device restent une option d'amélioration future (les mockups sont
fidèles à l'app actuelle).
