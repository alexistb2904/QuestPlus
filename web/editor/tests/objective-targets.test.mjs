import test from 'node:test';
import assert from 'node:assert/strict';
import {DIMENSIONS,BIOMES,STRUCTURES,ENTITIES,MARKERS,targetKind,initialTarget,coordinateBounds,positionValue,OBJECTIVE_UI,EVENT_LABELS,objectiveUi,changeObjectiveEvent,positionCoordinate,positionRadius,setPositionCoordinate,setPositionRadius} from '../src/core/objective-targets.js';
import {emptyProject,category,quest,validate,EVENT_KEYS,text,annotationText,annotationRich} from '../src/core/project.js';

test('objective event controls distinguish modded registry types',()=>{
 assert.equal(targetKind('block_break'),'block');
 assert.equal(targetKind('block_place'),'block');
 assert.equal(targetKind('item_smelt'),'item');
 assert.equal(targetKind('item_use'),'item');
 assert.equal(targetKind('entity_kill'),'entity');
 assert.equal(targetKind('structure_visit'),'structure');
 assert.equal(targetKind('biome_visit'),'biome');
 assert.equal(targetKind('location'),null);
 assert.ok(DIMENSIONS.some(x=>x[0]==='minecraft:the_nether'));
 assert.ok(BIOMES.includes('minecraft:cherry_grove'));
 assert.ok(STRUCTURES.includes('minecraft:trial_chambers'));
 assert.ok(ENTITIES.includes('minecraft:warden'));
});
test('marker targets and distance use the exact engine format',()=>{
 assert.equal(initialTarget('location'),MARKERS.location);
 assert.equal(initialTarget('travel_distance'),MARKERS.travel_distance);
 assert.equal(initialTarget('player_death'),MARKERS.player_death);
 assert.equal(initialTarget('biome_visit'),'minecraft:plains');
 assert.equal(initialTarget('block_break'),'minecraft:stone');
});
test('XYZ coordinates become valid condition bounds without erasing other conditions',()=>{
 const initial={sneaking:true};
 const x=coordinateBounds(initial,'x','12');
 assert.equal(positionValue(x,'x'),12);
 assert.deepEqual(x.and[0],initial);
 const y=coordinateBounds(x,'y','65');
 assert.equal(positionValue(y,'y'),65);
 assert.equal(y.and.length,2);
 assert.deepEqual(y.and[1],{position:{xMin:12,xMax:12,yMin:65,yMax:65}});
});

test('wrong vanilla biome and missing XYZ coordinates produce explicit editor errors',()=>{
 const p=emptyProject(),c=category('questplus:travel'),q=quest(c,'questplus:travel/first');
 p.categories.push(c);p.quests.push(q);
 q.objectives[0].event='biome_visit';q.objectives[0].target='minecraft:stone';
 assert.ok(validate(p).some(issue=>issue.message.includes('Biome vanilla inconnu')));
 q.objectives[0].target='minecraft:plains';assert.deepEqual(validate(p),[]);
 q.objectives[0].event='location';q.objectives[0].target='questplus:position';
 assert.ok(validate(p).some(issue=>issue.message.includes('renseigner X')));
 q.objectives[0].conditions={position:{xMin:1,xMax:1,yMin:64,yMax:64,zMin:5,zMax:5}};
 assert.deepEqual(validate(p),[]);
});

test('every built-in QuestPlus event has a distinct supported editor contract',()=>{
 const contracts=Object.keys(OBJECTIVE_UI).sort();
 assert.deepEqual(contracts,[...EVENT_KEYS].sort());
 assert.deepEqual(Object.keys(EVENT_LABELS).sort(),contracts);
 const expected={
   block_break:'block',block_place:'block',block_interact:'block',
   item_obtained:'item',item_craft:'item',item_smelt:'item',item_use:'item',
   item_consume:'item',fish_item:'item',trade:'item',inventory_count:'item',
   entity_kill:'entity',fish_entity:'entity',tame_animal:'entity',breed_animal:'entity',npc_interact:'entity',
   biome_visit:'biome',structure_visit:'structure',dimension_enter:'dimension',
   travel_distance:'distance',location:'position',player_death:'death',manual:'manual'
 };
 for(const [event,kind] of Object.entries(expected)){
   assert.equal(objectiveUi(event).kind,kind,event);
   assert.ok(EVENT_LABELS[event],event);
 }
 assert.equal(objectiveUi('othermod:custom_event').kind,'custom');
});
test('event conversion resets marker targets and preserves non-position conditions',()=>{
 const o={id:'step',event:'location',target:'questplus:position',count:1,conditions:{and:[{sneaking:true},{position:{xMin:3,xMax:5}}]}};
 const item=changeObjectiveEvent(o,'item_smelt','questplus:test/quest');
 assert.equal(item.target,'minecraft:stick');
 assert.equal(item.count,1);
 assert.deepEqual(item.conditions,{sneaking:true});
 const manual=changeObjectiveEvent(item,'manual','questplus:test/quest');
 assert.equal(manual.target,'questplus:test/quest');
 assert.equal(manual.count,1);
 const distance=changeObjectiveEvent(item,'travel_distance','questplus:test/quest');
 assert.equal(distance.count,100);
});
test('position fields accept a reachable radius, retain coordinates and preserve extra conditions',()=>{
 let condition={sneaking:true};
 condition=setPositionCoordinate(condition,'x','100');
 condition=setPositionCoordinate(condition,'y','64');
 condition=setPositionCoordinate(condition,'z','-12');
 assert.equal(positionCoordinate(condition,'x'),100);
 assert.equal(positionCoordinate(condition,'y'),64);
 assert.equal(positionCoordinate(condition,'z'),-12);
 assert.equal(positionRadius(condition),2);
 condition=setPositionRadius(condition,4);
 assert.deepEqual(condition.and[1].position,{xMin:96,xMax:104,yMin:60,yMax:68,zMin:-16,zMax:-8});
 assert.deepEqual(condition.and[0],{sneaking:true});
 assert.equal(setPositionCoordinate(condition,'x','').and[1].position.xMin,undefined);
});
test('missing English annotation text and rich media fall back to authored French',()=>{
 const note={text:{en_us:'Note',fr_fr:'Bah je test'},rich:{fr_fr:[{type:'text',text:'IMAGE'},{type:'image',data:'data:image/png;base64,YWJj',width:64,height:64}]}};
 assert.equal(annotationText(note,'en_us'),'Bah je test');
 assert.equal(annotationText(note,'es_es'),'Bah je test');
 assert.equal(annotationRich(note,'en_us')[1].type,'image');
 const blank={text:{en_us:'',es_es:'Español'},rich:{en_us:[],fr_fr:[{type:'text',text:'Bonjour'}]}};
 assert.equal(text(blank.text,'zh_cn'),'Español');
 assert.equal(annotationRich(blank,'en_us')[0].text,'Bonjour');
 assert.equal(annotationRich({rich:{en_us:[{type:'text',text:'  '}],fr_fr:[{type:'text',text:'Français'}]}},'en_us')[0].text,'Français');
});

test('category annotations may be French-only or rich-media-only without en_us',()=>{
 const p=emptyProject(),group=category('questplus:fallback','Fallback');
 p.categories=[group];
 group.annotations=[{id:'fr_only',x:0,y:0,style:'heading',color:'#c6a986',text:{fr_fr:'Bonjour'}}];
 assert.deepEqual(validate(p),[]);
 group.annotations.push({id:'rich_only',x:12,y:15,style:'label',color:'#c6a986',text:{},rich:{fr_fr:[{type:'text',text:'Salut',font:'default',size:1}]}});
 assert.deepEqual(validate(p),[]);
 group.annotations.push({id:'empty',x:10,y:0,text:{},rich:{fr_fr:[]}});
 assert.ok(validate(p).some(issue=>issue.message.includes('contenu requis')));
});

test('changing action within the same registry retains a selected modded item ID and quantity',()=>{
 const item={id:'modded',event:'item_use',target:'create:brass_ingot',count:7};
 assert.deepEqual(changeObjectiveEvent(item,'item_smelt','questplus:test'),{...item,event:'item_smelt'});
 const block={id:'modded',event:'block_break',target:'#create:decorative_blocks',count:4};
 assert.deepEqual(changeObjectiveEvent(block,'block_place','questplus:test'),{...block,event:'block_place'});
});
