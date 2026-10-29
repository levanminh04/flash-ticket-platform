# Đối chiếu ba use case trong image với rule hệ thống — 2026-10-03

> **Hiện hành 29/10/2026 — GOV-156:** theo ảnh Đặt vé.png trên ClickUp, FE → Payment → Booking lấy PurchaseSnapshot; Ticket → Payment → Booking commit COMPLETED → Payment kết thúc Saga. Hướng start/result theo GOV-149/150 ở nội dung 03/10 bên dưới là lịch sử, đã được thay thế trong phạm vi này. Xem [đối chiếu mới](2026-10-29-clickup-booking-flow.md). B13/B14/B15-v0.3 REVIEW_READY; route/freeze/QR chưa tự động được duyệt.

- Phân lớp: `FORMATION`; bằng chứng nội bộ do người dùng cung cấp, không phải kiểm kê legacy.
- Người đối chiếu: Codex; ngày đọc: 2026-10-03. Chỉ kiểm ảnh/tài liệu, không chạy service hoặc cổng thanh toán.
- Yêu cầu: đọc ba UC đã vẽ, đặc biệt sequence đặt vé và hủy vé; cập nhật docs nếu khác.
- Xác nhận tiếp theo của Minh: **“Áp dụng cả hai theo hình”**, sau khi được trình hai thay đổi tại §3. Ghi riêng `GOV-149`, `GOV-150` trong [sổ quyết định](../../project/decision-register.md).
- Đề tài/nhiệm vụ hiện hành theo [DT18](../project-direction/2026-09-18-de-tai-va-nhiem-vu.md); trách nhiệm nhóm theo [roles](../../project/roles.md).

## 1. Nguồn ảnh đã đọc

| Nguồn | Tệp | Vai trò |
|---|---|---|
| IMG-BOOK-UC | [Biểu đồ UseCase.png](<../../../image/UseCase Đặt Vé/Biểu đồ UseCase.png>) | Đặt vé gồm đăng nhập, chọn sự kiện/ghế, thông tin người mua, thanh toán, phát hành; áp mã là extend tùy chọn |
| IMG-BOOK-SEQ | [Đặt vé.png](<../../../image/UseCase Đặt Vé/Đặt vé.png>) | 73 bước, từ giữ ghế tới xem vé; nhánh thành công |
| IMG-CANCEL-UC | [Biểu đồ UC.png](<../../../image/UseCase Hủy Đơn Đặt/Biểu đồ UC.png>) | Gửi yêu cầu hủy, kiểm điều kiện, giải phóng tài nguyên |
| IMG-CANCEL-SEQ | [Sequence.png](<../../../image/UseCase Hủy Đơn Đặt/Sequence.png>) | 11 bước, commit CANCELLED hoặc rollback khi đã ISSUING |
| IMG-VIEW-UC | [Biểu đồ UC.png](<../../../image/UseCase Xem Đơn Đặt/Biểu đồ UC.png>) | Tìm và xem chi tiết đơn |
| IMG-VIEW-SEQ | [Sequence.png](<../../../image/UseCase Xem Đơn Đặt/Sequence.png>) | 21 bước: danh sách/chi tiết từ Booking, vé từ Ticket |

Tệp class và draw.io khác trong image không được dùng để suy thêm boundary, schema hoặc rule của ba UC. Không sửa ảnh gốc trong lượt này.

### 1.1 Phiên bản ảnh đã đối chiếu

| Nguồn | Bytes | SHA-256 |
|---|---:|---|
| IMG-BOOK-UC | 152459 | `92cccf9cf2cea68708993c1d9504effd1f475e06dfd8f62da011a8a327efd00e` |
| IMG-BOOK-SEQ | 790934 | `d86fae0bdb29af1414744a278e8356bf9bc7890a3a0c26740f87f1cf730282f3` |
| IMG-CANCEL-UC | 96819 | `a6f8e581cf223df500e856fbfb5ab6e78d6b4df56bd18134b4e411734fb58c04` |
| IMG-CANCEL-SEQ | 141549 | `3d28c9e1199144b996ea910c3517c220463287ee59ece1003381f4dc1d7c7b81` |
| IMG-VIEW-UC | 82454 | `a3175dbf3f64130546afe414de0eb0e1abb931c40cabcd512cb7a263151aa558` |
| IMG-VIEW-SEQ | 229427 | `76a83d5086137432e2546d59217af8c5415d781cb6f426e73c8d5fdc3555fec5` |

Ảnh là tài sản local: `.gitignore` hiện có rule `image/`. Link đã kiểm trên máy này; clone repo chưa có ảnh sẽ cần đúng bộ nguồn với hash trên. Lượt này không đổi quy tắc Git hoặc công bố ảnh.

## 2. Bảng đối chiếu

Mỗi quan sát từ ảnh là `FACT` trong phạm vi hình. Chỉ hai thay đổi đã được Minh xác nhận ở §3 là `USER_CONFIRMED`; không biến việc ảnh bỏ qua một nhánh thành quyết định xóa nhánh đó.

| Nội dung | Bằng chứng ảnh | Rule trước cập nhật | Kết quả/xử lý |
|---|---|---|---|
| Phạm vi tên “Đặt vé” | IMG-BOOK-UC bao thanh toán/phát hành; IMG-BOOK-SEQ 1–73 | B6 tách UC-10, UC-11, UC-12, UC-14; xem lịch sử ở UC-28 | Khác mức gom hình, không mâu thuẫn nghiệp vụ. Giữ danh mục 30 UC, không gộp/xóa UC đã duyệt |
| Tạo đơn và giữ ghế | IMG-BOOK-SEQ 1–7: order/reservation/allocations, COMMIT, PENDING_PAYMENT/expiresAt | B13 §3; B14 §2.1–4 | Khớp: tạo và giữ nguồn cung nguyên tử, trả hạn chung; selection ghế chỉ là một biến thể SEAT_MAP/SEATED, không bỏ QUANTITY/STANDING |
| Áp voucher | IMG-BOOK-UC extend; IMG-BOOK-SEQ 8–14: usage HELD, cập nhật tiền | B6 UC-11; B13 §3; B14 §2.5 | Khớp; voucher tùy chọn, phải trước freeze; hình chưa vẽ alt/opt không làm voucher bắt buộc |
| Khởi tạo thanh toán | IMG-BOOK-SEQ 15–23: FE → Booking, freeze/commit, trả nhận yêu cầu, Booking → Payment | B13-v0.1 §3 route do Payment nhận; §4 Payment gọi Booking để freeze đầu tiên. B14-v0.1 §2.6 cùng hướng | **Khác; cập nhật theo GOV-149:** Booking nhận start, freeze và outbox cùng commit rồi trả 202, Payment tạo attempt từ snapshot |
| Poll URL/QR thanh toán | IMG-BOOK-SEQ 26–37: Payment tạo/lưu rồi FE đọc Payment theo orderId | B13-v0.1 PaymentStart có paymentUrl, nhưng PaymentView chỉ có attemptId/state, không trả URL để poll | **Thiếu; bổ sung DTO poll trong B13-v0.2.** Cách mã hóa/lưu QR độc lập là OPEN, không tự thêm cột QR |
| Ghi nhận thu và acceptance | IMG-BOOK-SEQ 39–52: verify, charge, WAITING_BOOKING, AcceptPayment, confirmation/WAITING_TICKETS | B13 §5; B14 §3 P1–P3 | Khớp: charge chưa phải confirmation; Payment điều phối, Booking chốt giữ chỗ/nguồn cung trước phát vé |
| Order ISSUING | IMG-BOOK-SEQ 46 chỉ ghi “order status”; IMG-CANCEL-SEQ 9 từ chối vì ISSUING | B12 enum; B14 §3 B2; GOV-131/136 | Hình đặt vé chưa ghi tên ở bước 46. Giữ ISSUING sau acceptance, không nhảy thẳng COMPLETED hoặc bỏ trạng thái |
| Phát đủ vé | IMG-BOOK-SEQ 53–56: IssueTickets(PurchaseSnapshot), TX Ticket/COMMIT | B13 IssueTickets; B14 §3 T1/T2 | Khớp ở happy path; giữ nguyên phát nguyên bộ, retry/idempotency, fence thất bại đã duyệt |
| Kết thúc đơn/Saga | IMG-BOOK-SEQ 57–66: Ticket → Booking, Booking COMPLETED/COMMIT → Payment, Saga COMPLETED/COMMIT | B13-v0.1 §5.2: Ticket → Payment → Booking. B14-v0.1 §3 P4: Saga COMPLETED trước Booking nhận result | **Khác; cập nhật theo GOV-150:** Ticket success tới Booking, completion bền của Booking mới cho Payment kết thúc Saga |
| Xem vé sau mua | IMG-BOOK-SEQ 67–73: FE → Ticket → Ticket DB | B13 GET orders/{id}/tickets; B6 UC-14/UC-28 | Khớp. Đọc bộ vé đã phát; ownership phải kiểm ở Ticket, không chỉ ở màn hình |
| Hủy đơn chưa acceptance | IMG-CANCEL-SEQ 1–7: Booking khóa/kiểm, release limit/voucher, COMMIT CANCELLED | B6 UC-13; B13 cancel PENDING_PAYMENT còn giữ chỗ; B14 §4 | Khớp; làm rõ transaction Booking gồm reservation/allocations/nguồn cung/limit/promotion, commit trước success |
| Từ chối hủy | IMG-CANCEL-SEQ 8–11: ROLLBACK vì ISSUING | B13 cancel sau chấp nhận tiền bị cấm | Khớp. Không suy rằng chỉ ISSUING bị cấm: COMPLETED cũng không cho buyer hủy. Freeze không tự cấm hủy nếu vẫn PENDING_PAYMENT đủ điều kiện |
| Xem danh sách/chi tiết đơn | IMG-VIEW-SEQ 1–14: Booking truy vấn đơn của buyer hiện tại | B6 UC-28; B13 §9 owner-filtered list và §3 detail | Khớp; thêm diễn giải B14 §6.1 và route danh sách B13. GET không tạo giao dịch hoặc thay trạng thái |
| Xem vé của đơn | IMG-VIEW-SEQ 15–21: FE đọc Ticket theo orderId | B13 Ticket API và quyền chủ đơn | Khớp; không buộc Booking JOIN Ticket DB hoặc gọi Ticket để đọc order detail |
| Nhánh không có trong ảnh | IMG-BOOK-SEQ chỉ happy path, không có expiry/late/duplicate/failure/refund/email | B6 UC-12/14/15; B13/B14 baseline | Giữ toàn bộ rule. Không coi hình là bằng chứng bỏ bù trừ, bảo mật, email hoặc kiểm lặp |

## 3. Hai quyết định được xác nhận và tác động

### GOV-149 — khởi tạo qua Booking và poll tại Payment

Luồng xác nhận: **Frontend → Booking đóng băng đơn → yêu cầu Payment tạo thanh toán; Frontend poll QR/URL ở Payment.**

- Dùng lại: PurchaseSnapshot, payment_frozen_at, snapshot version, Payment attempts/URL, outbox/inbox và idempotency đã có.
- Sửa: owner nhận API start từ Payment sang Booking; phản hồi start là nhận yêu cầu trước có URL; poll trả dữ liệu attempt/URL và kết quả request.
- Bỏ trong hướng mới: FE start trực tiếp ở Payment và chờ URL ngay trong response POST.
- Thêm ở hợp đồng CANDIDATE: StartPayment/receipt, message start-requested và trạng thái kết quả command để poll không chờ mãi khi request bị từ chối.
- Guard cũ giữ: attempt mới phải còn payable; command đến trễ kiểm lại Booking trước gọi cổng. Guard HTTP ngoài TX. Hủy sau guard vẫn phân xử ở acceptance/hoàn muộn, không hứa atomic xuyên DB.

### GOV-150 — Booking hoàn tất đơn trước Payment hoàn tất Saga

Luồng xác nhận: **Ticket → Booking ghi đơn COMPLETED → báo Payment kết thúc Saga.** Payment vẫn điều phối theo ADR-002.

- Dùng lại: atomic issuance, accepted charge, frozen snapshot, outbox/inbox, Saga WAITING_TICKETS/COMPLETED.
- Sửa: result thành công Ticket tới Booking; Booking ghi order + completion outbox cùng TX; Payment hoàn tất khi nhận completion khớp confirmation.
- Bỏ trong nhánh success mới: Payment hoàn tất Saga rồi mới gửi result tới Booking.
- Thêm ở hợp đồng CANDIDATE: OrderCompleted. Không thêm Saga/state/bảng hoặc đổi ownership.
- Nhánh FAILED giữ Ticket → Payment → Booking và refund theo baseline; ảnh không chốt thay đổi nhánh này. Order CANCELLED không bị result muộn hồi sinh.

Hai lựa chọn không sửa B11/ADR, bất biến B7, FR B8 hay phạm vi DT18. Phiên bản v0.2 của hợp đồng/sequence và tài liệu phụ thuộc ở REVIEW_READY; xác nhận hai hướng không tự phê duyệt từng DTO, tên topic hoặc toàn văn phiên bản mới.

## 4. Bản đồ tác động và thứ tự cập nhật

| Thứ tự | Tạo tác | Tác động |
|---|---|---|
| 1 | decision-register.md | Ghi GOV-149/150 nguyên tử, bằng chứng ảnh và câu xác nhận; giữ các quyết định cũ để truy lịch sử |
| 2 | B13-api-and-event-contracts.md | Sửa owner/receipt/poll/start message và hướng result/completion; v0.2 REVIEW_READY |
| 3 | B14-main-flow-sequences.md | Đồng bộ thứ tự commit, retry/recovery, hủy và truy vấn UC-28; v0.2 REVIEW_READY |
| 4 | diagrams/src/B14-01-order-payment-ticket-sequence.puml | Sơ đồ dẫn xuất theo hai hướng mới |
| 5 | B15-verification-plan.md | V21–V25 cho start/replay, completion, cancel, quyền đọc và command đến trễ; v0.2 REVIEW_READY; NOT RUN |
| 6 | implementation-readiness.md | Lát cắt 3 và trạng thái nguồn mới; READY-v0.2 REVIEW_READY |
| 7 | docs/README.md; project/implementation-status.md | Chỉ mục/trạng thái hiện hành, tách baseline phê duyệt 14/09 khỏi sửa 03/10 |
| 8 | Tệp bằng chứng này | Lưu nguồn ảnh, bảng đối chiếu, giả định/OPEN và kết quả kiểm tài liệu |

Ngoài phạm vi: ảnh/VP gốc, application code, SQL/Mongo/ERD, AWS, bộ RCA, B11 và ADR. Không dùng yêu cầu quan sát RCA để sinh hai thay đổi; cả hai bắt nguồn từ ảnh và xác nhận của Minh. Phép thử độc lập: bỏ nguồn RCA khỏi lượt này vẫn suy ra đúng hai thay đổi; không sửa bất biến hoặc phương pháp của bộ kia.

## 5. Chỗ ảnh cần chỉnh hoặc chưa thể chốt

- **FACT — bước 61 IMG-BOOK-SEQ:** mũi tên ACK từ Booking tới lifeline Ticket DB. Không được biến nó thành quyền truy DB xuyên service. Khi chỉnh hình, ACK phải thuộc giao nhận Ticket/consumer hoặc broker sau commit Booking; trong hợp đồng mới ghi rõ ACK consumer sau commit.
- **FACT — bước 46:** tên trạng thái sau acceptance chưa ghi; nên ghi ISSUING để nối với nhánh từ chối hủy bước 9.
- **FACT — bước 34 IMG-BOOK-SEQ:** COMMIT được vẽ trong đoạn GET QR. Truy vấn này không thay trạng thái nghiệp vụ; một read transaction không phải bước freeze/thu tiền.
- **CANDIDATE — biểu diễn luồng:** bọc áp voucher trong opt để khớp extend của hình UC. Hình mua vé là hình tổng hợp, không thay mã UC-10–14 trong docs.
- **OPEN — B13-OPEN-01:** dữ liệu QR thanh toán thực tế/định dạng/lưu độc lập, owner Tuấn/Minh. payment_url có sẵn trong B12; không có bằng chứng từ ảnh về một cột lưu ảnh/chuỗi QR riêng. Chốt adapter và tác động B12 trước sửa DDL.
- **Giả định được giữ trong phạm vi xác nhận:** luồng đặt vé ở ảnh là nhánh success; FAILED giữ baseline; các nhãn POST payment/GET tickets là tên rút gọn, không chốt đường dẫn public đầy đủ. Route/DTO mới do agent đề xuất ở trạng thái CANDIDATE.

## 6. Kiểm chứng và phần dùng cho báo cáo

Đã mở/đọc cả sáu ảnh UC/sequence; đối chiếu B6 UC-10–14/28, B13/B14, ADR-002, cấu trúc payment_url/idempotency ở B12 và kế hoạch B15. Kiểm tài liệu gồm diff, link nguồn ảnh, hướng message và trạng thái/phiên bản; kết quả lệnh cuối được ghi trong bàn giao. Không chạy V21–V25 hoặc tuyên bố cổng đã trả QR thực tế.

Kết quả kiểm tĩnh ngày 03/10:

- `rtk git diff --check`: exit 0, không lỗi whitespace.
- `rtk proxy powershell -NoProfile -File .agents/skills/govern-capstone-work/scripts/audit-governance.ps1`: exit 0, PASS, một cảnh báo số file trong diff lớn hơn ba. Bản đồ tác động tại §4 và xác nhận hai thay đổi của Minh là căn cứ phạm vi; script không tự kiểm nội dung sự phê duyệt này. Diff còn có các sửa workspace đã tồn tại trước lượt.
- Kiểm bằng Node trên chín tệp của lượt: 37 link local tồn tại; block Markdown/PlantUML cân bằng; GOV-149/150 không trùng; header REVIEW_READY và hướng start/completion khớp; không phát hiện lỗi trong tập kiểm này.
- PlantUML chỉ kiểm block/source, **chưa render bằng engine**; chưa có renderer local được xác định trong lượt. Không coi kiểm cân block là chứng nhận layout hoặc toàn bộ cú pháp engine.

Không còn xung đột luồng success trong các tạo tác đã cập nhật; OPEN còn QR payment và việc rà/duyệt tên route/message/DTO v0.2. Các phép kiểm ứng dụng vẫn NOT RUN.

Phần nên đưa vào báo cáo: thứ tự commit cục bộ và thông điệp; phân biệt PENDING_PAYMENT/ISSUING/COMPLETED; nhận yêu cầu tạo URL khác thu tiền; order COMPLETED trước Saga COMPLETED ở nhánh success; hủy trả tài nguyên đúng một lần; QR thanh toán khác QR vé. Giữ các nhánh timeout/lặp/hoàn và giới hạn chưa kiểm thực thi bên cạnh happy path.
