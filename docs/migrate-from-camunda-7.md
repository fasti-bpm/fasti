English | [Tiếng Việt](migrate-from-camunda-7.vi.md) | [Deutsch](migrate-from-camunda-7.de.md) | [Español](migrate-from-camunda-7.es.md) | [Français](migrate-from-camunda-7.fr.md) | [हिन्दी](migrate-from-camunda-7.hi.md) | [Bahasa Indonesia](migrate-from-camunda-7.id.md) | [日本語](migrate-from-camunda-7.ja.md) | [한국어](migrate-from-camunda-7.ko.md) | [Português (Brasil)](migrate-from-camunda-7.pt-BR.md) | [Русский](migrate-from-camunda-7.ru.md) | [ไทย](migrate-from-camunda-7.th.md) | [简体中文](migrate-from-camunda-7.zh-CN.md)

# Moving from Camunda 7 to Fasti BPM

Fasti BPM is built on Camunda 7 Community Edition 7.24.0 and is compatible with Camunda 7
databases. It is an independent project, not affiliated with Camunda Services GmbH.

<a id="summary"></a>

## Summary

If you run Camunda 7 **7.24** (Run, the container image, the Spring Boot starter, Tomcat or
WildFly), Fasti runs on **that same database without changing its schema**: it uses the 7.24
schema unchanged and writes no tables, columns or schema versions of its own. You can replace
the old nodes, run Fasti next to them, and go back to Camunda 7.24 (see
[Rollback](#rollback-to-camunda-724) and the tested databases under [Limits](#limits)).

Databases of Camunda 7.23 and earlier must first be upgraded to 7.24 with the SQL scripts (see
[Older versions](#older-versions-717723)). Fasti never upgrades a schema itself; it refuses to
start on an older schema and names the scripts to run.

<a id="steps-container-image"></a>

## Steps (container image)

1. **Back up the database.** None of the steps below changes the schema, but a backup is
   mandatory before any change to a production system.
2. **Check the schema version:** `select VERSION_ from ACT_GE_SCHEMA_LOG` — the highest value
   must be `7.24.x`.
3. **Write down the current configuration** of the old node: `DB_*` variables, history level
   (`camunda.bpm.history-level`, `ACT_GE_PROPERTY.historyLevel`), REST authentication
   (`camunda.bpm.run.auth.enabled`), authorizations (`camunda.bpm.authorization.enabled`),
   plugins, LDAP/SSO, JVM time zone, password hashing (`passwordEncryptor`, `saltGenerator`),
   table prefix/schema.
4. **Pull the image** `fastidev/fasti-bpm:1.1.0` (or build it from source as described in
   [distro/docker/README.md](../distro/docker/README.md)).
5. **Start Fasti with the same `DB_*` variables** (and the same `TZ`). Unlike Camunda Run, the
   Fasti image **requires REST authentication and enforces authorizations by default**: database
   accounts keep working, but every REST client must send Basic credentials or a bearer token,
   and users outside `camunda-admin` need grants in `ACT_RU_AUTHORIZATION` (see "Secure by
   default" in [distro/docker/README.md](../distro/docker/README.md)).
   `FASTI_INSECURE_DEMO=true` is for demos only. Fasti logs
   `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
6. **Check:** sign in at `/fasti/` with an existing account; check that filters, tasks, instances and history are there; call `GET /engine-rest/task` (with Basic credentials) and `GET /actuator/health/readiness`.
7. **Move traffic** (load balancer, DNS) to Fasti; stop the old nodes or keep them running (see
   [Mixed operation](#mixed-operation-with-camunda-7-nodes)).

Spring Boot starter / Tomcat / WildFly: replace the `org.camunda.bpm:camunda-*` artifacts with
the matching `dev.fasti.platform:fasti-*` ones and keep `camunda.bpm.*`, `bpm-platform.xml` and
the `org.camunda.bpm.*` packages. Do not keep both engine jars on the classpath; Fasti then refuses to start (for the limits of this check see "Startup check" in
[embedding.md](embedding.md#startup-check)).

> **Spring Boot starter:** Fasti runs on **Spring Boot 4.1** and needs **Java 17 or later**.
> Applications on the Camunda 7.24 starter (Spring Boot 3.5) must move to Spring Boot 4.1 and
> Java 17+ at the same time as they switch artifacts. See
> [embedding.md](embedding.md#what-is-different-from-camunda-7240-ce) and the
> [CHANGELOG](../CHANGELOG.md#changed).

<a id="tomcat-and-wildfly-distributions"></a>

## Tomcat and WildFly distributions

> **Required when taking over the database of Camunda Run** (or of a server whose processes were
> deployed through the REST API): the Tomcat and WildFly distributions keep
> `jobExecutorDeploymentAware=true` like Camunda 7. Jobs (timers, async continuations, retries)
> of deployments that are not registered on the node **do not run, and no error is reported**.
> Before starting, set `<property name="jobExecutorDeploymentAware">false</property>` in
> `conf/bpm-platform.xml` (Tomcat) or in the engine properties of `standalone.xml` (WildFly).
> Keep `true` if other nodes run process applications with Java classes of their own (see
> [Mixed operation](#mixed-operation-with-camunda-7-nodes)).

Compared with the Tomcat/WildFly distributions of Camunda 7.24:

- **The REST API requires authentication** (breaking). `/engine-rest` has the `camunda-auth`
  filter (HTTP Basic) enabled: every REST client must send the user name and password of an engine user; otherwise it receives 401; only the list of engines (`/engine`) stays open.
  Configure credentials in REST clients, external task workers and scripts that call the REST
  API anonymously before you switch. OpenID Connect bearer tokens (`fasti-oidc.properties`,
  `fasti.security.oidc.rest.bearer-enabled=true`) also work: requests with a valid token need no password. To switch authentication off (**not recommended**), remove the
  `camunda-auth` filter and its `filter-mapping` from `WEB-INF/web.xml` of the `engine-rest` WAR.
- **No default users** (breaking). The invoice example is no longer deployed, so it does not
  create `demo/demo` (administrator), `john`, `mary` and `peter`; its WAR is
  `examples/camunda-invoice.war`, to be copied to `webapps/` (Tomcat) or
  `standalone/deployments/` (WildFly) to try it out. On an empty database open `/fasti/`
  and create the first administrator on the setup page; on a Camunda 7 database use the
  existing accounts.
- **The classic Cockpit, Tasklist, Admin and Welcome are not served** (breaking), as in the
  image: their pages and bookmarks lead to the matching section of the new UI, requests for their files return 404. To re-enable them temporarily (deprecated, to be removed), use one of these settings:
  `-Dfasti.webapp.classic.enabled=true` in `CATALINA_OPTS` (Tomcat) or `JAVA_OPTS` (WildFly), the
  environment variable `FASTI_CLASSIC_WEBAPPS=true`, or the context parameter
  `fasti.webapp.classic.enabled` in `WEB-INF/web.xml` of the web application.

<a id="what-is-identical"></a>

## What is identical

- The schema and the rows of `ACT_GE_PROPERTY`/`ACT_GE_SCHEMA_LOG` (Fasti adds no version row),
  the installation id, id generation (`StrongUuidGenerator` in Run/Spring Boot, the engine's
  default `DbIdGenerator`).
- Passwords: SHA-512 with a 16-byte salt like Camunda 7; existing users can sign in, and
  passwords changed on Fasti work on Camunda 7.
- The REST API at `/engine-rest`: the same endpoints, parameters and payloads, plus additional
  endpoints (authentication and some status codes differ, see [What differs](#what-differs)); external tasks
  (`fetchAndLock`, long polling), the Java and JS clients, `JSESSIONID`, `XSRF-TOKEN`/`X-XSRF-TOKEN`, `camunda.bpm.*`
  properties, the `camunda:` BPMN namespace.
- Filters from the classic Tasklist (`color`, `description`, `priority`, `refresh`, `variables`,
  `showUndefinedVariable`) are read and written in the same format by the new one.

<a id="what-differs"></a>

## What differs

- Web applications live at `/fasti/app/...`; `/camunda/...` redirects there (301/308). If your proxy exposes only `/camunda`, expose `/fasti` as well or set `FASTI_WEBAPP_PATH=/camunda`. The root
  opens the new web application (`fasti.webapp.default-app: welcome` restores Welcome, except
  in the image, where the classic applications are off).
- **Breaking, container image:** the classic Cockpit, Tasklist, Admin and Welcome are not
  served, because AngularJS 1.8.2 and Bootstrap 3 are end of life. Their pages and bookmarks,
  also below `/camunda/app/`, redirect to the matching section of the new web UI (`#/cockpit`,
  `#/tasklist`, `#/admin`); requests for their files return 404. Move users to the new web UI;
  `FASTI_CLASSIC_WEBAPPS=true` serves the classic applications again during a transition period, with a
  warning. Tomcat and WildFly do not serve them either (see
  [Tomcat and WildFly distributions](#tomcat-and-wildfly-distributions)). The Run zip and the
  Spring Boot starter still serve them, but they are deprecated and will be removed in a later
  release (`fasti.webapp.classic.enabled: false` turns them off early).
- With an older schema, Fasti refuses to start with `ENGINE-03111` instead of failing later
  (`FASTI_SCHEMA_VERSION_CHECK=warn` to start anyway).
- Expressions in queries and filters are limited to an allow-list (date functions, current
  user); filters using custom beans or functions need them declared in the engine configuration
  properties `allowedExpressionFunctionsInQueries`, `allowedExpressionBeansInQueries` and
  `allowedExpressionPropertiesInQueries`.
- REST API behaviour: in the container image and the Tomcat and WildFly distributions every
  request except the list of engines (`/engine`) needs Basic credentials or a bearer token, so
  REST clients and external task workers must be configured to send them (their code does not
  change). In every distribution, query
  expressions outside the allow-list are rejected with `400`; reading or changing task
  attachments and comments without permission is answered with `403` (with authorization
  checks on); some invalid requests that were answered with `500` are answered with `400`
  (unparsable deployment resources, variables in the prohibited Java serialization format,
  query parameters of the wrong type, passwords that violate the password policy). See
  [Differences from Camunda 7](api/README.md#differences-from-camunda-7).
- Image defaults: schema update `false` (`FASTI_DB_SCHEMA_UPDATE=true` for a new database); no
  demo user, invoice example or "All tasks" filter; CORS off (`FASTI_CORS_ALLOWED_ORIGINS=*`
  restores it); `/actuator/health` added; files below `/fasti` (files mounted at
  `/camunda/configuration` are read too); no `--swaggerui`.
- OAuth2/OIDC: `camunda-admin` in a group claim no longer grants administrator rights by itself
  (`fasti.security.oidc.groups.admin`); `group-name-delimiter` is literal text, not a regex;
  logout needs a same-origin POST.
- New Tasklist: a filter without `refresh` refreshes automatically.
- The Run jar is `fasti-bpm-run-core.jar`.
- Security fixes relative to 7.24.0 (attachment/comment authorization, initial setup, ...) can make some previously successful calls return 403; see the *Security* sections of the [CHANGELOG](../CHANGELOG.md) and <https://fasti.dev/security>.

<a id="rollback-to-camunda-724"></a>

## Rollback to Camunda 7.24

Stop Fasti and start the Camunda 7.24 nodes on the same database again. Everything done on
Fasti is visible there. What remains: `fasti.prefs.*` rows in `ACT_ID_INFO` (preferences of the
new web UI, ignored by Camunda 7) and `refresh: true` on filters saved by the new Tasklist where
it was absent. Deployed processes that use Fasti-only features do not run on Camunda 7.

<a id="mixed-operation-with-camunda-7-nodes"></a>

## Mixed operation with Camunda 7 nodes

Fasti and Camunda 7.24 can run on one database at the same time (tested: both see each other's
data and jobs run on either node). Requirements:

- the **same history level** (the image uses the stored level), the **same JVM time zone**, the
  same password settings and table prefix;
- **job executor:** by default Fasti (image, Run, Spring Boot) picks up the jobs of every deployment, as Run does.
  The Tomcat and WildFly distributions default to `jobExecutorDeploymentAware=true` (as Camunda
  7 does): jobs (timers, async continuations, retries) of deployments not registered on that
  node, including deployments made by Run or through REST before the last restart, **do not
  run**; when taking over the database of Run, set it to `false` in `conf/bpm-platform.xml`
  (Tomcat) or `standalone.xml` (WildFly). If the old nodes run process applications with classes that Fasti does not have (`JavaDelegate`, listeners, beans),
  Fasti would pick up their jobs, which would then fail and raise incidents. Then set
  `FASTI_JOB_EXECUTOR_ENABLED=false` on Fasti, or make **every** node deployment-aware (a node's
  job executor only runs jobs of deployments registered on that node; a deployment made through
  REST is registered only on the node that received it, until a restart). Synchronous REST calls (completing a task,
  starting an instance) still run delegates on the node that receives them — do not route such
  processes to Fasti;
- **load balancer:** sticky sessions for the web applications; Basic-auth REST calls need none;
- the classic web applications (on Camunda 7 nodes, or Fasti with
  `FASTI_CLASSIC_WEBAPPS=true`) and the new one share filters and users.

<a id="older-versions-717723"></a>

## Older versions (7.17–7.23)

Back up; run `sql/upgrade/<db>_engine_<from>_to_<to>.sql` in order up to 7.24, including
outstanding patch scripts of your current minor version (shipped in `configuration/sql/upgrade`, in the
image `/fasti/configuration/sql/upgrade`); check that `ACT_GE_SCHEMA_LOG` contains `7.24.0`;
start Fasti. Example 7.22 → 7.24: `postgres_engine_7.22_to_7.23.sql`, then
`postgres_engine_7.23_to_7.24.sql`. Camunda nodes older than 7.24 must not keep running on the
upgraded schema except within Camunda's rolling-update rules (one minor version only, and the
old nodes must not use new features).

<a id="performance"></a>

## Performance

The image no longer takes the job executor and connection pool defaults of Camunda Run (up to Fasti
1.1 it did): it runs 10 job threads, a queue of 10 and 10 jobs per acquisition (Camunda Run: core 3,
max 10, queue 3, 3 per acquisition) and a connection pool of up to 20 connections, 10 of them kept
open when idle (Camunda Run: 10). Size the connection limit of the database for the pools of all
nodes together, Camunda 7 nodes included ([runtime tuning](tuning-runtime.md)). If you tuned the
connection pool or job executor of your former Camunda 7 installation, carry those values over
(`FASTI_DB_CONN_MAXACTIVE`, `FASTI_DB_CONN_MINIDLE`, `CAMUNDA_BPM_JOB_EXECUTION_*`); more guidance
is in `distro/docker/README.md`, "Performance tuning". Fasti never changes the indexes of the schema
by itself; for large databases it ships optional indexes as scripts an administrator runs
(`/fasti/configuration/sql/optional/<database>_fasti_perf_indexes_create.sql`, see
[performance.md](performance.md)), safe next to Camunda 7.24 nodes and removed by the matching
`_drop.sql`.

<a id="limits"></a>

## Limits

- Only the 7.24 Community Edition schema is supported; Enterprise patch schemas are untested.
- Tested with the container image (Camunda Run 7.24.0 creates and seeds the database → Fasti
  takes over → back to Run → both at once):

  | Database | Driver | Takeover | Rollback | Mixed | Upgrade 7.23 → 7.24 |
  |---|---|---|---|---|---|
  | PostgreSQL 16 | bundled | pass | pass | pass | not tested |
  | MariaDB 11.8 | MariaDB Connector/J (bundled) or MySQL Connector/J | pass | pass | pass | pass |
  | MySQL 8.4 | bundled | pass | — | pass | pass |
  | SQL Server 2022 (Developer) | bundled (`encrypt=false`) | pass | pass | pass | not tested |
  | Oracle Database 23ai Free | `ojdbc11` via `FASTI_EXTRA_LIB` | pass | pass | pass | not tested |
  | DB2 | — | not tested | | | |

  Also: two Fasti nodes on MariaDB (MariaDB Connector/J) completing the same tasks and fetching external tasks in parallel lost or duplicated nothing; the losing transaction received an optimistic locking error. MySQL/MariaDB need `transaction-isolation=READ-COMMITTED`. On MySQL
  give each engine its own database: the engine (Camunda Run and Fasti alike) looks for tables
  in every database the account can see.
- Plugins and process applications built for Camunda 7 must be rebuilt against the Fasti
  artifacts to run inside Fasti. REST clients and external task clients need no code changes;
  with the container image, Tomcat or WildFly they must be configured to send Basic credentials
  or a bearer token.
- The schema check reads `ACT_GE_SCHEMA_LOG` only; it does not compare columns.
