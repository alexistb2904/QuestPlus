// Session-scoped, never persistent: the connected server is the authority for
// modded registry IDs. No resource packs, auth tokens, or player state are stored.
const keys=['items','blocks','entities','effects','enchantments','biomes','structures','dimensions','statistics'];
let current={};
const valid=id=>/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(id);
export function setHubRegistry(value){
 const output={};
 for(const key of keys){
  const list=Array.isArray(value?.[key])?value[key]:[];
  output[key]=[...new Set(list.filter(id=>typeof id==='string'&&valid(id)).slice(0,30000))].sort();
 }
 output.tags={};
 for(const key of ['items','blocks','entities']){
  const list=Array.isArray(value?.tags?.[key])?value.tags[key]:[];
  output.tags[key]=[...new Set(list.filter(id=>typeof id==='string'&&id.startsWith('#')&&valid(id.slice(1))).slice(0,30000))].sort();
 }
 output.extensions=[];
 for(const e of Array.isArray(value?.extensions)?value.extensions.slice(0,128):[]){
  if(!e||!['event','condition','reward'].includes(e.kind)||!valid(e.id)||typeof e.label!=='string'||e.label.length>80||!Array.isArray(e.fields)||e.fields.length>20)continue;
  const fields=e.fields.filter(f=>f&&/^[a-z][a-zA-Z0-9_]{0,39}$/.test(f.key)&&['string','integer','boolean','item','block','entity','biome','dimension','enchantment'].includes(f.type)&&typeof f.label==='string'&&f.label.length<=80).map(({key,type,label})=>({key,type,label}));
  if(fields.length===e.fields.length)output.extensions.push({kind:e.kind,id:e.id,label:e.label,fields});
 }
 current=output;
 return output;
}
export function hubRegistry(){return current}
export function hubTargets(kind){
 const map={item:'items',block:'blocks',entity:'entities',biome:'biomes',structure:'structures',enchantment:'enchantments',effect:'effects',dimension:'dimensions',statistic:'statistics'};
 return current[map[kind]||kind]||[];
}

/** Authoring-only metadata; gameplay events and validation remain server-owned. */
export function hubEditorDescriptors(kind){return (current.extensions||[]).filter(d=>d.kind===kind)}
