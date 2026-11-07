# Integrate another mod

Quest Plus accepts trusted **server side Fabric integrations**.

| What you need                | API                                                   |
| ---------------------------- | ----------------------------------------------------- |
| Track a custom action        | `registerEvent` + `submit`                            |
| Validate objective options   | `registerObjectiveData`                               |
| Add a condition              | `registerCondition`                                   |
| Add a reward                 | `registerReward`                                      |
| Show custom fields in Studio | `registerEditorDescriptor`                            |
| Bridge stages                | `registerStageProvider`                               |
| Count fluid or FE transfer   | `reportFluidTransferred` or `reportEnergyTransferred` |

[**Copy Java examples →**](/reference/java-api)

## Mod Registry Browser

In Quest Studio, use **Mod Registry Browser** to select IDs from a connected server or imported registry snapshot. A snapshot helps authoring but cannot install mods or create gameplay handlers.

![The registry browser explaining how to load server or snapshot data](/media/registry.webp)

## Expose an editor form

Register the gameplay event, condition or reward first. Then call `registerEditorDescriptor` with kind `event`, `condition` or `reward`, a namespaced ID, a label and a Gson array of fields.

```java
JsonArray fields = new JsonArray();
fields.add(JsonParser.parseString(
    "{\"key\":\"recipe\",\"type\":\"string\",\"label\":\"Recipe identifier\"}"
));
QuestPlusApi.registerEditorDescriptor(
    "event", "example:machine_complete", "Complete a machine recipe", fields
);
```

Each field has exactly `key`, `type` and `label`. Supported types include `string`, `integer`, `boolean`, `item`, `block`, `entity`, `biome`, `dimension`, `enchantment`, `fluid`, `structure`, `statistic`, `advancement` and `effect`. A descriptor can contain up to 20 fields.

Descriptors supply data only. They never execute browser scripts and do not replace server validators. An offline project can retain extension data, but the actual server must have the matching integration before accepting it.

## Stages and reusable rewards

A stage provider bridges external progression through `QuestStages.Provider`. Register it during initialization and read or mutate stages on the server thread.

Named reward tables let multiple quests reuse a pool. Studio stores them in `queststudio-compatibility.json`. The candidate registry checks references and cycles before activation. Existing granted entitlements retain their concrete outcomes when a table changes.

Need HTTP requests instead? See [Quest Hub API](/reference/web-api).
