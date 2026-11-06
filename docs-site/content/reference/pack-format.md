# Pack format and file layout

Quest Plus uses versioned definition files. The current authoring workflow exports **JSON5** for quests and categories, while world configuration and player progress remain strict JSON.

## World layout

```text
<world>/
  questplus/
    config.json
    queststudio-compatibility.json
    index.txt
    categories/
      engineering.json5
    quests/
      engineering/
        first_circuit.json5
  data/
    questplus/
      ...
```

The definitions folder and persistent player state are intentionally separate. Never ship `data/questplus/` as part of an authored quest pack.

## Core quest fields

| Field | Meaning |
| --- | --- |
| `schemaVersion` | Quest schema, currently 1 or 2 |
| `id` | Stable `namespace:path` identifier |
| `category` | Existing category ID |
| `icon` | Minecraft or modded icon ID |
| `title` | Localized map with `en_us` |
| `description` | Optional localized explanation |
| `objectives` | Tasks with stable local IDs |
| `objectiveMode` | `all`, `any`, `sequence` or `at_least` |
| `requires` | Dependency expression |
| `rewards` | Server-recognized reward entries |
| `progression` | `once`, `repeatable`, `infinite` or `tiered` |
| `layout` | Optional diagram coordinates |

JSON5 allows comments and trailing commas in authored definitions. Unknown field names or unregistered event/reward types can still fail server validation.

## Advanced authoring

- Use unique category and quest IDs within the pack.
- Keep objective IDs stable so old progress is not accidentally orphaned.
- Keep dependency expressions acyclic.
- Prefer resource IDs and tags from installed mods rather than hard-coded display names.
- Validate large annotation images and rich text before export.
- Test mechanics that depend on other installed mods on an actual matching server.

## Canonical sources

The complete [JSON Schema](https://github.com/alexistb2904/QuestPlus/blob/main/docs/quest.schema.json) and [detailed format notes](https://github.com/alexistb2904/QuestPlus/blob/main/docs/json-schema.md) are maintained with the mod.

Use Quest Studio's **Advanced JSON** panel only when the visual controls cannot express a supported field. The validation and export path remains the same.
