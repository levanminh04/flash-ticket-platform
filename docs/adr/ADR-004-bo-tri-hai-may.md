# ADR-004 — Bố trí mục tiêu trên hai máy

- Trạng thái: `Chấp nhận`
- Ngày: 2026-09-04
- Người quyết định: Lê Văn Minh (`GOV-090`, `GOV-096`–`GOV-098`)
- Gate: `B11-C`; vị trí datastore chờ `B12`
- Liên kết phương án: `PA-6`, `ADR-001`, `ADR-003`
- Tác động lên A1–A6: **Có — `A6-v0.6` đã ghi bố trí, ngân sách, không HA và quyền nâng instance; phần thay đổi do B11 đã được duyệt lại.**

## Bối cảnh

Nhóm có hai máy, mỗi máy 2 vCPU/8 GiB. Cần cách ly đường giao dịch nóng khỏi tải chẩn đoán nhưng vẫn giữ số hop hợp lý cho mua vé và Saga.

## Các phương án đã cân nhắc

- dàn đều service theo số lượng: đơn giản về đếm nhưng không phản ánh đường nóng;
- đặt RCA cùng mặt phẳng giao dịch: giảm kết nối nhưng cạnh tranh CPU/RAM;
- tách transaction plane và control/diagnosis plane.

## Quyết định

- Máy 1 — transaction plane: gateway, `booking-service`, `payment-service`, `ticket-service`, broker, cache.
- Máy 2 — control/diagnosis plane: `event-service`, `user-service`, Keycloak, config/discovery, adapter chatbot, collector/kho quan sát, RCA và lớp giải thích gọi Gemini API.

Datastore chỉ là placeholder; `B12` chốt quyền sở hữu và vị trí vật lý.

Mỗi máy 8 GiB dùng ngân sách mục tiêu khoảng 6 GiB cho tiến trình/container và phải công bố heap/memory limit (`GOV-097`). Không bổ sung máy dự phòng hoặc cam kết HA cho phạm vi đồ án/demo (`GOV-096`). Nếu cấu hình hiện tại thiếu trong lượt kiểm thử hoặc demo hữu hạn, nhóm nâng instance AWS tạm thời thay vì mở lại `PA-6` (`GOV-098`).

## Hệ quả

Tích cực: đường mua vé và Saga ở cùng máy; RCA được cách ly khỏi đường nóng; trách nhiệm vận hành rõ.

Tiêu cực: máy 1 có nguy cơ tập trung tải; máy 2 có nhiều thành phần nền; sự cố toàn máy vẫn ảnh hưởng nhiều trách nhiệm và được chấp nhận vì không có HA; cấu hình từ máy 2 sang đường giao dịch cần bản sao chịu lỗi.

## Cách kiểm chứng sau chấp nhận

- công bố heap/container limit và kiểm tổng ngân sách mục tiêu khoảng 6 GiB mỗi máy;
- thử tải hữu hạn ở Giai đoạn 5–6; nếu thiếu thì ghi cấu hình instance nâng tạm thời và chi phí, không tuyên bố cấu hình cũ đã đạt;
- kiểm đường mua thành công, callback lặp, phát hành lỗi và bù trừ;
- kiểm lỗi Gemini không chặn kết quả RCA cốt lõi;
- phần tác động B11 trong `A6-v0.6` đã được duyệt lại; sơ đồ không gán quyền sở hữu datastore trước `B12`.
