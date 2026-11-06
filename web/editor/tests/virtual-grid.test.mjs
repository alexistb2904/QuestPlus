import {test} from 'node:test';
import assert from 'node:assert/strict';
import {visibleRows} from '../src/core/virtual-grid.js';

test('virtual registry displays the final entries and bounds the rendered rows',()=>{
 const result=visibleRows(10000,720,109500,600);
 assert.equal(result.columns,8);
 assert.equal(result.last,result.rows);
 assert.ok(result.last-result.first<20);
 assert.equal(visibleRows(0,0,0,0).last,0);
});
