# Quest Plus — TODO Codex handoff (P0 → P2)

> Scope: stacked PR #29 (FTB migration) and #27 (modpack authoring). The user requested merging both while retaining an explicit unfinished-work backlog. **Merged does not mean production-ready.**

## P0 — correctness / security (do before shipping to a real multiplayer world)

- [ ] **Run and fix CI**: Java 25 / Fabric Minecraft 26.3 `./gradlew build integrationTest`, then `cd web/editor && npm ci && npm run typecheck && npm test && npm run build`. No successful run was witnessed during the connector-only implementation. Fix compile failures, failing tests and old tests affected by new enums, then rerun.
- [ ] **Test reward choice end-to-end**, especially `/questplus choose <quest> <1-based-option>` → async entitlement save → `/questplus claim`; verify storage failure, disconnect, replay protection, multiple reward cycles, team entitlements and two choices in one reward list. The current command chooses the first unresolved choice reward in the earliest entitlement. Design an explicit choice-index protocol for multiple choices; block ambiguous cases until handled.
- [ ] **In-tablet selection UI**: render options from the *personal entitlement* (not just quest definition), send a dedicated authenticated client-to-server choice packet or a safe server-command callback, await persisted acknowledgement, then enable Claim. No command concatenation from untrusted client strings.
- [ ] **Item hand-in crash safety**: inventory consumption + player progress persistence are not atomic. Refactor to a persisted transaction/idempotency record, durable reservation and recovery on crash / write failure. Test full inventory, tags, duplicate packets, team play, server restart, logout during write and reentrancy. Do not claim exactly-once semantics until verified.
- [ ] **Rewards reservation recovery**: reserved claims after a partial claim/fault need an audited admin recovery route and tests; vanilla inventory and progress JSON cannot atomically commit as currently implemented.
- [ ] **GameTest/real-world smoke tests** on disposable world with at least 2 players, modded items and fluids; verify JSON5 Quest Hub publish, backup, rollback and malicious/oversized pack rejection.
- [ ] **Review `QuestRuntime.chooseReward` persistence**: `persistCurrent` uses async storage, and a race may be possible between two updates of the same user; verify `busy` lifecycle, player/team snapshots and error reporting, inspect invalid choice data and `claim` blocking.
- [ ] **Authoring/engine parity**: validate allowed event IDs, state/action quantities, conditions and rewards on both sides, unknown IDs and namespaced extension descriptors. Add tests that roundtrip Quest Studio export to actual server `QuestRegistry`.

## P1 — modpack features

- [ ] **Mod integrations**: `fluid_transfer` counts mB and `energy_transfer` counts FE only if a *trusted server mod* calls `QuestPlusApi.reportFluidTransferred` / `reportEnergyTransferred` after a real successful transfer. Write specific compatibility adapters and regression tests for each supported tech mod. Avoid spoofed or repeated transfer credit. Never silently count stored energy as transferred energy.
- [ ] **Look-at / observation objective**: design tick-based server-authoritative raycasts, required dwell time, rate limits, dimension checks, block/entity targets and event deduplication.
- [ ] **GameStages and other mod bridge objectives**: explicitly optional dependencies; no hard link required when integration mod absent. Version-gate adapters.
- [ ] **Reward table sharing**: named reusable pools, reference integrity and recursion/cycle prevention, reward option limits, predictable snapshot on completion, authored Studio UI.
- [ ] **Items with Data Components**: design safe exact/partial predicates for objectives, submissions and rewards; do not silently strip enchanted/renamed/charged metadata on migration or hand-in. Test registry reloads and persistence.
- [ ] **Extension forms**: conditions and rewards have metadata-driven fields; add objective field forms, target picker integration, type-aware defaults, stronger client-side validation and backwards compatibility. Keep execution and validation on the trusted server.
- [ ] **Migration of existing player progress when quest IDs/steps change**: fingerprinted migration preview, backup, explicit admin acceptance, integrity validation and rollback.

## P2 — polish/migration

- [ ] **FTB legacy SNBT**, chapter groups, external translated text, decorations, item component predicates, nontrivial dependency quorums, third-party tasks/rewards, external/named loot/reward tables; import as warning+skip when semantics cannot be preserved. No drop of required conditions. Include source-ID maps and blocked quest counts in report.
- [ ] **FTB import report UI**: report downloadable JSON is available; add an inline severity/selections list, confirm destructive replacement, preserve source backups, a comparison preview and rollback if publish fails.
- [ ] **Bulk authoring**: current editor supports visibility, category, icon and additive XP/item rewards, with undo. Add objective transformation, per-quest preview, large-selection virtualization, multi-select on graph, copy/paste across categories, bulk tags and conflict diagnostics.
- [ ] **Portable templates**: current personal JSON template library is browser-local. Add optional project-scoped reusable templates; version migration, localization, icon/media portability and import review.
- [ ] **Studio internationalization**: translate all newly added action labels, dialogs and help to configured primary languages; keyboard and touch accessibility, responsive behavior.
- [ ] **Document test matrices** for Fabric 26.3 dedicated server + Iris/shader combinations, multiplayer, registry-mod differences, server editor sessions and save reload.

## Known implementation markers

- `server/src/main/java/dev/questplus/fabric/QuestRuntime.java`: `chooseReward`, `submitItem`, `claim` — async persistence, delivery, crash safety.
- `server/src/main/java/dev/questplus/core/RewardTables.java`: `validateChoice`, `resolve`, `choose`; currently resolves the first undecided choice via command.
- `client/src/main/java/dev/questplus/fabric/client/QuestScreen.java`: add a player-specific selection UI.
- `server/src/main/java/dev/questplus/fabric/api/QuestPlusApi.java`: adapter contract for trusted fluids/FE events.
- `web/editor/src/components/QuestDetails.tsx`: choice reward authoring; `Inspector.tsx`: condition descriptors.
- `web/editor/src/core/ftb-import.js`: conservative JSON5 conversion; `useStudio.ts`: migration/report preview.
- `web/editor/src/components/BulkEditor.tsx` / `TemplateLibrary.tsx`: more authoring workflows.

**Do not remove TODOs solely because the PRs were merged. Mark each item only after implementation, tests and verification.**
