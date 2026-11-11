import { hubEditorDescriptors } from "./hub-registry.js";
const pools = ["questplus:choice", "questplus:random", "questplus:loot", "questplus:all"];
const id = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const integer = (n, min, max) => Number.isInteger(n) && n >= min && n <= max;
export const rewardTranslations = (v, max) =>
	v &&
	typeof v === "object" &&
	!Array.isArray(v) &&
	typeof v.en_us === "string" &&
	Object.keys(v).length <= 128 &&
	Object.entries(v).every(([k, s]) => /^[a-z]{2,3}_[a-z]{2,4}$/.test(k) && typeof s === "string" && s.trim() && s.length <= max);
export const POOL_TYPES = pools;
export function resolveNamedTable(reward, tables, chain = []) {
	if (reward?.type !== "questplus:table") return structuredClone(reward);
	if (!reward.data || Object.keys(reward.data).join() !== "name" || typeof reward.data.name !== "string") throw Error("Invalid table reference");
	const name = reward.data.name;
	if (chain.includes(name) || chain.length >= 16) throw Error("Reward table reference cycle");
	const table = tables?.find((t) => t.name === name);
	if (!table) throw Error("Unknown reward table: " + name);
	const resolved = resolveNamedTable(table.reward, tables, [...chain, name]);
	if (!pools.includes(resolved.type) || poolError(resolved)) throw Error("Invalid named reward table");
	for (const key of ["text", "teamReward", "autoClaim", "excludeFromClaimAll"]) if (reward[key] !== undefined) resolved[key] = structuredClone(reward[key]);
	return resolved;
}
/** Same item the server drops for this crate table (NamedRewardTables.drop). */
export function crateGiveCommand(table) {
	const name = { text: table.crateName || table.name, ...(table.crateColor ? { color: table.crateColor } : {}) },
		parts = ["minecraft:custom_data={questplus_table:" + JSON.stringify(table.name) + "}",
			"minecraft:custom_name={text:" + JSON.stringify(name.text) + (name.color ? ",color:" + JSON.stringify(name.color) : "") + "}"];
	if (table.crateGlow !== undefined) parts.push("minecraft:enchantment_glint_override=" + table.crateGlow);
	return "/give @s questplus:loot_crate[" + parts.join(",") + "]";
}
export function poolError(reward) {
	const { type, data } = reward || {},
		choice = type === "questplus:choice",
		all = type === "questplus:all",
		loot = type === "questplus:loot";
	if (!pools.includes(type) || !data || typeof data !== "object" || Array.isArray(data)) return "Invalid reward table";
	const allowed = ["options", "description", ...(!choice && !all ? ["rolls"] : []), ...(loot ? ["emptyWeight"] : [])];
	if (
		Object.keys(data).some((k) => !allowed.includes(k)) ||
		!Array.isArray(data.options) ||
		data.options.length < (all ? 0 : 1) ||
		data.options.length > 32 ||
		(!choice && !all && !integer(data.rolls ?? 1, 0, 16)) ||
		(loot && !integer(data.emptyWeight, 0, 1000000)) ||
		(data.description !== undefined && !rewardTranslations(data.description, 1024))
	)
		return "Invalid reward table";
	for (const option of data.options) {
		if (!option || typeof option !== "object" || Array.isArray(option)) return "Invalid reward option";
		const plain = { ...option };
		if (!choice) {
			if (!integer(plain.weight, 0, 1000000)) return "Poids de récompense invalide";
			delete plain.weight;
		}
		if (plain.text !== undefined) {
			if (!rewardTranslations(plain.text, 256)) return "Invalid reward translation";
			delete plain.text;
		}
		const keys = Object.keys(plain).sort().join(",");
		if (plain.type === "item") {
			if (
				Object.keys(plain).some((k) => !["count", "item", "type", "components", "onlyOne", "randomBonus"].includes(k)) ||
				!id.test(plain.item || "") ||
				plain.item === "minecraft:air" ||
				!integer(plain.count, 1, 2304)
			)
				return "Invalid item option";
			if (
				plain.components !== undefined &&
				(!plain.components || typeof plain.components !== "object" || Array.isArray(plain.components) || JSON.stringify(plain.components).length > 16384)
			)
				return "Invalid item components";
			if ((plain.onlyOne !== undefined && typeof plain.onlyOne !== "boolean") || (plain.randomBonus !== undefined && !integer(plain.randomBonus, 0, 2304 - plain.count)))
				return "Invalid item option";
		} else if (["experience", "experience_levels"].includes(plain.type)) {
			if (keys !== "amount,type" || !integer(plain.amount, 1, 1000000)) return "Invalid XP option";
		} else if (plain.type === "questplus:advancement") {
			if (
				keys !== "data,type" ||
				Object.keys(plain.data || {}).some((k) => !["advancement", "criterion"].includes(k)) ||
				!id.test(plain.data.advancement || "") ||
				(plain.data.criterion !== undefined && (typeof plain.data.criterion !== "string" || !plain.data.criterion.trim() || plain.data.criterion.length > 256))
			)
				return "Invalid advancement option";
		} else if (plain.type === "questplus:message") {
			if (
				keys !== "data,type" ||
				Object.keys(plain.data || {}).join() !== "message" ||
				typeof plain.data.message !== "string" ||
				!plain.data.message.trim() ||
				plain.data.message.length > 256
			)
				return "Invalid message option";
		} else if (plain.type === "questplus:stage") {
			if (
				keys !== "data,type" ||
				!plain.data ||
				Object.keys(plain.data).sort().join(",") !== "remove,stage" ||
				!id.test(plain.data.stage || "") ||
				typeof plain.data.remove !== "boolean"
			)
				return "Invalid stage reward";
		} else if (plain.type === "questplus:toast") {
			if (
				keys !== "data,type" ||
				!plain.data ||
				Object.keys(plain.data).sort().join(",") !== "description,icon,title" ||
				!rewardTranslations(plain.data.title, 256) ||
				!rewardTranslations(plain.data.description, 256) ||
				!id.test(plain.data.icon || "")
			)
				return "Invalid toast reward";
		} else {
			const descriptor = hubEditorDescriptors("reward").find((d) => d.id === plain.type && d.pool);
			if (!descriptor || keys !== "data,type" || !plain.data || Object.keys(plain.data).some((k) => !descriptor.fields.some((f) => f.key === k)))
				return "Unsupported reward option";
			for (const field of descriptor.fields) {
				const value = plain.data[field.key];
				if (
					field.type === "integer"
						? !Number.isSafeInteger(value)
						: field.type === "boolean"
							? typeof value !== "boolean"
							: field.type === "string"
								? typeof value !== "string"
								: !id.test(value || "")
				)
					return "Invalid extension reward option";
			}
		}
	}
	return "";
}
/** Per draw, with replacement. Choice has no random probability. */
export function poolProbabilities(reward) {
	if (poolError(reward)) return [];
	const { type, data } = reward;
	if (type === "questplus:choice") return data.options.map(() => null);
	if (type === "questplus:all") return data.options.map(() => 1);
	const weights = data.options.map((o) => o.weight);
	if (type === "questplus:loot") weights.push(data.emptyWeight);
	const total = weights.reduce((a, b) => a + b, 0);
	return weights.map((w, i) => (!total ? 0 : w === 0 && i < data.options.length ? 1 : w / total));
}
export function simulatePool(reward, random = Math.random) {
	const error = poolError(reward);
	if (error || reward.type === "questplus:choice") throw Error(error || "Choices are selected by the player");
	if (reward.type === "questplus:all") return structuredClone(reward.data.options);
	const { data } = reward,
		total = data.options.reduce((n, o) => n + o.weight, 0) + (reward.type === "questplus:loot" ? data.emptyWeight : 0),
		results = [];
	if (!total) return results;
	for (const option of data.options) if (option.weight === 0) results.push(structuredClone(option));
	for (let roll = 0; roll < (reward.data.rolls ?? 1); roll++) {
		const value = random();
		if (!Number.isFinite(value) || value < 0 || value >= 1) throw Error("Invalid simulation random source");
		const ticket = value * total;
		let cumulative = 0,
			index = data.options.length;
		for (let i = 0; i < data.options.length; i++) {
			cumulative += data.options[i].weight;
			if (ticket < cumulative) {
				index = i;
				break;
			}
		}
		results.push(structuredClone(reward.data.options[index] ?? null));
	}
	return results;
}
/** Switch a pool's draw type, keeping its options and only the settings the new type accepts. */
export function convertPool(reward, type) {
	const data = reward?.data || {},
		options = (data.options || []).map(({ weight, ...option }) => (type === "questplus:choice" ? option : { ...option, weight: weight ?? 1 }));
	return {
		...reward,
		type,
		data: {
			options,
			...(data.description ? { description: data.description } : {}),
			...(type === "questplus:random" || type === "questplus:loot" ? { rolls: data.rolls ?? 1 } : {}),
			...(type === "questplus:loot" ? { emptyWeight: data.emptyWeight ?? 1 } : {}),
		},
	};
}
export const CRATE_ITEM = "questplus:loot_crate";
/** Item reward giving the crate of a named table, with the same components the server puts on dropped crates. */
export function crateReward(table, count = 1) {
	const components = {
		"minecraft:custom_data": { questplus_table: table.name },
		"minecraft:custom_name": { text: table.crateName || table.name, ...(table.crateColor ? { color: table.crateColor } : {}) },
	};
	if (table.crateGlow !== undefined) components["minecraft:enchantment_glint_override"] = table.crateGlow;
	return { type: "item", item: CRATE_ITEM, count, components };
}
/** Table name of a crate item reward, or undefined for any other reward. */
export function crateTableName(reward) {
	const name = reward?.type === "item" && reward.item === CRATE_ITEM ? reward.components?.["minecraft:custom_data"]?.questplus_table : undefined;
	return typeof name === "string" ? name : undefined;
}
/** Rename a named table and every quest, table or crate reference to it, in place. */
export function renameRewardTable(project, from, to) {
	const fix = (reward) => {
		if (reward?.type === "questplus:table" && reward.data?.name === from) reward.data.name = to;
		if (crateTableName(reward) === from) reward.components["minecraft:custom_data"].questplus_table = to;
	};
	for (const table of project.rewardTables || []) {
		if (table.name === from) table.name = to;
		fix(table.reward);
	}
	for (const quest of project.quests || []) {
		quest.rewards?.forEach(fix);
		quest.tiers?.forEach((tier) => tier.rewards?.forEach(fix));
	}
	return project;
}
