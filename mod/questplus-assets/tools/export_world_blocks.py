"""Export editable Blockbench world sources without opening the GUI."""
import base64
import copy
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
SOURCES = ROOT / 'questplus-assets/blockbench/world'
ASSETS = ROOT / 'client/src/main/resources/assets/questplus'
SIZES = [(3, 2), (4, 2), (4, 3), (6, 4)]
def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
def export():
    models = {}
    texture = None
    for path in sorted(SOURCES.glob('*.bbmodel')):
        source = json.loads(path.read_text(encoding='utf-8'))
        if source['meta']['model_format'] != 'java_block':
            continue
        png = base64.b64decode(source['textures'][0]['source'].split(',', 1)[1])
        assert texture is None or png == texture, 'World-block atlases diverged'
        texture = png
        elements = []
        for cube in source['elements']:
            assert cube['type'] == 'cube' and not any(cube.get('rotation', [0, 0, 0]))
            assert all(0 <= a < b <= 16 for a, b in zip(cube['from'], cube['to'])), path
            faces = {}
            for direction, face in cube['faces'].items():
                assert face['texture'] == 0 and all(0 <= n <= 64 for n in face['uv'])
                faces[direction] = {'uv': [n / 4 for n in face['uv']], 'texture': '#utility'}
            elements.append({'name': cube['name'], 'from': cube['from'], 'to': cube['to'], 'faces': faces})
        model = {'textures': {'utility': 'questplus:block/questplus_utility', 'particle': 'questplus:block/questplus_utility'}, 'elements': elements}
        models[path.stem] = model
        write(ASSETS / f'models/block/{path.stem}.json', model)
    assert len(models) == 12
    target = ASSETS / 'textures/block/questplus_utility.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(texture)
    variants = {}
    for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
        for size, (w, h) in enumerate(SIZES):
            for x in range(6):
                for y in range(4):
                    part = ('n' if y == h - 1 else 's' if y == 0 else '') + ('w' if x == 0 else 'e' if x == w - 1 else '')
                    variants[f'facing={facing},size={size},column={x},row={y}'] = {'model': 'questplus:block/quest_board_' + (part or 'c'), 'y': angle}
    assert len(variants) == 384
    write(ASSETS / 'blockstates/quest_board.json', {'variants': variants})
    write(ASSETS / 'blockstates/submission_station.json', {'variants': {'facing=' + f: {'model': 'questplus:block/submission_station', 'y': a} for f, a in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}})
    write(ASSETS / 'blockstates/quest_relay.json', {'variants': {'powered=' + str(on).lower(): {'model': 'questplus:block/quest_relay' + ('_on' if on else '')} for on in [False, True]}})
    board = {'textures': models['quest_board_c']['textures'], 'elements': []}
    for y in range(2):
        for x in range(3):
            part = ('n' if y else 's') + ('w' if x == 0 else 'e' if x == 2 else '')
            for original in models['quest_board_' + part]['elements']:
                e = copy.deepcopy(original)
                for key in ['from', 'to']:
                    e[key] = [(e[key][0] + x * 16) / 3, (e[key][1] + y * 16) / 3 + 8 / 3, (e[key][2] - 13) / 3 + 8]
                board['elements'].append(e)
    write(ASSETS / 'models/item/quest_board.json', board)
    for name in ['quest_board', 'submission_station', 'quest_relay']:
        if name != 'quest_board':
            write(ASSETS / f'models/item/{name}.json', {'parent': 'questplus:block/' + name, 'display': {'gui': {'rotation': [30, 225, 0], 'scale': [0.65] * 3}}})
        write(ASSETS / f'items/{name}.json', {'model': {'type': 'minecraft:model', 'model': 'questplus:item/' + name}})
    print('Exported 12 block models, 384 board states, 3 items and shared atlas')
if __name__ == '__main__':
    export()
