# Architecture Decision Records

Mỗi quyết định kiến trúc quan trọng được lưu trong một tệp `ADR-<3 chữ số>-<chu-de-khong-dau>.md`.

Trạng thái hợp lệ: `Đề xuất`, `Chấp nhận`, `Không dùng nữa`, `Bị thay thế bởi ADR-XXX`.

Không sửa ngầm kết luận của ADR đã chấp nhận. Khi thay đổi quyết định, tạo ADR mới và liên kết hai bản. Mỗi ADR phải nêu phương án đã cân nhắc, hệ quả tích cực/tiêu cực, ASR hoặc yêu cầu được phục vụ và cách kiểm chứng.

## Cổng đối với ADR kiến trúc đích

`ADR-000` là quyết định phương pháp về việc dùng ADR nên không phải ADR kiến trúc đích và không chịu cổng B11-C dưới đây.

Với ADR kiến trúc đích:

1. Không tạo hoặc chấp nhận ADR kiến trúc trước Giai đoạn 4.
2. Chỉ chuyển trạng thái sang `Chấp nhận` tại B11-C khi `docs/architecture/B11-A-independent-alternatives.md` và `docs/architecture/B11-B-legacy-feasibility.md` đều đã được người thật duyệt `APPROVED`.
3. ADR phải liên kết đúng phương án B11-A, phiên bản B11-A mà B11-B đã đối chiếu và kết quả khả thi tương ứng. B5.5 không được ghi như nguồn sinh phương án kiến trúc.
4. Trước khi chấp nhận, ADR phải có trường `Tác động lên A1–A6: Không | Có — <tạo tác và lý do>`. Nếu có tác động đến phát biểu vấn đề, mục tiêu, phạm vi, câu hỏi nghiên cứu hoặc cách đánh giá, cập nhật và duyệt lại tạo tác liên quan cùng các đầu vào phụ thuộc trước khi chấp nhận ADR.
