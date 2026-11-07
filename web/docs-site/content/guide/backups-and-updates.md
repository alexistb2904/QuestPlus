# Backups and pack updates

Quest definitions and player progress are stored separately. Back up the complete world before installing a pack update or changing quest mechanics.

## Know the folders

| Location                       | Contains                                 | Update policy                                                  |
| ------------------------------ | ---------------------------------------- | -------------------------------------------------------------- |
| `config/questplus/default/`    | Default definitions for future worlds    | Ship with your modpack                                         |
| `<world>/questplus/`           | This world's active definitions          | Replace through a validated import or a controlled file update |
| `<world>/data/questplus/`      | Progress, teams and reward records       | Preserve as a coherent set                                     |
| `config/questplus-client.json` | Local presentation settings              | Separate from gameplay data                                    |
| `<world>/questplus-hub.json`   | Optional world web connection and secret | Keep private                                                   |

The current world takes its own copy of the default definitions. Updating the defaults does not silently alter existing worlds.

## Prepare an update

1. Export the previous and new Studio projects.
2. Stop the server and copy the complete world to a backup location.
3. Test the update on a copy using the same mods and registry IDs.
4. Keep quest, objective and tier IDs stable where the mechanic stays the same.
5. Play through unlocking, submissions, choices and repeated claims before updating the real world.

Text and presentation changes are different from mechanical changes. Changing event types, objective structure or progression can invalidate partial progress. There is no automatic migration for renamed IDs.

## Apply definitions

Use an approved Studio session and wait for confirmation, or stop the world and replace the files inside `<world>/questplus/`. Remove obsolete definition files as part of the controlled replacement so they cannot remain as extra quests. Preserve settings you still need from `config.json`.

Start the world and inspect validation messages. Operators can use `/questplus reload` after a file update. If the new definitions fail to load, fix the error or restore the previous definitions.

To intentionally copy the current defaults into an existing world, preview with `/questplus retrieve`, then use `/questplus retrieve confirm` within two minutes. This replaces definitions and retains progress, so test ID compatibility first.

## Restore the right backup

`/questplus rollback` restores the latest definition backup. It is not a player progress rollback.

For a full world restore, stop the server and restore a coherent backup of world data together. Do not mix isolated player, team and reward files from different points in time. Keep the failed world copy for diagnosis.

[Install a pack](/guide/import-and-publish) · [Troubleshooting](/guide/troubleshooting)
