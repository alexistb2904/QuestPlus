# Validation de la continuation — 5 novembre 2026

Checkout : `QuestPlus-p0-p2`, branche `feat/p0-p2-player-integration`, base `50490e4`. Le checkout IDE `QuestPlus` sur `main` et ses modifications locales sont conservés.

## Tests exécutés

| Vérification | Résultat |
| --- | --- |
| Java 25 : `gradlew.bat build integrationTest` | 177 tests unitaires ; 7 GameTests au total (6 QuestPlus + contrôle vanilla), sauvegardes de 140 UUID après arrêt vérifiées |
| `gradlew.bat runClientGameTest` | Client Minecraft 26.3 réel, paquets et clics dans la tablette native 3D ; choix indépendants, reçu concret, 19 XP exacts, stage attribué, toast reçu et rejeu refusé |
| Studio : `npm ci`, `npm run typecheck`, `npm test`, `npm run build` | 138 tests, typecheck et build réussis ; audit npm : zéro vulnérabilité |
| API : `npm ci`, `npm test` | 29 tests réussis ; audit npm : zéro vulnérabilité |
| Navigateur Playwright 1440×1000 et 390×844 | Présentation, création/liaison/édition de table, revue SNBT, annulation, confirmation et undo ; décalage horizontal corrigé et revérifié |

Logs de travail locaux dans `.tools/` (ignorés). Le build Vite conserve un avertissement de bundle >500 kB. Le navigateur signale des 404 du CDN de textures Minecraft, avec icônes de repli ; ce n’est pas une exécution navigateur sans erreurs console.

## Régressions couvertes

- Relecture vanilla : les preuves contenant le seul marqueur sont refusées ; un composant manquant empêche commit/reprise et conserve le journal. GameTest avec nom, données personnalisées et pile nommée en main secondaire.
- Journal d’inventaire : ancien/nouveau/mauvais marqueur, empreinte conflictuelle, écriture partielle joueur/équipe, fichier neuf interrompu avant rename et audit de résolution.
- Remise réelle : tags, 36 slots + main secondaire, vérification non consommatrice, refus créatif/doublon, quête infinie et dernier palier répétable. Les barrières bloquent toute nouvelle action pendant l’écriture.
- Observation réelle côté serveur : durée, changement de regard, complétion ; stage sondé depuis le fournisseur, sauvegarde vanilla et reconnexion. Cinq mille polls pendant une sauvegarde ne remplissent plus la file d’événements.
- Séparation des états : observer `minecraft:pig` ne valide pas un stage portant le même identifiant. Les données d’objectif d’extension sont validées et leur prédicat est exécuté.
- Projection : objectifs/récompenses/texte privés retirés tant que les règles de visibilité ne les autorisent pas.
- Tables : snapshots indépendants, propagation des références au prochain registre, noms inconnus et cycles refusés.
- FTB : vrai SNBT multiligne avec commentaires et virgules facultatives entre lignes, types bornés, `all_started` conservé comme AND, stages distincts encodés sans collision, cas non préservés et dépendants exclus explicitement.

## Revue de code

Revue indépendante en lecture seule : quatre défauts confirmés puis corrigés (mélange de types d’état, `all_started` en OR, SNBT sans virgules refusé, collisions des stages importés). Une revue dédiée du journal avait détecté le fichier neuf `.tmp` et le reset de compteur à la fin des cycles : les deux sont corrigés et testés.

## Captures

- [Choix tablette](tablet-choices.png), [reçu après attribution](tablet-toast-stage.png). La réception du toast et le stage serveur sont également contrôlés par assertions ; une capture seule ne prouve pas leur persistance.
- [Studio bureau](studio-desktop.png), [Studio 390 px](studio-mobile.png), [revue FTB](studio-import.png).

## Limites

Pas de certification deux clients réels/serveur dédié, shaders/Iris, adaptateurs fluides/énergie tiers, coupure électrique ou disque plein réels. Le journal simule les interruptions de processus ; la relecture vanilla compare le marqueur et les 37 piles avec leurs composants persistants, avant commit et à la reprise. Les codecs tiers ne sont pas couverts par les fixtures vanilla. Les barrières synchrones n’ont pas de budget de latence mesuré. Les effets partiellement livrés exigent une revue OP, sans réattribution automatique.

Les caisses ouvrables, Data Components d’authoring complets, thèmes externes, imports tiers et politiques restantes sont ouverts dans [le comparatif](../../ftb-quests-gap-analysis.md) et [le backlog](../../CODEX_TODO_P0_P2.md). Ces résultats ne couvrent pas toutes les fonctionnalités.

GitHub : Actions désactivé sur le dépôt (API `/actions/permissions`, `enabled=false`), aucun run distant pour cette branche à la livraison. PR #30 laissée en brouillon ; cette configuration n’a pas été modifiée.
