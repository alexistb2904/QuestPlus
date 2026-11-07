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
| `mod/questplus-assets/`                               | Editable Blockbench sources and exporters                   |

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

Both build options compile `https://quest.alexistb.com/` as the default editor address. Existing `questplus-hub.json` URLs override that default. No server secret is embedded in the JAR. Connected editing still requires the web API's private key in the Minecraft configuration. Without it, the command provides the website link for ZIP authoring and explains how to enable connected editing.

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

Never expose player saves, claim records or the server API key to the browser. Keep editor tokens in memory. A session URL is not authorization.

Before changing a protocol, read `docs/architecture.md` and `web/api/README.md`. Add tests for changes to protocol, registry validation, permissions, rollback or release packaging.

## Edit tablet assets

Open the models in `mod/questplus-assets/blockbench/` with Blockbench. Export the Bedrock animation and geometry files, then run the converters described in `mod/questplus-assets/README.md`. They apply the camera, animation and coordinate adjustments required by the mod. Keep the editable source and updated runtime assets together, then run the asset tests.

Report build, server integration, browser and live client results separately. A passing build does not prove a real session with two Minecraft clients.
