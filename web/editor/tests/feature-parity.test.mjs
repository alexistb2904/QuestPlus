import assert from "node:assert/strict";
import test from "node:test";
import { addChild, childrenOf, setAt, wrap } from "../src/core/conditions.js";
import { category, emptyProject, quest, references, validate } from "../src/core/project.js";
test("started/quorum dependency forms preserve child paths and reference validation", () => {
	const group = { at_least: { count: 1, of: [{ started: "test:a" }, { quest: "test:b" }] } };
	assert.equal(childrenOf(group).type, "at_least");
	const edited = addChild(group, "", { quest: "test:c" });
	assert.equal(edited.at_least.of.length, 3);
	assert.deepEqual(references(edited), ["test:a", "test:b", "test:c"]);
	assert.deepEqual(setAt(group, "0", { quest: "test:c" }).at_least.of[0], { quest: "test:c" });
	assert.deepEqual(wrap(group, "", "or"), { or: group.at_least.of });
	const p = emptyProject(),
		c = category("test:cat", "Parity");
	p.categories.push(c);
	const a = quest(c, "test:a", "A"),
		b = quest(c, "test:b", "B");
	p.quests.push(a, b);
	b.requires = { at_least: { count: 1, of: [{ started: a.id }] } };
	assert.deepEqual(validate(p), []);
	b.requires.at_least.count = 2;
	assert.ok(validate(p).length);
});
test("observation and stages are authored objectives with safe toast/stage rewards", () => {
	const p = emptyProject(),
		c = category("test:cat", "Parity"),
		q = quest(c, "test:a", "A");
	p.categories.push(c);
	p.quests.push(q);
	q.objectives = [
		{ id: "watch", event: "observe_entity", target: "minecraft:cow", count: 40 },
		{ id: "stage", event: "stage", target: "test:chapter", count: 1 },
	];
	q.rewards = [
		{ type: "questplus:stage", data: { stage: "test:next", remove: false } },
		{ type: "questplus:toast", data: { title: { en_us: "Ready" }, description: { en_us: "Next chapter" }, icon: "minecraft:book" } },
	];
	assert.deepEqual(validate(p), []);
	q.rewards[0].data.remove = "yes";
	assert.ok(validate(p).length);
});

test("presentation, stage pool options and linked named tables validate before export", async () => {
	const { resolveNamedTable } = await import("../src/core/reward-tables.js");
	const p = emptyProject(),
		c = category("test:cat", "Parity"),
		q = quest(c, "test:a", "A");
	p.categories.push(c);
	p.quests.push(q);
	c.group = "Exploration";
	q.presentation = { subtitle: { en_us: "Track" }, shape: "hexagon", iconScale: 1.25, tags: ["chapter:one"], hideDetailsUntilUnlocked: true };
	p.rewardTables = [
		{ name: "stages", reward: { type: "questplus:all", data: { options: [{ type: "questplus:stage", weight: 1, data: { stage: "test:ready", remove: false } }] } } },
	];
	q.rewards = [{ type: "questplus:table", data: { name: "stages" } }];
	assert.deepEqual(validate(p), []);
	const first = resolveNamedTable(q.rewards[0], p.rewardTables);
	p.rewardTables[0].reward.data.options[0].data.remove = true;
	assert.equal(first.data.options[0].data.remove, false);
	assert.equal(resolveNamedTable(q.rewards[0], p.rewardTables).data.options[0].data.remove, true);
	q.presentation.iconScale = 3;
	assert.ok(validate(p).length);
	q.presentation.iconScale = 1;
	p.rewardTables[0].reward = q.rewards[0];
	assert.ok(validate(p).some((e) => e.message.includes("cycle")));
});
