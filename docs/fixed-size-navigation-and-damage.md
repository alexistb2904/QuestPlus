# QuestPlus tree camera: fixed-size artwork, pointer-centered zoom

Quest Studio and the Minecraft tablet use **the same authored world coordinates**.
The wheel zooms the *distance between anchor points*, never the size of quest icons,
annotations, or embedded images. For example, a quest icon authored at 32 × 32 GUI
pixels remains exactly 32 × 32 GUI pixels at 50%, 100%, or 250% tree zoom.

- **Wheel:** zoom the tree around the cursor (35–250% in Quest Studio; 25–250% in Minecraft).
- **Shift + wheel / horizontal trackpad (Studio):** pan horizontally, retaining zoom.
- **Pinch gesture (Studio):** zoom around the gesture midpoint.
- **Drag the empty canvas:** pan in any direction, at every zoom.
- **Center tree / Home:** recenter the layout without changing the current zoom.
- **Reset zoom / 100% (Studio):** restore default zoom without moving the view center.
- **+ / − (Minecraft):** zoom around the tree viewport center.

The unavoidable geometry: actual zoom changes **distances between anchors**. A 200-pixel
world-space separation appears 100 pixels apart at 50% and 400 pixels apart at
200%. This cannot remain visually fixed *and* provide a genuine zoom, so the editor
and game keep pixel dimensions fixed while scaling only node/annotation positions.
The exported x/y, width/height, and image references never change.

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
