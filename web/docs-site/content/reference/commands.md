---
title: "Commands"
description: "All /questplus commands for players, teams and operators, including quest grant and reset, recovery and FTB Quests progress migration."
---

# Commands

Use namespaced quest IDs such as `questplus:survival/logs`. **OP** commands require operator permission.

`<quest>` is a quest ID, and `<objective>` is its local objective ID. Team commands use UUIDs for player and team arguments. Use the tablet's team controls or the UUIDs shown by server diagnostics when you do not have those identifiers.

## Players

| Command                                 | Action                      |
| --------------------------------------- | --------------------------- |
| `/questplus`                            | Help                        |
| `/questplus progress [quest]`           | View progress               |
| `/questplus submit <quest> <objective>` | Submit an objective         |
| `/questplus trigger <quest>`            | Activate a manual objective |
| `/questplus choose <quest> <option>`    | Choose a reward             |
| `/questplus claim <quest>`              | Claim rewards               |
| `/questplus restart <quest>`            | Restart an eligible quest   |

## Teams

| Command                             | Action             |
| ----------------------------------- | ------------------ |
| `/questplus team create <name>`     | Create a team      |
| `/questplus team invite <player>`   | Invite a player    |
| `/questplus team join <team>`       | Join               |
| `/questplus team decline <team>`    | Decline            |
| `/questplus team leave`             | Leave              |
| `/questplus team kick <player>`     | Remove a player    |
| `/questplus team promote <player>`  | Owner: make a member a manager |
| `/questplus team demote <player>`   | Owner: make a manager a member |
| `/questplus team revoke <player>`   | Manager/owner: cancel an invitation |
| `/questplus team rename <name>`     | Manager/owner: rename the team |
| `/questplus team sharing <true\|false>` | Manager/owner: enable or disable collective quest progress |
| `/questplus team transfer <player>` | Transfer ownership |
| `/questplus team disband`           | Disband            |
| `/questplus team info`              | Show membership    |
| `/questplus team invitations`       | List invitations   |

## Operators

| Command                                   | Action                                          |
| ----------------------------------------- | ----------------------------------------------- |
| `/questplus reload`                       | Reload definitions                              |
| `/questplus editor`                       | Start online editing                            |
| `/questplus editor world`                 | Edit the current world's definitions            |
| `/questplus editor default`               | Edit the default pack for new worlds            |
| `/questplus editor approve <code>`        | Approve browser access                          |
| `/questplus editor revoke`                | Revoke access                                   |
| `/questplus rollback`                     | Restore previous definitions                    |
| `/questplus import <pack>`                | Preview a shared pack                           |
| `/questplus import confirm`               | Confirm import                                  |
| `/questplus retrieve`                     | Preview replacement with the default pack       |
| `/questplus retrieve confirm`             | Confirm replacement of this world's definitions |
| `/questplus debug [player]`               | Runtime diagnostics                             |
| `/questplus diagnose <quest>`             | Inspect quest conditions                        |
| `/questplus quest grant <player> <quest>` | Grant completion                                |
| `/questplus quest reset <player> <quest>` | Reset player progress                           |

## Recovery and migration (OP)

| Command                                                           | Action              |
| ----------------------------------------------------------------- | ------------------- |
| `/questplus recovery inspect <player>`                            | Inspect claims      |
| `/questplus recovery resolve <player> <claim> <outcome> <reason>` | Resolve a claim     |
| `/questplus migrate-ftb preview <source> <player>`                | Preview a migration |
| `/questplus migrate-ftb confirm`                                  | Apply migration     |
| `/questplus migrate-ftb rollback <operation>`                     | Roll back safely    |

Player, team and operation identifiers use UUIDs where required.

Progress reset and recovery resolution are operator tools with lasting effects. Back up the world and inspect the current state first. A definition rollback does not undo reward delivery. See [Troubleshooting](/guide/troubleshooting) before resolving a claim.

[Connect Quest Studio](/guide/import-and-publish)
