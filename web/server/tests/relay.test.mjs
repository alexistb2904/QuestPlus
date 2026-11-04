import test from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import net from 'node:net';

const key='self-hosted-test-secret-very-long-32-characters';
const files=[
 {name:'config.json',content:'{"schemaVersion":1,"autosaveSeconds":5}'},
 {name:'categories/demo.json',content:'{"id":"questplus:demo","title":{"en_us":"Demo"},"description":{"en_us":"Demo"}}'},
 {name:'quests/demo.json',content:'{"id":"questplus:demo","schemaVersion":2,"objectives":[]}'}
];
const port=()=>new Promise((resolve,reject)=>{
 const server=net.createServer();server.once('error',reject);
 server.listen(0,'127.0.0.1',()=>{const address=server.address();server.close(()=>resolve(address.port))});
});
async function api(base,route,{method='GET',body,key:serverKey,origin}={}){
 const response=await fetch(base+route,{method,headers:{
  ...(serverKey?{Authorization:'Bearer '+serverKey}:{}),
  ...(origin?{Origin:origin}:{}),
  ...(body?{'Content-Type':'application/json'}:{})},
  body:body?JSON.stringify(body):undefined});
 return {status:response.status,data:await response.json()};
}
test('Hub enforces browser approval and immutable packs before Minecraft publish',async()=>{
 const dir=await mkdtemp(path.join(tmpdir(),'questplus-hub-test-')),PORT=await port();
 const base='http://127.0.0.1:'+PORT;
 const child=spawn(process.execPath,['server.mjs'],{
  cwd:new URL('..',import.meta.url),env:{...process.env,PORT:String(PORT),HUB_PUBLIC_URL:base,HUB_DATA_DIR:dir,
  HUB_SERVER_KEY:key,STUDIO_ORIGIN:'http://localhost:5173',STUDIO_URL:'http://localhost:5173/'},
  stdio:['ignore','pipe','pipe']
 });
 try{
  let ready=false;for(let i=0;i<80;i++){
   try{const r=await api(base,'/health');if(r.status===200){ready=true;break}}catch{}
   await new Promise(ok=>setTimeout(ok,50));
  }
  assert.ok(ready,'hub starts');
  assert.equal((await api(base,'/v1/server/sessions',{method:'POST',body:{serverName:'test',files}})).status,401);
  assert.equal((await api(base,'/v1/packs',{method:'POST',origin:'https://evil.example',body:{files}})).status,403);
  const start=await api(base,'/v1/server/sessions',{method:'POST',key,body:{serverName:'Test server',files,registry:{items:['mymod:crystal']}}});
  assert.equal(start.status,201);
  const id=start.data.id;
  assert.ok(start.data.url.includes('session='+id));
  const pending=await api(base,'/v1/editor/sessions/'+id+'/request',{method:'POST',body:{}});
  assert.equal(pending.status,201);
  const poll=await api(base,'/v1/server/poll?session='+id,{key});
  assert.equal(poll.data.pending.code,pending.data.code);
  assert.equal((await api(base,'/v1/editor/sessions/'+id+'/data')).status,403);
  const status=async()=>api(base,'/v1/editor/sessions/'+id+'/status',{method:'POST',body:{
   requestId:pending.data.requestId,requestSecret:pending.data.requestSecret}});
  assert.equal((await status()).data.approved,false);
  assert.equal((await api(base,'/v1/server/approve',{method:'POST',key,body:{
   session:id,id:pending.data.requestId,code:'000000'}})).status,409);
  assert.equal((await api(base,'/v1/server/approve',{method:'POST',key,body:{
   session:id,id:pending.data.requestId,code:pending.data.code}})).status,200);
  const auth=(await status()).data.editorToken;
  assert.ok(auth?.length>32);
  const data=await api(base,'/v1/editor/sessions/'+id+'/data',{key:auth});
  assert.deepEqual(data.data.files,files);
  assert.deepEqual(data.data.registry.items,['mymod:crystal']);
  assert.equal((await api(base,'/v1/editor/sessions/'+id+'/publish',{method:'POST',key:auth,
   body:{files,baseRevision:'wrong'}})).status,409);
  const updated=files.map(f=>f.name==='config.json'?{...f,content:'{"schemaVersion":1,"autosaveSeconds":12}'}:f);
  const publish=await api(base,'/v1/editor/sessions/'+id+'/publish',{method:'POST',key:auth,
   body:{files:updated,baseRevision:data.data.revision}});
  assert.equal(publish.status,202);
  const job=(await api(base,'/v1/server/poll?session='+id,{key})).data.job;
  assert.equal(job.id,publish.data.id);
  assert.deepEqual(job.files,updated);
  assert.equal((await api(base,'/v1/server/ack',{method:'POST',key,body:{session:id,id:job.id,success:true}})).status,200);
  const complete=await api(base,'/v1/editor/sessions/'+id+'/jobs/'+job.id,{key:auth});
  assert.equal(complete.data.status,'applied');
  assert.notEqual(complete.data.revision,data.data.revision);
  const saved=await api(base,'/v1/packs',{method:'POST',body:{files}});
  assert.equal(saved.status,201);
  assert.equal((await api(base,'/v1/server/packs/'+saved.data.id,{key})).status,200);
  assert.equal((await api(base,'/v1/packs/'+saved.data.id)).status,200);
  assert.equal((await api(base,'/v1/packs/'+saved.data.id.slice(0,5))).status,404);
  const injection=await api(base,'/v1/packs',{method:'POST',body:{files:[...files,{name:'quests/../world.json',content:'{}'}]}});
  assert.equal(injection.status,400);
  const revoke=await api(base,'/v1/server/revoke',{method:'POST',key,body:{session:id}});
  assert.equal(revoke.status,200);
  assert.equal((await api(base,'/v1/editor/sessions/'+id+'/data',{key:auth})).status,404);
 }finally{child.kill('SIGTERM');await rm(dir,{recursive:true,force:true})}
},{timeout:30000});
