# Architecture et frontières

## Architecture initiale 0.1.0 et extensions backend 0.2.0

Trois packages de responsabilité suffisent à ce stade ; aucun système avancé n'est représenté par une classe vide.

| Package / composant | Responsabilité réelle |
|---|---|
| `core.QuestRegistry` | Lecture JSON stricte, validation structurelle, références, DAG, registre immuable et index par événement/cible/tag |
| `core.QuestDefinition` | Définitions immuables ; objectifs, récompenses, expressions de conditions/prérequis |
| `core.QuestEngine` | Compteurs, déblocage, complétion unique, protection contre une mécanique modifiée |
| `core.Localization` | Résolution d'une carte de langues avec repli anglais et paramètres littéraux `{nom}` |
| `core.PlayerProgress` | État individuel sans dépendance à Minecraft, données initiales implicites |
| `persistence.JsonProgressStore` | JSON par UUID, snapshots, worker borné, validation, force/renommage atomique et sauvegarde précédente |
| `fabric.QuestPlusMod` | Lifecycle et conversion des vrais événements Minecraft vers `QuestEvent` |
| `fabric.QuestRuntime` | Autorité serveur, cache, dirty state, rechargement et livraison des récompenses |
| `fabric.QuestCommands` | Frontière Brigadier, permissions Minecraft et diagnostics |
| `fabric.Defaults`, `Messages` | Installation sans écrasement, messages natifs anglais/français |
| `fabric.api.QuestPlusApi` | Entrée serveur destinée aux autres mods de confiance |

Le core et la persistance ne chargent aucune classe Minecraft/Fabric. L'adaptateur et son API serveur utilisent les types réels 26.3.
Aucune classe cliente ni SavedData/NBT n'est utilisée pour les données Quest Plus.

## Traitement d'un événement

1. Le serveur confirme l'action. La casse utilise `PlayerBlockBreakEvents.AFTER` et conserve l'outil observé dans `BEFORE`.
2. La mort utilise `ServerLivingEntityEvents.AFTER_DEATH` : auteur du DamageSource, puis crédit vanilla de la victime.
   Une flèche est attribuée à son tireur via DamageSource. Pas d'inférence par proximité.
3. Le ramassage enveloppe uniquement `Inventory.add` dans `ItemEntity.playerTouch` : delta de pile avant/après, avant le rétablissement vanilla de la pile d'une entité supprimée.
4. Le contexte capture dimension, outil, santé et accroupissement. L'index sélectionne les seuls objectifs de l'événement et de la cible/tags.
5. Les prérequis sont évalués sur l'ensemble des complétions **avant** cet événement. Les quêtes qu'il déverrouille attendent le prochain.
6. Les objectifs multiples sont combinés en AND, chaque compteur est saturé à sa cible. La complétion est distincte de la réclamation.

Les UUID d'événement sont dédupliqués sur les 256 derniers événements de cet état en mémoire. Une extension doit réutiliser
le même UUID pour la même occurrence et éviter de republier un événement vanilla déjà capturé. Ce cache ne remplace pas
un journal durable de messages distribués : aucun transport réseau de progression n'est proposé dans cette tranche.

`NOT` filtre le contexte/prérequis au moment de l'événement. Une quête déjà terminée reste terminée même si cette négation devient fausse.
Le core calcule les complétions parmi les quêtes commencées ; il ne balaie pas tout le catalogue à chaque tick.

## Rechargement et évolutions

Construire et valider un catalogue candidat complet, y compris registres, tags et catégories, puis remplacer la référence courante.
Une erreur conserve le catalogue précédent. Au premier chargement invalide le backend se désactive avec diagnostic et conserve les fichiers.
Une quête supprimée conserve ses données orphelines pour permettre sa réintroduction ; l'ID d'une ancienne quête ne doit jamais être recyclé.
Les changements mécaniques sont détectés par empreinte SHA-256. Les quêtes actives incompatibles restent conservées et bloquées,
visibles par `/questplus progress <id>` ; une réinitialisation administrative explicite permet un nouveau départ sans réouvrir les récompenses.

La seule version de sauvegarde existante est 1. Aucune ancienne version fictive n'est migrée ; les versions inconnues sont refusées.
Les futurs migrateurs devront transformer une copie validée, conserver un backup et ne publier le nouveau fichier qu'après validation complète.

L'ajout d'une langue ne change pas le core : ajouter ses champs dans les JSON et son fichier natif de messages dans les ressources du mod.
La langue n'apparaît jamais dans une sauvegarde. Les messages renvoient aussi un fallback localisé côté serveur aux clients sans le mod.
Les descriptions d'objectifs sont traduisibles ; les descriptions personnalisées de récompenses/dialogues restent à ajouter au schéma suivant.

## Extensions préparées par contrat

- Équipes : propriétaire de progression distinct du bénéficiaire des récompenses ; journal de transaction entre fichiers, voir document dédié.
- Répétitions/paliers : numéros de cycle/palier stables et compteurs de cycle distincts des cumulatifs ; ne pas réutiliser le ledger `once` sans migration.
- Conditions plus riches : enrichir le contexte normalisé à partir du serveur et enregistrer les nouvelles feuilles via une API versionnée.
- Synchronisation future : projection limitée aux quêtes visibles et à l'état du joueur/équipe autorisé ; jamais envoyer fichiers de sauvegarde ou ledger complet.
- Commandes de récompense : non supportées ici. Une future extension nécessitera allowlist, arguments validés, permission bornée et aucune concaténation de texte joueur.

## Contrat PNJ/narration pour la phase 6

Ne pas scanner les chunks ou structures chaque tick. Une future définition de PNJ devra préciser ID stable, type d'entité,
dimension/biomes/tags/structure ou zone, probabilité, conditions, plafond et quêtes liées. Les instances de structure seront identifiées
par dimension + type + position de départ. À leur première observation, tirer une seule décision persistée dans `world.json`, y compris
les refus d'apparition, avant de créer une entité étiquetée avec son ID. Après crash, vérifier cette identité avant de réessayer.
Les structures déjà générées seront découvertes progressivement aux chargements de chunks, avec budget par tick ; la première observation
fera foi, sans nouvelle loterie à chaque visite. Les choix/dialogues seront des événements serveur liés à un joueur responsable.
Cette politique est une spécification, pas un spawner implémenté.

## Extensions backend présentes en version 0.2.0

- `team.TeamManager`, `team.TeamData` : équipe unique par joueur, invitations temporaires, union rétroactive,
  séparation et commandes ; snapshots avant commit puis activation après transaction durable.
- `persistence.JsonProgressStore` : schéma de joueur v2, snapshots d'équipes, journal d'opérations
  inter-fichiers et récupération à l'initialisation.
- `core.QuestEngine` : cycles répétables ou infinis, paliers, ordre séquentiel, tentatives chronométrées,
  contraintes sans mort et lecture du stock actuel dans l'inventaire.
- `core.QuestExtensions` : enregistrement d'événements, conditions et types de récompenses supplémentaires.
- `fabric.AdvancedEvents` et mixins : événements étendus et échantillonnage borné de l'exploration.
- Le schéma de quêtes v2 conserve les positions de diagramme facultatives ; il n'existe aucun rendu client.

## Validation historique (tranche 0.1.0) exécutée le 3 novembre 2026

`gradlew.bat build integrationTest` sous Java 25 : compilation et 17 tests Java réussis.
Le GameTest Quest Plus exécute réellement les hooks de casse, mort et ramassage sur un serveur dédié 26.3,
avec un joueur simulé et des dégâts de projectile crédités au joueur. Il contrôle aussi les permissions,
le rechargement invalide, les doubles réclamations et le maintien du ledger après reset/grant.
Le framework rapporte deux tests serveur : le scénario Quest Plus et son test de contrôle interne.
La tâche vérifie ensuite 140 JSON de joueurs hors ligne et le ledger du bénéficiaire après arrêt du serveur.
Le test de sauvegarde isolé vérifie qu'un UUID corrompu ne bloque pas la sauvegarde des 140 autres.

Le JAR a été inspecté : CRC valide, six quêtes embarquées, aucune classe de test ni copie de Minecraft.
La configuration Codex a été validée contre le schéma officiel et ses rôles contre la documentation courante.
La revue native a réellement utilisé Architect/Sol high, Developer/Terra medium, Tester/Luna high et Reviewer/Sol medium.

Non exécuté : session entre deux clients réels, arrêt brutal de processus, coupure électrique, panne disque physique,
fonctionnalités d'équipe/répétition/narration qui restent planifiées. Les contrôles de corruption et temporaires
sont des tests sur fichiers, pas une simulation de panne matérielle.
