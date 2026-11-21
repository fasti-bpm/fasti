[English](mcp.md) | Tiếng Việt

# MCP server cho trợ lý AI

Fasti BPM có thể phục vụ một máy chủ [Model Context Protocol](https://modelcontextprotocol.io)
(MCP) để các trợ lý và agent AI (Claude, ChatGPT, Copilot, Cursor và các MCP client khác) xem
định nghĩa quy trình, process instance, task, sự cố (incident) và quyết định DMN, và nếu bạn cho
phép thì khởi động quy trình, xử lý task và chạy lại job lỗi. Máy chủ **tắt theo mặc định**. Khi
bật, mọi yêu cầu đều phải đăng nhập và mọi công cụ (tool) chạy dưới danh nghĩa người dùng đó,
qua các kiểm tra phân quyền của engine: trợ lý thấy và làm đúng những gì người dùng được thấy
và làm trong Cockpit và Tasklist, không hơn.

Máy chủ dùng MCP 2025-11-25 (cũng nhận 2025-06-18 và 2025-03-26) qua giao thức truyền
Streamable HTTP tại `/mcp`, xây dựng trên MCP Java SDK chính thức.

## Bật máy chủ

| Bản phân phối | Cách bật |
|---|---|
| Run | khởi động với `--mcp` (`./start.sh --mcp`, `start.bat --mcp`); được thêm vào cùng các thành phần khác |
| Container image | `FASTI_MCP_ENABLED=true` (hoặc tham số `--mcp`) |
| Spring Boot | thêm `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-mcp` và đặt `fasti.mcp.enabled=true` |

Các công cụ thay đổi dữ liệu có công tắc riêng thứ hai: `fasti.mcp.write-tools.enabled=true`
(`FASTI_MCP_WRITE_TOOLS_ENABLED=true` trong image). Không bật thì máy chủ chỉ đọc.

Trong ứng dụng Spring Boot nhúng có cấu hình Spring Security riêng, hãy cho các yêu cầu tới
`/mcp` đi qua (`permitAll`): máy chủ MCP tự kiểm tra thông tin đăng nhập.

## Kết nối client

Địa chỉ là `http(s)://<host>:<port>/mcp`. Client gửi thông tin đăng nhập của một người dùng
engine trong header `Authorization`: `Basic <base64(user:password)>`, hoặc với OIDC (bên dưới)
`Bearer <access token>`. Đa số client nhận cấu hình như sau; tên file và tên khóa gốc
(`mcpServers`, `servers`) tùy từng client:

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

Ví dụ với Claude Code:

```bash
claude mcp add --transport http fasti https://bpm.example.com/mcp \
  --header "Authorization: Basic $(printf '%s' 'agent:secret' | base64)"
```

Mỗi trợ lý nên dùng một người dùng kỹ thuật riêng, chỉ có những quyền cần thiết (ví dụ `READ`
trên định nghĩa quy trình của một phòng ban), không dùng tài khoản quản trị.

### Thử bằng tay

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

Máy chủ không giữ phiên (stateless): không cấp `Mcp-Session-Id`, trả lời mỗi yêu cầu bằng một
phản hồi JSON và từ chối `GET` (không có luồng sự kiện từ máy chủ tới client) với mã 405.

## Mô hình bảo mật

- **Xác thực ở mọi yêu cầu.** HTTP basic với người dùng của engine (kèm cơ chế khóa tạm sau
  nhiều lần đăng nhập sai của engine), hoặc bearer token. Không có truy cập ẩn danh, kể cả khi
  REST API của Run chạy không xác thực. `fasti.mcp.basic-auth-enabled=false` chỉ để lại bearer
  token.
- **Bearer token (OIDC).** Với module Spring Security (`--oauth2` trong Run) và
  `fasti.security.oidc.rest.bearer-enabled=true`, máy chủ MCP nhận cùng access token như REST
  API, kiểm tra cùng cách (chữ ký, issuer, audience, hạn dùng) và ánh xạ ra cùng người dùng, nhóm
  và tenant (`fasti.security.oidc.rest.*`, xem [single-sign-on.vi.md](single-sign-on.vi.md)).
  Khi đó máy chủ cũng phục vụ metadata tài nguyên được bảo vệ OAuth 2.0 (RFC 9728) tại
  `/.well-known/oauth-protected-resource/mcp` và ghi địa chỉ này trong `WWW-Authenticate`, để
  MCP client tìm được nhà cung cấp danh tính. Token phải có một trong các audience đã cấu hình.
- **Đúng quyền của người dùng, không hơn.** Mỗi lần gọi công cụ đặt người dùng, nhóm và tenant
  đã xác thực lên engine trong suốt lần gọi đó và xóa đi sau đó. Truy vấn chỉ trả về những gì
  người dùng được đọc; đọc hoặc thay đổi khi không có quyền sẽ nhận lỗi công cụ kèm lý do của
  engine, không bao giờ nhận dữ liệu. Hãy bật kiểm tra phân quyền
  (`camunda.bpm.authorization.enabled=true`, mặc định của container image); nếu tắt, mọi người
  dùng đã xác thực đọc được mọi thứ và máy chủ ghi cảnh báo khi khởi động.
- **Chỉ đọc, trừ khi được bật.** Khởi động, nhận, trả lại, hoàn thành task và chạy lại job chỉ
  có khi `fasti.mcp.write-tools.enabled=true`; không bật thì chúng không xuất hiện trong danh
  sách. Công cụ ghi chỉ hành động cho chính người gọi: task được nhận cho người gọi, và chỉ task
  đang giao cho người gọi mới trả lại hoặc hoàn thành được. Quy tắc bốn mắt
  ([four-eyes.vi.md](four-eyes.vi.md)) do engine bắt buộc; khi bị từ chối, công cụ trả lỗi kèm
  lý do.
- **Không chạy mã của người gọi.** Không công cụ nào triển khai mô hình, chạy script hay tính
  biểu thức do người gọi đưa vào. Biến truyền cho công cụ ghi chỉ là giá trị đơn giản (chuỗi,
  số, boolean, null), không phải đối tượng. Tính một quyết định DMN chỉ chạy các biểu thức của
  quyết định đã triển khai.
- **Kiểm toán.** Thay đổi đi qua các thao tác bình thường của engine dưới danh nghĩa người dùng,
  nên lịch sử, nhật ký thao tác (user operation log) và nhật ký kiểm toán nếu bật
  ([audit-trail.vi.md](audit-trail.vi.md)) đều ghi người dùng. Các lần gọi công cụ ghi cũng
  được ghi log (`dev.fasti.spring.boot.mcp`, mức INFO).
- **Tối thiểu hóa dữ liệu.** Danh sách được phân trang (mặc định 25, tối đa 100 mỗi lần gọi).
  Biến có tên chứa `password`, `secret`, `token`, `credential`, `apikey` hoặc `api_key` được
  che, không trả giá trị. Giá trị dài (2000 ký tự), mô hình (200.000) và stack trace (8000) bị
  cắt và đánh dấu. Giá trị nhị phân và file chỉ được mô tả, không trả nội dung. Đối tượng được
  trả ở dạng đã tuần tự hóa (JSON, XML); đối tượng tuần tự hóa kiểu Java không bao giờ được giải
  tuần tự hóa, chỉ nêu tên kiểu.
- **Origin và DNS rebinding.** Yêu cầu có header `Origin` không nằm trong
  `fasti.mcp.allowed-origins` bị từ chối với 403, đúng yêu cầu của đặc tả MCP; MCP client chạy
  ngoài trình duyệt không gửi `Origin`. `fasti.mcp.allowed-hosts` kiểm tra thêm header `Host`
  (nếu sai: 421). Thân yêu cầu giới hạn 256 KB.
- **Địa chỉ lắng nghe.** Nếu chỉ dùng trên một máy, hãy cho máy chủ lắng nghe ở loopback
  (`server.address=127.0.0.1`) hoặc chỉ publish cổng container trên `127.0.0.1`. Khi dùng qua
  mạng, đặt sau TLS (reverse proxy hoặc `server.ssl.*`): thông tin basic và token không được đi
  dưới dạng rõ.
- **Nội dung không tin cậy.** Dữ liệu quy trình (business key, biến, tên task, thông báo sự cố)
  được trả cho trợ lý như dữ liệu. Văn bản trong đó có thể tìm cách điều khiển trợ lý (prompt
  injection). Hãy tắt công cụ ghi ở nơi trợ lý đọc dữ liệu do người khác nhập, hoặc để client
  hỏi người dùng trước mỗi lần gọi công cụ ghi.

## Công cụ

| Công cụ | Chức năng | Quyền (engine) |
|---|---|---|
| `get_engine_info` | tên engine, phiên bản, mức lịch sử, phân quyền có bật không, người gọi cùng nhóm và tenant, các giới hạn | – |
| `list_process_definitions` | định nghĩa quy trình đã triển khai, mặc định bản mới nhất; lọc theo key, tên, tenant | `READ` trên định nghĩa |
| `get_process_definition` | một định nghĩa theo id hoặc key (và phiên bản), số instance đang chạy; BPMN XML khi yêu cầu | `READ` |
| `list_process_instances` | instance đang chạy theo định nghĩa, business key, trạng thái, sự cố | `READ` trên instance hoặc `READ_INSTANCE` |
| `get_process_instance` | một instance: hoạt động đang chạy, sự cố, biến (đang chạy) hoặc dữ liệu lịch sử và biến (đã kết thúc); các hoạt động đã chạy khi yêu cầu | như trên, lịch sử: `READ_HISTORY` |
| `list_historic_process_instances` | lịch sử instance theo định nghĩa, business key, trạng thái, thời gian bắt đầu/kết thúc, người khởi động | `READ_HISTORY` |
| `list_tasks` | task đang mở: của tôi, tôi nhận được, theo người được giao, nhóm ứng viên, instance, định nghĩa, hoạt động, tên | `READ` trên task hoặc `READ_TASK` |
| `get_task` | một task với form key, người dùng và nhóm ứng viên, biến | như trên |
| `list_incidents` | sự cố đang mở theo instance, định nghĩa, loại, hoạt động | `READ` trên instance |
| `get_incident` | một sự cố kèm job lỗi (số lần thử lại, stack trace) hoặc external task (chi tiết lỗi) | `READ` trên instance |
| `list_decision_definitions` | quyết định DMN đã triển khai; DMN XML khi yêu cầu | `READ` trên quyết định |
| `evaluate_decision` | tính một quyết định với giá trị đầu vào đơn giản; không đổi dữ liệu quy trình, engine ghi lần tính vào lịch sử | `CREATE_INSTANCE` trên quyết định |
| `start_process_instance` *(ghi)* | khởi động instance theo id hoặc key của định nghĩa, kèm business key và biến | `CREATE` trên process instance, `CREATE_INSTANCE` trên định nghĩa |
| `claim_task` *(ghi)* | nhận task cho người gọi | `TASK_WORK` hoặc `UPDATE` |
| `unclaim_task` *(ghi)* | trả lại task đang giao cho người gọi | `TASK_WORK` hoặc `UPDATE` |
| `complete_task` *(ghi)* | hoàn thành task đang giao cho người gọi, kèm biến; trả về các task mở tiếp theo | `TASK_WORK` hoặc `UPDATE` |
| `retry_failed_job` *(ghi)* | đặt lại số lần thử cho job hoặc external task lỗi, theo id sự cố hoặc id job (1–10) | `RETRY_JOB` hoặc `UPDATE` trên instance (`UPDATE_INSTANCE` trên định nghĩa) |

`evaluate_decision` dùng được mà không cần công tắc ghi: nó không thay đổi dữ liệu quy trình,
task hay nghiệp vụ, và quyền của nó (`CREATE_INSTANCE` trên định nghĩa quyết định) phải được cấp
có chủ đích. Chú thích công cụ đánh dấu nó không phải chỉ đọc, nên client hỏi trước các lệnh gọi
không chỉ đọc sẽ hỏi trước khi gọi nó.

Tài nguyên (template): `fasti://process-definitions/{id}/bpmn` và
`fasti://decision-definitions/{id}/dmn` trả về XML của mô hình đã triển khai (cần `READ`).

## Cấu hình

| Thuộc tính | Mặc định | Ý nghĩa |
|---|---|---|
| `fasti.mcp.enabled` | `false` | bật máy chủ MCP |
| `fasti.mcp.write-tools.enabled` | `false` | cung cấp các công cụ thay đổi dữ liệu |
| `fasti.mcp.path` | `/mcp` | đường dẫn của endpoint |
| `fasti.mcp.basic-auth-enabled` | `true` | nhận thông tin HTTP basic của người dùng engine |
| `fasti.mcp.allowed-origins` | (không có) | origin được gọi từ trình duyệt, ví dụ `http://localhost:*` |
| `fasti.mcp.allowed-hosts` | (không kiểm tra) | giá trị header `Host` được chấp nhận, ví dụ `localhost:*,bpm.example.com` |
| `fasti.mcp.max-request-size` | `256KB` | kích thước thân yêu cầu tối đa |
| `fasti.mcp.default-results`, `fasti.mcp.max-results` | `25`, `100` | số mục mỗi trang của công cụ danh sách và giới hạn trên |
| `fasti.mcp.max-variables` | `100` | số biến trả về cho mỗi instance hoặc task |
| `fasti.mcp.max-value-length` | `2000` | số ký tự của một giá trị trả về |
| `fasti.mcp.max-input-variables`, `fasti.mcp.max-input-value-length` | `50`, `10000` | số biến công cụ ghi nhận, số ký tự mỗi giá trị chuỗi |
| `fasti.mcp.max-xml-length` | `200000` | số ký tự của mô hình BPMN hoặc DMN trả về |
| `fasti.mcp.max-stacktrace-length` | `8000` | số ký tự của stack trace trả về |
| `fasti.mcp.masked-variable-names` | `password, secret, token, credential, apikey, api_key` | phần tên biến có giá trị bị che |

Trong container image, mọi thuộc tính đều đặt được bằng biến môi trường, ví dụ
`FASTI_MCP_ALLOWED_ORIGINS=http://localhost:6274`.

## Giới hạn

- Giao thức không giữ phiên: không có phiên, không có thông điệp do máy chủ chủ động gửi (tiến
  độ, thông báo thay đổi, sampling), không có luồng sự kiện qua `GET`.
- Một engine: các công cụ dùng process engine mặc định của ứng dụng.
- Không có CORS: client chạy trong trình duyệt ở origin khác không gọi được endpoint.
- Bản phân phối Tomcat và WildFly không có máy chủ MCP; hãy dùng Run, container image hoặc
  Spring Boot starter.
- Biến truyền cho công cụ ghi là giá trị đơn giản; không nhận đối tượng JSON, danh sách, file
  hay ngày giờ.
