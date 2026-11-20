[English](SECURITY.md) | Tiếng Việt

# Chính sách bảo mật

## Báo cáo lỗ hổng

Xin **không** báo lỗi bảo mật qua issue, pull request hay thảo luận công khai. Hãy dùng
chức năng báo lỗi riêng tư của GitHub: mở thẻ **Security** của kho mã và chọn **Report a
vulnerability**, kèm phiên bản bị ảnh hưởng, cấu hình cần có và các bước tái hiện.

Nếu không dùng được tính năng báo cáo riêng của GitHub, hãy liên hệ riêng với người duy trì qua
thông tin liên hệ trên trang GitHub của chủ repository, và đừng gửi chi tiết khai thác trong tin
nhắn đầu tiên.

Bạn sẽ nhận được xác nhận đã nhận báo cáo, và chúng tôi sẽ cập nhật tình hình cho tới khi bản vá
được phát hành. Xin dành cho chúng tôi một khoảng thời gian hợp lý để phát hành bản vá trước khi
công bố chi tiết.

## Phạm vi

Chính sách này áp dụng cho mã nguồn trong kho này, một bản fork của Camunda 7 Community Edition
7.24.0. Nếu lỗi cũng ảnh hưởng tới mã gốc hoặc các dự án khác phát triển từ mã gốc, chúng tôi sẽ
phối hợp công bố với người bảo trì các dự án đó khi có thể.

Dự án này không liên kết với Camunda Services GmbH. Đừng báo lỗi của bản fork này cho Camunda.

<a id="support-policy"></a>

## Chính sách hỗ trợ

> Chính sách này do nhóm dự án Fasti BPM duy trì và có thể được cập nhật; bản trong kho mã là
> bản có hiệu lực. Chính sách cũng được đăng tại <https://fasti.dev/security#policy>.

- **Dòng được hỗ trợ:** bản minor mới nhất của dòng `1.x` nhận bản sửa lỗi và bản vá bảo mật
  (phát hành dạng patch, ví dụ `1.0.1`).
- **Bản vá bảo mật:** lỗ hổng của Fasti và của thư viện đi kèm được ưu tiên trước các việc khác;
  image được build lại khi image nền hoặc JRE có bản vá. Dự án theo dõi thông báo bảo mật công
  khai về Camunda 7 và các fork của nó, và kiểm tra Fasti có bị ảnh hưởng không.
- **Tương thích:** dòng `1.x` giữ schema cơ sở dữ liệu 7.24 và package `org.camunda.bpm.*`. Thay
  đổi phá vỡ tương thích được ghi **BREAKING** trong [CHANGELOG](CHANGELOG.md).
- **Hỗ trợ:** câu hỏi và lỗi xin gửi lên GitHub; hỗ trợ ở đó trong khả năng của nhóm, không có
  SLA. Cần hỗ trợ cài đặt, chuyển đổi hoặc vận hành production, hay thống nhất điều khoản hỗ trợ,
  hãy gửi mail đến <contact@fasti.dev>.
- **Danh sách lỗi đã vá:** mục *Fixed* và *Security* của [CHANGELOG](CHANGELOG.md) liệt kê các lỗi
  đã sửa so với Camunda 7 CE 7.24.0, kèm điều gì đã sai và điều gì đã thay đổi, kể cả các vấn đề
  trong mã của chính Fasti; thông báo bảo mật được liệt kê bên dưới.

## Thông báo bảo mật

- [CVE-2026-114938 ("Java4Shell")](docs/security/CVE-2026-114938.vi.md): lỗi của Java runtime; Fasti đã làm gì và bạn cần làm gì.

