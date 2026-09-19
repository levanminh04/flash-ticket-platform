# A9 — Độ đo thực nghiệm theo quy chuẩn

> **Căn cứ hiện hành từ 18/09:** [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md). DH-* bên dưới giữ nguồn gốc khảo sát/kiểm định lịch sử; không là tên hoặc toàn bộ nhiệm vụ hiện hành. Số liệu và trạng thái khoa học của phiếu không được đổi bởi cập nhật này. Minh phụ trách chính RCA; phương pháp thử trên dữ liệu công khai trước rồi trên FlashTicket.

- **Phiên bản:** `A9-v0.4`
- **Trạng thái:** `DRAFT` — khảo sát đang dùng cho nhiệm vụ DT18; bản gốc phục vụ mốc hai tuần, chưa được duyệt
- **Người duyệt:** — (chờ Lê Văn Minh)
- **Ngày duyệt:** —
- **Đầu vào:** `A7-khai-niem-rca.md` (`A7-v0.3`); nguồn `T-06`, `T-08`, `T-17`, `T-19` trong `source-register.md`; kiểm định 990 ca tại `E1-v0.3`; [`docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`](../evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md) `DH-DO` — nguyên văn, `FACT`
- **Đi vào báo cáo:** phần Phương pháp — độ đo đánh giá; và phần Đánh giá kết quả
- **Ràng buộc:** tài liệu này định nghĩa độ đo và cách đọc. Nó **không** tự gắn ngưỡng chấp nhận trước thực nghiệm; ngưỡng và biên không-thua phải được đăng ký trước khi xem tập kiểm tra giữ lại.

> Tài liệu này trả lời nửa đầu mục 5 trong thư định hướng: *"Khảo sát các độ đo thực nghiệm theo qui chuẩn để thực nghiệm so sánh giải pháp đề xuất so với các giải pháp đã có cơ sở."*

---

## 1. Bài toán được chấm điểm như thế nào

Các độ đo xếp hạng ở §1–§5 chấm **danh sách nghi phạm**; §6 chấm lớp giải thích. **OPEN theo DT18-NV2/NV3:** cần bổ sung giao thức/độ đo phát hiện bất thường và vùng ảnh hưởng (nhãn, đơn vị chấm, cửa sổ, cảnh báo giả/bỏ sót); không dùng AC@k thay cho độ đo phát hiện. Minh phụ trách, chốt trước thực nghiệm xác nhận. Cập nhật này chưa chọn công thức hoặc ngưỡng mới.

```
Đầu vào:  dữ liệu của một ca lỗi
Đầu ra:   [ứng viên hạng 1, ứng viên hạng 2, ứng viên hạng 3, ...]
Đáp án:   tập nguyên nhân thật của ca đó
Câu hỏi:  nguyên nhân thật nằm ở hạng bao nhiêu?
```

Vì đầu ra là **xếp hạng**, họ độ đo xếp hạng phải là trung tâm. Trong bài toán mỗi ca chỉ có một nguyên nhân thật, `AC@1` trùng về số học với độ chính xác top-1; tuy nhiên accuracy/F1 đơn lẻ không cho biết nguyên nhân nằm ở hạng 2, 3 hay 5 và vì thế không đủ để đánh giá cả danh sách.

Ứng viên là gì tuỳ mức đánh giá, theo `A7` §6: mức thô thì ứng viên là service; mức mịn thì ứng viên là chỉ số.

---

## 2. Nhóm độ đo của `T-06` — dùng cho RCAEval

### 2.1 `AC@k`

```
                1        số nguyên nhân thật lọt vào top-k
AC@k  =  ───────────  ∑  ─────────────────────────────────────
           số ca lỗi  ca   min( k , số nguyên nhân thật của ca )
```

Đọc bằng lời tổng quát: **mức bao phủ nguyên nhân thật trong `k` ứng viên đầu, đã chuẩn hóa theo số nguyên nhân thật của từng ca.** Giá trị từ 0 đến 1, càng cao càng tốt. Với 990 ca đang kiểm định, mỗi ca có đúng một nhãn nguyên nhân service, nên `AC@k` mới rút gọn thành tỷ lệ ca mà nhãn đó nằm trong top-`k`.

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
| `AC@3`, `AC@5` | Khi muốn biết nguyên nhân có nằm trong tầm nhìn không | Không phân biệt các vị trí cùng nằm trong ngưỡng; muốn biết kết quả do may mắn hay không còn phải so với đối chứng và độ bất định |
| `Avg@5` | Mô tả chất lượng tích luỹ trong năm vị trí đầu | Có thể che việc nghi phạm hạng 1 thường sai nếu đứng một mình |
| `MRR` | Phạt nhanh khi nguyên nhân đầu tiên bị xếp thấp; dùng như độ đo bổ sung trong kiểm định này | Không phải một trong hai độ đo tích hợp mà bài RCAEval công bố ở §4.2; phải định nghĩa rõ ca không có hạng đóng góp 0 |

**Quy tắc `USER_CONFIRMED` cho nghiên cứu này:** `AC@1` là độ đo **chính**, vì câu hỏi vận hành cốt lõi là nghi phạm đầu tiên có đúng hay không. `AC@3`, `AC@5`, `Avg@5` và `MRR` là độ đo **phụ** để mô tả độ hữu ích của danh sách ngắn và vị trí nguyên nhân. Báo cáo phải hiện cả độ đo chính và các độ đo phụ; không được dùng một điểm top-5 đẹp để thay cho `AC@1` thấp.

---

## 3. Nhóm độ đo của `T-08` — dùng cho LEMMA-RCA

| Độ đo | Ý nghĩa |
|---|---|
| `PR@K`, được bài gọi là `Precision@K` | Công thức tác giả công bố có cùng dạng chuẩn hóa theo tập nguyên nhân thật như `AC@k`; không được tự thay bằng công thức precision thông thường có mẫu số cố định là `K` |
| `MAP@K` | Điểm tích lũy các `PR@j` tới hạng `K` theo công thức của bài; phải khóa theo evaluator trước khi dùng vì ký hiệu công bố không đủ để suy ra mọi chi tiết biên |
| `MRR` | Mean Reciprocal Rank — trung bình của `1 / hạng của nguyên nhân thật đầu tiên` |

`MRR` đáng chú ý vì nó rất gọn: nguyên nhân ở hạng 1 cho 1,0; hạng 2 cho 0,5; hạng 4 cho 0,25. Nó phạt việc xếp thấp nhanh hơn `Avg@k`.

**Ràng buộc bắt buộc:** `T-06` và `T-08` dùng hai bộ độ đo khác nhau. **Không đưa số của hai nguồn vào cùng một bảng.** Nếu cần trình bày cả hai, tách thành hai bảng và nói rõ bộ độ đo của từng bảng.

### 3.1 Quy tắc cho ca không có hạng

Nếu phương pháp không trả nguyên nhân thật, trả danh sách ngắn không chứa nguyên nhân thật hoặc không tạo được thứ hạng hợp lệ cho một ca, ca đó vẫn nằm trong mẫu số:

- `AC@1`, `AC@3`, `AC@5` và mọi `AC@k` tương ứng bằng 0;
- đóng góp của ca vào `Avg@5` bằng 0 nếu nguyên nhân nằm ngoài top 5;
- đóng góp của ca vào `MRR` bằng 0 nếu không có hạng.

Không được bỏ các ca này rồi chỉ lấy trung bình trên phần phương pháp trả được kết quả. Ví dụ, kiểm định `E1` thấy MicroRank trên Train Ticket thiếu hạng ở 56/90 ca; bỏ 56 ca sẽ làm `MRR` tăng giả tạo từ khoảng 0,237 lên khoảng 0,627 trên phần còn lại.

---

## 4. Mức sàn ngẫu nhiên — điều kiện để đọc mọi con số

Một con số hiệu năng **không có nghĩa nếu không kèm mức sàn**. Đây là nội dung quan trọng nhất của tài liệu này.

### 4.1 Công thức và điều kiện áp dụng

Với **một nguyên nhân thật**, một hoán vị ngẫu nhiên đều trên `N` ứng viên và `N ≥ 5`:

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

- trên 6 ứng viên → mức sàn 50 % → cao gấp **1,7 lần** đối chứng ngẫu nhiên;
- trên 300 ứng viên → mức sàn 1 % → cao gấp **85 lần** đối chứng ngẫu nhiên.

Hai tỷ số cho thấy kích thước tập ứng viên ảnh hưởng mạnh tới cách đọc kết quả, nhưng **không tỷ số nào tự chứng minh phương pháp hiệu quả**. Vẫn phải xét khoảng tin cậy, độ đa dạng service/loại lỗi, khả năng rò rỉ nhãn và việc các phương pháp có thực sự dùng cùng tập ứng viên hay không. Vì vậy:

> **Quy tắc bắt buộc: mọi bảng kết quả phải có một hàng đối chứng ngẫu nhiên, và phải ghi rõ số ứng viên `N`.**

`N` phải lấy từ **candidate manifest của đúng lượt chạy**, không mặc nhiên lấy số service được một bài báo hoặc kho hệ thống mô tả. Riêng Online Boutique đã có nhiều ranh giới đếm: bài RCAEval ghi 12 service; mục Architecture của kho Google hiện hành ghi 11 microservice và liệt kê cả `loadgenerator`, trong khi phần About của cùng kho ghi 10. Các top-5 CSV còn có tên hạ tầng/alias. Train Ticket được bài mô tả có 64 service nhưng tệp đã kiểm định lộ ít nhất 68 định danh ứng viên. Nếu chưa có manifest, mức sàn phải để `OPEN` hoặc trình bày theo khoảng có giải thích.

Không được chọn mức đánh giá mịn chỉ để làm `N` lớn và khiến tỷ số so với ngẫu nhiên đẹp hơn. Xếp hạng service trả lời *“khu vực nào có khả năng gây lỗi?”*; xếp hạng chỉ số/thao tác trả lời *“tín hiệu hoặc thao tác nào trong khu vực đó đáng kiểm tra?”*. Đây là hai câu hỏi khác nhau. Mức đánh giá phải được khóa theo câu hỏi nghiên cứu và hành động vận hành trước khi xem kết quả; nếu đủ dữ liệu, báo cả hai bảng riêng.

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

Kết luận mô tả được: phương pháp đạt `Avg@5 = 0,60`, cao gấp khoảng 20 lần mức sàn 0,03 trong ví dụ. Muốn kết luận hiệu quả thật còn cần số ca đủ lớn và phân tích độ bất định. Riêng ca 3 xếp hạng 7 nên rơi ngoài mọi mốc `k ≤ 5`; đây là loại ca cần phân tích riêng trong báo cáo.

---

## 6. Quy trình so sánh và suy luận thống kê

### 6.1 Đơn vị phân tích và ghép cặp

Mỗi dòng kết quả là một **ca lỗi**. Khi so sánh hai phương pháp trên cùng bộ dữ liệu, chỉ ghép đúng cùng `case_id`; không so hai trung bình từ hai tập ca khác nhau như thể độc lập. Ca thiếu hạng vẫn được giữ với điểm 0 theo §3.1.

Các lần lặp cùng một service và cùng một loại lỗi thường gần nhau hơn các ca thuộc nhóm khác. Vì vậy khoảng tin cậy phải dùng **bootstrap theo cụm `service × fault type`**, lấy lại mẫu ở cấp cụm thay vì giả định 90 dòng hoàn toàn độc lập.

### 6.2 Kiểm định và hiệu chỉnh nhiều phép thử

- Với `AC@1`, mỗi ca cho kết quả đúng/sai. **McNemar chính xác** đếm hai nhóm bất đồng: số ca chỉ A đúng và số ca chỉ B đúng. Tuy nhiên phép kiểm này giả định các cặp ca là độc lập; 90 dòng hiện tại có ba lần lặp nằm trong cùng cụm `service × fault type`, nên p-value McNemar theo từng dòng chỉ là phân tích nhạy cảm/mô tả, không phải bằng chứng suy luận chính.
- Với chênh lệch `AC@1`, `Avg@5` hoặc `MRR`, báo khoảng tin cậy bootstrap theo cụm `service × fault type`.
- Khi kiểm tra nhiều cặp phương pháp hoặc nhiều giả thuyết, điều chỉnh p-value bằng **Holm**. Tập giả thuyết phải được xác định trước khi xem kết quả; Holm không biến một nhóm so sánh được chọn hậu nghiệm thành kiểm định xác nhận.
- Báo đồng thời chênh lệch hiệu quả, khoảng tin cậy và số ca cải thiện/bị hại; p-value một mình không cho biết mức thay đổi có giá trị vận hành hay không.

Kiểm định `E1` minh họa vì sao các quy tắc này cần thiết: MM-CIRCA thấp hơn CIRCA về `AC@1` trên Online Boutique, nhưng p-value McNemar theo dòng từ khoảng 0,0347 thành khoảng 0,0694 sau Holm, còn khoảng tin cậy cụm của `Avg@5` cắt qua 0. Các cặp so sánh này được đặt ra khi đã nhìn dữ liệu, nên toàn bộ p-value chỉ mang tính thăm dò. Kết luận hợp lệ là **một cấu hình MM-CIRCA có xu hướng giảm và cần ablation**, không phải “trace gây hại”. Công bố của tác giả mô tả MM-CIRCA đa nguồn; hàm consumer ở snapshot công khai `6018cde` chỉ dùng metric và log; còn modality thật của 11 lượt CSV vẫn `OPEN` vì thiếu run manifest và patch hash.

### 6.3 “Chạy thành công” khác “xếp đúng trong top 5”

Cột `success` trong 11 CSV đã kiểm định trùng hoàn toàn với `ac_5`: nó có nghĩa **ground truth xuất hiện trong top 5**, không chứng minh chương trình đã chạy không lỗi. Trạng thái vận hành của một lượt chạy phải nằm ở trường riêng, ví dụ `run_status`, kèm mã lỗi nếu có. Một pipeline có thể hoàn tất kỹ thuật nhưng RCA sai; ngược lại một ca không có hạng có thể do thuật toán từ chối, dữ liệu thiếu hoặc lỗi chương trình — cần trạng thái riêng để phân biệt, nhưng điểm xếp hạng của ca vẫn là 0 nếu không trả đúng nguyên nhân.

### 6.4 Hồ sơ tái lập bắt buộc

Mỗi lượt chạy phải công bố tối thiểu: dataset và phiên bản/hash; danh sách `case_id`; candidate manifest và quy tắc alias; commit thuật toán; patch hash; cấu hình/cửa sổ; seed; thời gian chạy; máy/môi trường; output hash; và quy tắc xử lý ca thiếu. Thiếu seed/runtime trong CSV không được điền bằng suy đoán hoặc bằng tốc độ tổng hợp của một log khác.

---

## 7. Ngoài xếp hạng — độ đo cho lớp giải thích

`DH-MT4` — *"AI hỗ trợ giải thích để trợ lý có thể diễn giải nguyên nhân và gợi ý bước xử lý"* — cho đầu ra **không phải xếp hạng**, nên không dùng được `AC@k`.

**Bộ tiêu chí nay do tài liệu này giữ.** Trước 2026-08-27 nó dẫn ngược về một yêu cầu ở bảng yêu cầu nghiệp vụ; yêu cầu đó đã được gỡ theo `RES-034` vì đo chất lượng lời giải thích là việc của bộ RCA. Ba tiêu chí, định nghĩa **trước** khi chạy:

| # | Tiêu chí | Đo thế nào |
|---|---|---|
| 1 | **Trung thành với đầu vào** | Lời diễn giải chỉ dùng bằng chứng thật sự có trong danh sách nghi phạm nhận được: đúng chỉ số đó, đúng cạnh đó, đúng mẫu log đó |
| 2 | **Hữu ích** | Bước kiểm tra được gợi ý có dẫn tới xác nhận hoặc loại trừ được nghi phạm không |
| 3 | **Trung thực** | Số lần đưa ra bằng chứng hoặc hành động **không có thật** trong dữ liệu đầu vào |

> **Vì sao tiêu chí 1 thay cho câu cũ.** Bản trước ghi *"danh sách nguyên nhân khả dĩ do trợ lý đề xuất có chứa nguyên nhân thật hay không"*. Câu đó chấm **cơ chế xếp hạng**, không chấm lớp giải thích: theo `DH-MT3` việc tìm nguyên nhân là của cơ chế, lớp giải thích chỉ **nhận** kết quả đã xếp hạng. Nếu cơ chế đưa nhầm nghi phạm lên đầu thì một lớp giải thích hoàn hảo vẫn bị chấm trượt — tức chấm sai đối tượng. Việc nguyên nhân thật có lọt top-`k` hay không đã được `AC@k` và `Avg@k` ở §2–§4 đo rồi.

Tiêu chí 3 là bắt buộc: một lớp giải thích bịa ra bằng chứng nghe hợp lý còn nguy hiểm hơn không có lớp giải thích.

**Tài liệu này không gắn ngưỡng.** Nhóm có thể hiệu chỉnh ngưỡng trên vòng pilot/development đầu tiên, nhưng phải khóa ngưỡng và biên không-thua trước khi mở tập kiểm tra giữ lại. Không được nhìn held-out rồi chọn ngưỡng có lợi.

---

## 8. Vấn đề `OPEN`

| ID | Vấn đề | Gate xử lý |
|---|---|---|
| `A9-OPEN-01` | Công thức vận hành của `MAP@K` trên LEMMA cần được khóa theo mã evaluator khi dùng bộ này; riêng quy tắc `MRR=0` cho ca không có hạng đã được chốt trong giao thức nghiên cứu hiện tại | Trước lượt chạy LEMMA đầu tiên |
| `A9-OPEN-02` | **Đã đóng ở phạm vi thực nghiệm RCAEval hiện tại:** `AC@1` là chính; `AC@3`, `AC@5`, `Avg@5`, `MRR` là phụ. Nếu đánh giá LEMMA theo giao thức gốc, báo bảng riêng với bộ độ đo của LEMMA | Đóng tại `A9-v0.3`, duy trì tại `A9-v0.4`; không trộn bảng giữa hai giao thức |
| `A9-OPEN-03` | Ngưỡng số cho ba tiêu chí ở §7 chưa gắn | Hiệu chỉnh trên pilot/development và khóa trước held-out, cùng giao thức với độ đo xếp hạng. Không còn phụ thuộc `B9`/`B10` sau `RES-034` |
| `A9-OPEN-04` | Chưa chốt mức đánh giá thô hay mịn cho phần thực nghiệm của đồ án; phân tích E1 không có quyền chốt thay quyết định này | Trước khi đăng ký thí nghiệm MyRCA xác nhận |
| `A9-OPEN-05` | Chưa có candidate manifest và alias registry theo từng lượt chạy; chưa khóa chính xác `N` cho mức sàn | Tuần 1 của `E1` |
| `A9-OPEN-06` | Seed và runtime per-case còn trống trong 11 CSV; runtime tổng hợp của log Train Ticket không thay thế được hai trường này | Tái chạy có run manifest hoặc nhận đúng hồ sơ lượt chạy |
| `A9-OPEN-07` | Chưa đăng ký số vòng/seed bootstrap, phép kiểm suy luận có xét cụm, tập giả thuyết cho Holm, biên không-thua và split development/held-out của thí nghiệm MyRCA chính thức. Các so sánh E1 hiện tại là hậu nghiệm/thăm dò | Trước khi chạy đánh giá xác nhận |

---

## 9. Phép tự kiểm

- [x] Trả lời trực tiếp nửa đầu mục 5 trong thư định hướng.
- [x] Mỗi độ đo có công thức, cách đọc bằng lời, và điểm yếu.
- [x] Có ví dụ tính tay đầy đủ, kiểm chứng lại được bằng máy tính tay.
- [x] Mức sàn ngẫu nhiên có suy luận kèm theo, không chỉ nêu kết quả.
- [x] Nêu rõ ràng buộc không trộn hai bộ độ đo vào một bảng.
- [x] Chọn `AC@1` làm độ đo chính; `AC@3`, `AC@5`, `Avg@5`, `MRR` chỉ hỗ trợ.
- [x] Ca không có hạng đóng góp 0 và không bị loại khỏi mẫu số.
- [x] So sánh ghép cặp dùng đúng `case_id` và bootstrap theo cụm `service × fault type`; McNemar theo dòng/Holm được hạ đúng thành phân tích thăm dò vì có phụ thuộc trong cụm và chưa đăng ký trước.
- [x] Phân biệt chương trình hoàn tất với cột CSV `success = ac_5`.
- [x] Candidate manifest, seed và runtime thiếu được giữ `OPEN`, không suy đoán.
- [x] Không gắn ngưỡng số nào. Ngưỡng cho **độ đo xếp hạng** và **ba tiêu chí lớp giải thích ở §7** chỉ được hiệu chỉnh trên pilot/development rồi khóa trước held-out; chúng **không còn phụ thuộc `B9`/`B10`** sau khi `NFR-07` được gỡ khỏi bảng yêu cầu (`RES-034`, `A9-OPEN-03`).
- [x] Bộ tiêu chí cho lớp giải thích **do tài liệu này giữ**, không dẫn ngược sang bảng yêu cầu nghiệp vụ nữa (`RES-034`). Tiêu chí 1 chấm đúng lớp giải thích, không chấm hộ cơ chế xếp hạng.
- [x] Định nghĩa độ đo bám trực tiếp công bố chính chủ; số liệu E1 bám CSV kiểm định và được ghi rõ là phân tích cục bộ, không giả làm kết quả do tác giả công bố.
- [ ] Xác minh `A9-OPEN-01`.
- [ ] Lê Văn Minh duyệt.

---

## 10. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A9-v0.4` | 2026-09-03 | Làm rõ `AC@k` nhiều nguyên nhân và `AC@1` một nguyên nhân; tách MRR bổ sung khỏi hai độ đo RCAEval công bố; ghi đúng công thức `PR@K` riêng của LEMMA; bổ sung điều kiện cho mức sàn, gỡ suy luận chọn độ mịn để tăng `N`; hạ McNemar/Holm hậu nghiệm về phân tích thăm dò và khôi phục ba lớp bằng chứng MM-CIRCA | Sửa mâu thuẫn đo lường và giới hạn suy luận, không đổi 990 kết quả |
| `A9-v0.3` | 2026-09-02 | Đồng bộ đầu vào `A7-v0.3`/`E1`; chọn `AC@1` làm độ đo chính; quy định ca thiếu hạng bằng 0 và không bị loại; thêm bootstrap cụm `service × fault type`, McNemar chính xác và Holm; tách trạng thái chạy khỏi CSV `success=AC@5`; mở candidate manifest, seed/runtime và protocol đăng ký trước | Hiệu chỉnh giao thức đo theo kiểm định 990 ca |
| `A9-v0.2` | 2026-08-27 | §6 tự giữ ba tiêu chí đánh giá lớp giải thích thay vì dẫn ngược về `NFR-07` — yêu cầu đó đã gỡ khỏi bảng yêu cầu nghiệp vụ theo `RES-034`. Thay tiêu chí *"danh sách có chứa nguyên nhân thật không"* bằng **tính trung thành với đầu vào**, vì câu cũ chấm cơ chế xếp hạng chứ không chấm lớp giải thích. Đổi tên mục từ *nhánh trợ lý* sang *lớp giải thích*. **Không đổi công thức, ví dụ tính tay hay mức sàn ngẫu nhiên ở §2–§5** | Tiếp nhận từ bộ hệ thống + sửa lỗi chấm sai đối tượng |
| `A9-v0.1` | 2026-08-23 | Bản đầu: công thức `AC@k` và `Avg@k` kèm bảng phân biệt theo hạng; nhóm độ đo của LEMMA-RCA; mức sàn ngẫu nhiên có suy luận và bảng tham chiếu; ví dụ tính tay 4 ca; nối nhánh trợ lý về `NFR-07` | Tạo mới theo định hướng giảng viên ngày 2026-08-22 |
