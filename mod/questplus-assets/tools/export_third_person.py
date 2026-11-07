"""Convert Blockbench Bedrock animations to QuestPlus third-person resources."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'blockbench/questplus_third_person.bbmodel'
OUTPUT = ROOT.parent / 'client/src/main/resources/assets/questplus/animations/third_person.json'


def export(animation_path):
    compiled = json.loads(Path(animation_path).read_text(encoding='utf-8'))
    # The renderer expects tablet_root in Blockbench axes; the player bones stay Bedrock-native.
    for clip in compiled['animations'].values():
        root = clip['bones']['tablet_root']
        for value in root['rotation'].values():
            value[0], value[1] = -value[0], -value[1]
        for value in root['position'].values():
            value[0] = -value[0]
    verify_source(compiled)
    return compiled


def verify_source(compiled):
    source = json.loads(SOURCE.read_text(encoding='utf-8'))
    names = {group['uuid']: group['name'] for group in source['groups']}
    for animation in source['animations']:
        bones = compiled['animations'][animation['name']]['bones']
        for uuid, animator in animation['animators'].items():
            name = names[uuid]
            for frame in animator['keyframes']:
                if frame['interpolation'] != 'linear' or len(frame['data_points']) != 1:
                    raise ValueError(f'Unsupported keyframe in {animation["name"]}/{name}')
                channel = frame['channel']
                vector = [float(frame['data_points'][0][axis]) for axis in 'xyz']
                if name != 'tablet_root' and channel == 'rotation':
                    vector[0], vector[1] = -vector[0], -vector[1]
                elif name != 'tablet_root' and channel == 'position':
                    vector[0] = -vector[0]
                samples = bones[name][channel]
                key = min(samples, key=lambda time: abs(float(time) - frame['time']))
                if abs(float(key) - frame['time']) > .011 or any(abs(a-b) > 1e-5 for a,b in zip(vector, samples[key])):
                    raise ValueError(f'Codec mismatch in {animation["name"]}/{name}/{channel} at {frame["time"]}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('animation', help='Blockbench Bedrock animation JSON')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    data = export(args.animation)
    if args.check:
        if data != json.loads(OUTPUT.read_text(encoding='utf-8')):
            raise SystemExit('Runtime third-person export differs from Blockbench')
        print('Native third-person export matches source and runtime')
    else:
        OUTPUT.write_text(json.dumps(data, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
        print('Wrote native third-person export:', OUTPUT)
