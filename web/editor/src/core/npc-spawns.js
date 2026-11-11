// Vanilla places a quest giver can spawn, as shown in Quest Studio. Each entry is one
// config.json spawn rule target; tags (#) group the variants of a structure.

export const VILLAGES = [
	{ target: "minecraft:village_plains", name: "Plains village", icon: "minecraft:hay_block" },
	{ target: "minecraft:village_desert", name: "Desert village", icon: "minecraft:sandstone" },
	{ target: "minecraft:village_savanna", name: "Savanna village", icon: "minecraft:acacia_log" },
	{ target: "minecraft:village_snowy", name: "Snowy village", icon: "minecraft:snow_block" },
	{ target: "minecraft:village_taiga", name: "Taiga village", icon: "minecraft:spruce_log" },
];
/** Fallback for every village without its own rule: an exact structure beats a tag on the server. */
export const ANY_VILLAGE = { target: "#minecraft:village", name: "Any village", icon: "minecraft:bell" };

export const STRUCTURE_GROUPS = [
	{
		name: "Overworld",
		items: [
			{ target: "#minecraft:abandoned_camp", name: "Abandoned camp", icon: "minecraft:campfire" },
			{ target: "minecraft:desert_pyramid", name: "Desert temple", icon: "minecraft:chiseled_sandstone" },
			{ target: "minecraft:jungle_pyramid", name: "Jungle temple", icon: "minecraft:mossy_cobblestone" },
			{ target: "minecraft:igloo", name: "Igloo", icon: "minecraft:packed_ice" },
			{ target: "minecraft:pillager_outpost", name: "Pillager outpost", icon: "minecraft:crossbow" },
			{ target: "minecraft:mansion", name: "Woodland mansion", icon: "minecraft:dark_oak_log" },
			{ target: "minecraft:swamp_hut", name: "Swamp hut", icon: "minecraft:cauldron" },
			{ target: "minecraft:trail_ruins", name: "Trail ruins", icon: "minecraft:brush" },
			{ target: "#minecraft:ruined_portal", name: "Ruined portal", icon: "minecraft:crying_obsidian" },
		],
	},
	{
		name: "Underground",
		items: [
			{ target: "#minecraft:mineshaft", name: "Mineshaft", icon: "minecraft:rail" },
			{ target: "minecraft:stronghold", name: "Stronghold", icon: "minecraft:end_portal_frame" },
			{ target: "minecraft:trial_chambers", name: "Trial chambers", icon: "minecraft:trial_key" },
			{ target: "minecraft:ancient_city", name: "Ancient city", icon: "minecraft:sculk_shrieker" },
		],
	},
	{
		name: "Ocean",
		items: [
			{ target: "#minecraft:shipwreck", name: "Shipwreck", icon: "minecraft:oak_boat" },
			{ target: "#minecraft:ocean_ruin", name: "Ocean ruins", icon: "minecraft:suspicious_gravel" },
			{ target: "minecraft:monument", name: "Ocean monument", icon: "minecraft:prismarine_bricks" },
			{ target: "minecraft:buried_treasure", name: "Buried treasure", icon: "minecraft:heart_of_the_sea" },
		],
	},
	{
		name: "Nether & End",
		items: [
			{ target: "minecraft:fortress", name: "Nether fortress", icon: "minecraft:nether_bricks" },
			{ target: "minecraft:bastion_remnant", name: "Bastion remnant", icon: "minecraft:gilded_blackstone" },
			{ target: "minecraft:nether_fossil", name: "Nether fossil", icon: "minecraft:bone_block" },
			{ target: "minecraft:end_city", name: "End city", icon: "minecraft:purpur_block" },
		],
	},
];

export const DIMENSION_SPAWNS = [
	{ target: "minecraft:overworld", name: "Overworld", icon: "minecraft:grass_block" },
	{ target: "minecraft:the_nether", name: "Nether", icon: "minecraft:netherrack" },
	{ target: "minecraft:the_end", name: "The End", icon: "minecraft:end_stone" },
];

/** Vanilla villager outfits, by the biome that dresses them (config.json "villager"). */
export const VILLAGER_TYPES = [
	{ id: "plains", name: "Plains", icon: "minecraft:hay_block" },
	{ id: "desert", name: "Desert", icon: "minecraft:sand" },
	{ id: "savanna", name: "Savanna", icon: "minecraft:acacia_log" },
	{ id: "snow", name: "Snow", icon: "minecraft:snow_block" },
	{ id: "taiga", name: "Taiga", icon: "minecraft:spruce_log" },
	{ id: "jungle", name: "Jungle", icon: "minecraft:jungle_log" },
	{ id: "swamp", name: "Swamp", icon: "minecraft:lily_pad" },
];

const SKIN_PREFIX = "data:image/png;base64,";
export const MAX_SKIN_BYTES = 16384;

/** Mirrors the server: a 64×64 PNG of at most 16 KB, read from its IHDR header. */
export function skinProblem(skin) {
	if (typeof skin !== "string" || !skin.startsWith(SKIN_PREFIX)) return "The skin must be a PNG image";
	const data = skin.slice(SKIN_PREFIX.length);
	if (!/^[A-Za-z0-9+/]+={0,2}$/.test(data) || (data.length * 3) / 4 > MAX_SKIN_BYTES + 2) return "The skin must be a PNG of at most 16 KB";
	const head = atob(data.slice(0, 32)),
		int = (at) => ((head.charCodeAt(at) << 24) | (head.charCodeAt(at + 1) << 16) | (head.charCodeAt(at + 2) << 8) | head.charCodeAt(at + 3)) >>> 0;
	if (head.slice(1, 4) !== "PNG" || head.slice(12, 16) !== "IHDR") return "The skin must be a PNG image";
	if (int(16) !== 64 || int(20) !== 64) return "The skin must be 64 × 64 pixels, like a Minecraft skin";
	return "";
}

const STRUCTURE_PLACES =[...VILLAGES, ANY_VILLAGE, ...STRUCTURE_GROUPS.flatMap((group) => group.items)];
const NAMES = new Map([...STRUCTURE_PLACES, ...DIMENSION_SPAWNS].map((place) => [place.target, place.name]));

/** Default chance for a place the author just switched on. */
export const DEFAULT_CHANCE = { structure: 0.25, random: 0.002 };

export const ruleFor = (npc, type, target) => (npc.spawns || []).find((rule) => rule.type === type && rule.target === target);

/** Switches a place on (with a chance from 0 to 1) or off (chance null), keeping rule order. */
export function setRule(npc, type, target, chance) {
	const rules = npc.spawns || [],
		index = rules.findIndex((rule) => rule.type === type && rule.target === target);
	if (chance === null) {
		if (index >= 0) rules.splice(index, 1);
	} else if (index >= 0) rules[index].chance = chance;
	else rules.push({ type, target, chance });
	npc.spawns = rules;
}

/** Rules Quest Studio has no tile for: modded structures and custom tags. */
export const customRules = (npc) => (npc.spawns || []).filter((rule) => rule.type === "structure" && !NAMES.has(rule.target));

export const placeName = (target) => NAMES.get(target) || target;

/** Percent shown to authors, kept to three decimals so 0.2% and 0.002% stay exact. */
export const percent = (chance) => Math.round(chance * 100000) / 1000;
export const fromPercent = (value) => Math.min(1, Math.max(0, (Number(value) || 0) / 100));
/** The percent in the interface language's notation: 0,5 in French, 0.5 in English. */
export const formatPercent = (chance, locale) => new Intl.NumberFormat(locale, { maximumFractionDigits: 3, useGrouping: false }).format(percent(chance));

/** A stable NPC identifier from its display name, unique in the project. */
export function npcId(name, taken) {
	const base =
		String(name)
			.normalize("NFD")
			.replace(/[̀-ͯ]/g, "")
			.toLowerCase()
			.replace(/[^a-z0-9]+/g, "_")
			.replace(/^_+|_+$/g, "")
			.slice(0, 56) || "npc";
	let id = base,
		n = 2;
	while (taken.has(id)) id = base + "_" + n++;
	return id;
}
