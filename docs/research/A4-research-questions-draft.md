# A4 — Câu hỏi nghiên cứu

- **Phiên bản:** `A4-v0.3`
- **Trạng thái:** `APPROVED` — **nội dung được duyệt là `v0.2`.** `v0.3` chỉ sửa **một câu khai sai về nơi chốt ngưỡng** ở mục *Kết quả xác nhận* điểm 3; **không câu hỏi nghiên cứu nào đổi một chữ**. Nếu Lê Văn Minh coi đây là thay đổi vật chất thì trả về `REVIEW_READY`
- **Người duyệt:** Lê Văn Minh · bản `A4-v0.1` đã được duyệt ngày 2026-08-13 theo trục cũ
- **Ngày duyệt:** 2026-08-27, sau `A2-v0.2` (`GOV-033`)
- **Đầu vào:** [`docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`](../evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md) — nguyên văn, `FACT`; `A2-problem-statement.md` (`A2-v0.2`)
- **Đi vào báo cáo:** Chỉ đưa vào nếu mẫu báo cáo hoặc giảng viên thấy câu hỏi nghiên cứu giúp mạch đánh giá rõ hơn
- **Nguyên tắc:** Một câu hỏi trung tâm, tối đa hai câu phụ, không chứa tên công nghệ

> **Vì sao có `A4-v0.2`.** Bản `v0.1` đặt câu trung tâm quanh **bất biến vòng đời vé**, và câu phụ 2 ghi *"đây là nhánh hỗ trợ, không thay thế trục nhất quán vòng đời vé"*. `DH-TEN` đặt chẩn đoán làm đề tài. Tái baseline theo `RES-032`.

## Câu hỏi trung tâm

> Trên một hệ giao dịch trực tuyến, một cơ chế lan truyền và xếp hạng trên đồ thị phụ thuộc — dựng từ quan hệ giữa các thành phần và nuôi bằng dấu vết vận hành — chỉ ra được nguyên nhân gốc của một sự cố ở mức chính xác nào, so với các phương pháp đã công bố trên cùng bộ dữ liệu và cùng bộ độ đo?

### Bằng chứng cần có

- Điểm của cơ chế theo bộ độ đo xếp hạng, tính trên toàn bộ ca lỗi của bộ dữ liệu đã chọn.
- Điểm của tập phương pháp đối chứng, chạy **cùng bộ, cùng độ đo**.
- Cột đối chứng ngẫu nhiên để biết mức sàn ứng với số ứng viên.
- Tỷ lệ ca dựng được đồ thị, kèm quy mô đồ thị thu được.

## Câu hỏi phụ 1

> Việc thêm nguồn dữ liệu — dấu vết, chỉ số, log giao dịch — thay đổi chất lượng xếp hạng ở mức nào so với dùng một nguồn?

**Phục vụ câu trung tâm:** trả lời trực tiếp giả thuyết nằm sau `DH-MT2`, rằng ánh xạ log và dấu vết lên đồ thị làm việc phát hiện bất thường tốt hơn.

**Bằng chứng:** chênh lệch điểm giữa biến thể đơn nguồn và biến thể đa nguồn **cùng lõi thuật toán**, trên cùng bộ dữ liệu — cách duy nhất tách bạch được ảnh hưởng của nguồn dữ liệu khỏi ảnh hưởng của thuật toán.

## Câu hỏi phụ 2

> Hệ giao dịch cần sinh ra những dấu vết nào để một cơ chế chẩn đoán dựa đồ thị hoạt động được trên nó?

**Phục vụ câu trung tâm:** câu trung tâm đo cơ chế trên dữ liệu đã công bố; câu này hỏi điều kiện để đưa cơ chế đó về chạy trên hệ thống của nhóm.

**Bằng chứng tối thiểu:** đặc tả trường bắt buộc của một dòng log giao dịch; quy tắc một span thành một cạnh; tỷ lệ giao dịch mẫu dựng lại được trình tự đầy đủ chỉ từ dấu vết. Các điều kiện còn thiếu được ghi `OPEN` kèm gate, không được coi là đã đạt.

## BÁO CÁO — Cách sử dụng

Nếu giữ A4, phần Kết luận phải trả lời lần lượt câu trung tâm và hai câu phụ bằng số liệu ở chương Đánh giá.

**Giới hạn phải công bố:** số của câu trung tâm và câu phụ 1 thu trên **bộ dữ liệu đã công bố**, không thu trên hệ thống của nhóm. Không suy rộng thành khẳng định về hiệu quả chẩn đoán trên chính hệ đó cho tới khi có nhãn nguyên nhân thật sinh từ hệ đó.

## Kết quả xác nhận và vấn đề còn mở

1. **Bản `v0.1` được duyệt ngày 2026-08-13** và giữ hiệu lực tới `RES-031`. Câu hỏi cũ về bất biến vòng đời vé **không bị bác bỏ** — chúng chuyển thành tiêu chí nghiệm thu sản phẩm, ghi tại `A6` §2.1 và đo bằng các kịch bản `B9`.
2. **`OPEN` về trình bày:** chờ giảng viên hoặc mẫu ĐATN xác nhận có cần một mục *"Câu hỏi nghiên cứu"* riêng hay chỉ trình bày dưới dạng mục tiêu và nội dung đánh giá.
3. **`OPEN` về ngưỡng:** chưa chốt ngưỡng định lượng. `B9` và `B10` **đã xong và đã được duyệt** ngày 2026-08-27/28 — chúng xác định **phép đo và cách kiểm chứng**, nhưng **cố ý không gắn con số**: ngưỡng là đầu ra của **vòng đo thử đầu tiên**, không phải của hai tài liệu đó (`B9-OPEN-01` vẫn mở sau khi `B9` được duyệt). *Sửa 2026-08-29: câu cũ ghi "`B9`–`B10` xác định phép đo **và ngưỡng**", tức khai sai nơi con số được chốt.*

## Phép tự kiểm

- [x] Một câu trung tâm, đúng hai câu phụ.
- [x] Không tên công nghệ, framework, thuật toán hay nhà cung cấp.
- [x] Câu trung tâm khớp `DH-MT1`–`DH-MT3`; câu phụ 1 khớp `DH-MT2`; câu phụ 2 là điều kiện đưa cơ chế về hệ nhà.
- [x] Mỗi câu có bằng chứng cần thu, không câu nào chỉ là phát biểu định tính.
- [x] Cụm *"nhánh hỗ trợ, không thay thế trục nhất quán vòng đời vé"* đã được **gỡ**.
- [x] Không tuyên bố tính mới; câu trung tâm hỏi **mức chính xác so với phương pháp đã có**, không hỏi có vượt trội không.
- [x] Giới hạn suy rộng được nêu trong mục *Cách sử dụng*.
- [x] Lê Văn Minh đã duyệt `A4-v0.2` ngày 2026-08-27 (`GOV-033`), sau `A1-v0.2` và `A2-v0.2`.

## Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A4-v0.3` | 2026-08-29 | Sửa một câu khai sai ở *Kết quả xác nhận* điểm 3: `B9`/`B10` xác định **phép đo**, không chốt **ngưỡng** — ngưỡng là đầu ra của vòng đo thử đầu tiên, và `B9-OPEN-01` vẫn mở sau khi `B9` được duyệt. Câu cũ viết trước khi hai gate đó đóng, nên khai nhầm nơi con số được chốt. **Ba câu hỏi nghiên cứu không đổi một chữ** | Sửa lời khai gate |
| `A4-v0.2` | 2026-08-26 | Tái baseline theo `DH-TEN`: câu trung tâm chuyển sang độ chính xác của cơ chế xếp hạng trên đồ thị phụ thuộc; câu phụ 1 đo ảnh hưởng của việc thêm nguồn dữ liệu; câu phụ 2 hỏi điều kiện đưa cơ chế về hệ nhà; gỡ cụm "nhánh hỗ trợ" | Tái baseline (`RES-032`) |
| `A4-v0.1` | 2026-08-13 | Bản đầu, `APPROVED` theo trục bất biến vòng đời vé | Tạo mới |
