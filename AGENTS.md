# QuestPlus — three runtime responsibilities: web, client, server

## Source layout

- `web/editor/`: React/TypeScript quest authoring, offline ZIP workflows and connected editing.
- `web/api/`: same-origin HTTPS web runtime, approval sessions, immutable pack storage, upload/ack relay.
- `mod/client/`: client-only Fabric code, tablet renderer, client models, resources and client mixins.
- `mod/server/`: authoritative Fabric engine, data validation, persistent progress, commands, networking and server mixins.

This is still **one Fabric JAR**, not two separately installed Fabric mods. Gradle compiles
both `mod/client/src/main/java` and `mod/server/src/main/java` in its main source set, once per
Minecraft target in `mod/versions/` (see `mod/versions/README.md`). Version-specific Minecraft or
Fabric calls belong in `dev.questplus.compat` adapters, never in shared code.
Minecraft entrypoints and mixin package names are unchanged.

## Rules

- Never trust a quest update solely because it came from a browser. The Minecraft
  server must validate the complete pack and perform an atomic reload or rollback.
- A Hub session URL is not an authorization token. Confirmation requires the
  initiating operator in Minecraft; keep publish tokens in memory.
- No player saves, reward ledgers, or server session credentials may be sent to the browser.
- Browser data/pack links must never permit remote console commands.
- Keep all mutable game state on the Minecraft server thread.
- Keep client rendering code out of the dedicated-server execution path.
- Keep the React editor usable offline without an API connection.
- Add tests for any new protocol, registry, permission, rollback or release changes.
- Run `cd mod && ./gradlew build`, `cd web/editor && npm test && npm run typecheck && npm run build`,
  and `cd web/api && npm test` on a checkout before merging.
- Do not claim live Minecraft client/server parity until Fabric integration tests run.

## Existing work

Preserve existing quest IDs, schemaVersion, progression/rewards and game compatibility.
Read `docs/architecture.md` and `web/api/README.md` before editing any
cross-process protocol. Do not rewrite another subsystem as part of a cosmetic refactor.
