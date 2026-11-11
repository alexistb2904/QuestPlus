"""Exercise both converters without changing the repository assets."""
import copy
import json
import shutil
import tempfile
import unittest
from pathlib import Path

import export_first_person as first
import export_third_person as third

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT.parent / 'client/src/main/resources/assets/questplus'


class ExportTest(unittest.TestCase):
    def test_converters(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'assets'
            shutil.copytree(ROOT / 'models', root / 'models')
            shutil.copytree(ROOT / 'blockbench', root / 'blockbench')
            (root / 'animations/player').mkdir(parents=True)
            runtime = Path(directory) / 'runtime'
            shutil.copytree(RUNTIME, runtime)

            first.ROOT, first.RUNTIME = root, runtime
            first.export(RUNTIME / 'fp_animations/questplus_first_person.animation.json',
                         RUNTIME / 'fp_models/questplus_first_person.geo.json')
            animation = json.loads((runtime / 'fp_animations/questplus_first_person.animation.json').read_text())
            self.assertEqual(animation, json.loads((runtime / 'animations/first_person.json').read_text()))
            self.assertEqual(animation['animations']['questplus.open']['loop'], 'hold_on_last_frame')
            self.assertIn('player_animation_library', animation['animations']['questplus.open'])

            third.SOURCE = root / 'blockbench/questplus_third_person.bbmodel'
            expected = json.loads((RUNTIME / 'animations/third_person.json').read_text())
            original = copy.deepcopy(expected)
            for clip in original['animations'].values():
                tablet = clip['bones']['tablet_root']
                for value in tablet['rotation'].values():
                    value[0], value[1] = -value[0], -value[1]
                for value in tablet['position'].values():
                    value[0] = -value[0]
            source = Path(directory) / 'animation.json'
            source.write_text(json.dumps(original))
            self.assertEqual(third.export(source), expected)


if __name__ == '__main__':
    unittest.main()
