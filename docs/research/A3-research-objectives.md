# A3 — Mục tiêu nghiên cứu

- **Phiên bản:** `A3-v0.6`
- **Phê duyệt từng phần, 2026-08-29** (`GOV-054`): **§4 — phần cố ý không đặt thành mục tiêu — đã được Lê Văn Minh duyệt**, cùng quyết định `RES-048` ghi trong đó. §2.1, §3.1 và §5.1 **chưa duyệt**: Lê Văn Minh nêu rõ chưa hiểu ngữ cảnh vì ba mục viết bằng mã hiệu. Cần diễn giải lại bằng lời thường trước khi đưa ra duyệt.
- **Trạng thái:** `DRAFT` — **chưa được duyệt.** Mọi mục tiêu của cả hai vòng nay đã được Lê Văn Minh xác nhận ở mức định tính; còn lại hai việc trước khi chốt được tài liệu: `A3-OPEN-07` (`ASR-14` không có mục tiêu nào phủ) và ngưỡng số, vốn là đầu ra của vòng đo thử
- **Người duyệt:** — (chờ Lê Văn Minh duyệt `A3` cùng `A5`, `A6`). **Phần đã được xác nhận riêng:** `MT-1`–`MT-4` ở mức định tính (`RES-002`); câu chữ mới của `MT-5` (`RES-041`); vai của `MT-1`–`MT-3` (`RES-046`); vòng của `MT-4`/`MT-5` (`RES-045`); `MT-R1`–`MT-R4` (`RES-047`). **Giảng viên chưa xác nhận `MT-R1`–`MT-R4`**
- **Ngày duyệt:** —
- **Đầu vào:** `A1-context-and-urgency.md` (`A1-v0.2`), `A2-problem-statement.md` (`A2-v0.2`) và `A4-research-questions-draft.md` (`A4-v0.3`) — cả ba `APPROVED` ngày 2026-08-27 (`GOV-033`) sau tái baseline theo `DH-TEN`; `A5-doi-tuong-nghien-cuu.md` (`A5-v0.3`) và `A6-pham-vi.md` (`A6-v0.4`)
- **Đi vào báo cáo:** phần Mục tiêu nghiên cứu · **ràng buộc lên phần Đánh giá**
- **Ràng buộc:** không nhắc tên công nghệ, framework, thuật toán hay nhà cung cấp; không đặt mục tiêu cần điều kiện nhóm không có như người dùng thật hoặc hạ tầng lớn

> **Vì sao tài liệu này tồn tại và vì sao nó chỉ là `DRAFT`.** B9 khai đầu vào là “B8 và phiếu A3”, nhưng A3 chưa từng được tạo. Bản nháp này gỡ nút thắt đó. Theo `docs/quy-trinh-lam-viec.md`, A3–A6 chỉ được **chốt** ở Giai đoạn 3 cùng B9/B10, vì không thể viết một ngưỡng đo trước khi có kịch bản chất lượng và một vòng đo thử. Vì vậy bản này nêu **cách biết là đạt**, cố ý **không** điền con số.

> **Vì sao có `A3-v0.4`.** `A6` chia phạm vi thành **Vòng 1 — phạm vi nghiên cứu** (chẩn đoán nguyên nhân gốc) và **Vòng 2 — phạm vi sản phẩm** (FlashTicket Platform) để phân biệt cái được đo sâu với cái chỉ cần chạy được. Mục tiêu vì vậy phải tách theo hai vòng. Đây **không** phải hai đề tài: `DH-MT1` đặt hệ thống đặt vé vào mục tiêu đầu tiên của đề tài chẩn đoán. Bản này **giữ nguyên câu chữ `MT-1`–`MT-5`** đã được xác nhận tại `RES-002` và **bổ sung** nhóm `MT-R1`–`MT-R4` cho Vòng 1. Việc `MT-1`–`MT-3` đổi vai từ mục tiêu nghiên cứu sang tiêu chí nghiệm thu sản phẩm là **hệ quả suy ra**, giữ ở `CANDIDATE` tại §2.1, chưa được coi là xác nhận của chủ đồ án.

> **Vì sao có `A3-v0.5`.** `MT-5` là chỗ **cuối cùng** trong bộ tài liệu còn phát biểu trục của đề tài cũ. Vế mục tiêu ghi *“đây là nhánh hỗ trợ, không thay thế trục nhất quán vòng đời vé”* — tự hạ chẩn đoán xuống nhánh phụ, chống thẳng `DH-TEN` và `DH-MT4`. Vế tiêu chí đo chấm *“danh sách nguyên nhân khả dĩ do trợ lý đề xuất”* — chấm **cơ chế xếp hạng** chứ không chấm **lớp giải thích**, nên cơ chế đưa nhầm nghi phạm lên đầu thì lớp giải thích dù hoàn hảo vẫn bị chấm trượt.
>
> Các vòng dọn dẹp trước **cố ý không đụng** vào đây, vì `RES-002` là `USER_CONFIRMED` bảo vệ nguyên văn `MT-1`–`MT-5`; việc đó được ghi lại tại `A3-OPEN-04` chứ không bị bỏ quên. Ngày 2026-08-27 Lê Văn Minh chốt sửa cả hai vế thành một quyết định mới, `RES-041`, thay `RES-002` **chỉ ở phần `MT-5`**. **`MT-1`–`MT-4` không đổi một chữ.**

## 1. Hai mục tiêu tổng quát

### 1.1 Vòng 1 — phạm vi nghiên cứu

> Khảo sát, xây dựng và đánh giá một cơ chế chẩn đoán nguyên nhân gốc cho hệ microservice dựa trên đồ thị phụ thuộc dựng từ dấu vết vận hành, đối chứng trung thực với các phương pháp đã công bố trên cùng bộ dữ liệu và cùng bộ độ đo.

### 1.2 Vòng 2 — phạm vi sản phẩm

> Thiết kế và kiểm chứng một cách phối hợp vòng đời vé trong hệ thống đặt vé phân tán sao cho các bất biến cốt lõi được duy trì và hệ thống phục hồi về trạng thái chấp nhận được khi gặp yêu cầu đồng thời, thông điệp lặp hoặc lỗi từng phần, trong giới hạn hạ tầng của đồ án.

*(Câu ở §1.2 là mục tiêu tổng quát đã có từ `A3-v0.1`, giữ nguyên câu chữ.)*

## 2. Mục tiêu cụ thể của Vòng 2 — phạm vi sản phẩm

> **Bốn mục, không phải năm** (`RES-045`, 2026-08-28). `MT-5` đã chuyển sang §3 — nhóm Vòng 1. Lý do: `A6` §5 ghi `A6-OPEN-02` **đã đóng** với kết luận *"lớp giải thích của `DH-MT4` thuộc **Vòng 1**"*, trong khi bản trước của tài liệu này vẫn để `MT-5` ở bảng Vòng 2 **và** khai điểm mở của nó là *"kế thừa `A6-OPEN-02`"* — tức kế thừa một điểm mở mà nguồn của nó đã khép lại. Hai phiếu nay nói cùng một điều. **Mã `MT-5` giữ nguyên**, không đánh số lại.
>
> **`MT-4` ở lại Vòng 2 có chủ ý.** `AGENTS.md` mục 7 phân trách nhiệm rõ: *"FlashTicket phải sinh **dữ liệu quan sát** nào"* thuộc bộ hệ thống, còn cách xử lý log và trace thuộc bộ RCA. `MT-4` nói về thứ hệ thống phải **sinh ra**, nên nó là mục tiêu của sản phẩm.

> **Vai của `MT-1`–`MT-3` đã đổi** (`RES-046`, 2026-08-28): chúng là **tiêu chí nghiệm thu sản phẩm**, báo cáo ở chương Kiểm thử, **không viết là đóng góp nghiên cứu**. Câu chữ và ý nghĩa **không đổi một chữ**; `RES-002` vẫn còn hiệu lực với phần nội dung. Xem §2.1.

| Mã | Mục tiêu cụ thể | Biết là đạt bằng |
|---|---|---|
| `MT-1` | Xác định và đặc tả tập bất biến cốt lõi của vòng đời vé, từ giữ chỗ và thanh toán tới phát hành và kiểm soát vào cửa | Mỗi bất biến truy được về một quyết định nghiệp vụ đã xác nhận, và có ít nhất một ca kiểm thử làm nó thất bại nếu cơ chế bảo vệ bị gỡ bỏ |
| `MT-2` | Thiết kế cách phối hợp trạng thái giữa các bước của vòng đời vé sao cho các bất biến ở `MT-1` vẫn giữ khi có yêu cầu gần đồng thời, thông điệp lặp và lỗi từng phần | Chạy được tập ca đồng thời, lặp và lỗi từng phần trên hệ thống đã dựng, ghi lại kết quả đạt hoặc không đạt cho từng bất biến |
| `MT-3` | Làm rõ đánh đổi giữa tính đúng đắn, khả năng phục hồi và chi phí hiệu năng của cách phối hợp được chọn | Thử tải có kiểm soát và tăng dần trên cấu hình được công bố, ghi độ trễ và thông lượng ở từng mức tải; ngưỡng cụ thể chốt ở B9/B10. Đây là kiểm thử hiệu năng/tải trong giới hạn đồ án, không phải tuyên bố mô phỏng lưu lượng sản xuất |
| `MT-4` | Bảo đảm hệ thống sinh đủ dấu vết vận hành để hỗ trợ việc xác định nguyên nhân một sự cố đã xảy ra; không cam kết loại bỏ việc tái hiện lỗi theo `PRJ-002` | Với tập ca lỗi có nguyên nhân đã biết, đo được tỷ lệ ca mà dấu vết thu được chứa đủ tín hiệu cần thiết để lần ra nguyên nhân |

### 2.1 Đổi vai của `MT-1`–`MT-3` — **đã chốt**

Theo định nghĩa Vòng 2 của Tầng A, phạm vi sản phẩm chỉ được kiểm chứng ở mức **kiểm thử chức năng**. Ba mục tiêu `MT-1`, `MT-2`, `MT-3` vốn được viết như mục tiêu nghiên cứu có đo đạc, nên phải đổi vai.

| Nội dung | Trạng thái |
|---|---|
| Câu chữ và ý nghĩa của `MT-1`–`MT-4` | Giữ nguyên; `RES-002` còn hiệu lực với bốn mục tiêu này |
| Câu chữ của `MT-5` | **Đã sửa cả hai vế** ngày 2026-08-27 theo `RES-041`, thay `RES-002` **chỉ ở phần `MT-5`** |
| `MT-1`–`MT-3` chuyển thành **tiêu chí nghiệm thu sản phẩm có số**, báo cáo ở chương Kiểm thử, không viết là đóng góp nghiên cứu | **`USER_CONFIRMED`** — `RES-046`, 2026-08-28. Thay `RES-002` **chỉ ở phần vai trò**, không đụng phần nội dung |
| `MT-4` thuộc **Vòng 2**; `MT-5` thuộc **Vòng 1** | **`USER_CONFIRMED`** — `RES-045`, 2026-08-28. Đóng nốt `A3-OPEN-04`; hai phiếu `A3` và `A6` nay khớp nhau |

> **Đổi vai không phải hạ giá trị.** Ba mục tiêu này **vẫn phải đạt và vẫn phải đo**; chúng chỉ thôi được viết là đóng góp nghiên cứu. Đề tài chính thức là chẩn đoán nguyên nhân gốc; việc hệ đặt vé không bán vượt vé, không tính tiền hai lần, phục hồi được sau lỗi từng phần là yêu cầu đúng đắn mà **mọi hệ bán vé nghiêm túc đều phải có**. Giữ chúng ở vai đóng góp buộc nhóm phải chứng minh cái không mới; đổi vai làm phần đóng góp của báo cáo gọn và sắc hơn.

Bốn tiêu chí nghiệm thu tương ứng đã được liệt kê tại `A6` §2.1 và đều truy về file định hướng đồ án, không phải do A3 tự đặt thêm.

## 3. Mục tiêu cụ thể của Vòng 1 — phạm vi nghiên cứu

Bốn mục `MT-R` bám vào bốn mục trong phạm vi nghiên cứu tại `A6` §1. **`MT-5` chuyển sang nhóm này** ngày 2026-08-28 theo `RES-045`, vì `A6` đã chốt lớp giải thích thuộc Vòng 1; mã của nó giữ nguyên để không lệch các chỗ đang dẫn.

> **Trạng thái xác nhận, đọc kỹ hai vế:** `MT-R1`–`MT-R4` **đã được Lê Văn Minh xác nhận** ở mức định tính ngày 2026-08-28 (`RES-047`). **Giảng viên vẫn CHƯA xác nhận** — nơi trình là báo cáo hai tuần ở mốc `DH-MOC`. Không được đọc dòng đầu thành dòng sau.

| Mã | Mục tiêu cụ thể | Biết là đạt bằng |
|---|---|---|
| `MT-R1` | Dựng được đồ thị phụ thuộc giữa các thành phần từ dấu vết vận hành của một ca lỗi, và ánh xạ được tín hiệu bất thường từ các nguồn dữ liệu còn lại lên đồ thị đó | Tỷ lệ ca lỗi dựng được đồ thị trên bộ dữ liệu đã chọn, kèm quy mô đồ thị thu được ở mỗi hệ thống nguồn |
| `MT-R2` | Xây dựng một cơ chế lan truyền bất thường và xếp hạng nghi phạm trên đồ thị đó | Điểm của cơ chế theo bộ độ đo xếp hạng đã định nghĩa ở `A9`, tính trên toàn bộ ca lỗi của bộ đã chọn |
| `MT-R3` | Đánh giá cơ chế đó **cùng điều kiện** với một tập phương pháp đã công bố và với một mức sàn ngẫu nhiên | Một bảng so sánh cho mỗi bộ dữ liệu, mọi phương pháp chạy cùng bộ và cùng độ đo, có cột đối chứng ngẫu nhiên |
| `MT-R4` | Làm rõ việc thêm nguồn dữ liệu vào có cải thiện kết quả hay không | Chênh lệch điểm giữa biến thể đơn nguồn và biến thể đa nguồn cùng lõi, trên cùng bộ dữ liệu |
| `MT-5` | Đánh giá mức hữu ích của **lớp giải thích** — thứ `DH-MT4` gọi là *"trợ lý"* — trên tập ca lỗi dùng để đo `MT-R2` | Bộ tiêu chí được định nghĩa trước, giữ tại `docs/research-rca/A9-do-do-thuc-nghiem.md` §6: (1) **trung thành với đầu vào** — lời diễn giải chỉ dùng bằng chứng thật sự có trong danh sách nghi phạm nhận được; (2) **hữu ích** — bước kiểm tra được gợi ý có dẫn tới xác nhận hoặc loại trừ được nghi phạm không; (3) **trung thực** — số lần đưa ra bằng chứng hoặc hành động không có thật. Đây là phép đo trên tập giả thuyết, **không** coi lớp giải thích là bên kết luận nguyên nhân cuối cùng (`PRJ-002` qua `RES-034`) |

### 3.1 Ràng buộc bắt buộc lên nhóm `MT-R`

- **Không mục tiêu nào được phát biểu là "đề xuất phương pháp mới".** `A10` §2.2 đã ghi: việc dùng lại một cơ chế đã công bố không tạo ra tính mới, và chỉ được viết là đề xuất khi có kết quả thực nghiệm chống lưng.
- `MT-R3` là mục tiêu **không được cắt** dù thiếu thời gian. Không có nó thì `MT-R2` không đọc được: một điểm số không kèm mức sàn và không kèm đối chứng thì không cho biết điều gì (`A7` §7).
- Không mục tiêu nào gắn ngưỡng số ở bản này.

## 4. Phần cố ý không đặt thành mục tiêu

- Không đặt mục tiêu về số lượng người dùng thật hoặc lưu lượng sản xuất; nhóm không có điều kiện đó.
- Không đặt mục tiêu loại bỏ hoàn toàn việc tái hiện lỗi thủ công. `PRJ-002` đã giới hạn phạm vi hỗ trợ chẩn đoán ở mức thu thập, liên kết dấu vết và đề xuất nguyên nhân khả dĩ, không tự kết luận và không tự sửa.
- Không đặt mục tiêu so sánh toàn diện các mẫu kiến trúc. Việc so sánh chỉ diễn ra trong phạm vi các phương án được hình thành ở B11-A và giới hạn hạ tầng của đồ án.
- Không đặt mục tiêu về chất lượng giao diện hoặc trải nghiệm người dùng.
- Không đặt mục tiêu đề xuất một bộ dữ liệu chuẩn hoặc một benchmark mới; đồ án dùng bộ đã công bố.
- Không đặt mục tiêu tuyên bố tính mới cho cơ chế ở `MT-R2`. Xem §3.1.
- **Không đặt kiểm soát truy cập thành mục tiêu, dù `ASR-14` ở mức Cao** (`RES-048`, 2026-08-29). Lê Văn Minh chốt: *“việc kiểm quyền theo vai trò và quan hệ sở hữu là một điều kiện luôn luôn phải có cho dù là hệ thống nào đi nữa… nó luôn luôn mặc định phải có chứ không phải là một yêu cầu cần xếp cao hay thấp.”* Một nghĩa vụ mặc định của mọi hệ thống là **ràng buộc**, không phải **đích cần đo** — đặt nó thành mục tiêu nghiên cứu là hứa một đóng góp mà đề tài không có. Nó vẫn phải làm đúng và vẫn được nghiệm thu, bằng kiểm thử chức năng ở `A6` §2.1.

> **Vì sao `ASR-14` vẫn ở mức Cao mà không mâu thuẫn với câu trên.** Mức ưu tiên ở `B10` trả lời đúng một câu: *“nếu đòi hỏi này đổi thì kiến trúc có phải khác đi không?”* — nó đo **sức nặng với kiến trúc**, không đo **mức độ mong muốn**. Một nghĩa vụ bắt buộc thì đương nhiên làm kiến trúc khác đi, nên mức Cao là hệ quả của chính lập luận *“luôn luôn phải có”*, không phải trái với nó. Trong danh sách ASR nó nằm cùng nhóm với `ASR-12`, `ASR-13`, `ASR-15` — đều là ràng buộc, đều không truy về mục tiêu nào, và `A3` §5.1 đã kết luận việc đó là **đúng**. **`GOV-040` giữ nguyên hiệu lực; `B10-v0.7` không bị mở lại.**

## 5. Ràng buộc lên phần Đánh giá

Mỗi mục tiêu ở §2 và §3 phải có một mục tương ứng trong kế hoạch kiểm chứng ở B15. Mục tiêu nào không có cách lấy bằng chứng ở cuối kỳ thì phải viết lại hoặc bỏ, không được giữ để tài liệu trông đầy đủ.

`MT-3`, `MT-4` và `MT-5` là ba mục tiêu bắt buộc có số. Ba con số đó chỉ được điền sau khi B9 dựng kịch bản chất lượng và B10 xếp ưu tiên; điền sớm thì hoặc quá dễ hoặc bất khả thi.

Nhóm `MT-R1`–`MT-R4` cũng bắt buộc có số, nhưng số của chúng đến từ bộ độ đo xếp hạng đã định nghĩa sẵn ở `A9`, không phải từ B9/B10. Điều còn thiếu ở nhóm này không phải cách đo mà là **bộ dữ liệu chính** (`A8-OPEN-04`).

**Ràng buộc trình bày:** kết quả của hai vòng không được trộn vào cùng một bảng, cùng một biểu đồ hay cùng một câu kết luận. Vòng 1 trả lời câu hỏi nghiên cứu; Vòng 2 trả lời tiêu chí nghiệm thu sản phẩm.

### 5.1 Đối chiếu mục tiêu với danh sách ASR — đã chạy 2026-08-28

`A3-OPEN-02` đòi phép đối chiếu này với hạn là **sau khi `B10` xong**. `B10-v0.5` được duyệt ngày 2026-08-28 (`GOV-043`) — **lượt duyệt này sau đó bị `GOV-047` rút và được thay bằng `B10-v0.7` ngày 2026-08-29 (`GOV-050`); danh sách 15 ASR không đổi nên phép đối chiếu dưới đây vẫn đứng** — nên phép thử chạy được và kết quả ghi ở đây.

**Chiều bắt buộc — mỗi mục tiêu có phục vụ ít nhất một ASR không:**

| Mục tiêu | ASR tương ứng ở `B10` | Ghi chú |
|---|---|---|
| `MT-1` đặc tả bất biến vòng đời vé | `ASR-01`, `ASR-02`, `ASR-03`, `ASR-04`, `ASR-05` | Năm ASR này chính là các bất biến ấy phát biểu ở mức kết quả |
| `MT-2` phối hợp trạng thái giữ bất biến khi đồng thời, lặp, lỗi từng phần | `ASR-01`–`ASR-05` | Cùng tập; `MT-1` hỏi *bất biến nào*, `MT-2` hỏi *giữ được không khi bị ép* |
| `MT-3` đánh đổi đúng đắn – phục hồi – hiệu năng | `ASR-06` | `ASR-06` dẫn thẳng `MT-3` ở cột nguồn |
| `MT-4` hệ thống sinh đủ dấu vết | `ASR-07`, `ASR-08` | Đủ tín hiệu để lần ra nguyên nhân; dựng lại được trình tự |
| `MT-5` mức hữu ích của lớp giải thích | `ASR-09` | Chỉ phần *"lời giải thích đến được người có quyền sử dụng"*. **Chất lượng** lời giải thích đo ở `A9` §6, **không** qua ASR — đúng ranh giới hai bộ tài liệu |
| `MT-R1`–`MT-R4` | `ASR-09`, `ASR-15` (gián tiếp) | `ASR-09` chừa chỗ cho cơ chế chạy trên hệ nhà; `ASR-15` giữ khả năng chèn lỗi để có ca có đáp án. Phần đo chính lấy từ bộ dữ liệu công bố, không qua ASR |

**Kết quả: đạt.** Không mục tiêu nào không phục vụ ASR nào. `A3-OPEN-02` đóng.

**Chiều ngược lại — không thuộc phép thử, nhưng phải ghi ra vì nó lộ một khoảng trống thật:**

Năm ASR **không truy về mục tiêu nào**: `ASR-10`, `ASR-11` là hai trần phạm vi; `ASR-12` là ràng buộc chỉ đọc; `ASR-13` và `ASR-15` là ràng buộc phủ định. Bốn trong năm cái đó là **ràng buộc**, không phải mục tiêu, nên việc chúng không có mục tiêu tương ứng là đúng.

✅ **`ASR-14` — đã xử lý ngày 2026-08-29 tại `RES-048`.** Lê Văn Minh chốt rằng kiểm quyền theo vai trò và quan hệ sở hữu là **nghĩa vụ mặc định của mọi hệ thống**, nên nó là **ràng buộc**, không phải mục tiêu. Vậy **cả năm** ASR không truy về mục tiêu đều là ràng buộc, và kết luận *“việc chúng không có mục tiêu tương ứng là đúng”* ở đoạn trên áp cho cả năm. Ghi tại §4. `A3-OPEN-07` đóng. Đoạn dưới đây là **lo ngại ban đầu, giữ lại làm lịch sử**:

> ⚠️ **Ngoại lệ đáng lo là `ASR-14`** — *kiểm quyền theo vai trò **và** theo quan hệ sở hữu*. Nó vừa được Lê Văn Minh nâng lên mức Cao ngày 2026-08-28 (`GOV-040`), tức là một đòi hỏi **thật sự làm kiến trúc khác đi**. Nhưng nó **không truy về mục tiêu nào ở §2 hay §3**, và cũng **không nằm trong bốn tiêu chí nghiệm thu** ở `A6` §2.1. Hệ quả cho báo cáo: chương Kiến trúc sẽ có một động lực mà chương Mục tiêu không hề nhắc tới. Mở tại `A3-OPEN-07`.

## 6. Việc còn phải làm

| ID | Việc | Gate |
|---|---|---|
| `A3-OPEN-01` | Điền ngưỡng đo cho `MT-3`, `MT-4`, `MT-5` | Sau B9 và B10 |
| `A3-OPEN-02` | **ĐÃ ĐÓNG** ngày 2026-08-28. Phép đối chiếu đã chạy sau khi `B10-v0.5` được duyệt (nay đọc là `B10-v0.7`, `GOV-050`; 15 ASR không đổi); kết quả và bảng ánh xạ ghi tại §5.1. Mọi mục tiêu đều phục vụ ít nhất một ASR | Đã đóng |
| `A3-OPEN-03` | Tái kiểm tra A1, A2 và A3 cùng nhau, rồi chốt A3–A6 | Giai đoạn 3, và impact check lại tại B11-C |
| `A3-OPEN-04` | **Phần câu chữ đã đóng** ngày 2026-08-27 theo `RES-041`: `MT-5` từng ghi *“đây là nhánh hỗ trợ, không thay thế trục nhất quán vòng đời vé”* — phát biểu trục của đề tài cũ, chống thẳng `DH-TEN` và `DH-MT4` — nay đã gỡ, cùng với tiêu chí đo cũ vốn chấm nhầm cơ chế xếp hạng thay vì lớp giải thích. **Phần còn lại ĐÃ ĐÓNG** ngày 2026-08-28 tại `RES-045`: `MT-4` thuộc **Vòng 2** vì `AGENTS.md` mục 7 giao việc *sinh dữ liệu quan sát* cho bộ hệ thống; `MT-5` thuộc **Vòng 1** theo đúng kết luận mà `A6` đã chốt. Trước đó dòng này kế thừa `A6-OPEN-02` — một điểm mở mà `A6` **đã khép lại**, nên hai phiếu nói ngược nhau | Đã đóng |
| `A3-OPEN-05` | **Nửa đầu ĐÃ ĐÓNG** ngày 2026-08-28 tại `RES-047`: Lê Văn Minh đã xác nhận `MT-R1`–`MT-R4` ở mức định tính. ⚠️ **Nửa sau CÒN MỞ: giảng viên chưa xác nhận.** Nơi trình là báo cáo hai tuần | Giảng viên — mốc `DH-MOC`, hạn 2026-09-05 |
| `A3-OPEN-06` | **ĐÃ ĐÓNG** ngày 2026-08-28 tại `RES-046`: `MT-1`–`MT-3` đổi vai thành tiêu chí nghiệm thu sản phẩm, báo cáo ở chương Kiểm thử. Câu chữ và ý nghĩa không đổi một chữ | Đã đóng |
| `A3-OPEN-07` | **ĐÃ ĐÓNG** ngày 2026-08-29 tại `RES-048`: Lê Văn Minh chọn hướng thứ ba — **ghi rõ là ràng buộc, không nâng thành mục tiêu**, vì kiểm quyền theo vai trò và quan hệ sở hữu là nghĩa vụ mặc định của mọi hệ thống. Ghi tại §4; `A6` §2.1 nhận phần nghiệm thu. `ASR-14` **giữ mức Cao**, `GOV-040` nguyên hiệu lực. *Nội dung gốc:* ⚠️ **`ASR-14` — kiểm quyền theo vai trò và theo quan hệ sở hữu — không truy về mục tiêu nào**, và cũng không nằm trong bốn tiêu chí nghiệm thu ở `A6` §2.1. Nó vừa được nâng lên mức Cao tại `GOV-040`, tức là một động lực thật sự làm kiến trúc khác đi. Nếu để nguyên, chương Kiến trúc sẽ có một động lực mà chương Mục tiêu không nhắc tới. **Ba cách xử:** thêm một mục tiêu cụ thể về kiểm soát truy cập vào §2; hoặc thêm nó vào tập tiêu chí nghiệm thu ở `A6` §2.1; hoặc ghi rõ đây là ràng buộc chất lượng không nâng thành mục tiêu, và nói lý do. **Agent không tự chọn** — cả ba đều đụng phạm vi | Lê Văn Minh — trước khi chốt `A3` | Đã đóng tại `RES-048` 

## 7. Phép tự kiểm

- [x] Mỗi vòng có một mục tiêu tổng quát viết trong một câu.
- [x] Mỗi vòng có 4–5 mục tiêu cụ thể, nằm trong khoảng 3–5 mà phiếu A3 yêu cầu. Sau `RES-045`: Vòng 2 có **bốn** (`MT-1`–`MT-4`), Vòng 1 có **năm** (`MT-R1`–`MT-R4` và `MT-5`). Đếm lại trực tiếp trên hai bảng, không đọc lại câu cũ.
- [x] **`A3` và `A6` nói cùng một điều về lớp giải thích.** `A6` §5 chốt nó thuộc Vòng 1 và `A6` §1 xếp `DH-MT4` vào bảng Vòng 1; `A3` §3 nay cũng vậy. **Ô này ở `v0.5` không giữ được** — `A3` để `MT-5` ở bảng Vòng 2 và còn khai kế thừa một điểm mở mà `A6` đã khép.
- [x] **Phép đối chiếu mục tiêu với ASR đã chạy** sau khi `B10` được duyệt, kết quả tại §5.1: mọi mục tiêu phục vụ ít nhất một ASR. Chiều ngược lại cũng được rà, và **một khoảng trống thật đã được ghi ra** thay vì bỏ qua — `ASR-14` không truy về mục tiêu nào (`A3-OPEN-07`).
- [x] Mỗi mục tiêu có cột “biết là đạt bằng”, trả lời được câu hỏi *cuối kỳ lấy gì ra để chứng minh*.
- [x] Không có tên công nghệ, framework, thuật toán hoặc nhà cung cấp — kể cả ở nhóm `MT-R`, nơi các phương pháp cụ thể được dẫn qua `A10` thay vì gọi tên.
- [x] Không có mục tiêu cần người dùng thật hoặc hạ tầng lớn.
- [x] Chưa điền con số nào.
- [x] Codex đã rà chéo A3 cùng B6/B8; `MT-3` và `NFR-04` dùng cùng cách hiểu về thử tải có kiểm soát.
- [x] `MT-1`–`MT-4` giữ nguyên câu chữ đã được Lê Văn Minh xác nhận (`RES-002`). **`MT-5` là ngoại lệ có chủ đích:** `A3-v0.5` sửa cả hai vế của nó theo `RES-041` — một quyết định mới do Lê Văn Minh chốt, thay `RES-002` **chỉ ở phần `MT-5`**, không sửa lặng lẽ và không đụng bốn mục tiêu còn lại.
- [x] Không mục tiêu nào tuyên bố tính mới.
- [x] Hệ quả suy ra được tách thành `CANDIDATE`/`OPEN` riêng, không gộp vào phần đã được xác nhận.
- [x] Ô tự kiểm cũ *“mục tiêu hỗ trợ chẩn đoán đặt đúng vai trò nhánh hỗ trợ theo A4”* **đã bỏ hẳn**, không phải để treo. `A4-v0.2` gỡ cụm *"nhánh hỗ trợ"* khỏi chính nó (`RES-032`), nên ô cũ mất đối tượng đối chiếu. Phần còn lại — `MT-1`–`MT-3` đổi vai — vẫn ở `A3-OPEN-04`.
- [x] **`MT-R1`–`MT-R4` đã được Lê Văn Minh xác nhận** ngày 2026-08-28 (`RES-047`), ở mức định tính, không gắn ngưỡng.
- [ ] **Giảng viên xác nhận `MT-R1`–`MT-R4`** — nửa còn lại của `A3-OPEN-05`, trình ở báo cáo hai tuần.
- [x] `A3-OPEN-07` được quyết: **ràng buộc không nâng thành mục tiêu** (`RES-048`, 2026-08-29). Hai hướng kia — thành mục tiêu ở §2, hoặc thành tiêu chí nghiệm thu thứ năm ở `A6` §2.1 — **đều bị loại**, vì §2 tự khai *“không phải do A3 tự đặt thêm”* và bốn tiêu chí kia đều truy về file định hướng.
- [ ] Điền ngưỡng cho `MT-3`, `MT-4`, `MT-5` sau vòng đo thử đầu tiên. **`B9`/`B10` đã xong và đã duyệt**, nhưng ngưỡng là đầu ra của **vòng đo thử**, không phải của hai tài liệu đó — `B9-OPEN-01` vẫn mở sau khi `B9` được duyệt.
- [ ] Lê Văn Minh duyệt `A3` cùng `A5`, `A6`; kèm tái kiểm tra `A1`, `A2`, `A4` theo `A3-OPEN-03`. **AI không tự đánh dấu** (`GOV-011`).

## 8. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A3-v0.6` | 2026-08-28 | **Áp ba quyết định của Lê Văn Minh và chạy một phép đối chiếu vừa mở khóa.** (1) **`MT-5` chuyển sang nhóm Vòng 1** (`RES-045`) — sửa chỗ `A3` và `A6` nói ngược nhau: `A6` đã chốt lớp giải thích thuộc Vòng 1 và đã đóng `A6-OPEN-02`, trong khi `A3` vẫn để `MT-5` ở bảng Vòng 2 **và** khai kế thừa đúng điểm mở đã khép ấy. `MT-4` ở lại Vòng 2 có chủ ý theo `AGENTS.md` mục 7. Mã `MT-5` giữ nguyên. (2) **`MT-1`–`MT-3` đổi vai** thành tiêu chí nghiệm thu sản phẩm (`RES-046`); câu chữ và ý nghĩa **không đổi một chữ**, `RES-002` còn hiệu lực với phần nội dung. (3) **`MT-R1`–`MT-R4` được Lê Văn Minh xác nhận** (`RES-047`); giảng viên **vẫn chưa**. (4) **Thêm §5.1** — đối chiếu mục tiêu với danh sách ASR, chạy được vì `B10` vừa duyệt: mọi mục tiêu phục vụ ít nhất một ASR, `A3-OPEN-02` đóng; chiều ngược lại lộ ra `ASR-14` không truy về mục tiêu nào, mở `A3-OPEN-07`. Đóng `A3-OPEN-02`, `A3-OPEN-04`, `A3-OPEN-06` và nửa đầu `A3-OPEN-05`. **Tài liệu vẫn `DRAFT`** — chờ Lê Văn Minh duyệt cùng `A5`, `A6` | Áp quyết định + đối chiếu sau `B10` |
| `A3-v0.5` | 2026-08-27 | **Sửa `MT-5` ở cả hai vế** theo `RES-041`: gỡ cụm *“đây là nhánh hỗ trợ, không thay thế trục nhất quán vòng đời vé”*, và thay tiêu chí đo cũ — vốn chấm cơ chế xếp hạng thay vì lớp giải thích — bằng ba tiêu chí giữ tại `A9` §6. Đóng phần câu chữ của `A3-OPEN-04`; phần Vòng 1/Vòng 2 giữ nguyên `OPEN`. `MT-1`–`MT-4` không đổi một chữ | Lan truyền quyết định người dùng |
| `A3-v0.4` | 2026-08-24 | Tách mục tiêu theo hai vòng phạm vi của `A6`: giữ nguyên câu chữ `MT-1`–`MT-5` cho Vòng 2 và bổ sung `MT-R1`–`MT-R4` cho Vòng 1; ghi việc đổi vai `MT-1`–`MT-3` ở mức `CANDIDATE`; thêm ràng buộc không trộn kết quả hai vòng; mở `A3-OPEN-04`–`A3-OPEN-06` | Lan truyền quyết định giữ hai khối song song |
| `A3-v0.3` | 2026-08-22 | Ghi nhận năm mục tiêu định tính đã được xác nhận; làm rõ `MT-3` bao gồm thử tải có kiểm soát và tăng dần, không đòi lưu lượng sản xuất; giữ A3 ở `DRAFT` cho tới khi B9/B10 chốt ngưỡng | Lan truyền quyết định nghiên cứu |
| `A3-v0.2` | 2026-08-21 | Đồng bộ với vòng rà chéo B8: `MT-4` không hứa bỏ tái hiện lỗi, `MT-5` nói rõ là phép đo trên tập giả thuyết chứ không coi trợ lý là bên kết luận | Sửa sau rà chéo |
| `A3-v0.1` | 2026-08-21 | Bản nháp đầu: mục tiêu tổng quát, năm mục tiêu cụ thể kèm cách kiểm chứng, phần cố ý không đặt thành mục tiêu, và ba việc còn treo chờ B9/B10 | Tạo mới |
