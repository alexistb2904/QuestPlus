# Reward and player action contract

The existing generation/sequence snapshot/delta protocol remains in place. Definitions now carry separate `rewardPreview` and `tierRewardPreviews` arrays; the internal `Reward.data` field is still stripped. Presentation only copies explicitly allowed item/XP/advancement/message/weight/translation fields. Arbitrary extension payloads and commands never cross this boundary.

Per-quest personal progress may contain `rewardState: {token,status,rewards}`. The token identifies one server-owned grant without exposing its persistence key. The view selects the earliest pending nonempty grant or a completed receipt; empty outcomes are visible without requiring a meaningless claim. `claimable` applies to that selected grant. `claimableAny` separately enables Collect All when another resolved cycle is ready. `saving` disables actions during durable writes.

`ChooseReward(quest, UUID token, slot, option)` is a bounded binary serverbound payload. The server derives the actor from the connection, shares the ordinary eight-requests-per-second budget, requires a visible personal unclaimed grant, validates the unchanged slot as a choice, resolves only a supported option and persists it while locking the affected group. Converting that slot to a concrete reward makes a retry invalid. A token from another cycle cannot address a new grant. The save callback invalidates projections; failures freeze progress and never authorize delivery. The legacy choose command remains supported by the same implementation.

The ordinary Action packet keeps claim/claim_all/restart/submit/team actions. `interact` carries a quest/objective selection; the server looks up the declared manual objective and supplies its target and scoped context itself. Legacy `trigger` and the trigger command use the same handler. Only trusted server Java events may intentionally omit manual scoping. This adds no administrative command path.

Reward randomness is resolved by the engine at completion, before entitlements are stored, and is never resolved by the client or claim path. Explicit choice is persisted before reservation. `reserved` prevents automatic retries after a potentially partial delivery; `delivered` retains the compact anti-replay key and only the latest concrete receipt per quest. Empty outcomes are also bounded to one unreserved receipt per quest. Pending nonempty rewards and reserved entries are never pruned.

Studio named tables are portable editing snapshots in `queststudio-compatibility.json`. Inserting one copies its supported flat pool inline into the quest. Changes to a saved table do not rewrite inserted copies. Because live references and nested pools are not accepted, authored cycles cannot execute. Adding live references later requires a separately versioned registry/validation contract.

## Remaining transaction boundary

The vanilla player inventory and Quest Plus JSON do not share an atomic commit. Consuming submissions need a journal coordinated with vanilla player saving. A reserved claim interrupted after reservation requires audited operator recovery, not automatic re-delivery. The current safeguards prove replay refusal, not universal exactly-once delivery under an abrupt process or disk failure. See [P0 backlog](CODEX_TODO_P0_P2.md).
