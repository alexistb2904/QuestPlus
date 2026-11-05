# Quest Plus face à FTB Quests — 5 novembre 2026

Comparaison fonctionnelle avec la documentation officielle FTB 26.1.x, et sa documentation SNBT 1.21.1. Aucune instance FTB n’a été exécutée ; aucune compatibilité binaire FTB/Fabric 26.3 n’est revendiquée. **Il reste des fonctionnalités manquantes.**

## Fonctions intégrées dans cette continuation

| Fonction FTB | Quest Plus | Validation actuelle |
| --- | --- | --- |
| [Observation et stages](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Quests/Types/) | `observe_block` / `observe_entity`, regard continu avec raycast serveur à six blocs, tags et reset en changeant de cible ; objectif/condition/récompense stage avec fournisseur Java remplaçable et tags vanilla persistants par défaut | Core, formulaires Studio, progression tablette ; GameTest regard réel et stage après reconnexion |
| Choix / random / loot / all | Cartes, plusieurs choix immuables, droits personnels d’équipe, résultats concrets persistés avant récupération, probabilités et simulation Studio | Tests unitaires, GameTests équipes/reconnexion/rejeu, client natif réel |
| Toast personnalisé | Titre/description traduisibles, icône, envoi serveur au client et file limitée ; stage/toast aussi autorisés dans les pools validés | Formulaires et aperçus filtrés ; test client de réception du toast et attribution du stage |
| [Prérequis et présentation](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Quests/Settings/) | Quête commencée, quorum de dépendances, sous-titres, tags, formes, échelle d’icône, quête optionnelle, toast de complétion désactivable, visibilité selon complétion/objectifs, détails/texte masqués et liens masqués | Validation serveur/Studio ; données masquées retirées du paquet ; formes tablette et graphe Studio |
| [Tables nommées](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Quests/Reward_Tables/) | `questplus:table` dans les récompenses normales/paliers, bibliothèque portable et éditable dans Studio. Résolution au chargement du pack ; cycles, noms inconnus et profondeur >16 refusés ; les droits déjà attribués gardent leur snapshot | Tests Java/Node, parcours navigateur de création/référence/édition |
| Groupes de chapitres | Champ `category.group`, titres de groupe dans Studio, groupes repliables dans la tablette | Chargement/projection validés ; pas de format de thème FTB |
| Extension d’objectif | `objective.data` validé par `registerObjectiveData`, prédicat exécuté côté serveur, formulaires déclarés et sélecteurs de registres pour objectifs/conditions/récompenses | Tests de validation et filtrage du prédicat ; données privées retirées de la projection joueur |
| [Import SNBT FTB](https://docs.feed-the-beast.com/mod-docs/1.21.1/mods/technical/SNBT/) | JSON/JSON5/SNBT, suffixes numériques sûrs, tableaux typés, séparateurs par ligne, commentaires `#`, traductions JSON, observation/stages et paramètres compatibles. Revue filtrable avant remplacement, téléchargement des sources et undo | Régressions `all_started`, collisions de stages, skip transitif ; import/annulation/confirmation/undo vérifiés dans le navigateur |
| Modèles portables | Snapshots de modèles dans les métadonnées du projet, copie explicite vers/depuis la bibliothèque personnelle | Tests de roundtrip et interface raccordée |

## Écarts encore ouverts

- **Caisses de butin utilisables comme objets**, distribution via loot tables vanilla, variantes de rareté et compositions de caisses imbriquées. Les références actuelles pointent vers des pools complets ; elles ne sont pas des objets ouvrables. Dépendance : protocole durable de consommation/attribution et Data Components d’objets.
- **Data Components complets** pour récompenses/imports et prédicats d’objets exacts/partiels. Les remises conservent les piles via le codec Minecraft, mais le schéma de récompense n’auteurise pas encore des composants arbitraires.
- **Intégrations fluides/énergie concrètes** : formulaires, unités et API de transferts serveur existent ; aucun adaptateur de tank/câble d’un mod tiers n’est livré. Un stock possédé n’équivaut pas à un transfert effectué. Fournisseur externe de stages à enregistrer pour le modpack concerné.
- **Thèmes de resource pack FTB** avec règles par tag/quête et formes arbitraires : le style Quest Plus et ses formes prédéfinies sont conservés.
- **Fidélité complète de migration** : décorations, définitions/traductions des groupes FTB, textes externes hors JSON et objectifs/récompenses tiers. Les cas non préservés restent bloqués et leurs dépendants sont exclus avec avertissement ; aucune disparition silencieuse de prérequis.
- **Autoclaim et autres politiques spécifiques FTB**, sélection partielle d’import, aperçu comparatif complet, migration de progression avec revue des fingerprints et rollback, édition de masse avancée : restent à vérifier/compléter selon le backlog.

## Persistance et limites de preuve

Les remises consommatrices calculent d’abord les images de progression/d’équipe/droits, forcent un journal, sauvegardent un marqueur avec l’inventaire vanilla, puis committent les images JSON. La reprise utilise le marqueur sauvegardé et les empreintes des images, sans réécrire un inventaire plus récent. Les conflits gèlent le stockage. Les claims ambigus se résolvent par commande OP auditée, sans réattribution automatique.

Ce mécanisme est testé par interruption simulée du journal, pas par coupure électrique ou disque plein réel. La durabilité du remplacement vanilla/dossier dépend du système de fichiers ; la relecture vérifie désormais le marqueur et les 37 piles encodées avec leurs composants persistants, y compris lors d’une reprise. Les composants transitoires non sérialisés ne sont pas comparés. Les barrières disque synchrones peuvent ralentir le thread serveur. Les commandes arbitraires et handlers tiers conservent une revue manuelle après effet partiel.

Preuves et commandes : [revue et validation](reviews/ftb-parity/README.md). Les tests actuels ne certifient pas deux clients réels sur serveur dédié, ni les adaptateurs tiers ou la matrice shaders. Voir [le backlog](CODEX_TODO_P0_P2.md).
