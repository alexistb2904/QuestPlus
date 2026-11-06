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
 const startup=read('../src/core/startup-session.js');
 for(const key of ['fileMap','readZip','makeZip','importDocuments','STUDIO_STORAGE_KEY'])assert.ok(hook.includes(key),key);
 assert.ok(startup.includes('questplus.quest-studio.v1'));
 for(const key of ['gameLayout','onPointerMove','onWheel','onPointerCancel'])assert.ok(tree.includes(key),key);
 assert.ok(rich.includes('toEmbeddedImage'));
});
test('Vite serves production build on GitHub Pages',()=>{
 assert.match(read('../vite.config.ts'),/base:'\.\/'/);
 assert.match(read('../../../.github/workflows/pages.yml'),/web\/editor\/dist/);
});

test('JavaScript source files are organized inside src rather than the editor root',()=>{
 const root=fs.readdirSync(new URL('../',import.meta.url));
 assert.deepEqual(root.filter(name=>name.endsWith('.js')),[]);
 for(const entry of ['src/core/project.js','src/core/conditions.js','src/core/game-layout.js',
                    'src/data/default-data.js','src/data/vanilla-registry-26.3.js',
                    'src/ui/minecraft-icons.js','src/ui/ui-i18n.js',
                    'src/styles/quest-skin.css','src/styles/react.css','public/questplus-logo.png']){
  assert.ok(fs.existsSync(new URL('../'+entry,import.meta.url)),entry);
 }
 assert.ok(!root.includes('questplus-logo.svg'));
 assert.ok(!root.includes('questplus-logo.png'));
});

test('saved project startup choice uses the React modal and reset path',()=>{
 const app=read('../src/App.tsx'),hook=read('../src/hooks/useStudio.ts');
 const dialog=read('../src/components/Dialog.tsx'),tr=read('../src/ui/ui-i18n-additional.js');
 for(const label of ['Resume current project','Create a new project','Empty project','Project with examples']) {
   assert.ok(app.includes(label),label);
   assert.ok(tr.includes(label),label);
 }
 assert.match(app,/studio\.startFresh\(false\)/);
 assert.match(app,/studio\.startFresh\(true\)/);
 assert.match(dialog,/dismissible\?:boolean/);
 assert.match(hook,/history\.current = \[\]/);
});

test('Markdown documentation is deployed alongside React Quest Studio',()=>{
 const site=read('../../../docs-site/.vitepress/config.mts');
 const workflow=read('../../../.github/workflows/pages.yml');
 assert.ok(site.includes("base: '/QuestPlus/docs/'"));
 assert.ok(site.includes("search: { provider: 'local' }"));
 assert.ok(workflow.includes('docs-site/dist/.'));
});

test('new English Markdown guide obeys punctuation rules',()=>{
 const fsRoot=new URL('../../../docs-site/content/',import.meta.url);
 const traverse=(url)=>{
   for(const item of fs.readdirSync(url,{withFileTypes:true})){
     const next=new URL(item.name+(item.isDirectory()?'/':''),url);
     if(item.isDirectory())traverse(next);
     else if(item.name.endsWith('.md')){
       const body=fs.readFileSync(next,'utf8');
       assert.ok(!body.includes('—'),item.name+' contains an em dash');
       const prose=body.replace(/~~~[^]*?~~~/g,'');
       assert.ok(!prose.includes(';'),item.name+' contains a semicolon outside code');
     }
   }
 };
 traverse(fsRoot);
});

test('docs cover every built-in objective and reward type',()=>{
 const source=read('../src/core/project.js');
 const events=source.split('export const EVENTS = [')[1]?.split('];')[0];
 assert.ok(events,'objective event registry is present');
 const objectives=read('../../../docs-site/content/guide/objectives.md');
 const types=[...events.matchAll(/\['([^']+)','[^']+'\]/g)].map(match=>match[1]);
 assert.ok(types.length>=35,'expected all current built-in objective types');
 for(const type of types)assert.ok(objectives.includes('`'+type+'`'),'Undocumented objective: '+type);
 const rewards=read('../../../docs-site/content/guide/rewards.md');
 const editor=read('../src/components/RewardEditor.tsx');
 const names=editor.split('const types:')[1]?.split('const make=')[0];
 assert.ok(names,'built-in rewards and pools are present');
 for(const [,type] of names.matchAll(/\['([^']+)','[^']+'\]/g))
   assert.ok(rewards.includes('`'+type+'`'),'Undocumented reward: '+type);
 for(const type of ['questplus:command','questplus:table'])
   assert.ok(rewards.includes('`'+type+'`'),'Undocumented special reward: '+type);
});
test('docs are dark, mobile-safe and have their own deployable logo',()=>{
 const config=read('../../../docs-site/.vitepress/config.mts');
 const css=read('../../../docs-site/.vitepress/theme/custom.css');
 assert.match(config,/appearance: 'force-dark'/);
 assert.match(config,/theme: 'github-dark'/);
 assert.ok(config.includes("logo: '/questplus-mark.svg'"));
 assert.match(config,/text: 'Open editor'/);
 assert.doesNotMatch(config,/Open editor ↗/);
 assert.ok(css.includes('overflow-x: auto !important'));
 assert.ok(css.includes('.VPNavBarTitle .title'));
 assert.ok(fs.existsSync(new URL('../../../docs-site/content/public/questplus-mark.svg',import.meta.url)));
});
