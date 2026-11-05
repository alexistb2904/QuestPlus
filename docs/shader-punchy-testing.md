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

Iris 1.11.7 soumet les mains dans deux passes, solide puis translucide.
FP PAL 1.1.1 soumet son modèle personnalisé dans les deux, hors du filtrage
vanilla des objets tenus. Avec Bliss, cette seconde soumission transforme
le cadre et l'écran en miroir. Changer seulement `entityTranslucent` en
`entityCutout` ne suffit donc pas.

QuestPlus filtre maintenant cette seconde soumission directement dans
`FpRenderBuffer.drawGeometry` et dans son rendu des bras natifs. Le modèle
reste dans la passe solide, même si le joueur tient deux blocs de verre.
Les autres animations et objets reprennent les passes habituelles à la fermeture.

## Fermeture : restauration impérative de l'interface Minecraft

L'ancien code cachait le HUD pendant tout l'état `CLOSING`. Si FP PAL
n'achevait pas son animation (après un changement de shader, par exemple),
la hotbar et d'autres éléments restaient absents jusqu'à la reconnexion.

Désormais :
- Le HUD n'est masqué que pendant l'affichage réel du `QuestScreen`.
  Dès que Échap ferme la fenêtre, Minecraft reprend sa hotbar et son HUD.
- Le système laisse terminer les animations normales.
- La fin `hold_on_last_frame` est reconnue par l'état PAL `PAUSED`, même
  lorsque les fractions de rendu placent son compteur juste sous la durée
  exacte. Les snapshots conservent alors la pose finale normalisée.
- L'atlas conserve sa dernière image pendant le rangement : l'interface
  ne devient plus un écran noir dès la fermeture de la fenêtre.
- Un délai de sécurité de **2,5 secondes** force l'arrêt de l'animation de
  fermeture si elle n'a pas rendu de dernier état.
- Un délai de **3,5 secondes** récupère également une ouverture bloquée,
  en proposant alors la GUI standard.
- Une exception FP PAL, l'apparition d'une autre fenêtre ou une déconnexion
  libèrent le contrôleur et rétablissent l'état de caméra.

First Person Model utilise son API `ActivationHandler.preventFirstperson`
pour céder temporairement le rendu du corps à FP PAL, puis le reprendre.
Son réglage d'activation utilisateur n'est pas modifié.

Punchy Moves intercepte ses raccourcis avant Minecraft. En cas de conflit avec
la touche configurable de QuestPlus, la tablette reçoit cette touche ; une
autre affectation redonne son raccourci à l'émote sans modifier les réglages.

## Matrice GPU reproductible

```powershell
node scripts/test-tablet-compatibility.mjs
# Un profil seulement : baseline, punchy, nea ou firstperson
node scripts/test-tablet-compatibility.mjs punchy
```

Le lanceur télécharge les versions officielles Modrinth déclarées pour 26.3,
vérifie leur SHA-512 et utilise uniquement `build/tablet-assets/` et
`build/tablet-compat/<profil>/`. Il ne modifie pas `run/client/`, ses mods,
ses options ou ses sauvegardes. Les versions et empreintes figurent dans
`assets.json` de chaque profil ; les preuves sont dans `screenshots/` et
`logs/latest.log`.

| Profil | Mods supplémentaires à PAL / FP PAL / QuestPlus |
| --- | --- |
| baseline | Iris, Sodium |
| punchy | Iris, Sodium, Punchy!, Punchy Moves, Fluid & Foolish, 3D Skin Layers |
| nea | Iris, Sodium, Not Enough Animations, 3D Skin Layers |
| firstperson | Iris, Sodium, Not Enough Animations, First Person Model, 3D Skin Layers |

Chaque profil teste l'absence de shader puis Bliss, Complementary Reimagined,
Complementary Unbound, BSL, Photon, MakeUp et Sildur's Vibrant. Deux cycles
par rendu : écran éclairé / mains vides et écran non éclairé / verre dans les
deux mains. Les assertions vérifient l'activation réelle du shader, une zone
sombre de l'interface dans la capture GPU (régression miroir), l'évolution
des os pendant le rangement, la conservation de l'atlas et la restitution
du HUD / de la caméra. Un cycle en marchant et les poses de deux avatars
distants simulés complètent chaque profil. Ce dernier essai n'est pas une
connexion multijoueur à deux clients réels.

La capture d'une zone sombre ne prouve pas à elle seule la lisibilité de
tous les textes : les captures restent à examiner visuellement. Les packs
sont testés avec leurs réglages par défaut, sur le GPU disponible ; cela ne
couvre pas toutes les options PBR / DOF, les pilotes ou les combinaisons de mods.

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
