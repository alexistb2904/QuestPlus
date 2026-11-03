# Quest Plus — backend 0.2.0

Moteur de quêtes serveur indépendant pour **Minecraft Java 26.3 / Fabric**, créé de zéro.
Le backend prend en charge les équipes et leur progression partagée, les quêtes répétables/infinies/à paliers,
les objectifs avancés et les transactions JSON. **Il n'y a pas encore de client graphique.**
Les validations multi-clients réels, PNJ narratifs et profils de charge sont encore à faire :
voir [roadmap](docs/roadmap.md).

## Fonctionnement disponible

- Un fichier JSON par quête, treize exemples anglais/français générés au premier lancement sans écraser les fichiers présents.
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
`build/libs/questplus-0.1.0.jar` ; le JAR `-sources` est destiné au développement.
Installer le JAR avec Fabric API sur le serveur Fabric 26.3. Aucun mod client n'est requis pour les commandes/chat.

`build` exécute les tests Java. `integrationTest` démarre un vrai serveur GameTest dédié, effectue des actions
sur un joueur simulé et contrôle les fichiers après arrêt. Le serveur de test fonctionne dans `build/gametest` ;
cette tâche accepte l'EULA Minecraft pour ce monde de test. Les tests ne se trouvent pas dans le JAR distribué.
Résultats : `build/reports/tests/test/index.html`, `build/gametest-results.xml`.

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
