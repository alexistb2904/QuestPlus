// A tiny but complete pack the prompt shows the AI. tests/ai-prompt.test.mjs keeps it valid.
const label = (id, x, y, text, color) => ({ id, style: "label", x, y, width: 240, height: 34, color, text: { en_us: text }, rich: { en_us: [{ type: "text", text, color, size: 1.4 }] } });
const quest = (id, x, y, look, title, description, objectives, rewards, requires) => ({
	schemaVersion: 2,
	id: "mypack:" + id,
	category: "mypack:getting_started",
	icon: look.icon || objectives[0].target,
	size: look.size,
	presentation: { shape: look.shape },
	title: { en_us: title },
	description: { en_us: description },
	objectives,
	rewards,
	progression: "once",
	...(requires && { requires }),
	layout: { x, y },
});
const obtain = (...items) => items.map((item) => ({ id: item.split(/[:_]/).pop(), event: "item_obtained", target: "minecraft:" + item, count: 1 }));
const craft = (item) => [{ id: "craft", event: "item_craft", target: "minecraft:" + item, count: 1 }];
const done = (id) => ({ quest: "mypack:" + id });
const FREE = { size: 36, shape: "circle" };

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
				{ id: "bg_hub", style: "background", x: 0, y: 0, width: 100, height: 100, color: "#5b6b4a", text: { en_us: "Hub" } },
				label("title_free", 0, -170, "Free quests, any order", "#c9d6a3"),
				label("title_armor", -150, 200, "Armor path", "#c3c8de"),
				{ id: "tip_diamond", style: "note", x: 180, y: 300, width: 200, height: 60, color: "#c6a986", text: { en_us: "Diamonds need an iron pickaxe." } },
			],
			linkRoutes: [
				{
					from: "mypack:iron_pickaxe",
					to: "mypack:diamond_armor",
					points: [
						{ x: 110, y: 200 },
						{ x: 0, y: 300 },
					],
				},
			],
		},
	],
	quests: [
		quest(
			"welcome",
			0,
			0,
			{ size: 64, shape: "octagon", icon: "minecraft:writable_book" },
			"Welcome",
			"A crafting table, a furnace and a bed will carry you through the first nights. Mobs hit hard early on, so armor comes next: leather is cheap, iron is solid, and diamond will take you to the Nether.",
			[{ id: "read", event: "manual", target: "mypack:welcome", count: 1, text: { en_us: "I am ready" } }],
			[{ type: "item", item: "minecraft:bread", count: 8 }]
		),
		quest("crafting_table", -95, -55, FREE, "A Place to Craft", "Craft a crafting table.", craft("crafting_table"), [{ type: "experience", amount: 20 }]),
		quest("furnace", 0, -110, FREE, "Hot Stuff", "Craft a furnace.", craft("furnace"), [{ type: "item", item: "minecraft:coal", count: 8 }]),
		quest("bed", 95, -55, FREE, "A Good Night", "Craft a bed to skip the night.", craft("white_bed"), [{ type: "experience", amount: 20 }]),
		quest(
			"leather_armor",
			0,
			110,
			{ size: 45, shape: "rounded_square" },
			"Leather Armor",
			"Get a full leather set.",
			obtain("leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"),
			[{ type: "experience", amount: 50 }]
		),
		quest(
			"iron_armor",
			0,
			200,
			{ size: 50, shape: "rounded_square" },
			"Iron Armor",
			"Get a full iron set.",
			obtain("iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots"),
			[{ type: "experience", amount: 120 }],
			done("leather_armor")
		),
		quest("iron_pickaxe", 110, 200, FREE, "Ready for Diamonds", "Craft an iron pickaxe. Diamonds cannot be mined without it.", craft("iron_pickaxe"), [{ type: "experience", amount: 30 }]),
		quest(
			"diamond_armor",
			0,
			300,
			{ size: 60, shape: "diamond" },
			"Diamond Armor",
			"Get a full diamond set.",
			obtain("diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots"),
			[{ type: "experience", amount: 300 }],
			{ and: [done("iron_armor"), done("iron_pickaxe")] }
		),
	],
};

const xp = (amount) => ({ type: "experience", amount });
const WORLD = { xMin: -30000000, xMax: 30000000, zMin: -30000000, zMax: 30000000 };
const CACHE = "Miner cache";

// Format samples the prompt quotes next to each feature. tests/ai-prompt.test.mjs keeps every one of them valid.
export const AI_PROMPT_SAMPLES = {
	quest: {
		repeatable: { progression: "repeatable", cooldownSeconds: 86400 },
		infinite: { progression: "infinite" },
		tiered: {
			objectives: [{ id: "stone", event: "block_break", target: "minecraft:stone", count: 100 }],
			rewards: [],
			progression: "tiered",
			tiers: [
				{ id: "t1", threshold: 1, rewards: [xp(20)] },
				{ id: "t2", threshold: 10, rewards: [xp(100)] },
				{ id: "t3", threshold: 100, rewards: [xp(500)] },
			],
			afterLast: "stop",
		},
		quorum: { objectiveMode: "at_least", objectiveThreshold: 2 },
		challenge: { timeLimitSeconds: 600, noDeath: true },
		information: { kind: "information", acknowledgement: "read_only", objectives: [], rewards: [] },
		presentation: { presentation: { shape: "heart", subtitle: { en_us: "Side quest" }, optional: true } },
	},
	requires: {
		either: { or: [{ quest: "mypack:leather_armor" }, { quest: "mypack:iron_pickaxe" }] },
		quorum: { at_least: { count: 2, of: [{ quest: "mypack:crafting_table" }, { quest: "mypack:furnace" }, { quest: "mypack:bed" }] } },
		started: { started: "mypack:welcome" },
		exclusive: { not: { quest: "mypack:bed" } },
	},
	objectives: {
		gear: {
			id: "armor",
			event: "equipment_check",
			target: "questplus:gear",
			count: 1,
			conditions: { "questplus:gear": { items: [{ slot: "chest", item: "minecraft:diamond_chestplate", enchantments: { "minecraft:protection": 4 } }] } },
		},
		depth: { id: "bottom", event: "location", target: "questplus:position", count: 1, conditions: { position: { ...WORLD, yMin: -64, yMax: -50 } } },
		alternatives: { id: "logs", event: "item_obtained", target: "minecraft:oak_log", count: 16, data: { targets: ["minecraft:oak_log", "minecraft:birch_log"] } },
		conditions: {
			id: "hunt",
			event: "entity_kill",
			target: "minecraft:zombie",
			count: 10,
			conditions: { and: [{ dimension: "minecraft:overworld" }, { tool: "minecraft:wooden_sword" }, { healthAtMost: 6 }] },
		},
	},
	rewards: {
		choice: {
			type: "questplus:choice",
			data: { options: [{ type: "item", item: "minecraft:iron_sword", count: 1 }, { type: "item", item: "minecraft:bow", count: 1 }] },
		},
		random: {
			type: "questplus:random",
			data: { rolls: 1, options: [{ type: "item", item: "minecraft:iron_ingot", count: 8, weight: 3 }, { type: "item", item: "minecraft:diamond", count: 1, weight: 1 }] },
		},
		loot: { type: "questplus:loot", data: { rolls: 2, emptyWeight: 2, options: [{ type: "item", item: "minecraft:emerald", count: 2, weight: 3 }, { ...xp(100), weight: 1 }] } },
		stage: { type: "questplus:stage", data: { stage: "mypack:nether_ready", remove: false } },
		toast: { type: "questplus:toast", data: { title: { en_us: "The Nether awaits" }, description: { en_us: "A new chapter is open." }, icon: "minecraft:obsidian" } },
		message: { type: "questplus:message", data: { message: "The old miner nods. You are ready." } },
		advancement: { type: "questplus:advancement", data: { advancement: "minecraft:story/mine_stone" } },
		table: { type: "questplus:table", data: { name: CACHE } },
		crate: {
			type: "item",
			item: "questplus:loot_crate",
			count: 1,
			components: { "minecraft:custom_data": { questplus_table: CACHE }, "minecraft:custom_name": { text: "Miner Cache", color: "#e0b040" } },
		},
		options: { type: "item", item: "minecraft:golden_apple", count: 1, randomBonus: 2, autoClaim: "enabled", text: { en_us: "A snack for the road" } },
	},
	rewardTables: [
		{
			name: CACHE,
			reward: {
				type: "questplus:loot",
				data: { rolls: 2, emptyWeight: 2, options: [{ type: "item", item: "minecraft:raw_iron", count: 8, weight: 6 }, { type: "item", item: "minecraft:diamond", count: 1, weight: 1 }] },
			},
			crate: true,
			crateName: "Miner Cache",
			crateColor: "#e0b040",
			crateGlow: true,
			crateDrops: { passive: 0, monster: 5, boss: 50 },
		},
	],
	npcs: [
		{
			id: "old_miner",
			name: { en_us: "Old Miner" },
			model: "villager",
			villager: "snow",
			quests: ["mypack:miner_request"],
			spawns: [
				{ type: "structure", target: "#minecraft:village", chance: 0.3 },
				{ type: "structure", target: "#minecraft:mineshaft", chance: 0.05 },
				{ type: "random", target: "minecraft:overworld", chance: 0.0005 },
			],
		},
	],
};

export const AI_PROMPT_FIELDS = ["theme", "mods", "goals", "guided", "size", "languages", "rewards", "tone"];

export function aiPrompt(version, details = {}) {
	const example = JSON.stringify({ ...AI_PROMPT_EXAMPLE, targetMinecraftVersion: version }, null, 1);
	const v = (key, fallback = "not specified, choose what fits best") => String(details[key] || "").trim().replace(/\s*\n\s*/g, ", ") || fallback;
	const S = AI_PROMPT_SAMPLES,
		j = JSON.stringify,
		// The fields of a sample without its braces, to read as "fields to add".
		f = (sample) => j(sample).slice(1, -1);
	return `You are an expert Minecraft modpack quest designer. Build a complete, polished quest pack for the QuestPlus mod on Minecraft Java ${version}.

This brief has seven parts, to read in order:
1. My modpack
2. What to return
3. How to design the pack
4. The file format and every feature
5. The layout
6. An example
7. The final check

## 1. My modpack
- Modpack name and theme: ${v("theme")}
- Mods in the pack: ${v("mods", "vanilla only")}
- What the quests should cover: ${v("goals")}
- Mods that are complex or little known and need a guided path: ${v("guided", "none")}
- Size: ${v("size")}
- Quest languages: en_us is always required. Also: ${v("languages", "none")}
- Rewards style: ${v("rewards")}
- Tone and guidance level: ${v("tone", "not specified, deduce it as explained below")}

## 2. What to return
Return ONE strict JSON file named questplus-pack.json (no comments, no trailing commas). If you can create files, give it as a download. Otherwise put the whole file in a single \`\`\`json code block, with nothing else inside the block. I will open it in QuestPlus Studio with File > Open.
{ "targetMinecraftVersion": "${version}", "categories": [...], "quests": [...], "npcs": [...], "rewardTables": [...] }
- "categories" and "quests" are required.
- "npcs" (quest givers, part 4.11) and "rewardTables" (part 4.9) are optional. Leave them out when you do not use them.
- The pack ships as is, so it must be finished and valid on its own.

## 3. How to design the pack
Work in this order: study the mods, settle the tone, choose the features, then write the categories and the quests.

### 3.1 Study the mod list
Do this before writing a single quest. The mod list of part 1 is your source of truth.
- For each mod, work out what it really adds: its entry item or block, its core machines or mechanics, its tiers, its endgame and its guide book if it has one.
- Work out how the mods relate to each other: which mod produces what another one consumes (power, ores, fluids, mob drops), which one gates another, which ones do the same job.
- Skip libraries, APIs, performance mods and client-only mods. They give no quests.
- From this, decide the categories and which quests are actually possible. Only write a quest when you know the item, block, entity or dimension it needs. For a mod you only half know, write fewer and safer quests, and explain the mod with confirmation quests (part 3.5) instead of guessing IDs.

### 3.2 Settle the tone
- If I gave a tone, follow it. If not, deduce it from the modpack name and theme, the kind of mods (tech, magic, exploration, survival, hardcore), the size I asked for and the rewards style. Keep it for the whole pack.
- The tone decides the voice of the texts, how much you guide, how generous the rewards are, how dense a category is and which features you use.
- If the tone is roleplay or story driven, write every title and description in character, with a narrator voice and a story that moves forward from category to category. Otherwise write like a friendly guide.

### 3.3 Use only the features this pack needs
Part 4 lists everything QuestPlus can do. It is a toolbox, not a checklist. A good pack uses the few tools that serve it and leaves the others out. Choose from the modpack name and theme, the kind of mods, what the quests should cover, the size and the tone. Some leads:
- A relaxed guide pack mostly needs plain quests, confirmation quests and a few milestones.
- A hardcore or challenge pack leans on time limits, no death runs, conditions and tight rewards.
- A roleplay or adventure pack leans on quest givers, story texts, hidden quests and reward choices.
- A long running server pack leans on repeatable contracts, tiered milestones, turn ins and loot crates.
- A tech or factory pack leans on turn ins, ordered steps and automation goals.
These are leads, not rules. Never add a feature only to show that it exists, and never try to use them all. Many packs need no quest giver, no stage and no loot crate. Most quests of most packs are plain: one or a few objectives, done once, rewarded with XP or items. When a plain quest does the job, write a plain quest.

### 3.4 Keep prerequisites rare
A prerequisite ("requires", part 4.5) locks a quest until others are done. Use prerequisites sparingly, or players feel forced and stop doing quests. This is the most important design rule.
- Simple vanilla tasks (craft a crafting table, a furnace, a bed, smelt iron, find a village...) get NO prerequisite. They are free quests, done in any order.
- Use prerequisite chains as a GUIDE for a mod that is complex or little known: each step teaches the next thing to build or do in that mod (first machine, how to power it, how to automate it...).
- Armor and gear tiers form a progression (leather, then iron, then diamond, then netherite, or the mod's equivalent) so players do not skip tiers.
- Big milestones (a boss, a new dimension) may require one or two key quests, not a whole tree.
- Most quests in the pack should have no prerequisite. Never chain unrelated quests just to draw lines.

### 3.5 Teach inside the quest book
A player must never have to leave the game and search a wiki to make a mod work. The quest book is the wiki.

The tool for this is the confirmation quest. A confirmation objective (event "manual", part 4.4) is completed when the player clicks it in the quest book. It asks for nothing in the world, so it is the right way to TELL the player something. The explanation goes in the quest description (a few clear sentences, as long as needed). The objective "text" is the label of the button, for example "Understood" or a line in character. A confirmation objective may also sit next to normal objectives in the same quest. Use confirmation quests generously:
- One welcome quest at the heart of each category: what this part of the game is about and what the player will be able to do after it. It is often the hub that the practical quests of a mod hang from.
- Before the first step of a complex mod: how the mod works (its power system, its multiblocks, the order to build things, the mistakes to avoid).
- Where two mods meet: how one feeds or unlocks the other.
- In a roleplay pack: story beats, characters speaking, the reason behind the next goal.

How to write a tutorial:
- Give the real steps, in order, with the exact blocks, items and amounts. For example, for Twilight Forest: dig a 2 by 2 pool of water one block deep, surround it with flowers or other plants, throw a diamond into the water, wait for the lightning, then step in. Do the same for any portal, ritual, multiblock, machine setup, power source, boss summon or hidden mechanic.
- Split a long tutorial into a short chain: one confirmation quest that explains the step, then a practical quest that checks it was done (the item crafted, the dimension entered, the boss killed), then the next explanation.
- Also say what the game does not tell: where to find the resource, which tool or tier is needed first, the usual mistake and how to avoid it.
- Only explain what you actually know about the mod. A wrong tutorial is worse than none: if you are not sure of a step, stay general and point to the mod's own in-game guide book.
- Adapt how much you guide to the tone. A beginner friendly or relaxed pack gets every step spelled out. An expert or hardcore pack gets short hints and leaves the discovery to the player. A roleplay pack gives the same steps in character. In every case, explain fully the mods listed as complex or little known, and keep vanilla and well known mods brief.

Links:
- To go further, a tutorial may end with a link, for example "More on the wiki: https://minecraft.wiki/w/Nether_portal". Write the full https address as plain text in the quest description: the game makes it clickable and asks the player before opening it. A link completes the explanation, it never replaces it.
- Choose the site of every link yourself, and only one that is clearly trustworthy: well known, widely used by Minecraft players, and the natural home of that information (the official site or wiki of the mod, its official download page, the Minecraft Wiki, a major community wiki, the official channel of the author). Never link to a small or little known site, a site with few users, a file hosting or link shortening site, or anything that could carry harmful content. Only give an address you are sure exists. When in doubt, give no link.

### 3.6 Write every text for the player
Titles, descriptions, objective texts, annotations and quest giver names only talk about the game: a mod, a mechanic, an item, a goal, the story.
- NEVER describe or explain the quest book itself: no word about the layout, the drawing a chapter forms, shapes, sizes, colors, positions ("the quests above", "the left room", "the ring", "the line going down") or why quests are arranged the way they are. The layout must speak for itself.
- Never mention this brief, your design choices, or that the pack was generated.

## 4. The file format and every feature
The server rejects anything that does not follow this part. Each feature says what it does, when it fits and how to write it. A "Sample" only shows the fields that the feature adds to a normal quest, objective or reward.

### 4.1 Rules for the whole file
- IDs are lowercase "namespace:path" matching ^[a-z0-9_.-]+:[a-z0-9_./-]+$. Use one namespace for the pack (a short slug of the modpack name), categories like "mypack:tech" and quests like "mypack:tech/first_machine". Every ID is unique.
- Local ids (objectives, tiers, annotations) match ^[a-z0-9_.-]+$ and are unique inside their parent.
- Only use item, block, entity, biome, structure, dimension, fluid, effect, enchantment, statistic and advancement IDs that really exist in Minecraft ${version} or in the listed mods. If you are not sure an ID exists, pick another objective. Never invent IDs. Where a tag is accepted, it is written with a leading # (for example "#minecraft:logs").
- A "text map" is { "en_us": "..." } plus one entry for each other requested language. Every text shown to the player is a text map, unless this part says otherwise.
- Unknown fields are rejected. Only write the fields named in this brief, and only write the fields of a feature when you use that feature.

### 4.2 Category
Fields: "id", "order" (0, 1, 2...), "icon" (an item ID), "title" and "description" (text maps), "annotations" and "linkRoutes" (part 5).
- Optional "group": "<name>". Categories with the same group are listed together under that name. Only useful in a big pack with many categories (for example a "Tech" group and a "Magic" group).

### 4.3 Quest
Fields of every quest:
- "schemaVersion": 2
- "id" and "category" (the ID of its category)
- "icon": an item ID
- "title" and "description": text maps. One short sentence for a simple task, more when the quest teaches something.
- "objectives": part 4.4
- "rewards": part 4.8. May be [].
- "progression": "once", unless part 4.6 gives a better mode
- "size", "presentation": { "shape" } and "layout": { "x", "y" }: part 5
Fields to add only when needed: "requires" (4.5), "objectiveMode" and "objectiveThreshold" (4.4), "tiers", "afterLast", "cooldownSeconds", "timeLimitSeconds", "noDeath" and "progressBeforeUnlock" (4.6), "hidden", "narrative", "kind" and "acknowledgement" (4.7).

### 4.4 Objectives
An objective is { "id", "event", "target", "count" }. "count" is an integer from 1 to 1000000000. A quest has 1 to 256 objectives.
Optional fields: "text" (a text map that replaces the label), "optional", "conditions" and "data", all explained below. Without "text" the game writes the label itself in the language of the player, from the event, the count and the name of the target. Only write "text" when you have something better to say.

Every event:
| Event | Target | The count is |
| --- | --- | --- |
| block_break | block ID or # tag | blocks broken |
| block_place | block ID or # tag | blocks placed |
| block_interact | block ID or # tag | right clicks on the block |
| observe_block | block ID or # tag | time spent looking at it, in ticks (20 per second) |
| item_obtained | item ID or # tag | items picked up, added up over time |
| item_craft | item ID or # tag | items crafted |
| item_smelt | item ID or # tag | items taken out of a furnace |
| item_use | item ID or # tag | right clicks with the item |
| item_consume | item ID or # tag | items eaten or drunk |
| fish_item | item ID or # tag | items fished |
| trade | item ID or # tag | items bought from a villager |
| inventory_count | item ID or # tag | items held right now. Nothing is taken and the count can go back down |
| item_submit | item ID or # tag | items shown when the player presses Submit. They are checked and kept |
| item_turn_in | item ID or # tag | items handed over and consumed. Deposits add up, and a Submission Station (4.12) can automate them |
| entity_kill | entity ID or # tag | kills |
| tame_animal | entity ID or # tag | animals tamed |
| breed_animal | entity ID or # tag | animals bred |
| fish_entity | entity ID or # tag | creatures hooked with a fishing rod |
| observe_entity | entity ID or # tag | time spent looking at it, in ticks |
| npc_interact | entity ID or # tag | right clicks on that kind of entity (for example "minecraft:villager"). It does not point to a quest giver |
| questplus:fish_any | "questplus:fish" | fish caught, whatever the species |
| biome_visit | biome ID | 1 |
| structure_visit | structure ID | 1 |
| dimension_enter | dimension ID | 1 |
| travel_distance | "questplus:travel" | distance in centimeters: 100 is one block, 100000 is one kilometer |
| location | "questplus:position" | 1. Needs a "position" condition, see below |
| xp_levels | "questplus:levels" | XP level the player has right now |
| xp_points | "questplus:points" | XP points the player has right now |
| xp_submit_levels | "questplus:levels" | XP levels paid by the player |
| xp_submit_points | "questplus:points" | XP points paid by the player |
| equipment_check | "questplus:gear" | 1. Needs a "questplus:gear" condition, see below |
| advancement_complete | advancement ID | 1 |
| custom_stat | Minecraft custom statistic ID | value of the statistic to reach |
| stage | stage ID (4.10) | 1 |
| player_death | "questplus:death" | deaths |
| manual | the id of its own quest | 1. This is the confirmation objective of part 3.5 |
| fluid_submit | fluid ID | millibuckets paid into a Submission Station (1000 is one bucket) |
| energy_submit | "questplus:fe" | FE paid into a Submission Station |
| fluid_transfer, energy_transfer | | Never use them, they need a dedicated integration mod |

Notes on the events:
- Confirmation objective, written in full: { "id": "read", "event": "manual", "target": "<the id of this quest>", "count": 1, "text": { "en_us": "Understood" } }
- Pick the item event that matches the intent: item_craft or item_obtained for a first step, inventory_count for a stock to own, item_turn_in for a delivery or a cost, equipment_check for gear that must really be worn.
- Several accepted items for one objective: "data": { "targets": [...] } lists every accepted item ID or # tag (32 at most, the main target included). It works on the item events. Sample: ${j(S.objectives.alternatives)}
- location: the player stands inside a box. All six bounds are required. You do not know the map, so only use it for a height or a depth, with very wide x and z bounds. Sample: ${j(S.objectives.depth)}
- equipment_check: the player wears or holds the listed gear. Slots are head, chest, legs, feet, mainhand, offhand and inventory. "enchantments" is optional. Sample: ${j(S.objectives.gear)}
- advancement_complete: the advancements of a mod are a reliable way to detect its milestones, when you are sure of the ID. Vanilla ones look like "minecraft:story/mine_stone".
- custom_stat: statistics such as "minecraft:jump", "minecraft:mob_kills" or "minecraft:sleep_in_bed".
- player_death: only for a joke or a hardcore pack.
- fluid_submit and energy_submit: only for a tech pack whose mods move fluids or FE with buckets, pipes or cables.

Combining the objectives of a quest:
- "objectiveMode": "all" (default, every objective), "any" (one of them is enough, for example any of three ways to get an item), "sequence" (objectives unlock one after the other, a small guided tutorial inside a single quest) or "at_least" with "objectiveThreshold" (any N of the list, for example visit 3 of these 5 biomes). Sample: ${f(S.quest.quorum)}
- "optional": true on an objective makes it a bonus step that does not block completion. A quest keeps at least one objective that is not optional.

Conditions on an objective:
- "conditions" makes an objective count only in a given situation. A condition is an object with ONE key: "dimension", "biome", "tool" (item ID held in the main hand), "effect" (an effect ID the player has), "sneaking" (true or false), "healthAtMost" or "healthAtLeast" (health points, 20 is a full bar), "equipment" ({ "slot", "item" }), "questplus:gear", "questplus:stage" (a stage ID), "position", or "and", "or", "not" to combine them. Sample: ${j(S.objectives.conditions)}
- Fits challenges and themed goals (kill the boss with a given weapon, mine in a given dimension, fight at low health). A normal quest needs no condition.
- Always state the condition in the description or in the objective "text". The player cannot guess it.

### 4.5 Prerequisites
"requires" locks a quest until the condition is true. Part 3.4 says when to use it. Forms:
- one quest completed: { "quest": "<quest id>" }
- all of several: { "and": [ { "quest": "..." }, { "quest": "..." } ] }
- one of several: ${j(S.requires.either)}
- any N of a list: ${j(S.requires.quorum)}
- a quest that is only started, not finished: ${j(S.requires.started)}
- a quest that is NOT completed, to close a path once the other one was taken: ${j(S.requires.exclusive)}
Forms can be nested. Prefer quests of the same category. No cycles. Every quest named must exist.

### 4.6 Repeating, tiers and challenges
"progression" says how often a quest can be done:
- "once" (the default): done one time. Right for almost every quest.
- "repeatable": once it is completed and "cooldownSeconds" (0 to 31536000) has passed, the player can start it again from the quest book. It pays its rewards every time. Fits daily or weekly contracts, bounties, deliveries to a guild, farming goals, anything that must stay useful in a world that lasts. Does not fit a tutorial step or a milestone. Sample: ${f(S.quest.repeatable)}
- "infinite": a new cycle starts by itself after each completion, with no click. Fits an endless counter that pays a little every N actions. Use it rarely and with small rewards. Sample: ${f(S.quest.infinite)}
- "tiered": one quest with several milestones, each with its own rewards. Fits long goals and collections (stone mined, distance travelled, mobs killed, items crafted) instead of five copies of the same quest. A tier has an "id", a "threshold" and "rewards". The threshold multiplies the count of every objective: with a count of 100 and thresholds 1, 10 and 100, the tiers are reached at 100, 1000 and 10000. Thresholds strictly increase, count times threshold stays under 1000000000, and the quest level "rewards" is usually []. "afterLast" is "stop" (default) or "repeat". Sample: ${f(S.quest.tiered)}

Challenges, on any quest:
- "timeLimitSeconds" (1 to 31536000): the attempt must be finished in that time or it starts over.
- "noDeath": true resets the unfinished attempt when the player dies.
- Both fit boss fights, speed runs and hardcore challenges. They do not fit a relaxed pack or a learning step. Sample: ${f(S.quest.challenge)}

Counting early:
- "progressBeforeUnlock": true lets the objectives count while the quest is still locked by its prerequisites. Fits counters, so players do not redo what they already did.

### 4.7 Visibility and story
- "hidden": true keeps a quest out of sight until its prerequisites are done. Fits secrets, surprises, story twists and long trees that would scare a new player. It needs a prerequisite: a hidden quest without one is visible at once.
- "narrative": a text map shown in the quest details next to the description. In a story pack, put the voice of the narrator or of a character there and keep the description for the practical steps.
- An information quest ("kind": "information") is a page with no objective. With "acknowledgement": "read_only" it is never completed, gives no reward and cannot be a prerequisite: a pure reference page (server rules, a table of ore heights, a piece of lore). With "acknowledgement": "manual" the player confirms the reading and it may give rewards. It is always "once", with no tier, timer or "objectiveMode". For teaching, the confirmation quest of part 3.5 stays the normal tool. Sample: ${f(S.quest.information)}
- "presentation" accepts more than "shape". Sample: ${f(S.quest.presentation)}
  - "subtitle": a text map shown under the title
  - "optional": true marks a side quest
  - "tags": a list of short words
  - "iconScale": 0.25 to 2
  - "disableCompletionToast": true shows no popup when the quest is completed
  - "hideDetailsUntilUnlocked": true keeps objectives and rewards secret while the quest is locked
  - "hideTextUntilCompleted": true makes a mystery quest
  - "invisibleUntilCompleted": true makes a secret achievement that only shows once earned
  - "invisibleUntilTasks": N shows the quest after N of its objectives are done
  - "hideDependencyLines" and "hideDependentLines": true do not draw the links coming in or going out
  - "hideUntilDependenciesVisible": true hides the quest while its prerequisites are hidden

### 4.8 Rewards
"rewards" is a list of 0 to 64 rewards. Follow the rewards style of part 1. Most rewards are plain XP and items, and a quest may have none, which is normal for many confirmation quests.

Direct rewards:
- XP points: { "type": "experience", "amount": N } (1 to 1000000)
- XP levels: { "type": "experience_levels", "amount": N } (1 to 1000000)
- Items: { "type": "item", "item": "<item id>", "count": N } (1 to 2304). Optional "randomBonus": N adds 0 to N more. Optional "onlyOne": true is for a unique item the player must not own twice.
- A stage (4.10): ${j(S.rewards.stage)}
- A popup with a title, a description and an item icon, good to announce that something new opened: ${j(S.rewards.toast)}
- A chat line. The message is one string of 256 characters at most, not a text map: ${j(S.rewards.message)}
- A Minecraft or mod advancement: ${j(S.rewards.advancement)}
- Never a "questplus:command" reward. The server rejects it.

Reward pools hold several options. An option is an item, XP, levels, a stage, a toast, a message or an advancement. 32 options at most, and no pool inside a pool.
- "questplus:choice": the player picks ONE option. Fits a class or path choice (sword or bow), or letting each player take what is useful. Options have no weight. Sample: ${j(S.rewards.choice)}
- "questplus:random": "rolls" draws (0 to 16) among options with a "weight". A weight of 0 means always given. Fits repeatable quests and anything that should feel like loot. Sample: ${j(S.rewards.random)}
- "questplus:loot": the same, with "emptyWeight" as the weight of getting nothing. Sample: ${j(S.rewards.loot)}
- "questplus:all": gives every option (each still needs a "weight", use 1). Mostly useful inside a named table (4.9).

Options that any reward accepts. Sample: ${j(S.rewards.options)}
- "text": your own label, a text map
- "autoClaim": "enabled" gives the reward at completion with no click, "no_toast" does it silently, "invisible" also hides it from the reward list, which suits a technical stage
- "teamReward": true shares it with the team of the player, for multiplayer packs
- "excludeFromClaimAll": true means it must be claimed on its own

### 4.9 Reward tables and loot crates
- A named table is a pool written once in the top level "rewardTables" array and used by many quests with ${j(S.rewards.table)}. Fits a pack where many quests share the same loot (every contract of a guild, every dungeon quest). For a single quest, write the pool directly in its rewards.
- A table with "crate": true is also a loot crate: an item the player opens to draw the table once, with a chest animation and a rarity label. Fits loot driven, RPG, dungeon and long running server packs. It adds nothing to a small guide pack. A crate table cannot be a "questplus:choice".
- Sample table: ${j(S.rewardTables)}
- "name" (80 characters at most, unique) is what quests and crates refer to. "reward" is one pool. "crateName", "crateColor" (#rrggbb) and "crateGlow" set the look of the crate item.
- Give a crate as a reward with exactly this shape, where "questplus_table" is the table name: ${j(S.rewards.crate)}
- "crateDrops" makes mobs drop the crate, with one weight for passive mobs, monsters and bosses. It is compared with a "no crate" weight of 4000 for passive mobs, 600 for monsters and 0 for bosses. Mob drops only work once I enable loot crate drops in the Studio settings, so tell me after the file when you rely on them.

### 4.10 Stages
A stage is a named flag on a player, with an ID like "mypack:nether_ready".
- A "questplus:stage" reward (4.8) gives it, or removes it with "remove": true.
- An objective with event "stage" checks it, and the condition "questplus:stage" makes any objective count only for players who have it (4.4).
- Fits a choice that must be remembered (a faction or a class picked through a "questplus:choice" reward, then quests reserved to each side) or a state shared by distant categories.
- Most packs do not need stages: "requires" is simpler and draws a visible link.

### 4.11 Quest givers (NPCs)
A quest giver is a character standing in the world: a villager in a biome outfit, or a player shaped character. The player right clicks it, reads the request with its objectives and rewards, then accepts or declines. A quest offered by a quest giver stays out of the quest book and counts nothing until the player accepts it, and its rewards are claimed by going back to the character. Quest givers never despawn and cannot be hurt.
- Fits: roleplay and adventure packs, exploration (a character met in a village, a ruin or a structure), contracts and bounties (it goes well with "repeatable"), side stories told by someone. A quest giver has a name and a personality, so write its quests in its own voice. Its quests can be chained with "requires" so the character gives them one after the other.
- Does not fit: the main progression, or anything the player must find to move forward, because a quest giver appears by chance and only in newly generated terrain. Keep the core path in the quest book and give side content to quest givers. Avoid them in a skyblock or void world, where structures do not generate.
- A few memorable characters (one to five) are better than a crowd. Many packs need none.
Write them in the top level "npcs" array. Sample: ${j(S.npcs)}
- "id": lowercase letters, digits, _ . - (64 characters at most), unique.
- "name": a text map, 64 characters at most.
- "model": "villager" with "villager" set to plains, desert, savanna, snow, taiga, jungle or swamp, or "player". Never write a "skin".
- "quests": up to 64 IDs of quests that exist in "quests". Each one is a normal quest with a category and a layout, and it shows in the quest book once accepted. Give these quests their own category or their own corner of a category.
- "spawns": up to 64 rules, each with a "chance" from 0 to 1.
  - A "structure" rule rolls once when that structure generates. Its target is a structure ID or a # tag, for example "#minecraft:village", "minecraft:village_taiga", "minecraft:desert_pyramid", "minecraft:trail_ruins", "#minecraft:mineshaft", "#minecraft:shipwreck", or a structure of a listed mod when you are sure of its ID. An exact structure ID wins over a tag.
  - A "random" rule rolls once for each new chunk of a dimension (target "minecraft:overworld" for example), so keep its chance tiny (0.0005).

### 4.12 Around the quests
Nothing here is written in the file. It is useful for ideas and for texts.
- Players read quests in a quest book screen or on a craftable tablet (item "questplus:tablet"). On a server they can form teams that share progress.
- The Quest Board ("questplus:quest_board") shows quests and leaderboards on a wall.
- The Submission Station ("questplus:submission_station") receives item, fluid and energy turn ins from hoppers, pipes and cables, so a turn in quest can become an automation goal in a tech pack.
- The Quest Relay ("questplus:quest_relay") turns the state of a quest into a redstone signal.
- When one of these really serves the pack, a quest may teach how to use it. Otherwise do not mention them.

## 5. The layout
Each category is a picture that tells its story. A player should understand a category before reading a word: where to start, what matters most, what is optional, where it ends. Do NOT line quests up in rows and columns by default. A grid is only right for a true collection of equal, unordered things (every crop, every ore), and even then keep it a small compact block or a ring inside a larger composition, or use it when I asked for a grid.

### 5.1 How things are drawn
- "layout" x and y are the CENTER of the quest node. "size" is its width in the same units, an integer, and 45 is the normal size.
- "presentation": { "shape" } is one of square, circle, rounded_square, diamond, pentagon, hexagon, octagon, heart, gear.
- Every prerequisite draws a link. A link leaves the bottom of the prerequisite and enters the top of the dependent quest. Without a route it is a right-angle elbow (down, across, down), and a clean straight line when both quests share the same x.

### 5.2 Composition
- Choose ONE composition per category that fits its content, and vary it from one category to the next: a hub with spokes or rings (a core item in the middle, its uses around it), a spine with branches (tiers going down, side quests leaving each tier), a symmetric tree, separate islands for independent topics, a winding path for a journey or a story, two mirrored wings for two parallel choices or anything else that is not a grid. A category may have several compositions, but each composition must be complete and self-contained.
- Position tells the story. The start is at the top or the center, the final goal at the bottom or the outer edge. Free quests sit next to the step they relate to. Optional content stays on the sides.
- Size shows importance. Hubs and milestones are 60 to 90, normal steps 45, small free or optional quests 30 to 36. Sizes may grow along a tier chain so the last tier is the biggest.
- Shape shows meaning. Pick a small legend and keep it for the whole pack, for example circle for free tasks, rounded_square for guided steps, hexagon for machines, octagon for hubs and confirmation quests, diamond for milestones and gates, gear for automation, pentagon for exploration, heart for optional fun. Use 2 to 4 shapes per category.
- Keep it balanced and even. Mirror the layout around a vertical axis or a center. Siblings share the same distance to their parent and the same gap between them. On a ring, use equal angle steps: x = cx + r * cos(angle), y = cy + r * sin(angle), rounded to integers. Clusters have similar weight on the left and on the right, with equal gaps between them.
- Keep at least 30 between the edges of two nodes (center distance of at least half of both sizes plus 30), about 80 to 110 between the centers of linked quests, and 150 or more between clusters. Nothing overlaps. Links never cross a node they do not belong to and should not cross each other.

### 5.3 Link routes
A category may give waypoints to its links: "linkRoutes": [ { "from": "<prerequisite quest id>", "to": "<dependent quest id>", "points": [ { "x": N, "y": N }, ... ] } ]
- With a route, the link runs straight from waypoint to waypoint. Quest nodes are drawn over links, so a waypoint at a quest center is hidden: two waypoints, the prerequisite center then the dependent center, draw one straight line in any direction, even sideways or upward.
- Add waypoints in between to bend a link around a node or to merge several links into one shared trunk.
- Each entry must match a real "requires" link between two quests of this category. One entry per link, at most 128 entries per category and 64 points per link.

### 5.4 Annotations
Annotations are visual only. An annotation is { "id", "style", "x", "y", "width", "height", "color", "text" }. x and y are its center, "width" and "height" are integers, "color" is #rrggbb and "text" is a text map. Optional "align": "left", "center" or "right". Styles:
- "label" is plain text with no frame, and "heading" is the same in bold. Use them for section titles and short captions next to a cluster. To color or resize the text, add "rich" as in the example of part 6.
- "note" is a framed box that catches the eye. Keep it for the few things the player must not miss (a warning, a gate condition, a key tip). Zero to two per category. Never use it as a title or as decoration, and put long explanations in a confirmation quest instead.
- "background" is a colored rectangle drawn behind quests. It is optional and takes no "rich". Use it as an accent, behind a hub or to tint one zone that must stand apart, not as a box around every group. Use muted, fairly dark colors and a lighter matching color for the labels.
- At most 128 annotations per category. Annotations never cover a quest or a link, except a background.

## 6. Example
The example shows the format and a layout. It is small and plain on purpose: make your pack much bigger and specific to my modpack, and take the features it does not show from part 4.
${example}

## 7. Final check
Before answering, check that:
- Content: every quest comes from a mod in my list or from vanilla. Each category has confirmation quests that explain its mods and mechanics. Every feature of part 4 that you used is there because this pack needs it, not because it exists.
- Prerequisites: most quests have none. Every "requires" points to an existing quest, with no cycle.
- IDs and fields: every ID is valid and real, every text has en_us, and no field is unknown.
- Features: tier thresholds strictly increase, every quest listed by a quest giver exists, and every table name used by a reward or a crate exists in "rewardTables".
- Texts: no text describes the layout or the shape of a chapter. Every link is an https address on a well known, trustworthy site.
- Layout: no category is a plain grid unless I asked for one, sizes and shapes follow one legend, nodes do not overlap, every "linkRoutes" entry matches a real link of its category, and framed notes are rare.`;
}
