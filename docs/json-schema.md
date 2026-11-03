# Définitions de quêtes JSON

Quest Plus lit des fichiers `.json` indépendants dans le dossier `quests`. Le schéma JSON Schema Draft 2020-12 de référence est [`quest.schema.json`](quest.schema.json). Le schéma décrit la forme des données; les règles qui dépendent des autres champs, de l'ordre ou des extensions enregistrées restent validées par le chargeur.

## Exemple

```json
{
  "schemaVersion": 2,
  "id": "questplus:mining/stone_milestones",
  "category": "questplus:mining",
  "icon": "minecraft:stone_pickaxe",
  "title": { "en_us": "Stone Milestones", "fr_fr": "Jalons de pierre" },
  "description": { "en_us": "Mine 100, 1,000 and 10,000 stone.", "fr_fr": "Minez 100, 1 000 et 10 000 pierres." },
  "objectives": [{ "id": "stone", "event": "block_break", "target": "minecraft:stone", "count": 100 }],
  "rewards": [],
  "progression": "tiered",
  "tiers": [
    { "id": "hundred", "threshold": 1, "rewards": [{ "type": "experience", "amount": 10 }] },
    { "id": "thousand", "threshold": 10, "rewards": [{ "type": "experience", "amount": 50 }] },
    { "id": "ten_thousand", "threshold": 100, "rewards": [{ "type": "experience", "amount": 200 }] }
  ]
}
```

## Version et structure

- `schemaVersion` vaut `1` ou `2`. La version 1 conserve le format initial: `progression` doit être `once` et les champs de progression v2 sont interdits. La version 2 accepte `once`, `repeatable`, `infinite` et `tiered`.
- Les objets rejettent les propriétés inconnues. `id`, `category` et `icon` sont des identifiants `namespace:path`. Chaque carte de texte (`title`, `description`, `text`, `narrative`, et texte de récompense) exige une traduction anglaise `en_us`; les autres langues sont facultatives.
- Une quête standard contient de 1 à 256 objectifs. Une quête `kind: "information"` (v2) indique `objectives: []`, et `acknowledgement` vaut `manual` (défaut : un objectif virtuel `manual` reconnaît la lecture via `/questplus trigger <id>`) ou `read_only` (lecture sans progression, jamais automatiquement complétée et sans récompense). Une quête en lecture seule ne peut pas servir de prérequis. Les conditions de lecture/déclenchement et l'affichage de la modale seront pris en charge par le futur client. Les quêtes d'information restent `once`, sans timers ni paliers. Toutes les listes de récompenses ont au plus 64 entrées. Les identifiants locaux d'objectifs et de paliers restent stables.
- `objectiveMode` vaut `all` (défaut), `any` ou `sequence`. `all` exige tous les objectifs, `any` un seul. `sequence` débloque les objectifs dans l'ordre; un événement ne valide qu'une étape.
- `cooldownSeconds` accepte 0 à 31 536 000 secondes. `timeLimitSeconds` accepte 1 à 31 536 000 secondes. `noDeath` est un booléen. `afterLast` vaut `stop` (défaut) ou `repeat`, uniquement pour `tiered`.
- `hidden` est facultatif et vaut `false` par défaut. Si `true`, le serveur révèle la quête dès que ses prérequis sont satisfaits ou que la progression collective ou personnelle contient déjà cette quête. Une quête sans prérequis est donc immédiatement visible. Cette visibilité est recalculée : un reset peut à nouveau masquer une quête sans progression si ses prérequis ne sont plus satisfaits. Les références à une quête non révélée sont masquées dans les définitions transmises ; les textes rédigés par l'administrateur restent de sa responsabilité.

## Progression et récompenses

- `once` termine la quête après une complétion.
- `repeatable` attend la commande `/questplus restart <id>` après la complétion. Le délai éventuel s'applique avant le redémarrage.
- `infinite` ouvre un nouveau cycle automatiquement après chaque complétion.
- `tiered` exige un tableau non vide de paliers. Chaque palier contient `id`, `threshold` positif et `rewards`. Les seuils doivent être strictement croissants. Pour un objectif de compte `c`, son seuil effectif est `c × threshold`; le produit doit être au plus 1 000 000 000. Ces vérifications croisées sont faites par le chargeur et ne sont pas exprimées par le schéma.
- Un événement peut terminer au plus un cycle par appel; tout excédent de compte au-delà de l'objectif ne reporte pas sa progression dans le cycle suivant. Les paliers distincts déjà atteints lors de cet événement donnent chacun leur récompense.
- Les récompenses intégrées sont `{ "type": "experience", "amount": n }` (1 à 1 000 000) et `{ "type": "item", "item": "namespace:item", "count": n }` (1 à 2 304). Une récompense d'extension utilise `{ "type": "namespace:type", "data": { ... } }`; le type doit avoir été enregistré par un mod. `text` peut fournir un libellé traduit.
- Une complétion `once` utilise l'ID de quête comme clé de réclamation. Les cycles répétables et infinis utilisent l'ID de quête avec l'UUID de tentative; chaque palier atteint possède aussi sa clé unique. Le redémarrage conserve l'historique des réclamations.

## Objectifs, événements et conditions

- Chaque objectif contient `id`, `event`, `target` et `count` (1 à 1 000 000 000). `text` et `conditions` sont facultatifs. La cible est un identifiant ou un tag préfixé par `#`; le chargeur Fabric vérifie les blocs, objets, entités et autres registres applicables.
- Événements intégrés: `block_break`, `block_place`, `block_interact`, `item_obtained`, `item_craft`, `item_smelt`, `item_use`, `item_consume`, `fish_item`, `trade`, `entity_kill`, `fish_entity`, `tame_animal`, `breed_animal`, `npc_interact`, `biome_visit`, `structure_visit`, `dimension_enter`, `inventory_count`, `travel_distance`, `location`, `player_death` et `manual`. Un événement personnalisé doit utiliser un ID namespacé et être enregistré par un mod.
- Les objectifs d'inventaire (`inventory_count`) suivent la quantité courante dans le snapshot d'inventaire fourni par l'événement; elle peut redescendre, y compris à zéro. Ils ne représentent pas un cumul de ramassages et ne déclenchent pas de scan permanent.
- Conditions d'objectif: `dimension`, `tool`, `biome`, `effect`, `healthAtMost`, `healthAtLeast`, `sneaking`, `equipment` (`slot` et `item`), `position` (bornes `xMin`/`xMax`/`yMin`/`yMax`/`zMin`/`zMax`), `context` (`key` et valeur primitive `equals`), ainsi que `and`, `or` et `not`. Les extensions enregistrées peuvent ajouter leurs propres conditions namespacées.
- Les prérequis `requires` acceptent `{ "quest": "namespace:id" }` et les opérateurs `and`, `or`, `not`. Les références doivent exister et les cycles sont rejetés.
- Les arbres de conditions et de prérequis sont limités à une profondeur de 16 et 256 nœuds au total; un produit de seuil supérieur à 1e9, des seuils non croissants, des IDs de palier dupliqués ou une extension inconnue font échouer le chargement.

## Sauvegarde, délais et rechargement

Les temps sont enregistrés comme horodatages absolus en millisecondes. Un délai ou une limite de durée continue donc à s'écouler serveur arrêté. À l'expiration, l'événement suivant constate le délai et démarre une tentative vide. Une mort réinitialise la tentative non terminée d'une quête `noDeath`; le cumul historique et les réclamations restent conservés.

Le fingerprint mécanique v1 reste identique pour les définitions v1. En v2, la progression, le mode d'objectif, les paramètres de durée, les IDs et seuils des paliers font partie du fingerprint. Un changement mécanique d'une quête active laisse son ancienne progression et son historique en place, mais la bloque pour éviter de reprendre des compteurs incompatibles. Un changement de texte ou de récompenses ne change pas le fingerprint. Les IDs de quête et d'objectif doivent rester stables; aucune migration automatique d'ID n'est fournie.

## Définitions fournies

[`../src/main/resources/defaults/quests/`](../src/main/resources/defaults/quests/) contient six quêtes v1 historiques, une quête informative et des exemples v2 de répétition avec délai, cycles infinis, paliers, séquence, quantité d'inventaire et exploration. [`../src/main/resources/defaults/index.txt`](../src/main/resources/defaults/index.txt) inventorie les ressources livrées. L'installation initiale préserve les fichiers utilisateur; un monde déjà initialisé ne reçoit pas automatiquement les nouvelles définitions.

Les définitions montrent les formats configurés, pas la preuve que chaque événement a un hook de jeu. La couverture effective des hooks et leur comportement serveur doivent être vérifiés dans les tests d'intégration appropriés. Les sauvegardes corrompues ou de version inconnue ne doivent jamais être remplacées par une progression vide.

## Texte automatique et annotations de l'arbre

La `description` d'une quête est facultative. Sans description, seuls les objectifs sont affichés, générés automatiquement dans la langue du joueur à partir de l'événement, de la quantité et du nom localisé de la cible. Un texte `objective.text` explicite reste prioritaire. Si la description répète exactement le texte d'un objectif, l'interface ne l'affiche qu'une fois.

Chaque catégorie accepte désormais `annotations` (au plus 128 objets), par exemple :

```json
{"annotations":[{"id":"depart","x":0,"y":-70,"text":{"en_us":"Start here","fr_fr":"Commencez ici"},"style":"heading","color":"#c6a986"}]}
```

Les styles admis sont `note`, `heading` et `label`. Les annotations utilisent le même repère `layout.x/y` et la même caméra zoom/pan que les quêtes ; elles sont uniquement visuelles. `x` et `y` doivent être finis et entre ±100000, et l'ID unique par catégorie.
