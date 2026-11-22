[English](install-docker.md) | [Tiếng Việt](install-docker.vi.md) | [Deutsch](install-docker.de.md) | [Español](install-docker.es.md) | [Français](install-docker.fr.md) | [हिन्दी](install-docker.hi.md) | [Bahasa Indonesia](install-docker.id.md) | [日本語](install-docker.ja.md) | [한국어](install-docker.ko.md) | [Português (Brasil)](install-docker.pt-BR.md) | [Русский](install-docker.ru.md) | [ไทย](install-docker.th.md) | 简体中文

> 本译文由社区维护，可能落后于英文版；以英文版为准。欢迎指正。

# 使用 Docker 安装 Fasti BPM

分步指南：使用 H2 快速试用、使用 PostgreSQL 部署生产环境（`docker run` 或
`docker compose`）、连接现有 Camunda 7 数据库、并行运行模式、健康
检查、升级、回滚和故障排查。完整镜像参考文档：
[distro/docker/README.md](../distro/docker/README.md)。Fasti BPM 基于 Camunda 7 CE
7.24.0 构建，是一个独立项目，与 Camunda Services GmbH 无隶属关系。

<a id="1-requirements"></a>

## 1. 环境要求

- Docker Engine 和 Docker Compose v2（`docker compose`）。
- 对于实际工作负载：为 Fasti 容器至少分配 2 个 CPU 和 2 GB 内存（低于此配置时，
  JVM 会选择 Serial GC）。
- 生产数据库：PostgreSQL、MariaDB、MySQL、SQL Server 或 Oracle（参见
  [已测试的兼容性矩阵](migrate-from-camunda-7.zh-CN.md#limits)）。DB2 尚未验证。H2 仅供评估使用。

<a id="2-pull-the-image"></a>

## 2. 拉取镜像

<!-- update with the Docker Hub image name at release -->
镜像支持 amd64 和 arm64，在以下两个位置发布：

```bash
docker pull fastidev/fasti-bpm:1.2.0              # Docker Hub
docker pull ghcr.io/fasti-bpm/fasti-bpm:1.2.0     # GitHub Container Registry
```

标签：`1.2.0`（指定的确切发行版本）、`1.2`（1.2 的最新补丁版本）和 `latest`（最近发布的版本）。镜像使用
cosign 签名（无密钥，GitHub OIDC），并附带 SBOM 和来源证明。从 1.0.1 起，签名采用 cosign 3 的 Sigstore
bundle 格式；1.0.0 镜像带有旧版 cosign 2 格式的签名。

也可以从源代码构建镜像（需要 JDK 17+，并能够通过网络访问 Maven
Central、nodejs.org 和 npm registry），然后在下方所有命令中使用 `fasti-bpm:local` 替换
`fastidev/fasti-bpm:1.2.0`：

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="3-quick-trial-with-h2"></a>

## 3. 使用 H2 快速试用

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  -v fasti-data:/fasti/data \
  fastidev/fasti-bpm:1.2.0
```

- 打开 <http://localhost:8080/fasti/>，以 `admin` 身份登录。
- 未设置 `DB_URL` 时，镜像会使用 `/fasti/data` 中的 H2；`fasti-data` 卷会在
  重新创建容器时保留数据。仅供评估使用，切勿用于实际数据。
- 在命令末尾追加 `--webapps --rest --example`，还可获得一个示例流程（发票）。
  仅使用 `--example` 不会启用 Web 界面和 REST API。
- REST API 地址为 `http://localhost:8080/engine-rest`，需要 HTTP Basic 身份验证凭据；参见
  [REST API 指南](api/README.md)。

<a id="4-production-with-postgresql-docker-run"></a>

## 4. 使用 PostgreSQL 部署生产环境：`docker run`

创建两个密钥文件（数据库密码和管理员密码）。使用 `--production` 时，引擎
会强制执行密码策略：至少 10 个字符，包含大写字母、小写字母、数字
和特殊字符。

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
  fastidev/fasti-bpm:1.2.0 --production
```

- `FASTI_DB_SCHEMA_UPDATE=true` 只会在新数据库中创建缺失的表；Fasti 绝不会
  升级现有数据库结构。后续启动时可以保留此设置。
- `--production` 会启用 `Secure` Cookie，并拒绝隐式使用 H2 数据库以及设置 `DEBUG=true`。
  请在 Fasti 前端的反向代理上终止 TLS；浏览器仅通过 HTTPS（或向
  `localhost`）发送 `Secure` Cookie。
- `WAIT_FOR` 会延迟启动，直到数据库能够接受连接。
- 仅在管理员尚不存在时设置管理员密码；之后请在管理界面中修改。

<a id="5-production-with-postgresql-docker-compose"></a>

## 5. 使用 PostgreSQL 部署生产环境：`docker compose`

示例文件：[distro/docker/examples/docker-compose.postgres.yml](../distro/docker/examples/docker-compose.postgres.yml)
（PostgreSQL 16、密钥、健康检查、`--production`、2 GB 内存限制）。

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

启动时可设置的变量：`FASTI_IMAGE`（默认值为 `fastidev/fasti-bpm:1.2.0`，例如
`FASTI_IMAGE=fasti-bpm:local`）、`FASTI_HOST_PORT`（默认值为 `8080`）、`TZ`（默认值为 `UTC`）。

<a id="6-connecting-to-an-existing-camunda-7-database"></a>

## 6. 连接现有 Camunda 7 数据库

Fasti 可在 7.24 数据库结构上运行，无需修改该结构。开始之前：

1. **备份**数据库。
2. 检查数据库结构版本：`select VERSION_ from ACT_GE_SCHEMA_LOG` — 最大值必须为
   `7.24.x`。对于 7.17–7.23 的数据库结构：先运行 `sql/upgrade` 脚本（随镜像提供，位于
   `/fasti/configuration/sql/upgrade`），参见
   [迁移指南](migrate-from-camunda-7.zh-CN.md#older-versions-717723)。
3. 检查授权，因为 Fasti 会强制执行授权检查：`select * from ACT_ID_MEMBERSHIP where
   GROUP_ID_='camunda-admin'` 和 `select count(*) from ACT_RU_AUTHORIZATION`。如果旧系统
   运行时关闭了授权检查，请在切换流量之前为普通用户授予权限。
4. 记录旧节点的 `TZ`、历史记录级别、表前缀/数据库模式以及
   密码设置。

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=UTC \
  fastidev/fasti-bpm:1.2.0 --production
```

- 端口仅绑定到本机；如需远程访问，请通过启用 HTTPS 的反向代理。
- **不要**设置 `FASTI_DB_SCHEMA_UPDATE` 或 `FASTI_ADMIN_*`：表和账户已经存在。
  启动日志必须包含 `ENGINE-03115 Database schema version 7.24.0 matches this engine.`。
- 历史记录级别默认为 `auto`：使用数据库中存储的级别。
- 现在，每个 REST 客户端都必须发送 Basic 身份验证凭据或 Bearer 令牌。
- 驱动程序：已包含 PostgreSQL、MySQL、MariaDB、SQL Server 和 H2 驱动程序。请使用 `-v /driver/dir:/drivers:ro -e FASTI_EXTRA_LIB=/drivers` 自行添加 **Oracle** 和 **DB2** 驱动程序（`ojdbc11.jar`、`jcc.jar`）。
  SQL Server：13.x 驱动程序默认会加密连接并验证证书；
  服务器需要有效证书，且证书必须与 URL 中的主机名匹配。`trustServerCertificate=true`
  （跳过证书检查）或 `encrypt=false`（不加密）仅供本地试用。MySQL/MariaDB 需要设置
  `transaction-isolation=READ-COMMITTED`。

<a id="7-side-by-side-with-camunda-7-nodes"></a>

## 7. 与 Camunda 7 节点并行运行

Fasti 和 Camunda 7.24 可以同时在同一个数据库上运行（已测试：两者均能看到对方的
数据）。条件如下：

- 历史记录级别、`TZ`、密码设置和表前缀均相同；
- 如果旧节点是包含 Java 类（`JavaDelegate`、监听器）的流程应用，而 Fasti 缺少这些类，请在 Fasti 上设置 `-e FASTI_JOB_EXECUTOR_ENABLED=false`，或让**每个**节点
  都启用部署感知（Fasti 使用 `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE=true`）。这仅能保护
  异步作业：通过 Fasti 启动实例或完成任务时，仍会在 Fasti 上运行委托类，
  因此应将这些请求发送到包含相应 Java 类的节点；
- 负载均衡器：Web 应用需要粘性会话；使用 Basic 身份验证凭据的 REST 请求不需要。

详情：[与 Camunda 7 节点混合运行](migrate-from-camunda-7.zh-CN.md#mixed-operation-with-camunda-7-nodes)。

<a id="8-common-environment-variables"></a>

## 8. 常用环境变量

| 变量 | 含义 |
|---|---|
| `DB_URL`, `DB_DRIVER`, `DB_USERNAME`, `DB_PASSWORD` (`_FILE`) | 数据库连接（沿用原 Camunda 7 Run 镜像的变量名；别名：`FASTI_DB_*`） |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | 启动前等待 `host:port` 可用 |
| `TZ` | JVM 时区；所有节点必须相同 |
| `FASTI_DB_SCHEMA_UPDATE` | `true`：在新数据库中创建缺失的表 |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (`_FILE`) | 管理员不存在时创建管理员 |
| `FASTI_JOB_EXECUTOR_ENABLED`, `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | 此节点的作业执行器 |
| `FASTI_EXTRA_LIB` | 附加 jar 文件所在目录（Oracle、DB2 驱动程序） |
| `FASTI_DB_CONN_MAXACTIVE` | 连接池最大大小（默认值为 20；空闲时保持 10 个，`FASTI_DB_CONN_MINIDLE`） |
| `JAVA_OPTS`, `FASTI_MAX_RAM_PERCENTAGE` | JVM 选项、分配给堆的内存百分比（75） |

完整表格：[distro/docker/README.md](../distro/docker/README.md#environment-variables)。

<a id="9-health-checks"></a>

## 9. 健康检查

| 端点 | 用途 |
|---|---|
| `/actuator/health/liveness` | Kubernetes 存活探针 |
| `/actuator/health/readiness` | 就绪探针、负载均衡器；检查范围包括数据库连接和引擎 |
| `/actuator/health` | 整体健康状态 |

无需登录；仅开放健康检查端点。镜像的 `HEALTHCHECK` 使用
就绪检查，因此 Fasti 就绪后，`docker ps` 会显示 `healthy`：

```bash
curl -s http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
docker inspect -f '{{.State.Health.Status}}' fasti           # healthy
```

Kubernetes：`livenessProbe` → `/actuator/health/liveness`，`readinessProbe` →
`/actuator/health/readiness`，`terminationGracePeriodSeconds: 60`。

<a id="10-upgrade-and-rollback"></a>

## 10. 升级和回滚

**升级 Fasti**（例如 `1.1.0` → `1.2.0`）：

```bash
docker pull fastidev/fasti-bpm:1.2.0
docker stop -t 60 fasti && docker rm fasti
docker run -d --name fasti ... fastidev/fasti-bpm:1.2.0 --production   # same options as before
```

使用 Docker Compose 时：修改 `FASTI_IMAGE`（或 `image:` 行），然后执行
`docker compose -f docker-compose.postgres.yml up -d`。请先阅读[更新日志](../CHANGELOG.md)，
尤其是 **BREAKING** 条目。`1.x` 系列保留 7.24 数据库结构，因此该系列内的升级
不会改动数据库结构。`docker stop -t 60` 会让正在处理的请求完成后再
停止容器。

**回滚：**

- 回滚到上一个 Fasti 发行版本：使用旧标签重新启动；
- 回滚到 Camunda 7.24：停止 Fasti，然后在同一数据库上重新启动 Camunda 7.24 节点。新界面的偏好设置会保留（存储在 `ACT_ID_INFO` 中的 `fasti.prefs.*`，Camunda 7 会忽略这些设置）。已部署的流程如果使用了 Fasti 专有功能，将无法在 Camunda 7 上运行。参见
  [回滚到 Camunda 7.24](migrate-from-camunda-7.zh-CN.md#rollback-to-camunda-724)。

<a id="11-troubleshooting"></a>

## 11. 故障排查

使用 `docker logs fasti`（或 `docker compose logs fasti`）查看日志。

| 现象 | 原因和解决方法 |
|---|---|
| REST API 返回 `401` | Fasti 要求身份验证：发送 `-u user:password` 或 Bearer 令牌 |
| 列表为空或返回 `403` | 用户缺少授权：在管理界面 → 授权中，为其授予相关资源所需的权限。仅将真正的管理员添加到 `camunda-admin` |
| 停止运行并显示 `ENGINE-03111 The database schema has version …` | 数据库结构版本低于 7.24：先备份，再运行 `sql/upgrade` 脚本（第 6 节）。不要使用 `FASTI_SCHEMA_VERSION_CHECK=warn` 绕过检查：不支持在旧版数据库结构上运行，运行过程中可能出现故障 |
| 停止运行并显示 `Insecure configuration refused` | 某个设置关闭了 REST 身份验证或授权检查；请移除该设置（只有 `FASTI_INSECURE_DEMO=true` 会开放实例，仅供演示使用） |
| 停止运行并显示 `Password does not match policy` | `--production` 会对 `FASTI_ADMIN_PASSWORD` 强制执行密码策略：至少 10 个字符，包含大写字母、小写字母、数字和特殊字符 |
| `password authentication failed` | 密钥文件中的密码与数据库密码不同。在 Windows 上创建的文件可能以 `\r\n` 结尾：使用 `tr -d '\r\n'` 重新生成文件。PostgreSQL 仅在首次初始化卷时读取 `POSTGRES_PASSWORD_FILE` |
| `FASTI_ADMIN_USER needs FASTI_ADMIN_PASSWORD` | 设置 `FASTI_ADMIN_PASSWORD` 或 `FASTI_ADMIN_PASSWORD_FILE` |
| `--production needs a database` | 未配置数据库；`--production` 不会回退到演示用 H2 数据库：请将 `DB_URL` 设置为生产数据库 |
| `_FILE` 变量出现 `… cannot be read` | 文件未挂载到容器中，或 uid 1000 无法读取该文件（文件权限需设为 `0644`；其目录权限可保持为 `0700`） |
| 通过 HTTP 登录 Web 界面后无法保持会话 | `Secure` Cookie（`--production`）仅通过 HTTPS（或向 `localhost`）发送：请在代理上终止 TLS |
| 无法连接 SQL Server（证书错误） | 为 SQL Server 配置有效证书，且证书必须与 URL 中的主机名匹配；`trustServerCertificate=true` 仅供本地试用 |
| 容器不断重启，日志显示 `OutOfMemoryError` | JVM 会在内存耗尽时退出：提高容器内存限制（2 GB） |

仍然无法解决？请提交 GitHub issue，并附上日志（移除密码）。安全问题：参见
[SECURITY.md](../SECURITY.md#security-policy)。

<a id="12-without-docker"></a>

## 12. 不使用 Docker

预构建的 Tomcat 1.1.0 软件包（下载页面：<https://fasti.dev/download>）：
- [fasti-bpm-tomcat-1.1.0.zip](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.zip.sha256))
- [fasti-bpm-tomcat-1.1.0.tar.gz](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.1.0/fasti-bpm-tomcat-1.1.0.tar.gz.sha256))

解压前请验证校验和，例如 `sha256sum -c fasti-bpm-tomcat-1.1.0.zip.sha256`。
