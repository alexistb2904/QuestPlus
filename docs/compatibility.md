# Compatibilité — QuestPlus 0.2.0

## Installation

| Élément | Version / rôle |
| --- | --- |
| Minecraft | **Java 26.3 uniquement** |
| Java | **25 minimum** |
| Fabric Loader | Minimum déclaré **0.20.1** |
| Fabric API | Minimum déclaré **0.165.2+26.3**, utiliser un build pour Minecraft 26.3 |
| QuestPlus | **Un même JAR** client/serveur ; pas deux mods séparés |
| Solo | Le serveur intégré gère la progression |
| Serveur dédié | Moteur, commandes et chat ; installer aussi le mod côté client pour la tablette |
| Forge / NeoForge / Quilt / Bedrock | Aucun build pris en charge annoncé |

Les minima sont ceux de `fabric.mod.json`. Une version ultérieure de Fabric API ou d'un mod tiers n'est pas une promesse de compatibilité universelle.

## Tablette et animations

**Player Animation Library 1.2.8** et **First Person PAL Expansion 1.1.1** sont les versions utilisées pour les bras et la tablette 3D. Installez-les côté client avec leurs dépendances. Elles ne sont pas intégrées au JAR QuestPlus. Sans elles, ou avec les animations désactivées / le mouvement réduit, QuestPlus propose l'interface 2D.

| Mod supplémentaire | Intégration prévue |
| --- | --- |
| Punchy! **3.1b** | Cède le rendu des mains à la tablette via son API |
| Punchy Moves **1.0.6-hotfix** | La touche configurée de la tablette garde la priorité ; changer cette touche rend le raccourci à l'émote |
| Fluid & Foolish **1.0.1** | Resource pack du profil Punchy Moves, pas un mod Fabric |
| Not Enough Animations | Profil de test dédié ; utiliser le build Fabric 26.3 figurant dans le manifeste du test |
| First Person Model | Cède temporairement la caméra à FP PAL sans changer le réglage du joueur |
| 3D Skin Layers | Inclus dans les profils de test d'animation |

Ces intégrations ciblent les versions testées, pas tous les mods qui changent les bras, la caméra ou les raccourcis. Les versions exactes et empreintes des téléchargements sont enregistrées dans `build/tablet-compat/<profil>/assets.json`.

## Shaders

Les médias publics utilisent **Sodium 0.9.2**, **Iris 1.11.7+26.3-fabric** et **Complementary Reimagined r5.9.3** sur une **GeForce RTX 4070 Ti SUPER**. Ils montrent le rendu obtenu sur cette configuration, sans garantir les mêmes performances sur un autre GPU.

Avec un pack effectivement actif dans Iris :

- l'écran et le cadre utilisent la voie de rendu adaptée aux shaders ;
- le flou d'arrière-plan est automatiquement suspendu, puis retrouve le choix du joueur après désactivation du shader ;
- l'auto-éclairage et la luminosité restent réglables dans la tablette.

Les options DOF, PBR, les pilotes et les autres mods peuvent modifier le résultat. Iris installé avec les shaders désactivés conserve le comportement sans shader. OptiFine n'est pas la configuration testée.

### Vérifier une configuration

```powershell
node scripts/test-tablet-compatibility.mjs baseline
node scripts/test-tablet-compatibility.mjs punchy
node scripts/test-tablet-compatibility.mjs nea
node scripts/test-tablet-compatibility.mjs firstperson
```

La matrice exerce le rendu sans shader, Bliss, Complementary Reimagined, Complementary Unbound, BSL, Photon, MakeUp et Sildur's Vibrant. Chaque profil contrôle ouverture, écran éclairé/non éclairé, fermeture, restitution du HUD, déplacement et poses d'avatars distants simulés. Le [protocole détaillé](shader-punchy-testing.md) explique les assertions et les limites.

Les rapports GPU existants des quatre profils ont été contrôlés pour ce showcase. Le profil de base est également relancé séparément de la capture. **Les captures seules ne constituent pas un test de compatibilité** : leur outil neutralise les délais de sécurité pendant les lectures PNG synchrones. La matrice GPU conserve les délais normaux.

Les GameTests serveur sont distincts des essais GPU. Les avatars distants simulés ne constituent pas une partie avec deux clients réels ; aucune parité multijoueur de ce type n'est annoncée ici.

## Modpacks et formats

- IDs et tags des mods installés utilisables selon le type d'objectif ; les nouvelles mécaniques nécessitent un hook de jeu ou l'[API Java](../server/src/main/java/dev/questplus/fabric/api/QuestPlusApi.java).
- Packs **QuestPlus JSON / ZIP** ; aucun import ou export **FTB Quests** fourni.
- Interfaces FR, EN, ES et chinois simplifié ; les textes de vos propres quêtes doivent être traduits dans le pack.
- Éditeur local utilisable hors connexion après chargement ; les icônes externes peuvent nécessiter le réseau si elles ne sont pas en cache.
- Édition connectée facultative, avec service web configuré et autorisation d'opérateur dans Minecraft.

Pour l'installation, les imports et les commandes : [bien démarrer](getting-started.md).
