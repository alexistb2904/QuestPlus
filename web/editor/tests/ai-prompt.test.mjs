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

test("every example quest sits inside a background with margin", () => {
	const notes = AI_PROMPT_EXAMPLE.categories[0].annotations.filter((a) => a.style === "background");
	for (const q of AI_PROMPT_EXAMPLE.quests) {
		const { x, y } = q.layout;
		assert.ok(
			notes.some((b) => Math.abs(x - b.x) + 22 + 30 <= b.width / 2 && Math.abs(y - b.y) + 22 + 30 <= b.height / 2),
			q.id
		);
	}
});
