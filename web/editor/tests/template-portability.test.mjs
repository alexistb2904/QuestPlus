import assert from "node:assert/strict";
import test from "node:test";
import { exportTemplateBundle, importTemplateBundle, saveQuestTemplate, savedQuestTemplates } from "../src/core/studio-template-library.js";

const storage = () => {
	const map = new Map();
	return { getItem: (k) => map.get(k) ?? null, setItem: (k, v) => map.set(k, v) };
};
const quest = {
	id: "private:original",
	category: "private:category",
	requires: { quest: "private:source" },
	schemaVersion: 2,
	icon: "minecraft:book",
	title: { en_us: "Template" },
	description: { en_us: "Reusable" },
	objectives: [{ id: "step", event: "block_break", target: "minecraft:stone", count: 1 }],
	rewards: [],
	progression: "once",
	layout: { x: 3, y: 5 },
};
test("exports and reimports personal templates without source IDs, dependencies or progress", () => {
	const a = storage(),
		b = storage();
	saveQuestTemplate(quest, "Reliable Starter", a);
	const raw = exportTemplateBundle(a);
	assert.ok(!raw.includes("private:original"));
	assert.ok(!raw.includes("private:source"));
	assert.equal(importTemplateBundle(raw, b), 1);
	const saved = savedQuestTemplates(b);
	assert.equal(saved.length, 1);
	assert.equal(saved[0].label, "Reliable Starter");
	assert.deepEqual(saved[0].quest.objectives, quest.objectives);
});
test("malformed imports do not change persistent templates", () => {
	const a = storage();
	saveQuestTemplate(quest, "Existing", a);
	const before = exportTemplateBundle(a);
	assert.throws(() => importTemplateBundle("{invalid json", a), /valid JSON/);
	assert.throws(() => importTemplateBundle(JSON.stringify({ format: "questplus-studio-templates", version: 2, templates: [] }), a), /Unsupported/);
	assert.throws(
		() =>
			importTemplateBundle(
				JSON.stringify({ format: "questplus-studio-templates", version: 1, templates: [{ id: "custom:test", label: "Invalid", quest: { objectives: [] } }] }),
				a
			),
		/Invalid/
	);
	assert.equal(exportTemplateBundle(a), before);
});
test("duplicate imports are rejected without partial replacement", () => {
	const a = storage();
	const t = { id: "custom:test", label: "Test", quest: { objectives: [{ id: "step", event: "manual", target: "mod:q", count: 1 }], rewards: [] } };
	const bundle = JSON.stringify({ format: "questplus-studio-templates", version: 1, templates: [t, t] });
	assert.throws(() => importTemplateBundle(bundle, a), /Duplicate/);
	assert.equal(savedQuestTemplates(a).length, 0);
});
