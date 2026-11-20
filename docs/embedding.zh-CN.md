[English](embedding.md) | [Tiếng Việt](embedding.vi.md) | [Deutsch](embedding.de.md) | [Español](embedding.es.md) | [Français](embedding.fr.md) | [हिन्दी](embedding.hi.md) | [Bahasa Indonesia](embedding.id.md) | [日本語](embedding.ja.md) | [한국어](embedding.ko.md) | [Português (Brasil)](embedding.pt-BR.md) | [Русский](embedding.ru.md) | [ไทย](embedding.th.md) | 简体中文

> 本译文由社区维护，可能落后于英文版；以英文版为准。欢迎指正。

# 嵌入 Fasti BPM：兼容性与 Maven 坐标

Fasti 从 Camunda 7 保留了什么、与 7.24.0 有何不同、版本如何编号，以及嵌入引擎的应用所用的 Maven 坐标。如需将 Fasti 作为服务器运行，请参阅[使用 Docker 安装](install-docker.zh-CN.md)或 <https://fasti.dev/download> 上的 Tomcat 捆绑包。

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
[迁移指南](migrate-from-camunda-7.zh-CN.md#what-differs)中的“差异”部分。

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
[Web 应用路径](web-ui.zh-CN.md#web-application-path-fasti)）：

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
