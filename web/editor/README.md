# Quest Studio

Quest Studio is the visual editor for QuestPlus quest packs. It lets you arrange quests, edit objectives and rewards, and import or export a pack as a ZIP file. It runs in the browser and works offline. Connecting to a Minecraft server requires the [web API](../api/README.md).

Your draft is stored in the browser, not in an account. Export a ZIP to keep a backup or move your work to another device.

## Run locally

Install Node.js 22 or later, then run these commands from this folder:

```sh
npm ci
npm run dev
```

Open http://localhost:5173. The development server reloads as you edit the source. On Windows, you can also use `start.bat`.

## Make changes

The editor uses React, TypeScript and Vite. Start with the folder that matches your change:

- `src/components/` contains the interface, quest tree and editing panels.
- `src/hooks/useStudio.ts` manages the project state and editing actions.
- `src/core/` contains the quest model, validation, layout and ZIP handling.
- `src/data/` contains the default project and Minecraft registries. The challenge and equipment catalogues are also included in the mod build.
- `src/ui/` and `src/styles/` contain interface translations, icon helpers and styles.
- `public/` contains images and fonts. See [asset credits](public/ASSETS.md) and the [font license](public/fonts/OFL.txt).

The [design guide](DESIGN.md) describes the editor layout and visual choices. When changing pack data or validation, check the corresponding server behavior as the Minecraft server remains responsible for validating published packs.

## Configure and test

Copy [.env.example](.env.example) to `.env.local` if you need different API or documentation addresses. `VITE_QUESTPLUS_HUB_URL` is the API origin without `/api`. The editor adds the route itself. `VITE_QUESTPLUS_DOCS_URL` controls the documentation link. Vite embeds these values in the browser bundle, so do not put secrets in them. Without an override, production uses the current origin for the API and development uses `http://localhost:8787`.

```sh
npm test
npm run typecheck
npm run build
npm run preview
```

The production build is written to `dist/`. For connected editing and deployment, see the [API guide](../api/README.md) and the [web deployment guide](../README.md).
