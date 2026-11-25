English | [Tiếng Việt](docs/i18n/README.vi.md) | [Deutsch](docs/i18n/README.de.md) | [Español](docs/i18n/README.es.md) | [Français](docs/i18n/README.fr.md) | [हिन्दी](docs/i18n/README.hi.md) | [Bahasa Indonesia](docs/i18n/README.id.md) | [日本語](docs/i18n/README.ja.md) | [한국어](docs/i18n/README.ko.md) | [Português (Brasil)](docs/i18n/README.pt-BR.md) | [Русский](docs/i18n/README.ru.md) | [ไทย](docs/i18n/README.th.md) | [简体中文](docs/i18n/README.zh-CN.md)

# Fasti BPM Platform

Fasti BPM is an independent, open-source (Apache-2.0) process engine: it runs business processes
modelled in BPMN and decisions in DMN, with user tasks, timers, messages and a full history. It is
built on Camunda 7 Community Edition 7.24.0 and runs on your existing Camunda 7.24 database, with
current libraries, security fixes and a new web interface. Website: <https://fasti.dev>

<p align="center">
  <a href="https://fasti.dev"><img src="https://assets.fasti.dev/img/og-cover-2.png" alt="Fasti BPM: the task list of the new web interface" width="820"></a>
</p>

<p align="center">
  <a href="https://github.com/fasti-bpm/fasti/releases/latest"><img src="https://img.shields.io/github/v/release/fasti-bpm/fasti?label=release" alt="Latest release"></a>
  <a href="https://hub.docker.com/r/fastidev/fasti-bpm"><img src="https://img.shields.io/docker/pulls/fastidev/fasti-bpm" alt="Docker pulls"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue" alt="License: Apache-2.0"></a>
</p>

| **138 → 14 ms** | **BPMN · DMN · forms** | **Secure defaults** | **13 languages** |
|:---:|:---:|:---:|:---:|
| overdue task list with authorization on ([how it was measured](docs/benchmark-camunda-7.24.md)) | modeler in the browser | sign-in and authorization on; self-tested, [results public](docs/security/pentest-1.3.md) | phone and desktop |

> **Not affiliated with Camunda Services GmbH; Camunda is a trademark of Camunda Services GmbH.**
> Fasti BPM is an independent open-source project (Apache-2.0). It is not part of, sponsored by
> or endorsed by Camunda Services GmbH. The name "Camunda" is used only to describe where the
> source code comes from and what Fasti is compatible with.

<a id="quick-start"></a>

## Quick start

The image is available on Docker Hub (`fastidev/fasti-bpm`) and on GitHub
Container Registry (`ghcr.io/fasti-bpm/fasti-bpm`), for amd64 and arm64:

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.3.1
```

Open <http://localhost:8080/fasti/> and sign in. Without `DB_URL` the image uses an H2 database
inside the container, for evaluation only. The REST API needs Basic credentials:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

Full guide, including your own database: <https://fasti.dev/install>

<a id="highlights"></a>

## Highlights

**Same database, same packages.** The Camunda 7.24 schema is used as it is, the
`org.camunda.bpm.*` packages stay, and the image takes the same `DB_*` variables as Camunda Run.
Coming from Camunda Run or the 7.24 image, point Fasti at the same database and give REST clients
credentials; embedded applications are rebuilt against Fasti. Fasti and 7.24 nodes can share one
database, and you can go back ([migration](https://fasti.dev/install#migrate)).

Everything in this repository is Apache-2.0, including single sign-on, the audit trail, the
four-eyes control and the analytics. There is no enterprise edition, and Fasti sends no telemetry.

### Secure defaults

- **Sign-in required from the first start** (container image): sign-in required, no default or demo user,
  and settings that turn REST authentication or authorizations off are refused. Tomcat and
  WildFly require credentials for the REST API and create no users.
- **Fixes for the Camunda 7 advisories with published details**, in every distribution,
  including the 2026 ones that Camunda fixed only in Enterprise releases (expressions in queries,
  initial setup, attachments and comments, Cockpit permissions, XML in the external task client):
  [status of each](docs/security/camunda-7-advisories.md). Seven 2026 advisories have no
  published details; we cannot say yet whether they apply.
- **Tested, results public:** our own security test of 1.3 (not a third-party audit), what held
  and what was fixed: [security test](docs/security/pentest-1.3.md).
- **Know the model:** whoever may deploy processes can run code on the server (script tasks are
  part of BPMN). Grant `CREATE` on deployments only to people you would give shell access.
- **Further safeguards:** an allow-list for expressions in queries and filters, attachment and
  comment checks inside the engine, hardened security filters, headers and cookies in the web
  applications, and a startup warning on a JVM exposed to Java4Shell
  ([security](https://fasti.dev/security), [what differs](docs/migrate-from-camunda-7.md#what-differs)).
- **Single sign-on:** OpenID Connect and JWT bearer tokens ([guide](docs/single-sign-on.md)).

### A new web interface

- **One modern application** for the task list, monitoring, cases (CMMN) and administration, in
  13 languages, light and dark, usable on a phone ([features](https://fasti.dev/#features)).
- **Modeler built in:** edit BPMN, DMN and forms and deploy them from the same interface
  ([modeler](https://fasti.dev/#modeler)).
- **Process analytics:** a heat map on the diagram by median, P90, P99, time waiting, rework or
  incidents, a bottleneck ranking, the slowest and longest-waiting instances, the trend against a
  target and the waiting vs working time of user tasks. Computed by the database you already run,
  with no extra service or license ([guide](docs/process-analytics.md)).
- **The tools to run it:** history, instance modification and restart, migration, batch
  operations, bulk retry and the operation log ([operations](https://fasti.dev/#operate)).
- **Light on the server:** the workload board loads with 11 API requests (the classic Tasklist
  and Cockpit needed 344)
  ([web UI performance](docs/performance-web-ui.md)).

### Faster, with security on

Measured on PostgreSQL 16 with 1,000,000 tasks and 2,200,000 authorizations, REST as an
administrator, against Camunda Run 7.24.0 on the same machine
([comparison](docs/benchmark-camunda-7.24.md)):

| | Camunda 7.24 | Fasti 1.2.1 |
|---|---|---|
| Overdue task list, authorization on | 138 ms | **14 ms** |
| Task board of 145 requests, authorization on | 20.8 s | **8.8 s** |
| Idle memory, as shipped | 393 MB | **362 MB** |

The image is 390 MB and answers REST requests about 6.5 s after start (`Started in` 4.8 s);
it returns unused heap to the system (G1 periodic collection).

- **Optional index scripts** for six databases: an idle job poll 162 ms → 0.1 ms, overdue tasks
  sorted by due date 61 ms → 0.3 ms ([database performance](docs/performance.md)).
- **One request for a team's workload** (`/task/workload`) instead of 145.

### Current platform

Java 17 or later, Spring Boot 4.1, GraalJS 25, Jackson 2.21 and libraries upgraded to releases
with security fixes.

<a id="installation"></a><a id="get-fasti"></a>

## Get Fasti

- <a id="one-command-on-your-existing-camunda-724-database"></a>**Docker image** (`fastidev/fasti-bpm`, amd64 and arm64): <https://fasti.dev/install>; step by
  step in [docs/install-docker.md](docs/install-docker.md), all variables in
  [distro/docker/README.md](distro/docker/README.md#environment-variables).
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**Tomcat bundle**, without Docker: <https://fasti.dev/download>. Ready-made Tomcat 1.3.0 bundle:
  - [fasti-bpm-tomcat-1.3.0.zip](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.zip.sha256))
  - [fasti-bpm-tomcat-1.3.0.tar.gz](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.tar.gz.sha256))

  Verify the checksum before extracting, for example `sha256sum -c fasti-bpm-tomcat-1.3.0.zip.sha256`,
  and read `README.txt` in the bundle before starting it.
- **Embedded** in Spring Boot or another Java application: new Maven coordinates
  (`dev.fasti.platform:fasti-*`), the same `org.camunda.bpm.*` packages; see
  [docs/embedding.md](docs/embedding.md).

<a id="moving-from-camunda-7"></a>

## Moving from Camunda 7

If you run Camunda 7.24, Fasti runs on that same database without changing its schema. You can
replace the old nodes, run Fasti next to them, and go back to Camunda 7.24 at any time. The Fasti
image was tested taking over databases created by Camunda Run 7.24 on PostgreSQL 16, MariaDB 11.8,
MySQL 8.4, SQL Server 2022 and Oracle Database 23ai; going back to Camunda Run was tested on the
same databases except MySQL. DB2 is not tested yet. Every REST client, external task workers
included, must now send Basic credentials or a bearer token.

Embedded Spring Boot applications also move to Spring Boot 4.1 and Java 17; older schemas are
upgraded with the standard scripts first. Plugins and process applications built for Camunda 7 must
be rebuilt against the Fasti artifacts to run inside Fasti. Running side by side and going back have
limits (the same configuration on every node, which node runs jobs, processes that use Fasti-only
features): see
[mixed operation](docs/migrate-from-camunda-7.md#mixed-operation-with-camunda-7-nodes) and
[rollback](docs/migrate-from-camunda-7.md#rollback-to-camunda-724).

Steps and limits: <https://fasti.dev/install#migrate> and the
[migration guide](docs/migrate-from-camunda-7.md) (rollback, older schemas, test results).

<a id="comparison"></a><a id="how-it-compares"></a>

## How it compares

Several good projects carry Camunda 7 forward. Fasti keeps the `org.camunda.bpm.*` packages and
the 7.24 schema, so the move is a change of connection rather than a migration project, and it
adds a new interface. The other projects are sound choices too. The full table, with sources:
<https://fasti.dev/compare>.

<a id="commitments-support-and-security-patches"></a><a id="security-and-support"></a>

## Security and support

The latest minor release of the `1.x` line receives bug fixes and security patches, as patch
releases such as `1.3.1`, at least until 31 December 2027. Should the project stop, your database
is still a Camunda 7.24 database: go back to 7.24 or to another fork without migrating data.
Please report security problems privately, not in a public issue: see
[SECURITY.md](.github/SECURITY.md#security-policy), which also holds the
[support policy](.github/SECURITY.md#support-policy). Fix list, Java4Shell and the pre-release review:
<https://fasti.dev/security>. For help with installation, migration or production, and for support
terms, write to <contact@fasti.dev>.

<a id="who-maintains-fasti"></a>

## Who maintains Fasti

Fasti BPM is an independent open-source project (GitHub organization `fasti-bpm`, contact
<contact@fasti.dev>). It is not affiliated with Camunda and has no enterprise edition.

We work with the most capable AI models available at the time, currently Anthropic's Claude
(Opus and Fable) and OpenAI's GPT through Codex, to write code and above all to review it and hunt
for bugs. Security-relevant changes are reviewed in parallel by several models that do not see each
other's conclusions, and a finding counts only once it is confirmed in the code and by a test; this
is how the issues fixed in 1.3.1 were found. A change is never accepted because a model wrote it:
every change goes through the test suite (more than 16,000 engine tests inherited from Camunda 7
plus our own), the web UI tests, CI on Java 17 and 21 and CodeQL. What we tested for security, and
what we did not, is in the [security test](docs/security/pentest-1.3.md).

We also study how people actually use and judge BPM tools: we read the questions and complaints of
users on Hacker News, the Camunda forum, blogs and the issue trackers of Camunda 7 and its forks,
above all from teams that run BPM in production, and turn what keeps coming up into features,
fixes and documentation. That is why single sign-on, the audit trail, the four-eyes control and
the analytics are free here, why the security results are public, and why the upgrade notes say
plainly what changes.

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## Roadmap

This is a plan, not a commitment to dates. Released in `1.3.0`:
[process analytics](docs/process-analytics.md) with a heat map on the diagram, bottlenecks,
percentiles, rework, the trend and user-task times, computed by the database. Released in `1.2.1`: faster authorization checks, a
smaller image that starts faster and uses less memory
([comparison with Camunda 7.24](docs/benchmark-camunda-7.24.md)). Released in `1.2.0`, focused on performance: a workload
endpoint for task counts, optional database indexes, a startup performance advisor, audit trail
tuning and a faster web UI. Released in `1.1.0`: a Helm chart and Kubernetes manifests, Prometheus
metrics, OpenTelemetry tracing, structured JSON logs, a substitute during absence, four-eyes control
for approvals, a tamper-evident (hash-chained) audit trail, an MCP server for AI assistants, and cases (CMMN) in the
new interface. Next in `1.x`: publishing the Maven artifacts on Maven Central, verification on DB2,
a native-speaker review of the translations, and retiring the AngularJS applications. The timeline,
from the end of Camunda 7 CE to the goals for 2027: <https://fasti.dev/#timeline>.

<a id="documentation"></a>

## Documentation

On [fasti.dev](https://fasti.dev): [Install with Docker](https://fasti.dev/install) ·
[Tomcat download](https://fasti.dev/download) · [API integration guide](https://fasti.dev/docs) ·
[Security and support](https://fasti.dev/security) · [Compare](https://fasti.dev/compare) ·
[Blog](https://fasti.dev/blog)

<a id="what-this-is"></a><a id="what-is-different-from-camunda-7240-ce"></a><a id="versioning"></a><a id="maven-coordinates"></a><a id="third-party-libraries-that-still-pull-in-the-original-engine"></a><a id="startup-check"></a><a id="web-application-path-fasti"></a><a id="single-sign-on-openid-connect"></a><a id="a-ui-built-from-real-user-complaints"></a>

| Document | Contents |
|---|---|
| [docs/install-docker.md](docs/install-docker.md) | Installing with Docker, step by step |
| [docs/install-kubernetes.md](docs/install-kubernetes.md) | Installing on Kubernetes: Helm chart and plain manifests (from 1.1.0) |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.md) | Moving from Camunda 7, rollback, limits |
| [docs/embedding.md](docs/embedding.md) | Compatibility, what is different from 7.24.0, versioning, Maven coordinates, startup check |
| [docs/web-ui.md](docs/web-ui.md) | The web interface: features users asked for, the `/fasti` path, the classic apps |
| [docs/absence.md](docs/absence.md) | Substitutes during absence (from 1.1.0) |
| [docs/four-eyes.md](docs/four-eyes.md) | Four-eyes control for approvals (from 1.1.0) |
| [docs/audit-trail.md](docs/audit-trail.md) | Tamper-evident (hash-chained) audit trail (from 1.1.0) |
| [docs/performance.md](docs/performance.md) | Database performance: optional indexes, startup hints, engine settings, measurements (from 1.2.0) |
| [docs/process-analytics.md](docs/process-analytics.md) | Process analytics: heat map, bottlenecks, durations, REST API, settings, measurements (from 1.3.0) |
| [docs/single-sign-on.md](docs/single-sign-on.md) | Single sign-on (OpenID Connect): Run, Spring Boot, Tomcat, WildFly |
| [docs/api/README.md](docs/api/README.md) | REST API integration, Java and JavaScript external task clients |
| [distro/docker/README.md](distro/docker/README.md) | Image: environment variables, drivers, operations, tuning |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | The new web UI |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell and Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](.github/SECURITY.md#security-policy) | Changes, security, support policy |

<a id="build"></a>

## Build from source

Requires JDK 17 or later (21 recommended) and network access to Maven Central, nodejs.org and the
npm registry (the build downloads Node/npm for the web UI).

```bash
./mvnw clean verify
```

A full build runs all Java tests and the web UI unit tests (close to an hour; the Playwright
end-to-end tests run separately, see [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)).
See [TESTING.md](docs/testing.md). To build the container image (the first build takes about 10–20
minutes):

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## Contributing

Questions, bug reports and pull requests are welcome on GitHub; see
[CONTRIBUTING.md](.github/CONTRIBUTING.md#how-to-contribute) and the
[Code of Conduct](.github/CODE_OF_CONDUCT.md). Reviews of the translations by native speakers are
especially welcome.

Using Fasti BPM in your organization? [Add it to the adopters](docs/adopters.md): a short form, and
only with your permission.

<a id="acknowledgements"></a>

## Acknowledgements

- Fasti BPM Platform builds on more than a decade of work by Camunda and the Camunda 7
  community.
- While reviewing defects, the project consulted public information (issues, advisories,
  release notes) of other community projects that descend from Camunda 7: Operaton, CIB
  seven, OrqueIO and EximeeBPMS. The source code in this repository was written
  independently on top of Camunda 7 CE.

<a id="license"></a>

## License

[Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for the copyright of the original code.
