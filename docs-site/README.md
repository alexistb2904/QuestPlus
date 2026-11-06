# Quest Plus documentation

A static Markdown documentation site built with VitePress. English is the only published language for now.

```sh
cd docs-site
npm install
npm run dev
npm run build
```

- Content is under `content/`.
- Navigation, search, base path and locale scaffolding are in `.vitepress/config.mts`.
- Styling lives in `.vitepress/theme/custom.css` and follows the React Quest Studio visual identity.
- GitHub Pages serves the React editor at `/QuestPlus/` and this site at `/QuestPlus/docs/`.

To add another language, create its own translated Markdown tree and declare its locale, navigation and sidebar in the config. Do not automatically claim an unfinished translation is available.

Only document behavior present in the current source. Keep instructions concise, use simple punctuation and avoid long dashes and semicolons in prose.
