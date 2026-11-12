---
title: "Create your first Minecraft quest"
description: "Step by step tutorial: create a category, a quest that asks for eight oak logs and a 25 XP reward in Quest Studio, then test it in Minecraft."
---

# Create your first quest

Build a small quest that asks the player to break eight oak logs and awards 25 XP. This walkthrough uses the offline editor. You can publish the same project through an approved server session later.

## Create a category

Open [Quest Studio](https://quest.alexistb.com/). If the startup dialog offers an existing draft, export it before replacing it. Choose **Create a new project**, then **Empty project**.

Select **Add Category**. Name it `First Steps` and choose an oak log as its icon. Keep the generated category ID. Categories organise the tablet and do not track progress themselves.

## Add the objective

Select **Add Quest** and enter `Gather oak logs` as the English title. Choose `minecraft:oak_log` as the icon.

In **Objectives**, add a **Break blocks** objective. Set its target to `minecraft:oak_log` and its count to `8`. Keep the objective ID stable after publishing.

![The Objectives tab with action, target and count controls](/media/objectives.webp)

_The same controls configure the bundled quests. Select the action first, then the target and amount._

Breaking logs, picking up logs and having logs in your inventory are separate actions. Choose `block_break` for this walkthrough. An axe is optional unless you add a tool condition.

## Add a reward

Open **Rewards**, add **XP points**, and enter `25`. Leave the quest on **once** progression. Do not enable automatic claiming for this first test so you can check the Claim action yourself.

![Reward editing in Quest Studio](/media/rewards.webp)

## Export and play

1. Check that validation reports no issues and select **Export ZIP**.
2. Create a disposable world with QuestPlus (and Fabric API on Fabric), then close the world.
3. Back up its `questplus/` folder. Replace the definition files with your export so `categories/`, `quests/` and `config.json` sit directly inside `<world>/questplus/`.
4. Reopen the world, use survival mode and press **J**. Select **First Steps**, then **Gather oak logs**.
5. Pin the quest, close the tablet and break eight oak logs. Open the quest again and claim the XP.

::: tip Test the actual action
Creative and spectator gameplay does not count toward normal quest events. Giving yourself logs does not simulate breaking a block.
:::

## Add a second step

Create a quest named `Build a crafting table`. Add an `item_craft` objective targeting `minecraft:crafting_table` with count `1`. In **Prerequisites**, select `Gather oak logs`.

The second quest now waits for the first quest's completion. Its reward claim is a separate action. Check the order in a fresh world before distributing the pack.

[Arrange the tree](/guide/categories-and-quests) · [Install or publish a pack](/guide/import-and-publish)
