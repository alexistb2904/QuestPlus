import React,{useMemo,useState} from 'react';
import {Dialog} from './Dialog';
import {tUI} from '../ui/ui-i18n.js';
import {availableEnchantments} from '../data/vanilla-enchantments-26.3.js';

/** The official 26.3 vanilla registry is searchable. Modded IDs remain editable inline. */
export function EnchantmentPicker({taken,onPick,onClose,replaceId=''}:{
 taken:string[];replaceId?:string;onPick:(id:string)=>void;onClose:()=>void
}){
 const [search,setSearch]=useState('');
 const [group,setGroup]=useState('All');
 const available=useMemo(()=>availableEnchantments(taken,replaceId),[taken,replaceId]);
 const matches=useMemo(()=>available.filter(row=>{
  const query=search.trim().toLowerCase();
  return (group==='All'||row.group===group) && (!query||(`${row.id} ${row.name}`).toLowerCase().includes(query));
 }),[available,search,group]);
 return <Dialog title={tUI('Choose enchantment')} onClose={onClose} className="icon-dialog">
  <div className="picker-filters">
   <input autoFocus aria-label={tUI('Search enchantments…')} placeholder={tUI('Search enchantments…')}
    value={search} onChange={e=>setSearch(e.target.value)}/>
   <select aria-label={tUI('Enchantment group')} value={group} onChange={e=>setGroup(e.target.value)}>
    {['All','Armor','Combat','Tools','Ranged','Fishing','Universal'].map(key=><option key={key} value={key}>{tUI(key)}</option>)}
   </select>
  </div>
  <div className="picker-count">{matches.length} {tUI('results')}</div>
  {matches.length?
   <div className="enchantment-picker-list">
    {matches.map(row=><button type="button" key={row.id} className="enchantment-picker-row" onClick={()=>onPick(row.id)}
     title={row.id}><span><strong>{row.name}</strong><small>{row.id}</small></span>
     <span className="enchantment-level-hint">{tUI('Max level')} {row.maxLevel}</span></button>)}
   </div>:
   <p className="picker-help">{tUI('No enchantments match this search.')}</p>}
  <p className="picker-help">{tUI('Modded enchantments: enter a namespaced ID in the field.')}</p>
 </Dialog>;
}
