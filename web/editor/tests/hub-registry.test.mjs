import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import { hubLabel, hubTargets, hubTexture, setHubRegistry } from "../src/core/hub-registry.js";
import { resourceMatcher } from "../src/core/resource-search.js";

test("server mod previews support localized search while rejecting unregistered or unsafe assets", () => {
	setHubRegistry({
		items: ["example:gem"],
		assets: {
			names: { "example:gem": { en_us: "Blue Gem", fr_fr: "Gemme bleue" }, "example:secret": { fr_fr: "Secret" } },
			textures: { "example:gem": "data:image/png;base64,iVBORw0KGgo=", "example:secret": "data:image/png;base64,iVBORw0KGgo=" },
		},
	});
	assert.equal(hubLabel("example:gem", "fr"), "Gemme bleue");
	assert.equal(hubTexture("example:gem"), "data:image/png;base64,iVBORw0KGgo=");
	assert.equal(hubTexture("example:secret"), "");
	assert.ok(resourceMatcher("gemme bleue")("example:gem"));
	setHubRegistry({});
});

test("does not truncate a large server registry", () => {
	const items = Array.from({ length: 30001 }, (_, i) => `mod:item_${i}`);
	setHubRegistry({ items });
	assert.equal(hubTargets("item").length, items.length);
	setHubRegistry({});
});
const source = (file) => fs.readFileSync(new URL(file, import.meta.url), "utf8");
test("server registries filter unsafe entries and support modded targets", () => {
	setHubRegistry({
		items: ["create:brass_ingot", "minecraft:stone", "create:brass_ingot", "../../secrets"],
		blocks: ["create:mechanical_press"],
		enchantments: ["modded:supercharged", "minecraft:mending"],
		biomes: ["modded:crystal_desert"],
		tags: { items: ["#c:ingots", "../../bad"] },
	});
	assert.deepEqual(hubTargets("item"), ["create:brass_ingot", "minecraft:stone"]);
	assert.deepEqual(hubTargets("block"), ["create:mechanical_press"]);
	assert.deepEqual(hubTargets("enchantment"), ["minecraft:mending", "modded:supercharged"]);
	assert.deepEqual(hubTargets("biome"), ["modded:crystal_desert"]);
	assert.deepEqual(hubTargets("unknown"), []);
});
test("Quest Studio preserves offline mode and never stores authentication tokens in localStorage", () => {
	const app = source("../src/App.tsx"),
		hub = source("../src/components/HubPanel.tsx");
	assert.match(app, /<HubPanel\s+project=\{project\}/);
	assert.match(hub, /\/api\/editor\/sessions\//);
	assert.match(hub, /\/publish/);
	assert.match(hub, /\/api\/packs/);
	assert.doesNotMatch(hub, /localStorage\.setItem|sessionStorage\.setItem/);
	const item = source("../src/components/IconPicker.tsx");
	const enchant = source("../src/components/EnchantmentPicker.tsx");
	assert.match(item, /hubTargets\(kind\)/);
	assert.match(enchant, /hubTargets\(["']enchantment["']\)/);
});

test("extension forms retain supported registry field descriptors and reject code fields", () => {
	const fields = ["fluid", "structure", "statistic", "advancement", "effect"].map((type, i) => ({ key: "field" + i, type, label: type }));
	const value = setHubRegistry({
		extensions: [
			{ kind: "event", id: "test:data", label: "Data", fields },
			{ kind: "reward", id: "test:code", label: "Code", fields: [{ key: "run", type: "javascript", label: "Run" }] },
		],
	});
	assert.equal(value.extensions.length, 1);
	assert.deepEqual(value.extensions[0].fields, fields);
});
