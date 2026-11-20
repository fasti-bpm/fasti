[English](embedding.md) | [Tiếng Việt](embedding.vi.md) | [Deutsch](embedding.de.md) | [Español](embedding.es.md) | [Français](embedding.fr.md) | [हिन्दी](embedding.hi.md) | [Bahasa Indonesia](embedding.id.md) | 日本語 | [한국어](embedding.ko.md) | [Português (Brasil)](embedding.pt-BR.md) | [Русский](embedding.ru.md) | [ไทย](embedding.th.md) | [简体中文](embedding.zh-CN.md)

> この翻訳はコミュニティによって管理されており、英語版より古い場合があります。正式な内容は英語版です。修正を歓迎します。

# Fasti BPM の組み込み：互換性と Maven 座標

Fasti が Camunda 7 から引き継いでいるものと 7.24.0 との違い、バージョン番号の付け方、エンジンを組み込むアプリケーション向けの Maven 座標をまとめています。Fasti をサーバーとして動かす場合は、[Docker でのインストール](install-docker.ja.md) または <https://fasti.dev/download> の Tomcat バンドルを参照してください。

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
[移行ガイド](migrate-from-camunda-7.ja.md#what-differs) の「相違点」を読んでください。

<a id="versioning"></a>

## バージョニング

Fasti BPM Platform は `1.0.0` から [セマンティックバージョニング](https://semver.org/) に従います。
`1.x` 系列は、Camunda 7.24 の Java API、`org.camunda.bpm.*` パッケージ、`camunda.bpm.*` 設定プレフィックス、
データベーススキーマを維持します。エンジンを組み込むアプリケーションは Java 17 以降へ、Spring Boot starter を
使う場合は Spring Boot 4.1 へ移行します。プラグインとプロセスアプリケーションは `dev.fasti.platform`
アーティファクトを使用して再ビルドします。

<a id="maven-coordinates"></a>

## Maven 座標

Fasti BPM Platform のアーティファクトには独自の Maven 座標があります。**Java パッケージは
`org.camunda.bpm.*` のまま**なので、アプリケーションコードのインポートは維持できます。設定の接頭辞 `camunda.bpm.*`、
BPMN/DMN の名前空間、データベーススキーマ、WildFly のモジュール名も
変更されません。Maven に関して変更されるのは依存関係の宣言だけです
（Web アプリケーションのパスは別途変更されます。
[Web アプリケーションのパス](web-ui.ja.md#web-application-path-fasti) を参照）。

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
