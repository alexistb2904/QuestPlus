# Windows - tester QuestPlus

Ouvrez **0-Menu-Tests.bat** a la racine du depot : 23 options sont disponibles.
Chaque lanceur peut aussi etre demarre independamment depuis l'Explorateur.

## Tests automatiques

| Option | Controle |
| --- | --- |
| 3 | Build Java, ressources, JAR Fabric et tests JUnit |
| 4 | GameTests serveur dedie et verification de la sauvegarde |
| 5 | GameTests client avec fenetre Minecraft graphique |
| 8 | React tests, TypeScript, build Vite et tests HTTP API |
| 10 | Suite complete SANS GameTests client GUI |
| 11 | Verification JDK 25, Node 22+, fichiers, Docker facultatif |
| 13 | Typecheck TypeScript puis build React |
| 14 | Tests React uniquement |
| 15 | Tests API : approbation, packs, publication et securite |
| 17 | Tests JUnit seulement |
| 18 | Compiler uniquement Java + ressources |
| 22 | Suite complete AVEC GameTests client GUI |

L'option 10 continue apres chaque echec et ecrit un bilan dans
`run/questplus-test-summary.txt`. La sortie reste non nulle si une
etape a echoue. Ouvrez les rapports avec l'option 21.

## Test reel web + Minecraft

1. **12** : generer une cle locale persistante et le fichier
   `run/server/world/questplus-hub.json`. Les fichiers sont ignores par Git.
   Les fichiers existants ne sont jamais ecrases et le secret n'est pas affiche.
2. **9** : demarrer Quest Studio + API sur http://localhost:8787/.
   Garder cette fenetre ouverte : le processus HTTP tourne en premier plan.
3. **16** dans une autre fenetre : GET /health pour verifier l'API.
4. **2** : lancer le serveur Minecraft local apres lecture/acceptation
   de la Minecraft EULA. La config de developpement utilise 127.0.0.1
   et le mode hors ligne, a ne jamais exposer sur Internet.
5. **1** : lancer le client Minecraft, puis rejoindre localhost:25565.
6. En etant OP, executer `/questplus editor`, ouvrir le lien sur le site,
   demander l'autorisation et valider le code en jeu.
7. Charger les quetes, editer un objectif, publier et verifier la tablette.
   Tester aussi l'import d'un pack partage et `/questplus rollback`.

Pour une validation approfondie, verifier les refus d'autorisation, les
conflits de versions, un pack JSON invalide, une coupure reseau et la
progression joueur apres rechargement. Ces tests manuels ne sont PAS
remplaces par les tests unitaires.

## Autres options

| Option | Action |
| --- | --- |
| 6 | Installer Java 25 via winget apres confirmation |
| 7 | Lancer Vite + HMR |
| 9 | Demarrer le site integre React + API |
| 12 | Configuration de developpement local securisee |
| 16 | Healthcheck API locale |
| 19 | Preview Vite production |
| 20 | Construire l'image Docker web sans deployer |
| 21 | Consulter les rapports et le bilan |
| 23 | Reinstaller proprement les dependances (npm ci) |

Commandes disponibles directement sous Windows :

```bat
10-Tout-Tester.bat
10-Tout-Tester.bat gui
scripts\_qa.bat studio-test
scripts\_qa.bat studio-build
scripts\_qa.bat api-test
scripts\_qa.bat java-build
scripts\_qa.bat server-test
scripts\_qa.bat api-health
```

Le JDK 25 et Node 22+ sont obligatoires pour toute la suite ; Docker est
facultatif. Les nouveaux scripts ne touchent pas aux donnees d'une
sauvegarde de production. La validation finale des .bat doit etre
effectuee sur une machine Windows avec Fabric 26.3 et un vrai client.

## Lancement complet en un clic (option 24)

`13-Demarrer-Tout.bat` lance le frontend React et l'API dans une
console, puis attend la disponibilite HTTP avant de lancer le serveur
Fabric dans sa console, attend l'ouverture de `localhost:25565`, et
demarre une instance client Minecraft dans une troisieme console.
La configuration de developpement est creee/reutilisee automatiquement.

En cas de premier demarrage, **lisez et acceptez manuellement l'EULA**
dans la console serveur. Aucune acceptation n'est automatique.
Les ports 8787 et 25565 doivent etre libres. Les fenetres restent
ouvertes pour afficher les logs. Pour arreter proprement, quitter le
client, taper `stop` dans la console du serveur, puis Ctrl+C dans
celle du web. Aucun serveur de production n'est modifie.
