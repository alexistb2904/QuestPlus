# QuestPlus — gear, categories, notifications and rewards

## Gear conditions

The Studio supports `questplus:gear` on an `equipment_check` objective or
as a modifier on another event. Requirements are exact server-validated
item IDs, slots (`inventory`, `head`, `chest`, `legs`, `feet`,
`mainhand`, `offhand`) and optional enchantment minimums:

```json
{"questplus:gear":{"items":[{"slot":"mainhand","item":"minecraft:diamond_pickaxe",
  "enchantments":{"minecraft:efficiency":5,"minecraft:unbreaking":3}}]}}
```

The Studio gear builder has full-set armor presets, per-item browser, modded ID
entry and per-item enchantment/level editing. Minecraft resolves the same
predicate against a server-authoritative snapshot for equipment_check and
normal event conditions; the tablet/HUD show a localized human-readable
summary instead of raw JSON.

## Maintained default categories

New installations and new Studio projects contain 16 maintained categories
in this order: Homestead, Professions, Quarries, Wildlife, Hunts, Expeditions,
Ocean, Archaeology, Engineering, Curiosities, Nether, Endgame, Equipment,
Milestones, Trials, Contracts.

Combat, Exploration, Mining and Survival sample chapters are no longer
installed by default. During startup of an existing world, QuestPlus deletes
them **only** when all associated category/quest files exactly match bundled
legacy samples. Any edited sample is retained, along with its entire chapter,
to prevent player-authored data loss. Legacy data remains as test fixtures.

## Notifications and rewards

Completed quests are automatically unpinned. Progress and single-objective
notifications are suppressed; only quest unlocks and completions produce
toasts. Category icons show blue unread-unlock markers or gold claimable
reward markers (rewards take precedence). Opening a category marks new
unlocks as seen.

The static bottom-right chest icon claims rewards from all categories.
This is **one authorized network request**. The server performs durable
reservations sequentially, stopping if inventory or storage becomes
unavailable; this avoids flooding the API and double reward delivery.

## 3D screen brightness

At 100% brightness, the tablet preserves the original screen colors without
a white highlight overlay. Lower brightness levels dim toward black. This
applies only to the 3D screen atlas, not the 2D GUI or world.
