---
title: "JSON5 quest pack format"
description: "Reference for the Quest Plus JSON5 quest pack format: example quest, folder layout, categories, configuration and companion files."
---

# JSON5 quest packs

Quest Studio generates the files for you. Direct JSON5 editing is also supported.

## Example quest

Save this category as `categories/basics.json5` first.

```json5
{
	id: "example:basics",
	icon: "minecraft:stone",
	title: { en_us: "Basics" },
	description: { en_us: "Your first steps." },
}
```

Then save the quest as `quests/basics/stone.json5`.

```json5
{
	schemaVersion: 2,
	id: "example:stone",
	category: "example:basics",
	icon: "minecraft:stone",
	title: { en_us: "Collect stone" },
	objectives: [{ id: "stone", event: "item_obtained", target: "minecraft:stone", count: 32 }],
	rewards: [{ type: "experience", amount: 50 }],
	progression: "once",
}
```

The category must exist, and quest and objective IDs must stay stable once progress is saved.

## Folder layout

```text
questplus/
  config.json
  categories/
    basics.json5
  quests/
    basics/
      stone.json5
```

Install the pack under `<world>/questplus/`. Player save data is stored separately.

Studio also opens a single JSON file shaped as `{ "targetMinecraftVersion": "26.3", "categories": [...], "quests": [...] }`. This is the format the prompt behind the **Create your own** button in Studio asks an AI to produce. Export it as a ZIP to get the folder layout above.

## Configuration and companion files

A minimal `config.json` uses the configuration schema, which is separate from the quest schema version.

```json
{
	"schemaVersion": 1,
	"autosaveSeconds": 5
}
```

Studio can also export `queststudio-compatibility.json` for supported metadata and named reward tables. Keep companion files with the pack. A named table reference must resolve before the server activates the registry.

## Core quest fields

| Field           | Meaning                                          |
| --------------- | ------------------------------------------------ |
| `schemaVersion` | Quest schema version, currently 1 or 2           |
| `id`            | Stable namespaced quest identity                 |
| `category`      | Existing category ID                             |
| `icon`          | Resource ID or a supported embedded icon         |
| `title`         | Translation map with `en_us`                     |
| `objectives`    | Stable objective IDs, events, targets and counts |
| `rewards`       | Validated grants or supported pools              |
| `requires`      | Prerequisite expression                          |
| `progression`   | `once`, `repeatable`, `infinite` or `tiered`     |
| `layout`        | Diagram coordinates, separate from progression   |

Version 1 supports once quests. Version 2 enables advanced progression and related options. Unknown properties, invalid registry IDs, missing dependencies and cyclic prerequisites are rejected.

## Add a prerequisite

This fragment requires a completed quest. Place it in another quest definition whose ID is different.

```json5
requires: { quest: 'example:stone' }
```

Use `and`, `or` and `not` to combine requirements. The loader checks referenced quests and rejects cycles. Information quests with read only acknowledgment cannot act as completion prerequisites.

## Validate at both boundaries

Editor validation catches authoring errors. The Minecraft server also validates the complete pack against installed mods before it can replace active definitions. A valid JSON file alone does not establish that an event has a gameplay integration.

Preserve quest, objective and tier IDs after release. Text changes do not rename progress. Mechanical changes can invalidate partial counters. Back up and test upgrades on a copy.

[JSON Schema](https://github.com/alexistb2904/QuestPlus/blob/main/docs/quest.schema.json) · [Custom objective events](/reference/java-api)
