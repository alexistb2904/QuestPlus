# QuestPlus tablet — unified 2D/3D display

## Physical tablet item

The creative **Quest** tab contains the tablet and loot crate. You can also use
`/give @s questplus:tablet` to obtain the non-stackable tablet. Holding it
in the main hand keeps the visible side pose from closing on the right in first person;
right-click (including on blocks) starts the existing opening animation.
Closing the menu returns to the carry pose. Changing items, opening another
screen, changing camera or disconnecting releases the first-person animation.
The J shortcut and the reduced-motion/2D fallback remain available.
In third person, simply holding the tablet applies the existing Blockbench
side pose and physical tablet layer, including for other players. The ordinary
held-item model is suppressed while that layer is visible; the left arm and
head keep their normal animations in the carry pose.
All third-person tablet poses use a 0.4 scale, with the tablet grip anchored
inside the right palm. The two-handed pose brings the wrists inward for that
smaller width. In first person, left-click plays the editable `questplus.carry_attack`
clip without canceling the normal attack. Closing the menu switches directly
back to `questplus.carry`, retaining FP PAL ownership throughout the transition.
The editable carry clip lives in `questplus_first_person.bbmodel`; the existing
exporter also generates the inventory/third-person item model from its geometry.

## Same screen UI in both modes

The fallback 2D window and the first-person 3D tablet share the **same 500×300
logical display layout**, with identical tab/navigation/category/quest/settings
positions. The 2D fallback displays a slim flat bezel instead of attempting to
stretch the 3D model across an arbitrary aspect ratio.

A single uniform transform scales the complete 2D tablet, respecting the
Minecraft GUI dimensions and the saved **Interface scale** preference. All
mouse clicks, dragging, scrolling, hover and pointer deltas are converted
back through the inverse transform. Changing Interface scale while the 2D
tablet is open no longer recalculates different widget dimensions: only the
viewport transform changes.

The fit algorithm is in `clientmodel/TabletUiViewport.java`. This record has
no rendering dependencies so scale, aspect, centering and input round-trips
can be tested independently at different GUI sizes.

## 3D illumination / brightness

**Tablet → Settings** displays two 3D-specific controls when Tablet is enabled:

- **Self-illuminated screen**: ON by default, controls whether the screen surface
  is rendered with `entityTranslucentEmissive` or the world-lit
  `entityTranslucent` shader.
- **Screen brightness**: 0–100%, steps of 10% via the setting button; default 100%.
  The backlight uses a separate darkening pass on the **3D tablet atlas only**.

Neither option changes the 2D interface's colors or its rendering scale.
Both are local client preferences saved in `config/questplus-client.json`.
Old config files missing these fields are upgraded to **emissive=true** and
**brightness=1.0**, without losing any existing settings.

Localized in English, French, Spanish and Simplified Chinese.

## Ambient 3D details

The book medallion's engravings breathe with a six-second violet light cycle;
the corner rivets have a softer amber pulse. Four small pixel motes drift above
the medallion, fading in and out over 3.6 seconds. These are emissive quads
attached to the tablet, shared by first-person and third-person rendering;
they do not spawn world particles, illuminate blocks, or cover the quest display.

Disabling animations or enabling reduced motion keeps the engravings and rivets
at a constant low glow and hides the motes. The 2D fallback is unchanged.
Geometry offsets follow the current standalone Blockbench model; update
`TabletIdleEffects` if the medallion or rivets move in a future asset revision.

## Verification scope

Node source-contract tests check layout equivalence, inverse mouse handling,
the 3D render-type switch and all four language strings. Pure Java JUnit
tests check uniform 5:3 fitting and pointer hit testing across six GUI sizes
and three requested interface scales.

Minecraft 26.3/Fabric rendering and GUI-scale crash reproduction still need
an actual game launch on JDK 25; the user's previous crash log was not
attached, so a specific historical exception cannot be conclusively identified.
