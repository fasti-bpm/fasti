[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | Español | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> Esta traducción la mantiene la comunidad y puede quedar por detrás de la versión en inglés; la versión en inglés es la de referencia. Las correcciones son bienvenidas.

# Fasti BPM Platform

Fasti BPM es un motor independiente y de código abierto construido sobre Camunda 7 Community
Edition 7.24.0. Funciona con su base de datos de Camunda 7 actual, con bibliotecas al día,
correcciones de seguridad y una nueva interfaz web. Sitio web: <https://fasti.dev>

> **Fasti BPM no está afiliado a Camunda Services GmbH; Camunda es una marca comercial de Camunda Services GmbH.**
> Fasti BPM es un proyecto independiente de código abierto (Apache-2.0). No forma parte de Camunda Services GmbH,
> ni está patrocinado ni avalado por esta empresa. El nombre "Camunda" se utiliza únicamente para describir de dónde
> procede el código fuente y con qué es compatible Fasti.

<a id="quick-start"></a>

## Inicio rápido

La imagen está disponible en Docker Hub (`fastidev/fasti-bpm`) y en GitHub
Container Registry (`ghcr.io/fasti-bpm/fasti-bpm`), para amd64 y arm64:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.2.0
```

Abra <http://localhost:8080/fasti/> e inicie sesión. Sin `DB_URL`, la imagen utiliza una base de datos H2
dentro del contenedor, únicamente para evaluación. La API REST requiere credenciales de autenticación Basic:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

Guía completa, incluido el uso de su propia base de datos: <https://fasti.dev/install>

<a id="highlights"></a>

## Características destacadas

- **Cambie la conexión. Esa es la migración.** El esquema de Camunda 7.24 se usa tal cual; los
  paquetes `org.camunda.bpm.*` se mantienen ([migración](https://fasti.dev/install#migrate)).
- **Un contenedor, un comando**, con las mismas variables `DB_*` que Camunda Run ([instalación](https://fasti.dev/install)).
- **Hecha para quienes trabajan la cola de tareas**: una nueva interfaz web en 13 idiomas, con tema
  claro y oscuro, utilizable en un teléfono ([funciones](https://fasti.dev/#features)).
- **Modele y despliegue en el navegador**: BPMN, DMN y formularios ([modelador](https://fasti.dev/#modeler)).
- **Las herramientas para operarlo vienen incluidas**: historial, reparación de instancias,
  reintento en bloque, registro de operaciones ([operación](https://fasti.dev/#operate)).
- **Cerrado por defecto en la imagen de contenedor**: inicio de sesión obligatorio desde el primer
  arranque, sin usuario por defecto, se rechazan los ajustes que desactivan la autenticación REST o
  las autorizaciones, y Java4Shell está mitigado. Tomcat y WildFly exigen credenciales para la API
  REST y no crean usuarios (el primer administrador se crea en la página de configuración inicial);
  el zip de Run y el starter de Spring Boot conservan los valores predeterminados de Camunda 7. Todas
  las distribuciones incluyen las correcciones de seguridad y avisan al arrancar si la JVM está
  expuesta a Java4Shell ([seguridad](https://fasti.dev/security)).
- **Inicio de sesión único**: OpenID Connect y bearer tokens JWT ([guía](docs/single-sign-on.es.md)).
- **Plataforma actual**: Java 17 o posterior, Spring Boot 4.1, GraalJS 25, Jackson 2.21
  ([rendimiento](https://fasti.dev/#performance)).

<a id="installation"></a><a id="get-fasti"></a>

## Obtener Fasti

- <a id="one-command-on-your-existing-camunda-724-database"></a>**Imagen de Docker** (`fastidev/fasti-bpm`, amd64 y arm64): <https://fasti.dev/install>;
  paso a paso en [docs/install-docker.es.md](docs/install-docker.es.md), todas las variables en
  [distro/docker/README.md](distro/docker/README.md#environment-variables).
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**Paquete de Tomcat**, sin Docker: <https://fasti.dev/download>. Paquete de Tomcat 1.1.0 listo para usar:
  - [fasti-bpm-tomcat-1.1.0.zip](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.zip.sha256))
  - [fasti-bpm-tomcat-1.1.0.tar.gz](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.tar.gz.sha256))

  Verifique la suma de comprobación antes de extraerlo, por ejemplo con
  `sha256sum -c fasti-bpm-tomcat-1.1.0.zip.sha256`, y lea `README.txt` en el paquete antes de
  iniciarlo.
- **Integrado** en Spring Boot u otra aplicación Java: nuevas coordenadas de Maven
  (`dev.fasti.platform:fasti-*`), los mismos paquetes `org.camunda.bpm.*`; consulte
  [docs/embedding.es.md](docs/embedding.es.md).

<a id="moving-from-camunda-7"></a>

## Migrar desde Camunda 7

Si usa Camunda 7.24, Fasti funciona sobre esa misma base de datos sin modificar su esquema. Puede
sustituir los nodos antiguos, ejecutar Fasti junto a ellos y volver a Camunda 7.24 en cualquier
momento. La imagen de Fasti se probó tomando el control de bases de datos creadas por Camunda Run
7.24 en PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022 y Oracle Database 23ai; la vuelta a
Camunda Run se probó en las mismas bases de datos, salvo MySQL. DB2 aún no está probada. Ahora todos los clientes REST, incluidos los workers de tareas externas, deben enviar
credenciales Basic o un bearer token.

Las aplicaciones Spring Boot embebidas pasan además a Spring Boot 4.1 y Java 17; los esquemas
anteriores se actualizan primero con los scripts estándar. Los plugins y las aplicaciones de proceso
creados para Camunda 7 deben recompilarse contra los artefactos de Fasti para ejecutarse dentro de
Fasti. La ejecución en paralelo y la vuelta atrás tienen límites (la misma configuración en todos
los nodos, qué nodo ejecuta los jobs, procesos que usan funciones exclusivas de Fasti): consulte
[operación mixta](docs/migrate-from-camunda-7.es.md#mixed-operation-with-camunda-7-nodes) y
[vuelta atrás](docs/migrate-from-camunda-7.es.md#rollback-to-camunda-724).

Pasos y límites de la migración: <https://fasti.dev/install#migrate> y la
[guía de migración](docs/migrate-from-camunda-7.es.md) (reversión, esquemas anteriores, resultados
de las pruebas).

<a id="comparison"></a><a id="how-it-compares"></a>

## Comparación

Varios buenos proyectos dan continuidad a Camunda 7. Fasti mantiene los paquetes `org.camunda.bpm.*`
y el esquema de 7.24, de modo que el cambio es un cambio de conexión y no un proyecto de migración, y
añade una nueva interfaz. Los otros proyectos también son opciones sólidas. La tabla completa, con
fuentes: <https://fasti.dev/compare>.

<a id="commitments-support-and-security-patches"></a><a id="security-and-support"></a>

## Seguridad y soporte

La última versión menor de la línea `1.x` recibe correcciones de errores y parches de seguridad,
como versiones de parche del tipo `1.0.1`. Por favor, notifique los problemas de seguridad de forma
privada, no en un issue público: consulte [SECURITY.md](SECURITY.md#security-policy), que también
recoge la [política de soporte](SECURITY.md#support-policy). Lista de correcciones, Java4Shell y la
revisión previa al lanzamiento: <https://fasti.dev/security>. Para ayuda con instalación, migración
o producción, y para condiciones de soporte, escriba a <contact@fasti.dev>.

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## Hoja de ruta

Esto es un plan, no un compromiso de fechas. Publicado en `1.2.0`, centrado en el rendimiento: un
endpoint de carga de trabajo, índices opcionales para la base de datos, avisos de rendimiento al
arrancar, ajustes del registro de auditoría y una interfaz web más rápida. Publicado en `1.1.0`: un
chart de Helm y manifiestos de Kubernetes, métricas de Prometheus, trazas de OpenTelemetry, logs
estructurados en JSON, un sustituto durante las ausencias, control de cuatro ojos para las
aprobaciones, un registro de auditoría inmutable, un servidor MCP para asistentes de IA y casos
(CMMN) en la nueva interfaz. A continuación en `1.x`: la publicación de los artefactos Maven en
Maven Central, la verificación con DB2, la revisión de las traducciones por hablantes nativos y la
retirada de las aplicaciones AngularJS. La cronología, desde el final de Camunda 7 CE hasta los
objetivos para 2027: <https://fasti.dev/#timeline>.

<a id="documentation"></a>

## Documentación

En [fasti.dev](https://fasti.dev): [Instalar con Docker](https://fasti.dev/install) ·
[Descarga para Tomcat](https://fasti.dev/download) · [Guía de integración con la API](https://fasti.dev/docs) ·
[Seguridad y soporte](https://fasti.dev/security) · [Comparativa](https://fasti.dev/compare) ·
[Blog](https://fasti.dev/blog)

<a id="what-this-is"></a><a id="what-is-different-from-camunda-7240-ce"></a><a id="versioning"></a><a id="maven-coordinates"></a><a id="third-party-libraries-that-still-pull-in-the-original-engine"></a><a id="startup-check"></a><a id="web-application-path-fasti"></a><a id="single-sign-on-openid-connect"></a><a id="a-ui-built-from-real-user-complaints"></a>

| Documento | Contenido |
|---|---|
| [docs/install-docker.es.md](docs/install-docker.es.md) | Instalación con Docker, paso a paso |
| [docs/install-kubernetes.md](docs/install-kubernetes.md) | Instalación en Kubernetes: chart de Helm y manifiestos simples (desde 1.1.0) |
| [docs/migrate-from-camunda-7.es.md](docs/migrate-from-camunda-7.es.md) | Migración desde Camunda 7, reversión, limitaciones |
| [docs/embedding.es.md](docs/embedding.es.md) | Compatibilidad, qué cambia respecto a 7.24.0, versionado, coordenadas de Maven, comprobación al arrancar |
| [docs/web-ui.es.md](docs/web-ui.es.md) | La interfaz web: funciones que pidieron los usuarios, la ruta `/fasti`, las aplicaciones clásicas |
| [docs/absence.md](docs/absence.md) | Sustitutos durante las ausencias (desde 1.1.0) |
| [docs/four-eyes.md](docs/four-eyes.md) | Control de cuatro ojos para las aprobaciones (desde 1.1.0) |
| [docs/audit-trail.md](docs/audit-trail.md) | Registro de auditoría inmutable (desde 1.1.0) |
| [docs/single-sign-on.es.md](docs/single-sign-on.es.md) | Inicio de sesión único (OpenID Connect): Run, Spring Boot, Tomcat, WildFly |
| [docs/api/README.md](docs/api/README.md) | Integración con la API REST, clientes de tareas externas para Java y JavaScript |
| [distro/docker/README.md](distro/docker/README.md) | Imagen: variables de entorno, controladores, operaciones, ajustes |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | La nueva interfaz web |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell y Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy) | Cambios, seguridad, política de soporte |

<a id="build"></a>

## Compilación desde el código fuente

Requiere JDK 17 o posterior (se recomienda 21) y acceso de red a Maven Central, nodejs.org y el
registro npm (la compilación descarga Node/npm para la interfaz web).

```bash
./mvnw clean verify
```

Una compilación completa ejecuta todas las pruebas de Java y las pruebas unitarias de la interfaz web
(cerca de una hora; las pruebas de extremo a extremo de Playwright se ejecutan por separado, consulte [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)).
Consulte [TESTING.md](TESTING.md). Para compilar la imagen del contenedor (la primera compilación
tarda unos 10–20 minutos):

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## Contribuir

Las preguntas, los informes de errores y los pull requests son bienvenidos en GitHub; consulte
[CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) y el
[Código de conducta](CODE_OF_CONDUCT.md). Son especialmente bienvenidas las revisiones de las
traducciones por parte de hablantes nativos.

<a id="acknowledgements"></a>

## Agradecimientos

- Fasti BPM Platform se basa en más de una década de trabajo de Camunda y de la comunidad de Camunda 7.
- Al revisar los defectos, el proyecto consultó información pública (incidencias, avisos,
  notas de versión) de otros proyectos comunitarios derivados de Camunda 7: Operaton, CIB
  seven, OrqueIO y EximeeBPMS. El código fuente de este repositorio se escribió
  de forma independiente sobre Camunda 7 CE.

<a id="license"></a>

## Licencia

[Licencia Apache 2.0](LICENSE). Consulte [NOTICE](NOTICE) para conocer los derechos de autor del código original.
