English | [Tiếng Việt](mcp.vi.md)

# MCP server for AI assistants

Fasti BPM can serve a [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server, so
that AI assistants and agents (Claude, ChatGPT, Copilot, Cursor and other MCP clients) can look
at process definitions, instances, tasks, incidents and decisions, and, if you allow it, start
processes, work on tasks and retry failed jobs. The server is **off by default**. When it is on,
every request needs a login and every tool runs as that user, through the authorization checks
of the engine, so an assistant sees and does exactly what the user may see and do in Cockpit and
Tasklist, and nothing more.

The server speaks MCP 2025-11-25 (also 2025-06-18 and 2025-03-26) over the Streamable HTTP
transport at `/mcp`. It is built on the official MCP Java SDK.

## Turning it on

| Distribution | How |
|---|---|
| Run | start with `--mcp` (`./start.sh --mcp`, `start.bat --mcp`); it is added to the other components |
| Container image | `FASTI_MCP_ENABLED=true` (or the argument `--mcp`) |
| Spring Boot | add `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-mcp` and set `fasti.mcp.enabled=true` |

Tools that change data are a second, separate switch: `fasti.mcp.write-tools.enabled=true`
(`FASTI_MCP_WRITE_TOOLS_ENABLED=true` in the image). Without it the server is read-only.

In an embedded Spring Boot application that has its own Spring Security configuration, let
requests to `/mcp` through (`permitAll`): the MCP server checks the credentials itself.

## Connecting a client

The endpoint is `http(s)://<host>:<port>/mcp`. Clients send the credentials of an engine user in
the `Authorization` header: `Basic <base64(user:password)>`, or, with OIDC (below),
`Bearer <access token>`. Most clients take a configuration like this one; the file name and the
name of the top-level key (`mcpServers`, `servers`) depend on the client:

```json
{
  "mcpServers": {
    "fasti": {
      "type": "http",
      "url": "https://bpm.example.com/mcp",
      "headers": {
        "Authorization": "Basic ZGVtbzpkZW1v"
      }
    }
  }
}
```

Claude Code, for example:

```bash
claude mcp add --transport http fasti https://bpm.example.com/mcp \
  --header "Authorization: Basic $(printf '%s' 'agent:secret' | base64)"
```

Use a technical user for each assistant with only the permissions it needs (for example `READ`
on the process definitions of one department), not an administrator.

### A session by hand

```bash
AUTH="Authorization: Basic $(printf '%s' 'demo:demo' | base64)"
H=(-H "$AUTH" -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream')

curl -s "${H[@]}" http://localhost:8080/mcp -d '{"jsonrpc":"2.0","id":1,"method":"initialize",
  "params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}'
curl -s "${H[@]}" -H 'MCP-Protocol-Version: 2025-11-25' http://localhost:8080/mcp \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
curl -s "${H[@]}" -H 'MCP-Protocol-Version: 2025-11-25' http://localhost:8080/mcp \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_tasks","arguments":{"assignedToMe":true}}}'
```

The server is stateless: it issues no `Mcp-Session-Id`, answers every request with a single JSON
response and refuses `GET` (no server-to-client event stream) with 405.

## Security model

- **Authentication on every request.** HTTP basic against the users of the engine (with the
  engine's lockout after failed logins), or bearer tokens. There is no anonymous access, also
  when the REST API of Run runs without authentication. `fasti.mcp.basic-auth-enabled=false`
  leaves bearer tokens only.
- **Bearer tokens (OIDC).** With the Spring Security module (`--oauth2` in Run) and
  `fasti.security.oidc.rest.bearer-enabled=true`, the MCP server accepts the same access tokens
  as the REST API, checked the same way (signature, issuer, audience, expiry) and mapped to the
  same user, groups and tenants (`fasti.security.oidc.rest.*`, see
  [single-sign-on.md](single-sign-on.md)). It then also serves the OAuth 2.0 protected resource
  metadata (RFC 9728) at `/.well-known/oauth-protected-resource/mcp` and names it in the
  `WWW-Authenticate` challenge, so that MCP clients can find the identity provider. The tokens
  must carry one of the configured audiences.
- **The user's permissions, nothing else.** Each tool call sets the authenticated user, groups
  and tenants on the engine for its duration and clears them afterwards. Queries return only
  what the user may read; reading or changing something without permission returns a tool error
  with the engine's reason, never the data. Turn on authorization checks
  (`camunda.bpm.authorization.enabled=true`, the default of the container image); without them
  every authenticated user can read everything, and the server logs a warning at startup.
- **Read-only unless switched on.** Start, claim, unclaim, complete and retry exist only with
  `fasti.mcp.write-tools.enabled=true`; without it they are not even listed. Write tools act for
  the caller only: a task is claimed for the caller, and only the caller's own tasks can be
  unclaimed or completed. Four-eyes rules ([four-eyes.md](four-eyes.md)) are enforced by the
  engine; a refusal comes back as an error with its reason.
- **No code from the caller.** No tool deploys models, runs scripts or evaluates expressions
  given by the caller. Variables passed to write tools are plain values (strings, numbers,
  booleans, null), never objects. Evaluating a DMN decision runs only the expressions of the
  deployed decision.
- **Audit.** Changes go through the normal engine operations as the user, so the history, the
  user operation log and, if enabled, the audit trail ([audit-trail.md](audit-trail.md)) record
  the user. Calls of write tools are also logged (`dev.fasti.spring.boot.mcp`, INFO).
- **Data minimisation.** Lists are paged (default 25, at most 100 per call). Variables whose
  names contain `password`, `secret`, `token`, `credential`, `apikey` or `api_key` are returned
  masked, without value. Long values (2000 characters), models (200,000) and stack traces
  (8000) are cut and marked. Binary values and files are described, not returned. Objects are
  returned in their serialized form (JSON, XML); Java-serialized objects are never deserialized
  and only named.
- **Origin and DNS rebinding.** A request with an `Origin` header that is not in
  `fasti.mcp.allowed-origins` is refused with 403, as the MCP specification requires; MCP
  clients outside a browser send no `Origin`. `fasti.mcp.allowed-hosts` also checks the `Host`
  header (421 otherwise). Requests are limited to 256 KB.
- **Binding.** For use on one machine, bind the server to the loopback interface
  (`server.address=127.0.0.1`) or publish the container port on `127.0.0.1` only. Across a
  network, put it behind TLS (a reverse proxy or `server.ssl.*`): basic credentials and tokens
  must not travel in clear text.
- **Untrusted content.** Process data (business keys, variables, task names, incident messages)
  is returned to the assistant as data. Text in it can try to steer the assistant (prompt
  injection). Keep write tools off where an assistant reads data entered by others, or let the
  client ask a person before each write call.

## Tools

| Tool | What it does | Permission (engine) |
|---|---|---|
| `get_engine_info` | engine name, version, history level, whether authorization is on, the caller with groups and tenants, limits | – |
| `list_process_definitions` | deployed process definitions, latest version by default; filter by key, name, tenant | `READ` on the definition |
| `get_process_definition` | one definition by id or key (and version), running instance count; BPMN XML on request | `READ` |
| `list_process_instances` | running instances by definition, business key, state, incidents | `READ` on the instance or `READ_INSTANCE` |
| `get_process_instance` | one instance: active activities, incidents, variables (running) or historic data and variables (ended); executed activities on request | as above, history: `READ_HISTORY` |
| `list_historic_process_instances` | history of instances by definition, business key, state, start/end time, starter | `READ_HISTORY` |
| `list_tasks` | open user tasks: mine, claimable by me, by assignee, candidate group, instance, definition, activity, name | `READ` on the task or `READ_TASK` |
| `get_task` | one task with form key, candidate users and groups, variables | as above |
| `list_incidents` | open incidents by instance, definition, type, activity | `READ` on the instance |
| `get_incident` | one incident with the failed job (retries, stack trace) or external task (error details) | `READ` on the instance |
| `list_decision_definitions` | deployed DMN decisions; DMN XML on request | `READ` on the decision |
| `evaluate_decision` | evaluates a decision with plain input values; changes no process data, the engine records the evaluation in the history | `CREATE_INSTANCE` on the decision |
| `start_process_instance` *(write)* | starts an instance by definition id or key, with business key and variables | `CREATE` on process instances, `CREATE_INSTANCE` on the definition |
| `claim_task` *(write)* | claims a task for the caller | `TASK_WORK` or `UPDATE` |
| `unclaim_task` *(write)* | gives back a task assigned to the caller | `TASK_WORK` or `UPDATE` |
| `complete_task` *(write)* | completes a task assigned to the caller, with variables; returns the next open tasks | `TASK_WORK` or `UPDATE` |
| `retry_failed_job` *(write)* | new retries for a failed job or external task, by incident or job id (1–10) | `RETRY_JOB` or `UPDATE` on the instance (`UPDATE_INSTANCE` on the definition) |

`evaluate_decision` is available without the write switch: it changes no process, task or
business data, and its permission (`CREATE_INSTANCE` on the decision definition) has to be
granted on purpose. Tool annotations mark it as not read-only, so clients that ask before
non-read-only calls ask before it.

Resources (templates): `fasti://process-definitions/{id}/bpmn` and
`fasti://decision-definitions/{id}/dmn` return the XML of a deployed model (`READ` on it).

## Settings

| Property | Default | Meaning |
|---|---|---|
| `fasti.mcp.enabled` | `false` | serves the MCP server |
| `fasti.mcp.write-tools.enabled` | `false` | offers the tools that change data |
| `fasti.mcp.path` | `/mcp` | path of the endpoint |
| `fasti.mcp.basic-auth-enabled` | `true` | accepts HTTP basic credentials of engine users |
| `fasti.mcp.allowed-origins` | (none) | origins allowed to call from a browser, e.g. `http://localhost:*` |
| `fasti.mcp.allowed-hosts` | (not checked) | accepted `Host` headers, e.g. `localhost:*,bpm.example.com` |
| `fasti.mcp.max-request-size` | `256KB` | largest request body |
| `fasti.mcp.default-results`, `fasti.mcp.max-results` | `25`, `100` | page size of list tools, and its upper limit |
| `fasti.mcp.max-variables` | `100` | variables returned per instance or task |
| `fasti.mcp.max-value-length` | `2000` | characters of a returned value |
| `fasti.mcp.max-input-variables`, `fasti.mcp.max-input-value-length` | `50`, `10000` | variables a write tool accepts, characters per string value |
| `fasti.mcp.max-xml-length` | `200000` | characters of a returned BPMN or DMN model |
| `fasti.mcp.max-stacktrace-length` | `8000` | characters of a returned stack trace |
| `fasti.mcp.masked-variable-names` | `password, secret, token, credential, apikey, api_key` | parts of names whose values are masked |

In the container image every property can be set as an environment variable, for example
`FASTI_MCP_ALLOWED_ORIGINS=http://localhost:6274`.

## Limitations

- Stateless transport: no sessions, no server-initiated messages (progress, change
  notifications, sampling), no event stream on `GET`.
- One engine: the tools use the default process engine of the application.
- No CORS: browser-based clients on another origin cannot call the endpoint.
- The Tomcat and WildFly distributions do not include the MCP server; use Run, the container
  image or the Spring Boot starter.
- Variables passed to write tools are plain values; JSON objects, lists, files and dates are not
  accepted.
- Serialized object values (JSON, XML) are read from the database in full and then cut: the
  limits bound the answer, not the read. Binary values and files are not read.
- Engine errors that may quote model details or values (failed expressions, scripts, delegates)
  are not returned; the tool error names a reference to look up in the server log.
