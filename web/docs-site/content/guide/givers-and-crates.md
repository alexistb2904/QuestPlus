---
title: "Quest givers (NPCs) and loot crates"
description: "Meet Quest Plus quest givers in villages and ruins, accept their quests and open animated loot crates. Pack creators can add their own NPCs and crates."
---

# Quest givers and loot crates

Some quests start with a conversation, and some rewards arrive in a crate that you open yourself. The bundled pack uses both, and pack creators can add their own.

## Meet a quest giver

[![Marlo the Cartographer standing between a Quest Board and a Submission Station](/media/giver-world.webp)](/media/giver-world.webp)

_A quest giver shows its name above its head. Marlo belongs to the bundled Explorers guild._

Quest givers are characters that stay where they appeared. They never despawn and cannot be hurt in survival. The bundled pack adds three of them.

| Quest giver              | Where to look                                                     |
| ------------------------ | ----------------------------------------------------------------- |
| Marlo the Cartographer   | Villages, most often taiga villages                               |
| Sela the Treasure Hunter | Desert pyramids, jungle pyramids and trail ruins, rarely villages |
| Oswin the Wanderer       | Abandoned camps and, very rarely, anywhere in the Overworld       |

Each structure rolls its chance once, when it is generated. Chunks that existed before a quest giver was defined never receive one. An operator can place one by hand with `/questplus npc <id>`.

### Accept a request

[![The quest giver dialog with a request, its objectives, its rewards and the Accept button](/media/giver-offer.webp)](/media/giver-offer.webp)

1. Right-click the quest giver.
2. Read the request. **What I need** lists the objectives and **What you will receive** lists the rewards.
3. Choose **Accept** to start the quest. **Decline** only closes the dialog.

A quest offered by a quest giver stays off the tablet and counts no progress until you accept it. When a quest giver has several open requests, use the arrows in the dialog to move between them.

### Follow your progress and claim

[![The same dialog after accepting, with a counter beside each objective](/media/giver-progress.webp)](/media/giver-progress.webp)

_Talk to the quest giver again to see where each objective stands._

Return to the quest giver when the objectives are complete and choose **Claim reward**. By default, rewards of these quests are claimed from a quest giver that offers the quest. A pack can set `npcTurnIn` to `false` so they are claimed like any other reward.

## Open a loot crate

[![A loot crate reveal with an open chest, an EPIC label and the rewards floating above it](/media/loot-chest.webp)](/media/loot-chest.webp)

_The reveal shows every reward and the rarity of the draw._

A **loot crate** is an item that holds one draw from a reward table. You can receive one as a quest reward, from a mob when the pack enables drops, or from an operator.

1. Hold the crate in survival or adventure mode.
2. Right-click the ground. A chest appears, shakes, opens and shows your rewards before they fly to you.
3. Right-click in the air to open the crate at once, without the reveal. The same happens when the chest has no room to stand.

<details class="doc-clip">
<summary>Watch a crate reveal</summary>

![A loot crate placed on the ground that shakes, bursts open and hands out its rewards](/media/loot-crate.gif)

</details>

The chest is only a show. It cannot be mined, pushed or looted, and it removes itself after about seven seconds. If your inventory is full, what does not fit drops at your feet. A pack can make rewards wait for free space instead, see [Full inventories](/reference/configuration#full-inventories).

| Label     | Color  | Chance of the draw |
| --------- | ------ | ------------------ |
| COMMON    | White  | Above 40%          |
| UNCOMMON  | Green  | 40% or less        |
| RARE      | Blue   | 15% or less        |
| EPIC      | Purple | 5% or less         |
| LEGENDARY | Gold   | 1% or less         |

A crate is never rated below the Minecraft rarity of the items it shows. The reveal displays up to eight rewards.

## Add them to your pack

### Loot crates

[![A reward table in Quest Studio with the Openable loot crate option, crate name, color, mob drops and give command](/media/crates.webp)](/media/crates.webp)

1. Open **Reward tables** in Quest Studio and create or select a table.
2. Enable **Openable loot crate**. A Player choice table cannot become a crate.
3. Set the crate name, its color and the enchantment glint.
4. Hand it out. Add a **Loot crate** reward to a quest, copy the **Give command** for an operator, or set the **Mob drops** weights for passive mobs, monsters and bosses.

Mob drops also need **Enable loot crate drops** in Studio, under **Server & sharing → Settings**. This is the `dropLootCrates` field of the [pack configuration](/reference/configuration).

::: warning Keep table names stable
The table name is the identifier stored in every crate. Crates already given to players stop opening after their table is renamed.
:::

### Quest givers

[![The quest giver library in Quest Studio with appearance, village and structure spawn rules and offered quests](/media/quest-givers.webp)](/media/quest-givers.webp)

Open the **NPC** tab of a quest to choose who offers it. **Manage quest givers** edits names, appearance, spawn rules and offered quests. A quest giver looks like a villager in a biome outfit or uses a player skin.

See the [quest giver reference](/reference/configuration#quest-givers-npcs) for the `npcs` format and spawn rules.

[Rewards and progression](/guide/rewards) · [Quest blocks](/guide/world-blocks) · [Use the tablet](/guide/tablet)
