import React,{useEffect,useMemo,useRef,useState} from 'react';
import {gameLayout} from '../../game-layout.js';
import {text} from '../../project.js';
import {richToHtml} from '../../rich-format.js';
import {ItemIcon} from './IconPicker';
import type {Quest,Category,Selection,Camera,Position} from '../types';

type Props={quests:Quest[];category?:Category;language:string;selection:Selection|null;camera:Camera;onCamera:(v:Camera)=>void;onSelect:(s:Selection)=>void;onPosition:(type:'quest'|'annotation',id:string,at:Position)=>void};
export function TreeCanvas({quests,category,language,selection,camera,onCamera,onSelect,onPosition}:Props){
 const ref=useRef<HTMLDivElement>(null);
 const pointers=useRef(new Map<number,{x:number;y:number}>());
 const gesture=useRef<any>(null);
 const dragging=useRef<any>(null);
 const [size,setSize]=useState({width:720,height:520});
 useEffect(()=>{
  const el=ref.current;if(!el)return;
  const resize=()=>setSize({width:el.clientWidth,height:el.clientHeight});
  resize();const observer=new ResizeObserver(resize);observer.observe(el);return()=>observer.disconnect();
 },[]);
 const graph=useMemo(()=>{try{return gameLayout(quests)}catch(e){return {points:new Map(),edges:[]}}},[quests]);
 const pixel=(p:Position)=>({x:(p.x-camera.x)*camera.zoom+size.width/2,y:(p.y-camera.y)*camera.zoom+size.height/2});
 const world=(x:number,y:number)=>({x:(x-size.width/2)/camera.zoom+camera.x,y:(y-size.height/2)/camera.zoom+camera.y});
 const local=(e:React.PointerEvent)=>{const rect=ref.current!.getBoundingClientRect();return {x:e.clientX-rect.left,y:e.clientY-rect.top};};
 const zoomAt=(next:number,x:number,y:number)=>{
  const zoom=Math.max(.25,Math.min(2.5,next)),p=world(x,y);
  onCamera({zoom,x:p.x-(x-size.width/2)/zoom,y:p.y-(y-size.height/2)/zoom});
 };
 const onDown=(e:React.PointerEvent)=>{
  if(e.button!==0&&e.pointerType!=='touch')return;
  const target=(e.target as HTMLElement).closest<HTMLElement>('[data-node-id]');
  const p=local(e);pointers.current.set(e.pointerId,p);
  ref.current?.setPointerCapture(e.pointerId);
  if(pointers.current.size===2){
   const pair=[...pointers.current.values()];gesture.current={d:Math.hypot(pair[1].x-pair[0].x,pair[1].y-pair[0].y)||1,zoom:camera.zoom,center:{x:(pair[0].x+pair[1].x)/2,y:(pair[0].y+pair[1].y)/2},base:camera};dragging.current=null;return;
  }
  const type=target?.dataset.nodeType as ('quest'|'annotation'|undefined),id=target?.dataset.nodeId;
  if(type&&id){
   const source=type==='quest'?graph.points.get(id):category?.annotations?.find(a=>a.id===id);
   if(source){dragging.current={type,id,origin:p,initial:{x:source.x,y:source.y},moved:false};onSelect({type,id});}
  } else dragging.current={type:'pan',origin:p,initial:{x:camera.x,y:camera.y},moved:false};
 };
 const onMove=(e:React.PointerEvent)=>{
  const p=local(e);if(!pointers.current.has(e.pointerId))return;pointers.current.set(e.pointerId,p);
  if(pointers.current.size===2&&gesture.current){
   const pair=[...pointers.current.values()],g=gesture.current;
   const distance=Math.hypot(pair[1].x-pair[0].x,pair[1].y-pair[0].y)||1;
   const midpoint={x:(pair[0].x+pair[1].x)/2,y:(pair[0].y+pair[1].y)/2};
   const zoom=Math.max(.25,Math.min(2.5,g.zoom*distance/g.d));
   const anchor={x:(g.center.x-size.width/2)/g.zoom+g.base.x,y:(g.center.y-size.height/2)/g.zoom+g.base.y};
   onCamera({zoom,x:anchor.x-(midpoint.x-size.width/2)/zoom,y:anchor.y-(midpoint.y-size.height/2)/zoom});return;
  }
  const drag=dragging.current;if(!drag)return;
  const dx=p.x-drag.origin.x,dy=p.y-drag.origin.y;
  if(Math.hypot(dx,dy)>3)drag.moved=true;
  if(!drag.moved)return;
  if(drag.type==='pan')onCamera({x:drag.initial.x-dx/camera.zoom,y:drag.initial.y-dy/camera.zoom,zoom:camera.zoom});
  else {drag.pending={x:Math.round(drag.initial.x+dx/camera.zoom),y:Math.round(drag.initial.y+dy/camera.zoom)};
   setGhost({id:drag.id,...drag.pending});}
 };
 const [ghost,setGhost]=useState<any>(null);
 const onUp=(e:React.PointerEvent)=>{
  pointers.current.delete(e.pointerId);
  try{ref.current?.releasePointerCapture(e.pointerId)}catch{}
  if(pointers.current.size<2)gesture.current=null;
  const drag=dragging.current;if(drag?.pending)onPosition(drag.type,drag.id,drag.pending);
  dragging.current=null;setGhost(null);
 };
 const icon=(q:Quest)=> <ItemIcon id={q.icon} size={28}/>;
 return <div className="viewport" id="viewport" ref={ref} aria-label="Interactive quest tree" tabIndex={0} onPointerDown={onDown} onPointerMove={onMove} onPointerUp={onUp} onPointerCancel={onUp}
   onWheel={e=>{e.preventDefault();const box=ref.current!.getBoundingClientRect();zoomAt(camera.zoom*(e.deltaY>0?.9:1.1),e.clientX-box.left,e.clientY-box.top)}}
   style={{backgroundSize:(32*camera.zoom)+'px '+(32*camera.zoom)+'px',backgroundPosition:(-camera.x*camera.zoom+size.width/2)+'px '+(-camera.y*camera.zoom+size.height/2)+'px'}}>
  <svg className="edges" viewBox={'0 0 '+size.width+' '+size.height} aria-hidden="true">
   {graph.edges.map((edge:any)=>{
    const a=graph.points.get(edge.from),b=graph.points.get(edge.to);if(!a||!b)return null;
    const p=pixel({x:a.x,y:a.y+16}),q=pixel({x:b.x,y:b.y-16}),mid=(p.y+q.y)/2;
    return <path key={edge.from+'-'+edge.to} className={selection?.type==='quest'&&[edge.from,edge.to].includes(selection.id)?'selected-edge':''} d={'M'+p.x+' '+p.y+'V'+mid+'H'+q.x+'V'+q.y}/>;
   })}
  </svg>
  <div className="nodes">
   {quests.map(q=>{const pt=graph.points.get(q.id);if(!pt)return null;
    const worldPosition=ghost?.id===q.id?ghost:pt,p=pixel(worldPosition);
    if(Math.abs(p.x)>size.width+250||Math.abs(p.y)>size.height+250)return null;
    return <div key={q.id} className={'graph-node'+(selection?.type==='quest'&&selection.id===q.id?' selected':'')}
      data-node-id={q.id} data-node-type="quest" style={{left:p.x,top:p.y,transform:'translate(-50%,-50%) scale('+camera.zoom+')'}}>
      <div className="node-icon">{icon(q)}</div><div className="node-title" title={q.id}>{text(q.title,language)}</div>
     </div>;
   })}
   {(category?.annotations||[]).map(a=>{
    const pt=ghost?.id===a.id?ghost:a,p=pixel(pt);
    if(Math.abs(p.x)>size.width+240||Math.abs(p.y)>size.height+240)return null;
    const rich=a.rich?.[language]||a.rich?.en_us;
    return <div key={a.id} className={'annotation-node '+(a.style||'note')+(selection?.type==='annotation'&&selection.id===a.id?' selected':'')}
       data-node-id={a.id} data-node-type="annotation" style={{left:p.x,top:p.y,borderColor:a.color||'#c6a986',color:a.color||'#c6a986',transform:'translate(-50%,-50%) scale('+camera.zoom+')'}}>
       {rich?<span dangerouslySetInnerHTML={{__html:richToHtml(rich)}}/>:text(a.text,language)}
      </div>;
   })}
  </div>
  {quests.length===0&&!(category?.annotations?.length)&&<div className="empty-state"><h3>Empty tree</h3><p>Add a quest to begin</p></div>}
 </div>;
}
