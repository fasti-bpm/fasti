[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | 简体中文

> 本译文由社区维护，可能落后于英文版；以英文版为准。欢迎指正。

# Fasti BPM Platform

**Make Camunda 7 Great Again.** Camunda 7 Community Edition 的最后一个版本为
7.24.0。Fasti BPM 在此基础上继续发展：沿用相同的引擎和数据库，
持续修复问题、升级依赖库，并提供全新的 Web UI。

> **Fasti BPM 与 Camunda Services GmbH 无关联；Camunda 是 Camunda Services GmbH 的商标。**
> Fasti BPM 是一个独立的开源项目（Apache-2.0）。它不属于 Camunda Services GmbH，也未获得该公司的
> 赞助或认可。“Camunda”这一名称仅用于说明
> 源代码的来源以及 Fasti 的兼容对象。

<a id="quick-start"></a>

## 快速开始

镜像在 Docker Hub（`fastidev/fasti-bpm`）和 GitHub
Container Registry（`ghcr.io/fasti-bpm/fasti-bpm`）上提供，支持 amd64 和 arm64：

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.0.0
```

打开 <http://localhost:8080/fasti/> 并登录。未设置 `DB_URL` 时，镜像使用容器内的 H2 数据库，
仅供评估使用。REST API 需要 Basic 身份验证凭据：

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

也可以从源代码**自行构建镜像**。这需要 Docker、JDK 17+，并且能够通过网络
访问 Maven Central、nodejs.org 和 npm registry（构建过程会为
Web UI 下载 Node/npm）；首次构建大约需要 10–20 分钟：

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
# then run the docker run command above with fasti-bpm:local instead of fastidev/fasti-bpm:1.0.0
```

镜像**默认要求 REST 身份验证并强制执行授权检查**（与 Camunda
Run 不同）；只有设置 `FASTI_INSECURE_DEMO=true` 才会关闭这些检查，仅供演示使用。

<a id="highlights"></a>

## 主要特性

- **全新的 Web UI**，位于 `/fasti/`（React）：
  - 同时按多个变量搜索流程实例，支持 AND/OR 组合；
  - 在任务中查看业务个案时间线（包含每个步骤的评论和操作）；
  - “获取下一项工作”按钮；
  - 截止日期、紧急程度（SLA）及逾期工作；
  - 按用户和组查看团队工作量；
  - 为事件附加操作手册；
  - 系统健康页面（事件、失败作业、延迟作业、外部任务、批处理）；
  - 适配移动设备的布局；
  - 支持 13 种语言。
- **单点登录（OIDC）**：支持 Keycloak、Microsoft Entra ID 和其他 OpenID Connect 提供方；
  将声明映射到组和租户；REST API 支持 JWT Bearer 令牌。适用于 Run、
  Spring Boot、Tomcat 和 WildFly。
- **安全加固**：查询和筛选器中的表达式允许列表、引擎内部的附件和
  评论检查、初始设置、Cockpit 权限、开放重定向、Web
  应用响应头、Cookie 和 CSP。详情参见[变更日志](CHANGELOG.md)。
- **默认启用身份验证**：镜像要求 REST 请求提供凭据，并在
  所有模式下强制执行授权检查。
- **数据库模式版本保护**：当数据库模式版本低于 7.24 时，引擎拒绝启动，并且绝不会
  自行升级数据库模式。
- **Java4Shell 缓解措施**（[CVE-2026-114938](docs/security/CVE-2026-114938.md)）：amd64
  镜像内置已修复的 Temurin JRE，并关闭 Base64 内建优化；当引擎
  在受影响的 JVM 上运行时会发出警告。
- **性能**：新 Web UI 的首次加载大小从 960 KB 降至 330 KB（默认启用
  gzip）；经过调优，作业执行器的吞吐量增至原来的约 2.5 倍（数据和测试环境见
  [distro/docker/README.md](distro/docker/README.md#performance-tuning)）。
- **现代平台**：Java 17 或更高版本、Spring Boot 4.1、GraalJS 25、Jackson 2.21。

<a id="installation"></a>

## 安装

<a id="one-command-on-your-existing-camunda-724-database"></a>

### 一条命令，接入现有的 Camunda 7.24 数据库

镜像接受原 Camunda 7 Run 镜像的 `DB_*` 变量。请先备份数据库，
然后执行：

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

用户、密码、组、租户、授权、筛选器、部署、运行中的实例、
历史记录、作业和事件均保持原样。端口仅绑定到本机；如需远程
访问，请通过启用 HTTPS 的反向代理。在 `/fasti/` 使用现有账户登录。
由于身份验证已启用，每个 REST 客户端（外部任务工作器、集成系统）都必须发送
Basic 身份验证凭据或 Bearer 令牌。

- 与 Camunda 7.24 CE 节点在同一数据库上**并行运行**：使用相同的历史记录级别、相同的
  `TZ` 和相同的密码设置。如果旧节点是包含 Java 类的流程应用，而
  Fasti 中没有这些类，请在 Fasti 上设置 `FASTI_JOB_EXECUTOR_ENABLED=false`（或将所有节点
  配置为感知部署），并且不要通过 Fasti 启动这些流程或完成其任务：
  同步调用会在接收请求的节点上运行委托类。
- **回滚**：Fasti 不修改数据库模式，因此只需停止 Fasti，然后在同一数据库上重新启动 Camunda 7.24 节点。
  使用 Fasti 专有功能的已部署流程无法在
  Camunda 7 上运行；参见[回滚到 Camunda 7.24](docs/migrate-from-camunda-7.zh-CN.md#rollback-to-camunda-724)。
- **较旧的数据库模式（7.17–7.23）**：先运行镜像附带的 `sql/upgrade` 脚本
  （`/fasti/configuration/sql/upgrade`），升级至 7.24；例如从 7.23 → 7.24：
  `postgres_engine_7.23_to_7.24.sql`。

已使用镜像完成测试（接管由 Camunda Run 7.24.0 创建的数据库、回滚、
并行运行）的数据库：**PostgreSQL 16、MariaDB 11.8、MySQL 8.4、SQL Server 2022、Oracle
Database 23**（未内置 Oracle 驱动；需自行添加 `ojdbc11`；未在 MySQL
上测试回滚）。**DB2 尚未
验证。** H2 仅供评估使用。各项测试结果及限制：
[迁移指南](docs/migrate-from-camunda-7.zh-CN.md#limits)。

详细指南：

- [使用 Docker 安装](docs/install-docker.zh-CN.md)：H2、通过 `docker run` 和
  `docker compose` 使用 PostgreSQL、现有数据库、并行运行模式、健康检查、升级、回滚、
  故障排查。
- [从 Camunda 7 迁移](docs/migrate-from-camunda-7.zh-CN.md)：迁移步骤、相同之处与
  差异、回滚、限制。
- [镜像环境变量参考](distro/docker/README.md#environment-variables)。

<a id="without-docker-tomcat-bundle-download-from-fastidev"></a>

### 不使用 Docker：Tomcat 捆绑包（从 fasti.dev 下载）

预构建的 Tomcat 1.0.0 捆绑包（下载页面：<https://fasti.dev/download>）：
- [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip)（[SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256)）
- [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz)（[SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256)）

解压前请验证校验和，例如执行 `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`。

Tomcat 发行包也可以从源代码构建（`distro/tomcat`）。启动前请阅读
捆绑包中的 `README.txt`：REST API（`/engine-rest`）默认要求 HTTP Basic 身份验证，
不会自动创建用户（请在 `/fasti/` 的设置页面创建首个管理员），
接管 Camunda Run 数据库时，请在
`conf/bpm-platform.xml` 中设置 `jobExecutorDeploymentAware=false`，否则现有部署中的定时器和异步作业将无法运行。

<a id="a-ui-built-from-real-user-complaints"></a>

## 根据用户真实反馈打造的 UI

新 Web UI 的功能选自 Camunda 7 用户在
Camunda 论坛和 GitHub 上公开提出的需求，例如：

| 用户需求 | 来源 | Fasti 中的实现 |
|---|---|---|
| 按多个变量搜索实例，支持 OR | 论坛 [39269](https://forum.camunda.io/t/39269)、[45695](https://forum.camunda.io/t/45695)、[48976](https://forum.camunda.io/t/48976)、[64208](https://forum.camunda.io/t/64208)、[28855](https://forum.camunda.io/t/28855)、[4678](https://forum.camunda.io/t/4678)；[camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | 多变量条件构建器，支持 AND/OR |
| 查看业务个案每个步骤的评论 | 论坛 [30260](https://forum.camunda.io/t/30260)、[52109](https://forum.camunda.io/t/52109)、[2173](https://forum.camunda.io/t/2173) | 任务中的业务个案时间线；直接针对流程实例发表评论 |
| 按变量对任务排序，最早的优先 | 论坛 [17599](https://forum.camunda.io/t/17599)、[42078](https://forum.camunda.io/t/42078)、[41193](https://forum.camunda.io/t/41193)、[67328](https://forum.camunda.io/t/67328) | 按变量、截止日期和紧急程度排序 |
| 委派、缺勤期间的替代人 | 论坛 [2788](https://forum.camunda.io/t/2788)、[38199](https://forum.camunda.io/t/38199)、[9331](https://forum.camunda.io/t/9331) | 已支持单个任务的委派；离岗替代人已列入[路线图](#roadmap-plan) |
| 无表单启动流程时填写业务键和变量 | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | 支持 |
| 下载已部署的 BPMN XML | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | 支持 |
| 一致的标签、多语言支持 | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 种语言 |

<a id="comparison"></a>

## 对比

以下是根据各项目公开文档整理的简要对比（于 2026 年 11 月查阅）；
“未验证”表示未找到可靠来源。每个项目都有自己的发展方向，
适用于不同需求；请在做出决定前查阅其文档。

| | 许可证 | 可在现有 C7 数据库上运行 | 是否需要迁移 | 安全补丁 | Java / Spring Boot | UI | SSO | Docker 镜像 |
|---|---|---|---|---|---|---|---|---|
| **Fasti BPM** | Apache-2.0 | 是，7.24 数据库模式保持不变 | 镜像：无需迁移。嵌入式应用：新的 Maven 坐标、Spring Boot 4.1 和 Java 17+；保留 `org.camunda.*` 包 | 是，参见[下方政策](#commitments-support-and-security-patches) | 17+ / 4.1 | 全新 UI（React）+ 经典 UI（已弃用） | 内置 OIDC | 是，从 1.0.0 开始 |
| Camunda 7 CE 7.24 | 源代码为 Apache-2.0；部分捆绑的 Web 组件采用 MIT（[13]） | （原版） | — | 7.24 之后不再发布 CE 版本（[1]） | 17、21、25（[2]）/ 3.5 | Cockpit、Tasklist、Admin（AngularJS） | 通过安全 starter 支持 OAuth2 | 原 Run 镜像 |
| Camunda 7 Enterprise | 商业许可证 | 是（同一产品线） | 否 | 至 2030 年 4 月，可付费延长至 2032 年 4 月（[3]） | 未验证 | Cockpit Enterprise | 未验证 | 未验证 |
| Camunda 8 | Camunda License 1.0；自 8.6 起，自托管生产环境需要许可证（[4]） | 否（不同的引擎） | 是（模型、实例、数据） | 是（厂商提供） | 引擎不同，无法直接比较 | Operate、Tasklist | 未验证 | 未验证 |
| Operaton | Apache-2.0 | 表结构与 7.24 相同；2.2 通过升级脚本将数据库模式版本记录为 7.25（[5]） | 包重命名为 `org.operaton.*`（提供 OpenRewrite 配方）；数据库模式版本脚本（2.2） | 是，2.2.x 系列（[6]） | 17+ / 4.1（[5]） | 经典 UI + webapps-neo（预览版） | 未验证 | 是（[7]） |
| CIB seven | Apache-2.0；EE 需要许可证密钥 | 是（已合并 7.24 的变更） | 包重命名为 `org.cibseven.*`，前缀为 `cibseven.*` | 是，随各版本发布（[8]） | 17、21 / 3.5 和 4.0（[9]） | Vue 3 Web 客户端 | REST 支持 OAuth2/JWT（[8]） | 是（[8]） |
| OrqueIO | Apache-2.0 | 据其文档说明，与 C7 表兼容（[10]） | Maven group 为 `io.orqueio`；未说明是否重命名包 | 是，据其文档说明（[10]） | 21 / 3.5、4.0、4.1 | 使用 Angular 重写 | 支持多个提供方的 OIDC | 未验证 |
| EximeeBPMS | Apache-2.0（Monitor：MIT） | 该项目声明其数据库模式保持与 Camunda 7 兼容（未经 Fasti 验证）（[14]） | 包重命名为 `org.eximeebpms.*`，Model API 重命名 | 是，支持最近两个次要版本（[11]） | 21 / 未说明 Spring Boot 版本 | 独立的 Monitor | 未验证 | 是（`eximeebpms-docker` 仓库） |
| Fluxnova | Apache-2.0（FINOS） | 未验证 | 未验证 | 未验证 | 21（Corretto 镜像）/ 未验证 | Control Center | 未验证 | 是（[12]） |

Fasti 自身的不足也应明确说明：项目尚处于早期阶段，团队规模较小，暂不提供商业
支持，DB2 尚未验证。

来源：[1] [CE 生命周期终止公告](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921)；
[2] [7.24 支持的环境](https://docs.camunda.org/manual/7.24/introduction/supported-environments/)；
[3] [Enterprise 支持延期](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/)；
[4] [Camunda 8.6 许可说明](https://camunda.com/blog/2024/10/camunda-licensing-what-you-need-to-know/)；
[5] [Operaton 2.2](https://docs.operaton.org/docs/documentation/reference/release-notes/2_2/)；
[6] [Operaton SECURITY.md](https://github.com/operaton/operaton/blob/main/SECURITY.md)；
[7] [operaton-docker](https://github.com/operaton/operaton-docker)；
[8] [CIB seven 2.2.0](https://docs.cibseven.org/manual/latest/release-notes/release-notes-2.2.0-ce/)；
[9] [CIB seven 支持的环境](https://docs.cibseven.org/manual/latest/introduction/supported-environments/)；
[10] [OrqueIO 文档](https://docs.orqueio.io/)；
[11] [EximeeBPMS SECURITY.md](https://github.com/EximeeBPMS/eximeebpms/blob/main/SECURITY.md)；
[12] [Fluxnova](https://github.com/finos/fluxnova-bpm-platform)；
[13] [Camunda 7 许可证](https://docs.camunda.org/manual/latest/introduction/licenses/)；
[14] [EximeeBPMS](https://github.com/EximeeBPMS/eximeebpms)；
独立综述：[WZ-IT，2026-10-15](https://wz-it.com/en/blog/camunda-7-end-of-life-operaton-cibseven-fluxnova-compared/)。

<a id="timeline"></a>

## 时间线

<a id="camunda-7-milestones"></a>

### Camunda 7 里程碑

| 日期 | 里程碑 | 来源 |
|---|---|---|
| 2024-03-06 | Camunda 宣布 Camunda 7 CE 将于 2025 年 10 月结束生命周期，7.24 为最后一个 CE 版本 | [Camunda 论坛](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921) |
| 2025-02-28 | Camunda 将 Camunda 7 Enterprise 支持延长至 2030-04-09，并提供至 2032 年 4 月的付费延长支持 | [Camunda 博客](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) |
| 2025-10-07 | 创建 `7.24.0` 标签并发布至 Maven Central：最后一个 CE 版本 | [标签 7.24.0](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0)、[Maven Central](https://repo1.maven.org/maven2/org/camunda/bpm/camunda-engine/7.24.0/) |
| 2025-10-14 | Camunda 计划的 7.24 发布日期；此后不再发布任何 CE 版本，包括安全补丁。CE 仓库此后已归档 | [公告](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921)、[支持页面](https://docs.camunda.org/enterprise/announcement) |
| 2030 年 4 月 | Camunda 7 Enterprise 支持结束（7.24 LTS：2030-04-13） | [支持页面](https://docs.camunda.org/enterprise/announcement) |
| 2032 年 4 月 | Camunda 7 Enterprise 付费延长支持结束 | [支持页面](https://docs.camunda.org/enterprise/announcement) |

<a id="fasti-bpm"></a>

### Fasti BPM

Fasti 直接从 `7.24.0` 标签继续开发；所有修复和变更均以该点
为起点统计（`git log 7.24.0..`）。以下阶段按已完成工作的顺序排列，不标注日期：

1. **基础平台**：Java 17、Spring Boot 4.1、依赖库升级；Maven 坐标为
   `dev.fasti.platform`。
2. **安全**：修复 7.x 系列中已公开披露的问题、表达式允许列表、
   引擎内部的附件/评论检查、Web 应用加固。
3. **全新的 Web UI**，位于 `/fasti/`，逐步替代经典 Cockpit、Tasklist 和 Admin。
4. 为 Run、Spring Boot、Tomcat 和 WildFly 提供**单点登录**（OIDC）。
5. **直接接入现有数据库**：镜像接受原有的 `DB_*` 变量，并检查数据库模式版本；已在五种数据库系统上完成测试。
6. **发布前安全审查**（代码审查、镜像渗透测试、ZAP 基线
   扫描；发现的问题均已修复）以及 **Java4Shell 缓解措施**。
7. **1.0.0**：首个版本（2026-11-20）。

<a id="roadmap-plan"></a>

## 路线图（计划）

> 这是**计划**，并非对日期的承诺。范围和顺序可能调整。

### 2026 年 11 月和 12 月（进行中）

首先，完成用户已经提出的所有需求。

**1.x：更易运维**

- 在 Maven Central 发布 Maven 构件。
- Helm chart 和 Kubernetes 示例清单。
- Prometheus 指标和 OpenTelemetry 链路追踪。
- 结构化 JSON 日志。
- 升级 MyBatis，同时更新 OGNL。
- DB2 验证，以及由母语人士审校译文。

**2.0：受监管行业团队的需求**

- 缺勤期间的替代人，避免工作因有人休假而停滞（[论坛 2788](https://forum.camunda.io/t/2788)、[38199](https://forum.camunda.io/t/38199)）。
- 审批的双人复核（四眼原则）。
- 不可篡改的审计跟踪，经签名或哈希处理，并提供审计报告。
- 在新界面中支持 CMMN，并淘汰 AngularJS 应用。

**新领域**

- 长期支持版本系列。
- 面向引擎的 MCP 服务器。
- 流程中的 AI 步骤。

### 2027 年目标

- **2027 年第一季度：大规模运行**：用于滚动升级和扩缩容的 Kubernetes Operator；将历史记录流式传输到 Kafka 和 OpenSearch，使运行时数据库保持精简；发布性能基准测试结果，并在每个版本中重复测试。
- **2027 年第二季度：面向开发者的工具包**：连接器 SDK，附带 REST、Kafka、电子邮件和对象存储连接器；包含场景测试和覆盖率报告的流程测试套件；带可复用组件的表单构建器。
- **2027 年第三季度：洞察流程表现**：流程分析（热力图、瓶颈和耗时报告）；带告警的服务级别目标；可根据文本起草 BPMN 并解释事件的助手。
- **2027 年第四季度：企业级可信**：多租户管理，可为每个租户设置配额；独立的安全审计，并公开审计报告；可重现构建，每个版本均附带签名的来源证明。

<a id="commitments-support-and-security-patches"></a>

## 承诺：支持和安全补丁

> 本政策由 Fasti BPM 项目团队维护，可能会更新；以本
> 仓库中的版本为准。

- **受支持的版本系列：**`1.x` 系列的最新次要版本会获得错误修复和安全
  补丁（以补丁版本发布，例如 `1.0.1`）。
- **安全补丁：**Fasti 及其附带依赖库中的漏洞优先于
  其他工作处理；基础镜像或 JRE 获得修复时，会重新构建镜像。项目会关注
  Camunda 7 及其分支项目的公开安全公告，并检查 Fasti 是否受到影响。
- **兼容性：**`1.x` 系列保留 7.24 数据库模式和 `org.camunda.bpm.*`
  包。破坏性变更会在[变更日志](CHANGELOG.md)中标记为 **BREAKING**。
- **支持级别：**这是一个社区项目；我们会尽力提供支持，但**不提供 SLA**，
  暂无商业支持。
- **报告漏洞：**请**不要**创建公开 issue；参见
  [SECURITY.md](SECURITY.md#security-policy)（通过 GitHub 私密报告）。
- **已修复的缺陷：**[变更日志](CHANGELOG.md)中的*修复*和*安全*部分列出了
  相较于 Camunda 7 CE 7.24.0 修复的问题；安全公告列于
  [SECURITY.md](SECURITY.md#advisories)。

<a id="documentation"></a>

## 文档

| 文档 | 内容 |
|---|---|
| [docs/install-docker.md](docs/install-docker.zh-CN.md) | 使用 Docker 安装的分步指南 |
| [docs/api/README.md](docs/api/README.md) | REST API 集成、Java 和 JavaScript 外部任务客户端 |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.zh-CN.md) | 从 Camunda 7 迁移、回滚、限制 |
| [distro/docker/README.md](distro/docker/README.md) | 镜像：环境变量、驱动、运维、调优 |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | 全新的 Web UI |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell 与 Fasti |
| [CHANGELOG.md](CHANGELOG.md)、[SECURITY.md](SECURITY.md#security-policy)、[CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) | 变更、安全、贡献指南 |

<a id="what-this-is"></a>

## 项目介绍

本仓库包含 BPMN 2.0 / DMN 流程引擎、REST API、Web
应用（Cockpit、Tasklist、Admin）和发行包（Run、Tomcat、WildFly）。它
以 Camunda 7 CE 的 `7.24.0` 标签为起点，持续修复错误并升级
依赖库。

目标是与运行在 Camunda 7 上的应用保持**向后兼容**：

- 保留 `org.camunda.bpm.*` 包、BPMN 命名空间
  `http://camunda.org/schema/1.0/bpmn` 和 `camunda.bpm.*` 配置前缀；
- 保留 7.24 数据库模式；
- 保留所有部署形式：Spring Boot、Run、Tomcat、WildFly；`javax` 和 `jakarta`。

<a id="what-is-different-from-camunda-7240-ce"></a>

## 与 Camunda 7.24.0 CE 的差异

- **安全：**修复 7.x 系列中已公开披露的问题（查询中的
  表达式、初始设置、附件和评论访问、Cockpit 权限、开放
  重定向），并增加额外防护：查询和
  筛选器中的表达式允许列表、引擎内部的附件和评论检查、Web 应用中经过加固的安全过滤器、
  响应头和 Cookie。
- **稳定性：**修复使用
  JavaScript（GraalJS）执行脚本和求值 DMN 表达式时的内存泄漏，以及其他多项引擎问题。
- **平台：**Java 17 或更高版本、Spring Boot 4.1、GraalJS 25、Jackson 2.21，以及许多其他
  已升级至包含安全修复版本的依赖库。

部分修复调整了默认配置，使其行为更加安全；升级前请阅读
[迁移指南](docs/migrate-from-camunda-7.zh-CN.md#what-differs)中的“差异”部分。

<a id="versioning"></a>

## 版本规则

Fasti BPM Platform 遵循[语义化版本](https://semver.org/)，从 `1.0.0` 开始。
`1.x` 系列与为 Camunda 7.24 编写的应用兼容。

<a id="maven-coordinates"></a>

## Maven 坐标

Fasti BPM Platform 的构件使用独立的 Maven 坐标。**Java 包
仍为 `org.camunda.bpm.*`**，因此应用代码无需修改导入语句；`camunda.bpm.*`
配置前缀、BPMN/DMN 命名空间、数据库模式和
WildFly 上的模块名称也保持不变。对于 Maven 而言，只需更改依赖声明
（Web 应用路径另有变更，参见
[Web 应用路径](#web-application-path-fasti)）：

- **groupId：**`org.camunda.bpm` → `dev.fasti.platform`，`org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`；组 `org.camunda.commons`、`org.camunda.connect`、
  `org.camunda.spin` 和 `org.camunda.template-engines` → `dev.fasti.platform.<group name>`。
- **artifactId：**前缀 `camunda-` 改为 `fasti-`，其余部分保持不变。
- **version：**使用 Fasti BPM Platform 的版本（参见[版本规则](#versioning)），替代
  `7.24.0`。

这些构件尚未向 Maven Central 发布任何版本。在发布之前，请从
源代码构建，并将构件放入项目使用的 Maven 仓库。

使用 BOM 管理版本：

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

常用构件：

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

FEEL 库 `org.camunda.feel:feel-engine` 是外部依赖库，不属于
本仓库；其坐标保持不变。

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

### 仍会引入原版引擎的第三方库

由于包名未变，原版引擎（`org.camunda.bpm:camunda-engine`）
和 `fasti-engine` 包含同名类。Maven 将它们视为两个互不相关的
库，不会自动移除其中任何一个。如果某个第三方库（例如社区扩展）
依赖原版构件，请排除这些构件，改为声明对应的
Fasti 构件：

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

`mvn dependency:tree -Dincludes="org.camunda*"` 可列出仍然
存在的原版构件（`org.camunda.feel:feel-engine` 是正常依赖，请勿排除）。

<a id="startup-check"></a>

### 启动检查

引擎在创建时会检查类路径，如果发现
多个库中存在流程引擎类，就会**拒绝启动**；错误 `ENGINE-12021` 会列出
每个库的位置。Spring Boot starter 会在
应用启动时立即执行相同检查。仅通过父类加载器可见的库（例如服务器的共享
引擎与 Web 应用中嵌入的引擎并存）不计入
检查。如果这是有意采用的配置，可以通过系统属性关闭检查：

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

限制：如果原版引擎库在类路径中排在 `fasti-engine` 前面，
引擎将由原版库中的类构建，而这些类不包含此检查。
这种情况下，只有使用 Fasti Spring Boot starter 的应用会被阻止启动；对于
其他集成方式，请按上述说明检查依赖。

<a id="web-application-path-fasti"></a>

## Web 应用路径：`/fasti`

所有发行包均在 `/fasti`（原为 `/camunda`）下提供 Cockpit、Tasklist、Admin 和新的 Web UI：
`http://localhost:8080/fasti/` 打开新 UI
（`/fasti/app/next/default/`），经典应用位于 `/fasti/app/cockpit/...`、
`/fasti/app/tasklist/...` 和 `/fasti/app/admin/...`。REST API 仍位于 `/engine-rest`。

**经典应用已弃用。** 经典 Cockpit、Tasklist、Admin 和 Welcome
基于 AngularJS 1.8.2 和 Bootstrap 3，两者均已结束生命周期。**容器镜像默认不再
提供这些应用**（破坏性变更）：其页面和旧书签会重定向到新 UI 中对应的部分，对其文件的请求返回 `404`；设置 `FASTI_CLASSIC_WEBAPPS=true` 可重新
启用（会显示警告），参见 [distro/docker/README.md](distro/docker/README.md)。
Tomcat 和 WildFly 捆绑包默认也不再提供这些应用（可通过
`-Dfasti.webapp.classic.enabled=true` 或 `FASTI_CLASSIC_WEBAPPS=true` 重新启用）。Run zip 包和 Spring
Boot starter 仍像以前一样提供这些应用，以免影响现有安装；可通过
`fasti.webapp.classic.enabled: false` 关闭。经典应用将在
后续版本中移除。

旧路径仍可使用：对 `/camunda/...` 的所有请求都会重定向到
`/fasti/...` 下的相同路径，其余路径和查询字符串保持不变。GET 和 HEAD 返回
`301`；所有其他方法（POST、PUT、DELETE，例如旧客户端调用 `/camunda/api/...`）
返回 `308`，使客户端使用相同的方法和请求体重新发送请求。重定向响应
包含 `Cache-Control: no-store`，因此切换回 `/camunda` 后会立即生效。

如需恢复 `/camunda`：

- **Spring Boot、Run：**`camunda.bpm.webapp.application-path: /camunda`（此时不再重定向）。
  如仅需关闭重定向：`fasti.webapp.legacy-path-redirect: false`。
- **Tomcat：**删除 `webapps/camunda`（重定向应用），并将
  `webapps/fasti` 重命名为 `webapps/camunda`。
- **WildFly：**删除 `standalone/deployments/camunda.war`（以及 `camunda.war.dodeploy`），并
  在 Web 应用 WAR 的 `WEB-INF/jboss-web.xml` 中设置
  `<context-root>/camunda</context-root>`。

**反向代理：**如果代理仅转发 `/camunda/`（或针对 `/camunda` 重写路径、设置 Cookie
路径、CSP 或健康检查），请添加 `/fasti/`，或完全迁移至 `/fasti/`。
`XSRF-TOKEN` Cookie（以及 Tomcat 和 WildFly 上的 `JSESSIONID`）的路径现在为 `/fasti`，
因此用户需要重新登录一次。书签和旧链接仍可通过重定向继续使用。

<a id="single-sign-on-openid-connect"></a>

## 单点登录（OpenID Connect）

支持 **Run**（`./start.sh --oauth2`，模块 `internal/oauth2`）以及使用
`fasti-bpm-spring-boot-starter-security` 的 **Spring Boot**
应用。通过声明 Spring Security 客户端注册（`spring.security.oauth2.client.*`）启用。
**Tomcat** 和 **WildFly** 的说明见本节末尾。

- **登录：**新 Web 应用的登录页面显示“使用 &lt;provider&gt; 登录”，
  替代密码表单（标签：`fasti.security.oidc.login-label`，默认为
  `client-name`；存在多个注册时，通过
  `fasti.security.oidc.registration-id` 选择一个）。授权码流程使用 `state`、`nonce`
  和 PKCE（S256，也适用于机密客户端）；登录时会更换会话 ID。用户
  会返回新 Web 应用中最初访问的页面（仅接受
  `/fasti/app/next/` 下的路径，因此不存在开放重定向）。
- **注销：**`POST /logout`（仅限同源请求）会结束会话；设置
  `camunda.bpm.oauth2.sso-logout.enabled=true` 后，用户会继续访问提供方的会话
  结束端点（RP 发起的注销，`post-logout-redirect-uri`）。Spring Security 的
  后通道注销保持启用。`GET /logout` 不再执行用户注销。
- **会话过期：**当访问令牌无法刷新或会话超时时，
  对 `/fasti/api/**` 的请求返回 `401`（不重定向），新 Web 应用会显示登录页面。
- **CSRF：**`/fasti/api` 和 `/fasti/app` 保留 Web 应用自身的 CSRF 过滤器
  （`X-XSRF-TOKEN`）；与上游一致，Spring Security 的 CSRF 防护保持禁用。
- **组、管理员、租户：**组声明通过
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` 指定（沿用上游属性名；支持
  `realm_access.roles` 等嵌套声明）。`fasti.security.oidc.groups.admin` 列出
  可授予 `camunda-admin` 的声明值；`groups.mapping` 将声明值映射到引擎组
  ID；`groups.include-unmapped=false` 会丢弃未映射的值。**与上游的差异：**
  声明值 `camunda-admin` 本身不再自动授予管理员权限，
  `SCOPE_openid` 或 `OIDC_USER` 等权限项也不再转换为组。
  `fasti.security.oidc.tenants.claim` 用于映射租户。姓名和电子邮件：
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`。
- **身份提供方：**默认情况下，引擎从
  当前会话的令牌中读取用户、组和租户（只读）。设置
  `camunda.bpm.oauth2.identity-provider.enabled=false` 可改用数据库（此时组
  来自成员关系表）。尚未实现自动预配。
  当用户来自令牌时（默认行为），初始设置页面（首个管理员）
  会关闭，因为管理员来自声明（`groups.admin`）；此前，不属于管理员组的用户会被转到设置页面。
- **使用 Bearer 令牌的 REST API：**设置 `fasti.security.oidc.rest.bearer-enabled=true` 后，
  `/engine-rest` 除 Basic 身份验证外，还接受 `Authorization: Bearer <JWT>`。
  必须配置 `rest.issuer-uri` 和 `rest.audiences`；签名仅使用非对称算法，
  根据颁发者的 JWKS 进行校验（`rest.jws-algorithms`，默认为 `RS256`），并
  验证 `iss`、`aud`、`exp`、`nbf`。Run 还需设置 `camunda.bpm.run.auth.enabled=true`。
  浏览器会话 Cookie 绝不会用于 `/engine-rest` 的身份验证。

Keycloak 示例（Run 的 `configuration/default.yml`）：

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

Microsoft Entra ID 示例：

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

**Tomcat、WildFly（WAR）：**提供相同功能，通过 `fasti-oidc.properties` 配置（Tomcat：
`conf/`，WildFly：`standalone/configuration/`；同目录下提供带注释的 `fasti-oidc.properties.example`
文件）。没有该文件时，行为保持不变。配置键与 Run 的 `fasti.security.oidc.*` 属性一致，另有 `issuer-uri`、`client-id`、`client-secret`、`user-name-claim`、`groups-claim`。
提供方必须允许重定向 URI `<base>/fasti/sso/callback`；后通道注销 URL 为
`<base>/fasti/sso/backchannel-logout`。通过
`fasti.security.oidc.rest.bearer-enabled=true` 启用 `/engine-rest` 的 Bearer 令牌支持（除非设置
`rest.basic-enabled=false`，否则 Basic 身份验证仍然有效；引擎列表 `/engine` 也需要凭据）。用户无需
存在于数据库中。若要让引擎根据声明响应有关
已登录用户所属组和租户的查询，请启用 `OidcIdentityProviderPlugin`（在
`bpm-platform.xml` / `standalone.xml` 中默认被注释）。限制：用于后通道注销跟踪的会话
保存在各服务器的内存中（不在集群节点间共享）；经典应用仅重定向到提供方；完整注销需要使用新的 Web UI。

<a id="build"></a>

## 构建

需要 JDK 17 或更高版本（推荐 21）。

```bash
./mvnw clean verify
```

完整构建会运行所有 Java 测试和 Web UI 单元测试（接近一小时；Playwright
端到端测试单独运行，参见 [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)），并且需要通过网络访问 Maven Central、
nodejs.org 和 npm registry。参见 [TESTING.md](TESTING.md)。

<a id="acknowledgements"></a>

## 致谢

- Fasti BPM Platform 建立在 Camunda 和 Camunda 7
  社区十余年的工作成果之上。
- 在审查缺陷时，项目参考了其他源自 Camunda 7 的社区项目的公开信息（issue、安全公告、
  发布说明），包括 Operaton、CIB
  seven、OrqueIO 和 EximeeBPMS。本仓库中的源代码
  基于 Camunda 7 CE 独立编写。

<a id="license"></a>

## 许可证

[Apache License 2.0](LICENSE)。原始代码的版权信息请参见 [NOTICE](NOTICE)。
