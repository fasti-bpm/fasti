# Changelog

Mọi thay đổi đáng chú ý của Fasti BPM được ghi ở đây. Định dạng theo
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), phiên bản theo
[Semantic Versioning](https://semver.org/). Fasti BPM được xây dựng trên Camunda 7 CE 7.24.0;
các mục dưới đây là khác biệt so với bản đó.

All notable changes to Fasti BPM are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). Fasti BPM is built on Camunda 7 CE 7.24.0; the
entries below describe the differences from that release.

## [Unreleased]

### Added

- Helm chart (`distro/kubernetes/helm/fasti-bpm`) and plain Kubernetes manifests with Kustomize
  (`distro/kubernetes/manifests`) for the container image against an external database:
  production mode on by default, credentials only from existing Secrets (mounted as files),
  non-root pod with a read-only root file system that passes the `restricted` Pod Security
  Standard, startup/liveness/readiness probes on the actuator health endpoints, graceful
  shutdown, pod anti-affinity, topology spread, PodDisruptionBudget, and optional Ingress with
  TLS, HorizontalPodAutoscaler, NetworkPolicy, ServiceMonitor and OpenID Connect settings. A
  values schema rejects unknown keys. Guide: `docs/install-kubernetes.md`. CI checks the chart
  and the manifests with `helm lint` and kubeconform against Kubernetes 1.29–1.33.
- Observability for Fasti BPM Run and the container image, all off by default
  ([docs](docs/install-docker.md#13-observability)):
  - JSON logs: `fasti.logging.format=json` (`FASTI_LOG_FORMAT=json` in the image) writes one
    JSON object per log line in the Elastic Common Schema, through the structured logging of
    Spring Boot; `logstash` and `gelf` are available as well.
  - Prometheus metrics at `/actuator/prometheus` with `fasti.metrics.prometheus.enabled=true`:
    JVM, HTTP, connection pool, job executor thread pool and process engine metrics. On the
    port of the application a scrape token is required (`Authorization: Bearer`,
    `fasti.metrics.prometheus.token`, `FASTI_METRICS_PROMETHEUS_TOKEN(_FILE)`); without one Run
    only starts if the actuator has a management port of its own (`management.server.port`,
    `FASTI_MANAGEMENT_PORT`). While the switch is off the endpoint does not exist, even for
    configurations that expose every actuator endpoint.
  - Engine metrics for Micrometer, also in the Spring Boot starter (`fasti.metrics.engine.*`):
    counters of started and ended root process instances, activity instances, executed,
    failed, acquired and rejected jobs and evaluated decisions, and gauges of active process
    instances, open user tasks, incidents, due and failed jobs and open external tasks (read
    from the database at most every 30 s). Tags are limited to the engine name and a fixed
    result or state.
  - OpenTelemetry tracing with `fasti.tracing.enabled=true`: spans for HTTP requests, the
    engine commands they run and each job execution, exported over OTLP/HTTP to
    `management.opentelemetry.tracing.export.otlp.endpoint` or `OTEL_EXPORTER_OTLP_ENDPOINT`.
    Nothing is sent while no endpoint is set.

### Changed

- New web UI: one consistent look across the Tasklist, monitoring, modeler, administration,
  preferences and welcome pages. Page headers, cards, tables, tabs, confirmations and options
  are styled with the application instead of with the monitoring section, so a page no longer
  looks different depending on the page opened before it (the preferences showed bare radio
  buttons when opened directly). Every size, weight, layer and shadow comes from the design
  tokens; the subtle text colour of the light theme and the frames of text fields now meet
  WCAG 2.2 AA contrast (4.5:1 and 3:1). The tabs of a task and of the other pages, and the
  segmented choices, share one style with a visible focus. Every dialog is left with the same
  action, placed before the main one; pages that failed to load offer to try again; the
  operation log keeps its filter fields apart and formats times like the rest; scrolling
  started by the application honours reduced motion.

### Fixed

- The container image ignores a `FASTI_PORT` that is not a port number, with a warning, in the
  entrypoint and the `HEALTHCHECK`: Kubernetes sets `FASTI_PORT=tcp://<ip>:<port>` for a Service
  named `fasti`, and the server then failed to start.

## [1.0.1] - 2026-11-21

### Security

- The template filter of the web applications no longer looks up resources outside their
  folder: a request such as `/app/cockpit/default/../../WEB-INF/x.html` could be forwarded to a
  file below `WEB-INF`. Names with `..`, backslashes, percent-encoded or control characters are
  refused, also in the Spring Boot starter.
- LDAP identity provider: `acceptUntrustedCertificates=true` replaced the default `SSLContext`
  of the JVM, so other TLS connections that use that default (for example to the OpenID
  Connect provider, connectors or mail) could also accept untrusted certificates. The setting
  now applies only to the connections to the LDAP server.
- Dependency updates that close the open security advisories of the classic web applications
  (among them qs, express, ws, node-forge, shell-quote, serialize-javascript and
  fast-xml-parser 5), of the REST API documentation build (postcss, fast-xml-parser) and of
  test libraries (AssertJ, OkHttp, OpenAPI Generator). The development server of the classic
  web applications moves to webpack-dev-server 5.2.7. The remaining advisories concern
  AngularJS 1.8 and Bootstrap 3 of the deprecated classic web applications, for which no fixed
  release exists; those applications stay off by default.
- The container image of each release is signed with cosign 3 in the Sigstore bundle format.
  Verify it with cosign 3, or with cosign 2.6 and `--new-bundle-format`.

### Changed

- Minor and patch updates of the Java libraries and Maven plugins (among them Logback 1.6.7,
  GraalJS 25.0.5.1, Joda-Time 2.15.1, Unirest 2.4.03) and of the web UI (bpmn-js 18.33,
  TanStack Query 5.108).
- The build supports Maven 3.9 and later; the Maven wrapper uses Maven 3.10.1.
- Documentation is English-first with translations into 12 more languages, and the roadmap
  follows the plan published on fasti.dev.

### Fixed

- The check of a `bpm-platform.xml` location accepted characters other than a drive letter
  before `:` and `|` as a path separator.

## [1.0.0] - 2026-11-20

### Added

- New web UI below `/fasti/`: application shell and Tasklist, monitoring and modelling,
  administration, translations into eleven more languages, unit and end-to-end tests.
- OpenID Connect single sign-on and JWT bearer tokens for the REST API; single sign-on for
  Tomcat and WildFly.
- Comments on a process instance as a whole (REST API).
- Container image that runs on the database of an existing Camunda 7 installation and accepts
  the environment variables of the former Run image.
- CI workflows (build, CodeQL, dependency review, signed multi-arch release), Dependabot,
  code of conduct, changelog, issue and pull request templates.
- Guides for installing with Docker (with a PostgreSQL `docker compose` example) and for
  integrating through the REST API.

### Changed

- **BREAKING:** Java 17 is the minimum.
- **BREAKING:** Spring Boot starters and Run move to Spring Boot 4.1.
- **BREAKING:** artifacts are published as `dev.fasti.platform:fasti-*`, version `1.0.0`.
- **BREAKING:** the engine refuses to start with two engine libraries on one class path or with
  a schema older than 7.24.
- **BREAKING:** the web apps are served below `/fasti`; `/camunda` redirects there.
- **BREAKING:** the container image serves only the new web UI. The classic Cockpit, Tasklist,
  Admin and Welcome (AngularJS 1.8.2 and Bootstrap 3, both end of life) are off: their pages and
  bookmarks, also below `/camunda/app/`, redirect to the matching section of the new UI and
  their files answer 404. `FASTI_CLASSIC_WEBAPPS=true` brings them back with a warning. Run and
  the Spring Boot starter are unchanged; `fasti.webapp.classic.enabled=false` turns the classic
  apps off there.
- **BREAKING:** the Tomcat and WildFly distributions do not serve the classic web applications
  either. The system property `fasti.webapp.classic.enabled=true`, the environment variable
  `FASTI_CLASSIC_WEBAPPS=true` or the context parameter `fasti.webapp.classic.enabled` of the
  web application brings them back with a warning.
- GraalJS 25 and upgraded runtime and test libraries (Jackson, Guava and others).
- Documentation presents Fasti BPM as an independent project built on Camunda 7 CE.
- Camunda-internal CI, workflows and issue templates removed.

### Deprecated

- The classic web applications Cockpit, Tasklist, Admin and Welcome. They are built on
  AngularJS 1.8.2 and Bootstrap 3, both end of life, and will be removed in a later release;
  use the new web UI.

### Fixed

- Query, job, call activity and database defects of the engine.
- GraalJS contexts are closed; script variable handling.
- Open redirect in `AppendTrailingSlashFilter`.
- A comment created through a task can no longer be attached to an unrelated process
  instance.

### Security

- **BREAKING:** secure-by-default container: REST authentication and authorization are on in
  every mode; the open mode needs the explicit opt-in `FASTI_INSECURE_DEMO=true`.
- **BREAKING:** the REST API of the Tomcat and WildFly distributions requires HTTP Basic
  authentication (`camunda-auth` filter of the `engine-rest` WAR); REST clients must send
  credentials. OpenID Connect bearer tokens work alongside. Removing the filter switches
  authentication off (not recommended).
- **BREAKING:** the Tomcat and WildFly distributions no longer deploy the invoice example, which
  created the administrator `demo/demo` and the users `john`, `mary` and `peter`. It ships as
  `examples/camunda-invoice.war`; the first administrator is created on the setup page of the
  web UI.
- The Tomcat distribution leaves out the documentation, manager and host-manager applications
  and the default page of Tomcat; `/` leads to the Fasti welcome page, and error pages written
  by Tomcat itself no longer show the exception or the server version.
- **BREAKING:** expressions in queries are restricted to an allow-list.
- Task attachments and comments are authorized.
- Initial setup and Cockpit/Admin permission checks hardened.
- Web app headers, cookies, CSP and path authorization hardened.
- Vulnerable libraries upgraded.
- CVE-2026-114938 ("Java4Shell"): the amd64 image ships the fixed Temurin JRE 21.0.13.1+2 and
  turns off the Base64 intrinsic; the engine warns at startup on an exposed JVM.
