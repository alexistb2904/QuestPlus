# Host the web service

The optional web service serves the built Quest Studio editor and `/api/` API from one origin. It handles temporary approval sessions, immutable shared packs and queued publications. Minecraft still validates and applies the pack.

The static GitHub Pages editor supports offline authoring but does not host API endpoints.

## Local setup

Use Node.js 22 and first build `web/editor/` with `npm ci` and `npm run build`. Install API dependencies with `npm ci` in `web/api/`.

Generate a strong random secret, for example with `openssl rand -hex 32`. Keep it private. Set `HUB_SERVER_KEY` to this value and `HUB_PUBLIC_URL` to `http://localhost:8787`, then start `node server.mjs` from `web/api/`.

For PowerShell, the environment setup has this form. Replace the placeholder before starting the service.

```powershell
$env:HUB_SERVER_KEY = '<your-random-secret>'
$env:HUB_PUBLIC_URL = 'http://localhost:8787'
node server.mjs
```

Create `<world>/questplus-hub.json`, or use `config/questplus-hub.json` as the instance fallback for solo development.

```json
{
	"url": "http://localhost:8787",
	"serverKey": "<the-same-random-secret>"
}
```

The world file takes precedence. Restart Minecraft after configuration. Plain HTTP is allowed for localhost development only.

For the hosted service, the mod defaults to `https://quest.alexistb.com/` when `url` is absent. Set `serverKey` to that service's private key to enable connected editing. The key is never included in compiled JARs. Without a key, `/questplus editor` opens the website for ZIP authoring and reports the missing configuration. Connection failures explain whether the address, connection, HTTPS certificate, credentials or web service needs attention. Technical diagnostics remain in the game log.

Open `http://localhost:8787/` and check `/health`. In Minecraft, run `/questplus editor`, open its link, request access and approve the matching code from the initiating operator. Do not copy the server secret into the browser.

## Production

`web/api/Dockerfile` builds the editor and packages it with the API. From `web/api/`, configure `HUB_PUBLIC_URL` with the public HTTPS origin and `HUB_SERVER_KEY` with the private secret, then run `docker compose up -d --build`.

Put an HTTPS reverse proxy in front of the service. Compose binds the internal port to localhost by default. Use that same public HTTPS origin in `questplus-hub.json`.

Minecraft opens outbound requests to the configured endpoint. No additional inbound Minecraft port is needed. Serve the editor and API from the same origin to avoid a separate cross origin setup.

## Sessions and recovery

Approval belongs to the operator who started the session. Disconnecting that operator revokes access. Sessions expire after inactivity. Closing the browser does not immediately revoke them, so use `/questplus editor revoke` when finished.

Keep the pack storage directory and server configuration in your backup plan. Sessions and publication queues are process local. Do not run independent writers against one directory as if it were a shared durable queue.

The API bounds sessions, bytes, request concurrency and stored packs. Busy requests can return `429` or `503` with a retry delay. Do not raise limits without measuring actual payload size and memory use.

[Publish from Studio](/guide/import-and-publish) · [HTTP reference](/reference/web-api)
