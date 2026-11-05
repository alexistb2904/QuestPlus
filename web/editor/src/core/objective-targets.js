// Namespaced targets used by server-side marker events. Distances are stored in cm.
export const MARKERS={location:'questplus:position',travel_distance:'questplus:travel',player_death:'questplus:death'};
export const DIMENSIONS=[
 ['minecraft:overworld','Overworld'],['minecraft:the_nether','Nether'],['minecraft:the_end','The End']
];
export const BIOMES=[
 'badlands','bamboo_jungle','basalt_deltas','beach','birch_forest','cherry_grove','cold_ocean',
 'crimson_forest','dark_forest','deep_cold_ocean','deep_dark','deep_frozen_ocean','deep_lukewarm_ocean',
 'deep_ocean','desert','dripstone_caves','end_barrens','end_highlands','end_midlands','eroded_badlands',
 'flower_forest','forest','frozen_ocean','frozen_peaks','frozen_river','grove','ice_spikes','jagged_peaks',
 'jungle','lukewarm_ocean','lush_caves','mangrove_swamp','meadow','mushroom_fields','nether_wastes',
 'ocean','old_growth_birch_forest','old_growth_pine_taiga','old_growth_spruce_taiga','plains','river',
 'savanna','savanna_plateau','small_end_islands','snowy_beach','snowy_plains','snowy_slopes',
 'snowy_taiga','soul_sand_valley','sparse_jungle','stony_peaks','stony_shore','sunflower_plains',
 'swamp','taiga','the_end','the_void','warm_ocean','warped_forest','windswept_forest',
 'windswept_gravelly_hills','windswept_hills','windswept_savanna','wooded_badlands'
].map(id=>'minecraft:'+id);
export const STRUCTURES=[
 'ancient_city','bastion_remnant','buried_treasure','desert_pyramid','end_city','fortress',
 'igloo','jungle_pyramid','mansion','mineshaft','mineshaft_mesa','monument','nether_fossil',
 'ocean_ruin_cold','ocean_ruin_warm','pillager_outpost','ruined_portal','ruined_portal_desert',
 'ruined_portal_jungle','ruined_portal_mountain','ruined_portal_nether','ruined_portal_ocean',
 'ruined_portal_swamp','shipwreck','shipwreck_beached','stronghold','swamp_hut','trail_ruins',
 'trial_chambers','village_desert','village_plains','village_savanna','village_snowy',
 'village_taiga'
].map(id=>'minecraft:'+id);
export const ENTITIES=[
 'allay','armadillo','axolotl','bat','bee','blaze','bogged','breeze','camel','cat','cave_spider',
 'chicken','cod','cow','creaking','creeper','dolphin','donkey','drowned','elder_guardian',
 'ender_dragon','enderman','endermite','evoker','fox','frog','ghast','glow_squid','goat',
 'guardian','hoglin','horse','husk','iron_golem','llama','magma_cube','mooshroom','mule',
 'ocelot','panda','parrot','phantom','pig','piglin','piglin_brute','pillager','polar_bear',
 'pufferfish','rabbit','ravager','salmon','sheep','shulker','silverfish','skeleton',
 'skeleton_horse','slime','sniffer','snow_golem','spider','squid','stray','strider',
 'tadpole','trader_llama','tropical_fish','turtle','vex','villager','vindicator',
 'wandering_trader','warden','witch','wither','wither_skeleton','wolf','zoglin',
 'zombie','zombie_horse','zombie_villager','zombified_piglin'
].map(id=>'minecraft:'+id);
export const targetKind=event=>
 ['block_break','block_place','block_interact'].includes(event)?'block':
 ['item_obtained','item_craft','item_smelt','item_use','item_consume','fish_item','trade','inventory_count','item_submit','item_turn_in'].includes(event)?'item':
 ['entity_kill','fish_entity','tame_animal','breed_animal','npc_interact'].includes(event)?'entity':
 event==='fluid_transfer'?'fluid':event==='biome_visit'?'biome':event==='structure_visit'?'structure':null;
export const initialTarget=event=>(
 event==='dimension_enter'?'minecraft:overworld':MARKERS[event]||
 (targetKind(event)==='block'?'minecraft:stone':targetKind(event)==='item'?'minecraft:stick':
 targetKind(event)==='entity'?'minecraft:zombie':targetKind(event)==='biome'?'minecraft:plains':
 targetKind(event)==='structure'?'minecraft:village_plains':targetKind(event)==='fluid'?'minecraft:water':'questplus:manual')
);
export const coordinateBounds=(raw,axis,value)=>{
 const previous=raw?.position||(Array.isArray(raw?.and)?raw.and.find(n=>n?.position)?.position:null);
 const bounds={...(previous||{})};
 if(value==='') {delete bounds[axis+'Min'];delete bounds[axis+'Max'];}
 else{bounds[axis+'Min']=Number(value);bounds[axis+'Max']=Number(value);}
 const leaf={position:bounds};
 if(!raw||raw.position)return leaf;
 if(Array.isArray(raw.and)){
   const next=raw.and.filter(n=>!n?.position);
   return {and:[...next,leaf]};
 }
 return {and:[raw,leaf]};
};
export const positionValue=(root,axis)=>{
 const node=root?.position?root:Array.isArray(root?.and)?root.and.find(n=>n?.position):null;
 const b=node?.position||{};
 return b[axis+'Min']===b[axis+'Max']&&b[axis+'Min']!==undefined?b[axis+'Min']:'';
};


/**
 * Every built-in event declared by QuestExtensions has a deliberate input contract.
 * Mod-registered event IDs keep the unrestricted namespaced target editor.
 */
export const OBJECTIVE_UI=Object.freeze({
  block_break:{kind:'block',amount:'Blocks'},
  block_place:{kind:'block',amount:'Blocks'},
  block_interact:{kind:'block',amount:'Interactions'},
  item_obtained:{kind:'item',amount:'Items obtained'},
  item_craft:{kind:'item',amount:'Items crafted'},
  item_smelt:{kind:'item',amount:'Items smelted'},
  item_use:{kind:'item',amount:'Uses'},
  item_consume:{kind:'item',amount:'Items consumed'},
  fish_item:{kind:'item',amount:'Items caught'},
  trade:{kind:'item',amount:'Items traded'},
  inventory_count:{kind:'item',amount:'Required in inventory'},
  item_submit:{kind:'item',amount:'Items to verify'},
  item_turn_in:{kind:'item',amount:'Items to consume'},
  fluid_transfer:{kind:'fluid',amount:'Fluid (mB)'},
  energy_transfer:{kind:'custom',amount:'Energy (FE)'},
  entity_kill:{kind:'entity',amount:'Kills'},
  fish_entity:{kind:'entity',amount:'Entities caught'},
  tame_animal:{kind:'entity',amount:'Animals tamed'},
  breed_animal:{kind:'entity',amount:'Animals bred'},
  npc_interact:{kind:'entity',amount:'Interactions'},
  biome_visit:{kind:'biome',amount:'Visits'},
  structure_visit:{kind:'structure',amount:'Visits'},
  dimension_enter:{kind:'dimension',amount:'Entries'},
  travel_distance:{kind:'distance',amount:'Distance (m)'},
  location:{kind:'position',amount:null},
  player_death:{kind:'death',amount:'Deaths'},
  equipment_check:{kind:'custom',amount:null},
  xp_levels:{kind:'custom',amount:'XP Levels'},
  custom_stat:{kind:'custom',amount:'Stat value'},
  advancement_complete:{kind:'custom',amount:null},
  'questplus:fish_any':{kind:'custom',amount:'Fish'},
  manual:{kind:'manual',amount:'Confirmations'}
});
export const objectiveUi=event=>OBJECTIVE_UI[event]||{kind:'custom',amount:'Count'};
export const objectiveTarget=event=>objectiveUi(event).kind;
export const removePositionCondition=root=>{
  if(!root||typeof root!=='object')return root;
  if(Object.hasOwn(root,'position'))return null;
  if(Array.isArray(root.and)) {
    const remaining=root.and.map(removePositionCondition).filter(Boolean);
    return remaining.length===0?null:remaining.length===1?remaining[0]:{and:remaining};
  }
  return root;
};
export const changeObjectiveEvent=(objective,event,questId)=>{
  if(objective.event===event)return objective;
  const oldKind=objectiveUi(objective.event).kind,newKind=objectiveUi(event).kind;
  // Switching between related events must not erase an already chosen modded ID.
  const sameRegistry=oldKind===newKind && event!=='energy_transfer' && ['block','item','entity','biome','structure','dimension','fluid','custom'].includes(newKind);
  const target=sameRegistry?objective.target:event==='questplus:fish_any'?'questplus:fish':event==='equipment_check'?'questplus:gear':event==='xp_levels'?'questplus:levels':event==='energy_transfer'?'questplus:fe':event==='custom_stat'?'minecraft:jump':event==='advancement_complete'?'minecraft:story/mine_stone':event==='manual'?(questId||'questplus:manual'):initialTarget(event);
  const next={...objective,event,target,count:sameRegistry?objective.count:event==='travel_distance'?100:1};
  const cleaned=removePositionCondition(objective.conditions);
  if(cleaned)next.conditions=cleaned;
  else delete next.conditions;
  return next;
};
const positionBounds=raw=>raw?.position||(Array.isArray(raw?.and)?raw.and.find(n=>n?.position)?.position:null)||{};
export const positionCoordinate=(raw,axis)=>{
  const bounds=positionBounds(raw),min=bounds[axis+'Min'],max=bounds[axis+'Max'];
  return Number.isFinite(min)&&Number.isFinite(max)?(min+max)/2:'';
};
export const positionRadius=raw=>{
  const b=positionBounds(raw);
  const radii=['x','y','z'].filter(a=>Number.isFinite(b[a+'Min'])&&Number.isFinite(b[a+'Max']))
    .map(a=>(b[a+'Max']-b[a+'Min'])/2);
  return radii.length?Math.max(...radii):2;
};
/** A small reachable box around the location (exact coordinates can be radius 0). */
export const setPositionCoordinate=(root,axis,value,radius=positionRadius(root))=>{
  const b={...positionBounds(root)},r=Math.max(0,Math.min(128,Number(radius)||0));
  if(value==='') {delete b[axis+'Min'];delete b[axis+'Max'];}
  else {
    const center=Number(value);
    if(!Number.isFinite(center))return root;
    b[axis+'Min']=center-r;b[axis+'Max']=center+r;
  }
  const leaf=Object.keys(b).length?{position:b}:null;
  if(root?.position || !root)return leaf;
  if(Array.isArray(root.and)){
    const remaining=root.and.filter(x=>!x?.position);
    if(leaf)remaining.push(leaf);
    return remaining.length===0?null:remaining.length===1?remaining[0]:{and:remaining};
  }
  return leaf?{and:[root,leaf]}:root;
};
export const setPositionRadius=(root,value)=>{
  let next=root,r=Math.max(0,Math.min(128,Number(value)||0));
  for(const axis of ['x','y','z']){
    const center=positionCoordinate(root,axis);
    if(center!=='')next=setPositionCoordinate(next,axis,center,r);
  }
  return next;
};

export const EVENT_LABELS={
  "block_break": "Break blocks",
  "block_place": "Place blocks",
  "block_interact": "Interact with block",
  "item_obtained": "Obtain items",
  "item_craft": "Craft items",
  "item_smelt": "Smelt items",
  "item_use": "Use item",
  "item_consume": "Consume item",
  "fish_item": "Fish item",
  "trade": "Trade item",
  "inventory_count": "Hold items",
  "item_submit": "Submit without consuming",
  "item_turn_in": "Submit and consume",
  "fluid_transfer": "Transfer fluid",
  "energy_transfer": "Transfer energy",
  "entity_kill": "Kill entities",
  "fish_entity": "Fish entity",
  "questplus:fish_any": "Fish (any species)",
  "tame_animal": "Tame animal",
  "breed_animal": "Breed animals",
  "npc_interact": "Interact with NPC",
  "biome_visit": "Visit biome",
  "structure_visit": "Visit structure",
  "dimension_enter": "Enter dimension",
  "travel_distance": "Travel distance",
  "location": "Reach position",
  "player_death": "Die",
  "equipment_check": "Verified Equipment",
  "xp_levels": "Reach an XP level",
  "custom_stat": "Reach a Minecraft stat",
  "advancement_complete": "Complete an advancement",
  "manual": "Manual confirmation"
};
