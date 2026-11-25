[English](install-docker.md) | [Tiếng Việt](install-docker.vi.md) | [Deutsch](install-docker.de.md) | Español | [Français](install-docker.fr.md) | [हिन्दी](install-docker.hi.md) | [Bahasa Indonesia](install-docker.id.md) | [日本語](install-docker.ja.md) | [한국어](install-docker.ko.md) | [Português (Brasil)](install-docker.pt-BR.md) | [Русский](install-docker.ru.md) | [ไทย](install-docker.th.md) | [简体中文](install-docker.zh-CN.md)

> Esta traducción la mantiene la comunidad y puede quedar por detrás de la versión en inglés; la versión en inglés es la de referencia. Las correcciones son bienvenidas.

# Instalación de Fasti BPM con Docker

Una guía paso a paso: prueba rápida con H2, producción con PostgreSQL (`docker run` o
`docker compose`), conexión a una base de datos existente de Camunda 7, ejecución en paralelo, comprobaciones
de estado, actualización, reversión y resolución de problemas. Referencia completa de la imagen:
[distro/docker/README.md](../distro/docker/README.md). Fasti BPM está basado en Camunda 7 CE
7.24.0 y es un proyecto independiente, no afiliado a Camunda Services GmbH.

<a id="1-requirements"></a>

## 1. Requisitos

- Docker Engine con Docker Compose v2 (`docker compose`).
- Para cargas de trabajo reales: al menos 2 CPU y 2 GB de memoria para el contenedor de Fasti (por debajo de ese umbral, la
  JVM selecciona Serial GC).
- Base de datos de producción: PostgreSQL, MariaDB, MySQL, SQL Server u Oracle (consulte la
  [matriz de pruebas](migrate-from-camunda-7.es.md#limits)). DB2 no está verificado. H2 es solo para evaluación.

<a id="2-pull-the-image"></a>

## 2. Descargar la imagen

<!-- update with the Docker Hub image name at release -->
La imagen se publica para amd64 y arm64 en dos ubicaciones:

```bash
docker pull fastidev/fasti-bpm:1.3.0              # Docker Hub
docker pull ghcr.io/fasti-bpm/fasti-bpm:1.3.0     # GitHub Container Registry
```

Etiquetas: `1.3.0` (exactamente esa versión), `1.3` (última versión de parche de 1.3) y `latest` (la
versión publicada más reciente). La imagen está firmada con cosign (sin claves, GitHub OIDC) e incluye
una SBOM y una atestación de procedencia. A partir de 1.0.1 la firma usa el formato de bundle de
Sigstore de cosign 3; la imagen 1.0.0 lleva una firma en el formato anterior de cosign 2.

También puede compilar la imagen a partir del código fuente (requiere JDK 17+ y acceso de red a Maven
Central, nodejs.org y al registro de npm) y usar el nombre `fasti-bpm:local` en lugar de
`fastidev/fasti-bpm:1.3.0` en todos los comandos siguientes:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="3-quick-trial-with-h2"></a>

## 3. Prueba rápida con H2

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  -v fasti-data:/fasti/data \
  fastidev/fasti-bpm:1.3.0
```

- Abra <http://localhost:8080/fasti/> e inicie sesión como `admin`.
- Sin `DB_URL`, la imagen usa H2 en `/fasti/data`; el volumen `fasti-data` conserva los datos
  cuando se vuelve a crear el contenedor. Solo para evaluación, nunca para datos reales.
- Añada `--webapps --rest --example` al comando para obtener también un proceso de ejemplo (factura).
  `--example` por sí solo deja desactivadas la interfaz web y la API REST.
- La API REST está en `http://localhost:8080/engine-rest` y requiere credenciales HTTP Basic; consulte la
  [guía de la API REST](api/README.md).

<a id="4-production-with-postgresql-docker-run"></a>

## 4. Producción con PostgreSQL: `docker run`

Cree dos archivos de secretos (contraseña de la base de datos y contraseña del administrador). Con `--production`, el motor
aplica su política de contraseñas: al menos 10 caracteres con letras mayúsculas y minúsculas, un dígito
y un carácter especial.

```bash
mkdir -p secrets
openssl rand -hex 24 > secrets/db-password.txt
echo "Fasti-1-$(openssl rand -hex 12)" > secrets/admin-password.txt
chmod 0700 secrets           # other users on the host cannot enter the directory
chmod 0644 secrets/*.txt     # the containers read them as uid 999 (PostgreSQL) and 1000 (Fasti)
```

```bash
docker network create fasti-net

docker run -d --name fasti-db --network fasti-net \
  -e POSTGRES_DB=fasti -e POSTGRES_USER=fasti \
  -e POSTGRES_PASSWORD_FILE=/run/secrets/db-password \
  -v "$PWD/secrets/db-password.txt:/run/secrets/db-password:ro" \
  -v fasti-pgdata:/var/lib/postgresql/data \
  postgres:16

docker run -d --name fasti --network fasti-net -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://fasti-db:5432/fasti \
  -e DB_USERNAME=fasti -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v "$PWD/secrets/db-password.txt:/run/secrets/db-password:ro" \
  -e WAIT_FOR=fasti-db:5432 -e WAIT_FOR_TIMEOUT=60 \
  -e FASTI_DB_SCHEMA_UPDATE=true \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD_FILE=/run/secrets/admin-password \
  -v "$PWD/secrets/admin-password.txt:/run/secrets/admin-password:ro" \
  -e TZ=UTC \
  --memory 2g --stop-timeout 60 \
  fastidev/fasti-bpm:1.3.0 --production
```

- `FASTI_DB_SCHEMA_UPDATE=true` solo crea las tablas que faltan en una base de datos nueva; Fasti nunca
  actualiza un esquema existente. Puede mantenerse configurado en los arranques posteriores.
- `--production` activa las cookies `Secure` y rechaza una base de datos H2 implícita y `DEBUG=true`.
  Termine TLS en un proxy inverso delante de Fasti; los navegadores envían las cookies `Secure` solo por HTTPS (o a
  `localhost`).
- `WAIT_FOR` retrasa el arranque hasta que la base de datos acepte conexiones.
- La contraseña del administrador solo se establece si este aún no existe; cámbiela más adelante en Administración.

<a id="5-production-with-postgresql-docker-compose"></a>

## 5. Producción con PostgreSQL: `docker compose`

Archivo de ejemplo: [distro/docker/examples/docker-compose.postgres.yml](../distro/docker/examples/docker-compose.postgres.yml)
(PostgreSQL 16, secretos, comprobaciones de estado, `--production`, límite de memoria de 2 GB).

```bash
cd distro/docker/examples          # or copy the file to a directory of its own
mkdir -p secrets
openssl rand -hex 24 > secrets/db-password.txt
echo "Fasti-1-$(openssl rand -hex 12)" > secrets/admin-password.txt
chmod 0700 secrets
chmod 0644 secrets/*.txt
docker compose -f docker-compose.postgres.yml up -d
docker compose -f docker-compose.postgres.yml ps      # both services "healthy"
```

Variables que puede configurar al arrancar: `FASTI_IMAGE` (valor predeterminado: `fastidev/fasti-bpm:1.3.0`, por
ejemplo `FASTI_IMAGE=fasti-bpm:local`), `FASTI_HOST_PORT` (valor predeterminado: `8080`), `TZ` (valor predeterminado: `UTC`).

<a id="6-connecting-to-an-existing-camunda-7-database"></a>

## 6. Conexión a una base de datos existente de Camunda 7

Fasti funciona sobre el esquema 7.24 sin modificarlo. Antes de empezar:

1. **Haga una copia de seguridad** de la base de datos.
2. Compruebe la versión del esquema: `select VERSION_ from ACT_GE_SCHEMA_LOG` — el valor más alto debe ser
   `7.24.x`. Esquemas 7.17–7.23: ejecute primero los scripts de `sql/upgrade` (incluidos en la imagen en
   `/fasti/configuration/sql/upgrade`); consulte la
   [guía de migración](migrate-from-camunda-7.es.md#older-versions-717723).
3. Compruebe las autorizaciones, ya que Fasti las aplica: `select * from ACT_ID_MEMBERSHIP where
   GROUP_ID_='camunda-admin'` y `select count(*) from ACT_RU_AUTHORIZATION`. Si el sistema anterior
   funcionaba con las autorizaciones desactivadas, conceda permisos a los usuarios habituales antes de redirigir el tráfico.
4. Anote `TZ`, el nivel de historial, el prefijo de tablas/esquema y la configuración de contraseñas de los
   nodos anteriores.

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=UTC \
  fastidev/fasti-bpm:1.3.0 --production
```

- El puerto está vinculado únicamente a esta máquina; para el acceso remoto, use un proxy inverso con HTTPS.
- **No** configure `FASTI_DB_SCHEMA_UPDATE` ni `FASTI_ADMIN_*`: las tablas y las cuentas ya existen. El
  registro de arranque debe contener `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
- El nivel de historial predeterminado es `auto`: se utiliza el nivel almacenado en la base de datos.
- Ahora todos los clientes REST deben enviar credenciales Basic o un token de portador.
- Controladores: se incluyen PostgreSQL, MySQL, MariaDB, SQL Server y H2. Añada usted mismo los controladores de **Oracle** y **DB2** (`ojdbc11.jar`, `jcc.jar`) con `-v /driver/dir:/drivers:ro -e FASTI_EXTRA_LIB=/drivers`.
  SQL Server: el controlador 13.x cifra la conexión y verifica el certificado de forma predeterminada; el
  servidor necesita un certificado válido que coincida con el nombre de host de la URL. `trustServerCertificate=true`
  (omite las comprobaciones del certificado) o `encrypt=false` (sin cifrado) son solo para pruebas locales. MySQL/MariaDB necesitan
  `transaction-isolation=READ-COMMITTED`.

<a id="7-side-by-side-with-camunda-7-nodes"></a>

## 7. Ejecución en paralelo con nodos de Camunda 7

Fasti y Camunda 7.24 pueden ejecutarse al mismo tiempo sobre una misma base de datos (verificado: ambos ven los datos
del otro). Condiciones:

- mismo nivel de historial, mismo `TZ`, misma configuración de contraseñas y mismo prefijo de tablas;
- si los nodos anteriores son aplicaciones de procesos con clases Java (`JavaDelegate`, escuchadores) que Fasti no tiene, configure `-e FASTI_JOB_EXECUTOR_ENABLED=false` en Fasti o haga que **todos** los nodos
  tengan en cuenta los despliegues (`FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE=true` para Fasti). Esto protege únicamente
  los trabajos asíncronos: iniciar instancias o completar tareas a través de Fasti sigue ejecutando los delegados
  en Fasti, por lo que debe enviar esas solicitudes a nodos que dispongan de las clases Java;
- balanceador de carga: sesiones persistentes para las aplicaciones web; REST con credenciales Basic no las necesita.

Detalles: [Funcionamiento mixto con nodos de Camunda 7](migrate-from-camunda-7.es.md#mixed-operation-with-camunda-7-nodes).

<a id="8-common-environment-variables"></a>

## 8. Variables de entorno habituales

| Variable | Significado |
|---|---|
| `DB_URL`, `DB_DRIVER`, `DB_USERNAME`, `DB_PASSWORD` (`_FILE`) | conexión a la base de datos (nombres de variables de la antigua imagen de Camunda 7 Run; alias: `FASTI_DB_*`) |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | esperar a `host:port` antes de arrancar |
| `TZ` | zona horaria de la JVM; debe ser la misma en todos los nodos |
| `FASTI_DB_SCHEMA_UPDATE` | `true`: crear las tablas que faltan en una base de datos nueva |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (`_FILE`) | crear el administrador si no existe |
| `FASTI_JOB_EXECUTOR_ENABLED`, `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | ejecutor de trabajos de este nodo |
| `FASTI_EXTRA_LIB` | directorio de archivos jar adicionales (controladores de Oracle y DB2) |
| `FASTI_DB_CONN_MAXACTIVE` | tamaño máximo del grupo de conexiones (valor predeterminado: 20; en reposo se mantienen 10 abiertas, `FASTI_DB_CONN_MINIDLE`) |
| `JAVA_OPTS`, `FASTI_MAX_RAM_PERCENTAGE` | opciones de la JVM, porcentaje de memoria para el heap (75) |

Tabla completa: [distro/docker/README.md](../distro/docker/README.md#environment-variables).

<a id="9-health-checks"></a>

## 9. Comprobaciones de estado

| Endpoint | Uso |
|---|---|
| `/actuator/health/liveness` | sonda de actividad de Kubernetes |
| `/actuator/health/readiness` | sonda de disponibilidad, balanceadores de carga; incluye la conexión a la base de datos y el motor |
| `/actuator/health` | estado general |

No es necesario iniciar sesión; solo se exponen los endpoints de estado. El `HEALTHCHECK` de la imagen usa
la disponibilidad, por lo que `docker ps` muestra `healthy` cuando Fasti está listo:

```bash
curl -s http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
docker inspect -f '{{.State.Health.Status}}' fasti           # healthy
```

Kubernetes: `livenessProbe` → `/actuator/health/liveness`, `readinessProbe` →
`/actuator/health/readiness`, `terminationGracePeriodSeconds: 60`.

<a id="10-upgrade-and-rollback"></a>

## 10. Actualización y reversión

**Actualización de Fasti** (por ejemplo, `1.1.0` → `1.3.0`):

```bash
docker pull fastidev/fasti-bpm:1.3.0
docker stop -t 60 fasti && docker rm fasti
docker run -d --name fasti ... fastidev/fasti-bpm:1.3.0 --production   # same options as before
```

Con Docker Compose: cambie `FASTI_IMAGE` (o la línea `image:`) y, a continuación, ejecute
`docker compose -f docker-compose.postgres.yml up -d`. Lea primero el [registro de cambios](../CHANGELOG.md),
especialmente las entradas de **CAMBIOS INCOMPATIBLES**. La serie `1.x` mantiene el esquema 7.24, por lo que las actualizaciones
dentro de ella no modifican el esquema. `docker stop -t 60` permite que las solicitudes en curso terminen antes de que
se detenga el contenedor.

**Reversión:**

- a la versión anterior de Fasti: vuelva a arrancar con la etiqueta anterior;
- a Camunda 7.24: detenga Fasti y vuelva a arrancar los nodos de Camunda 7.24 sobre la misma base de datos. Las preferencias de la nueva interfaz permanecen (`fasti.prefs.*` en `ACT_ID_INFO`, ignoradas por Camunda 7). Los procesos desplegados que utilicen funciones exclusivas de Fasti no se ejecutarán en Camunda 7. Consulte
  [Reversión a Camunda 7.24](migrate-from-camunda-7.es.md#rollback-to-camunda-724).

<a id="11-troubleshooting"></a>

## 11. Resolución de problemas

Consulte el registro con `docker logs fasti` (o `docker compose logs fasti`).

| Síntoma | Causa y solución |
|---|---|
| La API REST devuelve `401` | Fasti requiere autenticación: envíe `-u user:password` o un token de portador |
| Listas vacías o `403` | el usuario carece de autorizaciones: concédale los permisos que necesita sobre los recursos implicados en Administración → Autorizaciones. Añada únicamente a los administradores reales a `camunda-admin` |
| Se detiene con `ENGINE-03111 The database schema has version …` | esquema anterior a 7.24: haga una copia de seguridad y ejecute los scripts de `sql/upgrade` (sección 6). No omita la comprobación con `FASTI_SCHEMA_VERSION_CHECK=warn`: la ejecución sobre un esquema anterior no está admitida y puede fallar durante el funcionamiento |
| Se detiene con `Insecure configuration refused` | una opción desactiva la autenticación REST o las autorizaciones; elimínela (solo `FASTI_INSECURE_DEMO=true` abre la instancia, para demostraciones) |
| Se detiene con `Password does not match policy` | `--production` aplica la política de contraseñas a `FASTI_ADMIN_PASSWORD`: al menos 10 caracteres con letras mayúsculas y minúsculas, un dígito y un carácter especial |
| `password authentication failed` | la contraseña del archivo de secretos difiere de la de la base de datos. Los archivos creados en Windows pueden terminar en `\r\n`: vuelva a crearlos con `tr -d '\r\n'`. PostgreSQL lee `POSTGRES_PASSWORD_FILE` solo cuando el volumen se inicializa por primera vez |
| `FASTI_ADMIN_USER needs FASTI_ADMIN_PASSWORD` | configure `FASTI_ADMIN_PASSWORD` o `FASTI_ADMIN_PASSWORD_FILE` |
| `--production needs a database` | no hay ninguna base de datos configurada; `--production` no recurre a la base de datos H2 de demostración: configure `DB_URL` para una base de datos de producción |
| `… cannot be read` para una variable `_FILE` | el archivo no está montado en el contenedor o el uid 1000 no puede leerlo (el archivo necesita el modo `0644`; su directorio puede mantener `0700`) |
| El inicio de sesión web por HTTP no conserva la sesión | la cookie `Secure` (`--production`) solo se envía por HTTPS (o a `localhost`): termine TLS en el proxy |
| No se puede conectar a SQL Server (error de certificado) | proporcione a SQL Server un certificado válido que coincida con el nombre de host de la URL; `trustServerCertificate=true` es solo para pruebas locales |
| El contenedor se reinicia continuamente y el registro muestra `OutOfMemoryError` | la JVM termina cuando se agota la memoria: aumente el límite de memoria del contenedor (2 GB) |

¿Sigue teniendo problemas? Abra una incidencia en GitHub con el registro (sin contraseñas). Problemas de seguridad: consulte
[SECURITY.md](../SECURITY.md#security-policy).

<a id="12-without-docker"></a>

## 12. Sin Docker

Paquete de Tomcat 1.3.0 listo para usar (página de descarga: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.3.0.zip](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.zip.sha256))
- [fasti-bpm-tomcat-1.3.0.tar.gz](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.tar.gz.sha256))

Verifique la suma de comprobación antes de extraer el archivo, por ejemplo con `sha256sum -c fasti-bpm-tomcat-1.3.0.zip.sha256`.
