# Publishing the Maven artifacts to Maven Central

This guide is for the maintainers who release Fasti BPM Platform. The build is ready to
publish to [Maven Central](https://central.sonatype.com) through the Sonatype Central Portal;
what is still missing is the account, the namespace, the signing key and the GitHub secrets.

## What is published

The root POM profile `central` builds and publishes the libraries that applications use, with
their parent POMs and BOMs (83 artifacts for 1.0.1), all under `dev.fasti.platform` and its
subgroups:

- BOMs: `fasti-bom`, `fasti-only-bom` and the BOMs of commons, connect, spin and the DMN engine;
  the parent POMs they and the libraries inherit from (`fasti-root`, `fasti-parent`,
  `fasti-database-settings`, `fasti-core-internal-dependencies` and the module roots).
- Engine and libraries: `fasti-engine`, BPMN/CMMN/DMN/XML model APIs, DMN engine and FEEL,
  JUEL, commons, connect, spin, FreeMarker template engine, CDI and Spring integration, the REST
  API (jars and WARs), the engine plugins (connect, spin, LDAP, OpenID Connect), the EJB clients.
- Spring Boot starters (core, REST, security, web applications, test, external task client)
  and the Quarkus extension.
- The web applications (WARs, webjar, OpenID Connect module) and the Java external task
  client.
- Test libraries: `fasti-bpm-assert`, `fasti-bpm-junit5`, `fasti-dmn-junit5`.

Not published: QA and integration test modules, the distributions (Fasti BPM Run, Tomcat,
WildFly, Docker image; these are released as the container image and on GitHub), the license
book, the REST API documentation build and internal build/test helpers. A module that is built
by the profile but must not be published sets `<skip.central.release>true</skip.central.release>`
(for example `test-utils/archunit`); a module that should not be built at all is simply not
listed in the `central` profile of the root POM (or of its aggregator, see
`spring-boot-starter/pom.xml`).

Every published artifact gets a sources jar, a javadoc jar and a GPG signature; the POMs carry
the project metadata (URL, Apache-2.0 licence, SCM, developers, issue tracker) inherited from
`fasti-root`, which has no parent of its own.

## One-time setup (owner)

1. **Central Portal account.** Sign in at <https://central.sonatype.com> (a GitHub login works)
   with the account that will own the namespace.
2. **Namespace `dev.fasti`.** Under *Namespaces*, add `dev.fasti`. The portal shows a
   verification key; add it as a DNS `TXT` record on `fasti.dev` and press *Verify*. Once
   `dev.fasti` is verified, all subgroups (`dev.fasti.platform`, `dev.fasti.platform.run`, ...)
   can be published. The record can be removed after verification.
3. **GPG key.** Create a signing key for the project (not a personal key), for example:

   ```sh
   gpg --full-generate-key            # RSA 4096 or ed25519, name "Fasti BPM", email dev@fasti.dev
   gpg --list-secret-keys --keyid-format long
   gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   gpg --armor --export-secret-keys <KEY_ID> > fasti-signing-key.asc   # for the secret below
   ```

   The public key must be on a public key server: Central checks the signatures against it.
   Keep a backup of the key and its passphrase outside GitHub, and delete the exported file
   after adding the secret.
4. **Portal token.** In the portal, *View Account* → *Generate User Token*. It shows a
   username and a password for the token (not your login).
5. **GitHub environment `maven-central`** (repository *Settings* → *Environments* → *New
   environment*). The `publish` job of the workflow runs in this environment and is the only
   job that can read its secrets.

   - *Required reviewers*: add the maintainers who may release. Every run then waits for an
     approval before the job starts, that is before any secret is read.
   - *Deployment branches and tags*: restrict to `main`, the branch the workflow is started
     from.
   - *Environment secrets*:

     | Secret | Value |
     | --- | --- |
     | `MAVEN_CENTRAL_USERNAME` | token username from step 4 |
     | `MAVEN_CENTRAL_PASSWORD` | token password from step 4 |
     | `MAVEN_GPG_PRIVATE_KEY` | contents of `fasti-signing-key.asc` (ASCII-armoured secret key) |
     | `MAVEN_GPG_PASSPHRASE` | passphrase of the key |

   Add them as secrets of the environment, not of the repository: a repository secret can be
   read by every workflow and every job. If the environment does not exist when the workflow
   first runs, GitHub creates it empty and without protection, and the `publish` job fails
   because the secrets are missing.

## How the workflow keeps the secrets away from the build

`publish-maven.yml` has two jobs:

1. **`bundle`** (no secrets, no environment) checks out the tag, checks that the POM version
   matches it and runs `./mvnw -Pcentral deploy -DskipTests -Dgpg.skip -Dcentral.skipUpload=true`.
   This is the step that runs project code, Maven plugins and the npm build of the web UIs. It
   produces the unsigned `central-bundle.zip` (artifacts, sources, javadoc, checksums) and keeps
   it as a workflow artifact, with its SHA-256 as a job output.
2. **`publish`** (environment `maven-central`) checks out nothing and runs no build. It
   downloads the bundle, compares it with the SHA-256 of the first job, signs every file with
   `gpg` in a keyring that exists for that step only, and uploads the signed bundle through the
   [Publisher API](https://central.sonatype.com/api-doc) of the Central Portal (`curl`). It
   then waits until the portal has validated the deployment.

A dry run stops after the first job, so it never touches a secret or the signing key.

## Releasing a version

1. Release the version as usual (tag `vX.Y.Z`; `release.yml` runs the full build and tests and
   publishes the container image).
2. In GitHub, *Actions* → **publish-maven** → *Run workflow* (from `main`), enter the tag
   (`vX.Y.Z`). Leave *auto-publish* off. The `bundle` job checks that the POM version matches
   the tag and builds the published modules (without tests). The `publish` job then waits for
   the approval of a required reviewer of the environment `maven-central`, signs every file
   and uploads the bundle. It ends when the portal has validated the deployment.
3. On <https://central.sonatype.com/publishing/deployments>, look at the deployment
   *Fasti BPM Platform X.Y.Z*: the list of components and the validation result. Press
   **Publish** to release it, or **Drop** to discard it. Publishing cannot be undone: a version
   on Maven Central can never be changed or deleted.
4. The artifacts appear on `repo1.maven.org` within about 30 minutes, in the search a few hours
   later.

Without the secrets in the environment `maven-central` the `publish` job fails before it signs
or uploads anything; the `bundle` job and dry runs do not need them.

## Dry run

- **In GitHub:** run **publish-maven** with *dry-run* on. Only the `bundle` job runs: it reads
  no secrets and needs no approval, builds the unsigned bundle, uploads nothing and keeps
  `central-bundle.zip` as a workflow artifact for three days.
- **Locally**, without a key or an account:

  ```sh
  ./mvnw -Pcentral deploy -DskipTests -Dgpg.skip -Dcentral.skipUpload=true
  ```

  The bundle is written to `target/central-publishing/central-bundle.zip`. Add
  `-pl <module> -am` to build only part of the reactor, and `-Dskip.frontend.build=true` to
  leave out the web UI build (Node.js, most of the time; the WARs then lack the UI, so never
  publish such a bundle). Only the portal validates a bundle, after an upload: a local dry run
  shows that the build works and which files the bundle contains.

## Publishing from a workstation

With the token in `~/.m2/settings.xml` (server id `central`) and the key in the local GPG
keyring:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>TOKEN_USERNAME</username>
      <password>TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

```sh
git checkout vX.Y.Z
MAVEN_GPG_PASSPHRASE=... ./mvnw -Pcentral deploy -DskipTests [-Dgpg.keyname=<KEY_ID>]
```

## Properties of the profile

| Property | Default | Meaning |
| --- | --- | --- |
| `skip.central.release` | `false` | set to `true` in a module that is built but not published |
| `central.skipUpload` | `false` | build the bundle locally, do not upload it (the Portal validates only uploaded bundles) |
| `central.autoPublish` | `false` | publish right after validation, without the manual step |
| `central.waitUntil` | `validated` | how long the build waits (`uploaded`, `validated`, `published`) |
| `gpg.skip` | `false` | do not sign (dry runs, and the `bundle` job of the workflow, whose `publish` job signs afterwards; Central rejects unsigned files) |
| `gpg.keyname` | default key | the key to sign with |

The plugin is `org.sonatype.central:central-publishing-maven-plugin`; the server id in the
Maven settings is `central`.
