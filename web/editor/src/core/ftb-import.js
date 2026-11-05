import JSON5 from 'json5';
import {parseSnbt} from './snbt.js';
import {emptyProject,slug} from './project.js';

/** Conservative FTB Quests 26.x JSON5 converter. Failing tasks/dependencies never disappear silently. */
const ID=/^#?[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const validCount=value=>Number.isSafeInteger(value)&&value>=1&&value<=1_000_000_000;
const resource=value=>typeof value==='string'?value:value&&typeof value==='object'&&!Array.isArray(value)?value.id:null;
const kind=value=>String(value||'').split(':').at(-1);

function ftbBool(value,inherited=false){
  if(value===undefined||value===null||value==='default')return inherited;
  if(value===true||value===1||value==='true')return true;
  if(value===false||value===0||value==='false')return false;
  throw Error('unknown FTB tristate '+String(value));
}
function convertTask(t,id,index,chapter){
  if(!t||typeof t!=='object'||Array.isArray(t))throw Error('invalid task');
  const type=kind(t.type),count=t.count??t.value??1;
  const withTarget=(event,target,quantity=count,conditions=undefined)=>{
    if(!ID.test(target||'')||!validCount(quantity))throw Error('invalid target or count');
    return {id:'step'+(index+1),event,target,count:quantity,...(conditions?{conditions}:{})};
  };
  switch(type){
    case 'item': {
      if(t.item?.components && Object.keys(t.item.components).length || t.item?.tag ||
         t.match_components && !['none','NONE'].includes(t.match_components))
        throw Error('item component/tag matching needs manual conversion');
      const consume=ftbBool(t.consume_items,ftbBool(chapter?.consume_items,false));
      const craft=ftbBool(t.only_from_crafting,false);
      const screen=ftbBool(t.task_screen_only,false);
      if(screen)throw Error('FTB Task Screen-only automation is not equivalent to a player action');
      if(consume&&craft)throw Error('crafted-and-consumed item semantics are not supported together');
      return withTarget(craft?'item_craft':consume?'item_turn_in':'inventory_count',resource(t.item),t.count??t.item?.count??1);
    }
    case 'observe_block':return withTarget('observe_block',resource(t.block),t.ticks??t.value??t.count??1);
    case 'observe_entity':return withTarget('observe_entity',resource(t.entity),t.ticks??t.value??t.count??1);
    case 'stage':return withTarget('stage',stageId(t.stage),1);
    case 'kill':return withTarget('entity_kill',resource(t.entity),t.value??t.count??1);
    case 'biome':return withTarget('biome_visit',resource(t.biome),1);
    case 'structure':return withTarget('structure_visit',resource(t.structure),1);
    case 'dimension':return withTarget('dimension_enter',resource(t.dimension),1);
    case 'stat':return withTarget('custom_stat',resource(t.stat),t.value??t.count??1);
    case 'advancement':return withTarget('advancement_complete',resource(t.advancement),1);
    case 'advancement':return withTarget('advancement_complete',resource(t.advancement),1);
    case 'xp': {
      if(ftbBool(t.points,false))throw Error('FTB XP points task cannot safely map to XP levels');
      return withTarget('xp_levels','questplus:levels',t.value??1);
    }
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
function stageId(value){
 const raw=resource(value);if(typeof raw!=='string')throw Error('invalid stage ID');
 // Encode legacy names without collapsing distinct source stage gates.
 const id=raw.includes(':')?raw:/^[a-z0-9_./-]+$/.test(raw)&&!raw.startsWith('encoded/')?'ftb:'+raw:'ftb:encoded/'+Array.from(new TextEncoder().encode(raw),n=>n.toString(16).padStart(2,'0')).join('');
 if(!ID.test(id))throw Error('invalid stage ID');return id;
}
function convertReward(r){
  if(!r||typeof r!=='object'||Array.isArray(r))throw Error('invalid reward');
  const type=kind(r.type);
  if(type==='xp'||type==='xp_levels'){
    const amount=r[type]??r.amount;
    if(!validCount(amount)||amount>1_000_000)throw Error('invalid XP amount');
    return {type:type==='xp'?'experience':'experience_levels',amount};
  }
  if(type==='advancement') {
    const advancement=resource(r.advancement);
    if(!ID.test(advancement||''))throw Error('invalid advancement reward ID');
    return {type:'questplus:advancement',data:{advancement}};
  }
  if(type==='stage')return {type:'questplus:stage',data:{stage:stageId(r.stage),remove:ftbBool(r.remove,false)}};
  if(type==='toast'){const icon=resource(r.icon)||'minecraft:book';if(!ID.test(icon))throw Error('invalid toast icon');return {type:'questplus:toast',data:{title:{en_us:String(r.title||'')},description:{en_us:String(r.description||'')},icon}}}
  if(type==='item'){
    if(r.item?.components||r.item?.tag)throw Error('custom item components require manual conversion');
    const item=resource(r.item),count=r.count??r.item?.count??1;
    if(!ID.test(item||'')||item==='minecraft:air'||!Number.isInteger(count)||count<1||count>2304)
      throw Error('invalid item reward');
    return {type:'item',item,count};
  }
  throw Error('unsupported FTB reward '+(r.type||'(missing type)'));
}

// TODO(Codex P2): finish Data Components, decoration and third-party semantics,
// stage gates and complex reward tables. Reject unsupported required semantics.
export function inspectFtbPack(documents){
  const project=emptyProject(),warnings=[],sources=[],ids=new Map(),ambiguous=new Set(),seen=new Set(),candidates=[];
  const translations={};
  for(const {name,content} of documents){const m=String(name).replace(/\\/g,'/').match(/(?:^|\/)lang\/([a-z]{2,3}_[a-z]{2,4})\.json$/i);if(m){try{translations[m[1].toLowerCase()]=JSON.parse(content)}catch{warnings.push(name+': invalid translation file')}}}
  const localized=value=>{const raw=String(value??'');const result={};for(const [locale,entries] of Object.entries(translations))if(typeof entries[raw]==='string')result[locale]=entries[raw];if(!result.en_us)result.en_us=raw;return result};
  for(const {name,content} of documents){
    const path=String(name).replace(/\\/g,'/');
    if(!/(?:^|\/)chapters\/[^/]+\.(?:json5?|snbt)$/i.test(path))continue;
    let root;
    try{root=/\.snbt$/i.test(path)?parseSnbt(String(content).replace(/^\uFEFF/,'')):JSON5.parse(String(content).replace(/^\uFEFF/,''));}
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
    const filename=name.split(/[\\/]/).at(-1).replace(/\.(?:json5?|snbt)$/i,'');
    const categoryId=safeName('questplus:ftb',root.id||filename);
    project.categories.push({id:categoryId,icon:'minecraft:book',title:localized(root.title||filename),
      description:{en_us:'Imported from FTB Quests'},annotations:[],...(typeof root.group==='string'?{group:root.group}:{})});
    for(const [ix,raw] of root.quests.entries()){
      if(!raw||typeof raw!=='object'||Array.isArray(raw))continue;
      const foreign=raw.id===undefined?null:String(raw.id);
      const id=safeName(categoryId,foreign??ix+1);
      if(foreign!==null){
        if(ids.has(foreign)){ambiguous.add(foreign);warnings.push('Duplicate FTB quest ID '+foreign+'; dependencies are ambiguous')}
        else ids.set(foreign,id);
      }
      candidates.push({id,raw,categoryId,index:ix,foreign,chapter:root});
    }
  }
  for(const {id,raw,categoryId,index,foreign,chapter} of candidates){
    const objectives=[],rewards=[];let safe=true;
    const tasks=Array.isArray(raw.tasks)?raw.tasks:[];
    for(const [i,task] of tasks.entries()){
      try{objectives.push(convertTask(task,id,i,chapter))}
      catch(error){warnings.push(id+': task '+(task?.type||i)+' not imported: '+error.message);safe=false}
    }
    if(!safe||!objectives.length){warnings.push(id+': skipped to prevent an easier/incomplete quest');continue}
    for(const [i,reward] of (Array.isArray(raw.rewards)?raw.rewards:[]).entries()){
      try{rewards.push(convertReward(reward))}
      catch(error){warnings.push(id+': reward '+(reward?.type||i)+' not imported: '+error.message);safe=false}
    }
    if(!safe){warnings.push(id+': skipped to prevent incomplete rewards');continue}
    const deps=Array.isArray(raw.dependencies)?raw.dependencies:[];
    const minimum=raw.min_required_dependencies??0;
    if(!Number.isInteger(minimum)||minimum<0||minimum>deps.length ||
       (raw.dependency_requirement&&!['all_completed','ALL_COMPLETED','any_completed','ANY_COMPLETED','all_started','ALL_STARTED','any_started','ANY_STARTED'].includes(raw.dependency_requirement))){
      warnings.push(id+': unsupported dependency rule; quest skipped');continue;
    }
    const quest={schemaVersion:2,id,category:categoryId,icon:'minecraft:book',
      title:localized(raw.title||'Imported quest '+(index+1)),objectives,rewards,progression:'once',
      layout:{x:Number.isFinite(raw.x)?raw.x:index*60,y:Number.isFinite(raw.y)?raw.y:0}};
    if(raw.description!==undefined){const lines=Array.isArray(raw.description)?raw.description:[raw.description];if(lines.some(v=>typeof v!=='string')){warnings.push(id+': unsupported description; quest skipped');continue}const localizedLines=lines.map(localized),locales=new Set(localizedLines.flatMap(v=>Object.keys(v)));quest.description=Object.fromEntries([...locales].map(locale=>[locale,localizedLines.map(v=>v[locale]??v.en_us).join('\n')]));}
    if(raw.icon!==undefined){const icon=resource(raw.icon);if(!ID.test(icon||'')){warnings.push(id+': unsupported icon; quest skipped');continue}quest.icon=icon}
    if(ftbBool(raw.require_sequential_tasks,ftbBool(chapter?.require_sequential_tasks,false)))quest.objectiveMode='sequence';
    if(['flexible','FLEXIBLE'].includes(raw.progression_mode||chapter?.progression_mode))quest.progressBeforeUnlock=true;
    if(ftbBool(raw.can_repeat,ftbBool(chapter?.default_repeatable_quest,false))){
      quest.progression='repeatable';
      if(Number.isInteger(raw.repeat_cooldown)&&raw.repeat_cooldown>=0&&raw.repeat_cooldown<=31536000)
        quest.cooldownSeconds=raw.repeat_cooldown;
    }
    if(ftbBool(raw.hide_until_deps_complete,false))quest.hidden=true;
    const presentation={};
    if(raw.subtitle) presentation.subtitle=localized(raw.subtitle);
    if(Array.isArray(raw.tags))presentation.tags=raw.tags.filter(v=>typeof v==='string');
    if(raw.shape!==undefined){if(!['square','circle','rounded_square','diamond','pentagon','hexagon','octagon','heart','gear'].includes(raw.shape)){warnings.push(id+': unsupported shape; quest skipped');continue}presentation.shape=raw.shape}
    if(raw.icon_scale!==undefined){if(typeof raw.icon_scale!=='number'||raw.icon_scale<.25||raw.icon_scale>2){warnings.push(id+': invalid icon scale; quest skipped');continue}presentation.iconScale=raw.icon_scale}
    for(const [from,to] of Object.entries({optional:'optional',disable_completion_toast:'disableCompletionToast',hide_details_until_unlocked:'hideDetailsUntilUnlocked',hide_text_until_completed:'hideTextUntilCompleted',invisible_until_completed:'invisibleUntilCompleted',hide_dependency_lines:'hideDependencyLines',hide_dependent_lines:'hideDependentLines'}))if(raw[from]!==undefined)presentation[to]=ftbBool(raw[from]);
    if(raw.invisible_until_tasks!==undefined){if(!Number.isInteger(raw.invisible_until_tasks)||raw.invisible_until_tasks<0||raw.invisible_until_tasks>256){warnings.push(id+': invalid invisible task count; quest skipped');continue}presentation.invisibleUntilTasks=raw.invisible_until_tasks}
    if(Object.keys(presentation).length)quest.presentation=presentation;
    for(const item of [...tasks,...(raw.rewards||[])])if(kind(item?.type)==='stage'&&typeof item.stage==='string'&&!item.stage.includes(':'))warnings.push(id+': stage '+item.stage+' mapped to '+stageId(item.stage));
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
      const unresolved=dependencies.some(dep=>ambiguous.has(String(dep))||!ids.has(String(dep))||!existing.has(ids.get(String(dep))));
      if(unresolved){warnings.push(q.id+': prerequisite not imported; quest skipped');existing.delete(q.id);changed=true;return false}
      return true;
    });
  }
  for(const q of project.quests){
    const dependencies=source.get(q.id)?.dependencies;
    if(!Array.isArray(dependencies)||!dependencies.length)continue;
    const raw=source.get(q.id);
    const started=/started/i.test(raw.dependency_requirement||'');
    const conditions=dependencies.map(dep=>started?{started:ids.get(String(dep))}:{quest:ids.get(String(dep))});
    const any=raw.min_required_dependencies===1&&conditions.length>1 ||
        ['any_completed','ANY_COMPLETED','any_started','ANY_STARTED'].includes(raw.dependency_requirement);
    q.requires=conditions.length===1?conditions[0]:raw.min_required_dependencies>1&&raw.min_required_dependencies<conditions.length?{at_least:{count:raw.min_required_dependencies,of:conditions}}:any?{or:conditions}:{and:conditions};
  }
  if(!project.quests.length)warnings.push('No safely convertible quests found. Original FTB files were not modified.');
  return {project,warnings,chapterCount:sources.length,questCount:project.quests.length,sourceIdMap:ids};
}
