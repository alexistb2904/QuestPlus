# Quest Studio — React + TypeScript + Vite

Quest Studio now runs as a **real React 19 application** with reusable TSX components,
reactive project state and Vite hot module replacement. The QuestPlus JSON schema,
Minecraft 26.3 quest positions, and original data pack are preserved.

## Local development

Requires Node.js 22. On Windows, run `web/editor/start.bat`, or:

```bash
cd web/editor
npm install
npm run dev
```

Open **http://localhost:5173/**.

## Tests and production

```bash
npm test
npm run typecheck
npm run build
npm run preview
```

Vite outputs the React bundle in `dist/`. The integrated production runtime
in `../api/` builds and serves this bundle **on the same origin** as the
session and pack REST API. GitHub Pages still offers a static/offline demo,
but the live server workflow requires the integrated web backend.

## Features and storage

- Reuse the **20 categories and 222 quests** bundled by Minecraft Quest Plus (209 new Vanilla+ challenges plus 13 historical examples).
- Drag quests and annotations in an interactive tree that uses the same
  layout algorithm and world coordinates as the mod.
- Edit objective conditions, nested prerequisites, reward types, translations
  and quest descriptions; browse vanilla Minecraft icons or type a modded item ID.
- Add locale codes (for example `fr_fr`, `ja_jp`, `de_de`) while keeping
  `en_us` as the source language.
- Edit rich annotations with text formatting, vanilla fonts and embedded
  PNG/JPEG/GIF media. The JSON format remains compatible with the Minecraft client.
- Import QuestPlus ZIP, JSON files or an extracted folder; export a portable ZIP.
- Undo/redo and autosave using the existing browser storage key
  `questplus.quest-studio.v1`. Existing drafts remain readable.

The offline editor works without any backend. Online sessions and unlisted
shareable packs are optional features of `web/api/`. Drafts remain in
browser localStorage, not a hosted user account. Export ZIP regularly as a backup.

## React architecture

`src/main.tsx` mounts the app with `createRoot`.
`src/App.tsx` owns the workspace;
`src/hooks/useStudio.ts` manages project state and data operations.
React components in `src/components/` render the sidebar, tree canvas,
WYSIWYG editor, Minecraft icon picker and quest inspector.
Framework-independent JavaScript lives under `src/` and remains the source
of truth for Minecraft compatibility:

- `src/core/` — quest model, condition trees, layouts, IDs, rich text format and ZIP handling.
- `src/data/` — bundled quest definitions and Minecraft 26.3 registry.
- `src/ui/` — Minecraft icon lookups, rich-text DOM conversion and interface translations.
- `src/styles/` — shared Minecraft-inspired styling and React component styles.
- `public/` — the favicon/logo copied unchanged into the production build.

The editor root contains only application configuration, the HTML entry point,
tests, documentation and the Windows launcher; generated `dist/` and
`node_modules/` are ignored by Git.


## Minecraft workspace (4 November 2026)

The desktop editor follows the supplied reference: wood quest library, stone controls,
labelled graph cards, violet selection, details tabs below the graph and persistent
properties on the right. At narrow widths these panels stack; tabs remain horizontally
scrollable. The design contract is in `../../DESIGN.md`.

- Save / Ctrl+S preserves the browser draft **and downloads `QuestPlus-quests.zip`**,
  ready to import into the Minecraft mod. Automatic autosave remains local-only;
  the footer also provides a dedicated Export ZIP action. Invalid quest packs
  cannot be exported until validation issues are fixed.
- Grid visibility/spacing, snapping, selection/pan tools, fitting, wheel zoom and
  pointer/touch movement operate without changing Minecraft's layout coordinate system.
- Details expose objectives, rewards, prerequisites, progression/tiers/timers/no-death,
  information quests, translations and advanced JSON. Rich annotations remain editable.
  English is the required source for titles and descriptions; information buttons
  in Properties open the translations tab directly. The editor UI supports English,
  French, Spanish, and simplified Chinese independently from quest content locales.
- PNJ/NPC uses `npc_interact` with an entity-type target. To unlock a follow-up quest,
  make the interaction quest its prerequisite. Unique NPC definitions, direct NPC unlocks
  and branching dialogues are outside the current server schema.
- Progression changes are one undoable operation. Converting to information warns about
  removed mechanics; converting back preserves narrative text. Quests with dependents
  and categories containing quests cannot be deleted until their references/content are moved.

Executed checks on 2026-11-04: TypeScript, 30 Node tests, production build, browser workflow
(undo/redo, conversion cancel/preservation, NPC objective, blocked referenced deletion,
preview, ZIP download/reimport, snapped drag and undo, wheel zoom, saved reload), and desktop/mobile captures.
The repository Java 25 `gradlew.bat build` also passed (81 unit tests and 4 required server GameTests).
These checks do not establish real two-client multiplayer behavior, screen-reader usability
or the future NPC/dialogue manager.

To repeat the browser workflow against a disposable browser session with the bundled
four categories and thirteen quests, start Vite, open it with Playwright CLI, then run
`playwright-cli run-code --filename=web/editor/tests/browser-workflow.mjs` from the repository root.
The workflow creates its ZIP artifact under `output/playwright/`. Its final result is stored
in sessionStorage key `__studioWorkflowResult` because native confirmation dialogs can
make the CLI return before the whole async workflow has finished.

Asset provenance and font license: `public/ASSETS.md`, `public/fonts/OFL.txt`.

## Shared Vanilla+ catalogue

The default Studio project uses the same 209 new challenge definitions shipped in the Fabric JAR:
`src/data/challenge-catalogue.json` and `src/data/equipment-catalogue.json`.
The build embeds them via Gradle `processResources`, and the server installs individual JSONs on *first world initialization only*. Already initialized worlds are intentionally not modified.
To see the updated defaults in a previously customized Studio project, export your work, then select **Reset / Defaults**. Saved projects are never replaced silently.

The equipment quests use the server extension event `equipment_check` and condition `questplus:gear` to validate exact equipped armor and enchanted inventory items. For automation quests, the current objectives validate minimum components, not that redstone mechanisms operate correctly; functional machine detection remains future work.

### Diagram zoom parity

Quest Studio and the in-game tablet scale the **complete world-space diagram**,
including quest nodes, sprites, titles, rich annotations, image/GIF content,
compartment backgrounds, arrows and line thickness. Zoom is anchored to the
mouse pointer, not the viewport center. A typical mouse-wheel notch multiplies
zoom by 1.15 in either client; trackpads interpolate continuous fractional
steps. Both clamp at 20%–500%. Drag-pan remains in screen pixels, while saved
quest positions, route bends and annotation dimensions remain world coordinates.
The Studio's Center tree button keeps the current zoom, as in the tablet.
