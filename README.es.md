[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | Español | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> Esta traducción la mantiene la comunidad y puede quedar por detrás de la versión en inglés; la versión en inglés es la de referencia. Las correcciones son bienvenidas.

# Fasti BPM Platform

**Make Camunda 7 Great Again.** Camunda 7 Community Edition terminó con 7.24.0, su última
versión. Fasti BPM continúa a partir de ahí: el mismo motor sobre la misma base de datos, con
correcciones y actualizaciones de bibliotecas continuas, y una nueva interfaz web.

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
  fastidev/fasti-bpm:1.0.0
```

Abra <http://localhost:8080/fasti/> e inicie sesión. Sin `DB_URL`, la imagen utiliza una base de datos H2
dentro del contenedor, únicamente para evaluación. La API REST requiere credenciales de autenticación Basic:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

**Compile la imagen usted mismo** a partir del código fuente. Para ello se necesitan Docker, JDK 17+ y acceso
de red a Maven Central, nodejs.org y el registro npm (la compilación descarga Node/npm para la
interfaz web); la primera compilación tarda unos 10–20 minutos:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
# then run the docker run command above with fasti-bpm:local instead of fastidev/fasti-bpm:1.0.0
```

La imagen **requiere autenticación REST y aplica las autorizaciones de forma predeterminada** (a diferencia de Camunda
Run); solo `FASTI_INSECURE_DEMO=true` las desactiva, para demostraciones.

<a id="highlights"></a>

## Características destacadas

- **Nueva interfaz web** en `/fasti/` (React):
  - búsqueda de instancias de proceso por varias variables a la vez, combinadas con AND/OR;
  - cronología del caso dentro de la tarea (comentarios y acciones en cada paso);
  - un botón «Obtener siguiente trabajo»;
  - fechas de vencimiento y urgencia (SLA), trabajo vencido;
  - carga de trabajo del equipo por usuario y grupo;
  - procedimientos operativos adjuntos a los incidentes;
  - una página de estado del sistema (incidentes, trabajos fallidos, trabajos retrasados, tareas externas, lotes);
  - diseño adaptado a dispositivos móviles;
  - 13 idiomas, incluido el vietnamita; el modelador BPMN también está disponible en vietnamita.
- **Inicio de sesión único (OIDC)**: Keycloak, Microsoft Entra ID y otros proveedores de OpenID Connect;
  asignación de atributos de identidad a grupos e inquilinos; tokens de portador JWT para la API REST. Disponible para Run,
  Spring Boot, Tomcat y WildFly.
- **Refuerzo de la seguridad**: lista de expresiones permitidas en consultas y filtros, comprobaciones de adjuntos y
  comentarios dentro del motor, configuración inicial, permisos de Cockpit, redirecciones abiertas, cabeceras de
  aplicaciones web, cookies y CSP. Más información en el [registro de cambios](CHANGELOG.md).
- **Autenticación activada de forma predeterminada**: la imagen requiere credenciales para REST y aplica
  las autorizaciones en todos los modos.
- **Comprobación de la versión del esquema**: el motor se niega a arrancar con un esquema anterior a 7.24 y nunca
  actualiza un esquema por sí solo.
- **Mitigación de Java4Shell** ([CVE-2026-114938](docs/security/CVE-2026-114938.md)): la imagen
  amd64 incluye un JRE de Temurin corregido y desactiva el intrínseco Base64; el motor emite una advertencia cuando
  se ejecuta en una JVM afectada.
- **Rendimiento**: la primera carga de la nueva interfaz web pasa de 960 KB a 330 KB (gzip activado de forma
  predeterminada); el rendimiento del ejecutor de trabajos aumenta aproximadamente 2.5x con ajustes (cifras y configuración de las pruebas en
  [distro/docker/README.md](distro/docker/README.md#performance-tuning)).
- **Plataforma actual**: Java 17 o posterior, Spring Boot 4.1, GraalJS 25, Jackson 2.21.

<a id="installation"></a>

## Instalación

<a id="one-command-on-your-existing-camunda-724-database"></a>

### Un solo comando sobre su base de datos existente de Camunda 7.24

La imagen acepta las variables `DB_*` de la antigua imagen de Camunda 7 Run. Haga una copia de seguridad de la base de datos
y, después:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e TZ=Europe/Berlin \
  fastidev/fasti-bpm:1.0.0
```

Los usuarios, contraseñas, grupos, inquilinos, autorizaciones, filtros, despliegues, instancias en ejecución,
historial, trabajos e incidentes se conservan tal cual. El puerto solo está vinculado a esta máquina; para acceder
de forma remota, utilice un proxy inverso con HTTPS. Inicie sesión en `/fasti/` con una cuenta existente.
Como la autenticación está activada, todos los clientes REST (trabajadores de tareas externas, integraciones) deben enviar
credenciales de autenticación Basic o un token de portador.

- **Ejecución en paralelo** con nodos de Camunda 7.24 CE sobre la misma base de datos: el mismo nivel de historial, la misma
  `TZ` y la misma configuración de contraseñas. Si los nodos antiguos son aplicaciones de proceso con clases Java que
  Fasti no tiene, configure `FASTI_JOB_EXECUTOR_ENABLED=false` en Fasti (o haga que todos los nodos
  tengan en cuenta los despliegues), y no inicie esos procesos ni complete sus tareas a través de Fasti: una
  llamada síncrona ejecuta los delegados en el nodo que la recibe.
- **Reversión**: Fasti no modifica el esquema, por lo que basta con detener Fasti y volver a iniciar los nodos de Camunda 7.24
  sobre la misma base de datos. Los procesos desplegados que utilicen funciones exclusivas de Fasti no se ejecutarán en
  Camunda 7; consulte [Reversión a Camunda 7.24](docs/migrate-from-camunda-7.es.md#rollback-to-camunda-724).
- **Esquemas anteriores (7.17–7.23)**: ejecute primero los scripts de `sql/upgrade` incluidos en la imagen
  (`/fasti/configuration/sql/upgrade`) hasta llegar a 7.24; por ejemplo, de 7.23 → 7.24:
  `postgres_engine_7.23_to_7.24.sql`.

Probado con la imagen (utilizando una base de datos creada por Camunda Run 7.24.0, revirtiendo
y ejecutando en paralelo): **PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022, Oracle
Database 23** (el controlador de Oracle no está incluido; añada `ojdbc11` usted mismo; la reversión no se probó
en MySQL). **DB2 no se ha
verificado.** H2 es solo para evaluación. Resultados de cada prueba y limitaciones:
[guía de migración](docs/migrate-from-camunda-7.es.md#limits).

Guías detalladas:

- [Instalación con Docker](docs/install-docker.es.md): H2, PostgreSQL con `docker run` y
  `docker compose`, bases de datos existentes, modo en paralelo, comprobaciones de estado, actualización, reversión,
  resolución de problemas.
- [Migración desde Camunda 7](docs/migrate-from-camunda-7.es.md): pasos, qué se mantiene igual y qué
  cambia, reversión, limitaciones.
- [Referencia de variables de entorno de la imagen](distro/docker/README.md#environment-variables).

<a id="without-docker-tomcat-bundle-download-from-fastidev"></a>

### Sin Docker: paquete de Tomcat (descarga desde fasti.dev)

Paquete de Tomcat 1.0.0 listo para usar (página de descarga: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256))
- [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256))

Verifique la suma de comprobación antes de extraerlo, por ejemplo con `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`.

La distribución de Tomcat también se puede compilar a partir del código fuente (`distro/tomcat`). Lea `README.txt` en
el paquete antes de iniciarlo: la API REST (`/engine-rest`) requiere autenticación HTTP Basic
de forma predeterminada, no se crea ningún usuario (cree el primer administrador en la página de configuración de `/fasti/`),
y, al utilizar una base de datos de Camunda Run, configure `jobExecutorDeploymentAware=false` en
`conf/bpm-platform.xml`; de lo contrario, no se ejecutarán los temporizadores ni los trabajos asíncronos de los despliegues existentes.

<a id="a-ui-built-from-real-user-complaints"></a>

## Una interfaz creada a partir de quejas reales de los usuarios

Las funciones de la nueva interfaz web se eligieron a partir de lo que los usuarios de Camunda 7 han publicado en el
foro de Camunda y en GitHub, por ejemplo:

| Lo que pedían los usuarios | Fuente | En Fasti |
|---|---|---|
| Buscar instancias por varias variables, con OR | foro [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Constructor de condiciones con varias variables, AND/OR |
| Ver los comentarios en cada paso de un caso | foro [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Cronología del caso en la tarea; comentarios en la propia instancia del proceso |
| Ordenar las tareas por variables, las más antiguas primero | foro [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Ordenación por variables, fechas de vencimiento y urgencia |
| Delegación, suplentes durante las ausencias | foro [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Ya existe la delegación por tarea; los suplentes por ausencia están en la [hoja de ruta](#roadmap-plan) |
| Clave de negocio y variables al iniciar un proceso sin formulario | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Sí |
| Descargar el XML BPMN desplegado | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Sí |
| Etiquetas coherentes, varios idiomas | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 idiomas |

<a id="comparison"></a>

## Comparación

Una comparación breve basada en la documentación pública de cada proyecto (consultada en noviembre de 2026);
«no verificado» significa que no encontramos ninguna fuente fiable. Cada proyecto tiene su propia orientación y es una
opción sólida para distintas necesidades; consulte su documentación antes de decidir.

| | Licencia | Funciona sobre una BD existente de C7 | Migración necesaria | Parches de seguridad | Java / Spring Boot | Interfaz | SSO | Imagen de Docker |
|---|---|---|---|---|---|---|---|---|
| **Fasti BPM** | Apache-2.0 | Sí, esquema 7.24 sin cambios | Imagen: ninguna. Aplicaciones con motor integrado: nuevas coordenadas de Maven, Spring Boot 4.1 y Java 17+; se conservan los paquetes `org.camunda.*` | Sí, consulte la [política más abajo](#commitments-support-and-security-patches) | 17+ / 4.1 | Nueva (React) + clásica (obsoleta) | OIDC integrado | Sí, desde 1.0.0 |
| Camunda 7 CE 7.24 | Código fuente Apache-2.0; algunos componentes web incluidos bajo MIT ([13]) | (la original) | — | No hay versiones de CE después de 7.24 ([1]) | 17, 21, 25 ([2]) / 3.5 | Cockpit, Tasklist, Admin (AngularJS) | OAuth2 mediante el starter de seguridad | Antigua imagen de Run |
| Camunda 7 Enterprise | Comercial | Sí (misma línea de producto) | No | Hasta 04/2030, extensión de pago hasta 04/2032 ([3]) | no verificado | Cockpit Enterprise | no verificado | no verificado |
| Camunda 8 | Camunda License 1.0; la producción autogestionada requiere una licencia desde 8.6 ([4]) | No (motor diferente) | Sí (modelos, instancias, datos) | Sí (proveedor) | motor diferente, no comparable | Operate, Tasklist | no verificado | no verificado |
| Operaton | Apache-2.0 | Misma estructura de tablas que 7.24; 2.2 registra la versión de esquema 7.25 mediante un script de actualización ([5]) | Paquetes renombrados a `org.operaton.*` (receta de OpenRewrite disponible); script de versión del esquema (2.2) | Sí, línea 2.2.x ([6]) | 17+ / 4.1 ([5]) | Clásica + webapps-neo (versión preliminar) | no verificado | Sí ([7]) |
| CIB seven | Apache-2.0; EE requiere una clave de licencia | Sí (cambios de 7.24 incorporados) | Paquetes renombrados a `org.cibseven.*`, prefijo `cibseven.*` | Sí, por versión ([8]) | 17, 21 / 3.5 y 4.0 ([9]) | Cliente web Vue 3 | OAuth2/JWT para REST ([8]) | Sí ([8]) |
| OrqueIO | Apache-2.0 | Compatible con las tablas de C7 según su documentación ([10]) | Grupo de Maven `io.orqueio`; no se indica si se renombran los paquetes | Sí, según su documentación ([10]) | 21 / 3.5, 4.0, 4.1 | Reescritura en Angular | OIDC con varios proveedores | no verificado |
| EximeeBPMS | Apache-2.0 (Monitor: MIT) | El proyecto afirma que su esquema sigue siendo compatible con Camunda 7 (no verificado por Fasti) ([14]) | Paquetes renombrados a `org.eximeebpms.*`, Model API renombrada | Sí, las dos versiones menores más recientes ([11]) | 21 / versión de Spring Boot no indicada | Monitor independiente | no verificado | Sí (repositorio `eximeebpms-docker`) |
| Fluxnova | Apache-2.0 (FINOS) | no verificado | no verificado | no verificado | 21 (imagen de Corretto) / no verificado | Control Center | no verificado | no verificado | Sí ([12]) |

Los puntos débiles del propio Fasti, expuestos con claridad: un proyecto joven con un equipo pequeño, aún sin soporte
comercial y sin verificación de DB2.

Fuentes: [1] [Anuncio del fin de vida de CE](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921);
[2] [Entornos compatibles con 7.24](https://docs.camunda.org/manual/7.24/introduction/supported-environments/);
[3] [Extensión de Enterprise](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/);
[4] [Licencias de Camunda 8.6](https://camunda.com/blog/2024/10/camunda-licensing-what-you-need-to-know/);
[5] [Operaton 2.2](https://docs.operaton.org/docs/documentation/reference/release-notes/2_2/);
[6] [SECURITY.md de Operaton](https://github.com/operaton/operaton/blob/main/SECURITY.md);
[7] [operaton-docker](https://github.com/operaton/operaton-docker);
[8] [CIB seven 2.2.0](https://docs.cibseven.org/manual/latest/release-notes/release-notes-2.2.0-ce/);
[9] [Entornos compatibles con CIB seven](https://docs.cibseven.org/manual/latest/introduction/supported-environments/);
[10] [Documentación de OrqueIO](https://docs.orqueio.io/);
[11] [SECURITY.md de EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms/blob/main/SECURITY.md);
[12] [Fluxnova](https://github.com/finos/fluxnova-bpm-platform);
[13] [Licencias de Camunda 7](https://docs.camunda.org/manual/latest/introduction/licenses/);
[14] [EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms);
análisis independiente: [WZ-IT, 2026-10-15](https://wz-it.com/en/blog/camunda-7-end-of-life-operaton-cibseven-fluxnova-compared/).

<a id="timeline"></a>

## Cronología

<a id="camunda-7-milestones"></a>

### Hitos de Camunda 7

| Fecha | Hito | Fuente |
|---|---|---|
| 2024-03-06 | Camunda anuncia el fin de vida de Camunda 7 CE para octubre de 2025, con 7.24 como última versión de CE | [Foro de Camunda](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921) |
| 2025-02-28 | Camunda amplía el soporte de Camunda 7 Enterprise hasta 2030-04-09, con soporte extendido de pago hasta abril de 2032 | [Blog de Camunda](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) |
| 2025-10-07 | Se crea la etiqueta `7.24.0` y se publica en Maven Central: la última versión de CE | [etiqueta 7.24.0](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0), [Maven Central](https://repo1.maven.org/maven2/org/camunda/bpm/camunda-engine/7.24.0/) |
| 2025-10-14 | Fecha de publicación de 7.24 según el calendario de Camunda; después de esta fecha no hay más versiones de CE, incluidos parches de seguridad. Desde entonces, el repositorio de CE se ha archivado | [anuncio](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921), [página de soporte](https://docs.camunda.org/enterprise/announcement) |
| Abril de 2030 | Fin del soporte de Camunda 7 Enterprise (7.24 LTS: 2030-04-13) | [página de soporte](https://docs.camunda.org/enterprise/announcement) |
| Abril de 2032 | Fin del soporte extendido de pago de Camunda 7 Enterprise | [página de soporte](https://docs.camunda.org/enterprise/announcement) |

<a id="fasti-bpm"></a>

### Fasti BPM

Fasti continúa directamente desde la etiqueta `7.24.0`; todas las correcciones y cambios se contabilizan desde ese punto
(`git log 7.24.0..`). Las fases siguientes están ordenadas por el trabajo realizado, sin fechas:

1. **Base**: Java 17, Spring Boot 4.1, actualizaciones de bibliotecas; coordenadas de Maven
   `dev.fasti.platform`.
2. **Seguridad**: correcciones de los problemas divulgados públicamente de la línea 7.x, lista de expresiones permitidas,
   comprobaciones de adjuntos y comentarios dentro del motor, refuerzo de las aplicaciones web.
3. **Nueva interfaz web** en `/fasti/`, que sustituye gradualmente a las aplicaciones clásicas Cockpit, Tasklist y Admin.
4. **Inicio de sesión único** (OIDC) para Run, Spring Boot, Tomcat y WildFly.
5. **Uso directo sobre bases de datos existentes**: la imagen acepta las antiguas variables `DB_*` y comprueba la versión del esquema; probado en cinco sistemas de bases de datos.
6. **Revisión de seguridad previa al lanzamiento** (revisión de código, prueba de penetración de la imagen, análisis de referencia
   con ZAP; los problemas detectados se corrigieron) y **mitigación de Java4Shell**.
7. **1.0.0**: la primera versión (2026-11-20).

<a id="roadmap-plan"></a>

## Hoja de ruta (plan)

> Esto es un **plan**, no un compromiso; el orden puede cambiar según los comentarios de los usuarios.

- **Delegación por ausencia**: un suplente para un intervalo de fechas; las tareas nuevas se reasignan y el
  cambio queda registrado para auditoría ([foro 2788](https://forum.camunda.io/t/2788),
  [38199](https://forum.camunda.io/t/38199)).
- **Aprobación por dos personas**: quien apruebe un paso posterior no debe ser quien haya realizado uno
  anterior, con una comprobación obligatoria en el motor.
- **Chart de Helm** para Kubernetes.
- **Métricas y OpenTelemetry**: Micrometer/Prometheus nativos, trazas para procesos y trabajos.
- **Actualización de MyBatis** a una versión actual.
- **Líneas de soporte a largo plazo (LTS)** para usuarios que actualizan con poca frecuencia.
- **Verificación de DB2**.
- **Endpoint de agregación de carga de trabajo**, para que la página de carga de trabajo siga siendo rápida con grandes conjuntos de datos.

<a id="commitments-support-and-security-patches"></a>

## Compromisos: soporte y parches de seguridad

> El equipo del proyecto Fasti BPM mantiene esta política, que puede actualizarse; la versión de este
> repositorio es la vigente.

- **Línea con soporte:** la última versión menor de la línea `1.x` recibe correcciones de errores y parches
  de seguridad (como versiones de parche, por ejemplo `1.0.1`).
- **Parches de seguridad:** las vulnerabilidades de Fasti y de las bibliotecas que incluye tienen prioridad sobre
  otros trabajos; la imagen se vuelve a compilar cuando la imagen base o el JRE recibe una corrección. El proyecto sigue
  los avisos públicos de seguridad sobre Camunda 7 y sus bifurcaciones y comprueba si Fasti está afectado.
- **Compatibilidad:** la línea `1.x` conserva el esquema de base de datos de 7.24 y los paquetes `org.camunda.bpm.*`.
  Los cambios incompatibles se marcan como **BREAKING** en el [registro de cambios](CHANGELOG.md).
- **Nivel de soporte:** este es un proyecto comunitario; el soporte se ofrece en la medida de lo posible, **sin SLA**,
  y todavía no hay soporte comercial.
- **Notificación de vulnerabilidades:** **no** abra una incidencia pública; consulte
  [SECURITY.md](SECURITY.md#security-policy) (notificación privada a través de GitHub).
- **Defectos corregidos:** las secciones *Corregido* y *Seguridad* del [registro de cambios](CHANGELOG.md) enumeran
  lo que se ha corregido respecto a Camunda 7 CE 7.24.0; los avisos se enumeran en
  [SECURITY.md](SECURITY.md#advisories).

<a id="documentation"></a>

## Documentación

| Documento | Contenido |
|---|---|
| [docs/install-docker.md](docs/install-docker.es.md) | Instalación con Docker, paso a paso |
| [docs/api/README.md](docs/api/README.md) | Integración con la API REST, clientes de tareas externas para Java y JavaScript |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.es.md) | Migración desde Camunda 7, reversión, limitaciones |
| [distro/docker/README.md](distro/docker/README.md) | Imagen: variables de entorno, controladores, operaciones, ajustes |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | La nueva interfaz web |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell y Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy), [CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) | Cambios, seguridad, contribuciones |

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
[guía de migración](docs/migrate-from-camunda-7.es.md#what-differs) antes de actualizar.

<a id="versioning"></a>

## Versionado

Fasti BPM Platform sigue el [versionado semántico](https://semver.org/), a partir de `1.0.0`.
La línea `1.x` es compatible con las aplicaciones escritas para Camunda 7.24.

<a id="maven-coordinates"></a>

## Coordenadas de Maven

Los artefactos de Fasti BPM Platform tienen sus propias coordenadas de Maven. **Los paquetes Java
siguen siendo `org.camunda.bpm.*`**, por lo que el código de las aplicaciones conserva sus importaciones; el prefijo de configuración
`camunda.bpm.*`, los espacios de nombres BPMN/DMN, el esquema de base de datos y los nombres de los módulos en
WildFly tampoco cambian. En lo que respecta a Maven, solo cambian las declaraciones
de dependencias (la ruta de la aplicación web cambia por separado, consulte
[Ruta de la aplicación web](#web-application-path-fasti)):

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

<a id="web-application-path-fasti"></a>

## Ruta de la aplicación web: `/fasti`

Cockpit, Tasklist, Admin y la nueva interfaz web se sirven bajo `/fasti` (antes `/camunda`)
en todas las distribuciones: `http://localhost:8080/fasti/` abre la nueva interfaz
(`/fasti/app/next/default/`), las aplicaciones clásicas se encuentran en `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` y `/fasti/app/admin/...`. La API REST permanece en `/engine-rest`.

**Las aplicaciones clásicas están obsoletas.** Las aplicaciones clásicas Cockpit, Tasklist, Admin y Welcome
se basan en AngularJS 1.8.2 y Bootstrap 3, ambos fuera de soporte. **La imagen del contenedor no
las sirve de forma predeterminada** (cambio incompatible): sus páginas y los marcadores antiguos redirigen a la sección correspondiente de la nueva interfaz, y las solicitudes de sus archivos devuelven `404`; `FASTI_CLASSIC_WEBAPPS=true` las
reactiva (con una advertencia), consulte [distro/docker/README.md](distro/docker/README.md). Los paquetes
de Tomcat y WildFly tampoco las sirven de forma predeterminada (reactívelas con
`-Dfasti.webapp.classic.enabled=true` o `FASTI_CLASSIC_WEBAPPS=true`). El zip de Run y el starter de Spring
Boot siguen sirviéndolas como antes, para que las instalaciones en funcionamiento no dejen de funcionar; desactívelas
con `fasti.webapp.classic.enabled: false`. Las aplicaciones clásicas se eliminarán en una
versión posterior.

La ruta antigua sigue funcionando: todas las solicitudes a `/camunda/...` se redirigen a la misma ruta
bajo `/fasti/...`, conservando el resto de la ruta y la cadena de consulta. GET y HEAD reciben
`301`; todos los demás métodos (POST, PUT, DELETE, por ejemplo, clientes antiguos que llaman a `/camunda/api/...`)
reciben `308`, para que los clientes repitan la solicitud con el mismo método y cuerpo. Las redirecciones
incluyen `Cache-Control: no-store`, por lo que volver a `/camunda` surte efecto de inmediato.

Para restaurar `/camunda`:

- **Spring Boot, Run:** `camunda.bpm.webapp.application-path: /camunda` (en ese caso no hay redirección).
  Para desactivar solo la redirección: `fasti.webapp.legacy-path-redirect: false`.
- **Tomcat:** elimine `webapps/camunda` (la aplicación de redirección) y cambie el nombre de
  `webapps/fasti` a `webapps/camunda`.
- **WildFly:** elimine `standalone/deployments/camunda.war` (y `camunda.war.dodeploy`) y
  configure `<context-root>/camunda</context-root>` en `WEB-INF/jboss-web.xml` del WAR de la
  aplicación web.

**Proxies inversos:** si un proxy solo reenvía `/camunda/` (o reescribe rutas, configura rutas de cookies,
CSP o comprobaciones de estado para `/camunda`), añada `/fasti/` o migre por completo a `/fasti/`.
La cookie `XSRF-TOKEN` (y `JSESSIONID` en Tomcat y WildFly) ahora tiene la ruta `/fasti`,
por lo que los usuarios deben volver a iniciar sesión. Los marcadores y los enlaces antiguos siguen funcionando mediante la redirección.

<a id="single-sign-on-openid-connect"></a>

## Inicio de sesión único (OpenID Connect)

Compatible con **Run** (`./start.sh --oauth2`, módulo `internal/oauth2`) y con aplicaciones **Spring Boot**
que utilicen `fasti-bpm-spring-boot-starter-security`. Se activa declarando un
registro de cliente de Spring Security (`spring.security.oauth2.client.*`). Para **Tomcat** y
**WildFly**, consulte el final de esta sección.

- **Inicio de sesión:** la página de inicio de sesión de la nueva aplicación web muestra «Iniciar sesión con &lt;proveedor&gt;»
  en lugar del formulario de contraseña (etiqueta: `fasti.security.oidc.login-label`, cuyo valor predeterminado es
  `client-name`; si hay varios registros, elija uno con
  `fasti.security.oidc.registration-id`). El flujo de código de autorización utiliza `state`, `nonce`
  y PKCE (S256, también para clientes confidenciales); el identificador de sesión se sustituye al iniciar sesión. Los usuarios
  vuelven a la página de la nueva aplicación web desde la que iniciaron el proceso (solo se aceptan rutas bajo
  `/fasti/app/next/`, por lo que no hay redirecciones abiertas).
- **Cierre de sesión:** `POST /logout` (solo solicitudes del mismo origen) finaliza la sesión; con
  `camunda.bpm.oauth2.sso-logout.enabled=true`, el usuario continúa al endpoint de finalización
  de sesión del proveedor (cierre de sesión iniciado por el RP, `post-logout-redirect-uri`). El cierre de sesión
  por canal posterior de Spring Security sigue activado. `GET /logout` ya no cierra la sesión del usuario.
- **Caducidad de la sesión:** cuando no se puede renovar el token de acceso o la sesión caduca,
  las solicitudes a `/fasti/api/**` devuelven `401` (sin redirección) y la nueva aplicación web muestra su página de inicio de sesión.
- **CSRF:** `/fasti/api` y `/fasti/app` conservan el filtro CSRF propio de las aplicaciones web
  (`X-XSRF-TOKEN`); la protección CSRF de Spring Security permanece desactivada, como en el proyecto original.
- **Grupos, administradores, inquilinos:** el atributo de identidad de grupos se configura con
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` (nombre de propiedad del proyecto original; se admiten
  atributos anidados como `realm_access.roles`). `fasti.security.oidc.groups.admin` enumera los
  valores de atributo que conceden `camunda-admin`; `groups.mapping` asigna valores de atributo a identificadores de grupo
  del motor; `groups.include-unmapped=false` descarta los valores sin asignación. **Cambio respecto al proyecto original:** un
  valor de atributo `camunda-admin` ya no concede derechos de administrador por sí solo, y
  las autoridades como `SCOPE_openid` u `OIDC_USER` ya no se convierten en grupos.
  `fasti.security.oidc.tenants.claim` asigna los inquilinos. Nombre y correo electrónico:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Proveedor de identidad:** de forma predeterminada, el motor lee los usuarios, grupos e inquilinos del
  token de la sesión actual (solo lectura). Configure
  `camunda.bpm.oauth2.identity-provider.enabled=false` para utilizar la base de datos en su lugar (los grupos
  procederán entonces de las tablas de pertenencia). El aprovisionamiento automático no está implementado.
  Mientras los usuarios procedan del token (el valor predeterminado), la página de configuración inicial (primer administrador)
  permanece desactivada, ya que los administradores proceden de los atributos de identidad (`groups.admin`); antes, los usuarios ajenos al grupo de administradores eran enviados a la página de configuración.
- **API REST con tokens de portador:** `fasti.security.oidc.rest.bearer-enabled=true` acepta
  `Authorization: Bearer <JWT>` en `/engine-rest`, además de la autenticación básica.
  `rest.issuer-uri` y `rest.audiences` son obligatorios; las firmas se comprueban con el
  JWKS del emisor utilizando únicamente algoritmos asimétricos (`rest.jws-algorithms`, valor predeterminado `RS256`), y
  se validan `iss`, `aud`, `exp`, `nbf`. Run también necesita `camunda.bpm.run.auth.enabled=true`.
  Una cookie de sesión del navegador nunca autentica `/engine-rest`.

Ejemplo de Keycloak (`configuration/default.yml` de Run):

```yaml
spring.security.oauth2.client:
  registration.keycloak:
    client-id: fasti
    client-secret: ${KEYCLOAK_CLIENT_SECRET}
    client-name: Keycloak
    scope: openid, profile, email
  provider.keycloak:
    issuer-uri: https://sso.example.com/realms/fasti
    user-name-attribute: preferred_username
camunda.bpm:
  oauth2:
    # the client's "Group Membership" mapper, with "Full group path" off
    identity-provider.group-name-attribute: groups
    sso-logout:
      enabled: true
      post-logout-redirect-uri: "{baseUrl}/fasti/app/next/default/"
  run.auth.enabled: true
fasti.security.oidc:
  groups.admin: [fasti-admins]
  rest:
    bearer-enabled: true
    issuer-uri: https://sso.example.com/realms/fasti
    audiences: [fasti-api]          # "Audience" mapper in Keycloak
    user-id-claim: preferred_username
```

Ejemplo de Microsoft Entra ID:

```yaml
spring.security.oauth2.client:
  registration.entra:
    client-id: <application-id>
    client-secret: ${ENTRA_CLIENT_SECRET}
    client-name: Microsoft
    scope: openid, profile, email
  provider.entra:
    issuer-uri: https://login.microsoftonline.com/<tenant-id>/v2.0
    user-name-attribute: preferred_username
camunda.bpm.oauth2:
  # app registration manifest: "groupMembershipClaims": "SecurityGroup"
  identity-provider.group-name-attribute: groups
  sso-logout.enabled: true
fasti.security.oidc:
  groups:
    admin: ["<object id of the administrators group>"]
    mapping:
      "<object id of the accounting group>": accounting
    include-unmapped: false          # Entra sends object ids, not group names
  rest:
    bearer-enabled: true
    issuer-uri: https://login.microsoftonline.com/<tenant-id>/v2.0
    audiences: ["api://<application-id>"]   # v2 access tokens: accessTokenAcceptedVersion = 2
    user-id-claim: preferred_username
```

**Tomcat, WildFly (WAR):** las mismas funciones, configuradas en `fasti-oidc.properties` (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; junto a él hay un archivo `fasti-oidc.properties.example`
con anotaciones). Sin este archivo no cambia nada. Las claves coinciden con las propiedades `fasti.security.oidc.*` de Run, además de `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`. El
proveedor debe permitir el URI de redirección `<base>/fasti/sso/callback`; la URL de cierre de sesión por canal posterior es
`<base>/fasti/sso/backchannel-logout`. Los tokens de portador en `/engine-rest` se activan con
`fasti.security.oidc.rest.bearer-enabled=true` (la autenticación básica sigue funcionando salvo que se configure
`rest.basic-enabled=false`; la lista de motores `/engine` también requiere credenciales). Los usuarios no tienen que
existir en la base de datos. Para que el motor responda a consultas sobre los grupos e inquilinos del
usuario que ha iniciado sesión a partir de sus atributos de identidad, active `OidcIdentityProviderPlugin` (comentado en
`bpm-platform.xml` / `standalone.xml`). Limitaciones: las sesiones registradas para el cierre de sesión por canal posterior se
conservan en memoria por servidor (no se comparten entre los nodos del clúster); las aplicaciones clásicas solo redirigen al proveedor; un cierre de sesión completo requiere la nueva interfaz web.

<a id="build"></a>

## Compilación

Requiere JDK 17 o posterior (se recomienda 21).

```bash
./mvnw clean verify
```

Una compilación completa ejecuta todas las pruebas de Java y las pruebas unitarias de la interfaz web (cerca de una hora; las pruebas
de extremo a extremo de Playwright se ejecutan por separado, consulte [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)) y necesita acceso de red a Maven Central,
nodejs.org y el registro npm. Consulte [TESTING.md](TESTING.md).

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
