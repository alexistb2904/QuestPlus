export function clampPaneSize(kind,value,workspaceWidth,otherWidth,mainHeight){
 const min=kind==='library'?180:kind==='properties'?240:155;
 const max=kind==='details'?Math.max(min,mainHeight-240):Math.max(min,Math.min(kind==='library'?600:700,workspaceWidth-otherWidth-270));
 return Math.round(Math.max(min,Math.min(max,Number.isFinite(value)?value:min)));
}
