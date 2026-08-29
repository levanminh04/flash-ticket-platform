# A9 — Độ đo thực nghiệm theo quy chuẩn

- **Phiên bản:** `A9-v0.2`
- **Trạng thái:** `DRAFT` — khảo sát phục vụ báo cáo hai tuần; chưa được duyệt
- **Người duyệt:** — (chờ Lê Văn Minh)
- **Ngày duyệt:** —
- **Đầu vào:** `A7-khai-niem-rca.md` (**`A7-v0.2`** — lời khai này trước 2026-08-29 ghi `v0.1`, một phiên bản cũ hơn bản đang có; `A7-v0.2` sửa §1 từ *"ba vai"* thành **bốn vai**, tức có đổi nội dung chứ không chỉ sửa câu chữ); nguồn `T-06`, `T-08` trong `source-register.md`; thư định hướng của giảng viên ngày 2026-08-22, mục 5
- **Đi vào báo cáo:** phần Phương pháp — độ đo đánh giá; và phần Đánh giá kết quả
- **Ràng buộc:** tài liệu này định nghĩa độ đo và cách đọc. Nó **không** gắn ngưỡng mục tiêu — theo Tầng B, ngưỡng chỉ chốt sau B9/B10.

> Tài liệu này trả lời nửa đầu mục 5 trong thư định hướng: *"Khảo sát các độ đo thực nghiệm theo qui chuẩn để thực nghiệm so sánh giải pháp đề xuất so với các giải pháp đã có cơ sở."*

---

## 1. Bài toán được chấm điểm như thế nào

Mọi độ đo trong tài liệu này chấm cùng một loại đầu ra: **một danh sách xếp hạng**.

```
Đầu vào:  dữ liệu của một ca lỗi
Đầu ra:   [ứng viên hạng 1, ứng viên hạng 2, ứng viên hạng 3, ...]
Đáp án:   tập nguyên nhân thật của ca đó
Câu hỏi:  nguyên nhân thật nằm ở hạng bao nhiêu?
```

Vì đầu ra là **xếp hạng** chứ không phải một đáp án duy nhất, không dùng được các độ đo phân loại quen thuộc như accuracy hay F1. Phải dùng họ độ đo xếp hạng.

Ứng viên là gì tuỳ mức đánh giá, theo `A7` §6: mức thô thì ứng viên là service; mức mịn thì ứng viên là chỉ số.

---

## 2. Nhóm độ đo của `T-06` — dùng cho RCAEval

### 2.1 `AC@k`

```
                1        số nguyên nhân thật lọt vào top-k
AC@k  =  ───────────  ∑  ─────────────────────────────────────
           số ca lỗi  ca   min( k , số nguyên nhân thật của ca )
```

Đọc bằng lời: **tỷ lệ ca lỗi mà nguyên nhân thật nằm trong `k` ứng viên đầu tiên.** Giá trị từ 0 đến 1, càng cao càng tốt.

Phần mẫu `min(k, số nguyên nhân thật)` xử lý trường hợp một ca có nhiều nguyên nhân thật: không thể đòi lọt 3 nguyên nhân vào top-1.

### 2.2 `Avg@k`

```
Avg@k  =  ( AC@1 + AC@2 + ... + AC@k ) / k
```

Đây là **trung bình cộng của các `AC@j`**, không phải một `AC` riêng lẻ.

### 2.3 Điểm dễ nhầm nhất — `Avg@5` không phải `AC@5`

Bảng dưới giả định một ca có đúng một nguyên nhân thật:

| Nguyên nhân thật ở hạng | `AC@1` | `AC@3` | `AC@5` | **`Avg@5`** |
|---|---|---|---|---|
| 1 | 1 | 1 | 1 | **1,00** |
| 2 | 0 | 1 | 1 | **0,80** |
| 3 | 0 | 1 | 1 | **0,60** |
| 4 | 0 | 0 | 1 | **0,40** |
| 5 | 0 | 0 | 1 | **0,20** |
| ngoài top 5 | 0 | 0 | 0 | **0,00** |

`AC@5` không phân biệt hạng 1 với hạng 5 — cả hai đều được 1. `Avg@5` phân biệt: hạng 1 được 1,00 còn hạng 5 chỉ được 0,20.

Suy ra công thức rút gọn: nếu nguyên nhân thật ở hạng `p ≤ k` thì `Avg@k = (k + 1 − p) / k`.

### 2.4 Chọn độ đo nào để báo cáo

| Độ đo | Khi nào dùng | Điểm yếu |
|---|---|---|
| `AC@1` | Khi muốn phản ánh giá trị vận hành thật — người trực kiểm tra nghi phạm đầu tiên trước | Nghiêm khắc; một phương pháp tốt vẫn có thể ra số thấp |
| `AC@3`, `AC@5` | Khi muốn biết nguyên nhân có nằm trong tầm nhìn không | Không phân biệt xếp giỏi với xếp may |
| `Avg@5` | Điểm tổng hợp cân bằng nhất; nên là cột chính của bảng so sánh | Khó giải thích cho người chưa biết công thức |

**Khuyến nghị:** báo cáo cả `AC@1`, `AC@3` và `Avg@5` trong cùng một bảng. Chỉ báo cáo `Avg@5` thì mất thông tin về khả năng đoán trúng ngay lần đầu.

---

## 3. Nhóm độ đo của `T-08` — dùng cho LEMMA-RCA

| Độ đo | Ý nghĩa |
|---|---|
| `Precision@K` | Độ chính xác trong `K` ứng viên đầu — cùng họ với `AC@k` |
| `MAP@K` | Mean Average Precision — chất lượng xếp hạng tích luỹ tới hạng `K` |
| `MRR` | Mean Reciprocal Rank — trung bình của `1 / hạng của nguyên nhân thật đầu tiên` |

`MRR` đáng chú ý vì nó rất gọn: nguyên nhân ở hạng 1 cho 1,0; hạng 2 cho 0,5; hạng 4 cho 0,25. Nó phạt việc xếp thấp nhanh hơn `Avg@k`.

**Ràng buộc bắt buộc:** `T-06` và `T-08` dùng hai bộ độ đo khác nhau. **Không đưa số của hai nguồn vào cùng một bảng.** Nếu cần trình bày cả hai, tách thành hai bảng và nói rõ bộ độ đo của từng bảng.

---

## 4. Mức sàn ngẫu nhiên — điều kiện để đọc mọi con số

Một con số hiệu năng **không có nghĩa nếu không kèm mức sàn**. Đây là nội dung quan trọng nhất của tài liệu này.

### 4.1 Công thức

Với xếp hạng ngẫu nhiên đều trên `N` ứng viên và một nguyên nhân thật:

```
AC@1  = 1/N
Avg@5 = 3/N
MRR   = (1/N) · (1 + 1/2 + ... + 1/N)     — số điều hoà chia N
```

Suy ra `Avg@5`: nếu nguyên nhân rơi vào hạng `p ≤ 5` thì `Avg@5 = (6 − p)/5`; lấy kỳ vọng trên `N` vị trí đều nhau được

```
(1/N) · (5 + 4 + 3 + 2 + 1)/5  =  (1/N) · 15/5  =  3/N
```

### 4.2 Bảng tham chiếu

| Số ứng viên `N` | `AC@1` ngẫu nhiên | `Avg@5` ngẫu nhiên |
|---|---|---|
| 6 | 16,7 % | 50,0 % |
| 12 | 8,3 % | 25,0 % |
| 15 | 6,7 % | 20,0 % |
| 64 | 1,6 % | 4,7 % |
| 120 | 0,8 % | 2,5 % |
| 300 | 0,3 % | 1,0 % |

### 4.3 Hệ quả

Một phương pháp đạt `Avg@5 = 0,85`:

- trên 6 ứng viên → mức sàn 50 % → **cải thiện 1,7 lần**, gần như không chứng minh được gì
- trên 300 ứng viên → mức sàn 1 % → **cải thiện 85 lần**, bằng chứng mạnh

Cùng một con số, hai kết luận trái ngược. Vì vậy:

> **Quy tắc bắt buộc: mọi bảng kết quả phải có một hàng đối chứng ngẫu nhiên, và phải ghi rõ số ứng viên `N`.**

Đây cũng là lý do kỹ thuật để chọn mức đánh giá mịn thay vì thô khi số service ít — mức mịn đưa `N` từ vài đơn vị lên vài trăm, khôi phục khả năng phân biệt. Đây là quyết định về **cách đo**, không phải nhận định về quy mô hệ thống nào tốt hơn.

---

## 5. Ví dụ tính tay đầy đủ

Một bộ dữ liệu giả định có **4 ca lỗi**, mỗi ca một nguyên nhân thật, `N = 100` ứng viên.

Kết quả của một phương pháp:

| Ca | Nguyên nhân thật | Hạng phương pháp xếp |
|---|---|---|
| 1 | `database_cpu` | 1 |
| 2 | `payment_latency` | 3 |
| 3 | `auth_mem` | 7 |
| 4 | `partner_timeout` | 2 |

Tính từng `AC@k`:

| | ca 1 | ca 2 | ca 3 | ca 4 | trung bình |
|---|---|---|---|---|---|
| `AC@1` | 1 | 0 | 0 | 0 | **0,25** |
| `AC@2` | 1 | 0 | 0 | 1 | **0,50** |
| `AC@3` | 1 | 1 | 0 | 1 | **0,75** |
| `AC@4` | 1 | 1 | 0 | 1 | **0,75** |
| `AC@5` | 1 | 1 | 0 | 1 | **0,75** |

```
Avg@5 = (0,25 + 0,50 + 0,75 + 0,75 + 0,75) / 5 = 3,00 / 5 = 0,60
```

Đối chứng ngẫu nhiên với `N = 100`: `AC@1` ≈ 0,01 và `Avg@5` ≈ 0,03.

Kết luận đọc được: phương pháp đạt `Avg@5 = 0,60` so với mức sàn 0,03 — cải thiện khoảng 20 lần. Riêng ca 3 xếp hạng 7 nên rơi ngoài mọi mốc `k ≤ 5`, kéo toàn bộ điểm xuống; đây là loại ca cần phân tích riêng trong báo cáo.

---

## 6. Ngoài xếp hạng — độ đo cho lớp giải thích

`DH-MT4` — *"AI hỗ trợ giải thích để trợ lý có thể diễn giải nguyên nhân và gợi ý bước xử lý"* — cho đầu ra **không phải xếp hạng**, nên không dùng được `AC@k`.

**Bộ tiêu chí nay do tài liệu này giữ.** Trước 2026-08-27 nó dẫn ngược về một yêu cầu ở bảng yêu cầu nghiệp vụ; yêu cầu đó đã được gỡ theo `RES-034` vì đo chất lượng lời giải thích là việc của bộ RCA. Ba tiêu chí, định nghĩa **trước** khi chạy:

| # | Tiêu chí | Đo thế nào |
|---|---|---|
| 1 | **Trung thành với đầu vào** | Lời diễn giải chỉ dùng bằng chứng thật sự có trong danh sách nghi phạm nhận được: đúng chỉ số đó, đúng cạnh đó, đúng mẫu log đó |
| 2 | **Hữu ích** | Bước kiểm tra được gợi ý có dẫn tới xác nhận hoặc loại trừ được nghi phạm không |
| 3 | **Trung thực** | Số lần đưa ra bằng chứng hoặc hành động **không có thật** trong dữ liệu đầu vào |

> **Vì sao tiêu chí 1 thay cho câu cũ.** Bản trước ghi *"danh sách nguyên nhân khả dĩ do trợ lý đề xuất có chứa nguyên nhân thật hay không"*. Câu đó chấm **cơ chế xếp hạng**, không chấm lớp giải thích: theo `DH-MT3` việc tìm nguyên nhân là của cơ chế, lớp giải thích chỉ **nhận** kết quả đã xếp hạng. Nếu cơ chế đưa nhầm nghi phạm lên đầu thì một lớp giải thích hoàn hảo vẫn bị chấm trượt — tức chấm sai đối tượng. Việc nguyên nhân thật có lọt top-`k` hay không đã được `AC@k` và `Avg@k` ở §2–§4 đo rồi.

Tiêu chí 3 là bắt buộc: một lớp giải thích bịa ra bằng chứng nghe hợp lý còn nguy hiểm hơn không có lớp giải thích.

**Tài liệu này không gắn ngưỡng.** Ngưỡng chốt sau vòng thực nghiệm đầu tiên, cùng lúc với các độ đo xếp hạng.

---

## 7. Vấn đề `OPEN`

| ID | Vấn đề | Gate xử lý |
|---|---|---|
| `A9-OPEN-01` | Chưa xác minh công thức `MAP@K` và `MRR` từ toàn văn `T-08`; hiện chỉ có tên và mô tả ngắn | Nếu quyết định dùng `T-08` |
| `A9-OPEN-02` | Chưa chốt bộ độ đo chính của đồ án giữa nhóm `T-06` và nhóm `T-08` | Sau khi chốt bộ dữ liệu chính, `A8-OPEN-04` |
| `A9-OPEN-03` | Ngưỡng số cho ba tiêu chí ở §6 chưa gắn | Sau vòng thực nghiệm đầu tiên, cùng lúc với ngưỡng của độ đo xếp hạng. Không còn phụ thuộc `B9`/`B10` sau `RES-034` |
| `A9-OPEN-04` | Chưa chốt mức đánh giá thô hay mịn cho phần thực nghiệm của đồ án | Sau `E1` |

---

## 8. Phép tự kiểm

- [x] Trả lời trực tiếp nửa đầu mục 5 trong thư định hướng.
- [x] Mỗi độ đo có công thức, cách đọc bằng lời, và điểm yếu.
- [x] Có ví dụ tính tay đầy đủ, kiểm chứng lại được bằng máy tính tay.
- [x] Mức sàn ngẫu nhiên có suy luận kèm theo, không chỉ nêu kết quả.
- [x] Nêu rõ ràng buộc không trộn hai bộ độ đo vào một bảng.
- [x] Không gắn ngưỡng số nào. Ngưỡng cho **độ đo xếp hạng** chốt sau vòng thực nghiệm đầu tiên; ngưỡng cho **ba tiêu chí lớp giải thích ở §6** cũng vậy — chúng **không còn phụ thuộc `B9`/`B10`** sau khi `NFR-07` được gỡ khỏi bảng yêu cầu (`RES-034`, `A9-OPEN-03`).
- [x] Bộ tiêu chí cho lớp giải thích **do tài liệu này giữ**, không dẫn ngược sang bảng yêu cầu nghiệp vụ nữa (`RES-034`). Tiêu chí 1 chấm đúng lớp giải thích, không chấm hộ cơ chế xếp hạng.
- [x] Chỉ dùng tài liệu đã công bố làm căn cứ; không dùng nguồn đối chiếu cài đặt nào.
- [ ] Xác minh `A9-OPEN-01`.
- [ ] Lê Văn Minh duyệt.

---

## 9. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A9-v0.2` | 2026-08-27 | §6 tự giữ ba tiêu chí đánh giá lớp giải thích thay vì dẫn ngược về `NFR-07` — yêu cầu đó đã gỡ khỏi bảng yêu cầu nghiệp vụ theo `RES-034`. Thay tiêu chí *"danh sách có chứa nguyên nhân thật không"* bằng **tính trung thành với đầu vào**, vì câu cũ chấm cơ chế xếp hạng chứ không chấm lớp giải thích. Đổi tên mục từ *nhánh trợ lý* sang *lớp giải thích*. **Không đổi công thức, ví dụ tính tay hay mức sàn ngẫu nhiên ở §2–§5** | Tiếp nhận từ bộ hệ thống + sửa lỗi chấm sai đối tượng |
| `A9-v0.1` | 2026-08-23 | Bản đầu: công thức `AC@k` và `Avg@k` kèm bảng phân biệt theo hạng; nhóm độ đo của LEMMA-RCA; mức sàn ngẫu nhiên có suy luận và bảng tham chiếu; ví dụ tính tay 4 ca; nối nhánh trợ lý về `NFR-07` | Tạo mới theo định hướng giảng viên ngày 2026-08-22 |
