# Import, export and publish

## Export from Quest Studio

1. Fix validation errors shown by the editor.
2. Click **Export ZIP**.
3. Store the ZIP and extracted source files in version control.
4. Install the quest pack into a test world before deployment.

Quest Studio exports `categories/*.json5`, `quests/*.json5`, `config.json`, `queststudio-compatibility.json` and `index.txt`.

## Manual server installation

Stop the dedicated server and back up the world. Extract the ZIP into `<world>/questplus/`, preserving the categories and quests folders. Restart or run:

```text
/questplus reload
```

Player progress is stored separately in `<world>/data/questplus/`. **Do not overwrite player progress when updating pack definitions.** Stable quest and objective IDs are important for persistence.

Invalid definitions fail validation rather than being silently replaced.

## Connected server editor

The optional Quest Hub service in `web/api/` enables approved online editing.

1. Configure and run the web API.
2. As a Minecraft operator, run `/questplus editor`.
3. Open the browser link and approve its code with `/questplus editor approve <code>`.
4. Load server quests and edit them.
5. Save to server and wait for Minecraft to validate the pack.

The access link alone grants no edit permission. Use `/questplus editor revoke` to revoke the session or `/questplus rollback` to restore the previous definition backup.

GitHub Pages hosts the **offline editor**, not the live Quest Hub API.

## Import an FTB Quests pack

Quest Studio accepts compatible FTB Quests JSON, JSON5 and SNBT chapter packs. Import the folder or ZIP, inspect the conversion report, select the quests and confirm. Dependencies of selected quests are included automatically.

The import reports unsupported tasks and blocks their dependent quests rather than silently changing gameplay rules. Original FTB files are not modified.

Private FTB player-progress migration is a separate operator workflow. It requires a preview and explicit confirmation.

See [supported conversions and migration rules](https://github.com/alexistb2904/QuestPlus/blob/main/docs/ftb-migration-compatibility.md).

## Reusable templates

Project templates may travel with a pack through `queststudio-compatibility.json`. Personal templates stored only in the browser require a deliberate export or copy to become portable.

See the [pack format](/reference/pack-format) for exact file responsibilities.
