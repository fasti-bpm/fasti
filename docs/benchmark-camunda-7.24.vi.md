[English](benchmark-camunda-7.24.md) | Tiếng Việt

# So sánh Fasti BPM với Camunda 7.24.0

Đo ngày 23 và 24/11/2026. Fasti BPM 1.2.0, Fasti BPM 1.2.1 và Camunda Run 7.24.0 chạy trên cùng
một máy, cùng cơ sở dữ liệu, cùng giới hạn CPU và bộ nhớ. Trang này ghi lại mọi khác biệt tìm
thấy, theo cả hai chiều. Cách đo và các script chỉ mục được mô tả trong
[Hiệu năng cơ sở dữ liệu](performance.vi.md). Bản 1.2.1 sửa các chi phí mà lần đo 1.2.0 phát
hiện; số liệu của nó nằm ở mục [Fasti 1.2.1](#fasti-121), các mục sau là số đo của 1.2.0.

## Tóm tắt

| | Kết quả |
|---|---|
| Công việc của engine (job, truy vấn, kế hoạch SQL) | **Như nhau.** SQL và kế hoạch thực thi giống hệt nhau trên 1 triệu task; thông lượng job nằm trong độ dao động giữa các lần chạy. |
| Bảng khối lượng công việc, 20 nhóm và 20 người dùng, 1 triệu task | **1 request thay cho 145, 1,53 s thay cho 3,45 s, đọc ít hơn 17 lần số trang dữ liệu.** |
| Script chỉ mục tùy chọn (đi kèm Fasti) | **Lần quét job khi rảnh 162 ms → 0,1 ms; task quá hạn sắp theo hạn 61 ms → 0,3 ms trong cơ sở dữ liệu.** Đây là chỉ mục SQL thông thường nên cũng giúp Camunda 7.24 y như vậy. |
| Khởi động (1.2.1) | **Gần như nhau**: 6,45 s so với 6,35 s đến khi REST sẵn sàng (1.2.0: 7,66 s). |
| Bộ nhớ (1.2.1) | **Fasti 362 MB, Camunda 393 MB** khi rảnh, theo cấu hình mặc định (1.2.0: 518 MB). |
| Image container (1.2.1) | Fasti khoảng 390 MB so với 338 MB chưa nén (1.2.0: 554 MB, có một Java runtime bị lặp). |
| REST khi bật phân quyền, 2,2 triệu bản ghi phân quyền (1.2.1) | **Fasti nhanh hơn Camunda 7.24 bật phân quyền ở mọi lệnh gọi đã đo**, ví dụ danh sách task quá hạn 14 ms so với 138 ms. Camunda Run mặc định tắt phân quyền, nên nhanh hơn nữa ở chỗ việc kiểm tra có nhiều việc (bảng task 145 request: 3,7 s khi tắt, 8,8 s với Fasti 1.2.1, 20,8 s với Camunda bật phân quyền). |

## Fasti 1.2.1

Đo ngày 24/11/2026 trong cùng một lượt với Fasti 1.2.0 và Camunda Run 7.24.0, trên môi trường mô
tả bên dưới. Fasti 1.2.1 là bản build từ commit phát hành; lần đo khởi động và bộ nhớ dùng schema
có sẵn nên thời gian ngắn hơn ở mục 1.2.0.

**Khởi động, bộ nhớ và image.** Khởi động 5 lần, job 3 lần (mỗi lần 50.000 job), xen kẽ.

| | Sẵn sàng (s) | `Started in` (s) | Bộ nhớ khi rảnh (MB) | Job mỗi giây | Image (MB) |
|---|---|---|---|---|---|
| Camunda 7.24 | 6,35 | 4,52 | 393 | 732 (693–733) | 338 |
| Fasti 1.2.0 | 7,66 | 5,64 | 518 | – | 554 |
| Fasti 1.2.1 | 6,45 | 4,83 | **362** | 756 (710–814) | khoảng 390 |

**REST khi bật phân quyền**, trên cơ sở dữ liệu PostgreSQL lớn bên dưới (1.000.000 task,
2.200.000 bản ghi phân quyền). Camunda Run 7.24.0 cũng chạy với xác thực và phân quyền bật (mặc
định Camunda Run tắt). Trung vị của 50 lần gọi, mili giây, với tài khoản quản trị.

| Lệnh gọi | Camunda mặc định (tắt) | Camunda bật phân quyền | Fasti 1.2.0 | Fasti 1.2.1 |
|---|---|---|---|---|
| Task quá hạn, 50 bản đầu | 12 | 138 | 156 | **14** |
| Task của một người theo hạn | 7 | 60 | 72 | **10** |
| Đếm task của một nhóm ứng viên | 33 | 120 | 120 | **38** |
| Process instance lịch sử, 50 bản đầu | 7 | 71 | 74 | **8** |
| Đếm toàn bộ task | 126 | 1.082 | 1.090 | 929 |
| Bảng task, 145 request (s, trung vị 5 lần) | 3,7 | 20,8 | 21,0 | **8,8** |
| `POST /task/workload` (s, trung vị 5 lần) | – | – | 2,54 | 2,15 |

Những gì đã thay đổi:

- Mỗi lệnh đều đọc mã nhóm của toàn bộ 2,2 triệu bản ghi phân quyền để bỏ các nhóm của người dùng
  không có quyền nào; Camunda 7.24 cũng làm vậy. Bản 1.2.1 chỉ hỏi về các nhóm của chính người
  dùng.
- Truy vấn con phân quyền lọc `TYPE_ IN (0, 1)` thay cho `TYPE_ < 2`, nên PostgreSQL tra các chỉ
  mục unique thay vì đọc cả bảng: danh sách task quá hạn 77 → 1,6 ms trong cơ sở dữ liệu. Các câu
  lệnh workload giữ `TYPE_ < 2`, vì ở đó nhanh hơn 250 lần với người dùng có nhiều quyền.
- Xác thực basic qua REST được ghi nhớ 30 s cùng nhóm và tenant của người dùng: 3 thay cho 6 câu
  lệnh và 1 thay cho 4 transaction mỗi request. Trên cơ sở dữ liệu này nó chỉ tiết kiệm tối đa
  2 ms mỗi lệnh gọi; khác biệt chính đến từ hai thay đổi trên.

Một người dùng có 20.400 quyền (200 task, 3 nhóm) chậm ở cả hai sản phẩm: danh sách task quá hạn
mất khoảng 3,2 s với Fasti 1.2.1 và 3,3 s với Camunda bật phân quyền; đếm toàn bộ task và bảng
task không xong trong 60 s ở cả hai. Nguyên nhân là phép join phân quyền của engine (mỗi task so
với mọi quyền của người dùng), còn cần cải thiện. Đếm process instance của người dùng này giảm từ
174 ms xuống 24 ms.

## Đã so sánh những gì

- **Fasti BPM 1.2.0**: image `fastidev/fasti-bpm:1.2.0` (digest `sha256:1a6d43e5…25969f8`,
  Temurin 21.0.13.1).
- **Camunda 7.24.0**: Camunda Run 7.24.0. Image chính thức `camunda/camunda-bpm-platform` không
  còn tải được từ Docker Hub, nên một image tương đương đã được dựng từ bản phân phối chính thức
  `camunda-bpm-run-7.24.0.tar.gz` (trung tâm tải về của Camunda, SHA-256 `b4bc0583…04ed634`) và
  giai đoạn cuối của Dockerfile chính thức, giữ nguyên (nhánh `7.24`, Alpine, OpenJDK 17), cùng
  các JDBC driver mà Dockerfile đó chọn. Khởi động với `--webapps --rest`.

Cả hai chạy theo cấu hình phát hành, trừ khi ghi chú khác. Để tách ảnh hưởng của cấu hình mặc
định khỏi ảnh hưởng của mã nguồn, Fasti còn được chạy với thiết lập của Camunda Run: 3 đến 10
luồng job executor, 3 job mỗi lần lấy, 10 kết nối cơ sở dữ liệu, REST không xác thực
(`FASTI_INSECURE_DEMO=true`). Bên dưới gọi cấu hình này là "Fasti, cùng thiết lập".

**Môi trường.** Intel Core i5-13400, 32 GB, Windows 11 với Docker Desktop (WSL2). Container ứng
dụng `--cpus 4 --memory 2g`; container cơ sở dữ liệu `--cpus 4`, dữ liệu trên tmpfs. PostgreSQL
16.16 (`shared_buffers=1GB`), MariaDB 11.4.11. Mỗi lúc chỉ một container cơ sở dữ liệu và một
container ứng dụng. Số liệu là trung vị, kèm khoảng nhỏ nhất–lớn nhất.

## Khởi động và bộ nhớ

Thời gian từ `docker run` đến HTTP 200 đầu tiên của `GET /engine-rest/engine`, trên cơ sở dữ liệu
trống, mỗi cấu hình 3 lần. Bộ nhớ là dung lượng thường trú của tiến trình Java 2 phút sau khi
khởi động.

| | Sẵn sàng (s) | Bộ nhớ khi rảnh (MB) |
|---|---|---|
| Camunda 7.24, mặc định (heap 25 % giới hạn) | 7,29 (7,17–7,76) | 353 (349–360) |
| Camunda 7.24, heap 75 % | 6,83 (6,82–6,91) | 442 (439–442) |
| Fasti 1.2.0, mặc định (heap 75 % giới hạn) | 8,30 (8,24–9,10) | 512 (446–560) |
| Fasti 1.2.0, heap 25 % | 8,39 (8,25–8,61) | 447 (424–450) |

Fasti khởi động chậm hơn khoảng 1 s và, với cùng thiết lập heap, dùng thêm khoảng 90 MB bộ nhớ.
Fasti mang theo nhiều thành phần hơn (giao diện web mới, metrics, MCP, đăng nhập một lần) trên
nền mới hơn (Java 21, Spring Boot 4); phần nào gây ra chênh lệch thì chưa đo. CPU khi rảnh của cả
hai dưới 1,5 %.

## Thông lượng job

Một quy trình có 10 service task bất đồng bộ; khởi tạo 5.000 instance khi job executor tắt, sau
đó bật executor để xử lý hết 50.000 job. Mức lịch sử full. Mỗi cấu hình 3 lần, chạy xen kẽ.

| | Job mỗi giây |
|---|---|
| Camunda 7.24 | 891 (851–1.049) |
| Fasti 1.2.0, mặc định | 951 (830–1.077) |
| Fasti 1.2.0, cùng thiết lập | 831 (750–944) |

Các khoảng chồng lên nhau: không có khác biệt nào vượt được độ nhiễu. Cả hai sản phẩm đều dùng
hết 4 CPU ứng dụng và 4 CPU cơ sở dữ liệu, nên đây là công việc của engine, vốn là cùng một mã
nguồn. Thiết lập job executor lớn hơn của Fasti không giúp gì trên 4 CPU với những job không làm
gì.

## Cơ sở dữ liệu lớn (PostgreSQL 16)

1.000.000 task đang chạy (600.000 đã giao cho 5.000 người, khoảng 25.000 quá hạn), 1.600.000 liên
kết ứng viên trong 200 nhóm, 1.000.000 job, 2.200.000 bản ghi phân quyền, 1.000.000 instance lịch
sử; 6,6 GB. Schema do Camunda 7.24 tạo, mọi cấu hình dùng chung một cơ sở dữ liệu. Thời gian câu
lệnh là số của chính cơ sở dữ liệu (`auto_explain` với `ANALYZE, BUFFERS`).

### Kế hoạch SQL

Với cùng thiết lập, Fasti và Camunda 7.24 gửi cùng câu lệnh, có cùng kế hoạch thực thi và đọc cùng
số trang, ở mọi câu lệnh đã đo.

### Script chỉ mục tùy chọn

| Câu lệnh | Không có chỉ mục | Có chỉ mục |
|---|---|---|
| Lấy job khi chưa có job đến hạn (mọi node, mỗi lần quét) | 162 ms, 47.631 trang (quét toàn bộ `ACT_RU_JOB`) | **0,1 ms, 6 trang** |
| Task quá hạn sắp theo hạn, 50 dòng đầu | 61 ms, 31.394 trang | **0,3 ms, 68 trang** |
| Qua REST, `GET /task?dueBefore=now&sortBy=dueDate` | 56 ms | **8 ms** |
| Task của một người theo hạn | 203 trang | 68 trang |

Khi không có client, cả hai sản phẩm quét job mỗi phút một lần; không có chỉ mục thì mỗi lần quét
đọc toàn bộ bảng job (khoảng 370 MB trang dữ liệu). Các chỉ mục là SQL thông thường, không gắn
với Fasti: Camunda 7.24 cũng được lợi y như vậy. Phần Fasti bổ sung là script cho sáu loại cơ sở
dữ liệu, phần kiểm tra lúc khởi động báo khi thiếu chỉ mục, và số đo cho từng chỉ mục. Không chỉ
mục nào làm nhanh phép đếm task của một nhóm ứng viên (23–33 ms cho 5.000 task ở cả hai sản phẩm).

### Bảng khối lượng công việc

Các con số của một màn hình khối lượng công việc theo nhóm: task đang mở, chưa nhận, quá hạn và
task lâu nhất, cho tổng, 20 nhóm và 20 người. Camunda 7.24 không có endpoint cho việc này; client
phải hỏi từng dòng, tức 145 request. Fasti trả lời bằng một `POST /task/workload`. Cả hai cho cùng
số liệu ở cả 40 dòng.

| | Request | Thời gian, trung vị 5 lần | Số trang dữ liệu đọc |
|---|---:|---|---:|
| Camunda 7.24, hỏi từng dòng | 145 | 3,45 s (3,32–3,96) | 2.795.826 |
| Fasti 1.2.0, cùng thiết lập, `POST /task/workload` | 1 | **1,53 s** (1,49–1,58) | 167.904 |
| Fasti 1.2.0 mặc định (bật phân quyền), `POST /task/workload` | 1 | 2,50 s (2,45–2,72) | 283.961 |
| Fasti 1.2.0 mặc định, hỏi từng dòng | 145 | 20,6 s (20,5–21,0) | 13.708.069 |

Endpoint này không nhẹ: nó đếm toàn bộ 1.000.000 task đang mở để ra tổng. Dù vậy nó vẫn nhanh
hơn 2,3 lần so với hỏi từng dòng trên Camunda 7.24 và đọc ít hơn 17 lần số trang.

### Độ trễ REST

Một client, 10 lần gọi khởi động, sau đó đo 50 lần. Trung vị / phân vị 95, đơn vị mili giây.

| Lời gọi | Camunda 7.24 | Fasti, cùng thiết lập | Fasti, mặc định |
|---|---|---|---|
| `GET /task?dueBefore=now&maxResults=50` | 11,6 / 13,8 | 12,8 / 17,1 | 153,7 / 177,0 |
| `GET /task?assignee=…&sortBy=dueDate&maxResults=50` | 7,3 / 9,3 | 8,3 / 17,3 | 74,0 / 91,0 |
| `GET /task/count?candidateGroup=…` | 33,7 / 48,4 | 32,2 / 40,8 | 122,8 / 148,2 |

Cùng thiết lập thì hai bên bằng nhau. Với cấu hình mặc định, Fasti chậm hơn trên cơ sở dữ liệu
này vì mặc định an toàn của nó: REST yêu cầu đăng nhập và bật kiểm tra phân quyền, còn Camunda Run
phát hành với cả hai đều tắt. Khi đó mỗi request phải đọc người dùng, nhóm và tenant của họ, và
mỗi truy vấn task phải join bảng phân quyền, ở đây có 2,2 triệu dòng. Camunda 7.24 khi bật phân
quyền cũng chạy đúng các kiểm tra này; trường hợp đó chưa được đo. Script chỉ mục không giảm được
chi phí này.

## MariaDB 11.4

Chỉ đo Fasti, trên một nửa dữ liệu (500.000 task và job), để kiểm chứng các lựa chọn trong script
chỉ mục.

| | Không có chỉ mục | Có chỉ mục |
|---|---|---|
| Lấy job khi chưa có job đến hạn (500.000 job) | 154 ms, duyệt 500.000 dòng | **0,12 ms, 0 dòng, 24 trang** |
| Như trên, với 50.000 job lỗi và 10.000 job tạm dừng | 164 ms | **2,3 ms** (chỉ mục đơn `DUEDATE_`: 117 ms) |
| Tra cứu vắng mặt, chuỗi ba người thay thế | 2 lần quét toàn bộ `ACT_ID_INFO`, 200.024 dòng, 40,5 ms | 4 lần tìm theo chỉ mục, 12 dòng, 0,53 ms |
| Như trên, toàn bộ lời gọi REST | 86 ms | 51 ms |

Bộ tư vấn hiệu năng đã báo thiếu chỉ mục trên một schema MariaDB mới và không còn báo sau khi chạy
script. Script chạy hai lần liên tiếp không lỗi.

## Giao diện web

Tasklist cổ điển của Camunda 7.24 và giao diện web Fasti là hai sản phẩm khác nhau nên không đo
đối đầu. Tasklist cổ điển gửi 9 request API khi mở và không gửi thêm khi đang mở. Danh sách task
của Fasti gửi 14 request khi mở và khoảng 4,5 request mỗi phút vì tự làm mới. Xem
[Hiệu năng giao diện web](performance-web-ui.vi.md).

## Vấn đề đã biết và bước tiếp theo

- Đã sửa trong 1.2.1: Java runtime bị lặp trong image, việc đọc mã nhóm của mọi bản ghi phân quyền
  ở mỗi lệnh, khởi động chậm hơn và bộ nhớ khi rảnh cao hơn.
- Người dùng có hàng chục nghìn quyền trên cơ sở dữ liệu một triệu task: đếm task không có bộ lọc
  không xong trong thời gian hợp lý, ở Fasti cũng như Camunda 7.24. Giảm được phần này cần một
  cách join phân quyền khác.
- Khởi tạo process instance qua REST chậm hơn khi bật phân quyền (5.000 lần trong 18,6 s, so với
  12,1 s với REST mở của Camunda Run); mỗi lần khởi tạo đều được kiểm tra.

## Chưa đo

MySQL, Oracle, SQL Server và DB2 khi bật phân quyền; nhiều hơn một node; thời gian dừng của bộ
thu gom rác khi tải dồn dập với thiết lập bộ nhớ của 1.2.1; các máy khác ngoài máy ở trên. Trên phần cứng khác số liệu sẽ khác; điều trang này muốn cho thấy là tỉ lệ
giữa các cấu hình.
