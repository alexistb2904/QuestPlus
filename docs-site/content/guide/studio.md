# Quest Studio

Quest Studio is the **React 19 + TypeScript + Vite** editor in `web/editor/`. It works in the browser and can edit complete quest packs without writing individual JSON5 files.

## Launch locally

Node.js 22 is required for local development.

```sh
cd web/editor
npm ci
npm run dev
```

Open `http://localhost:5173/`. On Windows, `web/editor/start.bat` is also provided.

## Choose your starting point

When Quest Studio finds a previous browser draft, it asks you to **Resume current project** or **Create a new project**. The second option offers:

- **Empty project**, with no categories or quests
- **Project with examples**, using the bundled Quest Plus quest catalogue

Choosing a new project replaces the local browser draft. Export a ZIP first if you need to keep the old one. A first visit without a saved draft starts with the bundled catalogue.

## Understand the workspace

| Region | Purpose |
| --- | --- |
| Category library | Select, create and organize categories |
| Quest tree | Pan, position nodes, link prerequisites and place annotations |
| Properties | Edit the selected category, quest, objective or decoration |
| Details | Objectives, rewards, dependency rules, progression and localization |
| Top actions | Import, project tools, templates, registry browsing and history |
| Footer | Validation status, language and ZIP export |

Use mouse wheel to zoom the diagram, drag to pan with the pan tool, or use **Center tree**. Quest node icons keep their visual size while navigating the canvas.

## Make a pack

1. Add a category with **Add Category**.
2. Open the category and select **Add Quest**, or right-click the diagram to choose a template.
3. Configure the quest's English title, icon and optional description.
4. Add objectives, dependencies, conditions and rewards.
5. Drag the quest to position it, then adjust annotations and connections.
6. Resolve any validation issues.
7. **Export ZIP** for Minecraft, or save to an authorized server if connected.

Personal or project templates speed up repetitive authoring. Saving a quest as a template copies reusable mechanics but does not reuse old quest IDs or player progress.

## Saving

Quest Studio autosaves browser drafts under `questplus.quest-studio.v1`. The browser draft is **not a cloud backup**. The Save action downloads the portable ZIP in addition to saving locally, while the dedicated Export ZIP control is available at the bottom.

Do not rely on browser storage for distribution or backup. Export the pack into version control.

## Localized editing

The editor interface supports English, French, Spanish and simplified Chinese. Quest text is separate and uses `en_us` as its required source language. See [localization](/guide/localization).

See [categories and quests](/guide/categories-and-quests) for a full authoring walkthrough.
