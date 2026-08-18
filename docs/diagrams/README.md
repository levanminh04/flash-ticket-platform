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
