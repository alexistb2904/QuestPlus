# Java integration

Call `dev.questplus.fabric.api.QuestPlusApi` from your **server-side** Fabric mod.

## Report a custom event

Register on mod initialization:

~~~java
QuestPlusApi.registerEvent(
    "example:machine_complete",
    "block",
    false
);
~~~

After the machine actually completes a recipe, on the server thread:

~~~java
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
~~~

Import `QuestPlusMod` from `dev.questplus.fabric` and `QuestEvent` from `dev.questplus.core`. Use a valid registered block ID as the target.

## Match custom options

~~~java
QuestPlusApi.registerObjectiveData(
    "example:machine_complete",
    data -> {
        if (!data.has("recipe") || data.size() != 1)
            throw new IllegalArgumentException("Missing recipe");
    },
    (data, event) -> data.get("recipe").getAsString()
        .equals(event.context().get("recipe"))
);
~~~

Quest authors can set `objective.data.recipe`. Expose an editor field with `registerEditorDescriptor("event", id, label, fields)`, where `fields` is a Gson `JsonArray` of objects with `key`, `type` and `label`.

## Other hooks

- `registerCondition(id, validator, predicate)` for custom checks.
- `registerReward(id, validator, fits, deliver)` for rewards.
- `registerPoolReward(...)` for trusted pool entries.
- `registerStageProvider(provider)` for stage bridges.
- `reportFluidTransferred(player, fluidId, millibuckets)`.
- `reportEnergyTransferred(player, energy)` for FE.

Never trust client claims. Execute on the server thread, validate data and handle reward retries safely.

[All objectives](/guide/objectives) · [All rewards](/guide/rewards)
