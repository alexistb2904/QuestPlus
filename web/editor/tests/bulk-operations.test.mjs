import assert from "node:assert/strict";
import test from "node:test";
import { bulkEdit } from "../src/core/bulk-operations.js";
import { validate } from "../src/core/project.js";
const base = () => ({
	categories: [
		{ id: "mod:a", title: { en_us: "A" }, description: { en_us: "A" }, icon: "minecraft:book" },
		{ id: "mod:b", title: { en_us: "B" }, description: { en_us: "B" }, icon: "minecraft:book" },
	],
	quests: [
		{
			schemaVersion: 2,
			id: "mod:q1",
			category: "mod:a",
			title: { en_us: "Q1" },
			icon: "minecraft:book",
			objectives: [{ id: "step", event: "block_break", target: "minecraft:stone", count: 1 }],
			progression: "once",
			rewards: [],
		},
		{
			schemaVersion: 2,
			id: "mod:q2",
			category: "mod:a",
			title: { en_us: "Q2" },
			icon: "minecraft:book",
			objectives: [{ id: "step", event: "block_break", target: "minecraft:stone", count: 1 }],
			progression: "once",
			rewards: [],
		},
	],
	config: { schemaVersion: 1, autosaveSeconds: 5 },
});
test("bulk move clones and leaves original project untouched", () => {
	const old = base(),
		next = bulkEdit(old, ["mod:q1"], { type: "category", value: "mod:b" });
	assert.equal(old.quests[0].category, "mod:a");
	assert.equal(next.quests[0].category, "mod:b");
	assert.equal(next.quests[1].category, "mod:a");
	assert.deepEqual(validate(next), []);
});
test("batch reward appends only selected quest and schema validates", () => {
	const next = bulkEdit(base(), ["mod:q2"], { type: "reward_levels", value: 10 });
	assert.deepEqual(next.quests[0].rewards, []);
	assert.deepEqual(next.quests[1].rewards, [{ type: "experience_levels", amount: 10 }]);
	assert.deepEqual(validate(next), []);
});
test("bulk changes reject ambiguous and unsafe selections/values", () => {
	assert.throws(() => bulkEdit(base(), ["mod:q1", "mod:q1"], { type: "hide" }), /distinct/);
	assert.throws(() => bulkEdit(base(), ["mod:missing"], { type: "hide" }), /unknown quest/);
	assert.throws(() => bulkEdit(base(), ["mod:q1"], { type: "reward_item", value: { item: "minecraft:air", count: 2 } }), /Invalid item reward/);
	assert.throws(() => bulkEdit(base(), ["mod:q1"], { type: "category", value: "mod:missing" }), /not found/);
});
test("read-only information rewards are blocked in bulk editor", () => {
	const old = base();
	old.quests[0] = { ...old.quests[0], kind: "information", acknowledgement: "read_only", objectives: [] };
	assert.throws(() => bulkEdit(old, ["mod:q1"], { type: "reward_xp", value: 5 }), /Read-only/);
});
