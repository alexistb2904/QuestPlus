# Client — Minecraft presentation

This folder contains Minecraft **client-only** code and resources: the
native animated tablet, tree diagram, quest information, HUD, notifications,
camera, localization, mixins and render assets.

- Java: `src/main/java/dev/questplus/fabric/client/` and client models.
- Assets: `src/main/resources/assets/questplus/`.
- Client mixins: `src/main/resources/questplus.client.mixins.json`.
- Unit and Fabric client GameTests: `src/test/` and `src/gametest/`.

The client **does not** authorize website users or own server quest state.
It renders the projections delivered by the existing `QuestNetwork` protocol.
The server validates new quests and invalidates client state after publishing.

This folder is **not a separate Minecraft mod**. The Gradle main source
set combines this folder with `../server/` to build one Fabric JAR, with
the original client entrypoint retained in `server/src/main/resources/fabric.mod.json`.

Do not add persistent player data, remote admin credentials or a dedicated
HTTP listener to the client.
