# QuestPlus world blocks

Visual contract: stained oak, worn iron, copper fasteners, dark slate and small
amethyst runes, sampled from the tablet's material language. No glass neon panels.
All geometry is baked Minecraft JSON; no per-frame geometry construction.

* Quest Board: wall mounted slate writing surface, thick timber frame and corner
  straps. Sizes are height × width: 2×3, 2×4, 3×4 and 4×6. Each tile is within its
  block bounds; only the lower-left tile owns an entity. Content sits in front of
  the slate, inside the frame.
* Submission Station: wooden cabinet, iron foot/rim, top hatch with copper handle,
  front insertion slot and a small recessed amethyst status strip.
* Quest Relay: low slate plinth, copper contacts and a cross-shaped amethyst rune.
  Its on/off baked models show the output state.

Source: `tools/export_world_blocks.py`. Textures are deterministic original 16px
pixel art. Regenerate with `python mod/questplus-assets/tools/export_world_blocks.py`.
