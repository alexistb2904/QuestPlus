# Quest Plus — backend 0.2.0

Moteur de quêtes serveur indépendant pour **Minecraft Java 26.3 / Fabric**, créé de zéro.
Le backend prend en charge les équipes et leur progression partagée, les quêtes répétables/infinies/à paliers,
les objectifs avancés et les transactions JSON. **Il n'y a pas encore de client graphique.**
Les validations multi-clients réels, PNJ narratifs et profils de charge sont encore à faire :
voir [roadmap](docs/roadmap.md).

## Fonctionnement disponible

- Un fichier JSON par quête : **209 nouveaux défis Vanilla+** (dont 36 défis d'équipement) et 13 exemples historiques conservés pour compatibilité, installés au premier lancement sans écraser les fichiers existants.
- Casse réelle de blocs, kills crédités au joueur (dont dégâts de projectiles), ramassage exact d'objets, tags vanilla/moddés.
- Objectifs multiples, compteurs saturés, prérequis en graphe sans cycle et expressions `and` / `or` / `not`.
- Conditions au moment de l'événement : dimension, outil, accroupissement, santé (`2` = un cœur).
- Quêtes à complétion unique, récompenses multiples en objets et points d'expérience, réclamation manuelle.
- Sauvegardes JSON compactes par UUID, cache paresseux, écritures regroupées et atomiques, sauvegarde précédente `.bak`.
- Rechargement validé avant application, conservation des historiques et rejet des sauvegardes corrompues.
- API pour que des mods serveur enregistrent des événements/conditions/récompenses et soumettent les événements normalisés.
- Équipes : création, invitation avec expiration, acceptation/refus, départ, expulsion, transfert et dissolution ; union rétroactive et instantanés de séparation.
- Quêtes répétables/infinies/à paliers, objectifs séquentiels, délais et contraintes sans mort.
- Quêtes informatives sans objectif de gameplay, avec acquittement manuel ou lecture seule (la modale graphique arrivera avec le client).
- Événements étendus : fabrication, cuisson, pêche, placement, interaction, apprivoisement, élevage, échanges, biome, dimension, structure et déplacement.
- Les invitations reçues peuvent être listées avec `/questplus team invitations`.

Les événements naturels ignorent les joueurs créatifs et spectateurs. « Obtenir » signifie ici **ramasser une entité objet** ;
ce n'est pas un état d'inventaire. Déplacer un objet depuis un coffre, crafter, recevoir une commande `/give`
ou une récompense Quest Plus ne compte pas. Jeter puis ramasser un objet reste un nouveau ramassage :
une condition de possession ou un objectif de collecte unique nécessitera une mécanique distincte.

## Compiler et vérifier

Versions épinglées : Java 25, Gradle 9.7.1, Loom 1.18.4, Fabric Loader 0.20.1, Fabric API 0.165.2+26.3.
Minecraft 26.3 est distribué sans obfuscation : pas de mappings Yarn ni de remappage inventé.

Sous PowerShell, avec votre chemin réel vers le JDK 25 :

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.5.0.3'
.\gradlew.bat build
.\gradlew.bat integrationTest
```

Sous Linux/macOS avec Java 25 : `./gradlew build integrationTest`.
Le premier lancement télécharge les outils et dépendances. Le JAR installable est
`build/libs/questplus-0.2.0.jar` ; le JAR `-sources` est destiné au développement.
Installer le JAR avec Fabric API sur le serveur Fabric 26.3. Aucun mod client n'est requis pour les commandes/chat.

`build` exécute les tests Java. `integrationTest` démarre un vrai serveur GameTest dédié, effectue des actions
sur un joueur simulé et contrôle les fichiers après arrêt. Le serveur de test fonctionne dans `build/gametest` ;
cette tâche accepte l'EULA Minecraft pour ce monde de test. Les tests ne se trouvent pas dans le JAR distribué.
Résultats : `build/reports/tests/test/index.html`, `build/gametest-results.xml`.

## Tester l'animation Blockbench de la tablette (PR #2)

**La version native est pour le moment sur `fix/native-first-person-tablet` ; elle n'est pas encore fusionnee
dans `main`.** Pour verifier les animations avec `1-Lancer-Client.bat`, faites :

```powershell
git fetch origin
git switch fix/native-first-person-tablet
git pull origin fix/native-first-person-tablet
```

Cette branche installe FP PAL 1.1.1 et Player Animation Library 1.2.8 dans le
client de developpement via **`implementation`** (Fabric Loom non-remapping, Minecraft 26.3). Aucun export Blockbench
supplementaire n'est necessaire : la branche contient les animations `open`,
`idle` et `close` de `questplus_first_person.bbmodel` et le modele
`fp_models/questplus_first_person.geo.json` avec sa texture.

Pour diagnostiquer le chargement, dans le chat du jeu :

```text
/fp_anim list
/fp_anim play questplus:questplus.open
```

La premiere commande doit lister les animations `questplus:questplus.open`,
`questplus:questplus.idle` et `questplus:questplus.close`.
La seconde doit jouer **l'animation des vrais bras vanilla**, meme sans appuyer sur J.
Si `/fp_anim` est inconnue, verifier le chargement des mods FP PAL et PAL.
Si l'animation est absente, consulter `run/client/logs/latest.log` et
les lignes `FP animation` / `Quest Plus has fallen back`.
Ce test isole le probleme de chargement du rendu de la tablette.

La tablette est maintenant rendue automatiquement par FP PAL pendant le mouvement,
en meme temps que les bras. L'interface des quetes utilise encore une couche 2D
a aligner sur la surface visible de la tablette ; le rendu GPU de l'ecran sur
la surface 3D et la validation visuelle FOV/GUI Scale ne sont **pas encore termines**.

## Quest Studio — éditeur web visuel des quêtes

Quest Studio embarque le **même catalogue par défaut** que le serveur (209 défis Vanilla+ et 13 quêtes historiques). Les données d'origine viennent de `web/editor/src/data/challenge-catalogue.json` et `equipment-catalogue.json`, embarquées dans le JAR via Gradle. Un monde déjà initialisé n'est jamais écrasé : importer volontairement les nouvelles définitions avec le ZIP exporté par Quest Studio. Les projets locaux déjà édités restent préservés ; choisir **Réinitialiser > Défauts** pour charger les nouvelles quêtes dans Studio.

Quest Studio est disponible sous [`web/editor/`](web/editor/README.md). Sous Windows, choisir
**7. Ouvrir Quest Studio** dans `0-Menu-Tests.bat` ou double-cliquer sur `web/editor/start.bat`.
Il ouvre l'éditeur à `http://localhost:5173/web/editor/` et ne nécessite ni compte ni serveur distant.

L'éditeur permet de créer et modifier les catégories, quêtes, objectifs, récompenses et annotations,
de positionner les quêtes en glisser-déposer sur leur arbre, et d'importer/exporter un pack ZIP.
La `description` des quêtes est désormais facultative : les objectifs sont formulés
automatiquement dans la langue du joueur, sans les répéter dans les détails.
L'export produit les définitions JSON du serveur : le remplacer manuellement après sauvegarde,
puis lancer `/questplus reload`. Les sauvegardes joueurs ne sont jamais incluses.

## Lancer le mod depuis Windows (.bat)

Les raccourcis suivants se trouvent **a la racine du depot**. Lancez `0-Menu-Tests.bat` pour choisir une operation,
ou double-cliquez directement sur le fichier voulu. Ils utilisent le wrapper Gradle inclus (pas d'installation Gradle requise)
et les **sources actuelles** : aucun JAR a copier dans `mods/`.

| Fichier | Fonction |
| --- | --- |
| `1-Lancer-Client.bat` | Lance Minecraft 26.3 + Fabric API + Quest Plus en mode developpement (`runClient`). |
| `2-Lancer-Serveur.bat` | Lance un serveur Fabric local de test avec Quest Plus (`runServer`). |
| `3-Compiler-Mod.bat` | Compile et execute les tests Gradle (`build`). |
| `4-Tests-Serveur.bat` | Lance `build integrationTest`, y compris les GameTests et controles des JSON de sauvegarde. |
| `5-Tests-Client.bat` | Lance les Client GameTests configures par Fabric (`runClientGameTest`). |
| `6-Installer-Java-25.bat` | Installe Eclipse Temurin JDK 25 via winget, **uniquement apres confirmation**. |

**Prerequis :** Windows, JDK 25 et connexion Internet au premier demarrage pour
telecharger les dependances Minecraft/Fabric/Gradle. Si Java 17 reste le Java par defaut
du PC, `scripts/Find-Java25.ps1` recherche automatiquement une installation Java 25
dans `JAVA_HOME`, `PATH`, les dossiers habituels des fournisseurs et les JDK d'IDE.
Le lanceur force `JAVA_HOME` uniquement dans son processus ; il ne change pas la
configuration globale de Windows ni celle de tes autres jeux/outils.

Si le JDK 25 n'est pas trouve, choisir **6** dans le menu, confirmer l'installation,
puis choisir **1**. Installation manuelle alternative :
`winget install -e --id EclipseAdoptium.Temurin.25.JDK`.
Pour une installation personnalisee, definir la variable d'environnement
`QUESTPLUS_JAVA_HOME` vers le **repertoire racine du JDK 25**
(contenant `bin/java.exe` et `bin/javac.exe`), avant de lancer le script.

Les scripts affichent le resultat et restent ouverts apres la fermeture du jeu
ou la fin des tests.

**Dossiers isoles :** le client utilise `run/client/` (mondes dans `run/client/saves/`),
le serveur `run/server/` (monde et `questplus/` dans ce repertoire), les GameTests serveur
`build/gametest/`. Ces repertoires sont ignores par Git.

**Serveur local :** `2-Lancer-Serveur.bat` demande votre accord explicite avec
[l'EULA Minecraft](https://aka.ms/MinecraftEULA). Au premier lancement, il cree seulement
`run/server/server.properties` avec `server-ip=127.0.0.1` et `online-mode=false`
pour que le client de developpement puisse se connecter **en local uniquement**. Ne publiez
pas cette configuration sur Internet. Le port par defaut est `25565` ;
depuis le client, ouvrez *Multijoueur > Connexion directe > localhost:25565*.
Les scripts ne modifient plus les proprietes serveur si le fichier existe deja.

**Note :** le backend peut aussi etre utilise sans UI graphique. Les fichiers JSON de
quetes du serveur sont crees dans `run/server/world/questplus/` (nom de monde vanilla par defaut).
Pour tester le partage des quetes avec deux utilisateurs reels, utilisez des sessions/client
et identites distinctes. Les Client GameTests ne remplacent pas ce test multijoueur manuel.

## Fichiers du monde

```text
world/
├── questplus/
│   ├── config.json
│   ├── quests/{survival,mining,combat,exploration}/*.json
│   ├── categories/*.json
│   └── npcs/                     # répertoire pour la suite, sans apparition active
└── data/questplus/
    ├── players/<uuid>.json        # progression personnelle et droits aux récompenses
    ├── teams/<uuid>.json          # progression partagée et membres
    └── transactions/pending.json  # uniquement pendant une transaction multi-fichiers
```

`config.json` contient `schemaVersion: 1` et `autosaveSeconds: 5` (1 à 300 secondes).
Les définitions et les sauvegardes sont indépendantes. Une quête absente d'une sauvegarde commence à zéro.
La présence de `config.json` marque l'initialisation du monde : les quêtes supprimées ne sont pas régénérées aux démarrages suivants.
Le dossier `teams/` et `world.json` seront introduits avec leurs données utiles en phases équipes/narration.
Schéma et exemples : [format JSON](docs/json-schema.md), [JSON Schema](docs/quest.schema.json).

## Commandes

| Commande | Accès / effet |
|---|---|
| `/questplus` | Aide |
| `/questplus progress [quest-id]` | État personnel, liste limitée à 50 entrées sans ID |
| `/questplus claim <quest-id>` | Réclamer ses récompenses après complétion |
| `/questplus reload` | Permission Minecraft `COMMANDS_GAMEMASTER` (niveau opérateur 2 standard) |
| `/questplus debug [uuid]` | Même permission ; version, quêtes ou historique personnel/réservations |
| `/questplus quest grant <uuid> <quest-id>` | Même permission ; complétion administrative, sans attribution automatique |
| `/questplus quest reset <uuid> <quest-id>` | Même permission ; réinitialise les compteurs, conserve les réclamations |

Les IDs complets, par exemple `questplus:survival/logs`, sont suggérés. `grant`, `reset` et `debug <uuid>`
fonctionnent aussi sur les sauvegardes de joueurs déconnectés. La console peut administrer ; `progress` et `claim` requièrent un joueur.
Les commandes `/questplus team create/invite/invitations/join/decline/leave/kick/transfer/disband/info`
sont disponibles. Les invitations sont adressées à des UUID, expirent après cinq minutes et
ne sont visibles que pour le destinataire via `/questplus team invitations`.
Les fusions rétroactives ne réattribuent pas automatiquement les récompenses historiques.
Lancer un test à deux vrais clients reste une étape de validation à effectuer.

## Sauvegardes et récompenses

Toutes les mutations se produisent sur le thread serveur. Un worker unique écrit des instantanés détachés,
sans écrire pour chaque événement. Le chargement initial d'un UUID est synchrone et plafonné à 8 Mio ;
les UUID visités restent en cache pour la session. À l'arrêt, tous les états en cache sont sauvegardés et le worker est drainé.

Une récompense est d'abord marquée `reserved` et sauvegardée durablement ; la livraison revient ensuite sur le thread serveur,
puis passe à `delivered`. Réclamer deux fois, réinitialiser la quête ou recharger les JSON ne supprime pas ce registre.
Les récompenses en cours utilisent la définition capturée au début de la réclamation.

**Limite transactionnelle :** les fichiers Quest Plus et l'inventaire vanilla ne peuvent pas être validés atomiquement.
Un crash, une déconnexion ou un changement d'inventaire après réservation peut laisser une récompense réservée sans livraison complète.
Elle n'est jamais rejouée automatiquement. Examiner `/questplus debug <uuid>`, le JSON, les logs et l'inventaire avant réparation
manuelle serveur arrêté ; ne pas supprimer une réservation sans établir ce qui a déjà été livré.
La coupure électrique et les caches du système de fichiers restent hors de la garantie d'un simple renommage atomique.

Un fichier illisible, incomplet ou d'une version inconnue bloque seulement les données de cet UUID et n'est jamais remplacé par un état vide.
Pour récupérer : arrêter le serveur, copier tous les fichiers concernés hors du monde, examiner le `.bak`, restaurer une version valide,
puis retirer les fichiers temporaires après examen. Les `.tmp` ne sont jamais promus automatiquement.
Restaurer une ancienne sauvegarde sans son historique de récompenses peut recréer des droits déjà consommés.

## Codex et suite

Les quatre rôles natifs sont dans [.codex/agents](.codex/agents/), avec les modèles et efforts demandés.
La configuration limite les sous-agents simultanés à deux. Les décisions et limites sont dans
[AGENTS.md](AGENTS.md), [architecture](docs/architecture.md) et [progression d'équipe](docs/team-progression.md).
Rouvrir une session Codex pour découvrir les nouveaux agents/configurations du projet ; ils ne reconfigurent pas rétroactivement cette conversation.
Cette exécution a utilisé les capacités natives disponibles avec des modèles/efforts explicites.

La syntaxe a été vérifiée avec le schéma officiel Codex et la [documentation des sous-agents](https://learn.chatgpt.com/docs/agent-configuration/subagents).
Les versions Fabric ont été vérifiées sur les [métadonnées officielles](https://meta.fabricmc.net/v2/versions/loader/26.3)
et le [dépôt Maven Fabric](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/).


## Three-part architecture (experimental branch)

This branch is now organized into **web**, **client** and **server**:

- `web/editor/`: React Quest Studio, browser draft and ZIP tools.
- `web/server/`: integrated same-origin web hosting, HTTP API, sharing
  and operator-approved publishing; **not a fourth standalone Hub**.
- `client/`: Minecraft client rendering, tablet UX and client resources.
- `server/`: Java/Fabric quest engine, commands, validation and persistence.

Gradle produces **one universal Fabric JAR** from the client/server trees.
The React UI and web API run as **one web service** in production. Player
progress remains on the Minecraft server and never enters the web service.
See [architecture](docs/architecture.md) and
[web/server/README.md](web/server/README.md) for setup and security.
This work remains in PR #26 until build and real Minecraft tests pass.
