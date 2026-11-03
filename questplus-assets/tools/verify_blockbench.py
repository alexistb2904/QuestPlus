"""Reopen every source in the user's running Blockbench; capture native evidence."""
import json, base64
from pathlib import Path
from blockbench_bridge import call

ROOT=Path(__file__).resolve().parents[1]
def write(rel,data):
    (ROOT/rel).write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def shot(name,view=None):
    args={'width':1200,'height':900,'annotate':True}
    if view:args['view']=view
    value=call('screenshot',args)
    (ROOT/'previews'/name).write_bytes(base64.b64decode(value['base64']))
evidence={}
for path in sorted((ROOT/'blockbench').glob('*.bbmodel')):
    project=call('load_project',{'path':str(path)})
    evidence[path.name]={'loaded':project}
    check=call('check_model');evidence[path.name]['check_model']=check
    if path.stem=='questplus_tablet':
        write('previews/blockbench_model_check.json',check)
        write('previews/blockbench_complexity.json',call('audit_complexity',{'target':'prop'}))
        for view,name in [('front','front'),('back','back'),('right','profile'),('front_right','isometric')]:
            call('screenshot',{'view':view,'width':100,'height':100})
            call('execute_script',{'code':'Preview.selected.camera.zoom=1.5; Preview.selected.camera.updateProjectionMatrix(); return true;'})
            shot('blockbench_'+name+'.png')
        call('export_model',{'codec':'gltf','path':str(ROOT/'models/questplus_tablet.gltf')})
        # Use the real native codec for geometry and animations (including BB5 sign conventions).
        geo=call('execute_script',{'code':'return Codecs.bedrock.compile({raw:true});'})
        write('models/questplus_tablet.geo.json',geo)
    # A native save plus reload proves the round-trip rather than just successful JSON parsing.
    call('save_project',{'path':str(path)})
    evidence[path.name]['reopened']=call('load_project',{'path':str(path)})
    compiled=call('execute_script',{'code':'return {format_version:"1.8.0", animations:Object.fromEntries(Animation.all.map(a=>[a.name,a.compileBedrockAnimation()]))};'})
    if path.stem=='questplus_tablet':
        write('animations/tablet/questplus_tablet.animation.json',compiled)
    elif not path.stem.endswith('slim'):
        if 'first_person' in path.stem:
            from export_first_person import export
            compiled,_=export()
        else:
            from export_third_person import export, OUTPUT
            compiled=export()
            OUTPUT.write_text(json.dumps(compiled,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
            for anim in compiled['animations'].values():anim['bones'].pop('tablet_root',None)
        write('animations/player/'+path.stem+'.animation.json',compiled)
    if path.stem!='questplus_tablet':
        stats=[]
        names=call('execute_script',{'code':'return Animation.all.map(a=>a.name);'})
        for name in names:stats.append(call('analyze_animation',{'animation':name,'samples':16}))
        evidence[path.name]['animation_analysis']=stats
        # The generic humanoid rig checker expects elbows; vanilla arms deliberately stay rigid.
        evidence[path.name]['rig_check']=call('check_rig')
        call('execute_script',{'code':'Modes.options.animate.select(); Animation.all[1].select(); Timeline.setTime(0); Animator.preview(); return true;'})
        if 'first_person' in path.stem:
            camera=json.loads((ROOT/'models/attachment.json').read_text(encoding='utf-8'))['preview_camera']
            call('set_camera_angle',{'position':camera['eye'],'target':camera['target']})
        else:call('set_camera_angle',{'position':[30,28,-40],'target':[0,19,-4]})
        call('execute_script',{'code':'Preview.selected.camera.zoom=1; Preview.selected.camera.fov=65; Preview.selected.camera.updateProjectionMatrix(); return true;'})
        shot('blockbench_'+path.stem.replace('questplus_','')+'.png')
    print('Reopened and checked:',path.name,flush=True)
write('previews/blockbench-verification.json',evidence)
# Leave the requested exterior view visible, screen facing the player.
call('load_project',{'path':str(ROOT/'blockbench/questplus_third_person.bbmodel')})
call('execute_script',{'code':'Modes.options.animate.select(); Animation.all[1].select(); Timeline.setTime(0); Animator.preview(); return true;'})
call('set_camera_angle',{'position':[30,28,-40],'target':[0,19,-4]})
call('execute_script',{'code':'Preview.selected.camera.zoom=1; Preview.selected.camera.fov=45; Preview.selected.camera.updateProjectionMatrix(); return true;'})
