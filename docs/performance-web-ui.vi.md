[English](performance-web-ui.md) | Tiếng Việt

# Lượng request của giao diện web

> **Có từ bản sau 1.1.0.** Các số liệu dưới đây so sánh giao diện web của 1.1.0 với bản phát
> hành tiếp theo. Giao diện web của bản mới vẫn làm việc được với server 1.1.0: khi đó bảng
> khối lượng việc quay về cách đếm từng dòng bằng task query (xem bên dưới).

Các trang của giao diện web tự làm mới khi đang mở. Khi có nhiều nhóm và người dùng, hoặc nhiều
người cùng ngồi trước màn hình, những lần làm mới này chiếm phần lớn việc engine phải trả lời.
Trang này liệt kê mỗi trang gửi những gì và đã thay đổi ra sao.

## Cách đo

`webapps/frontend-next/scripts/request-trace.mjs` mở từng trang bằng Chromium của Playwright
trên một Fasti BPM Run đang chạy (`--webapps --rest --example`, H2), đăng nhập bằng `demo`:

```sh
node scripts/request-trace.mjs --base http://localhost:8080/fasti/ --seed 46 --window 120
```

- `--seed 46` tạo 46 nhóm, 46 người dùng và 20 task (một phần ba đã quá hạn) để bảng khối
  lượng việc có đủ trang; dữ liệu này được xóa sau khi đo.
- Mỗi trang được theo dõi 120 giây sau khi tải xong, con trỏ chuột di chuyển mỗi 10 giây như có
  người đang ngồi xem, sau đó thêm 90 giây ở tab bị ẩn.
- Chỉ các request tới REST API và API của ứng dụng web được tính là "API request"; script,
  style và font chỉ được tính vào "mọi request" của lần tải đầu.

Hai lần đo dùng cùng script, dữ liệu và máy: 1.1.0 (commit `7df3ab864e`) và nhánh này.

## Kết quả

| Trang | API request khi tải | API request mỗi phút | API kB mỗi phút |
|---|---:|---:|---:|
| Cockpit → Workload | 344 → **11** | 509,5 → **2** | 484,3 → **15,9** |
| Cockpit → Dashboard | 46 → **40** | 52 → **18** | 48,1 → **18,4** |
| Tasklist | 13 → 14 | 8 → **4,5** | 52,2 → **46,5** |
| Tasklist: nhận / trả task | 10 / 7 → **8 / 4** | | |
| Process instance | 13 → 13 | 8 → 8 | 15,5 → 15,5 |

Tab bị ẩn không gửi request nào, cả trước và sau: các trang vốn đã dừng khi bị ẩn. Ngoại lệ
duy nhất là mới: khi bật "Báo cho tôi khi có công việc mới" và trình duyệt cho phép thông báo, danh
sách task vẫn đọc trang đầu tiên khi tab bị ẩn (xem mục Tasklist).

### Bảng khối lượng việc

Trước đây bảng gửi ba hoặc bốn task query cho mỗi nhóm và mỗi người (`/task/count` và
`/task?maxResults=1`), khoảng 340 request mỗi lần làm mới khi các trang đầy, cứ 30 giây một lần
và mỗi khi quay lại tab. Nay engine có `GET`/`POST /task/workload` (xem
[hướng dẫn REST](api/README.vi.md#khối-lượng-việc-theo-nhóm-và-theo-người-fasti)), đếm task
đang mở, chưa ai nhận, quá hạn và task cũ nhất theo từng candidate group và từng assignee bằng
vài câu lệnh có nhóm (group by). Các câu lệnh này bao quanh task query của engine, nên kiểm tra
quyền (kể cả quyền bị thu hồi), kiểm tra tenant và cách xử lý CMMN giữ nguyên: số đếm không bao
giờ chứa task mà `GET /task` không trả cho cùng người dùng. Tối đa 200 nhóm và 200 người mỗi
request.

Bảng gửi một `POST /task/workload` mỗi lần làm mới; danh sách nhóm và người dùng được đọc năm
phút một lần. Server chưa có endpoint này (404 hoặc 405), hoặc có nhưng cấu hình MyBatis thiếu
các câu lệnh của nó (501, xem hướng dẫn REST), được hỏi từng dòng như trước: mỗi lần bốn nhóm và
bốn người, mỗi dòng ba hoặc bốn request. Việc endpoint có tồn tại hay không chỉ được ghi nhớ năm
phút, nên trang để mở trong lúc nâng cấp cuốn chiếu (rolling update) hoặc rollback sẽ chuyển sang
cách cũ khi endpoint mất đi và dùng lại nó khi nó trở lại.

### Dashboard

- Số task đang mở, tình trạng giao việc và số task quá hạn lấy từ cùng một request tổng hợp
  thay vì năm lần đếm task.
- Bỏ hai request `statistics-count` vốn không được dùng.
- Batch có lỗi được đếm bằng `/batch/statistics/count` thay vì liệt kê mọi batch.
- Định nghĩa, deployment và xu hướng theo ngày của lịch sử được đọc năm phút một lần thay vì
  30 giây. 22 lần đếm lịch sử vẫn chạy khi tải trang: 8 cho xu hướng và 14 cho số instance
  bắt đầu theo ngày.
- Dòng "Không có gì cần xử lý" chỉ hiện khi đã đếm xong số task quá hạn, và ô số liệu nào không
  đọc được sẽ báo lỗi thay vì chờ mãi.

### Tasklist

- Danh sách filter được đọc không kèm số đếm, năm phút một lần. Số task của các filter (mỗi
  filter là một task query trên server) được đọc hai phút một lần khi khung filter đang mở, một
  lần sau một loạt thao tác trên task thay vì sau từng thao tác, và hoàn toàn không đọc khi
  khung đã đóng khi menu filter không được dùng. Filter đang dùng lấy số đếm từ danh sách
  task.
- Task của filter dùng lần trước được hỏi song song với danh sách filter; hai lần tra business
  key (process và case) chạy cùng lúc; mỗi business key được đọc một lần và giữ năm phút (cho
  tới khi phiên kết thúc) thay vì đọc lại mỗi lần làm mới. Việc không đọc được key của một
  instance (quyền trên task không bao gồm process instance) cũng được ghi nhớ như vậy.
- Chế độ xem theo mức khẩn cấp (quá hạn, sắp đến hạn) không còn đổi query mỗi phút; "bây giờ"
  được lấy lúc đọc danh sách. Với filter đã tắt làm mới, chế độ xem này vẫn dịch theo thời gian
  mỗi phút một lần như trước.
- Khi bật "Báo cho tôi khi có công việc mới", danh sách được đọc ít nhất mỗi phút một lần kể cả khi
  không có ai thao tác, nên task mới được báo trong vòng một phút. Nếu trình duyệt cho phép thông
  báo, danh sách còn được đọc khi tab bị ẩn, đúng lúc thông báo hệ thống được hiển thị; trước
  đây tab bị ẩn không bao giờ làm mới nên thông báo đó không bao giờ xuất hiện.

### Process instance

Cây activity instance giữ lại các nút của phần không thay đổi, nên mỗi lần làm mới chỉ vẽ lại
phần đã thay đổi. Cây được đọc thưa hơn khi lớn (trên 100 hoặc 500 activity instance) hoặc
không thay đổi trong hai hay mười phút; khung đo 120 giây ở trên quá ngắn để thấy điều này.

### Khi không có ai thao tác

Các trang làm mới mỗi 30 giây sẽ chuyển sang 60 giây sau hai phút không có thao tác, và năm phút
sau mười phút. Thao tác đầu tiên đọc ngay những gì đã cũ. Trước đây tần suất không phụ thuộc
vào thao tác của người dùng. Đo bằng `--window 60 --idle 150` (theo dõi 240 giây sau 150 giây
không thao tác): dashboard gửi 9 thay vì 18 API request mỗi phút (9,2 thay vì 18,4 kB), task
list 24,9 thay vì 45,9 kB mỗi phút.

Khi người dùng quay lại, chỉ những danh sách đang hiển thị và tự làm mới mới được đọc ngay; danh
sách mà người dùng đã tạm dừng làm mới được giữ nguyên. Khi con trỏ bàn phím nằm trong một frame
của trang (form nhúng hoặc form bên ngoài của task), người dùng được coi là đang có mặt, vì thao
tác trong đó không tới được trang. Màn hình treo tường không ai chạm vào nhưng cần luôn cập nhật
có thể tắt cơ chế giãn nhịp: `"idlePacing": false` trong `config.json` cho cả bản cài đặt, hoặc
`?idlePacing=false` trong địa chỉ trước dấu `#` (ví dụ
`/fasti/app/next/default/?idlePacing=false#/cockpit`) cho một màn hình.

### Khởi động

Lần vẽ đầu tiên chờ `config.json` tối đa ba giây; không có trả lời thì trang khởi động với cấu
hình mặc định. Tệp vẫn tiếp tục được đọc và áp dụng khi trả lời muộn: ngôn ngữ mặc định của nó
thay cho tiếng Anh nếu người dùng chưa chọn ngôn ngữ, và request làm thay đổi dữ liệu sẽ chờ tệp
này (tối đa 20 giây), nên không bao giờ gửi đi với tên cookie CSRF sai. Khi lỗi kết nối, tệp được
thử lại ba lần. Ngôn ngữ người dùng đã chọn trước đó được tải song song với cấu hình thay vì sau
nó.

## Bundle

Kích thước của `vite build`, đã minify (gzip trong ngoặc):

| Chunk | 1.1.0 | Nay |
|---|---:|---:|
| `index` (entry) | 396,5 kB (123,0) | 91,7 kB (27,0) |
| `react` (React, React DOM, router, scheduler) | nằm trong `index` | 315,1 kB (99,3) |
| `ui` (UI dùng chung, thông điệp tiếng Anh) | 274,2 kB (81,6) | 257,7 kB (76,3) |
| `icons` (icon dùng ở nhiều trang) | 54 file dưới 1,5 kB | 27,4 kB (9,3) |
| Số file JavaScript | 204 | 164 |

Lượng JavaScript tải trước trang đầu tiên gần như không đổi: React và router chuyển sang một
chunk riêng, chỉ thay đổi khi nâng cấp thư viện. Fasti Run, Docker image và Spring Boot starter
phục vụ các tệp có hash dưới `/app/next/assets/` với
`Cache-Control: public, max-age=31536000, immutable`, nên trình duyệt giữ lại chunk có nội dung
không đổi qua các bản phát hành; trên Tomcat và WildFly việc này do servlet container quyết
định. Dòng thời gian của case trong chi tiết task chỉ được tải khi mở tab đó.

Trang để mở trong lúc máy chủ được cập nhật vẫn trỏ tới các tệp của bản build cũ đã không còn.
Phần nào tải sau mà lỗi vì lý do này (tab case hoặc sơ đồ của task đang mở) sẽ báo ngay tại chỗ
rằng đã có phiên bản mới và đề nghị tải lại; phần còn lại của trang, kể cả form task đang điền,
được giữ nguyên.

Chunk entry tăng từ 293 kB (16/11) lên 396 kB (21/11) do các tính năng được thêm cho tới
1.1.0, không phải do nâng cấp thư viện. Source map cho thấy React DOM (207 kB) và React Router (96 kB) là hai
phần lớn nhất. Phần lớn nhất thuộc về ứng dụng trong những gì tải đầu tiên là bộ thông điệp
tiếng Anh (khoảng 128 kB sau minify, nằm trong `ui`), vốn là ngôn ngữ dự phòng của mọi ngôn
ngữ; tách các bộ thông điệp không thuộc phần lõi theo từng khu vực là bước tiếp theo có thể làm.
