import {test} from 'node:test';
import assert from 'node:assert/strict';
import {resourceMatcher} from '../src/core/resource-search.js';

test('search supports multilingual names, synonyms, typos and multiple words',()=>{
 assert.ok(resourceMatcher('bois')('minecraft:oak_planks'));
 assert.ok(resourceMatcher('planches de chene')('minecraft:oak_planks'));
 assert.ok(resourceMatcher('diamnd swrd')('minecraft:diamond_sword'));
 assert.ok(resourceMatcher('diamnod sword')('minecraft:diamond_sword'));
 assert.ok(resourceMatcher('diamante')('minecraft:diamond'));
 assert.ok(resourceMatcher('钻石')('minecraft:diamond'));
 assert.ok(resourceMatcher('brass ingot')('create:brass_ingot'));
 assert.equal(resourceMatcher('or')('minecraft:iron_sword'),false);
 assert.equal(resourceMatcher('oak')('minecraft:birch_planks'),false);
 assert.equal(resourceMatcher('diamond sword')('minecraft:iron_sword'),false);
 assert.equal(resourceMatcher('zzzzzz')('minecraft:diamond'),false);
});
