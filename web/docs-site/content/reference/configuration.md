# Configuration

Pack configuration, client presentation and web credentials have different owners. Change the file that belongs to the behavior you intend to configure.

## World pack configuration

`<world>/questplus/config.json` configures the active world. The default copy for new worlds lives at `config/questplus/default/config.json`.

```json
{
	"schemaVersion": 1,
	"autosaveSeconds": 5,
	"teamRewardPolicy": "all_members",
	"allowDuplicateTeamNames": true,
	"enableCommandRewards": false,
	"dropLootCrates": false
}
```

| Field                  | Accepted values                              | Default                           |
| ---------------------- | -------------------------------------------- | --------------------------------- |
| `schemaVersion`        | `1`                                          | Required                          |
| `autosaveSeconds`      | Integer from 1 to 300                        | Required, bundled value is 5      |
| `teamRewardPolicy`     | `all_members` or `contributor`               | `all_members`                     |
| `allowDuplicateTeamNames` | Boolean. With `false`, new duplicate names are rejected, ignoring case | `true` |
| `enableCommandRewards` | Boolean                                      | `false`                           |
| `dropLootCrates`       | Boolean                                      | `false`                           |
| `crateNoDrop`          | Validated weight overrides by creature group | Passive 4000, monster 600, boss 0 |

Existing teams are preserved when duplicates are disabled. Disbanded names can be reused. Reload to apply the setting. Unknown configuration keys are rejected. Back up the world before changing progression or reward policy, then reload and check the server log.

## Trusted command rewards

`questplus:command` is disabled unless trusted world configuration explicitly enables it. Do not enable this for packs you have not reviewed. A browser session cannot authorize itself to execute console commands.

```json5
{
	type: "questplus:command",
	data: {
		command: "say Quest completed",
		permissionLevel: 0,
		silent: true,
	},
}
```

This is a reward fragment, not a complete quest. The command runs as the claiming player, with a permission level from 0 to 2. The default is 2 and output is suppressed by default. Command text is limited to 240 characters and cannot contain multiple lines.

Use the player's command context and selectors where appropriate. There is no interpolation of arbitrary player supplied text. Test command rewards in a disposable world before distributing them.

## Client preferences

`config/questplus-client.json` stores display mode, brightness, scale, animation preferences, HUD and notification placement. Use the tablet's settings screen for normal changes. It cannot grant progress or change server rewards.

See [Settings and accessibility](/guide/settings) for the controls and defaults that matter to players.

## Web connection

`<world>/questplus-hub.json` stores the optional Hub URL. A world file takes precedence over `config/questplus-hub.json`. Restart Minecraft after connection changes. Session credentials are generated automatically and must not be exposed in logs or screenshots.

[Host the web service](/reference/hosting) · [Backups and updates](/guide/backups-and-updates)
