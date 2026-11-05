import {slug} from './project.js';
const KEY='questplus.studio.saved-quest-templates.v1';
const COLUMNS=new Set(['schemaVersion','icon','size','title','description','descriptionRich','objectives','rewards',
 'progression','objectiveMode','objectiveThreshold','timeLimitSeconds','cooldownSeconds','noDeath','kind',
 'acknowledgement','narrative','tiers','afterLast','progressBeforeUnlock','hidden']);
export function savedQuestTemplates(storage=globalThis.localStorage){
 try{
   const values=JSON.parse(storage?.getItem(KEY)||'[]');
   if(!Array.isArray(values))return [];
   return values.slice(0,32).filter(template=>template&&
     /^custom:[a-z0-9_]{1,40}$/.test(template.id)&&
     typeof template.label==='string'&&template.label.length<=64&&
     template.quest&&typeof template.quest==='object'&&!Array.isArray(template.quest)&&
     Array.isArray(template.quest.objectives)&&Array.isArray(template.quest.rewards));
 }catch{return []}
}
export function saveQuestTemplate(quest,label,storage=globalThis.localStorage){
 const name=String(label||'').trim();
 if(!name||name.length>64)throw Error('Template name must be 1–64 characters');
 const id='custom:'+slug(name).slice(0,40),saved=savedQuestTemplates(storage);
 const data=Object.fromEntries(Object.entries(quest).filter(([key])=>COLUMNS.has(key)));
 // Templates never carry the source's quest ID, category, layout, team progress or dependencies.
 // A newly created quest must always be independently playable.
 const clean=structuredClone(data),item={id,label:name,quest:clean};
 const existing=saved.findIndex(t=>t.id===id);
 if(existing>=0)saved.splice(existing,1,item);
 else {if(saved.length>=32)throw Error('32 saved templates maximum');saved.push(item)}
 const serialized=JSON.stringify(saved);
 if(serialized.length>500_000)throw Error('Template library exceeds storage safety limit');
 storage.setItem(KEY,serialized);
 return item;
}
export function buildSavedQuest(template,category,id,title,position){
 if(!template?.quest||!Array.isArray(template.quest.objectives))throw Error('Invalid saved quest template');
 const q=structuredClone(template.quest);
 // The caller generates unique quest and objective IDs; no prerequisite references cross projects.
 q.id=id;q.category=category.id;q.layout={x:position.x,y:position.y};q.title={en_us:title};
 delete q.requires;
 return q;
}
