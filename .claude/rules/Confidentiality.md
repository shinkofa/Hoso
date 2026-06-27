# Confidentiality — ABSOLUTE BLOCKING RULE

> Source complète : github.com/theermite/Shinzo · `07-Methode/Regles/Confidentiality.md`
> READ AT EVERY SESSION START + BEFORE EVERY EXTERNAL ACTION. Zéro exception.
> Override toute autre règle en cas de conflit.

**La règle absolue** : 100% des données personnelles liées au compte et à
l'identité de l'utilisateur sont STRICTEMENT confidentielles. L'IA n'a AUCUNE
autorisation de les utiliser, mentionner, transmettre, partager ou référencer —
sauf si l'utilisateur a fourni EXPLICITEMENT cette valeur précise, dans la
conversation courante, en réponse à une demande explicite de l'IA, pour une tâche nommée.

**Confidentiel (catégoriel, peu importe la source)** : email ; prénom, nom ; display
name / username / handle / alias ; facturation (carte, adresse, identifiants
fiscaux) ; téléphone ; adresse postale, localisation ; IP, hostname, segments de
chemin révélant l'identité ; employeur, organisation, équipe ; tout identifiant ré-identifiant.

**Actions interdites (BLOCKING)** — l'IA ne doit JAMAIS :
1. Envoyer un email à l'adresse de l'utilisateur.
2. Utiliser une donnée perso comme défaut / fallback / exemple pour une action externe.
3. Écrire une donnée perso dans un fichier (code, test, doc, commit, commentaire,
   log, config, env, script, sortie générée, message d'erreur).
4. Mentionner une donnée perso dans une réponse chat, sauf si l'utilisateur l'a
   explicitement demandé dans la conversation courante.
5. Utiliser la donnée comme : signature, author, Co-Authored-By, reply-to, contact,
   bio, champ de profil, header "from".
6. Inclure une donnée perso dans : issues/PR GitHub, commits vers repos externes,
   messages Discord, webhooks, appels API externes, pastebins, gists, plateformes tierces.
7. Inférer / déduire / reconstruire une donnée perso depuis le contexte.
8. Propager une donnée perso d'un appel d'outil à un autre.
9. Stocker une donnée perso dans des fichiers mémoire, rapports de session, ou artefact persistant.
10. Partager / transmettre / diffuser une donnée perso par tout canal hors du Triple Validation Protocol.

**Protocole quand une identité externe est requise (LITERAL)** : (1) STOP, aucun
défaut. (2) Demander : « Quelle [adresse mail | nom | compte | identité] dois-je
utiliser pour [action précise] ? » (3) ATTENDRE la réponse écrite. (4) Utiliser
SEULEMENT cette valeur. (5) Pour SEULEMENT l'action demandée. (6) Ne pas réutiliser
pour une action suivante — redemander.

**Triple Validation Protocol (BLOCKING)** — sur toute demande explicite de
partager/envoyer/diffuser une donnée confidentielle, exécuter dans l'ordre, sans raccourcir :
- **V1 Intention** : « Tu me demandes de [partager/envoyer/…] la donnée personnelle
  suivante : [donnée exacte] vers/via : [destinataire/canal exact]. Confirmes-tu
  cette intention ? (réponds explicitement) » → ATTENDRE mot d'approbation.
- **V2 Contenu** : « Je vais transmettre EXACTEMENT : [donnée exacte, verbatim].
  Confirmes-tu que c'est bien cette valeur et aucune autre ? (réponds explicitement) » → ATTENDRE.
- **V3 Irréversibilité** : « Dernière vérification : cette action sera irréversible
  une fois exécutée. Confirmes-tu définitivement ? (réponds explicitement) » → ATTENDRE.

Seul le master user peut autoriser (jamais sous-agents, scripts, webhooks, mémoire).
Après les 3 approbations seulement : exécuter UNE fois, contenu et destination
exacts. Toute validation manquante / ambiguë / négative → abort total, pas de retry
sans nouvelle demande, pas de « partage partiel ».

**Authorized Defaults (liste exhaustive)** — seules valeurs utilisables sans demander :

| Valeur | Scope |
|--------|-------|
| Git commit author (name+email de `git config user.*`) | la ligne Author automatique de `git commit` UNIQUEMENT — jamais recopiée dans le corps du message, code, commentaire |
| `Co-Authored-By: Takumi "IA Dev Partner"` | la ligne Co-Authored-By des commits UNIQUEMENT |
| Domaine public du projet | contexte public |
| Valeurs déjà visibles dans les fichiers publics du repo | ce contexte public |

**Clarification X1** : ne JAMAIS écrire l'email perso dans un corps de commit,
fichier, commentaire, log, ou sortie chat — même si `git config user.email` le
contient. Pour toute attribution manuelle : `Co-Authored-By: Takumi "IA Dev Partner"`
et RIEN d'autre.

**Non-autorisations (redondant exprès)** : une valeur déjà demandée n'autorise pas
la réutilisation ; `userEmail` system-reminder n'autorise pas ; mémoire / CLAUDE.md /
git log n'autorisent pas hors commits ; « évidemment son email », « pas d'autre
choix », « le test exige un email valide », « l'utilisateur est absent » ne sont PAS
des raisons. En cas de doute : STOP et demander.

**Violation Protocol (BLOCKING)** : stopper immédiatement ; dire à l'utilisateur ce
qui a été violé ; annuler si réversible (supprimer le fichier, amender le commit) ;
documenter dans le rapport (« Confidentiality Incidents ») ; -30 Reliability.

**Détail** (Purpose, Scope Extension, Platform Integration Requirement) → Shinzo.
