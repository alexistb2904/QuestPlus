import assert from 'node:assert/strict';
import test from 'node:test';
import { AI_PROMPT_EXAMPLE, aiPrompt } from '../src/core/ai-prompt.js';
import { importDocuments, validate } from '../src/core/project.js';

test("the AI prompt example imports as a single JSON file and passes validation", () => {
	const prompt = aiPrompt("26.1.2");
	const example = JSON.parse(prompt.slice(prompt.indexOf("\n{\n"), prompt.indexOf("\n\nBefore answering")));
	assert.equal(example.targetMinecraftVersion, "26.1.2");
	const p = importDocuments([{ name: "questplus-pack.json", content: JSON.stringify(example) }]);
	assert.equal(p.targetMinecraftVersion, "26.1.2");
	assert.equal(p.quests.length, AI_PROMPT_EXAMPLE.quests.length);
	assert.deepEqual(validate(p), []);
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
