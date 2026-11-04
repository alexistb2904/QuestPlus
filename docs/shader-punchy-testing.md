# QuestPlus: shaders, Self Illumination et fermeture de la tablette

## Comportement voulu

Le seul critère d'activation est **un shader pack réellement utilisé par
Iris** (API `IrisApi.isShaderPackInUse`). Installer Iris sans activer de
shader ne modifie ni le flou ni la luminosité de QuestPlus.

| Shader actif ? | Self Illumination | Matériau écran | Lumière | Background Blur |
| --- | --- | --- | --- | --- |
| Non | Désactivé | Translucide classique | Lumière du monde | Choix utilisateur |
| Non | Activé | Émissif classique | Luminosité d'origine | Choix utilisateur |
| Oui | Désactivé | Mat / opaque | Lumière du monde | **OFF automatiquement** |
| Oui | Activé | Mat / opaque | Lumière maximale, plafond 78 % | **OFF automatiquement** |

L'ancien paramètre « Écran compatible shaders (Iris) » a été **supprimé**.
Le réglage Self Illumination déclenche son ancien comportement lumineux mat
uniquement lorsque le shader est actif. En désactivant Self Illumination,
l'écran ne reçoit plus de lumière artificielle et n'utilise pas de matériau
émissif ; il reste néanmoins **mat** pour éviter d'être interprété comme
du verre par Bliss.

Le choix Background Blur est conservé sur disque, mais son état effectif est
OFF (bouton désactivé et mention explicative) tant qu'un shader tourne.
Dès que le shader est désactivé, l'ancien choix du joueur redevient effectif.

## Bliss : cadre / écran physique

Le cadre 3D utilise la **texture originale statique du modèle**, et ne lit
plus le framebuffer de Quest Studio lors du rendu d'un shader. Seule la face
écran utilise l'atlas GUI dynamique. Les UV du cadre reviennent donc à
l'échelle d'origine du modèle (256×256), distincte de l'atlas 2048×1024.

La face écran utilise un matériau `entityCutout` **même quand Self Illumination
est désactivé**, car les matériaux `entityTranslucent` sont souvent traités
comme du verre / SSR dans les shaders. L'intensité maximale dépend uniquement
de l'état de Self Illumination.

Cette séparation limite les reflets, mais les réflexions et le DOF propres à
Bliss ou à un autre shader peuvent encore affecter les modèles 3D.

## Fermeture : restauration impérative de l'interface Minecraft

L'ancien code cachait le HUD pendant tout l'état `CLOSING`. Si FP PAL
n'achevait pas son animation (après un changement de shader, par exemple),
la hotbar et d'autres éléments restaient absents jusqu'à la reconnexion.

Désormais :
- Le HUD n'est masqué que pendant l'affichage réel du `QuestScreen`.
  Dès que Échap ferme la fenêtre, Minecraft reprend sa hotbar et son HUD.
- Le système laisse terminer les animations normales.
- Un délai de sécurité de **2,5 secondes** force l'arrêt de l'animation de
  fermeture si elle n'a pas rendu de dernier état.
- Un délai de **3,5 secondes** récupère également une ouverture bloquée,
  en proposant alors la GUI standard.
- Une exception FP PAL, l'apparition d'une autre fenêtre ou une déconnexion
  libèrent le contrôleur et rétablissent l'état de caméra.

## Validation manuelle (client Fabric 26.3 + Iris + Punchy)

1. Avec **Iris installé mais shaders désactivés**, vérifier les réglages
   classiques Self Illumination et Background Blur.
2. Avec **Complementary**, vérifier Blur OFF automatique pour les deux états
   Self Illumination et essayer une dizaine d'ouvertures/fermetures avec Échap.
3. Avec **Bliss**, vérifier l'écran et le cadre sans reflets en jour/nuit,
   et comparer Self Illumination ON/OFF.
4. Avec **MakeUp**, vérifier la lisibilité et que l'écran éclairé ne brûle pas
   les couleurs de ses icônes.
5. À chaque fermeture, tester immédiatement hotbar, inventaire, souris,
   affichage HUD, touche J et réouverture de la tablette.
6. Essayer la bascule de shader lorsque la tablette est ouverte, puis fermée.
7. Vérifier que l'inventaire et le HUD réapparaissent même si FP PAL échoue.

Les tests statiques prouvent les branchements ; **ils ne remplacent pas un
test rendu GPU dans Minecraft**. Les shaders peuvent avoir un DOF indépendant
sur lequel QuestPlus n'a pas de contrôle universel.
