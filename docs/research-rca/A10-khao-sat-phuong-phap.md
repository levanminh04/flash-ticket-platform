# A10 — Khảo sát phương pháp chẩn đoán nguyên nhân gốc và lập luận chọn đối chứng

> **Căn cứ hiện hành từ 18/09:** [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md). DH-* bên dưới giữ nguồn gốc khảo sát/kiểm định lịch sử; không là tên hoặc toàn bộ nhiệm vụ hiện hành. Số liệu và trạng thái khoa học của phiếu không được đổi bởi cập nhật này. Minh phụ trách chính RCA; phương pháp thử trên dữ liệu công khai trước rồi trên FlashTicket.

- **Phiên bản:** `A10-v0.5`
- **Trạng thái:** `REVIEW_READY` — đã hiệu chỉnh phả hệ công bố và mâu thuẫn MM-CIRCA theo nguồn chính chủ; chờ Lê Văn Minh và giảng viên phản biện
- **Phân lớp:** `FORMATION`
- **Người duyệt:** —
- **Ngày duyệt:** —
- **Đầu vào:** `A7-v0.3`, `A8-v0.3`, `A9-v0.3`, `E1-v0.2`; nguồn `T-06`–`T-20`; [thư định hướng ngày 2026-08-22](../evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md)
- **Đi vào báo cáo:** cơ sở lý thuyết, phương pháp thực nghiệm, giới hạn tái lập và lập luận cho MyRCA
- **Ranh giới:** phiếu này phân tích phương pháp RCA. Nó không chọn kiến trúc `B11-C`, không chốt tập node hay chuẩn quan sát của FlashTicket và không đọc repository cũ.

> **Kết luận:** không có phương pháp nào mạnh trong mọi bối cảnh. Một pipeline chạy hết không chứng minh RCA đúng. Đóng góp phù hợp của nhóm là **tổ hợp có kiểm chứng**, còn thiết kế và hiệu quả MyRCA vẫn là `CANDIDATE`/`OPEN` cho tới khi vượt cổng đánh giá tại `E1`.

---

## 1. Bốn họ cơ chế cần phân biệt

| Họ | Cơ chế lõi | Bằng chứng bắt buộc | Điểm mù điển hình |
|---|---|---|---|
| Thống kê thuần | So sánh phân phối hoặc độ lệch chuỗi thời gian rồi xếp hạng | metric có nhãn thực thể, mốc thời gian và cửa sổ hợp lệ | Dễ xếp cao hệ quả có biên độ lớn thay vì nguyên nhân |
| Suy luận nhân quả | Học quan hệ phụ thuộc thống kê và lần ngược ứng viên | metric đồng bộ, đủ mẫu; giả định của thuật toán phải phù hợp | Cấu trúc học được có thể bất ổn khi số biến lớn hoặc cửa sổ ngắn |
| Đồ thị lời gọi từ trace | Dựng quan hệ operation/service từ span rồi lan truyền/xếp hạng | trace ID, span ID, parent span, operation, thời gian, duration, status | Mù tại service không instrument, trace đứt hoặc lấy mẫu thiên lệch |
| Khai phá trace không lan truyền | So sánh trace bình thường/bất thường và tập thực thể xuất hiện | trace đầy đủ và ranh giới normal/abnormal | Không nhìn thấy lỗi chỉ biểu hiện ở metric; coverage thấp làm mất ứng viên |

Log là một nguồn bằng chứng riêng: có thể chấm theo mức tăng của mẫu log hoặc hợp nhất với metric. Kỹ thuật gom mẫu log chỉ là tiền xử lý; nó không tự dựng đồ thị và không tự chọn nguyên nhân.

---

## 2. Tập phương pháp thực sự được kiểm định

Báo cáo giữ **sáu mục phương pháp**, nhưng hai biến thể đa nguồn nằm chung một mục nên có **bảy phương pháp chạy được**. `Dummy` là đối chứng ngẫu nhiên, không phải phương pháp RCA thứ tám.

| Mục trong báo cáo | Phương pháp chạy | Họ | Mô tả công bố và hành vi mã đã kiểm tra |
|---|---|---|---|
| 1 | BARO | thống kê thuần | metric |
| 2 | CIRCA | suy luận nhân quả | metric |
| 3 | TraceRCA | trace, không lan truyền | trace |
| 4 | MicroRank | đồ thị lời gọi + PageRank | trace |
| 5 | MM-BARO; MM-CIRCA | đa nguồn | Bài WWW 2025 mô tả các biến thể đa nguồn xử lý metric cùng time series từ log và trace. Tại commit `6018cde`, MM-BARO có nhánh trace; loader của MM-CIRCA nạp/truyền cả ba nguồn nhưng hàm `mmcirca` chỉ đọc metric và log. Modality thật của 11 CSV vẫn `OPEN` |
| 6 | PC+RandomWalk | đồ thị học từ metric + lan truyền | metric, nhưng bản triển khai hiện tại có lỗi làm kết quả không kiểm chứng được tác dụng của đồ thị |
| Đối chứng | Dummy | ngẫu nhiên | danh sách ứng viên; phải có seed và manifest |

TORAI (`T-19`) và GALA+ (`T-20`) chỉ đặt **ranh giới nghiên cứu gần nhất**. Chúng không được thêm vào tập thực nghiệm chính sau khi đã xem kết quả, vì như vậy sẽ đổi giao thức giữa chừng. TORAI cung cấp mốc công bố mới có mã; GALA+ là prior art quan trọng cho fusion bằng RRF nhưng chưa có mã công khai do thỏa thuận công nghiệp.

**Quy tắc đọc MM-CIRCA:** luôn tách ba lớp bằng chứng. “Tác giả mô tả phương pháp thế nào”, “mã công khai tại commit cụ thể thực sự tiêu thụ trường nào” và “lượt chạy tạo CSV dùng mã/patch nào” là ba câu hỏi khác nhau. Hai lớp đầu đã kiểm được; lớp thứ ba chưa có run manifest và patch hash nên không được tự suy ra.

---

## 3. Kiểm định từng phương pháp

### 3.1 BARO — baseline metric mạnh, nhưng phải tách bài báo khỏi cấu hình RCAEval

BARO gốc (`T-09`) kết hợp phát hiện điểm đổi đa biến bằng MBOCPD với xếp hạng thống kê bền vững. Đường chạy RCAEval đã kiểm tra không thực thi toàn bộ quy trình đó: nó dùng thời điểm tiêm lỗi đã biết để chia trước/sau, chuẩn hóa bền vững rồi lấy độ lệch lớn nhất để xếp hạng.

- **Mạnh:** ít phụ thuộc trace; kết quả trên Train Ticket và Online Boutique cho thấy đây là baseline cần phải vượt.
- **Yếu:** cần mốc chia cửa sổ hợp lệ; có thể ưu tiên một metric hậu quả có biên độ lớn; tên metric phải ánh xạ chính xác về thực thể.
- **Hợp với:** CPU, bộ nhớ, đĩa và những lỗi làm chuỗi số thay đổi rõ.
- **Không được tuyên bố:** kết quả cấu hình RCAEval là tái lập toàn bộ BARO gốc.

### 3.2 CIRCA — mạnh trên Online Boutique, nhưng nhạy với số biến và cấu trúc học được

CIRCA học quan hệ nhân quả từ metric rồi truy vết nguyên nhân. Trong 90 ca Online Boutique đã kiểm định, CIRCA đạt `AC@1 = 0,667`, cao nhất trong nhóm có tệp kết quả của hệ này.

- **Mạnh:** có khả năng phân biệt nguyên nhân với hệ quả tốt hơn xếp hạng độ lệch thuần trong bối cảnh đã đo.
- **Yếu:** chi phí và độ ổn định giảm khi số metric tăng; cần đồng bộ thời gian và đủ mẫu; cấu trúc học từ quan sát không mặc nhiên là phụ thuộc kiến trúc thật.
- **Rủi ro pipeline chạy nhưng sai:** thuật toán vẫn trả hạng khi đồ thị học sai, hoặc nhánh fallback che giấu lỗi cấu trúc.

### 3.3 TraceRCA — khoanh vùng tốt khi trace đủ, nhưng coverage là điều kiện sống còn

TraceRCA (`T-13`) phát hiện trace bất thường, khai phá tập service nghi ngờ rồi xếp hạng. Nó không dùng PageRank.

- **Mạnh:** giải thích được bằng trace cụ thể; trên Train Ticket đạt `AC@1 = 0,644`, gần BARO `0,667`.
- **Yếu:** service không xuất hiện trong trace gần như không có cơ hội được chọn; timestamp, operation và parent-child phải đúng.
- **Bằng chứng coverage:** tập ứng viên quan sát trong CSV chỉ có 7 tên ở Online Boutique và 20 ở Train Ticket, thấp hơn đáng kể tập ứng viên đánh giá.
- **Điều kiện FlashTicket:** phải truyền context qua HTTP và RabbitMQ; nếu producer/consumer không nối trace, nguyên nhân ở luồng bất đồng bộ có thể biến mất khỏi đồ thị.

### 3.4 MicroRank — đúng hướng đồ thị nhưng hợp đồng đầu vào rất chặt

MicroRank (`T-11`, `T-16`) phân tách trace bình thường/bất thường, xây phổ thực thi và dùng personalized PageRank để xếp hạng operation/service.

- **Mạnh:** dùng cả cấu trúc gọi và sự khác nhau giữa hai nhóm trace; phù hợp lỗi độ trễ lan truyền theo request path.
- **Yếu:** cần đầy đủ trace ID, span ID, parent span, operation, duration, status và đủ hai nhóm trace; nhạy với sampling và điểm mù instrumentation.
- **Kết quả kiểm định:** trên Train Ticket, `AC@1 = 0,144`; 56/90 ca không có hạng ground truth. Nếu bỏ các ca thiếu thay vì tính thất bại, MRR sẽ bị thổi phồng từ `0,237` lên khoảng `0,627`.
- **Với LEMMA:** phải kiểm schema trace thô trước. Viết adapter về schema chuẩn; không sửa thuật toán riêng cho từng dataset và không gọi là MicroRank hợp lệ khi thiếu liên kết span.

### 3.5 MM-BARO và MM-CIRCA — mô tả công bố, mã công khai và lượt chạy không đồng nhất

Ba lớp bằng chứng hiện có là:

- **Công bố của tác giả (`T-06`):** phần baseline nói mã của multi-source BARO, RCD và CIRCA được cập nhật để xử lý time series từ log và trace; README hiện hành diễn đạt cả ba phương pháp dùng metric, log và trace.
- **Mã công khai tại commit `6018cde` (`T-15`):** loader nạp `logts`, `tracets_err`, `tracets_lat` rồi truyền cả ba cùng metric. MM-BARO có dùng time series trace khi khả dụng; riêng hàm `mmcirca` chỉ lấy `metric` và `logts`, hạ mẫu metric còn 15 giây, lọc tương quan rồi chạy PC + RHT. Release 1.4.0 của tác giả cũng mô tả MM-CIRCA là hợp nhất metric và log time series.
- **Lượt chạy tạo 11 CSV:** chưa có run manifest, trạng thái working tree hoặc patch hash. Commit ghi trong CSV không chứng minh không có bản vá cục bộ, nên modality thực sự của lượt chạy là `OPEN`.

Vì modality thực tế của run, tần suất mẫu, số biến và nhánh thuật toán chưa được cô lập, không thể gán chênh lệch kết quả cho “thêm log” hay “thêm trace”. Trên các CSV được cung cấp, MM-BARO gần BARO còn MM-CIRCA thấp hơn CIRCA; sau bootstrap theo cụm và hiệu chỉnh Holm, bằng chứng cho kết luận “MM-CIRCA gây hại” chưa đủ bền (`p_Holm = 0,0694` cho AC@1), và nguyên nhân không được quy cho một modality cụ thể.

### 3.6 PC+RandomWalk — bản hiện tại không đo được giá trị của đồ thị

Kiểm tra mã RCAEval tại commit được CSV khai báo cho thấy khi điểm bất thường đầu vào bằng 0, ma trận chuyển tiếp trở thành phân phối đều; điểm bắt đầu dùng lựa chọn ngẫu nhiên không seed; số bước chỉ bằng số node. Khi đó cạnh đồ thị hầu như không quyết định kết quả.

Do đó:

- kết quả `AC@1 = 0,033` trên Online Boutique chỉ mô tả **bản triển khai lỗi/không xác định**;
- không được dùng nó để kết luận đồ thị phụ thuộc vô ích;
- muốn đánh giá lại phải sửa bằng một test đồ thị nhỏ có đáp án, seed cố định, kiểm tổng xác suất và so ablation có/không cạnh.

### 3.7 Dummy — mức sàn, không phải một thuật toán RCA

Dummy phải dùng cùng candidate manifest với phương pháp được so, lặp nhiều seed và công bố khoảng tin cậy. CSV hiện để trống seed, nên con số chỉ là một lượt đối chứng chưa đủ tái lập.

---

## 4. Vì sao không được gọi so sánh hiện tại là “2×2”

Một thiết kế nhân tố 2×2 hợp lệ cần giữ nguyên mọi thứ ngoài hai yếu tố đang xét. Dữ liệu hiện tại không đạt điều đó:

| Cặp | Thay đổi ngoài modality | Hệ quả |
|---|---|---|
| BARO → MM-BARO | hạ mẫu metric 15 giây, đường tiền xử lý khác, có thể thêm nhiều loại dữ liệu | Chênh lệch không cô lập tác dụng log/trace |
| CIRCA → MM-CIRCA | mã công khai hạ mẫu, thêm biến log, lọc tương quan và có nhánh fallback; modality của lượt chạy CSV chưa xác minh | Chênh lệch không cô lập tác dụng log hoặc trace |

Cách viết hợp lệ là **so sánh quan sát bị trộn biến**. Muốn đo tác dụng modality, nhóm phải tự dựng ablation: cùng cửa sổ, cùng sampling, cùng candidate set, cùng lõi chấm điểm và chỉ bật/tắt một nguồn bằng chứng.

---

## 5. Ranh giới nghiên cứu gần nhất

### 5.1 Phả hệ công bố RCAEval và phạm vi Online Boutique

Ba snapshot `ase24 → www25 → fse26` trong kho tác giả gắn với ba công bố khác nhau, không phải ba revision của cùng một bài:

- Công trình ASE 2024 (`T-07`) là tiền thân metric-only và có công bố kết quả trên Online Boutique.
- Bài duy nhất mang tiêu đề *RCAEval* tại WWW Companion 2025 (`T-06`) có năm revision arXiv. Cả năm chỉ trình bày bảng hiệu năng RCA sơ bộ trên Train Ticket–RE2; Online Boutique vẫn là một hệ dùng để thu dataset nhưng không có bảng hiệu năng tương ứng trong bài này.
- Công trình TORAI tại FSE 2026 (`T-19`) tiếp tục dùng hạ tầng RCAEval và công bố kết quả trên Online Boutique.

Vì vậy câu hợp lệ là: **“Bài RCAEval tại WWW Companion 2025 không trình bày bảng hiệu năng RCA trên Online Boutique.”** Không được rút gọn thành “RCAEval không công bố kết quả Online Boutique”, vì câu đó xóa mất hai công bố còn lại.

### 5.2 TORAI

TORAI (`T-19`, FSE 2026) kết hợp telemetry đa nguồn và báo cáo trên Online Boutique, Sock Shop, Train Ticket. Nguồn này quan trọng vì công bố cả độ phủ trace: Online Boutique chỉ 7/11 service có trace, Train Ticket 27/64. Điều đó củng cố yêu cầu chất lượng dữ liệu và cơ chế từ chối, không chứng minh sẵn rằng MyRCA sẽ thắng.

Cả arXiv v1 ngày 2026-04-15 và v2 ngày 2026-04-18 mang tiêu đề *TORAI: Multi-source Root Cause Analysis for Blind Spots in Microservice Service Call Graph*. Cụm *TORAI: Unsupervised Fine-grained RCA using Multi-Source Telemetry Data* là tiêu đề Mục 3 của bài, không phải tiêu đề metadata của v1. Khi trích xuất bản, gọi v1/v2 là các revision của bản tiền in và gọi DOI `10.1145/3808137` là bản công bố chính thức tại PACMSE/FSE 2026.

### 5.3 GALA+

GALA+ (`T-20`, 2026) đã dùng Reciprocal Rank Fusion trong pipeline RCA. Vì vậy “dùng RRF để gộp nhiều hạng” không phải tính mới. Giá trị có thể bảo vệ của MyRCA phải nằm ở hợp đồng dữ liệu, trọng số theo chất lượng, ablation, trạng thái từ chối và bằng chứng trên dữ liệu giữ lại. Mã GALA+ chưa công khai, nên chỉ dùng làm prior art, không làm baseline tái lập kỳ này.

---

## 6. Lập luận chọn MyRCA để kiểm chứng

- `USER_CONFIRMED`: định vị đóng góp là **tổ hợp có kiểm chứng**.
- `CANDIDATE`: MyRCA v0 chuẩn hóa metric/log/span, chấm từng modality, hợp nhất hạng có trọng số chất lượng, thử hiệu chỉnh bằng đồ thị như một ablation và có quyền từ chối khi bằng chứng yếu.
- `OPEN`: MyRCA có vượt hoặc ít nhất không thua baseline mạnh về `AC@1` hay không; trọng số, ngưỡng từ chối, tập node và hiệu quả trên FlashTicket.

Quyết định này không hứa phát minh một thuật toán hoàn toàn mới. Một kết quả âm vẫn có giá trị nếu chứng minh minh bạch thành phần nào không giúp, trong điều kiện nào và vì sao.

---

## 7. Điều kiện thực nghiệm bắt buộc

1. Một bảng chỉ chứa ca từ cùng dataset/phiên bản; không trộn mức sàn khác nhau.
2. `AC@1` là thước đo chính; ca thiếu hạng là thất bại, không bị loại.
3. Mỗi run phải khóa commit, patch, dataset hash, candidate manifest, seed, cửa sổ và runtime.
4. Phân biệt lỗi chương trình với cột `success` trong CSV; cột này hiện bằng `AC@5`.
5. So sánh ghép cặp theo cùng `case_id`; bootstrap theo cụm `service × fault`; McNemar exact cho AC@1 và Holm cho nhiều kiểm định.
6. Không dùng ground truth để lọc ứng viên ở pipeline triển khai. Nếu dùng để thăm dò thì phải ghi **oracle upper bound**.
7. Không chọn trọng số/graph rule trên cùng tập dùng báo cáo kết quả cuối.

---

## 8. Vấn đề còn mở

| ID | Trạng thái | Vấn đề | Điều kiện đóng |
|---|---|---|---|
| `A10-OPEN-02` | Đã đóng | BARO gốc và cấu hình RCAEval đã được phân biệt | Kiểm tra bài BARO và mã RCAEval |
| `A10-OPEN-03` | `OPEN` | Mô tả công bố và hành vi mã công khai của MM-CIRCA đã kiểm tra, nhưng chưa biết 11 CSV được tạo từ working tree/patch nào | Run manifest, lockfile, trạng thái working tree và patch hash |
| `A10-OPEN-04` | Đã thay | Hướng đóng góp không còn là hai ứng viên ngang nhau | Lê Văn Minh chọn “tổ hợp có kiểm chứng”; hiệu quả MyRCA chuyển sang `A10-OPEN-07` |
| `A10-OPEN-05` | Đã đóng cho E1 | Đã có 90 ca Online Boutique và 90 ca Train Ticket cho các phương pháp khả dụng | Không có nghĩa hai hệ có cùng tập phương pháp |
| `A10-OPEN-06` | Đã thu hẹp | TORAI/GALA+ đã có nguồn; các tên tham khảo khác không được dùng nếu chưa bổ sung nguồn | Chỉ mở lại khi đưa chúng vào lập luận |
| `A10-OPEN-07` | `OPEN` | MyRCA có đạt cổng `AC@1`, ổn định held-out và từ chối đúng khi dữ liệu kém hay không | Thực nghiệm tuần 2 và tập kiểm tra giữ lại |
| `A10-OPEN-08` | `OPEN` | Sửa và kiểm chứng PC+RandomWalk | Unit test đồ thị, seed, ablation và rerun |

---

## 9. Phép tự kiểm

- [x] Sáu mục báo cáo và bảy phương pháp chạy được đã được đếm riêng; Dummy là đối chứng.
- [x] MM-CIRCA được tách đủ ba lớp: công bố `metric + log + trace`; mã công khai tại `6018cde` tiêu thụ `metric + log`; modality của 11 CSV là `OPEN`.
- [x] Không gọi cặp multi-source là thí nghiệm 2×2.
- [x] Tách BARO gốc khỏi cấu hình RCAEval dùng mốc tiêm lỗi biết trước.
- [x] PC+RandomWalk không còn được dùng để suy giá trị của đồ thị.
- [x] TORAI/GALA+ chỉ là ranh giới nghiên cứu, không thêm hồi tố vào thực nghiệm chính.
- [x] Chỉ “tổ hợp có kiểm chứng” là `USER_CONFIRMED`; thiết kế và hiệu quả MyRCA vẫn `CANDIDATE`/`OPEN`.
- [x] Không đọc repository cũ và không quyết định thay `B11-C`/`B16`.
- [ ] Lê Văn Minh và giảng viên duyệt.

---

## 10. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A10-v0.5` | 2026-09-02 | Thu hồi kết luận tuyệt đối về modality MM-CIRCA; tách công bố–mã–lượt chạy; phân biệt năm revision bài RCAEval với ba snapshot công bố; định danh đúng phạm vi Online Boutique và tiêu đề TORAI | Sửa theo nguồn chính chủ của tác giả |
| `A10-v0.4` | 2026-09-02 | Kiểm định lại tập thực nghiệm; sửa modality MM-CIRCA, thiết kế “2×2”, BARO trong RCAEval và PC+RandomWalk; thêm TORAI/GALA+ làm ranh giới; định vị MyRCA theo quyết định “tổ hợp có kiểm chứng” | Sửa mâu thuẫn và bổ sung bằng chứng |
| `A10-v0.3` | 2026-08-27 | Ghi trạng thái ứng viên của kỹ thuật gom mẫu log | Tiếp nhận từ bộ hệ thống |
| `A10-v0.2` | 2026-08-26 | Đối chiếu Bảng 6 RCAEval và sửa số RCD/MicroRank | Sửa sau khi mở nguồn gốc |
| `A10-v0.1` | 2026-08-23 | Bản khảo sát đầu | Tạo mới |
