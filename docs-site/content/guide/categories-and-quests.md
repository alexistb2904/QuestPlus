# Categories and quests

## Create a category

Click **Add Category**, enter its name, then set its icon and order in **Properties**.

## Create a quest

1. Select a category and choose **Add Quest**.
2. Add a title, icon and optional description.
3. Set [objectives](/guide/objectives) and [rewards](/guide/rewards).
4. Link prerequisite quests in the diagram.
5. Export the finished pack.

**Example:** To make *Collect Stone*, add `item_obtained` targeting `minecraft:stone` with count `32`. Reward the player with XP or items.

Use a right-click template for common quest types. You can also add decorative annotations.

## Build a progression tree

Connect **Collect Stone** → **Craft a Furnace**. The second quest unlocks after the first. Use `and`, `or` and `not` for complex prerequisites. Cyclic dependencies are invalid.

[Advanced JSON5 format](/reference/pack-format)
