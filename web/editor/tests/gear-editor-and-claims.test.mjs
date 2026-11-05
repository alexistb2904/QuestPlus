import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {demoProject,validate,quest,orderedCategories,objectiveLabel,gearRequirementText} from '../src/core/project.js';
const read=path=>fs.readFileSync(new URL(path,import.meta.url),'utf8');

test('new projects contain only maintained categories in progression order',()=>{
 const project=demoProject();
 const removed=['combat','exploration','mining','survival'];
 assert.ok(!project.categories.some(c=>removed.some(id=>c.id==='questplus:'+id)));
 assert.ok(!project.quests.some(q=>removed.some(id=>q.category==='questplus:'+id)));
 assert.equal(project.categories.length,16);
 const ids=orderedCategories(project.categories).map(c=>c.id);
 assert.equal(ids[0],'questplus:homestead');
 assert.ok(ids.indexOf('questplus:endgame')>ids.indexOf('questplus:nether'));
 assert.ok(ids.indexOf('questplus:equipment')>ids.indexOf('questplus:quarries'));
});

test('studio supports modded gear with armor slots and enchantment levels',()=>{
 const project=demoProject();
 const q=quest(project.categories[0],'questplus:custom/enchanted_pickaxe','Enchanted Pickaxe');
 q.objectives=[{id:'enchanted',event:'equipment_check',target:'questplus:gear',count:1,
  conditions:{'questplus:gear':{items:[{slot:'mainhand',item:'some_mod:legendary_pickaxe',
   enchantments:{'minecraft:efficiency':5,'minecraft:unbreaking':3}}]}}}];
 project.quests.push(q);
 assert.deepEqual(validate(project),[]);
 q.objectives[0].conditions['questplus:gear'].items[0].enchantments['minecraft:efficiency']=11;
 assert.ok(validate(project).some(x=>/condition/i.test(x.message)), 'Enchant 11 must not pass server validation');
});

test('Studio displays short localized armor objectives instead of serialized conditions',()=>{
 const golden=demoProject().quests.find(q=>q.id==='questplus:equipment/set_golden');
 assert.ok(golden);
 for(const lang of ['en_us','fr_fr','es_es','zh_cn']){
   const label=objectiveLabel(golden.objectives[0],lang);
   assert.ok(label.length<100,lang+' '+label);
   assert.doesNotMatch(label,/\\{.*items|\\bhead:/);
 }
 const levelled=demoProject().quests.find(q=>q.id==='questplus:equipment/diamond_p4');
 const summary=gearRequirementText(levelled.objectives[0].conditions,'en_us');
 assert.match(summary,/protection 4/);
});

test('game presentation uses a gear-specific renderer instead of JSON fallback',()=>{
 const s=read('../../../client/src/main/java/dev/questplus/fabric/client/QuestConditionText.java');
 assert.match(s,/case "questplus:gear" -> gear\(value\)/);
 assert.match(s,/gear\.full/);
 assert.doesNotMatch(s,/return tr\("other","questplus:gear",value\)/);
 const advanced=read('../../../server/src/main/java/dev/questplus/fabric/AdvancedEvents.java');
 const context=read('../../../server/src/main/java/dev/questplus/fabric/QuestPlusMod.java');
 assert.match(advanced,/public static Map<String,Object> gearSnapshot/);
 assert.match(context,/context\.put\("gear",AdvancedEvents\.gearSnapshot\(player\)\)/);
});

test('completion auto-unpins, unlock and completion alone trigger toasts',()=>{
 const s=read('../../../client/src/main/java/dev/questplus/fabric/client/QuestPlusClient.java');
 assert.match(s,/settings\.pinned\.removeIf/);
 assert.match(s,/QuestClientState\.flag\(next,"unlocked"\)/);
 assert.doesNotMatch(s,/QuestToast\.show\(b>=target/);
 assert.doesNotMatch(s,/progress_notice/);
 const screen=read('../../../client/src/main/java/dev/questplus/fabric/client/QuestScreen.java');
 assert.match(screen,/claimAll=button\("claim_all"/);
 assert.match(screen,/NEW_UNLOCKS\.contains\(q\.id\(\)\)/);
 assert.match(screen,/treeLeft=left\+38/);
});

test('claim-all remains a single server-authorized action with durable sequential claims',()=>{
 const runtime=read('../../../server/src/main/java/dev/questplus/fabric/QuestRuntime.java');
 const network=read('../../../server/src/main/java/dev/questplus/fabric/QuestNetwork.java');
 assert.match(network,/case "claim_all" -> runtime\.claimAll\(player\)/);
 assert.match(runtime,/private void claimNext\(ServerPlayer player,List<String> ids,int index\)/);
 assert.match(runtime,/claim\(player,ids\.get\(index\),\(\) ->/);
 assert.match(runtime,/visibleQuests\(uuid\)/);
});

test('brightness maximum keeps the original colors without a white boost',()=>{
 const screen=read('../../../client/src/main/java/dev/questplus/fabric/client/TabletSurfaceRenderer.java');
 assert.match(screen,/if\(brightness<\.999\)/);
 assert.match(screen,/\(1-brightness\)\*255/);
 assert.doesNotMatch(screen,/glow|0x00ffffff/);
});
