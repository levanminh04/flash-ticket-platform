# Sơ đồ

Tệp nguồn sơ đồ thay đổi thường xuyên được đặt trong `src/`. Nháp bằng PlantUML hoặc Structurizr DSL; chỉ dựng lại trên Visual Paradigm khi sơ đồ đã đủ ổn định để đưa vào báo cáo.

Tên tệp theo quy ước `<mã-chương>-<loại>-<chủ-đề>`. Không khóa mã chương cho tới khi nhận mẫu báo cáo chính thức; trong giai đoạn nháp có thể dùng mã nội bộ mô tả loại và chủ đề.

## B3 — Biểu đồ hoạt động có swimlane

Bốn tệp nguồn bắt buộc của B3 nằm trong `src/`:

- `B3-01-event-lifecycle.puml`
- `B3-02-order-payment-ticket.puml`
- `B3-03-refund-reconciliation-payout.puml`
- `B3-04-online-check-in.puml`

Các sơ đồ này mô tả tác nhân và kết quả nghiệp vụ, không biểu diễn service, schema, Saga hoặc giao thức. Chỉ dựng lại trên Visual Paradigm/đánh số theo chương sau khi B3 ổn định và mẫu báo cáo được xác nhận.

## B5 — Biểu đồ gói cho Bản đồ Bounded Context

Nguồn PlantUML của bản đồ đã duyệt nằm trong `src/`:

- `B5-01-bounded-context-map.puml`

Mỗi package trong sơ đồ là một **bounded context ứng viên**, không mặc nhiên là service hoặc schema vật lý. Đường nét đứt chỉ biểu diễn phụ thuộc ngữ nghĩa đã được truy vết trong B5 §5.1; không được đọc thành API, topic, Saga hay hướng gọi đồng bộ. Bản Visual Paradigm chỉ cần dựng khi sơ đồ được chọn đưa vào báo cáo.
