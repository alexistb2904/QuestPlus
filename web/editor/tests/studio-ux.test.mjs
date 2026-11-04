import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {ITEM_IDS,BLOCK_IDS,VERSION} from '../vanilla-registry-26.3.js';
import {vanillaCatalog,itemExists,blockExists,iconUrl,versionWarning} from '../minecraft-icons.js';
import {titleSlug,uniqueId,questId,categoryId} from '../quest-ids.js';
import {addChild,setAt,atPath,wrap,leaf,renderConditionTree} from '../conditions.js';
import {demoProject,fileMap,importDocuments,validate} from '../project.js';
import {makeZip,readZip} from '../zip.js';

test('complete version-pinned Minecraft Java 26.3 registry',()=>{
 assert.equal(VERSION,'26.3');
 assert.equal(ITEM_IDS.length,1658);
 assert.equal(BLOCK_IDS.length,1286);
 assert.ok(vanillaCatalog().length>=1658);
 assert.equal(new Set(vanillaCatalog().map(x=>x.id)).size,vanillaCatalog().length);
 assert.ok(itemExists('minecraft:diamond_pickaxe'));
 assert.ok(blockExists('minecraft:stone'));
 assert.ok(vanillaCatalog().some(x=>x.id==='minecraft:stone'));
 assert.match(iconUrl('minecraft:diamond_pickaxe'),/26\.3/);
});
test('quest and category IDs derive from English titles; duplicates are hyphenated',()=>{
 const ids=new Set(['questplus:mining/break-stone','questplus:mining/break-stone-2']);
 assert.equal(titleSlug('Break Stone!'),'break-stone');
 assert.equal(questId('questplus:mining','Break Stone',ids),'questplus:mining/break-stone-3');
 assert.equal(questId('questplus:mining','Break Stone',new Set()),'questplus:mining/break-stone');
 assert.equal(categoryId('My Hero Quests',new Set()),'questplus:my-hero-quests');
 assert.equal(uniqueId('',new Set(),{random:()=> 'fixed-identity'}),'questplus:quest-fixedidentity');
 assert.match(questId('questplus:mining','🧪',new Set(),{random:()=> '1234'}),/^questplus:quest-/);
});
test('nested ET/OU/NOT prerequisites match QuestRegistry JSON grammar',()=>{
 let root=null;
 root=addChild(root,'',{quest:'questplus:a'});
 root=wrap(root,'','and');
 root=addChild(root,'',{or:[{quest:'questplus:b'},{quest:'questplus:c'}]});
 assert.deepEqual(root,{and:[{quest:'questplus:a'},{or:[{quest:'questplus:b'},{quest:'questplus:c'}]}]});
 root=wrap(root,'1','not');
 assert.deepEqual(atPath(root,'1'),{not:{or:[{quest:'questplus:b'},{quest:'questplus:c'}]}});
 root=setAt(root,'1.0.1',null);
 assert.deepEqual(atPath(root,'1.0'),{or:[{quest:'questplus:b'}]});
 root=setAt(root,'1',null);
 assert.deepEqual(root,{and:[{quest:'questplus:a'}]});
 assert.match(renderConditionTree(root,'requires',{quests:[{id:'questplus:a',title:{en_us:'First'}}]}),/Chercher une quête/);
});
test('objective conditions support conjunctions and health/dimension conditions',()=>{
 let root=addChild(null,'',leaf('objective:0','dimension'));
 root=wrap(root,'','and');
 root=addChild(root,'',{or:[{healthAtMost:2},{sneaking:true}]});
 assert.deepEqual(root,{and:[{dimension:'minecraft:overworld'},{or:[{healthAtMost:2},{sneaking:true}]}]});
 assert.match(renderConditionTree(root,'objective:0'),/Vie ≤/);
});
test('compatibility export carries future item versions separately from mod JSON',async()=>{
 const p=demoProject();
 p.targetMinecraftVersion='26.3';p.iconVersions={'minecraft:future_item':'26.4'};
 p.quests[0].icon='minecraft:future_item';
 assert.match(versionWarning(p,'minecraft:future_item'),/26\.4/);
 assert.deepEqual(validate(p),[]);
 const files=fileMap(p);
 assert.ok(files.has('queststudio-compatibility.json'));
 const metadata=JSON.parse(files.get('queststudio-compatibility.json'));
 assert.equal(metadata.targetMinecraftVersion,'26.3');
 assert.equal(metadata.iconVersions['minecraft:future_item'],'26.4');
 assert.equal(JSON.parse(files.get('quests/combat/iron_and_zombie.json')).icon,'minecraft:future_item');
 const pack=makeZip(files),documents=await readZip(pack),reimport=importDocuments(documents);
 assert.equal(reimport.iconVersions['minecraft:future_item'],'26.4');
 assert.equal(reimport.targetMinecraftVersion,'26.3');
});
test('editor source includes independent preview camera, wheel, pinch, and icon fallback',()=>{
 const editor=fs.readFileSync(new URL('../editor.js',import.meta.url),'utf8');
 const html=fs.readFileSync(new URL('../index.html',import.meta.url),'utf8');
 const css=fs.readFileSync(new URL('../style.css',import.meta.url),'utf8');
 const java=fs.readFileSync(new URL('../../../src/main/java/dev/questplus/fabric/client/QuestScreen.java',import.meta.url),'utf8');
 for(const token of ['zoomPreviewAt','gestureStart','pointermove','wheel','pointercancel','pickerLimit','renderConditionSuggestions'])assert.ok(editor.includes(token),token);
 for(const token of ['icon-filter','target-version','zoom-in','zoom-out','data-lucide'])assert.ok(html.includes(token),token);
 assert.ok(css.includes('touch-action:none'));
 assert.ok(java.includes('Items.BARRIER'));
});
