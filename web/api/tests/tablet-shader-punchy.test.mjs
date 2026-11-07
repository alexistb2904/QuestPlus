import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");
const read = (p) => readFileSync(path.join(root, p), "utf8");

test("First Person Model yields through its activation API without changing player settings", () => {
	const compat = read("mod/client/src/main/java/dev/questplus/fabric/client/FirstPersonTabletCompat.java");
	assert.match(compat, /registerPlayerHandler/);
	assert.match(compat, /preventFirstperson" -> QuestClientTabletFlow.nativeHandsActive\(\)/);
	assert.doesNotMatch(compat, /setEnabled/);
});

test("native first-person renderer claims only QuestPlus tablet frames", () => {
	const hands = read("mod/client/src/main/java/dev/questplus/fabric/client/mixin/TabletNativeFirstPersonMixin.java");
	const renderer = read("mod/client/src/main/java/dev/questplus/fabric/client/TabletNativeFirstPersonRenderer.java");
	assert.match(hands, /FirstPersonHandsAndItemsRenderer\.class/);
	assert.match(hands, /submitHandsWithItems/);
	assert.match(hands, /TabletNativeFirstPersonRenderer\.submit/);
	assert.match(hands, /ci\.cancel\(\)/);
	assert.match(renderer, /TabletNativeArms\.active\(\)/);
	assert.match(renderer, /RenderTypes\.entityCutout/);
});

test("Iris presence alone does not activate shader behavior: actual pack state is checked", () => {
	const shader = read("mod/client/src/main/java/dev/questplus/fabric/client/TabletShaderCompat.java");
	assert.match(shader, /isModLoaded\("iris"\)/);
	assert.match(shader, /isShaderPackInUse/);
	assert.match(shader, /public static boolean shadersActive\(\)/);
	assert.match(shader, /return shaderPackActive\(\);/);
	assert.match(shader, /return userEnabled && !shaderActive;/);
	const options = read("mod/client/src/main/java/dev/questplus/fabric/client/ClientSettings.java");
	assert.match(options, /screenEmissive = true/);
	assert.match(options, /blur = true/);
	assert.doesNotMatch(options, /shaderSafeMode/);
});

test("native screen materials preserve matte and emissive shader behavior", () => {
	const shader = read("mod/client/src/main/java/dev/questplus/fabric/client/TabletShaderCompat.java");
	const renderer = read("mod/client/src/main/java/dev/questplus/fabric/client/TabletNativeFirstPersonRenderer.java");
	const geometry = read("mod/client/src/main/java/dev/questplus/fabric/client/TabletNativeGeometry.java");
	const atlas = read("mod/client/src/main/java/dev/questplus/fabric/client/TabletSurfaceRenderer.java");
	assert.match(shader, /return shaderActive && selfIlluminated;/);
	assert.match(renderer, /RenderTypes\.entityCutout\(TabletSurfaceRenderer\.TEXTURE\)/);
	assert.match(renderer, /RenderTypes\.entityTranslucentEmissive\(TabletSurfaceRenderer\.TEXTURE\)/);
	assert.match(renderer, /RenderTypes\.entityTranslucent\(TabletSurfaceRenderer\.TEXTURE\)/);
	assert.match(geometry, /LightCoordsUtil\.FULL_BRIGHT/);
	assert.match(atlas, /TabletShaderCompat\.illuminatedScreen\(\)/);
});

test("active shaders always bypass global menu blur regardless of self illumination", () => {
	const game = read("mod/client/src/main/java/dev/questplus/fabric/client/mixin/TabletGameRendererMixin.java");
	const options = read("mod/client/src/main/java/dev/questplus/fabric/client/mixin/TabletBlurMixin.java");
	const screen = read("mod/client/src/main/java/dev/questplus/fabric/client/QuestScreen.java");
	assert.match(game, /TabletShaderCompat\.backgroundBlurEnabled\(\)/);
	assert.match(options, /TabletShaderCompat\.backgroundBlurEnabled\(\)/);
	assert.match(screen, /TabletShaderCompat\.backgroundBlurEnabled\(\)/);
	assert.match(screen, /b\.active\s*=\s*false/);
	assert.match(screen, /blur_shader_disabled/);
	assert.doesNotMatch(screen, /button\("shader_safe_mode"/);
});

test("tablet body and interactive screen use separate native render passes", () => {
	const renderer = read("mod/client/src/main/java/dev/questplus/fabric/client/TabletNativeFirstPersonRenderer.java");
	const geo = read("mod/client/src/main/java/dev/questplus/fabric/client/TabletNativeGeometry.java");
	assert.match(renderer, /surface\s*\?\s*TabletRenderer\.texture\(\)\s*:\s*BODY/);
	assert.match(renderer, /TabletNativeGeometry\.Pass\.BODY/);
	assert.match(renderer, /TabletNativeGeometry\.Pass\.SCREEN/);
	assert.match(geo, /TabletFaceAnchor\.capture\(pose\)/);
	assert.match(geo, /TabletSurfaceRenderer\.SCREEN_X/);
});

test("Esc and stalled animation cannot indefinitely hide hotbar and first-person state", () => {
	const flow = read("mod/client/src/main/java/dev/questplus/fabric/client/QuestClientTabletFlow.java");
	const renderer = read("mod/client/src/main/java/dev/questplus/fabric/client/TabletSurfaceRenderer.java");
	const hud = read("mod/client/src/main/java/dev/questplus/fabric/client/mixin/TabletHudMixin.java");
	assert.match(renderer, /nativeHandsActive\(\)\s*&&\s*screen\(\)\s*!=\s*null/);
	assert.match(renderer, /return QuestRecipeViewer\.tabletScreen\(\)/);
	assert.match(flow, /CLOSE_WATCHDOG_NANOS\s*=\s*2_500_000_000L/);
	assert.match(flow, /OPEN_WATCHDOG_NANOS\s*=\s*3_500_000_000L/);
	assert.match(flow, /System\.nanoTime\(\)\s*-\s*closingSince\s*>\s*CLOSE_WATCHDOG_NANOS/);
	assert.match(flow, /stop\(\);\s*reset\(\);\s*return;/);
	assert.match(flow, /closingSince\s*=\s*0/);
	assert.match(hud, /TabletSurfaceRenderer\.hidesHud\(\)/);
});

test("Punchy remains disabled while native tablet hands own animation", () => {
	const bridge = read("mod/client/src/main/java/dev/questplus/fabric/client/PunchyTabletCompat.java");
	const flow = read("mod/client/src/main/java/dev/questplus/fabric/client/QuestClientTabletFlow.java");
	assert.match(bridge, /getMethod\("disable",\s*String\.class,\s*BooleanSupplier\.class\)/);
	assert.match(bridge, /QuestClientTabletFlow\.nativeHandsActive\(\)/);
	const opening = flow.slice(flow.indexOf("private static void open(Minecraft client)"), flow.indexOf("static void close()"));
	const claimHands = opening.search(/nativeHands\s*=\s*true;/);
	const openingStage = opening.search(/stage\s*=\s*Stage\.OPENING;/);
	const play = opening.indexOf("TabletNativeAnimation.play(openingClip)");
	const idle = opening.indexOf('TabletNativeAnimation.play("questplus.idle")');
	assert.ok(claimHands >= 0 && idle > claimHands, "hands are claimed before idle starts when transitions are disabled");
	assert.ok(openingStage > claimHands && play > openingStage, "hands and opening stage are set before the opening animation starts");
});

test("removed independent shader-safe toggle and translated automatic blur explanation in four locales", () => {
	for (const locale of ["en_us", "fr_fr", "es_es", "zh_cn"]) {
		const json = JSON.parse(read("mod/client/src/main/resources/assets/questplus/lang/" + locale + ".json"));
		assert.equal(typeof json["questplus.client.blur_shader_disabled"], "string", locale);
		assert.ok(json["questplus.client.blur_shader_disabled"].length > 10, locale);
		assert.ok(!("questplus.client.shader_safe_mode" in json), locale);
	}
});

test("the release builds use only native runtime components", () => {
	const build = read("mod/build.gradle");
	const mixins = read("mod/client/src/main/resources/questplus.client.mixins.json");
	assert.doesNotMatch(build, /include ['"](?:maven\.modrinth|curse\.maven|de\.marhali|teamreborn)/);
	assert.match(build, /verifyNativeRenderer/);
	assert.doesNotMatch(mixins, /TabletFpInterpolationMixin|TabletFaceCaptureMixin|TabletNativeArmsMixin/);
});
