# Host the web service

Quest Studio's optional Hub serves the editor and `/api/` from the same origin. It relays temporary editing sessions and stores immutable shared packs. Minecraft performs all quest validation and changes. A static editor supports offline ZIP editing but not connected publishing.

## Public hosted editor

With the standard mod, no configuration is needed. The Hub defaults to `https://quest.alexistb.com/`.

An in-game operator runs `/questplus editor`, opens the link, requests browser access, then approves the matching code with `/questplus editor approve <code>`. Changes are only applied after the Minecraft server has validated and acknowledged them. Use `/questplus editor revoke` to end the session.

The old shared `serverKey` is **not needed**. Each server receives a unique, temporary server token when creating a session. The browser never receives this token and still needs explicit in-game approval.

## Self-hosted Hub

Build the editor with Node.js 22+ using `npm ci && npm run build` in `web/editor/`. Then in `web/api/` install dependencies with `npm ci`, set `HUB_PUBLIC_URL` to your HTTPS origin and start `node server.mjs`. For Docker and Coolify, use `web/compose.yml` or `web/api/compose.yml`, and configure `HUB_PUBLIC_URL`. `HUB_SERVER_KEY` is optional for legacy self-hosted clients.

To connect a Minecraft server to another Hub, create `<world>/questplus-hub.json` or `config/questplus-hub.json`:

```json
{
  "url": "https://your-hub.example"
}
```

The world file takes precedence. The URL must be an origin without paths or credentials. Plain HTTP is only supported on localhost for development. Restart Minecraft after changing the config.

Open `https://your-hub.example/health` to check the service. Minecraft makes outbound HTTPS requests, so no inbound Minecraft API port is required. Keep the editor and API on the same origin to avoid cross-origin issues.

## Sessions and recovery

A new session is created without prior approval from the Hub owner. The Hub returns a secret `serverToken` only to the initiating Minecraft server. It authenticates polling, operator approval, acknowledgments and revocation for that session, never for another server. An unrelated browser cannot edit from the session URL alone.

Only the original in-game operator can approve browser access. Their disconnect revokes the session. Sessions also expire after inactivity. Closing the browser tab does not revoke a session: use `/questplus editor revoke`.

The API limits sessions, memory, concurrent requests and stored packs; busy requests may return HTTP `429` or `503`. Keep backups of the pack storage. The session queue is process-local and multiple API instances do not share state.

[Publish from Studio](/guide/import-and-publish) · [HTTP reference](/reference/web-api)
