/** Small, lossless editor for QuestRegistry's condition JSON grammar. */
const escape=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const OBJECTIVE_KINDS=[
 ['dimension','Dimension'],['tool','Outil'],['biome','Biome'],['effect','Effet'],
 ['questplus:stage','Stage'],['healthAtMost','Vie ≤'],['healthAtLeast','Vie ≥'],['sneaking','Accroupi']
];
export const rootPath='';
export function childrenOf(node){
 if(!node)return null;
 if(Array.isArray(node.and))return {type:'and',children:node.and};
 if(Array.isArray(node.or))return {type:'or',children:node.or};
 if(node.at_least&&Array.isArray(node.at_least.of))return {type:'at_least',children:node.at_least.of};
 if(node.not)return {type:'not',children:[node.not]};
 return null;
}
export function atPath(root,path=''){
 if(!path)return root;
 let result=root;
 for(const part of path.split('.')){
   const branch=childrenOf(result);
   if(!branch)throw Error('Condition path is not a group');
   const index=Number(part);
   if(!Number.isInteger(index)||index<0||index>=branch.children.length)throw Error('Invalid condition path');
   result=branch.children[index];
 }
 return result;
}
export function setAt(root,path,replacement){
 if(!path)return replacement;
 const parts=path.split('.').map(Number),clone=structuredClone(root);
 let node=clone;
 for(let i=0;i<parts.length-1;i++){
   const branch=childrenOf(node);if(!branch)throw Error('Invalid condition parent');
   node=branch.children[parts[i]];
 }
 const branch=childrenOf(node);if(!branch)throw Error('Invalid condition target');
 const array=branch.type==='not'?null:branch.type==='at_least'?node.at_least.of:node[branch.type];
 if(branch.type==='not'){
   if(parts.at(-1)!==0)throw Error('Invalid NOT child');
   if(replacement===null)return null;
   node.not=replacement;
 } else{
   if(replacement===null)array.splice(parts.at(-1),1);
   else array[parts.at(-1)]=replacement;
 }
 return tidy(clone);
}
export function tidy(node){
 if(node==null)return null;
 const branch=childrenOf(node);
 if(!branch)return node;
 if(branch.type==='not'){const child=tidy(node.not);return child?{not:child}:null;}
 const arr=branch.children.map(tidy).filter(Boolean);
 if(!arr.length)return null;
 return branch.type==='at_least'?{at_least:{count:Math.min(node.at_least.count,arr.length),of:arr}}:{[branch.type]:arr};
}
export function addChild(root,path,child){
 if(!root)return child;
 const node=atPath(root,path),branch=childrenOf(node);
 if(!branch){return setAt(root,path,{and:[node,child]});}
 if(branch.type==='not')return setAt(root,path,{and:[node,child]});
 const updated=structuredClone(node);
 (branch.type==='at_least'?updated.at_least.of:updated[branch.type]).push(child);
 return setAt(root,path,updated);
}
export function wrap(root,path,operator){
 if(!['and','or','not','at_least'].includes(operator))throw Error('Invalid operator');
 const node=atPath(root,path);
 if(!node)return null;
 const existing=childrenOf(node);
 if(operator==='not')return setAt(root,path,{not:node});
 if(existing?.type===operator)return root;
 if(operator==='at_least')return setAt(root,path,{at_least:{count:1,of:existing&&existing.type!=='not'?existing.children:[node]}});
 if(existing && existing.type!=='not')return setAt(root,path,{[operator]:existing.children});
 return setAt(root,path,{[operator]:[node]});
}
export const leaf=(scope,kind)=>scope==='requires'?{quest:''}:
 kind==='sneaking'?{sneaking:true}:
 kind==='healthAtMost'||kind==='healthAtLeast'?{[kind]:2}:
 {[kind]:'minecraft:overworld'};
export function renderConditionTree(node,scope,{quests=[],language='en_us'}={}){
 const roots=scope==='requires';
 const label=id=>quests.find(q=>q.id===id)?.title?.[language]||quests.find(q=>q.id===id)?.title?.en_us||id;
 const render=(current,path,depth)=>{
   const branch=childrenOf(current),tag=path||'root';
   const del=`<button class="cond-remove" type="button" data-cond-action="remove" data-cond-path="${escape(path)}" title="Supprimer" aria-label="Supprimer">×</button>`;
   if(branch){
     const typ=branch.type,ops=typ==='not'?'<span class="cond-op">NON</span>':
       `<select class="cond-op" data-cond-action="operator" data-cond-path="${escape(path)}" aria-label="Type de groupe">
       <option value="and" ${typ==='and'?'selected':''}>ET · tous</option>
       <option value="or" ${typ==='or'?'selected':''}>OU · au choix</option></select>`;
     const children=branch.children.map((child,i)=>render(child,path?path+'.'+i:String(i),depth+1)).join('');
     return `<div class="cond-group depth-${Math.min(depth,4)}" data-condition-path="${escape(path)}">
       <div class="cond-group-head">${ops}${del}</div>
       <div class="cond-children">${children}</div>
       ${typ==='not'?'':`<div class="cond-tools">${roots?'<button data-cond-action="search" data-cond-path="'+escape(path)+'">+ Quête</button>':
          '<button data-cond-action="add-leaf" data-cond-path="'+escape(path)+'">+ Condition</button>'}
          <button data-cond-action="add-and" data-cond-path="${escape(path)}">+ ET</button>
          <button data-cond-action="add-or" data-cond-path="${escape(path)}">+ OU</button>
          <button data-cond-action="add-not" data-cond-path="${escape(path)}">+ NON</button>
       </div>`}</div>`;
   }
   if(roots){
     const id=current.quest||'';
     return `<div class="cond-leaf" data-condition-path="${escape(path)}"><span class="cond-quest-label">${escape(label(id))}</span>${del}</div>`;
   }
   const kind=Object.keys(current)[0]||'dimension',value=current[kind];
   const choices=OBJECTIVE_KINDS.map(([id,name])=>`<option value="${id}" ${id===kind?'selected':''}>${name}</option>`).join('');
   const input=kind==='sneaking'? `<select data-cond-action="value" data-cond-path="${escape(path)}"><option value="true" ${value===true?'selected':''}>Oui</option><option value="false" ${value===false?'selected':''}>Non</option></select>`:
   `<input data-cond-action="value" data-cond-path="${escape(path)}" value="${escape(value)}" ${kind.startsWith('health')?'type="number" min="0" step="0.5"':'spellcheck="false"'}>`;
   return `<div class="cond-leaf" data-condition-path="${escape(path)}">
     <select data-cond-action="kind" data-cond-path="${escape(path)}">${choices}</select>${input}${del}</div>`;
 };
 const root=node?render(node,'',0):'<div class="cond-empty">Aucune condition</div>';
 const rootTools=`<div class="cond-tools root-tools">
   ${roots?'<button data-cond-action="search" data-cond-path="">+ Quête</button>':'<button data-cond-action="add-leaf" data-cond-path="">+ Condition</button>'}
   <button data-cond-action="add-and" data-cond-path="">+ ET</button>
   <button data-cond-action="add-or" data-cond-path="">+ OU</button>
   <button data-cond-action="add-not" data-cond-path="">+ NON</button>
 </div>`;
 return `<div class="condition-builder" data-condition-scope="${escape(scope)}">${root}${rootTools}
  ${roots?`<div class="cond-search-panel"><input type="search" class="cond-search-input" placeholder="Chercher une quête…" autocomplete="off"><div class="cond-search-results"></div></div>`:''}
 </div>`;
}
