# Fasti BPM Platform

**Make Camunda 7 Great Again.** Camunda 7 Community Edition đã dừng ở bản 7.24.0, bản CE cuối
cùng. Fasti BPM nhận tiếp từ đó: cùng engine, cùng cơ sở dữ liệu, tiếp tục vá lỗi và nâng thư
viện, kèm giao diện web mới.

*Make Camunda 7 Great Again: Camunda 7 Community Edition ended with 7.24.0, its final release.
Fasti BPM carries it on: the same engine on the same database, with continued fixes, current
libraries and a new web UI. [English below](#english).*

> **Không liên kết với Camunda Services GmbH; Camunda là nhãn hiệu của Camunda Services GmbH.**
> Fasti BPM là dự án độc lập mã nguồn mở (Apache-2.0), không thuộc, không được bảo trợ hay chứng
> thực bởi Camunda Services GmbH. Tên "Camunda" chỉ được dùng để mô tả nguồn gốc mã nguồn và
> khả năng tương thích.

## Bắt đầu nhanh

**Từ bản 1.0.0**, image có trên Docker Hub (`fastidev/fasti-bpm`) và GitHub Container Registry
(`ghcr.io/fasti-bpm/fasti-bpm`), cho amd64 và arm64:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='đổi-mật-khẩu-này' \
  fastidev/fasti-bpm:1.0.0
```

Mở <http://localhost:8080/fasti/> và đăng nhập. Không đặt `DB_URL` thì image dùng H2 bên trong
container, chỉ để thử. REST API cần Basic auth:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: thiếu thông tin đăng nhập
curl -u admin:'đổi-mật-khẩu-này' http://localhost:8080/engine-rest/task
```

**Trước khi image được phát hành**, hãy build từ mã nguồn. Cần Docker, JDK 17+ và mạng tới Maven
Central, nodejs.org và npm registry (build tải Node/npm cho web UI); lần đầu mất khoảng 10–20
phút:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
# rồi chạy lệnh docker run ở trên với fasti-bpm:local thay cho fastidev/fasti-bpm:1.0.0
```

Image **bật sẵn xác thực REST và phân quyền** (khác Camunda Run); chỉ `FASTI_INSECURE_DEMO=true`
tắt được, để demo.

## Tính năng nổi bật

- **Giao diện web mới** ở `/fasti/` (React):
  - tìm lượt chạy theo nhiều biến cùng lúc, kết hợp AND/OR;
  - dòng thời gian của cả hồ sơ ngay trong công việc (bình luận và thao tác của mọi bước);
  - nút "Nhận việc tiếp theo";
  - hạn xử lý và mức khẩn (SLA), việc quá hạn;
  - khối lượng công việc theo người và nhóm;
  - hướng dẫn xử lý (runbook) gắn với sự cố;
  - trang sức khỏe hệ thống (sự cố, job lỗi, job trễ, external task, batch);
  - dùng tốt trên điện thoại;
  - 13 ngôn ngữ, gồm tiếng Việt; trình soạn BPMN có tiếng Việt.
- **Đăng nhập một lần (OIDC)**: Keycloak, Microsoft Entra ID và nhà cung cấp OpenID Connect
  khác; claim sang nhóm và tenant; JWT bearer cho REST API. Có cho Run, Spring Boot, Tomcat và
  WildFly.
- **Gia cố bảo mật**: allow-list expression trong query và filter, kiểm quyền attachment và
  comment ở tầng engine, setup lần đầu, quyền trong Cockpit, open redirect, header, cookie và
  CSP của webapp. Chi tiết trong [CHANGELOG](CHANGELOG.md).
- **Xác thực bật sẵn**: image yêu cầu đăng nhập cho REST và bật phân quyền ở mọi chế độ.
- **Bảo vệ phiên bản schema**: engine từ chối chạy trên schema cũ hơn 7.24 và không bao giờ tự
  nâng schema.
- **Giảm thiểu Java4Shell** ([CVE-2026-114938](docs/security/CVE-2026-114938.md)): image amd64
  dùng JRE Temurin đã vá và tắt intrinsic Base64; engine cảnh báo khi chạy trên JVM có lỗi.
- **Hiệu năng**: tải lần đầu của web UI mới giảm từ 960 KB xuống 330 KB (gzip bật sẵn); thông
  lượng job executor tăng khoảng 2,5 lần khi tinh chỉnh (số đo và điều kiện đo trong
  [distro/docker/README.md](distro/docker/README.md#tinh-chỉnh-hiệu-năng)).
- **Nền mới**: Java 17 trở lên, Spring Boot 4.1, GraalJS 25, Jackson 2.21.

## Cài đặt

### Một lệnh, trên cơ sở dữ liệu Camunda 7.24 đang có

Image nhận đúng các biến `DB_*` của image Camunda 7 Run trước đây. Sao lưu cơ sở dữ liệu, rồi:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /đường/dẫn/db-password:/run/secrets/db-password:ro \
  -e TZ=Asia/Ho_Chi_Minh \
  fastidev/fasti-bpm:1.0.0
```

Người dùng, mật khẩu, nhóm, tenant, phân quyền, filter, deployment, instance đang chạy, lịch
sử, job và incident giữ nguyên. Cổng chỉ mở trên máy này; truy cập từ xa hãy đi qua reverse proxy
có HTTPS. Đăng nhập `/fasti/` bằng tài khoản đã có. Vì xác thực bật sẵn,
mọi client REST (external task worker, tích hợp) phải gửi Basic auth hoặc bearer token.

- **Chạy song song** với các node Camunda 7.24 CE trên cùng cơ sở dữ liệu: cùng mức lịch sử,
  cùng `TZ`, cùng cấu hình mật khẩu. Nếu node cũ là process application có lớp Java mà Fasti
  không có, đặt `FASTI_JOB_EXECUTOR_ENABLED=false` trên Fasti (hoặc bật deployment-aware trên
  mọi node), và đừng gọi các process đó (bắt đầu, hoàn thành task) qua Fasti: lời gọi đồng bộ
  chạy delegate ngay trên node nhận yêu cầu.
- **Quay lại**: Fasti không đổi schema, nên chỉ cần dừng Fasti và khởi động lại node Camunda
  7.24 trên cùng cơ sở dữ liệu. Process đã deploy có dùng tính năng chỉ Fasti có thì không chạy
  được trên Camunda 7; xem [Quay lại Camunda 7.24](docs/chuyen-tu-camunda-7.md#quay-lại-camunda-724).
- **Schema cũ hơn (7.17–7.23)**: chạy các script `sql/upgrade` có trong image
  (`/fasti/configuration/sql/upgrade`) cho tới 7.24 trước; ví dụ 7.23 → 7.24:
  `postgres_engine_7.23_to_7.24.sql`.

Đã thử thật với image (tiếp quản cơ sở dữ liệu do Camunda Run 7.24.0 tạo, quay lại, chạy song
song): **PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022, Oracle Database 23** (driver
Oracle không kèm sẵn, tự thêm `ojdbc11`; với MySQL chưa thử bước quay lại). **DB2 chưa được kiểm chứng.** H2 chỉ để thử. Kết quả
từng phép thử và giới hạn: [hướng dẫn chuyển đổi](docs/chuyen-tu-camunda-7.md#giới-hạn).

Hướng dẫn chi tiết:

- [Cài đặt bằng Docker](docs/cai-dat-docker.md): H2, PostgreSQL với `docker run` và
  `docker compose`, cơ sở dữ liệu có sẵn, chạy song song, health check, nâng cấp, quay lại, xử
  lý sự cố.
- [Chuyển từ Camunda 7](docs/chuyen-tu-camunda-7.md): các bước, cái gì giống và khác, quay lại,
  giới hạn.
- [Tham chiếu biến môi trường của image](distro/docker/README.md#biến-môi-trường).

### Không dùng Docker: gói Tomcat (tải từ fasti.dev)

Gói Tomcat dựng sẵn sẽ được tải từ fasti.dev:
<!-- update with the R2 download URL -->
[tải gói Tomcat](https://fasti.dev/) (liên kết sẽ được cập nhật khi phát hành). Trong lúc chờ,
có thể build bản phân phối Tomcat từ mã nguồn (`distro/tomcat`).

## Giao diện xây từ những phàn nàn có thật

Các tính năng của giao diện mới được chọn từ những gì người dùng Camunda 7 đã viết công khai
trên diễn đàn Camunda và GitHub, ví dụ:

| Mong muốn của người dùng | Nguồn | Trong Fasti |
|---|---|---|
| Tìm lượt chạy theo nhiều biến, có OR | diễn đàn [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Bộ dựng điều kiện nhiều biến, AND/OR |
| Thấy bình luận của mọi bước trong hồ sơ | diễn đàn [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Dòng thời gian hồ sơ trong công việc; bình luận cho cả process instance |
| Sắp việc theo biến, việc cũ nhất trước | diễn đàn [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Sắp theo biến, hạn xử lý và mức khẩn |
| Ủy quyền, người thay thế khi vắng | diễn đàn [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Ủy quyền từng việc đã có; người thay thế khi vắng nằm trong [lộ trình](#lộ-trình-kế-hoạch) |
| Bắt đầu quy trình không có form vẫn nhập business key và biến | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Có |
| Tải XML BPMN đã triển khai | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Có |
| Nhãn nhất quán, nhiều ngôn ngữ | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 ngôn ngữ |

## So sánh

So sánh ngắn gọn, theo tài liệu công khai của từng dự án (đọc tháng 11/2026); "chưa kiểm chứng"
nghĩa là chúng tôi chưa tìm được nguồn đáng tin. Mỗi dự án có hướng đi riêng và đều là lựa
chọn hợp lý cho những nhu cầu khác nhau; hãy đối chiếu với tài liệu của họ trước khi quyết định.

| | Giấy phép | Chạy thẳng trên DB C7 có sẵn | Phải chuyển đổi | Bản vá bảo mật | Java / Spring Boot | Giao diện | SSO | Image Docker |
|---|---|---|---|---|---|---|---|---|
| **Fasti BPM** | Apache-2.0 | Có, schema 7.24 không đổi | Image: không. Ứng dụng nhúng: đổi tọa độ Maven, lên Spring Boot 4.1 và Java 17+; package `org.camunda.*` giữ nguyên | Có, theo [chính sách bên dưới](#cam-kết-hỗ-trợ-và-bản-vá-bảo-mật) | 17+ / 4.1 | Mới (React) + cổ điển (lỗi thời) | OIDC có sẵn | Có, từ 1.0.0 |
| Camunda 7 CE 7.24 | Apache-2.0 | (bản gốc) | — | Không còn bản CE sau 7.24 ([1]) | 17, 21, 25 ([2]) / 3.5 | Cockpit, Tasklist, Admin (AngularJS) | OAuth2 qua starter security | Image Run cũ |
| Camunda 7 Enterprise | Thương mại | Có (cùng dòng sản phẩm) | Không | Tới 04/2030, gia hạn trả phí tới 04/2032 ([3]) | chưa kiểm chứng | Cockpit Enterprise | chưa kiểm chứng | chưa kiểm chứng |
| Camunda 8 | Camunda License 1.0; production self-managed cần license từ 8.6 ([4]) | Không (engine khác) | Có (mô hình, instance, dữ liệu) | Có (nhà cung cấp) | engine khác, không so được | Operate, Tasklist | chưa kiểm chứng | chưa kiểm chứng |
| Operaton | Apache-2.0 | Cấu trúc bảng như 7.24; bản 2.2 ghi phiên bản schema 7.25 bằng script nâng cấp ([5]) | Đổi package `org.operaton.*` (có OpenRewrite recipe); chạy script phiên bản schema (2.2) | Có, dòng 2.2.x ([6]) | 17+ / 4.1 ([5]) | Cổ điển + webapps-neo (preview) | chưa kiểm chứng | Có ([7]) |
| CIB seven | Apache-2.0; bản EE cần khóa license | Có (đã gộp thay đổi 7.24) | Đổi package `org.cibseven.*`, tiền tố `cibseven.*` | Có, theo bản phát hành ([8]) | 17, 21 / 3.5 và 4.0 ([9]) | Webclient Vue 3 | OAuth2/JWT cho REST ([8]) | Có ([8]) |
| OrqueIO | Apache-2.0 | Tương thích bảng C7 theo tài liệu của họ ([10]) | Đổi package sang `io.orqueio.*` | Có, theo tài liệu của họ ([10]) | 21 / 3.5, 4.0, 4.1 | Viết lại bằng Angular | OIDC đa nhà cung cấp | chưa kiểm chứng |
| EximeeBPMS | Apache-2.0 (Monitor: MIT) | chưa kiểm chứng | Đổi package `org.eximeebpms.*`, đổi tên Model API | Có, hai minor gần nhất ([11]) | 21 / 4.1 | Monitor riêng | chưa kiểm chứng | Có (repo `eximeebpms-docker`) |
| Fluxnova | Apache-2.0 (FINOS) | chưa kiểm chứng | chưa kiểm chứng | chưa kiểm chứng | 21 (image Corretto) / chưa kiểm chứng | Control Center | chưa kiểm chứng | Có ([12]) |

Điểm yếu của Fasti cũng cần nói rõ: dự án mới, nhóm nhỏ, chưa có hỗ trợ thương mại, DB2 chưa
được kiểm chứng.

Nguồn: [1] [thông báo CE hết vòng đời](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921);
[2] [môi trường hỗ trợ 7.24](https://docs.camunda.org/manual/7.24/introduction/supported-environments/);
[3] [gia hạn Enterprise](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/);
[4] [giấy phép Camunda 8.6](https://camunda.com/blog/2024/10/camunda-licensing-what-you-need-to-know/);
[5] [Operaton 2.2](https://docs.operaton.org/docs/documentation/reference/release-notes/2_2/);
[6] [Operaton SECURITY.md](https://github.com/operaton/operaton/blob/main/SECURITY.md);
[7] [operaton-docker](https://github.com/operaton/operaton-docker);
[8] [CIB seven 2.2.0](https://docs.cibseven.org/manual/latest/release-notes/release-notes-2.2.0-ce/);
[9] [CIB seven: môi trường hỗ trợ](https://docs.cibseven.org/manual/latest/introduction/supported-environments/);
[10] [tài liệu OrqueIO](https://docs.orqueio.io/);
[11] [EximeeBPMS SECURITY.md](https://github.com/EximeeBPMS/eximeebpms/blob/main/SECURITY.md);
[12] [Fluxnova](https://github.com/finos/fluxnova-bpm-platform);
tổng quan độc lập: [WZ-IT, 15/10/2026](https://wz-it.com/en/blog/camunda-7-end-of-life-operaton-cibseven-fluxnova-compared/).

## Dòng thời gian

### Các mốc của Camunda 7

| Ngày | Mốc | Nguồn |
|---|---|---|
| 06/03/2024 | Camunda thông báo Camunda 7 CE hết vòng đời vào tháng 10/2025, với 7.24 là bản CE cuối | [diễn đàn Camunda](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921) |
| 28/02/2025 | Camunda gia hạn hỗ trợ Camunda 7 Enterprise tới 09/04/2030; hỗ trợ mở rộng trả phí tới 04/2032 | [blog Camunda](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) |
| 07/10/2025 | Tag `7.24.0` được tạo và phát hành lên Maven Central: bản CE cuối cùng | [tag 7.24.0](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0), [Maven Central](https://repo1.maven.org/maven2/org/camunda/bpm/camunda-engine/7.24.0/) |
| 14/10/2025 | Ngày phát hành 7.24 theo lịch Camunda công bố; sau ngày này không còn bản CE nào, kể cả bản vá bảo mật. Kho mã CE nay đã được lưu trữ (archived) | [thông báo](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921), [trang hỗ trợ](https://docs.camunda.org/enterprise/announcement) |
| 04/2030 | Kết thúc hỗ trợ Camunda 7 Enterprise (7.24 LTS: 13/04/2030) | [trang hỗ trợ](https://docs.camunda.org/enterprise/announcement) |
| 04/2032 | Kết thúc hỗ trợ mở rộng trả phí của Camunda 7 Enterprise | [trang hỗ trợ](https://docs.camunda.org/enterprise/announcement) |

### Fasti BPM

Fasti tiếp nối trực tiếp từ tag `7.24.0`; mọi bản vá và thay đổi được tính từ đó (`git log
7.24.0..`). Các giai đoạn dưới đây xếp theo công việc đã làm, không gắn ngày:

1. **Nền móng**: Java 17, Spring Boot 4.1, nâng thư viện; tọa độ Maven `dev.fasti.platform`.
2. **Bảo mật**: vá các lỗi đã công bố của dòng 7.x, allow-list expression, kiểm quyền
   attachment/comment ở engine, gia cố webapp.
3. **Giao diện web mới** ở `/fasti/`, thay dần Cockpit, Tasklist và Admin cổ điển.
4. **Đăng nhập một lần** (OIDC) cho Run, Spring Boot, Tomcat và WildFly.
5. **Chạy thẳng trên cơ sở dữ liệu có sẵn**: image nhận biến `DB_*` cũ, kiểm tra phiên bản
   schema, thử thật trên năm hệ quản trị cơ sở dữ liệu.
6. **Rà soát bảo mật trước khi công khai** (review mã, kiểm thử xâm nhập image, quét ZAP
   baseline; đã sửa các lỗi tìm được) và **giảm thiểu Java4Shell**.
7. **1.0.0**: bản phát hành đầu tiên (đang chuẩn bị).

## Lộ trình (kế hoạch)

> Đây là **kế hoạch**, chưa phải cam kết; thứ tự có thể đổi theo phản hồi của người dùng.

- **Ủy quyền khi vắng mặt**: người thay thế trong một khoảng ngày, task mới được giao lại và ghi
  audit ([diễn đàn 2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199)).
- **Phê duyệt bốn mắt**: người duyệt bước sau không được là người đã làm bước trước, kiểm tra ở
  engine.
- **Helm chart** cho Kubernetes.
- **Metrics và OpenTelemetry**: Micrometer/Prometheus gốc, tracing cho process và job.
- **Nâng MyBatis** lên bản mới.
- **Dòng hỗ trợ dài hạn (LTS)** cho người cần nâng cấp thưa.
- **Kiểm chứng DB2**.
- **Endpoint tổng hợp khối lượng công việc** để trang khối lượng nhanh hơn với dữ liệu lớn.

## Cam kết: hỗ trợ và bản vá bảo mật

> Chính sách này do nhóm dự án Fasti BPM duy trì và có thể được cập nhật; bản trong kho mã là
> bản có hiệu lực.

- **Dòng được hỗ trợ:** bản minor mới nhất của dòng `1.x` nhận bản sửa lỗi và bản vá bảo mật
  (phát hành dạng patch, ví dụ `1.0.1`).
- **Bản vá bảo mật:** lỗ hổng của Fasti và của thư viện đi kèm được ưu tiên trước các việc khác;
  image được build lại khi image nền hoặc JRE có bản vá. Dự án theo dõi thông báo bảo mật công
  khai về Camunda 7 và các fork của nó, và kiểm tra Fasti có bị ảnh hưởng không.
- **Tương thích:** dòng `1.x` giữ schema cơ sở dữ liệu 7.24 và package `org.camunda.bpm.*`. Thay
  đổi phá vỡ tương thích được ghi **BREAKING** trong [CHANGELOG](CHANGELOG.md).
- **Mức hỗ trợ:** đây là dự án cộng đồng; hỗ trợ trong khả năng của nhóm, **không có SLA** và
  chưa có hỗ trợ thương mại.
- **Báo lỗ hổng:** xin **đừng** mở issue công khai; xem [SECURITY.md](SECURITY.md) (báo cáo riêng
  qua GitHub).
- **Danh sách lỗi đã vá:** mục *Fixed* và *Security* của [CHANGELOG](CHANGELOG.md) liệt kê các lỗi
  đã sửa so với Camunda 7 CE 7.24.0; thông báo bảo mật nằm trong [SECURITY.md](SECURITY.md#thông-báo-bảo-mật).

## Tài liệu

| Tài liệu | Nội dung |
|---|---|
| [docs/cai-dat-docker.md](docs/cai-dat-docker.md) | Cài đặt bằng Docker từng bước |
| [docs/api/README.md](docs/api/README.md) | Tích hợp qua REST API, client external task Java và JavaScript |
| [docs/chuyen-tu-camunda-7.md](docs/chuyen-tu-camunda-7.md) | Chuyển từ Camunda 7, quay lại, giới hạn |
| [distro/docker/README.md](distro/docker/README.md) | Image: biến môi trường, driver, vận hành, tinh chỉnh |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md) | Giao diện web mới |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md) | Java4Shell và Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md), [CONTRIBUTING.md](CONTRIBUTING.md) | Thay đổi, bảo mật, đóng góp |

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

- **Bảo mật:** vá các lỗi đã được công bố công khai của dòng 7.x (expression trong query,
  setup lần đầu, quyền trên attachment/comment, quyền trong Cockpit, open redirect) và thêm
  các lớp bảo vệ riêng: allow-list cho expression trong query và filter, kiểm quyền
  attachment/comment ở tầng engine, tăng cứng bộ lọc bảo mật của webapp, header và cookie.
- **Ổn định:** hết rò rỉ bộ nhớ khi chạy script và biểu thức DMN bằng JavaScript (GraalJS),
  cùng nhiều lỗi engine khác.
- **Nền tảng:** Java 17 trở lên, Spring Boot 4.1, GraalJS 25, Jackson 2.21 và nhiều thư viện
  khác được nâng lên bản đã vá lỗi bảo mật.

Một số bản vá làm thay đổi hành vi mặc định theo hướng an toàn hơn; hãy đọc ghi chú phát
hành trước khi nâng cấp.

## Phiên bản

Fasti BPM Platform đánh số theo [Semantic Versioning](https://semver.org/lang/vi/), bắt đầu
từ `1.0.0`. Dòng `1.x` tương thích với ứng dụng viết cho Camunda 7.24.

## Toạ độ Maven

Các artifact của Fasti BPM Platform có toạ độ Maven riêng. **Package Java vẫn là
`org.camunda.bpm.*`**, nên mã nguồn của ứng dụng không phải sửa `import`; tiền tố cấu hình
`camunda.bpm.*`, namespace BPMN/DMN, schema cơ sở dữ liệu, context path của các webapp và
tên module trên WildFly cũng giữ nguyên. Chỉ phần khai báo dependency thay đổi:

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
và giữ nguyên toạ độ.

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

**Make Camunda 7 Great Again.** Camunda 7 Community Edition ended with 7.24.0, its final
release. Fasti BPM carries it on from there: the same engine on the same database, with
continued fixes and library upgrades, and a new web UI.

> **Not affiliated with Camunda Services GmbH; Camunda is a trademark of Camunda Services GmbH.**
> Fasti BPM is an independent open-source project (Apache-2.0). It is not part of, sponsored by
> or endorsed by Camunda Services GmbH. The name "Camunda" is used only to describe where the
> source code comes from and what Fasti is compatible with.

### Quick start

**From 1.0.0 on**, the image is available on Docker Hub (`fastidev/fasti-bpm`) and on GitHub
Container Registry (`ghcr.io/fasti-bpm/fasti-bpm`), for amd64 and arm64:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='change-me' \
  fastidev/fasti-bpm:1.0.0
```

Open <http://localhost:8080/fasti/> and sign in. Without `DB_URL` the image uses an H2 database
inside the container, for trying out only. The REST API needs Basic credentials:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: no credentials
curl -u admin:'change-me' http://localhost:8080/engine-rest/task
```

**Until the image is published**, build it from source. This needs Docker, JDK 17+ and network
access to Maven Central, nodejs.org and the npm registry (the build downloads Node/npm for the
web UI); the first build takes about 10–20 minutes:

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
# then run the docker run command above with fasti-bpm:local instead of fastidev/fasti-bpm:1.0.0
```

The image **requires REST authentication and enforces authorizations by default** (unlike Camunda
Run); only `FASTI_INSECURE_DEMO=true` turns them off, for demos.

### Highlights

- **New web UI** at `/fasti/` (React):
  - search process instances by several variables at once, combined with AND/OR;
  - case timeline inside the task (comments and actions of every step);
  - a "Get next work" button;
  - due dates and urgency (SLA), overdue work;
  - team workload per user and group;
  - runbooks attached to incidents;
  - a system health page (incidents, failed jobs, late jobs, external tasks, batches);
  - mobile-friendly layout;
  - 13 languages including Vietnamese; the BPMN modeler speaks Vietnamese too.
- **Single sign-on (OIDC)**: Keycloak, Microsoft Entra ID and other OpenID Connect providers;
  claims mapped to groups and tenants; JWT bearer tokens for the REST API. Available for Run,
  Spring Boot, Tomcat and WildFly.
- **Security hardening**: allow-list for expressions in queries and filters, attachment and
  comment checks inside the engine, initial setup, Cockpit permissions, open redirect, web
  application headers, cookies and CSP. Details in the [CHANGELOG](CHANGELOG.md).
- **Authentication on by default**: the image requires credentials for REST and enforces
  authorizations in every mode.
- **Schema version guard**: the engine refuses to start on a schema older than 7.24 and never
  upgrades a schema by itself.
- **Java4Shell mitigation** ([CVE-2026-114938](docs/security/CVE-2026-114938.md)): the amd64
  image ships a fixed Temurin JRE and turns off the Base64 intrinsic; the engine warns when it
  runs on an affected JVM.
- **Performance**: the first load of the new web UI drops from 960 KB to 330 KB (gzip on by
  default); job executor throughput rises about 2.5x with tuning (numbers and test setup in
  [distro/docker/README.md](distro/docker/README.md#performance-tuning)).
- **Current platform**: Java 17 or later, Spring Boot 4.1, GraalJS 25, Jackson 2.21.

### Installation

#### One command, on your existing Camunda 7.24 database

The image accepts the `DB_*` variables of the former Camunda 7 Run image. Back up the database,
then:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e DB_DRIVER=org.postgresql.Driver \
  -e DB_URL=jdbc:postgresql://db.example.com:5432/camunda \
  -e DB_USERNAME=camunda -e DB_PASSWORD_FILE=/run/secrets/db-password \
  -v /path/to/db-password:/run/secrets/db-password:ro \
  -e TZ=Europe/Berlin \
  fastidev/fasti-bpm:1.0.0
```

Users, passwords, groups, tenants, authorizations, filters, deployments, running instances,
history, jobs and incidents stay as they are. The port is bound to this machine only; for remote
access go through a reverse proxy with HTTPS. Sign in at `/fasti/` with an existing account.
Because authentication is on, every REST client (external task workers, integrations) must send
Basic credentials or a bearer token.

- **Side by side** with Camunda 7.24 CE nodes on the same database: same history level, same
  `TZ`, same password settings. If the old nodes are process applications with Java classes that
  Fasti lacks, set `FASTI_JOB_EXECUTOR_ENABLED=false` on Fasti (or make every node
  deployment-aware), and do not drive those processes (start, complete tasks) through Fasti: a
  synchronous call runs the delegates on the node that receives it.
- **Rollback**: Fasti does not change the schema, so stop Fasti and start the Camunda 7.24 nodes
  again on the same database. Deployed processes that use Fasti-only features will not run on
  Camunda 7; see [Rollback to Camunda 7.24](docs/chuyen-tu-camunda-7.md#rollback-to-camunda-724).
- **Older schemas (7.17–7.23)**: run the `sql/upgrade` scripts shipped in the image
  (`/fasti/configuration/sql/upgrade`) up to 7.24 first; for example 7.23 → 7.24:
  `postgres_engine_7.23_to_7.24.sql`.

Tested for real with the image (taking over a database created by Camunda Run 7.24.0, rolling
back, running side by side): **PostgreSQL 16, MariaDB 11.8, MySQL 8.4, SQL Server 2022, Oracle
Database 23** (the Oracle driver is not bundled; add `ojdbc11` yourself; rollback was not tested
on MySQL). **DB2 is not
verified.** H2 is for trying out only. Results per test and limits:
[migration guide](docs/chuyen-tu-camunda-7.md#limits).

Detailed guides:

- [Installing with Docker](docs/cai-dat-docker.md#english): H2, PostgreSQL with `docker run` and
  `docker compose`, existing databases, side-by-side mode, health checks, upgrade, rollback,
  troubleshooting.
- [Moving from Camunda 7](docs/chuyen-tu-camunda-7.md#english): steps, what is identical and what
  differs, rollback, limits.
- [Environment variable reference of the image](distro/docker/README.md#environment-variables).

#### Without Docker: Tomcat bundle (download from fasti.dev)

A ready-made Tomcat bundle will be available for download from fasti.dev:
<!-- update with the R2 download URL -->
[download the Tomcat bundle](https://fasti.dev/) (the link will be updated at release). Until
then, the Tomcat distribution can be built from source (`distro/tomcat`).

### A UI built from real user complaints

The features of the new web UI were chosen from what Camunda 7 users have written publicly on the
Camunda forum and on GitHub, for example:

| What users asked for | Source | In Fasti |
|---|---|---|
| Search instances by several variables, with OR | forum [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Multi-variable condition builder, AND/OR |
| See the comments of every step of a case | forum [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Case timeline in the task; comments on the process instance itself |
| Sort tasks by variables, oldest first | forum [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Sorting by variables, due dates and urgency |
| Delegation, substitutes during absence | forum [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Per-task delegation exists; out-of-office substitutes are on the [roadmap](#roadmap-plan) |
| Business key and variables when starting a process without a form | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Yes |
| Download the deployed BPMN XML | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Yes |
| Consistent labels, several languages | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 languages |

### Comparison

A compact comparison based on each project's public documentation (read in November 2026);
"not verified" means we found no reliable source. Every project has its own direction and is a
sound choice for different needs; check their documentation before you decide.

| | Licence | Runs on an existing C7 DB | Migration needed | Security patches | Java / Spring Boot | UI | SSO | Docker image |
|---|---|---|---|---|---|---|---|---|
| **Fasti BPM** | Apache-2.0 | Yes, 7.24 schema unchanged | Image: none. Embedded apps: new Maven coordinates, Spring Boot 4.1 and Java 17+; `org.camunda.*` packages kept | Yes, see the [policy below](#commitments-support-and-security-patches) | 17+ / 4.1 | New (React) + classic (deprecated) | OIDC built in | Yes, from 1.0.0 |
| Camunda 7 CE 7.24 | Apache-2.0 | (the original) | — | No CE releases after 7.24 ([1]) | 17, 21, 25 ([2]) / 3.5 | Cockpit, Tasklist, Admin (AngularJS) | OAuth2 via the security starter | Former Run image |
| Camunda 7 Enterprise | Commercial | Yes (same product line) | No | Until 04/2030, paid extension to 04/2032 ([3]) | not verified | Cockpit Enterprise | not verified | not verified |
| Camunda 8 | Camunda License 1.0; self-managed production needs a licence since 8.6 ([4]) | No (different engine) | Yes (models, instances, data) | Yes (vendor) | different engine, not comparable | Operate, Tasklist | not verified | not verified |
| Operaton | Apache-2.0 | Same table structure as 7.24; 2.2 records schema version 7.25 through an upgrade script ([5]) | Packages renamed to `org.operaton.*` (OpenRewrite recipe available); schema-version script (2.2) | Yes, 2.2.x line ([6]) | 17+ / 4.1 ([5]) | Classic + webapps-neo (preview) | not verified | Yes ([7]) |
| CIB seven | Apache-2.0; EE needs a licence key | Yes (7.24 changes merged) | Packages renamed to `org.cibseven.*`, prefix `cibseven.*` | Yes, per release ([8]) | 17, 21 / 3.5 and 4.0 ([9]) | Vue 3 webclient | OAuth2/JWT for REST ([8]) | Yes ([8]) |
| OrqueIO | Apache-2.0 | Compatible with C7 tables per their docs ([10]) | Packages renamed to `io.orqueio.*` | Yes, per their docs ([10]) | 21 / 3.5, 4.0, 4.1 | Angular rewrite | Multi-provider OIDC | not verified |
| EximeeBPMS | Apache-2.0 (Monitor: MIT) | not verified | Packages renamed to `org.eximeebpms.*`, Model API renamed | Yes, two latest minors ([11]) | 21 / 4.1 | Separate Monitor | not verified | Yes (`eximeebpms-docker` repository) |
| Fluxnova | Apache-2.0 (FINOS) | not verified | not verified | not verified | 21 (Corretto image) / not verified | Control Center | not verified | Yes ([12]) |

Fasti's own weak points, stated plainly: a young project with a small team, no commercial
support yet, DB2 not verified.

Sources: [1] [CE end-of-life announcement](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921);
[2] [7.24 supported environments](https://docs.camunda.org/manual/7.24/introduction/supported-environments/);
[3] [Enterprise extension](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/);
[4] [Camunda 8.6 licensing](https://camunda.com/blog/2024/10/camunda-licensing-what-you-need-to-know/);
[5] [Operaton 2.2](https://docs.operaton.org/docs/documentation/reference/release-notes/2_2/);
[6] [Operaton SECURITY.md](https://github.com/operaton/operaton/blob/main/SECURITY.md);
[7] [operaton-docker](https://github.com/operaton/operaton-docker);
[8] [CIB seven 2.2.0](https://docs.cibseven.org/manual/latest/release-notes/release-notes-2.2.0-ce/);
[9] [CIB seven supported environments](https://docs.cibseven.org/manual/latest/introduction/supported-environments/);
[10] [OrqueIO docs](https://docs.orqueio.io/);
[11] [EximeeBPMS SECURITY.md](https://github.com/EximeeBPMS/eximeebpms/blob/main/SECURITY.md);
[12] [Fluxnova](https://github.com/finos/fluxnova-bpm-platform);
independent overview: [WZ-IT, 2026-10-15](https://wz-it.com/en/blog/camunda-7-end-of-life-operaton-cibseven-fluxnova-compared/).

### Timeline

#### Camunda 7 milestones

| Date | Milestone | Source |
|---|---|---|
| 2024-03-06 | Camunda announces the end of life of Camunda 7 CE for October 2025, with 7.24 as the final CE release | [Camunda forum](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921) |
| 2025-02-28 | Camunda extends Camunda 7 Enterprise support to 2030-04-09, with paid extended support to April 2032 | [Camunda blog](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) |
| 2025-10-07 | Tag `7.24.0` created and published to Maven Central: the final CE release | [tag 7.24.0](https://github.com/camunda/camunda-bpm-platform/tree/7.24.0), [Maven Central](https://repo1.maven.org/maven2/org/camunda/bpm/camunda-engine/7.24.0/) |
| 2025-10-14 | Release date of 7.24 as scheduled by Camunda; after it there are no CE releases, security patches included. The CE repository has since been archived | [announcement](https://forum.camunda.io/t/important-update-camunda-7-community-edition-end-of-life-announced/50921), [support page](https://docs.camunda.org/enterprise/announcement) |
| April 2030 | End of Camunda 7 Enterprise support (7.24 LTS: 2030-04-13) | [support page](https://docs.camunda.org/enterprise/announcement) |
| April 2032 | End of paid extended support for Camunda 7 Enterprise | [support page](https://docs.camunda.org/enterprise/announcement) |

#### Fasti BPM

Fasti continues directly from the `7.24.0` tag; every fix and change counts from there
(`git log 7.24.0..`). The phases below are ordered by the work done, without dates:

1. **Foundation**: Java 17, Spring Boot 4.1, library upgrades; Maven coordinates
   `dev.fasti.platform`.
2. **Security**: fixes for the publicly disclosed issues of the 7.x line, expression allow-list,
   attachment/comment checks inside the engine, web application hardening.
3. **New web UI** at `/fasti/`, gradually replacing the classic Cockpit, Tasklist and Admin.
4. **Single sign-on** (OIDC) for Run, Spring Boot, Tomcat and WildFly.
5. **Drop-in on existing databases**: the image accepts the former `DB_*` variables, checks the
   schema version, tested for real on five database systems.
6. **Pre-release security review** (code review, penetration test of the image, ZAP baseline
   scan; the findings were fixed) and **Java4Shell mitigation**.
7. **1.0.0**: the first release (in preparation).

### Roadmap (plan)

> This is a **plan**, not a commitment; the order may change with user feedback.

- **Out-of-office delegation**: a substitute for a date range; new tasks are reassigned and the
  change is audited ([forum 2788](https://forum.camunda.io/t/2788),
  [38199](https://forum.camunda.io/t/38199)).
- **Four-eyes approval**: the approver of a later step must not be the person who did an earlier
  one, enforced in the engine.
- **Helm chart** for Kubernetes.
- **Metrics and OpenTelemetry**: native Micrometer/Prometheus, tracing for processes and jobs.
- **MyBatis upgrade** to a current release.
- **Long-term support (LTS) lines** for users who upgrade rarely.
- **DB2 verification**.
- **Workload aggregate endpoint**, so that the workload page stays fast on large data.

### Commitments: support and security patches

> This policy is maintained by the Fasti BPM project team and may be updated; the version in this
> repository is the one in force.

- **Supported line:** the latest minor release of the `1.x` line receives bug fixes and security
  patches (as patch releases, for example `1.0.1`).
- **Security patches:** vulnerabilities in Fasti and in the libraries it ships take priority over
  other work; the image is rebuilt when the base image or the JRE gets a fix. The project follows
  public security advisories about Camunda 7 and its forks and checks whether Fasti is affected.
- **Compatibility:** the `1.x` line keeps the 7.24 database schema and the `org.camunda.bpm.*`
  packages. Breaking changes are marked **BREAKING** in the [CHANGELOG](CHANGELOG.md).
- **Level of support:** this is a community project; support is best effort, **without an SLA**,
  and there is no commercial support yet.
- **Reporting vulnerabilities:** please do **not** open a public issue; see
  [SECURITY.md](SECURITY.md#security-policy) (private reporting through GitHub).
- **Fixed defects:** the *Fixed* and *Security* sections of the [CHANGELOG](CHANGELOG.md) list
  what was fixed compared with Camunda 7 CE 7.24.0; advisories are listed in
  [SECURITY.md](SECURITY.md#advisories).

### Documentation

| Document | Contents |
|---|---|
| [docs/cai-dat-docker.md](docs/cai-dat-docker.md#english) | Installing with Docker, step by step |
| [docs/api/README.md](docs/api/README.md#english) | REST API integration, Java and JavaScript external task clients |
| [docs/chuyen-tu-camunda-7.md](docs/chuyen-tu-camunda-7.md#english) | Moving from Camunda 7, rollback, limits |
| [distro/docker/README.md](distro/docker/README.md#english) | Image: environment variables, drivers, operations, tuning |
| [webapps/frontend-next/README.md](webapps/frontend-next/README.md#new-web-ui-frontend-next) | The new web UI |
| [docs/security/CVE-2026-114938.md](docs/security/CVE-2026-114938.md#cve-2026-114938-java4shell-and-fasti-bpm) | Java4Shell and Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.md](SECURITY.md#security-policy), [CONTRIBUTING.md](CONTRIBUTING.md#how-to-contribute) | Changes, security, contributing |

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

Some fixes change defaults towards the safer behaviour; read the release notes before
upgrading.

### Versioning

Fasti BPM Platform follows [Semantic Versioning](https://semver.org/), starting at `1.0.0`.
The `1.x` line is compatible with applications written for Camunda 7.24.

### Maven coordinates

The artifacts of Fasti BPM Platform have their own Maven coordinates. **The Java packages
remain `org.camunda.bpm.*`**, so application code keeps its imports; the `camunda.bpm.*`
configuration prefix, the BPMN/DMN namespaces, the database schema, the context paths of the
web applications and the module names on WildFly are unchanged as well. Only the dependency
declarations change:

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

When it is built, the engine inspects the class path and **refuses to start** if it finds
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
