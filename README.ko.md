[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | 한국어 | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> 이 번역은 커뮤니티가 관리하며 영어 버전보다 늦게 갱신될 수 있습니다. 영어 버전이 공식 기준입니다. 수정 제안을 환영합니다.

# Fasti BPM Platform

Fasti BPM은 Camunda 7 Community Edition 7.24.0을 기반으로 만든 독립적인 오픈소스 엔진입니다. 기존
Camunda 7 데이터베이스에서 그대로 실행되며, 최신 라이브러리와 보안 수정, 새로운 웹 인터페이스를
제공합니다. 웹사이트: <https://fasti.dev>

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
  fastidev/fasti-bpm:1.0.1
```

<http://localhost:8080/fasti/>에 접속하여 로그인하세요. `DB_URL`이 없으면 이미지는 컨테이너 내부의 H2 데이터베이스를
사용하며, 이는 평가용으로만 사용해야 합니다. REST API에는 Basic 인증 정보가 필요합니다.

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

자체 데이터베이스 사용 방법을 포함한 전체 가이드: <https://fasti.dev/install>

<a id="highlights"></a>

## 주요 기능

- **연결 정보만 바꾸면 됩니다. 그것이 마이그레이션입니다.** Camunda 7.24 스키마를 그대로 사용하며
  `org.camunda.bpm.*` 패키지를 유지합니다([마이그레이션](https://fasti.dev/install#migrate)).
- **컨테이너 하나. 명령 하나.** Camunda Run과 같은 `DB_*` 변수를 사용합니다([설치](https://fasti.dev/install)).
- **업무 대기열을 처리하는 사람들을 위해 만들었습니다**: 13개 언어, 라이트·다크 테마를 지원하며
  휴대폰에서도 쓰기 편한 새로운 웹 인터페이스([기능](https://fasti.dev/#features)).
- **브라우저에서 모델링하고 배포하기**: BPMN, DMN, 폼([모델러](https://fasti.dev/#modeler)).
- **운영에 필요한 도구가 기본으로 들어 있습니다**: 이력, 인스턴스 복구, 일괄 재시도, 작업 로그
  ([운영](https://fasti.dev/#operate)).
- **컨테이너 이미지는 기본이 닫힌 상태**: 첫 시작부터 로그인 필요, 기본 사용자 없음, REST 인증이나
  권한 검사를 끄는 설정은 거부되며, Java4Shell도 완화되어 있습니다. Tomcat과 WildFly는 REST API에 자격
  증명을 요구하고 사용자를 만들지 않습니다(첫 관리자는 설정 페이지에서 만듭니다). Run zip과 Spring Boot
  스타터는 Camunda 7 기본값을 유지합니다. 모든 배포판에 보안 수정이 포함되어 있으며, Java4Shell에 노출된
  JVM에서는 시작 시 경고합니다([보안](https://fasti.dev/security)).
- **싱글 사인온**: OpenID Connect와 JWT bearer token([가이드](docs/single-sign-on.ko.md)).
- **최신 플랫폼**: Java 17 이상, Spring Boot 4.1, GraalJS 25, Jackson 2.21
  ([성능](https://fasti.dev/#performance)).

<a id="installation"></a><a id="get-fasti"></a>

## Fasti 받기

- <a id="one-command-on-your-existing-camunda-724-database"></a>**Docker 이미지**(`fastidev/fasti-bpm`, amd64 및 arm64): <https://fasti.dev/install>. 단계별 안내는
  [docs/install-docker.ko.md](docs/install-docker.ko.md), 모든 변수는
  [distro/docker/README.md](distro/docker/README.md#environment-variables)를 참조하세요.
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**Tomcat 번들**(Docker 없이): <https://fasti.dev/download>. 사전 빌드된 Tomcat 1.0.1 번들:
  - [fasti-bpm-tomcat-1.0.1.zip](https://assets.fasti.dev/releases/1.0.1/fasti-bpm-tomcat-1.0.1.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.1/fasti-bpm-tomcat-1.0.1.zip.sha256))
  - [fasti-bpm-tomcat-1.0.1.tar.gz](https://assets.fasti.dev/releases/1.0.1/fasti-bpm-tomcat-1.0.1.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.1/fasti-bpm-tomcat-1.0.1.tar.gz.sha256))

  압축을 풀기 전에 체크섬을 확인하세요. 예: `sha256sum -c fasti-bpm-tomcat-1.0.1.zip.sha256`.
  시작하기 전에 번들에 포함된 `README.txt`를 읽으세요.
- **임베디드**(Spring Boot 또는 다른 Java 애플리케이션에 내장): 새로운 Maven 좌표
  (`dev.fasti.platform:fasti-*`), 동일한 `org.camunda.bpm.*` 패키지.
  [docs/embedding.ko.md](docs/embedding.ko.md)를 참조하세요.

<a id="moving-from-camunda-7"></a>

## Camunda 7에서 이전

Camunda 7.24를 운영 중이라면 Fasti는 스키마를 변경하지 않고 같은 데이터베이스에서 실행됩니다. 기존 노드를
교체할 수도, Fasti를 함께 실행할 수도, 언제든 Camunda 7.24로 되돌아갈 수도 있습니다.
Fasti 이미지는 PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022, Oracle Database 23ai에서 Camunda Run 7.24가 만든 데이터베이스를 인계받는 테스트를 거쳤습니다. Camunda Run으로 되돌아가는 것은 MySQL을 제외한 같은 데이터베이스에서 테스트했습니다. DB2는 아직 테스트되지 않았습니다. 외부 태스크
워커를 포함한 모든 REST 클라이언트는 이제 Basic 자격 증명 또는 bearer token을 보내야 합니다.

임베디드 Spring Boot 애플리케이션은 Spring Boot 4.1과 Java 17로도 옮겨야 합니다. 이전 스키마는 먼저 표준 스크립트로 업그레이드합니다. Camunda 7용으로 만든 플러그인과 프로세스 애플리케이션을 Fasti 안에서 실행하려면 Fasti 아티팩트를 대상으로 다시 빌드해야 합니다. 병행 운영과 되돌리기에는 제약이 있습니다(모든 노드의 동일한 설정, 어느 노드가 잡을 실행하는지, Fasti 전용 기능을 사용하는 프로세스). [혼합 운영](docs/migrate-from-camunda-7.ko.md#mixed-operation-with-camunda-7-nodes) 및 [롤백](docs/migrate-from-camunda-7.ko.md#rollback-to-camunda-724)을 참조하십시오.

마이그레이션 단계와 제약: <https://fasti.dev/install#migrate> 및
[마이그레이션 가이드](docs/migrate-from-camunda-7.ko.md)(롤백, 이전 스키마, 테스트 결과).

<a id="comparison"></a><a id="how-it-compares"></a>

## 다른 프로젝트와의 비교

Camunda 7을 이어 가는 좋은 프로젝트가 여럿 있습니다. Fasti는 `org.camunda.bpm.*` 패키지와 7.24 스키마를
유지하므로, 전환은 마이그레이션 프로젝트가 아니라 연결 변경에 그치며, 새 인터페이스가 추가됩니다. 다른
프로젝트들도 좋은 선택입니다. 출처가 포함된 전체 표:
<https://fasti.dev/compare>.

<a id="commitments-support-and-security-patches"></a><a id="security-and-support"></a>

## 보안과 지원

`1.x` 라인의 최신 마이너 릴리스가 `1.0.1`과 같은 패치 릴리스 형태로 버그 수정과 보안 패치를 받습니다.
보안 문제는 공개 이슈가 아니라 비공개로 신고해 주십시오. [SECURITY.md](SECURITY.md#security-policy)를
참조하세요. 이 문서에는 [지원 정책](SECURITY.md#support-policy)도 담겨 있습니다. 수정 목록, Java4Shell,
릴리스 전 검토: <https://fasti.dev/security>. 설치, 마이그레이션, 프로덕션 지원과 지원 조건은
<contact@fasti.dev>로 메일을 보내 주세요.

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## 로드맵

이것은 계획이며 일정에 대한 약속이 아닙니다. `1.x`에서 다음으로 예정된 항목: Maven Central의 Maven
아티팩트, Helm 차트, Prometheus 메트릭과 OpenTelemetry 트레이싱, 구조화된 JSON 로그. `2.0`에서 예정된
항목: 부재 중 대리인 지정, 승인에 대한 4-eyes 통제, 변경 불가능한 감사 추적, 새로운 인터페이스의 CMMN
지원. Camunda 7 CE 종료부터 2027년 목표까지의 타임라인:
<https://fasti.dev/#timeline>.

<a id="documentation"></a>

## 문서

[fasti.dev](https://fasti.dev)에서: [Docker로 설치](https://fasti.dev/install) ·
[Tomcat 다운로드](https://fasti.dev/download) · [API 연동 가이드](https://fasti.dev/docs) ·
[보안과 지원](https://fasti.dev/security) · [비교](https://fasti.dev/compare) ·
[블로그](https://fasti.dev/blog)

<a id="what-this-is"></a><a id="what-is-different-from-camunda-7240-ce"></a><a id="versioning"></a><a id="maven-coordinates"></a><a id="third-party-libraries-that-still-pull-in-the-original-engine"></a><a id="startup-check"></a><a id="web-application-path-fasti"></a><a id="single-sign-on-openid-connect"></a><a id="a-ui-built-from-real-user-complaints"></a>

| 문서 | 내용 |
|---|---|
| [docs/install-docker.md](docs/install-docker.ko.md) | 단계별 Docker 설치 |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.ko.md) | Camunda 7에서 이전, 롤백, 제한 사항 |
| [docs/embedding.md](docs/embedding.ko.md) | 호환성, 7.24.0과의 차이점, 버전 관리, Maven 좌표, 시작 시 검사 |
| [docs/web-ui.md](docs/web-ui.ko.md) | 웹 인터페이스: 사용자가 요청한 기능, `/fasti` 경로, 클래식 애플리케이션 |
| [docs/single-sign-on.md](docs/single-sign-on.ko.md) | 싱글 사인온(OpenID Connect): Run, Spring Boot, Tomcat, WildFly |
| [docs/api/README.md](docs/api/README.md) | REST API 통합, Java 및 JavaScript 외부 작업 클라이언트 |
| [distro/docker/README.md](distro/docker/README.md) | 이미지: 환경 변수, 드라이버, 운영, 튜닝 |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | 새로운 웹 UI |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell과 Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy) | 변경 사항, 보안, 지원 정책 |

<a id="build"></a>

## 소스에서 빌드

JDK 17 이상(21 권장)과 Maven Central, nodejs.org 및 npm 레지스트리에 대한 네트워크 접근이 필요합니다(빌드
과정에서 웹 UI용 Node/npm을 다운로드합니다).

```bash
./mvnw clean verify
```

전체 빌드는 모든 Java 테스트와 웹 UI 단위 테스트를 실행합니다(약 1시간 소요; Playwright
종단 간 테스트는 별도로 실행합니다. [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright) 참조).
[TESTING.md](TESTING.md)를 참조하세요. 컨테이너 이미지를 빌드하려면 다음을 실행하세요(첫 빌드에는 약
10–20분이 걸립니다).

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## 기여하기

질문, 버그 보고, 풀 리퀘스트는 GitHub에서 환영합니다. [CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute)와
[행동 강령](CODE_OF_CONDUCT.md)을 참조하세요. 원어민의 번역 검토는 특히 환영합니다.

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
