# QuestPlus web

The web project contains three parts:

- [Quest Studio](editor/README.md) is the browser editor for quest packs.
- [The API](api/README.md) connects the editor to Minecraft and stores shared packs.
- [The documentation site](docs-site/README.md) contains the player and developer guides.

The editor works offline. For connected editing, the API and editor share an origin. The Minecraft server validates each submitted pack and keeps player progress.

## Run locally

For editor work, follow the [editor setup](editor/README.md). For a connected session, build the editor and start the API using the [API setup](api/README.md). The documentation has its own [local preview](docs-site/README.md).

## Deploy the complete site

Copy [.env.example](.env.example) to `.env` and set `HUB_SERVER_KEY` to a unique private value. You can generate one with `openssl rand -hex 32`. The example configures `https://quest.alexitb.com` as the public origin. Set the same origin and key in Minecraft's `questplus-hub.json`.

From `web/`, run:

```sh
docker compose up -d --build
```

The web service routes the editor at `/`, the documentation at `/docs/` and the API at `/api/`. Put HTTPS in front of the `web` service. The API should remain private behind the proxy. Back up the `questplus-packs` volume before removing or recreating the deployment.

For Coolify, create a Docker Compose application from Git using `web/compose.yml`. Set `HUB_PUBLIC_URL` and `HUB_SERVER_KEY` in Coolify. Assign the HTTPS domain to the `web` service on internal port 80. Set `VITE_QUESTPLUS_HUB_URL` and `VITE_QUESTPLUS_DOCS_URL` if the editor build needs explicit API or documentation addresses.

`HUB_PUBLIC_URL` and `VITE_QUESTPLUS_HUB_URL` use the origin without `/api`. The frontend adds API paths itself. Vite embeds `VITE_` values in public JavaScript, so never put secrets in them. Rebuild after changing these values. Check `/health` and `/docs/`, then test an operator approval session in Minecraft.

The separate [API Compose file](api/compose.yml) serves the editor and API without the documentation. Its setup is described in the [API guide](api/README.md).
