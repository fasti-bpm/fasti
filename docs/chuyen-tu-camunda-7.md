# Chuyển từ Camunda 7 sang Fasti BPM / Moving from Camunda 7 to Fasti BPM

[Tiếng Việt](#tiếng-việt) · [English](#english)

Fasti BPM được xây dựng trên Camunda 7 Community Edition 7.24.0 và tương thích với cơ sở dữ
liệu của Camunda 7. Fasti BPM là dự án độc lập, không liên kết với Camunda Services GmbH.

*Fasti BPM is built on Camunda 7 Community Edition 7.24.0 and is compatible with Camunda 7
databases. It is an independent project, not affiliated with Camunda Services GmbH.*

---

## Tiếng Việt

### Tóm tắt

Nếu hệ của bạn chạy Camunda 7 **7.24** (Run, image container, Spring Boot starter, Tomcat hay
WildFly), Fasti chạy được trên **chính cơ sở dữ liệu đó, không đổi gì trong schema**: Fasti
dùng nguyên schema của 7.24 và không ghi thêm bảng, cột hay phiên bản schema nào. Bạn có thể
thay hẳn các node cũ, chạy Fasti song song với chúng, và quay lại Camunda 7.24 bất cứ lúc nào.

Cơ sở dữ liệu của 7.23 trở về trước phải được nâng cấp lên 7.24 bằng script SQL trước (xem
[Phiên bản cũ hơn](#phiên-bản-cũ-hơn-717723)). Fasti không tự nâng cấp schema, và từ chối
khởi động trên schema cũ với thông báo nêu rõ script cần chạy.

### Các bước (image container)

1. **Sao lưu cơ sở dữ liệu.** Không bước nào dưới đây sửa schema, nhưng sao lưu là bắt buộc
   trước mọi thay đổi trên hệ production.
2. **Kiểm tra phiên bản schema:**
   `select VERSION_ from ACT_GE_SCHEMA_LOG` — giá trị lớn nhất phải là `7.24.x`.
3. **Ghi lại cấu hình đang dùng** của node cũ: biến `DB_*`, mức lịch sử
   (`camunda.bpm.history-level`, `ACT_GE_PROPERTY.historyLevel`), xác thực REST
   (`camunda.bpm.run.auth.enabled`), phân quyền (`camunda.bpm.authorization.enabled`), plugin,
   LDAP/SSO, múi giờ JVM, cấu hình mật khẩu (`passwordEncryptor`, `saltGenerator`), tiền tố
   bảng/schema.
4. **Build image** theo [distro/docker/README.md](../distro/docker/README.md).
5. **Chạy Fasti với cùng biến `DB_*`** (và `TZ` giống node cũ). Khác Camunda Run, image Fasti
   **bật sẵn xác thực REST và phân quyền**: tài khoản trong cơ sở dữ liệu đăng nhập được,
   nhưng mọi client REST phải gửi Basic auth hoặc bearer token, và user không thuộc
   `camunda-admin` cần có quyền trong `ACT_RU_AUTHORIZATION` (xem "Bảo mật mặc định" trong
   [distro/docker/README.md](../distro/docker/README.md)). `FASTI_INSECURE_DEMO=true` chỉ để demo. Fasti kiểm tra phiên bản schema và in
   `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
6. **Kiểm tra:** đăng nhập `/fasti/` bằng tài khoản có sẵn; xem filter, task, instance, lịch
   sử; `GET /engine-rest/task` (kèm Basic auth); `GET /actuator/health/readiness`.
7. **Chuyển lưu lượng** (load balancer, DNS) sang Fasti; dừng node cũ, hoặc để chạy song song
   (xem [Chạy lẫn](#chạy-lẫn-với-node-camunda-7)).

Spring Boot starter / Tomcat / WildFly: thay các artifact `org.camunda.bpm:camunda-*` bằng
`dev.fasti.platform:fasti-*` cùng chức năng, giữ nguyên cấu hình `camunda.bpm.*`, `bpm-platform.xml`
và package `org.camunda.bpm.*`. Không để jar engine của Camunda và của Fasti cùng nằm trên
classpath: Fasti dừng khởi động khi thấy cả hai (giới hạn của phép kiểm tra này: xem mục
"Kiểm tra khi khởi động" trong [README](../README.md#kiểm-tra-khi-khởi-động)).

> **Spring Boot starter:** Fasti chạy trên **Spring Boot 4.1** và cần **Java 17 trở lên**. Ứng
> dụng đang dùng starter của Camunda 7.24 (Spring Boot 3.5) phải nâng lên Spring Boot 4.1 và
> Java 17+ cùng lúc với việc đổi artifact. Xem [README](../README.md#khác-gì-so-với-camunda-7240-ce)
> và [CHANGELOG](../CHANGELOG.md#changed).

### Bản phân phối Tomcat và WildFly

> **Bắt buộc khi tiếp quản cơ sở dữ liệu của Camunda Run** (hoặc của máy chủ mà process được
> deploy qua REST API): bản Tomcat và WildFly giữ `jobExecutorDeploymentAware=true` như Camunda
> 7. Job (timer, async, retry) của deployment không đăng ký trên node đó **không chạy, và không
> báo lỗi**. Trước khi khởi động, đặt
> `<property name="jobExecutorDeploymentAware">false</property>` trong `conf/bpm-platform.xml`
> (Tomcat) hoặc trong phần properties của engine trong `standalone.xml` (WildFly). Giữ `true` nếu node khác chạy process application có lớp Java
> riêng (xem [Chạy lẫn](#chạy-lẫn-với-node-camunda-7)).

Khác với bản Tomcat/WildFly của Camunda 7.24:

- **REST API cần đăng nhập** (breaking). `/engine-rest` bật sẵn bộ lọc `camunda-auth`
  (HTTP Basic): mọi client REST phải gửi tên và mật khẩu của một người dùng của engine,
  không có thì nhận 401; chỉ danh sách engine (`/engine`) còn mở. Client REST, worker
  external task và script đang gọi REST không đăng nhập phải được cấu hình thông tin đăng
  nhập trước khi chuyển. Bearer token OpenID Connect (`fasti-oidc.properties`,
  `fasti.security.oidc.rest.bearer-enabled=true`) dùng được cùng lúc: request có token hợp lệ
  không cần mật khẩu. Tắt xác thực (**không khuyến nghị**): xóa bộ lọc `camunda-auth` và
  `filter-mapping` của nó trong `WEB-INF/web.xml` của WAR `engine-rest`.
- **Không có người dùng mặc định** (breaking). Ví dụ hóa đơn không còn được triển khai sẵn
  nên không tạo `demo/demo` (quản trị viên), `john`, `mary`, `peter`; WAR của nó nằm ở
  `examples/camunda-invoice.war`, chỉ chép vào `webapps/` (Tomcat) hoặc
  `standalone/deployments/` (WildFly) để thử. Trên cơ sở dữ liệu trống, mở `/fasti/` và tạo
  quản trị viên đầu tiên ở trang thiết lập; trên cơ sở dữ liệu của Camunda 7 dùng tài khoản có
  sẵn.
- **Không phục vụ Cockpit, Tasklist, Admin, Welcome cổ điển** (breaking), như image: trang và
  bookmark chuyển tới mục tương ứng của giao diện mới, file trả 404. Bật lại tạm thời (lỗi thời,
  sẽ bị gỡ): `-Dfasti.webapp.classic.enabled=true` trong `CATALINA_OPTS` (Tomcat) hoặc
  `JAVA_OPTS` (WildFly), biến môi trường `FASTI_CLASSIC_WEBAPPS=true`, hoặc context parameter
  `fasti.webapp.classic.enabled` trong `WEB-INF/web.xml` của web app.

### Cái gì giống hệt

- Schema cơ sở dữ liệu và giá trị `ACT_GE_PROPERTY`/`ACT_GE_SCHEMA_LOG` (Fasti không thêm dòng
  phiên bản), installation id, sinh id (`StrongUuidGenerator` trong Run/Spring Boot,
  `DbIdGenerator` mặc định của engine).
- Mật khẩu: SHA-512 + salt 16 byte như Camunda 7 — người dùng cũ đăng nhập được, mật khẩu đổi
  trên Fasti dùng được trên Camunda 7.
- REST API `/engine-rest` (hành vi giữ nguyên, chỉ thêm endpoint mới), external task
  (`fetchAndLock`, long polling), client Java/JS; cookie `JSESSIONID`, CSRF `XSRF-TOKEN` /
  `X-XSRF-TOKEN`; thuộc tính `camunda.bpm.*`; namespace BPMN `camunda:`.
- Filter của Tasklist cổ điển (`color`, `description`, `priority`, `refresh`, `variables`,
  `showUndefinedVariable`) được Tasklist mới đọc và lưu cùng định dạng.

### Cái gì khác

| Chủ đề | Camunda 7.24 | Fasti | Làm gì |
|---|---|---|---|
| Đường dẫn web app | `/camunda/app/...` | `/fasti/app/...`; `/camunda/...` chuyển hướng (301/308) | Proxy chỉ mở `/camunda` phải mở thêm `/fasti`, hoặc đặt `FASTI_WEBAPP_PATH=/camunda` |
| Trang mở đầu | Welcome | Web app mới (`/fasti/app/next/`) | `fasti.webapp.default-app: welcome` để như cũ (không áp dụng cho image, nơi ứng dụng cổ điển tắt) |
| **Image: Cockpit, Tasklist, Admin, Welcome cổ điển** (breaking) | phục vụ ở `/camunda/app/...` | **không phục vụ**: trang và bookmark (kể cả `/camunda/app/...`) chuyển tới mục tương ứng của giao diện mới (`#/cockpit`, `#/tasklist`, `#/admin`), file trả 404. Lý do: AngularJS 1.8.2 và Bootstrap 3 đã hết vòng đời | dùng giao diện mới; tạm thời `FASTI_CLASSIC_WEBAPPS=true` (có cảnh báo, sẽ bị gỡ) |
| **Tomcat, WildFly: ứng dụng cổ điển** (breaking) | phục vụ | **không phục vụ** (như image) | dùng giao diện mới; tạm thời `-Dfasti.webapp.classic.enabled=true` (`CATALINA_OPTS`/`JAVA_OPTS`), `FASTI_CLASSIC_WEBAPPS=true` hoặc context parameter trong `WEB-INF/web.xml` |
| Run zip, Spring Boot starter: ứng dụng cổ điển | có | vẫn có, nhưng **lỗi thời (deprecated)** | lên kế hoạch chuyển sang giao diện mới; tắt sớm bằng `fasti.webapp.classic.enabled: false` |
| Schema cũ | khởi động, lỗi về sau | dừng với `ENGINE-03111` | nâng cấp schema; tạm thời `FASTI_SCHEMA_VERSION_CHECK=warn` |
| Expression trong query/filter | mọi expression | allow-list (hàm thời gian, user hiện tại) | filter dùng bean/hàm riêng: khai báo trong `allowedExpressionFunctionsInQueries`, `allowedExpressionBeansInQueries`, `allowedExpressionPropertiesInQueries` của cấu hình engine |
| Image: tạo schema | `true` (Spring Boot) | `false` | `FASTI_DB_SCHEMA_UPDATE=true` cho cơ sở dữ liệu mới |
| Image: demo user, ví dụ invoice, filter "All tasks" | có (`default.yml`) | không | `FASTI_ADMIN_USER`, `--example` |
| Image: CORS | `*` | tắt | `FASTI_CORS_ALLOWED_ORIGINS=*` |
| Image: `/actuator/health` | không | có | — |
| Image: thư mục | `/camunda` | `/fasti` (đọc cả file gắn ở `/camunda/configuration`) | — |
| Image: `--swaggerui` | có | không | — |
| OAuth2/OIDC | claim `camunda-admin` thành admin | phải khai báo nhóm admin | `fasti.security.oidc.groups.admin` |
| OAuth2 `group-name-delimiter` | regex | chuỗi thường | đổi regex thành ký tự |
| OAuth2 logout | GET | POST cùng origin | sửa link logout |
| Tasklist mới: `refresh` vắng mặt | không tự làm mới | tự làm mới | đặt `refresh: false` nếu cần |
| Tên jar Run | `camunda-bpm-run-core.jar` | `fasti-bpm-run-core.jar` | sửa script/systemd riêng |

Bảo mật và lỗi đã vá so với 7.24.0 (phân quyền attachment/comment, setup lần đầu, …) có thể
làm một số lời gọi trước đây thành công nay trả 403; xem [README.md](../README.md) của kho mã.

### Quay lại Camunda 7.24

Vì Fasti không đổi schema, quay lại chỉ là: dừng Fasti, khởi động lại node Camunda 7.24 trên
cùng cơ sở dữ liệu. Mọi thứ làm trên Fasti (task đã hoàn thành, deployment mới, người dùng,
filter) hiện ra trên Camunda 7. Còn lại sau khi quay lại:

- dòng `fasti.prefs.*` trong `ACT_ID_INFO` (tùy chọn giao diện mới) — Camunda 7 bỏ qua;
- filter đã lưu bằng Tasklist mới có `refresh: true` nếu trước đó vắng mặt;
- process đã deploy có dùng tính năng chỉ Fasti có thì không chạy được trên Camunda 7.

### Chạy lẫn với node Camunda 7

Fasti và Camunda 7.24 chạy cùng lúc trên một cơ sở dữ liệu được (đã thử: cả hai thấy dữ liệu
của nhau, job chạy trên node nào cũng được). Điều kiện:

- **cùng mức lịch sử** (image dùng `auto`: lấy mức đang lưu), **cùng múi giờ JVM** (`TZ`), cùng
  cấu hình mật khẩu, cùng tiền tố bảng;
- **job executor:** mặc định Fasti (image, Run, Spring Boot) nhận job của mọi deployment (như Run).
  Riêng bản Tomcat và WildFly mặc định `jobExecutorDeploymentAware=true` (như Camunda 7): job
  (timer, async, retry) của deployment không đăng ký trên node đó, kể cả deployment từ Run hay
  qua REST trước lần khởi động lại, **không chạy**; khi tiếp quản cơ sở dữ liệu của Run, đặt
  `false` trong `conf/bpm-platform.xml` (Tomcat) hoặc `standalone.xml` (WildFly). Nếu node cũ là
  *process application* có lớp Java (`JavaDelegate`, listener, bean) mà Fasti không có, Fasti
  sẽ lấy job của chúng và làm hỏng (incident). Khi đó: `FASTI_JOB_EXECUTOR_ENABLED=false`
  trên Fasti, hoặc bật `deployment-aware` trên **mọi** node (job executor của node chỉ chạy
  job của deployment đã đăng ký trên node đó; deployment qua REST chỉ đăng ký trên node đã
  deploy, cho đến khi khởi động lại). REST đồng bộ (hoàn thành task, bắt đầu instance) vẫn
  chạy delegate ngay trên Fasti — đừng gọi các process đó qua Fasti;
- **load balancer:** sticky session cho web app; REST Basic auth không cần;
- web app cổ điển (trên node Camunda 7, hoặc Fasti với `FASTI_CLASSIC_WEBAPPS=true`) và web
  app mới dùng chung filter và người dùng.

### Phiên bản cũ hơn (7.17–7.23)

1. Sao lưu.
2. Chạy lần lượt các script `sql/upgrade/<db>_engine_<từ>_to_<đến>.sql` (trong bản phân phối:
   `configuration/sql/upgrade`; trong image: `/fasti/configuration/sql/upgrade`), từ phiên bản
   hiện tại đến 7.24, kể cả các script patch của minor hiện tại chưa chạy. Ví dụ
   7.22 → 7.24: `postgres_engine_7.22_to_7.23.sql`, rồi `postgres_engine_7.23_to_7.24.sql`.
3. Kiểm tra `ACT_GE_SCHEMA_LOG` có `7.24.0`, rồi khởi động Fasti.

Node Camunda cũ hơn 7.24 không được chạy cùng lúc trên schema đã nâng cấp, trừ khi theo quy
trình rolling update của Camunda (chỉ một minor, và node cũ không được dùng tính năng mới).

### Hiệu năng

Mặc định của image (pool 10 kết nối, job executor core 3/max 10) giống Camunda Run. Nếu hệ
Camunda 7 cũ đã chỉnh pool hoặc job executor, đặt lại các giá trị đó (`FASTI_DB_CONN_MAXACTIVE`,
`CAMUNDA_BPM_JOB_EXECUTION_*`); hướng dẫn và số đo trong `distro/docker/README.md`, mục "Tinh
chỉnh hiệu năng". Fasti không thêm chỉ mục nào vào schema.

### Giới hạn

- Chỉ hỗ trợ schema 7.24 (Community Edition). Schema của bản Enterprise (patch 7.24.x EE) chưa
  được thử.
- Đã thử thật với image (Camunda Run 7.24.0 tạo và nạp dữ liệu → Fasti tiếp quản → quay lại
  Run → chạy song song):

  | Cơ sở dữ liệu | Driver | Tiếp quản | Quay lại | Song song | Nâng cấp 7.23 → 7.24 |
  |---|---|---|---|---|---|
  | PostgreSQL 16 | có sẵn | đạt | đạt | đạt | chưa thử |
  | MariaDB 11.8 | MariaDB Connector/J (có sẵn) hoặc MySQL Connector/J | đạt | đạt | đạt | đạt |
  | MySQL 8.4 | có sẵn | đạt | — | đạt | đạt |
  | SQL Server 2022 (Developer) | có sẵn (`encrypt=false`) | đạt | đạt | đạt | chưa thử |
  | Oracle Database 23ai Free | `ojdbc11` qua `FASTI_EXTRA_LIB` | đạt | đạt | đạt | chưa thử |
  | DB2 | — | chưa thử | | | |

  Ngoài ra: hai node Fasti trên MariaDB (MariaDB Connector/J) hoàn thành song song cùng một
  task và lấy external task song song (fetchAndLock) không mất hay trùng dữ liệu; giao dịch
  thua trả lỗi khóa lạc quan. MySQL/MariaDB cần `transaction-isolation=READ-COMMITTED`.
  Trên MySQL, mỗi engine nên có database riêng: engine (cả Camunda Run lẫn Fasti) dò bảng ở
  mọi database mà tài khoản thấy được.
- Plugin/process application viết cho Camunda 7 cần build lại với artifact của Fasti nếu chạy
  bên trong Fasti; process chỉ dùng REST/external task không cần.
- Kiểm tra phiên bản schema chỉ đọc `ACT_GE_SCHEMA_LOG`, không so từng cột.

---

## English

### Summary

If you run Camunda 7 **7.24** (Run, the container image, the Spring Boot starter, Tomcat or
WildFly), Fasti runs on **that same database without changing its schema**: it uses the 7.24
schema unchanged and writes no tables, columns or schema versions of its own. You can replace
the old nodes, run Fasti next to them, and go back to Camunda 7.24 at any time.

Databases of 7.23 and older must first be upgraded to 7.24 with the SQL scripts (see
[Older versions](#older-versions-717723)). Fasti never upgrades a schema itself; it refuses to
start on an older schema and names the scripts to run.

### Steps (container image)

1. **Back up the database.** None of the steps below changes the schema, but a backup is
   mandatory before any change to a production system.
2. **Check the schema version:** `select VERSION_ from ACT_GE_SCHEMA_LOG` — the highest value
   must be `7.24.x`.
3. **Write down the current configuration** of the old node: `DB_*` variables, history level
   (`camunda.bpm.history-level`, `ACT_GE_PROPERTY.historyLevel`), REST authentication
   (`camunda.bpm.run.auth.enabled`), authorizations (`camunda.bpm.authorization.enabled`),
   plugins, LDAP/SSO, JVM time zone, password hashing (`passwordEncryptor`, `saltGenerator`),
   table prefix/schema.
4. **Build the image** as described in [distro/docker/README.md](../distro/docker/README.md#english).
5. **Start Fasti with the same `DB_*` variables** (and the same `TZ`). Unlike Camunda Run, the
   Fasti image **requires REST authentication and enforces authorizations by default**: database
   accounts keep working, but every REST client must send Basic credentials or a bearer token,
   and users outside `camunda-admin` need grants in `ACT_RU_AUTHORIZATION` (see "Secure by
   default" in [distro/docker/README.md](../distro/docker/README.md#english)).
   `FASTI_INSECURE_DEMO=true` is for demos only. Fasti logs
   `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
6. **Check:** sign in at `/fasti/` with an existing account; filters, tasks, instances and
   history are there; `GET /engine-rest/task` (with Basic credentials); `GET /actuator/health/readiness`.
7. **Move traffic** (load balancer, DNS) to Fasti; stop the old nodes or keep them running (see
   [Mixed operation](#mixed-operation-with-camunda-7-nodes)).

Spring Boot starter / Tomcat / WildFly: replace the `org.camunda.bpm:camunda-*` artifacts with
the matching `dev.fasti.platform:fasti-*` ones and keep `camunda.bpm.*`, `bpm-platform.xml` and
the `org.camunda.bpm.*` packages. Do not keep both engine jars on the classpath; Fasti refuses
to start then (for the limits of this check see "Startup check" in the
[README](../README.md#startup-check)).

> **Spring Boot starter:** Fasti runs on **Spring Boot 4.1** and needs **Java 17 or later**.
> Applications on the Camunda 7.24 starter (Spring Boot 3.5) must move to Spring Boot 4.1 and
> Java 17+ together with the artifact change. See the
> [README](../README.md#what-is-different-from-camunda-7240-ce) and the
> [CHANGELOG](../CHANGELOG.md#changed).

### Tomcat and WildFly distributions

> **Required when taking over the database of Camunda Run** (or of a server whose processes were
> deployed through the REST API): the Tomcat and WildFly distributions keep
> `jobExecutorDeploymentAware=true` like Camunda 7. Jobs (timers, async continuations, retries)
> of deployments that are not registered on the node **do not run, and no error is reported**.
> Before starting, set `<property name="jobExecutorDeploymentAware">false</property>` in
> `conf/bpm-platform.xml` (Tomcat) or in the engine properties of `standalone.xml` (WildFly).
> Keep `true` if other nodes run process applications with Java classes of their own (see
> [Mixed operation](#mixed-operation-with-camunda-7-nodes)).

Compared with the Tomcat/WildFly distributions of Camunda 7.24:

- **The REST API requires authentication** (breaking). `/engine-rest` has the `camunda-auth`
  filter (HTTP Basic) enabled: every REST client must send the user name and password of a
  user of the engine, otherwise it gets 401; only the list of engines (`/engine`) stays open.
  Configure credentials in REST clients, external task workers and scripts that call the REST
  API anonymously before you switch. OpenID Connect bearer tokens (`fasti-oidc.properties`,
  `fasti.security.oidc.rest.bearer-enabled=true`) work alongside: requests with a valid token
  need no password. To switch authentication off (**not recommended**), remove the
  `camunda-auth` filter and its `filter-mapping` from `WEB-INF/web.xml` of the `engine-rest` WAR.
- **No default users** (breaking). The invoice example is no longer deployed, so it does not
  create `demo/demo` (administrator), `john`, `mary` and `peter`; its WAR is
  `examples/camunda-invoice.war`, to be copied to `webapps/` (Tomcat) or
  `standalone/deployments/` (WildFly) for trying it out. On an empty database open `/fasti/`
  and create the first administrator on the setup page; on a Camunda 7 database use the
  existing accounts.
- **The classic Cockpit, Tasklist, Admin and Welcome are not served** (breaking), as in the
  image: their pages and bookmarks lead to the matching section of the new UI, their files
  answer 404. To bring them back for a while (deprecated, to be removed):
  `-Dfasti.webapp.classic.enabled=true` in `CATALINA_OPTS` (Tomcat) or `JAVA_OPTS` (WildFly), the
  environment variable `FASTI_CLASSIC_WEBAPPS=true`, or the context parameter
  `fasti.webapp.classic.enabled` in `WEB-INF/web.xml` of the web application.

### What is identical

- The schema and the rows of `ACT_GE_PROPERTY`/`ACT_GE_SCHEMA_LOG` (Fasti adds no version row),
  the installation id, id generation (`StrongUuidGenerator` in Run/Spring Boot, the engine's
  default `DbIdGenerator`).
- Passwords: SHA-512 with a 16-byte salt like Camunda 7; existing users can sign in, and
  passwords changed on Fasti work on Camunda 7.
- The REST API at `/engine-rest` (unchanged behaviour plus additive endpoints), external tasks
  (`fetchAndLock`, long polling), the Java and JS clients, `JSESSIONID`, `XSRF-TOKEN`/`X-XSRF-TOKEN`, `camunda.bpm.*`
  properties, the `camunda:` BPMN namespace.
- Filters of the classic Tasklist (`color`, `description`, `priority`, `refresh`, `variables`,
  `showUndefinedVariable`) are read and written in the same format by the new one.

### What differs

- Web applications live at `/fasti/app/...`; `/camunda/...` redirects there (301/308). Proxies
  exposing only `/camunda` must expose `/fasti`, or set `FASTI_WEBAPP_PATH=/camunda`. The root
  opens the new web application (`fasti.webapp.default-app: welcome` restores Welcome, except
  in the image, where the classic applications are off).
- **Breaking, container image:** the classic Cockpit, Tasklist, Admin and Welcome are not
  served, because AngularJS 1.8.2 and Bootstrap 3 are end of life. Their pages and bookmarks,
  also below `/camunda/app/`, redirect to the matching section of the new web UI (`#/cockpit`,
  `#/tasklist`, `#/admin`); their files answer 404. Move users to the new web UI;
  `FASTI_CLASSIC_WEBAPPS=true` serves the classic applications again for a transition, with a
  warning. Tomcat and WildFly do not serve them either (see
  [Tomcat and WildFly distributions](#tomcat-and-wildfly-distributions)). The Run zip and the
  Spring Boot starter still serve them, but they are deprecated and will be removed in a later
  release (`fasti.webapp.classic.enabled: false` turns them off early).
- An older schema stops the start with `ENGINE-03111` instead of failing later
  (`FASTI_SCHEMA_VERSION_CHECK=warn` to start anyway).
- Expressions in queries and filters are limited to an allow-list (date functions, current
  user); filters using custom beans or functions need them declared in the engine configuration
  properties `allowedExpressionFunctionsInQueries`, `allowedExpressionBeansInQueries` and
  `allowedExpressionPropertiesInQueries`.
- Image defaults: schema update `false` (`FASTI_DB_SCHEMA_UPDATE=true` for a new database); no
  demo user, invoice example or "All tasks" filter; CORS off (`FASTI_CORS_ALLOWED_ORIGINS=*`
  restores it); `/actuator/health` added; files below `/fasti` (files mounted at
  `/camunda/configuration` are read too); no `--swaggerui`.
- OAuth2/OIDC: `camunda-admin` in a group claim no longer grants admin by itself
  (`fasti.security.oidc.groups.admin`); `group-name-delimiter` is literal text, not a regex;
  logout needs a same-origin POST.
- New Tasklist: a filter without `refresh` refreshes automatically.
- The Run jar is `fasti-bpm-run-core.jar`.
- Security fixes over 7.24.0 (attachment/comment authorization, initial setup, ...) can turn
  some formerly successful calls into 403; see the [README](../README.md#english).

### Rollback to Camunda 7.24

Stop Fasti and start the Camunda 7.24 nodes on the same database again. Everything done on
Fasti is visible there. What remains: `fasti.prefs.*` rows in `ACT_ID_INFO` (preferences of the
new web UI, ignored by Camunda 7) and `refresh: true` on filters saved by the new Tasklist where
it was absent. Deployed processes that use Fasti-only features do not run on Camunda 7.

### Mixed operation with Camunda 7 nodes

Fasti and Camunda 7.24 can run on one database at the same time (tested: both see each other's
data and jobs run on either node). Requirements:

- the **same history level** (the image takes the stored one), the **same JVM time zone**, the
  same password settings and table prefix;
- **job executor:** by default Fasti (image, Run, Spring Boot) takes jobs of every deployment, like Run.
  The Tomcat and WildFly distributions default to `jobExecutorDeploymentAware=true` (as Camunda
  7 does): jobs (timers, async continuations, retries) of deployments not registered on that
  node, including deployments made by Run or through REST before the last restart, **do not
  run**; when taking over the database of Run, set it to `false` in `conf/bpm-platform.xml`
  (Tomcat) or `standalone.xml` (WildFly). If old nodes are
  process applications with classes Fasti does not have (`JavaDelegate`, listeners, beans),
  Fasti would pick up their jobs and fail them (incidents). Then set
  `FASTI_JOB_EXECUTOR_ENABLED=false` on Fasti, or make **every** node deployment-aware (a node's
  job executor only runs jobs of deployments registered on that node; a deployment made through
  REST is registered only on the node that received it, until a restart). Synchronous REST calls (completing a task,
  starting an instance) still run delegates on the node that receives them — do not route such
  processes to Fasti;
- **load balancer:** sticky sessions for the web applications; Basic-auth REST calls need none;
- the classic web applications (on Camunda 7 nodes, or Fasti with
  `FASTI_CLASSIC_WEBAPPS=true`) and the new one share filters and users.

### Older versions (7.17–7.23)

Back up; run `sql/upgrade/<db>_engine_<from>_to_<to>.sql` in order up to 7.24, including
outstanding patch scripts of your current minor (shipped in `configuration/sql/upgrade`, in the
image `/fasti/configuration/sql/upgrade`); check that `ACT_GE_SCHEMA_LOG` contains `7.24.0`;
start Fasti. Example 7.22 → 7.24: `postgres_engine_7.22_to_7.23.sql`, then
`postgres_engine_7.23_to_7.24.sql`. Camunda nodes older than 7.24 must not keep running on the
upgraded schema except within Camunda's rolling-update rules (one minor version only, and the
old nodes must not use new features).

### Performance

The image defaults (10 pooled connections, job executor core 3/max 10) match Camunda Run. If the
former Camunda 7 installation tuned its pool or job executor, carry those values over
(`FASTI_DB_CONN_MAXACTIVE`, `CAMUNDA_BPM_JOB_EXECUTION_*`); guidance and measurements are in
`distro/docker/README.md`, "Performance tuning". Fasti adds no index to the schema.

### Limits

- Only the 7.24 Community Edition schema is supported; Enterprise patch schemas are untested.
- Tested for real with the image (Camunda Run 7.24.0 creates and seeds the database → Fasti
  takes over → back to Run → both at once):

  | Database | Driver | Takeover | Rollback | Mixed | Upgrade 7.23 → 7.24 |
  |---|---|---|---|---|---|
  | PostgreSQL 16 | bundled | pass | pass | pass | not tested |
  | MariaDB 11.8 | MariaDB Connector/J (bundled) or MySQL Connector/J | pass | pass | pass | pass |
  | MySQL 8.4 | bundled | pass | — | pass | pass |
  | SQL Server 2022 (Developer) | bundled (`encrypt=false`) | pass | pass | pass | not tested |
  | Oracle Database 23ai Free | `ojdbc11` via `FASTI_EXTRA_LIB` | pass | pass | pass | not tested |
  | DB2 | — | not tested | | | |

  Also: two Fasti nodes on MariaDB (MariaDB Connector/J) completing the same tasks and fetching
  external tasks in parallel lost or duplicated nothing; the losing transaction gets an
  optimistic locking error. MySQL/MariaDB need `transaction-isolation=READ-COMMITTED`. On MySQL
  give each engine its own database: the engine (Camunda Run and Fasti alike) looks for tables
  in every database the account can see.
- Plugins and process applications built for Camunda 7 must be rebuilt against the Fasti
  artifacts to run inside Fasti; REST/external-task clients need nothing.
- The schema check reads `ACT_GE_SCHEMA_LOG` only; it does not compare columns.
