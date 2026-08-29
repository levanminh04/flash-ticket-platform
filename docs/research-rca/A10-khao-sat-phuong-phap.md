# A10 — Khảo sát phương pháp chẩn đoán nguyên nhân gốc và lập luận chọn phương pháp thực nghiệm

- **Phiên bản:** `A10-v0.3`
- **Trạng thái:** `DRAFT` — khảo sát phục vụ báo cáo hai tuần; chưa được duyệt
- **Người duyệt:** — (chờ Lê Văn Minh)
- **Ngày duyệt:** —
- **Đầu vào:** `A7-khai-niem-rca.md` (**`A7-v0.2`** — lời khai này trước 2026-08-29 ghi `v0.1`; `A7-v0.2` sửa §1 từ *"ba vai"* thành **bốn vai**, và §1 chính là mục mà §1 của tệp này dẫn tới); nguồn `T-06`–`T-14` trong `source-register.md`, trong đó **Bảng 6 toàn văn của `T-06` đã được mở và đối chiếu** ngày 2026-08-26; [`docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`](../evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md) — nguyên văn, `FACT`
- **Đi vào báo cáo:** phần Cơ sở lý thuyết — các hướng tiếp cận hiện có; và phần Phương pháp — lý do chọn tập phương pháp đối chứng
- **Ràng buộc:** tài liệu này khảo sát và chọn **tập phương pháp để chạy đối chứng**. Nó **không** đề xuất phương pháp của đồ án, **không** tuyên bố tính mới, **không** chốt kiến trúc.

---

## 1. Bốn họ phương pháp

Phân họ theo **cơ chế lõi** và **nguồn dữ liệu**, không theo năm công bố. Chi tiết thuật ngữ ở `A7` §4.

### 1.1 Họ thống kê thuần — không dựng đồ thị

Không xây bất kỳ đồ thị nào. Coi mỗi chỉ số là một chuỗi thời gian, tìm thời điểm chuỗi đổi hành vi, rồi xếp hạng bằng kiểm định thống kê.

| Phương pháp | Nguồn | Cơ chế | Đầu vào |
|---|---|---|---|
| **BARO** | `T-09`, FSE 2024 | Multivariate Bayesian Online Change Point Detection để phát hiện bất thường; kiểm định giả thuyết phi tham số để xếp hạng | metric |
| ε-Diagnosis | trong `T-06` | Kiểm định hai mẫu trên chuỗi số liệu | metric |

Đóng góp đáng chú ý của BARO theo `T-09`: bước xếp hạng được thiết kế để **ít nhạy cảm với độ chính xác của bước phát hiện bất thường** so với các công trình trước. Đây là bài học thiết kế đáng giữ: không dồn toàn bộ đặt cược vào bộ phát hiện bất thường.

### 1.2 Họ suy luận nhân quả — đồ thị suy ra từ thống kê

Có cấu trúc đồ thị, nhưng đồ thị **không lấy từ trace**. Nó được suy ra bằng kiểm định độc lập có điều kiện trên dữ liệu số.

| Phương pháp | Nguồn | Cơ chế | Đầu vào |
|---|---|---|---|
| **RCD** | `T-10`, NeurIPS 2022 | Coi sự cố như một **can thiệp** lên nguyên nhân gốc; học phân cấp và cục bộ, **không học toàn bộ đồ thị nhân quả**, chỉ chạy kiểm định để tìm đích can thiệp | metric |
| CIRCA, MicroCause, CausalRCA, EasyRCA, RUN | trong `T-06` | Các biến thể phát hiện cấu trúc nhân quả rồi lần ngược | metric |

Đây là dòng mà bài `T-07` — nguồn giảng viên gửi — khảo sát. `T-07` đánh giá 9 phương pháp phát hiện nhân quả và 21 phương pháp chẩn đoán.

### 1.3 Họ đồ thị phụ thuộc dựng từ trace — đúng hướng giảng viên định hướng

Đồ thị lấy trực tiếp từ quan hệ gọi trong trace, rồi lan truyền trên đồ thị đó.

| Phương pháp | Nguồn | Cơ chế | Đầu vào |
|---|---|---|---|
| **MicroRank** | `T-11`, WWW 2021 | Phân biệt trace bất thường và bình thường; personalized PageRank gán trọng số cho các trace; phân tích phổ mở rộng để xếp hạng | trace |
| MicroRCA | `T-12`, NOMS 2020 | Đồ thị thuộc tính mô hình hóa lan truyền bất thường xuyên **service và máy chủ**; trích đồ thị con bất thường; personalized PageRank | metric + quan hệ gọi |

### 1.4 Họ dùng trace nhưng không lan truyền

| Phương pháp | Nguồn | Cơ chế | Đầu vào |
|---|---|---|---|
| **TraceRCA** | `T-13`, IWQoS 2021 | Ba bước: phát hiện trace bất thường → khai phá tập service nghi ngờ → xếp hạng. Giả thiết lõi: service có **càng nhiều trace lỗi và càng ít trace bình thường** đi qua thì càng khả nghi | trace |

### 1.5 Ngoài bốn họ — để tham chiếu, không đưa vào thực nghiệm kỳ này

| Nhóm | Đại diện | Vì sao ghi nhận |
|---|---|---|
| Đa phương thức | Nezha (FSE 2023), MULAN, CORAL, MRCA, PDiagnose | Hợp nhất log, metric, trace thành **sự kiện** rồi khai phá mẫu. Nezha cho nguyên nhân ở mức **vùng mã nguồn và loại tài nguyên** — mịn hơn mức service của MicroRank và TraceRCA |
| Dựa log | LogRCA | Chọn tập dòng log tối thiểu liên quan nguyên nhân. Đây là nhánh mà Drain đóng vai trò **tiền xử lý — gom dòng log thành mẫu, trước khi ánh xạ lên đồ thị**; Drain không tự làm việc ánh xạ. Nhóm từng chọn kỹ thuật này cho hướng cũ; nó vẫn là một **ứng viên** khi thiết kế bước xử lý log của `DH-MT2`, **chưa chốt dùng** (`RES-035`) |
| LLM và tác tử | RCACopilot (EuroSys 2024), Flow-of-Action (WWW 2025), Praxis, mABC | Dự đoán loại nguyên nhân và sinh diễn giải. RCACopilot công bố độ chính xác 0,766 trên dữ liệu sự cố thật của Microsoft |

---

## 2. Phân bố trong benchmark hiện có

15 phương pháp tham chiếu của `T-06` chia theo nguồn dữ liệu:

| Nhóm | Số lượng | Tên |
|---|---|---|
| Dựa metric | **9** | RUN, CausalRCA, CIRCA, RCD, MicroCause, EasyRCA, MSCRED, BARO, ε-Diagnosis |
| Dựa trace | **2** | TraceRCA, MicroRank |
| Đa nguồn | **4** | PDiagnose, multi-source BARO, multi-source RCD, multi-source CIRCA |

### 2.1 Ba nhận định rút ra

**`FACT` — hướng dựa trace là nhóm mỏng nhất.** Chỉ 2 trên 15 phương pháp tham chiếu dùng trace, trong khi 9 chỉ dùng metric.

**`FACT` — chưa có phương pháp nào vượt trội ở mọi tình huống.** `T-07` kết luận mỗi phương pháp hoặc thiếu về hiệu quả, hoặc thiếu về hiệu năng, hoặc nhạy cảm với điều kiện cụ thể. `T-07` cũng nêu rằng kết quả trên dữ liệu tổng hợp **không phản ánh đúng** hiệu năng trên hệ thật, và hệ microservice quy mô lớn vẫn là thách thức.

**`FACT` — mọi loại lỗi trong các bộ dữ liệu công khai đều là lỗi kỹ thuật.** Theo `T-06` và `T-07`: CPU hog, memory leak, disk IO stress, network delay, packet loss, socket, và lỗi mức mã nguồn. Không bộ nào chèn lỗi vi phạm bất biến nghiệp vụ.

### 2.2 Hệ quả — ở mức `CANDIDATE`

Ba nhận định trên gợi ra hai hướng đóng góp kiểm chứng được. Cả hai đều là `CANDIDATE`, **chưa được chọn**:

| ID | Hướng | Cần gì để trở thành đề xuất |
|---|---|---|
| `A10-CAND-01` | Kết hợp đồ thị phụ thuộc từ trace với phát hiện điểm đổi trên metric và tín hiệu từ log, đánh giá xem đa nguồn có cải thiện so với đơn nguồn không | Kết quả thực nghiệm ở `E1` cho thấy khoảng cách thật giữa nhóm đơn nguồn và đa nguồn |
| `A10-CAND-02` | Đánh giá các phương pháp hiện có trên một lớp lỗi chưa xuất hiện trong benchmark công khai — vi phạm bất biến nghiệp vụ | Quyết định của Lê Văn Minh và giảng viên về vai trò hệ thống của đồ án; hiện là `A7-OPEN-02` |

**Không được viết trong báo cáo** rằng đồ án "đề xuất phương pháp mới" cho tới khi có kết quả thực nghiệm chống lưng. Việc dùng lại một cơ chế đã công bố không tạo ra tính mới.

---

## 3. Chọn tập phương pháp cho thực nghiệm hai tuần

### 3.1 Tiêu chí chọn

Bốn tiêu chí, áp theo thứ tự:

1. **Phủ được cả bốn họ.** Mục đích của thực nghiệm là cho thấy các cơ chế khác nhau cho kết quả khác nhau, không phải xếp hạng dài.
2. **Có cài đặt tái lập được trong `T-06`.** Phương pháp phải tự cài lại là rủi ro tiến độ, không phải giá trị nghiên cứu.
3. **Đại diện được nguồn giảng viên gửi.** Bỏ hoàn toàn dòng suy luận nhân quả là bỏ chính nguồn `T-07`.
4. **Gần định hướng "đồ thị phụ thuộc".** Phải có ít nhất một phương pháp thực sự lan truyền trên đồ thị.

### 3.2 Tập được chọn — 5 phương pháp và 1 đối chứng

> **Lý do chọn không dùng điểm số** (`A10-v0.2`). Bản `v0.1` biện minh cho BARO bằng *"điểm cao nhất trong `T-06`"* và cho TraceRCA bằng *"điểm cao thứ nhì"*. Sau khi mở Bảng 6 toàn văn, **cả hai đều sai**: cao nhất là multi-source BARO với 0,81, còn 0,77 của TraceRCA đứng **thứ ba**. Quan trọng hơn, chọn phương pháp đối chứng theo điểm là **sai phương pháp luận** — nó biến tập đối chứng thành một bảng vinh danh thay vì một mặt cắt qua bốn họ. Tiêu chí ở §3.1 vốn đã là **phủ họ**; §3.2 nay viết đúng theo tiêu chí đó.

| Chọn | Họ | Lý do chọn | Tiêu chí thoả |
|---|---|---|---|
| **Ngẫu nhiên** | đối chứng | Xác lập mức sàn theo `A7` §7. Trả lời trước câu hỏi *"đoán bừa được bao nhiêu?"*. Rất ít công trình công bố cột này, nên nó cũng là điểm mạnh của báo cáo | — |
| **BARO** | thống kê thuần | Đại diện họ thống kê thuần. Chỉ cần metric nên rủi ro môi trường thấp nhất → chạy đầu tiên | 1, 2 |
| **RCD** | suy luận nhân quả | Đại diện đúng dòng mà `T-07` khảo sát | 1, 2, 3 |
| **TraceRCA** | trace, không lan truyền | Một trong **hai** phương pháp dựa trace duy nhất trong `T-06`; đại diện cách dùng trace **không** lan truyền, để tách bạch với MicroRank | 1, 2 |
| **MicroRank** | **đồ thị phụ thuộc + PageRank** | **Gần định hướng của giảng viên nhất.** Là mẫu chuẩn của "lan truyền trên đồ thị phụ thuộc" ở mục tiêu nghiên cứu số 3 | 1, 2, 4 |
| **Multi-source BARO** (`mmbaro`) | đa nguồn | Cùng lõi thuật toán với BARO, nên chênh lệch giữa hai dòng **chỉ do nguồn dữ liệu** — đúng câu hỏi của `A10-CAND-01` và câu hỏi phụ 1 ở `A4` | 1, 2 |

### 3.3 Loại và lý do loại

| Loại | Họ | Lý do loại |
|---|---|---|
| CIRCA, MicroCause, CausalRCA, EasyRCA, RUN | nhân quả | Trùng họ với RCD. Thêm một phương pháp cùng họ chỉ thêm một dòng, không thêm góc nhìn. **Ngoại lệ:** nếu người phụ trách họ nhân quả còn thời gian, thêm CIRCA để so trong nội bộ họ |
| MSCRED | học sâu tái tạo | **Cần huấn luyện mô hình.** Lệch khỏi đặc điểm "không huấn luyện" của các phương pháp còn lại và làm phần so sánh mất tính đồng nhất về điều kiện |
| ε-Diagnosis | thống kê | BARO đã đại diện họ này với cơ chế mạnh hơn |
| PDiagnose | đa nguồn | `mmbaro` đại diện họ đa nguồn với gốc so sánh rõ hơn: cùng lõi BARO nên tách bạch được ảnh hưởng của việc thêm nguồn dữ liệu |
| MicroRCA (`T-12`) | đồ thị | Không nằm trong tập cài đặt của `T-06` → phải tự cài. MicroRank đã đại diện cơ chế personalized PageRank. Ngoài ra số hiệu năng công bố của `T-12` đo trên bộ dữ liệu riêng nên **không so trực tiếp** được |
| Nezha, LogRCA, nhóm LLM | đa phương thức, log, LLM | Ngoài phạm vi thực nghiệm hai tuần. Ghi nhận ở §1.5 để tham chiếu; nhóm LLM liên quan tới mục tiêu nghiên cứu số 4 và sẽ xử lý ở gate sau |
| TORAI, EventADL | đa nguồn | Xuất hiện trong kho mã của `T-06` nhưng mới hơn bài báo; tài liệu chưa ổn định |

### 3.4 Nguyên tắc bắt buộc khi chạy

- **Một bảng chỉ được chứa kết quả từ một bộ dữ liệu.** Không trộn số của `re1-ob` với `re2-tt`.
- **Mọi phương pháp trong cùng một bảng phải chạy trên cùng bộ, cùng độ đo.** Đây là nghĩa của yêu cầu "cùng bộ dataset" trong thư giảng viên.
- **Mỗi con số phải ghi rõ do nhóm chạy hay trích từ nguồn nào.**

---

## 4. Số hiệu năng đã công bố — đã đối chiếu Bảng 6 toàn văn

### 4.1 Ba cảnh báo phải đọc trước khi nhìn bảng

1. **Mọi số dưới đây đo trên `re2-tt`, tức bộ `RE2` thu từ Train Ticket.** `RES-022` chốt bộ chính là **`RE2`** nhưng **chưa chốt hệ nào trong `RE2`** — bộ này có dữ liệu từ ba hệ, và báo cáo hai tuần §5.4 mới đề xuất Online Boutique ở mức `CANDIDATE`. Nếu chốt một hệ khác Train Ticket thì **không số nào ở đây là mốc đối chiếu**: Train Ticket có 64 ứng viên mức service, Online Boutique có 12, nên mức sàn ngẫu nhiên `Avg@5` chênh nhau hơn năm lần (`R0` §2.2). Hai bảng khác mức sàn thì **không so ngang** được. Việc chọn hệ ghi ở `A10-OPEN-05`.
2. **Bản `A10-v0.1` gán sai một số.** Nó ghi *"RCD ≈ 0,54"*. Bảng 6 cho **RCD = 0,13**; **0,54 là điểm của multi-source RCD**, một dòng khác. Đây là lỗi khớp **tên** mà lệch **nghĩa** — chỉ lộ ra khi mở bảng gốc, không lộ ra khi đọc bản tóm tắt.
3. **Số của bài là để đối chiếu mức tái lập, không phải để trích thẳng vào báo cáo.** Số chính của báo cáo là số nhóm tự chạy ở `E1`.

### 4.2 Bảng 6 của `T-06` — `Avg@5` trên `re2-tt`

| Phương pháp | Họ | `Avg@5` | Ghi chú |
|---|---|---|---|
| **Multi-source BARO** | đa nguồn | **0,81** | Cao nhất trong bảng |
| **BARO** | thống kê thuần | **0,80** | Chỉ dùng metric. Mạnh ở lỗi tài nguyên, yếu ở lỗi mạng |
| **TraceRCA** | trace, không lan truyền | **0,77** | Đứng **thứ ba**, không phải thứ nhì |
| **Multi-source RCD** | đa nguồn | **0,54** | Đây mới là dòng ứng với số 0,54 |
| **CIRCA** | nhân quả | **0,46** | Chỉ dùng metric |
| **MicroRank** | **đồ thị phụ thuộc + PageRank** | **0,31** | **Thấp nhất trong nhóm được trích** |
| **RCD** | nhân quả | **0,13** | Chỉ dùng metric |

Chênh lệch BARO → multi-source BARO là **+0,01**; chênh lệch RCD → multi-source RCD là **+0,41**. Hai con số này nói rằng lợi ích của việc thêm nguồn dữ liệu **phụ thuộc mạnh vào lõi thuật toán**, không phải một hằng số. Đó chính là lý do câu hỏi phụ 1 ở `A4` bắt buộc so **cùng lõi thuật toán**.

### 4.3 Điểm khó chịu phải nói thẳng

**MicroRank là phương pháp gần định hướng của giảng viên nhất — và nó đứng gần cuối bảng.** `DH-MT3` yêu cầu *lan truyền trên đồ thị phụ thuộc*; MicroRank là mẫu chuẩn của đúng cơ chế đó, nhưng đạt `Avg@5` = 0,31 trong khi một phương pháp không dựng đồ thị nào đạt 0,80.

Không được giấu điều này, và cũng không được đọc nó thành *"hướng đồ thị là hướng sai"*. Ba cách đọc đều còn mở, và chỉ thực nghiệm mới phân định được:

- Bảng đo trên Train Ticket với **lỗi kỹ thuật** — CPU, bộ nhớ, đĩa, mạng. Lỗi tài nguyên biểu hiện rõ trên **chuỗi chỉ số** hơn trên **cấu trúc gọi**, nên sân chơi này thuận cho họ thống kê.
- MicroRank chỉ dùng **trace**. Nó không nhìn thấy metric, tức không nhìn thấy đúng lớp tín hiệu mà lỗi tài nguyên tạo ra.
- Điểm thấp có thể là điểm của **một cài đặt cụ thể trong `T-06`**, không phải trần của cơ chế PageRank trên đồ thị.

Cách đọc thứ nhất và thứ hai cùng gợi một điều kiểm chứng được: **đồ thị phụ thuộc có thể mạnh lên khi được nuôi thêm nguồn dữ liệu**, đúng hướng `A10-CAND-01`. Cách đọc thứ ba là lý do phải chạy lại chứ không trích số.

### 4.4 Số ngoài Bảng 6 — không so trực tiếp

| Phương pháp | Số đọc được | Nguồn | Vì sao không so ngang |
|---|---|---|---|
| MicroRCA | precision 89 %, MAP 97 % | `T-12` | Đo trên bộ dữ liệu riêng của tác giả, độ đo khác |
| RCACopilot | độ chính xác 0,766 | EuroSys 2024 | Đo trên dữ liệu sự cố nội bộ Microsoft, bài toán khác |

---

## 5. Vấn đề `OPEN`

| ID | Vấn đề | Gate xử lý |
|---|---|---|
| `A10-OPEN-06` | Các phương pháp ghi nhận ở §1.5 chưa có mã nguồn trong `source-register.md`. Không chặn thực nghiệm, nhưng chặn việc trích chúng vào báo cáo | Trước khi đưa bất kỳ khẳng định nào của §1.5 vào báo cáo |
| `A10-OPEN-05` | `RES-022` chốt bộ `RE2` nhưng chưa chốt **hệ nào trong `RE2`** ở mức quyết định; báo cáo hai tuần §5.4 đề xuất Online Boutique ở mức `CANDIDATE` (`A8-OPEN-06`). Nếu chốt một hệ khác Train Ticket thì Bảng 6 **không còn là mốc đối chiếu mức tái lập** | Trước `E1`; nếu cần giữ mốc đối chiếu thì chạy thêm một vòng trên `re2-tt` |
| `A10-OPEN-02` | Chưa xác minh từ toàn văn `T-09` rằng BARO hoàn toàn không dựng đồ thị; kế thừa `A7-OPEN-04` | Khi đọc toàn văn `T-09` |
| `A10-OPEN-03` | Chưa đọc toàn văn cơ chế của CIRCA; hiện chỉ xếp họ theo mô tả trong `T-06` | Chỉ cần xử lý nếu quyết định chạy CIRCA |
| `A10-OPEN-04` | `A10-CAND-01` và `A10-CAND-02` chưa được chọn làm hướng đóng góp | Sau `E1` và sau phản hồi của giảng viên |

`A10-OPEN-01` **đã đóng** ngày 2026-08-26: Bảng 6 toàn văn của `T-06` đã được mở, bốn số được đối chiếu, và một số đã được sửa — xem §4.1 mục 2. Phần chưa biết còn lại không phải *"số có đúng không"* mà là *"số nào áp cho bộ nhóm chạy"*, chuyển sang `A10-OPEN-05`.

---

## 6. Phép tự kiểm

- [x] Phân họ theo cơ chế lõi và nguồn dữ liệu, không theo năm công bố.
- [x] Mỗi phương pháp **được chọn hoặc bị loại ở §3** dẫn về một mã nguồn trong `source-register.md`.
- [ ] **Chưa đạt:** các phương pháp chỉ **ghi nhận để tham chiếu** ở §1.5 — nhóm đa phương thức, nhóm dựa log, nhóm mô hình ngôn ngữ — **chưa có mã nguồn** trong sổ nguồn. Chúng nằm ngoài phạm vi thực nghiệm vòng này nên không chặn, nhưng **không được trích số hay khẳng định nào của chúng vào báo cáo** trước khi bổ sung nguồn. Ghi tại `A10-OPEN-06`.
- [x] Nêu rõ họ nào thật sự lan truyền trên đồ thị, họ nào không.
- [x] Tiêu chí chọn nêu trước danh sách chọn, không dựng tiêu chí sau để hợp thức hoá lựa chọn.
- [x] Mỗi phương pháp bị loại đều có lý do cụ thể, không loại vì "không đủ thời gian" chung chung.
- [x] Mọi số ở §4.2 đã đối chiếu Bảng 6 toàn văn của `T-06`, không còn số nào ở trạng thái *"đọc từ bản tóm tắt"*.
- [x] Số của bản `v0.1` gán sai được nói thẳng ở §4.1 mục 2, kèm dòng đúng và dòng bị nhầm — không sửa im lặng.
- [x] Phạm vi của bảng — `re2-tt`, không phải bộ nhóm sẽ chạy — nêu **trước** bảng chứ không nêu trong chú thích cuối.
- [x] Kết quả bất lợi cho hướng đồ thị được nêu ở §4.3 kèm ba cách đọc còn mở, không giấu và cũng không kết luận sớm.
- [x] Không tuyên bố tính mới; hai hướng đóng góp giữ ở `CANDIDATE`.
- [x] Chỉ dùng tài liệu đã công bố làm căn cứ; không dùng nguồn đối chiếu cài đặt nào.
- [ ] Lê Văn Minh duyệt.

---

## 7. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A10-v0.3` | 2026-08-27 | Thêm một câu ở §1.5 ghi vai trò và trạng thái của kỹ thuật gom mẫu log sau khi nó đổi chủ từ bộ hệ thống sang bộ này (`RES-035`): ứng viên cho bước **tiền xử lý**, chưa chốt dùng. **Không đổi bốn họ phương pháp, tập đối chứng hay số ở §4** | Tiếp nhận từ bộ hệ thống |
| `A10-v0.2` | 2026-08-26 | Đối chiếu Bảng 6 toàn văn của `T-06`: sửa RCD 0,54 → **0,13**, bổ sung multi-source RCD 0,54, multi-source BARO 0,81 và **MicroRank 0,31**; viết lại §4 thành ba mục có cảnh báo phạm vi `re2-tt` đứng trước bảng; thêm §4.3 nói thẳng việc MicroRank đứng gần cuối; gỡ điểm số khỏi lý do chọn ở §3.2 và chuyển sang tiêu chí phủ họ; đóng `A10-OPEN-01`, mở `A10-OPEN-05`; suy lại ô tự kiểm §6 | Sửa sau khi mở nguồn gốc |
| `A10-v0.1` | 2026-08-23 | Bản đầu: bốn họ phương pháp và họ nào dùng đồ thị; phân bố 9/2/4 trong benchmark; ba nhận định `FACT` và hai hướng `CANDIDATE`; tiêu chí chọn và tập 5 phương pháp + 1 đối chứng; lý do loại từng phương pháp; bảng số hiệu năng kèm cảnh báo chưa xác minh | Tạo mới theo định hướng giảng viên ngày 2026-08-22 |
