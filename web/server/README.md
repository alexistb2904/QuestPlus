# QuestPlus Hub — experimental secure web editing

QuestPlus Hub is an **opt-in, self-hosted Node.js 22+ relay** that connects the
existing Quest Studio web application to one Minecraft Fabric 26.3 server.

## How it works

1. A Minecraft operator runs **/questplus editor**. The server opens an
   outbound HTTPS request and receives a private browser session URL.
2. The browser opens Quest Studio, requests approval and shows a six-digit
   code. The server shows the same code in game.
3. The original operator authorizes with **/questplus editor approve 123456**.
   Only then does Hub grant the browser a temporary 256-bit edit token.
4. Quest Studio loads the current quest definitions plus the installed server
   registries (modded items, blocks, entities, biomes, structures, enchantments,
   effects, dimensions and tags).
5. **Publish to server** sends new JSON definitions for Minecraft validation.
   The server checks its local revision, backs up the old definitions,
   installs the proposed files, reloads, and confirms success or failure.
   On failure the previous definitions are restored.
6. Existing QuestNetwork synchronization broadcasts the new generation to
   online players. Player progress/reward ledgers are never uploaded.

The browser's editor token is kept **in memory**, never written to localStorage
or embedded in a share URL. A published pack link is a read capability only:
it grants **zero** Minecraft administration permissions.

## Run locally

Create a private random secret (at least 32 characters), for example using
the output of: openssl rand -hex 32

Run Hub (keep the same key across restarts):

    cd hub
    HUB_SERVER_KEY=YOUR_PRIVATE_64_CHAR_HEX_SECRET \
    HUB_PUBLIC_URL=http://localhost:8787 \
    STUDIO_ORIGIN=http://localhost:5173 \
    STUDIO_URL=http://localhost:5173/ \
    node server.mjs

Run Quest Studio from web/editor with npm install && npm run dev.

Create the following file in your Minecraft world's root directory
(world/questplus-hub.json), using the same Hub key:

    {
      "url": "http://localhost:8787",
      "serverKey": "YOUR_PRIVATE_64_CHAR_HEX_SECRET"
    }

Restart the Minecraft server. Hub integration is **disabled** when this file
does not exist.

## Production

Run behind a trusted HTTPS reverse proxy (Traefik, nginx or Caddy).
The included Docker Compose file binds Hub to 127.0.0.1:8787 by default.

Set HUB_PUBLIC_URL to your public HTTPS Hub domain,
STUDIO_ORIGIN to the **origin** hosting Quest Studio (e.g.
https://alexistb2904.github.io), STUDIO_URL to its **complete URL** including
any GitHub Pages path, and HUB_SERVER_KEY to the random secret shared with
the Minecraft server. Run docker compose up -d --build.

Do not expose port 8787 without TLS; non-local HTTP is rejected in Minecraft.
The deployment currently supports **one server per Hub instance**, not a
public multi-tenant account system.

## Server commands (OP level 2)

- /questplus editor — create a temporary secure browser session.
- /questplus editor approve <code> — approve browser after comparing codes.
- /questplus editor revoke — revoke the active editing session.
- /questplus import <pack-id> — download and preview an immutable shared pack.
- /questplus import confirm — apply the previewed pack within two minutes.
- /questplus rollback — restore the most recent pre-publication backup.
- /questplus reload — existing local file reload command.

In the web editor, use the Online / Server button to connect and
explicitly publish. The original Save still exports an offline ZIP.
Save online / Share creates a seven-day unlisted immutable pack that may
later be imported using a pack ID.

## Security and operational caveats

- Only the server sends the API key. Browser session IDs are NOT editor tokens.
- Approvals are bound to the original player UUID, require OP rights and are
  sent through authenticated server-to-Hub requests.
- Sessions expire after 30 minutes without editor activity (the browser keeps
  an authorized active session alive with one lightweight request per minute);
  session state is
  memory-only and Hub restart invalidates all active sessions.
- HTTP inputs are bounded (12 MiB), file names restricted, JSON validated,
  per-origin CORS configured, and anonymous pack creation rate limited.
- Shared pack IDs are 192-bit unguessable capabilities; retention is seven days.
- A server imports packs only from its **configured Hub**; it does not fetch
  arbitrary URLs supplied by a Minecraft command.
- Minecraft revalidates game references against its actual installed mods.
  Invalid changes are rolled back; backups are stored in
  world/questplus-hub-backups (review retention for production).
- Progress/reward state lives under world/data/questplus and is not replaced.
  Updating objective mechanics can make existing progress incompatible; review
  changes before publishing.
- Hub server-side session state and pending jobs are not persisted. In a
  production multi-tenant deployment add a durable queue, quotas, user
  accounts and stronger per-client anti-abuse controls.
- Treat unknown Hub URLs as untrusted. Never post private mods, player data or
  server credentials to a third-party Hub.
- Visual icons for modded items are not currently exported; the selectors
  provide identifiers and use fallback visuals where necessary.
- A full real-world Fabric client/server end-to-end test is required before
  deploying this work-in-progress branch publicly.

## Tests

Run: cd hub && npm test
Run: cd web/editor && npm test && npm run typecheck && npm run build
Run: ./gradlew build

The Hub test starts a real Node process to exercise the complete approval
handshake, revision conflicts, publication acknowledgements, remote pack
sharing, rejected payloads and revocation. Java regression tests validate
unsafe paths and prevent player data from entering the export.

## API summary

Server-key endpoints:
POST /v1/server/sessions, GET /v1/server/poll, POST /v1/server/approve,
POST /v1/server/ack, POST /v1/server/revoke, GET /v1/server/packs/:id.

Browser endpoints:
GET /v1/editor/sessions/:id (public metadata),
POST /v1/editor/sessions/:id/request (approval request),
POST /v1/editor/sessions/:id/status (request secret required),
GET /v1/editor/sessions/:id/data (editor token required),
POST /v1/editor/sessions/:id/publish (editor token required),
GET /v1/editor/sessions/:id/jobs/:job (editor token required),
POST /v1/packs (rate limited), GET /v1/packs/:id (unguessable pack ID).

For direct Node development, the listener defaults to 127.0.0.1. Docker
explicitly binds 0.0.0.0 *inside the container*, with host exposure limited
to 127.0.0.1 and the TLS reverse proxy.
