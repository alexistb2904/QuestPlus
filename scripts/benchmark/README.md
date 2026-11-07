# Benchmarks locaux QuestPlus

## Quest Studio

API réelle en localhost, données synthétiques, Node >=22, sans serveur Minecraft
de production. Le banc lance lui-même des processus isolés, avec un tas plafonné
à 384 Mio et une télémétrie IPC privée. Une hausse excessive de RSS arrête le
scénario. Les 1 000 demandes utilisent de vraies connexions HTTP et adresses
sources distinctes dans le loopback, ce ne sont pas 1 000 navigateurs.

```powershell
node scripts/benchmark/studio-load.mjs baseline small trial1
node scripts/benchmark/studio-load.mjs baseline large trial1
node scripts/benchmark/studio-load.mjs current small trial1
node scripts/benchmark/studio-load.mjs current large trial1
node scripts/benchmark/studio-search.mjs
```

Répéter avec `trial2` et `trial3`, en alternant l'ordre avant/après. Ne pas lancer
d'autres builds ou benchmarks pendant les mesures. Les résultats sont sous
`build/benchmark/studio/<version>-<scenario>/results-trialN.json`.
La source `baseline` provient du commit `2702882`, conservé dans l'historique Git.
`STUDIO_BENCHMARK_BASELINE` permet de choisir une autre référence pour une nouvelle
expérience. Le runtime et les dépendances restent ceux du checkout courant.
Le benchmark de recherche utilise la même référence. Les références exactes de
l'expérience du 6 novembre sont décrites dans le rapport. Les artefacts privés
et traces sont ignorés par Git.

Le cas lourd représente 500 namespaces de mods, 50 000 items, 25 000 blocks,
2 500 tags, 1 000 noms traduits et 1 000 quêtes en grille. Il ne charge pas de
JARs de mods et ne mesure pas la validation Minecraft. Les scénarios comprennent
admission, approbation, lectures de catalogue, publications et uploads de packs
publics en rafale, ainsi que la disponibilité de `/health` pendant et après la
surcharge. Des 429/503 et, au-delà des limites de transport, des resets peuvent
être attendus, ils sont comptés séparément. `completed` exclut les erreurs du
banc, pas les refus de charge attendus.

Pour le navigateur, lancer Vite sur 5199, puis l'API sur 5201 avec
`HUB_PUBLIC_URL=http://127.0.0.1:5201`, `STUDIO_ORIGIN=http://127.0.0.1:5199`,
`STUDIO_URL=http://127.0.0.1:5199/`, un `HUB_DATA_DIR` isolé sous `build/` et la
clé **factice** `synthetic-studio-browser-key-not-a-real-secret`.
`studio-browser-bridge.mjs create` fournit l'URL. Après « Connect this browser »,
`studio-browser-bridge.mjs approve` simule le pair Minecraft approuvant la demande.
Charger les quêtes et revenir à l'éditeur. Les fonctions Playwright CLI
`studio-browser-connected.mjs` et `studio-browser.mjs` mesurent les interactions.
La première injecte un 503 sur le polling et le heartbeat, après le clic Save,
`studio-browser-bridge.mjs ack` simule la confirmation et permet de vérifier
qu'une seule publication a été émise. Le second scénario s'exécute dans Mod Registry.
Les captures vont dans `output/playwright/`. L'approbation/ACK simulés ne sont
pas une validation des quêtes par un vrai serveur Minecraft.

## Moteur Fabric

Depuis la racine du dépôt, sous Windows avec un JDK 25 :

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.5.0.3' # adapter au JDK 25 installé
./scripts/benchmark/Run-Benchmarks.ps1 -CompareParent -RecordJfr
```

Le lanceur réutilise `Find-Java25.ps1`. Il compile le banc séparément, puis
exécute trois répétitions par version, alternativement avant/après, avec G1 et un tas
de 512 Mio à 3 Gio. Rien n'est ajouté au JAR distribué. Les résultats, données
entièrement synthétiques et traces privées restent dans `build/benchmark/`.

`baseline` est le **premier parent de HEAD**. Seules les cinq classes mesurées
(`QuestEngine`, `QuestRegistry`, `PlayerProgress`, `JsonProgressStore`,
`ClientProjection`) sont recompilées et placées avant les classes courantes
sur le classpath. Ce n'est pas un ancien serveur Minecraft complet.

Les scénarios couvrent :

- Registre et progression : 100/1 000/5 000 quêtes, 1 à 1 000 quêtes
  correspondant au même événement.
- Une série complémentaire, dans des JVM séparées, fait dépendre
  10/100/500/999 quêtes de l'état « commencée » d'une quête commune. Elle
  exerce explicitement le partage des prérequis ajouté par le merge.
- Charge par tick simulé : 100/300/1 000/3 000 profils indépendants,
  100 ou 1 000 états de quête chacun, un événement pertinent par profil.
  L'historique est terminé sauf la quête active, aucun événement de fin de
  quête ni de récompense n'est émis dans ce scénario.
- Projection complète du progrès, sérialisation des définitions + progrès,
  layout et recherche des nœuds visibles.
- Sauvegarde de 24 profils : coût synchrone sur l'appelant, attente durable
  et transaction placée juste derrière. La version courante emploie
  `saveAutosaveBatch`, l'ancienne `saveBatch`. `copy_json_24` mesure séparément
  l'ancienne copie JSON, elle ne représente pas l'ancienne autosauvegarde.

Chaque mesure CPU comporte 350 ms de chauffe puis au moins une seconde de
mesure. Les ticks lourds visent trois secondes et au moins 30 échantillons,
avec un arrêt après 15 secondes. Le p99 d'un petit échantillon est proche du
maximum observé : ne pas l'interpréter comme un percentile de production.
Les octets/op proviennent du thread appelant uniquement. Les assertions
contrôlent la sélection, la copie indépendante, la progression et la relecture
des sauvegardes. Les timings disque comportent dix répétitions après chauffe.
Un échec d'I/O est enregistré explicitement sous `persistence_failure` et
exclut ce scénario des timings disque, les autres scénarios continuent.
`completed` indique que le banc est allé au bout, pas que toutes les I/O ont
réussi. Consulter `persistence_failures` et les logs avant de conclure.

Pour le vrai client natif, **après** la création des fixtures `current-1` :

```powershell
$env:JAVA_HOME = (& ./scripts/Find-Java25.ps1).Trim()
./gradlew.bat -I scripts/benchmark/benchmark.gradle -PbenchmarkClient runClientGameTest
./gradlew.bat -I scripts/benchmark/benchmark.gradle -PbenchmarkClient -PbenchmarkClientDense runClientGameTest
```

La première capture couvre le HUD fermé et la tablette ouverte avec 100,
1 000 et 5 000 quêtes, dont seules quelques-unes sont visibles. La seconde
dessine volontairement 1 000 nœuds superposés dans une petite zone. Ce palier
dépassant déjà le budget d'une frame, il constitue la limite de ce test extrême.
Les catalogues de ces essais sont injectés directement dans la projection
client, ils ne mesurent pas un transfert réseau. Les mondes sont des mondes
GameTest isolés. Le mélange de mods est celui de développement, sans shaders.
Le progrès injecté est vide : les nœuds empruntent le rendu verrouillé par
défaut, avec icône et cadenas. C'est un test du coût de dessin de cet état.

Les mesures client portent sur `Minecraft.renderFrame`, à 1280×720, VSync
désactivée, elles ne sont pas une mesure de latence GPU ni de FPS présentés.
Le limiteur AFK est réinitialisé et son absence est vérifiée à chaque phase.
Les traces JFR et captures se trouvent dans `build/benchmark/client*`.

Pour revérifier l'intégration dans un répertoire serveur neuf :

```powershell
./gradlew.bat -I scripts/benchmark/benchmark.gradle "-PbenchmarkFreshGameTest=$(Get-Date -Format yyyyMMdd-HHmmss)" build integrationTest
```

Exécuter les JVM de benchmark, GameTests, builds et analyses JFR **en série**.
Les mesures de composants ne prouvent ni des TPS Minecraft ni la capacité
d'un serveur à héberger autant de connexions/chunks/entités. Le banc n'inclut
pas de clients réseau, d'équipes de 64, de panne disque ou de modpack 500 mods.

Pour résumer les premières méthodes QuestPlus présentes dans les piles JFR,
sans exporter les traces brutes (après `prepareBenchmark`) :

```powershell
$cp = Get-Content build/benchmark/classpath.txt -Raw
& "$env:JAVA_HOME/bin/java.exe" -cp $cp dev.questplus.benchmark.JfrSummary build/benchmark/components.jfr build/benchmark/components-summary.json
```

Les poids d'allocation JFR sont des estimations échantillonnées, pas des
compteurs exacts. Les piles sont attribuées au premier appel QuestPlus trouvé,
ce ne sont donc pas des temps propres exclusifs par méthode.
