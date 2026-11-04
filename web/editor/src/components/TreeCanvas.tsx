import React,{useEffect,useMemo,useRef,useState} from 'react';
import {gameLayout} from '../core/game-layout.js';
import {dependencyError} from '../core/quest-links.js';
import {text,annotationText,annotationRich} from '../core/project.js';
import {richToHtml} from '../core/rich-format.js';
import {MessageCircle,GitBranch,Plus,X,StickyNote,SquarePlus} from 'lucide-react';
import {screenPoint,worldPoint,snapEditor,SCALE_X,SCALE_Y} from '../core/editor-geometry.js';
import {tUI} from '../ui/ui-i18n.js';
import {ItemIcon} from './IconPicker';
import type {Quest,Category,Selection,Camera,Position} from '../types';

type Props={quests:Quest[];allQuests:Quest[];onLink:(sourceId:string,targetId:string)=>void;category?:Category;language:string;selection:Selection|null;camera:Camera;onCamera:(v:Camera)=>void;onSelect:(s:Selection)=>void;showGrid:boolean;gridSize:number;snap:boolean;tool:'select'|'pan';onPosition:(type:'quest'|'annotation',id:string,at:Position)=>void;onCreateAt?:(kind:'quest'|'annotation',at:Position)=>void;onResize?:(kind:'quest'|'annotation',id:string,size:{width?:number;height?:number;size?:number})=>void};
export function TreeCanvas({quests,allQuests,onLink,category,language,selection,camera,onCamera,onSelect,onPosition,onCreateAt,onResize,showGrid,gridSize,snap,tool}:Props){
 const ref=useRef<HTMLDivElement>(null);
 const [contextMenu,setContextMenu]=useState<{x:number;y:number;at:Position}|null>(null);
 const resize=useRef<{type:'quest'|'annotation';id:string;startX:number;startY:number;width:number;height:number;size:number;pointer:number}|null>(null);
 const [resizeGhost,setResizeGhost]=useState<{id:string;width?:number;height?:number;size?:number}|null>(null);
 const startResize=(e:React.PointerEvent,type:'quest'|'annotation',id:string,width:number,height:number,nodeSize:number)=>{
   e.preventDefault();e.stopPropagation();onSelect({type,id});
   resize.current={type,id,startX:e.clientX,startY:e.clientY,width,height,size:nodeSize,pointer:e.pointerId};
   e.currentTarget.setPointerCapture(e.pointerId);
 };
 const moveResize=(e:React.PointerEvent)=>{
   const v=resize.current;if(!v||v.pointer!==e.pointerId)return;
   e.stopPropagation();
   const dx=e.clientX-v.startX,dy=e.clientY-v.startY;
   if(v.type==='quest'){
     const sz=Math.max(24,Math.min(96,Math.round(v.size+dx/5)));
     setResizeGhost({id:v.id,size:sz});
   }else setResizeGhost({id:v.id,width:Math.max(60,Math.min(400,Math.round(v.width+dx))),height:Math.max(20,Math.min(300,Math.round(v.height+dy)))});
 };
 const endResize=(e:React.PointerEvent)=>{
   const v=resize.current;if(!v||v.pointer!==e.pointerId)return;
   e.stopPropagation();
   if(e.type!=='pointercancel'&&resizeGhost?.id===v.id)onResize?.(v.type,v.id,resizeGhost);
   resize.current=null;setResizeGhost(null);
   try{e.currentTarget.releasePointerCapture(e.pointerId)}catch{}
 };
 const handle=(type:'quest'|'annotation',id:string,width:number,height:number,sz:number)=>
   <button type="button" className="node-resize-handle" aria-label={tUI('Resize')} title={tUI('Drag to resize')}
     onPointerDown={e=>startResize(e,type,id,width,height,sz)} onPointerMove={moveResize} onPointerUp={endResize} onPointerCancel={endResize}/>;

 const pointers=useRef(new Map<number,{x:number;y:number}>());
 const gesture=useRef<any>(null);
 const dragging=useRef<any>(null);
 type Cable={from:string;point:{x:number;y:number};to:string|null;error:string;moved:boolean;pointerId?:number};
 const cable=useRef<Cable|null>(null),[connection,setConnection]=useState<Cable|null>(null);
 const showCable=(next:Cable|null)=>{cable.current=next;setConnection(next)};
 const targetAt=(x:number,y:number)=>document.elementFromPoint(x,y)?.closest<HTMLElement>('[data-node-type="quest"]')?.dataset.nodeId||null;
 const selectNode=(id:string)=>{if(cable.current){onLink(cable.current.from,id);showCable(null)}else onSelect({type:'quest',id})};
 const [size,setSize]=useState({width:720,height:520});
 useEffect(()=>{
  const el=ref.current;if(!el)return;
  const resize=()=>setSize({width:el.clientWidth,height:el.clientHeight});
  resize();const observer=new ResizeObserver(resize);observer.observe(el);return()=>observer.disconnect();
 },[]);
 const graph=useMemo(()=>{try{return gameLayout(quests)}catch(e){return {points:new Map(),edges:[]}}},[quests]);
 const pixel=(p:Position)=>screenPoint(p,camera,size);
 const world=(x:number,y:number)=>worldPoint({x,y},camera,size);
 const local=(e:React.PointerEvent)=>{const rect=ref.current!.getBoundingClientRect();return {x:e.clientX-rect.left,y:e.clientY-rect.top};};
 const zoomAt=(next:number,x:number,y:number)=>{
  const zoom=Math.max(.25,Math.min(2.5,next)),p=world(x,y);
  onCamera({zoom,x:p.x-(x-size.width/2)/(SCALE_X*zoom),y:p.y-(y-size.height/2)/(SCALE_Y*zoom)});
 };
 useEffect(()=>{const el=ref.current;if(!el)return;const onWheel=(e:WheelEvent)=>{e.preventDefault();const box=el.getBoundingClientRect();zoomAt(camera.zoom*(e.deltaY>0?.9:1.1),e.clientX-box.left,e.clientY-box.top)};el.addEventListener('wheel',onWheel,{passive:false});return()=>el.removeEventListener('wheel',onWheel)},[camera,size]);
 const onDown=(e:React.PointerEvent)=>{
  if(contextMenu)setContextMenu(null);
  if(e.button!==0&&e.pointerType!=='touch')return;
  const target=tool==='pan'?null:(e.target as Element).closest<HTMLElement>('[data-node-id]');
  const p=local(e);pointers.current.set(e.pointerId,p);
  ref.current?.setPointerCapture(e.pointerId);
  if(pointers.current.size===2){
   const pair=[...pointers.current.values()];gesture.current={d:Math.hypot(pair[1].x-pair[0].x,pair[1].y-pair[0].y)||1,zoom:camera.zoom,center:{x:(pair[0].x+pair[1].x)/2,y:(pair[0].y+pair[1].y)/2},base:camera};dragging.current=null;showCable(null);return;
  }
  const port=(e.target as Element).closest<HTMLElement>('[data-link-port]');
  if(port&&target?.dataset.nodeId){
   if(port.getAttribute('aria-disabled')==='true')return;
   showCable({from:target.dataset.nodeId,point:p,to:null,error:'',moved:false,pointerId:e.pointerId});dragging.current=null;onSelect({type:'quest',id:target.dataset.nodeId});return;
  }
  if(cable.current){showCable({...cable.current,point:p,pointerId:e.pointerId});dragging.current=null;return;}
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
   const anchor={x:(g.center.x-size.width/2)/(g.zoom*SCALE_X)+g.base.x,y:(g.center.y-size.height/2)/(g.zoom*SCALE_Y)+g.base.y};
   onCamera({zoom,x:anchor.x-(midpoint.x-size.width/2)/(zoom*SCALE_X),y:anchor.y-(midpoint.y-size.height/2)/(zoom*SCALE_Y)});return;
  }
  if(cable.current?.pointerId===e.pointerId){
   const current=cable.current,to=targetAt(e.clientX,e.clientY);
   showCable({...current,point:p,to,error:to===current.to?current.error:to?dependencyError(allQuests,current.from,to):'',moved:true});return;
  }
  const drag=dragging.current;if(!drag)return;
  const dx=p.x-drag.origin.x,dy=p.y-drag.origin.y;
  if(Math.hypot(dx,dy)>3)drag.moved=true;
  if(!drag.moved)return;
  if(drag.type==='pan')onCamera({x:drag.initial.x-dx/(camera.zoom*SCALE_X),y:drag.initial.y-dy/(camera.zoom*SCALE_Y),zoom:camera.zoom});
  else {drag.pending={x:Math.round(drag.initial.x+dx/(camera.zoom*SCALE_X)),y:Math.round(drag.initial.y+dy/(camera.zoom*SCALE_Y))};
   if(snap)drag.pending=snapEditor(drag.pending,gridSize);
   setGhost({id:drag.id,...drag.pending});}
 };
 const [ghost,setGhost]=useState<any>(null);
 const onUp=(e:React.PointerEvent)=>{
  pointers.current.delete(e.pointerId);
  try{ref.current?.releasePointerCapture(e.pointerId)}catch{}
  if(pointers.current.size<2)gesture.current=null;
  const active=cable.current;
  if(active?.pointerId===e.pointerId){
   const to=targetAt(e.clientX,e.clientY);
   if(e.type!=='pointercancel'&&to&&to!==active.from){onLink(active.from,to);showCable(null)}
   else if(e.type==='pointercancel'||active.moved)showCable(null);
   else showCable({...active,pointerId:undefined});
   return;
  }
  const drag=dragging.current;if(e.type!=='pointercancel'&&drag?.pending)onPosition(drag.type,drag.id,drag.pending);
  dragging.current=null;setGhost(null);
 };
 const icon=(q:Quest)=> <ItemIcon id={q.icon} size={28}/>;
 return <div className="viewport" id="viewport" ref={ref} aria-label={tUI('Interactive quest tree')} role="region" tabIndex={0} onPointerDown={onDown} onPointerMove={onMove} onPointerUp={onUp} onPointerCancel={onUp}
   onContextMenu={e=>{e.preventDefault();const rect=ref.current!.getBoundingClientRect();
     const x=e.clientX-rect.left,y=e.clientY-rect.top;
     setContextMenu({x:Math.min(x,Math.max(0,rect.width-185)),y:Math.min(y,Math.max(0,rect.height-115)),at:world(x,y)});
   }}
   onKeyDown={e=>{if(e.key==='Escape'){showCable(null);setContextMenu(null);dragging.current=null;return;}if((e.target as HTMLElement).closest('[data-node-id]'))return;const delta=24/camera.zoom;if(['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(e.key)){e.preventDefault();onCamera({...camera,x:camera.x+(e.key==='ArrowLeft'?-delta:e.key==='ArrowRight'?delta:0),y:camera.y+(e.key==='ArrowUp'?-delta:e.key==='ArrowDown'?delta:0)})}}}
   style={{backgroundImage:showGrid?undefined:'none',backgroundSize:(gridSize*camera.zoom)+'px '+(gridSize*camera.zoom)+'px',backgroundPosition:(-camera.x*SCALE_X*camera.zoom+size.width/2)+'px '+(-camera.y*SCALE_Y*camera.zoom+size.height/2)+'px'}}>
  {contextMenu&&<div className="graph-context-menu" role="menu" style={{left:contextMenu.x,top:contextMenu.y}} onPointerDown={e=>e.stopPropagation()}>
   <button role="menuitem" type="button" onClick={()=>{onCreateAt?.('quest',contextMenu.at);setContextMenu(null)}}><SquarePlus size={15}/>{tUI('Create quest')}</button>
   <button role="menuitem" type="button" onClick={()=>{onCreateAt?.('annotation',contextMenu.at);setContextMenu(null)}}><StickyNote size={15}/>{tUI('Create annotation')}</button>
   </div>}
  <svg className="edges" viewBox={'0 0 '+size.width+' '+size.height} aria-hidden="true">
   <defs><marker id="quest-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" className="arrow-head"/></marker></defs>
   {graph.edges.map((edge:any)=>{
    const a=graph.points.get(edge.from),b=graph.points.get(edge.to);if(!a||!b)return null;
    const p=pixel(ghost?.id===edge.from?ghost:a),q=pixel(ghost?.id===edge.to?ghost:b);
    const source=allQuests.find(z=>z.id===edge.from),target=allQuests.find(z=>z.id===edge.to);
    p.y+=33*(source?.size||32)/32;q.y-=33*(target?.size||32)/32;const mid=(p.y+q.y)/2;
    return <path markerEnd="url(#quest-arrow)" key={edge.from+'-'+edge.to} className={selection?.type==='quest'&&[edge.from,edge.to].includes(selection.id)?'selected-edge':''} d={'M'+p.x+' '+p.y+'V'+mid+'H'+q.x+'V'+q.y}/>;
   })}
   {connection&&graph.points.has(connection.from)&&(()=>{const start=pixel(graph.points.get(connection.from));start.y+=33*camera.zoom;const end=connection.point,bend=Math.max(45,Math.abs(end.y-start.y)*.55);return <path className={'connection-preview'+(connection.error?' invalid':'')} d={'M'+start.x+' '+start.y+' C'+start.x+' '+(start.y+bend)+','+end.x+' '+(end.y-bend)+','+end.x+' '+end.y}/>;})()}
  </svg>
  {connection&&<div className={'connection-hint'+(connection.error?' invalid':'')} role="status"><GitBranch size={14}/><span>{tUI(connection.error||'Drop on a quest to make it depend on this quest. Esc to cancel.')}</span><button aria-label={tUI('Cancel connection')} onPointerDown={e=>e.stopPropagation()} onClick={()=>showCable(null)}><X size={14}/></button></div>}
  <div className="nodes">
   {quests.map(q=>{const pt=graph.points.get(q.id);if(!pt)return null;
    const worldPosition=ghost?.id===q.id?ghost:pt,p=pixel(worldPosition);
    if(Math.abs(p.x)>size.width+250||Math.abs(p.y)>size.height+250)return null;
    const unavailable=q.kind==='information'&&q.acknowledgement==='read_only';
    const nodeSize=resizeGhost?.id===q.id?resizeGhost.size??q.size??32:q.size??32;
    return <div key={q.id} className={'graph-node'+(selection?.type==='quest'&&selection.id===q.id?' selected':'')+(connection?.from===q.id?' connecting':'')+(connection?.to===q.id?(connection.error?' link-invalid':' link-target'):'')}
      data-node-id={q.id} data-node-type="quest" style={{left:p.x,top:p.y,width:Math.round(162*nodeSize/32),height:Math.round(66*nodeSize/32),transform:'translate(-50%,-50%)'}}>
      <button className="node-select" type="button" aria-pressed={selection?.type==='quest'&&selection.id===q.id} onClick={()=>selectNode(q.id)}>
       <span className="node-icon"><ItemIcon id={q.icon} size={Math.round(28*nodeSize/32)}/></span><span className="node-title" title={q.id}>{text(q.title,language)}</span>
      </button>
      <button className="node-connector" data-link-port="true" aria-disabled={unavailable} aria-label={tUI('Connect quest')+' '+text(q.title,language)} title={tUI(unavailable?'A read-only quest cannot unlock another quest.':'Drag to another quest to add a prerequisite')} style={{'--port-scale':Math.max(1,.85/camera.zoom)} as React.CSSProperties}
       onClick={e=>{e.stopPropagation();if(e.detail===0&&!unavailable){onSelect({type:'quest',id:q.id});showCable({from:q.id,point:{x:p.x,y:p.y+33*nodeSize/32},to:null,error:'',moved:false})}}}><Plus size={14}/></button>
      {q.objectives?.some(o=>o.event==='npc_interact')&&<MessageCircle className="node-npc" size={13}/>}
      {selection?.type==='quest'&&selection.id===q.id&&<>{handle('quest',q.id,162,66,q.size??32)}<span className="node-position">x: {worldPosition.x} &nbsp; y: {worldPosition.y}</span><span className="selection-handles" aria-hidden="true"/></>}
     </div>;
   })}
   {(category?.annotations||[]).map(a=>{
    const pt=ghost?.id===a.id?ghost:a,p=pixel(pt);
    if(Math.abs(p.x)>size.width+240||Math.abs(p.y)>size.height+240)return null;
    const rich=annotationRich(a,language);
    const nodeWidth=resizeGhost?.id===a.id?resizeGhost.width??a.width??170:a.width??170;
    const nodeHeight=resizeGhost?.id===a.id?resizeGhost.height??a.height??100:a.height??100;
    return <div role="button" tabIndex={0} aria-label={annotationText(a,language)} onKeyDown={e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();onSelect({type:'annotation',id:a.id})}}} key={a.id} className={'annotation-node '+(a.style||'note')+(selection?.type==='annotation'&&selection.id===a.id?' selected':'')}
       data-node-id={a.id} data-node-type="annotation" style={{left:p.x,top:p.y,width:nodeWidth,height:nodeHeight,borderColor:a.color||'#c6a986',color:a.color||'#c6a986',transform:'translate(-50%,-50%)'}}>
       {rich?<span dangerouslySetInnerHTML={{__html:richToHtml(rich)}}/>:annotationText(a,language)}
       {selection?.type==='annotation'&&selection.id===a.id&&handle('annotation',a.id,a.width??170,a.height??100,32)}
      </div>;
   })}
  </div>
  {quests.length===0&&!(category?.annotations?.length)&&<div className="empty-state"><GitBranch size={36}/><h3>{tUI('Empty tree')}</h3><p>{tUI('Add a quest to begin')}</p></div>}
 </div>;
}
