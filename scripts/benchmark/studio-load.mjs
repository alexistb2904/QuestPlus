import assert from 'node:assert/strict';
import http from 'node:http';
import {spawn} from 'node:child_process';
import {writeFile,mkdir} from 'node:fs/promises';
import {fileURLToPath,pathToFileURL} from 'node:url';
import path from 'node:path';
import {performance} from 'node:perf_hooks';
import {emptyProject,category,quest,fileMap} from '../../web/editor/src/core/project.js';

const root=path.resolve(fileURLToPath(new URL('../..',import.meta.url)));
const label=process.argv[2]||'current',scenario=process.argv[3]||'large',run=process.argv[4]||'run';
const out=path.join(root,'build/benchmark/studio',label+'-'+scenario);await mkdir(out,{recursive:true});
function registry(mods=500,itemsPerMod=100){
 const items=Array.from({length:mods*itemsPerMod},(_,i)=>`benchmod${Math.floor(i/itemsPerMod)}:resource_${i%itemsPerMod}`);
 return {items,blocks:items.filter((_,i)=>i%2===0),tags:{items:items.filter((_,i)=>i%20===0).map(id=>'#'+id)},assets:{names:Object.fromEntries(items.slice(0,1000).map(id=>[id,{en_us:'Benchmark resource '+id,fr_fr:'Ressource '+id}])),textures:{},limited:true}};
}
const project=emptyProject(),cat=category('questplus:bench','Load benchmark');project.categories=[cat];
project.quests=Array.from({length:scenario==='small'?100:1000},(_,i)=>({...quest(cat,'questplus:bench/q'+i,'Benchmark quest '+i),layout:{x:(i%32)*80,y:Math.floor(i/32)*80}}));
const files=[...fileMap(project)].filter(([name])=>/\.json5?$/.test(name)).map(([name,content])=>({name,content}));
const catalogue=registry(scenario==='small'?2:500,scenario==='small'?20:100);
const payload=JSON.stringify({serverName:'Synthetic 500-mod server',files,registry:catalogue});
await writeFile(path.join(out,'fixture.json'),JSON.stringify({files,registry:catalogue}));
if(label==='baseline')throw Error('The historic Hub protocol is unsupported. Compare new session-based runs instead.');
const serverFile=path.join(root,'web/api/server.mjs');
const port=await new Promise(resolve=>{const s=http.createServer();s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>resolve(p))})});
let last={},peakRss=0,peakHeap=0,loopMax=0,childExit=null,stderr='';
const child=spawn(process.execPath,['--max-old-space-size=384','--import',pathToFileURL(path.join(root,'scripts/benchmark/studio-metrics.mjs')).href,serverFile],{
 cwd:path.join(root,'web/api'),env:{...process.env,PORT:String(port),HUB_PUBLIC_URL:`http://127.0.0.1:${port}`,STUDIO_ORIGIN:`http://127.0.0.1:${port}`,HUB_DATA_DIR:path.join(out,'packs-'+run),WEB_STATIC_DIR:path.join(root,'web/editor/dist')},stdio:['ignore','ignore','pipe','ipc']});
child.on('message',m=>{if(m.type==='metrics'){last=m;peakRss=Math.max(peakRss,m.rss);peakHeap=Math.max(peakHeap,m.heap);loopMax=Math.max(loopMax,m.max)}});
child.stderr.on('data',b=>{stderr=(stderr+b.toString()).slice(-30000)});child.on('exit',(code,signal)=>{childExit={code,signal}});
const agent=new http.Agent({keepAlive:true,maxSockets:128});
function request(route,{method='GET',body,auth,actor=0,collect=true}={}){
 return new Promise(resolve=>{
  const start=performance.now(),headers={...(auth?{authorization:'Bearer '+auth}:{}),...(body?{'content-type':'application/json','content-length':Buffer.byteLength(body)}:{})};
  const req=http.request({host:'127.0.0.1',port,path:route,method,headers,agent,localAddress:`127.${Math.floor(actor/60000)}.${Math.floor(actor/250)%240}.${actor%250+2}`},res=>{
   let bytes=0;const chunks=[];res.on('data',b=>{bytes+=b.length;if(collect)chunks.push(b)});
   res.on('end',()=>{let data;try{data=collect?JSON.parse(Buffer.concat(chunks).toString()):null}catch{}
    resolve({status:res.statusCode,ms:performance.now()-start,bytes,data,retry:res.headers['retry-after']})});
   res.on('error',()=>resolve({status:'reset',ms:performance.now()-start,bytes}));
  });req.setTimeout(15000,()=>req.destroy());req.on('error',()=>resolve({status:'reset',ms:performance.now()-start,bytes:0}));req.end(body);
 });
}
const rows=[],sessions=[],healthSamples=[];let healthTimer;
async function authorize(session){
 const pending=await request(`/v1/editor/sessions/${session.id}/request`,{method:'POST',body:'{}',actor:session.actor});assert.equal(pending.status,201);
 const a=await request('/v1/server/approve',{method:'POST',auth:session.serverToken,actor:session.actor,body:JSON.stringify({session:session.id,id:pending.data.requestId,code:pending.data.code})});assert.equal(a.status,200);
 const auth=await request(`/v1/editor/sessions/${session.id}/status`,{method:'POST',body:JSON.stringify(pending.data),actor:session.actor});assert.equal(auth.status,200);
 return {...session,token:auth.data.editorToken,revision:auth.data.revision};
}
async function phase(name,total,concurrency,operation){
 const results=[];let cursor=0;const start=performance.now();
 await Promise.all(Array.from({length:concurrency},async()=>{for(;;){const i=cursor++;if(i>=total)break;results.push(await operation(i))}}));
 const times=results.map(r=>r.ms).sort((a,b)=>a-b),counts={};for(const r of results)counts[r.status]=(counts[r.status]||0)+1;
 const q=p=>times[Math.min(times.length-1,Math.ceil(times.length*p)-1)]||0;
 const row={phase:name,requests:results.length,concurrency,statuses:counts,p50:q(.5),p95:q(.95),p99:q(.99),elapsed:performance.now()-start,bytes:results.reduce((s,r)=>s+r.bytes,0),rss:last.rss,heap:last.heap,loopP99:last.p99};rows.push(row);console.log(JSON.stringify(row));return results;
}
try{
 for(let i=0;i<100;i++){if((await request('/health')).status===200)break;await new Promise(r=>setTimeout(r,50))}
 assert.equal((await request('/health')).status,200);
 let probing=false;healthTimer=setInterval(async()=>{if(probing)return;probing=true;try{healthSamples.push(await request('/health'))}finally{probing=false}},100);
 for(let start=0;start<1000;start+=20){
  await phase('sessions-'+(start+20),20,8,async i=>{
   const actor=start+i,r=await request('/v1/server/sessions',{method:'POST',body:payload,actor});
   if(r.status===201)sessions.push({...r.data,actor});return r;
  });
  if(start===0&&sessions.length){
   const probes=[];for(const s of sessions.slice(0,4))probes.push(await authorize(s));
   await phase('initial-catalogue-reads',4,4,i=>request(`/v1/editor/sessions/${probes[i].id}/data`,{auth:probes[i].token,actor:probes[i].actor,collect:false}));
  }
  if(childExit||peakRss>450*1024*1024){rows.push({phase:'safety_stop',accepted:sessions.length,reason:childExit?'child_exit':'rss_450_MiB'});break}
 }
 const approved=[];
 for(const session of sessions.slice(0,128)){
  approved.push(await authorize(session));
 }
 if(approved.length&&!childExit&&peakRss<=450*1024*1024){
  for(const concurrency of [8,32,128])await phase('catalogue-reads',Math.min(1000,scenario==='small'?1000:256),concurrency,i=>{const s=approved[i%approved.length];return request(`/v1/editor/sessions/${s.id}/data`,{auth:s.token,actor:s.actor,collect:false})});
  const publishBody=JSON.stringify({files,baseRevision:approved[0].revision});
  await phase('publish-burst',256,64,i=>{const s=approved[i%approved.length];return request(`/v1/editor/sessions/${s.id}/publish`,{method:'POST',auth:s.token,actor:s.actor,body:publishBody})});
  const packBody=JSON.stringify({files});await phase('public-pack-burst',32,16,i=>request('/v1/packs',{method:'POST',body:packBody,actor:20000+i}));
  await phase('health-after-overload',100,8,()=>request('/health'));
 }
}catch(error){rows.push({phase:'failure',error:error.message});console.error(error)}
finally{
 clearInterval(healthTimer);
 if(healthSamples.length){const times=healthSamples.map(r=>r.ms).sort((a,b)=>a-b);rows.push({phase:'health-during-load',requests:times.length,successes:healthSamples.filter(r=>r.status===200).length,p95:times[Math.ceil(times.length*.95)-1],p99:times[Math.ceil(times.length*.99)-1]})}
 agent.destroy();child.kill();await new Promise(resolve=>childExit?resolve():child.once('exit',resolve));
 const completed=!rows.some(r=>r.phase==='failure');
 const report={label,scenario,node:process.version,payloadBytes:Buffer.byteLength(payload),mods:scenario==='small'?2:500,registryItems:catalogue.items.length,quests:project.quests.length,accepted:sessions.length,peakRss,peakHeap,loopMax,childExit,completed,rows};
 await writeFile(path.join(out,'results.json'),JSON.stringify(report,null,2));await writeFile(path.join(out,'results-'+run+'.json'),JSON.stringify(report,null,2));await writeFile(path.join(out,'server-'+run+'.log'),stderr);
 console.log('RESULT '+JSON.stringify({accepted:sessions.length,peakRss,peakHeap,loopMax,childExit}));
 if(!completed)process.exitCode=1;
}
