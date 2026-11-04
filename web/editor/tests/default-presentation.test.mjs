import test from 'node:test';
import assert from 'node:assert/strict';
import {BUILTIN_PROJECT} from '../src/data/default-data.js';
import {ITEM_IDS} from '../src/data/vanilla-registry-26.3.js';
import {validate} from '../src/core/project.js';

test('default quest icons use real items and reflect their objectives',()=>{
 const items=new Set(ITEM_IDS.map(id=>'minecraft:'+id));
 for(const q of BUILTIN_PROJECT.quests)assert.ok(items.has(q.icon),q.id+' '+q.icon);
 const icons=new Map(BUILTIN_PROJECT.quests.map(q=>[q.id,q.icon]));
 for(const [id,item] of Object.entries({
  'homestead/map_room':'map', 'engineering/automatic_furnace':'furnace',
  'archaeology/silent_city':'echo_shard', 'ocean/axolotl_sanctuary':'axolotl_bucket',
  'equipment/deep_helmet':'netherite_helmet', 'equipment/shadow_leggings':'netherite_leggings'
 }))assert.equal(icons.get('questplus:'+id),'minecraft:'+item,id);
 for(const c of BUILTIN_PROJECT.categories){
  const rows=BUILTIN_PROJECT.quests.filter(q=>q.category===c.id);
  assert.ok(new Set(rows.map(q=>q.icon)).size>=Math.ceil(rows.length/3),c.id);
 }
});

test('default thematic islands have explicit positions with room for nodes and labels',()=>{
 assert.equal(BUILTIN_PROJECT.quests.length,209);
 for(const category of BUILTIN_PROJECT.categories){
  const rows=BUILTIN_PROJECT.quests.filter(q=>q.category===category.id);
  for(const [i,q] of rows.entries()){
   assert.ok(Number.isFinite(q.layout?.x)&&Number.isFinite(q.layout?.y),q.id);
   for(const other of rows.slice(i+1)){
    const clearance=category.id==='questplus:equipment'?48:60;
    assert.ok(Math.hypot(q.layout.x-other.layout.x,q.layout.y-other.layout.y)>=clearance,q.id+' / '+other.id);
   }
   for(const note of category.annotations||[]){
    if(note.style==='background')continue; // Compartments intentionally enclose quests.
    const overlap=Math.abs(q.layout.x-note.x)<note.width/2+16 && Math.abs(q.layout.y-note.y)<note.height/2+16;
    assert.ok(!overlap,q.id+' / '+note.id);
   }
  }
  assert.ok(category.annotations.some(a=>a.style==='label'&&a.text.en_us&&a.text.fr_fr),category.id);
 }
 assert.deepEqual(validate(BUILTIN_PROJECT),[]);
});
