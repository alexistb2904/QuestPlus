import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdtemp,rm,readFile,readdir} from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import {ASSETS,MC_VERSION,selectVersion,selectFile,installAsset} from '../../../scripts/install-client-test-assets.mjs';

test('all requested client mods, animation resource pack and 3 shaders are configured for 26.3',()=>{
 assert.equal(MC_VERSION,'26.3');
 assert.deepEqual(ASSETS.filter(x=>x.type==='shader').map(x=>x.id),
  ['makeup-ultra-fast-shaders','complementary-reimagined','bliss-shader']);
 assert.deepEqual(ASSETS.filter(x=>x.type==='mod').map(x=>x.id),
  ['sodium','iris','punchy-fpa','punchymoves']);
 assert.deepEqual(ASSETS.filter(x=>x.type==='resourcepack').map(x=>x.id),['fluid-foolish']);
 assert.ok(ASSETS.every(x=>x.ext===(x.type==='mod'?'.jar':'.zip')));
});
test('version selection rejects other Minecraft versions and loaders; prefers stable release',()=>{
 const mod=ASSETS[2];
 const make=(type,game,loaders,id)=>({
  id,version_type:type,game_versions:[game],loaders,date_published:'2026-10-01T00:00:00Z',
  files:[{filename:'punchy-fpa.jar',hashes:{sha512:'a'.repeat(128)}}]});
 const versions=[
  make('release','26.2',['fabric'],'wrong-game'),
  make('release','26.3',['forge'],'wrong-loader'),
  make('beta','26.3',['fabric'],'beta'),
  make('release','26.3',['fabric'],'release')
 ];
 assert.equal(selectVersion(versions,mod).id,'release');
 assert.throws(()=>selectVersion([versions[0],versions[1]],mod),/No stable or beta/);
});
test('download metadata is restricted to Modrinth CDN and verified SHA512',()=>{
 const mod=ASSETS[2],url='https://cdn.modrinth.com/data/abc/versions/xyz/punchy.jar';
 const versions={files:[{primary:true,filename:'punchy.jar',size:1024,url,
  hashes:{sha512:'a'.repeat(128)}}]};
 assert.equal(selectFile(versions,mod).url,url);
 assert.throws(()=>selectFile({...versions,files:[{...versions.files[0],url:'https://evil.example/mod.jar'}]},mod),/Untrusted/);
 assert.throws(()=>selectFile({...versions,files:[{...versions.files[0],hashes:{sha512:'aaa'}}]},mod),/Missing SHA-512/);
 assert.throws(()=>selectFile({...versions,files:[{...versions.files[0],size:200_000_000}]},mod),/Oversized/);
});
test('client asset installation caches verified downloads and never fetches again while cached',async()=>{
 const root=await mkdtemp(path.join(os.tmpdir(),'questplus-client-assets-'));
 const before=globalThis.fetch;
 const content=Buffer.from('test-archive-valid-sha512');
 const expected=createHash('sha512').update(content).digest('hex');
 let calls=0;
 globalThis.fetch=async url=>{
  calls++;
  const target=String(url);
  if(target.startsWith('https://api.modrinth.com/v2/project/')){
   return new Response(JSON.stringify([{id:'unit-test-version',version_number:'1.2.3',version_type:'release',
    date_published:'2026-10-01T00:00:00Z',game_versions:['26.3'],loaders:['fabric'],
    files:[{primary:true,filename:'punchy.jar',url:'https://cdn.modrinth.com/data/example/versions/demo/punchy.jar',
      size:content.length,hashes:{sha512:expected}}]}]),{status:200});
  }
  return new Response(content,{status:200});
 };
 try{
  const asset=ASSETS.find(x=>x.id==='punchy-fpa');
  const installed=await installAsset(asset,root);
  assert.equal(installed.project,'punchy-fpa');
  assert.equal(installed.sha512,expected);
  const dest=path.join(root,'run/client/mods');
  assert.deepEqual(await readdir(dest),[installed.file]);
  assert.deepEqual(await readFile(path.join(dest,installed.file)),content);
  const cached=await installAsset(asset,root,installed);
  assert.deepEqual(cached,installed);
  assert.equal(calls,2,'no re-download when already cached');
 }finally{globalThis.fetch=before;await rm(root,{recursive:true,force:true})}
});
