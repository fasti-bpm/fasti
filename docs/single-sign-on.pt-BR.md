[English](single-sign-on.md) | [Tiếng Việt](single-sign-on.vi.md) | [Deutsch](single-sign-on.de.md) | [Español](single-sign-on.es.md) | [Français](single-sign-on.fr.md) | [हिन्दी](single-sign-on.hi.md) | [Bahasa Indonesia](single-sign-on.id.md) | [日本語](single-sign-on.ja.md) | [한국어](single-sign-on.ko.md) | Português (Brasil) | [Русский](single-sign-on.ru.md) | [ไทย](single-sign-on.th.md) | [简体中文](single-sign-on.zh-CN.md)

> Esta tradução é mantida pela comunidade e pode estar defasada em relação à versão em inglês; a versão em inglês é a oficial. Correções são bem-vindas.

<a id="single-sign-on-openid-connect"></a>

# Autenticação única (OpenID Connect)

Disponível no **Run** (`./start.sh --oauth2`, módulo `internal/oauth2`) e em aplicações **Spring Boot**
com `fasti-bpm-spring-boot-starter-security`. É ativada ao declarar um
registro de cliente do Spring Security (`spring.security.oauth2.client.*`). Para **Tomcat** e
**WildFly**, consulte o final desta seção.

- **Login:** a página de login da nova aplicação web mostra "Entrar com &lt;provedor&gt;"
  em vez do formulário de senha (rótulo: `fasti.security.oidc.login-label`, usa por padrão o
  `client-name`; com vários registros, escolha um com
  `fasti.security.oidc.registration-id`). O fluxo de código de autorização usa `state`, `nonce`
  e PKCE (S256, também para clientes confidenciais); o ID da sessão é substituído no login. Os usuários
  retornam à página da nova aplicação web de onde partiram (somente caminhos sob
  `/fasti/app/next/` são aceitos, portanto não há redirecionamento aberto).
- **Logout:** `POST /logout` (somente solicitações da mesma origem) encerra a sessão; com
  `camunda.bpm.oauth2.sso-logout.enabled=true`, o usuário segue para o endpoint de encerramento de
  sessão do provedor (logout iniciado pelo RP, `post-logout-redirect-uri`). O logout por
  canal de retaguarda do Spring Security permanece ativado. `GET /logout` não encerra mais a sessão do usuário.
- **Expiração da sessão:** quando o token de acesso não pode ser renovado ou a sessão expira,
  as solicitações para `/fasti/api/**` retornam `401` (sem redirecionamento), e a nova aplicação web mostra sua página de login.
- **CSRF:** `/fasti/api` e `/fasti/app` mantêm o filtro CSRF próprio das aplicações web
  (`X-XSRF-TOKEN`); a proteção CSRF do Spring Security permanece desativada, como no projeto de origem.
- **Grupos, administradores, tenants:** a declaração de grupos é
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` (nome da propriedade no projeto de origem; declarações
  aninhadas, como `realm_access.roles`, funcionam). `fasti.security.oidc.groups.admin` lista os
  valores de declaração que concedem `camunda-admin`; `groups.mapping` mapeia valores de declaração para IDs de
  grupos do motor; `groups.include-unmapped=false` descarta valores não mapeados. **Alteração em relação ao projeto de origem:** um
  valor de declaração `camunda-admin` não concede mais direitos de administrador por si só, e
  autoridades como `SCOPE_openid` ou `OIDC_USER` não se tornam mais grupos.
  `fasti.security.oidc.tenants.claim` mapeia tenants. Nome e e-mail:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Provedor de identidade:** por padrão, o motor lê usuários, grupos e tenants do
  token da sessão atual (somente leitura). Defina
  `camunda.bpm.oauth2.identity-provider.enabled=false` para usar o banco de dados em seu lugar (os grupos
  passam a vir das tabelas de associação). O provisionamento automático não está implementado.
  Enquanto os usuários vêm do token (o padrão), a página de configuração inicial (primeiro administrador)
  fica desativada, pois os administradores vêm das declarações (`groups.admin`); anteriormente, usuários fora do grupo de administradores eram enviados à página de configuração.
- **API REST com tokens bearer:** `fasti.security.oidc.rest.bearer-enabled=true` aceita
  `Authorization: Bearer <JWT>` em `/engine-rest`, além da autenticação básica.
  `rest.issuer-uri` e `rest.audiences` são obrigatórios; as assinaturas são verificadas com o
  JWKS do emissor usando apenas algoritmos assimétricos (`rest.jws-algorithms`, padrão `RS256`), e
  `iss`, `aud`, `exp`, `nbf` são validados. O Run também exige `camunda.bpm.run.auth.enabled=true`.
  Um cookie de sessão do navegador nunca autentica `/engine-rest`.

Exemplo com Keycloak (`configuration/default.yml` do Run):

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

Exemplo com Microsoft Entra ID:

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

**Tomcat, WildFly (WAR):** os mesmos recursos, configurados em `fasti-oidc.properties` (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; um `fasti-oidc.properties.example` com anotações
fica ao lado dele). Sem o arquivo, nada muda. As chaves correspondem às propriedades `fasti.security.oidc.*` do Run, mais `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`. O
provedor deve permitir a URI de redirecionamento `<base>/fasti/sso/callback`; a URL de logout por canal de retaguarda é
`<base>/fasti/sso/backchannel-logout`. Os tokens bearer em `/engine-rest` são ativados com
`fasti.security.oidc.rest.bearer-enabled=true` (a autenticação básica continua funcionando, a menos que
`rest.basic-enabled=false`; a lista de motores `/engine` também exige credenciais). Os usuários não precisam
existir no banco de dados. Para que o motor responda a consultas sobre os grupos e tenants do
usuário conectado a partir das declarações, ative o `OidcIdentityProviderPlugin` (comentado em
`bpm-platform.xml` / `standalone.xml`). Limitações: as sessões rastreadas para logout por canal de retaguarda são
mantidas na memória de cada servidor (não são compartilhadas entre nós do cluster); as aplicações clássicas apenas redirecionam para o provedor; um logout completo exige a nova interface web.
