# A2 — Phát biểu vấn đề

- **Phiên bản:** `A2-v0.2`
- **Trạng thái:** `APPROVED`
- **Người duyệt:** Lê Văn Minh · bản `A2-v0.1` đã được duyệt ngày 2026-08-13 theo trục cũ
- **Ngày duyệt:** 2026-08-27, sau `A1-v0.2` (`GOV-033`)
- **Đầu vào:** [`docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`](../evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md) — nguyên văn, `FACT`; `A1-context-and-urgency.md`
- **Đi vào báo cáo:** Phần Đặt vấn đề, sau A1
- **Ràng buộc:** Không quá 150 từ và không chứa tên công nghệ

> **Vì sao có `A2-v0.2`.** Bản `v0.1` phát biểu bài toán quanh **nhất quán vòng đời vé**, và ô tự kiểm của nó ghi *"nhánh chẩn đoán được đặt ở vai trò hỗ trợ vận hành, không trở thành đề tài độc lập thứ hai"*. `DH-TEN` đặt tên đề tài là *"Chẩn đoán nguyên nhân gốc sự cố giao dịch trực tuyến bằng đồ thị phụ thuộc"* — chẩn đoán **là** đề tài. Bản này tái baseline theo `RES-032`.
>
> Vòng đời vé **không bị loại khỏi phát biểu**. `DH-MT1` gọi đích danh *"các dịch vụ đặt vé, thanh toán, xác thực, cơ sở dữ liệu, API đối tác"*, nên nó là **hệ giao dịch được mô hình hóa**, xuất hiện trong phát biểu với vai bối cảnh và đối tượng áp dụng.

## Các trường cấu thành

| Trường | Nội dung |
|---|---|
| Chủ thể chịu ảnh hưởng | Người vận hành và nhà phát triển phải xác định nguyên nhân một sự cố; qua đó là người mua vé và nhà tổ chức chịu hậu quả khi sự cố kéo dài |
| Hệ quả | Thời gian xác định nguyên nhân kéo dài; phải tái hiện lỗi mới có bằng chứng; tri thức xử lý nằm trong đầu người trực, khó tái sử dụng |
| Hoàn cảnh | Một giao dịch đi qua nhiều thành phần — đặt vé, thanh toán, xác thực, cơ sở dữ liệu, API đối tác — và dấu vết của nó nằm rải rác, không có mã liên kết thống nhất |
| Nguyên nhân kỹ thuật gốc | Quan hệ phụ thuộc giữa các thành phần không được mô hình hóa, nên bằng chứng thu được không chỉ ra được thành phần nào là nguồn của sự cố |

## BÁO CÁO — Phát biểu đề xuất

> Trong một hệ giao dịch trực tuyến, một giao dịch đi qua nhiều thành phần: đặt vé, thanh toán, xác thực, cơ sở dữ liệu và giao tiếp với bên thứ ba. Khi có sự cố, dấu vết của cùng một giao dịch nằm rải rác giữa các thành phần và không cho biết thành phần nào là nguồn. Người trực phải tái hiện lỗi, dò thủ công qua nhiều nguồn dữ liệu, và kết luận phụ thuộc kinh nghiệm cá nhân. Bài toán của đồ án là mô hình hóa quan hệ phụ thuộc giữa các thành phần thành một đồ thị, ánh xạ dấu vết vận hành lên đồ thị đó, rồi đề xuất và đánh giá một cơ chế lan truyền và xếp hạng để chỉ ra nguyên nhân gốc dựa trên bằng chứng thay vì suy đoán.

## Phép tự kiểm

Các ô dưới đây được suy lại theo nội dung hiện tại của `A2-v0.2`.

- [x] Dưới giới hạn 150 từ theo cách tách bằng khoảng trắng.
- [x] Không có tên framework, dịch vụ đám mây, mẫu kiến trúc, thuật toán hoặc nhà cung cấp.
- [x] Nêu rõ chủ thể, hậu quả, hoàn cảnh và nguyên nhân gốc.
- [x] Phát biểu khớp `DH-TEN` và bốn mục tiêu `DH-MT1`–`DH-MT4`: mô hình hóa đồ thị, ánh xạ dấu vết, cơ chế lan truyền và xếp hạng.
- [x] Hệ giao dịch được nêu bằng đúng năm nhóm thành phần mà `DH-MT1` liệt kê, không thu hẹp thành riêng nghiệp vụ bán vé.
- [x] Không tuyên bố cơ chế nào là mới; phát biểu dừng ở mức bài toán.
- [x] Ô tự kiểm cũ *"nhánh chẩn đoán… không trở thành đề tài độc lập thứ hai"* đã được **gỡ** — nó mâu thuẫn trực tiếp với `DH-TEN`.
- [x] Lê Văn Minh đã duyệt `A2-v0.2` ngày 2026-08-27 (`GOV-033`), sau `A1-v0.2`.

## Kết quả xác nhận

Bản `A2-v0.1` được chủ đồ án duyệt ngày 2026-08-13 và giữ hiệu lực cho tới `RES-031`. Nội dung nghiệp vụ mà nó mô tả — bán vượt vé, trạng thái thanh toán và phát hành không khớp, check-in trùng — **không bị bác bỏ**; chúng chuyển thành **tiêu chí nghiệm thu sản phẩm** của hệ thống được mô hình hóa, ghi tại `A6` §2.1.

Nếu B9/B10 hoặc phản hồi của giảng viên làm đổi nghĩa phát biểu này, A2 phải được tái kiểm tra theo phase gate.

## Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A2-v0.2` | 2026-08-26 | Tái baseline theo `DH-TEN`: phát biểu chuyển từ nhất quán vòng đời vé sang chẩn đoán nguyên nhân gốc bằng đồ thị phụ thuộc; hệ giao dịch giữ vai bối cảnh theo `DH-MT1`; gỡ ô tự kiểm mâu thuẫn với đề tài | Tái baseline (`RES-032`) |
| `A2-v0.1` | 2026-08-13 | Bản đầu, `APPROVED` theo trục nhất quán vòng đời vé | Tạo mới |
