English | [Tiếng Việt](README.vi.md)

# Fasti BPM container image

The image runs **Fasti BPM Run** (engine, REST API at `/engine-rest` and the new web
application at `/fasti/`; the classic web applications are **off by default**, see below). It is made to be **pointed at the database an
existing Camunda 7 (7.24) installation uses** and to carry on with it: users, passwords,
groups, tenants, authorizations, filters, deployments, running instances, history, jobs and
incidents stay as they are. Fasti BPM is compatible with Camunda 7 databases; it is an
independent project, not affiliated with Camunda.

Full migration guide (steps, rollback, mixed operation, limits):
[docs/migrate-from-camunda-7.md](../../docs/migrate-from-camunda-7.md).

## Build

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:1.3.1 \
  --build-arg VERSION=1.3.1 --build-arg REVISION=$(git rev-parse --short HEAD) \
  distro/docker/target/docker
```

Offline: add `-o -Dfasti.docker.skipMariadbDriver=true` (the MariaDB driver is then left out,
see JDBC drivers). More repeatable builds: pin the base image by
digest (the `apk upgrade` step still takes the latest Alpine fixes) with `--build-arg JRE_IMAGE=eclipse-temurin:21-jre-alpine@sha256:<digest>`.

## Secure by default — different from Camunda Run

> **Deliberate difference:** Camunda Run and the former Camunda 7 image serve `/engine-rest`
> **without authentication** and with **authorizations off**. The Fasti image turns **both on by
> default**, with or without `--production`; only `FASTI_INSECURE_DEMO=true` (demos) turns them off.

- The REST API requires Basic authentication (or a bearer token with OIDC); authorization checks
  are always on. Users, groups and authorizations come from the database, so pointing `DB_*` at
  the database of an existing Camunda 7 installation keeps its logins (REST and web UI).
- **Migration impact:** every REST client (external task workers, integrations, scripts) must
  send `Authorization: Basic …` of a user with suitable permissions, or a bearer token. If the
  old installation ran with authorizations off, `ACT_RU_AUTHORIZATION` may lack grants: members
  of `camunda-admin` can still do everything, other users may see empty lists or get 403. Check
  first (`select * from ACT_ID_MEMBERSHIP where GROUP_ID_='camunda-admin'`,
  `select count(*) from ACT_RU_AUTHORIZATION`) and grant permissions in Admin before moving
  traffic.
- There is no anonymous "create the first administrator" page. For an empty database (or the H2
  demo) set `FASTI_ADMIN_USER` + `FASTI_ADMIN_PASSWORD` (or `_FILE`); an existing administrator
  is left unchanged (no password reset). `FASTI_INITIAL_SETUP=true` brings the setup page back;
  it asks for the one-time setup token printed to the container log (`docker logs`) when the page
  is first opened, or for `FASTI_SETUP_TOKEN`.
- The application **refuses to start** if the effective configuration (mounted YAML files,
  environment variables, `SPRING_APPLICATION_JSON`, arguments) turns REST authentication or
  authorization off, or sets `camunda.bpm.admin-user.id` without a password.
- **Open mode is for demos only:** `FASTI_INSECURE_DEMO=true` turns authentication and
  authorization off, logs a large warning and is refused together with `--production`. Publish
  the port on 127.0.0.1 only (`-p 127.0.0.1:8080:8080`) and never use it with real data.
- `--production` also refuses the implicit H2 database and `DEBUG=true`.

## New web UI only — classic web applications off by default

> **Breaking change:** the image serves only the new web UI (`/fasti/app/next/`). The classic
> Cockpit, Tasklist, Admin and Welcome are built on AngularJS 1.8.2 and Bootstrap 3, both end of
> life (no more security fixes), and are no longer served. Users of the classic applications in
> the image move to the new web UI or turn them back on with `FASTI_CLASSIC_WEBAPPS=true`.

- Pages of the classic applications and old bookmarks are redirected (`302`) to the matching
  section of the new web UI, keeping the engine name: `/fasti/app/cockpit/default/` →
  `/fasti/app/next/default/#/cockpit`, Tasklist → `#/tasklist`, Admin → `#/admin`, Welcome →
  the start page. `/camunda/app/...` goes through the `/camunda` → `/fasti` redirect and on to
  the new web UI. Routes inside a classic application (the part after `#`) cannot be
  translated: users land on the start page of the section.
- Scripts, styles and translations of the classic applications and the static files of classic
  plugins (`/fasti/api/{cockpit,tasklist,admin}/plugin/*/static/...`) answer `404`, also the
  copy Spring Boot serves below `/webjars/camunda/app/...`. The files
  stay in the image (so that they can be turned back on) but are not served.
- The REST resources of the web applications (`/fasti/api/...`) keep working: the new web UI
  uses them. The frame that renders embedded forms (`/fasti/app/forms/`) still loads
  `app/tasklist/scripts/config.js` and the custom scripts it names; the frame is sandboxed from
  the new web UI. The embedded forms frame itself still uses AngularJS (Camunda 7 embedded forms
  need it), so `/fasti/lib/` and `/fasti/assets/` stay served for it.
- `FASTI_CLASSIC_WEBAPPS=true` (or `fasti.webapp.classic.enabled=true`) serves the classic
  applications again, with a `WARN` at startup that they are deprecated and built on end-of-life
  AngularJS. They will be removed in a later release.
- A `fasti.webapp.default-app` that names a classic application is ignored (with a warning)
  while they are off.
- The new web UI has no CMMN screens yet; to look at CMMN cases in the classic Cockpit use
  `FASTI_CLASSIC_WEBAPPS=true`, or the REST API.

## Run against an existing database

```bash
docker run -d --name fasti -p 8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=UTC \
  fasti-bpm:1.3.1
```

`DB_PASSWORD_FILE` must name a file inside the container (a Docker/Kubernetes secret, or
`-v /path/to/db-password:/run/secrets/db-password:ro`); `DB_PASSWORD` works as well.
Open `http://localhost:8080/fasti/` and sign in with an existing account; REST clients send that
account's Basic credentials. New, empty database: add `FASTI_DB_SCHEMA_UPDATE=true` and
`FASTI_ADMIN_USER`/`FASTI_ADMIN_PASSWORD`. Without `DB_URL`/`FASTI_DB_URL` (and without `SPRING_DATASOURCE_URL`, `SKIP_DB_CONFIG`
or a configuration file of your own) the image uses H2 in `/fasti/data`, for trying out only.

## Environment variables

The names of the former Camunda 7 Run image and `FASTI_*` aliases are accepted (when both are
set, the `FASTI_*` value is used; `DEBUG` and `JMX_PROMETHEUS` are on if either name is `true`).
Any Spring Boot property can be set as an environment variable too (for example
`CAMUNDA_BPM_RUN_AUTH_ENABLED=true`). Entries marked **from 1.1.0** are not in the 1.0.x
images; entries marked **from 1.2.0** are not in the 1.1.x images; entries marked **from 1.3.0**
are not in the 1.2.x images.

| Variable | Default | Meaning |
|---|---|---|
| `DB_URL` / `FASTI_DB_URL` | (H2 demo) | JDBC URL |
| `DB_DRIVER` / `FASTI_DB_DRIVER` | derived from the URL | JDBC driver class |
| `DB_USERNAME`, `DB_PASSWORD` (+ `_FILE`) | | credentials; `_FILE` reads the value from a file (Docker/Kubernetes secret) |
| `DB_CONN_MAXACTIVE`, `DB_CONN_MINIDLE` | Hikari | HikariCP `maximum-pool-size`, `minimum-idle` |
| `DB_VALIDATE_ON_BORROW` + `DB_VALIDATION_QUERY` | | HikariCP `connection-test-query` |
| `DB_CONN_MAXIDLE` | | no HikariCP counterpart; ignored (logged) |
| `SKIP_DB_CONFIG=true` | | do not configure the datasource from `DB_*` (use a configuration file) |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | –, 30 | wait for `host:port[,host:port]` before starting |
| `TZ` | UTC | JVM time zone (`-Duser.timezone`); **must match every other node** |
| `DEBUG=true` | | remote debugging on port `FASTI_DEBUG_PORT` (8000) |
| `JMX_PROMETHEUS=true` | | Prometheus JMX exporter on port `JMX_PROMETHEUS_PORT` (9404), configuration `JMX_PROMETHEUS_CONF`, if the agent jar is in `/fasti/javaagent` |
| `JAVA_OPTS` | | additional JVM options (space-separated, no quotes) |
| `FASTI_MAX_RAM_PERCENTAGE` | 75 (60 below a 1.5 GiB limit) | percentage of container memory used for the heap. **From 1.3.2** the default is 60 when the container memory limit (cgroup v2 or v1) is below 1.5 GiB, as metaspace, code cache, threads and GraalJS need about 400-500 MB besides the heap |
| `SERVER_SERVLET_SESSION_COOKIE_NAME` | `FASTI_SESSION` | **From 1.3.2** (up to 1.3.1 `JSESSIONID`). Name of the session cookie of the web applications; keep it distinct from other applications on the same host, as browsers send cookies to every port of a host |
| `SERVER_TOMCAT_THREADS_MAX` | 100 | **From 1.3.2** (up to 1.3.1 Tomcat's 200). Request threads; see Performance tuning |
| `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` | 10000 | **From 1.3.2** (up to 1.3.1 HikariCP's 30000). Milliseconds a request waits for a free connection |
| `FASTI_ANALYTICS_CACHE_TTL` | `60s` | **From 1.3.0.** How long the answers of the process analytics are kept for the same view (`0` turns this off; a number without unit is seconds); what runs now may be that much older. Setting `fasti.analytics.cache-ttl` |
| `FASTI_ANALYTICS_MAX_CONCURRENT` | `2` | **From 1.3.0.** How many scans of the process analytics (activities, rework, user tasks) read the history at the same time on this node, plus one quick read (instances, trend, runs); the others wait for their turn without holding a database connection, so jobs and task lists keep theirs. `0`: no limit. Setting `fasti.analytics.max-concurrent` |
| `FASTI_ANALYTICS_MAX_WAIT` | `60s` | **From 1.3.0.** The longest wait for that turn; then the REST API answers `503` (a number without unit is seconds). Setting `fasti.analytics.max-wait` |
| `FASTI_ANALYTICS_STATEMENT_TIMEOUT` | `120s` | **From 1.3.2.** How long one statement of the process analytics may run; then the call answers `503` and its turn and connection are free again (`0`: no timeout of its own; a lower `jdbc-statement-timeout` of the engine applies either way; a number without unit is seconds). Setting `fasti.analytics.statement-timeout` |
| `FASTI_WORKLOAD_CACHE_TTL` | `5s` | **From 1.3.2.** How long the answer of `/task/workload` is kept for the same question of the same user (with the same groups and tenants); callers that ask the same at the same time share one read. `0` turns keeping off. Setting `fasti.workload.cache-ttl` |
| `FASTI_RETURN_MEMORY` | `true` | **From 1.2.1.** Return unused heap to the system (`-XX:G1PeriodicGCInterval=30000 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30`); `false` keeps the JVM defaults |
| `FASTI_DB_SCHEMA_UPDATE` | `false` (`true` for the built-in H2 demo database) | `true` creates missing tables (new database); never upgrades a schema |
| `FASTI_SCHEMA_VERSION_CHECK` | `enforce` | `warn`: a schema older than 7.24 only logs a warning instead of stopping |
| `FASTI_DB_SCHEMA`, `FASTI_DB_TABLE_PREFIX` | | `camunda.bpm.database.schema-name` / `table-prefix` |
| `FASTI_TRUSTED_PROXIES` | private addresses | **From 1.3.1.** Regular expression of the IP addresses of the proxies whose `X-Forwarded-*` headers are trusted, e.g. `10\.0\.0\.5`; the proxy must overwrite `X-Forwarded-Host` and `X-Forwarded-Proto` |
| `FASTI_DESERIALIZATION_ALLOWED_PACKAGES` | — | **From 1.3.1.** Packages (comma separated) whose classes Spin JSON/XML and Object variables may be deserialized into, besides the basic Java types; type validation is on. Prefix match: end each package with a dot (`com.example.`) so that `com.examplefoo` is not allowed too. |
| `FASTI_DESERIALIZATION_ALLOWED_CLASSES` | — | **From 1.3.1.** Single classes allowed in the same way |
| `FASTI_REST_MULTIPART_MAX_SIZE` | `100MB` | **From 1.3.1.** Largest multipart request (deployment, file variable, attachment); larger requests get `413`; `-1`: no limit |
| `FASTI_REST_MULTIPART_MAX_PARTS` | `1000` | **From 1.3.1.** Most parts in one multipart request |
| `FASTI_HISTORY_LEVEL` | `auto` | uses the history level stored in the database (new database: `full`) |
| `FASTI_JOB_EXECUTOR_ENABLED` | `true` | `false` turns off the job executor on this node |
| `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | `false` | runs only jobs of deployments registered on this node |
| `FASTI_INSECURE_DEMO` | `false` | `true`: **turns off** REST authentication and authorization, for demos only (see Secure by default) |
| `FASTI_REST_AUTH` | `true` | always on; `false` is refused (use `FASTI_INSECURE_DEMO`) |
| `FASTI_REST_AUTH_CACHE_TTL` | `30s` | **From 1.2.1.** Give a unit (`30s`; a bare number is milliseconds). How long a successful basic authentication of the REST API is remembered, with the user's groups and tenants, so that repeated requests do not read them again (`0` turns it off). A deleted or locked user and changed memberships take effect after this time at the latest; changes made through the REST API of the same node clear it at once. Setting `fasti.rest.auth-cache-ttl` |
| `FASTI_INITIAL_SETUP` | `false` | `true`: enables the page that creates the first administrator when there is none; it asks for a one-time setup token printed to the log |
| `FASTI_SETUP_TOKEN` | — | **From 1.3.1.** Fixed setup token (at least 16 characters) instead of the printed one, for automated installations. With several replicas, set it (the printed token is per node), or start one replica for the setup. |
| `FASTI_CORS_ALLOWED_ORIGINS` | (off) | enables CORS for REST; `*` matches the former Run default |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (+`_FILE`) | | creates the administrator if missing; pass the password in an environment variable, not on the command line |
| `FASTI_WEBAPP_PATH` | `/fasti` | `/camunda` keeps the old path, without redirect |
| `FASTI_LEGACY_PATH_REDIRECT` | `true` | redirects `/camunda/...` to `/fasti/...` |
| `FASTI_CLASSIC_WEBAPPS` | `false` | `true`: serves the classic Cockpit, Tasklist, Admin and Welcome again (deprecated, AngularJS is end of life; logs a warning) |
| `FASTI_MCP_ENABLED` | `false` | **From 1.1.0.** `true` (or the argument `--mcp`): MCP server for AI assistants at `/mcp`, with the logins and permissions of the engine ([docs/mcp.md](../../docs/mcp.md)) |
| `FASTI_MCP_WRITE_TOOLS_ENABLED` | `false` | **From 1.1.0.** `true`: also the MCP tools that start processes, claim and complete tasks and retry jobs |
| `FASTI_CONFIG` | | additional YAML files (comma-separated) |
| `FASTI_EXTRA_LIB` | | directory with additional jars (drivers, plugins) |
| `FASTI_MARIADB_USE_MYSQL_DRIVER` | `true` | uses MySQL Connector/J when the MariaDB driver is missing |
| `FASTI_PORT` | 8080 | HTTP port; the image `HEALTHCHECK` follows it. **From 1.1.0:** a value that is not a number (Kubernetes sets `FASTI_PORT=tcp://...` for a Service named `fasti`) is ignored with a warning; the 1.0.x images fail to start with it |
| `FASTI_LOG_FORMAT` | `text` | **From 1.1.0.** `json`: one JSON object per log line (Elastic Common Schema); also `logstash`, `gelf` |
| `FASTI_METRICS_PROMETHEUS_ENABLED` | `false` | **From 1.1.0.** `true`: Prometheus metrics at `/actuator/prometheus` (JVM, HTTP, pool, engine); needs a token or `FASTI_MANAGEMENT_PORT` |
| `FASTI_METRICS_PROMETHEUS_TOKEN` (+`_FILE`) | | **From 1.1.0.** Scrape token (`Authorization: Bearer <token>`) for the metrics on the application port; a random value of at least 16 characters (the server does not start with a shorter one or with `change-me`) |
| `FASTI_MANAGEMENT_PORT` | | **From 1.1.0.** Serves the actuator (health and metrics) on a port of its own; the `HEALTHCHECK` follows it (a value that is not a number is ignored with a warning; the `HEALTHCHECK` then falls back to `FASTI_PORT`, else 8080) |
| `FASTI_TRACING_ENABLED` | `false` | **From 1.1.0.** `true`: OpenTelemetry spans for requests, engine commands and jobs, sent to `OTEL_EXPORTER_OTLP_ENDPOINT` (OTLP/HTTP) |
| `FASTI_TRACING_ENGINE_ENABLED` | `true` | **From 1.1.0.** `false`: with tracing on, keeps the spans of HTTP requests and leaves out those of engine commands and job executions |
| `FASTI_ABSENCE_ENABLED` | `false` | **From 1.1.0.** `true`: [substitutes during absence](../../docs/absence.md); turn it on for every node |
| `FASTI_FOUR_EYES_ENABLED` | `true` | **From 1.1.0.** [Four-eyes control](../../docs/four-eyes.md) for user tasks whose model declares `fasti:fourEyes`; `false` turns it off |
| `FASTI_AUDIT_ENABLED` | `false` | **From 1.1.0.** `true`: [immutable audit trail](../../docs/audit-trail.md), on every node. Unless `FASTI_DB_SCHEMA_UPDATE=true`, create its tables first with `/fasti/configuration/sql/create/<database>_fasti_audit.sql` |
| `FASTI_AUDIT_HMAC_KEY`, `FASTI_AUDIT_HMAC_KEY_FILE` | | **From 1.1.0.** Key that signs the checkpoints of the audit trail (at least 32 bytes), or the path of a file that holds it (Docker/Kubernetes secret) |
| `FASTI_PERFORMANCE_INDEX_CHECK` | `warn` | **From 1.2.0.** One warning at start listing the [optional performance indexes](../../docs/performance.md) of `/fasti/configuration/sql/optional/<database>_fasti_perf_indexes_create.sql` that the database does not have; `off` silences it |
| `FASTI_PERFORMANCE_CONFIG_CHECK` | `warn` | **From 1.2.0.** Warnings at start for settings that cost performance as the data grows (history without cleanup window, connection pool smaller than the job executor needs, …); `off` silences them |
| `FASTI_APPCDS`, `FASTI_APPCDS_FILE` | `false`, `/fasti/data/fasti-appcds.jsa` | class data sharing for faster startup (see Performance tuning); works from 1.3.2 on |
| `FASTI_BASE64_INTRINSICS` | `false` | `false` adds `-XX:-UseBASE64Intrinsics` to mitigate CVE-2026-114938 ("Java4Shell"); `true` keeps the JVM Base64 intrinsic. See [docs/security/CVE-2026-114938.md](../../docs/security/CVE-2026-114938.md) |

Start arguments of the former image work: `--webapps`, `--rest`, `--example`, `--oauth2`,
`--production` (container production settings: REST authentication, authorizations, `Secure`
cookies, no CORS; TLS at the proxy). A leading `./camunda.sh` is ignored; `--swaggerui` does
not exist in Fasti BPM. Files mounted where the former image read them are used:
`/camunda/configuration/default.yml` (and `production.yml` with `--production`),
`/camunda/configuration/userlib`, `/camunda/configuration/resources`. Fasti BPM's own paths are
below `/fasti/...`.

## JDBC drivers

Bundled (licences in the License Book and NOTICE): PostgreSQL 42.7.14 (BSD-2-Clause), MySQL
Connector/J 9.7.0 (GPLv2 with the Universal FOSS Exception), MariaDB Connector/J 3.5.11
(LGPL-2.1, unless built
with `skipMariadbDriver`; then MariaDB is reached through MySQL Connector/J), SQL Server
13.6.2.jre11 (MIT; encrypts by default: you may need `encrypt=false` or
`trustServerCertificate=true`), H2 2.5.252. Oracle (`ojdbc11.jar`) and DB2 (`jcc.jar`) are
licensed separately and not redistributed: add them in a derived image
(`COPY ojdbc11.jar /fasti/configuration/userlib/`) or mount a directory and set
`FASTI_EXTRA_LIB`.

MySQL and MariaDB keep the times of the engine to the whole second (the Camunda 7.24 schema has
no fractional seconds). **From 1.1.0** the engine binds every date truncated to whole seconds on
these databases, so time filters such as `createdAfter`, `startedAfter` or a task's `dueDate`
find a row stored in the same second with either driver; no URL option is needed. MariaDB
Connector/J has no option to leave out fractional seconds, so the 1.0.x images with that driver
can miss such rows; with 1.0.x, connect through MySQL Connector/J instead
(`DB_DRIVER=com.mysql.cj.jdbc.Driver`, `DB_URL=jdbc:mysql://host:3306/db?sendFractionalSeconds=false`).

## Operations

- Health: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
  (readiness includes the database and the engine); nothing else of the actuator is exposed,
  no login needed. The image's `HEALTHCHECK` polls readiness.
- Metrics, tracing, JSON logs (from 1.1.0): off by default; see
  [docs/install-docker.md](../../docs/install-docker.md#13-observability). The native
  `/actuator/prometheus` endpoint replaces the JMX exporter agent (`JMX_PROMETHEUS`) for most uses.
- Rate limiting: repeated failed logins lock the account for an increasing time, which an
  attacker can abuse to lock out an administrator briefly. Rate-limit `/engine-rest` and
  `/fasti/api/admin/auth` per IP at the proxy.
- SIGTERM shuts down gracefully (30 s for running requests); give the platform more
  (`docker stop -t 60`, `terminationGracePeriodSeconds: 60`).
- Runs as uid/gid 1000 (like the former image), not root; the application files are read-only, only `/fasti/data` is
  writable. Logs go to stdout. Heap is `FASTI_MAX_RAM_PERCENTAGE` % of the container limit and
  the JVM exits on out-of-memory.
- No volumes are declared: mount `/fasti/data` for H2 and `/fasti/configuration/resources` for
  automatic deployments.
- Behind a proxy, `X-Forwarded-*` headers are trusted from private networks; the proxy must
  overwrite them. `--production` sets `Secure` cookies and needs HTTPS at the proxy.
- Several nodes behind a load balancer: use sticky sessions for the web applications (sessions
  are per node, in the cookie `FASTI_SESSION`); Basic-auth REST calls need none. All nodes need the same history level, time
  zone, process classes and plugins, and password settings.

## Performance tuning

Reference numbers (this image, PostgreSQL 16 on the same host, 16 cores, 2 GB container limit,
about 50k process instances, 360k history rows, 10k open tasks):

| Item | Default | Tuned |
|---|---|---|
| Job executor (async jobs/s) | ~400 | ~1,000 (core 8, max 16, queue 16, 16 jobs per acquisition, pool 30) |
| First load of the new web UI (bytes transferred) | 960 KB | 330 KB (gzip, on by default) |
| Startup (`Started … in`) | about 6–11 s depending on host and load | somewhat faster with `FASTI_APPCDS=true` (from the second run) |

- **JVM:** heap is `FASTI_MAX_RAM_PERCENTAGE` % of the memory limit: 75, or 60 when the limit
  is below 1.5 GiB (from 1.3.2; at 75 % such a container left too little for metaspace, code
  cache, threads and GraalJS and could be killed for exceeding its limit). Always set a memory
  limit. Below 2 CPUs or ~1.8 GB the JVM picks Serial GC; give real workloads at least 2 CPUs
  and 2 GB, or set `JAVA_OPTS=-XX:+UseG1GC`. Extra flags go into `JAVA_OPTS`.
- **Faster startup:** `FASTI_APPCDS=true` writes a class data sharing archive to
  `/fasti/data/fasti-appcds.jsa` on a clean shutdown and reuses it on later starts. Up to 1.3.1
  this did not work: the JVM refused to write the archive (a directory came before the jars on
  the class path) and exited with code 1 on every stop. `/fasti/data` must be writable and kept:
  mount a volume, as on Kubernetes an `emptyDir` is new on every pod start and the archive is
  then written again each time (`FASTI_APPCDS_FILE` can point to another volume). An archive
  that does not match the JDK or class path (other image, other start arguments, a jar added to
  `configuration/userlib`) is ignored and written again on the next clean shutdown.
- **Connection pool (HikariCP):** up to 20 connections by default (10 up to 1.1): the ten job
  executor threads plus room for concurrent REST requests. An idle node keeps 10 open and opens
  the others under load. Change them with `FASTI_DB_CONN_MAXACTIVE` (for example 30) and
  `FASTI_DB_CONN_MINIDLE`. The maxima of all nodes together (Camunda 7.24 nodes included), plus
  other clients and the reserve of the database, must stay below its connection limit
  (`max_connections`); count the nodes at their highest (autoscaling maximum plus the extra pods
  of a rolling update). **From 1.3.2** a request waits at most 10 s for a free connection
  (`SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT`, HikariCP's default 30 s), so an exhausted pool
  shows as errors and in the readiness probe instead of tying up request threads.
- **Request threads:** **from 1.3.2** at most 100 (`SERVER_TOMCAT_THREADS_MAX`, Tomcat's
  default 200). With 20 connections and 10 job threads about ten requests use the database at
  once; more threads only queue for a connection while holding memory. Pages, scripts and the
  health endpoints need no connection but share these threads, and so does the liveness probe:
  when every thread waits for the database, they wait too (each waiting request at most the 10 s
  above). For a probe that never queues behind requests, give the actuator
  its own port and thread pool: `FASTI_MANAGEMENT_PORT` (Helm chart: `management.port`).
- **Job executor:** defaults as in Run: 10 threads, a queue of 10 and 10 jobs per acquisition
  (up to 1.1: core 3, max 10, queue 3, 3 jobs per acquisition). For many async/timer jobs raise
  `CAMUNDA_BPM_JOB_EXECUTION_MAX_POOL_SIZE`, `..._QUEUE_CAPACITY` and
  `..._MAX_JOBS_PER_ACQUISITION` together, and grow the connection pool by as many
  ([runtime tuning](../../docs/tuning-runtime.md)). The core threads follow `..._MAX_POOL_SIZE`
  unless `..._CORE_POOL_SIZE` is set; a core larger than the maximum is reduced to it with a
  warning. The exclusive jobs of one process instance acquired together run one after the
  other under one lock of `..._LOCK_TIME_IN_MILLIS` (5 minutes): keep 10 × the slowest such job
  below it, or raise it, or a job may run twice. Switch the job executor off on nodes that only
  serve REST/UI: `FASTI_JOB_EXECUTOR_ENABLED=false`.
- **Several nodes:** every node's job executor acquires from the same tables; more nodes raise
  job throughput and also optimistic locking conflicts (harmless, the job is retried). Keep
  `max-jobs-per-acquisition` moderate (≤ 16) with many nodes. The web UI needs sticky sessions.
- **Compression and caching:** gzip is on for pages, scripts, styles, SVG, JSON and XML above 2 KB
  (`server.compression.enabled=false` if a proxy compresses already). Content-hashed files of
  the new web UI are cached for a year (`immutable`); `index.html` is always revalidated
  (`no-cache`).
- **Database indexes:** the image adds or changes no index; the schema stays identical to 7.24.0.
  **From 1.2.0:** optional indexes for large databases are scripts an administrator runs,
  `/fasti/configuration/sql/optional/<database>_fasti_perf_indexes_create.sql` (and `_drop.sql`),
  safe next to Camunda 7.24 nodes ([performance guide](../../docs/performance.md)).
