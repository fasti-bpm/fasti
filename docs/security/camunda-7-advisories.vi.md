[English](camunda-7-advisories.md) | Tiếng Việt

# Thông báo bảo mật của Camunda 7 và Fasti BPM

*Cập nhật: 25/11/2026, cho Fasti BPM 1.3.1.*

Camunda 7 Community Edition dừng ở bản 7.24.0. Các bản sửa bảo mật Camunda công bố sau đó chỉ có
trong bản vá Enterprise, nên một bản cài 7.24.0 CE nguyên gốc vẫn bị ảnh hưởng. Fasti BPM xây dựng
trên 7.24.0 CE và tự sửa các lỗi này bằng mã của mình, viết độc lập. Trang này liệt kê các thông
báo chúng tôi biết có liên quan tới mã 7.24 và trạng thái của Fasti với từng cái. Nếu thiếu hoặc sai, hãy báo cho chúng tôi
(xem [SECURITY.vi.md](../../.github/SECURITY.vi.md)).

Nguồn: [security notices](https://docs.camunda.org/security/notices/) của Camunda, NVD/OSV và
thông báo của các fork Camunda 7 khác.

## Thông báo năm 2026 (Camunda chỉ sửa trong Enterprise 7.24.x)

| Thông báo | Thành phần, lỗi | Fasti BPM |
|---|---|---|
| CVE-2026-109432 | Truy vấn task qua REST: biểu thức trong `orQueries` không được kiểm tra, người dùng đã đăng nhập có thể cho chạy biểu thức tùy ý (chạy mã) | **Không bị ảnh hưởng từ 1.0.0.** Mọi or query đều được kiểm tra, biểu thức trong truy vấn ad-hoc bị tắt, filter đã lưu chỉ nhận allow-list (`currentUser()`, `currentUserGroups()`, `now()`, phép tính `dateTime()`). Đã kiểm thử lại trong [đợt kiểm thử 1.3](pentest-1.3.vi.md). |
| Notice 157 | Biểu thức trong filter đã lưu và truy vấn task ad-hoc (chạy mã, leo thang quyền) | **Không bị ảnh hưởng từ 1.0.0**, như trên. |
| CVE-2026-77226 | Thiết lập lần đầu của Admin: tạo được admin mà không cần xác thực | **Sửa trong 1.0.0** (trang setup đóng ngay khi có admin, không còn race) và **siết thêm trong 1.3.1**: trang setup đòi mã dùng một lần in trong log của server. Image tắt trang này theo mặc định. |
| CVE-2026-109431 | Bình luận và tệp đính kèm của task qua REST: thiếu kiểm tra quyền (đọc, xóa) | **Sửa trong 1.0.0**; đã kiểm thử lại trong 1.3. |
| CVE-2026-109433 | Cockpit: kiểm tra quyền bị tắt bị đảo ngược | **Sửa trong 1.0.0.** Cockpit cổ điển cũng tắt theo mặc định trong image và các bundle Tomcat, WildFly. |
| CVE-2026-77224 | External task client: biến XML được phân tích mà không chặn external entity (đọc file, SSRF trên máy chạy worker) | **Sửa trong 1.3.1.** Client từ chối khai báo DOCTYPE và external entity. |
| CVE-2026-40973, -22016, -34282, -41841, -41842, -41843, -76183 (notice 160, 162, 163, 171) | Chưa công bố chi tiết | Chưa rõ cơ chế; chúng tôi theo dõi và sẽ cập nhật trang này. |
| CVE-2026-64607 | httpclient5 (rò kết nối) | Không bị ảnh hưởng: Fasti dùng httpclient5 5.6.5. |
| GHSA-72hv-8253-57qq | jackson-core (độ dài số) | Không bị ảnh hưởng: Fasti dùng Jackson 2.21.8. |
| Tomcat, Spring | CVE của máy chủ và framework đi kèm | Đã nâng phiên bản; lần quét Trivy 0.68.1 ngày 25/11/2026 trên image và bundle Tomcat 1.3.1 không thấy lỗi đã biết mức nghiêm trọng hay cao, và thấy một lỗi mức trung bình (OpenTelemetry, đã sửa trong 1.3.1). |

## Các thông báo cũ hơn vẫn liên quan tới mã 7.24

| Notice | Lỗi | Fasti BPM |
|---|---|---|
| 25 | Deserialize đối tượng Java qua biến Object | Java serialization tắt. Kiểm tra kiểu Spin JSON/XML bật trong image (mọi chế độ) và bundle Tomcat từ 1.3.1 (trước đó chỉ với `--production`); engine nhúng giữ mặc định của engine (tắt), hãy đặt `deserializationTypeValidationEnabled=true`. |
| 34, 40, 55 | DOCTYPE / external entity trong BPMN, DMN và Spin XML | Bị từ chối (đã kiểm thử ở 1.0 và 1.3). |
| 35 | Câu SQL trong thông báo lỗi | Thông báo lỗi không chứa SQL, stack trace hay đường dẫn (đã kiểm thử ở 1.3). |
| 48, 50, các notice về thư viện JS | XSS trong ứng dụng AngularJS, thiếu SameSite | Giao diện web mới viết bằng React (tên hiển thị dạng văn bản, CSP dùng nonce). Ứng dụng AngularJS tắt theo mặc định trong image và các bundle Tomcat, WildFly (ứng dụng Spring Boot nhúng vẫn phục vụ chúng trừ khi đặt `fasti.webapp.classic.enabled=false`); cookie phiên có SameSite. |
| 60, 75, 77 | H2 console trong các bản phân phối | Không triển khai và không phục vụ H2 console (driver H2 có đi kèm cho cơ sở dữ liệu dùng thử). |
| 85 | User đã xóa vẫn còn đăng nhập | Phiên web kiểm tra lại quyền sau tối đa 5 phút; đăng nhập Basic thành công được nhớ tối đa 30 giây trên mỗi node (`FASTI_REST_AUTH_CACHE_TTL=0` để tắt). |
| 99, 83, 57 | Mật khẩu admin, mật khẩu đã băm hoặc cấu hình LDAP xuất hiện trong log | Image truyền mật khẩu admin qua biến môi trường, không qua dòng lệnh, và không ghi mật khẩu vào log (đã kiểm thử ở 1.3). |

Không liên kết với Camunda Services GmbH; Camunda là nhãn hiệu của Camunda Services GmbH.
