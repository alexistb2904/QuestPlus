const pools=['questplus:choice','questplus:random','questplus:loot','questplus:all'];
const id=/^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const integer=(n,min,max)=>Number.isInteger(n)&&n>=min&&n<=max;
export const rewardTranslations=(v,max)=>v&&typeof v==='object'&&!Array.isArray(v)&&typeof v.en_us==='string'&&Object.keys(v).length<=128&&Object.entries(v).every(([k,s])=>/^[a-z]{2,3}_[a-z]{2,4}$/.test(k)&&typeof s==='string'&&s.trim()&&s.length<=max);
export const POOL_TYPES=pools;
export function resolveNamedTable(reward,tables,chain=[]){
 if(reward?.type!=='questplus:table')return structuredClone(reward);
 if(!reward.data||Object.keys(reward.data).join()!=='name'||typeof reward.data.name!=='string')throw Error('Invalid table reference');
 const name=reward.data.name;
 if(chain.includes(name)||chain.length>=16)throw Error('Reward table reference cycle');
 const table=tables?.find(t=>t.name===name);if(!table)throw Error('Unknown reward table: '+name);
 const resolved=resolveNamedTable(table.reward,tables,[...chain,name]);
 if(!pools.includes(resolved.type)||poolError(resolved))throw Error('Invalid named reward table');
 if(reward.text)resolved.text=structuredClone(reward.text);
 return resolved;
}
export function poolError(reward){
 const {type,data}=reward||{},choice=type==='questplus:choice',all=type==='questplus:all',loot=type==='questplus:loot';
 if(!pools.includes(type)||!data||typeof data!=='object'||Array.isArray(data))return 'Invalid reward table';
 const allowed=['options','description',...(!choice&&!all?['rolls']:[]),...(loot?['emptyWeight']:[])];
 if(Object.keys(data).some(k=>!allowed.includes(k))||!Array.isArray(data.options)||data.options.length<(choice?2:1)||data.options.length>32
   ||(!choice&&!all&&!integer(data.rolls??1,1,16))||(loot&&!integer(data.emptyWeight,0,1000000))
   ||(data.description!==undefined&&!rewardTranslations(data.description,1024)))return 'Invalid reward table';
 for(const option of data.options){
  if(!option||typeof option!=='object'||Array.isArray(option))return 'Invalid reward option';
  const plain={...option};
  if(!choice){if(!integer(plain.weight,1,1000000))return 'Poids de récompense invalide';delete plain.weight;}
  if(plain.text!==undefined){if(!rewardTranslations(plain.text,256))return 'Invalid reward translation';delete plain.text;}
  const keys=Object.keys(plain).sort().join(',');
  if(plain.type==='item'){
   if(keys!=='count,item,type'||!id.test(plain.item||'')||plain.item==='minecraft:air'||!integer(plain.count,1,2304))return 'Invalid item option';
  }else if(['experience','experience_levels'].includes(plain.type)){
   if(keys!=='amount,type'||!integer(plain.amount,1,1000000))return 'Invalid XP option';
  }else if(plain.type==='questplus:advancement'){
   if(keys!=='data,type'||Object.keys(plain.data||{}).join()!=='advancement'||!id.test(plain.data.advancement||''))return 'Invalid advancement option';
  }else if(plain.type==='questplus:message'){
   if(keys!=='data,type'||Object.keys(plain.data||{}).join()!=='message'||typeof plain.data.message!=='string'||!plain.data.message.trim()||plain.data.message.length>256)return 'Invalid message option';
  }else if(plain.type==='questplus:stage'){
   if(keys!=='data,type'||!plain.data||Object.keys(plain.data).sort().join(',')!=='remove,stage'||!id.test(plain.data.stage||'')||typeof plain.data.remove!=='boolean')return 'Invalid stage reward';
  }else if(plain.type==='questplus:toast'){
   if(keys!=='data,type'||!plain.data||Object.keys(plain.data).sort().join(',')!=='description,icon,title'||!rewardTranslations(plain.data.title,256)||!rewardTranslations(plain.data.description,256)||!id.test(plain.data.icon||''))return 'Invalid toast reward';
  }else return 'Unsupported reward option';
 }
 return '';
}
/** Per draw, with replacement. Choice has no random probability. */
export function poolProbabilities(reward){
 if(poolError(reward))return [];
 const {type,data}=reward;
 if(type==='questplus:choice')return data.options.map(()=>null);
 if(type==='questplus:all')return data.options.map(()=>1);
 const weights=data.options.map(o=>o.weight);
 if(type==='questplus:loot')weights.push(data.emptyWeight);
 const total=weights.reduce((a,b)=>a+b,0);
 return weights.map(w=>w/total);
}
export function simulatePool(reward,random=Math.random){
 const error=poolError(reward);
 if(error||reward.type==='questplus:choice')throw Error(error||'Choices are selected by the player');
 if(reward.type==='questplus:all')return structuredClone(reward.data.options);
 const probabilities=poolProbabilities(reward),results=[];
 for(let roll=0;roll<(reward.data.rolls??1);roll++){
  const ticket=random();if(!Number.isFinite(ticket)||ticket<0||ticket>=1)throw Error('Invalid simulation random source');
  let cumulative=0,index=probabilities.length-1;
  for(let i=0;i<probabilities.length;i++){cumulative+=probabilities[i];if(ticket<cumulative){index=i;break;}}
  results.push(structuredClone(reward.data.options[index]??null));
 }
 return results;
}
