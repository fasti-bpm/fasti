[English](migrate-from-camunda-7.md) | [Tiếng Việt](migrate-from-camunda-7.vi.md) | [Deutsch](migrate-from-camunda-7.de.md) | [Español](migrate-from-camunda-7.es.md) | [Français](migrate-from-camunda-7.fr.md) | [हिन्दी](migrate-from-camunda-7.hi.md) | [Bahasa Indonesia](migrate-from-camunda-7.id.md) | [日本語](migrate-from-camunda-7.ja.md) | [한국어](migrate-from-camunda-7.ko.md) | [Português (Brasil)](migrate-from-camunda-7.pt-BR.md) | [Русский](migrate-from-camunda-7.ru.md) | [ไทย](migrate-from-camunda-7.th.md) | 简体中文

> 本译文由社区维护，可能落后于英文版；以英文版为准。欢迎指正。

# 从 Camunda 7 迁移到 Fasti BPM

Fasti BPM 基于 Camunda 7 Community Edition 7.24.0 构建，兼容 Camunda 7
数据库。它是一个独立项目，与 Camunda Services GmbH 无关联。

<a id="summary"></a>

## 概述

如果您运行的是 Camunda 7 **7.24**（Run、容器镜像、Spring Boot starter、Tomcat 或
WildFly），Fasti 可以**直接使用同一个数据库，无需更改其模式**：它原样使用 7.24
模式，不会写入任何自身专用的表、列或模式版本。您可以替换
旧节点，也可以让 Fasti 与旧节点并行运行，还可以切回 Camunda 7.24（参见
[回滚](#rollback-to-camunda-724)以及[限制](#limits)中已测试的数据库）。

Camunda 7.23 及更早版本的数据库必须先使用 SQL 脚本升级到 7.24（参见
[旧版本](#older-versions-717723)）。Fasti 从不自行升级数据库模式；遇到旧模式时，它会拒绝
启动，并指出需要运行的脚本。

<a id="steps-container-image"></a>

## 操作步骤（容器镜像）

1. **备份数据库。** 以下步骤均不会更改数据库模式，但在对生产系统进行任何更改之前，
   都必须备份。
2. **检查模式版本：** `select VERSION_ from ACT_GE_SCHEMA_LOG` — 其中的最大值
   必须为 `7.24.x`。
3. **记录旧节点的当前配置**：`DB_*` 变量、历史记录级别
   （`camunda.bpm.history-level`、`ACT_GE_PROPERTY.historyLevel`）、REST 身份验证
   （`camunda.bpm.run.auth.enabled`）、授权（`camunda.bpm.authorization.enabled`）、
   插件、LDAP/SSO、JVM 时区、密码哈希配置（`passwordEncryptor`、`saltGenerator`）、
   表前缀/数据库模式。
4. **拉取镜像** `fastidev/fasti-bpm:1.2.1`（或按照
   [distro/docker/README.md](../distro/docker/README.md) 中的说明从源代码构建）。
5. **使用相同的 `DB_*` 变量启动 Fasti**（以及相同的 `TZ`）。与 Camunda Run 不同，
   Fasti 镜像**默认要求 REST 身份验证并强制执行授权检查**：数据库中的
   账号仍可使用，但每个 REST 客户端都必须发送 Basic 凭据或 Bearer 令牌，
   且不属于 `camunda-admin` 的用户需要在 `ACT_RU_AUTHORIZATION` 中获得授权（参见
   [distro/docker/README.md](../distro/docker/README.md) 中的“默认安全”）。
   `FASTI_INSECURE_DEMO=true` 仅供演示使用。Fasti 会记录以下日志：
   `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
6. **检查：** 使用现有账号登录 `/fasti/`；检查过滤器、任务、实例和历史记录是否齐全；调用 `GET /engine-rest/task`（携带 Basic 凭据）和 `GET /actuator/health/readiness`。
7. **将流量切换**（负载均衡器、DNS）到 Fasti；停止旧节点或让其继续运行（参见
   [混合运行](#mixed-operation-with-camunda-7-nodes)）。

Spring Boot starter / Tomcat / WildFly：将 `org.camunda.bpm:camunda-*` 构件替换为
对应的 `dev.fasti.platform:fasti-*` 构件，并保留 `camunda.bpm.*`、`bpm-platform.xml` 和
`org.camunda.bpm.*` 包。不要在类路径中同时保留两个引擎的 jar，否则 Fasti 会拒绝启动（此项检查的局限性参见
[embedding.zh-CN.md](embedding.zh-CN.md#startup-check) 中的“启动检查”）。

> **Spring Boot starter：** Fasti 运行于 **Spring Boot 4.1**，需要 **Java 17 或更高版本**。
> 使用 Camunda 7.24 starter（Spring Boot 3.5）的应用必须在切换构件的同时升级到 Spring Boot 4.1 和
> Java 17+。参见
> [embedding.zh-CN.md](embedding.zh-CN.md#what-is-different-from-camunda-7240-ce) 和
> [变更日志](../CHANGELOG.md#changed)。

<a id="tomcat-and-wildfly-distributions"></a>

## Tomcat 和 WildFly 发行版

> **接管 Camunda Run 的数据库时必须执行此设置**（通过 REST API 部署流程的服务器的数据库
> 也适用）：Tomcat 和 WildFly 发行版与 Camunda 7 一样，保留
> `jobExecutorDeploymentAware=true`。未在节点上注册的部署所包含的作业（定时器、异步延续、重试）
> **不会运行，也不会报告错误**。
> 启动前，请在
> `conf/bpm-platform.xml`（Tomcat）或 `standalone.xml` 的引擎属性（WildFly）中设置 `<property name="jobExecutorDeploymentAware">false</property>`。
> 如果其他节点运行的流程应用包含其自身的 Java 类，请保留 `true`（参见
> [混合运行](#mixed-operation-with-camunda-7-nodes)）。

与 Camunda 7.24 的 Tomcat/WildFly 发行版相比：

- **REST API 要求身份验证**（不兼容变更）。`/engine-rest` 已启用 `camunda-auth`
  过滤器（HTTP Basic）：每个 REST 客户端都必须发送引擎用户的用户名和密码，否则会收到 401；只有引擎列表（`/engine`）仍可公开访问。
  切换前，请为此前匿名调用 REST
  API 的 REST 客户端、外部任务工作器和脚本配置凭据。也支持 OpenID Connect Bearer 令牌（`fasti-oidc.properties`、
  `fasti.security.oidc.rest.bearer-enabled=true`）：携带有效令牌的请求无需密码。要关闭身份验证（**不建议**），请从
  `engine-rest` WAR 的 `WEB-INF/web.xml` 中移除 `camunda-auth` 过滤器及其 `filter-mapping`。
- **没有默认用户**（不兼容变更）。不再部署发票示例，因此不会
  创建 `demo/demo`（管理员）、`john`、`mary` 和 `peter`；其 WAR 文件为
  `examples/camunda-invoice.war`，可将其复制到 `webapps/`（Tomcat）或
  `standalone/deployments/`（WildFly）进行试用。对于空数据库，请打开 `/fasti/`
  并在初始化页面创建第一个管理员；对于 Camunda 7 数据库，请使用
  现有账号。
- **不再提供经典版 Cockpit、Tasklist、Admin 和 Welcome**（不兼容变更），与
  镜像中的行为一致：其页面和书签会跳转到新 UI 中的对应区域，对其文件的请求会返回 404。要临时重新启用它们（已弃用，将被移除），请使用以下任一设置：
  在 `CATALINA_OPTS`（Tomcat）或 `JAVA_OPTS`（WildFly）中设置 `-Dfasti.webapp.classic.enabled=true`，
  设置环境变量 `FASTI_CLASSIC_WEBAPPS=true`，或在 Web 应用的
  `WEB-INF/web.xml` 中设置上下文参数 `fasti.webapp.classic.enabled`。

<a id="what-is-identical"></a>

## 保持一致的部分

- 数据库模式和 `ACT_GE_PROPERTY`/`ACT_GE_SCHEMA_LOG` 中的行（Fasti 不会添加版本行）、
  安装 ID、ID 生成方式（Run/Spring Boot 中的 `StrongUuidGenerator`，以及引擎
  默认的 `DbIdGenerator`）。
- 密码：与 Camunda 7 一样，使用 SHA-512 和 16 字节的盐值；现有用户可以登录，
  在 Fasti 上更改的密码也可用于 Camunda 7。
- `/engine-rest` 下的 REST API：端点、参数和负载相同，并增加了额外端点（身份验证和部分状态码不同，
  参见[有哪些不同](#what-differs)）；外部任务
  （`fetchAndLock`、长轮询）、Java 和 JS 客户端、`JSESSIONID`、`XSRF-TOKEN`/`X-XSRF-TOKEN`、`camunda.bpm.*`
  属性、`camunda:` BPMN 命名空间。
- 新版 Tasklist 以相同格式读写经典版 Tasklist 的过滤器（`color`、`description`、`priority`、`refresh`、`variables`、
  `showUndefinedVariable`）。

<a id="what-differs"></a>

## 存在差异的部分

- Web 应用位于 `/fasti/app/...`；`/camunda/...` 会重定向至此（301/308）。如果代理仅对外提供 `/camunda`，请同时对外提供 `/fasti`，或设置 `FASTI_WEBAPP_PATH=/camunda`。根路径
  会打开新的 Web 应用（`fasti.webapp.default-app: welcome` 可恢复 Welcome，但
  镜像除外，因为其中的经典版应用已关闭）。
- **容器镜像中的不兼容变更：** 不再提供经典版 Cockpit、Tasklist、Admin 和 Welcome，
  因为 AngularJS 1.8.2 和 Bootstrap 3 已停止维护。其页面和书签，
  包括 `/camunda/app/` 下的页面和书签，都会重定向到新 Web UI 的对应区域（`#/cockpit`、
  `#/tasklist`、`#/admin`）；对其文件的请求会返回 404。请让用户改用新的 Web UI；
  在过渡期内，`FASTI_CLASSIC_WEBAPPS=true` 可重新提供经典版应用，同时会显示
  警告。Tomcat 和 WildFly 也不再提供这些应用（参见
  [Tomcat 和 WildFly 发行版](#tomcat-and-wildfly-distributions)）。Run zip 和
  Spring Boot starter 仍提供这些应用，但它们已被弃用，将在后续
  版本中移除（`fasti.webapp.classic.enabled: false` 可提前将其关闭）。
- 遇到旧数据库模式时，Fasti 会报出 `ENGINE-03111` 并拒绝启动，而不是在后续运行中失败
  （设置 `FASTI_SCHEMA_VERSION_CHECK=warn` 可仍然启动）。
- 查询和过滤器中的表达式仅限于允许列表中的内容（日期函数、当前
  用户）；使用自定义 Bean 或函数的过滤器需要在引擎配置
  属性 `allowedExpressionFunctionsInQueries`、`allowedExpressionBeansInQueries` 和
  `allowedExpressionPropertiesInQueries` 中声明它们。
- REST API 行为：在容器镜像以及 Tomcat 和 WildFly 发行版中，除引擎列表（`/engine`）外的每个请求都需要
  Basic 凭据或 bearer token，因此必须配置 REST 客户端和外部任务工作器发送这些凭据（其代码无需更改）。
  在所有发行版中，允许列表之外的查询表达式会被拒绝并返回 `400`；无权限读取或修改任务附件和评论时返回
  `403`（启用授权检查时）；部分此前返回 `500` 的无效请求现在返回 `400`（无法解析的部署资源、使用被禁止的
  Java 序列化格式的变量、类型错误的查询参数、违反密码策略的密码）。参见
  [与 Camunda 7 的差异](api/README.md#differences-from-camunda-7)。
- 镜像默认设置：模式更新为 `false`（新数据库使用 `FASTI_DB_SCHEMA_UPDATE=true`）；不提供
  演示用户、发票示例或“所有任务”过滤器；关闭 CORS（`FASTI_CORS_ALLOWED_ORIGINS=*`
  可恢复）；新增 `/actuator/health`；文件位于 `/fasti` 下（也会读取挂载在
  `/camunda/configuration` 下的文件）；不支持 `--swaggerui`。
- OAuth2/OIDC：组声明中的 `camunda-admin` 不再单独授予管理员权限
  （`fasti.security.oidc.groups.admin`）；`group-name-delimiter` 按字面文本处理，而非正则表达式；
  注销需要同源 POST 请求。
- 新版 Tasklist：未设置 `refresh` 的过滤器会自动刷新。
- Run jar 为 `fasti-bpm-run-core.jar`。
- 相较于 7.24.0 的安全修复（附件/评论授权、初始设置等）可能使某些此前成功的调用返回 403；参见 [CHANGELOG](../CHANGELOG.md) 的 *Security* 部分和 <https://fasti.dev/security>。

<a id="rollback-to-camunda-724"></a>

## 回滚到 Camunda 7.24

停止 Fasti，并重新启动连接同一数据库的 Camunda 7.24 节点。在
Fasti 上执行的所有操作都可在其中看到。遗留内容包括：`ACT_ID_INFO` 中的 `fasti.prefs.*` 行（新
Web UI 的偏好设置，Camunda 7 会忽略），以及新版 Tasklist 保存的过滤器中
原先不存在、后来添加的 `refresh: true`。使用 Fasti 专有功能的已部署流程无法在 Camunda 7 上运行。

<a id="mixed-operation-with-camunda-7-nodes"></a>

## 与 Camunda 7 节点混合运行

Fasti 和 Camunda 7.24 可以同时使用同一个数据库（已测试：双方均可看到对方的
数据，作业可在任一节点上运行）。要求如下：

- **历史记录级别相同**（镜像使用已存储的级别）、**JVM 时区相同**，
  密码设置和表前缀相同；
- **作业执行器：** 默认情况下，Fasti（镜像、Run、Spring Boot）与 Run 一样，会获取所有部署的作业。
  Tomcat 和 WildFly 发行版默认设置为 `jobExecutorDeploymentAware=true`（与 Camunda
  7 一样）：未在该节点上注册的部署所包含的作业（定时器、异步延续、重试），
  包括上次重启前由 Run 或通过 REST 创建的部署，其作业**不会
  运行**；接管 Run 的数据库时，请在 `conf/bpm-platform.xml`
  （Tomcat）或 `standalone.xml`（WildFly）中将其设置为 `false`。如果旧节点运行的流程应用包含 Fasti 中没有的类（`JavaDelegate`、监听器、Bean），
  Fasti 会获取其作业，随后这些作业会失败并产生事件（incident）。此时请在 Fasti 上设置
  `FASTI_JOB_EXECUTOR_ENABLED=false`，或让**每个**节点都启用部署感知（节点的
  作业执行器仅运行在该节点上注册的部署所包含的作业；通过
  REST 创建的部署仅在接收该请求的节点上注册，直到重启为止）。同步 REST 调用（完成任务、
  启动实例）仍会在接收请求的节点上运行委托逻辑 — 不要将此类
  流程路由到 Fasti；
- **负载均衡器：** Web 应用需要粘性会话；使用 Basic 身份验证的 REST 调用无需粘性会话；
- 经典版 Web 应用（运行于 Camunda 7 节点上，或设置了
  `FASTI_CLASSIC_WEBAPPS=true` 的 Fasti 上）与新版 Web 应用共享过滤器和用户。

<a id="older-versions-717723"></a>

## 旧版本（7.17–7.23）

先备份；按顺序运行 `sql/upgrade/<db>_engine_<from>_to_<to>.sql`，直至升级到 7.24，包括
当前次版本尚未执行的补丁脚本（随附于 `configuration/sql/upgrade`，在
镜像中位于 `/fasti/configuration/sql/upgrade`）；检查 `ACT_GE_SCHEMA_LOG` 是否包含 `7.24.0`；
启动 Fasti。以 7.22 → 7.24 为例：先运行 `postgres_engine_7.22_to_7.23.sql`，再运行
`postgres_engine_7.23_to_7.24.sql`。低于 7.24 的 Camunda 节点不得继续使用
升级后的模式运行，除非符合 Camunda 的滚动更新规则（仅允许跨一个次版本，且
旧节点不得使用新功能）。

<a id="performance"></a>

## 性能

镜像不再沿用 Camunda Run 的作业执行器和连接池默认值（Fasti 1.1 及以前与之一致）：它使用 10 个作业线程、容量为 10 的队列和每次获取 10 个作业（Camunda Run：核心线程 3、最大线程 10、队列 3、每次获取 3 个），连接池最多 20 个连接，空闲时保持 10 个（Camunda Run：10）。请按所有节点（包括 Camunda 7 节点）连接池的总和来设置数据库的连接上限（[运行时调优](tuning-runtime.md)，英文）。如果您曾调整原有 Camunda 7 安装的连接池或作业执行器，请沿用这些值（`FASTI_DB_CONN_MAXACTIVE`、`FASTI_DB_CONN_MINIDLE`、`CAMUNDA_BPM_JOB_EXECUTION_*`）；更多指导见 `distro/docker/README.md` 的“性能调优”部分。Fasti 从不自行更改数据库模式的索引；针对大型数据库，它以脚本形式提供可选索引，由管理员执行（`/fasti/configuration/sql/optional/<database>_fasti_perf_indexes_create.sql`，参见 [performance.md](performance.md)），可与 Camunda 7.24 节点安全共存，并可通过对应的 `_drop.sql` 删除。

<a id="limits"></a>

## 限制

- 仅支持 7.24 Community Edition 的数据库模式；尚未测试 Enterprise 补丁版本的模式。
- 已使用容器镜像测试以下流程（Camunda Run 7.24.0 创建数据库并填充初始数据 → Fasti
  接管 → 切回 Run → 两者同时运行）：

  | 数据库 | 驱动程序 | 接管 | 回滚 | 混合运行 | 升级 7.23 → 7.24 |
  |---|---|---|---|---|---|
  | PostgreSQL 16 | 内置 | 通过 | 通过 | 通过 | 未测试 |
  | MariaDB 11.8 | MariaDB Connector/J（内置）或 MySQL Connector/J | 通过 | 通过 | 通过 | 通过 |
  | MySQL 8.4 | 内置 | 通过 | — | 通过 | 通过 |
  | SQL Server 2022 (Developer) | 内置（`encrypt=false`） | 通过 | 通过 | 通过 | 未测试 |
  | Oracle Database 23ai Free | 通过 `FASTI_EXTRA_LIB` 加载 `ojdbc11` | 通过 | 通过 | 通过 | 未测试 |
  | DB2 | — | 未测试 | | | |

  另外：在 MariaDB（MariaDB Connector/J）上，两个 Fasti 节点并行完成相同任务并获取外部任务时，未发生任何丢失或重复；竞争失败的事务收到了乐观锁错误。MySQL/MariaDB 需要 `transaction-isolation=READ-COMMITTED`。在 MySQL 上，
  请为每个引擎分配独立的数据库：引擎（Camunda Run 和 Fasti 均如此）会在
  账号可见的每个数据库中查找表。
- 为 Camunda 7 构建的插件和流程应用必须基于 Fasti
  构件重新构建，才能在 Fasti 内运行。REST 客户端和外部任务客户端无需更改代码；使用容器镜像、Tomcat 或
  WildFly 时，必须将它们配置为发送 Basic 凭据或 bearer token。
- 模式检查仅读取 `ACT_GE_SCHEMA_LOG`，不会比较列。
