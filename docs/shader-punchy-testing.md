## Test tablette avec Iris + Punchy! (Minecraft 26.3)

**Objectif :** les mains et le modèle 3D de QuestPlus restent gérés par FP PAL
pendant l'animation d'ouverture, même si Punchy! est installé. Punchy! reprend
son fonctionnement normal dès que la tablette est rangée.

- Punchy! **3.1+** est nécessaire pour que QuestPlus utilise l'API publique
  `PunchyApi.disable("questplus", condition)` : elle désactive temporairement
  Punchy! pendant que QuestPlus contrôle les mains, sans modifier la configuration
  de Punchy! et sans dépendance obligatoire dans le JAR QuestPlus.
- Le téléchargeur de test ignore les versions antérieures et rafraîchit
  automatiquement l'ancien cache si celui-ci contient Punchy! 3.0 ou moins.
- Si l'animation ne démarre toujours pas, vérifier les logs pour
  `Punchy compatibility` et `Playing Blockbench first-person`. Avec une
  vieille version de Punchy!, F8 permet de désactiver Punchy temporairement.

### Shaders : rendu mat et flou

Lorsqu'Iris déclare qu'un shader pack est actif, QuestPlus active par défaut
**Écran compatible shaders (Iris)** dans les paramètres de sa tablette.

1. L'ancienne passe `processBlurEffect` n'est plus empilée par-dessus le
   post-traitement du shader (cause du flou global avec Complementary).
2. L'écran 3D passe de `entityTranslucentEmissive` à une surface opaque
   `entityCutout`, pour limiter les effets de miroir/SSR de Bliss et le bloom
   ou l'exposition excessive de MakeUp.
3. La lumière de l'écran seule utilise le lightmap maximal, plutôt que le
   matériau HDR émissif. Le contenu UI est modéré à 78 % de sa luminosité
   maximale pour les shader packs (les réglages Vanilla restent inchangés).
4. Le cadre 3D et le monde gardent leur éclairage naturel. Le flou 2D standard
   n'est pas modifié quand aucun shader n'est actif.
5. Le mode est désactivable dans la tablette pour comparer les rendus.

**Limite importante :** les shaders contrôlent eux-mêmes la profondeur de
champ (DOF), les réflexions et la post-exposition. QuestPlus ne peut pas
garantir qu'un écran 3D soit exclu d'un DOF intégré au shader sans une
intégration spécifique au shader / une passe de composition après son rendu.
Si Complementary continue à flouter la tablette, désactive son option DOF
dans le menu shader pour vérifier la différence. Le correctif supprime le
flou Minecraft ajouté en doublon, pas les effets volontaires du shader.

### Scénarios à exécuter sur Windows

| Configuration | Contrôle |
| --- | --- |
| Aucun shader + Punchy 3.1 | Animations d'ouverture/fermeture QuestPlus et bras visibles |
| Complementary | Scène non globalement floutée par la passe Minecraft |
| Bliss | Écran mat, sans miroir exagéré ; lisibilité en extérieur et intérieur |
| MakeUp Ultra Fast | Interface non surexposée et icônes lisibles |
| Tous les shaders | Taille tablette, hover, clics, zoom et animations stables |
| Shader ON/OFF dans Iris | Bascule du rendu sans redémarrage ni crash |
| Mode shader-safe OFF | Comparaison avec l'ancien rendu |
| Punchy absent | Comportement original QuestPlus inchangé |
