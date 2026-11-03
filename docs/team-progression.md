# Progression des équipes — contrat et implémentation 0.2.0

**Statut : implémenté, avec tests unitaires et GameTest serveur ; validation sur deux clients réels encore à effectuer.**
`TeamManager` fournit la création, les invitations expirantes, la découverte des invitations reçues, l'adhésion,
le départ, l'expulsion, le transfert et la dissolution. `QuestRuntime` relaie les événements à la progression
partagée et persiste les opérations multi-fichiers par journal. Les règles ci-dessous décrivent
à la fois les garanties du code actuel et les limites qu'il reste à vérifier expérimentalement.

## Autorité et appartenance

Une progression appartient soit à un UUID individuel, soit à un ID d'équipe stable. Pendant l'appartenance, l'état collectif fait autorité ;
les événements des membres en ligne le modifient sur le thread serveur, et les membres hors ligne retrouvent cet état à leur retour.
Le fichier personnel conserve l'état de séparation et le registre des récompenses propre au bénéficiaire ; pas de copie collective à chaque action.

Le joueur n'appartient qu'à une équipe. Création et adhésion échouent s'il en possède déjà une. Le propriétaire invite, expulse,
transfère ou dissout ; un membre quitte ou refuse une invitation. Les invitations auront un UUID, un destinataire et une échéance absolue serveur,
une limite par équipe et une consommation transactionnelle. Les commandes vérifieront l'identité de l'appelant et son rôle côté serveur.
Le dernier propriétaire doit transférer ou dissoudre avant de quitter ; pas d'équipe sans propriétaire.

## Fusion déterministe

| Donnée | Règle prévue |
|---|---|
| Quêtes terminées `once` | Union des identifiants ; jamais supprimer une complétion |
| Objectifs partiels compatibles | Maximum par objectif stable et même empreinte mécanique ; jamais addition des historiques |
| Objectifs incompatibles | Conserver les sources pour diagnostic/migration ; ne pas inventer de conversion |
| Dépendances | Réévaluer à partir de l'union des complétions ; un déblocage ne constitue ni un objectif accompli ni un paiement |
| Paliers | Union des paliers atteints et maximum du compteur cumulatif compatible ; conserver les droits/consommations par bénéficiaire |
| Répétitions | Ne pas fusionner aveuglément des numéros de cycle locaux : ils ne désignent pas forcément le même accomplissement |
| Objectifs composites | Fusionner leurs feuilles compatibles ; préserver l'ordre des objectifs séquentiels quand cette fonctionnalité sera définie |
| Objectifs chronométrés/sans mort | Ne pas combiner deux tentatives partielles ; conserver les complétions acquises, démarrer une tentative collective explicite |

Exemple : A possède 10 identifiants terminés, B en possède 5 distincts. À l'adhésion de B, l'équipe reconnaît immédiatement les 15,
pour tous les membres, même hors ligne. Si certains identifiants sont communs ils ne comptent qu'une fois.
Les nouveaux événements sont ensuite cumulés normalement dans la progression commune.

Au départ ou à l'expulsion, copier un instantané indépendant de l'état collectif dans l'état personnel, en conservant son ledger bénéficiaire.
L'équipe garde son propre état. À la dissolution, effectuer cette séparation pour chaque membre, y compris hors ligne.
Une réadhésion applique les mêmes règles de maximum/union, sans additionner les événements copiés lors d'un départ précédent.

## Récompenses

Complétion, déblocage, droit à récompense et paiement sont quatre états distincts. Une fusion historique reconnaît une complétion,
mais n'émet pas de nouveau droit par défaut. Quitter et rejoindre plusieurs équipes ne réinitialise jamais le ledger personnel.
La clé d'une récompense devra inclure le bénéficiaire, l'ID de quête et l'identifiant stable du cycle/palier/droit.
Les droits créés par une nouvelle complétion collective devront définir explicitement quels membres en bénéficient, y compris les déconnectés.
Une politique configurable pourra changer ces bénéficiaires ; elle ne devra jamais effacer les paiements antérieurs.

Le ledger `claims` actuel est une base **pour les quêtes once individuelles uniquement**. L'arrivée des équipes doit le migrer avant
d'introduire des bénéficiaires et des droits explicites. Les réservations durables ne doivent pas être rejouées après crash sans contrôle :
l'inventaire Minecraft n'est pas transactionnel avec les JSON Quest Plus.

## Cohérence entre fichiers

Un renommage atomique ne suffit pas pour modifier simultanément `players/<uuid>.json`, `teams/<id>.json` et `world.json`.
Prévoir un journal d'opération durable avec ID, préconditions et snapshots/versions attendues. Valider les données, écrire le journal,
appliquer les fichiers de façon idempotente, puis enregistrer le commit. Au redémarrage, terminer/reconcilier chaque opération avant
d'accepter de nouveaux événements pour les propriétaires concernés. Ne jamais restaurer séparément un seul de ces fichiers sans réconciliation.

Tests requis avant d'activer la phase : double adhésion, invitation expirée/rejouée, permissions, fusion d'IDs communs/distincts,
maximum des compteurs, sauvegardes incompatibles, membres hors ligne, départ/expulsion/dissolution, réadhésion et absence de double paiement.
Injecter une interruption après chaque écriture du journal et des fichiers ; vérifier la reprise avant d'annoncer le système fonctionnel.
