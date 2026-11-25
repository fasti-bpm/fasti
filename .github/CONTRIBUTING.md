English | [Tiếng Việt](CONTRIBUTING.vi.md)

# How to contribute

Thank you for your interest in Fasti BPM Platform, an independent fork of Camunda 7 Community
Edition 7.24.0.

## Issues

* Search the existing issues before opening a new one.
* For a bug, describe the version, the environment (database, application server, Java
  version), the steps to reproduce and what you expected to happen. A failing unit test is
  the most helpful reproduction.
* Do **not** report security issues in public. See [SECURITY.md](SECURITY.md).
* Using Fasti BPM in your organization? See [docs/adopters.md](../docs/adopters.md) to be listed.

## Build from source

Requires JDK 17 or later (21 recommended). The Maven wrapper is included:

```
./mvnw clean verify
```

See [TESTING.md](../docs/testing.md) for the test suites and database profiles.

## Pull requests

* Keep a pull request focused on one topic.
* Add or adapt tests. A bug fix should come with a test that fails without the fix.
* Stay compatible: public API, the `org.camunda.bpm.*` packages, the configuration
  properties and the database schema of 7.24 must keep working. If a change alters default
  behaviour, say so in the pull request description.
* License headers (see "License headers and packages" below): new files carry the Fasti
  header, files modified from upstream keep the Camunda header.
* Only contribute code that you wrote yourself or that you are entitled to submit under the
  Apache License 2.0. Do not copy code from other projects into a contribution without
  stating its origin and licence in the pull request.
* Public documentation (README files, `docs/`, the security policy) is written in English.
  Translations are optional and live next to the English file as `<name>.<locale>.md` (for
  example `README.vi.md`); a change to an English document should update its translations or
  say in the pull request which ones are now out of date. The English version is
  authoritative.

By submitting a contribution you agree that it is licensed under the Apache License 2.0.

## License headers and packages

* Files written for Fasti BPM (not present in the upstream tag `7.24.0`) start with:

  ```
  /*
   * SPDX-License-Identifier: Apache-2.0
   * Copyright Fasti BPM contributors
   */
  ```

  (with `#` or `<!-- -->` for files that do not use `/* */` comments).
* Files that come from Camunda 7 CE 7.24.0 keep the Camunda header, also when modified. Files
  copied or renamed from an upstream file keep the Camunda header too; list their new paths in
  `build-config/license-header-upstream-derived.txt`.
* `webapps/frontend-next/scripts/check-license-headers.mjs` and the Maven profile
  `license-header-check` check this rule.
* Fasti BPM code intentionally lives in `org.camunda.*` packages, for compatibility with
  existing configuration, plugins and process applications. Do not move packages.
  `dev.fasti.*` is reserved for clearly separate modules.

## Commit message conventions

```
<type>(<scope>): <subject>

<body>
```

* `type`: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci` or `chore`.
* `scope`: the module that is changed, e.g. `engine`, `engine-rest`, `webapps`.
* `subject`: imperative form, at most 72 characters, no trailing period.
* `body`: what the problem was and how the change solves it; lines of at most 80
  characters. It can be omitted for small changes.
* Breaking changes: add `!` after the type or scope and a `BREAKING CHANGE:` footer that
  explains how to migrate.
* Commit messages are written in English.

Example:

```
fix(engine): validate expressions in nested or queries of task queries

Or queries were created without the validators of the enclosing query,
so restrictions on expressions did not apply to them.
```
