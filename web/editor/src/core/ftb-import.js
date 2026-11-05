import JSON5 from 'json5';
import {emptyProject,slug} from './project.js';

/** Conservative FTB Quests 26.x JSON5 converter. Failing tasks/dependencies never disappear silently. */
const ID=/^#?[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const validCount=value=>Number.isSafeInteger(value)&&value>=1&&value<=1_000_000_000;
const resource=value=>typeof value==='string'?value:value&&typeof value==='object'&&!Array.isArray(value)?value.id:null;
const kind=value=>String(value||'').split(':').at(-1);

function convertTask(t,id,index){
  if(!t||typeof t!=='object'||Array.isArray(t))throw Error('invalid task');
  const type=kind(t.type),count=t.count??t.value??1;
  const withTarget=(event,target,quantity=count,conditions=undefined)=>{
    if(!ID.test(target||'')||!validCount(quantity))throw Error('invalid target or count');
    return {id:'step'+(index+1),event,target,count:quantity,...(conditions?{conditions}:{})};
  };
  switch(type){
    case 'item': {
      if(t.item?.components||t.item?.tag)throw Error('item components/tag filters need manual conversion');
      return withTarget(t.consume_items===true?'item_turn_in':'item_submit',resource(t.item),t.count??t.item?.count??1);
    }
    case 'kill':return withTarget('entity_kill',resource(t.entity),t.value??t.count??1);
    case 'biome':return withTarget('biome_visit',resource(t.biome),1);
    case 'structure':return withTarget('structure_visit',resource(t.structure),1);
    case 'dimension':return withTarget('dimension_enter',resource(t.dimension),1);
    case 'stat':return withTarget('custom_stat',resource(t.stat),t.value??t.count??1);
    case 'checkmark':return withTarget('manual',id,1);
    case 'location': {
      if(!Array.isArray(t.position)||t.position.length!==3||!t.position.every(Number.isFinite))
        throw Error('location requires three finite coordinates');
      const radius=t.radius??0;
      if(!Number.isFinite(radius)||radius<0||radius>128)throw Error('location radius exceeds Quest Plus maximum');
      const [x,y,z]=t.position;
      const position={xMin:x-radius,xMax:x+radius,yMin:y-radius,yMax:y+radius,zMin:z-radius,zMax:z+radius};
      const conditions=resource(t.dimension)&&ID.test(resource(t.dimension))
        ?{and:[{position},{dimension:resource(t.dimension)}]}:{position};
      return withTarget('location','questplus:position',1,conditions);
    }
    default:throw Error('unsupported FTB task '+(t.type||'(missing type)'));
  }
}
function convertReward(r){
  if(!r||typeof r!=='object'||Array.isArray(r))throw Error('invalid reward');
  const type=kind(r.type);
  if(type==='xp'||type==='xp_levels'){
    const amount=r[type]??r.amount;
    if(!validCount(amount)||amount>1_000_000)throw Error('invalid XP amount');
    return {type:type==='xp'?'experience':'experience_levels',amount};
  }
  if(type==='item'){
    if(r.item?.components||r.item?.tag)throw Error('custom item components require manual conversion');
    const item=resource(r.item),count=r.count??r.item?.count??1;
    if(!ID.test(item||'')||item==='minecraft:air'||!Number.isInteger(count)||count<1||count>2304)
      throw Error('invalid item reward');
    return {type:'item',item,count};
  }
  throw Error('unsupported FTB reward '+(r.type||'(missing type)'));
}

export function inspectFtbPack(documents){
  const project=emptyProject(),warnings=[],sources=[],ids=new Map(),seen=new Set(),candidates=[];
  for(const {name,content} of documents){
    const path=String(name).replace(/\\/g,'/');
    if(!/(?:^|\/)chapters\/[^/]+\.json5?$/i.test(path))continue;
    let root;
    try{root=JSON5.parse(String(content).replace(/^\uFEFF/,''));}
    catch(e){warnings.push(name+': invalid JSON5: '+e.message);continue}
    if(!root||typeof root!=='object'||Array.isArray(root)||!Array.isArray(root.quests))continue;
    sources.push({name,root});
  }
  if(!sources.length)throw Error('No FTB chapters detected. Expected chapters/*.json5 containing quests[].');

  const safeName=(prefix,raw)=>{
    const base=prefix+'/'+slug(String(raw));let next=base,i=2;
    while(seen.has(next))next=base+'_'+i++;
    seen.add(next);return next;
  };
  // Build IDs first so dependencies can point to quests in later chapters.
  for(const {name,root} of sources){
    const filename=name.split(/[\\/]/).at(-1).replace(/\.json5?$/i,'');
    const categoryId=safeName('questplus:ftb',root.id||filename);
    project.categories.push({id:categoryId,icon:'minecraft:book',title:{en_us:String(root.title||filename)},
      description:{en_us:'Imported from FTB Quests'},annotations:[]});
    for(const [ix,raw] of root.quests.entries()){
      if(!raw||typeof raw!=='object'||Array.isArray(raw))continue;
      const foreign=raw.id===undefined?null:String(raw.id);
      const id=safeName(categoryId,foreign??ix+1);
      if(foreign!==null){
        if(ids.has(foreign))warnings.push('Duplicate FTB quest ID '+foreign+'; dependencies are ambiguous');
        else ids.set(foreign,id);
      }
      candidates.push({id,raw,categoryId,index:ix,foreign});
    }
  }
  for(const {id,raw,categoryId,index,foreign} of candidates){
    const objectives=[],rewards=[];let safe=true;
    const tasks=Array.isArray(raw.tasks)?raw.tasks:[];
    for(const [i,task] of tasks.entries()){
      try{objectives.push(convertTask(task,id,i))}
      catch(error){warnings.push(id+': task '+(task?.type||i)+' not imported: '+error.message);safe=false}
    }
    if(!safe||!objectives.length){warnings.push(id+': skipped to prevent an easier/incomplete quest');continue}
    for(const [i,reward] of (Array.isArray(raw.rewards)?raw.rewards:[]).entries()){
      try{rewards.push(convertReward(reward))}
      catch(error){warnings.push(id+': reward '+(reward?.type||i)+' not imported: '+error.message)}
    }
    const deps=Array.isArray(raw.dependencies)?raw.dependencies:[];
    const minimum=raw.min_required_dependencies??0;
    if(!Number.isInteger(minimum)||minimum<0||minimum>deps.length ||
       (minimum>1&&minimum<deps.length) ||
       (raw.dependency_requirement&&!['all_completed','ALL_COMPLETED','any_completed','ANY_COMPLETED'].includes(raw.dependency_requirement))){
      warnings.push(id+': unsupported dependency rule; quest skipped');continue;
    }
    const quest={schemaVersion:2,id,category:categoryId,icon:'minecraft:book',
      title:{en_us:String(raw.title||'Imported quest '+(index+1))},objectives,rewards,progression:'once',
      layout:{x:Number.isFinite(raw.x)?raw.x:index*60,y:Number.isFinite(raw.y)?raw.y:0}};
    if(raw.require_sequential_tasks===true)quest.objectiveMode='sequence';
    if(raw.hide_until_deps_complete===true)quest.hidden=true;
    // Keep source dependencies only for a second pass; never publish unresolved references.
    project.quests.push(quest);
  }

  const existing=new Set(project.quests.map(q=>q.id));
  const source=new Map(candidates.map(c=>[c.id,c.raw]));
  // Prune transitively: a quest must never unlock early because its prerequisite failed import.
  let changed=true;
  while(changed){
    changed=false;
    project.quests=project.quests.filter(q=>{
      const dependencies=source.get(q.id)?.dependencies;
      if(!Array.isArray(dependencies)||!dependencies.length)return true;
      const unresolved=dependencies.some(dep=>!ids.has(String(dep))||!existing.has(ids.get(String(dep))));
      if(unresolved){warnings.push(q.id+': prerequisite not imported; quest skipped');existing.delete(q.id);changed=true;return false}
      return true;
    });
  }
  for(const q of project.quests){
    const dependencies=source.get(q.id)?.dependencies;
    if(!Array.isArray(dependencies)||!dependencies.length)continue;
    const conditions=dependencies.map(dep=>({quest:ids.get(String(dep))}));
    const raw=source.get(q.id);
    const any=raw.min_required_dependencies===1&&conditions.length>1 ||
        ['any_completed','ANY_COMPLETED'].includes(raw.dependency_requirement);
    q.requires=conditions.length===1?conditions[0]:any?{or:conditions}:{and:conditions};
  }
  if(!project.quests.length)warnings.push('No safely convertible quests found. Original FTB files were not modified.');
  return {project,warnings,chapterCount:sources.length,questCount:project.quests.length,sourceIdMap:ids};
}
