import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const source=(p)=>fs.readFileSync(new URL(p,import.meta.url),'utf8');
test('2D fallback reuses exact native 500x300 3D layout and transforms mouse input',()=>{
 const screen=source('../../../client/src/main/java/dev/questplus/fabric/client/QuestScreen.java');
 const viewport=source('../../../client/src/main/java/dev/questplus/clientmodel/TabletUiViewport.java');
 assert.match(screen,/width=TabletUiViewport\.WIDTH/);
 assert.match(screen,/height=TabletUiViewport\.HEIGHT/);
 assert.match(screen,/screenScale=viewport\.scale\(\)/);
 assert.match(screen,/g\.pose\(\)\.scale\(\(float\)screenScale\)/);
 assert.match(screen,/twoDX\(event\.x\(\)\)/);
 assert.match(screen,/twoDY\(event\.y\(\)\)/);
 assert.match(screen,/dx\/=screenScale;dy\/=screenScale/);
 assert.match(screen,/QuestSkin\.panel\(g,left-5,top-5,right-left\+10,bottom-top\+10/);
 assert.doesNotMatch(screen,/TabletRenderer\.draw\(g,left,top,right,bottom\)/);
 assert.match(viewport,/screenX\(double x\)/);
 assert.match(viewport,/localDelta\(double pixels\)/);
 assert.match(screen,/private Double pendingInterfaceScale/);
 assert.match(screen,/if \(pendingInterfaceScale!=null\)/);
 assert.match(screen,/pendingInterfaceScale=settings\.scale>=1\.25/);
 assert.doesNotMatch(screen,/settings\.scale=settings\.scale>=1\.25 \? 0\.75 : settings\.scale\+0\.25;\s*settings\.save\(\); init\(\);/);
});
test('3D lighting is self-illuminated by default, brightness only affects 3D atlas',()=>{
 const settings=source('../../../client/src/main/java/dev/questplus/fabric/client/ClientSettings.java');
 const surface=source('../../../client/src/main/java/dev/questplus/fabric/client/TabletSurfaceRenderer.java');
 const shader=source('../../../client/src/main/java/dev/questplus/fabric/client/mixin/TabletFaceCaptureMixin.java');
 const screen=source('../../../client/src/main/java/dev/questplus/fabric/client/QuestScreen.java');
 assert.match(settings,/boolean screenEmissive = true/);
 assert.match(settings,/double screenBrightness = 1\.0/);
 assert.match(settings,/!json\.has\("screenEmissive"\)/);
 assert.match(settings,/!json\.has\("screenBrightness"\)/);
 assert.match(shader,/screenEmissive/);
 assert.match(shader,/RenderTypes\.entityTranslucentEmissive/);
 assert.match(shader,/RenderTypes\.entityTranslucent\(TabletSurfaceRenderer\.TEXTURE\)/);
 assert.match(surface,/if\(brightness<\.999\)/);
 assert.match(surface,/graphics\.nextStratum\(\)/);
 assert.match(screen,/settings\.screenBrightness=\(steps\+1\)%11\/10\.0/);
});
test('all 3D screen controls have localized labels',()=>{
 for(const locale of ['en_us','fr_fr','es_es','zh_cn']){
  const lang=JSON.parse(source('../../../client/src/main/resources/assets/questplus/lang/'+locale+'.json'));
  assert.ok(lang['questplus.client.screen_emissive'],locale);
  assert.ok(lang['questplus.client.screen_brightness'],locale);
 }
});
