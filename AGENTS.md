# Quest Plus

Independent backend for Minecraft Java 26.3 / Fabric / Java 25. Never copy code from Wilderness Quest.
Use Ponytail full. Do not use Superpowers. No client rendering, UI, Blockbench or web editor in this phase.

## Working contract

- Inspect the affected code, callers, configuration, fixtures and tests before editing.
- Keep Minecraft/Fabric imports in `dev.questplus.fabric`; the core and persistence stay Java-only.
- The server thread owns mutable gameplay state. Disk workers receive detached snapshots only.
- Never replace corrupt/unknown-version saves with empty progress. Never reset reward claim history implicitly.
- Keep quest IDs and objective IDs stable; validate the whole registry before swapping it on reload.
- No disk write per gameplay event, no scan of every quest each tick, no client-authoritative progress.
- Do not implement future features as empty classes. Unsupported JSON must fail with a useful error.
- Preserve unrelated changes. Run the main behavior tests and compile at each important milestone.

## Native Codex orchestration

Use project agents in `.codex/agents/`: architect = GPT-6.1 Sol high; developer = GPT-6.1 Terra medium;
tester = GPT-6 Luna high; reviewer = GPT-6.1 Sol medium.
The parent owns architecture, integration and final verification. Select roles automatically by task.
At most TWO child agents simultaneously; no nested delegation. Avoid agents for trivial operations.
Give exclusive file ownership, tell agents they share the workspace, and inspect their actual output.
Do not have multiple agents modify the same files or repeat the same investigation.
If agent definitions are not discovered in the current session, use native spawn with the exact requested
model/effort and report this fallback. Reopen the Codex session to load the new project configuration.

## Verification

Use Java 25, then `./gradlew build`. See README for server integration checks.
Keep `docs/roadmap.md` honest: a build does not prove multiplayer, gameplay hooks or crash recovery.
Never call a feature functional unless it has implementation and appropriate executed checks.
