[English](web-ui.md) | [Tiếng Việt](web-ui.vi.md) | [Deutsch](web-ui.de.md) | [Español](web-ui.es.md) | [Français](web-ui.fr.md) | [हिन्दी](web-ui.hi.md) | [Bahasa Indonesia](web-ui.id.md) | [日本語](web-ui.ja.md) | 한국어 | [Português (Brasil)](web-ui.pt-BR.md) | [Русский](web-ui.ru.md) | [ไทย](web-ui.th.md) | [简体中文](web-ui.zh-CN.md)

> 이 번역은 커뮤니티가 관리하며 영어 버전보다 늦게 갱신될 수 있습니다. 영어 버전이 공식 기준입니다. 수정 제안을 환영합니다.

# Fasti 웹 인터페이스

새 웹 인터페이스에 이러한 기능이 있는 이유와 웹 애플리케이션이 제공되는 경로를 설명합니다. 인터페이스 둘러보기: <https://fasti.dev/#features>

<a id="a-ui-built-from-real-user-complaints"></a>

## 실제 사용자 불편 사항을 바탕으로 만든 UI

새로운 웹 UI의 기능은 Camunda 7 사용자들이 Camunda 포럼과 GitHub에 공개적으로
작성한 의견을 바탕으로 선정했습니다. 예를 들면 다음과 같습니다.

| 사용자 요청 | 출처 | Fasti의 구현 |
|---|---|---|
| 여러 변수와 OR 조건으로 인스턴스 검색 | 포럼 [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | 다중 변수 조건 빌더, AND/OR |
| 케이스의 모든 단계에서 댓글 확인 | 포럼 [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | 작업 내 케이스 타임라인; 프로세스 인스턴스 자체에 댓글 작성 |
| 변수 기준 및 오래된 순서로 작업 정렬 | 포럼 [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | 변수, 마감일 및 긴급도 기준 정렬 |
| 위임, 부재 중 대리 담당자 | 포럼 [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | 작업별 위임, 그리고 인계를 포함한 [부재 중 대리인](absence.md) (1.1.0부터) |
| 폼 없이 프로세스를 시작할 때 비즈니스 키와 변수 지정 | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | 지원 |
| 배포된 BPMN XML 다운로드 | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | 지원 |
| 일관된 레이블, 다국어 지원 | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13개 언어. BPMN, DMN, 폼 편집기 안의 기본 제공 레이블은 영어와 베트남어만 지원 |

<a id="web-application-path-fasti"></a>

## 웹 애플리케이션 경로: `/fasti`

모든 배포판은 Cockpit, Tasklist, Admin 및 새로운 웹 UI를 `/fasti`(기존 `/camunda`)
아래에서 제공합니다. `http://localhost:8080/fasti/`는 새로운 UI
(`/fasti/app/next/default/`)를 열며, 클래식 애플리케이션은 `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` 및 `/fasti/app/admin/...`에 있습니다. REST API는 `/engine-rest`를 유지합니다.

**클래식 애플리케이션은 사용 중단 예정입니다.** 클래식 Cockpit, Tasklist, Admin 및 Welcome은
모두 지원이 종료된 AngularJS 1.8.2와 Bootstrap 3를 기반으로 합니다. **컨테이너 이미지는 기본적으로
이를 제공하지 않습니다**(호환성을 깨뜨리는 변경). 해당 페이지와 기존 북마크는 새로운 UI의 대응 섹션으로 리디렉션되며, 해당 파일 요청에는 `404`가 반환됩니다. `FASTI_CLASSIC_WEBAPPS=true`로 다시
활성화할 수 있습니다(경고 표시). [distro/docker/README.md](../distro/docker/README.md)를 참조하세요.
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
