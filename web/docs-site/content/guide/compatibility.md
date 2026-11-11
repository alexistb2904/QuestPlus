# Compatibility

## Required environment

QuestPlus runs on **Minecraft Java 26.1, 26.1.1, 26.1.2, 26.2 and 26.3** with **Fabric** and **Java 25**. One JAR covers all of them: it detects the running Minecraft and loads the matching code. Install the same QuestPlus release and a Fabric API for your Minecraft version on clients and the server.

The minimum versions are **Fabric Loader 0.19.5** and the Fabric API build each version was tested with:

| Minecraft | Minimum Fabric API |
| --- | --- |
| 26.1, 26.1.1, 26.1.2 | 0.155.3+26.1.2 |
| 26.2 | 0.161.0+26.2 |
| 26.3 | 0.161.0+26.3 |

The tablet animations are built in and no animation library is bundled or required. Fabric API is installed separately. Check the requirements of the release you download.

There is no supported Forge, NeoForge or Bedrock build.

## Shaders and animation mods

Shader packs run through **Iris**. QuestPlus has no hard dependency on Iris. It detects an active pack and adapts its rendering.

### World blocks

The Quest Board, the Submission Station, the Quest Relay and the loot crate reveal work with the following shader packs. They were tested with Sodium 0.9.2 and Iris 1.11.7 on Minecraft 26.3, and Iris 1.11.4 on Minecraft 26.1.2 and 26.2. Minecraft 26.1 and 26.1.1 are an exception, listed below.

| Shader pack | Version tested |
| --- | --- |
| Complementary Reimagined | r5.9.3 |
| Complementary Unbound | r5.9.3 |
| BSL Shaders | 10.1.8 |
| Photon | v1.4 |
| Solas | 3.9 |
| Bliss | 2.1.2 |
| MakeUp - Ultra Fast | 9.6 |
| Sildur's Vibrant | 2.02 |

In each pack, board and station text stays readable. No stray geometry is drawn around the blocks, and the crate glow stays around the crate.

While a shader pack is active, board and station text uses Minecraft's pixel font instead of the QuestPlus font. Iris does not render outline fonts correctly in the world yet. The QuestPlus font comes back as soon as shaders are turned off.

Known differences:

- With Solas, the board is darker than with the other packs. It stays readable.
- With Sildur's Vibrant, the front of the loot crate looks black during the reveal.
- At night, station text dims with the surrounding light, like a sign.
- On Minecraft 26.1 and 26.1.1, Iris 1.10.9 is the newest Iris available. With a shader pack active there, the Quest Board, the Submission Station, the Quest Relay and the loot crate are not drawn, although they still work. The tablet is not affected. Play on 26.1.2 or later to use these blocks with shaders.

### Tablet

QuestPlus suppresses its own background blur while a shader pack is active. Use screen brightness and Self Illumination to tune the tablet. Shader depth of field and reflections remain controlled by the shader.

### Animation mods

The mod has been tested on Minecraft 26.3 with the following animation mods: Punchy, Punchy Moves, Not Enough Animations.

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
