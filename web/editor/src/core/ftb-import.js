import JSON5 from 'json5';
import {emptyProject,slug} from './project.js';

const IDENTIFIER=/^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const isId=value=>typeof value==='string'&&IDENTIFIER.test(value);
const positive=(value,defaultValue=1)=>{
 const number=value===undefined?defaultValue:Number(value);
 return Number.isSafeInteger(number)&&number>=1&&number<=1_000_000_000?number:null;
};
const typeOf=value=>String(value?.type||'').split(':').at(-1);
const itemId=value=>typeof value==='string'?value:typeof value?.id==='string'?value.id:null;

/** Only translate fields that preserve supported Quest Plus semantics. Unsupported
 * gameplay mechanics remain in the report, never silently changed or unlocked.
 */
function convertTask(task,questId,index,warnings) {
 const type=typeOf(task), count=positive(task?.count??task?.value);
 const id='step'+(index+1);
 const invalid=reason=>{warnings.push(questId+': task '+(task?.type||'(missing type)')+' not converted ('+reason+')');return null};
 if(!task||typeof task!=='object'||!count)return invalid('invalid task/count');
 if(type==='item'){
   const item=itemId(task.item);
   if(!isId(item))return invalid('missing item ID');
   if(task.item?.components || task.item?.nbt || task.nbt || task.ignore_components===false)
     return invalid('item component/NBT matching is not yet supported');
   if(task.consume_items===true)
     return {id,event:'item_turn_in',target:item,count};
   warnings.push(questId+': non-consuming FTB item task mapped to live inventory check; verify the original hand-in behavior');
   return {id,event:'inventory_count',target:item,count};
 }
 if(type==='checkmark')return {id,event:'manual',target:questId,count:1};
 const event={
   kill:'entity_kill',biome:'biome_visit',structure:'structure_visit',
   dimension:'dimension_enter',stat:'custom_stat',
 }[type];
 const target={
   kill:task.entity,biome:task.biome,structure:task.structure,
   dimension:task.dimension,stat:task.stat,
 }[type];
 if(event && isId(target))return {id,event,target,count};
 if(type==='location' && Array.isArray(task.position)&&task.position.length===3 &&
     task.position.every(v=>Number.isFinite(v)) && (!task.dimension||isId(task.dimension))){
   const radius=positive(task.radius??1);
   if(radius===null || radius>128)return invalid('location radius exceeds supported range 1–128');
   const [x,y,z]=task.position;
   const bounds={xMin:x-radius,xMax:x+radius,yMin:y-radius,yMax:y+radius,zMin:z-radius,zMax:z+radius};
   return {id,event:'location',target:'questplus:position',count:1,
     conditions:task.dimension?{and:[{position:bounds},{dimension:task.dimension}]}:{position:bounds}};
 }
 return invalid('unsupported type, missing target, or unsupported parameters');
}

function convertReward(reward,questId,warnings){
 const type=typeOf(reward);
 const reject=reason=>{warnings.push(questId+': reward '+(reward?.type||'(missing type)')+' not converted ('+reason+')');return null};
 if(!reward||typeof reward!=='object')return reject('invalid reward');
 if(type==='item') {
   const item=itemId(reward.item),count=positive(reward.count??reward.item?.count);
   if(!isId(item)||count===null||count>2304)return reject('invalid item/count');
   if(reward.item?.components||reward.item?.nbt||reward.nbt)return reject('components/NBT not preserved');
   return {type:'item',item,count};
 }
 if(type==='xp'||type==='xp_levels'){
   const amount=positive(reward.amount??reward.xp??reward.value);
   if(amount===null||amount>1_000_000)return reject('missing or invalid XP value');
   return {type:type==='xp_levels'||reward.is_levels===true?'experience_levels':'experience',amount};
 }
 return reject('unsupported reward mechanic');
}

function toDependencies(raw){
 if(raw===undefined)return [];
 if(!Array.isArray(raw))return null;
 const refs=raw.map(value=>typeof value==='string'?value:typeof value==='number'?String(value):null);
 return refs.every(v=>v!==null)?refs:null;
}

/** Preview-only migration for FTB Quests 26.x chapter JSON5 files.
 * sourceIdMap maps FTB quest IDs to Quest Plus namespaced quest IDs.
 * No source file is changed, and no command/script is executed.
 */
export function inspectFtbPack(documents) {
 const project=emptyProject(),warnings=[],sources=[],seen=new Set(),ids=new Map(),drafts=[];
 for(const {name,content} of documents){
   const path=String(name).replace(/\\/g,'/');
   if(!/(?:^|\/)chapters\/[^\/]+\.json5?$/i.test(path))continue;
   let root;
   try{root=JSON5.parse(String(content).replace(/^\uFEFF/,''));}
   catch(error){warnings.push(name+': invalid JSON5: '+error.message);continue;}
   if(!root||typeof root!=='object'||Array.isArray(root)||!Array.isArray(root.quests)){
     warnings.push(name+': not a recognized chapter (expected quests array)');continue;
   }
   sources.push({name,root});
 }
 if(!sources.length)throw Error('No FTB chapters detected. Expected chapters/*.json5 with quests[].');
 const safeName=(prefix,raw)=>{
   const base=prefix+'/'+slug(String(raw));let next=base,i=2;
   while(seen.has(next))next=base+'_'+i++;
   seen.add(next);return next;
 };
 for(const {name,root} of sources){
   const fileName=name.replace(/\\/g,'/').split('/').pop().replace(/\.json5?$/i,'');
   const categoryId=safeName('questplus:ftb',root.id||fileName);
   project.categories.push({
     id:categoryId,icon:'minecraft:book',
     title:{en_us:String(root.title||fileName)},description:{en_us:'Imported from FTB Quests'},annotations:[]
   });
   for(const [ix,raw] of root.quests.entries()){
     if(!raw||typeof raw!=='object'||Array.isArray(raw)){warnings.push(categoryId+': invalid quest');continue;}
     const oldId=String(raw.id??'');
     if(!oldId || ids.has(oldId)){warnings.push(categoryId+': missing or duplicate FTB quest ID '+oldId);continue;}
     const id=safeName(categoryId,oldId);
     const tasks=Array.isArray(raw.tasks)?raw.tasks:[];
     const converted=tasks.map((t,n)=>convertTask(t,id,n,warnings)).filter(Boolean);
     if(!converted.length){warnings.push(id+': no supported objectives — quest skipped');continue;}
     if(converted.length!==tasks.length){
       warnings.push(id+': quest skipped because partially converted objectives could alter completion conditions');
       continue;
     }
     const rewards=[];
     for(const reward of Array.isArray(raw.rewards)?raw.rewards:[]){
       const convertedReward=convertReward(reward,id,warnings);
       if(convertedReward)rewards.push(convertedReward);
     }
     const deps=toDependencies(raw.dependencies);
     if(deps===null){warnings.push(id+': unsupported dependency structure; quest skipped');continue;}
     if(raw.tasks?.length>1 && raw.task_mode && !['all','any'].includes(raw.task_mode)){
       warnings.push(id+': unknown task mode; quest skipped');continue;
     }
     const quest={schemaVersion:2,id,category:categoryId,icon:'minecraft:book',
       title:{en_us:String(raw.title||'Imported quest '+(ix+1))},
       description:{en_us:typeof raw.subtitle==='string'&&raw.subtitle.trim()?raw.subtitle:'Imported FTB quest'},
       objectives:converted,rewards,progression:'once',
       objectiveMode:raw.task_mode==='any'?'any':'all',
       layout:{x:Number.isFinite(raw.x)?raw.x:ix*60,y:Number.isFinite(raw.y)?raw.y:0}};
     ids.set(oldId,id);drafts.push({quest,deps,oldId});
   }
 }
 // Dependencies must never become invisible omissions that unlock a quest early.
 const draftsByQuest=new Map(drafts.map(d=>[d.quest.id,d]));
 let allowed=new Set(draftsByQuest.keys()),changed=true;
 while(changed){
   changed=false;
   for(const d of drafts){
     if(!allowed.has(d.quest.id))continue;
     if(d.deps.some(old=>!ids.has(old)||!allowed.has(ids.get(old)))){
       warnings.push(d.quest.id+': dependency is missing or unsupported — quest skipped');
       allowed.delete(d.quest.id);changed=true;
     }
   }
 }
 for(const d of drafts.filter(d=>allowed.has(d.quest.id))){
   const refs=d.deps.map(old=>({quest:ids.get(old)}));
   if(refs.length)d.quest.requires=refs.length===1?refs[0]:{and:refs};
   project.quests.push(d.quest);
 }
 if(!project.quests.length)warnings.push('No lossless supported quests converted; original files remain unchanged.');
 return {project,warnings,chapterCount:sources.length,questCount:project.quests.length,sourceIdMap:ids};
}
