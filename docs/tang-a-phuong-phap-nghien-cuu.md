# TẦNG A — KHUNG PHƯƠNG PHÁP NGHIÊN CỨU
## ĐATN FlashTicket · PTIT · Nộp 14/12/2026
### *(Bản viết lại — thay thế bản trước, vốn đã đi thẳng vào điền nội dung thay vì dựng khung)*

---

## 0. Tài liệu này là gì

**Bốn phần, theo thứ tự:**

| Phần | Nội dung |
|---|---|
| 1 | **Tham khảo** — DSRM cung cấp cách nối vấn đề, giải pháp và đánh giá |
| 2 | **Chắt lọc** — lấy gì, bỏ gì, sửa gì, vì sao |
| 3 | **Ghép chuẩn** — phần DSRM không phủ được thì lấy từ chuẩn Chương 1 của ĐATN Việt Nam |
| 4 | **Khung làm việc** — các ô cần điền để chuẩn bị nội dung báo cáo |

**Không chứa:** nội dung cụ thể của đề tài (câu hỏi nghiên cứu là gì, phạm vi gồm những gì). Đó là thứ bạn điền vào Phần 4 khi làm bước 1.1–1.3 của Tầng B.

**Quan hệ ba tầng — quy tắc chống trùng lặp:** A quyết định *lấy gì làm bằng chứng và tại sao*; B quyết định *làm gì theo thứ tự nào*; C quyết định *vẽ và viết theo quy ước nào*. Mỗi thông tin sống ở đúng một tầng.

**Vòng đời:** đây là tài liệu sống trong giai đoạn lập kế hoạch. Chỉ đóng băng các mục đã được nhóm và giảng viên xác nhận; thay đổi quan trọng ghi vào Nhật ký hoặc ADR tương ứng.

Ký hiệu ưu tiên: 🔴 bắt buộc · 🟡 nên có · ⚪ cắt được

---

# PHẦN 1 — DSRM Ở MỨC THAM KHẢO

## 1.1 DSRM sinh ra để giải quyết chuyện gì

Trong ngành hệ thống thông tin có hai kiểu nghiên cứu: kiểu **giải thích** một hiện tượng có sẵn (behavioral science — khảo sát, thống kê, kiểm định giả thuyết), và kiểu **tạo ra** một thứ chưa có để giải quyết vấn đề (design science). Kiểu thứ hai lâu nay bị chê là "chỉ là làm sản phẩm" vì không có quy trình chuẩn để trình bày và đánh giá.

Peffers và cộng sự (2007) đề xuất DSRM để lấp chỗ đó: một quy trình sáu bước cho phép người làm ra tạo tác trình bày công việc của mình theo cách có thể đánh giá được như một nghiên cứu.

**Tư tưởng cốt lõi — chỉ cần nhớ đúng một câu:**

> Mục tiêu và cách kiểm chứng phải được xác định **trước khi** xây; ngưỡng số chỉ chốt khi có căn cứ hoặc phép đo thăm dò, rồi được đo lại trên phương án cuối.

Mọi thứ còn lại của DSRM là bộ khung để bảo đảm câu trên được thực hiện nghiêm túc. Nếu bạn chỉ giữ được một ý từ toàn bộ Tầng A, giữ ý này.

## 1.2 Sáu hoạt động

| # | Hoạt động | Nội dung | Sản phẩm đầu ra |
|---|---|---|---|
| 1 | **Nhận diện vấn đề & động cơ** | Xác định vấn đề cụ thể và biện minh vì sao đáng giải quyết. Vấn đề nên được chẻ nhỏ để giải pháp có thể "bắt" được từng phần | Phát biểu vấn đề, lý do đáng làm |
| 2 | **Xác định mục tiêu của giải pháp** | Suy ra mục tiêu từ vấn đề, dựa trên hiểu biết về cái gì khả thi. Mục tiêu có thể định lượng (tốt hơn cái hiện có ở điểm nào, bao nhiêu) hoặc định tính (mô tả cách giải pháp hỗ trợ vấn đề chưa được giải) | Danh sách mục tiêu có tiêu chí đo |
| 3 | **Thiết kế & xây dựng** | Tạo ra tạo tác: xác định chức năng mong muốn, kiến trúc, rồi hiện thực hóa | Tạo tác (hệ thống, mô hình, phương pháp) |
| 4 | **Trình diễn** | Dùng tạo tác để giải quyết **một** trường hợp cụ thể của vấn đề — thí nghiệm, mô phỏng, tình huống, chứng minh | Bằng chứng "nó dùng được" |
| 5 | **Đánh giá** | Quan sát và **đo** mức độ tạo tác hỗ trợ giải quyết vấn đề. So sánh mục tiêu ở bước 2 với kết quả quan sát được ở bước 4 | Số liệu, kết luận đạt/không đạt |
| 6 | **Công bố** | Truyền đạt vấn đề, tạo tác, tính hữu ích và tính mới tới các đối tượng liên quan | Bài báo / báo cáo / bảo vệ |

**Điểm dễ nhầm nhất và cũng là điểm giá trị nhất của bảng này: hoạt động 4 khác hoạt động 5.**

Trình diễn chỉ chứng minh *"nó chạy được trong một trường hợp"*. Đánh giá mới trả lời *"nó tốt đến mức nào so với mục tiêu đã đặt"*. Rất nhiều ĐATN dừng ở hoạt động 4 — demo chức năng, chụp màn hình — rồi đặt tên chương là "Đánh giá". Hội đồng có kinh nghiệm nhận ra ngay.

## 1.3 Bốn điểm khởi đầu và vòng phản hồi

DSRM cho phép vào quy trình ở bốn chỗ khác nhau, tùy nghiên cứu bắt nguồn từ đâu:

| Điểm khởi đầu | Bắt đầu ở hoạt động | Khi nào |
|---|---|---|
| Từ vấn đề | 1 | Quan sát thấy một vấn đề thực tế |
| Từ mục tiêu | 2 | Có sẵn nhu cầu về một giải pháp tốt hơn |
| Từ thiết kế | 3 | Đã có sẵn một tạo tác, đi tìm vấn đề nó giải được |
| Từ bối cảnh/khách hàng | 4 | Một giải pháp đã tồn tại trong thực tế, nghiên cứu ngược lại |

Và có **vòng phản hồi**: từ hoạt động 5 (Đánh giá) quay lại hoạt động 3 (Thiết kế) khi kết quả cho thấy thiết kế cần sửa. Trình tự sáu bước là **trình tự trình bày**, không bắt buộc là trình tự thực hiện.

## 1.4 Vài khái niệm phụ trợ của design science

**(a) Bốn loại tạo tác** — để trả lời câu "kết quả nghiên cứu của em là cái gì":

| Loại | Nghĩa | Ví dụ trong ngữ cảnh phần mềm |
|---|---|---|
| Construct | Khái niệm, thuật ngữ | Từ điển miền, mô hình khái niệm |
| Model | Mô hình biểu diễn quan hệ | Mô hình kiến trúc, mô hình dữ liệu |
| Method | Quy trình, phương pháp | Cơ chế/quy trình xử lý được đề xuất |
| Instantiation | Hiện thực chạy được | Hệ thống phần mềm đã triển khai |

**(b) Các nhóm phương pháp đánh giá** trong design science: quan sát (observational), phân tích (analytical), thực nghiệm (experimental), kiểm thử (testing), mô tả (descriptive).

---

# PHẦN 2 — CHẮT LỌC: LẤY GÌ, BỎ GÌ, SỬA GÌ

## 2.1 Bảng chắt lọc

| Thành phần DSRM | Quyết định | Lý do (chiếu vào ĐATN của bạn) |
|---|---|---|
| **Sáu hoạt động** | 🟡 **THAM KHẢO** | Dùng để tự kiểm tra có đủ vấn đề, mục tiêu, thiết kế, hiện thực và đánh giá; **không ánh xạ máy móc sáu hoạt động vào chương báo cáo** |
| **Nguyên tắc "mục tiêu và bằng chứng đặt trước — đo lại sau"** | 🔴 **LẤY** | Giữ mạch mục tiêu–bằng chứng; chỉ chốt ngưỡng số khi có căn cứ, còn mục tiêu chức năng có thể kiểm chứng bằng test/use case |
| **Tách trình diễn khỏi đánh giá** | 🔴 **LẤY về ý nghĩa** | Có thể trình bày ở hai mục hoặc cùng một chương tùy mẫu báo cáo; điều quan trọng là không lấy ảnh demo làm toàn bộ bằng chứng đánh giá |
| **Vòng phản hồi 5 → 3** | 🟡 **LẤY gọn** | Ghi ADR khi một **quyết định kiến trúc quan trọng** thay đổi; không tạo bản ghi cho mọi chỉnh sửa nhỏ |
| **Bốn điểm khởi đầu** | 🟡 **LẤY 1, biết 3** | Đề tài của bạn khởi đầu từ vấn đề → chỉ dùng điểm thứ nhất. Ba cái kia chỉ cần biết để trả lời nếu hội đồng hỏi, **không đưa vào báo cáo** |
| **Bốn loại tạo tác** | 🟡 **LẤY, dùng một lần** | Chỉ dùng ở mục "Đóng góp" để nói rõ kết quả nghiên cứu gồm những gì (mô hình kiến trúc + hệ thống chạy được), tránh việc đóng góp chỉ được mô tả mơ hồ |
| **Năm nhóm phương pháp đánh giá** | 🟡 **LẤY 3, BỎ 2** | Lấy: thực nghiệm, kiểm thử, phân tích. Bỏ **quan sát** (cần người dùng thật — không có) và **mô tả** (chỉ lập luận suông, quá yếu để làm chỗ dựa chính) |
| **Hoạt động 6 — Công bố** | 🔴 **SỬA** | DSRM hiểu là công bố khoa học. Với ĐATN, đọc là: quyển báo cáo + buổi bảo vệ. Không cố ép thành bài báo |
| **Bộ ba chu trình của Hevner** (relevance / rigor / design cycle) | ⚪ **BỎ** | Là lý thuyết nền của design science, thêm một tầng trừu tượng mà không thêm việc gì cụ thể phải làm. Ở mức ĐATN đại học, đưa vào chỉ làm loãng |
| **Phân loại mức đóng góp tri thức** (invention / improvement / exaptation / routine design) | ⚪ **BỎ khỏi báo cáo, GIỮ trong đầu** | Hữu ích để tự kiểm tra mình đang phát biểu đóng góp có quá lời không, nhưng viết vào báo cáo thì hàn lâm quá mức cần thiết |
| **Tranh luận design science vs behavioral science** | ⚪ **BỎ** | Không liên quan tới việc phải làm |

## 2.2 Kết quả sau chắt lọc

Từ DSRM giữ lại **bốn nguyên tắc thực dụng**:

1. Phát biểu vấn đề và mục tiêu trước khi lựa chọn giải pháp.
2. Mỗi mục tiêu quan trọng phải có cách kiểm chứng tương ứng; ngưỡng số chưa có căn cứ được chốt sau phép đo thăm dò.
3. Phân biệt hệ thống “chạy được” với bằng chứng “đáp ứng đến mức nào”.
4. Ghi lại thay đổi kiến trúc quan trọng và đánh giá bằng kiểm thử, thực nghiệm hoặc phân tích phù hợp.

Bốn nguyên tắc này đủ cho một ĐATN đại học; các khái niệm còn lại chỉ dùng khi thực sự giúp giải thích cách làm.

## 2.3 ⚠️ Tự phản biện về mức độ khai báo DSRM trong báo cáo

Rủi ro nếu để DSRM chiếm spotlight — vẽ sơ đồ sáu bước tiếng Anh, đặt tên chương theo nó: hội đồng PTIT có thể thấy bày vẽ, hoặc hỏi sâu vào phương pháp luận. Rủi ro ngược lại nếu giấu hoàn toàn: mục "Phương pháp nghiên cứu" lại rỗng như cũ.

**Điểm cân bằng:** nếu cần, nhắc DSRM trong một đoạn ngắn ở mục phương pháp; không vẽ quy trình riêng, không đặt tên chương theo DSRM. Nếu mẫu ĐATN hiện hành không yêu cầu tên phương pháp, chỉ cần mô tả trực tiếp các hoạt động khảo sát, phân tích, thiết kế, hiện thực và đánh giá.

---

# PHẦN 3 — GHÉP VỚI CHUẨN CHƯƠNG 1 CỦA ĐATN VIỆT NAM

## 3.1 Chuẩn Chương 1 ở các trường kỹ thuật Việt Nam

Cấu trúc lặp lại ở PTIT, HUST và các trường kỹ thuật khác:

- Đặt vấn đề / Tổng quan về đề tài
- Lý do chọn đề tài / Tính cấp thiết
- Mục đích (mục tiêu) nghiên cứu và ý nghĩa của đề tài
- Đối tượng và phạm vi nghiên cứu
- Phương pháp nghiên cứu
- Bố cục đồ án
- Kết luận chương

## 3.2 Đối chiếu: DSRM phủ được gì, thiếu gì

| Mục Chương 1 (chuẩn VN) | DSRM có phủ không | Xử lý |
|---|---|---|
| Đặt vấn đề / Tổng quan | Hoạt động 1 | Dùng thẳng |
| Tính cấp thiết | Hoạt động 1 (phần "động cơ") | Dùng thẳng |
| Mục tiêu nghiên cứu | Hoạt động 2 | Dùng thẳng — và đây là chỗ DSRM mạnh nhất, vì nó ép mục tiêu phải đo được |
| **Đối tượng nghiên cứu** | ❌ Không có khái niệm này | **Bổ sung** từ chuẩn VN |
| Phạm vi nghiên cứu | Gián tiếp | **Bổ sung** cách chia phạm vi |
| Phương pháp nghiên cứu | DSRM chỉ là khung tham khảo | Mô tả trực tiếp các phương pháp thật sự đã dùng; có thể dẫn DSRM một lần để giải thích mạch tạo tác–đánh giá |
| **Ý nghĩa khoa học và thực tiễn** | Một phần ở hoạt động 6 | **Bổ sung** — chuẩn VN đòi tách riêng ý nghĩa khoa học và ý nghĩa thực tiễn |
| **Bố cục đồ án** | ❌ Không có | **Bổ sung** |
| **Câu hỏi nghiên cứu** | Không bắt buộc trong DSRM | 🟡 **Bổ sung tự nguyện** — không phải mục bắt buộc ở ĐATN VN, nhưng có thì mục Kết luận có chỗ để quy chiếu về |

## 3.3 Ràng buộc riêng của nhóm bạn ảnh hưởng tới Tầng A

Ba dữ kiện bối cảnh, mỗi cái đẻ ra một quy tắc:

| Dữ kiện | Quy tắc phát sinh cho Tầng A |
|---|---|
| **Chấm điểm cá nhân, một quyển báo cáo, hỏi theo phần** | Không chia thành nhiều câu hỏi nghiên cứu song song (dễ thành ba đồ án dán lại). Dùng **một trục nghiên cứu duy nhất**, và bổ sung một bảng **phân công thực hiện** để mỗi phần truy được về người làm → Phiếu A9 |
| **Bạn là người quyết định thiết kế toàn hệ thống** | Trục nghiên cứu đặt ở tầng **kiến trúc và các cơ chế bảo đảm tính đúng đắn**. Mobile là một client của cùng hệ thống và không cần thành nhánh nghiên cứu riêng; tuy vậy, check-in trực tuyến vẫn là use case nghiệp vụ cốt lõi cần API, idempotency và kiểm thử cạnh tranh ở backend |
| **Trợ lý chẩn đoán dùng hướng Drain + LLM API** | Mục tiêu viết ở mức vấn đề chẩn đoán. Không tuyên bố huấn luyện mô hình; Drain là bước gom template, còn giá trị triển khai nằm ở logging có cấu trúc, tạo context, ranh giới quyền chỉ đọc và đánh giá trên ca lỗi đã biết |

---

# PHẦN 4 — KHUNG RỖNG

Chín phiếu. Mỗi phiếu: **mục đích → quy tắc điền → phép thử → đi vào đâu**. Điền hết chín phiếu là có Chương 1 và có ràng buộc cho Chương Đánh giá.

Thứ tự điền đề xuất: A1 → A2 → A3 → A4 → A5 → A6 → A8 → A7 → A9. *(A7 điền gần cuối vì nó tổng hợp các phiếu trước; A9 điền sau cùng vì phụ thuộc bố cục thật.)*

---

### 🔴 PHIẾU A1 — Bối cảnh và tính cấp thiết

**Mục đích:** làm cho người đọc thấy vấn đề này có thật và đáng giải, trước khi nghe giải pháp.

**Quy tắc điền:**
- Đi từ bối cảnh rộng → thu hẹp dần về đúng bài toán của đồ án. Không mở đầu bằng "Ngày nay công nghệ thông tin phát triển mạnh mẽ"
- Mỗi khẳng định về thực trạng phải có chỗ dựa: số liệu, quan sát có bằng chứng lưu lại, hoặc tài liệu — không viết theo cảm nhận
- Kết đoạn bằng một câu chuyển tiếp thẳng sang Phiếu A2

**Phép thử:** xóa hết tên công nghệ khỏi đoạn văn — nội dung còn đứng vững không? Nếu sụp, tức là đang bán công nghệ chứ chưa nêu được vấn đề.

### ⚠️ Trường hợp đề tài không mới — cách trình bày đúng

Bài toán bán vé trong hệ phân tán **đã được giải quyết trong công nghiệp**, và các mẫu thiết kế liên quan đều đã công bố. Viết A1 theo lối “hiện chưa có giải pháp nào” là sai sự thật. Ngược lại, đồ án đại học cũng không bắt buộc phải chứng minh một “khoảng trống nghiên cứu toàn cầu” nếu chưa thực hiện tổng quan tài liệu đủ rộng.

**Bốn nước đi thay thế** — tập trung vào bài toán kỹ thuật và bằng chứng:

| Nước | Nội dung | Điều KHÔNG được viết |
|---|---|---|
| **1. Vấn đề nghiệp vụ có thật và có hậu quả** | Chứng minh bằng quan sát có bằng chứng ở B1: giữ chỗ, hết vé, thanh toán, phát hành, check-in | *“Ngày nay công nghệ thông tin phát triển mạnh mẽ…”* |
| **2. Vì sao kiến trúc phân tán làm bài toán khó hơn** | Nêu lỗi từng phần, yêu cầu/thông điệp lặp, dữ liệu thuộc nhiều chủ sở hữu và các bất biến cần giữ | *“Chưa có hệ thống nào giải quyết được vấn đề này”* |
| **3. Vì sao mô tả kiến trúc là chưa đủ** | Cần kiểm chứng bất biến, hiệu năng, phục hồi và khả năng chẩn đoán trong cấu hình được công bố | Dùng thiếu sót của một repository làm “khoảng trống nghiên cứu” |
| **4. Đồ án tạo ra và đánh giá gì** | Thiết kế ranh giới/dữ liệu, phối hợp các mẫu đã biết, hiện thực hệ thống và đánh giá bằng test/thực nghiệm phù hợp | *“Lần đầu tiên”, “vượt trội”, “chưa ai đo”* khi chưa có tổng quan chứng minh |

**Điểm cần hiểu rõ:** ở bậc đại học, giá trị nằm ở độ sâu, tính nhất quán giữa vấn đề–thiết kế–mã nguồn và chất lượng bằng chứng. Đề tài quen thuộc không phải điểm yếu cần che và cũng không cần biến lịch sử phát triển mã thành động cơ nghiên cứu.

**Ô trống:**
```
Nước 1 — Vấn đề nghiệp vụ có thật:
  Bối cảnh rộng: ____________________________________________
  Thu hẹp về vòng đời vé: ___________________________________
  Quan sát có bằng chứng từ B1: _____________________________
Nước 2 — Thách thức khi phân tán:
  Bất biến/trạng thái liên thuộc: ____________________________
  Điều kiện lỗi hoặc cạnh tranh: _____________________________
Nước 3 — Bằng chứng cần có:
  Bất biến/thuộc tính cần kiểm chứng: ________________________
  Bằng chứng vận hành cần thu: _______________________________
Nước 4 — Giá trị của đồ án:
  Tạo tác được thiết kế/xây dựng: ____________________________
  Phạm vi và cách đánh giá: _________________________________
```

**Đi vào:** Chương 1 — Đặt vấn đề / Tính cấp thiết

---

### 🔴 PHIẾU A2 — Phát biểu vấn đề

**Mục đích:** cô đọng vấn đề thành một phát biểu duy nhất, đủ hẹp để giải được trong 19 tuần.

**Quy tắc điền:**
- Không quá 150 từ
- **Không được chứa tên công nghệ nào** — nếu có, bạn đang phát biểu giải pháp
- Phải nêu được: ai chịu thiệt, thiệt gì, trong hoàn cảnh nào
- Nếu vấn đề quá to, chẻ nhỏ đến khi từng mảnh có thể "bắt" được bằng một cơ chế cụ thể (DSRM hoạt động 1 khuyến nghị đúng điều này)

**Phép thử:** đưa đoạn này cho một người không học CNTT đọc — họ có hiểu ai đang gặp rắc rối gì không?

**Ô trống:**
```
Chủ thể chịu ảnh hưởng: ______________________________________
Hệ quả họ gánh: ______________________________________________
Hoàn cảnh phát sinh: _________________________________________
Nguyên nhân kỹ thuật gốc: ____________________________________
→ Phát biểu vấn đề (≤150 từ): ________________________________
```

**Đi vào:** Chương 1 — Đặt vấn đề

---

### 🔴 PHIẾU A3 — Mục tiêu nghiên cứu

**Mục đích:** xác định rõ hệ thống cần đạt điều gì và phần đánh giá sau này phải đưa ra bằng chứng gì.

**Quy tắc điền:**
- Chia hai tầng: **mục tiêu tổng quát** (1 câu) và **mục tiêu cụ thể** (3–5 gạch đầu dòng)
- Mỗi mục tiêu cụ thể phải kèm **cách biết là đã đạt**. Chưa cần con số chính xác ở giai đoạn này (con số chốt ở Tầng B, bước lập kịch bản chất lượng), nhưng phải nói rõ **sẽ đo bằng gì**
- Mục tiêu liên quan tới AI viết ở mức lớp vấn đề, **không nhắc tên kỹ thuật**
- Không đặt mục tiêu cần điều kiện mình không có (người dùng thật, hạ tầng lớn)

**Phép thử:** với mỗi mục tiêu, hỏi *"cuối kỳ tôi lấy gì ra để chứng minh cái này đạt?"*. Không trả lời được → viết lại.

**Ô trống:**
```
Mục tiêu tổng quát: __________________________________________

Mục tiêu cụ thể:
 1. ____________________  | Biết là đạt bằng: ___________________
 2. ____________________  | Biết là đạt bằng: ___________________
 3. ____________________  | Biết là đạt bằng: ___________________
 4. ____________________  | Biết là đạt bằng: ___________________
```

**Đi vào:** phần Mục tiêu nghiên cứu · **ràng buộc lên phần Đánh giá**

---

### 🟡 PHIẾU A4 — Câu hỏi nghiên cứu

**Mục đích:** cho phần Kết luận một chỗ để quy chiếu về. Đây là mục tùy chọn; chỉ dùng nếu câu hỏi giúp báo cáo rõ hơn thay vì lặp lại mục tiêu.

**Quy tắc điền:**
- **Một trục duy nhất**: một câu hỏi trung tâm, tối đa hai câu hỏi phụ. Không chia theo đầu người
- Câu hỏi phụ phải phục vụ câu trung tâm, không đứng độc lập
- Không chứa tên công nghệ
- Ưu tiên dạng *"ở mức độ nào"* / *"với chi phí gì"* hơn dạng *"có thể hay không"* — dạng sau chỉ trả lời được bằng có/không, không tạo ra kết quả đo được

**Phép thử:**
1. Có tên công nghệ trong câu không? → có thì viết lại
2. Trả lời câu này cần **bằng chứng** gì? → nếu chỉ cần mô tả thì viết lại
3. Với dữ liệu và hạ tầng nhóm thực sự có, thu thập được bằng chứng đó không?
4. Ở Kết luận, có viết được một đoạn trả lời thẳng, dẫn chiếu tới kết quả đánh giá không?

**Ô trống:**
```
Câu hỏi trung tâm: ___________________________________________
 Bằng chứng cần có: __________________________________________

Câu hỏi phụ 1: _______________________________________________
 Bằng chứng cần có: __________________________________________
 Phục vụ câu trung tâm ở chỗ: ________________________________

Câu hỏi phụ 2: _______________________________________________
 Bằng chứng cần có: __________________________________________
 Phục vụ câu trung tâm ở chỗ: ________________________________
```

**Đi vào:** phần Mục tiêu/Câu hỏi nghiên cứu nếu mẫu báo cáo phù hợp · **quy chiếu ngược ở Kết luận**

---

### 🔴 PHIẾU A5 — Đối tượng nghiên cứu

**Mục đích:** chuẩn ĐATN Việt Nam đòi mục này, DSRM không có. Nó trả lời *"em nghiên cứu cái gì"* — khác với *"em làm ra cái gì"*.

**Quy tắc điền:**
- Đối tượng nghiên cứu là **hiện tượng / cơ chế / thuộc tính** được nghiên cứu, không phải sản phẩm
- Sản phẩm là *phương tiện* để nghiên cứu đối tượng đó, ghi riêng
- Phân biệt rõ với Phiếu A6: đối tượng = *nghiên cứu cái gì*; phạm vi = *nghiên cứu đến đâu*

**Phép thử:** nếu ô "đối tượng nghiên cứu" điền tên hệ thống của bạn thì sai — đó là sản phẩm, không phải đối tượng.

**Ô trống:**
```
Đối tượng nghiên cứu: ________________________________________
Phương tiện nghiên cứu (sản phẩm tạo ra): ____________________
Khách thể (bối cảnh áp dụng): ________________________________
```

**Đi vào:** Chương 1 — Đối tượng và phạm vi nghiên cứu

---

### 🔴 PHIẾU A6 — Phạm vi

**Mục đích:** chống hai lỗi cùng lúc — làm dàn trải không phần nào đủ sâu, và bị hỏi "sao em không làm X".

**Quy tắc điền — ba lớp phạm vi:**

| Vòng | Nghĩa | Kiểm chứng ở mức nào |
|---|---|---|
| **Vòng 1 — Phạm vi nghiên cứu** | Đào sâu, có đo đạc, có phân tích đánh đổi | Thực nghiệm + phân tích |
| **Vòng 2 — Phạm vi sản phẩm** | Làm chạy được để vòng 1 có ngữ cảnh hoạt động | Chỉ kiểm thử chức năng |
| **Vòng 3 — Ngoài phạm vi** | Nêu tên **kèm lý do**, không làm | Không |

- Chỉ đưa vào lớp nghiên cứu những điểm gắn trực tiếp với trục nhất quán/độ tin cậy hoặc trợ lý chẩn đoán và có cách kiểm chứng rõ.
- Nêu các mục ngoài phạm vi quan trọng để tránh hiểu nhầm; không cần liệt kê mọi tính năng không làm.
- Ghi kèm điều kiện hạ tầng/dữ liệu vì chúng giới hạn khả năng suy rộng kết quả.
- Mobile được tính trong phạm vi sản phẩm và phân tích nghiệp vụ chung. Check-in trực tuyến thuộc lớp nghiên cứu khi dùng để kiểm chứng bất biến “một vé chỉ check-in thành công một lần”; **check-in offline nằm ngoài phạm vi**.
- CI/CD là công việc hỗ trợ nếu có điều kiện, không nằm trong phạm vi nghiên cứu cốt lõi.

**Phép thử:** với mỗi mục ở lớp nghiên cứu, phải chỉ ra được vấn đề nguồn và bằng chứng sẽ thu. Nếu hai mục đo cùng một điều, gộp lại.

**Ô trống:**
```
VÒNG 1 — Phạm vi nghiên cứu:
 1. ______________  2. ______________  3. ______________
 4. ______________  5. ______________

VÒNG 2 — Phạm vi sản phẩm:
 _____________________________________________________________

VÒNG 3 — Ngoài phạm vi (mục | lý do không làm):
 ______________ | ______________
 ______________ | ______________

Giới hạn hạ tầng: 2 EC2 m7i-flex.large (2 vCPU/8 GiB mỗi máy),
  hai tài khoản AWS riêng; cách bố trí chưa chốt — xem B5.5 mục 3.3
Giới hạn thời gian: __________________________________________
```

**Đi vào:** Chương 1 — Đối tượng và phạm vi nghiên cứu

---

### 🔴 PHIẾU A7 — Phương pháp nghiên cứu ✍️ *(phiếu điền mẫu)*

**Mục đích:** khai báo khung phương pháp, nguồn tri thức và chiến lược kiểm chứng. Đây là mục mà toàn bộ Tầng A phục vụ.

**Quy tắc điền:**
- Viết ngắn theo ba ý: cách xác định yêu cầu; cách thiết kế/hiện thực; cách kiểm chứng. Không cần sơ đồ phương pháp riêng.
- Nếu dùng tên DSRM, trích dẫn đúng một lần và chỉ tuyên bố dùng nó như khung tham khảo.
- Nguồn tri thức phải khai báo trung thực, kèm giới hạn của từng nguồn
- **Tuyệt đối không tuyên bố quá mức** về phương pháp: nếu một phương pháp đòi điều kiện mình không có (chuyên gia nghiệp vụ, nhóm đánh giá độc lập, người dùng thật) thì ghi rõ là "áp dụng có điều chỉnh" hoặc "tham chiếu cấu trúc"

**Phép thử:** đọc lại và gạch chân mọi động từ chỉ việc đã làm. Với mỗi cái, hỏi *"cuối kỳ tôi có bằng chứng cho việc này không?"*

**Vì sao mình chọn đúng phiếu này để điền mẫu:** nội dung của A7 do chính Tầng A quyết định, không phụ thuộc các lựa chọn thiết kế của bạn — nên điền sẵn được mà không lấn sang phần việc của bạn. Các phiếu còn lại (nhất là A3, A4, A6) phụ thuộc vào kết quả khảo sát ở Tầng B, mình cố tình để trống.

---

> **BẢN ĐIỀN MẪU — dùng được ngay, chỉ cần chỉnh cho khớp nội dung thật**
>
> *Đồ án sử dụng cách tiếp cận nghiên cứu tạo tác, tham khảo Design Science Research Methodology của Peffers và cộng sự (2007): xuất phát từ vấn đề, xác định mục tiêu và bằng chứng cần thu, sau đó thiết kế, hiện thực và đánh giá hệ thống. Khung này được dùng để giữ liên kết giữa mục tiêu và kết quả, không dùng để quyết định máy móc cấu trúc chương báo cáo.*
>
> *Do đề tài không có tổ chức thụ hưởng cụ thể, tri thức nghiệp vụ được thu thập từ ba nguồn. Thứ nhất là phân tích đối sánh các hệ thống bán vé đang vận hành trong và ngoài nước; nguồn này cho phép quan sát hành vi hệ thống ở phía người dùng, song không cho biết cấu trúc bên trong, nên các nhận định về cơ chế được trình bày dưới dạng suy luận. Thứ hai là nghiên cứu tài liệu chuyên ngành về hệ thống phân tán và các mẫu thiết kế liên quan. Thứ ba là kinh nghiệm thực tiễn của thành viên nhóm trong quy trình xử lý sự cố phần mềm tại doanh nghiệp; nguồn này giới hạn trong một bối cảnh tổ chức cụ thể và không được khái quát hóa.*
>
> *Kết quả được kiểm chứng bằng kiểm thử chức năng cho các use case chính, thực nghiệm có cấu hình công bố cho các bất biến/thuộc tính chất lượng ưu tiên, và phân tích một số kịch bản lỗi quan trọng. Với trợ lý chẩn đoán, nhóm dùng tập log và ca lỗi đã biết để kiểm tra bước gom template và mức hữu ích của tư vấn. Các kết quả không đạt được báo cáo kèm điều kiện thử và phân tích nguyên nhân.*

---

**Ô trống cho các chỗ cần chỉnh:**
```
Ba nguồn tri thức có đúng ba nguồn trên không, hay cần thêm/bớt: ____
Ba mức kiểm chứng có đúng không, hay chỉ làm được hai: ______________
Câu cuối (báo cáo kết quả không đạt nguyên trạng) có giữ không: _____
```

**Đi vào:** mục Phương pháp nghiên cứu · **ràng buộc lên phần Đánh giá**

---

### 🔴 PHIẾU A8 — Ý nghĩa và đóng góp dự kiến

**Mục đích:** trả lời "kết quả nghiên cứu là cái gì" mà không nói quá.

**Quy tắc điền:**
- Tách hai phần theo chuẩn VN: **ý nghĩa khoa học** và **ý nghĩa thực tiễn**
- Dùng phân loại tạo tác ở mục 1.4 để nói rõ kết quả gồm những loại nào (thường là: mô hình kiến trúc + hệ thống chạy được)
- **Không dùng các từ "lần đầu tiên", "chưa từng có", "vượt trội"** trừ khi có trích dẫn chứng minh
- Với các mẫu thiết kế đã nổi tiếng, không viết "đồ án đề xuất" — viết "đồ án lựa chọn, phối hợp và điều chỉnh"

**Phép thử về mức phát biểu:** với mỗi đóng góp, hỏi *"nếu người phản biện chỉ ra rằng đã có người làm việc này, phát biểu của tôi có sụp không?"*. Nếu sụp → hạ mức phát biểu ngay từ bây giờ.

**Ô trống:**
```
Ý nghĩa khoa học: ____________________________________________
Ý nghĩa thực tiễn: ___________________________________________

Kết quả nghiên cứu gồm (theo loại tạo tác):
 □ Mô hình:      ______________________________________________
 □ Phương pháp:  ______________________________________________
 □ Hệ thống:     ______________________________________________

Điều KHÔNG tuyên bố (để tự nhắc): ____________________________
```

**Đi vào:** Chương 1 — Ý nghĩa của đề tài · **Đối chiếu lại ở Kết luận**

---

### 🔴 PHIẾU A9 — Bố cục đồ án và phân công thực hiện

**Mục đích:** chuẩn bị mục bố cục theo mẫu ĐATN chính thức và làm rõ phân công trong một quyển báo cáo chung.

**Quy tắc điền:**
- Chỉ chốt số chương sau khi nhận mẫu/ý kiến chính thức cho ĐATN; tài liệu môn Thực tập cơ sở chỉ là tham khảo phong cách.
- Bố cục: mỗi chương một dòng, nói rõ nội dung và đầu ra chính; không cần gắn số hoạt động DSRM.
- Phân công: một bảng nhỏ **phần việc → người thực hiện**, đặt ở Lời mở đầu hoặc cuối Chương 1
- Vì bạn quyết định thiết kế toàn hệ thống, bảng cần tách hai cột khác nhau: **người quyết định thiết kế** và **người hiện thực hóa** — hai cột này không trùng nhau, và đó chính là điều cần thể hiện

**Phép thử:** khi hội đồng hỏi riêng một thành viên về phần của họ, người đó tra bảng này có biết mình phải trả lời phần nào không?

**Ô trống:**
```
BỐ CỤC (số chương theo mẫu ĐATN được xác nhận):
 Chương/Mục: ________________ | Nội dung/đầu ra: ______________
 Chương/Mục: ________________ | Nội dung/đầu ra: ______________
 Chương/Mục: ________________ | Nội dung/đầu ra: ______________

PHÂN CÔNG (phần việc | quyết định thiết kế | hiện thực hóa):
 ______________ | ______________ | ______________
 ______________ | ______________ | ______________
 ______________ | ______________ | ______________
```

**Đi vào:** mục Bố cục đồ án/Lời mở đầu theo mẫu chính thức

---

# PHẦN 5 — TẦNG A RÀNG BUỘC GÌ LÊN TẦNG B VÀ C

Đây là mặt tiếp giáp giữa ba tầng. Tầng B và C **không được mâu thuẫn** với các ràng buộc dưới đây:

| Ràng buộc từ Tầng A | Tầng B phải làm gì | Tầng C phải có gì |
|---|---|---|
| Mục tiêu quan trọng có cách kiểm chứng | Trước khi hiện thực từng cơ chế cốt lõi, xác định bằng chứng sẽ thu; chỉ đặt ngưỡng số sau phép đo thăm dò nếu chưa có cơ sở | Mẫu kịch bản chất lượng gọn |
| Trình diễn ≠ Đánh giá | Phần kiểm chứng phải phân biệt ảnh/demo với test và số liệu đánh giá, không bắt buộc tách thành chương riêng | — |
| Vòng phản hồi được ghi nhận | Chỉ thay đổi quyết định kiến trúc quan trọng mới cần ADR mới/trạng thái thay thế | Mẫu ADR gọn |
| Chọn đúng phương pháp đánh giá | Dùng kiểm thử, thực nghiệm hoặc phân tích theo từng mục tiêu; không bắt buộc mọi mục tiêu phải có đủ cả ba | Mẫu báo cáo thí nghiệm |
| Nội dung AI viết ở mức lớp vấn đề | Ghi rõ pipeline logging–Drain–context–LLM và đánh giá trong phạm vi đã chọn; không tuyên bố huấn luyện mô hình | Mẫu ADR/thí nghiệm dùng chung |
| Mobile là client của cùng nghiệp vụ | Không tạo nhánh nghiên cứu mobile riêng; vẫn đặc tả và kiểm thử use case check-in trực tuyến ở backend | Use case, sequence và test tương ứng |
| Không tuyên bố quá mức về phương pháp | Mọi kỹ thuật áp dụng có điều chỉnh phải ghi rõ mức áp dụng | Quy ước diễn đạt: "tham chiếu cấu trúc của…", "áp dụng trong phạm vi nhóm" |

---

## Nhật ký sửa đổi

| Ngày | Sửa gì | Lý do |
|---|---|---|
| 2026-08-07 | Bản đầu | — |
| 2026-08-08 | Giản lược DSRM; bỏ ánh xạ chương máy móc; cập nhật mobile, AI và cách đánh giá | Phù hợp phạm vi ĐATN đại học và các quyết định mới |

---

## Tài liệu tham chiếu

- Peffers, K., Tuunanen, T., Rothenberger, M. A., & Chatterjee, S. (2007). *A Design Science Research Methodology for Information Systems Research.* Journal of Management Information Systems, 24(3), 45–77.
- Hevner, A. R., March, S. T., Park, J., & Ram, S. (2004). *Design Science in Information Systems Research.* MIS Quarterly, 28(1), 75–105. *(nguồn cho phân loại tạo tác và các nhóm phương pháp đánh giá)*
