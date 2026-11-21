[English](web-ui.md) | [Tiếng Việt](web-ui.vi.md) | [Deutsch](web-ui.de.md) | [Español](web-ui.es.md) | [Français](web-ui.fr.md) | [हिन्दी](web-ui.hi.md) | [Bahasa Indonesia](web-ui.id.md) | 日本語 | [한국어](web-ui.ko.md) | [Português (Brasil)](web-ui.pt-BR.md) | [Русский](web-ui.ru.md) | [ไทย](web-ui.th.md) | [简体中文](web-ui.zh-CN.md)

> この翻訳はコミュニティによって管理されており、英語版より古い場合があります。正式な内容は英語版です。修正を歓迎します。

# Fasti の Web インターフェース

新しい Web インターフェースにこれらの機能がある理由と、Web アプリケーションが提供されるパスについて説明します。インターフェースの紹介：<https://fasti.dev/#features>

<a id="a-ui-built-from-real-user-complaints"></a>

## 実際のユーザーの不満をもとに構築した UI

新しい Web UI の機能は、Camunda 7 ユーザーが Camunda フォーラムや GitHub に公開した
意見をもとに選定しました。たとえば、次のようなものです。

| ユーザーの要望 | 出典 | Fasti での対応 |
|---|---|---|
| 複数の変数と OR 条件でインスタンスを検索したい | フォーラム [39269](https://forum.camunda.io/t/39269)、[45695](https://forum.camunda.io/t/45695)、[48976](https://forum.camunda.io/t/48976)、[64208](https://forum.camunda.io/t/64208)、[28855](https://forum.camunda.io/t/28855)、[4678](https://forum.camunda.io/t/4678)；[camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | 複数変数の条件ビルダー、AND/OR |
| ケースの各ステップのコメントを確認したい | フォーラム [30260](https://forum.camunda.io/t/30260)、[52109](https://forum.camunda.io/t/52109)、[2173](https://forum.camunda.io/t/2173) | タスク内のケースタイムライン；プロセスインスタンス自体へのコメント |
| 変数でタスクを並べ替え、古いものから表示したい | フォーラム [17599](https://forum.camunda.io/t/17599)、[42078](https://forum.camunda.io/t/42078)、[41193](https://forum.camunda.io/t/41193)、[67328](https://forum.camunda.io/t/67328) | 変数、期限、緊急度による並べ替え |
| 委任や不在時の代理担当者を設定したい | フォーラム [2788](https://forum.camunda.io/t/2788)、[38199](https://forum.camunda.io/t/38199)、[9331](https://forum.camunda.io/t/9331) | タスク単位の委任と、引き継ぎ付きの[不在時の代理人](absence.md)（1.1.0 以降） |
| フォームなしでプロセスを開始するときにビジネスキーと変数を指定したい | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | 対応済み |
| デプロイ済みの BPMN XML をダウンロードしたい | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | 対応済み |
| ラベルの一貫性と多言語対応がほしい | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 言語。BPMN、DMN、フォームの各エディター内の組み込みラベルは英語とベトナム語のみ |

<a id="cases-cmmn"></a>

## ケース (CMMN)

CMMN 1.1 でモデル化し `.cmmn` ファイルとしてデプロイしたケースは、新しい UI で監視・操作できます。
Cockpit → ケース (CMMN) ではケース定義をバージョン、図、未クローズのインスタンスとともに表示します。
ケースインスタンスでは各プランアイテムの状態を表示し、手動の遷移 (開始、完了、無効化、再有効化、
終了) を実行できます (元に戻せない操作は確認を求めます)。タスク、変数、履歴も表示します。
Cockpit → ケース履歴には未クローズとクローズ済みのケースインスタンスが並びます。Tasklist では、
ケースのタスクにケース名が表示され、ケースインスタンスへのリンクとケース図が表示されます。
図はモデルの CMMNDI から Fasti 自身が描画します。メンテナンスが終了した cmmn-js
ライブラリ (最終リリース 0.20.0) は使用しません。

<a id="web-application-path-fasti"></a>

## Web アプリケーションのパス：`/fasti`

すべてのディストリビューションで、Cockpit、Tasklist、Admin、新しい Web UI は `/fasti` 配下で提供されます（従来は `/camunda`）。
`http://localhost:8080/fasti/` で新しい UI
（`/fasti/app/next/default/`）が開き、従来のアプリケーションは `/fasti/app/cockpit/...`、
`/fasti/app/tasklist/...`、`/fasti/app/admin/...` にあります。REST API は `/engine-rest` のままです。

**従来のアプリケーションは非推奨です。** 従来の Cockpit、Tasklist、Admin、Welcome は、
いずれもサポートが終了した AngularJS 1.8.2 と Bootstrap 3 を使用しています。**コンテナイメージではデフォルトで
これらを提供しません**（破壊的変更）。それらのページと古いブックマークは新しい UI の対応するセクションにリダイレクトされ、ファイルへのリクエストには `404` を返します。`FASTI_CLASSIC_WEBAPPS=true` で
再び有効にできます（警告が表示されます）。[distro/docker/README.md](../distro/docker/README.md) を参照してください。
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
