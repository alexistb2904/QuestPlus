// Simulated Minecraft peer; use only the credentials returned for its session.
import {readFile,writeFile} from 'node:fs/promises';
const base='http://127.0.0.1:5201';
const output=new URL('../../build/benchmark/studio/browser-session.json',import.meta.url);
async function api(route,body,auth){const r=await fetch(base+route,{method:body?'POST':'GET',headers:{...(auth?{authorization:'Bearer '+auth}:{}),'content-type':'application/json'},body:body?JSON.stringify(body):undefined});if(!r.ok)throw Error(r.status+' '+await r.text());return r.json()}
if(process.argv[2]==='create'){
 const fixture=JSON.parse(await readFile(new URL('../../build/benchmark/studio/current-large/fixture.json',import.meta.url),'utf8'));
 const session=await api('/v1/server/sessions',{serverName:'500 mods / 1000 quests benchmark',...fixture});
 await writeFile(output,JSON.stringify(session));console.log(session.url);
}else{
 const s=JSON.parse(await readFile(output,'utf8')),poll=await api('/v1/server/poll?session='+s.id,undefined,s.serverToken);
 if(process.argv[2]==='approve'){
  if(!poll.pending)throw Error('No browser approval request');
  console.log(await api('/v1/server/approve',{session:s.id,...poll.pending},s.serverToken));
 }else if(process.argv[2]==='ack'){
  if(!poll.job)throw Error('No pending publication');
  if(poll.job.files.length<1000)throw Error('Incomplete large publication');
  console.log(await api('/v1/server/ack',{session:s.id,id:poll.job.id,success:true},s.serverToken));
 }else throw Error('Expected create, approve or ack');
}
