[English](../../README.md) | Tiếng Việt | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [हिन्दी](README.hi.md) | [Bahasa Indonesia](README.id.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Português (Brasil)](README.pt-BR.md) | [Русский](README.ru.md) | [ไทย](README.th.md) | [简体中文](README.zh-CN.md)

# Fasti BPM Platform

Fasti BPM là một process engine mã nguồn mở (Apache-2.0), độc lập: nó chạy các quy trình nghiệp
vụ mô hình hóa bằng BPMN và các bảng quyết định DMN, với tác vụ cho người dùng, hẹn giờ, message và
lịch sử đầy đủ. Fasti xây dựng trên Camunda 7 Community Edition 7.24.0 và chạy trên chính cơ sở dữ
liệu Camunda 7 bạn đang có, với thư viện được cập nhật, các bản vá bảo mật và giao diện web mới.
Website: <https://fasti.dev>

<p align="center">
  <a href="https://fasti.dev"><img src="https://assets.fasti.dev/img/og-cover-2.png" alt="Fasti BPM: danh sách task của giao diện web mới" width="820"></a>
</p>

<p align="center">
  <a href="https://github.com/fasti-bpm/fasti/releases/latest"><img src="https://img.shields.io/github/v/release/fasti-bpm/fasti?label=release" alt="Bản phát hành mới nhất"></a>
  <a href="https://hub.docker.com/r/fastidev/fasti-bpm"><img src="https://img.shields.io/docker/pulls/fastidev/fasti-bpm" alt="Lượt tải Docker"></a>
  <a href="../../LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue" alt="Giấy phép: Apache-2.0"></a>
</p>

| **Nhanh hơn ≈10 lần** | **BPMN · DMN · biểu mẫu** | **Mặc định an toàn** | **13** |
|:---:|:---:|:---:|:---:|
| danh sách task khi bật phân quyền ([số liệu](../benchmark-camunda-7.24.vi.md)) | modeler ngay trên trình duyệt | bật đăng nhập và phân quyền, [đã kiểm thử](../security/pentest-1.3.vi.md) | ngôn ngữ, trên điện thoại và máy tính |

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
  fastidev/fasti-bpm:1.3.0
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

**Đổi kết nối. Việc chuyển đổi chỉ có vậy.** Fasti dùng nguyên schema của Camunda 7.24, các
package `org.camunda.bpm.*` giữ nguyên, và image nhận cùng các biến `DB_*` như Camunda Run. Node
Fasti và node 7.24 có thể dùng chung một cơ sở dữ liệu, và bạn có thể quay lại
([chuyển đổi](https://fasti.dev/install#migrate)).

### An toàn ngay từ mặc định

- **Đóng ngay từ lần chạy đầu** (image container): phải đăng nhập, không có người dùng mặc định
  hay người dùng demo, và các thiết lập tắt xác thực REST hoặc phân quyền bị từ chối. Tomcat và
  WildFly yêu cầu thông tin đăng nhập cho REST API và không tạo người dùng nào.
- **Các bản vá bảo mật so với 7.24.0** trong mọi bản phân phối: các lỗi đã công bố của dòng 7.x
  (expression trong query, initial setup, truy cập attachment và comment, phân quyền Cockpit,
  open redirect).
- **Lớp bảo vệ thêm:** allow-list cho expression trong query và filter, kiểm quyền attachment và
  comment ngay trong engine, gia cố security filter, header và cookie của các ứng dụng web, và
  cảnh báo khi khởi động nếu JVM bị Java4Shell ảnh hưởng
  ([bảo mật](https://fasti.dev/security), [cái gì khác](../migrate-from-camunda-7.vi.md#what-differs)).
- **Đăng nhập một lần (SSO):** OpenID Connect và JWT bearer token ([hướng dẫn](../single-sign-on.vi.md)).

### Giao diện web mới

- **Một ứng dụng hiện đại** cho danh sách task, giám sát, hồ sơ (CMMN) và quản trị, 13 ngôn ngữ,
  sáng và tối, dùng tốt trên điện thoại ([tính năng](https://fasti.dev/#features)).
- **Có sẵn modeler:** sửa BPMN, DMN và biểu mẫu rồi triển khai ngay trong cùng giao diện
  ([modeler](https://fasti.dev/#modeler)).
- **Phân tích quy trình:** bản đồ nhiệt trên sơ đồ theo trung vị, P90, P99, thời gian đang chờ,
  làm lại hoặc sự cố, xếp hạng điểm nghẽn, các instance chậm nhất và chờ lâu nhất, xu hướng so với
  mục tiêu, thời gian chờ và thời gian làm của user task. Do chính cơ sở dữ liệu đang dùng tính,
  không cần thêm dịch vụ hay giấy phép ([hướng dẫn](../process-analytics.vi.md)).
- **Công cụ vận hành:** lịch sử, sửa và khởi động lại instance, migration, thao tác hàng loạt,
  chạy lại job lỗi hàng loạt và nhật ký thao tác ([vận hành](https://fasti.dev/#operate)).
- **Nhẹ cho máy chủ:** bảng khối lượng công việc mở ra với 11 request API thay cho 344
  ([hiệu năng giao diện web](../performance-web-ui.vi.md)).

### Nhanh hơn, khi vẫn bật bảo mật

Đo trên PostgreSQL 16 với 1.000.000 task và 2.200.000 bản ghi phân quyền, gọi REST bằng tài khoản
quản trị, so với Camunda Run 7.24.0 trên cùng máy
([bản so sánh](../benchmark-camunda-7.24.vi.md)):

| | Camunda 7.24 | Fasti 1.2.1 |
|---|---|---|
| Danh sách task quá hạn, bật phân quyền | 138 ms | **14 ms** |
| Bảng task 145 request, bật phân quyền | 20,8 s | **8,8 s** |
| Bộ nhớ khi rảnh, cấu hình mặc định | 393 MB | **362 MB** |

Image nặng 390 MB, khởi động trong 4,8 s và trả phần bộ nhớ không dùng về hệ thống.

- **Script chỉ mục tùy chọn** cho sáu loại cơ sở dữ liệu: một lần quét job khi rảnh 162 ms → 0,1 ms,
  task quá hạn sắp theo hạn 61 ms → 0,3 ms ([hiệu năng cơ sở dữ liệu](../performance.vi.md)).
- **Một request cho khối lượng công việc của cả nhóm** (`/task/workload`) thay cho 145.

### Nền tảng hiện tại

Java 17 trở lên, Spring Boot 4.1, GraalJS 25, Jackson 2.21 và các thư viện đã nâng lên bản có bản
vá bảo mật.

<a id="installation"></a><a id="get-fasti"></a>

## Tải Fasti

- <a id="one-command-on-your-existing-camunda-724-database"></a>**Image Docker** (`fastidev/fasti-bpm`, amd64 và arm64): <https://fasti.dev/install>;
  từng bước trong [docs/install-docker.vi.md](../install-docker.vi.md), toàn bộ biến trong
  [distro/docker/README.vi.md](../../distro/docker/README.vi.md).
- <a id="without-docker-tomcat-bundle-download-from-fastidev"></a>**Gói Tomcat**, không dùng Docker: <https://fasti.dev/download>. Gói Tomcat 1.3.0 dựng sẵn:
  - [fasti-bpm-tomcat-1.3.0.zip](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.zip) ([SHA-256](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.zip.sha256))
  - [fasti-bpm-tomcat-1.3.0.tar.gz](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.tar.gz) ([SHA-256](https://assets.fasti.dev/releases/1.3.0/fasti-bpm-tomcat-1.3.0.tar.gz.sha256))

  Kiểm tra checksum trước khi giải nén, ví dụ `sha256sum -c fasti-bpm-tomcat-1.3.0.zip.sha256`,
  và đọc `README.txt` trong gói trước khi chạy.
- **Nhúng** vào Spring Boot hoặc một ứng dụng Java khác: tọa độ Maven mới
  (`dev.fasti.platform:fasti-*`), vẫn các package `org.camunda.bpm.*`; xem
  [docs/embedding.vi.md](../embedding.vi.md).

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
xem [chạy song song](../migrate-from-camunda-7.vi.md#mixed-operation-with-camunda-7-nodes) và
[quay lại](../migrate-from-camunda-7.vi.md#rollback-to-camunda-724).

Các bước chuyển đổi và giới hạn: <https://fasti.dev/install#migrate> và
[hướng dẫn chuyển đổi](../migrate-from-camunda-7.vi.md) (quay lại, schema cũ hơn, kết quả kiểm
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
[SECURITY.vi.md](../../.github/SECURITY.vi.md), nơi có cả [chính sách hỗ trợ](../../.github/SECURITY.vi.md#support-policy).
Danh sách bản vá, Java4Shell và đợt rà soát trước phát hành: <https://fasti.dev/security>. Cần hỗ
trợ cài đặt, chuyển đổi hoặc vận hành production, hay thống nhất điều khoản hỗ trợ, hãy gửi mail
đến <contact@fasti.dev>.

<a id="timeline"></a><a id="camunda-7-milestones"></a><a id="fasti-bpm"></a><a id="roadmap-plan"></a>

## Lộ trình

Đây là kế hoạch, không phải cam kết về ngày. Đã phát hành trong `1.3.0`:
[phân tích quy trình](../process-analytics.vi.md) với bản đồ nhiệt trên sơ đồ, điểm nghẽn, phân
vị, phần làm lại, xu hướng và thời gian của user task, do cơ sở dữ liệu tính. Đã phát hành trong `1.2.1`: kiểm tra phân quyền nhanh
hơn, image nhỏ hơn, khởi động nhanh hơn và dùng ít bộ nhớ hơn
([so sánh với Camunda 7.24](../benchmark-camunda-7.24.vi.md)). Đã phát hành trong `1.2.0`, tập trung vào hiệu năng:
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
| [docs/install-docker.vi.md](../install-docker.vi.md) | Cài đặt bằng Docker từng bước |
| [docs/install-kubernetes.vi.md](../install-kubernetes.vi.md) | Cài đặt trên Kubernetes: Helm chart và manifest thuần (từ 1.1.0) |
| [docs/migrate-from-camunda-7.vi.md](../migrate-from-camunda-7.vi.md) | Chuyển từ Camunda 7, quay lại, giới hạn |
| [docs/embedding.vi.md](../embedding.vi.md) | Tương thích, khác gì so với 7.24.0, phiên bản, tọa độ Maven, kiểm tra khi khởi động |
| [docs/web-ui.vi.md](../web-ui.vi.md) | Giao diện web: tính năng người dùng yêu cầu, đường dẫn `/fasti`, các ứng dụng cổ điển |
| [docs/absence.vi.md](../absence.vi.md) | Người thay thế khi vắng mặt (từ 1.1.0) |
| [docs/four-eyes.vi.md](../four-eyes.vi.md) | Nguyên tắc bốn mắt khi phê duyệt (từ 1.1.0) |
| [docs/audit-trail.vi.md](../audit-trail.vi.md) | Nhật ký kiểm toán không thể sửa đổi (từ 1.1.0) |
| [docs/performance.vi.md](../performance.vi.md) | Hiệu năng cơ sở dữ liệu: chỉ mục tùy chọn, cảnh báo khi khởi động, cấu hình engine, số đo (từ 1.2.0) |
| [docs/process-analytics.vi.md](../process-analytics.vi.md) | Phân tích quy trình: bản đồ nhiệt, điểm nghẽn, thời lượng, REST API, cấu hình, số đo (từ 1.3.0) |
| [docs/single-sign-on.vi.md](../single-sign-on.vi.md) | Đăng nhập một lần (OpenID Connect): Run, Spring Boot, Tomcat, WildFly |
| [docs/api/README.vi.md](../api/README.vi.md) | Tích hợp qua REST API, client external task Java và JavaScript |
| [distro/docker/README.vi.md](../../distro/docker/README.vi.md) | Image: biến môi trường, driver, vận hành, tinh chỉnh |
| [webapps/frontend-next/README.vi.md](../../webapps/frontend-next/README.vi.md) | Giao diện web mới |
| [docs/security/CVE-2026-114938.vi.md](../security/CVE-2026-114938.vi.md) | Java4Shell và Fasti |
| [CHANGELOG.md](../../CHANGELOG.md), [SECURITY.vi.md](../../.github/SECURITY.vi.md) | Thay đổi, bảo mật, chính sách hỗ trợ |

<a id="build"></a>

## Build từ mã nguồn

Yêu cầu: JDK 17 trở lên (khuyến nghị 21) và mạng tới Maven Central, nodejs.org và npm registry
(build tải Node/npm cho web UI).

```bash
./mvnw clean verify
```

Build đầy đủ chạy toàn bộ test Java và test đơn vị của web UI (có thể mất gần một giờ; test
đầu-cuối Playwright chạy riêng, xem [webapps/frontend-next](../../webapps/frontend-next/README.vi.md)).
Xem thêm [TESTING.md](../testing.md). Để build image container (lần đầu mất khoảng 10–20 phút):

```bash
./mvnw -pl distro/docker -am package -DskipTests
docker build -t fasti-bpm:local distro/docker/target/docker
```

<a id="contributing"></a>

## Đóng góp

Câu hỏi, báo lỗi và pull request đều được chào đón trên GitHub; xem
[CONTRIBUTING.vi.md](../../.github/CONTRIBUTING.vi.md) và [Quy tắc ứng xử](../../.github/CODE_OF_CONDUCT.vi.md). Rất mong
người bản ngữ giúp rà soát các bản dịch.

Đơn vị của bạn đang dùng Fasti BPM? [Hãy ghi tên vào danh sách](../adopters.vi.md): một biểu mẫu
ngắn, và chỉ khi bạn đồng ý.

<a id="acknowledgements"></a>

## Ghi nhận

- Fasti BPM Platform dựng trên công sức của Camunda và cộng đồng Camunda 7 trong hơn mười năm.
- Khi rà soát lỗi, dự án có tham khảo thông tin công khai (issue, advisory, ghi chú phát
  hành) của các dự án cộng đồng cùng gốc Camunda 7: Operaton, CIB seven, OrqueIO và
  EximeeBPMS. Mã nguồn trong kho này được viết độc lập trên nền Camunda 7 CE.

<a id="license"></a>

## Giấy phép

[Apache License 2.0](../../LICENSE). Xem [NOTICE](../../NOTICE) về bản quyền của mã gốc.
