import React,{useEffect,useRef,useState} from 'react';
import {Globe,Link2,UploadCloud} from 'lucide-react';
import {Dialog} from './Dialog';
import {fileMap,importDocuments,validate} from '../core/project.js';
import {setHubRegistry} from '../core/hub-registry.js';
import {tUI} from '../ui/ui-i18n.js';
import type {Project} from '../types';

type HubData={files:{name:string;content:string}[];registry?:Record<string,unknown>;revision:string;serverName?:string};
const params=new URLSearchParams(window.location.search);
const defaultHub=String(params.get('hub')||import.meta.env.VITE_QUESTPLUS_HUB_URL||'http://localhost:8787').replace(/\/$/,'');
const linkedSession=params.get('session')||'';
const linkedPack=params.get('pack')||'';
const documents=(p:Project)=>[...fileMap(p)].filter(([name])=>name.endsWith('.json')).map(([name,content])=>({name,content}));
const json=async(url:string,method='GET',body?:unknown,access?:string)=>{
 const response=await fetch(url,{method,headers:{...(body?{'Content-Type':'application/json'}:{}),...(access?{Authorization:'Bearer '+access}:{})},body:body?JSON.stringify(body):undefined,cache:'no-store'});
 const data=await response.json().catch(()=>({error:'Invalid Hub response'}));
 if(!response.ok)throw Error(data.error||'HTTP '+response.status);
 return data;
};
export function HubPanel({project,onLoad}:{project:Project;onLoad:(p:Project)=>void}){
 const [open,setOpen]=useState(Boolean(linkedPack||linkedSession));
 const [hub,setHub]=useState(defaultHub);
 const [session,setSession]=useState(linkedSession);
 const [pack,setPack]=useState(linkedPack);
 const [pending,setPending]=useState<{requestId:string;requestSecret:string;code:string}|null>(null);
 const [token,setToken]=useState('');
 const [revision,setRevision]=useState('');
 const [serverName,setServerName]=useState('');
 const [share,setShare]=useState('');
 const [status,setStatus]=useState('');
 const [busy,setBusy]=useState(false);
 const tokenRef=useRef(token);tokenRef.current=token;
 const host=hub.replace(/\/$/,'');
 const run=async(job:()=>Promise<void>)=>{
  setBusy(true);setStatus('');
  try{await job()}catch(e){setStatus((e as Error).message)}finally{setBusy(false)}
 };
 const loadProject=(data:HubData)=>{
  const next=importDocuments(data.files);
  const issues=validate(next);
  if(issues.length)throw Error(issues[0].where+': '+issues[0].message);
  if(!window.confirm('Replace the current editor project with the selected pack? Export your existing work first.'))return false;
  onLoad(next as Project);setHubRegistry(data.registry||{});return true;
 };
 const start=()=>run(async()=>{
  if(!session)throw Error('Enter a server session ID');
  const meta=await json(host+'/v1/editor/sessions/'+encodeURIComponent(session));
  setServerName(meta.serverName||'Minecraft server');
  const req=await json(host+'/v1/editor/sessions/'+encodeURIComponent(session)+'/request','POST',{});
  setPending(req);setStatus('Compare code '+req.code+' with Minecraft, then approve using /questplus editor approve '+req.code);
 });
 useEffect(()=>{
  if(!pending||!session||token)return;
  let cancelled=false;
  const check=async()=>{
   try{
    const result=await json(host+'/v1/editor/sessions/'+encodeURIComponent(session)+'/status','POST',pending);
    if(cancelled||!result.approved)return;
    setToken(result.editorToken);setRevision(result.revision);setPending(null);
    setStatus('Authorized by Minecraft. You can now load and publish server quests.');
   }catch(error){if(!cancelled)setStatus('Approval: '+(error as Error).message)}
  };
  void check();const timer=window.setInterval(()=>{void check()},2500);
  return()=>{cancelled=true;window.clearInterval(timer)};
 },[hub,session,pending,token]);
 useEffect(()=>{
  if(!token)return;
  let mounted=true;
  const timer=window.setInterval(()=>{
   void json(host+'/v1/editor/sessions/'+encodeURIComponent(session)+'/ping','GET',undefined,token)
    .catch(e=>{if(mounted){setToken('');setRevision('');setStatus('Session expired or revoked: '+(e as Error).message)}});
  },60_000);
  return()=>{mounted=false;window.clearInterval(timer)};
 },[hub,session,token]);
 const load=()=>run(async()=>{
  if(!tokenRef.current)throw Error('Approve the browser from Minecraft first');
  const response:HubData=await json(host+'/v1/editor/sessions/'+encodeURIComponent(session)+'/data','GET',undefined,tokenRef.current);
  if(loadProject(response)){setRevision(response.revision);setStatus('Server quests loaded; edits remain local until Publish.')}
 });
 const publish=()=>run(async()=>{
  if(!tokenRef.current)throw Error('Authorize this browser first');
  const issues=validate(project);if(issues.length)throw Error(issues[0].where+': '+issues[0].message);
  if(!window.confirm('Publish these quest definitions to the connected Minecraft server? Existing player progress will be preserved.'))return;
  const response=await json(host+'/v1/editor/sessions/'+encodeURIComponent(session)+'/publish','POST',{files:documents(project),baseRevision:revision},tokenRef.current);
  setStatus('Publishing… waiting for Minecraft server validation.');
  for(let i=0;i<65;i++){
   await new Promise(ok=>setTimeout(ok,2500));
   const update=await json(host+'/v1/editor/sessions/'+encodeURIComponent(session)+'/jobs/'+response.id,'GET',undefined,tokenRef.current);
   if(update.status==='applied'){setRevision(update.revision);setStatus('Published successfully! Minecraft players are updated.');return}
   if(update.status==='failed')throw Error('Server rejected publication: '+update.error);
  }
  throw Error('Server did not acknowledge publication. Check Minecraft before retrying.');
 });
 const publishPack=()=>run(async()=>{
  const issues=validate(project);if(issues.length)throw Error(issues[0].where+': '+issues[0].message);
  const result=await json(host+'/v1/packs','POST',{files:documents(project)});
  setShare(result.url+'&hub='+encodeURIComponent(host));setStatus('Pack saved for 7 days. Import in Minecraft: /questplus import '+result.id);
 });
 const loadPack=()=>run(async()=>{
  if(!pack)throw Error('Enter a pack ID');
  const data=await json(host+'/v1/packs/'+encodeURIComponent(pack));
  if(loadProject(data))setStatus('Pack opened. Save ZIP or edit and share a new version.');
 });
 return <>
  <button type="button" className="button secondary hub-toolbar-button" onClick={()=>setOpen(true)}>
   <Globe size={17}/>{token?tUI('Connected server'):tUI('Online / Server')}
  </button>
  {open&&<Dialog title={tUI('QuestPlus Hub')} onClose={()=>setOpen(false)}>
   <div className="hub-panel">
    <label className="field"><span>{tUI('Hub address')}</span>
     <input value={hub} onChange={e=>{setHub(e.target.value);setToken('');setPending(null);setRevision('');}} spellCheck={false} placeholder="https://hub.example.com"/></label>
    <p className="picker-help">Use a trusted HTTPS QuestPlus Hub. Only local development may use HTTP localhost.</p>
    <section className="hub-section">
     <h3>{tUI('Connect Minecraft server')}</h3>
     <label className="field"><span>{tUI('Session ID')}</span>
      <input value={session} onChange={e=>{setSession(e.target.value);setToken('');setPending(null);setRevision('');}} spellCheck={false} placeholder="Session from /questplus editor"/></label>
     {!token&&<button className="button primary" disabled={busy||!!pending} onClick={start}>{pending?tUI('Awaiting in-game approval'):tUI('Request authorization')}</button>}
     {pending&&<p className="hub-confirm-code">{pending.code}</p>}
     {token&&<div className="hub-actions">
      <span className="hub-online">{serverName||'Minecraft'} — {tUI('Authorized')}</span>
      <button className="button secondary" disabled={busy} onClick={load}>{tUI('Load server quests')}</button>
      <button className="button primary" disabled={busy} onClick={publish}><UploadCloud size={16}/>{tUI('Publish to server')}</button>
     </div>}
    </section>
    <section className="hub-section">
     <h3>{tUI('Standalone quest packs')}</h3>
     <button className="button secondary" disabled={busy} onClick={publishPack}><Link2 size={16}/>{tUI('Save online / Share')}</button>
     <label className="field"><span>{tUI('Open shared pack')}</span>
      <div className="hub-actions"><input value={pack} onChange={e=>setPack(e.target.value)} placeholder="Pack ID"/>
       <button className="button secondary" disabled={busy} onClick={loadPack}>{tUI('Open pack')}</button></div>
     </label>
     {share&&<div className="hub-share"><input readOnly aria-label={tUI('Share link')} value={share}/><button className="button secondary" onClick={()=>void navigator.clipboard.writeText(share)}>{tUI('Copy link')}</button></div>}
    </section>
    {status&&<p className="hub-status" role="status">{status}</p>}
   </div>
  </Dialog>}
 </>;
}
