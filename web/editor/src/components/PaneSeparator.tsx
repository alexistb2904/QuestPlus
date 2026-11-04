import React,{useRef} from 'react';
import {tUI} from '../ui/ui-i18n.js';

type Kind='library'|'properties'|'details';
const names:Record<Kind,string>={library:'Resize library',properties:'Resize properties',details:'Resize diagram and objectives'};
export function PaneSeparator({kind,value,onResize}:{kind:Kind;value:()=>number;onResize:(size:number)=>void}){
 const drag=useRef<{pointer:number;start:number;size:number}|null>(null);
 const vertical=kind!=='details',direction=kind==='library'?1:-1;
 return <div role="separator" tabIndex={0} aria-orientation={vertical?'vertical':'horizontal'}
  aria-label={tUI(names[kind])} aria-controls={kind==='library'?undefined:kind==='properties'?'properties-panel':'graph-panel'}
  aria-valuemin={kind==='library'?180:kind==='properties'?240:155} aria-valuemax={kind==='library'?600:700} aria-valuenow={Math.round(value())}
  className={'pane-separator '+(vertical?'pane-separator-vertical':'pane-separator-horizontal')}
  onPointerDown={e=>{if(e.button!==0)return;e.preventDefault();drag.current={pointer:e.pointerId,start:vertical?e.clientX:e.clientY,size:value()};e.currentTarget.setPointerCapture(e.pointerId)}}
  onPointerMove={e=>{const d=drag.current;if(!d||d.pointer!==e.pointerId)return;onResize(d.size+direction*((vertical?e.clientX:e.clientY)-d.start))}}
  onPointerUp={e=>{if(drag.current?.pointer===e.pointerId)drag.current=null;try{e.currentTarget.releasePointerCapture(e.pointerId)}catch{}}}
  onPointerCancel={()=>{drag.current=null}}
  onKeyDown={e=>{const delta=vertical?(e.key==='ArrowLeft'?-1:e.key==='ArrowRight'?1:0):(e.key==='ArrowUp'?-1:e.key==='ArrowDown'?1:0);if(delta){e.preventDefault();onResize(value()+direction*delta*(e.shiftKey?50:10))}}}/>
}
