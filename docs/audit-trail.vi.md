[English](audit-trail.md) | Tiếng Việt

# Nhật ký kiểm toán bất biến

> **Có từ 1.1.0.** Các bản phát hành và image 1.0.x chưa có tính năng này; các thiết lập của nó
> không có tác dụng ở đó.

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

Plugin ghi những gì engine tạo ra, và điều đó phụ thuộc **mức lịch sử**:

| Mức lịch sử | Được ghi |
|---|---|
| `full` (mặc định của Run và container image) | tất cả những gì nêu trên |
| `audit` | `process`, `task` (`create`, `assign`, `update`, `complete`, `delete`), `variable`, `deployment`, `audit` và, nếu bật, `activity`. Không ghi: `operation` (ai nhận việc, thay đổi người dùng và phân quyền), `incident`, `decision`, và các hành động `identity-link-add` / `identity-link-delete` của tác vụ (người và nhóm ứng viên, chủ sở hữu) |
| `activity` | như `audit`, và cũng không có bản ghi `variable` |
| `none` | chỉ có `deployment` và bản ghi của chính nhật ký kiểm toán (`audit`) |

Plugin ghi cảnh báo khi mức thấp hơn `full`. Nhật ký thao tác cần người dùng đã xác thực, như
trong Camunda 7.

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

Mỗi bản ghi lưu `HASH_ = SHA-256(PREV_HASH_ + "\n" + dạng chuẩn của bản ghi)`: mã băm trước (64 ký
tự thập lục phân; 64 số không với bản ghi đầu của chuỗi), một ký tự xuống dòng và dạng chuẩn, tính
trên các byte UTF-8. Dạng chuẩn ghi mỗi trường trên một dòng (có thoát ký tự, phân biệt `null` với
văn bản) và bắt đầu bằng phiên bản `fasti-audit-v1`. Các bản ghi tạo thành **chuỗi**: vị trí 1, 2, 3, … không có khoảng trống, mỗi bản
ghi chứa mã băm của bản ghi trước. Bảng `FASTI_AUDIT_HEAD` giữ vị trí và mã băm của bản ghi cuối
của từng chuỗi.

**Đồng thời mà không có khóa toàn cục.** Mỗi node ghi vào `fasti.audit.chains` chuỗi của riêng
nó (mặc định 4), tên là `<node id>/<slot>`; một giao dịch dùng slot của luồng (thread) chạy nó.
Khi ghi, chỉ dòng head của chuỗi đó bị khóa (bằng một lệnh `UPDATE`, mọi cơ sở dữ liệu đều hỗ trợ
như nhau), vào cuối lệnh, cho đến khi commit. Vì vậy lệnh trên slot khác và node khác không bao
giờ chờ nhau, còn các lệnh cùng slot chỉ xếp hàng trong khoảnh khắc commit. Một giao dịch lồng
("requires new") bắt đầu khi các giao dịch ngoài của cùng luồng đang giữ chuỗi sẽ lấy slot của nó
ở dải kế tiếp: với `chains` = 4, giao dịch ngoài cùng dùng slot 0 đến 3, giao dịch lồng trong đó
dùng 4 đến 7, và cứ thế (các chuỗi `<node id>/end-4`, `<node id>/4` … xuất hiện khi dùng lần đầu). Vì vậy một giao
dịch chỉ chờ các giao dịch cùng độ sâu lồng, không bao giờ chờ một giao dịch ngoài đang tạm dừng,
nên giao dịch lồng của nhiều luồng không thể chặn nhau theo cách mà cơ sở dữ liệu không nhìn
thấy. Một giao dịch trải qua nhiều lệnh giữ chuỗi của nó từ bản ghi đầu tiên đến khi commit, nên
các giao dịch như vậy trên cùng một slot chạy lần lượt. Giao dịch như vậy (một lệnh tham gia giao
dịch Spring bên ngoài) ghi bản ghi trước các lệnh của engine, trên `<node id>/<slot>`, như mọi giao
dịch của bản 1.0 và 1.1, còn lệnh có giao dịch kết thúc cùng nó (mọi yêu cầu và job của Run và
container image, mọi lệnh của engine standalone) ghi bản ghi sau cùng, trên các chuỗi riêng
`<node id>/end-<slot>` (lý do ở [tinh chỉnh khi chạy](tuning-runtime.vi.md#nhật-ký-kiểm-toán)).
Head của dải đầu tiên được tạo khi khởi động, cho mỗi loại chuỗi mà engine ghi vào; head nào thiếu
về sau (dải tiếp theo, head bị xóa từ bên ngoài) được tạo và commit trong một giao dịch ngắn riêng,
để giao dịch cần nó không giữ một dòng mới mà giao dịch khác phải chờ. Thời gian chờ chuỗi có giới
hạn: sau `fasti.audit.lock-timeout-seconds` (mặc định 60) giây, lệnh thất bại mà không ghi gì, thay
vì treo. Đánh đổi: không có một thứ tự toàn cục duy nhất cho mọi bản ghi (mỗi chuỗi có thứ tự; giữa
các chuỗi thì sắp theo thời gian), và việc kiểm tra phải đọc mọi chuỗi.

**Node id** (`fasti.audit.node-id`) mặc định là `<tên engine>@<tên máy>`. Hãy đặt cho mỗi node một
id ổn định và duy nhất; hai node cùng id sẽ dùng chung chuỗi, vẫn đúng (khóa head xếp hàng chúng)
nhưng phải chờ nhau. Trên Kubernetes, tên máy là tên pod, và Deployment đổi tên này mỗi lần khởi
động: mỗi lần khởi động mở chuỗi mới, và chuỗi mà pod bỏ lại trước điểm kiểm tra đầu tiên chỉ được
ký nếu pod tắt êm (xem điểm kiểm tra có chữ ký bên dưới). Hãy chạy engine dạng StatefulSet và đặt
node id bằng tên pod (`FASTI_AUDIT_NODE_ID` lấy từ trường `metadata.name`), để `fasti-0` vẫn là
`fasti-0` sau khi khởi động lại và chuỗi của nó được nối tiếp; với Deployment, hãy đặt
`checkpoint-interval-seconds` ngắn và cho pod đủ thời gian tắt (`terminationGracePeriodSeconds`). Điều này cũng đúng với một node bản này và một node 1.1 cùng id, ví dụ hai JVM
trên một máy trong lúc nâng cấp hoặc quay về: node 1.0 và 1.1 chỉ ghi vào `<node id>/<slot>`, không
bao giờ vào các chuỗi `end-`, nên hai node không bao giờ chờ nhau thành vòng trên một chuỗi. Trong
giai đoạn hỗn hợp như vậy, hãy dọn từ một node bản này: lần dọn của 1.1 khóa head của mọi chuỗi cùng
lúc, trong một giao dịch, và giữ chúng đến khi xóa xong. Khi đó nó có thể bị cơ sở dữ liệu chọn làm
nạn nhân của deadlock, và các lệnh ghi của mọi chuỗi (cả các chuỗi `end-`) phải chờ nó và thất bại
sau `fasti.audit.lock-timeout-seconds` nếu việc xóa kéo dài hơn. Cũng đừng kiểm tra từ một node 1.1
khi một lần dọn của bản này đang chạy: việc kiểm tra của 1.1 chỉ đọc lại một chuỗi hai lần khi một
lần dọn xóa bản ghi dưới nó, còn lần dọn của bản này xóa theo từng lô, nên nó có thể báo một chỗ
đứt không có thật.

**Kiểm tra** (`POST /audit/verify`, hoặc nút trong giao diện web) tính lại từng chuỗi, theo từng
trang, và báo cho mỗi chuỗi nó còn toàn vẹn hay không và đứt ở đâu đầu tiên:

| Lý do | Ý nghĩa |
|---|---|
| `hash-mismatch` | bản ghi đã bị sửa sau khi được ghi |
| `previous-hash-mismatch` | bản ghi không nối tiếp bản ghi trước: một trong hai bị sửa, hoặc có bản ghi bị xóa hay chèn vào giữa |
| `sequence-gap` | thiếu vị trí: bản ghi đã bị xóa |
| `missing-start` | chuỗi không bắt đầu từ 1 và không có bản ghi dọn nào nói về các bản ghi trước đó |
| `purge-unattested` | đã đặt khóa, chuỗi không bắt đầu từ 1, và bản ghi dọn nói về các bản ghi trước đó không có chữ ký hợp lệ của một khóa của engine |
| `head-mismatch` | head chỉ tới bản ghi cuối khác: bản ghi ở cuối bị xóa, hoặc head bị sửa |
| `head-missing` | có bản ghi cho một chuỗi không có head |
| `checkpoint-mismatch` | HMAC của một điểm kiểm tra có chữ ký không khớp: chuỗi trước đó đã bị viết lại |
| `checkpoint-unsigned` | đã đặt khóa mà một điểm kiểm tra không có chữ ký |
| `checkpoint-unknown-key` | đã đặt khóa mà một điểm kiểm tra ghi một khóa không phải khóa hiện tại hay khóa đã thôi dùng |
| `checkpoint-missing` | đã đặt khóa mà không có điểm kiểm tra ở chỗ khoảng cách quy định: điểm kiểm tra đã bị xóa |

Bản thân việc kiểm tra cũng thành một bản ghi (ai kiểm tra, kết quả). Mỗi engine chỉ chạy một
lần kiểm tra tại một thời điểm, vì nó đọc và băm mọi bản ghi; yêu cầu thứ hai bị từ chối (`409`
qua REST, `AuditVerificationRunningException` trong Java) cho đến khi lần đầu xong. Giới hạn này
tính theo từng engine, không phải cả cụm.

**Hai kết luận.** Một lần kiểm tra trả lời hai câu hỏi, cho từng chuỗi và cho cả nhật ký:

- **`intact`** (toàn vẹn): không có gì kiểm được bằng thiết lập của engine bị sai. Các mã băm
  nhất quán từ bản ghi đầu của chuỗi đến head. Khi có khóa, thêm vào đó, mọi điểm kiểm tra và
  mọi bản ghi dọn giải thích phần đầu bị xóa đều có chữ ký hợp lệ (hoặc chữ ký mà thiết lập liệt
  kê là đã thôi dùng, xem dưới), và không thiếu điểm kiểm tra nào.
- **`authenticated`** (đã xác thực): toàn vẹn, có đặt khóa, mọi điểm kiểm tra cùng bản ghi dọn
  của phần đầu bị xóa đã được *kiểm* bằng một khóa mà engine đang giữ, và chuỗi có bản ghi thì có
  ít nhất một điểm kiểm tra như vậy. Chỉ kết luận này mới loại trừ việc người có quyền ghi vào cơ
  sở dữ liệu viết lại một chuỗi cho đến điểm kiểm tra cuối của nó hoặc xóa phần đầu của nó. Nó là
  `false` khi không có khóa, với chuỗi có chữ ký được chấp nhận mà không kiểm (id khóa đã thôi
  dùng), với chuỗi mà phần đầu bị xóa chỉ được nêu bởi một bản ghi dọn không có chữ ký đã kiểm
  (`purgeAttested: false`), và, từ 1.3.2, với chuỗi có bản ghi nhưng chưa có điểm kiểm tra nào
  (không chữ ký nào bao phủ chúng; đến 1.3.1 chuỗi như vậy vẫn được báo là đã xác thực). Với từng
  chuỗi, `authenticatedThrough` là bản ghi cuối cùng được một chữ ký đã kiểm bao phủ (bản ghi ngay
  trước điểm kiểm tra cuối; `0` khi chuỗi chưa được xác thực hoặc rỗng): các bản ghi sau nó chỉ
  được mã băm và head bảo vệ.

Vậy khi không có khóa, "toàn vẹn" nghĩa là "các mã băm nhất quán"; giao diện web nói rõ điều đó,
và nói rõ khi một nhật ký toàn vẹn chưa được xác thực.

**Điểm kiểm tra có chữ ký.** Người có quyền ghi vào cơ sở dữ liệu có thể viết lại cả một chuỗi
từ một bản ghi nào đó trở đi, với mã băm đúng, và dời head. Để phát hiện được điều đó, hãy đặt
một khóa ít nhất 32 byte (`fasti.audit.hmac-key`, tốt nhất qua biến môi trường
`FASTI_AUDIT_HMAC_KEY`, hoặc một tệp trong `fasti.audit.hmac-key-file`). Sau mỗi
`checkpoint-interval` bản ghi (mặc định 1000), chuỗi có một bản ghi `checkpoint` chứa
HMAC-SHA256 của chuỗi, vị trí và mã băm trước nó, cùng khoảng cách dùng khi ghi. Khóa không bao
giờ vào cơ sở dữ liệu; `keyId` (phần đầu SHA-256 của khóa) giúp phân biệt các khóa.

Chuỗi không đạt tới khoảng cách đó sẽ không bao giờ được ký. Vì vậy, khi có khóa, mỗi node còn ký
phần cuối của từng chuỗi của nó có bản ghi sau điểm kiểm tra cuối: mỗi
`checkpoint-interval-seconds` giây (mặc định 300; `0` để tắt) và khi engine tắt êm. Điểm kiểm tra
như vậy là một bản ghi như mọi bản ghi khác, đến sớm hơn chỗ khoảng cách sẽ đặt (việc kiểm tra chấp
nhận điều này, kể cả ở các bản trước), và chỉ giữ head của chuỗi cho riêng nó, chờ head tối đa 5
giây (chuỗi bị giữ lâu hơn sẽ được ký lần sau). Trước khi ký, node kiểm tra bản ghi cuối vẫn khớp
mã băm của nó và head. Node dừng mà không tắt êm (bị kill, hết bộ nhớ) để lại chưa ký nhiều nhất là
các bản ghi của `checkpoint-interval-seconds` giây cuối. Các lệnh còn chạy trong lúc engine tắt (một
request đang được phục vụ, một job của job executor dùng chung với engine khác) cũng vậy: bản ghi của
chúng được ký khi node chạy lại với cùng node id.

Khi đã đặt khóa, thiếu chữ ký cũng là một chỗ đứt như chữ ký sai, vì người viết lại chuỗi không
giả được chữ ký nhưng xóa được chúng:

- mọi điểm kiểm tra phải có chữ ký hợp lệ của khóa hiện tại hoặc của một khóa đã thôi dùng;
- các điểm kiểm tra phải nối tiếp nhau, tính từ bản ghi đầu của chuỗi, đúng khoảng cách dùng khi
  ghi chúng, và sau điểm cuối cùng không được có nhiều bản ghi hơn khoảng cách đó: điểm kiểm tra
  bị xóa sẽ bị phát hiện, kể cả khi engine đã ghi thêm điểm mới lên một chuỗi đã bị viết lại;
- chuỗi không bắt đầu từ bản ghi 1 cần một bản ghi dọn được ký bằng một khóa như vậy.

**Đổi khóa, hoặc đặt khóa về sau.** Đưa các khóa cũ cho engine qua
`fasti.audit.retired-hmac-keys` (phân cách bằng dấu phẩy, ví dụ `FASTI_AUDIT_RETIRED_HMAC_KEYS`):
những gì chúng đã ký vẫn được kiểm, và nhật ký vẫn được xác thực. Nếu một khóa cũ đã mất, hoặc
điểm kiểm tra và bản ghi dọn được ghi trước khi đặt khóa, hãy liệt kê id khóa của chúng (12 ký
tự hex, như `GET /audit` và các điểm kiểm tra hiển thị) hoặc từ `unsigned` trong
`fasti.audit.retired-key-ids`. Khi đó các chữ ký ấy được chấp nhận mà không kiểm, nhưng chỉ trước
điểm kiểm tra đầu tiên của khóa hiện tại trong chuỗi, và chuỗi như vậy được báo là toàn vẹn chứ
không phải đã xác thực ("đã xác minh 3/5" trong giao diện web, `checkpointsVerified` qua REST).
Hãy bỏ mục đó khi một lần dọn đã xóa các bản ghi mà nó bao phủ. Không có cả hai thiết lập, việc
kiểm tra báo `checkpoint-unknown-key`, `checkpoint-unsigned` hoặc `purge-unattested`. Đổi
`checkpoint-interval` từ giá trị này sang giá trị khác thì không sao (mỗi điểm kiểm tra ghi lại
khoảng cách dùng khi ghi nó). Nhưng một đoạn của chuỗi có ký dài hơn khoảng cách mà không có điểm
kiểm tra (điểm kiểm tra từng tắt, `0`, một thời gian; khoảng cách bị giảm trước điểm kiểm tra đầu
tiên của chuỗi; node khác dùng khoảng cách lớn hơn) sẽ bị báo `checkpoint-missing`, vì điểm kiểm
tra bị xóa cũng trông như vậy. Ở đầu chuỗi, hãy liệt kê `unsigned` để chấp nhận (khi đó chuỗi
không được xác thực); ở giữa chuỗi thì nó tồn tại cho đến khi một lần dọn xóa phần đó. Vì vậy hãy
đặt khóa và khoảng cách trước các bản ghi đầu tiên, giống nhau trên mọi node của cụm.

Mỗi điểm kiểm tra cũng được ghi vào log của ứng
dụng (`Audit trail checkpoint …`); khi log được chuyển sang kho log, đó là một mốc nằm ngoài cơ sở
dữ liệu. Các bản ghi sau điểm kiểm tra cuối chỉ được chuỗi bảo vệ: hãy lưu các mã băm cuối mà
việc kiểm tra báo (hoặc các dòng log) ở ngoài cơ sở dữ liệu để phát hiện việc cắt bớt hay viết
lại chúng về sau.

## Bất biến, thời hạn lưu giữ và dọn

- Ứng dụng chỉ thêm bản ghi. **Việc dọn lịch sử không động tới chúng**: chúng không nằm trong các
  bảng lịch sử, bất kể thời gian sống của lịch sử.
- Bản ghi được giữ cho đến khi bị dọn có chủ đích. `POST /audit/purge`
  `{"before": "…", "exported": true}` (Java: `AuditService#purge`) xóa, theo từng chuỗi, các bản
  ghi từ đầu chuỗi đến bản ghi cuối cùng trước `before`, rồi thêm một bản ghi `purge` nêu vị trí
  và mã băm của bản ghi cuối bị xóa, để phần còn lại của chuỗi vẫn kiểm tra được. Khi có khóa,
  bản ghi dọn được ký (HMAC trên chuỗi, vị trí và mã băm bị xóa, cùng id và vị trí của chính bản
  ghi dọn), và việc kiểm tra chỉ chấp nhận một chuỗi thiếu phần đầu khi có chữ ký đó: bản ghi dọn
  do ai đó tự ghi vào cơ sở dữ liệu để che việc xóa bản ghi sẽ bị phát hiện. Không có khóa thì
  bản ghi dọn không chứng minh được gì; chuỗi được báo với `purgeAttested: false` và không bao
  giờ là đã xác thực. Việc dọn chạy trong các giao
  dịch riêng (không nằm trong giao dịch đã ghi bản ghi kiểm toán): mỗi chuỗi theo từng lô tối đa
  `fasti.audit.purge-batch-size` bản ghi (mặc định 10.000), mỗi lô trong một giao dịch với bản ghi
  dọn riêng, nên các lệnh ghi vào chuỗi chỉ phải chờ nhiều nhất một lô. Một chuỗi không dọn được
  (giao dịch khác giữ nó lâu hơn thời gian chờ khóa) không chặn các chuỗi khác: kết quả liệt kê nó
  trong `failedChains` (Java: `AuditPurgeResult#getFailedChains`, nguyên nhân nằm trong log máy
  chủ), và `purgedThrough` cho biết phần đã dọn của nó trước đó; dọn lại để hoàn tất. Chuỗi bị mất
  head cũng không được dọn (liệt kê như vậy): hãy kiểm tra nhật ký. Bản ghi mới hơn
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
  từng chuỗi, hoặc một cảnh báo nêu chuỗi, bản ghi và lý do của chỗ đứt đầu tiên. Dưới "Toàn vẹn"
  có ghi nhật ký đã được xác thực hay chưa, và nếu chưa thì vì sao (không có khóa, hoặc có chữ ký
  không kiểm được); mỗi chuỗi cho biết chữ ký của nó bao phủ đến bản ghi nào, và phần đầu đã dọn
  mà không có chữ ký đã kiểm được đánh dấu trên chuỗi của nó.
- **Lịch sử một lượt chạy → Nhật ký kiểm toán**: các bản ghi của lượt chạy đó, cũ nhất trước
  (hiện khi nhật ký kiểm toán đang bật).

Khi không có plugin, trang cho biết nhật ký kiểm toán đang tắt và cách bật.

## REST API

Tất cả nằm dưới `/engine-rest` (cũng có trong tài liệu OpenAPI, nhãn "Audit Trail"):

| Phương thức và đường dẫn | Chức năng |
|---|---|
| `GET /audit` | `enabled`, cho mọi người gọi và cả khi nhật ký kiểm toán tắt (`200` với `enabled: false`); với người được đọc nhật ký thì có thêm các thiết lập (`nodeId`, `chains`, `types`, `variableValues`, `maskedVariables`, `signed`, `keyId`, `retiredKeyIds`, `checkpointInterval`, `retentionDays`, `historyLevel`); không bao giờ có khóa |
| `GET /audit/event` | Các bản ghi, mới nhất trước; lọc `processInstanceId`, `businessKey`, `actor` (`system` cho engine), `type` (phân cách bằng dấu phẩy), `action`, `tenantId`, `processDefinitionKey`, `entityId`, `chainId`, `from`, `to`, `sortOrder` (`desc`, `asc`), phân trang `firstResult` (tối đa 1.000.000), `maxResults` (tối đa 1000); trang được cắt ngay trong cơ sở dữ liệu |
| `GET /audit/event/count` | Số bản ghi khớp bộ lọc. Với `maxCount` (1 đến 1.000.000) cơ sở dữ liệu dừng khi đã tìm thấy từng đó bản ghi, và kết quả bằng `maxCount` nghĩa là "từng đó trở lên". Nó giới hạn số bản ghi được đếm, không giới hạn số dòng cơ sở dữ liệu phải xem: một bộ lọc không có chỉ mục phục vụ (bên dưới) vẫn có thể đọc nhiều dòng, hoặc cả bảng khi có ít bản ghi khớp hơn |
| `POST /audit/event/export?format=csv\|json` | Phần đang lọc (cùng bộ lọc, dạng tham số truy vấn) dưới dạng tệp, đọc từ một con trỏ cơ sở dữ liệu; được ghi lại là `audit`/`export`. Dùng `POST` vì nó ghi bản ghi đó: `GET` trả `405` và không ghi gì |
| `POST /audit/verify?chainId=` | Kiểm tra mọi chuỗi (hoặc một chuỗi): `intact`, `authenticated`, `records`, `chains` (với `authenticated`, `authenticatedThrough`, `lastHash`, `checkpointsVerified`, `purgedThrough`, `purgeAttested`, `firstBreak`) và `firstBreak`; `409` khi một lần kiểm tra khác đang chạy |
| `POST /audit/purge` | `{"before": "...", "exported": true}`: dọn bản ghi cũ, xem ở trên; kết quả có `removed`, `purgedThrough` (theo từng chuỗi) và `failedChains` |

**Phân quyền**: đọc, xuất và kiểm tra cần là thành viên nhóm quản trị (`camunda-admin`) hoặc có
quyền `READ` trên tài nguyên `SYSTEM`; dọn cần nhóm quản trị hoặc `DELETE` trên `SYSTEM`. Tài
nguyên `SYSTEM` có sẵn trong Camunda 7.24 nên không cần loại tài nguyên mới. Khi không kiểm tra
phân quyền, như mọi nơi trong engine, ai cũng được. Điều đó cũng đúng với yêu cầu không có người
dùng đã xác thực, kể cả khi phân quyền đang bật: đó là trường hợp xác thực REST đang tắt, như trong
cấu hình mặc định của Fasti BPM Run (container image thì bật). Mọi endpoint trừ `GET /audit` trả về
`400` khi nhật ký kiểm toán tắt.

**Tenant**: người đọc không phải quản trị viên chỉ xem, đếm và xuất được bản ghi của các tenant
trong phiên xác thực của mình và các bản ghi không có tenant (deployment và thao tác không có
tenant, điểm kiểm tra, lần kiểm tra, lần xuất, lần dọn), như các truy vấn của engine khi việc
kiểm tra tenant đang bật. Việc kiểm tra luôn bao trùm cả chuỗi; nó trả về vị trí và mã băm, không
trả về bản ghi.

**Lỗi**: khi cơ sở dữ liệu lỗi, phản hồi chỉ nêu việc không làm được ("The audit trail could not
read records"), không bao giờ nêu thông báo của cơ sở dữ liệu; thông báo đó nằm trong log máy
chủ. Truy vấn và xuất dừng sau `fasti.audit.query-timeout-seconds` (mặc định 120) giây.

**Bộ lọc nào nhanh**: các trang và phép đếm lọc theo người thực hiện (cả `system`), theo một loại
kèm hành động, hoặc theo khoảng thời gian (riêng hoặc kèm người thực hiện) được đọc theo một chỉ mục
đúng thứ tự của trang, nên cơ sở dữ liệu dừng sau trang đó. Lọc theo thực thể quy trình, theo
business key, theo chuỗi và chỉ theo loại (hoặc nhiều loại) cũng có chỉ mục phục vụ: cơ sở dữ liệu
hoặc đọc các bản ghi khớp rồi sắp xếp, rẻ khi có ít bản ghi, hoặc đọc theo thứ tự của trang và bỏ
qua bản ghi khác, rẻ với loại phổ biến trong mọi khoảng thời gian. Bộ lọc không có chỉ mục phục vụ
thì đọc bản ghi cho đến khi đủ một trang, và đọc cả bảng khi có ít bản ghi khớp: `action` không kèm
`type`, `tenantId`, `processDefinitionKey`, `entityId`, và giới hạn tenant của người đọc không phải
quản trị. Hãy kết hợp chúng với người thực hiện, loại kèm hành động, hoặc khoảng thời gian.

Java: `AuditTrail.of(processEngine)` trả về `AuditService` (`createQuery()`, `verify()`,
`recordExport(…)`, `purge(before)`); `AuditEventQuery#listPage` phân trang trong cơ sở dữ liệu và
`#forEach` đọc một kết quả lớn từ một con trỏ.

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
| `hmac-key` / `hmac-key-file` | không có | khóa ký điểm kiểm tra và bản ghi dọn (ít nhất 32 byte) |
| `retired-hmac-keys` | không có | các khóa cũ, phân cách bằng dấu phẩy: những gì chúng đã ký vẫn được kiểm |
| `retired-key-ids` | không có | id của các khóa cũ đã mất, và `unsigned`: được chấp nhận mà không kiểm, khi đó chuỗi không được xác thực |
| `checkpoint-interval` | `1000` | số bản ghi giữa hai điểm kiểm tra; `0` là không có |
| `checkpoint-interval-seconds` | `300` | khi có khóa: số giây mà sau đó phần cuối của chuỗi được ghi thêm từ điểm kiểm tra cuối sẽ được ký; `0` để chỉ ký phần cuối lúc tắt |
| `retention-days` | `365` | bản ghi mới hơn số ngày này không dọn được |
| `lock-timeout-seconds` | `60` | lệnh chờ chuỗi của nó bao lâu trước khi thất bại; `0` là theo giới hạn của cơ sở dữ liệu |
| `query-timeout-seconds` | `120` | một truy vấn hay lần xuất được chạy bao lâu; `0` là không giới hạn |
| `purge-batch-size` | `10000` | số bản ghi một lần dọn xóa khỏi một chuỗi trong một giao dịch (mỗi lô để lại một bản ghi dọn) |

**Vì sao mặc định tắt ở mọi nơi**: tính năng thêm hai bảng và một lần ghi cho mỗi lệnh tạo lịch
sử, cần chạy trên mọi node của cụm (node không bật sẽ không ghi gì), cần quản lý một khóa để có
bảo đảm mạnh nhất, và một cơ sở dữ liệu có sẵn có thể đang dùng chung với node Camunda 7.24. Mỗi
hệ thống bật nó một cách có chủ đích.

**Bảng.** Với `databaseSchemaUpdate` là `true` hoặc `create` (Run, starter theo mặc định) plugin tự
tạo bảng khi khởi động. Nếu không (container image giữ nguyên cơ sở dữ liệu có sẵn), hãy tạo trước
bằng script `<database>_fasti_audit.sql` của bản phân phối: trong `configuration/sql/create/` của
Fasti BPM Run (`/fasti/configuration/sql/create/` trong container image), trong `sql/create/` của
bản Tomcat và WildFly (cũng có trong jar engine dưới `dev/fasti/engine/audit/db/create`). Script
cùng tên trong `sql/drop/` bên cạnh xóa bảng cùng mọi bản ghi. Nếu thiếu bảng mà
không được tạo, engine từ chối khởi động với thông báo nêu tên script. Có script cho H2,
PostgreSQL, MySQL/MariaDB (dùng `utf8mb4`), Oracle, SQL Server và DB2.

**Chi phí.** Mỗi lệnh tạo lịch sử: một lệnh update và một lệnh select dòng head, một lần insert
theo lô các bản ghi và một lệnh update head, lúc commit; với cập nhật tác vụ thêm một lần đọc có
chỉ mục bảng lịch sử tác vụ (để nhận ra việc giao việc); tính mã băm không đáng kể. Khi giao dịch
kết thúc cùng lệnh (mọi yêu cầu và job của Run và container image, mọi lệnh của engine
standalone), các bản ghi được ghi sau mọi thứ engine ghi cho lệnh đó, nên khóa dòng head chỉ được
giữ trong các lệnh này và lúc commit, kể cả những gì trình quản lý giao dịch tự ghi lúc commit (với
JPA là các thay đổi entity mà Hibernate flush lúc đó, và các lệnh của callback `beforeCommit`; gọi
`entityManager.flush()` trong delegate để gửi chúng trước khi head bị khóa); với mặc định bốn chuỗi
mỗi node, các luồng hiếm khi phải chờ
([tinh chỉnh khi chạy](tuning-runtime.vi.md#nhật-ký-kiểm-toán)).

**Nâng cấp bảng đã có.** Bảng kiểm toán do bản 1.0 hoặc 1.1 tạo chưa có các chỉ mục cho thứ tự của
một trang: `FASTI_IDX_AUDIT_ORDER (TIME_, CHAIN_ID_, SEQ_)`, và cho trang lọc theo người thực hiện
hoặc theo loại và hành động là `FASTI_IDX_AUDIT_ACTOR_ORDER (ACTOR_, TIME_, CHAIN_ID_, SEQ_)` và
`FASTI_IDX_AUDIT_TYPE_ORDER (TYPE_, ACTION_, TIME_, CHAIN_ID_, SEQ_)`. Chạy script
`<database>_fasti_audit_after_1.1.sql` trong `sql/upgrade/` của bản phân phối
(`configuration/sql/upgrade/` của Fasti BPM Run, `/fasti/configuration/sql/upgrade/` trong container
image; trong jar engine là `dev/fasti/engine/audit/db/upgrade/fasti.<database>.upgrade.audit.after_1.1.sql`).
Script tạo ba chỉ mục rồi xóa `FASTI_IDX_AUDIT_TIME`, `FASTI_IDX_AUDIT_ACTOR` và `FASTI_IDX_AUDIT_TYPE`,
vốn được chúng thay thế (mỗi lần insert vẫn cập nhật bảy chỉ mục). Có thể chạy lại: chỉ mục đã có
được bỏ qua, chỉ mục đã mất thì không xóa nữa. Tạo chỉ mục phải đọc cả bảng:

- PostgreSQL tạo chúng `concurrently`, không chặn việc insert bản ghi kiểm toán; chạy script bằng
  `psql` (không dùng `psql -1` hay công cụ bọc script trong một giao dịch). Lần tạo thất bại để lại
  một chỉ mục không hợp lệ; script có hướng dẫn tìm và xóa nó.
- MySQL và MariaDB tạo chúng trực tuyến (`ALGORITHM=INPLACE LOCK=NONE`); script đặt
  `lock_wait_timeout` là 10 giây, nên một lệnh phải chờ giao dịch dài trên bảng sẽ bỏ cuộc thay vì
  bắt mọi lệnh có ghi kiểm toán xếp hàng sau nó: chạy lại sau.
- Oracle và SQL Server tạo chỉ mục không có `ONLINE` bằng cách chặn insert trong lúc đó, và mọi
  lệnh có ghi kiểm toán phải chờ trong khi giữ head của chuỗi, nên các lệnh khác của chuỗi đó thất
  bại sau `fasti.audit.lock-timeout-seconds`. Thêm `ONLINE` (Oracle Enterprise Edition) hoặc
  `WITH (ONLINE = ON)` (SQL Server Enterprise Edition, Azure SQL) nếu phiên bản có, hoặc chạy vào
  lúc vắng.
- DB2 tạo chỉ mục trong khi insert vẫn tiếp tục. DB2 không có `IF [NOT] EXISTS` cho chỉ mục: chạy
  lần hai thì các lệnh lỗi với SQLSTATE 42710 hoặc 42704, không gây hại.

Chưa chạy thì các trang vẫn được đọc như trước, chỉ là không nhanh hơn. Node 1.0 và 1.1 vẫn chạy
bình thường trên bảng đã nâng cấp. Script drop của 1.0 và 1.1 xóa các chỉ mục bị thay thế theo tên
và sẽ lỗi trên bảng đã nâng cấp: hãy xóa bảng bằng script drop của bản này, dùng được cho bảng của
mọi phiên bản. Các bản ghi cùng mili giây ở các chuỗi khác nhau được xếp theo mã chuỗi, đảo lại khi
bản ghi mới nhất đứng trước.

**Oracle và `NLS_SORT`.** Thứ tự của một trang so sánh mã chuỗi như văn bản. Oracle sắp văn bản theo
`NLS_SORT` của phiên, và driver JDBC lấy nó từ ngôn ngữ của JVM: với locale như `vi_VN`, `de_DE` hay
`fr_FR` đó là một cách sắp theo ngôn ngữ (chỉ tiếng Anh và vài ngôn ngữ khác cho `BINARY`). Chỉ mục
giữ thứ tự nhị phân, nên với cách sắp theo ngôn ngữ, Oracle sắp mọi bản ghi khớp bộ lọc cho mỗi
trang thay vì đọc một trang theo chỉ mục (kết quả như nhau, chỉ chậm hơn). Hãy cho các kết nối của
engine `NLS_SORT=BINARY`, ví dụ qua pool (`spring.datasource.hikari.connection-init-sql=alter
session set NLS_SORT=BINARY`), hoặc chạy JVM với `-Duser.language=en`. Khi đó các truy vấn của chính
engine cũng sắp văn bản theo nhị phân, như trên hệ thống tiếng Anh; phép so sánh không bị ảnh hưởng
(`NLS_COMP` vẫn là `BINARY`). Điều này được suy ra từ tài liệu của Oracle, chưa đo: không có cơ sở
dữ liệu Oracle để thử.

## Lưu trữ và tương thích

- **Các bảng của Camunda 7.24 giữ nguyên.** Nhật ký kiểm toán có bảng tùy chọn riêng:
  `FASTI_AUDIT_EVENT` (bản ghi; `TIME_` tính bằng mili giây, nên mã băm không phụ thuộc múi giờ hay
  độ chính xác thời gian) và `FASTI_AUDIT_HEAD` (mỗi chuỗi một dòng).
- **Node Camunda 7.24 và node Fasti không bật plugin** trên cùng cơ sở dữ liệu bỏ qua các bảng
  này; những gì chúng làm không được ghi. Hãy bật plugin cho mọi node cùng lúc.
- **Quay về Camunda 7.24**: để nguyên các bảng hoặc xóa bằng script drop; không có gì khác bị ghi.
- **Quay về Fasti 1.1**: các bảng, các chỉ mục đã nâng cấp và các chuỗi `end-` giữ nguyên; 1.1 đọc,
  kiểm tra và dọn được mọi chuỗi. Muốn xóa bảng về sau, hãy dùng script drop của bản này (xem "Nâng
  cấp bảng đã có").
- Nếu không ghi được vào bảng (bị xóa, hoặc có bản ghi chèn từ bên ngoài vào vị trí kế tiếp), các
  lệnh tạo lịch sử sẽ thất bại thay vì chạy mà không được ghi.

## Giới hạn

- Nhật ký chứng minh bản ghi không bị sửa sau khi được ghi. Nó không chứng minh được rằng mọi việc
  đều đã được ghi: node không bật plugin, mức lịch sử thấp hơn, hoặc sửa trực tiếp bảng engine
  trong cơ sở dữ liệu sẽ đi vòng qua nó.
- Không có khóa ký, người có quyền ghi vào cơ sở dữ liệu có thể viết lại một chuỗi từ một bản ghi
  nào đó trở đi cùng với head, hoặc xóa phần đầu của nó rồi ghi một bản ghi dọn cho phần đó; khi
  ấy việc kiểm tra vẫn báo toàn vẹn, nhưng không bao giờ báo đã xác thực. Có khóa thì chỉ các bản
  ghi sau điểm kiểm tra cuối mới viết lại được: các bản ghi của `checkpoint-interval-seconds` giây
  cuối trước khi điểm tiếp theo được ghi. Hãy lưu các mã băm cuối được báo hoặc các dòng log điểm
  kiểm tra ở ngoài cơ sở dữ liệu.
- Xóa mọi bản ghi và head của một chuỗi chỉ phát hiện được khi đối chiếu với mốc bên ngoài như vậy.
- Một lần dọn xóa các bản ghi của một chuỗi trước một thời điểm theo từng lô, lần lượt từng chuỗi,
  mỗi lô trong một giao dịch riêng với bản ghi dọn riêng: lần dọn chỉ giữ một head mỗi lúc, trong
  một lô. Nếu một lô thất bại, các lô đã xong vẫn được dọn, mỗi lô có bản ghi dọn của nó, và các
  chuỗi khác vẫn được dọn; kết quả liệt kê các chuỗi thất bại.
- Script cho Oracle, SQL Server và DB2 được viết theo kiểu script của engine nhưng chưa được kiểm
  thử ở đây; H2, PostgreSQL 16 và MariaDB 11.4 đã được kiểm thử.
