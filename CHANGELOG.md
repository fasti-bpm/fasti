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

Planned for release as 1.1.0, because it adds features. None of these changes is part of the
1.0.1 release or of the published 1.0.1 image.

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
- Publishing to Maven Central prepared: the root POM profile `central` builds the published
  libraries, starters, BOMs and parent POMs (QA modules, distributions and build helpers are
  left out), attaches sources and javadoc jars, signs with GPG and uploads through the Central
  Portal (`central-publishing-maven-plugin`, deployment left for a manual "Publish"). The manual
  workflow `publish-maven` runs it for a version tag once the secrets `MAVEN_CENTRAL_USERNAME`,
  `MAVEN_CENTRAL_PASSWORD`, `MAVEN_GPG_PRIVATE_KEY` and `MAVEN_GPG_PASSPHRASE` exist (it skips
  with a notice otherwise; a dry run needs none). Guide: `docs/publishing-maven-central.md`. No
  version has been published yet.

### Changed

- POMs: `fasti-root` no longer inherits `org.camunda:camunda-bpm-release-parent`; the parts the
  build used are part of `fasti-root` now. The POMs no longer declare Camunda's Artifactory
  (`artifacts.camunda.com`) as repository or deployment target, and carry the metadata of
  Fasti BPM (URL https://fasti.dev, organisation, SCM and issue tracker on GitHub, licence
  Apache-2.0) instead of Camunda's. The `javadocs` profile no longer adds Camunda's Google Tag Manager script to the
  generated pages. Plugin updates: maven-source-plugin 3.3.1, maven-javadoc-plugin 3.12.0,
  maven-gpg-plugin 3.2.8, maven-deploy-plugin 3.1.4. The integration tests of the Java external
  task client need the Tomcat distribution only when tests run (profile `tomcat-runtime`).
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
- MyBatis 3.5.19 → 3.6.0. Since 3.6 a `<bind>` inside `<foreach>`, `<where>`, `<trim>` or
  `<set>` is visible only inside that element (and inside one iteration of a `<foreach>`), and
  a property path through `null` (`a.b` with `a == null`) fails again, as before 3.5.15. The
  engine mappings that built the sort order, the join flags of `or` queries and the names of
  the variable joins of the historic process instance query over several iterations now compute
  them in one expression. Plugins with their own MyBatis mappings (for example Cockpit plugins)
  must check for both patterns.
- Statement parameters without a `jdbcType` get their type handler from the class of the value,
  as with MyBatis 3.5. MyBatis 3.6 instead reads the parameter metadata of every statement from
  the JDBC driver (with PostgreSQL an extra round trip per statement, also per row of a JDBC
  batch, and a failed lookup aborts the transaction). For parameters declared as `Object` it
  finds no type handler when the driver offers no metadata (MySQL Connector/J by default) and
  uses the handler of a given `jdbcType` regardless of the value, which would break the history
  cleanup and removal time of comments on PostgreSQL.

### Fixed

- The container image ignores a `FASTI_PORT` that is not a port number, with a warning, in the
  entrypoint and the `HEALTHCHECK`: Kubernetes sets `FASTI_PORT=tcp://<ip>:<port>` for a Service
  named `fasti`, and the server then failed to start.

### Security

- MyBatis 3.6.0 embeds OGNL 3.4.16 instead of 3.4.4, which closes CVE-2026-113730
  (GHSA-crwc-vw2r-qr6m, a bypass of the OGNL stricter-invocation block list) reported by
  scanners for every MyBatis 3.5 jar. The engine was not exposed in practice: MyBatis evaluates
  only the expressions of the mapping files, never user input, so the block list is no security
  boundary there.

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
- From this release on, the container image is signed with cosign 3 in the Sigstore bundle
  format (the 1.0.0 image carries a signature in the older cosign 2 format). Verify it with
  cosign 3, or with cosign 2.6 and `--new-bundle-format`.

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
  administration, in English and twelve more languages (13 in total), unit and end-to-end
  tests.
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

- Or queries of tasks did not combine candidate user and candidate groups correctly; they do
  now.
- Or queries of historic process instances ignored incident criteria, and or queries of process
  instances ignored `withIncident`; both are applied now.
- A task filter whose candidate group expression yields no group (for example
  `${currentUserGroups()}` for a user without groups) failed with "Candidate group list is
  empty"; it now matches no task, also inside an or query.
- Jobs that suspend or activate a process with a delay were created without the configured
  default number of retries; they get it now.
- Job declarations were linked to their job definitions only after the process definition had
  been added to the deployment cache, so a job created at that moment by another thread could
  lack its job definition and process definition reference; they are linked before now.
- A failed job without a process definition id (as created by former engine versions) could not
  be handled by the retry logic; the default retry strategy applies to it now.
- When the history cleanup job had been removed while it ran (for example because another engine
  changed the degree of parallelism), rescheduling it failed; it is skipped now.
- Process instances that call each other through call activities without a wait state were
  started until the memory was exhausted; the engine now stops after 1,000 nested instances
  within one command (`maxCallActivityNestingDepth`, 0 switches the limit off).
- Date form fields shared one date format between threads, which is not safe for concurrent
  use, and failed for values that already are dates (for example the result of an expression);
  each conversion uses its own format and accepts dates now.
- The id of the root process instance can be added to the logging context (MDC) with the
  `loggingContextRootProcessInstanceId` setting (off by default).
- MariaDB Connector/J was not recognised as a known database; it is now detected and uses the
  MySQL database type, as with MySQL Connector/J.
- Completing a task whose execution had been removed by a concurrent transaction failed with a
  `NullPointerException`; it is reported as an optimistic locking failure now.
- The `collectEntries` result mapping of a business rule task returned an unmodifiable list
  when the decision had no result; it returns a modifiable list now, as for a result with
  entries.
- Service task connectors did not release the connector response (for example the HTTP
  connection) after the output mapping; it is released exactly once now, and a failure to
  release it is logged without failing the activity.
- Unparsable deployment resources, variables in the prohibited Java serialization format and
  query parameters of the wrong type were answered with `500`; they are answered with `400`
  now.
- GraalJS: every script evaluation created a polyglot context that was never closed, which
  leaked memory in long-running engines, and values could escape a closed context. Scripts in
  the engine and in DMN now use one context per evaluation, close it and detach the returned
  values; a shared script engine factory no longer keeps a polyglot engine, and the contexts of
  a shared engine are created one after the other.
- GraalJS scripts did not store their variables when `autoStoreScriptVariables` was enabled;
  they do again.
- `AppendTrailingSlashFilter` (Spring Boot web applications) built its redirect target from the
  request URI, so a crafted path could redirect users to another host (open redirect); the
  target now always stays within the application.
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
- **BREAKING:** expressions in queries are restricted to an allow-list. Stored queries such as
  task filters may carry expressions, and the validation of 7.24 missed expressions in nested or
  queries and in or queries of other query types, so an authenticated user could have arbitrary
  expressions evaluated. Every or query is validated now, expressions are limited to criteria
  that support them, and only allow-listed functions, beans and properties are accepted (the
  REST API answers `400` otherwise). Filters that need more declare it in
  `allowedExpressionFunctionsInQueries`, `allowedExpressionBeansInQueries` and
  `allowedExpressionPropertiesInQueries`.
- Task attachments and comments are authorized: reading a single attachment or comment, adding
  comments and changing attachments did not check the authorizations of the related task or
  process instance. The engine and the REST API check read and write access now and answer
  `403` instead of `500` when it is denied.
- Initial setup and Cockpit/Admin permission checks hardened: the initial setup stayed open
  until a user existed in the `camunda-admin` group and could be raced by concurrent requests;
  it now closes once any administrator or any principal able to manage authorizations exists,
  creates at most one user and can be switched off. Cockpit plugin queries skipped permission
  checks too broadly and now skip only the disabled permissions; Admin requires permission to
  read the aggregated metrics; saving a user checks the permission before the password policy,
  and a policy violation is answered with `400` instead of `500`.
- Web app headers, cookies, CSP and path authorization hardened: activity names in the Cockpit
  variables tab are bound safely, the HTML preview of deployment resources is sanitized and
  task ids are encoded in Tasklist notifications; session tokens and API headers are no longer
  sent to other origins; the security filter denies secured paths that no rule allows and
  authorizes the path the container resolved; the CSP nonce is bound to its request and
  generated securely; security headers and cookie attributes are also set on rejected requests
  and on responses without a body, and SameSite is set on WildFly; the REST API warns at
  startup when it is not authenticated.
- Libraries with known vulnerabilities moved to fixed releases, among them Jackson 2.21.8,
  Guava 33.8.0, commons-lang3 3.20.0, Jython 2.7.4, Ant 1.10.18, FreeMarker 2.3.35, the
  PostgreSQL and SQL Server JDBC drivers, WildFly 37.0.1 and, in the classic web applications,
  DOMPurify, fast-xml-parser, lodash and moment.
- CVE-2026-114938 ("Java4Shell"): the amd64 image ships the fixed Temurin JRE 21.0.13.1+2 and
  turns off the Base64 intrinsic; the engine warns at startup on an exposed JVM.
