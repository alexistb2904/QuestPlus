# Mod integrations and registry browser

## Use installed mod resources

Quest Studio supports namespaced IDs such as `minecraft:stone` or `my_mod:custom_block`. Choose **Mod Registry Browser** to inspect an approved server snapshot or import an offline registry JSON file.

An offline snapshot helps with authoring. It **does not** install a mod, make an ID valid on the server, or add gameplay event hooks.

## Register custom mechanics

Server extensions can register event, condition and reward handlers using the Java `QuestPlusApi` interface. They can also provide optional editor descriptors with typed fields.

The supported descriptor kinds are `event`, `condition` and `reward`. Field types include strings, booleans, integers and Minecraft registry-backed values such as item, block, entity, biome, dimension and advancement.

The descriptor is only form metadata. Quest Studio never executes scripts supplied with a descriptor. The Java server handler must validate and implement the actual mechanic.

See [the API and descriptor example](https://github.com/alexistb2904/QuestPlus/blob/main/docs/quest-studio-extensions.md) and [the Java API](https://github.com/alexistb2904/QuestPlus/blob/main/server/src/main/java/dev/questplus/fabric/api/QuestPlusApi.java).

## Stages, fluids and energy

Stage providers can be replaced by an authorized Java integration. Fluid and energy objectives have backend contracts, but a third-party tank or cable mechanic may still require an adapter. Claiming that a resource ID exists does not prove the external system is hooked.

## FTB import is a conversion, not a runtime bridge

FTB Quests source definitions can be converted when Quest Plus has equivalent semantics. A KubeJS or custom FTB task is not automatically executed in Quest Plus. Port the required mechanic as a server extension.

Read [capability boundaries](/reference/limitations) before using imported packs in production.
