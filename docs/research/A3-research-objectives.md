# A3 — Mục tiêu nghiên cứu

- **Phiên bản:** `A3-v0.3`
- **Trạng thái:** `DRAFT` — mục tiêu định tính đã được xác nhận; ngưỡng số chỉ được điền sau B9/B10
- **Người duyệt:** Lê Văn Minh — đã xác nhận `MT-1`–`MT-5` ở mức định tính; chưa chốt A3
- **Ngày duyệt:** —
- **Đầu vào:** `A1-context-and-urgency.md` và `A2-problem-statement.md` — baseline `APPROVED` ngày 2026-08-13; `A4-research-questions-draft.md` — baseline `APPROVED` ngày 2026-08-13
- **Đi vào báo cáo:** phần Mục tiêu nghiên cứu · **ràng buộc lên phần Đánh giá**
- **Ràng buộc:** không nhắc tên công nghệ, framework, thuật toán hay nhà cung cấp; không đặt mục tiêu cần điều kiện nhóm không có như người dùng thật hoặc hạ tầng lớn

> **Vì sao tài liệu này tồn tại và vì sao nó chỉ là `DRAFT`.** B9 khai đầu vào là “B8 và phiếu A3”, nhưng A3 chưa từng được tạo. Bản nháp này gỡ nút thắt đó. Theo `docs/quy-trinh-lam-viec.md`, A3–A6 chỉ được **chốt** ở Giai đoạn 3 cùng B9/B10, vì không thể viết một ngưỡng đo trước khi có kịch bản chất lượng và một vòng đo thử. Vì vậy bản này nêu **cách biết là đạt**, cố ý **không** điền con số.

## 1. Mục tiêu tổng quát

> Thiết kế và kiểm chứng một cách phối hợp vòng đời vé trong hệ thống đặt vé phân tán sao cho các bất biến cốt lõi được duy trì và hệ thống phục hồi về trạng thái chấp nhận được khi gặp yêu cầu đồng thời, thông điệp lặp hoặc lỗi từng phần, trong giới hạn hạ tầng của đồ án.

## 2. Mục tiêu cụ thể

| Mã | Mục tiêu cụ thể | Biết là đạt bằng |
|---|---|---|
| `MT-1` | Xác định và đặc tả tập bất biến cốt lõi của vòng đời vé, từ giữ chỗ và thanh toán tới phát hành và kiểm soát vào cửa | Mỗi bất biến truy được về một quyết định nghiệp vụ đã xác nhận, và có ít nhất một ca kiểm thử làm nó thất bại nếu cơ chế bảo vệ bị gỡ bỏ |
| `MT-2` | Thiết kế cách phối hợp trạng thái giữa các bước của vòng đời vé sao cho các bất biến ở `MT-1` vẫn giữ khi có yêu cầu gần đồng thời, thông điệp lặp và lỗi từng phần | Chạy được tập ca đồng thời, lặp và lỗi từng phần trên hệ thống đã dựng, ghi lại kết quả đạt hoặc không đạt cho từng bất biến |
| `MT-3` | Làm rõ đánh đổi giữa tính đúng đắn, khả năng phục hồi và chi phí hiệu năng của cách phối hợp được chọn | Thử tải có kiểm soát và tăng dần trên cấu hình được công bố, ghi độ trễ và thông lượng ở từng mức tải; ngưỡng cụ thể chốt ở B9/B10. Đây là kiểm thử hiệu năng/tải trong giới hạn đồ án, không phải tuyên bố mô phỏng lưu lượng sản xuất |
| `MT-4` | Bảo đảm hệ thống sinh đủ dấu vết vận hành để hỗ trợ việc xác định nguyên nhân một sự cố đã xảy ra; không cam kết loại bỏ việc tái hiện lỗi theo `PRJ-002` | Với tập ca lỗi có nguyên nhân đã biết, đo được tỷ lệ ca mà dấu vết thu được chứa đủ tín hiệu cần thiết để lần ra nguyên nhân |
| `MT-5` | Đánh giá mức hữu ích của việc hỗ trợ chẩn đoán tự động cho cùng tập ca lỗi đó — đây là nhánh hỗ trợ, không thay thế trục nhất quán vòng đời vé | Bộ tiêu chí được định nghĩa trước gồm: **danh sách nguyên nhân khả dĩ do trợ lý đề xuất có chứa nguyên nhân thật hay không**, tính hữu ích của bước kiểm tra được đề xuất, và số lần đưa ra bằng chứng hoặc hành động không có thật. Đây là phép đo trên tập giả thuyết, không coi trợ lý là bên kết luận nguyên nhân cuối cùng |

## 3. Phần cố ý không đặt thành mục tiêu

- Không đặt mục tiêu về số lượng người dùng thật hoặc lưu lượng sản xuất; nhóm không có điều kiện đó.
- Không đặt mục tiêu loại bỏ hoàn toàn việc tái hiện lỗi thủ công. `PRJ-002` đã giới hạn phạm vi hỗ trợ chẩn đoán ở mức thu thập, liên kết dấu vết và đề xuất nguyên nhân khả dĩ, không tự kết luận và không tự sửa.
- Không đặt mục tiêu so sánh toàn diện các mẫu kiến trúc. Việc so sánh chỉ diễn ra trong phạm vi các phương án được hình thành ở B11-A và giới hạn hạ tầng của đồ án.
- Không đặt mục tiêu về chất lượng giao diện hoặc trải nghiệm người dùng.

## 4. Ràng buộc lên phần Đánh giá

Mỗi mục tiêu ở §2 phải có một mục tương ứng trong kế hoạch kiểm chứng ở B15. Mục tiêu nào không có cách lấy bằng chứng ở cuối kỳ thì phải viết lại hoặc bỏ, không được giữ để tài liệu trông đầy đủ.

`MT-3`, `MT-4` và `MT-5` là ba mục tiêu bắt buộc có số. Ba con số đó chỉ được điền sau khi B9 dựng kịch bản chất lượng và B10 xếp ưu tiên; điền sớm thì hoặc quá dễ hoặc bất khả thi.

## 5. Việc còn phải làm

| ID | Việc | Gate |
|---|---|---|
| `A3-OPEN-01` | Điền ngưỡng đo cho `MT-3`, `MT-4`, `MT-5` | Sau B9 và B10 |
| `A3-OPEN-02` | Đối chiếu `MT-1`–`MT-5` với danh sách ASR để bảo đảm không có mục tiêu nào không phục vụ ASR nào | B10 |
| `A3-OPEN-03` | Tái kiểm tra A1, A2 và A3 cùng nhau, rồi chốt A3–A6 | Giai đoạn 3, và impact check lại tại B11-C |

## 6. Phép tự kiểm

- [x] Mục tiêu tổng quát viết trong một câu.
- [x] Năm mục tiêu cụ thể, nằm trong khoảng 3–5 mà phiếu A3 yêu cầu.
- [x] Mỗi mục tiêu có cột “biết là đạt bằng”, trả lời được câu hỏi *cuối kỳ lấy gì ra để chứng minh*.
- [x] Không có tên công nghệ, framework, thuật toán hoặc nhà cung cấp.
- [x] Mục tiêu liên quan tới hỗ trợ chẩn đoán viết ở mức lớp vấn đề và được đặt đúng vai trò nhánh hỗ trợ theo A4.
- [x] Không có mục tiêu cần người dùng thật hoặc hạ tầng lớn.
- [x] Chưa điền con số nào, đúng quy định chốt ngưỡng ở B9/B10.
- [x] Codex đã rà chéo A3 cùng B6/B8; `MT-3` và `NFR-04` dùng cùng cách hiểu về thử tải có kiểm soát.
- [x] Lê Văn Minh xác nhận cả năm mục tiêu định tính đúng ý đồ án (`RES-002`).
- [ ] Sau B9/B10, quay lại điền ngưỡng và chốt A3 cùng A1, A2, A4, A5, A6.

## 7. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A3-v0.3` | 2026-08-22 | Ghi nhận năm mục tiêu định tính đã được xác nhận; làm rõ `MT-3` bao gồm thử tải có kiểm soát và tăng dần, không đòi lưu lượng sản xuất; giữ A3 ở `DRAFT` cho tới khi B9/B10 chốt ngưỡng | Lan truyền quyết định nghiên cứu |
| `A3-v0.2` | 2026-08-21 | Đồng bộ với vòng rà chéo B8: `MT-4` không hứa bỏ tái hiện lỗi, `MT-5` nói rõ là phép đo trên tập giả thuyết chứ không coi trợ lý là bên kết luận | Sửa sau rà chéo |
| `A3-v0.1` | 2026-08-21 | Bản nháp đầu: mục tiêu tổng quát, năm mục tiêu cụ thể kèm cách kiểm chứng, phần cố ý không đặt thành mục tiêu, và ba việc còn treo chờ B9/B10 | Tạo mới |
