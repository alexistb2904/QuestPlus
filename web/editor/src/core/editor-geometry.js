// QuestPlus diagram mode: a pan-only world. Anchors, spacing and media sizes
// are always one authored world unit to one screen pixel, at every viewport.
export const SCALE_X=1, SCALE_Y=1;
export function screenPoint(point,camera,size){
 return {x:point.x-camera.x+size.width/2,y:point.y-camera.y+size.height/2};
}
export function worldPoint(point,camera,size){
 return {x:point.x-size.width/2+camera.x,y:point.y-size.height/2+camera.y};
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
