import assert from "node:assert/strict";
import test from "node:test";
import { inspectFtbPack, selectFtbQuests } from "../src/core/ftb-import.js";
import { setHubRegistry } from "../src/core/hub-registry.js";
import { changeObjectiveEvent } from "../src/core/objective-targets.js";
import { fileMap, importDocuments, validate } from "../src/core/project.js";
import { simulatePool } from "../src/core/reward-tables.js";
const chapter = (quests) => ({ name: "chapters/test.json5", content: JSON.stringify({ id: "0000000000000010", quests }) });
const source = (id, tasks = [{ id: "0000000000000020", type: "checkmark" }], rewards = []) => ({ id, tasks, rewards });
const portable = (project) => {
	assert.deepEqual(validate(project), []);
	const copy = importDocuments([...fileMap(project)].map(([name, content]) => ({ name, content })));
	assert.deepEqual(validate(copy), []);
	return copy;
};

test("modern translations, started dependencies, visibility and selection survive export", () => {
	const a = source("000000000000000a"),
		b = {
			...source("000000000000000b"),
			dependencies: [a.id],
			dependency_requirement: "one_started",
			shape: "rsquare",
			invisible: true,
			invisible_until_tasks: 1,
			x: 2,
			y: -1,
		};
	const report = inspectFtbPack([
		chapter([a, b]),
		{
			name: "lang/fr_fr/chapters/test.json5",
			content: JSON.stringify({ "quest.000000000000000B.title": "Titre FR", "quest.000000000000000B.quest_desc": ["Ligne 1", "Ligne 2"] }),
		},
	]);
	const q = report.project.quests[1];
	assert.equal(q.title.fr_fr, "Titre FR");
	assert.equal(q.description.fr_fr, "Ligne 1\nLigne 2");
	assert.equal(q.requires.started, report.project.quests[0].id);
	assert.equal(q.presentation.shape, "rounded_square");
	assert.equal(q.presentation.invisibleUntilTasks, 1);
	assert.equal(q.presentation.invisibleUntilCompleted, undefined);
	assert.deepEqual(q.layout, { x: 128, y: -64 });
	const selected = selectFtbQuests(report, [q.id]);
	assert.equal(selected.quests.length, 2);
	assert.deepEqual(portable(selected).ftbMappings, selected.ftbMappings);
	assert.equal(inspectFtbPack([chapter([{ ...a, invisible: false, invisible_until_tasks: 1 }])]).project.quests[0].presentation?.invisibleUntilTasks, undefined);
});
test("components, OR filters, consuming XP and explicit fluid/energy units retain meaning", () => {
	const tasks = [
		{ id: "1", type: "item", item: { id: "minecraft:stone", components: { "minecraft:custom_data": { grade: 2 } } }, match_components: "partial", consume_items: true },
		{ id: "2", type: "item", item: { id: "ftbfiltersystem:smart_filter", components: { "ftbfiltersystem:filter": "or(item(minecraft:stone)tag(minecraft:logs))" } } },
		{ id: "3", type: "xp", value: 3, points: true },
		{ id: "4", type: "fluid", fluid: { id: "minecraft:water", amount: 81000 } },
		{ id: "5", type: "energy", value: 100 },
	];
	const report = inspectFtbPack(
		[
			chapter([
				source("a", tasks, [
					{
						id: "6",
						type: "item",
						item: { id: "minecraft:stone", components: { "minecraft:custom_data": { grade: 2 } } },
						random_bonus: 3,
						only_one: true,
						team_reward: true,
						auto: "no_toast",
						exclude_from_claim_all: true,
					},
				]),
			]),
		],
		{ fluidUnits: "droplets", energyRatio: 10 }
	);
	const q = report.project.quests[0];
	assert.equal(q.objectives[0].event, "item_turn_in");
	assert.equal(q.objectives[0].data.matchComponents, "partial");
	assert.deepEqual(q.objectives[1].data.targets, ["minecraft:stone", "#minecraft:logs"]);
	assert.equal(q.objectives[2].event, "xp_submit_points");
	assert.equal(q.objectives[3].count, 1000);
	assert.equal(q.objectives[4].data.fePerEnergy, 10);
	assert.equal(q.rewards[0].randomBonus, 3);
	assert.equal(q.rewards[0].onlyOne, true);
	assert.equal(q.rewards[0].teamReward, true);
	assert.equal(q.rewards[0].autoClaim, "no_toast");
	portable(report.project);
});
test("64-bit table IDs, guaranteed entries and crate variants are lossless", () => {
	const table = {
		name: "reward_tables/a.json5",
		content:
			"{id:'7fffffffffffffff',loot_size:0,empty_weight:1,loot_crate:{string_id:'rare',item_name:'Rare',color:16711680,glow:true,drops:{passive:0,monster:1,boss:5}},rewards:[{weight:0,item:{id:'minecraft:stone'}}]}",
	};
	const quest = {
		name: "chapters/a.json5",
		content:
			"{id:'A',quests:[{id:'B',tasks:[{id:'C',type:'checkmark'}],rewards:[{id:'D',type:'loot',table_id:9223372036854775807,team_reward:true},{id:'E',item:{id:'ftbquests:lootcrate',components:{'ftbquests:loot_crate':'rare'}}}]}]}",
	};
	const report = inspectFtbPack([table, quest]);
	assert.equal(report.questCount, 1, report.warnings.join("\n"));
	const q = report.project.quests[0];
	assert.equal(q.rewards[0].teamReward, true);
	assert.equal(q.rewards[0].excludeFromClaimAll, true);
	assert.equal(q.rewards[1].components["minecraft:custom_data"].questplus_table, "ftb_crate/7fffffffffffffff");
	const crate = report.project.rewardTables.find((t) => t.crate);
	assert.equal(crate.crateColor, "#ff0000");
	assert.equal(crate.crateGlow, true);
	assert.deepEqual(simulatePool(crate.reward), [crate.reward.data.options[0]]);
	portable(report.project);
	const random = {
		type: "questplus:random",
		data: {
			rolls: 0,
			options: [
				{ type: "experience", amount: 2, weight: 0 },
				{ type: "experience", amount: 3, weight: 1 },
			],
		},
	};
	assert.deepEqual(simulatePool(random), [random.data.options[0]]);
	random.data.options.pop();
	assert.deepEqual(simulatePool(random), []);
});
test("local decorations import without fetching URLs; unsupported actions are reported", () => {
	const data = "data:image/png;base64,iVBORw0KGgo=",
		doc = chapter([source("a")]),
		content = JSON.parse(doc.content);
	content.images = [
		{ x: 1, y: 2, width: 2, height: 1, image: "test:textures/note.png" },
		{ x: 0, y: 0, width: 2, height: 1, image: "text:Hello" },
		{ x: 0, y: 0, width: 1, height: 1, image: "https://invalid.example/x.png", click: "run_command:op @s" },
	];
	doc.content = JSON.stringify(content);
	const report = inspectFtbPack([doc, { name: "resourcepacks/demo/assets/test/textures/note.png", content: data }]),
		notes = report.project.categories[0].annotations;
	assert.equal(notes.length, 2);
	assert.equal(notes[0].rich.en_us[0].data, data);
	assert.equal(notes[0].x, 0);
	assert.equal(notes[0].y, 96);
	assert.equal(notes[1].text.en_us, "Hello");
	assert.match(report.warnings.join(), /decoration 2 not imported/);
	portable(report.project);
});
test("registered adapters use exact namespaces and explicitly opt into pools", () => {
	setHubRegistry({
		extensions: [
			{ kind: "event", id: "addon:counter", label: "Counter", fields: [{ key: "mode", type: "string", label: "Mode" }] },
			{ kind: "reward", id: "addon:coins", label: "Coins", pool: true, fields: [{ key: "amount", type: "integer", label: "Amount" }] },
		],
	});
	try {
		const report = inspectFtbPack([
			chapter([
				source(
					"a",
					[{ id: "t", type: "addon:counter", target: "addon:signal", count: 2, mode: "real" }],
					[{ type: "random", table_data: { loot_size: 1, rewards: [{ type: "addon:coins", amount: 5, weight: 1 }] } }]
				),
			]),
		]);
		assert.equal(report.questCount, 1, report.warnings.join("\n"));
		portable(report.project);
		assert.equal(
			inspectFtbPack([chapter([source("a", [{ type: "other:counter", target: "addon:signal", count: 2, mode: "real" }]), { ...source("b"), dependencies: ["a"] }])])
				.questCount,
			0
		);
	} finally {
		setHubRegistry({});
	}
});
test("unknown fields, duplicate IDs and Task Screen actions never become easier quests", () => {
	assert.equal(
		inspectFtbPack([
			chapter([
				{ ...source("a"), invented_requirement: true },
				{ ...source("b"), dependencies: ["a"] },
			]),
		]).questCount,
		0
	);
	assert.equal(inspectFtbPack([chapter([source("a"), source("a")])]).questCount, 0);
	assert.equal(inspectFtbPack([chapter([source("a", [{ type: "item", item: "minecraft:stone", task_screen_only: true }])])]).questCount, 0);
	const next = changeObjectiveEvent({ id: "x", event: "xp_submit_levels", target: "questplus:levels", count: 5, data: { stale: true } }, "energy_submit", "test:a");
	assert.equal(next.target, "questplus:fe");
	assert.equal(next.data, undefined);
});
