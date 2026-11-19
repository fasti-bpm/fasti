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
