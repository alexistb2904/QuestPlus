import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,mkdtempSync,rmSync,existsSync,writeFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import path from 'node:path';
import os from 'node:os';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../..');
const read=p=>readFileSync(path.join(root,p),'utf8');

test('Windows dashboard exposes 23 options and real task handlers',()=>{
 const menu=read('0-Menu-Tests.bat');
 const qa=read('scripts/_qa.bat');
 for(let i=1;i<=23;i++)
  assert.ok(menu.includes('if "%SELECTION%"=="'+i+'"'),'Missing menu option '+i);
 assert.doesNotMatch(menu,/"&goto RUN_TASK/,'no unconditional cmd goto');
 assert.match(menu,/10-Tout-Tester\.bat/);
 for(const label of ['JAVA_BUILD','JAVA_TEST','JAVA_COMPILE','SERVER_TEST','CLIENT_TEST','STUDIO_DEV',
  'STUDIO_TEST','STUDIO_BUILD','API_TEST','API_RUN','API_HEALTH','DOCKER_BUILD','SUITE','STEP','DOCTOR'])
  assert.ok(qa.includes('\n:'+label+'\n')||qa.includes('\r\n:'+label+'\r\n'),'Missing task: '+label);
 assert.match(qa,/runClientGameTest/);
 assert.match(qa,/integrationTest/);
 assert.match(qa,/npm run %~1/);
 assert.match(qa,/set "HUB_BIND=127\.0\.0\.1"/);
});

test('standalone Windows launchers target split client/server and integrated web folders',()=>{
 for(const file of ['1-Lancer-Client.bat','2-Lancer-Serveur.bat','3-Compiler-Mod.bat','4-Tests-Serveur.bat',
  '5-Tests-Client.bat','6-Installer-Java-25.bat','7-QuestStudio.bat','8-Tests-Web.bat',
  '9-Lancer-Web-API.bat','10-Tout-Tester.bat','11-Diagnostic.bat','12-Configuration-Locale.bat']){
  assert.ok(existsSync(path.join(root,file)),file+' missing');
  const content=read(file);
  assert.match(content,/@echo off/i,file);
  assert.match(content,/exit \/b|goto /i,file+' should terminate or return');
 }
 assert.ok(existsSync(path.join(root,'web/editor/start.bat')));
 assert.match(read('scripts/_qa.bat'),/web\\api/);
 assert.match(read('scripts/_qa.bat'),/web\\editor/);
 assert.doesNotMatch(read('scripts/_qa.bat'),/hub\\|web\\server\\/);
});

test('local setup creates an untracked key, reuses it, and never overwrites an existing server config',()=>{
 const temp=mkdtempSync(path.join(os.tmpdir(),'questplus-windows-qa-'));
 try{
  const script=path.join(root,'web/api/tools/setup-local.mjs');
  const run=()=>spawnSync(process.execPath,[script,temp],{encoding:'utf8',timeout:10000});
  const first=run();
  assert.equal(first.status,0,first.stderr);
  const secret=readFileSync(path.join(temp,'run/questplus-web-local.key'),'utf8').trim();
  assert.match(secret,/^[0-9a-f]{64}$/);
  assert.ok(!first.stdout.includes(secret),'Secret must not be printed');
  const config=path.join(temp,'run/server/world/questplus-hub.json');
  assert.deepEqual(JSON.parse(readFileSync(config,'utf8')),{url:'http://localhost:8787',serverKey:secret});
  assert.equal(run().status,0);
  assert.equal(readFileSync(path.join(temp,'run/questplus-web-local.key'),'utf8').trim(),secret);
  writeFileSync(config,'{"url":"https://private.example","serverKey":"user-owned"}');
  assert.equal(run().status,0);
  assert.equal(readFileSync(config,'utf8'),'{"url":"https://private.example","serverKey":"user-owned"}');
 }finally{rmSync(temp,{recursive:true,force:true})}
});
