import React,{useMemo,useState} from 'react';
import {Dialog} from './Dialog';
import {ItemIcon} from './IconPicker';
import {hubRegistry,setHubRegistry} from '../core/hub-registry.js';

/** Browser backed by the current approved server's registry snapshot, or by a local JSON export. */
export function RegistryBrowser({onClose,onPick}:{onClose:()=>void;onPick?:(kind:string,id:string)=>void}){
 const [version,setVersion]=useState(0),[kind,setKind]=useState('items'),[query,setQuery]=useState(''),[limit,setLimit]=useState(120),[message,setMessage]=useState('');
 const data=hubRegistry() as Record<string,any>;
 const kinds=['items','blocks','entities','effects','enchantments','biomes','structures','dimensions'];
 const entries=useMemo(()=>{
   const values=Array.isArray(data[kind])?data[kind] as string[]:[];
   const tags=Array.isArray(data.tags?.[kind])?data.tags[kind] as string[]:[];
   const key=query.trim().toLowerCase();
   return [...values,...tags].filter(id=>id.toLowerCase().includes(key));
 },[data,kind,query,version]);
 const load=async(file:File)=>{
   try{
     if(file.size>5_000_000)throw Error('Registry snapshot exceeds 5 MB');
     const raw=JSON.parse(await file.text());
     if(!raw || typeof raw!=='object'||Array.isArray(raw))throw Error('Invalid registry snapshot');
     setHubRegistry(raw.registry && typeof raw.registry==='object'?raw.registry:raw);
     setVersion(n=>n+1);setMessage('Registry snapshot loaded locally for this session.');
   }catch(error){setMessage((error as Error).message)}
 };
 const copy=async(id:string)=>{
   try {await navigator.clipboard.writeText(id);setMessage('Copied '+id)}
   catch{setMessage('Clipboard unavailable: '+id)}
 };
 return <Dialog title="Mod Registry Browser" onClose={onClose} className="icon-dialog">
  <p>Search items, blocks and other resources installed on the connected Minecraft server. A local export can also be opened without connecting.</p>
  <label className="button secondary" style={{display:'inline-flex',cursor:'pointer'}}>Import registry JSON
   <input type="file" accept=".json" hidden onChange={e=>{const file=e.target.files?.[0];if(file)void load(file);e.target.value=''}}/>
  </label>
  <div className="picker-filters" style={{marginTop:12}}>
   <select aria-label="Registry type" value={kind} onChange={e=>{setKind(e.target.value);setLimit(120)}}>
    {kinds.map(k=><option key={k} value={k}>{k} ({Array.isArray(data[k])?data[k].length:0})</option>)}
   </select>
   <input aria-label="Search registry resources" placeholder="Search modid, ID or tag…" value={query} onChange={e=>{setQuery(e.target.value);setLimit(120)}}/>
  </div>
  <div className="picker-count">{entries.length} entries {entries.length===0?'· Connect via Server Editor, or import a registry snapshot.':''}</div>
  <div className="picker-grid">
   {entries.slice(0,limit).map(id=><button key={id} type="button" className="picker-item" title={id} onClick={()=>{if(onPick)onPick(kind,id);else void copy(id)}}>
    {(kind==='items'||kind==='blocks')&&<ItemIcon id={id.replace(/^#/,'')}/>}
    <span>{id}</span>
   </button>)}
  </div>
  {entries.length>limit&&<button className="button secondary" onClick={()=>setLimit(n=>n+120)}>Load more</button>}
  <p role="status" className="picker-help">{message||'Click a resource to copy its ID, or insert it into the selected quest objective.'}</p>
 </Dialog>;
}
