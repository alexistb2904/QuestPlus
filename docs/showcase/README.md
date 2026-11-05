# Kit showcase Modrinth

Captures réelles de **QuestPlus 0.2.0 / Minecraft Java 26.3**, réalisées le **5 novembre 2026**. Aucun visuel de jeu généré par IA.

## Médias

| Fichier | Usage conseillé |
| --- | --- |
| [tablet.gif](tablet.gif) | Premier visuel de la description : ouverture, arbre et rangement |
| [quests.gif](quests.gif) | Arbre et détails d'une quête d'équipement |
| [tablet.webp](tablet.webp) | Image principale de la galerie |
| [details.webp](details.webp) | Objectif, prérequis et actions d'une quête |
| [hud.webp](hud.webp) | Suivi d'une quête épinglée pendant la partie |
| [team.webp](team.webp) | Onglet équipe dans la tablette, groupe créé dans le serveur intégré |
| [studio.webp](studio.webp) | Quest Studio dans le navigateur, arbre et objectif d'équipement |
| [tablet.mp4](tablet.mp4) · [quests.mp4](quests.mp4) | Versions vidéo des mêmes séquences pour réutilisation |

Les images Minecraft font **1600 × 900** ; Quest Studio **1920 × 1080**. Les GIF font **960 × 540**, sont bouclés et limités à moins de **5 Mio** chacun. Les PNG sources restent dans `build/showcase/screenshots/`, les séquences dans `build/showcase/frames/`.

## Rendu et crédits

- **Complementary Reimagined r5.9.3**, preset **HIGH** par défaut, par **EminGTR** — [projet officiel](https://modrinth.com/shader/complementary-reimagined).
- **Iris 1.11.7+26.3-fabric**, **Sodium 0.9.2**, **GeForce RTX 4070 Ti SUPER**.
- **Player Animation Library 1.2.8** et **First Person PAL Expansion 1.1.1** pour les poses de la tablette.
- Textures Minecraft vanilla ; shader et mods tiers non redistribués dans ce kit.

Les scènes utilisent le catalogue réel, une équipe de démonstration et un objectif épinglé via l'interface. Les GIF sont des montages de captures GPU échantillonnées, avec coupes entre les vues. Les **10 images/s d'export ne mesurent pas les FPS du jeu**. Quest Studio est une capture du navigateur ; les shaders concernent les vues Minecraft.

Les lectures PNG synchrones ralentissent les tests : l'outil de showcase compense ce temps dans les délais de sécurité de la tablette. **Aucun code du JAR distribué n'est modifié** pour cette capture. Les tests GPU de compatibilité utilisent le comportement normal ; voir [la compatibilité](../compatibility.md).

## Texte prêt à utiliser

Copiez le contenu de [la description Modrinth](../modrinth.md). Le [README](../../README.md) est la version courte pour le dépôt.

Ordre de galerie : `tablet.webp`, `details.webp`, `hud.webp`, `studio.webp`, puis `team.webp`.

Titres de galerie :

1. **Vos quêtes, dans votre monde** — Tablette animée et arbre interactif, avec Complementary Reimagined.
2. **Votre prochain objectif** — Détails, équipement demandé et actions de la quête.
3. **Gardez le cap** — Une quête épinglée au HUD pendant l'exploration.
4. **Créez votre aventure** — Quest Studio : arbre, objectifs et propriétés dans le navigateur.
5. **Avancez ensemble** — Gestion de l'équipe depuis la tablette.

Les images de la description utilisent des URL absolues vers `raw.githubusercontent.com/.../main/docs/showcase/`. Elles seront accessibles après publication des fichiers sur cette branche ; sinon, hébergez les médias puis remplacez leurs URL. Le kit est préparé localement, sans modification de la page Modrinth.

Le dépôt courant ne précise pas de licence de redistribution ; les textes ne lui attribuent donc pas une licence inventée.

## Reproduire

Prérequis : Java 25, Node.js 22, Python avec Pillow et FFmpeg dans le PATH.

```powershell
# Remplit le cache build/tablet-assets avec les versions officielles vérifiées par SHA-512.
node scripts/test-tablet-compatibility.mjs baseline
$env:JAVA_HOME = & .\scripts\Find-Java25.ps1
.\gradlew.bat runClientGameTest -I scripts/showcase/showcase.gradle --console=plain *> build/showcase-capture.log
```

Le monde, les options et les dépendances de cette prise restent dans `build/showcase/`. Aucun monde de `run/client/` n'est utilisé. L'initialisation de test accepte l'EULA pour ce monde de démonstration. Le source de capture est compilé uniquement avec ce fichier Gradle.

Capturez ensuite Quest Studio à 1920 × 1080 dans `output/playwright/studio-final.png`, puis :

```powershell
python scripts/showcase/export.py
# Rétablit les ressources GameTest normales après la prise isolée.
.\gradlew.bat processGametestResources
```

L'export vérifie le succès de la prise, les dimensions, le nombre d'images des GIF, leur boucle et leur poids. [manifest.json](manifest.json) conserve les versions, empreintes des dépendances et SHA-256 des médias.
