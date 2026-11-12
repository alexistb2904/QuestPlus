---
title: "Quest Hub HTTP API"
description: "Reference for the Quest Hub HTTP API: public requests, approved editor requests and the operator approval lifecycle."
---

# Quest Hub HTTP API

Quest Hub is an **optional** web service. The static Quest Studio site does not host these endpoints.

The public API uses `/api/`. Previous `/v1/` routes remain available for existing clients.

## Public requests

| Endpoint              | Purpose                          |
| --------------------- | -------------------------------- |
| `GET /health`         | Check the service                |
| `POST /api/packs`     | Share an unlisted pack           |
| `GET /api/packs/<id>` | Fetch a shared pack              |
| `POST /api/feedback`  | Send feedback to the maintainers |

```sh
curl https://your-hub.example/health
```

Send `POST /api/packs` with JSON containing a `files` object of pack definition files. The response provides a temporary share URL. Keep unlisted pack IDs private.

`POST /api/feedback` takes `{ "rating": "good" | "bad", "message": "..." }` (1 to 2000 characters) and forwards it to the Discord webhook set in `FEEDBACK_DISCORD_WEBHOOK_URL`. It answers `503` when no webhook is configured.

## Approved editor requests

| Endpoint                                   | Purpose                  |
| ------------------------------------------ | ------------------------ |
| `GET /api/editor/sessions/<id>/data`       | Read quests and registry |
| `POST /api/editor/sessions/<id>/publish`   | Queue an update          |
| `GET /api/editor/sessions/<id>/jobs/<job>` | Check the result         |

These require an operator-approved session and bearer token. A publish request also requires `baseRevision`. Use Quest Studio to complete the approval flow.

## Approval lifecycle

`GET /api/editor/sessions/<id>` exposes public session metadata. `POST /api/editor/sessions/<id>/request` returns a request ID, request secret and comparison code. The initiating operator approves the code inside Minecraft.

The browser checks `POST /api/editor/sessions/<id>/status` with its request ID and secret. A successful approval returns an editor token. Keep that token in memory and use `Authorization: Bearer <editor-token>` for protected editor requests.

`GET /api/editor/sessions/<id>/ping` maintains the approved session and returns its expiry and revision. A session URL never substitutes for approval.

## Publish a complete pack

Send a JSON body with `baseRevision` and `files`, where `files` maps permitted relative definition paths to file contents. A successful submission returns HTTP `202` with an `id` and `status: pending`.

Poll `/api/editor/sessions/<id>/jobs/<job>` for the outcome. Minecraft validates the complete candidate, performs the reload or restoration, then acknowledges the result. The browser must wait for this result before reporting success.

| HTTP result    | Meaning                                          |
| -------------- | ------------------------------------------------ |
| `401` or `403` | Missing, expired or invalid authorization        |
| `404`          | Unknown or expired resource                      |
| `409`          | Revision conflict or another pending operation   |
| `413`          | Request or stored pack too large                 |
| `429`          | Rate limit, follow `Retry-After`                 |
| `503`          | Capacity limit, retain the draft and retry later |

Do not resubmit a pending publication just because a job poll was delayed. On a revision conflict, reload and reconcile rather than overwriting the base revision.

## Server requests

The `/api/server/sessions` route creates sessions without a global shared secret and returns a random `serverToken` only to the initiating Minecraft server. Use `Authorization: Bearer <serverToken>` for subsequent `/api/server/poll`, `/api/server/approve`, `/api/server/revoke` and `/api/server/ack` requests. Tokens are scoped to their session and never sent to the browser. Browser access still requires approval from the operator who started the session.

Shared packs can be fetched from the public `GET /api/packs/<id>` route and still require in-game confirmation before importing.

These routes are a publication relay. Public pack links cannot invoke remote console commands, grant rewards or fetch player saves.

::: warning Not a gameplay API
You cannot grant player progress through public Hub requests. Use the [server side Java API](/reference/java-api) to report gameplay.
:::

[Connect a server](/guide/import-and-publish)
