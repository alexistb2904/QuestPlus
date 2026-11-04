# Server — authoritative Fabric engine

This folder contains the dedicated-server-safe Java logic, quest definitions
and game integration:

- `src/main/java/dev/questplus/core/`: strict JSON, conditions, objectives,
  reward semantics and progress models.
- `src/main/java/dev/questplus/fabric/`: server lifecycle, OP commands,
  events, networking, reload and optional outbound web integration.
- `src/main/java/dev/questplus/persistence/`: durable saves and reward history.
- `src/main/resources/defaults/`: bundled quest catalogue installation.
- `src/main/resources/fabric.mod.json`: one universal mod descriptor.
- `src/test/` and `src/gametest/`: unit and dedicated-server tests.

The server is the **only** authority that can apply quest definitions, check
the installed mod registries and grant player rewards. Existing saves stay
inside `world/data/questplus/`. Definitions are stored in `world/questplus/`.

When the optional online editor is configured, `QuestHubService` performs
**outbound** HTTPS polling only. It sends a server-only bearer secret, never
a player's progress records. Editor sessions require an in-game operator
approval and a valid browser token; publication is revalidated server-side.

The server stages definitions and keeps backups before reloading. It will
reject unsafe imports and restore the previous definitions on failure. For
deployment see [web/api/README.md](../web/api/README.md).

This folder and `../client/` compile to the **same single Fabric JAR** via
`../build.gradle`. No second mod, network protocol rewrite or save migration
is required.
