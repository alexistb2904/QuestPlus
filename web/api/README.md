# QuestPlus web API

The web API connects Quest Studio to a Minecraft server. It serves the built editor, creates temporary editing sessions, stores shared packs and relays publication requests. The Minecraft server validates each pack and applies or rejects it. The editor can also be used offline without this service.

## Run locally

Install Node.js 22 or later. Build the editor from `web/editor/` first:

```sh
npm ci
npm run build
```

Then install the API dependencies from `web/api/` and start the server with a private key. For example, in PowerShell:

```powershell
npm ci
$env:HUB_SERVER_KEY = node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))"
$env:HUB_PUBLIC_URL = 'http://localhost:8787'
npm start
```

Open http://localhost:8787/ for the built editor or http://localhost:8787/health to check the API. Keep the generated key for the Minecraft configuration during your test. A new key invalidates existing server configuration.

For editor development with Vite, start `npm run dev` in `web/editor/`, set `STUDIO_ORIGIN=http://localhost:5173` on the API, and restart the API. In the editor, use `http://localhost:8787` as the server address.

## Connect Minecraft

Configure `questplus-hub.json` in the world or in the client configuration for a solo world. Use the API origin without `/api` and the same secret as `HUB_SERVER_KEY`:

```json
{
  "url": "http://localhost:8787",
  "serverKey": "YOUR_PRIVATE_KEY"
}
```

A world configuration takes precedence over the instance configuration. If `url` is omitted, the mod uses `https://quest.alexistb.com/`. This default is compiled into the same JAR by both build options 5 and 10. An explicit URL, including localhost for development, is preserved. The private `serverKey` is required for connected editing and is never bundled in the JAR. Without it, `/questplus editor` provides the website link for ZIP authoring and explains the missing configuration. Restart Minecraft after configuration changes. For Windows development, `dev.bat setup` creates local configuration and a key without overwriting existing credentials. See [Windows testing](../../docs/windows-testing.md) for paths and details.

An operator starts a session with `/questplus editor`. Open the link, request access and approve the matching code in Minecraft with `/questplus editor approve <code>`. The link alone does not grant access. Use `/questplus editor revoke` to end the session. Saving to the server waits for Minecraft to validate the pack before reporting success.

On Windows, `dev.bat client` prepares the local configuration and starts the web API in the background before launching Minecraft, so connected editing also works in solo worlds. An already running service is reused. Startup output is written to `run/questplus-client-web.log`; the local API stays available after the game closes. If startup fails, Minecraft still launches and reports how to start the API manually with `dev.bat api`. Direct Gradle launches and installed mod JARs still require a separately configured, running web API, including in solo. The JAR does not embed Node.js or the web service.

## Make changes

The API implementation is in `server.mjs`, with automated checks in `tests/`. Run them from this folder:

```sh
npm test
```

The API routes are documented in the [HTTP API reference](../docs-site/content/reference/web-api.md). Read the [architecture](../../docs/architecture.md) before changing the protocol. Keep the server key and player saves out of browser responses. Browser session tokens belong in memory. The Minecraft server must validate the complete pack and handle rollback.

## Deploy

For the complete site, copy `web/.env.example` to `web/.env`, generate a unique value for `HUB_SERVER_KEY`, and run `docker compose up -d --build` from `web/`. The editor, docs and API are served at `/`, `/docs/` and `/api/`. See the [web deployment guide](../README.md) for HTTPS and Coolify setup.

The `compose.yml` in this folder is an alternative that serves the API and editor without the documentation. Copy [.env.example](.env.example) to `.env`, set the key and run `docker compose up -d --build` here. The container listens on `127.0.0.1:8787` and needs an HTTPS reverse proxy for public access.

`HUB_PUBLIC_URL` and the Minecraft `url` must be the public origin, such as `https://quest.alexistb.com`, without `/api`. Use the same private key on the web service and Minecraft server. Never commit `.env` or `questplus-hub.json`. The `VITE_` settings are public build values. Rebuild the editor after changing them and never put secrets in them.

Shared packs are stored in the `questplus-packs` volume. Back it up before removing the deployment. Sessions and publication queues live in the API process, so do not run multiple independent API instances against the same storage expecting them to share session state.
