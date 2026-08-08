# QUY TRÌNH LÀM VIỆC — TÀI LIỆU CHỦ
## ĐATN FlashTicket · PTIT
### *Tài liệu duy nhất theo hằng ngày. Ba tầng A/B/C là phần tra cứu chi tiết cho từng bước ở đây.*

> **Bản này không chứa lịch theo tuần.** Trình tự được điều khiển bằng **cổng chuyển giai đoạn** — điều kiện phải đạt để đi tiếp — chứ không bằng mốc thời gian. Lý do ở Phần 6.

---

# PHẦN 0 — CẤU TRÚC BỘ TÀI LIỆU

## 0.1 Quan hệ giữa tài liệu này và ba tầng

```
        TÀI LIỆU CHỦ  (file này)
        = TRÌNH TỰ và CỔNG — làm gì trước, khi nào được đi tiếp
                │
     ┌──────────┼──────────┬──────────────┐
     ▼          ▼          ▼              ▼
  TẦNG A     TẦNG B     TẦNG C          B5.5
 phương     quy trình   quy ước      hiện trạng
  pháp       kỹ thuật   trình bày    mã nguồn
     └──────────┴──────────┴──────────────┘
        = CHI TIẾT — mỗi bước làm cụ thể thế nào
```

| | Tài liệu chủ | Ba tầng |
|---|---|---|
| Trả lời | *Bây giờ tôi làm gì? Đã được đi tiếp chưa?* | *Bước đó làm cụ thể thế nào?* |
| Mở khi nào | Hằng ngày | Khi bắt đầu một bước mới |

## 0.2 Một hệ mã duy nhất

Mã chính thức của sản phẩm công việc: **`A1–A9` · `B1–B19` · `C1–C8`**. Tài liệu chủ **không đặt mã riêng**, chỉ sắp thứ tự. Bỏ hẳn cách gọi "Pha 4 bước 4.5".

**Đính chính so với bản trước:** bản trước tuyên bố dải mã là `B1–B15` nhưng lại dùng `B16`, còn `B17`/`B18` thì không xuất hiện ở giai đoạn nào — tức là nhánh trợ lý sửa lỗi bị xây mà không được đo. Đã sửa: Tầng B nay có **Nhóm 5** gồm `B16–B19`, và nhánh AI hiện diện ở **cả bảy giai đoạn** (Phần 3).

## 0.3 Đọc theo thứ tự nào

Một lần ở giai đoạn lập kế hoạch: tài liệu này → phần chắt lọc của Tầng A/B/C → B5.5. Có thể kiểm kê mã nguồn bất cứ lúc nào để biết tài sản hiện có; chỉ **không dùng cấu trúc code làm căn cứ duy nhất để chốt ranh giới**. Sau đó mở đúng phiếu cần thiết, không phải tuần tự hoàn thành mọi biểu mẫu.

---

# PHẦN 1 — BỐI CẢNH ĐỊNH HÌNH QUY TRÌNH

**1. Báo cáo bắt đầu từ vấn đề, không bắt đầu từ thao tác refactor.** Nhóm đã có một nguyên mẫu chạy được và sẽ tái sử dụng có chọn lọc, nhưng mạch báo cáo vẫn là *vấn đề → yêu cầu → thiết kế → hiện thực → đánh giá*. Repo cũ là tài sản tiền nhiệm và bằng chứng hiện trạng, không phải lý do kiến trúc. Nhóm không viết mục tiêu là “refactor repo cũ”, cũng không giả vờ toàn bộ hệ thống được viết từ số không.

**2. Trọng tâm phân rã nằm ở `core-service`, nhưng mọi ranh giới kế thừa vẫn phải được rà soát.** `user-service` và `discovery-service` là điểm xuất phát có kho dữ liệu/vòng đời riêng; giữ gần như nguyên nếu phân tích không cho thấy lý do đổi.

> **Cách khai báo đúng trong báo cáo:** đây là các thành phần kế thừa được đánh giá và tái sử dụng. Sau phần phân tích, nêu ranh giới nào được giữ, ranh giới nào thay đổi và lý do. Không trình bày như thể mọi service đều được suy ra hoàn toàn mới từ một workshop.
>
> Câu hỏi chính: **các miền hiện nằm trong `core-service` nên được phân rã và sở hữu dữ liệu thế nào trong hai trần đã chốt**. Repo hiện có các package nghiệp vụ `event`, `booking`, `payment`, `notification`, `promotion` cùng `common/shared`, nhưng chưa có ranh giới module được cưỡng chế.

**3. Repo cũ là nguồn mã và bảng dữ liệu có thể tái sử dụng, không phải đường cơ sở đối chứng mặc định.** Hướng so sánh monolith với microservices không phải trục nghiên cứu.

> Chỉ tạo bản đối chứng/cờ tắt khi cần trả lời một câu hỏi so sánh cụ thể và có thể cài đặt công bằng. Nhiều bất biến như oversell = 0 hoặc check-in trùng = 0 được kiểm chứng trực tiếp, không cần cố tình tạo một bản sai. Nhánh AI có thể so với quy trình thủ công nếu điều kiện cho phép; đánh giá tối thiểu vẫn phải có ca lỗi và nguyên nhân thật.

**4. Khảo sát bên ngoài ở mức công khai và có giới hạn.** Mục tiêu là hiểu luồng người mua và lấy bằng chứng cho phần tổng quan, không phải khám phá chức năng admin/organizer không thể tiếp cận. Mọi suy luận kỹ thuật từ giao diện phải được ghi là suy luận, không phải sự thật về kiến trúc bên trong.

**5. Chấm điểm cá nhân, một quyển báo cáo, hỏi theo phần.** Bảng phân công có hai cột tách nhau: người quyết định thiết kế và người hiện thực hóa.

**6. Mục tiêu là học kỹ thuật và làm đẹp CV, không phải sáng tạo cái mới.** Quy trình này tối ưu cho chiều sâu kỹ thuật có số đo, có lập luận bảo vệ được. Hai mục tiêu này trùng nhau, không xung đột.

**7. Nhóm dùng công cụ AI.** Viết mã rẻ, ra quyết định kiến trúc thì không. Nút thắt là chất lượng thiết kế.

**8. Mobile là một client của cùng hệ thống nghiệp vụ.** Không tạo nhánh nghiên cứu mobile riêng nếu ứng dụng chủ yếu dùng lại backend Spring Boot. Tuy nhiên, check-in trực tuyến vẫn là use case cốt lõi: backend phải xác thực QR, bảo đảm idempotency và ngăn hai thiết bị check-in cùng vé. **Không làm check-in offline.**

**9. CI/CD là phần hỗ trợ.** Thực hiện nếu giúp tích hợp/triển khai, nhưng không đưa thành mục tiêu nghiên cứu, ASR hay điều kiện kết luận đồ án thành công.

---

# PHẦN 2 — MẠCH NỘI DUNG CỦA QUYỂN BÁO CÁO

**Chưa chốt số chương.** Các tài liệu Thực tập cơ sở và nhận xét cũ chỉ cho biết phong cách giảng viên, không phải mẫu bắt buộc của ĐATN. Nhóm phải xin đề cương/file mẫu ĐATN hiện hành và xác nhận với cô Liên trước khi đánh số chương.

Dù mẫu cuối có 3, 4 hay 5 chương, báo cáo cần giữ mạch sau:

| Khối nội dung | Phải trả lời | Đầu ra nguồn |
|---|---|---|
| **Bối cảnh và vấn đề** | Vì sao bài toán đáng làm; giới hạn khảo sát là gì | A1–A2, B1 |
| **Mục tiêu, phạm vi, phương pháp** | Làm đến đâu và lấy gì làm bằng chứng | A3–A7 |
| **Cơ sở lựa chọn** | Khái niệm/mẫu nào thực sự dùng: microservices, nhất quán, Saga/Outbox, idempotency, logging, Drain/LLM | Tài liệu nghiên cứu gắn với ADR |
| **Phân tích yêu cầu/nghiệp vụ** | Actor, use case, bất biến và mô hình miền | B2–B10 |
| **Thiết kế** | Ranh giới service, schema độc lập, API/event, Saga, check-in trực tuyến, logging và trợ lý | B5.5, B11–B18 |
| **Hiện thực** | Thành phần đã xây, phần mã/bảng cũ tái sử dụng và thay đổi, sai lệch so với thiết kế | Code, migration, ADR |
| **Kiểm thử và đánh giá** | Chức năng, bất biến, hiệu năng/khả năng phục hồi và mức hữu ích của trợ lý | B15, B17–B19 |
| **Kết luận và hạn chế** | Mục tiêu nào đạt, không đạt, giới hạn suy rộng và hướng phát triển | A4, A8, kết quả |

### Cách viết để không biến báo cáo thành “nhật ký refactor”

- Phần Phân tích/Thiết kế được viết từ yêu cầu và quyết định đích, không đi theo thứ tự file/package cũ.
- Repo cũ xuất hiện ở mục hiện trạng hoặc hiện thực hóa: bảng nào/lớp nào được giữ, sửa, tách, viết mới hoặc bỏ.
- Những nội dung như cấu trúc package cũ, commit/migration chi tiết có thể đưa vào phụ lục; phần chính tập trung vào lý do và kiến trúc đích.
- Viết báo cáo **song song** với khảo sát, thiết kế và đánh giá. Không đợi hệ thống xong mới hồi tưởng toàn bộ lập luận.

### Cơ sở lý thuyết

Chỉ viết lý thuyết cần để hiểu quyết định và phép đánh giá thực tế. Mỗi mục lý thuyết phải được dùng ở một quyết định, mô hình hoặc thí nghiệm; không đặt chỉ tiêu số trang và không thêm framework chỉ để làm báo cáo trông hàn lâm.

---

# PHẦN 3 — BẢY GIAI ĐOẠN, HAI NHÁNH

Hai nhánh chạy song song qua cùng bảy giai đoạn:
- **Nhánh N — nghiệp vụ vé** (trục nghiên cứu chính)
- **Nhánh T — trợ lý sửa lỗi** (nhánh AI)

Nhánh T không phải phần phụ làm sau. Nó có bounded context/quyền riêng, kịch bản chất lượng và phần đánh giá riêng trong báo cáo.

---

## GIAI ĐOẠN 0 — Nền tảng làm việc

| | Việc | Chi tiết ở |
|---|---|---|
| N+T | Lập `docs/`: `adr/`, `glossary.md`, `diagrams/src/`, `contracts/`, `experiments/` | Tầng C mục 3.5 |
| N+T | Chốt mẫu ADR gọn; ADR-000 chỉ cần nếu nhóm muốn ghi quyết định dùng ADR | Tầng C mẫu C1 |
| N+T | Chốt quy ước đặt tên và bảng phân vai ký hiệu | Tầng C mục 3.1, 3.3 |
| N | Clone repo mới; repo cũ để nguyên làm nguồn mã tham khảo | — |
| N | Xóa `.env` khỏi repo mới, xoay khóa nếu là khóa thật | — |
| N+T | Hỏi cô Liên: đề cương/cấu trúc chương ĐATN? · xin file mẫu trình bày hiện hành của khoa | Tầng A, Tầng C |
| N+T | Tạo khung báo cáo ngay từ đầu và ghi nội dung đã có bằng nguồn/ghi chú, chưa chốt số chương | Phần 2 |

**Điều kiện sẵn sàng:** repo/tài liệu làm việc chung dùng được; đã có nơi ghi quyết định; câu hỏi về mẫu báo cáo được gửi cho cô Liên. Không để việc chờ mẫu trình bày chặn khảo sát và phân tích nội dung.

---

## GIAI ĐOẠN 1 — Vấn đề và bối cảnh

| | Việc | Mã |
|---|---|---|
| N | Khảo sát các luồng công khai theo bảng công việc B1; ghi cả phần không quan sát được, không yêu cầu admin/organizer | B1 |
| **T** | Ghi lại quy trình dò lỗi thủ công hiện tại có cấu trúc — bước, dữ liệu dùng, điểm nghẽn và thời gian nếu có thể ghi nhận | B1 |
| N+T | Viết bối cảnh và tính cấp thiết — theo **bốn nước** dành cho đề tài không mới | A1 |
| N+T | Chưng cất phát biểu vấn đề (≤150 từ, không tên công nghệ) | A2 |
| N+T | **Nháp** câu hỏi nghiên cứu — chưa chốt | A4 |

**Điều kiện sẵn sàng:** bảng khảo sát có bằng chứng và giới hạn; quy trình dò lỗi thủ công đã được mô tả; phát biểu vấn đề không phụ thuộc tên công nghệ.

---

## GIAI ĐOẠN 2 — Miền nghiệp vụ và ranh giới 🔑

> **Giai đoạn quan trọng nhất.** Nơi quyết định tách service — thứ đắt nhất để sửa về sau.

| | Việc | Mã |
|---|---|---|
| N | Từ điển miền — phân biệt dứt khoát các cặp dễ nhầm | B2 |
| N | Mô hình quy trình nghiệp vụ, mỗi quy trình có ít nhất một nhánh thất bại | B3 |
| N | Lập dòng thời gian sự kiện miền gọn, tham khảo Event Storming | B4 |
| N | Gom cụm → Bounded Context ứng viên → phân loại cốt lõi/hỗ trợ/chung | B5 |
| N | Hoàn thiện đối chiếu context đích với các package/bảng hiện có; quyết định giữ/sửa/tách/viết mới/bỏ | B5.5 |
| N | Xác định aggregate và bất biến trong mỗi context | B7 |
| **T** | **Định nghĩa "sự cố" là gì** và phân loại 4 lớp mục tiêu: lỗi âm thầm · lỗi ngoại lệ · lỗi CSDL · lỗi hạ tầng | B4, B5 |
| **T** | Xác định trợ lý sửa lỗi là **một bounded context riêng**; chốt ranh giới quyền: chỉ đọc, không ghi nghiệp vụ | B5 |

**Hai câu nghiệp vụ còn để ngỏ:** chính sách hoàn tiền do nền tảng hay nhà tổ chức đặt · tỉ lệ giữ lại phòng hoàn tiền và mốc mở kỳ đối soát. Check-in offline đã bỏ; check-in trực tuyến phải chốt bất biến và phản hồi khi hai thiết bị quét gần đồng thời.

**Điều kiện sẵn sàng:**
- Kiến trúc ứng viên không vượt **8 service nghiệp vụ** và **3 luồng Saga** — hai trần đã chốt
- Mọi điểm nóng đã giải quyết hoặc chuyển vào danh sách rủi ro
- Mọi module của `core-service` đã ánh xạ được vào ít nhất một context
- **Đã có ADR cho vị trí aggregate tồn kho vé** (3 phương án ở B5.5 mục 2.3)
- Trợ lý sửa lỗi đã có ranh giới quyền rõ ràng bằng văn bản

---

## GIAI ĐOẠN 3 — Yêu cầu và mục tiêu đo được

| | Việc | Mã |
|---|---|---|
| N | Use case + đặc tả các ca cốt lõi; mobile dùng chung nghiệp vụ, gồm check-in trực tuyến | B6 |
| N | Bảng yêu cầu FR/NFR | B8 |
| N | Kịch bản chất lượng gọn; mỗi kịch bản có tiêu chí quan sát được phù hợp (số, bất biến hoặc tiêu chí chấm rõ) | B9 |
| **T** | Kịch bản riêng cho trợ lý: chất lượng gom template/context và mức hữu ích trên ca lỗi đã biết; thời gian so với thủ công là phép đo cộng thêm | B9 |
| N+T | Bảng ưu tiên phẳng, chốt danh sách ASR | B10 |
| N+T | **Quay lại CHỐT** mục tiêu, đối tượng, phạm vi, câu hỏi nghiên cứu | A3, A5, A6, A4 |

> ⚠️ **Vì sao A3–A6 chốt ở đây chứ không ở Giai đoạn 1:** không thể viết *"mục tiêu: p95 ≤ X ms"* trước khi có kịch bản chất lượng và một vòng đo thử. Chốt sớm thì hoặc quá dễ hoặc bất khả thi. **Đây là chỗ Tầng A và Tầng B đan xen — không tầng nào xong trước tầng nào.**

**Điều kiện sẵn sàng:** các ASR thật sự ảnh hưởng kiến trúc đã được ưu tiên; mỗi mục tiêu quan trọng có cách kiểm chứng; mục tiêu/phạm vi/câu hỏi nghiên cứu không mâu thuẫn nhau.

---

## GIAI ĐOẠN 4 — Kiến trúc và bản vẽ 🔑

> Giai đoạn chốt **các quyết định xuyên service cần có trước khi tách/viết mới** — xem Phần 5. Code nguyên mẫu đã tồn tại nên không dùng cụm “trước dòng code đầu tiên” theo nghĩa đen.

| | Việc | Mã |
|---|---|---|
| N | Chốt phân rã `core-service`, ghi ADR cho từng quyết định lớn | B11 |
| N | Bản đồ sở hữu dữ liệu; chuyển đổi bảng cũ; schema/CSDL và ERD riêng từng service; credential độc lập | B12 |
| N | Hợp đồng API + lược đồ sự kiện | B13 |
| N | Sequence diagram các Saga và luồng liên service cốt lõi, gồm luồng lỗi/idempotency | B14 |
| N | So sánh các phương án bố trí 2 EC2; chỉ chốt bằng ADR sau sơ đồ và đo thử sơ bộ | B11 |
| **T** | Chuẩn logging có cấu trúc, mã tương quan, masking và luồng thu thập | **B16** |
| **T** | Thiết kế Anti-Corruption Layer cho trợ lý; tách nhóm tool đọc khỏi nhóm tool ghi trong `discovery-service` | B11 |
| **T** | Thiết kế `Drain → context builder → LLM API`, đầu ra tư vấn chỉ đọc và ranh giới dữ liệu | B17, B18 |

**Điều kiện sẵn sàng:**
- Các bản đồ/đặc tả xuyên service cốt lõi ở Phần 5 đã đủ để ba thành viên hiện thực nhất quán; không đặt chỉ tiêu đúng 8 hình
- Mọi ADR có đủ hai trường *Phục vụ ASR nào* và *Kiểm chứng bằng cách nào*
- Mỗi service sở hữu schema/CSDL và credential riêng; **không có khóa ngoại, JOIN, repository hoặc truy vấn trực tiếp xuyên schema**
- **Không có vòng lặp phụ thuộc đồng bộ** (A gọi B, B gọi A)
- **B16 đã chốt** — đây là cổng cứng, xem Phần 6

---

## GIAI ĐOẠN 5 — Xây dựng

| | Việc |
|---|---|
| N | **Bộ khung xuyên suốt** — lát cắt mỏng chạy xuyên hệ thống, triển khai lên EC2 thật |
| N | Hiện thực theo lát dọc; tái sử dụng có chọn lọc mã và migration từ repo cũ, ghi rõ phần giữ/sửa/viết mới |
| N | Tách schema/CSDL, cấp credential riêng và thay mọi truy cập chéo bằng API/sự kiện/read model |
| N | Cài đặt cơ chế nhất quán; chỉ thêm cấu hình đối chứng khi phép đánh giá cần so sánh |
| **T** | Xây đường ống `structured log → collector → Drain → context builder → LLM API → tư vấn` |
| **T** | Thu thập/gán nhãn một tập log phát triển nhỏ và một tập ca đánh giá trong phạm vi các luồng đã chọn | B17–B19 |
| N+T | Khi đảo một quyết định kiến trúc đã chấp nhận: tạo ADR thay thế; chỉnh code nhỏ không cần ADR |
| N+T | Cập nhật phần Hiện thực của báo cáo theo lát dọc đã hoàn thành; không để tới cuối mới viết |

**🚪 CỔNG 5 → 6:**
- Mọi use case cốt lõi chạy đầu-cuối trên EC2
- Các test/thí nghiệm ưu tiên cao chạy lặp lại được và lưu cấu hình/kết quả
- Tập log đánh giá có phạm vi, nhãn và nguyên nhân lỗi thật được ghi trước khi chấm kết quả cuối

---

## GIAI ĐOẠN 6 — Kiểm chứng

| | Việc | Mã |
|---|---|---|
| N | Kiểm thử chức năng, liên kết use case cốt lõi với test case | B15 |
| N | Kịch bản trình diễn, gồm chủ đích làm hỏng một thành phần | B15 |
| N | Thí nghiệm đo các bất biến và thuộc tính chất lượng; đối chứng chỉ cho câu hỏi thật sự cần so sánh | B15 |
| N | Phân tích các rủi ro/đánh đổi quan trọng xuất hiện trong kết quả | B15 |
| **T** | Kiểm tra mức gom template của Drain trên tập log nhỏ đã gán nhãn | **B17** |
| **T** | Kiểm tra context có chứa đủ bằng chứng và đã loại dữ liệu nhạy cảm | **B18** |
| **T** | Đánh giá tư vấn trên một số ca lỗi đã biết; đo thời gian có/không trợ lý nếu điều kiện cho phép | **B19** |

**Điều kiện sẵn sàng:** mỗi ASR có kết quả hoặc giải thích trung thực vì sao chưa đo được; nhánh AI có kết quả từng ca, gồm cả ca sai/thất bại; giới hạn cỡ mẫu và môi trường được ghi rõ.

---

## GIAI ĐOẠN 7 — Hoàn thiện

Hoàn thiện A7 (phương pháp), A8 (ý nghĩa/đóng góp), A9 (bố cục/phân công) theo nội dung đã thực hiện · trả lời mục tiêu/câu hỏi bằng kết quả đánh giá · nêu hạn chế và hướng phát triển · chạy danh sách tự kiểm C8 · chỉnh số chương theo mẫu ĐATN đã xác nhận.

---

# PHẦN 4 — LẬP LUẬN ĐỂ TÁCH SERVICE

Phần này là **phương pháp**. Danh sách service cụ thể bàn riêng.

**Phạm vi áp dụng:** chỉ `core-service`. `user-service` và `discovery-service` là ràng buộc kế thừa, khai báo chứ không suy ra.

## 4.1 Bắt đầu từ sự kiện, không từ danh từ

Sai lầm phổ biến nhất: nhìn danh sách bảng CSDL rồi chia service theo danh từ. Danh từ cho bạn **bảng**; **sự kiện** mới cho bạn **ranh giới**.

Ranh giới service là ranh giới của **quyền thay đổi trạng thái**. Chỉ có sự kiện mới lộ ra ai thay đổi cái gì và khi nào. Hai thực thể có thể nằm chung một bảng nhưng thuộc hai vòng đời hoàn toàn khác nhau.

## 4.2 Sáu câu hỏi cho một đường cắt đề xuất

Đây là bảng cân nhắc, không phải bài thi đạt/trượt. Một bất biến xuyên ranh giới không tự động “cấm cắt”, nhưng buộc nhóm chứng minh cơ chế nhất quán/Saga và chi phí tương ứng; nếu không có lợi ích đủ lớn thì nên gộp.

| # | Phép thử | Câu hỏi | Trượt thì sao |
|---|---|---|---|
| **1** | **Bất biến** | Có quy tắc nghiệp vụ nào **bắt buộc phải đúng cùng một lúc** ở cả hai bên đường cắt không? | Chi phí nhất quán tăng mạnh; chỉ cắt khi có lý do rõ và một Saga/cơ chế phù hợp trong trần đã chốt |
| **2** | **Triển khai độc lập** | Đổi bên A có buộc phải đổi và triển khai bên B **cùng lúc** không? | Nếu buộc → chưa phải hai service, chỉ là hai package |
| **3** | **Dữ liệu độc lập** | Bên A có cần `JOIN` sang bảng của bên B để trả lời một truy vấn thường xuyên không? | Nếu có → hoặc gộp, hoặc chấp nhận sao chép qua sự kiện |
| **4** | **Chu kỳ thay đổi** | Hai bên có bị sửa vì những lý do nghiệp vụ khác nhau không? | Nếu luôn sửa cùng nhau → lý do tách yếu |
| **5** | **Chu kỳ tải** | Hai bên có cần scale theo những nhịp khác nhau không? | Nếu giống hệt → lý do tách yếu |
| **6** | **Chủ sở hữu quyết định** | Hai bên có do hai vai trò nghiệp vụ khác nhau định đoạt quy tắc không? | Nếu cùng một vai trò → cân nhắc gộp |

## 4.3 Bốn cách tách SAI

| Kiểu tách sai | Ví dụ | Vì sao sai |
|---|---|---|
| **Theo tầng kỹ thuật** | `auth-service`, `logging-service` | Quan tâm xuyên suốt, không phải miền nghiệp vụ. Mọi luồng đều đi qua → nghẽn và ghép nối chặt |
| **Theo vai trò người dùng** | `admin-service`, `buyer-service` | Cùng một thực thể bị chia đôi theo ai truy cập. Hai service cùng ghi một bảng |
| **Theo bảng CSDL** | Mỗi bảng một service | Bỏ qua khái niệm aggregate; đẻ Saga cho cả thao tác lẽ ra là một giao dịch |
| **Theo cảm giác về quy mô** | *"Service này to quá, tách đôi cho gọn"* | Kích thước không phải tiêu chí. Một service lớn ranh giới đúng tốt hơn hai service nhỏ ranh giới sai |

## 4.4 Đếm chi phí trước khi chốt

```
Phương án:  ________________________
Số service nghiệp vụ:             ___  (trần dự án ≤ 8, đã tính user + discovery)
Số luồng giao dịch xuyên service:  ___  (trần dự án ≤ 3)
Số điểm cần sao chép dữ liệu:      ___
Số hợp đồng API phải chốt:         ___
```

**Cả hai trần đều đã chốt.** Trần Saga cần chú ý đặc biệt vì mỗi luồng kéo theo trạng thái, idempotency, bù trừ, quan sát và kiểm thử lỗi.

## 4.5 Vì sao mã nguồn cũ không được quyết định kiến trúc đích

`core-service` có các package nghiệp vụ `event`/`booking`/`payment`/`notification`/`promotion` nhưng chưa có ranh giới module được cưỡng chế. Cám dỗ là lấy nguyên package làm service.

**Vấn đề không phải đọc code sớm, mà là dùng code làm căn cứ duy nhất.** Nhóm vẫn nên kiểm kê mã/bảng để ước lượng tái sử dụng và phát hiện ghép nối; nhưng phải lập luận đường cắt từ nghiệp vụ, bất biến, quyền sở hữu dữ liệu và quan hệ gọi.

Kết quả có thể trùng một phần với package hiện tại. Khi đó báo cáo nêu: phân tích miền và dữ liệu dẫn tới các ranh giới nào; đối chiếu code cho thấy phần nào trùng/lệch; cách di trú mỗi điểm truy cập entity/repository xuyên package và mỗi bảng sang schema sở hữu mới.

---

# PHẦN 5 — BỘ THIẾT KẾ CẦN CHỐT TRƯỚC KHI TÁCH/VIẾT MỚI

## 5.1 Tiêu chí phân loại

Code nguyên mẫu đã tồn tại, nên mục tiêu không phải tái hiện một lịch sử “chưa có dòng code nào”. Nhóm cần chốt trước những quyết định mà nếu sai sẽ làm nhiều service/schema cùng sửa; chi tiết nội bộ có thể phát triển lặp.

> **Bản vẽ nào mà sai thì phải sửa NHIỀU service cùng lúc → phải có TRƯỚC.**
> **Bản vẽ nào mà sai thì chỉ sửa TRONG một service → làm SAU được.**

Nỗi lo *"tài liệu nền tảng sai từ đầu thì thảm họa"* **đúng với nhóm thứ nhất và không đúng với nhóm thứ hai**. Cố chốt sớm nhóm thứ hai vừa lãng phí vừa làm chậm nhóm thứ nhất.

## 5.2 Đầu ra kiến trúc tối thiểu

| # | Đầu ra | Vai trò | Mã |
|---|---|---|---|
| 1 | **Bản đồ context/service + ADR phân rã** | Chốt ranh giới trong trần ≤8 service, ≤3 Saga | B5, B11 |
| 2 | **C4 System Context + Container** | Chốt trong/ngoài, đơn vị triển khai và quan hệ chính | B11 |
| 3 | **Bản đồ sở hữu dữ liệu + kế hoạch chuyển bảng** | Chốt schema/credential độc lập và dữ liệu lấy qua API/sự kiện | B12 |
| 4 | **Hợp đồng API + lược đồ sự kiện cốt lõi** | Cho phép các thành viên tích hợp độc lập | B13 |
| 5 | **Sequence các Saga/luồng tranh chấp chính** | Chốt idempotency, lỗi, thử lại và bù trừ | B14 |
| 6 | **Chuẩn logging + trace/context của trợ lý** | Chốt đầu vào cho Drain/LLM và bảo vệ dữ liệu | B16–B18 |
| 7 | **Các phương án deployment trên 2 EC2** | So sánh trước, chưa khóa service nào lên máy nào; chốt sau đo thử | B11 |

Không bắt buộc mỗi dòng phải là một hình riêng; có thể là bảng, đặc tả hoặc ADR nếu biểu đạt rõ hơn. Cần đủ nội dung, không chạy theo đúng số lượng sơ đồ.

## 5.3 Nội dung hoàn thiện theo từng lát dọc

| Bản vẽ | Làm khi nào |
|---|---|
| Biểu đồ lớp mức thiết kế | Khi bắt đầu code chính service đó |
| ERD chi tiết từng service | Hoàn thiện sau khi đã chốt quyền sở hữu/schema, trước migration của service đó |
| Biểu đồ trạng thái (vòng đời Đơn hàng, vòng đời Vé) | Khi cài đặt vòng đời đó |
| C4 L3/component | Chỉ cho service cần giải thích cấu trúc bên trong |
| Wireframe giao diện | Song song, không chặn backend |
| Biểu đồ use case, đặc tả use case | Đã có từ Giai đoạn 3 — chỉ hoàn thiện |
| Biểu đồ hoạt động nghiệp vụ | Đã có từ Giai đoạn 2 — chỉ hoàn thiện |

Lý do chung: các nội dung này phụ thuộc chi tiết hiện thực và thường chỉ ảnh hưởng một service/client.

## 5.4 Cách vẽ để không tự trói mình

Nháp bằng **sơ đồ dạng mã** (PlantUML, Structurizr DSL) khi thiết kế còn thay đổi — sinh nhanh, sửa nhanh, vào git, so sánh được giữa các phiên bản. Chỉ dựng lại trên Visual Paradigm những sơ đồ thực sự vào báo cáo.

Điều này quan trọng hơn nó có vẻ: nếu mỗi lần đổi ý phải vẽ lại tay 30 phút, bạn sẽ **ngại đổi ý** — và ngại đổi ý trong giai đoạn thiết kế là đúng thứ nguy hiểm nhất.

---

# PHẦN 6 — ĐIỀU KIỆN SẴN SÀNG VÀ THEO DÕI

Không biến quy trình thành chuỗi cổng hình thức. Các giai đoạn có thể chồng lấn, nhưng một số phụ thuộc kỹ thuật cần được tôn trọng:

| Trước khi làm việc gì | Cần có tối thiểu | Vì sao |
|---|---|---|
| Tách/viết một service | Trách nhiệm, dữ liệu sở hữu, API/event chính | Tránh hai service cùng ghi bảng hoặc tích hợp theo giả định khác nhau |
| Hiện thực một Saga | Trạng thái, idempotency, timeout/thử lại/bù trừ và test lỗi | Đây là phần tốn chi phí nhất và bị giới hạn ≤3 luồng |
| Xây trợ lý chẩn đoán | Logging có cấu trúc, trace/correlation ID, masking và nguồn log | Drain/LLM không cứu được đầu vào thiếu ngữ cảnh hoặc lộ bí mật |
| Benchmark | Cấu hình triển khai thực tế, workload, dữ liệu kiểm tra và tiêu chí | Kết quả chỉ có nghĩa khi điều kiện chạy được công bố |
| Ba người hiện thực song song | Hợp đồng liên quan đủ ổn định và có người sở hữu thay đổi | Tránh sửa hai đầu không kiểm soát |

Hai ràng buộc phạm vi **không được vượt** nếu chưa có quyết định mới của chủ đồ án: ≤8 service nghiệp vụ và ≤3 Saga. Việc dùng 2 EC2 cũng đã chốt; **cách bố trí chưa chốt** và được quyết định sau khi so sánh/đo thử.

Khi theo dõi tiến độ, nhóm có thể dùng lịch tuần bình thường. Mỗi lần rà soát chỉ cần trả lời:

1. Đầu ra cốt lõi nào đã hoàn thành và bằng chứng ở đâu?
2. Quyết định nào đang mở hoặc đã đổi và có cần ADR không?
3. Rủi ro/tích hợp nào đang ảnh hưởng bước tiếp theo?
4. Phần báo cáo nào có thể cập nhật ngay từ kết quả vừa có?

---

# PHẦN 7 — CÁI GÌ CHƯA CÓ TRONG TÀI LIỆU NÀY

| Còn thiếu | Sẽ bàn ở |
|---|---|
| **Danh sách service cụ thể** — phản biện đề xuất *Event, Inventory, Order, Payment, Ticket, Notification* | Lượt sau |
| **Ngăn xếp hạ tầng đầy đủ** — Grafana, Loki, Tempo, Prometheus và những thứ chưa biết là cần | Lượt sau |
| **Kafka: có cần hay không** — kèm lập luận và phương án thay thế | Lượt sau |
| Bố cục/số chương báo cáo chính thức | Xác nhận với cô Liên và mẫu ĐATN hiện hành; sau đó ánh xạ mạch nội dung ở Phần 2 |
| Cơ sở lý thuyết chi tiết | Viết song song từ các quyết định thực sự dùng, không đặt chỉ tiêu trang |
| Hai câu nghiệp vụ để ngỏ: chính sách hoàn tiền · tỉ lệ giữ lại phòng hoàn tiền | Giai đoạn 2, trong phân tích dòng sự kiện miền |
| Vị trí aggregate tồn kho vé (3 phương án ở B5.5) | Giai đoạn 2, là ADR kiến trúc đầu tiên |
| Workflow chi tiết của context builder, prompt/đầu ra LLM và bảng AI cần lưu | Giai đoạn 4, phiếu B18; chỉ chốt schema sau khi workflow rõ |
| Bố trí 2 EC2 | Sau sơ đồ container/deployment ứng viên và một lần đo thử sơ bộ |

---

## Nhật ký sửa đổi

| Ngày | Sửa gì | Lý do |
|---|---|---|
| 2026-08-08 | Bản đầu | Hợp nhất tài liệu chủ với ba tầng |
| 2026-08-08 | Bỏ lịch theo tuần, thay bằng cổng phụ thuộc · mở dải mã lên B1–B19 và đưa nhánh trợ lý sửa lỗi vào cả 7 giai đoạn · bỏ hướng so sánh monolith–microservices · thu hẹp phạm vi tách service về `core-service` | Hai mâu thuẫn được chỉ ra: mốc tuần 8 không đạt được theo chính lịch của nó, và B16–B18 nằm ngoài dải mã đã tuyên bố nên nhánh AI bị xây mà không được đo |
| 2026-08-08 | Đổi cổng cứng thành điều kiện sẵn sàng; bỏ check-in offline/cây tiện ích/đối chứng bắt buộc; cập nhật schema độc lập, khảo sát công khai, Drain→context→LLM và bố trí EC2 còn mở | Phù hợp các quyết định mới và giảm thủ tục quá mức cho ĐATN đại học |
