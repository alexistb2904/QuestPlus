# Contributing

Issues and pull requests are welcome. Describe the problem, how to reproduce it, and the Minecraft, loader (Fabric or NeoForge) and QuestPlus versions where relevant. For a change, explain its behavior and the checks you ran. Please keep changes focused and do not include worlds, credentials, caches or generated build outputs.

## Fresh checkout

Install Git, a **JDK 25** (not only a JRE), and **Node.js 22 or later** with npm. Set `JAVA_HOME` to the JDK and put its `bin` directory on `PATH`. The Gradle wrapper in `mod/` downloads Gradle npm installs from the committed lockfiles. The first build requires internet access for Minecraft, Fabric and other dependencies. No local `.tools/`, Minecraft installation, IDE settings or existing `run/` directory is required.

From the repository root, run the following on Windows use `gradlew.bat` in place of `./gradlew`:

```sh
cd mod
./gradlew build
cd ../web/editor
npm ci
npm test
npm run typecheck
npm run build
cd ../api
npm ci
npm test
```

These are the checks required before merging. `./gradlew build` covers every supported Minecraft version on both Fabric and NeoForge (see [Minecraft targets](mod/versions/README.md), which also explains how to add one). The server integration tests of both loaders additionally run with `cd mod && ./gradlew integrationTest`. Client tests need a graphical Minecraft environment: `cd mod && ./gradlew runClientGameTest -Ploader=fabric` and `./gradlew :neoforge-26.3:clientSmokeTest -Pmc=26.3`. They are separate from unit tests. Windows users can run `dev.bat doctor` to check prerequisites, `dev.bat test` for the automated suite, or `dev.bat` for the menu. See [development](docs/development.md) and [Windows testing](docs/windows-testing.md).

For the standalone editor, run `npm run dev` in `web/editor` it works without the API. For connected editing, see [the API setup](web/api/README.md). Local keys, worlds and API data stay ignored use the example configuration and generate your own credentials.

## Optional assets and compatibility checks

Editable Blockbench models and runtime assets are included. Open the models in `mod/questplus-assets/blockbench/` with Blockbench. To update the runtime assets, follow the [tablet asset export guide](mod/questplus-assets/README.md). The export tools are optional and are not required for a normal build.

Shader and mod compatibility tests need optional downloaded assets and a GPU see [shader testing](docs/shader-punchy-testing.md). The FTB import verification script accepts a path to an external FTB pack and defaults to the ignored `.tools/ftb-real-pack` it is not part of the standard test suite. No third-party pack is included.

## Project boundaries

The server validates packs and owns progression and rewards client code handles rendering, and the editor remains usable offline. Read [architecture](docs/architecture.md) and the [API README](web/api/README.md) before changing the web/Minecraft protocol. The project currently has no root source-code license ask the maintainers about reuse and contribution terms. Third-party notices are in `mod/licenses/` the bundled CommonUtils redistribution question described there must be resolved before distributing release JARs.
