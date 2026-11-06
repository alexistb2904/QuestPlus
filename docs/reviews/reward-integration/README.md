# Reward integration review — 5 November 2026

Branch: `feat/p0-p2-player-integration`, based on main `1d6d238`. Original IDE checkout and its uncommitted fixes were preserved in `QuestPlus`; implementation used a sibling worktree.

## Evidence

- Java 25: `gradlew.bat build integrationTest` passed. 161 JUnit tests, six required Fabric server GameTests and shutdown verification of 140 UUID saves.
- `gradlew.bat runClientGameTest` passed with FP PAL and PAL. The reward fixture opens the physical first-person tablet, clicks a custom manual objective, selects two reward slots, waits for persisted acknowledgment, claims exactly 19 XP and rejects a replayed binary choice packet. Existing UI/animation/viewport tests also run.
- Studio: `npm ci`, `npm run typecheck`, `npm test` (129), `npm run build` passed. The shared JSON5 export fixture also loads in Java `QuestRegistry`.
- API: `npm ci`, `npm test` (29) passed, including JSON5 acceptance, malformed JSON5 rejection, strict JSON configuration, authorization/publication and malicious paths. `cmd /c scripts\_qa.bat api-test` passed.
- Browser: created a choice and loot pool, edited/reordered options, selected preview cards, saved a named snapshot, simulated three draws with an empty weight, switched EN/FR/ES/ZH and inspected desktop plus 390×844 layouts. Existing remote Minecraft texture URLs sometimes return 404; fallback rendering remains visible. The Vite build reports the existing large JS chunk warning.
- Independent review found grant-key confusion in Collect All and growth of delivered/empty receipts. All were corrected with regression coverage. The final review is recorded in the PR description.

## Captures

[Physical tablet: choice](tablet-choice.png) · [server result](tablet-result.png) · [already claimed](tablet-claimed.png)

[Studio choice](studio-choice.png) · [French mobile layout](studio-mobile-fr.png)

## Limits

The server GameTests use simulated players. The native client uses a real integrated server and connection. This is not a dedicated server with two real clients, not a modded fluid/energy compatibility matrix, not an Iris/shader certification and not a proof of atomicity between vanilla inventories and JSON under abrupt crashes. Unit recovery tests exercise the JSON transaction journal, not that missing cross-storage transaction.

The PR stays draft while inventory hand-in crash atomicity and audited reserved-claim recovery remain open. The [backlog](../../CODEX_TODO_P0_P2.md) and [feature status](../../ftb-quests-gap-analysis.md) distinguish implemented behavior from remaining work.
