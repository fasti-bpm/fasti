English | [Tiếng Việt](install-kubernetes.vi.md)

# Installing Fasti BPM on Kubernetes

Fasti BPM runs on Kubernetes 1.29 or later with the official container image and an external
database, either with the **Helm chart** (`distro/kubernetes/helm/fasti-bpm`) or with the
**plain manifests** (`distro/kubernetes/manifests`, Kustomize). Fasti BPM is built on Camunda 7
CE 7.24.0 and is an independent project, not affiliated with Camunda Services GmbH.

> **Since 1.1.0.** The Helm chart and the manifests are released with Fasti from 1.1.0 on and
> run the image of the same version by default.

References: [chart README](../distro/kubernetes/helm/fasti-bpm/README.md) (all values),
[image reference](../distro/docker/README.md) (all environment variables),
[Docker guide](install-docker.md), [migration from Camunda 7](migrate-from-camunda-7.md).

<a id="1-requirements"></a>

## 1. Requirements

- Kubernetes 1.29 or later, `kubectl`, and Helm 3 or 4 for the chart (checked with 3.22 and 4.3).
- A database reachable from the cluster: PostgreSQL, MariaDB, MySQL, SQL Server or Oracle (see
  the [tested matrix](migrate-from-camunda-7.md#limits)). The chart never starts the built-in H2
  database; run the database as a managed service or with an operator of your choice.
- Per pod: at least 2 CPUs and 2 GiB of memory for real workloads (the chart requests 1 CPU and
  2 GiB by default).
- HTTPS in front of Fasti (Ingress controller with TLS, or a load balancer that terminates TLS):
  production mode sets `Secure` session cookies, which browsers send only over HTTPS.

<a id="2-database-and-secrets"></a>

## 2. Database and secrets

Create a database and a user for Fasti, for example on PostgreSQL:

```sql
CREATE USER fasti WITH PASSWORD '<database password>';
CREATE DATABASE fasti OWNER fasti;
```

Store the credentials in Secrets; neither the chart nor the manifests contain passwords:

```bash
kubectl create namespace fasti
kubectl -n fasti create secret generic fasti-db \
  --from-literal=username=fasti --from-literal=password='<database password>'
# administrator for a new, empty database (password policy in production mode: at least 10
# characters with upper- and lowercase letters, a digit and a special character)
kubectl -n fasti create secret generic fasti-admin \
  --from-literal=password="Fasti-1-$(openssl rand -hex 12)"
```

Secret managers (External Secrets Operator, Sealed Secrets, Vault) work the same way, as long as
the Secret has the keys `username` and `password` (configurable in the chart).

<a id="3-install-with-helm"></a>

## 3. Install with Helm

Write the settings into a values file, for example `fasti-values.yaml`:

```yaml
image:
  tag: "1.3.0"
  # digest: sha256:...            # recommended: pin the image
database:
  url: jdbc:postgresql://postgres.db.svc.cluster.local:5432/fasti
  existingSecret: fasti-db
  schemaUpdate: true               # new database only: create the tables
  waitFor: postgres.db.svc.cluster.local:5432
admin:                             # new database only
  user: admin
  existingSecret: fasti-admin
engine:
  timezone: UTC                    # same on every node of the database
ingress:
  enabled: true
  className: nginx
  hosts:
    - host: fasti.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: fasti-tls        # for example issued by cert-manager
      hosts: [fasti.example.com]
```

```bash
helm install fasti-bpm distro/kubernetes/helm/fasti-bpm -n fasti -f fasti-values.yaml
kubectl -n fasti rollout status deployment/fasti-bpm
helm test fasti-bpm -n fasti      # readiness through the Service
```

Open `https://fasti.example.com/fasti/` and sign in as `admin`. The REST API is at
`https://fasti.example.com/engine-rest` and requires Basic credentials or a bearer token
([REST API guide](api/README.md)). Without an Ingress:
`kubectl -n fasti port-forward svc/fasti-bpm 8080:8080`, then `http://localhost:8080/fasti/`.

The chart runs with production mode on, a read-only root file system, uid 1000, no capabilities
and passes the `restricted` Pod Security Standard, so the namespace can enforce it:

```bash
kubectl label namespace fasti pod-security.kubernetes.io/enforce=restricted
```

<a id="4-install-with-plain-manifests"></a>

## 4. Install with plain manifests

`distro/kubernetes/manifests` contains a Namespace (with the `restricted` Pod Security label),
ServiceAccount, Deployment, Service and PodDisruptionBudget, plus optional `ingress.yaml` and
`networkpolicy.yaml` and a `secret-example.yaml` that is not applied. Adapt `DB_URL`, the image
tag, `TZ` and the resources in `deployment.yaml` (or in a Kustomize overlay), create the Secrets
of section 2, then:

```bash
kubectl apply -k distro/kubernetes/manifests
kubectl -n fasti rollout status deployment/fasti
```

An overlay keeps your changes apart from the files of the project:

```yaml
# my-fasti/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../fasti/distro/kubernetes/manifests
images:
  - name: fastidev/fasti-bpm
    newTag: "1.3.0"
patches:
  - target: {kind: Deployment, name: fasti}
    patch: |
      - op: replace
        path: /spec/template/spec/containers/0/env/1/value
        value: jdbc:postgresql://db.example.com:5432/fasti
```

<a id="5-existing-camunda-7-database"></a>

## 5. Connecting to an existing Camunda 7 database

Follow the checks of the [Docker guide, section 6](install-docker.md#6-connecting-to-an-existing-camunda-7-database)
first: back up, schema version `7.24.x`, authorizations, `TZ`, history level, table prefix. Then
leave out `database.schemaUpdate` and `admin.*` (manifests: remove `FASTI_DB_SCHEMA_UPDATE`,
`FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD_FILE` and the `fasti-admin` volume source). The log
must contain `ENGINE-03115 Database schema version 7.24.0 matches this engine.` If Camunda 7
nodes keep running on the same database, read
[side by side with Camunda 7 nodes](install-docker.md#7-side-by-side-with-camunda-7-nodes).

<a id="6-several-replicas"></a>

## 6. Several replicas and high availability

```yaml
replicaCount: 2
podAntiAffinityPreset: soft        # hard: never two pods on one node
topologySpreadConstraints:
  - maxSkew: 1
    topologyKey: topology.kubernetes.io/zone
    whenUnsatisfiable: ScheduleAnyway
ingress:
  annotations:
    nginx.ingress.kubernetes.io/affinity: cookie
database:
  pool:
    maxActive: 30
```

- The job executors of all pods share the job tables; optimistic locking makes sure each job
  runs on one pod. In the test of this guide (k3s 1.33, PostgreSQL 16, two pods) 240
  asynchronous jobs ran exactly once each, spread over both pods.
- `engine.jobExecutor.deploymentAware` stays `false` while every pod runs the same image and
  plugins. Set it to `true` on every node only when the database is shared with nodes whose
  process applications contain Java classes these pods lack.
- The web UI keeps sessions per pod: configure cookie affinity at the Ingress controller (the
  annotation above is for ingress-nginx), or `service.sessionAffinity: ClientIP`. REST clients
  with Basic credentials or bearer tokens need none.
- The PodDisruptionBudget (`maxUnavailable: 1`) keeps all but one pod running during node
  drains; rolling updates start the new pod before stopping an old one (`maxSurge: 1`,
  `maxUnavailable: 0`).
- The connection pools of all pods together must stay below the database's `max_connections`.
- Optional autoscaling on CPU: `autoscaling.enabled: true` (needs the metrics server).

<a id="7-single-sign-on"></a>

## 7. Single sign-on and bearer tokens

```bash
kubectl -n fasti create secret generic fasti-oidc --from-literal=client-secret='<client secret>'
```

```yaml
oidc:
  enabled: true
  clientName: Keycloak
  issuerUri: https://sso.example.com/realms/fasti
  clientId: fasti
  existingSecret: fasti-oidc
  groupsClaim: groups
  adminGroups: [fasti-admins]
  rest:
    bearerEnabled: true
    audiences: [fasti-api]
```

The provider must allow the redirect URI `https://fasti.example.com/login/oauth2/code/oidc`.
The pods must reach the issuer over HTTPS (add it to `networkPolicy.egress` if you restrict
egress). More options: [chart README](../distro/kubernetes/helm/fasti-bpm/README.md#single-sign-on-openid-connect)
and the [single sign-on guide](single-sign-on.md).

<a id="8-network-policy"></a>

## 8. Network policy

```yaml
networkPolicy:
  enabled: true
  ingressFrom:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: ingress-nginx
  egress:
    - to:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: db
      ports:
        - port: 5432
          protocol: TCP
```

With `egress` set, the pods may only reach DNS and the listed peers: add the OIDC provider, mail
servers and every service your processes call. Needs a CNI that enforces NetworkPolicy.

<a id="9-health-and-metrics"></a>

## 9. Health checks and metrics

| Endpoint | Probe |
|---|---|
| `/actuator/health/liveness` | startup probe (up to 5 minutes for the first start) and liveness probe |
| `/actuator/health/readiness` | readiness probe; includes the database connection and the engine |

No login is needed and nothing else of the actuator is exposed. The 1.0.x image has no metrics
endpoint; `metrics.serviceMonitor` is therefore off by default. From 1.1.0, with metrics enabled
(`FASTI_METRICS_PROMETHEUS_ENABLED=true` and a scrape token or a management port, see the
[Docker guide](install-docker.md#13-observability)), the image serves Prometheus metrics at
`/actuator/prometheus`. Set `management.port` (for example `9464`) to give the actuator a port
of its own: probes, `helm test`, the Service and the NetworkPolicy follow it, and
`metrics.serviceMonitor.port: management` scrapes it. The chart does not send a scrape token
(see the chart README).

<a id="10-upgrade-and-rollback"></a>

## 10. Upgrade and rollback

```bash
helm upgrade fasti-bpm distro/kubernetes/helm/fasti-bpm -n fasti -f fasti-values.yaml \
  --set image.tag=<new version>
kubectl -n fasti rollout status deployment/fasti-bpm
helm rollback fasti-bpm -n fasti          # back to the previous revision
```

Pods are replaced one at a time and the Service keeps answering (tested: no failed request
during a rolling upgrade of two pods). Within 1.x the schema stays at 7.24, so old and new pods
can run side by side. Read the [CHANGELOG](../CHANGELOG.md) first, especially **BREAKING**
entries. Changes of `config` or `oidc` roll the pods automatically (checksum annotation); a
changed Secret needs `kubectl -n fasti rollout restart deployment/fasti-bpm`.

<a id="11-troubleshooting"></a>

## 11. Troubleshooting

`kubectl -n fasti logs deployment/fasti-bpm` and `kubectl -n fasti describe pod -l app.kubernetes.io/name=fasti-bpm`.

| Symptom | Cause and fix |
|---|---|
| `helm install` fails with `database.url … is required` | set `database.url` (or `urlKey`) and `database.existingSecret` |
| Pod stays `Pending` | not enough CPU or memory for the requests, or `podAntiAffinityPreset: hard` with fewer nodes than replicas |
| Crash with `Invalid value 'tcp://…' for configuration property 'server.port'` | a Service named `fasti` in the namespace and service links on: Kubernetes sets `FASTI_PORT=tcp://…`, which the 1.0.x image reads as its port (from 1.1.0 the image ignores such a value with a warning). Keep `enableServiceLinks: false` (the default of the chart and the manifests) |
| `… cannot be read` for a `_FILE` variable | the Secret or key is missing; check `database.existingSecret` and the key names |
| `password authentication failed` | the Secret does not match the database password |
| Readiness stays down, log shows `ENGINE-03111` | the schema is older than 7.24: run the upgrade scripts (see the Docker guide) |
| Startup probe fails on a slow database | raise `startupProbe.failureThreshold` |
| Web login does not keep the session | no HTTPS in front (Secure cookie), or several pods without session affinity |
| `OOMKilled` or `OutOfMemoryError` | raise `resources.limits.memory`; the heap is 75 % of the limit |
| `Insecure configuration refused` | a setting in `config` or `extraEnv` turns REST authentication or authorizations off |

More causes: [Docker guide, troubleshooting](install-docker.md#11-troubleshooting).
