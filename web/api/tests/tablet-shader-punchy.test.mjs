import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../..');
const read=p=>readFileSync(path.join(root,p),'utf8');

test('Iris presence alone does not activate shader behavior: actual pack state is checked',()=>{
 const shader=read('client/src/main/java/dev/questplus/fabric/client/TabletShaderCompat.java');
 assert.match(shader,/isModLoaded\("iris"\)/);
 assert.match(shader,/isShaderPackInUse/);
 assert.match(shader,/public static boolean shadersActive\(\)/);
 assert.match(shader,/return shaderPackActive\(\);/);
 assert.match(shader,/return userEnabled && !shaderActive;/);
 const options=read('client/src/main/java/dev/questplus/fabric/client/ClientSettings.java');
 assert.match(options,/screenEmissive = true/);
 assert.match(options,/blur = true/);
 assert.doesNotMatch(options,/shaderSafeMode/);
});

test('self illumination ON/OFF uses shader matte material and no shader uses classic materials',()=>{
 const shader=read('client/src/main/java/dev/questplus/fabric/client/TabletShaderCompat.java');
 const face=read('client/src/main/java/dev/questplus/fabric/client/mixin/TabletFaceCaptureMixin.java');
 const atlas=read('client/src/main/java/dev/questplus/fabric/client/TabletSurfaceRenderer.java');
 assert.match(shader,/return shaderActive && selfIlluminated;/);
 assert.match(shader,/Matte|matteIllumination/);
 assert.match(face,/var screenType=shaders \? RenderTypes\.entityCutout\(TabletSurfaceRenderer\.TEXTURE\)/);
 assert.match(face,/settings\.screenEmissive\s*\? RenderTypes\.entityTranslucentEmissive/);
 assert.match(face,/RenderTypes\.entityTranslucent\(TabletSurfaceRenderer\.TEXTURE\)/);
 assert.match(face,/LightCoordsUtil\.FULL_BRIGHT/);
 assert.match(face,/TabletShaderCompat\.illuminatedScreen\(\)/);
 assert.match(atlas,/TabletShaderCompat\.illuminatedScreen\(\)/);
 assert.match(shader,/Math\.clamp\(requested,0,matteIllumination\?0\.78:1\)/);
});

test('active shaders always bypass global menu blur regardless of self illumination',()=>{
 const game=read('client/src/main/java/dev/questplus/fabric/client/mixin/TabletGameRendererMixin.java');
 const options=read('client/src/main/java/dev/questplus/fabric/client/mixin/TabletBlurMixin.java');
 const screen=read('client/src/main/java/dev/questplus/fabric/client/QuestScreen.java');
 assert.match(game,/TabletShaderCompat\.backgroundBlurEnabled\(\)/);
 assert.match(options,/TabletShaderCompat\.backgroundBlurEnabled\(\)/);
 assert.match(screen,/TabletShaderCompat\.backgroundBlurEnabled\(\)/);
 assert.match(screen,/b\.active=false/);
 assert.match(screen,/blur_shader_disabled/);
 assert.doesNotMatch(screen,/button\("shader_safe_mode"/);
});

test('Bliss: static body texture is separate from the dynamic screen atlas under shaders',()=>{
 const face=read('client/src/main/java/dev/questplus/fabric/client/mixin/TabletFaceCaptureMixin.java');
 assert.match(face,/TABLET_BODY=Identifier\.fromNamespaceAndPath/);
 assert.match(face,/RenderTypes\.entityCutout\(TABLET_BODY\)/);
 assert.match(face,/questplus\$pass==1 && TabletShaderCompat\.shadersActive\(\)/);
 assert.match(face,/questplus\$pass==2 && face==FpGeoModel\.FACE_NORTH/);
 assert.match(face,/TextureRenderer\.TEXTURE|TabletSurfaceRenderer\.TEXTURE/);
});

test('Esc and stalled animation cannot indefinitely hide hotbar and first-person state',()=>{
 const flow=read('client/src/main/java/dev/questplus/fabric/client/QuestClientTabletFlow.java');
 const renderer=read('client/src/main/java/dev/questplus/fabric/client/TabletSurfaceRenderer.java');
 const hud=read('client/src/main/java/dev/questplus/fabric/client/mixin/TabletHudMixin.java');
 assert.match(renderer,/nativeHandsActive\(\)\s*&& Minecraft\.getInstance\(\)\.gui\.screen\(\) instanceof QuestScreen/);
 assert.match(flow,/CLOSE_WATCHDOG_NANOS=2_500_000_000L/);
 assert.match(flow,/OPEN_WATCHDOG_NANOS=3_500_000_000L/);
 assert.match(flow,/System\.nanoTime\(\)-closingSince>CLOSE_WATCHDOG_NANOS/);
 assert.match(flow,/stop\(\);reset\(\);return;/);
 assert.match(flow,/closingSince=0/);
 assert.match(hud,/TabletSurfaceRenderer\.hidesHud\(\)/);
});

test('Punchy remains disabled while native tablet hands own animation',()=>{
 const bridge=read('client/src/main/java/dev/questplus/fabric/client/PunchyTabletCompat.java');
 const flow=read('client/src/main/java/dev/questplus/fabric/client/QuestClientTabletFlow.java');
 assert.match(bridge,/getMethod\("disable",String\.class,BooleanSupplier\.class\)/);
 assert.match(bridge,/QuestClientTabletFlow\.nativeHandsActive\(\)/);
 const opening=flow.slice(flow.indexOf('private static void open(Minecraft client)'),flow.indexOf('static void close()'));
 assert.ok(opening.indexOf('nativeHands=true;stage=Stage.OPENING')<opening.indexOf('FirstPersonPalBridge.play("questplus.open")'));
});

test('removed independent shader-safe toggle and translated automatic blur explanation in four locales',()=>{
 for(const locale of ['en_us','fr_fr','es_es','zh_cn']){
  const json=JSON.parse(read('client/src/main/resources/assets/questplus/lang/'+locale+'.json'));
  assert.equal(typeof json['questplus.client.blur_shader_disabled'],'string',locale);
  assert.ok(json['questplus.client.blur_shader_disabled'].length>10,locale);
  assert.ok(!('questplus.client.shader_safe_mode' in json),locale);
 }
});
