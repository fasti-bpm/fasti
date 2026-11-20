[English](single-sign-on.md) | Tiếng Việt | [Deutsch](single-sign-on.de.md) | [Español](single-sign-on.es.md) | [Français](single-sign-on.fr.md) | [हिन्दी](single-sign-on.hi.md) | [Bahasa Indonesia](single-sign-on.id.md) | [日本語](single-sign-on.ja.md) | [한국어](single-sign-on.ko.md) | [Português (Brasil)](single-sign-on.pt-BR.md) | [Русский](single-sign-on.ru.md) | [ไทย](single-sign-on.th.md) | [简体中文](single-sign-on.zh-CN.md)

<a id="single-sign-on-openid-connect"></a>

# Đăng nhập một lần (SSO, OpenID Connect)

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
