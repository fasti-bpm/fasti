[English](migrate-from-camunda-7.md) | [Tiếng Việt](migrate-from-camunda-7.vi.md) | [Deutsch](migrate-from-camunda-7.de.md) | [Español](migrate-from-camunda-7.es.md) | [Français](migrate-from-camunda-7.fr.md) | [हिन्दी](migrate-from-camunda-7.hi.md) | [Bahasa Indonesia](migrate-from-camunda-7.id.md) | [日本語](migrate-from-camunda-7.ja.md) | 한국어 | [Português (Brasil)](migrate-from-camunda-7.pt-BR.md) | [Русский](migrate-from-camunda-7.ru.md) | [ไทย](migrate-from-camunda-7.th.md) | [简体中文](migrate-from-camunda-7.zh-CN.md)

> 이 번역은 커뮤니티가 관리하며 영어 버전보다 늦게 갱신될 수 있습니다. 영어 버전이 공식 기준입니다. 수정 제안을 환영합니다.

# Camunda 7에서 Fasti BPM으로 이전하기

Fasti BPM은 Camunda 7 Community Edition 7.24.0을 기반으로 구축되었으며 Camunda 7
데이터베이스와 호환됩니다. 독립적인 프로젝트이며 Camunda Services GmbH와 제휴 관계가 없습니다.

<a id="summary"></a>

## 요약

Camunda 7 **7.24**(Run, 컨테이너 이미지, Spring Boot 스타터, Tomcat 또는
WildFly)를 실행 중이라면 Fasti는 **스키마를 변경하지 않고 동일한 데이터베이스에서 실행됩니다**. 7.24
스키마를 그대로 사용하며 자체 테이블, 열 또는 스키마 버전을 추가하지 않습니다. 기존
노드를 교체하거나 기존 노드와 함께 Fasti를 실행할 수 있으며, Camunda 7.24로 돌아갈 수도 있습니다
([롤백](#rollback-to-camunda-724) 및 [제한 사항](#limits)의 테스트된 데이터베이스 참조).

Camunda 7.23 이하의 데이터베이스는 먼저 SQL 스크립트로 7.24로 업그레이드해야 합니다(
[이전 버전](#older-versions-717723) 참조). Fasti는 스키마를 직접 업그레이드하지 않습니다. 이전 스키마에서는
시작을 거부하고 실행해야 할 스크립트 이름을 안내합니다.

<a id="steps-container-image"></a>

## 절차(컨테이너 이미지)

1. **데이터베이스를 백업합니다.** 아래 단계는 스키마를 변경하지 않지만, 프로덕션 시스템을
   변경하기 전에는 반드시 백업해야 합니다.
2. **스키마 버전을 확인합니다:** `select VERSION_ from ACT_GE_SCHEMA_LOG` — 최댓값은
   `7.24.x`여야 합니다.
3. **기존 노드의 현재 설정을 기록합니다:** `DB_*` 변수, 이력 수준
   (`camunda.bpm.history-level`, `ACT_GE_PROPERTY.historyLevel`), REST 인증
   (`camunda.bpm.run.auth.enabled`), 권한 부여(`camunda.bpm.authorization.enabled`),
   플러그인, LDAP/SSO, JVM 시간대, 비밀번호 해싱(`passwordEncryptor`, `saltGenerator`),
   테이블 접두사/스키마.
4. **이미지** `fastidev/fasti-bpm:1.3.0`**을 가져옵니다**(또는
   [distro/docker/README.md](../distro/docker/README.md)에 설명된 대로 소스에서 빌드합니다).
5. **동일한 `DB_*` 변수**(및 동일한 `TZ`)로 **Fasti를 시작합니다**. Camunda Run과 달리
   Fasti 이미지는 **기본적으로 REST 인증을 요구하고 권한 검사를 적용합니다**. 데이터베이스
   계정은 계속 사용할 수 있지만, 모든 REST 클라이언트는 Basic 인증 정보 또는 Bearer 토큰을 전송해야 하며,
   `camunda-admin`에 속하지 않은 사용자에게는 `ACT_RU_AUTHORIZATION`의 권한 부여가 필요합니다(
   [distro/docker/README.md](../distro/docker/README.md)의 "기본 보안 설정" 참조).
   `FASTI_INSECURE_DEMO=true`는 데모 전용입니다. Fasti는 다음 로그를 기록합니다:
   `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
6. **확인합니다:** 기존 계정으로 `/fasti/`에 로그인하고, 필터, 작업, 인스턴스 및 이력이 있는지 확인합니다. `GET /engine-rest/task`(Basic 인증 정보 포함)와 `GET /actuator/health/readiness`를 호출합니다.
7. **트래픽**(로드 밸런서, DNS)을 Fasti로 **전환합니다**. 기존 노드는 중지하거나 계속 실행합니다(
   [혼합 운영](#mixed-operation-with-camunda-7-nodes) 참조).

Spring Boot 스타터 / Tomcat / WildFly: `org.camunda.bpm:camunda-*` 아티팩트를
해당하는 `dev.fasti.platform:fasti-*` 아티팩트로 교체하고 `camunda.bpm.*`, `bpm-platform.xml` 및
`org.camunda.bpm.*` 패키지는 유지합니다. 클래스패스에 두 엔진의 jar를 함께 두지 마십시오. 이 경우 Fasti는 시작을 거부합니다(이 검사의 한계는
[embedding.ko.md](embedding.ko.md#startup-check)의 "시작 시 검사" 참조).

> **Spring Boot 스타터:** Fasti는 **Spring Boot 4.1**에서 실행되며 **Java 17 이상**이 필요합니다.
> Camunda 7.24 스타터(Spring Boot 3.5)를 사용하는 애플리케이션은 아티팩트를 교체할 때
> Spring Boot 4.1 및 Java 17+로도 함께 이전해야 합니다.
> [embedding.ko.md](embedding.ko.md#what-is-different-from-camunda-7240-ce) 및
> [변경 이력](../CHANGELOG.md#changed)을 참조하십시오.

<a id="tomcat-and-wildfly-distributions"></a>

## Tomcat 및 WildFly 배포판

> **Camunda Run의 데이터베이스를 인계받는 경우 필수 사항**(또는 REST API로 프로세스가
> 배포된 서버의 데이터베이스를 인계받는 경우): Tomcat 및 WildFly 배포판은 Camunda 7과 같이
> `jobExecutorDeploymentAware=true`를 유지합니다. 노드에 등록되지 않은 배포의
> 잡(타이머, 비동기 연속 실행, 재시도)은 **실행되지 않으며 오류도 보고되지 않습니다**.
> 시작하기 전에 `conf/bpm-platform.xml`(Tomcat) 또는 `standalone.xml`(WildFly)의
> 엔진 속성에 `<property name="jobExecutorDeploymentAware">false</property>`를 설정하십시오.
> 다른 노드에서 자체 Java 클래스가 있는 프로세스 애플리케이션을 실행한다면 `true`를 유지하십시오(
> [혼합 운영](#mixed-operation-with-camunda-7-nodes) 참조).

Camunda 7.24의 Tomcat/WildFly 배포판과 비교하면 다음과 같습니다:

- **REST API에 인증이 필요합니다**(호환성을 깨뜨리는 변경). `/engine-rest`에는 `camunda-auth`
  필터(HTTP Basic)가 활성화되어 있습니다. 모든 REST 클라이언트는 엔진 사용자의 사용자 이름과 비밀번호를 전송해야 하며, 그렇지 않으면 401을 받습니다. 엔진 목록(`/engine`)만 공개 상태로 유지됩니다.
  전환하기 전에 REST API를 익명으로 호출하는 REST 클라이언트, 외부 작업 워커 및 스크립트에
  인증 정보를 설정하십시오. OpenID Connect Bearer 토큰(`fasti-oidc.properties`,
  `fasti.security.oidc.rest.bearer-enabled=true`)도 사용할 수 있습니다. 유효한 토큰이 있는 요청에는 비밀번호가 필요하지 않습니다. 인증을 끄려면(**권장하지 않음**)
  `engine-rest` WAR의 `WEB-INF/web.xml`에서 `camunda-auth` 필터와 해당 `filter-mapping`을 제거하십시오.
- **기본 사용자가 없습니다**(호환성을 깨뜨리는 변경). 청구서 예제가 더 이상 배포되지 않으므로
  `demo/demo`(관리자), `john`, `mary`, `peter`가 생성되지 않습니다. 해당 WAR는
  `examples/camunda-invoice.war`에 있으며, 시험해 보려면 `webapps/`(Tomcat) 또는
  `standalone/deployments/`(WildFly)로 복사하십시오. 빈 데이터베이스에서는 `/fasti/`를 열고
  설정 페이지에서 첫 번째 관리자를 생성하십시오. Camunda 7 데이터베이스에서는
  기존 계정을 사용하십시오.
- 이미지와 마찬가지로 **클래식 Cockpit, Tasklist, Admin 및 Welcome이 제공되지 않습니다**(호환성을 깨뜨리는 변경).
  해당 페이지와 북마크는 새 UI의 해당 섹션으로 연결되며, 해당 파일 요청은 404를 반환합니다. 임시로 다시 활성화하려면(사용 중단 예정이며 향후 제거됨) 다음 설정 중 하나를 사용하십시오:
  `CATALINA_OPTS`(Tomcat) 또는 `JAVA_OPTS`(WildFly)의 `-Dfasti.webapp.classic.enabled=true`,
  환경 변수 `FASTI_CLASSIC_WEBAPPS=true`, 또는 웹 애플리케이션의
  `WEB-INF/web.xml`에 있는 컨텍스트 매개변수 `fasti.webapp.classic.enabled`.

<a id="what-is-identical"></a>

## 동일한 사항

- 스키마와 `ACT_GE_PROPERTY`/`ACT_GE_SCHEMA_LOG`의 행(Fasti는 버전 행을 추가하지 않음),
  설치 ID, ID 생성 방식(Run/Spring Boot의 `StrongUuidGenerator`, 엔진의
  기본 `DbIdGenerator`).
- 비밀번호: Camunda 7과 동일하게 16바이트 솔트를 사용하는 SHA-512입니다. 기존 사용자는 로그인할 수 있으며,
  Fasti에서 변경한 비밀번호는 Camunda 7에서도 사용할 수 있습니다.
- `/engine-rest`의 REST API: 엔드포인트, 매개변수 및 페이로드는 같고 엔드포인트가 추가됨(인증과 일부
  상태 코드는 다름, [달라지는 점](#what-differs) 참조), 외부 작업
  (`fetchAndLock`, 롱 폴링), Java 및 JS 클라이언트, `JSESSIONID`, `XSRF-TOKEN`/`X-XSRF-TOKEN`, `camunda.bpm.*`
  속성, `camunda:` BPMN 네임스페이스.
- 새 Tasklist는 클래식 Tasklist의 필터(`color`, `description`, `priority`, `refresh`, `variables`,
  `showUndefinedVariable`)를 동일한 형식으로 읽고 씁니다.

<a id="what-differs"></a>

## 달라진 사항

- 웹 애플리케이션은 `/fasti/app/...`에 있으며, `/camunda/...`는 해당 경로로 리디렉션됩니다(301/308). 프록시가 `/camunda`만 노출한다면 `/fasti`도 노출하거나 `FASTI_WEBAPP_PATH=/camunda`를 설정하십시오. 루트
  경로는 새 웹 애플리케이션을 엽니다(`fasti.webapp.default-app: welcome`으로 Welcome을 복원할 수 있지만,
  클래식 애플리케이션이 꺼져 있는 이미지에서는 예외입니다).
- **호환성을 깨뜨리는 변경, 컨테이너 이미지:** AngularJS 1.8.2 및 Bootstrap 3의 지원이 종료되었으므로
  클래식 Cockpit, Tasklist, Admin 및 Welcome이 제공되지 않습니다. `/camunda/app/` 아래의 항목을 포함하여 해당 페이지와 북마크는
  새 웹 UI의 해당 섹션(`#/cockpit`,
  `#/tasklist`, `#/admin`)으로 리디렉션되며, 해당 파일 요청은 404를 반환합니다. 사용자를 새 웹 UI로 이전하십시오.
  전환 기간에는 `FASTI_CLASSIC_WEBAPPS=true`로 클래식 애플리케이션을 다시 제공할 수 있으며,
  경고가 표시됩니다. Tomcat 및 WildFly도 이를 제공하지 않습니다(
  [Tomcat 및 WildFly 배포판](#tomcat-and-wildfly-distributions) 참조). Run zip과
  Spring Boot 스타터는 여전히 이를 제공하지만, 사용 중단 예정이며 향후 릴리스에서
  제거됩니다(`fasti.webapp.classic.enabled: false`로 미리 끌 수 있습니다).
- 이전 스키마를 사용하는 경우 Fasti는 나중에 실패하는 대신 `ENGINE-03111`과 함께 시작을 거부합니다
  (`FASTI_SCHEMA_VERSION_CHECK=warn`으로 설정하면 시작할 수 있음).
- 쿼리 및 필터의 표현식은 허용 목록(날짜 함수, 현재
  사용자)으로 제한됩니다. 사용자 정의 빈이나 함수를 사용하는 필터는 엔진 설정
  속성 `allowedExpressionFunctionsInQueries`, `allowedExpressionBeansInQueries` 및
  `allowedExpressionPropertiesInQueries`에 이를 선언해야 합니다.
- REST API 동작: 컨테이너 이미지와 Tomcat 및 WildFly 배포판에서는 엔진 목록(`/engine`)을 제외한 모든
  요청에 Basic 자격 증명 또는 bearer token이 필요하므로, REST 클라이언트와 외부 작업 워커가 이를 보내도록
  설정해야 합니다(코드는 바뀌지 않습니다). 모든 배포판에서 허용 목록에 없는 쿼리 표현식은 `400`으로
  거부되고, 권한 없이 작업 첨부 파일과 댓글을 읽거나 변경하면 `403`이 반환되며(권한 검사가 켜진 경우),
  이전에 `500`을 반환하던 일부 잘못된 요청은 `400`을 반환합니다(구문 분석할 수 없는 배포 리소스, 금지된
  Java 직렬화 형식의 변수, 형식이 잘못된 쿼리 매개변수, 비밀번호 정책을 위반하는 비밀번호).
  [Camunda 7과의 차이](api/README.md#differences-from-camunda-7)를 참조하십시오.
- 이미지 기본값: 스키마 업데이트 `false`(새 데이터베이스는 `FASTI_DB_SCHEMA_UPDATE=true` 사용), 
  데모 사용자, 청구서 예제 및 "모든 작업" 필터 없음, CORS 꺼짐(`FASTI_CORS_ALLOWED_ORIGINS=*`로
  복원), `/actuator/health` 추가, `/fasti` 아래에 파일 배치(
  `/camunda/configuration`에 마운트된 파일도 읽음), `--swaggerui` 없음.
- OAuth2/OIDC: 그룹 클레임의 `camunda-admin`만으로는 더 이상 관리자 권한이 부여되지 않습니다
  (`fasti.security.oidc.groups.admin`). `group-name-delimiter`는 정규식이 아닌 리터럴 텍스트입니다.
  로그아웃에는 동일 출처의 POST가 필요합니다.
- 새 Tasklist: `refresh`가 없는 필터는 자동으로 새로 고침됩니다.
- Run jar는 `fasti-bpm-run-core.jar`입니다.
- 7.24.0 대비 보안 수정 사항(첨부 파일/댓글 권한 검사, 초기 설정 등)으로 인해 이전에 성공했던 일부 호출이 403을 반환할 수 있습니다. [CHANGELOG](../CHANGELOG.md)의 *Security* 섹션과 <https://fasti.dev/security>를 참조하십시오.

<a id="rollback-to-camunda-724"></a>

## Camunda 7.24로 롤백

Fasti를 중지하고 동일한 데이터베이스에서 Camunda 7.24 노드를 다시 시작하십시오. Fasti에서
수행한 모든 작업을 확인할 수 있습니다. 남는 항목은 `ACT_ID_INFO`의 `fasti.prefs.*` 행(
새 웹 UI의 환경설정이며 Camunda 7에서는 무시됨)과 새 Tasklist에서 저장한 필터 중
기존에 해당 속성이 없었던 필터의 `refresh: true`입니다. Fasti 전용 기능을 사용하는 배포된 프로세스는 Camunda 7에서 실행되지 않습니다.

<a id="mixed-operation-with-camunda-7-nodes"></a>

## Camunda 7 노드와 혼합 운영

Fasti와 Camunda 7.24는 하나의 데이터베이스에서 동시에 실행할 수 있습니다(테스트 결과: 양쪽에서 서로의
데이터를 볼 수 있으며 어느 노드에서든 잡이 실행됩니다). 요구 사항:

- **동일한 이력 수준**(이미지는 저장된 수준을 사용), **동일한 JVM 시간대**,
  동일한 비밀번호 설정 및 테이블 접두사;
- **잡 실행기:** 기본적으로 Fasti(이미지, Run, Spring Boot)는 Run과 마찬가지로 모든 배포의 잡을 가져옵니다.
  Tomcat 및 WildFly 배포판의 기본값은 `jobExecutorDeploymentAware=true`입니다(Camunda
  7과 동일). 마지막 재시작 이전에 Run 또는 REST를 통해 수행한 배포를 포함하여 해당
  노드에 등록되지 않은 배포의 잡(타이머, 비동기 연속 실행, 재시도)은 **실행되지
  않습니다**. Run의 데이터베이스를 인계받을 때는 `conf/bpm-platform.xml`
  (Tomcat) 또는 `standalone.xml`(WildFly)에서 이를 `false`로 설정하십시오. 기존 노드에서 Fasti에 없는 클래스(`JavaDelegate`, 리스너, 빈)를 포함한 프로세스 애플리케이션을 실행한다면,
  Fasti가 해당 잡을 가져와 실행에 실패하고 인시던트를 발생시키게 됩니다. 이 경우
  Fasti에서 `FASTI_JOB_EXECUTOR_ENABLED=false`를 설정하거나 **모든** 노드가 배포를 인식하도록 설정하십시오(노드의
  잡 실행기는 해당 노드에 등록된 배포의 잡만 실행합니다. REST를 통해 수행한 배포는
  재시작 전까지 해당 요청을 수신한 노드에만 등록됩니다). 동기 REST 호출(작업 완료,
  인스턴스 시작)은 여전히 요청을 수신한 노드에서 델리게이트를 실행하므로, 이러한
  프로세스는 Fasti로 라우팅하지 마십시오;
- **로드 밸런서:** 웹 애플리케이션에는 고정 세션이 필요하며, Basic 인증 REST 호출에는 필요하지 않습니다;
- 클래식 웹 애플리케이션(Camunda 7 노드 또는
  `FASTI_CLASSIC_WEBAPPS=true`가 설정된 Fasti)과 새 웹 애플리케이션은 필터와 사용자를 공유합니다.

<a id="older-versions-717723"></a>

## 이전 버전(7.17–7.23)

백업한 다음, 현재 마이너 버전의 미적용 패치 스크립트를 포함하여
`sql/upgrade/<db>_engine_<from>_to_<to>.sql`을 7.24까지 순서대로 실행하십시오(스크립트는 `configuration/sql/upgrade`에 제공되며,
이미지에서는 `/fasti/configuration/sql/upgrade`에 있음). `ACT_GE_SCHEMA_LOG`에 `7.24.0`이 있는지 확인한 다음
Fasti를 시작하십시오. 7.22 → 7.24 예: `postgres_engine_7.22_to_7.23.sql`을 실행한 다음
`postgres_engine_7.23_to_7.24.sql`을 실행합니다. 7.24보다 이전 버전의 Camunda 노드는
Camunda의 롤링 업데이트 규칙이 허용하는 경우를 제외하고 업그레이드된 스키마에서 계속 실행해서는 안 됩니다(
마이너 버전 차이는 하나만 허용되며, 기존 노드는 새 기능을 사용해서는 안 됨).

<a id="performance"></a>

## 성능

이미지는 이제 잡 실행기와 연결 풀에 Camunda Run 기본값을 사용하지 않습니다(Fasti 1.1까지는 동일했습니다). 잡 스레드 10개, 큐 10, 획득당 잡 10개(Camunda Run: 코어 3, 최대 10, 큐 3, 획득당 3)를 사용하고, 연결 풀은 최대 20개이며 유휴 시 10개를 열어 둡니다(Camunda Run: 10). 데이터베이스의 연결 한도는 Camunda 7 노드를 포함한 모든 노드의 풀 합계에 맞춰 정하십시오([런타임 튜닝](tuning-runtime.md), 영어). 기존 Camunda 7 설치에서 연결 풀이나 잡 실행기를 조정했다면 해당 값을 그대로 적용하십시오(`FASTI_DB_CONN_MAXACTIVE`, `FASTI_DB_CONN_MINIDLE`, `CAMUNDA_BPM_JOB_EXECUTION_*`). 추가 지침은 `distro/docker/README.md`의 "성능 튜닝"에 있습니다. Fasti는 스키마의 인덱스를 스스로 변경하지 않습니다. 대규모 데이터베이스를 위해 관리자가 실행하는 스크립트 형태의 선택적 인덱스를 제공합니다(`/fasti/configuration/sql/optional/<database>_fasti_perf_indexes_create.sql`, [performance.md](performance.md) 참조). 이 인덱스는 Camunda 7.24 노드와 함께 사용해도 안전하며 해당 `_drop.sql`로 제거됩니다.

<a id="limits"></a>

## 제한 사항

- 7.24 Community Edition 스키마만 지원하며, Enterprise 패치 스키마는 테스트하지 않았습니다.
- 컨테이너 이미지로 테스트했습니다(Camunda Run 7.24.0에서 데이터베이스 생성 및 초기 데이터 입력 → Fasti로
  인계 → Run으로 복귀 → 양쪽 동시 실행):

  | 데이터베이스 | 드라이버 | 인계 | 롤백 | 혼합 운영 | 7.23 → 7.24 업그레이드 |
  |---|---|---|---|---|---|
  | PostgreSQL 16 | 기본 포함 | 통과 | 통과 | 통과 | 테스트하지 않음 |
  | MariaDB 11.8 | MariaDB Connector/J(기본 포함) 또는 MySQL Connector/J | 통과 | 통과 | 통과 | 통과 |
  | MySQL 8.4 | 기본 포함 | 통과 | — | 통과 | 통과 |
  | SQL Server 2022 (Developer) | 기본 포함(`encrypt=false`) | 통과 | 통과 | 통과 | 테스트하지 않음 |
  | Oracle Database 23ai Free | `FASTI_EXTRA_LIB`를 통한 `ojdbc11` | 통과 | 통과 | 통과 | 테스트하지 않음 |
  | DB2 | — | 테스트하지 않음 | | | |

  추가로, MariaDB(MariaDB Connector/J)에서 두 Fasti 노드가 동일한 작업을 완료하고 외부 작업을 병렬로 가져오는 테스트에서도 손실이나 중복은 발생하지 않았습니다. 경합에서 진 트랜잭션은 낙관적 잠금 오류를 받았습니다. MySQL/MariaDB에는 `transaction-isolation=READ-COMMITTED`가 필요합니다. MySQL에서는
  각 엔진에 별도의 데이터베이스를 할당하십시오. 엔진은(Camunda Run과 Fasti 모두) 계정에서 볼 수 있는
  모든 데이터베이스에서 테이블을 찾습니다.
- Camunda 7용으로 빌드된 플러그인 및 프로세스 애플리케이션을 Fasti 내에서 실행하려면 Fasti
  아티팩트를 대상으로 다시 빌드해야 합니다. REST 클라이언트 및 외부 작업 클라이언트는 코드를 변경할 필요가
  없지만, 컨테이너 이미지, Tomcat 또는 WildFly에서는 Basic 자격 증명 또는 bearer token을 보내도록 설정해야 합니다.
- 스키마 검사는 `ACT_GE_SCHEMA_LOG`만 읽으며, 열을 비교하지 않습니다.
