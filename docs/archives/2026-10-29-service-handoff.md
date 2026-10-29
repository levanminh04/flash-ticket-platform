# Trạng thái code bàn giao trên dev/son — 29/10/2026

- Booking giữ nguyên consumer cấu hình bán vé và API `POST /api/v1/orders`: idempotency, khóa snapshot/inventory, order/reservation/allocation, giữ chỗ 15 phút và lock timeout 5 giây. Chưa có worker trả lượt giữ hết hạn, áp voucher hoặc xử lý thanh toán/phát vé.
- Ticket giữ scaffold đã setup; chưa triển khai nghiệp vụ phát vé/QR/check-in.
- Payment, Event, User được user yêu cầu restore riêng thư mục service về `1b7fd74` của `levanminh04` vì thành viên khác đang phát triển ở branch riêng. Mỗi service chỉ còn application, Maven/YAML và test khởi động. Đây không phải kết luận các nghiệp vụ của ba service đã hoàn tất.
- Code cũ và thay đổi chưa commit được lưu ở branch `codex/archive-booking-event-user-20261029`: `cb5bed4` lưu Booking/Event/User, `70b43e6` bổ sung Payment. Booking đã được phục hồi nguyên trạng vào dev/son sau khi user đính chính phạm vi restore. Build output/file ignored chỉ lưu cục bộ, không đưa vào Git.
- Config Server giữ các cấu hình hạ tầng, bỏ tham chiếu UserLogFields/PaymentLogFields/SafeStackTracePrinter của User và Payment vì các class này không còn trong scaffold đã restore. Khi merge implementation của thành viên, đối chiếu lại YAML với code thực tế.
- Keycloak SPI gửi Envelope<UserCreatedV1> đủ 10 field; ví dụ tại `docs/contracts/identity/user-created-envelope-v1.example.json`. Image `flash-ticket/keycloak:26.8.0-identity-envelope-20261028` đã deploy lên 119 và kiểm discovery HTTP 200. Consumer User hiểu envelope nằm ở branch của người phụ trách; chưa kiểm xuyên suốt với consumer mới.
- Giới hạn kiểm chứng Booking: test thường và HTTP đã kiểm theo plan; D17/D20 chưa đạt timeout 5 giây qua tunnel 119, chỉ đạt khi tăng timeout test lên 30 giây. Không coi đây là bằng chứng toàn bộ concurrency/hiệu năng đã được nghiệm thu; xem §10 của plan create-order.
- Kiểm trước push ngày 29/10/2026: Maven test Booking đạt 348 lượt, SPI đạt 14 lượt, không failure/error. Đây là bộ test thường, không phải lần chạy mới của toàn bộ integration trên PostgreSQL.

Tài liệu triển khai nằm trong `docs/java-workflow/specs/`, `plans/`, `explanations/`, `tests/`. Docs Payment ở đây ghi lại bản đã lưu trữ, không mô tả application Payment hiện tại trên dev/son.
