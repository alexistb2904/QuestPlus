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
    textures = {}
    for path in sorted(SOURCES.glob('*.bbmodel')):
        source = json.loads(path.read_text(encoding='utf-8'))
        if source['meta']['model_format'] != 'java_block':
            continue
        png = base64.b64decode(source['textures'][0]['source'].split(',', 1)[1])
        texture_name = 'quest_relay' if path.stem in ('quest_relay', 'quest_relay_on') else 'questplus_utility'
        assert texture_name not in textures or png == textures[texture_name], 'World-block atlases diverged'
        textures[texture_name] = png
        elements = []
        for cube in source['elements']:
            assert cube['type'] == 'cube' and not any(cube.get('rotation', [0, 0, 0]))
            assert all(0 <= a < b <= 16 for a, b in zip(cube['from'], cube['to'])), path
            faces = {}
            for direction, face in cube['faces'].items():
                assert face['texture'] == 0 and all(0 <= n <= 64 for n in face['uv'])
                faces[direction] = {'uv': [n / 4 for n in face['uv']], 'texture': '#utility'}
            elements.append({'name': cube['name'], 'from': cube['from'], 'to': cube['to'], 'faces': faces})
        model = {'textures': {'utility': 'questplus:block/' + texture_name, 'particle': 'questplus:block/' + texture_name}, 'elements': elements}
        models[path.stem] = model
        write(ASSETS / f'models/block/{path.stem}.json', model)
    assert len(models) >= 12
    for name, png in textures.items():
        target = ASSETS / f'textures/block/{name}.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(png)
    variants = {}
    for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
        for size, (w, h) in enumerate(SIZES):
            for x in range(6):
                for y in range(4):
                    part = ('n' if y == h - 1 else 's' if y == 0 else '') + ('w' if x == 0 else 'e' if x == w - 1 else '')
                    variants[f'facing={facing},size={size},column={x},row={y}'] = {'model': 'questplus:block/quest_board_' + (part or 'c'), 'y': angle}
    assert len(variants) == 384
    multipart = []
    for key, model in variants.items():
        when = dict(item.split('=') for item in key.split(','))
        when['modular'] = 'false'
        multipart.append({'when': when, 'apply': model})
    for edges in range(16):
        template = models['quest_board_module']
        model = {'textures': template['textures'], 'elements': copy.deepcopy(models['quest_board_c']['elements'])}
        for bit, side in enumerate(['n', 's', 'w', 'e']):
            if edges & (1 << bit):
                model['elements'].extend(copy.deepcopy(e) for e in template['elements']
                                         if e['name'].endswith('_' + side))
        for element in model['elements']:
            if element['name'].endswith(('_w', '_e')):
                element['from'][1] = 2 if edges & 2 else 0
                element['to'][1] = 14 if edges & 1 else 16
        for vertical, horizontal, mask in [('n', 'w', 5), ('n', 'e', 9), ('s', 'w', 6), ('s', 'e', 10)]:
            if edges & mask == mask:
                model['elements'].extend(copy.deepcopy(e) for e in template['elements']
                                         if e['name'].endswith('_' + vertical + horizontal))
        # Opposite borders and one-cell strips use the same full-height edge pieces.
        write(ASSETS / f'models/block/quest_board_module_{edges}.json', model)
        for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
            multipart.append({'when': {'modular': 'true', 'edges': str(edges), 'facing': facing},
                              'apply': {'model': f'questplus:block/quest_board_module_{edges}', 'y': angle}})
    assert len(multipart) == 448
    write(ASSETS / 'blockstates/quest_board.json', {'multipart': multipart})
    write(ASSETS / 'blockstates/submission_station.json', {'variants': {'facing=' + f: {'model': 'questplus:block/submission_station', 'y': a} for f, a in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}})
    write(ASSETS / 'blockstates/quest_relay.json', {'variants': {'powered=' + str(on).lower(): {'model': 'questplus:block/quest_relay' + ('_on' if on else '')} for on in [False, True]}})
    display = json.loads((SOURCES / 'quest_board_module.bbmodel').read_text(encoding='utf-8'))['display']
    write(ASSETS / 'models/item/quest_board.json', {'parent': 'questplus:block/quest_board_module_15', 'display': display})
    for name in ['quest_board', 'submission_station', 'quest_relay']:
        if name != 'quest_board':
            display = json.loads((SOURCES / f'{name}.bbmodel').read_text(encoding='utf-8'))['display']
            write(ASSETS / f'models/item/{name}.json', {'parent': 'questplus:block/' + name, 'display': display})
        write(ASSETS / f'items/{name}.json', {'model': {'type': 'minecraft:model', 'model': 'questplus:item/' + name}})
    print('Exported world blocks, 16 modular borders, 448 board rules, 3 item poses and material atlases')
if __name__ == '__main__':
    export()
