import test from 'node:test';
import assert from 'node:assert/strict';
import {poolProbabilities,simulatePool,poolError} from '../src/core/reward-tables.js';
import {emptyProject,category,quest,validate,fileMap,importDocuments} from '../src/core/project.js';
import {readFileSync} from 'node:fs';
import JSON5 from 'json5';

const loot={type:'questplus:loot',data:{rolls:2,emptyWeight:2,options:[
 {weight:1,type:'item',item:'minecraft:diamond',count:3},
 {weight:1,type:'experience',amount:10}
]}};
test('the shared server fixture is an exact Studio JSON5 reward export',()=>{
 const content=readFileSync(new URL('../../../server/src/test/resources/studio-rewards.json5',import.meta.url),'utf8');
 const q=JSON5.parse(content),project=emptyProject();
 project.categories.push(category(q.category,'Studio'));project.quests.push(q);
 assert.deepEqual(validate(project),[]);
 assert.equal(fileMap(project).get('quests/studio_roundtrip.json5'),content);
});
test('probabilities include empty rolls and simulation cannot mutate authored rewards',()=>{
 assert.deepEqual(poolProbabilities(loot),[.25,.25,.5]);
 assert.deepEqual(simulatePool(loot,()=>.8),[null,null]);
 const result=simulatePool(loot,()=>0);
 result[0].count=999;
 assert.equal(loot.data.options[0].count,3);
 assert.equal(poolError(loot),'');
});
test('all pools award each option; choices do not run a random simulation',()=>{
 assert.equal(simulatePool({type:'questplus:all',data:{options:loot.data.options}}).length,2);
 assert.throws(()=>simulatePool({type:'questplus:choice',data:{options:[]}}));
});
test('tables cannot nest references, executable rewards, or malformed weights',()=>{
 for(const option of [{type:'questplus:random',data:{table:'self'},weight:1},{type:'questplus:command',data:{command:'op @s'},weight:1},
  {type:'experience',amount:10,weight:1.5}])assert.ok(poolError({...loot,data:{...loot.data,options:[option]}}));
});
test('named project tables survive ZIP documents as snapshots without entering server quest/config JSON',()=>{
 const project=emptyProject(),cat=category('questplus:test','Tables');
 project.categories.push(cat);project.quests.push(quest(cat,'questplus:test','Tables'));
 project.rewardTables=[{name:'Miner loot',reward:structuredClone(loot)}];
 assert.deepEqual(validate(project),[]);
 const files=fileMap(project),loaded=importDocuments([...files].filter(([name])=>/\.json5?$/.test(name)).map(([name,content])=>({name,content})));
 assert.deepEqual(loaded.rewardTables,project.rewardTables);
 assert.ok(!files.get('config.json').includes('rewardTables'));
 assert.ok(!files.get('quests/test.json5').includes('rewardTables'));
 loaded.rewardTables[0].reward.data.options[0].count=9;
 assert.equal(project.rewardTables[0].reward.data.options[0].count,3);
 project.rewardTables.push({name:'Miner loot',reward:loot});assert.ok(validate(project).length);
});
