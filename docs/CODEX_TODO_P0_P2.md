# Quest Plus — suivi Codex P0–P2

Mise à jour du 5 novembre 2026, après les PR #27/#29 et l'intégration des récompenses dans les interfaces.
Une case cochée correspond aux limites de validation décrites ci-dessous. Le projet ne revendique pas encore une parité complète avec FTB Quests.

## Vérifié sur le checkout

- [x] Java 25 : `gradlew.bat build integrationTest` ; 161 tests unitaires, six GameTests serveur requis, contrôle des sauvegardes des 140 UUID après arrêt.
- [x] Client réel : `gradlew.bat runClientGameTest` ; clics dans la tablette 3D, deux choix indépendants, confirmation persistée, attribution et rejet d'un rejeu. Captures dans `docs/reviews/reward-integration/`.
- [x] Studio : `npm ci`, `npm run typecheck`, `npm test` (129), `npm run build` ; API : `npm ci`, `npm test` (29). Lanceur Windows `scripts/_qa.bat api-test` vérifié.
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

- [ ] **Atomicité des remises d'objets** : `QuestRuntime.submitItem` modifie l'inventaire puis persiste la progression. Le JSON et la sauvegarde vanilla ne forment pas une transaction. Concevoir journal, réservation durable et récupération coordonnée avec la sauvegarde joueur ; tester arrêt brutal avant/après retrait, disque plein, déconnexion et réentrance. Aucun claim d'exactly-once pour cette fenêtre.
- [ ] **Récupération des claims réservés** : la réservation durable empêche un rejeu, mais une déconnexion/inventaire modifié/exception après réservation exige une revue administrateur. Ajouter une commande de résolution auditée ; jamais de nouvelle attribution automatique après un effet partiel. `TODO(Codex)` conservé dans `QuestRuntime.claim`.
- [ ] **Deux clients réels sur serveur dédié jetable** : la couverture actuelle utilise des joueurs simulés dans le serveur Fabric et un client réel avec serveur intégré. Tester objets moddés, fluides, redémarrages, publication Hub/backup/rollback et packs malveillants/surdimensionnés.
- [ ] **Parité complète d'authoring** : les nouvelles récompenses ont un roundtrip partagé ; étendre ce contrat à tous les événements, quantités, conditions, descripteurs et exports de migration.

## P1 — outils de création et intégrations

- [x] Éditeur graphique commun aux récompenses normales et aux paliers : ajouter/supprimer/réordonner, choix, random/loot/all, poids, tirages, vide, pourcentages, simulation sans attribution, sélecteurs d'objets/advancements et champs des récompenses d'extension.
- [x] Titres d'options et descriptions de choix traduisibles, aperçu de cartes, erreurs bloquant l'export. Nouveaux libellés anglais/français/espagnol/chinois ; contrôles clavier natifs et vérification visuelle bureau/390 px.
- [x] Tables nommées portables dans les métadonnées du projet et du Hub, insérées comme **copies indépendantes** dans les quêtes. Les tables imbriquées, références circulaires et commandes dans les pools sont rejetées.
- [ ] **Tables référencées dynamiquement côté serveur** : la bibliothèque actuelle matérialise des snapshots inline, sans propagation d'une modification aux copies déjà insérées. Définir des références versionnées, l'intégrité, les cycles et la résolution bornée avant d'ajouter des références vivantes.
- [ ] **Fluides/énergie** : seuls les appels Java serveur de confiance `reportFluidTransferred`/`reportEnergyTransferred` après un vrai transfert alimentent les objectifs. Écrire et tester chaque adaptateur de mod technique ; ne pas compter une capacité ou un stock comme un transfert.
- [ ] **Observation** : raycast serveur, cible bloc/entité, durée de regard, dimension et anti-spam.
- [ ] **Stages** : intégration optionnelle et versionnée ; objectifs/conditions/récompenses. Aucun lien dur à un mod absent.
- [ ] **Data Components** : prédicats exacts/partiels et sauvegarde des objets enchantés, renommés ou chargés lors des récompenses/imports/soumissions.
- [ ] **Extensions** : les conditions/récompenses ont des formulaires de champs ; compléter les pickers de conditions et les paramètres d'objectifs. `Objective` ne possède pas de champ arbitraire `data` : concevoir d'abord validation et contrat Java. Ne pas afficher des champs qui ne seraient jamais utilisés par le moteur.
- [ ] **Récompenses d'extension dans les pools** : les pools acceptent uniquement objets, XP, niveaux, advancements et messages. Ajouter un contrat de validation/aperçu sûr avant d'autoriser d'autres handlers ou des pools imbriqués.
- [ ] **Migration de progression** : aperçu par fingerprint, backup, acceptation administrateur explicite et rollback quand les IDs/étapes changent.

## P2 — migration et finition

- [ ] **FTB** : ancien SNBT, groupes de chapitres, traductions externes, décorations, Data Components, quorums complexes et objectifs/récompenses tiers. Conserver warning+skip lorsque la sémantique n'est pas préservée ; ne jamais supprimer silencieusement un prérequis.
- [ ] **Rapport d'import FTB** : le JSON téléchargeable et la confirmation existent ; ajouter une liste inline filtrable par gravité, sélection/aperçu comparatif et restauration explicite après publication rejetée. Sources originales inchangées.
- [ ] **Édition en masse** : transformations d'objectifs, aperçu par quête, virtualisation, multi-sélection du graphe, copie entre catégories, tags et diagnostics de conflits ; préserver undo.
- [ ] **Modèles de quêtes portables** : les modèles personnels restent locaux au navigateur. Ajouter stockage projet, revue d'import, versionnement, traductions et médias portables.
- [ ] **Internationalisation globale** : les nouvelles interfaces récompenses sont traduites ; reprendre les anciens libellés encore anglais, dont les options projet et les messages d'import. Les textures externes manquantes ont un fallback mais peuvent produire des 404 navigateur.
- [ ] **Matrice de compatibilité** : serveur dédié 26.3, deux clients, Iris/shaders, registres divergents, sessions Hub et reprise des sauvegardes. Le test client de base n'est pas cette matrice complète.

Voir [comparatif FTB](ftb-quests-gap-analysis.md) et [preuves de validation](reviews/reward-integration/README.md).
