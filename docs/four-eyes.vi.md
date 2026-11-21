[English](four-eyes.md) | Tiếng Việt

# Kiểm soát bốn mắt cho bước phê duyệt

> **Có từ 1.1.0, chưa có trong bản phát hành 1.0.1.** Tính năng này đã có trên nhánh `main` và sẽ
> được phát hành cùng Fasti 1.1.0. Bản phát hành 1.0.1 và image 1.0.1 đã công bố chưa có tính năng
> này; các thiết lập của nó không có tác dụng ở đó.

Trong các ngành được quản lý chặt, một phê duyệt chỉ có giá trị khi do người khác với người
đã chuẩn bị công việc thực hiện. Fasti BPM cho phép mô hình khai báo điều đó trên tác vụ
người dùng phê duyệt, và engine bắt buộc tuân thủ: người đã hoàn thành một trong các bước
trước được nêu tên, trong cùng một process instance, không thể hoàn thành bước phê duyệt.
Giao diện web báo trước điều này để không ai phải thử mới biết.

## Khai báo quy tắc

Thêm thuộc tính mở rộng `fasti:fourEyes` vào tác vụ người dùng làm nhiệm vụ kiểm tra, với id
của các tác vụ người dùng trước đó mà nó kiểm tra, ngăn cách bằng dấu phẩy:

```xml
<bpmn:userTask id="approveContract" name="Phê duyệt hợp đồng" camunda:candidateGroups="legal">
  <bpmn:extensionElements>
    <camunda:properties>
      <camunda:property name="fasti:fourEyes" value="draftContract, reviewContract" />
    </camunda:properties>
  </bpmn:extensionElements>
</bpmn:userTask>
```

- Các bước được nêu phải là tác vụ người dùng của cùng quy trình (kể cả bên trong
  sub-process nhúng). Tên không hợp lệ, hoặc danh sách rỗng, làm việc triển khai thất bại với
  lỗi phân tích chỉ rõ tác vụ.
- Một tác vụ có thể tự nêu chính nó: khi đó mỗi lần hoàn thành nó cần một người khác, ví dụ
  "hai người khác nhau phê duyệt" với tác vụ multi-instance song song, hoặc một bước lặp lại
  trong vòng lặp.
- Nhiều tác vụ người dùng trong một quy trình có thể có quy tắc, và một tác vụ có thể vừa
  được kiểm tra vừa kiểm tra (soạn thảo → rà soát kiểm tra soạn thảo → phê duyệt kiểm tra rà
  soát).
- Trong trình soạn mô hình của giao diện web, bảng thuộc tính của tác vụ người dùng có mục
  **Kiểm soát bốn mắt** với một ô chọn cho mỗi tác vụ người dùng của quy trình và một ô cho
  chính tác vụ đó. Bước kiểm tra trước khi triển khai từ chối những gì engine sẽ từ chối.

Namespace `camunda` và `camunda:property` là phần mở rộng chuẩn của Camunda 7, nên mô hình
vẫn đọc được bằng mọi công cụ Camunda 7; không có namespace XML mới.

## Ai đã "làm" một bước

Một người đã làm một bước khi họ **hoàn thành** bước đó, với tư cách

- người dùng đã xác thực thực hiện việc hoàn thành (kể cả khi không nhận việc trước), hoặc
- **người được giao** (assignee) của bước tại thời điểm hoàn thành (ví dụ khi quản lý hoàn
  thành thay cho họ).

Cả hai đều được tính, đây là cách hiểu chặt hơn. Một bước kết thúc theo cách khác (bị hủy bởi
boundary event, bị xóa cùng instance, kết thúc bằng BPMN error) thì không tính là đã làm.

Với [người thay thế khi vắng mặt](absence.vi.md), người thay thế đã giữ và hoàn thành bước là
người đã làm bước đó, không phải người vắng mặt.

## Engine từ chối những gì

Khi một tác vụ người dùng có quy tắc sắp được **hoàn thành**, **gửi biểu mẫu**, hoặc kết thúc
bằng **BPMN error** hay **escalation**, engine xét hai người: người dùng đã xác thực và người
được giao của tác vụ. Nếu một trong hai đã làm một bước được kiểm tra của cùng process
instance, engine từ chối bằng `FourEyesViolationException` (một `AuthorizationException`), và
không có gì thay đổi. Nếu không có ai trong hai người đó trong khi một bước được kiểm tra đã
được làm, engine cũng từ chối, vì không biết ai là người phê duyệt.

Cố ý cho phép:

- **Nhận việc và giao việc** không bị từ chối: quy tắc giao việc (ví dụ
  `camunda:assignee="${initiator}"`) chạy trong lúc bước trước hoàn thành và không được làm
  bước đó thất bại. Danh sách việc cảnh báo thay vào đó, và việc hoàn thành bị từ chối.
- **Trả lại** một tác vụ được ủy quyền (biểu mẫu của nó, hoặc `resolve`) chuyển nó về chủ sở
  hữu, không phải là hoàn thành.
- **Không có quyền vượt cho quản trị viên**. Thành viên `camunda-admin` cũng bị từ chối như mọi
  người. Nếu không còn ai được phép, hãy giao lại tác vụ cho người chưa làm các bước được
  kiểm tra, hoặc thay đổi instance bằng process instance modification.

Phạm vi: một process instance. Một quy trình được gọi (call activity) là một process instance
riêng; quy tắc của nó nêu các bước của chính nó.

## Trong giao diện web

- **Chi tiết việc**: nếu bạn đã hoàn thành một bước được kiểm tra, một thông báo cho biết "Bạn
  đã hoàn thành một bước trước đó của hồ sơ này mà việc này kiểm tra (…), nên một người khác
  phải hoàn thành việc này." và biểu mẫu không gửi được. Nếu người được giao đã làm, thông báo
  nêu tên họ và đề nghị giao việc cho người khác. Với việc có quy tắc nhưng không chặn bạn,
  một dòng ngắn cho biết ai không được hoàn thành.
- Khi engine từ chối (ví dụ từ một cửa sổ khác), lý do được giải thích bằng lời thay vì báo
  thiếu quyền.
- **Trình soạn mô hình**: mục **Kiểm soát bốn mắt** của tác vụ người dùng (xem trên).

## REST API

| Phương thức và đường dẫn | Chức năng |
|---|---|
| `GET /task/{id}/four-eyes` | Cho người dùng của yêu cầu: `enabled`, `guarded`, `earlierSteps` (`id`, `name`), `userExcluded` và `userSteps`, `assignee`, `assigneeExcluded` và `assigneeSteps`, và `blocked` (engine có từ chối việc hoàn thành lúc này không). Cần quyền `READ` trên tác vụ; `404` nếu tác vụ không tồn tại |
| `POST /task/{id}/complete`, `/submit-form`, `/bpmnError`, `/bpmnEscalation` | Bị từ chối với `403` và `{"type": "FourEyesViolationException", "message": "Four-eyes control: …"}` |
| Mọi yêu cầu đặt hoặc xóa biến `fasti_completedBy_…` của process instance (`PUT`, `POST`, `DELETE` trên biến của tác vụ, process instance hoặc execution; `variables` của `complete`, `submit-form`, message, modification, …) | Bị từ chối với `403` và `{"type": "AuthorizationException", "message": "Four-eyes control: variable … is written by the engine only; …"}`, xem [Giới hạn](#limits) |

Java: `FourEyes.of(processEngine)` trả về `FourEyesService` với `isEnabled()` và
`getStatus(taskId)`; ngoại lệ là `dev.fasti.engine.foureyes.FourEyesViolationException` kèm
tác vụ, những người bị loại trừ và các bước họ đã làm.

## Bật tính năng

| Bản phân phối | Thiết lập |
|---|---|
| Fasti BPM Run | Bật trong `default.yml` và `production.yml` (`fasti.four-eyes.enabled: true`) |
| Container image | Bật; `FASTI_FOUR_EYES_ENABLED=false` để tắt |
| Tomcat, WildFly | Bật: plugin `dev.fasti.engine.foureyes.FourEyesPlugin` trong `bpm-platform.xml` / `standalone.xml` |
| Spring Boot starter | `fasti.four-eyes.enabled=true` (mặc định tắt, như mọi tính năng engine của Fasti trong starter) |

Vì sao bật sẵn trong các bản phân phối: plugin chỉ tác động lên tác vụ người dùng có mô hình
khai báo quy tắc, và một kiểm soát đã khai báo mà không được thực thi là lỗi tệ hơn. Không có
plugin, thuộc tính chỉ là một thuộc tính mở rộng bình thường: không kiểm tra, không ghi nhận,
và `GET /task/{id}/four-eyes` trả về `enabled: false`.

Chi phí: mỗi lần hoàn thành tác vụ được kiểm tra hoặc làm nhiệm vụ kiểm tra, một lần đọc biến
của process instance (thường đã được nạp), một truy vấn historic task instance và một truy vấn
operation log của instance đó (đều trên cột có index), và một lần cập nhật thêm dòng của
process instance.

## Lưu trữ và tương thích

**Lược đồ cơ sở dữ liệu giữ nguyên như Camunda 7.24.** Plugin ghi người đã hoàn thành một bước
được kiểm tra vào biến của process instance, `fasti_completedBy_<id của bước>`, một mảng JSON
các user id (`["alice","bob"]`). Ở nơi có lưu lịch sử, plugin còn đọc người được giao của các
historic task instance đã hoàn thành (mức `activity` trở lên) và người dùng của các mục
`Complete` trong user operation log (mức `full`). Hợp của cả ba nguồn được tính.

- **Mọi mức lịch sử đều hoạt động**, kể cả `none`, vì biến tồn tại ở mọi mức. Với `none`, biến
  là bản ghi duy nhất.
- **Node Camunda 7.24 và node không có plugin** trên cùng cơ sở dữ liệu không thực thi quy tắc
  và không ghi biến. Một bước được hoàn thành trên node như vậy vẫn được tính trên node Fasti
  ở nơi có lưu lịch sử (assignee trong lịch sử, hoặc operation log ở mức `full`). Hãy bật
  plugin trên mọi node.
- **Quay về Camunda 7.24**: các biến còn lại như biến chuỗi bình thường; không có gì khác được
  ghi.
- **Triển khai khi chưa có plugin**: một mô hình có quy tắc sai được triển khai khi plugin tắt
  sẽ không bị từ chối khi node Fasti nạp nó, để các instance tiếp tục chạy; các tên sai bị bỏ
  qua và được ghi cảnh báo vào log.
- **Đồng thời**: hoàn thành cùng lúc một bước được kiểm tra và bước kiểm tra nó trong cùng
  instance (nhánh song song) không thể cùng commit: cả hai đều cập nhật process instance, nên
  một bên thất bại với `OptimisticLockingException` và, khi thử lại, bị từ chối. Các lần hoàn
  thành bước được kiểm tra của một instance cũng được tuần tự hóa như vậy.

<a id="limits"></a>

Giới hạn:

- **Bản ghi là biến dành riêng.** Trên node có plugin, biến tên `fasti_completedBy_…` trong
  process instance chỉ do cơ chế kiểm soát ghi. Mọi thao tác khác đặt hoặc xóa biến này đều bị
  từ chối (`403`, trong Java là `ReservedVariableException`, một `AuthorizationException`), dù
  đi qua đường nào: biến của tác vụ, process instance hoặc execution (REST `PUT`, `POST`,
  `DELETE`; `setVariable`, `removeVariable` và các biến thể), biến truyền cho `complete`, form,
  message, signal, modification, batch hoặc external task, instance mới khởi tạo kèm biến như
  vậy, và delegate, listener, script, mapping của mô hình. Không quyền nào cho phép việc này,
  kể cả quản trị viên. (Trước quy tắc này, bất kỳ candidate hay assignee nào cũng làm rỗng được
  bản ghi qua biến của tác vụ của mình.)
  - Đặt bản ghi bằng đúng giá trị đang có thì được chấp nhận và không thay đổi gì, nên form
    và worker gửi lại mọi biến đã đọc vẫn chạy.
  - Các bản sao toàn bộ biến bỏ qua bản ghi: call activity với `variables="all"` (cả hai
    chiều) và việc khởi động lại (restart) process instance. `removeVariables()` trong
    delegate giữ lại bản ghi.
  - Biến cục bộ của tác vụ có tên như vậy không phải là bản ghi và không bị hạn chế.
- **Điều engine không ngăn được:** thay đổi trực tiếp trong cơ sở dữ liệu, và node không có
  plugin (Camunda 7.24, hoặc plugin bị tắt) dùng chung cơ sở dữ liệu, nơi biến này là biến
  thường. Ở nơi có lưu lịch sử, dữ liệu lịch sử vẫn được tính khi biến bị mất hoặc hỏng; với
  lịch sử `none` thì không.
- Biến chứa mỗi người dùng một lần; hơn khoảng một trăm người khác nhau hoàn thành cùng một
  bước được kiểm tra trong một instance có thể vượt kích thước biến chuỗi trên Oracle (2000 ký
  tự).
- Human task của CMMN không được hỗ trợ.
- Các đường hoàn tất dành cho quản trị ngoài task service (process instance modification, xóa
  instance) không phải là hoàn thành và không bị kiểm tra.
