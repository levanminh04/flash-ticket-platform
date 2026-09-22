# R0 — Ngữ cảnh hiện tại và ràng buộc gửi tới các gate hạ nguồn

> **Điều hướng nghiên cứu cập nhật 22/09/2026:** trạng thái/lựa chọn phương pháp công khai trong §1–2 là snapshot 18/09. Hướng hiện hành đọc [C Phase 2](task-c-research-decision-lock.md), data capability đọc [B digest](task-b-dataset-capability-summary.md), tiến độ/lộ trình đọc [CURRENT-STATE](CURRENT-STATE.md) và [MASTER](MASTER-RESEARCH-PROGRAM.md). Notice này không sửa mười hai ràng buộc §3, các OPEN, gate hệ thống hoặc duyệt proposal MyRCA cũ.

- **Phiên bản:** `R0-v0.6`
- **Trạng thái:** `DRAFT` — ghi ngữ cảnh và ràng buộc; chưa được duyệt
- **Người duyệt:** — (chờ Lê Văn Minh)
- **Ngày duyệt:** —
- **Ngày cập nhật ngữ cảnh:** 2026-09-18; các ảnh chụp trạng thái cũ đọc ở lịch sử Git/nhật ký.
- **Loại tài liệu:** **cửa chiều nghiên cứu → hệ thống**. Chiều ngược lại đi qua `docs/project/lien-ket-rca.md`. Hai cửa cộng lại tạo liên kết hai chiều; bản thân tệp này chỉ là một trong hai.
- **Đầu vào hiện hành:** [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md) (`PRJ-025/026`); phân công 18/09; baseline hệ thống đã duyệt. DH-* giữ vai trò nguồn lịch sử.
- **Đi vào báo cáo:** không trực tiếp. Đây là tài liệu quản trị nội bộ. Một phần §2 có thể chuyển vào mục *Giới hạn* của báo cáo.

> **Vì sao tài liệu này tồn tại.** Chủ đồ án yêu cầu: *"tôi cần bạn ghi rõ ngữ cảnh hiện tại vào một tài liệu nào đó, tránh trường hợp sau này làm sâu đến B12 B13 phát sinh ra một cản trở nào đó cho RCA."*
>
> Đây chính là tài liệu đó. Nó tồn tại vì một lý do rất cụ thể: các quyết định ở B11–B16 **rẻ khi làm đúng ngay từ đầu và rất đắt khi sửa sau**. Một trường ngữ cảnh dấu vết thiếu trong phong bì sự kiện ở B13 nghĩa là sau này phải sửa mọi hợp đồng. Một chuẩn logging ở B16 không mang định danh thành phần sinh ra dữ liệu nghĩa là không quy được tín hiệu về ứng viên nào — và mất luôn khả năng xếp hạng.
>
> Tài liệu này nói **cần nhìn thấy gì**, không nói **phải xây thế nào**. Việc chọn kiến trúc thuộc `B11`.

---

## 1. Ngữ cảnh hiện hành — 2026-09-18

### 1.1 Hai bộ tài liệu

Hệ thống và bộ RCA cùng thực hiện nhiệm vụ DT18: xây dựng/đánh giá hệ bán vé phân tán, ứng dụng đồ thị để giám sát/chẩn đoán. Hai bộ giữ trách nhiệm/gate riêng và đúng hai cửa nối. Minh phụ trách chính RCA; nhóm bốn người Minh, Sơn, Tuấn, Tuyến. Mobile là phần phụ, chỉ làm khi thực sự thừa thời gian (`PRJ-035`); xem `docs/project/roles.md`.

### 1.2 Đã có gì và chưa có bằng chứng gì

| Thành phần | Trạng thái có nguồn |
|---|---|
| Thiết kế hệ thống | B11-C/PA-6 đã chốt; B12–B16/readiness đã duyệt 14/09 (GOV-144) |
| Mã ứng dụng | Commit 091fca1 ngày 18/09 có năm project nghiệp vụ và ba project hỗ trợ; mới là bộ khung |
| Kiểm chứng hệ thống | Chưa có bằng chứng luồng nghiệp vụ/E2E/tải và telemetry runtime đạt trong lượt cập nhật này |
| Nghiên cứu công khai | E1-v0.3 REVIEW_READY ghi kiểm định 11 CSV/990 dòng; MyRCA CANDIDATE, không phải kết quả trên FlashTicket |
| Điều kiện chấm FlashTicket | Runtime, telemetry đủ, workload/harness/nhãn và giao thức còn phải được hiện thực/kiểm chứng |

Trạng thái chi tiết đọc `docs/project/implementation-status.md`; nguồn kết luận thực nghiệm là E1. Không dùng bảng “chưa có B11/B16” ngày 24/08 làm hiện trạng.

### 1.3 Ràng buộc

Hai EC2, không HA, ngân sách mục tiêu theo B11-C; trần tám service nghiệp vụ/ba Saga không phải số node RCA. Hạn nộp được ghi là 14/12/2026. Mốc DH-MOC 05/09 là lịch sử; không tự đặt lịch mới. Thông tin máy nghiên cứu ở A6 là hồ sơ cũ, không phải kiểm kê trực tiếp ngày 18/09.

## 2. Thực nghiệm công khai trước, áp dụng FlashTicket sau

### 2.1 Điều kiện để đánh giá trên hệ thống

DT18-NV3 yêu cầu nghiên cứu/thực nghiệm trên bộ công khai phù hợp trước rồi áp dụng thử trên hệ bán vé. Đánh giá trên FlashTicket cần hệ chạy, log/trace/metrics đủ định danh và thời gian, workload kiểm soát, ca lỗi và nhãn có căn cứ, giao thức đo rõ. Bộ công khai và hệ nhà cho hai loại bằng chứng riêng; không suy rộng qua môi trường khi chưa kiểm.

### 2.2 Mức node và cách đọc kết quả

Tập ứng viên được xếp hạng phải có manifest và mức ngữ nghĩa rõ. Số service nghiệp vụ không tự bằng số node vận hành, operation hoặc chỉ số; không ép đánh giá mức chỉ số chỉ để tăng số node. Công thức/mức sàn và kiểm định số liệu đọc A7-v0.3 và E1-v0.3. Bảng 8/12/15/64 cùng kết luận trong R0-v0.5 là lập luận lịch sử đã được kiểm định lại ở E1, không dùng để chốt tập node hiện hành. `R0-OPEN-06` vẫn mở.

### 2.3 Vai trò FlashTicket

FlashTicket là đầu ra xây dựng/đánh giá trực tiếp của DT18-NV1 và nơi áp dụng mô hình/cơ chế DT18-NV2/NV3. Mô hình miền và kiến trúc cung cấp bằng chứng phụ thuộc, telemetry cung cấp quan sát thực. Mọi đồ thị suy ra qua cửa hệ thống → nghiên cứu giữ CANDIDATE tới khi được duyệt; không dùng nghiên cứu để tự thay bất biến, service hoặc schema.

### 2.4 Lộ trình và phần còn mở

| Mức | Nội dung | Điều kiện |
|---|---|---|
| 1 — Mô hình và thử trên dữ liệu công khai | Mô hình có nguồn; adapter và thực nghiệm phương pháp | Schema/nhãn/manifest được kiểm |
| 2 — Áp dụng trên FlashTicket | Gắn log, trace, metrics thật; giám sát/chẩn đoán và giao diện minh họa | Luồng chính và B16 được hiện thực/kiểm |
| 3 — Đánh giá ca thử FlashTicket | Ca lỗi có nhãn, độ đo phù hợp, phân tích hiệu quả/hạn chế | Giao thức/cỡ tập ca/độ đo được chốt ở nguồn sở hữu trước phép đánh giá |

Không tiếp tục coi áp dụng/đánh giá trên FlashTicket là phần có thể bỏ bằng câu lịch sử “cô không yêu cầu”. Quy mô và ngưỡng vẫn OPEN, không mặc định số ca hoặc độ chính xác. Các điều kiện §4 là cách kiểm độ sẵn sàng, không phải bằng chứng đã đạt.

---

## 3. Ràng buộc gửi tới các gate hạ nguồn — phần chống cản trở

**Cách dùng bảng này.** Mỗi gate từ `B9` tới `B16` phải đọc bảng này **trước khi chốt**, và ghi vào phần tự kiểm của mình rằng đã đối chiếu. Ràng buộc nào không giữ được thì phải ghi thành `OPEN` kèm hậu quả, không được bỏ im lặng.

Ba ràng buộc số 1, 2 và 5 đã được **viết lại ở `R0-v0.3`** (`RES-029`): bản trước quyết định kiến trúc triển khai — mỗi service một tiến trình riêng, giới hạn tài nguyên theo đơn vị triển khai, và giả định mẫu outbox — tức ép `B11` trước khi `B11` mở. Bản này chỉ nói **cần nhìn thấy gì**, không nói **phải xây thế nào**.

Toàn bộ mười hai ràng buộc dưới đây ở mức `CANDIDATE`: chúng do phân tích của phiếu này suy ra, chưa được Lê Văn Minh xác nhận, và **chưa phải yêu cầu hệ thống**. Ràng buộc nào làm phát sinh hoặc thay đổi mã, hợp đồng, schema hay hành vi ngoài phạm vi đã duyệt thì phải được trình tác động và xác nhận riêng trước khi thành yêu cầu.

| # | Gate | Ràng buộc | Vì sao chẩn đoán cần | Hậu quả nếu bỏ qua |
|---|---|---|---|---|
| 1 | `B16` + `B11` | **Mỗi log, trace và chỉ số dùng cho chẩn đoán phải xác định được thành phần đang chạy đã sinh ra nó.** Việc nối thành phần đang chạy với năng lực nghiệp vụ tương ứng làm **sau** `B11` | Mọi phương pháp xếp hạng đều cần biết tín hiệu thuộc về ứng viên nào | Không quy được tín hiệu về ứng viên nào → không xếp hạng được |
| 2 | `B15` kiểm chứng | **Kế hoạch thực nghiệm phải ghi được thành phần bị tác động, loại lỗi được tạo và phạm vi thành phần bị ảnh hưởng.** Mức cô lập đến đâu phụ thuộc kiến trúc được duyệt; không ép mỗi năng lực phải chạy riêng | Không ghi được phạm vi ảnh hưởng thì không biết tín hiệu quan sát được là của lỗi hay của nhiễu | Ca lỗi không diễn giải được, kể cả khi đã chèn thành công |
| 3 | `B11` triển khai | Ghi rõ **số ứng viên** mà kiến trúc tạo ra ở cả hai mức: mức service và mức chỉ số | Mức sàn ngẫu nhiên phụ thuộc trực tiếp vào số này (§2.2) | Không đọc được kết quả; không biết một điểm số là mạnh hay chỉ hơn đoán bừa |
| 4 | `B13` hợp đồng | **Ngữ cảnh dấu vết là trường bắt buộc trong phong bì thông điệp**, cho cả API đồng bộ và sự kiện bất đồng bộ — không phải header tuỳ chọn | Đồ thị phụ thuộc dựng từ quan hệ gọi; một hop mất ngữ cảnh là một lỗ trên đồ thị | Đồ thị đứt tại **mọi** ranh giới hàng đợi. Đây là cản trở nặng nhất và đắt nhất để sửa sau |
| 5 | `B13` | **Nếu kiến trúc có bước chuyển xử lý bất đồng bộ, thông tin nối trace phải đi qua được bước đó.** Câu điều kiện: không giả định mẫu kiến trúc nào đã được chọn | Bước chuyển bất đồng bộ chạy sau và ở ngữ cảnh khác; không mang thông tin nối thì dấu vết đứt đúng chỗ quan trọng nhất | Mất khả năng lần theo các luồng bù trừ và thử lại |
| 6 | `B12` + `B13` + `B16` | **Một định danh service duy nhất**, dùng nguyên văn ở cả ba nơi: thuộc tính tài nguyên của dấu vết, trường trong log, nhãn của chỉ số | Chẩn đoán phải ghép ba nguồn theo tên service; ba cách viết tên là ba thực thể khác nhau | Không ghép được log – dấu vết – chỉ số → mất hoàn toàn nhóm phương pháp đa nguồn |
| 7 | `B16` logging | Dấu thời gian **UTC, ISO-8601 có phần milli**; **một chu kỳ thu chỉ số duy nhất** được khai báo và giữ ổn định | Mọi phương pháp đều phát hiện điểm đổi trên chuỗi thời gian; lệch giờ giữa các service làm sai thứ tự nhân quả | Suy luận nhân quả sai — và sai một cách khó phát hiện, vì kết quả vẫn ra số |
| 8 | `B8` + `B16` | Mỗi bất biến cốt lõi có **một tín hiệu quan sát được khi bị vi phạm** (bộ đếm hoặc sự kiện log có cấu trúc). Không thất bại im lặng | Không bộ dữ liệu công khai nào chứa lỗi vi phạm bất biến nghiệp vụ. Muốn dùng lớp lỗi này thì trước hết phải quan sát được nó | Mất hướng đóng góp riêng duy nhất mà hệ thống của nhóm có thể mang lại |
| 9 | `B16` lưu trữ | Giữ dữ liệu đủ dài để mỗi ca lỗi có **một cửa sổ bình thường trước sự cố**, không chỉ cửa sổ sau | Nhóm thống kê và nhóm nhân quả so sánh trước với sau; không có cửa sổ trước thì không có gì để so | Không chạy được hai trong bốn họ phương pháp |
| 10 | `B14` sequence | Sequence các luồng xuyên service vẽ kèm **ranh giới span**, để biết chỗ nào bắt buộc phải có span | Đồ thị phụ thuộc chỉ chi tiết bằng mức chi tiết của span | Đồ thị quá thô, không phân định được service nào gây lỗi |
| 11 | `B9` + `B10` | Khả năng quan sát và khả năng chèn lỗi được nêu thành **kịch bản chất lượng có tiêu chí quan sát được**, rồi đưa vào danh sách ASR | Nếu không vào `B10` thì không có ASR nào bảo vệ nó, và `B11` sẽ đánh đổi nó đi đầu tiên khi thiếu bộ nhớ | Bị cắt lặng lẽ đúng lúc thiếu thời gian nhất |
| 12 | `B15` kiểm chứng | Kế hoạch kiểm chứng có một mục riêng: **harness chèn lỗi và ghi nhãn nguyên nhân thật** | Không có nhãn thì không tính được `AC@k`, dù đã có đủ dấu vết | Vĩnh viễn không chạy được chẩn đoán trên hệ đích, kể cả khi mọi thứ khác đã sẵn |

### 3.1 Ba ràng buộc đắt nhất nếu phát hiện muộn

Nếu chỉ nhớ được ba dòng trong bảng trên, nhớ ba dòng này:

1. **Số 4 — ngữ cảnh dấu vết trong phong bì sự kiện.** Sửa sau nghĩa là sửa mọi hợp đồng đã chốt.
2. **Số 1 — xác định được thành phần đang chạy đã sinh ra dữ liệu.** Ràng buộc này **không** đòi tách tiến trình: một hệ gộp nhiều năng lực vào một tiến trình vẫn thỏa được, miễn dữ liệu mang định danh thành phần. Việc nối thành phần với năng lực nghiệp vụ là quyết định của `B11`, và số ứng viên thu được ảnh hưởng mức sàn ở §2.2.
3. **Số 6 — một định danh service duy nhất.** Rẻ tuyệt đối nếu quy ước ngay; sau này là công việc dò tay xuyên ba hệ thống lưu trữ khác nhau.

### 3.2 Ràng buộc này **không** làm gì

- Không chốt kiến trúc, không chọn service, không ép `B11-A` theo hướng nào.
- Không yêu cầu thêm service, thêm bảng hay thêm API.
- Không mở rộng phạm vi sản phẩm. Mỗi ràng buộc là một **thuộc tính của thiết kế**, không phải một tính năng mới.
- Không thay thế `B16`. Khi `B16` mở, nó là nơi chốt chuẩn thật; bảng này chỉ nêu điều kiện cần.

---

## 4. Điều kiện để FlashTicket trở thành nơi thực nghiệm

Danh sách kiểm, dùng để trả lời câu *"đã đủ điều kiện chưa?"* mà không tranh luận lại từ đầu.

- [ ] Hệ thống chạy được đầu-cuối theo kiến trúc đích trên hạ tầng thật
- [ ] Dấu vết phân tán liền mạch qua **cả** ranh giới đồng bộ và bất đồng bộ (ràng buộc 4, 5)
- [ ] Log, dấu vết và chỉ số đều xác định được thành phần đang chạy đã sinh ra chúng (ràng buộc 1, 2)
- [ ] Một định danh service duy nhất dùng chung ba nguồn (ràng buộc 6)
- [ ] Sinh tải lặp lại được, có cửa sổ bình thường trước sự cố (ràng buộc 9)
- [ ] Harness chèn lỗi và ghi nhãn nguyên nhân thật (ràng buộc 12)
- [ ] Tập ca và mức ứng viên có manifest/nhãn phù hợp; nêu giới hạn cỡ mẫu (§2.2, ràng buộc 3)

**Cách đọc danh sách này:** đây là điều kiện kiểm chứng việc áp dụng/đánh giá theo DT18-NV3. Chưa tích ô nghĩa là thiếu bằng chứng; quy mô/giao thức chi tiết còn OPEN, không tự coi đã hoàn thành.

---

## 5. Liên kết hai chiều giữa hai bộ tài liệu

Hai bộ tài liệu tách nhau để làm việc cho gọn, **không phải để tách đề tài**. DT18 đặt một nhiệm vụ gồm hệ thống phân tán và ứng dụng đồ thị để giám sát/chẩn đoán.

### 5.1 Hai chiều đi qua đúng hai cửa

| Chiều | Đi qua | Nội dung |
|---|---|---|
| **Nghiên cứu → Hệ thống** | §3 của tài liệu này | Mười hai ràng buộc mà `B9`–`B16` phải giữ, để thiết kế hệ thống không chặn việc tích hợp cơ chế chẩn đoán |
| **Hệ thống → Nghiên cứu** | `docs/project/lien-ket-rca.md` | Nguồn dựng đồ thị cho `DT18-NV2`: các tương tác có bằng chứng ở `B4`, bảng bằng chứng từng cạnh ở `B5` §5.1, bất biến ở `B7`. Cộng trạng thái đã đối chiếu ràng buộc của từng gate |

**Không tạo thêm cửa thứ ba** mà không ghi vào đây.

### 5.2 Các nguyên tắc còn lại

| Nguyên tắc | Nội dung |
|---|---|
| Không chặn nhau | Bộ hệ thống đi tiếp mà không chờ kết quả thực nghiệm. Bộ nghiên cứu chạy thực nghiệm mà không chờ backend |
| Nguồn dùng chung | `docs/research/source-register.md` là sổ nguồn **duy nhất** cho cả hai bộ. Không tách thành hai danh mục tài liệu tham khảo, vì quyển báo cáo cuối chỉ có một |
| Cấu trúc quyển báo cáo | **Chưa chốt số chương.** Nội dung theo DT18-NV1–NV3; số chương chờ mẫu ĐATN |

---

## 6. Vấn đề `OPEN`

| ID | Vấn đề | Chủ sở hữu | Gate xử lý |
|---|---|---|---|
| `R0-OPEN-01` | Mười hai ràng buộc ở §3 **vẫn chưa** được Lê Văn Minh xác nhận; chúng chưa phải yêu cầu hệ thống. ⚠️ **Gate cũ ghi *"Trước `B9`"* đã trôi qua mà điểm này chưa đóng:** `B9` được duyệt 2026-08-27 và `B10` được duyệt 2026-08-28. Việc đó **không phải vi phạm** — hai gate đó đi qua bằng **cơ chế phụ lục đối chiếu cộng phép thử độc lập** ở `docs/project/lien-ket-rca.md` §2.2, đúng cách mà `RES-015` và Tầng B §3.3 cho phép khi ràng buộc còn `CANDIDATE`: đối chiếu thì bắt buộc, còn dùng làm nguồn sinh nội dung thì cấm. Ràng buộc nào không giữ được đã được mở thành `OPEN` — `B10-OPEN-06` cho ràng buộc 11 và `B10-OPEN-09` cho ràng buộc 8 — chứ không bỏ im lặng. **Gate được ghi lại cho đúng thực tế** | Lê Văn Minh | Trước `B11-A`; và trước `B16` cho nhóm ràng buộc logging |
| `R0-OPEN-07` | **Nhận chuyển giao từ bộ hệ thống ngày 2026-08-27** (`RES-034`), thay `B4-OPEN-02` và `B5-OPEN-05`. Cơ chế chẩn đoán được **khởi động** thế nào, **chọn dấu vết nào** làm đầu vào, **lưu phản hồi** của người kiểm tra ra sao, và **tập ca đánh giá** gồm gì. Chưa được trả lời — chỉ đổi nơi quản lý. Phần *tập ca đánh giá* nối về `A8`/`A9`; phần *khởi động và chọn dấu vết* thuộc thiết kế cơ chế. **Không** sinh yêu cầu nào cho bộ hệ thống; điểm tích hợp phía hệ thống là `NFR-12` và `RES-039` | Lê Văn Minh | Trước khi chạy cơ chế trên hệ nhà (mức 2 ở §2.4) |
| `R0-OPEN-06` | Số ứng viên mà kiến trúc được duyệt tạo ra ở mức service và mức chỉ số chưa biết, nên chưa tính được mức sàn ngẫu nhiên ở §2.2 cho hệ đích. Đây là câu hỏi **đọc kết quả**, không phải câu hỏi tài nguyên | Lê Văn Minh | `B11`, sau khi chốt kiến trúc |
| `R0-OPEN-03` | Chưa chốt giao thức/quy mô/lịch đánh giá FlashTicket theo DT18-NV3; danh sách §4 cần bằng chứng runtime | Lê Văn Minh | Sau thực nghiệm đầu tiên trên dữ liệu công bố |
| `R0-OPEN-05` | Bốn phiếu trong thư mục này vẫn dùng mã `A7`–`A10`, trùng nghĩa với `A7`/`A8`/`A9` của Tầng A; kế thừa `RES-009`. Đề xuất đổi sang `R1`–`R4` khi có thời gian, vì kéo theo khoảng 59 tham chiếu mã | Lê Văn Minh | Trước Giai đoạn 7 |

`R0-OPEN-02` đã đóng theo `RES-030`: nó phát biểu xung đột giữa *"mỗi service một tiến trình riêng"* và giới hạn bộ nhớ, nhưng vế thứ nhất là một quyết định kiến trúc mà `R0-v0.3` đã gỡ khỏi ràng buộc số 1. Phần còn giá trị của nó chuyển sang `R0-OPEN-06`.

`R0-OPEN-04` đã đóng: thư định hướng được lưu nguyên văn tại [`docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`](../evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md).

---

## 7. Phép tự kiểm

- [x] Ngữ cảnh §1.2 phân biệt thiết kế, bộ khung và bằng chứng runtime; không lặp lại hiện trạng 24/08.
- [x] Lộ trình theo DT18-NV3: thực nghiệm công khai trước, áp dụng/thử trên hệ bán vé sau; giữ giới hạn suy rộng.
- [x] Mức ứng viên và cách đọc kết quả dẫn E1; không dùng con số node chưa được kiểm định.
- [x] Mỗi ràng buộc ở §3 có gate, lý do và hậu quả cụ thể — không có dòng nào chỉ nói "nên làm cho tốt".
- [x] Nêu rõ ràng buộc nào đắt nhất nếu phát hiện muộn.
- [x] Ghi rõ ràng buộc này **không** chốt kiến trúc và **không** mở rộng phạm vi sản phẩm.
- [x] Không ràng buộc nào ở §3 chọn hộ `B11` một mẫu triển khai: ba dòng 1, 2, 5 nói điều kiện quan sát, `B11` chọn cách đạt. Câu hỏi còn lại — số ứng viên mà kiến trúc tạo ra — được ghi thành `R0-OPEN-06`, không tự giải quyết.
- [x] Toàn bộ ràng buộc giữ ở `CANDIDATE`; không tự nâng thành yêu cầu hệ thống.
- [x] Liên kết hai chiều đi qua đúng hai cửa đã đặt tên ở §5.1; không tạo cửa thứ ba.
- [x] Nguồn hiện hành là DT18 do Minh xác nhận; các dẫn DH-* còn lại thuộc lịch sử, không quy lời mới cho giảng viên.
- [ ] Lê Văn Minh xác nhận §3.

---

## 8. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `R0-v0.6` | 2026-09-18 | Đồng bộ DT18, nhóm bốn người, mobile phụ và trạng thái đã có project; dẫn E1 cho mức node; giữ nguyên bảng 12 ràng buộc CANDIDATE ở §3 | Cập nhật ngữ cảnh, chưa duyệt ràng buộc |
| `R0-v0.5` | 2026-08-29 | **Ba sửa sau vòng đọc toàn văn, không đụng mười hai ràng buộc.** (1) §2.2 trỏ sai số hiệu: *"ràng buộc số 3 và 5"* → **số 1 và 3**; số 5 nói về nối trace qua bước bất đồng bộ, không liên quan số ứng viên. Tham chiếu treo lại sau khi `v0.3` viết lại ba ràng buộc 1, 2, 5, trong khi §4 đã dùng đúng số từ trước — hai mục lệch nhau. (2) Ghi chú trạng thái ở §1.1 cập nhật theo `GOV-033` và `GOV-043`; `B10` nay đã duyệt. (3) `R0-OPEN-01` ghi lại gate cho đúng thực tế: gate cũ *"trước `B9`"* đã trôi qua, và hai gate đó đi qua hợp lệ bằng cơ chế phụ lục đối chiếu, không phải bằng việc xác nhận ràng buộc | Sửa tham chiếu treo và lời khai gate |
| `R0-v0.4` | 2026-08-27 | Mở `R0-OPEN-07` nhận chuyển giao `B4-OPEN-02` và `B5-OPEN-05` từ bộ hệ thống sau khi thiết kế trợ lý cũ bị gỡ (`RES-034`). Câu hỏi **chưa được trả lời**, chỉ đổi nơi quản lý — nên mã cũ ghi *chuyển giao*, không ghi *đóng*. **Mười hai ràng buộc §3 không đổi** | Nhận chuyển giao |
| `R0-v0.3` | 2026-08-26 | Viết lại ba ràng buộc 1, 2, 5 ở §3 thành **yêu cầu quan sát** thay vì quyết định kiến trúc triển khai (`RES-029`); sửa §3.1 mục 2, §4, khung dẫn nhập; dòng 8 tự khai lại là **một** cửa thay vì "cầu nối hai chiều", khớp §5.1 và `AGENTS.md` mục 4; đóng `R0-OPEN-02` theo `RES-030`, mở `R0-OPEN-06`; suy lại ô tự kiểm §7. **Giữ nguyên chín ràng buộc còn lại** | Gỡ phần ép gate hạ nguồn |
| `R0-v0.2` | 2026-08-25 | Sửa sau khi thư định hướng được lưu nguyên văn. §2 đổi từ *"không chạy trên FlashTicket"* sang **lộ trình ba mức** theo `DH-MT1`; §2.3 viết lại vai của FlashTicket; thêm §2.4; §5 đổi từ **một chiều sang hai chiều** và đặt tên hai cửa; đóng `R0-OPEN-04`. **Giữ nguyên mười hai ràng buộc ở §3** | Sửa sau khi có bằng chứng gốc |
| `R0-v0.1` | 2026-08-24 | Bản đầu: ngữ cảnh chốt ngày 2026-08-24 và bảng "chưa có"; hai lập luận vì sao thực nghiệm chạy trên dữ liệu công bố, kèm bảng mức sàn ngẫu nhiên theo số ứng viên; mười hai ràng buộc gửi tới `B9`–`B16` và ba ràng buộc đắt nhất; danh sách điều kiện để FlashTicket thành nơi thực nghiệm; năm nguyên tắc độc lập giữa hai bộ tài liệu; năm điểm `OPEN` | Tạo mới theo yêu cầu của chủ đồ án |
