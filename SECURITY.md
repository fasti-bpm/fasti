# Chính sách bảo mật

*[English below](#security-policy).*

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

- [CVE-2026-114938 ("Java4Shell")](docs/security/CVE-2026-114938.md): lỗi của Java runtime; Fasti đã làm gì và bạn cần làm gì.

---

# Security Policy

## Reporting a vulnerability

Please do **not** report security issues in public issues, pull requests or discussions.

Use GitHub's private vulnerability reporting for this repository: open the **Security** tab
and choose **Report a vulnerability**. Include the affected version, the configuration that
is needed, and steps to reproduce.

If private vulnerability reporting is unavailable, contact the maintainers privately through
the contact listed on the repository owner's GitHub profile, and do not include exploit details
in the first message.

You will receive an acknowledgement, and we will keep you informed until a fix is released.
Please give us reasonable time to publish a fix before disclosing details.

## Scope

This policy covers the code in this repository. It is a fork of Camunda 7 Community Edition
7.24.0. If an issue also affects the upstream code base or other projects derived from it,
we coordinate disclosure with the maintainers of those projects where possible.

This project is not affiliated with Camunda Services GmbH. Do not report issues of this
fork to Camunda.

## Advisories

- [CVE-2026-114938 ("Java4Shell")](docs/security/CVE-2026-114938.md): a flaw in the Java runtime; what Fasti changed and what you should do.
