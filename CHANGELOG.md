# Changelog

All notable changes to Fasti BPM are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). Fasti BPM is built on Camunda 7 CE 7.24.0; the
entries below describe the differences from that release.

Mọi thay đổi đáng chú ý của Fasti BPM được ghi ở đây. Định dạng theo
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), phiên bản theo
[Semantic Versioning](https://semver.org/). Fasti BPM được xây dựng trên Camunda 7 CE 7.24.0;
các mục dưới đây là khác biệt so với bản đó.

## [Unreleased]

### Added

- REST: `GET` and `POST /task/workload` count the open tasks the caller may read in one request:
  in total, per candidate group and per assignee asked for (at most 200 of each), with the
  unclaimed and overdue ones and the oldest. The grouped statements wrap the task query of the
  engine, so its authorization check (revoked permissions included), tenant check and CMMN
  handling apply unchanged. Counts are reported under the ids asked for, also on databases whose
  collation ignores case (SQL Server, MySQL, MariaDB). `tenantIdIn` takes at most 200 ids, every
  id at most 255 characters, and empty ids are refused (`400`); an engine whose MyBatis mappings
  lack the workload statements (an application's own `mappings.xml`) answers `501`. Java:
  `dev.fasti.engine.workload.TaskWorkloads.query(engine)`. Documented in the OpenAPI document and
  the [REST guide](docs/api/README.md).
- [Runtime tuning](docs/tuning-runtime.md): job executor and pool sizing, the audit trail under
  load, the engine gauges, and the trade-off of the history removal time strategy `start`.
- Optional performance indexes ([docs](docs/performance.md)): `sql/optional/<database>_fasti_perf_indexes_create.sql`
  and `..._drop.sql` for PostgreSQL, MySQL/MariaDB, Oracle, SQL Server, Db2 and H2 (Run:
  `configuration/sql/optional`, image: `/fasti/configuration/sql/optional`). They add four
  non-unique indexes for job acquisition, overdue tasks, tasks of one assignee by due date and the
  absence lookup, plus three commented ones for history and incident pages. Measured on
  PostgreSQL 16 with 1,000,000 tasks and jobs: an idle job acquisition reads 6 instead of 40,009
  pages, the overdue task list 36 instead of 27,417, the absence lookup 8 instead of 1,359, and no
  throughput regression of the engine was detected; MariaDB 11.4 (half the data) shows the same
  plans, from full table scans to index ranges. Candidates that did not pay off (a user-leading
  authorization index made some task queries with authorization 86 to 128 times slower) are
  listed with their numbers in the docs. The schema of Camunda 7.24 stays unchanged: the scripts
  are run by an administrator, never by the engine, are not recorded in `ACT_GE_SCHEMA_LOG`, and
  are safe next to 7.24 nodes; the drop script removes them. The Oracle, SQL Server and Db2
  scripts are reviewed but not yet verified on those databases. The scripts set a lock timeout at
  the top (MySQL/MariaDB, Oracle, SQL Server, Db2, H2). On databases without partial indexes the
  job index is `(SUSPENSION_STATE_, DUEDATE_, RETRIES_)`, so an idle acquisition does not read
  every failed and suspended job. The commented second group can be run again.
- Performance hints at engine start: one warning listing the optional indexes the database does
  not have (matched by columns, so an administrator's own index counts), and warnings
  for history without cleanup window, end-time based cleanup, a connection pool smaller than the
  job executor needs, `ensureJobDueDateNotNull` with jobs that have no due date, and acquisition by
  due date that allows jobs without one. They only read and never fail the start;
  `fasti.performance.index-check` / `config-check` (`warn` or `off`; image
  `FASTI_PERFORMANCE_INDEX_CHECK` / `FASTI_PERFORMANCE_CONFIG_CHECK`; Tomcat and WildFly plugin
  `PerformanceAdvisorPlugin`). An unknown `fasti.performance.*` value logs a warning instead of
  failing the start. Catalog reads have a 10 s timeout and no unbounded fallback. On PostgreSQL
  only B-tree indexes count, and the job index must be `NULLS FIRST` when jobs are acquired by due
  date. FASTI-PERF-006 appears only on PostgreSQL, Oracle and Db2 nodes that run jobs. On H2 all
  findings are logged at INFO. The messages point to
  https://github.com/fasti-bpm/fasti/blob/main/docs/performance.md and to the Maven artifact of
  the scripts, `dev.fasti.platform.distro:fasti-sql-scripts`. The `bpm-platform.xml` (Tomcat)
  and `standalone*.xml` (WildFly) of this release enable the plugin, which Fasti BPM 1.1 does not
  have: to roll back to 1.1 binaries with these files, remove the `PerformanceAdvisorPlugin`
  element first.

### Changed

- Web UI: far fewer requests while pages are open ([measurements](docs/performance-web-ui.md)).
  The workload board reads its totals and rows with one `POST /task/workload` instead of three
  or four task queries per group and person (344 API requests at load and about 510 per minute
  before, 11 and 2 now; a server without the endpoint is asked per row as before). The
  dashboard drops two unused statistics counts, takes the task totals from one request and
  reads slow-moving numbers every five minutes (52 to 18 requests per minute). The task list
  reads filter counts only while they are shown, every two minutes and once after a burst of
  task actions (claim 10 to 8 requests, unclaim 7 to 4), and a business key once instead of
  with every refresh. Pages refresh every 60 s after two minutes without input and every five
  minutes after ten. The activity tree of an instance keeps the parts that did not change and
  is read less often when large or unchanged.
- Web UI: the page starts with the defaults when `config.json` has not answered after three
  seconds and applies it when it arrives (CSRF cookie name, default language, `idlePacing`); a
  failed request is retried and requests that change data wait for it (up to 20 s). A stored
  language loads beside it. React and the router are a chunk of their own
  (entry chunk 397 kB to 92 kB), icons used by several pages one chunk (54 files under 1.5 kB
  to 13), and the case timeline of the task detail is loaded when its tab is opened.
- Web UI: the workload board and the dashboard fall back to counting row by row whenever the
  server answers `404`, `405` or `501` for `/task/workload` (older nodes during a rolling upgrade
  or a rollback), not only before the first answer, and show an error instead of empty or
  endlessly loading tiles when their numbers fail. With new-task notices on, the task list is
  read at least once a minute (also in a hidden tab when notifications are allowed). Coming back
  after idle refetches only lists that refresh by themselves; input inside an embedded form
  counts as presence; `idlePacing: false` in `config.json` or `?idlePacing=false` keeps the full
  refresh rate (wall displays). A part of an older build that fails to load after an upgrade
  offers a reload in its place and keeps an open task form. Hashed web UI assets are served with
  `Cache-Control: public, max-age=31536000, immutable`.
- Audit trail: a command whose transaction ends with it (every request and job of Run and the
  container image, every command of a standalone engine) writes its records after the statements
  of the engine, on chains of its own (`<node>/end-<slot>`), so the head row of a chain is locked
  for the audit statements and the commit only and no longer while the engine sends its inserts,
  updates and deletes ([runtime tuning](docs/tuning-runtime.md#audit-trail)). A command that
  joins a transaction that goes on after it writes first, as before, on `<node>/<slot>`, the
  chains 1.0 and 1.1 write to. A node of 1.1 and one of this version that share a node id (during
  an upgrade or a rollback) therefore never mix the two write orders on one chain. The heads of
  the first band are created at startup; any other missing head is created in a transaction of
  its own. The starter asks the transaction manager of the engine whether a command joins a
  transaction (resource bound, or JTA status), and releases what a transaction held even when its
  commit or rollback fails. The records, their hashes and the transaction they belong to are
  unchanged.
- Audit trail purge: each chain is purged in batches of `fasti.audit.purge-batch-size` records
  (default 10,000), each in its own transaction with its own purge record, which locks the head of
  the chain before it reads what to purge. A purge holds one head at a time and can no longer
  wait for a transaction that waits for it. A chain that cannot be purged no longer stops the
  others; the result lists it in `failedChains` (REST and `AuditPurgeResult`).
- Audit trail pages are ordered by time, chain and position in one direction (newest first:
  all descending), served by the new index `FASTI_IDX_AUDIT_ORDER (TIME_, CHAIN_ID_, SEQ_)`, which
  replaces `FASTI_IDX_AUDIT_TIME`. Records of the same millisecond in different chains change
  their relative order when the newest are listed first. `FASTI_IDX_AUDIT_ACTOR_ORDER (ACTOR_,
  TIME_, CHAIN_ID_, SEQ_)` and `FASTI_IDX_AUDIT_TYPE_ORDER (TYPE_, ACTION_, TIME_, CHAIN_ID_,
  SEQ_)` replace `FASTI_IDX_AUDIT_ACTOR` and `FASTI_IDX_AUDIT_TYPE`. Pages filtered by actor (also
  `system`), or by category and action, read one page even for an actor whose records are all
  old (MariaDB, 500k records: 375,000 rows / 1.6 s down to 25 rows / 0.5 ms). **Existing audit
  tables**: `sql/upgrade/<db>_fasti_audit_after_1.1.sql`, now in every distribution, online where
  the database allows and re-runnable; without it pages are read as before. Drop upgraded tables
  with this version's drop script (the 1.1 drop scripts fail on them).
- `GET /audit/event/count` takes `maxCount` (1 to 1,000,000), now in the OpenAPI document: the
  limit caps the records counted, not the rows the database examines, and a result equal to
  `maxCount` means "that many or more". `AuditEventQuery.count(long limit)` does the same in Java.
  The export counts at most 100,001 records before refusing.
- The gauges of the engine state (`fasti.engine.*.active`, `.open`, `.executable`, `.failed`) are
  read on a background thread. A Prometheus scrape returns the values read last and never waits
  for the six count queries; a value is up to one refresh interval plus one scrape interval old,
  and `NaN` until the first round has finished. While a round has been running for more than three
  refresh intervals (a count that waits for a lock or on a dead connection) the gauges report
  `NaN` instead of the last values. The gauges stop when the application context stops, before
  the engine is closed, and one thread serves the rounds while the gauges are scraped.
- Fasti BPM Run and the container image: the job executor acquires 10 jobs per cycle (3 before)
  into a queue of 10 (3) for 10 threads (3 core, 10 maximum), and the connection pool holds up to
  20 connections (10), so that job threads cannot use up every connection; an idle node keeps 10
  open (`minimum-idle`, as before). The core threads follow `max-pool-size`, so lowering only the
  maximum (`CAMUNDA_BPM_JOB_EXECUTION_MAX_POOL_SIZE`) lowers both. One node may now open 20
  database connections under load: the maxima of all nodes (counted at the autoscaling maximum
  plus the pods of a rolling update), the other clients and the reserve of the database must stay
  below its connection limit; check it before upgrading many replicas, or set the former values
  ([runtime tuning](docs/tuning-runtime.md)). The exclusive jobs of one process instance that
  one acquisition takes run one after the other under one lock of 5 minutes, as before, but a
  batch can now hold up to 10 jobs instead of 3: keep 10 × the slowest such job below
  `lock-time-in-millis`, or raise it, or a job may run a second time on another node. The new
  values are in the shipped configuration files: a Run installation that keeps its
  `configuration/default.yml` or `production.yml` from 1.1 keeps the former values until the
  `camunda.bpm.job-execution` block and the `hikari` lines are copied over. Applications that
  embed the starter keep their defaults.
- Statement parameters are bound through a plain statement wrapper instead of a dynamic proxy
  (no reflective call per parameter; while MyBatis logs a mapper's statements at `DEBUG` they are
  bound through the proxy as before).
- Absence substitution: with an index led by `USER_ID_` on `ACT_ID_INFO` (the optional
  `FASTI_IDX_ID_INFO_USER`) absences are read user by user, each lookup an index seek. Without
  one, a command that looks up several users still reads all absences at once: the second lookup
  reads them with one statement, which answers every later lookup of the command (a chain of
  three substitutes sends two statements instead of four). The index is detected at engine start.
  Reading all absences no longer fills the entity cache.

### Fixed

- Spring Boot starter: a `camunda.bpm.job-execution.core-pool-size` larger than `max-pool-size`
  kept the application from starting (`IllegalArgumentException` without a message while the
  thread pool of the job executor was created; also in Camunda 7.24). The starter now uses
  `max-pool-size` core threads and logs a warning.

## [1.1.0] - 2026-11-21

### Added

- Immutable audit trail ([docs](docs/audit-trail.md)): with `fasti.audit.enabled=true` the engine
  records, next to its history, process instances, user tasks with who created, assigned,
  completed or deleted them, variables (as SHA-256 or HMAC by default, in clear on request, never
  for masked names such as `*password*`), deployments with the hash of every resource, incidents,
  decisions and every entry of the user operation log (identity and authorization changes
  included), with the authenticated user, the engine clock, the engine and the tenant, in the
  transaction of what it records. Each record carries SHA-256(previous hash, canonical record);
  every node appends to a few chains of its own, locking only the head row of a chain, so nodes
  do not wait for one global lock; nested transactions use slots of their own and the wait for
  a chain is limited (`fasti.audit.lock-timeout-seconds`). Checkpoints and purge records can be
  signed with an HMAC key that never reaches the database (`FASTI_AUDIT_HMAC_KEY` or a key
  file). `POST /audit/verify` (one at a time per engine, `409` otherwise) and the
  **Cockpit → Audit trail** page recompute the chains and name the first changed, removed or
  inserted record. Verification gives two verdicts: `intact` (nothing that can be checked
  failed) and `authenticated` (every checkpoint and the purge record of a removed start were
  checked with a key of the engine). With a key, a checkpoint without valid signature, a missing
  checkpoint and a removed start without signed purge record are breaks; earlier keys stay
  usable through `fasti.audit.retired-hmac-keys` (checked) or `fasti.audit.retired-key-ids`
  (accepted unchecked, so not authenticated). Without a key a purged start is reported as not
  attested. The page and the new "Audit trail" tab of an instance history filter by instance,
  business key, user, category and time (pages are cut by the database) and export CSV or JSON
  with `POST /audit/event/export`, streamed from one cursor (the export is recorded; a `GET`
  exports and records nothing). History cleanup does not touch the records; an explicit,
  export-confirmed `POST /audit/purge` removes records older than `fasti.audit.retention-days`
  and leaves a purge record that keeps the chain verifiable. Reading needs an administrators
  group or `READ` on `SYSTEM`, purging `DELETE` on `SYSTEM`; readers who are not administrators
  see the records of their tenants and those without tenant. Database error messages are logged,
  not returned. Stored in the optional tables `FASTI_AUDIT_EVENT` and
  `FASTI_AUDIT_HEAD` (scripts for H2, PostgreSQL, MySQL/MariaDB, Oracle, SQL Server and DB2,
  `sql/create/<database>_fasti_audit.sql`, below `configuration/` in Run and the container
  image); the Camunda 7.24 schema is unchanged. Off by default
  everywhere (plugin `dev.fasti.engine.audit.AuditTrailPlugin`, `FASTI_AUDIT_ENABLED` in the
  container image).
- Four-eyes control for approvals ([docs](docs/four-eyes.md)): a BPMN user task with the
  extension property `fasti:fourEyes` (ids of earlier user tasks of the same process, comma
  separated; the task itself for "a different person each time") cannot be completed, have its
  form submitted, or be ended with a BPMN error or escalation by a person who completed one of
  those steps in the same process instance, as the authenticated user or as assignee. The
  engine refuses with `FourEyesViolationException` (`403` over REST, type
  `FourEyesViolationException`); claiming is not refused and there is no administrator
  override. Who completed a step is recorded in the process instance variable
  `fasti_completedBy_<step>` and also read from the historic task instances and the operation
  log, so it works at every history level, counts steps completed on nodes without the plugin
  (where history is kept, so not at level `none`), and keeps the Camunda 7.24 schema. On an
  engine with the plugin these variables are reserved in process instances: only the control
  writes them, every other write or removal is refused with `403`
  (`ReservedVariableException`), through any API and from code of the model, and copies of all
  variables (call activity `variables="all"`, restart) leave them out; see the limits in
  [docs/four-eyes.md](docs/four-eyes.md#limits). Wrong rules fail the deployment.
  `GET /task/{id}/four-eyes` tells the current user whether they may complete a task (`404`
  for a task the user may not read, as for a missing one); the tasklist says so and disables
  the form, and the modeler has a four-eyes field on user tasks. On in Run, the container image
  (`FASTI_FOUR_EYES_ENABLED=false` turns it off) and the Tomcat and WildFly bundles; off in the
  Spring Boot starter (`fasti.four-eyes.enabled`, plugin
  `dev.fasti.engine.foureyes.FourEyesPlugin`).
- Substitutes during absence ([docs](docs/absence.md)): a user records an absence (from, until,
  substitute, note) in the preferences of the new web UI, or an administrator does so on the
  Absence tab of a user. While it is active, BPMN user tasks and CMMN human tasks created for
  or assigned to the user go to the substitute (following absent substitutes up to
  `fasti.absence.max-hops`, never in a circle), and the substitute becomes a candidate user next
  to an absent candidate user. A substituted assignee leaves a task comment (not at history
  level `none`) and the local task variables `fasti_absentAssignee` and
  `fasti_absenceSubstitute`; a substitute added as candidate user leaves the comment only. Open tasks from before the
  absence are handed over on request ("Hand over my open tasks now"). The tasklist says when
  you are away or stand in for someone and marks tasks held for an absent user. REST:
  `GET`/`PUT`/`DELETE /user/{id}/absence`, `POST /user/{id}/absence/handover`,
  `GET /user/{id}/absence/substituting`; a user manages their own absence, others need `READ`
  or `UPDATE` on the user. Setting an absence also needs `READ` on the substitute; a
  substitute that does not exist, may not be seen or leads back to the user gets one and the
  same `400`, and `effectiveSubstituteId` follows absent substitutes only as far as the caller
  has `READ` on them. The handover needs the permission to assign each task (`TASK_ASSIGN` or
  `UPDATE`), as the task API does: tasks without it stay and are counted in `notAllowed`,
  tasks of a tenant the caller does not belong to are left alone, and administrators may hand
  over every task. Ended absences are removed the next time an absence is set or ended.
  Absences are stored as user info (`ACT_ID_INFO`), so the database
  schema stays that of Camunda 7.24, also with LDAP. On in the default configuration of Run;
  off in the Spring Boot starter, the container image (`FASTI_ABSENCE_ENABLED=true`),
  `production.yml` and the Tomcat and WildFly bundles (`fasti.absence.enabled`, plugin
  `dev.fasti.engine.absence.AbsenceSubstitutionPlugin`).
- Helm chart (`distro/kubernetes/helm/fasti-bpm`) and plain Kubernetes manifests with Kustomize
  (`distro/kubernetes/manifests`) for the container image against an external database:
  production mode on by default, credentials only from existing Secrets (mounted as files),
  non-root pod with a read-only root file system that passes the `restricted` Pod Security
  Standard, startup/liveness/readiness probes on the actuator health endpoints, graceful
  shutdown, pod anti-affinity, topology spread, PodDisruptionBudget, and optional Ingress with
  TLS, HorizontalPodAutoscaler, NetworkPolicy, ServiceMonitor and OpenID Connect settings. A
  values schema rejects unknown keys and values that cannot work (for example an extra port
  named `http` or `management`, or a ServiceMonitor port that the Service does not have). In
  the chart, `management.port` gives the actuator (health endpoints, Prometheus metrics) a
  port of its own (`FASTI_MANAGEMENT_PORT`): the container port, the probes, the Service,
  `helm test` and the NetworkPolicy follow it, and `metrics.serviceMonitor.port: management`
  scrapes it; setting `FASTI_MANAGEMENT_PORT` through `extraEnv` is refused, because the
  probes would keep calling port 8080. Guide: `docs/install-kubernetes.md`. CI checks the
  chart and the manifests with `helm lint` and kubeconform against Kubernetes 1.29–1.33.
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
    `FASTI_MANAGEMENT_PORT`). A token must have at least 16 characters and the placeholder
    `change-me` is refused. While the switch is off, a configuration that exposes every
    actuator endpoint does not serve the metrics; when the Spring Boot settings behind the
    switch (endpoint access, Prometheus registry, web exposure) are set by hand, the same
    start-up check and token rules apply. The `HEALTHCHECK` of the image follows the
    management port (`FASTI_PORT` when it is not a port number).
  - Engine metrics for Micrometer, also in the Spring Boot starter (`fasti.metrics.engine.*`):
    counters of started and ended root process instances, activity instances, executed,
    failed, acquired and rejected jobs and evaluated decisions, and gauges of active process
    instances, open user tasks, incidents, due and failed jobs and open external tasks (read
    from the database at most every 30 s). Tags are limited to the engine name and a fixed
    result or state.
  - OpenTelemetry tracing with `fasti.tracing.enabled=true`: spans for HTTP requests, the
    engine commands they run and each job execution, exported over OTLP/HTTP to
    `management.opentelemetry.tracing.export.otlp.endpoint` or `OTEL_EXPORTER_OTLP_ENDPOINT`.
    Nothing is sent while no endpoint is set. Log lines written inside a span carry the trace
    and span ids: `traceId` and `spanId` in JSON lines, `[<trace id>-<span id>]` in text lines
    (the console pattern of the container image now includes them; without a span it adds
    nothing).
- Publishing to Maven Central prepared: the root POM profile `central` builds the published
  libraries, starters, BOMs and parent POMs (QA modules, distributions and build helpers are
  left out), attaches sources and javadoc jars, signs with GPG and uploads through the Central
  Portal (`central-publishing-maven-plugin`, deployment left for a manual "Publish"). The manual
  workflow `publish-maven` runs it for a version tag: one job builds the bundle without any
  secret, a second job in the GitHub environment `maven-central` (required reviewers; secrets
  `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `MAVEN_GPG_PRIVATE_KEY` and
  `MAVEN_GPG_PASSPHRASE`) signs it and uploads it, and a dry run stops after the first job.
  Guide: `docs/publishing-maven-central.md`. No version has been published yet.
- MCP server for AI assistants ([docs](docs/mcp.md)): with `fasti.mcp.enabled=true` (Run:
  `--mcp`; container image: `FASTI_MCP_ENABLED=true`; Spring Boot: the new starter
  `fasti-bpm-spring-boot-starter-mcp`) the engine serves a Model Context Protocol server at
  `/mcp` (Streamable HTTP, stateless, MCP 2025-11-25), built on the official MCP Java SDK 2.1.0.
  Every request must be authenticated, with HTTP basic against the engine users or, with
  `fasti.security.oidc.rest.bearer-enabled=true`, with the bearer tokens of the REST API (plus
  RFC 9728 protected resource metadata); every tool runs as that user through the engine's
  authorization checks, four-eyes rules and audit trail. Read tools: engine info, process
  definitions with BPMN XML, running and historic process instances with masked and cut
  variables, user tasks, incidents with stack traces, DMN decisions (list, evaluate); BPMN and
  DMN XML also as resources. Tools that start processes, claim, unclaim and complete tasks and
  retry failed jobs exist only with `fasti.mcp.write-tools.enabled=true`; no tool deploys
  models or evaluates expressions or scripts of the caller. Requests with a foreign `Origin`
  are refused (DNS rebinding), results are paged and limited. Off by default everywhere.
- Cases (CMMN) in the new web UI ([docs](docs/web-ui.md#cases-cmmn)): **Cockpit → Cases (CMMN)**
  lists the case definitions with their versions, tenant and open instances; a version shows its
  diagram with the number of active or enabled plan items and its open instances, and creates new
  ones; a case instance shows the state of every plan item on the diagram and in a list with the
  manual transitions the engine allows (start, complete, disable, enable again, terminate), its
  tasks, variables (editable) and the history of its plan items, and completes, terminates or
  closes the case. Irreversible actions ask first, refusals of the engine name their reason, and
  closed instances are shown read only from the history. **Cockpit → Case history** lists open
  and closed case instances. In the Tasklist a task of a case is named by its case and business
  key, links to its case instance and draws the case diagram; embedded forms of such tasks may read
  their own case instance. The search finds case instances by ID or business key, and deployments
  draw `.cmmn` resources. The diagrams are drawn read only by Fasti from the CMMNDI of the model
  (no cmmn-js), loaded with the case pages only. The classic Cockpit only counted case
  definitions and drew `.cmmn` resources of deployments; this is a step towards retiring the
  AngularJS applications.

### Changed

- POMs: `fasti-root` no longer inherits `org.camunda:camunda-bpm-release-parent`; the parts the
  build used are part of `fasti-root` now. The POMs no longer declare Camunda's Artifactory
  (`artifacts.camunda.com`) as repository or deployment target, and carry the metadata of
  Fasti BPM (URL https://fasti.dev, organisation, SCM and issue tracker on GitHub, licence
  Apache-2.0) instead of Camunda's. The `javadocs` profile no longer adds Camunda's Google Tag Manager script to the
  generated pages. Plugin updates: maven-source-plugin 3.3.1, maven-javadoc-plugin 3.12.0,
  maven-gpg-plugin 3.2.8, maven-deploy-plugin 3.1.4. The integration tests of the Java external
  task client need the Tomcat distribution only when tests run (profile `tomcat-runtime`).
- POMs: the JBoss repository, needed by the build for a few test and provided dependencies,
  is declared in a build-only profile of `fasti-parent`. An application that depends on a
  Fasti artifact no longer gets `repository.jboss.org` added to its build (75 of the 83
  published POMs inherited it, with snapshots enabled).
- New web UI: one consistent look across the Tasklist, monitoring, modeler, administration,
  preferences and welcome pages. Page headers, cards, tables, tabs, confirmations and options
  are styled with the application instead of with the monitoring section. Every size, weight,
  layer and shadow comes from the design tokens. The tabs of a task and of the other pages,
  and the segmented choices, share one style with a visible focus. Every dialog is left with
  the same action, placed before the main one; pages that failed to load offer to try again;
  the operation log formats times like the rest.
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
  cleanup and removal time of comments on PostgreSQL. The engine therefore keeps the parameter
  metadata from MyBatis (the lookup is answered with one shared exception without stack
  trace).
- Release workflow: a tag must be a semantic version and equal the version of the POMs, or
  nothing is built. The image tag `latest` moves only when the tag is the highest release of
  the repository, so a maintenance release of an older line no longer takes it back.
- Build: the Maven wrapper verifies the SHA-256 of the Maven distribution it downloads and of
  its own jar. The HTTP retry settings use the keys of Maven Resolver 2; the previous keys
  were ignored by the Maven of the wrapper.

### Fixed

- New web UI: a page no longer looks different depending on the page opened before it (the
  preferences showed bare radio buttons when opened directly). The subtle text colour of the
  light theme and the frames of text fields now meet WCAG 2.2 AA contrast (4.5:1 and 3:1).
  The filter fields of the operation log are kept apart, and a text filter applied with Enter
  keeps the keyboard focus. Scrolling started by the application honours reduced motion.
- New web UI, Tasklist: a task claimed from its detail page could be completed before the server
  had confirmed the claim; the completion then failed with an optimistic-locking error. Complete
  and the form submit now wait until a pending claim, unclaim or assignment of the task is
  settled, and a refused change is reverted at once.
- New web UI: confirming a dialog opened from inside a form also submitted that form. Deleting a
  saved task filter therefore also tried to save it ("The filter could not be saved"), and
  handing over open tasks from the absence settings also saved the absence.
- REST API: a servlet context started again in the same class loader (a redeployment of the REST
  application) got no running fetch-and-lock handler, so long polling requests of external task
  workers were never answered. A new handler is started.
- The container image ignores a `FASTI_PORT` that is not a port number, with a warning, in the
  entrypoint and the `HEALTHCHECK`: Kubernetes sets `FASTI_PORT=tcp://<ip>:<port>` for a Service
  named `fasti`, and the server then failed to start.
- MySQL and MariaDB: time filters of queries (for example `createdAfter` of historic variable
  instances, `startedAfter`, `executedActivityAfter`, `executedJobAfter`, a task's `dueDate`, also
  in saved filters) missed rows stored in the same second as the given time when the engine was
  connected through MariaDB Connector/J (the driver of the container image) or through MySQL
  Connector/J without `sendFractionalSeconds=false`. The Camunda 7.24 schema stores these times
  to the whole second, but the driver sent the milliseconds of the parameter. On these databases
  the engine now binds every date truncated to whole seconds, stored values and query parameters
  alike, as `sendFractionalSeconds=false` does, so the result no longer depends on the driver.
  Comparisons work on whole seconds: `>=` and `=` now find a row of the same second, `>` and
  `<=` are unchanged, and `<` (`dueBefore`, a task's `createdBefore`, expired locks) no longer
  matches a row of the same second. MySQL no longer rounds a stored time up to the next second.
  Other databases are unchanged.
- MySQL and MariaDB: the expiration of a lock (external task `fetchAndLock` and `extendLock`,
  job acquisition) is rounded up to the next whole second before it is stored. Cut off to the second, an external task locked for less than a second could be
  fetched by another worker right away (500 ms from 12:00:00.100 ended at 12:00:00). A lock now
  lasts at least as long as asked for and less than a second longer; the `lockExpirationTime`
  a worker gets is the stored one. Migration note: on these databases lock expiration times are
  whole seconds, up to 999 ms later than `now + lockDuration`. Other databases are unchanged.

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
