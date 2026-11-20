English | [Tiếng Việt](single-sign-on.vi.md) | [Deutsch](single-sign-on.de.md) | [Español](single-sign-on.es.md) | [Français](single-sign-on.fr.md) | [हिन्दी](single-sign-on.hi.md) | [Bahasa Indonesia](single-sign-on.id.md) | [日本語](single-sign-on.ja.md) | [한국어](single-sign-on.ko.md) | [Português (Brasil)](single-sign-on.pt-BR.md) | [Русский](single-sign-on.ru.md) | [ไทย](single-sign-on.th.md) | [简体中文](single-sign-on.zh-CN.md)

<a id="single-sign-on-openid-connect"></a>

# Single sign-on (OpenID Connect)

Supported on **Run** (`./start.sh --oauth2`, module `internal/oauth2`) and on **Spring Boot**
applications with `fasti-bpm-spring-boot-starter-security`. It is enabled by declaring a
Spring Security client registration (`spring.security.oauth2.client.*`). For **Tomcat** and
**WildFly** see the end of this section.

- **Login:** the login page of the new web application shows "Sign in with &lt;provider&gt;"
  instead of the password form (label: `fasti.security.oidc.login-label`, defaults to the
  `client-name`; with several registrations choose one with
  `fasti.security.oidc.registration-id`). The authorization code flow uses `state`, `nonce`
  and PKCE (S256, also for confidential clients); the session id is replaced on login. Users
  return to the page of the new web application they started from (only paths below
  `/fasti/app/next/` are accepted, so there is no open redirect).
- **Logout:** `POST /logout` (same-origin requests only) ends the session; with
  `camunda.bpm.oauth2.sso-logout.enabled=true` the user continues to the provider's end
  session endpoint (RP-initiated logout, `post-logout-redirect-uri`). Spring Security's
  back-channel logout stays enabled. `GET /logout` no longer logs the user out.
- **Session expiry:** when the access token cannot be refreshed or the session times out,
  requests to `/fasti/api/**` return `401` (no redirect) and the new web application shows its login page.
- **CSRF:** `/fasti/api` and `/fasti/app` keep the web applications' own CSRF filter
  (`X-XSRF-TOKEN`); Spring Security's CSRF protection stays disabled, as upstream.
- **Groups, administrators, tenants:** the groups claim is
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` (upstream property name; nested
  claims such as `realm_access.roles` work). `fasti.security.oidc.groups.admin` lists the
  claim values that grant `camunda-admin`; `groups.mapping` maps claim values to engine group
  ids; `groups.include-unmapped=false` drops unmapped values. **Changed from upstream:** a
  claim value `camunda-admin` no longer grants administrator rights by itself, and
  authorities such as `SCOPE_openid` or `OIDC_USER` no longer become groups.
  `fasti.security.oidc.tenants.claim` maps tenants. Name and email:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Identity provider:** by default the engine reads users, groups and tenants from the
  token of the current session (read-only). Set
  `camunda.bpm.oauth2.identity-provider.enabled=false` to use the database instead (groups
  then come from the membership tables). Auto-provisioning is not implemented.
  While users come from the token (the default), the initial setup page (first administrator)
  is switched off, since administrators come from the claims (`groups.admin`); previously, users outside the administrators group were sent to the setup page.
- **REST API with bearer tokens:** `fasti.security.oidc.rest.bearer-enabled=true` accepts
  `Authorization: Bearer <JWT>` on `/engine-rest` in addition to basic authentication.
  `rest.issuer-uri` and `rest.audiences` are required; signatures are checked against the
  issuer's JWKS with asymmetric algorithms only (`rest.jws-algorithms`, default `RS256`), and
  `iss`, `aud`, `exp`, `nbf` are validated. Run also needs `camunda.bpm.run.auth.enabled=true`.
  A browser session cookie never authenticates `/engine-rest`.

Keycloak example (`configuration/default.yml` of Run):

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

Microsoft Entra ID example:

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

**Tomcat, WildFly (WAR):** the same features, configured in `fasti-oidc.properties` (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; an annotated `fasti-oidc.properties.example`
sits next to it). Without the file nothing changes. The keys match Run's `fasti.security.oidc.*` properties, plus `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`. The
provider must allow the redirect URI `<base>/fasti/sso/callback`; the back-channel logout URL is
`<base>/fasti/sso/backchannel-logout`. Bearer tokens on `/engine-rest` are enabled with
`fasti.security.oidc.rest.bearer-enabled=true` (basic authentication keeps working unless
`rest.basic-enabled=false`; the engine list `/engine` requires credentials too). Users need not
exist in the database. For the engine to answer queries about the groups and tenants of the
signed-in user from the claims, enable the `OidcIdentityProviderPlugin` (commented out in
`bpm-platform.xml` / `standalone.xml`). Limits: the sessions tracked for back-channel logout are
kept in memory per server (not shared across cluster nodes); the classic applications only redirect to the provider; a complete logout requires the new web UI.
