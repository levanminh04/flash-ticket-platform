# E1 — Kiểm định RCAEval và nghiên cứu khả thi MyRCA

> **Căn cứ hiện hành từ 18/09:** [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md). DH-* bên dưới giữ nguồn gốc khảo sát/kiểm định lịch sử; không là tên hoặc toàn bộ nhiệm vụ hiện hành. Số liệu và trạng thái khoa học của phiếu không được đổi bởi cập nhật này. Minh phụ trách chính RCA; phương pháp thử trên dữ liệu công khai trước rồi trên FlashTicket.

- **Phiên bản:** `E1-v0.3`
- **Trạng thái:** `REVIEW_READY` — đã kiểm định lại phả hệ công bố, tính toàn vẹn 990 dòng và giới hạn suy luận thống kê; chờ Lê Văn Minh và giảng viên phản biện
- **Phân lớp:** `FORMATION`
- **Ngày khóa bằng chứng:** 2026-09-02
- **Người duyệt:** —
- **Đầu vào kiểm định:** thư định hướng ngày 2026-08-22 (`DH-TEN`, `DH-MT1`–`DH-MT4`, `DH-DATA`, `DH-DO`, `DH-MOC`); các bản trước hiệu chỉnh `A7-v0.2`, `A8-v0.2`, `A9-v0.2`, `A10-v0.3`; nguồn `T-06`–`T-21`; 11 tệp CSV do nhóm cung cấp; mã nguồn RCAEval tại commit mà CSV khai báo
- **Tạo tác đồng bộ sau kiểm định:** `A7-v0.3`, `A8-v0.3`, `A9-v0.4`, `A10-v0.5`. Các bản này dẫn ngược về E1; chúng không được khai là đầu vào sinh ra E1.
- **Ranh giới:** tài liệu này kiểm định phương pháp và đề xuất một thiết kế nghiên cứu. Nó **không** chọn kiến trúc B11-C, không quyết định số service nghiệp vụ, không chốt schema/hợp đồng/chuẩn quan sát và không đọc repository cũ. Thông tin khả thi Java/Spring Boot, gateway, Keycloak, PostgreSQL, MongoDB, Redis và RabbitMQ chỉ được nhận qua `B11-B-v0.6` đã duyệt; không dùng để sinh ranh giới đích.

> **Kết luận ngắn:** xây được pipeline MyRCA là khả thi; xây được pipeline **không đồng nghĩa** chẩn đoán đúng. Hướng có rủi ro thấp nhất là một **tổ hợp có kiểm chứng**: chuẩn hóa dữ liệu, chấm điểm metric/log/trace riêng, hợp nhất thứ hạng có kiểm soát chất lượng, rồi chỉ giữ bước hiệu chỉnh bằng đồ thị nếu ablation chứng minh nó giúp trên dữ liệu chưa dùng để lựa chọn. Không đặt mục tiêu “phát minh thuật toán hoàn toàn mới” và không cam kết vượt baseline trước khi đo.

---

## 1. Trả lời trực tiếp hai câu hỏi mở đầu

### 1.1 Năm service có đồng nghĩa đồ thị RCA chỉ khoảng mười node không?

**Không.** Số service nghiệp vụ và số node RCA là hai đại lượng khác nhau. Trộn chúng làm một sẽ vừa tính sai mức sàn ngẫu nhiên, vừa tạo một đồ thị không rõ mỗi node đại diện cho điều gì.

| Mức đồ thị | Node đại diện cho | Với một hệ có 5 service nghiệp vụ |
|---|---|---|
| Service thuần | Mỗi service là một node | Có thể đúng 5 node |
| Thực thể vận hành | Service, gateway, định danh, cơ sở dữ liệu, cache, message broker, API đối tác | Lớn hơn 5; chưa được phép chốt con số tại E1 |
| Thao tác | Endpoint, RPC operation, consumer/producer hoặc thao tác cơ sở dữ liệu | Có thể lên hàng chục dù chỉ có 5 service |
| Bằng chứng | Metric, mẫu log, span, lỗi/status | Chủ yếu **gắn vào** node service/thao tác; không mặc nhiên là node ngang hàng |

Nguồn TORAI (`T-19`) minh họa độ chênh này trên RE2: Online Boutique được mô tả có 11 service, 77 metric, `33 ± 9` mẫu log và 17 trace operation; Train Ticket có 64 service, 376 metric, `163 ± 44` mẫu log và `148,3 ± 26` trace operation. Đây không phải phép quy đổi cố định, mà là bằng chứng rằng số service không quyết định số biến quan sát hay số thao tác.

**Quy tắc cho FlashTicket:** B11-C sau này ghi riêng số service nghiệp vụ và tập thực thể vận hành được phép xếp hạng; B16 mới ghi tập chỉ số quan sát. `R0-OPEN-06` vì vậy vẫn `OPEN`. E1 không được dùng câu hỏi RCA để ép kiến trúc thành nhiều hay ít service.

### 1.2 Muốn chạy MicroRank trên LEMMA có phải đọc dataset trước rồi sửa code không?

Phải đọc **hợp đồng đầu vào của thuật toán** và **schema thực tế của dataset** trước. Nhưng cách làm đúng là viết adapter về một schema chuẩn, không tạo một bản thuật toán riêng cho mỗi dataset.

MicroRank cần tối thiểu:

1. `trace_id`, `span_id` và tham chiếu span cha; tham chiếu cha chỉ được trống ở span gốc;
2. `service_name`/instance và `operation_name`;
3. thời điểm bắt đầu, `duration` và `span_kind` để nhận diện vai trò server/frontend theo cài đặt chính chủ;
4. cửa sổ bình thường và bất thường hoặc một mốc phát hiện hợp lệ;
5. đủ trace bình thường để ước lượng SLO theo operation và đủ trace bất thường để so sánh phổ thực thi.

Trạng thái span là trường bổ trợ hữu ích cho adapter/kiểm tra chất lượng, nhưng không được khai là đầu vào tối thiểu của lõi MicroRank hiện hành.

Kho LEMMA chính chủ (`T-17`) nói JSON thô có metric, log và “even trace data”, nhưng pipeline tiền xử lý và baseline công khai mô tả rõ metric và log không cấu trúc hơn là một schema span đầy đủ. Chưa có bằng chứng rằng dữ liệu trace thô giữ đủ ID cha–con, ranh giới cửa sổ và độ phủ cần cho MicroRank. Vì vậy:

- nếu đủ trường: viết `LEMMA adapter -> canonical span schema -> MicroRank`;
- nếu thiếu tên trường nhưng còn đủ ý nghĩa: ánh xạ tên/cỡ thời gian trong adapter;
- nếu thiếu trace ID, parent span hoặc cửa sổ so sánh: **không được gọi đó là chạy MicroRank hợp lệ**;
- không sửa lõi MicroRank chỉ để ép một dataset thiếu dữ liệu cho ra kết quả.

`A7-OPEN-06` và `A8-OPEN-08` giữ việc kiểm tra này ở trạng thái `OPEN` cho tới khi lấy mẫu dữ liệu thô và lập schema manifest.

---

## 2. Phạm vi bằng chứng và cách kiểm định

### 2.1 Dữ liệu thực nghiệm đã nhận

- 11 tệp CSV, tổng 990 dòng kết quả.
- Mỗi tệp có 90 `case_id` duy nhất; không có dòng trùng.
- Trong từng hệ, mọi phương pháp dùng cùng tập 90 ca; từng `case_id` cũng khớp cùng nhãn service và loại lỗi giữa các phương pháp.
- Mỗi hệ có 5 service được tiêm lỗi × 6 loại lỗi × 3 lần lặp = 90 ca, tương ứng 30 cụm `service × fault`.
- Tất cả CSV khai `window = 20` và `commit_hash = 6018cde`.
- Toàn bộ `runtime_sec` và `seed` đều trống.
- Cột `success` bằng đúng `ac_5` ở mọi dòng: nó biểu diễn **ground truth có nằm trong top 5**, không biểu diễn chương trình chạy thành công.
- Mọi hạng từ 1 đến 5 khớp đúng vị trí nhãn trong năm cột dự đoán; `AC@1`, `AC@3`, `AC@5` và `Avg@5` khớp công thức từ `ground_truth_rank` ở cả 990 dòng.
- Không có ca nào lặp cùng một định danh trong top 5; tập đúng 11 tệp và SHA-256 của từng tệp khớp bảng §10.

### 2.2 Quy tắc thống kê đã dùng

- `AC@1` là độ đo chính vì người vận hành kiểm tra nghi phạm đầu tiên trước.
- `AC@3`, `AC@5`, `Avg@5` và MRR là độ đo phụ.
- Ground-truth rank bị thiếu được tính reciprocal rank bằng 0; không loại dòng đó khỏi MRR.
- Khoảng tin cậy percentile 95% lấy mẫu lại theo 30 cụm `service × fault`, không giả định ba lần lặp trong cùng cụm là độc lập hoàn toàn. Đây là mô tả độ bất định của tập ca hiện có, không thay thế một thực nghiệm xác nhận đã đăng ký trước.
- So sánh AC@1 theo cặp có báo McNemar exact trên 90 dòng như một **phân tích nhạy cảm**. P-value này giả định các cặp độc lập, trong khi ba lần lặp cùng cụm có thể phụ thuộc; vì vậy nó không phải căn cứ suy luận chính.
- Holm được tính riêng cho bốn cặp Online Boutique và ba cặp Train Ticket. Hai họ giả thuyết được chọn trong quá trình kiểm định hậu nghiệm, không được đăng ký trước khi nhìn kết quả; p-value đã hiệu chỉnh vẫn chỉ có giá trị thăm dò.
- Bootstrap dùng 50.000 lần lấy mẫu lại, seed kiểm định `20260902`. Seed này thuộc **phép kiểm định E1**, không phải seed chạy thuật toán trong các CSV.

### 2.3 Giới hạn của bằng chứng

1. CSV không chứa lệnh chạy, manifest môi trường, hash dataset hay bản vá mã nguồn.
2. Commit `6018cde96694e775360f5f4b8f5e68ffe54d439d` tồn tại trong lịch sử RCAEval và chỉ sửa đường dẫn bài báo trong README; mã phương pháp không khác snapshot liền kề đã kiểm tra. Commit này không chứa ba bản vá trace mà báo cáo cũ mô tả.
3. Tại commit `6018cde`, nhánh đa nguồn trong `main.py` nạp `logts.csv`, `tracets_err.csv` và `tracets_lat.csv`, rồi truyền chúng cùng metric vào hàm phương pháp. Riêng hàm `mmcirca` chỉ lấy `metric` và `logts`. Đây là kiểm tra **trace-derived time series ở consumer**, không phải kết luận dựa trên việc có hay không có raw trace loader.
4. Thư mục kết quả chung không được dọn cho mọi phương pháp; kết quả cũ có thể lẫn vào lần chấm sau nếu quy trình bên ngoài không dọn.
5. `runtime_sec` và `seed` trống nên không thể tái lập tuyên bố tốc độ hoặc tính xác định chỉ từ CSV.
6. Mọi so sánh, bootstrap và phân tích theo nhóm ở E1 được thực hiện sau khi đã nhận kết quả. Chúng phù hợp để phát hiện vấn đề và sinh giả thuyết, không đủ để tuyên bố xác nhận hoặc không-thua trên dữ liệu giữ lại.

### 2.4 Khóa phả hệ công bố và ba lớp bằng chứng MM-CIRCA

Các kết luận dưới đây được đối chiếu trực tiếp từ bài báo, DOI, kho và release của tác giả; không suy từ bản báo cáo do nhóm cung cấp.

| Đối tượng | `FACT` đã kiểm chứng | Giới hạn diễn giải |
|---|---|---|
| Bài RCAEval | arXiv `2412.17015` có **5 revision** từ v1 ngày 2024-12-22 đến v5 ngày 2025-02-03; bản chính thức là WWW Companion 2025, DOI `10.1145/3701716.3715290` | Không gọi `ase24/www25/fse26` là ba revision của bài; đó là ba snapshot repository gắn với ba công bố |
| Online Boutique | Công trình ASE 2024 có bảng kết quả Online Boutique; bài RCAEval WWW 2025 chỉ trình bày bảng hiệu năng trên Train Ticket–RE2; TORAI/FSE 2026 lại có kết quả Online Boutique | Chỉ được viết “bài RCAEval tại WWW 2025 không trình bày bảng hiệu năng RCA trên Online Boutique”, không viết “RCAEval không công bố kết quả Online Boutique” |
| MM-CIRCA theo công bố | §4.1 bài WWW 2025 nói mã multi-source BARO/RCD/CIRCA được cập nhật để xử lý time series từ log và trace; README hiện hành mô tả các phương pháp đa nguồn dùng metric, log và trace | Đây là mô tả học thuật/tài liệu của tác giả, chưa chứng minh một commit hay lượt chạy cụ thể thực sự tiêu thụ đủ ba nguồn |
| MM-CIRCA theo mã `6018cde` | Loader nạp/truyền metric, log và hai trace-derived time series; hàm `mmcirca` chỉ đọc metric và log. Release 1.4.0 cũng mô tả MM-CIRCA là hợp nhất metric và log time series | Không được dùng hành vi một snapshot để đổi định nghĩa công bố thành “MM-CIRCA chỉ có hai nguồn” |
| MM-CIRCA trong 11 CSV | CSV chỉ khai commit; không có run manifest, working-tree state hay patch hash | Modality thật của lượt chạy là `OPEN`; không được quy chênh lệch kết quả cho log hoặc trace |
| TORAI | arXiv v1 ngày 2026-04-15 và v2 ngày 2026-04-18 đều mang tiêu đề *TORAI: Multi-source Root Cause Analysis for Blind Spots in Microservice Service Call Graph*; bản chính thức là PACMSE 3(FSE), Article FSE130, DOI `10.1145/3808137` | *Unsupervised Fine-grained RCA using Multi-Source Telemetry Data* là tiêu đề Mục 3, không phải tiêu đề metadata của v1 |

---

## 3. Kết quả kiểm định 990 ca

### 3.1 Kết quả tổng hợp tính lại từ CSV

Mỗi ô ghi `trung bình [cận dưới; cận trên]` của khoảng tin cậy percentile 95% theo 30 cụm `service × fault`, 50.000 lần lấy mẫu lại. MRR cho ca không có hạng bằng 0.

| Hệ | Phương pháp | AC@1 | AC@3 | AC@5 | Avg@5 | MRR |
|---|---|---|---|---|---|---|
| OB | CIRCA | 0,667 [0,556; 0,767] | 0,889 [0,822; 0,944] | 0,911 [0,844; 0,967] | 0,840 [0,769; 0,902] | 0,785 [0,709; 0,854] |
| OB | MM-CIRCA | 0,544 [0,411; 0,678] | 0,811 [0,700; 0,911] | 0,922 [0,844; 0,978] | 0,776 [0,682; 0,858] | 0,698 [0,603; 0,789] |
| OB | MM-BARO | 0,167 [0,056; 0,300] | 0,878 [0,778; 0,967] | 0,967 [0,911; 1,000] | 0,756 [0,682; 0,818] | 0,540 [0,469; 0,618] |
| OB | BARO | 0,144 [0,033; 0,278] | 0,878 [0,767; 0,967] | 0,944 [0,878; 1,000] | 0,742 [0,660; 0,811] | 0,526 [0,456; 0,603] |
| OB | TraceRCA | 0,100 [0,022; 0,200] | 0,656 [0,500; 0,800] | 0,956 [0,889; 1,000] | 0,620 [0,531; 0,707] | 0,432 [0,362; 0,510] |
| OB | PC+RandomWalk | 0,033 [0,000; 0,067] | 0,433 [0,311; 0,556] | 0,656 [0,544; 0,767] | 0,387 [0,300; 0,473] | 0,304 [0,261; 0,346] |
| OB | Dummy | 0,111 [0,044; 0,189] | 0,256 [0,156; 0,367] | 0,389 [0,278; 0,500] | 0,247 [0,164; 0,338] | 0,271 [0,211; 0,340] |
| TT | BARO | 0,667 [0,522; 0,800] | 0,822 [0,711; 0,922] | 0,867 [0,767; 0,956] | 0,800 [0,691; 0,898] | 0,760 [0,648; 0,863] |
| TT | TraceRCA | 0,644 [0,522; 0,767] | 0,767 [0,667; 0,856] | 0,811 [0,722; 0,889] | 0,753 [0,660; 0,842] | 0,731 [0,639; 0,822] |
| TT | MicroRank | 0,144 [0,033; 0,278] | 0,378 [0,222; 0,544] | 0,378 [0,222; 0,544] | 0,302 [0,173; 0,438] | 0,237 [0,126; 0,361] |
| TT | Dummy | 0,033 [0,000; 0,078] | 0,078 [0,033; 0,133] | 0,122 [0,067; 0,178] | 0,080 [0,040; 0,124] | 0,103 [0,072; 0,140] |

Các trung bình trong báo cáo cũ khớp bảng tính lại này. Vấn đề chính không phải phép cộng sai, mà là **cách diễn giải vượt quá điều dữ liệu và mã nguồn chứng minh được**. Các khoảng trên cũng không được đọc như bằng chứng xác nhận, vì giao thức và giả thuyết chưa được đăng ký trước khi xem dữ liệu.

### 3.2 So sánh ghép cặp thăm dò trên Online Boutique

Quy ước: chênh lệch bằng phương pháp bên phải trừ phương pháp bên trái. “Sửa đúng” là ca bên trái sai top-1 nhưng bên phải đúng; “làm hỏng” là chiều ngược lại. McNemar/Holm là phân tích nhạy cảm theo 90 dòng; CI là bootstrap theo 30 cụm và **không** phải khoảng đồng thời đã hiệu chỉnh cho nhiều cặp. Vì các cặp được chọn sau khi đã có dữ liệu, cột p và CI **không phải kiểm định xác nhận**.

| So sánh | Sửa đúng / làm hỏng | McNemar p | Holm p | ΔAC@1, CI cụm 95% | ΔAvg@5, CI cụm 95% | Diễn giải thận trọng |
|---|---:|---:|---:|---:|---:|---|
| BARO → MM-BARO | 3 / 1 | 0,625 | 0,625 | +0,022 [-0,022; +0,067] | +0,013 [-0,004; +0,033] | Chênh lệch nhỏ và cả hai CI cắt 0; chưa thấy cải thiện ổn định |
| CIRCA → MM-CIRCA | 6 / 17 | 0,0347 | 0,0694 | -0,122 [-0,233; -0,022] | -0,064 [-0,138; +0,007] | Cấu hình MM-CIRCA thấp hơn về AC@1 trong mẫu; Avg@5 chưa tách khỏi 0 và không được quy nguyên nhân cho trace |
| BARO → CIRCA | 51 / 4 | < 0,000001 | < 0,000001 | +0,522 [+0,356; +0,678] | +0,098 [+0,047; +0,144] | Chênh lệch hậu nghiệm lớn trên OB; chưa chứng minh nguyên nhân chỉ là “có đồ thị” |
| MM-BARO → MM-CIRCA | 43 / 9 | 0,000002 | 0,000006 | +0,378 [+0,167; +0,567] | +0,020 [-0,073; +0,104] | AC@1 hậu nghiệm cao hơn; Avg@5 chưa cho thấy chênh lệch ổn định |

Không được gọi bốn ô này là một thiết kế nhân tố 2×2 cô lập ảnh hưởng của modality và đồ thị: MM-BARO/MM-CIRCA vừa đổi dữ liệu, vừa hạ mẫu metric xuống 15 giây; CIRCA/MM-CIRCA còn khác số biến, lọc tương quan và đường lỗi dự phòng.

### 3.3 So sánh ghép cặp thăm dò trên Train Ticket

| So sánh | Sửa đúng / làm hỏng | McNemar p | Holm p | ΔAC@1, CI cụm 95% | ΔAvg@5, CI cụm 95% | Diễn giải thận trọng |
|---|---:|---:|---:|---:|---:|---|
| BARO → TraceRCA | 18 / 20 | 0,871 | 0,871 | -0,022 [-0,189; +0,156] | -0,047 [-0,173; +0,089] | Hai phương pháp có trung bình gần nhau nhưng sửa/hỏng các ca khác nhau; chưa thấy bên nào hơn ổn định |
| BARO → MicroRank | 8 / 55 | < 0,000001 | < 0,000001 | -0,522 [-0,722; -0,300] | -0,498 [-0,664; -0,322] | Lượt MicroRank trong CSV thấp hơn rõ về mô tả; không suy rộng thành kết luận về thuật toán gốc |
| TraceRCA → MicroRank | 4 / 49 | < 0,000001 | < 0,000001 | -0,500 [-0,656; -0,333] | -0,451 [-0,596; -0,302] | Cùng cảnh báo: kết quả nói về lượt chạy thiếu hồ sơ tái lập, không phải mọi cấu hình MicroRank |

BARO và TraceRCA có AC@1 gần nhau nhưng bất đồng ở 38/90 ca. Đây là tín hiệu đáng thử fusion trên **development split**; không phải quyền dùng 90 ca này vừa chọn cấu hình vừa báo kết quả cuối.

### 3.4 Kết quả thay đổi theo service bị tiêm lỗi và loại lỗi

Bảng service cho biết tỷ lệ top-1 đúng khi lỗi được tiêm vào từng service cụ thể. Nó ngăn một trung bình đẹp che việc phương pháp chỉ làm tốt ở một khu vực.

| OB — phương pháp | Checkout | Currency | Email | Product Catalog | Recommendation |
|---|---:|---:|---:|---:|---:|
| BARO | 0,000 | 0,000 | 0,278 | 0,278 | 0,167 |
| CIRCA | 0,778 | 0,444 | 0,944 | 0,556 | 0,611 |
| Dummy | 0,111 | 0,111 | 0,222 | 0,000 | 0,111 |
| MM-BARO | 0,000 | 0,000 | 0,278 | 0,333 | 0,222 |
| MM-CIRCA | 0,667 | 0,389 | 0,556 | 0,389 | 0,722 |
| PC+RandomWalk | 0,167 | 0,000 | 0,000 | 0,000 | 0,000 |
| TraceRCA | 0,500 | 0,000 | 0,000 | 0,000 | 0,000 |

| TT — phương pháp | Xác thực | Đơn hàng | Tuyến | Tàu | Hành trình |
|---|---:|---:|---:|---:|---:|
| BARO | 0,444 | 0,611 | 0,889 | 1,000 | 0,389 |
| Dummy | 0,056 | 0,056 | 0,000 | 0,056 | 0,000 |
| MicroRank | 0,722 | 0,000 | 0,000 | 0,000 | 0,000 |
| TraceRCA | 0,667 | 0,278 | 0,833 | 0,944 | 0,500 |

MicroRank trong gói CSV đạt 0,722 ở service xác thực nhưng bằng 0 ở bốn service còn lại. TraceRCA trên OB đạt 0,500 ở checkout và bằng 0 ở bốn service còn lại. Hai hình dạng này quan trọng hơn việc chỉ nói “điểm trung bình thấp”: chúng gợi ý độ phủ/ánh xạ bị co hẹp và cần kiểm tra input trước khi phán xét cơ chế phương pháp.

Bảng loại lỗi cho biết cùng phương pháp phản ứng khác nhau thế nào với lỗi tài nguyên (`cpu`, `disk`, `mem`, `socket`) và lỗi mạng (`delay`, `loss`).

| OB — phương pháp | CPU | Delay | Disk | Loss | Memory | Socket |
|---|---:|---:|---:|---:|---:|---:|
| BARO | 0,000 | 0,000 | 0,133 | 0,400 | 0,333 | 0,000 |
| CIRCA | 0,600 | 0,467 | 0,867 | 0,667 | 0,800 | 0,600 |
| Dummy | 0,133 | 0,133 | 0,000 | 0,000 | 0,133 | 0,267 |
| MM-BARO | 0,000 | 0,000 | 0,067 | 0,467 | 0,467 | 0,000 |
| MM-CIRCA | 0,600 | 0,200 | 0,933 | 0,467 | 0,533 | 0,533 |
| PC+RandomWalk | 0,067 | 0,000 | 0,067 | 0,000 | 0,067 | 0,000 |
| TraceRCA | 0,067 | 0,200 | 0,200 | 0,067 | 0,000 | 0,067 |

| TT — phương pháp | CPU | Delay | Disk | Loss | Memory | Socket |
|---|---:|---:|---:|---:|---:|---:|
| BARO | 0,467 | 0,467 | 1,000 | 0,533 | 0,933 | 0,600 |
| Dummy | 0,067 | 0,000 | 0,000 | 0,000 | 0,133 | 0,000 |
| MicroRank | 0,200 | 0,133 | 0,000 | 0,133 | 0,200 | 0,200 |
| TraceRCA | 0,600 | 0,867 | 0,600 | 0,600 | 0,533 | 0,667 |

Trên TT, BARO nổi bật ở disk/memory còn TraceRCA nổi bật ở delay. Đây là lời giải thích cụ thể cho tính bổ sung tiềm năng của hai nguồn bằng chứng; vẫn cần split mới để kiểm tra fusion chứ chưa được lấy chính các khác biệt này làm trọng số cuối.

### 3.5 Độ phủ và thiên lệch hạng 1

| Cấu hình | Node chiếm hạng 1 | Số ca | Số tên khác nhau xuất hiện trong top 5 | Cảnh báo |
|---|---|---:|---:|---|
| OB BARO | `redis` | 77/90 | 11 | Redis không nằm trong 5 nhãn tiêm lỗi |
| OB MM-BARO | `redis` | 75/90 | 13 | Có thêm alias `frontendservice`, `traceservice` |
| OB TraceRCA | `frontendservice` | 71/90 | 7 | Chỉ nhìn được vùng có trace |
| OB PC+RandomWalk | `cartservice` | 68/90 | 12 | Kết quả bị chi phối bởi triển khai random walk lỗi |
| TT MicroRank | `ts-auth-service` / `ts-station-service` | 56 / 33 | 10 | Gần như chỉ chọn hai node |
| TT BARO | `ts-train-service` là lớn nhất | 20/90 | 47 | Phân tán hơn OB |
| TT TraceRCA | `ts-train-service`, `ts-route-service` | 18 / 18 | 20 | Vẫn bị giới hạn bởi trace coverage |
| TT Dummy | không có node áp đảo | tối đa 5/90 | 68 | Lộ ít nhất 68 định danh ứng viên |

Trong MM-BARO trên OB, `frontendservice` xuất hiện 27 lần trong top 5 và `traceservice` xuất hiện một lần, trong khi metric/log dùng `frontend`. Đây là lỗi chuẩn hóa định danh. Nó có thể làm sai mẫu số ứng viên, tách một thực thể thành nhiều node và làm fusion/đồ thị đếm trùng.

### 3.6 Trường hợp MicroRank bị thiếu kết quả

- 56/90 ca không có `ground_truth_rank`.
- 11/90 ca có ít hơn 5 dự đoán.
- `ts-order-service`: 0/18 ca có ground truth trong danh sách.
- `ts-travel-service`: 0/18.
- `ts-train-service`: 1/18.
- `ts-auth-service`: 15/18.
- `ts-route-service`: 18/18.

Nếu bỏ 56 dòng thiếu, MRR tăng giả tạo lên khoảng 0,627. Khi giữ đủ 90 ca và gán reciprocal rank bằng 0, MRR đúng là 0,237. Vì thế quy ước xử lý kết quả thiếu phải được ghi trước khi chạy.

### 3.7 Hai phân tích thăm dò không được nâng thành kết quả chính

**Oracle candidate filter.** Khi loại mọi node chưa từng bị tiêm lỗi bằng chính ground truth toàn bộ benchmark, AC@1 của BARO trên OB tăng từ 0,144 lên 0,867 (+0,722). Đây là cận trên về lợi ích của việc hạn chế tập ứng viên; nó không đo tác dụng của đồ thị vì pipeline triển khai không biết trước danh sách node sẽ hỏng.

**Hợp nhất thứ hạng RRF.** BARO và TraceRCA bổ sung nhau trên TT: 40 ca cả hai đúng top 1, 20 ca chỉ BARO đúng, 18 ca chỉ TraceRCA đúng, 12 ca cả hai sai; cận trên chọn đúng giữa hai phương pháp là 0,867. RRF ngang trọng số cải thiện Avg@5 trong phân tích thăm dò nhưng AC@1 phụ thuộc cách phá hòa. Thêm MicroRank yếu làm kết quả giảm. Đây là lý do MyRCA cần quality gating và một tie policy cố định; chưa phải bằng chứng MyRCA thắng.

---

## 4. Kiểm định từng phương pháp

Sáu mục phương pháp của báo cáo chứa bảy phương pháp chạy được. Dummy chỉ là đối chứng.

### 4.1 BARO — baseline metric mạnh, nhưng cấu hình chạy không phải toàn bộ BARO gốc

**Bài gốc.** BARO (`T-09`) dùng Multivariate Bayesian Online Change Point Detection để xác định thời điểm đổi trạng thái và một bước xếp hạng bền vững dựa trên phân phối trước/sau.

**Cấu hình RCAEval đã kiểm.** Hàm `baro` nhận trực tiếp `inject_time`, chia bảng thành trước/sau, khớp `RobustScaler` cho từng cột trên cửa sổ bình thường, lấy giá trị chuẩn hóa lớn nhất trong cửa sổ bất thường làm điểm rồi sắp giảm dần. Nó không gọi MBOCPD trong đường chạy này.

**Dữ liệu:** metric time series. **Đồ thị:** không. **Tham số quan trọng:** cửa sổ, thời điểm chia, quy tắc chọn cột, scaler và phép tổng hợp `max`.

**Ưu:** đơn giản, rất nhanh, mạnh trên TT và các lỗi tài nguyên có tín hiệu cục bộ. **Nhược:** dễ xếp node “hứng” nhiều triệu chứng lên đầu; dùng `max` nhạy với một spike; cấu hình benchmark dùng oracle injection time nên chưa đại diện phát hiện trực tuyến.

**Với FlashTicket:** phù hợp làm metric scorer và fallback, không đủ làm pipeline RCA duy nhất. Phải tách thí nghiệm “biết thời điểm tiêm lỗi” khỏi thí nghiệm dùng thời điểm cảnh báo/phát hiện.

### 4.2 CIRCA — phân biệt nguyên nhân và hệ quả tốt trên OB, nhưng khó mở rộng

**Cơ chế.** CIRCA học/nhận đồ thị nhân quả giữa các chuỗi và dùng kiểm định phần dư: một node bất thường nhưng được cha giải thích có thể là hệ quả; phần dư không giải thích được làm node đáng nghi hơn.

**Cấu hình RCAEval.** Tiền xử lý metric, chạy PC để tạo adjacency rồi dùng RHT để xếp hạng. Kết quả OB AC@1 0,667 vượt BARO 0,144, nhưng chưa có lần chạy TT hoàn chỉnh trong tệp đã nhận.

**Ưu:** có cơ chế chống đổ lỗi cho triệu chứng lan truyền; AC@1 cao và phân bố dự đoán rộng trên OB. **Nhược:** causal discovery trên nhiều biến tốn kém và nhạy với cỡ mẫu/giả định độc lập; hướng cạnh và quan hệ thống kê không tự động là phụ thuộc vận hành thật; chưa chứng minh khả năng mở rộng.

**Với FlashTicket:** có giá trị làm baseline giải thích cơ chế, nhưng không nên là lõi đầu tiên của MyRCA khi hệ chưa có dữ liệu đủ dài. Một đồ thị quan sát từ trace cũng không thể thay thế trực tiếp đồ thị nhân quả mà không kiểm tra semantics cạnh.

### 4.3 TraceRCA — khoanh vùng tốt, phụ thuộc hoàn toàn vào trace coverage

**Cơ chế trong cấu hình đã kiểm.** Ghép `serviceName` và `methodName/operationName` thành operation; dùng trace bình thường để tính mean/std duration; đánh dấu bất thường khi duration vượt `mean + 3 × std`; kết hợp support và confidence để xếp hạng.

**Dữ liệu:** raw spans và mốc phân chia. **Đồ thị:** không chạy PageRank; quan hệ trace dùng để phân nhóm luồng, không phải lan truyền graph như MicroRank.

**Ưu:** mịn tới operation; TT đạt AC@1 0,644 và mạnh ở lỗi DELAY; OB đạt AC@5 0,956. **Nhược:** không thể xếp node không có trace; gateway/frontend dễ tích lũy độ trễ; operation chỉ xuất hiện trong sự cố có thể thiếu SLO; timestamp phải cùng đơn vị.

**Với FlashTicket:** phù hợp làm trace scorer nếu HTTP/gRPC và luồng RabbitMQ giữ context. Theo OpenTelemetry (`T-21`), consumer không thể nối trực tiếp với producer nếu message creation context không được gắn và truyền theo message; đây là rủi ro trọng yếu cho phần bất đồng bộ.

### 4.4 MicroRank — đúng hướng đồ thị lời gọi, nhưng hợp đồng đầu vào chặt và độ phủ đang thấp

**Cơ chế.** MicroRank (`T-11`, `T-16`) tách trace bình thường/bất thường theo SLO operation, dựng mạng hai phía trace–operation cùng quan hệ gọi, chạy PageRank cá nhân hóa và dùng spectrum analysis để xếp operation/service.

**Ưu:** tận dụng cấu trúc lời gọi thật; có lan truyền tường minh; phù hợp lỗi độ trễ end-to-end khi trace đầy đủ và định danh ổn định. **Nhược:** cần raw span chuẩn, trace bình thường, SLO và coverage cao; chi phí lớn; node cổng vào hoặc operation phổ biến có thể áp đảo; không nhìn được blind spot.

**Bằng chứng TT:** AC@1 0,144, 56/90 ca không đưa ground truth vào top 5 và top-1 gần như chỉ có hai service. Điều này không chứng minh ý tưởng PageRank sai; nó chứng minh pipeline hiện tại không đủ mạnh/đủ phủ để dùng làm RCA đáng tin cậy.

**Với LEMMA/FlashTicket:** chỉ chạy sau schema gate. Không viết adapter nếu chưa chứng minh được parent relation, timestamps và coverage. Khi coverage dưới ngưỡng định trước, phương pháp phải abstain hoặc có trọng số 0 trong fusion.

### 4.5 MM-BARO và MM-CIRCA — tên “đa nguồn” không bảo đảm mã và lượt chạy dùng cùng nguồn

**Theo công bố:** bài RCAEval WWW 2025 đặt multi-source BARO, RCD và CIRCA trong nhóm xử lý metric cùng time series sinh từ log và trace.

**Theo mã công khai tại `6018cde`:** nhánh loader nạp và truyền metric, log, trace-error time series và trace-latency time series. MM-BARO dùng các time series này khi dữ liệu/cấu hình cho phép; metric 1 giây được lấy mỗi điểm thứ 15 để khớp chu kỳ 15 giây. Riêng hàm `mmcirca` chỉ ghép metric đã hạ mẫu với log time series, bỏ cột tương quan > 0,99, chạy PC rồi RHT. Nếu PC ném exception, hàm trả `node_names` theo thứ tự cột như một bảng xếp hạng thay vì báo ca lỗi thất bại.

**Theo 11 CSV:** chưa biết working tree tạo kết quả có sạch tại `6018cde` hay chứa bản vá cục bộ. Vì thiếu run manifest và patch hash, không được khẳng định chắc lượt chạy MM-CIRCA đã tiêu thụ hai hay ba nguồn.

**Hệ quả:** không thể nói MM-CIRCA giảm vì “trace tương quan”, không thể coi BARO→MM-BARO chỉ thay modality và không thể gọi cặp 2×2 là thiết kế nhân tố sạch.

**Kết quả:** trong CSV được cung cấp, MM-BARO gần BARO còn MM-CIRCA thấp hơn CIRCA trên OB. Khoảng tin cậy cụm của chênh lệch AC@1 không cắt 0, nhưng khoảng Avg@5 cắt 0; McNemar/Holm theo dòng vừa không xử lý phụ thuộc trong cụm vừa thuộc một họ giả thuyết đặt ra hậu nghiệm. Vì vậy kết luận chỉ là **lượt MM-CIRCA này có AC@1 thấp hơn trong mẫu**, chưa phải xác nhận gây hại. Nguyên nhân còn `OPEN`: modality thật của run, hạ mẫu, số biến, lọc tương quan, graph discovery hoặc nhánh fallback đều có thể góp phần.

**Với MyRCA:** không đổ mọi cột vào một ma trận duy nhất. Mỗi modality có scorer và quality score riêng, chỉ hợp nhất sau khi quy về cùng entity registry.

### 4.6 PC+RandomWalk — kết quả hiện tại không đánh giá được giá trị của đồ thị

Đường chạy đã kiểm:

1. PC dựng adjacency.
2. Hàm random walk chọn node bắt đầu bằng `np.random.choice(nodes)` không nhận seed từ CSV.
3. Tất cả `previous_scores` mặc định bằng 0.
4. Trọng số cạnh phụ thuộc trị tuyệt đối của điểm node, nên toàn bộ bằng 0.
5. Mỗi cột ma trận chuyển tiếp rơi về phân phối đều `1 / size`.
6. `main.py` truyền `n_iter = num_node`, nên chỉ đi khoảng một bước cho mỗi node thay vì mặc định dài hơn.

Do đó random walk hầu như không dùng thông tin adjacency mà PC tạo. AC@1 0,033 không phải bằng chứng “đồ thị nhân quả kém”, “PC dựng sai đồ thị” hay “lan truyền graph vô ích”; nó là kết quả của một baseline triển khai không hợp lệ cho câu hỏi đó.

### 4.7 Dummy — đối chứng phải có seed và candidate manifest

Dummy dùng lựa chọn ngẫu nhiên không có seed ghi nhận. Vì vậy khác biệt giữa hai lần chạy là dự kiến. Mức sàn thực nghiệm chỉ có ý nghĩa khi tập ứng viên cố định, seed được lưu và lặp nhiều seed hoặc tính kỳ vọng giải tích. TT Dummy lộ 68 tên trong top 5, nên không được dùng 64 service làm mẫu số mà không giải thích 4 tên bổ sung.

---

## 5. Các mâu thuẫn phải sửa trong báo cáo hiện hành

| Mệnh đề cũ | Kết quả kiểm định | Cách viết mới |
|---|---|---|
| Thiết kế nhân tố 2×2 cô lập modality/đồ thị | Sai do hạ mẫu, số biến và đường thuật toán cùng thay đổi | So sánh quan sát có nhiễu; cần ablation cùng preprocessing |
| MM-CIRCA dùng metric+log+trace | Đúng ở lớp mô tả công bố; tại commit `6018cde`, loader nạp/truyền `M+L+T` nhưng hàm `mmcirca` chỉ tiêu thụ `M+L`; chưa xác minh working tree của lượt chạy CSV | Ghi đủ ba lớp: công bố `M+L+T`; đường truyền và consumer của mã công khai; lượt chạy `OPEN` |
| MM-BARO và BARO chỉ khác modality | Sai | MM-BARO còn hạ mẫu metric 15 giây |
| Cấu hình chạy thực thi đầy đủ BARO/MBOCPD | Sai | Tách BARO gốc khỏi scorer RCAEval dùng injection time |
| PC+RandomWalk chứng minh đồ thị kém | Không được hỗ trợ | Baseline graph head lỗi, không dùng được để kết luận |
| +0,722 đến từ lọc bằng đồ thị | Sai | Oracle upper bound dùng ground truth candidate set |
| 9 cạnh/7 service là “true dependency graph” | Quá mạnh | Đồ thị lời gọi quan sát được, cục bộ và thiếu 4/11 service |
| Mọi service OB gọi shared Redis | Sai | Online Boutique chính chủ cho thấy Redis gắn với cart service; `redis` vẫn có thể là node triệu chứng do cách thu metric |
| Trace làm MM-CIRCA giảm | Không thể suy ra: hàm `mmcirca` ở snapshot công khai không tiêu thụ các đối số trace dù loader có nạp/truyền chúng; modality và working tree của lượt chạy chưa xác minh, đồng thời thiết kế bị trộn nhiều thay đổi | Nguyên nhân `OPEN`; không quy cho modality nào |
| MM-CIRCA gây hại có ý nghĩa | Phân tích hậu nghiệm cho thấy AC@1 giảm và CI cụm không cắt 0, nhưng CI Avg@5 cắt 0; McNemar/Holm theo dòng không xử lý phụ thuộc trong cụm và họ giả thuyết chưa đăng ký | Chỉ viết “lượt MM-CIRCA này thấp hơn về AC@1 trong mẫu”; cần ablation/held-out, không dùng từ xác nhận và không quy nguyên nhân cho trace |
| Khớp số công bố chứng minh bản vá đúng | Quá mạnh | Chỉ là smoke check đầu ra tổng hợp |
| Mọi phương pháp dùng cùng tham số/default | Sai | Có preprocessing và patch khác nhau |
| OB có đúng 12 microservice theo một nghĩa duy nhất | Nguồn dùng ranh giới đếm khác nhau: bài RCAEval ghi 12 service; mục Architecture của kho Google hiện hành ghi 11 và liệt kê cả `loadgenerator`, còn phần About ghi 10; CSV lại có tên hạ tầng/alias | Ghi nguồn, phiên bản và đối tượng được đếm; cần candidate manifest trước khi dùng 10, 11 hoặc 12 làm mẫu số |
| TT có đúng 64 ứng viên | Không khớp CSV | Hệ được mô tả có 64 service; CSV lộ ít nhất 68 định danh ứng viên |
| MicroRank OB “không hợp lệ” | Không có CSV OB trong gói bằng chứng | Gỡ hoặc ghi chưa kiểm chứng |
| `success` là chạy thành công | Sai | Ground truth nằm trong top 5 |
| `split("_")[0]` luôn lấy đúng service | Mong manh | Cần entity registry/parser theo schema |
| `frontend`, `frontendservice`, `traceservice` là ba node hợp lệ | Không có manifest chứng minh | Lỗi/điểm mở chuẩn hóa định danh |
| Runtime OB đã được chứng minh | CSV trống và không có log OB | Đánh dấu chưa kiểm chứng |
| Seed và bản vá có thể suy từ commit | Sai | Ghi `OPEN`; phải cung cấp run manifest/patch hash |

---

## 6. MyRCA v0 — thiết kế `CANDIDATE` khả thi nhất

### 6.1 Đóng góp được phép tuyên bố

`USER_CONFIRMED`: đóng góp được định vị là **tổ hợp có kiểm chứng**.

`CANDIDATE`: MyRCA v0 là một pipeline xác định, không học sâu, đặt hợp đồng dữ liệu và kiểm soát chất lượng trước fusion; mọi bước đều có ablation.

`OPEN`: MyRCA có cải thiện baseline mạnh hay không; trọng số tối ưu; ngưỡng từ chối; tập node FlashTicket; graph direction; hiệu quả trên lỗi nghiệp vụ.

Không tuyên bố RRF, PageRank, graph propagation, robust anomaly scoring hoặc LLM explanation là phát minh mới. TORAI (`T-19`) đã làm multi-source severity/cluster/causal rank; GALA+ (`T-20`) đã dùng graph evidence, RRF và LLM agents. Tính mới, nếu có, phải nằm ở câu hỏi và bằng chứng cụ thể của FlashTicket: hợp đồng telemetry, quality-aware abstention, kiểm chứng blind spot và vận hành trên hệ giao dịch của nhóm.

### 6.2 Đồ thị phân cấp

1. **Tầng thực thể:** các loại ứng viên có thể gồm service nghiệp vụ, gateway, identity, database, cache, broker và external API; chỉ đưa loại nào vào sau khi tập thực thể quan sát được `B11-C`/`B16` chốt.
2. **Tầng operation:** HTTP/RPC endpoint và producer/consumer operation lấy từ trace; có thể thiếu.
3. **Tầng bằng chứng:** metric, log template và span gắn vào node; không tạo node đồng cấp nếu không có giả thuyết cần kiểm.

Mỗi cạnh phải có `edge_type`, `direction`, `source` và `coverage`. Cạnh từ trace là “đã quan sát trong cửa sổ”, không tự động là toàn bộ phụ thuộc tĩnh hay quan hệ nhân quả.

### 6.3 Hợp đồng dữ liệu chuẩn

```text
MetricRecord
  timestamp, entity_id, metric_name, value, unit

LogRecord
  timestamp, entity_id, template_id?, message?, severity?, trace_id?, span_id?

SpanRecord
  trace_id, span_id, parent_span_id, entity_id, operation,
  start_time, duration, span_kind, status?
  # parent_span_id chỉ nullable ở span gốc; status là bổ trợ

CaseRecord
  case_id, normal_window, abnormal_window hoặc detected_at,
  dataset_id, dataset_version, ground_truth?
  # ground_truth chỉ tồn tại trong evaluator hoặc ca fault-injection có kiểm soát;
  # không đi vào pipeline production

RankedCause
  candidate_id, candidate_type, entity_id, operation?, rank, score, modality_evidence,
  graph_provenance, coverage, confidence, abstain_reason?
```

Một `entity-registry` quản lý alias, entity type và quan hệ metric/log/span→entity. Adapter phải báo lỗi khi gặp ID không ánh xạ được; không cắt tên bằng dấu gạch dưới.

### 6.4 Pipeline

1. **Schema gate:** xác nhận trường bắt buộc, đơn vị thời gian, coverage và alias.
2. **Windowing:** tách hai chế độ rõ ràng: oracle `inject_time` để tái lập benchmark; `detected_at` để mô phỏng triển khai.
3. **Metric scorer:** RobustScaler/deviation kiểu BARO, nhưng lưu cả độ lớn, độ bền và số điểm bất thường thay vì chỉ giữ một spike.
4. **Log scorer:** đếm mức tăng template/severity theo entity; không dùng LLM để chấm nguyên nhân.
5. **Trace scorer:** operation anomaly kiểu TraceRCA; trả coverage và số operation có baseline.
6. **Entity mapping:** gom điểm về đúng entity/operation qua registry, giữ nguồn bằng chứng.
7. **Quality-aware fusion:** RRF là baseline đầu tiên; modality thiếu/không đạt schema có trọng số 0. Trọng số chỉ được chọn trên development folds.
8. **Graph consistency ablation:** thử riêng tác dụng lan truyền 0–2 hop, hướng và degree normalization. Node không có bằng chứng trực tiếp không được đứng đầu chỉ nhờ centrality.
9. **Confidence/abstention:** từ chối khi coverage dưới ngưỡng đã đăng ký, top-1/top-2 quá sát hoặc modality mâu thuẫn mạnh.
10. **Explanation:** lớp ngôn ngữ nhận bảng xếp hạng và provenance để diễn giải/gợi ý bước kiểm tra; không đổi thứ hạng, không kết luận cuối cùng và không tự sửa hệ thống (`DH-MT4`, `RES-034`).

### 6.5 Cấu hình thử nghiệm đầu tiên

- RRF dùng danh sách service chuẩn hóa; `k` được cố định trước hoặc chọn trên development folds, không chọn sau khi nhìn test.
- Tie-break: tổng điểm anomaly đã chuẩn hóa trên development data; nếu vẫn hòa, dùng `entity_id` để kết quả xác định. Thứ tự chữ chỉ phá hòa kỹ thuật, không được diễn giải là bằng chứng.
- Graph weight gồm cả 0 để phép thử có thể kết luận “không dùng graph”.
- Modality weight = 0 khi thiếu schema, coverage bằng 0 hoặc validator thất bại.
- Ground truth không xuất hiện ở bất kỳ bước nào trước khâu evaluator.

---

## 7. Đánh giá khả thi đối với FlashTicket chưa có mã

### 7.1 Điều có thể kết luận ngay

| Hạng mục | Mức khả thi | Lý do |
|---|---|---|
| Canonical schema và adapter RCAEval | Cao | Cấu trúc metric/log/span đã biết; phạm vi hẹp, kiểm thử được |
| Tái lập BARO/TraceRCA baseline | Trung bình–cao | Có mã chính chủ nhưng phải pin commit, lưu patch và sửa run isolation |
| MyRCA deterministic fusion | Trung bình–cao | Không cần huấn luyện mô hình lớn; RRF và scorer đơn giản |
| Graph consistency | Trung bình | Cần định nghĩa đúng semantics cạnh và chịu blind spot |
| LLM explanation | Cao về tích hợp, chưa biết chất lượng | Chỉ chạy sau ranking; cần bộ tiêu chí factuality/grounding |
| MyRCA vượt baseline mạnh | Chưa biết | Fusion thăm dò cho tín hiệu nhưng phương pháp yếu có thể làm giảm kết quả |
| Chạy trực tiếp trong FlashTicket | Chưa đủ điều kiện | Chưa có code, telemetry, fault harness, workload và nhãn |

### 7.2 Hình dung ban đầu không khóa kiến trúc

Theo bằng chứng khả thi đã duyệt ở B11-B v0.6, hệ có thể sử dụng Java/Spring Boot, gateway/discovery/config, Keycloak, PostgreSQL, MongoDB, Redis/Redisson và RabbitMQ. Đây chỉ là đầu vào lập kế hoạch instrumentation:

- OpenTelemetry Java agent có thể giảm công sức lấy HTTP/database span ban đầu (`T-21`).
- Luồng RabbitMQ cần kiểm tra truyền context ở producer, message carrier và consumer; nếu không, đồ thị bị đứt đúng tại các giao dịch bất đồng bộ.
- Database/cache/broker là thực thể vận hành có thể cần node riêng, nhưng B11-C/B16 mới có quyền chốt tập node.
- Chatbot mua vé và cơ chế RCA được tính riêng về tài nguyên vận hành; không cộng vào trần tám service nghiệp vụ.

### 7.3 Rủi ro hiệu quả

1. **RCA đúng dataset nhưng sai production:** injection time và fault types công khai khác cảnh báo thật.
2. **Đồ thị thiếu cạnh:** blind spot khiến thuật toán không thể nhìn thấy root cause; TORAI ghi nhận OB chỉ 7/11 service có trace và TT 27/64.
3. **Định danh phân mảnh:** alias tạo node ma và xếp hạng sai.
4. **Node hút triệu chứng:** gateway, shared datastore hoặc broker có thể luôn bất thường nhưng không phải root cause.
5. **Fusion âm:** thêm một modality yếu có thể kéo kết quả xuống, như MicroRank trong thử RRF. Chênh lệch MM-CIRCA trong bảng OB chỉ là cảnh báo về một cấu hình bị trộn biến; chưa biết chính xác modality của lượt chạy nên không được dùng làm bằng chứng nhân quả cho fusion.
6. **Rò rỉ nhãn:** dùng injection target/candidate filter làm pipeline trông tốt giả tạo.
7. **Không biết từ chối:** một top-1 luôn tồn tại dù mọi tín hiệu đều yếu; đây là thất bại nguy hiểm nhất khi demo.

---

## 8. Kế hoạch hai tuần có đầu ra kiểm chứng được

### Tuần 1 — hiểu baseline và khóa hợp đồng

| Ngày | Công việc | Bằng chứng hoàn thành |
|---|---|---|
| 1 | Lập manifest dataset/case/candidate, pin commit và hash dữ liệu | Manifest đọc được bằng máy; không còn 64/68 hoặc 11/12 mơ hồ |
| 2 | Chuẩn hóa entity registry và đơn vị thời gian | Validator bắt được alias/timestamp sai |
| 3 | Tái lập BARO và Dummy với output directory sạch, seed rõ | Kết quả lặp lại và có run manifest |
| 4 | Tái lập TraceRCA; đo trace coverage | Báo cáo ca không đủ SLO/parent/context |
| 5 | Audit MicroRank input; quyết định LEMMA tương thích/không tương thích | Schema matrix và lý do có thể kiểm tra |
| 6–7 | Viết failure analysis, không chỉ bảng điểm | Mỗi sai lệch gắn với ca và cơ chế khả dĩ |

### Tuần 2 — MyRCA tối thiểu và ablation

| Ngày | Công việc | Bằng chứng hoàn thành |
|---|---|---|
| 8 | Metric/log/trace scorer về cùng output contract | Unit test từng scorer |
| 9 | RRF xác định + tie policy | Kết quả lặp lại theo seed |
| 10 | Quality gating và missing-modality tests | Phương pháp yếu/thiếu được hạ trọng số hoặc abstain |
| 11 | Graph adjustment với weight gồm 0 | Ablation chứng minh giữ/bỏ graph |
| 12 | Split theo nhóm và chạy benchmark chưa dùng để chọn cấu hình | Không rò rỉ case cùng service×fault |
| 13 | Chạy phép suy luận có xét cụm đã đăng ký; McNemar/Holm chỉ làm kiểm tra nhạy cảm; đo runtime/RAM | Bảng có CI, protocol và provenance |
| 14 | Tổng hợp báo cáo, negative result nếu không thắng | Kết luận không vượt bằng chứng |

Đây không chỉ là “nói lại nghiên cứu của người trước”. Việc tái lập đúng, phát hiện baseline triển khai lỗi, chỉ ra input contract, đo blind spot và chứng minh fusion khi nào gây hại là đóng góp nghiên cứu có thể bảo vệ.

---

## 9. Cổng chấp nhận hoặc loại MyRCA

MyRCA chỉ được đi tiếp nếu đạt đồng thời:

1. adapter/schema/identity validator đạt trên mọi ca được dùng;
2. baseline được tái lập bằng commit + dataset hash + patch hash + run manifest;
3. mọi ablation dùng cùng preprocessing ngoài thành phần đang thử;
4. cấu hình được chọn trên development split, đánh giá một lần trên held-out split;
5. biên không-thua `δ` cho AC@1 được định trước trên development data rồi đăng ký trước held-out; với chênh lệch `d = AC@1(MyRCA) − AC@1(baseline)`, MyRCA chỉ đạt nếu cận dưới khoảng tin cậy trên held-out lớn hơn `−δ`; AC@3/Avg@5/MRR chỉ là hỗ trợ;
6. kết quả ổn định theo `service × fault`, không chỉ do một service dễ;
7. phương pháp từ chối khi modality thiết yếu thiếu hoặc coverage thấp;
8. runtime/RAM được đo trên đúng máy, không suy từ log thiếu;
9. sau này vượt qua fault injection có kiểm soát trên FlashTicket.

Nếu không đạt, phương án đúng là giữ baseline mạnh nhất hoặc dùng TORAI làm comparator/ứng viên triển khai và báo cáo MyRCA như **kết quả âm có giá trị**. Không triển khai một pipeline chỉ vì nó chạy được.

---

## 10. Hash tệp bằng chứng

| Tệp | SHA-256 |
|---|---|
| `cases_ob_baro.csv` | `A08BB67388BBF74C39278DA431B5B9D309B1A4AEB3E59D9EB9132EE701455E96` |
| `cases_ob_circa.csv` | `A0DA09698C3C513B43048D9484D56E4ADF72823A9F9FE6E6464E7EBA58F4DF7E` |
| `cases_ob_dummy.csv` | `8C0652617E13E0BE354D24C394893AE8AAF5610006029CC9779278D3D5E890E2` |
| `cases_ob_mmbaro.csv` | `6F6F6F195EA4089246739E411C1DD176E5C0922E10E40C17C44D12BB825B4D2A` |
| `cases_ob_mmcirca.csv` | `D9773B3FB25D4EBDF136E54836B7CA0548DAC1D98505D1CA46916913F6AD3DED` |
| `cases_ob_pc_randomwalk.csv` | `0E636646C2CD1B6DC5FF8BFB72490B4B93E656062592468D1C2481DE1081431A` |
| `cases_ob_tracerca.csv` | `C1AD10A40003ACAB206FEE2EEEE4D4C619BE4DD71E4A31F54288233D3837759F` |
| `cases_tt_baro.csv` | `55D8DF87B9BFDC60059115A1FE9830BE4F7E7727B417742D2F2FA69CE8C861EF` |
| `cases_tt_dummy.csv` | `575A726DFEBAD9DF131B1455B2D16509F351C672D4E5B4BAF74C09A07FD2B281` |
| `cases_tt_microrank.csv` | `D299537D9F36F3304CB5F8713967409993468B73BFB0A9920399EC9D76B60086` |
| `cases_tt_tracerca.csv` | `1A1A8DF607E27C2C4C7624A1D2F52EFA4674AF25C467C9CA482540BC8CFA0FB9` |
| `log_tt_all.txt` | `2510CE2A9002EE0AB38029CB7462D2E5538F662A56B4125FC94965F0F331987E` |

---

## 11. Sổ nguồn trực tiếp

- `T-06`: Pham và cộng sự, [RCAEval: A Benchmark for Root Cause Analysis of Microservice Systems with Telemetry Data](https://arxiv.org/abs/2412.17015), năm revision arXiv; WWW Companion 2025, DOI [10.1145/3701716.3715290](https://doi.org/10.1145/3701716.3715290).
- `T-07`: Pham và cộng sự, [Root Cause Analysis for Microservice System based on Causal Inference: How Far Are We?](https://arxiv.org/html/2408.13729), ASE 2024, DOI [10.1145/3691620.3695065](https://doi.org/10.1145/3691620.3695065).
- `T-09`: Pham, Ha, Zhang, [BARO](https://arxiv.org/abs/2405.09330), FSE 2024.
- `T-11`, `T-16`: Yu và cộng sự, [MicroRank paper](https://doi.org/10.1145/3442381.3449905); [official code](https://github.com/IntelligentDDS/MicroRank).
- `T-13`: Li và cộng sự, [TraceRCA official code and paper link](https://github.com/NetManAIOps/TraceRCA), IWQoS 2021.
- `T-15`: [RCAEval official repository](https://github.com/phamquiluan/RCAEval), commit `6018cde96694e775360f5f4b8f5e68ffe54d439d`; [release 1.4.0](https://github.com/phamquiluan/RCAEval/releases/tag/1.4.0).
- `T-17`: [LEMMA-RCA paper](https://arxiv.org/html/2406.05375v1), [official baseline repository](https://github.com/KnowledgeDiscovery/rca_baselines), [pinned Hugging Face tree](https://huggingface.co/datasets/Lemma-RCA-NEC/Product_Review_Original/tree/2c6d4dd7c214dbb9cbb7c0d858c845e2b9315c05).
- `T-18`: Google, [Online Boutique official repository](https://github.com/GoogleCloudPlatform/microservices-demo).
- `T-19`: Pham và cộng sự, [TORAI](https://arxiv.org/abs/2604.13522), arXiv v1/v2; PACMSE/FSE 2026 Article FSE130, DOI [10.1145/3808137](https://doi.org/10.1145/3808137); [snapshot mã `fse26`](https://github.com/phamquiluan/RCAEval/tree/fse26).
- `T-20`: [GALA+: Graph-Augmented LLM Agents for RCA](https://arxiv.org/html/2608.08968), 2026. Mã nguồn không được công khai theo thỏa thuận với đối tác công nghiệp.
- `T-21`: OpenTelemetry, [Java agent](https://opentelemetry.io/docs/zero-code/java/agent/) và [messaging span conventions](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/).

---

## 12. Điểm còn mở

| ID | Trạng thái | Cần gì để đóng |
|---|---|---|
| `E1-OPEN-01` | `OPEN` | Lệnh chạy, lockfile/môi trường và hash dataset cho 11 lượt |
| `E1-OPEN-02` | `OPEN` | Run manifest, working-tree state và patch hash của 11 lượt chạy; đây là đầu vào bắt buộc để xác định modality thực sự của MM-CIRCA và kiểm tra ba sửa đổi trace được báo cáo cũ mô tả |
| `E1-OPEN-03` | `OPEN` | Runtime per-case OB; seed của Dummy/PC+RandomWalk |
| `E1-OPEN-04` | `OPEN` | Candidate manifest giải thích 11/12 ở OB và 64/68 ở TT |
| `E1-OPEN-05` | `OPEN` | Kiểm tra mẫu raw LEMMA để xác nhận khả năng chạy MicroRank |
| `E1-OPEN-06` | `OPEN` | Nguồn có thẩm quyền thống nhất giấy phép LEMMA; README nói CC BY-NC 4.0 nhưng tệp/license text đã quan sát không đồng nhất |
| `E1-OPEN-07` | `OPEN` | Đăng ký trước phép kiểm có xét cụm, họ giả thuyết, biên không-thua và split; sau đó mới mở held-out để biết MyRCA có hiệu quả hay không |
| `E1-OPEN-08` | `OPEN` | B11-C/B16 chốt tập node vận hành và telemetry FlashTicket; E1 không được chốt hộ |

---

## 13. Phép tự kiểm

- [x] Trả lời trực tiếp câu hỏi 5 service/node và MicroRank/LEMMA.
- [x] Phân biệt thuật toán gốc với cấu hình RCAEval đã chạy.
- [x] Phân biệt năm revision của bài RCAEval với ba snapshot công bố `ase24/www25/fse26`; giới hạn đúng câu về Online Boutique.
- [x] Tách MM-CIRCA thành ba lớp bằng chứng: công bố, mã công khai và lượt chạy CSV.
- [x] Tính lại 990 dòng và giữ ca thiếu hạng trong mẫu số.
- [x] Dùng bootstrap theo cụm; hạ McNemar exact/Holm theo dòng đúng thành phân tích nhạy cảm hậu nghiệm, không dùng p-value làm kết luận xác nhận.
- [x] Không gọi oracle filter là graph improvement.
- [x] Không dùng PC+RandomWalk hiện tại để phán xét giá trị của đồ thị.
- [x] Định vị MyRCA là `CANDIDATE` và “tổ hợp có kiểm chứng”, không tự phong `DECIDED`.
- [x] Không đọc repository cũ, không sửa B11-A/B11-B/B11-C và không sinh ranh giới kiến trúc.
- [x] Ghi rõ dữ liệu, runtime, seed, patch, license và hiệu quả chưa có bằng chứng.
- [ ] Lê Văn Minh và giảng viên phản biện trước khi dùng kết luận như quyết định triển khai.

---

## 14. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `E1-v0.3` | 2026-09-03 | Khóa kiểm tra tự động cho 11 CSV/990 dòng; bổ sung CI cụm cho mọi độ đo, so sánh TT và phân tích theo service/loại lỗi; sửa cách đọc `AC@k`, mức sàn và chi tiết đếm Online Boutique; hạ McNemar/Holm hậu nghiệm thành phân tích nhạy cảm; làm rõ cổng held-out/không-thua | Tăng độ tái lập và sửa giới hạn suy luận, không đổi trung bình 990 ca hay thiết kế MyRCA |
| `E1-v0.2` | 2026-09-02 | Đối chiếu trực tiếp công bố/DOI/repository/release của tác giả; thu hồi kết luận tuyệt đối MM-CIRCA chỉ có metric+log; tách năm revision RCAEval khỏi ba snapshot công bố; giới hạn đúng nhận định Online Boutique và sửa tiêu đề TORAI | Sửa sự thật nền, chưa đổi kết quả 990 ca hay thiết kế MyRCA |
| `E1-v0.1` | 2026-09-02 | Kiểm định 11 CSV/990 ca, tách thuật toán gốc khỏi cấu hình RCAEval, sửa diễn giải thống kê/đồ thị/modality, phân tích node count và MicroRank–LEMMA, đề xuất MyRCA quality-aware có ablation/abstention và lập kế hoạch hai tuần | Tạo mới, `FORMATION`, chờ duyệt |
