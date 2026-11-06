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
| Événements instantanés | Test exact cible/tag avant de construire le contexte, y compris cassage de bloc et élimination | Aucun snapshot lourd pour un événement **sans objectif correspondant** ; le contexte reste complet pour les événements retenus |
| Observation | Joueur inactif échantillonné tous les **3 ticks**, avec répartition par UUID ; observation d'un objectif reconnu contrôlée **à chaque tick** | Réduit les raycasts en l'absence d'observation active sans attribuer de durée sur des ticks non vérifiés |
| États d'observation | Zéros inchangés non renvoyés dans le moteur ; remise à zéro après changement de catalogue, reconnexion/mode de jeu | Évite l'application répétée de valeurs identiques, en préservant la remise à zéro des compteurs |

**Compromis intentionnel :** la détection du début d'observation peut prendre jusqu'à ~3 ticks (150 ms à 20 TPS). Après détection, chaque tick consécutif reste nécessaire pour compter la durée. Les objectifs de position/statistiques ont un échantillonnage plafonné à 8 joueurs/tick, donc leur latence peut être élevée si une seule JVM accepte des milliers de joueurs.

## Moteur de progression et persistance — deuxième passe

- Le registre maintient deux indexes immuables imbriqués **événement → cible exacte** et **événement → tag** : les événements ne créent plus de chaînes composites temporaires. Il renvoie directement la liste immuable de candidats pour un seul index ; les objectifs correspondant à plusieurs index restent dédupliqués. Le garde de `QuestRuntime.event()` utilise `hasCandidate()` sans créer de liste de résultats pour les événements non pertinents.
- `QuestEngine` partage les ensembles constants d'événements, calcule les prérequis une seule fois à l'intérieur de la validation d'une quête, vérifie les objectifs obligatoires en une seule boucle et n'utilise plus de Stream dans la boucle de détection des quêtes commencées. Les conditions, le verrouillage et les transitions de tiers restent évalués côté serveur.
- `PlayerProgress.copyForAutosave()` crée une copie indépendante de tous les champs persistés, y compris compteurs, cumuls, paliers et récompenses. Le thread Minecraft copie uniquement les structures de données mutables ; le **worker d'écriture** effectue ensuite sérialisation JSON, validation et accès disque.
- `QuestRuntime` répartit les autosauvegardes personnelles par lots de **24 états au maximum** et plafonne à **2 lots en attente**. L'inspection des joueurs sales est elle-même plafonnée à **96 UUID par passe**, avec rotation des joueurs bloqués ; les équipes sont inspectées à **16 UUID maximum** pour éviter de refaire un scan global permanent. Les sauvegardes non critiques d'équipe sont également séquencées. Le journal de transactions, les réservations de récompenses et les opérations d'inventaire restent sur la voie **transactionnelle et durable**, volontairement séparée.
- **Compromis important :** le snapshot des données est toujours effectué sur le thread Minecraft (il doit voir un état stable) ; une très grande progression individuelle peut encore occasionner une courte pause. Une sérialisation strictement hors thread ne signifie pas absence absolue de coût sur le tick. Les commits critiques gardent leurs garanties de crash/recovery.

### Profiler sans redémarrer Minecraft

Le script `scripts/profile-questplus.ps1` (fondé sur la [commande officielle JFR de Java 25](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html)) déclenche un enregistrement JFR **borné** (15 à 3 600 secondes) sur une JVM Java 25 déjà lancée :

```powershell
jcmd -l
.\scripts\profile-questplus.ps1 -ProcessId 12345 -DurationSeconds 120 -Settings profile -Output profiles/questplus.jfr
```

Sur Linux : `jcmd <PID> JFR.start name=QuestPlusProfile settings=profile duration=120s filename=/chemin/questplus.jfr`.
Ouvrir ensuite le fichier dans **Java Mission Control** ; vérifier les allocations associées aux méthodes `dev.questplus`, le thread serveur et les pauses GC. Utiliser `-Settings default` pour une capture moins intrusive. **Ne pas publier les traces JFR brutes**, qui peuvent inclure des noms de mondes, chemins et autres informations d'environnement. Les fichiers `*.jfr` et `profiles/` sont ignorés par Git.

Protocole : réaliser une capture **à vide**, tablette fermée ; une seconde avec quêtes/intérêts actifs ; une troisième avec 100 reconnexions et récompenses. Comparer au même modpack sans QuestPlus, à version de Java et monde identiques. Le script n'exécute pas les benchmarks à lui seul et ne prétend pas valider 3 000 joueurs.

## Nouvelles étapes P0/P1 à mesurer avant une affirmation « 3 000 joueurs »

1. **Profil JFR / spark / allocations** : comparer avec/sans QuestPlus sur un vrai modpack 500 mods. Mesurer p50/p95/p99 du tick, allocations par tick, CPU du serveur, disque et volume réseau par joueur. Éviter de fixer des pourcentages de gain sans mesures.
2. **Cache de contexte ciblé par condition** : le contexte générique fabrique encore inventaire, équipements et snapshots d'armure pour certaines requêtes ; bâtir une projection strictement requise par chaque événement, en gardant la compatibilité des extensions Java.
3. **Index d'objectifs actifs par joueur/équipe** : réduire les scans de quêtes déjà terminées et les observations inutiles. Cache invalidé par révision du progrès, recharge du catalogue et changement d'équipe. Conserver les conditions dynamiques et les quêtes répétables.
4. **Persistance — partie implémentée** : autosauvegardes personnelles sérialisées sur le worker et réparties en lots bornés ; mesurer encore le coût des copies en tick, les IOPS et les temps de latence d'une réservation lorsqu'un lot de 24 fichiers est en cours d'écriture. Ne jamais dégrader l'anti-duplication en échange de TPS.
5. **Protocole réseau** : mutualiser la sérialisation des définitions identiques au niveau génération, plafonner octets/tick et envois/joueur, vérification de version/sequence et reconstitution des fragments, tests de reconnexion. Le delta reste privilégié.
6. **Client moddé** : aucun scan permanent d'inventaire côté client, aucun layout ni recalcul de textures/annotations si tablette fermée, invalidation ciblée des caches de diagramme, rendu cullé, pas d'allocation superflue par frame. Mesurer avec Iris, shaders, 500 mods et petite configuration.
7. **Tests de charge** : 100/300 joueurs par nœud puis 3 000 sur plusieurs nœuds ; reconnexions simultanées, équipes de 64, 1 000 objectifs, progression intensive, changement de catalogue, disque lent, crash/restart et trafic client hostile.

## Audit communautaire et documentation (novembre 2026)

La recherche a été comparée au code réel, et non interprétée comme des gains chiffrés garantis.

- [spark, CPU/allocations et statistiques de tick](https://github.com/lucko/spark) : comparer un profil vanilla/modpack sans QuestPlus à **la même scène** avec QuestPlus, par captures CPU, `--alloc` et `--only-ticks-over`. Un TPS moyen de 20 ne suffit pas pour conclure que les pics ne sont pas perceptibles.
- [Discussion r/feedthebeast : saccades avec ~400 mods](https://www.reddit.com/r/feedthebeast/comments/1jdwi3r/) : un micro-freeze périodique peut venir d'un mod individuel ; isoler la cause par profilage plutôt que d'attribuer les micro-freezes au nombre de mods seul.
- [Discussion r/fabricmc : callbacks HUD enregistrés pendant le rendu](https://www.reddit.com/r/fabricmc/comments/vsgobw/) : ne jamais enregistrer un listener à chaque frame ; QuestPlus enregistre ses callbacks une seule fois à l'initialisation.
- [Fabric : networking](https://docs.fabricmc.net/develop/networking) : transmettre aux seuls clients concernés et valider chaque action du serveur.
- [Sodium 0.9.2](https://github.com/CaffeineMC/sodium/releases) : les allocations/tailles de buffers et le frame pacing peuvent dominer les ressentis avec shaders ; préserver la compatibilité avec le pipeline natif, pas de pipeline GL parallèle.

## Optimisations client supplémentaires et sécurité

- Le HUD épinglé conserve un modèle (textes déjà traduits, objectifs, icônes) entre deux révisions de progression. La taille d'affichage et les positions restent calculées à chaque frame pour suivre le viewport.
- Les badges de catégories sont indexés **une fois par delta de progression** ou changement des nouvelles quêtes, au lieu de deux parcours du registre complet par catégorie visible et par frame.
- Les notifications n'effectuent plus de copie de la carte entière de progression à chaque paquet ; seuls les identifiants modifiés sont comparés aux valeurs précédentes.
- L'assemblage client de paquets d'état est borné (nombre de fragments et nombre de caractères). Ne pas s'appuyer sur ce plafond pour charger un contenu non testé en conditions réelles.

**Expérience de sécurité invalidée :** une tentative pour omettre tout contexte d'inventaire/équipement sur certains objectifs *correspondants* a cassé le GameTest « casser du bois sans être accroupi ». Cette optimisation a été **retirée**, car le contexte de conditions et des quêtes multi-objectifs doit rester autoritatif. Les rejets d'événements *non correspondants* sont conservés. Toute nouvelle réduction du contexte devra compiler la dépendance de toutes les conditions et être validée par les GameTests.

### Critères de validation recommandés

Comparer la même version des mods, la même génération de monde et la même activité avec et sans QuestPlus. Conserver les courbes de **MSPT p50/p95/p99 et maximum**, les **allocations/sec client et serveur**, la **latence des paquets delta et snapshots**, le **p99 de frame time** (notamment pendant l'ouverture/fermeture de la tablette) et le **volume de sauvegarde/IOPS**. Au moins trois scénarios : tablette fermée (charge quasiment nulle), progression intensive, et 100 joueurs reconnectant simultanément. Des résultats avec 500 mods et 3 000 joueurs répartis restent à mesurer.

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
