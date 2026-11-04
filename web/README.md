# Web — Quest Studio + integrated API

This is one deployable web component consisting of:

- **editor/** — React 19, Vite, TypeScript, quest diagram, objective and
  reward editors, translations, ZIP import/export, local drafts.
- **server/** — Node 22, HTTP API and built React hosting, short-lived
  Minecraft editor sessions, in-game authorization, immutable shared packs,
  publication acknowledgments and rate limits.

The production container builds `editor/` and serves its compiled assets
and `/v1/*` API on one HTTPS origin. There is **no separate Hub service**;
the term Hub remains a feature name for the browser session protocol.

To run from a checkout:

1. `cd web/editor && npm ci && npm test && npm run build`
2. `cd ../server && npm test`
3. `HUB_SERVER_KEY=<secret> HUB_PUBLIC_URL=http://localhost:8787 node server.mjs`
4. Open `http://localhost:8787/`

Refer to [server/README.md](server/README.md) for the Minecraft config,
operator approval commands, Docker Compose and production requirements.

Offline editing remains available with `cd web/editor && npm run dev`
even when the API process is not running. The Java server still validates
every proposed pack and owns player progression.
