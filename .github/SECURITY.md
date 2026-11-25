English | [Tiếng Việt](SECURITY.vi.md)

# Security Policy

## Reporting a vulnerability

Please do **not** report security issues in public issues, pull requests or discussions.

Use GitHub's private vulnerability reporting for this repository: open the **Security** tab
and choose **Report a vulnerability**. Include the affected version, the configuration that
is needed, and steps to reproduce.

If private vulnerability reporting is unavailable, write to <contact@fasti.dev> with the subject
"Security", and do not include exploit details in the first message.

What you can expect:

| Step | Target |
|---|---|
| Acknowledgement | within 3 working days |
| First assessment (affected versions, severity) | within 7 working days |
| Fix of a critical or high issue | a patch release as soon as possible, aiming at 14 days |
| Fix of a medium or low issue | the next regular release |
| Public advisory | when the fix is released; we credit you unless you prefer not |

Please give us reasonable time to publish a fix before disclosing details.

## How we publish fixes and incidents

- Critical and high issues get a patch release of their own; released versions are never changed
  afterwards. The [CHANGELOG](../CHANGELOG.md) lists every security fix under *Security* with what
  was wrong and what changed.
- An advisory names the affected versions and configurations, the root cause, the fixed version
  and what users must do (for example rotate a secret). We do not call an issue resolved before
  that is published.
- Releases are verifiable: container images are signed with cosign and carry an SBOM
  (`fasti-bpm.spdx.json` on each GitHub release); the Tomcat bundle has a SHA-256 checksum next to
  it. Verify an image with
  `cosign verify fastidev/fasti-bpm:<version> --certificate-identity-regexp '^https://github.com/fasti-bpm/fasti/' --certificate-oidc-issuer https://token.actions.githubusercontent.com`.
- Fasti sends no telemetry and has no update check. It opens connections only to the database and
  to what you configure (identity provider, LDAP, tracing exporter) or what your process models
  call (connectors, scripts).
- Our own security tests and their results are public: [Security test of 1.3](../docs/security/pentest-1.3.md);
  where Fasti stands on each Camunda 7 advisory: [Camunda 7 security advisories](../docs/security/camunda-7-advisories.md);
  personal data and retention: [Personal data](../docs/security/personal-data.md).

## Scope

This policy covers the code in this repository. It is a fork of Camunda 7 Community Edition
7.24.0. If an issue also affects the upstream code base or other projects derived from it,
we coordinate disclosure with the maintainers of those projects where possible.

This project is not affiliated with Camunda Services GmbH. Do not report issues of this
fork to Camunda.

<a id="support-policy"></a>

## Support policy

> This policy is maintained by the Fasti BPM project team and may be updated; the version in this
> repository is the one in force. It is also published on <https://fasti.dev/security#policy>.

- **Supported line:** the latest minor release of the `1.x` line receives bug fixes and security
  patches (as patch releases, for example `1.3.1`), **at least until 31 December 2027**.
- **If the project stops:** Fasti keeps the Camunda 7.24 database schema, so a Fasti database
  stays a Camunda 7.24 database. You can go back to Camunda 7.24 or move to another Camunda 7 fork
  without a migration of the data; the code stays available under the Apache License 2.0.
- **Security patches:** vulnerabilities in Fasti and in the libraries it ships take priority over
  other work. When a security fix of the base image or the JRE affects Fasti, a patch release
  with the updated base image or JRE is published; images that are already published are not
  rebuilt. The project follows public security advisories about Camunda 7 and its forks and
  checks whether Fasti is affected.
- **Compatibility:** the `1.x` line keeps the 7.24 database schema and the `org.camunda.bpm.*`
  packages. Breaking changes are marked **BREAKING** in the [CHANGELOG](../CHANGELOG.md).
- **Support:** questions and bugs are welcome on GitHub; support there is best effort, without an
  SLA. For help with installation, migration or production, and for support terms, write to
  <contact@fasti.dev>.
- **Fixed defects:** the *Fixed* and *Security* sections of the [CHANGELOG](../CHANGELOG.md) list
  what was fixed compared with Camunda 7 CE 7.24.0, with what was wrong and what changed,
  including problems in Fasti's own code; advisories are listed below.

## Advisories

- [Security test of 1.3](../docs/security/pentest-1.3.md): the issues found in 1.3.0 and fixed in 1.3.1.
- [Camunda 7 security advisories](../docs/security/camunda-7-advisories.md): where Fasti stands on each, CVE-2026-77224 (fixed in 1.3.1) included.
- [CVE-2026-114938 ("Java4Shell")](../docs/security/CVE-2026-114938.md): a flaw in the Java runtime; what Fasti changed and what you should do.
