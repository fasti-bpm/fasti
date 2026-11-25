[English](personal-data.md) | Tiếng Việt

# Dữ liệu cá nhân và thời gian lưu

Fasti BPM lưu những gì quy trình của bạn đưa vào. Trang này cho biết dữ liệu cá nhân nằm ở đâu, lưu
bao lâu, và cần đặt gì để không lưu lâu hơn mức cần (ví dụ theo GDPR). Trang mô tả engine; dữ liệu
nào đi vào là do quy trình của bạn quyết định.

## Dữ liệu cá nhân được lưu ở đâu

Các nơi chính (các bảng lịch sử khác, như quyết định, job và external task, cũng có thể chứa user
id và giá trị):

| Dữ liệu | Bảng | Được xóa khi |
|---|---|---|
| Tài khoản người dùng (id, tên, e-mail, hash mật khẩu có salt), nhóm, thành viên | `ACT_ID_*` | xóa user trong Admin hoặc qua REST API; không dùng khi đăng nhập một lần lấy user từ token |
| Process instance: business key, người bắt đầu | `ACT_RU_EXECUTION` khi đang chạy; `ACT_HI_PROCINST`, `ACT_HI_ACTINST` sau đó | dọn lịch sử |
| Ai làm việc gì: người được giao, người ứng viên, chủ của task | `ACT_RU_TASK`, `ACT_RU_IDENTITYLINK` khi đang chạy; `ACT_HI_TASKINST`, `ACT_HI_IDENTITYLINK` sau đó | task kết thúc; dọn lịch sử |
| Biến quy trình, business key, dữ liệu biểu mẫu | `ACT_RU_VARIABLE` khi đang chạy; `ACT_HI_VARINST`, `ACT_HI_DETAIL` sau đó; giá trị lớn trong `ACT_GE_BYTEARRAY` | instance kết thúc; dọn lịch sử |
| Bình luận và tệp đính kèm | `ACT_HI_COMMENT`, `ACT_HI_ATTACHMENT`, `ACT_GE_BYTEARRAY` | dọn lịch sử |
| Ai đã làm gì (user operation log) | `ACT_HI_OP_LOG` | dọn lịch sử |
| Bản ghi bốn mắt: ai đã hoàn thành bước được kiểm tra | biến quy trình `fasti_completedBy_<bước>` | như các biến khác |
| Audit trail (nếu bật) | `FASTI_AUDIT_EVENT`, `FASTI_AUDIT_HEAD` | **không** bị dọn lịch sử xóa; dùng purge của audit (`POST /audit/purge`, xem [audit trail](../audit-trail.vi.md)) |
| Vắng mặt và người thay (nếu bật) | user info `ACT_ID_INFO`; biến task `fasti_absentAssignee`, `fasti_absenceSubstitute` | xóa lịch vắng mặt; dọn lịch sử |

Process analytics tính từ lịch sử ở trên và chỉ giữ một cache ngắn trong bộ nhớ
(`FASTI_ANALYTICS_CACHE_TTL`, mặc định 60 giây); không tự lưu gì. Các dòng log của chính Fasti
có user id (ví dụ khi đăng nhập sai) nhưng không có mật khẩu hay giá trị biến; ngoại lệ do delegate
và script của bạn ném ra được ghi nguyên văn. Fasti không gửi telemetry.

## Không giữ lịch sử lâu hơn mức cần

Lịch sử chỉ tự động bị xóa bởi **dọn lịch sử (history cleanup)**, và chỉ khi có đặt thời gian lưu:

1. Đặt thời gian lưu lịch sử cho mọi định nghĩa quy trình và quyết định, trong mô hình
   (`camunda:historyTimeToLive="P180D"` hoặc số ngày) hoặc cho mọi deployment bằng thuộc tính engine
   `historyTimeToLive`. Mặc định engine từ chối deploy định nghĩa không có thời gian lưu
   (`enforceHistoryTimeToLive`); định nghĩa deploy trước Camunda 7.20 có thể không có.
2. Đặt khung giờ dọn, ví dụ 22:00–06:00, bằng thuộc tính engine `historyCleanupBatchWindowStartTime`
   và `historyCleanupBatchWindowEndTime` (image: một file gắn vào và khai trong `FASTI_CONFIG`, xem
   chú thích trong `fasti-docker.yml`; bundle Tomcat chỉ đặt giờ bắt đầu 00:01 trong `bpm-platform.xml`, nên khung giờ kéo dài gần cả
   ngày). Không có
   khung giờ thì chỉ dọn khi chạy bằng tay.
3. Kiểm tra việc dọn có chạy: `GET /history/cleanup/configuration` cho biết khung giờ,
   `GET /history/cleanup/jobs` cho biết các job dọn; bảng lịch sử phải ngừng tăng khi các instance
   đầu tiên quá thời gian lưu.

Image và bundle Tomcat không tự đặt thời gian lưu: phải lưu (hoặc được lưu) bao lâu tùy nghĩa vụ
của bạn. Xem [tinh chỉnh khi chạy](../tuning-runtime.vi.md) về chiến lược thời điểm xóa và đánh đổi
của nó.

## Xóa dữ liệu của một người

Không có công tắc "quên người này". User id là chuỗi thường nằm trong nhiều bảng lịch sử. Để xóa dữ
liệu của một người trước hạn: xóa user; xóa hoặc ẩn danh các process instance chứa dữ liệu của họ
(`DELETE /history/process-instance/{id}` xóa lịch sử của một instance đã kết thúc); lưu ý audit trail, nếu bật,
giữ bản ghi cho tới khi purge theo ngày (`POST /audit/purge`), vì nó được thiết kế để không thể xóa
từng bản ghi. Hạn chế đưa dữ liệu cá nhân vào biến; hãy lưu một tham chiếu tới hệ thống của bạn,
nơi giữ dữ liệu và việc xóa nó ở một chỗ.

## Khuyến nghị

- Lưu tham chiếu chứ không lưu tài liệu trong biến; không bao giờ lưu mật khẩu hay số thẻ ở đó.
- Đặt thời gian lưu lịch sử cho mọi định nghĩa và một khung giờ dọn.
- Chọn mức lịch sử theo nhu cầu: `activity` không giữ biến, `audit` giữ giá trị cuối của biến mà
  không có nhật ký thay đổi, `full` giữ mọi thay đổi.
- Chỉ cấp `READ_HISTORY` cho người cần: quyền này cũng cho xem bình luận, tệp đính kèm và analytics
  của quy trình đó.

Không liên kết với Camunda Services GmbH; Camunda là nhãn hiệu của Camunda Services GmbH.
