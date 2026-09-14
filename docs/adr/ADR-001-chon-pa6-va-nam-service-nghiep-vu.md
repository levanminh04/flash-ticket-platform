# ADR-001 — Chọn PA-6 và năm service nghiệp vụ

- Trạng thái: `Chấp nhận`
- Ngày: 2026-09-04
- Người quyết định: Lê Văn Minh (`GOV-086`, `GOV-087`, `GOV-094`)
- Gate: `B11-C`
- Nguồn phương án: `B11-A-v0.5` `APPROVED`
- Nguồn kiểm khả thi: `B11-B-v0.6` `APPROVED`
- Tác động lên A1–A6: **Có — `A6-v0.6` đã đồng bộ bố trí, ngân sách và phạm vi nâng instance; phần thay đổi do B11 đã được duyệt lại. `A1`–`A5` không đổi.**

## Bối cảnh

Tập `PA-1`–`PA-6` đã được hình thành độc lập và duyệt. Không phương án nào bị kiểm khả thi loại. Kiến trúc phải giữ không quá tám service nghiệp vụ, không quá ba Saga và chạy trên hai máy 2 vCPU/8 GiB.

## Các phương án đã cân nhắc

- `PA-1`–`PA-4`: các mức gộp/tách đã duyệt trong `B11-A`.
- `PA-5`: bảy ranh giới và đường cắt giữ chỗ–đơn còn khoảng trống mô hình.
- `PA-6`: năm ranh giới; giữ trọn năng lực mua vé và cam kết nguồn cung; tiền gộp cả đối soát.

## Quyết định

Chọn `PA-6` với năm service:

1. `event-service`;
2. `booking-service`;
3. `payment-service`;
4. `ticket-service`;
5. `user-service`.

Chatbot là kênh. RCA, gateway, Keycloak, config/discovery và hạ tầng quan sát không phải service nghiệp vụ.

## Động lực

- năm bất biến của mua vé giữ cục bộ trong `booking-service`;
- không phải sửa mô hình để hợp thức hóa đường cắt giữ chỗ–đơn;
- chỉ một bất biến xuyên ranh giới đã biết (`INV-11`);
- năm service nằm dưới trần và chừa tài nguyên cho hạ tầng/RCA;
- một đường Saga bắt buộc có ranh giới rõ.

## Hệ quả

Tích cực: mô hình dễ giải thích; giảm phối hợp trên đường tranh chấp mua vé; số tiến trình nghiệp vụ vừa sức nhóm.

Tiêu cực: `booking-service` là điểm nóng lớn; cấu hình thương mại phải sao chép có phiên bản; giao dịch mua vẫn đi qua ba service và đòi correlation đầy đủ.

## Cách kiểm chứng và theo dõi sau chấp nhận

- công bố heap/container limit và giữ ngân sách mục tiêu khoảng 6 GiB mỗi máy theo `GOV-097`;
- kiểm `QS-01`, `QS-03`, `QS-04` và `QS-06` ở phạm vi service mới;
- thử tải thật ở Giai đoạn 5–6; nếu cấu hình thiếu thì nâng instance tạm thời theo `GOV-098`, không tự đổi ranh giới;
- xác nhận phần tác động B11 của `A6-v0.6` đã được duyệt lại;
- kiểm sơ đồ/ledger có đúng năm service và không chốt schema trước `B12`.
