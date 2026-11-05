# Quest Plus: Fluid and energy quest integration (experimental)

Quest Plus now accepts two **trusted server-side event objectives**, distinct from automatic vanilla detection. They are designed for modpack authors and mod developers integrating external fluid/energy systems.

| Event | Target | Unit | Reporter |
| --- | --- | --- | --- |
| `fluid_transfer` | Fluid registry ID, e.g. `minecraft:water` | millibuckets (mB) | Trusted Java mod |
| `energy_transfer` | Fixed marker `questplus:fe` | Forge Energy-compatible unit (FE) | Trusted Java mod |

## Usage from another Fabric mod

Call these methods **on the dedicated server thread, after a successful transfer**, with the server player responsible for the action:

```java
// The transfer succeeded; 1 bucket = 1000 mB.
QuestPlusApi.reportFluidTransferred(player, "minecraft:water", 1000);

// The transfer succeeded; 8000 FE were moved.
QuestPlusApi.reportEnergyTransferred(player, 8000);
```

Quest Plus validates that the reported transfer is positive and at most 1,000,000,000 units. It evaluates progress on the server and uses normal quest conditions, player/team progress and persistence. A report is **not** a request to perform the transfer and does not consume tanks or storage.

### Important limitation

This is **not automatic compatibility** with arbitrary mods such as Create, Applied Energistics, or specific FE/cable systems. A compatible mod/addon must call the API from its actual successful transfer hook. Do not report client-side intents or capacity checks: those could credit transfers that never happened. Integration authors must provide any additional anti-loop / farming rules their pack requires.

Quest Studio's Mod Registry Browser lists available fluid IDs from the approved server snapshot. Energy uses a fixed marker to prevent ID ambiguity. The templates named *Fluid transfer (integration)* and *Energy transfer (integration)* are only completable when a server-side integration reports those events.

The per-objective `count` is the target number of mB/FE transferred. Do not confuse fluid amounts with item/bucket counts. Player data remains on the existing server-authoritative persistence path.
