# A1 — Bối cảnh và tính cấp thiết

- **Phiên bản:** `A1-v0.2`
- **Trạng thái:** `APPROVED`
- **Người duyệt:** Lê Văn Minh · bản `A1-v0.1` đã được duyệt ngày 2026-08-13 theo trục cũ
- **Ngày duyệt:** 2026-08-27, duyệt đầu chuỗi `A1 → A2 → A4 → B2 → … → B9` (`GOV-033`)
- **Đầu vào:** [`docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`](../evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md) — nguyên văn, `FACT`; B1 khảo sát công khai ngày 2026-08-09; B1 baseline chẩn đoán; các nguồn kỹ thuật trong `source-register.md`
- **Đi vào báo cáo:** Phần Đặt vấn đề và Tính cấp thiết

> **Vì sao có `A1-v0.2`.** Mạch **bốn nước** của bản `v0.1` **giữ được gần như nguyên vẹn** — nó vốn đã kết ở khó khăn chẩn đoán. Thứ phải đổi là **đích đến**: `v0.1` dùng ba nước đầu để dẫn tới *nhất quán vòng đời vé*, còn `DH-TEN` đặt đích ở *chẩn đoán nguyên nhân gốc bằng đồ thị phụ thuộc*. Tái baseline theo `RES-032`.
>
> **Mạch hiện tại ở §1 có NĂM nước**, không phải bốn: `v0.2` chèn thêm nước 4 về khoảng trống từ dấu vết tới nguyên nhân. Ghi rõ ở đây vì `report-outline.md` dẫn đúng *"mạch năm nước"* trong khi câu trên nói *"bốn nước"* — hai con số nói về hai bản khác nhau, dễ đọc thành mâu thuẫn.

## 1. Mạch lập luận

### Nước 1 — Hệ giao dịch trực tuyến có hậu quả trực tiếp khi sai

Vòng đời một vé đi qua khả dụng, giữ chỗ, thanh toán, phát hành và check-in. Khảo sát các luồng công khai cho thấy nền tảng bán vé phải biểu diễn các trạng thái như hết vé, giữ chỗ có thời hạn, thanh toán chưa hoàn tất, vé điện tử được phát hành sau thanh toán và vé đã sử dụng. Nếu các trạng thái này không phối hợp đúng, người mua và nhà tổ chức chịu hậu quả thật: bán vượt số lượng, trạng thái thanh toán và phát hành không khớp, hoặc một vé được chấp nhận nhiều lần.

Đây là lý do một hệ đặt vé là **bối cảnh đáng nghiên cứu** cho bài toán chẩn đoán: sự cố ở đây có hậu quả đo được, không chỉ là chỉ số kỹ thuật.

### Nước 2 — Kiến trúc phân tán làm sự cố khó truy nguyên

Khi trách nhiệm và dữ liệu được phân chia giữa nhiều thành phần, một giao dịch nghiệp vụ không còn nằm gọn trong một tiến trình. Yêu cầu gửi lại, thông điệp lặp, lỗi mạng hoặc một thành phần dừng giữa chừng khiến các bước hoàn tất không đồng đều. Các mẫu như idempotency, transactional outbox và Saga đã được công bố để giữ tính đúng đắn, nhưng chính chúng làm đường đi của một giao dịch **dài hơn và gián đoạn hơn** — và vì thế khó lần lại hơn khi có sự cố.

### Nước 3 — Dấu vết rời rạc là điểm nghẽn thật, đã đo được

Baseline chẩn đoán của nhóm ghi lại quy trình hiện tại qua tám bước. Điểm nghẽn xuất hiện ngay ở bước 1: **thiếu mã giao dịch thống nhất để bắt đầu tìm**. Kiểm kê tài sản logging cho thấy không tìm thấy `trace_id`, `correlation_id`, `span_id` hay MDC trong bốn ứng dụng được kiểm. Hệ quả là người trực phải **tái hiện lỗi** rồi chờ log xuất hiện, và dò thủ công từ thông điệp về vùng mã.

Theo [OpenTelemetry](https://opentelemetry.io/docs/concepts/observability-primer/), log có thể được liên kết với trace và span để dựng lại đường đi của một yêu cầu; nghiên cứu [Drain](https://doi.org/10.1109/ICWS.2017.13) cho thấy thông điệp log có thể được gom thành mẫu trực tuyến để máy xử lý được. Nhưng cả hai đều chỉ hoạt động trong phạm vi chất lượng của dữ liệu đầu vào.

### Nước 4 — Từ dấu vết tới nguyên nhân còn một khoảng trống

Có đủ dấu vết chưa đồng nghĩa với biết nguyên nhân. Khi một giao dịch chạm hàng chục thành phần, danh sách nghi phạm vẫn dài. Khoảng trống này là chỗ **đồ thị phụ thuộc** vào cuộc: nếu quan hệ giữa các thành phần được mô hình hóa, tín hiệu bất thường có thể được lan truyền theo chiều phụ thuộc để xếp hạng nghi phạm, thay vì để người trực đoán.

Đây chính là đề tài mà giảng viên hướng dẫn đặt ngày 2026-08-22, với bốn mục tiêu: dựng mô hình đồ thị phụ thuộc cho hệ giao dịch; ánh xạ log giao dịch và dấu vết vận hành lên đồ thị; đề xuất cơ chế lan truyền và xếp hạng; và tích hợp lớp AI hỗ trợ giải thích.

### Nước 5 — Giá trị của đồ án nằm ở thiết kế có lập luận và bằng chứng

Đây là đồ án xây dựng và kiểm chứng, không phải tuyên bố tìm ra bài toán chưa từng được giải. Giới nghiên cứu đã công bố nhiều phương pháp chẩn đoán; giá trị cần bảo vệ gồm: một hệ giao dịch thật với ranh giới và bất biến được suy ra từ miền nghiệp vụ; một mô hình đồ thị mà mỗi cạnh truy được về bằng chứng; một cơ chế được đánh giá **cùng điều kiện** với các phương pháp đã công bố; và việc công bố trung thực giới hạn suy rộng. Không cần dựng một "khoảng trống nghiên cứu" từ lịch sử mã nguồn hoặc khẳng định tính mới toàn cầu.

## 2. BÁO CÁO — Bản nháp đề xuất

Hệ thống giao dịch trực tuyến không kết thúc tại thao tác tạo đơn. Một hành trình hoàn chỉnh đi qua các trạng thái khả dụng, giữ chỗ, thanh toán, phát hành và kiểm soát tại cổng, và trách nhiệm cho những trạng thái đó nằm ở nhiều thành phần khác nhau: dịch vụ đặt vé, dịch vụ thanh toán, dịch vụ xác thực, cơ sở dữ liệu và giao tiếp với bên thứ ba. Khảo sát các luồng công khai của Ticketbox, TicketGo và Eventbrite cho thấy các trạng thái như hết vé, chỗ đang được giữ, đơn chưa hoàn tất và vé đã sử dụng đều xuất hiện trong vận hành thực tế, và sai lệch giữa chúng ảnh hưởng trực tiếp tới quyền lợi người mua và nhà tổ chức.

Chính sự phân tán đó làm việc xác định nguyên nhân một sự cố trở nên tốn kém. Tài liệu của AWS về [idempotent API](https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/) và [transactional outbox](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html) mô tả các cơ chế cần thiết để giữ tính đúng đắn khi một thao tác được gửi lại hoặc khi cập nhật dữ liệu tách rời khỏi việc phát thông báo. Những cơ chế này giữ được tính đúng đắn, nhưng đồng thời kéo dài và cắt khúc đường đi của một giao dịch, khiến dấu vết của nó nằm rải rác giữa nhiều thành phần và nhiều thời điểm.

Khảo sát quy trình chẩn đoán hiện tại của nhóm cho thấy điểm nghẽn nằm ngay ở bước đầu tiên: không có mã giao dịch thống nhất để bắt đầu tìm kiếm. Người phát triển phải cố tái hiện lỗi, mở hệ thống log tập trung hoặc truy cập máy chủ, rồi lần từ thông điệp về đoạn mã liên quan. Theo [OpenTelemetry](https://opentelemetry.io/docs/concepts/observability-primer/), log có thể được liên kết với trace và span để dựng lại trình tự của một yêu cầu, và nghiên cứu [Drain](https://doi.org/10.1109/ICWS.2017.13) cho thấy log có thể được gom thành mẫu để máy xử lý. Tuy vậy, ngay cả khi dấu vết đã đầy đủ, danh sách thành phần khả nghi vẫn dài, vì bản thân dấu vết không nói thành phần nào là nguồn của sự cố.

Từ đó, đồ án mô hình hóa quan hệ phụ thuộc giữa các thành phần của hệ giao dịch thành một đồ thị, ánh xạ log giao dịch và dấu vết vận hành lên đồ thị đó để phát hiện bất thường, rồi đề xuất một cơ chế lan truyền và xếp hạng nguyên nhân gốc dựa trên bằng chứng thu được. Cơ chế được đánh giá cùng điều kiện với các phương pháp đã công bố, trên cùng bộ dữ liệu và cùng bộ độ đo. Kết quả được trình bày kèm mức sàn ngẫu nhiên, điều kiện chạy và giới hạn suy rộng, để giá trị của đồ án được bảo vệ bằng lập luận và số đo thay vì bằng tuyên bố tính mới.

## 3. Phép tự kiểm

Các ô dưới đây được suy lại theo nội dung hiện tại của `A1-v0.2`.

- [x] Xóa tên công nghệ, mạch *"giao dịch đi qua nhiều thành phần → dấu vết rời rạc → danh sách nghi phạm vẫn dài → cần mô hình phụ thuộc"* vẫn còn nguyên.
- [x] Đích của mạch là `DH-TEN`, không còn là nhất quán vòng đời vé.
- [x] Hệ đặt vé xuất hiện đúng vai `DH-MT1` — hệ giao dịch được mô hình hóa — chứ không bị loại khỏi bối cảnh.
- [x] Không dùng lịch sử repository làm bối cảnh, tính cấp thiết hoặc khoảng trống nghiên cứu.
- [x] Không tuyên bố tính mới toàn cầu; nước 5 nói rõ giới nghiên cứu đã công bố nhiều phương pháp.
- [x] Quan sát sản phẩm và nguồn kỹ thuật được phân biệt.
- [x] Con số về logging dẫn về baseline chẩn đoán B1, không tự bịa.
- [x] Chưa đưa ngưỡng hiệu năng nào.
- [x] Lê Văn Minh đã duyệt `A1-v0.2` ngày 2026-08-27 (`GOV-033`).

## 4. Kết quả xác nhận và vấn đề còn mở

1. **Bản `A1-v0.1`** được duyệt ngày 2026-08-13 và giữ hiệu lực tới `RES-031`. Nội dung nghiệp vụ mà nó nêu không bị bác bỏ; nó chuyển từ **đích của mạch** thành **bối cảnh của mạch**.
2. **Đã thay đổi:** `PRJ-001` từng chốt tên **"trợ lý chẩn đoán sự cố"** cho lớp AI. Tên đó đã bị thu hồi ngày 2026-08-27 (`RES-034`) cùng với thiết kế trợ lý cũ. Năng lực giải thích của `DH-MT4` nay là **bước cuối của cơ chế chẩn đoán**, đặc tả ở `docs/research-rca/`.
3. **`OPEN`:** mẫu trích dẫn của khoa chưa được ban hành. Tạm giữ URL/DOI làm dấu vết nguồn; chuẩn hóa sau khi nhận mẫu, không chặn phân tích.

## 5. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A1-v0.2` | 2026-08-26 | Tái baseline theo `DH-TEN`. Giữ mạch bốn nước và bổ sung nước 4 về khoảng trống từ dấu vết tới nguyên nhân; đích của mạch chuyển sang chẩn đoán bằng đồ thị phụ thuộc; hệ đặt vé giữ vai hệ giao dịch được mô hình hóa theo `DH-MT1`; viết lại §2 | Tái baseline (`RES-032`) |
| `A1-v0.1` | 2026-08-13 | Bản đầu, `APPROVED` theo trục nhất quán vòng đời vé | Tạo mới |
