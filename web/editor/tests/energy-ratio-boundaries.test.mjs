import assert from "node:assert/strict";
import test from "node:test";
import { emptyProject, category, quest, validate } from "../src/core/project.js";

function project(count, thresholds) {
	const p = emptyProject(), c = category(), q = quest(c);
	p.categories.push(c);
	p.quests.push(q);
	q.objectives = [{ id: "energy", event: "energy_submit", target: "questplus:fe", count, data: { fePerEnergy: 10 } }];
	if (thresholds) {
		q.progression = "tiered";
		q.tiers = thresholds.map((threshold, i) => ({ id: "tier" + i, threshold, rewards: [] }));
	}
	return p;
}

test("energy submissions require whole native units at every target", () => {
	assert.deepEqual(validate(project(100)), []);
	assert.ok(validate(project(101)).some((issue) => issue.message.includes("multiple of fePerEnergy")));
	assert.ok(validate(project(1)).some((issue) => issue.message.includes("multiple of fePerEnergy")));
});

test("tiered energy conversion validates scaled targets", () => {
	assert.deepEqual(validate(project(1, [10, 20])), []);
	assert.ok(validate(project(1, [10, 11])).some((issue) => issue.message.includes("multiple of fePerEnergy")));
});
