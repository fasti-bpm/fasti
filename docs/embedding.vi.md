[English](embedding.md) | Tiếng Việt | [Deutsch](embedding.de.md) | [Español](embedding.es.md) | [Français](embedding.fr.md) | [हिन्दी](embedding.hi.md) | [Bahasa Indonesia](embedding.id.md) | [日本語](embedding.ja.md) | [한국어](embedding.ko.md) | [Português (Brasil)](embedding.pt-BR.md) | [Русский](embedding.ru.md) | [ไทย](embedding.th.md) | [简体中文](embedding.zh-CN.md)

# Nhúng Fasti BPM: tương thích và tọa độ Maven

Những gì Fasti giữ lại từ Camunda 7 và những gì khác so với 7.24.0, cách đánh số phiên bản, và tọa độ Maven cho ứng dụng nhúng engine. Để chạy Fasti như một máy chủ, xem [Cài đặt bằng Docker](install-docker.vi.md) hoặc gói Tomcat tại <https://fasti.dev/download>.

<a id="what-this-is"></a>

## Đây là gì

Kho mã này chứa process engine BPMN 2.0 / DMN, REST API, các webapp (Cockpit, Tasklist,
Admin) và các bản phân phối (Run, Tomcat, WildFly) — xuất phát từ tag `7.24.0` của Camunda 7
CE và được vá lỗi, nâng thư viện tiếp từ đó.

Mục tiêu là **tương thích ngược** với ứng dụng đang chạy trên Camunda 7:

- giữ nguyên package `org.camunda.bpm.*`, namespace BPMN `http://camunda.org/schema/1.0/bpmn`
  và tiền tố cấu hình `camunda.bpm.*`;
- giữ nguyên schema cơ sở dữ liệu của 7.24;
- giữ đủ các kiểu triển khai: Spring Boot, Run, Tomcat, WildFly; cả `javax` lẫn `jakarta`.

<a id="what-is-different-from-camunda-7240-ce"></a>

## Khác gì so với Camunda 7.24.0 CE

- **Bảo mật:** vá các lỗi đã được công bố của dòng 7.x (expression trong query,
  setup lần đầu, quyền trên attachment/comment, quyền trong Cockpit, open redirect) và thêm
  các lớp bảo vệ riêng: allow-list cho expression trong query và filter, kiểm quyền
  attachment/comment ở tầng engine, gia cố bộ lọc bảo mật, header và cookie của webapp.
- **Ổn định:** hết rò rỉ bộ nhớ khi chạy script và biểu thức DMN bằng JavaScript (GraalJS),
  cùng nhiều lỗi engine khác.
- **Nền tảng:** Java 17 trở lên, Spring Boot 4.1, GraalJS 25, Jackson 2.21 và nhiều thư viện
  khác được nâng lên bản đã vá lỗi bảo mật.

Một số bản vá làm thay đổi hành vi mặc định theo hướng an toàn hơn; hãy đọc mục "Cái gì khác"
của [hướng dẫn chuyển đổi](migrate-from-camunda-7.vi.md) trước khi nâng cấp.

<a id="versioning"></a>

## Phiên bản

Fasti BPM Platform đánh số theo [Semantic Versioning](https://semver.org/lang/vi/), bắt đầu
từ `1.0.0`. Dòng `1.x` tương thích với ứng dụng viết cho Camunda 7.24.

<a id="maven-coordinates"></a>

## Tọa độ Maven

Các artifact của Fasti BPM Platform có tọa độ Maven riêng. **Package Java vẫn là
`org.camunda.bpm.*`**, nên mã nguồn của ứng dụng không phải sửa `import`; tiền tố cấu hình
`camunda.bpm.*`, namespace BPMN/DMN, schema cơ sở dữ liệu và tên module trên WildFly cũng giữ
nguyên. Về mặt Maven, chỉ phần khai báo dependency thay đổi (đường dẫn web app đổi riêng, xem
[Đường dẫn web app](web-ui.vi.md#web-application-path-fasti)):

- **groupId:** `org.camunda.bpm` → `dev.fasti.platform`, `org.camunda.bpm.<x>` →
  `dev.fasti.platform.<x>`; các nhóm `org.camunda.commons`, `org.camunda.connect`,
  `org.camunda.spin` và `org.camunda.template-engines` → `dev.fasti.platform.<tên nhóm>`.
- **artifactId:** tiền tố `camunda-` đổi thành `fasti-`, phần còn lại giữ nguyên.
- **version:** phiên bản của Fasti BPM Platform (xem [Phiên bản](#versioning)), không còn là
  `7.24.0`.

Chưa có bản phát hành nào của các artifact này trên Maven Central; bản build đã được chuẩn bị
sẵn cho việc đó (xem [Publishing to Maven Central](publishing-maven-central.md), tiếng Anh) và
phiên bản đầu tiên được phát hành sẽ được ghi trong changelog. Cho tới khi có, hãy build từ mã
nguồn và đưa artifact vào kho Maven mà dự án của bạn sử dụng.

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
và giữ nguyên tọa độ.

<a id="third-party-libraries-that-still-pull-in-the-original-engine"></a>

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

<a id="startup-check"></a>

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
