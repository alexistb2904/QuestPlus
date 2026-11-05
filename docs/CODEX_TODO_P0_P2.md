# Quest Plus — suivi Codex P0–P2

Mise à jour du 5 novembre 2026, après les PR #27/#29 et l'intégration des récompenses dans les interfaces.
Une case cochée correspond aux limites de validation décrites ci-dessous. Le projet ne revendique pas encore une parité complète avec FTB Quests.

## Vérifié sur le checkout

- [x] Java 25 : `gradlew.bat build integrationTest` ; 175 tests unitaires, sept GameTests serveur (dont le contrôle vanilla), contrôle des sauvegardes des 140 UUID après arrêt.
- [x] Client réel : `gradlew.bat runClientGameTest` ; clics dans la tablette 3D, deux choix indépendants, confirmation persistée, attribution et rejet d'un rejeu. Captures initiales dans `docs/reviews/reward-integration/`, continuation dans `docs/reviews/ftb-parity/`.
- [x] Studio : `npm ci`, `npm run typecheck`, `npm test` (138), `npm run build` ; API : `npm ci`, `npm test` (29). Lanceur Windows `scripts/_qa.bat api-test` vérifié.
- [x] Revue indépendante : confusion de cycle dans Claim All et accumulation des reçus corrigées, avec régressions GameTest. Un seul reçu livré détaillé et un résultat vide au maximum sont conservés par quête ; les clés anti-rejeu restent conservées.
- [ ] Observer les vérifications GitHub de la PR ; ne pas confondre les validations locales avec un résultat CI.

## P0 — livré dans cette intégration

- [x] Choix tablette depuis le droit personnel : paquet `ChooseReward(quest, token, slot, option)`, connexion du joueur comme identité, limitation de fréquence commune, validation serveur du droit/de l'option/de l'emplacement. Les anciens paquets ne peuvent ni sélectionner un nouveau cycle ni consommer un autre emplacement.
- [x] Blocage de Claim pendant les choix et les écritures ; invalidation du snapshot après confirmation persistée. Un échec de stockage gèle la progression et n'autorise aucune attribution.
- [x] Plusieurs choix par quête, droits individuels d'équipe, répétitions, déconnexion pendant le choix, restauration des valeurs concrètes et reprise après journal interrompu. Les commandes historiques restent disponibles ; la tablette n'exécute aucune commande textuelle.
- [x] Aperçus filtrés des objets, XP, niveaux, advancements, messages et tables ; aucune commande ni donnée privée d'extension transmise. Possibilités et résultats serveur séparés, probabilités par tirage et révélation légère respectant le mouvement réduit.
- [x] Collecte globale des droits concrets disponibles même si un autre cycle attend un choix. Ni tirage au moment de Claim, ni rejeu d'un reçu livré.
- [x] Validation des identifiants d'objets/advancements dans les choix contre les registres installés, comme pour les autres tables.
- [x] Interactions manuelles par objectif, y compris les marqueurs personnalisés : paquet joueur dédié à cette action, contexte scoped quête/objectif et commande historique réutilisant le même chemin. Un marqueur partagé ne valide pas une autre quête.
- [x] Soumissions optionnelles dans les séquences : progression réelle sans sauter une étape obligatoire ; vérification des tags, main secondaire, mode de jeu, doublons et inventaire plein. Les soumissions non consommatrices de paliers vérifient toute l'exigence du palier ; les dépôts ne prélèvent pas silencieusement pour un palier supérieur.
- [x] Fixture JSON5 réellement exportée par Studio, validée par les tests Node et chargée par `QuestRegistry` dans les tests Java.

## P0 — reste bloquant avant certification multijoueur

- [x] **Journal des remises et récupération de processus** : plan détaché, piles sérialisées par codec, marqueur sauvegardé avec l’inventaire vanilla, images JSON joueur/équipe et empreintes ; reprise avant/après save sans nouveau prélèvement. Régressions nouveau fichier `.tmp`, répétition infinie et dernier palier répétable.
- [x] **Claims réservés** : `/questplus recovery inspect <uuid>` et `resolve <uuid> "<claim>" <delivered|discard> <reason>`, permissions OP et décision auditée atomiquement ; aucune réattribution automatique.
- [ ] **Durabilité matérielle des remises** : tester coupure réelle à chaque frontière, disque plein, composants moddés et comparaison complète de l’inventaire relu. Le readback actuel vérifie le marqueur ; pas de garantie de coupure électrique ni de borne mesurée de latence des barrières synchrones. `TODO(Codex)` dans `InventoryMarker.save`.
- [ ] **Deux clients réels sur serveur dédié jetable** : la couverture actuelle utilise des joueurs simulés dans le serveur Fabric et un client réel avec serveur intégré. Tester objets moddés, fluides, redémarrages, publication Hub/backup/rollback et packs malveillants/surdimensionnés.
- [ ] **Parité complète d'authoring** : les nouvelles récompenses ont un roundtrip partagé ; étendre ce contrat à tous les événements, quantités, conditions, descripteurs et exports de migration.

## P1 — outils de création et intégrations

- [x] Éditeur graphique commun aux récompenses normales et aux paliers : ajouter/supprimer/réordonner, choix, random/loot/all, poids, tirages, vide, pourcentages, simulation sans attribution, sélecteurs d'objets/advancements et champs des récompenses d'extension.
- [x] Titres d'options et descriptions de choix traduisibles, aperçu de cartes, erreurs bloquant l'export. Nouveaux libellés anglais/français/espagnol/chinois ; contrôles clavier natifs et vérification visuelle bureau/390 px.
- [x] Tables nommées portables dans les métadonnées du projet et du Hub, insérées comme **copies indépendantes** dans les quêtes. Les tables imbriquées, références circulaires et commandes dans les pools sont rejetées.
- [x] **Références de tables** : résolution des `questplus:table` avant activation du registre, cycles/noms inconnus/profondeur bornée, bibliothèque éditable et validation d’export ; les anciens droits restent concrets.
- [ ] **Caisses et compositions imbriquées** : objet ouvrable et Data Components, protocole de consommation/attribution durable et loot tables vanilla ; `TODO(Codex)` dans `NamedRewardTables`.
- [ ] **Fluides/énergie** : seuls les appels Java serveur de confiance `reportFluidTransferred`/`reportEnergyTransferred` après un vrai transfert alimentent les objectifs. Écrire et tester chaque adaptateur de mod technique ; ne pas compter une capacité ou un stock comme un transfert.
- [x] **Observation** : raycast serveur, bloc/entité/tags, durée continue, reset cible/dimension, progression et formulaires ; GameTest réel.
- [x] **Stages et toast** : objectif/condition/récompense de stage, fournisseur serveur remplaçable et tags vanilla par défaut ; toast traduisible reçu par client natif. Fournisseur d’un mod tiers toujours à intégrer au modpack.
- [x] **Dépendances et présentation** : commencée/quorum, sous-titres/tags/formes/échelle, quête optionnelle, réglages de visibilité/texte/détails/liens et toast ; masquage appliqué côté serveur.
- [x] **Groupes de chapitres** : champ validé `group`, bibliothèque Studio et groupes repliables natifs. Import des définitions/traductions de groupes FTB encore partiel.
- [ ] **Data Components** : prédicats exacts/partiels et sauvegarde des objets enchantés, renommés ou chargés lors des récompenses/imports/soumissions.
- [x] **Paramètres d’objectifs d’extension** : données `data` ≤16 KiB, validateur et prédicat Java obligatoires pour des données non vides, formulaire de descripteur ; sélecteurs de registres dans les trois types de formulaires. Test de collision entre types d’état.
- [ ] **Extensions dans les pools** : les pools acceptent objets, XP/niveaux, advancements, messages, stages et toasts intégrés. Les handlers tiers requièrent encore un contrat d’aperçu/validation explicite ; commandes et pools imbriqués restent rejetés.
- [ ] **Migration de progression** : aperçu par fingerprint, backup, acceptation administrateur explicite et rollback quand les IDs/étapes changent.

## P2 — migration et finition

- [ ] **FTB** : ancien SNBT, groupes de chapitres, traductions externes, décorations, Data Components, quorums complexes et objectifs/récompenses tiers. Conserver warning+skip lorsque la sémantique n'est pas préservée ; ne jamais supprimer silencieusement un prérequis.
- [x] **Revue d’import FTB** : rapport inline filtrable, IDs et exclusions explicites, téléchargement des sources, confirmation/annulation et undo. Parcours navigateur vérifié.
- [ ] **Revue avancée** : sélection partielle, aperçu comparatif par quête et restauration explicite après publication rejetée.
- [ ] **Édition en masse** : transformations d'objectifs, aperçu par quête, virtualisation, multi-sélection du graphe, copie entre catégories, tags et diagnostics de conflits ; préserver undo.
- [x] **Modèles portables** : snapshots dans les métadonnées projet/ZIP/Hub et copie explicite entre bibliothèque personnelle et projet ; couverture de roundtrip.
- [ ] **Modèles avancés** : revue de conflits/versionnement et médias externes portables.
- [ ] **Internationalisation globale** : les nouvelles interfaces récompenses sont traduites ; reprendre les anciens libellés encore anglais, dont les options projet et les messages d'import. Les textures externes manquantes ont un fallback mais peuvent produire des 404 navigateur.
- [ ] **Matrice de compatibilité** : serveur dédié 26.3, deux clients, Iris/shaders, registres divergents, sessions Hub et reprise des sauvegardes. Le test client de base n'est pas cette matrice complète.

Voir [comparatif FTB](ftb-quests-gap-analysis.md) et [preuves de validation](reviews/reward-integration/README.md).

## Validation de cette continuation

Revue indépendante : quatre défauts corrigés (collision d’états, `all_started`, SNBT sans virgules, collisions d’identifiants de stage). Test additionnel de 5 000 polls pendant une sauvegarde : les snapshots resondés ne saturent plus la file. Source et périmètre exacts : [comparatif FTB](ftb-quests-gap-analysis.md), [preuves](reviews/ftb-parity/README.md). La PR reste brouillon tant que les écarts bloquants ci-dessus sont ouverts.
