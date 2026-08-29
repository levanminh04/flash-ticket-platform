# A5 — Đối tượng nghiên cứu

- **Phiên bản:** `A5-v0.3`
- **Trạng thái:** `APPROVED` — Lê Văn Minh duyệt ngày 2026-08-29 (`GOV-054`). `A5-OPEN-03` **vẫn mở** và **không chặn**: nó thuộc giảng viên, xử lý ở mốc báo cáo hai tuần
- **Người duyệt:** Lê Văn Minh
- **Ngày duyệt:** 2026-08-29 (`GOV-054`), duyệt riêng — **không** kèm `A3` và `A6`
- **Đầu vào:** [`docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`](../evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md) — nguyên văn, `FACT`; Phiếu A5 tại `docs/tang-a-phuong-phap-nghien-cuu.md` §4
- **Đi vào báo cáo:** Chương Mở đầu — mục *Đối tượng và phạm vi nghiên cứu*, đặt ngay trước `A6`
- **Ràng buộc:** chỉ khai **nghiên cứu cái gì**. Không chọn phương pháp, không chốt bộ dữ liệu, không chốt kiến trúc.

> **Vì sao có `A5-v0.2`.** Bản `v0.1` được viết khi thư định hướng chưa có trong repo, và đã đọc sai một điểm cốt lõi: nó xếp FlashTicket vào ô **khách thể** và lập luận rằng hệ thống của nhóm không phải phương tiện nghiên cứu. `DH-MT1` bác bỏ điều đó — mục tiêu đầu tiên của đề tài là *"Xây dựng mô hình đồ thị phụ thuộc cho hệ thống giao dịch trực tuyến (các dịch vụ **đặt vé**, thanh toán, xác thực, cơ sở dữ liệu, API đối tác)"*. Bản này sửa lại.

---

## 1. Ba ô theo mẫu Tầng A

```
Đối tượng nghiên cứu:   cơ chế chẩn đoán nguyên nhân gốc sự cố giao dịch trực
                        tuyến bằng đồ thị phụ thuộc — gồm cách dựng đồ thị, cách
                        ánh xạ log giao dịch và trace lên đồ thị, và cách lan
                        truyền rồi xếp hạng nguyên nhân trên đồ thị đó

Phương tiện nghiên cứu: FlashTicket Platform — hệ giao dịch trực tuyến do nhóm
(sản phẩm tạo ra)       xây, là hệ được mô hình hóa ở DH-MT1 và là nơi cơ chế
                        được tích hợp; kèm chương trình thực nghiệm chạy trên bộ
                        dữ liệu ca lỗi đã công bố để đo cơ chế

Khách thể:              lớp hệ giao dịch trực tuyến kiến trúc microservice, nơi
(bối cảnh áp dụng)      một giao dịch đi qua nhiều thành phần và dấu vết của nó
                        nằm rải rác
```

### 1.1 Phép thử của Tầng A

Phiếu A5 quy định: *"nếu ô 'đối tượng nghiên cứu' điền tên hệ thống của bạn thì sai — đó là sản phẩm, không phải đối tượng."*

Ô đối tượng ở trên không chứa tên hệ thống nào. FlashTicket nằm ở ô **phương tiện**, đúng vai mà Tầng A định nghĩa: *"Sản phẩm là phương tiện để nghiên cứu đối tượng đó, ghi riêng."*

---

## 2. Phân định ba khái niệm hay bị gộp

| Câu hỏi | Trả lời | Cách kiểm chứng |
|---|---|---|
| Đồ án **nghiên cứu** cái gì? | Cơ chế chẩn đoán nguyên nhân gốc bằng đồ thị phụ thuộc | Điểm của cơ chế theo bộ độ đo xếp hạng, so với các giải pháp đã có trên cùng bộ dữ liệu (`DH-DO`) |
| Đồ án **xây** ra cái gì? | FlashTicket Platform, và cơ chế chẩn đoán tích hợp vào nó | Hệ thống chạy được; cơ chế chạy được trên đồ thị dựng từ hệ đó |
| Đồ án đặt bài toán **trong bối cảnh** nào? | Hệ giao dịch trực tuyến kiến trúc microservice | Không phải đối tượng đo; là điều kiện của bài toán |

**Hệ quả cho khối phân tích nghiệp vụ đã làm.** `B2`–`B8` không phải phần phụ và không phải "phạm vi sản phẩm" tách khỏi nghiên cứu. Chúng là **nguồn dựng đồ thị của `DH-MT1`**: `B4` cho các tương tác có bằng chứng, `B5` §5.1 cho bảng bằng chứng từng cạnh, `B7` cho bất biến. Không có khối đó thì mục tiêu 1 không có gì để dựng lên.

---

## 3. Bốn mục tiêu của đề tài neo vào ô nào

| Mục tiêu | Neo vào ô nào của §1 |
|---|---|
| `DH-MT1` — xây mô hình đồ thị phụ thuộc cho hệ giao dịch trực tuyến | **Phương tiện**: đồ thị dựng từ chính FlashTicket |
| `DH-MT2` — ánh xạ log giao dịch và trace lên đồ thị | **Đối tượng**: đây là một phần của cơ chế |
| `DH-MT3` — đề xuất cơ chế lan truyền và xếp hạng | **Đối tượng**: đây là lõi của cơ chế |
| `DH-MT4` — tích hợp AI hỗ trợ giải thích | **Phương tiện**: lớp diễn giải đặt trên đầu ra của cơ chế |

Bảng này cho thấy đối tượng và phương tiện **đan vào nhau chứ không tách rời**: cơ chế được thiết kế cho hệ thống, và hệ thống là nơi cơ chế chạy.

---

## 4. Nơi lấy số để đánh giá

Cơ chế chạy trên FlashTicket, nhưng **số để so sánh lấy từ bộ dữ liệu ca lỗi đã công bố**. Đây không phải mâu thuẫn — đó là đúng điều `DH-DATA` yêu cầu: *"Tìm những bộ dữ liệu đã được công bố để **thực nghiệm giải pháp**."*

Lý do kỹ thuật: chỉ bộ công bố mới có **nhãn nguyên nhân thật** cho từng ca lỗi. Không có đáp án thì không tính được độ đo xếp hạng. Chi tiết ở `A6` và ở `docs/research-rca/R0-boi-canh-va-rang-buoc.md` §2.

Lộ trình tích hợp lên FlashTicket đi theo ba mức, ghi tại `A6`: mô hình hóa → chạy thật trên hệ nhà → chấm điểm trên hệ nhà.

---

## 5. Vấn đề `OPEN`

| ID | Vấn đề | Chủ sở hữu | Gate xử lý |
|---|---|---|---|
| `A5-OPEN-03` | Cô chưa xác nhận cách hiểu ở §2 và §3; báo cáo hai tuần là nơi trình bày để cô chỉnh | Giảng viên hướng dẫn | Sau mốc `DH-MOC` |

`A5-OPEN-02` của bản `v0.1` đã đóng: câu hỏi *"có nâng FlashTicket lên vai trò phương tiện nghiên cứu không"* được `DH-MT1` trả lời trực tiếp.

`A5-OPEN-01` đã đóng theo `RES-032`: nó ghi việc `A2` và `A4` còn đặt chẩn đoán ở vai *"nhánh hỗ trợ"*, không khớp `DH-TEN`. Hai phiếu đã được tái baseline thành `A2-v0.2` và `A4-v0.2`, và chuỗi `B5`–`B8` đã khai lại đầu vào theo đó — đúng hệ quả mà `A5-OPEN-01` dự báo, nay đã thực hiện chứ không còn treo.

---

## 6. Phép tự kiểm

- [x] Ô "đối tượng nghiên cứu" không chứa tên hệ thống của nhóm — đạt phép thử Tầng A.
- [x] Mỗi khẳng định về ý cô đều dẫn về một mã `DH-*` trong tạo tác nguyên văn, không dẫn về trí nhớ.
- [x] Phân biệt rõ đối tượng, phương tiện, khách thể; nói rõ chúng đan vào nhau.
- [x] Ghi rõ `B2`–`B8` là nguồn của `DH-MT1`, không phải phần phụ.
- [x] Giải thích vì sao số đánh giá lấy từ bộ công bố mà không mâu thuẫn với việc cơ chế chạy trên FlashTicket.
- [x] Không sửa `A1`, `A2`, `A4`, `B2`–`B8`.
- [x] Không chọn phương pháp, không chốt bộ dữ liệu, không tuyên bố tính mới.
- [ ] Cô xác nhận cách hiểu.
- [x] Lê Văn Minh duyệt. **Đã duyệt ngày 2026-08-29** (`GOV-054`); AI không tự tích ô này (`GOV-011`).

---

## 7. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A5-v0.3` | 2026-08-26 | Đóng `A5-OPEN-01` theo `RES-032`: `A2-v0.2` và `A4-v0.2` đã tái baseline, chuỗi `B5`–`B8` đã lan truyền | Đóng điểm mở |
| `A5-v0.2` | 2026-08-25 | Viết lại sau khi thư định hướng được lưu nguyên văn. **FlashTicket chuyển từ ô khách thể sang ô phương tiện** theo `DH-MT1`; bỏ §3 cũ vốn lập luận FlashTicket không phải phương tiện; thêm bảng neo bốn mục tiêu vào ba ô; đóng `A5-OPEN-02` | Sửa sau khi có bằng chứng gốc |
| `A5-v0.1` | 2026-08-24 | Bản đầu, viết khi chưa có thư trong repo | Tạo mới |
