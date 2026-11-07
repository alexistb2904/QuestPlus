import assert from "node:assert/strict";
import { test } from "node:test";
import { setHubRegistry } from "../src/core/hub-registry.js";
import { resourceMatcher } from "../src/core/resource-search.js";

test("search supports multilingual names, synonyms, typos and multiple words", () => {
	assert.ok(resourceMatcher("bois")("minecraft:oak_planks"));
	assert.ok(resourceMatcher("planches de chene")("minecraft:oak_planks"));
	assert.ok(resourceMatcher("diamnd swrd")("minecraft:diamond_sword"));
	assert.ok(resourceMatcher("diamnod sword")("minecraft:diamond_sword"));
	assert.ok(resourceMatcher("diamante")("minecraft:diamond"));
	assert.ok(resourceMatcher("钻石")("minecraft:diamond"));
	assert.ok(resourceMatcher("brass ingot")("create:brass_ingot"));
	assert.equal(resourceMatcher("or")("minecraft:iron_sword"), false);
	assert.equal(resourceMatcher("oak")("minecraft:birch_planks"), false);
	assert.equal(resourceMatcher("diamond sword")("minecraft:iron_sword"), false);
	assert.equal(resourceMatcher("zzzzzz")("minecraft:diamond"), false);
});

test("cached searches follow registry replacements, disconnects and labels", () => {
	const matches = resourceMatcher("quartz");
	setHubRegistry({ items: ["example:gem"], assets: { names: { "example:gem": { en_us: "Quartz" } } } });
	assert.equal(matches("example:gem"), true);
	assert.equal(matches("example:gem"), true);
	setHubRegistry({ items: ["example:gem"], assets: { names: { "example:gem": { en_us: "Sapphire" } } } });
	assert.equal(matches("example:gem"), false);
	assert.equal(resourceMatcher("sapphire")("example:gem"), true);
	assert.equal(matches("example:gem", "Quartz"), true);
	assert.equal(matches("example:gem", "Sapphire"), false);
	setHubRegistry({});
	assert.equal(resourceMatcher("sapphire")("example:gem"), false);
});

test("large search index keeps matching after its bounded cache evicts older entries", () => {
	setHubRegistry({});
	const matches = resourceMatcher("resource");
	for (let i = 0; i < 100002; i++) assert.equal(matches("example:resource_" + i), true);
	assert.equal(matches("example:resource_0"), true);
	assert.equal(resourceMatcher("unrelated")("example:resource_100001"), false);
});
