import assert from 'node:assert/strict';
import test from 'node:test';
import { AI_PROMPT_EXAMPLE, AI_PROMPT_SAMPLES, aiPrompt } from '../src/core/ai-prompt.js';
import { EVENT_KEYS, MODES, PROGRESSIONS, importDocuments, validate } from '../src/core/project.js';
import { POOL_TYPES } from '../src/core/reward-tables.js';

test("the AI prompt example imports as a single JSON file and passes validation", () => {
	const prompt = aiPrompt("26.1.2");
	const example = JSON.parse(prompt.slice(prompt.indexOf("\n{\n"), prompt.indexOf("\n\n## 7. Final check")));
	assert.equal(example.targetMinecraftVersion, "26.1.2");
	const p = importDocuments([{ name: "questplus-pack.json", content: JSON.stringify(example) }]);
	assert.equal(p.targetMinecraftVersion, "26.1.2");
	assert.equal(p.quests.length, AI_PROMPT_EXAMPLE.quests.length);
	assert.deepEqual(validate(p), []);
});

test("every format sample quoted in the prompt opens as one file and passes validation", () => {
	const S = AI_PROMPT_SAMPLES,
		base = AI_PROMPT_EXAMPLE.quests.find((q) => q.id === "mypack:leather_armor"),
		make = (id, extra) => ({ ...base, id: "mypack:" + id, ...extra });
	const quests = [
		...AI_PROMPT_EXAMPLE.quests,
		...Object.entries(S.quest).map(([key, fields]) => make("sample_" + key, fields)),
		...Object.entries(S.requires).map(([key, requires]) => make("requires_" + key, { requires })),
		make("sample_objectives", { objectives: Object.values(S.objectives) }),
		make("miner_request", { rewards: Object.values(S.rewards) }),
	];
	const p = importDocuments([{ name: "questplus-pack.json", content: JSON.stringify({ ...AI_PROMPT_EXAMPLE, quests, npcs: S.npcs, rewardTables: S.rewardTables }) }]);
	assert.deepEqual(p.config.npcs, S.npcs);
	assert.deepEqual(p.rewardTables, S.rewardTables);
	assert.deepEqual(validate(p), []);
	const prompt = aiPrompt("26.3");
	for (const sample of [...Object.values(S.objectives), ...Object.values(S.rewards), ...Object.values(S.requires), S.npcs, S.rewardTables])
		assert.ok(prompt.includes(JSON.stringify(sample)), JSON.stringify(sample));
});

test("the prompt names every built in event, objective rule, progression and reward type", () => {
	const prompt = aiPrompt("26.3");
	const rewards = ["experience", "experience_levels", "item", "questplus:advancement", "questplus:message", "questplus:stage", "questplus:toast", ...POOL_TYPES, "questplus:table"];
	for (const name of [...EVENT_KEYS, ...MODES, ...PROGRESSIONS, ...rewards]) assert.ok(prompt.includes(name), name);
});

test("the form fills the prompt and empty fields fall back to defaults", () => {
	const prompt = aiPrompt("26.3", { theme: "Sky Tech", mods: "Create 6.0\n  Botania\n", languages: " " });
	assert.match(prompt, /- Modpack name and theme: Sky Tech\n/);
	assert.match(prompt, /- Mods in the pack: Create 6\.0, Botania\n/);
	assert.match(prompt, /Also: none\n/);
	assert.match(prompt, /- Size: not specified, choose what fits best\n/);
});

test("the example follows its own layout rules and shows a confirmation quest and a routed link", () => {
	const quests = AI_PROMPT_EXAMPLE.quests;
	for (const [i, a] of quests.entries())
		for (const b of quests.slice(i + 1))
			assert.ok(Math.hypot(a.layout.x - b.layout.x, a.layout.y - b.layout.y) >= (a.size + b.size) / 2 + 30, a.id + " / " + b.id);
	assert.ok(quests.some((q) => q.objectives.some((o) => o.event === "manual")));
	assert.ok(AI_PROMPT_EXAMPLE.categories[0].linkRoutes.length);
});

test("the tone field fills the prompt and falls back to deduction", () => {
	const prompt = aiPrompt("26.3", { tone: "Hardcore, short hints" });
	assert.ok(prompt.includes("- Tone and guidance level: Hardcore, short hints\n"));
	assert.ok(aiPrompt("26.3").includes("- Tone and guidance level: not specified, deduce it as explained below\n"));
});
