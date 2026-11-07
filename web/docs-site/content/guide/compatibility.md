# Compatibility

## Required environment

QuestPlus targets **Minecraft Java 26.3**, **Fabric** and **Java 25**. Install the same QuestPlus release and a compatible Fabric API on clients and the server. The project builds one universal Fabric JAR.

Player Animation Library 1.2.8 and First Person PAL Expansion 1.1.1 are bundled in the current artifact configuration. Fabric API is installed separately. Check the requirements of the release you download.

There is no supported Forge, NeoForge or Bedrock build.

## Shaders and animation mods

Shaders works in theory, but the tablet animation can conflict with depth of field, reflections and other camera effects. Test your exact shader, camera and player animation combination. If it interferes, use the flat interface while investigating. Actually Make Up Fast, Bliss and Complementary Reimagined are known to work with the tablet animation.

QuestPlus detects an active shader pack and suppresses its own background blur. Use screen brightness and Self Illumination to tune the tablet. Shader depth of field and reflections remain controlled by the shader.

The mod has been tested with the following animation mods: Punchy, Punchy Moves, Not Enough Animations.

## Modded content

Supported objectives can target installed mod items, blocks, entities and tags. Registry previews help select IDs. They do not install missing mods or add hooks for custom gameplay.

Custom machine events, stage providers, fluid transfers and energy transfers need a trusted server integration. [Java integration](/reference/java-api) explains the available hooks.

## FTB Quests

Studio can convert supported FTB definitions and reports excluded mechanics. This is a conversion workflow, not a runtime dependency on FTB Quests. Unsupported tasks and their dependents need manual work.

Definition import does not migrate player saves. Progress migration is a separate operator workflow. Test the converted pack in its actual Fabric environment before shipping it.

## What to test for a release

Test a fresh world, an upgraded world copy, a dedicated server and two real clients. Include team changes, reconnects, reward choices, submission failure and rollback. A browser preview or a single integrated client does not establish full multiplayer compatibility.

[Troubleshooting](/guide/troubleshooting) · [FTB import](/guide/modpack-creators#import-ftb-quests)
