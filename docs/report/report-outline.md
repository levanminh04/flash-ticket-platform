# Khung nội dung báo cáo

**Đề tài:** *"Chẩn đoán nguyên nhân gốc sự cố giao dịch trực tuyến bằng đồ thị phụ thuộc"* — tên do giảng viên đặt, `DH-TEN`.

**Trạng thái:** Khung nội dung sống. Chưa đánh số chương cho tới khi nhận mẫu ĐATN hiện hành.

**Phiên bản khung:** `v4` — cập nhật ngày 2026-08-28 sau vòng duyệt `B8`/`B9`/`B10` và ba quyết định về mục tiêu nghiên cứu (`GOV-043`, `RES-045`–`RES-047`, `GOV-041`, `RES-046`). `v3` dựng ngày 2026-08-25 sau khi thư định hướng được lưu nguyên văn tại [`docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`](../evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md).

> **Phân biệt hai tài liệu.** Tệp này là khung của **quyển báo cáo cuối** (nộp 14/12/2026). Báo cáo nộp cô ở mốc hai tuần (`DH-MOC`) là một tài liệu khác, bám đúng sáu mục cô liệt kê, nằm tại `bao-cao-2-tuan-2026-09-05.md`.

---

## Nguyên tắc chi phối

**Một đề tài, một trục.** `DH-MT1` đặt hệ thống của nhóm — các dịch vụ đặt vé, thanh toán, xác thực, cơ sở dữ liệu, API đối tác — vào mục tiêu đầu tiên. Khối phân tích nghiệp vụ `B2`–`B8` **không phải phần phụ**: nó là nguồn dựng đồ thị.

**Hai thứ không được lẫn:**

| | Chỗ cơ chế được xây và chạy | Chỗ lấy số so sánh |
|---|---|---|
| Ở đâu | FlashTicket Platform | Bộ dữ liệu ca lỗi đã công bố |
| Vì sao | `DH-MT1` | `DH-DATA`: chỉ bộ công bố mới có nhãn nguyên nhân thật |

**Giới hạn phải công bố:** cho tới khi đạt mức 3 của lộ trình tại `A6` §1.3, không suy rộng kết quả trên bộ công bố thành khẳng định về hiệu quả trên chính FlashTicket.

---

## Bối cảnh và vấn đề

- Bối cảnh hệ giao dịch trực tuyến kiến trúc phân tán; hậu quả nghiệp vụ khi phối hợp sai.
- Chi phí xác định nguyên nhân khi dấu vết của một giao dịch nằm rải ở nhiều thành phần.
- Hiện trạng quy trình chẩn đoán thủ công.
- Giới hạn của khảo sát hệ thống bên ngoài.

**Nguồn:** `A1-v0.2` (mạch năm nước), `A2-v0.2`, `B1`.

> ✅ `A1-v0.2`, `A2-v0.2`, `A4-v0.2` đã tái baseline theo `DH-TEN` (`RES-032`) và **đã được Lê Văn Minh duyệt ngày 2026-08-27** (`GOV-033`). Khung này trích được và khóa được câu chữ. *(Dòng này trước 2026-08-28 còn ghi ba phiếu ở `REVIEW_READY` — lời khai lỗi thời.)*

## Đối tượng, phạm vi, mục tiêu, phương pháp

Đối tượng nghiên cứu · phương tiện · khách thể · ba vòng phạm vi · mục tiêu từng vòng · câu hỏi nghiên cứu · phương pháp thu bằng chứng.

**Nguồn:** `A5`, `A6`, `A3`, `A4`.

## Cơ sở lý thuyết

Chỉ trình bày khái niệm thực sự được dùng trong một quyết định hoặc một phép đánh giá.

- Bốn nhóm tên riêng không được lẫn: hệ thống thử nghiệm · bộ dữ liệu · thuật toán · độ đo.
- Bốn họ phương pháp chẩn đoán, và họ nào thật sự lan truyền trên đồ thị.
- Bộ độ đo xếp hạng, cách đọc, mức sàn ngẫu nhiên.
- Quan hệ log – span – trace – metric.
- Nền tảng nghiệp vụ: phân rã theo miền, nhất quán phân tán, idempotency.

**Nguồn:** `A7`, `A9`, `A10` trong `docs/research-rca/`; `T-01`–`T-05`.

## Phân tích và thiết kế hệ thống

- Tác nhân, use case, đặc tả use case.
- Quy trình nghiệp vụ có nhánh thất bại.
- Từ điển miền, mô hình miền, aggregate và bất biến.
- Yêu cầu chức năng, ASR, kịch bản chất lượng.
- C4 System Context và Container.
- Lập luận ranh giới context, rồi lập luận riêng cách gộp/tách thành service vật lý.
- Sở hữu dữ liệu, schema độc lập, ERD đích.
- API, sự kiện, các luồng giao dịch xuyên service.
- Check-in trực tuyến, idempotency, xử lý cạnh tranh.
- Chuẩn logging, mã tương quan, khử nhạy cảm.
- Kiến trúc triển khai trên hai máy.
- UML theo đúng mục đích từng hình.

**Nguồn:** `B2`–`B8`; `B10`/`B11` cùng ADR; `B12`–`B16`.

> Phần này vừa là thiết kế sản phẩm, vừa là **đầu vào của `DH-MT1` và `DH-MT2`**. Ràng buộc để nó không chặn nhánh chẩn đoán: [`docs/project/lien-ket-rca.md`](../project/lien-ket-rca.md).

## Mô hình đồ thị phụ thuộc — `DH-MT1`

- Vì sao đồ thị ngữ nghĩa của `B5` §5 **không phải** đồ thị phụ thuộc vận hành.
- Quy tắc chuyển đổi, và bằng chứng cho từng cạnh.
- Nút hạ tầng và hệ ngoài.
- Ánh xạ sang service vật lý sau khi `B11` chốt.

## Ánh xạ log giao dịch và trace lên đồ thị — `DH-MT2`

Trường bắt buộc của một dòng log giao dịch · quy tắc một span thành một cạnh · cách gắn tín hiệu bất thường vào nút hay cạnh.

## Cơ chế lan truyền và xếp hạng — `DH-MT3`

Bốn họ phương pháp và họ nào lan truyền trên đồ thị · cơ chế của nhóm · thiết kế so sánh.

**Nguồn:** `A10` §1, §3.

> Không viết "đề xuất phương pháp mới" khi chưa có kết quả thực nghiệm chống lưng.

## Tích hợp AI hỗ trợ giải thích — `DH-MT4`

Đầu vào là danh sách nghi phạm đã xếp hạng kèm bằng chứng; đầu ra là diễn giải nguyên nhân và bước xử lý gợi ý.

**Ranh giới chỉ đọc** viết theo `NFR-08` và `ASR-12`, không dẫn `PRJ-002` như một thiết kế: `PRJ-002` gắn với **trợ lý cũ** đã bị gỡ, ba giới hạn của nó được chép sang `RES-034` và ràng buộc chỉ đọc nay viết trung tính cho **mọi thành phần quan sát hoặc chẩn đoán**. `ASR-12` thêm một vế phải giữ: quyền đó **thực thi bằng phân quyền thật, không phải bằng quy ước**.

Ba giới hạn còn nguyên hiệu lực, phải nêu trong báo cáo: **không cam kết loại bỏ việc tái hiện lỗi · không tự kết luận nguyên nhân cuối cùng · không tự sửa hệ thống**.

## Hiện thực

Thành phần đã xây · phương pháp phát triển áp dụng thực tế · cách hiện thực các quyết định đích · sai lệch có ý nghĩa so với thiết kế đã chấp nhận.

Mô tả trạng thái cuối, không kể lịch sử chuyển file, package hay commit.

## Thực nghiệm và đánh giá

**Hai mục tách bạch, không trộn số.**

### Kết quả chẩn đoán — `DH-DO`

- Cấu hình chạy, bộ dữ liệu, số ca lỗi, điều kiện tái lập.
- Bảng so sánh: **một bảng cho một bộ dữ liệu**, mọi phương pháp cùng bộ cùng độ đo.
- **Cột đối chứng ngẫu nhiên trong mọi bảng.**
- Mỗi số ghi rõ do nhóm chạy hay trích nguồn nào.
- Hai lớp lỗi: tài nguyên/mạng và mức mã nguồn.
- Kết quả không đạt và các ca thất bại.

### Nghiệm thu sản phẩm

Kiểm thử chức năng và hợp đồng · không bán vượt vé dưới tải cao · một vé không check-in thành công nhiều lần · thanh toán chỉ ghi nhận một lần khi callback lặp · độ trễ và thông lượng trên cấu hình công bố.

> **Ba mục tiêu `MT-1`, `MT-2`, `MT-3` được báo cáo Ở ĐÂY, không ở phần đóng góp nghiên cứu** (`RES-046`, 2026-08-28). Câu chữ và ý nghĩa của chúng không đổi; chỉ vai trò trong báo cáo đổi. Lý do viết thẳng vào báo cáo được: việc hệ đặt vé không bán vượt vé, không tính tiền hai lần, phục hồi được sau lỗi từng phần là **yêu cầu đúng đắn mà mọi hệ bán vé nghiêm túc đều phải có** — giữ chúng ở vai đóng góp buộc nhóm phải chứng minh cái không mới.

**Nguồn:** `A9` cho độ đo; `B15` cho kiểm thử; `A3` §2 cho ba mục tiêu.

## Kết luận và hướng phát triển

Đối chiếu bốn mục tiêu của `DH-MT1`–`DH-MT4` với kết quả · đối chiếu riêng hai nhóm mục tiêu của `A3` — `MT-R1`–`MT-R4` và `MT-5` cho vòng nghiên cứu, `MT-1`–`MT-4` cho vòng sản phẩm, **không trộn vào cùng một bảng** · đóng góp kỹ thuật và đóng góp từng thành viên · hạn chế, giới hạn suy rộng, hướng phát triển.

**Giới hạn tự nhận phải công bố, không được bỏ:**

- **Mức độ hoàn thiện của việc khử/che trường nhạy cảm trước khi dữ liệu rời phạm vi kiểm soát** (`GOV-041`, 2026-08-28). Yêu cầu này còn nguyên hiệu lực ở `B8`/`B9` và vẫn phải kiểm chứng, nhưng được xếp mức **Thấp** ở bảng ưu tiên kiến trúc, nên nó không chỉ đạo việc chọn phương án. Chủ đồ án chọn **công bố thẳng đây là điểm cần cải thiện** thay vì giấu.
- **Giới hạn suy rộng của số đo chẩn đoán** — xem *Nguyên tắc chi phối* ở đầu tệp này.
- **Cỡ tập ca lỗi thật trên hệ nhà**: baseline hiện có đúng một ca có đáp án gốc (`B9-OPEN-02`).

---

## Quy tắc viết — trả lời phê bình `DH-PB`

Cô phê bình bản trước: *"cảm tưởng bản thảo em gửi đây do AI viết còn khá mơ hồ, phần tổng hợp của các em cũng chưa thể hiện rõ liên kết logic giữa các nội dung đưa ra."*

1. Mỗi khẳng định có **một nguồn hoặc một con số**.
2. Mỗi phần mở đầu bằng một câu nói rõ **nó nối vào phần trước thế nào**.
3. Phân biệt tuyệt đối phần **nhóm tự chạy** với phần **trích từ bài báo**.
4. Ưu tiên bảng và hình **của chính hệ thống nhóm** hơn văn xuôi khảo sát.
5. Cấm cụm mơ hồ kiểu "có thể cải thiện đáng kể", "góp phần nâng cao".
6. Viết báo cáo **song song** với khảo sát và đánh giá, không đợi xong mới hồi tưởng.

## Điểm `OPEN` ảnh hưởng tới khung này

| ID | Ảnh hưởng |
|---|---|
| `A6-OPEN-01` | Cô chưa xác nhận cách đặt phạm vi |
| ~~`RES-032`~~ | **Không còn ảnh hưởng.** `A1`, `A2`, `A4` và chuỗi `B2`–`B9` đã được duyệt ngày 2026-08-27 (`GOV-033`); `B8-v0.12`, `B9-v0.7`, `B10-v0.5` được duyệt ngày 2026-08-28 (`GOV-043`). Câu chữ khóa được |
| `A3-OPEN-07` | `ASR-14` — kiểm quyền theo quan hệ sở hữu — **không truy về mục tiêu nào** và không nằm trong bốn tiêu chí nghiệm thu, dù là động lực kiến trúc mức Cao. Nếu không xử, chương Thiết kế sẽ có một động lực mà chương Mục tiêu không nhắc tới |
| `A3-OPEN-05` | Giảng viên **chưa xác nhận** `MT-R1`–`MT-R4`; nơi trình là báo cáo hai tuần |
| `A8-OPEN-04` | Bộ chính `RE2` đã chốt nội bộ (`RES-022`); còn chờ cô xác nhận |
| `A8-OPEN-06`, `A10-OPEN-05` | Chưa chốt **hệ nào trong `RE2`**; lựa chọn này quyết định mức sàn ngẫu nhiên, tức quyết định cách đọc mọi điểm số |
| `A10-OPEN-04` | Hướng đóng góp chưa chọn — chạy thực nghiệm trước |
| — | Số chương và mẫu trình bày chờ đề cương ĐATN |
