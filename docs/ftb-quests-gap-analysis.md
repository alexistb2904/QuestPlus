# Quest Plus face à FTB Quests — comparaison vérifiée le 5 novembre 2026

Comparaison du code actuel avec la documentation officielle consultée, pas avec une instance FTB exécutée. La documentation affiche principalement 26.1.x et avertit que certaines explications proviennent de Minecraft 1.21 ; la page détaillée Toast affiche 1.21.1. Cela ne certifie aucune compatibilité binaire FTB avec Fabric 26.3.

## Objectifs et progression

La [liste officielle des types FTB](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Quests/Types/) décrit les fonctions comparées ici.

| Fonction | Quest Plus actuel | Travail restant |
| --- | --- | --- |
| Objets, XP niveaux, statistiques, advancements, morts/combats, positions, dimensions, biomes, structures | Moteur, Studio et progression tablette existants | Étendre la couverture de roundtrip et les smoke tests moddés |
| Cases manuelles | Objectifs `manual`, marqueurs personnalisés et acquittement des quêtes information via action scoped | Vérifications serveur de la quête et de l’objectif, sans créditer un autre marqueur partagé |
| Observation d'un bloc ou d'une entité | Absente | Raycast serveur et durée de regard, P1 |
| Stages | API générale d'extension, aucun pont livré | Objectifs/conditions et mod fournisseur, P1 |
| Fluides / énergie | Événements Java de transfert de confiance, formulaires Studio, unités tablette | Pas d'extraction automatique de tanks/câbles ; adaptateurs de mods à écrire, P1 |
| Images décoratives | Annotations/images localisées existantes | Migration des décorations FTB incomplète |

Les [réglages de progression FTB](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Quests/Settings/) exposent aussi des écarts : Quest Plus dispose des prérequis AND/OR/NOT fondés sur la complétion, du mode flexible, des répétitions et des objectifs séquentiels/quorums. Les prérequis fondés sur une quête **commencée**, les quorums de dépendances et les différentes politiques de masquage des détails ne sont pas équivalents. Les formes de nœuds, sous-titres, tags de quête et désactivation individuelle du toast restent à concevoir.

## Récompenses

Référence : [types de récompenses FTB](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Quests/Rewards/).

| Fonction | Quest Plus actuel | Travail restant |
| --- | --- | --- |
| Objets, XP/niveaux, advancements | Attributions serveur et formulaires normaux/paliers | Data Components complets |
| Choix du joueur | Cartes tablette, paquet authentifié, slots immuables, sauvegarde avant Claim, plusieurs choix, cycles et droits personnels d'équipe testés | Smoke test dédié avec deux clients réels |
| Random / loot / all | Résolution serveur à la complétion, droits concrets persistés, chances par tirage, simulation Studio | Tests avec adaptateurs tiers ; pas de pool de commandes/scripts |
| Commande | Autorisation explicite par configuration serveur, jamais transmise à la tablette comme charge exécutable | Revue de chaque pack de commandes par l'administrateur |
| Personnalisée | Handlers Java serveur et descripteurs de champs | Contrat d'aperçu public pour les types tiers ; pas d'exécution de scripts de pack |
| Toast personnalisé | Toast de complétion existant et message de récompense | Une récompense Toast avec titre/description/autoclaim n'est pas implémentée |
| Stage | Absente | Attribuer/retirer selon un fournisseur optionnel |

FTB décrit un [fournisseur de stages remplaçable](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Quests/Rewards/Stage_Reward/) et une [récompense Toast paramétrable](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Quests/Rewards/Toast_Reward/). Les messages et toasts de complétion Quest Plus ne sont pas présentés comme des équivalents complets.

## Tables, chapitres et présentation

- Les [tables et caisses FTB](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Quests/Reward_Tables/) peuvent servir de références partagées et de caisses utilisables comme objets, y compris dans les loot tables et compositions imbriquées. Studio possède maintenant une bibliothèque nommée portable de **snapshots** ; les caisses, références vivantes et imbrications restent absentes. L'export inline conserve l'autonomie des packs et interdit les cycles.
- Les [groupes de chapitres FTB](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Chapters/) n'ont pas d'équivalent hiérarchique : Quest Plus organise actuellement des catégories plates ordonnées.
- Le [système de thèmes FTB](https://docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Styling/) permet des thèmes de resource pack, filtrés par quête/tag et des formes personnalisées. Quest Plus conserve son style Minecraft, ses annotations et ses préférences tablette ; il ne lit pas ce format de thème.

## Priorité proposée

1. Résoudre les deux limites P0 existantes : atomicité des remises et récupération auditée des claims réservés.
2. Ajouter les paramètres d'objectifs d'extension et les adaptateurs fluides/énergie choisis par le modpack.
3. Observation/stages et fidélité Data Components.
4. Références de tables versionnées et caisses si le besoin gameplay est confirmé.
5. Migration SNBT/groupes/traductions et rapport inline ; puis réglages de présentation.

Le [backlog P0–P2](CODEX_TODO_P0_P2.md) conserve ces tâches ouvertes. Aucun pourcentage global de parité n'est revendiqué : les types listés ne prouvent pas la fidélité de chaque combinaison ni celle d'un import tiers.
