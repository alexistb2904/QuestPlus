# Bundled animation libraries

QuestPlus embeds the following upstream JARs without modifying them:

| Component | Version | Authors | License / notice |
| --- | --- | --- | --- |
| [Player Animation Library](https://github.com/PlayerAnimationLibrary/PlayerAnimationLibrary) | 1.2.8+mc.26.3 | ZigyTheBird, dima_dencep | MIT, `licenses/player-animation-library.txt` |
| [First Person PAL Expansion](https://github.com/RazorPlay01/First-Person-PAL-Expansion) | 1.1.1 | RazorPlay01 | MIT, `licenses/first-person-pal-expansion.txt` |
| [MochaFloats](https://github.com/PlayerAnimationLibrary/mochafloats) (lexer, parser, runtime, inside PAL) | 6.1.0 | Unnamed Team, PAL contributors | MIT, `licenses/mochafloats.txt` |
| [CommonUtils](https://github.com/RedlanceMinecraft/CommonUtils) (services, inside PAL) | 1.3.10 | RedlanceMinecraft (dima_dencep) | All Rights Reserved, `licenses/common-utils.txt` |

PAL includes code based on [GeckoLib 4.8.4, commit 2d7dc6e](https://github.com/bernie-g/geckolib/tree/2d7dc6e), under MIT (`licenses/geckolib.txt`).
FP PAL credits its arm rendering to AzureLib's `AzItemArmRenderUtil`, Copyright (c) 2024 AzureDoom, under MIT (`licenses/azurelib.txt`).
The FP PAL license also retains the upstream template authors' notices, those notices are reproduced verbatim.

## Public release prerequisite

The PAL and FP PAL projects declare MIT, but CommonUtils' upstream repository declares All Rights Reserved.
No separate redistribution permission for the nested `services` module was found.
Retaining that notice does not grant redistribution rights. Confirm permission with the rights holder, or use an upstream release with clarified licensing, before publishing the bundled QuestPlus JAR on Modrinth or CurseForge.

Once cleared, declare PAL and FP PAL as `embedded` dependencies on Modrinth and `embeddedLibrary` relations on CurseForge, Fabric API remains a required external dependency.
The nested mod IDs and metadata are preserved for Fabric Loader's normal dependency resolution.

## License sources

The texts in `META-INF/licenses/` are copied from these upstream revisions:

- PAL: https://github.com/PlayerAnimationLibrary/PlayerAnimationLibrary/blob/2eb78330ab5de76fc83d1e31c7c3cedb7e1e96b5/LICENSE
- FP PAL: https://github.com/RazorPlay01/First-Person-PAL-Expansion/blob/e484e88e5f3cbe6a01145b4d42a240a1f53f1263/LICENSE
- AzureLib: https://github.com/AzureDoom/AzureLib/blob/dacfc3c31ed3194d73ed0117bc6db13896149838/LICENSE
- MochaFloats: https://github.com/PlayerAnimationLibrary/mochafloats/blob/a19936be6d47df27499606d9bab3943b5d550ef4/license.txt
- CommonUtils: https://github.com/RedlanceMinecraft/CommonUtils/blob/bc9396d9ddd7bc07bf27ca43682346185befb90d/LICENSE
- GeckoLib: https://github.com/bernie-g/geckolib/blob/2d7dc6e/LICENSE
