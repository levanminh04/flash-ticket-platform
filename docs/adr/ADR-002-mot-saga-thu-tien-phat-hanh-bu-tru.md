# ADR-002 — Một Saga thu tiền–phát hành–bù trừ

- Trạng thái: `Chấp nhận`
- Ngày: 2026-09-04
- Người quyết định: Lê Văn Minh (`GOV-088`, `GOV-095`)
- Gate: `B11-C`; chi tiết hợp đồng/sequence tại `B13`/`B14`
- Liên kết phương án: `PA-6`, `ADR-001`
- Tác động lên A1–A6: **Không — mục tiêu và phạm vi nghiên cứu không đổi.**

## Bối cảnh

Sau khi tiền đã được cổng thanh toán xác nhận, phát hành vé có thể thất bại. Rollback cục bộ không thể hoàn tác tiền đã thu. Không phải mọi giao dịch phân tán đều cần Saga, và trần của đồ án là ba.

## Các phương án đã cân nhắc

- không dùng Saga, dựa vào giao dịch cục bộ: không xử lý được tiền đã thu;
- một Saga cho thu tiền–phát hành–bù trừ;
- thêm Saga cho hủy sự kiện hoặc giữ chỗ–đơn: không có nhu cầu bắt buộc trong `PA-6`; hủy sự kiện là phản ứng một chiều có thử lại tiến.

## Quyết định

Dùng đúng một Saga: thu tiền → phát hành vé → nếu phát hành thất bại thì hoàn tiền và trả tài nguyên/lượt dùng khuyến mãi.

Đặt trách nhiệm điều phối tại `payment-service` (`GOV-095`). Đây là nơi biết tiền đã thực sự được thu và sở hữu tiến trình hoàn tiền. `booking-service` chỉ sở hữu giữ chỗ/đơn và `ticket-service` chỉ sở hữu phát hành vé, nên hai service đó không được tự kết luận trạng thái tiền. Điều phối viên chỉ giữ trạng thái phối hợp và gửi lệnh/sự kiện; nó không ghi trực tiếp dữ liệu của service khác.

ADR này không chốt state machine, payload, topic, timeout, outbox/inbox hay idempotency key.

## Hệ quả

Tích cực: bù trừ tập trung quanh sự thật “tiền đã thu”; không tiêu hết trần Saga; loại bỏ cách hiểu hủy sự kiện là Saga.

Tiêu cực: điều phối viên phải bền vững trước retry/trùng/lệch thứ tự; trạng thái hoàn tiền có thể kéo dài; cần bằng chứng xuyên ba service.

## Cách kiểm chứng sau chấp nhận

- `B13` chứng minh hợp đồng idempotent và không có vòng gọi đồng bộ;
- `B14` chứng minh mọi bước có đường tiến hoặc bù trừ, kể cả callback lặp và sự cố giữa chừng;
- kiểm số Saga đích vẫn đúng 1.
