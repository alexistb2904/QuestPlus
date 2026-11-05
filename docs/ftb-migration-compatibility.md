# FTB Quests migration compatibility — Quest Plus Studio (experimental)

Use Quest Plus Studio's Import ZIP / Import Folder action on an FTB Quests 26.x export containing `chapters/*.json5`. The editor **downloads a migration report before asking whether to import partial results**. Preserve the original pack.

### Migrated objectives

| FTB task | Quest Plus objective | Notes |
| --- | --- | --- |
| `kill` | `entity_kill` | Requires a valid namespaced entity ID |
| `biome` | `biome_visit` | Exact biome ID |
| `structure` | `structure_visit` | Exact structure ID |
| `dimension` | `dimension_enter` | Exact dimension ID |
| `stat` | `custom_stat` | Server-tracked Minecraft custom statistic |
| `item` with `consume_items:true` | `item_turn_in` | Requires player submission; removes actual inventory items |
| `item` with `consume_items:false` | `inventory_count` | Uses live inventory state; **warning** because original FTB manual hand-in behavior may differ |
| `checkmark` | `manual` | Requires manual confirmation; no scripted action |
| `location` | `location` + bounds/dimension conditions | Coordinates and radius 1–128 |

Vanilla item/XP/XP-level rewards can be translated when the source provides a valid, unambiguous item ID or amount. Item data components and NBT are deliberately **not discarded silently**: the corresponding task is rejected or the reward is flagged.

### Preserved structure

Chapter name → Quest Plus category, quest coordinates, basic English title/subtitle, ID mappings, prerequisite links when every referenced quest was converted, and basic task grouping (`all` or `any`). Dependencies are never omitted in order to make a quest easier; if a required prerequisite was not imported, dependent quests are skipped.

### Not converted yet

Advancement, fluid, energy, Game Stage, observation, nontrivial item-component rules, nested/choice/command/loot-table rewards, translations stored outside chapters, unsupported UI decorations, and older SNBT packs. Any quest with an unsupported required objective is skipped entirely. Unsupported rewards may be omitted **only with a warning**.

### Validation and security

- Source JSON5 is parsed as data; no source script is executed.
- Unknown IDs, unsupported mechanics and dependencies produce explicit compatibility warnings.
- The report contains original FTB ID mappings and warnings, which may be useful for debugging. Review it before publishing.
- Check the converted pack inside a *test world*, especially when migrating a server with existing player progress.
- Use `npm run typecheck && npm test && npm run build` in `web/editor` and `./gradlew build integrationTest` on Java 25, then perform in-game multiplayer tests before declaring parity.

The converter is **not** an equivalent FTB Quests interpreter; it is a conservative migration assistant.
