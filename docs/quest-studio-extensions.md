# Quest Plus integration API — Quest Studio authoring descriptors (experimental)

Server-side Java integrations remain authoritative. Mods can use `QuestPlusApi.registerEvent`, `registerCondition`, and `registerReward` to add mechanics. To expose an editor-friendly form, register an **optional data-only descriptor** during mod initialization.

```java
import com.google.gson.*;
import dev.questplus.fabric.api.QuestPlusApi;

QuestPlusApi.registerEvent("example:machine_complete", "block", false);

JsonArray fields = new JsonArray();
fields.add(JsonParser.parseString(
    "{\"key\":\"recipe\",\"type\":\"string\",\"label\":\"Recipe identifier\"}"
));
QuestPlusApi.registerEditorDescriptor(
    "event", "example:machine_complete", "Complete a machine recipe", fields
);
```

Descriptors may declare `kind` = `event`, `condition`, or `reward`, a namespaced `id`, label and up to 20 input field declarations. Supported field `type`: `string`, `integer`, `boolean`, `item`, `block`, `entity`, `biome`, `dimension`, `enchantment`. The editor only displays metadata from the previously approved Quest Hub session. It never executes scripts from descriptors.

### How it appears in Studio

- Event descriptors are added to the Action selector. The event's target remains a namespaced ID; the descriptor's extra `fields` are currently *informational*, because objective data cannot yet hold arbitrary extension form fields.
- Reward descriptors appear in the shared normal/tier reward-type selector and are edited as `data` fields. Registry-backed reward fields use the existing target picker where supported. The extension must already have called `registerReward` and its registered server validator controls which values are accepted.
- Condition descriptors render declared scalar fields in the condition editor. Registry-aware condition pickers and objective parameter payloads remain incomplete.
- The **Mod Registry Browser** uses the same approved-session registry snapshot as the editor and can also import an offline JSON snapshot. Offline data is for editing only and cannot influence server gameplay state.

### Compatibility

The descriptor contract is experimental and must not be mistaken for a security sandbox: extensions are trusted Java mods running inside the server. Unknown/unregistered extension types are rejected by the quest registry, and third-party schemas must be tested with the real server before publishing packs.
