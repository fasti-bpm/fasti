[English](migrate-from-camunda-7.md) | [Tiếng Việt](migrate-from-camunda-7.vi.md) | [Deutsch](migrate-from-camunda-7.de.md) | Español | [Français](migrate-from-camunda-7.fr.md) | [हिन्दी](migrate-from-camunda-7.hi.md) | [Bahasa Indonesia](migrate-from-camunda-7.id.md) | [日本語](migrate-from-camunda-7.ja.md) | [한국어](migrate-from-camunda-7.ko.md) | [Português (Brasil)](migrate-from-camunda-7.pt-BR.md) | [Русский](migrate-from-camunda-7.ru.md) | [ไทย](migrate-from-camunda-7.th.md) | [简体中文](migrate-from-camunda-7.zh-CN.md)

> Esta traducción la mantiene la comunidad y puede quedar por detrás de la versión en inglés; la versión en inglés es la de referencia. Las correcciones son bienvenidas.

# Migración de Camunda 7 a Fasti BPM

Fasti BPM está basado en Camunda 7 Community Edition 7.24.0 y es compatible con las bases de datos de Camunda 7.
Es un proyecto independiente y no está afiliado a Camunda Services GmbH.

<a id="summary"></a>

## Resumen

Si utiliza Camunda 7 **7.24** (Run, la imagen de contenedor, el starter de Spring Boot, Tomcat o
WildFly), Fasti funciona con **esa misma base de datos sin modificar su esquema**: utiliza el esquema de 7.24
sin cambios y no añade tablas, columnas ni versiones de esquema propias. Puede sustituir
los nodos antiguos, ejecutar Fasti junto a ellos y volver a Camunda 7.24 (consulte
[Reversión a Camunda 7.24](#rollback-to-camunda-724) y las bases de datos probadas en [Limitaciones](#limits)).

Las bases de datos de Camunda 7.23 y versiones anteriores deben actualizarse primero a 7.24 mediante los scripts SQL (consulte
[Versiones anteriores](#older-versions-717723)). Fasti nunca actualiza un esquema por sí mismo; se niega a
iniciarse con un esquema anterior e indica qué scripts debe ejecutar.

<a id="steps-container-image"></a>

## Pasos (imagen de contenedor)

1. **Haga una copia de seguridad de la base de datos.** Ninguno de los pasos siguientes modifica el esquema, pero es obligatorio
   realizar una copia de seguridad antes de cualquier cambio en un sistema de producción.
2. **Compruebe la versión del esquema:** `select VERSION_ from ACT_GE_SCHEMA_LOG` — el valor más alto
   debe ser `7.24.x`.
3. **Anote la configuración actual** del nodo antiguo: variables `DB_*`, nivel de historial
   (`camunda.bpm.history-level`, `ACT_GE_PROPERTY.historyLevel`), autenticación REST
   (`camunda.bpm.run.auth.enabled`), autorizaciones (`camunda.bpm.authorization.enabled`),
   plugins, LDAP/SSO, zona horaria de la JVM, hash de contraseñas (`passwordEncryptor`, `saltGenerator`),
   prefijo de tablas/esquema.
4. **Descargue la imagen** `fastidev/fasti-bpm:1.3.2` (o compílela desde el código fuente como se
   describe en [distro/docker/README.md](../distro/docker/README.md)).
5. **Inicie Fasti con las mismas variables `DB_*`** (y la misma `TZ`). A diferencia de Camunda Run, la
   imagen de Fasti **exige autenticación REST y aplica las autorizaciones de forma predeterminada**: las cuentas de la base de datos
   siguen funcionando, pero todos los clientes REST deben enviar credenciales Basic o un token de portador,
   y los usuarios que no pertenecen a `camunda-admin` necesitan permisos en `ACT_RU_AUTHORIZATION` (consulte «Seguridad de forma
   predeterminada» en [distro/docker/README.md](../distro/docker/README.md)).
   `FASTI_INSECURE_DEMO=true` es solo para demostraciones. Fasti registra
   `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
6. **Compruebe lo siguiente:** inicie sesión en `/fasti/` con una cuenta existente; compruebe que los filtros, las tareas, las instancias y el historial estén presentes; invoque `GET /engine-rest/task` (con credenciales Basic) y `GET /actuator/health/readiness`.
7. **Dirija el tráfico** (balanceador de carga, DNS) a Fasti; detenga los nodos antiguos o manténgalos en ejecución (consulte
   [Funcionamiento mixto](#mixed-operation-with-camunda-7-nodes)).

Starter de Spring Boot / Tomcat / WildFly: sustituya los artefactos `org.camunda.bpm:camunda-*` por
los correspondientes `dev.fasti.platform:fasti-*` y conserve `camunda.bpm.*`, `bpm-platform.xml` y
los paquetes `org.camunda.bpm.*`. No mantenga los archivos jar de ambos motores en el classpath; en ese caso, Fasti se niega a iniciarse (para conocer las limitaciones de esta comprobación, consulte «Comprobación de inicio» en el
[embedding.es.md](embedding.es.md#startup-check)).

> **Starter de Spring Boot:** Fasti funciona con **Spring Boot 4.1** y necesita **Java 17 o posterior**.
> Las aplicaciones que utilizan el starter de Camunda 7.24 (Spring Boot 3.5) deben migrar a Spring Boot 4.1 y
> Java 17+ al mismo tiempo que cambian los artefactos. Consulte el
> [embedding.es.md](embedding.es.md#what-is-different-from-camunda-7240-ce) y el
> [registro de cambios](../CHANGELOG.md#changed).

<a id="tomcat-and-wildfly-distributions"></a>

## Distribuciones de Tomcat y WildFly

> **Obligatorio al hacerse cargo de la base de datos de Camunda Run** (o de un servidor cuyos procesos se
> desplegaron mediante la API REST): las distribuciones de Tomcat y WildFly mantienen
> `jobExecutorDeploymentAware=true`, como Camunda 7. Los trabajos (temporizadores, continuaciones asíncronas, reintentos)
> de los despliegues que no están registrados en el nodo **no se ejecutan y no se informa de ningún error**.
> Antes de iniciar, establezca `<property name="jobExecutorDeploymentAware">false</property>` en
> `conf/bpm-platform.xml` (Tomcat) o en las propiedades del motor de `standalone.xml` (WildFly).
> Mantenga `true` si otros nodos ejecutan aplicaciones de procesos con clases Java propias (consulte
> [Funcionamiento mixto](#mixed-operation-with-camunda-7-nodes)).

En comparación con las distribuciones de Tomcat/WildFly de Camunda 7.24:

- **La API REST exige autenticación** (cambio incompatible). `/engine-rest` tiene activado el filtro `camunda-auth`
  (HTTP Basic): todos los clientes REST deben enviar el nombre de usuario y la contraseña de un usuario del motor; de lo contrario, reciben un 401; solo la lista de motores (`/engine`) sigue siendo de acceso público.
  Configure las credenciales en los clientes REST, los workers de tareas externas y los scripts que invocan la API
  REST de forma anónima antes de realizar el cambio. Los tokens de portador de OpenID Connect (`fasti-oidc.properties`,
  `fasti.security.oidc.rest.bearer-enabled=true`) también funcionan: las solicitudes con un token válido no necesitan contraseña. Para desactivar la autenticación (**no recomendado**), elimine el
  filtro `camunda-auth` y su `filter-mapping` de `WEB-INF/web.xml` del WAR de `engine-rest`.
- **No hay usuarios predeterminados** (cambio incompatible). El ejemplo de facturación ya no se despliega, por lo que no
  crea `demo/demo` (administrador), `john`, `mary` ni `peter`; su WAR es
  `examples/camunda-invoice.war`, que debe copiarse a `webapps/` (Tomcat) o
  `standalone/deployments/` (WildFly) para probarlo. Con una base de datos vacía, abra `/fasti/`
  y cree el primer administrador en la página de configuración; con una base de datos de Camunda 7, utilice las
  cuentas existentes.
- **Las aplicaciones clásicas Cockpit, Tasklist, Admin y Welcome no se sirven** (cambio incompatible), al igual que en la
  imagen: sus páginas y marcadores llevan a la sección correspondiente de la nueva interfaz, y las solicitudes de sus archivos devuelven 404. Para volver a habilitarlas temporalmente (obsoleto, se eliminará), utilice una de estas opciones:
  `-Dfasti.webapp.classic.enabled=true` en `CATALINA_OPTS` (Tomcat) o `JAVA_OPTS` (WildFly), la
  variable de entorno `FASTI_CLASSIC_WEBAPPS=true` o el parámetro de contexto
  `fasti.webapp.classic.enabled` en `WEB-INF/web.xml` de la aplicación web.

<a id="what-is-identical"></a>

## Qué se mantiene igual

- El esquema y las filas de `ACT_GE_PROPERTY`/`ACT_GE_SCHEMA_LOG` (Fasti no añade ninguna fila de versión),
  el identificador de instalación y la generación de identificadores (`StrongUuidGenerator` en Run/Spring Boot, el
  `DbIdGenerator` predeterminado del motor).
- Contraseñas: SHA-512 con una sal de 16 bytes, como Camunda 7; los usuarios existentes pueden iniciar sesión, y
  las contraseñas modificadas en Fasti funcionan en Camunda 7.
- La API REST en `/engine-rest`: los mismos endpoints, parámetros y cuerpos, más endpoints
  adicionales (la autenticación y algunos códigos de estado difieren, consulte [Qué cambia](#what-differs)); las tareas externas
  (`fetchAndLock`, sondeo prolongado), los clientes Java y JS, `XSRF-TOKEN`/`X-XSRF-TOKEN`, las propiedades
  `camunda.bpm.*` y el espacio de nombres BPMN `camunda:`. La cookie de sesión se llama `JSESSIONID` en las distribuciones Tomcat y WildFly; Run y la imagen de contenedor la llaman `FASTI_SESSION` desde la 1.3.2 (`server.servlet.session.cookie.name`), para que no choque con Camunda 7 Run en el mismo host. Los scripts que buscan la cookie por su nombre necesitan el nombre nuevo.
- Los filtros de la Tasklist clásica (`color`, `description`, `priority`, `refresh`, `variables`,
  `showUndefinedVariable`) se leen y escriben en el mismo formato en la nueva.

<a id="what-differs"></a>

## Qué cambia

- Las aplicaciones web se encuentran en `/fasti/app/...`; `/camunda/...` redirige allí (301/308). Si su proxy solo expone `/camunda`, exponga también `/fasti` o establezca `FASTI_WEBAPP_PATH=/camunda`. La raíz
  abre la nueva aplicación web (`fasti.webapp.default-app: welcome` restaura Welcome, excepto
  en la imagen, donde las aplicaciones clásicas están desactivadas).
- **Cambio incompatible, imagen de contenedor:** las aplicaciones clásicas Cockpit, Tasklist, Admin y Welcome no se
  sirven porque AngularJS 1.8.2 y Bootstrap 3 han llegado al final de su vida útil. Sus páginas y marcadores,
  también bajo `/camunda/app/`, redirigen a la sección correspondiente de la nueva interfaz web (`#/cockpit`,
  `#/tasklist`, `#/admin`); las solicitudes de sus archivos devuelven 404. Traslade a los usuarios a la nueva interfaz web;
  `FASTI_CLASSIC_WEBAPPS=true` vuelve a servir las aplicaciones clásicas durante un período de transición, con una
  advertencia. Tomcat y WildFly tampoco las sirven (consulte
  [Distribuciones de Tomcat y WildFly](#tomcat-and-wildfly-distributions)). El zip de Run y el
  starter de Spring Boot todavía las sirven, pero están obsoletas y se eliminarán en una
  versión posterior (`fasti.webapp.classic.enabled: false` las desactiva anticipadamente).
- Con un esquema anterior, Fasti se niega a iniciarse con `ENGINE-03111` en lugar de fallar más tarde
  (`FASTI_SCHEMA_VERSION_CHECK=warn` para iniciarse de todos modos).
- Las expresiones en consultas y filtros se limitan a una lista de elementos permitidos (funciones de fecha, usuario
  actual); los filtros que utilizan beans o funciones personalizados necesitan que estos se declaren en las propiedades de configuración del motor
  `allowedExpressionFunctionsInQueries`, `allowedExpressionBeansInQueries` y
  `allowedExpressionPropertiesInQueries`.
- Comportamiento de la API REST: en la imagen de contenedor y en las distribuciones de Tomcat y
  WildFly, todas las solicitudes, salvo la lista de motores (`/engine`), necesitan credenciales Basic
  o un bearer token, por lo que los clientes REST y los workers de tareas externas deben configurarse
  para enviarlas (su código no cambia). En todas las distribuciones, las expresiones de consulta
  fuera de la lista permitida se rechazan con `400`; leer o modificar adjuntos y comentarios de
  tareas sin permiso se responde con `403` (con las comprobaciones de autorización activadas);
  algunas solicitudes no válidas que se respondían con `500` se responden ahora con `400` (recursos
  de despliegue que no se pueden analizar, variables en el formato de serialización Java prohibido,
  parámetros de consulta del tipo incorrecto, contraseñas que incumplen la política de contraseñas).
  Consulte [Differences from Camunda 7](api/README.md#differences-from-camunda-7) (en inglés).
- Valores predeterminados de la imagen: actualización del esquema en `false` (`FASTI_DB_SCHEMA_UPDATE=true` para una base de datos nueva); sin
  usuario de demostración, ejemplo de facturación ni filtro «Todas las tareas»; CORS desactivado (`FASTI_CORS_ALLOWED_ORIGINS=*`
  lo restaura); se añade `/actuator/health`; archivos bajo `/fasti` (también se leen los archivos montados en
  `/camunda/configuration`); sin `--swaggerui`.
- OAuth2/OIDC: `camunda-admin` en una claim de grupo ya no concede derechos de administrador por sí solo
  (`fasti.security.oidc.groups.admin`); `group-name-delimiter` es texto literal, no una expresión regular;
  el cierre de sesión requiere un POST del mismo origen.
- Nueva Tasklist: un filtro sin `refresh` se actualiza automáticamente.
- El jar de Run es `fasti-bpm-run-core.jar`.
- Las correcciones de seguridad respecto a 7.24.0 (autorización de adjuntos/comentarios, configuración inicial, ...) pueden hacer que algunas llamadas que antes funcionaban devuelvan 403; consulte las secciones *Security* del [CHANGELOG](../CHANGELOG.md) y <https://fasti.dev/security>.

<a id="rollback-to-camunda-724"></a>

## Reversión a Camunda 7.24

Detenga Fasti y vuelva a iniciar los nodos de Camunda 7.24 con la misma base de datos. Todo lo realizado en
Fasti será visible allí. Lo que permanece: las filas `fasti.prefs.*` en `ACT_ID_INFO` (preferencias de la
nueva interfaz web, que Camunda 7 ignora) y `refresh: true` en los filtros guardados por la nueva Tasklist donde
antes no estaba presente. Los procesos desplegados que utilizan funciones exclusivas de Fasti no se ejecutan en Camunda 7.

<a id="mixed-operation-with-camunda-7-nodes"></a>

## Funcionamiento mixto con nodos de Camunda 7

Fasti y Camunda 7.24 pueden ejecutarse con una misma base de datos al mismo tiempo (comprobado: ambos ven los
datos del otro y los trabajos se ejecutan en cualquiera de los nodos). Requisitos:

- el **mismo nivel de historial** (la imagen utiliza el nivel almacenado), la **misma zona horaria de la JVM**, la
  misma configuración de contraseñas y el mismo prefijo de tablas;
- **ejecutor de trabajos:** de forma predeterminada, Fasti (imagen, Run, Spring Boot) recoge los trabajos de todos los despliegues, como hace Run.
  Las distribuciones de Tomcat y WildFly utilizan de forma predeterminada `jobExecutorDeploymentAware=true` (como Camunda
  7): los trabajos (temporizadores, continuaciones asíncronas, reintentos) de los despliegues no registrados en ese
  nodo, incluidos los despliegues realizados por Run o mediante REST antes del último reinicio, **no se
  ejecutan**; al hacerse cargo de la base de datos de Run, establezca el valor en `false` en `conf/bpm-platform.xml`
  (Tomcat) o `standalone.xml` (WildFly). Si los nodos antiguos ejecutan aplicaciones de procesos con clases que Fasti no tiene (`JavaDelegate`, listeners, beans),
  Fasti recogería sus trabajos, que fallarían y generarían incidentes. En ese caso, establezca
  `FASTI_JOB_EXECUTOR_ENABLED=false` en Fasti o configure **todos** los nodos para que tengan en cuenta los despliegues (el
  ejecutor de trabajos de un nodo solo ejecuta trabajos de los despliegues registrados en ese nodo; un despliegue realizado mediante
  REST solo se registra en el nodo que lo recibió, hasta que se reinicia). Las llamadas REST síncronas (completar una tarea,
  iniciar una instancia) siguen ejecutando los delegados en el nodo que las recibe — no dirija esos
  procesos a Fasti;
- **balanceador de carga:** sesiones persistentes para las aplicaciones web; las llamadas REST con autenticación Basic no las necesitan;
- las aplicaciones web clásicas (en los nodos de Camunda 7 o en Fasti con
  `FASTI_CLASSIC_WEBAPPS=true`) y la nueva comparten filtros y usuarios.

<a id="older-versions-717723"></a>

## Versiones anteriores (7.17–7.23)

Haga una copia de seguridad; ejecute `sql/upgrade/<db>_engine_<from>_to_<to>.sql` en orden hasta llegar a 7.24, incluidos
los scripts de parches pendientes de su versión menor actual (incluidos en `configuration/sql/upgrade`, en la
imagen `/fasti/configuration/sql/upgrade`); compruebe que `ACT_GE_SCHEMA_LOG` contenga `7.24.0`;
inicie Fasti. Ejemplo 7.22 → 7.24: `postgres_engine_7.22_to_7.23.sql`, después
`postgres_engine_7.23_to_7.24.sql`. Los nodos de Camunda anteriores a 7.24 no deben seguir ejecutándose con el
esquema actualizado salvo dentro de las reglas de actualización gradual de Camunda (solo una versión menor, y los
nodos antiguos no deben utilizar funciones nuevas).

<a id="performance"></a>

## Rendimiento

La imagen ya no usa los valores predeterminados de Camunda Run para el ejecutor de trabajos y el
pool de conexiones (hasta Fasti 1.1 sí lo hacía): usa 10 hilos de trabajo, una cola de 10 y 10
trabajos por adquisición (Camunda Run: 3 hilos principales, máximo 10, cola de 3, 3 por adquisición)
y un pool de hasta 20 conexiones, de las que 10 permanecen abiertas en reposo (Camunda Run: 10).
Dimensione el límite de conexiones de la base de datos para los pools de todos los nodos juntos,
incluidos los nodos de Camunda 7 ([ajuste en tiempo de ejecución](tuning-runtime.md), en inglés). Si
ajustó el pool de conexiones o el ejecutor de trabajos de su instalación anterior de Camunda 7,
conserve esos valores (`FASTI_DB_CONN_MAXACTIVE`, `FASTI_DB_CONN_MINIDLE`,
`CAMUNDA_BPM_JOB_EXECUTION_*`); encontrará más indicaciones en `distro/docker/README.md`, «Ajuste
del rendimiento». Fasti nunca modifica por sí mismo los índices del esquema; para bases de datos
grandes incluye índices opcionales como scripts que ejecuta un administrador
(`/fasti/configuration/sql/optional/<database>_fasti_perf_indexes_create.sql`, véase
[performance.md](performance.md)), seguros junto a nodos de Camunda 7.24 y que se eliminan con el
`_drop.sql` correspondiente.

<a id="limits"></a>

## Limitaciones

- Solo se admite el esquema de Community Edition 7.24; los esquemas de parches de Enterprise no se han probado.
- Probado con la imagen de contenedor (Camunda Run 7.24.0 crea y carga los datos iniciales de la base de datos → Fasti
  toma el relevo → vuelta a Run → ambos a la vez):

  | Base de datos | Controlador | Toma del relevo | Reversión | Funcionamiento mixto | Actualización 7.23 → 7.24 |
  |---|---|---|---|---|---|
  | PostgreSQL 16 | incluido | correcto | correcto | correcto | sin probar |
  | MariaDB 11.8 | MariaDB Connector/J (incluido) o MySQL Connector/J | correcto | correcto | correcto | correcto |
  | MySQL 8.4 | incluido | correcto | — | correcto | correcto |
  | SQL Server 2022 (Developer) | incluido (`encrypt=false`) | correcto | correcto | correcto | sin probar |
  | Oracle Database 23ai Free | `ojdbc11` mediante `FASTI_EXTRA_LIB` | correcto | correcto | correcto | sin probar |
  | Db2 12.1 (Community Edition) | `com.ibm.db2:jcc` 12.1.2.0 mediante `FASTI_EXTRA_LIB` | correcto | correcto | correcto | correcto (solo el esquema) |

  Db2: ejecute `db2set DB2_EVALUNCOMMITTED=ON DB2_SKIPINSERTED=ON DB2_SKIPDELETED=ON` y reinicie
  la instancia. Sin ellas, los comandos concurrentes entran en interbloqueo (`SQL0911N`) en una
  sentencia del motor (`update ACT_HI_TASKINST set REMOVAL_TIME_ … where ROOT_PROC_INST_ID_ = ?`),
  también en los nodos de Camunda 7.24, y sobre todo en una base de datos nueva, todavía vacía (en
  la prueba, la mayoría de las finalizaciones concurrentes de tareas fallaron allí). Probado en
  Db2 12.1 Community Edition con páginas de 4 K, juego de códigos UTF-8, aislamiento CS y
  `CUR_COMMIT=ON`; también las pruebas del motor de Fasti y 15 paquetes del conjunto de pruebas del
  motor. `LOCKTIMEOUT` es -1 de forma predeterminada (la espera de un bloqueo no termina nunca):
  establezca un valor. El controlador (`com.ibm.db2:jcc`) no se incluye; añádalo con
  `FASTI_EXTRA_LIB`. Sin probar: Db2 11.5, Db2 para z/OS o IBM i, otros tamaños de página, una
  actualización de una base de datos 7.23 que contiene datos.

  Además: dos nodos de Fasti en MariaDB (MariaDB Connector/J) que completaban las mismas tareas y obtenían tareas externas en paralelo no perdieron ni duplicaron nada; la transacción que no pudo completarse recibió un error de bloqueo optimista. MySQL/MariaDB necesitan `transaction-isolation=READ-COMMITTED`. En MySQL,
  asigne a cada motor su propia base de datos: el motor (tanto Camunda Run como Fasti) busca tablas
  en todas las bases de datos que la cuenta puede ver.
- Los plugins y las aplicaciones de procesos creados para Camunda 7 deben recompilarse con los
  artefactos de Fasti para ejecutarse dentro de Fasti. Los clientes REST y los clientes de tareas externas no necesitan cambios en el código;
  con la imagen de contenedor, Tomcat o WildFly deben configurarse para enviar credenciales Basic
  o un bearer token.
- La comprobación del esquema solo lee `ACT_GE_SCHEMA_LOG`; no compara columnas.
