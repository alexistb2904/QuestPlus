import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import {performance} from 'node:perf_hooks';
import {setHubRegistry,hubTargets} from '../../web/editor/src/core/hub-registry.js';
import {resourceMatcher} from '../../web/editor/src/core/resource-search.js';

const root=new URL('../../',import.meta.url),out=new URL('build/benchmark/studio/',root);
const source=execFileSync('git',['show',(process.env.STUDIO_BENCHMARK_BASELINE||'2702882')+':web/editor/src/core/resource-search.js'],{cwd:root,encoding:'utf8'})
 .replace('../data/minecraft-search-labels-26.3.json',new URL('web/editor/src/data/minecraft-search-labels-26.3.json',root).href)
 .replace('./hub-registry.js',new URL('web/editor/src/core/hub-registry.js',root).href);
await writeFile(new URL('search-baseline.mjs',out),source);
const before=(await import(new URL('search-baseline.mjs',out))).resourceMatcher;
const fixture=JSON.parse(await readFile(new URL('current-large/fixture.json',out),'utf8'));
setHubRegistry(fixture.registry);
const ids=[...hubTargets('item'),...fixture.registry.tags.items],rows=[];
for(const query of ['benchmod499','resource_99','ressource','missing_resource_zzzz']){
 let expected;
 for(const [version,build] of [['before',before],['after',resourceMatcher]]){
  ids.filter(build(query));const samples=[];
  for(let i=0;i<5;i++){const start=performance.now(),found=ids.filter(build(query));samples.push(performance.now()-start);if(expected===undefined)expected=found;else assert.deepEqual(found,expected)}
  samples.sort((a,b)=>a-b);rows.push({version,query,entries:ids.length,matches:expected.length,p50:samples[2],max:samples[4]});
 }
}
await writeFile(new URL('search-results.json',out),JSON.stringify({node:process.version,rows},null,2));console.log(JSON.stringify(rows));
