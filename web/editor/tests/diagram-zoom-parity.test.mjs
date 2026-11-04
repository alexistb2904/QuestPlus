import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {MIN_ZOOM,MAX_ZOOM,ZOOM_STEP,clampZoom,screenPoint,worldPoint,zoomEditorAt,wheelZoomFactor,fitEditor} from '../src/core/editor-geometry.js';

const code=path=>fs.readFileSync(new URL(path,import.meta.url),'utf8');

test('video-style global map zoom has identical scale and mouse-wheel steps',()=>{
 assert.equal(MIN_ZOOM,.2);
 assert.equal(MAX_ZOOM,5);
 assert.equal(ZOOM_STEP,1.15);
 assert.ok(Math.abs(wheelZoomFactor(-100)-ZOOM_STEP)<1e-12);
 assert.ok(Math.abs(wheelZoomFactor(100)-1/ZOOM_STEP)<1e-12);
 assert.ok(Math.abs(wheelZoomFactor(-3,1)-ZOOM_STEP)<1e-12);
 assert.ok(Math.abs(wheelZoomFactor(-.5,2)-Math.pow(ZOOM_STEP,2.5))<1e-12);
 assert.equal(clampZoom(-99),MIN_ZOOM);
 assert.equal(clampZoom(99),MAX_ZOOM);
});

test('cursor anchoring works after repeated zoom-in/out and pan across the whole range',()=>{
 const size={width:960,height:620},cursor={x:137,y:285};
 let camera={x:210,y:-75,zoom:1};
 const anchor=worldPoint(cursor,camera,size);
 for(let i=0;i<50;i++){
  camera=zoomEditorAt(camera,camera.zoom*(i%3===0?1.15:1/1.15),cursor,size);
  let mapped=screenPoint(anchor,camera,size);
  assert.ok(Math.abs(mapped.x-cursor.x)<1e-8);
  assert.ok(Math.abs(mapped.y-cursor.y)<1e-8);
 }
 for(const zoom of [.2,.35,1,2.5,5]){
  camera=zoomEditorAt(camera,zoom,cursor,size);
  const mapped=screenPoint(anchor,camera,size);
  assert.ok(Math.abs(mapped.x-cursor.x)<1e-8);
  assert.ok(Math.abs(mapped.y-cursor.y)<1e-8);
  assert.equal(32*zoom,32*camera.zoom,'quest icon scales with parent');
  assert.equal(280*zoom,280*camera.zoom,'background scales with parent');
 }
});

test('Minecraft and Studio use the same numeric zoom bounds and wheel step',()=>{
 const tablet=code('../../../client/src/main/java/dev/questplus/clientmodel/QuestCamera.java');
 const screen=code('../../../client/src/main/java/dev/questplus/fabric/client/QuestScreen.java');
 const canvas=code('../src/components/TreeCanvas.tsx');
 assert.match(tablet,/MIN_ZOOM=0\.2, MAX_ZOOM=5, ZOOM_STEP=1\.15/);
 assert.match(screen,/Math\.pow\(QuestCamera\.ZOOM_STEP,vertical\)/);
 assert.match(screen,/nodeRadius\(state\.quests\.get\(id\)\)\*camera\.zoom\(\)/);
 assert.match(screen,/g\.pose\(\)\.translate\(x,y\); g\.pose\(\)\.scale\(\(float\)camera\.zoom\(\)\)/);
 assert.match(screen,/RichAnnotationRenderer\.render\(g,font,note,imagePool/);
 assert.match(canvas,/wheelZoomFactor\(e\.deltaY,e\.deltaMode\)/);
 assert.match(canvas,/\*clampZoom\(camera\.zoom\)\/2/);
 assert.match(canvas,/style=\{\{strokeWidth:Math\.max\(\.2,1\.5\*clampZoom\(camera\.zoom\)\)\}\}/);
});

test('recentering does not reset a custom zoom',()=>{
 const fitted=fitEditor([{x:-200,y:-90},{x:400,y:230}],900,500,undefined,3.5);
 assert.deepEqual(fitted,{x:100,y:70,zoom:3.5});
 assert.equal(fitEditor([],500,300,undefined,2).zoom,2);
 const hooks=code('../src/hooks/useStudio.ts');
 assert.match(hooks,/state\.camera\.zoom\s*\) as Camera/);
});
