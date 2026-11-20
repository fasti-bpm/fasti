[English](single-sign-on.md) | [Tiếng Việt](single-sign-on.vi.md) | Deutsch | [Español](single-sign-on.es.md) | [Français](single-sign-on.fr.md) | [हिन्दी](single-sign-on.hi.md) | [Bahasa Indonesia](single-sign-on.id.md) | [日本語](single-sign-on.ja.md) | [한국어](single-sign-on.ko.md) | [Português (Brasil)](single-sign-on.pt-BR.md) | [Русский](single-sign-on.ru.md) | [ไทย](single-sign-on.th.md) | [简体中文](single-sign-on.zh-CN.md)

> Diese Übersetzung wird von der Community gepflegt und kann hinter der englischen Fassung zurückliegen; maßgeblich ist die englische Fassung. Korrekturen sind willkommen.

<a id="single-sign-on-openid-connect"></a>

# Single Sign-on (OpenID Connect)

Unterstützt auf **Run** (`./start.sh --oauth2`, Modul `internal/oauth2`) und in **Spring Boot**-
Anwendungen mit `fasti-bpm-spring-boot-starter-security`. Die Aktivierung erfolgt durch Deklaration einer
Spring Security-Clientregistrierung (`spring.security.oauth2.client.*`). Hinweise zu **Tomcat** und
**WildFly** finden Sie am Ende dieses Abschnitts.

- **Anmeldung:** Die Anmeldeseite der neuen Webanwendung zeigt „Mit &lt;provider&gt; anmelden“
  anstelle des Passwortformulars (Beschriftung: `fasti.security.oidc.login-label`, standardmäßig der
  `client-name`; wählen Sie bei mehreren Registrierungen eine mit
  `fasti.security.oidc.registration-id` aus). Der Authorization-Code-Flow verwendet `state`, `nonce`
  und PKCE (S256, auch für vertrauliche Clients); die Sitzungs-ID wird bei der Anmeldung ersetzt. Benutzer
  kehren zu der Seite der neuen Webanwendung zurück, von der aus sie gestartet sind (nur Pfade unter
  `/fasti/app/next/` werden akzeptiert, sodass keine offene Weiterleitung möglich ist).
- **Abmeldung:** `POST /logout` (nur Anfragen derselben Herkunft) beendet die Sitzung; mit
  `camunda.bpm.oauth2.sso-logout.enabled=true` wird der Benutzer zum End-
  Session-Endpunkt des Anbieters weitergeleitet (RP-initiierte Abmeldung, `post-logout-redirect-uri`). Die
  Back-Channel-Abmeldung von Spring Security bleibt aktiviert. `GET /logout` meldet den Benutzer nicht mehr ab.
- **Sitzungsablauf:** Wenn das Zugriffstoken nicht erneuert werden kann oder die Sitzung abläuft,
  liefern Anfragen an `/fasti/api/**` den Status `401` (ohne Weiterleitung), und die neue Webanwendung zeigt ihre Anmeldeseite.
- **CSRF:** `/fasti/api` und `/fasti/app` behalten den eigenen CSRF-Filter der Webanwendungen
  (`X-XSRF-TOKEN`); der CSRF-Schutz von Spring Security bleibt wie im Ursprungsprojekt deaktiviert.
- **Gruppen, Administratoren, Mandanten:** Der Gruppen-Claim wird über
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` festgelegt (Eigenschaftsname aus dem Ursprungsprojekt; verschachtelte
  Claims wie `realm_access.roles` funktionieren). `fasti.security.oidc.groups.admin` führt die
  Claim-Werte auf, die `camunda-admin` gewähren; `groups.mapping` ordnet Claim-Werte Gruppen-
  IDs der Engine zu; `groups.include-unmapped=false` verwirft nicht zugeordnete Werte. **Änderung gegenüber dem Ursprungsprojekt:** Ein
  Claim-Wert `camunda-admin` gewährt für sich allein keine Administratorrechte mehr, und
  Berechtigungen wie `SCOPE_openid` oder `OIDC_USER` werden nicht mehr zu Gruppen.
  `fasti.security.oidc.tenants.claim` ordnet Mandanten zu. Name und E-Mail:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Identitätsanbieter:** Standardmäßig liest die Engine Benutzer, Gruppen und Mandanten aus dem
  Token der aktuellen Sitzung (schreibgeschützt). Setzen Sie
  `camunda.bpm.oauth2.identity-provider.enabled=false`, um stattdessen die Datenbank zu verwenden (Gruppen
  stammen dann aus den Mitgliedschaftstabellen). Automatische Provisionierung ist nicht implementiert.
  Solange Benutzer aus dem Token stammen (Standard), ist die Seite zur Ersteinrichtung (erster Administrator)
  deaktiviert, da Administratoren aus den Claims (`groups.admin`) hervorgehen; zuvor wurden Benutzer außerhalb der Administratorengruppe zur Einrichtungsseite weitergeleitet.
- **REST-API mit Bearer-Token:** `fasti.security.oidc.rest.bearer-enabled=true` akzeptiert
  `Authorization: Bearer <JWT>` auf `/engine-rest` zusätzlich zur Basic-Authentifizierung.
  `rest.issuer-uri` und `rest.audiences` sind erforderlich; Signaturen werden anhand des
  JWKS des Ausstellers ausschließlich mit asymmetrischen Algorithmen geprüft (`rest.jws-algorithms`, standardmäßig `RS256`), und
  `iss`, `aud`, `exp`, `nbf` werden validiert. Run benötigt außerdem `camunda.bpm.run.auth.enabled=true`.
  Ein Browser-Sitzungscookie authentifiziert niemals Anfragen an `/engine-rest`.

Keycloak-Beispiel (`configuration/default.yml` von Run):

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

Microsoft Entra ID-Beispiel:

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

**Tomcat, WildFly (WAR):** Dieselben Funktionen, konfiguriert in `fasti-oidc.properties` (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; eine kommentierte `fasti-oidc.properties.example`
liegt daneben). Ohne diese Datei ändert sich nichts. Die Schlüssel entsprechen den `fasti.security.oidc.*`-Eigenschaften von Run, ergänzt um `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`. Der
Anbieter muss die Weiterleitungs-URI `<base>/fasti/sso/callback` zulassen; die URL für die Back-Channel-Abmeldung lautet
`<base>/fasti/sso/backchannel-logout`. Bearer-Token auf `/engine-rest` werden mit
`fasti.security.oidc.rest.bearer-enabled=true` aktiviert (Basic-Authentifizierung funktioniert weiterhin, sofern nicht
`rest.basic-enabled=false` gesetzt ist; auch die Engine-Liste `/engine` erfordert Zugangsdaten). Benutzer müssen nicht
in der Datenbank vorhanden sein. Damit die Engine Abfragen zu Gruppen und Mandanten des
angemeldeten Benutzers anhand der Claims beantworten kann, aktivieren Sie das `OidcIdentityProviderPlugin` (auskommentiert in
`bpm-platform.xml` / `standalone.xml`). Einschränkungen: Die für die Back-Channel-Abmeldung erfassten Sitzungen werden
je Server im Arbeitsspeicher gehalten (nicht zwischen Cluster-Knoten geteilt); die klassischen Anwendungen leiten nur zum Anbieter weiter; eine vollständige Abmeldung erfordert die neue Weboberfläche.
