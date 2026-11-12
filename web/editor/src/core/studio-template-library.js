import { slug } from "./project.js";
const KEY = "questplus.studio.saved-quest-templates.v1";
const COLUMNS = new Set([
	"schemaVersion",
	"icon",
	"size",
	"title",
	"description",
	"descriptionRich",
	"objectives",
	"rewards",
	"progression",
	"objectiveMode",
	"objectiveThreshold",
	"timeLimitSeconds",
	"cooldownSeconds",
	"noDeath",
	"kind",
	"acknowledgement",
	"narrative",
	"tiers",
	"afterLast",
	"progressBeforeUnlock",
	"hidden",
	"presentation",
]);
export function savedQuestTemplates(storage = globalThis.localStorage) {
	try {
		const values = JSON.parse(storage?.getItem(KEY) || "[]");
		if (!Array.isArray(values)) return [];
		return values
			.slice(0, 32)
			.filter(
				(template) =>
					template &&
					/^custom:[a-z0-9_]{1,40}$/.test(template.id) &&
					typeof template.label === "string" &&
					template.label.length <= 64 &&
					template.quest &&
					typeof template.quest === "object" &&
					!Array.isArray(template.quest) &&
					Array.isArray(template.quest.objectives) &&
					Array.isArray(template.quest.rewards)
			);
	} catch {
		return [];
	}
}
export function saveQuestTemplate(quest, label, storage = globalThis.localStorage) {
	const name = String(label || "").trim();
	if (!name || name.length > 64) throw Error("Template name must be 1–64 characters");
	const id = "custom:" + slug(name).slice(0, 40),
		saved = savedQuestTemplates(storage);
	const data = Object.fromEntries(Object.entries(quest).filter(([key]) => COLUMNS.has(key)));
	// Templates never carry the source's quest ID, category, layout, team progress or dependencies.
	// A newly created quest must always be independently playable.
	const clean = structuredClone(data),
		item = { id, label: name, quest: clean };
	const existing = saved.findIndex((t) => t.id === id);
	if (existing >= 0) saved.splice(existing, 1, item);
	else {
		if (saved.length >= 32) throw Error("32 saved templates maximum");
		saved.push(item);
	}
	const serialized = JSON.stringify(saved);
	if (serialized.length > 500_000) throw Error("Template library exceeds storage safety limit");
	storage.setItem(KEY, serialized);
	return item;
}
export function removeQuestTemplate(id, storage = globalThis.localStorage) {
	const saved = savedQuestTemplates(storage),
		kept = saved.filter((t) => t.id !== id);
	if (kept.length !== saved.length) storage.setItem(KEY, JSON.stringify(kept));
	return kept;
}
export function buildSavedQuest(template, category, id, title, position) {
	if (!template?.quest || !Array.isArray(template.quest.objectives)) throw Error("Invalid saved quest template");
	const q = structuredClone(template.quest);
	// The caller generates unique quest and objective IDs. No prerequisite references cross projects.
	q.id = id;
	q.category = category.id;
	q.layout = { x: position.x, y: position.y };
	q.title = { en_us: title };
	delete q.requires;
	return q;
}

/** Portable, versioned export of personal authoring templates (no account data). */
export function exportTemplateBundle(storage = globalThis.localStorage) {
	const templates = savedQuestTemplates(storage);
	return JSON.stringify({ format: "questplus-studio-templates", version: 1, templates }, null, 2) + "\n";
}
/** All validation is staged before touching persistent storage. Duplicate template IDs replace old copies. */
export function importTemplateBundle(source, storage = globalThis.localStorage) {
	if (typeof source !== "string" || source.length > 750_000) throw Error("Template file exceeds 750 kB");
	let bundle;
	try {
		bundle = JSON.parse(source);
	} catch {
		throw Error("Template file is not valid JSON");
	}
	if (!bundle || bundle.format !== "questplus-studio-templates" || bundle.version !== 1 || !Array.isArray(bundle.templates) || bundle.templates.length > 32)
		throw Error("Unsupported template library format");
	const result = new Map(savedQuestTemplates(storage).map((t) => [t.id, t]));
	const imported = new Set();
	for (const candidate of bundle.templates) {
		if (
			!candidate ||
			typeof candidate !== "object" ||
			Array.isArray(candidate) ||
			typeof candidate.label !== "string" ||
			!candidate.label.trim() ||
			candidate.label.length > 64 ||
			typeof candidate.id !== "string" ||
			!/^custom:[a-z0-9_]{1,40}$/.test(candidate.id) ||
			!candidate.quest ||
			typeof candidate.quest !== "object" ||
			Array.isArray(candidate.quest) ||
			!Array.isArray(candidate.quest.objectives) ||
			!Array.isArray(candidate.quest.rewards) ||
			candidate.quest.objectives.length > 256 ||
			candidate.quest.rewards.length > 64
		)
			throw Error("Invalid saved quest template");
		if (imported.has(candidate.id)) throw Error("Duplicate template " + candidate.id);
		imported.add(candidate.id);
		const quest = Object.fromEntries(Object.entries(candidate.quest).filter(([key]) => COLUMNS.has(key)));
		result.set(candidate.id, { id: candidate.id, label: candidate.label.trim(), quest: structuredClone(quest) });
	}
	if (result.size > 32) throw Error("32 saved templates maximum; delete some before importing");
	const serialized = JSON.stringify([...result.values()]);
	if (serialized.length > 500_000) throw Error("Merged template library exceeds 500 kB");
	storage.setItem(KEY, serialized);
	return imported.size;
}
