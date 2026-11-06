import test from 'node:test';
import assert from 'node:assert/strict';
import {readStoredStudioSession,STUDIO_STORAGE_KEY} from '../src/core/startup-session.js';

const storage = value => ({
  getItem: key => key === STUDIO_STORAGE_KEY ? value : null,
});

test('startup offers resume only when a valid saved project exists', () => {
  assert.equal(readStoredStudioSession(storage(null)), null);
  assert.equal(readStoredStudioSession(storage('{bad json')), null);
  assert.equal(readStoredStudioSession(storage(JSON.stringify({project:{categories:{},quests:[]}}))), null);
  const saved = {project:{categories:[],quests:[]},camera:{x:4,y:2,zoom:1}};
  assert.deepEqual(readStoredStudioSession(storage(JSON.stringify(saved))), saved);
});

test('startup tolerates inaccessible browser storage', () => {
  assert.equal(readStoredStudioSession({getItem(){throw Error('blocked')}}), null);
});
