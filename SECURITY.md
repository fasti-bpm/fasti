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

## Advisories

- [CVE-2026-114938 ("Java4Shell")](docs/security/CVE-2026-114938.md): a flaw in the Java runtime; what Fasti changed and what you should do.
