# QuestPlus web API

The web API connects Quest Studio to Minecraft servers. It hosts the React editor, relays temporary publishing sessions and stores shared quest packs. Minecraft validates and applies every update. The editor also works offline with ZIP export.

## Connect any Minecraft server

No account, registration or shared server key is required.

1. Install QuestPlus on the Minecraft server and ensure it can reach `https://quest.alexistb.com`.
2. As an in-game operator, run `/questplus editor`.
3. Open the link, request access and approve the displayed code with `/questplus editor approve <code>`.
4. Edit the quests in Studio and use **Save to server**. Wait until Minecraft confirms the reload.

The mod uses the public hosted Hub by default. No `questplus-hub.json` is required. For a custom self-hosted Hub, add `<world>/questplus-hub.json` or `config/questplus-hub.json`:

```json
{
  "url": "https://your-hub.example"
}
```

The world config takes precedence over the instance config. URLs must be HTTPS origins, except localhost development. Restart Minecraft after changing the configuration.

A server creates a new editing session without a global key. The Hub returns a random `serverToken` used only by that Minecraft server for polling, approval, publication acknowledgments and revocation. Browser approval is separate and must come from the operator who started the session. Neither the server token nor player progress is sent to the browser. The server validates full packs and rolls back failed updates.

Use `/questplus editor revoke` to end a session. The server revokes its previous session when a new one is created.

## Run locally

Install Node.js 22+, build the editor from `web/editor/`:

```sh
npm ci
npm run build
```

Then install the API dependencies from `web/api/`:

```sh
npm ci
```

Set `HUB_PUBLIC_URL=http://localhost:8787` in your environment and run `npm start`. Open `http://localhost:8787/` or check `http://localhost:8787/health`. Point Minecraft at the local origin:

```json
{
  "url": "http://localhost:8787"
}
```

For Vite development, run `npm run dev` in `web/editor/`, set `STUDIO_ORIGIN=http://localhost:5173` on the API and use `http://localhost:8787` as the Hub address. The Windows `dev.bat setup` helper still generates a local legacy key for compatibility testing; normal deployments do not need it. `dev.bat client` also prepares the local web API for solo testing. A standard JAR does not embed Node.js.

Run `npm test` from `web/api/` for API tests.

## Deploy

From `web/`, copy `.env.example` to `.env`, set `HUB_PUBLIC_URL` to the public HTTPS origin, then run:

```sh
docker compose up -d --build
```

This deploys the editor, API and documentation. The alternative `web/api/compose.yml` serves the editor and API without documentation. Keep the API behind an HTTPS reverse proxy, allow outbound requests from Minecraft servers, and protect the shared pack volume with backups. Sessions are process-local and must use a single API instance.

`HUB_SERVER_KEY` is **optional** for existing self-hosted legacy clients. Only use a 32+ character random secret when intentionally supporting clients that send that key. It is not an allowlist and is not required on public Hub deployments. Never put secrets in `VITE_` variables or commit credentials.

See the [HTTP API reference](../docs-site/content/reference/web-api.md) and [architecture rules](../../AGENTS.md). The existing server validation, revision checks, and in-game operator approval are required regardless of authentication mode.
