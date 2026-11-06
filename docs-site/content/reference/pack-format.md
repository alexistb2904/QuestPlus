# JSON5 quest packs

Quest Studio generates the files for you. Direct JSON5 editing is also supported.

## Example quest

~~~json5
{
  schemaVersion: 2,
  id: 'example:stone',
  category: 'example:basics',
  icon: 'minecraft:stone',
  title: { en_us: 'Collect stone' },
  objectives: [
    { id: 'stone', event: 'item_obtained', target: 'minecraft:stone', count: 32 }
  ],
  rewards: [{ type: 'experience', amount: 50 }],
  progression: 'once'
}
~~~

The category must exist, and quest and objective IDs must stay stable once progress is saved.

## Folder layout

~~~text
questplus/
  config.json
  categories/
    basics.json5
  quests/
    basics/
      stone.json5
~~~

Install the pack under `<world>/questplus/`. Player save data is stored separately.

[JSON Schema](https://github.com/alexistb2904/QuestPlus/blob/main/docs/quest.schema.json) · [Custom objective events](/reference/java-api)
