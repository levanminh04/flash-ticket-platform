# Quy trình chẩn đoán sự cố hiện tại — B1

## Baseline đã biết

Khi có bug do khách hàng báo hoặc bug âm thầm, người xử lý mở Loki hoặc SSH vào máy Linux, cố tái hiện lỗi, đợi log xuất hiện để lấy tên lỗi rồi dò đoạn mã liên quan. Mô tả này là đầu vào ban đầu, chưa phải phép đo hoàn chỉnh.

## Bảng ghi nhận ca thực tế

| Mã ca | Loại lỗi | Tín hiệu bắt đầu | Bước đã thực hiện | Nguồn dữ liệu dùng | Điểm nghẽn | Thời gian | Nguyên nhân thật | Dữ liệu đã khử nhạy cảm? |
|---|---|---|---|---|---|---|---|---|
| INC-01 | Chưa ghi nhận |  |  |  |  |  |  |  |

## Quy tắc

- Ưu tiên 3–5 ca thật hoặc có thể tái hiện thuộc các lớp: lỗi âm thầm, ngoại lệ, CSDL và hạ tầng.
- Ghi thời gian theo từng bước nếu có thể; không ước lượng hồi tưởng rồi trình bày như số đo.
- Không đưa log thô có bí mật/dữ liệu cá nhân vào Git. Chỉ lưu đoạn đã masking hoặc đặc trưng cần thiết.
- Baseline này dùng để phát hiện điểm nghẽn và thiết kế phép đánh giá trợ lý, không dùng để hứa rằng AI sẽ sửa lỗi tự động.
