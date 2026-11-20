English | [Tiếng Việt](README.vi.md)

# Fasti BPM Platform

**Make Camunda 7 Great Again.** Camunda 7 Community Edition ended with 7.24.0, its final
release. Fasti BPM carries it on from there: the same engine on the same database, with
continued fixes and library upgrades, and a new web UI.

> **Not affiliated with Camunda Services GmbH; Camunda is a trademark of Camunda Services GmbH.**
> Fasti BPM is an independent open-source project (Apache-2.0). It is not part of, sponsored by
> or endorsed by Camunda Services GmbH. The name "Camunda" is used only to describe where the
> source code comes from and what Fasti is compatible with.

## Quick start

The image is available on Docker Hub (`fastidev/fasti-bpm`) and on GitHub
Container Registry (`ghcr.io/fasti-bpm/fasti-bpm`), for amd64 and arm64:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.0.0
```

Open <http://localhost:8080/fasti/> and sign in. Without `DB_URL` the image uses an H2 database
inside the container, for evaluation only. The REST API needs Basic credentials:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

**Build the image yourself** from source. This needs Docker, JDK 17+ and network
access to Maven Central, nodejs.org and the npm registry (the build downloads Node/npm for the
web UI); the first build takes about 10–20 minutes:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
# then run the docker run command above with fasti-bpm:local instead of fastidev/fasti-bpm:1.0.0
```

The image **requires REST authentication and enforces authorizations by default** (unlike Camunda
Run); only `FASTI_INSECURE_DEMO=true` turns them off, for demos.

## Highlights

- **New web UI** at `/fasti/` (React):
  - search process instances by several variables at once, combined with AND/OR;
  - case timeline within the task (comments and actions at every step);
  - a "Get next work" button;
  - due dates and urgency (SLA), overdue work;
  - team workload per user and group;
  - runbooks attached to incidents;
  - a system health page (incidents, failed jobs, late jobs, external tasks, batches);
  - mobile-friendly layout;
  - 13 languages including Vietnamese; the BPMN modeler speaks Vietnamese too.
- **Single sign-on (OIDC)**: Keycloak, Microsoft Entra ID and other OpenID Connect providers;
  claims mapped to groups and tenants; JWT bearer tokens for the REST API. Available for Run,
  Spring Boot, Tomcat and WildFly.
- **Security hardening**: allow-list for expressions in queries and filters, attachment and
  comment checks inside the engine, initial setup, Cockpit permissions, open redirect, web
  application headers, cookies and CSP. Details in the [CHANGELOG](CHANGELOG.md).
- **Authentication on by default**: the image requires credentials for REST and enforces
  authorizations in every mode.
- **Schema version guard**: the engine refuses to start on a schema older than 7.24 and never
  upgrades a schema by itself.
- **Java4Shell mitigation** ([CVE-2026-114938](docs/security/CVE-2026-114938.md)): the amd64
  image ships a fixed Temurin JRE and turns off the Base64 intrinsic; the engine warns when it
  runs on an affected JVM.
- **Performance**: the first load of the new web UI drops from 960 KB to 330 KB (gzip on by
  default); job executor throughput rises about 2.5x with tuning (numbers and test setup in
  [distro/docker/README.md](distro/docker/README.md#performance-tuning)).
- **Current platform**: Java 17 or later, Spring Boot 4.1, GraalJS 25, Jackson 2.21.

## Installation

### One command, on your existing Camunda 7.24 database

The image accepts the `DB_*` variables of the former Camunda 7 Run image. Back up the database,
then:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e TZ=Europe/Berlin \
  fastidev/fasti-bpm:1.0.0
```

Users, passwords, groups, tenants, authorizations, filters, deployments, running instances,
history, jobs and incidents stay as they are. The port is bound to this machine only; for remote
access go through a reverse proxy with HTTPS. Sign in at `/fasti/` with an existing account.
Because authentication is on, every REST client (external task workers, integrations) must send
Basic credentials or a bearer token.

- **Side by side** with Camunda 7.24 CE nodes on the same database: same history level, same
  `TZ`, same password settings. If the old nodes are process applications with Java classes that
  Fasti lacks, set `FASTI_JOB_EXECUTOR_ENABLED=false` on Fasti (or make every node
  deployment-aware), and do not start those processes or complete their tasks through Fasti: a
  synchronous call runs the delegates on the node that receives it.
- **Rollback**: Fasti does not change the schema, so stop Fasti and start the Camunda 7.24 nodes
  again on the same database. Deployed processes that use Fasti-only features will not run on
  Camunda 7; see [Rollback to Camunda 7.24](docs/migrate-from-camunda-7.md#rollback-to-camunda-724).
- **Older schemas (7.17–7.23)**: run the `sql/upgrade` scripts shipped in the image
  (`/fasti/configuration/sql/upgrade`) up to 7.24 first; for example 7.23 → 7.24:
  `postgres_engine_7.23_to_7.24.sql`.

Tested with the image (taking over a database created by Camunda Run 7.24.0, rolling
back, running side by side): **PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022, Oracle
Database 23** (the Oracle driver is not bundled; add `ojdbc11` yourself; rollback was not tested
on MySQL). **DB2 is not
verified.** H2 is for evaluation only. Results per test and limits:
[migration guide](docs/migrate-from-camunda-7.md#limits).

Detailed guides:

- [Installing with Docker](docs/install-docker.md): H2, PostgreSQL with `docker run` and
  `docker compose`, existing databases, side-by-side mode, health checks, upgrade, rollback,
  troubleshooting.
- [Moving from Camunda 7](docs/migrate-from-camunda-7.md): steps, what is identical and what
  differs, rollback, limits.
- [Environment variable reference for the image](distro/docker/README.md#environment-variables).

### Without Docker: Tomcat bundle (download from fasti.dev)

Ready-made Tomcat 1.0.0 bundle (download page: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256))
- [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256))

Verify the checksum before extracting, for example `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`.

The Tomcat distribution can also be built from source (`distro/tomcat`). Read `README.txt` in
the bundle before starting it: the REST API (`/engine-rest`) requires HTTP Basic authentication
by default, no user is created (create the first administrator on the setup page of `/fasti/`),
and when taking over a Camunda Run database, set `jobExecutorDeploymentAware=false` in
`conf/bpm-platform.xml`, otherwise, timers and async jobs of existing deployments will not run.

## A UI built from real user complaints

The features of the new web UI were chosen from what Camunda 7 users have written publicly on the
Camunda forum and on GitHub, for example:

| What users asked for | Source | In Fasti |
|---|---|---|
| Search instances by several variables, with OR | forum [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Multi-variable condition builder, AND/OR |
| See the comments at every step of a case | forum [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Case timeline in the task; comments on the process instance itself |
| Sort tasks by variables, oldest first | forum [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Sorting by variables, due dates and urgency |
| Delegation, substitutes during absence | forum [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Per-task delegation exists; out-of-office substitutes are on the [roadmap](#roadmap-plan) |
| Business key and variables when starting a process without a form | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Yes |
| Download the deployed BPMN XML | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Yes |
| Consistent labels, several languages | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 languages |

## Comparison

A compact comparison based on each project's public documentation (read in November 2026);
"not verified" means we found no reliable source. Every project has its own direction and is a
sound choice for different needs; check their documentation before you decide.

| | License | Runs on an existing C7 DB | Migration needed | Security patches | Java / Spring Boot | UI | SSO | Docker image |
|---|---|---|---|---|---|---|---|---|
| **Fasti BPM** | Apache-2.0 | Yes, 7.24 schema unchanged | Image: none. Embedded apps: new Maven coordinates, Spring Boot 4.1 and Java 17+; `org.camunda.*` packages kept | Yes, see the [policy below](#commitments-support-and-security-patches) | 17+ / 4.1 | New (React) + classic (deprecated) | OIDC built in | Yes, from 1.0.0 |
| Camunda 7 CE 7.24 | Apache-2.0 source; some bundled web components under MIT ([13]) | (the original) | — | No CE releases after 7.24 ([1]) | 17, 21, 25 ([2]) / 3.5 | Cockpit, Tasklist, Admin (AngularJS) | OAuth2 via the security starter | Former Run image |
| Camunda 7 Enterprise | Commercial | Yes (same product line) | No | Until 04/2030, paid extension to 04/2032 ([3]) | not verified | Cockpit Enterprise | not verified | not verified |
| Camunda 8 | Camunda License 1.0; self-managed production needs a license since 8.6 ([4]) | No (different engine) | Yes (models, instances, data) | Yes (vendor) | different engine, not comparable | Operate, Tasklist | not verified | not verified |
| Operaton | Apache-2.0 | Same table structure as 7.24; 2.2 records schema version 7.25 through an upgrade script ([5]) | Packages renamed to `org.operaton.*` (OpenRewrite recipe available); schema-version script (2.2) | Yes, 2.2.x line ([6]) | 17+ / 4.1 ([5]) | Classic + webapps-neo (preview) | not verified | Yes ([7]) |
| CIB seven | Apache-2.0; EE needs a license key | Yes (7.24 changes merged) | Packages renamed to `org.cibseven.*`, prefix `cibseven.*` | Yes, per release ([8]) | 17, 21 / 3.5 and 4.0 ([9]) | Vue 3 webclient | OAuth2/JWT for REST ([8]) | Yes ([8]) |
| OrqueIO | Apache-2.0 | Compatible with C7 tables per their docs ([10]) | Maven group `io.orqueio`; package rename not stated | Yes, per their docs ([10]) | 21 / 3.5, 4.0, 4.1 | Angular rewrite | Multi-provider OIDC | not verified |
| EximeeBPMS | Apache-2.0 (Monitor: MIT) | The project states its schema stays compatible with Camunda 7 (not verified by Fasti) ([14]) | Packages renamed to `org.eximeebpms.*`, Model API renamed | Yes, the two latest minor releases ([11]) | 21 / Spring Boot version not stated | Separate Monitor | not verified | Yes (`eximeebpms-docker` repository) |
| Fluxnova | Apache-2.0 (FINOS) | not verified | not verified | not verified | 21 (Corretto image) / not verified | Control Center | not verified | Yes ([12]) |

Fasti's own weak points, stated plainly: a young project with a small team, no commercial
support yet, DB2 not verified.

Sources: [1] [CE end-of-life announcement](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921);
[2] [7.24 supported environments](https://docs.camunda.org/manual/7.24/introduction/supported-environments/);
[3] [Enterprise extension](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/);
[4] [Camunda 8.6 licensing](https://camunda.com/blog/2024/10/camunda-licensing-what-you-need-to-know/);
[5] [Operaton 2.2](https://docs.operaton.org/docs/documentation/reference/release-notes/2_2/);
[6] [Operaton SECURITY.md](https://github.com/operaton/operaton/blob/main/SECURITY.md);
[7] [operaton-docker](https://github.com/operaton/operaton-docker);
[8] [CIB seven 2.2.0](https://docs.cibseven.org/manual/latest/release-notes/release-notes-2.2.0-ce/);
[9] [CIB seven supported environments](https://docs.cibseven.org/manual/latest/introduction/supported-environments/);
[10] [OrqueIO docs](https://docs.orqueio.io/);
[11] [EximeeBPMS SECURITY.md](https://github.com/EximeeBPMS/eximeebpms/blob/main/SECURITY.md);
[12] [Fluxnova](https://github.com/finos/fluxnova-bpm-platform);
[13] [Camunda 7 licenses](https://docs.camunda.org/manual/latest/introduction/licenses/);
[14] [EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms);
independent overview: [WZ-IT, 2026-10-15](https://wz-it.com/en/blog/camunda-7-end-of-life-operaton-cibseven-fluxnova-compared/).

## Timeline

### Camunda 7 milestones

| Date | Milestone | Source |
|---|---|---|
| 2024-03-06 | Camunda announces the end of life of Camunda 7 CE for October 2025, with 7.24 as the final CE release | [Camunda forum](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921) |
| 2025-02-28 | Camunda extends Camunda 7 Enterprise support to 2030-04-09, with paid extended support to April 2032 | [Camunda blog](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) |
| 2025-10-07 | Tag `7.24.0` created and published to Maven Central: the final CE release | [tag 7.24.0](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0), [Maven Central](https://repo1.maven.org/maven2/org/camunda/bpm/camunda-engine/7.24.0/) |
| 2025-10-14 | Release date of 7.24 as scheduled by Camunda; after this date there are no further CE releases, including security patches. The CE repository has since been archived | [announcement](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921), [support page](https://docs.camunda.org/enterprise/announcement) |
| April 2030 | End of Camunda 7 Enterprise support (7.24 LTS: 2030-04-13) | [support page](https://docs.camunda.org/enterprise/announcement) |
| April 2032 | End of paid extended support for Camunda 7 Enterprise | [support page](https://docs.camunda.org/enterprise/announcement) |

### Fasti BPM

Fasti continues directly from the `7.24.0` tag; every fix and change is counted from that point
(`git log 7.24.0..`). The phases below are ordered by the work done, without dates:

1. **Foundation**: Java 17, Spring Boot 4.1, library upgrades; Maven coordinates
   `dev.fasti.platform`.
2. **Security**: fixes for the publicly disclosed issues of the 7.x line, expression allow-list,
   attachment/comment checks inside the engine, web application hardening.
3. **New web UI** at `/fasti/`, gradually replacing the classic Cockpit, Tasklist and Admin.
4. **Single sign-on** (OIDC) for Run, Spring Boot, Tomcat and WildFly.
5. **Drop-in on existing databases**: the image accepts the former `DB_*` variables and checks the schema version; tested on five database systems.
6. **Pre-release security review** (code review, penetration test of the image, ZAP baseline
   scan; the findings were fixed) and **Java4Shell mitigation**.
7. **1.0.0**: the first release (2026-11-20).

## Roadmap (plan)

> This is a **plan**, not a commitment; the order may change with user feedback.

- **Out-of-office delegation**: a substitute for a date range; new tasks are reassigned and the
  change is audited ([forum 2788](https://forum.camunda.io/t/2788),
  [38199](https://forum.camunda.io/t/38199)).
- **Four-eyes approval**: the approver of a later step must not be the person who did an earlier
  one, enforced in the engine.
- **Helm chart** for Kubernetes.
- **Metrics and OpenTelemetry**: native Micrometer/Prometheus, tracing for processes and jobs.
- **MyBatis upgrade** to a current release.
- **Long-term support (LTS) lines** for users who upgrade rarely.
- **DB2 verification**.
- **Workload aggregate endpoint**, so that the workload page stays fast with large datasets.

## Commitments: support and security patches

> This policy is maintained by the Fasti BPM project team and may be updated; the version in this
> repository is the one in force.

- **Supported line:** the latest minor release of the `1.x` line receives bug fixes and security
  patches (as patch releases, for example `1.0.1`).
- **Security patches:** vulnerabilities in Fasti and in the libraries it ships take priority over
  other work; the image is rebuilt when the base image or the JRE gets a fix. The project follows
  public security advisories about Camunda 7 and its forks and checks whether Fasti is affected.
- **Compatibility:** the `1.x` line keeps the 7.24 database schema and the `org.camunda.bpm.*`
  packages. Breaking changes are marked **BREAKING** in the [CHANGELOG](CHANGELOG.md).
- **Level of support:** this is a community project; support is best effort, **without an SLA**,
  and there is no commercial support yet.
- **Reporting vulnerabilities:** please do **not** open a public issue; see
  [SECURITY.md](SECURITY.md#security-policy) (private reporting through GitHub).
- **Fixed defects:** the *Fixed* and *Security* sections of the [CHANGELOG](CHANGELOG.md) list
  what was fixed compared with Camunda 7 CE 7.24.0; advisories are listed in
  [SECURITY.md](SECURITY.md#advisories).

## Documentation

| Document | Contents |
|---|---|
| [docs/install-docker.md](docs/install-docker.md) | Installing with Docker, step by step |
| [docs/api/README.md](docs/api/README.md) | REST API integration, Java and JavaScript external task clients |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.md) | Moving from Camunda 7, rollback, limits |
| [distro/docker/README.md](distro/docker/README.md) | Image: environment variables, drivers, operations, tuning |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | The new web UI |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell and Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy), [CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) | Changes, security, contributing |

## What this is

This repository contains the BPMN 2.0 / DMN process engine, the REST API, the web
applications (Cockpit, Tasklist, Admin) and the distributions (Run, Tomcat, WildFly). It
starts from the `7.24.0` tag of Camunda 7 CE and continues with bug fixes and library
upgrades.

The goal is **backward compatibility** with applications that run on Camunda 7:

- the `org.camunda.bpm.*` packages, the BPMN namespace
  `http://camunda.org/schema/1.0/bpmn` and the `camunda.bpm.*` configuration prefix are kept;
- the database schema of 7.24 is kept;
- all deployment flavors are kept: Spring Boot, Run, Tomcat, WildFly; `javax` and `jakarta`.

## What is different from Camunda 7.24.0 CE

- **Security:** fixes for the publicly disclosed issues of the 7.x line (expressions in
  queries, initial setup, access to attachments and comments, Cockpit permissions, open
  redirect) plus additional safeguards: an allow-list for expressions in queries and
  filters, attachment and comment checks inside the engine, hardened security filters,
  headers and cookies in the web applications.
- **Stability:** no more memory leak when scripts and DMN expressions are evaluated with
  JavaScript (GraalJS), and a number of other engine fixes.
- **Platform:** Java 17 or later, Spring Boot 4.1, GraalJS 25, Jackson 2.21 and many other
  libraries upgraded to releases with security fixes.

Some fixes change defaults toward safer behavior; read "What differs" in the
[migration guide](docs/migrate-from-camunda-7.md#what-differs) before upgrading.

## Versioning

Fasti BPM Platform follows [Semantic Versioning](https://semver.org/), starting at `1.0.0`.
The `1.x` line is compatible with applications written for Camunda 7.24.

## Maven coordinates

The artifacts of Fasti BPM Platform have their own Maven coordinates. **The Java packages
remain `org.camunda.bpm.*`**, so application code keeps its imports; the `camunda.bpm.*`
configuration prefix, the BPMN/DMN namespaces, the database schema and the module names on
WildFly are unchanged as well. As far as Maven is concerned, only the dependency declarations
change (the web application path changes separately, see
[Web application path](#web-application-path-fasti)):

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`; the groups `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` and `org.camunda.template-engines` → `dev.fasti.platform.<group name>`.
- **artifactId:** the prefix `camunda-` becomes `fasti-`, the rest is kept.
- **version:** the version of Fasti BPM Platform (see [Versioning](#versioning)) instead of
  `7.24.0`.

No release of these artifacts has been published to Maven Central yet. Until then, build
from source and make the artifacts available in the Maven repository your project uses.

Manage the versions with the BOM:

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

Frequently used artifacts:

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

The FEEL library `org.camunda.feel:feel-engine` is an external library that is not part of
this repository; its coordinates stay as they are.

### Third-party libraries that still pull in the original engine

Because the packages are unchanged, the original engine (`org.camunda.bpm:camunda-engine`)
and `fasti-engine` contain classes with the same names. Maven treats them as two unrelated
libraries and does not drop either of them. If a third-party library (a community extension,
for example) depends on the original artifacts, exclude them and declare the corresponding
Fasti artifacts instead:

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

`mvn dependency:tree -Dincludes="org.camunda*"` lists original artifacts that are still
present (`org.camunda.feel:feel-engine` is legitimate, do not exclude it).

### Startup check

When it is created, the engine inspects the class path and **refuses to start** if it finds
the classes of the process engine in more than one library; the error `ENGINE-12021` lists
the location of each of them. The Spring Boot starter performs the same check as soon as the
application starts. A library that is only visible through the parent class loader (a shared
engine of the server next to an engine embedded in a web application, for example) is not
counted. If your setup is intentional, the check can be switched off with a system property:

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

Limitation: if the original engine library precedes `fasti-engine` on the class path, the
engine is built by the classes of the original library, which do not contain this check.
In that case only applications that use the Fasti Spring Boot starter are stopped; with
other integrations, review the dependencies as described above.

## Web application path: `/fasti`

Cockpit, Tasklist, Admin and the new web UI are served below `/fasti` (formerly `/camunda`)
by every distribution: `http://localhost:8080/fasti/` opens the new UI
(`/fasti/app/next/default/`), the classic applications live at `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` and `/fasti/app/admin/...`. The REST API stays at `/engine-rest`.

**The classic applications are deprecated.** The classic Cockpit, Tasklist, Admin and Welcome
are built on AngularJS 1.8.2 and Bootstrap 3, both end of life. **The container image does not
serve them by default** (breaking change): their pages and old bookmarks redirect to the matching section of the new UI, and requests for their files return `404`; `FASTI_CLASSIC_WEBAPPS=true` brings them
back (with a warning), see [distro/docker/README.md](distro/docker/README.md). The
Tomcat and WildFly bundles do not serve them by default either (bring them back with
`-Dfasti.webapp.classic.enabled=true` or `FASTI_CLASSIC_WEBAPPS=true`). The Run zip and the Spring
Boot starter keep serving them as before, so that running installations do not break; turn them
off with `fasti.webapp.classic.enabled: false`. The classic applications will be removed in a
later release.

The old path keeps working: every request for `/camunda/...` is redirected to the same path
below `/fasti/...`, keeping the rest of the path and the query string. GET and HEAD get
`301`; all other methods (POST, PUT, DELETE, e.g. old clients calling `/camunda/api/...`)
get `308`, so that clients repeat the request with the same method and body. The redirects
carry `Cache-Control: no-store`, so switching back to `/camunda` takes effect at once.

To restore `/camunda`:

- **Spring Boot, Run:** `camunda.bpm.webapp.application-path: /camunda` (no redirect then).
  To turn off only the redirect: `fasti.webapp.legacy-path-redirect: false`.
- **Tomcat:** delete `webapps/camunda` (the redirect application) and rename
  `webapps/fasti` to `webapps/camunda`.
- **WildFly:** delete `standalone/deployments/camunda.war` (and `camunda.war.dodeploy`) and
  set `<context-root>/camunda</context-root>` in `WEB-INF/jboss-web.xml` of the web
  application WAR.

**Reverse proxies:** if a proxy forwards only `/camunda/` (or rewrites paths, sets cookie
paths, CSP or health checks for `/camunda`), add `/fasti/` or move to `/fasti/` entirely.
The `XSRF-TOKEN` cookie (and `JSESSIONID` on Tomcat and WildFly) now has the path `/fasti`,
so users sign in once more. Bookmarks and old links keep working through the redirect.

## Single sign-on (OpenID Connect)

Supported on **Run** (`./start.sh --oauth2`, module `internal/oauth2`) and on **Spring Boot**
applications with `fasti-bpm-spring-boot-starter-security`. It is enabled by declaring a
Spring Security client registration (`spring.security.oauth2.client.*`). For **Tomcat** and
**WildFly** see the end of this section.

- **Login:** the login page of the new web application shows "Sign in with &lt;provider&gt;"
  instead of the password form (label: `fasti.security.oidc.login-label`, defaults to the
  `client-name`; with several registrations choose one with
  `fasti.security.oidc.registration-id`). The authorization code flow uses `state`, `nonce`
  and PKCE (S256, also for confidential clients); the session id is replaced on login. Users
  return to the page of the new web application they started from (only paths below
  `/fasti/app/next/` are accepted, so there is no open redirect).
- **Logout:** `POST /logout` (same-origin requests only) ends the session; with
  `camunda.bpm.oauth2.sso-logout.enabled=true` the user continues to the provider's end
  session endpoint (RP-initiated logout, `post-logout-redirect-uri`). Spring Security's
  back-channel logout stays enabled. `GET /logout` no longer logs the user out.
- **Session expiry:** when the access token cannot be refreshed or the session times out,
  requests to `/fasti/api/**` return `401` (no redirect) and the new web application shows its login page.
- **CSRF:** `/fasti/api` and `/fasti/app` keep the web applications' own CSRF filter
  (`X-XSRF-TOKEN`); Spring Security's CSRF protection stays disabled, as upstream.
- **Groups, administrators, tenants:** the groups claim is
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` (upstream property name; nested
  claims such as `realm_access.roles` work). `fasti.security.oidc.groups.admin` lists the
  claim values that grant `camunda-admin`; `groups.mapping` maps claim values to engine group
  ids; `groups.include-unmapped=false` drops unmapped values. **Changed from upstream:** a
  claim value `camunda-admin` no longer grants administrator rights by itself, and
  authorities such as `SCOPE_openid` or `OIDC_USER` no longer become groups.
  `fasti.security.oidc.tenants.claim` maps tenants. Name and email:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Identity provider:** by default the engine reads users, groups and tenants from the
  token of the current session (read-only). Set
  `camunda.bpm.oauth2.identity-provider.enabled=false` to use the database instead (groups
  then come from the membership tables). Auto-provisioning is not implemented.
  While users come from the token (the default), the initial setup page (first administrator)
  is switched off, since administrators come from the claims (`groups.admin`); previously, users outside the administrators group were sent to the setup page.
- **REST API with bearer tokens:** `fasti.security.oidc.rest.bearer-enabled=true` accepts
  `Authorization: Bearer <JWT>` on `/engine-rest` in addition to basic authentication.
  `rest.issuer-uri` and `rest.audiences` are required; signatures are checked against the
  issuer's JWKS with asymmetric algorithms only (`rest.jws-algorithms`, default `RS256`), and
  `iss`, `aud`, `exp`, `nbf` are validated. Run also needs `camunda.bpm.run.auth.enabled=true`.
  A browser session cookie never authenticates `/engine-rest`.

Keycloak example (`configuration/default.yml` of Run):

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

Microsoft Entra ID example:

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

**Tomcat, WildFly (WAR):** the same features, configured in `fasti-oidc.properties` (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; an annotated `fasti-oidc.properties.example`
sits next to it). Without the file nothing changes. The keys match Run's `fasti.security.oidc.*` properties, plus `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`. The
provider must allow the redirect URI `<base>/fasti/sso/callback`; the back-channel logout URL is
`<base>/fasti/sso/backchannel-logout`. Bearer tokens on `/engine-rest` are enabled with
`fasti.security.oidc.rest.bearer-enabled=true` (basic authentication keeps working unless
`rest.basic-enabled=false`; the engine list `/engine` requires credentials too). Users need not
exist in the database. For the engine to answer queries about the groups and tenants of the
signed-in user from the claims, enable the `OidcIdentityProviderPlugin` (commented out in
`bpm-platform.xml` / `standalone.xml`). Limits: the sessions tracked for back-channel logout are
kept in memory per server (not shared across cluster nodes); the classic applications only redirect to the provider; a complete logout requires the new web UI.

## Build

Requires JDK 17 or later (21 recommended).

```bash
./mvnw clean verify
```

A full build runs all Java tests and the web UI unit tests (close to an hour; the Playwright
end-to-end tests run separately, see [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)) and needs network access to Maven Central,
nodejs.org and the npm registry. See [TESTING.md](TESTING.md).

## Acknowledgements

- Fasti BPM Platform builds on more than a decade of work by Camunda and the Camunda 7
  community.
- While reviewing defects, the project consulted public information (issues, advisories,
  release notes) of other community projects that descend from Camunda 7: Operaton, CIB
  seven, OrqueIO and EximeeBPMS. The source code in this repository was written
  independently on top of Camunda 7 CE.

## License

[Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for the copyright of the original code.
