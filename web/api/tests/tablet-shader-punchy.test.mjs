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

test("only tablet hands skip the second Iris pass before FP PAL submits geometry", () => {
	const shader = read("mod/client/src/main/java/dev/questplus/fabric/client/TabletShaderCompat.java");
	const hands = read("mod/client/src/main/java/dev/questplus/fabric/client/mixin/TabletNativeArmsMixin.java");
	const face = read("mod/client/src/main/java/dev/questplus/fabric/client/mixin/TabletFaceCaptureMixin.java");
	assert.match(shader, /getMethod\("isRenderingSolid"\)/);
	assert.match(shader, /HANDS\s*==\s*null\s*\|\|\s*!shadersActive\(\)/);
	assert.match(hands, /!TabletShaderCompat.translucentHandPass\(\)/);
	assert.match(face, /TabletNativeArms.active\(\) && TabletShaderCompat.translucentHandPass\(\)/);
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

test("self illumination ON/OFF uses shader matte material and no shader uses classic materials", () => {
	const shader = read("mod/client/src/main/java/dev/questplus/fabric/client/TabletShaderCompat.java");
	const face = read("mod/client/src/main/java/dev/questplus/fabric/client/mixin/TabletFaceCaptureMixin.java");
	const atlas = read("mod/client/src/main/java/dev/questplus/fabric/client/TabletSurfaceRenderer.java");
	assert.match(shader, /return shaderActive && selfIlluminated;/);
	assert.match(shader, /Matte|matteIllumination/);
	assert.match(face, /var screenType\s*=\s*shaders\s*\?\s*RenderTypes\.entityCutout\(TabletSurfaceRenderer\.TEXTURE\)/);
	assert.match(face, /settings\.screenEmissive\s*\? RenderTypes\.entityTranslucentEmissive/);
	assert.match(face, /RenderTypes\.entityTranslucent\(TabletSurfaceRenderer\.TEXTURE\)/);
	assert.match(face, /LightCoordsUtil\.FULL_BRIGHT/);
	assert.match(face, /TabletShaderCompat\.illuminatedScreen\(\)/);
	assert.match(atlas, /TabletShaderCompat\.illuminatedScreen\(\)/);
	assert.match(shader, /Math\.clamp\(requested,\s*0,\s*matteIllumination\s*\?\s*0\.78\s*:\s*1\)/);
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

test("Bliss: static body texture is separate from the dynamic screen atlas under shaders", () => {
	const face = read("mod/client/src/main/java/dev/questplus/fabric/client/mixin/TabletFaceCaptureMixin.java");
	assert.match(face, /TABLET_BODY\s*=\s*Identifier\.fromNamespaceAndPath/);
	assert.match(face, /RenderTypes\.entityCutout\(TABLET_BODY\)/);
	assert.match(face, /questplus\$pass\s*==\s*1\s*&&\s*TabletShaderCompat\.shadersActive\(\)/);
	assert.match(face, /questplus\$pass\s*==\s*2\s*&&\s*face\s*==\s*FpGeoModel\.FACE_NORTH/);
	assert.match(face, /TextureRenderer\.TEXTURE|TabletSurfaceRenderer\.TEXTURE/);
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
	const play = opening.indexOf("FirstPersonPalBridge.play(openingClip)");
	const idle = opening.indexOf('FirstPersonPalBridge.play("questplus.idle")');
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
