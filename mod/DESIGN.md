---
name: QuestPlus In-Game Interface
description: The quest tablet and quest block screens, drawn as a Minecraft map, parchment under ink.
colors:
  paper: "#d9c79e"
  field: "#e8dab5"
  panel: "#cdb98a"
  line: "#b59b6a"
  ink: "#3a2817"
  dim: "#574229"
  light: "#fff4d6"
  done: "#3f7a22"
  active: "#3f64a6"
  claim: "#b5362b"
  claim-hover: "#96291f"
  alert: "#8f2218"
  bar-edge: "#6b5236"
  paper-grain: "#d0bd92"
typography:
  title:
    fontFamily: "Atkinson Hyperlegible, minecraft:default"
    fontSize: "17px"
    fontWeight: 700
  bold:
    fontFamily: "Atkinson Hyperlegible, minecraft:default"
    fontSize: "10px"
    fontWeight: 700
    lineHeight: 1.1
  ui:
    fontFamily: "Atkinson Hyperlegible, minecraft:default"
    fontSize: "10px"
    fontWeight: 400
    lineHeight: 1.1
rounded:
  cut: "1px"
spacing:
  gap: "2px"
  inset: "4px"
  pad: "8px"
  line: "11px"
  rail: "26px"
components:
  button:
    backgroundColor: "{colors.field}"
    textColor: "{colors.ink}"
    typography: "{typography.ui}"
    rounded: "{rounded.cut}"
    padding: "0 4px"
    height: "15px"
  button-pressed:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink}"
  button-selected:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.field}"
  button-selected-hover:
    backgroundColor: "{colors.dim}"
    textColor: "{colors.field}"
  button-accent:
    backgroundColor: "{colors.claim}"
    textColor: "{colors.light}"
  button-accent-hover:
    backgroundColor: "{colors.claim-hover}"
    textColor: "{colors.light}"
  button-disabled:
    backgroundColor: "transparent"
    textColor: "{colors.dim}"
  bar-tab:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.field}"
    width: "76px"
    height: "15px"
  bar-tab-selected:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
  text-field:
    backgroundColor: "{colors.field}"
    textColor: "{colors.ink}"
    rounded: "{rounded.cut}"
    padding: "1px 4px"
    height: "15px"
  title-bar:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.field}"
    height: "22px"
  section-box:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink}"
    rounded: "{rounded.cut}"
    padding: "8px"
  row-tile:
    backgroundColor: "{colors.field}"
    textColor: "{colors.ink}"
    rounded: "{rounded.cut}"
  category-cell:
    backgroundColor: "{colors.paper}"
    rounded: "{rounded.cut}"
    size: "22px"
  category-cell-selected:
    backgroundColor: "{colors.ink}"
  hover-box:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.field}"
    rounded: "{rounded.cut}"
    padding: "4px 6px"
---

# Design System: QuestPlus In-Game Interface

## Overview

**Creative North Star: "The Cartographer's Table"**

The quest tree is charted territory: explored, in sight, still under fog. The whole interface is the Minecraft map item made into a working surface, a parchment page under a dark ink title bar, with ink text and ink rules. It covers the quest tablet (a 500×300 logical screen, shown in a flat 2D window or on the 3D tablet held in first person) and the quest block screens (board, submission station, relay, block settings, quest picker), which share the same window, controls and palette.

This is not a web surface. Everything is drawn with Java fill and text calls; there are no CSS variables, rems or web components. **All sizes in this file are Minecraft logical GUI pixels**, and the source of truth is the Java constants in `mod/client/src/main/java/dev/questplus/fabric/client/ui/QuestSkin.java` (palette, window, panel, bar, type) and `ui/QuestButton.java` / `ui/QuestTextField.java` (control states). When this file and those constants disagree, the constants win and this file is stale.

The surface is dense and flat. Colour is spent only where it carries quest state; everything else is one of four parchment tones or one of two inks. Out of scope and unchanged by this system: HUD pins, toasts, in-world block displays and the 3D models.

**Key Characteristics:**
- Parchment grounds, ink text, an ink title bar on every window.
- Three state colours (green, blue, red) that mean explored, in progress and reward, and nothing else.
- State is always a mark as well as a colour: fog, badge, ring, square.
- Flat fills, one-pixel cut corners, no gradients, no text shadows.
- One outline typeface, rasterised at the texel density of the pass being drawn.
- The chart fills the tablet; a right panel opens only for a selected quest or a search, and closes with it.

## Colors

A map item's palette: four parchment tones stepped from light to rule, two inks, and three state colours laid on top.

### Primary
- **Map Ink** (`ink`, #3a2817): all primary text, the title bar, window and marker outlines, selected controls, explored trails. The interface's one dark.
- **Parchment** (`paper`, #d9c79e): the page. Window ground, the chart, the selected tab (which joins the page).

### Secondary
- **Claim Red** (`claim`, #b5362b): the reward action (Claim, Claim all, Choose) and reward markers: the ring around a claimable quest, the swatch beside a claimable search row, the claimable category badge. `claim-hover` (#96291f) is its hovered fill.
- **Explored Green** (`done`, #3f7a22): completed quests, completed progress bars, the category progress bar, an online team member.
- **Underway Blue** (`active`, #3f64a6): quests in progress and their progress bars.

### Tertiary
- **Alert Red** (`alert`, #8f2218): error and unsaved-state text. Darker than claim red so it reads as text on parchment (5.2:1 on `paper`).

### Neutral
- **Field** (`field`, #e8dab5): the lightest tone. Resting buttons, text fields, row tiles, the plate behind an item icon on a state-coloured marker, text on ink.
- **Panel** (`panel`, #cdb98a): the tone below the page. The category band, the right panel, section boxes on block screens, a pressed button, the fill of a locked marker.
- **Rule** (`line`, #b59b6a): resting borders, hairline rules under headings, the empty track of a progress bar, the category rail ground, subtitles on the ink bar.
- **Dim Ink** (`dim`, #574229): secondary text (descriptions, hints, section headings, disabled labels), unexplored trails, the chart's corner marks.
- **Lamp** (`light`, #fff4d6): text on claim red and the glyph inside an ink badge. Used nowhere else.
- **Bar Edge** (`bar-edge`, #6b5236): the hover fill of a tab and the outline of other controls on the ink bar; a scroll track outside the window.
- **Grain** (`paper-grain`, #d0bd92): the flecks on the chart paper.

### Named Rules
**The Meaning-Only Rule.** Green, blue and red appear only where they carry quest state. No decorative use, no tinted panels, no coloured headings.

**The Reward Red Rule.** Claim red is reserved for the reward action and reward markers. A screen has at most one claim-red button.

**The Never Colour Alone Rule.** Every state has a non-colour mark: a locked quest sits under a hatched fog, a completed quest carries a check badge, a claimable quest a red ring and a badge, an in-progress row a progress bar, presence a filled or hollow square. The details panel names the state in words beside its swatch.

**The On-Paper Rule.** Authored and pack colours were picked for a dark ground. Anything brighter than luma 84 is scaled down to luma 84 with its hue kept (`QuestSkin.onPaper`) before it is drawn on parchment: annotation text and borders, rich description spans, trail colours. Resource-pack theme overrides still apply: `text_color` and `disabled_text_color` replace `ink` and `dim` in text drawn through `QuestSkin`, and the per-quest colour properties replace the state fills.

**The Fills Not Words Rule.** The three state colours sit near 3:1 on parchment: enough for a marker or a bar, not for running text. State colours fill shapes; words are set in `ink`, `dim` or `alert`.

## Typography

**Display Font:** Atkinson Hyperlegible Bold (falls back to the game's default font for glyphs it lacks, such as CJK)
**Body Font:** Atkinson Hyperlegible Regular (same fallback)

**Character:** A plain, wide-apertured outline face chosen because the pixel face was unreadable on the 3D tablet. It is shipped under the SIL Open Font License (`mod/licenses/AtkinsonHyperlegible-OFL.txt`).

Each cut exists six times, at oversample 1, 2, 3, 4, 6 and 8 (`assets/questplus/font/{ui,bold,title}_{1,2,3,4,6,8}.json`, plus unsuffixed copies at 4 for vanilla fallbacks). Font textures are sampled NEAREST, so a glyph drawn at another density than it was rasterised for drops or doubles texels. Every draw through `QuestSkin.text`, `centered`, `big` and the rich-text renderer therefore picks the sample closest by ratio to the screen pixels it lands on (`QuestSkin.density`: the current pose scale times the pass density), including text scaled by the chart's zoom. Plain `g.text` with the screen font uses the pass density alone. Never apply Minecraft's synthetic bold to these faces (it draws each glyph twice, a pixel apart): use the Bold or Title cut.

### Hierarchy
- **Title** (bold, 17px): the one large line a screen owns. The category name on the tablet and the team name; the Title cut is used only on the tablet. If it does not fit it drops to Bold rather than truncate.
- **Bold** (bold, 10px, 11px line pitch): the selected quest's name, section headings in the search list and details (set in `dim` over a 1px rule), the window title of every block dialog.
- **UI** (regular, 10px, 11px line pitch): everything else. Labels, descriptions, counts, button text.

There is no size between 10px and 17px and none below 10px.

### Named Rules
**The One Title Rule.** A screen has one 17px line. Hierarchy below it comes from weight and from ink versus dim ink, never from another size or from scaling text up.

**The No Shadow Rule.** Text is drawn without a drop shadow. On parchment a shadow only muddies the face.

**The Ellipsis Rule.** Text that would overflow is cut with an ellipsis (`QuestSkin.fit`), and a control whose label was cut shows the full label in its hover box.

## Layout

The tablet is a fixed 500×300 logical surface. In 2D it is scaled uniformly to fit the window; on the 3D tablet it is rendered offscreen and mapped onto the model's screen.

- **Ink bar** (22px tall, across the top): three tabs from the left (76px wide, 15px tall, 6px apart, bottom edge flush with the bar), the search field, and a 15px close control at the right, 10px clear of the search field.
- **Category rail** (37px wide, left edge, on `line`): 22px cells on a 26px pitch.
- **Category band** (33px tall, on `panel`, closed by a 1px ink rule): category icon, the Title line, a one-line description in `dim`, and at the right a count over an 80×3px progress bar. While anything is claimable a compact claim-red Claim all sits at the band's right end; the count gives way to it when room is short.
- **Filter row** (15px controls): four status chips at the left, three 15px chart controls at the right.
- **Chart**: fills the centre, from under the filter row to 4px above the bottom edge.
- **Right panel** (168px wide when open, on `panel`, with a 1px ink rule on its left; content inset 8px, 152px wide): closed by default, and the chart then runs to the right edge. It opens for the selected quest's details, or for the search list while the search field holds text: every quest in the catalogue whose title or category title matches, grouped by category under Bold headings, current category first. Closing details returns to the list when there is a query and otherwise closes the panel. **The search never filters the chart; only the status chips do.** When the panel opens the chart pans just enough to keep the selected quest visible; it never refits. Below 480px of width the panel is dropped and details overlay the chart.

Block screens are compact centred dialogs that never grow with the window (`QuestBlockChrome`): picker at most 300×268 with 20px rows, submission station 440×288, relay 460 wide, block settings 320×294, board settings 620 wide. Each has a 32px ink bar with the block's icon and a Bold title at the left, a context line in `line` under it, and the owner button at the right; section boxes on `panel` inside the page; list rows on `field`; and a footer under a 1px rule with the status at the left (`alert` while there are unapplied changes or an error, `dim` otherwise) and Apply (80px) and Close (72px) at the right. Controls are 20px tall with 4px gaps and 10px side padding. Below 400px of width the columns stack and the dialog scrolls. A control whose label is cut shows the full label in the shared hover box. Behind every 2D window the world is dimmed by a flat dark scrim.

Rhythm: 2px between sibling controls, 4px text inset inside a control, 8px padding inside a box or panel, 11px line pitch.

## Elevation & Depth

There are no shadows and no gradients. Depth is tonal: `field` (raised, touchable) over `paper` (the page) over `panel` (set back) over `line` (rail and tracks), with ink rules where two regions meet. The only translucent fills are the fog over a locked quest, the ground of an authored note, and the hover box (ink at 96%), each a flat alpha.

On the 3D tablet the screen is drawn with the true-emissive `eyes` render type when the screen-emissive setting is on and no shader pack is active, so palette values reach the player unshaded by cardinal face lighting. With a shader pack, or with the setting off, the screen takes world lighting.

### Named Rules
**The Flat Ink Rule.** Surfaces are flat fills. A region is separated by a tone step or a 1px rule, never by a shadow, a glow or a gradient.

## Shapes

One form: the rectangle with a one-pixel cut corner (`QuestSkin.panel`). The corner pixel is removed and the 1px border steps diagonally across it. Windows, buttons, fields, boxes, tiles, category cells, square quest markers, rings and the hover box all use it. There is no radius anywhere.

Borders are 1px: `line` at rest, `ink` when hovered, focused, selected or structural. Rules are 1px. Progress bars are square-ended, 2 or 3px tall, `line` track under a state fill. Quest markers may take an authored pixel shape; whatever the shape, the outline is ink and the fill is the state colour.

## Components

### Buttons
Flat, compact, keyboard-focusable and narrated; no vanilla sprites.
- **Shape:** cut-corner rectangle, 15px tall on the tablet, 20px on block screens; label centred with a 4px inset, optional 12px item icon at the left.
- **Default:** `field` fill, `line` border, `ink` label.
- **Hover / Focus:** border turns `ink`. No fill change, no motion.
- **Pressed:** `panel` fill.
- **Selected:** `ink` fill, `field` label; `dim` fill when hovered. Used for the current filter chip, an enabled toggle, the chosen option in a group, and the confirming action of a block screen (Save, Apply).
- **Accent:** `claim` fill, `light` label, `ink` border; `claim-hover` when hovered. The reward action only.
- **Tab:** a bare `field` label on the ink bar; `bar-edge` fill on hover or focus. Selected: `paper` fill and `ink` label, so the tab becomes a piece of the page.
- **Other controls on the bar** (close, the block screens' owner button): no fill, `bar-edge` border, `field` label; border turns `field` on hover.
- **Disabled:** no fill, `line` border, `dim` label (`line` label on the bar).

### Chips
The four status filters are ordinary buttons sized to their label (label width plus 10px), 2px apart; the current one is Selected. The filter is always visible and one click away.

### Cards / Containers
- **Window:** `paper` page with a 1px ink cut-corner border and an ink bar inside its top edge (`QuestSkin.window`).
- **Section box** (block screens): `panel` fill, `line` border, 8px padding; heading in `ink`, hint in `dim`.
- **Row tile** (objectives, rewards, pinned quests): `field` fill, `line` border, item icon at the left, label in `ink`, count in `dim` at the right over a 2px progress bar (blue while under way, green at 100%).
- **Search list row:** no box. A 12px item icon, the title in `ink` cut with an ellipsis, a 7px status swatch at the right with the status named in the hover box; an in-progress row carries a 2px blue bar under the title. The hovered row gets a `field` ground.
- **Section heading:** Bold in `dim` over a full-width 1px `line` rule, with more air above than below.

### Inputs / Fields
- **Style:** `field` fill, `line` cut-corner border, centred `ink` text, hint in `dim`, no text shadow. 15px tall on the tablet, 20px on block screens. The picker's search field carries a magnifier cap and left-aligned text so it reads as an input.
- **Focus:** border turns `ink`.
- **Uneditable:** text in `dim`.

### Navigation
- **Tabs** sit on the ink bar at fixed top-left coordinates (client GameTests click them); see Buttons, On the bar.
- **Category rail:** 22px cut-corner cells holding a 16px item icon; `paper` fill at rest, `ink` fill for the current category, ink border always. A group header cell is on `panel`. A 7px corner badge (5px colour inside a 1px contrasting frame) marks a category with a reward to claim (claim red) or a newly unlocked quest (blue). A 1px ink tick above or below the rail shows there is more to scroll.

### Quest giver dialog
A block-screen window (at most 420×300, never taller than its content needs, at least 196) named after the NPC, with "Request 1 of 3" as the context line when the NPC has several requests. At the left, a 92px `panel` box holds the NPC itself, drawn live and glancing gently toward the cursor (`QuestGiverRenderer.portrait`). At the right, the NPC speaks: one greeting line in `dim`, the quest name as the screen's one Title line (wrapped, never cut), the description in `ink`, then "What I need" and "What you will receive" as section headings. Objectives are row tiles; once accepted they carry a count and a 2px progress bar. Up to four rewards are tiles; more become a grid of 22px icon cells with their amount in the game font and the full label in the hover box. Long content scrolls behind a 2px `line` rail with a `dim` thumb. Footer: page arrows at the left; Decline and Accept (Selected, not claim red) at the right, Claim reward (accent) when a reward waits, Farewell otherwise. With nothing to offer, the NPC says why in character: the quest already completed, a request still locked, or a cooldown.

### Hover box
Drawn by the screen, inside the 500×300 surface, never as a native tooltip (which could escape the tablet). Ink at 96% with a `field` border and `field` text, up to 214px of text wide, 11px line pitch, 4px by 6px padding. It appears only when it says more than the control already shows: an explicit note, a label that was cut, or the name of a symbol-only control.

### Chart (signature)
- **Paper:** `paper` with sparse 1px `paper-grain` flecks, a 1px `line` border inset 1px, and a small `dim` mark in each corner, like a framed map.
- **Markers:** 32px by default. Fill is the state colour (`field` available, `panel` locked, blue in progress, green completed, claim red when a reward waits), outline is ink. On a state-coloured marker the item icon sits on a `field` plate.
- **Fog:** a locked marker is veiled by `panel` at 63% with 1px `line` hatching every 3px.
- **Completed:** an ink badge at the lower right holding a `light` check built from fills.
- **Claimable:** a 1px claim-red ring 2px outside the marker, plus an ink badge at the lower right with a `light` mark.
- **Selected or keyboard-focused:** a double ink ring outside any reward ring.
- **Trails:** a dependency whose source is completed is a solid 2px ink line; otherwise a 1px `dim` dashed line (4 on, 3 off). Both end in a small arrowhead and scale with the zoom.
- **Authored notes:** drawn on a translucent `field` ground with their colour passed through the On-Paper Rule.

### Progress
`QuestSkin.bar`: `line` track, square ends, 2px in rows and 3px in the category band. Blue while under way, green at 100% and for category completion. Block screens also use a segmented bar of `line` cells filled with the state colour.

### Presence
A 6px square beside a team member: filled green when online, hollow when offline.

### Motion
The 2D tablet slides in and out over 250ms on a smoothstep curve; a claimed reward draws a 1px ink rule across over 400ms. Both are skipped when animations are off or reduced motion is on. Controls do not animate.

## Do's and Don'ts

### Do:
- **Do** draw every surface with `QuestSkin.window` and `QuestSkin.panel`, and every control with `QuestButton` and `QuestTextField`, so the tablet and the block screens stay one interface.
- **Do** take colours from the `QuestSkin` constants. Text is `ink` or `dim`; errors are `alert`; text on ink is `field`; text on claim red is `light`.
- **Do** give every state a mark as well as a colour, and name the state in words wherever there is room.
- **Do** pass any authored or pack colour through `QuestSkin.onPaper` before drawing it on parchment.
- **Do** draw text through `QuestSkin.text`, `centered` or `big`, so resource-pack text colour overrides apply.
- **Do** keep one 17px Title line per screen and set everything else at 10px, regular or bold.
- **Do** cut overflowing text with `QuestSkin.fit` and let the hover box carry the full label.
- **Do** keep hover content inside the 500×300 surface.
- **Do** honour the animations and reduced-motion settings for any movement.

### Don't:
- **Don't** use green, blue or claim red for decoration, headings or body text.
- **Don't** use claim red for anything but the reward action and reward markers, or put two claim-red buttons on one screen.
- **Don't** signal a state with colour alone.
- **Don't** add drop shadows to text, gradients, glows or rounded corners.
- **Don't** scale text up for emphasis or introduce a size between 10px and 17px.
- **Don't** use vanilla button sprites or native tooltips on these screens.
- **Don't** resize the chart when a quest is opened, or move the tabs from their top-left coordinates.
- **Don't** restyle the HUD pins, toasts, in-world block displays or the 3D models from this file; they are outside this system.
