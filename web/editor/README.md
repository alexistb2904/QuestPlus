# Quest Studio — éditeur visuel de QuestPlus

Interface web **locale** et sans backend, ni compte, ni téléchargement de dépendances. Le navigateur conserve un brouillon dans `localStorage`; **exportez régulièrement vos packs ZIP** pour ne pas perdre le projet si vous videz vos données de navigation.

## Lancer

Sous Windows : double-cliquer sur [`web/editor/start.bat`](start.bat). Sinon, depuis la racine du dépôt :

```powershell
python -m http.server 5173 --bind 127.0.0.1
```

Ouvrir **http://localhost:5173/web/editor/**. Ne pas ouvrir `index.html` via `file://` : les modules JavaScript ES nécessitent un serveur HTTP. Aucun accès réseau ni service distant n'est nécessaire, hors chargement facultatif de la police Google (la police système prend le relais si elle est bloquée).

## Publication sur GitHub Pages

Le workflow [`pages.yml`](../../.github/workflows/pages.yml) publie **uniquement** les fichiers
statiques de Quest Studio à la racine du site Pages, sans publier les sources Java du mod,
les tests ou les fichiers de configuration du dépôt.

**Configuration initiale (une seule fois)** : dans le dépôt GitHub, ouvrir
**Settings → Pages → Build and deployment → Source → GitHub Actions**.
Il faut que GitHub Pages soit disponible pour le dépôt et son offre GitHub ;
le workflow ne peut pas activer Pages à lui seul avec le `GITHUB_TOKEN` standard.

Après un push sur `main` modifiant `web/editor/**`, le workflow exécute les tests Node,
prépare le dossier `_site` puis déploie via `actions/deploy-pages`.
Il peut également être déclenché manuellement dans l'onglet **Actions**.
L'URL GitHub Pages habituelle est `https://alexistb2904.github.io/QuestPlus/`
(si aucun domaine personnalisé n'est configuré), après le premier déploiement réussi.

Le site est **statique** : il n'y a pas d'API ni de base de données serveur.
Les projets modifiés restent dans le stockage local du navigateur et ne sont **pas**
automatiquement publiés ou synchronisés d'un appareil à l'autre. Utiliser l'export
ZIP pour transférer des définitions entre appareils ou vers Minecraft.

## Design system & internationalization

Quest Studio mirrors the QuestPlus Minecraft GUI in
`src/main/java/dev/questplus/fabric/client/ui/QuestSkin.java`:
PAPER `#35251d`, INK `#e3cdb1`, DIM `#ac927a`,
EDGE `#76513b`, SELECTED `#d4ad70`, background `#30231e`
and tree canvas `#38291f`. The green-themed website was replaced by the
actual in-game copper/parchment palette.

**One editor tree only.** The former `Aperçu jeu` mode was removed as
redundant; the canvas itself uses QuestLayout world coordinates, supports
mouse-wheel zoom, drag-to-pan, and touch pinch zoom.

**Site UI localization:** English by default. Browser detection uses
`navigator.languages` for English, French, Spanish and Simplified Chinese,
falling back to English when unsupported. A separate **Interface** dropdown
stores a manual override under `questplus.studio.uiLocale`.
Translations are bundled in `web/editor/ui-i18n.js` for offline use.
**Quest text localization is separate:** the "Quest text" field always starts
with the canonical English `en_us` source and offers `fr_fr`,
`es_es`, `zh_cn` as immediate translation targets, plus custom Minecraft
locale codes. Switching the site's interface language never rewrites
quest/category/objective translations. Previously saved editor drafts
stay in localStorage without data migration.

## Éditeur Minecraft 26.3

Le site démarre sur **les 4 catégories et 13 quêtes réellement intégrées au mod**
(`src/main/resources/defaults`), copiées sans réécriture dans
`web/editor/default-data.js`. Un brouillon déjà sauvegardé dans le navigateur
reste prioritaire pour éviter d'écraser le travail ; le bouton **Défaut** restaure
les quêtes du mod après confirmation.

**English (`en_us`) est le texte source.** Le menu Langue permet de choisir les
traductions présentes et **+** d'ajouter un code de langue Minecraft
(`de_de`, `es_es`, `ja_jp`, etc.). Les champs ne montrent que l'anglais
et la langue sélectionnée, sans imposer `fr_fr`. Les traductions sont conservées
dans les objets JSON `title`, `description`, `text`, etc. Si aucune traduction
n'est fournie, le jeu utilise `en_us`. Les codes ajoutés sans traduction sont
un état de l'éditeur, non un élément des fichiers serveur exportés.

**Icônes :** sélecteur visuel à recherche, alimenté par le catalogue
`minecraft-textures@26.3.0` (CDN jsDelivr, chargement facultatif, fallback
sur une liste embarquée). Pour les blocs, les faces de texture Minecraft sont
récupérées depuis MC Assets 26.3. Les images externes demandent Internet ;
l'éditeur local et l'export fonctionnent sans elles. Les IDs non-vanilla,
par exemple `create:brass_ingot`, restent librement saisissables et sont
préservés à l'export, même si leur icône n'est pas prévisualisable sans assets
du mod correspondant. Les textures originales appartiennent à Mojang Studios.

**Coordonnées :** `game-layout.js` reproduit les règles de `QuestLayout.java`
pour les nœuds 32 × 32, l'espacement automatique 64 × 72, les dépendances,
la priorité des positions `layout.x/y` explicites, ainsi que les bornes
de zoom de `QuestCamera.java`. Le diagramme de l'éditeur est la seule représentation de l'arbre ; il utilise directement les coordonnées du mod.

La création reste compacte : nom anglais, icône, objectifs, position ;
les récompenses, prérequis et paramètres se trouvent dans des sections
repliables. La description est facultative, sans répétition de l'objectif.

## Fonctionnalités

- Créer, modifier et dupliquer les **catégories**, les **quêtes** et les **annotations** (heading, note, label).
- Glisser les quêtes et annotations dans un arbre 2D, panoramique au clic/glisser, zoom à la molette, position automatique et ajustement.
- Composer les objectifs (événement, cible, quantité), les récompenses XP/objets et les prérequis ET ; les dépendances OU/NON et autres fonctionnalités avancées sont accessibles dans le JSON.
- Prévisualiser des **objectifs automatiquement libellés en français ou anglais** sans devoir rédiger une description répétitive.
- Éditer le JSON brut de chaque élément sans perte de ses champs inconnus ; les champs avancés sont conservés si non modifiés.
- Importer un ZIP QuestPlus, un dossier contenant `categories/`, `quests/` et `config.json`, ou plusieurs JSON ; exporter un ZIP autonome comprenant les mêmes dossiers, le fichier `config.json` et `index.txt`.
- Valider IDs, objets, références, cycles de prérequis, positions et cohérence de base ; annuler/rétablir, sauvegarde locale automatique.

**Import d'un monde existant :** copier/importer son dossier `config/questplus` (ou le dossier QuestPlus configuré sur le serveur), sans les fichiers de progression des joueurs. L'éditeur n'importe que les définitions JSON. Vérifier les quêtes et catégories à l'écran après l'import.

**Déployer un pack :** extraire le ZIP exporté vers le dossier de définitions QuestPlus du serveur. **Sauvegarder ce dossier avant toute mise à jour** ; l'export ne contient pas les sauvegardes des joueurs. Ne pas supprimer par inadvertance les anciens fichiers : si un ID est renommé, supprimer ou migrer explicitement son ancienne définition pour éviter les doublons. Exécuter `/questplus reload` sur le serveur et lire les diagnostics. Le serveur valide les identifiants de registres et les extensions que le navigateur hors ligne ne peut pas connaître.

**Compatibilité et limites :** données principales de QuestPlus schemaVersion 1/2, annotations des catégories dans `annotations` (nouveau), `layout.x/y` des quêtes. Le panneau graphique édite les cas fréquents ; pour `conditions`, prérequis composés, récompenses d'extensions, tiers, timers et champs avancés, utiliser l'éditeur JSON. Le projet ne contient pas encore de moteur de prévisualisation Minecraft 3D ni de collaboration temps réel. Le site n'écrit pas automatiquement sur un serveur distant.

## Studio : gestes, conditions et compatibilité

Le navigateur utilise une seule caméra : glisser pour déplacer, molette pour zoomer/dézoomer et pincer avec deux doigts sur mobile. Les boutons +, − et ajuster restent disponibles.

**IDs** : à la création, le titre anglais donne `questplus:category/title-name`.
En cas de doublon, le prochain suffixe libre est `-2`, `-3`… Si le titre
n'a aucun caractère convertible en ID, un identifiant aléatoire est généré.
Les quêtes déjà expédiées avec le mod conservent leurs IDs pour ne pas
casser les progressions des joueurs. Les IDs de nouvelles quêtes restent
suivis automatiquement quand leur titre change.

**Prérequis/conditions** : le panneau de la quête contient une recherche
de prérequis et un arbre éditable ET/OU/NON, avec groupes imbriqués.
La section Conditions de chaque objectif propose les mêmes opérateurs et
les prédicats autorisés par `QuestRegistry` (biome, dimension, outil, effet,
vie et accroupissement). Le résultat est directement le JSON lu par le mod.

**Icônes** : le catalogue embarqué vient des registres `item` et `block`
de `misode/mcmeta` figés au tag `26.3-registries` (1 658 items et
1 286 blocs). Aucun téléchargement de manifeste n'est requis pour choisir
un ID. Les miniatures téléchargées depuis MC Assets sont des previews PNG :
un objet 3D particulier peut être présenté par sa texture ou un losange,
sans affecter l'ID sélectionné. Les IDs moddées restent acceptés.

**Version** : une version Minecraft cible est associée au projet. Pour les
items introduits dans une version ultérieure, l'auteur peut renseigner la
version minimum à côté de l'icône. Un avertissement de compatibilité est
affiché avant l'export. Le ZIP ajoute
`queststudio-compatibility.json` (métadonnées Studio seulement) : les
catégories, quêtes et `config.json` demeurent strictement compatibles avec
le mod. En jeu, si l'ID d'item n'existe pas dans le registre chargé, QuestPlus
utilise l'icône vanilla `minecraft:barrier` comme fallback visible.
