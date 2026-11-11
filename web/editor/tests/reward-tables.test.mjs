import JSON5 from "json5";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { category, emptyProject, fileMap, importDocuments, quest, validate } from "../src/core/project.js";
import { convertPool, crateGiveCommand, crateReward, crateTableName, poolError, poolProbabilities, renameRewardTable, simulatePool } from "../src/core/reward-tables.js";

const loot = {
	type: "questplus:loot",
	data: {
		rolls: 2,
		emptyWeight: 2,
		options: [
			{ weight: 1, type: "item", item: "minecraft:diamond", count: 3 },
			{ weight: 1, type: "experience", amount: 10 },
		],
	},
};
test("the shared server fixture preserves exact quest data through Studio JSON5 export", () => {
	const content = readFileSync(new URL("../../../mod/server/src/test/resources/studio-rewards.json5", import.meta.url), "utf8").replace(/\r\n/g, "\n");
	const q = JSON5.parse(content),
		project = emptyProject();
	project.categories.push(category(q.category, "Studio"));
	project.quests.push(q);
	assert.deepEqual(validate(project), []);
	assert.deepEqual(JSON5.parse(fileMap(project).get("quests/studio_roundtrip.json5")), q);
});
test("probabilities include empty rolls and simulation cannot mutate authored rewards", () => {
	assert.deepEqual(poolProbabilities(loot), [0.25, 0.25, 0.5]);
	assert.deepEqual(
		simulatePool(loot, () => 0.8),
		[null, null]
	);
	const result = simulatePool(loot, () => 0);
	result[0].count = 999;
	assert.equal(loot.data.options[0].count, 3);
	assert.equal(poolError(loot), "");
});
test("all pools award each option; choices do not run a random simulation", () => {
	assert.equal(simulatePool({ type: "questplus:all", data: { options: loot.data.options } }).length, 2);
	assert.throws(() => simulatePool({ type: "questplus:choice", data: { options: [] } }));
});
test("tables cannot nest references, executable rewards, or malformed weights", () => {
	for (const option of [
		{ type: "questplus:random", data: { table: "self" }, weight: 1 },
		{ type: "questplus:command", data: { command: "op @s" }, weight: 1 },
		{ type: "experience", amount: 10, weight: 1.5 },
	])
		assert.ok(poolError({ ...loot, data: { ...loot.data, options: [option] } }));
});
test("named project tables survive ZIP documents as snapshots without entering server quest/config JSON", () => {
	const project = emptyProject(),
		cat = category("questplus:test", "Tables");
	project.categories.push(cat);
	project.quests.push(quest(cat, "questplus:test", "Tables"));
	project.rewardTables = [{ name: "Miner loot", reward: structuredClone(loot) }];
	assert.deepEqual(validate(project), []);
	const files = fileMap(project),
		loaded = importDocuments([...files].filter(([name]) => /\.json5?$/.test(name)).map(([name, content]) => ({ name, content })));
	assert.deepEqual(loaded.rewardTables, project.rewardTables);
	assert.ok(!files.get("config.json").includes("rewardTables"));
	assert.ok(!files.get("quests/test.json5").includes("rewardTables"));
	loaded.rewardTables[0].reward.data.options[0].count = 9;
	assert.equal(project.rewardTables[0].reward.data.options[0].count, 3);
	project.rewardTables.push({ name: "Miner loot", reward: loot });
	assert.ok(validate(project).length);
});
test("crate tables export exactly the shared server fixture and give the same item mobs drop", () => {
	const fixture = JSON.parse(readFileSync(new URL("../../../mod/server/src/test/resources/studio-crate-tables.json", import.meta.url), "utf8")),
		project = emptyProject();
	project.rewardTables = structuredClone(fixture.rewardTables);
	assert.deepEqual(validate(project), []);
	assert.deepEqual(JSON.parse(fileMap(project).get("queststudio-compatibility.json")), fixture);
	assert.equal(
		crateGiveCommand(project.rewardTables[0]),
		'/give @s questplus:loot_crate[minecraft:custom_data={questplus_table:"Common chest"},minecraft:custom_name={text:"Coffre commun",color:"#4aa8ff"},minecraft:enchantment_glint_override=true]'
	);
	assert.equal(crateGiveCommand(project.rewardTables[1]), '/give @s questplus:loot_crate[minecraft:custom_data={questplus_table:"Jackpot"},minecraft:custom_name={text:"Jackpot"}]');
	// The server rejects a crate whose resolved pool is a choice, even through an alias.
	project.rewardTables.push({ name: "Choice alias", reward: { type: "questplus:table", data: { name: "Pick one" } }, crate: true });
	assert.ok(validate(project).some((e) => e.message === "Crates cannot contain a player choice"));
});
test("changing a table's draw type keeps options and drops settings the new type rejects", () => {
	const choice = convertPool(loot, "questplus:choice");
	assert.equal(poolError(choice), "");
	assert.deepEqual(Object.keys(choice.data), ["options"]);
	assert.ok(choice.data.options.every((o) => o.weight === undefined));
	const back = convertPool(choice, "questplus:loot");
	assert.equal(poolError(back), "");
	assert.deepEqual([back.data.rolls, back.data.emptyWeight, back.data.options[0].weight], [1, 1, 1]);
	assert.equal(poolError(convertPool(loot, "questplus:all")), "");
	assert.equal(loot.data.options[0].weight, 1, "source pool is not mutated");
});
test("renaming a reward table updates quest, tier and alias references", () => {
	const project = emptyProject();
	project.categories.push(category("c", "C"));
	const q = quest("c", "Q");
	q.rewards = [{ type: "questplus:table", data: { name: "Old" } }];
	q.tiers = [{ id: "t", threshold: 1, rewards: [{ type: "questplus:table", data: { name: "Old" } }] }];
	project.quests.push(q);
	project.rewardTables = [
		{ name: "Old", reward: structuredClone(loot) },
		{ name: "Alias", reward: { type: "questplus:table", data: { name: "Old" } } },
	];
	renameRewardTable(project, "Old", "New");
	assert.deepEqual(project.rewardTables.map((t) => t.name), ["New", "Alias"]);
	assert.equal(project.rewardTables[1].reward.data.name, "New");
	assert.equal(q.rewards[0].data.name, "New");
	assert.equal(q.tiers[0].rewards[0].data.name, "New");
});
test("a loot crate reward gives the same item the server drops and is validated against its table", () => {
	const project = emptyProject();
	project.categories.push(category("c", "C"));
	const q = quest("c", "Q");
	const table = { name: "Ore box", crate: true, crateColor: "#55ffff", reward: structuredClone(loot) };
	project.rewardTables = [table];
	q.rewards = [crateReward(table, 2)];
	project.quests.push(q);
	assert.deepEqual(q.rewards[0].components, {
		"minecraft:custom_data": { questplus_table: "Ore box" },
		"minecraft:custom_name": { text: "Ore box", color: "#55ffff" },
	});
	assert.equal(crateTableName(q.rewards[0]), "Ore box");
	assert.equal(crateTableName({ type: "item", item: "minecraft:diamond", count: 1 }), undefined);
	const before = validate(project).length;
	assert.ok(!validate(project).some((e) => /crate|Unknown reward table/.test(e.message)));
	table.crate = false;
	assert.ok(validate(project).some((e) => e.where === q.id && /not an openable crate/.test(e.message)));
	table.crate = true;
	assert.equal(validate(project).length, before, "a crate reward on a crate table adds no error");
	renameRewardTable(project, "Ore box", "Gem box");
	assert.equal(crateTableName(q.rewards[0]), "Gem box");
	project.rewardTables = [];
	assert.ok(validate(project).some((e) => e.where === q.id && /Unknown reward table/.test(e.message)));
});
