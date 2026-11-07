# Tablet settings and accessibility

Open **Settings** in the tablet. These preferences are local to your Minecraft client and do not change server quest definitions or rewards.

## Choose a display mode

The animated 3D tablet and the flat 2D interface use the same quest layout and actions. Disable the tablet display option to use the flat view. Use **Interface scale** to make the complete interface larger or smaller.

Enable **Reduced motion**, or disable animations, if the opening movement and ambient tablet effects are distracting. Reduced motion also removes the drifting decorative motes.

![The same quest interface displayed in the flat mode](/media/tablet-flat.webp)

_The flat view gives the same controls more direct screen space, without the 3D pose._

## Make the screen readable

**Self Illumination** controls whether the 3D screen provides its own light. **Screen brightness** dims that screen independently of the world. Neither option changes the flat interface colours.

When a shader pack is active, QuestPlus automatically suppresses its background blur. Shader depth of field and reflections still come from the shader pack. Adjust those in Iris if they obscure the tablet.

![A fresh tablet capture with Complementary Reimagined active](/media/tablet-shader.webp)

_Shaders are optional. This example shows the 3D display with its illuminated screen._

## Keep the HUD useful

Pin up to three quests from their details. Completed quests unpin automatically. The HUD starts disabled on a new configuration, so enable it in settings if pinned objectives are not visible.

HUD position and notification position are separate preferences. Move either display if it overlaps another mod's interface. Turning off notifications does not stop quest progress.

By default, damage closes the tablet. Enable the option to keep it open on damage only if that suits your play style.

## Keyboard and configuration

Change **J** in Minecraft's key bindings if another mod uses it. In menus, use the displayed keyboard hints and normal focus navigation. The flat view is also available when a rendering or animation combination is uncomfortable.

Preferences live in `config/questplus-client.json`. Close Minecraft and back up this file before editing it manually. Player progress lives on the server, in a separate location.

[Troubleshooting](/guide/troubleshooting) · [Compatibility](/guide/compatibility)
