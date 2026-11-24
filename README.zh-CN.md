[English](README.md) | [Tiếng Việt](README.vi.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | 简体中文

> 本译文由社区维护，可能落后于英文版；以英文版为准。欢迎指正。

# Fasti BPM Platform

Fasti BPM 是一个独立的开源引擎，基于 Camunda 7 Community Edition 7.24.0 构建。它直接运行在你现有的
Camunda 7 数据库上，并提供更新的依赖库、安全修复和全新的 Web 界面。网站：<https://fasti.dev>

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
  fastidev/fasti-bpm:1.2.1
```

打开 <http://localhost:8080/fasti/> 并登录。未设置 `DB_URL` 时，镜像使用容器内的 H2 数据库，
仅供评估使用。REST API 需要 Basic 身份验证凭据：

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

完整指南（包括使用你自己的数据库）：<https://fasti.dev/install>

<a id="highlights"></a>

## 主要特性

- **改一下连接，迁移就完成了。** 原样使用 Camunda 7.24 的 schema，并保留 `org.camunda.bpm.*` 包
  （[迁移](https://fasti.dev/install#migrate)）。
- **一个容器，一条命令。** 使用与 Camunda Run 相同的 `DB_*` 变量（[安装](https://fasti.dev/install)）。
- **为处理任务队列的人而打造**：全新的 Web 界面，支持 13 种语言、浅色和深色主题，在手机上也能使用
  （[功能](https://fasti.dev/#features)）。
- **在浏览器中建模和部署**：BPMN、DMN 和表单（[建模器](https://fasti.dev/#modeler)）。
- **运维所需的工具，开箱即有**：历史、实例修复、批量重试、操作日志
  （[运维](https://fasti.dev/#operate)）。
- **容器镜像默认关闭**：首次启动即要求登录，无默认用户，拒绝关闭 REST 身份验证或授权的设置，
  Java4Shell 已缓解。Tomcat 和 WildFly 的 REST API 要求凭据，且不创建任何用户（第一个管理员在设置页面上
  创建）；Run zip 和 Spring Boot starter 保留 Camunda 7 的默认设置。所有发行版都包含安全修复，并会在启动时
  对受 Java4Shell 影响的 JVM 发出警告（[安全](https://fasti.dev/security)）。
- **单点登录**：OpenID Connect 和 JWT bearer token（[指南](docs/single-sign-on.zh-CN.md)）。
- **现代平台**：Java 17 或更高版本、Spring Boot 4.1、GraalJS 25、Jackson 2.21
  （[性能](https://fasti.dev/#performance)）。

<a id="installation"></a><a id="get-fasti"></a>

## 获取 Fasti

- <a id="one-command-on-your-existing-camunda-724-database"></a>**Docker 镜像**（`fastidev/fasti-bpm`，支持 amd64 和 arm64）：<https://fasti.dev/install>；分步说明见
  [docs/install-docker.zh-CN.md](docs/install-docker.zh-CN.md)，全部变量见
  [distro/docker/README.md](distro/docker/README.md#environment-variables)。
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**Tomcat 捆绑包**（不使用 Docker）：<https://fasti.dev/download>。预构建的 Tomcat 1.2.1 捆绑包：
  - [fasti-bpm-tomcat-1.2.1.zip](https://assets.fasti.dev/releases/1.2.1/fasti-bpm-tomcat-1.2.1.zip)（[SHA-256](https://assets.fasti.dev/releases/1.2.1/fasti-bpm-tomcat-1.2.1.zip.sha256)）
  - [fasti-bpm-tomcat-1.2.1.tar.gz](https://assets.fasti.dev/releases/1.2.1/fasti-bpm-tomcat-1.2.1.tar.gz)（[SHA-256](https://assets.fasti.dev/releases/1.2.1/fasti-bpm-tomcat-1.2.1.tar.gz.sha256)）

  解压前请验证校验和，例如执行 `sha256sum -c fasti-bpm-tomcat-1.2.1.zip.sha256`。启动前请阅读
  捆绑包中的 `README.txt`。
- **嵌入式**，用于 Spring Boot 或其他 Java 应用：新的 Maven 坐标（`dev.fasti.platform:fasti-*`），
  相同的 `org.camunda.bpm.*` 包；参见 [docs/embedding.zh-CN.md](docs/embedding.zh-CN.md)。

<a id="moving-from-camunda-7"></a>

## 从 Camunda 7 迁移

如果你运行的是 Camunda 7.24，Fasti 可以在同一个数据库上运行，且不改动其 schema。你可以替换旧节点，
让 Fasti 与它们并行运行，也可以随时回退到 Camunda 7.24。Fasti 镜像已在 PostgreSQL 16、MariaDB 11.8、MySQL 8.4、SQL Server 2022 和 Oracle Database 23ai 上测试过接管由 Camunda Run 7.24 创建的数据库；回退到 Camunda Run 已在除 MySQL 外的相同数据库上测试。DB2 尚未测试。
每个 REST 客户端（包括外部任务 worker）现在都必须发送 Basic 凭据或 bearer token。

嵌入式 Spring Boot 应用还需迁移到 Spring Boot 4.1 和 Java 17；更早的 schema 需先用标准脚本升级。为 Camunda 7 构建的插件和流程应用必须基于 Fasti 制品重新构建，才能在 Fasti 内运行。并行运行和回退都有限制（所有节点配置相同、由哪个节点运行作业、使用 Fasti 独有功能的流程）：参见[混合运行](docs/migrate-from-camunda-7.zh-CN.md#mixed-operation-with-camunda-7-nodes)和[回退](docs/migrate-from-camunda-7.zh-CN.md#rollback-to-camunda-724)。

迁移步骤与限制：<https://fasti.dev/install#migrate> 以及
[迁移指南](docs/migrate-from-camunda-7.zh-CN.md)（回滚、旧版 schema、测试结果）。

<a id="comparison"></a><a id="how-it-compares"></a>

## 与其他项目的对比

有好几个不错的项目在延续 Camunda 7。Fasti 保留了 `org.camunda.bpm.*` 包和 7.24 的 schema，因此切换
只是更换连接，而不是一个迁移项目，同时还增加了新界面。其他项目也是可靠的选择。带来源的完整表格：
<https://fasti.dev/compare>。

<a id="commitments-support-and-security-patches"></a><a id="security-and-support"></a>

## 安全与支持

`1.x` 系列的最新次版本会以补丁版本（如 `1.0.1`）的形式获得缺陷修复和安全补丁。请私下报告安全问题，
不要提交到公开的 issue：参见 [SECURITY.md](SECURITY.md#security-policy)，其中也包含
[支持政策](SECURITY.md#support-policy)。修复列表、Java4Shell 和发布前审查：
<https://fasti.dev/security>。如需安装、迁移或生产运维方面的帮助，或商定支持条款，请发邮件至
<contact@fasti.dev>。

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## 路线图

这是一份计划，而不是对日期的承诺。已随 `1.2.0` 发布的内容（性能改进）：工作量统计接口、可选的数据库
索引、启动时的性能诊断、审计轨迹调优，以及更快的 Web 界面。已随 `1.1.0` 发布的内容：Helm chart 和
Kubernetes 清单、Prometheus 指标、OpenTelemetry 链路追踪、结构化 JSON 日志、缺勤期间的代理人、审批的
四眼原则控制、不可篡改的审计轨迹、面向 AI 助手的 MCP 服务器，以及新界面中的案例 (CMMN)。`1.x` 的后续
计划：将 Maven 构件发布到 Maven Central、在 DB2 上验证、由母语者审校译文，以及停用 AngularJS 应用。从
Camunda 7 CE 终止到 2027 年目标的时间线：
<https://fasti.dev/#timeline>。

<a id="documentation"></a>

## 文档

[fasti.dev](https://fasti.dev) 上的页面：[使用 Docker 安装](https://fasti.dev/install) ·
[Tomcat 版下载](https://fasti.dev/download) · [API 集成指南](https://fasti.dev/docs) ·
[安全与支持](https://fasti.dev/security) · [对比](https://fasti.dev/compare) ·
[博客](https://fasti.dev/blog)

<a id="what-this-is"></a><a id="what-is-different-from-camunda-7240-ce"></a><a id="versioning"></a><a id="maven-coordinates"></a><a id="third-party-libraries-that-still-pull-in-the-original-engine"></a><a id="startup-check"></a><a id="web-application-path-fasti"></a><a id="single-sign-on-openid-connect"></a><a id="a-ui-built-from-real-user-complaints"></a>

| 文档 | 内容 |
|---|---|
| [docs/install-docker.md](docs/install-docker.zh-CN.md) | 使用 Docker 安装的分步指南 |
| [docs/install-kubernetes.md](docs/install-kubernetes.md) | 在 Kubernetes 上安装：Helm chart 和原生清单（自 1.1.0 起） |
| [docs/migrate-from-camunda-7.md](docs/migrate-from-camunda-7.zh-CN.md) | 从 Camunda 7 迁移、回滚、限制 |
| [docs/embedding.md](docs/embedding.zh-CN.md) | 兼容性、与 7.24.0 的差异、版本规则、Maven 坐标、启动检查 |
| [docs/web-ui.md](docs/web-ui.zh-CN.md) | Web 界面：用户要求的功能、`/fasti` 路径、经典应用 |
| [docs/absence.md](docs/absence.md) | 缺勤期间的代理人（自 1.1.0 起） |
| [docs/four-eyes.md](docs/four-eyes.md) | 审批的四眼原则控制（自 1.1.0 起） |
| [docs/audit-trail.md](docs/audit-trail.md) | 不可篡改的审计轨迹（自 1.1.0 起） |
| [docs/single-sign-on.md](docs/single-sign-on.zh-CN.md) | 单点登录（OpenID Connect）：Run、Spring Boot、Tomcat、WildFly |
| [docs/api/README.md](docs/api/README.md) | REST API 集成、Java 和 JavaScript 外部任务客户端 |
| [distro/docker/README.md](distro/docker/README.md) | 镜像：环境变量、驱动、运维、调优 |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | 全新的 Web UI |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell 与 Fasti |
| [CHANGELOG.md](CHANGELOG.md)、[SECURITY.md](SECURITY.md#security-policy) | 变更、安全、支持政策 |

<a id="build"></a>

## 从源代码构建

需要 JDK 17 或更高版本（推荐 21），并且需要通过网络访问 Maven Central、nodejs.org 和 npm registry
（构建过程会为 Web UI 下载 Node/npm）。

```bash
./mvnw clean verify
```

完整构建会运行所有 Java 测试和 Web UI 单元测试（接近一小时；Playwright
端到端测试单独运行，参见 [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)）。
参见 [TESTING.md](TESTING.md)。构建容器镜像（首次构建大约需要 10–20 分钟）：

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## 参与贡献

欢迎在 GitHub 上提问、报告缺陷和提交 pull request；参见 [CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute)
和[行为准则](CODE_OF_CONDUCT.md)。特别欢迎母语使用者审阅译文。

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
