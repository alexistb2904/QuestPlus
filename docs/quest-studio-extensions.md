# Quest Plus integration API — Quest Studio authoring descriptors (experimental)

Server-side Java integrations remain authoritative. Mods can use `QuestPlusApi.registerEvent`, `registerCondition`, and `registerReward` to add mechanics. To expose an editor-friendly form, register an **optional data-only descriptor** during mod initialization.

```java
import com.google.gson.*;
import dev.questplus.fabric.api.QuestPlusApi;

QuestPlusApi.registerEvent("example:machine_complete", "block", false);
QuestPlusApi.registerObjectiveData("example:machine_complete", data -> {
    if (!data.keySet().equals(java.util.Set.of("recipe"))
        || !data.get("recipe").isJsonPrimitive()
        || !data.get("recipe").getAsJsonPrimitive().isString())
        throw new IllegalArgumentException("Expected recipe");
}, (data, event) -> data.get("recipe").getAsString().equals(event.context().get("recipe")));

JsonArray fields = new JsonArray();
fields.add(JsonParser.parseString(
    "{\"key\":\"recipe\",\"type\":\"string\",\"label\":\"Recipe identifier\"}"
));
QuestPlusApi.registerEditorDescriptor(
    "event", "example:machine_complete", "Complete a machine recipe", fields
);
```

Descriptors may declare `kind` = `event`, `condition`, or `reward`, a namespaced `id`, label and up to 20 input field declarations. Supported field `type`: `string`, `integer`, `boolean`, `item`, `block`, `entity`, `biome`, `dimension`, `enchantment`, `fluid`, `structure`, `statistic`, `advancement`, `effect`. The editor only displays metadata from the previously approved Quest Hub session. It never executes scripts from descriptors.

### How it appears in Studio

- Event descriptors are added to the Action selector. The event’s target remains a namespaced ID; descriptor fields edit `objective.data`. Nonempty data needs a registered `registerObjectiveData` validator and matcher. The matcher receives the trusted event, runs before progression and can reject a different recipe/mode. Data is bounded to 16 KiB and omitted from the player projection.
- Reward descriptors appear in the shared normal/tier reward-type selector and are edited as `data` fields. Registry-backed reward fields use the existing target picker where supported. The extension must already have called `registerReward` and its registered server validator controls which values are accepted.
- Condition descriptors render declared fields and registry pickers, as do objective and reward forms. The server extension remains responsible for the complete schema and registry semantics. Unknown descriptors can be retained offline for validation on the server.
- The **Mod Registry Browser** uses the same approved-session registry snapshot as the editor and can also import an offline JSON snapshot. Offline data is for editing only and cannot influence server gameplay state.

### Compatibility

The descriptor contract is experimental and must not be mistaken for a security sandbox: extensions are trusted Java mods running inside the server. Unknown/unregistered extension types are rejected by the quest registry, and third-party schemas must be tested with the real server before publishing packs.

### Stages and named tables

`QuestPlusApi.registerStageProvider(QuestStages.Provider)` replaces the default vanilla-tag provider once during initialization. All stage reads/writes require the Minecraft server thread. `stage` objectives poll that provider; `questplus:stage` conditions read its snapshot, and stage rewards set/remove through it. No external stage mod is required; install and test a provider to share its state with one.

Studio’s named table library lives in `queststudio-compatibility.json` as `rewardTables: [{name, reward}]`. Use `{type:"questplus:table", data:{name:"Starter"}}` in quest/tier rewards to share a table. The candidate registry resolves references before activation (maximum depth 16, 128 tables, no cycles). Entitlements already granted retain their concrete outcomes across table edits. This is not a crate-item API; nested crates and arbitrary extension pools remain unsupported.
