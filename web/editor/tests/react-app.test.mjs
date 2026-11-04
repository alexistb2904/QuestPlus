import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(new URL(p,import.meta.url),'utf8');
test('React mount uses TypeScript components, not legacy editor.js',()=>{
 const html=read('../index.html'),main=read('../src/main.tsx'),app=read('../src/App.tsx');
 assert.match(html,/id="root"/);
 assert.match(main,/createRoot/);
 assert.doesNotMatch(html,/editor\.js/);
 for(const name of ['TreeCanvas','Inspector','Sidebar','IconPicker','useStudio'])assert.ok(app.includes(name),name);
});
test('Quest Studio preserves Minecraft data and annotation editing',()=>{
 const hook=read('../src/hooks/useStudio.ts'),tree=read('../src/components/TreeCanvas.tsx'),rich=read('../src/components/RichEditor.tsx');
 for(const key of ['fileMap','readZip','makeZip','importDocuments','questplus.quest-studio.v1'])assert.ok(hook.includes(key),key);
 for(const key of ['gameLayout','onPointerMove','onWheel','onPointerCancel'])assert.ok(tree.includes(key),key);
 assert.ok(rich.includes('toEmbeddedImage'));
});
test('Vite serves production build on GitHub Pages',()=>{
 assert.match(read('../vite.config.ts'),/base:'\.\/'/);
 assert.match(read('../../../.github/workflows/pages.yml'),/web\/editor\/dist/);
});
