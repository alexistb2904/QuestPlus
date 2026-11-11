---
name: QuestPlus Quest Studio
description: A dense Minecraft quest editor designed for desktop work.
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

**Direction: Minecraft workshop.** The supplied reference and the density choice confirmed on November 4, 2026 define a dark workspace: wood for the library, stone for controls and panels, and a grid for the graph. Material details distinguish areas while keeping controls readable.

The graph stays visible between the library and the properties. Objectives and other settings occupy the panel below the graph. User text and data take priority over decoration.

**Characteristics:**

- Desktop density, three columns, and compact panels and controls.
- Green for primary actions, purple for selection, and red for destructive actions and errors.
- Grass trim and thick node frames are intentional exceptions drawn from the reference.
- Lucide interface icons. Minecraft icons represent items and blocks.

## Colors

The palette combines dark stone grays and wood browns with action green and selection purple.

### Primary

- **Action green** (#3d8d20 to #21600e): create, save, and export. Border #73b548.
- **Selection purple** (#ae73e8): active items, graph selection, and enabled controls.

### Neutral

- **Background** (#111214), **stone** (#242327), and **recessed** (#1b1c20): background and panel levels.
- **Text** (#f1eee9), **secondary** (#c1b9b1), and **subtle** (#aca39a): reading hierarchy.
- **Borders** (#4b494f, #6b6870): surface boundaries.
- **Gold** (#e9bc73): details and identity elements.
- **Error** (#ffb1a4) and **success** (#ade291): states and validation.

## Typography

**Headings:** Pixelify Sans 700, locally hosted, with a sans-serif fallback. The editor nameplate uses 27 px. Panel headings use 21 px.

**Body and controls:** Segoe UI, Arial, sans-serif. Body text uses 13 px and controls use 12 px. Numeric inputs use tabular figures. The interface language and quest content language are independent.

## Layout

On desktop, the workspace grid uses `270px minmax(0, 1fr) 355px` with 9 px between columns. The library can be collapsed. At 1600 px and above, side columns become 295 and 390 px. At 1350 px or less they become 235 and 320 px, and at 1100 px or less 205 and 285 px. The toolbar may wrap.

At 780 px or less, areas stack: library, graph and details, then properties. The graph retains touch gestures, and tabs scroll horizontally. The compact layout removes the nameplate and landscape to leave room for tasks.

## Elevation & Depth

Depth combines translucent overlays on textures, highlighted borders, and short shadows. Fields and search boxes are slightly recessed. Dialogs use a larger shadow (`0 18px 60px #000b`). Nonessential animations and transitions are disabled when reduced motion is requested.

## Shapes

Panels and buttons use 4 px corners, fields 3 px, and nodes 5 px. The 3 px node borders, stone headers, and 4 px grass trim deliberately follow the visual reference.

## Components

### Buttons

Command buttons have a minimum height of 33 px, 11 px horizontal padding, and a stone gradient background. The primary button uses a green gradient. Delete actions use a red gradient. Disabled buttons have reduced opacity. Keyboard focus uses a 2 px light purple outline with a 3 px offset.

### Inputs / Fields

Fields use a #18181de8 background, #6a626d border, 3 px radius, 30 px minimum height, and 5px 8px padding. Focus strengthens the purple border and retains the keyboard outline.

### Quest graph

The canvas is a pannable dark grid. Each node, 162 × 66 px before zoom, combines a Minecraft item icon and a title. A purple border and background indicate selection. Prerequisite links connect quests. A minimap, zoom, and snapping support navigation.

### Quest details

Tabs provide access to objectives, rewards, prerequisites, progression, NPCs, and translations. Advanced JSON remains accessible. The NPC tab picks which quest giver offers the quest and opens the Quest givers library, a wide dialog built on the reward-library shell: an NPC list, then appearance, spawn-place tiles and a quest search grouped by chapter. The `npc_interact` objective lives in the Objectives tab like any other objective.

## Do's and Don'ts

### Do

- Keep the main settings readable alongside the graph on desktop.
- Preserve keyboard controls, focus states, and validation and save feedback.
- Use the textures and icons already included locally.

### Don't

- Present a control for a feature absent from the server schema as if it works.
- Replace the user's names, identifiers, or translations with decorative text.
