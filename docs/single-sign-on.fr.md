[English](single-sign-on.md) | [Tiếng Việt](single-sign-on.vi.md) | [Deutsch](single-sign-on.de.md) | [Español](single-sign-on.es.md) | Français | [हिन्दी](single-sign-on.hi.md) | [Bahasa Indonesia](single-sign-on.id.md) | [日本語](single-sign-on.ja.md) | [한국어](single-sign-on.ko.md) | [Português (Brasil)](single-sign-on.pt-BR.md) | [Русский](single-sign-on.ru.md) | [ไทย](single-sign-on.th.md) | [简体中文](single-sign-on.zh-CN.md)

> Cette traduction est maintenue par la communauté et peut être en retard sur la version anglaise ; la version anglaise fait foi. Les corrections sont les bienvenues.

<a id="single-sign-on-openid-connect"></a>

# Authentification unique (OpenID Connect)

Prise en charge sur **Run** (`./start.sh --oauth2`, module `internal/oauth2`) et dans les applications **Spring Boot**
avec `fasti-bpm-spring-boot-starter-security`. Elle est activée en déclarant un
enregistrement de client Spring Security (`spring.security.oauth2.client.*`). Pour **Tomcat** et
**WildFly**, voir la fin de cette section.

- **Connexion :** la page de connexion de la nouvelle application web affiche « Se connecter avec &lt;provider&gt; »
  à la place du formulaire de mot de passe (libellé : `fasti.security.oidc.login-label`, valeur par défaut :
  `client-name` ; avec plusieurs enregistrements, choisissez-en un avec
  `fasti.security.oidc.registration-id`). Le flux de code d’autorisation utilise `state`, `nonce`
  et PKCE (S256, y compris pour les clients confidentiels) ; l’identifiant de session est remplacé lors de la connexion. Les utilisateurs
  reviennent à la page de la nouvelle application web depuis laquelle ils ont commencé (seuls les chemins sous
  `/fasti/app/next/` sont acceptés, ce qui évite les redirections ouvertes).
- **Déconnexion :** `POST /logout` (requêtes de même origine uniquement) termine la session ; avec
  `camunda.bpm.oauth2.sso-logout.enabled=true`, l’utilisateur est ensuite dirigé vers le point de terminaison de fin de
  session du fournisseur (déconnexion initiée par la partie utilisatrice, `post-logout-redirect-uri`). La
  déconnexion par canal arrière de Spring Security reste activée. `GET /logout` ne déconnecte plus l’utilisateur.
- **Expiration de la session :** lorsque le jeton d’accès ne peut pas être renouvelé ou que la session expire,
  les requêtes vers `/fasti/api/**` renvoient `401` (sans redirection) et la nouvelle application web affiche sa page de connexion.
- **CSRF :** `/fasti/api` et `/fasti/app` conservent le filtre CSRF propre aux applications web
  (`X-XSRF-TOKEN`) ; la protection CSRF de Spring Security reste désactivée, comme dans le projet amont.
- **Groupes, administrateurs, tenants :** l’attribut de jeton contenant les groupes est
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` (nom de propriété du projet amont ; les attributs
  imbriqués tels que `realm_access.roles` fonctionnent). `fasti.security.oidc.groups.admin` répertorie les
  valeurs d’attribut qui accordent `camunda-admin` ; `groups.mapping` associe les valeurs d’attribut aux identifiants de groupe
  du moteur ; `groups.include-unmapped=false` élimine les valeurs sans correspondance. **Modification par rapport au projet amont :** une
  valeur d’attribut `camunda-admin` n’accorde plus à elle seule les droits d’administrateur, et
  les autorités telles que `SCOPE_openid` ou `OIDC_USER` ne deviennent plus des groupes.
  `fasti.security.oidc.tenants.claim` définit la correspondance des tenants. Nom et adresse e-mail :
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Fournisseur d’identité :** par défaut, le moteur lit les utilisateurs, groupes et tenants dans le
  jeton de la session courante (en lecture seule). Définissez
  `camunda.bpm.oauth2.identity-provider.enabled=false` pour utiliser la base de données à la place (les groupes
  proviennent alors des tables d’appartenance). Le provisionnement automatique n’est pas implémenté.
  Tant que les utilisateurs proviennent du jeton (comportement par défaut), la page de configuration initiale (premier administrateur)
  est désactivée, puisque les administrateurs proviennent des attributs du jeton (`groups.admin`) ; auparavant, les utilisateurs extérieurs au groupe des administrateurs étaient dirigés vers la page de configuration.
- **API REST avec jetons Bearer :** `fasti.security.oidc.rest.bearer-enabled=true` accepte
  `Authorization: Bearer <JWT>` sur `/engine-rest` en plus de l’authentification Basic.
  `rest.issuer-uri` et `rest.audiences` sont obligatoires ; les signatures sont vérifiées à partir du
  JWKS de l’émetteur avec des algorithmes asymétriques uniquement (`rest.jws-algorithms`, valeur par défaut : `RS256`), et
  `iss`, `aud`, `exp`, `nbf` sont validés. Run nécessite également `camunda.bpm.run.auth.enabled=true`.
  Un cookie de session de navigateur n’authentifie jamais les requêtes vers `/engine-rest`.

Exemple Keycloak (`configuration/default.yml` de Run) :

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

Exemple Microsoft Entra ID :

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

**Tomcat, WildFly (WAR) :** les mêmes fonctionnalités, configurées dans `fasti-oidc.properties` (Tomcat :
`conf/`, WildFly : `standalone/configuration/` ; un fichier annoté `fasti-oidc.properties.example`
se trouve à côté). Sans ce fichier, rien ne change. Les clés correspondent aux propriétés `fasti.security.oidc.*` de Run, auxquelles s’ajoutent `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`. Le
fournisseur doit autoriser l’URI de redirection `<base>/fasti/sso/callback` ; l’URL de déconnexion par canal arrière est
`<base>/fasti/sso/backchannel-logout`. Les jetons Bearer sur `/engine-rest` sont activés avec
`fasti.security.oidc.rest.bearer-enabled=true` (l’authentification Basic continue de fonctionner sauf si
`rest.basic-enabled=false` ; la liste des moteurs `/engine` exige également des identifiants). Les utilisateurs n’ont pas besoin
d’exister dans la base de données. Pour que le moteur réponde aux requêtes concernant les groupes et les tenants de
l’utilisateur connecté à partir des attributs du jeton, activez `OidcIdentityProviderPlugin` (mis en commentaire dans
`bpm-platform.xml` / `standalone.xml`). Limites : les sessions suivies pour la déconnexion par canal arrière sont
conservées en mémoire sur chaque serveur (elles ne sont pas partagées entre les nœuds du cluster) ; les applications classiques se contentent de rediriger vers le fournisseur ; une déconnexion complète nécessite la nouvelle interface web.
