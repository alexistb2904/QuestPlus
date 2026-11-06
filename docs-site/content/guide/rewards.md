# Rewards and progression

## Add rewards

Select a quest, open its **Rewards** panel and choose a reward type. Current built-in authoring includes:

| Type | Notes |
| --- | --- |
| Item | Item ID, count and optional components |
| XP points and levels | Independent reward types |
| Advancement | Optional criterion |
| Message and toast | Player-facing feedback |
| Stage | Grants or removes a stage |
| Weighted random | Server draws from weighted options |
| Loot chance | Weighted outcomes with an optional empty result |
| All table | Grants configured supported outcomes |
| Player choice | Player selects an offered reward |
| Named table reference | Reuses a stored project table |
| Trusted command | Administrator opt-in, disabled by default |
| Extension reward | Requires installed, registered server extension |

Rewards are resolved on the server. Random outcomes are fixed before delivery rather than rerolled each time a player reconnects. A choice has to be made and saved before the actual claim.

## Reward tables

The named table library can be shared between quests. Author a table in Quest Studio, then insert a copy or a live reference. Named tables are retained in the compatibility metadata and resolved by the server's registry.

Nested or circular tables are rejected. Preview and simulation are authoring aids, not a guarantee of gameplay state.

## Repeatable progression

| Mode | Meaning |
| --- | --- |
| `once` | A single completion |
| `repeatable` | Manual restart when eligible |
| `infinite` | Begins another cycle automatically |
| `tiered` | Milestones with separate thresholds and rewards |

Optional cooldowns, time limits and no-death requirements can change eligibility. Use `/questplus restart <quest>` only on quests that permit it.

## Reward claim policies

Quest Studio exposes options such as automatic claims, team reward behavior, disabling a claim toast and excluding a reward from Collect All. Validate these policies on the server, particularly after changing team composition.

## Command rewards and security

Command rewards are **disabled by default**. They run with gamemaster-level privileges and can alter the world. Enable them only for trusted, locally reviewed quest packs.

```json
{
  "schemaVersion": 1,
  "autosaveSeconds": 5,
  "enableCommandRewards": true
}
```

A command reward is not a general-purpose script engine. It has no arbitrary variable substitution or permission level above the gamemaster role. Connected Hub publishing refuses command rewards and is not a remote console.

See the [command reward security notes](https://github.com/alexistb2904/QuestPlus/blob/main/docs/command-rewards.md).
