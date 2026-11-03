# Correction de la tablette native — PR #2

Branche `fix/native-first-person-tablet`, Minecraft Java 26.3, Java 25.0.5, Fabric Loader 0.20.1, Fabric API 0.165.2, FP PAL 1.1.1 et PAL 1.2.8. Aucune fusion dans `main`.

## Causes et corrections

- L'ancien GUI utilisait le rectangle englobant d'une projection avec le FOV des options et seulement `tablet_root`. Il perdait la perspective et les transformations de `screen`.
- Le chronomètre commençait avant le démarrage effectif de PAL. La transition utilise maintenant le contrôleur réel ; les poses open/idle/close et leur cadrage sont identiques aux jonctions. Un second appui pendant open programme close à la jonction, sans disparition instantanée.
- Le cadrage initial ne correspondait pas à la caméra Blockbench. L'export calcule sa rotation et sa translation à partir du centre du modèle et de la caméra source. Après le retour utilisateur, cette caméra est passée de `[0,25.6,1]` à `[0,17.6,.5]` : vue plus proche et face légèrement orientée vers le haut (environ 6°).
- FP PAL applique des pivots `[±6,6,6]` et une échelle générique aux bras natifs ; la référence utilise `[±5,22,0]`. `TabletNativeArms` dessine les vrais bras/manches/skins vanilla dans le repère des os source, sans modifier leurs poses : les callbacks différés ne reçoivent que les maillages immuables. Les manches sont déjà enfants des bras dans Minecraft 26.3 et ne sont plus transformées une seconde fois.
- La main droite commençait déjà abaissée ; la tablette grandissait de zéro à un en 20 ms. La source commence maintenant au repos puis descend/remonte, avec une tablette de taille constante. Les bras sont réduits à 45 % dans les pistes Blockbench ; la translation est recalculée autour du contact de paume pour conserver la prise, plutôt que de décaler le modèle au hasard.
- L'atlas initial était lu à l'envers et coupé à x=500 malgré un décalage de contenu de 256 px. Les UV et dimensions d'extraction sont corrigés. Le cube d'écran a un matériau émissif distinct du corps.

## Rendu retenu

Le vrai `.bbmodel` a été ouvert, inspecté et sauvegardé dans Blockbench : 83 cubes, 15 os, trois animations. Les exports viennent du codec Bedrock natif via `tools/export_first_person.py`, avec comparaison automatisée de chaque image-clé au fichier source.

La face nord du cube existant `screen` mesure 13×7,8 unités. Elle reçoit la région logique 500×300, rendue en 1500×900 dans un atlas GPU persistant 2048×1024, contenant aussi la texture originale du corps. `GuiRenderState`, `GuiGraphicsExtractor` et `GuiRenderer` assurent le rendu hors écran, objets et scissor compris. Les dimensions de fenêtre, la cible et la projection sont remplacées dans un scope `try/finally`, puis restaurées. Aucun appel OpenGL direct.

Le corps et le cube d'écran sont chacun dessinés une seule fois, avec leurs matériaux respectifs. Aucun second modèle ni bras factice. `TabletFaceAnchor` capture les transformations réelles de `screen` et de tous ses parents et utilise la projection des mains Minecraft. Le rayon souris inverse donne les UV pour clics, survol, déplacement et molette. L'échelle d'interface change le canevas logique sans déplacer la surface physique. Les ressources GPU sont réutilisées et libérées à la fermeture du renderer.

Les textes utilisent des pixels entiers ; recherche, zoom et actions sont remontés hors de la prise des mains. Le diagramme reste déplaçable. Le mode sans animation conserve un écran classique, avec suppression de son ancien rendu de faux bras.

## Cinq cycles de correction exécutés

Chaque étape a compilé et lancé Minecraft ; les défauts constatés ont été corrigés avant la validation finale.

| Cycle | Essai et résultat |
|---|---|
| 1 — audit | Modèle Blockbench réel, lecture des API et ouverture en monde local : tablette visible pendant open puis disparition au GUI. Résultat rejeté. |
| 2 — transitions | Horloge PAL et orientation UV : tablette maintenue en idle, mais moitié droite du GUI coupée. Résultat rejeté. |
| 3 — surface | Cadrage et dimensions d'atlas : GUI sur la face, onglets cliquables ; bras absents identifiés séparément. |
| 4 — interactions | Conversion des bras vanilla, matériau d'écran, lisibilité et clics : vrais bras visibles et interactions en perspective. |
| 5 — régression | Cinq configurations ci-dessous, skins classiques/fins, échelle 125 %, fermeture normale et fermeture demandée pendant open ; nouvelles captures inspectées. |

## Matrice Minecraft

Chaque séquence utilise réellement J, capture la descente/open/fin de mouvement/idle, sélectionne une quête, clique Équipe et Paramètres aux coordonnées projetées, passe l'échelle à 125 %, revient aux Quêtes et ferme avec J.

| Séquence | Fenêtre | FOV | GUI Scale | Skin | Capture idle |
|---|---|---|---|---|---|
| 1 | 1280×800 | 45 | 2 | Classique | [image](native-tablet/idle-1.png) |
| 2 | 1024×768 | 70 | 3 | Fin | [image](native-tablet/idle-2.png) |
| 3 | 1600×900 | 100 | 1 | Classique | [image](native-tablet/idle-3.png) |
| 4 | 1280×720 | 30 | 2 | Fin | [image](native-tablet/idle-4.png) |
| 5 | 1600×900 | 85 | 4 | Classique | [image](native-tablet/idle-5.png) |

Autres captures Minecraft : [open](native-tablet/opening.png), [fin d'ouverture](native-tablet/settling.png), [échelle 125 %](native-tablet/scaled.png), [close](native-tablet/closing.png). Le contrôle manuel a également chargé le monde local, utilisé J de nuit, cliqué un nœud pour ouvrir ses détails, puis fermé avec J.

## Commandes et contrôles

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-25.0.5.0.3'
./gradlew.bat build runClientGameTest --console=plain
python questplus-assets/tools/export_first_person.py
python questplus-assets/tools/validate_assets.py
```

79 tests JUnit et GameTests serveur de progression/récompenses, événements avancés et équipes exécutés. Le Client GameTest couvre les cinq configurations natives, les clics projetés et l'interruption. Les tests unitaires vérifient les exports, la continuité, la taille physique constante, l'absence de cubes de bras supplémentaires et les intersections souris/face (centre, bords, extérieur, rayon parallèle, perspective, rotation et échelle). La validation géométrique Python vérifie les contacts bras/corps-tablette ; elle ne remplace pas Minecraft.

## Fichiers modifiés

- Source et export : `questplus-assets/blockbench/questplus_first_person.bbmodel`, `models/attachment.json`, `animations/player/questplus_first_person.animation.json`, `tools/motion.py`, `tools/build_assets.py`, `tools/verify_blockbench.py`, nouveau `tools/export_first_person.py` ; copies des animations dans les ressources `animations/` et `fp_animations/`.
- Flux et GUI : `FirstPersonPalBridge`, `QuestClientTabletFlow`, `QuestScreen`, `TabletFaceAnchor`, `TabletRenderer`, `ui/QuestSkin`, nouveaux `TabletNativeArms` et `TabletSurfaceRenderer`.
- Hooks : `TabletFaceCaptureMixin`, nouveaux `GameRendererAccessor`, `TabletGameRendererMixin`, `TabletGuiExtractorMixin`, `TabletGuiRendererMixin`, `TabletNativeArmsMixin`, et `questplus.client.mixins.json`.
- Géométrie auxiliaire déplacée de `assets/questplus/models/tablet/geometry.json` vers `assets/questplus/tablet/geometry.json`, pour ne plus être interprétée comme un modèle JSON vanilla invalide.
- Tests : `FirstPersonAssetTest`, `TabletFaceAnchorTest`, `TabletFirstPersonAnimationTest`, `QuestClientGameTest`, et un mixin de skin dans le source set GameTest uniquement.
- Documentation : ce rapport et ses captures, suivi dans `client-architecture.md` et `roadmap.md`.

## Limites

Les essais graphiques utilisent OpenGL sur RTX 4070 Ti SUPER. Le code utilise RenderPearl ; Vulkan n'est pas validé : les périphériques de cet environnement manquent de `VK_KHR_push_descriptor`. Pas de certification Iris/shaders tiers ni de partie avec deux vrais clients distants. La scène de régression extérieure utilise deux RemotePlayer rendus par le vrai moteur, dont un reçoit les paquets de pose du serveur intégré ; elle ne simule pas deux connexions réseau. Les bras arrivent par le bas et prennent les bords inférieurs sans couvrir les contrôles. Aucun changement du core, de la persistance ou des données de progression.


## Retouches du 4 novembre 2026

Les retours sur les captures ont conduit à une nouvelle boucle Minecraft complète. Les cinq configurations de la matrice ont été rejouées après les corrections suivantes.

- **HUD** : hotbar, décorations et réticule masqués pendant open/idle/close ; retour normal après close. Le curseur de souris utilisé pour les clics reste disponible.
- **Bras** : approche depuis le bas dans la source `.bbmodel`, pivot de paume conservé par calcul matriciel, échelle native 45 %. Open/close ont une clé tous les 10 ms (100 échantillons/s), interpolée par PAL ; cela ne promet pas 100 FPS sur toute machine.
- **Fluidité réelle** : FP PAL ne copie ses os qu’à 20 ticks/s. Deux instantanés bruts des bras, de la racine et des os de tablette sont interpolés à chaque image, sans rappeler `setupAnim` ni avancer les événements. Le test Minecraft compare deux sous-images du même tick : les bras et la tablette bougent et le compteur PAL reste identique. La pose terminale de `hold_on_last_frame` est appliquée exactement au raccord ; attendre un tick supplémentaire bloquait l’ouverture, car FP PAL cesse de produire ses poses à la fin.
- **Fin de fermeture** : `hold_on_last_frame` et désactivation immédiate du contrôleur après la dernière pose. Quatre captures successives après close vérifient l'absence de tablette réapparue en haut.
- **Netteté** : rendu GUI à trois fois sa définition logique, filtrage linéaire et atlas persistant. Le texte évite l'aliasing qui supprimait des traits avec le filtrage nearest à petite taille. Le nombre de pixels physiques de l'écran reste une limite à toute taille extrême.
- **Flou du décor** : post-traitement vanilla déclenché après le monde et avant les mains. Comparaison [désactivé](native-tablet/blur-off.png) / [activé](native-tablet/blur-on.png) ; l'écran et les mains restent nets.
- **Quêtes** : [objectifs et récompenses](native-tablet/details.png) en blocs compacts avec icône Minecraft, libellé traduit et compteur séparé ; œuf d'apparition pour les créatures. Les descriptions longues défilent. L'en-tête utilise toute la largeur disponible, avec infobulle du texte complet.
- **Catégories** : [rail défilable](native-tablet/categories-scrolled.png), décalage vérifié et clic sur la catégorie réellement affichée. Aucun changement des données serveur : les vingt catégories ajoutées par le test sont une fixture locale temporaire.
- **Champs** : contenu et indication centrés, sans ombre ; [saisie réelle au clavier](native-tablet/centered-input.png) après clic projeté.
- **Échelle 125 %** : [détails compacts](native-tablet/scaled-details.png), disparition des contrôles du diagramme derrière le panneau ; test de fermeture des détails dans l'ancienne zone de collision, puis retour du filtre.
- **Vue extérieure** : plus de formule de rotation indépendante. `TabletThirdPersonAnimation` lit les pistes du codec natif, converties dans le repère vanilla ; position et échelle de tenue proviennent du repère source, avec tête dirigée vers la tablette. Fondu depuis/vers la pose vanilla et continuité de la respiration lors de close. [Porteur et témoin](native-tablet/remote-idle.png), [profil](native-tablet/remote-side.png), [après fermeture](native-tablet/remote-closed.png).
- **Isolation entre joueurs** : aucune mutation partagée des ModelPart par les mains subjectives ; pas de copie de rotation vers les manches enfants. Le test inspecte le modèle du témoin avant et après une consultation locale : bras au repos et rotations des manches nulles.

Nouveaux fichiers : `TabletFrameInterpolation`, `TabletFpInterpolationMixin`, `TabletFrameInterpolationTest`, `TabletThirdPersonAnimation`, `TabletHudMixin`, `TabletModelPartAccessor`, `animations/third_person.json`, `TabletThirdPersonAnimationTest` et `tools/export_third_person.py`. Les instantanés sont également effacés si le joueur se déconnecte pendant la consultation. Les autres fichiers concernés sont ceux du flux, GUI, renderer, mixins, source/export première personne, traductions, tests et génération listés plus haut.

Export extérieur reproductible, sans reconstruire ni écraser les sources éditées :

```powershell
python questplus-assets/tools/export_third_person.py
python questplus-assets/tools/export_third_person.py --check
```

Validation serveur : quatre GameTests obligatoires passent ; `integrationTest` relit les sauvegardes après arrêt et vérifie 140 UUID et les historiques de récompenses/équipes. La scène graphique vérifie les paquets de pose et le renderer de deux avatars locaux ; une partie avec deux vrais clients, les shaders tiers et Vulkan restent à contrôler.

Réglage demandé ensuite le 4 novembre : échelle de la tablette extérieure seule passée de 0,65 à 0,50 (environ 23 % plus petite), autour du même pivot. Les bras, les pistes d’animation et la tablette subjective restent inchangés. `build runClientGameTest` passe (1 min 21 s), avec les captures de face/profil contrôlées et actualisées dans ce rapport.
