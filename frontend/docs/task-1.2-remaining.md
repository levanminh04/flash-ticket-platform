# Task 1.2 — Checklist còn lại

[Task ClickUp 1.2](https://app.clickup.com/t/z8qgvcbr2v) đang In progress. Đây là bản bổ sung **chưa lưu được lên ClickUp** vì công cụ báo hết hạn mức100/100; phần mô tả cũ trên ClickUp vẫn còn.

## Kết nối backend thật — phụ thuộc 1.1

- [ ] Chốt môi trường test và Gateway trong root `.env`. Hiện FE proxy `/api` qua `127.0.0.1:18081` tới Gateway8081 trong Docker; Keycloak gọi119. Không mặc định API nghiệp vụ đang đi thẳng119.
- [ ] Kiểm route Event detail, Booking availability, create order, promotion, cancel và instance đăng ký service discovery. Xử lý401 catalog ở1.1; xác định nguyên nhân timeout119:8082 đã quan sát.
- [ ] Có buyer thật với realm role BUYER và dữ liệu Event/Booking khớp UUID ticketType/sector/seat, cấu hình bán vé cùng voucher còn hiệu lực.

## Code/contract cần thống nhất thêm

- [ ] Chốt API đọc OrderView theo orderId, quyền sở hữu buyer và response lỗi. OrderController hiện chưa có GET đọc đơn; chưa coi endpoint đề xuất là endpoint đã có.
- [ ] Khi backend có contract thống nhất, nối checkout đọc trạng thái/giá/expiresAt khi mở link, reload hoặc quay lại; xử lý401/403/404/hết hạn. Receipt sessionStorage hiện chỉ là phản hồi gần nhất, không phải trạng thái mới nhất hoặc bằng chứng thanh toán. Khai báo Java mới vẫn qua gate workflow.
- [ ] Xác định ranh giới API lịch sử đơn với1.3; MyOrders/MyTickets legacy chưa được tính là đã tích hợp.

## Nghiệm thu bằng buyer và API thật

- [ ] QUANTITY: chọn số lượng → tạo đơn; đối chiếu items, tiền, state, expiresAt với Booking.
- [ ] STANDING: tổng nhiều loại vé cùng sector không vượt pool; SEATED: đúng UUID, cùng sector, chỉ ghế AVAILABLE; kiểm geometry/coordinates trên sơ đồ thật.
- [ ] Voucher: hợp lệ, không tồn tại, ngoài thời hạn, hết lượt, đã dùng; kiểm discount/total và lỗi giá khóa/order hết hạn.
- [ ] Double click, gửi lặp, mất phản hồi rồi retry/reload: cùng Idempotency-Key trả cùng orderId và chỉ giữ tồn kho một lần.
- [ ] Hai buyer tranh ghế/lượng vé cuối: chỉ tồn kho hợp lệ được giữ, FE báo conflict và tải lại.
- [ ] Hủy chủ động giải phóng đúng tồn kho/voucher; countdown/reload không tự gửi cancel; backend từ chối thao tác trái trạng thái.
- [ ] Kiểm desktop/mobile, email người nhận, lỗi mạng/token hết hạn; buyer khác không xem/thao tác được đơn.
- [ ] Lưu bằng chứng request/response đã che token và dữ liệu cá nhân, orderId/trạng thái/tồn kho trước-sau; cập nhật docs và ClickUp trước khi hoàn tất.

Payment/VNPay/phát vé thuộc1.3; E2E toàn luồng thuộc1.4. Build và25 nhóm kiểm tra fixture đã qua không thay nghiệm thu API thật.
