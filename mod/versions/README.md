# Minecraft targets

Quest Plus ships **one Fabric JAR** and **one NeoForge JAR**. Each runs on every Minecraft version
its loader lists here. Each folder is one Minecraft version: its Fabric target is the folder itself
(Gradle project `:<mc>`), its NeoForge target is `<mc>/neoforge` (project `:neoforge-<mc>`).

| Folder | Fabric: builds against | Fabric: declared | NeoForge: builds against | NeoForge: declared | Adapter package |
| --- | --- | --- | --- | --- | --- |
| `26.1` | 26.1.2, Fabric API 0.155.3+26.1.2 | `26.1`, `26.1.1`, `26.1.2` | 26.1.2, NeoForge 26.1.2.126 | `26.1.2` | `dev.questplus.compat.mc26_1` |
| `26.2` | 26.2, Fabric API 0.161.0+26.2 | `26.2` | 26.2, NeoForge 26.2.0.93 | `26.2` | `dev.questplus.compat.mc26_2` |
| `26.3` | 26.3, Fabric API 0.161.0+26.3 | `26.3` | 26.3, NeoForge 26.3.0.102 | `26.3` | `dev.questplus.compat.mc26_3` |

**NeoForge on Minecraft 26.1 and 26.1.1 is not supported.** NeoForge publishes only beta builds for
them (`26.1.0.x-beta`, `26.1.1.x-beta`, checked 2026-11-11 on maven.neoforged.net), and Quest Plus
declares stable NeoForge builds only. Fabric still supports both. Once a stable NeoForge build exists,
follow "Hotfix releases" below to add it to `26.1/neoforge`.

## Source layout

| Directory | Contents | Compiled into |
| --- | --- | --- |
| `mod/server`, `mod/client` | Quest engine, teams, network, saves, GUI, rendering. **No loader API.** | every target |
| `mod/fabric` | Fabric glue: entrypoints, `fabric.mod.json`, Fabric API events, networking, Transfer API storages | Fabric targets |
| `mod/neoforge` | NeoForge glue: `@Mod` entrypoint, `neoforge.mods.toml`, NeoForge events, payloads, capabilities | NeoForge targets |
| `versions/<mc>/src/main` | Minecraft adapters for this version (`CommonAdapterImpl`, `ClientAdapterImpl`, `mixin/`) | both loaders' `<mc>` targets |
| `versions/<mc>/neoforge/src/main` | NeoForge API differences for this version (`NeoForgeClientAdapterImpl`) | `:neoforge-<mc>` |

Package names (`dev.questplus.fabric...`) predate the NeoForge port and are kept, as are quest IDs,
`schemaVersion`, save formats and payload IDs: worlds and quest packs work unchanged on both loaders.

## How it works

- **Loader glue.** `mod/fabric` and `mod/neoforge` each define the same three classes:
  `dev.questplus.platform.Loader` (mod list, config dir, versions, adapter lookup, never touches
  Minecraft classes, so mixin plugins may call it), `Platform` (server events, payloads, registration
  helpers) and `ClientPlatform` (client events, payload receivers, renderers, key mappings, HUD).
  Shared code calls their static methods. Each target compiles against its loader's copy. There is no
  service lookup or interface dispatch at runtime. **`verifyPlatformApi`** (part of `check`) fails when
  the two copies stop declaring the same public methods. Glue that needs package-private access
  (`QuestStationStorage`, `QuestResourceContainers`) is a same-named class in `dev.questplus.fabric`.
- **Minecraft adapters** (`versions/<mc>/src/main/java/dev/questplus/compat/<id>/`) hold only the
  calls whose Minecraft API differs: `CommonAdapterImpl`, `ClientAdapterImpl` and `mixin/` (listed in
  `questplus.<id>.mixins.json`). Both loaders compile the same adapter sources. Shared code reaches
  them through `CommonAdapter.INSTANCE` / `ClientAdapter.INSTANCE` (`dev.questplus.compat`).
- **NeoForge adapters** (`versions/<mc>/neoforge/src/main/java/dev/questplus/compat/<id>/`) hold the
  NeoForge API calls that differ between NeoForge builds (`NeoForgeClientAdapter`, for example the
  picture-in-picture renderer factory changed after 26.1.2).
- **`verifySharedBytecode`** (part of `check`) proves that every target, on **both loaders**, compiles
  the shared code to the same bytecode, and that each loader's glue is identical across that loader's
  targets. Each JAR ships those classes once, so this guarantees they link on every version: same
  members, same descriptors, no inlined Minecraft constants (26.3 switched key codes from GLFW to
  SDL, for example). Only the access flags of Minecraft's nested classes are ignored.
- **`verifyMixinTargets`** (part of `check`, per target) reads every enabled mixin with ASM and
  checks its target class, injected methods, `@At` targets, accessors and shadows against that
  target's Minecraft JAR, NeoForge-patched on NeoForge targets. NeoForge rewrites some vanilla
  methods, so this catches an injection point that exists on Fabric but not on NeoForge. Client
  mixins never load in server GameTests.
- **Mixins per loader.** `TargetMixinPlugin` enables only the running target's config and, on
  NeoForge, skips `TabletHudMixin`: NeoForge draws the HUD as GUI layers, so its glue cancels the
  same layers in `RenderGuiLayerEvent.Pre`. `questplus.neoforge.mixins.json` adds the after-break
  hook Fabric gets from `PlayerBlockBreakEvents.AFTER`.
- **Universal JARs**: each loader's newest target JAR also contains every other target's adapter
  classes and mixin config. Gradle generates the metadata: exact Minecraft versions, the
  `questplus:<id>` entrypoints and `questplus:targets` in `fabric.mod.json`. The `[[mixins]]`, the
  `minecraft` version range and the NeoForge minimum in `neoforge.mods.toml`, plus
  `META-INF/questplus-targets.json`. Release artifacts: `mod/build/libs/questplus-<version>.jar`
  (Fabric) and `mod/build/libs/questplus-<version>-neoforge.jar`.
- **At startup**, `MinecraftTarget` picks the target that lists the *exact* running Minecraft version
  and checks that target's tested Fabric API / NeoForge minimum. Only that target's adapters are
  instantiated (by Fabric entrypoints, or by class name on NeoForge), and `TargetMixinPlugin` disables
  every other target's mixin config before any of its target classes is looked up.
- **NeoForge registration.** NeoForge accepts registry entries only inside `RegisterEvent`.
  `QuestPlusNeoForge` runs the same `QuestPlusMod.initialize()` as Fabric during the BLOCK registry's
  event: every registry stays unfrozen for the whole pass, and NeoForge has already bound the
  attributes, data components and particles that content construction reads. Payloads, capabilities,
  attributes, renderers, key mappings and HUD layers are queued (`ModBus`, `ClientModBus`) and
  replayed into their mod-bus events. A registration that arrives after its event fails loudly.
- **Tests.**
  - Unit tests run on every target. `fabric/src/test` and `neoforge/src/test` check each loader's
    generated metadata.
  - The **same server GameTest classes** run on both loaders (`integrationTest`): every shipped
    trigger, rewards, teams, stations and FTB migration, then the shutdown saves are verified. On
    NeoForge, `QuestPlusTestNeoForge` registers the same `@GameTest` methods under Fabric's test IDs.
    `LoaderTestSupport` is the only per-loader test code: the test battery, plus transactional access
    to station capabilities.
  - Client: Fabric client GameTests (`runClientGameTest`) and the NeoForge client smoke test
    (`clientSmokeTest`) need a window. The smoke test starts a real client, audits every mixin,
    creates a world, receives the catalogue over the network, renders the tablet, the three utility
    blocks and a Quest Giver, takes screenshots and quits.
  - API differences used only by tests live in `versions/<mc>/src/gametest` and
    `versions/<mc>/neoforge/src/gametest` and are never shipped.

## Known loader differences

Gameplay, saves and payloads are identical. These are the places the loaders behave differently:

- **Hooks.** NeoForge has no post-break or post-death event. Block breaks come from a mixin on
  NeoForge's `removeBlock`, and deaths from `LivingDeathEvent` at lowest priority, skipped when cancelled.
  The death hook runs before the drops, where Fabric's `AFTER_DEATH` runs after them.
- **Fluid units.** NeoForge's fluid capability counts millibuckets, Fabric's counts droplets
  (81 per mB). Stations store droplets on both loaders.
- **Energy.** On NeoForge, Submission Stations always expose an energy capability. On Fabric they
  only do when the optional Team Reborn Energy API is installed.
- **Mod list icon.** NeoForge 26.3's loader shows a warning screen on every launch for the old
  `logoFile` key, so `neoforge.mods.toml` uses `iconFile`. The 26.1.2 and 26.2 loaders don't read
  that key, so the mod list shows no icon there (cosmetic only).
- **Recipe viewers and third-party mods.** The JEI/REI hooks are shared code (`@JeiPlugin`, found by
  both loaders' JEI), but they haven't been run with JEI or REI installed on NeoForge yet. The opt-in
  third-party objective suites (`-PobjectiveCompatibility`) and the shader/animation compatibility
  scripts in `scripts/` use Fabric builds of those mods and run on Fabric only.

## Commands (from `mod/`)

```sh
./gradlew build                          # every target, both loaders: compile, tests, GameTests, bytecode checks, JARs
./gradlew build -Pmc=26.1                # one Minecraft version, both loaders (single-target JARs)
./gradlew build -Ploader=neoforge        # one loader, every Minecraft version
./gradlew integrationTest                # server GameTests on every target of both loaders
./gradlew runClientGameTest -Pmc=26.2 -Ploader=fabric   # Fabric client GameTests (needs a window)
./gradlew :neoforge-26.2:clientSmokeTest -Pmc=26.2      # NeoForge client smoke test (needs a window)
./gradlew runClient                      # newest Fabric target, world in ../run/client
./gradlew :26.1:runClient                # older Fabric target, world in ../run/26.1/client
./gradlew :neoforge-26.3:runClient       # NeoForge, world in ../run/neoforge/26.3/client
```

Overrides such as `-Pfabric_version=...` or `-Pneoforge_version=...` apply to every configured target
of that loader, so combine them with `-Pmc=<folder>` and `-Ploader=<loader>`. The GPU, showcase,
benchmark and JEI scripts in `scripts/` pin 26.3 Fabric mods and configure the newest Fabric target only.

## Adding a Minecraft version (for example 26.4)

1. Copy `versions/26.3` to `versions/26.4` (it includes `neoforge/`). Rename the package
   `mc26_3` → `mc26_4` in Java (both `src/` and `neoforge/src/`) and in the mixin config, and rename
   `questplus.mc26_3.mixins.json` → `questplus.mc26_4.mixins.json`.
2. In `versions/26.4/gradle.properties` set `minecraft_version` and `fabric_version`. In
   `versions/26.4/neoforge/gradle.properties` set `minecraft_version` and `neoforge_version` to a
   **stable** NeoForge build (no `-beta`). Leave `validated_minecraft` out of any release until
   step 5 passes. If one loader has no stable build yet, delete that loader's properties file. The
   other loader still builds. Document the blocker in the table above.
3. `./gradlew :26.4:compileJava :neoforge-26.4:compileJava -Pmc=26.4`. Fix errors in
   `versions/26.4` first.
   - Shared code no longer compiles: add a method to `CommonAdapter`/`ClientAdapter`, implement it in
     **every** target, and call it from shared code. Never add a version check to shared code.
   - A NeoForge API changed: add a method to `NeoForgeClientAdapter` and implement it in every
     `versions/*/neoforge`.
   - A loader API changed in a way every target of that loader shares: change `mod/fabric` or
     `mod/neoforge`, keeping the `Loader`/`Platform`/`ClientPlatform` signatures identical on both
     loaders.
4. `./gradlew build`.
   - `verifySharedBytecode` lists classes: the shared source compiled differently on 26.4 or on one
     loader. Compare with `javap -c -p` and move that call into the adapters or the platform glue.
   - `verifyMixinTargets` lists a mixin: fix its injection point in `versions/26.4`, or move a shared
     mixin into every target's `mixin/` package when its target differs.
5. Run `./gradlew integrationTest`, `./gradlew runClientGameTest -Pmc=26.4 -Ploader=fabric` and
   `./gradlew :neoforge-26.4:clientSmokeTest -Pmc=26.4`, then check the tablet in game on both loaders.
   Only then set each loader's `validated_minecraft` to the exact versions you ran.
6. The newest folder becomes each loader's reference target automatically (release JARs, `run/client`).
   Update the mods pinned in `scripts/*.gradle` and `scripts/test-tablet-compatibility.mjs`.
7. Add every new `validated_minecraft` entry to `MINECRAFT_VERSIONS` in
   `web/editor/src/core/project.js`, newest first. It is the only list in Quest Studio's
   Minecraft select (project bar). `web/editor/tests/studio-ux.test.mjs` fails until both match.

To drop a version, delete its folder. Remove it from `MINECRAFT_VERSIONS` too. To drop only NeoForge for it, delete `<mc>/neoforge/gradle.properties`.

## Hotfix releases

A hotfix release that keeps the API (for example 26.1.3) is added to `validated_minecraft` of the folder
it belongs to, without a new folder, once:

1. the folder compiles to byte-identical classes on it, adapters included. Compare
   `versions/<folder>/build/classes/java/main` after
   `./gradlew :<folder>:compileJava -Pmc=<folder> -Pminecraft_version=<hotfix>` with the normal build.
2. its tests ran on it: `test`, `integrationTest` and `runClientGameTest` with the same two
   properties, and `node scripts/test-tablet-compatibility.mjs --mc=<folder> --minecraft=<hotfix>`.

Then add the hotfix to `MINECRAFT_VERSIONS` in `web/editor/src/core/project.js` (step 7 above).

This is how 26.1 and 26.1.1 share the `26.1` folder on Fabric. If the bytes differ, the hotfix changed an
API the adapters use and it needs its own folder.

On NeoForge, every NeoForge build targets exactly one Minecraft version, so a hotfix needs its own
NeoForge build. Add it to `<folder>/neoforge/validated_minecraft` after the same checks with
`-Pneoforge_version=<that build>`, and run `clientSmokeTest` instead of `runClientGameTest`.

If a future version diverges so far that a universal JAR becomes fragile, `-Pmc=<folder>` already
builds single-target JARs from the same sources.
