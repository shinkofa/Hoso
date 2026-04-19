# Guide post-install — Desactiver le battery killing

> OBLIGATOIRE pour que Hoso reste actif pendant le stream.
> Sans ces reglages, Android tuera le service apres quelques minutes.

---

## Oppo CPH2173 (Android 14, ColorOS)

### Etape 1 — Autostart
1. Ouvrir **Parametres** > **Gestion des applications** > **Liste des applications**
2. Trouver **Hoso**
3. Activer **Autoriser le demarrage automatique**

### Etape 2 — Batterie
1. **Parametres** > **Batterie** > **Plus de parametres batterie**
2. Desactiver **Optimisation intelligente de la batterie** pour Hoso
3. OU : **Parametres** > **Gestion des applications** > **Hoso** > **Utilisation de la batterie** > **Sans restriction**

### Etape 3 — Verrouiller dans les recents
1. Ouvrir Hoso
2. Appuyer sur le bouton **recents** (carre)
3. Appuyer sur le cadenas (ou tirer vers le bas) sur la carte Hoso

### Etape 4 — Mode jeu (si disponible)
1. **Parametres** > **Fonctionnalites speciales** > **Barre de jeu**
2. Ajouter Hoso comme app gaming → ColorOS la traite en priorite

---

## Xiaomi Redmi Note 14 5G (Android 15, HyperOS)

### Etape 1 — Autostart
1. **Parametres** > **Applications** > **Autostart**
2. Activer **Hoso**

### Etape 2 — Batterie
1. **Parametres** > **Applications** > **Hoso** > **Economie de batterie**
2. Selectionner **Aucune restriction**

### Etape 3 — Verrouiller dans les recents
1. Ouvrir Hoso, afficher les recents
2. Glisser la carte Hoso vers le BAS (pas le haut !) pour verrouiller
3. Un cadenas apparait

### Etape 4 — Apps protegees
1. **Parametres** > **Batterie** > **Economiseur de batterie**
2. Aller dans **Applications protegees**
3. Activer **Hoso**

### Etape 5 — Desactiver MIUI/HyperOS optimizations
1. **Parametres** > **Parametres supplementaires** > **Options pour les developpeurs**
2. Desactiver **Optimisation MIUI** (redemarrage requis)
3. (Si non visible : activer les options developpeurs via "A propos" > taper 7x sur numero de build)

---

## Verification

Apres configuration, lancer un stream de test pendant **10 minutes** :
1. Lancer Hoso → START
2. Changer d'app (ouvrir Dofus Touch par exemple)
3. Revenir sur Hoso apres 10 min
4. Le stream doit toujours etre actif (notification visible, statut "EN DIRECT")

Si le stream a ete coupe : verifier les etapes ci-dessus. Le verrouillage dans les recents est souvent oublie.

---

**Source** : [dontkillmyapp.com](https://dontkillmyapp.com/) — reference des comportements battery killing par constructeur.
