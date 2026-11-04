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
 ['item_obtained','item_craft','item_smelt','item_use','item_consume','fish_item','trade','inventory_count'].includes(event)?'item':
 ['entity_kill','fish_entity','tame_animal','breed_animal','npc_interact'].includes(event)?'entity':
 event==='biome_visit'?'biome':event==='structure_visit'?'structure':null;
export const initialTarget=event=>(
 event==='dimension_enter'?'minecraft:overworld':MARKERS[event]||
 (targetKind(event)==='block'?'minecraft:stone':targetKind(event)==='item'?'minecraft:stick':
 targetKind(event)==='entity'?'minecraft:zombie':targetKind(event)==='biome'?'minecraft:plains':
 targetKind(event)==='structure'?'minecraft:village_plains':'questplus:manual')
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
