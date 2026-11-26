[English](install-docker.md) | [Tiếng Việt](install-docker.vi.md) | [Deutsch](install-docker.de.md) | [Español](install-docker.es.md) | Français | [हिन्दी](install-docker.hi.md) | [Bahasa Indonesia](install-docker.id.md) | [日本語](install-docker.ja.md) | [한국어](install-docker.ko.md) | [Português (Brasil)](install-docker.pt-BR.md) | [Русский](install-docker.ru.md) | [ไทย](install-docker.th.md) | [简体中文](install-docker.zh-CN.md)

> Cette traduction est maintenue par la communauté et peut être en retard sur la version anglaise ; la version anglaise fait foi. Les corrections sont les bienvenues.

# Installation de Fasti BPM avec Docker

Un guide pas à pas : essai rapide avec H2, mise en production avec PostgreSQL (`docker run` ou
`docker compose`), connexion à une base de données Camunda 7 existante, fonctionnement en parallèle, contrôles
d’état, mise à niveau, retour arrière et dépannage. Documentation complète de l’image :
[distro/docker/README.md](../distro/docker/README.md). Fasti BPM repose sur Camunda 7 CE
7.24.0 et est un projet indépendant, non affilié à Camunda Services GmbH.

<a id="1-requirements"></a>

## 1. Prérequis

- Docker Engine avec Docker Compose v2 (`docker compose`).
- Pour des charges de travail réelles : au moins 2 processeurs et 2 Go de mémoire pour le conteneur Fasti (en dessous de ce seuil, la
  JVM choisit le Serial GC).
- Base de données de production : PostgreSQL, MariaDB, MySQL, SQL Server ou Oracle (voir la
  [matrice des configurations testées](migrate-from-camunda-7.fr.md#limits)). DB2 n’a pas été vérifié. H2 est réservé à l’évaluation.

<a id="2-pull-the-image"></a>

## 2. Télécharger l’image

<!-- update with the Docker Hub image name at release -->
L’image est publiée pour amd64 et arm64 à deux emplacements :

```bash
docker pull fastidev/fasti-bpm:1.3.2              # Docker Hub
docker pull ghcr.io/fasti-bpm/fasti-bpm:1.3.2     # GitHub Container Registry
```

Tags : `1.3.2` (cette version précise), `1.3` (dernière version corrective de 1.3) et `latest` (la
dernière version publiée). L’image est signée avec cosign (sans clé, GitHub OIDC) et contient une
nomenclature logicielle (SBOM) ainsi qu’une attestation de provenance. À partir de 1.0.1, la
signature est au format bundle Sigstore de cosign 3 ; l’image 1.0.0 porte une signature dans
l’ancien format de cosign 2.

Vous pouvez également construire l’image à partir des sources (nécessite JDK 17+ et un accès réseau à Maven
Central, nodejs.org et au registre npm) et utiliser le nom `fasti-bpm:local` à la place de
`fastidev/fasti-bpm:1.3.2` dans toutes les commandes ci-dessous :

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="3-quick-trial-with-h2"></a>

## 3. Essai rapide avec H2

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  -v fasti-data:/fasti/data \
  fastidev/fasti-bpm:1.3.2
```

- Ouvrez <http://localhost:8080/fasti/> et connectez-vous en tant que `admin`.
- Sans `DB_URL`, l’image utilise H2 dans `/fasti/data` ; le volume `fasti-data` conserve les données
  lorsque le conteneur est recréé. À utiliser uniquement pour l’évaluation, jamais pour des données réelles.
- Ajoutez `--webapps --rest --example` à la commande pour obtenir également un exemple de processus
  (facture). Avec `--example` seul, l’interface web et l’API REST restent désactivées.
- L’API REST est accessible à l’adresse `http://localhost:8080/engine-rest` et nécessite des identifiants HTTP Basic ; consultez le
  [guide de l’API REST](api/README.md).

<a id="4-production-with-postgresql-docker-run"></a>

## 4. Mise en production avec PostgreSQL : `docker run`

Créez deux fichiers de secrets (mot de passe de la base de données et mot de passe administrateur). Avec `--production`, le moteur
applique sa politique de mots de passe : au moins 10 caractères comprenant des lettres majuscules et minuscules, un chiffre
et un caractère spécial.

```bash
mkdir -p secrets
openssl rand -hex 24 > secrets/db-password.txt
echo "Fasti-1-$(openssl rand -hex 12)" > secrets/admin-password.txt
chmod 0700 secrets           # other users on the host cannot enter the directory
chmod 0644 secrets/*.txt     # the containers read them as uid 999 (PostgreSQL) and 1000 (Fasti)
```

```bash
docker network create fasti-net

docker run -d --name fasti-db --network fasti-net \
  -e POSTGRES_DB=fasti -e POSTGRES_USER=fasti \
  -e POSTGRES_PASSWORD_FILE=/run/secrets/db-password \
  -v "$PWD/secrets/db-password.txt:/run/secrets/db-password:ro" \
  -v fasti-pgdata:/var/lib/postgresql/data \
  postgres:16

docker run -d --name fasti --network fasti-net -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://fasti-db:5432/fasti \
  -e DB_USERNAME=fasti -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v "$PWD/secrets/db-password.txt:/run/secrets/db-password:ro" \
  -e WAIT_FOR=fasti-db:5432 -e WAIT_FOR_TIMEOUT=60 \
  -e FASTI_DB_SCHEMA_UPDATE=true \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD_FILE=/run/secrets/admin-password \
  -v "$PWD/secrets/admin-password.txt:/run/secrets/admin-password:ro" \
  -e TZ=UTC \
  --memory 2g --stop-timeout 60 \
  fastidev/fasti-bpm:1.3.2 --production
```

- `FASTI_DB_SCHEMA_UPDATE=true` crée uniquement les tables manquantes dans une nouvelle base de données ; Fasti ne met jamais
  à niveau un schéma existant. Ce paramètre peut rester défini lors des démarrages suivants.
- `--production` active les cookies `Secure` et refuse une base de données H2 implicite ainsi que `DEBUG=true`.
  Terminez TLS sur un proxy inverse placé devant Fasti ; les navigateurs envoient les cookies `Secure` uniquement via HTTPS (ou à
  `localhost`).
- `WAIT_FOR` retarde le démarrage jusqu’à ce que la base de données accepte les connexions.
- Le mot de passe administrateur n’est défini que si l’administrateur n’existe pas encore ; modifiez-le ensuite dans Admin.

<a id="5-production-with-postgresql-docker-compose"></a>

## 5. Mise en production avec PostgreSQL : `docker compose`

Fichier d’exemple : [distro/docker/examples/docker-compose.postgres.yml](../distro/docker/examples/docker-compose.postgres.yml)
(PostgreSQL 16, secrets, contrôles d’état, `--production`, limite de mémoire de 2 Go).

```bash
cd distro/docker/examples          # or copy the file to a directory of its own
mkdir -p secrets
openssl rand -hex 24 > secrets/db-password.txt
echo "Fasti-1-$(openssl rand -hex 12)" > secrets/admin-password.txt
chmod 0700 secrets
chmod 0644 secrets/*.txt
docker compose -f docker-compose.postgres.yml up -d
docker compose -f docker-compose.postgres.yml ps      # both services "healthy"
```

Variables que vous pouvez définir au démarrage : `FASTI_IMAGE` (par défaut `fastidev/fasti-bpm:1.3.2`, par
exemple `FASTI_IMAGE=fasti-bpm:local`), `FASTI_HOST_PORT` (par défaut `8080`), `TZ` (par défaut `UTC`).

<a id="6-connecting-to-an-existing-camunda-7-database"></a>

## 6. Connexion à une base de données Camunda 7 existante

Fasti fonctionne avec le schéma 7.24 sans le modifier. Avant de commencer :

1. **Sauvegardez** la base de données.
2. Vérifiez la version du schéma : `select VERSION_ from ACT_GE_SCHEMA_LOG` — la valeur la plus élevée doit être
   `7.24.x`. Schémas 7.17–7.23 : exécutez d’abord les scripts `sql/upgrade` (fournis dans l’image à l’emplacement
   `/fasti/configuration/sql/upgrade`), consultez le
   [guide de migration](migrate-from-camunda-7.fr.md#older-versions-717723).
3. Vérifiez les autorisations, car Fasti les applique : `select * from ACT_ID_MEMBERSHIP where
   GROUP_ID_='camunda-admin'` et `select count(*) from ACT_RU_AUTHORIZATION`. Si l’ancien système
   fonctionnait avec les autorisations désactivées, accordez les permissions aux utilisateurs ordinaires avant de rediriger le trafic.
4. Notez `TZ`, le niveau d’historique, le préfixe des tables/schéma et les paramètres de mots de passe des
   anciens nœuds.

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=UTC \
  fastidev/fasti-bpm:1.3.2 --production
```

- Le port n’est accessible que depuis cette machine ; pour un accès distant, passez par un proxy inverse avec HTTPS.
- Ne définissez **pas** `FASTI_DB_SCHEMA_UPDATE` ni `FASTI_ADMIN_*` : les tables et les comptes existent déjà. Le
  journal de démarrage doit contenir `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
- Le niveau d’historique est défini par défaut sur `auto` : le niveau enregistré dans la base de données est utilisé.
- Chaque client REST doit désormais envoyer des identifiants Basic ou un jeton bearer.
- Pilotes : PostgreSQL, MySQL, MariaDB, SQL Server et H2 sont inclus. Ajoutez vous-même les pilotes **Oracle** et **DB2** (`ojdbc11.jar`, `jcc.jar`) avec `-v /driver/dir:/drivers:ro -e FASTI_EXTRA_LIB=/drivers`.
  SQL Server : le pilote 13.x chiffre la connexion et vérifie le certificat par défaut ; le
  serveur doit disposer d’un certificat valide correspondant au nom d’hôte dans l’URL. `trustServerCertificate=true`
  (ignore la vérification des certificats) ou `encrypt=false` (aucun chiffrement) sont réservés aux essais locaux. MySQL/MariaDB nécessitent
  `transaction-isolation=READ-COMMITTED`.

<a id="7-side-by-side-with-camunda-7-nodes"></a>

## 7. Fonctionnement en parallèle avec des nœuds Camunda 7

Fasti et Camunda 7.24 peuvent fonctionner simultanément sur une même base de données (testé : chacun voit les données
de l’autre). Conditions :

- même niveau d’historique, même `TZ`, mêmes paramètres de mots de passe, même préfixe de tables ;
- si les anciens nœuds sont des applications de processus contenant des classes Java (`JavaDelegate`, écouteurs) absentes de Fasti, définissez `-e FASTI_JOB_EXECUTOR_ENABLED=false` sur Fasti ou configurez **chaque** nœud
  pour tenir compte des déploiements (`FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE=true` pour Fasti). Cela protège uniquement
  les tâches asynchrones : le démarrage d’instances ou la finalisation de tâches via Fasti exécute toujours les délégués
  sur Fasti ; envoyez donc ces requêtes aux nœuds qui disposent des classes Java ;
- répartiteur de charge : affinité de session pour les applications web ; REST avec des identifiants Basic n’en nécessite pas.

Détails : [Fonctionnement mixte avec des nœuds Camunda 7](migrate-from-camunda-7.fr.md#mixed-operation-with-camunda-7-nodes).

<a id="8-common-environment-variables"></a>

## 8. Variables d’environnement courantes

| Variable | Signification |
|---|---|
| `DB_URL`, `DB_DRIVER`, `DB_USERNAME`, `DB_PASSWORD` (`_FILE`) | connexion à la base de données (noms de variables de l’ancienne image Camunda 7 Run ; alias : `FASTI_DB_*`) |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | attendre que `host:port` soit disponible avant de démarrer |
| `TZ` | fuseau horaire de la JVM ; doit être identique sur chaque nœud |
| `FASTI_DB_SCHEMA_UPDATE` | `true` : créer les tables manquantes dans une nouvelle base de données |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (`_FILE`) | créer l’administrateur s’il n’existe pas |
| `FASTI_JOB_EXECUTOR_ENABLED`, `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | exécuteur de tâches de ce nœud |
| `FASTI_EXTRA_LIB` | répertoire des fichiers jar supplémentaires (pilotes Oracle, DB2) |
| `FASTI_DB_CONN_MAXACTIVE` | taille maximale du pool de connexions (20 par défaut ; 10 restent ouvertes au repos, `FASTI_DB_CONN_MINIDLE`) |
| `JAVA_OPTS`, `FASTI_MAX_RAM_PERCENTAGE` | options de la JVM, pourcentage de mémoire destiné au tas (75) |

Tableau complet : [distro/docker/README.md](../distro/docker/README.md#environment-variables).

<a id="9-health-checks"></a>

## 9. Contrôles d’état

| Point de terminaison | Utilisation |
|---|---|
| `/actuator/health/liveness` | sonde de vitalité Kubernetes |
| `/actuator/health/readiness` | sonde de disponibilité, répartiteurs de charge ; inclut la connexion à la base de données et le moteur |
| `/actuator/health` | état global |

Aucune connexion n’est nécessaire ; seuls les points de terminaison de contrôle d’état sont exposés. Le `HEALTHCHECK` de l’image utilise
la disponibilité, de sorte que `docker ps` affiche `healthy` dès que Fasti est prêt :

```bash
curl -s http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
docker inspect -f '{{.State.Health.Status}}' fasti           # healthy
```

Kubernetes : `livenessProbe` → `/actuator/health/liveness`, `readinessProbe` →
`/actuator/health/readiness`, `terminationGracePeriodSeconds: 60`.

<a id="10-upgrade-and-rollback"></a>

## 10. Mise à niveau et retour arrière

**Mise à niveau de Fasti** (par exemple `1.1.0` → `1.3.2`) :

```bash
docker pull fastidev/fasti-bpm:1.3.2
docker stop -t 60 fasti && docker rm fasti
docker run -d --name fasti ... fastidev/fasti-bpm:1.3.2 --production   # same options as before
```

Avec Docker Compose : modifiez `FASTI_IMAGE` (ou la ligne `image:`), puis exécutez
`docker compose -f docker-compose.postgres.yml up -d`. Lisez d’abord le [journal des modifications](../CHANGELOG.md),
en particulier les entrées **BREAKING**. La branche `1.x` conserve le schéma 7.24, les mises à niveau
au sein de cette branche ne modifient donc pas le schéma. `docker stop -t 60` laisse les requêtes en cours se terminer avant
l’arrêt du conteneur.

**Retour arrière :**

- vers la version précédente de Fasti : redémarrez avec l’ancien tag ;
- vers Camunda 7.24 : arrêtez Fasti et redémarrez les nœuds Camunda 7.24 sur la même base de données. Les préférences de la nouvelle interface restent présentes (`fasti.prefs.*` dans `ACT_ID_INFO`, ignorées par Camunda 7). Les processus déployés qui utilisent des fonctionnalités propres à Fasti ne fonctionneront pas sur Camunda 7. Consultez
  [Retour à Camunda 7.24](migrate-from-camunda-7.fr.md#rollback-to-camunda-724).

<a id="11-troubleshooting"></a>

## 11. Dépannage

Consultez le journal avec `docker logs fasti` (ou `docker compose logs fasti`).

| Symptôme | Cause et solution |
|---|---|
| L’API REST renvoie `401` | Fasti exige une authentification : envoyez `-u user:password` ou un jeton bearer |
| Listes vides ou `403` | l’utilisateur ne dispose pas des autorisations nécessaires : accordez-lui les permissions requises sur les ressources concernées dans Admin → Autorisations. Ajoutez uniquement les véritables administrateurs à `camunda-admin` |
| Arrêt avec `ENGINE-03111 The database schema has version …` | schéma antérieur à 7.24 : effectuez une sauvegarde, puis exécutez les scripts `sql/upgrade` (section 6). Ne contournez pas la vérification avec `FASTI_SCHEMA_VERSION_CHECK=warn` : le fonctionnement avec un ancien schéma n’est pas pris en charge et peut entraîner des défaillances en cours d’utilisation |
| Arrêt avec `Insecure configuration refused` | un paramètre désactive l’authentification REST ou les autorisations ; supprimez-le (seul `FASTI_INSECURE_DEMO=true` ouvre l’accès à l’instance, pour les démonstrations) |
| Arrêt avec `Password does not match policy` | `--production` applique la politique de mots de passe à `FASTI_ADMIN_PASSWORD` : au moins 10 caractères comprenant des lettres majuscules et minuscules, un chiffre et un caractère spécial |
| `password authentication failed` | le mot de passe dans le fichier de secret diffère de celui de la base de données. Les fichiers créés sous Windows peuvent se terminer par `\r\n` : recréez-les avec `tr -d '\r\n'`. PostgreSQL lit `POSTGRES_PASSWORD_FILE` uniquement lors de la première initialisation du volume |
| `FASTI_ADMIN_USER needs FASTI_ADMIN_PASSWORD` | définissez `FASTI_ADMIN_PASSWORD` ou `FASTI_ADMIN_PASSWORD_FILE` |
| `--production needs a database` | aucune base de données n’est configurée ; `--production` ne se rabat pas sur la base de démonstration H2 : définissez `DB_URL` pour pointer vers une base de données de production |
| `… cannot be read` pour une variable `_FILE` | le fichier n’est pas monté dans le conteneur ou l’uid 1000 ne peut pas le lire (le fichier doit avoir le mode `0644` ; son répertoire peut conserver le mode `0700`) |
| La connexion web via HTTP ne conserve pas la session | le cookie `Secure` (`--production`) est envoyé uniquement via HTTPS (ou à `localhost`) : terminez TLS sur le proxy |
| Impossible de se connecter à SQL Server (erreur de certificat) | fournissez à SQL Server un certificat valide correspondant au nom d’hôte dans l’URL ; `trustServerCertificate=true` est réservé aux essais locaux |
| Le conteneur redémarre en boucle et le journal affiche `OutOfMemoryError` | la JVM s’arrête lorsque la mémoire est épuisée : augmentez la limite de mémoire du conteneur (2 Go) |

Toujours bloqué ? Ouvrez un ticket GitHub en joignant le journal (après suppression des mots de passe). Problèmes de sécurité : consultez
[SECURITY.md](../.github/SECURITY.md#security-policy).

<a id="12-without-docker"></a>

## 12. Sans Docker

Distribution Tomcat 1.3.1 prête à l’emploi (page de téléchargement : https://fasti.dev/download) :
- [fasti-bpm-tomcat-1.3.1.zip](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.zip) ([SHA-256](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.zip.sha256))
- [fasti-bpm-tomcat-1.3.1.tar.gz](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.tar.gz.sha256))

Vérifiez la somme de contrôle avant l’extraction, par exemple `sha256sum -c fasti-bpm-tomcat-1.3.1.zip.sha256`.
