import test from 'node:test';
import assert from 'node:assert/strict';
import {QUEST_TEMPLATES,buildQuestFromTemplate} from '../src/core/quest-templates.js';
import {validate} from '../src/core/project.js';
import {inspectFtbPack} from '../src/core/ftb-import.js';

const category={id:'questplus:authoring',icon:'minecraft:book',title:{en_us:'Authoring'},annotations:[]};
test('every quest template validates against Studio schema',()=>{
 for(const {id} of QUEST_TEMPLATES){
   const q=buildQuestFromTemplate(category,'questplus:authoring/'+id,'Template '+id,{x:0,y:0},id);
   const problems=validate({categories:[category],quests:[q],config:{schemaVersion:1,autosaveSeconds:5}});
   assert.deepEqual(problems,[],id+': '+JSON.stringify(problems));
 }
});
test('FTB preview imports only supported semantics and reports unsupported ones',()=>{
 const input=`// Json5: imported chapters may contain comments
 {id:'legacy',title:'Mobs',quests:[
   {id:'abc',title:'Hunt',x:20,y:100,tasks:[
     {type:'ftbquests:kill',entity:'minecraft:zombie',count:3},
     {type:'ftbquests:item',item:'minecraft:diamond',count:4}
   ],rewards:[{type:'ftbquests:command',command:'give @p diamond'}]}
 ]}`;
 const report=inspectFtbPack([{name:'chapters/mobs.json5',content:input}]);
 assert.equal(report.questCount,1);
 assert.equal(report.project.quests[0].objectives.length,1);
 assert.equal(report.project.quests[0].objectives[0].event,'entity_kill');
 assert.ok(report.warnings.some(w=>w.includes('ftbquests:item')));
 assert.ok(report.warnings.some(w=>w.includes('reward')));
});
test('FTB importer rejects packs with no chapters',()=>{
 assert.throws(()=>inspectFtbPack([{name:'other.json5',content:'{}'}]),/No FTB chapters/);
});
