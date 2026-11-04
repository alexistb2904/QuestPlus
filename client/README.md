# Client — Minecraft presentation

This folder contains Minecraft **client-only** code and resources: the
native animated tablet, tree diagram, quest information, HUD, notifications,
camera, localization, mixins and render assets.

- Java: `src/main/java/dev/questplus/fabric/client/` and client models.
- Assets: `src/main/resources/assets/questplus/`.
- Client mixins: `src/main/resources/questplus.client.mixins.json`.
- Unit and Fabric client GameTests: `src/test/` and `src/gametest/`.

The client **does not** authorize website users or own server quest state.
It renders the projections delivered by the existing `QuestNetwork` protocol.
The server validates new quests and invalidates client state after publishing.

This folder is **not a separate Minecraft mod**. The Gradle main source
set combines this folder with `../server/` to build one Fabric JAR, with
the original client entrypoint retained in `server/src/main/resources/fabric.mod.json`.

Do not add persistent player data, remote admin credentials or a dedicated
HTTP listener to the client.

## Profil de test shaders + animations (Fabric 26.3)

`1-Lancer-Client.bat` installe dans **run/client uniquement** les mods
Iris + Sodium, Punchy! + Punchy! Moves, le pack Fluid & Foolish et trois
shaders (MakeUp Ultra Fast / Complementary Reimagined / Bliss). Les versions
proviennent de Modrinth et sont verifiees SHA-512. Ce n'est pas une
nouvelle dependance du JAR QuestPlus ; le serveur dedie reste vanilla
pour ces packs graphiques.

Activez un shader dans Iris et Fluid & Foolish dans les resource packs.
Les animations Punchy! sont a tester en combinaison avec la tablette
1ere personne existante : des interactions avec FP PAL sont possibles.
Option 25 du menu : actualisation manuelle des archives.
Pour sauter la preparation des packs, definir
`QUESTPLUS_SKIP_TEST_ASSETS=1` avant de lancer le client.

### Compatibilite avec Iris / Complementary / Bliss / MakeUp

QuestPlus detecte facultativement Iris via son API publique : lorsque les
shaders sont activés, le mode **Écran compatible shaders (Iris)** rend l'écran
en matériau mat avec lumière contrôlée et évite d'appliquer une seconde passe
de flou globale. Le réglage est activé par défaut et désactivable dans
l'onglet Paramètres de la tablette. L'affichage Vanilla reste inchangé.

**Punchy! 3.1+** expose `PunchyApi.disable`, que QuestPlus utilise pendant
son animation FP PAL uniquement. Punchy reste actif en dehors de la tablette.
Le profil de test exige au minimum cette version afin d'éviter le conflit
des bras/animations avec des versions plus anciennes.

Ce mode ne supprime **pas** le DOF/les réflexions générés volontairement
par un shader pack après sa composition : pour tester l'effet réel avec
Complementary, désactiver temporairement le DOF dans les options du shader.
Le [guide de test shaders/Punchy](../docs/shader-punchy-testing.md) détaille
les trois packs et la matrice de validation.
