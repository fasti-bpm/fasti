English | [Tiếng Việt](embedding.vi.md) | [Deutsch](embedding.de.md) | [Español](embedding.es.md) | [Français](embedding.fr.md) | [हिन्दी](embedding.hi.md) | [Bahasa Indonesia](embedding.id.md) | [日本語](embedding.ja.md) | [한국어](embedding.ko.md) | [Português (Brasil)](embedding.pt-BR.md) | [Русский](embedding.ru.md) | [ไทย](embedding.th.md) | [简体中文](embedding.zh-CN.md)

# Embedding Fasti BPM: compatibility and Maven coordinates

What Fasti keeps from Camunda 7 and what is different from 7.24.0, how versions are numbered, and the Maven coordinates for applications that embed the engine. To run Fasti as a server, see [Installing with Docker](install-docker.md) or the Tomcat bundle on <https://fasti.dev/download>.

<a id="what-this-is"></a>

## What this is

This repository contains the BPMN 2.0 / DMN process engine, the REST API, the web
applications (Cockpit, Tasklist, Admin) and the distributions (Run, Tomcat, WildFly). It
starts from the `7.24.0` tag of Camunda 7 CE and continues with bug fixes and library
upgrades.

The goal is **backward compatibility** with applications that run on Camunda 7:

- the `org.camunda.bpm.*` packages, the BPMN namespace
  `http://camunda.org/schema/1.0/bpmn` and the `camunda.bpm.*` configuration prefix are kept;
- the database schema of 7.24 is kept;
- all deployment flavors are kept: Spring Boot, Run, Tomcat, WildFly; `javax` and `jakarta`.

<a id="what-is-different-from-camunda-7240-ce"></a>

## What is different from Camunda 7.24.0 CE

- **Security:** fixes for the publicly disclosed issues of the 7.x line (expressions in
  queries, initial setup, access to attachments and comments, Cockpit permissions, open
  redirect) plus additional safeguards: an allow-list for expressions in queries and
  filters, attachment and comment checks inside the engine, hardened security filters,
  headers and cookies in the web applications.
- **Stability:** no more memory leak when scripts and DMN expressions are evaluated with
  JavaScript (GraalJS), and a number of other engine fixes.
- **Platform:** Java 17 or later, Spring Boot 4.1, GraalJS 25, Jackson 2.21 and many other
  libraries upgraded to releases with security fixes.

Some fixes change defaults toward safer behavior; read "What differs" in the
[migration guide](migrate-from-camunda-7.md#what-differs) before upgrading.

<a id="versioning"></a>

## Versioning

Fasti BPM Platform follows [Semantic Versioning](https://semver.org/), starting at `1.0.0`.
The `1.x` line is compatible with applications written for Camunda 7.24.

<a id="maven-coordinates"></a>

## Maven coordinates

The artifacts of Fasti BPM Platform have their own Maven coordinates. **The Java packages
remain `org.camunda.bpm.*`**, so application code keeps its imports; the `camunda.bpm.*`
configuration prefix, the BPMN/DMN namespaces, the database schema and the module names on
WildFly are unchanged as well. As far as Maven is concerned, only the dependency declarations
change (the web application path changes separately, see
[Web application path](web-ui.md#web-application-path-fasti)):

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`; the groups `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` and `org.camunda.template-engines` → `dev.fasti.platform.<group name>`.
- **artifactId:** the prefix `camunda-` becomes `fasti-`, the rest is kept.
- **version:** the version of Fasti BPM Platform (see [Versioning](#versioning)) instead of
  `7.24.0`.

No release of these artifacts has been published to Maven Central yet; the build is prepared
for it (see [Publishing to Maven Central](publishing-maven-central.md)) and the first
published version will be announced in the changelog. Until then, build from source and make
the artifacts available in the Maven repository your project uses.

Manage the versions with the BOM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.fasti.platform</groupId>
      <artifactId>fasti-bom</artifactId>
      <version>${fasti.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Frequently used artifacts:

| Camunda 7 | Fasti BPM Platform |
|---|---|
| `org.camunda.bpm:camunda-bom` | `dev.fasti.platform:fasti-bom` |
| `org.camunda.bpm:camunda-engine` | `dev.fasti.platform:fasti-engine` |
| `org.camunda.bpm:camunda-engine-spring` | `dev.fasti.platform:fasti-engine-spring` |
| `org.camunda.bpm:camunda-engine-spring-6` | `dev.fasti.platform:fasti-engine-spring-6` |
| `org.camunda.bpm:camunda-engine-cdi-jakarta` | `dev.fasti.platform:fasti-engine-cdi-jakarta` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-rest` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-webapp` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-webapp` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-security` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-security` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-external-task-client` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-external-task-client` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-test` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-test` |
| `org.camunda.bpm:camunda-engine-rest-core` | `dev.fasti.platform:fasti-engine-rest-core` |
| `org.camunda.bpm:camunda-engine-rest-core-jakarta` | `dev.fasti.platform:fasti-engine-rest-core-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp` | `dev.fasti.platform.webapp:fasti-webapp` |
| `org.camunda.bpm.webapp:camunda-webapp-jakarta` | `dev.fasti.platform.webapp:fasti-webapp-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp-webjar` | `dev.fasti.platform.webapp:fasti-webapp-webjar` |
| `org.camunda.bpm:camunda-external-task-client` | `dev.fasti.platform:fasti-external-task-client` |
| `org.camunda.bpm:camunda-engine-plugin-spin` | `dev.fasti.platform:fasti-engine-plugin-spin` |
| `org.camunda.bpm:camunda-engine-plugin-connect` | `dev.fasti.platform:fasti-engine-plugin-connect` |
| `org.camunda.spin:camunda-spin-dataformat-all` | `dev.fasti.platform.spin:fasti-spin-dataformat-all` |
| `org.camunda.connect:camunda-connect-connectors-all` | `dev.fasti.platform.connect:fasti-connect-connectors-all` |
| `org.camunda.bpm.dmn:camunda-engine-dmn` | `dev.fasti.platform.dmn:fasti-engine-dmn` |
| `org.camunda.bpm.model:camunda-bpmn-model` | `dev.fasti.platform.model:fasti-bpmn-model` |
| `org.camunda.bpm:camunda-bpm-assert` | `dev.fasti.platform:fasti-bpm-assert` |
| `org.camunda.bpm:camunda-bpm-junit5` | `dev.fasti.platform:fasti-bpm-junit5` |

The FEEL library `org.camunda.feel:feel-engine` is an external library that is not part of
this repository; its coordinates stay as they are.

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

### Third-party libraries that still pull in the original engine

Because the packages are unchanged, the original engine (`org.camunda.bpm:camunda-engine`)
and `fasti-engine` contain classes with the same names. Maven treats them as two unrelated
libraries and does not drop either of them. If a third-party library (a community extension,
for example) depends on the original artifacts, exclude them and declare the corresponding
Fasti artifacts instead:

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>some-camunda-extension</artifactId>
  <version>1.2.3</version>
  <exclusions>
    <exclusion>
      <groupId>org.camunda.bpm</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.model</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.dmn</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.commons</groupId>
      <artifactId>*</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

`mvn dependency:tree -Dincludes="org.camunda*"` lists original artifacts that are still
present (`org.camunda.feel:feel-engine` is legitimate, do not exclude it).

<a id="startup-check"></a>

### Startup check

When it is created, the engine inspects the class path and **refuses to start** if it finds
the classes of the process engine in more than one library; the error `ENGINE-12021` lists
the location of each of them. The Spring Boot starter performs the same check as soon as the
application starts. A library that is only visible through the parent class loader (a shared
engine of the server next to an engine embedded in a web application, for example) is not
counted. If your setup is intentional, the check can be switched off with a system property:

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

Limitation: if the original engine library precedes `fasti-engine` on the class path, the
engine is built by the classes of the original library, which do not contain this check.
In that case only applications that use the Fasti Spring Boot starter are stopped; with
other integrations, review the dependencies as described above.
