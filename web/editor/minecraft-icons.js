/** Vanilla icon search for Minecraft Java 26.3 with custom namespaced ID support. */
const BASE='https://cdn.jsdelivr.net/npm/minecraft-textures@26.3.0';
export const MC_VERSION='26.3';
export const COMMON_ITEMS=[
 'book','written_book','enchanted_book','crafting_table','chest','compass','map','paper','clock',
 'stone','cobblestone','deepslate','dirt','grass_block','oak_log','oak_planks','oak_sapling','oak_leaves',
 'iron_pickaxe','stone_pickaxe','diamond_pickaxe','netherite_pickaxe','wooden_pickaxe',
 'iron_sword','diamond_sword','netherite_sword','bow','crossbow','shield','trident',
 'coal','iron_ingot','gold_ingot','copper_ingot','diamond','emerald','lapis_lazuli','redstone','netherite_ingot',
 'raw_iron','iron_ore','deepslate_iron_ore','diamond_ore','obsidian','ancient_debris','amethyst_shard',
 'apple','bread','cooked_beef','carrot','wheat','wheat_seeds','potato','baked_potato','golden_apple',
 'bucket','water_bucket','lava_bucket','fishing_rod','shears','flint_and_steel','torch','lantern',
 'furnace','blast_furnace','smoker','anvil','enchanting_table','brewing_stand','ender_pearl','ender_eye',
 'blaze_rod','bone','string','gunpowder','rotten_flesh','spider_eye','slime_ball','magma_cream','ghast_tear',
 'nether_star','dragon_egg','elytra','totem_of_undying','experience_bottle','zombie_head','skeleton_skull',
 'oak_boat','minecart','saddle','lead','honey_bottle','bee_nest','tnt','glass','glass_bottle','purple_wool',
 'diamond_helmet','iron_chestplate','netherite_chestplate','redstone_block','piston','hopper','observer',
 'shulker_box','end_stone','netherrack','quartz','emerald_block'
];
const catalog=new Map(COMMON_ITEMS.map(s=>['minecraft:'+s,{id:'minecraft:'+s,label:s.replaceAll('_',' '),texture:null}]));
let loaded=false;
export function vanillaCatalog(){return [...catalog.values()].sort((a,b)=>a.label.localeCompare(b.label));}
export function isVanilla(id){return /^minecraft:[a-z0-9_./-]+$/.test(String(id));}
export function iconUrl(id){
  if(!isVanilla(id))return '';
  const value=catalog.get(id);
  if(value?.texture)return BASE+'/dist/textures/assets/'+value.texture.replace(/^\//,'');
  return 'https://assets.mcasset.cloud/'+MC_VERSION+'/assets/minecraft/textures/item/'+encodeURIComponent(id.split(':')[1].split('/').pop())+'.png';
}
export function blockIconUrl(id){
  if(!isVanilla(id))return '';
  return 'https://assets.mcasset.cloud/'+MC_VERSION+'/assets/minecraft/textures/block/'+encodeURIComponent(id.split(':')[1].split('/').pop())+'.png';
}
export async function loadVanillaIcons(){
  if(loaded)return vanillaCatalog();
  try{
    const response=await fetch(BASE+'/manifest/26.3.json',{mode:'cors'});
    if(!response.ok)throw Error('HTTP '+response.status);
    const manifest=await response.json();
    if(!Array.isArray(manifest.items))throw Error('Invalid icon manifest');
    for(const item of manifest.items){
      const raw=String(item.id||'');
      const id=raw.includes(':')?raw:'minecraft:'+raw;
      if(!isVanilla(id))continue;
      catalog.set(id,{id,label:item.readable||raw.replaceAll('_',' '),texture:String(item.texture||'')});
    }
    loaded=true;
  }catch(e){console.warn('Minecraft icons CDN unreachable; using local common item list',e);}
  return vanillaCatalog();
}
