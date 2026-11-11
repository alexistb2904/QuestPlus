# Quest Studio

[**Open Quest Studio →**](https://quest.alexistb.com/)

Build quest packs visually in the browser. Use the local editor for ZIP workflows, or connect to the optional web service when an operator approves access.

![Quest Studio with the category library, diagram and properties](/media/studio.webp)

_The library selects content, the middle shows the tree, and Properties edits the current selection._

## Create a project

Choose **Resume** or **New project** when a saved draft exists. A new project can be empty or include examples.

1. Click **Add Category**.
2. Click **Add Quest** or right-click the diagram for a template.
3. Configure objectives, prerequisites and rewards in **Details**.
4. Drag nodes to arrange the tree and connect prerequisites.
5. Check validation and select **Export ZIP**.

| Panel        | Purpose                           |
| ------------ | --------------------------------- |
| Categories   | Create and reorder chapters       |
| Diagram      | Position quests and links         |
| Properties   | Edit titles, icons and visibility |
| Details      | Configure quest mechanics         |
| Translations | Add other languages               |

For a complete example, follow [Create your first quest](/guide/first-quest).

## Navigate the workspace

Select a category in the library to show its diagram. Select a quest to open its properties and detail tabs. Drag nodes to arrange the tree, use the wheel to zoom, and use the diagram's hand tool to pan. The fit control brings the current content back into view.

Resize the dividers to give the diagram or detail panel more room. Collapse the library when you need additional horizontal space. Use the search fields to find content in a large project.

![The equipment chapter zoomed out to show its whole quest tree and links](/media/tree.webp)

_Zoom out and center the tree to review a whole chapter and its prerequisite links._

## Choose the right tool

| Tool              | Use it for                                       |
| ----------------- | ------------------------------------------------ |
| Templates         | Reuse a common quest structure                   |
| Bulk Edit         | Apply a supported change to selected quests      |
| Reward tables     | Manage reusable named reward pools               |
| Mod Registry      | Find resource IDs and inspect available previews |
| Advanced JSON     | Inspect fields that need direct editing          |
| Validation status | Find errors before exporting                     |

![The Bulk Quest Editor with a category filter, a quest list and an operation to apply](/media/bulk-edit.webp)

_Bulk Edit applies one operation to every selected quest and can be undone._

![The registry browser before a server snapshot has been loaded](/media/registry.webp)

The bundled registry will show the IDs of installed mods when the Quest Studio web service is connected to a running Minecraft server. The registry snapshot is stored in the project and used for validation. A missing mod will show a warning icon.

## Save, export and publish

**Save draft** stores this browser's project. **Export ZIP** produces portable definitions. **Save to server** publishes through an approved session and waits for Minecraft validation. These actions have different destinations.

Studio supports offline authoring once its assets are available. For dependable offline use, run a local copy and keep ZIP backups. Browser storage is specific to the browser profile and origin.

::: warning Save your work
Browser drafts save locally. **Export ZIP** to keep a portable backup. Starting a new project replaces the draft.
:::

[Quest walkthrough](/guide/first-quest) · [Publish to a server](/guide/import-and-publish)
