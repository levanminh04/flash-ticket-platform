# A5 — Đối tượng nghiên cứu

- **Phiên bản:** `A5-v0.4`; cập nhật 2026-09-18.
- **Trạng thái:** `REVIEW_READY` — đã đồng bộ định hướng DT18; phần diễn giải cần Minh rà soát, không tự kế thừa phê duyệt phiên bản cũ.
- **Người duyệt:** —; **Ngày duyệt:** —.
- **Đầu vào:** [tên và nhiệm vụ hiện hành](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md) (`DT18-TEN`, `DT18-NV1`–`DT18-NV3`, `PRJ-025/026`); `PRJ-035` về mobile; các quyết định nghiệp vụ/kiến trúc còn hiệu lực.
- **Hiệu lực:** tên, nguyên văn nhiệm vụ và ưu tiên mobile là `USER_CONFIRMED`; các cách diễn đạt/ánh xạ bên dưới là `CANDIDATE` trừ phần dẫn rõ quyết định đã có. DH-* chỉ được dùng để truy lịch sử.

## 1. Đối tượng, sản phẩm và bối cảnh

| Khái niệm | Nội dung diễn giải từ DT18 |
|---|---|
| Đối tượng nghiên cứu | Cơ chế phối hợp và các thuộc tính của giao dịch bán vé phân tán dưới tải/đồng thời; mô hình phụ thuộc và cách dùng dữ liệu vận hành để giám sát, phát hiện và hỗ trợ chẩn đoán sự cố |
| Sản phẩm/phương tiện | FlashTicket; mô hình đồ thị phụ thuộc; cơ chế hỗ trợ chẩn đoán; giao diện minh họa kết quả phân tích; công cụ thực nghiệm trên dữ liệu công khai phù hợp |
| Bối cảnh áp dụng | Hệ thống bán vé trực tuyến kiến trúc phân tán, giao dịch đi qua nhiều dịch vụ/thành phần |

Đối tượng được phát biểu bằng cơ chế/thuộc tính cần kiểm chứng, không chỉ điền tên sản phẩm. Hệ thống vừa là đầu ra xây dựng trực tiếp của DT18-NV1, vừa là nơi áp dụng đồ thị và chẩn đoán; không thu hẹp nó thành bối cảnh chỉ cần chạy được.

## 2. Nguồn và quyền quyết định

DT18-NV1 quy định chức năng và các mặt đánh giá hệ thống; DT18-NV2 quy định ý nghĩa nút/cạnh và log/trace/metrics; DT18-NV3 quy định trình tự thực nghiệm và bốn sản phẩm cuối. B2–B8 cung cấp mô hình miền, yêu cầu và bất biến; B11–B16 sở hữu triển khai, hợp đồng và chuẩn quan sát. Bộ RCA nhận mô hình qua cửa hệ thống → nghiên cứu, không tự thay ranh giới đã duyệt.

## 3. Nơi lấy bằng chứng

Phương pháp được nghiên cứu và thực nghiệm trước trên bộ công khai phù hợp, rồi thử trên FlashTicket. Đánh giá xếp hạng cần nguyên nhân thật; nhãn có thể đến từ dataset hoặc ca lỗi FlashTicket được kiểm soát, không phải chỉ dataset công khai mới có thể có nhãn. Mỗi kết quả khai rõ nguồn dữ liệu, phạm vi, môi trường và giới hạn suy rộng.

## 4. Điểm mở và kiểm tra

- `A5-OPEN-03`: xác nhận của giảng viên về bản diễn giải đối tượng/phạm vi hiện hành; không tự suy rằng cô đã duyệt từ lời xác nhận của Minh.
- Minh cần review A5-v0.4; A5-v0.3 đã duyệt là bản lịch sử.
- Không chốt thêm thuật toán, số node, bộ dữ liệu, ngưỡng hoặc kiến trúc trong phiếu này.

## Nhật ký phiên bản — lịch sử, không dùng làm ngữ cảnh hiện hành

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A5-v0.4` | 2026-09-18 | Đồng bộ tên/nhiệm vụ DT18, phạm vi xây dựng và đánh giá hệ thống, nhóm bốn người và mobile tùy thời gian; bản diễn giải được đưa về REVIEW_READY | Theo PRJ-025–035 và phạm vi GOV-146, chưa duyệt nội dung mới |
| `A5-v0.3` | 2026-08-26 | Đóng `A5-OPEN-01` theo `RES-032`: `A2-v0.2` và `A4-v0.2` đã tái baseline, chuỗi `B5`–`B8` đã lan truyền | Đóng điểm mở |
| `A5-v0.2` | 2026-08-25 | Viết lại sau khi thư định hướng được lưu nguyên văn. **FlashTicket chuyển từ ô khách thể sang ô phương tiện** theo `DH-MT1`; bỏ §3 cũ vốn lập luận FlashTicket không phải phương tiện; thêm bảng neo bốn mục tiêu vào ba ô; đóng `A5-OPEN-02` | Sửa sau khi có bằng chứng gốc |
| `A5-v0.1` | 2026-08-24 | Bản đầu, viết khi chưa có thư trong repo | Tạo mới |
