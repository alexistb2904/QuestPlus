import {VERSION,ITEM_IDS,BLOCK_IDS} from './vanilla-registry-26.3.js';
export const MC_VERSION=VERSION;
export const COMMON_ITEMS=ITEM_IDS;
const itemSet=new Set(ITEM_IDS),blockSet=new Set(BLOCK_IDS);
const names=[...new Set([...ITEM_IDS,...BLOCK_IDS])].sort((a,b)=>a.localeCompare(b));
const catalog=names.map(name=>({
 id:'minecraft:'+name,
 label:name.replace(/[_/]/g,' '),
 kind:itemSet.has(name)&&blockSet.has(name)?'both':blockSet.has(name)?'block':'item',
 version:VERSION
}));
export const isVanilla=id=>/^minecraft:[a-z0-9_./-]+$/.test(String(id));
export const vanillaCatalog=()=>catalog;
export async function loadVanillaIcons(){return catalog;}
export const itemExists=id=>isVanilla(id)&&itemSet.has(id.slice('minecraft:'.length));
export const blockExists=id=>isVanilla(id)&&blockSet.has(id.slice('minecraft:'.length));
export const inVanillaRegistry=id=>itemExists(id)||blockExists(id);
export const knownVersion=id=>inVanillaRegistry(id)?VERSION:null;
const textureName=id=>encodeURIComponent(String(id).split(':').slice(1).join(':').split('/').pop());
export function iconUrl(id,version=VERSION){
 if(!isVanilla(id))return '';
 return 'https://assets.mcasset.cloud/'+encodeURIComponent(version)+'/assets/minecraft/textures/item/'+textureName(id)+'.png';
}
export function blockIconUrl(id,version=VERSION){
 if(!isVanilla(id))return '';
 return 'https://assets.mcasset.cloud/'+encodeURIComponent(version)+'/assets/minecraft/textures/block/'+textureName(id)+'.png';
}
export function imageCandidates(id,version=VERSION){
 if(!isVanilla(id))return [];
 const block=blockIconUrl(id,version),item=iconUrl(id,version);
 return blockExists(id)&&!itemExists(id)?[block,item]:[item,block];
}
export const versionCompare=(a,b)=>{
 const parse=s=>String(s||VERSION).split('.').map(n=>Number.parseInt(n,10)||0);
 const A=parse(a),B=parse(b);
 for(let i=0;i<Math.max(A.length,B.length);i++){if((A[i]||0)!==(B[i]||0))return (A[i]||0)-(B[i]||0);}
 return 0;
};
export const requiredMinecraftVersion=(project,id)=>project?.iconVersions?.[id]||knownVersion(id)||null;
export const versionWarning=(project,id)=>{
 const required=requiredMinecraftVersion(project,id),target=project?.targetMinecraftVersion||VERSION;
 return required&&versionCompare(required,target)>0?`⚠ ${id} requires Minecraft ${required} (pack: ${target})`:null;
};
