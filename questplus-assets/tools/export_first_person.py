"""Export the authoritative first-person project through the running Blockbench codec."""
import json
import math
from pathlib import Path
from blockbench_bridge import call

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT.parent / 'client/src/main/resources/assets/questplus'

def export():
    call('load_project', {'path': str(ROOT/'blockbench/questplus_first_person.bbmodel')})
    animation = call('execute_script', {'code': 'return {format_version:"1.8.0",animations:Object.fromEntries(Animation.all.map(a=>[a.name,a.compileBedrockAnimation()]))};'})
    geometry = call('execute_script', {'code': 'return Codecs.bedrock.compile({raw:true});'})
    bones = geometry['minecraft:geometry'][0]['bones']
    screen = next(b for b in bones if b['name'] == 'screen')['cubes'][0]
    center_y = screen['origin'][1] + screen['size'][1]/2
    camera=json.loads((ROOT/'models/attachment.json').read_text(encoding='utf-8'))['preview_camera']['eye']
    center_z=next(b for b in bones if b['name']=='tablet_root')['pivot'][2]
    pitch=math.atan2(camera[1]-center_y,camera[2]-center_z)
    # Reproduce the Blockbench preview camera, including its look-at rotation.
    # FP PAL already adds its camera anchor (0,-.52,-.72) blocks.
    display=[0,round(.52*16-math.cos(pitch)*camera[1]+math.sin(pitch)*camera[2],6),
        round(.72*16-math.sin(pitch)*camera[1]-math.cos(pitch)*camera[2],6)]
    animation['azurelib_format_version'] = 2
    for clip in animation['animations'].values():
        clip['bones']['fp_player_arms'] = {'rotation': {'0.0': [-math.degrees(pitch),0,0]}}
        clip['player_animation_library'] = {'fpDisplayTranslation': display, 'fpDisplayScale': 1, 'fpDisplayAnchor': 'camera'}
    animation['animations']['questplus.open']['loop'] = 'hold_on_last_frame'
    animation['animations']['questplus.close']['loop'] = 'hold_on_last_frame'
    for bone in bones:
        if bone['name'] in ('rightArm','leftArm'):
            bone.pop('cubes', None)
    for path, data in [
        (ROOT/'animations/player/questplus_first_person.animation.json', animation),
        (ROOT/'models/questplus_first_person.geo.json', geometry),
        (RUNTIME/'animations/first_person.json', animation),
        (RUNTIME/'fp_animations/questplus_first_person.animation.json', animation),
        (RUNTIME/'fp_models/questplus_first_person.geo.json', geometry),
    ]:
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
    print('Native Blockbench export; camera display:', display)
    return animation, geometry

if __name__ == '__main__':
    export()
