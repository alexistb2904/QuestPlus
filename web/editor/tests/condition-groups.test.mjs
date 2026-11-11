import assert from "node:assert/strict";
import test from "node:test";
import { addToGroup, groupView, setAt, setGroupMode } from "../src/core/conditions.js";

const a = { quest: "x:a" },
	b = { quest: "x:b" },
	c = { quest: "x:c" };

test("adding to an empty or single prerequisite builds an 'all' group", () => {
	assert.deepEqual(addToGroup(null, "", a), a);
	assert.deepEqual(addToGroup(a, "", b), { and: [a, b] });
	assert.equal(groupView(a).mode, "and");
	assert.deepEqual(groupView(null).items, []);
});
test("changing the mode keeps items, including 'none' stored as NOT of OR", () => {
	const root = { and: [a, b] };
	assert.deepEqual(setGroupMode(root, "", "or"), { or: [a, b] });
	assert.deepEqual(setGroupMode(root, "", "at_least", 5), { at_least: { count: 2, of: [a, b] } });
	const none = setGroupMode(root, "", "none");
	assert.deepEqual(none, { not: { or: [a, b] } });
	assert.equal(groupView(none).mode, "none");
	assert.deepEqual(groupView(none).items.map((i) => i.node), [a, b]);
	assert.deepEqual(addToGroup(none, "", c), { not: { or: [a, b, c] } });
	assert.deepEqual(setGroupMode(none, "", "and"), { and: [a, b] });
	assert.deepEqual(setGroupMode(a, "", "none"), { not: a });
});
test("nested 'any one of' groups add and remove items through their editor paths", () => {
	let root = addToGroup({ and: [a] }, "", { or: [b] });
	assert.deepEqual(root, { and: [a, { or: [b] }] });
	root = addToGroup(root, "1", c);
	assert.deepEqual(root, { and: [a, { or: [b, c] }] });
	const item = groupView(root.and[1], "1").items[1];
	assert.equal(item.path, "1.1");
	assert.deepEqual(setAt(root, item.removePath, null), { and: [a, { or: [b] }] });
	const single = { and: [a, { not: b }] };
	const view = groupView(single.and[1], "1");
	assert.equal(view.mode, "none");
	assert.equal(view.items[0].removePath, "1", "removing the only item of a NOT removes the group");
	assert.deepEqual(setAt(single, view.items[0].removePath, null), { and: [a] });
});
