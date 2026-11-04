// Session-scoped, never persistent: the connected server is the authority for
// modded registry IDs. No resource packs, auth tokens, or player state are stored.
const keys=['items','blocks','entities','effects','enchantments','biomes','structures','dimensions'];
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
 current=output;
 return output;
}
export function hubRegistry(){return current}
export function hubTargets(kind){
 const map={item:'items',block:'blocks',entity:'entities',biome:'biomes',structure:'structures',enchantment:'enchantments',effect:'effects',dimension:'dimensions'};
 return current[map[kind]||kind]||[];
}
