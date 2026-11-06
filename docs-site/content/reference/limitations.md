# Capabilities and limitations

This page distinguishes implemented authoring paths from features that cannot be inferred merely from an editor field.

## Supported in the current project

- Minecraft Java 26.3 with Fabric, Java 25 and the matching Fabric API.
- React-based Quest Studio with browser drafts, ZIP/JSON5 export, templates and multilingual quest text.
- Categories, quests, graph layout, annotations, prerequisites and graphical editing.
- Standard gameplay objectives, submissions, observation, stages, equipment, XP and modded registry IDs.
- One-time, repeating and tiered progression, optional tasks and server-driven rewards.
- Choice, random, loot and reusable named reward tables with bounded validation.
- Team progression and the command tree listed in this documentation.
- Reviewed FTB JSON5/SNBT conversion for supported task and reward semantics.
- Opt-in connected editing through Quest Hub with operator approval.

## Not supported or not guaranteed

| Request | Boundary |
| --- | --- |
| Run the mod on Forge, NeoForge or Bedrock | No supported build |
| Run arbitrary script tasks from an imported FTB pack | Requires a trusted Java extension |
| Make a new objective by typing an unknown ID | Requires an installed registered event handler |
| Fully reproduce every FTB Quests pack | Unsupported semantics are reported or excluded |
| Play embedded remote videos in graph annotations | Not a supported format |
| Use GitHub Pages to modify a live Minecraft server | GitHub Pages serves only the static editor |
| Give untrusted packs command execution | Intentionally disabled by default |
| Treat browser autosave as cloud backup | Browser local storage is not a backup |
| Guarantee perfect shader compatibility | Test specific versions and graphics configurations |
| Automatically migrate IDs when editing old quest definitions | Preserve stable IDs or write a migration |
| Guarantee reward delivery under every abrupt disk failure | Requires audited server recovery procedures |

## Validation boundaries

The editor can reject malformed files and unsupported combinations before export. The Minecraft server is the final authority for modded registry IDs, world behavior and reward claims. A passing UI preview is not a substitute for a dedicated-server integration test.

Complex FTB presentations, external scripts and mod-specific transfers may need additional work. See the [current FTB migration report](https://github.com/alexistb2904/QuestPlus/blob/main/docs/ftb-migration-compatibility.md) and [remaining technical work](https://github.com/alexistb2904/QuestPlus/blob/main/docs/CODEX_TODO_P0_P2.md).

## Reporting issues

Include the Quest Plus commit or version, Minecraft/Fabric versions, server log, relevant quest file and an exact reproduction. For rendering issues, also include shader and animation-mod versions.

Open a report on [GitHub Issues](https://github.com/alexistb2904/QuestPlus/issues).
