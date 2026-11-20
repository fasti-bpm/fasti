English | [Tiếng Việt](SECURITY.vi.md)

# Security Policy

## Reporting a vulnerability

Please do **not** report security issues in public issues, pull requests or discussions.

Use GitHub's private vulnerability reporting for this repository: open the **Security** tab
and choose **Report a vulnerability**. Include the affected version, the configuration that
is needed, and steps to reproduce.

If private vulnerability reporting is unavailable, contact the maintainers privately through
the contact listed on the repository owner's GitHub profile, and do not include exploit details
in the first message.

You will receive an acknowledgement, and we will keep you informed until a fix is released.
Please give us reasonable time to publish a fix before disclosing details.

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
  patches (as patch releases, for example `1.0.1`).
- **Security patches:** vulnerabilities in Fasti and in the libraries it ships take priority over
  other work; the image is rebuilt when the base image or the JRE gets a fix. The project follows
  public security advisories about Camunda 7 and its forks and checks whether Fasti is affected.
- **Compatibility:** the `1.x` line keeps the 7.24 database schema and the `org.camunda.bpm.*`
  packages. Breaking changes are marked **BREAKING** in the [CHANGELOG](CHANGELOG.md).
- **Support:** questions and bugs are welcome on GitHub; support there is best effort, without an
  SLA. For help with installation, migration or production, and for support terms, write to
  <contact@fasti.dev>.
- **Fixed defects:** the *Fixed* and *Security* sections of the [CHANGELOG](CHANGELOG.md) list
  what was fixed compared with Camunda 7 CE 7.24.0, with what was wrong and what changed,
  including problems in Fasti's own code; advisories are listed below.

## Advisories

- [CVE-2026-114938 ("Java4Shell")](docs/security/CVE-2026-114938.md): a flaw in the Java runtime; what Fasti changed and what you should do.
