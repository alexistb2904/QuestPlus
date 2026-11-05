import JSON5 from 'json5';
import {emptyProject,slug} from './project.js';

/** Conservative FTB Quests 26.x JSON5 importer. Unknown game mechanics are reported, never guessed. */
export function inspectFtbPack(documents) {
  const project=emptyProject(),warnings=[],sources=[];
  const seen=new Set(),ids=new Map();
  for(const {name,content} of documents){
    const path=String(name).replace(/\\/g,'/');
    if(!/(?:^|\/)chapters\/.*\.json5?$/i.test(path))continue;
    let root;
    try{root=JSON5.parse(String(content).replace(/^\uFEFF/,''));}
    catch(e){warnings.push(name+': invalid JSON5: '+e.message);continue}
    if(!root||typeof root!=='object'||Array.isArray(root)||!Array.isArray(root.quests))continue;
    sources.push({name,root});
  }
  if(!sources.length)throw Error('No FTB chapters detected. Expected chapters/*.json5 containing quests[].');
  const safeName=(prefix,raw)=>{const base=prefix+'/'+slug(String(raw));let next=base,i=2;while(seen.has(next))next=base+'_'+i++;seen.add(next);return next};
  for(const {name,root} of sources){
    const fileName=name.replace(/\\/g,'/').split('/').pop().replace(/\.json5?$/i,'');
    const categoryId=safeName('questplus:ftb',root.id||fileName);
    project.categories.push({id:categoryId,icon:'minecraft:book',title:{en_us:String(root.title||fileName)},description:{en_us:'Imported from FTB Quests'},annotations:[]});
    for(let ix=0;ix<root.quests.length;ix++){
      const raw=root.quests[ix];if(!raw||typeof raw!=='object')continue;
      const id=safeName(categoryId,String(raw.id||ix+1));
      ids.set(String(raw.id),id);
      const objectives=[];
      for(const [n,t] of (Array.isArray(raw.tasks)?raw.tasks:[]).entries()){
        if(!t || typeof t!=='object'){warnings.push(id+': invalid task');continue;}
        const type=String(t.type||'').split(':').pop(), count=Math.max(1,Number(t.count||t.value||1));
        const target=String(t.item||t.entity||t.dimension||t.biome||'');
        const supported={
          kill:['entity_kill',String(t.entity||'')],
          biome:['biome_visit',String(t.biome||'')],
          dimension:['dimension_enter',String(t.dimension||'')],
        }[type];
        if(!supported || !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(supported[1]) || !Number.isSafeInteger(count) || count>1_000_000_000){
          warnings.push(id+': task '+(t.type||'(missing type)')+' requires manual conversion');continue;
        }
        objectives.push({id:'step'+(n+1),event:supported[0],target:supported[1],count});
      }
      if(!objectives.length){warnings.push(id+': no lossless supported objectives; quest skipped');continue;}
      if(Array.isArray(raw.rewards)&&raw.rewards.length)warnings.push(id+': '+raw.rewards.length+' reward(s) require manual conversion');
      if(raw.dependencies?.length)warnings.push(id+': dependency mapping pending verification');
      project.quests.push({schemaVersion:2,id,category:categoryId,icon:'minecraft:book',
        title:{en_us:String(raw.title||'Imported quest '+(ix+1))},
        objectives,rewards:[],progression:'once',
        layout:{x:Number.isFinite(raw.x)?raw.x:ix*60,y:Number.isFinite(raw.y)?raw.y:0}});
    }
  }
  if(!project.quests.length)warnings.push('No supported quests converted. Original source files were not changed.');
  return {project,warnings,chapterCount:sources.length,questCount:project.quests.length,sourceIdMap:ids};
}
