> **2026-11 architecture update:** all client-only source and tablet
resources are now under `client/src/main/` and split from authoritative
`server/src/main/`. Both compile into a single Fabric mod JAR. For the
process trust boundaries and live web API, see
[docs/architecture.md](architecture.md).

# Client Quest Plus

> État courant : voir [le rapport de correction PR #2](reviews/native-tablet.md). Le reste de ce document décrit les étapes historiques, dont le rendu 2D a été remplacé par une texture hors écran sur le cube `screen`. Les dépendances sont actuellement déclarées `implementation` avec Loom non-remapping. La synchronisation utilise désormais l'horloge PAL, et aucun modèle HUD n'est ajouté pendant la séquence native.

La phase client est autorisée par le brief du 3 novembre 2026. Le Core et les sauvegardes restent indépendants du rendu.

## Plan et contrats

1. A : auditer les API réelles 26.3 dans les dépendances résolues ; définir une projection de lecture sans ledger privé ni données de commandes de récompenses.
2. B : Screen natif, catégories serveur, placement DAG déterministe mis en cache, index spatial, caméra indépendante, sélection et panneau défilant.
3. C : actions d'équipe exclusivement serveur, affichage après transaction durable, confirmations locales des départs/dissolutions.
4. D : recherche et filtres, épingles/HUD, notifications regroupées, traductions Minecraft et paramètres locaux.
5. E : modèle Blockbench conservé avec texture vierge ; rendu de modèle 3D natif sous une interface 2D alignée. Cette approche évite une texture dynamique et ses ressources GPU supplémentaires.
6. F : bras utilisant le skin connecté, animations interruptibles, poses distantes séparées des données de quêtes, flou natif désactivable.
7. G : compilation/tests, serveur dédié, lancement client et inspection visuelle si le poste le permet. Les validations non exécutées restent explicitement ouvertes.

Les imports Minecraft/Fabric restent sous `dev.questplus.fabric`. Le placement et les transformations sont du Java pur. Les paquets C2S n'identifient jamais l'acteur : la connexion le fournit. Les droits réclamables proviennent de l'état personnel, les compteurs de l'état collectif éventuel.

Le catalogue initial est fragmenté et appliqué atomiquement. Les changements de progression sont incrémentaux et regroupés ; un rechargement réussi remplace le catalogue. Les cartes de traductions voyagent avec les données pour permettre un changement de langue sans rechargement des JSON. Le client ne lit aucun fichier serveur.

L'API 26.3 utilise `GuiGraphicsExtractor` et l'extraction d'états de rendu. Les éléments hors viewport sont éliminés avant dessin ; le placement ne change qu'avec les définitions ou la catégorie. Le mode 2D demeure disponible. Aucun appel OpenGL direct.

## Points à valider

Le format actuel n'a pas de politique de quêtes secrètes ; il ne faut pas prétendre cacher une quête uniquement dans l'écran. Toute introduction de visibilité doit filtrer la projection serveur et les références avant transmission, avec tests.

L'image annoncée dans le brief n'est pas attachée à cette conversation et Blockbench ne contient aucune référence chargée. La direction disponible est donc la description bois sombre/métal/pierre/améthyste.

La compilation ne valide ni les shaders Iris/Euphoria, ni les skins, ni le rendu multijoueur. Consigner les contrôles réellement exécutés dans la roadmap.

## Correction premiere personne (branche experimentale)

Le mauvais rendu du 3 novembre (video de test) provenait d'une duplication entre les bras
dessines par `TabletRenderer` dans le GUI et le bras normal que Minecraft dessine dans
le monde. Une reduction supplementaire a 76 % rendait aussi la tablette illisible.
**Ces regressions ont ete annulees dans main avant ce nouveau travail.**

La nouvelle integration testee sur branche passe par le vrai pipeline des bras vanilla :
`First Person PAL Expansion 1.1.1` + `Player Animation Library 1.2.8`.
Les fichiers `questplus-assets/animations/player/questplus_first_person.animation.json`
sont charges tels quels depuis `assets/questplus/fp_animations/`.
`QuestClientTabletFlow` attend les 1,2 seconde de `questplus.open` avant de creer
`QuestScreen`. En ouverture et fermeture, le HUD dessine uniquement la tablette
sans faux bras. Pendant la consultation, les bras restent controles par la boucle
`questplus.idle` et `QuestScreen` dessine le modele sans doublon. Une fermeture
lance `questplus.close`.

**Limite visuelle restante :** le contenu des quetes est toujours compose en 2D
dans l'ouverture de l'ecran de la tablette, pas rendu sur une texture GPU de sa
surface 3D. L'alignement exact avec les mains sous differents FOV et shaders
demande une validation en jeu. Ne pas qualifier ce rendu de surface 3D native.

Le mod conserve une interface de repli si FP PAL n'est pas installe ; le serveur
dedie n'a pas besoin des dependances d'animation clientes. Les dependances sont
resolues automatiquement pour le client de developpement par Gradle.

A verifier : compilation Java 25, lancement `1-Lancer-Client.bat`,
ouverture sur J (main vanilla droite, seconde main), bascule a 1,2 s,
absence de bras clone, fermeture/Echap, GUI Scale 1 a 4,
skins slim/standard, monde multijoueur et server GameTests.

### Diagnostic FP PAL (3 novembre, correction PR #2)

La video montrait l'ancien rendu de `main`, sans mouvement FP PAL. `main` avait ete restaure,
mais PR #2 n'etait pas encore fusionnee. D'autre part, les dependances de mods etaient declarees
`implementation` ; elles sont maintenant en `modImplementation` pour etre chargees par Loom.

`assets/questplus/fp_animations/questplus_first_person.animation.json` comporte deja
`questplus.open`, `questplus.idle`, `questplus.close` et leurs keyframes, sans regenerer les assets.
FP PAL associe le fichier a `fp_models/questplus_first_person.geo.json` et
`textures/fp/questplus_first_person.png` : **une seule tablette** est rendue par FP PAL avec les
vrais bras. Ne pas la rendre une seconde fois via `TabletRenderer` pendant la sequence native.

En jeu, utiliser `/fp_anim list`, puis `/fp_anim play questplus:questplus.open` pour tester
l'animation independamment de J. Les logs indiquent maintenant si le plugin ou ses assets
ne se chargent pas, plutot que de basculer silencieusement vers le vieux GUI.
