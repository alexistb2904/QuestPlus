import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {inspectFtbPack} from '../web/editor/src/core/ftb-import.js';
import {validate,fileMap,importDocuments} from '../web/editor/src/core/project.js';

const root=path.resolve(process.argv[2]||'.tools/ftb-real-pack');
async function read(directory){
 const docs=[];
 for(const entry of await fs.readdir(directory,{withFileTypes:true})){
  const file=path.join(directory,entry.name);
  if(entry.isDirectory())docs.push(...await read(file));
  else if(/\.(?:json5?|snbt|txt)$/i.test(entry.name)&&entry.name!=='provenance.json')docs.push({name:path.relative(root,file).replaceAll('\\','/'),content:await fs.readFile(file,'utf8')});
 }
 return docs;
}
const docs=await read(root),report=inspectFtbPack(docs);
assert.ok(report.questCount>0,'No convertible quests');
assert.deepEqual(validate(report.project),[],'Imported project must validate');
const exported=fileMap(report.project),roundtrip=importDocuments([...exported].map(([name,content])=>({name,content})));
assert.deepEqual(validate(roundtrip),[],'Portable project must still validate');
assert.deepEqual(roundtrip.ftbMappings,report.project.ftbMappings,'Source mappings survive export');
console.log(JSON.stringify({chapters:report.chapterCount,sourceQuests:report.sourceQuestCount,importedQuests:report.questCount,excludedQuests:report.sourceQuestCount-report.questCount,warnings:report.warnings.length,details:report.warnings},null,2));
