import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import { backgroundRect, edgePoints, insertWaypoint, nearestSegment } from "../src/core/diagram-geometry.js";
import { BUILTIN_PROJECT } from "../src/data/default-data.js";

test("all 219 built-in quests are aligned in disjoint tablet-compatible compartments", () => {
	let total = 0;
	for (const category of BUILTIN_PROJECT.categories) {
		const labels = (category.annotations || []).filter((note) => note.style === "label");
		const backgrounds = (category.annotations || []).filter((note) => note.style === "background");
		const quests = BUILTIN_PROJECT.quests.filter((quest) => quest.category === category.id);
		total += quests.length;
		assert.ok(labels.length > 0, category.id + " has no sections");
		assert.equal(backgrounds.length, labels.length, category.id + " backgrounds");
		for (const box of backgrounds) {
			assert.ok(Number.isInteger(box.width) && Number.isInteger(box.height));
			assert.ok(box.width > 0 && box.height > 0);
			const label = labels.find((note) => note.text.en_us === box.text.en_us);
			assert.ok(label, category.id + " missing heading for " + box.id);
			assert.equal(label.y, box.y - box.height / 2 + 39, category.id + " title baseline");
			assert.equal(label.x, box.x - box.width / 2 + 166, category.id + " title left alignment");
		}
		for (const quest of quests) {
			assert.ok(Number.isFinite(quest.layout?.x) && Number.isFinite(quest.layout?.y), quest.id);
			const inside = backgrounds.filter((box) => Math.abs(quest.layout.x - box.x) <= box.width / 2 - 23 && Math.abs(quest.layout.y - box.y) <= box.height / 2 - 23);
			assert.equal(inside.length, 1, quest.id + " must lie in exactly one section");
			const box = inside[0];
			assert.equal((quest.layout.y - (box.y - box.height / 2) - 128) % 101, 0, quest.id + " must be row-aligned");
		}
		for (let i = 0; i < quests.length; i++)
			for (let j = i + 1; j < quests.length; j++)
				assert.ok(Math.hypot(quests[i].layout.x - quests[j].layout.x, quests[i].layout.y - quests[j].layout.y) >= 98, quests[i].id + " overlaps " + quests[j].id);
		const topByRow = new Map();
		for (const box of backgrounds) {
			const row = box.y - box.height / 2,
				label = labels.find((note) => note.text.en_us === box.text.en_us);
			if (topByRow.has(row)) assert.equal(label.y, topByRow.get(row), category.id + " headings on same row");
			topByRow.set(row, label.y);
		}
	}
	assert.equal(total, 219);
});

test("homestead and equipment have coherent thematic compartments with aligned titles", () => {
	const homestead = BUILTIN_PROJECT.categories.find((c) => c.id === "questplus:homestead");
	const equipment = BUILTIN_PROJECT.categories.find((c) => c.id === "questplus:equipment");
	assert.deepEqual(
		homestead.annotations.filter((a) => a.style === "background").map((a) => a.text.en_us),
		["A place to live", "Spaces to discover", "Working rooms"]
	);
	const boxes = equipment.annotations.filter((a) => a.style === "background");
	assert.equal(boxes.length, 6);
	assert.ok(boxes.find((b) => b.text.en_us === "Enchanted armor").y > boxes.find((b) => b.text.en_us === "Armor collection").y);
	assert.equal(boxes.find((b) => b.text.en_us === "Melee weapons").y, boxes.find((b) => b.text.en_us === "Armor collection").y);
	assert.equal(boxes.find((b) => b.text.en_us === "Ranged & mobility").y, boxes.find((b) => b.text.en_us === "Armor collection").y);
	assert.ok(BUILTIN_PROJECT.quests.some((q) => q.id === "questplus:equipment/equipment_master"));
});

test("routing creates one user waypoint on a cable without persisting invisible elbows", () => {
	const a = { x: 0, y: 0 },
		b = { x: 200, y: 140 },
		click = { x: 100, y: 70 };
	const visual = edgePoints(a, b);
	assert.equal(visual.length, 4);
	const index = nearestSegment(visual, click);
	assert.deepEqual(insertWaypoint([], index, click), [click]);
	const authored = [
		{ x: 50, y: 60 },
		{ x: 150, y: 60 },
	];
	const inserted = insertWaypoint(authored, 1, click);
	assert.deepEqual(inserted, [authored[0], click, authored[1]]);
	assert.deepEqual(authored, [
		{ x: 50, y: 60 },
		{ x: 150, y: 60 },
	]);
	assert.equal(insertWaypoint(authored, 999, click).length, 3);
	assert.deepEqual(backgroundRect({ x: 12, y: 20 }, { x: 212, y: 120 }), { x: 112, y: 70, width: 200, height: 100 });
});

test("Minecraft tablet renders backgrounds and waypoints from the same category JSON", () => {
	const file = fs.readFileSync(new URL("../../../mod/client/src/main/java/dev/questplus/fabric/client/QuestScreen.java", import.meta.url), "utf8");
	const gradle = fs.readFileSync(new URL("../../../mod/build.gradle", import.meta.url), "utf8");
	const canvas = fs.readFileSync(new URL("../src/components/TreeCanvas.tsx", import.meta.url), "utf8");
	assert.match(file, /drawBackgrounds\(g\)/);
	assert.match(file, /note\.get\("style"\)\.getAsString\(\)\.equals\("background"\)/);
	assert.match(file, /getAsJsonArray\("linkRoutes"\)/);
	assert.match(file, /routes\.get\(edge\)/);
	assert.match(gradle, /include 'challenge-catalogue\.json', 'equipment-catalogue\.json'/);
	assert.match(canvas, /insertWaypoint\(authored\s*\|\|\s*\[\],\s*index,\s*routePosition\(e\)\)/);
});
