[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | 日本語 | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

> この翻訳はコミュニティによって管理されており、英語版より古い場合があります。正式な内容は英語版です。修正を歓迎します。

# Fasti BPM Platform

Fasti BPM は、Camunda 7 Community Edition 7.24.0 をベースに構築された、独立したオープンソースのエンジンです。
既存の Camunda 7 データベース上でそのまま動作し、最新のライブラリ、セキュリティ修正、新しい Web
インターフェースを備えています。Web サイト：<https://fasti.dev>

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
  fastidev/fasti-bpm:1.0.1
```

<http://localhost:8080/fasti/> を開いてサインインしてください。`DB_URL` を指定しない場合、イメージは
コンテナ内の H2 データベースを使用します。これは評価専用です。REST API には Basic 認証の認証情報が必要です。

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

独自のデータベースを使う方法を含む詳細なガイド：<https://fasti.dev/install>

<a id="highlights"></a>

## 主な特長

- **接続先を変える。それが移行です。** Camunda 7.24 のスキーマをそのまま使用し、
  `org.camunda.bpm.*` パッケージも維持します（[移行](https://fasti.dev/install#migrate)）。
- **コンテナ 1 つ、コマンド 1 つ。** Camunda Run と同じ `DB_*` 変数を使います（[インストール](https://fasti.dev/install)）。
- **日々キューをさばく人のために**：13 言語に対応し、ライトとダークのテーマを備え、スマートフォンでも
  使える新しい Web インターフェース（[機能](https://fasti.dev/#features)）。
- **ブラウザでモデリングしてデプロイ**：BPMN、DMN、フォーム（[モデラー](https://fasti.dev/#modeler)）。
- **運用に必要なツールは最初から揃っています**：履歴、インスタンスの修復、一括リトライ、操作ログ
  （[運用](https://fasti.dev/#operate)）。
- **コンテナーイメージはデフォルトで閉じた設定**：初回起動時からサインイン必須、デフォルトユーザーなし、
  REST 認証や認可を無効にする設定は拒否され、Java4Shell も緩和済みです。Tomcat と WildFly は REST API に
  認証情報を要求し、ユーザーを作成しません（最初の管理者はセットアップページで作成します）。Run zip と
  Spring Boot starter は Camunda 7 のデフォルトのままです。すべてのディストリビューションにセキュリティ修正が
  含まれ、Java4Shell の影響を受ける JVM では起動時に警告します（[セキュリティ](https://fasti.dev/security)）。
- **シングルサインオン**：OpenID Connect と JWT bearer token（[ガイド](docs/single-sign-on.ja.md)）。
- **最新のプラットフォーム**：Java 17 以降、Spring Boot 4.1、GraalJS 25、Jackson 2.21
  （[パフォーマンス](https://fasti.dev/#performance)）。

<a id="installation"></a><a id="get-fasti"></a>

## Fasti の入手

- <a id="one-command-on-your-existing-camunda-724-database"></a>**Docker イメージ**（`fastidev/fasti-bpm`、amd64 および arm64）：<https://fasti.dev/install>。手順は
  [docs/install-docker.ja.md](docs/install-docker.ja.md)、すべての変数は
  [distro/docker/README.md](distro/docker/README.md#environment-variables) を参照してください。
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**Tomcat バンドル**（Docker を使わない場合）：<https://fasti.dev/download>。ビルド済みの Tomcat 1.0.0 バンドル：
  - [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip)（[SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256)）
  - [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz)（[SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256)）

  展開する前にチェックサムを検証してください。例：`sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`。
  起動前にバンドル内の `README.txt` を読んでください。このバンドルはまだバージョン 1.0.0 で、1.0.1 の
  2 つのセキュリティ修正（テンプレートフィルター、LDAP 証明書）は含まれていません。それらが必要な場合は、
  イメージを使用するか、`v1.0.1` タグから Tomcat ディストリビューション（`distro/tomcat`）をビルドしてください。
- **組み込み**（Spring Boot またはその他の Java アプリケーション）：新しい Maven 座標
  （`dev.fasti.platform:fasti-*`）、同じ `org.camunda.bpm.*` パッケージ。
  [docs/embedding.ja.md](docs/embedding.ja.md) を参照してください。

<a id="moving-from-camunda-7"></a>

## Camunda 7 からの移行

Camunda 7.24 を運用している場合、Fasti はスキーマを変更せずに同じデータベース上で動作します。古いノードを
置き換えることも、Fasti を並行して動かすことも、いつでも Camunda 7.24 に戻すこともできます。
Fasti のイメージは、PostgreSQL 16、MariaDB 11.8、MySQL 8.4、SQL Server 2022、Oracle Database 23ai 上で Camunda Run 7.24 が作成したデータベースを引き継ぐテストを行っています。Camunda Run に戻すテストは、MySQL を除く同じデータベースで行っています。DB2 はまだテストしていません。外部タスク
ワーカーを含むすべての REST クライアントは、Basic 認証情報または bearer token を送信する必要があります。

組み込み型の Spring Boot アプリケーションは、Spring Boot 4.1 と Java 17 への移行も必要です。それより古いスキーマは、まず標準スクリプトでアップグレードしてください。Camunda 7 向けにビルドされたプラグインやプロセスアプリケーションを Fasti 内で動かすには、Fasti のアーティファクトに対して再ビルドする必要があります。並行稼働と切り戻しには制約があります（全ノードで同じ設定、どのノードがジョブを実行するか、Fasti 固有の機能を使うプロセス）。[混在運用](docs/migrate-from-camunda-7.ja.md#mixed-operation-with-camunda-7-nodes) と [ロールバック](docs/migrate-from-camunda-7.ja.md#rollback-to-camunda-724) を参照してください。

移行の手順と制限：<https://fasti.dev/install#migrate> および
[移行ガイド](docs/migrate-from-camunda-7.ja.md)（ロールバック、古いスキーマ、テスト結果）。

<a id="comparison"></a><a id="how-it-compares"></a>

## 他のプロジェクトとの比較

Camunda 7 を引き継ぐ優れたプロジェクトはいくつもあります。Fasti は `org.camunda.bpm.*` パッケージと
7.24 のスキーマを維持するため、切り替えは移行プロジェクトではなく接続先の変更で済みます。さらに新しい
インターフェースが加わります。他のプロジェクトも堅実な選択肢です。出典付きの完全な表：
<https://fasti.dev/compare>。

<a id="commitments-support-and-security-patches"></a><a id="security-and-support"></a>

## セキュリティとサポート

`1.x` 系の最新マイナーリリースが、`1.0.1` のようなパッチリリースとしてバグ修正とセキュリティパッチを
受け取ります。セキュリティ上の問題は、公開 issue ではなく非公開で報告してください。
[SECURITY.md](SECURITY.md#security-policy) を参照してください。[サポートポリシー](SECURITY.md#support-policy)
も同じファイルに記載しています。修正一覧、Java4Shell、リリース前のレビュー：
<https://fasti.dev/security>。インストール・移行・本番運用の支援やサポート条件については
<contact@fasti.dev> までご連絡ください。

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## ロードマップ

これは計画であり、日程の約束ではありません。`1.x` で次に予定しているもの：Maven Central での Maven
アーティファクト公開、Helm チャート、Prometheus メトリクスと OpenTelemetry トレーシング、構造化 JSON
ログ。`2.0` で予定しているもの：不在時の代理者設定、承認に対する四眼原則（フォーアイズ）による統制、
改ざん不能な監査証跡、新しいインターフェースでの CMMN 対応。Camunda 7 CE の終了から 2027年の目標までの
タイムライン：<https://fasti.dev/#timeline>。

<a id="documentation"></a>

## ドキュメント

[fasti.dev](https://fasti.dev) のページ：[Docker でインストール](https://fasti.dev/install) ·
[Tomcat 版ダウンロード](https://fasti.dev/download) · [API 連携ガイド](https://fasti.dev/docs) ·
[セキュリティとサポート](https://fasti.dev/security) · [比較](https://fasti.dev/compare) ·
[ブログ](https://fasti.dev/blog)

<a id="what-this-is"></a><a id="what-is-different-from-camunda-7240-ce"></a><a id="versioning"></a><a id="maven-coordinates"></a><a id="third-party-libraries-that-still-pull-in-the-original-engine"></a><a id="startup-check"></a><a id="web-application-path-fasti"></a><a id="single-sign-on-openid-connect"></a><a id="a-ui-built-from-real-user-complaints"></a>

| ドキュメント | 内容 |
|---|---|
| [docs/install-docker.md](docs/install-docker.ja.md) | Docker でのインストール手順 |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.ja.md) | Camunda 7 からの移行、ロールバック、制限事項 |
| [docs/embedding.md](docs/embedding.ja.md) | 互換性、7.24.0 との違い、バージョニング、Maven 座標、起動時チェック |
| [docs/web-ui.md](docs/web-ui.ja.md) | Web インターフェース：ユーザーが要望した機能、`/fasti` パス、従来のアプリケーション |
| [docs/single-sign-on.md](docs/single-sign-on.ja.md) | シングルサインオン（OpenID Connect）：Run、Spring Boot、Tomcat、WildFly |
| [docs/api/README.md](docs/api/README.md) | REST API 連携、Java および JavaScript の外部タスククライアント |
| [distro/docker/README.md](distro/docker/README.md) | イメージ：環境変数、ドライバー、運用、チューニング |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | 新しい Web UI |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell と Fasti |
| [CHANGELOG.md](CHANGELOG.md)、[SECURITY.md](SECURITY.md#security-policy) | 変更、セキュリティ、サポートポリシー |

<a id="build"></a>

## ソースからのビルド

JDK 17 以降（21 を推奨）と、Maven Central、nodejs.org、npm レジストリへのネットワークアクセスが
必要です（ビルド時に Web UI 用の Node/npm をダウンロードします）。

```bash
./mvnw clean verify
```

完全なビルドでは、すべての Java テストと Web UI のユニットテストを実行します（1 時間近くかかります。Playwright の
エンドツーエンドテストは別途実行します。[webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright) を参照）。
[TESTING.md](TESTING.md) を参照してください。コンテナイメージをビルドするには、次を実行します（初回のビルドには
約 10～20 分かかります）。

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## コントリビュート

質問、バグ報告、プルリクエストを GitHub で歓迎します。[CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) と
[行動規範](CODE_OF_CONDUCT.md) を参照してください。ネイティブスピーカーによる翻訳のレビューは
特に歓迎します。

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
