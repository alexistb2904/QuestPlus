import test from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'node:net';
import {mkdtemp,writeFile,mkdir,rm} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {checkConfig,tcpReady,waitUntil} from '../../../scripts/local-stack.mjs';

async function listen(){
 const server=createServer(socket=>socket.end());
 await new Promise((resolve,reject)=>server.once('error',reject).listen(0,'127.0.0.1',resolve));
 return server;
}
test('stack readiness detects available and unavailable ports without binding them',async()=>{
 const server=await listen();
 try{
  const port=server.address().port;
  assert.equal(await tcpReady(port),true);
  await new Promise((resolve,reject)=>server.close(error=>error?reject(error):resolve()));
  assert.equal(await tcpReady(port),false);
 }finally{if(server.listening)await new Promise(resolve=>server.close(resolve))}
});
test('stack preflight never rewrites a mismatched local server key',async()=>{
 const root=await mkdtemp(path.join(os.tmpdir(),'questplus-stack-check-'));
 try{
  await mkdir(path.join(root,'run/server/world'),{recursive:true});
  const secret='a'.repeat(64),key=path.join(root,'run/questplus-web-local.key');
  const config=path.join(root,'run/server/world/questplus-hub.json');
  await writeFile(key,secret+'\n');
  await writeFile(config,JSON.stringify({url:'http://localhost:8787',serverKey:secret}));
  assert.equal(await checkConfig(root),true);
  await writeFile(config,JSON.stringify({url:'https://different.example',serverKey:secret}));
  await assert.rejects(()=>checkConfig(root),/differs/);
  await writeFile(config,JSON.stringify({url:'http://localhost:8787',serverKey:'b'.repeat(64)}));
  await assert.rejects(()=>checkConfig(root),/differs/);
 }finally{await rm(root,{recursive:true,force:true})}
});
test('stack wait loop succeeds when readiness turns true and rejects on timeout',async()=>{
 let attempts=0;
 assert.equal(await waitUntil(async()=>++attempts===3,{label:'test',timeoutMs:600,intervalMs:10}),true);
 await assert.rejects(()=>waitUntil(async()=>false,{label:'test endpoint',timeoutMs:40,intervalMs:10}),/did not become ready/);
});
