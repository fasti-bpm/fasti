[English](README.md) | Tiếng Việt

# Fasti BPM — image container

Image chạy **Fasti BPM Run** (engine, REST API `/engine-rest` và web app mới ở `/fasti/`; các
web app cổ điển **tắt theo mặc định**, xem bên dưới). Image được làm để **trỏ vào cơ sở dữ liệu mà một hệ Camunda 7 (7.24) đang
dùng** và chạy tiếp trên đó: người dùng, mật khẩu, nhóm, tenant, phân quyền, filter,
deployment, instance đang chạy, lịch sử, job và incident giữ nguyên. Fasti BPM tương thích với
cơ sở dữ liệu của Camunda 7; đây là dự án độc lập, không liên kết với Camunda.

Hướng dẫn chuyển đổi đầy đủ (các bước, quay lại, chạy lẫn với node cũ, giới hạn):
[docs/migrate-from-camunda-7.vi.md](../../docs/migrate-from-camunda-7.vi.md).

## Build

```bash
# 1. bản phân phối Run, driver JDBC và Dockerfile vào distro/docker/target/docker
./mvnw -pl distro/docker -am package -DskipTests
# 2. image
docker build -t fasti-bpm:1.3.1 \
  --build-arg VERSION=1.3.1 --build-arg REVISION=$(git rev-parse --short HEAD) \
  distro/docker/target/docker
```

- Build không có mạng: thêm `-o -Dfasti.docker.skipMariadbDriver=true` (khi đó không có
  driver MariaDB, xem bảng driver).
- Image ổn định hơn giữa các lần build: ghim image nền bằng digest (bước `apk upgrade` vẫn lấy
  bản vá mới nhất của Alpine),
  `--build-arg JRE_IMAGE=eclipse-temurin:21-jre-alpine@sha256:<digest>`.

## Bảo mật mặc định — khác với Camunda Run

> **Khác biệt có chủ đích:** Camunda Run và image Camunda 7 cũ để `/engine-rest` **không cần
> xác thực** và **tắt phân quyền**. Image Fasti **bật sẵn cả hai**, kể cả khi không có `--production`;
> chỉ `FASTI_INSECURE_DEMO=true` (demo) tắt được.

- REST API yêu cầu Basic auth (hoặc bearer token khi bật OIDC); phân quyền
  (`camunda.bpm.authorization.enabled`) luôn bật. Người dùng, nhóm và phân quyền lấy từ cơ sở
  dữ liệu, nên khi chỉ đổi `DB_*` sang cơ sở dữ liệu của hệ Camunda 7 cũ, tài khoản cũ vẫn đăng
  nhập được (REST và web UI).
- **Ảnh hưởng khi chuyển đổi:** mọi client REST (external task worker, tích hợp, script) phải
  gửi `Authorization: Basic …` của một user có quyền phù hợp, hoặc bearer token. Nếu hệ cũ chạy
  với phân quyền tắt, bảng `ACT_RU_AUTHORIZATION` có thể thiếu các quyền cần thiết: thành viên
  nhóm `camunda-admin` vẫn làm được mọi việc, user khác có thể thấy danh sách rỗng hoặc nhận 403.
  Kiểm tra trước: `select * from ACT_ID_MEMBERSHIP where GROUP_ID_='camunda-admin'` và
  `select count(*) from ACT_RU_AUTHORIZATION`; cấp quyền trong Admin trước khi chuyển lưu lượng.
- Không có trang "tạo admin đầu tiên" ẩn danh. Cơ sở dữ liệu trống (hoặc H2 demo): đặt
  `FASTI_ADMIN_USER` + `FASTI_ADMIN_PASSWORD` (hoặc `_FILE`). Admin đã tồn tại thì không bị đổi
  mật khẩu. `FASTI_INITIAL_SETUP=true` bật lại trang thiết lập; trang này hỏi mã cài đặt dùng một lần, được in vào log của container (`docker logs`) khi trang được mở lần đầu, hoặc lấy từ `FASTI_SETUP_TOKEN`.
- Ứng dụng **từ chối khởi động** nếu cấu hình thực tế (file YAML gắn vào, biến môi trường,
  `SPRING_APPLICATION_JSON`, tham số) tắt xác thực REST hoặc phân quyền, hoặc đặt
  `camunda.bpm.admin-user.id` mà không có mật khẩu.
- **Chế độ mở chỉ để demo:** `FASTI_INSECURE_DEMO=true` tắt xác thực và phân quyền, in cảnh báo
  lớn, và bị từ chối cùng `--production`. Chỉ publish cổng trên 127.0.0.1
  (`-p 127.0.0.1:8080:8080`) và không bao giờ dùng với dữ liệu thật.
- `--production` còn từ chối H2 ngầm định và `DEBUG=true`.

## Chỉ giao diện web mới — web app cổ điển tắt theo mặc định

> **Thay đổi phá vỡ tương thích (breaking):** image chỉ phục vụ giao diện web mới
> (`/fasti/app/next/`). Cockpit, Tasklist, Admin và Welcome cổ điển dựng trên AngularJS 1.8.2 và
> Bootstrap 3, cả hai đã hết vòng đời (end of life, không còn bản vá bảo mật), nên không được
> phục vụ nữa. Ai đang dùng các ứng dụng cổ điển trong image cần chuyển sang giao diện mới hoặc
> bật lại bằng `FASTI_CLASSIC_WEBAPPS=true`.

- Trang của ứng dụng cổ điển và bookmark cũ chuyển hướng (`302`) tới mục tương ứng của giao
  diện mới, giữ tên engine: `/fasti/app/cockpit/default/` → `/fasti/app/next/default/#/cockpit`,
  Tasklist → `#/tasklist`, Admin → `#/admin`, Welcome → trang đầu. `/camunda/app/...` đi qua
  chuyển hướng `/camunda` → `/fasti` rồi tới giao diện mới. Đường dẫn bên trong ứng dụng cũ
  (phần sau `#`) không chuyển đổi được: người dùng tới trang đầu của mục đó.
- Script, style, bản dịch của ứng dụng cổ điển và file tĩnh của plugin cổ điển
  (`/fasti/api/{cockpit,tasklist,admin}/plugin/*/static/...`) trả `404`, kể cả bản sao mà
  Spring Boot phục vụ dưới `/webjars/camunda/app/...`. Các file vẫn nằm trong
  image (để bật lại được) nhưng không được phục vụ.
- REST API của web app (`/fasti/api/...`) vẫn hoạt động: giao diện mới dùng nó. Khung hiển thị
  biểu mẫu nhúng (`/fasti/app/forms/`) vẫn tải `app/tasklist/scripts/config.js` và custom script
  mà file đó khai báo; khung này chạy tách biệt (sandbox) với giao diện mới. Khung biểu mẫu
  nhúng vẫn dùng AngularJS (biểu mẫu nhúng của Camunda 7 cần nó), nên `/fasti/lib/` và
  `/fasti/assets/` vẫn được phục vụ cho khung này.
- `FASTI_CLASSIC_WEBAPPS=true` (tương đương `fasti.webapp.classic.enabled=true`) bật lại các
  ứng dụng cổ điển, kèm cảnh báo `WARN` khi khởi động rằng chúng đã lỗi thời và dùng AngularJS
  hết vòng đời. Chúng sẽ bị gỡ trong một bản phát hành sau.
- `fasti.webapp.default-app` trỏ tới ứng dụng cổ điển bị bỏ qua (có cảnh báo) khi chúng tắt.
- Giao diện mới chưa có màn hình CMMN; ai cần xem case CMMN trong Cockpit cổ điển dùng
  `FASTI_CLASSIC_WEBAPPS=true` hoặc REST API.

## Chạy trên cơ sở dữ liệu có sẵn

Dùng đúng các biến môi trường của image Camunda 7 Run trước đây:

```bash
docker run -d --name fasti -p 8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=UTC \
  fasti-bpm:1.3.1
```

`DB_PASSWORD_FILE` phải trỏ tới một file có trong container (ví dụ Docker/Kubernetes secret, hoặc
`-v /đường/dẫn/db-password:/run/secrets/db-password:ro`); cũng có thể dùng `DB_PASSWORD`.
Rồi mở `http://localhost:8080/fasti/` và đăng nhập bằng tài khoản đã có; client REST gửi
Basic auth của tài khoản đó (ví dụ `curl -u alice:… http://localhost:8080/engine-rest/task`).

Cơ sở dữ liệu mới, trống: thêm `-e FASTI_DB_SCHEMA_UPDATE=true` (tạo bảng) và
`-e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD=...`.
Không đặt `DB_URL`/`FASTI_DB_URL` (và không có `SPRING_DATASOURCE_URL`, `SKIP_DB_CONFIG` hay file
cấu hình riêng): image dùng H2 trong `/fasti/data`, chỉ để thử.

## Biến môi trường

Tên biến của image cũ và các bí danh `FASTI_*` đều được chấp nhận (khi đặt cả hai, giá trị `FASTI_*` được dùng;
với `DEBUG` và `JMX_PROMETHEUS`, chỉ cần một trong hai tên là `true`). Mọi thuộc tính
Spring Boot cũng đặt được bằng biến môi trường (ví dụ `CAMUNDA_BPM_RUN_AUTH_ENABLED=true`). Các
mục ghi **từ 1.1.0** chưa có trong các image 1.0.x; các mục ghi **từ 1.2.0** chưa có trong các
image 1.1.x; các mục ghi **từ 1.3.0** chưa có trong các image 1.2.x.

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_URL` / `FASTI_DB_URL` | (H2 demo) | JDBC URL |
| `DB_DRIVER` / `FASTI_DB_DRIVER` | theo URL | lớp driver JDBC |
| `DB_USERNAME`, `DB_PASSWORD` (+ `_FILE`) | | tài khoản; `_FILE` đọc từ file (Docker/Kubernetes secret) |
| `DB_CONN_MAXACTIVE`, `DB_CONN_MINIDLE` | Hikari | `maximum-pool-size`, `minimum-idle` của HikariCP |
| `DB_VALIDATE_ON_BORROW` + `DB_VALIDATION_QUERY` | | `connection-test-query` của HikariCP |
| `DB_CONN_MAXIDLE` | | không có tương ứng trong HikariCP, bỏ qua (có log) |
| `SKIP_DB_CONFIG=true` | | không đặt datasource từ `DB_*` (dùng file cấu hình) |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | –, 30 | chờ `host:port[,host:port]` trước khi khởi động |
| `TZ` | UTC | múi giờ của JVM (`-Duser.timezone`) — **phải giống mọi node khác** |
| `DEBUG=true` | | remote debug cổng `FASTI_DEBUG_PORT` (8000) |
| `JMX_PROMETHEUS=true` | | Prometheus JMX exporter cổng `JMX_PROMETHEUS_PORT` (9404), cấu hình `JMX_PROMETHEUS_CONF`, nếu có jar trong `/fasti/javaagent` |
| `JAVA_OPTS` | | thêm tùy chọn JVM (tách bằng khoảng trắng, không có dấu nháy) |
| `FASTI_MAX_RAM_PERCENTAGE` | 75 | phần trăm RAM của container cho heap |
| `FASTI_ANALYTICS_CACHE_TTL` | `60s` | **Từ 1.3.0.** Thời gian giữ kết quả phân tích quy trình cho cùng một góc nhìn (`0` để tắt; số không có đơn vị tính là giây); số liệu đang chạy có thể cũ tối đa chừng ấy. Thiết lập `fasti.analytics.cache-ttl` |
| `FASTI_ANALYTICS_MAX_CONCURRENT` | `2` | **Từ 1.3.0.** Số lượt quét của phân tích quy trình (activities, rework, user task) được đọc lịch sử cùng lúc trên node này, cộng một lượt đọc nhanh (instances, trend, truy vết); các lượt khác chờ đến lượt mà không giữ kết nối CSDL, nên job và danh sách task vẫn có kết nối của chúng. `0`: không giới hạn. Thiết lập `fasti.analytics.max-concurrent` |
| `FASTI_ANALYTICS_MAX_WAIT` | `60s` | **Từ 1.3.0.** Thời gian chờ lượt lâu nhất; quá thời gian này REST API trả `503` (số không có đơn vị tính là giây). Thiết lập `fasti.analytics.max-wait` |
| `FASTI_RETURN_MEMORY` | `true` | **Có từ 1.2.1.** Trả phần heap không dùng về hệ thống (`-XX:G1PeriodicGCInterval=30000 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30`); `false` giữ mặc định của JVM |
| `FASTI_DB_SCHEMA_UPDATE` | `false` (`true` với cơ sở dữ liệu H2 demo có sẵn) | `true` tạo bảng còn thiếu (cơ sở dữ liệu mới); không bao giờ nâng cấp schema |
| `FASTI_SCHEMA_VERSION_CHECK` | `enforce` | `warn`: schema cũ hơn 7.24 chỉ cảnh báo thay vì dừng |
| `FASTI_DB_SCHEMA`, `FASTI_DB_TABLE_PREFIX` | | `camunda.bpm.database.schema-name` / `table-prefix` |
| `FASTI_TRUSTED_PROXIES` | địa chỉ nội bộ | **Từ 1.3.1.** Biểu thức chính quy cho địa chỉ IP của các proxy được tin header `X-Forwarded-*`, ví dụ `10\.0\.0\.5`; proxy phải ghi đè `X-Forwarded-Host` và `X-Forwarded-Proto` |
| `FASTI_DESERIALIZATION_ALLOWED_PACKAGES` | — | **Từ 1.3.1.** Các package (cách nhau bằng dấu phẩy) mà biến Spin JSON/XML và biến Object được phép deserialize thành, ngoài các kiểu Java cơ bản; kiểm tra kiểu luôn bật. So khớp theo tiền tố: kết thúc mỗi package bằng dấu chấm (`com.example.`) để không cho phép cả `com.examplefoo`. |
| `FASTI_DESERIALIZATION_ALLOWED_CLASSES` | — | **Từ 1.3.1.** Từng class được phép theo cách tương tự |
| `FASTI_REST_MULTIPART_MAX_SIZE` | `100MB` | **Từ 1.3.1.** Kích thước lớn nhất của một request multipart (deployment, biến file, tệp đính kèm); lớn hơn thì nhận `413`; `-1`: không giới hạn |
| `FASTI_REST_MULTIPART_MAX_PARTS` | `1000` | **Từ 1.3.1.** Số phần tối đa trong một request multipart |
| `FASTI_HISTORY_LEVEL` | `auto` | mặc định lấy mức lịch sử đang lưu trong cơ sở dữ liệu (mới: `full`) |
| `FASTI_JOB_EXECUTOR_ENABLED` | `true` | `false` tắt job executor của node này |
| `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | `false` | chỉ chạy job của deployment đã đăng ký trên node này |
| `FASTI_INSECURE_DEMO` | `false` | `true`: **tắt** xác thực REST và phân quyền, chỉ để demo (xem Bảo mật mặc định) |
| `FASTI_REST_AUTH` | `true` | luôn bật; giá trị `false` bị từ chối (dùng `FASTI_INSECURE_DEMO`) |
| `FASTI_REST_AUTH_CACHE_TTL` | `30s` | **Có từ 1.2.1.** Cần ghi đơn vị (`30s`; số trần tính là mili giây). Thời gian ghi nhớ một lần xác thực basic thành công của REST API, cùng nhóm và tenant của người dùng, để các request lặp lại không phải đọc lại (`0` để tắt). Người dùng bị xoá hoặc bị khoá và thay đổi thành viên nhóm có hiệu lực chậm nhất sau khoảng này; thay đổi qua REST API của cùng node xoá cache ngay. Thiết lập `fasti.rest.auth-cache-ttl` |
| `FASTI_INITIAL_SETUP` | `false` | `true`: bật trang tạo admin đầu tiên khi chưa có admin; trang hỏi mã cài đặt dùng một lần được in vào log |
| `FASTI_SETUP_TOKEN` | — | **Từ 1.3.1.** Mã cài đặt cố định (ít nhất 16 ký tự) thay cho mã được in ra, dùng khi cài tự động. Khi chạy nhiều bản sao, hãy đặt biến này (mã được in ra riêng cho từng node), hoặc chỉ chạy một bản sao lúc cài đặt. |
| `FASTI_CORS_ALLOWED_ORIGINS` | (tắt) | bật CORS cho REST; `*` như Run trước đây |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (+`_FILE`) | | tạo admin nếu chưa có; mật khẩu truyền qua biến môi trường, không qua dòng lệnh |
| `FASTI_WEBAPP_PATH` | `/fasti` | `/camunda` để giữ đường dẫn cũ, không chuyển hướng |
| `FASTI_LEGACY_PATH_REDIRECT` | `true` | chuyển `/camunda/...` sang `/fasti/...` |
| `FASTI_CLASSIC_WEBAPPS` | `false` | `true`: phục vụ lại Cockpit, Tasklist, Admin, Welcome cổ điển (lỗi thời, AngularJS hết vòng đời; có cảnh báo) |
| `FASTI_MCP_ENABLED` | `false` | **Từ 1.1.0.** `true` (hoặc tham số `--mcp`): MCP server cho trợ lý AI tại `/mcp`, dùng tài khoản và quyền của engine ([docs/mcp.vi.md](../../docs/mcp.vi.md)) |
| `FASTI_MCP_WRITE_TOOLS_ENABLED` | `false` | **Từ 1.1.0.** `true`: bật thêm các công cụ MCP khởi động quy trình, nhận và hoàn thành task, chạy lại job |
| `FASTI_CONFIG` | | file YAML thêm (cách nhau dấu phẩy) |
| `FASTI_EXTRA_LIB` | | thư mục jar thêm (driver, plugin) |
| `FASTI_MARIADB_USE_MYSQL_DRIVER` | `true` | khi thiếu driver MariaDB, dùng MySQL Connector/J |
| `FASTI_PORT` | 8080 | cổng HTTP; `HEALTHCHECK` của image dùng cùng cổng này. **Từ 1.1.0:** giá trị không phải số (Kubernetes đặt `FASTI_PORT=tcp://...` cho Service tên `fasti`) bị bỏ qua kèm cảnh báo; các image 1.0.x không khởi động được với giá trị đó |
| `FASTI_LOG_FORMAT` | `text` | **Từ 1.1.0.** `json`: mỗi dòng log là một đối tượng JSON (Elastic Common Schema); có thêm `logstash`, `gelf` |
| `FASTI_METRICS_PROMETHEUS_ENABLED` | `false` | **Từ 1.1.0.** `true`: metrics Prometheus tại `/actuator/prometheus` (JVM, HTTP, pool, engine); cần token hoặc `FASTI_MANAGEMENT_PORT` |
| `FASTI_METRICS_PROMETHEUS_TOKEN` (+`_FILE`) | | **Từ 1.1.0.** Token scrape (`Authorization: Bearer <token>`) cho metrics trên cổng của ứng dụng; giá trị ngẫu nhiên ít nhất 16 ký tự (server không khởi động với token ngắn hơn hoặc với `change-me`) |
| `FASTI_MANAGEMENT_PORT` | | **Từ 1.1.0.** Actuator (health và metrics) chạy trên cổng riêng; `HEALTHCHECK` theo cổng này (giá trị không phải số bị bỏ qua kèm cảnh báo; khi đó `HEALTHCHECK` dùng `FASTI_PORT`, nếu không thì 8080) |
| `FASTI_TRACING_ENABLED` | `false` | **Từ 1.1.0.** `true`: span OpenTelemetry cho request, lệnh engine và job, gửi tới `OTEL_EXPORTER_OTLP_ENDPOINT` (OTLP/HTTP) |
| `FASTI_TRACING_ENGINE_ENABLED` | `true` | **Từ 1.1.0.** `false`: khi bật tracing, giữ span của request HTTP và bỏ span của lệnh engine và của các lần chạy job |
| `FASTI_ABSENCE_ENABLED` | `false` | **Từ 1.1.0.** `true`: [người thay thế khi vắng mặt](../../docs/absence.vi.md); hãy bật trên mọi node |
| `FASTI_FOUR_EYES_ENABLED` | `true` | **Từ 1.1.0.** [Kiểm soát bốn mắt](../../docs/four-eyes.vi.md) cho các user task mà mô hình khai báo `fasti:fourEyes`; `false` để tắt |
| `FASTI_AUDIT_ENABLED` | `false` | **Từ 1.1.0.** `true`: [nhật ký kiểm toán bất biến](../../docs/audit-trail.vi.md), trên mọi node. Trừ khi `FASTI_DB_SCHEMA_UPDATE=true`, hãy tạo bảng trước bằng `/fasti/configuration/sql/create/<database>_fasti_audit.sql` |
| `FASTI_AUDIT_HMAC_KEY`, `FASTI_AUDIT_HMAC_KEY_FILE` | | **Từ 1.1.0.** Khóa ký các điểm kiểm tra của nhật ký kiểm toán (ít nhất 32 byte), hoặc đường dẫn tệp chứa khóa (secret của Docker/Kubernetes) |
| `FASTI_PERFORMANCE_INDEX_CHECK` | `warn` | **Từ 1.2.0.** Một cảnh báo khi khởi động liệt kê các [chỉ mục hiệu năng tùy chọn](../../docs/performance.vi.md) của `/fasti/configuration/sql/optional/<database>_fasti_perf_indexes_create.sql` mà cơ sở dữ liệu chưa có; `off` để tắt |
| `FASTI_PERFORMANCE_CONFIG_CHECK` | `warn` | **Từ 1.2.0.** Cảnh báo khi khởi động cho các cấu hình tốn hiệu năng khi dữ liệu lớn dần (lịch sử không có khung giờ dọn, connection pool nhỏ hơn mức job executor cần, …); `off` để tắt |
| `FASTI_APPCDS`, `FASTI_APPCDS_FILE` | `false`, `/fasti/data/fasti-appcds.jsa` | class data sharing để khởi động nhanh hơn (xem Tinh chỉnh hiệu năng) |
| `FASTI_BASE64_INTRINSICS` | `false` | `false` thêm `-XX:-UseBASE64Intrinsics` để giảm thiểu CVE-2026-114938 ("Java4Shell"); `true` giữ intrinsic Base64 của JVM. Xem [docs/security/CVE-2026-114938.vi.md](../../docs/security/CVE-2026-114938.vi.md) |

Tham số lệnh của image cũ được nhận: `--webapps`, `--rest`, `--example`, `--oauth2`,
`--production` (cấu hình production cho container: bật xác thực REST, phân quyền, cookie
`Secure`, không CORS; TLS đặt ở proxy). `./camunda.sh` ở đầu lệnh được bỏ qua.
`--swaggerui` không có trong Fasti BPM.

File cấu hình gắn ở chỗ image cũ đọc vẫn được dùng: `/camunda/configuration/default.yml`
(và `production.yml` với `--production`), thư viện ở `/camunda/configuration/userlib`,
deployment ở `/camunda/configuration/resources`. Đường dẫn riêng của Fasti BPM nằm dưới `/fasti/...`.

## Driver JDBC

| Cơ sở dữ liệu | Trong image | Ghi chú |
|---|---|---|
| PostgreSQL | `postgresql` 42.7.14 | BSD-2-Clause |
| MySQL | `mysql-connector-j` 9.7.0 | GPLv2 + Universal FOSS Exception |
| MariaDB | `mariadb-java-client` 3.5.11 (LGPL-2.1) | build offline bỏ qua: khi đó dùng MySQL Connector/J với `jdbc:mysql:` |
| SQL Server | `mssql-jdbc` 13.6.2.jre11 | MIT; 13.x mã hóa mặc định: có thể cần `encrypt=false` hoặc `trustServerCertificate=true` |
| H2 | `h2` 2.5.252 | chỉ để thử |
| Oracle | không | giấy phép riêng: thêm `ojdbc11.jar` |
| DB2 | không | giấy phép riêng: thêm `jcc.jar` |

Thêm driver: image con (`FROM fasti-bpm:1.3.1` + `COPY ojdbc11.jar /fasti/configuration/userlib/`)
hoặc gắn thư mục và đặt `FASTI_EXTRA_LIB=/drivers`.

MySQL và MariaDB lưu thời gian của engine đến từng giây (schema Camunda 7.24 không có phần lẻ của
giây). **Từ 1.1.0** engine gửi mọi giá trị ngày giờ đã cắt về nguyên giây trên hai cơ sở dữ liệu
này, nên bộ lọc thời gian như `createdAfter`, `startedAfter` hay `dueDate` của task tìm thấy cả
dòng được lưu trong cùng giây, với driver nào cũng vậy; không cần tham số URL. MariaDB
Connector/J không có tùy chọn bỏ phần lẻ của giây, nên các image 1.0.x dùng driver đó có thể bỏ sót
các dòng như vậy; với 1.0.x, hãy kết nối qua MySQL Connector/J
(`DB_DRIVER=com.mysql.cj.jdbc.Driver`, `DB_URL=jdbc:mysql://host:3306/db?sendFractionalSeconds=false`).

## Vận hành

- **Health:** `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
  (readiness gồm kết nối cơ sở dữ liệu và engine). Chỉ endpoint health được mở; không cần
  đăng nhập. Kubernetes: liveness → `/actuator/health/liveness`, readiness →
  `/actuator/health/readiness`. `HEALTHCHECK` của image dùng readiness.
- **Metrics, tracing, log JSON (từ 1.1.0):** tắt theo mặc định; xem
  [docs/install-docker.vi.md](../../docs/install-docker.vi.md#13-observability). Endpoint
  `/actuator/prometheus` có sẵn thay được agent JMX exporter (`JMX_PROMETHEUS`) trong phần lớn trường hợp.
- **Giới hạn tốc độ:** đăng nhập sai nhiều lần làm tài khoản bị khóa tạm thời (thời gian tăng
  dần); kẻ tấn công có thể lợi dụng để khóa admin trong chốc lát. Đặt giới hạn tốc độ theo IP
  cho `/engine-rest` và `/fasti/api/admin/auth` ở proxy.
- **Dừng:** SIGTERM → dừng êm (request đang chạy được 30 s). Đặt thời gian chờ của nền tảng
  lớn hơn (ví dụ `docker stop -t 60`, `terminationGracePeriodSeconds: 60`).
- **Người dùng:** uid/gid 1000 (như image cũ), không phải root; ứng dụng chỉ đọc, chỉ
  `/fasti/data` ghi được (H2).
- **Log:** stdout.
- **Bộ nhớ:** heap = `FASTI_MAX_RAM_PERCENTAGE` % giới hạn bộ nhớ của container; JVM thoát khi
  hết bộ nhớ để nền tảng khởi động lại.
- **Volume:** không khai báo sẵn. H2: gắn `/fasti/data`. Deployment tự động: gắn
  `/fasti/configuration/resources`.
- **Sau proxy:** `X-Forwarded-*` được tin từ địa chỉ mạng nội bộ (Tomcat `RemoteIpValve`);
  proxy phải ghi đè các header này. Cookie `Secure` với `--production` cần HTTPS ở proxy.
- **Nhiều node sau load balancer:** bật sticky session cho web app (phiên đăng nhập nằm trong
  từng node); REST với Basic auth không cần. Mọi node phải dùng cùng mức lịch sử, cùng múi
  giờ, cùng plugin/lớp Java của process, cùng cấu hình mật khẩu.

## Tinh chỉnh hiệu năng

Số đo tham khảo (image này, PostgreSQL 16 cùng máy, 16 nhân, container giới hạn 2 GB, khoảng
50 nghìn process instance, 360 nghìn dòng lịch sử, 10 nghìn task mở):

| Hạng mục | Mặc định | Đã chỉnh |
|---|---|---|
| Job executor (job async/giây) | ~400 | ~1 000 (core 8, max 16, queue 16, 16 job/lần lấy, pool 30) |
| Tải lần đầu web UI mới (byte truyền) | 960 KB | 330 KB (gzip, bật sẵn) |
| Khởi động (`Started … in`) | khoảng 6–11 s tùy máy và tải | nhanh hơn một chút với `FASTI_APPCDS=true` (từ lần chạy thứ hai) |

- **JVM:** heap = `FASTI_MAX_RAM_PERCENTAGE` (75) % giới hạn bộ nhớ; luôn đặt giới hạn bộ
  nhớ cho container. Dưới 2 CPU hoặc dưới ~1,8 GB JVM tự chọn Serial GC; với tải thật nên cấp
  ít nhất 2 CPU và 2 GB, hoặc đặt `JAVA_OPTS=-XX:+UseG1GC`. Thêm cờ qua `JAVA_OPTS`.
- **Khởi động nhanh hơn:** `FASTI_APPCDS=true` ghi archive class data sharing vào
  `/fasti/data/fasti-appcds.jsa` khi container dừng êm; các lần chạy sau dùng lại. Cần
  `/fasti/data` ghi được (gắn volume để giữ qua các lần tạo lại container). Archive không khớp
  với JDK hoặc classpath thì JVM bỏ qua; khi nâng cấp image nên xóa file này để nó được tạo lại.
- **Pool kết nối (HikariCP):** mặc định tối đa 20 kết nối (đến 1.1 là 10): mười luồng job
  executor cộng chỗ cho các request REST đồng thời. Node đang rảnh giữ mở 10 kết nối và chỉ mở
  thêm khi có tải. Đổi bằng `FASTI_DB_CONN_MAXACTIVE` (ví dụ 30) và `FASTI_DB_CONN_MINIDLE`. Tổng
  số tối đa của mọi node (kể cả node Camunda 7.24), cộng các client khác và phần dự trữ của cơ sở
  dữ liệu, phải nằm dưới giới hạn kết nối của nó (`max_connections`); tính số node ở mức cao nhất
  (số bản sao tối đa khi tự co giãn cộng các pod thêm của một lần cập nhật cuốn chiếu).
- **Job executor:** mặc định giống Run: 10 luồng, hàng đợi 10 và 10 job mỗi lần lấy (đến 1.1:
  core 3, max 10, queue 3, 3 job mỗi lần lấy). Khi có nhiều job async/timer, tăng cùng lúc
  `CAMUNDA_BPM_JOB_EXECUTION_MAX_POOL_SIZE`, `..._QUEUE_CAPACITY` và
  `..._MAX_JOBS_PER_ACQUISITION`, và tăng pool kết nối thêm từng đó
  ([tinh chỉnh khi chạy](../../docs/tuning-runtime.vi.md)). Số luồng lõi theo `..._MAX_POOL_SIZE`
  trừ khi đặt `..._CORE_POOL_SIZE`; số lõi lớn hơn số tối đa được hạ xuống bằng số tối đa kèm
  cảnh báo. Các job độc quyền của cùng một phiên bản quy trình được lấy cùng lúc sẽ chạy lần lượt
  dưới một khóa `..._LOCK_TIME_IN_MILLIS` (5 phút): giữ 10 × job chậm nhất loại này dưới mức đó,
  hoặc tăng nó, nếu không một job có thể chạy hai lần. Tắt hẳn job executor trên node chỉ phục vụ
  REST/UI: `FASTI_JOB_EXECUTOR_ENABLED=false`.
- **Nhiều node:** mỗi node có job executor riêng lấy job từ cùng bảng; thêm node tăng thông
  lượng job nhưng cũng tăng xung đột khóa lạc quan (vô hại, job được thử lại). Giữ
  `max-jobs-per-acquisition` vừa phải (≤ 16) khi có nhiều node. Web UI cần sticky session.
- **Nén và cache:** gzip bật cho trang, script, style và JSON trên 2 KB
  (`server.compression.enabled=false` nếu proxy đã nén). File của web UI mới có hash trong tên
  được cache một năm (`immutable`); `index.html` luôn được kiểm tra lại (`no-cache`).
- **Chỉ mục cơ sở dữ liệu:** image không thêm hay đổi chỉ mục; schema giữ nguyên như 7.24.0.
  **Từ 1.2.0:** chỉ mục tùy chọn cho cơ sở dữ liệu lớn là các script do quản trị viên chạy,
  `/fasti/configuration/sql/optional/<database>_fasti_perf_indexes_create.sql` (và `_drop.sql`),
  an toàn khi chạy cạnh node Camunda 7.24 ([hướng dẫn hiệu năng](../../docs/performance.vi.md)).

