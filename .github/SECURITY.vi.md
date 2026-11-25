[English](SECURITY.md) | Tiếng Việt

# Chính sách bảo mật

## Báo cáo lỗ hổng

Xin **không** báo lỗi bảo mật qua issue, pull request hay thảo luận công khai. Hãy dùng
chức năng báo lỗi riêng tư của GitHub: mở thẻ **Security** của kho mã và chọn **Report a
vulnerability**, kèm phiên bản bị ảnh hưởng, cấu hình cần có và các bước tái hiện.

Nếu không dùng được tính năng báo cáo riêng của GitHub, hãy gửi thư tới <contact@fasti.dev> với
tiêu đề "Security", và đừng gửi chi tiết khai thác trong thư đầu tiên.

Bạn có thể mong đợi:

| Bước | Mục tiêu |
|---|---|
| Xác nhận đã nhận báo cáo | trong 3 ngày làm việc |
| Đánh giá ban đầu (phiên bản bị ảnh hưởng, mức độ) | trong 7 ngày làm việc |
| Sửa lỗi mức nghiêm trọng hoặc cao | bản patch sớm nhất có thể, mục tiêu 14 ngày |
| Sửa lỗi mức trung bình hoặc thấp | bản phát hành thường kỳ tiếp theo |
| Thông báo công khai | khi bản sửa được phát hành; ghi công người báo, trừ khi bạn không muốn |

Xin dành cho chúng tôi một khoảng thời gian hợp lý để phát hành bản vá trước khi công bố chi tiết.

## Cách công bố bản sửa và sự cố

- Lỗi mức nghiêm trọng hoặc cao có bản patch riêng; bản đã phát hành không bao giờ bị sửa lại.
  Mục *Security* của [CHANGELOG](../CHANGELOG.md) ghi mọi bản sửa bảo mật: lỗi là gì và đã thay
  đổi gì.
- Thông báo bảo mật nêu phiên bản và cấu hình bị ảnh hưởng, nguyên nhân gốc, phiên bản đã sửa và
  việc người dùng phải làm (ví dụ đổi một khóa bí mật). Chúng tôi không coi một sự cố là đã xử lý
  xong trước khi thông báo đó được công bố.
- Bản phát hành kiểm chứng được: image container được ký bằng cosign và kèm SBOM
  (`fasti-bpm.spdx.json` trên mỗi bản phát hành GitHub); bundle Tomcat có checksum SHA-256 đi kèm.
  Kiểm tra image bằng
  `cosign verify fastidev/fasti-bpm:<version> --certificate-identity-regexp '^https://github.com/fasti-bpm/fasti/' --certificate-oidc-issuer https://token.actions.githubusercontent.com`.
- Fasti không gửi telemetry và không tự kiểm tra cập nhật. Nó chỉ mở kết nối tới cơ sở dữ liệu,
  tới những gì bạn cấu hình (nhà cung cấp định danh, LDAP, bộ xuất tracing) hoặc những gì mô hình
  quy trình của bạn gọi (connector, script).
- Kết quả các đợt tự kiểm thử bảo mật được công bố: [Kiểm thử bảo mật bản 1.3](../docs/security/pentest-1.3.vi.md);
  trạng thái của Fasti với từng thông báo bảo mật của Camunda 7: [Thông báo bảo mật của Camunda 7](../docs/security/camunda-7-advisories.vi.md);
  dữ liệu cá nhân và thời gian lưu: [Dữ liệu cá nhân](../docs/security/personal-data.vi.md).

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
  (phát hành dạng patch, ví dụ `1.3.1`), **ít nhất tới hết ngày 31/12/2027**.
- **Nếu dự án dừng:** Fasti giữ nguyên schema cơ sở dữ liệu Camunda 7.24, nên cơ sở dữ liệu của
  Fasti vẫn là cơ sở dữ liệu Camunda 7.24. Bạn có thể quay về Camunda 7.24 hoặc chuyển sang một
  fork Camunda 7 khác mà không phải chuyển đổi dữ liệu; mã nguồn vẫn có theo giấy phép Apache 2.0.
- **Bản vá bảo mật:** lỗ hổng của Fasti và của thư viện đi kèm được ưu tiên trước các việc khác.
  Khi image nền hoặc JRE có bản vá bảo mật ảnh hưởng tới Fasti, một bản patch dùng image nền hoặc
  JRE đã cập nhật sẽ được phát hành; image đã công bố không được build lại. Dự án theo dõi thông
  báo bảo mật công khai về Camunda 7 và các fork của nó, và kiểm tra Fasti có bị ảnh hưởng không.
- **Tương thích:** dòng `1.x` giữ schema cơ sở dữ liệu 7.24 và package `org.camunda.bpm.*`. Thay
  đổi phá vỡ tương thích được ghi **BREAKING** trong [CHANGELOG](../CHANGELOG.md).
- **Hỗ trợ:** câu hỏi và lỗi xin gửi lên GitHub; hỗ trợ ở đó trong khả năng của nhóm, không có
  SLA. Cần hỗ trợ cài đặt, chuyển đổi hoặc vận hành production, hay thống nhất điều khoản hỗ trợ,
  hãy gửi mail đến <contact@fasti.dev>.
- **Danh sách lỗi đã vá:** mục *Fixed* và *Security* của [CHANGELOG](../CHANGELOG.md) liệt kê các lỗi
  đã sửa so với Camunda 7 CE 7.24.0, kèm điều gì đã sai và điều gì đã thay đổi, kể cả các vấn đề
  trong mã của chính Fasti; thông báo bảo mật được liệt kê bên dưới.

## Thông báo bảo mật

- [Kiểm thử bảo mật bản 1.3](../docs/security/pentest-1.3.vi.md): các lỗi tìm thấy ở 1.3.0 và đã sửa trong 1.3.1.
- [Thông báo bảo mật của Camunda 7](../docs/security/camunda-7-advisories.vi.md): trạng thái của Fasti với từng thông báo, gồm CVE-2026-77224 (sửa trong 1.3.1).
- [CVE-2026-114938 ("Java4Shell")](../docs/security/CVE-2026-114938.vi.md): lỗi của Java runtime; Fasti đã làm gì và bạn cần làm gì.

