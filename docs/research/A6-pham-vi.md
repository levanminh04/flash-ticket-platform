# A6 — Phạm vi

- **Phiên bản:** `A6-v0.4`
- **Phê duyệt từng phần, 2026-08-29** (`GOV-054`): **§1.3 và §2.1 đã được Lê Văn Minh duyệt.** Phần còn lại của tài liệu **chưa** duyệt. `A6-OPEN-07` đóng tại `RES-049`; bốn dòng `OPEN` còn lại ở §5 **không dòng nào chặn `B11`** — xem cột *Gate xử lý* của từng dòng.
- **Trạng thái:** `DRAFT` — viết lại theo thư định hướng nguyên văn; chưa được duyệt
- **Người duyệt:** — (chờ Lê Văn Minh)
- **Ngày duyệt:** —
- **Đầu vào:** [`docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`](../evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md) — nguyên văn, `FACT`; `A5-doi-tuong-nghien-cuu.md` (**`A5-v0.3`**, `DRAFT` — lời khai này trước 2026-08-28 ghi `A5-v0.3`, một phiên bản cũ hơn bản đang có); Phiếu A6 tại `docs/tang-a-phuong-phap-nghien-cuu.md` §4; `A8`, `A10` cho bộ dữ liệu và tập phương pháp
- **Đi vào báo cáo:** Chương Mở đầu — mục *Đối tượng và phạm vi nghiên cứu*, ngay sau `A5`
- **Ràng buộc:** khai **nghiên cứu đến đâu**. Không gắn ngưỡng số. Không sửa `A1`, `A2`, `A4`, `B2`–`B8`.

> **Vì sao có `A6-v0.2`.** Bản `v0.1` dựng khung "hai khối song song": khối nghiên cứu RCA ở Vòng 1 và khối nghiệp vụ vé ở Vòng 2, tách rời nhau. `DH-MT1` bác bỏ cách chia đó — hệ thống đặt vé nằm **trong** mục tiêu đầu tiên của đề tài. Bản này giữ cấu trúc ba vòng mà Tầng A yêu cầu, nhưng cắt đường ranh ở chỗ khác: **cái gì được đo sâu** so với **cái gì chỉ cần chạy được**.

---

## 1. Vòng 1 — Phạm vi nghiên cứu

Đào sâu, có đo đạc, có phân tích đánh đổi. Sáu mục dưới đây là **đúng sáu mục cô liệt kê**, không thêm không bớt.

| # | Nội dung | Mã trong thư | Bằng chứng sẽ thu |
|---|---|---|---|
| 1 | Mô hình đồ thị phụ thuộc của hệ giao dịch trực tuyến: nút là dịch vụ đặt vé, thanh toán, xác thực, cơ sở dữ liệu, API đối tác | `DH-MT1` | Danh sách nút và cạnh, **mỗi cạnh truy về một dòng `B4` hoặc `B5` §5.1** |
| 2 | Ánh xạ log giao dịch và trace lên đồ thị để phát hiện bất thường | `DH-MT2` | Đặc tả trường bắt buộc của log giao dịch; quy tắc một span thành một cạnh; tỷ lệ ca dựng được đồ thị trên bộ dữ liệu có trace |
| 3 | Cơ chế lan truyền và xếp hạng nguyên nhân gốc dựa trên bằng chứng log/trace | `DH-MT3` | Điểm của cơ chế theo bộ độ đo tại `A9` |
| 4 | Tích hợp AI hỗ trợ giải thích nguyên nhân và gợi ý bước xử lý — **bước cuối của cơ chế RCA**, đặc tả ở bộ RCA, chạy trong FlashTicket | `DH-MT4` | Ba tiêu chí định trước tại [`docs/research-rca/A9-do-do-thuc-nghiem.md`](../research-rca/A9-do-do-thuc-nghiem.md) §6: diễn giải trung thành với bằng chứng nhận được; bước kiểm tra có hữu ích không; số lần đưa ra bằng chứng không có thật |
| 5 | Bộ dữ liệu đã công bố để thực nghiệm giải pháp | `DH-DATA` | Bảng đối chiếu các bộ; lý do chọn bộ chính; ràng buộc loại trừ |
| 6 | Độ đo theo quy chuẩn; chạy so sánh giải pháp đề xuất với các giải pháp đã có **trên cùng bộ dataset** | `DH-DO` | Bảng so sánh kèm cột đối chứng ngẫu nhiên; một bảng chỉ một bộ dữ liệu |

**Sáu mục dùng chung một trục:** xếp hạng nguyên nhân gốc từ dấu vết vận hành của một hệ giao dịch trực tuyến. Không mục nào đo một trục thứ hai.

### 1.1 Ràng buộc cứng lên việc chọn bộ dữ liệu

`DH-MT2` và `DH-MT3` đều đòi bằng chứng **log và trace**. Một bộ dữ liệu chỉ có metric **không kiểm được hai mục tiêu này**. Đây là ràng buộc loại trừ, không phải sở thích:

| Bộ | Metric | Log | Trace | Kiểm được `DH-MT2`/`DH-MT3`? |
|---|---|---|---|---|
| `RE1` | ✔ | ✘ | ✘ | **Không** |
| **`RE2`** | ✔ | ✔ | ✔ | **Có** — bộ chính |
| `RE3` | ✔ | ✔ | ✔ | Có — trục đánh giá thứ hai, lỗi mức mã nguồn |
| Bộ Zenodo cô gửi (`DH-DATA-1`) | ✔ | ✘ | ✘ | **Không** — đây là nội dung câu hỏi số 1 gửi cô |

### 1.2 Hai lớp lỗi — trục đánh giá dài hạn

`RE2` chứa lỗi tài nguyên và mạng; `RE3` chứa lỗi mức mã nguồn. Mục tiêu dài hạn là cơ chế phủ được cả hai lớp:

```
              Cơ chế của nhóm
                     │
             chẩn đoán đa nguồn
                     │
         ┌───────────┴───────────┐
         ↓                       ↓
        RE2                     RE3
  lỗi tài nguyên/mạng     lỗi mức mã nguồn
         └───────────┬───────────┘
                     ↓
        Đánh giá chéo hai lớp lỗi
```

`RE3` **chưa chạy trong vòng này** nhưng có mặt trong phạm vi như trục đã định trước. Lớp lỗi mức mã nguồn gần với lỗi nghiệp vụ của hệ đặt vé hơn lỗi hạ tầng, nên đây cũng là chỗ nối tự nhiên về sau.

### 1.3 Lộ trình tích hợp lên FlashTicket

| Mức | Nội dung | Khi nào |
|---|---|---|
| **1 — Mô hình hóa** | Dựng mô hình đồ thị từ `B4`/`B5`/`B7`; đặc tả ánh xạ log/trace; thiết kế cơ chế | Vòng này. Đủ cho `DH-MT1` vì cô viết *"**Xây dựng mô hình** đồ thị"* |
| **2 — Chạy thật trên hệ nhà** | Gắn dấu vết thật, dựng đồ thị từ trace thật, cơ chế chạy, lớp AI diễn giải | Sau `B11`–`B14`, khi hệ thống chạy |
| **3 — Chấm điểm trên hệ nhà** | Thêm harness chèn lỗi và ghi nhãn nguyên nhân thật để tính được độ đo trên chính FlashTicket | Nếu còn sức. Cô **không** yêu cầu mức này |

Điều kiện kỹ thuật của từng mức và các ràng buộc phải giữ ở `B9`–`B16` ghi tại [`docs/research-rca/R0-boi-canh-va-rang-buoc.md`](../research-rca/R0-boi-canh-va-rang-buoc.md) §3 và §4.

---

## 2. Vòng 2 — Phạm vi sản phẩm

Làm chạy được, kiểm thử chức năng. Đây là phần FlashTicket **không được đo sâu** trong đồ án — nó vẫn phục vụ sáu mục ở §1, chỉ là không mang bằng chứng nghiên cứu.

> **Vì sao sửa cách nói này** (`A6-v0.3`). Bản `v0.2` viết *"không phục vụ trực tiếp sáu mục ở §1"*. Câu đó **tự mâu thuẫn với §1 mục 1**: nút và cạnh của đồ thị `DH-MT1` lấy từ `B4`, `B5` §5.1 và `B7` — tức chính hồ sơ nghiệp vụ ở bảng dưới. Ranh giới thật giữa hai vòng không phải *có phục vụ hay không*, mà là **mức kiểm chứng**: Vòng 1 đo và phân tích đánh đổi, Vòng 2 chỉ cần chạy được và kiểm thử chức năng.

| Nhóm | Nội dung | Hồ sơ |
|---|---|---|
| Phân tích nghiệp vụ | Từ điển miền, quy trình, bản đồ sự kiện, bounded context, use case, aggregate và bất biến, yêu cầu | `B2`–`B9` đều `APPROVED` ngày 2026-08-27 (`GOV-033`), sau vòng `RES-034` gỡ thiết kế trợ lý cũ. Lượt duyệt trước là `B2`–`B4` ngày 2026-08-22 (`GOV-023`). **Nội dung nghiệp vụ bán vé không đổi một chữ qua cả hai vòng** |
| Kiến trúc và thiết kế đích | Phương án kiến trúc, ADR, sở hữu dữ liệu và schema, hợp đồng API/sự kiện, sequence các luồng tranh chấp | `B11`–`B14` |
| Hiện thực | Web người mua và nhà tổ chức; ứng dụng di động cho check-in trực tuyến; các luồng giữ chỗ, thanh toán, phát hành vé, kiểm soát vào cửa | Giai đoạn 5 |
| Vận hành và dấu vết | Chuẩn logging có cấu trúc, mã tương quan, khử/che dữ liệu nhạy cảm | `B16` — **là đầu vào của `DH-MT2`**, nên nằm ở ranh giới hai vòng |

### 2.1 Mức kiểm chứng của Vòng 2

Kiểm thử chức năng, cộng một tập **tiêu chí nghiệm thu có số** mà file định hướng đồ án yêu cầu (`docs/boi-canh-va-mong-muon.md` §3): không bán vượt vé dưới tải cao · một vé không check-in thành công nhiều lần · một giao dịch thanh toán chỉ ghi nhận một lần khi callback lặp · độ trễ và thông lượng trên cấu hình công bố.

Các số này vào chương Kiểm thử với tư cách **tiêu chí nghiệm thu sản phẩm**, không dùng để trả lời câu hỏi nghiên cứu ở §1.

**Hai mục được chốt thêm ngày 2026-08-29, không phải tiêu chí có số:**

| Mục | Mức | Nghiệm thu bằng | Quyết định |
|---|---|---|---|
| **Kiểm quyền theo vai trò và quan hệ sở hữu** | Bắt buộc — nghĩa vụ mặc định, không xếp cao thấp | **Kiểm thử chức năng**, đúng mức kiểm chứng mà Vòng 2 tự khai ở đầu §2. Không đặt ngưỡng số, không thành mục tiêu nghiên cứu — xem `A3` §4 | `RES-048` |
| **Hệ thống giám sát: dashboard thời gian thực, cảnh báo tự động** | **Thấp — thừa thời gian thì làm** | Chạy được là đủ. **Không dùng làm điều kiện kết luận** trục nghiên cứu hay phạm vi sản phẩm thành công hay thất bại, cùng cách xử lý mà `docs/boi-canh-va-mong-muon.md` §3 đã dành cho CI/CD | `RES-049` |

> **Vì sao mục giám sát xuất hiện muộn.** `docs/boi-canh-va-mong-muon.md` §3 liệt **năm** gạch tiêu chí nghiệm thu; bốn tiêu chí có số ở trên cùng gạch CI/CD ở §3.2 mới phủ **bốn**. Gạch giám sát bị rơi lặng — không được xếp vào phạm vi mà cũng không bị loại ra — cho tới khi `A6-OPEN-07` phát hiện ngày 2026-08-29. Nay đã có chỗ. ⚠️ **Đây là mong muốn đã ghi của chủ đồ án được khôi phục, không phải phạm vi mở rộng mới.**

---

## 3. Vòng 3 — Ngoài phạm vi

### 3.1 Ngoài phạm vi nghiên cứu

| Mục | Lý do |
|---|---|
| Đề xuất một bộ dữ liệu chuẩn hoặc benchmark mới | `DH-DATA` yêu cầu **dùng** bộ đã công bố, không tạo bộ mới |
| Tự cài lại phương pháp không có trong tập cài đặt tham chiếu | Rủi ro tiến độ, không tạo giá trị nghiên cứu (`A10` §3.1) |
| Cài và vận hành các hệ sinh dữ liệu (Online Boutique, Sock Shop, Train Ticket) | Dữ liệu đã thu sẵn; không cần chạy hệ thống (`A7` §2) |
| Phương pháp cần huấn luyện mô hình học sâu | Làm mất tính đồng nhất điều kiện so sánh (`A10` §3.3) |
| Dữ liệu ngoài lĩnh vực giao dịch trực tuyến | Phần lớn ca lỗi của `DH-DATA-2` thuộc hệ xử lý nước (`A8` §2.2) |
| Chấm điểm trên chính FlashTicket trong vòng này | Mức 3 ở §1.3; cô không yêu cầu |

### 3.2 Ngoài phạm vi sản phẩm

| Mục | Lý do |
|---|---|
| Check-in offline | Đã chốt bỏ (`docs/boi-canh-va-mong-muon.md` §9) |
| Hoàn tiền một phần, chargeback, tranh chấp | Đã chốt bỏ |
| Chuyển nhượng vé, định giá động, đa tiền tệ, tích điểm | Đã chốt bỏ |
| Hủy đồng thời nhiều sự kiện | Đã chốt bỏ; hủy **một** sự kiện vẫn trong phạm vi |
| Đăng nhập qua nhà cung cấp mạng xã hội; thu hồi role | Đã chốt bỏ |
| Pipeline CI/CD | Phần hỗ trợ, không phải trục nghiên cứu (`docs/quy-trinh-lam-viec.md` Phần 1 mục 9) |
| Chất lượng giao diện và trải nghiệm người dùng | `A3` §4 cố ý không đặt thành mục tiêu |
| Người dùng thật và lưu lượng sản xuất | Nhóm không có điều kiện đó |

---

## 4. Giới hạn hạ tầng, dữ liệu, thời gian

| Loại | Nội dung |
|---|---|
| Hạ tầng sản phẩm | 2 máy EC2 `m7i-flex.large` — 2 vCPU / 8 GiB **mỗi máy**, hai tài khoản AWS rời. Bố trí chưa chốt, chờ `B11` |
| Máy chạy thực nghiệm | i5-1240P 12 nhân/16 luồng, 15,7 GB RAM, ổ D còn 73,3 GB. Cần thêm distro Ubuntu trên WSL2 và Python 3.12 (hiện 3.9.13) |
| Rủi ro dữ liệu | `RE2` có log và trace nên nặng hơn `RE1` nhiều. Thư viện yêu cầu dữ liệu ở dạng `pandas.DataFrame`; nạp trace cỡ chục triệu span vào 15,7 GB RAM là rủi ro thật. **Phải đo trước khi cam kết** — `A8-OPEN-05` |
| Trần kiến trúc | ≤ 8 service nghiệp vụ, ≤ 3 luồng Saga; đánh giá tại `B10`/`B11` |
| Thời gian | Hạn nộp 14/12/2026. Mốc gần: `DH-MOC` — báo cáo sau 2 tuần rồi cô hướng dẫn tiếp |

**Giới hạn suy rộng phải công bố:** số so sánh ở §1 mục 6 thu trên bộ dữ liệu benchmark công khai. Cho tới khi đạt mức 3 ở §1.3, không được suy rộng thành khẳng định về hiệu quả chẩn đoán trên chính FlashTicket.

---

## 5. Vấn đề `OPEN`

| ID | Vấn đề | Chủ sở hữu | Gate xử lý |
|---|---|---|---|
| `A6-OPEN-01` | Cô chưa xác nhận cách đặt phạm vi này; báo cáo ở mốc `DH-MOC` là nơi trình bày | Giảng viên hướng dẫn | Sau mốc 2 tuần |
| `A6-OPEN-03` | Nhóm phương pháp dùng LLM liên quan `DH-MT4` nhưng đang để ngoài thực nghiệm vòng này | Lê Văn Minh | Sau `E1` |
| `A6-OPEN-04` | Chưa gắn ngưỡng số cho mục nào; kế thừa `A3-OPEN-01` | Lê Văn Minh | Sau `B9`/`B10` |
| `A6-OPEN-06` | Số ca lỗi của `RE2` trên riêng Online Boutique chưa xác minh; `A8` chỉ ghi 270 ca trên ba hệ | Người viết báo cáo | Trước khi đưa số vào báo cáo |
| `A6-OPEN-07` | **ĐÃ ĐÓNG** ngày 2026-08-29 tại `RES-049`: Lê Văn Minh chốt tiêu chí giám sát ở **mức Thấp — thừa thời gian thì làm**, nghiệm thu bằng *chạy được*, không dùng làm điều kiện kết luận. Đã ghi vào §2.1. *Nội dung gốc:* **Tiêu chí *“Hệ thống giám sát: dashboard thời gian thực, cảnh báo tự động”* của `docs/boi-canh-va-mong-muon.md` §3 không xuất hiện ở §2.1 lẫn §3.1/§3.2 — `A6` không xếp nó vào phạm vi và cũng không loại nó ra.** Bốn tiêu chí đang ghi ở §2.1 chỉ phủ bốn trong năm gạch đầu dòng của §3; gạch CI/CD đã được §3.2 loại có lý do, riêng gạch giám sát bị rơi lặng. Cần quyết: đưa vào Vòng 2, loại ra Vòng 3, hay coi là đã được `ASR-09` phủ. | Lê Văn Minh | Đã đóng tại `RES-049` |

`A6-OPEN-02` (trợ lý thuộc vòng nào) và `A6-OPEN-05` (thư chưa được lưu) đã đóng: lớp giải thích của `DH-MT4` thuộc **Vòng 1** và được đặc tả ở bộ tài liệu RCA (`RES-034`), còn thư đã là tạo tác trong repo.

> **Đồng bộ với `A3`, 2026-08-28.** Kết luận *"lớp giải thích thuộc Vòng 1"* của dòng trên **đã được `A3` áp theo**: `MT-5` — mục tiêu đo đúng lớp giải thích ấy — chuyển từ bảng Vòng 2 sang bảng Vòng 1 tại `RES-045`. Trước đó `A3` vừa để `MT-5` ở Vòng 2 vừa khai kế thừa `A6-OPEN-02`, tức kế thừa một điểm mở mà chính tệp này đã khép. Hai phiếu nay nói cùng một điều. **`MT-4` ở lại Vòng 2** có chủ ý, khớp bảng §2 của tệp này vốn xếp chuẩn logging và dấu vết ở ranh giới hai vòng với ghi chú *"là đầu vào của `DH-MT2`"*.

---

## 6. Phép tự kiểm

- [x] Đủ ba vòng theo mẫu Tầng A.
- [x] Vòng 1 là **đúng sáu mục cô liệt kê**, mỗi mục dẫn về một mã `DH-*`.
- [x] Mỗi mục Vòng 1 có bằng chứng sẽ thu.
- [x] Sáu mục dùng chung một trục; không có trục thứ hai.
- [x] Nêu ràng buộc loại trừ bộ dữ liệu chỉ có metric, kèm bảng đối chiếu.
- [x] Ghi lộ trình ba mức và nói rõ cô không yêu cầu mức 3.
- [x] Mục Vòng 3 nào cũng có lý do cụ thể.
- [x] Ghi rủi ro bộ nhớ khi nạp `RE2` và nêu rõ phải đo trước.
- [x] Không điền ngưỡng số nào.
- [x] Không sửa `A1`, `A2`, `A4`, `B2`–`B8` từ tệp này; bảng §2 chỉ **ghi lại** trạng thái duyệt hiện hành của chúng.
- [x] Ranh giới hai vòng phát biểu theo **mức kiểm chứng**, không phát biểu theo *"có phục vụ mục tiêu hay không"* — cách sau mâu thuẫn với §1 mục 1.
- [ ] Cô xác nhận cách đặt phạm vi.
- [ ] Lê Văn Minh duyệt.

---

## 7. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A6-v0.4` | 2026-08-27 | Nói rõ lớp giải thích của `DH-MT4` được đặc tả ở bộ RCA và chạy trong FlashTicket, sau khi thiết kế trợ lý cũ bị gỡ khỏi bộ hệ thống (`RES-034`); đổi bộ tiêu chí ở mục 4 Vòng 1 sang ba tiêu chí của `A9` §6. **Ba vòng phạm vi và sáu mục Vòng 1 không đổi** | Lan truyền `RES-034` |
| `A6-v0.3` | 2026-08-26 | §2 sửa câu mở đầu: ranh giới hai vòng là **mức kiểm chứng**, không phải *"không phục vụ trực tiếp §1"* — cách nói cũ mâu thuẫn với §1 mục 1; đồng bộ trạng thái `B5`–`B8` sang `REVIEW_READY` theo `RES-032`; thêm một ô tự kiểm | Sửa mâu thuẫn nội tại |
| `A6-v0.2` | 2026-08-25 | Viết lại theo thư nguyên văn. **Bỏ khung "hai khối song song"**; Vòng 1 nay là đúng sáu mục của cô, mỗi mục dẫn về mã `DH-*`; thêm ràng buộc loại trừ bộ chỉ có metric; thêm hai lớp lỗi `RE2`/`RE3`; thêm lộ trình ba mức; đóng `A6-OPEN-02` và `A6-OPEN-05`, mở `A6-OPEN-06` | Sửa sau khi có bằng chứng gốc |
| `A6-v0.1` | 2026-08-24 | Bản đầu, khung hai khối song song, viết khi chưa có thư trong repo | Tạo mới |
