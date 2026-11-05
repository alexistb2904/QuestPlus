# Quest Plus — Modpack authoring parity status (WIP)

Scope: `feat/modpack-authoring-ftb-parity` / draft PR #27. This branch is **not a complete FTB Quests replacement** and is not certified for production servers.

## Implemented on the branch

### P0 gameplay foundations
- Built-in item objectives include inventory possession, item pickup, and server-authoritative submissions: `item_submit` (verify without withdrawal) and `item_turn_in` (consume deposited items). The interactive tablet and `/questplus submit <quest> <objective>` route through the server.
- Additional server-backed objectives: XP levels, Minecraft custom statistics and completed advancements. Items, blocks, entities, biomes, structures, dimensions, equipment/enchantments and existing gameplay events remain supported.
- Reward kinds: item, XP points, XP levels, weighted random, all-table, empty-weight loot, native advancement grant, and trusted operator-enabled server command (disabled by default). Weighted selections are resolved at completion and stored as concrete entitlements, avoiding rerolls on reconnect.
- Quest progression: optional tasks, `at_least` quorum, sequential / any / all, tiers, timers, no-death and progress-before-unlock (flexible prerequisites).
- Includes per-objective operator-only `/questplus diagnose <quest>` for debugging.

### P1 authoring experience
- Quest Studio right-click templates including items, crafting, exploration, equipment, sequence, timed, repeatable, information, stats and advancements.
- Mod Registry Browser reads approved Quest Hub registry snapshots and accepts offline JSON snapshots, including statistics and advancements; text-based custom modded IDs remain supported.
- Extensions can publish data-only editor descriptors through the approved Hub. Studio renders registered event labels and typed reward fields.
- Studio exports JSON5 quests/categories; the Fabric loader still accepts existing JSON quests/categories. World config and player progress remain strict JSON.
- FTB Quests 26.x JSON5 import preview safely converts supported item hand-ins, kills, biome/structure/dimension/position goals, custom statistics, checkmarks and advancements. Simple item/XP/advancement rewards and ALL/ANY dependencies are mapped; unsupported task mechanics block that quest and its transitive dependents. Unsupported rewards are explicitly reported. Original sources are not modified.

### Validation and safety
- JUnit tests added for stats, advancements, reward schemas, progression and JSON5; Node tests cover templates and FTB conversion.
- CI config is in `.github/workflows/authoring-checks.yml`. Passing CI has **not been observed** and cannot be claimed from committed test files.
- Reward claims retain their existing server-side persistence and reservation safeguards. They are not a fully atomic transaction with the vanilla inventory/advancement system.
- Server code never evaluates scripts from imported packs. Command rewards are opt-in by the world administrator, and third-party extension Java code is trusted server code, not sandboxed data.

## Remaining P0–P2 work

- Native fluid/energy submissions, general observation (look-at) objectives, GameStages and other cross-mod task bridges.
- Player-selected choice rewards (with explicit selection, durable delivery), reusable named reward tables and item Data Component fidelity.
- Full extension-driven condition/objective forms, beyond current event listing and custom reward fields.
- Bulk/multi-selection editing, comprehensive reusable quest templates across projects, migration tools for updated player progress, complete in-game edit workflows.
- Full FTB Quests import: legacy SNBT, translations, chapter groups, item component predicates, external reward tables and every third-party task/reward type.
- Real multiplayer, dedicated server load tests, JSON5 Hub publication/rollback tests, all reward paths and compatibility across third-party mods.
- Refactor or stress-test the hand-in transaction path for abrupt crashes and save failures before production use.

## Required verification before merging

```bash
cd web/editor
npm ci
npm run typecheck
npm test
npm run build
cd ../..
./gradlew build integrationTest
```

Test on a disposable Minecraft 26.3 world with two clients, modded registries, invalid JSON5 files, Quest Hub rollback, submissions, advancement/command/random rewards and restart/reconnect flows. Keep PR #27 **draft**, and do not merge `main` until green CI and real-world smoke tests.
