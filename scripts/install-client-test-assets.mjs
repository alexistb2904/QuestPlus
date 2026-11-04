#!/usr/bin/env node
/**
 * QuestPlus dev-client extras only (never distributed in the Fabric JAR).
 * Downloads official Modrinth versions, checks SHA-512, caches selected
 * releases and never touches a user's manually installed jars/resource packs.
 */
import {createHash} from 'node:crypto';
import {mkdir,readFile,writeFile,rename,rm,stat,readdir} from 'node:fs/promises';
import {createWriteStream} from 'node:fs';
import {Readable,Transform} from 'node:stream';
import {pipeline} from 'node:stream/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

export const MC_VERSION='26.3';
export const ASSETS=Object.freeze([
 {id:'sodium',type:'mod',loader:'fabric',ext:'.jar'},
 {id:'iris',type:'mod',loader:'fabric',ext:'.jar'},
 {id:'punchy-fpa',type:'mod',loader:'fabric',ext:'.jar'},
 {id:'punchymoves',type:'mod',loader:'fabric',ext:'.jar'},
 {id:'fluid-foolish',type:'resourcepack',ext:'.zip'},
 {id:'makeup-ultra-fast-shaders',type:'shader',ext:'.zip'},
 {id:'complementary-reimagined',type:'shader',ext:'.zip'},
 {id:'bliss-shader',type:'shader',ext:'.zip'}
]);
const MAX_SIZE=100*1024*1024,API='https://api.modrinth.com/v2';
const timeout=()=>AbortSignal.timeout(35000);
const safeName=id=>id.replace(/[^a-z0-9_-]/g,'-');
const userAgent='QuestPlus-Development/0.2 (+https://github.com/alexistb2904/QuestPlus)';
export function selectVersion(versions,asset){
 if(!Array.isArray(versions))throw Error('Invalid Modrinth version list for '+asset.id);
 const available=versions.filter(v=>v&&v.game_versions?.includes(MC_VERSION)&&
  (!asset.loader||v.loaders?.includes(asset.loader))&&v.files?.some(f=>f.filename?.toLowerCase().endsWith(asset.ext)));
 const ranked=available.sort((a,b)=>{
  const priority=x=>x.version_type==='release'?2:x.version_type==='beta'?1:0;
  return priority(b)-priority(a)||String(b.date_published||'').localeCompare(String(a.date_published||''));
 });
 const found=ranked.find(v=>v.version_type!=='alpha');
 if(!found)throw Error('No stable or beta '+MC_VERSION+' '+(asset.loader||asset.type)+' version for '+asset.id);
 return found;
}
export function selectFile(version,asset){
 const files=version.files.filter(f=>f.filename?.toLowerCase().endsWith(asset.ext));
 const chosen=files.find(f=>f.primary)||files[0];
 if(!chosen?.hashes?.sha512||!/^[a-f0-9]{128}$/i.test(chosen.hashes.sha512))
  throw Error('Missing SHA-512 for '+asset.id);
 const url=new URL(chosen.url);
 if(url.protocol!=='https:'||!['cdn.modrinth.com','cdn-raw.modrinth.com'].includes(url.hostname))
  throw Error('Untrusted download host for '+asset.id);
 if(chosen.size>MAX_SIZE)throw Error('Oversized '+asset.id+' download');
 return chosen;
}
async function apiVersions(asset){
 const url=new URL(API+'/project/'+asset.id+'/version');
 url.searchParams.set('game_versions',JSON.stringify([MC_VERSION]));
 if(asset.loader)url.searchParams.set('loaders',JSON.stringify([asset.loader]));
 const res=await fetch(url,{headers:{'User-Agent':userAgent},signal:timeout()});
 if(!res.ok)throw Error('Modrinth returned HTTP '+res.status+' for '+asset.id);
 return res.json();
}
async function hashFile(file){
 const {createReadStream}=await import('node:fs');
 const {createHash}=await import('node:crypto');
 const h=createHash('sha512');
 for await(const part of createReadStream(file))h.update(part);
 return h.digest('hex');
}
const present=async file=>Boolean(await stat(file).catch(()=>null));
export async function installAsset(asset,root,record=null,{update=false}={}){
 const folder=asset.type==='mod'?'mods':asset.type==='shader'?'shaderpacks':'resourcepacks';
 const destDir=path.join(root,'run','client',folder);
 await mkdir(destDir,{recursive:true});
 if(!update&&record?.file&&record?.sha512){
  const existing=path.join(destDir,path.basename(record.file));
  if(record.folder===folder&&record.project===asset.id&&
     path.basename(record.file)===record.file&&await present(existing)){
   if((await hashFile(existing)).toLowerCase()===record.sha512.toLowerCase()){
    console.log('[OK] Cached: '+asset.id+' ('+record.version+')');
    return record;
   }
  }
 }
 const release=selectVersion(await apiVersions(asset),asset);
 const file=selectFile(release,asset);
 const name='questplus-test-'+safeName(asset.id)+'-'+safeName(release.id)+asset.ext;
 const target=path.join(destDir,name),expected=file.hashes.sha512.toLowerCase();
 if(await present(target)){
  if((await hashFile(target)).toLowerCase()===expected){
   console.log('[OK] Already installed: '+asset.id);
   return {project:asset.id,version:release.version_number,versionId:release.id,sha512:expected,folder,file:name};
  }
  throw Error('Checksum mismatch on existing '+target+'; refusing to overwrite');
 }
 console.log('[DOWNLOAD] '+asset.id+' -> '+folder+' (Minecraft '+MC_VERSION+')');
 const response=await fetch(file.url,{headers:{'User-Agent':userAgent},signal:timeout()});
 if(!response.ok||!response.body)throw Error('Download failed: '+asset.id+' HTTP '+response.status);
 const real=new URL(response.url);
 if(real.protocol!=='https:'||!['cdn.modrinth.com','cdn-raw.modrinth.com'].includes(real.hostname))
  throw Error('Unexpected download redirect for '+asset.id);
 const tmp=target+'.partial-'+process.pid;
 const sha=createHash('sha512');let total=0;
 try{
  await pipeline(Readable.fromWeb(response.body),new Transform({
   transform(chunk,encoding,cb){total+=chunk.length;
    if(total>MAX_SIZE)return cb(new Error('Download too large'));
    sha.update(chunk);cb(null,chunk);
   }
  }),createWriteStream(tmp,{flags:'wx'}));
  if(sha.digest('hex')!==expected)throw Error('SHA-512 mismatch: '+asset.id);
  await rename(tmp,target);
 }catch(e){await rm(tmp,{force:true});throw e}
 const entry={project:asset.id,version:release.version_number,versionId:release.id,sha512:expected,folder,file:name};
 // Only delete a previous file created by THIS tool. Never touch user mods.
 if(record?.file&&record.project===asset.id&&record.folder===folder&&record.file!==name&&
    /^questplus-test-[a-z0-9_-]+-[a-z0-9_-]+\.(jar|zip)$/.test(record.file)){
  await rm(path.join(destDir,record.file),{force:true});
 }
 console.log('[INSTALLED] '+asset.id+': '+release.version_number);
 return entry;
}
export async function installAll(root,{update=false}={}){
 const manifestFile=path.join(root,'run','client','questplus-test-assets.json');
 let current={};
 try{const data=JSON.parse(await readFile(manifestFile,'utf8'));if(data.gameVersion===MC_VERSION)current=data.entries||{}}catch{}
 const out={...current},errors=[];
 for(const asset of ASSETS){
  try{
   out[asset.id]=await installAsset(asset,root,current[asset.id],{update});
   await mkdir(path.dirname(manifestFile),{recursive:true});
   await writeFile(manifestFile,JSON.stringify({gameVersion:MC_VERSION,entries:out},null,2)+'\n');
  }catch(e){errors.push(asset.id+': '+e.message);console.error('[FAILED] '+asset.id+': '+e.message)}
 }
 if(errors.length)throw Error(errors.length+' test asset(s) not installed. '+errors.join(' | '));
 console.log('\n[READY] Iris + Sodium, Punchy!, Punchy! Moves, Fluid & Foolish and 3 shaders.');
 console.log('[INFO] Choose one shader in Iris. Enable Fluid & Foolish under Resource Packs.');
 console.log('[INFO] Punchy! and the QuestPlus tablet first-person renderer may conflict: test both.');
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const root=path.resolve(process.argv[2]||path.join(path.dirname(fileURLToPath(import.meta.url)),'..'));
 installAll(root,{update:process.argv.includes('--update')}).catch(error=>{
  console.error('[QuestPlus] '+error.message);
  process.exitCode=1;
 });
}
