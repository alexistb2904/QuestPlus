import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../..');
const read=p=>readFileSync(path.join(root,p),'utf8');

test('Iris shader detection is optional and vanilla behavior is preserved without shader packs',()=>{
 const iris=read('client/src/main/java/dev/questplus/fabric/client/TabletShaderCompat.java');
 assert.match(iris,/isModLoaded\("iris"\)/);
 assert.match(iris,/isShaderPackInUse/);
 assert.match(iris,/return false;/);
 assert.match(iris,/Math\.clamp\(requested,0,shaderSafe\?0\.78:1\)/);
 const options=read('client/src/main/java/dev/questplus/fabric/client/ClientSettings.java');
 assert.match(options,/shaderSafeMode = true/);
 assert.match(options,/!json\.has\("shaderSafeMode"\)/);
});
test('shader-safe tablet does not double blur the whole world or send GUI through HDR emissive materials',()=>{
 const game=read('client/src/main/java/dev/questplus/fabric/client/mixin/TabletGameRendererMixin.java');
 const blur=read('client/src/main/java/dev/questplus/fabric/client/mixin/TabletBlurMixin.java');
 const face=read('client/src/main/java/dev/questplus/fabric/client/mixin/TabletFaceCaptureMixin.java');
 const atlas=read('client/src/main/java/dev/questplus/fabric/client/TabletSurfaceRenderer.java');
 const idle=read('client/src/main/java/dev/questplus/fabric/client/TabletIdleEffects.java');
 assert.match(game,/!TabletShaderCompat\.safeRendering\(\)/);
 assert.match(blur,/TabletShaderCompat\.safeRendering\(\)/);
 assert.match(blur,/setReturnValue\(0\)/);
 assert.match(face,/RenderTypes\.entityCutout\(TabletSurfaceRenderer\.TEXTURE\)/);
 assert.match(face,/LightCoordsUtil\.FULL_BRIGHT/);
 assert.match(face,/@ModifyVariable/);
 assert.match(atlas,/TabletShaderCompat\.screenBrightness/);
 assert.match(idle,/TabletShaderCompat\.safeRendering\(\)/);
 assert.match(face,/questplus\$pass==2/);
});
test('Punchy official API temporarily steps aside whenever FP PAL is presenting QuestPlus tablet',()=>{
 const bridge=read('client/src/main/java/dev/questplus/fabric/client/PunchyTabletCompat.java');
 const flow=read('client/src/main/java/dev/questplus/fabric/client/QuestClientTabletFlow.java');
 const entry=read('client/src/main/java/dev/questplus/fabric/client/QuestPlusClient.java');
 assert.match(bridge,/isModLoaded\("punchy"\)/);
 assert.match(bridge,/punchy\.api\.PunchyApi/);
 assert.match(bridge,/getMethod\("disable",String\.class,BooleanSupplier\.class\)/);
 assert.match(bridge,/tabletOwnsHands/);
 assert.match(bridge,/QuestClientTabletFlow\.nativeHandsActive\(\)/);
 assert.match(entry,/PunchyTabletCompat\.register\(\)/);
 const opening=flow.slice(flow.indexOf('private static void open(Minecraft client)'),flow.indexOf('static void close()'));
 assert.ok(opening.indexOf('nativeHands=true;stage=Stage.OPENING;')<opening.indexOf('FirstPersonPalBridge.play("questplus.open")'),
  'Punchy must stand down BEFORE the first FP PAL frame');
 assert.match(opening,/nativeHands=false;stage=Stage.CLOSED/);
});
test('shader-safe toggle translated into the four primary QuestPlus locales',()=>{
 const locales=['en_us','fr_fr','es_es','zh_cn'];
 for(const locale of locales){
  const json=JSON.parse(read('client/src/main/resources/assets/questplus/lang/'+locale+'.json'));
  assert.equal(typeof json['questplus.client.shader_safe_mode'],'string',locale);
  assert.ok(json['questplus.client.shader_safe_mode'].length>7,locale);
 }
 const screen=read('client/src/main/java/dev/questplus/fabric/client/QuestScreen.java');
 assert.match(screen,/button\("shader_safe_mode"/);
 assert.match(screen,/settings\.shaderSafeMode=!settings\.shaderSafeMode/);
});
