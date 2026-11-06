# Quest Hub HTTP API

Quest Hub is an **optional** web service. The static Quest Studio site does not host these endpoints.

## Public requests

| Endpoint | Purpose |
| --- | --- |
| `GET /health` | Check the service |
| `POST /v1/packs` | Share an unlisted pack |
| `GET /v1/packs/<id>` | Fetch a shared pack |

~~~sh
curl https://your-hub.example/health
~~~

Send `POST /v1/packs` with JSON containing a `files` object of pack definition files. The response provides a temporary share URL. Keep unlisted pack IDs private.

## Approved editor requests

| Endpoint | Purpose |
| --- | --- |
| `GET /v1/editor/sessions/<id>/data` | Read quests and registry |
| `POST /v1/editor/sessions/<id>/publish` | Queue an update |
| `GET /v1/editor/sessions/<id>/jobs/<job>` | Check the result |

These require an operator-approved session and bearer token. A publish request also requires `baseRevision`. Use Quest Studio to complete the approval flow.

::: warning Not a gameplay API
You cannot grant player progress through public Hub requests. Use the [server-side Java API](/reference/java-api) to report gameplay.
:::

[Connect a server](/guide/import-and-publish)
