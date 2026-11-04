import test from 'node:test';
import assert from 'node:assert/strict';
import {emptyProject, category, orderedCategories, validate, fileMap, importDocuments} from '../src/core/project.js';
import {makeZip, readZip} from '../src/core/zip.js';

test('category order is a compact sort key, not a slot index',()=>{
  const a=category('questplus:a','A'),b=category('questplus:b','B'),c=category('questplus:c','C');
  a.order=15;b.order=0;c.order=1;
  const sorted=orderedCategories([a,b,c]);
  assert.deepEqual(sorted.map(x=>x.id),['questplus:b','questplus:c','questplus:a']);
  assert.equal(sorted.length,3); // 0,1,15 creates exactly three rows.
  const d=category('questplus:d','D');
  assert.deepEqual(orderedCategories([d,a,c,b]).map(x=>x.id),['questplus:b','questplus:c','questplus:a','questplus:d']);
});
test('invalid order is rejected but missing and duplicate orders remain safe',()=>{
  const p=emptyProject(),a=category('questplus:a'),b=category('questplus:b');
  p.categories=[a,b];
  assert.deepEqual(validate(p),[]);
  a.order=3;b.order=3;
  assert.deepEqual(orderedCategories(p.categories).map(x=>x.id),['questplus:a','questplus:b']);
  assert.deepEqual(validate(p),[]);
  for(const bad of [-1,1.5,1000001,NaN,Infinity,'5']){
    a.order=bad;
    assert.ok(validate(p).some(x=>x.message.includes('ordre')),String(bad));
  }
});
test('category order survives ZIP round trip without reindexing',async()=>{
  const p=emptyProject(),a=category('questplus:a'),b=category('questplus:b');
  a.order=15;b.order=0;p.categories=[a,b];
  const docs=await readZip(makeZip(fileMap(p)));
  const loaded=importDocuments(docs);
  assert.deepEqual(loaded.categories.map(x=>x.order),[15,0]);
  assert.deepEqual(orderedCategories(loaded.categories).map(x=>x.id),['questplus:b','questplus:a']);
});
