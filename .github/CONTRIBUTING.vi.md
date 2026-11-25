[English](CONTRIBUTING.md) | Tiếng Việt

# Đóng góp cho dự án

Cảm ơn bạn quan tâm tới Fasti BPM Platform, một bản fork độc lập của Camunda 7 Community Edition
7.24.0.

## Issue

* Tìm trong các issue đã có trước khi mở issue mới.
* Với lỗi, hãy nêu phiên bản, môi trường (cơ sở dữ liệu, máy chủ ứng dụng, phiên bản Java), các
  bước tái hiện và kết quả bạn mong đợi. Một unit test thất bại là cách tái hiện hữu ích nhất.
* **Không** báo lỗi bảo mật công khai. Xem [SECURITY.vi.md](SECURITY.vi.md).
* Đơn vị của bạn đang dùng Fasti BPM? Xem [docs/adopters.vi.md](../docs/adopters.vi.md) để được ghi tên.

## Build từ mã nguồn

Cần JDK 17 trở lên (khuyến nghị 21). Kho mã có sẵn Maven wrapper:

```
./mvnw clean verify
```

Xem [TESTING.md](../docs/testing.md) về các bộ test và profile cơ sở dữ liệu.

## Pull request

* Mỗi pull request chỉ tập trung vào một chủ đề.
* Thêm hoặc sửa test. Bản vá lỗi cần có một test thất bại khi chưa có bản vá.
* Giữ tương thích: API công khai, các package `org.camunda.bpm.*`, các thuộc tính cấu hình và
  schema cơ sở dữ liệu của 7.24 phải tiếp tục dùng được. Nếu thay đổi làm đổi hành vi mặc định,
  hãy nêu rõ trong mô tả pull request.
* Header giấy phép (xem mục "Header giấy phép và package" bên dưới): file mới mang header
  Fasti, file sửa từ upstream giữ header của Camunda.
* Chỉ đóng góp mã do chính bạn viết hoặc bạn có quyền nộp theo Apache License 2.0. Không chép
  mã từ dự án khác vào đóng góp mà không nêu nguồn gốc và giấy phép trong pull request.
* Tài liệu công khai (các file README, `docs/`, chính sách bảo mật) được viết bằng tiếng Anh.
  Bản dịch là tùy chọn và nằm cạnh file tiếng Anh dưới dạng `<tên>.<locale>.md` (ví dụ
  `README.vi.md`); khi sửa một tài liệu tiếng Anh, hãy cập nhật các bản dịch của nó hoặc nêu
  trong pull request những bản dịch nào đang bị lỗi thời. Bản tiếng Anh là bản có hiệu lực.

Khi gửi đóng góp, bạn đồng ý rằng đóng góp đó được cấp phép theo Apache License 2.0.

## Header giấy phép và package

* File do Fasti BPM viết mới (không có trong tag `7.24.0` của upstream) bắt đầu bằng header:

  ```
  /*
   * SPDX-License-Identifier: Apache-2.0
   * Copyright Fasti BPM contributors
   */
  ```

  (dùng `#` hoặc `<!-- -->` cho file không theo cú pháp `/* */`).
* File có sẵn từ Camunda 7 CE 7.24.0, kể cả khi đã sửa, giữ nguyên header của Camunda. File
  sao chép hoặc đổi tên từ một file upstream cũng giữ header của Camunda; ghi đường dẫn mới vào
  `build-config/license-header-upstream-derived.txt`.
* `webapps/frontend-next/scripts/check-license-headers.mjs` và profile Maven
  `license-header-check` kiểm tra quy tắc này.
* Mã của Fasti BPM cố ý nằm trong các package `org.camunda.*` để tương thích với cấu hình,
  plugin và process application hiện có. Không chuyển package. `dev.fasti.*` chỉ dành cho
  module tách biệt rõ ràng.

## Quy ước commit message

```
<type>(<scope>): <subject>

<body>
```

* `type`: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci` hoặc `chore`.
* `scope`: module bị thay đổi, ví dụ `engine`, `engine-rest`, `webapps`.
* `subject`: dạng mệnh lệnh, tối đa 72 ký tự, không có dấu chấm cuối.
* `body`: vấn đề là gì và thay đổi giải quyết nó thế nào; mỗi dòng tối đa 80 ký tự. Có thể bỏ
  qua với thay đổi nhỏ.
* Thay đổi không tương thích (breaking change): thêm `!` sau type hoặc scope và một footer
  `BREAKING CHANGE:` giải thích cách chuyển đổi.
* Commit message viết bằng tiếng Anh.

Ví dụ:

```
fix(engine): validate expressions in nested or queries of task queries

Or queries were created without the validators of the enclosing query,
so restrictions on expressions did not apply to them.
```

