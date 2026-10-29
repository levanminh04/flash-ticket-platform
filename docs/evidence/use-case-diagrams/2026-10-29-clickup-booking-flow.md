# Đối chiếu nghiệp vụ với ảnh Đặt vé trên ClickUp — 2026-10-29

- USER_CONFIRMED: user yêu cầu “update lại lại nghiệp vụ cho tôi follow theo ảnh”. Hướng phối hợp dưới đây thay GOV-149/150, không tự duyệt tên route/DTO hay code mới.
- FACT: ảnh Đặt vé.png, 61 bước, 5559×5436, 778301 bytes; attachment `9ca572ac-b2fe-4f85-979f-212d7fe6a007.png` ở [impl luồng thanh toán](https://app.clickup.com/t/z8qgvc7vtn). Đã tải và đọc trực tiếp; ảnh khác bản 73 bước trong bằng chứng 03/10.
- SHA-256: `1a90001bddccb273120b282c10234b0d19f89b686320fd7730ee5e069710eeca`.
- Phạm vi: tài liệu nghiệp vụ/hợp đồng/sequence và mô tả task; không thay Java, schema, runtime hoặc tuyên bố E2E đã đạt.

| Bước ảnh | Luồng hiện hành | Transaction / kết quả |
|---|---|---|
| 1–7 | FE → Booking tạo order/giữ chỗ | order + reservation + allocations cùng commit; trả PENDING_PAYMENT, orderId, expiresAt |
| 8–14 | FE → Booking áp voucher (tùy chọn) | Kiểm mã, giữ promotion_usage HELD, cập nhật tiền cùng commit; trả chi tiết mới |
| 15–16 | User xác nhận; FE → Payment POST payment(orderId) | Payment là nơi nhận yêu cầu bắt đầu thanh toán |
| 17–21 | Payment → Booking lấy PurchaseSnapshot | Booking đọc DB của mình và trả; Payment không truy DB Booking |
| 22–26 | Payment lưu snapshot + PaymentAttempt, commit, trả dữ liệu thanh toán cho FE | FE hiển thị QR; không phải Booking trả 202 rồi phát command khởi tạo |
| 27–33 | User thanh toán; cổng webhook → Payment | Xác minh, ghi charge/attempt SUCCEEDED và Saga WAITING_BOOKING, commit trước phản hồi |
| 34–38 | Payment → Booking AcceptPayment | Booking khóa đơn, chấp nhận reservation/cập nhật inventory và trạng thái, commit rồi xác nhận; giữ trạng thái ISSUING trước phát đủ vé |
| 39–42 | Payment ghi confirmation + WAITING_TICKETS; gửi Ticket IssueTickets | Dùng PurchaseSnapshot đã lưu |
| 43–46 | Ticket tạo vé cùng transaction, commit rồi báo Payment | Không coi ACK này là order/Saga đã COMPLETED |
| 47–51 | Payment → Booking thông báo phát vé thành công | Booking commit order COMPLETED rồi xác nhận Payment |
| 52–54 | Payment cập nhật Saga COMPLETED | Chỉ sau xác nhận Booking |
| 55–61 | FE → Ticket đọc vé theo orderId | GET chỉ đọc, kiểm chủ đơn |

## Quy tắc giữ nguyên và điểm chưa được ảnh quyết định

- Giữ ownership, idempotency, không bán vượt tồn, deadline Booking, không gia hạn giữ chỗ, retry không lặp hiệu ứng. Ảnh chỉ happy path; không bỏ expiry/cancel/hoàn muộn/FAILED.
- Không có payment.start-requested từ Booking trong flow này; không bắt buộc FE poll thay cho response tạo thanh toán.
- B13-OPEN-01: URL/chuỗi QR/ảnh QR và cách gọi adapter cổng. Ảnh trả QR từ lifeline DB không có nghĩa DB tự tạo QR hoặc đã chốt provider API.
- B13-OPEN-02: cơ chế freeze, thời điểm snapshot/version, chống áp mã đồng thời khi Payment lấy snapshot. Không tự gắn write/freeze vào GET; phải chốt trong spec trước triển khai.
- ACK trong ảnh là xác nhận giao tiếp sau commit; khi dùng RabbitMQ, broker ACK không tự cập nhật Saga ở service khác. Cơ chế result/xác nhận bền là chi tiết kỹ thuật cần đối chiếu khi lập plan.

## Tác động

B13-v0.3, B14-v0.3, B15-v0.3 và B14-01 đã đồng bộ; readiness/chỉ mục/trạng thái có cảnh báo thay thế hướng 03/10. Bằng chứng cũ và GOV-149/150 giữ nguyên lịch sử; GOV-156 ghi quyết định mới. Không lan phê duyệt baseline thành phê duyệt toàn bộ phiên bản mới. Kiểm tra tài liệu tĩnh không phải kiểm chứng runtime.
