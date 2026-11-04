import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {ENCHANTMENTS_26_3,availableEnchantments} from '../src/data/vanilla-enchantments-26.3.js';
import {tUI} from '../src/ui/ui-i18n.js';
const read=p=>fs.readFileSync(new URL(p,import.meta.url),'utf8');

test('26.3 enchantment browser contains the 43 Mojang registry entries with sane levels',()=>{
 assert.equal(ENCHANTMENTS_26_3.length,43);
 assert.equal(new Set(ENCHANTMENTS_26_3.map(e=>e.id)).size,43);
 for(const entry of ENCHANTMENTS_26_3){
  assert.match(entry.id,/^minecraft:[a-z_]+$/);
  assert.ok(entry.name&&entry.group);
  assert.ok(entry.maxLevel>=1&&entry.maxLevel<=5,entry.id);
 }
 const byId=new Map(ENCHANTMENTS_26_3.map(e=>[e.id,e]));
 assert.equal(byId.get('minecraft:lunge').maxLevel,3);
 assert.equal(byId.get('minecraft:wind_burst').maxLevel,3);
 assert.equal(byId.get('minecraft:protection').maxLevel,4);
 assert.equal(byId.get('minecraft:unbreaking').maxLevel,3);
});
test('picker does not offer duplicate enchantments but lets edit current value',()=>{
 assert.ok(!availableEnchantments(['minecraft:protection']).some(e=>e.id==='minecraft:protection'));
 const edit=availableEnchantments(['minecraft:protection','minecraft:unbreaking'],'minecraft:protection');
 assert.ok(edit.some(e=>e.id==='minecraft:protection'));
 assert.ok(!edit.some(e=>e.id==='minecraft:unbreaking'));
 assert.equal(availableEnchantments([]).length,43);
});
test('modded enchantment ID remains editable and dialog/browser is localized',()=>{
 const gear=read('../src/components/GearEditor.tsx');
 const picker=read('../src/components/EnchantmentPicker.tsx');
 const tablet=read('../../../client/src/main/java/dev/questplus/fabric/client/QuestScreen.java');
 assert.match(gear,/const enchantId=\/\^\[a-z0-9_\.\-\]\+:/);
 assert.match(gear,/onBrowse=\{\(\)=>setPickEnchant\(\{item:i,previous:id\}\)\}/);
 assert.match(gear,/preserved\|\|1/);
 assert.match(gear,/tUI\('Browse enchantments'\)/);
 assert.match(picker,/availableEnchantments\(taken,replaceId\)/);
 assert.match(tablet,/drawBoundedHover\(g,mx,my\)/);
 assert.doesNotMatch(tablet,/setTooltipForNextFrame/);
 for(const locale of ['fr','es','zh'])for(const key of ['Choose enchantment','Search enchantments…','Browse enchantments','Max level'])
  assert.notEqual(tUI(key,locale),key,locale+': '+key);
});
