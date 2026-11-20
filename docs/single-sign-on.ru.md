[English](single-sign-on.md) | [Tiếng Việt](single-sign-on.vi.md) | [Deutsch](single-sign-on.de.md) | [Español](single-sign-on.es.md) | [Français](single-sign-on.fr.md) | [हिन्दी](single-sign-on.hi.md) | [Bahasa Indonesia](single-sign-on.id.md) | [日本語](single-sign-on.ja.md) | [한국어](single-sign-on.ko.md) | [Português (Brasil)](single-sign-on.pt-BR.md) | Русский | [ไทย](single-sign-on.th.md) | [简体中文](single-sign-on.zh-CN.md)

> Этот перевод поддерживается сообществом и может отставать от английской версии; основной является английская версия. Исправления приветствуются.

<a id="single-sign-on-openid-connect"></a>

# Единый вход (OpenID Connect)

Поддерживается в **Run** (`./start.sh --oauth2`, модуль `internal/oauth2`) и приложениях **Spring Boot**
с `fasti-bpm-spring-boot-starter-security`. Включается объявлением
регистрации клиента Spring Security (`spring.security.oauth2.client.*`). Для **Tomcat** и
**WildFly** см. конец этого раздела.

- **Вход:** страница входа нового веб-приложения показывает «Войти через &lt;provider&gt;»
  вместо формы пароля (подпись: `fasti.security.oidc.login-label`, по умолчанию используется
  `client-name`; при нескольких регистрациях выберите одну с помощью
  `fasti.security.oidc.registration-id`). Процесс авторизации с использованием кода (Authorization Code Flow) использует `state`, `nonce`
  и PKCE (S256, в том числе для конфиденциальных клиентов); при входе идентификатор сеанса заменяется. Пользователи
  возвращаются на исходную страницу нового веб-приложения (допускаются только пути внутри
  `/fasti/app/next/`, что исключает открытую переадресацию).
- **Выход:** `POST /logout` (только запросы с того же источника) завершает сеанс; при
  `camunda.bpm.oauth2.sso-logout.enabled=true` пользователь переходит на конечную точку завершения
  сеанса провайдера (выход по инициативе RP, `post-logout-redirect-uri`). Выход по обратному каналу
  Spring Security остаётся включённым. `GET /logout` больше не выполняет выход.
- **Истечение сеанса:** когда токен доступа невозможно обновить или время сеанса истекает,
  запросы к `/fasti/api/**` возвращают `401` (без перенаправления), а новое веб-приложение показывает страницу входа.
- **CSRF:** `/fasti/api` и `/fasti/app` сохраняют собственный CSRF-фильтр веб-приложений
  (`X-XSRF-TOKEN`); защита CSRF в Spring Security остаётся отключённой, как в исходном проекте.
- **Группы, администраторы, арендаторы:** утверждение с группами задаётся свойством
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` (имя свойства из исходного проекта; вложенные
  утверждения, например `realm_access.roles`, поддерживаются). `fasti.security.oidc.groups.admin` перечисляет
  значения утверждений, предоставляющие `camunda-admin`; `groups.mapping` сопоставляет значения утверждений с идентификаторами
  групп движка; `groups.include-unmapped=false` отбрасывает несопоставленные значения. **Отличие от исходного проекта:**
  значение утверждения `camunda-admin` больше не предоставляет права администратора само по себе, а
  полномочия вроде `SCOPE_openid` или `OIDC_USER` больше не становятся группами.
  `fasti.security.oidc.tenants.claim` задаёт сопоставление арендаторов. Имя и электронная почта:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Провайдер идентификации:** по умолчанию движок читает пользователей, группы и арендаторов из
  токена текущего сеанса (только чтение). Задайте
  `camunda.bpm.oauth2.identity-provider.enabled=false`, чтобы использовать базу данных (в этом случае группы
  берутся из таблиц членства). Автоматическое создание учётных записей не реализовано.
  Пока пользователи берутся из токена (по умолчанию), страница первоначальной настройки (первый администратор)
  отключена, поскольку администраторы определяются утверждениями (`groups.admin`); ранее пользователи вне группы администраторов направлялись на страницу настройки.
- **REST API с токенами Bearer:** `fasti.security.oidc.rest.bearer-enabled=true` разрешает
  `Authorization: Bearer <JWT>` для `/engine-rest` в дополнение к аутентификации Basic.
  Обязательны `rest.issuer-uri` и `rest.audiences`; подписи проверяются по JWKS
  издателя только с асимметричными алгоритмами (`rest.jws-algorithms`, по умолчанию `RS256`),
  проверяются `iss`, `aud`, `exp`, `nbf`. Для Run также требуется `camunda.bpm.run.auth.enabled=true`.
  Файл cookie сеанса браузера никогда не используется для аутентификации в `/engine-rest`.

Пример Keycloak (`configuration/default.yml` в Run):

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

Пример Microsoft Entra ID:

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

**Tomcat, WildFly (WAR):** те же возможности настраиваются в `fasti-oidc.properties` (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; рядом находится `fasti-oidc.properties.example`
с пояснениями). Без этого файла ничего не меняется. Ключи соответствуют свойствам Run `fasti.security.oidc.*`, плюс `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`.
Провайдер должен разрешать URI перенаправления `<base>/fasti/sso/callback`; URL выхода по обратному каналу —
`<base>/fasti/sso/backchannel-logout`. Токены Bearer для `/engine-rest` включаются с помощью
`fasti.security.oidc.rest.bearer-enabled=true` (аутентификация Basic продолжает работать, если не задано
`rest.basic-enabled=false`; список движков `/engine` также требует учётных данных). Пользователи могут
отсутствовать в базе данных. Чтобы движок отвечал на запросы о группах и арендаторах
вошедшего пользователя на основе утверждений, включите `OidcIdentityProviderPlugin` (закомментирован в
`bpm-platform.xml` / `standalone.xml`). Ограничения: сеансы, отслеживаемые для выхода по обратному каналу,
хранятся в памяти каждого сервера (не разделяются между узлами кластера); классические приложения только перенаправляют к провайдеру; для полного выхода нужен новый веб-интерфейс.
