# Đề tài và nhiệm vụ hiện hành — 2026-09-18

- Trạng thái: `USER_CONFIRMED` — Lê Văn Minh cung cấp và xác nhận trong cuộc trao đổi ngày 2026-09-18.
- Hiệu lực: từ ngày 2026-09-18. Đây là nguồn hiện hành cho tên đề tài và nhiệm vụ; không tự quy nội dung này thành thư/phê duyệt mới của giảng viên.
- Quyết định: `PRJ-025`, `PRJ-026`; phân công tại [roles.md](../../project/roles.md); ưu tiên mobile tại `PRJ-035` trong [sổ quyết định](../../project/decision-register.md).
- Bản [thư 2026-08-22](../advisor-direction/2026-08-22-dinh-huong-de-tai.md) và mã `DH-*` là bằng chứng lịch sử. Không dùng `DH-TEN` làm tên đề tài hiện hành.

## 1. Tên chính thức — `DT18-TEN`

Xây dựng hệ thống bán vé theo kiến trúc phân tán có ứng dụng đồ thị phụ thuộc để giám sát và chẩn đoán sự cố

## 2. Nhiệm vụ — nguyên văn

Đề tài tập trung thiết kế và xây dựng một hệ thống bán vé trực tuyến theo kiến trúc phân tán, mô phỏng các chức năng chính như quản lý sự kiện, quản lý loại vé, đặt vé, kiểm soát tồn kho và xử lý thanh toán. Hệ thống được kiểm thử trong nhiều mức tải và tình huống đồng thời nhằm đánh giá thông lượng, thời gian đáp ứng, tỷ lệ lỗi, khả năng mở rộng, tính nhất quán dữ liệu và độ ổn định của các giao dịch chính.

Trên cơ sở kiến trúc và luồng giao tiếp giữa các thành phần, đề tài xây dựng mô hình đồ thị phụ thuộc, trong đó các nút biểu diễn dịch vụ hoặc thành phần hệ thống, còn các cạnh biểu diễn quan hệ gọi hàm, trao đổi thông điệp hoặc phụ thuộc dữ liệu. Dữ liệu log, trace và metrics được thu thập trong quá trình vận hành và ánh xạ lên đồ thị để xác định vùng ảnh hưởng, phát hiện dấu hiệu bất thường và xếp hạng các thành phần có khả năng là nguyên nhân gốc của sự cố.

Phương pháp được nghiên cứu, thực nghiệm trước trên các bộ dữ liệu công khai phù hợp, sau đó áp dụng thử nghiệm trên hệ thống bán vé. Kết quả được đánh giá bằng các độ đo phù hợp cho bài toán phát hiện và chẩn đoán nguyên nhân, đồng thời phân tích các trường hợp phương pháp hoạt động hiệu quả hoặc còn hạn chế. Sản phẩm cuối cùng gồm hệ thống bán vé, mô hình đồ thị phụ thuộc, cơ chế hỗ trợ chẩn đoán sự cố và giao diện minh họa kết quả phân tích.

## 3. Mã dẫn nguồn

| Mã | Đoạn nguồn ở §2 |
|---|---|
| `DT18-NV1` | Đoạn 1: xây dựng hệ thống bán vé phân tán và đánh giá dưới tải/đồng thời |
| `DT18-NV2` | Đoạn 2: mô hình nút/cạnh; log, trace, metrics; vùng ảnh hưởng, bất thường và xếp hạng |
| `DT18-NV3` | Đoạn 3: thực nghiệm trên dữ liệu công khai trước, áp dụng trên hệ bán vé sau; đánh giá và sản phẩm cuối |

Các mã chỉ định vị nguyên văn, không thêm yêu cầu kỹ thuật hoặc ngưỡng nghiệm thu. Thiết kế chi tiết và kế hoạch đo dẫn về tạo tác sở hữu tương ứng.
