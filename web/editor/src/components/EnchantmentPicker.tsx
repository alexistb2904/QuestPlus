import React,{useMemo,useState} from 'react';
import {Dialog} from './Dialog';
import {tUI} from '../ui/ui-i18n.js';
import {availableEnchantments} from '../data/vanilla-enchantments-26.3.js';
import {hubTargets} from '../core/hub-registry.js';
import {resourceMatcher} from '../core/resource-search.js';

/** The official 26.3 vanilla registry is searchable. Modded IDs remain editable inline. */
export function EnchantmentPicker({taken,onPick,onClose,replaceId=''}:{
 taken:string[];replaceId?:string;onPick:(id:string)=>void;onClose:()=>void
}){
 const [search,setSearch]=useState('');
 const [group,setGroup]=useState('All');
 const available=useMemo(()=>{
  const vanilla=availableEnchantments(taken,replaceId);
  const known=new Set(vanilla.map(x=>x.id));
  const selected=new Set(taken.filter(id=>id!==replaceId));
  const modded=hubTargets('enchantment').filter(id=>!id.startsWith('minecraft:')&&!selected.has(id)&&!known.has(id))
    .map(id=>({id,name:id.split(':')[1].replace(/_/g,' '),maxLevel:0,group:'Modded'}));
  return [...vanilla,...modded];
 },[taken,replaceId]);
 const matches=useMemo(()=>{const match=resourceMatcher(search);return available.filter(row=>
  (group==='All'||row.group===group) && match(row.id,row.name)
 )},[available,search,group]);
 return <Dialog title={tUI('Choose enchantment')} onClose={onClose} className="icon-dialog">
  <div className="picker-filters">
   <input autoFocus aria-label={tUI('Search enchantments…')} placeholder={tUI('Search enchantments…')}
    value={search} onChange={e=>setSearch(e.target.value)}/>
   <select aria-label={tUI('Enchantment group')} value={group} onChange={e=>setGroup(e.target.value)}>
    {['All','Armor','Combat','Tools','Ranged','Fishing','Universal','Modded'].map(key=><option key={key} value={key}>{tUI(key)}</option>)}
   </select>
  </div>
  <div className="picker-count">{matches.length} {tUI('results')}</div>
  {matches.length?
   <div className="enchantment-picker-list">
    {matches.map(row=><button type="button" key={row.id} className="enchantment-picker-row" onClick={()=>onPick(row.id)}
     title={row.id}><span><strong>{row.name}</strong><small>{row.id}</small></span>
     <span className="enchantment-level-hint">{row.maxLevel? tUI('Max level')+' '+row.maxLevel:tUI('Server registry')}</span></button>)}
   </div>:
   <p className="picker-help">{tUI('No enchantments match this search.')}</p>}
  <p className="picker-help">{tUI('Modded enchantments: enter a namespaced ID in the field.')}</p>
 </Dialog>;
}
