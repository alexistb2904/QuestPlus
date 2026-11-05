import {quest as newQuest} from './project.js';

/** Templates are authoring conveniences. Every template uses event types supported by the engine. */
export const QUEST_TEMPLATES=Object.freeze([
  {id:'basic',label:'Empty quest',description:'One editable objective'},
  {id:'collect',label:'Resource collection',description:'Hold a quantity of an item'},
  {id:'craft',label:'Crafting',description:'Craft a recipe output'},
  {id:'boss',label:'Boss challenge',description:'Kill a boss without dying'},
  {id:'explore',label:'Exploration',description:'Discover a biome and a structure'},
  {id:'equipment',label:'Equipment mastery',description:'Wear an enchanted armor piece'},
  {id:'sequence',label:'Progression chain',description:'Complete actions in order'},
  {id:'timed',label:'Timed challenge',description:'Finish within five minutes'},
  {id:'repeat',label:'Repeatable contract',description:'Repeat after a cooldown'},
  {id:'information',label:'Information / story',description:'Read an introduction without gameplay actions'}
]);
export function buildQuestFromTemplate(category,id,title,position,templateId='basic'){
  if(!QUEST_TEMPLATES.some(t=>t.id===templateId)) throw Error('Unknown quest template: '+templateId);
  const q=newQuest(category,id,title,position);
  const step=(id,event,target,count=1,conditions=undefined)=>({id,event,target,count,...(conditions?{conditions}:{})});
  switch(templateId){
    case 'collect': q.icon='minecraft:iron_ingot';q.objectives=[step('collect','inventory_count','minecraft:iron_ingot',32)];break;
    case 'craft': q.icon='minecraft:crafting_table';q.objectives=[step('craft','item_craft','minecraft:crafting_table')];break;
    case 'boss': q.icon='minecraft:nether_star';q.objectives=[step('boss','entity_kill','minecraft:wither')];q.noDeath=true;q.rewards=[{type:'experience',amount:500}];break;
    case 'explore': q.icon='minecraft:compass';q.objectives=[step('biome','biome_visit','minecraft:desert'),step('structure','structure_visit','minecraft:desert_pyramid')];break;
    case 'equipment':
      q.icon='minecraft:diamond_chestplate';
      q.objectives=[step('gear','equipment_check','questplus:gear',1,{'questplus:gear':{items:[{slot:'chest',item:'minecraft:diamond_chestplate',enchantments:{'minecraft:protection':4}}]}})];break;
    case 'sequence':q.icon='minecraft:iron_pickaxe';q.objectives=[step('wood','block_break','minecraft:oak_log',8),step('stone','block_break','minecraft:stone',32),step('iron','inventory_count','minecraft:iron_ingot',8)];q.objectiveMode='sequence';break;
    case 'timed':q.icon='minecraft:clock';q.objectives=[step('hunt','entity_kill','minecraft:zombie',10)];q.timeLimitSeconds=300;break;
    case 'repeat':q.icon='minecraft:emerald';q.objectives=[step('trade','trade','minecraft:emerald',8)];q.progression='repeatable';q.cooldownSeconds=3600;break;
    case 'information':q.kind='information';q.acknowledgement='manual';q.objectives=[];q.narrative={en_us:'Add your story or instructions here.'};break;
  }
  return q;
}
