# Web: Quest Studio + integrated API

There are **exactly three runtime responsibilities**: `web/`, `client/`,
and `server/`. No standalone Hub application or second Minecraft mod is
required. `web/editor` holds React; `web/api` serves the *built React
application and versioned REST API from the same origin*. The web backend
also handles temporary editor sessions, sharing and a queued publication.
The game server always validates a publication and remains authoritative.

## Local setup

Requires Node 22 and Java 25 + Fabric 26.3.

1. Build React: `cd web/editor && npm ci && npm run build`.
2. Create one strong shared secret with `openssl rand -hex 32`.
3. Run the integrated web service:

       cd web/api
       HUB_SERVER_KEY=<64-hex-secret> HUB_PUBLIC_URL=http://localhost:8787 \
         node server.mjs

4. Browse `http://localhost:8787/`. Both React and the API are available
   from the same origin. For Vite HMR development use
   `cd web/editor && npm run dev`; set
   `STUDIO_ORIGIN=http://localhost:5173` on the API (restart it), and point
   the editor at `http://localhost:8787` using the Online / Server modal.
5. Create the file `<world>/questplus-hub.json` on the Fabric server:

       {"url":"http://localhost:8787","serverKey":"<same-64-hex-secret>"}

   Restart Minecraft. HTTP is permitted for localhost development only.
6. As an operator, run `/questplus editor`, open the received URL in a
   browser, request authorization and approve the matching six-digit
   code using `/questplus editor approve <code>`. Load server quests and
   publish when finished.

### Commands

- `/questplus editor` — generate a temporary authorized editing session.
- `/questplus editor approve <code>` — approve a browser in-game.
- `/questplus editor revoke` — revoke the editing session.
- `/questplus import <pack-id>` — preview a shared immutable pack.
- `/questplus import confirm` — import it within two minutes.
- `/questplus rollback` — restore the latest backup.
- `/questplus reload` — reload local definitions.

All new commands require Minecraft operator permissions, and an editor URL
does not grant rights by itself. The web API key is never sent to React.

## Production deployment

The Dockerfile at `web/api/Dockerfile` builds the React app before
assembling the server container, creating **one web service**.

From `web/api/`:

    HUB_PUBLIC_URL=https://questplus.example.com \
    HUB_SERVER_KEY=<64-hex-secret> docker compose up -d --build

Only expose HTTPS through your reverse proxy (Traefik/Caddy/nginx);
Compose binds the internal HTTP service to `127.0.0.1:8787` on the host.
Use `https://questplus.example.com` as the `url` in Minecraft's
`questplus-hub.json`. React and /v1 API share that exact domain; separate
STUDIO_ORIGIN or STUDIO_URL settings are not required.

The optional GitHub Pages workflow continues to publish the **offline**
React demo, but connecting a game server requires a running web backend.
No extra inbound port is needed on the Minecraft server.

## Data flow and security

    Browser (web/editor)
        | HTTPS, one-time approval + temporary editor bearer
        v
    Web service (web/api)
        | outbound HTTPS polling FROM Minecraft, server API key
        v
    Authoritative Minecraft server (server/)
        | existing QuestNetwork snapshots
        v
    Minecraft UI (client/)

The API uses an unlisted immutable pack store (default: seven days).
A live session is short-lived (30-minute inactivity timeout), bound to an
in-game operator approval and revoked on the owner's disconnect. The
server uploads only quest definitions and registry IDs, not player data.
It validates registry references against the installed mods before a
publication can be applied.

- Player saves and reward claims remain exclusively in the world's data folder.
- The server checks local revision changes, stages definitions, backs up
  old files, loads the new registry, and restores the old one on failure.
- Only the configured HTTPS endpoint is contacted, never an arbitrary
  URL supplied by a user.
- Browsers cannot send executable commands or access the game filesystem.
- Tokens are kept in browser memory, never in localStorage or share URLs.
- Modded registry **IDs** are transmitted; proprietary textures and JARs are not.
- A production public service needs durable queues, more quotas, monitoring
  and anti-abuse enforcement before supporting multiple independent servers.

## Tests

    cd web/editor && npm test && npm run typecheck && npm run build
    cd ../api && npm test
    cd ../.. && ./gradlew build

The Node suite launches a real web server to exercise temporary approvals,
publication acknowledgment, revision conflicts, pack sharing and static
serving. Java and game tests validate server behavior; this does not replace
a real two-client Minecraft integration test. Never promote the branch to
production until CI and dedicated-client tests pass.

## Windows test dashboard

From the repository root run `0-Menu-Tests.bat` or the dedicated
`8-Tests-Web.bat`, `9-Lancer-Web-API.bat`, or `10-Tout-Tester.bat`.
`12-Configuration-Locale.bat` creates a reusable local-only secret and
world configuration; it never overwrites existing credentials.
See [Windows testing](../../docs/windows-testing.md).
