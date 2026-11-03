"""Rebuild editable sources and honest software previews; Python + Pillow + NumPy."""
import base64, copy, hashlib, io, json, math, random, uuid
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from motion import tracks_for, clear_palm_contacts, TIMES, DURATION, HELD

ROOT = Path(__file__).resolve().parents[1]
for folder in ('blockbench','textures','models','animations/tablet','animations/player','previews'):
    (ROOT/folder).mkdir(parents=True, exist_ok=True)
def uid(name): return str(uuid.uuid5(uuid.NAMESPACE_URL, 'questplus-assets/'+name))
def save(path, data): (ROOT/path).write_text(json.dumps(data, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
atlas=Image.new('RGB',(256,256),(27,24,29)); pen=ImageDraw.Draw(atlas)
mannequin_atlas=Image.new('RGB',(256,256),(27,24,29))
slots=[]; sx=sy=1; row=0
preview_tiles={}
palette={'wood':(65,43,33),'endgrain':(79,51,36),'metal':(76,78,83),'edge':(158,155,137),'stone':(96,89,91),'gold':(133,102,65),'copper':(169,95,57),'crystal':(123,78,168),'facet':(180,133,212),'screen':(29,33,34),'strap':(47,32,28),'cloth':(58,78,79),'skin':(170,139,111),'head':(96,102,101)}
def tile(w,h,material):
    global sx,sy,row
    w=max(1,round(w*2));h=max(1,round(h*2))
    key=(w,h,material)
    if material in ('skin','cloth','head') and key in preview_tiles:return preview_tiles[key]
    if sx+w+1>256:sx=1;sy+=row+1;row=0
    assert sy+h+1<256, 'atlas overflow'
    x,y=sx,sy;sx+=w+1;row=max(row,h);slots.append([x,y,w,h,material])
    rng=random.Random(len(slots)*971);base=palette[material]
    for v in range(h):
        for u in range(w):
            noise=rng.choice([-5,-3,0,0,2,4])
            if material in ('wood','endgrain','strap'):
                noise+=(-10 if (u+int(2*math.sin(v*.24)))%9==0 else 0)+(3 if u%9==1 else 0)
            if material in ('metal','edge','gold','copper'):noise+=(12 if v==0 or u==0 else -12 if v==h-1 else 0)
            if material in ('crystal','facet'):noise+=int(16*(1-u/max(w,1)))+(13 if (u+v)%11==0 else 0)
            if material=='stone' and (u*3+v*7)%31==0:noise-=16
            if material=='screen':noise=rng.choice([0,0,0,1,-1])
            atlas.putpixel((x+u,y+v),tuple(max(0,min(255,c+noise)) for c in base))
    # Edge extrusion prevents neighbouring atlas islands bleeding into the faces.
    atlas.paste(atlas.crop((x,y,x+w,y+1)),(x,y-1));atlas.paste(atlas.crop((x,y+h-1,x+w,y+h)),(x,y+h))
    atlas.paste(atlas.crop((x,y,x+1,y+h)),(x-1,y));atlas.paste(atlas.crop((x+w-1,y,x+w,y+h)),(x+w,y))
    uv=[x,y,x+w,y+h]
    if material in ('skin','cloth','head'):preview_tiles[key]=uv
    return uv

class Model:
    def __init__(self,name):self.name=name;self.elements=[];self.groups=[];self.outliner=[];self.animations=[]
    def group(self,name,pivot=(0,0,0),parent=None):
        g={'name':name,'origin':list(pivot),'rotation':[0,0,0],'uuid':uid(self.name+'/'+name),'export':True,'isOpen':True}
        self.groups.append(g);node={'uuid':g['uuid'],'isOpen':True,'children':[]}
        (parent['children'] if parent else self.outliner).append(node);return node
    def cube(self,name,lo,hi,material,group,rotation=None,origin=None):
        dx,dy,dz=np.subtract(hi,lo);faces={}
        for face,w,h in [('north',dx,dy),('south',dx,dy),('east',dz,dy),('west',dz,dy),('up',dx,dz),('down',dx,dz)]:
            faces[face]={'uv':tile(w,h,material),'texture':1 if material in ('skin','cloth','head') else 0}
        e={'name':name,'type':'cube','uuid':uid(self.name+'/'+name),'from':list(lo),'to':list(hi),'origin':list(origin or [0,0,0]),'rotation':list(rotation or [0,0,0]),'faces':faces,'rescale':False,'box_uv':False,'export':True,'visibility':True,'color':0}
        self.elements.append(e);group['children'].append(e['uuid']);return e
    def document(self):
        stream=io.BytesIO();atlas.save(stream,format='PNG')
        textures=[{'path':'../textures/questplus_tablet.png','name':'questplus_tablet.png','id':'0','uuid':uid('texture'),'width':256,'height':256,'uv_width':256,'uv_height':256,'source':'data:image/png;base64,'+base64.b64encode(stream.getvalue()).decode()}]
        if self is not tablet:
            preview=io.BytesIO();mannequin_atlas.save(preview,format='PNG')
            textures.append({**textures[0],'path':'../textures/preview_mannequin.png','name':'preview_mannequin.png','id':'1','uuid':uid('preview_texture'),'source':'data:image/png;base64,'+base64.b64encode(preview.getvalue()).decode()})
        return {'meta':{'format_version':'5.0','model_format':'free','box_uv':False},'name':self.name,'model_identifier':self.name,'visible_box':[4,4,1],'resolution':{'width':256,'height':256},'elements':self.elements,'groups':self.groups,'outliner':self.outliner,'textures':textures,'animations':self.animations}

tablet=Model('questplus_tablet');root=tablet.group('tablet_root');body=tablet.group('tablet_body',parent=root);frame=tablet.group('frame',parent=root);corners=tablet.group('metal_corners',parent=root);stones=tablet.group('carved_stone',parent=root);crystals=tablet.group('crystals',parent=root);screen=tablet.group('screen',parent=root);back=tablet.group('back_details',parent=root)
tablet.group('left_hand_anchor',(-11.2,-2,1.1),root);tablet.group('right_hand_anchor',(11.2,-2,1.1),root)
tablet.cube('solid_walnut_core',[-11.95,-7.95,-.35],[11.95,7.95,.75],'wood',body)
tablet.cube('blank_display',[-10,-6,-.82],[10,6,-.36],'screen',screen)
for side,x in [('left',-12),('right',10.3)]:
    tablet.cube(side+'_carved_stile',[x,-7.8,-1],[x+1.7,7.8,.6],'wood',frame)
    tablet.cube(side+'_stepped_edge',[x+.4,-6.5,-1.15],[x+1.3,6.5,-1.01],'endgrain',frame)
    for j in range(3):tablet.cube(side+'_iron_binding_'+str(j),[x-.08,-4.8+j*3.1,-1.29],[x+.42,-3.5+j*3.1,.87],'metal',corners)
    for y in [-5.9,5.0]:tablet.cube(side+'_copper_clasp_'+str(y),[x-.1,y,-1.44],[x+.85,y+.8,.9],'copper',corners)
for side,y in [('lower',-8),('upper',6.3)]:
    tablet.cube(side+'_carved_rail',[-10.3,y,-1],[10.3,y+1.7,.6],'wood',frame)
    tablet.cube(side+'_raised_moulding',[-10,y+.35,-1.16],[10,y+.7,-1.01],'endgrain',frame)
    for x in [-6.6,5.7]:
        tablet.cube(side+'_iron_band_'+str(x),[x-.25,y-.05,-1.31],[x+1.05,y+1.75,.9],'metal',corners)
        tablet.cube(side+'_copper_band_'+str(x),[x+.2,y-.15,-1.44],[x+.85,y+1.85,1.03],'copper',corners)
for side,x in [('left',-10.3),('right',10)]:tablet.cube(side+'_inner_bead',[x,-6.3,-1.01],[x+.3,6.3,-.81],'gold',frame)
for y in [-6.3,6]:tablet.cube('inner_bead_'+str(y),[-10,y,-1.01],[10,y+.3,-.81],'gold',frame)
for sign in [-1,1]:
    for top in [-1,1]:
        x=sign*11.15;y=top*7.15;tag=f'corner_{sign}_{top}'
        tablet.cube(tag+'_vertical',[x-.95,y-1.15,-1.45],[x+.95,y+1.15,.98],'stone',stones)
        xx=sign*10.3
        tablet.cube(tag+'_horizontal',[xx-1.35,y-.96,-1.57],[xx+1.35,y+.96,1.1],'stone',stones)
        tablet.cube(tag+'_bevel',[x-.65,y-.7,-1.75],[x+.65,y+.7,-1.58],'metal',corners)
        tablet.cube(tag+'_rivet',[x-.2,y-.2,-1.92],[x+.2,y+.2,-1.76],'edge',corners)
tablet.cube('crown_iron_base',[-2,6.5,-1.42],[2,8.65,.83],'metal',corners)
tablet.cube('crown_stone_bevel',[-1.73,6.6,-1.6],[1.73,8.5,-1.43],'stone',stones)
tablet.cube('crown_dark_inset',[-1.18,6.85,-1.72],[1.18,8.25,-1.61],'screen',stones)
for s in [-1,1]:
    center=[s*.53,7.53,-1.8]
    tablet.cube('book_cover_'+str(s),[s*.53-.5,6.96,-1.88],[s*.53+.5,8.14,-1.73],'crystal',crystals,[0,0,s*10],center)
    tablet.cube('book_page_stack_'+str(s),[s*.53-.42,7.03,-1.97],[s*.53+.42,8.07,-1.89],'edge',crystals,[0,0,s*10],center)
    tablet.cube('book_open_page_'+str(s),[s*.53-.37,7.1,-2.03],[s*.53+.37,8.02,-1.98],'facet',crystals,[0,0,s*10],center)
    for k in range(4):
        width=.46 if k<3 else .29
        tablet.cube(f'book_engraving_{s}_{k}',[s*.53-width/2,7.25+k*.17,-2.07],[s*.53+width/2,7.30+k*.17,-2.04],'crystal',crystals,[0,0,s*10],center)
tablet.cube('book_spine',[-.075,6.92,-2.1],[.075,8.04,-1.74],'crystal',crystals)
tablet.cube('book_bookmark',[-.04,6.76,-2.12],[.13,7.13,-2.08],'gold',crystals)
tablet.cube('lower_lock_iron',[-1.8,-8.2,-1.3],[1.8,-6.7,.9],'metal',corners)
tablet.cube('lower_lock_copper',[-.85,-8.3,-1.5],[.85,-6.6,1.05],'copper',corners)
for x in [-7.1,6.5]:
    tablet.cube('rear_strap_'+str(x),[x,-6.5,.76],[x+.6,6.5,1.0],'strap',back)
    for y in [-5.8,5.3]:tablet.cube(f'rear_clasp_{x}_{y}',[x-.2,y,.99],[x+.8,y+.5,1.16],'metal',back)
tablet.cube('rear_center_panel',[-4.3,-4.2,.76],[4.3,4.2,.88],'endgrain',back)
for i,(w,h) in enumerate([(3.6,.45),(.45,3.6)]):tablet.cube('rear_compass_'+str(i),[-w/2,-h/2,.9],[w/2,h/2,1.08],'stone',back,[0,0,45],[0,0,1])
tablet.cube('rear_compass_gem',[-.55,-.55,1.09],[.55,.55,1.35],'crystal',back,[0,0,45],[0,0,1.1])

def keys(times,values,channel):return [{'channel':channel,'data_points':[dict(zip(('x','y','z'),v))],'uuid':uid(f'{channel}{t}{v}'),'time':t,'color':-1,'interpolation':'linear'} for t,v in zip(times,values)]
def animation(model,name,length,tracks,loop='once'):
    anim={'uuid':uid(name+model.name),'name':name,'loop':loop,'override':False,'length':length,'snapping':100,'animators':{}}
    for bone,channels in tracks.items():
        group=next(g for g in model.groups if g['name']==bone)
        anim['animators'][group['uuid']]={'name':bone,'type':'bone','keyframes':sum([keys(t,v,c) for c,(t,v) in channels.items()],[])}
        for key in anim['animators'][group['uuid']]['keyframes']:key['uuid']=uid(f"{model.name}/{name}/{bone}/{key['channel']}/{key['time']}")
    model.animations.append(anim)

def smooth(t):return t*t*(3-2*t)
def blend(a,b,s):return [round(x+(y-x)*s,6) for x,y in zip(a,b)]

def make_mannequin(name,slim=False,third=False):
    m=Model(name);r=m.group('player' if third else 'fp_player_arms');m.group('body',parent=r);head=m.group('head',(0,24,0),r)
    torso=next(n for n in r['children'] if n['uuid']==uid(name+'/body'))
    if third:
        m.cube('preview_torso',[-4,12,-2],[4,24,2],'cloth',torso);m.cube('preview_head',[-4,24,-4],[4,32,4],'head',head)
        for x in [-4,0]:m.cube('preview_leg_'+str(x),[x,0,-2],[x+4,12,2],'cloth',torso)
    width=3 if slim else 4
    for bone,sign in [('rightArm',1),('leftArm',-1)]:
        pivot=[sign*5,22,0];g=m.group(bone,pivot,r)
        xmin=-8 if sign<0 else 8-width
        m.cube(bone+'_sleeve',[xmin,15,-2],[xmin+width,24,2],'cloth',g)
        m.cube(bone+'_hand',[xmin,12,-2],[xmin+width,15,2],'skin',g)
    # Reference geometry is a separate subtree, removable without affecting the vanilla arm rig.
    doc=copy.deepcopy(tablet)
    # Reference subtree only; the actual player skin is supplied later by Minecraft.
    for e in doc.elements:
        for key in ['from','to','origin']:e[key]=[round(v*.65+o,6) for v,o in zip(e[key],HELD)]
        e['uuid']=uid(name+'/'+e['name']);m.elements.append(e)
    lookup={old['uuid']:new['uuid'] for old,new in zip(tablet.elements,doc.elements)}
    for g in doc.groups:
        old=g['uuid'];g['uuid']=uid(name+'/'+g['name']);lookup[old]=g['uuid'];g['origin']=[round(v*.65+o,6) for v,o in zip(g['origin'],HELD)];m.groups.append(g)
    def remap(node):return {'uuid':lookup[node['uuid']],'isOpen':True,'children':[lookup[c] if isinstance(c,str) else remap(c) for c in node['children']]}
    r['children'].append(remap(tablet.outliner[0]))
    prefix='questplus.third_person_' if third else 'questplus.'
    opened=clear_palm_contacts(tracks_for(third),tablet.elements)
    animation(m,prefix+'open',DURATION,opened)
    idle={}
    for bone,channels in opened.items():
        idle[bone]={}
        for channel,(_,values) in channels.items():
            base=values[-1];ts=[i/4 for i in range(17)];breath=[(1-math.cos(t*math.pi/2))/2 for t in ts];vs=[list(base) for _ in ts]
            # Entire assembly translates together, preserving wrist registration and screen angle.
            if channel=='position':vs=[blend(base,[base[0],base[1]+.045,base[2]],a) for a in breath]
            idle[bone][channel]=(ts,vs)
    for bone in ['rightArm','leftArm']:
        base=opened[bone].get('position',(None,[[0,0,0]]))[1][-1]
        idle[bone]['position']=(ts,[[base[0],round(base[1]+a*.045,6),base[2]] for a in breath])
    animation(m,prefix+'idle',4,idle,'loop')
    closed={b:{c:([round(DURATION-t,5) for t in reversed(times)],list(reversed(v))) for c,(times,v) in cs.items()} for b,cs in opened.items()}
    animation(m,prefix+'close',DURATION,closed)
    return m

models=[tablet,make_mannequin('questplus_first_person'),make_mannequin('questplus_first_person_slim',True),make_mannequin('questplus_third_person',third=True),make_mannequin('questplus_third_person_slim',True,True)]
# Standalone tablet motions are local attachment deltas. Never apply them on top of player reference tracks.
for suffix in ['open','idle','close']:
    source=models[3].animations[['open','idle','close'].index(suffix)]
    a=next(v for v in source['animators'].values() if v['name']=='tablet_root')
    tablet.animations.append({**copy.deepcopy(source),'uuid':uid('tablet/'+suffix),'name':'questplus.tablet_'+suffix,'animators':{uid('questplus_tablet/tablet_root'):copy.deepcopy(a)}})
for x,y,w,h,material in slots:
    if material in ('skin','cloth','head'):
        region=(x-1,y-1,x+w+1,y+h+1);mannequin_atlas.paste(atlas.crop(region),region[:2]);atlas.paste((27,24,29),region)
atlas.save(ROOT/'textures/questplus_tablet.png');mannequin_atlas.save(ROOT/'textures/preview_mannequin.png')
save('models/atlas-layout.json',{'resolution':[256,256],'pixels_per_model_unit':2,'islands':slots})
for m in models:save('blockbench/'+m.name+'.bbmodel',m.document())

def gecko(m):
    byid={e['uuid']:e for e in m.elements};groups={g['uuid']:g for g in m.groups};bones=[]
    def walk(node,parent=None):
        g=groups[node['uuid']];bone={'name':g['name'],'pivot':[-g['origin'][0],*g['origin'][1:]],'cubes':[]}
        if parent:bone['parent']=parent
        for child in node['children']:
            if isinstance(child,dict):continue
            e=byid[child];cube={'origin':[-e['to'][0],*e['from'][1:]],'size':list(np.subtract(e['to'],e['from']).astype(float)),'uv':{k:{'uv':v['uv'][:2] if k not in ('up','down') else v['uv'][2:],'uv_size':list((np.subtract(v['uv'][2:],v['uv'][:2])*(-1 if k in ('up','down') else 1)).astype(float))} for k,v in e['faces'].items()}}
            if any(e['rotation']):cube.update(pivot=[-e['origin'][0],*e['origin'][1:]],rotation=[-e['rotation'][0],-e['rotation'][1],e['rotation'][2]])
            bone['cubes'].append(cube)
        bones.append(bone)
        for child in node['children']:
            if isinstance(child,dict):walk(child,g['name'])
    for n in m.outliner:walk(n)
    return {'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.'+m.name,'texture_width':256,'texture_height':256,'visible_bounds_width':4,'visible_bounds_height':4,'visible_bounds_offset':[0,0,0]},'bones':bones}]}
save('models/questplus_tablet.geo.json',gecko(tablet))
def bedrock_animations(m,player=False):
    result={}
    for a in m.animations:
        bones={}
        for tr in a['animators'].values():
            if player and tr['name']=='tablet_root':continue
            channels={}
            for k in tr['keyframes']:
                vector=[k['data_points'][0][v] for v in ['x','y','z']]
                if k['channel']=='rotation':vector=[-vector[0],-vector[1],vector[2]]
                if k['channel']=='position':vector=[-vector[0],vector[1],vector[2]]
                channels.setdefault(k['channel'],{})[str(k['time'])]={'vector':vector}
            bones[tr['name']]=channels
        result[a['name']]={'loop':a['loop']=='loop','animation_length':a['length'],'bones':bones}
    return {'format_version':'1.8.0','animations':result}
save('animations/tablet/questplus_tablet.animation.json',bedrock_animations(tablet))
save('animations/player/questplus_third_person.animation.json',bedrock_animations(models[3],True))
save('animations/player/questplus_first_person.animation.json',bedrock_animations(models[1],True))

def rotation(v):
    x,y,z=np.radians(v);cx,sx=math.cos(x),math.sin(x);cy,sy=math.cos(y),math.sin(y);cz,sz=math.cos(z),math.sin(z)
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
def matrix(origin,rot,translation=(0,0,0),scale=(1,1,1)):
    r=rotation(rot)@np.diag(scale);m=np.eye(4);m[:3,:3]=r;m[:3,3]=np.array(origin)-r@np.array(origin)+translation;return m
def sample(anim,bone,channel,t,default):
    a=next((a for a in anim['animators'].values() if a['name']==bone),None)
    ks=sorted([k for k in a['keyframes'] if k['channel']==channel],key=lambda k:k['time']) if a else []
    if not ks:return default
    for i,k in enumerate(ks):
        if t<=k['time']:
            prev=ks[max(0,i-1)];f=0 if k['time']==prev['time'] else (t-prev['time'])/(k['time']-prev['time'])
            return blend([prev['data_points'][0][v] for v in 'xyz'],[k['data_points'][0][v] for v in 'xyz'],f)
    return [ks[-1]['data_points'][0][v] for v in 'xyz']
FACES={'north':[0,3,2,1],'south':[5,6,7,4],'west':[4,7,3,0],'east':[1,2,6,5],'up':[3,7,6,2],'down':[4,0,1,5]}
def render(m,path,eye,target,span=31,anim=None,t=0,size=(1000,800),label=None):
    w,h=size;eye=np.array(eye,float);target=np.array(target,float);forward=target-eye;forward/=np.linalg.norm(forward);right=np.cross(forward,[0,1,0]);right/=np.linalg.norm(right);up=np.cross(right,forward);view=np.array([right,up,forward]);scale=h/span
    buf=np.zeros((h,w,3),dtype=np.uint8);buf[:]=[28,30,34];depth=np.full((h,w),np.inf)
    transforms={};groups={g['uuid']:g for g in m.groups}
    def walk(n,parent):
        g=groups[n['uuid']];rot=sample(anim,g['name'],'rotation',t,[0,0,0]) if anim else [0,0,0];pos=sample(anim,g['name'],'position',t,[0,0,0]) if anim else [0,0,0];sc=sample(anim,g['name'],'scale',t,[1,1,1]) if anim else [1,1,1];mat=parent@matrix(g['origin'],rot,pos,sc)
        for c in n['children']:
            if isinstance(c,dict):walk(c,mat)
            else:transforms[c]=mat
    for n in m.outliner:walk(n,np.eye(4))
    for e in m.elements:
        if abs(np.linalg.det(transforms[e['uuid']][:3,:3]))<1e-8:continue
        x,y,z=e['from'];X,Y,Z=e['to'];p=np.array([[x,y,z],[X,y,z],[X,Y,z],[x,Y,z],[x,y,Z],[X,y,Z],[X,Y,Z],[x,Y,Z]],float)
        mat=transforms[e['uuid']]@matrix(e['origin'],e['rotation']);world=(mat@np.c_[p,np.ones(8)].T).T[:,:3];p=(view@(world-eye).T).T;p[:,0]=w/2+p[:,0]*scale;p[:,1]=h/2-p[:,1]*scale
        for face,inds in FACES.items():
            tex=np.array(mannequin_atlas if e['faces'][face]['texture']==1 else atlas)
            worldface=world[inds];normal=np.cross(worldface[1]-worldface[0],worldface[2]-worldface[0]);normal/=np.linalg.norm(normal)
            light=.6+.4*abs(np.dot(normal,np.array([-.4,.8,-.45])/1.002))
            u,v,U,V=e['faces'][face]['uv'];uv=np.array([[u,V],[u,v],[U,v],[U,V]],float)
            for tri in [(0,1,2),(0,2,3)]:
                a,b,c=p[[inds[j] for j in tri]];ta,tb,tc=uv[list(tri)];xmin=max(0,int(min(a[0],b[0],c[0])));xmax=min(w-1,int(max(a[0],b[0],c[0])+1));ymin=max(0,int(min(a[1],b[1],c[1])));ymax=min(h-1,int(max(a[1],b[1],c[1])+1))
                if xmin>xmax or ymin>ymax:continue
                yy,xx=np.mgrid[ymin:ymax+1,xmin:xmax+1];den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
                if abs(den)<1e-8:continue
                wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den;wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;wc=1-wa-wb;zz=wa*a[2]+wb*b[2]+wc*c[2];section=depth[ymin:ymax+1,xmin:xmax+1];mask=(wa>=0)&(wb>=0)&(wc>=0)&(zz<section)
                uu=np.clip((wa*ta[0]+wb*tb[0]+wc*tc[0]).astype(int),min(u,U),max(u,U)-1);vv=np.clip((wa*ta[1]+wb*tb[1]+wc*tc[1]).astype(int),min(v,V),max(v,V)-1)
                buf[ymin:ymax+1,xmin:xmax+1][mask]=(tex[vv[mask],uu[mask]]*light).astype(np.uint8);section[mask]=zz[mask]
    img=Image.fromarray(buf);d=ImageDraw.Draw(img);d.text((24,20),label or m.name,fill=(215,209,195));d.text((24,h-30),'QUEST PLUS / geometry preview / software render',fill=(139,142,145))
    if path:img.save(ROOT/'previews'/path)
    return img

render(tablet,'front.png',[0,0,-40],[0,0,0],22,label='FRONT / blank 5:3 display')
render(tablet,'back.png',[0,0,40],[0,0,0],22,label='BACK / leather straps and stone compass')
render(tablet,'profile.png',[35,8,-7],[0,0,0],22,label='PROFILE / layered construction')
render(tablet,'isometric.png',[28,20,-36],[0,0,0],25,label='WALNUT / iron / carved stone / amethyst')
for i,name in [(1,'first_person'),(3,'third_person'),(2,'first_person_slim'),(4,'third_person_slim')]:
    m=models[i];fp=i<3
    eye=[0,25.6,1] if fp else [33,29,-43];target=[0,17,-9] if fp else [0,17,-3]
    render(m,name+'.png',eye,target,25 if fp else 39,m.animations[1],0,label=name+' / consultation pose')
    stages=[]
    for ai,time,label in [(0,0,'pocket / 0.00s'),(0,.24,'grasp / 0.24s'),(0,.60,'draw / 0.60s'),(0,.94,'left joins / 0.94s'),(1,0,'held / 1.20s'),(2,1.2,'stowed / close 1.20s')]:stages.append(render(m,None,eye,target,29 if fp else 43,m.animations[ai],time,(500,400),label))
    sheet=Image.new('RGB',(1500,800))
    for j,img in enumerate(stages):sheet.paste(img,((j%3)*500,(j//3)*400))
    sheet.save(ROOT/'previews'/f'{name}_stages.png')
    if not name.endswith('slim'):
        frames=[]
        for ai,duration in [(0,DURATION),(1,4),(2,DURATION)]:
            for time in np.linspace(0,duration,max(2,round(duration*12))):frames.append(render(m,None,eye,target,29 if fp else 43,m.animations[ai],float(time),(500,400),name))
        frames[0].save(ROOT/'previews'/f'{name}.gif',save_all=True,append_images=frames[1:],duration=83,loop=0)
save('models/attachment.json',{'units':'model pixels; 16 pixels = 1 Minecraft block','tablet_bounds':{'min':[-12.1,-8.3,-2.12],'max':[12.1,8.65,1.35]},'front_normal':[0,0,-1],'screen':{'min':[-10,-6,-.82],'max':[10,6,-.82],'render_plane_z':-.835,'aspect_ratio':'5:3','origin_uv_top_left':[-10,6,-.835],'u_direction':[1,0,0],'v_direction':[0,-1,0]},'hand_anchors':{'left_hand_anchor':[-11.2,-2,1.1],'right_hand_anchor':[11.2,-2,1.1]},'held_transform':{'scale':[.65,.65,.65],'translation':[0,18.8,-10.5],'rotation':[0,180,0]},'preview_camera':{'eye':[0,17.6,.5],'target':[0,18.8,-10.5],'projection':'perspective','vertical_span':25},'note':'Camera is an authoring preview, calibrated for the first-person export. Tablet front faces +Z toward player in held pose. Anchors are prop-local placement guides, not tracked finger joints.'})
print(f'Generated {len(models)} bbmodels, {len(tablet.elements)} tablet cubes, {len(slots)} unique UV islands; atlas last row {sy+row}/256')
