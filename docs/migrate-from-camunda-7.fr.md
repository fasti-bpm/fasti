[English](migrate-from-camunda-7.md) | [Tiếng Việt](migrate-from-camunda-7.vi.md) | [Deutsch](migrate-from-camunda-7.de.md) | [Español](migrate-from-camunda-7.es.md) | Français | [हिन्दी](migrate-from-camunda-7.hi.md) | [Bahasa Indonesia](migrate-from-camunda-7.id.md) | [日本語](migrate-from-camunda-7.ja.md) | [한국어](migrate-from-camunda-7.ko.md) | [Português (Brasil)](migrate-from-camunda-7.pt-BR.md) | [Русский](migrate-from-camunda-7.ru.md) | [ไทย](migrate-from-camunda-7.th.md) | [简体中文](migrate-from-camunda-7.zh-CN.md)

> Cette traduction est maintenue par la communauté et peut être en retard sur la version anglaise ; la version anglaise fait foi. Les corrections sont les bienvenues.

# Migration de Camunda 7 vers Fasti BPM

Fasti BPM repose sur Camunda 7 Community Edition 7.24.0 et est compatible avec les bases de données
Camunda 7. Il s’agit d’un projet indépendant, non affilié à Camunda Services GmbH.

<a id="summary"></a>

## Résumé

Si vous utilisez Camunda 7 **7.24** (Run, l’image de conteneur, le starter Spring Boot, Tomcat ou
WildFly), Fasti fonctionne sur **cette même base de données sans modifier son schéma** : il utilise le schéma 7.24
sans modification et n’ajoute aucune table, colonne ou version de schéma propre. Vous pouvez remplacer
les anciens nœuds, exécuter Fasti à leurs côtés et revenir à Camunda 7.24 (voir
[Retour](#rollback-to-camunda-724) et les bases de données testées sous [Limites](#limits)).

Les bases de données de Camunda 7.23 et des versions antérieures doivent d’abord être mises à niveau vers 7.24 à l’aide des scripts SQL (voir
[Versions antérieures](#older-versions-717723)). Fasti ne met jamais lui-même un schéma à niveau ; il refuse de
démarrer sur un schéma antérieur et indique les scripts à exécuter.

<a id="steps-container-image"></a>

## Étapes (image de conteneur)

1. **Sauvegardez la base de données.** Aucune des étapes ci-dessous ne modifie le schéma, mais une sauvegarde est
   obligatoire avant toute modification d’un système de production.
2. **Vérifiez la version du schéma :** `select VERSION_ from ACT_GE_SCHEMA_LOG` — la valeur la plus élevée
   doit être `7.24.x`.
3. **Notez la configuration actuelle** de l’ancien nœud : variables `DB_*`, niveau d’historique
   (`camunda.bpm.history-level`, `ACT_GE_PROPERTY.historyLevel`), authentification REST
   (`camunda.bpm.run.auth.enabled`), autorisations (`camunda.bpm.authorization.enabled`),
   plugins, LDAP/SSO, fuseau horaire de la JVM, hachage des mots de passe (`passwordEncryptor`, `saltGenerator`),
   préfixe des tables/schéma.
4. **Récupérez l’image** `fastidev/fasti-bpm:1.2.1` (ou construisez-la à partir des sources comme
   décrit dans [distro/docker/README.md](../distro/docker/README.md)).
5. **Démarrez Fasti avec les mêmes variables `DB_*`** (et le même `TZ`). Contrairement à Camunda Run,
   l’image Fasti **exige l’authentification REST et applique les autorisations par défaut** : les comptes de la base de données
   continuent de fonctionner, mais chaque client REST doit envoyer des identifiants Basic ou un jeton Bearer,
   et les utilisateurs hors de `camunda-admin` doivent disposer de droits dans `ACT_RU_AUTHORIZATION` (voir « Sécurisé par
   défaut » dans [distro/docker/README.md](../distro/docker/README.md)).
   `FASTI_INSECURE_DEMO=true` est réservé aux démonstrations. Fasti consigne
   `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
6. **Vérifiez :** connectez-vous à `/fasti/` avec un compte existant ; vérifiez que les filtres, tâches, instances et données d’historique sont présents ; appelez `GET /engine-rest/task` (avec des identifiants Basic) et `GET /actuator/health/readiness`.
7. **Basculez le trafic** (répartiteur de charge, DNS) vers Fasti ; arrêtez les anciens nœuds ou laissez-les fonctionner (voir
   [Fonctionnement mixte](#mixed-operation-with-camunda-7-nodes)).

Starter Spring Boot / Tomcat / WildFly : remplacez les artefacts `org.camunda.bpm:camunda-*` par
les artefacts `dev.fasti.platform:fasti-*` correspondants et conservez `camunda.bpm.*`, `bpm-platform.xml` et
les packages `org.camunda.bpm.*`. Ne conservez pas les deux JAR du moteur dans le classpath ; Fasti refuse alors de démarrer (pour les limites de cette vérification, voir « Vérification au démarrage » dans le
[embedding.fr.md](embedding.fr.md#startup-check)).

> **Starter Spring Boot :** Fasti fonctionne sur **Spring Boot 4.1** et nécessite **Java 17 ou une version ultérieure**.
> Les applications utilisant le starter Camunda 7.24 (Spring Boot 3.5) doivent passer à Spring Boot 4.1 et
> Java 17+ en même temps qu’elles changent d’artefacts. Consultez le
> [embedding.fr.md](embedding.fr.md#what-is-different-from-camunda-7240-ce) et le
> [journal des modifications](../CHANGELOG.md#changed).

<a id="tomcat-and-wildfly-distributions"></a>

## Distributions Tomcat et WildFly

> **Obligatoire lors de la reprise de la base de données de Camunda Run** (ou d’un serveur dont les processus ont été
> déployés via l’API REST) : les distributions Tomcat et WildFly conservent
> `jobExecutorDeploymentAware=true`, comme Camunda 7. Les jobs (minuteries, continuations asynchrones, nouvelles tentatives)
> des déploiements qui ne sont pas enregistrés sur le nœud **ne s’exécutent pas, et aucune erreur n’est signalée**.
> Avant le démarrage, définissez `<property name="jobExecutorDeploymentAware">false</property>` dans
> `conf/bpm-platform.xml` (Tomcat) ou dans les propriétés du moteur de `standalone.xml` (WildFly).
> Conservez `true` si d’autres nœuds exécutent des applications de processus avec leurs propres classes Java (voir
> [Fonctionnement mixte](#mixed-operation-with-camunda-7-nodes)).

Par rapport aux distributions Tomcat/WildFly de Camunda 7.24 :

- **L’API REST exige une authentification** (rupture de compatibilité). Le filtre `camunda-auth`
  (HTTP Basic) est activé sur `/engine-rest` : chaque client REST doit envoyer le nom d’utilisateur et le mot de passe d’un utilisateur du moteur ; sinon, il reçoit une réponse 401 ; seule la liste des moteurs (`/engine`) reste accessible sans authentification.
  Configurez les identifiants dans les clients REST, les workers de tâches externes et les scripts qui appellent l’API
  REST de manière anonyme avant de basculer. Les jetons Bearer OpenID Connect (`fasti-oidc.properties`,
  `fasti.security.oidc.rest.bearer-enabled=true`) fonctionnent également : les requêtes munies d’un jeton valide n’ont pas besoin de mot de passe. Pour désactiver l’authentification (**déconseillé**), supprimez le
  filtre `camunda-auth` et son `filter-mapping` du fichier `WEB-INF/web.xml` du WAR `engine-rest`.
- **Aucun utilisateur par défaut** (rupture de compatibilité). L’exemple de facturation n’est plus déployé et ne
  crée donc plus `demo/demo` (administrateur), `john`, `mary` et `peter` ; son WAR est
  `examples/camunda-invoice.war`, à copier dans `webapps/` (Tomcat) ou
  `standalone/deployments/` (WildFly) pour l’essayer. Sur une base de données vide, ouvrez `/fasti/`
  et créez le premier administrateur sur la page de configuration ; sur une base de données Camunda 7, utilisez les
  comptes existants.
- **Les applications classiques Cockpit, Tasklist, Admin et Welcome ne sont pas servies** (rupture de compatibilité), comme dans
  l’image : leurs pages et favoris mènent à la section correspondante de la nouvelle interface, et les requêtes visant leurs fichiers renvoient 404. Pour les réactiver temporairement (fonctionnalité obsolète, destinée à être supprimée), utilisez l’un des paramètres suivants :
  `-Dfasti.webapp.classic.enabled=true` dans `CATALINA_OPTS` (Tomcat) ou `JAVA_OPTS` (WildFly), la
  variable d’environnement `FASTI_CLASSIC_WEBAPPS=true`, ou le paramètre de contexte
  `fasti.webapp.classic.enabled` dans le fichier `WEB-INF/web.xml` de l’application web.

<a id="what-is-identical"></a>

## Ce qui reste identique

- Le schéma et les lignes de `ACT_GE_PROPERTY`/`ACT_GE_SCHEMA_LOG` (Fasti n’ajoute aucune ligne de version),
  l’identifiant d’installation, la génération des identifiants (`StrongUuidGenerator` dans Run/Spring Boot, le
  `DbIdGenerator` par défaut du moteur).
- Les mots de passe : SHA-512 avec un sel de 16 octets, comme Camunda 7 ; les utilisateurs existants peuvent se connecter, et
  les mots de passe modifiés sur Fasti fonctionnent sur Camunda 7.
- L’API REST à l’adresse `/engine-rest` : les mêmes points de terminaison, paramètres et contenus,
  plus des points de terminaison supplémentaires (l’authentification et certains codes de statut
  diffèrent, voir [Ce qui diffère](#what-differs)) ; les tâches externes
  (`fetchAndLock`, interrogation longue), les clients Java et JS, `JSESSIONID`, `XSRF-TOKEN`/`X-XSRF-TOKEN`, les propriétés
  `camunda.bpm.*`, l’espace de noms BPMN `camunda:`.
- Les filtres de la Tasklist classique (`color`, `description`, `priority`, `refresh`, `variables`,
  `showUndefinedVariable`) sont lus et écrits au même format par la nouvelle Tasklist.

<a id="what-differs"></a>

## Ce qui diffère

- Les applications web se trouvent à l’adresse `/fasti/app/...` ; `/camunda/...` y redirige (301/308). Si votre proxy expose uniquement `/camunda`, exposez également `/fasti` ou définissez `FASTI_WEBAPP_PATH=/camunda`. La racine
  ouvre la nouvelle application web (`fasti.webapp.default-app: welcome` rétablit Welcome, sauf
  dans l’image, où les applications classiques sont désactivées).
- **Rupture de compatibilité, image de conteneur :** les applications classiques Cockpit, Tasklist, Admin et Welcome ne sont pas
  servies, car AngularJS 1.8.2 et Bootstrap 3 sont en fin de vie. Leurs pages et favoris,
  y compris sous `/camunda/app/`, redirigent vers la section correspondante de la nouvelle interface web (`#/cockpit`,
  `#/tasklist`, `#/admin`) ; les requêtes visant leurs fichiers renvoient 404. Faites passer les utilisateurs à la nouvelle interface web ;
  `FASTI_CLASSIC_WEBAPPS=true` sert à nouveau les applications classiques pendant une période de transition, avec un
  avertissement. Tomcat et WildFly ne les servent pas non plus (voir
  [Distributions Tomcat et WildFly](#tomcat-and-wildfly-distributions)). L’archive ZIP Run et le
  starter Spring Boot les servent encore, mais elles sont obsolètes et seront supprimées dans une version
  ultérieure (`fasti.webapp.classic.enabled: false` permet de les désactiver dès maintenant).
- Avec un schéma antérieur, Fasti refuse de démarrer avec `ENGINE-03111` au lieu d’échouer plus tard
  (`FASTI_SCHEMA_VERSION_CHECK=warn` permet de démarrer malgré tout).
- Les expressions dans les requêtes et les filtres sont limitées à une liste d’autorisation (fonctions de date, utilisateur
  courant) ; les filtres utilisant des beans ou des fonctions personnalisés nécessitent leur déclaration dans les propriétés
  de configuration du moteur `allowedExpressionFunctionsInQueries`, `allowedExpressionBeansInQueries` et
  `allowedExpressionPropertiesInQueries`.
- Comportement de l’API REST : dans l’image conteneur et les distributions Tomcat et WildFly, chaque
  requête, sauf la liste des moteurs (`/engine`), exige des identifiants Basic ou un bearer token ;
  les clients REST et les workers de tâches externes doivent donc être configurés pour les envoyer
  (leur code ne change pas). Dans toutes les distributions, les expressions de requête hors de la
  liste autorisée sont refusées avec `400` ; la lecture ou la modification des pièces jointes et
  commentaires de tâches sans autorisation reçoit `403` (lorsque les contrôles d’autorisation sont
  actifs) ; certaines requêtes invalides qui recevaient `500` reçoivent désormais `400` (ressources
  de déploiement non analysables, variables au format de sérialisation Java interdit, paramètres de
  requête du mauvais type, mots de passe contraires à la politique de mots de passe). Voir
  [Differences from Camunda 7](api/README.md#differences-from-camunda-7) (en anglais).
- Valeurs par défaut de l’image : mise à jour du schéma à `false` (`FASTI_DB_SCHEMA_UPDATE=true` pour une nouvelle base de données) ; aucun
  utilisateur de démonstration, exemple de facturation ou filtre « Toutes les tâches » ; CORS désactivé (`FASTI_CORS_ALLOWED_ORIGINS=*`
  le réactive) ; ajout de `/actuator/health` ; fichiers sous `/fasti` (les fichiers montés dans
  `/camunda/configuration` sont également lus) ; absence de `--swaggerui`.
- OAuth2/OIDC : `camunda-admin` dans une revendication de groupe n’accorde plus à lui seul les droits d’administrateur
  (`fasti.security.oidc.groups.admin`) ; `group-name-delimiter` est du texte littéral, pas une expression régulière ;
  la déconnexion nécessite une requête POST de même origine.
- Nouvelle Tasklist : un filtre sans `refresh` s’actualise automatiquement.
- Le JAR Run est `fasti-bpm-run-core.jar`.
- Les correctifs de sécurité par rapport à 7.24.0 (autorisation des pièces jointes/commentaires, configuration initiale, ...) peuvent faire renvoyer 403 à certains appels qui aboutissaient auparavant ; consultez les sections *Security* du [CHANGELOG](../CHANGELOG.md) et <https://fasti.dev/security>.

<a id="rollback-to-camunda-724"></a>

## Retour à Camunda 7.24

Arrêtez Fasti et redémarrez les nœuds Camunda 7.24 sur la même base de données. Tout ce qui a été effectué sur
Fasti y est visible. Éléments qui subsistent : les lignes `fasti.prefs.*` dans `ACT_ID_INFO` (préférences de la
nouvelle interface web, ignorées par Camunda 7) et `refresh: true` sur les filtres enregistrés par la nouvelle Tasklist lorsque
cette propriété était absente. Les processus déployés qui utilisent des fonctionnalités propres à Fasti ne s’exécutent pas sur Camunda 7.

<a id="mixed-operation-with-camunda-7-nodes"></a>

## Fonctionnement mixte avec des nœuds Camunda 7

Fasti et Camunda 7.24 peuvent fonctionner simultanément sur une même base de données (vérifié : chacun voit les
données de l’autre et les jobs s’exécutent sur l’un ou l’autre nœud). Conditions requises :

- le **même niveau d’historique** (l’image utilise le niveau enregistré), le **même fuseau horaire de la JVM**, les
  mêmes paramètres de mot de passe et le même préfixe de tables ;
- **exécuteur de jobs :** par défaut, Fasti (image, Run, Spring Boot) prend en charge les jobs de tous les déploiements, comme Run.
  Les distributions Tomcat et WildFly utilisent par défaut `jobExecutorDeploymentAware=true` (comme Camunda
  7) : les jobs (minuteries, continuations asynchrones, nouvelles tentatives) des déploiements non enregistrés sur ce
  nœud, y compris ceux effectués par Run ou via REST avant le dernier redémarrage, **ne s’exécutent
  pas** ; lors de la reprise de la base de données de Run, définissez ce paramètre à `false` dans `conf/bpm-platform.xml`
  (Tomcat) ou `standalone.xml` (WildFly). Si les anciens nœuds exécutent des applications de processus avec des classes dont Fasti ne dispose pas (`JavaDelegate`, listeners, beans),
  Fasti prendrait en charge leurs jobs, qui échoueraient alors et déclencheraient des incidents. Dans ce cas, définissez
  `FASTI_JOB_EXECUTOR_ENABLED=false` sur Fasti, ou configurez **chaque** nœud pour tenir compte des déploiements (l’exécuteur de
  jobs d’un nœud n’exécute que les jobs des déploiements enregistrés sur ce nœud ; un déploiement effectué via
  REST n’est enregistré que sur le nœud qui l’a reçu, jusqu’à un redémarrage). Les appels REST synchrones (terminer une tâche,
  démarrer une instance) exécutent toujours les délégués sur le nœud qui les reçoit — ne dirigez pas ces
  processus vers Fasti ;
- **répartiteur de charge :** affinité de session pour les applications web ; les appels REST avec authentification Basic n’en ont pas besoin ;
- les applications web classiques (sur les nœuds Camunda 7, ou Fasti avec
  `FASTI_CLASSIC_WEBAPPS=true`) et la nouvelle partagent les filtres et les utilisateurs.

<a id="older-versions-717723"></a>

## Versions antérieures (7.17–7.23)

Effectuez une sauvegarde ; exécutez `sql/upgrade/<db>_engine_<from>_to_<to>.sql` dans l’ordre jusqu’à 7.24, y compris
les scripts correctifs non encore appliqués de votre version mineure actuelle (fournis dans `configuration/sql/upgrade`, dans
l’image `/fasti/configuration/sql/upgrade`) ; vérifiez que `ACT_GE_SCHEMA_LOG` contient `7.24.0` ;
démarrez Fasti. Exemple 7.22 → 7.24 : `postgres_engine_7.22_to_7.23.sql`, puis
`postgres_engine_7.23_to_7.24.sql`. Les nœuds Camunda antérieurs à 7.24 ne doivent pas continuer à fonctionner sur le
schéma mis à niveau, sauf dans le cadre des règles de mise à jour progressive de Camunda (une seule version mineure, et les
anciens nœuds ne doivent pas utiliser les nouvelles fonctionnalités).

<a id="performance"></a>

## Performances

L’image ne reprend plus les valeurs par défaut de Camunda Run pour l’exécuteur de jobs et le pool de
connexions (c’était le cas jusqu’à Fasti 1.1) : elle utilise 10 threads de jobs, une file de 10 et
10 jobs par acquisition (Camunda Run : 3 threads de base, 10 au maximum, file de 3, 3 par
acquisition) et un pool de 20 connexions au plus, dont 10 restent ouvertes au repos (Camunda Run :
10). Dimensionnez la limite de connexions de la base de données pour les pools de tous les nœuds
ensemble, nœuds Camunda 7 compris ([réglage à l’exécution](tuning-runtime.md), en anglais). Si vous
avez ajusté le pool de connexions ou l’exécuteur de jobs de votre ancienne installation Camunda 7,
reportez ces valeurs (`FASTI_DB_CONN_MAXACTIVE`, `FASTI_DB_CONN_MINIDLE`,
`CAMUNDA_BPM_JOB_EXECUTION_*`) ; d’autres conseils figurent dans `distro/docker/README.md`, à la
section « Réglage des performances ». Fasti ne modifie jamais de lui-même les index du schéma ; pour
les grandes bases de données, il fournit des index optionnels sous forme de scripts qu’un
administrateur exécute
(`/fasti/configuration/sql/optional/<database>_fasti_perf_indexes_create.sql`, voir
[performance.md](performance.md)), sans risque à côté de nœuds Camunda 7.24 et supprimés par le
`_drop.sql` correspondant.

<a id="limits"></a>

## Limites

- Seul le schéma de Community Edition 7.24 est pris en charge ; les schémas des correctifs Enterprise n’ont pas été testés.
- Tests effectués avec l’image de conteneur (Camunda Run 7.24.0 crée et initialise la base de données → Fasti
  prend le relais → retour à Run → les deux simultanément) :

  | Base de données | Pilote | Reprise | Retour arrière | Fonctionnement mixte | Mise à niveau 7.23 → 7.24 |
  |---|---|---|---|---|---|
  | PostgreSQL 16 | fourni | réussi | réussi | réussi | non testé |
  | MariaDB 11.8 | MariaDB Connector/J (fourni) ou MySQL Connector/J | réussi | réussi | réussi | réussi |
  | MySQL 8.4 | fourni | réussi | — | réussi | réussi |
  | SQL Server 2022 (Developer) | fourni (`encrypt=false`) | réussi | réussi | réussi | non testé |
  | Oracle Database 23ai Free | `ojdbc11` via `FASTI_EXTRA_LIB` | réussi | réussi | réussi | non testé |
  | DB2 | — | non testé | | | |

  Également : deux nœuds Fasti sur MariaDB (MariaDB Connector/J), terminant les mêmes tâches et récupérant des tâches externes en parallèle, n’ont rien perdu ni dupliqué ; la transaction perdante a reçu une erreur de verrouillage optimiste. MySQL/MariaDB nécessitent `transaction-isolation=READ-COMMITTED`. Sur MySQL,
  attribuez à chaque moteur sa propre base de données : le moteur (Camunda Run comme Fasti) recherche les tables
  dans toutes les bases de données auxquelles le compte a accès.
- Les plugins et applications de processus conçus pour Camunda 7 doivent être recompilés avec les artefacts
  Fasti pour s’exécuter dans Fasti. Les clients REST et les clients de tâches externes ne nécessitent aucune modification de code ;
  avec l’image conteneur, Tomcat ou WildFly, ils doivent être configurés pour envoyer des identifiants Basic
  ou un bearer token.
- La vérification du schéma lit uniquement `ACT_GE_SCHEMA_LOG` ; elle ne compare pas les colonnes.
