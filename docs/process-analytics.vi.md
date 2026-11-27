[English](process-analytics.md) | Tiếng Việt

# Phân tích quy trình

Fasti tính phân tích quy trình ngay trong cơ sở dữ liệu, trên toàn bộ lịch sử mà engine vốn đã ghi:
bản đồ nhiệt trên sơ đồ BPMN, các điểm nghẽn của quy trình, process instance mất bao lâu và thay
đổi thế nào theo thời gian, thời gian của user task đi đâu. Không cần kho phân tích riêng, không
cần nhập dữ liệu, không có độ trễ: cái vừa kết thúc một giây trước đã được tính. Có từ Fasti 1.3
trong giao diện web (quy trình → **Phân tích**), qua REST và trong Java.

## Bạn có gì

| | Fasti | Camunda 7 Community Edition |
|---|---|---|
| Bản đồ nhiệt trên sơ đồ | Số lượt, trung vị, P90, P99, tổng thời gian, đang chờ, làm lại, sự cố; giá trị hiện trên từng bước | Chỉ instance đang chạy và sự cố |
| Thời lượng | Phân vị (P50 đến P99), trung bình, lớn nhất, biểu đồ phân bố | — |
| Điểm nghẽn | Xếp theo thời gian dồn vào, kèm phần vượt trung vị | — |
| Trực tiếp | Lượt đang chờ, lượt chậm hơn P90 lịch sử, lượt chờ lâu nhất | Chỉ instance đang chạy |
| Làm lại | Bước chạy lại trong cùng instance, trừ multi-instance | — |
| Mục tiêu | Tỉ lệ instance đạt thời lượng mục tiêu, chính xác | — |
| Xu hướng | Trung vị và P90 theo ngày, tuần hoặc tháng | — |
| User task | Thời gian chờ (đến khi có người nhận) và xử lý, theo task và theo người | — |
| Truy vết | Các lượt đã xong chậm nhất và lượt đang chờ lâu nhất, có link đến instance | — |

Camunda 7 Enterprise có bản đồ nhiệt về số lần chạy và hai báo cáo thời lượng trong Cockpit.
Camunda Optimize, sản phẩm phân tích trả phí cho Camunda 7 và 8, nhập lịch sử vào Elasticsearch
hoặc OpenSearch và so mục tiêu với giá trị trung bình. Fasti không cần cả hai.

## Trong giao diện web

Mở một quy trình trong phần Giám sát và chọn tab **Phân tích**.

- **Khoảng thời gian và phiên bản.** Những gì kết thúc trong 7, 30, 90 hoặc 365 ngày qua, của
  phiên bản này hoặc mọi phiên bản.
- **Mục tiêu.** Nhập một thời lượng (`90m`, `4h`, `2d`): các ô số cho biết tỉ lệ instance hoàn
  thành trong mục tiêu, biểu đồ xu hướng vẽ nó thành một đường.
- **Bản đồ nhiệt.** Chọn màu thể hiện điều gì. Mỗi bước mang giá trị của nó; chọn một bước để xem
  chi tiết: phân vị, phân bố thời lượng, làm lại và sự cố, các lượt chậm nhất và các lượt đang chờ
  lâu nhất, mỗi lượt có link đến instance.
- **Điểm nghẽn.** Các bước xếp theo thời gian dồn vào chúng, cộng mọi lượt. Phần vượt trung vị là
  thời gian tiết kiệm được nếu các lượt chậm nhanh như lượt điển hình.
- **Thời lượng theo thời gian.** Trung vị và P90 của các instance hoàn thành mỗi ngày, tuần hoặc
  tháng.
- **User task.** Thời gian chờ và xử lý theo task và theo người xử lý.

Máy chủ chưa có các endpoint phân tích (Fasti trước 1.3, trong lúc nâng cấp cuốn chiếu) sẽ hiện
thống kê cũ, tính trên trình duyệt từ 2.000 lượt chạy mới nhất.

## Độ chính xác

- Số lượng, tổng, trung bình, nhỏ nhất và lớn nhất là chính xác.
- Phân vị của bước và process instance lấy từ biểu đồ phân bố hai mươi khoảng cho mỗi bậc lũy thừa
  mười, do cơ sở dữ liệu tính, sai lệch khoảng 12 % so với giá trị chính xác. Giao diện đánh dấu
  bằng ≈. Tỉ lệ đạt mục tiêu là chính xác.
- Thời gian chờ và xử lý của user task là chính xác. Chờ tính từ lúc tạo task đến lần giao đầu
  tiên, lấy từ lịch sử identity link mà engine ghi ở history level `full`; task thiếu thông tin
  này được đếm là "không rõ thời gian chờ".
- Làm lại đếm các phạm vi (process hoặc subprocess instance) đã chạy một bước nhiều hơn một lần
  trong khoảng thời gian. Các vòng của một bước multi-instance không phải là làm lại.
- Một lượt được tính vào khoảng thời gian mà nó kết thúc. Những gì đang chạy được tính bất kể
  khoảng thời gian.

## Quyền và tenant

Đọc phân tích cần quyền `READ_HISTORY` trên process definition key, giống thống kê lịch sử của
engine. Khi bật kiểm tra tenant, chỉ lịch sử của các tenant của người dùng và của definition dùng
chung được đọc.

## REST

Tất cả nằm dưới `/analytics/process-definition/key/{key}` (cả `/engine/{name}/analytics/...`), với
các tham số `processDefinitionIdIn`, `tenantIdIn`, `finishedAfter`, `finishedBefore` và
`targetDuration` (mili giây):

| Lệnh gọi | Trả về |
|---|---|
| `GET .../activities` | Mỗi bước một mục: số lượt, thống kê thời lượng, đang chạy quá P90, làm lại, sự cố; `rework=false` bỏ phần làm lại |
| `GET .../rework` | Làm lại theo từng bước, gọi riêng: phần tốn kém nhất khi lịch sử dài |
| `GET .../activities/{activityId}/instances` | Các lượt đã xong chậm nhất, hoặc với `running=true` các lượt đang chạy lâu nhất (`maxResults`, tối đa 200) |
| `GET .../instances` | Instance hoàn thành, bị dừng và đang chạy, thống kê thời lượng, đạt mục tiêu |
| `GET .../trend?boundaries=...` | Theo từng khoảng giữa các mốc (2 đến 63): bắt đầu, hoàn thành, thống kê thời lượng |
| `GET .../user-tasks` | Thời gian chờ và xử lý theo task và theo người (`maxTasks`, mặc định 50.000) |

Chi tiết và ví dụ: tài liệu OpenAPI của REST API (tag *Process Analytics*).

Kết quả được giữ 60 giây cho cùng một truy vấn (`fasti.analytics.cache-ttl`, trên image là
`FASTI_ANALYTICS_CACHE_TTL`, `0` để tắt, số không có đơn vị tính là giây), nên mở lại một góc nhìn
hoặc đồng nghiệp mở cùng góc nhìn sẽ không phải đọc lại lịch sử; quyền vẫn được kiểm tra ở mỗi lần
gọi, còn số liệu đang chạy có thể cũ tối đa chừng ấy. Các lần gọi hỏi cùng một thứ vào cùng lúc chỉ
đọc một lần. Chỉ kết quả của lần gọi có `finishedBefore` mới được giữ: thiếu nó, khoảng thời gian
kết thúc ở mili giây hiện tại, nên sẽ không ai hỏi lại đúng kết quả đó (các lần gọi như vậy vẫn
dùng chung một lần đọc đang chạy). Giao diện web cho khoảng thời gian kết thúc ở phút tròn kế tiếp
(5, 15 và 60 phút với 30, 90 và 365 ngày), nên mọi người xem cùng một khoảng dùng chung một lần
đọc. Mỗi node giữ tối đa 256 kết quả; khi có kết quả mới, các kết quả đã hết hạn nhường chỗ trước,
sau đó đến kết quả cũ nhất.

**Engine được ưu tiên.** Mỗi node chạy cùng lúc tối đa hai lượt quét khoảng thời gian (activities,
rework, user task) (`fasti.analytics.max-concurrent`, trên image là
`FASTI_ANALYTICS_MAX_CONCURRENT`, `0` để không giới hạn), cộng một lượt đọc nhanh (instances, trend,
truy vết), nên các phần nhanh của một màn hình không phải chờ sau các lượt quét. Các lượt khác chờ đến lượt theo thứ tự mà không giữ kết nối CSDL, nên job executor, danh sách
task và health check vẫn có kết nối của chúng. Lần gọi không đến lượt trong 60 giây
(`fasti.analytics.max-wait`, trên image là `FASTI_ANALYTICS_MAX_WAIT`) nhận 503. Mỗi câu lệnh
được chạy tối đa 120 giây (`fasti.analytics.statement-timeout`, trên image là
`FASTI_ANALYTICS_STATEMENT_TIMEOUT`, `0` để không có giới hạn riêng, số không có đơn vị tính là
giây), để hai lượt quét chậm không giữ mãi cả hai lượt và kết nối của chúng; `jdbcStatementTimeout`
thấp hơn của engine vẫn áp dụng. Lần gọi có câu lệnh chạy lâu hơn nhận 503 kèm thông báo đề nghị
chọn khoảng thời gian ngắn hơn (Java: `AnalyticsTimeoutException`, một loại
`AnalyticsBusyException`). Giới hạn được đặt cho từng câu lệnh qua JDBC nên dùng được trên mọi cơ
sở dữ liệu được hỗ trợ. Java: `ProcessAnalytics.setMaxConcurrent(int)`, `setMaxWait(Duration)`,
`setStatementTimeout(Duration)` và `setCacheTimeToLive(Duration)`.

Các thuộc tính `fasti.analytics.*` (và `fasti.workload.cache-ttl`) chỉ được Fasti Run và image
container đọc. Ứng dụng Spring Boot nhúng, Tomcat hay WildFly giữ các giá trị mặc định nêu trên,
trừ khi gọi các phương thức Java này lúc khởi động.

## Java

```java
ProcessAnalyticsQuery query = ProcessAnalytics.query(processEngine)
    .processDefinitionKey("invoice")
    .finishedAfter(thirtyDaysAgo)
    .targetDuration(Duration.ofHours(4).toMillis());

List<ActivityAnalytics> activities = query.activities();
InstanceAnalytics instances = query.instances();
List<SlowInstance> slowest = query.slowest("approveInvoice", 10);
UserTaskAnalytics userTasks = query.userTasks(50_000);
```

`dev.fasti.engine.analytics` trong `fasti-engine`.

## Cách hoạt động và chi phí

Các câu lệnh đọc các bảng lịch sử `ACT_HI_ACTINST`, `ACT_HI_PROCINST`, `ACT_HI_TASKINST`,
`ACT_HI_IDENTITYLINK` và `ACT_HI_INCIDENT` của một quy trình và gom nhóm ngay trong cơ sở dữ liệu.
Chúng dùng SQL thuần mà PostgreSQL, MySQL, MariaDB, Oracle, SQL Server, Db2 và H2 đều chạy được:
một thời lượng được xếp vào khoảng của nó bằng `CASE` lồng nhau của các hằng số, một ngày vào kỳ
của nó bằng `CASE` của các mốc ngày truyền vào, nên không cần hàm ngày tháng, logarit hay phân
vị. Không ghi gì và không đổi schema.

Chi phí tăng theo lượng lịch sử của quy trình trong khoảng thời gian: các câu lệnh đọc các lượt của
quy trình kết thúc trong khoảng đó. Trên `ACT_HI_ACTINST` và `ACT_HI_PROCINST` chúng dùng chỉ mục
`(PROC_DEF_ID_, END_TIME_)` (các phiên bản của quy trình được quy về id). Schema gốc chỉ đánh chỉ
mục `ACT_HI_TASKINST` và `ACT_HI_INCIDENT` theo `PROC_DEF_KEY_`, nên các câu lệnh đó luôn kèm key
(và thêm các phiên bản đã chọn) và chỉ đọc task và incident của đúng quy trình đó. Với quy trình có
nhiều user task, chỉ mục tùy chọn `FASTI_IDX_HI_TI_PDKEY_END` trên
`ACT_HI_TASKINST (PROC_DEF_KEY_, END_TIME_)` giúp phần thời gian user task chỉ đọc các task mới nhất
của khoảng thời gian ([hiệu năng](performance.vi.md)). Khoảng ngắn hơn và một phiên bản thì rẻ hơn. Thời gian user task đọc tối đa `maxTasks` task,
mới nhất trước; phần truy vết đọc tối đa `maxResults` lượt; cả hai giới hạn ngay trong SQL. Ứng
dụng thay `mappings.xml` mặc định bằng bản riêng phải thêm
`dev/fasti/engine/analytics/ProcessAnalytics.xml`; thiếu nó các endpoint trả 501.

## Số đo

PostgreSQL 16 (4 CPU, `work_mem` 16 MB), một quy trình với 200.000 instance trong một năm: 2,45
triệu lượt chạy bước, 832.000 user task, 1,65 triệu identity link, 3.543 instance đang chạy. Trung
vị của 11 lần gọi liên tiếp khi không dùng cache kết quả, bằng tài khoản quản trị:

| Lệnh gọi | 30 ngày | 365 ngày |
|---|---|---|
| `activities?rework=false` (bản đồ nhiệt), một phiên bản | 0,12 s | 0,53 s |
| `activities?rework=false`, mọi phiên bản | 0,13 s | 0,64 s |
| `rework`, một phiên bản / mọi phiên bản | 0,11 / 0,13 s | 0,58 / 1,35 s |
| `instances` | 0,03–0,05 s | 0,08–0,13 s |
| `trend` | 0,06 s | 0,11–0,19 s |
| `user-tasks` (50.000 task mới nhất) | 0,36 s | 0,35–0,51 s |
| lượt chậm nhất / đang chờ lâu nhất của một bước | 0,04–0,07 s | 0,02–0,33 s |

Trên giao diện web, bản đồ nhiệt của một năm, mọi phiên bản, hiện sau 1,0–1,7 s kể từ lúc bấm tab; mở lại cùng góc nhìn thì cache trả lời trong khoảng 0,1 s.

**Khi tải cao**, với thiết lập mặc định: 25 người liên tục mở phân tích (cách nhau một đến ba giây,
khoảng thời gian từ 7 đến 365 ngày) nhận kết quả trong 46 ms (trung vị) và 134 ms (phân vị 95),
PostgreSQL dùng 41 % CPU, không có lỗi, health check dưới 0,2 s. Trong lúc 10 người làm vậy, engine
vẫn giữ khoảng hai phần ba thông lượng job (ít hơn 28–35 % job mỗi giây so với khi chạy một mình) và
danh sách task trả lời trong khoảng 40 ms.

So với SQL chính xác trên cùng dữ liệu, mọi số đếm đều khớp, phân vị của bước lệch tối đa 4,7 %,
phân vị instance 0,3 %, xu hướng 3,7 %; thời gian user task là chính xác. MariaDB 11.4 và SQL
Server 2022 trả đúng mọi lệnh gọi trên một lịch sử nhỏ hơn. Chưa chạy trên Oracle và Db2.

Với khoảng thời gian dài trên lịch sử lớn:

- **PostgreSQL:** gom nhóm một năm của một quy trình bận rộn sẽ tràn ra đĩa với `work_mem` mặc định
  (4 MB); đặt 32–64 MB cho user của engine là đủ. Engine yêu cầu PostgreSQL lập kế hoạch riêng cho
  từng lần gọi phân tích (`plan_cache_mode`), giúp khoảng thời gian dài nhanh gấp đôi, và gom nhóm
  thời lượng hoạt động bằng bảng băm (tắt `enable_sort` chỉ cho câu lệnh đó): bộ lập kế hoạch
  tưởng số nhóm nhiều gấp hàng trăm lần thực tế nên sẽ sắp xếp cả khoảng trên đĩa, chậm gấp đôi.
- **MySQL/MariaDB:** tăng `tmp_table_size` và `max_heap_table_size` (ví dụ 256 MB) để việc gom nhóm
  nằm trong bộ nhớ.
- **SQL Server:** thêm `sendStringParametersAsUnicode=false` vào JDBC URL để các tham số id dùng
  được chỉ mục (có ích cho mọi truy vấn của engine).
