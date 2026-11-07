/** Safe multi-quest edits for modpack authors. IDs, objectives and prerequisites are never rewritten.
 * Returned project is a copy; callers validate before committing to undo history. */
export const BULK_ACTIONS = Object.freeze([
	{ id: "hide", label: "Hide quests" },
	{ id: "show", label: "Show quests" },
	{ id: "category", label: "Move to category" },
	{ id: "icon", label: "Set icon" },
	{ id: "reward_item", label: "Add an item reward" },
	{ id: "reward_xp", label: "Add XP-point reward" },
	{ id: "reward_levels", label: "Add XP-level reward" },
]);
const ID = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const isInt = (n, min, max) => Number.isSafeInteger(n) && n >= min && n <= max;
export function bulkEdit(project, ids, operation) {
	if (!project || !Array.isArray(project.quests) || !Array.isArray(project.categories)) throw Error("Invalid project");
	if (!Array.isArray(ids) || !ids.length || ids.length > 5000 || new Set(ids).size !== ids.length) throw Error("Choose between 1 and 5000 distinct quests");
	if (!BULK_ACTIONS.some((x) => x.id === operation?.type)) throw Error("Unknown bulk operation");
	const selected = new Set(ids),
		existing = new Set(project.quests.map((q) => q.id));
	if (ids.some((id) => !existing.has(id))) throw Error("Selection contains an unknown quest");
	const { type, value } = operation;
	if (type === "category" && !project.categories.some((c) => c.id === value)) throw Error("Destination category not found");
	if (type === "icon" && !ID.test(value || "")) throw Error("Invalid icon ID");
	if (type === "reward_item" && (!ID.test(value?.item || "") || value.item === "minecraft:air" || !isInt(value?.count, 1, 2304))) throw Error("Invalid item reward");
	if (["reward_xp", "reward_levels"].includes(type) && !isInt(value, 1, 1_000_000)) throw Error("Invalid XP amount");
	const clone = structuredClone(project);
	for (const quest of clone.quests) {
		if (!selected.has(quest.id)) continue;
		if (type === "hide") quest.hidden = true;
		if (type === "show") quest.hidden = false;
		if (type === "category") quest.category = value;
		if (type === "icon") quest.icon = value;
		if (type.startsWith("reward_")) {
			if (quest.kind === "information" && quest.acknowledgement === "read_only") throw Error("Read-only information quests cannot have rewards: " + quest.id);
			if ((quest.rewards?.length || 0) >= 64) throw Error("Reward limit reached: " + quest.id);
			quest.rewards ||= [];
			quest.rewards.push(
				type === "reward_item"
					? { type: "item", item: value.item, count: value.count }
					: { type: type === "reward_levels" ? "experience_levels" : "experience", amount: value }
			);
		}
	}
	return clone;
}
