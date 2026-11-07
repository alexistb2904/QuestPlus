import { references } from "./project.js";

export function dependencyError(quests, sourceId, targetId) {
	const byId = new Map(quests.map((q) => [q.id, q])),
		source = byId.get(sourceId),
		target = byId.get(targetId);
	if (!source || !target) return "Quest not found.";
	if (sourceId === targetId) return "Choose another quest to connect.";
	if (source.kind === "information" && source.acknowledgement === "read_only") return "A read-only quest cannot unlock another quest.";
	if (references(target.requires).includes(sourceId)) return "These quests are already connected.";
	const pending = [sourceId],
		seen = new Set();
	while (pending.length) {
		const id = pending.pop();
		if (id === targetId) return "This connection would create a dependency cycle.";
		if (seen.has(id)) continue;
		seen.add(id);
		const quest = byId.get(id);
		if (quest) pending.push(...references(quest.requires));
	}
	return "";
}
export function withPrerequisite(quests, sourceId, targetId) {
	const error = dependencyError(quests, sourceId, targetId);
	if (error) throw Error(error);
	const existing = quests.find((q) => q.id === targetId).requires,
		added = { quest: sourceId };
	if (!existing) return added;
	// A new cable adds a required quest without weakening an existing OR/NOT expression.
	return Array.isArray(existing.and) ? { and: [...structuredClone(existing.and), added] } : { and: [structuredClone(existing), added] };
}
