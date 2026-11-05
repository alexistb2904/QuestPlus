import test from 'node:test';
import assert from 'node:assert/strict';
import {parseSnbt} from '../src/core/snbt.js';
import {inspectFtbPack} from '../src/core/ftb-import.js';
test('bounded SNBT parses compounds, numeric suffixes and arrays safely',()=>{
 const result=parseSnbt(`{id:'chapter', values:[I;1,2,3], x:1.5f, title:"hello"}`);
 assert.equal(result.x,1.5);assert.deepEqual(result.values,[1,2,3]);
 assert.throws(()=>parseSnbt('{__proto__:1}'));assert.throws(()=>parseSnbt('[B;999]'));
 assert.throws(()=>parseSnbt('['.repeat(70)+'1'+']'.repeat(70)),/limit/);
});
test('SNBT migration maps stages and blocks dependents of unknown rewards',()=>{
 const report=inspectFtbPack([{name:'chapters/a.snbt',content:`{id:'a',quests:[
 {id:'a',title:'start',tasks:[{type:'stage',stage:'hello'}],rewards:[{type:'stage',stage:'hello',remove:0b}]},
 {id:'b',dependencies:['a'],dependency_requirement:'all_started',tasks:[{type:'checkmark'}]},
 {id:'c',tasks:[{type:'checkmark'}],rewards:[{type:'thirdparty:unknown'}]},
 {id:'d',dependencies:['c'],tasks:[{type:'checkmark'}]}]}`}]);
 assert.equal(report.questCount,2);const [a,b]=report.project.quests;
 assert.equal(a.objectives[0].target,'ftb:hello');assert.equal(a.rewards[0].data.stage,'ftb:hello');
 assert.deepEqual(b.requires,{started:a.id});assert.ok(report.warnings.some(w=>w.includes('mapped to ftb:hello')));
});

test('all_started keeps every dependency while any_started keeps an alternative',()=>{
 const chapter={id:'c',quests:[{id:'a',tasks:[{type:'checkmark'}]},{id:'b',tasks:[{type:'checkmark'}]},
 {id:'all',dependencies:['a','b'],dependency_requirement:'all_started',tasks:[{type:'checkmark'}]},
 {id:'any',dependencies:['a','b'],dependency_requirement:'any_started',tasks:[{type:'checkmark'}]}]};
 const report=inspectFtbPack([{name:'chapters/c.json',content:JSON.stringify(chapter)}]);
 assert.equal(report.questCount,4);
 assert.ok(report.project.quests[2].requires.and);assert.ok(report.project.quests[3].requires.or);
 assert.equal(report.project.quests[2].requires.and.length,2);
});

test('FTB multiline SNBT accepts newline separators, comments and trailing commas',()=>{
 const report=inspectFtbPack([{name:'chapters/main.snbt',content:`{
 # FTB extended SNBT
 id: "chapter"
 title: "Chapter"
 quests: [
  {id:"a", tasks:[{type:"checkmark"}], hide_until_deps_complete:false,}
  {id:"b", tasks:[{type:"checkmark"}], dependencies:["a"]}
 ]
 }`}]);
 assert.equal(report.questCount,2);assert.equal(parseSnbt('{enabled:true}').enabled,true);
 assert.throws(()=>parseSnbt('{a:1 b:2}'),/comma/);
 assert.throws(()=>parseSnbt('{a:1\na:2}'),/Duplicate/);
});

test('distinct legacy stage names remain distinct even if their slugs match',()=>{
 const quests=['Chapter A','chapter?a','encoded/436861707465722041'].map((stage,i)=>({id:String(i),tasks:[{type:'stage',stage}],rewards:[{type:'stage',stage}]}));
 const report=inspectFtbPack([{name:'chapters/stages.json',content:JSON.stringify({id:'stages',quests})}]);
 assert.equal(report.questCount,3);assert.equal(new Set(report.project.quests.map(q=>q.objectives[0].target)).size,3);
 for(const q of report.project.quests)assert.equal(q.objectives[0].target,q.rewards[0].data.stage);
});
