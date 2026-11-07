import assert from "node:assert/strict";
import test from "node:test";
import { iconUrl } from "../src/ui/minecraft-icons.js";
test("vanilla icons use model-resolved texture paths and skip unavailable textures", () => {
	assert.match(iconUrl("minecraft:diamond"), /textures\/item\/diamond\.png$/);
	assert.match(iconUrl("minecraft:acacia_fence"), /textures\/block\/acacia_planks\.png$/);
	assert.equal(iconUrl("minecraft:air"), "");
});
