# Validation de la continuation — 5 novembre 2026

| Frontière | Commande / parcours | Résultat |
| --- | --- | --- |
| JAR universel Java 25 | `gradlew.bat build integrationTest` | Build réussi ; 191 tests unitaires, aucun échec ; 10 GameTests requis réussis ; sauvegardes et ledger de 140 UUID vérifiés |
| Client Minecraft 26.3 | `gradlew.bat runClientGameTest` | Réussi ; tablette, choix, attribution, rejeu, dimensions et ouverture/fermeture testés |
| Studio | `npm test`, `npm run typecheck`, `npm run build` | 145 tests réussis ; TypeScript et build réussis |
| API | `npm ci --no-audit --no-fund`, `npm test` | 29 tests réussis ; premier essai sans dépendance JSON5 installée échouait au démarrage |
| Import réel | `node scripts/verify-ftb-import.mjs` | 958/970 quêtes, 19 chapitres, 21 avertissements ; export/réimport validé |
| Navigateur | ZIP réel, revue à 1440×1000 et 390×844, confirmation | 958 quêtes, validation sans problème ; six nœuds du premier chapitre visibles après recentrage ; annulation disponible |
| Détecteur UI | Impeccable detect sur composants modifiés | Aucun signalement |

Captures de cette exécution : `output/playwright/ftb-import-desktop.png`, `ftb-import-mobile.png`, `ftb-import-result-confirmed.png` ; client : `build/run/clientGameTest/screenshots/`. Journaux locaux sous `.tools/*tests.log`.

Les tests ont révélé puis corrigé le recentrage après import et cinq alias de textures vanilla. Après correction, le navigateur n’a plus signalé d’erreur console dans le parcours de confirmation. Les textures des mods absents gardent l’icône de remplacement prévue.

Le build Vite prévient que le bundle principal dépasse 500 kB. Le client journalise une ancienne valeur de filtrage anisotrope et un retard ponctuel de ticks, sans échec du test. Pas de preuve de deux clients réels, de coupure électrique, de disque plein, de shaders ni du fonctionnement complet du modpack source. Les limites d’import restantes sont détaillées dans [compatibilité](../../ftb-migration-compatibility.md). Task Screens exclus volontairement.
