# QuestPlus: three runtime responsibilities (web, client, server) and two mod loaders

## Source layout

- `web/editor/`: React/TypeScript quest authoring, offline ZIP workflows and connected editing.
- `web/api/`: same-origin HTTPS web runtime, approval sessions, immutable pack storage, upload/ack relay.
- `mod/client/`: client-only shared code, tablet renderer, client models, resources and client mixins.
- `mod/server/`: authoritative engine, data validation, persistent progress, commands, networking and server mixins.
- `mod/fabric/`: Fabric glue only (entrypoints, `fabric.mod.json`, Fabric API events, payloads, Transfer API).
- `mod/neoforge/`: NeoForge glue only (`@Mod` entrypoint, `neoforge.mods.toml`, events, payloads, capabilities).

Quest Plus ships **one Fabric JAR and one NeoForge JAR**, each with client and server code.
Every target compiles `mod/server` and `mod/client` plus its loader's glue, once per Minecraft
version in `mod/versions/` (see `mod/versions/README.md`). Shared code never imports a loader API:
it calls `dev.questplus.platform.Loader`/`Platform`/`ClientPlatform`, which `mod/fabric` and
`mod/neoforge` each implement with identical signatures. Version-specific Minecraft calls belong in
`dev.questplus.compat` adapters, NeoForge-version-specific calls in `versions/<mc>/neoforge`.
Package names (`dev.questplus.fabric...`), mod IDs, payload IDs and mixin package names are unchanged.

## Rules

- Never trust a quest update solely because it came from a browser. The Minecraft
  server must validate the complete pack and perform an atomic reload or rollback.
- A Hub session URL is not an authorization token. Confirmation requires the
  initiating operator in Minecraft. Keep publish tokens in memory.
- No player saves, reward ledgers, or server session credentials may be sent to the browser.
- Browser data/pack links must never permit remote console commands.
- Keep all mutable game state on the Minecraft server thread.
- Keep client rendering code out of the dedicated-server execution path.
- Never import Fabric or NeoForge APIs in `mod/server` or `mod/client`. Add a `Platform` method to
  both loaders instead. `verifySharedBytecode` and `verifyPlatformApi` enforce this.
- Declare only stable NeoForge builds (no `-beta`), and only Minecraft versions whose tests ran.
- Keep the React editor usable offline without an API connection.
- Add tests for any new protocol, registry, permission, rollback or release changes.
- Run `cd mod && ./gradlew build` (every target on both loaders, including server GameTests),
  `cd web/editor && npm test && npm run typecheck && npm run build`, and `cd web/api && npm test`
  on a checkout before merging.
- Do not claim live Minecraft client/server parity until both loaders' integration tests run
  (Fabric `runClientGameTest`, NeoForge `clientSmokeTest`, both need a window).

## Existing work

Preserve existing quest IDs, schemaVersion, progression/rewards and game compatibility.
Read `docs/architecture.md` and `web/api/README.md` before editing any
cross-process protocol. Do not rewrite another subsystem as part of a cosmetic refactor.
