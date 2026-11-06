import test from 'node:test';
import assert from 'node:assert/strict';
import {emptyProject,category,quest,validate} from '../src/core/project.js';

const sample=()=>{
 const p=emptyProject(),c=category('questplus:test','Tests');
 p.categories.push(c);
 const q=quest(c,'questplus:choice_test','Choose');
 q.rewards=[{type:'questplus:choice',data:{options:[
  {type:'item',item:'minecraft:diamond',count:2},
  {type:'experience_levels',amount:10}
 ]}}];
 p.quests.push(q);
 return p;
};
test('valid player choice between an item and XP levels',()=>{
 assert.deepEqual(validate(sample()),[]);
});
test('one option is valid; empty choices and server commands are rejected',()=>{
 const p=sample();
 p.quests[0].rewards[0].data.options.length=0;
 assert.ok(validate(p).length>0);
 p.quests[0].rewards[0].data.options.push({type:'questplus:command',command:'say hi'});
 assert.ok(validate(p).length>0);
});
test('reject invalid count, excess options, and extra fields',()=>{
 const p=sample(),reward=p.quests[0].rewards[0];
 reward.data.options[0].count=0;assert.ok(validate(p).length>0);
 reward.data.options[0].count=2;
 reward.data.options=Array.from({length:33},()=>({type:'experience',amount:10}));
 assert.ok(validate(p).length>0);
 reward.data.options=reward.data.options.slice(0,2);
 reward.data.rolls=1;
 assert.ok(validate(p).length>0);
});
