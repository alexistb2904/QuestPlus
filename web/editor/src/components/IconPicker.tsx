import React,{useEffect,useMemo,useState} from 'react';
import {vanillaCatalog,iconUrl,blockIconUrl,loadVanillaIcons} from '../../minecraft-icons.js';
import {tUI} from '../../ui-i18n.js';

export function ItemIcon({id,version='26.3',size=30}:{id?:string;version?:string;size?:number}){
 const [fail,setFail]=useState(0);
 useEffect(()=>setFail(0),[id,version]);
 const url=fail===0?iconUrl(id||'',version):fail===1?blockIconUrl(id||'',version):'';
 return <span className="mc-icon" title={id||''} style={{width:size,height:size,flexShrink:0}}>
  {url?<img alt="" loading="lazy" src={url} onError={()=>setFail(f=>f+1)}/>:<span aria-hidden>◇</span>}
 </span>;
}
export function IconPicker({onPick,onClose}:{onPick:(value:string)=>void;onClose:()=>void}){
 const [search,setSearch]=useState(''),[filter,setFilter]=useState('all'),[limit,setLimit]=useState(96);
 useEffect(()=>{loadVanillaIcons().catch(()=>{});},[]);
 const matches=useMemo(()=>vanillaCatalog().filter((item:any)=>(filter==='all'||item.kind===filter||item.kind==='both')&&(item.id+' '+item.label).toLowerCase().includes(search.toLowerCase())),[search,filter]);
 return <div className="react-overlay" role="presentation" onMouseDown={e=>{if(e.target===e.currentTarget)onClose()}}>
  <section className="react-dialog icon-dialog" role="dialog" aria-modal="true" aria-label={tUI('Choose an icon')}>
   <div className="react-dialog-heading"><h2>{tUI('Choose an icon')}</h2><button className="icon-button" onClick={onClose} aria-label="Close">×</button></div>
   <div className="picker-filters"><input autoFocus value={search} onChange={e=>{setSearch(e.target.value);setLimit(96)}} placeholder={tUI('Search every item and block…')}/>
    <select value={filter} onChange={e=>{setFilter(e.target.value);setLimit(96)}} aria-label="Icon type"><option value="all">{tUI('All')}</option><option value="item">{tUI('Items')}</option><option value="block">{tUI('Blocks')}</option></select>
   </div>
   <div className="picker-count">{matches.length} {tUI('results')}</div>
   <div className="picker-grid">{matches.slice(0,limit).map((item:any)=><button key={item.id} className="picker-item" title={item.id} onClick={()=>onPick(item.id)}><ItemIcon id={item.id}/><span>{item.label}</span></button>)}</div>
   {matches.length>limit&&<button className="button secondary" onClick={()=>setLimit(l=>l+120)}>{tUI('Load more')}</button>}
   <p className="picker-help">Modded items: enter a namespaced ID (e.g. create:brass_ingot) directly.</p>
  </section>
 </div>;
}
