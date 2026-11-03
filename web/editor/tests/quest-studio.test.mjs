import test from 'node:test';
import assert from 'node:assert/strict';
import {demoProject,emptyProject,category,quest,annotation,objectiveLabel,visibleDescription,validate,fileMap,importDocuments,references} from '../project.js';
import {makeZip,readZip} from '../zip.js';

test('default demonstration project validates and exports questpack paths',()=>{
  const p=demoProject();assert.deepEqual(validate(p),[]);
  const files=fileMap(p);
  assert.ok(files.has('quests/mining/stone.json'));
  assert.ok(files.has('categories/mining.json'));
  assert.ok(files.has('config.json'));
  assert.ok(files.has('index.txt'));
  assert.ok(!files.get('quests/mining/stone.json').includes('"description"'));
});
test('description-free quest generates locale-aware objectives without repetition',()=>{
  const p=demoProject(),q=p.quests[0];
  assert.equal(objectiveLabel(q.objectives[0],'fr_fr'),'Casser 32 stone');
  assert.equal(objectiveLabel(q.objectives[0],'en_us'),'Break 32 stone');
  assert.equal(visibleDescription(q),'');
  q.description={en_us:'Break 32 stone',fr_fr:'Casser 32 stone'};
  assert.equal(visibleDescription(q,'en_us'),'');
  assert.equal(visibleDescription(q,'fr_fr'),'');
  q.objectives.push({id:'more',event:'item_craft',target:'minecraft:iron_pickaxe',count:1});
  assert.equal(visibleDescription(q,'fr_fr'),'');
  q.objectives.pop();
  q.description={en_us:'This is a custom narrative',fr_fr:'Narration supplémentaire'};
  assert.equal(visibleDescription(q),'Narration supplémentaire');
});
test('serialization and ZIP offline roundtrip preserve coordinates and annotation metadata',async()=>{
  const original=demoProject(),map=fileMap(original),zip=makeZip(map);
  const docs=await readZip(zip),after=importDocuments(docs);
  assert.equal(after.quests.length,3);assert.equal(after.categories.length,2);
  assert.deepEqual(after.quests.map(q=>q.layout),original.quests.map(q=>q.layout));
  assert.deepEqual(after.categories[0].annotations,original.categories[0].annotations);
  assert.deepEqual(validate(after),[]);
});
test('dependency cycle and orphan are rejected before export',()=>{
  const p=demoProject();
  p.quests[0].requires={quest:p.quests[2].id};
  assert.ok(validate(p).some(x=>x.message.includes('Cycle')));
  p.quests[0].requires={quest:'questplus:missing'};
  assert.ok(validate(p).some(x=>x.message.includes('introuvable')));
  assert.deepEqual(references({and:[{quest:'q:a'},{or:[{quest:'q:b'}]}]}),['q:a','q:b']);
});
test('allows custom objectives, attributes and information quests',()=>{
  const p=emptyProject(),cat=category('questplus:info');
  p.categories.push(cat);
  const q=quest(cat,'questplus:info/welcome');
  q.kind='information';q.acknowledgement='manual';q.objectives=[];q.narrative={en_us:'Read this'};
  p.quests.push(q);
  p.categories[0].annotations=[annotation('intro',20,40)];
  assert.deepEqual(validate(p),[]);
});
