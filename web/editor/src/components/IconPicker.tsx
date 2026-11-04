import React,{useEffect,useMemo,useState} from 'react';
import {Package} from 'lucide-react';
import {Dialog} from './Dialog';
import {vanillaCatalog,iconUrl,blockIconUrl,blockExists,inVanillaRegistry,loadVanillaIcons} from '../ui/minecraft-icons.js';
import {tUI} from '../ui/ui-i18n.js';
import {BIOMES,STRUCTURES,ENTITIES} from '../core/objective-targets.js';

export function ItemIcon({id,version='26.3',size=30}:{id?:string;version?:string;size?:number}){
 const [fail,setFail]=useState(0);
 useEffect(()=>setFail(0),[id,version]);
 const blockFirst=blockExists(id);
 const url=!inVanillaRegistry(id)?'':fail===0?(blockFirst?blockIconUrl(id||'',version):iconUrl(id||'',version)):fail===1?(blockFirst?iconUrl(id||'',version):blockIconUrl(id||'',version)):'';
 return <span className="mc-icon" title={id||''} style={{width:size,height:size,flexShrink:0}}>
  {url?<img alt="" loading="lazy" src={url} onError={()=>setFail(f=>f+1)}/>:<Package size={Math.min(size,24)} aria-hidden="true"/>}
 </span>;
}
export function IconPicker({onPick,onClose}:{onPick:(value:string)=>void;onClose:()=>void}){
 const [search,setSearch]=useState(''),[filter,setFilter]=useState('all'),[limit,setLimit]=useState(96);
 useEffect(()=>{loadVanillaIcons().catch(()=>{});},[]);
 const matches=useMemo(()=>vanillaCatalog().filter((item:any)=>(filter==='all'||item.kind===filter||item.kind==='both')&&(item.id+' '+item.label).toLowerCase().includes(search.toLowerCase())),[search,filter]);
 return <Dialog title={tUI('Choose an icon')} onClose={onClose} className="icon-dialog">
   <div className="picker-filters"><input autoFocus aria-label={tUI('Search every item and block…')} value={search} onChange={e=>{setSearch(e.target.value);setLimit(96)}} placeholder={tUI('Search every item and block…')}/>
    <select value={filter} onChange={e=>{setFilter(e.target.value);setLimit(96)}} aria-label={tUI("Icon type")}><option value="all">{tUI('All')}</option><option value="item">{tUI('Items')}</option><option value="block">{tUI('Blocks')}</option></select>
   </div>
   <div className="picker-count">{matches.length} {tUI('results')}</div>
   <div className="picker-grid">{matches.slice(0,limit).map((item:any)=><button key={item.id} className="picker-item" title={item.id} onClick={()=>onPick(item.id)}><ItemIcon id={item.id}/><span>{item.label}</span></button>)}</div>
   {matches.length>limit&&<button className="button secondary" onClick={()=>setLimit(l=>l+120)}>{tUI('Load more')}</button>}
   <p className="picker-help">{tUI('Modded items: enter a namespaced ID (e.g. create:brass_ingot) directly.')}</p>
 </Dialog>;
}

/** A searchable vanilla catalogue with an unrestricted namespaced-ID input for modded content. */
export function TargetPicker({kind,onPick,onClose}:{kind:'block'|'item'|'entity'|'biome'|'structure';onPick:(value:string)=>void;onClose:()=>void}){
 const [search,setSearch]=useState(''),[limit,setLimit]=useState(96),[custom,setCustom]=useState('');
 const catalogue=useMemo(()=>{
   if(kind==='biome')return BIOMES;
   if(kind==='structure')return STRUCTURES;
   if(kind==='entity')return ENTITIES;
   return vanillaCatalog().filter((item:any)=>kind==='block'?item.kind==='block'||item.kind==='both':item.kind==='item'||item.kind==='both').map((item:any)=>item.id);
 },[kind]);
 const matches=useMemo(()=>catalogue.filter(id=>id.toLowerCase().includes(search.trim().toLowerCase())),[catalogue,search]);
 const valid=/^#?[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(custom.trim());
 return <Dialog title={tUI('Choose target')} onClose={onClose} className="icon-dialog">
   <div className="picker-filters"><input autoFocus value={search} onChange={e=>{setSearch(e.target.value);setLimit(96)}} placeholder={tUI('Search targets…')} aria-label={tUI('Search targets…')}/></div>
   <div className="picker-count">{matches.length} {tUI('results')}</div>
   <div className="picker-grid">{matches.slice(0,limit).map(id=><button type="button" className="picker-item" key={id} title={id} onClick={()=>onPick(id)}>{(kind==='block'||kind==='item')&&<ItemIcon id={id}/>}<span>{id.replace('minecraft:','').replace(/_/g,' ')}</span></button>)}</div>
   {matches.length>limit&&<button className="button secondary" onClick={()=>setLimit(n=>n+120)}>{tUI('Load more')}</button>}
   <div className="target-custom"><input aria-label={tUI('Modded target ID')} placeholder="modid:custom_target" value={custom} spellCheck={false} onChange={e=>setCustom(e.target.value)}/><button className="button primary" type="button" disabled={!valid} onClick={()=>onPick(custom.trim())}>{tUI('Use ID')}</button></div>
 </Dialog>;
}
