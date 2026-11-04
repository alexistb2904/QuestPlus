// Presentation stretches the tiny in-game slots into labelled cards; saved coordinates stay unchanged.
export const SCALE_X=2.8, SCALE_Y=1.5;
export function screenPoint(point,camera,size){
 return {x:(point.x-camera.x)*SCALE_X*camera.zoom+size.width/2,y:(point.y-camera.y)*SCALE_Y*camera.zoom+size.height/2};
}
export function worldPoint(point,camera,size){
 return {x:(point.x-size.width/2)/(SCALE_X*camera.zoom)+camera.x,y:(point.y-size.height/2)/(SCALE_Y*camera.zoom)+camera.y};
}
export function fitEditor(points,width,height,padding={x:81,y:33}){
 const list=[...points];if(!list.length)return {x:0,y:0,zoom:1};
 const xs=list.map(p=>p.x),ys=list.map(p=>p.y),minX=Math.min(...xs),maxX=Math.max(...xs),minY=Math.min(...ys),maxY=Math.max(...ys);
 // Node artwork is screen-sized, so padding must NOT shrink with zoom.
 const horizontal=Math.max(0,width-padding.x*2-28),vertical=Math.max(0,height-padding.y*2-28);
 const dx=(maxX-minX)*SCALE_X,dy=(maxY-minY)*SCALE_Y;
 return {x:(minX+maxX)/2,y:(minY+maxY)/2,zoom:Math.max(.25,Math.min(1,dx?horizontal/dx:1,dy?vertical/dy:1))};
}

export function snapEditor(point,gridSize){
 return {x:Math.round(Math.round(point.x*SCALE_X/gridSize)*gridSize/SCALE_X),y:Math.round(Math.round(point.y*SCALE_Y/gridSize)*gridSize/SCALE_Y)};
}
