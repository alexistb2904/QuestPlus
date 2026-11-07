# Compatibility

## Required environment

QuestPlus targets **Minecraft Java 26.3**, **Fabric** and **Java 25**. Install the same QuestPlus release and a compatible Fabric API on clients and the server. The project builds one universal Fabric JAR.

The minimum versions are **Fabric Loader 0.19.5** and **Fabric API 0.161.0+26.3**. Newer versions for Minecraft 26.3 are accepted, including Fabric API 0.164.0+26.3. These minima come from the bundled animation libraries. Older 26.3 builds cannot load them. Minecraft versions other than 26.3 remain unsupported.

Gradle compiles against these minimum versions. The verification workflow also runs server GameTests with Loader 0.19.5 / API 0.164.0+26.3 and Loader 0.20.1 / API 0.165.2+26.3.

Player Animation Library 1.2.8 and First Person PAL Expansion 1.1.1 are bundled in the current artifact configuration. Fabric API is installed separately. Check the requirements of the release you download.

There is no supported Forge, NeoForge or Bedrock build.

## Shaders and animation mods

Shaders works in theory, but the tablet animation can conflict with depth of field, reflections and other camera effects. Test your exact shader, camera and player animation combination. If it interferes, use the flat interface while investigating. Actually Make Up Fast, Bliss and Complementary Reimagined are known to work with the tablet animation.

QuestPlus detects an active shader pack and suppresses its own background blur. Use screen brightness and Self Illumination to tune the tablet. Shader depth of field and reflections remain controlled by the shader.

The mod has been tested with the following animation mods: Punchy, Punchy Moves, Not Enough Animations.

The first-person tablet hand rendering has also been checked with Fresh Animations 1.11.2, Player Extension 1.1, EMF 3.3.12 and ETF 7.2.7, alongside Punchy. While the tablet is active, it draws the player's skin on its own vanilla arm meshes and suppresses normal first-person hands. Custom player arm geometry from resource packs is not used for the tablet. This check does not cover every shader or resource-pack combination.

## Modded content

Supported objectives can target installed mod items, blocks, entities and tags. Registry previews help select IDs. They do not install missing mods or add hooks for custom gameplay.

Custom machine events, stage providers, fluid transfers and energy transfers need a trusted server integration. [Java integration](/reference/java-api) explains the available hooks.

## FTB Quests

Studio can convert supported FTB definitions and reports excluded mechanics. This is a conversion workflow, not a runtime dependency on FTB Quests. Unsupported tasks and their dependents need manual work.

Definition import does not migrate player saves. Progress migration is a separate operator workflow. Test the converted pack in its actual Fabric environment before shipping it.

## What to test for a release

Test a fresh world, an upgraded world copy, a dedicated server and two real clients. Include team changes, reconnects, reward choices, submission failure and rollback. A browser preview or a single integrated client does not establish full multiplayer compatibility.

[Troubleshooting](/guide/troubleshooting) · [FTB import](/guide/modpack-creators#import-ftb-quests)
