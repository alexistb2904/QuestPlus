# Quest Plus — Roadmap backend (audit du 3 novembre 2026)

**Référence :** branche `main`, version Gradle `0.2.0`. La précédente roadmap restait sur les cases de la 0.1.0 alors que les sources incluent déjà équipes, quêtes avancées et GameTests.

**Notation :** [x] code présent ; [ ] travail restant. Une case [x] n'est **pas** une garantie de validation sur plusieurs clients réels. Aucun développement de frontend, tablette, Blockbench ni animation dans ce suivi.

## Phase 1 — Fondations

- [x] Fabric Minecraft 26.3 / Java 25, wrapper Gradle et serveur dédié.
- [x] Core Java indépendant des imports Minecraft, adaptateur Fabric et persistance JSON.
- [x] Quêtes JSON individuelles, validation stricte, identifiants, DAG, catégories et chargement transactionnel du registre.
- [x] Textes anglais/français, fallback `en_us` au champ, exemples installés sans écraser les fichiers.
- [x] Configuration des rôles Codex et documentation technique.

## Phase 2 — Moteur de quêtes

- [x] Blocs cassés, entités tuées et ramassage réel d'objets avec tags.
- [x] Objectifs multiples `all`/`any`/`sequence`, prérequis AND/OR/NOT, compteurs et progression initiale implicite.
- [x] Conditions santé, outil, dimension, biome, effets, équipement, position, contexte personnalisé, accroupissement.
- [x] Quantité d'inventaire actuellement détenue, séparée des événements de ramassage.
- [x] Récompenses XP/objets, droits personnels et ledger de réclamation, protection contre les doubles réclamations.
- [x] Fichiers `data/questplus/players/<uuid>.json`, v2, snapshots différés, écritures atomiques et backups.
- [x] Commandes backend, reload validé et API d'extensions.
- [ ] Vérifier en gameplay réel toutes les variantes d'objets, tags moddés et sources d'acquisition, et corriger les divergences.

## Phase 3 — Équipes

- [x] Création, invitation expirante, affichage des invitations reçues, adhésion unique, refus, départ, expulsion, transfert, dissolution.
- [x] Progression partagée ; union rétroactive des complétions, maximum des compteurs compatibles.
- [x] Départ/expulsion/dissolution avec instantané personnel, y compris pour les membres hors ligne.
- [x] Droits aux récompenses par bénéficiaire, fusion historique sans double octroi.
- [x] Fichiers par équipe et journal de transaction multi-fichiers avec récupération au démarrage.
- [x] Tests unitaires et GameTest serveur simulant fusion, déconnexion, commandes et réadhésion.
- [ ] Validation bout en bout avec deux vrais clients, déconnexions et reconnexions.
- [ ] Injection de crashs du processus et d'échecs disque en conditions serveur réalistes.

## Phase 4 — Progression avancée

- [x] Quêtes classiques, répétables, infinies et à paliers distincts.
- [x] Délais, tentatives chronométrées, `noDeath`, objectifs composites et séquentiels.
- [x] Compteurs par tentative et historiques cumulés, sauvegardes v1 -> v2.
- [ ] Définir/implémenter le traitement des excédents d'un gros événement à travers plusieurs cycles :
      actuellement **un seul cycle maximum par événement**, surplus non reporté.
- [ ] Étendre les règles de fusion pour les tentatives chronométrées/séquentielles quand les règles de jeu le nécessitent.

## Phase 5 — Événements avancés

- [x] Hooks code : placement, utilisation/consommation, fabrication, cuisson, pêche, échanges, apprivoisement, élevage, interactions.
- [x] Échantillonnage serveur borné : biomes, dimensions, structures, distance, coordonnées et inventaire.
- [x] Enregistrement d'événements/conditions/récompenses par des mods serveur de confiance.
- [x] GameTest sur plusieurs vrais hooks et tests unitaires des règles avancées.
- [ ] Valider particulièrement visites de structures, pêche d'objets, contexte moddé et sources indirectes en partie multijoueur.
- [ ] Profiler les vérifications d'inventaire et l'échantillonnage exploration sous charge.

## Phase 6 — Narration / PNJ

- [x] Champ narratif traduit et événement manuel de complétion disponibles côté moteur.
- [x] Règles d'apparition probabiliste par instance de structure spécifiées dans `docs/architecture.md`.
- [x] Modèle JSON de quête **purement informative** v2, avec `acknowledgement: manual/read_only` et objectif d'acquittement virtuel.
- [ ] Brancher l'acquittement à la lecture/validation de la modale lors du futur développement client.
- [ ] NPC Manager réellement fonctionnel : définitions JSON, chances par structure/biome/dimension, décisions persistantes et spawn contrôlé.
- [ ] Interactions de dialogues, choix, conséquences et tests de reprise après redémarrage.

## Phase 7 — Qualité et stabilisation

- [x] Tests JUnit du core, de la persistance, des équipes et des progressions avancées.
- [x] Tests GameTest serveur pour des événements vanilla, récompenses et scénarios d'équipe.
- [x] Schéma JSON de définitions et documentation des règles.
- [ ] Refaire `./gradlew build integrationTest` sur la **dernière révision** et consigner les rapports : non exécuté depuis cet environnement d'audit.
- [ ] Test réel deux clients ; tests sur de longues sessions et serveurs moddés.
- [ ] Tests d'arrêt forcé à chaque point critique des transactions et des réservations de récompenses.
- [ ] Profilage CPU/mémoire avec grand catalogue et beaucoup de joueurs, éviction éventuelle du cache UUID.
- [ ] Politique de migration d'ID de quête et de schémas futurs, restauration coordonnée d'équipes/sauvegardes.
- [ ] Tests de sécurité des extensions tierces et de compatibilité Fabric/versions.

**Hors périmètre :** interface graphique, modèle Blockbench, animations, shaders et éditeur web.
