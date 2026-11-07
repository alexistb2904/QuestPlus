import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const read = (path) => fs.readFileSync(new URL(path, import.meta.url), "utf8");

test("toast and HUD read different saved position fields", () => {
	const toast = read("../../../mod/client/src/main/java/dev/questplus/fabric/client/QuestToast.java");
	const hud = read("../../../mod/client/src/main/java/dev/questplus/fabric/client/QuestHud.java");
	const settings = read("../../../mod/client/src/main/java/dev/questplus/fabric/client/ClientSettings.java");
	assert.match(toast, /QuestPlusClient\.settings\.notificationPosition/);
	assert.doesNotMatch(toast, /settings\.hudPosition/);
	assert.match(hud, /settings\.hudPosition/);
	assert.match(settings, /hudPosition = 2, notificationPosition = 1/);
	assert.match(settings, /!json\.has\("notificationPosition"\)/);
	assert.match(settings, /settings\.notificationPosition\s*=\s*1/);
	assert.match(settings, /settings\.notificationPosition\s*<\s*0\s*\|\|\s*settings\.notificationPosition\s*>\s*8/);
});

test("tablet settings cycle toast position without changing HUD position", () => {
	const screen = read("../../../mod/client/src/main/java/dev/questplus/fabric/client/QuestScreen.java");
	assert.match(screen, /button\("hud_position"/);
	assert.match(screen, /button\("notification_position"/);
	assert.match(screen, /settings\.notificationPosition\s*=\s*\(settings\.notificationPosition\s*\+\s*1\)\s*%\s*9/);
	assert.match(screen, /settings\.hudPosition\s*=\s*\(settings\.hudPosition\s*\+\s*1\)\s*%\s*9/);
	assert.match(screen, /notificationAnchor\.hoverText\(notificationLabel\)/);
});

test("new notification position setting is translated into all four primary languages", () => {
	for (const locale of ["en_us", "fr_fr", "es_es", "zh_cn"]) {
		const lang = JSON.parse(read("../../../mod/client/src/main/resources/assets/questplus/lang/" + locale + ".json"));
		assert.ok(lang["questplus.client.notification_position"]?.length > 2, locale);
		for (let i = 0; i < 9; i++) assert.ok(lang["questplus.client.position." + i], locale + " " + i);
	}
});
