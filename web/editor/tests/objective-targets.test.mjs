import test from 'node:test';
import assert from 'node:assert/strict';
import {DIMENSIONS,BIOMES,STRUCTURES,ENTITIES,MARKERS,targetKind,initialTarget,coordinateBounds,positionValue} from '../src/core/objective-targets.js';
import {emptyProject,category,quest,validate} from '../src/core/project.js';

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
