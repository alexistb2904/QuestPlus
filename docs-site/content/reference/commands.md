# Commands

Use namespaced quest IDs such as `questplus:survival/logs`. **OP** commands require operator permission.

## Players

| Command | Action |
| --- | --- |
| `/questplus` | Help |
| `/questplus progress [quest]` | View progress |
| `/questplus submit <quest> <objective>` | Submit an objective |
| `/questplus trigger <quest>` | Activate a manual objective |
| `/questplus choose <quest> <option>` | Choose a reward |
| `/questplus claim <quest>` | Claim rewards |
| `/questplus restart <quest>` | Restart an eligible quest |

## Teams

| Command | Action |
| --- | --- |
| `/questplus team create <name>` | Create a team |
| `/questplus team invite <player>` | Invite a player |
| `/questplus team join <team>` | Join |
| `/questplus team decline <team>` | Decline |
| `/questplus team leave` | Leave |
| `/questplus team kick <player>` | Remove a player |
| `/questplus team transfer <player>` | Transfer ownership |
| `/questplus team disband` | Disband |
| `/questplus team info` | Show membership |
| `/questplus team invitations` | List invitations |

## Operators

| Command | Action |
| --- | --- |
| `/questplus reload` | Reload definitions |
| `/questplus editor` | Start online editing |
| `/questplus editor approve <code>` | Approve browser access |
| `/questplus editor revoke` | Revoke access |
| `/questplus rollback` | Restore previous definitions |
| `/questplus import <pack>` | Preview a shared pack |
| `/questplus import confirm` | Confirm import |
| `/questplus debug [player]` | Runtime diagnostics |
| `/questplus diagnose <quest>` | Inspect quest conditions |
| `/questplus quest grant <player> <quest>` | Grant completion |
| `/questplus quest reset <player> <quest>` | Reset player progress |

## Recovery and migration (OP)

| Command | Action |
| --- | --- |
| `/questplus recovery inspect <player>` | Inspect claims |
| `/questplus recovery resolve <player> <claim> <outcome> <reason>` | Resolve a claim |
| `/questplus migrate-ftb preview <source> <player>` | Preview a migration |
| `/questplus migrate-ftb confirm` | Apply migration |
| `/questplus migrate-ftb rollback <operation>` | Roll back safely |

Player, team and operation identifiers use UUIDs where required.

[Connect Quest Studio](/guide/import-and-publish)
