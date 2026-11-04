import React,{useEffect,useState} from 'react';
import {ItemIcon,TargetPicker} from './IconPicker';
import {EnchantmentPicker} from './EnchantmentPicker';
import {tUI} from '../ui/ui-i18n.js';

export type GearItem={slot:string;item:string;enchantments?:Record<string,number>};
export const ARMOR_SLOTS=['head','chest','legs','feet'] as const;
export const GEAR_SLOTS=['inventory','mainhand','offhand',...ARMOR_SLOTS];
export const MATERIALS=['leather','copper','chainmail','iron','golden','diamond','netherite'];
const enchantId=/^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
export const getGear=(conditions:any):GearItem[]=>{
 const node=conditions?.['questplus:gear'] ? conditions : conditions?.and?.find((c:any)=>c?.['questplus:gear']);
 return node?.['questplus:gear']?.items||[];
};
export const updateGear=(conditions:any,items:GearItem[])=>{
 const gear={'questplus:gear':{items}};
 if(!conditions||conditions['questplus:gear'])return gear;
 if(Array.isArray(conditions.and)){
  const remaining=conditions.and.filter((c:any)=>!c?.['questplus:gear']);
  return {and:[...remaining,gear]};
 }
 return {and:[conditions,gear]};
};
export const armorPreset=(material:string):GearItem[]=>{
 const suffixes=['helmet','chestplate','leggings','boots'];
 const base=material==='gold'?'golden':material;
 return ARMOR_SLOTS.map((slot,i)=>({slot,item:'minecraft:'+base+'_'+suffixes[i],enchantments:{}}));
};

function EnchantmentId({id,taken,onRename,onBrowse}:{id:string;taken:Record<string,number>;onRename:(next:string)=>void;onBrowse:()=>void}){
 const [draft,setDraft]=useState(id);
 useEffect(()=>setDraft(id),[id]);
 const invalid=!enchantId.test(draft.trim())||(draft.trim()!==id&&Object.hasOwn(taken,draft.trim()));
 const commit=()=>{if(!invalid&&draft.trim()!==id)onRename(draft.trim())};
 return <div className="field gear-enchantment-id"><label htmlFor={'enchantment-'+id}>{tUI('Enchantment ID')}</label>
  <div className="gear-enchantment-id-entry">
   <input id={'enchantment-'+id} spellCheck={false} value={draft} aria-label={tUI('Enchantment ID')} aria-invalid={invalid}
    title={invalid?tUI('Invalid or duplicate enchantment ID'):undefined}
    onChange={e=>setDraft(e.target.value)} onBlur={commit} onKeyDown={e=>{if(e.key==='Enter'){e.preventDefault();commit();e.currentTarget.blur()}}}/>
   <button type="button" className="button secondary" onClick={onBrowse}>{tUI('Browse')}</button>
  </div>
 </div>;
}

/** Dedicated, mod-friendly equipment editor: no raw JSON editing is required. */
export function GearEditor({conditions,onChange}:{conditions:any;onChange:(next:any)=>void}){
 const [pick,setPick]=useState<number|null>(null);
 const [pickEnchant,setPickEnchant]=useState<{item:number;previous?:string}|null>(null);
 const items=getGear(conditions);
 const change=(next:GearItem[])=>onChange(updateGear(conditions,next));
 const edit=(index:number,partial:Partial<GearItem>)=>change(items.map((item,i)=>i===index?{...item,...partial}:item));
 const enchant=(index:number,id:string,value:number|null)=>{
  const enchantments={...(items[index].enchantments||{})};
  if(value===null)delete enchantments[id];else enchantments[id]=Math.max(1,Math.min(10,Math.round(value)||1));
  edit(index,{enchantments});
 };
 return <div className="gear-editor">
  <div className="gear-toolbar">
   <label className="field"><span>{tUI('Armor preset')}</span>
    <select defaultValue="" aria-label={tUI('Armor preset')} onChange={e=>{
     if(e.target.value)change(armorPreset(e.target.value));e.target.value='';
    }}><option value="">{tUI('Select set…')}</option>{MATERIALS.map(m=><option key={m} value={m}>{tUI(m)}</option>)}</select></label>
   <button type="button" className="button secondary" onClick={()=>change([...items,{slot:'mainhand',item:'minecraft:diamond_pickaxe',enchantments:{}}])}>{tUI('Add equipment')}</button>
  </div>
  <div className="gear-list">{items.map((gear,i)=><section className="gear-item-editor" key={i}>
   <div className="gear-item-heading">
    <ItemIcon id={gear.item} size={28}/>
    <div className="gear-item-name"><strong>{tUI(gear.slot)}</strong><span title={gear.item}>{String(gear.item||'').split(':').pop()?.replace(/_/g,' ')}</span></div>
    <button type="button" className="tiny danger" aria-label={tUI('Remove equipment')+' '+(i+1)} onClick={()=>change(items.filter((_,n)=>n!==i))}>×</button>
   </div>
   <div className="gear-fields">
    <label className="field"><span>{tUI('Equipment slot')}</span>
     <select value={gear.slot} aria-label={tUI('Equipment slot')+' '+(i+1)} onChange={e=>edit(i,{slot:e.target.value})}>
      {GEAR_SLOTS.map(slot=><option key={slot} value={slot}>{tUI(slot)}</option>)}
     </select></label>
    <label className="field gear-item-id"><span>{tUI('Equipment item')}</span>
     <span className="gear-item-entry"><input value={gear.item} spellCheck={false} aria-label={tUI('Equipment item')+' '+(i+1)}
      onChange={e=>edit(i,{item:e.target.value})} placeholder="modid:custom_armor"/>
      <button type="button" className="button secondary" onClick={()=>setPick(i)}>{tUI('Browse')}</button></span></label>
   </div>
   {Object.entries(gear.enchantments||{}).map(([id,level])=><div className="gear-enchantment" key={id}>
    <EnchantmentId id={id} taken={gear.enchantments||{}} onBrowse={()=>setPickEnchant({item:i,previous:id})} onRename={newId=>{
      const next={...(gear.enchantments||{})};delete next[id];next[newId]=level;edit(i,{enchantments:next});
    }}/>
    <label className="field gear-enchantment-level"><span>{tUI('Minimum level')}</span>
     <input type="number" min={1} max={10} value={level} onChange={e=>enchant(i,id,Number(e.target.value))}/></label>
    <button type="button" className="tiny danger" aria-label={tUI('Remove enchantment')} onClick={()=>enchant(i,id,null)}>×</button>
   </div>)}
   <button type="button" className="gear-add-enchantment" onClick={()=>{
    const existing=gear.enchantments||{};let id='minecraft:protection',n=2;
    while(Object.hasOwn(existing,id))id='modid:enchantment_'+n++;
    enchant(i,id,1);
   }}>+ {tUI('Enchantment')}</button>
   <button type="button" className="gear-add-enchantment" onClick={()=>setPickEnchant({item:i})}>{tUI('Browse enchantments')}</button>
  </section>)}</div>
  {pickEnchant!==null&&<EnchantmentPicker
   taken={Object.keys(items[pickEnchant.item]?.enchantments||{})}
   replaceId={pickEnchant.previous}
   onPick={id=>{
    const current=items[pickEnchant.item];
    if(!current){setPickEnchant(null);return;}
    const next={...(current.enchantments||{})};
    if(pickEnchant.previous&&pickEnchant.previous!==id){
     const preserved=next[pickEnchant.previous];delete next[pickEnchant.previous];next[id]=preserved||1;
    }else if(!pickEnchant.previous)next[id]=1;
    edit(pickEnchant.item,{enchantments:next});setPickEnchant(null);
   }} onClose={()=>setPickEnchant(null)}/>}
  {pick!==null&&<TargetPicker kind="item" onPick={id=>{edit(pick,{item:id});setPick(null)}} onClose={()=>setPick(null)}/>}
 </div>;
}
