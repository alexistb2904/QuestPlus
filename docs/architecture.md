# QuestPlus — architecture (web / client / server)

## Physical layout

```text
QuestPlus/
├── web/
│   ├── editor/         React 19 + Vite + TypeScript (Quest Studio)
│   └── server/         Node 22 HTTP API, built web assets, session relay, packs
├── client/
│   └── src/
│       ├── main/java/  QuestPlusClient, native tablet, client models
│       ├── main/resources/assets/questplus/ and client mixins
│       └── test/, gametest/
├── server/
│   └── src/
│       ├── main/java/  QuestPlusMod, QuestRuntime, QuestNetwork, core, saves
│       ├── main/resources/  fabric.mod.json, server mixins, default quests
│       └── test/, gametest/
└── build.gradle       one Java 25 / Fabric 26.3 universal JAR
```

**Three responsibility zones, two deployed units:** the web service and
the Minecraft Fabric JAR. The Minecraft client and dedicated server both
consume the **same** JAR, with Fabric's existing `client` and `main`
entrypoints. Do not add a second independently versioned client mod.

## Responsibilities and trust boundaries

| Zone | Owns | Must not own |
| --- | --- | --- |
| web/editor | Quest project UI, export/import ZIP, draft editing | Operator permissions, Minecraft progress |
| web/api | React static hosting, HTTP APIs, temporary session state, versioned pack storage | Minecraft registry validation, player records |
| client | Game tablet rendering, input, local visual settings | Quest definitions as authority, file writes |
| server | Quest registry, reload, permissions, player saves, rewards, owner approval | Arbitrary browser script execution |

`QuestNetwork` sends visible quest projections to clients and is already
invalidated by `QuestRuntime.reload`. `QuestHubService` is the outbound
Minecraft-side HTTP integration; its name reflects the protocol but is part
of **server/**, not an independently deployed Hub. Neither it nor the Node
web service may modify client-only visual state.

## Connected editing protocol

1. An operator executes `/questplus editor` on the Minecraft server.
2. The **server** exports its quest JSON definitions and installed registry
   identifiers (items, blocks, enchantments, biomes, etc.) to the web service
   over an outbound HTTPS request authenticated with a server-only API key.
3. The **browser** joins a temporary session URL and requests authorization.
   The **original in-game operator** compares a six-digit challenge and
   explicitly approves it. A bearer token is issued to that browser.
4. Quest Studio loads definitions and the modded registry. Autosaves remain
   local; publication requires explicit confirmation.
5. Publication is queued by the web API. The Minecraft server polls
   asynchronously and validates all files, including modded references.
6. Definitions are staged, snapshotted and replaced. On reload failure the
   previous definitions are restored. Saves and reward claims stay separate.
7. Acknowledgment updates the browser and QuestNetwork refreshes online clients.

**An editor link is never a permission.** Unlisted pack links only grant
read access to the pack and cannot issue commands. Server imports require
OP permissions and a second in-game confirmation.

## Deployment

Only the web service needs HTTPS/public ingress. The Minecraft server opens
outbound HTTPS requests and requires no new public port. In production,
`web/api/Dockerfile` performs a React build and serves its output from
the same Node process as `/v1/*`. No cross-origin browser bridge is
required. See `web/api/README.md` for concrete setup commands.

## Build and tests

Gradle uses `client/src/main/java` + `server/src/main/java` for the main
source set; resources from both trees are merged into one mod JAR. Unit
tests and Fabric GameTests have corresponding split source sets.
The editor's bundled default catalogue still builds into
`defaults/catalogue` via `processResources`.

The GitHub Actions verification workflow compiles the universal JAR,
runs Java tests, React typecheck/build/tests and the web API protocol
tests. The branch requires a real dedicated-server and client gameplay
test before merging. Static tests alone do not prove multiplayer parity.

## Storage and compatibility

Existing worlds still read their authored definitions from `world/questplus/`
and progress from `world/data/questplus/`. No data migration or new
Minecraft mod ID is introduced. The Java package paths and Fabric entrypoints
remain unchanged despite moving source files.
