English | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

# Fasti BPM Platform

Fasti BPM is an independent, open-source engine built on Camunda 7 Community Edition 7.24.0. It
runs on your existing Camunda 7 database, with current libraries, security fixes and a new web
interface. Website: <https://fasti.dev>

> **Not affiliated with Camunda Services GmbH; Camunda is a trademark of Camunda Services GmbH.**
> Fasti BPM is an independent open-source project (Apache-2.0). It is not part of, sponsored by
> or endorsed by Camunda Services GmbH. The name "Camunda" is used only to describe where the
> source code comes from and what Fasti is compatible with.

<a id="quick-start"></a>

## Quick start

The image is available on Docker Hub (`fastidev/fasti-bpm`) and on GitHub
Container Registry (`ghcr.io/fasti-bpm/fasti-bpm`), for amd64 and arm64:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.2.0
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

- **Change the connection. That is the migration.** The Camunda 7.24 schema is used as it is; the
  `org.camunda.bpm.*` packages stay ([migration](https://fasti.dev/install#migrate)).
- **One container, one command**, with the same `DB_*` variables as Camunda Run ([install](https://fasti.dev/install)).
- **Built for the people who work the queue**: a new web interface in 13 languages, light and
  dark, usable on a phone ([features](https://fasti.dev/#features)).
- **Model and deploy in the browser**: BPMN, DMN and forms ([modeler](https://fasti.dev/#modeler)).
- **The tools to run it are in the box**: history, instance repair, bulk retry, operation log
  ([operations](https://fasti.dev/#operate)).
- **Closed by default in the container image**: sign-in required from the first start, no
  default user, settings that turn REST authentication or authorizations off are refused, and
  Java4Shell is mitigated. Tomcat and WildFly require credentials for the REST API and create no
  users (the first administrator is created on the setup page); the Run zip and the Spring Boot
  starter keep the Camunda 7 defaults. Every distribution has the security fixes and warns at
  startup on a JVM exposed to Java4Shell ([security](https://fasti.dev/security)).
- **Single sign-on**: OpenID Connect and JWT bearer tokens ([guide](docs/single-sign-on.md)).
- **Current platform**: Java 17 or later, Spring Boot 4.1, GraalJS 25, Jackson 2.21
  ([performance](https://fasti.dev/#performance)).

<a id="installation"></a><a id="get-fasti"></a>

## Get Fasti

- <a id="one-command-on-your-existing-camunda-724-database"></a>**Docker image** (`fastidev/fasti-bpm`, amd64 and arm64): <https://fasti.dev/install>; step by
  step in [docs/install-docker.md](docs/install-docker.md), all variables in
  [distro/docker/README.md](distro/docker/README.md#environment-variables).
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**Tomcat bundle**, without Docker: <https://fasti.dev/download>. Ready-made Tomcat 1.1.0 bundle:
  - [fasti-bpm-tomcat-1.1.0.zip](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.zip.sha256))
  - [fasti-bpm-tomcat-1.1.0.tar.gz](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.tar.gz.sha256))

  Verify the checksum before extracting, for example `sha256sum -c fasti-bpm-tomcat-1.1.0.zip.sha256`,
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
releases such as `1.0.1`. Please report security problems privately, not in a public issue: see
[SECURITY.md](SECURITY.md#security-policy), which also holds the
[support policy](SECURITY.md#support-policy). Fix list, Java4Shell and the pre-release review:
<https://fasti.dev/security>. For help with installation, migration or production, and for support
terms, write to <contact@fasti.dev>.

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## Roadmap

This is a plan, not a commitment to dates. Released in `1.2.0`, focused on performance: a workload
endpoint for task counts, optional database indexes, a startup performance advisor, audit trail
tuning and a faster web UI. Released in `1.1.0`: a Helm chart and Kubernetes manifests, Prometheus
metrics, OpenTelemetry tracing, structured JSON logs, a substitute during absence, four-eyes control
for approvals, an immutable audit trail, an MCP server for AI assistants, and cases (CMMN) in the
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
| [docs/audit-trail.md](docs/audit-trail.md) | Immutable audit trail (from 1.1.0) |
| [docs/performance.md](docs/performance.md) | Database performance: optional indexes, startup hints, engine settings, measurements (from 1.2.0) |
| [docs/single-sign-on.md](docs/single-sign-on.md) | Single sign-on (OpenID Connect): Run, Spring Boot, Tomcat, WildFly |
| [docs/api/README.md](docs/api/README.md) | REST API integration, Java and JavaScript external task clients |
| [distro/docker/README.md](distro/docker/README.md) | Image: environment variables, drivers, operations, tuning |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | The new web UI |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell and Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy) | Changes, security, support policy |

<a id="build"></a>

## Build from source

Requires JDK 17 or later (21 recommended) and network access to Maven Central, nodejs.org and the
npm registry (the build downloads Node/npm for the web UI).

```bash
./mvnw clean verify
```

A full build runs all Java tests and the web UI unit tests (close to an hour; the Playwright
end-to-end tests run separately, see [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)).
See [TESTING.md](TESTING.md). To build the container image (the first build takes about 10–20
minutes):

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## Contributing

Questions, bug reports and pull requests are welcome on GitHub; see
[CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) and the
[Code of Conduct](CODE_OF_CONDUCT.md). Reviews of the translations by native speakers are
especially welcome.

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
