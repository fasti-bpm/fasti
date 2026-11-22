# Tinh chỉnh khi chạy

English: [tuning-runtime.md](tuning-runtime.md)

Các thiết lập của engine, bộ thực thi job, pool kết nối và các phần bổ sung của Fasti quyết định
một node làm được bao nhiêu việc, kèm cái giá của từng thiết lập. Mọi thứ ở đây áp dụng cho từng
node và an toàn khi chạy cạnh các node Camunda 7.24 trên cùng cơ sở dữ liệu: không bảng nào của
Camunda 7.24 bị thay đổi và job vẫn được khóa như cũ. Chỉ mục và thiết lập của cơ sở dữ liệu không
nằm trong tài liệu này.

Các con số đo trên một máy trạm 16 nhân dùng chung, cơ sở dữ liệu chạy trong container cục bộ;
chúng cho thấy tỷ lệ, không phải mức phần cứng của bạn sẽ đạt. Hãy đo tải của chính bạn trước và
sau khi đổi.

## Bộ thực thi job và pool kết nối

Bộ thực thi job chạy theo chu kỳ: một lần lấy job chọn tối đa `max-jobs-per-acquisition` job đến
hạn và khóa chúng, đưa vào hàng đợi `queue-capacity`, rồi tối đa `max-pool-size` luồng thực thi.
Mỗi job đang chạy giữ một kết nối cơ sở dữ liệu, như mọi yêu cầu đọc hoặc ghi engine.

| Thiết lập | Đến 1.1 (Run, image) | Từ 1.2 (Run, image) | Mặc định của engine / starter |
|---|---|---|---|
| `camunda.bpm.job-execution.core-pool-size` | 3 | 10 (theo `max-pool-size`) | 3 |
| `camunda.bpm.job-execution.max-pool-size` | 10 | 10 | 10 |
| `camunda.bpm.job-execution.queue-capacity` | 3 | 10 | 3 |
| `camunda.bpm.job-execution.max-jobs-per-acquisition` | 3 | 10 | 3 |
| `camunda.bpm.job-execution.lock-time-in-millis` | 300000 (5 phút) | 300000 | 300000 |
| `spring.datasource.hikari.maximum-pool-size` | 10 (mặc định HikariCP) | 20 | 10 |
| `spring.datasource.hikari.minimum-idle` | 10 (= số tối đa) | 10 | = số tối đa |

Giá trị mới được đặt trong `configuration/default.yml` và `production.yml` của Fasti BPM Run và
trong cấu hình của container image; ứng dụng nhúng starter giữ mặc định ở cột cuối và tự đặt thứ
mình cần. **Bản cài Run giữ lại các tệp cấu hình từ 1.1** thì vẫn dùng giá trị cũ: chép khối
`camunda.bpm.job-execution` và các dòng `hikari` của `spring.datasource` từ tệp mới sang tệp được
giữ lại.

Vì sao chọn các giá trị này:

- **Ba job mỗi lần lấy** là giới hạn của mặc định cũ: mỗi chu kỳ là một lệnh select và một lô lệnh
  update khóa, nên node không thể bắt đầu quá ba job cho mỗi cặp lượt đi về, dù còn bao nhiêu
  luồng rảnh.
- **Hàng đợi chứa vừa một lần lấy.** Hàng đợi nhỏ hơn sẽ từ chối một phần của mỗi lần lấy (job vẫn
  bị khóa cho đến khi được thử lại); hàng đợi quá lớn khiến một node khóa những job nó chưa chạy
  ngay được, và node khác không lấy được chúng.
- **Số luồng lõi bằng số tối đa.** Pool luồng chỉ tăng quá `core-pool-size` khi hàng đợi đầy, nên
  với số lõi nhỏ hơn thì phần lớn luồng không bao giờ được tạo. Các tệp đi kèm đặt
  `core-pool-size: ${camunda.bpm.job-execution.max-pool-size:10}`, nên chỉ hạ số tối đa (ví dụ
  `CAMUNDA_BPM_JOB_EXECUTION_MAX_POOL_SIZE=4` cho một cơ sở dữ liệu nhỏ) là hạ cả hai. Một
  `core-pool-size` lớn hơn `max-pool-size` (pool luồng không thể có nhiều luồng lõi hơn số tối đa)
  được starter hạ xuống bằng số tối đa, kèm một cảnh báo, thay vì làm node không khởi động được.
- **Pool = luồng job + phần dự trữ.** Với mười luồng job và pool mười kết nối, mười job chạy lâu
  có thể giữ hết kết nối trong khi các yêu cầu và kiểm tra readiness (`db`) phải chờ. Tính pool
  bằng `max-pool-size` + số yêu cầu dùng cơ sở dữ liệu cùng lúc + 2 (lấy job và việc nền); 20 để
  chỗ cho khoảng tám yêu cầu đồng thời. Node đang rảnh giữ mở `minimum-idle` kết nối (10, bằng số
  kết nối đến 1.1) và chỉ mở thêm, tối đa 20, khi có tải; HikariCP đóng các kết nối dư sau
  `idle-timeout` (10 phút).

Số job async mỗi giây của một node Run trên PostgreSQL 16 (2.000 phiên bản của một quy trình có
một service task bất đồng bộ, các job được thả cùng lúc; mỗi cấu hình chạy ba lần, xen kẽ nhau, mỗi
lần ba vòng đo; trong lúc đó máy còn chạy một tải khác):

| Cấu hình | Trung vị | Khoảng |
|---|---|---|
| Đến 1.1 (3 mỗi lần lấy, hàng đợi 3, lõi 3, pool 10) | 267 job/s | 190 đến 286 |
| Từ 1.2 (10, 10, 10, pool 20) | 285 job/s | 225 đến 299 |

Trên máy đang tải nặng đó mức tăng nhỏ (+7 %, và ổn định hơn): giới hạn là cơ sở dữ liệu, không
phải việc lấy job. Giá trị mới có ích nhất khi job phải chờ thứ khác ngoài cơ sở dữ liệu (gọi HTTP,
script): với giá trị cũ thường chỉ ba luồng chạy, vì pool chỉ tăng quá số lõi khi hàng đợi đầy.

Muốn nhiều hơn:

- Thêm luồng job chỉ có ích khi cơ sở dữ liệu và CPU của node còn dư. Tăng `max-pool-size`,
  `core-pool-size`, `queue-capacity` và `max-jobs-per-acquisition` cùng nhau, và tăng
  `maximum-pool-size` thêm từng đó.
- **Kết nối:** cơ sở dữ liệu phải nhận được mọi kết nối mà tất cả client của nó có thể mở cùng
  lúc:

  ```
  tổng maximum-pool-size của mọi node (node Fasti và node Camunda 7.24)
    + các client khác (công cụ quản trị, sao lưu, giám sát, migration)
    + phần dự trữ của cơ sở dữ liệu (PostgreSQL: superuser_reserved_connections, mặc định 3)
    < giới hạn kết nối của cơ sở dữ liệu (PostgreSQL, MySQL/MariaDB: max_connections)
  ```

  Tính số node ở mức cao nhất: khi tự co giãn là số bản sao tối đa, cộng các pod thêm của một lần
  cập nhật cuốn chiếu (`maxSurge`). Ví dụ trên PostgreSQL với `max_connections=100`: bốn node
  Fasti × 20 + một node Camunda 7.24 × 10 + 5 cho công cụ + 3 dự trữ = 98, vừa đủ. Pool 20 thay vì
  10 nghĩa là một node có thể mở gấp đôi số kết nối khi có tải (lúc rảnh nó giữ `minimum-idle`);
  kiểm tra giới hạn trước khi nâng cấp nhiều bản sao. Image nhận `DB_CONN_MAXACTIVE` /
  `DB_CONN_MINIDLE` hoặc `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` /
  `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE`.
- **Job độc quyền và thời gian khóa.** Các job của một lần lấy được khóa trong
  `camunda.bpm.job-execution.lock-time-in-millis` (5 phút, như trên node Camunda 7.24). Các job
  độc quyền (exclusive) của cùng một phiên bản quy trình trong số đó (continuation bất đồng bộ mặc
  định là độc quyền) chạy lần lượt trong một luồng, và engine không kiểm tra lại khóa trước mỗi
  job. Với 10 job mỗi lần lấy, một lô như vậy có thể dài tới 10 job thay vì 3, nên quỹ thời gian
  cho mỗi job giảm từ khoảng 100 xuống khoảng 30 giây: khi khóa hết hạn mà lô vẫn đang chạy, một
  node khác (Fasti hoặc Camunda 7.24) lấy phần còn lại và thực thi lần thứ hai; xung đột khóa lạc
  quan hoàn tác một lần thực thi, nhưng các tác dụng bên ngoài cơ sở dữ liệu (gọi HTTP, gửi mail)
  xảy ra hai lần. Quỹ thời gian đó cũng tính cả lúc lô chờ trong hàng đợi. Giữ
  `max-jobs-per-acquisition` × job độc quyền chậm nhất (cộng thời gian chờ hàng đợi) dưới thời
  gian khóa, hoặc tăng thời gian khóa (ví dụ 600000), hoặc giảm `max-jobs-per-acquisition`. Job
  mất hàng chục giây nên là external task. Giá trị đi kèm vẫn là 5 phút: khóa dài hơn cũng khiến
  các job mà một node đã khóa nhưng chưa chạy khi nó bị sập hoặc bị dừng cưỡng bức phải chờ chừng
  ấy thời gian rồi node khác mới lấy được.
- **Nhiều node:** mọi node lấy job từ cùng một bảng. Lần lấy càng lớn thì hai node càng dễ chọn
  trùng job; node thua khi khóa sẽ bỏ qua job đó (xung đột khóa lạc quan, vô hại). Giữ
  `max-jobs-per-acquisition` từ 16 trở xuống và đặt
  `camunda.bpm.job-execution.backoff-time-in-millis` (ví dụ 50, với `max-backoff` 5000) để node vừa
  mất job chờ một chút rồi mới lấy tiếp.
- `spring.datasource.hikari.connection-timeout` (mặc định 30 giây) quyết định một yêu cầu chờ kết
  nối bao lâu khi pool hết. Giá trị thấp hơn (5 đến 10 giây) giúp readiness probe báo pool cạn sớm
  hơn.
- Node chỉ phục vụ giao diện web hoặc REST API: `camunda.bpm.job-execution.enabled: false`
  (`FASTI_JOB_EXECUTOR_ENABLED=false`).

## Nhật ký kiểm toán

Khi bật `fasti.audit.enabled`, mỗi lệnh tạo lịch sử nối các bản ghi của nó vào một chuỗi của node
và khóa dòng head của chuỗi đó đến khi giao dịch kết thúc ([audit-trail.vi.md](audit-trail.vi.md)).
Các giao dịch rơi vào cùng một chuỗi phải chờ nhau trong lúc head bị khóa.

**Từ 1.2 khóa được giữ ngắn.** Đến 1.1 các bản ghi được ghi trước các lệnh của engine, nên head bị
khóa suốt lúc engine gửi mọi lệnh insert, update và delete. Từ 1.2, lệnh có giao dịch kết thúc
cùng nó ghi bản ghi sau cùng: head chỉ bị khóa trong các lệnh kiểm toán (khóa, đọc, insert,
update) và lúc commit, kể cả những gì trình quản lý giao dịch ghi khi commit (với JPA là các
thay đổi entity mà Hibernate flush lúc đó). Đó là mọi yêu cầu và mọi job của Fasti BPM Run và container image, và mọi
lệnh của engine standalone (bản phân phối Tomcat).

**Giao dịch trải qua nhiều lệnh** (Spring `@Transactional` bọc ngoài các lời gọi engine) được xử
lý khác. Giao dịch như vậy giữ chuỗi của nó từ cuối lệnh đầu tiên có ghi nhận đến lần commit bên
ngoài, và trong lúc đó vẫn tiếp tục ghi các dòng của engine. Nếu một lệnh ghi sau cùng dùng chung
chuỗi đó, nó sẽ giữ các dòng engine của mình trong lúc chờ head, và có thể chờ một giao dịch mà
tiếp theo lại cần một trong các dòng đó: hai bên chờ nhau cho đến khi cơ sở dữ liệu chọn một bên
để hủy hoặc thời gian chờ khóa kết thúc một bên. Vì vậy lệnh tham gia một giao dịch có sẵn ghi bản
ghi trước trên các chuỗi `<node>/<slot>`, như đến 1.1, còn các lệnh ghi sau cùng dùng chuỗi riêng
(`<node>/end-<slot>`), để hai loại không bao giờ chờ head của nhau và một node 1.1 có cùng node id
chỉ ghi vào loại thứ nhất. Head của dải thứ nhất được tạo khi khởi động. Starter phân biệt hai loại
bằng cách hỏi trình quản lý giao dịch của engine xem nó đã có giao dịch trên luồng hay chưa
(DataSource hoặc EntityManagerFactory của nó đã được gắn, hoặc trạng thái của giao dịch JTA): giao
dịch của một trình quản lý khác (cơ sở dữ liệu thứ hai, JMS, Kafka) không khiến lệnh ghi trước.
Engine không phân biệt được (JTA không có starter, như bản phân phối WildFly, hoặc Spring không
dùng starter) ghi mọi bản ghi trước, trên `<node>/<slot>`, như đến 1.1.

Số giao dịch ghi mỗi giây của một engine khi bật nhật ký kiểm toán (`AuditLockWindowBenchmark`
trong test của engine: 16 luồng, 4 chuỗi; mỗi luồng bắt đầu một phiên bản với năm biến rồi hoàn
thành tác vụ người dùng của nó). Mã của 1.1 và mã mới chạy cùng lúc, mỗi bên với một máy chủ cơ sở
dữ liệu riêng trên cùng mạng container, để tải của máy tác động như nhau lên cả hai; trung vị của
ba vòng. "+1 ms" thêm một mili giây chờ trước mỗi câu lệnh và mỗi lần commit, như khi cơ sở dữ
liệu nằm trên máy khác:

| Cơ sở dữ liệu | Ghi bản ghi trước (1.1) | Ghi bản ghi sau cùng | Thay đổi |
|---|---|---|---|
| PostgreSQL 16 | 228 | 409 | +79 % |
| PostgreSQL 16, +1 ms | 93 | 240 | +158 % (hai vòng) |
| MariaDB 11.4 | 94 | 167 | +79 % |
| MariaDB 11.4, +1 ms | 51 | 85 | +69 % |
| H2 (tệp, trong JVM) | 735 | 655 | −11 % |
| H2, +1 ms | 150 | 397 | +166 % |

Mức tăng phụ thuộc vào tải của máy: một loạt đo trước đó gồm ba vòng, không thêm thời gian chờ,
cho PostgreSQL 208 → 355 (+71 %) và MariaDB 81 → 116 (+43 %). Hãy tính theo mức thấp.

H2 chạy trong JVM và không có lượt đi về: khi nhiều giao dịch gửi lệnh engine cùng lúc hơn, nó tốn
thời gian vào khóa nội bộ của chính nó. Đó là cơ sở dữ liệu cho phát triển.

Những gì vẫn quyết định thông lượng khi bật nhật ký kiểm toán:

- `fasti.audit.chains` (mặc định 4, từ 1 đến 64): số giao dịch của một node có thể nối bản ghi
  cùng lúc. Một luồng luôn dùng cùng một chuỗi (mã luồng chia lấy dư cho số chuỗi), nên hãy tăng
  về phía số luồng ghi cùng lúc (luồng job cộng luồng yêu cầu đang bận) khi `pg_stat_activity`
  hoặc công cụ tương đương cho thấy các phiên chờ `FASTI_AUDIT_HEAD`. Thêm chuỗi không tốn gì khi
  ghi; việc kiểm tra đọc từng chuỗi.
- **Giao dịch dài.** Một giao dịch của ứng dụng bao nhiều lệnh engine (Spring `@Transactional` bọc
  ngoài) khóa chuỗi của nó (`<node>/<slot>`) khi kết thúc lệnh đầu tiên có ghi nhận và giữ đến lần commit bên
  ngoài; các giao dịch như vậy cùng slot phải chờ trong lúc đó. Như đến 1.1, hai giao dịch như vậy
  cùng slot vẫn có thể chờ nhau khi một bên đã ghi một dòng engine trong một lệnh trước đó không
  ghi nhận gì (bên kia lại cần dòng đó trong lúc giữ chuỗi); cơ sở dữ liệu kết thúc tình huống này
  bằng cách hoàn tác một bên. Giữ các giao dịch như vậy ngắn, hoặc cho node thêm chuỗi.
- Chi phí mỗi lệnh là bốn câu lệnh (năm khi một tác vụ được cập nhật) trong giao dịch của lệnh đó.

**Các trang của nhật ký kiểm toán.** Một trang được sắp theo thời gian, chuỗi và vị trí, tất cả
cùng một chiều (các bản ghi cùng mili giây ở các chuỗi khác nhau xuất hiện theo thứ tự chuỗi đảo
ngược khi bản ghi mới nhất đứng trước). Chỉ mục `FASTI_IDX_AUDIT_ORDER (TIME_, CHAIN_ID_, SEQ_)`
cho đúng thứ tự đó, nên cơ sở dữ liệu đọc một trang thay vì sắp xếp mọi bản ghi khớp bộ lọc. Bảng
kiểm toán mới có sẵn chỉ mục này. **Bảng kiểm toán đã có** (do 1.0 hoặc 1.1 tạo) cần chạy script
nâng cấp một lần: `sql/upgrade/<db>_fasti_audit_after_1.1.sql`, có trong mọi bản phân phối (tạo
`FASTI_IDX_AUDIT_ORDER`, `FASTI_IDX_AUDIT_ACTOR_ORDER` và `FASTI_IDX_AUDIT_TYPE_ORDER`, xóa
`FASTI_IDX_AUDIT_TIME`, `FASTI_IDX_AUDIT_ACTOR` và `FASTI_IDX_AUDIT_TYPE` mà chúng thay thế; chạy
lại được). Chưa chạy thì mọi thứ vẫn hoạt động như trước, chỉ là không nhanh hơn. Tạo chỉ mục phải
đọc cả bảng: PostgreSQL tạo song song (concurrently) và MySQL/MariaDB tạo trực tuyến (online); trên
Oracle, SQL Server và Db2 hãy thêm ONLINE nếu phiên bản có hỗ trợ, nếu không mọi lệnh insert có
ghi nhận kiểm toán phải chờ trong lúc tạo. Xóa bảng đã nâng cấp bằng script drop của phiên bản này;
script drop của 1.1 sẽ lỗi trên các bảng đó.

Thời gian cơ sở dữ liệu dành cho một câu lệnh, 2 triệu bản ghi kiểm toán trong 4 chuỗi (500 người
thực hiện), trước (chỉ mục của 1.1, thứ tự hỗn hợp) và sau script nâng cấp (PostgreSQL
`EXPLAIN ANALYZE`, MariaDB `ANALYZE FORMAT=JSON` với buffer pool mặc định 128 MB; mỗi câu lệnh chạy
một lần sau khi `ANALYZE` bảng, nên hãy xem các giá trị như bậc độ lớn). Các cột "sau" được đo
chỉ với `FASTI_IDX_AUDIT_ORDER`, trước khi các chỉ mục theo người thực hiện và theo loại thay thế
chỉ mục cũ; bộ lọc theo người thực hiện được đo ở bên dưới:

| Câu lệnh | PostgreSQL 16 trước | sau | MariaDB 11.4 trước | sau |
|---|---|---|---|---|
| Trang đầu, mới nhất trước (25 bản ghi) | 0,13 ms | 0,05 ms | 25,3 s (sắp xếp cả 2 triệu) | 9 ms |
| Trang ở vị trí 10.000 | 6,5 ms | 4,1 ms | – | – |
| Trang đầu lọc theo loại | 0,07 ms | 0,06 ms | – | – |
| Đếm mọi bản ghi | 202 ms | 176 ms | 2,3 s | 2,9 s |
| Đếm với `maxCount=10000` | – | 1,8 ms | – | 17 ms |
| Tạo `FASTI_IDX_AUDIT_ORDER` | | 5,9 s | | 26,7 s |

PostgreSQL 16 vốn đã sắp xếp thứ tự cũ theo kiểu tăng dần (incremental sort) dọc
`FASTI_IDX_AUDIT_TIME`, nên trước đây các trang đã rẻ; MariaDB và MySQL (và các cơ sở dữ liệu khác
không có incremental sort) phải sắp xếp mọi bản ghi khớp bộ lọc cho mỗi trang.

Các trang lọc theo người thực hiện (kể cả `system`), theo loại và hành động, theo phiên bản quy
trình hoặc theo business key được đọc dọc một chỉ mục theo đúng thứ tự trang và dừng sau khi đủ
trang. Bộ lọc không có chỉ mục như vậy (hành động mà không có loại, giới hạn tenant,
`processDefinitionKey`, `entityId`) đọc bản ghi cho đến khi đủ một trang, tức cả bảng khi ít bản ghi
khớp. Đo trên MariaDB 11.4 với 500 nghìn bản ghi: một người thực hiện có 6.000 bản ghi đều nằm trong
một phần tư cũ nhất trước đây cần 375.000 mục chỉ mục và lần tra dòng (1,6 s trên tmpfs, vài phút
trên đĩa), nay 25 dòng (0,5 ms).

**Đếm.** `GET /audit/event/count` đếm mọi bản ghi khớp bộ lọc, tức là đọc cả bảng khi không có bộ
lọc. Với `maxCount=<n>` (1 đến 1.000.000) cơ sở dữ liệu dừng khi đã đếm `n` bản ghi (bộ lọc không có
chỉ mục vẫn có thể phải xét nhiều dòng hơn); kết quả bằng `n` nghĩa là "`n` trở lên". Client chỉ cần biết còn trang sau hay không cũng có thể xin `GET /audit/event`
nhiều hơn một bản ghi so với số hiển thị và bỏ qua lệnh đếm. Trong Java:
`AuditEventQuery.count(long limit)`.

## Gauge của engine

Các gauge về trạng thái engine (`fasti.engine.process.instances.active`, `tasks.open`,
`incidents.open`, `jobs.executable`, `jobs.failed`, `external.tasks.open`) là sáu truy vấn đếm.
Từ 1.2, một lần scrape không bao giờ chạy chúng: nó trả các giá trị đọc gần nhất, và khi các giá
trị cũ hơn `fasti.metrics.engine.gauges.refresh-interval` (mặc định 30 giây) thì khởi động một
lượt truy vấn trên luồng nền, kết quả dành cho lần scrape sau. Vì vậy một giá trị có thể cũ đến
một chu kỳ làm mới cộng một chu kỳ scrape; trước khi lượt đầu xong, các gauge báo `NaN`. Một lệnh
đếm chậm (bảng `ACT_RU_*` lớn không có chỉ mục phù hợp) không còn làm chậm hay làm quá hạn lần
scrape. Lượt đầu chạy khi engine khởi động, kể cả trên node không bao giờ được scrape; sau đó node
chỉ chạy các lệnh đếm trong lúc được scrape. Khi ứng dụng dừng, các gauge dừng trước khi engine
đóng và giữ các giá trị cuối.

- Một lượt đã chạy quá ba chu kỳ (một lệnh đếm chờ khóa, hoặc một kết nối đã chết mà không báo
  lỗi) khiến mọi gauge báo `NaN` cho đến khi nó kết thúc, thay vì các giá trị cuối: trong cảnh báo,
  hãy coi `NaN` như một lần scrape thất bại. Trong lúc đó lượt này giữ một kết nối của pool;
  `camunda.bpm.generic-properties.properties.jdbc-statement-timeout` (giây) kết thúc nó.
- Với cơ sở dữ liệu lớn, tăng chu kỳ (`60s` đến `5m`): mỗi node chạy sáu lệnh đếm một lần mỗi chu
  kỳ trong lúc nó được scrape.
- `fasti.metrics.engine.gauges.enabled: false` trên mọi node trừ một node tránh N lượt giống hệt
  nhau trong cụm (các giá trị mô tả cơ sở dữ liệu, không phải node).

## Lịch sử: chiến lược thời điểm xóa

Với chiến lược mặc định `end`, engine ghi thời điểm xóa cho toàn bộ lịch sử của một phiên bản quy
trình gốc khi phiên bản đó kết thúc: một lệnh `update ACT_HI_… set REMOVAL_TIME_ = ? where
ROOT_PROC_INST_ID_ = ?` cho mỗi bảng lịch sử, 16 câu lệnh trong giao dịch kết thúc phiên bản, phần
lớn không khớp dòng nào (17 khi bật `enableHistoricInstancePermissions`, vì khi đó
`ACT_RU_AUTHORIZATION` cũng được cập nhật).

`historyRemovalTimeStrategy: start` ghi thời điểm xóa cùng với từng dòng lịch sử ngay khi dòng
được tạo (thời điểm bắt đầu của phiên bản gốc + thời gian lưu) và không gửi lệnh update nào trong
số này.

```yaml
camunda.bpm:
  generic-properties.properties:
    history-removal-time-strategy: start
```

| | `end` (mặc định) | `start` |
|---|---|---|
| Câu lệnh khi một phiên bản gốc kết thúc | +16 lệnh update (mỗi bảng lịch sử một lệnh; 17 khi bật quyền trên phiên bản lịch sử) | không có |
| Thời điểm xóa | lúc phiên bản gốc kết thúc + thời gian lưu | lúc phiên bản gốc bắt đầu + thời gian lưu |
| Lịch sử của phiên bản chạy lâu hơn thời gian lưu | được giữ đến khi phiên bản kết thúc và hết thời gian lưu | **bị dọn lịch sử xóa trong khi phiên bản vẫn đang chạy** |
| Thời gian lưu đổi sau khi bắt đầu | áp dụng khi phiên bản kết thúc | không áp dụng cho phiên bản đã bắt đầu (dùng batch "Set removal time") |

Chỉ chọn `start` khi mọi phiên bản kết thúc sớm hơn nhiều so với thời gian lưu lịch sử của nó (quy
trình ngắn, số lượng lớn) hoặc khi chấp nhận mất lịch sử của các phiên bản chạy lâu. Mặc định không
đổi. Mọi node trên cùng cơ sở dữ liệu, kể cả node Camunda 7.24, nên dùng cùng một chiến lược; nếu
không, thời điểm xóa của một dòng phụ thuộc vào node đã ghi nó. Đổi chiến lược chỉ ảnh hưởng đến
phiên bản gốc mới; các dòng đã có giữ nguyên giá trị.

## Script (JavaScript)

JavaScript chạy trên GraalJS. Engine tạo một script engine và một context cho mỗi lần đánh giá rồi
đóng lại; các thiết lập `enableScriptEngineCaching` và `enableScriptCompilation` không áp dụng
cho nó (script engine của GraalJS không an toàn với đa luồng nên không bao giờ được giữ lại, và mã
nguồn JavaScript không được biên dịch thành `CompiledScript`). Thứ được dùng chung là bộ đệm mã
của polyglot engine đứng sau các script engine: một script đã được đánh giá sẽ không bị phân tích
lại chừng nào engine đó còn sống. Nó chỉ được tham chiếu yếu, để class loader của process
application có thể được giải phóng; sau một lần thu gom rác khi không có script nào đang chạy, nó
được tạo lại và các lần đánh giá tiếp theo phân tích lại mã nguồn. Trên JVM không có trình biên
dịch Graal, script chạy ở chế độ thông dịch.

Với mô hình đánh giá nhiều script mỗi giây, hãy ưu tiên biểu thức (JUEL), Groovy (được biên dịch
và giữ lại theo từng script) hoặc Java delegate trên đường xử lý nóng, và dùng JavaScript cho phần
còn lại.

## Các thay đổi nhỏ hơn trong 1.2

- **Gán tham số.** MyBatis gán mọi tham số của câu lệnh qua một lớp bọc statement che metadata
  tham số của driver. Lớp bọc giờ là một lớp thường thay vì dynamic proxy, nên gán một tham số là
  một lời gọi trực tiếp thay vì lời gọi phản chiếu.

  `ParameterBindingBenchmark` (trong test của engine) gán 24 tham số của một lệnh insert trên H2:
  lớp bọc thường tốn ít hơn 13 đến 18 % thời gian so với proxy trong ba lần chạy, tức 0,2 đến
  0,4 µs mỗi câu lệnh. Nhỏ với từng câu lệnh, nhưng áp dụng cho mọi tham số của mọi câu lệnh và mọi
  dòng của một lô JDBC. Trong lúc MyBatis ghi log các câu lệnh của một mapper ở mức `DEBUG` (ví dụ
  `org.camunda.bpm.engine.impl.persistence.entity`), lớp bọc ghi log của nó trông như một lời gọi
  stored procedure, và các câu lệnh đó được gán tham số qua proxy như trước.

- **Lệnh không ghi nhận gì** (truy vấn) không còn sao chép các thao tác hàng loạt của lệnh để tìm
  deployment khi bật nhật ký kiểm toán.
- **Vắng mặt.** Khi `ACT_ID_INFO` không có chỉ mục bắt đầu bằng `USER_ID_` (lược đồ Camunda 7.24
  không có), mỗi lần tra cứu vắng mặt đều đọc cả bảng: người đầu tiên một lệnh hỏi được đọc riêng,
  người thứ hai khiến lệnh đọc mọi thông tin vắng mặt bằng một câu lệnh, trả lời luôn mọi lần tra
  cứu sau đó (chuỗi ba người thay thế gửi hai câu lệnh thay vì bốn). Có chỉ mục tùy chọn
  `FASTI_IDX_ID_INFO_USER` ([hiệu năng](performance.vi.md)), mỗi lần tra cứu là một lần seek chỉ
  mục và engine tra từng người; engine kiểm tra chỉ mục khi khởi động.

## Các thiết lập khác nên biết

| Thiết lập | Mặc định | Ghi chú |
|---|---|---|
| `jdbcBatchProcessing` | `true` | giữ nguyên: các câu lệnh của một lệnh được gửi đến cơ sở dữ liệu theo lô |
| Mức lịch sử | `full` trong Run và starter | `full` ghi khoảng gấp đôi số dòng lịch sử so với `audit`; quy tắc bốn mắt và nhật ký thao tác người dùng cần `full` |
| `historyCleanupBatchWindowStartTime` / `…EndTime` | chưa đặt | không có khung giờ thì dọn lịch sử chỉ chạy khi khởi động bằng tay; hãy đặt khung giờ, và thời gian lưu cho mọi định nghĩa |
| `camunda.bpm.generic-properties.properties.jdbc-statement-timeout` | chưa đặt | số giây sau đó một câu lệnh bị hủy, để một truy vấn bất thường (hoặc một lượt gauge bị treo) không giữ mãi một kết nối |
| `fasti.absence.enabled` | bật trong Run, tắt trong image | thêm một lần đọc thông tin vắng mặt của người dùng cho mỗi lần giao việc cho người khác |
