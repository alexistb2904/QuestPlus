#!/usr/bin/env node
// Creates/reuses an untracked LOCAL development secret. Never overwrites
// existing server configuration, and never prints the key.
import {randomBytes} from 'node:crypto';
import {mkdir,readFile,writeFile,stat} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(process.argv[2]||path.join(path.dirname(fileURLToPath(import.meta.url)),'../../..'));
const run=path.join(root,'run');
const keyFile=path.join(run,'questplus-web-local.key');
const world=path.join(run,'server','world');
const config=path.join(world,'questplus-hub.json');
const exists=async file=>Boolean(await stat(file).catch(()=>null));
await mkdir(run,{recursive:true});
let secret;
if(await exists(keyFile)){
 secret=(await readFile(keyFile,'utf8')).trim();
 if(!/^[a-f0-9]{64}$/.test(secret))throw Error('Existing development key is invalid; inspect run/questplus-web-local.key');
 console.log('[QuestPlus] Existing local key reused.');
}else{
 secret=randomBytes(32).toString('hex');
 await writeFile(keyFile,secret+'\n',{flag:'wx',mode:0o600});
 console.log('[QuestPlus] Local development key created (not printed).');
}
await mkdir(world,{recursive:true});
if(await exists(config)){
 console.log('[QuestPlus] Existing Minecraft world configuration kept unchanged.');
}else{
 await writeFile(config,JSON.stringify({url:'http://localhost:8787',serverKey:secret},null,2)+'\n',{flag:'wx',mode:0o600});
 console.log('[QuestPlus] Created run/server/world/questplus-hub.json for LOCAL testing.');
}
console.log('[QuestPlus] Start the local API and restart the Minecraft test server.');
