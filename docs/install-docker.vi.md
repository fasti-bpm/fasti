[English](install-docker.md) | Tiếng Việt | [Deutsch](install-docker.de.md) | [Español](install-docker.es.md) | [Français](install-docker.fr.md) | [हिन्दी](install-docker.hi.md) | [Bahasa Indonesia](install-docker.id.md) | [日本語](install-docker.ja.md) | [한국어](install-docker.ko.md) | [Português (Brasil)](install-docker.pt-BR.md) | [Русский](install-docker.ru.md) | [ไทย](install-docker.th.md) | [简体中文](install-docker.zh-CN.md)

# Cài đặt Fasti BPM bằng Docker

Hướng dẫn từng bước: thử nhanh với H2, chạy production với PostgreSQL (`docker run` hoặc
`docker compose`), nối vào cơ sở dữ liệu Camunda 7 có sẵn, chạy song song, health check, nâng cấp,
quay lại, xử lý sự cố và giám sát (log JSON, metrics Prometheus, tracing; có từ 1.1.0). Tham chiếu đầy đủ của image: [distro/docker/README.vi.md](../distro/docker/README.vi.md).
Fasti BPM được xây dựng trên Camunda 7 CE 7.24.0 và là dự án độc lập, không liên kết với Camunda
Services GmbH.

<a id="1-requirements"></a>

## 1. Yêu cầu

- Docker Engine có Docker Compose v2 (`docker compose`).
- Với tải thật: ít nhất 2 CPU và 2 GB bộ nhớ cho container Fasti (dưới mức đó JVM tự chọn Serial GC).
- Cơ sở dữ liệu production: PostgreSQL, MariaDB, MySQL, SQL Server, Oracle hoặc Db2 12.1 (xem
  [bảng đã thử](migrate-from-camunda-7.vi.md#giới-hạn); Db2 cần ba biến registry). H2 chỉ để thử.

<a id="2-pull-the-image"></a>

## 2. Tải image

<!-- update with the Docker Hub image name at release -->
Image cho amd64 và arm64, có ở hai nơi:

```bash
docker pull fastidev/fasti-bpm:1.3.2              # Docker Hub
docker pull ghcr.io/fasti-bpm/fasti-bpm:1.3.2     # GitHub Container Registry
```

Tag: `1.3.2` (đúng một bản), `1.3` (bản vá mới nhất của 1.3) và `latest` (bản phát hành có số
phiên bản cao nhất: bản bảo trì của một dòng cũ, ví dụ 1.2.4 ra sau 1.3.0, không đổi nó, và bản
thử nghiệm như `-rc` không bao giờ được gắn). Image được ký bằng cosign (keyless, GitHub OIDC) và kèm SBOM và provenance.
Từ 1.0.1, chữ ký dùng định dạng Sigstore bundle của cosign 3; image 1.0.0 mang chữ ký theo định
dạng cũ của cosign 2.

Cũng có thể build image từ mã nguồn (cần JDK 17+ và mạng tới Maven Central,
nodejs.org, npm registry) rồi dùng tên `fasti-bpm:local` thay cho `fastidev/fasti-bpm:1.3.2` ở mọi
lệnh bên dưới:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="3-quick-trial-with-h2"></a>

## 3. Thử nhanh với H2

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='đổi-mật-khẩu-này' \
  -v fasti-data:/fasti/data \
  fastidev/fasti-bpm:1.3.2
```

- Mở <http://localhost:8080/fasti/> và đăng nhập bằng `admin`.
- Không đặt `DB_URL` thì image dùng H2 trong `/fasti/data`; volume `fasti-data` giữ dữ liệu khi
  tạo lại container. Chỉ để thử, không dùng cho dữ liệu thật.
- Thêm `--webapps --rest --example` ở cuối lệnh để có thêm quy trình mẫu (invoice). Chỉ ghi
  `--example` thì web UI và REST API không được bật.
- REST API ở `http://localhost:8080/engine-rest` và cần Basic auth; xem
  [hướng dẫn REST API](api/README.vi.md).

<a id="4-production-with-postgresql-docker-run"></a>

## 4. Production với PostgreSQL: `docker run`

Tạo hai tệp bí mật (mật khẩu cơ sở dữ liệu và mật khẩu admin). Với `--production`, engine áp chính
sách mật khẩu: ít nhất 10 ký tự, có chữ hoa, chữ thường, chữ số và ký tự đặc biệt.

```bash
mkdir -p secrets
openssl rand -hex 24 > secrets/db-password.txt
echo "Fasti-1-$(openssl rand -hex 12)" > secrets/admin-password.txt
chmod 0700 secrets           # người dùng khác trên máy chủ không vào được thư mục
chmod 0644 secrets/*.txt     # container đọc tệp bằng uid 999 (PostgreSQL) và 1000 (Fasti)
```

```bash
docker network create fasti-net

docker run -d --name fasti-db --network fasti-net \
  -e POSTGRES_DB=fasti -e POSTGRES_USER=fasti \
  -e POSTGRES_PASSWORD_FILE=/run/secrets/db-password \
  -v "$PWD/secrets/db-password.txt:/run/secrets/db-password:ro" \
  -v fasti-pgdata:/var/lib/postgresql/data \
  postgres:16

docker run -d --name fasti --network fasti-net -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://fasti-db:5432/fasti \
  -e DB_USERNAME=fasti -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v "$PWD/secrets/db-password.txt:/run/secrets/db-password:ro" \
  -e WAIT_FOR=fasti-db:5432 -e WAIT_FOR_TIMEOUT=60 \
  -e FASTI_DB_SCHEMA_UPDATE=true \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD_FILE=/run/secrets/admin-password \
  -v "$PWD/secrets/admin-password.txt:/run/secrets/admin-password:ro" \
  -e TZ=UTC \
  --memory 2g --stop-timeout 60 \
  fastidev/fasti-bpm:1.3.2 --production
```

- `FASTI_DB_SCHEMA_UPDATE=true` chỉ tạo các bảng còn thiếu của một cơ sở dữ liệu mới; Fasti không
  bao giờ nâng cấp schema đã có. Có thể giữ biến này ở các lần chạy sau.
- `--production` bật cookie `Secure`, từ chối H2 ngầm định và `DEBUG=true`. Đặt TLS ở reverse
  proxy phía trước; trình duyệt chỉ gửi cookie `Secure` qua HTTPS (hoặc tới `localhost`).
- `WAIT_FOR` chờ cơ sở dữ liệu nhận kết nối trước khi khởi động.
- Mật khẩu admin chỉ được đặt khi admin chưa tồn tại; đổi mật khẩu sau đó trong mục Quản trị.

<a id="5-production-with-postgresql-docker-compose"></a>

## 5. Production với PostgreSQL: `docker compose`

Tệp mẫu: [distro/docker/examples/docker-compose.postgres.yml](../distro/docker/examples/docker-compose.postgres.yml)
(PostgreSQL 16, bí mật bằng `secrets`, health check, `--production`, giới hạn bộ nhớ 2 GB).

```bash
cd distro/docker/examples          # hoặc chép tệp sang thư mục riêng
mkdir -p secrets
openssl rand -hex 24 > secrets/db-password.txt
echo "Fasti-1-$(openssl rand -hex 12)" > secrets/admin-password.txt
chmod 0700 secrets
chmod 0644 secrets/*.txt
docker compose -f docker-compose.postgres.yml up -d
docker compose -f docker-compose.postgres.yml ps      # cả hai dịch vụ "healthy"
```

Biến có thể đặt khi chạy: `FASTI_IMAGE` (mặc định `fastidev/fasti-bpm:1.3.2`, ví dụ
`FASTI_IMAGE=fasti-bpm:local`), `FASTI_HOST_PORT` (mặc định `8080`), `TZ` (mặc định `UTC`).

<a id="6-connecting-to-an-existing-camunda-7-database"></a>

## 6. Nối vào cơ sở dữ liệu Camunda 7 có sẵn

Fasti chạy trên schema 7.24 mà không đổi gì. Trước khi bắt đầu:

1. **Sao lưu** cơ sở dữ liệu.
2. Kiểm tra phiên bản schema: `select VERSION_ from ACT_GE_SCHEMA_LOG` — giá trị lớn nhất phải là
   `7.24.x`. Schema 7.17–7.23: chạy trước các script `sql/upgrade` (có trong image ở
   `/fasti/configuration/sql/upgrade`), xem [hướng dẫn chuyển đổi](migrate-from-camunda-7.vi.md#phiên-bản-cũ-hơn-717723).
3. Kiểm tra phân quyền, vì Fasti bật phân quyền: `select * from ACT_ID_MEMBERSHIP where
   GROUP_ID_='camunda-admin'` và `select count(*) from ACT_RU_AUTHORIZATION`. Nếu hệ cũ chạy với
   phân quyền tắt, cấp quyền cho người dùng thường trước khi chuyển lưu lượng.
4. Ghi lại `TZ`, mức lịch sử, tiền tố bảng/schema và cấu hình mật khẩu của node cũ.

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /đường/dẫn/db-password:/run/secrets/db-password:ro \
  -e WAIT_FOR=db.example.com:5432 \
  -e TZ=UTC \
  fastidev/fasti-bpm:1.3.2 --production
```

- Cổng chỉ mở trên máy này; truy cập từ xa hãy đi qua reverse proxy có HTTPS.
- **Không** đặt `FASTI_DB_SCHEMA_UPDATE` và `FASTI_ADMIN_*`: bảng và tài khoản đã có. Log khi khởi
  động phải có `ENGINE-03115 Database schema version 7.24.0 matches this engine.`
- Mức lịch sử mặc định là `auto`: lấy mức đang lưu trong cơ sở dữ liệu.
- Mọi client REST giờ phải gửi Basic auth hoặc bearer token.
- Driver: PostgreSQL, MySQL, MariaDB, SQL Server, H2 có sẵn. **Oracle** và **DB2** phải tự thêm
  (`ojdbc11.jar`, `jcc.jar`) bằng `-v /thư/mục/driver:/drivers:ro -e FASTI_EXTRA_LIB=/drivers`.
  SQL Server: driver 13.x mã hóa kết nối và kiểm tra chứng chỉ theo mặc định; máy chủ cần chứng
  chỉ hợp lệ, đúng tên máy trong URL. `trustServerCertificate=true` (bỏ kiểm tra chứng chỉ) hoặc
  `encrypt=false` (không mã hóa) chỉ nên dùng khi thử trên máy cục bộ. MySQL/MariaDB cần `transaction-isolation=READ-COMMITTED`.

<a id="7-side-by-side-with-camunda-7-nodes"></a>

## 7. Chạy song song với node Camunda 7

Fasti và Camunda 7.24 chạy cùng lúc trên một cơ sở dữ liệu được (đã thử: cả hai thấy dữ liệu của
nhau). Điều kiện:

- cùng mức lịch sử, cùng `TZ`, cùng cấu hình mật khẩu, cùng tiền tố bảng;
- nếu node cũ là process application có lớp Java (`JavaDelegate`, listener) mà Fasti không có:
  `-e FASTI_JOB_EXECUTOR_ENABLED=false` trên Fasti, hoặc bật deployment-aware trên **mọi** node
  (`FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE=true` cho Fasti). Điều này chỉ bảo vệ job bất đồng bộ:
  bắt đầu instance hay hoàn thành task qua Fasti vẫn chạy delegate ngay trên Fasti, nên hãy gửi
  các yêu cầu đó tới node có đủ lớp Java;
- load balancer: sticky session cho web app; REST với Basic auth không cần;
- trên cùng một máy: từ 1.3.2 cookie phiên là `FASTI_SESSION`, không còn là `JSESSIONID`. Trình
  duyệt gửi cookie cho mọi cổng của một máy, nên với `JSESSIONID` và `Path=/` (Camunda 7 Run và
  Fasti đến 1.3.1), mở ứng dụng này làm người dùng bị đăng xuất khỏi ứng dụng kia. Nếu đổi tên
  (`SERVER_SERVLET_SESSION_COOKIE_NAME`), hãy giữ một tên riêng.

Chi tiết: [Chạy lẫn với node Camunda 7](migrate-from-camunda-7.vi.md#chạy-lẫn-với-node-camunda-7).

<a id="8-common-environment-variables"></a>

## 8. Biến môi trường hay dùng

| Biến | Ý nghĩa |
|---|---|
| `DB_URL`, `DB_DRIVER`, `DB_USERNAME`, `DB_PASSWORD` (`_FILE`) | kết nối cơ sở dữ liệu (tên giống image Camunda 7 Run cũ; bí danh `FASTI_DB_*`) |
| `WAIT_FOR`, `WAIT_FOR_TIMEOUT` | chờ `host:port` trước khi khởi động |
| `TZ` | múi giờ JVM, phải giống mọi node |
| `FASTI_DB_SCHEMA_UPDATE` | `true`: tạo bảng thiếu cho cơ sở dữ liệu mới |
| `FASTI_ADMIN_USER`, `FASTI_ADMIN_PASSWORD` (`_FILE`) | tạo admin nếu chưa có |
| `FASTI_JOB_EXECUTOR_ENABLED`, `FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE` | job executor của node |
| `FASTI_EXTRA_LIB` | thư mục jar thêm (driver Oracle, DB2) |
| `FASTI_DB_CONN_MAXACTIVE` | kích thước tối đa của pool kết nối (mặc định 20; khi rảnh giữ mở 10, `FASTI_DB_CONN_MINIDLE`) |
| `JAVA_OPTS`, `FASTI_MAX_RAM_PERCENTAGE` | tùy chọn JVM, phần trăm RAM của container cho heap (75; 60 khi giới hạn dưới 1,5 GiB) |
| `SERVER_SERVLET_SESSION_COOKIE_NAME` | tên cookie phiên (mặc định `FASTI_SESSION`; đến 1.3.1 là `JSESSIONID`) |

Bảng đầy đủ: [distro/docker/README.vi.md](../distro/docker/README.vi.md#biến-môi-trường).

<a id="9-health-checks"></a>

## 9. Health check

| Endpoint | Dùng cho |
|---|---|
| `/actuator/health/liveness` | Kubernetes liveness probe |
| `/actuator/health/readiness` | readiness probe, load balancer; gồm kết nối cơ sở dữ liệu và engine |
| `/actuator/health` | tổng hợp |

Không cần đăng nhập; chỉ endpoint health được mở. `HEALTHCHECK` của image dùng readiness, nên
`docker ps` hiện `healthy` khi Fasti sẵn sàng:

```bash
curl -s http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
docker inspect -f '{{.State.Health.Status}}' fasti           # healthy
```

Kubernetes: `livenessProbe` → `/actuator/health/liveness`, `readinessProbe` →
`/actuator/health/readiness`, `terminationGracePeriodSeconds: 60`. Helm chart và manifest:
[install-kubernetes.vi.md](install-kubernetes.vi.md).

<a id="10-upgrade-and-rollback"></a>

## 10. Nâng cấp và quay lại

**Nâng cấp Fasti** (ví dụ `1.1.0` → `1.3.2`):

```bash
docker pull fastidev/fasti-bpm:1.3.2
docker stop -t 60 fasti && docker rm fasti
docker run -d --name fasti ... fastidev/fasti-bpm:1.3.2 --production   # cùng tham số như trước
```

Với compose: đổi `FASTI_IMAGE` (hoặc dòng `image:`) rồi `docker compose -f docker-compose.postgres.yml up -d`.
Đọc [CHANGELOG](../CHANGELOG.md) trước, nhất là các mục **BREAKING**. Dòng `1.x` giữ schema 7.24,
nên nâng cấp trong dòng này không đụng tới schema. `docker stop -t 60` cho request đang chạy kết
thúc êm trước khi container bị dừng. Nâng từ 1.3.1 trở về trước lên 1.3.2 trở về sau thì mọi
người dùng web bị đăng xuất một lần: cookie phiên đổi tên từ `JSESSIONID` thành `FASTI_SESSION`.

**Quay lại:**

- về bản Fasti trước: chạy lại với tag cũ;
- về Camunda 7.24: dừng Fasti và khởi động lại node Camunda 7.24 trên cùng cơ sở dữ liệu. Còn
  lại sau đó: tùy chọn giao diện mới (`fasti.prefs.*` trong `ACT_ID_INFO`, Camunda 7 bỏ qua), và
  process đã deploy có dùng tính năng chỉ Fasti có thì không chạy được trên Camunda 7. Xem
  [Quay lại Camunda 7.24](migrate-from-camunda-7.vi.md#quay-lại-camunda-724).

<a id="11-troubleshooting"></a>

## 11. Xử lý sự cố

Xem log bằng `docker logs fasti` (hoặc `docker compose logs fasti`).

| Hiện tượng | Nguyên nhân và cách xử lý |
|---|---|
| REST trả `401` | Fasti bật xác thực: gửi `-u user:mật-khẩu` hoặc bearer token |
| Danh sách rỗng hoặc `403` | người dùng thiếu quyền: cấp đúng quyền trên tài nguyên cần dùng trong Quản trị → Phân quyền. Chỉ thêm vào `camunda-admin` những người thật sự là quản trị viên |
| Dừng với `ENGINE-03111 The database schema has version …` | schema cũ hơn 7.24: sao lưu rồi chạy các script `sql/upgrade` (mục 6). Đừng bỏ qua bằng `FASTI_SCHEMA_VERSION_CHECK=warn`: chạy trên schema cũ không được hỗ trợ và có thể lỗi khi vận hành |
| Dừng với `Insecure configuration refused` | cấu hình tắt xác thực REST hoặc phân quyền; bỏ cấu hình đó (chỉ `FASTI_INSECURE_DEMO=true` mở được, để demo) |
| Dừng với `Password does not match policy` | `--production` áp chính sách mật khẩu cho `FASTI_ADMIN_PASSWORD`: ít nhất 10 ký tự, có hoa, thường, số, ký tự đặc biệt |
| `password authentication failed` | mật khẩu trong tệp bí mật khác với của cơ sở dữ liệu. Tệp tạo trên Windows có thể kết thúc bằng `\r\n`: tạo lại bằng `tr -d '\r\n'`. PostgreSQL chỉ đọc `POSTGRES_PASSWORD_FILE` khi khởi tạo volume lần đầu |
| `FASTI_ADMIN_USER needs FASTI_ADMIN_PASSWORD` | đặt `FASTI_ADMIN_PASSWORD` hoặc `FASTI_ADMIN_PASSWORD_FILE` |
| `--production needs a database` | chưa cấu hình cơ sở dữ liệu nào; `--production` không cho dùng H2 demo mặc định: đặt `DB_URL` tới cơ sở dữ liệu production |
| `… cannot be read` của biến `_FILE` | tệp không gắn vào container hoặc uid 1000 không đọc được (tệp cần quyền `0644`; thư mục chứa nó có thể để `0700`) |
| Đăng nhập web qua HTTP không giữ được phiên | cookie `Secure` (`--production`) chỉ được gửi qua HTTPS (hoặc tới `localhost`): đặt TLS ở proxy |
| Không kết nối được SQL Server (lỗi chứng chỉ) | cài chứng chỉ hợp lệ cho SQL Server, đúng tên máy trong URL; `trustServerCertificate=true` chỉ để thử cục bộ |
| Container khởi động lại liên tục, log có `OutOfMemoryError` | JVM thoát khi hết bộ nhớ: tăng giới hạn bộ nhớ (2 GB) của container |

Vẫn chưa được? Mở issue trên GitHub kèm log (bỏ mật khẩu). Lỗi bảo mật: xem [SECURITY.vi.md](../.github/SECURITY.vi.md).

<a id="12-without-docker"></a>

## 12. Không dùng Docker

Gói Tomcat 1.3.1 dựng sẵn (trang tải: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.3.1.zip](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.zip) ([SHA-256](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.zip.sha256))
- [fasti-bpm-tomcat-1.3.1.tar.gz](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.3.1/fasti-bpm-tomcat-1.3.1.tar.gz.sha256))

Kiểm tra checksum trước khi giải nén, ví dụ `sha256sum -c fasti-bpm-tomcat-1.3.1.zip.sha256`.


<a id="13-observability"></a>

## 13. Giám sát: log JSON, metrics, tracing

> **Có từ 1.1.0.** Các image 1.0.x chưa có các thiết lập trong mục này.

Log JSON, metrics Prometheus và tracing OpenTelemetry đều **tắt theo mặc định**. Mỗi tính năng
bật bằng một biến môi trường của image; cùng các thiết lập đó dùng được dưới dạng thuộc tính
Spring Boot cho Fasti BPM Run chạy ngoài Docker (`configuration/default.yml` hoặc
`production.yml`, xem các ví dụ đã chú thích sẵn trong đó).

| Biến | Thuộc tính | Tác dụng |
|---|---|---|
| `FASTI_LOG_FORMAT=json` | `fasti.logging.format` | mỗi dòng log là một đối tượng JSON ([Elastic Common Schema](https://www.elastic.co/guide/en/ecs/current/)); có thêm `logstash`, `gelf`; mặc định `text` |
| `FASTI_METRICS_PROMETHEUS_ENABLED=true` | `fasti.metrics.prometheus.enabled` | metrics Prometheus tại `/actuator/prometheus` |
| `FASTI_METRICS_PROMETHEUS_TOKEN` (+`_FILE`) | `fasti.metrics.prometheus.token` | token để scrape, gửi dạng `Authorization: Bearer <token>`; giá trị ngẫu nhiên ít nhất 16 ký tự |
| `FASTI_MANAGEMENT_PORT` | `management.server.port` | actuator (health **và** metrics) chạy trên cổng riêng |
| `FASTI_TRACING_ENABLED=true` | `fasti.tracing.enabled` | span OpenTelemetry cho request HTTP, lệnh của engine và mỗi lần chạy job |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `management.opentelemetry.tracing.export.otlp.endpoint` | nơi nhận span (OTLP qua HTTP, cổng 4318) |

### Log JSON

```bash
docker run -d --name fasti -p 8080:8080 -e FASTI_LOG_FORMAT=json ... fastidev/fasti-bpm:1.3.2
docker logs fasti | head -1
# {"@timestamp":"2026-11-21T09:12:03.120Z","log":{"level":"INFO","logger":"org.camunda.bpm.run.CamundaBpmRun"},
#  "process":{"pid":1,"thread":{"name":"main"}},"service":{"name":"fasti-bpm","version":"1.3.2","node":{}},
#  "message":"Starting CamundaBpmRun v1.3.2 using Java 21 ...","ecs":{"version":"8.11"}}
```

Tên dịch vụ là `fasti-bpm`, trừ khi đặt `SPRING_APPLICATION_NAME`. Stack trace nằm trong chính
đối tượng JSON, nên bộ thu log (Fluent Bit, Vector, Filebeat, Loki/Promtail) đọc từng dòng mà
không cần luật ghép nhiều dòng. Vài dòng entrypoint in ra trước khi JVM khởi động
(`fasti-entrypoint: ...`) vẫn là văn bản thường.

### Metrics Prometheus

Có metrics của JVM, request HTTP (`http_server_requests_seconds`), connection pool
(`hikaricp_*`), thread pool của job executor (`executor_*`) và của process engine:

| Metric | Loại | Ý nghĩa |
|---|---|---|
| `fasti_engine_process_instances_started_total` | counter | số process instance gốc đã khởi động |
| `fasti_engine_process_instances_ended_total{state="completed"\|"canceled"}` | counter | số process instance gốc đã kết thúc |
| `fasti_engine_activity_instances_started_total`, `..._ended_total` | counter | activity instance |
| `fasti_engine_jobs_executed_total{result="successful"\|"failed"}` | counter | số lần chạy job |
| `fasti_engine_jobs_acquired_total{result="success"\|"failure"}`, `fasti_engine_jobs_acquisition_attempts_total`, `fasti_engine_jobs_rejected_total`, `fasti_engine_jobs_locked_exclusive_total` | counter | việc lấy job |
| `fasti_engine_decision_instances_evaluated_total`, `fasti_engine_decision_elements_evaluated_total` | counter | số lần đánh giá DMN |
| `fasti_engine_process_instances_active`, `fasti_engine_tasks_open`, `fasti_engine_incidents_open`, `fasti_engine_jobs_executable`, `fasti_engine_jobs_failed`, `fasti_engine_external_tasks_open` | gauge | trạng thái hiện tại, đọc từ cơ sở dữ liệu nhiều nhất 30 giây một lần (`FASTI_METRICS_ENGINE_GAUGES_REFRESH_INTERVAL`) |

Mọi metric của engine có tag `engine` (tên engine); key của process definition hay id của
activity không bao giờ thành tag, nên số chuỗi thời gian luôn nhỏ. Counter bắt đầu từ 0 khi node
khởi động và đếm những gì node đó chạy; gauge cho biết toàn bộ cơ sở dữ liệu (khi có nhiều node:
cộng counter, không cộng gauge). Counter chỉ tăng khi metrics của engine bật
(`camunda.bpm.metrics.enabled`, mặc định bật).

Metrics **không công khai**. Chọn một trong hai cách:

1. **Token scrape** trên cổng của ứng dụng. Mỗi request phải có
   `Authorization: Bearer <token>`, nếu không sẽ nhận `401`. Token phải là giá trị ngẫu nhiên
   ít nhất 16 ký tự; node có token ngắn hơn, hoặc dùng giá trị mẫu `change-me` của cấu hình ví
   dụ, sẽ từ chối khởi động:

   ```bash
   openssl rand -hex 32 > metrics-token && chmod 0644 metrics-token
   docker run -d --name fasti -p 8080:8080 \
     -e FASTI_METRICS_PROMETHEUS_ENABLED=true \
     -e FASTI_METRICS_PROMETHEUS_TOKEN_FILE=/run/secrets/metrics-token \
     -v "$PWD/metrics-token:/run/secrets/metrics-token:ro" ... fastidev/fasti-bpm:1.3.2
   curl -s -H "Authorization: Bearer $(cat metrics-token)" http://localhost:8080/actuator/prometheus | head
   ```

   Prometheus:

   ```yaml
   scrape_configs:
     - job_name: fasti
       metrics_path: /actuator/prometheus
       authorization:
         credentials_file: /etc/prometheus/fasti-metrics-token
       static_configs:
         - targets: ["fasti:8080"]
   ```

2. **Cổng management** chỉ mạng giám sát truy cập được, ví dụ `-e FASTI_MANAGEMENT_PORT=9464`
   và không publish cổng 9464 ra ngoài. Ở cổng này không cần token (nếu đã cấu hình token thì
   vẫn kiểm tra). Các endpoint health cũng chuyển sang cổng đó: `HEALTHCHECK` của image tự theo
   `FASTI_MANAGEMENT_PORT`, nhưng probe của Kubernetes và load balancer cũng phải dùng cổng
   management.

Không có cách nào trong hai cách trên thì node có `FASTI_METRICS_PROMETHEUS_ENABLED=true` sẽ từ
chối khởi động. Khi công tắc tắt, endpoint hoàn toàn không tồn tại, kể cả khi tệp cấu hình mở mọi
endpoint của actuator (`management.endpoints.web.exposure.include: "*"`). Quy tắc này cũng áp
dụng khi endpoint được bật bằng chính các cấu hình Spring Boot đứng sau công tắc
(`management.endpoint.prometheus.access`, `management.prometheus.metrics.export.enabled` và
danh sách exposure): node từ chối khởi động nếu không có token hoặc cổng management riêng, và
token đã cấu hình là bắt buộc trên endpoint.

### Tracing (OpenTelemetry)

```bash
docker run -d --name fasti -p 8080:8080 \
  -e FASTI_TRACING_ENABLED=true \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318 \
  ... fastidev/fasti-bpm:1.3.2
```

Mỗi request HTTP thành một trace; các lệnh engine mà request chạy (ví dụ truy vấn process
definition hay `StartProcessInstanceCmd`) là span con, tên lệnh nằm trong thuộc tính
`fasti.engine.command`. Mỗi lần chạy job mở một trace riêng (`job execution`). Header W3C
`traceparent` gửi đến được nối tiếp. Tên dịch vụ là `fasti-bpm` (đổi bằng `OTEL_SERVICE_NAME`).

- Span chỉ được gửi khi đã đặt endpoint (`OTEL_EXPORTER_OTLP_ENDPOINT`, tự thêm `/v1/traces`,
  hoặc URL đầy đủ trong `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`); chưa đặt thì không mở kết nối mạng
  nào. OTLP gửi qua HTTP (`http/protobuf`, cổng 4318), không dùng gRPC.
- Khi bật tracing, mọi request đều được lấy mẫu. Với lưu lượng lớn hãy giảm, ví dụ
  `-e MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0.1` (hoặc `OTEL_TRACES_SAMPLER_ARG`).
- `FASTI_TRACING_ENGINE_ENABLED=false` giữ span HTTP và bỏ span của lệnh engine.
- Khi bật tracing, dòng log văn bản có trace id và span id; dòng log JSON có chúng ở `traceId`
  và `spanId`.
- Request tới actuator (health check, scrape metrics) không được trace; khi bật tracing chúng cũng
  không được đếm trong `http_server_requests_seconds`.
- Khi bật tracing, endpoint Prometheus có thêm timer `fasti_engine_command_seconds` và
  `fasti_engine_job_seconds` (tag `engine`, `error`).
- Chỉ span được gửi qua OTLP. Metrics vẫn ở endpoint Prometheus; log vẫn ra stdout.
