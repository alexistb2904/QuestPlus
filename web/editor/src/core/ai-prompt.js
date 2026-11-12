// A tiny but complete pack the prompt shows the AI. tests/ai-prompt.test.mjs keeps it valid.
const label = (id, x, y, text, color) => ({ id, style: "label", x, y, width: 240, height: 34, color, text: { en_us: text }, rich: { en_us: [{ type: "text", text, color, size: 1.4 }] } });
const quest = (id, x, y, title, description, objectives, rewards, requires) => ({
	schemaVersion: 2,
	id: "mypack:" + id,
	category: "mypack:getting_started",
	icon: objectives[0].target,
	size: 45,
	title: { en_us: title },
	description: { en_us: description },
	objectives,
	rewards,
	progression: "once",
	...(requires && { requires: { quest: "mypack:" + requires } }),
	layout: { x, y },
});
const obtain = (...items) => items.map((item) => ({ id: item.split(/[:_]/).pop(), event: "item_obtained", target: "minecraft:" + item, count: 1 }));

export const AI_PROMPT_EXAMPLE = {
	targetMinecraftVersion: "26.3",
	categories: [
		{
			id: "mypack:getting_started",
			order: 0,
			icon: "minecraft:crafting_table",
			title: { en_us: "Getting Started" },
			description: { en_us: "First steps and your first armor." },
			annotations: [
				{ id: "bg_basics", style: "background", x: -160, y: 0, width: 220, height: 120, color: "#5b6b4a", text: { en_us: "Basics" } },
				label("title_basics", -160, -85, "Basics", "#c9d6a3"),
				{ id: "bg_armor", style: "background", x: 140, y: 0, width: 320, height: 120, color: "#5a5f73", text: { en_us: "Armor" } },
				label("title_armor", 140, -85, "Armor progression", "#c3c8de"),
				{ id: "tip_basics", style: "note", x: -160, y: 110, width: 220, height: 60, color: "#c6a986", text: { en_us: "Do these in any order." } },
			],
		},
	],
	quests: [
		quest("crafting_table", -200, 0, "A Place to Craft", "Craft a crafting table.", [{ id: "craft", event: "item_craft", target: "minecraft:crafting_table", count: 1 }], [{ type: "experience", amount: 20 }]),
		quest("furnace", -120, 0, "Hot Stuff", "Craft a furnace.", [{ id: "craft", event: "item_craft", target: "minecraft:furnace", count: 1 }], [{ type: "item", item: "minecraft:coal", count: 8 }]),
		quest("leather_armor", 40, 0, "Leather Armor", "Get a full leather set.", obtain("leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"), [{ type: "experience", amount: 50 }]),
		quest("iron_armor", 140, 0, "Iron Armor", "Get a full iron set.", obtain("iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots"), [{ type: "experience", amount: 120 }], "leather_armor"),
		quest("diamond_armor", 240, 0, "Diamond Armor", "Get a full diamond set.", obtain("diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots"), [{ type: "experience", amount: 300 }], "iron_armor"),
	],
};

export const AI_PROMPT_FIELDS = ["theme", "mods", "goals", "guided", "size", "languages", "rewards"];

export function aiPrompt(version, details = {}) {
	const example = JSON.stringify({ ...AI_PROMPT_EXAMPLE, targetMinecraftVersion: version }, null, 1);
	const v = (key, fallback = "not specified, choose what fits best") => String(details[key] || "").trim().replace(/\s*\n\s*/g, ", ") || fallback;
	return `You are an expert Minecraft modpack quest designer. Build a complete, polished quest pack for the QuestPlus mod on Minecraft Java ${version}.

## My modpack
- Modpack name and theme: ${v("theme")}
- Mods in the pack: ${v("mods", "vanilla only")}
- What the quests should cover: ${v("goals")}
- Mods that are complex or little known and need a guided path: ${v("guided", "none")}
- Size: ${v("size")}
- Quest languages: en_us is always required. Also: ${v("languages", "none")}
- Rewards style: ${v("rewards")}

## Output
Return ONE strict JSON file named questplus-pack.json (no comments, no trailing commas). If you can create files, give it as a download, otherwise put the whole file in a single \`\`\`json code block and nothing else in it. I will open it in QuestPlus Studio with File > Open. Shape:
{ "targetMinecraftVersion": "${version}", "categories": [...], "quests": [...] }

## Prerequisites: the most important design rule
Prerequisites (\`requires\`) lock a quest until others are done. Use them sparingly, or players feel forced and stop doing quests.
- Simple vanilla tasks (craft a crafting table, a furnace, a bed, smelt iron, find a village...) get NO prerequisite. They are free quests, done in any order.
- Use prerequisite chains as a GUIDE for a mod that is complex or little known: each step teaches the next thing to build or do in that mod (first machine, how to power it, how to automate it...).
- Armor and gear tiers form a progression (leather, then iron, then diamond, then netherite, or the mod's equivalent) so players do not skip tiers.
- Big milestones (a boss, a new dimension) may require one or two key quests, not a whole tree.
- Most quests in the pack should have no prerequisite. Never chain unrelated quests just to draw lines.

## Format rules (the server rejects anything else)
- IDs are lowercase \`namespace:path\` matching ^[a-z0-9_.-]+:[a-z0-9_./-]+$. Use one namespace for the pack (a short slug of the modpack name), categories like "mypack:tech" and quests like "mypack:tech/first_machine". Every ID is unique.
- Only use item, block, entity, biome, structure, dimension and advancement IDs that really exist in Minecraft ${version} or in the listed mods. If you are not sure an ID exists, pick another objective. Never invent IDs. Item tags are allowed as targets with a leading # (for example "#minecraft:logs").
- Every title, description and annotation text is a map with "en_us" plus the other requested languages.
- Category: id, order (0, 1, 2...), icon (an item ID), title, description, annotations.
- Quest: schemaVersion 2, id, category, icon (an item ID), size 45, title, description (one short sentence for the player), objectives, rewards, progression "once", layout {x, y}, and requires only when the rule above says so.
- Objective: { "id": lowercase local id unique in the quest, "event", "target", "count": positive integer }. Events: item_obtained, item_craft, item_smelt, item_consume, item_turn_in (takes the items), inventory_count, block_break, block_place, entity_kill, tame_animal, breed_animal, biome_visit, structure_visit, dimension_enter, advancement_complete. A quest with several objectives needs all of them.
- Rewards: { "type": "experience", "amount": N } or { "type": "item", "item": "<item id>", "count": N } (count 1 to 2304). No command rewards.
- requires: { "quest": "<quest id>" }, or { "and": [ ... ] } / { "or": [ ... ] } of those. Prefer quests of the same category. No cycles.

## Layout: make each category look designed
- layout x/y is the CENTER of the quest node, in canvas pixels. A quest node is 45 pixels wide. Keep about 80 pixels between quest centers and align quests on a clean grid.
- Group related quests into sections. Each section has a "background" annotation (a colored rectangle drawn behind its quests) and a "label" annotation (the section title) just above it. Annotation x/y is also the center.
- Every quest of a section sits fully inside its background with at least 30 pixels of margin. Backgrounds and labels never overlap each other.
- Guide chains read left to right or top to bottom along the chain. Free quests sit in a tidy grid.
- Use muted, fairly dark background colors (#rrggbb), a different one per section, and a lighter matching color for the label. Add a few "note" annotations for useful tips, not on every section.
- Annotation ids match ^[a-z0-9_.-]+$ and are unique in their category. At most 128 annotations per category.

## Example (format only, make yours much bigger and specific to my modpack)
${example}

Before answering, check that: every requires points to an existing quest, there is no cycle, most quests have no prerequisite, every quest is inside its section background, every ID is valid and real, and every text has en_us.`;
}
