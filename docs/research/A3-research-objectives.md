# A3 — Mục tiêu và bằng chứng đánh giá

- **Phiên bản:** `A3-v0.7`; cập nhật 2026-09-18.
- **Trạng thái:** `REVIEW_READY` — đã đồng bộ định hướng DT18; phần diễn giải cần Minh rà soát, không tự kế thừa phê duyệt phiên bản cũ.
- **Người duyệt:** —; **Ngày duyệt:** —.
- **Đầu vào:** [tên và nhiệm vụ hiện hành](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md) (`DT18-TEN`, `DT18-NV1`–`DT18-NV3`, `PRJ-025/026`); `PRJ-035` về mobile; các quyết định nghiệp vụ/kiến trúc còn hiệu lực.
- **Hiệu lực:** tên, nguyên văn nhiệm vụ và ưu tiên mobile là `USER_CONFIRMED`; các cách diễn đạt/ánh xạ bên dưới là `CANDIDATE` trừ phần dẫn rõ quyết định đã có. DH-* chỉ được dùng để truy lịch sử.

## 1. Mục tiêu tổng quát

Thiết kế, xây dựng và đánh giá hệ thống bán vé phân tán có ứng dụng đồ thị phụ thuộc để giám sát và hỗ trợ chẩn đoán sự cố, theo nguyên văn DT18-NV1–NV3. Đây là một đề tài với các đầu ra liên kết, không phân chia theo đầu người.

## 2. Ánh xạ nhiệm vụ hiện hành — bản diễn giải `CANDIDATE`

| Phần nhiệm vụ | Bằng chứng cần có | Tạo tác sở hữu |
|---|---|---|
| DT18-NV1 — xây dựng các chức năng bán vé | Luồng chính chạy, kiểm nghiệp vụ và bất biến dưới đồng thời/lặp/lỗi | B6–B8, B11–B15 |
| DT18-NV1 — đánh giá hệ phân tán | Thông lượng, thời gian đáp ứng, tỷ lệ lỗi, khả năng mở rộng, tính nhất quán và ổn định giao dịch tại nhiều mức tải, có cấu hình và workload | B9/B10, B15; ngưỡng và cấu hình so sánh còn OPEN |
| DT18-NV2 — mô hình và giám sát | Nút/cạnh có nguồn, ánh xạ log/trace/metrics, bằng chứng vùng ảnh hưởng và dấu hiệu bất thường | B16 và bộ RCA |
| DT18-NV2/NV3 — chẩn đoán và đánh giá | Độ đo phát hiện/xếp hạng, kết quả theo từng bộ công khai rồi theo ca thử FlashTicket, phân tích ca hiệu quả/thất bại | Bộ RCA; kế hoạch ca lỗi FlashTicket phối hợp B15 |
| DT18-NV3 — giao diện kết quả | Minh họa đồ thị/bằng chứng/kết quả phân tích cho người có quyền; không thay thế số đo chất lượng | Điểm tích hợp B11/B13 và đặc tả giao diện RCA; chi tiết còn OPEN |

## 3. Mục tiêu chi tiết kế thừa và mã truy vết

Các mã dưới đây giữ nội dung định tính đã xác nhận (`RES-002`, `RES-041`, `RES-045`, `RES-047`) để không làm đứt nguồn của yêu cầu và ASR. `PRJ-026` thay cách trình bày giới hạn hệ thống ở vai “chỉ cần chạy được”; MT-1–MT-3 nay cũng phục vụ nhiệm vụ xây dựng/đánh giá trực tiếp của DT18-NV1. Điều đó không tự biến các mẫu kỹ thuật quen thuộc thành phát minh mới.

| Mã | Mục tiêu cụ thể | Biết là đạt bằng |
|---|---|---|
| `MT-1` | Xác định và đặc tả tập bất biến cốt lõi của vòng đời vé, từ giữ chỗ và thanh toán tới phát hành và kiểm soát vào cửa | Mỗi bất biến truy được về một quyết định nghiệp vụ đã xác nhận, và có ít nhất một ca kiểm thử làm nó thất bại nếu cơ chế bảo vệ bị gỡ bỏ |
| `MT-2` | Thiết kế cách phối hợp trạng thái giữa các bước của vòng đời vé sao cho các bất biến ở `MT-1` vẫn giữ khi có yêu cầu gần đồng thời, thông điệp lặp và lỗi từng phần | Chạy được tập ca đồng thời, lặp và lỗi từng phần trên hệ thống đã dựng, ghi lại kết quả đạt hoặc không đạt cho từng bất biến |
| `MT-3` | Làm rõ đánh đổi giữa tính đúng đắn, khả năng phục hồi và chi phí hiệu năng của cách phối hợp được chọn | Thử tải có kiểm soát và tăng dần trên cấu hình được công bố, ghi độ trễ và thông lượng ở từng mức tải; ngưỡng cụ thể chốt ở B9/B10. Đây là kiểm thử hiệu năng/tải trong giới hạn đồ án, không phải tuyên bố mô phỏng lưu lượng sản xuất |
| `MT-4` | Bảo đảm hệ thống sinh đủ dấu vết vận hành để hỗ trợ việc xác định nguyên nhân một sự cố đã xảy ra; không cam kết loại bỏ việc tái hiện lỗi theo `PRJ-002` | Với tập ca lỗi có nguyên nhân đã biết, đo được tỷ lệ ca mà dấu vết thu được chứa đủ tín hiệu cần thiết để lần ra nguyên nhân |
| `MT-R1` | Dựng được đồ thị phụ thuộc giữa các thành phần từ dấu vết vận hành của một ca lỗi, và ánh xạ được tín hiệu bất thường từ các nguồn dữ liệu còn lại lên đồ thị đó | Tỷ lệ ca lỗi dựng được đồ thị trên bộ dữ liệu đã chọn, kèm quy mô đồ thị thu được ở mỗi hệ thống nguồn |
| `MT-R2` | Xây dựng một cơ chế lan truyền bất thường và xếp hạng nghi phạm trên đồ thị đó | Điểm của cơ chế theo bộ độ đo xếp hạng đã định nghĩa ở `docs/research-rca/A9-do-do-thuc-nghiem.md`, tính trên toàn bộ ca lỗi của bộ đã chọn |
| `MT-R3` | Đánh giá cơ chế đó **cùng điều kiện** với một tập phương pháp đã công bố và với một mức sàn ngẫu nhiên | Một bảng so sánh cho mỗi bộ dữ liệu, mọi phương pháp chạy cùng bộ và cùng độ đo, có cột đối chứng ngẫu nhiên |
| `MT-R4` | Làm rõ việc thêm nguồn dữ liệu vào có cải thiện kết quả hay không | Chênh lệch điểm giữa biến thể đơn nguồn và biến thể đa nguồn cùng lõi, trên cùng bộ dữ liệu |
| `MT-5` | Đánh giá mức hữu ích của **lớp giải thích** — thứ `DH-MT4` gọi là *"trợ lý"* — trên tập ca lỗi dùng để đo `MT-R2` | Bộ tiêu chí được định nghĩa trước, giữ tại `docs/research-rca/A9-do-do-thuc-nghiem.md` §6: (1) **trung thành với đầu vào** — lời diễn giải chỉ dùng bằng chứng thật sự có trong danh sách nghi phạm nhận được; (2) **hữu ích** — bước kiểm tra được gợi ý có dẫn tới xác nhận hoặc loại trừ được nghi phạm không; (3) **trung thực** — số lần đưa ra bằng chứng hoặc hành động không có thật. Đây là phép đo trên tập giả thuyết, **không** coi lớp giải thích là bên kết luận nguyên nhân cuối cùng (`PRJ-002` qua `RES-034`) |

### 3.1 Phần cần bổ sung giao thức, không tự đặt ngưỡng

- MT-3 đã bao phủ độ trễ/thông lượng; phải đối chiếu thêm tỷ lệ lỗi, khả năng mở rộng và ổn định theo DT18-NV1. Không kết luận “mở rộng tốt” từ một cấu hình chưa có đối chiếu.
- MT-R1/R2 nối với vùng ảnh hưởng và phát hiện bất thường; độ đo xếp hạng không thay cho độ đo phát hiện. Cần xác định đơn vị chấm, nhãn, cửa sổ và độ đo tương ứng trước phép thử xác nhận.
- MT-R3/R4 giữ so sánh công bằng và kiểm ảnh hưởng từng thành phần; hiệu quả MyRCA vẫn chưa được chốt.
- Lớp giải thích MT-5 giữ hiệu lực theo RES-034/041; nhiệm vụ mới không được dùng để tự xóa phần đã duyệt.

## 4. Ràng buộc và giới hạn

- Không đặt mục tiêu về lưu lượng sản xuất/người dùng thật hoặc hạ tầng ngoài điều kiện nhóm.
- Kiểm quyền vai trò/sở hữu là nghĩa vụ bắt buộc (`RES-048`), không tuyên bố là đóng góp mới.
- Không tự kết luận nguyên nhân cuối cùng, tự sửa nghiệp vụ hoặc hứa bỏ hoàn toàn tái hiện lỗi.
- Giao diện minh họa phân tích là sản phẩm DT18-NV3; không đặt chất lượng thẩm mỹ/UX thành câu hỏi nghiên cứu riêng.
- Mobile chỉ làm khi thực sự thừa thời gian (`PRJ-035`), không dùng việc thiếu app mobile làm tiêu chí thất bại của nhiệm vụ chính.
- CI baseline thuộc phạm vi Minh; CI/CD không trở thành mục tiêu nghiên cứu.

## 5. Truy vết và trạng thái kiểm

Phép đối chiếu ASR ngày 28/08 là bằng chứng lịch sử cho MT cũ: MT-1/2 → ASR-01–05; MT-3 → ASR-06; MT-4 → ASR-07/08; MT-5 và MT-R → điểm tích hợp ASR-09/15. Nó không chứng nhận bao phủ mọi khía cạnh DT18 mới. B9/B10 và thiết kế đã duyệt tiếp tục giữ nội dung kỹ thuật; phần bổ sung giao thức đo đi đúng gate.

| ID | Tình trạng hiện hành | Owner / đầu vào |
|---|---|---|
| A3-OPEN-01 | OPEN: ngưỡng đo sau phép đo thử; không tự điền | Minh và người phụ trách thí nghiệm |
| A3-OPEN-03 | OPEN: rà và duyệt lại A1–A6 theo DT18 | Minh |
| A3-OPEN-05 | OPEN: xác nhận từ giảng viên về diễn giải chi tiết; không suy từ xác nhận của Minh | Giảng viên |
| A3-OPEN-08 | OPEN: giao thức đầy đủ cho mở rộng/ổn định, phát hiện và vùng ảnh hưởng | Minh, nhóm; B15/B16 và bộ RCA |
| A3-OPEN-02/04/06/07 | Đã xử lý trong baseline cũ; không mở lại kiểm quyền ASR-14 hoặc các câu chữ MT cũ | RES-041/045/046/047/048; cách định vị phạm vi nay theo PRJ-026 |

## 6. Phép tự kiểm

- Không đổi ngưỡng, service, Saga hoặc bất biến; không biến quyết định giao việc thành thiết kế kỹ thuật.
- Tách số đo hệ thống, phát hiện và xếp hạng; tách bộ dữ liệu, môi trường và nhãn.
- Các bảng diễn giải mới cần Minh review; AI không tự duyệt.

## Nhật ký phiên bản — lịch sử, không dùng làm ngữ cảnh hiện hành

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A3-v0.7` | 2026-09-18 | Đồng bộ tên/nhiệm vụ DT18, phạm vi xây dựng và đánh giá hệ thống, nhóm bốn người và mobile tùy thời gian; bản diễn giải được đưa về REVIEW_READY | Theo PRJ-025–035 và phạm vi GOV-146, chưa duyệt nội dung mới |
| `A3-v0.6` | 2026-08-28 | **Áp ba quyết định của Lê Văn Minh và chạy một phép đối chiếu vừa mở khóa.** (1) **`MT-5` chuyển sang nhóm Vòng 1** (`RES-045`) — sửa chỗ `A3` và `A6` nói ngược nhau: `A6` đã chốt lớp giải thích thuộc Vòng 1 và đã đóng `A6-OPEN-02`, trong khi `A3` vẫn để `MT-5` ở bảng Vòng 2 **và** khai kế thừa đúng điểm mở đã khép ấy. `MT-4` ở lại Vòng 2 có chủ ý theo `AGENTS.md` mục 7. Mã `MT-5` giữ nguyên. (2) **`MT-1`–`MT-3` đổi vai** thành tiêu chí nghiệm thu sản phẩm (`RES-046`); câu chữ và ý nghĩa **không đổi một chữ**, `RES-002` còn hiệu lực với phần nội dung. (3) **`MT-R1`–`MT-R4` được Lê Văn Minh xác nhận** (`RES-047`); giảng viên **vẫn chưa**. (4) **Thêm §5.1** — đối chiếu mục tiêu với danh sách ASR, chạy được vì `B10` vừa duyệt: mọi mục tiêu phục vụ ít nhất một ASR, `A3-OPEN-02` đóng; chiều ngược lại lộ ra `ASR-14` không truy về mục tiêu nào, mở `A3-OPEN-07`. Đóng `A3-OPEN-02`, `A3-OPEN-04`, `A3-OPEN-06` và nửa đầu `A3-OPEN-05`. **Tài liệu vẫn `DRAFT`** — chờ Lê Văn Minh duyệt cùng `A5`, `A6` | Áp quyết định + đối chiếu sau `B10` |
| `A3-v0.5` | 2026-08-27 | **Sửa `MT-5` ở cả hai vế** theo `RES-041`: gỡ cụm *“đây là nhánh hỗ trợ, không thay thế trục nhất quán vòng đời vé”*, và thay tiêu chí đo cũ — vốn chấm cơ chế xếp hạng thay vì lớp giải thích — bằng ba tiêu chí giữ tại `A9` §6. Đóng phần câu chữ của `A3-OPEN-04`; phần Vòng 1/Vòng 2 giữ nguyên `OPEN`. `MT-1`–`MT-4` không đổi một chữ | Lan truyền quyết định người dùng |
| `A3-v0.4` | 2026-08-24 | Tách mục tiêu theo hai vòng phạm vi của `A6`: giữ nguyên câu chữ `MT-1`–`MT-5` cho Vòng 2 và bổ sung `MT-R1`–`MT-R4` cho Vòng 1; ghi việc đổi vai `MT-1`–`MT-3` ở mức `CANDIDATE`; thêm ràng buộc không trộn kết quả hai vòng; mở `A3-OPEN-04`–`A3-OPEN-06` | Lan truyền quyết định giữ hai khối song song |
| `A3-v0.3` | 2026-08-22 | Ghi nhận năm mục tiêu định tính đã được xác nhận; làm rõ `MT-3` bao gồm thử tải có kiểm soát và tăng dần, không đòi lưu lượng sản xuất; giữ A3 ở `DRAFT` cho tới khi B9/B10 chốt ngưỡng | Lan truyền quyết định nghiên cứu |
| `A3-v0.2` | 2026-08-21 | Đồng bộ với vòng rà chéo B8: `MT-4` không hứa bỏ tái hiện lỗi, `MT-5` nói rõ là phép đo trên tập giả thuyết chứ không coi trợ lý là bên kết luận | Sửa sau rà chéo |
| `A3-v0.1` | 2026-08-21 | Bản nháp đầu: mục tiêu tổng quát, năm mục tiêu cụ thể kèm cách kiểm chứng, phần cố ý không đặt thành mục tiêu, và ba việc còn treo chờ B9/B10 | Tạo mới |
