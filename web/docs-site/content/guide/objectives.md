---
title: "Quest objectives (tasks)"
description: "Every built in Quest Plus objective type: break and place blocks, collect and submit items, kill mobs, fishing, advancements, dimensions, stats and more."
---

# Objectives

An objective is an action or state a player must satisfy. Open a quest in **Quest Studio**, go to **Objectives**, and choose a type. Every built in type is listed below.

![Objective action, target, count and conditions in the editor](/media/objectives.webp)

_Configure the event before choosing its target. The server validates both against the installed game._

## Blocks and interaction

| Objective       | Type             | What counts                                       |
| --------------- | ---------------- | ------------------------------------------------- |
| Break blocks    | `block_break`    | Break the selected block                          |
| Place blocks    | `block_place`    | Place the selected block                          |
| Use a block     | `block_interact` | Interact with the selected block                  |
| Look at a block | `observe_block`  | Keep the block in sight for the required duration |

## Items and inventory

| Objective       | Type              | What counts                                  |
| --------------- | ----------------- | -------------------------------------------- |
| Collect items   | `item_obtained`   | Pick up the selected item                    |
| Craft items     | `item_craft`      | Craft the selected item                      |
| Smelt items     | `item_smelt`      | Obtain a result from smelting                |
| Use an item     | `item_use`        | Use the selected item                        |
| Consume an item | `item_consume`    | Eat, drink, or consume the item              |
| Catch an item   | `fish_item`       | Fish the selected item                       |
| Trade items     | `trade`           | Obtain an item through trading               |
| Hold items      | `inventory_count` | Have enough matching items in inventory      |
| Submit items    | `item_submit`     | Submit matching items without consuming them |
| Turn in items   | `item_turn_in`    | Submit and consume matching items            |

**Item matching:** Set an item ID, a tag beginning with `#`, or up to 32 alternative targets where supported. Compatible item objectives also offer component matching with `none`, `partial`, or `exact`.

## Creatures and fishing

| Objective            | Type                 | What counts                                       |
| -------------------- | -------------------- | ------------------------------------------------- |
| Defeat a creature    | `entity_kill`        | Kill the selected entity                          |
| Hook a creature      | `fish_entity`        | Fish an entity                                    |
| Catch any fish       | `questplus:fish_any` | Catch fish regardless of species                  |
| Tame an animal       | `tame_animal`        | Tame the selected animal                          |
| Breed animals        | `breed_animal`       | Breed the selected animal                         |
| Interact with an NPC | `npc_interact`       | Interact with the selected entity                 |
| Look at an entity    | `observe_entity`     | Keep an entity in sight for the required duration |

## Exploration and movement

| Objective         | Type              | What counts                        |
| ----------------- | ----------------- | ---------------------------------- |
| Visit a biome     | `biome_visit`     | Enter a matching biome             |
| Visit a structure | `structure_visit` | Reach a matching structure         |
| Enter a dimension | `dimension_enter` | Enter the selected dimension       |
| Reach coordinates | `location`        | Enter the specified X, Y, Z bounds |
| Travel a distance | `travel_distance` | Move the required distance         |

## Experience

| Objective        | Type               | What counts                              |
| ---------------- | ------------------ | ---------------------------------------- |
| Submit XP levels | `xp_submit_levels` | Turn in levels through the Submit action |
| Submit XP points | `xp_submit_points` | Turn in points through the Submit action |
| Hold XP levels   | `xp_levels`        | Reach the required current level         |
| Hold XP points   | `xp_points`        | Reach the required current XP total      |

Holding XP is different from submitting XP. Submission consumes the specified resource when accepted.

## Fluids, energy and integrations

| Objective       | Type              | What counts                                     |
| --------------- | ----------------- | ----------------------------------------------- |
| Submit fluid    | `fluid_submit`    | Turn in a fluid through a supported integration |
| Submit energy   | `energy_submit`   | Turn in energy through a supported integration  |
| Transfer fluid  | `fluid_transfer`  | Receive a fluid transfer event                  |
| Transfer energy | `energy_transfer` | Receive an energy transfer event                |

**Requires integration:** Quest Plus does not automatically read every modded tank, pipe, or FE network. The relevant server side mod must report a transfer or provide a supported submission handler. See [Java integration](/reference/java-api).

## Player state and special objectives

| Objective            | Type                   | What counts                                |
| -------------------- | ---------------------- | ------------------------------------------ |
| Hold a stage         | `stage`                | Have the selected progression stage        |
| Reach a statistic    | `custom_stat`          | Reach the required Minecraft statistic     |
| Earn an advancement  | `advancement_complete` | Complete the selected advancement          |
| Meet equipment rules | `equipment_check`      | Wear or hold the required gear             |
| Die                  | `player_death`         | Register a player death                    |
| Complete manually    | `manual`               | Finish through an authorized manual action |

**Equipment:** Use the equipment controls to choose armor slots, items, or supported enchantment requirements. **Information quests** can instead require acknowledgement, with no gameplay objectives.

## Combine objectives

| Mode       | Behavior                                        |
| ---------- | ----------------------------------------------- |
| `all`      | Complete every required objective               |
| `any`      | Complete any required objective                 |
| `sequence` | Complete required objectives in order           |
| `at_least` | Complete a chosen number of required objectives |

Mark a step **Optional** so it does not block quest completion. For `at_least`, enter the required objective count. A quest can contain up to 256 objectives.

Use **Conditions** for equipment, location, biome, dimension, effects, health, sneaking, custom context, and quest prerequisites. Enable **Progress before unlock** if steps may advance before the quest becomes available.

## Example: mine 32 stone blocks

```json5
{
	id: "mine_stone",
	event: "block_break",
	target: "minecraft:stone",
	count: 32,
}
```

This is one objective inside a quest, not an entire quest pack.

For a custom event such as `example:machine_complete`, see [Report events from another mod](/reference/java-api). If an objective is not progressing, use `/questplus diagnose <quest>` and confirm the target and conditions.

## Test the chosen event

Use survival or adventure mode. Test the actual action named by the objective, including its tool, location and equipment conditions. For `inventory_count`, removing items can lower the current count. It is not a lifetime collection counter.

A submission requires the player's explicit action. `item_submit` checks matching items without consuming them, while `item_turn_in` consumes accepted items. Failed submissions must leave the player's resources intact.

[First quest walkthrough](/guide/first-quest) · [Objective troubleshooting](/guide/troubleshooting#an-objective-does-not-advance)
