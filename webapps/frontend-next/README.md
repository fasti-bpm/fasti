English | [Tiếng Việt](README.vi.md)

# New web UI (frontend-next)

The new web UI of Fasti BPM, built with React, TypeScript and Vite. It contains the application
shell (login, navigation, language, light/dark theme), the welcome page (applications the user
may use, own profile, quick links), the Tasklist, the monitoring section (Cockpit) and the
administration (Admin). The classic applications (`webapps/frontend`) are untouched and run side
by side.

## How the application is served

- The build output is `target/webapp/app/next/`; it is packaged into the WAR, the Jakarta WAR,
  the webjar and Run next to the classic applications. URL: `{appRoot}/app/next/{engine}/`
  (e.g. `/fasti/app/next/default/`); `{appRoot}/app/next/` redirects to the default engine.
- `index.html` is not a static file: `ProcessEnginesFilter` reads it, replaces `$APP_ROOT` and
  `$CSP_NONCE` and sends the result. The `servletTemplate` plugin in `vite.config.ts` puts the
  placeholders into the built page. The page must be ASCII and free of Maven filter tokens.
- Everything else in `app/next/` is a static file. Client-side routing uses the `#` part of the
  URL, so the server needs no rule for it.
- The application reuses the existing session, cookies and REST API (`{appRoot}/api/...`).
  Modifying requests carry the `X-XSRF-TOKEN` header. The application sends requests to the
  origin of the page only. The one exception is the opt-in `separate-origin` forms mode (see
  [Embedded HTML forms](#embedded-html-forms)): the form frame is loaded from the configured
  forms origin, while its REST calls are still brokered by the page.
- The new web UI is the default entry point: `{appRoot}/`, `{appRoot}/app/` and the page after
  the login lead to `{appRoot}/app/next/{engine}/` (the welcome page). The classic applications
  stay deployed and reachable at their URLs (`/app/welcome/`, `/app/tasklist/`, `/app/cockpit/`,
  `/app/admin/`) for what the new UI does not cover yet (for example CMMN). They are deprecated
  (AngularJS 1.8.2 and Bootstrap 3 are end of life): the container image does not serve them
  (their pages lead to the matching section of the new UI, `FASTI_CLASSIC_WEBAPPS=true` brings
  them back); Run and the Spring Boot starter turn them off with
  `fasti.webapp.classic.enabled: false`. The server then announces this in
  `<meta name="fasti-classic-webapps" content="false">` and the new UI hides its links to the
  classic applications.
- To restore the classic entry point: Run and the Spring Boot starter read the property
  `fasti.webapp.default-app: welcome` (in `application.yml`, or the environment variable
  `FASTI_WEBAPP_DEFAULT_APP=welcome`); the WARs for Tomcat and WildFly read the init parameter
  `defaultApp` of the `Engines Filter` in `WEB-INF/web.xml` (present there as a comment). Allowed
  values: `next` (default), `welcome`, `tasklist`, `cockpit`, `admin`; any other value stops the
  application from starting, so that a typo does not silently change the entry point.
- Content security policy: pages and files below `/app/next/` are served with a stricter policy
  without `'unsafe-eval'` and `'unsafe-inline'` in `script-src` (only the nonce of the response
  and `'strict-dynamic'`; `'self'` is a fallback for old browsers). The policy of the classic
  applications is unchanged. A policy configured by the operator (`contentSecurityPolicyValue`,
  `camunda.bpm.webapp.header-security.content-security-policy-value`) applies to every
  application, the new web UI included. Nothing is loaded from other hosts.
- `config.json` (optional, next to `index.html`): `csrfCookieName` if the CSRF cookie has a
  custom name, `defaultLocale` (the tag of an available language, e.g. `vi`; default `en`).

## Development

```
npm ci
npm run dev        # development server, proxies /fasti/api to http://localhost:18080
npm test           # unit tests (Vitest, Testing Library)
npm run build      # type check, lint, bundle
```

Node.js 24 is required (the Maven build downloads it). `./mvnw clean verify -pl
webapps/assembly-jakarta -am` builds the classic and the new UI; `-Dskip.frontend.build=true`
skips both.

### End-to-end tests (Playwright)

The suite in `e2e/` runs against a running distribution (Run, Tomcat or WildFly) and is not
part of the default build. The global setup deploys the test processes, decision and form of
`e2e/fixtures` through REST and removes them afterwards (every name starts with `e2e`). Every
test fails on CSP violations and console errors.

```
npx playwright install chromium                         # once, downloads Playwright's Chromium
E2E_BASE_URL=http://localhost:18800/fasti/ npm run e2e  # this address is the default; user demo/demo
./mvnw verify -Pe2e-next -pl webapps -De2e.baseUrl=http://localhost:18800/fasti/
```

Other variables: `E2E_USER`, `E2E_PASSWORD`, `E2E_ENGINE`, `E2E_KEEP_DATA=1` (keeps the test
data). The HTML report and traces are written to `target/e2e/`.

#### Single sign-on (SSO)

The separate suite `e2e/sso/` tests the login through a real OpenID Connect provider (for
example Keycloak) against a distribution configured for SSO: login and return to the requested
page, mapping of user, groups and tenants, logout ending the provider session too, and bearer
tokens on `/engine-rest` (valid, missing, bad signature, wrong audience). It is skipped unless
`E2E_OIDC_BASE_URL`, `E2E_OIDC_USER` and `E2E_OIDC_PASSWORD` are set and needs no seeded data.

```
E2E_OIDC_BASE_URL=http://localhost:8080/fasti/ \
E2E_OIDC_USER=alice E2E_OIDC_PASSWORD=... \
E2E_OIDC_GROUPS=camunda-admin,accounting E2E_OIDC_TENANTS=tenant-a \
E2E_OIDC_TOKEN_URL=https://sso.example.com/realms/fasti/protocol/openid-connect/token \
E2E_OIDC_CLIENT_ID=fasti-cli E2E_OIDC_OTHER_CLIENT_ID=other-cli \
npm run e2e:sso
```

`E2E_OIDC_TOKEN_URL` and `E2E_OIDC_CLIENT_ID` (a public client allowing the password grant whose
tokens carry an audience of `fasti.security.oidc.rest.audiences`) enable the bearer token tests;
`E2E_OIDC_OTHER_CLIENT_ID` is a client with another audience. Others: `E2E_OIDC_USER_ID` (engine
user id if it differs from the login name), `E2E_OIDC_ENGINE`, `E2E_OIDC_REST_URL`. The login page
of the provider must have `username` and `password` fields (Keycloak). Reports go to
`target/e2e-sso/`.

## Structure

| Folder | Content |
| --- | --- |
| `src/api` | Typed REST client with CSRF and uniform error handling |
| `src/app` | Application shell: login, navigation, command palette, session |
| `src/welcome` | Welcome page |
| `src/i18n` | Messages: one folder per language in `locales/`, English is the reference (see Languages) |
| `src/theme` | Design tokens (colour, spacing, type) for the light and the dark theme |
| `src/ui` | Shared components on top of Radix UI |
| `src/tasklist`, `src/processes` | Tasklist and Start process |
| `src/cockpit` | Monitoring: dashboard, processes, instances, incidents, jobs, history, deployments, decisions, batches |
| `src/modeler` | BPMN, DMN and form modeler |
| `src/admin` | Administration |
| `src/forms` | Form area and form renderers |
| `src/bpmn`, `src/dmn` | Process diagram (bpmn-js) and decision model (dmn-js) |
| `src/plugins` | Plugin points |

## Monitoring (Cockpit)

The section is only offered to users who may use the `cockpit` application; the server checks
the permissions on the data, and a page shows a message when it is refused (403) instead of
breaking. The section calls the REST API of the engine (`/api/engine/engine/{engine}/...`,
including the process instance comment endpoint added by Fasti BPM) and the resources of the
`base` plugin of the classic Cockpit (`/api/cockpit/plugin/base/{engine}/...`: process
statistics, instance list, incidents, called processes). The calls are in `src/api/`
(`cockpit.ts`, `operations.ts`, `maintenance.ts`, `workload.ts`).

| Address (after `#`) | Content |
| --- | --- |
| `/cockpit` | Dashboard: the main numbers and charts (own SVG) |
| `/cockpit/processes` | Processes, grouped by key, with instance and incident counts |
| `/cockpit/processes/definition/{id}` | One version of a process: diagram with numbers per activity, instances, incidents, called processes, job definitions |
| `/cockpit/processes/key/{key}` | Redirects to the latest version of the process with that key |
| `/cockpit/processes/instance/{id}` | One instance: diagram, activity tree, variables, incidents, called instances, user tasks, jobs, external tasks |
| `/cockpit/incidents` | Incidents of the whole engine |
| `/cockpit/deployments`, `/cockpit/deployments/{id}` | Deployments, their files, upload |
| `/cockpit/decisions`, `/cockpit/decisions/definition/{id}` | Decisions and their evaluations |
| `/cockpit/batches` | Batches |
| `/cockpit/jobs` | Failed jobs of the whole engine, bulk retries |
| `/cockpit/external-tasks` | External tasks by topic and worker |
| `/cockpit/workload` | Workload per user and group |
| `/cockpit/history`, `/cockpit/history/instance/{id}` | Finished instances, timeline and history of one instance |
| `/cockpit/decision-history`, `/cockpit/decision-history/{id}` | Decision evaluation history |
| `/cockpit/migration` | Migrating instances to another version |
| `/cockpit/audit` | Operation log (who changed what) |
| `/cockpit/cleanup` | History cleanup |
| `/cockpit/modeler` | BPMN, DMN and form modeler, deployment |

Filters, pages and the open tab are part of the query string, so every view can be linked. The
sorting of a table and a paused automatic refresh are remembered by the browser. Variable values,
error messages and file contents are always shown as text, never as HTML. The instance page
also offers instance modification and restart. Not available yet: CMMN — use the classic
Cockpit for it.

## Administration (Admin)

The administration is only shown to users who may use the `admin` application. It only calls the
REST API of the engine (`/api/engine/engine/{engine}/user|group|tenant|authorization|identity/password-policy|telemetry/data`)
and the initial setup resource of the classic Admin (`/api/admin/setup/{engine}/user/create`); all
calls are in `src/api/admin.ts`. What a user may do is read from the links of `OPTIONS` requests:
if the identity provider is read-only (e.g. LDAP) or a permission is missing, the create, edit and
delete actions are hidden and the page says why.

| Path | Content |
| --- | --- |
| `/admin/users`, `/admin/users/{id}` | Users: search, create, profile, password, groups, tenants, delete |
| `/admin/groups`, `/admin/groups/{id}` | Groups: create, edit, members, tenants, delete |
| `/admin/tenants`, `/admin/tenants/{id}` | Tenants: create, edit, users, groups, delete |
| `/admin/authorizations` | Authorizations per resource type: create, edit, delete |
| `/admin/system` | Versions and environment (read only; nothing is sent anywhere) |
| `/setup` | Creates the first administrator of an engine without one |

As long as the engine has no administrator, the server redirects `/app/next/{engine}/` to
`/app/next/{engine}/setup/#/setup` (instead of the setup page of the classic Admin).

## Forms

The form area (`FormHost`) does not know how a form is drawn. It resolves the kind of form
(`resolveForm`), picks the registered renderer and submits the variables the renderer returns.
Built-in renderers: Camunda Forms (form-js), forms generated from the form fields of the model
(rendered natively), the generic variable form, embedded HTML forms and external forms.

### Embedded HTML forms

Embedded forms (`embedded:app:...`, `embedded:deployment:...`, start forms included) contain
markup and scripts of whoever deployed the process; they need AngularJS, `eval` and inline
scripts. The policy of `/app/next` forbids those and is not loosened. Such a form is rendered by
the *form frame* instead:

- The frame is a separate page, `{appRoot}/app/forms/{engine}/`, built by the classic frontend
  (`webapps/frontend/ui/forms/`). It uses the embedded forms SDK of the classic Tasklist
  (`camunda-bpm-sdk-js`, `CamForm` with AngularJS), so forms behave as they did there:
  `cam-variable-name`/`cam-variable-type`, `cam-script` with `camForm`, `$scope` and `inject`,
  `cam-business-key`, `cam-file-download`, file uploads, error and escalation buttons, and the
  "Save" of entered values in the local storage.
- The frame is served with the policy of the classic applications, except `frame-ancestors`
  and `sandbox`, which follow the isolation mode (see `ContentSecurityPolicyProvider`); a policy
  configured by the operator is adapted the same way.
- Host and frame talk by `postMessage` only, with the one frame window and its expected origin;
  unknown messages are dropped (`src/forms/embedded/protocol.ts`,
  `frontend/ui/forms/client/scripts/protocol.js`). The host says which task or start event to
  render; the frame renders the form, then reports its size, validity and the result of a submit.
  Popups of a form (e.g. a date picker) are clipped at the edge of the frame.

The isolation is chosen with `fasti.webapp.forms-isolation` (Run, Spring Boot starter; environment
variable `FASTI_WEBAPP_FORMS_ISOLATION`) or the init parameter `formsIsolation` of the
`Engines Filter` in `WEB-INF/web.xml` (Tomcat, WildFly; present as a comment):

- `sandboxed` (default): the `iframe` is sandboxed **without** `allow-same-origin`
  (`allow-scripts allow-forms allow-popups allow-downloads`), so it has an opaque origin: no
  cookies, no session, no access to the embedding page. The policy of `/app/forms` carries the
  same `sandbox` directive, so the page stays isolated when it is opened on its own. Every REST
  call of a form (the SDK's `camForm` client and AngularJS `$http`) is sent to the host by
  `postMessage`; the host checks it against an allow-list tied to the task or start event being
  shown (`src/forms/embedded/broker.ts`) and performs it with the user's session and CSRF token.
  Allowed: reading the task, its form, its variables and those of its process instance, file
  downloads of those variables, changing task variables, complete / submit, BPMN error and
  escalation; for start forms reading the definition and its form and starting exactly that
  definition; plus the form resource and the current user. Everything else is answered with 403
  and logged on the console of both pages. Stored ("Save") values are kept by the host (only the
  `camForm:{id}` key of this form); downloads are performed by the host.
- `separate-origin`: the frame is served from another origin (`fasti.webapp.forms-origin`, init
  parameter `formsOrigin`, e.g. `https://forms.example.com`, a second host name of the same
  server). It keeps `allow-same-origin` for that origin, so local storage, modules and fonts work
  as usual; REST calls are still brokered by the host. The new web UI may frame that origin
  (`frame-src`); the frame may be framed by anyone (`frame-ancestors *`) since it has no session.
  Do not log in on the forms origin, and block its `/api` at the reverse proxy.
- `same-origin`: the previous behaviour: the frame shares origin and session with the new web UI.
  **Risk:** form scripts can reach `window.parent` and call any REST API with the user's session.
  Only for installations whose process deployers are all trusted and whose forms cannot run
  isolated yet.

Limits of the isolated modes: form scripts reach the REST API through `camForm.client` or
`$http` only; plain `fetch`/`XMLHttpRequest` have no session. Custom scripts of the classic
Tasklist (`customScripts`) are loaded through the host, but relative imports inside them fail.

### AngularJS

AngularJS (end of life) stays only inside the form frame, because existing embedded forms need
it. No page of `/app/next` loads AngularJS (test `src/app/noAngular.test.ts`). The path for
customers is to move embedded forms to Camunda Forms (form-js, `camunda-forms:deployment:...`),
which the new web UI renders natively, without a frame:

1. Rebuild the form in the form-js editor (Camunda Modeler, or the form editor in the Modeler
   section of the new web UI), keeping the variable names (`cam-variable-name` becomes the
   field `key`).
2. Replace `cam-script` with form-js features (conditions, FEEL expressions, validation); logic
   that called the REST API moves to server-side listeners or delegates.
3. Deploy the `.form` with the process, switch `camunda:formKey` to `camunda:formRef` (or
   `camunda-forms:deployment:...`) and retire the HTML form.

### External forms

A form key that is a URL, or `app:...` without `embedded:`, belongs to another application. As
in the classic Tasklist, the form opens in a new tab with `taskId` (or `processDefinitionKey`)
and `callbackUrl` (the Tasklist of this application) as query parameters. Only `http(s)`
addresses are accepted.

A new renderer (a form builder, a custom kind of form) is added without touching the Tasklist:

```ts
registerFormRenderer({
  id: 'my-renderer',
  supports: (form, target) => form.info.key?.startsWith('my-forms:') ?? false,
  component: MyRenderer, // receives FormRendererProps: target, form, disabled, submitLabel, submit
});
```

Renderers registered later are asked first.

## Languages (i18n)

Every language is a folder `src/i18n/locales/<BCP 47 tag>/` (e.g. `de`, `pt-BR`, `zh-CN`) and is
discovered automatically; no other file changes:

- `manifest.ts`: native name, English name, direction (`ltr`/`rtl`), `reviewed`.
- every other file (`core.ts`, `ops.ts`, `tools.ts`, ...): messages, merged; which file holds a
  key does not matter. These files only use `import type`.

English (`locales/en`) is the reference catalogue, bundled, and the fallback for missing keys;
other languages are separate chunks (one per language) loaded when chosen. Unknown keys are
compile errors; missing keys are reported by `npm run i18n:check` (part of `npm run lint`).
Plurals follow `Intl.PluralRules`: the key without suffix is the general form, `key.one`,
`key.few`, `key.many`, ... are variants where the language has them. Dates and numbers use `Intl`
with the chosen language.

Adding a language:

1. `npm run i18n:new -- ar` creates the folder with a manifest and the English keys commented out.
2. Translate each line and remove the comment marker; keep `{placeholders}`, BPMN/DMN, API names.
3. `npm run i18n:check` must be clean. Translations not reviewed by a native speaker keep
   `reviewed: false`.

Right-to-left languages (e.g. Arabic) are prepared: the page `dir` follows the manifest and the
CSS uses logical properties (`margin-inline-start`, `inset-inline-end`, ...).

The language is the saved choice, otherwise `defaultLocale` in `config.json` (default `en`). The
browser language is only suggested on the login page while the user has not chosen one.

## Plugin points

Two points exist: `shell.navigation` (a navigation entry with a page) and `tasklist.task.tab`
(a tab of the open task). Plugins are registered in `src/plugins/index.ts` with
`registerPlugin(...)` and are part of the bundle.

The next step, once separately deployed plugins are needed: the server lists plugins through the
existing mechanism (`/api/tasklist/plugin/{id}/static/...`), `index.html` receives that list
through a placeholder, and the application loads each plugin with `import()` (with the nonce) and
calls `registerPlugin`. This is not implemented yet; the `Plugin` interface is kept small enough
that it will not have to change for it.
