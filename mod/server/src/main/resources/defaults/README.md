# Quêtes par défaut

Les quêtes actuelles sont définies dans `web/editor/src/data/challenge-catalogue.json` et `web/editor/src/data/equipment-catalogue.json`, à la racine du dépôt. Quest Studio utilise directement ces fichiers et Gradle les embarque dans le JAR sous `defaults/catalogue/`.

À la première installation, le serveur crée les fichiers de quêtes dans `config/questplus/default/`. Un nouveau monde reçoit ensuite sa propre copie dans `<world>/questplus/`. Les packs existants ne sont pas remplacés automatiquement.

Les anciennes ressources se trouvent dans `mod/server/src/legacy-defaults/` : elles restent nécessaires à la compatibilité et aux tests, mais les anciennes quêtes ne sont pas installées par défaut.
