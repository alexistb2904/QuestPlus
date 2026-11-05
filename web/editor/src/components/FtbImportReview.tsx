import React,{useState} from 'react';
import {tUI as t} from '../ui/ui-i18n.js';
import {Dialog} from './Dialog';
import {inspectFtbPack} from '../core/ftb-import.js';
type Pending={report:ReturnType<typeof inspectFtbPack>;documents:{name:string;content:string}[];files:File[]};
export function FtbImportReview({pendingImport,onConfirm,onCancel}:{pendingImport:Pending;onConfirm:()=>void;onCancel:()=>void}){
 const [search,setSearch]=useState('');const {report,files}=pendingImport;
 const warnings=report.warnings.filter((w:string)=>w.toLowerCase().includes(search.toLowerCase()));
 const entries=[...report.sourceIdMap.entries()].filter(([a,b])=>(a+' '+b).toLowerCase().includes(search.toLowerCase()));
 const download=(blob:Blob,name:string)=>{const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1500)};
 return <Dialog title={t('Review FTB import')} onClose={onCancel}>
  <p>{report.chapterCount} {t('Chapters')} · {report.questCount} {t('Quests')} · {report.warnings.length} {t('Warnings')}</p>
  <p>{t('Unsupported quests and their dependents are excluded. Review the mapping before replacing your project; undo restores it.')}</p>
  <label className="field"><span>{t('Search report')}</span><input value={search} onChange={e=>setSearch(e.target.value)}/></label>
  <h3>{t('Warnings')}</h3><div className="bulk-quest-list">{warnings.map((warning:string,i:number)=><p key={i}><strong>{/skipped|not imported|invalid|ambiguous/i.test(warning)?t('Blocked'):t('Warning')}:</strong> {warning}</p>)}{!warnings.length&&<p>{t('No matching warnings.')}</p>}</div>
  <h3>{t('Source ID mapping')}</h3><div className="bulk-quest-list">{entries.map(([source,target])=><p key={source}>{source} → {target}{!report.project.quests.some((q:{id:string})=>q.id===target)?' · '+t('Excluded'):''}</p>)}</div>
  <div className="toolbar-row"><button className="button secondary" onClick={()=>files.forEach(file=>download(file,file.name))}>{t('Download source backup')}</button>
  <button className="button secondary" onClick={onCancel}>{t('Cancel')}</button><button className="button primary" disabled={!report.questCount} onClick={onConfirm}>{t('Replace project')}</button></div>
 </Dialog>;
}
