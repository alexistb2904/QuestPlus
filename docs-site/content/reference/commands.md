# Command reference

This table follows the command registration in `server/src/main/java/dev/questplus/fabric/QuestCommands.java` on `main`. `<quest>` is a namespaced quest ID. `<player>`, `<team>`, `<source>` and `<operation>` are UUIDs.

**Operator** means Minecraft's `COMMANDS_GAMEMASTER` permission. Commands that require a player cannot run from the server console unless they explicitly accept a player UUID.

## Player commands

| Command | Purpose |
| --- | --- |
| `/questplus` | Show help |
| `/questplus progress` | Show up to 50 visible quests with progress |
| `/questplus progress <quest>` | Inspect one visible quest and its cycles |
| `/questplus submit <quest> <objective>` | Submit a supported objective |
| `/questplus choose <quest> <option>` | Choose a reward option, 1 to 32 |
| `/questplus claim <quest>` | Claim pending eligible rewards |
| `/questplus restart <quest>` | Restart an eligible repeatable quest |
| `/questplus trigger <quest>` | Trigger a manual objective |
| `/questplus team create <name>` | Create a team |
| `/questplus team invite <player>` | Invite a player UUID |
| `/questplus team join <team>` | Join with an invitation |
| `/questplus team decline <team>` | Decline an invitation |
| `/questplus team leave` | Leave the team |
| `/questplus team kick <player>` | Remove a member, if authorized |
| `/questplus team transfer <player>` | Transfer ownership, if authorized |
| `/questplus team disband` | Disband the team, if authorized |
| `/questplus team invitations` | List invitations |
| `/questplus team info` | Inspect membership |

## Operator commands

| Command | Purpose |
| --- | --- |
| `/questplus reload` | Validate and reload world definitions |
| `/questplus editor` | Create a browser authorization session |
| `/questplus editor approve <code>` | Approve a matching browser code |
| `/questplus editor revoke` | Revoke browser access |
| `/questplus rollback` | Restore previous published definitions |
| `/questplus import <pack>` | Preview a shared pack ID |
| `/questplus import confirm` | Apply the pending import |
| `/questplus debug` | Show runtime summary |
| `/questplus debug <player>` | Inspect stored player progress |
| `/questplus diagnose <quest>` | Show objective counters and conditions |
| `/questplus quest grant <player> <quest>` | Grant completion to a player |
| `/questplus quest reset <player> <quest>` | Reset a player's quest |
| `/questplus recovery inspect <player>` | Inspect reserved claims |
| `/questplus recovery resolve <player> <claim> <outcome> <reason>` | Resolve a reserved claim after audit |
| `/questplus migrate-ftb preview <source> <player>` | Preview private progress migration |
| `/questplus migrate-ftb confirm` | Confirm reviewed migration |
| `/questplus migrate-ftb rollback <operation>` | Roll back a migration if safe |

### Notes

- `<quest>` is a **greedy string** in most one-quest commands, so it consumes the rest of the command line.
- `submit` only lists eligible submission objectives. It does not make every objective manually completable.
- Operator recovery is for ambiguous reserved claims, not an automatic refund or reward replay tool.
- The command tree does **not** provide a general-purpose in-game editor for arbitrary JSON files.
- Team actions enforce ownership and membership rules even though the commands themselves are visible to players.

The graphical tablet is the preferred player interface. Use commands for automation, administration and debugging.
