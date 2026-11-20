[English](install-docker.md) | Tiếng Việt

# Cài đặt Fasti BPM bằng Docker

Hướng dẫn từng bước: thử nhanh với H2, chạy production với PostgreSQL (`docker run` hoặc
`docker compose`), nối vào cơ sở dữ liệu Camunda 7 có sẵn, chạy song song, health check, nâng cấp,
quay lại và xử lý sự cố. Tham chiếu đầy đủ của image: [distro/docker/README.vi.md](../distro/docker/README.vi.md).
Fasti BPM được xây dựng trên Camunda 7 CE 7.24.0 và là dự án độc lập, không liên kết với Camunda
Services GmbH.

## 1. Yêu cầu

- Docker Engine có Docker Compose v2 (`docker compose`).
- Với tải thật: ít nhất 2 CPU và 2 GB bộ nhớ cho container Fasti (dưới mức đó JVM tự chọn Serial GC).
- Cơ sở dữ liệu production: PostgreSQL, MariaDB, MySQL, SQL Server hoặc Oracle (xem
  [bảng đã thử](migrate-from-camunda-7.vi.md#giới-hạn)). DB2 chưa được kiểm chứng. H2 chỉ để thử.

## 2. Tải image

<!-- update with the Docker Hub image name at release -->
Image cho amd64 và arm64, có ở hai nơi:

```bash
docker pull fastidev/fasti-bpm:1.0.0              # Docker Hub
docker pull ghcr.io/fasti-bpm/fasti-bpm:1.0.0     # GitHub Container Registry
```

Tag: `1.0.0` (đúng một bản) và `1.0` (bản vá mới nhất của 1.0). Image được ký bằng cosign
(keyless, GitHub OIDC) và kèm SBOM và provenance.

Cũng có thể build image từ mã nguồn (cần JDK 17+ và mạng tới Maven Central,
nodejs.org, npm registry) rồi dùng tên `fasti-bpm:local` thay cho `fastidev/fasti-bpm:1.0.0` ở mọi
lệnh bên dưới:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

## 3. Thử nhanh với H2

```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='đổi-mật-khẩu-này' \
  -v fasti-data:/fasti/data \
  fastidev/fasti-bpm:1.0.0
```

- Mở <http://localhost:8080/fasti/> và đăng nhập bằng `admin`.
- Không đặt `DB_URL` thì image dùng H2 trong `/fasti/data`; volume `fasti-data` giữ dữ liệu khi
  tạo lại container. Chỉ để thử, không dùng cho dữ liệu thật.
- Thêm `--webapps --rest --example` ở cuối lệnh để có thêm quy trình mẫu (invoice). Chỉ ghi
  `--example` thì web UI và REST API không được bật.
- REST API ở `http://localhost:8080/engine-rest` và cần Basic auth; xem
  [hướng dẫn REST API](api/README.vi.md).

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
  -e TZ=Asia/Ho_Chi_Minh \
  --memory 2g --stop-timeout 60 \
  fastidev/fasti-bpm:1.0.0 --production
```

- `FASTI_DB_SCHEMA_UPDATE=true` chỉ tạo các bảng còn thiếu của một cơ sở dữ liệu mới; Fasti không
  bao giờ nâng cấp schema đã có. Có thể giữ biến này ở các lần chạy sau.
- `--production` bật cookie `Secure`, từ chối H2 ngầm định và `DEBUG=true`. Đặt TLS ở reverse
  proxy phía trước; trình duyệt chỉ gửi cookie `Secure` qua HTTPS (hoặc tới `localhost`).
- `WAIT_FOR` chờ cơ sở dữ liệu nhận kết nối trước khi khởi động.
- Mật khẩu admin chỉ được đặt khi admin chưa tồn tại; đổi mật khẩu sau đó trong mục Quản trị.

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

Biến có thể đặt khi chạy: `FASTI_IMAGE` (mặc định `fastidev/fasti-bpm:1.0.0`, ví dụ
`FASTI_IMAGE=fasti-bpm:local`), `FASTI_HOST_PORT` (mặc định `8080`), `TZ` (mặc định `UTC`).

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
  -e TZ=Asia/Ho_Chi_Minh \
  fastidev/fasti-bpm:1.0.0 --production
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

## 7. Chạy song song với node Camunda 7

Fasti và Camunda 7.24 chạy cùng lúc trên một cơ sở dữ liệu được (đã thử: cả hai thấy dữ liệu của
nhau). Điều kiện:

- cùng mức lịch sử, cùng `TZ`, cùng cấu hình mật khẩu, cùng tiền tố bảng;
- nếu node cũ là process application có lớp Java (`JavaDelegate`, listener) mà Fasti không có:
  `-e FASTI_JOB_EXECUTOR_ENABLED=false` trên Fasti, hoặc bật deployment-aware trên **mọi** node
  (`FASTI_JOB_EXECUTOR_DEPLOYMENT_AWARE=true` cho Fasti). Điều này chỉ bảo vệ job bất đồng bộ:
  bắt đầu instance hay hoàn thành task qua Fasti vẫn chạy delegate ngay trên Fasti, nên hãy gửi
  các yêu cầu đó tới node có đủ lớp Java;
- load balancer: sticky session cho web app; REST với Basic auth không cần.

Chi tiết: [Chạy lẫn với node Camunda 7](migrate-from-camunda-7.vi.md#chạy-lẫn-với-node-camunda-7).

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
| `FASTI_DB_CONN_MAXACTIVE` | kích thước pool kết nối (mặc định 10) |
| `JAVA_OPTS`, `FASTI_MAX_RAM_PERCENTAGE` | tùy chọn JVM, phần trăm RAM cho heap (75) |

Bảng đầy đủ: [distro/docker/README.vi.md](../distro/docker/README.vi.md#biến-môi-trường).

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
`/actuator/health/readiness`, `terminationGracePeriodSeconds: 60`.

## 10. Nâng cấp và quay lại

**Nâng cấp Fasti** (ví dụ `1.0.0` → `1.0.1`):

```bash
docker pull fastidev/fasti-bpm:1.0.1
docker stop -t 60 fasti && docker rm fasti
docker run -d --name fasti ... fastidev/fasti-bpm:1.0.1 --production   # cùng tham số như trước
```

Với compose: đổi `FASTI_IMAGE` (hoặc dòng `image:`) rồi `docker compose -f docker-compose.postgres.yml up -d`.
Đọc [CHANGELOG](../CHANGELOG.md) trước, nhất là các mục **BREAKING**. Dòng `1.x` giữ schema 7.24,
nên nâng cấp trong dòng này không đụng tới schema. `docker stop -t 60` cho request đang chạy kết
thúc êm trước khi container bị dừng.

**Quay lại:**

- về bản Fasti trước: chạy lại với tag cũ;
- về Camunda 7.24: dừng Fasti và khởi động lại node Camunda 7.24 trên cùng cơ sở dữ liệu. Còn
  lại sau đó: tùy chọn giao diện mới (`fasti.prefs.*` trong `ACT_ID_INFO`, Camunda 7 bỏ qua), và
  process đã deploy có dùng tính năng chỉ Fasti có thì không chạy được trên Camunda 7. Xem
  [Quay lại Camunda 7.24](migrate-from-camunda-7.vi.md#quay-lại-camunda-724).

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

Vẫn chưa được? Mở issue trên GitHub kèm log (bỏ mật khẩu). Lỗi bảo mật: xem [SECURITY.vi.md](../SECURITY.vi.md).

## 12. Không dùng Docker

Gói Tomcat 1.0.0 dựng sẵn (trang tải: https://fasti.dev/download):
- [fasti-bpm-tomcat-1.0.0.zip](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.zip.sha256))
- [fasti-bpm-tomcat-1.0.0.tar.gz](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.0.0/fasti-bpm-tomcat-1.0.0.tar.gz.sha256))

Kiểm tra checksum trước khi giải nén, ví dụ `sha256sum -c fasti-bpm-tomcat-1.0.0.zip.sha256`.

