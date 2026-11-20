[English](absence.md) | Tiếng Việt

# Người thay thế khi vắng mặt

Công việc không nên phải chờ người đang nghỉ phép. Người dùng ghi lại lịch vắng mặt (từ, đến,
người thay thế và ghi chú tùy chọn); trong thời gian vắng mặt, công việc (user task) được tạo
cho hoặc giao cho người đó sẽ chuyển sang người thay thế. Nhu cầu này được nêu trên diễn đàn ở
[2788](https://forum.camunda.io/t/2788) và [38199](https://forum.camunda.io/t/38199).

## Điều gì xảy ra khi vắng mặt

- **Công việc của người vắng mặt chuyển sang người thay thế**: khi một user task BPMN hoặc human
  task CMMN được tạo với người vắng mặt là người xử lý (assignee), hoặc được giao cho họ sau đó
  (task API, ủy quyền, giao diện web), engine giao nó cho người thay thế.
- **Người thay thế cũng vắng thì đi tiếp**: nếu người thay thế cũng đang vắng mặt, người thay
  thế của họ nhận việc, tối đa `max-hops` người (mặc định 5). Người đầu tiên có mặt sẽ nhận. Nếu
  chuỗi quay lại một người đã đi qua, người thay thế không còn tồn tại, hoặc mọi người trong tầm
  đều vắng mặt, công việc ở lại với người vắng mặt (không mất việc).
- **Người dùng ứng viên (candidate user) vắng mặt**: khi tạo công việc, người thay thế của mỗi
  candidate user đang vắng mặt cũng trở thành candidate user; người vắng mặt vẫn giữ vai trò đó
  (`substitute-candidates`, mặc định `true`).
- **Người vắng mặt rõ ràng đang có mặt** khi tự giao việc cho chính mình (nhận việc): trường hợp
  này không bao giờ bị chuyển hướng.
- **Mỗi lần thay thế đều được ghi lại**: công việc có một bình luận như
  "Assigned to bob as substitute for alice, who is absent." và các biến cục bộ
  `fasti_absentAssignee` (người vắng mặt) và `fasti_absenceSubstitute` (người nhận việc). Có thể
  truy vấn các biến này (`POST /task` với
  `{"taskVariables": [{"name": "fasti_absentAssignee", "operator": "eq", "value": "alice"}]}`;
  cú pháp `GET` dạng `name_operator_value` không chứa được dấu gạch dưới trong tên biến) và chúng
  còn lại trong lịch sử (`includeDeleted`). Chúng bị xóa khi công việc rời khỏi người thay thế, ví dụ quay về người vắng mặt sau
  khi họ trở lại.
- **Công việc người dùng đã có trước khi vắng mặt** không tự động chuyển: người dùng (hoặc quản
  trị viên) bàn giao một cách tường minh bằng **Bàn giao ngay công việc đang mở của tôi**. Công
  việc đang tạm dừng (suspended) ở lại. Khi có người dùng đăng nhập, mỗi lần bàn giao được ghi
  vào nhật ký thao tác (operation log) là "Assign".

Chưa hỗ trợ: công việc tạo không thuộc process hay case (`TaskService#newTask`) chỉ được chuyển
bằng bàn giao, và candidate user thêm vào công việc sau khi tạo không có người thay thế.

## Trên giao diện web

- **Tùy chọn → Vắng mặt**: đặt thời điểm bắt đầu (để trống là ngay bây giờ), kết thúc, người
  thay thế (có tìm người dùng) và ghi chú; kết thúc vắng mặt; bàn giao ngay công việc đang mở.
- **Tasklist**: một banner cho biết bạn đang hoặc sắp vắng mặt, và bạn đang thay thế cho ai.
  Thẻ công việc đang giữ thay người vắng mặt hiển thị "Thay *tên*"; chi tiết công việc ghi
  "Giao cho bạn với vai trò người thay thế *tên*".
- **Quản trị → Người dùng → một người dùng → Vắng mặt**: như trên cho bất kỳ người dùng nào,
  dành cho quản trị viên.

## REST API

Tất cả nằm dưới `/engine-rest/user/{id}/absence` (cũng có trong tài liệu OpenAPI):

| Phương thức và đường dẫn | Chức năng |
|---|---|
| `GET /user/{id}/absence` | Lịch vắng mặt (`status`: `none`, `upcoming`, `active`, `ended`), `enabled` và, khi đang hiệu lực, `effectiveSubstituteId` |
| `PUT /user/{id}/absence` | Đặt lịch: `{"from": "...", "until": "...", "substituteId": "bob", "note": "..."}`; `from` không bắt buộc |
| `DELETE /user/{id}/absence` | Kết thúc |
| `POST /user/{id}/absence/handover?maxTasks=500` | Bàn giao ngay công việc đang mở: `{"substituteId", "handedOver", "remaining"}` |
| `GET /user/{id}/absence/substituting` | Các lịch vắng mặt đang hiệu lực mà công việc chuyển sang người dùng này |

Ngày giờ dùng định dạng của REST API (`yyyy-MM-dd'T'HH:mm:ss.SSSZ`, có múi giờ). Engine so sánh
theo đồng hồ của nó ở UTC, nên lịch vắng mặt đúng ở mọi múi giờ.

Kiểm tra dữ liệu (`400`): bắt buộc có thời điểm kết thúc, sau thời điểm bắt đầu và ở tương lai;
người thay thế phải tồn tại và khác người dùng; từ chối người thay thế vắng mặt cùng thời gian mà
chuỗi người thay thế quay lại người dùng; ghi chú tối đa 255 ký tự.

**Phân quyền**: người dùng tự quản lý lịch vắng mặt của mình. Lịch của người khác cần quyền
`READ` (để xem) hoặc `UPDATE` (để sửa, bàn giao) trên người dùng đó (tài nguyên `USER`), là quyền
mà thành viên nhóm `camunda-admin` có. Thao tác bàn giao kiểm tra quyền này, không kiểm tra quyền
trên từng công việc. Khi tắt kiểm tra phân quyền thì, như mọi nơi trong engine, ai cũng làm được.

Java: `Absences.of(processEngine)` trả về `AbsenceService` với các thao tác tương tự.

## Bật tính năng

| Bản phân phối | Cấu hình |
|---|---|
| Fasti BPM Run | Bật trong `default.yml` (`fasti.absence.enabled: true`); tắt trong `production.yml` cho đến khi bạn bỏ chú thích |
| Spring Boot starter | `fasti.absence.enabled=true` (mặc định tắt) |
| Container image | `FASTI_ABSENCE_ENABLED=true` (mặc định tắt) |
| Tomcat, WildFly | Bỏ chú thích plugin `dev.fasti.engine.absence.AbsenceSubstitutionPlugin` trong `bpm-platform.xml` / `standalone.xml` |

Thuộc tính khác: `fasti.absence.max-hops` (mặc định `5`) và `fasti.absence.substitute-candidates`
(mặc định `true`); với Tomcat và WildFly là thuộc tính plugin `maxHops` và `substituteCandidates`.

Vì sao mặc định tắt với engine nhúng và cơ sở dữ liệu có sẵn: cấu hình này thay đổi cách giao
việc, và mọi node trong cụm nên thống nhất. Không có gì thay đổi cho đến khi có người ghi lịch
vắng mặt; chi phí là một truy vấn nhỏ vào user info mỗi lần giao việc.

## Lưu trữ và tương thích

Lịch vắng mặt được lưu dưới dạng user info của identity service (bảng `ACT_ID_INFO`, khóa
`fasti.absence.from`, `fasti.absence.until`, `fasti.absence.substitute`, `fasti.absence.note`,
thời gian là instant ISO-8601 ở UTC). **Schema cơ sở dữ liệu giữ nguyên như Camunda 7.24**:
không thêm bảng hay cột, vẫn quay lui được.

- **Node Camunda 7.24** dùng chung cơ sở dữ liệu bỏ qua lịch vắng mặt (không thay thế) và không
  đụng tới user info. Hãy bật tính năng cho mọi node Fasti cùng lúc.
- **LDAP và identity provider chỉ đọc khác**: không sửa được người dùng qua engine, nhưng user
  info nằm trong cơ sở dữ liệu của engine nên lịch vắng mặt vẫn hoạt động. Người thay thế phải
  là người dùng mà identity provider biết.
- **Đăng nhập một lần (SSO)**: tương tự; người thay thế phải được identity service biết.
- **Không có bảng identity** (`dbIdentityUsed=false` và không có bảng `ACT_ID_*`) thì không lưu
  được lịch vắng mặt: plugin ghi log lý do, REST API trả `enabled: false` kèm lý do, và giao
  diện web báo tính năng không khả dụng.
- Xóa người dùng (qua engine) sẽ xóa lịch vắng mặt cùng các user info khác.
- Thao tác ghi lịch vắng mặt giữ khóa độc quyền của engine (dòng `ACT_GE_PROPERTY` mà thao tác
  deploy khóa), nên hai lần ghi đầu tiên cho cùng một người dùng cùng lúc không bị trộn lẫn. Giống
  như engine khi deploy, Fasti không khóa trên H2 (vốn chỉ dành cho phát triển).
