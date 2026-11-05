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
 assert.ok(report.warnings.some(w=>w.includes('reward')));
});
test('FTB importer rejects packs with no chapters',()=>{
 assert.throws(()=>inspectFtbPack([{name:'other.json5',content:'{}'}]),/No FTB chapters/);
});

test('FTB items, statistics, locations, rewards and dependencies preserve semantics',()=>{
 const files=[{name:'chapters/progress.json5',content:`{
  id:'progress',title:'Progress',quests:[
   {id:'A',title:'Collect',tasks:[{type:'item',item:{id:'minecraft:iron_ingot'},count:8,consume_items:true}],
    rewards:[{type:'xp',xp:250},{type:'xp_levels',xp_levels:3},{type:'item',item:{id:'minecraft:diamond'},count:2}]},
   {id:'B',title:'Explore',dependencies:['A'],tasks:[
     {type:'stat',stat:'minecraft:jump',value:30},
     {type:'location',position:[100,64,-200],radius:5,dimension:'minecraft:overworld'},
     {type:'structure',structure:'minecraft:stronghold'}
   ]}
 ]}`}];
 const {project,warnings,sourceIdMap}=inspectFtbPack(files);
 assert.equal(project.quests.length,2,JSON.stringify(warnings));
 const collect=project.quests.find(q=>q.id===sourceIdMap.get('A'));
 assert.equal(collect.objectives[0].event,'item_turn_in');
 assert.deepEqual(collect.rewards.map(r=>r.type),['experience','experience_levels','item']);
 const explore=project.quests.find(q=>q.id===sourceIdMap.get('B'));
 assert.deepEqual(explore.requires,{quest:collect.id});
 assert.equal(explore.objectives[0].event,'custom_stat');
 assert.equal(explore.objectives[1].conditions.and[0].position.xMin,95);
 assert.equal(explore.objectives[2].event,'structure_visit');
 assert.deepEqual(validate(project),[]);
});
test('FTB unknown task blocks import of that quest and its dependents',()=>{
 const report=inspectFtbPack([{name:'chapters/safety.json5',content:`{id:'safety',quests:[
  {id:'A',tasks:[{type:'ftbquests:energy',value:500}]},
  {id:'B',dependencies:['A'],tasks:[{type:'kill',entity:'minecraft:zombie',value:2}]},
  {id:'C',tasks:[{type:'checkmark'}]}
 ]}`}]);
 assert.equal(report.questCount,1);
 assert.ok(report.warnings.some(w=>w.includes('skipped')));
 assert.equal(report.project.quests[0].objectives[0].event,'manual');
});

test('FTB importer distinguishes automatic possession, crafting and player hand-in',()=>{
 const content=`{id:'item_modes',consume_items:false,quests:[
  {id:'a',tasks:[{type:'item',item:{id:'minecraft:stone'},count:12}]},
  {id:'b',tasks:[{type:'item',item:{id:'minecraft:chest'},count:2,only_from_crafting:true}]},
  {id:'c',tasks:[{type:'item',item:{id:'minecraft:diamond'},count:3,consume_items:true}]},
  {id:'d',tasks:[{type:'item',item:{id:'minecraft:diamond'},count:3,task_screen_only:true}]},
  {id:'e',tasks:[{type:'advancement',advancement:'minecraft:story/mine_stone'}]},
  {id:'f',tasks:[{type:'xp',value:20,points:false}]}
 ]}`;
 const report=inspectFtbPack([{name:'chapters/items.json5',content}]);
 const modes=report.project.quests.map(q=>q.objectives[0].event);
 assert.deepEqual(modes,['inventory_count','item_craft','item_turn_in','advancement_complete','xp_levels']);
 assert.ok(report.warnings.some(w=>w.includes('Task Screen-only')));
 assert.equal(report.questCount,5);
 assert.deepEqual(validate(report.project),[]);
});

test('FTB advancement objective and reward stay type-safe',()=>{
 const {project,warnings}=inspectFtbPack([{name:'chapters/adv.json5',content:`{
  id:'adv',quests:[{id:'stone',title:'Stone Age',
   tasks:[{type:'advancement',advancement:'minecraft:story/mine_stone'}],
   rewards:[{type:'advancement',advancement:'minecraft:story/iron_tools'}]
  }]
 }`}]);
 assert.equal(project.quests.length,1,JSON.stringify(warnings));
 assert.equal(project.quests[0].objectives[0].event,'advancement_complete');
 assert.deepEqual(project.quests[0].rewards[0],{type:'questplus:advancement',data:{advancement:'minecraft:story/iron_tools'}});
 assert.deepEqual(validate(project),[]);
});

test('reward builder validates random tables, command opt-in and message',()=>{
 const copyQuest=()=>buildQuestFromTemplate(category,'questplus:authoring/rewards','Reward validation',{x:0,y:0},'basic');
 const project={categories:[category],quests:[copyQuest()],config:{schemaVersion:1,autosaveSeconds:5}};
 const q=project.quests[0];
 q.rewards=[{type:'questplus:random',data:{rolls:2,options:[
   {weight:4,type:'item',item:'minecraft:diamond',count:1},
   {weight:1,type:'experience_levels',amount:5},
 ]}},{type:'questplus:message',data:{message:'Congratulations!'}}];
 assert.deepEqual(validate(project),[]);
 q.rewards[0].data.options[0].weight=0;
 assert.ok(validate(project).some(issue=>issue.message.includes('Poids')));
 q.rewards=[{type:'questplus:loot',data:{rolls:1,emptyWeight:2,options:[
  {weight:1,type:'item',item:'minecraft:iron_ingot',count:4}
 ]}}];
 assert.deepEqual(validate(project),[]);
 q.rewards=[{type:'questplus:command',data:{command:'say well done'}}];
 assert.ok(validate(project).some(issue=>issue.message.includes('Activer')));
 project.config.enableCommandRewards=true;
 assert.deepEqual(validate(project),[]);
 q.rewards[0].data.command='say hello\\nworld';
 assert.ok(validate(project).some(issue=>issue.message.includes('Commande invalide')));
 q.rewards=[{type:'questplus:all',data:{options:[
  {weight:1,type:'item',item:'minecraft:diamond',count:1},
  {weight:1,type:'experience',amount:300}
 ]}}];
 assert.deepEqual(validate(project),[]);
});
