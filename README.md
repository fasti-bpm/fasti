# Fasti BPM Platform

**Make Camunda 7 Great Again.** Camunda 7 Community Edition đã dừng ở bản 7.24.0, bản CE cuối
cùng. Fasti BPM nhận tiếp từ đó: cùng engine, cùng cơ sở dữ liệu, tiếp tục vá lỗi và nâng thư
viện, kèm giao diện web mới.

*Make Camunda 7 Great Again: Camunda 7 Community Edition ended with 7.24.0, its final release.
Fasti BPM carries it on: the same engine on the same database, with continued fixes, current
libraries and a new web UI. [English below](#english).*

> **Không liên kết với Camunda.** Fasti BPM là dự án độc lập, không thuộc, không được bảo trợ hay
> chứng thực bởi Camunda Services GmbH. "Camunda" là nhãn hiệu của Camunda Services GmbH và
> chỉ được dùng ở đây để mô tả nguồn gốc mã nguồn.

## Đây là gì

Kho mã này chứa process engine BPMN 2.0 / DMN, REST API, các webapp (Cockpit, Tasklist,
Admin) và các bản phân phối (Run, Tomcat, WildFly) — xuất phát từ tag `7.24.0` của Camunda 7
CE và được vá lỗi, nâng thư viện tiếp từ đó.

Mục tiêu là **tương thích ngược** với ứng dụng đang chạy trên Camunda 7:

- giữ nguyên package `org.camunda.bpm.*`, namespace BPMN `http://camunda.org/schema/1.0/bpmn`
  và tiền tố cấu hình `camunda.bpm.*`;
- giữ nguyên schema cơ sở dữ liệu của 7.24;
- giữ đủ các kiểu triển khai: Spring Boot, Run, Tomcat, WildFly; cả `javax` lẫn `jakarta`.

## Khác gì so với Camunda 7.24.0 CE

- **Bảo mật:** vá các lỗi đã được công bố của dòng 7.x (expression trong query,
  setup lần đầu, quyền trên attachment/comment, quyền trong Cockpit, open redirect) và thêm
  các lớp bảo vệ riêng: allow-list cho expression trong query và filter, kiểm quyền
  attachment/comment ở tầng engine, gia cố bộ lọc bảo mật, header và cookie của webapp.
- **Ổn định:** hết rò rỉ bộ nhớ khi chạy script và biểu thức DMN bằng JavaScript (GraalJS),
  cùng nhiều lỗi engine khác.
- **Nền tảng:** Java 17 trở lên, Spring Boot 4.1, GraalJS 25, Jackson 2.21 và nhiều thư viện
  khác được nâng lên bản đã vá lỗi bảo mật.

Một số bản vá làm thay đổi hành vi mặc định theo hướng an toàn hơn; hãy đọc mục "Cái gì khác"
của [hướng dẫn chuyển đổi](docs/chuyen-tu-camunda-7.md) trước khi nâng cấp.

## Phiên bản

Fasti BPM Platform đánh số theo [Semantic Versioning](https://semver.org/lang/vi/), bắt đầu
từ `1.0.0`. Dòng `1.x` tương thích với ứng dụng viết cho Camunda 7.24.

## Tọa độ Maven

Các artifact của Fasti BPM Platform có tọa độ Maven riêng. **Package Java vẫn là
`org.camunda.bpm.*`**, nên mã nguồn của ứng dụng không phải sửa `import`; tiền tố cấu hình
`camunda.bpm.*`, namespace BPMN/DMN, schema cơ sở dữ liệu và tên module trên WildFly cũng giữ
nguyên. Về mặt Maven, chỉ phần khai báo dependency thay đổi (đường dẫn web app đổi riêng, xem
[Đường dẫn web app](#đường-dẫn-web-app-fasti)):

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`; các nhóm `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` và `org.camunda.template-engines` → `dev.fasti.platform.<tên nhóm>`.
- **artifactId:** tiền tố `camunda-` đổi thành `fasti-`, phần còn lại giữ nguyên.
- **version:** phiên bản của Fasti BPM Platform (xem [Phiên bản](#phiên-bản)), không còn là
  `7.24.0`.

Chưa có bản phát hành nào của các artifact này trên Maven Central. Cho tới khi có, hãy build
từ mã nguồn và đưa artifact vào kho Maven mà dự án của bạn sử dụng.

Nên quản lý phiên bản bằng BOM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.fasti.platform</groupId>
      <artifactId>fasti-bom</artifactId>
      <version>${fasti.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Các artifact hay dùng:

| Camunda 7 | Fasti BPM Platform |
|---|---|
| `org.camunda.bpm:camunda-bom` | `dev.fasti.platform:fasti-bom` |
| `org.camunda.bpm:camunda-engine` | `dev.fasti.platform:fasti-engine` |
| `org.camunda.bpm:camunda-engine-spring` | `dev.fasti.platform:fasti-engine-spring` |
| `org.camunda.bpm:camunda-engine-spring-6` | `dev.fasti.platform:fasti-engine-spring-6` |
| `org.camunda.bpm:camunda-engine-cdi-jakarta` | `dev.fasti.platform:fasti-engine-cdi-jakarta` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-rest` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-webapp` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-webapp` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-security` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-security` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-external-task-client` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-external-task-client` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-test` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-test` |
| `org.camunda.bpm:camunda-engine-rest-core` | `dev.fasti.platform:fasti-engine-rest-core` |
| `org.camunda.bpm:camunda-engine-rest-core-jakarta` | `dev.fasti.platform:fasti-engine-rest-core-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp` | `dev.fasti.platform.webapp:fasti-webapp` |
| `org.camunda.bpm.webapp:camunda-webapp-jakarta` | `dev.fasti.platform.webapp:fasti-webapp-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp-webjar` | `dev.fasti.platform.webapp:fasti-webapp-webjar` |
| `org.camunda.bpm:camunda-external-task-client` | `dev.fasti.platform:fasti-external-task-client` |
| `org.camunda.bpm:camunda-engine-plugin-spin` | `dev.fasti.platform:fasti-engine-plugin-spin` |
| `org.camunda.bpm:camunda-engine-plugin-connect` | `dev.fasti.platform:fasti-engine-plugin-connect` |
| `org.camunda.spin:camunda-spin-dataformat-all` | `dev.fasti.platform.spin:fasti-spin-dataformat-all` |
| `org.camunda.connect:camunda-connect-connectors-all` | `dev.fasti.platform.connect:fasti-connect-connectors-all` |
| `org.camunda.bpm.dmn:camunda-engine-dmn` | `dev.fasti.platform.dmn:fasti-engine-dmn` |
| `org.camunda.bpm.model:camunda-bpmn-model` | `dev.fasti.platform.model:fasti-bpmn-model` |
| `org.camunda.bpm:camunda-bpm-assert` | `dev.fasti.platform:fasti-bpm-assert` |
| `org.camunda.bpm:camunda-bpm-junit5` | `dev.fasti.platform:fasti-bpm-junit5` |

Thư viện FEEL `org.camunda.feel:feel-engine` là thư viện bên ngoài, không thuộc kho mã này
và giữ nguyên tọa độ.

### Thư viện bên thứ ba vẫn kéo theo engine gốc

Vì package không đổi, engine gốc (`org.camunda.bpm:camunda-engine`) và `fasti-engine` chứa
các class trùng tên. Maven coi đó là hai thư viện không liên quan và không tự loại bớt bên
nào. Nếu một thư viện bên thứ ba (ví dụ một community extension) phụ thuộc vào các artifact
gốc, hãy loại chúng ra và khai báo artifact tương ứng của Fasti:

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>some-camunda-extension</artifactId>
  <version>1.2.3</version>
  <exclusions>
    <exclusion>
      <groupId>org.camunda.bpm</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.model</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.dmn</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.commons</groupId>
      <artifactId>*</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

Lệnh `mvn dependency:tree -Dincludes="org.camunda*"` liệt kê những artifact gốc còn sót lại
(`org.camunda.feel:feel-engine` là hợp lệ, đừng loại nó).

### Kiểm tra khi khởi động

Khi được khởi tạo, engine kiểm tra class path và **từ chối chạy** nếu tìm thấy các class của
process engine ở nhiều hơn một thư viện; lỗi `ENGINE-12021` liệt kê vị trí của từng thư viện.
Spring Boot starter thực hiện cùng phép kiểm tra đó ngay khi ứng dụng khởi động. Thư viện chỉ
nhìn thấy được qua class loader cha (ví dụ engine dùng chung của máy chủ bên cạnh engine
nhúng trong một ứng dụng web) không bị tính. Nếu cấu hình của bạn là có chủ ý, có thể tắt
kiểm tra bằng system property:

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

Giới hạn: nếu thư viện engine gốc đứng trước `fasti-engine` trên class path thì engine được
dựng bằng các class của thư viện gốc, vốn không có bước kiểm tra này. Khi đó chỉ ứng dụng
dùng Spring Boot starter của Fasti mới được chặn lại; với các cách tích hợp khác hãy tự rà
soát dependency như mô tả ở trên.

## Chuyển từ Camunda 7, image container

Fasti BPM chạy trên cơ sở dữ liệu của Camunda 7.24 mà không đổi schema; có thể thay hẳn, chạy
song song hoặc quay lại. Xem [docs/chuyen-tu-camunda-7.md](docs/chuyen-tu-camunda-7.md); image
container nhận các biến `DB_*` của image Run trước đây: [distro/docker/README.md](distro/docker/README.md).

## Đường dẫn web app: `/fasti`

Cockpit, Tasklist, Admin và giao diện web mới nằm dưới `/fasti` (trước đây `/camunda`) trên
mọi bản phân phối: `http://localhost:8080/fasti/` mở giao diện mới
(`/fasti/app/next/default/`), các ứng dụng cổ điển ở `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...`, `/fasti/app/admin/...`. REST API vẫn ở `/engine-rest`.

Đường dẫn cũ vẫn dùng được: mọi yêu cầu tới `/camunda/...` được chuyển hướng tới cùng
đường dẫn dưới `/fasti/...`, giữ nguyên phần còn lại của đường dẫn và query. GET/HEAD nhận
`301`; các phương thức khác (POST, PUT, DELETE, ví dụ client cũ gọi `/camunda/api/...`) nhận
`308`, nên client gửi lại đúng phương thức và nội dung. Phản hồi chuyển hướng có
`Cache-Control: no-store`, để có thể quay lại `/camunda` bất cứ lúc nào.

Quay lại `/camunda`:

- **Spring Boot, Run:** `camunda.bpm.webapp.application-path: /camunda` (khi đó không còn
  chuyển hướng). Chỉ tắt chuyển hướng: `fasti.webapp.legacy-path-redirect: false`.
- **Tomcat:** xóa `webapps/camunda` (ứng dụng chuyển hướng) rồi đổi tên `webapps/fasti`
  thành `webapps/camunda`.
- **WildFly:** xóa `standalone/deployments/camunda.war` (và `camunda.war.dodeploy`), rồi
  đặt `<context-root>/camunda</context-root>` trong `WEB-INF/jboss-web.xml` của WAR web app.

**Reverse proxy:** nếu proxy chỉ chuyển tiếp `/camunda/` (hoặc viết lại đường dẫn, đặt
cookie path, CSP, health check theo `/camunda`), hãy thêm `/fasti/` — hoặc chuyển hẳn sang
`/fasti/`. Cookie `XSRF-TOKEN` (và `JSESSIONID` trên Tomcat, WildFly) giờ có path `/fasti`,
nên người dùng phải đăng nhập lại một lần. Bookmark và liên kết cũ vẫn chạy nhờ chuyển hướng.

## Đăng nhập một lần (SSO, OpenID Connect)

Hỗ trợ trên **Run** (`./start.sh --oauth2`, module `internal/oauth2`) và ứng dụng **Spring
Boot** dùng `fasti-bpm-spring-boot-starter-security`. Bật bằng cách khai báo một client
registration của Spring Security (`spring.security.oauth2.client.*`). Trên **Tomcat** và
**WildFly** xem đoạn cuối mục này.

- **Đăng nhập:** trang đăng nhập của giao diện mới hiện nút "Đăng nhập bằng &lt;nhà cung
  cấp&gt;" thay cho ô mật khẩu (nhãn: `fasti.security.oidc.login-label`, mặc định là
  `client-name`; có nhiều registration thì chọn bằng `fasti.security.oidc.registration-id`).
  Luồng authorization code có `state`, `nonce` và PKCE (S256, cả với confidential client);
  session id được thay mới khi đăng nhập. Người dùng quay lại đúng trang của giao diện mới
  (chỉ nhận đường dẫn dưới `/fasti/app/next/`, nên không có open redirect).
- **Đăng xuất:** `POST /logout` (chỉ chấp nhận yêu cầu cùng origin) xóa session; khi
  `camunda.bpm.oauth2.sso-logout.enabled=true` người dùng được chuyển tới endpoint đăng xuất
  của nhà cung cấp (RP-initiated logout, `post-logout-redirect-uri`). Back-channel logout
  của Spring Security vẫn bật. `GET /logout` không còn đăng xuất.
- **Hết phiên:** khi access token không làm mới được hoặc session hết hạn, `/fasti/api/**`
  trả `401` (không chuyển hướng) và giao diện mới quay về trang đăng nhập.
- **CSRF:** `/fasti/api` và `/fasti/app` vẫn được bảo vệ bởi bộ lọc CSRF của web app
  (`X-XSRF-TOKEN`); CSRF của Spring Security tắt như bản gốc.
- **Nhóm, quyền quản trị, tenant:** claim nhóm đặt bằng
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` (giữ tên thuộc tính gốc; hỗ
  trợ claim lồng nhau như `realm_access.roles`). `fasti.security.oidc.groups.admin` liệt kê
  các giá trị claim được vào nhóm `camunda-admin`; `groups.mapping` đổi giá trị claim sang id
  nhóm của engine; `groups.include-unmapped=false` bỏ các giá trị chưa ánh xạ.
  **Khác bản gốc:** giá trị `camunda-admin` trong claim không còn tự cấp quyền quản trị, và
  các authority như `SCOPE_openid`, `OIDC_USER` không còn thành nhóm.
  `fasti.security.oidc.tenants.claim` lấy tenant từ claim. Tên, email:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Identity provider:** mặc định engine đọc người dùng, nhóm, tenant từ token của phiên
  hiện tại (chỉ đọc). Đặt `camunda.bpm.oauth2.identity-provider.enabled=false` để dùng cơ sở
  dữ liệu (khi đó nhóm lấy từ bảng membership). Chưa có tự động tạo người dùng
  (auto-provisioning).
  Khi thông tin người dùng được lấy từ token (mặc định), trang thiết lập ban đầu (tạo quản trị
  viên đầu tiên) tắt hẳn, vì quản trị viên đến từ claim (`groups.admin`); trước đây người dùng không thuộc
  nhóm quản trị bị đưa tới trang thiết lập.
- **REST API bằng bearer token:** `fasti.security.oidc.rest.bearer-enabled=true` cho phép
  `Authorization: Bearer <JWT>` trên `/engine-rest`, song song với basic auth. Bắt buộc
  `rest.issuer-uri` và `rest.audiences`; chữ ký được kiểm tra bằng JWKS của issuer, chỉ nhận
  thuật toán bất đối xứng (`rest.jws-algorithms`, mặc định `RS256`); kiểm tra `iss`, `aud`,
  `exp`, `nbf`. Trên Run cần thêm `camunda.bpm.run.auth.enabled=true`. Cookie phiên của
  trình duyệt không bao giờ xác thực được `/engine-rest`.

Ví dụ Keycloak (`configuration/default.yml` của Run):

```yaml
spring.security.oauth2.client:
  registration.keycloak:
    client-id: fasti
    client-secret: ${KEYCLOAK_CLIENT_SECRET}
    client-name: Keycloak
    scope: openid, profile, email
  provider.keycloak:
    issuer-uri: https://sso.example.com/realms/fasti
    user-name-attribute: preferred_username
camunda.bpm:
  oauth2:
    # mapper "Group Membership" của client, tắt "Full group path"
    identity-provider.group-name-attribute: groups
    sso-logout:
      enabled: true
      post-logout-redirect-uri: "{baseUrl}/fasti/app/next/default/"
  run.auth.enabled: true
fasti.security.oidc:
  groups.admin: [fasti-admins]
  rest:
    bearer-enabled: true
    issuer-uri: https://sso.example.com/realms/fasti
    audiences: [fasti-api]          # mapper "Audience" trong Keycloak
    user-id-claim: preferred_username
```

Ví dụ Microsoft Entra ID:

```yaml
spring.security.oauth2.client:
  registration.entra:
    client-id: <application-id>
    client-secret: ${ENTRA_CLIENT_SECRET}
    client-name: Microsoft
    scope: openid, profile, email
  provider.entra:
    issuer-uri: https://login.microsoftonline.com/<tenant-id>/v2.0
    user-name-attribute: preferred_username
camunda.bpm.oauth2:
  # manifest của app registration: "groupMembershipClaims": "SecurityGroup"
  identity-provider.group-name-attribute: groups
  sso-logout.enabled: true
fasti.security.oidc:
  groups:
    admin: ["<object-id nhóm quản trị>"]
    mapping:
      "<object-id nhóm kế toán>": accounting
    include-unmapped: false          # Entra gửi object id, không phải tên nhóm
  rest:
    bearer-enabled: true
    issuer-uri: https://login.microsoftonline.com/<tenant-id>/v2.0
    audiences: ["api://<application-id>"]   # access token v2: accessTokenAcceptedVersion = 2
    user-id-claim: preferred_username
```

**Tomcat, WildFly (WAR):** cùng tính năng, cấu hình bằng tệp `fasti-oidc.properties` (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; mẫu có chú thích là
`fasti-oidc.properties.example` ở cùng chỗ). Không có tệp thì không có gì thay đổi. Khóa giống
`fasti.security.oidc.*` của Run; thêm `issuer-uri`, `client-id`, `client-secret`,
`user-name-claim`, `groups-claim`. Nhà cung cấp phải cho phép redirect URI
`<gốc>/fasti/sso/callback`; URL back-channel logout là `<gốc>/fasti/sso/backchannel-logout`.
Bearer token trên `/engine-rest` bật bằng `fasti.security.oidc.rest.bearer-enabled=true` (basic
auth vẫn dùng được trừ khi `rest.basic-enabled=false`; danh sách engine `/engine` cũng cần xác
thực). Người dùng không cần có trong cơ sở dữ liệu. Để engine trả lời truy vấn về nhóm/tenant
của người đang đăng nhập theo claim, bật `OidcIdentityProviderPlugin` (có sẵn dạng chú thích
trong `bpm-platform.xml` / `standalone.xml`). Giới hạn: danh sách phiên cho back-channel logout
nằm trong bộ nhớ của từng server (không chia sẻ giữa các node của cụm); ứng dụng cổ điển chỉ
được chuyển tới nhà cung cấp, đăng xuất đầy đủ dùng giao diện mới.

## Build

Yêu cầu: JDK 17 trở lên (khuyến nghị 21).

```bash
./mvnw clean verify
```

Build đầy đủ chạy toàn bộ test Java và test đơn vị của web UI (có thể mất gần một giờ; test
đầu-cuối Playwright chạy riêng, xem [webapps/frontend-next](webapps/frontend-next/README.md)) và cần mạng tới Maven Central,
nodejs.org và npm registry. Xem thêm [TESTING.md](TESTING.md).

## Ghi nhận

- Fasti BPM Platform dựng trên công sức của Camunda và cộng đồng Camunda 7 trong hơn mười năm.
- Khi rà soát lỗi, dự án có tham khảo thông tin công khai (issue, advisory, ghi chú phát
  hành) của các dự án cộng đồng cùng gốc Camunda 7: Operaton, CIB seven, OrqueIO và
  EximeeBPMS. Mã nguồn trong kho này được viết độc lập trên nền Camunda 7 CE.

## Giấy phép

[Apache License 2.0](LICENSE). Xem [NOTICE](NOTICE) về bản quyền của mã gốc.

---

## English

> **Not affiliated with Camunda.** Fasti BPM is an independent project. It is not part of,
> sponsored by or endorsed by Camunda Services GmbH. "Camunda" is a trademark of Camunda
> Services GmbH and is used here only to describe where the source code comes from.

### What this is

This repository contains the BPMN 2.0 / DMN process engine, the REST API, the web
applications (Cockpit, Tasklist, Admin) and the distributions (Run, Tomcat, WildFly). It
starts from the `7.24.0` tag of Camunda 7 CE and continues with bug fixes and library
upgrades.

The goal is **backward compatibility** with applications that run on Camunda 7:

- the `org.camunda.bpm.*` packages, the BPMN namespace
  `http://camunda.org/schema/1.0/bpmn` and the `camunda.bpm.*` configuration prefix are kept;
- the database schema of 7.24 is kept;
- all deployment flavours are kept: Spring Boot, Run, Tomcat, WildFly; `javax` and `jakarta`.

### What is different from Camunda 7.24.0 CE

- **Security:** fixes for the publicly disclosed issues of the 7.x line (expressions in
  queries, initial setup, access to attachments and comments, Cockpit permissions, open
  redirect) plus additional safeguards: an allow-list for expressions in queries and
  filters, attachment and comment checks inside the engine, hardened security filters,
  headers and cookies in the web applications.
- **Stability:** no more memory leak when scripts and DMN expressions are evaluated with
  JavaScript (GraalJS), and a number of other engine fixes.
- **Platform:** Java 17 or later, Spring Boot 4.1, GraalJS 25, Jackson 2.21 and many other
  libraries upgraded to releases with security fixes.

Some fixes change defaults towards safer behaviour; read "What differs" in the
[migration guide](docs/chuyen-tu-camunda-7.md#what-differs) before upgrading.

### Versioning

Fasti BPM Platform follows [Semantic Versioning](https://semver.org/), starting at `1.0.0`.
The `1.x` line is compatible with applications written for Camunda 7.24.

### Maven coordinates

The artifacts of Fasti BPM Platform have their own Maven coordinates. **The Java packages
remain `org.camunda.bpm.*`**, so application code keeps its imports; the `camunda.bpm.*`
configuration prefix, the BPMN/DMN namespaces, the database schema and the module names on
WildFly are unchanged as well. As far as Maven is concerned, only the dependency declarations
change (the web application path changes separately, see
[Web application path](#web-application-path-fasti)):

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`; the groups `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` and `org.camunda.template-engines` → `dev.fasti.platform.<group name>`.
- **artifactId:** the prefix `camunda-` becomes `fasti-`, the rest is kept.
- **version:** the version of Fasti BPM Platform (see [Versioning](#versioning)) instead of
  `7.24.0`.

No release of these artifacts has been published to Maven Central yet. Until then, build
from source and make the artifacts available in the Maven repository your project uses.

Manage the versions with the BOM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.fasti.platform</groupId>
      <artifactId>fasti-bom</artifactId>
      <version>${fasti.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Frequently used artifacts:

| Camunda 7 | Fasti BPM Platform |
|---|---|
| `org.camunda.bpm:camunda-bom` | `dev.fasti.platform:fasti-bom` |
| `org.camunda.bpm:camunda-engine` | `dev.fasti.platform:fasti-engine` |
| `org.camunda.bpm:camunda-engine-spring` | `dev.fasti.platform:fasti-engine-spring` |
| `org.camunda.bpm:camunda-engine-spring-6` | `dev.fasti.platform:fasti-engine-spring-6` |
| `org.camunda.bpm:camunda-engine-cdi-jakarta` | `dev.fasti.platform:fasti-engine-cdi-jakarta` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-rest` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-webapp` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-webapp` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-security` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-security` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-external-task-client` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-external-task-client` |
| `org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-test` | `dev.fasti.platform.springboot:fasti-bpm-spring-boot-starter-test` |
| `org.camunda.bpm:camunda-engine-rest-core` | `dev.fasti.platform:fasti-engine-rest-core` |
| `org.camunda.bpm:camunda-engine-rest-core-jakarta` | `dev.fasti.platform:fasti-engine-rest-core-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp` | `dev.fasti.platform.webapp:fasti-webapp` |
| `org.camunda.bpm.webapp:camunda-webapp-jakarta` | `dev.fasti.platform.webapp:fasti-webapp-jakarta` |
| `org.camunda.bpm.webapp:camunda-webapp-webjar` | `dev.fasti.platform.webapp:fasti-webapp-webjar` |
| `org.camunda.bpm:camunda-external-task-client` | `dev.fasti.platform:fasti-external-task-client` |
| `org.camunda.bpm:camunda-engine-plugin-spin` | `dev.fasti.platform:fasti-engine-plugin-spin` |
| `org.camunda.bpm:camunda-engine-plugin-connect` | `dev.fasti.platform:fasti-engine-plugin-connect` |
| `org.camunda.spin:camunda-spin-dataformat-all` | `dev.fasti.platform.spin:fasti-spin-dataformat-all` |
| `org.camunda.connect:camunda-connect-connectors-all` | `dev.fasti.platform.connect:fasti-connect-connectors-all` |
| `org.camunda.bpm.dmn:camunda-engine-dmn` | `dev.fasti.platform.dmn:fasti-engine-dmn` |
| `org.camunda.bpm.model:camunda-bpmn-model` | `dev.fasti.platform.model:fasti-bpmn-model` |
| `org.camunda.bpm:camunda-bpm-assert` | `dev.fasti.platform:fasti-bpm-assert` |
| `org.camunda.bpm:camunda-bpm-junit5` | `dev.fasti.platform:fasti-bpm-junit5` |

The FEEL library `org.camunda.feel:feel-engine` is an external library that is not part of
this repository; its coordinates stay as they are.

#### Third-party libraries that still pull in the original engine

Because the packages are unchanged, the original engine (`org.camunda.bpm:camunda-engine`)
and `fasti-engine` contain classes with the same names. Maven treats them as two unrelated
libraries and does not drop either of them. If a third-party library (a community extension,
for example) depends on the original artifacts, exclude them and declare the corresponding
Fasti artifacts instead:

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>some-camunda-extension</artifactId>
  <version>1.2.3</version>
  <exclusions>
    <exclusion>
      <groupId>org.camunda.bpm</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.model</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.bpm.dmn</groupId>
      <artifactId>*</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.camunda.commons</groupId>
      <artifactId>*</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

`mvn dependency:tree -Dincludes="org.camunda*"` lists original artifacts that are still
present (`org.camunda.feel:feel-engine` is legitimate, do not exclude it).

#### Startup check

When it is created, the engine inspects the class path and **refuses to start** if it finds
the classes of the process engine in more than one library; the error `ENGINE-12021` lists
the location of each of them. The Spring Boot starter performs the same check as soon as the
application starts. A library that is only visible through the parent class loader (a shared
engine of the server next to an engine embedded in a web application, for example) is not
counted. If your setup is intentional, the check can be switched off with a system property:

```
-Dfasti.engine.duplicate-library-check.enabled=false
```

Limitation: if the original engine library precedes `fasti-engine` on the class path, the
engine is built by the classes of the original library, which do not contain this check.
In that case only applications that use the Fasti Spring Boot starter are stopped; with
other integrations, review the dependencies as described above.

### Moving from Camunda 7, container image

Fasti BPM runs on a Camunda 7.24 database without changing the schema; you can replace the old
nodes, run side by side or roll back. See [docs/chuyen-tu-camunda-7.md](docs/chuyen-tu-camunda-7.md#english); the container
image accepts the `DB_*` variables of the former Run image: [distro/docker/README.md](distro/docker/README.md#english).

### Web application path: `/fasti`

Cockpit, Tasklist, Admin and the new web UI are served below `/fasti` (formerly `/camunda`)
by every distribution: `http://localhost:8080/fasti/` opens the new UI
(`/fasti/app/next/default/`), the classic applications live at `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...` and `/fasti/app/admin/...`. The REST API stays at `/engine-rest`.

The old path keeps working: every request for `/camunda/...` is redirected to the same path
below `/fasti/...`, keeping the rest of the path and the query string. GET and HEAD get
`301`; all other methods (POST, PUT, DELETE, e.g. old clients calling `/camunda/api/...`)
get `308`, so that clients repeat the request with the same method and body. The redirects
carry `Cache-Control: no-store`, so switching back to `/camunda` takes effect at once.

To restore `/camunda`:

- **Spring Boot, Run:** `camunda.bpm.webapp.application-path: /camunda` (no redirect then).
  To turn off only the redirect: `fasti.webapp.legacy-path-redirect: false`.
- **Tomcat:** delete `webapps/camunda` (the redirect application) and rename
  `webapps/fasti` to `webapps/camunda`.
- **WildFly:** delete `standalone/deployments/camunda.war` (and `camunda.war.dodeploy`) and
  set `<context-root>/camunda</context-root>` in `WEB-INF/jboss-web.xml` of the web
  application WAR.

**Reverse proxies:** if a proxy forwards only `/camunda/` (or rewrites paths, sets cookie
paths, CSP or health checks for `/camunda`), add `/fasti/` or move to `/fasti/` entirely.
The `XSRF-TOKEN` cookie (and `JSESSIONID` on Tomcat and WildFly) now has the path `/fasti`,
so users sign in once more. Bookmarks and old links keep working through the redirect.

### Single sign-on (OpenID Connect)

Supported on **Run** (`./start.sh --oauth2`, module `internal/oauth2`) and on **Spring Boot**
applications with `fasti-bpm-spring-boot-starter-security`. It is enabled by declaring a
Spring Security client registration (`spring.security.oauth2.client.*`). For **Tomcat** and
**WildFly** see the end of this section.

- **Login:** the login page of the new web application shows "Sign in with &lt;provider&gt;"
  instead of the password form (label: `fasti.security.oidc.login-label`, defaults to the
  `client-name`; with several registrations choose one with
  `fasti.security.oidc.registration-id`). The authorization code flow uses `state`, `nonce`
  and PKCE (S256, also for confidential clients); the session id is replaced on login. Users
  return to the page of the new web application they started from (only paths below
  `/fasti/app/next/` are accepted, so there is no open redirect).
- **Logout:** `POST /logout` (same-origin requests only) ends the session; with
  `camunda.bpm.oauth2.sso-logout.enabled=true` the user continues to the provider's end
  session endpoint (RP-initiated logout, `post-logout-redirect-uri`). Spring Security's
  back-channel logout stays enabled. `GET /logout` no longer logs out.
- **Session expiry:** when the access token cannot be refreshed or the session times out,
  `/fasti/api/**` answers `401` (no redirect) and the new web application shows its login page.
- **CSRF:** `/fasti/api` and `/fasti/app` keep the web applications' own CSRF filter
  (`X-XSRF-TOKEN`); Spring Security's CSRF protection stays off as upstream.
- **Groups, administrators, tenants:** the groups claim is
  `camunda.bpm.oauth2.identity-provider.group-name-attribute` (upstream property name; nested
  claims such as `realm_access.roles` work). `fasti.security.oidc.groups.admin` lists the
  claim values that grant `camunda-admin`; `groups.mapping` maps claim values to engine group
  ids; `groups.include-unmapped=false` drops unmapped values. **Changed from upstream:** a
  claim value `camunda-admin` no longer grants administrator rights by itself, and
  authorities such as `SCOPE_openid` or `OIDC_USER` no longer become groups.
  `fasti.security.oidc.tenants.claim` maps tenants. Name and email:
  `fasti.security.oidc.user.{first-name,last-name,email}-claim`.
- **Identity provider:** by default the engine reads users, groups and tenants from the
  token of the current session (read-only). Set
  `camunda.bpm.oauth2.identity-provider.enabled=false` to use the database instead (groups
  then come from the membership tables). Auto-provisioning is not implemented.
  While users come from the token (the default), the initial setup page (first administrator)
  is switched off, since administrators come from the claims (`groups.admin`); before, users
  outside the administrators group were sent to it.
- **REST API with bearer tokens:** `fasti.security.oidc.rest.bearer-enabled=true` accepts
  `Authorization: Bearer <JWT>` on `/engine-rest` in addition to basic authentication.
  `rest.issuer-uri` and `rest.audiences` are required; signatures are checked against the
  issuer's JWKS with asymmetric algorithms only (`rest.jws-algorithms`, default `RS256`), and
  `iss`, `aud`, `exp`, `nbf` are validated. Run also needs `camunda.bpm.run.auth.enabled=true`.
  A browser session cookie never authenticates `/engine-rest`.

The Keycloak and Microsoft Entra ID examples in the Vietnamese section above apply unchanged.

**Tomcat, WildFly (WAR):** the same features, configured in `fasti-oidc.properties` (Tomcat:
`conf/`, WildFly: `standalone/configuration/`; an annotated `fasti-oidc.properties.example`
sits next to it). Without the file nothing changes. The keys match `fasti.security.oidc.*` of
Run, plus `issuer-uri`, `client-id`, `client-secret`, `user-name-claim`, `groups-claim`. The
provider must allow the redirect URI `<base>/fasti/sso/callback`; the back-channel logout URL is
`<base>/fasti/sso/backchannel-logout`. Bearer tokens on `/engine-rest` are enabled with
`fasti.security.oidc.rest.bearer-enabled=true` (basic authentication keeps working unless
`rest.basic-enabled=false`; the engine list `/engine` requires credentials too). Users need not
exist in the database. For the engine to answer queries about the groups and tenants of the
signed-in user from the claims, enable the `OidcIdentityProviderPlugin` (commented out in
`bpm-platform.xml` / `standalone.xml`). Limits: the sessions known for back-channel logout are
kept in memory per server (not shared across cluster nodes); classic applications are only sent
to the provider, a complete logout goes through the new web UI.

### Build

Requires JDK 17 or later (21 recommended).

```bash
./mvnw clean verify
```

A full build runs all Java tests and the web UI unit tests (close to an hour; the Playwright
end-to-end tests run separately, see [webapps/frontend-next](webapps/frontend-next/README.md#end-to-end-tests-playwright)) and needs network access to Maven Central,
nodejs.org and the npm registry. See [TESTING.md](TESTING.md).

### Acknowledgements

- Fasti BPM Platform builds on more than a decade of work by Camunda and the Camunda 7
  community.
- While reviewing defects, the project consulted public information (issues, advisories,
  release notes) of other community projects that descend from Camunda 7: Operaton, CIB
  seven, OrqueIO and EximeeBPMS. The source code in this repository was written
  independently on top of Camunda 7 CE.

### License

[Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for the copyright of the original code.
