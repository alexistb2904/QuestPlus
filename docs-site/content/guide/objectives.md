# Objectives, validation and prerequisites

Quest progress is computed by the server from gameplay events and explicit player actions. A display change in Quest Studio does not create a new server event.

## Built-in objective families

| Family | Examples of event IDs |
| --- | --- |
| Blocks | `block_break`, `block_place`, `block_interact` |
| Items | `item_obtained`, `item_craft`, `item_smelt`, `item_use`, `item_consume`, `inventory_count` |
| Submissions | `item_submit`, `item_turn_in`, `xp_submit_levels`, `xp_submit_points`, `fluid_submit`, `energy_submit` |
| Entities | `entity_kill`, `tame_animal`, `breed_animal`, `npc_interact`, `fish_entity` |
| World | `biome_visit`, `structure_visit`, `dimension_enter`, `location`, `travel_distance` |
| Player state | `equipment_check`, `xp_points`, `xp_levels`, `player_death`, `custom_stat`, `advancement_complete` |
| Other | `observe_block`, `observe_entity`, `stage`, `manual`, `fluid_transfer`, `energy_transfer` |

Select the objective type in the Details pane, enter its resource ID or tag and choose a target count. Use **Mod Registry Browser** for registered IDs from the connected server or an imported offline registry snapshot.

**Possession**, **pickup**, **crafting** and **consuming a hand-in** have different meanings. `inventory_count` is a current inventory snapshot rather than a lifetime total.

## How objectives combine

| Mode | Behavior |
| --- | --- |
| `all` | Complete every required objective |
| `any` | Complete any one qualifying objective |
| `sequence` | Complete the objectives in order |
| `at_least` | Reach a minimum number of qualifying objectives |

An objective can be optional. A sequential objective needs its turn before it can advance. The editor exposes these settings and validates combinations.

## Prerequisites and conditions

Prerequisite expressions can reference other quest IDs and compose conditions with `and`, `or` and `not`. Started-quest and minimum-required dependency rules are also supported in the current editor and loader.

Objective conditions include biome, dimension, held tool, equipment, effect, health, sneaking, position and extension-defined context. A condition must be true when the relevant event occurs.

Example:

```json5
{
  id: 'mine_stone',
  event: 'block_break',
  target: 'minecraft:stone',
  count: 32,
  conditions: { sneaking: true }
}
```

## Manual and submission tasks

For a manual action, use the tablet's interaction control or `/questplus trigger <quest>`. For hand-ins, use the tablet's submit action or `/questplus submit <quest> <objective>`.

The server applies the actual withdrawal or state change. Setting `item_turn_in` in a file does not make ownership alone count as a submission.

## Test before publishing

Use `/questplus diagnose <quest>` as an operator to inspect the required objectives, live counters and conditions. Test with survival players on a disposable world, especially for modded registries and fluid/energy integrations.

See [capabilities and limits](/reference/limitations).
