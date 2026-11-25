# Fasti BPM Helm chart

Runs the Fasti BPM container image (engine, REST API at `/engine-rest`, web UI at `/fasti/`) on
Kubernetes 1.29 or later against an **external database**. Fasti BPM is built on Camunda 7 CE
7.24.0 and is an independent project, not affiliated with Camunda Services GmbH.

The chart is released with Fasti since 1.1.0. Its version is the version of Fasti, and it runs
the image of that version (the default `appVersion`).

Step-by-step guide (database, secrets, TLS, upgrades, troubleshooting):
[docs/install-kubernetes.md](../../../../docs/install-kubernetes.md). Image reference and every
environment variable: [distro/docker/README.md](../../../docker/README.md).

## Quick start

```bash
kubectl create namespace fasti
kubectl -n fasti create secret generic fasti-db \
  --from-literal=username=fasti --from-literal=password='<database password>'
kubectl -n fasti create secret generic fasti-admin \
  --from-literal=password="Fasti-1-$(openssl rand -hex 12)"

helm install fasti-bpm distro/kubernetes/helm/fasti-bpm -n fasti \
  --set database.url=jdbc:postgresql://postgres.db.svc.cluster.local:5432/fasti \
  --set database.existingSecret=fasti-db \
  --set database.schemaUpdate=true \
  --set admin.user=admin --set admin.existingSecret=fasti-admin

kubectl -n fasti rollout status deployment/fasti-bpm
kubectl -n fasti port-forward svc/fasti-bpm 8080:8080   # http://localhost:8080/fasti/
helm test fasti-bpm -n fasti
```

`database.schemaUpdate` and `admin.*` are for a new, empty database only. For the database of an
existing Camunda 7.24 installation leave them out: tables and accounts already exist.

## What the chart does

- **Production mode on by default** (`production: true` → `--production`): REST authentication
  and authorizations (always on in the image), `Secure`/`SameSite=Strict` session cookies
  (`SameSite=Lax` with `oidc.enabled`, as `Strict` would drop the session on the redirect back
  from the identity provider), password policy, no implicit H2, no remote debugging. Needs HTTPS
  in front (Ingress with TLS).
- **No credentials in values:** database username and password, the administrator password and
  the OIDC client secret come from existing Secrets. Database and admin secrets are mounted as
  files (`DB_PASSWORD_FILE`, `FASTI_ADMIN_PASSWORD_FILE`), mode `0440`, group of the container
  user. The chart refuses to render without `database.url` (or `database.urlKey`) and
  `database.existingSecret`; it never starts the built-in H2 database.
- **Hardened pod:** uid/gid 1000, `runAsNonRoot`, read-only root file system (writable `emptyDir`
  for `/tmp` and `/fasti/data`), all capabilities dropped, `RuntimeDefault` seccomp, no service
  account token, no service links. Passes the `restricted` Pod Security Standard.
- **Probes** on the image's actuator endpoints (no login needed): startup and liveness
  `/actuator/health/liveness`, readiness `/actuator/health/readiness` (database connection and
  process engine included). Nothing else of the actuator is exposed.
- **Graceful shutdown:** `preStop` pause of 10 s, then SIGTERM with 30 s for running requests;
  `terminationGracePeriodSeconds: 60`. Rolling updates with `maxUnavailable: 0`.
- **Spread:** soft pod anti-affinity over nodes by default (`podAntiAffinityPreset`), optional
  `topologySpreadConstraints` (the selector of the release is added when omitted),
  PodDisruptionBudget (`maxUnavailable: 1`).
- Optional: Ingress with TLS, HorizontalPodAutoscaler, NetworkPolicy, ServiceMonitor, OpenID
  Connect single sign-on and JWT bearer tokens, additional Spring Boot configuration.

## Values

| Key | Default | Meaning |
|---|---|---|
| `image.repository` | `fastidev/fasti-bpm` | also published as `ghcr.io/fasti-bpm/fasti-bpm` |
| `image.tag` | `""` (appVersion `1.3.1`) | image tag |
| `image.digest` | `""` | `sha256:…`; pins the image (recommended) |
| `replicaCount` | `1` | pods sharing the database (see Several replicas) |
| `production` | `true` | `--production` of the image |
| `components.webapps`, `.rest`, `.example` | `true`, `true`, `false` | parts of Fasti BPM Run that are started |
| `database.url` | `""` | JDBC URL (required, or `database.urlKey`) |
| `database.driver` | `org.postgresql.Driver` | JDBC driver class; empty derives it from the URL |
| `database.existingSecret` | `""` | Secret with `usernameKey`/`passwordKey` (required) |
| `database.urlKey` | `""` | key of the JDBC URL in `existingSecret`, instead of `url` |
| `database.schemaUpdate` | `false` | `true` creates missing tables in a new database; never upgrades a schema |
| `database.schemaVersionCheck` | `enforce` | `warn` only logs a schema older than 7.24 (unsupported) |
| `database.schema`, `.tablePrefix` | `""` | schema name / table prefix |
| `database.pool.maxActive`, `.minIdle` | `""` | HikariCP pool per pod (default of the image: up to 20, 10 kept when idle; up to 1.1: 10 and 10); see Several replicas for the connection budget |
| `database.waitFor`, `.waitForTimeout` | `""`, `60` | wait for `host:port` before starting the JVM |
| `admin.user`, `admin.existingSecret`, `admin.passwordKey` | `""`, `""`, `password` | administrator created if missing |
| `engine.timezone` | `UTC` | `TZ`; must match every node on the database |
| `engine.historyLevel` | `""` (stored level) | `none`, `activity`, `audit`, `full`, `auto` |
| `engine.jobExecutor.enabled`, `.deploymentAware` | `true`, `false` | job executor of the pods |
| `webapp.path`, `.legacyPathRedirect` | `""` | `/fasti` path, `/camunda` redirect |
| `webapp.classicWebapps` | `false` | deprecated classic web applications (AngularJS, end of life) |
| `webapp.corsAllowedOrigins` | `""` | CORS for REST from other origins |
| `oidc.*` | off | single sign-on, see below |
| `config` | `{}` | additional Spring Boot configuration (YAML), mounted and named in `FASTI_CONFIG` |
| `jvm.maxRamPercentage`, `jvm.javaOpts` | `75`, `""` | heap share of the memory limit, `JAVA_OPTS` |
| `extraArgs`, `extraEnv`, `extraEnvFrom` | `[]` | more arguments and environment variables |
| `extraVolumes`, `extraVolumeMounts`, `initContainers`, `extraContainers` | `[]` | for example a driver directory with `FASTI_EXTRA_LIB` |
| `resources` | requests 1 CPU / 2Gi, limit 2Gi | at least 2 CPUs and 2 GiB for real workloads |
| `management.port` | `""` | port of its own for the actuator (health, metrics), images from 1.1.0; see Metrics |
| `startupProbe`, `livenessProbe`, `readinessProbe` | see `values.yaml` | `enabled: false` removes a probe; port `http` follows `management.port` |
| `podSecurityContext`, `containerSecurityContext` | hardened | see above |
| `podAntiAffinityPreset` | `soft` | `soft`, `hard` or `""`; ignored when `affinity` is set |
| `topologySpreadConstraints` | `[]` | spread over zones |
| `service.type`, `.port`, `.sessionAffinity` | `ClusterIP`, `8080`, `None` | `ClientIP` for sticky sessions without an Ingress |
| `ingress.*` | off | `className`, `annotations`, `hosts`, `tls` |
| `pdb.enabled`, `.maxUnavailable`, `.minAvailable` | `true`, `1`, `""` | PodDisruptionBudget |
| `autoscaling.*` | off | HPA on CPU (`minReplicas` 2, `maxReplicas` 4, 70 %) |
| `networkPolicy.*` | off | `ingressFrom` peers, `egress` rules (DNS added) |
| `metrics.serviceMonitor.*` | off | Prometheus Operator ServiceMonitor (see Metrics) |
| `metrics.extraPort.*` | off | additional container/service port, for example 9404 |

`values.schema.json` rejects unknown keys and malformed values; the templates refuse
incomplete combinations (missing secrets, OIDC without issuer, `FASTI_INSECURE_DEMO` or `DEBUG`
together with production mode, `FASTI_MANAGEMENT_PORT` in `extraEnv` instead of
`management.port`, a ServiceMonitor port the Service does not have).

Not exposed on purpose: `FASTI_INSECURE_DEMO` (open REST API, refused with `--production`) and
`FASTI_INITIAL_SETUP` (first-admin page, protected by a one-time token from the log). Use `admin.*` for an empty database.

From 1.3.1, process applications that store their own classes as JSON or XML variables need
`FASTI_DESERIALIZATION_ALLOWED_PACKAGES` in `extraEnv` (type validation is on), and uploads
over 100 MB need `FASTI_REST_MULTIPART_MAX_SIZE`.

## Several replicas

All pods share the database; the engine is built for that.

- **Job executor:** every pod acquires jobs from the same tables with optimistic locking, so each
  job (async continuation, timer, retry) runs on one pod only; a pod that loses the race leaves
  the job to the other. Keep `max-jobs-per-acquisition` moderate (≤ 16) with many pods and size the
  pool above the job executor threads plus concurrent REST requests (`database.pool.maxActive`).
- **Connections:** each pod opens up to `database.pool.maxActive` connections (image default 20;
  an idle pod keeps `database.pool.minIdle`, image default 10). Count the pods at their highest
  and keep the sum below the connection limit of the database:

  ```
  (autoscaling.maxReplicas, or replicaCount, + updateStrategy.rollingUpdate.maxSurge) × maxActive
    + pools of other nodes on the database (Camunda 7.24 nodes) + other clients + reserve
    < max_connections
  ```

  With the defaults (`maxReplicas` 4, `maxSurge` 1, pool 20) that is 5 × 20 = 100 before any
  other client, exactly the `max_connections` of a default PostgreSQL (100, of which 3 are
  reserved for superusers). Raise the limit of the database, lower `database.pool.maxActive`, or
  lower `autoscaling.maxReplicas` before enabling autoscaling. The [runtime tuning
  guide](../../../../docs/tuning-runtime.md) has the full formula.
- **Deployment-aware:** keep `engine.jobExecutor.deploymentAware: false` when every pod runs the
  same image, plugins and process classes: processes are deployed through the REST API or the
  web UI into the database and are known to every pod. Set it to `true` (on every node) only when
  the database is shared with other nodes, for example Camunda 7 process applications, whose
  Java classes these pods lack; or turn the job executor off with
  `engine.jobExecutor.enabled: false`. See "Side by side with Camunda 7 nodes" in the Docker
  guide.
- **Same settings everywhere:** image, `engine.timezone`, history level, password policy, plugins.
- **Sessions:** the web UI keeps its session per pod. Use cookie-based session affinity at the
  Ingress controller (for ingress-nginx: `nginx.ingress.kubernetes.io/affinity: cookie`), or
  `service.sessionAffinity: ClientIP` without an Ingress. REST clients with Basic credentials or
  bearer tokens need no affinity.
- **Autoscaling:** CPU-based HPA works; more pods also mean more connections and more job
  acquisition contention. The JVM heap does not shrink, so memory-based scaling is not useful.

## Single sign-on (OpenID Connect)

```yaml
oidc:
  enabled: true
  clientName: Keycloak
  issuerUri: https://sso.example.com/realms/fasti
  clientId: fasti
  existingSecret: fasti-oidc          # key client-secret
  groupsClaim: groups
  adminGroups: [fasti-admins]
  ssoLogout:
    enabled: true
  rest:
    bearerEnabled: true
    audiences: [fasti-api]
    userIdClaim: preferred_username
```

The chart adds `--oauth2`, renders the Spring Security client registration and the
`fasti.security.oidc.*` settings into a mounted file and passes the client secret as an
environment variable. The provider must allow the redirect URI
`https://<host>/login/oauth2/code/<registrationId>` (default registration id `oidc`). Settings
the values do not cover go into `config`. Details: the
[single sign-on guide](../../../../docs/single-sign-on.md).

## Metrics

The 1.0.x image exposes no metrics endpoint (only `/actuator/health`), so
`metrics.serviceMonitor.enabled` stays `false`. From 1.1.0, with metrics enabled
(`FASTI_METRICS_PROMETHEUS_ENABLED=true` and a scrape token or a management port; see
"Observability" in the Docker guide), the image serves Prometheus metrics at
`/actuator/prometheus`. The ServiceMonitor sends no scrape token, so use the management port:

```yaml
management:
  port: 9464
extraEnv:
  - name: FASTI_METRICS_PROMETHEUS_ENABLED
    value: "true"
metrics:
  serviceMonitor:
    enabled: true
    port: management
```

`management.port` sets `FASTI_MANAGEMENT_PORT`, which moves the whole actuator (the health
endpoints too) to that port. The chart follows it: the container and the Service get a port
named `management`, probes whose port is `http` use `management`, `helm test` calls it and the
NetworkPolicy admits it (list the namespace of Prometheus in `networkPolicy.ingressFrom`). The
Ingress keeps routing to `http` only, so the actuator is not reachable from outside. Setting
`FASTI_MANAGEMENT_PORT` or `MANAGEMENT_SERVER_PORT` in `extraEnv` is refused, because the probes
would keep calling port 8080 and the pod would never become ready. For the JMX
exporter agent of the image
(`JMX_PROMETHEUS=true`, agent jar added to `/fasti/javaagent`) use `metrics.extraPort` and
`port: metrics`. The ServiceMonitor needs the Prometheus Operator CRDs.

## Upgrades

`helm upgrade` replaces the pods one by one. Within the 1.x line the database schema stays at
7.24, so old and new pods can run side by side during the rollout. Read the CHANGELOG first. For
a release that changes the schema, follow its upgrade notes (usually `updateStrategy.type:
Recreate` and the SQL upgrade scripts before the new version starts).

## Checks

`distro/kubernetes/validate.sh` runs `helm lint --strict`, `helm template` with the value sets
in `ci/`, refusal cases and kubeconform (strict, Kubernetes 1.29–1.33) from pinned container
images; CI runs it on every change below `distro/kubernetes/`.
