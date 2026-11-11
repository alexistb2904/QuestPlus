import {installAsset,ASSETS,repositoryRoot} from './install-client-test-assets.mjs';
import {readFile,writeFile,mkdir,readdir} from 'node:fs/promises';
import {spawnSync,execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

export const PROFILES=Object.freeze({
 baseline:['sodium','iris'],
 punchy:['sodium','iris','punchy-fpa','punchymoves','fluid-foolish','3dskinlayers'],
 nea:['sodium','iris','not-enough-animations','3dskinlayers'],
 firstperson:['sodium','iris','not-enough-animations','first-person-model','3dskinlayers'],
 fresh:['sodium','iris','entity-model-features','entitytexturefeatures','fresh-animations','fa-player-extension'],
 blocks:['sodium','iris']
});
export const COMPAT_ASSETS=Object.freeze([...ASSETS,
 ...['not-enough-animations','first-person-model','3dskinlayers','entity-model-features','entitytexturefeatures'].map(id=>({id,type:'mod',loader:'fabric',ext:'.jar'})),
 ...['fresh-animations','fa-player-extension'].map(id=>({id,type:'resourcepack',ext:'.zip'})),
 ...['complementary-unbound','photon-shader','bsl-shaders','sildurs-vibrant-shaders','solas-shader'].map(id=>({id,type:'shader',ext:'.zip'}))
]);

const SHADERS=COMPAT_ASSETS.filter(a=>a.type==='shader').length;
export function validateGpuLog(log,profile){
 if(profile==='blocks'){
  if(!log.includes('WORLD BLOCKS SHADER MATRIX PASS '+SHADERS))throw Error('Incomplete world blocks shader matrix');
 }else{
  // Vanilla plus every pack, each lit and unlit.
  if((log.match(/TABLET COMPAT PASS /g)||[]).length!==2*(1+SHADERS))throw Error('Incomplete shader matrix');
  if(!log.includes('TABLET MOVEMENT AND REMOTE POSE PASS'))throw Error('Missing movement/remote pose validation');
 }
 if(profile==='punchy'&&!log.includes('TABLET PUNCHY REBIND PASS'))throw Error('Missing Punchy shortcut validation');
 if(/Tablet (opening|close) animation timed out|ShaderCompileException|Failed to create shader rendering pipeline|falling back to normal rendering without shaders|Iris hand pass API is unavailable|Cannot identify Iris hand pass/i.test(log))
  throw Error('Shader or animation fallback in GPU log');
}

async function main(args){
 // --mc=<mod/versions folder> picks the Minecraft target; the newest folder by default.
 const root=repositoryRoot();
 const targets=(await readdir(path.join(root,'mod','versions'),{withFileTypes:true})).filter(d=>d.isDirectory()).map(d=>d.name)
  .sort((a,b)=>a.localeCompare(b,undefined,{numeric:true}));
 const target=(args.find(a=>a.startsWith('--mc='))||'--mc='+targets.at(-1)).slice(5);
 if(!targets.includes(target))throw Error('Unknown Minecraft target: '+target+' (known: '+targets.join(', ')+')');
 // --minecraft=<version> runs that target on another release it declares (e.g. 26.1.1 for 26.1).
 const minecraft=args.find(a=>a.startsWith('--minecraft='))?.slice(12)||(await readFile(path.join(root,'mod','versions',target,'gradle.properties'),'utf8'))
  .match(/^minecraft_version=(.+)$/m)[1].trim();
 process.env.QUESTPLUS_MC_VERSION=minecraft;
 let profiles=args.filter(a=>!a.startsWith('--'));
 if(!profiles.length)profiles=Object.keys(PROFILES);
 for(const profile of profiles)if(!Object.hasOwn(PROFILES,profile))throw Error('Unknown profile: '+profile);
 const cache=path.join(root,'mod','build','tablet-assets',minecraft);
 await mkdir(cache,{recursive:true});
 let records={};
 try{records=JSON.parse(await readFile(path.join(cache,'manifest.json'),'utf8'))}catch{}
 const needed=new Set(profiles.flatMap(p=>PROFILES[p]));
 for(const asset of COMPAT_ASSETS.filter(a=>a.type==='shader'||needed.has(a.id))){
  records[asset.id]=await installAsset(asset,cache,records[asset.id]);
  await writeFile(path.join(cache,'manifest.json'),JSON.stringify(records,null,2)+'\n');
 }
 const java=execFileSync('powershell.exe',['-NoProfile','-File',path.join(root,'scripts','Find-Java25.ps1')],{encoding:'utf8'}).trim();
 const failed=[];
 for(const profile of profiles){
  console.log('\n[TABLET MATRIX] Minecraft '+process.env.QUESTPLUS_MC_VERSION+' '+profile);
  const result=spawnSync('cmd.exe',['/d','/c',`"${path.join(root,'mod','gradlew.bat')}" --console=plain runClientGameTest -Pmc=${target} -Pminecraft_version=${minecraft} -I ../scripts/tablet-compatibility.gradle -PtabletCompatibility=${profile}`],
   // Verbatim: Node would otherwise escape the quotes around the wrapper path for cmd.exe.
   {cwd:path.join(root,'mod'),stdio:'inherit',windowsVerbatimArguments:true,env:{...process.env,JAVA_HOME:java}});
  if(result.error)throw result.error;
  if(result.status!==0)failed.push(profile);
  else {
   try {validateGpuLog(await readFile(path.join(root,'mod','build','tablet-compat',minecraft,profile,'logs','latest.log'),'utf8'),profile)}
   catch(e){console.error(profile+': '+e.message);failed.push(profile)}
  }
 }
 if(failed.length)throw Error('Failed GPU profiles: '+failed.join(', '));
 console.log('[PASS] '+profiles.join(', ')+'. Screenshots and logs: build/tablet-compat/'+minecraft+'/<profile>/');
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))
 main(process.argv.slice(2)).catch(e=>{console.error(e.message);process.exitCode=1});
