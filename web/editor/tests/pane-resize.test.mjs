import test from 'node:test';
import assert from 'node:assert/strict';
import {clampPaneSize} from '../src/core/pane-resize.js';
import {tUI} from '../src/ui/ui-i18n.js';

test('pane resizing keeps the central editor usable and respects bounds',()=>{
 assert.equal(clampPaneSize('library',120,1200,355,700),180);
 assert.equal(clampPaneSize('library',900,1200,355,700),575);
 assert.equal(clampPaneSize('properties',900,1200,270,700),660);
 assert.equal(clampPaneSize('properties',400,1200,270,700),400);
 assert.equal(clampPaneSize('details',700,1200,270,700),460);
 assert.equal(clampPaneSize('details',20,1200,270,700),155);
 assert.equal(clampPaneSize('details',NaN,1200,270,700),155);
});

test('equipment action and resize controls are localized',()=>{
 assert.equal(tUI('Verified Equipment','en'),'Verified Equipment');
 assert.equal(tUI('Verified Equipment','fr'),'Vérifier l’équipement');
 for(const locale of ['fr','es','zh']){
  for(const key of ['Verified Equipment','Resize properties','Resize diagram and objectives','Draw background','Remove waypoint'])
   assert.notEqual(tUI(key,locale),key,locale+' '+key);
 }
});
