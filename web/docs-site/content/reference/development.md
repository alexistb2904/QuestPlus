# Build and contribute

QuestPlus has three runtime responsibilities and two deployed units. The browser editor and optional web API form the web service. Client and server Java compile together into one Fabric JAR.

## Source map

| Folder                                                | Responsibility                                              |
| ----------------------------------------------------- | ----------------------------------------------------------- |
| `mod/client/src/main/java/`                           | Tablet, input, HUD, client models and rendering             |
| `mod/server/src/main/java/dev/questplus/core/`        | Pack validation, objective rules and progress models        |
| `mod/server/src/main/java/dev/questplus/fabric/`      | Server lifecycle, commands, networking and integrations     |
| `mod/server/src/main/java/dev/questplus/persistence/` | Durable progress and claims                                 |
| `web/editor/`                                         | React and TypeScript quest authoring                        |
| `web/api/`                                            | Same origin static hosting, approvals and publication relay |
| `web/docs-site/`                                      | This VitePress documentation                                |
| `mod/questplus-assets/`                               | Editable Blockbench sources and animation converters        |

## Prerequisites

Use Java 25 and Node.js 22. The Gradle wrapper selects the project's Gradle version. On Windows, `scripts/Find-Java25.ps1` locates a compatible local JDK.

```powershell
$env:JAVA_HOME = & .\scripts\Find-Java25.ps1
cd mod
.\gradlew.bat build
```

In `mod/`, on other platforms use `./gradlew build` with Java 25 configured. The release artifact is written to `mod/build/libs/`.

On Windows, `dev.bat` option **5** (`dev.bat build`) runs the full mod build, JUnit tests, server GameTests and JAR checks. Option **10** (`dev.bat compile`) only compiles and packages the mod: enter the desired mod version, or press Enter to keep the current `mod_version` from `mod/gradle.properties`. The selected version is saved to that file before compilation, so Gradle also writes it into the generated `fabric.mod.json` inside the JAR. The script copies `questplus-<version>.jar` into `output/` at the repository root. The selected version remains saved if compilation fails. An existing output JAR with the same version is replaced only after a successful build. The Minecraft version stays unchanged.

## Run the required checks

Both build options compile `https://quest.alexistb.com/` as the default editor address. Existing `questplus-hub.json` URLs override that default. Connected editing uses per-session credentials issued automatically by the Hub. No credentials need to be configured or embedded in the JAR.

Run each block from the repository root or from the indicated folder.

```sh
cd mod
./gradlew build
./gradlew integrationTest
```

```sh
cd web/editor
npm ci
npm test
npm run typecheck
npm run build
```

```sh
cd web/api
npm ci
npm test
```

```sh
cd web/docs-site
npm ci
npm run build
```

The build validates Java and artifact packaging. `integrationTest` exercises the Fabric server boundary. Client GameTests require graphics support. Run Gradle compilation and GPU tests sequentially because they share generated resources.

## Prepare a release

Review `licenses/ANIMATION-NOTICES.md` before distributing bundled libraries. The current repository notes record an unresolved redistribution prerequisite for the CommonUtils services library nested inside PAL. Passing artifact verification does not resolve that prerequisite.

When release requirements are satisfied, the animation libraries are embedded dependencies and Fabric API remains external. Do not publish a capture or test JAR as a release artifact.

## Run locally

From `mod/`, use `./gradlew runClient` or `./gradlew runServer` for the development game. Start Studio with `npm run dev` in `web/editor/`. It remains useful without the web API through local drafts and ZIP import or export.

On Windows, `dev.bat client` also prepares and starts the local web API for solo editing, or reuses the running service. Run `/questplus editor` in your world, open the link and approve the browser code in Minecraft. Startup logs are in `run/questplus-client-web.log`. The API stays running after the game closes. If it cannot start, gameplay remains available and you can use `dev.bat api` before retrying. Direct Gradle launches and installed JARs still need a separately configured web API for connected editing in solo or multiplayer.

For connected editing, build Studio and run the optional API as described in [Host the web service](/reference/hosting). Use a disposable world for publication and rollback tests.

## Keep the boundaries intact

Keep mutable gameplay state on the Minecraft server thread. The browser sends authored definitions, and the server validates the complete pack before applying it. Client rendering classes must not be loaded by dedicated server code.

Never expose player saves, claim records or per-session server credentials to the browser. Keep editor tokens in memory. A session URL is not authorization.

Before changing a protocol, read `docs/architecture.md` and `web/api/README.md`. Add tests for changes to protocol, registry validation, permissions, rollback or release packaging.

## Edit 3D models

Every 3D model has an editable Blockbench project (`.bbmodel`) in `mod/questplus-assets/blockbench/`. Edit models in Blockbench only. No script generates or rewrites geometry, so do not add one. The game reads the exported files in `mod/client/src/main/resources/assets/questplus/`. Editing a `.bbmodel` alone does not change the game.

### Blocks and items

| Model                                             | Blockbench source                                  | Exported file                             |
| ------------------------------------------------- | -------------------------------------------------- | ----------------------------------------- |
| Fixed-size quest boards                           | `blockbench/world/quest_board_{c,n,s,e,w,ne,nw,se,sw}.bbmodel` | `models/block/quest_board_*.json` |
| Modular quest board                               | `blockbench/world/quest_board_module_0` … `_15.bbmodel` | `models/block/quest_board_module_*.json` |
| Submission station                                | `blockbench/world/submission_station.bbmodel`      | `models/block/submission_station.json`    |
| Quest relay (off / on)                            | `blockbench/world/quest_relay.bbmodel`, `quest_relay_on.bbmodel` | `models/block/quest_relay*.json` |
| Tablet in the inventory and on the ground         | `blockbench/questplus_tablet_item.bbmodel`         | `models/item/tablet.json`                 |

1. Open the `.bbmodel` in Blockbench, make your change and save the project.
2. Choose **File › Export › Export Block/Item Model** and overwrite the exported file listed above.
3. If you painted the texture, also save it over its PNG in `textures/` (`block/questplus_utility.png` for the board and station, `block/quest_relay.png` for the relay, `fp/questplus_first_person.png` for the tablet).
4. Commit the `.bbmodel` and the exported files together.

Each modular board piece has its own file. Its number lists the framed borders: north = 1, south = 2, west = 4, east = 8. For example, `5` has a frame on the north and west sides, `15` on all four sides and `0` on none. A change to a frame must be repeated in every module that has that border. Keep the slate surface inside the frames. If it runs under a frame, both faces share the board's outer edge and flicker (z-fighting).

`quest_board_module_15.bbmodel` also holds the hand and ground poses of the board item. They are copied into `models/item/quest_board.json`. The `quest_board_2x3` … `quest_board_4x6` projects only preview whole boards and are never exported. Blockstates and item definition files contain no geometry. Edit them directly as JSON.

### Tablet animations

The first person and third person tablet models are animated. Edit `questplus_first_person.bbmodel` or `questplus_third_person.bbmodel` in Blockbench, then export the Bedrock animation and geometry. Then run the converters described in `mod/questplus-assets/README.md`. They do not create geometry. They add the camera, animation and axis adjustments that the mod reads at runtime.

### Check your change

From `mod/`, run `./gradlew build`. Asset tests check the board borders, the item poses and the tablet resources. They do not replace a look in game: start `./gradlew runClient` and inspect the model from the front, the sides and the back.

Report build, server integration, browser and live client results separately. A passing build does not prove a real session with two Minecraft clients.
