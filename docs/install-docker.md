English | [Tiếng Việt](install-docker.vi.md) | [Deutsch](install-docker.de.md) | [Español](install-docker.es.md) | [Français](install-docker.fr.md) | [हिन्दी](install-docker.hi.md) | [Bahasa Indonesia](install-docker.id.md) | [日本語](install-docker.ja.md) | [한국어](install-docker.ko.md) | [Português (Brasil)](install-docker.pt-BR.md) | [Русский](install-docker.ru.md) | [ไทย](install-docker.th.md) | [简体中文](install-docker.zh-CN.md)

# Installing Fasti BPM with Docker

A step-by-step guide: a quick trial with H2, production with PostgreSQL (`docker run` or
`docker compose`), connecting to an existing Camunda 7 database, side-by-side mode, health
checks, upgrade, rollback, troubleshooting and observability (JSON logs, Prometheus metrics,
tracing; since 1.1.0). Full image reference:
[distro/docker/README.md](../distro/docker/README.md). Fasti BPM is built on Camunda 7 CE
7.24.0 and is an independent project, not affiliated with Camunda Services GmbH.

<a id="1-requirements"></a>

## 1. Requirements

- Docker Engine with Docker Compose v2 (`docker compose`).
- For real workloads: at least 2 CPUs and 2 GB of memory for the Fasti container (below that the
  JVM picks the Serial GC).
- Production database: PostgreSQL, MariaDB, MySQL, SQL Server or Oracle (see the
  [tested matrix](migrate-from-camunda-7.md#limits)). DB2 is not verified. H2 is for evaluation only.

<a id="2-pull-the-image"></a>

## 2. Pull the image

<!-- update with the Docker Hub image name at release -->
The image is published for amd64 and arm64 in two places:

```bash
docker pull fastidev/fasti-bpm:1.3.0              # Docker Hub
docker pull ghcr.io/fasti-bpm/fasti-bpm:1.3.0     # GitHub Container Registry
```

Tags: `1.3.0` (exactly that release), `1.3` (latest patch release of 1.3) and `latest` (the most
recently published release). The image is signed with cosign (keyless, GitHub OIDC) and carries
an SBOM and a provenance attestation. From 1.0.1 on the signature is in the Sigstore bundle
format of cosign 3; the 1.0.0 image carries a signature in the older cosign 2 format.

You can also build the image from source (needs JDK 17+ and network access to Maven
Central, nodejs.org and the npm registry) and use the name `fasti-bpm:local` instead of
`fastidev/fasti-bpm:1.3.0` in every command below:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="3-quick-trial-with-h2"></a>

## 3. Quick trial with H2

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  -v fasti-data:/fasti/data \
  fastidev/fasti-bpm:1.3.0
```

- Open <http://localhost:8080/fasti/> and sign in as `admin`.
- Without `DB_URL` the image uses H2 in `/fasti/data`; the `fasti-data` volume keeps the data
  when the container is recreated. For evaluation only, never for real data.
- Append `--webapps --rest --example` to the command to get an example process (invoice) as
  well. `--example` alone leaves the web UI and the REST API off.
- The REST API is at `http://localhost:8080/engine-rest` and requires HTTP Basic credentials; see the
  [REST API guide](api/README.md).

<a id="4-production-with-postgresql-docker-run"></a>

## 4. Production with PostgreSQL: `docker run`

Create two secret files (database password and admin password). With `--production` the engine
enforces its password policy: at least 10 characters with upper- and lowercase letters, a digit
and a special character.

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
  fastidev/fasti-bpm:1.3.0 --production
```

- `FASTI_DB_SCHEMA_UPDATE=true` only creates missing tables in a new database; Fasti never
  upgrades an existing schema. It can stay set on later starts.
- `--production` turns on `Secure` cookies and refuses an implicit H2 database and `DEBUG=true`.
  Terminate TLS at a reverse proxy in front of Fasti; browsers send `Secure` cookies only over HTTPS (or to
  `localhost`).
- `WAIT_FOR` delays startup until the database accepts connections.
- The admin password is only set when the admin does not exist yet; change it later in Admin.

<a id="5-production-with-postgresql-docker-compose"></a>

## 5. Production with PostgreSQL: `docker compose`

Example file: [distro/docker/examples/docker-compose.postgres.yml](../distro/docker/examples/docker-compose.postgres.yml)
(PostgreSQL 16, secrets, health checks, `--production`, 2 GB memory limit).

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

Variables you can set when starting: `FASTI_IMAGE` (default `fastidev/fasti-bpm:1.3.0`, for
example `FASTI_IMAGE=fasti-bpm:local`), `FASTI_HOST_PORT` (default `8080`), `TZ` (default `UTC`).

<a id="6-connecting-to-an-existing-camunda-7-database"></a>

## 6. Connecting to an existing Camunda 7 database

Fasti runs on the 7.24 schema without changing it. Before you start:

1. **Back up** the database.
2. Check the schema version: `select VERSION_ from ACT_GE_SCHEMA_LOG` — the highest value must be
   `7.24.x`. Schemas 7.17–7.23: run the `sql/upgrade` scripts first (shipped in the image at
   `/fasti/configuration/sql/upgrade`), see the
   [migration guide](migrate-from-camunda-7.md#older-versions-717723).
3. Check authorizations, because Fasti enforces them: `select * from ACT_ID_MEMBERSHIP where
   GROUP_ID_='camunda-admin'` and `select count(*) from ACT_RU_AUTHORIZATION`. If the old system
   ran with authorizations off, grant permissions to regular users before moving traffic.
4. Write down `TZ`, the history level, the table prefix/schema and the password settings of the
   old nodes.

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=UTC \
  fastidev/fasti-bpm:1.3.0 --production
```

- The port is bound to this machine only; for remote access go through a reverse proxy with HTTPS.
- Do **not** set `FASTI_DB_SCHEMA_UPDATE` or `FASTI_ADMIN_*`: the tables and accounts already exist. The
  startup log must contain `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
- The history level defaults to `auto`: the level stored in the database is used.
- Every REST client must now send Basic credentials or a bearer token.
- Drivers: PostgreSQL, MySQL, MariaDB, SQL Server and H2 are included. Add the **Oracle** and **DB2** drivers yourself (`ojdbc11.jar`, `jcc.jar`) with `-v /driver/dir:/drivers:ro -e FASTI_EXTRA_LIB=/drivers`.
  SQL Server: driver 13.x encrypts the connection and verifies the certificate by default; the
  server needs a valid certificate matching the host name in the URL. `trustServerCertificate=true`
  (skips certificate checks) or `encrypt=false` (no encryption) are for local trials only. MySQL/MariaDB need
  `transaction-isolation=READ-COMMITTED`.

<a id="7-side-by-side-with-camunda-7-nodes"></a>

## 7. Side by side with Camunda 7 nodes

Fasti and Camunda 7.24 can run at the same time on one database (tested: both see each other's
data). Conditions:

- same history level, same `TZ`, same password settings, same table prefix;
- if the old nodes are process applications with Java classes (`JavaDelegate`, listeners) that Fasti lacks, set `-e FASTI_JOB_EXECUTOR_ENABLED=false` on Fasti or make **every** node
  deployment-aware (`FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE=true` for Fasti). This protects only
  asynchronous jobs: starting instances or completing tasks through Fasti still runs the delegates
  on Fasti, so send those requests to nodes that have the Java classes;
- load balancer: sticky sessions for the web applications; REST with Basic credentials needs none.

Details: [Mixed operation with Camunda 7 nodes](migrate-from-camunda-7.md#mixed-operation-with-camunda-7-nodes).

<a id="8-common-environment-variables"></a>

## 8. Common environment variables

| Variable | Meaning |
|---|---|
| `DB_URL`, `DB_DRIVER`, `DB_USERNAME`, `DB_PASSWORD` (`_FILE`) | database connection (variable names of the former Camunda 7 Run image; aliases: `FASTI_DB_*`) |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | wait for `host:port` before starting |
| `TZ` | JVM time zone; must be the same on every node |
| `FASTI_DB_SCHEMA_UPDATE` | `true`: create missing tables in a new database |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (`_FILE`) | create the admin if missing |
| `FASTI_JOB_EXECUTOR_ENABLED`, `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | job executor of this node |
| `FASTI_EXTRA_LIB` | directory of additional jars (Oracle, DB2 drivers) |
| `FASTI_DB_CONN_MAXACTIVE` | maximum connection pool size (default 20; an idle node keeps 10 open, `FASTI_DB_CONN_MINIDLE`) |
| `JAVA_OPTS`, `FASTI_MAX_RAM_PERCENTAGE` | JVM options, percentage of memory for the heap (75) |

Full table: [distro/docker/README.md](../distro/docker/README.md#environment-variables).

<a id="9-health-checks"></a>

## 9. Health checks

| Endpoint | Use |
|---|---|
| `/actuator/health/liveness` | Kubernetes liveness probe |
| `/actuator/health/readiness` | readiness probe, load balancers; includes the database connection and the engine |
| `/actuator/health` | overall |

No login is needed; only the health endpoints are exposed. The image's `HEALTHCHECK` uses
readiness, so `docker ps` shows `healthy` once Fasti is ready:

```bash
curl -s http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
docker inspect -f '{{.State.Health.Status}}' fasti           # healthy
```

Kubernetes: `livenessProbe` → `/actuator/health/liveness`, `readinessProbe` →
`/actuator/health/readiness`, `terminationGracePeriodSeconds: 60`. Helm chart and manifests:
[install-kubernetes.md](install-kubernetes.md).

<a id="10-upgrade-and-rollback"></a>

## 10. Upgrade and rollback

**Upgrading Fasti** (for example `1.1.0` → `1.3.0`):

```bash
docker pull fastidev/fasti-bpm:1.3.0
docker stop -t 60 fasti && docker rm fasti
docker run -d --name fasti ... fastidev/fasti-bpm:1.3.0 --production   # same options as before
```

With Docker Compose: change `FASTI_IMAGE` (or the `image:` line), then
`docker compose -f docker-compose.postgres.yml up -d`. Read the [CHANGELOG](../CHANGELOG.md)
first, especially the **BREAKING** entries. The `1.x` line keeps the 7.24 schema, so upgrades
within it do not touch the schema. `docker stop -t 60` lets running requests finish before the
container is stopped.

**Rollback:**

- to the previous Fasti release: start again with the old tag;
- to Camunda 7.24: stop Fasti and start the Camunda 7.24 nodes again on the same database. The preferences of the new UI remain (`fasti.prefs.*` in `ACT_ID_INFO`, ignored by Camunda 7). Deployed processes that use Fasti-only features will not run on Camunda 7. See
  [Rollback to Camunda 7.24](migrate-from-camunda-7.md#rollback-to-camunda-724).

<a id="11-troubleshooting"></a>

## 11. Troubleshooting

Read the log with `docker logs fasti` (or `docker compose logs fasti`).

| Symptom | Cause and fix |
|---|---|
| the REST API returns `401` | Fasti requires authentication: send `-u user:password` or a bearer token |
| Empty lists or `403` | the user lacks authorizations: grant them the permissions they need on the resources involved in Admin → Authorizations. Add only real administrators to `camunda-admin` |
| Stops with `ENGINE-03111 The database schema has version …` | schema older than 7.24: back up, then run the `sql/upgrade` scripts (section 6). Do not bypass the check with `FASTI_SCHEMA_VERSION_CHECK=warn`: running on an older schema is not supported and can fail during operation |
| Stops with `Insecure configuration refused` | a setting turns REST authentication or authorizations off; remove it (only `FASTI_INSECURE_DEMO=true` opens the instance, for demos) |
| Stops with `Password does not match policy` | `--production` applies the password policy to `FASTI_ADMIN_PASSWORD`: at least 10 characters with upper- and lowercase letters, a digit and a special character |
| `password authentication failed` | the password in the secret file differs from the database's. Files created on Windows may end in `\r\n`: recreate them with `tr -d '\r\n'`. PostgreSQL reads `POSTGRES_PASSWORD_FILE` only when the volume is initialized for the first time |
| `FASTI_ADMIN_USER needs FASTI_ADMIN_PASSWORD` | set `FASTI_ADMIN_PASSWORD` or `FASTI_ADMIN_PASSWORD_FILE` |
| `--production needs a database` | no database is configured; `--production` does not fall back to the demo H2 database: set `DB_URL` to a production database |
| `… cannot be read` for a `_FILE` variable | the file is not mounted into the container or uid 1000 cannot read it (the file needs mode `0644`; its directory may stay `0700`) |
| Web login over HTTP does not keep the session | the `Secure` cookie (`--production`) is sent only over HTTPS (or to `localhost`): terminate TLS at the proxy |
| Cannot connect to SQL Server (certificate error) | give SQL Server a valid certificate matching the host name in the URL; `trustServerCertificate=true` is for local trials only |
| The container keeps restarting and the log shows `OutOfMemoryError` | the JVM exits when memory runs out: raise the container memory limit (2 GB) |

Still stuck? Open a GitHub issue with the log (passwords removed). Security issues: see
[SECURITY.md](../SECURITY.md#security-policy).

<a id="12-without-docker"></a>

## 12. Without Docker

Ready-made Tomcat 1.3.0 bundle (download page: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.3.0.zip](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.zip.sha256))
- [fasti-bpm-tomcat-1.3.0.tar.gz](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.tar.gz.sha256))

Verify the checksum before extracting, for example `sha256sum -c fasti-bpm-tomcat-1.3.0.zip.sha256`.

<a id="13-observability"></a>

## 13. Observability

> **Since 1.1.0.** The 1.0.x images do not have the settings in this section.

JSON logs, Prometheus metrics and OpenTelemetry tracing are all **off by default**. Each one is
switched on by an environment variable of the image; the same settings work as Spring Boot
properties for Fasti BPM Run outside Docker (`configuration/default.yml` or
`production.yml`, see the commented examples there).

| Variable | Property | Effect |
|---|---|---|
| `FASTI_LOG_FORMAT=json` | `fasti.logging.format` | one JSON object per log line ([Elastic Common Schema](https://www.elastic.co/guide/en/ecs/current/)); also `logstash`, `gelf`; `text` is the default |
| `FASTI_METRICS_PROMETHEUS_ENABLED=true` | `fasti.metrics.prometheus.enabled` | Prometheus metrics at `/actuator/prometheus` |
| `FASTI_METRICS_PROMETHEUS_TOKEN` (+`_FILE`) | `fasti.metrics.prometheus.token` | scrape token, sent as `Authorization: Bearer <token>`; a random value of at least 16 characters |
| `FASTI_MANAGEMENT_PORT` | `management.server.port` | the actuator (health **and** metrics) on a port of its own |
| `FASTI_TRACING_ENABLED=true` | `fasti.tracing.enabled` | OpenTelemetry spans for HTTP requests, engine commands and job executions |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `management.opentelemetry.tracing.export.otlp.endpoint` | where spans are sent (OTLP over HTTP, port 4318) |

### JSON logs

```bash
docker run -d --name fasti -p 8080:8080 -e FASTI_LOG_FORMAT=json ... fastidev/fasti-bpm:1.3.0
docker logs fasti | head -1
# {"@timestamp":"2026-11-21T09:12:03.120Z","log":{"level":"INFO","logger":"org.camunda.bpm.run.CamundaBpmRun"},
#  "process":{"pid":1,"thread":{"name":"main"}},"service":{"name":"fasti-bpm","version":"1.3.0","node":{}},
#  "message":"Starting CamundaBpmRun v1.3.0 using Java 21 ...","ecs":{"version":"8.11"}}
```

The service name is `fasti-bpm` unless `SPRING_APPLICATION_NAME` is set. Stack traces are part
of the JSON object, so a log collector (Fluent Bit, Vector, Filebeat, Loki/Promtail) can parse
each line without multi-line rules. The few lines the entrypoint prints before the JVM starts
(`fasti-entrypoint: ...`) stay plain text.

### Prometheus metrics

The metrics include the JVM, HTTP requests (`http_server_requests_seconds`), the connection
pool (`hikaricp_*`), the thread pool of the job executor (`executor_*`) and the process engine:

| Metric | Type | Meaning |
|---|---|---|
| `fasti_engine_process_instances_started_total` | counter | started root process instances |
| `fasti_engine_process_instances_ended_total{state="completed"\|"canceled"}` | counter | ended root process instances |
| `fasti_engine_activity_instances_started_total`, `..._ended_total` | counter | activity instances |
| `fasti_engine_jobs_executed_total{result="successful"\|"failed"}` | counter | job executions |
| `fasti_engine_jobs_acquired_total{result="success"\|"failure"}`, `fasti_engine_jobs_acquisition_attempts_total`, `fasti_engine_jobs_rejected_total`, `fasti_engine_jobs_locked_exclusive_total` | counter | job acquisition |
| `fasti_engine_decision_instances_evaluated_total`, `fasti_engine_decision_elements_evaluated_total` | counter | DMN evaluations |
| `fasti_engine_process_instances_active`, `fasti_engine_tasks_open`, `fasti_engine_incidents_open`, `fasti_engine_jobs_executable`, `fasti_engine_jobs_failed`, `fasti_engine_external_tasks_open` | gauge | current state, read from the database at most every 30 s (`FASTI_METRICS_ENGINE_GAUGES_REFRESH_INTERVAL`) |

Every engine metric has the tag `engine` (the engine name); process definition keys or activity
ids are never tags, so the number of series stays small. The counters start at zero when a node
starts and count what that node executed; the gauges show the whole database (sum the counters,
not the gauges, across nodes). The counters move only while the engine metrics are on
(`camunda.bpm.metrics.enabled`, the default).

The metrics are **not public**. Pick one of two ways:

1. **Scrape token** on the application port. Every request needs
   `Authorization: Bearer <token>`, otherwise the answer is `401`. The token must be a random
   value of at least 16 characters; a node with a shorter one, or with the placeholder
   `change-me` of the example configuration, refuses to start:

   ```bash
   openssl rand -hex 32 > metrics-token && chmod 0644 metrics-token
   docker run -d --name fasti -p 8080:8080 \
     -e FASTI_METRICS_PROMETHEUS_ENABLED=true \
     -e FASTI_METRICS_PROMETHEUS_TOKEN_FILE=/run/secrets/metrics-token \
     -v "$PWD/metrics-token:/run/secrets/metrics-token:ro" ... fastidev/fasti-bpm:1.3.0
   curl -s -H "Authorization: Bearer $(cat metrics-token)" http://localhost:8080/actuator/prometheus | head
   ```

   Prometheus:

   ```yaml
   scrape_configs:
     - job_name: fasti
       metrics_path: /actuator/prometheus
       authorization:
         credentials_file: /etc/prometheus/fasti-metrics-token
       static_configs:
         - targets: ["fasti:8080"]
   ```

2. **Management port** that only the monitoring network reaches, for example
   `-e FASTI_MANAGEMENT_PORT=9464` without publishing port 9464 to the outside. No token is
   needed there (a configured token is still checked). The health endpoints move to that port
   as well: the image's `HEALTHCHECK` follows `FASTI_MANAGEMENT_PORT`, but Kubernetes probes and
   load balancers must use the management port too.

Without either, a node with `FASTI_METRICS_PROMETHEUS_ENABLED=true` refuses to start. While the
switch is off the endpoint does not exist at all, even if a configuration file exposes every
actuator endpoint (`management.endpoints.web.exposure.include: "*"`). The same rule holds when
the endpoint is switched on with the Spring Boot settings behind the switch instead
(`management.endpoint.prometheus.access`, `management.prometheus.metrics.export.enabled` and
the exposure list): the node refuses to start without a token or a management port of its own,
and a configured token is required on the endpoint.

### Tracing (OpenTelemetry)

```bash
docker run -d --name fasti -p 8080:8080 \
  -e FASTI_TRACING_ENABLED=true \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318 \
  ... fastidev/fasti-bpm:1.3.0
```

Each HTTP request becomes a trace; the engine commands it runs (for example a process
definition query or `StartProcessInstanceCmd`) are child spans, with the command name in the
attribute `fasti.engine.command`. Each job execution starts a trace of its own (`job
execution`). Incoming W3C `traceparent` headers are continued. The service name is `fasti-bpm`
(`OTEL_SERVICE_NAME` changes it).

- Spans are only sent once an endpoint is set (`OTEL_EXPORTER_OTLP_ENDPOINT`, which gets
  `/v1/traces` appended, or the full URL in `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`); without one
  no network connection is made. OTLP is sent over HTTP (`http/protobuf`, port 4318), not gRPC.
- With tracing on, every request is sampled. For high request rates lower it, for example
  `-e MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0.1` (or `OTEL_TRACES_SAMPLER_ARG`).
- `FASTI_TRACING_ENGINE_ENABLED=false` keeps the HTTP spans and leaves out the engine commands.
- With tracing on, text log lines carry the trace and span ids; JSON log lines have them as
  `traceId` and `spanId`.
- Requests to the actuator (health checks, metric scrapes) are not traced; with tracing on they
  are not counted in `http_server_requests_seconds` either.
- With tracing on, the Prometheus endpoint also has the timers `fasti_engine_command_seconds` and
  `fasti_engine_job_seconds` (tags `engine`, `error`).
- Only spans are exported over OTLP. Metrics stay on the Prometheus endpoint; logs on stdout.
