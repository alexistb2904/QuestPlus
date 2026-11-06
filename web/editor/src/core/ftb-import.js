import JSON5 from 'json5';
import {parseSnbt} from './snbt.js';
import {emptyProject,slug,references} from './project.js';
import {poolError} from './reward-tables.js';
import {mediaPattern} from './rich-format.js';
import {hubEditorDescriptors} from './hub-registry.js';

/** Conservative FTB Quests 26.x JSON5 converter. Failing tasks/dependencies never disappear silently. */
const ID=/^#?[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const validCount=value=>Number.isSafeInteger(value)&&value>=1&&value<=1_000_000_000;
const resource=value=>typeof value==='string'?value:value&&typeof value==='object'&&!Array.isArray(value)?value.id:null;
const taskFields={item:['item','count','consume_items','only_from_crafting','match_components','task_screen_only'],kill:['entity','entityTypeTag','value','count','custom_name','nbt_filter'],xp:['value','points'],
 observation:['observation_type','to_observe','timer'],observe_block:['block','ticks','value','count'],observe_entity:['entity','ticks','value','count'],stage:['stage','team_stage'],gamestage:['stage','team_stage'],
 biome:['biome'],structure:['structure'],dimension:['dimension'],stat:['stat','value','count'],advancement:['advancement'],checkmark:[],location:['position','size','radius','dimension','ignore_dimension'],
 fluid:['fluid','amount'],energy:['value','energy','count'],forge_energy:['value','energy','count'],interaction:['interaction_type','to_interact','count']};
const rewardFields={item:['item','count','random_bonus','only_one'],xp:['xp','amount'],xp_levels:['xp_levels','amount'],advancement:['advancement','criterion'],stage:['stage','remove','silent'],gamestage:['stage','remove','silent'],toast:['description'],
 choice:['table_id','table_data'],random:['table_id','table_data'],loot:['table_id','table_data'],all_table:['table_id','table_data'],command:['command','permission_level','silent','feedback_message']};
function checkFields(value,extra,kind){
 const common=kind==='task'?['id','type','title','icon','tags','disable_completion_toast']:['id','type','title','icon','tags','weight','auto','team_reward','exclude_from_claim_all','ignore_reward_blocking','disable_reward_screen_blur'];
 const unknown=Object.keys(value).filter(key=>![...common,...extra].includes(key));if(unknown.length)throw Error('unsupported '+kind+' fields: '+unknown.join(', '));
}
const kind=value=>{
  const raw=String(value||'');
  if(raw.includes(':')&&!raw.startsWith('ftbquests:'))throw Error('third-party type requires a registered adapter: '+raw);
  return raw.replace(/^ftbquests:/,'');
};

function ftbBool(value,inherited=false){
  if(value===undefined||value===null||value==='default')return inherited;
  if(value===true||value===1||value==='true')return true;
  if(value===false||value===0||value==='false')return false;
  throw Error('unknown FTB tristate '+String(value));
}
function extensionData(kind,value){
  if(typeof value?.type!=='string'||!value.type.includes(':')||value.type.startsWith('ftbquests:'))return null;
  const descriptor=hubEditorDescriptors(kind).find(d=>d.id===value.type);if(!descriptor)return null;
  const common=['id','type','title','icon','tags','target','count','value','data','auto','team_reward','exclude_from_claim_all','weight'];
  if(Object.keys(value).some(k=>!common.includes(k)&&!descriptor.fields.some(f=>f.key===k)))throw Error('unknown fields for registered FTB adapter');
  const data={...(value.data||{})};
  for(const field of descriptor.fields){const item=data[field.key]??value[field.key];
    if(field.type==='integer'?!Number.isSafeInteger(item):field.type==='boolean'?typeof item!=='boolean':field.type==='string'?typeof item!=='string':!ID.test(item||''))throw Error('invalid adapter field '+field.key);
    data[field.key]=item;
  }
  if(Object.keys(data).some(k=>!descriptor.fields.some(f=>f.key===k)))throw Error('unknown adapter parameters');return {descriptor,data};
}
function itemFilterTargets(value){
  if(typeof value!=='string'||value.length>4096)throw Error('unsupported FTB item filter');
  let index=0,nodes=0;
  const read=(depth=0)=>{
    if(depth>16||++nodes>128)throw Error('item filter exceeds limits');while(/\s/.test(value[index]||'')&&index<value.length)index++;
    const match=/^(item|tag|or)\(/.exec(value.slice(index));if(!match)throw Error('item filter needs a matching adapter');index+=match[0].length;
    if(match[1]!=='or') {const end=value.indexOf(')',index);if(end<0)throw Error('unclosed item filter');const id=value.slice(index,end).trim();index=end+1;if(!ID.test(id))throw Error('invalid filter target');return [(match[1]==='tag'?'#':'')+id];}
    const targets=[];while(index<value.length&&value[index]!==')'){if(/[\s,]/.test(value[index])){index++;continue;}targets.push(...read(depth+1));}
    if(value[index++]!==')'||!targets.length)throw Error('invalid item filter');return targets;
  };
  const targets=[...new Set(read())];if(value.slice(index).trim()||targets.length>32)throw Error('unsupported/large item filter');return targets;
}
function convertTask(t,id,index,chapter){
  if(!t||typeof t!=='object'||Array.isArray(t))throw Error('invalid task');
  const custom=extensionData('event',t);if(custom){const target=resource(t.target),count=t.count??t.value??1;if(!ID.test(target||'')||!validCount(count))throw Error('registered adapter requires a normalized target/count');return {id:'step'+(index+1),event:custom.descriptor.id,target,count,data:custom.data};}
  const type=kind(t.type??'item'),count=t.count??t.value??1;
  if(taskFields[type])checkFields(t,taskFields[type],'task');
  const withTarget=(event,target,quantity=count,conditions=undefined)=>{
    if(!ID.test(target||'')||!validCount(quantity))throw Error('invalid target or count');
    return {id:'step'+(index+1),event,target,count:quantity,...(conditions?{conditions}:{})};
  };
  switch(type){
    case 'item': {
      if(t.item?.tag)throw Error('legacy item NBT requires a version-specific conversion');
      if(JSON.stringify(t.item?.components||{}).includes('@long:'))throw Error('64-bit component numbers require lossless NBT conversion');
      const consume=ftbBool(t.consume_items,ftbBool(chapter?.consume_items,false));
      const craft=ftbBool(t.only_from_crafting,false);
      const screen=ftbBool(t.task_screen_only,false);
      if(screen)throw Error('FTB Task Screen-only automation is not equivalent to a player action');
      if(consume&&craft)throw Error('crafted-and-consumed item semantics are not supported together');
      const filter=resource(t.item)==='ftbfiltersystem:smart_filter'?itemFilterTargets(t.item?.components?.['ftbfiltersystem:filter']):null;
      const objective=withTarget(craft?'item_craft':consume?'item_turn_in':'inventory_count',filter?.[0]||resource(t.item),t.count??t.item?.count??1);
      const mode=String(t.match_components||'none').toLowerCase();
      if(!['none','exact','partial'].includes(mode))throw Error('unsupported item matching mode '+mode);
      if(filter){if(mode!=='none')throw Error('component matching on smart filters requires an adapter');objective.data={targets:filter};objective.text={en_us:(consume?'Turn in ':craft?'Craft ':'Obtain ')+objective.count+' × any of: '+filter.join(', ')};}
      else if(mode!=='none')objective.data={components:t.item?.components||{},matchComponents:mode};
      return objective;
    }
    case 'observation': {
      const mode=String(t.observation_type||'block').toLowerCase();
      if(!['block','block_tag','entity_type','entity_type_tag'].includes(mode))throw Error('observation predicate requires a server adapter: '+mode);
      const target=(mode.endsWith('_tag')?'#':'')+String(t.to_observe||'').replace(/^#/,'');
      return withTarget(mode.startsWith('entity')?'observe_entity':'observe_block',target,Math.max(1,t.timer??0));
    }
    case 'observe_block':return withTarget('observe_block',resource(t.block),t.ticks??t.value??t.count??1);
    case 'observe_entity':return withTarget('observe_entity',resource(t.entity),t.ticks??t.value??t.count??1);
    case 'stage':case 'gamestage':
      if(ftbBool(t.team_stage,false))throw Error('team stage requires a matching stage provider');
      return withTarget('stage',stageId(t.stage),1);
    case 'kill':
      if(t.custom_name||t.nbt_filter)throw Error('entity name/NBT predicate requires a server adapter');
      return withTarget('entity_kill',t.entityTypeTag?'#'+t.entityTypeTag:resource(t.entity),t.value??t.count??1);
    case 'biome':return withTarget('biome_visit',resource(t.biome),1);
    case 'structure':return withTarget('structure_visit',resource(t.structure),1);
    case 'dimension':return withTarget('dimension_enter',resource(t.dimension),1);
    case 'stat':return withTarget('custom_stat',resource(t.stat),t.value??t.count??1);
    case 'advancement':return withTarget('advancement_complete',resource(t.advancement),1);
    case 'xp': {
      return ftbBool(t.points,false)?withTarget('xp_submit_points','questplus:points',t.value??1):withTarget('xp_submit_levels','questplus:levels',t.value??1);
    }
    case 'checkmark':return withTarget('manual',id,1);
    case 'fluid': {
      if(t.fluid?.components&&Object.keys(t.fluid.components).length)throw Error('fluid component predicates require a matching adapter');
      const amount=t.fluid?.amount??t.amount??(chapter._fluidUnits==='droplets'?81000:1000);
      return withTarget('fluid_submit',resource(t.fluid),chapter._fluidUnits==='droplets'?amount/81:amount);
    }
    case 'energy':case 'forge_energy': {
      const objective=withTarget('energy_submit','questplus:fe',t.value??t.energy??t.count??1),ratio=chapter._energyRatio||1;
      if(objective.count%ratio!==0)throw Error('FE amount is not divisible by the selected Fabric energy ratio');
      return {...objective,data:{fePerEnergy:ratio}};
    }
    case 'interaction': {
      const mode=String(t.interaction_type||'block').toLowerCase();
      if(!['block','block_tag','entity_type','entity_type_tag'].includes(mode))throw Error('interaction predicate requires a matching adapter');
      return withTarget(mode.startsWith('entity')?'npc_interact':'block_interact',(mode.endsWith('_tag')?'#':'')+String(t.to_interact||'').replace(/^#/,''),t.count??1);
    }
    case 'location': {
      if(!Array.isArray(t.position)||t.position.length!==3||!t.position.every(Number.isFinite))
        throw Error('location requires three finite coordinates');
      const radius=t.radius??0;
      if(!Number.isFinite(radius)||radius<0||radius>128)throw Error('location radius exceeds Quest Plus maximum');
      const [x,y,z]=t.position;
      if(t.size!==undefined&&(!Array.isArray(t.size)||t.size.length!==3||!t.size.every(v=>Number.isInteger(v)&&v>=1)))throw Error('invalid location size');
      const [w,h,d]=t.size||[radius*2,radius*2,radius*2];
      const position=t.size?{blockCoordinates:true,xMin:x,xMax:x+w-1,yMin:y,yMax:y+h-1,zMin:z,zMax:z+d-1}:{xMin:x-w/2,xMax:x+w/2,yMin:y-h/2,yMax:y+h/2,zMin:z-d/2,zMax:z+d/2};
      const dimension=resource(t.dimension)||(t.size?'minecraft:overworld':null);
      const conditions=dimension&&!ftbBool(t.ignore_dimension,false)&&ID.test(dimension)
        ?{and:[{position},{dimension}]}:{position};
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
function convertReward(r,tables=new Map(),chain=[]){
  if(!r||typeof r!=='object'||Array.isArray(r))throw Error('invalid reward');
  const custom=extensionData('reward',r);if(custom)return {type:custom.descriptor.id,data:custom.data,text:{en_us:r.title||custom.descriptor.label}};
  const type=kind(r.type||'item');
  if(rewardFields[type])checkFields(r,rewardFields[type],'reward');
  if(ftbBool(r.ignore_reward_blocking,false))throw Error('reward-blocking override requires explicit conversion');
  if(type==='xp'||type==='xp_levels'){
    const amount=r[type]??r.amount;
    if(!validCount(amount)||amount>1_000_000)throw Error('invalid XP amount');
    return {type:type==='xp'?'experience':'experience_levels',amount};
  }
  if(type==='advancement') {
    const advancement=resource(r.advancement);
    if(!ID.test(advancement||''))throw Error('invalid advancement reward ID');
    return {type:'questplus:advancement',data:{advancement,...(r.criterion?{criterion:r.criterion}:{})}};
  }
  if(type==='stage'||type==='gamestage')return {type:'questplus:stage',data:{stage:stageId(r.stage),remove:ftbBool(r.remove,false)}};
  if(type==='toast'){const icon=resource(r.icon)||'minecraft:book';if(!ID.test(icon))throw Error('invalid toast icon');return {type:'questplus:toast',data:{title:{en_us:String(r.title||'')},description:{en_us:String(r.description||'')},icon}}}
  if(type==='item'){
    if(r.item?.tag)throw Error('legacy item NBT requires a version-specific conversion');
    if(JSON.stringify(r.item?.components||{}).includes('@long:'))throw Error('64-bit component numbers require lossless NBT conversion');
    let item=resource(r.item);const count=r.count??r.item?.count??1,components=structuredClone(r.item?.components||{});
    if(item==='ftbquests:lootcrate'||components['ftbquests:loot_crate']!==undefined) {
      const crateId=components['ftbquests:loot_crate'],matches=[...tables.entries()].filter(([,t])=>t.loot_crate?.string_id===crateId);
      if(matches.length!==1||matches[0][1]._crateUnavailable)throw Error('unknown/unconvertible loot crate '+String(crateId));
      const [key,table]=matches[0];item='questplus:loot_crate';delete components['ftbquests:loot_crate'];
      if(components['minecraft:custom_data']&&typeof components['minecraft:custom_data']!=='object')throw Error('ambiguous crate custom data');
      components['minecraft:custom_data']={...(components['minecraft:custom_data']||{}),questplus_table:'ftb_crate/'+key};
      if(table.loot_crate.item_name&&!components['minecraft:custom_name'])components['minecraft:custom_name']={text:table.loot_crate.item_name,color:'#'+((table.loot_crate.color??0xffffff)>>>0&0xffffff).toString(16).padStart(6,'0')};
      if(table.loot_crate.glow)components['minecraft:enchantment_glint_override']=true;
    }
    if(!ID.test(item||'')||item==='minecraft:air'||!Number.isInteger(count)||count<1||count>2304)
      throw Error('invalid item reward');
    return {type:'item',item,count,...(Object.keys(components).length?{components}:{}),...(r.random_bonus?{randomBonus:r.random_bonus}:{}),...(ftbBool(r.only_one,false)?{onlyOne:true}:{})};
  }
  if(['choice','random','loot','all_table'].includes(type)) {
    const key=sourceId(r.table_id),table=r.table_data||tables.get(key);
    if(!table)throw Error('unknown reward table '+String(r.table_id));
    if(chain.includes(key)||chain.length>=16)throw Error('cyclic/deep reward table');
    const options=(table.rewards||[]).map(entry=>{
      const option=convertReward(entry.reward||entry,tables,[...chain,key]);
      if(['questplus:choice','questplus:random','questplus:loot','questplus:all','questplus:command'].includes(option.type))throw Error('nested pools/commands need a crate item');
      return {...option,...(type!=='choice'?{weight:entry.weight??1}:{})};
    });
    const data={options};
    if(type==='loot'){data.emptyWeight=table.empty_weight??0;data.rolls=table.loot_size??1;}
    if(type==='random')data.rolls=table.loot_size??1;
    if(type!=='choice'&&type!=='all_table') {
      const weights=[...options.map(o=>o.weight),...(type==='loot'?[data.emptyWeight]:[])];
      if(weights.some(w=>!Number.isFinite(w)||w<0))throw Error('invalid source table weights');
      const decimals=Math.max(0,...weights.map(w=>(String(w).split('.')[1]||'').length));if(decimals>6)throw Error('table weights exceed lossless conversion precision');
      const factor=10**decimals,values=weights.map(w=>Math.round(w*factor));
      if(values.some((v,i)=>!Number.isSafeInteger(v)||weights[i]>0&&v===0))throw Error('weights cannot be represented losslessly');
      const gcd=(a,b)=>b?gcd(b,a%b):a;const divisor=values.reduce((a,b)=>gcd(a,b),0)||1;
      options.forEach((o,i)=>o.weight=values[i]/divisor);if(type==='loot')data.emptyWeight=values.at(-1)/divisor;
    }
    if(type==='all_table')for(const option of options)option.weight=1;
    const converted={type:'questplus:'+(type==='all_table'?'all':type),data};const error=poolError(converted);if(error)throw Error(error);return converted;
  }
  if(type==='command') {
    if((r.permission_level??0)>2)throw Error('command requires permissions above the trusted GAMEMASTER limit');
    if(/[{}]/.test(r.command||'')||r.feedback_message)throw Error('command substitutions/feedback need explicit conversion');
    return {type:'questplus:command',data:{command:r.command,permissionLevel:r.permission_level??0,silent:ftbBool(r.silent,false)}};
  }
  throw Error('unsupported FTB reward '+(r.type||'(missing type)'));
}

function sourceId(value) {
  if(typeof value==='number') {
    if(!Number.isSafeInteger(value))throw Error('unsafe source ID');
    return BigInt.asUintN(64,BigInt(value)).toString(16).padStart(16,'0');
  }
  const raw=String(value??'');
  if(raw.startsWith('@long:'))return BigInt.asUintN(64,BigInt(raw.slice(6))).toString(16).padStart(16,'0');
  return /^[0-9a-f]{16}$/i.test(raw)?raw.toLowerCase():raw;
}

// Preserve long IDs before JSON5 converts them to imprecise JavaScript numbers.
function parseDocument(name,content) {
  const source=String(content).replace(/^\uFEFF/,'');
  if(source.length>2_000_000)throw Error('Source document exceeds 2 MB');
  if(/\.snbt$/i.test(name))return parseSnbt(source,{preserveLongs:true});
  const protectedSource=source.replace(/"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\/|(?<![\w$])[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?/g,token=>{
    if(!/^[-+]?\d+$/.test(token)||Number.isSafeInteger(Number(token)))return token;
    return JSON.stringify('@long:'+token);
  });
  return JSON5.parse(protectedSource);
}

// Unknown required semantics are reported and block the affected quest and its dependents.
export function inspectFtbPack(documents,{fluidUnits='millibuckets',energyRatio=1}={}){
  if(!['millibuckets','droplets'].includes(fluidUnits)||!Number.isSafeInteger(energyRatio)||energyRatio<1||energyRatio>1000000)throw Error('Invalid source resource units');
  const project=emptyProject(),warnings=[],sources=[],ids=new Map(),ambiguous=new Set(),seen=new Set(),candidates=[];
  const translations={},parsed=new Map(),tables=new Map(),groups=new Map();let defaults={};
  for(const {name,content} of documents){
    const path=String(name).replace(/\\/g,'/');
    if(!/\.(?:json5?|snbt)$/i.test(path))continue;
    try{parsed.set(path,parseDocument(path,content));}catch(error){warnings.push(name+': invalid data: '+error.message);}
  }
  for(const [path,root] of parsed){
    const locale=path.match(/(?:^|\/)lang\/([a-z]{2,3}_[a-z]{2,4})(?:\/[^.]+)?\.(?:json5?|snbt)$/i);
    if(locale){translations[locale[1].toLowerCase()]={...(translations[locale[1].toLowerCase()]||{}),...root};continue;}
    if(/(?:^|\/)data\.(?:json5?|snbt)$/i.test(path))defaults=root;
    if(/(?:^|\/)reward_tables\/[^/]+\.(?:json5?|snbt)$/i.test(path)){
      const key=sourceId(root.id??path.split('/').at(-1).replace(/\.[^.]+$/,''));
      if(tables.has(key))throw Error('Duplicate FTB reward table '+key);tables.set(key,root);
    }
    if(/(?:^|\/)chapter_groups\.(?:json5?|snbt)$/i.test(path))for(const group of root.chapter_groups||root.groups||[]){
      groups.set(sourceId(group.id),group);
    }
  }
  const format=value=>String(value??'').replace(/&([0-9a-flmnor])/gi,'§$1');
  const localized=value=>{const raw=String(value??'');const result={};for(const [locale,entries] of Object.entries(translations))if(typeof entries[raw]==='string')result[locale]=format(entries[raw]);if(!result.en_us)result.en_us=format(raw);return result};
  const translated=(object,type,field,fallback)=>{
    const result=localized(fallback??'');
    for(const [locale,entries] of Object.entries(translations)){
      const value=entries[type+'.'+String(object.id)+'.'+field]??entries[type+'.'+String(object.id).toUpperCase()+'.'+field];
      if(typeof value==='string'||Array.isArray(value)&&value.every(v=>typeof v==='string'))result[locale]=format(Array.isArray(value)?value.join('\n'):value);
    }
    return result;
  };
  for(const [key,table] of tables) {
    try {
      const reward=convertReward({type:'loot',table_id:key},tables),crate=table.loot_crate;
      project.rewardTables||=[];
      project.rewardTables.push({name:crate?'ftb_crate/'+key:'ftb_table/'+key,reward,...(crate?{crate:true,crateName:crate.item_name||translated(table,'reward_table','title',table.title||crate.string_id||key).en_us,
        crateColor:'#'+((crate.color??0xffffff)>>>0&0xffffff).toString(16).padStart(6,'0'),crateGlow:ftbBool(crate.glow,false),crateDrops:crate.drops||{passive:0,monster:0,boss:0}}:{})});
    }catch(error){table._crateUnavailable=true;warnings.push('Reward table '+key+' not imported: '+error.message);}
  }
  let invalidCrates=true;
  while(invalidCrates){invalidCrates=false;const names=new Set((project.rewardTables||[]).filter(t=>t.crate).map(t=>t.name));
    project.rewardTables=(project.rewardTables||[]).filter(table=>{
      const invalid=table.reward.data.options.some(o=>o.item==='questplus:loot_crate'&&!names.has(o.components?.['minecraft:custom_data']?.questplus_table));
      if(invalid){invalidCrates=true;const key=table.name.replace(/^ftb_(?:crate|table)\//,'');if(tables.has(key))tables.get(key)._crateUnavailable=true;warnings.push('Reward table '+key+' skipped: nested crate not imported');return false;}return true;
    });
  }
  if(ftbBool(defaults.drop_loot_crates,false)){project.config.dropLootCrates=true;project.config.crateNoDrop=defaults.loot_crate_no_drop||{passive:4000,monster:600,boss:0};}
  for(const [path,root] of parsed){
    if(!/(?:^|\/)chapters\/[^/]+\.(?:json5?|snbt)$/i.test(path))continue;
    if(!root||typeof root!=='object'||Array.isArray(root)||!Array.isArray(root.quests))continue;
    sources.push({name:path,root});
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
    const categoryId=safeName('questplus:ftb',sourceId(root.id||filename));
    const group=groups.get(sourceId(root.group));
    project.categories.push({id:categoryId,icon:resource(root.icon)||'minecraft:book',title:translated(root,'chapter','title',root.title||filename),
      description:translated(root,'chapter','chapter_subtitle',Array.isArray(root.subtitle)?root.subtitle.join('\n'):root.subtitle||'Imported from FTB Quests'),annotations:[],order:project.categories.length,
      ...(group?{group:translated(group,'chapter_group','title',group.title||String(root.group)).en_us}:typeof root.group==='string'?{group:root.group}:{})});
    if(root.quest_links?.length)warnings.push(categoryId+': quest_links require presentation conversion; source retained in backup');
    const category=project.categories.at(-1);
    for(const [index,image] of (Array.isArray(root.images)?root.images:[]).entries()){
      try{
        if(image.dev)continue;
        if(image.rotation||image.corner||image.dependency||image.click||image.click_action||image.text_on_image||image.alpha!==undefined&&image.alpha!==255||image.color!==undefined&&![0xffffff,-1].includes(image.color))throw Error('rotation, conditional visibility, tint and click actions require explicit conversion');
        if(![image.x,image.y,image.width,image.height].every(Number.isFinite)||image.width<=0||image.height<=0)throw Error('invalid decoration bounds');
        const width=Math.round(image.width*64),height=Math.round(image.height*64),x=image.x*64-width/2,y=image.y*64-height/2;
        const note={id:'ftb_image_'+index,x,y,width,height,style:'label',text:{en_us:''}};
        const value=String(image.image||'');
        if(value.startsWith('text:')){note.text=localized(value.slice(5));note.align=['left','center','right'].includes(image.text_h_align)?image.text_h_align:'center';}
        else{
          const match=value.match(/^([a-z0-9_.-]+):([a-z0-9_./-]+)$/);
          const suffix=match?'assets/'+match[1]+'/'+match[2]:null;
          const candidates=documents.filter(d=>String(d.name).replace(/\\/g,'/').endsWith('/'+suffix)||d.name===suffix);
          const data=mediaPattern.test(value)?value:candidates.length===1?candidates[0].content:null;
          if(!data||!mediaPattern.test(data))throw Error('include the matching local PNG/JPEG/GIF asset; remote media are never fetched');
          note.rich={en_us:[{type:'image',data,width,height}]};
        }
        category.annotations.push(note);
        if(image.order)warnings.push(categoryId+': decoration '+index+' uses native annotation layer order');
      }catch(error){warnings.push(categoryId+': decoration '+index+' not imported: '+error.message);}
    }
    for(const [ix,raw] of root.quests.entries()){
      if(!raw||typeof raw!=='object'||Array.isArray(raw))continue;
      const foreign=raw.id===undefined?null:sourceId(raw.id);
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
    if(ambiguous.has(foreign)){warnings.push(id+': duplicate source quest ID; quest skipped');continue;}
    const known=['id','title','subtitle','description','icon','tags','x','y','shape','size','icon_scale','tasks','rewards','dependencies','dependency_requirement','min_required_dependencies','require_sequential_tasks','progression_mode','can_repeat','repeat_cooldown','hide_until_deps_complete','optional','disable_completion_toast','hide_details_until_unlocked','hide_details_until_startable','hide_text_until_completed','hide_text_until_complete','invisible_until_completed','invisible','hide_until_deps_visible','hide_dependency_lines','hide_dependent_lines','invisible_until_tasks','preset','guide_page','min_width','hide_lock_icon','disable_recipe_mod','max_completable_dependents','ignore_reward_blocking'];
    const unknown=Object.keys(raw).filter(key=>!known.includes(key));if(unknown.length){warnings.push(id+': unsupported quest fields '+unknown.join(', ')+'; quest skipped');continue;}
    const tasks=Array.isArray(raw.tasks)?raw.tasks:[];
    for(const [i,task] of tasks.entries()){
      try{const objective=convertTask(task,id,i,{...defaults,...chapter,consume_items:ftbBool(chapter.consume_items,ftbBool(defaults.default_consume_items,false)),_fluidUnits:fluidUnits,_energyRatio:energyRatio});
        const label=translated(task,'task','title',task.title||'');if(label.en_us.trim())objective.text=label;
        objectives.push(objective);
      }
      catch(error){warnings.push(id+': task '+(task?.type||i)+' not imported: '+error.message);safe=false}
    }
    if(!safe||!objectives.length){warnings.push(id+': skipped to prevent an easier/incomplete quest');continue}
    for(const [i,reward] of (Array.isArray(raw.rewards)?raw.rewards:[]).entries()){
      try{
        let converted=convertReward(reward,tables);
        const type=String(reward.type||'item').replace(/^ftbquests:/,'');
        if(['choice','random','loot','all_table'].includes(type)) {
          const name=reward.table_data?'ftb_inline/'+foreign+'/'+i:'ftb_table/'+sourceId(reward.table_id)+'/'+type;
          project.rewardTables||=[];if(!project.rewardTables.some(t=>t.name===name)){if(project.rewardTables.length>=128)throw Error('named table limit exceeded');project.rewardTables.push({name,reward:structuredClone(converted)});}
          converted={type:'questplus:table',data:{name}};
        }
        const team=ftbBool(reward.team_reward,ftbBool(defaults.default_reward_team_by_type?.[reward.type],ftbBool(defaults.default_reward_team,false)));
        let auto=String(reward.auto??'default').toLowerCase();
        if(auto==='default')auto=String(defaults.default_autoclaim_rewards||'disabled').toLowerCase();
        if(ftbBool(defaults.suppress_all_autoclaiming,false))auto='disabled';
        if(!['disabled','enabled','no_toast','invisible'].includes(auto))throw Error('unknown autoclaim policy '+auto);
        if(team)converted.teamReward=true;if(auto!=='disabled')converted.autoClaim=auto;
        if(ftbBool(reward.exclude_from_claim_all,['choice','random','loot','all_table'].includes(type)))converted.excludeFromClaimAll=true;
        const label=translated(reward,'reward','title',reward.title||'');if(label.en_us.trim()&&converted.type!=='questplus:toast')converted.text=label;
        if(converted.type==='questplus:command'){project.config.enableCommandRewards=true;warnings.push(id+': command reward requires trusted server approval');}
        rewards.push(converted);
      }
      catch(error){warnings.push(id+': reward '+(reward?.type||i)+' not imported: '+error.message);safe=false}
    }
    if(!safe){warnings.push(id+': skipped to prevent incomplete rewards');continue}
    const deps=Array.isArray(raw.dependencies)?raw.dependencies:[];
    const minimum=raw.min_required_dependencies??0;
    if(!Number.isInteger(minimum)||minimum<0||minimum>deps.length ||
       (raw.dependency_requirement&&!['all_completed','ALL_COMPLETED','any_completed','ANY_COMPLETED','one_completed','ONE_COMPLETED','all_started','ALL_STARTED','any_started','ANY_STARTED','one_started','ONE_STARTED'].includes(raw.dependency_requirement))){
      warnings.push(id+': unsupported dependency rule; quest skipped');continue;
    }
    const quest={schemaVersion:2,id,category:categoryId,icon:'minecraft:book',
      title:translated(raw,'quest','title',raw.title||'Imported quest '+(index+1)),objectives,rewards,progression:'once',
      layout:{x:Number.isFinite(raw.x)?raw.x*64:index*64,y:Number.isFinite(raw.y)?raw.y*64:0}};
    const description=translated(raw,'quest','quest_desc',Array.isArray(raw.description)?raw.description.join('\n'):raw.description||'');
    if(Object.values(description).some(v=>v.trim())){if(!description.en_us.trim())description.en_us=Object.values(description).find(v=>v.trim());quest.description=description;}
    if(raw.icon!==undefined){const icon=resource(raw.icon);if(!ID.test(icon||'')){warnings.push(id+': unsupported icon; quest skipped');continue}quest.icon=icon}
    if(ftbBool(raw.require_sequential_tasks,ftbBool(chapter?.require_sequential_tasks,false)))quest.objectiveMode='sequence';
    const mode=[raw.progression_mode,chapter.progression_mode,defaults.progression_mode].find(value=>value&&String(value).toLowerCase()!=='default');
    if(String(mode).toLowerCase()==='flexible')quest.progressBeforeUnlock=true;
    if(ftbBool(raw.can_repeat,ftbBool(chapter?.default_repeatable_quest,false))){
      quest.progression='repeatable';
      if(Number.isInteger(raw.repeat_cooldown)&&raw.repeat_cooldown>=0&&raw.repeat_cooldown<=31536000)
        quest.cooldownSeconds=raw.repeat_cooldown;
    }
    if(ftbBool(raw.hide_until_deps_complete,ftbBool(chapter.hide_quest_until_deps_complete,false)))quest.hidden=true;
    const presentation={};
    const subtitle=translated(raw,'quest','quest_subtitle',raw.subtitle||'');if(subtitle.en_us.trim())presentation.subtitle=subtitle;
    if(Array.isArray(raw.tags))presentation.tags=raw.tags.filter(v=>typeof v==='string').map(v=>v.replace(/^#/,''));
    const preset=(defaults.presets||{})[raw.preset||chapter.preset||defaults.preset]||{};
    const sourceShape=preset.shape||raw.shape||chapter.default_quest_shape||defaults.default_quest_shape;
    const builtInShapes=['square','circle','rounded_square','diamond','pentagon','hexagon','octagon','heart','gear'];
    const shape=sourceShape==='rsquare'?'rounded_square':sourceShape&&!builtInShapes.includes(sourceShape)&&/^[a-z0-9_./-]+$/.test(sourceShape)?'ftbquests:'+sourceShape:sourceShape;
    if(shape){if(!builtInShapes.includes(shape)&&!ID.test(shape)){warnings.push(id+': unsupported shape; quest skipped');continue}presentation.shape=shape;if(shape.includes(':'))warnings.push(id+': custom shape requires the matching local resource pack: '+shape)}
    const size=preset.size||raw.size||chapter.default_quest_size;if(size)quest.size=Math.max(1,Math.round(size*32));
    if(raw.icon_scale!==undefined){if(typeof raw.icon_scale!=='number'||raw.icon_scale<.25||raw.icon_scale>2){warnings.push(id+': invalid icon scale; quest skipped');continue}presentation.iconScale=raw.icon_scale}
    for(const [from,to] of Object.entries({optional:'optional',disable_completion_toast:'disableCompletionToast',hide_details_until_unlocked:'hideDetailsUntilUnlocked',hide_text_until_completed:'hideTextUntilCompleted',invisible_until_completed:'invisibleUntilCompleted',hide_dependency_lines:'hideDependencyLines',hide_dependent_lines:'hideDependentLines'}))if(raw[from]!==undefined)presentation[to]=ftbBool(raw[from]);
    if(chapter.default_hide_dependency_lines!==undefined)presentation.hideDependencyLines=ftbBool(raw.hide_dependency_lines,ftbBool(chapter.default_hide_dependency_lines,false));
    for(const key of ['min_width','hide_lock_icon','disable_recipe_mod'])if(raw[key])warnings.push(id+': presentation setting '+key+' has no exact equivalent in the tablet');
    for(const [from,to,inherited] of [['hide_details_until_startable','hideDetailsUntilUnlocked','hide_quest_details_until_startable'],['hide_text_until_complete','hideTextUntilCompleted','hide_text_until_complete'],['hide_until_deps_visible','hideUntilDependenciesVisible','hide_quest_until_deps_visible'],['invisible','invisibleUntilCompleted',null]])
      if(raw[from]!==undefined||inherited&&chapter[inherited]!==undefined)presentation[to]=ftbBool(raw[from],ftbBool(chapter[inherited],false));
    if(chapter.always_invisible){warnings.push(id+': always-invisible chapter requires explicit presentation conversion; quest skipped');continue;}
    if(raw.invisible_until_tasks!==undefined){if(!Number.isInteger(raw.invisible_until_tasks)||raw.invisible_until_tasks<0||raw.invisible_until_tasks>256){warnings.push(id+': invalid invisible task count; quest skipped');continue}if(presentation.invisibleUntilCompleted&&raw.invisible_until_tasks>0){presentation.invisibleUntilTasks=raw.invisible_until_tasks;delete presentation.invisibleUntilCompleted;}}
    if(Object.keys(presentation).length)quest.presentation=presentation;
    for(const item of [...tasks,...(raw.rewards||[])])if(['stage','gamestage'].includes(String(item?.type).replace(/^ftbquests:/,''))&&typeof item.stage==='string'&&!item.stage.includes(':'))warnings.push(id+': stage '+item.stage+' mapped to '+stageId(item.stage));
    if(objectives.some(o=>o.event==='energy_submit'))warnings.push(id+': energy conversion: 1 Fabric energy unit = '+energyRatio+' FE; verify against the source mod');
    if(objectives.some(o=>o.event==='fluid_submit'))warnings.push(id+': source fluid units: '+fluidUnits+'; converted to millibuckets (81 droplets = 1 mB)');
    for(const key of ['max_completable_dependents','ignore_reward_blocking','guide_page'])if(raw[key]){warnings.push(id+': unsupported setting '+key+'; quest skipped');safe=false;}
    if(!safe)continue;
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
      const unresolved=dependencies.some(dep=>ambiguous.has(sourceId(dep))||!ids.has(sourceId(dep))||!existing.has(ids.get(sourceId(dep))));
      if(unresolved){warnings.push(q.id+': prerequisite not imported; quest skipped');existing.delete(q.id);changed=true;return false}
      return true;
    });
  }
  for(const q of project.quests){
    const dependencies=source.get(q.id)?.dependencies;
    if(!Array.isArray(dependencies)||!dependencies.length)continue;
    const raw=source.get(q.id);
    const started=/started/i.test(raw.dependency_requirement||'');
    const conditions=dependencies.map(dep=>started?{started:ids.get(sourceId(dep))}:{quest:ids.get(sourceId(dep))});
    const any=raw.min_required_dependencies===1&&conditions.length>1 ||
        ['any_completed','ANY_COMPLETED','one_completed','ONE_COMPLETED','any_started','ANY_STARTED','one_started','ONE_STARTED'].includes(raw.dependency_requirement);
    q.requires=conditions.length===1?conditions[0]:raw.min_required_dependencies>1&&raw.min_required_dependencies<conditions.length?{at_least:{count:raw.min_required_dependencies,of:conditions}}:any?{or:conditions}:{and:conditions};
  }
  if(!project.quests.length)warnings.push('No safely convertible quests found. Original FTB files were not modified.');
  project.ftbMappings=project.quests.map(q=>{const raw=source.get(q.id);return {sourceQuest:sourceId(raw.id),targetQuest:q.id,
    tasks:(raw.tasks||[]).map((t,i)=>t.id===undefined?null:({sourceId:sourceId(t.id),objectiveId:q.objectives[i].id})).filter(Boolean),
    rewards:(raw.rewards||[]).map((r,index)=>({sourceId:r.id===undefined?null:sourceId(r.id),index}))};});
  return {project,warnings,chapterCount:sources.length,questCount:project.quests.length,sourceQuestCount:candidates.length,sourceIdMap:ids};
}

/** Import requested quests together with their complete prerequisite closure. */
export function selectFtbQuests(report,selectedIds) {
  const selected=new Set(selectedIds),quests=new Map(report.project.quests.map(q=>[q.id,q])),pending=[...selected];
  while(pending.length){const id=pending.pop(),quest=quests.get(id);if(!quest)throw Error('Unknown import selection: '+id);
    for(const dependency of references(quest.requires))if(!selected.has(dependency)){selected.add(dependency);pending.push(dependency);}
  }
  const project=structuredClone(report.project);project.quests=project.quests.filter(q=>selected.has(q.id));
  const categories=new Set(project.quests.map(q=>q.category));project.categories=project.categories.filter(c=>categories.has(c.id));
  project.ftbMappings=project.ftbMappings?.filter(mapping=>selected.has(mapping.targetQuest));
  return project;
}
