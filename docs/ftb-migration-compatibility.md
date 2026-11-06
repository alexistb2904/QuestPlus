# Import FTB Quests — état vérifié le 5 novembre 2026

Dans Studio, importer un dossier ou ZIP contenant `chapters/*.json5`, `.json` ou `.snbt`. La revue affiche les avertissements et la correspondance des IDs. Sélectionner les quêtes ; leurs prérequis sont inclus automatiquement. La confirmation remplace le projet, avec annulation possible. Les sources ne sont jamais modifiées.

## Fonctions converties

- Objets possédés, craftés ou remis avec consommation ; composants exacts/partiels et filtres OR simples `item`/`tag`.
- XP consommée en points ou niveaux ; conteneurs de fluide via Fabric Transfer API ; batteries via Team Reborn Energy API inclus dans le JAR. Choisir explicitement les unités source et le rapport FE/unité Fabric.
- Élimination, biomes, structures, dimensions, statistiques, advancements, observation de blocs/entités/tags, stages, interactions et positions par coordonnées de blocs.
- Récompenses objets avec composants, bonus aléatoire figé, propriété unique, XP, advancements/critères, stages, notifications ; choix, random, loot, all et tables nommées. Caisses ouvrables et variantes avec poids de butin optionnels.
- Politiques individuelles/équipe, récupération automatique, masquée ou sans notification, exclusion de Tout récupérer. La récompense partagée reste unique après départ/dissolution d’équipe.
- Prérequis complétés/commencés, quorum, progression flexible/séquentielle/répétable, visibilité, formes, sous-titres, groupes et traductions externes. Coordonnées converties en unités natives.
- Décorations texte et images locales PNG/JPEG/GIF incluses dans les sources. Aucun téléchargement d’image externe. Rotation, actions de clic, teinte et visibilité conditionnelle sont signalées.

Les commandes de récompense nécessitent une installation locale approuvée et `enableCommandRewards`. La publication depuis le navigateur les refuse : le Hub ne constitue pas une console distante. Pas de substitutions FTB ni de permissions supérieures à 2.

## Progression privée

Après import des définitions et de leurs métadonnées, placer la sauvegarde FTB JSON5 dans `<monde>/ftbquests/<UUID source>.json5`, puis, comme opérateur :

```
/questplus migrate-ftb preview <UUID source> <UUID joueur>
/questplus migrate-ftb confirm
/questplus migrate-ftb rollback <UUID opération>
```

La prévisualisation expire après deux minutes et appartient à l’opérateur. Les sauvegardes avant/après restent privées sous `data/questplus/migrations`. Les fingerprints courants sont appliqués après contrôle des correspondances ; les récompenses déjà réclamées dans FTB ne sont pas redistribuées. Une progression cible existante ou des données modifiées depuis la revue bloquent l’opération. Un rollback refuse d’effacer des actions ultérieures, notamment des récompenses livrées. Une équipe QuestPlus cible doit déjà exister ; les appartenances FTB Teams ne sont pas créées automatiquement. Les anciennes sauvegardes SNBT de progression nécessitent une conversion locale préalable.

## Limites

Les Task Screens sont exclus à la demande. Les scripts KubeJS/CustomTask et types tiers nécessitent leurs handlers serveur portés vers QuestPlus. Le nom d’un type ne permet jamais de remplacer une mécanique absente par une case à cocher. Une quête non convertible et ses dépendantes sont exclues avec un motif explicite.

Les prédicats d’observation block-state/NBT complexes, NBT d’objets anciens et filtres avancés nécessitent un adaptateur. Les transferts automatisés de tanks/câbles nécessitent une intégration serveur spécifique ; les remises de conteneurs ne prétendent pas les remplacer.

Les thèmes locaux `assets/ftbquests/ftb_quests_theme.txt` ou `assets/questplus/quest_theme.txt` acceptent couleurs, sélecteurs globaux/tag/ID et références de propriétés. Les formes locales `textures/shapes/<nom>/shape.png` utilisent leur masque alpha. Les compositions d’icônes et fonds/contours FTB ne sont pas reproduits intégralement.

## Test reproductible

Pack public : [Randon One Block](https://github.com/PsyCrow1976/Randon-One-Block), commit `7441f9284e246fe43cca480af2fafdafb178e75d`.

```
node scripts/verify-ftb-import.mjs <dossier des sources>
```

Résultat : **19 chapitres, 970 quêtes source, 958 importées, 12 exclues, 21 avertissements**. Validation puis export/réimport réussis, mappings conservés. Les exclusions concernent les scripts custom et leurs dépendances. Les fichiers du pack restent dans `.tools`, hors sources livrées. Il s’agit d’une preuve de conversion, pas du lancement de ce modpack ni de la disponibilité de ses mods sur Fabric 26.3.

Voir [le bilan des tests](reviews/ftb-parity/continuation-tests.md).
