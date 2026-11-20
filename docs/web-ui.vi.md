[English](web-ui.md) | Tiếng Việt | [Deutsch](web-ui.de.md) | [Español](web-ui.es.md) | [Français](web-ui.fr.md) | [हिन्दी](web-ui.hi.md) | [Bahasa Indonesia](web-ui.id.md) | [日本語](web-ui.ja.md) | [한국어](web-ui.ko.md) | [Português (Brasil)](web-ui.pt-BR.md) | [Русский](web-ui.ru.md) | [ไทย](web-ui.th.md) | [简体中文](web-ui.zh-CN.md)

# Giao diện web của Fasti

Vì sao giao diện web mới có những tính năng này, và các ứng dụng web được phục vụ ở đường dẫn nào. Giới thiệu giao diện: <https://fasti.dev/#features>.

<a id="a-ui-built-from-real-user-complaints"></a>

## Giao diện xây từ những phàn nàn có thật

Các tính năng của giao diện mới được chọn từ những gì người dùng Camunda 7 đã viết công khai
trên diễn đàn Camunda và GitHub, ví dụ:

| Mong muốn của người dùng | Nguồn | Trong Fasti |
|---|---|---|
| Tìm lượt chạy theo nhiều biến, có OR | diễn đàn [39269](https://forum.camunda.io/t/39269), [45695](https://forum.camunda.io/t/45695), [48976](https://forum.camunda.io/t/48976), [64208](https://forum.camunda.io/t/64208), [28855](https://forum.camunda.io/t/28855), [4678](https://forum.camunda.io/t/4678); [camunda-bpm-platform#3236](https://github.com/camunda/camunda-bpm-platform/issues/3236) | Bộ dựng điều kiện nhiều biến, AND/OR |
| Thấy bình luận của mọi bước trong hồ sơ | diễn đàn [30260](https://forum.camunda.io/t/30260), [52109](https://forum.camunda.io/t/52109), [2173](https://forum.camunda.io/t/2173) | Dòng thời gian hồ sơ trong công việc; bình luận cho cả process instance |
| Sắp việc theo biến, việc cũ nhất trước | diễn đàn [17599](https://forum.camunda.io/t/17599), [42078](https://forum.camunda.io/t/42078), [41193](https://forum.camunda.io/t/41193), [67328](https://forum.camunda.io/t/67328) | Sắp theo biến, hạn xử lý và mức khẩn |
| Ủy quyền, người thay thế khi vắng | diễn đàn [2788](https://forum.camunda.io/t/2788), [38199](https://forum.camunda.io/t/38199), [9331](https://forum.camunda.io/t/9331) | Ủy quyền từng việc đã có; người thay thế khi vắng nằm trong [lộ trình](https://fasti.dev/#timeline) |
| Bắt đầu quy trình không có form vẫn nhập business key và biến | [cibseven-webclient#528](https://github.com/cibseven/cibseven-webclient/issues/528) | Có |
| Tải XML BPMN đã triển khai | [camunda/camunda#22005](https://github.com/camunda/camunda/issues/22005) | Có |
| Nhãn nhất quán, nhiều ngôn ngữ | [operaton#643](https://github.com/operaton/operaton/issues/643) | 13 ngôn ngữ; bên trong trình soạn BPMN, DMN và biểu mẫu, nhãn có sẵn của trình soạn chỉ có tiếng Anh và tiếng Việt |

<a id="web-application-path-fasti"></a>

## Đường dẫn web app: `/fasti`

Cockpit, Tasklist, Admin và giao diện web mới nằm dưới `/fasti` (trước đây `/camunda`) trên
mọi bản phân phối: `http://localhost:8080/fasti/` mở giao diện mới
(`/fasti/app/next/default/`), các ứng dụng cổ điển ở `/fasti/app/cockpit/...`,
`/fasti/app/tasklist/...`, `/fasti/app/admin/...`. REST API vẫn ở `/engine-rest`.

**Ứng dụng cổ điển đã lỗi thời (deprecated).** Cockpit, Tasklist, Admin và Welcome cổ điển dựng
trên AngularJS 1.8.2 và Bootstrap 3, cả hai đã hết vòng đời. **Image container không phục vụ
chúng theo mặc định** (thay đổi phá vỡ tương thích): trang và bookmark cũ chuyển tới mục tương
ứng của giao diện mới, file của chúng trả `404`; `FASTI_CLASSIC_WEBAPPS=true` bật lại (có cảnh
báo), xem [distro/docker/README.vi.md](../distro/docker/README.vi.md). Gói Tomcat và WildFly cũng không
phục vụ chúng theo mặc định (bật lại: `-Dfasti.webapp.classic.enabled=true` hoặc
`FASTI_CLASSIC_WEBAPPS=true`). Run zip và Spring Boot starter vẫn phục vụ chúng như trước để không
làm hỏng cài đặt đang chạy; tắt bằng `fasti.webapp.classic.enabled: false`. Các ứng dụng cổ điển
sẽ bị gỡ trong một bản phát hành sau.

Đường dẫn cũ vẫn dùng được: mọi yêu cầu tới `/camunda/...` được chuyển hướng tới cùng
đường dẫn dưới `/fasti/...`, giữ nguyên phần còn lại của đường dẫn và query. GET/HEAD nhận
`301`; các phương thức khác (POST, PUT, DELETE, ví dụ client cũ gọi `/camunda/api/...`) nhận
`308`, nên client gửi lại đúng phương thức và nội dung. Phản hồi chuyển hướng có
`Cache-Control: no-store`, để có thể quay lại `/camunda` bất cứ lúc nào.

Quay lại `/camunda`:

- **Spring Boot, Run:** `camunda.bpm.webapp.application-path: /camunda` (khi đó không còn
  chuyển hướng). Chỉ tắt chuyển hướng: `fasti.webapp.legacy-path-redirect: false`.
- **Tomcat:** xóa `webapps/camunda` (ứng dụng chuyển hướng) rồi đổi tên `webapps/fasti`
  thành `webapps/camunda`.
- **WildFly:** xóa `standalone/deployments/camunda.war` (và `camunda.war.dodeploy`), rồi
  đặt `<context-root>/camunda</context-root>` trong `WEB-INF/jboss-web.xml` của WAR web app.

**Reverse proxy:** nếu proxy chỉ chuyển tiếp `/camunda/` (hoặc viết lại đường dẫn, đặt
cookie path, CSP, health check theo `/camunda`), hãy thêm `/fasti/` — hoặc chuyển hẳn sang
`/fasti/`. Cookie `XSRF-TOKEN` (và `JSESSIONID` trên Tomcat, WildFly) giờ có path `/fasti`,
nên người dùng phải đăng nhập lại một lần. Bookmark và liên kết cũ vẫn chạy nhờ chuyển hướng.
