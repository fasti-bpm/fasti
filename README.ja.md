[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | 日本語 | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> この翻訳はコミュニティによって管理されており、英語版より古い場合があります。正式な内容は英語版です。修正を歓迎します。

# Fasti BPM Platform

**Make Camunda 7 Great Again.** Camunda 7 Community Edition は、最終リリースである 7.24.0 をもって終了しました。
Fasti BPM はその後を引き継ぎます。同じデータベース上で同じエンジンを使用しながら、
修正とライブラリの更新を継続し、新しい Web UI を提供します。

> **Fasti BPM は Camunda Services GmbH とは提携関係にありません。Camunda は Camunda Services GmbH の商標です。**
> Fasti BPM は独立したオープンソースプロジェクト（Apache-2.0）です。Camunda Services GmbH の一部ではなく、
> 同社によるスポンサー提供や推奨も受けていません。「Camunda」という名称は、
> ソースコードの由来と Fasti の互換性を説明する目的でのみ使用しています。

<a id="quick-start"></a>

## クイックスタート

amd64 および arm64 向けのイメージを Docker Hub（`fastidev/fasti-bpm`）と GitHub
Container Registry（`ghcr.io/fasti-bpm/fasti-bpm`）で公開しています。

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.0.0
```

<http://localhost:8080/fasti/> を開いてサインインしてください。`DB_URL` を指定しない場合、イメージは
コンテナ内の H2 データベースを使用します。これは評価専用です。REST API には Basic 認証の認証情報が必要です。

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

ソースから**イメージを自分でビルドする**こともできます。Docker、JDK 17 以降、および Maven Central、
nodejs.org、npm レジストリへのネットワークアクセスが必要です（ビルド時に Web UI 用の Node/npm を
ダウンロードします）。初回のビルドには約 10～20 分かかります。

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
# then run the docker run command above with fasti-bpm:local instead of fastidev/fasti-bpm:1.0.0
```

このイメージは、Camunda Run とは異なり、**デフォルトで REST 認証を必須とし、認可を強制します**。
これらを無効にできるのは、デモ用の `FASTI_INSECURE_DEMO=true` を指定した場合だけです。

<a id="highlights"></a>

## 主な特長

- `/fasti/` に**新しい Web UI**（React）を提供：
  - 複数の変数を AND/OR で組み合わせてプロセスインスタンスを検索；
  - タスク内にケースのタイムラインを表示（各ステップのコメントと操作）；
  - 「次の作業を取得」ボタン；
  - 期限と緊急度（SLA）、期限超過の作業；
  - ユーザー別・グループ別のチーム作業量；
  - インシデントに添付できるランブック；
  - システムの健全性を確認するページ（インシデント、失敗したジョブ、遅延ジョブ、外部タスク、バッチ）；
  - モバイル対応のレイアウト；
  - ベトナム語を含む 13 言語に対応。BPMN モデラーもベトナム語に対応しています。
- **シングルサインオン（OIDC）**：Keycloak、Microsoft Entra ID、その他の OpenID Connect プロバイダーに対応；
  クレームをグループやテナントにマッピングし、REST API では JWT Bearer トークンを使用できます。Run、
  Spring Boot、Tomcat、WildFly で利用できます。
- **セキュリティ強化**：クエリとフィルター内の式の許可リスト、エンジン内での添付ファイルと
  コメントのチェック、初期セットアップ、Cockpit の権限、オープンリダイレクト、Web
  アプリケーションのヘッダー、Cookie、CSP。詳細は [変更履歴](CHANGELOG.md) を参照してください。
- **デフォルトで認証を有効化**：このイメージは REST に認証情報を要求し、
  すべてのモードで認可を強制します。
- **スキーマバージョンの保護**：エンジンは 7.24 より古いスキーマでは起動せず、
  スキーマを自動でアップグレードすることもありません。
- **Java4Shell 対策**（[CVE-2026-114938](docs/security/CVE-2026-114938.md)）：amd64
  イメージには修正済みの Temurin JRE を同梱し、Base64 intrinsic を無効にしています。影響を受ける
  JVM 上で実行すると、エンジンが警告を出します。
- **パフォーマンス**：新しい Web UI の初回読み込みサイズを 960 KB から 330 KB に削減（gzip をデフォルトで
  有効化）。チューニングによりジョブエグゼキューターのスループットは約 2.5 倍に向上します（数値とテスト環境は
  [distro/docker/README.md](distro/docker/README.md#performance-tuning) を参照）。
- **最新のプラットフォーム**：Java 17 以降、Spring Boot 4.1、GraalJS 25、Jackson 2.21。

<a id="installation"></a>

## インストール

<a id="one-command-on-your-existing-camunda-724-database"></a>

### 既存の Camunda 7.24 データベースで、コマンド 1 つで実行

このイメージは、従来の Camunda 7 Run イメージの `DB_*` 変数を受け付けます。データベースをバックアップしてから、
次を実行してください。

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e TZ=Europe/Berlin \
  fastidev/fasti-bpm:1.0.0
```

ユーザー、パスワード、グループ、テナント、認可、フィルター、デプロイメント、実行中のインスタンス、
履歴、ジョブ、インシデントはそのまま維持されます。ポートはこのマシンだけにバインドされます。リモート
アクセスには HTTPS を備えたリバースプロキシを使用してください。既存のアカウントで `/fasti/` にサインインします。
認証が有効なため、すべての REST クライアント（外部タスクワーカー、連携システム）は
Basic 認証の認証情報または Bearer トークンを送信する必要があります。

- 同じデータベース上で Camunda 7.24 CE ノードと**並行稼働**：履歴レベル、
  `TZ`、パスワード設定を同じにしてください。古いノードが、Fasti に含まれていない Java クラスを持つ
  プロセスアプリケーションの場合、Fasti で `FASTI_JOB_EXECUTOR_ENABLED=false` を設定するか、すべてのノードを
  各ノードが自身にデプロイされたジョブだけを実行するようにしてください（deployment-aware）。また、それらのプロセスの開始やタスクの完了を Fasti 経由で行わないでください。
  同期呼び出しでは、受信したノード上でデリゲートが実行されます。
- **ロールバック**：Fasti はスキーマを変更しないため、Fasti を停止し、同じデータベース上で
  Camunda 7.24 ノードを再起動できます。Fasti 専用の機能を使用するデプロイ済みプロセスは
  Camunda 7 では実行できません。[Camunda 7.24 へのロールバック](docs/migrate-from-camunda-7.ja.md#rollback-to-camunda-724) を参照してください。
- **古いスキーマ（7.17～7.23）**：まず、イメージに同梱されている `sql/upgrade` スクリプト
  （`/fasti/configuration/sql/upgrade`）を実行して 7.24 に更新してください。たとえば 7.23 → 7.24 では、
  `postgres_engine_7.23_to_7.24.sql` を使用します。

このイメージでのテスト済み環境（Camunda Run 7.24.0 が作成したデータベースの引き継ぎ、ロールバック、
並行稼働）：**PostgreSQL 16、MariaDB 11.8、MySQL 8.4、SQL Server 2022、Oracle
Database 23**（Oracle ドライバーは同梱していないため、`ojdbc11` を別途追加してください。MySQL ではロールバックを
テストしていません）。**DB2 は
未検証です。** H2 は評価専用です。テストごとの結果と制限事項：
[移行ガイド](docs/migrate-from-camunda-7.ja.md#limits)。

詳細なガイド：

- [Docker でのインストール](docs/install-docker.ja.md)：H2、`docker run` および
  `docker compose` による PostgreSQL、既存データベース、並行稼働モード、ヘルスチェック、アップグレード、ロールバック、
  トラブルシューティング。
- [Camunda 7 からの移行](docs/migrate-from-camunda-7.ja.md)：手順、共通点と
  相違点、ロールバック、制限事項。
- [イメージの環境変数リファレンス](distro/docker/README.md#environment-variables)。

<a id="without-docker-tomcat-bundle-download-from-fastidev"></a>

### Docker を使わない場合：Tomcat バンドル（fasti.dev からダウンロード）

ビルド済みの Tomcat 1.0.0 バンドル（ダウンロードページ：<https://fasti.dev/download>）：
- [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip)（[SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256)）
- [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz)（[SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256)）

展開する前にチェックサムを検証してください。例：`sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`。

Tomcat ディストリビューションはソース（`distro/tomcat`）からビルドすることもできます。起動前に
バンドル内の `README.txt` を読んでください。REST API（`/engine-rest`）はデフォルトで HTTP Basic 認証を要求し、
ユーザーは作成されません（`/fasti/` のセットアップページで最初の管理者を作成してください）。
Camunda Run のデータベースを引き継ぐ場合は、`conf/bpm-platform.xml` で
`jobExecutorDeploymentAware=false` を設定してください。設定しないと、既存デプロイメントのタイマーと非同期ジョブが実行されません。

<a id="a-ui-built-from-real-user-complaints"></a>

## 実際のユーザーの不満をもとに構築した UI

新しい Web UI の機能は、Camunda 7 ユーザーが Camunda フォーラムや GitHub に公開した
意見をもとに選定しました。たとえば、次のようなものです。

| ユーザーの要望 | 出典 | Fasti での対応 |
|---|---|---|
| 複数の変数と OR 条件でインスタンスを検索したい | フォーラム [39269](https://forum.camunda.io/t/39269)、[45695](https://forum.camunda.io/t/45695)、[48976](https://forum.camunda.io/t/48976)、[64208](https://forum.camunda.io/t/64208)、[28855](https://forum.camunda.io/t/28855)、[4678](https://forum.camunda.io/t/4678)；[camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | 複数変数の条件ビルダー、AND/OR |
| ケースの各ステップのコメントを確認したい | フォーラム [30260](https://forum.camunda.io/t/30260)、[52109](https://forum.camunda.io/t/52109)、[2173](https://forum.camunda.io/t/2173) | タスク内のケースタイムライン；プロセスインスタンス自体へのコメント |
| 変数でタスクを並べ替え、古いものから表示したい | フォーラム [17599](https://forum.camunda.io/t/17599)、[42078](https://forum.camunda.io/t/42078)、[41193](https://forum.camunda.io/t/41193)、[67328](https://forum.camunda.io/t/67328) | 変数、期限、緊急度による並べ替え |
| 委任や不在時の代理担当者を設定したい | フォーラム [2788](https://forum.camunda.io/t/2788)、[38199](https://forum.camunda.io/t/38199)、[9331](https://forum.camunda.io/t/9331) | タスク単位の委任に対応；不在時の代理担当者は [ロードマップ](#roadmap-plan) に記載 |
| フォームなしでプロセスを開始するときにビジネスキーと変数を指定したい | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | 対応済み |
| デプロイ済みの BPMN XML をダウンロードしたい | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | 対応済み |
| ラベルの一貫性と多言語対応がほしい | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 言語 |

<a id="comparison"></a>

## 比較

各プロジェクトの公開ドキュメント（2026 年 11 月に確認）に基づく簡潔な比較です。
「未検証」は、信頼できる情報源が見つからなかったことを意味します。各プロジェクトには独自の方向性があり、
それぞれ異なるニーズに適した選択肢です。決定する前に各プロジェクトのドキュメントを確認してください。

| | ライセンス | 既存の C7 DB で稼働 | 必要な移行 | セキュリティパッチ | Java / Spring Boot | UI | SSO | Docker イメージ |
|---|---|---|---|---|---|---|---|---|
| **Fasti BPM** | Apache-2.0 | はい、7.24 スキーマを維持 | イメージ：不要。組み込みアプリ：新しい Maven 座標、Spring Boot 4.1、Java 17 以降；`org.camunda.*` パッケージを維持 | あり、[以下のポリシー](#commitments-support-and-security-patches) を参照 | 17+ / 4.1 | 新 UI（React）+ 従来の UI（非推奨） | OIDC を内蔵 | あり、1.0.0 以降 |
| Camunda 7 CE 7.24 | ソースは Apache-2.0；同梱の一部 Web コンポーネントは MIT（[13]） | （オリジナル） | — | 7.24 以降の CE リリースなし（[1]） | 17, 21, 25（[2]）/ 3.5 | Cockpit、Tasklist、Admin（AngularJS） | security starter 経由の OAuth2 | 従来の Run イメージ |
| Camunda 7 Enterprise | 商用 | はい（同じ製品系列） | 不要 | 04/2030 まで、有料延長は 04/2032 まで（[3]） | 未検証 | Cockpit Enterprise | 未検証 | 未検証 |
| Camunda 8 | Camunda License 1.0；8.6 以降、セルフマネージド環境での本番利用にはライセンスが必要（[4]） | いいえ（異なるエンジン） | 必要（モデル、インスタンス、データ） | あり（ベンダー） | エンジンが異なるため比較不可 | Operate、Tasklist | 未検証 | 未検証 |
| Operaton | Apache-2.0 | 7.24 と同じテーブル構造；2.2 ではアップグレードスクリプトでスキーマバージョン 7.25 を記録（[5]） | パッケージを `org.operaton.*` に変更（OpenRewrite レシピあり）；スキーマバージョンスクリプト（2.2） | あり、2.2.x 系列（[6]） | 17+ / 4.1（[5]） | 従来の UI + webapps-neo（プレビュー） | 未検証 | あり（[7]） |
| CIB seven | Apache-2.0；EE にはライセンスキーが必要 | はい（7.24 の変更をマージ済み） | パッケージを `org.cibseven.*` に変更、接頭辞は `cibseven.*` | あり、リリースごと（[8]） | 17, 21 / 3.5 および 4.0（[9]） | Vue 3 webclient | REST 向け OAuth2/JWT（[8]） | あり（[8]） |
| OrqueIO | Apache-2.0 | ドキュメントによると C7 テーブルと互換（[10]） | Maven グループは `io.orqueio`；パッケージ名変更の記載なし | あり、ドキュメントに記載（[10]） | 21 / 3.5, 4.0, 4.1 | Angular で再実装 | 複数プロバイダー対応の OIDC | 未検証 |
| EximeeBPMS | Apache-2.0（Monitor：MIT） | プロジェクトは Camunda 7 とのスキーマ互換性を維持すると表明（Fasti では未検証）（[14]） | パッケージを `org.eximeebpms.*` に変更、Model API の名称も変更 | あり、最新の 2 つのマイナーリリース（[11]） | 21 / Spring Boot のバージョンは記載なし | 独立した Monitor | 未検証 | あり（`eximeebpms-docker` リポジトリ） |
| Fluxnova | Apache-2.0（FINOS） | 未検証 | 未検証 | 未検証 | 21（Corretto イメージ）/ 未検証 | Control Center | 未検証 | あり（[12]） |

Fasti 自身の弱点も明記します。発足間もない小規模チームのプロジェクトであり、
商用サポートはまだなく、DB2 は未検証です。

出典：[1] [CE のサポート終了告知](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921)；
[2] [7.24 のサポート対象環境](https://docs.camunda.org/manual/7.24/introduction/supported-environments/)；
[3] [Enterprise のサポート延長](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/)；
[4] [Camunda 8.6 のライセンス](https://camunda.com/blog/2024/10/camunda-licensing-what-you-need-to-know/)；
[5] [Operaton 2.2](https://docs.operaton.org/docs/documentation/reference/release-notes/2_2/)；
[6] [Operaton SECURITY.md](https://github.com/operaton/operaton/blob/main/SECURITY.md)；
[7] [operaton-docker](https://github.com/operaton/operaton-docker)；
[8] [CIB seven 2.2.0](https://docs.cibseven.org/manual/latest/release-notes/release-notes-2.2.0-ce/)；
[9] [CIB seven のサポート対象環境](https://docs.cibseven.org/manual/latest/introduction/supported-environments/)；
[10] [OrqueIO ドキュメント](https://docs.orqueio.io/)；
[11] [EximeeBPMS SECURITY.md](https://github.com/EximeeBPMS/eximeebpms/blob/main/SECURITY.md)；
[12] [Fluxnova](https://github.com/finos/fluxnova-bpm-platform)；
[13] [Camunda 7 のライセンス](https://docs.camunda.org/manual/latest/introduction/licenses/)；
[14] [EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms)；
独立した比較記事：[WZ-IT, 2026-10-15](https://wz-it.com/en/blog/camunda-7-end-of-life-operaton-cibseven-fluxnova-compared/)。

<a id="timeline"></a>

## タイムライン

<a id="camunda-7-milestones"></a>

### Camunda 7 の主な出来事

| 日付 | 主な出来事 | 出典 |
|---|---|---|
| 2024-03-06 | Camunda が、7.24 を最終 CE リリースとして、2025 年 10 月に Camunda 7 CE のサポートを終了すると発表 | [Camunda フォーラム](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921) |
| 2025-02-28 | Camunda が Camunda 7 Enterprise のサポートを 2030-04-09 まで延長し、有料延長サポートを 2032 年 4 月まで提供すると発表 | [Camunda ブログ](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) |
| 2025-10-07 | タグ `7.24.0` を作成し、Maven Central に公開：最終 CE リリース | [タグ 7.24.0](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0)、[Maven Central](https://repo1.maven.org/maven2/org/camunda/bpm/camunda-engine/7.24.0/) |
| 2025-10-14 | Camunda が予定した 7.24 のリリース日。この日以降、セキュリティパッチを含め、CE の追加リリースはありません。CE リポジトリはその後アーカイブされました | [告知](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921)、[サポートページ](https://docs.camunda.org/enterprise/announcement) |
| 2030 年 4 月 | Camunda 7 Enterprise のサポート終了（7.24 LTS：2030-04-13） | [サポートページ](https://docs.camunda.org/enterprise/announcement) |
| 2032 年 4 月 | Camunda 7 Enterprise の有料延長サポート終了 | [サポートページ](https://docs.camunda.org/enterprise/announcement) |

<a id="fasti-bpm"></a>

### Fasti BPM

Fasti は `7.24.0` タグから直接開発を継続しており、すべての修正と変更はその時点を基準に数えています
（`git log 7.24.0..`）。以下の段階は日付を付けず、実施した作業の順に並べています。

1. **基盤**：Java 17、Spring Boot 4.1、ライブラリの更新；Maven 座標を
   `dev.fasti.platform` に設定。
2. **セキュリティ**：7.x 系列で公開された問題の修正、式の許可リスト、
   エンジン内での添付ファイル／コメントのチェック、Web アプリケーションの強化。
3. `/fasti/` に**新しい Web UI** を提供し、従来の Cockpit、Tasklist、Admin を段階的に置き換え。
4. Run、Spring Boot、Tomcat、WildFly 向けの**シングルサインオン**（OIDC）。
5. **既存データベースへのそのままの導入**：従来の `DB_*` 変数を受け付け、スキーマバージョンをチェックするイメージ；5 種類のデータベースシステムでテスト済み。
6. **リリース前のセキュリティレビュー**（コードレビュー、イメージのペネトレーションテスト、ZAP ベースライン
   スキャン；検出された問題は修正済み）と **Java4Shell 対策**。
7. **1.0.0**：初回リリース（2026-11-20）。

<a id="roadmap-plan"></a>

## ロードマップ（計画）

> これは**計画**であり、確約ではありません。ユーザーのフィードバックにより順序が変わることがあります。

- **不在時の委任**：期間を指定して代理担当者を設定。新しいタスクを再割り当てし、
  変更を監査記録に残します（[フォーラム 2788](https://forum.camunda.io/t/2788)、
  [38199](https://forum.camunda.io/t/38199)）。
- **実行者と承認者の分離（四眼原則）**：後続ステップの承認者が、それ以前のステップを実行した本人にならないよう、
  エンジンで強制します。
- Kubernetes 向けの **Helm チャート**。
- **メトリクスと OpenTelemetry**：Micrometer/Prometheus のネイティブ対応、プロセスとジョブのトレーシング。
- **MyBatis のアップグレード**：現行リリースへの更新。
- アップグレード頻度の低いユーザー向けの**長期サポート（LTS）系列**。
- **DB2 の検証**。
- 大規模なデータセットでも作業量ページの速度を維持するための、**作業量集計エンドポイント**。

<a id="commitments-support-and-security-patches"></a>

## コミットメント：サポートとセキュリティパッチ

> このポリシーは Fasti BPM プロジェクトチームが管理しており、更新される場合があります。
> このリポジトリ内のバージョンが有効です。

- **サポート対象系列：** `1.x` 系列の最新マイナーリリースに、バグ修正とセキュリティ
  パッチを提供します（`1.0.1` などのパッチリリースとして提供）。
- **セキュリティパッチ：** Fasti および同梱ライブラリの脆弱性への対応を、他の作業より優先します。
  ベースイメージや JRE に修正が入った場合は、イメージを再ビルドします。プロジェクトは
  Camunda 7 とそのフォークに関する公開セキュリティアドバイザリを追跡し、Fasti への影響を確認します。
- **互換性：** `1.x` 系列では、7.24 のデータベーススキーマと `org.camunda.bpm.*`
  パッケージを維持します。破壊的変更は [変更履歴](CHANGELOG.md) に **BREAKING** と明記します。
- **サポートレベル：** コミュニティプロジェクトのため、サポートはベストエフォートであり、**SLA はありません**。
  商用サポートもまだ提供していません。
- **脆弱性の報告：** 公開 Issue は**作成しないでください**。
  [SECURITY.md](SECURITY.md#security-policy) を参照してください（GitHub 経由で非公開報告）。
- **修正済みの不具合：** [変更履歴](CHANGELOG.md) の *Fixed* および *Security* セクションに、
  Camunda 7 CE 7.24.0 と比較して修正した内容を記載しています。アドバイザリは
  [SECURITY.md](SECURITY.md#advisories) に記載しています。

<a id="documentation"></a>

## ドキュメント

| ドキュメント | 内容 |
|---|---|
| [docs/install-docker.md](docs/install-docker.ja.md) | Docker でのインストール手順 |
| [docs/api/README.md](docs/api/README.md) | REST API 連携、Java および JavaScript の外部タスククライアント |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.ja.md) | Camunda 7 からの移行、ロールバック、制限事項 |
| [distro/docker/README.md](distro/docker/README.md) | イメージ：環境変数、ドライバー、運用、チューニング |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | 新しい Web UI |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell と Fasti |
| [CHANGELOG.md](CHANGELOG.md)、[SECURITY.md](SECURITY.md#security-policy)、[CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) | 変更、セキュリティ、コントリビューション |

<a id="what-this-is"></a>

## このプロジェクトについて

このリポジトリには、BPMN 2.0 / DMN プロセスエンジン、REST API、Web
アプリケーション（Cockpit、Tasklist、Admin）、ディストリビューション（Run、Tomcat、WildFly）が含まれています。
Camunda 7 CE の `7.24.0` タグを起点として、バグ修正とライブラリの
更新を継続しています。

目標は、Camunda 7 上で動作するアプリケーションとの**後方互換性**です。

- `org.camunda.bpm.*` パッケージ、BPMN 名前空間
  `http://camunda.org/schema/1.0/bpmn`、設定の接頭辞 `camunda.bpm.*` を維持；
- 7.24 のデータベーススキーマを維持；
- すべてのデプロイ形態を維持：Spring Boot、Run、Tomcat、WildFly；`javax` と `jakarta`。

<a id="what-is-different-from-camunda-7240-ce"></a>

## Camunda 7.24.0 CE との違い

- **セキュリティ：** 7.x 系列で公開された問題（クエリ内の式、
  初期セットアップ、添付ファイルとコメントへのアクセス、Cockpit の権限、オープン
  リダイレクト）の修正に加え、安全対策を追加しています。クエリと
  フィルター内の式の許可リスト、エンジン内での添付ファイルとコメントのチェック、Web アプリケーションの
  セキュリティフィルター、ヘッダー、Cookie の強化などです。
- **安定性：** JavaScript（GraalJS）でスクリプトや DMN 式を評価する際の
  メモリリークを解消し、エンジンのその他の不具合も多数修正しています。
- **プラットフォーム：** Java 17 以降、Spring Boot 4.1、GraalJS 25、Jackson 2.21、およびその他多数の
  ライブラリを、セキュリティ修正を含むリリースに更新しています。

一部の修正では、より安全な動作になるようデフォルト値を変更しています。アップグレード前に
[移行ガイド](docs/migrate-from-camunda-7.ja.md#what-differs) の「相違点」を読んでください。

<a id="versioning"></a>

## バージョニング

Fasti BPM Platform は `1.0.0` から [セマンティックバージョニング](https://semver.org/) に従います。
`1.x` 系列は、Camunda 7.24 向けに作成されたアプリケーションと互換性があります。

<a id="maven-coordinates"></a>

## Maven 座標

Fasti BPM Platform のアーティファクトには独自の Maven 座標があります。**Java パッケージは
`org.camunda.bpm.*` のまま**なので、アプリケーションコードのインポートは維持できます。設定の接頭辞 `camunda.bpm.*`、
BPMN/DMN の名前空間、データベーススキーマ、WildFly のモジュール名も
変更されません。Maven に関して変更されるのは依存関係の宣言だけです
（Web アプリケーションのパスは別途変更されます。
[Web アプリケーションのパス](#web-application-path-fasti) を参照）。

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`、`org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`；グループ `org.camunda.commons`、`org.camunda.connect`、
  `org.camunda.spin`、`org.camunda.template-engines` → `dev.fasti.platform.<group name>`。
- **artifactId:** 接頭辞 `camunda-` が `fasti-` に変わり、それ以外は維持されます。
- **version:** `7.24.0` の代わりに Fasti BPM Platform のバージョンを使用します
  （[バージョニング](#versioning) を参照）。

これらのアーティファクトのリリースは、まだ Maven Central に公開されていません。それまではソースから
ビルドし、プロジェクトで使用する Maven リポジトリからアーティファクトを利用できるようにしてください。

BOM でバージョンを管理します。

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.fasti.platform</groupId>
      <artifactId>fasti-bom</artifactId>
      <version>${fasti.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

よく使用するアーティファクト：

| Camunda 7 | Fasti BPM Platform |
|---|---|
| `org.camunda.bpm:camunda-bom` | `dev.fasti.platform:fasti-bom` |
| `org.camunda.bpm:camunda-engine` | `dev.fasti.platform:fasti-engine` |
| `org.camunda.bpm:camunda-engine-spring` | `dev.fasti.platform:fasti-engine-spring` |
| `org.camunda.bpm:camunda-engine-spring-6` | `dev.fasti.platform:fasti-engine-spring-6` |
| `org.camunda.bpm:camunda-engine-cdi-jakarta` | `dev.fasti.platform:fasti-engine-cdi-jakarta` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-rest` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-webapp` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-webapp` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-security` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-security` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-external-task-client` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-external-task-client` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-test` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-test` |
| `org.camunda.bpm:camunda-engine-rest-core` | `dev.fasti.platform:fasti-engine-rest-core` |
| `org.camunda.bpm:camunda-engine-rest-core-jakarta` | `dev.fasti.platform:fasti-engine-rest-core-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp` | `dev.fasti.platform.webapp:fasti-webapp` |
| `org.camunda.bpm.webapp:camunda-webapp-jakarta` | `dev.fasti.platform.webapp:fasti-webapp-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp-webjar` | `dev.fasti.platform.webapp:fasti-webapp-webjar` |
| `org.camunda.bpm:camunda-external-task-client` | `dev.fasti.platform:fasti-external-task-client` |
| `org.camunda.bpm:camunda-engine-plugin-spin` | `dev.fasti.platform:fasti-engine-plugin-spin` |
| `org.camunda.bpm:camunda-engine-plugin-connect` | `dev.fasti.platform:fasti-engine-plugin-connect` |
| `org.camunda.spin:camunda-spin-dataformat-all` | `dev.fasti.platform.spin:fasti-spin-dataformat-all` |
| `org.camunda.connect:camunda-connect-connectors-all` | `dev.fasti.platform.connect:fasti-connect-connectors-all` |
| `org.camunda.bpm.dmn:camunda-engine-dmn` | `dev.fasti.platform.dmn:fasti-engine-dmn` |
| `org.camunda.bpm.model:camunda-bpmn-model` | `dev.fasti.platform.model:fasti-bpmn-model` |
| `org.camunda.bpm:camunda-bpm-assert` | `dev.fasti.platform:fasti-bpm-assert` |
| `org.camunda.bpm:camunda-bpm-junit5` | `dev.fasti.platform:fasti-bpm-junit5` |

FEEL ライブラリ `org.camunda.feel:feel-engine` は、このリポジトリに含まれない
外部ライブラリです。座標は変更されません。

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

### オリジナルのエンジンを依存関係に取り込むサードパーティライブラリ

パッケージが変更されていないため、オリジナルのエンジン（`org.camunda.bpm:camunda-engine`）と
`fasti-engine` には同じ名前のクラスが含まれます。Maven はこれらを無関係な 2 つの
ライブラリとして扱うため、どちらも除外しません。サードパーティライブラリ（コミュニティ拡張など）が
オリジナルのアーティファクトに依存している場合は、それらを除外し、代わりに対応する
Fasti アーティファクトを宣言してください。

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>some-camunda-extension</artifactId>
  <version>1.2.3</version>
  <exclusions>
    <exclusion>
      <groupId>org.camunda.bpm</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.model</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.dmn</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.commons</groupId>
      <artifactId>*</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

`mvn dependency:tree -Dincludes="org.camunda*"` で、まだ含まれているオリジナルのアーティファクトを
一覧表示できます（`org.camunda.feel:feel-engine` は正当な依存関係なので、除外しないでください）。

<a id="startup-check"></a>

### 起動時チェック

エンジンは作成時にクラスパスを調べ、複数のライブラリにプロセスエンジンのクラスが
見つかった場合は**起動を拒否します**。エラー `ENGINE-12021` に、
それぞれの場所が表示されます。Spring Boot starter も、アプリケーションの起動直後に
同じチェックを行います。親クラスローダーからのみ見えるライブラリ（Web アプリケーションに組み込まれた
エンジンとは別に存在する、サーバーの共有エンジンなど）は
対象外です。意図した構成である場合は、システムプロパティでチェックを無効にできます。

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

制限事項：クラスパスでオリジナルのエンジンライブラリが `fasti-engine` より先にある場合、
このチェックを含まないオリジナルのライブラリのクラスでエンジンが構築されます。
この場合、停止されるのは Fasti Spring Boot starter を使用するアプリケーションだけです。
それ以外の連携方法では、上記の手順で依存関係を確認してください。

<a id="web-application-path-fasti"></a>

## Web アプリケーションのパス：`/fasti`

すべてのディストリビューションで、Cockpit、Tasklist、Admin、新しい Web UI は `/fasti` 配下で提供されます（従来は `/camunda`）。
`http://localhost:8080/fasti/` で新しい UI
（`/fasti/app/next/default/`）が開き、従来のアプリケーションは `/fasti/app/cockpit/...`、
`/fasti/app/tasklist/...`、`/fasti/app/admin/...` にあります。REST API は `/engine-rest` のままです。

**従来のアプリケーションは非推奨です。** 従来の Cockpit、Tasklist、Admin、Welcome は、
いずれもサポートが終了した AngularJS 1.8.2 と Bootstrap 3 を使用しています。**コンテナイメージではデフォルトで
これらを提供しません**（破壊的変更）。それらのページと古いブックマークは新しい UI の対応するセクションにリダイレクトされ、ファイルへのリクエストには `404` を返します。`FASTI_CLASSIC_WEBAPPS=true` で
再び有効にできます（警告が表示されます）。[distro/docker/README.md](distro/docker/README.md) を参照してください。
Tomcat と WildFly のバンドルでも、デフォルトでは提供しません（再び有効にするには
`-Dfasti.webapp.classic.enabled=true` または `FASTI_CLASSIC_WEBAPPS=true` を指定）。Run の zip と Spring
Boot starter では、稼働中の環境に支障が出ないよう、従来どおり提供を続けます。
`fasti.webapp.classic.enabled: false` で無効にできます。従来のアプリケーションは、
今後のリリースで削除されます。

古いパスも引き続き使用できます。`/camunda/...` へのすべてのリクエストは、パスの残りの部分と
クエリ文字列を保持したまま、`/fasti/...` 配下の同じパスへリダイレクトされます。GET と HEAD には
`301`、その他すべてのメソッド（POST、PUT、DELETE。たとえば `/camunda/api/...` を呼び出す古いクライアント）には
`308` を返し、クライアントが同じメソッドとボディでリクエストを再送できるようにします。リダイレクトには
`Cache-Control: no-store` を付けるため、`/camunda` に戻すとすぐに反映されます。

`/camunda` に戻すには：

- **Spring Boot、Run：** `camunda.bpm.webapp.application-path: /camunda`（この場合、リダイレクトは行われません）。
  リダイレクトだけを無効にするには：`fasti.webapp.legacy-path-redirect: false`。
- **Tomcat：** `webapps/camunda`（リダイレクト用アプリケーション）を削除し、
  `webapps/fasti` を `webapps/camunda` に名前変更します。
- **WildFly：** `standalone/deployments/camunda.war`（および `camunda.war.dodeploy`）を削除し、
  Web アプリケーション WAR の `WEB-INF/jboss-web.xml` で
  `<context-root>/camunda</context-root>` を設定します。

**リバースプロキシ：** プロキシが `/camunda/` だけを転送している場合（または `/camunda` 向けに
パスの書き換え、Cookie パス、CSP、ヘルスチェックを設定している場合）は、`/fasti/` を追加するか、完全に `/fasti/` へ移行してください。
`XSRF-TOKEN` Cookie（Tomcat と WildFly では `JSESSIONID` も）のパスは `/fasti` になったため、
ユーザーは再度サインインする必要があります。ブックマークと古いリンクは、リダイレクトを通じて引き続き使用できます。

<a id="single-sign-on-openid-connect"></a>

## シングルサインオン（OpenID Connect）

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

<a id="build"></a>

## ビルド

JDK 17 以降が必要です（21 を推奨）。

```bash
./mvnw clean verify
```

完全なビルドでは、すべての Java テストと Web UI のユニットテストを実行します（1 時間近くかかります。Playwright の
エンドツーエンドテストは別途実行します。[webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright) を参照）。また、Maven Central、
nodejs.org、npm レジストリへのネットワークアクセスが必要です。[TESTING.md](TESTING.md) を参照してください。

<a id="acknowledgements"></a>

## 謝辞

- Fasti BPM Platform は、Camunda と Camunda 7 コミュニティによる 10 年以上にわたる
  取り組みを基盤としています。
- 不具合の調査にあたり、Camunda 7 から派生した他のコミュニティプロジェクトである
  Operaton、CIB seven、OrqueIO、EximeeBPMS の公開情報（Issue、アドバイザリ、
  リリースノート）を参照しました。このリポジトリのソースコードは、
  Camunda 7 CE を基盤として独立に作成したものです。

<a id="license"></a>

## ライセンス

[Apache License 2.0](LICENSE)。オリジナルコードの著作権については [NOTICE](NOTICE) を参照してください。
