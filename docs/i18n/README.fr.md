[English](../../README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | Français | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> Cette traduction est maintenue par la communauté et peut être en retard sur la version anglaise ; la version anglaise fait foi. Les corrections sont les bienvenues.

# Plateforme Fasti BPM

Fasti BPM est un moteur indépendant et open source, construit sur Camunda 7 Community Edition
7.24.0. Il fonctionne sur votre base de données Camunda 7 existante, avec des bibliothèques à jour,
des correctifs de sécurité et une nouvelle interface web. Site web : <https://fasti.dev>

> **Fasti BPM n’est pas affilié à Camunda Services GmbH ; Camunda est une marque de Camunda Services GmbH.**
> Fasti BPM est un projet open source indépendant (Apache-2.0). Il ne fait pas partie de Camunda Services GmbH,
> n’est ni parrainé ni approuvé par cette société. Le nom « Camunda » est utilisé uniquement pour décrire
> l’origine du code source et les éléments avec lesquels Fasti est compatible.

<a id="quick-start"></a>

## Démarrage rapide

L’image est disponible sur Docker Hub (`fastidev/fasti-bpm`) et sur GitHub
Container Registry (`ghcr.io/fasti-bpm/fasti-bpm`), pour amd64 et arm64 :

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.3.2
```

Ouvrez <http://localhost:8080/fasti/> et connectez-vous. Sans `DB_URL`, l’image utilise une base H2
à l’intérieur du conteneur, uniquement à des fins d’évaluation. L’API REST nécessite des identifiants Basic :

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

Guide complet, y compris avec votre propre base de données : <https://fasti.dev/install>

<a id="highlights"></a>

## Points forts

- **Changez la connexion. Voilà la migration.** Le schéma Camunda 7.24 est utilisé tel quel ; les
  packages `org.camunda.bpm.*` sont conservés ([migration](https://fasti.dev/install#migrate)).
- **Un conteneur, une commande**, avec les mêmes variables `DB_*` que Camunda Run ([installation](https://fasti.dev/install)).
- **Conçue pour ceux qui traitent la file de travail** : une nouvelle interface web en 13 langues,
  en thème clair et sombre, utilisable sur un téléphone ([fonctionnalités](https://fasti.dev/#features)).
- **Modéliser et déployer dans le navigateur** : BPMN, DMN et formulaires ([modeleur](https://fasti.dev/#modeler)).
- **Les outils d’exploitation sont fournis** : historique, réparation d’instances, relance en masse,
  journal des opérations ([exploitation](https://fasti.dev/#operate)).
- **Fermé par défaut dans l’image conteneur** : connexion obligatoire dès le premier démarrage,
  aucun utilisateur par défaut, les réglages qui désactivent l’authentification REST ou les
  autorisations sont refusés, et Java4Shell est atténué. Tomcat et WildFly exigent des identifiants
  pour l’API REST et ne créent aucun utilisateur (le premier administrateur est créé sur la page de
  configuration initiale) ; le zip Run et le starter Spring Boot conservent les valeurs par défaut de
  Camunda 7. Chaque distribution contient les correctifs de sécurité et avertit au démarrage si la JVM
  est exposée à Java4Shell ([sécurité](https://fasti.dev/security)).
- **Authentification unique** : OpenID Connect et bearer tokens JWT ([guide](../single-sign-on.fr.md)).
- **Plateforme actuelle** : Java 17 ou version ultérieure, Spring Boot 4.1, GraalJS 25, Jackson 2.21
  ([performance](https://fasti.dev/#performance)).

<a id="installation"></a><a id="get-fasti"></a>

## Obtenir Fasti

- <a id="one-command-on-your-existing-camunda-724-database"></a>**Image Docker** (`fastidev/fasti-bpm`, amd64 et arm64) : <https://fasti.dev/install> ;
  pas à pas dans [docs/install-docker.fr.md](../install-docker.fr.md), toutes les variables dans
  [distro/docker/README.md](../../distro/docker/README.md#environment-variables).
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**Distribution Tomcat**, sans Docker : <https://fasti.dev/download>. Distribution Tomcat 1.3.1 prête à l’emploi :
  - [fasti-bpm-tomcat-1.3.2.zip](https://assets.fasti.dev/releases/1.3.2/fasti-bpm-tomcat-1.3.2.zip) ([SHA-256](https://assets.fasti.dev/releases/1.3.2/fasti-bpm-tomcat-1.3.2.zip.sha256))
  - [fasti-bpm-tomcat-1.3.2.tar.gz](https://assets.fasti.dev/releases/1.3.2/fasti-bpm-tomcat-1.3.2.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.3.2/fasti-bpm-tomcat-1.3.2.tar.gz.sha256))

  Vérifiez la somme de contrôle avant l’extraction, par exemple avec
  `sha256sum -c fasti-bpm-tomcat-1.3.2.zip.sha256`, et lisez `README.txt` dans la distribution avant
  de la démarrer.
- **Embarqué** dans Spring Boot ou une autre application Java : nouvelles coordonnées Maven
  (`dev.fasti.platform:fasti-*`), mêmes packages `org.camunda.bpm.*` ; voir
  [docs/embedding.fr.md](../embedding.fr.md).

<a id="moving-from-camunda-7"></a>

## Migrer depuis Camunda 7

Si vous utilisez Camunda 7.24, Fasti fonctionne sur cette même base de données sans en modifier le
schéma. Vous pouvez remplacer les anciens nœuds, exécuter Fasti à côté d’eux et revenir à Camunda
7.24 à tout moment. L’image Fasti a été testée en reprenant des bases de données créées par Camunda
Run 7.24 sous PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022 et Oracle Database 23ai ; le
retour à Camunda Run a été testé sur les mêmes bases de données, sauf MySQL. Db2 12.1 a été testé de
la même manière et nécessite trois variables de registre Db2
([Limites](../migrate-from-camunda-7.fr.md#limits)). Chaque client REST, workers de tâches externes compris, doit désormais
envoyer des identifiants Basic ou un bearer token.

Les applications Spring Boot embarquées passent aussi à Spring Boot 4.1 et Java 17 ; les schémas
plus anciens sont d’abord mis à niveau avec les scripts standard. Les plugins et les applications de
processus conçus pour Camunda 7 doivent être recompilés avec les artefacts Fasti pour s’exécuter
dans Fasti. Le fonctionnement côte à côte et le retour arrière ont des limites (même configuration
sur chaque nœud, nœud qui exécute les jobs, processus utilisant des fonctions propres à Fasti) :
voir [fonctionnement mixte](../migrate-from-camunda-7.fr.md#mixed-operation-with-camunda-7-nodes)
et [retour arrière](../migrate-from-camunda-7.fr.md#rollback-to-camunda-724).

Étapes et limites de la migration : <https://fasti.dev/install#migrate> et le
[guide de migration](../migrate-from-camunda-7.fr.md) (retour arrière, schémas plus anciens,
résultats des tests).

<a id="comparison"></a><a id="how-it-compares"></a>

## Comparaison

Plusieurs bons projets prolongent Camunda 7. Fasti conserve les paquets `org.camunda.bpm.*` et le
schéma 7.24 : le passage est un changement de connexion plutôt qu’un projet de migration, et il
ajoute une nouvelle interface. Les autres projets sont eux aussi de bons choix. Le tableau complet,
avec ses sources : <https://fasti.dev/compare>.

<a id="commitments-support-and-security-patches"></a><a id="security-and-support"></a>

## Sécurité et support

La dernière version mineure de la ligne `1.x` reçoit des corrections de bugs et des correctifs de
sécurité, sous forme de versions de patch comme `1.0.1`. Merci de signaler les problèmes de sécurité
en privé, et non dans une issue publique : voir [SECURITY.md](../../.github/SECURITY.md#security-policy), qui
contient aussi la [politique de support](../../.github/SECURITY.md#support-policy). Liste des corrections,
Java4Shell et la revue avant publication : <https://fasti.dev/security>. Pour de l’aide à
l’installation, à la migration ou en production, et pour des conditions de support, écrivez à
<contact@fasti.dev>.

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## Feuille de route

Ceci est un plan, pas un engagement sur des dates. Publié dans `1.2.0`, centré sur les performances
: un endpoint de charge de travail, des index optionnels pour la base de données, des alertes de
performance au démarrage, des réglages de la piste d’audit et une interface web plus rapide. Publié
dans `1.1.0` : un chart Helm et des manifestes Kubernetes, des métriques Prometheus, des traces
OpenTelemetry, des logs structurés en JSON, un suppléant pendant les absences, un contrôle à quatre
yeux pour les approbations, une piste d’audit immuable, un serveur MCP pour les assistants d’IA et
les dossiers (CMMN) dans la nouvelle interface. Ensuite dans `1.x` : la publication des artefacts
Maven sur Maven Central, la relecture des traductions par des locuteurs
natifs et le retrait des applications AngularJS. La chronologie, de la fin de Camunda 7 CE aux
objectifs pour 2027 : <https://fasti.dev/#timeline>.

<a id="documentation"></a>

## Documentation

Sur [fasti.dev](https://fasti.dev) : [Installer avec Docker](https://fasti.dev/install) ·
[Téléchargement pour Tomcat](https://fasti.dev/download) · [Guide d’intégration de l’API](https://fasti.dev/docs) ·
[Sécurité et support](https://fasti.dev/security) · [Comparer](https://fasti.dev/compare) ·
[Blog](https://fasti.dev/blog)

<a id="what-this-is"></a><a id="what-is-different-from-camunda-7240-ce"></a><a id="versioning"></a><a id="maven-coordinates"></a><a id="third-party-libraries-that-still-pull-in-the-original-engine"></a><a id="startup-check"></a><a id="web-application-path-fasti"></a><a id="single-sign-on-openid-connect"></a><a id="a-ui-built-from-real-user-complaints"></a>

| Document | Contenu |
|---|---|
| [docs/install-docker.fr.md](../install-docker.fr.md) | Installation avec Docker, étape par étape |
| [docs/install-kubernetes.md](../install-kubernetes.md) | Installation sur Kubernetes : chart Helm et manifestes simples (à partir de 1.1.0) |
| [docs/migrate-from-camunda-7.fr.md](../migrate-from-camunda-7.fr.md) | Migration depuis Camunda 7, retour arrière, limites |
| [docs/embedding.fr.md](../embedding.fr.md) | Compatibilité, différences par rapport à 7.24.0, gestion des versions, coordonnées Maven, vérification au démarrage |
| [docs/web-ui.fr.md](../web-ui.fr.md) | L’interface web : fonctionnalités demandées par les utilisateurs, le chemin `/fasti`, les applications classiques |
| [docs/absence.md](../absence.md) | Suppléants pendant les absences (à partir de 1.1.0) |
| [docs/four-eyes.md](../four-eyes.md) | Contrôle à quatre yeux pour les approbations (à partir de 1.1.0) |
| [docs/audit-trail.md](../audit-trail.md) | Piste d’audit immuable (à partir de 1.1.0) |
| [docs/single-sign-on.fr.md](../single-sign-on.fr.md) | Authentification unique (OpenID Connect) : Run, Spring Boot, Tomcat, WildFly |
| [docs/api/README.md](../api/README.md) | Intégration de l’API REST, clients de tâches externes Java et JavaScript |
| [distro/docker/README.md](../../distro/docker/README.md) | Image : variables d’environnement, pilotes, exploitation, réglages |
| [webapps/frontend-next/README.md](../../webapps/frontend-next/README.md#new-web-ui-frontend-next) | La nouvelle interface web |
| [docs/security/CVE-2026-114938.md](../security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell et Fasti |
| [CHANGELOG.md](../../CHANGELOG.md), [SECURITY.md](../../.github/SECURITY.md#security-policy) | Modifications, sécurité, politique de support |

<a id="build"></a>

## Compilation à partir des sources

Nécessite JDK 17 ou version ultérieure (21 recommandé) et un accès réseau à Maven Central,
nodejs.org et au registre npm (la compilation télécharge Node/npm pour l’interface web).

```bash
./mvnw clean verify
```

Une compilation complète exécute tous les tests Java et les tests unitaires de l’interface web (près
d’une heure ; les tests de bout en bout Playwright s’exécutent séparément, voir [webapps/frontend-next](../../webapps/frontend-next/README.md#end-to-end-tests-playwright)).
Voir [TESTING.md](../testing.md). Pour construire l’image de conteneur (la première compilation prend
environ 10 à 20 minutes) :

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## Contribuer

Les questions, les rapports de bugs et les pull requests sont les bienvenus sur GitHub ; voir
[CONTRIBUTING.md](../../.github/CONTRIBUTING.md#how-to-contribute) et le
[code de conduite](../../.github/CODE_OF_CONDUCT.md). Les relectures des traductions par des locuteurs natifs
sont particulièrement bienvenues.

<a id="acknowledgements"></a>

## Remerciements

- Fasti BPM Platform s’appuie sur plus d’une décennie de travail de Camunda et de la communauté
  Camunda 7.
- Lors de l’examen des défauts, le projet a consulté les informations publiques (tickets, avis,
  notes de version) d’autres projets communautaires issus de Camunda 7 : Operaton, CIB
  seven, OrqueIO et EximeeBPMS. Le code source de ce dépôt a été écrit
  indépendamment sur la base de Camunda 7 CE.

<a id="license"></a>

## Licence

[Licence Apache 2.0](../../LICENSE). Voir [NOTICE](../../NOTICE) pour les droits d’auteur du code d’origine.
