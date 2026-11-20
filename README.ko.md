[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | 한국어 | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> 이 번역은 커뮤니티가 관리하며 영어 버전보다 늦게 갱신될 수 있습니다. 영어 버전이 공식 기준입니다. 수정 제안을 환영합니다.

# Fasti BPM Platform

**Make Camunda 7 Great Again.** Camunda 7 Community Edition은 마지막 릴리스인 7.24.0으로
종료되었습니다. Fasti BPM은 그 뒤를 이어 동일한 데이터베이스에서 동일한 엔진을 실행하면서
지속적인 수정과 라이브러리 업그레이드, 새로운 웹 UI를 제공합니다.

> **Fasti BPM은 Camunda Services GmbH와 제휴 관계가 없으며, Camunda는 Camunda Services GmbH의 상표입니다.**
> Fasti BPM은 독립적인 오픈 소스 프로젝트(Apache-2.0)입니다. Camunda Services GmbH의 일부가 아니며,
> 해당 회사의 후원이나 보증을 받지 않습니다. "Camunda"라는 이름은 소스 코드의 출처와
> Fasti가 무엇과 호환되는지를 설명하기 위한 목적으로만 사용됩니다.

<a id="quick-start"></a>

## 빠른 시작

amd64 및 arm64용 이미지는 Docker Hub(`fastidev/fasti-bpm`)와 GitHub
Container Registry(`ghcr.io/fasti-bpm/fasti-bpm`)에서 제공됩니다.

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.0.0
```

<http://localhost:8080/fasti/>에 접속하여 로그인하세요. `DB_URL`이 없으면 이미지는 컨테이너 내부의 H2 데이터베이스를
사용하며, 이는 평가용으로만 사용해야 합니다. REST API에는 Basic 인증 정보가 필요합니다.

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

소스에서 **이미지를 직접 빌드**할 수 있습니다. Docker, JDK 17+와 함께 Maven Central,
nodejs.org 및 npm 레지스트리에 대한 네트워크 접근이 필요합니다(빌드 과정에서 웹 UI용
Node/npm을 다운로드합니다). 첫 빌드에는 약 10–20분이 걸립니다.

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
# then run the docker run command above with fasti-bpm:local instead of fastidev/fasti-bpm:1.0.0
```

이 이미지는 Camunda Run과 달리 **기본적으로 REST 인증을 요구하고 권한 검사를 적용합니다**.
데모용으로 `FASTI_INSECURE_DEMO=true`를 설정할 때만 비활성화됩니다.

<a id="highlights"></a>

## 주요 기능

- `/fasti/`의 **새로운 웹 UI**(React):
  - 여러 변수를 AND/OR로 조합하여 프로세스 인스턴스를 한 번에 검색;
  - 작업 내 케이스 타임라인(각 단계의 댓글 및 작업 내역);
  - "다음 업무 가져오기" 버튼;
  - 마감일 및 긴급도(SLA), 기한이 지난 업무;
  - 사용자 및 그룹별 팀 업무량;
  - 인시던트에 첨부된 런북;
  - 시스템 상태 페이지(인시던트, 실패한 잡, 지연된 잡, 외부 작업, 배치);
  - 모바일 친화적인 레이아웃;
  - 베트남어를 포함한 13개 언어 지원. BPMN 모델러도 베트남어를 지원합니다.
- **싱글 사인온(OIDC)**: Keycloak, Microsoft Entra ID 및 기타 OpenID Connect 제공자;
  클레임을 그룹 및 테넌트에 매핑; REST API용 JWT Bearer 토큰. Run,
  Spring Boot, Tomcat 및 WildFly에서 사용할 수 있습니다.
- **보안 강화**: 쿼리 및 필터의 표현식 허용 목록, 엔진 내부의 첨부 파일 및
  댓글 검사, 초기 설정, Cockpit 권한, 오픈 리디렉션, 웹
  애플리케이션 헤더, 쿠키 및 CSP. 자세한 내용은 [변경 이력](CHANGELOG.md)을 참조하세요.
- **기본적으로 인증 활성화**: 이미지는 REST 인증 정보를 요구하며 모든 모드에서
  권한 검사를 적용합니다.
- **스키마 버전 보호**: 엔진은 7.24보다 오래된 스키마에서 시작을 거부하며,
  스키마를 자체적으로 업그레이드하지 않습니다.
- **Java4Shell 완화**([CVE-2026-114938](docs/security/CVE-2026-114938.md)): amd64
  이미지에는 수정된 Temurin JRE가 포함되며 Base64 인트린식을 비활성화합니다. 엔진은
  영향을 받는 JVM에서 실행될 때 경고합니다.
- **성능**: 새로운 웹 UI의 최초 로드 크기가 960 KB에서 330 KB로 줄어듭니다(gzip 기본
  활성화). 튜닝을 통해 잡 실행기 처리량이 약 2.5배 증가합니다(수치와 테스트 환경은
  [distro/docker/README.md](distro/docker/README.md#performance-tuning) 참조).
- **최신 플랫폼**: Java 17 이상, Spring Boot 4.1, GraalJS 25, Jackson 2.21.

<a id="installation"></a>

## 설치

<a id="one-command-on-your-existing-camunda-724-database"></a>

### 명령 하나로 기존 Camunda 7.24 데이터베이스에서 실행

이미지는 기존 Camunda 7 Run 이미지의 `DB_*` 변수를 지원합니다. 데이터베이스를 백업한
다음 실행하세요.

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

사용자, 비밀번호, 그룹, 테넌트, 권한, 필터, 배포, 실행 중인 인스턴스,
이력, 잡 및 인시던트는 그대로 유지됩니다. 포트는 이 컴퓨터에만 바인딩됩니다. 원격으로
접근하려면 HTTPS를 사용하는 리버스 프록시를 거치세요. 기존 계정으로 `/fasti/`에 로그인하세요.
인증이 활성화되어 있으므로 모든 REST 클라이언트(외부 작업 워커, 통합 시스템)는
Basic 인증 정보 또는 Bearer 토큰을 보내야 합니다.

- 동일한 데이터베이스에서 Camunda 7.24 CE 노드와 **병행 실행**: 동일한 이력 수준, 동일한
  `TZ`, 동일한 비밀번호 설정을 사용하세요. 기존 노드가 Fasti에 없는 Java 클래스를 포함하는
  프로세스 애플리케이션이라면 Fasti에 `FASTI_JOB_EXECUTOR_ENABLED=false`를 설정하거나 모든 노드가
  배포를 인식하도록 설정하고, Fasti를 통해 해당 프로세스를 시작하거나 작업을 완료하지 마세요.
  동기 호출은 요청을 수신한 노드에서 델리게이트를 실행합니다.
- **롤백**: Fasti는 스키마를 변경하지 않으므로 Fasti를 중지하고 동일한 데이터베이스에서 Camunda 7.24 노드를
  다시 시작하면 됩니다. Fasti 전용 기능을 사용하는 배포된 프로세스는
  Camunda 7에서 실행되지 않습니다. [Camunda 7.24로 롤백](docs/migrate-from-camunda-7.ko.md#rollback-to-camunda-724)을 참조하세요.
- **이전 스키마(7.17–7.23)**: 먼저 이미지에 포함된 `sql/upgrade` 스크립트
  (`/fasti/configuration/sql/upgrade`)를 실행하여 7.24까지 업그레이드하세요. 예를 들어 7.23 → 7.24에는
  `postgres_engine_7.23_to_7.24.sql`을 사용합니다.

이미지로 테스트한 데이터베이스(Camunda Run 7.24.0으로 생성한 데이터베이스 인계, 롤백,
병행 실행): **PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022, Oracle
Database 23**(Oracle 드라이버는 포함되지 않으므로 `ojdbc11`을 직접 추가해야 합니다. MySQL에서는
롤백을 테스트하지 않았습니다). **DB2는
검증되지 않았습니다.** H2는 평가용으로만 사용해야 합니다. 테스트별 결과 및 제한 사항:
[마이그레이션 가이드](docs/migrate-from-camunda-7.ko.md#limits).

상세 가이드:

- [Docker로 설치](docs/install-docker.ko.md): H2, `docker run` 및
  `docker compose`를 사용하는 PostgreSQL, 기존 데이터베이스, 병행 실행 모드, 상태 검사, 업그레이드, 롤백,
  문제 해결.
- [Camunda 7에서 이전](docs/migrate-from-camunda-7.ko.md): 절차, 동일한 점과
  다른 점, 롤백, 제한 사항.
- [이미지 환경 변수 참조](distro/docker/README.md#environment-variables).

<a id="without-docker-tomcat-bundle-download-from-fastidev"></a>

### Docker 없이 설치: Tomcat 번들(fasti.dev에서 다운로드)

사전 빌드된 Tomcat 1.0.0 번들(다운로드 페이지: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256))
- [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256))

압축을 풀기 전에 체크섬을 확인하세요. 예: `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`.

Tomcat 배포판도 소스(`distro/tomcat`)에서 빌드할 수 있습니다. 시작하기 전에 번들에 포함된
`README.txt`를 읽으세요. REST API(`/engine-rest`)는 기본적으로 HTTP Basic 인증을
요구하며, 사용자는 생성되지 않습니다(`/fasti/`의 설정 페이지에서 첫 번째 관리자를 생성하세요).
Camunda Run 데이터베이스를 인계할 때는
`conf/bpm-platform.xml`에 `jobExecutorDeploymentAware=false`를 설정하세요. 그렇지 않으면 기존 배포의 타이머와 비동기 잡이 실행되지 않습니다.

<a id="a-ui-built-from-real-user-complaints"></a>

## 실제 사용자 불편 사항을 바탕으로 만든 UI

새로운 웹 UI의 기능은 Camunda 7 사용자들이 Camunda 포럼과 GitHub에 공개적으로
작성한 의견을 바탕으로 선정했습니다. 예를 들면 다음과 같습니다.

| 사용자 요청 | 출처 | Fasti의 구현 |
|---|---|---|
| 여러 변수와 OR 조건으로 인스턴스 검색 | 포럼 [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | 다중 변수 조건 빌더, AND/OR |
| 케이스의 모든 단계에서 댓글 확인 | 포럼 [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | 작업 내 케이스 타임라인; 프로세스 인스턴스 자체에 댓글 작성 |
| 변수 기준 및 오래된 순서로 작업 정렬 | 포럼 [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | 변수, 마감일 및 긴급도 기준 정렬 |
| 위임, 부재 중 대리 담당자 | 포럼 [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | 작업별 위임 지원; 부재 중 대리 담당자는 [로드맵](#roadmap-plan)에 포함 |
| 폼 없이 프로세스를 시작할 때 비즈니스 키와 변수 지정 | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | 지원 |
| 배포된 BPMN XML 다운로드 | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | 지원 |
| 일관된 레이블, 다국어 지원 | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13개 언어 |

<a id="comparison"></a>

## 비교

각 프로젝트의 공개 문서(2026년 11월 확인)를 기반으로 간략히 비교했습니다.
"미검증"은 신뢰할 수 있는 출처를 찾지 못했다는 의미입니다. 각 프로젝트는 고유한 방향성을 가지며
서로 다른 요구에 적합한 선택이 될 수 있습니다. 결정하기 전에 각 프로젝트의 문서를 확인하세요.

| | 라이선스 | 기존 C7 DB에서 실행 | 필요한 마이그레이션 | 보안 패치 | Java / Spring Boot | UI | SSO | Docker 이미지 |
|---|---|---|---|---|---|---|---|---|
| **Fasti BPM** | Apache-2.0 | 지원, 7.24 스키마 유지 | 이미지: 없음. 임베디드 앱: 새로운 Maven 좌표, Spring Boot 4.1 및 Java 17+; `org.camunda.*` 패키지 유지 | 제공, [아래 정책](#commitments-support-and-security-patches) 참조 | 17+ / 4.1 | 신규(React) + 클래식(사용 중단 예정) | OIDC 내장 | 제공, 1.0.0부터 |
| Camunda 7 CE 7.24 | 소스는 Apache-2.0; 일부 포함된 웹 구성 요소는 MIT ([13]) | (원본) | — | 7.24 이후 CE 릴리스 없음 ([1]) | 17, 21, 25 ([2]) / 3.5 | Cockpit, Tasklist, Admin (AngularJS) | 보안 스타터를 통한 OAuth2 | 기존 Run 이미지 |
| Camunda 7 Enterprise | 상용 | 지원(동일 제품 계열) | 없음 | 04/2030까지, 유료 연장 시 04/2032까지 ([3]) | 미검증 | Cockpit Enterprise | 미검증 | 미검증 |
| Camunda 8 | Camunda License 1.0; 8.6부터 자체 관리형 프로덕션 환경에 라이선스 필요 ([4]) | 미지원(다른 엔진) | 필요(모델, 인스턴스, 데이터) | 제공(공급업체) | 다른 엔진으로 비교 불가 | Operate, Tasklist | 미검증 | 미검증 |
| Operaton | Apache-2.0 | 7.24와 동일한 테이블 구조; 2.2는 업그레이드 스크립트로 스키마 버전 7.25 기록 ([5]) | 패키지를 `org.operaton.*`로 변경(OpenRewrite 레시피 제공); 스키마 버전 스크립트(2.2) | 제공, 2.2.x 계열 ([6]) | 17+ / 4.1 ([5]) | 클래식 + webapps-neo(프리뷰) | 미검증 | 제공 ([7]) |
| CIB seven | Apache-2.0; EE는 라이선스 키 필요 | 지원(7.24 변경 사항 병합) | 패키지를 `org.cibseven.*`로 변경, 접두사 `cibseven.*` | 제공, 릴리스별 ([8]) | 17, 21 / 3.5 및 4.0 ([9]) | Vue 3 웹 클라이언트 | REST용 OAuth2/JWT ([8]) | 제공 ([8]) |
| OrqueIO | Apache-2.0 | 문서에 따르면 C7 테이블과 호환 ([10]) | Maven 그룹 `io.orqueio`; 패키지 이름 변경 여부는 명시되지 않음 | 문서에 따르면 제공 ([10]) | 21 / 3.5, 4.0, 4.1 | Angular로 재작성 | 다중 제공자 OIDC | 미검증 |
| EximeeBPMS | Apache-2.0 (Monitor: MIT) | 프로젝트는 스키마가 Camunda 7과 계속 호환된다고 명시(Fasti에서 검증하지 않음) ([14]) | 패키지를 `org.eximeebpms.*`로 변경, Model API 이름 변경 | 제공, 최신 마이너 릴리스 2개 ([11]) | 21 / Spring Boot 버전 명시되지 않음 | 별도 Monitor | 미검증 | 제공(`eximeebpms-docker` 저장소) |
| Fluxnova | Apache-2.0 (FINOS) | 미검증 | 미검증 | 미검증 | 21(Corretto 이미지) / 미검증 | Control Center | 미검증 | 제공 ([12]) |

Fasti 자체의 약점을 솔직히 밝히면, 소규모 팀이 운영하는 신생 프로젝트이며 아직 상용
지원이 없고 DB2가 검증되지 않았다는 점입니다.

출처: [1] [CE 지원 종료 발표](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921);
[2] [7.24 지원 환경](https://docs.camunda.org/manual/7.24/introduction/supported-environments/);
[3] [Enterprise 지원 연장](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/);
[4] [Camunda 8.6 라이선스](https://camunda.com/blog/2024/10/camunda-licensing-what-you-need-to-know/);
[5] [Operaton 2.2](https://docs.operaton.org/docs/documentation/reference/release-notes/2_2/);
[6] [Operaton SECURITY.md](https://github.com/operaton/operaton/blob/main/SECURITY.md);
[7] [operaton-docker](https://github.com/operaton/operaton-docker);
[8] [CIB seven 2.2.0](https://docs.cibseven.org/manual/latest/release-notes/release-notes-2.2.0-ce/);
[9] [CIB seven 지원 환경](https://docs.cibseven.org/manual/latest/introduction/supported-environments/);
[10] [OrqueIO 문서](https://docs.orqueio.io/);
[11] [EximeeBPMS SECURITY.md](https://github.com/EximeeBPMS/eximeebpms/blob/main/SECURITY.md);
[12] [Fluxnova](https://github.com/finos/fluxnova-bpm-platform);
[13] [Camunda 7 라이선스](https://docs.camunda.org/manual/latest/introduction/licenses/);
[14] [EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms);
독립적인 개요: [WZ-IT, 2026-10-15](https://wz-it.com/en/blog/camunda-7-end-of-life-operaton-cibseven-fluxnova-compared/).

<a id="timeline"></a>

## 연혁

<a id="camunda-7-milestones"></a>

### Camunda 7 주요 이정표

| 날짜 | 이정표 | 출처 |
|---|---|---|
| 2024-03-06 | Camunda가 Camunda 7 CE의 지원을 2025년 10월에 종료하며 7.24가 마지막 CE 릴리스가 될 것이라고 발표 | [Camunda 포럼](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921) |
| 2025-02-28 | Camunda가 Camunda 7 Enterprise 지원을 2030-04-09까지 연장하고, 유료 연장 지원을 2032년 4월까지 제공 | [Camunda 블로그](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) |
| 2025-10-07 | 마지막 CE 릴리스인 `7.24.0` 태그 생성 및 Maven Central 게시 | [7.24.0 태그](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0), [Maven Central](https://repo1.maven.org/maven2/org/camunda/bpm/camunda-engine/7.24.0/) |
| 2025-10-14 | Camunda가 예정한 7.24 릴리스 날짜. 이후에는 보안 패치를 포함한 추가 CE 릴리스가 없음. CE 저장소는 이후 보관 처리됨 | [발표](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921), [지원 페이지](https://docs.camunda.org/enterprise/announcement) |
| 2030년 4월 | Camunda 7 Enterprise 지원 종료(7.24 LTS: 2030-04-13) | [지원 페이지](https://docs.camunda.org/enterprise/announcement) |
| 2032년 4월 | Camunda 7 Enterprise 유료 연장 지원 종료 | [지원 페이지](https://docs.camunda.org/enterprise/announcement) |

<a id="fasti-bpm"></a>

### Fasti BPM

Fasti는 `7.24.0` 태그에서 직접 이어지며, 모든 수정과 변경은 해당 시점을 기준으로 집계합니다
(`git log 7.24.0..`). 아래 단계는 날짜 없이 완료된 작업 순서로 나열했습니다.

1. **기반**: Java 17, Spring Boot 4.1, 라이브러리 업그레이드; Maven 좌표
   `dev.fasti.platform`.
2. **보안**: 7.x 계열에서 공개된 문제 수정, 표현식 허용 목록,
   엔진 내부의 첨부 파일/댓글 검사, 웹 애플리케이션 보안 강화.
3. `/fasti/`의 **새로운 웹 UI**로 기존 Cockpit, Tasklist 및 Admin을 점진적으로 대체.
4. Run, Spring Boot, Tomcat 및 WildFly용 **싱글 사인온**(OIDC).
5. **기존 데이터베이스에서 바로 사용**: 이미지는 기존 `DB_*` 변수를 지원하고 스키마 버전을 확인하며, 5개 데이터베이스 시스템에서 테스트됨.
6. **릴리스 전 보안 검토**(코드 검토, 이미지 침투 테스트, ZAP 기준
   스캔; 발견된 문제 수정) 및 **Java4Shell 완화**.
7. **1.0.0**: 첫 번째 릴리스(2026-11-20).

<a id="roadmap-plan"></a>

## 로드맵(계획)

> 이는 확정된 약속이 아닌 **계획**이며, 사용자 피드백에 따라 순서가 달라질 수 있습니다.

- **부재 중 위임**: 지정 기간 동안 대리 담당자를 설정하고, 새 작업을 재할당하며
  변경 사항을 감사 기록으로 남깁니다([포럼 2788](https://forum.camunda.io/t/2788),
  [38199](https://forum.camunda.io/t/38199)).
- **이중 검토 승인**: 후속 단계의 승인자가 이전 단계를 수행한 사람과 달라야 한다는
  규칙을 엔진에서 강제합니다.
- Kubernetes용 **Helm 차트**.
- **메트릭 및 OpenTelemetry**: 네이티브 Micrometer/Prometheus, 프로세스 및 잡 추적.
- 최신 릴리스로 **MyBatis 업그레이드**.
- 업그레이드 빈도가 낮은 사용자를 위한 **장기 지원(LTS) 계열**.
- **DB2 검증**.
- 대규모 데이터셋에서도 업무량 페이지가 빠르게 작동하도록 **업무량 집계 엔드포인트** 제공.

<a id="commitments-support-and-security-patches"></a>

## 지원 및 보안 패치에 대한 약속

> 이 정책은 Fasti BPM 프로젝트 팀에서 관리하며 업데이트될 수 있습니다. 이
> 저장소에 있는 버전이 현재 유효한 정책입니다.

- **지원 계열:** `1.x` 계열의 최신 마이너 릴리스에 버그 수정과 보안
  패치를 제공합니다(예: `1.0.1`과 같은 패치 릴리스).
- **보안 패치:** Fasti와 포함된 라이브러리의 취약점은 다른 작업보다
  우선합니다. 기본 이미지나 JRE에 수정 사항이 나오면 이미지를 다시 빌드합니다. 프로젝트는
  Camunda 7 및 그 포크에 관한 공개 보안 권고를 확인하고 Fasti의 영향 여부를 점검합니다.
- **호환성:** `1.x` 계열은 7.24 데이터베이스 스키마와 `org.camunda.bpm.*`
  패키지를 유지합니다. 호환성을 깨뜨리는 변경은 [변경 이력](CHANGELOG.md)에 **BREAKING**으로 표시합니다.
- **지원 수준:** 커뮤니티 프로젝트로서 가능한 범위에서 지원하며, **SLA는 제공하지 않습니다**.
  아직 상용 지원은 없습니다.
- **취약점 신고:** 공개 이슈를 **생성하지 마세요**.
  [SECURITY.md](SECURITY.md#security-policy)를 참조하세요(GitHub를 통한 비공개 신고).
- **수정된 결함:** [변경 이력](CHANGELOG.md)의 *수정 사항* 및 *보안* 섹션에
  Camunda 7 CE 7.24.0 대비 수정된 사항이 나열되어 있습니다. 보안 권고는
  [SECURITY.md](SECURITY.md#advisories)에 나열되어 있습니다.

<a id="documentation"></a>

## 문서

| 문서 | 내용 |
|---|---|
| [docs/install-docker.md](docs/install-docker.ko.md) | 단계별 Docker 설치 |
| [docs/api/README.md](docs/api/README.md) | REST API 통합, Java 및 JavaScript 외부 작업 클라이언트 |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.ko.md) | Camunda 7에서 이전, 롤백, 제한 사항 |
| [distro/docker/README.md](distro/docker/README.md) | 이미지: 환경 변수, 드라이버, 운영, 튜닝 |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | 새로운 웹 UI |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell과 Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy), [CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) | 변경 사항, 보안, 기여 방법 |

<a id="what-this-is"></a>

## 프로젝트 소개

이 저장소에는 BPMN 2.0 / DMN 프로세스 엔진, REST API, 웹
애플리케이션(Cockpit, Tasklist, Admin), 배포판(Run, Tomcat, WildFly)이 포함되어 있습니다.
Camunda 7 CE의 `7.24.0` 태그를 시작점으로 버그 수정과 라이브러리
업그레이드를 이어갑니다.

목표는 Camunda 7에서 실행되는 애플리케이션과의 **하위 호환성**입니다.

- `org.camunda.bpm.*` 패키지, BPMN 네임스페이스
  `http://camunda.org/schema/1.0/bpmn`, `camunda.bpm.*` 설정 접두사를 유지합니다.
- 7.24의 데이터베이스 스키마를 유지합니다.
- 모든 배포 형태를 유지합니다: Spring Boot, Run, Tomcat, WildFly; `javax` 및 `jakarta`.

<a id="what-is-different-from-camunda-7240-ce"></a>

## Camunda 7.24.0 CE와의 차이점

- **보안:** 7.x 계열에서 공개된 문제(쿼리의 표현식,
  초기 설정, 첨부 파일 및 댓글 접근, Cockpit 권한, 오픈
  리디렉션)를 수정하고 추가 보호 조치를 적용합니다. 쿼리 및
  필터의 표현식 허용 목록, 엔진 내부의 첨부 파일 및 댓글 검사, 강화된 보안 필터,
  웹 애플리케이션의 헤더 및 쿠키가 포함됩니다.
- **안정성:** JavaScript(GraalJS)로 스크립트와 DMN 표현식을 평가할 때 발생하던
  메모리 누수를 해결하고 여러 엔진 문제를 수정했습니다.
- **플랫폼:** Java 17 이상, Spring Boot 4.1, GraalJS 25, Jackson 2.21 및 기타 여러
  라이브러리를 보안 수정 사항이 포함된 릴리스로 업그레이드했습니다.

일부 수정은 더 안전한 동작을 위해 기본값을 변경합니다. 업그레이드하기 전에
[마이그레이션 가이드](docs/migrate-from-camunda-7.ko.md#what-differs)의 "차이점"을 읽으세요.

<a id="versioning"></a>

## 버전 관리

Fasti BPM Platform은 `1.0.0`부터 [유의적 버전](https://semver.org/)을 따릅니다.
`1.x` 계열은 Camunda 7.24용으로 작성된 애플리케이션과 호환됩니다.

<a id="maven-coordinates"></a>

## Maven 좌표

Fasti BPM Platform의 아티팩트는 자체 Maven 좌표를 사용합니다. **Java 패키지는
`org.camunda.bpm.*`로 유지되므로** 애플리케이션 코드의 import를 그대로 사용할 수 있습니다. `camunda.bpm.*`
설정 접두사, BPMN/DMN 네임스페이스, 데이터베이스 스키마 및
WildFly의 모듈 이름도 변경되지 않습니다. Maven에서는 의존성 선언만
변경됩니다(웹 애플리케이션 경로는 별도로 변경됩니다.
[웹 애플리케이션 경로](#web-application-path-fasti) 참조).

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`; 그룹 `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` 및 `org.camunda.template-engines` → `dev.fasti.platform.<group name>`.
- **artifactId:** 접두사 `camunda-`가 `fasti-`로 바뀌며 나머지는 유지됩니다.
- **version:** `7.24.0` 대신 Fasti BPM Platform 버전([버전 관리](#versioning) 참조)을
  사용합니다.

이 아티팩트의 릴리스는 아직 Maven Central에 게시되지 않았습니다. 그때까지는 소스에서
빌드하여 프로젝트에서 사용하는 Maven 저장소에서 아티팩트를 사용할 수 있도록 하세요.

BOM으로 버전을 관리하세요.

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

자주 사용하는 아티팩트:

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

FEEL 라이브러리 `org.camunda.feel:feel-engine`은 이 저장소에 포함되지 않은 외부
라이브러리이며, 좌표는 그대로 유지됩니다.

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

### 여전히 원본 엔진을 가져오는 서드파티 라이브러리

패키지가 변경되지 않았으므로 원본 엔진(`org.camunda.bpm:camunda-engine`)과
`fasti-engine`에는 이름이 같은 클래스가 포함됩니다. Maven은 이 둘을 서로 관련 없는
라이브러리로 취급하여 어느 쪽도 제거하지 않습니다. 서드파티 라이브러리(예:
커뮤니티 확장 기능)가 원본 아티팩트에 의존한다면 해당 아티팩트를 제외하고 그에 대응하는
Fasti 아티팩트를 대신 선언하세요.

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

`mvn dependency:tree -Dincludes="org.camunda*"`는 여전히 남아 있는 원본 아티팩트를
나열합니다(`org.camunda.feel:feel-engine`은 정상적인 의존성이므로 제외하지 마세요).

<a id="startup-check"></a>

### 시작 시 검사

엔진은 생성될 때 클래스 경로를 검사하며, 둘 이상의 라이브러리에서 프로세스 엔진
클래스가 발견되면 **시작을 거부합니다**. `ENGINE-12021` 오류에
각 클래스의 위치가 표시됩니다. Spring Boot 스타터도 애플리케이션이
시작되는 즉시 동일한 검사를 수행합니다. 부모 클래스 로더를 통해서만 보이는 라이브러리(예:
웹 애플리케이션에 내장된 엔진과 함께 존재하는 서버의 공유 엔진)는
집계하지 않습니다. 의도한 구성이라면 시스템 속성으로 검사를 비활성화할 수 있습니다.

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

제한 사항: 클래스 경로에서 원본 엔진 라이브러리가 `fasti-engine`보다 앞에 있으면,
이 검사가 포함되지 않은 원본 라이브러리의 클래스로 엔진이 구성됩니다.
이 경우 Fasti Spring Boot 스타터를 사용하는 애플리케이션만 시작이 중단됩니다.
다른 통합 방식에서는 위에서 설명한 대로 의존성을 검토하세요.

<a id="web-application-path-fasti"></a>

## 웹 애플리케이션 경로: `/fasti`

모든 배포판은 Cockpit, Tasklist, Admin 및 새로운 웹 UI를 `/fasti`(기존 `/camunda`)
아래에서 제공합니다. `http://localhost:8080/fasti/`는 새로운 UI
(`/fasti/app/next/default/`)를 열며, 클래식 애플리케이션은 `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` 및 `/fasti/app/admin/...`에 있습니다. REST API는 `/engine-rest`를 유지합니다.

**클래식 애플리케이션은 사용 중단 예정입니다.** 클래식 Cockpit, Tasklist, Admin 및 Welcome은
모두 지원이 종료된 AngularJS 1.8.2와 Bootstrap 3를 기반으로 합니다. **컨테이너 이미지는 기본적으로
이를 제공하지 않습니다**(호환성을 깨뜨리는 변경). 해당 페이지와 기존 북마크는 새로운 UI의 대응 섹션으로 리디렉션되며, 해당 파일 요청에는 `404`가 반환됩니다. `FASTI_CLASSIC_WEBAPPS=true`로 다시
활성화할 수 있습니다(경고 표시). [distro/docker/README.md](distro/docker/README.md)를 참조하세요.
Tomcat 및 WildFly 번들도 기본적으로 이를 제공하지 않습니다(
`-Dfasti.webapp.classic.enabled=true` 또는 `FASTI_CLASSIC_WEBAPPS=true`로 다시 활성화 가능). Run zip과 Spring
Boot 스타터는 운영 중인 설치 환경에 문제가 발생하지 않도록 이전처럼 계속 제공합니다.
`fasti.webapp.classic.enabled: false`로 비활성화할 수 있습니다. 클래식 애플리케이션은
향후 릴리스에서 제거될 예정입니다.

기존 경로는 계속 작동합니다. `/camunda/...`에 대한 모든 요청은 나머지 경로와
쿼리 문자열을 유지하면서 `/fasti/...` 아래의 동일한 경로로 리디렉션됩니다. GET과 HEAD에는
`301`이 반환되며, 다른 모든 메서드(POST, PUT, DELETE, 예: `/camunda/api/...`를 호출하는 기존 클라이언트)에는
`308`이 반환되어 클라이언트가 동일한 메서드와 본문으로 요청을 반복합니다. 리디렉션에는
`Cache-Control: no-store`가 포함되므로 `/camunda`로 되돌리면 즉시 적용됩니다.

`/camunda`로 복원하려면:

- **Spring Boot, Run:** `camunda.bpm.webapp.application-path: /camunda`(이 경우 리디렉션 없음).
  리디렉션만 비활성화하려면: `fasti.webapp.legacy-path-redirect: false`.
- **Tomcat:** `webapps/camunda`(리디렉션 애플리케이션)를 삭제하고
  `webapps/fasti`의 이름을 `webapps/camunda`로 변경하세요.
- **WildFly:** `standalone/deployments/camunda.war`와 `camunda.war.dodeploy`를 삭제하고
  웹 애플리케이션 WAR의 `WEB-INF/jboss-web.xml`에
  `<context-root>/camunda</context-root>`를 설정하세요.

**리버스 프록시:** 프록시가 `/camunda/`만 전달하거나 `/camunda`에 대해 경로 재작성, 쿠키
경로, CSP 또는 상태 검사를 설정한다면 `/fasti/`를 추가하거나 완전히 `/fasti/`로 이전하세요.
이제 `XSRF-TOKEN` 쿠키(Tomcat 및 WildFly의 `JSESSIONID` 포함)의 경로가 `/fasti`이므로
사용자는 다시 로그인해야 합니다. 북마크와 기존 링크는 리디렉션을 통해 계속 작동합니다.

<a id="single-sign-on-openid-connect"></a>

## 싱글 사인온(OpenID Connect)

**Run**(`./start.sh --oauth2`, 모듈 `internal/oauth2`) 및
`fasti-bpm-spring-boot-starter-security`를 사용하는 **Spring Boot** 애플리케이션에서 지원됩니다.
Spring Security 클라이언트 등록(`spring.security.oauth2.client.*`)을 선언하면 활성화됩니다. **Tomcat** 및
**WildFly**는 이 섹션의 끝부분을 참조하세요.

- **로그인:** 새로운 웹 애플리케이션의 로그인 페이지에는 비밀번호 폼 대신 "&lt;provider&gt;(으)로 로그인"이
  표시됩니다(레이블: `fasti.security.oidc.login-label`, 기본값은
  `client-name`; 등록이 여러 개라면
  `fasti.security.oidc.registration-id`로 하나를 선택). 인가 코드 흐름은 `state`, `nonce`
  및 PKCE(S256, 기밀 클라이언트에도 적용)를 사용하며, 로그인 시 세션 ID가 교체됩니다. 사용자는
  새로운 웹 애플리케이션에서 처음 접속했던 페이지로 돌아갑니다(
  `/fasti/app/next/` 아래의 경로만 허용하므로 오픈 리디렉션이 발생하지 않습니다).
- **로그아웃:** `POST /logout`(동일 출처 요청만 허용)은 세션을 종료합니다.
  `camunda.bpm.oauth2.sso-logout.enabled=true`이면 사용자는 제공자의 세션 종료
  엔드포인트로 이동합니다(RP 시작 로그아웃, `post-logout-redirect-uri`). Spring Security의
  백채널 로그아웃은 계속 활성화됩니다. `GET /logout`은 더 이상 사용자를 로그아웃시키지 않습니다.
- **세션 만료:** 액세스 토큰을 갱신할 수 없거나 세션 시간이 초과되면
  `/fasti/api/**` 요청은 `401`을 반환하며(리디렉션 없음), 새로운 웹 애플리케이션은 로그인 페이지를 표시합니다.
- **CSRF:** `/fasti/api`와 `/fasti/app`은 웹 애플리케이션 자체의 CSRF 필터
  (`X-XSRF-TOKEN`)를 유지합니다. Spring Security의 CSRF 보호는 업스트림과 동일하게 비활성화된 상태로 유지됩니다.
- **그룹, 관리자, 테넌트:** 그룹 클레임은
  `camunda.bpm.oauth2.identity-provider.group-name-attribute`입니다(업스트림 속성 이름;
  `realm_access.roles`와 같은 중첩 클레임도 지원). `fasti.security.oidc.groups.admin`은
  `camunda-admin`을 부여하는 클레임 값을 나열합니다. `groups.mapping`은 클레임 값을 엔진 그룹
  ID에 매핑하고, `groups.include-unmapped=false`는 매핑되지 않은 값을 제외합니다. **업스트림과의 차이점:**
  클레임 값 `camunda-admin`만으로는 더 이상 관리자 권한이 부여되지 않으며,
  `SCOPE_openid` 또는 `OIDC_USER`와 같은 권한도 더 이상 그룹으로 변환되지 않습니다.
  `fasti.security.oidc.tenants.claim`은 테넌트를 매핑합니다. 이름 및 이메일:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **ID 제공자:** 기본적으로 엔진은 현재 세션의 토큰에서 사용자, 그룹 및 테넌트를
  읽습니다(읽기 전용).
  `camunda.bpm.oauth2.identity-provider.enabled=false`를 설정하면 대신 데이터베이스를 사용합니다(이 경우 그룹은
  멤버십 테이블에서 가져옵니다). 자동 프로비저닝은 구현되지 않았습니다.
  토큰에서 사용자를 가져오는 동안에는(기본값) 관리자가 클레임(`groups.admin`)에서 결정되므로 초기 설정 페이지(첫 번째 관리자)가
  비활성화됩니다. 이전에는 관리자 그룹에 속하지 않은 사용자가 설정 페이지로 이동했습니다.
- **Bearer 토큰을 사용하는 REST API:** `fasti.security.oidc.rest.bearer-enabled=true`를 설정하면
  `/engine-rest`에서 Basic 인증 외에 `Authorization: Bearer <JWT>`도 허용합니다.
  `rest.issuer-uri`와 `rest.audiences`는 필수입니다. 서명은 비대칭 알고리즘만 사용하여 발급자의
  JWKS로 검증하며(`rest.jws-algorithms`, 기본값 `RS256`),
  `iss`, `aud`, `exp`, `nbf`를 검증합니다. Run에는 `camunda.bpm.run.auth.enabled=true`도 필요합니다.
  브라우저 세션 쿠키로는 `/engine-rest`를 인증할 수 없습니다.

Keycloak 예시(Run의 `configuration/default.yml`):

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

Microsoft Entra ID 예시:

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

**Tomcat, WildFly (WAR):** 동일한 기능을 `fasti-oidc.properties`에서 설정합니다(Tomcat:
`conf/`, WildFly: `standalone/configuration/`; 같은 위치에 주석이 포함된 `fasti-oidc.properties.example`
파일이 있습니다). 이 파일이 없으면 아무것도 변경되지 않습니다. 키는 Run의 `fasti.security.oidc.*` 속성과 동일하며, `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`이 추가됩니다.
제공자는 리디렉션 URI `<base>/fasti/sso/callback`을 허용해야 합니다. 백채널 로그아웃 URL은
`<base>/fasti/sso/backchannel-logout`입니다. `/engine-rest`의 Bearer 토큰은
`fasti.security.oidc.rest.bearer-enabled=true`로 활성화합니다(
`rest.basic-enabled=false`가 아니면 Basic 인증도 계속 작동합니다. 엔진 목록 `/engine`에도 인증 정보가 필요합니다). 사용자는
데이터베이스에 존재하지 않아도 됩니다. 엔진이 클레임을 기반으로 로그인한 사용자의
그룹 및 테넌트에 관한 쿼리에 응답하도록 하려면 `OidcIdentityProviderPlugin`을 활성화하세요(
`bpm-platform.xml` / `standalone.xml`에서 주석 처리되어 있음). 제한 사항: 백채널 로그아웃을 위해 추적하는 세션은
서버별 메모리에 유지됩니다(클러스터 노드 간 공유되지 않음). 클래식 애플리케이션은 제공자로 리디렉션만 수행하며, 완전한 로그아웃에는 새로운 웹 UI가 필요합니다.

<a id="build"></a>

## 빌드

JDK 17 이상이 필요합니다(21 권장).

```bash
./mvnw clean verify
```

전체 빌드는 모든 Java 테스트와 웹 UI 단위 테스트를 실행하며(약 1시간 소요; Playwright
종단 간 테스트는 별도로 실행합니다. [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright) 참조), Maven Central,
nodejs.org 및 npm 레지스트리에 대한 네트워크 접근이 필요합니다. [TESTING.md](TESTING.md)를 참조하세요.

<a id="acknowledgements"></a>

## 감사의 말

- Fasti BPM Platform은 Camunda와 Camunda 7 커뮤니티가 10년 이상 쌓아 온
  작업을 기반으로 합니다.
- 결함을 검토하는 과정에서 Camunda 7에서 파생된 다른 커뮤니티 프로젝트인 Operaton, CIB
  seven, OrqueIO 및 EximeeBPMS의 공개 정보(이슈, 보안 권고,
  릴리스 노트)를 참고했습니다. 이 저장소의 소스 코드는 Camunda 7 CE를 기반으로
  독립적으로 작성되었습니다.

<a id="license"></a>

## 라이선스

[Apache License 2.0](LICENSE). 원본 코드의 저작권은 [NOTICE](NOTICE)를 참조하세요.
