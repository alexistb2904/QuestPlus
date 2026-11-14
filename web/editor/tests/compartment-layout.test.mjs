import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import { backgroundRect, edgePoints, insertWaypoint, nearestSegment } from "../src/core/diagram-geometry.js";
import { BUILTIN_PROJECT } from "../src/data/default-data.js";

const SHAPES = ["square", "circle", "rounded_square", "diamond", "pentagon", "hexagon", "octagon", "heart", "gear"];
const refs = (node, out = []) => {
	if (node && typeof node === "object") {
		if (typeof node.quest === "string") out.push(node.quest);
		for (const value of Object.values(node)) refs(value, out);
	}
	return out;
};

test("every built-in chapter opens on one guide and draws its story without overlaps", () => {
	assert.equal(BUILTIN_PROJECT.quests.length, 252);
	for (const category of BUILTIN_PROJECT.categories) {
		const quests = BUILTIN_PROJECT.quests.filter((quest) => quest.category === category.id);
		const guides = quests.filter((quest) => quest.presentation?.subtitle?.en_us === "Chapter guide");
		assert.equal(guides.length, 1, category.id + " guide");
		const guide = guides[0];
		assert.equal(guide.presentation.shape, "octagon");
		assert.ok(guide.kind === "information" || guide.objectives.some((o) => o.event === "manual" && o.target === guide.id), guide.id);
		assert.ok(guide.description.en_us && guide.description.fr_fr, guide.id);
		for (const quest of quests) {
			assert.ok(SHAPES.includes(quest.presentation?.shape), quest.id + " shape");
			assert.ok(Number.isInteger(quest.size) && quest.size >= 36 && quest.size <= 92, quest.id + " size");
		}
		// Size shows importance: the guide or a milestone is the largest node of its chapter.
		assert.ok(quests.every((q) => q.size <= Math.max(guide.size, ...quests.filter((m) => m.presentation.shape === "diamond").map((m) => m.size))), category.id);
		for (let i = 0; i < quests.length; i++)
			for (let j = i + 1; j < quests.length; j++) {
				const a = quests[i], b = quests[j];
				assert.ok(Math.hypot(a.layout.x - b.layout.x, a.layout.y - b.layout.y) >= (a.size + b.size) / 2 + 25, a.id + " overlaps " + b.id);
			}
		for (const note of category.annotations.filter((a) => a.style !== "background"))
			for (const quest of quests)
				assert.ok(Math.abs(quest.layout.x - note.x) >= note.width / 2 + quest.size / 2 || Math.abs(quest.layout.y - note.y) >= note.height / 2 + quest.size / 2, note.id + " covers " + quest.id);
		assert.ok(category.annotations.filter((a) => a.style === "note").length <= 2, category.id + " notes");
		for (const route of category.linkRoutes || []) {
			const to = BUILTIN_PROJECT.quests.find((q) => q.id === route.to);
			assert.equal(to.category, category.id);
			assert.ok(refs(to.requires).includes(route.from), route.from + " -> " + route.to);
		}
	}
});

test("the armory fans each armor set into its trophy and the house keeps its rooms", () => {
	const homestead = BUILTIN_PROJECT.categories.find((c) => c.id === "questplus:homestead");
	const equipment = BUILTIN_PROJECT.categories.find((c) => c.id === "questplus:equipment");
	assert.deepEqual(
		homestead.annotations.filter((a) => a.style === "background").map((a) => a.text.en_us),
		["A place to live", "The hearth", "Spaces to discover", "Working rooms"]
	);
	const collector = BUILTIN_PROJECT.quests.find((q) => q.id === "questplus:equipment/armor_collector");
	const sets = refs(collector.requires);
	assert.equal(sets.length, 7);
	for (const set of sets) assert.ok(equipment.linkRoutes.some((r) => r.from === set && r.to === collector.id), set);
	const master = BUILTIN_PROJECT.quests.find((q) => q.id === "questplus:equipment/equipment_master");
	assert.equal(master.presentation.shape, "diamond");
	assert.ok(BUILTIN_PROJECT.quests.filter((q) => q.category === equipment.id).every((q) => q.size <= master.size));
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
