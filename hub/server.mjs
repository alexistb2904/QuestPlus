import http from 'node:http';
import {randomBytes,timingSafeEqual,createHash} from 'node:crypto';
import {mkdir,readFile,writeFile,readdir,unlink} from 'node:fs/promises';
import path from 'node:path';

const PORT=Number(process.env.PORT||8787);
const PUBLIC_URL=(process.env.HUB_PUBLIC_URL||'http://localhost:'+PORT).replace(/\/$/,'');
const ALLOWED_ORIGIN=process.env.STUDIO_ORIGIN||'http://localhost:5173';
const SERVER_KEY=process.env.HUB_SERVER_KEY||'';
const DATA_DIR=path.resolve(process.env.HUB_DATA_DIR||'./data/packs');
const MAX_BYTES=12*1024*1024, SESSION_MS=30*60*1000, PACK_MS=7*24*60*60*1000;
if(!SERVER_KEY||SERVER_KEY.length<32)throw Error('Set HUB_SERVER_KEY to a unique random secret (32+ characters)');
if(!PUBLIC_URL.startsWith('https://')&&!/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(PUBLIC_URL))
 throw Error('HUB_PUBLIC_URL must use HTTPS except for local development');
const sessions=new Map(),rates=new Map(),packs=new Map();
const token=(bytes=24)=>randomBytes(bytes).toString('base64url');
const digest=value=>createHash('sha256').update(JSON.stringify(value)).digest('hex');
const equal=(a,b)=>{const x=Buffer.from(String(a||'')),y=Buffer.from(String(b||''));return x.length===y.length&&timingSafeEqual(x,y)};
const fail=(code,message)=>Object.assign(new Error(message),{status:code});
const json=(res,status,value)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(value));};
const allowedFile=p=>/^(?:config\.json|queststudio-compatibility\.json|(?:quests|categories)\/[a-z0-9_./-]+\.json)$/.test(p)&&!p.split('/').includes('..')&&!p.startsWith('/');
function documents(value){
 if(!Array.isArray(value)||value.length>2000)throw fail(400,'Invalid documents');
 const seen=new Set();let total=0,quest=0,category=0;
 for(const f of value){
  if(!f||typeof f.name!=='string'||typeof f.content!=='string'||!allowedFile(f.name)||seen.has(f.name))throw fail(400,'Unsafe or duplicate document');
  seen.add(f.name);total+=Buffer.byteLength(f.content);if(total>MAX_BYTES)throw fail(413,'Pack too large');
  if(f.name.startsWith('quests/'))quest++;
  if(f.name.startsWith('categories/'))category++;
  try {JSON.parse(f.content)}catch{throw fail(400,'Invalid JSON document: '+f.name)}
 }
 if(!seen.has('config.json')||!quest||!category)throw fail(400,'Missing quests, categories or config');
 return value;
}
const active=s=>s&&Date.now()<s.expires;
const requireServer=req=>{if(!equal(req.headers.authorization,'Bearer '+SERVER_KEY))throw fail(401,'Invalid server credentials')};
const bearer=req=>String(req.headers.authorization||'').replace(/^Bearer /,'');
function editor(s,req){
 if(!active(s))throw fail(404,'Session expired');
 if(!s.auth||!equal(s.auth,bearer(req)))throw fail(403,'Editor not authorized');
 s.expires=Date.now()+SESSION_MS;
}
const clean=async()=>{const now=Date.now();
 for(const [key,s] of sessions)if(s.expires<=now)sessions.delete(key);
 for(const [key,rate] of rates)if(rate.until<=now)rates.delete(key);
 for(const [id,pack] of packs)if(pack.expires<=now)packs.delete(id);
 for(const name of await readdir(DATA_DIR).catch(()=>[]))if(/^[A-Za-z0-9_-]{32}\.json$/.test(name)){
  const file=path.join(DATA_DIR,name);
  try{const raw=JSON.parse(await readFile(file,'utf8'));if(raw.expires<=now)await unlink(file)}catch{}
 }
};
const rateLimit=(req,action,max,windowMs)=>{const ip=String(req.socket.remoteAddress||'local');const key=action+':'+ip;const now=Date.now();
 let entry=rates.get(key);if(!entry||entry.until<=now){entry={count:0,until:now+windowMs};rates.set(key,entry)}
 if(++entry.count>max)throw fail(429,'Rate limit exceeded');
};
async function body(req){const chunks=[];let size=0;for await(const chunk of req){size+=chunk.length;if(size>MAX_BYTES+1024*1024)throw fail(413,'Body too large');chunks.push(chunk)}
 try{return JSON.parse(Buffer.concat(chunks).toString('utf8'))}catch{throw fail(400,'Invalid JSON body')}}
const sessionInfo=s=>({expires:s.expires,revision:s.revision,serverName:s.serverName,approvalRequired:true});
async function readPack(id){if(!/^[A-Za-z0-9_-]{32}$/.test(id))throw fail(404,'Unknown pack');
 let stored=packs.get(id);if(!stored)try{stored=JSON.parse(await readFile(path.join(DATA_DIR,id+'.json'),'utf8'));packs.set(id,stored)}catch{throw fail(404,'Unknown pack')}
 if(stored.expires<=Date.now())throw fail(410,'Pack expired');
 return stored;
}
async function handler(req,res){
 try{
  const origin=req.headers.origin;
  if(origin){if(origin!==ALLOWED_ORIGIN)throw fail(403,'Origin not permitted');res.setHeader('Access-Control-Allow-Origin',ALLOWED_ORIGIN);res.setHeader('Vary','Origin')}
  res.setHeader('Access-Control-Allow-Headers','Content-Type, Authorization');
  res.setHeader('Access-Control-Allow-Methods','GET,POST,OPTIONS');
  if(req.method==='OPTIONS'){res.writeHead(204);return res.end()}
  const url=new URL(req.url,'http://localhost'),p=url.pathname;
  if(req.method==='GET'&&p==='/health')return json(res,200,{status:'ok',service:'QuestPlus Hub',version:1});
  // Server -> hub: the API key is NEVER sent to a browser.
  if(p.startsWith('/v1/server/')){
   requireServer(req);
   if(req.method==='POST'&&p==='/v1/server/sessions'){
    const input=await body(req),id=token(),files=documents(input.files);
    if(typeof input.serverName!=='string'||input.serverName.length>100)throw fail(400,'Invalid server name');
    const s={id,serverName:input.serverName,files,registry:input.registry||{},revision:digest(files),
     expires:Date.now()+SESSION_MS,pending:null,auth:null,job:null,jobStatus:null};
    sessions.set(id,s);
    return json(res,201,{id,url:ALLOWED_ORIGIN+'/?session='+encodeURIComponent(id),expires:s.expires});
   }
   if(req.method==='GET'&&p==='/v1/server/poll'){
    const s=sessions.get(url.searchParams.get('session'));if(!active(s))throw fail(404,'Session expired');
    return json(res,200,{pending:s.pending&&!s.pending.approved?{id:s.pending.id,code:s.pending.code}:null,
      job:s.job&&!s.job.taken?{id:s.job.id,files:s.job.files,baseRevision:s.job.baseRevision}:null});
   }
   if(req.method==='POST'&&p==='/v1/server/approve'){
    const {session,id,code}=await body(req),s=sessions.get(session);
    if(!active(s)||!s.pending||s.pending.id!==id||s.pending.code!==code||s.pending.approved)throw fail(409,'No pending approval');
    s.pending.approved=true;s.auth=token(32);return json(res,200,{approved:true});
   }
   if(req.method==='POST'&&p==='/v1/server/ack'){
    const {session,id,success,error}=await body(req),s=sessions.get(session);
    if(!active(s)||!s.job||s.job.id!==id)throw fail(409,'No matching publication');
    if(s.jobStatus?.status!=='pending')throw fail(409,'Already acknowledged');
    s.jobStatus=success?{status:'applied',revision:digest(s.job.files)}:{status:'failed',error:String(error||'Rejected').slice(0,500)};
    if(success){s.files=s.job.files;s.revision=s.jobStatus.revision}
    s.job.taken=true;return json(res,200,s.jobStatus);
   }
   if(req.method==='GET'&&p.startsWith('/v1/server/packs/')){const pack=await readPack(p.split('/').pop());return json(res,200,{files:pack.files,sha256:pack.sha256})}
   throw fail(404,'Unknown server endpoint');
  }
  // Browser: a session link alone NEVER confers edit rights.
  if(req.method==='GET'&&/^\/v1\/editor\/sessions\/[A-Za-z0-9_-]+$/.test(p)){
   const s=sessions.get(p.split('/')[4]);if(!active(s))throw fail(404,'Session expired');return json(res,200,sessionInfo(s));
  }
  if(req.method==='POST'&&/^\/v1\/editor\/sessions\/[A-Za-z0-9_-]+\/request$/.test(p)){
   rateLimit(req,'approval',6,60_000);const s=sessions.get(p.split('/')[4]);if(!active(s))throw fail(404,'Session expired');
   if(s.pending&&!s.pending.approved)throw fail(409,'Approval already pending');
   const pending={id:token(),secret:token(32),code:String(randomBytes(4).readUInt32BE(0)%900000+100000),approved:false};
   s.pending=pending;return json(res,201,{requestId:pending.id,requestSecret:pending.secret,code:pending.code});
  }
  if(req.method==='POST'&&/^\/v1\/editor\/sessions\/[A-Za-z0-9_-]+\/status$/.test(p)){
   const s=sessions.get(p.split('/')[4]);if(!active(s))throw fail(404,'Session expired');
   const {requestId,requestSecret}=await body(req);
   if(!s.pending||!equal(s.pending.id,requestId)||!equal(s.pending.secret,requestSecret))throw fail(403,'Invalid approval request');
   return json(res,200,s.pending.approved?{approved:true,editorToken:s.auth,revision:s.revision}:{approved:false});
  }
  if(req.method==='GET'&&/^\/v1\/editor\/sessions\/[A-Za-z0-9_-]+\/data$/.test(p)){
   const s=sessions.get(p.split('/')[4]);editor(s,req);
   return json(res,200,{files:s.files,registry:s.registry,revision:s.revision,serverName:s.serverName});
  }
  if(req.method==='POST'&&/^\/v1\/editor\/sessions\/[A-Za-z0-9_-]+\/publish$/.test(p)){
   const s=sessions.get(p.split('/')[4]);editor(s,req);
   const input=await body(req),files=documents(input.files);
   if(input.baseRevision!==s.revision)throw fail(409,'Server revision changed; reload before publishing');
   if(s.jobStatus?.status==='pending')throw fail(409,'Another publication is in progress');
   const id=token();s.job={id,files,baseRevision:s.revision,taken:false};
   s.jobStatus={status:'pending'};return json(res,202,{id,status:'pending'});
  }
  if(req.method==='GET'&&/^\/v1\/editor\/sessions\/[A-Za-z0-9_-]+\/jobs\/[A-Za-z0-9_-]+$/.test(p)){
   const split=p.split('/'),s=sessions.get(split[4]);editor(s,req);
   if(!s.job||s.job.id!==split[6])throw fail(404,'Unknown publication');
   return json(res,200,s.jobStatus);
  }
  // Anyone can publish an unlisted immutable pack; the ID is a 192-bit read capability.
  // This does not permit editing any server. Limit uploads to protect public deployments.
  if(req.method==='POST'&&p==='/v1/packs'){
   rateLimit(req,'packs',8,60*60_000);const input=await body(req),files=documents(input.files);
   const id=token(),record={files,sha256:digest(files),expires:Date.now()+PACK_MS};
   await mkdir(DATA_DIR,{recursive:true});await writeFile(path.join(DATA_DIR,id+'.json'),JSON.stringify(record),{flag:'wx',mode:0o600});
   packs.set(id,record);return json(res,201,{id,url:ALLOWED_ORIGIN+'/?pack='+id,expires:record.expires,sha256:record.sha256});
  }
  if(req.method==='GET'&&/^\/v1\/packs\/[A-Za-z0-9_-]+$/.test(p)){
   rateLimit(req,'readpacks',90,60_000);const pack=await readPack(p.split('/').pop());
   return json(res,200,{files:pack.files,sha256:pack.sha256,expires:pack.expires});
  }
  throw fail(404,'Unknown endpoint');
 }catch(error){json(res,error.status||500,{error:error.status?error.message:'Internal server error'});if(!error.status)console.error(error)}
}
await mkdir(DATA_DIR,{recursive:true});
setInterval(()=>{clean().catch(console.error)},10*60_000).unref();
http.createServer(handler).listen(PORT,'0.0.0.0',()=>console.log('QuestPlus Hub listening on port '+PORT));
