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
