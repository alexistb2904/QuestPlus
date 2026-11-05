# Quest Plus — Modpack authoring parity status (WIP)

Scope: `feat/modpack-authoring-ftb-parity` / draft PR #27. This branch is **not a complete FTB Quests replacement** and is not certified for production servers.

## Implemented on the branch

### P0 gameplay foundations
- Built-in item objectives include inventory possession, item pickup, and server-authoritative submissions: `item_submit` (verify without withdrawal) and `item_turn_in` (consume deposited items). The interactive tablet and `/questplus submit <quest> <objective>` route through the server.
- Additional server-backed objectives: XP levels, Minecraft custom statistics and completed advancements. Items, blocks, entities, biomes, structures, dimensions, equipment/enchantments and existing gameplay events remain supported.
- Reward kinds: item, XP points, XP levels, weighted random, all-table, empty-weight loot, native advancement grant, and trusted operator-enabled server command (disabled by default), and safe server-side player message rewards. Weighted selections are resolved at completion and stored as concrete entitlements, avoiding rerolls on reconnect.
- Quest progression: optional tasks, `at_least` quorum, sequential / any / all, tiers, timers, no-death and progress-before-unlock (flexible prerequisites).
- Includes per-objective operator-only `/questplus diagnose <quest>` for debugging.

### P1 authoring experience
- Quest Studio right-click templates including items, crafting, exploration, equipment, sequence, timed, repeatable, information, stats and advancements. Authors can now save a selected quest as a **personal reusable template** and create another from the right-click menu. Custom templates live in local browser storage (not embedded in exported packs), and clones receive a new quest ID and drop source prerequisites.
- Mod Registry Browser reads approved Quest Hub registry snapshots and accepts offline JSON snapshots, including statistics and advancements; text-based custom modded IDs remain supported.
- Extensions can publish data-only editor descriptors through the approved Hub. Studio renders registered event labels and typed reward fields.
- Studio exports JSON5 quests/categories; the Fabric loader still accepts existing JSON quests/categories. World config and player progress remain strict JSON.
- FTB Quests 26.x JSON5 import preview safely converts supported item possession, crafting and hand-ins, kills, biome/structure/dimension/position goals, custom statistics, checkmarks, XP levels and advancements. Source chapter defaults for consumed items are considered. Task Screen-only and unsupported component-matching semantics are rejected rather than approximated. Simple item/XP/advancement rewards and ALL/ANY dependencies are mapped; unsupported task mechanics block that quest and its transitive dependents. Unsupported rewards are explicitly reported. Original sources are not modified.

### Validation and safety
- JUnit tests added for stats, advancements, reward schemas, progression and JSON5; Node tests cover built-in and saved custom templates, random/loot/all table validation, command reward opt-in, and FTB conversion.
- CI config is in `.github/workflows/authoring-checks.yml`. Passing CI has **not been observed** and cannot be claimed from committed test files.
- Reward claims retain their existing server-side persistence and reservation safeguards. They are not a fully atomic transaction with the vanilla inventory/advancement system.
- Server code never evaluates scripts from imported packs. Command rewards are opt-in by the world administrator, and third-party extension Java code is trusted server code, not sandboxed data.

## Remaining P0–P2 work

- Native fluid/energy submissions, general observation (look-at) objectives, GameStages and other cross-mod task bridges.
- Live server references to reusable named reward tables and item Data Component fidelity. Studio now supplies portable named table snapshots, not live references.
- Full extension-driven condition/objective forms, beyond current event listing and custom reward fields.
- Bulk/multi-selection editing, import/export of personal templates across browsers, bulk editing, migration tools for updated player progress, complete in-game edit workflows.
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

## Additional authored safety checks (2026-11-05)

- Command reward Studio validation rejects newline, carriage return and NUL; command scripts cannot be executed through a client packet. The world config must enable trusted commands explicitly.
- FTB ItemTask `only_from_crafting` uses the crafting event, `consume_items` uses the consuming hand-in, and normal nonconsuming items use live inventory checks. Task Screen-only tasks are not automatically converted to a different semantic event.
- Personal templates are saved to localStorage and do not join exported ZIP packs. There is a 32-template and 500k-character storage limit; they do not copy old quest dependencies.
- More comprehensive JUnit and Node tests are committed but still require execution. A real dedicated-server run and multiplayer smoke test remain blockers.

## Verified interface integration — 2026-11-05

- Player choices are usable in the real native tablet, using a connection-authenticated packet with personal grant token, stable slot and option. Multiple choices, cycles, teammate isolation, save acknowledgment, logout during writing and replay refusal have server/client tests.
- Studio uses one graphical reward editor for normal rewards and tiers. It supports reordering, weights/rolls/empty outcomes, percentages, local simulation, option labels and translated descriptions, item/advancement pickers, extension reward fields and portable named snapshots in project metadata. Nested pools and commands inside pools remain forbidden.
- Potential rewards and concrete server results have separate tablet views. Payloads are allowlisted; extension data and server commands stay private. Delivered receipts and empty outcomes are bounded per quest, while claim keys retain replay protection.
- Optional submissions in sequences, tag/offhand/full-inventory checks and current-tier verification are covered. Manual interactions now target one declared objective, including custom markers; commands share the same server path.
- The API now relays JSON5 definitions, keeps config/metadata strict JSON, and preserves Studio metadata on publication/reload. Docker, CI and Windows launchers install the parser dependency.
- Local evidence: 161 Java tests, six dedicated-server GameTests, 129 Studio tests, 29 API tests and the real client GameTest. Screenshots and validation limits: [reward review](reviews/reward-integration/README.md).

This closes the interface gaps above, not the inventory/JSON crash-atomicity or reserved-claim recovery gaps. See the [updated backlog](CODEX_TODO_P0_P2.md) before production certification or merging a release.


## Continuation FTB — 5 novembre 2026

Observation, stages/toasts, dépendances commencées/quorums, réglages de présentation, groupes de chapitres, tables nommées référencées et paramètres d’objectifs d’extension sont désormais raccordés au serveur et aux interfaces. L’import SNBT inclut les séparateurs multilignes FTB, une revue avant remplacement et l’undo ; les modèles peuvent voyager dans le projet.

Les remises ont un journal coordonné avec un marqueur vanilla et une reprise contrôlée ; les claims ambigus ont une résolution OP auditée. Ces changements remplacent les constats historiques ci-dessus, sans constituer une garantie de coupure électrique ni de parité complète. [État courant et écarts](ftb-quests-gap-analysis.md), [preuves de tests](reviews/ftb-parity/README.md).
