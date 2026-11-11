import assert from "node:assert/strict";
import test from "node:test";
import { validateNpcs } from "../src/core/project.js";

const check = (npcs) => {
	const issues = [];
	validateNpcs(npcs, new Set(["questplus:stone"]), (where, message) => issues.push(message));
	return issues;
};
const npc = (fields) => ({ id: "miner", name: { en_us: "Old Miner" }, quests: ["questplus:stone"], ...fields });

test("quest givers mirror the server's NPC rules", () => {
	assert.deepEqual(
		check([
			npc({
				spawns: [
					{ type: "structure", target: "#minecraft:village", chance: 0.1 },
					{ type: "structure", target: "minecraft:village_snowy", chance: 0.5 },
					{ type: "random", target: "minecraft:overworld", chance: 0.001 },
				],
			}),
		]),
		[]
	);
	assert.deepEqual(check([npc({ quests: [] })]), [], "an NPC may be created before it offers quests");
	assert.equal(check([npc({ quests: ["questplus:missing"] })]).length, 1);
	assert.equal(check([npc({ spawns: [{ type: "structure", target: "minecraft:village_moon", chance: 0.1 }] })]).length, 1);
	assert.equal(check([npc({ spawns: [{ type: "random", target: "#minecraft:overworld", chance: 0.1 }] })]).length, 1);
	assert.equal(check([npc({ spawns: [{ type: "structure", target: "#minecraft:village", chance: 2 }] })]).length, 1);
	assert.equal(check([npc({ name: { fr_fr: "Mineur" } })]).length, 1);
	assert.equal(check([npc({}), npc({})]).length, 1, "identifiers are unique");
});

test("place tiles toggle spawn rules and keep modded targets separate", async () => {
	const { customRules, formatPercent, fromPercent, npcId, ruleFor, setRule } = await import("../src/core/npc-spawns.js");
	const npc = { id: "a", name: { en_us: "A" }, quests: [], spawns: [{ type: "structure", target: "mymod:castle", chance: 0.1 }] };
	setRule(npc, "structure", "minecraft:igloo", fromPercent("0.5"));
	setRule(npc, "structure", "minecraft:igloo", 0.25);
	assert.equal(ruleFor(npc, "structure", "minecraft:igloo").chance, 0.25, "updating keeps one rule");
	setRule(npc, "random", "minecraft:overworld", 0.002);
	assert.deepEqual(customRules(npc).map((rule) => rule.target), ["mymod:castle"]);
	setRule(npc, "structure", "minecraft:igloo", null);
	assert.equal(ruleFor(npc, "structure", "minecraft:igloo"), undefined);
	assert.deepEqual(check([{ ...npc, quests: ["questplus:stone"] }]), [], "tiles only produce rules the server accepts");
	assert.equal(formatPercent(0.005, "fr"), "0,5");
	assert.equal(formatPercent(0.00002, "en"), "0.002");
	assert.equal(npcId("Marlo le Cartographe", new Set(["marlo_le_cartographe"])), "marlo_le_cartographe_2");
});

test("quest giver appearance accepts a villager outfit or a 64×64 PNG skin", async () => {
	const { skinProblem } = await import("../src/core/npc-spawns.js");
	const png = (width, height) => {
		const bytes = Buffer.alloc(33);
		bytes.set([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a], 0);
		bytes.writeUInt32BE(13, 8);
		bytes.write("IHDR", 12, "ascii");
		bytes.writeUInt32BE(width, 16);
		bytes.writeUInt32BE(height, 20);
		return "data:image/png;base64," + bytes.toString("base64");
	};
	assert.equal(skinProblem(png(64, 64)), "");
	assert.match(skinProblem(png(64, 32)), /64 × 64/);
	assert.deepEqual(check([npc({ model: "villager", villager: "taiga" })]), []);
	assert.deepEqual(check([npc({ model: "player", skin: png(64, 64) })]), []);
	assert.equal(check([npc({ villager: "moon" })]).length, 1);
	assert.equal(check([npc({ skin: png(64, 64) })]).length, 1, "a skin needs the player model");
});

test("crate bosses accept entity IDs, #tags and ! exclusions only", async () => {
	const { crateBossesValid, parseCrateBosses } = await import("../src/core/project.js");
	const list = parseCrateBosses(" chronodawn:time_tyrant, #c:bosses\n!minecraft:wither ,");
	assert.deepEqual(list, ["chronodawn:time_tyrant", "#c:bosses", "!minecraft:wither"]);
	assert.ok(crateBossesValid(list) && crateBossesValid([]));
	for (const bad of [["Warden"], ["minecraft:"], ["#!c:bosses"], [1], "minecraft:warden", Array(257).fill("a:b")]) assert.ok(!crateBossesValid(bad), String(bad));
});
