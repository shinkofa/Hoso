<div align="center">
<img src="https://raw.githubusercontent.com/shinkofa/Hoso/main/Logo-Hoso-fnl.png" width="90" alt="Hōsō"/>

## Hōsō · 放送 — Streame ton jeu mobile vers Twitch

**Sans ordinateur. Sans abonnement. Directement depuis ton téléphone Android.**

> 🛒 **Play Store :** bientôt disponible — en attendant, installation directe en 4 étapes ↓

</div>

---

## 📥 Comment installer

Pas besoin de passer par le Play Store. L'app se télécharge comme un fichier — ça prend 4 minutes.

<div align="center">
<img src="https://raw.githubusercontent.com/shinkofa/Hoso/main/docs/screenshots/png/install-flow.png" width="100%" alt="Guide d'installation en 4 étapes"/>
</div>

### Étape 1 — Télécharge l'APK

Clique sur le fichier **`hoso-release.apk`** dans la section **Assets** en bas de cette page.

### Étape 2 — Autorise l'installation

Android affiche un avertissement pour toute app installée hors Play Store. **C'est normal et attendu** — ce n'est pas une alerte de virus, juste une sécurité par défaut. Tu autorises une seule fois, pour l'app que tu utilises pour ouvrir le fichier (ton navigateur ou ton gestionnaire de fichiers).

Le plus souvent, le téléphone te propose directement le bon bouton **« Autoriser »** au moment où tu ouvres l'APK. Sinon :

> **Samsung** : le popup d'autorisation apparaît automatiquement → **Autoriser**  
> **Xiaomi / Redmi** : **Paramètres → Applications → Autorisations spéciales → Installer des apps inconnues** → choisis ton navigateur → **Autoriser**  
> **Autres marques** : **Paramètres → Sécurité → Sources inconnues** (ou « Installer des apps inconnues »)

### Étape 3 — Installe et ouvre

Ouvre le fichier `hoso-release.apk` téléchargé → **Installer** → **Ouvrir**.

### Étape 4 — Désactive l'optimisation batterie ⚠️

**Cette étape est importante.** Sans elle, Android peut couper le stream après quelques minutes.

**Paramètres → Batterie → Optimisation de la batterie** → cherche « Hōsō » → sélectionne **Ne pas optimiser**.

→ [Guide détaillé par marque (Samsung, Xiaomi, Oppo…)](https://github.com/shinkofa/Hoso/blob/main/docs/Battery-Optimization-Guide.md)

---

## 📱 Aperçu de l'application

<table align="center">
  <tr>
    <td align="center">
      <img src="https://raw.githubusercontent.com/shinkofa/Hoso/main/docs/screenshots/png/screen-main.png" width="180" alt="Écran principal"/>
      <br/><sub><b>Écran principal</b><br/>Destinations, stream, audio, Streamer.bot</sub>
    </td>
    <td align="center">
      <img src="https://raw.githubusercontent.com/shinkofa/Hoso/main/docs/screenshots/png/screen-stream-config.png" width="180" alt="Réglages stream"/>
      <br/><sub><b>Réglages stream</b><br/>Résolution, bitrate, clé de stream</sub>
    </td>
    <td align="center">
      <img src="https://raw.githubusercontent.com/shinkofa/Hoso/main/docs/screenshots/png/screen-audio-mix.png" width="180" alt="Mode Mix audio"/>
      <br/><sub><b>Mode Mix audio</b><br/>Micro + son du jeu, balance indépendante</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="https://raw.githubusercontent.com/shinkofa/Hoso/main/docs/screenshots/png/screen-overlay-collapsed.png" width="180" alt="Bulle overlay"/>
      <br/><sub><b>En jeu — discret</b><br/>Bulle draggable, toujours là</sub>
    </td>
    <td align="center">
      <img src="https://raw.githubusercontent.com/shinkofa/Hoso/main/docs/screenshots/png/screen-overlay-expanded.png" width="180" alt="Contrôles live"/>
      <br/><sub><b>Contrôles live</b><br/>HUD + actions en un tap</sub>
    </td>
    <td align="center">
      <img src="https://raw.githubusercontent.com/shinkofa/Hoso/main/docs/screenshots/png/screen-chat.png" width="180" alt="Chat Twitch"/>
      <br/><sub><b>Chat Twitch</b><br/>Flottant, redimensionnable</sub>
    </td>
  </tr>
</table>

---

## ✨ Ce que fait Hōsō

**🎯 Multi-destinations** — Twitch, YouTube Live, Kick, ou n'importe quelle URL RTMP. Crée plusieurs profils, change de plateforme en un tap.

**📡 Stream robuste** — Résolution jusqu'à 1080p, bitrate de 1 000 à 8 000 kbps. Si ton réseau coupe (4G/5G instable), Hōsō se reconnecte automatiquement — tu n'as rien à faire.

**🎙 Audio Micro ou Mix** — Streame avec juste ta voix, ou avec ta voix ET le son du jeu en simultané. Deux curseurs de volume indépendants pour la balance parfaite.

**🫧 Overlay discret pendant le jeu** — Une petite bulle reste sur ton écran. Mute le micro, pause le stream, affiche le chat Twitch — sans jamais quitter le jeu.

**💬 Chat Twitch flottant** — Lis les messages de tes viewers en direct, repositionnable n'importe où, 3 tailles disponibles.

**🤖 Streamer.bot** — Déclenche tes alertes et automatisations depuis la bulle overlay, sans toucher au PC.

---

## ❓ Questions fréquentes

**C'est gratuit ?**  
Oui, complètement. Hōsō est open source sous licence Apache 2.0 — le code source est entièrement visible sur ce dépôt.

**Pourquoi ce n'est pas sur le Play Store ?**  
Le Play Store impose des restrictions strictes sur la capture d'écran. **Le Play Store arrive bientôt.** En attendant, l'installation APK directe fonctionne parfaitement et prend 4 minutes.

**L'APK est sûr ?**  
Le code source complet est visible ici. Hōsō ne collecte aucune donnée, ne crée aucun compte, et ne contacte aucun serveur externe — uniquement ta destination de stream que tu configures toi-même.

**Ça marche sur mon téléphone ?**  
Android 10 et supérieur (la grande majorité des téléphones depuis 2019). Testé sur Samsung, Xiaomi, OnePlus et Pixel.

**Le stream s'arrête si je change d'app ou reçois un appel ?**  
Non. Hōsō tourne en arrière-plan via un service natif Android. En cas de coupure réseau, l'auto-reconnexion tente jusqu'à 20 fois sur ~8 minutes.

---

*Hōsō (放送) — « diffusion » en japonais · [Code source](https://github.com/shinkofa/Hoso) · [Licence Apache 2.0](https://github.com/shinkofa/Hoso/blob/main/LICENSE)*
