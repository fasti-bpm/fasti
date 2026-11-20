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

## Thông báo bảo mật

- [CVE-2026-114938 ("Java4Shell")](docs/security/CVE-2026-114938.vi.md): lỗi của Java runtime; Fasti đã làm gì và bạn cần làm gì.

