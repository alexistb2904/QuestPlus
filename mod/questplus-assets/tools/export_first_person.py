"""Convert Blockbench Bedrock exports to QuestPlus first-person resources."""
import argparse
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT.parent / 'client/src/main/resources/assets/questplus'

def export(animation_path, geometry_path):
    animation = json.loads(Path(animation_path).read_text(encoding='utf-8'))
    geometry = json.loads(Path(geometry_path).read_text(encoding='utf-8'))
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
        # Keep static Blockbench channels in the same sampled form as animated tracks.
        for bone in clip['bones'].values():
            for channel,value in list(bone.items()):
                if channel in ('rotation','position','scale') and not isinstance(value,dict):
                    bone[channel] = {'0.0': value if isinstance(value,list) else [value]*3}
        clip['bones']['fp_player_arms'] = {'rotation': {'0.0': [-math.degrees(pitch),0,0]}}
        clip['player_animation_library'] = {'fpDisplayTranslation': display, 'fpDisplayScale': 1, 'fpDisplayAnchor': 'camera'}
    animation['animations']['questplus.open']['loop'] = 'hold_on_last_frame'
    animation['animations']['questplus.close']['loop'] = 'hold_on_last_frame'
    animation['animations']['questplus.carry_open']['loop'] = 'hold_on_last_frame'
    animation['animations']['questplus.carry_close']['loop'] = 'hold_on_last_frame'
    animation['animations']['questplus.carry_attack']['loop'] = 'hold_on_last_frame'
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
    # Reuse the physical tablet and its UVs for inventory and third-person item rendering.
    elements = []
    def item_point(point):
        return [round(point[0]+8,6),round(point[1]-18.8+8,6),round(point[2]+9.785+8,6)]
    for bone in bones:
        for cube in bone.get('cubes', []):
            element = {'from': item_point(cube['origin']),
                       'to': item_point([a+b for a,b in zip(cube['origin'],cube['size'])]),
                       'faces': {}}
            for face, uv in cube['uv'].items():
                u,v = uv['uv']; w,h = uv['uv_size']
                element['faces'][face] = {'uv': [u/16,v/16,(u+w)/16,(v+h)/16], 'texture': '#tablet'}
            rotations = [(axis,angle) for axis,angle in zip('xyz',cube.get('rotation',[])) if angle]
            if rotations:
                assert len(rotations)==1 and rotations[0][1] in (-45,-22.5,22.5,45), rotations
                element['rotation'] = {'origin': item_point(cube['pivot']), 'axis': rotations[0][0], 'angle': rotations[0][1]}
            elements.append(element)
    item = {'textures': {'tablet': 'questplus:fp/questplus_first_person', 'particle': 'questplus:fp/questplus_first_person'},
            'elements': elements, 'display': {
                'gui': {'rotation': [15,155,0], 'scale': [.85,.85,.85]},
                'thirdperson_righthand': {'rotation': [0,0,0], 'translation': [0,2,0], 'scale': [.4,.4,.4]},
                'thirdperson_lefthand': {'rotation': [0,0,0], 'translation': [0,2,0], 'scale': [.4,.4,.4]},
                'firstperson_righthand': {'rotation': [0,0,-20], 'translation': [2,-3,-3], 'scale': [.5,.5,.5]},
                'firstperson_lefthand': {'rotation': [0,0,20], 'translation': [2,-3,-3], 'scale': [.5,.5,.5]}}}
    (RUNTIME/'models/item/tablet.json').write_text(json.dumps(item,indent=2)+'\n',encoding='utf-8')
    print('Native Blockbench export; camera display:', display)
    return animation, geometry

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('animation', help='Blockbench Bedrock animation JSON')
    parser.add_argument('geometry', help='Blockbench Bedrock geometry JSON')
    args = parser.parse_args()
    export(args.animation, args.geometry)
