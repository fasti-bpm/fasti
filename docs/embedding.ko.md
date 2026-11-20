[English](embedding.md) | [Tiếng Việt](embedding.vi.md) | [Deutsch](embedding.de.md) | [Español](embedding.es.md) | [Français](embedding.fr.md) | [हिन्दी](embedding.hi.md) | [Bahasa Indonesia](embedding.id.md) | [日本語](embedding.ja.md) | 한국어 | [Português (Brasil)](embedding.pt-BR.md) | [Русский](embedding.ru.md) | [ไทย](embedding.th.md) | [简体中文](embedding.zh-CN.md)

> 이 번역은 커뮤니티가 관리하며 영어 버전보다 늦게 갱신될 수 있습니다. 영어 버전이 공식 기준입니다. 수정 제안을 환영합니다.

# Fasti BPM 임베드: 호환성과 Maven 좌표

Fasti가 Camunda 7에서 그대로 유지하는 것과 7.24.0과 다른 점, 버전 번호 체계, 엔진을 임베드하는 애플리케이션을 위한 Maven 좌표를 설명합니다. Fasti를 서버로 실행하려면 [Docker로 설치](install-docker.ko.md) 또는 <https://fasti.dev/download>의 Tomcat 번들을 참조하십시오.

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
[마이그레이션 가이드](migrate-from-camunda-7.ko.md#what-differs)의 "차이점"을 읽으세요.

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
[웹 애플리케이션 경로](web-ui.ko.md#web-application-path-fasti) 참조).

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
