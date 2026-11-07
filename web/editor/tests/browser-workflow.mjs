async (page) => {
	try {
		await page.evaluate(() => sessionStorage.removeItem("__studioWorkflowResult"));
		const assert = (condition, message) => {
			if (!condition) throw Error(message);
		};
		const read = () => page.evaluate(() => JSON.parse(localStorage.getItem("questplus.quest-studio.v1")));
		const save = async () => {
			await page.getByRole("button", { name: "Enregistrer le brouillon", exact: true }).click();
			return read();
		};
		const quest = async () => {
			const s = await save();
			return s.project.quests.find((q) => q.id === s.selection.id);
		};
		const choose = async (name) => page.locator(".quest-item").filter({ hasText: name }).click();
		await page.setViewportSize({ width: 1440, height: 1000 });
		await page.reload();
		if (await page.locator(".startup-dialog").isVisible()) await page.locator(".startup-dialog .startup-choice").nth(1).click();
		await page.locator("#ui-language").selectOption("fr");
		await page.locator("#preview-language").selectOption("fr_fr");
		await choose("Jalons de pierre");
		await page.getByRole("tab", { name: "Progression", exact: true }).click();
		const original = await quest();
		assert(original.progression === "tiered", "fixture tiered");
		page.once("dialog", (dialog) => dialog.accept());
		await page.getByLabel("Mode", { exact: true }).selectOption("once");
		let q = await quest();
		assert(q.progression === "once" && !q.tiers, "atomic progression conversion");
		await page.getByRole("button", { name: "Annuler", exact: true }).click();
		q = await quest();
		assert(JSON.stringify(q) === JSON.stringify(original), "one Undo must restore tiers and rewards exactly");
		await page.getByRole("button", { name: "Rétablir", exact: true }).click();
		q = await quest();
		assert(q.progression === "once" && !q.tiers, "redo progression");
		await page.getByRole("button", { name: "Annuler", exact: true }).click();
		const onCancel = (dialog) => dialog.dismiss();
		page.once("dialog", onCancel);
		await page.locator(".inspector").getByLabel("Type", { exact: true }).selectOption("information");
		q = await quest();
		assert(JSON.stringify(q) === JSON.stringify(original), "cancel information conversion preserves all data");
		page.once("dialog", (dialog) => dialog.accept());
		await page.locator(".inspector").getByLabel("Type", { exact: true }).selectOption("information");
		q = await quest();
		assert(q.kind === "information" && !q.tiers && !q.afterLast && !q.objectives.length, "confirmed information conversion");
		const narrative = JSON.stringify(q.narrative);
		await page.locator(".inspector").getByLabel("Type", { exact: true }).selectOption("standard");
		q = await quest();
		assert(JSON.stringify(q.narrative) === narrative && !q.acknowledgement, "conversion back preserves narrative");
		// Return exactly to the pre-conversion quest before the NPC path.
		await page.getByRole("button", { name: "Annuler", exact: true }).click();
		await page.getByRole("button", { name: "Annuler", exact: true }).click();
		q = await quest();
		assert(JSON.stringify(q) === JSON.stringify(original), "conversion undo restores original definition");
		await choose("Premières bûches");
		const before = await quest();
		await page.getByRole("tab", { name: "PNJ", exact: true }).click();
		await page.getByRole("button", { name: /Objectif d’interaction PNJ/ }).click();
		q = await quest();
		assert(q.objectives.at(-1).event === "npc_interact" && q.objectives.at(-1).target === "minecraft:villager", "NPC objective");
		await page.getByRole("button", { name: "Annuler", exact: true }).click();
		q = await quest();
		assert(JSON.stringify(q) === JSON.stringify(before), "one Undo removes NPC objective");
		await page.getByRole("tab", { name: /^Objectifs/ }).click();
		const source = page.locator(".inspector").getByLabel("Titre (anglais)", { exact: true });
		await source.fill(before.title.en_us + " verified");
		q = await quest();
		assert(q.title.en_us.endsWith(" verified"), "title edit");
		await page.getByRole("button", { name: "Annuler", exact: true }).click();
		q = await quest();
		assert(JSON.stringify(q) === JSON.stringify(before), "one Undo restores title");
		// Exact prerequisite IDs block unsafe deletion without a destructive confirmation.
		await choose("Gardez vos distances");
		const state = await save(),
			id = state.selection.id;
		const dependents = state.project.quests.filter((q) => JSON.stringify(q.requires || {}).includes(id));
		assert(dependents.length > 0, "fixture referenced quest");
		{
			const prior = JSON.stringify(state.project);
			await page.getByRole("button", { name: "Supprimer", exact: true }).click();
			assert(JSON.stringify((await save()).project) === prior, "referenced quest deletion blocked");
		}
		await choose("Abri, dans l\u0027ordre");
		await page.getByRole("button", { name: "Recentrer", exact: true }).click();
		await page.getByRole("button", { name: "Aperçu", exact: true }).click();
		assert(await page.getByRole("dialog").isVisible(), "preview dialog");
		await page.keyboard.press("Escape");
		assert((await page.getByRole("dialog").count()) === 0, "Escape closes preview");
		const [download] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "Exporter ZIP", exact: true }).click()]);
		assert(download.suggestedFilename() === "QuestPlus-quests.zip", "valid export download");
		const archive = "output/playwright/QuestPlus-browser-roundtrip.zip";
		await download.saveAs(archive);
		page.once("dialog", (dialog) => dialog.accept());
		await page.locator("input[type=file][accept]").setInputFiles(archive);
		await page.getByRole("status").filter({ hasText: "Pack importé" }).waitFor();
		const imported = await save();
		assert(imported.project.quests.length === state.project.quests.length, "ZIP import roundtrip");
		await page.locator("#preview-language").selectOption("fr_fr");
		await choose("Abri, dans l\u0027ordre");
		await page.getByRole("button", { name: "Recentrer", exact: true }).click();
		await page.getByRole("button", { name: "Aligner", exact: true }).click();
		const preDrag = await quest(),
			node = page.locator(".graph-node.selected"),
			rect = await node.boundingBox();
		await page.mouse.move(rect.x + rect.width / 2, rect.y + rect.height / 2);
		await page.mouse.down();
		await page.mouse.move(rect.x + rect.width / 2 + 70, rect.y + rect.height / 2 + 45, { steps: 5 });
		await page.mouse.up();
		let dragged = await quest();
		assert(dragged.layout && Number.isInteger(dragged.layout.x) && Number.isInteger(dragged.layout.y), "pointer drag exports integer coordinates");
		await page.getByRole("button", { name: "Annuler", exact: true }).click();
		assert(JSON.stringify(await quest()) === JSON.stringify(preDrag), "one Undo restores drag");
		await page.getByRole("button", { name: "Aligner", exact: true }).click();
		const zoomBefore = await page.locator(".zoom-reset").textContent();
		await page.locator("#viewport").hover();
		await page.mouse.wheel(0, 90);
		await page.waitForFunction((before) => document.querySelector(".zoom-reset").textContent !== before, zoomBefore, { timeout: 3000 });
		await page.getByRole("button", { name: "Recentrer", exact: true }).click();
		await save();
		await page.reload();
		if (await page.locator(".startup-dialog").isVisible()) await page.locator(".startup-dialog .startup-choice").nth(1).click();
		assert((await read()).language === "fr_fr", "saved locale survives reload");
		const errors = await page.evaluate(() => ({
			overflow: document.documentElement.scrollWidth > innerWidth,
			broken: [...document.images].filter((i) => !i.complete || !i.naturalWidth).map((i) => i.src),
		}));
		assert(!errors.overflow, "desktop horizontal overflow");
		const result = {
			result: "passed",
			checks: [
				"atomic undo/redo",
				"conversion cancel/preservation",
				"NPC objective",
				"title undo",
				"referenced deletion blocked",
				"preview Escape",
				"ZIP download/import",
				"pointer drag with snapping and undo",
				"wheel zoom",
				"reload persistence",
			],
			images: errors.broken,
		};
		await page.evaluate((result) => {
			sessionStorage.setItem("__studioWorkflowResult", JSON.stringify(result));
		}, result);
		return result;
	} catch (error) {
		await page.evaluate((message) => sessionStorage.setItem("__studioWorkflowResult", JSON.stringify({ error: message })), error.message);
		throw error;
	}
};
