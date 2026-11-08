# Notes de version

## 1.1.0 — 8 novembre 2026

### Les quêtes prennent place dans votre monde

- **Quest Board** : construisez un tableau mural modulaire pour afficher les quêtes épinglées, leur progression et les classements des joueurs, des équipes ou des contributions.
- **Submission Station** : reliez une station à un objectif de dépôt et alimentez-la avec des objets, des fluides ou de l'énergie. Configurez les faces d'entrée, l'automatisation et la poursuite des paliers. Le transfert d'énergie nécessite la Team Reborn Energy API et une source compatible.
- **Quest Relay** : pilotez vos circuits redstone selon l'état des quêtes, des paliers ou des stages, avec une sortie binaire, une impulsion ou un signal analogique.
- Les trois blocs disposent de recettes, de modèles et textures dédiés, ainsi que d'écrans de configuration intégrés au style Minecraft.
- La tablette physique dispose désormais d'une recette de fabrication.

### Des équipes plus souples

- Trois grades : **membre**, **gestionnaire** et **propriétaire**, avec des permissions adaptées.
- Gérez les invitations, les grades, le nom de l'équipe et le transfert de propriété depuis la tablette ou les commandes.
- Activez ou désactivez la progression partagée tout en conservant votre équipe. Le partage reste activé par défaut.
- Les changements de partage conservent les historiques sans additionner les anciennes contributions ni accorder de récompenses rétroactives. L'historique des récompenses reste attaché à son bénéficiaire.

### Fiabilité et documentation

- La station conserve sa configuration et ses ressources lorsqu'elle est cassée en survie ; son solde sauvegardé ne peut être restauré qu'une fois.
- Les dépôts et les actions d'équipe sont validés côté serveur. Les objets incompatibles et les quantités excédentaires sont refusés.
- Nouveaux tests pour les blocs, les équipes, les sauvegardes et les interfaces de configuration.
- Guides illustrés pour les blocs, documentation des grades et du partage, et mise à jour des commandes.

### Compatibilité

- Minecraft **26.3**, Fabric Loader **0.19.5** minimum et Fabric API **0.161.0+26.3** minimum.
- Un seul JAR QuestPlus pour le client et le serveur. Les identifiants des quêtes et la version du schéma restent inchangés.
- Pour déplacer ou sauvegarder un monde utilisant les stations, conservez aussi son dossier `data/questplus/blocks`.
