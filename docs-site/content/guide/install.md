# Install Quest Plus

## Requirements

| Component | Supported version |
| --- | --- |
| Minecraft Java | 26.3 |
| Loader | Fabric Loader 0.20.1 or newer for 26.3 |
| Fabric API | 0.165.2+26.3 or compatible newer build |
| Java | 25 or newer |
| Quest Plus | Same mod JAR on client and dedicated server |

Place the Quest Plus JAR and Fabric API in `mods/`. A single-player world already runs an integrated server. No Forge, NeoForge, Quilt or Bedrock build is advertised.

For the animated 3D tablet, install **Player Animation Library 1.2.8** and **First Person PAL Expansion 1.1.1** on the client. Without these, the 2D interface is available.

## First launch

1. Launch a world with the matching Fabric installation.
2. Press **J**, the default configurable quest key.
3. Open a category and select a quest.
4. Complete the objectives and collect the unlocked reward.

Bundled quest definitions are copied to a world when that world is first initialized. Updating the mod does not automatically replace definitions that a world already owns.

## Shaders and animation mods

Iris is optional. When an Iris shader is **active**, Quest Plus uses a shader-compatible tablet path and suspends background blur. Merely having Iris installed does not turn this mode on.

The project tests selected versions of Sodium, Iris, Complementary Reimagined, Punchy and First Person Model. Compatibility with every shader or camera mod is not guaranteed.

See the [source compatibility report](https://github.com/alexistb2904/QuestPlus/blob/main/docs/compatibility.md) for exact tested versions and reproduction instructions.

## Next step

Learn [tablet navigation](/guide/tablet) or [start building a quest pack](/guide/studio).
