# Modpack authoring / FTB Quests parity — implementation status

This document tracks the **implemented subset** of the P0–P2 request. It is not a claim of full FTB Quests compatibility.

## Changes on `feat/modpack-authoring-ftb-parity`

- **Quest templates:** right-click in the Studio tree to create an empty quest, collect/craft/boss/exploration/equipment/sequence/timed/repeating/information quest or an XP-level goal. The original create quest/annotation/background entries remain available.
- **Registry browser:** searchable modded registry snapshot, using the existing approved Quest Hub server session; optional import of a `.json` registry snapshot for offline use. Selection inserts a matching ID into the first compatible objective or copies it to clipboard.
- **Quest and category JSON5:** Studio exports both quest and category definitions as `.json5` using the JSON5 npm parser; the Fabric server loads strict `.json` and `.json5` for those definitions. Server config still uses strict JSON. The approved Quest Hub publication flow validates JSON5 and retains the existing backups/rollback mechanism. Player saves remain unchanged JSON.
- **FTB Quests importer (preview/beta):** detects `chapters/*.json5`, converts only explicitly supported kill/biome/dimension objectives; all other tasks, rewards and dependencies are reported as unconverted. Import requires confirmation, and refuses projects without any convertible quests. It does **not** silently approximate item-consumption, loot tables, scripts or other unsupported mechanics. Older SNBT is not supported.
- **Editor extension descriptors:** trusted mods can register data-only metadata through `QuestPlusApi.registerEditorDescriptor`, published via the approved Quest Hub session. Studio lists custom events and creates form fields for custom rewards; objective extra fields and condition forms remain pending. See `docs/quest-studio-extensions.md`.
- **New gameplay mechanics:** server-authoritative XP-level objective `xp_levels` (target `questplus:levels`), and `experience_levels` reward alongside point-based experience. XP-level goal is a live state, not a cumulative level-gain counter.

## Still missing / not implemented

- Item submission and item consumption, cross-source acquisition tracking, advancements, fluid and energy tasks, observation and generic statistics.
- Choice/random/command/loot-table and item-component rewards; authoring those without runtime support is **not** safe.
- Flexible and optional task progression, visual reward-table editing and complete extension condition/objective form authoring.
- FTB full importer, bulk editing, project migrations and end-to-end compatibility tests.
- Complete verification of client UI, 26.3 dedicated server, modded items and two-client multiplayer.

## Development checks

Tests added in `web/editor/tests/authoring-parity.test.mjs` and `server/src/test/java/dev/questplus/core/QuestJson5Test.java` and `server/src/test/java/dev/questplus/fabric/api/QuestStudioDescriptorsTest.java`. Run:

```sh
cd web/editor
npm ci && npm run typecheck && npm test && npm run build
cd ../..
./gradlew build integrationTest
```

Build and integration test execution **has not been verified in the authoring environment for this branch**. Do not merge into `main` until the build, hub publication/rollback, FTB warning flow and live XP state have been validated.

## Safety notes

- Do not parse player saves as JSON5 or accept client-provided registry snapshots as authoritative gameplay state.
- Reject unsafe paths and maintain stable IDs when importing packs.
- Granting XP levels after reward reservation still uses the standard Quest Plus durability/ledger behavior and its documented crash limitations.
- FTB imports currently perform partial conversion only with explicit warnings and user confirmation. Keep original FTB exports as backups.
