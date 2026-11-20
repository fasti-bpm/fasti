[English](single-sign-on.md) | [Tiếng Việt](single-sign-on.vi.md) | [Deutsch](single-sign-on.de.md) | [Español](single-sign-on.es.md) | [Français](single-sign-on.fr.md) | [हिन्दी](single-sign-on.hi.md) | [Bahasa Indonesia](single-sign-on.id.md) | [日本語](single-sign-on.ja.md) | 한국어 | [Português (Brasil)](single-sign-on.pt-BR.md) | [Русский](single-sign-on.ru.md) | [ไทย](single-sign-on.th.md) | [简体中文](single-sign-on.zh-CN.md)

> 이 번역은 커뮤니티가 관리하며 영어 버전보다 늦게 갱신될 수 있습니다. 영어 버전이 공식 기준입니다. 수정 제안을 환영합니다.

<a id="single-sign-on-openid-connect"></a>

# 싱글 사인온(OpenID Connect)

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
