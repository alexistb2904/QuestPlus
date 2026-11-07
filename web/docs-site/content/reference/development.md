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

## Run the required checks

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

For connected editing, build Studio and run the optional API as described in [Host the web service](/reference/hosting). Use a disposable world for publication and rollback tests.

## Keep the boundaries intact

Keep mutable gameplay state on the Minecraft server thread. The browser sends authored definitions, and the server validates the complete pack before applying it. Client rendering classes must not be loaded by dedicated server code.

Never expose player saves, claim records or the server API key to the browser. Keep editor tokens in memory. A session URL is not authorization.

Before changing a protocol, read `docs/architecture.md` and `web/api/README.md`. Add tests for changes to protocol, registry validation, permissions, rollback or release packaging.

## Edit tablet assets

Open the models in `mod/questplus-assets/blockbench/` with Blockbench. Export the Bedrock animation and geometry files, then run the converters described in `mod/questplus-assets/README.md`. They apply the camera, animation and coordinate adjustments required by the mod. Keep the editable source and updated runtime assets together, then run the asset tests.

Report build, server integration, browser and live client results separately. A passing build does not prove a real session with two Minecraft clients.
