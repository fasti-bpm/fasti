[English](migrate-from-camunda-7.md) | [Tiếng Việt](migrate-from-camunda-7.vi.md) | [Deutsch](migrate-from-camunda-7.de.md) | [Español](migrate-from-camunda-7.es.md) | [Français](migrate-from-camunda-7.fr.md) | [हिन्दी](migrate-from-camunda-7.hi.md) | [Bahasa Indonesia](migrate-from-camunda-7.id.md) | 日本語 | [한국어](migrate-from-camunda-7.ko.md) | [Português (Brasil)](migrate-from-camunda-7.pt-BR.md) | [Русский](migrate-from-camunda-7.ru.md) | [ไทย](migrate-from-camunda-7.th.md) | [简体中文](migrate-from-camunda-7.zh-CN.md)

> この翻訳はコミュニティによって管理されており、英語版より古い場合があります。正式な内容は英語版です。修正を歓迎します。

# Camunda 7 から Fasti BPM への移行

Fasti BPM は Camunda 7 Community Edition 7.24.0 をベースに構築されており、Camunda 7 の
データベースと互換性があります。独立したプロジェクトであり、Camunda Services GmbH との提携関係はありません。

<a id="summary"></a>

## 概要

Camunda 7 **7.24**（Run、コンテナーイメージ、Spring Boot starter、Tomcat または
WildFly）を実行している場合、Fasti は**スキーマを変更せずに、その同じデータベースで動作します**。7.24 の
スキーマをそのまま使用し、独自のテーブル、列、スキーマバージョンを書き込むことはありません。既存の
ノードを置き換えたり、並行して Fasti を実行したりでき、Camunda 7.24 に戻すこともできます
（[ロールバック](#rollback-to-camunda-724)と、[制限事項](#limits)にあるテスト済みデータベースを参照）。

Camunda 7.23 以前のデータベースは、まず SQL スクリプトで 7.24 にアップグレードする必要があります（
[旧バージョン](#older-versions-717723)を参照）。Fasti 自体がスキーマをアップグレードすることはありません。古いスキーマでは
起動を拒否し、実行すべきスクリプト名を示します。

<a id="steps-container-image"></a>

## 手順（コンテナーイメージ）

1. **データベースをバックアップします。** 以下の手順ではスキーマは変更されませんが、本番システムに
   変更を加える前には必ずバックアップを取得してください。
2. **スキーマバージョンを確認します。** `select VERSION_ from ACT_GE_SCHEMA_LOG` — 最大値が
   `7.24.x` である必要があります。
3. **既存ノードの現在の設定を記録します。** `DB_*` 変数、履歴レベル
   （`camunda.bpm.history-level`、`ACT_GE_PROPERTY.historyLevel`）、REST 認証
   （`camunda.bpm.run.auth.enabled`）、認可（`camunda.bpm.authorization.enabled`）、
   プラグイン、LDAP/SSO、JVM のタイムゾーン、パスワードのハッシュ化（`passwordEncryptor`、`saltGenerator`）、
   テーブルのプレフィックス／スキーマ。
4. **イメージ** `fastidev/fasti-bpm:1.3.0` **を取得します**（または
   [distro/docker/README.md](../distro/docker/README.md) の説明に従ってソースからビルドします）。
5. **同じ `DB_*` 変数**（および同じ `TZ`）**で Fasti を起動します**。Camunda Run とは異なり、
   Fasti イメージは**デフォルトで REST 認証を必須とし、認可を強制します**。データベース内の
   アカウントは引き続き使用できますが、すべての REST クライアントが Basic 認証情報または Bearer トークンを送信する必要があり、
   `camunda-admin` に属さないユーザーには `ACT_RU_AUTHORIZATION` で権限を付与する必要があります（
   [distro/docker/README.md](../distro/docker/README.md) の「デフォルトで安全な設定」を参照）。
   `FASTI_INSECURE_DEMO=true` はデモ専用です。Fasti は次のログを出力します。
   `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
6. **確認します。** 既存のアカウントで `/fasti/` にサインインし、フィルター、タスク、インスタンス、履歴が存在することを確認します。`GET /engine-rest/task`（Basic 認証情報付き）と `GET /actuator/health/readiness` を呼び出します。
7. **トラフィックを Fasti に切り替えます**（ロードバランサー、DNS）。既存ノードを停止するか、そのまま稼働させます（
   [混在運用](#mixed-operation-with-camunda-7-nodes)を参照）。

Spring Boot starter / Tomcat / WildFly：`org.camunda.bpm:camunda-*` アーティファクトを
対応する `dev.fasti.platform:fasti-*` アーティファクトに置き換え、`camunda.bpm.*`、`bpm-platform.xml`、
`org.camunda.bpm.*` パッケージは維持します。両方のエンジンの jar をクラスパスに残さないでください。両方が存在すると Fasti は起動を拒否します（このチェックの制限については、
[embedding.ja.md](embedding.ja.md#startup-check) の「起動時のチェック」を参照）。

> **Spring Boot starter：** Fasti は **Spring Boot 4.1** 上で動作し、**Java 17 以降**が必要です。
> Camunda 7.24 starter（Spring Boot 3.5）を使用するアプリケーションは、アーティファクトの切り替えと同時に Spring Boot 4.1 と
> Java 17 以降へ移行する必要があります。
> [embedding.ja.md](embedding.ja.md#what-is-different-from-camunda-7240-ce) および
> [変更履歴](../CHANGELOG.md#changed)を参照してください。

<a id="tomcat-and-wildfly-distributions"></a>

## Tomcat および WildFly ディストリビューション

> **Camunda Run のデータベースを引き継ぐ場合に必須です**（REST API 経由でプロセスを
> デプロイしたサーバーの場合も同様）。Tomcat および WildFly ディストリビューションでは、Camunda 7 と同様に
> `jobExecutorDeploymentAware=true` が維持されます。ノードに登録されていないデプロイメントのジョブ（タイマー、非同期継続、再試行）は
> **実行されず、エラーも報告されません**。
> 起動前に、Tomcat の場合は
> `conf/bpm-platform.xml`、WildFly の場合は `standalone.xml` のエンジンプロパティで `<property name="jobExecutorDeploymentAware">false</property>` を設定してください。
> 他のノードで独自の Java クラスを持つプロセスアプリケーションが実行されている場合は、`true` を維持してください（
> [混在運用](#mixed-operation-with-camunda-7-nodes)を参照）。

Camunda 7.24 の Tomcat/WildFly ディストリビューションと比較すると、次の違いがあります。

- **REST API は認証を必須とします**（破壊的変更）。`/engine-rest` では `camunda-auth`
  フィルター（HTTP Basic）が有効です。すべての REST クライアントはエンジンユーザーのユーザー名とパスワードを送信する必要があり、送信しない場合は 401 が返されます。エンジンの一覧（`/engine`）のみ、引き続き認証なしでアクセスできます。
  切り替え前に、REST API を匿名で呼び出している REST クライアント、外部タスクワーカー、スクリプトに
  認証情報を設定してください。OpenID Connect の Bearer トークン（`fasti-oidc.properties`、
  `fasti.security.oidc.rest.bearer-enabled=true`）も使用できます。有効なトークンを持つリクエストにはパスワードは不要です。認証を無効にするには（**非推奨**）、
  `engine-rest` WAR の `WEB-INF/web.xml` から `camunda-auth` フィルターとその `filter-mapping` を削除します。
- **デフォルトユーザーはありません**（破壊的変更）。請求書のサンプルはデプロイされなくなったため、
  `demo/demo`（管理者）、`john`、`mary`、`peter` は作成されません。その WAR は
  `examples/camunda-invoice.war` にあり、試すには `webapps/`（Tomcat）または
  `standalone/deployments/`（WildFly）にコピーします。空のデータベースでは `/fasti/` を開き、
  セットアップページで最初の管理者を作成してください。Camunda 7 のデータベースでは、
  既存のアカウントを使用してください。
- **従来の Cockpit、Tasklist、Admin、Welcome は提供されません**（破壊的変更）。これは
  イメージと同じ動作です。これらのページやブックマークは新しい UI の対応するセクションに移動し、これらのファイルへのリクエストには 404 が返されます。一時的に再び有効にするには（非推奨で、今後削除予定）、次のいずれかを設定します。
  `CATALINA_OPTS`（Tomcat）または `JAVA_OPTS`（WildFly）に `-Dfasti.webapp.classic.enabled=true`、
  環境変数
  `FASTI_CLASSIC_WEBAPPS=true`、または Web アプリケーションの `WEB-INF/web.xml` にコンテキストパラメーター
  `fasti.webapp.classic.enabled` を設定します。

<a id="what-is-identical"></a>

## 変わらない点

- スキーマと `ACT_GE_PROPERTY`/`ACT_GE_SCHEMA_LOG` の行（Fasti はバージョン行を追加しません）、
  インストール ID、ID 生成（Run/Spring Boot では `StrongUuidGenerator`、エンジンの
  デフォルトは `DbIdGenerator`）。
- パスワード：Camunda 7 と同じく、16 バイトのソルトを使用した SHA-512 です。既存ユーザーはサインインでき、
  Fasti で変更したパスワードは Camunda 7 でも使用できます。
- `/engine-rest` の REST API：エンドポイント、パラメーター、ペイロードは同じで、エンドポイントが追加されています
  （認証と一部のステータスコードは異なります。[相違点](#what-differs)を参照）。外部タスク
  （`fetchAndLock`、ロングポーリング）、Java および JS クライアント、`JSESSIONID`、`XSRF-TOKEN`/`X-XSRF-TOKEN`、`camunda.bpm.*`
  プロパティ、`camunda:` BPMN 名前空間。
- 従来の Tasklist のフィルター（`color`、`description`、`priority`、`refresh`、`variables`、
  `showUndefinedVariable`）は、新しい Tasklist でも同じ形式で読み書きされます。

<a id="what-differs"></a>

## 異なる点

- Web アプリケーションは `/fasti/app/...` に配置され、`/camunda/...` からそこへリダイレクトされます（301/308）。プロキシが `/camunda` のみを公開している場合は、`/fasti` も公開するか、`FASTI_WEBAPP_PATH=/camunda` を設定してください。ルートでは
  新しい Web アプリケーションが開きます（`fasti.webapp.default-app: welcome` を設定すると Welcome が復元されます。ただし、
  従来のアプリケーションが無効になっているイメージは除きます）。
- **破壊的変更、コンテナーイメージ：** 従来の Cockpit、Tasklist、Admin、Welcome は
  提供されません。AngularJS 1.8.2 と Bootstrap 3 のサポートが終了しているためです。これらのページやブックマークは、
  `/camunda/app/` 配下のものも含め、新しい Web UI の対応するセクション（`#/cockpit`、
  `#/tasklist`、`#/admin`）にリダイレクトされます。これらのファイルへのリクエストには 404 が返されます。ユーザーを新しい Web UI に移行してください。
  `FASTI_CLASSIC_WEBAPPS=true` を設定すると、移行期間中は警告付きで
  従来のアプリケーションを再び提供できます。Tomcat および WildFly でも提供されません（
  [Tomcat および WildFly ディストリビューション](#tomcat-and-wildfly-distributions)を参照）。Run の zip と
  Spring Boot starter では引き続き提供されますが、非推奨であり、今後の
  リリースで削除されます（`fasti.webapp.classic.enabled: false` を設定すると、それ以前に無効化できます）。
- 古いスキーマの場合、Fasti は後から失敗するのではなく、`ENGINE-03111` を出力して起動を拒否します
  （それでも起動するには `FASTI_SCHEMA_VERSION_CHECK=warn` を設定します）。
- クエリとフィルター内の式は、許可リスト（日付関数、現在の
  ユーザー）に制限されます。カスタム Bean や関数を使用するフィルターでは、エンジン設定の
  プロパティ `allowedExpressionFunctionsInQueries`、`allowedExpressionBeansInQueries`、
  `allowedExpressionPropertiesInQueries` にそれらを宣言する必要があります。
- REST API の動作：コンテナーイメージと Tomcat および WildFly ディストリビューションでは、エンジン一覧
  （`/engine`）を除くすべてのリクエストに Basic 認証情報または bearer token が必要です。そのため REST
  クライアントと外部タスクワーカーは、それらを送信するよう設定する必要があります（コードは変わりません）。
  すべてのディストリビューションで、許可リストにないクエリ式は `400` で拒否されます。権限なしでタスクの
  添付ファイルやコメントを読み取り・変更すると `403` が返されます（認可チェックが有効な場合）。以前は
  `500` が返されていた一部の無効なリクエストには `400` が返されます（解析できないデプロイリソース、
  禁止された Java シリアライズ形式の変数、型が誤ったクエリパラメーター、パスワードポリシーに違反する
  パスワード）。[Camunda 7 との違い](api/README.md#differences-from-camunda-7)を参照してください。
- イメージのデフォルト：スキーマ更新は `false`（新規データベースには `FASTI_DB_SCHEMA_UPDATE=true`）。デモ
  ユーザー、請求書のサンプル、「すべてのタスク」フィルターはありません。CORS は無効（`FASTI_CORS_ALLOWED_ORIGINS=*`
  で復元）。`/actuator/health` を追加。ファイルは `/fasti` 配下に配置（
  `/camunda/configuration` にマウントされたファイルも読み込まれます）。`--swaggerui` はありません。
- OAuth2/OIDC：グループクレーム内の `camunda-admin` だけでは管理者権限が付与されなくなりました
  （`fasti.security.oidc.groups.admin`）。`group-name-delimiter` は正規表現ではなくリテラル文字列です。
  ログアウトには同一オリジンの POST が必要です。
- 新しい Tasklist：`refresh` がないフィルターは自動的に更新されます。
- Run の jar は `fasti-bpm-run-core.jar` です。
- 7.24.0 に対するセキュリティ修正（添付ファイル／コメントの認可、初期セットアップなど）により、以前は成功していた呼び出しの一部で 403 が返される場合があります。[CHANGELOG](../CHANGELOG.md) の *Security* セクションと <https://fasti.dev/security> を参照してください。

<a id="rollback-to-camunda-724"></a>

## Camunda 7.24 へのロールバック

Fasti を停止し、同じデータベースで Camunda 7.24 のノードを再起動します。Fasti で行った
すべての操作結果をそこで確認できます。残るものは、`ACT_ID_INFO` の `fasti.prefs.*` 行（
新しい Web UI の設定で、Camunda 7 では無視されます）と、新しい Tasklist で保存されたフィルターに
元々存在しなかった場合に追加される `refresh: true` です。Fasti 専用の機能を使用するデプロイ済みプロセスは、Camunda 7 では動作しません。

<a id="mixed-operation-with-camunda-7-nodes"></a>

## Camunda 7 ノードとの混在運用

Fasti と Camunda 7.24 は、1 つのデータベースで同時に実行できます（検証済み：互いの
データを参照でき、どちらのノードでもジョブが実行されます）。要件は次のとおりです。

- **同じ履歴レベル**（イメージは保存済みのレベルを使用）、**同じ JVM タイムゾーン**、
  同じパスワード設定とテーブルプレフィックス。
- **ジョブエグゼキューター：** デフォルトでは、Fasti（イメージ、Run、Spring Boot）は Run と同様に、すべてのデプロイメントのジョブを取得します。
  Tomcat および WildFly ディストリビューションのデフォルトは `jobExecutorDeploymentAware=true`（Camunda
  7 と同じ）です。そのノードに登録されていないデプロイメントのジョブ（タイマー、非同期継続、再試行）は、
  最後の再起動より前に Run または REST 経由で行われたデプロイメントも含め、**実行
  されません**。Run のデータベースを引き継ぐ場合は、`conf/bpm-platform.xml`
  （Tomcat）または `standalone.xml`（WildFly）で `false` に設定してください。既存ノードが、Fasti に存在しないクラス（`JavaDelegate`、リスナー、Bean）を持つプロセスアプリケーションを実行している場合、
  Fasti はそれらのジョブを取得しますが、ジョブは失敗し、インシデントが発生します。その場合は、
  Fasti で `FASTI_JOB_EXECUTOR_ENABLED=false` を設定するか、**すべての**ノードでデプロイメントを認識する設定を有効にしてください（各ノードの
  ジョブエグゼキューターは、そのノードに登録されたデプロイメントのジョブのみを実行します。
  REST 経由のデプロイメントは、再起動するまで、それを受信したノードにのみ登録されます）。同期 REST 呼び出し（タスクの完了、
  インスタンスの開始）は、引き続き受信したノードでデリゲートを実行します。そのような
  プロセスは Fasti にルーティングしないでください。
- **ロードバランサー：** Web アプリケーションにはスティッキーセッションが必要です。Basic 認証の REST 呼び出しには不要です。
- 従来の Web アプリケーション（Camunda 7 ノード上、または
  `FASTI_CLASSIC_WEBAPPS=true` を設定した Fasti 上）と新しい Web アプリケーションは、フィルターとユーザーを共有します。

<a id="older-versions-717723"></a>

## 旧バージョン（7.17–7.23）

バックアップを取得し、`sql/upgrade/<db>_engine_<from>_to_<to>.sql` を順に実行して 7.24 までアップグレードします。
現在のマイナーバージョンの未適用パッチスクリプトも実行してください（`configuration/sql/upgrade` に同梱され、イメージでは
`/fasti/configuration/sql/upgrade` にあります）。`ACT_GE_SCHEMA_LOG` に `7.24.0` が含まれていることを確認し、
Fasti を起動します。7.22 → 7.24 の例：`postgres_engine_7.22_to_7.23.sql`、続いて
`postgres_engine_7.23_to_7.24.sql` を実行します。7.24 より前の Camunda ノードは、
Camunda のローリングアップデートのルールに従う場合を除き、アップグレード済みのスキーマで稼働し続けてはいけません（対象は 1 マイナーバージョン差のみで、
旧ノードでは新機能を使用してはいけません）。

<a id="performance"></a>

## パフォーマンス

イメージは、ジョブエグゼキューターと接続プールに Camunda Run のデフォルトを使わなくなりました（Fasti 1.1 までは同じでした）。ジョブスレッド 10、キュー 10、1 回の取得あたり 10 ジョブ（Camunda Run: コア 3、最大 10、キュー 3、取得あたり 3）で、接続プールは最大 20 接続、アイドル時は 10 接続を保持します（Camunda Run: 10）。データベースの接続上限は、Camunda 7 ノードを含むすべてのノードのプールの合計に合わせて設定してください（[ランタイムチューニング](tuning-runtime.md)、英語）。以前の Camunda 7 環境で接続プールやジョブエグゼキューターを調整していた場合は、その値を引き継いでください（`FASTI_DB_CONN_MAXACTIVE`、`FASTI_DB_CONN_MINIDLE`、`CAMUNDA_BPM_JOB_EXECUTION_*`）。その他の指針は `distro/docker/README.md` の「パフォーマンス調整」にあります。Fasti がスキーマのインデックスを自動で変更することはありません。大規模なデータベース向けには、管理者が実行するスクリプトとしてオプションのインデックスを提供しています（`/fasti/configuration/sql/optional/<database>_fasti_perf_indexes_create.sql`、[performance.md](performance.md) を参照）。これらは Camunda 7.24 ノードと併用しても安全で、対応する `_drop.sql` で削除できます。

<a id="limits"></a>

## 制限事項

- サポートされるのは 7.24 Community Edition のスキーマのみです。Enterprise のパッチスキーマは未検証です。
- コンテナーイメージで検証済みです（Camunda Run 7.24.0 でデータベースを作成し、初期データを投入 → Fasti
  が引き継ぐ → Run に戻す → 両方を同時に実行）。

  | データベース | ドライバー | 引き継ぎ | ロールバック | 混在運用 | 7.23 → 7.24 へのアップグレード |
  |---|---|---|---|---|---|
  | PostgreSQL 16 | 同梱 | 成功 | 成功 | 成功 | 未検証 |
  | MariaDB 11.8 | MariaDB Connector/J（同梱）または MySQL Connector/J | 成功 | 成功 | 成功 | 成功 |
  | MySQL 8.4 | 同梱 | 成功 | — | 成功 | 成功 |
  | SQL Server 2022 (Developer) | 同梱（`encrypt=false`） | 成功 | 成功 | 成功 | 未検証 |
  | Oracle Database 23ai Free | `FASTI_EXTRA_LIB` 経由の `ojdbc11` | 成功 | 成功 | 成功 | 未検証 |
  | DB2 | — | 未検証 | | | |

  また、MariaDB（MariaDB Connector/J）上の 2 つの Fasti ノードで、同じタスクの完了と外部タスクの取得を並行して行っても、消失や重複は発生しませんでした。競合に負けたトランザクションには楽観的ロックエラーが返されました。MySQL/MariaDB には `transaction-isolation=READ-COMMITTED` が必要です。MySQL では、
  エンジンごとに専用のデータベースを用意してください。エンジン（Camunda Run と Fasti の両方）は、そのアカウントから参照可能な
  すべてのデータベースでテーブルを検索します。
- Camunda 7 向けにビルドされたプラグインとプロセスアプリケーションを Fasti 内で実行するには、Fasti の
  アーティファクトを使用して再ビルドする必要があります。REST クライアントと外部タスククライアントのコード変更は
  不要ですが、コンテナーイメージ、Tomcat、WildFly では Basic 認証情報または bearer token を送信するよう設定する必要があります。
- スキーマチェックは `ACT_GE_SCHEMA_LOG` のみを読み取り、列の比較は行いません。
