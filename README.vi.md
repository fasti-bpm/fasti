[English](README.md) | Tiếng Việt | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

# Fasti BPM Platform

Fasti BPM là một engine mã nguồn mở, độc lập, xây dựng trên Camunda 7 Community Edition 7.24.0.
Nó chạy trên chính cơ sở dữ liệu Camunda 7 bạn đang có, với thư viện được cập nhật, các bản vá
bảo mật và giao diện web mới. Website: <https://fasti.dev>

> **Không liên kết với Camunda Services GmbH; Camunda là nhãn hiệu của Camunda Services GmbH.**
> Fasti BPM là dự án độc lập mã nguồn mở (Apache-2.0), không thuộc, không được bảo trợ hay chứng
> thực bởi Camunda Services GmbH. Tên "Camunda" chỉ được dùng để mô tả nguồn gốc mã nguồn và
> khả năng tương thích.

<a id="quick-start"></a>

## Bắt đầu nhanh

Image có trên Docker Hub (`fastidev/fasti-bpm`) và GitHub Container Registry
(`ghcr.io/fasti-bpm/fasti-bpm`), cho amd64 và arm64:

<!-- update with the Docker Hub image name at release -->
```bash
docker run -d --name fasti -p 127.0.0.1:8080:8080 \
  -e FASTI_ADMIN_USER=admin -e FASTI_ADMIN_PASSWORD='đổi-mật-khẩu-này' \
  fastidev/fasti-bpm:1.2.1
```

Mở <http://localhost:8080/fasti/> và đăng nhập. Không đặt `DB_URL` thì image dùng H2 bên trong
container, chỉ để thử. REST API cần Basic auth:

```bash
curl -i http://localhost:8080/engine-rest/task      # 401: thiếu thông tin đăng nhập
curl -u admin:'đổi-mật-khẩu-này' http://localhost:8080/engine-rest/task
```

Hướng dẫn đầy đủ, kể cả dùng cơ sở dữ liệu của bạn: <https://fasti.dev/install>

<a id="highlights"></a>

## Tính năng nổi bật

- **Đổi kết nối. Việc chuyển đổi chỉ có vậy.** Fasti dùng nguyên schema của Camunda 7.24; các
  package `org.camunda.bpm.*` giữ nguyên ([chuyển đổi](https://fasti.dev/install#migrate)).
- **Một container. Một lệnh.** Dùng cùng các biến `DB_*` như Camunda Run ([cài đặt](https://fasti.dev/install)).
- **Dành cho những người xử lý hàng đợi công việc**: giao diện web mới, 13 ngôn ngữ, sáng và tối,
  dùng tốt trên điện thoại ([tính năng](https://fasti.dev/#features)).
- **Mô hình hóa và triển khai ngay trên trình duyệt**: BPMN, DMN và biểu mẫu ([modeler](https://fasti.dev/#modeler)).
- **Công cụ vận hành đã có sẵn**: lịch sử có thể tìm kiếm, sửa instance đang chạy, chạy lại job
  lỗi hàng loạt, nhật ký thao tác ([vận hành](https://fasti.dev/#operate)).
- **Image container mặc định là đóng**: phải đăng nhập ngay từ lần chạy đầu, không có người dùng
  mặc định, từ chối các thiết lập tắt xác thực REST hoặc phân quyền, và đã giảm thiểu Java4Shell.
  Tomcat và WildFly yêu cầu thông tin đăng nhập cho REST API và không tạo người dùng nào (quản trị
  viên đầu tiên được tạo trên trang setup); Run zip và Spring Boot starter giữ mặc định của
  Camunda 7. Mọi bản phân phối đều có các bản vá bảo mật và cảnh báo khi khởi động nếu JVM bị
  Java4Shell ảnh hưởng ([bảo mật](https://fasti.dev/security)).
- **Đăng nhập một lần (SSO)**: OpenID Connect và JWT bearer token ([hướng dẫn](docs/single-sign-on.vi.md)).
- **Nền tảng:** Java 17 trở lên, Spring Boot 4.1, GraalJS 25, Jackson 2.21
  ([hiệu năng](https://fasti.dev/#performance)).

<a id="installation"></a><a id="get-fasti"></a>

## Tải Fasti

- <a id="one-command-on-your-existing-camunda-724-database"></a>**Image Docker** (`fastidev/fasti-bpm`, amd64 và arm64): <https://fasti.dev/install>;
  từng bước trong [docs/install-docker.vi.md](docs/install-docker.vi.md), toàn bộ biến trong
  [distro/docker/README.vi.md](distro/docker/README.vi.md).
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**Gói Tomcat**, không dùng Docker: <https://fasti.dev/download>. Gói Tomcat 1.2.0 dựng sẵn:
  - [fasti-bpm-tomcat-1.2.0.zip](https://assets.fasti.dev/releases/1.2.0/fasti-bpm-tomcat-1.2.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.2.0/fasti-bpm-tomcat-1.2.0.zip.sha256))
  - [fasti-bpm-tomcat-1.2.0.tar.gz](https://assets.fasti.dev/releases/1.2.0/fasti-bpm-tomcat-1.2.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.2.0/fasti-bpm-tomcat-1.2.0.tar.gz.sha256))

  Kiểm tra checksum trước khi giải nén, ví dụ `sha256sum -c fasti-bpm-tomcat-1.2.0.zip.sha256`,
  và đọc `README.txt` trong gói trước khi chạy.
- **Nhúng** vào Spring Boot hoặc một ứng dụng Java khác: tọa độ Maven mới
  (`dev.fasti.platform:fasti-*`), vẫn các package `org.camunda.bpm.*`; xem
  [docs/embedding.vi.md](docs/embedding.vi.md).

<a id="moving-from-camunda-7"></a>

## Chuyển từ Camunda 7

Nếu bạn đang chạy Camunda 7.24, Fasti chạy trên chính cơ sở dữ liệu đó mà không thay đổi schema. Bạn
có thể thay các nút cũ, chạy Fasti bên cạnh chúng, và quay lại Camunda 7.24 bất cứ lúc nào. Image
Fasti đã được kiểm thử khi tiếp quản cơ sở dữ liệu do Camunda Run 7.24 tạo ra trên PostgreSQL 16,
MariaDB 11.8, MySQL 8.4, SQL Server 2022 và Oracle Database 23ai; việc quay lại Camunda Run đã
được kiểm thử trên các cơ sở dữ liệu đó, trừ MySQL. DB2 chưa được kiểm thử. Mọi REST client, kể cả
external task worker, từ nay phải gửi thông tin đăng nhập Basic hoặc bearer token.

Ứng dụng Spring Boot nhúng engine cũng chuyển lên Spring Boot 4.1 và Java 17; schema cũ hơn cần được
nâng cấp bằng các script chuẩn trước. Plugin và process application viết cho Camunda 7 phải được
build lại với các artifact của Fasti thì mới chạy được bên trong Fasti. Chạy song song và quay lại
đều có giới hạn (cùng cấu hình trên mọi nút, nút nào chạy job, process dùng tính năng chỉ Fasti có):
xem [chạy song song](docs/migrate-from-camunda-7.vi.md#mixed-operation-with-camunda-7-nodes) và
[quay lại](docs/migrate-from-camunda-7.vi.md#rollback-to-camunda-724).

Các bước chuyển đổi và giới hạn: <https://fasti.dev/install#migrate> và
[hướng dẫn chuyển đổi](docs/migrate-from-camunda-7.vi.md) (quay lại, schema cũ hơn, kết quả kiểm
thử).

<a id="comparison"></a><a id="how-it-compares"></a>

## So sánh

Có nhiều dự án tốt đang tiếp nối Camunda 7. Fasti giữ nguyên các package `org.camunda.bpm.*` và
schema 7.24, nên việc chuyển sang chỉ là đổi kết nối chứ không phải một dự án migration, và có
thêm giao diện mới. Các dự án khác cũng là lựa chọn tốt. Bảng đầy đủ, có dẫn nguồn:
<https://fasti.dev/compare>.

<a id="commitments-support-and-security-patches"></a><a id="security-and-support"></a>

## Bảo mật và hỗ trợ

Bản minor mới nhất của dòng `1.x` được sửa lỗi và vá bảo mật, phát hành dưới dạng bản patch như
`1.0.1`. Vui lòng báo cáo vấn đề bảo mật một cách riêng tư, không qua issue công khai: xem
[SECURITY.vi.md](SECURITY.vi.md), nơi có cả [chính sách hỗ trợ](SECURITY.vi.md#support-policy).
Danh sách bản vá, Java4Shell và đợt rà soát trước phát hành: <https://fasti.dev/security>. Cần hỗ
trợ cài đặt, chuyển đổi hoặc vận hành production, hay thống nhất điều khoản hỗ trợ, hãy gửi mail
đến <contact@fasti.dev>.

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## Lộ trình

Đây là kế hoạch, không phải cam kết về ngày. Đã phát hành trong `1.2.1`: kiểm tra phân quyền nhanh
hơn, image nhỏ hơn, khởi động nhanh hơn và dùng ít bộ nhớ hơn
([so sánh với Camunda 7.24](docs/benchmark-camunda-7.24.vi.md)). Đã phát hành trong `1.2.0`, tập trung vào hiệu năng:
endpoint đếm khối lượng công việc, các chỉ mục cơ sở dữ liệu tùy chọn, cảnh báo hiệu năng khi khởi
động, tinh chỉnh nhật ký kiểm toán và giao diện web nhanh hơn. Đã phát hành trong `1.1.0`: Helm
chart và manifest Kubernetes, metrics cho Prometheus, tracing bằng OpenTelemetry, log có cấu trúc
dạng JSON, người thay thế khi vắng mặt, nguyên tắc bốn mắt khi phê duyệt, nhật ký kiểm toán không
thể sửa đổi, MCP server cho trợ lý AI, và hồ sơ (CMMN) trong giao diện mới. Tiếp theo trong `1.x`:
phát hành artifact Maven lên Maven Central, kiểm chứng trên DB2, người bản ngữ rà soát các bản dịch,
và ngừng dùng các ứng dụng AngularJS. Dòng thời gian, từ khi Camunda 7 CE kết thúc tới các mục tiêu
năm 2027: <https://fasti.dev/#timeline>.

<a id="documentation"></a>

## Tài liệu

Trên [fasti.dev](https://fasti.dev): [Cài đặt bằng Docker](https://fasti.dev/install) ·
[Tải bản Tomcat](https://fasti.dev/download) · [Hướng dẫn tích hợp API](https://fasti.dev/docs) ·
[Bảo mật và hỗ trợ](https://fasti.dev/security) · [So sánh](https://fasti.dev/compare) ·
[Blog](https://fasti.dev/blog)

<a id="what-this-is"></a><a id="what-is-different-from-camunda-7240-ce"></a><a id="versioning"></a><a id="maven-coordinates"></a><a id="third-party-libraries-that-still-pull-in-the-original-engine"></a><a id="startup-check"></a><a id="web-application-path-fasti"></a><a id="single-sign-on-openid-connect"></a><a id="a-ui-built-from-real-user-complaints"></a>

| Tài liệu | Nội dung |
|---|---|
| [docs/install-docker.vi.md](docs/install-docker.vi.md) | Cài đặt bằng Docker từng bước |
| [docs/install-kubernetes.vi.md](docs/install-kubernetes.vi.md) | Cài đặt trên Kubernetes: Helm chart và manifest thuần (từ 1.1.0) |
| [docs/migrate-from-camunda-7.vi.md](docs/migrate-from-camunda-7.vi.md) | Chuyển từ Camunda 7, quay lại, giới hạn |
| [docs/embedding.vi.md](docs/embedding.vi.md) | Tương thích, khác gì so với 7.24.0, phiên bản, tọa độ Maven, kiểm tra khi khởi động |
| [docs/web-ui.vi.md](docs/web-ui.vi.md) | Giao diện web: tính năng người dùng yêu cầu, đường dẫn `/fasti`, các ứng dụng cổ điển |
| [docs/absence.vi.md](docs/absence.vi.md) | Người thay thế khi vắng mặt (từ 1.1.0) |
| [docs/four-eyes.vi.md](docs/four-eyes.vi.md) | Nguyên tắc bốn mắt khi phê duyệt (từ 1.1.0) |
| [docs/audit-trail.vi.md](docs/audit-trail.vi.md) | Nhật ký kiểm toán không thể sửa đổi (từ 1.1.0) |
| [docs/performance.vi.md](docs/performance.vi.md) | Hiệu năng cơ sở dữ liệu: chỉ mục tùy chọn, cảnh báo khi khởi động, cấu hình engine, số đo (từ 1.2.0) |
| [docs/single-sign-on.vi.md](docs/single-sign-on.vi.md) | Đăng nhập một lần (OpenID Connect): Run, Spring Boot, Tomcat, WildFly |
| [docs/api/README.vi.md](docs/api/README.vi.md) | Tích hợp qua REST API, client external task Java và JavaScript |
| [distro/docker/README.vi.md](distro/docker/README.vi.md) | Image: biến môi trường, driver, vận hành, tinh chỉnh |
| [webapps/frontend-next/README.vi.md](webapps/frontend-next/README.vi.md) | Giao diện web mới |
| [docs/security/CVE-2026-114938.vi.md](docs/security/CVE-2026-114938.vi.md) | Java4Shell và Fasti |
| [CHANGELOG.md](CHANGELOG.md), [SECURITY.vi.md](SECURITY.vi.md) | Thay đổi, bảo mật, chính sách hỗ trợ |

<a id="build"></a>

## Build từ mã nguồn

Yêu cầu: JDK 17 trở lên (khuyến nghị 21) và mạng tới Maven Central, nodejs.org và npm registry
(build tải Node/npm cho web UI).

```bash
./mvnw clean verify
```

Build đầy đủ chạy toàn bộ test Java và test đơn vị của web UI (có thể mất gần một giờ; test
đầu-cuối Playwright chạy riêng, xem [webapps/frontend-next](webapps/frontend-next/README.vi.md)).
Xem thêm [TESTING.md](TESTING.md). Để build image container (lần đầu mất khoảng 10–20 phút):

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## Đóng góp

Câu hỏi, báo lỗi và pull request đều được chào đón trên GitHub; xem
[CONTRIBUTING.vi.md](CONTRIBUTING.vi.md) và [Quy tắc ứng xử](CODE_OF_CONDUCT.vi.md). Rất mong
người bản ngữ giúp rà soát các bản dịch.

<a id="acknowledgements"></a>

## Ghi nhận

- Fasti BPM Platform dựng trên công sức của Camunda và cộng đồng Camunda 7 trong hơn mười năm.
- Khi rà soát lỗi, dự án có tham khảo thông tin công khai (issue, advisory, ghi chú phát
  hành) của các dự án cộng đồng cùng gốc Camunda 7: Operaton, CIB seven, OrqueIO và
  EximeeBPMS. Mã nguồn trong kho này được viết độc lập trên nền Camunda 7 CE.

<a id="license"></a>

## Giấy phép

[Apache License 2.0](LICENSE). Xem [NOTICE](NOTICE) về bản quyền của mã gốc.
