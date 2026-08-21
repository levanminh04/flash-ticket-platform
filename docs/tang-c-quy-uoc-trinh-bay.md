# TẦNG C — KHUNG QUY ƯỚC TRÌNH BÀY
## ĐATN FlashTicket · PTIT · Nộp 14/12/2026

---

## 0. Tài liệu này là gì

**Trả lời:** vẽ gì bằng ký hiệu nào, ghi lại bằng mẫu nào, đặt tên ra sao, và diễn đạt thế nào để không tuyên bố quá mức.

**Năm phần:** Giải phẫu → Chắt lọc → Bộ quy ước thống nhất → Bộ mẫu → **Kiểm tra chéo ba tầng** (phần khép vòng, chỉ có ở tầng cuối).

**Không chứa:** nội dung nghiệp vụ, quyết định kiến trúc, trình tự công việc. Đó là A và B.

Ký hiệu: 🔴 bắt buộc · 🟡 nên có · ⚪ cắt được

---

# PHẦN 1 — GIẢI PHẪU

## 1.1 arc42

### Tư tưởng chính
Một khuôn mẫu tài liệu kiến trúc gồm 12 mục cố định, sắp theo trình tự "hiểu bối cảnh → ra quyết định → mô tả chi tiết → nhận diện rủi ro". Mục đích là để người đọc bất kỳ tài liệu kiến trúc nào cũng biết trước phải tìm thông tin ở đâu.

### Mười hai mục

| # | Mục | Nội dung |
|---|---|---|
| 1 | Giới thiệu và mục tiêu | Yêu cầu cốt lõi, mục tiêu chất lượng, các bên liên quan |
| 2 | Ràng buộc kiến trúc | Giới hạn không thay đổi được: kỹ thuật, tổ chức, quy ước |
| 3 | Phạm vi và ngữ cảnh | Ranh giới hệ thống — bối cảnh nghiệp vụ và bối cảnh kỹ thuật |
| 4 | Chiến lược giải pháp | Các quyết định nền tảng và lý do, ở mức tóm lược |
| 5 | Khung nhìn khối xây dựng | Phân rã tĩnh thành các khối, theo nhiều mức |
| 6 | Khung nhìn thời gian chạy | Các kịch bản vận hành quan trọng |
| 7 | Khung nhìn triển khai | Ánh xạ lên hạ tầng vật lý |
| 8 | Khái niệm xuyên suốt | Bảo mật, nhật ký, xử lý lỗi, giao dịch… |
| 9 | Quyết định kiến trúc | Các quyết định quan trọng kèm lý do |
| 10 | Yêu cầu chất lượng | Cây chất lượng và các kịch bản chất lượng |
| 11 | Rủi ro và nợ kỹ thuật | Điểm yếu đã biết |
| 12 | Bảng chú giải | Thuật ngữ dùng thống nhất |

### Kỹ thuật kèm theo
- **Mẫu hộp đen / hộp trắng** cho mô tả khối xây dựng: hộp đen mô tả *khối làm gì, giao diện ra sao*; hộp trắng mô tả *bên trong nó gồm những khối con nào*
- **Phân mức** — mức 1 là toàn hệ thống, mức 2 là bên trong một khối, cứ thế

### ❗ arc42 không phải là
Không phải quy trình (nó không nói làm gì trước làm gì sau), không phải ký hiệu vẽ (nó không quy định vẽ bằng UML hay gì khác).

---

## 1.2 C4 Model

### Tư tưởng chính
Mô tả kiến trúc phần mềm bằng bốn mức trừu tượng lồng nhau, mỗi mức phóng to một hộp của mức trước. Giải quyết đúng một vấn đề: các sơ đồ kiến trúc thường trộn lẫn nhiều mức chi tiết vào một hình, khiến người đọc không biết đang nhìn ở độ phóng nào.

### Bốn mức

| Mức | Tên | Nội dung | Đối tượng đọc |
|---|---|---|---|
| 1 | **System Context** | Hệ thống là một hộp đen; xung quanh là người dùng và hệ thống ngoài | Ai cũng đọc được, kể cả người không kỹ thuật |
| 2 | **Container** | Bên trong hệ thống: các đơn vị chạy được và lưu trữ được — ứng dụng, dịch vụ, CSDL, hàng đợi | Người kỹ thuật |
| 3 | **Component** | Bên trong một container: các nhóm chức năng chính | Lập trình viên |
| 4 | **Code** | Bên trong một component: lớp, quan hệ | Thường bỏ qua hoặc sinh tự động |

### Sơ đồ bổ trợ
System Landscape (nhiều hệ thống), Dynamic (luồng theo thời gian), Deployment (ánh xạ container lên hạ tầng).

### Kỷ luật ghi nhãn — phần giá trị nhất của C4
- Mỗi sơ đồ có **tiêu đề** và **chú giải ký hiệu**
- Mỗi hộp ghi: **tên · loại · công nghệ · mô tả ngắn**
- Mỗi mũi tên ghi: **làm gì · qua giao thức nào**
- Không phụ thuộc bộ ký hiệu cụ thể, nhưng phải nhất quán trong toàn tài liệu

### C4 và UML
C4 là **mô hình phân mức và tập quy ước**, không bắt buộc một bộ ký hiệu đồ họa duy nhất. Có thể dùng hình thức trực quan riêng của C4 hoặc một notation nhất quán khác. Tuy nhiên, “Component” trong C4 không đồng nghĩa với biểu đồ thành phần UML; nếu chọn UML thì gọi đúng tên UML và tuân thủ ký hiệu UML, không trộn tên/khái niệm của hai loại trong cùng sơ đồ.

---

## 1.3 Architecture Decision Record (ADR)

### Tư tưởng chính
Một quyết định kiến trúc quan trọng, một tệp văn bản ngắn, đánh số tuần tự. Khi đảo ngược quyết định đã áp dụng, tạo bản mới và đánh dấu bản cũ là *đã bị thay thế*. Có thể sửa lỗi chính tả hoặc bổ sung liên kết với ghi chú thay đổi; không dùng ADR cho mọi cấu hình/chi tiết code.

### Bộ trường của mẫu gốc (Nygard)
Tiêu đề · Trạng thái · Bối cảnh · Quyết định · Hệ quả.

### Bộ trường mở rộng (biến thể MADR)
Bối cảnh và phát biểu vấn đề · Các yếu tố dẫn dắt quyết định · Các phương án đã cân nhắc · Kết quả quyết định · Ưu nhược từng phương án · Liên kết tới ADR khác.

### Vòng đời trạng thái
`Đề xuất` → `Chấp nhận` → `Không dùng nữa` hoặc `Bị thay thế bởi ADR-XXX`

### Nguyên tắc lịch sử
Số hiệu không tái sử dụng. Không âm thầm viết lại lý do/kết luận của ADR đã chấp nhận; thay đổi quyết định thì tạo ADR thay thế để giữ lịch sử.

---

## 1.4 ISO/IEC/IEEE 42010 — Mô tả kiến trúc

### Tư tưởng chính
Chuẩn quốc tế quy định **một bản mô tả kiến trúc phải chứa gì**, không quy định vẽ bằng gì.

### Mô hình khái niệm

```
Bên liên quan (Stakeholder)
      │ có
      ▼
Mối quan tâm (Concern)
      │ được đóng khung bởi
      ▼
Góc nhìn (Viewpoint) ──quy định cách dựng──► Khung nhìn (View)
                                                    │ gồm
                                                    ▼
                                            Thành phần khung nhìn
```

Cùng với: **Quyết định kiến trúc và lý do** là phần bắt buộc của bản mô tả; **tương ứng (correspondence)** biểu diễn quan hệ giữa các phần tử của bản mô tả.

### Yêu cầu tuân thủ — bản tóm lược
Một bản mô tả kiến trúc tuân thủ chuẩn khi nó: xác định hệ thống đang được mô tả; xác định các bên liên quan có mối quan tâm mang ý nghĩa kiến trúc; xác định các mối quan tâm; gắn mỗi mối quan tâm với bên liên quan tương ứng; đưa vào mọi góc nhìn được sử dụng; và **mỗi mối quan tâm phải được đóng khung bởi ít nhất một góc nhìn**; ghi lại các vấn đề còn tồn đọng.

Bản 2022 bổ sung: nhóm mối quan tâm theo *lăng kính bên liên quan*, khái niệm *khía cạnh kiến trúc*, và mở rộng đối tượng mô tả từ "hệ thống" thành "thực thể quan tâm".

---

## 1.5 Chuẩn trình bày báo cáo ở các trường kỹ thuật Việt Nam

Các quy ước lặp lại ở PTIT, HUST và các trường kỹ thuật khác:

- Dùng **style của trình soạn thảo** (Heading 1 cho tên CHƯƠNG, Heading 2/3 cho mục con) để mục lục sinh tự động
- **Hình:** đánh số theo chương — *Hình 2.5*; chú thích đặt **dưới** hình; hình lấy từ nguồn khác phải ghi nguồn
- **Bảng:** đánh số theo chương — *Bảng 3.2*; chú thích đặt **trên** bảng
- Có **Danh mục hình vẽ**, **Danh mục bảng biểu**, **Danh mục từ viết tắt** ở đầu quyển
- **Trích dẫn** đánh số vuông [1], [2] theo thứ tự xuất hiện, khớp với Tài liệu tham khảo
- Mỗi chương kết bằng một mục **Kết luận chương**

> ⚠️ **Font, cỡ chữ, giãn dòng, lề, cách đánh số trang khác nhau giữa các khoa.** Mình không có bản quy định chính thức của Khoa CNTT PTIT nên **không khẳng định các con số cụ thể**. Việc cần làm: xin file mẫu chính thức của khoa (hoặc một quyển ĐATN đã bảo vệ đạt điểm cao gần đây) và lấy đó làm chuẩn — đây là loại lỗi bị trừ điểm hình thức rất oan.

---

# PHẦN 2 — CHẮT LỌC

## 2.1 arc42

| Thành phần | Quyết định | Lý do |
|---|---|---|
| **12 mục dùng làm nguồn tham khảo nội bộ** | 🟡 TÙY CHỌN | Có thể rà nhanh khi thiếu góc nhìn, nhưng không biến thành checklist bắt buộc hoặc nội dung báo cáo |
| **12 mục dùng làm tên mục trong báo cáo** | 🔴 **BỎ** | Hội đồng PTIT đọc mục lục không thấy "Phân tích thiết kế hệ thống", "Thiết kế cơ sở dữ liệu" sẽ thấy lạ. Tên chương giữ theo chuẩn Việt Nam |
| **Mẫu hộp đen / hộp trắng** | 🟡 LẤY | Cách mô tả từng service rất gọn: hộp đen nói *service làm gì, giao diện gì*; hộp trắng nói *bên trong gồm gì*. Dùng làm mẫu C5 ở Phần 4 |
| **Cơ chế phân mức của arc42** | ⚪ BỎ | Trùng vai trò với bốn mức của C4. Giữ cả hai là hai hệ đánh mức song song, gây rối |
| **Bộ mẫu chi tiết cho từng mục** | ⚪ BỎ | Quá nặng; ta chỉ cần danh sách tự kiểm |

## 2.2 C4 Model

| Thành phần | Quyết định | Lý do |
|---|---|---|
| **Mức 1 — System Context** | 🔴 LẤY, 1 sơ đồ | Sơ đồ quan trọng nhất và cũng dễ nhất. Nó chốt ranh giới trong/ngoài |
| **Mức 2 — Container** | 🔴 LẤY, 1 sơ đồ | Đây là sơ đồ thể hiện kiến trúc phân tán. Không có nó thì không mô tả được đề tài |
| **Mức 3 — Component** | 🟡 LẤY khi cần | Chỉ vẽ service mà cấu trúc bên trong thực sự cần giải thích; không ấn định đúng hai sơ đồ |
| **Mức 4 — Code** | 🔴 **BỎ** | **Trùng hoàn toàn với biểu đồ lớp mức thiết kế đã giữ ở Tầng B.** Vẽ cả hai là làm hai lần cùng một việc bằng hai bộ ký hiệu |
| **Sơ đồ Dynamic của C4** | 🔴 **BỎ** | Trùng với biểu đồ tuần tự UML đã giữ ở Tầng B. Biểu đồ tuần tự mạnh hơn cho luồng thất bại và hội đồng quen hơn |
| **Sơ đồ Deployment của C4** | 🔴 **BỎ** | Dùng biểu đồ triển khai UML thay thế — hội đồng quen ký hiệu UML hơn |
| **System Landscape** | ⚪ BỎ | Chỉ có một hệ thống |
| **Kỷ luật ghi nhãn C4** (tên·loại·công nghệ·mô tả; quan hệ có nhãn) | 🔴 LẤY cho sơ đồ C4 | Với UML chỉ áp dụng nguyên tắc chung: tiêu đề, nhãn quan hệ khi cần và chú giải cho ký hiệu tùy biến; không ép mọi lớp/use case ghi công nghệ hoặc mô tả |

**Kết quả:** giữ C4 mức 1 và mức 2 làm hai góc nhìn chính; mức 3 chỉ thêm khi cần giải thích một service phức tạp. Không đặt chỉ tiêu số lượng hình.

## 2.3 ADR

| Thành phần | Quyết định | Lý do |
|---|---|---|
| **Bộ trường gốc** (tiêu đề, trạng thái, bối cảnh, quyết định, hệ quả) | 🔴 LẤY | Là hạt nhân |
| **Trường "Các phương án đã cân nhắc"** (từ biến thể mở rộng) | 🔴 LẤY | Đây là trường mang giá trị học thuật cao nhất: nó chứng minh có lựa chọn, không phải làm theo quán tính |
| **Hệ quả tách hai chiều: tích cực và tiêu cực** | 🔴 LẤY | Bắt buộc điền cả hai. Ô "hệ quả tiêu cực" để trống là dấu hiệu chưa suy nghĩ đủ |
| **Giữ lịch sử + chuỗi thay thế** | 🔴 LẤY | Áp dụng cho quyết định đã chấp nhận; không cấm sửa lỗi trình bày có ghi chú |
| **Trường "Phục vụ ASR nào"** | 🔴 **THÊM MỚI** | Không có trong mẫu gốc. Thêm để thực thi quy tắc ở Tầng B: mọi quyết định phải truy về một yêu cầu có ý nghĩa kiến trúc |
| **Trường "Kiểm chứng bằng cách nào"** | 🔴 **THÊM MỚI** | Không có trong mẫu gốc. Thêm để thực thi ràng buộc Tầng A: mục tiêu đặt trước, đo lại sau |
| **Mục ưu/nhược chi tiết từng phương án** | 🟡 **RÚT GỌN** | Thay bằng một bảng so sánh ngắn. Viết đầy đủ từng phương án làm ADR dài ra mà không thêm thông tin |
| **Trường "Yếu tố dẫn dắt quyết định"** | ⚪ BỎ | Đã gộp vào trường "Phục vụ ASR nào" |

Hai trường thêm mới là chỗ Tầng C **không chỉ chép mẫu có sẵn** mà chỉnh mẫu cho khớp ràng buộc của hai tầng trên.

## 2.4 ISO/IEC/IEEE 42010

| Thành phần | Quyết định | Lý do |
|---|---|---|
| **Nguyên tắc xem xét mối quan tâm của người đọc** | 🟡 DÙNG NGẦM | Chỉ hỏi “người đọc cần biết gì và phần nào trả lời”; không cần lập bảng ISO riêng nếu mục lục/sơ đồ đã rõ |
| **Trích dẫn chuẩn trong báo cáo** | ⚪ KHÔNG CẦN | Chỉ trích dẫn nếu nhóm thực sự dùng khái niệm của chuẩn; không thêm để làm tài liệu trông hàn lâm |
| **Toàn bộ mô hình khái niệm** (tương ứng, quy tắc tương ứng, khung mô tả kiến trúc, ngôn ngữ mô tả kiến trúc) | ⚪ BỎ | Là bộ máy khái niệm cho hệ thống quy mô lớn nhiều tổ chức. Ở quy mô này chỉ thêm thuật ngữ |
| **Lăng kính bên liên quan, khía cạnh kiến trúc (bản 2022)** | ⚪ BỎ | Cùng lý do |
| **Tuyên bố tuân thủ chuẩn** | 🔴 **BỎ, và cấm tuyên bố** | Tuân thủ đòi đáp ứng đầy đủ các yêu cầu của chuẩn. Nhóm chỉ mượn một nguyên tắc → viết "tham chiếu nguyên tắc của…", **không viết "tuân thủ ISO/IEC/IEEE 42010"** |

## 2.5 Chuẩn trình bày Việt Nam

| Thành phần | Quyết định |
|---|---|
| Dùng style để sinh mục lục tự động | 🔴 LẤY — làm ngay từ file trắng, sửa sau rất mệt |
| Đánh số hình/bảng theo chương, chú thích hình ở dưới, bảng ở trên | 🔴 LẤY |
| Ba danh mục đầu quyển | 🔴 LẤY |
| Trích dẫn số vuông | 🔴 LẤY |
| Kết luận chương ở cuối mỗi chương | 🔴 LẤY |
| Font/cỡ/lề/giãn dòng cụ thể | 🔴 **LẤY TỪ FILE MẪU CỦA KHOA** — không tự đặt |

---

# PHẦN 3 — BỘ QUY ƯỚC THỐNG NHẤT

## 3.1 Phân vai ký hiệu — bảng chống trộn lẫn

Mỗi sơ đồ phải có một mục đích chính. Ưu tiên một loại biểu diễn quen thuộc cho mỗi câu hỏi, nhưng có thể dùng góc nhìn bổ sung nếu nó cung cấp thông tin khác và được gọi đúng tên.

| Cần thể hiện | Dùng | Bộ ký hiệu | Đã loại (và vì sao) |
|---|---|---|---|
| Ranh giới hệ thống, tác nhân, hệ thống ngoài | C4 mức 1 | C4 | — |
| Các service, CSDL, hàng đợi và liên kết giữa chúng | C4 mức 2 | C4 | Biểu đồ thành phần UML — trùng vai |
| Bên trong service phức tạp cần giải thích | C4 mức 3 hoặc biểu đồ lớp/package phù hợp | C4 hoặc UML, tách thành hình riêng | Không vẽ nếu chỉ lặp lại cấu trúc code |
| Chức năng hệ thống theo tác nhân | Biểu đồ use case | UML | — |
| Quy trình nghiệp vụ | Biểu đồ hoạt động có swimlane | UML | BPMN — hội đồng quen UML hơn |
| Luồng tương tác theo thời gian, gồm luồng lỗi | Biểu đồ tuần tự | UML | Sơ đồ Dynamic của C4 — trùng vai |
| Mô hình miền theo từng context | Biểu đồ lớp mức phân tích | UML | — |
| Cấu trúc lớp bên trong service | Biểu đồ lớp mức thiết kế | UML | C4 mức 4 — trùng vai |
| Vòng đời vé, vòng đời đơn hàng | Biểu đồ trạng thái | UML | — |
| Ánh xạ thành phần lên 2 EC2 theo phương án **đã chốt sau này** | Biểu đồ triển khai | UML | Không khóa sẵn service nào nằm trên máy nào |
| Bản đồ Bounded Context | Biểu đồ gói | UML | — |

> Trong Visual Paradigm, chọn loại sơ đồ đúng với tên gọi. Nếu dùng C4, giữ nhất quán khái niệm Person/System/Container/Component; nếu dùng UML Component Diagram, dùng tên và ký hiệu UML. Không ghép hai loại vào cùng một hình mà không có chú giải rõ.

### Quy trình hai bước: nháp bằng mã, bản cuối bằng Visual Paradigm

Vẽ thủ công trên Visual Paradigm là thao tác giao diện — không tự động hóa được, và trở thành nút thắt khi mọi khâu khác đã nhanh lên nhờ công cụ AI. Trong giai đoạn thiết kế còn thay đổi liên tục, mỗi lần đổi ý phải vẽ lại là rất tốn.

| Giai đoạn | Dùng gì | Vì sao |
|---|---|---|
| **Nháp** — thiết kế còn thay đổi | **Sơ đồ dưới dạng mã**: PlantUML cho UML, PlantUML hoặc Structurizr DSL cho C4 | Sinh nhanh, sửa nhanh, đưa được vào git, so sánh được giữa các phiên bản, công cụ AI tạo được. Đặt trong `docs/diagrams/src/` |
| **Bản cuối** — thiết kế đã ổn định | **Visual Paradigm**, chỉ cho các sơ đồ thực sự vào báo cáo | Trình bày chỉn chu, đúng chuẩn ký hiệu |

Bộ ký hiệu và bảng phân vai ở trên **không đổi** — đây chỉ là thay đổi về công cụ dựng hình.

## 3.2 Kỷ luật ghi nhãn theo loại sơ đồ

🔴 Áp dụng chung:
1. Sơ đồ có **tiêu đề** đặt theo quy ước ở 3.3
2. Sơ đồ có **chú giải ký hiệu** nếu dùng nhiều loại hộp/đường
3. Không có phần tử hoặc quan hệ quan trọng khiến người đọc phải đoán.

Riêng sơ đồ C4 mức 1–3: hộp ghi **tên · loại · công nghệ (nếu có ý nghĩa) · mô tả ngắn**; quan hệ ghi mục đích và giao thức khi cần. Với UML, dùng nhãn theo ngữ nghĩa của chính loại biểu đồ; không ép use case, lớp hoặc trạng thái phải ghi công nghệ.

**Phép thử:** đưa sơ đồ cho một người chưa từng nghe về đề tài — họ có đoán được mũi tên này nghĩa là gì không? Không thì thiếu nhãn.

### 3.2.1 Quy tắc ưu tiên cho sơ đồ dùng trong báo cáo

Sơ đồ trong báo cáo phải gần với cách trình bày quen thuộc của đồ án kỹ thuật tại PTIT/HUST, nhưng cụm này **không** được hiểu thành một bộ ký hiệu mới mang tên “phong cách PTIT/HUST”. Thứ tự ưu tiên là:

1. **Đúng nghĩa và đúng loại sơ đồ trước:** sơ đồ UML dùng đúng phần tử và quan hệ UML mà Visual Paradigm hỗ trợ; chọn đúng loại sơ đồ trong Visual Paradigm khi dựng bản cuối. Không dùng màu sắc, hình tự chế hoặc bố cục để thay đổi nghĩa của ký hiệu.
2. **Người đọc báo cáo nhìn thấy tên nghiệp vụ trước:** tên tiếng Việt dễ hiểu là nhãn chính. Mã quản trị/truy vết như `BC-CAND-01`, `B4-OPEN-02` hoặc ID nội bộ chỉ đặt nhỏ ở dòng phụ/chú giải khi thật sự cần đối chiếu; mặc định bỏ khỏi hình dành cho báo cáo. Trạng thái ứng viên phải nói một lần ở tiêu đề, chú thích hoặc chú giải, không lặp chữ `CANDIDATE` trong mọi hộp.
3. **Dùng stereotype và chú giải đúng chỗ:** phân loại bổ sung như `«Cốt lõi»`, `«Hỗ trợ»` hoặc `«Chung»` có thể thể hiện bằng stereotype/chú giải nhất quán. Mọi hình hoặc đường tùy biến ngoài ký pháp chính phải được giải thích; không được khiến người đọc nhầm bounded context với service, package mã nguồn hoặc schema.
4. **Ưu tiên khả năng đọc trên trang báo cáo:** mỗi hình có một thông điệp chính, hướng đọc rõ, hạn chế đường cắt nhau, chữ còn đọc được khi đặt trên một trang A4, màu ít và vẫn phân biệt được khi in thang xám. Phần giải thích bằng văn bản nằm ngay trước hoặc sau hình; hình không phải nơi nhồi toàn bộ mã truy vết.
5. **Mẫu chính thức có quyền cao nhất về hình thức:** font, cỡ chữ, lề, số hình và chú thích tuân theo mẫu ĐATN hiện hành của Khoa CNTT 1 PTIT hoặc chỉ dẫn của giảng viên hướng dẫn. Trong khi chưa có mẫu đó, dùng quy ước Việt Nam tại §2.5 và tham khảo cách trình bày của tài liệu chính thức PTIT/HUST; không tuyên bố đang tuân thủ một mẫu chưa nhận được.

Quy tắc này áp dụng cho cả bản nháp bằng mã và bản dựng lại trên Visual Paradigm. PlantUML được phép khác về nét vẽ mặc định, nhưng phải giữ đúng loại phần tử, nghĩa quan hệ, tên hiển thị và thứ bậc thông tin của bản cuối.

## 3.3 Quy ước đặt tên

| Đối tượng | Quy ước | Ví dụ dạng |
|---|---|---|
| Tệp sơ đồ | `<mã-chương>-<loại>-<chủ đề>` | `c3-c4l2-container-tong-the` |
| Tiêu đề sơ đồ trong báo cáo | `Hình <chương>.<số> — <Loại sơ đồ>: <chủ đề>` | `Hình 3.4 — Biểu đồ tuần tự: xử lý callback thanh toán trùng lặp` |
| Sơ đồ theo context | Tên context xuất hiện trong tiêu đề | `Hình 2.7 — Mô hình miền: ngữ cảnh <tên>` |
| Tệp ADR | `ADR-<3 chữ số>-<chu-de-khong-dau>.md` | `ADR-004-co-che-giu-cho.md` |
| Mã kịch bản chất lượng | `QS-<2 chữ số>` | `QS-01` |
| Mã use case | `UC-<2 chữ số>` | `UC-05` |
| Mã yêu cầu | `FR-<2 chữ số>` / `NFR-<2 chữ số>` | `NFR-02` |
| Mã thí nghiệm | `EXP-<2 chữ số>` | `EXP-03` |
| Điểm cuối API | danh từ số nhiều, phân cấp, chữ thường nối gạch | `/api/v1/<tài nguyên>/<id>/<tài nguyên con>` |
| Tên sự kiện | `<Danh từ><ĐộngTừ quá khứ>`, không kèm tên service | `TicketIssued`, `HoldExpired` |
| Phiên bản hợp đồng | tiền tố đường dẫn cho API; trường `version` trong lược đồ sự kiện | `/api/v1/…` |
| Trường nhật ký (log) | chữ thường nối gạch dưới, cố định trên mọi service | `correlation_id`, `template_id`, `service_name`, `order_id` |
| Tệp nguồn sơ đồ dạng mã | cùng tên với ảnh xuất ra | `docs/diagrams/src/c3-c4l2-container.puml` |

**Quy tắc quan trọng nhất:** mã (`UC-05`, `QS-01`, `ADR-004`) phải nhất quán ở các tài liệu có liên kết. Không cần chèn mã tài liệu vào mọi lớp/dòng code; liên kết tới module/test chỉ dùng cho các mục cốt lõi trong bảng C7.

## 3.4 Quy ước diễn đạt — chống tuyên bố quá mức

Đây là bảng thực thi ràng buộc mà cả Tầng A và Tầng B đều đặt ra:

| ❌ Không viết | ✅ Viết thay bằng |
|---|---|
| "Nhóm đã thực hiện đánh giá ATAM" | "Nhóm phân tích các kịch bản chất lượng ưu tiên và đánh đổi của những quyết định kiến trúc chính" |
| "Nhóm đã tổ chức Event Storming với các bên liên quan" | "Kỹ thuật Event Storming được áp dụng trong phạm vi nhóm phát triển, với nguồn tri thức nghiệp vụ là…" |
| "Tài liệu kiến trúc tuân thủ ISO/IEC/IEEE 42010" | "Tham chiếu nguyên tắc của ISO/IEC/IEEE 42010 về bao phủ mối quan tâm của các bên liên quan" |
| "Hệ thống X sử dụng Redis để giữ chỗ" *(khi chỉ quan sát từ ngoài)* | "Quan sát cho thấy có đếm ngược …; **suy ra** hệ thống có cơ chế giữ chỗ có thời hạn" |
| "Đồ án đề xuất cơ chế Saga" | "Đồ án lựa chọn, phối hợp và điều chỉnh mẫu Saga cho bài toán…" |
| "Lần đầu tiên", "chưa từng có", "vượt trội" | Bỏ hẳn, trừ khi có trích dẫn chứng minh |
| "Hệ thống hoạt động ổn định" | Nêu số đo cụ thể và điều kiện đo |
| "Nhóm đã phỏng vấn người dùng" *(khi chỉ phát biểu mẫu cho bạn cùng lớp)* | Ghi rõ số mẫu, đối tượng, và thừa nhận không phải người dùng thật |

## 3.5 Cấu trúc thư mục tài liệu

```
docs/
 ├─ tang-a-phuong-phap-nghien-cuu.md   (tài liệu sống cho tới khi mục tiêu/phạm vi được duyệt)
 ├─ tang-b-quy-trinh-ky-thuat.md       (cập nhật khi đầu ra hoặc quyết định đổi)
 ├─ tang-c-quy-uoc-trinh-bay.md        (tra cứu)
 ├─ glossary.md                        (từ điển miền — B2)
 ├─ adr/
 │   ├─ ADR-000-ap-dung-adr.md
 │   └─ ADR-XXX-….md
 ├─ quality-scenarios/QS-XX.md
 ├─ diagrams/                          (tệp nguồn Visual Paradigm + ảnh xuất)
 ├─ contracts/                         (đặc tả API, lược đồ sự kiện)
 ├─ experiments/                       (kịch bản đo, script, kết quả thô)
```

---

# PHẦN 4 — BỘ MẪU

## 🔴 C1 — Mẫu ADR ✍️ *(mẫu điền sẵn)*

**Quy tắc dùng:**
- Một quyết định một tệp. Số hiệu tuần tự, **không bao giờ tái sử dụng**
- Không âm thầm đổi lý do/kết luận của ADR đã *Chấp nhận*; nếu đảo quyết định thì tạo ADR mới và đánh dấu bản cũ là *Bị thay thế bởi ADR-XXX*. Sửa trình bày nhỏ phải có ghi chú thay đổi.
- Hai trường bắt buộc mà mẫu gốc không có: **Phục vụ ASR nào** và **Kiểm chứng bằng cách nào**
- Ô "Hệ quả tiêu cực" để trống là dấu hiệu chưa suy nghĩ đủ — không được để trống
- Chỉ viết ADR cho quyết định có phương án cạnh tranh hoặc hệ quả kiến trúc đáng kể; không lập ADR cho mọi tham số và thư viện nhỏ.

**Vì sao mình chọn đúng ADR này để điền mẫu:** nội dung của nó do chính Tầng C quyết định (dùng ADR hay không), nên điền sẵn được mà không lấn sang quyết định kiến trúc của bạn. Các ADR về kiến trúc thật (chống bán vượt, Saga, outbox…) mình cố tình để trống — đó là phần việc của bạn ở phiếu B11.

---

> # ADR-000 — Áp dụng Architecture Decision Record để ghi nhận quyết định kiến trúc
>
> **Trạng thái:** Chấp nhận · **Ngày:** ___ · **Người quyết định:** ___
>
> ## Bối cảnh
> Đồ án kéo dài khoảng 19 tuần với ba thành viên, trong đó một người giữ vai trò quyết định thiết kế. Các quyết định kiến trúc sẽ được đưa ra rải rác trong suốt quá trình, và một số sẽ phải thay đổi khi triển khai thực tế bộc lộ vấn đề. Đến giai đoạn viết báo cáo, nhóm cần tái dựng được *vì sao* từng quyết định được đưa ra, không chỉ *quyết định là gì*. Ngoài ra, do hình thức chấm điểm là cá nhân trong khi chỉ có một quyển báo cáo, cần có cơ chế truy vết ai quyết định điều gì.
>
> ## Các phương án đã cân nhắc
>
> | Phương án | Ưu | Nhược |
> |---|---|---|
> | Không ghi lại, dựa vào trí nhớ và lịch sử commit | Không tốn công | Đến tháng 11 không tái dựng được lý do; phần lập luận của báo cáo phải viết theo hồi tưởng |
> | Ghi trong một tài liệu chung, sửa trực tiếp khi đổi ý | Gọn, một chỗ | Mất lý do của quyết định đã bị thay thế; khó giải thích vì sao kiến trúc cuối chọn phương án hiện tại |
> | ADR đánh số, giữ lịch sử, có chuỗi thay thế | Tái dựng được quyết định và người chịu trách nhiệm | Tốn công viết; chỉ đáng dùng cho quyết định lớn |
>
> ## Quyết định
> Áp dụng ADR đánh số tuần tự cho các quyết định kiến trúc quan trọng trong `docs/adr/`. Quyết định bị đảo ngược được thể hiện bằng ADR mới kèm liên kết thay thế. Mỗi ADR nêu yêu cầu/kịch bản phục vụ và cách kiểm chứng khi áp dụng được.
>
> ## Phục vụ ASR nào
> Không phục vụ một ASR kỹ thuật cụ thể; phục vụ khả năng truy vết từ yêu cầu tới quyết định và tái dựng lập luận của kiến trúc cuối.
>
> ## Hệ quả tích cực
> - Chuỗi ADR giúp giải thích kiến trúc cuối bằng các phương án, bằng chứng và đánh đổi đã thực sự được cân nhắc
> - Mỗi câu hỏi của hội đồng về một quyết định đều có một tệp trả lời sẵn
> - Chương thiết kế của báo cáo có nguyên liệu viết sẵn, không phải hồi tưởng
>
> ## Hệ quả tiêu cực
> - Tốn thêm thời gian mỗi lần ra quyết định; nếu bỏ bê giữa chừng thì chuỗi bị đứt và mất phần lớn giá trị
> - Có rủi ro viết ADR theo kiểu hình thức, chỉ chép lại quyết định mà không nêu phương án đã loại — khi đó ADR trở thành gánh nặng không sinh lợi
>
> ## Kiểm chứng bằng cách nào
> Không kiểm chứng bằng thí nghiệm. Rà soát tại các mốc chốt kiến trúc và trước khi viết phần thiết kế: những quyết định lớn có ADR chưa, và ADR có phương án thực sự đã cân nhắc cùng hệ quả hai chiều chưa.
>
> ## Liên kết
> Tầng A Phần 5 (ràng buộc về vòng phản hồi) · Tầng B phiếu B11.

---

## 🔴 C2 — Mẫu kịch bản chất lượng

```
QS-__ | Tên: ______________________________________
Thuộc tính chất lượng: ______________________________
──────────────────────────────────────────────────────
Nguồn kích thích : ___________________________________
Kích thích       : ___________________________________
Tạo tác          : ___________________________________
Môi trường       : ___________________________________
Phản ứng         : ___________________________________
Độ đo phản ứng   : (1) bất biến  : ____________________
                   (2) ngưỡng    : ____________________
──────────────────────────────────────────────────────
Mức ưu tiên      : ___________  Lý do: __________________
ADR liên quan    : ___________  Kiểm chứng/Test/EXP: ____
```
*Ví dụ đã điền: xem phiếu B9 của Tầng B.*

## 🔴 C3 — Mẫu đặc tả use case

```
Mã UC       : UC-__          Tên: ______________________
Tác nhân    : ______________  Mức ưu tiên: ______________
Tiền điều kiện  : ____________________________________
Luồng chính     : 1. ______  2. ______  3. ______
Luồng thay thế  : __a. ______________________________
Luồng ngoại lệ  : __e. ______________________________
Hậu điều kiện   : ____________________________________
Sự kiện miền liên quan : _____________________________
Test case tương ứng    : _____________________________
```
> Hai dòng cuối là **thêm mới** so với mẫu đặc tả thông thường, để thực thi hai phép thử ở Tầng B: use case phải truy vết về sự kiện miền, và phải có test case tương ứng.

## 🔴 C4 — Mẫu báo cáo thí nghiệm

```
Mã EXP-__   | Kiểm chứng kịch bản: QS-__
─────────────────────────────────────────────────────
Mục tiêu đo        : __________________________________
Cấu hình môi trường: __________________________________
Kịch bản tải       : __________________________________
BẢN ĐỐI CHỨNG (nếu có): _____________________________
                       (mô tả cách cài đặt: __________)
CẤU HÌNH/PHƯƠNG ÁN ĐƯỢC ĐO: _________________________
Bất biến kiểm tra sau khi chạy: ______________________
─────────────────────────────────────────────────────
Kết quả đối chứng (nếu có): _________________________
Kết quả phương án đo      : _________________________
So với ngưỡng đặt ra  : Đạt / Không đạt
Phân tích            : _____________________________
```
> Đối chứng chỉ cần khi phép thử nhằm so sánh hai cách làm. Nếu có, phải mô tả đủ để người đọc đánh giá tính công bằng; nếu không, ghi rõ đây là kiểm thử bất biến hoặc đo đặc tính của một cấu hình.

## 🟡 C5 — Mẫu mô tả khối xây dựng *(hộp đen / hộp trắng)*

```
HỘP ĐEN — <tên service>
 Trách nhiệm      : ___________________________________
 Giao diện cung cấp: __________________________________
 Giao diện tiêu thụ: __________________________________
 Dữ liệu sở hữu   : ___________________________________
 Không được biết gì về: _______________________________

HỘP TRẮNG — <tên service>
 Các khối con     : ___________________________________
 Quan hệ giữa chúng: __________________________________
```

## ⚪ C6 — Bảng rà nhanh mối quan tâm *(tùy chọn nội bộ)*

```
Bên liên quan | Mối quan tâm | Sơ đồ/mục nào phủ
______________|______________|___________________
```
**Cách dùng:** chỉ lập khi nhóm thấy khó kiểm tra một mối quan tâm đã được giải thích ở đâu. Ô cuối có thể trỏ tới đoạn văn, bảng, test hoặc sơ đồ; không phải cứ trống là phải vẽ thêm hình. Không đưa bảng này vào báo cáo nếu mục lục đã rõ.

## 🟡 C7 — Bảng liên kết tối thiểu

Ma trận 9 cột và file bảng tính riêng là quá nặng cho đồ án này. Thay bằng một bảng Markdown chỉ dành cho các bất biến, ASR và quyết định cốt lõi:

| Vấn đề/yêu cầu quan trọng | Quyết định | Thành phần thực hiện | Cách kiểm chứng | Kết quả |
|---|---|---|---|---|
| *(A2/B8/B9)* | *(ADR hoặc mô tả thiết kế)* | *(service/module/test)* | *(test/EXP/phân tích)* | *(điền sau đánh giá)* |

**Cách dùng:**

- Cập nhật lần đầu sau khi chốt các quyết định kiến trúc chính; cập nhật lần hai sau khi có kết quả đánh giá.
- Không đưa mọi CRUD, lớp hoặc endpoint vào bảng. Use case đã có test tương ứng và ADR đã có yêu cầu phục vụ thì không cần lặp lại ở đây, trừ khi là phần cốt lõi.
- Chấp nhận “không áp dụng” khi một quyết định chỉ là ràng buộc trình bày hoặc không thể kiểm chứng bằng thí nghiệm; phải ghi lý do ngắn.
- Mục đích là tránh mục tiêu hoặc kết quả mồ côi, không phải tạo một hệ thống quản lý yêu cầu thu nhỏ.

---

## 🔴 C8 — Danh sách tự kiểm trước khi nộp

**Nội dung cốt lõi** *(không phải checklist tuân thủ arc42/ISO)*
- [ ] Mục tiêu chất lượng đã nêu **và xếp ưu tiên**?
- [ ] Ràng buộc đã liệt kê, phân biệt được với quyết định?
- [ ] Ranh giới hệ thống (trong/ngoài) đã rõ?
- [ ] Phần chiến lược có **so sánh phương án thay thế**, không chỉ mô tả cái đã chọn?
- [ ] Phân rã thành phần có lập luận về ranh giới?
- [ ] Kịch bản thời gian chạy có **cả luồng thất bại**?
- [ ] Khung nhìn triển khai phản ánh đúng hạ tầng thật?
- [ ] Khái niệm xuyên suốt (bảo mật, nhật ký, giao dịch) đã có?
- [ ] Quyết định kiến trúc ghi lại có cấu trúc?
- [ ] Yêu cầu chất lượng đã **định lượng và đo lại**?
- [ ] Rủi ro và nợ kỹ thuật nêu **trung thực**?
- [ ] Thuật ngữ định nghĩa và dùng nhất quán?
- [ ] Nếu dùng C6, các mối quan tâm quan trọng đã trỏ tới nội dung trả lời phù hợp?
- [ ] Bảng C7 đã nối các vấn đề/ASR cốt lõi tới quyết định, phần hiện thực và kết quả?
- [ ] Mọi ADR đã điền đủ hai trường *Phục vụ ASR nào* và *Kiểm chứng bằng cách nào*?

**Hình thức**
- [ ] Mục lục sinh tự động từ style?
- [ ] Mọi hình có số theo chương, chú thích **dưới**, có nguồn nếu lấy ngoài?
- [ ] Mỗi sơ đồ dùng đúng loại/ký pháp; tên nghiệp vụ tiếng Việt là nhãn chính, còn mã truy vết nội bộ chỉ xuất hiện khi cần đối chiếu?
- [ ] Sơ đồ vẫn đọc được khi đặt trên trang báo cáo, có hướng đọc rõ, hạn chế đường cắt nhau và có chú giải cho ký hiệu tùy biến?
- [ ] Mọi bảng có số theo chương, chú thích **trên**?
- [ ] Đủ ba danh mục đầu quyển?
- [ ] Mọi trích dẫn khớp Tài liệu tham khảo?
- [ ] Mỗi chương có Kết luận chương?
- [ ] Font/lề/giãn dòng khớp **file mẫu của khoa**?

**Diễn đạt**
- [ ] Đã rà toàn văn theo bảng 3.4, không còn câu tuyên bố quá mức?
- [ ] Mã (UC/QS/ADR/EXP) nhất quán ở mọi nơi xuất hiện?

---

# PHẦN 5 — KIỂM TRA CHÉO BA TẦNG

## 5.1 Mọi ràng buộc từ A và B đã có chỗ trú trong C chưa

| Ràng buộc | Từ | Trú ở đâu trong C | ✅ |
|---|---|---|---|
| Mẫu phiếu kịch bản chất lượng | A, B | C2 | ✅ |
| Mẫu ADR có trạng thái và "thay thế bởi" | A, B | C1 | ✅ |
| Mẫu báo cáo thí nghiệm ghi rõ cấu hình; hỗ trợ đối chứng khi có | A, B | C4 | ✅ |
| Mẫu ADR dùng chung cho mọi loại quyết định (kể cả AI) | A | C1 — không có trường riêng theo lĩnh vực | ✅ |
| Quy ước diễn đạt không tuyên bố quá mức | A, B | 3.4 | ✅ |
| Đặt tên file và tiêu đề sơ đồ theo context | B | 3.3 | ✅ |
| Gọi đúng tên và giữ nhất quán C4/UML | B | 3.1 | ✅ |
| Ký hiệu cho bản đồ Bounded Context | B | 3.1 (biểu đồ gói) | ✅ |
| Đặt tên endpoint, tên sự kiện, đánh phiên bản | B | 3.3 | ✅ |

## 5.2 Một thông tin sống ở đâu — bảng chống trôi dạt

| Loại thông tin | Sống ở | Hai tầng kia |
|---|---|---|
| Khung nghiên cứu, câu hỏi nghiên cứu, phạm vi, chiến lược kiểm chứng, tính hợp lệ, đóng góp | **A** | chỉ tham chiếu |
| Trình tự bước, phương pháp từng bước, phép thử hoàn thành, sản phẩm công việc | **B** | chỉ tham chiếu |
| Ký hiệu, mẫu biểu, quy ước đặt tên, quy ước diễn đạt, danh sách tự kiểm | **C** | chỉ tham chiếu |

**Quy tắc:** khi phải sửa một thông tin, sửa ở đúng tầng sở hữu nó. Nếu thấy mình đang sửa cùng một thứ ở hai file, tức là đã trùng lặp — xóa bản sao và thay bằng một dòng tham chiếu.

## 5.3 Vòng đời khác nhau của ba tầng

| | A | B | C |
|---|---|---|---|
| Sửa khi nào | Khi mục tiêu/phạm vi/bằng chứng được duyệt hoặc thay đổi | Khi đầu ra, quyết định hoặc thứ tự thực hiện đổi | Khi phát sinh loại sơ đồ/mẫu mới hoặc quy định chính thức |
| Nếu sai thì | Sửa mạch vấn đề–mục tiêu–đánh giá liên quan | Rà lại đầu ra/thiết kế bị ảnh hưởng | Sửa biểu diễn và liên kết liên quan |
| Ai đọc | Cô Liên, hội đồng | Ba thành viên | Người đang vẽ hoặc đang viết |

---

## Nhật ký sửa đổi

| Ngày | Sửa gì | Lý do |
|---|---|---|
| 2026-08-07 | Bản đầu | — |
| 2026-08-08 | Bỏ cơ chế arc42/ISO/ma trận truy vết mang tính thủ tục; sửa quy ước C4/UML và mẫu thí nghiệm | Giữ mức tài liệu phù hợp ĐATN đại học |
| 2026-08-21 | Bổ sung thứ tự ưu tiên cho sơ đồ báo cáo: đúng ký pháp UML/Visual Paradigm, tên nghiệp vụ dễ đọc, mã truy vết chỉ là thông tin phụ và hình thức cuối theo mẫu PTIT hiện hành | Ngăn sơ đồ nội bộ mang nguyên mã governance sang báo cáo và làm rõ nghĩa “gần phong cách PTIT/HUST” |

---

## Tài liệu tham chiếu

- arc42 — khuôn mẫu tài liệu kiến trúc phần mềm, 12 mục. arc42.org
- Brown, S. *The C4 Model for Visualising Software Architecture.* c4model.com
- Nygard, M. *Documenting Architecture Decisions.* — mẫu ADR gốc
- ISO/IEC/IEEE 42010 — *Systems and software engineering — Architecture description* (bản 2011 và bản 2022)
- Visual Paradigm — [UML: Modeling Software Architecture with Packages](https://www.visual-paradigm.com/guide/uml-unified-modeling-language/modeling-software-architecture-with-package/)
- HUST — [Học phần Phân tích và thiết kế hệ thống](https://seee.hust.edu.vn/en/dao-tao/hoc-phan/ac3010e/) và [Hướng dẫn trình bày đồ án, bảo vệ tốt nghiệp](https://soict.hust.edu.vn/wp-content/uploads/Huong-dan-trinh-bay-do-an-Bao-ve-tot-nghiep-TS.-Trinh-Van-Chien-1.pdf)
- PTIT — [Bài giảng Phân tích và thiết kế hệ thống thương mại điện tử](https://dlib.ptit.edu.vn/handle/HVCNBCVT/2615); các đồ án trong thư viện số dùng khối “Phân tích và thiết kế hệ thống” với biểu đồ UML
- Quy định trình bày đồ án/khóa luận tốt nghiệp — **lấy bản chính thức từ Khoa CNTT, PTIT**
