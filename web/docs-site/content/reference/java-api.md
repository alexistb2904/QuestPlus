# Java integration

Call `dev.questplus.fabric.api.QuestPlusApi` from your **server side** Fabric mod.

On the Minecraft server thread, `getQuests(player)` lists loaded IDs and
`getQuest(player, id)` returns a detached JSON representation of the loaded
definition. `getProgress(player, id)` returns a snapshot of the player's
progress, or their team's shared progress when applicable. Its objective map
is immutable and contains no reward claims or entitlements. Use
`getObjectiveProgress(player, questId, objectiveId)` for a single counter.
Unknown quest and objective IDs are rejected. Progress reads can throw
`IOException` when saved player data cannot be loaded. These methods are for
trusted server-side integrations, not browser authorization or pack editing.

`completeQuest(player, id)` grants a quest using the same team and reward
entitlement path as the operator grant command. It rejects a completed
one-time quest or a repeatable quest awaiting restart. `resetQuest(player, id)`
resets the player's or team's quest progress and retains reward claims. Both
are trusted administrative operations on the server thread. Completion queues
a durable transaction and can fail if the affected players are busy or storage
is unavailable. Reset uses the normal progress autosave path.
The completion method returns before the transaction finishes and does not
report a later storage failure to its caller.
Administrative completion currently accepts only `once` and `repeatable`
progression. It rejects tiered and infinite quests rather than bypassing their
cycle and tier rules.

Register extensions during mod initialization before packs load. Report events only after the real server action succeeds. Keep all mutable gameplay state on the Minecraft server thread.

## Imports

The examples use the following imports, plus your own player and recipe variables.

```java
import dev.questplus.fabric.api.QuestPlusApi;
import dev.questplus.fabric.QuestPlusMod;
import dev.questplus.core.QuestEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
```

## Report a custom event

Register on mod initialization:

```java
QuestPlusApi.registerEvent(
    "example:machine_complete",
    "block",
    false
);
```

After the machine actually completes a recipe, on the server thread:

```java
Map<String, Object> context =
    new HashMap<>(QuestPlusMod.context(player));
context.put("recipe", recipeId.toString());
QuestPlusApi.submit(
    player,
    new QuestEvent(
        "example:machine_complete",
        "example:machine",
        Set.of(), 1, context
    )
);
```

Import `QuestPlusMod` from `dev.questplus.fabric` and `QuestEvent` from `dev.questplus.core`. Use a valid registered block ID as the target.

## Match custom options

```java
QuestPlusApi.registerObjectiveData(
    "example:machine_complete",
    data -> {
        if (!data.keySet().equals(Set.of("recipe"))
            || !data.get("recipe").isJsonPrimitive()
            || !data.get("recipe").getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Missing recipe");
    },
    (data, event) -> data.get("recipe").getAsString()
        .equals(event.context().get("recipe"))
);
```

Quest authors can set `objective.data.recipe`. Expose an editor field with `registerEditorDescriptor("event", id, label, fields)`, where `fields` is a Gson `JsonArray` of objects with `key`, `type` and `label`.

## Other hooks

- `registerCondition(id, validator, predicate)` for custom checks.
- `registerReward(id, validator, fits, deliver)` for rewards.
- `registerPoolReward(...)` for trusted pool entries.
- `registerStageProvider(provider)` for stage bridges.
- `reportFluidTransferred(player, fluidId, millibuckets)`.
- `reportEnergyTransferred(player, energy)` for FE.

Never trust client claims. Execute on the server thread, validate data and handle reward retries safely.

## Reward handlers

`registerReward` takes a validator, a capacity predicate and a delivery callback. Reject invalid data in the validator, check whether delivery fits before accepting it, and avoid unrelated side effects in that predicate.

Custom delivery can cross a persistence boundary that Minecraft inventories cannot make transactional. Design and test interruption recovery before enabling a handler in a production pack. `registerPoolReward` explicitly opts a trusted handler into immutable pools, so only use it when that reward is safe for the pool contract.

## Fluid and energy units

`reportFluidTransferred(player, fluidId, millibuckets)` reports successful transfer in millibuckets. `reportEnergyTransferred(player, energy)` reports FE. Both require a positive amount no larger than 1,000,000,000. Do not report attempted transfers or stored capacity as new progress.

## Test the integration

Create a minimal pack with your event and data, verify its load, then exercise success, failure, reconnect and duplicate delivery paths. Confirm that Studio shows the descriptor from an approved registry snapshot and that an unknown extension is rejected on a server without your mod.

[Editor descriptors](/reference/extensions) · [Build and contribute](/reference/development)

[All objectives](/guide/objectives) · [All rewards](/guide/rewards)

## Read-only inspection

`getQuest(player, id)`, `getObjective(player, questId, objectiveId)` and `getCategories(player)` return detached JSON metadata. `getQuests(player)` returns sorted IDs, `isQuestUnlocked(player, questId)` evaluates server-side prerequisites, and `getTeamId(player)` returns an optional team UUID. `getProgress(player, questId)` and `getObjectiveProgress(player, questId, objectiveId)` expose server-authoritative counters without reward claims or internal mutable state.

## Trusted progress mutations

Use these operations only for server-authorized integrations, never for client-provided claims:

```java
QuestPlusApi.setObjectiveProgress(player, "example:automation", "energy", 150);
QuestPlusApi.incrementObjectiveProgress(player, "example:automation", "energy", 10);
```

Both operations target a single objective, enforce its configured maximum, and reuse normal prerequisites, completion, team and reward-entitlement paths. They run synchronously on the Minecraft server thread and may raise `IOException`, `IllegalArgumentException` or `IllegalStateException`. Mutations cannot update a terminal attempt or bypass an in-progress save. An override is **administrative**: it does not prove that a real Minecraft interaction happened.

Subscribe to lifecycle events with a closeable handle:

```java
AutoCloseable subscription = QuestPlusApi.onQuestCompleted(change -> {
    // Executed on the server thread after durable completion/entitlement saving.
    String questId = change.questId();
});
// Close the subscription when the integration unloads:
subscription.close();
```

Also available: `onObjectiveProgress`, `onObjectiveCompleted`, `onRewardClaimed`, `onQuestReset` and `onQuestEvent(type, listener)`. Objective events are in-memory notifications. Do not use them as an exactly-once external payment or reward ledger. The completion callback runs only after the completion transaction succeeds. Listener exceptions are logged and isolated.

## Future KubeJS bridge

KubeJS is **not** a QuestPlus dependency. Trusted Fabric mods can register a lifecycle adapter at initialization:

```java
QuestPlusApi.registerAdapter("example:bridge", new QuestPlusAdapters.Adapter() {
    public void started() { /* attach to the scripting runtime */ }
    public void definitionsReloaded() { /* refresh script-defined registrations */ }
    public void stopped() { /* release subscriptions and script resources */ }
});
```

Import `dev.questplus.fabric.api.QuestPlusAdapters`. Register before the server starts. The adapter is called only when installed. Use closeable event subscriptions and release them on script reload to avoid duplicate handlers. Loading scripts, exposing KubeJS events and scripting-language-specific conversion are **not implemented**. The eventual bridge must use the real compatible KubeJS API, not placeholder classes.

Mod-defined callbacks should verify gameplay server-side and take care not to recursively issue the same quest mutation.
