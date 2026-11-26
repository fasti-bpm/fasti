[English](migrate-from-camunda-7.md) | Tiếng Việt | [Deutsch](migrate-from-camunda-7.de.md) | [Español](migrate-from-camunda-7.es.md) | [Français](migrate-from-camunda-7.fr.md) | [हिन्दी](migrate-from-camunda-7.hi.md) | [Bahasa Indonesia](migrate-from-camunda-7.id.md) | [日本語](migrate-from-camunda-7.ja.md) | [한국어](migrate-from-camunda-7.ko.md) | [Português (Brasil)](migrate-from-camunda-7.pt-BR.md) | [Русский](migrate-from-camunda-7.ru.md) | [ไทย](migrate-from-camunda-7.th.md) | [简体中文](migrate-from-camunda-7.zh-CN.md)

# Chuyển từ Camunda 7 sang Fasti BPM

Fasti BPM được xây dựng trên Camunda 7 Community Edition 7.24.0 và tương thích với cơ sở dữ
liệu của Camunda 7. Fasti BPM là dự án độc lập, không liên kết với Camunda Services GmbH.

<a id="summary"></a>

## Tóm tắt

Nếu hệ của bạn chạy Camunda 7 **7.24** (Run, image container, Spring Boot starter, Tomcat hay
WildFly), Fasti chạy được trên **chính cơ sở dữ liệu đó, không đổi gì trong schema**: Fasti
dùng nguyên schema của 7.24 và không ghi thêm bảng, cột hay phiên bản schema nào. Bạn có thể
thay hẳn các node cũ, chạy Fasti song song với chúng, và quay lại Camunda 7.24 (xem
[Quay lại](#rollback-to-camunda-724) và các cơ sở dữ liệu đã kiểm thử ở [Giới hạn](#limits)).

Cơ sở dữ liệu của 7.23 trở về trước phải được nâng cấp lên 7.24 bằng script SQL trước (xem
[Phiên bản cũ hơn](#phiên-bản-cũ-hơn-717723)). Fasti không tự nâng cấp schema, và từ chối
khởi động trên schema cũ với thông báo nêu rõ script cần chạy.

<a id="steps-container-image"></a>

## Các bước (image container)

1. **Sao lưu cơ sở dữ liệu.** Không bước nào dưới đây sửa schema, nhưng sao lưu là bắt buộc
   trước mọi thay đổi trên hệ production.
2. **Kiểm tra phiên bản schema:**
   `select VERSION_ from ACT_GE_SCHEMA_LOG` — giá trị lớn nhất phải là `7.24.x`.
3. **Ghi lại cấu hình đang dùng** của node cũ: biến `DB_*`, mức lịch sử
   (`camunda.bpm.history-level`, `ACT_GE_PROPERTY.historyLevel`), xác thực REST
   (`camunda.bpm.run.auth.enabled`), phân quyền (`camunda.bpm.authorization.enabled`), plugin,
   LDAP/SSO, múi giờ JVM, cấu hình mật khẩu (`passwordEncryptor`, `saltGenerator`), tiền tố
   bảng/schema.
4. **Kéo image** `fastidev/fasti-bpm:1.3.1` (hoặc build từ mã nguồn theo
   [distro/docker/README.vi.md](../distro/docker/README.vi.md)).
5. **Chạy Fasti với cùng biến `DB_*`** (và `TZ` giống node cũ). Khác Camunda Run, image Fasti
   **bật sẵn xác thực REST và phân quyền**: tài khoản trong cơ sở dữ liệu đăng nhập được,
   nhưng mọi client REST phải gửi Basic auth hoặc bearer token, và user không thuộc
   `camunda-admin` cần có quyền trong `ACT_RU_AUTHORIZATION` (xem "Bảo mật mặc định" trong
   [distro/docker/README.vi.md](../distro/docker/README.vi.md)). `FASTI_INSECURE_DEMO=true` chỉ để demo. Fasti kiểm tra phiên bản schema và in
   `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
6. **Kiểm tra:** đăng nhập `/fasti/` bằng tài khoản có sẵn; xem filter, task, instance, lịch
   sử; `GET /engine-rest/task` (kèm Basic auth); `GET /actuator/health/readiness`.
7. **Chuyển lưu lượng** (load balancer, DNS) sang Fasti; dừng node cũ, hoặc để chạy song song
   (xem [Chạy lẫn](#chạy-lẫn-với-node-camunda-7)).

Spring Boot starter / Tomcat / WildFly: thay các artifact `org.camunda.bpm:camunda-*` bằng
`dev.fasti.platform:fasti-*` cùng chức năng, giữ nguyên cấu hình `camunda.bpm.*`, `bpm-platform.xml`
và package `org.camunda.bpm.*`. Không để jar engine của Camunda và của Fasti cùng nằm trên
classpath: Fasti dừng khởi động khi thấy cả hai (giới hạn của phép kiểm tra này: xem mục
"Kiểm tra khi khởi động" trong [embedding.vi.md](embedding.vi.md#startup-check)).

> **Spring Boot starter:** Fasti chạy trên **Spring Boot 4.1** và cần **Java 17 trở lên**. Ứng
> dụng đang dùng starter của Camunda 7.24 (Spring Boot 3.5) phải nâng lên Spring Boot 4.1 và
> Java 17+ cùng lúc với việc đổi artifact. Xem [embedding.vi.md](embedding.vi.md#what-is-different-from-camunda-7240-ce)
> và [CHANGELOG](../CHANGELOG.md#changed).

<a id="tomcat-and-wildfly-distributions"></a>

## Bản phân phối Tomcat và WildFly

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

<a id="what-is-identical"></a>

## Cái gì giống hệt

- Schema cơ sở dữ liệu và giá trị `ACT_GE_PROPERTY`/`ACT_GE_SCHEMA_LOG` (Fasti không thêm dòng
  phiên bản), installation id, sinh id (`StrongUuidGenerator` trong Run/Spring Boot,
  `DbIdGenerator` mặc định của engine).
- Mật khẩu: SHA-512 + salt 16 byte như Camunda 7 — người dùng cũ đăng nhập được, mật khẩu đổi
  trên Fasti dùng được trên Camunda 7.
- REST API `/engine-rest`: cùng endpoint, tham số và payload, chỉ thêm endpoint mới (xác thực và
  một số mã trạng thái khác, xem [Cái gì khác](#what-differs)), external task
  (`fetchAndLock`, long polling), client Java/JS; CSRF `XSRF-TOKEN` / `X-XSRF-TOKEN`; thuộc tính
  `camunda.bpm.*`; namespace BPMN `camunda:`. Cookie phiên là `JSESSIONID` trong bản Tomcat và
  WildFly; Run và container image đặt tên `FASTI_SESSION` từ 1.3.2
  (`server.servlet.session.cookie.name`) để không đụng Camunda 7 Run trên cùng máy. Script tìm
  cookie theo tên cần dùng tên mới.
- Filter của Tasklist cổ điển (`color`, `description`, `priority`, `refresh`, `variables`,
  `showUndefinedVariable`) được Tasklist mới đọc và lưu cùng định dạng.

<a id="what-differs"></a>

## Cái gì khác

| Chủ đề | Camunda 7.24 | Fasti | Làm gì |
|---|---|---|---|
| Đường dẫn web app | `/camunda/app/...` | `/fasti/app/...`; `/camunda/...` chuyển hướng (301/308) | Proxy chỉ mở `/camunda` phải mở thêm `/fasti`, hoặc đặt `FASTI_WEBAPP_PATH=/camunda` |
| Trang mở đầu | Welcome | Web app mới (`/fasti/app/next/`) | `fasti.webapp.default-app: welcome` để như cũ (không áp dụng cho image, nơi ứng dụng cổ điển tắt) |
| **Image: Cockpit, Tasklist, Admin, Welcome cổ điển** (breaking) | phục vụ ở `/camunda/app/...` | **không phục vụ**: trang và bookmark (kể cả `/camunda/app/...`) chuyển tới mục tương ứng của giao diện mới (`#/cockpit`, `#/tasklist`, `#/admin`), file trả 404. Lý do: AngularJS 1.8.2 và Bootstrap 3 đã hết vòng đời | dùng giao diện mới; tạm thời `FASTI_CLASSIC_WEBAPPS=true` (có cảnh báo, sẽ bị gỡ) |
| **Tomcat, WildFly: ứng dụng cổ điển** (breaking) | phục vụ | **không phục vụ** (như image) | dùng giao diện mới; tạm thời `-Dfasti.webapp.classic.enabled=true` (`CATALINA_OPTS`/`JAVA_OPTS`), `FASTI_CLASSIC_WEBAPPS=true` hoặc context parameter trong `WEB-INF/web.xml` |
| Run zip, Spring Boot starter: ứng dụng cổ điển | có | vẫn có, nhưng **lỗi thời (deprecated)** | lên kế hoạch chuyển sang giao diện mới; tắt sớm bằng `fasti.webapp.classic.enabled: false` |
| Schema cũ | khởi động, lỗi về sau | dừng với `ENGINE-03111` | nâng cấp schema; tạm thời `FASTI_SCHEMA_VERSION_CHECK=warn` |
| Expression trong query/filter | mọi expression | allow-list (hàm thời gian, user hiện tại) | filter dùng bean/hàm riêng: khai báo trong `allowedExpressionFunctionsInQueries`, `allowedExpressionBeansInQueries`, `allowedExpressionPropertiesInQueries` của cấu hình engine |
| REST API: xác thực | Run: tắt | image, Tomcat, WildFly: mọi request, trừ danh sách engine (`/engine`), cần thông tin đăng nhập Basic hoặc bearer token | cấu hình thông tin đăng nhập cho REST client và external task worker (không phải sửa code) |
| REST API: mã trạng thái | — | mọi bản phân phối: expression ngoài allow-list bị từ chối với `400`; đọc hoặc sửa attachment, comment của task khi không có quyền nhận `403` (khi bật phân quyền); một số request sai trước đây trả `500` nay trả `400` (tài nguyên deploy không parse được, biến ở định dạng Java serialization bị cấm, tham số query sai kiểu, mật khẩu vi phạm chính sách mật khẩu) | xem [Khác gì so với Camunda 7](api/README.vi.md#khác-gì-so-với-camunda-7) |
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
làm một số lời gọi trước đây thành công nay trả 403; xem các mục *Security* trong
[CHANGELOG](../CHANGELOG.md) và <https://fasti.dev/security>.

<a id="rollback-to-camunda-724"></a>

## Quay lại Camunda 7.24

Vì Fasti không đổi schema, quay lại chỉ là: dừng Fasti, khởi động lại node Camunda 7.24 trên
cùng cơ sở dữ liệu. Mọi thứ làm trên Fasti (task đã hoàn thành, deployment mới, người dùng,
filter) hiện ra trên Camunda 7. Còn lại sau khi quay lại:

- dòng `fasti.prefs.*` trong `ACT_ID_INFO` (tùy chọn giao diện mới) — Camunda 7 bỏ qua;
- filter đã lưu bằng Tasklist mới có `refresh: true` nếu trước đó vắng mặt;
- process đã deploy có dùng tính năng chỉ Fasti có thì không chạy được trên Camunda 7.

<a id="mixed-operation-with-camunda-7-nodes"></a>

## Chạy lẫn với node Camunda 7

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

<a id="older-versions-717723"></a>

## Phiên bản cũ hơn (7.17–7.23)

1. Sao lưu.
2. Chạy lần lượt các script `sql/upgrade/<db>_engine_<từ>_to_<đến>.sql` (trong bản phân phối:
   `configuration/sql/upgrade`; trong image: `/fasti/configuration/sql/upgrade`), từ phiên bản
   hiện tại đến 7.24, kể cả các script patch của minor hiện tại chưa chạy. Ví dụ
   7.22 → 7.24: `postgres_engine_7.22_to_7.23.sql`, rồi `postgres_engine_7.23_to_7.24.sql`.
3. Kiểm tra `ACT_GE_SCHEMA_LOG` có `7.24.0`, rồi khởi động Fasti.

Node Camunda cũ hơn 7.24 không được chạy cùng lúc trên schema đã nâng cấp, trừ khi theo quy
trình rolling update của Camunda (chỉ một minor, và node cũ không được dùng tính năng mới).

<a id="performance"></a>

## Hiệu năng

Image không còn dùng mặc định của Camunda Run cho job executor và pool kết nối (đến Fasti 1.1 thì
giống): 10 luồng job, hàng đợi 10 và 10 job mỗi lần lấy (Camunda Run: core 3, max 10, hàng đợi 3, 3
job mỗi lần lấy), pool tối đa 20 kết nối, giữ mở 10 kết nối khi rảnh (Camunda Run: 10). Đặt giới hạn
kết nối của cơ sở dữ liệu theo tổng pool của mọi node, kể cả node Camunda 7 ([tinh chỉnh khi
chạy](tuning-runtime.vi.md)). Nếu hệ Camunda 7 cũ đã chỉnh pool hoặc job executor, đặt lại các giá
trị đó (`FASTI_DB_CONN_MAXACTIVE`, `FASTI_DB_CONN_MINIDLE`, `CAMUNDA_BPM_JOB_EXECUTION_*`); hướng
dẫn thêm trong `distro/docker/README.vi.md`, mục "Tinh chỉnh hiệu năng". Fasti không bao giờ tự đổi
chỉ mục của schema; với cơ sở dữ liệu lớn, Fasti kèm các chỉ mục tùy chọn dưới dạng script do quản
trị viên chạy (`/fasti/configuration/sql/optional/<database>_fasti_perf_indexes_create.sql`, xem
[performance.vi.md](performance.vi.md)), an toàn khi chạy cạnh node Camunda 7.24 và gỡ được bằng
`_drop.sql` tương ứng.

<a id="limits"></a>

## Giới hạn

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
  bên trong Fasti. REST client và external task client không phải sửa code; với image container,
  Tomcat hoặc WildFly, chúng phải được cấu hình để gửi thông tin đăng nhập Basic hoặc bearer token.
- Kiểm tra phiên bản schema chỉ đọc `ACT_GE_SCHEMA_LOG`, không so từng cột.

