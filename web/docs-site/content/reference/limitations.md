---
title: "Capabilities and limits"
description: "What Quest Plus supports and what it does not: Forge, Bedrock, scripts, unknown mod actions and FTB Quests custom tasks."
---

# Capabilities and limits

## Supported

- Categories, quest trees and diagram annotations
- Objectives, submissions, conditions and prerequisites
- Repeatable and tiered quests
- Reward choices, random pools and tables
- Team progression and server commands
- Fabric and NeoForge
- Java integrations with other Fabric or NeoForge mods
- Compatible FTB Quests imports
- Offline or approved online editing

## Not supported

| Request                                 | Limit                           |
| --------------------------------------- | ------------------------------- |
| Forge or Bedrock                        | No supported build              |
| NeoForge on Minecraft 26.1 or 26.1.1    | No stable NeoForge build        |
| Run arbitrary scripts from quest files  | Not permitted                   |
| Track unknown mod actions automatically | Requires a Java integration     |
| Import every FTB custom task            | May require a manual conversion |

[How to integrate a mod](/reference/java-api)

## Evidence and release scope

An editor preview checks authored content, while the Minecraft server validates registry references and progression rules. A browser cannot grant progress or authorize its own publication.

The documentation media includes real Minecraft and Studio sessions. Those captures do not prove every shader setting, imported pack or multiplayer combination. Test your exact modpack on a dedicated server with two real clients before making compatibility claims.

Protocol sessions and publication queues are process local. A larger deployment needs an explicit shared state design rather than several independent writers using one storage folder.

[Compatibility](/guide/compatibility) · [Build and contribute](/reference/development)
