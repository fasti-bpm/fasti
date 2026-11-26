English | [Tiếng Việt](README.vi.md)

# REST API integration

Fasti BPM keeps the paths and request formats of the `/engine-rest` REST API of Camunda 7 CE
7.24.0, adds endpoints and tightens some behaviour for security (see
[Differences from Camunda 7](#differences-from-camunda-7)).
The full reference of every endpoint is still the
[Camunda 7.24 REST API reference](https://docs.camunda.org/rest/camunda-bpm-platform/7.24/);
this guide shows the most common calls and where Fasti differs. Fasti BPM is an independent
project, not affiliated with Camunda Services GmbH.

Every command below was run against the Fasti image with H2 and the user `demo`/`demo` and gave
the results shown. The commands use `bash`, `curl` and
[`jq`](https://jqlang.org/).

## Preparation

To try it out, start Fasti with H2 and the administrator `demo`/`demo` (every example below
uses this user; on a real system use your own user and password, see the
[installation guide](../install-docker.md)):

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti-api -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=demo -e FASTI_ADMIN_PASSWORD=demo \
  fastidev/fasti-bpm:1.3.2 --webapps --rest
```

Wait until `curl -s http://localhost:8080/actuator/health/readiness` answers `{"status":"UP"}`,
then set two variables for the commands below:

```bash
BASE=http://localhost:8080/engine-rest
AUTH='demo:demo'
```

## Authentication

**Basic authentication (default).** The Fasti image requires authentication for the REST API and
enforces authorizations in every mode (unlike the default configuration of Camunda Run 7.24, where
`/engine-rest` is open and authorizations are off). Users, groups and authorizations come from the engine database.

```bash
curl -s -o /dev/null -w '%{http_code}\n' "$BASE/task"   # 401
curl -s -u "$AUTH" "$BASE/version"                      # {"version":"..."}
```

- Give every integration (worker, service) its own user, created in the Admin section of the web
  UI. Members of `camunda-admin` may do everything; other users need authorizations, otherwise
  they see empty lists or get `403`.
- Repeated failed logins lock the account for a while; do not let a worker retry endlessly with
  a wrong password.

**Bearer tokens (OIDC).** With single sign-on enabled, the REST API also accepts
`Authorization: Bearer <JWT>` issued by the OpenID Connect provider, next to Basic
authentication:

```yaml
fasti.security.oidc.rest:
  bearer-enabled: true
  issuer-uri: https://sso.example.com/realms/fasti
  audiences: [fasti-api]
  user-id-claim: preferred_username
```

```bash
curl -s -H "Authorization: Bearer $ACCESS_TOKEN" "$BASE/task"
```

Signatures are checked against the issuer's JWKS with asymmetric algorithms only (default
`RS256`); `iss`, `aud`, `exp` and `nbf` are validated. A browser session cookie never
authenticates `/engine-rest`. Full configuration (Keycloak, Microsoft Entra ID, Tomcat, WildFly):
the [single sign-on guide](../single-sign-on.md). Bearer
tokens were not part of the test run of this guide (they need an OIDC provider).

## Example process

The examples use the process [`order.bpmn`](order.bpmn) in this directory: a user task for the
group `sales`, an external task with the topic `charge-card`, then a wait for the message
`PaymentConfirmed`. Run the commands from the `docs/api` directory of the repository, or download
`order.bpmn` into your current directory.

## Deploy

```bash
curl -s -u "$AUTH" "$BASE/deployment/create" \
  -F deployment-name=order \
  -F deploy-changed-only=true \
  -F order.bpmn=@order.bpmn
```

The response lists `deployedProcessDefinitions` with the key `order`. With
`deploy-changed-only=true`, deploying an unchanged file again creates no new version.

## Start an instance

```bash
PI=$(curl -s -u "$AUTH" -H 'Content-Type: application/json' \
  "$BASE/process-definition/key/order/start" \
  -d '{"businessKey":"ORD-1001",
       "variables":{"amount":{"value":250,"type":"Integer"},
                    "customer":{"value":"Jane Doe","type":"String"}}}' \
  | jq -r .id)
echo "$PI"
```

## Query and complete tasks

```bash
# GET with simple parameters
curl -s -u "$AUTH" "$BASE/task?processInstanceBusinessKey=ORD-1001"

# POST for richer criteria: candidate group, variables, sorting
curl -s -u "$AUTH" -H 'Content-Type: application/json' "$BASE/task" \
  -d '{"candidateGroup":"sales",
       "processVariables":[{"name":"amount","operator":"gteq","value":100}],
       "sorting":[{"sortBy":"created","sortOrder":"asc"}]}'

TASK=$(curl -s -u "$AUTH" "$BASE/task?processInstanceId=$PI" | jq -r '.[0].id')

curl -s -u "$AUTH" "$BASE/task/$TASK/form-variables"            # variables for a form
curl -s -u "$AUTH" -H 'Content-Type: application/json' \
  "$BASE/task/$TASK/claim" -d '{"userId":"demo"}'                 # 204
curl -s -u "$AUTH" -H 'Content-Type: application/json' \
  "$BASE/task/$TASK/complete" \
  -d '{"variables":{"approved":{"value":true,"type":"Boolean"}}}' # 204
```

To count instead of listing: `GET $BASE/task/count?...` returns `{"count":n}`.

### Workload per group and per person (Fasti)

`GET /task/workload` counts the open tasks the caller may read in one request: in total, per
candidate group and per assignee asked for (at most 200 of each), with the unclaimed and
overdue ones and the creation time of the oldest. The same authorization and tenant checks as
`GET /task` apply. `POST /task/workload` takes the same parameters as a JSON body.

```bash
curl -s -u "$AUTH" "$BASE/task/workload?groupIdIn=accounting,sales&userIdIn=demo"
# {"totals":{"id":null,"open":16,"unassigned":7,"overdue":0,"oldest":"..."},"orphaned":3,
#  "groups":[{"id":"accounting","open":4,"unassigned":4,"overdue":0,"oldest":"..."},...],
#  "users":[{"id":"demo","open":9,"unassigned":0,"overdue":0,"oldest":"..."}]}
```

`orphaned` counts unclaimed tasks offered to no group. Parameters: `groupIdIn`, `userIdIn`,
`tenantIdIn` (comma separated) and `overdueBefore` (a date; the current time by default).

- Each list takes at most 200 distinct ids of at most 255 characters. An empty id (`a,,b`, or
  `""`/`null` in the body), a longer one or more ids answer `400`; a parameter without a value
  is an empty list. A repeated id is answered once, in the order of its first occurrence.
- Ids are compared as the database compares them. With a case-insensitive collation (the
  default of SQL Server, MySQL and MariaDB) `sales` also counts the tasks offered to `Sales`,
  exactly as `GET /task?candidateGroup=sales` does; the entry carries the id as it was asked for.
  There, two ids that differ only in case name the same group: ask for one of them (if one is
  spelled as stored, only that one gets the tasks).
- The answer is kept for 5 seconds for the same question of the same caller (the same user,
  groups and tenants, under the same authorization settings), and callers that ask the same at
  the same time share one read (`fasti.workload.cache-ttl`, image `FASTI_WORKLOAD_CACHE_TTL`,
  `0` turns keeping off; Java: `TaskWorkloads.setCacheTimeToLive`). So a count can be up to that
  much older than the tasks, also after a grant is revoked.
- `501` means that the MyBatis mappings of the engine lack the workload statements: an
  application that replaced the default `mappings.xml` with a copy of its own must add
  `<mapper resource="dev/fasti/engine/workload/TaskWorkload.xml" />` to it. The web UI then
  counts per group and per person with `GET /task/count`, as with a server without the endpoint
  (`404`/`405`).

## Variables

```bash
curl -s -u "$AUTH" "$BASE/process-instance/$PI/variables"

curl -s -u "$AUTH" -X PUT -H 'Content-Type: application/json' \
  "$BASE/process-instance/$PI/variables/note" \
  -d '{"value":"Deliver during office hours","type":"String"}'    # 204

curl -s -u "$AUTH" "$BASE/process-instance/$PI/variables/note"

# find instances by a variable
curl -s -u "$AUTH" -H 'Content-Type: application/json' "$BASE/process-instance" \
  -d '{"variables":[{"name":"note","operator":"like","value":"%office%"}]}'
```

Common types: `String`, `Integer`, `Long`, `Double`, `Boolean`, `Date`
(`2026-11-19T10:00:00.000+0000`), `Json`. JSON bodies must be UTF-8; on Windows (Git Bash,
PowerShell) put non-ASCII data into a UTF-8 file (without BOM) and send it with
`--data-binary @body.json`; in PowerShell call `curl.exe` (not the `curl` alias) and quote
`'@body.json'`.

## Comments on the process instance itself (Fasti)

The Camunda 7 REST API adds comments only through a task (the Java API can already comment on a
process instance). Fasti adds a REST endpoint that writes a comment on the process instance
itself; the engine checks that the caller may update that instance.

```bash
curl -s -u "$AUTH" -H 'Content-Type: application/json' \
  "$BASE/process-instance/$PI/comment/create" \
  -d '{"message":"Customer called, approve today"}'

curl -s -u "$AUTH" "$BASE/process-instance/$PI/comment"
```

A comment can only be written while the instance is running (`POST` answers `404` once it has
finished); `GET` still returns the comments of a finished instance.

## External tasks (fetchAndLock)

A worker fetches work by topic, locks it for `lockDuration` milliseconds and reports completion.
With `asyncResponseTimeout` the request waits (long polling) until work arrives or the time is up.

```bash
curl -s -u "$AUTH" -H 'Content-Type: application/json' \
  "$BASE/external-task/fetchAndLock" \
  -d '{"workerId":"worker-1","maxTasks":1,"usePriority":true,
       "asyncResponseTimeout":10000,
       "topics":[{"topicName":"charge-card","lockDuration":60000,
                  "variables":["amount","customer"]}]}' > tasks.json

ET=$(jq -r '.[0].id' tasks.json)

curl -s -u "$AUTH" -H 'Content-Type: application/json' \
  "$BASE/external-task/$ET/complete" \
  -d '{"workerId":"worker-1",
       "variables":{"chargeId":{"value":"ch_123","type":"String"}}}'  # 204
```

On errors: `POST /external-task/{id}/failure` (the worker sends the remaining `retries`; `0`
creates an incident)
or `POST /external-task/{id}/bpmnError`; to extend the lock: `POST /external-task/{id}/extendLock`.
See the reference for the request bodies.

## Messages

```bash
curl -s -u "$AUTH" -H 'Content-Type: application/json' "$BASE/message" \
  -d '{"messageName":"PaymentConfirmed","businessKey":"ORD-1001",
       "processVariables":{"paidAt":{"value":"2026-11-19T10:00:00.000+0000","type":"Date"}},
       "resultEnabled":true}'
```

With `resultEnabled` the response lists the execution that received the message. If no instance
waits for it, the answer is `400`.

## History

```bash
curl -s -u "$AUTH" "$BASE/history/process-instance/$PI"    # "state":"COMPLETED"
```

## Java external task client

The Java and JavaScript clients below replace the `curl` worker above. To see them do work, start
a new instance and complete its "Approve order" task first, so that an external task waits on the
topic `charge-card`.

<!-- update with the Maven Central version at release -->
Artifact `dev.fasti.platform:fasti-external-task-client` (instead of
`org.camunda.bpm:camunda-external-task-client`; the package `org.camunda.bpm.client` is
unchanged). It is not on Maven Central yet: build it from source
(`./mvnw -pl clients/java/client -am install -DskipTests`) and set `fasti.version` to
`1.3.2`.

```xml
<dependency>
  <groupId>dev.fasti.platform</groupId>
  <artifactId>fasti-external-task-client</artifactId>
  <version>${fasti.version}</version>
</dependency>
<!-- the client needs a JAXB implementation at runtime -->
<dependency>
  <groupId>com.sun.xml.bind</groupId>
  <artifactId>jaxb-impl</artifactId>
  <version>4.0.9</version>
</dependency>
```

Without a JAXB implementation `ExternalTaskClient.create()...build()` stops with
`NoClassDefFoundError: jakarta/xml/bind/JAXBException` (as the original 7.24 client does).

```java
import java.util.Map;
import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.interceptor.auth.BasicAuthProvider;

public class ChargeCardWorker {
  public static void main(String[] args) {
    ExternalTaskClient client = ExternalTaskClient.create()
        .baseUrl("http://localhost:8080/engine-rest")
        .workerId("java-worker-1")
        .asyncResponseTimeout(10_000)
        .addInterceptor(new BasicAuthProvider("demo", "demo"))
        .build();

    client.subscribe("charge-card")
        .lockDuration(60_000)
        .handler((task, service) -> {
          Integer amount = task.getVariable("amount");
          service.complete(task, Map.of("chargeId", "java-" + amount));
        })
        .open();
  }
}
```

For Spring Boot there is the starter
`dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-external-task-client`.

## JavaScript external task client

Fasti has no JavaScript client of its own. The npm package
[`camunda-external-task-client-js`](https://www.npmjs.com/package/camunda-external-task-client-js)
(Apache-2.0, published by Camunda) speaks this REST API and works with Fasti; tested with
version 3.1.0 on Node.js 22. The package is an ES module, so name the file `.mjs` or set
`"type": "module"`.

```bash
npm install camunda-external-task-client-js
```

```js
import { Client, BasicAuthInterceptor, Variables, logger } from 'camunda-external-task-client-js';

const client = new Client({
  baseUrl: 'http://localhost:8080/engine-rest',
  workerId: 'js-worker-1',
  asyncResponseTimeout: 10000,
  interceptors: new BasicAuthInterceptor({ username: 'demo', password: 'demo' }),
  use: logger,
});

client.subscribe('charge-card', async ({ task, taskService }) => {
  const amount = task.variables.get('amount');
  await taskService.complete(task, new Variables().set('chargeId', `js-${amount}`));
});
```

For bearer tokens, pass an interceptor that adds the `Authorization` header.

## Differences from Camunda 7

| Topic | Camunda 7.24 (Run, former image) | Fasti |
|---|---|---|
| Authentication on `/engine-rest` | off | **on** (Basic, or bearer with OIDC); authorizations on |
| New endpoints | — | `POST /process-instance/{id}/comment/create` (comment on the instance itself); the existing `GET /process-instance/{id}/comment` returns those comments; `GET`/`POST /task/workload` (open tasks per group and assignee) |
| Web application path | `/camunda/...` | `/fasti/...` (`/camunda/...` redirects). The engine REST API stays at `/engine-rest` |
| Expressions in queries | any expression | allow-list (date functions, current user); other expressions are rejected with `400`; see the [migration guide](../migrate-from-camunda-7.md#what-differs) |
| Task attachments and comments | some endpoints unchecked | always authorized: expect `403` where access is missing |
| Invalid requests | some answered with `500` | `400` for unparsable deployment resources, variables in the prohibited Java serialization format, query parameters of the wrong type and passwords that violate the password policy |
| OpenAPI | Swagger UI (`--swaggerui`) | no Swagger UI; see below |

**OpenAPI.** The image does not serve the OpenAPI document (`/engine-rest/openapi.json` answers
`404`). The file `openapi.json` (OpenAPI 3.0, including the new comment endpoint) is part of the
artifact `dev.fasti.platform:fasti-engine-rest-openapi`; in a source build it is at
`engine-rest/engine-rest-openapi/target/generated-sources/openapi-json/openapi.json`. Use it to
generate clients or open it in any OpenAPI tool.
