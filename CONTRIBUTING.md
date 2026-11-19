# Đóng góp cho dự án

[Tiếng Việt](#đóng-góp-cho-dự-án) · [English](#how-to-contribute)

Cảm ơn bạn quan tâm tới Fasti BPM Platform, một bản fork độc lập của Camunda 7 Community Edition
7.24.0.

## Issue

* Tìm trong các issue đã có trước khi mở issue mới.
* Với lỗi, hãy nêu phiên bản, môi trường (cơ sở dữ liệu, máy chủ ứng dụng, phiên bản Java), các
  bước tái hiện và kết quả bạn mong đợi. Một unit test thất bại là cách tái hiện hữu ích nhất.
* **Không** báo lỗi bảo mật công khai. Xem [SECURITY.md](SECURITY.md).

## Build từ mã nguồn

Cần JDK 17 trở lên (khuyến nghị 21). Kho mã có sẵn Maven wrapper:

```
./mvnw clean verify
```

Xem [TESTING.md](TESTING.md) về các bộ test và profile cơ sở dữ liệu.

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

---

# How to contribute

Thank you for your interest in Fasti BPM Platform, an independent fork of Camunda 7 Community
Edition 7.24.0.

## Issues

* Search the existing issues before opening a new one.
* For a bug, describe the version, the environment (database, application server, Java
  version), the steps to reproduce and what you expected to happen. A failing unit test is
  the most helpful reproduction.
* Do **not** report security issues in public. See [SECURITY.md](SECURITY.md).

## Build from source

Requires JDK 17 or later (21 recommended). The Maven wrapper is included:

```
./mvnw clean verify
```

See [TESTING.md](TESTING.md) for the test suites and database profiles.

## Pull requests

* Keep a pull request focused on one topic.
* Add or adapt tests. A bug fix should come with a test that fails without the fix.
* Stay compatible: public API, the `org.camunda.bpm.*` packages, the configuration
  properties and the database schema of 7.24 must keep working. If a change alters default
  behaviour, say so in the pull request description.
* License headers (see "License headers and packages" below): new files carry the Fasti
  header, files modified from upstream keep the Camunda header.
* Only contribute code that you wrote yourself or that you are entitled to submit under the
  Apache License 2.0. Do not copy code from other projects into a contribution without
  stating its origin and licence in the pull request.

By submitting a contribution you agree that it is licensed under the Apache License 2.0.

## License headers and packages

* Files written for Fasti BPM (not present in the upstream tag `7.24.0`) start with:

  ```
  /*
   * SPDX-License-Identifier: Apache-2.0
   * Copyright Fasti BPM contributors
   */
  ```

  (with `#` or `<!-- -->` for files that do not use `/* */` comments).
* Files that come from Camunda 7 CE 7.24.0 keep the Camunda header, also when modified. Files
  copied or renamed from an upstream file keep the Camunda header too; list their new paths in
  `build-config/license-header-upstream-derived.txt`.
* `webapps/frontend-next/scripts/check-license-headers.mjs` and the Maven profile
  `license-header-check` check this rule.
* Fasti BPM code intentionally lives in `org.camunda.*` packages, for compatibility with
  existing configuration, plugins and process applications. Do not move packages.
  `dev.fasti.*` is reserved for clearly separate modules.

## Commit message conventions

```
<type>(<scope>): <subject>

<body>
```

* `type`: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci` or `chore`.
* `scope`: the module that is changed, e.g. `engine`, `engine-rest`, `webapps`.
* `subject`: imperative form, at most 72 characters, no trailing period.
* `body`: what the problem was and how the change solves it; lines of at most 80
  characters. It can be omitted for small changes.
* Breaking changes: add `!` after the type or scope and a `BREAKING CHANGE:` footer that
  explains how to migrate.
* Commit messages are written in English.

Example:

```
fix(engine): validate expressions in nested or queries of task queries

Or queries were created without the validators of the enclosing query,
so restrictions on expressions did not apply to them.
```
