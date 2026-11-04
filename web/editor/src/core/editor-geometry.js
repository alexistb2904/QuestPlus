// QuestPlus world-space camera: the wheel scales distances BETWEEN anchors.
// Each quest icon, annotation, and embedded image is drawn at its authored size
// in screen pixels, independently of camera.zoom.
export const SCALE_X=1, SCALE_Y=1;
export const MIN_ZOOM=0.35, MAX_ZOOM=2.5;
export function clampZoom(value){
 return Math.max(MIN_ZOOM,Math.min(MAX_ZOOM,Number.isFinite(value)?value:1));
}
export function screenPoint(point,camera,size){
 const z=clampZoom(camera.zoom);
 return {x:(point.x-camera.x)*z+size.width/2,y:(point.y-camera.y)*z+size.height/2};
}
export function worldPoint(point,camera,size){
 const z=clampZoom(camera.zoom);
 return {x:(point.x-size.width/2)/z+camera.x,y:(point.y-size.height/2)/z+camera.y};
}
// Zoom around the pointer, preserving the exact world point under the cursor.
export function zoomEditorAt(camera,nextZoom,pointer,size){
 const next=clampZoom(nextZoom),world=worldPoint(pointer,camera,size);
 return {x:world.x-(pointer.x-size.width/2)/next,y:world.y-(pointer.y-size.height/2)/next,zoom:next};
}
export function fitEditor(points,width,height){
 const list=[...points];
 if(!list.length)return {x:0,y:0,zoom:1};
 const xs=list.map(p=>p.x),ys=list.map(p=>p.y);
 return {x:(Math.min(...xs)+Math.max(...xs))/2,y:(Math.min(...ys)+Math.max(...ys))/2,zoom:1};
}
export function snapEditor(point,gridSize){
 return {x:Math.round(point.x/gridSize)*gridSize,y:Math.round(point.y/gridSize)*gridSize};
}
