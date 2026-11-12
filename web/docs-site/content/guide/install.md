---
title: "Install Quest Plus on Fabric or NeoForge (Minecraft 26.1+)"
description: "Download and install the Quest Plus quest mod for Minecraft Java 26.1, 26.2 and 26.3 on Fabric (with Fabric API) or NeoForge, on clients and dedicated servers."
---

# Install Quest Plus

| Requirement    | Version              |
| -------------- | -------------------- |
| Minecraft Java | 26.1 to 26.3 (NeoForge: 26.1.2, 26.2, 26.3) |
| Loader         | Fabric or NeoForge   |
| Java           | 25+                  |
| Fabric API     | Fabric only, for your Minecraft |

Download the JAR for your loader: `questplus-<version>.jar` for Fabric, `questplus-<version>-neoforge.jar` for NeoForge.

- **Fabric:** put Quest Plus and Fabric API in `mods/` on the client and server.
- **NeoForge:** put Quest Plus in `mods/` on the client and server. No other mod is required.

The tablet animations are built into Quest Plus. No separate animation library is needed. The 2D interface remains available in settings. Minimum loader versions are listed in [Compatibility](/guide/compatibility).

## Check the installation

Start Minecraft and check that the mod loads without dependency errors. For multiplayer, use matching QuestPlus versions and the same relevant content mods on the server and clients. Shaders are optional.

QuestPlus creates a default pack in `config/questplus/default/` when none exists. New worlds receive their own definitions in `<world>/questplus/`. You can play the bundled quests without a website account or a web service.

## First steps

1. Join your world and press **J**.
2. Choose a category and a quest.
3. Complete the objective and claim your reward.

Existing world quest definitions are not overwritten when the mod updates.

![The QuestPlus tablet in a newly generated world](/media/tablet.webp)

_The tablet presents the server's available quests. Its appearance can be changed in Settings._

## Install a custom pack

Use a QuestPlus ZIP exported by Studio. Back up the world and follow [Import and publish](/guide/import-and-publish). Pack files go in the world's definition folder, while player progress stays in `<world>/data/questplus/`.

For a modpack, distribute `config/questplus/default/` so new worlds start with your quests. Follow the [modpack creator guide](/guide/modpack-creators) before updating an existing world.

[Use the tablet](/guide/tablet) · [Quest blocks](/guide/world-blocks) · [Quest givers and loot crates](/guide/givers-and-crates) · [Compatibility](/guide/compatibility) · [Troubleshooting](/guide/troubleshooting)
