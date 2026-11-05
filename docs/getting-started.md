# Bien démarrer avec QuestPlus

## Installer

QuestPlus 0.2.0 cible **Minecraft Java 26.3, Fabric Loader 0.20.1 ou supérieur et Java 25**. Les autres versions de Minecraft et les autres loaders ne sont pas annoncés compatibles.

1. Installez Fabric et **Fabric API 0.165.2+26.3 ou supérieur pour Minecraft 26.3**.
2. Placez le même JAR QuestPlus dans `mods/` sur votre client et votre serveur. En solo, le client contient déjà le serveur intégré.
3. Pour la tablette 3D animée, ajoutez **Player Animation Library 1.2.8** et **First Person PAL Expansion 1.1.1** sur le client. Sans elles, utilisez l'interface 2D.
4. Lancez un monde et appuyez sur **J**. La touche se change dans les contrôles Minecraft.

Les shaders sont facultatifs. Pour retrouver le rendu des captures : **Sodium 0.9.2, Iris 1.11.7 et Complementary Reimagined r5.9.3**, dans leurs variantes Minecraft 26.3/Fabric. Voir la [compatibilité](compatibility.md).

## Jouer

Choisissez une catégorie, puis une quête dans son arbre. Les détails affichent ses objectifs, ses prérequis et ses récompenses. Épinglez jusqu'à **trois quêtes** pour les suivre dans le HUD, puis réclamez les récompenses une fois les objectifs remplis.

Dans l'onglet **Équipe**, créez un groupe ou rejoignez une invitation pour partager la progression. Les règles de récompense dépendent de la configuration du serveur : voir [les équipes](team-progression.md).

L'onglet **Réglages** permet notamment de choisir le mode tablette, l'échelle de l'interface, la luminosité, les animations, le mouvement réduit et la position du HUD et des notifications. Sous un shader actif, le flou d'arrière-plan est automatiquement suspendu.

Les événements de gameplay ignorent les joueurs créatifs et spectateurs. **Ramasser**, **fabriquer** et **posséder** sont des objectifs distincts : un `/give` ne remplace pas un ramassage au sol.

## Créer son pack avec Quest Studio

Lancez `web/editor/start.bat` sous Windows, ou :

```sh
cd web/editor
npm ci
npm run dev
```

Ouvrez `http://localhost:5173/`. Node.js 22 est requis pour ce lancement local ; les joueurs n'ont pas besoin de Node.js.

1. Partez du catalogue fourni ou importez votre pack QuestPlus.
2. Ajoutez vos catégories et vos quêtes ; placez les nœuds dans l'arbre.
3. Définissez objectifs, prérequis, conditions et récompenses dans les panneaux d'édition.
4. Renseignez le texte anglais source et ajoutez vos traductions.
5. Corrigez les erreurs signalées, puis utilisez **Exporter ZIP**.

L'éditeur conserve les brouillons dans le navigateur. Exportez votre ZIP pour les sauvegarder et les transférer. Une fois chargé, le mode hors connexion ne nécessite pas de service API ; les icônes distantes non mises en cache peuvent manquer.

### Installer le ZIP manuellement

Arrêtez le serveur et sauvegardez le monde avant votre première importation.

- Décompressez les définitions du ZIP dans `<monde>/questplus/`, en conservant les dossiers `quests/` et `categories/`.
- Préservez `config.json` pour garder les réglages de ce monde.
- Redémarrez, ou lancez `/questplus reload` si le serveur est déjà actif.
- Vérifiez le résultat de validation avant de reprendre la partie.

Un rechargement invalide est rejeté. Les définitions et la progression des joueurs sont séparées : **n'écrasez pas `<monde>/data/questplus/`** pour installer un pack.

Gardez les IDs de quêtes et d'objectifs stables. Renommer un ID ne transfère pas sa progression ; changer la mécanique d'une quête peut bloquer une ancienne tentative incompatible. Les détails sont dans la [référence JSON](json-schema.md).

### Modifier directement un serveur

L'administrateur doit d'abord configurer le [service web](../web/api/README.md). Ensuite :

1. Exécutez `/questplus editor` en tant qu'opérateur et ouvrez le lien proposé.
2. Demandez l'accès dans le navigateur, puis confirmez le code dans Minecraft avec `/questplus editor approve <code>`.
3. Chargez les quêtes du serveur, modifiez-les et cliquez sur **Enregistrer sur le serveur**.
4. Attendez la confirmation de Minecraft : le serveur valide et applique le pack.

Un lien de session seul n'accorde aucun droit. Les sauvegardes de joueurs et les clés serveur ne sont pas envoyées au navigateur. `/questplus editor revoke` révoque la session ; `/questplus rollback` restaure la dernière sauvegarde des définitions.

## Commandes utiles

| Commande | Usage |
| --- | --- |
| `/questplus progress [id]` | Consulter sa progression |
| `/questplus claim <id>` | Réclamer ses récompenses |
| `/questplus restart <id>` | Relancer une quête lorsque ses règles le permettent |
| `/questplus team invitations` | Voir les invitations reçues |
| `/questplus team info` | Consulter son équipe |
| `/questplus reload` | Recharger les définitions, opérateur requis |
| `/questplus editor` | Ouvrir une session d'édition, opérateur requis |
| `/questplus import <pack-id>` puis `/questplus import confirm` | Prévisualiser puis importer un pack partagé, opérateur requis |
| `/questplus rollback` | Restaurer la dernière sauvegarde de définitions, opérateur requis |

Les commandes d'administration utilisent la permission Minecraft `COMMANDS_GAMEMASTER` (niveau opérateur 2 standard). `/questplus` et l'autocomplétion présentent les commandes disponibles.

## Ajouter des mécaniques moddées

Les objectifs acceptent des identifiants `namespace:path` et des tags, selon leur type. Le serveur valide les références contre les registres des mods installés ; un simple ID dans un JSON n'ajoute pas un nouveau hook de gameplay.

Pour une mécanique personnalisée, utilisez l'[API Java QuestPlusApi](../server/src/main/java/dev/questplus/fabric/api/QuestPlusApi.java) : enregistrement d'événements, conditions, récompenses et soumission d'événements sur le thread serveur. Le [schéma JSON](json-schema.md) décrit les limites et les champs disponibles.
