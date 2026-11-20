English | [Tiếng Việt](web-ui.vi.md) | [Deutsch](web-ui.de.md) | [Español](web-ui.es.md) | [Français](web-ui.fr.md) | [हिन्दी](web-ui.hi.md) | [Bahasa Indonesia](web-ui.id.md) | [日本語](web-ui.ja.md) | [한국어](web-ui.ko.md) | [Português (Brasil)](web-ui.pt-BR.md) | [Русский](web-ui.ru.md) | [ไทย](web-ui.th.md) | [简体中文](web-ui.zh-CN.md)

# The Fasti web interface

Why the new web interface has the features it has, and where the web applications are served. A tour of the interface: <https://fasti.dev/#features>.

<a id="a-ui-built-from-real-user-complaints"></a>

## A UI built from real user complaints

The features of the new web UI were chosen from what Camunda 7 users have written publicly on the
Camunda forum and on GitHub, for example:

| What users asked for | Source | In Fasti |
|---|---|---|
| Search instances by several variables, with OR | forum [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Multi-variable condition builder, AND/OR |
| See the comments at every step of a case | forum [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Case timeline in the task; comments on the process instance itself |
| Sort tasks by variables, oldest first | forum [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Sorting by variables, due dates and urgency |
| Delegation, substitutes during absence | forum [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Per-task delegation, and [substitutes during absence](absence.md) with handover |
| Business key and variables when starting a process without a form | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Yes |
| Download the deployed BPMN XML | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Yes |
| Consistent labels, several languages | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 languages; inside the BPMN, DMN and form editors the built-in labels are in English and Vietnamese only |

<a id="web-application-path-fasti"></a>

## Web application path: `/fasti`

Cockpit, Tasklist, Admin and the new web UI are served below `/fasti` (formerly `/camunda`)
by every distribution: `http://localhost:8080/fasti/` opens the new UI
(`/fasti/app/next/default/`), the classic applications live at `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` and `/fasti/app/admin/...`. The REST API stays at `/engine-rest`.

**The classic applications are deprecated.** The classic Cockpit, Tasklist, Admin and Welcome
are built on AngularJS 1.8.2 and Bootstrap 3, both end of life. **The container image does not
serve them by default** (breaking change): their pages and old bookmarks redirect to the matching section of the new UI, and requests for their files return `404`; `FASTI_CLASSIC_WEBAPPS=true` brings them
back (with a warning), see [distro/docker/README.md](../distro/docker/README.md). The
Tomcat and WildFly bundles do not serve them by default either (bring them back with
`-Dfasti.webapp.classic.enabled=true` or `FASTI_CLASSIC_WEBAPPS=true`). The Run zip and the Spring
Boot starter keep serving them as before, so that running installations do not break; turn them
off with `fasti.webapp.classic.enabled: false`. The classic applications will be removed in a
later release.

The old path keeps working: every request for `/camunda/...` is redirected to the same path
below `/fasti/...`, keeping the rest of the path and the query string. GET and HEAD get
`301`; all other methods (POST, PUT, DELETE, e.g. old clients calling `/camunda/api/...`)
get `308`, so that clients repeat the request with the same method and body. The redirects
carry `Cache-Control: no-store`, so switching back to `/camunda` takes effect at once.

To restore `/camunda`:

- **Spring Boot, Run:** `camunda.bpm.webapp.application-path: /camunda` (no redirect then).
  To turn off only the redirect: `fasti.webapp.legacy-path-redirect: false`.
- **Tomcat:** delete `webapps/camunda` (the redirect application) and rename
  `webapps/fasti` to `webapps/camunda`.
- **WildFly:** delete `standalone/deployments/camunda.war` (and `camunda.war.dodeploy`) and
  set `<context-root>/camunda</context-root>` in `WEB-INF/jboss-web.xml` of the web
  application WAR.

**Reverse proxies:** if a proxy forwards only `/camunda/` (or rewrites paths, sets cookie
paths, CSP or health checks for `/camunda`), add `/fasti/` or move to `/fasti/` entirely.
The `XSRF-TOKEN` cookie (and `JSESSIONID` on Tomcat and WildFly) now has the path `/fasti`,
so users sign in once more. Bookmarks and old links keep working through the redirect.
