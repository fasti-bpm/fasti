[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | Français | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> Cette traduction est maintenue par la communauté et peut être en retard sur la version anglaise ; la version anglaise fait foi. Les corrections sont les bienvenues.

# Plateforme Fasti BPM

**Make Camunda 7 Great Again.** Camunda 7 Community Edition s’est arrêtée à 7.24.0, sa dernière
version. Fasti BPM prend le relais : le même moteur sur la même base de données, avec
des correctifs et des mises à niveau de bibliothèques continus, ainsi qu’une nouvelle interface web.

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
  fastidev/fasti-bpm:1.0.1
```

Ouvrez <http://localhost:8080/fasti/> et connectez-vous. Sans `DB_URL`, l’image utilise une base H2
à l’intérieur du conteneur, uniquement à des fins d’évaluation. L’API REST nécessite des identifiants Basic :

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

**Construisez vous-même l’image** à partir des sources. Cela nécessite Docker, JDK 17+ et un accès
réseau à Maven Central, nodejs.org et au registre npm (la compilation télécharge Node/npm pour
l’interface web) ; la première compilation prend environ 10 à 20 minutes :

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
# then run the docker run command above with fasti-bpm:local instead of fastidev/fasti-bpm:1.0.1
```

L’image **exige l’authentification REST et applique les autorisations par défaut** (contrairement à Camunda
Run) ; seul `FASTI_INSECURE_DEMO=true` les désactive, pour les démonstrations.

<a id="highlights"></a>

## Points forts

- **Nouvelle interface web** sur `/fasti/` (React) :
  - recherche d’instances de processus selon plusieurs variables simultanément, combinées avec ET/OU ;
  - chronologie du dossier au sein de la tâche (commentaires et actions à chaque étape) ;
  - bouton « Obtenir la tâche suivante » ;
  - dates d’échéance et urgence (SLA), tâches en retard ;
  - charge de travail de l’équipe par utilisateur et par groupe ;
  - procédures opérationnelles associées aux incidents ;
  - page d’état du système (incidents, jobs en échec, jobs en retard, tâches externes, traitements par lots) ;
  - mise en page adaptée aux mobiles ;
  - 13 langues.
- **Authentification unique (OIDC)** : Keycloak, Microsoft Entra ID et d’autres fournisseurs OpenID Connect ;
  attributs de jeton associés aux groupes et aux tenants ; jetons Bearer JWT pour l’API REST. Disponible pour Run,
  Spring Boot, Tomcat et WildFly.
- **Renforcement de la sécurité** : liste d’autorisation pour les expressions dans les requêtes et les filtres, vérifications des pièces jointes et
  des commentaires au sein du moteur, configuration initiale, permissions Cockpit, redirections ouvertes, en-têtes des
  applications web, cookies et CSP. Détails dans le [journal des modifications](CHANGELOG.md).
- **Authentification activée par défaut** : l’image exige des identifiants pour REST et applique
  les autorisations dans tous les modes.
- **Contrôle de la version du schéma** : le moteur refuse de démarrer sur un schéma antérieur à 7.24 et ne
  met jamais un schéma à niveau de lui-même.
- **Atténuation de Java4Shell** ([CVE-2026-114938](docs/security/CVE-2026-114938.md)) : l’image amd64
  embarque un JRE Temurin corrigé et désactive l’intrinsèque Base64 ; le moteur émet un avertissement lorsqu’il
  s’exécute sur une JVM affectée.
- **Performances** : le premier chargement de la nouvelle interface web passe de 960 Ko à 330 Ko (gzip activé par
  défaut) ; le débit de l’exécuteur de jobs augmente d’environ 2,5 fois après réglage (chiffres et configuration de test dans
  [distro/docker/README.md](distro/docker/README.md#performance-tuning)).
- **Plateforme actuelle** : Java 17 ou version ultérieure, Spring Boot 4.1, GraalJS 25, Jackson 2.21.

<a id="installation"></a>

## Installation

<a id="one-command-on-your-existing-camunda-724-database"></a>

### Une seule commande, sur votre base de données Camunda 7.24 existante

L’image accepte les variables `DB_*` de l’ancienne image Camunda 7 Run. Sauvegardez la base de données,
puis :

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e TZ=Europe/Berlin \
  fastidev/fasti-bpm:1.0.1
```

Les utilisateurs, mots de passe, groupes, tenants, autorisations, filtres, déploiements, instances en cours,
historiques, jobs et incidents restent inchangés. Le port n’est accessible que depuis cette machine ; pour un accès
distant, passez par un proxy inverse avec HTTPS. Connectez-vous sur `/fasti/` avec un compte existant.
L’authentification étant activée, chaque client REST (workers de tâches externes, intégrations) doit envoyer
des identifiants Basic ou un jeton Bearer.

- **Cohabitation** avec des nœuds Camunda 7.24 CE sur la même base de données : même niveau d’historique, même
  `TZ`, mêmes paramètres de mot de passe. Si les anciens nœuds sont des applications de processus contenant des classes Java dont
  Fasti ne dispose pas, définissez `FASTI_JOB_EXECUTOR_ENABLED=false` sur Fasti (ou configurez chaque nœud pour
  tenir compte des déploiements) et ne démarrez pas ces processus ni ne terminez leurs tâches via Fasti : un
  appel synchrone exécute les délégués sur le nœud qui le reçoit.
- **Retour arrière** : Fasti ne modifie pas le schéma ; arrêtez donc Fasti et redémarrez les nœuds Camunda 7.24
  sur la même base de données. Les processus déployés qui utilisent des fonctionnalités propres à Fasti ne fonctionneront pas sur
  Camunda 7 ; voir [Retour à Camunda 7.24](docs/migrate-from-camunda-7.fr.md#rollback-to-camunda-724).
- **Schémas plus anciens (7.17–7.23)** : exécutez d’abord les scripts `sql/upgrade` fournis dans l’image
  (`/fasti/configuration/sql/upgrade`) jusqu’à 7.24 ; par exemple, de 7.23 à 7.24 :
  `postgres_engine_7.23_to_7.24.sql`.

Tests réalisés avec l’image (reprise d’une base de données créée par Camunda Run 7.24.0, retour
arrière, fonctionnement en cohabitation) : **PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022, Oracle
Database 23** (le pilote Oracle n’est pas fourni ; ajoutez vous-même `ojdbc11` ; le retour arrière n’a pas été testé
sur MySQL). **DB2 n’a pas été
vérifié.** H2 est réservé à l’évaluation. Résultats par test et limites :
[guide de migration](docs/migrate-from-camunda-7.fr.md#limits).

Guides détaillés :

- [Installation avec Docker](docs/install-docker.fr.md) : H2, PostgreSQL avec `docker run` et
  `docker compose`, bases de données existantes, mode de cohabitation, contrôles d’état, mise à niveau, retour arrière,
  dépannage.
- [Migration depuis Camunda 7](docs/migrate-from-camunda-7.fr.md) : étapes, éléments identiques et
  différences, retour arrière, limites.
- [Référence des variables d’environnement de l’image](distro/docker/README.md#environment-variables).

<a id="without-docker-tomcat-bundle-download-from-fastidev"></a>

### Sans Docker : distribution Tomcat (à télécharger sur fasti.dev)

Distribution Tomcat 1.0.0 prête à l’emploi (page de téléchargement : https://fasti.dev/download) :
- [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256))
- [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256))

Vérifiez la somme de contrôle avant l’extraction, par exemple avec `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`.

La distribution Tomcat peut également être compilée à partir des sources (`distro/tomcat`). Lisez `README.txt` dans
la distribution avant de la démarrer : l’API REST (`/engine-rest`) exige l’authentification HTTP Basic
par défaut, aucun utilisateur n’est créé (créez le premier administrateur sur la page de configuration de `/fasti/`),
et, lors de la reprise d’une base de données Camunda Run, définissez `jobExecutorDeploymentAware=false` dans
`conf/bpm-platform.xml`, sinon les temporisateurs et les jobs asynchrones des déploiements existants ne s’exécuteront pas.

<a id="a-ui-built-from-real-user-complaints"></a>

## Une interface conçue à partir des problèmes concrets signalés par les utilisateurs

Les fonctionnalités de la nouvelle interface web ont été choisies à partir des témoignages publics d’utilisateurs de Camunda 7 sur le
forum Camunda et sur GitHub, par exemple :

| Demandes des utilisateurs | Source | Dans Fasti |
|---|---|---|
| Rechercher des instances selon plusieurs variables, avec OU | forum [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Éditeur de conditions multivariables, ET/OU |
| Voir les commentaires à chaque étape d’un dossier | forum [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Chronologie du dossier dans la tâche ; commentaires sur l’instance de processus elle-même |
| Trier les tâches par variables, les plus anciennes en premier | forum [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Tri par variables, dates d’échéance et urgence |
| Délégation, remplaçants pendant les absences | forum [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | La délégation par tâche existe ; les remplaçants en cas d’absence figurent dans la [feuille de route](#roadmap-plan) |
| Clé métier et variables lors du démarrage d’un processus sans formulaire | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Oui |
| Télécharger le XML BPMN déployé | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Oui |
| Libellés cohérents, plusieurs langues | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 langues |

<a id="comparison"></a>

## Comparaison

Comparaison synthétique fondée sur la documentation publique de chaque projet (consultée en novembre 2026) ;
« non vérifié » signifie que nous n’avons trouvé aucune source fiable. Chaque projet suit sa propre orientation et constitue un
choix pertinent pour des besoins différents ; consultez sa documentation avant de décider.

| | Licence | Fonctionne sur une base C7 existante | Migration nécessaire | Correctifs de sécurité | Java / Spring Boot | Interface | SSO | Image Docker |
|---|---|---|---|---|---|---|---|---|
| **Fasti BPM** | Apache-2.0 | Oui, schéma 7.24 inchangé | Image : aucune. Applications embarquées : nouvelles coordonnées Maven, Spring Boot 4.1 et Java 17+ ; packages `org.camunda.*` conservés | Oui, voir la [politique ci-dessous](#commitments-support-and-security-patches) | 17+ / 4.1 | Nouvelle (React) + classique (obsolète) | OIDC intégré | Oui, depuis 1.0.0 |
| Camunda 7 CE 7.24 | Sources sous Apache-2.0 ; certains composants web fournis sous MIT ([13]) | (la base d’origine) | — | Aucune version CE après 7.24 ([1]) | 17, 21, 25 ([2]) / 3.5 | Cockpit, Tasklist, Admin (AngularJS) | OAuth2 via le starter de sécurité | Ancienne image Run |
| Camunda 7 Enterprise | Commerciale | Oui (même gamme de produits) | Non | Jusqu’à 04/2030, prolongation payante jusqu’à 04/2032 ([3]) | non vérifié | Cockpit Enterprise | non vérifié | non vérifié |
| Camunda 8 | Camunda License 1.0 ; la production autogérée nécessite une licence depuis 8.6 ([4]) | Non (moteur différent) | Oui (modèles, instances, données) | Oui (éditeur) | moteur différent, non comparable | Operate, Tasklist | non vérifié | non vérifié |
| Operaton | Apache-2.0 | Même structure de tables que 7.24 ; 2.2 enregistre la version de schéma 7.25 au moyen d’un script de mise à niveau ([5]) | Packages renommés en `org.operaton.*` (recette OpenRewrite disponible) ; script de version du schéma (2.2) | Oui, branche 2.2.x ([6]) | 17+ / 4.1 ([5]) | Classique + webapps-neo (préversion) | non vérifié | Oui ([7]) |
| CIB seven | Apache-2.0 ; EE nécessite une clé de licence | Oui (modifications de 7.24 intégrées) | Packages renommés en `org.cibseven.*`, préfixe `cibseven.*` | Oui, à chaque version ([8]) | 17, 21 / 3.5 et 4.0 ([9]) | Client web Vue 3 | OAuth2/JWT pour REST ([8]) | Oui ([8]) |
| OrqueIO | Apache-2.0 | Compatible avec les tables C7 selon sa documentation ([10]) | Groupe Maven `io.orqueio` ; renommage des packages non précisé | Oui, selon sa documentation ([10]) | 21 / 3.5, 4.0, 4.1 | Réécriture en Angular | OIDC multifournisseur | non vérifié |
| EximeeBPMS | Apache-2.0 (Monitor : MIT) | Le projet indique que son schéma reste compatible avec Camunda 7 (non vérifié par Fasti) ([14]) | Packages renommés en `org.eximeebpms.*`, Model API renommée | Oui, les deux dernières versions mineures ([11]) | 21 / version de Spring Boot non précisée | Monitor distinct | non vérifié | Oui (dépôt `eximeebpms-docker`) |
| Fluxnova | Apache-2.0 (FINOS) | non vérifié | non vérifié | non vérifié | 21 (image Corretto) / non vérifié | Control Center | non vérifié | Oui ([12]) |

Les points faibles de Fasti, énoncés clairement : un projet jeune avec une petite équipe, pas encore de support
commercial, DB2 non vérifié.

Sources : [1] [Annonce de fin de vie de CE](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921);
[2] [Environnements pris en charge par 7.24](https://docs.camunda.org/manual/7.24/introduction/supported-environments/);
[3] [Prolongation Enterprise](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/);
[4] [Licences de Camunda 8.6](https://camunda.com/blog/2024/10/camunda-licensing-what-you-need-to-know/);
[5] [Operaton 2.2](https://docs.operaton.org/docs/documentation/reference/release-notes/2_2/);
[6] [SECURITY.md d’Operaton](https://github.com/operaton/operaton/blob/main/SECURITY.md);
[7] [operaton-docker](https://github.com/operaton/operaton-docker);
[8] [CIB seven 2.2.0](https://docs.cibseven.org/manual/latest/release-notes/release-notes-2.2.0-ce/);
[9] [Environnements pris en charge par CIB seven](https://docs.cibseven.org/manual/latest/introduction/supported-environments/);
[10] [Documentation d’OrqueIO](https://docs.orqueio.io/);
[11] [SECURITY.md d’EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms/blob/main/SECURITY.md);
[12] [Fluxnova](https://github.com/finos/fluxnova-bpm-platform);
[13] [Licences de Camunda 7](https://docs.camunda.org/manual/latest/introduction/licenses/);
[14] [EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms);
vue d’ensemble indépendante : [WZ-IT, 2026-10-15](https://wz-it.com/en/blog/camunda-7-end-of-life-operaton-cibseven-fluxnova-compared/).

<a id="timeline"></a>

## Chronologie

<a id="camunda-7-milestones"></a>

### Jalons de Camunda 7

| Date | Jalon | Source |
|---|---|---|
| 2024-03-06 | Camunda annonce la fin de vie de Camunda 7 CE pour octobre 2025, avec 7.24 comme dernière version CE | [Forum Camunda](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921) |
| 2025-02-28 | Camunda prolonge le support de Camunda 7 Enterprise jusqu’au 2030-04-09, avec un support étendu payant jusqu’en avril 2032 | [Blog Camunda](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) |
| 2025-10-07 | Création du tag `7.24.0` et publication sur Maven Central : la dernière version CE | [tag 7.24.0](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0), [Maven Central](https://repo1.maven.org/maven2/org/camunda/bpm/camunda-engine/7.24.0/) |
| 2025-10-14 | Date de sortie de 7.24 prévue par Camunda ; après cette date, aucune nouvelle version CE n’est publiée, y compris pour les correctifs de sécurité. Le dépôt CE a depuis été archivé | [annonce](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921), [page de support](https://docs.camunda.org/enterprise/announcement) |
| Avril 2030 | Fin du support de Camunda 7 Enterprise (7.24 LTS : 2030-04-13) | [page de support](https://docs.camunda.org/enterprise/announcement) |
| Avril 2032 | Fin du support étendu payant de Camunda 7 Enterprise | [page de support](https://docs.camunda.org/enterprise/announcement) |

<a id="fasti-bpm"></a>

### Fasti BPM

Fasti poursuit directement le développement à partir du tag `7.24.0` ; chaque correctif et chaque modification sont comptabilisés à partir de ce point
(`git log 7.24.0..`). Les phases ci-dessous sont présentées dans l’ordre des travaux réalisés, sans dates :

1. **Fondations** : Java 17, Spring Boot 4.1, mises à niveau des bibliothèques ; coordonnées Maven
   `dev.fasti.platform`.
2. **Sécurité** : correctifs pour les problèmes divulgués publiquement de la branche 7.x, liste d’autorisation des expressions,
   vérifications des pièces jointes et des commentaires au sein du moteur, renforcement des applications web.
3. **Nouvelle interface web** sur `/fasti/`, remplaçant progressivement les applications classiques Cockpit, Tasklist et Admin.
4. **Authentification unique** (OIDC) pour Run, Spring Boot, Tomcat et WildFly.
5. **Remplacement direct sur les bases de données existantes** : l’image accepte les anciennes variables `DB_*` et vérifie la version du schéma ; testé sur cinq systèmes de bases de données.
6. **Revue de sécurité avant publication** (revue de code, test d’intrusion de l’image, analyse de référence
   ZAP ; les problèmes constatés ont été corrigés) et **atténuation de Java4Shell**.
7. **1.0.0** : première version (2026-11-20).

<a id="roadmap-plan"></a>

## Feuille de route (prévisions)

> Il s’agit d’un **plan**, et non d’un engagement sur des dates. Le périmètre et l’ordre peuvent
> évoluer.

### Novembre et décembre 2026 (en cours)

D’abord, tout ce que les utilisateurs ont déjà demandé.

**1.x : Plus simple à exploiter**

- Artefacts Maven sur Maven Central.
- Chart Helm et exemples de manifestes Kubernetes.
- Métriques Prometheus et traçage OpenTelemetry.
- Journaux structurés au format JSON.
- Mise à niveau de MyBatis, qui met aussi à jour OGNL.
- Vérification de DB2 et relecture des traductions par des locuteurs natifs.

**2.0 : Ce que demandent les équipes des secteurs réglementés**

- Un remplaçant pendant les absences, pour que le travail n’attende pas une personne en congé
  ([forum 2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199)).
- Contrôle à quatre yeux pour les approbations.
- Une piste d’audit immuable, signée ou hachée, avec des rapports d’audit.
- CMMN dans la nouvelle interface et retrait des applications AngularJS.

**Nouveaux territoires**

- Branches de support à long terme.
- Un serveur MCP pour le moteur.
- Des étapes d’IA dans les processus.

### Objectifs pour 2027

- **T1 2027 : Fonctionner à grande échelle** : un opérateur Kubernetes pour les mises à niveau
  progressives et la mise à l’échelle ; un historique diffusé en continu vers Kafka et OpenSearch,
  pour que la base de données d’exécution reste compacte ; des benchmarks de performance publiés,
  refaits à chaque version.
- **T2 2027 : Une boîte à outils pour les développeurs** : un SDK de connecteurs, avec des
  connecteurs pour REST, Kafka, l’e-mail et le stockage objet ; un kit de test de processus avec des
  tests de scénarios et des rapports de couverture ; un générateur de formulaires avec des
  composants réutilisables.
- **T3 2027 : Voir comment les processus se comportent** : analyse des processus (cartes de chaleur,
  goulots d’étranglement et rapports de durée) ; objectifs de niveau de service avec alertes ; un
  assistant qui rédige du BPMN à partir de texte et explique les incidents.
- **T4 2027 : La confiance à l’échelle de l’entreprise** : administration multi-tenant avec des
  quotas par tenant ; un audit de sécurité indépendant, dont le rapport est publié ; des builds
  reproductibles avec une provenance signée pour chaque version.

<a id="commitments-support-and-security-patches"></a>

## Engagements : support et correctifs de sécurité

> Cette politique est tenue à jour par l’équipe du projet Fasti BPM et peut être modifiée ; la version présente dans ce
> dépôt est celle qui fait foi.

- **Branche prise en charge :** la dernière version mineure de la branche `1.x` reçoit les corrections de bogues et les correctifs
  de sécurité (sous forme de versions correctives, par exemple `1.0.1`).
- **Correctifs de sécurité :** les vulnérabilités dans Fasti et dans les bibliothèques qu’il embarque sont prioritaires sur
  les autres travaux ; l’image est reconstruite lorsque l’image de base ou le JRE reçoit un correctif. Le projet suit
  les avis de sécurité publics concernant Camunda 7 et ses forks et vérifie si Fasti est affecté.
- **Compatibilité :** la branche `1.x` conserve le schéma de base de données 7.24 et les packages `org.camunda.bpm.*`.
  Les modifications incompatibles sont signalées par **BREAKING** dans le [journal des modifications](CHANGELOG.md).
- **Niveau de support :** il s’agit d’un projet communautaire ; le support est assuré dans la mesure des moyens disponibles, **sans SLA**,
  et aucun support commercial n’est encore proposé.
- **Signalement des vulnérabilités :** veuillez **ne pas** ouvrir de ticket public ; consultez
  [SECURITY.md](SECURITY.md#security-policy) (signalement privé via GitHub).
- **Défauts corrigés :** les sections *Corrections* et *Sécurité* du [journal des modifications](CHANGELOG.md) répertorient
  les corrections apportées par rapport à Camunda 7 CE 7.24.0 ; les avis sont répertoriés dans
  [SECURITY.md](SECURITY.md#advisories).

<a id="documentation"></a>

## Documentation

| Document | Contenu |
|---|---|
| [docs/install-docker.md](docs/install-docker.fr.md) | Installation avec Docker, étape par étape |
| [docs/api/README.md](docs/api/README.md) | Intégration de l’API REST, clients de tâches externes Java et JavaScript |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.fr.md) | Migration depuis Camunda 7, retour arrière, limites |
| [distro/docker/README.md](distro/docker/README.md) | Image : variables d’environnement, pilotes, exploitation, réglages |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | La nouvelle interface web |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell et Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy), [CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) | Modifications, sécurité, contributions |

<a id="what-this-is"></a>

## Présentation du projet

Ce dépôt contient le moteur de processus BPMN 2.0 / DMN, l’API REST, les applications
web (Cockpit, Tasklist, Admin) et les distributions (Run, Tomcat, WildFly). Il
part du tag `7.24.0` de Camunda 7 CE et poursuit le développement avec des corrections de bogues et des mises à niveau
de bibliothèques.

L’objectif est la **rétrocompatibilité** avec les applications qui fonctionnent sur Camunda 7 :

- les packages `org.camunda.bpm.*`, l’espace de noms BPMN
  `http://camunda.org/schema/1.0/bpmn` et le préfixe de configuration `camunda.bpm.*` sont conservés ;
- le schéma de base de données de 7.24 est conservé ;
- tous les modes de déploiement sont conservés : Spring Boot, Run, Tomcat, WildFly ; `javax` et `jakarta`.

<a id="what-is-different-from-camunda-7240-ce"></a>

## Différences par rapport à Camunda 7.24.0 CE

- **Sécurité :** correctifs pour les problèmes divulgués publiquement de la branche 7.x (expressions dans les
  requêtes, configuration initiale, accès aux pièces jointes et aux commentaires, permissions Cockpit, redirections
  ouvertes), ainsi que des protections supplémentaires : liste d’autorisation pour les expressions dans les requêtes et les
  filtres, vérifications des pièces jointes et des commentaires au sein du moteur, renforcement des filtres de sécurité,
  des en-têtes et des cookies dans les applications web.
- **Stabilité :** suppression de la fuite mémoire lors de l’évaluation de scripts et d’expressions DMN avec
  JavaScript (GraalJS), ainsi que plusieurs autres corrections du moteur.
- **Plateforme :** Java 17 ou version ultérieure, Spring Boot 4.1, GraalJS 25, Jackson 2.21 et de nombreuses autres
  bibliothèques mises à niveau vers des versions contenant des correctifs de sécurité.

Certains correctifs modifient les paramètres par défaut pour adopter un comportement plus sûr ; lisez « Différences » dans le
[guide de migration](docs/migrate-from-camunda-7.fr.md#what-differs) avant la mise à niveau.

<a id="versioning"></a>

## Gestion des versions

Fasti BPM Platform suit le [versionnage sémantique](https://semver.org/), à partir de `1.0.0`.
La branche `1.x` est compatible avec les applications écrites pour Camunda 7.24.

<a id="maven-coordinates"></a>

## Coordonnées Maven

Les artefacts de Fasti BPM Platform disposent de leurs propres coordonnées Maven. **Les packages Java
restent `org.camunda.bpm.*`**, de sorte que le code des applications conserve ses imports ; le préfixe de configuration `camunda.bpm.*`,
les espaces de noms BPMN/DMN, le schéma de base de données et les noms de modules sur
WildFly restent également inchangés. Pour Maven, seules les déclarations de dépendances
changent (le chemin des applications web change séparément, voir
[Chemin des applications web](#web-application-path-fasti)) :

- **groupId :** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>` ; les groupes `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` et `org.camunda.template-engines` → `dev.fasti.platform.<group name>`.
- **artifactId :** le préfixe `camunda-` devient `fasti-`, le reste est conservé.
- **version :** la version de Fasti BPM Platform (voir [Gestion des versions](#versioning)) à la place de
  `7.24.0`.

Aucune version de ces artefacts n’a encore été publiée sur Maven Central. En attendant, compilez
à partir des sources et mettez les artefacts à disposition dans le dépôt Maven utilisé par votre projet.

Gérez les versions avec la BOM :

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.fasti.platform</groupId>
      <artifactId>fasti-bom</artifactId>
      <version>${fasti.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Artefacts fréquemment utilisés :

| Camunda 7 | Fasti BPM Platform |
|---|---|
| `org.camunda.bpm:camunda-bom` | `dev.fasti.platform:fasti-bom` |
| `org.camunda.bpm:camunda-engine` | `dev.fasti.platform:fasti-engine` |
| `org.camunda.bpm:camunda-engine-spring` | `dev.fasti.platform:fasti-engine-spring` |
| `org.camunda.bpm:camunda-engine-spring-6` | `dev.fasti.platform:fasti-engine-spring-6` |
| `org.camunda.bpm:camunda-engine-cdi-jakarta` | `dev.fasti.platform:fasti-engine-cdi-jakarta` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-rest` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-webapp` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-webapp` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-security` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-security` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-external-task-client` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-external-task-client` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-test` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-test` |
| `org.camunda.bpm:camunda-engine-rest-core` | `dev.fasti.platform:fasti-engine-rest-core` |
| `org.camunda.bpm:camunda-engine-rest-core-jakarta` | `dev.fasti.platform:fasti-engine-rest-core-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp` | `dev.fasti.platform.webapp:fasti-webapp` |
| `org.camunda.bpm.webapp:camunda-webapp-jakarta` | `dev.fasti.platform.webapp:fasti-webapp-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp-webjar` | `dev.fasti.platform.webapp:fasti-webapp-webjar` |
| `org.camunda.bpm:camunda-external-task-client` | `dev.fasti.platform:fasti-external-task-client` |
| `org.camunda.bpm:camunda-engine-plugin-spin` | `dev.fasti.platform:fasti-engine-plugin-spin` |
| `org.camunda.bpm:camunda-engine-plugin-connect` | `dev.fasti.platform:fasti-engine-plugin-connect` |
| `org.camunda.spin:camunda-spin-dataformat-all` | `dev.fasti.platform.spin:fasti-spin-dataformat-all` |
| `org.camunda.connect:camunda-connect-connectors-all` | `dev.fasti.platform.connect:fasti-connect-connectors-all` |
| `org.camunda.bpm.dmn:camunda-engine-dmn` | `dev.fasti.platform.dmn:fasti-engine-dmn` |
| `org.camunda.bpm.model:camunda-bpmn-model` | `dev.fasti.platform.model:fasti-bpmn-model` |
| `org.camunda.bpm:camunda-bpm-assert` | `dev.fasti.platform:fasti-bpm-assert` |
| `org.camunda.bpm:camunda-bpm-junit5` | `dev.fasti.platform:fasti-bpm-junit5` |

La bibliothèque FEEL `org.camunda.feel:feel-engine` est une bibliothèque externe qui ne fait pas partie de
ce dépôt ; ses coordonnées restent inchangées.

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

### Bibliothèques tierces qui incluent encore le moteur d’origine

Les packages étant inchangés, le moteur d’origine (`org.camunda.bpm:camunda-engine`)
et `fasti-engine` contiennent des classes portant les mêmes noms. Maven les traite comme deux
bibliothèques indépendantes et n’en élimine aucune. Si une bibliothèque tierce (une extension communautaire,
par exemple) dépend des artefacts d’origine, excluez-les et déclarez les artefacts
Fasti correspondants à leur place :

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>some-camunda-extension</artifactId>
  <version>1.2.3</version>
  <exclusions>
    <exclusion>
      <groupId>org.camunda.bpm</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.model</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.dmn</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.commons</groupId>
      <artifactId>*</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

`mvn dependency:tree -Dincludes="org.camunda*"` répertorie les artefacts d’origine encore
présents (`org.camunda.feel:feel-engine` est légitime, ne l’excluez pas).

<a id="startup-check"></a>

### Vérification au démarrage

Lors de sa création, le moteur inspecte le chemin de classes et **refuse de démarrer** s’il trouve
les classes du moteur de processus dans plusieurs bibliothèques ; l’erreur `ENGINE-12021` indique
l’emplacement de chacune d’elles. Le starter Spring Boot effectue la même vérification dès le
démarrage de l’application. Une bibliothèque visible uniquement via le chargeur de classes parent (par exemple, un moteur
partagé du serveur à côté d’un moteur embarqué dans une application web) n’est pas
comptabilisée. Si votre configuration est intentionnelle, la vérification peut être désactivée avec une propriété système :

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

Limite : si la bibliothèque du moteur d’origine précède `fasti-engine` dans le chemin de classes, le
moteur est construit par les classes de la bibliothèque d’origine, qui ne contiennent pas cette vérification.
Dans ce cas, seules les applications utilisant le starter Spring Boot de Fasti sont arrêtées ; pour
les autres intégrations, examinez les dépendances comme décrit ci-dessus.

<a id="web-application-path-fasti"></a>

## Chemin des applications web : `/fasti`

Cockpit, Tasklist, Admin et la nouvelle interface web sont accessibles sous `/fasti` (anciennement `/camunda`)
dans toutes les distributions : `http://localhost:8080/fasti/` ouvre la nouvelle interface
(`/fasti/app/next/default/`), les applications classiques se trouvent sur `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` et `/fasti/app/admin/...`. L’API REST reste sur `/engine-rest`.

**Les applications classiques sont obsolètes.** Les applications classiques Cockpit, Tasklist, Admin et Welcome
reposent sur AngularJS 1.8.2 et Bootstrap 3, tous deux en fin de vie. **L’image de conteneur ne les
expose pas par défaut** (modification incompatible) : leurs pages et les anciens favoris redirigent vers la section correspondante de la nouvelle interface, et les requêtes visant leurs fichiers renvoient `404` ; `FASTI_CLASSIC_WEBAPPS=true` les
réactive (avec un avertissement), voir [distro/docker/README.md](distro/docker/README.md). Les
distributions Tomcat et WildFly ne les exposent pas non plus par défaut (réactivez-les avec
`-Dfasti.webapp.classic.enabled=true` ou `FASTI_CLASSIC_WEBAPPS=true`). L’archive zip Run et le starter Spring
Boot continuent de les exposer comme auparavant, afin de ne pas perturber les installations en service ; désactivez-les
avec `fasti.webapp.classic.enabled: false`. Les applications classiques seront supprimées dans une
version ultérieure.

L’ancien chemin continue de fonctionner : toute requête vers `/camunda/...` est redirigée vers le même chemin
sous `/fasti/...`, en conservant le reste du chemin et la chaîne de requête. GET et HEAD reçoivent
`301` ; toutes les autres méthodes (POST, PUT, DELETE, par exemple les anciens clients appelant `/camunda/api/...`)
reçoivent `308`, afin que les clients répètent la requête avec la même méthode et le même corps. Les redirections
comportent `Cache-Control: no-store`, de sorte qu’un retour à `/camunda` prend effet immédiatement.

Pour rétablir `/camunda` :

- **Spring Boot, Run :** `camunda.bpm.webapp.application-path: /camunda` (aucune redirection dans ce cas).
  Pour désactiver uniquement la redirection : `fasti.webapp.legacy-path-redirect: false`.
- **Tomcat :** supprimez `webapps/camunda` (l’application de redirection) et renommez
  `webapps/fasti` en `webapps/camunda`.
- **WildFly :** supprimez `standalone/deployments/camunda.war` (ainsi que `camunda.war.dodeploy`) et
  définissez `<context-root>/camunda</context-root>` dans le fichier `WEB-INF/jboss-web.xml` du WAR de
  l’application web.

**Proxys inverses :** si un proxy ne transmet que `/camunda/` (ou réécrit les chemins, définit les chemins
des cookies, la CSP ou les contrôles d’état pour `/camunda`), ajoutez `/fasti/` ou passez entièrement à `/fasti/`.
Le cookie `XSRF-TOKEN` (et `JSESSIONID` sur Tomcat et WildFly) utilise désormais le chemin `/fasti`,
les utilisateurs doivent donc se reconnecter. Les favoris et les anciens liens continuent de fonctionner grâce à la redirection.

<a id="single-sign-on-openid-connect"></a>

## Authentification unique (OpenID Connect)

Prise en charge sur **Run** (`./start.sh --oauth2`, module `internal/oauth2`) et dans les applications **Spring Boot**
avec `fasti-bpm-spring-boot-starter-security`. Elle est activée en déclarant un
enregistrement de client Spring Security (`spring.security.oauth2.client.*`). Pour **Tomcat** et
**WildFly**, voir la fin de cette section.

- **Connexion :** la page de connexion de la nouvelle application web affiche « Se connecter avec &lt;provider&gt; »
  à la place du formulaire de mot de passe (libellé : `fasti.security.oidc.login-label`, valeur par défaut :
  `client-name` ; avec plusieurs enregistrements, choisissez-en un avec
  `fasti.security.oidc.registration-id`). Le flux de code d’autorisation utilise `state`, `nonce`
  et PKCE (S256, y compris pour les clients confidentiels) ; l’identifiant de session est remplacé lors de la connexion. Les utilisateurs
  reviennent à la page de la nouvelle application web depuis laquelle ils ont commencé (seuls les chemins sous
  `/fasti/app/next/` sont acceptés, ce qui évite les redirections ouvertes).
- **Déconnexion :** `POST /logout` (requêtes de même origine uniquement) termine la session ; avec
  `camunda.bpm.oauth2.sso-logout.enabled=true`, l’utilisateur est ensuite dirigé vers le point de terminaison de fin de
  session du fournisseur (déconnexion initiée par la partie utilisatrice, `post-logout-redirect-uri`). La
  déconnexion par canal arrière de Spring Security reste activée. `GET /logout` ne déconnecte plus l’utilisateur.
- **Expiration de la session :** lorsque le jeton d’accès ne peut pas être renouvelé ou que la session expire,
  les requêtes vers `/fasti/api/**` renvoient `401` (sans redirection) et la nouvelle application web affiche sa page de connexion.
- **CSRF :** `/fasti/api` et `/fasti/app` conservent le filtre CSRF propre aux applications web
  (`X-XSRF-TOKEN`) ; la protection CSRF de Spring Security reste désactivée, comme dans le projet amont.
- **Groupes, administrateurs, tenants :** l’attribut de jeton contenant les groupes est
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` (nom de propriété du projet amont ; les attributs
  imbriqués tels que `realm_access.roles` fonctionnent). `fasti.security.oidc.groups.admin` répertorie les
  valeurs d’attribut qui accordent `camunda-admin` ; `groups.mapping` associe les valeurs d’attribut aux identifiants de groupe
  du moteur ; `groups.include-unmapped=false` élimine les valeurs sans correspondance. **Modification par rapport au projet amont :** une
  valeur d’attribut `camunda-admin` n’accorde plus à elle seule les droits d’administrateur, et
  les autorités telles que `SCOPE_openid` ou `OIDC_USER` ne deviennent plus des groupes.
  `fasti.security.oidc.tenants.claim` définit la correspondance des tenants. Nom et adresse e-mail :
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Fournisseur d’identité :** par défaut, le moteur lit les utilisateurs, groupes et tenants dans le
  jeton de la session courante (en lecture seule). Définissez
  `camunda.bpm.oauth2.identity-provider.enabled=false` pour utiliser la base de données à la place (les groupes
  proviennent alors des tables d’appartenance). Le provisionnement automatique n’est pas implémenté.
  Tant que les utilisateurs proviennent du jeton (comportement par défaut), la page de configuration initiale (premier administrateur)
  est désactivée, puisque les administrateurs proviennent des attributs du jeton (`groups.admin`) ; auparavant, les utilisateurs extérieurs au groupe des administrateurs étaient dirigés vers la page de configuration.
- **API REST avec jetons Bearer :** `fasti.security.oidc.rest.bearer-enabled=true` accepte
  `Authorization: Bearer <JWT>` sur `/engine-rest` en plus de l’authentification Basic.
  `rest.issuer-uri` et `rest.audiences` sont obligatoires ; les signatures sont vérifiées à partir du
  JWKS de l’émetteur avec des algorithmes asymétriques uniquement (`rest.jws-algorithms`, valeur par défaut : `RS256`), et
  `iss`, `aud`, `exp`, `nbf` sont validés. Run nécessite également `camunda.bpm.run.auth.enabled=true`.
  Un cookie de session de navigateur n’authentifie jamais les requêtes vers `/engine-rest`.

Exemple Keycloak (`configuration/default.yml` de Run) :

```yaml
spring.security.oauth2.client:
  registration.keycloak:
    client-id: fasti
    client-secret: ${KEYCLOAK_CLIENT_SECRET}
    client-name: Keycloak
    scope: openid, profile, email
  provider.keycloak:
    issuer-uri: https://sso.example.com/realms/fasti
    user-name-attribute: preferred_username
camunda.bpm:
  oauth2:
    # the client's "Group Membership" mapper, with "Full group path" off
    identity-provider.group-name-attribute: groups
    sso-logout:
      enabled: true
      post-logout-redirect-uri: "{baseUrl}/fasti/app/next/default/"
  run.auth.enabled: true
fasti.security.oidc:
  groups.admin: [fasti-admins]
  rest:
    bearer-enabled: true
    issuer-uri: https://sso.example.com/realms/fasti
    audiences: [fasti-api]          # "Audience" mapper in Keycloak
    user-id-claim: preferred_username
```

Exemple Microsoft Entra ID :

```yaml
spring.security.oauth2.client:
  registration.entra:
    client-id: <application-id>
    client-secret: ${ENTRA_CLIENT_SECRET}
    client-name: Microsoft
    scope: openid, profile, email
  provider.entra:
    issuer-uri: https://login.microsoftonline.com/<tenant-id>/v2.0
    user-name-attribute: preferred_username
camunda.bpm.oauth2:
  # app registration manifest: "groupMembershipClaims": "SecurityGroup"
  identity-provider.group-name-attribute: groups
  sso-logout.enabled: true
fasti.security.oidc:
  groups:
    admin: ["<object id of the administrators group>"]
    mapping:
      "<object id of the accounting group>": accounting
    include-unmapped: false          # Entra sends object ids, not group names
  rest:
    bearer-enabled: true
    issuer-uri: https://login.microsoftonline.com/<tenant-id>/v2.0
    audiences: ["api://<application-id>"]   # v2 access tokens: accessTokenAcceptedVersion = 2
    user-id-claim: preferred_username
```

**Tomcat, WildFly (WAR) :** les mêmes fonctionnalités, configurées dans `fasti-oidc.properties` (Tomcat :
`conf/`, WildFly : `standalone/configuration/` ; un fichier annoté `fasti-oidc.properties.example`
se trouve à côté). Sans ce fichier, rien ne change. Les clés correspondent aux propriétés `fasti.security.oidc.*` de Run, auxquelles s’ajoutent `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`. Le
fournisseur doit autoriser l’URI de redirection `<base>/fasti/sso/callback` ; l’URL de déconnexion par canal arrière est
`<base>/fasti/sso/backchannel-logout`. Les jetons Bearer sur `/engine-rest` sont activés avec
`fasti.security.oidc.rest.bearer-enabled=true` (l’authentification Basic continue de fonctionner sauf si
`rest.basic-enabled=false` ; la liste des moteurs `/engine` exige également des identifiants). Les utilisateurs n’ont pas besoin
d’exister dans la base de données. Pour que le moteur réponde aux requêtes concernant les groupes et les tenants de
l’utilisateur connecté à partir des attributs du jeton, activez `OidcIdentityProviderPlugin` (mis en commentaire dans
`bpm-platform.xml` / `standalone.xml`). Limites : les sessions suivies pour la déconnexion par canal arrière sont
conservées en mémoire sur chaque serveur (elles ne sont pas partagées entre les nœuds du cluster) ; les applications classiques se contentent de rediriger vers le fournisseur ; une déconnexion complète nécessite la nouvelle interface web.

<a id="build"></a>

## Compilation

Nécessite JDK 17 ou version ultérieure (21 recommandé).

```bash
./mvnw clean verify
```

Une compilation complète exécute tous les tests Java et les tests unitaires de l’interface web (près d’une heure ; les tests
de bout en bout Playwright s’exécutent séparément, voir [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)) et nécessite un accès réseau à Maven Central,
nodejs.org et au registre npm. Voir [TESTING.md](TESTING.md).

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

[Licence Apache 2.0](LICENSE). Voir [NOTICE](NOTICE) pour les droits d’auteur du code d’origine.
