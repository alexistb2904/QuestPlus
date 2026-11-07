# Play in a team

A team shares quest progression. The server owns membership, invitations and progress. You can manage your team in the tablet or use commands in chat.

![The team tab in a freshly created Minecraft demonstration world](/media/team.webp)

_The Team tab shows the current group. This capture uses an integrated server with one demonstration member._

## Create and invite

Open **Team** in the tablet or run the following commands. Replace `<player-uuid>` with the invited player's UUID.

```text
/questplus team create Explorers
/questplus team invite <player-uuid>
/questplus team info
```

The invited player can inspect invitations with `/questplus team invitations`, then join with `/questplus team join <team>`. Use the team UUID returned by the game. Invitations expire and cannot be reused.

You can belong to one team at a time. Only its owner can invite, kick, transfer ownership or disband it.

## Understand shared progress

Joining merges compatible progress into the team. Completed quests are combined. Compatible partial counters use the higher value instead of adding two copies of past activity.

New gameplay from members contributes to shared progression. Members who reconnect receive the current server state. A merge can be rejected when two versions of a quest have incompatible mechanics. Ask an operator to inspect the definitions instead of deleting a save.

## Understand rewards

Quest completion and reward payment are different states. A pack can configure team reward policies. Sharing a completed quest does not mean every member receives the same reward again.

Claim history stays attached to its beneficiary. Leaving and joining teams is not a way to reset paid rewards. If a reward is unavailable, inspect the quest and the pack's reward settings before asking an operator to reset anything.

## Leave or change ownership

Use `/questplus team leave` to leave as a member. The server keeps an independent personal snapshot of shared progress. The team also retains its progress.

The owner must transfer ownership with `/questplus team transfer <player-uuid>` or disband with `/questplus team disband` before leaving. Disbanding separates all members, including those currently offline.

[Team commands](/reference/commands#teams) · [Rewards and progression](/guide/rewards)
