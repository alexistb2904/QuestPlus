#!/usr/bin/env node
// Only LOCAL, side-effect-free checks. Does not open ports, change EULA,
// overwrite user configuration or kill existing processes.
import net from 'node:net';
import path from 'node:path';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

export const WEB_PORT=8787, GAME_PORT=25565;
const sleep=ms=>new Promise(done=>setTimeout(done,ms));

export function tcpReady(port,host='127.0.0.1'){
 return new Promise(resolve=>{
  const socket=net.createConnection({host,port});
  let settled=false;
  const finish=ok=>{if(settled)return;settled=true;socket.destroy();resolve(ok)};
  socket.once('connect',()=>finish(true));
  socket.once('error',()=>finish(false));
  socket.setTimeout(700,()=>finish(false));
 });
}
export async function httpWebReady(){
 const controller=new AbortController();
 const timer=setTimeout(()=>controller.abort(),1800);
 try{
  const health=await fetch('http://127.0.0.1:8787/health',{signal:controller.signal});
  if(!health.ok)return false;
  const payload=await health.json();
  if(payload.status!=='ok'||payload.service!=='QuestPlus Hub')return false;
  const html=await fetch('http://127.0.0.1:8787/',{signal:controller.signal});
  if(!html.ok)return false;
  const page=await html.text();
  return /<html|<div id="root"/i.test(page);
 }catch{return false}finally{clearTimeout(timer)}
}
export async function checkConfig(root){
 const conf=JSON.parse(await readFile(path.join(root,'run/server/world/questplus-hub.json'),'utf8'));
 if(conf.url!=='http://localhost:8787')
  throw Error('Minecraft world config does not use the local Hub URL. Fix it manually; nothing was overwritten.');
 return true;
}
export async function waitUntil(probe,{label,timeoutMs,intervalMs=1200}){
 const deadline=Date.now()+timeoutMs;
 let attempt=0;
 while(Date.now()<deadline){
  if(await probe())return true;
  if(++attempt%8===0)console.log('[QuestPlus] '+label+' : en attente du demarrage...');
  await sleep(intervalMs);
 }
 throw Error(label+' did not become ready before the startup timeout');
}
async function main(){
 const command=process.argv[2];
 const root=path.resolve(process.argv[3]||path.join(path.dirname(fileURLToPath(import.meta.url)),'..'));
 switch(command){
  case 'web-ready':
   if(await httpWebReady())break;
   if(await tcpReady(WEB_PORT)){
    console.error('[QuestPlus] Port 8787 is in use but Quest Studio is not ready. Check the existing service.');
    process.exitCode=2;
   }else process.exitCode=1;
   break;
  case 'preflight':
   await checkConfig(root);
   if(await tcpReady(WEB_PORT))throw Error('Port 8787 already in use. Stop the previous web service first.');
   if(await tcpReady(GAME_PORT))throw Error('Port 25565 already in use. Stop the previous Minecraft server first.');
   console.log('[QuestPlus] Local Hub URL verified; ports 8787 and 25565 are free.');
   break;
  case 'wait-web':
   await waitUntil(httpWebReady,{label:'React + API (8787)',timeoutMs:180000});
   console.log('[OK] Quest Studio and API ready at http://localhost:8787/');
   break;
  case 'wait-minecraft':
   await waitUntil(()=>tcpReady(GAME_PORT),{label:'Minecraft Fabric (25565)',timeoutMs:480000});
   console.log('[OK] Minecraft server listens on 127.0.0.1:25565');
   break;
  default:throw Error('Usage: node scripts/local-stack.mjs preflight|web-ready|wait-web|wait-minecraft <root>');
 }
}
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 main().catch(e=>{console.error('[QuestPlus] '+e.message);process.exitCode=1});
}
