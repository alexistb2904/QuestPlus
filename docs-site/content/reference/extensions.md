# Integrate another mod

Quest Plus accepts trusted **server-side Fabric integrations**.

| What you need | API |
| --- | --- |
| Track a custom action | `registerEvent` + `submit` |
| Validate objective options | `registerObjectiveData` |
| Add a condition | `registerCondition` |
| Add a reward | `registerReward` |
| Show custom fields in Studio | `registerEditorDescriptor` |
| Bridge stages | `registerStageProvider` |
| Count fluid or FE transfer | `reportFluidTransferred` or `reportEnergyTransferred` |

[**Copy Java examples →**](/reference/java-api)

## Mod Registry Browser

In Quest Studio, use **Mod Registry Browser** to select IDs from a connected server or imported registry snapshot. A snapshot helps authoring but cannot install mods or create gameplay handlers.

Need HTTP requests instead? See [Quest Hub API](/reference/web-api).
