import React,{useState,useMemo} from 'react';
import {tUI as t} from '../ui/ui-i18n.js';
import {Dialog} from './Dialog';
import {inspectFtbPack,selectFtbQuests} from '../core/ftb-import.js';
type Pending={report:ReturnType<typeof inspectFtbPack>;documents:{name:string;content:string}[];files:File[]};
export function FtbImportReview({pendingImport,onConfirm,onCancel}:{pendingImport:Pending;onConfirm:(selection:string[],options?:{fluidUnits:string;energyRatio:number})=>void;onCancel:()=>void}){
 const [search,setSearch]=useState(''),[fluidUnits,setFluidUnits]=useState('millibuckets'),[energyRatio,setEnergyRatio]=useState(1);const {files}=pendingImport;
 const report=useMemo(()=>fluidUnits==='millibuckets'&&energyRatio===1?pendingImport.report:inspectFtbPack(pendingImport.documents,{fluidUnits,energyRatio}),[pendingImport,fluidUnits,energyRatio]);
 const [selected,setSelected]=useState<string[]>(()=>report.project.quests.map((q:{id:string})=>q.id));
 const effective=selectFtbQuests(report,selected.filter(id=>report.project.quests.some((q:{id:string})=>q.id===id)));
 const warnings=report.warnings.filter((w:string)=>w.toLowerCase().includes(search.toLowerCase()));
 const entries=[...report.sourceIdMap.entries()].filter(([a,b])=>(a+' '+b).toLowerCase().includes(search.toLowerCase()));
 const download=(blob:Blob,name:string)=>{const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1500)};
 return <Dialog title={t('Review FTB import')} onClose={onCancel}>
  <p>{report.chapterCount} {t('Chapters')} · {report.questCount} {t('Quests')} · {report.warnings.length} {t('Warnings')}</p>
  <p>{t('Unsupported quests and their dependents are excluded. Review the mapping before replacing your project. Undo restores it.')}</p>
  <label className="field"><span>{t('Search report')}</span><input value={search} onChange={e=>setSearch(e.target.value)}/></label>
  <div className="field-row"><label className="field"><span>{t('Source fluid units')}</span><select value={fluidUnits} onChange={e=>setFluidUnits(e.target.value)}><option value="millibuckets">mB (Forge / NeoForge)</option><option value="droplets">{t('Droplets (Fabric)')}</option></select></label>
  <label className="field"><span>{t('FE per Fabric energy unit')}</span><input type="number" min={1} max={1000000} step={1} value={energyRatio} onChange={e=>setEnergyRatio(Math.max(1,Math.min(1000000,Math.trunc(Number(e.target.value)||1))))}/></label></div>
  <h3>{t('Warnings')}</h3><div className="bulk-quest-list">{warnings.map((warning:string,i:number)=><p key={i}><strong>{/skipped|not imported|invalid|ambiguous/i.test(warning)?t('Blocked'):t('Warning')}:</strong> {warning}</p>)}{!warnings.length&&<p>{t('No matching warnings.')}</p>}</div>
  <h3>{t('Source ID mapping')}</h3><p>{effective.quests.length} {t('Quests')} · {t('Prerequisites are included automatically.')}</p>
  <div className="toolbar-row"><button className="button secondary" onClick={()=>setSelected(report.project.quests.map((q:{id:string})=>q.id))}>{t('Select all')}</button><button className="button secondary" onClick={()=>setSelected([])}>{t('Clear selection')}</button></div>
  <div className="bulk-quest-list">{entries.map(([source,target])=>{const quest=report.project.quests.find((q:{id:string})=>q.id===target);return <label className="checkbox-row" key={source}><input type="checkbox" disabled={!quest} checked={selected.includes(target)} onChange={e=>setSelected(e.target.checked?[...selected,target]:selected.filter(id=>id!==target))}/><span>{quest?.title?.en_us||source} · {source} → {target}{!quest?' · '+t('Excluded'):''}</span></label>;})}</div>
  <div className="toolbar-row"><button className="button secondary" onClick={()=>files.forEach(file=>download(file,file.name))}>{t('Download source backup')}</button>
  <button className="button secondary" onClick={()=>download(new Blob([JSON.stringify({warnings:report.warnings,mapping:Object.fromEntries(report.sourceIdMap),selected:effective.quests.map((q:{id:string})=>q.id)},null,2)],{type:'application/json'}),'ftb-import-report.json')}>{t('Download report')}</button>
  <button className="button secondary" onClick={onCancel}>{t('Cancel')}</button><button className="button primary" disabled={!effective.quests.length} onClick={()=>onConfirm(effective.quests.map((q:{id:string})=>q.id),{fluidUnits,energyRatio})}>{t('Replace project')}</button></div>
 </Dialog>;
}
