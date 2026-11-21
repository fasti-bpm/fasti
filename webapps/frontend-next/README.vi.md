[English](README.md) | Tiếng Việt

# Giao diện web mới (frontend-next)

Giao diện web mới của Fasti BPM, xây dựng bằng React, TypeScript và Vite. Hiện gồm khung ứng
dụng (đăng nhập, điều hướng, ngôn ngữ, giao diện sáng/tối), trang chào mừng (ứng dụng được phép
dùng, hồ sơ cá nhân, liên kết nhanh), mục Công việc (Tasklist), mục Giám sát (Cockpit) và mục Quản
trị (Admin). Các ứng dụng cổ điển (`webapps/frontend`) vẫn được giữ nguyên và chạy song song.

## Cách ứng dụng được phục vụ

- Bản dựng nằm trong `target/webapp/app/next/` và được đóng gói vào WAR, WAR Jakarta, webjar
  và Run cùng chỗ với các ứng dụng cổ điển. Địa chỉ: `{appRoot}/app/next/{engine}/`
  (ví dụ `/fasti/app/next/default/`); `{appRoot}/app/next/` chuyển hướng tới engine mặc định.
- Trang `index.html` không phải tệp tĩnh: `ProcessEnginesFilter` đọc trang, thay
  `$APP_ROOT` và `$CSP_NONCE` rồi gửi đi. Plugin `servletTemplate` trong `vite.config.ts`
  đặt các ký hiệu này vào trang đã dựng. Trang phải là ASCII và không chứa ký hiệu lọc của Maven.
- Mọi thứ khác trong `app/next/` là tệp tĩnh. Điều hướng phía trình duyệt dùng phần `#` của
  địa chỉ nên máy chủ không cần quy tắc riêng.
- Ứng dụng dùng lại phiên đăng nhập, cookie và REST API hiện có (`{appRoot}/api/...`). Yêu cầu
  thay đổi dữ liệu gửi kèm header `X-XSRF-TOKEN`. Ứng dụng chỉ gửi yêu cầu tới origin của trang.
  Ngoại lệ duy nhất là chế độ biểu mẫu `separate-origin` (phải bật riêng, xem
  [Biểu mẫu HTML nhúng và cách ly](#biểu-mẫu-html-nhúng-và-cách-ly)): khung biểu mẫu được tải từ
  origin biểu mẫu đã cấu hình, còn yêu cầu REST của nó vẫn đi qua trang chứa.
- Giao diện mới là lối vào mặc định: `{appRoot}/`, `{appRoot}/app/` và trang sau khi đăng nhập
  dẫn tới `{appRoot}/app/next/{engine}/` (trang chào mừng). Các ứng dụng cổ điển vẫn được triển
  khai và mở được ở địa chỉ cũ (`/app/welcome/`, `/app/tasklist/`, `/app/cockpit/`, `/app/admin/`)
  cho những việc giao diện mới chưa làm (ví dụ CMMN). Chúng đã lỗi thời (AngularJS 1.8.2 và
  Bootstrap 3 hết vòng đời): image container không phục vụ chúng (trang chuyển tới mục tương ứng
  của giao diện mới, `FASTI_CLASSIC_WEBAPPS=true` bật lại); Run và Spring Boot starter tắt chúng
  bằng `fasti.webapp.classic.enabled: false`. Khi tắt, máy chủ báo cho giao diện mới qua thẻ
  `<meta name="fasti-classic-webapps" content="false">` và giao diện mới ẩn các liên kết tới
  ứng dụng cổ điển.
- Muốn quay lại lối vào cổ điển: Run và Spring Boot starter dùng thuộc tính
  `fasti.webapp.default-app: welcome` (trong `application.yml`, hoặc biến môi trường
  `FASTI_WEBAPP_DEFAULT_APP=welcome`); WAR cho Tomcat và WildFly dùng tham số
  `defaultApp` của bộ lọc `Engines Filter` trong `WEB-INF/web.xml` (đã có sẵn dạng chú thích).
  Giá trị hợp lệ: `next` (mặc định), `welcome`, `tasklist`, `cockpit`, `admin`; giá trị khác làm
  ứng dụng không khởi động được, để lỗi chính tả không âm thầm đổi lối vào.
- Chính sách CSP: các trang và tệp dưới `/app/next/` được gửi kèm chính sách chặt hơn, không có
  `'unsafe-eval'` và `'unsafe-inline'` trong `script-src` (chỉ nonce của phản hồi và
  `'strict-dynamic'`; `'self'` chỉ là dự phòng cho trình duyệt cũ). Chính sách của các ứng dụng cổ
  điển giữ nguyên. Nếu quản trị viên tự cấu hình `contentSecurityPolicyValue`
  (`camunda.bpm.webapp.header-security.content-security-policy-value`), giá trị đó áp dụng cho
  mọi ứng dụng, kể cả giao diện mới. Không tải tài nguyên từ bên ngoài.
- `config.json` (tùy chọn, cạnh `index.html`): `csrfCookieName` nếu tên cookie CSRF được cấu
  hình khác mặc định, `defaultLocale` (thẻ của một ngôn ngữ có sẵn, ví dụ `vi`; mặc định `en`).

## Phát triển

```
npm ci
npm run dev        # máy chủ phát triển, chuyển tiếp /fasti/api tới http://localhost:18080
npm test           # kiểm thử đơn vị (Vitest, Testing Library)
npm run build      # kiểm tra kiểu, lint, đóng gói
```

Cần Node.js 24 (bản dựng Maven tự tải về). `./mvnw clean verify -pl webapps/assembly-jakarta -am`
dựng cả giao diện cổ điển và giao diện mới; `-Dskip.frontend.build=true` bỏ qua cả hai.

### Kiểm thử đầu-cuối (Playwright)

Bộ kiểm thử trong `e2e/` chạy với một bản phân phối đang chạy (Run, Tomcat hoặc WildFly) và
không thuộc bản dựng mặc định. Bước chuẩn bị chung triển khai các tiến trình, bảng quyết định và
biểu mẫu thử trong `e2e/fixtures` qua REST rồi dọn đi khi xong (mọi tên đều bắt đầu bằng `e2e`).
Mỗi kiểm thử thất bại khi có vi phạm CSP hoặc lỗi trên console.

```
npx playwright install chromium                         # một lần, tải Chromium của Playwright
E2E_BASE_URL=http://localhost:18800/fasti/ npm run e2e  # mặc định là địa chỉ này; người dùng demo/demo
./mvnw verify -Pe2e-next -pl webapps -De2e.baseUrl=http://localhost:18800/fasti/
```

Biến khác: `E2E_USER`, `E2E_PASSWORD`, `E2E_ENGINE`, `E2E_KEEP_DATA=1` (giữ dữ liệu thử).
Báo cáo HTML và dấu vết nằm trong `target/e2e/`.

#### Đăng nhập một lần (SSO)

Bộ riêng `e2e/sso/` kiểm thử đăng nhập qua nhà cung cấp OpenID Connect thật (ví dụ Keycloak)
trên một bản phân phối đã cấu hình SSO: đăng nhập và quay lại đúng trang, ánh xạ người dùng,
nhóm, tenant, đăng xuất kết thúc cả phiên ở nhà cung cấp, bearer token trên `/engine-rest`
(token hợp lệ, thiếu, sai chữ ký, sai audience). Bộ này bị bỏ qua khi chưa đặt
`E2E_OIDC_BASE_URL`, `E2E_OIDC_USER` và `E2E_OIDC_PASSWORD`; không cần bước chuẩn bị dữ liệu.

```
E2E_OIDC_BASE_URL=http://localhost:8080/fasti/ \
E2E_OIDC_USER=alice E2E_OIDC_PASSWORD=... \
E2E_OIDC_GROUPS=camunda-admin,accounting E2E_OIDC_TENANTS=tenant-a \
E2E_OIDC_TOKEN_URL=https://sso.example.com/realms/fasti/protocol/openid-connect/token \
E2E_OIDC_CLIENT_ID=fasti-cli E2E_OIDC_OTHER_CLIENT_ID=other-cli \
npm run e2e:sso
```

`E2E_OIDC_TOKEN_URL` + `E2E_OIDC_CLIENT_ID` (client public cho phép password grant, token có
audience của `fasti.security.oidc.rest.audiences`) bật các kiểm thử bearer token;
`E2E_OIDC_OTHER_CLIENT_ID` là client có audience khác. Khác: `E2E_OIDC_USER_ID` (id người dùng
trong engine nếu khác tên đăng nhập), `E2E_OIDC_ENGINE`, `E2E_OIDC_REST_URL`. Trang đăng nhập của
nhà cung cấp phải có ô `username` và `password` (Keycloak). Báo cáo nằm trong `target/e2e-sso/`.

## Cấu trúc

| Thư mục | Nội dung |
| --- | --- |
| `src/api` | Lớp gọi REST có kiểu, xử lý CSRF và lỗi thống nhất |
| `src/app` | Khung ứng dụng: đăng nhập, điều hướng, bảng lệnh, phiên làm việc |
| `src/welcome` | Trang chào mừng |
| `src/i18n` | Thông điệp: một thư mục mỗi ngôn ngữ trong `locales/`, tiếng Anh là danh mục gốc (xem Ngôn ngữ) |
| `src/theme` | Token thiết kế (màu, khoảng cách, chữ) cho giao diện sáng và tối |
| `src/ui` | Thành phần dùng chung trên nền Radix UI |
| `src/tasklist`, `src/processes` | Mục Công việc và Bắt đầu quy trình; thông báo [bốn mắt](../../docs/four-eyes.vi.md) của công việc (từ 1.1.0) |
| `src/absence` | [Người thay thế khi vắng mặt](../../docs/absence.vi.md) (từ 1.1.0): phần Vắng mặt trong Tùy chọn và trong trang người dùng của mục Quản trị, dải thông báo và ghi chú thay thế trong mục Công việc |
| `src/cockpit` | Mục Giám sát: tổng quan, quy trình, lượt chạy, sự cố, job, lịch sử, triển khai, quyết định, xử lý hàng loạt |
| `src/modeler` | Trình soạn BPMN, DMN và biểu mẫu |
| `src/admin` | Mục Quản trị |
| `src/forms` | Vùng biểu mẫu và các bộ hiển thị biểu mẫu |
| `src/bpmn`, `src/dmn` | Sơ đồ quy trình (bpmn-js) và bảng quyết định (dmn-js) |
| `src/plugins` | Điểm mở rộng |

## Giám sát (Cockpit)

Mục Giám sát chỉ hiện với người dùng được phép dùng ứng dụng `cockpit`; quyền trên từng dữ liệu
do máy chủ kiểm tra, trang hiển thị thông báo khi bị từ chối (403) thay vì hỏng. Mục này gọi REST
API của engine (`/api/engine/engine/{engine}/...`, gồm endpoint thêm comment cho process instance
do Fasti BPM bổ sung) và tài nguyên của plugin `base` của Cockpit cổ điển
(`/api/cockpit/plugin/base/{engine}/...`: thống kê quy trình, danh sách lượt chạy, sự cố, quy
trình con). Lời gọi nằm trong `src/api/` (`cockpit.ts`, `operations.ts`, `maintenance.ts`,
`workload.ts`).

| Địa chỉ (sau `#`) | Nội dung |
| --- | --- |
| `/cockpit` | Tổng quan: các con số chính và biểu đồ (SVG tự vẽ) |
| `/cockpit/processes` | Danh sách quy trình, gộp theo mã, kèm số lượt chạy và sự cố |
| `/cockpit/processes/definition/{id}` | Một phiên bản quy trình: sơ đồ kèm số liệu từng bước, lượt chạy, sự cố, quy trình được gọi, tác vụ nền |
| `/cockpit/processes/key/{key}` | Chuyển tới phiên bản mới nhất của quy trình có mã này |
| `/cockpit/processes/instance/{id}` | Một lượt chạy: sơ đồ, cây bước đang chạy, biến, sự cố, quy trình con, công việc, tác vụ nền, tác vụ ngoài |
| `/cockpit/incidents` | Sự cố của cả hệ thống |
| `/cockpit/deployments`, `/cockpit/deployments/{id}` | Bản triển khai, tệp của chúng, tải tệp lên |
| `/cockpit/decisions`, `/cockpit/decisions/definition/{id}` | Bảng quyết định và các lần đánh giá |
| `/cockpit/batches` | Xử lý hàng loạt |
| `/cockpit/jobs` | Job lỗi của cả engine, thử lại hàng loạt |
| `/cockpit/external-tasks` | Tác vụ ngoài theo topic và worker |
| `/cockpit/workload` | Khối lượng công việc theo người và nhóm |
| `/cockpit/history`, `/cockpit/history/instance/{id}` | Lượt chạy đã kết thúc, dòng thời gian và lịch sử của một lượt chạy |
| `/cockpit/decision-history`, `/cockpit/decision-history/{id}` | Lịch sử đánh giá bảng quyết định |
| `/cockpit/migration` | Di trú lượt chạy sang phiên bản khác |
| `/cockpit/audit` | Nhật ký thao tác (ai đã đổi gì) |
| `/cockpit/audit-trail` | [Nhật ký kiểm toán bất biến](../../docs/audit-trail.vi.md) (từ 1.1.0): bản ghi kèm bộ lọc, xuất CSV và JSON, kiểm tra toàn vẹn; khi tính năng đang tắt, trang cho biết cách bật |
| `/cockpit/cleanup` | Dọn lịch sử (history cleanup) |
| `/cockpit/modeler` | Trình soạn BPMN, DMN và biểu mẫu, triển khai |

Bộ lọc, trang, thẻ đang mở nằm trong chuỗi truy vấn của địa chỉ nên mọi màn hình đều gửi liên kết
được. Thứ tự sắp xếp của bảng và việc tạm ngừng tự làm mới được trình duyệt ghi nhớ. Giá trị của
biến, thông báo lỗi và nội dung tệp luôn được hiển thị dưới dạng văn bản, không bao giờ dưới
dạng HTML. Trang lượt chạy có thêm chỉnh sửa vị trí (modification) và khởi động lại. Chưa có:
CMMN — hãy dùng Cockpit cổ điển cho việc đó.

## Quản trị (Admin)

Mục Quản trị chỉ hiện với người dùng được phép dùng ứng dụng `admin`. Chỉ gọi REST API của engine
(`/api/engine/engine/{engine}/user|group|tenant|authorization|identity/password-policy|telemetry/data`)
và tài nguyên thiết lập ban đầu của Admin cổ điển (`/api/admin/setup/{engine}/user/create`); toàn
bộ lời gọi nằm trong `src/api/admin.ts`. Thao tác được phép đọc từ các liên kết của yêu cầu
`OPTIONS`: khi nguồn danh tính chỉ đọc (ví dụ LDAP) hoặc người dùng thiếu quyền, nút tạo/sửa/xóa
bị ẩn và trang ghi rõ lý do.

| Đường dẫn | Nội dung |
| --- | --- |
| `/admin/users`, `/admin/users/{id}` | Người dùng: tìm, tạo, hồ sơ, đổi mật khẩu, nhóm, đơn vị thuê, xóa |
| `/admin/groups`, `/admin/groups/{id}` | Nhóm: tạo, sửa, thành viên, đơn vị thuê, xóa |
| `/admin/tenants`, `/admin/tenants/{id}` | Đơn vị thuê: tạo, sửa, người dùng, nhóm, xóa |
| `/admin/authorizations` | Phân quyền theo loại tài nguyên: tạo, sửa, xóa |
| `/admin/system` | Phiên bản và môi trường (chỉ đọc; không gửi dữ liệu đi đâu) |
| `/setup` | Tạo quản trị viên đầu tiên khi engine chưa có ai |

Khi engine chưa có quản trị viên, máy chủ chuyển `/app/next/{engine}/` tới
`/app/next/{engine}/setup/#/setup` (thay vì trang thiết lập của Admin cổ điển).

## Biểu mẫu

Vùng biểu mẫu (`FormHost`) không biết biểu mẫu được vẽ thế nào. Nó xác định loại biểu mẫu
(`resolveForm`), chọn bộ hiển thị đã đăng ký và gửi các biến mà bộ hiển thị trả về. Bộ hiển thị có sẵn:
biểu mẫu Camunda Forms (form-js), biểu mẫu sinh từ trường khai báo trong mô hình, biểu mẫu
biến tự do, biểu mẫu HTML nhúng và biểu mẫu ngoài.

Thêm bộ hiển thị mới (ví dụ trình soạn biểu mẫu hay một loại biểu mẫu riêng) không cần sửa Tasklist:

```ts
registerFormRenderer({
  id: 'my-renderer',
  supports: (form, target) => form.info.key?.startsWith('my-forms:') ?? false,
  component: MyRenderer, // nhận FormRendererProps: target, form, disabled, submitLabel, submit
});
```

Bộ hiển thị đăng ký sau được hỏi trước.

### Biểu mẫu HTML nhúng và cách ly

Biểu mẫu nhúng (`embedded:app:...`, `embedded:deployment:...`) chứa HTML và script của người
triển khai quy trình, cần AngularJS, `eval` và script nội tuyến. Chính sách của `/app/next` cấm
những thứ đó và không được nới lỏng, nên biểu mẫu được vẽ trong *khung biểu mẫu*:

- Khung là một trang riêng, `{appRoot}/app/forms/{engine}/`, do giao diện cổ điển dựng
  (`webapps/frontend/ui/forms/`). Khung dùng SDK biểu mẫu nhúng của Tasklist cổ điển
  (`camunda-bpm-sdk-js`, `CamForm` với AngularJS), nên biểu mẫu chạy như trước:
  `cam-variable-name`/`cam-variable-type`, `cam-script` với `camForm`, `$scope` và `inject`,
  `cam-business-key`, `cam-file-download`, tải tệp lên, nút lỗi và escalation, và nút "Lưu" giữ
  giá trị đã nhập trong local storage.
- Khung được gửi kèm chính sách của các ứng dụng cổ điển, trừ `frame-ancestors` và `sandbox`
  thay đổi theo chế độ cách ly (xem `ContentSecurityPolicyProvider`); chính sách do quản trị
  viên tự cấu hình cũng được điều chỉnh như vậy.
- Trang chứa và khung chỉ trao đổi qua `postMessage`, với đúng cửa sổ khung và origin mong đợi;
  thông điệp lạ bị bỏ qua (`src/forms/embedded/protocol.ts`,
  `frontend/ui/forms/client/scripts/protocol.js`). Trang chứa cho biết công việc hoặc sự kiện
  bắt đầu cần vẽ; khung vẽ biểu mẫu rồi báo lại kích thước, tính hợp lệ và kết quả submit.
  Cửa sổ bật lên của biểu mẫu (ví dụ chọn ngày) bị cắt ở mép khung.

Cách ly được chọn bằng `fasti.webapp.forms-isolation` (Run, Spring Boot starter; biến môi trường
`FASTI_WEBAPP_FORMS_ISOLATION`) hoặc tham số `formsIsolation` của bộ lọc `Engines Filter` trong
`WEB-INF/web.xml` (Tomcat, WildFly; đã có sẵn dạng chú thích):

- `sandboxed` (mặc định): khung được sandbox **không** có `allow-same-origin`, nên có origin mờ
  (opaque): không cookie, không phiên đăng nhập, không chạm được tới trang chứa nó. Máy chủ còn
  gửi `sandbox` không có `allow-same-origin` trong CSP của `/app/forms`, nên trang vẫn bị cô lập
  cả khi được mở trực tiếp. Mọi yêu cầu REST của biểu mẫu (qua client `camForm` của SDK hoặc
  `$http` của AngularJS) được gửi lên trang chứa bằng `postMessage`; trang chứa đối chiếu với
  danh sách cho phép gắn với đúng công việc hoặc sự kiện bắt đầu đang mở (`src/forms/embedded/broker.ts`)
  rồi mới thực hiện bằng phiên và token CSRF của người dùng. Được phép: đọc công việc, biểu mẫu,
  biến của công việc đó và của tiến trình chứa nó, tải tệp của biến, sửa biến của công việc,
  hoàn thành / submit, lỗi BPMN và escalation; với biểu mẫu bắt đầu: đọc định nghĩa và biểu mẫu,
  bắt đầu đúng định nghĩa đó; thêm tệp biểu mẫu và người dùng hiện tại. Mọi yêu cầu khác bị từ
  chối (403) và ghi cảnh báo trên console của cả hai trang. Giá trị đã "Lưu" được trang chứa giữ
  (chỉ khóa `camForm:{id}` của biểu mẫu này); tải tệp do trang chứa thực hiện.
- `separate-origin`: khung được phục vụ từ một origin khác (`fasti.webapp.forms-origin`, tham số
  `formsOrigin`, ví dụ `https://forms.example.com`, một tên máy thứ hai trỏ tới cùng máy chủ). Khung
  giữ `allow-same-origin` cho origin đó nên local storage, module và font chạy như cũ; yêu cầu REST
  vẫn đi qua trang chứa như trên. Giao diện mới được phép nhúng origin đó (`frame-src`), khung cho
  phép mọi trang nhúng nó (`frame-ancestors *`) vì nó không có phiên. Không đăng nhập trên origin
  biểu mẫu và nên chặn `/api` của origin đó ở reverse proxy.
- `same-origin`: hành vi cũ: khung dùng chung origin và phiên với giao diện mới. **Rủi ro:** script
  của biểu mẫu chạm được `window.parent` và gọi bất kỳ REST API nào bằng phiên của người dùng. Chỉ
  dùng khi mọi người triển khai quy trình đều đáng tin và biểu mẫu chưa chạy được ở chế độ cách ly.

Giới hạn của chế độ cách ly: script của biểu mẫu chỉ gọi được REST qua `camForm.client` hoặc
`$http`; `fetch`/`XMLHttpRequest` trực tiếp không có phiên. Script tùy biến của Tasklist cổ điển
(`customScripts`) được nạp qua trang chứa nhưng import tương đối bên trong chúng không chạy.

### AngularJS

AngularJS (đã hết hỗ trợ) chỉ còn trong khung biểu mẫu, vì các biểu mẫu nhúng hiện có cần nó.
Không trang nào của `/app/next` nạp AngularJS (kiểm thử `src/app/noAngular.test.ts`). Lộ trình
cho người dùng: chuyển biểu mẫu nhúng sang Camunda Forms (form-js, `camunda-forms:deployment:...`),
được giao diện mới hiển thị trực tiếp, không cần khung:

1. Dựng lại biểu mẫu trong trình soạn form-js (Camunda Modeler hoặc trình soạn biểu mẫu trong
   mục Modeler của giao diện mới), giữ nguyên tên biến (`cam-variable-name` thành `key` của
   trường).
2. Thay `cam-script` bằng các tính năng của form-js (điều kiện, biểu thức FEEL, kiểm tra hợp lệ);
   logic gọi REST chuyển vào listener hoặc delegate phía máy chủ.
3. Triển khai `.form` cùng quy trình, đổi `camunda:formKey` sang `camunda:formRef` (hoặc
   `camunda-forms:deployment:...`), rồi bỏ biểu mẫu HTML cũ.

### Biểu mẫu ngoài

Khóa biểu mẫu là một URL, hoặc `app:...` không có `embedded:`, thuộc về một ứng dụng khác. Giống
Tasklist cổ điển, biểu mẫu được mở trong thẻ mới với tham số truy vấn `taskId` (hoặc
`processDefinitionKey`) và `callbackUrl` (Tasklist của ứng dụng này). Chỉ nhận địa chỉ `http(s)`.

## Ngôn ngữ (i18n)

Mỗi ngôn ngữ là một thư mục `src/i18n/locales/<thẻ BCP 47>/` (ví dụ `de`, `pt-BR`, `zh-CN`) và
được tự phát hiện, không cần sửa file nào khác:

- `manifest.ts`: tên bản ngữ, tên tiếng Anh, hướng chữ (`ltr`/`rtl`), `reviewed`.
- các file còn lại (`core.ts`, `ops.ts`, `tools.ts`, ...): thông điệp, được gộp lại; chia khóa vào
  file nào không quan trọng. Các file này chỉ được `import type`.

Tiếng Anh (`locales/en`) là danh mục gốc, nằm sẵn trong gói và là dự phòng cho khóa còn thiếu;
các ngôn ngữ khác được nạp riêng (một chunk mỗi ngôn ngữ) khi được chọn. Khóa sai là lỗi biên
dịch; khóa thiếu được `npm run i18n:check` báo (chạy trong `npm run lint`). Số nhiều theo
`Intl.PluralRules`: khóa không hậu tố là dạng chung, `khóa.one`, `khóa.few`, `khóa.many`, ... là
biến thể khi ngôn ngữ có. Ngày giờ và số dùng `Intl` với ngôn ngữ đang chọn.

Chữ do chính các trình soạn BPMN, DMN và biểu mẫu (bpmn-js, dmn-js, form-js) hiển thị, như bảng
công cụ, menu ngữ cảnh và nhãn thuộc tính có sẵn, được dịch riêng trong `src/modeler/texts/`. Hiện
mới có tiếng Việt; các ngôn ngữ khác hiển thị phần chữ này bằng tiếng Anh.

Thêm một ngôn ngữ:

1. `npm run i18n:new -- ar` tạo thư mục với manifest và các khóa tiếng Anh ở dạng chú thích.
2. Dịch từng dòng và bỏ dấu chú thích; giữ nguyên `{tham số}`, BPMN/DMN, tên API.
3. `npm run i18n:check` phải sạch. Bản dịch chưa được người bản ngữ duyệt để `reviewed: false`.

Ngôn ngữ viết từ phải sang trái (ví dụ tiếng Ả Rập) đã được chuẩn bị: `dir` của trang theo manifest và CSS
dùng thuộc tính logic (`margin-inline-start`, `inset-inline-end`, ...).

Ngôn ngữ được chọn theo thứ tự: lựa chọn đã lưu, nếu không thì `defaultLocale` trong
`config.json` (mặc định `en`). Ngôn ngữ của trình duyệt chỉ được gợi ý trên trang đăng nhập khi
người dùng chưa chọn.

## Điểm mở rộng (plugin)

Hiện có hai điểm: `shell.navigation` (mục điều hướng kèm một trang) và `tasklist.task.tab`
(thẻ của công việc đang mở). Plugin được đăng ký trong `src/plugins/index.ts` bằng
`registerPlugin(...)` và nằm trong cùng gói.

Bước tiếp theo, khi cần plugin triển khai riêng: máy chủ liệt kê plugin qua cơ chế sẵn có
(`/api/tasklist/plugin/{id}/static/...`), `index.html` nhận danh sách này qua một ký hiệu thay
thế, và ứng dụng nạp từng plugin bằng `import()` (kèm nonce) rồi gọi `registerPlugin`. Phần
này chưa được hiện thực; giao diện `Plugin` hiện tại được giữ đủ nhỏ để không phải thay đổi khi đó.

