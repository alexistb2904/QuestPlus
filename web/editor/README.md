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

Vite outputs the complete static site in `dist/`. GitHub Pages deploys
this production bundle on pushes to `main`. The base URL is relative so the
same build works at `/QuestPlus/` or on a custom domain.

## Features and storage

- Reuse the **4 categories and 13 quests** bundled by the Minecraft mod.
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

The site is client-only, with no server database. Export ZIP regularly as a
backup: browser localStorage is not guaranteed to persist forever.

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
