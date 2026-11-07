# QuestPlus documentation

This folder contains the public QuestPlus guides. The site is built with VitePress from Markdown files in `content/`.

## Run locally

Install Node.js 22 or later, then run these commands from this folder:

```sh
npm ci
npm run dev
```

Open the address printed by VitePress. Changes to Markdown and theme files appear in the local preview.

## Edit the documentation

- Put player and modpack walkthroughs in `content/guide/`.
- Put technical and configuration references in `content/reference/`.
- Update `.vitepress/config.mts` when a new page needs a navigation or sidebar entry.
- Edit `.vitepress/theme/` for shared layout and styles.
- Store public images and videos in `content/public/`. Add useful alternative text to images.

Write in clear English and document the behavior that exists in the project. Avoid semicolons and grammatical dashes. Keep punctuation required in code and paths. See the [media guide](content/reference/media.md) for capture sources and attribution.

## Check and build

Run the documentation checks from the repository root:

```sh
node scripts/docs/check-docs.mjs
```

Then build from this folder:

```sh
npm run build
npm run preview
```

The build is written to `dist/`. Review changed pages on desktop and mobile, including their links and images. The VitePress configuration uses `/QuestPlus/docs/` for GitHub Pages. The [web container](../Dockerfile) builds the documentation under `/docs/` for the integrated deployment. See the [web deployment guide](../README.md).
