import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {SCALE_X,SCALE_Y,screenPoint,worldPoint,fitEditor,zoomEditorAt,clampZoom} from '../src/core/editor-geometry.js';
import {gameLayout} from '../src/core/game-layout.js';

const source=path=>fs.readFileSync(new URL(path,import.meta.url),'utf8');
test('Quest Studio matches Minecraft world axes one-to-one',()=>{
 assert.equal(SCALE_X,1);
 assert.equal(SCALE_Y,1);
 const quests=[
   {id:'questplus:story/intro',layout:{x:20,y:-80},requires:null},
   {id:'questplus:story/second',layout:{x:100,y:30},requires:{quest:'questplus:story/intro'}}
 ];
 const game=gameLayout(quests);
 const view={width:900,height:450},camera={x:20,y:0,zoom:1};
 for(const q of quests){
   assert.deepEqual(game.points.get(q.id),q.layout);
   const px=screenPoint(q.layout,camera,view);
   assert.equal(px.x-view.width/2,q.layout.x-camera.x);
   assert.equal(px.y-view.height/2,q.layout.y-camera.y);
   assert.deepEqual(worldPoint(px,camera,view),q.layout);
 }
});
test('zoom scales all quest artwork, text and annotations together with their anchors',()=>{
 const screen={width:900,height:450},points=[{x:0,y:0},{x:210,y:175}];
 const left=points.map(p=>screenPoint(p,{x:0,y:0,zoom:.5},screen));
 const right=points.map(p=>screenPoint(p,{x:0,y:0,zoom:2.5},screen));
 assert.equal(right[1].x-right[0].x,5*(left[1].x-left[0].x));
 assert.equal(right[1].y-right[0].y,5*(left[1].y-left[0].y));
 const canvas=source('../src/components/TreeCanvas.tsx');
 assert.match(canvas,/width:nodeSize,height:nodeSize/);
 assert.match(canvas,/width:nodeWidth,height:nodeHeight/);
 assert.match(canvas,/transform:'translate\(-50%,-50%\) scale\('\+clampZoom\(camera\.zoom\)\+'\)'/);
 assert.match(canvas,/const scale=clampZoom\(camera\.zoom\)/);
 assert.match(canvas,/source\?\.size\|\|32\)\*clampZoom\(camera\.zoom\)\/2/);
 const css=source('../src/styles/quest-skin.css');
 assert.match(css,/their whole visual subtree is/);
});
test('Minecraft camera fit now includes authored annotation anchors',()=>{
 const screen=source('../../../client/src/main/java/dev/questplus/fabric/client/QuestScreen.java');
 const camera=source('../../../client/src/main/java/dev/questplus/clientmodel/QuestCamera.java');
 assert.match(screen,/camera\.fitBounds\(points/);
 assert.match(screen,/points\.add\(new QuestLayout\.Point\(note\.get\("x"\)/);
 assert.match(camera,/public double zoom\(\)\{return zoom;\}/);
 assert.match(screen,/int x=\(int\)camera\.screenX\(note\.get\("x"\)/);
 assert.match(screen,/RichAnnotationRenderer\.render\(g,font,note,imagePool/);
});
test('compact HUD, adaptable notice and three-pin cap are enforced',()=>{
 const hud=source('../../../client/src/main/java/dev/questplus/fabric/client/QuestHud.java');
 const toast=source('../../../client/src/main/java/dev/questplus/fabric/client/QuestToast.java');
 const settings=source('../../../client/src/main/java/dev/questplus/fabric/client/ClientSettings.java');
 assert.match(hud,/hudScale\(g\.guiWidth\(\),g\.guiHeight\(\)\)/);
 assert.match(hud,/g\.pose\(\)\.scale\(scale\)/);
 assert.match(hud,/panels\.size\(\)>=ClientSettings\.MAX_PINNED/);
 assert.match(settings,/MAX_PINNED=3/);
 assert.match(settings,/settings\.pinned=trimmed/);
 assert.doesNotMatch(toast,/g\.fill\(x,y,x\+3,y\+43,accent\)/);
 assert.match(toast,/g\.pose\(\)\.scale\(scale\)/);
});
test('camera fit preserves authored world centers',()=>{
 const s=source('../src/core/editor-geometry.js');
 assert.match(s,/const SCALE_X=1, SCALE_Y=1/);
 const fit=fitEditor([{x:0,y:0},{x:180,y:40}],800,420,{x:180,y:90});
 assert.equal(fit.zoom,1);
});

test('wheel and pinch zoom are restored and Shift-wheel pans',()=>{
 const canvas=source('../src/components/TreeCanvas.tsx');
 assert.match(canvas,/onCamera\(zoomEditorAt\(camera,/);
 assert.match(canvas,/const next=clampZoom\(g\.zoom\*/);
 assert.match(canvas,/e\.shiftKey/);
 const app=source('../src/App.tsx');
 assert.match(app,/Reset zoom/);
 assert.match(app,/Zoom in|Zoom out/);
 const game=source('../../../client/src/main/java/dev/questplus/fabric/client/QuestScreen.java');
 assert.match(game,/camera\.pan\(-horizontal\*36,0\)/);
 assert.match(game,/camera\.zoomAt\(Math\.pow\(QuestCamera\.ZOOM_STEP,vertical\),x,y\)/);
});
test('default damage behavior closes tablet with animation, and setting is opt-in',()=>{
 const flow=source('../../../client/src/main/java/dev/questplus/fabric/client/QuestClientTabletFlow.java');
 const settings=source('../../../client/src/main/java/dev/questplus/fabric/client/ClientSettings.java');
 const screen=source('../../../client/src/main/java/dev/questplus/fabric/client/QuestScreen.java');
 assert.match(settings,/stayOpenOnDamage = false/);
 assert.match(flow,/DAMAGE\.observe\(tabletActive,allow,/);
 assert.match(flow,/quest\.onClose\(\)/);
 assert.match(flow,/stage=Stage\.HELD;close\(\)/);
 assert.match(screen,/settings\.stayOpenOnDamage=!settings\.stayOpenOnDamage/);
});

test('pointer-centered zoom keeps target under the cursor and clamps zoom',()=>{
 const view={width:960,height:640},camera={x:110,y:45,zoom:1};
 const pointer={x:320,y:210};
 const target=worldPoint(pointer,camera,view);
 const next=zoomEditorAt(camera,2,pointer,view);
 const after=screenPoint(target,next,view);
 assert.ok(Math.abs(after.x-pointer.x)<1e-9);
 assert.ok(Math.abs(after.y-pointer.y)<1e-9);
 assert.equal(clampZoom(0),.2);
 assert.equal(clampZoom(999),5);
 const shifted={...next,x:next.x+40,y:next.y-20};
 assert.equal(screenPoint({x:30,y:100},shifted,view).x-screenPoint({x:30,y:100},next,view).x,-80);
});
