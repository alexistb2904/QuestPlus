# Objectives

An objective defines what a player must do. Add it from the quest's **Details** panel.

| Goal | Event |
| --- | --- |
| Break a block | `block_break` |
| Obtain an item | `item_obtained` |
| Craft an item | `item_craft` |
| Hand in items | `item_submit` |
| Defeat a mob | `entity_kill` |
| Visit a biome | `biome_visit` |
| Check equipment | `equipment_check` |
| Manual activation | `manual` |
| Track fluids or FE | `fluid_transfer`, `energy_transfer` |

Choose `all`, `any`, `sequence` or `at_least` to combine objectives. Optional objectives do not block completion.

Set biome, dimension, health, equipment and other conditions in the editor. The server checks them when the action happens.

**Important:** Holding an item does not submit it. Use the **Submit** action for hand-ins.

To count a custom mod action, [register a Java event](/reference/java-api). For debugging use `/questplus diagnose <quest>`.
