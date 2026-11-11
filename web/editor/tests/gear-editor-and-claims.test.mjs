import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import { demoProject, gearRequirementText, objectiveLabel, orderedCategories, quest, validate } from "../src/core/project.js";
const read = (path) => fs.readFileSync(new URL(path, import.meta.url), "utf8");

test("new projects contain only maintained categories in progression order", () => {
	const project = demoProject();
	const removed = ["combat", "exploration", "mining", "survival"];
	assert.ok(!project.categories.some((c) => removed.some((id) => c.id === "questplus:" + id)));
	assert.ok(!project.quests.some((q) => removed.some((id) => q.category === "questplus:" + id)));
	assert.equal(project.categories.length, 17);
	const ids = orderedCategories(project.categories).map((c) => c.id);
	assert.equal(ids[0], "questplus:homestead");
	assert.ok(ids.indexOf("questplus:endgame") > ids.indexOf("questplus:nether"));
	assert.ok(ids.indexOf("questplus:equipment") > ids.indexOf("questplus:quarries"));
});

test("studio supports modded gear with armor slots and enchantment levels", () => {
	const project = demoProject();
	const q = quest(project.categories[0], "questplus:custom/enchanted_pickaxe", "Enchanted Pickaxe");
	q.objectives = [
		{
			id: "enchanted",
			event: "equipment_check",
			target: "questplus:gear",
			count: 1,
			conditions: {
				"questplus:gear": { items: [{ slot: "mainhand", item: "some_mod:legendary_pickaxe", enchantments: { "minecraft:efficiency": 5, "minecraft:unbreaking": 3 } }] },
			},
		},
	];
	project.quests.push(q);
	assert.deepEqual(validate(project), []);
	q.objectives[0].conditions["questplus:gear"].items[0].enchantments["minecraft:efficiency"] = 11;
	assert.ok(
		validate(project).some((x) => /condition/i.test(x.message)),
		"Enchant 11 must not pass server validation"
	);
});

test("Studio displays short localized armor objectives instead of serialized conditions", () => {
	const golden = demoProject().quests.find((q) => q.id === "questplus:equipment/set_golden");
	assert.ok(golden);
	for (const lang of ["en_us", "fr_fr", "es_es", "zh_cn"]) {
		const label = objectiveLabel(golden.objectives[0], lang);
		assert.ok(label.length < 100, lang + " " + label);
		assert.doesNotMatch(label, /\\{.*items|\\bhead:/);
	}
	const levelled = demoProject().quests.find((q) => q.id === "questplus:equipment/diamond_p4");
	const summary = gearRequirementText(levelled.objectives[0].conditions, "en_us");
	assert.match(summary, /protection 4/);
});

test("game presentation uses a gear-specific renderer instead of JSON fallback", () => {
	const s = read("../../../mod/client/src/main/java/dev/questplus/fabric/client/QuestConditionText.java");
	assert.match(s, /case "questplus:gear" -> gear\(value\)/);
	assert.match(s, /gear\.full/);
	assert.doesNotMatch(s, /return tr\("other",\s*"questplus:gear",\s*value\)/);
	const advanced = read("../../../mod/server/src/main/java/dev/questplus/fabric/AdvancedEvents.java");
	const context = read("../../../mod/server/src/main/java/dev/questplus/fabric/QuestPlusMod.java");
	assert.match(advanced, /public static Map<String,\s*Object> gearSnapshot/);
	assert.match(context, /context\.put\("gear",\s*AdvancedEvents\.gearSnapshot\(player\)\)/);
});

test("completed and repeatable quests remain pinned until manually removed", () => {
	const client = read("../../../mod/client/src/main/java/dev/questplus/fabric/client/QuestPlusClient.java");
	assert.doesNotMatch(client, /settings\.pinned\.removeIf/);
	assert.match(client, /QuestClientState\.flag\(next,\s*"unlocked"\)/);
	assert.doesNotMatch(client, /QuestToast\.show\(b\s*>=\s*target/);
	assert.doesNotMatch(client, /progress_notice/);
	const screen = read("../../../mod/client/src/main/java/dev/questplus/fabric/client/QuestScreen.java");
	const pinStart = screen.indexOf("var pinButton = button(");
	const pinEnd = screen.indexOf("questAction = button(", pinStart);
	assert.ok(pinStart >= 0 && pinEnd > pinStart);
	const pinControl = screen.slice(pinStart, pinEnd);
	assert.match(pinControl, /settings\.pinned\.remove\(selected\)/);
	assert.match(pinControl, /settings\.pinned\.add\(selected\)/);
	assert.match(pinControl, /settings\.pinned\.size\(\) < ClientSettings\.MAX_PINNED/);
	assert.doesNotMatch(pinControl, /completed|terminal|waitingRestart/);
	assert.match(screen, /claimAll\s*=\s*button\("claim_all"/);
	assert.match(screen, /NEW_UNLOCKS\.contains\(q\.id\(\)\)/);
	assert.match(screen, /treeLeft\s*=\s*left\s*\+\s*38/);
});

test("actual reward labels describe what was obtained in every Minecraft locale", () => {
	const expected = {
		en_us: "Rewards obtained",
		fr_fr: "Récompenses obtenues",
		es_es: "Recompensas obtenidas",
		zh_cn: "已获得的奖励",
	};
	for (const [locale, label] of Object.entries(expected)) {
		const lang = JSON.parse(read("../../../mod/client/src/main/resources/assets/questplus/lang/" + locale + ".json"));
		assert.equal(lang["questplus.client.actual_rewards"], label);
		assert.ok(lang["questplus.client.possible_rewards"]);
	}
});

test("claim-all remains a single server-authorized action with durable sequential claims", () => {
	const runtime = read("../../../mod/server/src/main/java/dev/questplus/fabric/QuestRuntime.java");
	const network = read("../../../mod/server/src/main/java/dev/questplus/fabric/QuestNetwork.java");
	assert.match(network, /case "claim_all" -> runtime\.claimAllFromTablet\(player\)/);
	assert.match(runtime, /claimAllFromTablet\(ServerPlayer player\)[\s\S]*?claimAll\(player,/);
	assert.match(runtime, /private void claimNext\(ServerPlayer player,\s*List<String> ids,\s*int index,\s*Runnable onDelivered\)/);
	assert.match(runtime, /claim\(player,\s*grant\.questId\(\),\s*key,\s*\(\) ->/);
	assert.match(runtime, /visibleQuests\(uuid\)/);
});

test("brightness maximum keeps the original colors without a white boost", () => {
	const screen = read("../../../mod/client/src/main/java/dev/questplus/fabric/client/TabletSurfaceRenderer.java");
	assert.match(screen, /if\s*\(brightness\s*<\s*\.999\)/);
	assert.match(screen, /\(1\s*-\s*brightness\)\s*\*\s*255/);
	assert.doesNotMatch(screen, /glow|0x00ffffff/);
});
