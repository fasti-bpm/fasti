[English](single-sign-on.md) | [Tiếng Việt](single-sign-on.vi.md) | [Deutsch](single-sign-on.de.md) | [Español](single-sign-on.es.md) | [Français](single-sign-on.fr.md) | [हिन्दी](single-sign-on.hi.md) | [Bahasa Indonesia](single-sign-on.id.md) | [日本語](single-sign-on.ja.md) | [한국어](single-sign-on.ko.md) | [Português (Brasil)](single-sign-on.pt-BR.md) | [Русский](single-sign-on.ru.md) | [ไทย](single-sign-on.th.md) | 简体中文

> 本译文由社区维护，可能落后于英文版；以英文版为准。欢迎指正。

<a id="single-sign-on-openid-connect"></a>

# 单点登录（OpenID Connect）

支持 **Run**（`./start.sh --oauth2`，模块 `internal/oauth2`）以及使用
`fasti-bpm-spring-boot-starter-security` 的 **Spring Boot**
应用。通过声明 Spring Security 客户端注册（`spring.security.oauth2.client.*`）启用。
**Tomcat** 和 **WildFly** 的说明见本节末尾。

- **登录：**新 Web 应用的登录页面显示“使用 &lt;provider&gt; 登录”，
  替代密码表单（标签：`fasti.security.oidc.login-label`，默认为
  `client-name`；存在多个注册时，通过
  `fasti.security.oidc.registration-id` 选择一个）。授权码流程使用 `state`、`nonce`
  和 PKCE（S256，也适用于机密客户端）；登录时会更换会话 ID。用户
  会返回新 Web 应用中最初访问的页面（仅接受
  `/fasti/app/next/` 下的路径，因此不存在开放重定向）。
- **注销：**`POST /logout`（仅限同源请求）会结束会话；设置
  `camunda.bpm.oauth2.sso-logout.enabled=true` 后，用户会继续访问提供方的会话
  结束端点（RP 发起的注销，`post-logout-redirect-uri`）。Spring Security 的
  后通道注销保持启用。`GET /logout` 不再执行用户注销。
- **会话过期：**当访问令牌无法刷新或会话超时时，
  对 `/fasti/api/**` 的请求返回 `401`（不重定向），新 Web 应用会显示登录页面。
- **CSRF：**`/fasti/api` 和 `/fasti/app` 保留 Web 应用自身的 CSRF 过滤器
  （`X-XSRF-TOKEN`）；与上游一致，Spring Security 的 CSRF 防护保持禁用。
- **组、管理员、租户：**组声明通过
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` 指定（沿用上游属性名；支持
  `realm_access.roles` 等嵌套声明）。`fasti.security.oidc.groups.admin` 列出
  可授予 `camunda-admin` 的声明值；`groups.mapping` 将声明值映射到引擎组
  ID；`groups.include-unmapped=false` 会丢弃未映射的值。**与上游的差异：**
  声明值 `camunda-admin` 本身不再自动授予管理员权限，
  `SCOPE_openid` 或 `OIDC_USER` 等权限项也不再转换为组。
  `fasti.security.oidc.tenants.claim` 用于映射租户。姓名和电子邮件：
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`。
- **身份提供方：**默认情况下，引擎从
  当前会话的令牌中读取用户、组和租户（只读）。设置
  `camunda.bpm.oauth2.identity-provider.enabled=false` 可改用数据库（此时组
  来自成员关系表）。尚未实现自动预配。
  当用户来自令牌时（默认行为），初始设置页面（首个管理员）
  会关闭，因为管理员来自声明（`groups.admin`）；此前，不属于管理员组的用户会被转到设置页面。
- **使用 Bearer 令牌的 REST API：**设置 `fasti.security.oidc.rest.bearer-enabled=true` 后，
  `/engine-rest` 除 Basic 身份验证外，还接受 `Authorization: Bearer <JWT>`。
  必须配置 `rest.issuer-uri` 和 `rest.audiences`；签名仅使用非对称算法，
  根据颁发者的 JWKS 进行校验（`rest.jws-algorithms`，默认为 `RS256`），并
  验证 `iss`、`aud`、`exp`、`nbf`。Run 还需设置 `camunda.bpm.run.auth.enabled=true`。
  浏览器会话 Cookie 绝不会用于 `/engine-rest` 的身份验证。

Keycloak 示例（Run 的 `configuration/default.yml`）：

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

Microsoft Entra ID 示例：

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

**Tomcat、WildFly（WAR）：**提供相同功能，通过 `fasti-oidc.properties` 配置（Tomcat：
`conf/`，WildFly：`standalone/configuration/`；同目录下提供带注释的 `fasti-oidc.properties.example`
文件）。没有该文件时，行为保持不变。配置键与 Run 的 `fasti.security.oidc.*` 属性一致，另有 `issuer-uri`、`client-id`、`client-secret`、`user-name-claim`、`groups-claim`。
提供方必须允许重定向 URI `<base>/fasti/sso/callback`；后通道注销 URL 为
`<base>/fasti/sso/backchannel-logout`。通过
`fasti.security.oidc.rest.bearer-enabled=true` 启用 `/engine-rest` 的 Bearer 令牌支持（除非设置
`rest.basic-enabled=false`，否则 Basic 身份验证仍然有效；引擎列表 `/engine` 也需要凭据）。用户无需
存在于数据库中。若要让引擎根据声明响应有关
已登录用户所属组和租户的查询，请启用 `OidcIdentityProviderPlugin`（在
`bpm-platform.xml` / `standalone.xml` 中默认被注释）。限制：用于后通道注销跟踪的会话
保存在各服务器的内存中（不在集群节点间共享）；经典应用仅重定向到提供方；完整注销需要使用新的 Web UI。
