# Compiler et vérifier

Outils du dépôt : **Java 25, Gradle 9.7.1, Loom 1.18.4**, Minecraft 26.3, Fabric Loader 0.20.1 et Fabric API 0.165.2+26.3. Le wrapper Gradle est inclus. Node.js 22 sert à l'éditeur et au service web.

```powershell
$env:JAVA_HOME = & .\scripts\Find-Java25.ps1
.\gradlew.bat build integrationTest
cd web/editor
npm ci
npm test
npm run typecheck
npm run build
cd ../api
npm ci
npm test
```

Sous Linux/macOS avec Java 25, utilisez `./gradlew build integrationTest`. Le JAR installable est `build/libs/questplus-0.2.0.jar` ; `-sources.jar` est réservé au développement. Les tests et mods de capture n'entrent pas dans le JAR distribué.

## Lancer Minecraft

Sous Windows, `0-Menu-Tests.bat` regroupe les opérations courantes. `1-Lancer-Client.bat` et `2-Lancer-Serveur.bat` utilisent les sources actuelles et des répertoires isolés : `run/client/` et `run/server/`. Aucune copie manuelle du JAR n'est nécessaire. Voir [les tests Windows](windows-testing.md).

- `./gradlew runClient` : client de développement.
- `./gradlew runServer` : serveur de développement.
- `./gradlew runClientGameTest` : tests natifs de l'interface et de la tablette.
- `node scripts/test-tablet-compatibility.mjs` : matrice GPU shaders et animations, dans `build/tablet-compat/`.

Les réglages localhost des scripts sont destinés aux tests locaux.

## Ce que les vérifications prouvent

`build` compile le JAR et exécute les tests Java. `integrationTest` lance le serveur Fabric GameTest, exerce les actions de jeu et vérifie les sauvegardes après arrêt. Les tests client contrôlent le rendu et les interactions sur le GPU local. Les tests web couvrent l'éditeur et le protocole API.

Les GameTests acceptent l'EULA pour leurs mondes de test. Ils ne remplacent pas une partie avec deux clients réels, ni un test de chaque combinaison de mods ou de pilotes.

Rapports Java : `build/reports/tests/test/index.html` et `build/gametest-results.xml`. Pour les médias publics et leur reproduction, voir [le showcase](showcase/README.md).

Architecture : [web / client / serveur](architecture.md), [client](client-architecture.md), [API web](../web/api/README.md).
