# Fixed-size QuestPlus tree navigation

Quest Studio and the Minecraft tablet deliberately use a **1:1 world-coordinate to
logical GUI-pixel scale**. Neither drawing size nor distance between authored
coordinates can be changed by mouse-wheel or pinch gestures.

- **Wheel / trackpad:** pan vertically.
- **Shift + wheel / horizontal trackpad:** pan horizontally in Quest Studio.
- **Two-finger gesture:** move viewport midpoint; pinch does not scale content.
- **Drag the empty canvas:** pan in any direction.
- **Center tree / Home:** recenter the authored layout without fitting it to the
  viewport or changing its scale.
- **Arrows:** pan the Minecraft viewport; +/− no longer zoom.

A large diagram extends beyond the viewport. Different window or GUI scales may
change the physical size of Minecraft GUI pixels, but the **logical GUI-pixel dimensions
and the distances among anchors stay constant**. Device DPI and GUI scale apply to
the whole Minecraft UI; QuestPlus does not apply any additional scale from the
tree camera.

## Closing the tablet on damage

New local setting: `stayOpenOnDamage` (**false by default**). A client-side
health **or absorption** decrease while the GUI is open or the opening animation
is active closes the tablet. With player animations enabled, the current opening
pose is interrupted and the closing animation is requested. Otherwise the
standard legacy close behavior runs.

`stayOpenOnDamage=true` disables this automatic closure. The option is saved in
`config/questplus-client.json` and translated in en_us, fr_fr, es_es, zh_cn.
Existing settings files default to `false`.

Damage detection is local and observes the player's current health and absorption
between ticks; it does not depend on network packet hooks. Severe damage and death
may interrupt the cosmetic close animation, but the tablet must not remain stuck.
