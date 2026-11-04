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
