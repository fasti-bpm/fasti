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
5. **GitHub secrets** (repository *Settings* → *Secrets and variables* → *Actions*):

   | Secret | Value |
   | --- | --- |
   | `MAVEN_CENTRAL_USERNAME` | token username from step 4 |
   | `MAVEN_CENTRAL_PASSWORD` | token password from step 4 |
   | `MAVEN_GPG_PRIVATE_KEY` | contents of `fasti-signing-key.asc` (ASCII-armoured secret key) |
   | `MAVEN_GPG_PASSPHRASE` | passphrase of the key |

   Consider putting them in a GitHub *environment* with required reviewers if more people get
   write access to the repository.

## Releasing a version

1. Release the version as usual (tag `vX.Y.Z`; `release.yml` runs the full build and tests and
   publishes the container image).
2. In GitHub, *Actions* → **publish-maven** → *Run workflow*, enter the tag (`vX.Y.Z`). Leave
   *auto-publish* off. The workflow checks that the POM version matches the tag, builds the
   published modules (without tests), signs every file and uploads the bundle. It ends when the
   portal has validated the deployment.
3. On <https://central.sonatype.com/publishing/deployments>, look at the deployment
   *Fasti BPM Platform X.Y.Z*: the list of components and the validation result. Press
   **Publish** to release it, or **Drop** to discard it. Publishing cannot be undone: a version
   on Maven Central can never be changed or deleted.
4. The artifacts appear on `repo1.maven.org` within about 30 minutes, in the search a few hours
   later.

Without the secrets, the workflow does nothing: the `publish` job is skipped with a notice.

## Dry run

- **In GitHub:** run **publish-maven** with *dry-run* on. It needs no secrets, builds the
  bundle (signed only if the key secrets exist), uploads nothing and keeps
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
| `gpg.skip` | `false` | do not sign (dry runs only: Central rejects unsigned files) |
| `gpg.keyname` | default key | the key to sign with |

The plugin is `org.sonatype.central:central-publishing-maven-plugin`; the server id in the
Maven settings is `central`.
