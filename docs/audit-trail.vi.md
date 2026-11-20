[English](audit-trail.md) | Tiếng Việt

# Nhật ký kiểm toán bất biến

Kiểm toán viên muốn biết ai đã làm gì, khi nào, với hồ sơ nào, và rằng không ai sửa bản ghi đó
về sau. Lịch sử và nhật ký thao tác của engine trả lời câu hỏi đầu, nhưng bất kỳ ai có quyền
truy cập cơ sở dữ liệu đều có thể sửa chúng mà không để lại dấu vết, và việc dọn lịch sử sẽ xóa
chúng. Fasti BPM có thể giữ thêm một **nhật ký kiểm toán**: mỗi bản ghi được nối với bản ghi
trước nó bằng mã băm SHA-256, nên bản ghi bị sửa, bị xóa hay bị chèn thêm sẽ bị phát hiện khi
kiểm tra nhật ký. Ứng dụng không bao giờ sửa hay xóa bản ghi, trừ khi dọn có chủ đích, và việc
dọn đó cũng để lại một bản ghi.

## Những gì được ghi

Bên cạnh lịch sử thông thường (giữ nguyên), plugin biến các sự kiện engine tạo ra thành bản ghi,
trong cùng giao dịch với việc được ghi: nếu lệnh bị rollback thì bản ghi cũng vậy, và nếu không
ghi được bản ghi thì lệnh thất bại.

| Loại (`type`) | Hành động | Thông tin trong `details` |
|---|---|---|
| `process` | `start`, `update` (tạm dừng, kích hoạt), `end` (cả khi bị xóa), `migrate` | trạng thái, người khởi tạo, hoạt động đầu và cuối, lý do xóa, lượt chạy cha |
| `task` | `create`, `assign` (kèm người được giao trước đó), `update`, `complete`, `delete` (kết thúc cùng lượt chạy), `identity-link-add` / `identity-link-delete` (người và nhóm ứng viên, chủ sở hữu) | người được giao, chủ sở hữu, khóa định nghĩa tác vụ, lý do xóa |
| `variable` | `create`, `update`, `delete`, `migrate` | kiểu, tác vụ, execution, phiên bản, và giá trị theo `variable-values` (bên dưới) |
| `deployment` | `create` (kèm SHA-256 của từng tệp), `delete` | nguồn, mã băm của tệp |
| `operation` | mọi mục của nhật ký thao tác người dùng: `Claim`, `Complete`, `SetVariable`, `Create` / `Update` / `Delete` người dùng, nhóm, thành viên, tenant và phân quyền, tạm dừng, số lần thử lại của job, … | mã thao tác, phân loại, thuộc tính, giá trị cũ và mới, chú thích |
| `incident` | `create`, `resolve`, `delete`, `update`, `migrate` | thông báo, hoạt động, cấu hình, trạng thái |
| `decision` | `evaluate` | định nghĩa quyết định, hoạt động |
| `activity` (mặc định tắt) | `start`, `end` của từng hoạt động | kiểu và tên hoạt động |
| `audit` | `checkpoint`, `verify`, `export`, `purge` | những gì nhật ký kiểm toán tự làm, và ai yêu cầu |

Mỗi bản ghi có **người thực hiện** (người dùng đã xác thực, hoặc để trống khi engine tự làm: job
executor, timer, lúc khởi động), **thời điểm** theo đồng hồ engine (mili giây từ năm 1970 UTC),
**engine**, **tenant**, định nghĩa tiến trình, lượt chạy tiến trình và **mã nghiệp vụ** của nó.

Engine tạo ra sự kiện gì phụ thuộc **mức lịch sử**: ở `full` (mặc định của Run và container
image) là tất cả những gì nêu trên; ở `audit` và `activity` không có nhật ký thao tác người dùng
(ai nhận việc, thay đổi người dùng và phân quyền); ở `none` chỉ có triển khai và bản ghi của
chính nhật ký kiểm toán. Plugin ghi cảnh báo khi mức thấp hơn `full`. Nhật ký thao tác cần người
dùng đã xác thực, như trong Camunda 7.

**Giá trị biến** (`fasti.audit.variable-values`):

- `hash` (mặc định): SHA-256 của giá trị đã lưu; khi có khóa ký thì là HMAC-SHA256, để không đoán
  được các giá trị ngắn (có/không, số tiền) từ mã băm. Bản thân giá trị không được lưu, nhưng
  có thể đối chiếu với bản xuất lịch sử về sau.
- `value`: văn bản và số ở dạng rõ (tối đa 4000 ký tự, phần còn lại chỉ dưới dạng mã băm); giá trị
  nhị phân và tuần tự hóa chỉ có mã băm và kích thước.
- `none`: chỉ ghi rằng biến đã thay đổi.

Biến có tên khớp `fasti.audit.masked-variables` (mặc định
`*password*,*secret*,*token*,*credential*,*apikey*,*api_key*,*privatekey*,*private_key*`, không
phân biệt hoa thường, `*` và `?` là ký tự đại diện) được ghi với `"masked": true`, không có giá
trị hay mã băm. Một lần cập nhật ghi giá trị mới; giá trị cũ là giá trị của bản ghi trước đó của
cùng biến (`entityId`).

## Cách phát hiện sửa đổi

Mỗi bản ghi lưu `HASH_ = SHA-256(PREV_HASH_ ‖ dạng chuẩn của bản ghi)`. Dạng chuẩn ghi mỗi trường
trên một dòng (có thoát ký tự, phân biệt `null` với văn bản) và bắt đầu bằng phiên bản
`fasti-audit-v1`. Các bản ghi tạo thành **chuỗi**: vị trí 1, 2, 3, … không có khoảng trống, mỗi bản
ghi chứa mã băm của bản ghi trước. Bảng `FASTI_AUDIT_HEAD` giữ vị trí và mã băm của bản ghi cuối
của từng chuỗi.

**Đồng thời mà không có khóa toàn cục.** Mỗi node ghi vào `fasti.audit.chains` chuỗi của riêng
nó (mặc định 4), tên là `<node id>/<slot>`; một giao dịch dùng slot của luồng (thread) chạy nó.
Khi ghi, chỉ dòng head của chuỗi đó bị khóa (bằng một lệnh `UPDATE`, mọi cơ sở dữ liệu đều hỗ trợ
như nhau), vào cuối lệnh, cho đến khi commit. Vì vậy lệnh trên slot khác và node khác không bao
giờ chờ nhau, còn các lệnh cùng slot chỉ xếp hàng trong khoảnh khắc commit. Một giao dịch lồng
bắt đầu khi giao dịch ngoài của cùng luồng đang giữ một chuỗi sẽ lấy slot khác, nên không thể
chờ chính giao dịch ngoài của nó. Đánh đổi: không có một thứ tự toàn cục duy nhất cho mọi bản
ghi (mỗi chuỗi có thứ tự; giữa các chuỗi thì sắp theo thời gian), và việc kiểm tra phải đọc mọi
chuỗi.

**Node id** (`fasti.audit.node-id`) mặc định là `<tên engine>@<tên máy>`. Hãy đặt cho mỗi node một
id ổn định và duy nhất; hai node cùng id sẽ dùng chung chuỗi, vẫn đúng (khóa head xếp hàng chúng)
nhưng phải chờ nhau.

**Kiểm tra** (`POST /audit/verify`, hoặc nút trong giao diện web) tính lại từng chuỗi, theo từng
trang, và báo cho mỗi chuỗi nó còn toàn vẹn hay không và đứt ở đâu đầu tiên:

| Lý do | Ý nghĩa |
|---|---|
| `hash-mismatch` | bản ghi đã bị sửa sau khi được ghi |
| `previous-hash-mismatch` | bản ghi không nối tiếp bản ghi trước: một trong hai bị sửa, hoặc có bản ghi bị xóa hay chèn vào giữa |
| `sequence-gap` | thiếu vị trí: bản ghi đã bị xóa |
| `missing-start` | chuỗi không bắt đầu từ 1 và không có bản ghi dọn nào nói về các bản ghi trước đó |
| `head-mismatch` | head chỉ tới bản ghi cuối khác: bản ghi ở cuối bị xóa, hoặc head bị sửa |
| `head-missing` | có bản ghi cho một chuỗi không có head |
| `checkpoint-mismatch` | HMAC của một điểm kiểm tra có chữ ký không khớp: chuỗi trước đó đã bị viết lại |

Bản thân việc kiểm tra cũng thành một bản ghi (ai kiểm tra, kết quả).

**Điểm kiểm tra có chữ ký.** Người có quyền ghi vào cơ sở dữ liệu có thể viết lại cả một chuỗi
từ một bản ghi nào đó trở đi, với mã băm đúng, và dời head. Để phát hiện được điều đó, hãy đặt
một khóa ít nhất 32 byte (`fasti.audit.hmac-key`, tốt nhất qua biến môi trường
`FASTI_AUDIT_HMAC_KEY`, hoặc một tệp trong `fasti.audit.hmac-key-file`). Sau mỗi
`checkpoint-interval` bản ghi (mặc định 1000), chuỗi có một bản ghi `checkpoint` chứa
HMAC-SHA256 của vị trí và mã băm trước nó. Khóa không bao giờ vào cơ sở dữ liệu; `keyId` (phần
đầu SHA-256 của khóa) giúp phân biệt các khóa. Mỗi điểm kiểm tra cũng được ghi vào log của ứng
dụng (`Audit trail checkpoint …`); khi log được chuyển sang kho log, đó là một mốc nằm ngoài cơ sở
dữ liệu. Các bản ghi sau điểm kiểm tra cuối chỉ được chuỗi bảo vệ: hãy lưu các mã băm cuối mà
việc kiểm tra báo (hoặc các dòng log) ở ngoài cơ sở dữ liệu để phát hiện việc cắt bớt hay viết
lại chúng về sau.

## Bất biến, thời hạn lưu giữ và dọn

- Ứng dụng chỉ thêm bản ghi. **Việc dọn lịch sử không động tới chúng**: chúng không nằm trong các
  bảng lịch sử, bất kể thời gian sống của lịch sử.
- Bản ghi được giữ cho đến khi bị dọn có chủ đích. `POST /audit/purge`
  `{"before": "…", "exported": true}` (Java: `AuditService#purge`) xóa, theo từng chuỗi, các bản
  ghi từ đầu chuỗi đến bản ghi cuối cùng trước `before`, rồi thêm một bản ghi `purge` nêu mã băm
  của bản ghi cuối bị xóa, để phần còn lại của chuỗi vẫn kiểm tra được. Bản ghi mới hơn
  `fasti.audit.retention-days` (mặc định 365) ngày không bao giờ bị dọn. Hãy xuất trước: bản xuất
  JSON có đủ mọi mã băm, nên phần đã xóa vẫn kiểm tra được ngoại tuyến với bản ghi dọn. Dọn cần
  nhóm quản trị hoặc quyền `DELETE` trên `SYSTEM`.
- `databaseSchemaUpdate=create-drop` không xóa các bảng kiểm toán.

**Gia cố cơ sở dữ liệu.** Chạy ứng dụng bằng một người dùng cơ sở dữ liệu được thêm và đọc
`FASTI_AUDIT_EVENT` nhưng không được sửa, và chỉ được xóa khi đang dọn:

```sql
-- PostgreSQL (tương tự trên Oracle, SQL Server, DB2)
REVOKE UPDATE, DELETE, TRUNCATE ON fasti_audit_event FROM fasti_app;
GRANT SELECT, INSERT ON fasti_audit_event TO fasti_app;
GRANT SELECT, INSERT, UPDATE ON fasti_audit_head TO fasti_app;
-- MySQL / MariaDB
REVOKE UPDATE, DELETE ON fasti.FASTI_AUDIT_EVENT FROM 'fasti_app'@'%';
```

Để chắc chắn hơn, hãy định kỳ xuất nhật ký sang kho chỉ-ghi-một-lần (S3 Object Lock, Azure
immutable blob, băng WORM) và lưu các mã băm cuối ở đó, hoặc sao chép bảng sang một cơ sở dữ
liệu mà ứng dụng không ghi được.

## Trong giao diện web

- **Cockpit → Nhật ký kiểm toán**: các bản ghi, mới nhất trước, lọc theo lượt chạy tiến trình,
  mã nghiệp vụ, người dùng (hoặc `system` cho engine), loại, hành động và thời gian. **Xem** mở
  chi tiết một bản ghi và vị trí của nó trong chuỗi (vị trí, mã băm, mã băm trước). **Xuất CSV**
  / **Xuất JSON** tải phần đang lọc từ máy chủ (cũ nhất trước, tối đa 100.000 bản ghi; lần xuất
  được ghi lại); CSV thêm dấu nháy trước văn bản mà bảng tính có thể chạy như công thức, JSON giữ
  nguyên mọi giá trị như khi băm. **Kiểm tra toàn vẹn** hiện "Toàn vẹn" cùng mã băm cuối của
  từng chuỗi, hoặc một cảnh báo nêu chuỗi, bản ghi và lý do của chỗ đứt đầu tiên.
- **Lịch sử một lượt chạy → Nhật ký kiểm toán**: các bản ghi của lượt chạy đó, cũ nhất trước
  (hiện khi nhật ký kiểm toán đang bật).

Khi không có plugin, trang cho biết nhật ký kiểm toán đang tắt và cách bật.

## REST API

Tất cả nằm dưới `/engine-rest` (cũng có trong tài liệu OpenAPI, nhãn "Audit Trail"):

| Phương thức và đường dẫn | Chức năng |
|---|---|
| `GET /audit` | `enabled` và các thiết lập (`nodeId`, `chains`, `types`, `variableValues`, `maskedVariables`, `signed`, `keyId`, `checkpointInterval`, `retentionDays`, `historyLevel`); không bao giờ có khóa |
| `GET /audit/event` | Các bản ghi, mới nhất trước; lọc `processInstanceId`, `businessKey`, `actor` (`system` cho engine), `type` (phân cách bằng dấu phẩy), `action`, `tenantId`, `processDefinitionKey`, `entityId`, `chainId`, `from`, `to`, `sortOrder` (`desc`, `asc`), phân trang `firstResult`, `maxResults` (tối đa 1000) |
| `GET /audit/event/count` | Số bản ghi khớp bộ lọc |
| `GET /audit/event/export?format=csv\|json` | Phần đang lọc dưới dạng tệp (được ghi lại là `audit`/`export`) |
| `POST /audit/verify?chainId=` | Kiểm tra mọi chuỗi (hoặc một chuỗi): `intact`, `records`, `chains` (với `lastHash`, `checkpointsVerified`, `purgedThrough`, `firstBreak`) và `firstBreak` |
| `POST /audit/purge` | `{"before": "...", "exported": true}`: dọn bản ghi cũ, xem ở trên |

**Phân quyền**: đọc, xuất và kiểm tra cần là thành viên nhóm quản trị (`camunda-admin`) hoặc có
quyền `READ` trên tài nguyên `SYSTEM`; dọn cần nhóm quản trị hoặc `DELETE` trên `SYSTEM`. Tài
nguyên `SYSTEM` có sẵn trong Camunda 7.24 nên không cần loại tài nguyên mới. Khi không kiểm tra
phân quyền, như mọi nơi trong engine, ai cũng được. Trả về `400` khi nhật ký kiểm toán tắt.

Java: `AuditTrail.of(processEngine)` trả về `AuditService` (`createQuery()`, `verify()`,
`recordExport(…)`, `purge(before)`).

## Bật tính năng

| Bản phân phối | Thiết lập |
|---|---|
| Fasti BPM Run | Tắt; bỏ chú thích `fasti.audit` trong `default.yml` / `production.yml` |
| Spring Boot starter | `fasti.audit.enabled=true` (mặc định tắt) |
| Container image | `FASTI_AUDIT_ENABLED=true` (mặc định tắt); khóa trong `FASTI_AUDIT_HMAC_KEY` hoặc `FASTI_AUDIT_HMAC_KEY_FILE` |
| Tomcat, WildFly | Bỏ chú thích plugin `dev.fasti.engine.audit.AuditTrailPlugin` trong `bpm-platform.xml` / `standalone.xml` |

Thiết lập (`fasti.audit.*`; thuộc tính plugin viết kiểu camelCase cho Tomcat và WildFly):

| Thiết lập | Mặc định | Ý nghĩa |
|---|---|---|
| `node-id` | `<engine>@<máy>` | node này trong id của chuỗi |
| `chains` | `4` | số chuỗi mỗi node, ghi song song (1 đến 64) |
| `types` | tất cả trừ `activity` | các loại, phân cách bằng dấu phẩy, hoặc `all` |
| `variable-values` | `hash` | `hash`, `value` hoặc `none` |
| `masked-variables` | xem ở trên | mẫu tên biến không bao giờ được ghi giá trị |
| `hmac-key` / `hmac-key-file` | không có | khóa ký điểm kiểm tra (ít nhất 32 byte) |
| `checkpoint-interval` | `1000` | số bản ghi giữa hai điểm kiểm tra; `0` là không có |
| `retention-days` | `365` | bản ghi mới hơn số ngày này không dọn được |

**Vì sao mặc định tắt ở mọi nơi**: tính năng thêm hai bảng và một lần ghi cho mỗi lệnh tạo lịch
sử, cần chạy trên mọi node của cụm (node không bật sẽ không ghi gì), cần quản lý một khóa để có
bảo đảm mạnh nhất, và một cơ sở dữ liệu có sẵn có thể đang dùng chung với node Camunda 7.24. Mỗi
hệ thống bật nó một cách có chủ đích.

**Bảng.** Với `databaseSchemaUpdate` là `true` hoặc `create` (Run, starter theo mặc định) plugin tự
tạo bảng khi khởi động. Nếu không (container image giữ nguyên cơ sở dữ liệu có sẵn), hãy tạo trước
bằng `sql/create/<database>_fasti_audit.sql` của bản phân phối (cũng có trong jar engine dưới
`dev/fasti/engine/audit/db/create`); `sql/drop/…` xóa bảng cùng mọi bản ghi. Nếu thiếu bảng mà
không được tạo, engine từ chối khởi động với thông báo nêu tên script. Có script cho H2,
PostgreSQL, MySQL/MariaDB (dùng `utf8mb4`), Oracle, SQL Server và DB2.

**Chi phí.** Mỗi lệnh tạo lịch sử: một lệnh update và một lệnh select dòng head, một lần insert
theo lô các bản ghi và một lệnh update head, lúc commit; với cập nhật tác vụ thêm một lần đọc có
chỉ mục bảng lịch sử tác vụ (để nhận ra việc giao việc); tính mã băm không đáng kể. Khóa dòng
head được giữ từ cuối lệnh đến khi commit; với mặc định bốn chuỗi mỗi node, các luồng hiếm khi
phải chờ.

## Lưu trữ và tương thích

- **Các bảng của Camunda 7.24 giữ nguyên.** Nhật ký kiểm toán có bảng tùy chọn riêng:
  `FASTI_AUDIT_EVENT` (bản ghi; `TIME_` tính bằng mili giây, nên mã băm không phụ thuộc múi giờ hay
  độ chính xác thời gian) và `FASTI_AUDIT_HEAD` (mỗi chuỗi một dòng).
- **Node Camunda 7.24 và node Fasti không bật plugin** trên cùng cơ sở dữ liệu bỏ qua các bảng
  này; những gì chúng làm không được ghi. Hãy bật plugin cho mọi node cùng lúc.
- **Quay về Camunda 7.24**: để nguyên các bảng hoặc xóa bằng script drop; không có gì khác bị ghi.
- Nếu không ghi được vào bảng (bị xóa, hoặc có bản ghi chèn từ bên ngoài vào vị trí kế tiếp), các
  lệnh tạo lịch sử sẽ thất bại thay vì chạy mà không được ghi.

## Giới hạn

- Nhật ký chứng minh bản ghi không bị sửa sau khi được ghi. Nó không chứng minh được rằng mọi việc
  đều đã được ghi: node không bật plugin, mức lịch sử thấp hơn, hoặc sửa trực tiếp bảng engine
  trong cơ sở dữ liệu sẽ đi vòng qua nó.
- Không có khóa ký, người có quyền ghi vào cơ sở dữ liệu có thể viết lại một chuỗi từ một bản ghi
  nào đó trở đi cùng với head; có khóa thì chỉ các bản ghi sau điểm kiểm tra cuối. Hãy lưu các mã
  băm cuối được báo hoặc các dòng log điểm kiểm tra ở ngoài cơ sở dữ liệu.
- Xóa mọi bản ghi và head của một chuỗi chỉ phát hiện được khi đối chiếu với mốc bên ngoài như vậy.
- Một lần dọn xóa mọi bản ghi của một chuỗi trước một thời điểm trong một giao dịch; lần dọn rất
  lớn nên chia thành nhiều bước (nhiều mốc `before`).
- Script cho Oracle, SQL Server và DB2 được viết theo kiểu script của engine nhưng chưa được kiểm
  thử ở đây; H2, PostgreSQL 16 và MariaDB 11.4 đã được kiểm thử.
