# QuestPlus world blocks

Visual contract: stained oak, worn iron, copper fasteners, dark slate and small
amethyst runes, sampled from the tablet's material language. No glass neon panels.
All geometry is baked Minecraft JSON; no per-frame geometry construction.

* Quest Board: wall mounted slate writing surface, thick timber frame and corner
  straps. Place individual modules to build rectangles, strips, columns or irregular
  outlines, up to 256 connected modules. Sixteen border combinations join in all four
  horizontal orientations. Each module retains its own settings; connected modules
  belonging to the same owner share configuration. Text stays inside filled rectangles.
  Legacy fixed-size boards remain compatible.
* Submission Station: wooden cabinet, iron foot/rim, top hatch with copper handle,
  front insertion slot and a copper-framed slate screen showing the linked quest,
  its percentage and an amethyst progress bar. Server updates drive the display.
* Quest Relay: low wooden cabinet, iron corner caps, slate lid inset, recessed
  orange front lens and side redstone traces. Its on/off baked models show the
  output state. Its nine-pixel collision height follows the cabinet.

Editable sources are in `blockbench/world/`. Every runtime block model has its own
`.bbmodel`; no script generates or modifies geometry. The board and station embed
the original 64×64 material atlas (`textures/block/questplus_utility.png`); the relay
has its own 64×64 pixel atlas for the cabinet and signal states. The four assembled
board projects (`quest_board_2x3` … `quest_board_4x6`) are Generic Models for
viewing the complete silhouette only. They are not exported.

To change a model, open its `.bbmodel` in Blockbench, edit it, save it, then use
**File › Export › Export Block/Item Model** to overwrite the matching file in
`mod/client/src/main/resources/assets/questplus/models/block/`. If you paint the
atlas, also save the texture over the PNG in `textures/block/`. Commit the
`.bbmodel` and the exported JSON/PNG together.

* Legacy fixed boards: `quest_board_c`, `_n`, `_s`, `_e`, `_w`, `_ne`, `_nw`, `_se`, `_sw`.
* Modular boards: `quest_board_module_0` … `quest_board_module_15`. The number is the
  set of framed borders: north = 1, south = 2, west = 4, east = 8 (`15` is framed on
  all sides, `0` has no frame). The slate stops at the inner edge of every frame;
  letting it run under the frame makes the faces z-fight on the board's outer edge.
  A change to a frame piece must be made in each module that contains that border.

Blockstates (`blockstates/quest_board.json`: 384 legacy rules, 64 modular rules) and
item files are plain JSON with no geometry; edit them directly when needed.

`quest_board_module_15.bbmodel` also stores the board item's display transforms;
`models/item/quest_board.json` copies them. Dropped items use quarter scale; both
third-person hands use 0.4 scale with the wall-offset depth recentered around the grip.

## In-game use

Craft the blocks or find them in the QuestPlus creative tab. Place a board by
clicking a wall. Each click consumes one module; place neighbors to extend the
surface in either direction. Hold another board module to build directly against
an existing module. Every module requires wall support. Breaking a module returns
one item in survival and leaves the neighbors and their settings intact. Legacy
fixed-size boards still break as a whole and return exactly one item. Piston
movement is disabled.

Right-click to open the copper-and-timber configuration screen. Choose personal
or shared team progress, pinned/automatic/mixed display, category filtering and
whether completed quests appear. Add up to 32 pinned quests, reorder with the arrow
buttons, remove individual entries or clear the list, then Apply. The leaderboard
tab selects player, team or contribution ranking. Larger wall boards also show
the leaderboard alongside their quest rows. Small screens scroll vertically.

Right-click a station to select a consuming objective, personal or shared team
progress, automation, allowed world-facing input sides and optional next-tier
continuation. Its dedicated screen separates the linked quest/objective, exact
objective progress, stored resources and highlighted input sides. The searchable
quest picker lists all visible quests and selects the first compatible deposit
objective when one exists. Quests without a deposit objective remain browsable,
with an explicit explanation and Apply disabled; possession and gameplay objectives
keep their original behavior. Ownership settings list up to 64 online players; their UUID
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
and binary, four-tick pulse or analog output. The two target modes switch inline:
choose individual quests through a category-first catalogue, or select one entire
category and preview its monitored quests without individual removals. The screen
uses the available window area, clips its scrolling body and keeps Apply/Close
fixed. For a stage condition, enter the stage ID in Advanced. For a tier condition, the threshold controls
which reached tier maps to output 15. A pulse fires on either edge of a condition.

Item display transforms live in the editable Blockbench projects. Dropped utility
items use a quarter-size model, with separate first-person and third-person poses
for both hands. Blockbench's model export writes them into the item model.

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

Run `gradlew.bat runClientGameTest -PrelayClientTest` for relay target-mode separation,
category previews, live snapshots, C2S Apply, abandoned drafts, tier input, compact
control bounds and overlap checks, the two rendered model states and held/dropped
item views. This scenario uses one real client and an integrated server.

Run `gradlew.bat runClientGameTest -PboardClientTest` for module-by-module 4×2
placement, the dedicated board screen, offline-owner input, pinned order/C2S Apply,
leaderboard, long titles/player names, night readability, small-screen scrolling
and close/reopen persistence, plus front/back main-hand, off-hand and ground-item
presentation beside a vanilla stone item. Board lettering uses full-bright light with a shadow,
and player heads face outward with a 180-degree rotation. It also verifies
that live snapshots leave an open quest picker in place. Server board tests cover
9×3 boards in all four orientations, irregular outlines after removing a middle
piece, the 256-module limit, ownership separation, transfer validation, legacy
single-item drops and the expanded snapshot codec. Asset tests cover all 16 borders.

Verified on 2026-11-08: Java build and 353 unit tests passed, 36 dedicated-server
GameTests passed, and the rendered client scenario passed with searchable quest
and objective selection, C2S settings, completion and compact-screen scrolling.
Quest Studio passed 165 tests, typecheck and production build. The web API passed
50 tests with one existing skipped test. Native Blockbench checks passed for the
block assets. The station export matches Blockbench's native geometry and UVs.
