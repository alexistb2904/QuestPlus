import assert from "node:assert/strict";
import test from "node:test";
import { quest as newQuest, validate } from "../src/core/project.js";
import { buildSavedQuest, saveQuestTemplate, savedQuestTemplates } from "../src/core/studio-template-library.js";

const memory = () => {
	const store = new Map();
	return { getItem: (key) => store.get(key) || null, setItem: (key, value) => store.set(key, value) };
};
test("custom templates persist and strip dependency/identity while keeping objectives", () => {
	const storage = memory();
	const category = { id: "questplus:testing", icon: "minecraft:book", title: { en_us: "Testing" }, description: { en_us: "Testing category" }, annotations: [] };
	const origin = newQuest(category, "questplus:testing/original", "Original", { x: 10, y: 20 });
	origin.requires = { quest: "questplus:missing" };
	origin.rewards = [{ type: "experience", amount: 20 }];
	origin.objectives = [{ id: "boss", event: "entity_kill", target: "minecraft:wither", count: 1 }];
	origin.progression = "repeatable";
	const item = saveQuestTemplate(origin, "Boss challenge", storage);
	assert.equal(item.id, "custom:boss_challenge");
	assert.deepEqual(
		savedQuestTemplates(storage).map((t) => t.label),
		["Boss challenge"]
	);
	const copy = buildSavedQuest(item, category, "questplus:testing/new", "New boss", { x: 50, y: -20 });
	assert.equal(copy.id, "questplus:testing/new");
	assert.equal(copy.category, category.id);
	assert.equal(copy.title.en_us, "New boss");
	assert.deepEqual(copy.layout, { x: 50, y: -20 });
	assert.equal(copy.requires, undefined);
	assert.equal(copy.objectives[0].target, "minecraft:wither");
	assert.equal(copy.rewards[0].amount, 20);
	assert.deepEqual(validate({ categories: [category], quests: [copy], config: { schemaVersion: 1, autosaveSeconds: 5 } }), []);
});
test("saved templates replace names without adding duplicate rows", () => {
	const storage = memory(),
		category = { id: "questplus:test" };
	const q = newQuest(category, "questplus:q", "Q");
	saveQuestTemplate(q, "First", storage);
	saveQuestTemplate(q, "First", storage);
	assert.equal(savedQuestTemplates(storage).length, 1);
});
