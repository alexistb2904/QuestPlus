# QuestPlus Quest Studio

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Créateurs de packs de quêtes Minecraft utilisant QuestPlus. L'éditeur vise un travail dense sur ordinateur, confirmé par l'utilisateur le 4 novembre 2026.

## Product Purpose

Créer, organiser, traduire et exporter des définitions de quêtes compatibles avec le moteur QuestPlus, sans modifier les sauvegardes des joueurs.

## Operating Context

Application React / TypeScript / Vite, locale et statique. Graphe navigable, bibliothèque de catégories, inspecteur et panneaux d'édition. Import JSON/ZIP, export ZIP validé et brouillon dans le navigateur.

## Capabilities and Constraints

Préserver les identifiants, les données importées et les fonctionnalités existantes. Le schéma serveur fait autorité. L'événement `npc_interact` et les prérequis de quêtes existent ; les dialogues narratifs et le déblocage direct par identifiant de PNJ n'ont pas de schéma serveur. La vérification web ne prouve pas le fonctionnement multijoueur.

## Brand Commitments

Le 4 novembre 2026, l'utilisateur a demandé une reproduction fidèle de sa référence Minecraft : bibliothèque à gauche, graphe central, propriétés à droite, onglets sous le graphe, pierre sombre, bois, touches de vert et sélection violette. Interface dense adaptée au bureau. Nom du produit : QuestPlus. Icônes d'interface Lucide et objets Minecraft.

## Evidence on Hand

Référence visuelle et paysage fournis dans la conversation. Logo existant `web/editor/public/questplus-logo.png`. Définitions embarquées : quatre catégories et treize quêtes du mod. Contrat de données dans `docs/quest.schema.json` et `web/editor/src/core/project.js`.

## Product Principles

- Les contrôles produisent des données réellement prises en charge.
- Les réglages restent accessibles sans masquer le graphe.
- Le brouillon local et l'export serveur ont des états distincts et vérifiables.
- Les textes de quêtes et la langue d'interface sont indépendants.
