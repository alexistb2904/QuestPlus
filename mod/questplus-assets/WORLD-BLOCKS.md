# QuestPlus world blocks

Visual contract: stained oak, worn iron, copper fasteners, dark slate and small
amethyst runes, sampled from the tablet's material language. No glass neon panels.
All geometry is baked Minecraft JSON; no per-frame geometry construction.

* Quest Board: wall mounted slate writing surface, thick timber frame and corner
  straps. Sizes are height × width: 2×3, 2×4, 3×4 and 4×6. Each tile is within its
  block bounds; only the lower-left tile owns an entity. Content sits in front of
  the slate, inside the frame.
* Submission Station: wooden cabinet, iron foot/rim, top hatch with copper handle,
  front insertion slot and a small recessed amethyst status strip.
* Quest Relay: low slate plinth, copper contacts and a cross-shaped amethyst rune.
  Its on/off baked models show the output state.

Editable sources are in `blockbench/world/`. These models were authored through
the running Blockbench MCP. Each Java-block model embeds the original 64×64 atlas,
containing twelve 16px material tiles. The four assembled board projects are
Generic Models for editing the complete silhouette. They are not oversized block
models in Minecraft.

Regenerate the runtime JSON and PNG files with
`python mod/questplus-assets/tools/export_world_blocks.py`. The exporter validates
the source geometry, atlas consistency, UV bounds and all 384 board states. It
does not require a running Blockbench instance. Runtime geometry and UVs were
compared with Blockbench's native Java exporter, ignoring zero-angle transforms.

## In-game use

Craft the blocks or find them in the QuestPlus creative tab. Place a board by
clicking a wall. Its default size is 2×3. Right-click to select its size and mode.
Growing a board requires a supported, clear, loaded area. Shrinking keeps the
master and its configuration. Breaking any part removes the whole board and
produces one board item in survival. Piston movement is disabled.

Right-click a station to select a consuming objective, personal or shared team
progress, automation, allowed world-facing input sides and optional next-tier
continuation. The quest and objective pickers are searchable. Players can also
enter IDs directly. The owner picker lists up to 64 online players and the UUID
field also supports an offline owner. Owner/operator permissions protect ownership
transfer. Team owners and managers can configure a station bound to their team.

Items enter through vanilla hoppers and Fabric `ItemStorage.SIDED`. Fluids use
`FluidStorage.SIDED`, with 81 Fabric droplets per mB. Buckets can be used on the
station without sneaking. Energy uses `EnergyStorage.SIDED` when Team Reborn Energy
is installed separately. The API is compile-only and is not included in the JAR.
Sneak-right-click opens settings while holding a resource. Invalid resources and
excess amounts are refused. Disable automation to recover buffered items with a
hopper, or use the return button. Fluid and energy capabilities allow extraction
on enabled sides. No fake player is created.

A survival drop packs a station's configuration and all three buffers. The item
contains a one-use relocation reference, not a copied resource balance. Placement
atomically clears the previous account and installs the destination account.
Copying the item cannot redeem its balance twice. Ownership and team binding stay
with the station. An interrupted removal retains the account for recovery at the
original location. Do not delete `world/data/questplus/blocks` during world moves.

Relays select quests or a category, personal/team progress, a condition, ANY/ALL,
and binary, four-tick pulse or analog output. For a stage condition, enter the
stage ID in the group/stage field. For a tier condition, the threshold controls
which reached tier maps to output 15. A pulse fires on either edge of a condition.

Boards display quest icons, titles, percentages and state, or rank, player heads,
names and scores. The contributions ranking counts resources submitted through
stations. Text and icon layouts are cached on the client. Static frame geometry
stays in baked chunk models. Data packets are sent only when board rows change.

## Authority and recovery

All mutation runs on the server thread. Station balances and detached player/team
progress commit in the same existing JSON transaction journal. Rejected engine
validation leaves the buffers intact. Reward entitlement follows the current team
reward policy. No reward ledger or account balance is sent to the web editor.

Offline owners are supported when the server can validate the selected objective.
Player-specific conditions, including custom stage providers, require the owner
online. Dimension, biome and position conditions use the station's location.
Leaving/disbanding a bound team, disabling sharing, or removing the selected quest
pauses the block. It never silently switches to another team or personal progress.

Loaded blocks use an invalidation queue with a bounded per-tick count and time
budget. Buffered stations retry at a controlled interval for cooldown/provider
changes. Board integrity checks are scheduled locally, including across chunks.

## Verification

`cd mod` then run `gradlew.bat build integrationTest` with Java 25. Server tests
cover buffers, transaction rollback/recovery, offline submissions, rewards,
hoppers, fluids, optional energy, stale capabilities, relocation replay,
permissions, team changes, all board sizes, analog output, stages and pulses.

Run `gradlew.bat runClientGameTest -PworldBlocksClientTest` for actual client item
placement, cached board rendering, settings mouse input, C2S apply, submission and
event-driven board/relay updates. Captures are written under
`build/run/clientGameTest/screenshots`. This client test uses one real client and
an integrated server. The team tests use two server-side player connections.
Two independent interactive clients and third-party pipe mods require a separate
compatibility run. Fabric item/fluid/energy API transfers are covered directly.
