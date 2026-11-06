# Categories and quests

## Create a category

1. In Quest Studio, select **Add Category**.
2. Enter the English name.
3. In Properties, set its icon, description, order and optional presentation group.
4. Switch to that category before adding quests.

Each category has a stable namespaced ID such as `questplus:engineering`. Quest references depend on these IDs, so avoid renaming them after a pack has player progress.

A category can also contain visual annotations, background rectangles and routed connection paths. These affect presentation, not game state.

## Create a quest

Select a category and click **Add Quest**. A right-click on the diagram also offers templates. Set the English source title, a Minecraft icon ID, the node position and any optional description.

Then configure four independent parts:

1. **Objectives** describe what the player must do.
2. **Prerequisites** define when the quest can unlock.
3. **Progression** controls repeatability, timing and tiers.
4. **Rewards** define what can be claimed after completion.

Quests may be hidden, have visible prerequisites, or display custom annotations. Category and quest titles can be translated later.

## Connect a quest tree

Select a quest and add a prerequisite from the graphical connection tools or its prerequisite editor. The dependency tree supports boolean logic. The editor rejects missing IDs and cycles.

Connection routes can be repositioned for presentation without changing the underlying prerequisite. If a quest depends on another quest, removing the predecessor requires first updating the dependent quest.

## Information quests

Use a quest of kind `information` when a chapter needs instructions instead of gameplay tasks. A manually acknowledged information quest can be triggered. A read-only information quest does not complete and must not act as a progression prerequisite.

## Minimal files

A category file at `categories/engineering.json5`:

```json5
{
  id: 'questplus:engineering',
  icon: 'minecraft:redstone',
  title: { en_us: 'Engineering' },
  description: { en_us: 'Build practical machines.' },
  annotations: []
}
```

A quest at `quests/engineering/first_circuit.json5`:

```json5
{
  schemaVersion: 2,
  id: 'questplus:engineering/first_circuit',
  category: 'questplus:engineering',
  icon: 'minecraft:repeater',
  title: { en_us: 'First Circuit' },
  objectives: [
    { id: 'craft_repeater', event: 'item_craft', target: 'minecraft:repeater', count: 1 }
  ],
  rewards: [{ type: 'experience', amount: 50 }],
  progression: 'once',
  layout: { x: 0, y: 0 }
}
```

The namespaced ID determines the file's exported path. Quest Studio can author these files visually and export them automatically.

Read [pack format](/reference/pack-format) for JSON5 rules and the canonical JSON Schema.
