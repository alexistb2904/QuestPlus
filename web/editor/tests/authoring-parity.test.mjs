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
 assert.equal(report.project.quests[0].objectives.length,2);
 assert.equal(report.project.quests[0].objectives[0].event,'entity_kill');
 assert.equal(report.project.quests[0].objectives[1].event,'inventory_count');
 assert.ok(report.warnings.some(w=>w.includes('non-consuming FTB item task')));
 assert.ok(report.warnings.some(w=>w.includes('reward')));
});
test('FTB importer rejects packs with no chapters',()=>{
 assert.throws(()=>inspectFtbPack([{name:'other.json5',content:'{}'}]),/No FTB chapters/);
});

test('FTB migration handles consuming item tasks, statistics and safe dependency links',()=>{
 const chapter=`{id:'technology',title:'Progression',quests:[
  {id:'AAA',title:'Deliver iron',tasks:[{type:'item',item:{id:'minecraft:iron_ingot'},count:16,consume_items:true}],
   rewards:[{type:'item',item:{id:'minecraft:diamond',count:2}},{type:'xp_levels',amount:3}]},
  {id:'BBB',title:'Walk far',dependencies:['AAA'],tasks:[{type:'stat',stat:'minecraft:walk_one_cm',value:12500}]},
  {id:'CCC',title:'Reach base',dependencies:['BBB'],tasks:[{type:'location',dimension:'minecraft:overworld',position:[10,64,-20],radius:6}]}
 ]}`;
 const report=inspectFtbPack([{name:'chapters/tech.json5',content:chapter}]);
 assert.equal(report.questCount,3,report.warnings.join('\n'));
 const [item,stat,location]=report.project.quests;
 assert.equal(item.objectives[0].event,'item_turn_in');
 assert.deepEqual(item.rewards.map(r=>r.type),['item','experience_levels']);
 assert.equal(stat.objectives[0].event,'custom_stat');
 assert.equal(stat.requires.quest,item.id);
 assert.equal(location.requires.quest,stat.id);
 assert.deepEqual(location.objectives[0].conditions,{and:[
  {position:{xMin:4,xMax:16,yMin:58,yMax:70,zMin:-26,zMax:-14}},
  {dimension:'minecraft:overworld'}]});
 assert.deepEqual(validate(report.project),[]);
});
test('FTB importer never silently unlocks dependent quests after failed conversion',()=>{
 const chapter=`{quests:[
   {id:'A',title:'Unconverted',tasks:[{type:'advancement',advancement:'minecraft:story/mine_stone'}]},
   {id:'B',title:'Locked by A',dependencies:['A'],tasks:[{type:'kill',entity:'minecraft:zombie',value:2}]}
 ]}`;
 const report=inspectFtbPack([{name:'chapters/locked.json5',content:chapter}]);
 assert.equal(report.questCount,0);
 assert.ok(report.warnings.some(x=>x.includes('dependency')));
});
test('FTB importer rejects partial-objective quests rather than making them easier',()=>{
 const chapter=`{quests:[{id:'A',title:'Both needed',tasks:[
  {type:'kill',entity:'minecraft:blaze',value:5},
  {type:'fluid',fluid:'minecraft:water',amount:1000}
 ]}]}`;
 const report=inspectFtbPack([{name:'chapters/partial.json5',content:chapter}]);
 assert.equal(report.questCount,0);
 assert.ok(report.warnings.some(x=>x.includes('partially converted objectives')));
});
