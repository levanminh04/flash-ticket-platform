# Nguồn sơ đồ

Đặt `.puml`, `.dsl` và tài sản nguồn có thể tái sinh tại đây. Ảnh xuất hoặc tệp Visual Paradigm đặt ở thư mục cha khi cần.

## Thiết kế dữ liệu và luồng chính — 2026-09-14

- `B12-01`–`B12-04`: ERD 47 bảng thuộc bốn owner, chỉ chọn cột chính. Đường nối là FK nội bộ, không khẳng định bội số nghiệp vụ. Chi tiết đầy đủ 467 cột/NULL/default/constraint/index ở [B12 §12](../../architecture/B12-data-ownership-and-schema.md).
- `B12-05`: bốn collection User, giữ organizer_profiles và unique follow pair.
- `B14-01`: tạo đơn → thanh toán → phát vé.
- `B14-02`: hết hạn, hoàn tiền, hủy event.
- `B14-03`: gửi vé và check-in/audit replay.

Nguồn hành vi là [B13](../../architecture/B13-api-and-event-contracts.md)/[B14](../../architecture/B14-main-flow-sequences.md); ảnh là dẫn xuất, không thay hợp đồng. Render local vào Temp, không gửi dữ liệu lên renderer ngoài. Kết quả chạy và giới hạn ghi tại [B12-validation](../../architecture/B12-validation.md).
