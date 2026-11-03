# Quest Plus — revue de l'ancrage de l'interface (Fabric 26.3)

Date : 3 novembre 2026. Portee : **PR #2 uniquement**, jamais `main` sans validation.

## Resultat et limites

- La surface est trouvee **dans le modele FP reel** : os `screen` sous `tablet_root`,
  taille 13 × 7,8 unites (facteur 0,65 deja applique dans le modele FP).
- Le mod observe la pose du modele pendant `FpModelRenderer.render`, applique le meme
  bone `tablet_root` que le renderer FP PAL, et projette les quatre coins de l'ecran
  sur le plan de la GUI. Cela fixe position et taille sans decalage pixel arbitraire.
- La GUI est ancree dans le rectangle projete et ses widgets sont scissores.
  Si la face sort du champ, manque ou devient trop petite, **l'ancienne GUI reste le repli**.
- Les textes sont reduits a 80 % ; boutons standards 18 -> 15 px ;
  onglets, categories, commandes de zoom et pages parametres resserres.
- L'animation `questplus.open` conserve sa derniere pose avant le relais `idle`.
- **Ce n'est pas encore un rendu texture GPU sur le polygone 3D** : les widgets
  sont toujours extraits via `GuiGraphicsExtractor` en 2D et ne sont pas
  corriges en perspective. L'ancrage utilise un rectangle axis-aligned de
  l'image projetee. Ne pas qualifier ce resultat de projection 3D parfaite.

## Boucle de 5 revues de code

| Passe | Axes controles | Correction / constat |
| --- | --- | --- |
| 1 | Animation d'ouverture, temps et ordre ouverture -> UI | Capturer le modele des l'etat OPENING ; `open` utilise `hold_on_last_frame` pour eliminer un reset entre clips. |
| 2 | Identite de l'os et cycle de vie | Confirmation : `screen` enfant de `tablet_root`, largeur 13, hauteur 7,8, pivot [0,18.8,-10.5]. Nettoyer la capture au reset/deconnexion. |
| 3 | Accessibilite, clic, scissor et faible resolution | Adapter les marges et le header a l'aperture ; textes 80 %, boutons 15 px, widgets clips ; rejeter les captures inferieures a 260 × 150 px. |
| 4 | Transition FP PAL / continuité des keyframes | Nouveau test JUnit : `open` conserve la derniere pose et sa derniere image coincide avec `idle` pour positions/rotations des deux bras et de `tablet_root`. |
| 5 | Fabric 26.3, ressources, et regressions | 15 invariants de code verifies statiquement : signature `FpModelRenderer.render(PoseStack,FpRenderBuffer,int)`, Mixin client facultatif, JSON, texture et geometrie, detach, GUI repli, absence de modeles en double. |

## Attention particuliere — Minecraft 26.3

- `GuiGraphicsExtractor` **enregistre** les elements GUI dans un `GuiRenderState`.
  Ce n'est pas une cible de dessin OpenGL immediate.
- Une vraie texture interactive appliquee a une face requerra **une cible de rendu
  hors ecran**, un `GuiRenderer` isole et sa gestion de `RenderTarget`,
  puis la composition dans le passage de rendu 3D et une conversion
  de coordonnees de souris UV -> widgets. Le chemin experimental courant ne le fait pas.
- Ne pas invoquer directement OpenGL : Minecraft 26.x peut utiliser Vulkan.
- Le FOV, l'oscillation, les shaders, le ratio d'ecran, les skins slim et les
  transitions en cas de clic rapide doivent etre verifies visuellement en jeu.
- Le `@Pseudo` Mixin tient compte de l'absence du mod optionnel FP PAL hors
  de l'environnement de developpement, et les dependances non-remapping Loom
  restent sur `implementation`.

## Verification reproductible

```cmd
git switch fix/native-first-person-tablet
git pull origin fix/native-first-person-tablet
3-Compiler-Mod.bat
1-Lancer-Client.bat
```

Dans Minecraft, taper `/fp_anim play questplus:questplus.open`, puis ouvrir avec J.
Tester plusieurs FOV, GUI Scales, langues et resizes. Ne **pas** fusionner
sur `main` tant que le build JDK 25 et la validation visuelle ne sont pas verts.

**Portee des revues** : analyses de sources et tests ajoutes ; une revue
statique ne prouve ni la compilation Gradle ni le rendu en jeu.
