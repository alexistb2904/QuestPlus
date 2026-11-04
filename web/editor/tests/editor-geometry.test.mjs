import test from 'node:test';
import assert from 'node:assert/strict';
import {screenPoint,worldPoint,fitEditor,snapEditor,SCALE_X,SCALE_Y} from '../src/core/editor-geometry.js';

test('labelled editor cards preserve Minecraft coordinates through pan and zoom',()=>{
 const size={width:920,height:480};
 for(const camera of [{x:0,y:0,zoom:1},{x:-34,y:150,zoom:.25},{x:300,y:-60,zoom:2.5}]){
  for(const point of [{x:0,y:0},{x:-100,y:37},{x:9000,y:-1700}]){
   const restored=worldPoint(screenPoint(point,camera,size),camera,size);
   assert.ok(Math.abs(restored.x-point.x)<1e-9);
   assert.ok(Math.abs(restored.y-point.y)<1e-9);
  }
 }
});
test('fit includes complete quest cards and grid snapping keeps exported integer coordinates',()=>{
 const points=[{x:-64,y:0},{x:64,y:72}],size={width:800,height:450};
 const camera=fitEditor(points,size.width,size.height);
 for(const point of points){const at=screenPoint(point,camera,size);assert.ok(at.x>=81*camera.zoom&&at.x<=size.width-81*camera.zoom);assert.ok(at.y>=33*camera.zoom&&at.y<=size.height-33*camera.zoom);}
 for(const point of [{x:17,y:51},{x:-29,y:-43}]){
  const snapped=snapEditor(point,32);
  assert.ok(Number.isInteger(snapped.x)&&Number.isInteger(snapped.y));
  for(const [key,scale] of [['x',SCALE_X],['y',SCALE_Y]])assert.ok(Math.abs(snapped[key]*scale-Math.round(point[key]*scale/32)*32)<=scale/2);
 }
 assert.deepEqual(fitEditor([],800,450),{x:0,y:0,zoom:1});
});
