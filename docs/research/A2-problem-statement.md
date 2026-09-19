# A2 — Phát biểu vấn đề

- **Phiên bản:** `A2-v0.3`; cập nhật 2026-09-18.
- **Trạng thái:** `REVIEW_READY` — đã đồng bộ định hướng DT18; phần diễn giải cần Minh rà soát, không tự kế thừa phê duyệt phiên bản cũ.
- **Người duyệt:** —; **Ngày duyệt:** —.
- **Đầu vào:** [tên và nhiệm vụ hiện hành](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md) (`DT18-TEN`, `DT18-NV1`–`DT18-NV3`, `PRJ-025/026`); `PRJ-035` về mobile; các quyết định nghiệp vụ/kiến trúc còn hiệu lực.
- **Hiệu lực:** tên, nguyên văn nhiệm vụ và ưu tiên mobile là `USER_CONFIRMED`; các cách diễn đạt/ánh xạ bên dưới là `CANDIDATE` trừ phần dẫn rõ quyết định đã có. DH-* chỉ được dùng để truy lịch sử.

## Các trường cấu thành

| Trường | Nội dung |
|---|---|
| Chủ thể | Người mua, nhà tổ chức và người vận hành hệ thống bán vé |
| Hoàn cảnh | Giao dịch đi qua nhiều thành phần và chịu tải/đồng thời, xử lý lặp hoặc lỗi từng phần |
| Hậu quả cần kiểm soát | Sai lệch tồn kho/thanh toán, giao dịch thiếu ổn định, suy giảm đáp ứng và khó xác định vùng sự cố |
| Bài toán | Xây dựng hệ thống phân tán có kiểm chứng, liên kết dấu vết bằng đồ thị để giám sát và hỗ trợ chẩn đoán |

## BÁO CÁO — Phát biểu đề xuất

> Hệ thống bán vé phân tán cần giữ tồn kho và thanh toán nhất quán khi nhiều yêu cầu cùng xử lý, đồng thời duy trì khả năng đáp ứng và độ ổn định giao dịch. Khi xảy ra lỗi, bằng chứng nằm rải rác giữa các thành phần, gây khó khăn cho việc xác định vùng ảnh hưởng và nguyên nhân. Bài toán của đồ án là thiết kế, xây dựng và kiểm chứng hệ thống bán vé dưới nhiều mức tải và tình huống đồng thời; đồng thời mô hình hóa quan hệ phụ thuộc, ánh xạ dữ liệu vận hành để phát hiện bất thường và xếp hạng thành phần khả nghi. Cơ chế được thực nghiệm trên dữ liệu công khai trước khi thử trên hệ thống, với kết quả và hạn chế được đánh giá bằng độ đo phù hợp.

## Phép tự kiểm

- Phát biểu không chứa tên framework/nhà cung cấp và không vượt 150 từ theo cách tách khoảng trắng.
- Bao phủ nhiệm vụ xây dựng/kiểm thử, mô hình/giám sát/chẩn đoán và thứ tự thực nghiệm DT18.
- Không coi đồ thị phụ thuộc tự động là đồ thị nhân quả; không hứa kết luận nguyên nhân cuối cùng.
- `OPEN`: Minh duyệt bản diễn giải mới; phê duyệt A2-v0.2 chỉ áp cho bản lịch sử.

## Nhật ký phiên bản — lịch sử, không dùng làm ngữ cảnh hiện hành

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A2-v0.3` | 2026-09-18 | Đồng bộ tên/nhiệm vụ DT18, phạm vi xây dựng và đánh giá hệ thống, nhóm bốn người và mobile tùy thời gian; bản diễn giải được đưa về REVIEW_READY | Theo PRJ-025–035 và phạm vi GOV-146, chưa duyệt nội dung mới |
| `A2-v0.2` | 2026-08-26 | Tái baseline theo `DH-TEN`: phát biểu chuyển từ nhất quán vòng đời vé sang chẩn đoán nguyên nhân gốc bằng đồ thị phụ thuộc; hệ giao dịch giữ vai bối cảnh theo `DH-MT1`; gỡ ô tự kiểm mâu thuẫn với đề tài | Tái baseline (`RES-032`) |
| `A2-v0.1` | 2026-08-13 | Bản đầu, `APPROVED` theo trục nhất quán vòng đời vé | Tạo mới |
