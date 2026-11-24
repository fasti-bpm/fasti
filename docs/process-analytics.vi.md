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
| `GET .../activities` | Mỗi bước một mục: số lượt, thống kê thời lượng, đang chạy quá P90, làm lại, sự cố |
| `GET .../activities/{activityId}/instances` | Các lượt đã xong chậm nhất, hoặc với `running=true` các lượt đang chạy lâu nhất (`maxResults`, tối đa 200) |
| `GET .../instances` | Instance hoàn thành, bị dừng và đang chạy, thống kê thời lượng, đạt mục tiêu |
| `GET .../trend?boundaries=...` | Theo từng khoảng giữa các mốc (2 đến 63): bắt đầu, hoàn thành, thống kê thời lượng |
| `GET .../user-tasks` | Thời gian chờ và xử lý theo task và theo người (`maxTasks`, mặc định 50.000) |

Chi tiết và ví dụ: tài liệu OpenAPI của REST API (tag *Process Analytics*).

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

Chi phí tăng theo lượng lịch sử của quy trình trong khoảng thời gian: các câu lệnh về bước đọc các
lượt của quy trình kết thúc trong khoảng đó, qua chỉ mục `(PROC_DEF_ID_, END_TIME_)` khi chọn phiên
bản. Khoảng ngắn hơn và một phiên bản thì rẻ hơn. Thời gian user task đọc tối đa `maxTasks` task,
mới nhất trước. Ứng dụng thay `mappings.xml` mặc định bằng bản riêng phải thêm
`dev/fasti/engine/analytics/ProcessAnalytics.xml`; thiếu nó các endpoint trả 501.
