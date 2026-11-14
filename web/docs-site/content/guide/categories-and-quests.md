---
title: "Categories and quests"
description: "Create quest categories (chapters), add quests and connect prerequisites to build a progression tree in Quest Studio for Minecraft."
---

# Categories and quests

## Create a category

Click **Add Category**, enter its name, then set its icon and order in **Properties**.

![A category with text annotations and grouped quests](/media/studio.webp)

Category IDs connect quests to chapters. Use a stable namespaced identifier such as `example:basics`. Display order controls the chapter list, while layout positions control the diagram.

## Create a quest

1. Select a category and choose **Add Quest**.
2. Add a title, icon and optional description.
3. Set [objectives](/guide/objectives) and [rewards](/guide/rewards).
4. Link prerequisite quests in the diagram.
5. Export the finished pack.

**Example:** To make _Collect Stone_, add `item_obtained` targeting `minecraft:cobblestone` with count `32` (mined stone drops cobblestone, and only items picked up from the ground count). Reward the player with XP or items.

Use a right-click template for common quest types. You can also add decorative annotations.

## Build a progression tree

Connect **Collect Stone** → **Craft a Furnace**. The second quest unlocks after the first. Use `and`, `or` and `not` for complex prerequisites. Cyclic dependencies are invalid.

Use the **Prerequisites** tab for explicit dependencies. Layout alone does not make one quest depend on another. Completion unlocks a prerequisite, reward collection is separate.

![The Prerequisites tab of a quest that requires another quest to be completed](/media/prerequisites.webp)

_This quest becomes available when Blank Pages is completed._

## Explain the chapter

Add a heading or note to explain the goal of a branch. Background annotations group related nodes visually. Images and GIFs can illustrate a build or machine, but increase pack size. Annotations do not create objectives or grant progress.

Move link waypoints when automatic routing overlaps content. These routes change presentation only. Keep the tree readable at the tablet's smaller display size.

## Control visibility and information

A hidden quest is revealed when its prerequisites are satisfied or it already has progress. Text written elsewhere in the pack can still reveal its contents, so review descriptions if you want to avoid spoilers.

Use an information quest for an explanation or acknowledgment. A read only information quest has no completion or rewards and cannot unlock another quest. Use manual acknowledgment when reading should count toward progression.

## Keep IDs stable

Changing a title does not require changing an ID. Preserve quest, objective and tier IDs after publishing. Renaming them does not move saved progress. Changing mechanics may make existing partial progress incompatible, so test on a copied world.

[Advanced JSON5 format](/reference/pack-format)
