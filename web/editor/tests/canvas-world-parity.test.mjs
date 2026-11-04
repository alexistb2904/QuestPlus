import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {SCALE_X,SCALE_Y,screenPoint,worldPoint,fitEditor} from '../src/core/editor-geometry.js';
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
test('wheel navigation never changes sizes or the distance between anchors',()=>{
 const screen={width:900,height:450},points=[{x:0,y:0},{x:210,y:175}];
 const left=points.map(p=>screenPoint(p,{x:0,y:0,zoom:.25},screen));
 const right=points.map(p=>screenPoint(p,{x:0,y:0,zoom:2.5},screen));
 assert.equal(right[1].x-right[0].x,left[1].x-left[0].x);
 assert.equal(right[1].y-right[0].y,left[1].y-left[0].y);
 const canvas=source('../src/components/TreeCanvas.tsx');
 assert.match(canvas,/width:nodeSize,height:nodeSize/);
 assert.match(canvas,/width:nodeWidth,height:nodeHeight/);
 assert.doesNotMatch(canvas,/width:nodeWidth\*camera\.zoom|height:nodeHeight\*camera\.zoom/);
 const css=source('../src/styles/quest-skin.css');
 assert.match(css,/WYSIWYG diagram nodes use the same fixed screen-pixel measurements/);
});
test('Minecraft camera fit now includes authored annotation anchors',()=>{
 const screen=source('../../../src/main/java/dev/questplus/fabric/client/QuestScreen.java');
 const camera=source('../../../src/main/java/dev/questplus/clientmodel/QuestCamera.java');
 assert.match(screen,/camera\.fitBounds\(points/);
 assert.match(screen,/points\.add\(new QuestLayout\.Point\(note\.get\("x"\)/);
 assert.match(camera,/public double zoom\(\) \{ return 1; \}/);
 assert.match(screen,/int x=\(int\)camera\.screenX\(note\.get\("x"\)/);
 assert.match(screen,/RichAnnotationRenderer\.render\(g,font,note,imagePool/);
});
test('compact HUD, adaptable notice and three-pin cap are enforced',()=>{
 const hud=source('../../../src/main/java/dev/questplus/fabric/client/QuestHud.java');
 const toast=source('../../../src/main/java/dev/questplus/fabric/client/QuestToast.java');
 const settings=source('../../../src/main/java/dev/questplus/fabric/client/ClientSettings.java');
 assert.match(hud,/hudScale\(g\.guiWidth\(\),g\.guiHeight\(\)\)/);
 assert.match(hud,/g\.pose\(\)\.scale\(scale\)/);
 assert.match(hud,/panels\.size\(\)>=ClientSettings\.MAX_PINNED/);
 assert.match(settings,/MAX_PINNED=3/);
 assert.match(settings,/settings\.pinned=trimmed/);
 assert.doesNotMatch(toast,/g\.fill\(x,y,x\+3,y\+43,accent\)/);
 assert.match(toast,/g\.pose\(\)\.scale\(scale\)/);
});
test('camera fit includes constant-size note padding',()=>{
 const s=source('../src/core/editor-geometry.js');
 assert.match(s,/const SCALE_X=1, SCALE_Y=1/);
 const fit=fitEditor([{x:0,y:0},{x:180,y:40}],800,420,{x:180,y:90});
 assert.equal(fit.zoom,1);
});

test('wheel and touch gestures only pan both axes, and no zoom controls remain',()=>{
 const canvas=source('../src/components/TreeCanvas.tsx');
 assert.match(canvas,/onCamera\(\{x:camera\.x\+dx,y:camera\.y\+dy,zoom:1\}\)/);
 assert.match(canvas,/onCamera\(\{zoom:1,x:g\.base\.x\+g\.center\.x-midpoint\.x/);
 assert.doesNotMatch(canvas,/zoomAt\(/);
 const app=source('../src/App.tsx');
 assert.match(app,/Fixed-size canvas/);
 assert.doesNotMatch(app,/Zoom in|Zoom out|Reset zoom/);
 const game=source('../../../src/main/java/dev/questplus/fabric/client/QuestScreen.java');
 assert.match(game,/camera\.pan\(-horizontal\*36,vertical\*36\)/);
 assert.doesNotMatch(game,/camera\.zoomAt\(/);
});
test('default damage behavior closes tablet with animation, and setting is opt-in',()=>{
 const flow=source('../../../src/main/java/dev/questplus/fabric/client/QuestClientTabletFlow.java');
 const settings=source('../../../src/main/java/dev/questplus/fabric/client/ClientSettings.java');
 const screen=source('../../../src/main/java/dev/questplus/fabric/client/QuestScreen.java');
 assert.match(settings,/stayOpenOnDamage = false/);
 assert.match(flow,/DAMAGE\.observe\(tabletActive,allow,/);
 assert.match(flow,/quest\.onClose\(\)/);
 assert.match(flow,/stage=Stage\.HELD;close\(\)/);
 assert.match(screen,/settings\.stayOpenOnDamage=!settings\.stayOpenOnDamage/);
});
