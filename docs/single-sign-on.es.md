[English](single-sign-on.md) | [Tiếng Việt](single-sign-on.vi.md) | [Deutsch](single-sign-on.de.md) | Español | [Français](single-sign-on.fr.md) | [हिन्दी](single-sign-on.hi.md) | [Bahasa Indonesia](single-sign-on.id.md) | [日本語](single-sign-on.ja.md) | [한국어](single-sign-on.ko.md) | [Português (Brasil)](single-sign-on.pt-BR.md) | [Русский](single-sign-on.ru.md) | [ไทย](single-sign-on.th.md) | [简体中文](single-sign-on.zh-CN.md)

> Esta traducción la mantiene la comunidad y puede quedar por detrás de la versión en inglés; la versión en inglés es la de referencia. Las correcciones son bienvenidas.

<a id="single-sign-on-openid-connect"></a>

# Inicio de sesión único (OpenID Connect)

Compatible con **Run** (`./start.sh --oauth2`, módulo `internal/oauth2`) y con aplicaciones **Spring Boot**
que utilicen `fasti-bpm-spring-boot-starter-security`. Se activa declarando un
registro de cliente de Spring Security (`spring.security.oauth2.client.*`). Para **Tomcat** y
**WildFly**, consulte el final de esta sección.

- **Inicio de sesión:** la página de inicio de sesión de la nueva aplicación web muestra «Iniciar sesión con &lt;proveedor&gt;»
  en lugar del formulario de contraseña (etiqueta: `fasti.security.oidc.login-label`, cuyo valor predeterminado es
  `client-name`; si hay varios registros, elija uno con
  `fasti.security.oidc.registration-id`). El flujo de código de autorización utiliza `state`, `nonce`
  y PKCE (S256, también para clientes confidenciales); el identificador de sesión se sustituye al iniciar sesión. Los usuarios
  vuelven a la página de la nueva aplicación web desde la que iniciaron el proceso (solo se aceptan rutas bajo
  `/fasti/app/next/`, por lo que no hay redirecciones abiertas).
- **Cierre de sesión:** `POST /logout` (solo solicitudes del mismo origen) finaliza la sesión; con
  `camunda.bpm.oauth2.sso-logout.enabled=true`, el usuario continúa al endpoint de finalización
  de sesión del proveedor (cierre de sesión iniciado por el RP, `post-logout-redirect-uri`). El cierre de sesión
  por canal posterior de Spring Security sigue activado. `GET /logout` ya no cierra la sesión del usuario.
- **Caducidad de la sesión:** cuando no se puede renovar el token de acceso o la sesión caduca,
  las solicitudes a `/fasti/api/**` devuelven `401` (sin redirección) y la nueva aplicación web muestra su página de inicio de sesión.
- **CSRF:** `/fasti/api` y `/fasti/app` conservan el filtro CSRF propio de las aplicaciones web
  (`X-XSRF-TOKEN`); la protección CSRF de Spring Security permanece desactivada, como en el proyecto original.
- **Grupos, administradores, inquilinos:** el atributo de identidad de grupos se configura con
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` (nombre de propiedad del proyecto original; se admiten
  atributos anidados como `realm_access.roles`). `fasti.security.oidc.groups.admin` enumera los
  valores de atributo que conceden `camunda-admin`; `groups.mapping` asigna valores de atributo a identificadores de grupo
  del motor; `groups.include-unmapped=false` descarta los valores sin asignación. **Cambio respecto al proyecto original:** un
  valor de atributo `camunda-admin` ya no concede derechos de administrador por sí solo, y
  las autoridades como `SCOPE_openid` u `OIDC_USER` ya no se convierten en grupos.
  `fasti.security.oidc.tenants.claim` asigna los inquilinos. Nombre y correo electrónico:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Proveedor de identidad:** de forma predeterminada, el motor lee los usuarios, grupos e inquilinos del
  token de la sesión actual (solo lectura). Configure
  `camunda.bpm.oauth2.identity-provider.enabled=false` para utilizar la base de datos en su lugar (los grupos
  procederán entonces de las tablas de pertenencia). El aprovisionamiento automático no está implementado.
  Mientras los usuarios procedan del token (el valor predeterminado), la página de configuración inicial (primer administrador)
  permanece desactivada, ya que los administradores proceden de los atributos de identidad (`groups.admin`); antes, los usuarios ajenos al grupo de administradores eran enviados a la página de configuración.
- **API REST con tokens de portador:** `fasti.security.oidc.rest.bearer-enabled=true` acepta
  `Authorization: Bearer <JWT>` en `/engine-rest`, además de la autenticación básica.
  `rest.issuer-uri` y `rest.audiences` son obligatorios; las firmas se comprueban con el
  JWKS del emisor utilizando únicamente algoritmos asimétricos (`rest.jws-algorithms`, valor predeterminado `RS256`), y
  se validan `iss`, `aud`, `exp`, `nbf`. Run también necesita `camunda.bpm.run.auth.enabled=true`.
  Una cookie de sesión del navegador nunca autentica `/engine-rest`.

Ejemplo de Keycloak (`configuration/default.yml` de Run):

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

Ejemplo de Microsoft Entra ID:

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

**Tomcat, WildFly (WAR):** las mismas funciones, configuradas en `fasti-oidc.properties` (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; junto a él hay un archivo `fasti-oidc.properties.example`
con anotaciones). Sin este archivo no cambia nada. Las claves coinciden con las propiedades `fasti.security.oidc.*` de Run, además de `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`. El
proveedor debe permitir el URI de redirección `<base>/fasti/sso/callback`; la URL de cierre de sesión por canal posterior es
`<base>/fasti/sso/backchannel-logout`. Los tokens de portador en `/engine-rest` se activan con
`fasti.security.oidc.rest.bearer-enabled=true` (la autenticación básica sigue funcionando salvo que se configure
`rest.basic-enabled=false`; la lista de motores `/engine` también requiere credenciales). Los usuarios no tienen que
existir en la base de datos. Para que el motor responda a consultas sobre los grupos e inquilinos del
usuario que ha iniciado sesión a partir de sus atributos de identidad, active `OidcIdentityProviderPlugin` (comentado en
`bpm-platform.xml` / `standalone.xml`). Limitaciones: las sesiones registradas para el cierre de sesión por canal posterior se
conservan en memoria por servidor (no se comparten entre los nodos del clúster); las aplicaciones clásicas solo redirigen al proveedor; un cierre de sesión completo requiere la nueva interfaz web.
