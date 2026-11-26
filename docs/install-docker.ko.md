[English](install-docker.md) | [Tiếng Việt](install-docker.vi.md) | [Deutsch](install-docker.de.md) | [Español](install-docker.es.md) | [Français](install-docker.fr.md) | [हिन्दी](install-docker.hi.md) | [Bahasa Indonesia](install-docker.id.md) | [日本語](install-docker.ja.md) | 한국어 | [Português (Brasil)](install-docker.pt-BR.md) | [Русский](install-docker.ru.md) | [ไทย](install-docker.th.md) | [简体中文](install-docker.zh-CN.md)

> 이 번역은 커뮤니티가 관리하며 영어 버전보다 늦게 갱신될 수 있습니다. 영어 버전이 공식 기준입니다. 수정 제안을 환영합니다.

# Docker로 Fasti BPM 설치하기

단계별 가이드: H2를 이용한 빠른 체험, PostgreSQL을 이용한 운영 환경 구성(`docker run` 또는
`docker compose`), 기존 Camunda 7 데이터베이스 연결, 병행 실행 모드, 상태
점검, 업그레이드, 롤백 및 문제 해결을 다룹니다. 이미지에 관한 전체 참조 문서:
[distro/docker/README.md](../distro/docker/README.md). Fasti BPM은 Camunda 7 CE
7.24.0을 기반으로 구축된 독립 프로젝트이며, Camunda Services GmbH와 제휴 관계가 없습니다.

<a id="1-requirements"></a>

## 1. 요구 사항

- Docker Compose v2(`docker compose`)가 포함된 Docker Engine.
- 실제 워크로드: Fasti 컨테이너에 최소 CPU 2개와 메모리 2 GB 필요(이보다 적으면
  JVM이 Serial GC를 선택합니다).
- 운영 데이터베이스: PostgreSQL, MariaDB, MySQL, SQL Server 또는 Oracle(
  [테스트된 호환성 표](migrate-from-camunda-7.ko.md#limits) 참조). DB2는 검증되지 않았습니다. H2는 평가용으로만 사용하세요.

<a id="2-pull-the-image"></a>

## 2. 이미지 가져오기

<!-- update with the Docker Hub image name at release -->
이미지는 amd64 및 arm64용으로 다음 두 곳에 게시됩니다.

```bash
docker pull fastidev/fasti-bpm:1.3.2              # Docker Hub
docker pull ghcr.io/fasti-bpm/fasti-bpm:1.3.2     # GitHub Container Registry
```

태그: `1.3.2`(해당 릴리스 지정), `1.3`(1.3의 최신 패치 릴리스) 및 `latest`(가장 최근에 게시된 릴리스).
이미지는 cosign으로 서명되며(키리스, GitHub OIDC), SBOM과 출처 증명을 포함합니다. 1.0.1부터 서명은
cosign 3의 Sigstore 번들 형식이며, 1.0.0 이미지에는 이전 cosign 2 형식의 서명이 붙어 있습니다.

소스에서 이미지를 빌드할 수도 있습니다(JDK 17+ 및 Maven
Central, nodejs.org, npm 레지스트리에 대한 네트워크 접근 필요). 이 경우 아래의 모든 명령에서
`fastidev/fasti-bpm:1.3.2` 대신 `fasti-bpm:local`을 사용하세요.

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="3-quick-trial-with-h2"></a>

## 3. H2로 빠르게 체험하기

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  -v fasti-data:/fasti/data \
  fastidev/fasti-bpm:1.3.2
```

- <http://localhost:8080/fasti/>에 접속하여 `admin`으로 로그인하세요.
- `DB_URL`이 없으면 이미지는 `/fasti/data`의 H2를 사용합니다. `fasti-data` 볼륨은 컨테이너를
  다시 생성해도 데이터를 유지합니다. 평가용으로만 사용하고, 실제 데이터에는 절대 사용하지 마세요.
- 예제 프로세스(invoice)도 함께 사용하려면 명령에 `--webapps --rest --example`을
  추가하세요. `--example`만 지정하면 웹 UI와 REST API는 비활성화된 상태로 유지됩니다.
- REST API는 `http://localhost:8080/engine-rest`에서 제공되며 HTTP Basic 인증 정보가 필요합니다.
  [REST API 가이드](api/README.md)를 참조하세요.

<a id="4-production-with-postgresql-docker-run"></a>

## 4. PostgreSQL을 이용한 운영 환경 구성: `docker run`

시크릿 파일 두 개(데이터베이스 비밀번호 및 관리자 비밀번호)를 생성하세요. `--production`을 사용하면 엔진이
비밀번호 정책을 적용합니다. 비밀번호는 최소 10자이며 대문자, 소문자, 숫자,
특수 문자를 포함해야 합니다.

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
  fastidev/fasti-bpm:1.3.2 --production
```

- `FASTI_DB_SCHEMA_UPDATE=true`는 새 데이터베이스에 누락된 테이블만 생성합니다. Fasti는
  기존 스키마를 업그레이드하지 않습니다. 이후 시작 시에도 이 설정을 유지할 수 있습니다.
- `--production`은 `Secure` 쿠키를 활성화하며, 명시적으로 설정하지 않은 H2 데이터베이스와 `DEBUG=true`를 허용하지 않습니다.
  Fasti 앞단의 리버스 프록시에서 TLS를 종료하세요. 브라우저는 HTTPS 연결에서만(또는
  `localhost`로만) `Secure` 쿠키를 전송합니다.
- `WAIT_FOR`는 데이터베이스가 연결을 수락할 때까지 시작을 지연합니다.
- 관리자 비밀번호는 관리자가 아직 존재하지 않을 때만 설정됩니다. 이후에는 Admin에서 변경하세요.

<a id="5-production-with-postgresql-docker-compose"></a>

## 5. PostgreSQL을 이용한 운영 환경 구성: `docker compose`

예제 파일: [distro/docker/examples/docker-compose.postgres.yml](../distro/docker/examples/docker-compose.postgres.yml)
(PostgreSQL 16, 시크릿, 상태 점검, `--production`, 메모리 제한 2 GB).

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

시작 시 설정할 수 있는 변수: `FASTI_IMAGE`(기본값 `fastidev/fasti-bpm:1.3.2`,
예: `FASTI_IMAGE=fasti-bpm:local`), `FASTI_HOST_PORT`(기본값 `8080`), `TZ`(기본값 `UTC`).

<a id="6-connecting-to-an-existing-camunda-7-database"></a>

## 6. 기존 Camunda 7 데이터베이스에 연결하기

Fasti는 7.24 스키마를 변경하지 않고 실행됩니다. 시작하기 전에:

1. 데이터베이스를 **백업**하세요.
2. 스키마 버전을 확인하세요. `select VERSION_ from ACT_GE_SCHEMA_LOG`의 최댓값이
   `7.24.x`여야 합니다. 스키마가 7.17–7.23인 경우 먼저 `sql/upgrade` 스크립트(이미지의
   `/fasti/configuration/sql/upgrade`에 포함)를 실행하세요.
   [마이그레이션 가이드](migrate-from-camunda-7.ko.md#older-versions-717723)를 참조하세요.
3. Fasti는 권한 검사를 적용하므로 권한을 확인하세요. `select * from ACT_ID_MEMBERSHIP where
   GROUP_ID_='camunda-admin'` 및 `select count(*) from ACT_RU_AUTHORIZATION`을 실행하세요. 기존 시스템이
   권한 검사를 비활성화한 상태로 실행되었다면 트래픽을 전환하기 전에 일반 사용자에게 권한을 부여하세요.
4. 기존 노드의 `TZ`, 이력 수준, 테이블 접두사/스키마 및 비밀번호 설정을
   기록하세요.

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=UTC \
  fastidev/fasti-bpm:1.3.2 --production
```

- 포트는 이 컴퓨터에만 바인딩됩니다. 원격으로 접근하려면 HTTPS를 사용하는 리버스 프록시를 거치세요.
- 테이블과 계정이 이미 존재하므로 `FASTI_DB_SCHEMA_UPDATE` 또는 `FASTI_ADMIN_*`를 설정하지 **마세요**.
  시작 로그에 `ENGINE-03115 Database schema version 7.24.0 matches this engine.`이 포함되어야 합니다.
- 이력 수준의 기본값은 `auto`이며, 데이터베이스에 저장된 수준을 사용합니다.
- 이제 모든 REST 클라이언트는 Basic 인증 정보 또는 Bearer 토큰을 전송해야 합니다.
- 드라이버: PostgreSQL, MySQL, MariaDB, SQL Server 및 H2가 포함되어 있습니다. **Oracle** 및 **DB2** 드라이버(`ojdbc11.jar`, `jcc.jar`)는 `-v /driver/dir:/drivers:ro -e FASTI_EXTRA_LIB=/drivers`를 사용하여 직접 추가하세요.
  SQL Server: 드라이버 13.x는 기본적으로 연결을 암호화하고 인증서를 검증합니다.
  서버에는 URL의 호스트 이름과 일치하는 유효한 인증서가 필요합니다. `trustServerCertificate=true`
  (인증서 검사 생략) 또는 `encrypt=false`(암호화 안 함)는 로컬 체험용으로만 사용하세요. MySQL/MariaDB에는
  `transaction-isolation=READ-COMMITTED`가 필요합니다.

<a id="7-side-by-side-with-camunda-7-nodes"></a>

## 7. Camunda 7 노드와 병행 실행하기

Fasti와 Camunda 7.24는 하나의 데이터베이스에서 동시에 실행할 수 있습니다(서로의 데이터를
조회할 수 있음을 테스트로 확인). 조건:

- 동일한 이력 수준, 동일한 `TZ`, 동일한 비밀번호 설정, 동일한 테이블 접두사;
- 기존 노드가 Fasti에 없는 Java 클래스(`JavaDelegate`, 리스너)를 포함하는 프로세스 애플리케이션이라면 Fasti에 `-e FASTI_JOB_EXECUTOR_ENABLED=false`를 설정하거나 **모든** 노드에서
  배포 인식 기능을 활성화하세요(Fasti의 경우 `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE=true`). 이는
  비동기 작업만 보호합니다. Fasti를 통해 인스턴스를 시작하거나 태스크를 완료하면 여전히 Fasti에서 델리게이트가
  실행되므로, 이러한 요청은 해당 Java 클래스가 있는 노드로 보내세요;
- 로드 밸런서: 웹 애플리케이션에는 고정 세션을 사용하세요. Basic 인증 정보를 사용하는 REST에는 필요하지 않습니다.

자세한 내용: [Camunda 7 노드와 혼합 운영](migrate-from-camunda-7.ko.md#mixed-operation-with-camunda-7-nodes).

<a id="8-common-environment-variables"></a>

## 8. 주요 환경 변수

| 변수 | 의미 |
|---|---|
| `DB_URL`, `DB_DRIVER`, `DB_USERNAME`, `DB_PASSWORD` (`_FILE`) | 데이터베이스 연결(기존 Camunda 7 Run 이미지의 변수 이름, 별칭: `FASTI_DB_*`) |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | 시작 전에 `host:port`가 준비될 때까지 대기 |
| `TZ` | JVM 시간대. 모든 노드에서 동일해야 함 |
| `FASTI_DB_SCHEMA_UPDATE` | `true`: 새 데이터베이스에 누락된 테이블 생성 |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (`_FILE`) | 관리자가 없으면 생성 |
| `FASTI_JOB_EXECUTOR_ENABLED`, `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | 이 노드의 작업 실행기 |
| `FASTI_EXTRA_LIB` | 추가 jar 파일 디렉터리(Oracle, DB2 드라이버) |
| `FASTI_DB_CONN_MAXACTIVE` | 연결 풀 최대 크기(기본값 20, 유휴 시 10개 유지, `FASTI_DB_CONN_MINIDLE`) |
| `JAVA_OPTS`, `FASTI_MAX_RAM_PERCENTAGE` | JVM 옵션, 힙에 사용할 메모리 비율(75) |

전체 표: [distro/docker/README.md](../distro/docker/README.md#environment-variables).

<a id="9-health-checks"></a>

## 9. 상태 점검

| 엔드포인트 | 용도 |
|---|---|
| `/actuator/health/liveness` | Kubernetes 활성 상태 프로브 |
| `/actuator/health/readiness` | 준비 상태 프로브, 로드 밸런서용. 데이터베이스 연결과 엔진 상태 포함 |
| `/actuator/health` | 전체 상태 |

로그인이 필요하지 않으며, 상태 엔드포인트만 노출됩니다. 이미지의 `HEALTHCHECK`는
준비 상태를 확인하므로 Fasti가 준비되면 `docker ps`에 `healthy`가 표시됩니다.

```bash
curl -s http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
docker inspect -f '{{.State.Health.Status}}' fasti           # healthy
```

Kubernetes: `livenessProbe` → `/actuator/health/liveness`, `readinessProbe` →
`/actuator/health/readiness`, `terminationGracePeriodSeconds: 60`.

<a id="10-upgrade-and-rollback"></a>

## 10. 업그레이드 및 롤백

**Fasti 업그레이드**(예: `1.1.0` → `1.3.2`):

```bash
docker pull fastidev/fasti-bpm:1.3.2
docker stop -t 60 fasti && docker rm fasti
docker run -d --name fasti ... fastidev/fasti-bpm:1.3.2 --production   # same options as before
```

Docker Compose 사용 시: `FASTI_IMAGE`(또는 `image:` 줄)를 변경한 다음
`docker compose -f docker-compose.postgres.yml up -d`를 실행하세요. 먼저 [변경 이력](../CHANGELOG.md)을
읽고, 특히 **BREAKING** 항목을 확인하세요. `1.x` 계열은 7.24 스키마를 유지하므로 이 계열 내의
업그레이드는 스키마를 변경하지 않습니다. `docker stop -t 60`은 컨테이너가 중지되기 전에
처리 중인 요청이 완료될 시간을 줍니다.

**롤백:**

- 이전 Fasti 릴리스로 롤백: 이전 태그로 다시 시작하세요;
- Camunda 7.24로 롤백: Fasti를 중지하고 동일한 데이터베이스에서 Camunda 7.24 노드를 다시 시작하세요. 새 UI의 환경설정은 유지됩니다(`ACT_ID_INFO`의 `fasti.prefs.*`, Camunda 7에서는 무시됨). Fasti 전용 기능을 사용하는 배포된 프로세스는 Camunda 7에서 실행되지 않습니다.
  [Camunda 7.24로 롤백](migrate-from-camunda-7.ko.md#rollback-to-camunda-724)을 참조하세요.

<a id="11-troubleshooting"></a>

## 11. 문제 해결

`docker logs fasti`(또는 `docker compose logs fasti`)로 로그를 확인하세요.

| 증상 | 원인 및 해결 방법 |
|---|---|
| REST API가 `401`을 반환함 | Fasti는 인증이 필요합니다. `-u user:password` 또는 Bearer 토큰을 전송하세요 |
| 목록이 비어 있거나 `403`이 반환됨 | 사용자에게 권한이 없습니다. 관리 → 권한에서 관련 리소스에 필요한 권한을 부여하세요. 실제 관리자만 `camunda-admin`에 추가하세요 |
| `ENGINE-03111 The database schema has version …` 메시지와 함께 중지됨 | 스키마가 7.24보다 오래되었습니다. 백업한 다음 `sql/upgrade` 스크립트를 실행하세요(6절). `FASTI_SCHEMA_VERSION_CHECK=warn`으로 검사를 우회하지 마세요. 이전 스키마에서의 실행은 지원되지 않으며 운영 중 오류가 발생할 수 있습니다 |
| `Insecure configuration refused` 메시지와 함께 중지됨 | REST 인증 또는 권한 검사를 비활성화하는 설정이 있습니다. 해당 설정을 제거하세요(데모용 `FASTI_INSECURE_DEMO=true`만 인스턴스를 인증 및 권한 검사 없이 개방함) |
| `Password does not match policy` 메시지와 함께 중지됨 | `--production`은 `FASTI_ADMIN_PASSWORD`에 비밀번호 정책을 적용합니다. 최소 10자이며 대문자, 소문자, 숫자, 특수 문자를 포함해야 합니다 |
| `password authentication failed` | 시크릿 파일의 비밀번호가 데이터베이스의 비밀번호와 다릅니다. Windows에서 생성한 파일은 `\r\n`으로 끝날 수 있습니다. `tr -d '\r\n'`을 사용하여 다시 생성하세요. PostgreSQL은 볼륨을 처음 초기화할 때만 `POSTGRES_PASSWORD_FILE`을 읽습니다 |
| `FASTI_ADMIN_USER needs FASTI_ADMIN_PASSWORD` | `FASTI_ADMIN_PASSWORD` 또는 `FASTI_ADMIN_PASSWORD_FILE`을 설정하세요 |
| `--production needs a database` | 데이터베이스가 설정되지 않았습니다. `--production`은 데모 H2 데이터베이스로 대체하지 않습니다. `DB_URL`을 운영 데이터베이스로 설정하세요 |
| `_FILE` 변수에 대해 `… cannot be read`가 표시됨 | 파일이 컨테이너에 마운트되지 않았거나 uid 1000이 읽을 수 없습니다(파일 권한은 `0644`여야 하며, 디렉터리는 `0700`으로 유지해도 됨) |
| HTTP를 통한 웹 로그인에서 세션이 유지되지 않음 | `Secure` 쿠키(`--production`)는 HTTPS 연결에서만(또는 `localhost`로만) 전송됩니다. 프록시에서 TLS를 종료하세요 |
| SQL Server에 연결할 수 없음(인증서 오류) | SQL Server에 URL의 호스트 이름과 일치하는 유효한 인증서를 설정하세요. `trustServerCertificate=true`는 로컬 체험용으로만 사용하세요 |
| 컨테이너가 계속 다시 시작되며 로그에 `OutOfMemoryError`가 표시됨 | 메모리가 부족하면 JVM이 종료됩니다. 컨테이너 메모리 제한을 높이세요(2 GB) |

문제가 계속되나요? 로그에서 비밀번호를 제거한 후 로그를 첨부하여 GitHub 이슈를 등록하세요. 보안 문제는
[SECURITY.md](../.github/SECURITY.md#security-policy)를 참조하세요.

<a id="12-without-docker"></a>

## 12. Docker 없이 사용하기

사전 구성된 Tomcat 1.3.1 번들(다운로드 페이지: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.3.1.zip](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.zip) ([SHA-256](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.zip.sha256))
- [fasti-bpm-tomcat-1.3.1.tar.gz](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.tar.gz.sha256))

압축을 풀기 전에 체크섬을 확인하세요. 예: `sha256sum -c fasti-bpm-tomcat-1.3.1.zip.sha256`.
