import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = (p) => fs.readFileSync(new URL(p, import.meta.url), "utf8");
test("2D fallback reuses exact native 500x300 3D layout and transforms mouse input", () => {
	const screen = source("../../../mod/client/src/main/java/dev/questplus/fabric/client/QuestScreen.java");
	const viewport = source("../../../mod/client/src/main/java/dev/questplus/clientmodel/TabletUiViewport.java");
	assert.match(screen, /width\s*=\s*TabletUiViewport\.WIDTH/);
	assert.match(screen, /height\s*=\s*TabletUiViewport\.HEIGHT/);
	assert.match(screen, /screenScale\s*=\s*viewport\.scale\(\)/);
	assert.match(screen, /g\.pose\(\)\.scale\(\(float\)\s*screenScale\)/);
	assert.match(screen, /twoDX\(event\.x\(\)\)/);
	assert.match(screen, /twoDY\(event\.y\(\)\)/);
	assert.match(screen, /dx\s*\/=\s*screenScale;\s*dy\s*\/=\s*screenScale/);
	assert.match(screen, /QuestSkin\.panel\(g,\s*left\s*-\s*5,\s*top\s*-\s*5,\s*right\s*-\s*left\s*\+\s*10,\s*bottom\s*-\s*top\s*\+\s*10/);
	assert.doesNotMatch(screen, /TabletRenderer\.draw\(g,\s*left,\s*top,\s*right,\s*bottom\)/);
	assert.match(viewport, /screenX\(double x\)/);
	assert.match(viewport, /localDelta\(double pixels\)/);
	assert.match(screen, /private Double pendingInterfaceScale/);
	assert.match(screen, /if\s*\(pendingInterfaceScale\s*!=\s*null\)/);
	assert.match(screen, /pendingInterfaceScale\s*=\s*settings\.scale\s*>=\s*1\.25/);
	assert.doesNotMatch(screen, /settings\.scale\s*=\s*settings\.scale\s*>=\s*1\.25\s*\?\s*0\.75\s*:\s*settings\.scale\s*\+\s*0\.25;\s*settings\.save\(\);\s*init\(\);/);
});
test("3D lighting is self-illuminated by default, brightness only affects 3D atlas", () => {
	const settings = source("../../../mod/client/src/main/java/dev/questplus/fabric/client/ClientSettings.java");
	const surface = source("../../../mod/client/src/main/java/dev/questplus/fabric/client/TabletSurfaceRenderer.java");
	const shader = source("../../../mod/client/src/main/java/dev/questplus/fabric/client/TabletNativeFirstPersonRenderer.java");
	const screen = source("../../../mod/client/src/main/java/dev/questplus/fabric/client/QuestScreen.java");
	assert.match(settings, /boolean screenEmissive = true/);
	assert.match(settings, /double screenBrightness = 1\.0/);
	assert.match(settings, /!json\.has\("screenEmissive"\)/);
	assert.match(settings, /!json\.has\("screenBrightness"\)/);
	assert.match(shader, /screenEmissive/);
	assert.match(shader, /RenderTypes\.eyes\(TabletSurfaceRenderer\.TEXTURE\)/);
	assert.match(shader, /RenderTypes\.entityTranslucent\(TabletSurfaceRenderer\.TEXTURE\)/);
	assert.match(surface, /if\s*\(brightness\s*<\s*\.999\)/);
	assert.match(surface, /graphics\.nextStratum\(\)/);
	assert.match(screen, /settings\.screenBrightness\s*=\s*\(steps\s*\+\s*1\)\s*%\s*11\s*\/\s*10\.0/);
});
test("all 3D screen controls have localized labels", () => {
	for (const locale of ["en_us", "fr_fr", "es_es", "zh_cn"]) {
		const lang = JSON.parse(source("../../../mod/client/src/main/resources/assets/questplus/lang/" + locale + ".json"));
		assert.ok(lang["questplus.client.screen_emissive"], locale);
		assert.ok(lang["questplus.client.screen_brightness"], locale);
	}
});
