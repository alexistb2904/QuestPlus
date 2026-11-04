# QuestPlus — Independent notification position

**Tablet → Settings** now has two separate position selectors:

- **HUD position** (`hudPosition`, existing default index 2 = top right).
- **Notification position** (`notificationPosition`, default index 1 = top centre).

Both cycle independently through nine positions in row-major order:

| 0 Top left | 1 Top centre | 2 Top right |
|---|---|---|
| 3 Middle left | 4 Middle centre | 5 Middle right |
| 6 Bottom left | 7 Bottom centre | 8 Bottom right |

The choice is stored locally in `config/questplus-client.json` and does not
alter server quests, pins, or the other position.

Older config files without `notificationPosition` are migrated in memory to
**1 (top centre)**; nothing visually changes until the user selects a position.
Invalid positions outside 0–8 are rejected by settings validation.

Notifications use logical GUI pixels and retain QuestPlus's responsive display
scale. Top-row toasts slide down; bottom-row toasts slide up; middle left/right
toasts slide inward. Bottom-center notifications sit above the hotbar.
Reduced-motion mode disables the entrance/exit animations.
