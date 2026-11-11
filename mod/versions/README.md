# Minecraft targets

Quest Plus ships **one Fabric JAR** that runs on every Minecraft version listed here.
Each folder is one validated target:

| Folder | Builds against | Declared in `fabric.mod.json` | Adapter package |
| --- | --- | --- | --- |
| `26.1` | 26.1.2, Fabric API 0.155.3+26.1.2 | `26.1`, `26.1.1`, `26.1.2` | `dev.questplus.compat.mc26_1` |
| `26.2` | 26.2, Fabric API 0.161.0+26.2 | `26.2` | `dev.questplus.compat.mc26_2` |
| `26.3` | 26.3, Fabric API 0.161.0+26.3 | `26.3` | `dev.questplus.compat.mc26_3` |

## How it works

- **Shared code** (`mod/server`, `mod/client`) holds the quest engine, teams, network,
  saves, GUI and rendering. Every target compiles it against its own Minecraft.
- **Adapters** (`versions/<mc>/src/main/java/dev/questplus/compat/<id>/`) hold only the
  calls whose Minecraft/Fabric API differs: `CommonAdapterImpl`, `ClientAdapterImpl` and
  `mixin/` (listed in `questplus.<id>.mixins.json`). Shared code reaches them through
  `CommonAdapter.INSTANCE` / `ClientAdapter.INSTANCE` (`dev.questplus.compat`).
- **`verifySharedBytecode`** (part of `check`) proves that every target compiles the shared
  code to the same bytecode. The JAR ships those classes once, so this is what guarantees they
  link on every version: same members, same descriptors, no inlined Minecraft constants
  (26.3 switched key codes from GLFW to SDL, for example). Only the access flags of Minecraft's
  nested classes are ignored, because the JVM never links against them.
- **`verifyMixinTargets`** (part of `check`, per target) reads every enabled mixin with ASM and
  checks its target class, injected methods, `@At` targets, accessors and shadows against that
  target's Minecraft JAR. Client mixins never load in server GameTests, so this is what catches a
  renamed method in CI without a window.
- **Universal JAR**: the newest target's JAR also contains every other target's adapter
  classes and mixin config. Gradle generates `depends.minecraft` (exact versions only), the
  `questplus:<id>` entrypoints and the `questplus:targets` block. `mod/build/libs/questplus-<version>.jar`
  is the release artifact.
- **At startup**, `MinecraftTarget` picks the target that lists the *exact* running
  Minecraft version and checks that target's tested Fabric API minimum. Fabric then
  instantiates only that target's adapters, and `TargetMixinPlugin` disables every other
  target's mixin config before any of its target classes is looked up. No class of another
  target is ever loaded.
- **Tests**: unit tests, server GameTests (`integrationTest`) and client GameTests run on
  every target. The `MinecraftTargetGameTest` server test and the start of
  `QuestClientGameTest` check the selected target and run `MixinEnvironment.audit()`, which
  applies every enabled mixin. API differences used only by tests live in
  `versions/<mc>/src/gametest/java/dev/questplus/compat/*Shims.java` and are never shipped.

## Commands (from `mod/`)

```sh
./gradlew build                          # every target: compile, tests, GameTests, bytecode check, JAR
./gradlew build -Pmc=26.1                # one target only (outputs questplus-<version>+mc26.1.jar)
./gradlew integrationTest                # server GameTests on every target
./gradlew runClientGameTest -Pmc=26.2    # client GameTests on one target (needs a window)
./gradlew runClient                      # newest target, world in ../run/client
./gradlew :26.1:runClient                # older target, world in ../run/26.1/client
```

Overrides such as `-Pfabric_version=...` apply to every configured target, so combine them
with `-Pmc=<folder>`. The GPU, showcase, benchmark and JEI scripts in `scripts/` pin 26.3
mods and configure the newest target only.

## Adding a Minecraft version (for example 26.4)

1. Copy `versions/26.3` to `versions/26.4`. Rename the package `mc26_3` → `mc26_4` in Java and
   in the mixin config, and rename `questplus.mc26_3.mixins.json` → `questplus.mc26_4.mixins.json`.
2. In `versions/26.4/gradle.properties`, set `minecraft_version` and `fabric_version`. Leave
   `validated_minecraft` out of any release until step 5 passes.
3. `./gradlew :26.4:compileJava :26.4:compileGametestJava -Pmc=26.4`. Fix errors in
   `versions/26.4` first. If shared code no longer compiles, add a method to
   `CommonAdapter`/`ClientAdapter`, implement it in **every** target, and call it from shared
   code. Never add a version check to shared code.
4. `./gradlew build`. If `verifySharedBytecode` lists classes, the shared source compiled
   differently on 26.4. Compare with `javap -c -p` and move that call into the adapters too.
   If `verifyMixinTargets` lists a mixin, fix its injection point in `versions/26.4`, or move a
   shared mixin into every target's `mixin/` package when its target differs.
5. Run `./gradlew integrationTest` and `./gradlew runClientGameTest -Pmc=26.4`, then check the
   tablet in game. Only then set `validated_minecraft` to the exact versions you ran.
6. The newest folder becomes the reference target automatically (release JAR, `run/client`).
   Update the mods pinned in `scripts/*.gradle` and `scripts/test-tablet-compatibility.mjs`.

To drop a version, delete its folder. A hotfix release that keeps the API (for example 26.1.3) is
added to `validated_minecraft` of the folder it belongs to, without a new folder, once:

1. the folder compiles to byte-identical classes on it, adapters included. Compare
   `versions/<folder>/build/classes/java/main` after
   `./gradlew :<folder>:compileJava -Pmc=<folder> -Pminecraft_version=<hotfix>` with the normal build;
2. its tests ran on it: `test`, `integrationTest` and `runClientGameTest` with the same two
   properties, and `node scripts/test-tablet-compatibility.mjs --mc=<folder> --minecraft=<hotfix>`.

This is how 26.1 and 26.1.1 share the `26.1` folder. If the bytes differ, the hotfix changed an API
the adapters use and it needs its own folder.

If a future version diverges so far that a universal JAR becomes fragile, `-Pmc=<folder>`
already builds a single-target JAR from the same sources.
