[English](README.md) | Tiếng Việt

# Tích hợp qua REST API

Fasti BPM giữ nguyên đường dẫn và định dạng yêu cầu của REST API `/engine-rest` trong Camunda 7
CE 7.24.0, thêm endpoint mới và siết một số hành vi vì bảo mật (xem [Khác gì so với Camunda 7](#khác-gì-so-với-camunda-7)).
Tài liệu tham chiếu đầy đủ của từng endpoint vẫn là
[REST API reference của Camunda 7.24](https://docs.camunda.org/rest/camunda-bpm-platform/7.24/);
hướng dẫn này chỉ ra những lời gọi hay dùng nhất và những chỗ Fasti khác. Fasti BPM là dự án độc
lập, không liên kết với Camunda Services GmbH.

Mọi lệnh dưới đây đã được chạy thử trên image Fasti với H2 và người dùng `demo`/`demo` và cho
đúng kết quả như ghi. Lệnh dùng `bash`, `curl` và
[`jq`](https://jqlang.org/).

## Chuẩn bị

Để thử, chạy một Fasti với H2 và người dùng quản trị `demo`/`demo` (mọi ví dụ bên dưới dùng
người dùng này; với hệ thật hãy dùng người dùng và mật khẩu của bạn, xem
[hướng dẫn cài đặt](../install-docker.vi.md)):

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti-api -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=demo -e FASTI_ADMIN_PASSWORD=demo \
  fastidev/fasti-bpm:1.1.0 --webapps --rest
```

Đợi `curl -s http://localhost:8080/actuator/health/readiness` trả `{"status":"UP"}`, rồi đặt hai
biến cho các lệnh bên dưới:

```bash
BASE=http://localhost:8080/engine-rest
AUTH='demo:demo'
```

## Xác thực

**Basic auth (mặc định).** Image Fasti yêu cầu xác thực cho REST API và bật phân quyền ở mọi chế
độ (khác cấu hình mặc định của Camunda Run 7.24, nơi `/engine-rest` mở và phân quyền tắt). Người dùng, nhóm và phân quyền
lấy từ cơ sở dữ liệu của engine.

```bash
curl -s -o /dev/null -w '%{http_code}\n' "$BASE/task"   # 401
curl -s -u "$AUTH" "$BASE/version"                      # {"version":"..."}
```

- Mỗi hệ thống tích hợp (worker, dịch vụ) nên có người dùng riêng, tạo trong mục Quản trị của
  giao diện web. Thành viên nhóm `camunda-admin` làm được mọi việc; người dùng khác cần được cấp
  quyền trong mục Phân quyền, nếu không sẽ thấy danh sách rỗng hoặc nhận `403`.
- Đăng nhập sai nhiều lần làm tài khoản bị khóa tạm thời; đừng để worker thử lại liên tục với
  mật khẩu sai.

**Bearer token (OIDC).** Khi bật đăng nhập một lần, REST API nhận thêm
`Authorization: Bearer <JWT>` do nhà cung cấp OpenID Connect cấp, song song với Basic auth:

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

Chữ ký được kiểm tra bằng JWKS của issuer, chỉ nhận thuật toán bất đối xứng (mặc định `RS256`);
`iss`, `aud`, `exp`, `nbf` đều được kiểm tra. Cookie phiên của trình duyệt không bao giờ xác thực
được `/engine-rest`. Cấu hình đầy đủ (Keycloak, Microsoft Entra ID, Tomcat, WildFly): hướng dẫn
[Đăng nhập một lần](../single-sign-on.vi.md). Phần bearer
token không nằm trong lượt chạy thử của hướng dẫn này (cần một nhà cung cấp OIDC).

## Quy trình mẫu

Các ví dụ dùng quy trình [`order.bpmn`](order.bpmn) trong thư mục này: một user task cho nhóm
`sales`, một external task với topic `charge-card`, rồi chờ message `PaymentConfirmed`. Chạy
các lệnh từ thư mục `docs/api` của kho mã, hoặc tải `order.bpmn` về thư mục hiện tại.

## Triển khai (deploy)

```bash
curl -s -u "$AUTH" "$BASE/deployment/create" \
  -F deployment-name=order \
  -F deploy-changed-only=true \
  -F order.bpmn=@order.bpmn
```

Kết quả có `deployedProcessDefinitions` với khóa `order`. Với `deploy-changed-only=true`, triển
khai lại tệp không đổi không tạo phiên bản mới.

## Bắt đầu một instance

```bash
PI=$(curl -s -u "$AUTH" -H 'Content-Type: application/json' \
  "$BASE/process-definition/key/order/start" \
  -d '{"businessKey":"ORD-1001",
       "variables":{"amount":{"value":250,"type":"Integer"},
                    "customer":{"value":"Jane Doe","type":"String"}}}' \
  | jq -r .id)
echo "$PI"
```

## Tìm và hoàn thành task

```bash
# GET với tham số đơn giản
curl -s -u "$AUTH" "$BASE/task?processInstanceBusinessKey=ORD-1001"

# POST khi cần điều kiện phức tạp: nhóm ứng viên, biến, sắp xếp
curl -s -u "$AUTH" -H 'Content-Type: application/json' "$BASE/task" \
  -d '{"candidateGroup":"sales",
       "processVariables":[{"name":"amount","operator":"gteq","value":100}],
       "sorting":[{"sortBy":"created","sortOrder":"asc"}]}'

TASK=$(curl -s -u "$AUTH" "$BASE/task?processInstanceId=$PI" | jq -r '.[0].id')

curl -s -u "$AUTH" "$BASE/task/$TASK/form-variables"            # biến cho form
curl -s -u "$AUTH" -H 'Content-Type: application/json' \
  "$BASE/task/$TASK/claim" -d '{"userId":"demo"}'                 # 204
curl -s -u "$AUTH" -H 'Content-Type: application/json' \
  "$BASE/task/$TASK/complete" \
  -d '{"variables":{"approved":{"value":true,"type":"Boolean"}}}' # 204
```

Đếm thay vì lấy danh sách: `GET $BASE/task/count?...` trả `{"count":n}`.

### Khối lượng việc theo nhóm và theo người (Fasti)

`GET /task/workload` đếm trong một request các task đang mở mà người gọi được đọc: tổng cộng,
theo từng candidate group và từng assignee được hỏi (tối đa 200 mỗi loại), kèm số task chưa ai
nhận, số task quá hạn và thời điểm tạo của task cũ nhất. Kiểm tra quyền và tenant giống hệt
`GET /task`. `POST /task/workload` nhận cùng tham số dưới dạng JSON body.

```bash
curl -s -u "$AUTH" "$BASE/task/workload?groupIdIn=accounting,sales&userIdIn=demo"
# {"totals":{"id":null,"open":16,"unassigned":7,"overdue":0,"oldest":"..."},"orphaned":3,
#  "groups":[{"id":"accounting","open":4,"unassigned":4,"overdue":0,"oldest":"..."},...],
#  "users":[{"id":"demo","open":9,"unassigned":0,"overdue":0,"oldest":"..."}]}
```

`orphaned` là số task chưa ai nhận và không giao cho nhóm nào. Tham số: `groupIdIn`,
`userIdIn`, `tenantIdIn` (phân cách bằng dấu phẩy) và `overdueBefore` (một thời điểm; mặc định
là thời điểm hiện tại).

- Mỗi danh sách nhận tối đa 200 id khác nhau, mỗi id dài tối đa 255 ký tự. Id rỗng (`a,,b`, hoặc
  `""`/`null` trong body), id dài hơn hay nhiều id hơn đều trả về `400`; tham số không có giá trị
  là danh sách rỗng. Id lặp lại chỉ được trả lời một lần, theo thứ tự lần xuất hiện đầu tiên.
- Id được so sánh theo cách cơ sở dữ liệu so sánh. Với collation không phân biệt hoa thường (mặc
  định của SQL Server, MySQL và MariaDB), `sales` cũng đếm các task giao cho `Sales`, đúng như
  `GET /task?candidateGroup=sales`; mục trả về mang id đúng như khi được hỏi. Trên cơ sở dữ liệu
  như vậy, hai id chỉ khác nhau về hoa thường là cùng một nhóm: chỉ nên hỏi một trong hai (nếu
  một id viết đúng như giá trị đã lưu thì chỉ id đó nhận các task).
- `501` nghĩa là cấu hình MyBatis của engine thiếu các câu lệnh workload: ứng dụng nào thay
  `mappings.xml` mặc định bằng bản riêng phải thêm
  `<mapper resource="dev/fasti/engine/workload/TaskWorkload.xml" />` vào đó. Khi đó giao diện web
  đếm theo từng nhóm và từng người bằng `GET /task/count`, như với máy chủ chưa có endpoint này
  (`404`/`405`).

## Biến

```bash
curl -s -u "$AUTH" "$BASE/process-instance/$PI/variables"

curl -s -u "$AUTH" -X PUT -H 'Content-Type: application/json' \
  "$BASE/process-instance/$PI/variables/note" \
  -d '{"value":"Deliver during office hours","type":"String"}'    # 204

curl -s -u "$AUTH" "$BASE/process-instance/$PI/variables/note"

# tìm instance theo biến
curl -s -u "$AUTH" -H 'Content-Type: application/json' "$BASE/process-instance" \
  -d '{"variables":[{"name":"note","operator":"like","value":"%office%"}]}'
```

Kiểu hay dùng: `String`, `Integer`, `Long`, `Double`, `Boolean`, `Date`
(`2026-11-19T10:00:00.000+0000`), `Json`. Nội dung JSON phải là UTF-8; trên Windows (Git Bash,
PowerShell) dữ liệu có dấu nên đặt trong tệp UTF-8 (không BOM) và gửi bằng
`--data-binary @body.json`; trong PowerShell gọi `curl.exe` (không phải bí danh `curl`) và đặt
`'@body.json'` trong dấu nháy.

## Bình luận cho cả process instance (Fasti)

REST API của Camunda 7 chỉ cho thêm bình luận qua một task (Java API thì đã ghi được bình luận
cho process instance). Fasti thêm endpoint REST ghi bình luận cho chính process instance; engine kiểm tra người gọi có quyền cập nhật instance đó.

```bash
curl -s -u "$AUTH" -H 'Content-Type: application/json' \
  "$BASE/process-instance/$PI/comment/create" \
  -d '{"message":"Customer called, approve today"}'

curl -s -u "$AUTH" "$BASE/process-instance/$PI/comment"
```

Chỉ ghi được bình luận khi instance còn đang chạy (`POST` trả `404` sau khi instance kết thúc);
`GET` vẫn đọc được bình luận của instance đã kết thúc.

## External task (fetchAndLock)

Worker lấy việc theo topic, khóa trong `lockDuration` mili giây, rồi báo hoàn thành. Với
`asyncResponseTimeout`, yêu cầu chờ (long polling) tới khi có việc hoặc hết thời gian.

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

Khi xử lý lỗi: `POST /external-task/{id}/failure` (worker gửi số lần thử còn lại trong `retries`;
`0` tạo incident) hoặc
`POST /external-task/{id}/bpmnError`; gia hạn khóa: `POST /external-task/{id}/extendLock`. Xem
tài liệu tham chiếu để biết nội dung yêu cầu.

## Message

```bash
curl -s -u "$AUTH" -H 'Content-Type: application/json' "$BASE/message" \
  -d '{"messageName":"PaymentConfirmed","businessKey":"ORD-1001",
       "processVariables":{"paidAt":{"value":"2026-11-19T10:00:00.000+0000","type":"Date"}},
       "resultEnabled":true}'
```

Với `resultEnabled` kết quả liệt kê execution đã nhận message. Không instance nào chờ message đó
thì nhận `400`.

## Lịch sử

```bash
curl -s -u "$AUTH" "$BASE/history/process-instance/$PI"    # "state":"COMPLETED"
```

## Client external task bằng Java

Client Java và JavaScript dưới đây thay cho worker bằng `curl` ở trên. Để thấy chúng xử lý việc,
hãy bắt đầu một instance mới và hoàn thành task "Approve order" của nó trước, để có external
task đang chờ ở topic `charge-card`.

<!-- update with the Maven Central version at release -->
Artifact `dev.fasti.platform:fasti-external-task-client` (thay cho
`org.camunda.bpm:camunda-external-task-client`; package `org.camunda.bpm.client` giữ nguyên).
Chưa có trên Maven Central: hãy build từ mã nguồn
(`./mvnw -pl clients/java/client -am install -DskipTests`) và đặt `fasti.version` là
`1.1.0`.

```xml
<dependency>
  <groupId>dev.fasti.platform</groupId>
  <artifactId>fasti-external-task-client</artifactId>
  <version>${fasti.version}</version>
</dependency>
<!-- client cần một bản cài JAXB lúc chạy -->
<dependency>
  <groupId>com.sun.xml.bind</groupId>
  <artifactId>jaxb-impl</artifactId>
  <version>4.0.9</version>
</dependency>
```

Thiếu bản cài JAXB thì `ExternalTaskClient.create()...build()` dừng với
`NoClassDefFoundError: jakarta/xml/bind/JAXBException` (giống client gốc 7.24).

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

Với Spring Boot có starter `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-external-task-client`.

## Client external task bằng JavaScript

Fasti không có client JavaScript riêng. Gói npm
[`camunda-external-task-client-js`](https://www.npmjs.com/package/camunda-external-task-client-js)
(Apache-2.0, do Camunda phát hành) dùng đúng REST API này và chạy được với Fasti; đã thử với bản
3.1.0 trên Node.js 22. Gói là ES module, nên đặt tên tệp `.mjs` hoặc `"type": "module"`.

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

Với bearer token, truyền một interceptor tự thêm header `Authorization`.

## Khác gì so với Camunda 7

| Chủ đề | Camunda 7.24 (Run, image cũ) | Fasti |
|---|---|---|
| Xác thực `/engine-rest` | tắt | **bật** (Basic, hoặc bearer khi bật OIDC); phân quyền bật |
| Endpoint mới | — | `POST /process-instance/{id}/comment/create` (bình luận cho cả instance); `GET /process-instance/{id}/comment` có sẵn trả về các bình luận đó; `GET`/`POST /task/workload` (task đang mở theo nhóm và assignee) |
| Đường dẫn web app | `/camunda/...` | `/fasti/...` (`/camunda/...` chuyển hướng). REST của engine vẫn ở `/engine-rest` |
| Expression trong query | mọi expression | allow-list (hàm thời gian, người dùng hiện tại); expression khác bị từ chối với `400`; xem [hướng dẫn chuyển đổi](../migrate-from-camunda-7.vi.md#cái-gì-khác) |
| Attachment, comment của task | một số endpoint không kiểm quyền | luôn kiểm quyền: có thể nhận `403` |
| Request không hợp lệ | một số trả `500` | `400` cho tài nguyên deploy không parse được, biến ở định dạng Java serialization bị cấm, tham số query sai kiểu và mật khẩu vi phạm chính sách mật khẩu |
| OpenAPI | Swagger UI (`--swaggerui`) | không có Swagger UI; xem bên dưới |

**OpenAPI.** Image không phục vụ tài liệu OpenAPI (`/engine-rest/openapi.json` trả `404`).
Tệp `openapi.json` (OpenAPI 3.0, đã gồm endpoint bình luận mới) nằm trong artifact
`dev.fasti.platform:fasti-engine-rest-openapi`; khi build từ mã nguồn, tệp ở
`engine-rest/engine-rest-openapi/target/generated-sources/openapi-json/openapi.json`. Dùng tệp đó
để sinh client hoặc mở bằng công cụ OpenAPI bất kỳ.

