// Small dependency-free ZIP implementation for portable QuestPlus packs.
// ZIP STORE writer; reader accepts STORE and DEFLATE (native DecompressionStream).
// No servers/uploads: all project files stay in the browser.
const utf8=new TextEncoder(),decoder=new TextDecoder('utf-8',{fatal:true});
const table=Array.from({length:256},(_,n)=>{for(let k=0;k<8;k++)n=(n&1)?0xedb88320^(n>>>1):n>>>1;return n>>>0;});
function crc32(bytes){let c=0xffffffff;for(const b of bytes)c=table[(c^b)&255]^(c>>>8);return(c^0xffffffff)>>>0;}
function u16(a,p,n){a[p]=n&255;a[p+1]=n>>>8&255;}
function u32(a,p,n){u16(a,p,n);u16(a,p+2,Math.floor(n/65536));}
function rd16(v,p){return v.getUint16(p,true);}
function rd32(v,p){return v.getUint32(p,true);}
export function makeZip(entries){
  const chunks=[],directory=[];let offset=0;
  for(const [path,content] of entries){
    const name=utf8.encode(path),data=utf8.encode(content);
    if(name.length>65535||data.length>20_000_000)throw Error('Fichier ZIP trop volumineux');
    const crc=crc32(data),local=new Uint8Array(30+name.length);
    u32(local,0,0x04034b50);u16(local,4,20);u16(local,6,0x800);u16(local,8,0);
    u32(local,14,crc);u32(local,18,data.length);u32(local,22,data.length);u16(local,26,name.length);
    local.set(name,30);
    chunks.push(local,data);
    const central=new Uint8Array(46+name.length);
    u32(central,0,0x02014b50);u16(central,4,20);u16(central,6,20);u16(central,8,0x800);
    u32(central,16,crc);u32(central,20,data.length);u32(central,24,data.length);
    u16(central,28,name.length);u32(central,42,offset);central.set(name,46);
    directory.push(central);offset+=local.length+data.length;
  }
  if(directory.length>65535)throw Error('Nombre de fichiers ZIP excessif');
  const cdOffset=offset,cdLength=directory.reduce((n,b)=>n+b.length,0),end=new Uint8Array(22);
  u32(end,0,0x06054b50);u16(end,8,directory.length);u16(end,10,directory.length);
  u32(end,12,cdLength);u32(end,16,cdOffset);
  return new Blob([...chunks,...directory,end],{type:'application/zip'});
}
export async function readZip(file){
  const buffer=await file.arrayBuffer();
  if(buffer.byteLength>60_000_000)throw Error('Archive trop volumineuse (60 Mo maximum)');
  const bytes=new Uint8Array(buffer),view=new DataView(buffer);
  let eocd=-1;
  for(let p=bytes.length-22;p>=Math.max(0,bytes.length-65557);p--)
    if(rd32(view,p)===0x06054b50){eocd=p;break;}
  if(eocd<0)throw Error('Archive ZIP invalide (annuaire absent)');
  const count=rd16(view,eocd+10),cdSize=rd32(view,eocd+12),cdStart=rd32(view,eocd+16);
  if(count>2000||cdStart+cdSize>bytes.length)throw Error('Archive ZIP invalide ou trop volumineuse');
  const files=[];let pos=cdStart,total=0;
  for(let i=0;i<count;i++){
    if(pos+46>bytes.length||rd32(view,pos)!==0x02014b50)throw Error('En-tête ZIP invalide');
    const flag=rd16(view,pos+8),method=rd16(view,pos+10),crc=rd32(view,pos+16),size=rd32(view,pos+20),
      expanded=rd32(view,pos+24),n=rd16(view,pos+28),extra=rd16(view,pos+30),comment=rd16(view,pos+32),local=rd32(view,pos+42);
    if(pos+46+n+extra+comment>bytes.length)throw Error('Fichier ZIP tronqué');
    const name=decoder.decode(bytes.subarray(pos+46,pos+46+n));
    pos+=46+n+extra+comment;
    if(name.endsWith('/')||!name.toLowerCase().endsWith('.json'))continue;
    if(name.includes('..')||name.startsWith('/')||name.includes('\\'))throw Error('Chemin ZIP non autorisé : '+name);
    if((flag&1)!==0)throw Error('ZIP chiffré non pris en charge');
    if(![0,8].includes(method))throw Error('Compression ZIP non prise en charge : '+method);
    total+=expanded;
    if(expanded>2_000_000||total>25_000_000)throw Error('Archive décompressée trop volumineuse');
    if(local+30>bytes.length||rd32(view,local)!==0x04034b50)throw Error('Entrée ZIP introuvable');
    const dataOffset=local+30+rd16(view,local+26)+rd16(view,local+28);
    if(dataOffset+size>bytes.length)throw Error('Données ZIP tronquées');
    let raw=bytes.slice(dataOffset,dataOffset+size);
    if(method===8){
      if(typeof DecompressionStream==='undefined')throw Error('Navigateur sans décompression ZIP native');
      const stream=new Blob([raw]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
      raw=new Uint8Array(await new Response(stream).arrayBuffer());
    }
    if(raw.length!==expanded||crc32(raw)!==crc)throw Error('Archive corrompue : '+name);
    files.push({name,content:decoder.decode(raw)});
  }
  return files;
}
