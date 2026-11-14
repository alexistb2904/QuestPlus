---
title: "Quest objectives (tasks)"
description: "Every built in Quest Plus objective type: break and place blocks, collect and submit items, kill mobs, fishing, advancements, dimensions, stats and more."
---

# Objectives

An objective is an action or state a player must satisfy. Open a quest in **Quest Studio**, go to **Objectives**, and choose a type. Every built in type is listed below.

![Objective action, target, count and conditions in the editor](/media/objectives.webp)

_Configure the event before choosing its target. The server validates both against the installed game._

## Blocks and interaction

| Objective           | Type             | What counts                                                                                           |
| ------------------- | ---------------- | ----------------------------------------------------------------------------------------------------- |
| Break blocks        | `block_break`    | Blocks the player breaks. Explosions, pistons and other players do not count                          |
| Place blocks        | `block_place`    | Blocks the player places by hand. Dispensers and commands do not count                                |
| Right-click a block | `block_interact` | Right-clicks the block reacts to (open a chest, press a button). A click that does nothing is ignored |
| Look at a block     | `observe_block`  | Uninterrupted gaze from 6 blocks or closer. Looking away starts over. 20 ticks = 1 second             |

## Items and inventory

| Objective               | Type              | What counts                                                                                          |
| ----------------------- | ----------------- | ---------------------------------------------------------------------------------------------------- |
| Pick up items           | `item_obtained`   | Items picked up from the ground only                                                                 |
| Craft items             | `item_craft`      | Items the player takes out of a crafting table or the inventory grid                                 |
| Take from a furnace     | `item_smelt`      | Items the player takes out of a furnace, smoker or blast furnace. Hoppers and campfires do not count |
| Use an item             | `item_use`        | Right-clicks with the item that do something: throw, place, start eating, light a fire               |
| Eat or drink            | `item_consume`    | Food, potions or milk the player finishes                                                            |
| Fish up items           | `fish_item`       | Items reeled in with a fishing rod: fish, junk and treasure                                          |
| Buy from a villager     | `trade`           | Trades whose result is the item. One trade counts 1, whatever the stack size                         |
| Have in inventory       | `inventory_count` | Items in the inventory or offhand right now, from any source. Worn armor is not counted              |
| Show items (kept)       | `item_submit`     | The player presses Submit with the full amount. Checked once, locked in, items kept                  |
| Hand over items (taken) | `item_turn_in`    | The player presses Submit and the items are taken. Partial deposits add up                           |

**Pick up, craft or have:** these three look alike but count different things.

- `item_obtained` only counts items picked up from the ground: mob drops, mined blocks, items thrown by another player. Items taken from a chest, crafted, smelted or given by command never count. An item the player drops and picks up again counts again. Use it for raw resources gathered in the world.
- `item_craft` only counts what the player crafts at a crafting table or in the inventory grid. Smithing table, stonecutter, loom and Crafter results never count. In multiplayer, the player or their team must craft it: an item handed over by another player or team does not complete it. This is intended when crafting is the point of the quest, otherwise use `inventory_count`.
- `inventory_count` checks what the player has right now in the inventory or offhand, whatever its origin. Worn armor is not counted, so use `equipment_check` for gear to wear. The count drops if items leave the inventory. Use it for "find", "own" or "bring" quests, such as treasure from a chest.

**Have, show or hand over:** `inventory_count` is checked automatically every second and can go back down. `item_submit` waits for the player to press Submit, then locks the progress and leaves the items. `item_turn_in` takes the items.

**Use or eat:** `item_use` counts the right-click, so starting to eat counts even if the player stops. `item_consume` only counts food or drink actually finished.

**Item matching:** Set an item ID, a tag beginning with `#`, or up to 32 alternative targets where supported. Compatible item objectives also offer component matching with `none`, `partial`, or `exact`. To accept any fish, use `fish_item` on `minecraft:cod` and add salmon, pufferfish and tropical fish as alternatives.

## Creatures and fishing

| Objective              | Type             | What counts                                                                                    |
| ---------------------- | ---------------- | ---------------------------------------------------------------------------------------------- |
| Kill creatures         | `entity_kill`    | Kills by the player, their arrows or their tamed wolf, or shortly after the player hit it      |
| Hook a creature        | `fish_entity`    | Creatures pulled with the fishing rod hook. This is not fishing for items                      |
| Tame animals           | `tame_animal`    | Animals the player tames                                                                       |
| Breed animals          | `breed_animal`   | Babies born from animals of that species that the player fed                                   |
| Right-click a creature | `npc_interact`   | Right-clicks the entity reacts to (villager trades, milking a cow). Not linked to quest givers |
| Look at a creature     | `observe_entity` | Uninterrupted gaze from 6 blocks or closer. Looking away starts over                           |

Older packs that use `questplus:fish_any` still load. It was a duplicate of `fish_item` on the four vanilla fish, and the server and Quest Studio convert it automatically.

## Exploration and movement

| Objective         | Type              | What counts                                                                              |
| ----------------- | ----------------- | ---------------------------------------------------------------------------------------- |
| Visit a biome     | `biome_visit`     | Being in the biome. Already standing in it counts                                        |
| Visit a structure | `structure_visit` | Standing inside the structure. Being near it is not enough                               |
| Enter a dimension | `dimension_enter` | Being in the dimension. Already being there counts                                       |
| Reach a position  | `location`        | Standing inside the box around X, Y, Z                                                   |
| Travel a distance | `travel_distance` | Distance on foot, swimming, flying, riding, by boat or minecart. Teleports do not count |

## Experience

| Objective             | Type               | What counts                                                  |
| --------------------- | ------------------ | ------------------------------------------------------------ |
| Pay XP levels (taken) | `xp_submit_levels` | Levels taken when the player presses Submit. Payments add up |
| Pay XP points (taken) | `xp_submit_points` | Points taken when the player presses Submit. Payments add up |
| Have an XP level      | `xp_levels`        | Current level. Nothing is taken, spending levels lowers it   |
| Have XP points        | `xp_points`        | Current XP total. Nothing is taken                           |

## Fluids, energy and integrations

| Objective                | Type              | What counts                                                                                |
| ------------------------ | ----------------- | ------------------------------------------------------------------------------------------ |
| Hand over fluid (taken)  | `fluid_submit`    | Fluid drained from buckets or tanks in the inventory, or collected by a Submission Station |
| Hand over energy (taken) | `energy_submit`   | FE drained from batteries in the inventory, or collected by a Submission Station           |
| Fluid moved (mod API)    | `fluid_transfer`  | Only transfers another server mod reports through the Quest Plus API                      |
| Energy moved (mod API)   | `energy_transfer` | Only transfers another server mod reports through the Quest Plus API                       |

**Requires integration:** Quest Plus does not automatically read every modded tank, pipe, or FE network. The relevant server side mod must report a transfer or provide a supported submission handler. See [Java integration](/reference/java-api).

## Player state and special objectives

| Objective                   | Type                   | What counts                                                                           |
| --------------------------- | ---------------------- | ------------------------------------------------------------------------------------- |
| Have a stage                | `stage`                | Having the progression stage                                                          |
| Reach a Minecraft statistic | `custom_stat`          | The player's total since they first joined the world, so play before the quest counts |
| Earn an advancement         | `advancement_complete` | Having the advancement, even if earned before the quest                               |
| Wear or hold gear           | `equipment_check`      | Gear really worn or held, slot by slot, with optional enchantments                    |
| Die                         | `player_death`         | Player deaths, any cause                                                              |
| Manual confirmation         | `manual`               | The player clicks Acknowledge, or another mod sends the event                         |

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
