# 5 tình huống người dùng Payment — 02/11/2026

Payment JAR đã sửa + HTTP + PostgreSQL/RabbitMQ local thật; Booking/OAuth/Ticket/VNPay mô phỏng, không chuyển tiền thật; không phải UAT ba service.

Kết quả: 5/5 pass, 0 fail. Thời gian UTC: 2026-11-02T15:19:26.360Z → 2026-11-02T15:19:59.960Z.

| Case | Tình huống | Kết quả | Quan sát |
|---|---|---|---|
| S01 | Thanh toán thành công qua các bước xác nhận | PASS | HTTP200, amount180000; callback00; đúng key/version; Saga COMPLETED sau Booking xác nhận |
| S02 | Bấm thanh toán lặp với cùng key | PASS | Hai POST đồng thời và một retry đều HTTP200, chỉ một attempt/URL |
| S03 | Người khác thanh toán đơn không thuộc mình | PASS | HTTP404; không tạo attempt/charge |
| S04 | Thanh toán đơn đã hết hạn | PASS | HTTP409; không tạo attempt/charge |
| S05 | Booking lỗi hoặc trả JSON snapshot không hợp lệ | PASS | Cả Booking503 và JSON sai đều HTTP503; không tạo attempt |

Các ca dùng UUID/email/credential test tổng hợp. JWT được che. JSON cạnh file này lưu request/response và DB state. Saga là dữ liệu truy vấn DB, không phải field API GET Payment. Broker confirm không thay cho xác nhận nghiệp vụ.

Payment được khởi động bằng env-file gốc với cấu hình scenario local; block cấu hình một lần được gỡ khỏi .env sau khi chạy. Không đổi code production để chạy scenario, không sửa119.
