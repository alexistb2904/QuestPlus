---
title: "FTB Quests alternative for Minecraft 26.1+"
description: "Moving a modpack from FTB Quests to Minecraft 26.1, 26.2 or 26.3? Quest Plus imports FTB Quests chapters, converts them in Quest Studio and migrates player progress."
keywords: "FTB Quests alternative, FTB Quests 26.1, FTB Quests 26.2, FTB Quests 26.3, FTB Quests Fabric, FTB Quests NeoForge, import FTB Quests, convert SNBT quests, quest book mod 26.1, Minecraft quest mod 26.1"
image: /media/ftb-import.webp
faq:
  - q: "Is Quest Plus a replacement for FTB Quests on Minecraft 26.1+?"
    a: "Quest Plus is an independent quest mod for Minecraft Java 26.1, 26.1.1, 26.1.2, 26.2 and 26.3 on Fabric and NeoForge. It does not need FTB Quests or FTB Library at runtime, and Quest Studio can import supported FTB Quests chapters."
  - q: "Can I import my FTB Quests chapters into Quest Plus?"
    a: "Yes. Select the FTB quest folder with its chapters/ files in SNBT, JSON or JSON5 in Quest Studio. Studio converts supported quests, lists excluded mechanics in a report and exports a Quest Plus ZIP."
  - q: "Does the import keep player progress from FTB Quests?"
    a: "The Studio import converts quest definitions only. Operators migrate existing progress on the server with /questplus migrate-ftb preview, confirm and rollback."
  - q: "Which FTB Quests features are not converted automatically?"
    a: "Custom scripts, third party tasks and Task Screens are not converted. Unsupported quests and the quests that depend on them are excluded with a reason in the conversion report."
  - q: "Does Quest Plus work on Fabric and NeoForge?"
    a: "Yes. Each release ships one Fabric JAR and one NeoForge JAR. Fabric needs Fabric API. NeoForge is supported on Minecraft 26.1.2, 26.2 and 26.3 with stable builds."
  - q: "Is Quest Plus free?"
    a: "Yes. The mod is free on Modrinth and CurseForge, and Quest Studio runs free in the browser, including offline."
---

# Coming from FTB Quests

Quest Plus is a quest mod for **Minecraft Java 26.1, 26.2 and 26.3** on **Fabric and NeoForge**. If your modpack already has FTB Quests chapters, [Quest Studio](/guide/studio) converts them into a Quest Plus pack and the server can migrate player progress. Quest Plus has no runtime dependency on FTB Quests or FTB Library.

![The FTB import review in Quest Studio with conversion warnings and ID mapping](/media/ftb-import.webp)

## What you get with Quest Plus

| Feature | Quest Plus |
| --- | --- |
| Minecraft versions | 26.1, 26.1.1, 26.1.2, 26.2, 26.3 ([details](/guide/compatibility)) |
| Loaders | Fabric (with Fabric API) and NeoForge |
| Quest interface | Animated 3D [quest tablet](/guide/tablet) or a flat view, HUD tracking |
| In world blocks | [Quest Board, Submission Station and Quest Relay](/guide/world-blocks) |
| NPCs and crates | [Quest givers and animated loot crates](/guide/givers-and-crates) |
| Multiplayer | [Teams](/guide/teams) with shared progress and leaderboards |
| Editor | [Quest Studio](/guide/studio) in the browser, offline ZIP or live publish |
| Pack format | Plain [JSON5 files](/reference/pack-format) |
| FTB Quests | Definition import and progress migration |
| Mod integration | [Java API](/reference/java-api) for Fabric and NeoForge |

## Move a modpack in four steps

1. **Import the chapters.** In Quest Studio, choose **Folder** or **Import** and select the FTB quest source with its `chapters/` files in SNBT, JSON or JSON5. Follow the full [FTB import walkthrough](/guide/modpack-creators#import-ftb-quests).
2. **Review the report.** Check the warnings and the FTB to Quest Plus ID mapping, set fluid and energy units, then select **Replace project**.
3. **Test the pack.** Export the ZIP, [install it in a test world](/guide/modpack-creators#install-and-test-in-a-world) and play through the imported quests on your real loader and Minecraft version.
4. **Migrate progress.** On an existing server, back up the world, then run `/questplus migrate-ftb preview`, `confirm` and, if needed, `rollback`. See [commands](/reference/commands#recovery-and-migration-op).

Custom scripts, third party tasks and Task Screens need manual work. The [limits page](/reference/limitations) lists what is not supported.

## Frequently asked questions

<div v-for="f in $frontmatter.faq" :key="f.q">
  <h3>{{ f.q }}</h3>
  <p>{{ f.a }}</p>
</div>

[Install Quest Plus](/guide/install) · [Guide for modpack creators](/guide/modpack-creators) · [Compatibility](/guide/compatibility)
