---
name: QuestPlus Quest Studio
description: Éditeur visuel de quêtes Minecraft, dense et adapté au travail sur ordinateur.
colors:
  background: "#111214"
  surface-stone: "#242327"
  surface-recessed: "#1b1c20"
  border: "#4b494f"
  border-light: "#6b6870"
  text: "#f1eee9"
  text-muted: "#c1b9b1"
  text-faint: "#aca39a"
  selected: "#ae73e8"
  focus: "#dec2ff"
  gold: "#e9bc73"
  error: "#ffb1a4"
  success: "#ade291"
  action-green: "#3d8d20"
  action-green-deep: "#21600e"
  danger: "#833c39"
  graph: "#191a1f"
typography:
  display:
    fontFamily: "Pixelify Sans, sans-serif"
    fontSize: "27px"
    fontWeight: 700
  title:
    fontFamily: "Pixelify Sans, sans-serif"
    fontSize: "21px"
    fontWeight: 700
  body:
    fontFamily: "Segoe UI, Arial, sans-serif"
    fontSize: "13px"
  label:
    fontFamily: "Segoe UI, Arial, sans-serif"
    fontSize: "12px"
    fontWeight: 600
rounded:
  field: "3px"
  panel: "4px"
  graph-node: "5px"
spacing:
  compact: "5px"
  small: "8px"
  panel: "9px"
components:
  button-primary:
    backgroundColor: "{colors.action-green}"
    textColor: "#ffffff"
    rounded: "{rounded.panel}"
    height: "33px"
    padding: "0 11px"
  button-danger:
    backgroundColor: "{colors.danger}"
    textColor: "#ffe6df"
    rounded: "{rounded.panel}"
    height: "33px"
    padding: "0 11px"
  input:
    backgroundColor: "#18181de8"
    textColor: "{colors.text}"
    rounded: "{rounded.field}"
    height: "30px"
    padding: "5px 8px"
  graph-node:
    backgroundColor: "#34343ae6"
    textColor: "{colors.text}"
    rounded: "{rounded.graph-node}"
    width: "162px"
    height: "66px"
    padding: "7px 10px"
---

# Design System: QuestPlus Quest Studio

## Overview

**Direction : atelier Minecraft.** La référence fournie et le choix de densité confirmé le 4 novembre 2026 définissent un poste de travail sombre : bois pour la bibliothèque, pierre pour les commandes et les panneaux, grille pour le graphe. Les détails de matières servent la reconnaissance des zones tout en laissant lisibles les contrôles.

Le graphe reste visible entre la bibliothèque et les propriétés. Les objectifs et les autres réglages occupent le panneau sous le graphe. Les textes et les données de l'utilisateur restent prioritaires sur la décoration.

**Caractéristiques :**

- Densité de bureau, trois colonnes, panneaux et contrôles compacts.
- Vert pour les actions principales, violet pour la sélection, rouge pour les actions destructives et erreurs.
- Bordure d'herbe et cadres de nœuds épais : exceptions intentionnelles issues de la référence.
- Icônes d'interface Lucide ; objets et blocs représentés par leurs icônes Minecraft.

## Colors

La palette associe des gris de pierre sombres à des bruns de bois, un vert d'action et un violet de sélection.

### Primary

- **Vert action** (#3d8d20 à #21600e) : création, sauvegarde et export ; bordure #73b548.
- **Violet sélection** (#ae73e8) : élément actif, sélection du graphe et contrôles activés.

### Neutral

- **Fond** (#111214), **pierre** (#242327) et **creux** (#1b1c20) : fond et niveaux de panneaux.
- **Texte** (#f1eee9), **secondaire** (#c1b9b1) et **discret** (#aca39a) : hiérarchie de lecture.
- **Bordures** (#4b494f, #6b6870) : délimitation des surfaces.
- **Or** (#e9bc73) : détails et éléments liés à l'identité.
- **Erreur** (#ffb1a4) et **succès** (#ade291) : états et validation.

## Typography

**Titres :** Pixelify Sans 700, police locale, avec repli sans-serif. La plaque de l'éditeur utilise 27 px ; les titres de panneaux utilisent 21 px.

**Corps et contrôles :** Segoe UI, Arial, sans-serif. Le texte courant est à 13 px et les contrôles à 12 px. Les entrées numériques utilisent des chiffres tabulaires. Le texte d'interface et la langue du contenu de quête sont indépendants.

## Layout

Sur bureau, la grille de travail utilise 270px minmax(0, 1fr) 355px, avec 9 px entre les colonnes ; la bibliothèque peut être repliée. À partir de 1600 px, les colonnes latérales passent à 295 et 390 px. À 1350 px ou moins elles passent à 235 et 320 px ; à 1100 px ou moins à 205 et 285 px, et la barre de commandes peut revenir à la ligne.

À 780 px ou moins, les zones s'empilent : bibliothèque, graphe et détails, puis propriétés. Le graphe conserve ses gestes tactiles ; les onglets défilent horizontalement. La disposition réduite enlève la plaque et le paysage pour conserver de la place aux tâches.

## Elevation & Depth

La profondeur combine des superpositions translucides sur les textures, des bordures éclairées et des ombres courtes. Les champs et recherches sont légèrement enfoncés. Les dialogues utilisent une ombre plus ample (0 18px 60px #000b). Les animations et transitions non essentielles sont désactivées si l'utilisateur demande une réduction des mouvements.

## Shapes

Les panneaux et boutons utilisent des coins de 4 px ; les champs, 3 px ; les nœuds, 5 px. Les bordures de nœuds de 3 px, les en-têtes de pierre et le liseré d'herbe de 4 px reprennent délibérément la référence visuelle.

## Components

### Buttons

Les boutons de commande ont une hauteur minimale de 33 px, un remplissage horizontal de 11 px et un fond pierre dégradé. Le bouton principal utilise le dégradé vert ; l'action de suppression utilise un dégradé rouge. Les boutons désactivés réduisent leur opacité. Le focus clavier est dessiné par un contour violet clair de 2 px avec un décalage de 3 px.

### Inputs / Fields

Les champs utilisent un fond #18181de8, une bordure #6a626d, un rayon de 3 px, une hauteur minimale de 30 px et un remplissage de 5px 8px. Le focus renforce la bordure violette et conserve le contour clavier.

### Quest graph

Le canevas est une grille sombre déplaçable. Chaque nœud, de 162 × 66 px avant zoom, associe une icône d'objet Minecraft et un titre ; une bordure et un fond violets signalent la sélection. Les prérequis relient les quêtes. La carte miniature, le zoom et l'aimantation complètent la navigation.

### Quest details

Des onglets donnent accès aux objectifs, récompenses, prérequis, progression, PNJ et traductions ; le JSON avancé reste accessible. Le panneau PNJ propose un objectif npc_interact ciblant un type d'entité. Une autre quête peut utiliser cette quête comme prérequis : le moteur ne propose pas de déblocage direct par identité de PNJ ni de dialogue narratif.

## Do's and Don'ts

### Do:

- **Do** garder les réglages principaux lisibles en même temps que le graphe sur bureau.
- **Do** conserver les contrôles au clavier, les états de focus et les indications de validation et de sauvegarde.
- **Do** employer les textures et les icônes déjà incluses localement.

### Don't:

- **Don't** présenter un contrôle pour une fonctionnalité absente du schéma serveur comme si elle fonctionnait.
- **Don't** remplacer les noms, identifiants ou traductions de l'utilisateur par du texte décoratif.
