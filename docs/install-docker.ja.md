[English](install-docker.md) | [Tiếng Việt](install-docker.vi.md) | [Deutsch](install-docker.de.md) | [Español](install-docker.es.md) | [Français](install-docker.fr.md) | [हिन्दी](install-docker.hi.md) | [Bahasa Indonesia](install-docker.id.md) | 日本語 | [한국어](install-docker.ko.md) | [Português (Brasil)](install-docker.pt-BR.md) | [Русский](install-docker.ru.md) | [ไทย](install-docker.th.md) | [简体中文](install-docker.zh-CN.md)

> この翻訳はコミュニティによって管理されており、英語版より古い場合があります。正式な内容は英語版です。修正を歓迎します。

# Docker を使用した Fasti BPM のインストール

H2 での簡単な試用、PostgreSQL を使用した本番運用（`docker run` または
`docker compose`）、既存の Camunda 7 データベースへの接続、並行稼働、ヘルス
チェック、アップグレード、ロールバック、トラブルシューティングを順を追って説明します。イメージの完全なリファレンス：
[distro/docker/README.md](../distro/docker/README.md)。Fasti BPM は Camunda 7 CE
7.24.0 を基盤とする独立したプロジェクトであり、Camunda Services GmbH とは提携関係にありません。

<a id="1-requirements"></a>

## 1. 要件

- Docker Compose v2（`docker compose`）を備えた Docker Engine。
- 実際のワークロードでは、Fasti コンテナーに少なくとも CPU 2 コアとメモリ 2 GB を割り当ててください（これを下回ると
  JVM は Serial GC を選択します）。
- 本番用データベース：PostgreSQL、MariaDB、MySQL、SQL Server、Oracle（
  [検証済みの組み合わせ](migrate-from-camunda-7.ja.md#limits)を参照）。DB2 は未検証です。H2 は評価専用です。

<a id="2-pull-the-image"></a>

## 2. イメージの取得

<!-- update with the Docker Hub image name at release -->
amd64 および arm64 向けのイメージを、次の 2 か所で公開しています。

```bash
docker pull fastidev/fasti-bpm:1.1.0              # Docker Hub
docker pull ghcr.io/fasti-bpm/fasti-bpm:1.1.0     # GitHub Container Registry
```

タグ：`1.1.0`（そのリリースを厳密に指定）、`1.1`（1.1 の最新パッチリリース）、`latest`（最後に公開された
リリース）。イメージは cosign（キーレス、GitHub OIDC）で署名されており、SBOM と来歴証明が付属しています。
1.0.1 以降の署名は cosign 3 の Sigstore バンドル形式です。1.0.0 のイメージには旧来の cosign 2 形式の署名が付いています。

ソースからイメージをビルドすることもできます（JDK 17+ と、Maven
Central、nodejs.org、npm レジストリへのネットワークアクセスが必要です）。その場合、以降のすべてのコマンドで
`fastidev/fasti-bpm:1.1.0` の代わりに `fasti-bpm:local` を使用してください。

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="3-quick-trial-with-h2"></a>

## 3. H2 での簡単な試用

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  -v fasti-data:/fasti/data \
  fastidev/fasti-bpm:1.1.0
```

- <http://localhost:8080/fasti/> を開き、`admin` としてサインインします。
- `DB_URL` を指定しない場合、イメージは `/fasti/data` 内の H2 を使用します。`fasti-data` ボリュームにより、
  コンテナーを再作成してもデータが保持されます。評価専用であり、実データには決して使用しないでください。
- サンプルプロセス（請求書）も利用するには、コマンドの末尾に `--webapps --rest --example` を
  追加します。`--example` だけでは、Web UI と REST API は無効のままです。
- REST API のアドレスは `http://localhost:8080/engine-rest` で、HTTP Basic 認証情報が必要です。
  [REST API ガイド](api/README.md)を参照してください。

<a id="4-production-with-postgresql-docker-run"></a>

## 4. PostgreSQL を使用した本番運用：`docker run`

シークレットファイルを 2 つ（データベースのパスワードと管理者のパスワード）作成します。`--production` を指定すると、エンジンは
パスワードポリシーを適用します。パスワードは 10 文字以上で、大文字、小文字、数字、
特殊文字を含む必要があります。

```bash
mkdir -p secrets
openssl rand -hex 24 > secrets/db-password.txt
echo "Fasti-1-$(openssl rand -hex 12)" > secrets/admin-password.txt
chmod 0700 secrets           # other users on the host cannot enter the directory
chmod 0644 secrets/*.txt     # the containers read them as uid 999 (PostgreSQL) and 1000 (Fasti)
```

```bash
docker network create fasti-net

docker run -d --name fasti-db --network fasti-net \
  -e POSTGRES_DB=fasti -e POSTGRES_USER=fasti \
  -e POSTGRES_PASSWORD_FILE=/run/secrets/db-password \
  -v "$PWD/secrets/db-password.txt:/run/secrets/db-password:ro" \
  -v fasti-pgdata:/var/lib/postgresql/data \
  postgres:16

docker run -d --name fasti --network fasti-net -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://fasti-db:5432/fasti \
  -e DB_USERNAME=fasti -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v "$PWD/secrets/db-password.txt:/run/secrets/db-password:ro" \
  -e WAIT_FOR=fasti-db:5432 -e WAIT_FOR_TIMEOUT=60 \
  -e FASTI_DB_SCHEMA_UPDATE=true \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD_FILE=/run/secrets/admin-password \
  -v "$PWD/secrets/admin-password.txt:/run/secrets/admin-password:ro" \
  -e TZ=UTC \
  --memory 2g --stop-timeout 60 \
  fastidev/fasti-bpm:1.1.0 --production
```

- `FASTI_DB_SCHEMA_UPDATE=true` は、新しいデータベースに不足しているテーブルを作成するだけです。Fasti が
  既存のスキーマをアップグレードすることはありません。以降の起動時にも、この設定を維持できます。
- `--production` は `Secure` Cookie を有効にし、暗黙の H2 データベースの使用と `DEBUG=true` を拒否します。
  Fasti の前段に配置したリバースプロキシで TLS を終端してください。ブラウザーは HTTPS 経由（または
  `localhost` 宛て）でのみ `Secure` Cookie を送信します。
- `WAIT_FOR` は、データベースが接続を受け付けるまで起動を待機させます。
- 管理者パスワードは、管理者がまだ存在しない場合にのみ設定されます。後から変更するには Admin を使用してください。

<a id="5-production-with-postgresql-docker-compose"></a>

## 5. PostgreSQL を使用した本番運用：`docker compose`

サンプルファイル：[distro/docker/examples/docker-compose.postgres.yml](../distro/docker/examples/docker-compose.postgres.yml)
（PostgreSQL 16、シークレット、ヘルスチェック、`--production`、メモリ上限 2 GB）。

```bash
cd distro/docker/examples          # or copy the file to a directory of its own
mkdir -p secrets
openssl rand -hex 24 > secrets/db-password.txt
echo "Fasti-1-$(openssl rand -hex 12)" > secrets/admin-password.txt
chmod 0700 secrets
chmod 0644 secrets/*.txt
docker compose -f docker-compose.postgres.yml up -d
docker compose -f docker-compose.postgres.yml ps      # both services "healthy"
```

起動時に設定できる変数：`FASTI_IMAGE`（既定値は `fastidev/fasti-bpm:1.1.0`。設定例：
`FASTI_IMAGE=fasti-bpm:local`）、`FASTI_HOST_PORT`（既定値は `8080`）、`TZ`（既定値は `UTC`）。

<a id="6-connecting-to-an-existing-camunda-7-database"></a>

## 6. 既存の Camunda 7 データベースへの接続

Fasti は 7.24 スキーマを変更せずに動作します。開始前に、次を実施してください。

1. データベースを**バックアップ**します。
2. スキーマのバージョンを確認します：`select VERSION_ from ACT_GE_SCHEMA_LOG`。最大値が
   `7.24.x` である必要があります。スキーマが 7.17–7.23 の場合は、先に `sql/upgrade` スクリプトを実行してください（イメージ内の
   `/fasti/configuration/sql/upgrade` に同梱）。
   [移行ガイド](migrate-from-camunda-7.ja.md#older-versions-717723)を参照してください。
3. Fasti は認可を適用するため、認可設定を確認します：`select * from ACT_ID_MEMBERSHIP where
   GROUP_ID_='camunda-admin'` および `select count(*) from ACT_RU_AUTHORIZATION`。旧システムで
   認可を無効にしていた場合は、トラフィックを切り替える前に一般ユーザーに権限を付与してください。
4. 旧ノードの `TZ`、履歴レベル、テーブルのプレフィックス／スキーマ、パスワード設定を
   記録します。

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=UTC \
  fastidev/fasti-bpm:1.1.0 --production
```

- ポートはこのマシンにのみバインドされます。リモートアクセスには、HTTPS 対応のリバースプロキシを経由してください。
- テーブルとアカウントは既に存在するため、`FASTI_DB_SCHEMA_UPDATE` や `FASTI_ADMIN_*` を**設定しないでください**。
  起動ログに `ENGINE-03115 Database schema version 7.24.0 matches this engine.` が含まれている必要があります。
- 履歴レベルの既定値は `auto` で、データベースに保存されているレベルが使用されます。
- すべての REST クライアントは、Basic 認証情報または Bearer トークンを送信する必要があります。
- ドライバー：PostgreSQL、MySQL、MariaDB、SQL Server、H2 は同梱されています。**Oracle** と **DB2** のドライバー（`ojdbc11.jar`、`jcc.jar`）は、`-v /driver/dir:/drivers:ro -e FASTI_EXTRA_LIB=/drivers` を使用して自分で追加してください。
  SQL Server：ドライバー 13.x は、既定で接続を暗号化し、証明書を検証します。
  サーバーには、URL 内のホスト名と一致する有効な証明書が必要です。`trustServerCertificate=true`
  （証明書の検証を省略）または `encrypt=false`（暗号化なし）は、ローカルでの試用にのみ使用してください。MySQL/MariaDB には
  `transaction-isolation=READ-COMMITTED` が必要です。

<a id="7-side-by-side-with-camunda-7-nodes"></a>

## 7. Camunda 7 ノードとの並行稼働

Fasti と Camunda 7.24 は、1 つのデータベース上で同時に稼働できます（相互のデータを参照できることを
検証済み）。条件は次のとおりです。

- 履歴レベル、`TZ`、パスワード設定、テーブルのプレフィックスが同じであること。
- 旧ノードが、Fasti に存在しない Java クラス（`JavaDelegate`、リスナー）を含むプロセスアプリケーションである場合は、Fasti に `-e FASTI_JOB_EXECUTOR_ENABLED=false` を設定するか、**すべての**ノードで
  デプロイメントを認識するように設定してください（Fasti では `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE=true`）。この設定で保護されるのは
  非同期ジョブのみです。Fasti 経由でインスタンスを開始したりタスクを完了したりすると、デリゲートは引き続き
  Fasti 上で実行されるため、それらのリクエストは該当する Java クラスを持つノードに送信してください。
- ロードバランサー：Web アプリケーションにはスティッキーセッションが必要です。Basic 認証情報を使用する REST には不要です。

詳細：[Camunda 7 ノードとの混在運用](migrate-from-camunda-7.ja.md#mixed-operation-with-camunda-7-nodes)。

<a id="8-common-environment-variables"></a>

## 8. 主な環境変数

| 変数 | 意味 |
|---|---|
| `DB_URL`, `DB_DRIVER`, `DB_USERNAME`, `DB_PASSWORD` (`_FILE`) | データベース接続（従来の Camunda 7 Run イメージの変数名。別名：`FASTI_DB_*`） |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | 起動前に `host:port` が接続を受け付けるまで待機 |
| `TZ` | JVM のタイムゾーン。すべてのノードで同じである必要がある |
| `FASTI_DB_SCHEMA_UPDATE` | `true`：新しいデータベースに不足しているテーブルを作成 |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (`_FILE`) | 管理者が存在しない場合に作成 |
| `FASTI_JOB_EXECUTOR_ENABLED`, `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | このノードのジョブエグゼキューター |
| `FASTI_EXTRA_LIB` | 追加の jar（Oracle、DB2 ドライバー）を配置するディレクトリ |
| `FASTI_DB_CONN_MAXACTIVE` | 接続プールのサイズ（既定値 10） |
| `JAVA_OPTS`, `FASTI_MAX_RAM_PERCENTAGE` | JVM オプション、ヒープに割り当てるメモリの割合（75） |

完全な一覧：[distro/docker/README.md](../distro/docker/README.md#environment-variables)。

<a id="9-health-checks"></a>

## 9. ヘルスチェック

| エンドポイント | 用途 |
|---|---|
| `/actuator/health/liveness` | Kubernetes の liveness プローブ |
| `/actuator/health/readiness` | readiness プローブ、ロードバランサー。データベース接続とエンジンを含む |
| `/actuator/health` | 全体 |

ログインは不要です。公開されるのはヘルスエンドポイントのみです。イメージの `HEALTHCHECK` は
readiness を使用するため、Fasti の準備が完了すると `docker ps` に `healthy` と表示されます。

```bash
curl -s http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
docker inspect -f '{{.State.Health.Status}}' fasti           # healthy
```

Kubernetes：`livenessProbe` → `/actuator/health/liveness`、`readinessProbe` →
`/actuator/health/readiness`、`terminationGracePeriodSeconds: 60`。

<a id="10-upgrade-and-rollback"></a>

## 10. アップグレードとロールバック

**Fasti のアップグレード**（例：`1.0.1` → `1.1.0`）：

```bash
docker pull fastidev/fasti-bpm:1.1.0
docker stop -t 60 fasti && docker rm fasti
docker run -d --name fasti ... fastidev/fasti-bpm:1.1.0 --production   # same options as before
```

Docker Compose の場合：`FASTI_IMAGE`（または `image:` 行）を変更し、
`docker compose -f docker-compose.postgres.yml up -d` を実行します。事前に [変更履歴](../CHANGELOG.md)、
特に **BREAKING** の項目を確認してください。`1.x` 系列では 7.24 スキーマが維持されるため、この系列内の
アップグレードでスキーマが変更されることはありません。`docker stop -t 60` により、コンテナーを停止する前に
実行中のリクエストが完了するまで待機できます。

**ロールバック：**

- 以前の Fasti リリースに戻す場合：古いタグで再起動します。
- Camunda 7.24 に戻す場合：Fasti を停止し、同じデータベースを使用して Camunda 7.24 ノードを再起動します。新しい UI の設定は残ります（`ACT_ID_INFO` 内の `fasti.prefs.*`。Camunda 7 では無視されます）。Fasti 専用の機能を使用するデプロイ済みプロセスは、Camunda 7 では動作しません。
  [Camunda 7.24 へのロールバック](migrate-from-camunda-7.ja.md#rollback-to-camunda-724)を参照してください。

<a id="11-troubleshooting"></a>

## 11. トラブルシューティング

`docker logs fasti`（または `docker compose logs fasti`）でログを確認してください。

| 症状 | 原因と対処方法 |
|---|---|
| REST API が `401` を返す | Fasti では認証が必要です。`-u user:password` または Bearer トークンを送信してください |
| 一覧が空、または `403` が返される | ユーザーに権限がありません。Admin → Authorizations で、対象リソースに必要な権限を付与してください。`camunda-admin` に追加するのは、実際の管理者のみにしてください |
| `ENGINE-03111 The database schema has version …` と表示されて停止する | スキーマが 7.24 より古くなっています。バックアップを作成し、`sql/upgrade` スクリプトを実行してください（セクション 6）。`FASTI_SCHEMA_VERSION_CHECK=warn` でチェックを回避しないでください。古いスキーマでの実行はサポートされておらず、運用中に障害が発生する可能性があります |
| `Insecure configuration refused` と表示されて停止する | REST 認証または認可を無効にする設定があります。その設定を削除してください（デモ用の `FASTI_INSECURE_DEMO=true` のみ、インスタンスへのアクセス制限を解除します） |
| `Password does not match policy` と表示されて停止する | `--production` は `FASTI_ADMIN_PASSWORD` にパスワードポリシーを適用します。10 文字以上で、大文字、小文字、数字、特殊文字を含む必要があります |
| `password authentication failed` | シークレットファイル内のパスワードが、データベースのパスワードと異なっています。Windows で作成したファイルは末尾が `\r\n` になっている場合があります。`tr -d '\r\n'` を使用して作成し直してください。PostgreSQL は、ボリュームを初めて初期化するときにのみ `POSTGRES_PASSWORD_FILE` を読み取ります |
| `FASTI_ADMIN_USER needs FASTI_ADMIN_PASSWORD` | `FASTI_ADMIN_PASSWORD` または `FASTI_ADMIN_PASSWORD_FILE` を設定してください |
| `--production needs a database` | データベースが設定されていません。`--production` はデモ用の H2 データベースにフォールバックしません。`DB_URL` に本番用データベースを設定してください |
| `_FILE` 変数に対して `… cannot be read` と表示される | ファイルがコンテナーにマウントされていないか、uid 1000 で読み取れません（ファイルのモードは `0644` が必要です。ディレクトリは `0700` のままでかまいません） |
| HTTP 経由で Web にログインしてもセッションが維持されない | `Secure` Cookie（`--production`）は HTTPS 経由（または `localhost` 宛て）でのみ送信されます。プロキシで TLS を終端してください |
| SQL Server に接続できない（証明書エラー） | URL 内のホスト名と一致する有効な証明書を SQL Server に設定してください。`trustServerCertificate=true` はローカルでの試用専用です |
| コンテナーが再起動を繰り返し、ログに `OutOfMemoryError` が表示される | メモリが不足すると JVM は終了します。コンテナーのメモリ上限（2 GB）を引き上げてください |

解決しない場合は、ログからパスワードを削除したうえで、ログを添えて GitHub の Issue を作成してください。セキュリティ上の問題については、
[SECURITY.md](../SECURITY.md#security-policy) を参照してください。

<a id="12-without-docker"></a>

## 12. Docker を使用しない場合

構成済みの Tomcat 1.0.1 バンドル（ダウンロードページ：<https://fasti.dev/download>）：
- [fasti-bpm-tomcat-1.0.1.zip](https://assets.fasti.dev/releases/1.0.1/fasti-bpm-tomcat-1.0.1.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.1/fasti-bpm-tomcat-1.0.1.zip.sha256))
- [fasti-bpm-tomcat-1.0.1.tar.gz](https://assets.fasti.dev/releases/1.0.1/fasti-bpm-tomcat-1.0.1.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.1/fasti-bpm-tomcat-1.0.1.tar.gz.sha256))

展開する前に、たとえば `sha256sum -c fasti-bpm-tomcat-1.0.1.zip.sha256` でチェックサムを検証してください。
