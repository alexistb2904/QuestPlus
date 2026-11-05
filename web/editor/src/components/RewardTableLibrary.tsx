import React,{useState} from 'react';
import {Dialog} from './Dialog';
import {RewardEditor} from './RewardEditor';
import {tUI as t} from '../ui/ui-i18n.js';
import {validate} from '../core/project.js';
import type {Project} from '../types';

export function RewardTableLibrary({project,language,onChange,onClose}:{project:Project;language:string;onChange:(tables:NonNullable<Project['rewardTables']>)=>void;onClose:()=>void}){
 const [index,setIndex]=useState(0),tables=project.rewardTables||[],table=tables[index];
 const errors=validate(project).filter((e:{where:string})=>e.where==='Reward tables');
 return <Dialog title={t('Reward tables')} onClose={onClose}>
  <p>{t('Update the named table to update linked quests.')}</p>
  <select aria-label={t('Saved reward tables')} value={index} onChange={e=>setIndex(Number(e.target.value))}>{tables.map((v,i)=><option value={i} key={v.name}>{v.name}</option>)}</select>
  {table?<><RewardEditor key={table.name} singlePool rewards={[table.reward]} language={language} project={project} onAdvanced={()=>{}} onSaveTable={()=>{}} onChange={rewards=>onChange(tables.map((v,i)=>i===index?{...v,reward:rewards[0]}:v))}/>
   <button type="button" className="button danger" onClick={()=>{if(!confirm(t('Delete named table? Linked quests must be updated before export.')))return;onChange(tables.filter((_,i)=>i!==index));setIndex(0);}}>{t('Delete')}</button></>:<p>{t('Save a reward pool as a named table from a quest to start.')}</p>}
  {errors.map((error:{message:string},i:number)=><p role="alert" key={i}>{error.message}</p>)}
 </Dialog>;
}
