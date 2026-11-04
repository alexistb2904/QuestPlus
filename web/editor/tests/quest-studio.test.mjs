import test from 'node:test';
import assert from 'node:assert/strict';
import {demoProject,emptyProject,category,quest,annotation,objectiveLabel,visibleDescription,
  validate,fileMap,importDocuments,references} from '../src/core/project.js';
import {BUILTIN_PROJECT} from '../src/data/default-data.js';
import {gameLayout,gameFit,NODE_SIZE,HORIZONTAL_GAP,VERTICAL_GAP} from '../src/core/game-layout.js';
import {COMMON_ITEMS,isVanilla,iconUrl,blockIconUrl} from '../src/ui/minecraft-icons.js';
import {makeZip,readZip} from '../src/core/zip.js';

test('new editor sessions start with EXACT quest definitions shipped by QuestPlus',()=>{
  const p=demoProject();
  assert.deepEqual(p,BUILTIN_PROJECT);
  assert.equal(p.categories.length,16);
  assert.equal(p.quests.length,209);
  assert.deepEqual(validate(p),[]);
  const files=fileMap(p);
  assert.ok(files.has('quests/quarries/stone_mountain.json'));
  assert.ok(files.has('quests/equipment/equipment_master.json'));
  assert.ok(files.has('quests/trials/three_giants.json'));
  assert.ok(files.has('categories/equipment.json'));
  assert.ok(!files.has('categories/combat.json'));
  assert.ok(!files.has('quests/survival/welcome.json'));
  assert.ok(files.has('config.json'));assert.ok(files.has('index.txt'));
  assert.deepEqual(JSON.parse(files.get('quests/quarries/stone_mountain.json')),
    BUILTIN_PROJECT.quests.find(q=>q.id==='questplus:quarries/stone_mountain'));
  p.quests[0].title.en_us='Edited';
  assert.notEqual(p.quests[0].title.en_us,demoProject().quests[0].title.en_us);
});
test('English is the only required authoring language; translations are optional',()=>{
  const cat=category('questplus:work','Work'),q=quest(cat,'questplus:work/stone','Stone');
  assert.deepEqual(cat.title,{en_us:'Work'});
  assert.deepEqual(q.title,{en_us:'Stone'});
  const p=emptyProject();p.categories.push(cat);p.quests.push(q);
  assert.deepEqual(validate(p),[]);
  q.title.de_de='Stein';
  q.title.ja_jp='石';
  assert.deepEqual(validate(p),[]);
  assert.equal(q.title.de_de,'Stein');
});
test('description-free quests show generated objectives only once',()=>{
  const cat=category('questplus:work'),q=quest(cat,'questplus:work/cobble');
  q.objectives=[{id:'stone',event:'block_break',target:'minecraft:cobblestone',count:32}];
  assert.equal(objectiveLabel(q.objectives[0],'fr_fr'),'Casser 32 cobblestone');
  assert.equal(objectiveLabel(q.objectives[0],'en_us'),'Break 32 cobblestone');
  assert.equal(visibleDescription(q,'en_us'),'');
  q.description={en_us:'Break 32 cobblestone',fr_fr:'Casser 32 cobblestone'};
  assert.equal(visibleDescription(q,'en_us'),'');
  assert.equal(visibleDescription(q,'fr_fr'),'');
  q.description={en_us:'A custom adventure',fr_fr:'Une aventure'};
  assert.equal(visibleDescription(q,'en_us'),'A custom adventure');
});
test('ZIP import/export preserves the shipped data and edited world coordinates',async()=>{
  const p=demoProject();p.quests[0].layout={x:-128,y:96};
  p.categories[0].annotations=[annotation('intro',20,40)];
  p.categories[0].annotations[0].text.ja_jp='はじめ';
  const zip=makeZip(fileMap(p)),docs=await readZip(zip),after=importDocuments(docs);
  assert.equal(after.quests.length,209);assert.equal(after.categories.length,16);
  assert.deepEqual(after.quests.map(q=>q.layout),p.quests.map(q=>q.layout));
  assert.deepEqual(after.categories[0].annotations,p.categories[0].annotations);
  assert.deepEqual(validate(after),[]);
});
test('Minecraft automatic layout uses 32px nodes, 64x72 spacing and correct prerequisite links',()=>{
  assert.equal(NODE_SIZE,32);assert.equal(HORIZONTAL_GAP,64);assert.equal(VERTICAL_GAP,72);
  const cat=category('questplus:test'),a=quest(cat,'questplus:test/a','A'),b=quest(cat,'questplus:test/b','B'),c=quest(cat,'questplus:test/c','C');
  delete a.layout;delete b.layout;delete c.layout;c.requires={quest:a.id};
  const layout=gameLayout([a,b,c]);
  assert.deepEqual(layout.points.get(a.id),{x:-32,y:0});
  assert.deepEqual(layout.points.get(b.id),{x:32,y:0});
  assert.deepEqual(layout.points.get(c.id),{x:0,y:72});
  assert.deepEqual(layout.edges,[{from:a.id,to:c.id}]);
  a.layout={x:248,y:120};
  assert.deepEqual(gameLayout([a,b,c]).points.get(a.id),a.layout);
  const fit=gameFit(layout.points.values(),512,320);
  assert.ok(fit.zoom>=.25&&fit.zoom<=2.5);
});
test('cycles, unknown dependencies and extension IDs are handled correctly',()=>{
  const cat=category('questplus:test'),a=quest(cat,'questplus:test/a'),b=quest(cat,'questplus:test/b');
  const p=emptyProject();p.categories=[cat];p.quests=[a,b];a.requires={quest:b.id};b.requires={quest:a.id};
  assert.ok(validate(p).some(x=>x.message.includes('Cycle')));
  assert.throws(()=>gameLayout(p.quests),/cycle/);
  b.requires={quest:'questplus:missing'};
  assert.ok(validate(p).some(x=>x.message.includes('introuvable')));
  assert.deepEqual(references({and:[{quest:'q:a'},{or:[{quest:'q:b'}]}]}),['q:a','q:b']);
  delete a.requires;delete b.requires;
  a.icon='create:brass_ingot';assert.deepEqual(validate(p),[]);
});
test('mod icons preserve resource IDs and vanilla item assets use 26.3',()=>{
  assert.ok(COMMON_ITEMS.includes('iron_pickaxe'));
  assert.equal(isVanilla('minecraft:diamond'),true);
  assert.equal(isVanilla('create:brass_ingot'),false);
  assert.ok(iconUrl('minecraft:diamond').includes('26.3'));
  assert.ok(blockIconUrl('minecraft:cobblestone').includes('/block/cobblestone.png'));
  assert.equal(iconUrl('create:brass_ingot'),'');
});
test('information quests and annotation localization validate',()=>{
  const p=emptyProject(),cat=category('questplus:info');
  p.categories.push(cat);
  const q=quest(cat,'questplus:info/welcome');
  q.kind='information';q.acknowledgement='manual';q.objectives=[];q.narrative={en_us:'Read this'};
  p.quests.push(q);
  p.categories[0].annotations=[annotation('intro',20,40)];
  assert.deepEqual(validate(p),[]);
});

test('tiered quests validate thresholds, reward bounds and export round trip',async()=>{
 const p=emptyProject(),cat=category('questplus:tiers'),q=quest(cat,'questplus:tiers/stone');
 p.categories.push(cat);p.quests.push(q);
 q.progression='tiered';q.objectives[0].count=16;q.afterLast='repeat';
 q.tiers=[{id:'first',threshold:1,rewards:[{type:'item',item:'minecraft:diamond',count:2,text:{en_us:'Diamond'}}]},
          {id:'second',threshold:5,rewards:[{type:'experience',amount:20}]}];
 assert.deepEqual(validate(p),[]);
 const after=importDocuments(await readZip(makeZip(fileMap(p))));
 assert.deepEqual(after.quests[0],q);assert.deepEqual(validate(after),[]);
 const cases=[
  [x=>x.tiers[1].id='first','ID de palier'],
  [x=>x.tiers[1].threshold=1,'Seuils de paliers'],
  [x=>x.tiers[1].threshold=1000000000,'Quantité d’objectif'],
  [x=>x.tiers[0].rewards[0].count=0,'Récompense objet'],
  [x=>x.tiers[0].rewards[0].text.fr_fr='','Texte récompense'],
  [x=>x.afterLast='forever','dernier palier'],
  [x=>x.progression='once','Liste de paliers']
 ];
 for(const [edit,message] of cases){
  const broken=structuredClone(p);edit(broken.quests[0]);
  assert.ok(validate(broken).some(issue=>issue.message.includes(message)),message);
 }
});

test('information, timers and structured conditions reject definitions the server cannot parse',()=>{
 const p=emptyProject(),cat=category('questplus:rules'),q=quest(cat,'questplus:rules/test');
 p.categories.push(cat);p.quests.push(q);
 q.objectives[0].conditions={and:[{equipment:{slot:'mainhand',item:'minecraft:stick'}},{position:{xMin:0,xMax:5}},{context:{key:'questplus:test',equals:true}}]};
 q.noDeath=true;q.cooldownSeconds=0;q.timeLimitSeconds=60;
 assert.deepEqual(validate(p),[]);
 const cases=[
  [x=>x.objectives[0].conditions.and[0].equipment.slot='invalid','Condition'],
  [x=>x.objectives[0].conditions.and[1].position.xMax=-1,'Condition'],
  [x=>x.objectives[0].conditions.and[2].context.key='','Condition'],
  [x=>x.timeLimitSeconds=0,'Limite de temps'],
  [x=>x.noDeath='yes','sans mort'],
  [x=>x.acknowledgement='manual','lecture'],
  [x=>{x.kind='information';x.objectives=[];x.noDeath=false;delete x.timeLimitSeconds;x.acknowledgement='read_only';x.rewards=[{type:'experience',amount:1}]},'aucune récompense']
 ];
 for(const [edit,message] of cases){
  const broken=structuredClone(p);edit(broken.quests[0]);
  assert.ok(validate(broken).some(issue=>issue.message.includes(message)),message);
 }
});
