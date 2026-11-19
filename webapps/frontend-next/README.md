# Giao diện web mới (frontend-next)

Giao diện web mới của Fasti BPM, xây dựng bằng React, TypeScript và Vite. Hiện gồm khung ứng
dụng (đăng nhập, điều hướng, ngôn ngữ, giao diện sáng/tối), trang chào mừng (ứng dụng được phép
dùng, hồ sơ cá nhân, liên kết nhanh), mục Công việc (Tasklist), mục Giám sát (Cockpit) và mục Quản
trị (Admin). Các ứng dụng cổ điển (`webapps/frontend`) vẫn được giữ nguyên và chạy song song.

_English version below._

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
| `src/tasklist`, `src/processes` | Mục Công việc và Bắt đầu quy trình |
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

---

# New web UI (frontend-next)

The new web UI of Fasti BPM, built with React, TypeScript and Vite. It contains the application
shell (login, navigation, language, light/dark theme), the welcome page (applications the user
may use, own profile, quick links), the Tasklist, the monitoring section (Cockpit) and the
administration (Admin). The classic applications (`webapps/frontend`) are untouched and run side
by side.

## How the application is served

- The build output is `target/webapp/app/next/`; it is packaged into the WAR, the Jakarta WAR,
  the webjar and Run next to the classic applications. URL: `{appRoot}/app/next/{engine}/`
  (e.g. `/fasti/app/next/default/`); `{appRoot}/app/next/` redirects to the default engine.
- `index.html` is not a static file: `ProcessEnginesFilter` reads it, replaces `$APP_ROOT` and
  `$CSP_NONCE` and sends the result. The `servletTemplate` plugin in `vite.config.ts` puts the
  placeholders into the built page. The page must be ASCII and free of Maven filter tokens.
- Everything else in `app/next/` is a static file. Client-side routing uses the `#` part of the
  URL, so the server needs no rule for it.
- The application reuses the existing session, cookies and REST API (`{appRoot}/api/...`).
  Modifying requests carry the `X-XSRF-TOKEN` header. The application sends requests to the
  origin of the page only. The one exception is the opt-in `separate-origin` forms mode (see
  [Embedded HTML forms](#embedded-html-forms)): the form frame is loaded from the configured
  forms origin, while its REST calls are still brokered by the page.
- The new web UI is the default entry point: `{appRoot}/`, `{appRoot}/app/` and the page after
  the login lead to `{appRoot}/app/next/{engine}/` (the welcome page). The classic applications
  stay deployed and reachable at their URLs (`/app/welcome/`, `/app/tasklist/`, `/app/cockpit/`,
  `/app/admin/`) for what the new UI does not cover yet (for example CMMN). They are deprecated
  (AngularJS 1.8.2 and Bootstrap 3 are end of life): the container image does not serve them
  (their pages lead to the matching section of the new UI, `FASTI_CLASSIC_WEBAPPS=true` brings
  them back); Run and the Spring Boot starter turn them off with
  `fasti.webapp.classic.enabled: false`. The server then announces this in
  `<meta name="fasti-classic-webapps" content="false">` and the new UI hides its links to the
  classic applications.
- To restore the classic entry point: Run and the Spring Boot starter read the property
  `fasti.webapp.default-app: welcome` (in `application.yml`, or the environment variable
  `FASTI_WEBAPP_DEFAULT_APP=welcome`); the WARs for Tomcat and WildFly read the init parameter
  `defaultApp` of the `Engines Filter` in `WEB-INF/web.xml` (present there as a comment). Allowed
  values: `next` (default), `welcome`, `tasklist`, `cockpit`, `admin`; any other value stops the
  application from starting, so that a typo does not silently change the entry point.
- Content security policy: pages and files below `/app/next/` are served with a stricter policy
  without `'unsafe-eval'` and `'unsafe-inline'` in `script-src` (only the nonce of the response
  and `'strict-dynamic'`; `'self'` is a fallback for old browsers). The policy of the classic
  applications is unchanged. A policy configured by the operator (`contentSecurityPolicyValue`,
  `camunda.bpm.webapp.header-security.content-security-policy-value`) applies to every
  application, the new web UI included. Nothing is loaded from other hosts.
- `config.json` (optional, next to `index.html`): `csrfCookieName` if the CSRF cookie has a
  custom name, `defaultLocale` (the tag of an available language, e.g. `vi`; default `en`).

## Development

```
npm ci
npm run dev        # development server, proxies /fasti/api to http://localhost:18080
npm test           # unit tests (Vitest, Testing Library)
npm run build      # type check, lint, bundle
```

Node.js 24 is required (the Maven build downloads it). `./mvnw clean verify -pl
webapps/assembly-jakarta -am` builds the classic and the new UI; `-Dskip.frontend.build=true`
skips both.

### End-to-end tests (Playwright)

The suite in `e2e/` runs against a running distribution (Run, Tomcat or WildFly) and is not
part of the default build. The global setup deploys the test processes, decision and form of
`e2e/fixtures` through REST and removes them afterwards (every name starts with `e2e`). Every
test fails on CSP violations and console errors.

```
npx playwright install chromium                         # once, downloads Playwright's Chromium
E2E_BASE_URL=http://localhost:18800/fasti/ npm run e2e  # this address is the default; user demo/demo
./mvnw verify -Pe2e-next -pl webapps -De2e.baseUrl=http://localhost:18800/fasti/
```

Other variables: `E2E_USER`, `E2E_PASSWORD`, `E2E_ENGINE`, `E2E_KEEP_DATA=1` (keeps the test
data). The HTML report and traces are written to `target/e2e/`.

#### Single sign-on (SSO)

The separate suite `e2e/sso/` tests the login through a real OpenID Connect provider (for
example Keycloak) against a distribution configured for SSO: login and return to the requested
page, mapping of user, groups and tenants, logout ending the provider session too, and bearer
tokens on `/engine-rest` (valid, missing, bad signature, wrong audience). It is skipped unless
`E2E_OIDC_BASE_URL`, `E2E_OIDC_USER` and `E2E_OIDC_PASSWORD` are set and needs no seeded data.

```
E2E_OIDC_BASE_URL=http://localhost:8080/fasti/ \
E2E_OIDC_USER=alice E2E_OIDC_PASSWORD=... \
E2E_OIDC_GROUPS=camunda-admin,accounting E2E_OIDC_TENANTS=tenant-a \
E2E_OIDC_TOKEN_URL=https://sso.example.com/realms/fasti/protocol/openid-connect/token \
E2E_OIDC_CLIENT_ID=fasti-cli E2E_OIDC_OTHER_CLIENT_ID=other-cli \
npm run e2e:sso
```

`E2E_OIDC_TOKEN_URL` and `E2E_OIDC_CLIENT_ID` (a public client allowing the password grant whose
tokens carry an audience of `fasti.security.oidc.rest.audiences`) enable the bearer token tests;
`E2E_OIDC_OTHER_CLIENT_ID` is a client with another audience. Others: `E2E_OIDC_USER_ID` (engine
user id if it differs from the login name), `E2E_OIDC_ENGINE`, `E2E_OIDC_REST_URL`. The login page
of the provider must have `username` and `password` fields (Keycloak). Reports go to
`target/e2e-sso/`.

## Structure

| Folder | Content |
| --- | --- |
| `src/api` | Typed REST client with CSRF and uniform error handling |
| `src/app` | Application shell: login, navigation, command palette, session |
| `src/welcome` | Welcome page |
| `src/i18n` | Messages: one folder per language in `locales/`, English is the reference (see Languages) |
| `src/theme` | Design tokens (colour, spacing, type) for the light and the dark theme |
| `src/ui` | Shared components on top of Radix UI |
| `src/tasklist`, `src/processes` | Tasklist and Start process |
| `src/cockpit` | Monitoring: dashboard, processes, instances, incidents, jobs, history, deployments, decisions, batches |
| `src/modeler` | BPMN, DMN and form modeler |
| `src/admin` | Administration |
| `src/forms` | Form area and form renderers |
| `src/bpmn`, `src/dmn` | Process diagram (bpmn-js) and decision model (dmn-js) |
| `src/plugins` | Plugin points |

## Monitoring (Cockpit)

The section is only offered to users who may use the `cockpit` application; the server checks
the permissions on the data, and a page shows a message when it is refused (403) instead of
breaking. The section calls the REST API of the engine (`/api/engine/engine/{engine}/...`,
including the process instance comment endpoint added by Fasti BPM) and the resources of the
`base` plugin of the classic Cockpit (`/api/cockpit/plugin/base/{engine}/...`: process
statistics, instance list, incidents, called processes). The calls are in `src/api/`
(`cockpit.ts`, `operations.ts`, `maintenance.ts`, `workload.ts`).

| Address (after `#`) | Content |
| --- | --- |
| `/cockpit` | Dashboard: the main numbers and charts (own SVG) |
| `/cockpit/processes` | Processes, grouped by key, with instance and incident counts |
| `/cockpit/processes/definition/{id}` | One version of a process: diagram with numbers per activity, instances, incidents, called processes, job definitions |
| `/cockpit/processes/key/{key}` | Redirects to the latest version of the process with that key |
| `/cockpit/processes/instance/{id}` | One instance: diagram, activity tree, variables, incidents, called instances, user tasks, jobs, external tasks |
| `/cockpit/incidents` | Incidents of the whole engine |
| `/cockpit/deployments`, `/cockpit/deployments/{id}` | Deployments, their files, upload |
| `/cockpit/decisions`, `/cockpit/decisions/definition/{id}` | Decisions and their evaluations |
| `/cockpit/batches` | Batches |
| `/cockpit/jobs` | Failed jobs of the whole engine, bulk retries |
| `/cockpit/external-tasks` | External tasks by topic and worker |
| `/cockpit/workload` | Workload per user and group |
| `/cockpit/history`, `/cockpit/history/instance/{id}` | Finished instances, timeline and history of one instance |
| `/cockpit/decision-history`, `/cockpit/decision-history/{id}` | Decision evaluation history |
| `/cockpit/migration` | Migrating instances to another version |
| `/cockpit/audit` | Operation log (who changed what) |
| `/cockpit/cleanup` | History cleanup |
| `/cockpit/modeler` | BPMN, DMN and form modeler, deployment |

Filters, pages and the open tab are part of the query string, so every view can be linked. The
sorting of a table and a paused automatic refresh are remembered by the browser. Variable values,
error messages and file contents are always shown as text, never as HTML. The instance page
also offers instance modification and restart. Not available yet: CMMN — use the classic
Cockpit for it.

## Administration (Admin)

The administration is only shown to users who may use the `admin` application. It only calls the
REST API of the engine (`/api/engine/engine/{engine}/user|group|tenant|authorization|identity/password-policy|telemetry/data`)
and the initial setup resource of the classic Admin (`/api/admin/setup/{engine}/user/create`); all
calls are in `src/api/admin.ts`. What a user may do is read from the links of `OPTIONS` requests:
if the identity provider is read-only (e.g. LDAP) or a permission is missing, the create, edit and
delete actions are hidden and the page says why.

| Path | Content |
| --- | --- |
| `/admin/users`, `/admin/users/{id}` | Users: search, create, profile, password, groups, tenants, delete |
| `/admin/groups`, `/admin/groups/{id}` | Groups: create, edit, members, tenants, delete |
| `/admin/tenants`, `/admin/tenants/{id}` | Tenants: create, edit, users, groups, delete |
| `/admin/authorizations` | Authorizations per resource type: create, edit, delete |
| `/admin/system` | Versions and environment (read only; nothing is sent anywhere) |
| `/setup` | Creates the first administrator of an engine without one |

As long as the engine has no administrator, the server redirects `/app/next/{engine}/` to
`/app/next/{engine}/setup/#/setup` (instead of the setup page of the classic Admin).

## Forms

The form area (`FormHost`) does not know how a form is drawn. It resolves the kind of form
(`resolveForm`), picks the registered renderer and submits the variables the renderer returns.
Built-in renderers: Camunda Forms (form-js), forms generated from the form fields of the model
(rendered natively), the generic variable form, embedded HTML forms and external forms.

### Embedded HTML forms

Embedded forms (`embedded:app:...`, `embedded:deployment:...`, start forms included) contain
markup and scripts of whoever deployed the process; they need AngularJS, `eval` and inline
scripts. The policy of `/app/next` forbids those and is not loosened. Such a form is rendered by
the *form frame* instead:

- The frame is a separate page, `{appRoot}/app/forms/{engine}/`, built by the classic frontend
  (`webapps/frontend/ui/forms/`). It uses the embedded forms SDK of the classic Tasklist
  (`camunda-bpm-sdk-js`, `CamForm` with AngularJS), so forms behave as they did there:
  `cam-variable-name`/`cam-variable-type`, `cam-script` with `camForm`, `$scope` and `inject`,
  `cam-business-key`, `cam-file-download`, file uploads, error and escalation buttons, and the
  "Save" of entered values in the local storage.
- The frame is served with the policy of the classic applications, except `frame-ancestors`
  and `sandbox`, which follow the isolation mode (see `ContentSecurityPolicyProvider`); a policy
  configured by the operator is adapted the same way.
- Host and frame talk by `postMessage` only, with the one frame window and its expected origin;
  unknown messages are dropped (`src/forms/embedded/protocol.ts`,
  `frontend/ui/forms/client/scripts/protocol.js`). The host says which task or start event to
  render; the frame renders the form, then reports its size, validity and the result of a submit.
  Popups of a form (e.g. a date picker) are clipped at the edge of the frame.

The isolation is chosen with `fasti.webapp.forms-isolation` (Run, Spring Boot starter; environment
variable `FASTI_WEBAPP_FORMS_ISOLATION`) or the init parameter `formsIsolation` of the
`Engines Filter` in `WEB-INF/web.xml` (Tomcat, WildFly; present as a comment):

- `sandboxed` (default): the `iframe` is sandboxed **without** `allow-same-origin`
  (`allow-scripts allow-forms allow-popups allow-downloads`), so it has an opaque origin: no
  cookies, no session, no access to the embedding page. The policy of `/app/forms` carries the
  same `sandbox` directive, so the page stays isolated when it is opened on its own. Every REST
  call of a form (the SDK's `camForm` client and AngularJS `$http`) is sent to the host by
  `postMessage`; the host checks it against an allow-list tied to the task or start event being
  shown (`src/forms/embedded/broker.ts`) and performs it with the user's session and CSRF token.
  Allowed: reading the task, its form, its variables and those of its process instance, file
  downloads of those variables, changing task variables, complete / submit, BPMN error and
  escalation; for start forms reading the definition and its form and starting exactly that
  definition; plus the form resource and the current user. Everything else is answered with 403
  and logged on the console of both pages. Stored ("Save") values are kept by the host (only the
  `camForm:{id}` key of this form); downloads are performed by the host.
- `separate-origin`: the frame is served from another origin (`fasti.webapp.forms-origin`, init
  parameter `formsOrigin`, e.g. `https://forms.example.com`, a second host name of the same
  server). It keeps `allow-same-origin` for that origin, so local storage, modules and fonts work
  as usual; REST calls are still brokered by the host. The new web UI may frame that origin
  (`frame-src`); the frame may be framed by anyone (`frame-ancestors *`) since it has no session.
  Do not log in on the forms origin, and block its `/api` at the reverse proxy.
- `same-origin`: the previous behaviour: the frame shares origin and session with the new web UI.
  **Risk:** form scripts can reach `window.parent` and call any REST API with the user's session.
  Only for installations whose process deployers are all trusted and whose forms cannot run
  isolated yet.

Limits of the isolated modes: form scripts reach the REST API through `camForm.client` or
`$http` only; plain `fetch`/`XMLHttpRequest` have no session. Custom scripts of the classic
Tasklist (`customScripts`) are loaded through the host, but relative imports inside them fail.

### AngularJS

AngularJS (end of life) stays only inside the form frame, because existing embedded forms need
it. No page of `/app/next` loads AngularJS (test `src/app/noAngular.test.ts`). The path for
customers is to move embedded forms to Camunda Forms (form-js, `camunda-forms:deployment:...`),
which the new web UI renders natively, without a frame:

1. Rebuild the form in the form-js editor (Camunda Modeler, or the form editor in the Modeler
   section of the new web UI), keeping the variable names (`cam-variable-name` becomes the
   field `key`).
2. Replace `cam-script` with form-js features (conditions, FEEL expressions, validation); logic
   that called the REST API moves to server-side listeners or delegates.
3. Deploy the `.form` with the process, switch `camunda:formKey` to `camunda:formRef` (or
   `camunda-forms:deployment:...`) and retire the HTML form.

### External forms

A form key that is a URL, or `app:...` without `embedded:`, belongs to another application. As
in the classic Tasklist, the form opens in a new tab with `taskId` (or `processDefinitionKey`)
and `callbackUrl` (the Tasklist of this application) as query parameters. Only `http(s)`
addresses are accepted.

A new renderer (a form builder, a custom kind of form) is added without touching the Tasklist:

```ts
registerFormRenderer({
  id: 'my-renderer',
  supports: (form, target) => form.info.key?.startsWith('my-forms:') ?? false,
  component: MyRenderer, // receives FormRendererProps: target, form, disabled, submitLabel, submit
});
```

Renderers registered later are asked first.

## Languages (i18n)

Every language is a folder `src/i18n/locales/<BCP 47 tag>/` (e.g. `de`, `pt-BR`, `zh-CN`) and is
discovered automatically; no other file changes:

- `manifest.ts`: native name, English name, direction (`ltr`/`rtl`), `reviewed`.
- every other file (`core.ts`, `ops.ts`, `tools.ts`, ...): messages, merged; which file holds a
  key does not matter. These files only use `import type`.

English (`locales/en`) is the reference catalogue, bundled, and the fallback for missing keys;
other languages are separate chunks (one per language) loaded when chosen. Unknown keys are
compile errors; missing keys are reported by `npm run i18n:check` (part of `npm run lint`).
Plurals follow `Intl.PluralRules`: the key without suffix is the general form, `key.one`,
`key.few`, `key.many`, ... are variants where the language has them. Dates and numbers use `Intl`
with the chosen language.

Adding a language:

1. `npm run i18n:new -- ar` creates the folder with a manifest and the English keys commented out.
2. Translate each line and remove the comment marker; keep `{placeholders}`, BPMN/DMN, API names.
3. `npm run i18n:check` must be clean. Translations not reviewed by a native speaker keep
   `reviewed: false`.

Right-to-left languages (e.g. Arabic) are prepared: the page `dir` follows the manifest and the
CSS uses logical properties (`margin-inline-start`, `inset-inline-end`, ...).

The language is the saved choice, otherwise `defaultLocale` in `config.json` (default `en`). The
browser language is only suggested on the login page while the user has not chosen one.

## Plugin points

Two points exist: `shell.navigation` (a navigation entry with a page) and `tasklist.task.tab`
(a tab of the open task). Plugins are registered in `src/plugins/index.ts` with
`registerPlugin(...)` and are part of the bundle.

The next step, once separately deployed plugins are needed: the server lists plugins through the
existing mechanism (`/api/tasklist/plugin/{id}/static/...`), `index.html` receives that list
through a placeholder, and the application loads each plugin with `import()` (with the nonce) and
calls `registerPlugin`. This is not implemented yet; the `Plugin` interface is kept small enough
that it will not have to change for it.
