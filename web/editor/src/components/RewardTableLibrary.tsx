import React,{useState} from 'react';
import {Dialog} from './Dialog';
import {Field} from './Inspector';
import {RewardEditor} from './RewardEditor';
import {tUI as t} from '../ui/ui-i18n.js';
import {validate} from '../core/project.js';
import type {Project} from '../types';

export function RewardTableLibrary({project,language,onChange,onClose}:{project:Project;language:string;onChange:(tables:NonNullable<Project['rewardTables']>)=>void;onClose:()=>void}){
 const [index,setIndex]=useState(0),[newName,setNewName]=useState('New reward table'),tables=project.rewardTables||[],table=tables[index];
 const create=()=>{const name=newName.trim();if(!name||name.length>80||tables.some(row=>row.name===name))return;onChange([...tables,{name,reward:{type:'questplus:loot',data:{options:[{type:'item',item:'minecraft:diamond',count:1,weight:1}],emptyWeight:1,rolls:1}}}]);setIndex(tables.length);};
 const errors=validate(project).filter((e:{where:string})=>e.where==='Reward tables');
 return <Dialog title={t('Reward tables')} onClose={onClose}>
  <p>{t('Named tables let several quests share one reward pool. Changes here update every linked quest.')}</p>
  {tables.length>0&&<label className="field"><span>{t('Saved reward tables')}</span><select aria-label={t('Saved reward tables')} value={index} onChange={e=>setIndex(Number(e.target.value))}>{tables.map((v,i)=><option value={i} key={v.name}>{v.name}</option>)}</select></label>}
  {table?<><label className="checkbox-row"><input type="checkbox" disabled={table.reward.type==='questplus:choice'} checked={!!table.crate} onChange={e=>onChange(tables.map((v,i)=>i===index?{...v,crate:e.target.checked}:v))}/>{t('Openable loot crate')}</label>
   {table.crate&&<>
    <Field label="Crate name" value={table.crateName||table.name} onChange={crateName=>onChange(tables.map((v,i)=>i===index?{...v,crateName}:v))}/>
    <Field label="Crate color" type="color" value={table.crateColor||'#ffffff'} onChange={crateColor=>onChange(tables.map((v,i)=>i===index?{...v,crateColor}:v))}/>
    <label className="checkbox-row"><input type="checkbox" checked={!!table.crateGlow} onChange={e=>onChange(tables.map((v,i)=>i===index?{...v,crateGlow:e.target.checked}:v))}/>{t('Enchantment glint')}</label>
    <p>{t('Drop weights (enable crate drops in project settings)')}</p>
    <div className="field-row">{(['passive','monster','boss'] as const).map(kind=><Field key={kind} label={kind} type="number" value={table.crateDrops?.[kind]??0} onChange={value=>onChange(tables.map((v,i)=>i===index?{...v,crateDrops:{passive:0,monster:0,boss:0,...v.crateDrops,[kind]:value}}:v))}/>)}</div>
   </>}
   {table.crate&&<label className="field"><span>{t('Crate item command')}</span><textarea readOnly value={'/give @s questplus:loot_crate[minecraft:custom_data={questplus_table:'+JSON.stringify(table.name)+'}]'}/></label>}
   <RewardEditor key={table.name} singlePool rewards={[table.reward]} language={language} project={project} onAdvanced={()=>{}} onSaveTable={()=>{}} onChange={rewards=>onChange(tables.map((v,i)=>i===index?{...v,reward:rewards[0]}:v))}/>
   <button type="button" className="button danger" onClick={()=>{if(!confirm(t('Delete named table? Linked quests must be updated before export.')))return;onChange(tables.filter((_,i)=>i!==index));setIndex(0);}}>{t('Delete')}</button></>:<div className="library-empty">
    <strong>{t('No reward tables yet')}</strong><span>{t('Create a table to reuse the same reward pool across quests. You can edit its items, chances and draw count here.')}</span>
    <label className="field"><span>{t('Table name')}</span><input maxLength={80} value={newName} onChange={e=>setNewName(e.target.value)}/></label>
    <button type="button" className="button primary" disabled={!newName.trim()||newName.length>80||tables.some(row=>row.name===newName.trim())} onClick={create}>{t('Create reward table')}</button>
   </div>}
  {errors.map((error:{message:string},i:number)=><p role="alert" key={i}>{error.message}</p>)}
 </Dialog>;
}
