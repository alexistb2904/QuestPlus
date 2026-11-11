# Editing tablet models

The editable projects are in `blockbench/`. Open them directly in the Blockbench desktop application. The mod uses the exported JSON files in `mod/client/src/main/resources/assets/questplus/`. Editing a `.bbmodel` alone does not update the game.

The converters require Python 3 and use only its standard library. They do not require an AI agent or a local Blockbench bridge. Run the commands below from the repository root. On Windows, use `python` if `python3` is not available.

## First person

1. Open `blockbench/questplus_first_person.bbmodel` in Blockbench and save your changes.
2. Export the Bedrock geometry JSON and the Bedrock animation JSON using the Blockbench export commands. Save them outside the tracked asset directories, for example as `geometry.json` and `animation.json` in a temporary folder. Pass their actual paths to the command below.
3. Run `python3 mod/questplus-assets/tools/export_first_person.py /path/to/animation.json /path/to/geometry.json`.

The converter updates the first person animation and geometry copies in `animations/player/`, `models/` and the client resources. It applies the camera adjustment from `models/attachment.json`, adds FP PAL animation metadata and removes the arm cubes because Minecraft renders the player arms. Keep the required animation and bone names used by the mod.

## Tablet item (inventory and dropped item)

The item model is edited separately in `blockbench/questplus_tablet_item.bbmodel`. No script creates it. Save the project, then use **File › Export › Export Block/Item Model** to overwrite `mod/client/src/main/resources/assets/questplus/models/item/tablet.json`. Its texture is `textures/fp/questplus_first_person.png`, shared with the first person model.

World blocks (board, station, relay) follow the same rule; see `WORLD-BLOCKS.md`.

## Third person

1. Open `blockbench/questplus_third_person.bbmodel` and save your changes.
2. Export the Bedrock animation JSON to a temporary file.
3. Run `python3 mod/questplus-assets/tools/export_third_person.py /path/to/animation.json`.

The converter checks the animation keyframes against the saved Blockbench project, adjusts the tablet root coordinates and updates the third person runtime animation. Add `--check` to compare the converted export with the existing runtime file without writing it. The check requires linear keyframes with one data point.

## Textures and verification

The converters do not export textures. If you edit a texture, export the PNG from Blockbench and update the corresponding texture in `mod/client/src/main/resources/assets/questplus/textures/`. Check the paths referenced by the relevant geometry and item model. Review the resulting changes before committing the editable model and runtime resources together.

Run `python3 mod/questplus-assets/tools/test_exports.py` from the repository root to test the converters on temporary copies of the existing assets.

Run the focused Java tests from `mod/` with `./gradlew test --tests dev.questplus.fabric.client.FirstPersonAssetTest --tests dev.questplus.fabric.client.TabletThirdPersonAnimationTest`. On Windows, use `gradlew.bat`. These tests check runtime assets and keyframes. They do not replace opening the game to inspect animation, textures and positioning.

The other local scripts in `tools/` are ignored. They are not needed for these export steps or for the normal build.
