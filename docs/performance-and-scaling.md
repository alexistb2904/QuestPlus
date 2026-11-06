# QuestPlus — budget de performance & synchronisation à grande échelle

## Périmètre et règle d'autorité

Objectif de dimensionnement : **3 000 joueurs simultanés au total**, répartis sur plusieurs serveurs Minecraft, et clients dans des modpacks de **400–500 mods**. **Ce n'est pas une garantie de 3 000 joueurs sur une seule JVM Minecraft** : la charge vanilla, les chunks, les entités et les mods dominent le temps du thread serveur. Tester chaque nœud avec sa vraie liste de mods.

- **Client** : dessin de tablette/HUD, recherche, mise en page, animations, préférences et caches de présentation. Aucun calcul de récompense ou de progression faisant autorité.
- **Serveur Minecraft** : événements attestés par le jeu, conditions/objectifs, droits, inventaires, groupes, validation des actions et sauvegardes. Aucun aller-retour HTTP par événement de gameplay.
- **Quest Studio Hub (Node)** : sessions d'édition temporaires, catalogue et publication. **Ce n'est pas, à ce jour, un stockage de progression interserveur** ; le connecter aux 3 000 joueurs en temps réel serait une erreur d'architecture.

## Optimisations incluses dans cette branche

| Sujet | Comportement | Justification |
| --- | --- | --- |
| Présence réseau | JOIN/DISCONNECT invalident le joueur et les membres de son équipe, **pas toute la population** | Évite un fan-out O(joueurs) à chaque connexion ; le chargement de la progression reste côté serveur |
| Projections | Snapshots complets au premier sync/à la génération ou visibilité modifiée, puis deltas ; max **12 destinataires synchronisés par tick** | Coalescence équitable et traitement borné des vagues de connexions ; un full sync forcé par le client est mis en file et limité |
| Récompenses automatiques | Maximum **16 joueurs vérifiés par tick** dans une rotation équitable ; retry sur passages suivants | Supprime le pic de scan de toute la population une fois par seconde ; le délai de traitement croît sous forte charge |
| Événements avancés | Arrêt de l'échantillonneur si aucun objectif correspondant ; cache du contexte par échantillon, cible indexée au reload | Évite inventaire + équipements + effets recréés pour chaque cible et pour les mods sans objectifs avancés |
| Événements instantanés | Test exact cible/tag avant de construire le contexte, y compris cassage de bloc et élimination | Aucun snapshot lourd pour un événement qui ne correspond à aucune quête |
| Observation | Joueur inactif échantillonné tous les **3 ticks**, avec répartition par UUID ; observation d'un objectif reconnu contrôlée **à chaque tick** | Réduit les raycasts en l'absence d'observation active sans attribuer de durée sur des ticks non vérifiés |
| États d'observation | Zéros inchangés non renvoyés dans le moteur ; remise à zéro après changement de catalogue, reconnexion/mode de jeu | Évite l'application répétée de valeurs identiques, en préservant la remise à zéro des compteurs |

**Compromis intentionnel :** la détection du début d'observation peut prendre jusqu'à ~3 ticks (150 ms à 20 TPS). Après détection, chaque tick consécutif reste nécessaire pour compter la durée. Les objectifs de position/statistiques ont un échantillonnage plafonné à 8 joueurs/tick, donc leur latence peut être élevée si une seule JVM accepte des milliers de joueurs.

## Nouvelles étapes P0/P1 à mesurer avant une affirmation « 3 000 joueurs »

1. **Profil JFR / spark / allocations** : comparer avec/sans QuestPlus sur un vrai modpack 500 mods. Mesurer p50/p95/p99 du tick, allocations par tick, CPU du serveur, disque et volume réseau par joueur. Éviter de fixer des pourcentages de gain sans mesures.
2. **Cache de contexte ciblé par condition** : le contexte générique fabrique encore inventaire, équipements et snapshots d'armure pour certaines requêtes ; bâtir une projection strictement requise par chaque événement, en gardant la compatibilité des extensions Java.
3. **Index d'objectifs actifs par joueur/équipe** : réduire les scans de quêtes déjà terminées et les observations inutiles. Cache invalidé par révision du progrès, recharge du catalogue et changement d'équipe. Conserver les conditions dynamiques et les quêtes répétables.
4. **Persistance** : examiner le coût de sérialisation JSON sur le thread de jeu et les transactions de récompenses à synchronisation forte. Coalescer les écritures non critiques, donner la priorité aux transactions de remise, borner file mémoire et backpressure ; ne jamais dégrader l'anti-duplication en échange de TPS.
5. **Protocole réseau** : mutualiser la sérialisation des définitions identiques au niveau génération, plafonner octets/tick et envois/joueur, vérification de version/sequence et reconstitution des fragments, tests de reconnexion. Le delta reste privilégié.
6. **Client moddé** : aucun scan permanent d'inventaire côté client, aucun layout ni recalcul de textures/annotations si tablette fermée, invalidation ciblée des caches de diagramme, rendu cullé, pas d'allocation superflue par frame. Mesurer avec Iris, shaders, 500 mods et petite configuration.
7. **Tests de charge** : 100/300 joueurs par nœud puis 3 000 sur plusieurs nœuds ; reconnexions simultanées, équipes de 64, 1 000 objectifs, progression intensive, changement de catalogue, disque lent, crash/restart et trafic client hostile.

## Si la progression doit être partagée entre *plusieurs serveurs Minecraft*

Cette fonctionnalité **n'existe pas actuellement** et ne doit pas être simulée en copiant périodiquement les fichiers JSON entre nœuds. Contrat recommandé :

- Chaque joueur/équipe a un **propriétaire d'écriture unique** ou une transaction atomique sur un service de progression partagé. Les transferts de propriété sont explicites ; aucune attribution de récompense sur un état éventuellement cohérent.
- Les serveurs Minecraft émettent des **changements de progression durables** (ID événement unique, UUID sujet, séquence/révision, ID objectif, compteur et version catalogue), regroupés par nœud avec déduplication, compression et quotas. **Jamais** une requête HTTP par tick, bloc cassé ou joueur.
- Une **file asynchrone bornée** envoie hors thread du tick avec délai/coalescence (par exemple 1–5 s pour la progression non critique), backoff exponentiel et reprise idempotente. Si déconnecté, les remises critiques attendent une confirmation durable : on n'émet pas une récompense sur une écriture réseau incertaine.
- En présence d'équipes transverses, versionner ensemble progressions, claims et membres concernés. Les versions incompatibles de catalogue sont rejetées plutôt que fusionnées à l'aveugle.
- La synchronisation vers chaque client utilise **le réseau Minecraft existant**, seulement vers les joueurs concernés, en deltas coalescés. Aucun client ne reçoit des identifiants ou secrets Hub ni n'impose des compteurs au serveur.
- Prévoir authentification des nœuds, scopes, clés par serveur, TLS, limites de taille, rate limits, logs métriques sans données privées et arrêt sûr en cas de surcharge.

**À ne pas confondre :** le Hub actuel ne poll que pendant une session d'édition active. Il n'a pas à traiter les 3 000 événements de joueurs et ne remplace pas une base de progression transactionnelle.

## Validation

Les modifications de cette branche doivent passer les tests Gradle/unitaires et GameTests (observations, équipes, récompenses et connexion/déconnexion), puis tests client + serveur dédiés. Les mesures « 3 000 joueurs / 500 mods » sont **à réaliser**, pas réputées passées. Le workflow GitHub Actions de la branche principale a réussi le 6 novembre 2026 ; il ne constitue pas une mesure de charge ni une validation de cette branche. Vérifier les checks de la PR et compléter avec une mesure sur serveur dédié.
