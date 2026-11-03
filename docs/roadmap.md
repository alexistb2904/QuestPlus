# Roadmap

La version 0.1.0 livre les fondations et une première tranche verticale. Les cases vides sont des travaux futurs, pas des fonctionnalités disponibles.

## Phase 1 — Fondations

- [x] Projet Fabric 26.3 / Java 25 avec versions épinglées et wrapper Gradle.
- [x] Core Java indépendant, couche Fabric serveur et persistance JSON séparée.
- [x] Définitions indépendantes, JSON strict, schema versionné, index et DAG général.
- [x] Anglais/français, fallback anglais par champ et placeholders littéraux.
- [x] Génération sans écrasement de six quêtes et quatre catégories ; rechargement validé.
- [x] Configuration native Codex et quatre agents avec deux sous-agents simultanés maximum.

## Phase 2 — Première tranche fonctionnelle

- [x] Blocs cassés, kills attribués au joueur, ramassage exact d'entités objets et tags.
- [x] Conditions dimension/outil/santé/accroupissement et AND/OR/NOT ; objectifs multiples combinés par AND.
- [x] Compteurs, dépendances, complétion unique et données personnelles par UUID.
- [x] Récompenses XP/objets multiples, réclamation manuelle et ledger préservé par reset.
- [x] Commandes de progression, grant/reset, debug et reload ; UUID hors ligne pour administration.
- [x] Sauvegardes différées, atomiques, sauvegarde précédente, validation stricte et vidage à l'arrêt.
- [ ] Élargir les conditions : équipement, effets, biome, position et contexte personnalisé.
- [ ] Possession actuelle distincte des acquisitions, fabrication/cuisson et autres sources d'obtention.
- [ ] Extension enregistrable de nouveaux types de conditions/objectifs/récompenses ; l'API actuelle accepte seulement les trois événements existants.

## Phase 3 — Équipes

- [x] Contrat documenté : autorité, adhésion unique, maximum/union, bénéficiaires et journal entre fichiers.
- [ ] Gestion effective, invitations expirantes, transfert et commandes Brigadier.
- [ ] Progression collective, fusion rétroactive, instantanés de départ/expulsion/dissolution et hors ligne.
- [ ] Transactions crash-consistantes et migration du ledger par bénéficiaire.
- [ ] Tests ciblés d'adhésion, fusion, réadhésion, interruptions et exploits de récompenses.

## Phase 4 — Progressions avancées

- [ ] Répétables/infinies, cycles identifiés, paliers et politiques de dernier palier.
- [ ] Compteurs cumulatifs/par cycle, objectifs composites OR/séquentiels et tentatives limitées dans le temps.
- [ ] Version suivante du schéma et migrations validées des sauvegardes.

## Phase 5 — Événements avancés

- [ ] Pêche, élevage/apprivoisement, échanges, placements/interactions, exploration, structures et distance.
- [ ] Registre extensible des événements de mods tiers ; déduplication adaptée à chaque source.

## Phase 6 — Narration

- [x] Politique d'apparition par instance de structure et reprise documentées.
- [ ] Quêtes informatives, PNJ configurables, décisions persistées, dialogues et événements narratifs.

## Phase 7 — Stabilisation

- [x] Tests principaux du core, des JSON, du fallback et des sauvegardes.
- [x] Revue critique statique indépendante ; corrections de la saturation à l'arrêt et des récompenses AIR.
- [x] GameTest sur serveur dédié : casse/tag/NOT, kills via dégâts de flèche, ramassage partiel, inventaire plein,
  permissions, rechargement invalide, récompense AIR rejetée, réclamation concurrente et après reset/grant.
- [x] Vérification des 140 sauvegardes UUID après arrêt et du ledger de récompenses livré.
- [ ] Essai avec deux vrais clients, reconnexions et parties longues.
- [ ] Coupure brutale du processus, panne disque simulée et reprise après interruption réelle.
- [ ] Profilage charge multijoueur, cache avec éviction si nécessaire et catalogue volumineux.
- [ ] Migration automatisée d'IDs/schémas, restauration collective et validation des extensions.

Le frontend, la tablette 3D, les animations et l'éditeur web attendent la validation du moteur.
