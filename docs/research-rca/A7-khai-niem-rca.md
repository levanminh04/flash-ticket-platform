# A7 — Từ vựng nền cho chẩn đoán nguyên nhân gốc

> **Căn cứ hiện hành từ 18/09:** [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md). DH-* bên dưới giữ nguồn gốc khảo sát/kiểm định lịch sử; không là tên hoặc toàn bộ nhiệm vụ hiện hành. Số liệu và trạng thái khoa học của phiếu không được đổi bởi cập nhật này. Minh phụ trách chính RCA; phương pháp thử trên dữ liệu công khai trước rồi trên FlashTicket.

- **Phiên bản:** `A7-v0.3`
- **Trạng thái:** `DRAFT` — từ vựng nền của bộ RCA; ngữ cảnh hiện hành theo DT18; chưa được duyệt
- **Người duyệt:** — (chờ Lê Văn Minh)
- **Ngày duyệt:** —
- **Đầu vào:** [`docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`](../evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md) — nguyên văn, `FACT`; các nguồn `T-06`–`T-19` liên quan trong `source-register.md`; kiểm định tệp chạy ở `E1`
- **Đi vào báo cáo:** phần Cơ sở lý thuyết — mục thuật ngữ; và phần Phương pháp đánh giá
- **Ràng buộc:** tài liệu này chỉ định nghĩa thuật ngữ và ghi nhận sự kiện đã công bố. Nó **không** chọn phương pháp, **không** chốt phạm vi đề tài, **không** sửa `A1`/`A2`/`A4` — việc tái baseline ba phiếu đó đã làm ở gate của chính chúng theo `RES-032`.

> **Vì sao tài liệu này tồn tại.** Định hướng mới đưa vào cùng lúc bốn nhóm tên riêng — tên hệ thống, tên bộ dữ liệu, tên thuật toán, tên độ đo — và chúng dễ bị đọc nhầm là cùng một loại. Mọi phân tích sau đó đứng trên việc phân biệt đúng bốn nhóm này. Viết trước, cả nhóm đọc trước khi làm việc khác.

---

## 1. Bốn vai không được lẫn

Một thí nghiệm chẩn đoán nguyên nhân gốc luôn có đúng **bốn** vai. Đây là bảng gốc của toàn bộ tài liệu.

| Vai | Là gì | Ví dụ | Chạy được không? |
|---|---|---|---|
| **Hệ thống thử nghiệm** | Phần mềm bị chèn lỗi để thu dữ liệu | Train Ticket, Online Boutique, Sock Shop | Chạy được, nhưng nhóm **không** cần chạy |
| **Bộ dữ liệu** | Dữ liệu đã thu sẵn từ hệ thống trên, kèm đáp án | `re1-ob`, `re2-tt`, `re3-ss` | Không — đây là file |
| **Thuật toán** | Chương trình nhận dữ liệu, trả về xếp hạng nghi phạm | BARO, RCD, TraceRCA, MicroRank | **Có** — đây là thứ nhóm chạy |
| **Độ đo** | Con số chấm điểm kết quả của thuật toán | `AC@1`, `AC@3`, `Avg@5` | Không — đây là kết quả tính ra |

Cách nhớ: **nơi ra đề — đề thi — thí sinh — ba-rem chấm.**

- Hệ thống thử nghiệm là **nơi ra đề**: đề thi được sinh ra từ nó, nhưng nó không vào phòng thi cùng thí sinh.
- Bộ dữ liệu là **đề thi**.
- Thuật toán là **thí sinh**.
- Độ đo là **ba-rem chấm**.

> **Vì sao phải là bốn chứ không phải ba.** Bản `A7-v0.1` đặt tiêu đề *"ba vai"* trên một bảng **bốn dòng**, và câu ghi nhớ ba vế bỏ mất đúng vai **hệ thống thử nghiệm**. Đó là vai quyết định **số ứng viên**, tức quyết định mức sàn ngẫu nhiên ở §7 — bỏ nó khỏi câu ghi nhớ là bỏ đúng thứ làm một điểm số đọc được hay không đọc được.

`AC@1` không cài được, không có repo, không chạy được. Nó là con số nhóm tính ra **sau khi** chạy một thuật toán.

### Nhầm lẫn thường gặp

| Câu nói sai | Vì sao sai | Câu đúng |
|---|---|---|
| "Chạy BARO trên CIRCA" | Thí sinh không thi trên thí sinh | "Chạy BARO trên `re1-ob`" |
| "So sánh Train Ticket với Sock Shop" | Hệ thống là điều kiện, không phải đối tượng so sánh | "So sánh BARO với RCD, chạy trên cả Train Ticket và Sock Shop" |
| "Dùng độ đo AC@1 để tìm nguyên nhân" | Độ đo không tìm gì cả, nó chấm điểm | "Dùng BARO để tìm nguyên nhân, dùng AC@1 để chấm BARO" |
| "`re2-tt` là một thuật toán mới" | Đây là tên file dữ liệu | "`re2-tt` là bộ dữ liệu RE2 thu từ Train Ticket" |

---

## 2. Hệ thống thử nghiệm

Ba hệ thống mã nguồn mở mà giới nghiên cứu dùng làm nơi chèn lỗi. Nhóm **không cần cài** chúng — dữ liệu đã được thu sẵn và công bố.

| Hệ thống | Quy mô được nguồn mô tả | Ghi chú |
|---|---|---|
| Online Boutique | **11 microservice** trong kho chính thức hiện hành (`T-18`) | RCAEval và tệp kết quả có thể dùng một **tập định danh đánh giá** khác; 12 tên ứng viên quan sát được không biến thành 12 microservice của ứng dụng |
| Sock Shop | 15 | Go, Java Spring, Node.js |
| **Train Ticket** | **64 service** theo mô tả hệ thống/benchmark (`T-14`) | Tệp đối chứng đã cung cấp xuất hiện ít nhất **68 định danh ứng viên**; trong đó có thể có tài nguyên hoặc kho dữ liệu, nên 64 không tự động là `N` của phép chấm |

Train Ticket là hệ thống Java quy mô lớn nhất trong ba hệ benchmark được khảo sát. Tuy nhiên, **số service của hệ thống**, **số thực thể vận hành** và **số ứng viên mà bộ đánh giá cho phép xếp hạng** là ba đại lượng khác nhau. Mức sàn ngẫu nhiên ở §7 phải dùng đại lượng cuối cùng được kiểm kê từ chính tệp đánh giá, không được lấy số 64 theo thói quen.

---

## 3. Bộ dữ liệu — cách giải mã tên

Tên bộ dữ liệu là **mã ghép**, không phải khái niệm mới:

```
re2-tt  =  RE2  +  TT
            │      └── Train Ticket
            └───────── bộ số 2

re1-ob  =  RE1  +  Online Boutique
re3-ss  =  RE3  +  Sock Shop
```

Ba bộ × ba hệ thống = chín tổ hợp.

### Ba bộ khác nhau ở dữ liệu chứa bên trong

| Bộ | Ca lỗi | Metric | Log | Trace | Loại lỗi |
|---|---|---|---|---|---|
| **RE1** | 375 | ✔ 49–212 chỉ số | ✘ | ✘ | CPU, MEM, DISK, DELAY, LOSS |
| **RE2** | 270 | ✔ 77–376 chỉ số | ✔ 8,6–26,9 triệu dòng | ✔ 39,6–76,7 triệu span | thêm SOCKET |
| **RE3** | 90 | ✔ 68–322 chỉ số | ✔ 1,7–2,7 triệu dòng | ✔ 4,5–4,7 triệu span | 5 loại lỗi **mức mã nguồn** |

Tổng **735 ca lỗi**, **11 loại lỗi**, trên ba hệ thống. Nguồn: `T-06`.

Đối chiếu lại bảng công bố cho thấy khoảng của RE2 là **77–376 chỉ số**; `A7-OPEN-01` được đóng ở phiên bản này. Con số này mô tả số chuỗi telemetry trong bộ dữ liệu, không phải số service hay số node của đồ thị.

### Một ca lỗi có những nội dung logic gì

Mỗi ca lỗi có các nội dung dưới đây. **Tên tệp vật lý thay đổi giữa bản dữ liệu thô, bản tiền xử lý và mã chạy RCAEval**, nên bảng này không phải schema để viết cứng adapter:

| Nội dung | Trường tối thiểu cần hiểu | Có ở bộ nào |
|---|---|---|
| Metric | Mốc thời gian, định danh thực thể, tên chỉ số, giá trị | RE1, RE2, RE3 |
| Mốc tiêm lỗi | Thời điểm chia cửa sổ bình thường và bất thường trong benchmark | RE1, RE2, RE3 |
| Log | Mốc thời gian, định danh thực thể, nội dung hoặc mẫu log | RE2, RE3 |
| Trace | Trace/span ID, quan hệ cha–con, service/operation, thời gian và trạng thái nếu phương pháp yêu cầu | RE2, RE3 |

Nhãn đáp án gồm **hai mức**: service nào là nguyên nhân, và chỉ số nào chỉ ra nó.

### Hai bộ dữ liệu giảng viên gửi

| Nguồn | Nội dung | Modality |
|---|---|---|
| Zenodo 13305663 (`T-07`) | Artifact bài ASE 2024. 606,2 MB. Gồm dữ liệu tổng hợp (CIRCA, RCD, CausIL — mỗi loại 10 và 50 nút) và bốn bộ hệ thật: Online Boutique 31,0 MB · Sock Shop 1 3,5 MB · Sock Shop 2 79,1 MB · Train Ticket 279,7 MB | **Chỉ metric**, thu bằng Prometheus và cAdvisor |
| LEMMA-RCA (`T-08`, `T-17`) | 51 ca lỗi. Lĩnh vực CNTT (nền tảng đánh giá sản phẩm 216 pod; nền tảng điện toán đám mây 11 nút) và công nghiệp (hệ nước SWaT 51 cảm biến, WADI 123 cảm biến). 235,5 MB – 765 GB | Bản tiền xử lý mô tả rõ **metric + log không cấu trúc**; nguồn tổng quan nói có trace nhưng schema span, quan hệ cha–con và độ đầy đủ cần cho MicroRank **chưa được kiểm chứng** |

**Sự kiện cần ghi nhận (`FACT`):** bộ Zenodo giảng viên gửi **không chứa log và trace**. Mục tiêu nghiên cứu số 2 trong thư định hướng yêu cầu *"ánh xạ log giao dịch và dấu vết vận hành (trace) lên đồ thị"*. Việc lấp chỗ này được xử lý ở `A8`, không kết luận tại đây.

---

## 4. Thuật toán — bốn họ, và họ nào thật sự dùng đồ thị

Đây là mục quan trọng nhất cho định hướng mới. **Không phải mọi phương pháp chẩn đoán đều dùng đồ thị.**

| Họ | Dùng đồ thị? | Cơ chế lõi | Đầu vào | Đại diện |
|---|---|---|---|---|
| **Thống kê thuần** | **Không** | Phát hiện điểm đổi trên chuỗi thời gian, rồi kiểm định giả thuyết phi tham số để xếp hạng | metric | BARO (`T-09`), ε-Diagnosis |
| **Suy luận nhân quả** | Có, nhưng đồ thị **suy ra từ thống kê** — không lấy từ trace | Kiểm định độc lập có điều kiện để tìm cấu trúc nhân quả | metric | RCD (`T-10`), CIRCA, MicroCause, CausalRCA |
| **Đồ thị phụ thuộc từ trace** | **Có — lấy trực tiếp từ trace** | Personalized PageRank lan truyền trên đồ thị gọi thật | trace | MicroRank (`T-11`), MicroRCA (`T-12`) |
| **Trace, không lan truyền** | Dùng đường đi, nhưng không lan truyền trên đồ thị | Đếm: service có nhiều trace lỗi và ít trace bình thường đi qua thì khả nghi hơn | trace | TraceRCA (`T-13`) |

Ghi chú về RCD: theo `T-10`, phương pháp này coi sự cố như một **can thiệp** lên nguyên nhân gốc, và **không học toàn bộ đồ thị nhân quả** — nó chỉ chạy kiểm định độc lập có điều kiện theo lối phân cấp để tìm đích can thiệp, nhằm tránh chi phí của số lượng lớn phép kiểm định.

### Phân bố trong benchmark hiện có (`FACT`)

Trong 15 phương pháp tham chiếu của RCAEval:

| Nhóm | Số lượng | Tên |
|---|---|---|
| Dựa metric | **9** | RUN, CausalRCA, CIRCA, RCD, MicroCause, EasyRCA, MSCRED, BARO, ε-Diagnosis |
| Dựa trace | **2** | TraceRCA, MicroRank |
| Đa nguồn | **4** | PDiagnose, multi-source BARO, multi-source RCD, multi-source CIRCA |

Nhận định rút ra, ở mức `CANDIDATE`: hướng **"đồ thị phụ thuộc dựng từ trace"** mà giảng viên định hướng là nhóm **mỏng nhất** trong benchmark hiện tại — chỉ 2 trên 15. Việc nhận định này có trở thành luận cứ cho đóng góp của đồ án hay không thuộc `A10`, không kết luận tại đây.

---

## 5. Độ đo — công thức và cách đọc

### Công thức (theo `T-06`)

```
AC@k  =  trung bình trên mọi ca lỗi của:

              số nguyên nhân thật lọt vào top-k
         ────────────────────────────────────────
            min( k , số nguyên nhân thật của ca )


Avg@k =  ( AC@1 + AC@2 + ... + AC@k ) / k
```

### Điểm dễ nhầm nhất: `Avg@5` **không phải** `AC@5`

`Avg@5` là **trung bình của cả năm** giá trị `AC@1` đến `AC@5`. Hệ quả: nó **thưởng cho việc xếp nguyên nhân lên cao**.

Bảng dưới giả định một ca có đúng một nguyên nhân thật:

| Nguyên nhân thật nằm ở hạng | `AC@1` | `AC@3` | `AC@5` | **`Avg@5`** |
|---|---|---|---|---|
| 1 | 1 | 1 | 1 | **1,00** |
| 2 | 0 | 1 | 1 | **0,80** |
| 3 | 0 | 1 | 1 | **0,60** |
| 4 | 0 | 0 | 1 | **0,40** |
| 5 | 0 | 0 | 1 | **0,20** |
| ngoài top 5 | 0 | 0 | 0 | **0,00** |

Đọc bảng:

- **`AC@1`** — nghiêm khắc nhất, chỉ tính khi đoán trúng ngay lần đầu. Gần với giá trị vận hành thực tế nhất, vì người trực sẽ kiểm tra nghi phạm đầu tiên trước.
- **`AC@3`, `AC@5`** — dễ dần. Nhược điểm: hạng 1 và hạng 3 được điểm như nhau ở `AC@3`, nên không phân biệt được xếp giỏi với xếp may.
- **`Avg@5`** — vá đúng nhược điểm đó và hữu ích để mô tả chất lượng cả top 5, nhưng **không thay thế `AC@1`** khi câu hỏi vận hành là nghi phạm đầu tiên có đúng hay không.

### Ví dụ tính tay

Một ca lỗi có nguyên nhân thật là `database_cpu`. Thuật toán trả về:

```
hạng 1: payment_latency
hạng 2: database_cpu     ← nguyên nhân thật
hạng 3: booking_cpu
hạng 4: database_mem
hạng 5: auth_latency
```

→ `AC@1 = 0` · `AC@2 = 1` · `AC@3 = 1` · `AC@4 = 1` · `AC@5 = 1`
→ `Avg@5 = (0 + 1 + 1 + 1 + 1) / 5 = 0,80`

Lặp phép này cho toàn bộ ca lỗi của bộ dữ liệu rồi lấy trung bình, ra điểm của thuật toán.

### Độ đo của LEMMA-RCA

Bộ `T-08` dùng bộ độ đo khác nhưng cùng họ: `Precision@K`, `Mean Average Precision@K`, `Mean Reciprocal Rank`. Khi so sánh giữa hai nguồn phải nêu rõ đang dùng bộ độ đo nào; không trộn số của hai bộ độ đo vào một bảng.

---

## 6. Hai mức đánh giá — thô và mịn

`T-06` chấm ở hai mức, và mỗi ca lỗi được gán hai nhãn tương ứng:

| Mức | Đối tượng xếp hạng | Định nghĩa theo nguồn |
|---|---|---|
| **Thô** | Service | *root cause service* |
| **Mịn** | Chỉ số | *root cause indicator* — chỉ số hoặc log cụ thể chỉ ra nguyên nhân |

Ví dụ nhãn mức mịn theo `T-06`: lỗi CPU → chỉ số CPU của container; lỗi DELAY → chỉ số latency; lỗi mức mã nguồn → stack trace trong log của service tương ứng.

Chỉ số trong dữ liệu có dạng **`<tên service>_<loại tài nguyên>`**, ví dụ `cart_cpu`, `cart_mem`. Vì vậy một hệ 12 service sinh ra hàng chục tới hàng trăm chỉ số — đó là lý do số chỉ số ở §3 lớn hơn nhiều số service ở §2.

**Lưu ý về phạm vi:** tập chỉ số của các bộ công khai nghiêng về tài nguyên hạ tầng (CPU, bộ nhớ, đĩa, mạng) vì tập lỗi được chèn cũng là lỗi hạ tầng. Đây là đặc điểm của bộ dữ liệu, không phải giới hạn của khái niệm "chỉ số". Việc một hệ thống nghiệp vụ có thể có chỉ số ở tầng ứng dụng hay tầng nghiệp vụ là `CANDIDATE`, xử lý ở `A10`.

---

## 7. Mức sàn ngẫu nhiên

Một xếp hạng ngẫu nhiên trên `N` ứng viên, với một nguyên nhân thật, cho kỳ vọng:

```
AC@1  = 1/N
Avg@5 = 3/N
```

Suy ra `Avg@5`: nếu nguyên nhân thật rơi vào hạng `p ≤ 5` thì `Avg@5 = (6−p)/5`; lấy kỳ vọng trên `N` vị trí đều nhau được `(1/N)·(5+4+3+2+1)/5 = 3/N`.

Bảng tham chiếu:

| Số ứng viên thực sự được phép xếp hạng `N` | `AC@1` ngẫu nhiên | `Avg@5` ngẫu nhiên |
|---|---|---|
| 6 | 16,7 % | 50,0 % |
| 11 | 9,1 % | 27,3 % |
| 12 | 8,3 % | 25,0 % |
| 15 | 6,7 % | 20,0 % |
| 64 | 1,6 % | 4,7 % |
| 68 | 1,5 % | 4,4 % |
| 120 | 0,8 % | 2,5 % |
| 300 | 0,3 % | 1,0 % |

**Cách dùng bảng này.** Mọi con số hiệu năng phải đọc **cùng với** mức sàn tương ứng. Một điểm `Avg@5 = 0,85` trên 6 ứng viên và trên 300 ứng viên là hai kết quả khác nhau về bản chất. Vì vậy `E1` bắt buộc có đối chứng ngẫu nhiên và phải công bố cách lập tập ứng viên. Các dòng 11/12 và 64/68 minh họa chính sự khác nhau giữa **số service được mô tả** và **định danh ứng viên quan sát được**; không được chọn dòng có lợi hơn sau khi nhìn kết quả.

Hệ quả trực tiếp lên số ứng viên: đánh giá ở **mức thô** trên một hệ ít service cho mức sàn rất cao, còn đánh giá ở **mức mịn** đưa số ứng viên lên hàng trăm và khôi phục khả năng phân biệt. Đây là lý do kỹ thuật để chọn mức đánh giá, không phải nhận định về quy mô hệ thống nào tốt hơn.

---

## 8. Vấn đề `OPEN`

| ID | Vấn đề | Gate xử lý |
|---|---|---|
| `A7-OPEN-03` | Artifact Zenodo `T-07` giảng viên gửi chỉ có metric trong khi `DH-MT2` yêu cầu log và trace | `A8`; và câu hỏi gửi giảng viên, vòng 2 câu 1 |
| `A7-OPEN-05` | Tập ứng viên chính xác của từng cấu hình đánh giá chưa được khai báo thành manifest; Train Ticket được mô tả có 64 service nhưng các tệp chạy lộ ít nhất 68 định danh | `E1`: chuẩn hóa định danh và công bố candidate manifest trước khi so mức sàn |
| `A7-OPEN-06` | LEMMA có tuyên bố tổng quan về trace, nhưng chưa xác minh dữ liệu thô có đủ trace/span ID, quan hệ cha–con và cửa sổ bình thường/bất thường để chạy MicroRank hợp lệ hay không | Kiểm tra schema và mẫu dữ liệu thô trước khi viết adapter LEMMA → MicroRank |

`A7-OPEN-01` **đã đóng**: cận trên RE2 được xác minh là 376. `A7-OPEN-04` **đã đóng**: toàn văn BARO và cấu hình triển khai RCAEval đã được tách bạch ở `A10`; cả hai đều không dựng đồ thị, nhưng cấu hình benchmark không chạy đầy đủ bước MBOCPD của phương pháp gốc. `A7-OPEN-02` **đã đóng** theo `RES-032`: nó ghi mâu thuẫn giữa một ô tự kiểm của `A2` và định hướng ngày 2026-08-22; `A2-v0.2` đã gỡ hẳn ô đó.

---

## 9. Phép tự kiểm

- [x] Phân biệt rõ bốn nhóm tên riêng mà định hướng mới đưa vào; số vai ở tiêu đề §1, ở câu mở đầu, ở bảng và ở câu ghi nhớ **đều bằng bốn**.
- [x] Mỗi con số đều dẫn về một mã nguồn trong `source-register.md`.
- [x] Công thức độ đo chép theo nguồn, kèm ví dụ tính tay kiểm chứng được.
- [x] Mức sàn ngẫu nhiên có suy luận kèm theo, không chỉ nêu kết quả.
- [x] Không chọn phương pháp, không xếp hạng phương án, không chốt phạm vi đề tài.
- [x] Không sửa `A1`, `A2`, `A4` từ tệp này; mâu thuẫn với `A2` từng được ghi thành `OPEN` và đã đóng ở gate của chính `A2`, không đóng bằng một phán quyết tại đây.
- [x] Chỉ dùng tài liệu đã công bố và định hướng của giảng viên làm căn cứ; không dùng nguồn đối chiếu cài đặt nào.
- [x] Đã tách số service khỏi số thực thể/ứng viên đánh giá; đã đóng `A7-OPEN-01` và `A7-OPEN-04` bằng nguồn công bố và kiểm định triển khai.
- [ ] Đóng `A7-OPEN-05` và `A7-OPEN-06` bằng candidate manifest và kiểm tra schema LEMMA thực tế.
- [ ] Lê Văn Minh duyệt.

---

## 10. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A7-v0.3` | 2026-09-02 | Tách số microservice khỏi số định danh ứng viên; sửa Online Boutique thành 11 microservice chính thức và nêu chênh lệch 64/ít nhất 68 ở Train Ticket; xác minh RE2 có tối đa 376 chỉ số; hạ tuyên bố trace của LEMMA về `OPEN`; quy định `AC@1` là câu hỏi vận hành chính và mở manifest ứng viên/schema trace | Sửa mâu thuẫn bằng nguồn chính chủ + kiểm định tệp chạy |
| `A7-v0.2` | 2026-08-26 | §1 sửa *"ba vai"* thành **bốn vai** cho khớp bảng bốn dòng, và mở rộng câu ghi nhớ để không bỏ vai *hệ thống thử nghiệm*; khai lại đầu vào trỏ về tạo tác nguyên văn thư định hướng; đóng `A7-OPEN-02` theo `RES-032`; suy lại hai ô tự kiểm §9 | Sửa lỗi nội tại + đồng bộ `DH-TEN` |
| `A7-v0.1` | 2026-08-23 | Bản đầu: ba vai và bảng nhầm lẫn thường gặp; giải mã tên bộ dữ liệu; bốn họ thuật toán và họ nào dùng đồ thị; công thức `AC@k`/`Avg@k` kèm ví dụ; hai mức đánh giá; mức sàn ngẫu nhiên; bốn vấn đề `OPEN` | Tạo mới theo định hướng giảng viên ngày 2026-08-22 |
