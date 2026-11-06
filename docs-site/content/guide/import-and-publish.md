# Import and publish

## Export a quest pack

1. Choose **Export ZIP** in Quest Studio.
2. Back up the world.
3. Extract the pack to `<world>/questplus/`.
4. Restart or run `/questplus reload`.

Do **not** replace `<world>/data/questplus/`, which stores player progress. Keep existing quest IDs stable.

## Edit a live server

Connected editing requires Quest Hub and operator approval.

1. Run `/questplus editor` as an operator.
2. Open the link and request access.
3. Approve with `/questplus editor approve <code>`.
4. Edit the quest pack and click **Save to server**.

Use `/questplus editor revoke` to end access and `/questplus rollback` to restore the last definitions.

::: tip Offline editor
GitHub Pages hosts Quest Studio, not the live server API. Online editing needs a running Hub.
:::

## Import FTB Quests

Import a compatible FTB pack, review the conversion report and confirm. Custom scripts need a dedicated integration.

[Hub requests](/reference/web-api) · [Commands](/reference/commands)
