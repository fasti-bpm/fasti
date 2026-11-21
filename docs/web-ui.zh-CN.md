[English](web-ui.md) | [Tiếng Việt](web-ui.vi.md) | [Deutsch](web-ui.de.md) | [Español](web-ui.es.md) | [Français](web-ui.fr.md) | [हिन्दी](web-ui.hi.md) | [Bahasa Indonesia](web-ui.id.md) | [日本語](web-ui.ja.md) | [한국어](web-ui.ko.md) | [Português (Brasil)](web-ui.pt-BR.md) | [Русский](web-ui.ru.md) | [ไทย](web-ui.th.md) | 简体中文

> 本译文由社区维护，可能落后于英文版；以英文版为准。欢迎指正。

# Fasti 的 Web 界面

新 Web 界面为何具备这些功能，以及 Web 应用在哪个路径下提供。界面导览：<https://fasti.dev/#features>。

<a id="a-ui-built-from-real-user-complaints"></a>

## 根据用户真实反馈打造的 UI

新 Web UI 的功能选自 Camunda 7 用户在
Camunda 论坛和 GitHub 上公开提出的需求，例如：

| 用户需求 | 来源 | Fasti 中的实现 |
|---|---|---|
| 按多个变量搜索实例，支持 OR | 论坛 [39269](https://forum.camunda.io/t/39269)、[45695](https://forum.camunda.io/t/45695)、[48976](https://forum.camunda.io/t/48976)、[64208](https://forum.camunda.io/t/64208)、[28855](https://forum.camunda.io/t/28855)、[4678](https://forum.camunda.io/t/4678)；[camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | 多变量条件构建器，支持 AND/OR |
| 查看业务个案每个步骤的评论 | 论坛 [30260](https://forum.camunda.io/t/30260)、[52109](https://forum.camunda.io/t/52109)、[2173](https://forum.camunda.io/t/2173) | 任务中的业务个案时间线；直接针对流程实例发表评论 |
| 按变量对任务排序，最早的优先 | 论坛 [17599](https://forum.camunda.io/t/17599)、[42078](https://forum.camunda.io/t/42078)、[41193](https://forum.camunda.io/t/41193)、[67328](https://forum.camunda.io/t/67328) | 按变量、截止日期和紧急程度排序 |
| 委派、缺勤期间的替代人 | 论坛 [2788](https://forum.camunda.io/t/2788)、[38199](https://forum.camunda.io/t/38199)、[9331](https://forum.camunda.io/t/9331) | 支持单个任务的委派，以及带交接的[缺勤代理人](absence.md)（自 1.1.0 起） |
| 无表单启动流程时填写业务键和变量 | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | 支持 |
| 下载已部署的 BPMN XML | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | 支持 |
| 一致的标签、多语言支持 | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 种语言；BPMN、DMN 和表单编辑器内置的标签仅有英语和越南语 |

<a id="cases-cmmn"></a>

## 案例 (CMMN)

使用 CMMN 1.1 建模并以 `.cmmn` 文件部署的案例可在新界面中监控和处理：Cockpit → 案例 (CMMN)
显示案例定义及其版本、案例图和未关闭的实例；案例实例显示每个计划项的状态，并提供手动转换
（启动、完成、禁用、重新启用、终止，不可撤销的操作会先确认），以及任务、变量和历史；
Cockpit → 案例历史列出未关闭和已关闭的案例实例。在 Tasklist 中，案例的任务会显示其案例、链接到
案例实例并绘制案例图。案例图由 Fasti 根据模型的 CMMNDI 自行绘制，不使用已停止维护的
cmmn-js 库（最后版本 0.20.0）。

<a id="web-application-path-fasti"></a>

## Web 应用路径：`/fasti`

所有发行包均在 `/fasti`（原为 `/camunda`）下提供 Cockpit、Tasklist、Admin 和新的 Web UI：
`http://localhost:8080/fasti/` 打开新 UI
（`/fasti/app/next/default/`），经典应用位于 `/fasti/app/cockpit/...`、
`/fasti/app/tasklist/...` 和 `/fasti/app/admin/...`。REST API 仍位于 `/engine-rest`。

**经典应用已弃用。** 经典 Cockpit、Tasklist、Admin 和 Welcome
基于 AngularJS 1.8.2 和 Bootstrap 3，两者均已结束生命周期。**容器镜像默认不再
提供这些应用**（破坏性变更）：其页面和旧书签会重定向到新 UI 中对应的部分，对其文件的请求返回 `404`；设置 `FASTI_CLASSIC_WEBAPPS=true` 可重新
启用（会显示警告），参见 [distro/docker/README.md](../distro/docker/README.md)。
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
