[English](single-sign-on.md) | [Tiếng Việt](single-sign-on.vi.md) | [Deutsch](single-sign-on.de.md) | [Español](single-sign-on.es.md) | [Français](single-sign-on.fr.md) | [हिन्दी](single-sign-on.hi.md) | [Bahasa Indonesia](single-sign-on.id.md) | 日本語 | [한국어](single-sign-on.ko.md) | [Português (Brasil)](single-sign-on.pt-BR.md) | [Русский](single-sign-on.ru.md) | [ไทย](single-sign-on.th.md) | [简体中文](single-sign-on.zh-CN.md)

> この翻訳はコミュニティによって管理されており、英語版より古い場合があります。正式な内容は英語版です。修正を歓迎します。

<a id="single-sign-on-openid-connect"></a>

# シングルサインオン（OpenID Connect）

**Run**（`./start.sh --oauth2`、モジュール `internal/oauth2`）および
`fasti-bpm-spring-boot-starter-security` を使用する **Spring Boot**
アプリケーションでサポートしています。Spring Security のクライアント登録
（`spring.security.oauth2.client.*`）を宣言すると有効になります。**Tomcat** と **WildFly** については、このセクションの末尾を参照してください。

- **ログイン：** 新しい Web アプリケーションのログインページには、パスワードフォームの代わりに
  「&lt;provider&gt; でサインイン」が表示されます（ラベル：`fasti.security.oidc.login-label`、デフォルトは
  `client-name`；複数の登録がある場合は
  `fasti.security.oidc.registration-id` で選択）。認可コードフローでは `state`、`nonce`、
  PKCE（S256、機密クライアントでも使用）を使用し、ログイン時にセッション ID を更新します。ユーザーは
  新しい Web アプリケーションで最初に開いたページに戻ります（受け付けるのは
  `/fasti/app/next/` 配下のパスのみなので、オープンリダイレクトは発生しません）。
- **ログアウト：** `POST /logout`（同一オリジンのリクエストのみ）でセッションを終了します。
  `camunda.bpm.oauth2.sso-logout.enabled=true` の場合、ユーザーはプロバイダーのセッション終了
  エンドポイントへ進みます（RP 起点のログアウト、`post-logout-redirect-uri`）。Spring Security の
  バックチャネルログアウトは有効のままです。`GET /logout` ではログアウトしなくなりました。
- **セッションの有効期限切れ：** アクセストークンを更新できない場合やセッションがタイムアウトした場合、
  `/fasti/api/**` へのリクエストには `401` を返し（リダイレクトなし）、新しい Web アプリケーションにログインページを表示します。
- **CSRF：** `/fasti/api` と `/fasti/app` では、Web アプリケーション独自の CSRF フィルター
  （`X-XSRF-TOKEN`）を維持します。Spring Security の CSRF 保護は、上流と同様に無効のままです。
- **グループ、管理者、テナント：** グループのクレームは
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` で指定します（上流のプロパティ名；
  `realm_access.roles` などのネストしたクレームにも対応）。`fasti.security.oidc.groups.admin` は、
  `camunda-admin` を付与するクレーム値の一覧です。`groups.mapping` はクレーム値をエンジンのグループ
  ID にマッピングし、`groups.include-unmapped=false` はマッピングされていない値を除外します。**上流からの変更点：**
  クレーム値 `camunda-admin` だけで管理者権限が付与されることはなくなり、
  `SCOPE_openid` や `OIDC_USER` などの権限がグループになることもなくなりました。
  `fasti.security.oidc.tenants.claim` でテナントをマッピングします。名前とメールアドレス：
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`。
- **ID プロバイダー：** デフォルトでは、エンジンは現在のセッションのトークンからユーザー、グループ、テナントを
  読み取ります（読み取り専用）。
  `camunda.bpm.oauth2.identity-provider.enabled=false` を設定すると、代わりにデータベースを使用します
  （この場合、グループはメンバーシップテーブルから取得します）。自動プロビジョニングは実装していません。
  ユーザー情報をトークンから取得する間（デフォルト）は、管理者をクレーム（`groups.admin`）で指定するため、
  初期セットアップページ（最初の管理者）は無効になります。以前は、管理者グループに属さないユーザーがセットアップページへ送られていました。
- **Bearer トークンによる REST API：** `fasti.security.oidc.rest.bearer-enabled=true` を指定すると、
  `/engine-rest` で Basic 認証に加えて `Authorization: Bearer <JWT>` を受け付けます。
  `rest.issuer-uri` と `rest.audiences` は必須です。署名は非対称アルゴリズムのみを使用して
  発行者の JWKS で検証し（`rest.jws-algorithms`、デフォルトは `RS256`）、
  `iss`、`aud`、`exp`、`nbf` も検証します。Run では `camunda.bpm.run.auth.enabled=true` も必要です。
  ブラウザーのセッション Cookie が `/engine-rest` の認証に使用されることはありません。

Keycloak の例（Run の `configuration/default.yml`）：

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

Microsoft Entra ID の例：

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

**Tomcat、WildFly（WAR）：** 同じ機能を `fasti-oidc.properties` で設定できます（Tomcat：
`conf/`、WildFly：`standalone/configuration/`；同じ場所に注釈付きの `fasti-oidc.properties.example`
があります）。このファイルがなければ何も変わりません。キーは Run の `fasti.security.oidc.*` プロパティと同じで、さらに `issuer-uri`、`client-id`、`client-secret`、`user-name-claim`、`groups-claim` を使用します。
プロバイダーではリダイレクト URI `<base>/fasti/sso/callback` を許可する必要があります。バックチャネルログアウト URL は
`<base>/fasti/sso/backchannel-logout` です。`/engine-rest` の Bearer トークンは
`fasti.security.oidc.rest.bearer-enabled=true` で有効にします（`rest.basic-enabled=false` にしない限り
Basic 認証も引き続き使用できます。エンジン一覧の `/engine` にも認証情報が必要です）。ユーザーが
データベースに存在する必要はありません。エンジンがサインイン中のユーザーのグループとテナントに関する
クエリにクレームを使って応答するには、`OidcIdentityProviderPlugin` を有効にしてください
（`bpm-platform.xml` / `standalone.xml` ではコメントアウトされています）。制限事項：バックチャネルログアウト用に追跡する
セッションはサーバーごとのメモリに保持されます（クラスターノード間では共有されません）。従来のアプリケーションはプロバイダーへのリダイレクトのみを行い、完全なログアウトには新しい Web UI が必要です。
