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

export const AI_PROMPT_FIELDS = ["theme", "mods", "goals", "guided", "size", "languages", "rewards", "tone"];

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
- Tone and guidance level: ${v("tone", "not specified, deduce it as explained below")}

## Output
Return ONE strict JSON file named questplus-pack.json (no comments, no trailing commas). If you can create files, give it as a download, otherwise put the whole file in a single \`\`\`json code block and nothing else in it. I will open it in QuestPlus Studio with File > Open. Shape:
{ "targetMinecraftVersion": "${version}", "categories": [...], "quests": [...] }

## First, study the mod list
Do this before writing a single quest. The mod list above is your source of truth.
- For each mod, work out what it really adds: its entry item or block, its core machines or mechanics, its tiers, its endgame and its guide book if it has one.
- Work out how the mods relate to each other: which mod produces what another one consumes (power, ores, fluids, mob drops), which one gates another, which ones do the same job.
- Skip libraries, APIs, performance mods and client-only mods. They give no quests.
- From this, decide the categories and which quests are actually possible. Only write a quest when you know the item, block, entity or dimension it needs. For a mod you only half know, write fewer and safer quests, and explain the mod with confirmation quests (see below) instead of guessing IDs.
- Settle the tone of the pack. If I gave one, follow it. If not, deduce it from the modpack name and theme, the kind of mods (tech, magic, exploration, survival, hardcore), the size I asked for and the rewards style, then keep it for the whole pack. The tone decides the voice of the texts, how much you guide, how generous the rewards are and how dense a category is.
- If the tone is roleplay or story driven, write every title and description in character, with a narrator voice and a story that moves forward from category to category. Otherwise write like a friendly guide.

## Prerequisites: the most important design rule
Prerequisites (\`requires\`) lock a quest until others are done. Use them sparingly, or players feel forced and stop doing quests.
- Simple vanilla tasks (craft a crafting table, a furnace, a bed, smelt iron, find a village...) get NO prerequisite. They are free quests, done in any order.
- Use prerequisite chains as a GUIDE for a mod that is complex or little known: each step teaches the next thing to build or do in that mod (first machine, how to power it, how to automate it...).
- Armor and gear tiers form a progression (leather, then iron, then diamond, then netherite, or the mod's equivalent) so players do not skip tiers.
- Big milestones (a boss, a new dimension) may require one or two key quests, not a whole tree.
- Most quests in the pack should have no prerequisite. Never chain unrelated quests just to draw lines.

## Teach with confirmation quests
A confirmation objective (event "manual") is completed when the player clicks it in the quest book. It asks for nothing in the world, so it is the right tool to TELL the player something. Use it generously:
- One welcome quest at the heart of each category: what this part of the game is about and what the player will be able to do after it.
- Before the first step of a complex mod: how the mod works (its power system, its multiblocks, the order to build things, the mistakes to avoid).
- Where two mods meet: how one feeds or unlocks the other.
- In a roleplay pack: story beats, characters speaking, the reason behind the next goal.
The explanation goes in the quest description (a few clear sentences, as long as needed). The objective "text" is the label of the button, for example "Understood" or a line in character. A confirmation objective may also sit next to normal objectives in the same quest. A confirmation quest is often the hub that the practical quests of a mod hang from.

## The quest book is the wiki
A player must never have to leave the game and search a wiki to make a mod work. Whenever a mod needs knowledge to get going, write it as a small tutorial inside the quests:
- Give the real steps, in order, with the exact blocks, items and amounts. For example, for Twilight Forest: dig a 2 by 2 pool of water one block deep, surround it with flowers or other plants, throw a diamond into the water, wait for the lightning, then step in. Do the same for any portal, ritual, multiblock, machine setup, power source, boss summon or hidden mechanic.
- Split a long tutorial into a short chain: one confirmation quest that explains the step, then a practical quest that checks it was done (the item crafted, the dimension entered, the boss killed), then the next explanation.
- Also say what the game does not tell: where to find the resource, which tool or tier is needed first, the usual mistake and how to avoid it.
- Only explain what you actually know about the mod. A wrong tutorial is worse than none: if you are not sure of a step, stay general and point to the mod's own in-game guide book.
- Adapt how much you guide to the tone of the pack. A beginner friendly or relaxed pack gets every step spelled out. An expert or hardcore pack gets short hints and leaves the discovery to the player. A roleplay pack gives the same steps in character. In every case, explain fully the mods listed as complex or little known, and keep vanilla and well known mods brief.
- To go further, a tutorial may end with a link, for example "More on the wiki: https://minecraft.wiki/w/Nether_portal". Write the full https address as plain text in the quest description: the game makes it clickable and asks the player before opening it. A link completes the explanation, it never replaces it.
- Choose the site of every link yourself, and only one that is clearly trustworthy: well known, widely used by Minecraft players, and the natural home of that information (the official site or wiki of the mod, its official download page, the Minecraft Wiki, a major community wiki, the official channel of the author). Never link to a small or little known site, a site with few users, a file hosting or link shortening site, or anything that could carry harmful content. Only give an address you are sure exists. When in doubt, give no link.

## Every text is for the player, in a finished pack
This pack ships as is. Titles, descriptions, objective texts and annotations only talk about the game: a mod, a mechanic, an item, a goal, the story.
- NEVER describe or explain the quest book itself: no word about the layout, the drawing a chapter forms, shapes, sizes, colors, positions ("the quests above", "the left room", "the ring", "the line going down") or why quests are arranged the way they are. The layout must speak for itself.
- Never mention this prompt, your design choices, or that the pack was generated.

## Format rules (the server rejects anything else)
- IDs are lowercase \`namespace:path\` matching ^[a-z0-9_.-]+:[a-z0-9_./-]+$. Use one namespace for the pack (a short slug of the modpack name), categories like "mypack:tech" and quests like "mypack:tech/first_machine". Every ID is unique.
- Only use item, block, entity, biome, structure, dimension and advancement IDs that really exist in Minecraft ${version} or in the listed mods. If you are not sure an ID exists, pick another objective. Never invent IDs. Item tags are allowed as targets with a leading # (for example "#minecraft:logs").
- Every title, description and annotation text is a map with "en_us" plus the other requested languages.
- Category: id, order (0, 1, 2...), icon (an item ID), title, description, annotations, and linkRoutes when links need waypoints.
- Quest: schemaVersion 2, id, category, icon (an item ID), size (integer, 45 is the normal size), presentation { "shape" }, title, description (one short sentence for a simple task, more when the quest teaches something), objectives, rewards, progression "once", layout {x, y}, and requires only when the rule above says so.
- Shapes: square, circle, rounded_square, diamond, pentagon, hexagon, octagon, heart, gear.
- Objective: { "id": lowercase local id unique in the quest, "event", "target", "count": positive integer }. Events: item_obtained, item_craft, item_smelt, item_consume, item_turn_in (takes the items), inventory_count, block_break, block_place, entity_kill, tame_animal, breed_animal, biome_visit, structure_visit, dimension_enter, advancement_complete, manual. A quest with several objectives needs all of them.
- Confirmation objective: { "id": "read", "event": "manual", "target": "<the id of this quest>", "count": 1, "text": { "en_us": "Understood" } }.
- Rewards: { "type": "experience", "amount": N } or { "type": "item", "item": "<item id>", "count": N } (count 1 to 2304). No command rewards.
- requires: { "quest": "<quest id>" }, or { "and": [ ... ] } / { "or": [ ... ] } of those. Prefer quests of the same category. No cycles.
- linkRoutes: [ { "from": "<prerequisite quest id>", "to": "<dependent quest id>", "points": [ { "x": N, "y": N }, ... ] } ]. Each entry must match a real requires link between two quests of this category. One entry per link, at most 128 entries per category and 64 points per link.

## Layout: each category is a picture that tells its story
A player should understand a category before reading a word: where to start, what matters most, what is optional, where it ends. Do NOT line quests up in rows and columns by default. A grid is only right for a true collection of equal, unordered things (every crop, every ore), and even then keep it a small compact block or a ring inside a larger composition, or use it when I asked for a grid.

How things are drawn:
- layout x/y is the CENTER of the quest node. size is its width in the same units. Annotation x/y is also the center.
- A link leaves the bottom of the prerequisite and enters the top of the dependent quest. Without a route it is a right-angle elbow (down, across, down), and a clean straight line when both quests share the same x.
- A link can have several points. With a linkRoutes entry, the link runs straight from waypoint to waypoint. Quest nodes are drawn over links, so a waypoint at a quest center is hidden: two waypoints, the prerequisite center then the dependent center, draw one straight line in any direction, even sideways or upward. Add waypoints in between to bend a link around a node or to merge several links into one shared trunk.

Composition:
- Choose ONE composition per category that fits its content, and vary it from one category to the next: a hub with spokes or rings (a core item in the middle, its uses around it), a spine with branches (tiers going down, side quests leaving each tier), a symmetric tree, separate islands for independent topics, a winding path for a journey or a story, two mirrored wings for two parallel choices or anything else that is not a grid. A category may have several compositions, but each composition must be complete and self-contained.
- Position tells the story. The start is at the top or the center, the final goal at the bottom or the outer edge. Free quests sit next to the step they relate to. Optional content stays on the sides.
- Size shows importance. Hubs and milestones are 60 to 90, normal steps 45, small free or optional quests 30 to 36. Sizes may grow along a tier chain so the last tier is the biggest.
- Shape shows meaning. Pick a small legend and keep it for the whole pack, for example circle for free tasks, rounded_square for guided steps, hexagon for machines, octagon for hubs and confirmation quests, diamond for milestones and gates, gear for automation, pentagon for exploration, heart for optional fun. Use 2 to 4 shapes per category.
- Keep it balanced and even. Mirror the layout around a vertical axis or a center. Siblings share the same distance to their parent and the same gap between them. On a ring, use equal angle steps: x = cx + r * cos(angle), y = cy + r * sin(angle), rounded to integers. Clusters have similar weight on the left and on the right, with equal gaps between them.
- Keep at least 30 between the edges of two nodes (center distance of at least half of both sizes plus 30), about 80 to 110 between the centers of linked quests, and 150 or more between clusters. Nothing overlaps. Links never cross a node they do not belong to and should not cross each other.

Annotations (visual only):
- "label" is plain text with no frame. Use it for section titles and short captions next to a cluster.
- "note" is a framed box that catches the eye. Keep it for the few things the player must not miss (a warning, a gate condition, a key tip). Zero to two per category. Never use it as a title or as decoration, and put long explanations in a confirmation quest instead.
- "background" is a colored rectangle drawn behind quests. It is optional. Use it as an accent, behind a hub or to tint one zone that must stand apart, not as a box around every group. Use muted, fairly dark colors (#rrggbb) and a lighter matching color for the labels.
- Annotation ids match ^[a-z0-9_.-]+$ and are unique in their category. At most 128 annotations per category. Annotations never cover a quest or a link, except a background.

## Example (format only, make yours much bigger and specific to my modpack)
${example}

Before answering, check that: every quest comes from a mod in my list or from vanilla, every ID is valid and real, every requires points to an existing quest with no cycle, most quests have no prerequisite, each category has confirmation quests that explain its mods and mechanics, no text describes the layout or the shape of a chapter, no category is a plain grid unless I asked for one, sizes and shapes follow one legend, nodes do not overlap, every linkRoutes entry matches a real link of its category, framed notes are rare, every link is an https address on a well known, trustworthy site, and every text has en_us.`;
}
