[English](performance.md) | Tiếng Việt

# Hiệu năng cơ sở dữ liệu

> **Mới từ bản phát hành sau 1.1.0.** Các bản trước không có các script chỉ mục tùy chọn và các
> cảnh báo khi khởi động.

Cơ sở dữ liệu của Fasti BPM dùng nguyên schema của Camunda 7.24. Schema đó đánh chỉ mục khóa ngoại
khá đầy đủ, nhưng một số truy vấn mà engine và giao diện web chạy liên tục không có chỉ mục phù hợp
khi bảng lớn dần: lấy job để chạy (job acquisition), task quá hạn, task của một người theo hạn xử lý,
và việc tra cứu vắng mặt mỗi lần giao task. Trang này mô tả

- các **script chỉ mục tùy chọn** (thêm gì, cách áp dụng và gỡ trên từng cơ sở dữ liệu, vì sao an
  toàn khi chạy song song với node Camunda 7.24),
- các **cảnh báo khi engine khởi động** (thiếu chỉ mục, cấu hình tốn hiệu năng) và cách tắt,
- các **cấu hình engine** nên xem lại khi cơ sở dữ liệu lớn,
- và **số đo** làm căn cứ cho từng chỉ mục trong script, kể cả các ứng viên bị loại.

## Các script chỉ mục tùy chọn

Mỗi bản phân phối có một script tạo và một script xóa cho mỗi loại cơ sở dữ liệu:

| Bản phân phối | Vị trí |
|---|---|
| Fasti BPM Run | `configuration/sql/optional/` |
| Container image | `/fasti/configuration/sql/optional/` |
| Tomcat, WildFly | `sql/optional/` |
| Maven | `dev.fasti.platform.distro:fasti-sql-scripts`, thư mục `sql/optional/` |

Tên tệp: `<database>_fasti_perf_indexes_create.sql` và `<database>_fasti_perf_indexes_drop.sql`,
với `<database>` là `postgres`, `mysql` (dùng cả cho MariaDB), `oracle`, `mssql`, `db2` hoặc `h2`.

### Script tạo những gì

| Chỉ mục | Bảng (cột) | Phục vụ |
|---|---|---|
| `FASTI_IDX_JOB_DUEDATE` | `ACT_RU_JOB (DUEDATE_)`; PostgreSQL: `NULLS FIRST`, chỉ gồm job còn lượt thử lại và không bị tạm dừng; Oracle: `(DUEDATE_, 0)` | lấy job để chạy trên mọi node vài giây một lần, timer theo hạn |
| `FASTI_IDX_TASK_DUE` | `ACT_RU_TASK (DUE_DATE_)` | task quá hạn và sắp đến hạn, sắp xếp theo hạn (danh sách task, dashboard, trang khối lượng công việc) |
| `FASTI_IDX_TASK_ASSIGNEE_DUE` | `ACT_RU_TASK (ASSIGNEE_, DUE_DATE_)` | "task của tôi" theo hạn, số đếm theo người của trang khối lượng công việc, task chưa giao đã quá hạn |
| `FASTI_IDX_ID_INFO_USER` | `ACT_ID_INFO (USER_ID_, KEY_)` | tra cứu vắng mặt mỗi lần giao task ([vắng mặt](absence.vi.md)), hồ sơ người dùng |

Nhóm thứ hai nằm trong script dưới dạng chú thích. Chỉ tạo khi có đúng nhu cầu nêu:

| Chỉ mục | Bảng (cột) | Khi nào |
|---|---|---|
| `FASTI_IDX_HI_PI_PDEF_START` | `ACT_HI_PROCINST (PROC_DEF_ID_, START_TIME_)` | danh sách lịch sử của một định nghĩa quy trình, mới nhất trước, nhất là khi bật phân quyền |
| `FASTI_IDX_HI_AI_PDEF_START` | `ACT_HI_ACTINST (PROC_DEF_ID_, START_TIME_)` | trang thống kê hoạt động của một định nghĩa có lịch sử lớn (thêm một mục chỉ mục cho mỗi activity instance trong bảng lịch sử lớn nhất) |
| `FASTI_IDX_INC_TIMESTAMP` | `ACT_RU_INCIDENT (INCIDENT_TIMESTAMP_)` | hàng chục nghìn incident đang mở, sắp xếp cũ nhất trước |

### Chạy song song với Camunda 7.24 và quay lui

- Các chỉ mục **không unique và chỉ thêm vào**. Không bảng, cột hay ràng buộc nào thay đổi; node
  Camunda 7.24 chỉ thấy khác ở kế hoạch truy vấn.
- Engine **không bao giờ chạy các script này**, chúng **không được ghi vào `ACT_GE_SCHEMA_LOG`**,
  nên việc kiểm tra phiên bản schema của node 7.24 và node Fasti không bị ảnh hưởng, và
  `databaseSchemaUpdate` không đụng tới chúng.
- Những dạng chỉ mục có thể làm thao tác ghi của node 7.24 thất bại bị loại có chủ đích: không đánh
  chỉ mục cột văn bản dài của `ACT_RU_VARIABLE` (PostgreSQL và SQL Server từ chối dòng có khóa quá
  dài), không dùng filtered index trên SQL Server (ghi thất bại với lỗi 1934 ở phiên thiếu các tùy
  chọn `SET` bắt buộc), không đánh chỉ mục các cột thay đổi mỗi lần khóa hoặc cập nhật của
  `ACT_RU_JOB` và `ACT_RU_EXECUTION` (`LOCK_OWNER_`, `LOCK_EXP_TIME_`, `REV_`).
- Muốn quay lui, chạy script xóa; chạy được bất cứ lúc nào, kể cả khi các node đang chạy. Trên
  PostgreSQL, schema sau script xóa đã được so với schema trước script tạo
  (`pg_dump --schema-only`): giống hệt.

### Áp dụng

Chạy script tạo bằng tài khoản sở hữu các bảng của engine, sau đó cập nhật thống kê. Mọi script đều
chạy lại được (MySQL/MariaDB, PostgreSQL, SQL Server, Oracle và H2 bỏ qua chỉ mục đã có; trên Db2
trình dòng lệnh báo `SQL0601N` rồi chạy tiếp).

| Cơ sở dữ liệu | Lệnh | Không chặn thao tác ghi |
|---|---|---|
| PostgreSQL | `psql -f postgres_fasti_perf_indexes_create.sql`, rồi `ANALYZE` | Script dùng `CREATE INDEX CONCURRENTLY`, không được chạy trong transaction: dùng psql (autocommit), không dùng công cụ bọc cả script trong một transaction. Lần tạo concurrently thất bại để lại chỉ mục `INVALID` (`select indexrelid::regclass from pg_index where not indisvalid`); xóa nó rồi chạy lại script. Cảnh báo khi khởi động coi chỉ mục invalid là thiếu. |
| MySQL, MariaDB | client `mysql`/`mariadb`: `source mysql_fasti_perf_indexes_create.sql`, rồi `ANALYZE TABLE` | Các lệnh dùng `ALGORITHM=INPLACE, LOCK=NONE`; nếu không làm được như vậy, server từ chối lệnh thay vì khóa bảng. |
| Oracle | SQL*Plus, SQLcl hoặc SQL Developer | Thêm `ONLINE` sau danh sách cột (Enterprise Edition). |
| SQL Server | `sqlcmd -i mssql_fasti_perf_indexes_create.sql` | Thêm `WITH (ONLINE = ON)` (Enterprise Edition, Azure SQL). |
| Db2 | `db2 -tvf db2_fasti_perf_indexes_create.sql`, rồi `RUNSTATS` | Db2 tạo chỉ mục trong khi đọc và ghi vẫn diễn ra. |

Tạo online vẫn giữ khóa ngắn lúc bắt đầu và kết thúc. Trên cơ sở dữ liệu đo, mỗi chỉ mục mất 0,4 đến
3,5 giây (1 đến 2 triệu dòng, không concurrently).

**Đã kiểm chứng**: PostgreSQL 16 và MariaDB 11.4 (mỗi script chạy hai lần, kế hoạch truy vấn và chi
phí ghi đo ở dưới). **Chưa kiểm chứng**: Oracle, SQL Server và Db2. Các script đó mới chỉ được rà
cú pháp và ghi rõ điều này ở phần đầu; hãy chạy thử trên hệ thống kiểm thử trước. Riêng Oracle và
Db2, kiểm tra rằng việc lấy job với `jobExecutorAcquireByDueDate=true` không quét toàn bộ chỉ mục mới
khi không có job nào đến hạn (xem [Lấy job](#lay-job)).

## Cảnh báo khi engine khởi động

Mỗi lần khởi động, engine đọc danh mục chỉ mục của cơ sở dữ liệu và cấu hình của chính nó, rồi ghi
log những gì tốn hiệu năng. Nó không thay đổi gì, không bao giờ làm khởi động thất bại, và bỏ qua im
lặng những kiểm tra không làm được (không có quyền đọc danh mục, cơ sở dữ liệu không biết).

- **`FASTI-PERF-001`**: một cảnh báo liệt kê các chỉ mục của script tạo mà cơ sở dữ liệu chưa có,
  kèm đường dẫn script trong từng bản phân phối. Một chỉ mục được coi là có khi bất kỳ chỉ mục dùng
  được nào của bảng bắt đầu bằng đúng các cột đó theo đúng thứ tự, bất kể tên, nên chỉ mục do quản
  trị viên tự tạo cũng được tính. Trên PostgreSQL, chỉ mục `INVALID` bị coi là thiếu, và partial
  index chỉ được tính khi có đúng điều kiện của script. Trên H2 thông báo ở mức INFO (H2 dùng cho
  phát triển).
- **`FASTI-PERF-002`**: có ghi lịch sử (mức khác `none`) nhưng dọn lịch sử bị tắt hoặc không có
  khung giờ chạy, nên các bảng lịch sử lớn lên không giới hạn.
- **`FASTI-PERF-003`**: `historyCleanupStrategy=endTimeBased`, cách chọn bản ghi mà không chỉ mục
  nào hỗ trợ.
- **`FASTI-PERF-004`**: connection pool (HikariCP, pool của engine độc lập, Tomcat JDBC, DBCP 2)
  cho ít kết nối hơn số luồng job executor + 2, trên node có chạy job.
- **`FASTI-PERF-005`**: `ensureJobDueDateNotNull=true` nhưng `ACT_RU_JOB` có job chạy được mà không
  có hạn. Node này không bao giờ lấy các job đó; chúng đến từ node không đặt cấu hình này
  (Camunda 7.24 hoặc Fasti với mặc định).
- **`FASTI-PERF-006`**: `jobExecutorAcquireByDueDate=true` với `ensureJobDueDateNotNull=false`: mỗi
  lần lấy job phải sắp xếp các job đến hạn và job không có hạn (xem [Lấy job](#lay-job)).

| Bản phân phối | Công tắc (`warn` mặc định, `off`) |
|---|---|
| Run, Spring Boot starter | `fasti.performance.index-check`, `fasti.performance.config-check` |
| Container image | `FASTI_PERFORMANCE_INDEX_CHECK`, `FASTI_PERFORMANCE_CONFIG_CHECK` |
| Tomcat (`conf/bpm-platform.xml`), WildFly (`standalone*.xml`) | plugin `dev.fasti.engine.performance.PerformanceAdvisorPlugin`, thuộc tính `indexCheck`, `configCheck` |
| Cấu hình engine riêng | thêm `PerformanceAdvisorPlugin` vào danh sách process engine plugin |

## Cấu hình engine cho cơ sở dữ liệu lớn

Các cấu hình này là theo từng node: một node Fasti có chúng và một node Camunda 7.24 không có vẫn
làm việc trên cùng cơ sở dữ liệu.

| Cấu hình | Khuyến nghị |
|---|---|
| Dọn lịch sử | Đặt khung giờ (`historyCleanupBatchWindowStartTime` / `EndTime`); không có khung giờ thì việc dọn chỉ chạy khi có người khởi động. Giữ chiến lược mặc định `removalTimeBased` (xóa qua các cột `REMOVAL_TIME_` có chỉ mục). Tăng `historyCleanupDegreeOfParallelism` lên 2–4 khi lịch sử lớn. Job dọn lịch sử dùng chung: node nào bật dọn lịch sử cũng có thể chạy chúng. |
| Mức lịch sử | `full` ghi nhiều hơn `audit` đáng kể (chi tiết biến, nhật ký thao tác). [Kiểm soát bốn mắt](four-eyes.vi.md) và nhật ký thao tác cần `full`; ngoài ra `audit` hoặc `activity` là đủ. |
| Job executor | Chọn số luồng theo CPU và I/O, `maxJobsPerAcquisition` khoảng bằng hàng đợi + số luồng rảnh, hàng đợi nhỏ trong cụm để một node không khóa những job nó chưa chạy kịp. `backoffTimeInMillis` (ví dụ 50) và `maxBackoff` (ví dụ 5000) giảm va chạm khi nhiều node cùng lấy job. |
| Connection pool | Ít nhất bằng số luồng job executor + số request đồng thời + 2, và dưới mức cơ sở dữ liệu phục vụ được cho tất cả các node (`FASTI-PERF-004` kiểm tra phần đầu). |
| `ensureJobDueDateNotNull` | Biến điều kiện lấy job thành một khoảng trên cột hạn. Khi đã có `FASTI_IDX_JOB_DUEDATE`, việc lấy job mặc định vốn đã rẻ (6 buffer khi không có job đến hạn), nên cấu hình này chủ yếu có ý nghĩa khi dùng cùng `jobExecutorAcquireByDueDate=true`. Chỉ bật khi mọi node trong cụm đều đặt, sau khi các job không có hạn đã chạy xong (`FASTI-PERF-005`). |
| `jobExecutorAcquireByDueDate`, `…ByPriority` | Chỉ dùng cùng `FASTI_IDX_JOB_DUEDATE`; xem [Lấy job](#lay-job). |
| `queryMaxResultsLimit`, `jdbcStatementTimeout` | Nên đặt (ví dụ 2000 và 60 giây) để truy vấn không giới hạn hoặc bất thường không giữ kết nối mãi. |
| `enableHistoricInstancePermissions` | Để tắt nếu không cần: nó nhân số dòng của `ACT_RU_AUTHORIZATION`. |
| Mức cô lập | READ COMMITTED (`READ_COMMITTED_SNAPSHOT ON` trên SQL Server, `transaction-isolation=READ-COMMITTED` trên MySQL/MariaDB) thay vì `skipIsolationLevelCheck`. |
| Cơ sở dữ liệu | PostgreSQL: hệ số autovacuum riêng cho `ACT_RU_JOB`, `ACT_RU_EXECUTION`, `ACT_RU_VARIABLE` và các bảng lịch sử (dọn lịch sử xóa hàng loạt); cập nhật thống kê sau khi nạp dữ liệu lớn trên mọi cơ sở dữ liệu. |

## Số đo

### Bộ dữ liệu và cách đo

Mỗi cơ sở dữ liệu một bộ dữ liệu, sinh trực tiếp vào các bảng của engine sau khi engine đã tạo schema
và deploy 20 định nghĩa quy trình (user task có nhóm ứng viên và hạn, service task bất đồng bộ,
timer). Đây là bộ của PostgreSQL; bộ của MariaDB cùng hình dạng nhưng bằng một nửa và không có biến:

- 1.000.000 process instance đang chạy, mỗi cái chờ ở một user task;
- 1.000.000 task: 60 % đã giao cho 5.000 người (khoảng 200 task mỗi người), một nửa có hạn, khoảng
  29.000 task quá hạn;
- 1.600.000 liên kết ứng viên (200 nhóm, 100.000 người dùng ứng viên), 2.000.000 biến;
- 1.000.000 job: 990.000 timer đến hạn sau 30 đến 395 ngày, 5.000 job lỗi (hết lượt thử lại),
  5.000 job tạm dừng; 20.000 incident đang mở;
- 2.200.000 bản ghi phân quyền, đúng như engine ghi khi bật phân quyền (một cho mỗi người được giao
  và mỗi ứng viên của từng task);
- 101.600 dòng thông tin người dùng (20.000 người, 400 người đang vắng mặt);
- 1.000.000 process instance lịch sử, 1.600.000 activity instance lịch sử.

Các truy vấn là của chính engine (qua Java API, nên câu lệnh lấy từ các mapping MyBatis): lấy job,
đếm và liệt kê task như danh sách task, dashboard và trang khối lượng công việc dùng, tra cứu vắng
mặt, danh sách lịch sử và incident, mỗi truy vấn đo khi tắt và khi bật phân quyền. Với mỗi truy vấn,
bảng ghi câu lệnh như cơ sở dữ liệu đã thực thi (PostgreSQL `auto_explain` với `ANALYZE` và
`BUFFERS`; MariaDB `ANALYZE FORMAT=JSON`), sau một lần chạy trước cùng truy vấn, trước và sau khi có
chỉ mục. Buffer là số trang 8 kB đọc từ bộ nhớ hoặc đĩa; con số này không phụ thuộc tải của các tiến
trình khác trên máy, còn thời gian thì có (máy phát triển dùng chung). Bộ dữ liệu MariaDB nhỏ bằng một
nửa (xem bên dưới).

### PostgreSQL 16

| Truy vấn | Trước | Sau | Chỉ mục |
|---|---|---|---|
| Lấy job, không có job đến hạn (trường hợp thường gặp) | 40.009 buffer, 253 ms (API trung vị 142 ms) | 6 buffer, 0,1 ms (API 5,9 ms) | `FASTI_IDX_JOB_DUEDATE` |
| Lấy job, không có job đến hạn, `jobExecutorAcquireByDueDate=true` (API, trung vị) | 228 ms | 15 ms | `FASTI_IDX_JOB_DUEDATE` (`NULLS FIRST`) |
| Lấy job, `ensureJobDueDateNotNull=true` (API, trung vị) | 156 ms | 33 ms (phiên bản tăng dần: 7,5 ms) | `FASTI_IDX_JOB_DUEDATE` |
| Lấy job, 1.000 job đến hạn (API, trung vị) | 203 ms | 80–90 ms (câu lệnh vẫn đọc 40.000 buffer: truy vấn con cho job exclusive quét cả bảng) | `FASTI_IDX_JOB_DUEDATE` |
| Task quá hạn, 15 task đầu theo hạn | 27.417 buffer, 64 ms | 36 buffer, 0,2 ms | `FASTI_IDX_TASK_DUE` |
| Đếm task quá hạn (28.800) | 27.273 buffer, 91 ms | 18.196 buffer, 45 ms | `FASTI_IDX_TASK_DUE` |
| Đếm task quá hạn của một nhóm ứng viên | 50.028 buffer, 63 ms | 28.206 buffer, 35 ms | `FASTI_IDX_TASK_DUE` |
| Đếm task chưa giao đã quá hạn | 27.389 buffer, 61 ms | 9.729 buffer, 16 ms | `FASTI_IDX_TASK_ASSIGNEE_DUE` |
| Task quá hạn của một người (đếm) | 203 buffer | 20 buffer | `FASTI_IDX_TASK_ASSIGNEE_DUE` |
| Task của một người, 15 task đầu theo hạn | 203 buffer | 36 buffer | `FASTI_IDX_TASK_ASSIGNEE_DUE` |
| Thông tin người dùng của một người (tra cứu vắng mặt) | 1.359 buffer, 23 ms | 8 buffer, 0,3 ms | `FASTI_IDX_ID_INFO_USER` |
| Một mục thông tin người dùng | 1.359 buffer, 23 ms | 4 buffer, 0,1 ms | `FASTI_IDX_ID_INFO_USER` |
| Lịch sử một định nghĩa, 50 mới nhất | 1.260 buffer, 2,9 ms | 71 buffer, 0,3 ms | `FASTI_IDX_HI_PI_PDEF_START` (nhóm hai) |
| … khi bật phân quyền | 1.309.627 buffer, 1.279 ms | 350.498 buffer, 399 ms | `FASTI_IDX_HI_PI_PDEF_START` |
| Thống kê hoạt động của một định nghĩa (2.000 dòng) | 29.909 buffer, 114 ms | 1.058 buffer, 9 ms | `FASTI_IDX_HI_AI_PDEF_START` (nhóm hai) |
| Incident đang mở, 50 cũ nhất | 834 buffer, 21 ms | 67 buffer, 1,4 ms | `FASTI_IDX_INC_TIMESTAMP` (nhóm hai) |

Khi bật phân quyền, hiệu quả tùy truy vấn. Danh sách quá hạn giảm từ 87 xuống 48 ms, nhưng các phép
đếm trên mọi task mà một người được xem vẫn rất chậm trên bộ dữ liệu này: đếm mọi task quá hạn mất 60
giây trước và 63 và 76 giây trong hai lần đo sau, task chưa giao đã quá hạn 21 giây trước và 33 và 36
giây sau (các lần chạy nặng CPU nên thời gian dao động; sau khi có chỉ mục câu lệnh đọc ít buffer hơn).
Ở đó engine ghép từng task ứng viên với các quyền của người dùng, và không chỉ mục nào đã đo khắc phục
được; trang khối lượng công việc và dashboard không nên dựa vào các phép đếm như vậy trên bảng task rất
lớn.

<a id="lay-job"></a>**Lấy job.** Không có chỉ mục, mỗi lần lấy job đọc toàn bộ `ACT_RU_JOB`, trên mọi
node, vài giây một lần; khi có nhiều timer tương lai, đây là tải cố định lớn nhất engine đặt lên cơ sở
dữ liệu. Phiên bản đầu với chỉ mục tăng dần làm một trường hợp tệ hơn nhiều: với
`jobExecutorAcquireByDueDate=true` và `ensureJobDueDateNotNull=false`, câu lệnh là "đến hạn hoặc
không có hạn, sắp theo hạn, lấy ba job đầu", và PostgreSQL duyệt toàn bộ chỉ mục theo thứ tự khi
không có job đến hạn (990.000 mục chỉ mục, 228 ms → 1.145 ms mỗi lần). Chỉ mục được phát hành là
`NULLS FIRST`, thứ tự không phục vụ được phép sắp xếp đó, nên planner lấy các job đến hạn qua chỉ mục
rồi sắp xếp (15 ms). Khi có 100.000 job đến hạn, phép sắp xếp đó tốn hơn việc duyệt theo thứ tự
(83.000 so với 42.000 buffer), nhưng đó là tình trạng tồn đọng tạm thời chứ không phải trạng thái
thường. Với `ensureJobDueDateNotNull=true`, chỉ mục được phát hành đo được 33 ms mỗi lần (trung vị;
nhỏ nhất 19 ms), so với 7,5 ms của phiên bản tăng dần và 156 ms khi không có chỉ mục. MySQL, MariaDB, SQL Server và H2 vốn xếp NULL lên đầu; Oracle và Db2 xếp NULL xuống cuối
giống PostgreSQL và chưa được kiểm chứng (xem phần đầu script).

Chỉ mục ưu tiên `(PRIORITY_ DESC, DUEDATE_)` mà tài liệu Camunda gợi ý cho
`jobExecutorAcquireByPriority=true` có cùng cái bẫy, nên script không kèm theo. Đo khi lấy job theo
ưu tiên (API, trung vị): không chỉ mục 238 ms khi không có job đến hạn và 320 ms khi có 1.000 job
đến hạn; `FASTI_IDX_JOB_DUEDATE` 11 ms và 52 ms; `FASTI_IDX_JOB_DUEDATE` cộng chỉ mục ưu tiên 711 ms
và 54 ms, vì PostgreSQL duyệt chỉ mục ưu tiên khi không có job đến hạn.

**Chi phí ghi.** Thông lượng engine với 8 luồng (khởi tạo, nhận và hoàn thành một user task, sau đó
một service task bất đồng bộ và một timer qua job executor, bật phân quyền và lịch sử `full`), đo 60
giây sau 15 giây khởi động, xen kẽ các cấu hình:

| Chỉ mục | Số instance được khởi tạo, nhận và hoàn thành mỗi giây trong phút đo, từng lần chạy | Trung bình |
|---|---|---|
| không có | 26,4; 26,3; 27,0 | 26,6 |
| nhóm chính | 25,7; 21,7; 28,2; 31,9 | 26,9 |
| nhóm chính + nhóm hai | 27,0; 27,2 | 27,1 |

Không phát hiện được thông lượng giảm: chênh lệch nằm trong biên độ dao động giữa các lần chạy của
máy dùng chung (±15 %). Điều đó không có nghĩa chi phí ghi bằng không. Trên PostgreSQL, một cập nhật
không phải HOT update sẽ thêm một mục vào mọi chỉ mục của bảng, và các cập nhật của `ACT_RU_TASK` và
`ACT_RU_JOB` đều không phải HOT dù có hay không có chỉ mục mới (schema gốc đã đánh chỉ mục người được
giao và thời điểm cập nhật cuối, còn việc khóa job ghi lại cả dòng), nên mỗi lần cập nhật task và mỗi
lần cập nhật một job chạy được giờ ghi thêm một hoặc hai mục chỉ mục. Tỷ lệ HOT update của
`ACT_RU_EXECUTION` vẫn là 99 %.

**Các ứng viên bị loại** (có trong phân tích, đã đo, không phát hành):

| Chỉ mục | Kết quả |
|---|---|
| `ACT_RU_AUTHORIZATION (USER_ID_, RESOURCE_TYPE_, RESOURCE_ID_) INCLUDE (TYPE_, PERMS_)` | Có hại. Planner chuyển việc kiểm tra quyền của truy vấn task sang một bitmap chứa mọi quyền của người dùng và các nhóm: danh sách task quá hạn khi bật phân quyền 48 ms → 4.187 ms, đếm task của một nhóm ứng viên 133 ms → 17.044 ms (vài truy vấn khác nhanh hơn, ví dụ đếm process instance 164 → 89 ms). 190 MB. |
| `ACT_RU_IDENTITYLINK (GROUP_ID_, TYPE_, TASK_ID_)` | Không cải thiện cỡ một bậc độ lớn: đếm task của một nhóm ứng viên 50.028 → 27.448 buffer, nhưng planner chuyển sang quét bảng task (lần đo đó đọc 8.149 trang từ đĩa, 47 → 125 ms); kể cả task đã giao 40.031 → 30.103 buffer. 124 MB. |
| `ACT_RU_VARIABLE (NAME_, PROC_INST_ID_)` | Không kế hoạch PostgreSQL nào đã đo dùng đến (truy vấn task lọc theo biến quy trình vẫn dùng chỉ mục sẵn có). Chưa đo trên MariaDB (bộ dữ liệu đó không có biến). 136 MB trên bảng runtime bị ghi nhiều nhất. |

### MariaDB 11.4

Bộ dữ liệu MariaDB có cùng hình dạng nhưng bằng một nửa (500.000 process instance, task và job
đang chạy, 900.000 liên kết ứng viên, 1.060.000 bản ghi phân quyền, 500.000 process instance lịch sử
và 785.000 activity instance lịch sử, không có biến): bảng của MariaDB chiếm khoảng gấp đôi dung lượng
của PostgreSQL, và cơ sở dữ liệu đo chạy trong bộ nhớ. InnoDB buffer pool 2 GB, `READ-COMMITTED`. Số
dòng là số dòng server đã duyệt (`r_rows` của `ANALYZE FORMAT=JSON`).

| Truy vấn | Trước | Sau | Chỉ mục |
|---|---|---|---|
| Lấy job, không có job đến hạn | toàn bộ 500.000 dòng, 142 ms (API trung vị 252 ms) | 5.000 dòng, 14 ms (API 18 ms) | `FASTI_IDX_JOB_DUEDATE` |
| … với `jobExecutorAcquireByDueDate=true` (API) | 263 ms | 17 ms | `FASTI_IDX_JOB_DUEDATE` |
| … khi có 1.000 job đến hạn | toàn bộ 437.655 dòng, 320 ms (API 360 ms) | 1.100 dòng, 3,6 ms (API 10 ms) | `FASTI_IDX_JOB_DUEDATE` |
| Task quá hạn, 15 task đầu theo hạn | toàn bộ 500.000 dòng, 121 ms | 15 dòng, 0,1 ms | `FASTI_IDX_TASK_DUE` |
| Đếm task quá hạn (13.800) | toàn bộ 500.000 dòng, 88 ms | 13.827 dòng, 2,1 ms | `FASTI_IDX_TASK_DUE` |
| Đếm task chưa giao đã quá hạn | toàn bộ 500.000 dòng, 88 ms | 5.411 dòng, 0,8 ms | `FASTI_IDX_TASK_ASSIGNEE_DUE` |
| Task quá hạn của một người (đếm) | 100 dòng, 0,28 ms | 6 dòng, 0,02 ms | `FASTI_IDX_TASK_ASSIGNEE_DUE` |
| Task của một người, 15 task đầu theo hạn | 100 dòng và một lần sắp xếp, 0,45 ms | 15 dòng, 0,09 ms | `FASTI_IDX_TASK_ASSIGNEE_DUE` |
| Thông tin người dùng của một người (tra cứu vắng mặt) | toàn bộ 101.600 dòng, 21 ms | 5 dòng, 0,03 ms | `FASTI_IDX_ID_INFO_USER` |
| Một mục thông tin người dùng | toàn bộ 101.600 dòng, 30 ms | 1 dòng, 0,03 ms | `FASTI_IDX_ID_INFO_USER` |
| Lịch sử một định nghĩa, 50 mới nhất | 500.000 mục chỉ mục, 1.074 ms | 25.000 dòng (toàn bộ của định nghĩa; không dừng sớm), 154 ms | `FASTI_IDX_HI_PI_PDEF_START` (nhóm hai) |
| Thống kê hoạt động của một định nghĩa (2.000 dòng) | 37.500 dòng, 77 ms | 2.000 dòng, 5 ms | `FASTI_IDX_HI_AI_PDEF_START` (nhóm hai) |
| Incident đang mở, 50 cũ nhất | toàn bộ 10.000 dòng, 2,5 ms | 50 dòng, 0,1 ms | `FASTI_IDX_INC_TIMESTAMP` (nhóm hai) |

MySQL và MariaDB không có partial index, nên 5.000 dòng của một lần lấy job khi không có gì đến hạn là
các job lỗi và job tạm dừng mà chỉ mục PostgreSQL loại ra. Hai hệ này xếp NULL lên đầu, nên "đến hạn
hoặc không có hạn, sắp theo hạn" là một khoảng của chỉ mục và việc lấy job theo hạn được lợi như mặc
định (không có cái bẫy như trên PostgreSQL). Khi bật phân quyền, MariaDB tính truy vấn con phân quyền
cho từng task ứng viên: các phép đếm và danh sách trên mọi task mà một người được xem mất 16 đến 70
giây cả trước lẫn sau khi có chỉ mục trên bộ dữ liệu này; các chỉ mục không thay đổi điều đó.

Mỗi chỉ mục mất 0,4 đến 2,0 giây để tạo online (`ALGORITHM=INPLACE, LOCK=NONE`) và chiếm 26 đến 29 MB
(`FASTI_IDX_ID_INFO_USER` 6,5 MB).

**Chi phí ghi** (cùng phép đo như trên PostgreSQL, xen kẽ):

| Lần chạy (theo thứ tự) | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|---|---|---|---|---|---|---|---|
| Chỉ mục | không có | nhóm chính | không có | nhóm chính | nhóm chính | không có | nhóm chính |
| Số instance hoàn thành mỗi giây | 13,1 | 12,3 | 11,4 | 9,3 | 10,3 | 10,6 | 9,1 |

Thông lượng giảm đều trong suốt loạt đo ở cả hai cấu hình (từ 13,1 xuống 9,1 mỗi giây, khi dữ liệu lịch
sử và phân quyền của phép đo tích lại), nên không tách được chi phí của chỉ mục khỏi xu hướng đó: giữa
các lần chạy liền kề, lần có chỉ mục nhanh hơn 3 % đến chậm hơn 18 %. Hãy coi chi phí ghi trên MariaDB
là chưa đo được chính xác; nó bị chặn trên bởi một mục chỉ mục thêm cho mỗi lần chèn job và task và một
mục cho mỗi lần nhận task.

## Đo lại

Bộ công cụ đo (SQL sinh dữ liệu, chương trình chạy truy vấn qua engine API, bộ thu kế hoạch truy vấn)
không nằm trong bản phân phối. Phương pháp ở trên đủ để lặp lại: tạo schema bằng engine, sinh dữ liệu
theo hình dạng đã nêu, `ANALYZE`, rồi chạy các truy vấn của engine với `auto_explain` (PostgreSQL)
hoặc general log và `ANALYZE FORMAT=JSON` (MariaDB).
