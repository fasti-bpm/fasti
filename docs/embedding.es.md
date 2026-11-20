[English](embedding.md) | [Tiếng Việt](embedding.vi.md) | [Deutsch](embedding.de.md) | Español | [Français](embedding.fr.md) | [हिन्दी](embedding.hi.md) | [Bahasa Indonesia](embedding.id.md) | [日本語](embedding.ja.md) | [한국어](embedding.ko.md) | [Português (Brasil)](embedding.pt-BR.md) | [Русский](embedding.ru.md) | [ไทย](embedding.th.md) | [简体中文](embedding.zh-CN.md)

> Esta traducción la mantiene la comunidad y puede quedar por detrás de la versión en inglés; la versión en inglés es la de referencia. Las correcciones son bienvenidas.

# Integrar Fasti BPM: compatibilidad y coordenadas de Maven

Qué conserva Fasti de Camunda 7 y qué cambia respecto a 7.24.0, cómo se numeran las versiones y las coordenadas de Maven para las aplicaciones que integran el motor. Para ejecutar Fasti como servidor, consulte [Instalación con Docker](install-docker.es.md) o el paquete de Tomcat en <https://fasti.dev/download>.

<a id="what-this-is"></a>

## Qué es este proyecto

Este repositorio contiene el motor de procesos BPMN 2.0 / DMN, la API REST, las aplicaciones
web (Cockpit, Tasklist, Admin) y las distribuciones (Run, Tomcat, WildFly). Parte
de la etiqueta `7.24.0` de Camunda 7 CE y continúa con correcciones de errores y actualizaciones
de bibliotecas.

El objetivo es la **compatibilidad con versiones anteriores** para las aplicaciones que se ejecutan en Camunda 7:

- se conservan los paquetes `org.camunda.bpm.*`, el espacio de nombres BPMN
  `http://camunda.org/schema/1.0/bpmn` y el prefijo de configuración `camunda.bpm.*`;
- se conserva el esquema de base de datos de 7.24;
- se conservan todas las modalidades de despliegue: Spring Boot, Run, Tomcat, WildFly; `javax` y `jakarta`.

<a id="what-is-different-from-camunda-7240-ce"></a>

## Qué cambia respecto a Camunda 7.24.0 CE

- **Seguridad:** correcciones de los problemas divulgados públicamente de la línea 7.x (expresiones en
  consultas, configuración inicial, acceso a adjuntos y comentarios, permisos de Cockpit, redirecciones
  abiertas), además de medidas de protección adicionales: una lista de expresiones permitidas en consultas y
  filtros, comprobaciones de adjuntos y comentarios dentro del motor, filtros de seguridad reforzados,
  cabeceras y cookies en las aplicaciones web.
- **Estabilidad:** se elimina la fuga de memoria al evaluar scripts y expresiones DMN con
  JavaScript (GraalJS), además de otras correcciones del motor.
- **Plataforma:** Java 17 o posterior, Spring Boot 4.1, GraalJS 25, Jackson 2.21 y muchas otras
  bibliotecas actualizadas a versiones con correcciones de seguridad.

Algunas correcciones modifican los valores predeterminados para ofrecer un comportamiento más seguro; lea «Qué cambia» en la
[guía de migración](migrate-from-camunda-7.es.md#what-differs) antes de actualizar.

<a id="versioning"></a>

## Versionado

Fasti BPM Platform sigue el [versionado semántico](https://semver.org/), a partir de `1.0.0`.
La línea `1.x` conserva la API de Java, los paquetes `org.camunda.bpm.*`, el prefijo de
configuración `camunda.bpm.*` y el esquema de base de datos de Camunda 7.24. Las aplicaciones que
integran el motor pasan a Java 17 o posterior y, con el starter de Spring Boot, a Spring Boot 4.1;
los plugins y las aplicaciones de proceso se recompilan con los artefactos `dev.fasti.platform`.

<a id="maven-coordinates"></a>

## Coordenadas de Maven

Los artefactos de Fasti BPM Platform tienen sus propias coordenadas de Maven. **Los paquetes Java
siguen siendo `org.camunda.bpm.*`**, por lo que el código de las aplicaciones conserva sus importaciones; el prefijo de configuración
`camunda.bpm.*`, los espacios de nombres BPMN/DMN, el esquema de base de datos y los nombres de los módulos en
WildFly tampoco cambian. En lo que respecta a Maven, solo cambian las declaraciones
de dependencias (la ruta de la aplicación web cambia por separado, consulte
[Ruta de la aplicación web](web-ui.es.md#web-application-path-fasti)):

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`; los grupos `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` y `org.camunda.template-engines` → `dev.fasti.platform.<group name>`.
- **artifactId:** el prefijo `camunda-` pasa a ser `fasti-`; el resto se conserva.
- **version:** la versión de Fasti BPM Platform (consulte [Versionado](#versioning)) en lugar de
  `7.24.0`.

Todavía no se ha publicado ninguna versión de estos artefactos en Maven Central. Hasta entonces, compile
a partir del código fuente y ponga los artefactos a disposición en el repositorio de Maven que utilice su proyecto.

Gestione las versiones con el BOM:

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

Artefactos de uso frecuente:

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

La biblioteca FEEL `org.camunda.feel:feel-engine` es una biblioteca externa que no forma parte de
este repositorio; sus coordenadas se mantienen tal cual.

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

### Bibliotecas de terceros que siguen incorporando el motor original

Como los paquetes no cambian, el motor original (`org.camunda.bpm:camunda-engine`)
y `fasti-engine` contienen clases con los mismos nombres. Maven los trata como dos bibliotecas
sin relación y no descarta ninguna de ellas. Si una biblioteca de terceros (una extensión comunitaria,
por ejemplo) depende de los artefactos originales, exclúyalos y declare los artefactos
correspondientes de Fasti en su lugar:

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

`mvn dependency:tree -Dincludes="org.camunda*"` enumera los artefactos originales que siguen
presentes (`org.camunda.feel:feel-engine` es legítimo, no lo excluya).

<a id="startup-check"></a>

### Comprobación al arrancar

Al crearse, el motor inspecciona la ruta de clases y **se niega a arrancar** si encuentra
las clases del motor de procesos en más de una biblioteca; el error `ENGINE-12021` enumera
la ubicación de cada una de ellas. El starter de Spring Boot realiza la misma comprobación en cuanto
arranca la aplicación. Una biblioteca que solo sea visible a través del cargador de clases padre (un motor
compartido del servidor junto a un motor integrado en una aplicación web, por ejemplo) no se
contabiliza. Si su configuración es intencionada, puede desactivar la comprobación con una propiedad del sistema:

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

Limitación: si la biblioteca del motor original precede a `fasti-engine` en la ruta de clases, el
motor se construye con las clases de la biblioteca original, que no contienen esta comprobación.
En ese caso, solo se detienen las aplicaciones que utilizan el starter de Spring Boot de Fasti; con
otras integraciones, revise las dependencias como se describe más arriba.
