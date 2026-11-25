English | [Tiếng Việt](camunda-7-advisories.vi.md)

# Camunda 7 security advisories and Fasti BPM

*Updated: 2026-11-25, for Fasti BPM 1.3.1.*

Camunda 7 Community Edition ended with 7.24.0. Security fixes published by Camunda since then ship
only in Enterprise patch releases, so a plain 7.24.0 CE installation stays affected. Fasti BPM is
built on 7.24.0 CE and fixes these issues in its own code, written independently. This page lists
the advisories we know of that concern a 7.24 code base and where Fasti stands on each. If one is missing or wrong, please tell
us (see [SECURITY.md](../../.github/SECURITY.md)).

Sources: Camunda's [security notices](https://docs.camunda.org/security/notices/), NVD/OSV and
the advisories of other Camunda 7 forks.

## 2026 advisories (fixed by Camunda in Enterprise 7.24.x only)

| Advisory | Component, problem | Fasti BPM |
|---|---|---|
| CVE-2026-109432 | REST task queries: expressions in `orQueries` were not validated, so an authenticated user could have arbitrary expressions evaluated (code execution) | **Not affected since 1.0.0.** Every or query is validated, ad-hoc query expressions are off, and stored filters accept only an allow-list (`currentUser()`, `currentUserGroups()`, `now()`, `dateTime()` arithmetic). Tested again in the [1.3 security test](pentest-1.3.md). |
| Notice 157 | Expressions in stored filters and ad-hoc task queries (code execution, privilege escalation) | **Not affected since 1.0.0**, as above. |
| CVE-2026-77226 | Admin first-run setup: an administrator could be created without authentication | **Fixed in 1.0.0** (the setup closes as soon as any administrator exists, no race) and **hardened in 1.3.1**: the setup page also asks for a one-time token printed to the server log. The container image keeps the page off by default. |
| CVE-2026-109431 | REST task comments and attachments: missing authorization checks (read, delete) | **Fixed in 1.0.0**; tested again in 1.3. |
| CVE-2026-109433 | Cockpit: inverted check of disabled permissions | **Fixed in 1.0.0.** The classic Cockpit is also off by default in the container image and the Tomcat and WildFly bundles. |
| CVE-2026-77224 | External task client: XML variables parsed without protection against external entities (file read, SSRF on the worker) | **Fixed in 1.3.1.** The client refuses document type declarations and external entities. |
| CVE-2026-40973, -22016, -34282, -41841, -41842, -41843, -76183 (notices 160, 162, 163, 171) | No details published | Unknown mechanism; we follow the notices and will update this page. |
| CVE-2026-64607 | httpclient5 (connection leak) | Not affected: Fasti ships httpclient5 5.6.5. |
| GHSA-72hv-8253-57qq | jackson-core (number length) | Not affected: Fasti ships Jackson 2.21.8. |
| Tomcat, Spring | Third-party CVEs of the bundled server and framework | Covered by dependency updates; a Trivy 0.68.1 scan on 2026-11-25 of the 1.3.1 image and Tomcat bundle found no known critical or high issue, and one medium issue (OpenTelemetry, fixed in 1.3.1). |

## Earlier notices that still matter for a 7.24 code base

| Notice | Problem | Fasti BPM |
|---|---|---|
| 25 | Java object deserialization through Object variables | Java serialization is off. Spin JSON/XML type validation is on in the container image (every mode) and the Tomcat bundle since 1.3.1 (before: with `--production` only); embedded engines keep the engine default (off), set `deserializationTypeValidationEnabled=true`. |
| 34, 40, 55 | DOCTYPE / external entities in BPMN, DMN and Spin XML | Refused (tested in 1.0 and 1.3). |
| 35 | SQL statements in error messages | Error responses carry no SQL, stack traces or paths (tested in 1.3). |
| 48, 50, third-party JS notices | XSS in the AngularJS web applications, missing SameSite | The new web UI is React (names rendered as text, nonce-based CSP). The AngularJS applications are off by default in the container image and the Tomcat and WildFly bundles (embedded Spring Boot applications still serve them unless `fasti.webapp.classic.enabled=false`); session cookies are SameSite. |
| 60, 75, 77 | H2 console in the distributions | No H2 console is deployed or served (the H2 driver is shipped for the evaluation database). |
| 85 | Deleted users stay signed in | A web session re-reads the user's authorizations after at most 5 minutes; a successful Basic login is remembered for at most 30 s per node (`FASTI_REST_AUTH_CACHE_TTL=0` turns it off). |
| 99, 83, 57 | Admin password, hashed password or LDAP settings in the logs | The image passes the admin password through the environment, not the command line, and logs no passwords (tested in 1.3). |

Not affiliated with Camunda Services GmbH; Camunda is a trademark of Camunda Services GmbH.
