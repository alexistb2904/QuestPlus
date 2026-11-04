/** Exact world-space node geometry and automatic placement from QuestLayout.java. */
export const NODE_SIZE=32,HORIZONTAL_GAP=64,VERTICAL_GAP=72;
export function questRefs(node,out=[]){
  if(!node||typeof node!=='object')return out;
  if(typeof node.quest==='string')out.push(node.quest);
  for(const part of node.and||[])questRefs(part,out);
  for(const part of node.or||[])questRefs(part,out);
  if(node.not)questRefs(node.not,out);
  return out;
}
export function gameLayout(quests){
  const byId=new Map([...quests].sort((a,b)=>a.id.localeCompare(b.id)).map(q=>[q.id,q]));
  const parents=new Map(),children=new Map(),pending=new Map(),ranks=new Map(),edges=[];
  for(const q of byId.values()){
    const refs=[...new Set(questRefs(q.requires))].filter(id=>byId.has(id)).sort();
    parents.set(q.id,refs);pending.set(q.id,refs.length);
    for(const id of refs){if(!children.has(id))children.set(id,[]);children.get(id).push(q.id);edges.push({from:id,to:q.id});}
  }
  const queue=[...pending].filter(([,n])=>n===0).map(([id])=>id).sort(),layers=new Map();
  let visited=0;
  while(queue.length){
    const id=queue.shift();visited++;const rank=ranks.get(id)||0;
    if(!layers.has(rank))layers.set(rank,[]);layers.get(rank).push(id);
    for(const child of children.get(id)||[]){
      ranks.set(child,Math.max(ranks.get(child)||0,rank+1));
      const remaining=pending.get(child)-1;pending.set(child,remaining);
      if(!remaining){queue.push(child);queue.sort();}
    }
  }
  if(visited!==byId.size)throw Error('Quest dependencies contain a cycle');
  const points=new Map(),cells=new Map();
  const key=(x,y)=>Math.floor(x/HORIZONTAL_GAP)+','+Math.floor(y/HORIZONTAL_GAP);
  function add(id,p){points.set(id,p);const k=key(p.x,p.y);if(!cells.has(k))cells.set(k,[]);cells.get(k).push(id);}
  function overlaps(p){
    const x=Math.floor(p.x/HORIZONTAL_GAP),y=Math.floor(p.y/HORIZONTAL_GAP);
    for(let dx=-1;dx<=1;dx++)for(let dy=-1;dy<=1;dy++){
      for(const id of cells.get((x+dx)+','+(y+dy))||[]){
        const other=points.get(id);
        if(Math.abs(other.x-p.x)<NODE_SIZE && Math.abs(other.y-p.y)<NODE_SIZE)return true;
      }
    }
    return false;
  }
  for(const q of byId.values()){
    if(q.layout && Number.isFinite(q.layout.x) && Number.isFinite(q.layout.y))
      add(q.id,{x:q.layout.x,y:q.layout.y});
  }
  const order=new Map();
  for(const rank of [...layers.keys()].sort((a,b)=>a-b)){
    const ids=layers.get(rank);
    ids.sort((a,b)=>{
      const avg=id=>{const ps=parents.get(id);return ps.length?ps.reduce((n,p)=>n+(order.get(p)||0),0)/ps.length:0;};
      return avg(a)-avg(b)||a.localeCompare(b);
    });
    ids.forEach((id,i)=>order.set(id,i));
    ids.forEach((id,i)=>{
      if(points.has(id))return;
      const p={x:(i-(ids.length-1)/2)*HORIZONTAL_GAP,y:rank*VERTICAL_GAP};
      while(overlaps(p))p.x+=HORIZONTAL_GAP;
      add(id,p);
    });
  }
  return {points,edges};
}
export function gameFit(points,width,height,padding=24){
  const list=[...points];if(!list.length)return {x:0,y:0,zoom:1};
  const xs=list.map(p=>p.x),ys=list.map(p=>p.y),minX=Math.min(...xs),maxX=Math.max(...xs),minY=Math.min(...ys),maxY=Math.max(...ys);
  const halfX=(maxX-minX)/2+NODE_SIZE/2,halfY=(maxY-minY)/2+NODE_SIZE/2;
  const zoom=Math.max(.25,Math.min(2.5,Math.min(Math.max(1,width-2*padding)/2/halfX,Math.max(1,height-2*padding)/2/halfY)));
  return {x:(minX+maxX)/2,y:(minY+maxY)/2,zoom};
}
