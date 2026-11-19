# Fasti BPM — image container / container image

[Tiếng Việt](#tiếng-việt) · [English](#english)

---

## Tiếng Việt

Image chạy **Fasti BPM Run** (engine, REST API `/engine-rest` và web app mới ở `/fasti/`; các
web app cổ điển **tắt theo mặc định**, xem bên dưới). Image được làm để **trỏ vào cơ sở dữ liệu mà một hệ Camunda 7 (7.24) đang
dùng** và chạy tiếp trên đó: người dùng, mật khẩu, nhóm, tenant, phân quyền, filter,
deployment, instance đang chạy, lịch sử, job và incident giữ nguyên. Fasti BPM tương thích với
cơ sở dữ liệu của Camunda 7; đây là dự án độc lập, không liên kết với Camunda.

Hướng dẫn chuyển đổi đầy đủ (các bước, quay lại, chạy lẫn với node cũ, giới hạn):
[docs/chuyen-tu-camunda-7.md](../../docs/chuyen-tu-camunda-7.md).

### Build

```bash
# 1. bản phân phối Run, driver JDBC và Dockerfile vào distro/docker/target/docker
./mvnw -pl distro/docker -am package -DskipTests
# 2. image
docker build -t fasti-bpm:1.0.0 \
  --build-arg VERSION=1.0.0 --build-arg REVISION=$(git rev-parse --short HEAD) \
  distro/docker/target/docker
```

- Build không có mạng: thêm `-o -Dfasti.docker.skipMariadbDriver=true` (khi đó không có
  driver MariaDB, xem bảng driver).
- Image ổn định hơn giữa các lần build: ghim image nền bằng digest (bước `apk upgrade` vẫn lấy
  bản vá mới nhất của Alpine),
  `--build-arg JRE_IMAGE=eclipse-temurin:21-jre-alpine@sha256:<digest>`.

### Bảo mật mặc định — khác với Camunda Run

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
  mật khẩu. `FASTI_INITIAL_SETUP=true` bật lại trang thiết lập (có cảnh báo).
- Ứng dụng **từ chối khởi động** nếu cấu hình thực tế (file YAML gắn vào, biến môi trường,
  `SPRING_APPLICATION_JSON`, tham số) tắt xác thực REST hoặc phân quyền, hoặc đặt
  `camunda.bpm.admin-user.id` mà không có mật khẩu.
- **Chế độ mở chỉ để demo:** `FASTI_INSECURE_DEMO=true` tắt xác thực và phân quyền, in cảnh báo
  lớn, và bị từ chối cùng `--production`. Chỉ publish cổng trên 127.0.0.1
  (`-p 127.0.0.1:8080:8080`) và không bao giờ dùng với dữ liệu thật.
- `--production` còn từ chối H2 ngầm định và `DEBUG=true`.

### Chỉ giao diện web mới — web app cổ điển tắt theo mặc định

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

### Chạy trên cơ sở dữ liệu có sẵn

Dùng đúng các biến môi trường của image Camunda 7 Run trước đây:

```bash
docker run -d --name fasti -p 8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=Asia/Ho_Chi_Minh \
  fasti-bpm:1.0.0
```

`DB_PASSWORD_FILE` phải trỏ tới một file có trong container (ví dụ Docker/Kubernetes secret, hoặc
`-v /đường/dẫn/db-password:/run/secrets/db-password:ro`); cũng có thể dùng `DB_PASSWORD`.
Rồi mở `http://localhost:8080/fasti/` và đăng nhập bằng tài khoản đã có; client REST gửi
Basic auth của tài khoản đó (ví dụ `curl -u alice:… http://localhost:8080/engine-rest/task`).

Cơ sở dữ liệu mới, trống: thêm `-e FASTI_DB_SCHEMA_UPDATE=true` (tạo bảng) và
`-e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD=...`.
Không đặt `DB_URL`/`FASTI_DB_URL` (và không có `SPRING_DATASOURCE_URL`, `SKIP_DB_CONFIG` hay file
cấu hình riêng): image dùng H2 trong `/fasti/data`, chỉ để thử.

### Biến môi trường

Tên biến của image cũ và các bí danh `FASTI_*` đều được chấp nhận (khi đặt cả hai, giá trị `FASTI_*` được dùng;
với `DEBUG` và `JMX_PROMETHEUS`, chỉ cần một trong hai tên là `true`). Mọi thuộc tính
Spring Boot cũng đặt được bằng biến môi trường (ví dụ `CAMUNDA_BPM_RUN_AUTH_ENABLED=true`).

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
| `FASTI_DB_SCHEMA_UPDATE` | `false` | `true` tạo bảng còn thiếu (cơ sở dữ liệu mới); không bao giờ nâng cấp schema |
| `FASTI_SCHEMA_VERSION_CHECK` | `enforce` | `warn`: schema cũ hơn 7.24 chỉ cảnh báo thay vì dừng |
| `FASTI_DB_SCHEMA`, `FASTI_DB_TABLE_PREFIX` | | `camunda.bpm.database.schema-name` / `table-prefix` |
| `FASTI_HISTORY_LEVEL` | `auto` | mặc định lấy mức lịch sử đang lưu trong cơ sở dữ liệu (mới: `full`) |
| `FASTI_JOB_EXECUTOR_ENABLED` | `true` | tắt job executor của node này |
| `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | `false` | chỉ chạy job của deployment đã đăng ký trên node này |
| `FASTI_INSECURE_DEMO` | `false` | `true`: **tắt** xác thực REST và phân quyền, chỉ để demo (xem Bảo mật mặc định) |
| `FASTI_REST_AUTH` | `true` | luôn bật; giá trị `false` bị từ chối (dùng `FASTI_INSECURE_DEMO`) |
| `FASTI_INITIAL_SETUP` | `false` | `true`: bật trang tạo admin đầu tiên khi chưa có admin |
| `FASTI_CORS_ALLOWED_ORIGINS` | (tắt) | bật CORS cho REST; `*` như Run trước đây |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (+`_FILE`) | | tạo admin nếu chưa có; mật khẩu truyền qua biến môi trường, không qua dòng lệnh |
| `FASTI_WEBAPP_PATH` | `/fasti` | `/camunda` để giữ đường dẫn cũ, không chuyển hướng |
| `FASTI_LEGACY_PATH_REDIRECT` | `true` | chuyển `/camunda/...` sang `/fasti/...` |
| `FASTI_CLASSIC_WEBAPPS` | `false` | `true`: phục vụ lại Cockpit, Tasklist, Admin, Welcome cổ điển (lỗi thời, AngularJS hết vòng đời; có cảnh báo) |
| `FASTI_CONFIG` | | file YAML thêm (cách nhau dấu phẩy) |
| `FASTI_EXTRA_LIB` | | thư mục jar thêm (driver, plugin) |
| `FASTI_MARIADB_USE_MYSQL_DRIVER` | `true` | khi thiếu driver MariaDB, dùng MySQL Connector/J |
| `FASTI_PORT` | 8080 | cổng HTTP; `HEALTHCHECK` của image dùng cùng cổng này |
| `FASTI_APPCDS`, `FASTI_APPCDS_FILE` | `false`, `/fasti/data/fasti-appcds.jsa` | class data sharing để khởi động nhanh hơn (xem Tinh chỉnh hiệu năng) |
| `FASTI_BASE64_INTRINSICS` | `false` | `false` thêm `-XX:-UseBASE64Intrinsics` để giảm thiểu CVE-2026-114938 ("Java4Shell"); `true` giữ intrinsic Base64 của JVM. Xem [docs/security/CVE-2026-114938.md](../../docs/security/CVE-2026-114938.md) |

Tham số lệnh của image cũ được nhận: `--webapps`, `--rest`, `--example`, `--oauth2`,
`--production` (cấu hình production cho container: bật xác thực REST, phân quyền, cookie
`Secure`, không CORS; TLS đặt ở proxy). `./camunda.sh` ở đầu lệnh được bỏ qua.
`--swaggerui` không có trong Fasti BPM.

File cấu hình gắn ở chỗ image cũ đọc vẫn được dùng: `/camunda/configuration/default.yml`
(và `production.yml` với `--production`), thư viện ở `/camunda/configuration/userlib`,
deployment ở `/camunda/configuration/resources`. Đường dẫn riêng của Fasti BPM nằm dưới `/fasti/...`.

### Driver JDBC

| Cơ sở dữ liệu | Trong image | Ghi chú |
|---|---|---|
| PostgreSQL | `postgresql` 42.7.14 | BSD-2-Clause |
| MySQL | `mysql-connector-j` 9.7.0 | GPLv2 + Universal FOSS Exception |
| MariaDB | `mariadb-java-client` 3.5.11 (LGPL-2.1) | build offline bỏ qua: khi đó dùng MySQL Connector/J với `jdbc:mysql:` |
| SQL Server | `mssql-jdbc` 13.4.0.jre11 | MIT; 13.x mã hóa mặc định: có thể cần `encrypt=false` hoặc `trustServerCertificate=true` |
| H2 | `h2` 2.5.252 | chỉ để thử |
| Oracle | không | giấy phép riêng: thêm `ojdbc11.jar` |
| DB2 | không | giấy phép riêng: thêm `jcc.jar` |

Thêm driver: image con (`FROM fasti-bpm:1.0.0` + `COPY ojdbc11.jar /fasti/configuration/userlib/`)
hoặc gắn thư mục và đặt `FASTI_EXTRA_LIB=/drivers`.

### Vận hành

- **Health:** `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
  (readiness gồm kết nối cơ sở dữ liệu và engine). Chỉ endpoint health được mở; không cần
  đăng nhập. Kubernetes: liveness → `/actuator/health/liveness`, readiness →
  `/actuator/health/readiness`. `HEALTHCHECK` của image dùng readiness.
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


### Tinh chỉnh hiệu năng

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
- **Pool kết nối (HikariCP):** mặc định 10. Cần lớn hơn số luồng job executor cộng số request
  REST đồng thời mong đợi: `FASTI_DB_CONN_MAXACTIVE` (ví dụ 30). Tổng pool của mọi node phải
  nằm dưới `max_connections` của cơ sở dữ liệu.
- **Job executor:** mặc định giống Run (core 3, max 10, queue 3). Khi có nhiều job async/timer:
  `CAMUNDA_BPM_JOB_EXECUTION_CORE_POOL_SIZE`, `..._MAX_POOL_SIZE`, `..._QUEUE_CAPACITY`,
  `..._MAX_JOBS_PER_ACQUISITION`; tăng pool kết nối theo. Tắt hẳn trên node chỉ phục vụ REST/UI:
  `FASTI_JOB_EXECUTOR_ENABLED=false`.
- **Nhiều node:** mỗi node có job executor riêng lấy job từ cùng bảng; thêm node tăng thông
  lượng job nhưng cũng tăng xung đột khóa lạc quan (vô hại, job được thử lại). Giữ
  `max-jobs-per-acquisition` vừa phải (≤ 16) khi có nhiều node. Web UI cần sticky session.
- **Nén và cache:** gzip bật cho trang, script, style và JSON trên 2 KB
  (`server.compression.enabled=false` nếu proxy đã nén). File của web UI mới có hash trong tên
  được cache một năm (`immutable`); `index.html` luôn được kiểm tra lại (`no-cache`).
- **Chỉ mục cơ sở dữ liệu:** image không thêm hay đổi chỉ mục; schema giữ nguyên như 7.24.0.

---

## English

The image runs **Fasti BPM Run** (engine, REST API at `/engine-rest` and the new web
application at `/fasti/`; the classic web applications are **off by default**, see below). It is made to be **pointed at the database an
existing Camunda 7 (7.24) installation uses** and to carry on with it: users, passwords,
groups, tenants, authorizations, filters, deployments, running instances, history, jobs and
incidents stay as they are. Fasti BPM is compatible with Camunda 7 databases; it is an
independent project, not affiliated with Camunda.

Full migration guide (steps, rollback, mixed operation, limits):
[docs/chuyen-tu-camunda-7.md](../../docs/chuyen-tu-camunda-7.md#english).

### Build

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:1.0.0 \
  --build-arg VERSION=1.0.0 --build-arg REVISION=$(git rev-parse --short HEAD) \
  distro/docker/target/docker
```

Offline: add `-o -Dfasti.docker.skipMariadbDriver=true` (the MariaDB driver is then left out,
see JDBC drivers). More repeatable builds: pin the base image by
digest (the `apk upgrade` step still takes the latest Alpine fixes) with `--build-arg JRE_IMAGE=eclipse-temurin:21-jre-alpine@sha256:<digest>`.

### Secure by default — different from Camunda Run

> **Deliberate difference:** Camunda Run and the former Camunda 7 image serve `/engine-rest`
> **without authentication** and with **authorizations off**. The Fasti image turns **both on by
> default**, with or without `--production`; only `FASTI_INSECURE_DEMO=true` (demos) turns them off.

- The REST API requires Basic authentication (or a bearer token with OIDC); authorization checks
  are always on. Users, groups and authorizations come from the database, so pointing `DB_*` at
  the database of an existing Camunda 7 installation keeps its logins (REST and web UI).
- **Migration impact:** every REST client (external task workers, integrations, scripts) must
  send `Authorization: Basic …` of a user with suitable permissions, or a bearer token. If the
  old installation ran with authorizations off, `ACT_RU_AUTHORIZATION` may lack grants: members
  of `camunda-admin` can still do everything, other users may see empty lists or get 403. Check
  first (`select * from ACT_ID_MEMBERSHIP where GROUP_ID_='camunda-admin'`,
  `select count(*) from ACT_RU_AUTHORIZATION`) and grant permissions in Admin before moving
  traffic.
- There is no anonymous "create the first administrator" page. For an empty database (or the H2
  demo) set `FASTI_ADMIN_USER` + `FASTI_ADMIN_PASSWORD` (or `_FILE`); an existing administrator
  is left unchanged (no password reset). `FASTI_INITIAL_SETUP=true` brings the setup page back,
  with a warning.
- The application **refuses to start** if the effective configuration (mounted YAML files,
  environment variables, `SPRING_APPLICATION_JSON`, arguments) turns REST authentication or
  authorization off, or sets `camunda.bpm.admin-user.id` without a password.
- **Open mode is for demos only:** `FASTI_INSECURE_DEMO=true` turns authentication and
  authorization off, logs a large warning and is refused together with `--production`. Publish
  the port on 127.0.0.1 only (`-p 127.0.0.1:8080:8080`) and never use it with real data.
- `--production` also refuses the implicit H2 database and `DEBUG=true`.

### New web UI only — classic web applications off by default

> **Breaking change:** the image serves only the new web UI (`/fasti/app/next/`). The classic
> Cockpit, Tasklist, Admin and Welcome are built on AngularJS 1.8.2 and Bootstrap 3, both end of
> life (no more security fixes), and are no longer served. Users of the classic applications in
> the image move to the new web UI or turn them back on with `FASTI_CLASSIC_WEBAPPS=true`.

- Pages of the classic applications and old bookmarks are redirected (`302`) to the matching
  section of the new web UI, keeping the engine name: `/fasti/app/cockpit/default/` →
  `/fasti/app/next/default/#/cockpit`, Tasklist → `#/tasklist`, Admin → `#/admin`, Welcome →
  the start page. `/camunda/app/...` goes through the `/camunda` → `/fasti` redirect and on to
  the new web UI. Routes inside a classic application (the part after `#`) cannot be
  translated: users land on the start page of the section.
- Scripts, styles and translations of the classic applications and the static files of classic
  plugins (`/fasti/api/{cockpit,tasklist,admin}/plugin/*/static/...`) answer `404`, also the
  copy Spring Boot serves below `/webjars/camunda/app/...`. The files
  stay in the image (so that they can be turned back on) but are not served.
- The REST resources of the web applications (`/fasti/api/...`) keep working: the new web UI
  uses them. The frame that renders embedded forms (`/fasti/app/forms/`) still loads
  `app/tasklist/scripts/config.js` and the custom scripts it names; the frame is sandboxed from
  the new web UI. The embedded forms frame itself still uses AngularJS (Camunda 7 embedded forms
  need it), so `/fasti/lib/` and `/fasti/assets/` stay served for it.
- `FASTI_CLASSIC_WEBAPPS=true` (or `fasti.webapp.classic.enabled=true`) serves the classic
  applications again, with a `WARN` at startup that they are deprecated and built on end-of-life
  AngularJS. They will be removed in a later release.
- A `fasti.webapp.default-app` that names a classic application is ignored (with a warning)
  while they are off.
- The new web UI has no CMMN screens yet; to look at CMMN cases in the classic Cockpit use
  `FASTI_CLASSIC_WEBAPPS=true`, or the REST API.

### Run against an existing database

```bash
docker run -d --name fasti -p 8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=Asia/Ho_Chi_Minh \
  fasti-bpm:1.0.0
```

`DB_PASSWORD_FILE` must name a file inside the container (a Docker/Kubernetes secret, or
`-v /path/to/db-password:/run/secrets/db-password:ro`); `DB_PASSWORD` works as well.
Open `http://localhost:8080/fasti/` and sign in with an existing account; REST clients send that
account's Basic credentials. New, empty database: add `FASTI_DB_SCHEMA_UPDATE=true` and
`FASTI_ADMIN_USER`/`FASTI_ADMIN_PASSWORD`. Without `DB_URL`/`FASTI_DB_URL` (and without `SPRING_DATASOURCE_URL`, `SKIP_DB_CONFIG`
or a configuration file of your own) the image uses H2 in `/fasti/data`, for trying out only.

### Environment variables

The names of the former Camunda 7 Run image and `FASTI_*` aliases are accepted (when both are
set, the `FASTI_*` value is used; `DEBUG` and `JMX_PROMETHEUS` are on if either name is `true`); any Spring Boot property can be set as an environment variable too. See the table in
the Vietnamese part for the full list; in short:

- database: `DB_URL`, `DB_DRIVER`, `DB_USERNAME`, `DB_PASSWORD` (and `_FILE`), `DB_CONN_MAXACTIVE`,
  `DB_CONN_MINIDLE`, `DB_VALIDATE_ON_BORROW` + `DB_VALIDATION_QUERY` (mapped to HikariCP),
  `SKIP_DB_CONFIG`, `WAIT_FOR`, `WAIT_FOR_TIMEOUT`; `DB_CONN_MAXIDLE` has no HikariCP
  counterpart and is ignored;
- JVM: `TZ` (must match every other node), `DEBUG` (port `FASTI_DEBUG_PORT`, 8000),
  `JMX_PROMETHEUS` (port `JMX_PROMETHEUS_PORT`, 9404, configuration `JMX_PROMETHEUS_CONF`, agent
  jar in `/fasti/javaagent`), `JAVA_OPTS` (space-separated, no quotes), `FASTI_MAX_RAM_PERCENTAGE`
  (75), `FASTI_APPCDS` / `FASTI_APPCDS_FILE` (class data sharing);
  `FASTI_BASE64_INTRINSICS` (default `false`: adds `-XX:-UseBASE64Intrinsics` against CVE-2026-114938, "Java4Shell");
- schema: `FASTI_DB_SCHEMA_UPDATE` (default `false`; `true` creates missing tables, it never
  upgrades), `FASTI_SCHEMA_VERSION_CHECK` (`enforce` | `warn`), `FASTI_DB_SCHEMA`,
  `FASTI_DB_TABLE_PREFIX`;
- engine: `FASTI_HISTORY_LEVEL` (default `auto`: the level stored in the database, `full` for a
  new one), `FASTI_JOB_EXECUTOR_ENABLED` (`true`), `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` (`false`);
- security: `FASTI_INSECURE_DEMO` (`false`; `true` turns REST authentication and authorization
  off, demo only), `FASTI_REST_AUTH` (always on; `false` is refused), `FASTI_INITIAL_SETUP`
  (`false`);
- web/REST:
  `FASTI_CORS_ALLOWED_ORIGINS` (off; `*` restores Run's former default), `FASTI_ADMIN_USER`,
  `FASTI_ADMIN_PASSWORD`, `FASTI_WEBAPP_PATH` (`/camunda` keeps the old path),
  `FASTI_LEGACY_PATH_REDIRECT`, `FASTI_CLASSIC_WEBAPPS` (`false`; `true` serves the deprecated
  classic applications again, with a warning), `FASTI_PORT` (8080; the `HEALTHCHECK` follows it);
- files: `FASTI_CONFIG` (extra YAML files), `FASTI_EXTRA_LIB` (extra jars),
  `FASTI_MARIADB_USE_MYSQL_DRIVER` (`true`: use MySQL Connector/J when the MariaDB driver is
  missing).

Start arguments of the former image work: `--webapps`, `--rest`, `--example`, `--oauth2`,
`--production` (container production settings: REST authentication, authorizations, `Secure`
cookies, no CORS; TLS at the proxy). A leading `./camunda.sh` is ignored; `--swaggerui` does
not exist in Fasti BPM. Files mounted where the former image read them are used:
`/camunda/configuration/default.yml` (and `production.yml` with `--production`),
`/camunda/configuration/userlib`, `/camunda/configuration/resources`. Fasti BPM's own paths are
below `/fasti/...`.

### JDBC drivers

Bundled (licences in the License Book and NOTICE): PostgreSQL 42.7.14 (BSD-2-Clause), MySQL
Connector/J 9.7.0 (GPLv2 with the Universal FOSS Exception), MariaDB Connector/J 3.5.11
(LGPL-2.1, unless built
with `skipMariadbDriver`; then MariaDB is reached through MySQL Connector/J), SQL Server
13.4.0.jre11 (MIT; encrypts by default: you may need `encrypt=false` or
`trustServerCertificate=true`), H2 2.5.252. Oracle (`ojdbc11.jar`) and DB2 (`jcc.jar`) are
licensed separately and not redistributed: add them in a derived image
(`COPY ojdbc11.jar /fasti/configuration/userlib/`) or mount a directory and set
`FASTI_EXTRA_LIB`.

### Operations

- Health: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
  (readiness includes the database and the engine); nothing else of the actuator is exposed,
  no login needed. The image's `HEALTHCHECK` polls readiness.
- Rate limiting: repeated failed logins lock the account for an increasing time, which an
  attacker can abuse to lock out an administrator briefly. Rate-limit `/engine-rest` and
  `/fasti/api/admin/auth` per IP at the proxy.
- SIGTERM shuts down gracefully (30 s for running requests); give the platform more
  (`docker stop -t 60`, `terminationGracePeriodSeconds: 60`).
- Runs as uid/gid 1000 (like the former image), not root; the application files are read-only, only `/fasti/data` is
  writable. Logs go to stdout. Heap is `FASTI_MAX_RAM_PERCENTAGE` % of the container limit and
  the JVM exits on out-of-memory.
- No volumes are declared: mount `/fasti/data` for H2 and `/fasti/configuration/resources` for
  automatic deployments.
- Behind a proxy, `X-Forwarded-*` headers are trusted from private networks; the proxy must
  overwrite them. `--production` sets `Secure` cookies and needs HTTPS at the proxy.
- Several nodes behind a load balancer: use sticky sessions for the web applications (sessions
  are per node); Basic-auth REST calls need none. All nodes need the same history level, time
  zone, process classes and plugins, and password settings.

### Performance tuning

Reference numbers (this image, PostgreSQL 16 on the same host, 16 cores, 2 GB container limit,
about 50k process instances, 360k history rows, 10k open tasks):

| Item | Default | Tuned |
|---|---|---|
| Job executor (async jobs/s) | ~400 | ~1,000 (core 8, max 16, queue 16, 16 jobs per acquisition, pool 30) |
| First load of the new web UI (bytes transferred) | 960 KB | 330 KB (gzip, on by default) |
| Startup (`Started … in`) | about 6–11 s depending on host and load | somewhat faster with `FASTI_APPCDS=true` (from the second run) |

- **JVM:** heap is `FASTI_MAX_RAM_PERCENTAGE` (75) % of the memory limit; always set a memory
  limit. Below 2 CPUs or ~1.8 GB the JVM picks Serial GC; give real workloads at least 2 CPUs
  and 2 GB, or set `JAVA_OPTS=-XX:+UseG1GC`. Extra flags go into `JAVA_OPTS`.
- **Faster startup:** `FASTI_APPCDS=true` writes a class data sharing archive to
  `/fasti/data/fasti-appcds.jsa` on a clean shutdown and reuses it on later starts. `/fasti/data`
  must be writable (mount a volume to keep it across re-created containers). An archive that
  does not match the JDK or class path is ignored; delete the file when upgrading the image so
  that it is created again.
- **Connection pool (HikariCP):** 10 by default. Size it above the job executor threads plus the
  expected concurrent REST requests with `FASTI_DB_CONN_MAXACTIVE` (for example 30). The pools of
  all nodes together must stay below the database's `max_connections`.
- **Job executor:** defaults as in Run (core 3, max 10, queue 3). For many async/timer jobs set
  `CAMUNDA_BPM_JOB_EXECUTION_CORE_POOL_SIZE`, `..._MAX_POOL_SIZE`, `..._QUEUE_CAPACITY`,
  `..._MAX_JOBS_PER_ACQUISITION`, and grow the connection pool with them. Switch it off on
  nodes that only serve REST/UI: `FASTI_JOB_EXECUTOR_ENABLED=false`.
- **Several nodes:** every node's job executor acquires from the same tables; more nodes raise
  job throughput and also optimistic locking conflicts (harmless, the job is retried). Keep
  `max-jobs-per-acquisition` moderate (≤ 16) with many nodes. The web UI needs sticky sessions.
- **Compression and caching:** gzip is on for pages, scripts, styles and JSON above 2 KB
  (`server.compression.enabled=false` if a proxy compresses already). Content-hashed files of
  the new web UI are cached for a year (`immutable`); `index.html` is always revalidated
  (`no-cache`).
- **Database indexes:** the image adds or changes no index; the schema stays identical to 7.24.0.
