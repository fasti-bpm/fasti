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
docker build -t fasti-bpm:1.0.1 \
  --build-arg VERSION=1.0.1 --build-arg REVISION=$(git rev-parse --short HEAD) \
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
  is left unchanged (no password reset). `FASTI_INITIAL_SETUP=true` brings the setup page back,
  with a warning.
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
  -e TZ=Asia/Ho_Chi_Minh \
  fasti-bpm:1.0.1
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
`CAMUNDA_BPM_RUN_AUTH_ENABLED=true`).

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
| `FASTI_MAX_RAM_PERCENTAGE` | 75 | percentage of container memory used for the heap |
| `FASTI_DB_SCHEMA_UPDATE` | `false` | `true` creates missing tables (new database); never upgrades a schema |
| `FASTI_SCHEMA_VERSION_CHECK` | `enforce` | `warn`: a schema older than 7.24 only logs a warning instead of stopping |
| `FASTI_DB_SCHEMA`, `FASTI_DB_TABLE_PREFIX` | | `camunda.bpm.database.schema-name` / `table-prefix` |
| `FASTI_HISTORY_LEVEL` | `auto` | uses the history level stored in the database (new database: `full`) |
| `FASTI_JOB_EXECUTOR_ENABLED` | `true` | turns off the job executor on this node |
| `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | `false` | runs only jobs of deployments registered on this node |
| `FASTI_INSECURE_DEMO` | `false` | `true`: **turns off** REST authentication and authorization, for demos only (see Secure by default) |
| `FASTI_REST_AUTH` | `true` | always on; `false` is refused (use `FASTI_INSECURE_DEMO`) |
| `FASTI_INITIAL_SETUP` | `false` | `true`: enables the page that creates the first administrator when there is none |
| `FASTI_CORS_ALLOWED_ORIGINS` | (off) | enables CORS for REST; `*` matches the former Run default |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (+`_FILE`) | | creates the administrator if missing; pass the password in an environment variable, not on the command line |
| `FASTI_WEBAPP_PATH` | `/fasti` | `/camunda` keeps the old path, without redirect |
| `FASTI_LEGACY_PATH_REDIRECT` | `true` | redirects `/camunda/...` to `/fasti/...` |
| `FASTI_CLASSIC_WEBAPPS` | `false` | `true`: serves the classic Cockpit, Tasklist, Admin and Welcome again (deprecated, AngularJS is end of life; logs a warning) |
| `FASTI_CONFIG` | | additional YAML files (comma-separated) |
| `FASTI_EXTRA_LIB` | | directory with additional jars (drivers, plugins) |
| `FASTI_MARIADB_USE_MYSQL_DRIVER` | `true` | uses MySQL Connector/J when the MariaDB driver is missing |
| `FASTI_PORT` | 8080 | HTTP port; the image `HEALTHCHECK` follows it |
| `FASTI_APPCDS`, `FASTI_APPCDS_FILE` | `false`, `/fasti/data/fasti-appcds.jsa` | class data sharing for faster startup (see Performance tuning) |
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
13.4.0.jre11 (MIT; encrypts by default: you may need `encrypt=false` or
`trustServerCertificate=true`), H2 2.5.252. Oracle (`ojdbc11.jar`) and DB2 (`jcc.jar`) are
licensed separately and not redistributed: add them in a derived image
(`COPY ojdbc11.jar /fasti/configuration/userlib/`) or mount a directory and set
`FASTI_EXTRA_LIB`.

## Operations

- Health: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
  (readiness includes the database and the engine); nothing else of the actuator is exposed,
  no login needed. The image's `HEALTHCHECK` polls readiness.
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
  are per node); Basic-auth REST calls need none. All nodes need the same history level, time
  zone, process classes and plugins, and password settings.

## Performance tuning

Reference numbers (this image, PostgreSQL 16 on the same host, 16 cores, 2 GB container limit,
about 50k process instances, 360k history rows, 10k open tasks):

| Item | Default | Tuned |
|---|---|---|
| Job executor (async jobs/s) | ~400 | ~1,000 (core 8, max 16, queue 16, 16 jobs per acquisition, pool 30) |
| First load of the new web UI (bytes transferred) | 960 KB | 330 KB (gzip, on by default) |
| Startup (`Started … in`) | about 6–11 s depending on host and load | somewhat faster with `FASTI_APPCDS=true` (from the second run) |

- **JVM:** heap is `FASTI_MAX_RAM_PERCENTAGE` (75) % of the memory limit; always set a memory
  limit. Below 2 CPUs or ~1.8 GB the JVM picks Serial GC; give real workloads at least 2 CPUs
  and 2 GB, or set `JAVA_OPTS=-XX:+UseG1GC`. Extra flags go into `JAVA_OPTS`.
- **Faster startup:** `FASTI_APPCDS=true` writes a class data sharing archive to
  `/fasti/data/fasti-appcds.jsa` on a clean shutdown and reuses it on later starts. `/fasti/data`
  must be writable (mount a volume to keep it across re-created containers). An archive that
  does not match the JDK or class path is ignored; delete the file when upgrading the image so
  that it is created again.
- **Connection pool (HikariCP):** 10 by default. Size it above the job executor threads plus the
  expected concurrent REST requests with `FASTI_DB_CONN_MAXACTIVE` (for example 30). The pools of
  all nodes together must stay below the database's `max_connections`.
- **Job executor:** defaults as in Run (core 3, max 10, queue 3). For many async/timer jobs set
  `CAMUNDA_BPM_JOB_EXECUTION_CORE_POOL_SIZE`, `..._MAX_POOL_SIZE`, `..._QUEUE_CAPACITY`,
  `..._MAX_JOBS_PER_ACQUISITION`, and grow the connection pool with them. Switch it off on
  nodes that only serve REST/UI: `FASTI_JOB_EXECUTOR_ENABLED=false`.
- **Several nodes:** every node's job executor acquires from the same tables; more nodes raise
  job throughput and also optimistic locking conflicts (harmless, the job is retried). Keep
  `max-jobs-per-acquisition` moderate (≤ 16) with many nodes. The web UI needs sticky sessions.
- **Compression and caching:** gzip is on for pages, scripts, styles and JSON above 2 KB
  (`server.compression.enabled=false` if a proxy compresses already). Content-hashed files of
  the new web UI are cached for a year (`immutable`); `index.html` is always revalidated
  (`no-cache`).
- **Database indexes:** the image adds or changes no index; the schema stays identical to 7.24.0.
