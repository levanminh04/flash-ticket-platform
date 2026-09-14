# B11-B — Đối chiếu tính khả thi với tài sản hiện thực

- Phiên bản: `B11-B-v0.6`
- Trạng thái: `APPROVED`
- Người duyệt: **Lê Văn Minh** (`GOV-085`)
- Ngày duyệt: **2026-09-01**
- Phân lớp: **`COMPARISON`**
- **Bản hiện hành `v0.6` đã sửa các lời khai quá mức của `v0.5` bằng phép quét lại toàn bộ phạm vi liên quan:** thay cách nói “hai giao dịch” bằng **bảy họ giao dịch**, hạ mức phủ `INV-06` về đúng phần mã chứng minh được, phân loại lại bốn điểm ghép nối frontend, sửa quy tắc đếm cạnh import và kịch bản tài nguyên, đồng thời bổ sung `Yêu cầu hủy sự kiện` vào phần phải xây mới của đủ sáu phương án. Các vòng trước được giữ nguyên trong §14 làm lịch sử; kết luận hiện hành chỉ đọc theo `v0.6`. `B11-A-v0.4` **vẫn không bị chạm một chữ**
- **Ý nghĩa trạng thái `APPROVED`:** Lê Văn Minh đã duyệt đúng bản `B11-B-v0.6` hiện hành (`GOV-085`). Trước đó, hai lời giải thích của `B11-A` đã được sửa trong phiên `FORMATION` sạch thành `B11-A-v0.5` và được Lê Văn Minh duyệt lại (`GOV-084`). Hai cổng đầu của `B11` đã đạt. Cập nhật quản trị 2026-09-04: `B11-C-v0.3` đã chấp nhận `PA-6`; `GOV-097` thu hồi benchmark tải như blocker của B11 và thay bằng ngân sách tài nguyên, còn thử tải thật chuyển sang Giai đoạn 5–6
- ✅ **`B11-B-OPEN-04` ĐÃ ĐÓNG ngày 2026-08-31 (`GOV-079`)** — Lê Văn Minh phân xử **trước** lượt duyệt, đúng cách `GOV-070`/`GOV-072` đã làm với `B11-A-OPEN-03`: **trần 8 chỉ đếm service nghiệp vụ; chatbot và cơ chế chẩn đoán đếm riêng về tài nguyên vận hành.** Hệ quả: `PA-3` và `PA-5` giữ `ASR-10` = `Đạt`, **tập vẫn sáu phương án**, và kết luận của phiếu này **thôi có điều kiện**
- ✅ **Bốn phần việc sổ quyết định giao cho `B11-B` nay đã làm đủ** (`GOV-081`): `PRJ-009` (§3.2, §7.5), `PRJ-008` → **§3.4**, `PRJ-004`/`PRJ-005` → **§8.5**. `B11-B-OPEN-06` và `-07` đóng theo; `-05` đóng bằng hiệu đính `B11-A-v0.5`; `-08` đóng bằng lựa chọn nghĩa hẹp tại `GOV-091`; `-03` đóng bằng quyết định vận hành `GOV-097`/`GOV-098`. Chỉ `-02` còn mở và được định tuyến đúng sang `B12`
- **Đầu vào và phiên bản: `docs/architecture/B11-A-independent-alternatives.md` — `B11-A-v0.4`, `APPROVED` ngày 2026-08-31 (`GOV-070`)**, tập **sáu** phương án `PA-1`–`PA-6` cộng đường cơ sở `PA-0` ngoài tập (`GOV-055`). Phạm vi `R15` đã được phân xử ngày 2026-08-31 (`GOV-072`) — đọc **theo phạm vi**, tập ổn định trước khi phiếu này mở, đúng khuyến nghị vận hành mà `GOV-070` nêu
- **Đối chiếu sau hiệu đính:** `B11-A-v0.5` chỉ sửa đúng hai lời giải thích, được duyệt lại tại `GOV-084`; sáu phương án, mọi phép đếm, sơ đồ và kết luận khả thi không đổi. Vì vậy đầu vào so sánh nguyên thủy `B11-A-v0.4` của bản `B11-B-v0.6` không bị sửa vật chất hoặc mất hiệu lực
- Nguồn `COMPARISON` đã mở ở phiên này: `docs/b5.5-doi-chieu-ma-nguon-va-ba-tang.md` — **khảo sát 2026-08-07, sửa 2026-08-08, đính chính 2026-08-31 (`GOV-074`)**; repository `D:/Project/flash-ticket-system` ở commit sạch **`609fa2d37cad69aafa593b7db5b6cedeaf803da5`**, kiểm tra ngày 2026-09-01; `PRJ-009` cho câu hỏi giữ chỗ–đơn
- ⚠️ **`GOV-074` giữ nguyên văn khảo sát cũ và không đổi nội dung kiểm kê dùng làm đầu vào.** Tệp `B5.5` có nhận chú thích tại đầu PHẦN 4, §3.5 và ô kiểm cuối PHẦN 5; vì vậy không được phát biểu rằng các phần ấy “không bị chạm”. Dữ kiện ở §3–§9 của phiếu này không đổi vì không dựa vào nội dung trợ lý cũ đã được đính chính
- Nguồn thẩm quyền được dẫn: `AGENTS.md` (cấp 2); `docs/quy-trinh-lam-viec.md` GIAI ĐOẠN 4 (cấp 3); `docs/tang-b-quy-trinh-ky-thuat.md` phiếu `B11` (cấp 4); `.agents/skills/govern-capstone-work/references/project-authority-and-gates.md` §5
- Đi vào báo cáo: **không trực tiếp.** Phiếu này quản lý công việc di trú; báo cáo mô tả cấu trúc đích và lý do kiến trúc (`B5.5` §0). Chỉ hai kết quả ở §13 được đề cử

> ⛔ **Điều kiện phiên, khai trước mọi thứ khác.** Phiên soạn bản này **đã mở `B5.5` và đã đọc repository cũ**, đúng thẩm quyền mà `B11-B` được cấp. Vì vậy nó **không được sản xuất bất kỳ tạo tác `FORMATION` nào** — cùng hệ quả `GOV-057` và `PRJ-009` đã nêu. Trong phiên này **không** tạo, thêm, xoá, xếp hạng hay sửa một phương án, một ranh giới, một bất biến hay một yêu cầu nào. `B11-A-v0.4` **không bị sửa một chữ**.
>
> **Phiên này là một phiên khác với mọi phiên soạn `B11-A`.** `v0.1`–`v0.4` đều được soạn ở phiên sạch (`GOV-056`, `GOV-060`, `GOV-061`, `GOV-065`, `GOV-069`); bản này mở sau khi `GOV-070` tích ô *"chờ người thật"* của `B11-A` §10.

---

## 1. Phiếu này trả lời gì, và nó KHÔNG được phép làm gì

`B11-B` là **cổng thứ hai trong ba cổng** của `B11`. Nó nhận **đúng sáu** phương án đã duyệt và trả lời cho từng phương án bốn câu:

1. Tài sản hiện thực nào **dùng lại được** cho từng ranh giới của phương án đó?
2. Đi từ mã hiện tại tới hình dạng đó phải làm **những việc gì** — gỡ ghép nối nào, tách bảng nào, thay giao dịch nào?
3. Ràng buộc **vận hành** nào chạm vào phương án đó?
4. Có **rủi ro khả thi** nào đủ lớn để phải quay về một cổng thiết kế không?

`B11-B` **không** làm và **không được** làm:

- **không tạo, thêm, bớt, xếp hạng, đề nghị hay sửa một phương án** — `AGENTS.md`, mục *Legacy comparison and B11 sub-gates*;
- **không chọn** — việc chọn ở `B11-C` sau khi cả `B11-A` và `B11-B` `APPROVED` (`GOV-018`);
- **không chốt** hợp đồng (`B13`), sở hữu dữ liệu vật lý hay schema đích (`B12`), Saga (`B11-C` chốt, `B14` vẽ);
- **không sửa** một tài liệu `FORMATION` nào, kể cả để "cho khớp hiện trạng".

> ⛔ **Phép đọc bị cấm, ghi ra vì nó là cách hỏng phổ biến nhất của một cổng như thế này.** Mọi con số ở §7 đo **công di trú từ mã hiện có**. Một phương án có con số thấp **không vì thế mà tốt hơn**. Suy từ *"phương án này gần với mã đang có nhất"* sang *"nên chọn phương án này"* chính là chiều nghịch mà `AGENTS.md` cấm: `legacy packages/tables/imports → target boundaries → retrospective justification`. Thước đo **kiến trúc** là bốn con số ở **mục (5) của từng phương án `B11-A`** (chỉ số do `quy-trinh` §4.4 đặt — **không** phải §4.4 của `B11-A`, vốn là mục riêng của `PA-4`) và ma trận `15 × 6`; thước đo ở đây là **giá vé đi từ hôm nay tới đó**, và hai thứ đó được `B11-C` cân riêng.
>
> Ba tiền lệ buộc phải đọc như vậy: `B5.5` §0 bảng hai dòng; `B5.5` §2.3 *"Dữ kiện trên **không chứng minh** aggregate tồn kho thuộc context/service nào"*; và `PRJ-009` ⛔ *"không được dùng dòng này làm lý do chọn hay bỏ hình dạng nào"*.

**Kết quả tổng của vòng này, nói trước — và nay phát biểu vô điều kiện:** **không phương án nào trong sáu bị *bằng chứng hiện thực* loại bỏ**, và không có rủi ro khả thi nào đủ lớn để phải đóng `B5.5` rồi trả một ràng buộc tổng quát về `B11-A`.

> ✅ **Điều kiện duy nhất treo lên kết luận này đã được gỡ.** `v0.3` phát biểu kết luận **có điều kiện** vì `B11-B-OPEN-04` — trần 8 có tính chatbot và cơ chế chẩn đoán hay không — chưa được phân xử; nếu đọc theo `B5.5` §3.4 thì `PA-3`/`PA-5` đếm 9 > 8, trượt `ASR-10` và phải rời tập. **Lê Văn Minh đã phân xử ngày 2026-08-31 (`GOV-079`): trần 8 chỉ đếm service nghiệp vụ; chatbot và cơ chế chẩn đoán đếm riêng về tài nguyên vận hành.** Hai phương án ấy giữ `ASR-10` = `Đạt`, **tập sáu phương án đi tiếp nguyên vẹn**, và câu trên nay phát biểu **vô điều kiện**.
>
> ✅ **Dư âm về lời khai của `B11-A` đã được xử lý sau vòng so sánh này.** `GOV-079` chốt **kết luận** về cách đếm; ba nguồn cũ vẫn im lặng nên lý do *“đọc trực tiếp câu chữ”* từng sai. Phiên `FORMATION` sạch đã thay lý do ấy bằng dẫn `GOV-079` trong `B11-A-v0.5`, và Lê Văn Minh duyệt lại tại `GOV-084`; `GOV-080` vì vậy đã đóng.

---

## 2. Phương pháp và quy tắc đếm

Ba phép đo được dùng. Mỗi phép đo có quy tắc viết ra và **danh sách liệt kê được**, theo luật `B11-A-v0.4` đặt: *một ô hay một dòng không được chứa một con số mà chính nó không liệt kê ra*.

**Phép đo 1 — cạnh ghép nối mã bị cắt.** Một *cạnh* là một dòng `import` mà cả lớp nguồn và lớp đích đều gán được về một trong bảy năng lực đã duyệt. Có **25** cạnh giữa các package nghiệp vụ của `core-service` và **một** cạnh từ package `shared` vào package `event`; cạnh thứ 26 vẫn được giữ vì hai đầu đều gán về `BC-01`. Một cạnh **bị cắt** bởi một phương án khi lớp nguồn và lớp đích được phương án ấy đặt vào **hai ranh giới khác nhau**. Toàn bộ 26 cạnh liệt kê ở §4; phép gán từng lớp về một năng lực `B5` §3 ghi ở §3.

> ⚠️ **Giới hạn của phép đo 1, khai chứ không giấu.** Nó **mù với lời gọi trong cùng một package**. Hai đường cắt trong tập — cắt bên trong `BC-02` (`PA-4`, `PA-5`) và cắt tách `Phát hành vé` khỏi `BC-02` — đều cắt qua những lời gọi như vậy. Vì thế **phép đo 2** tồn tại, và **mười lăm** vị trí gọi đó được liệt kê đích danh ở §6.2.

**Phép đo 2 — họ giao dịch phải tháo khỏi ACID cục bộ.** Không đếm đơn thuần số chú giải `@Transactional`, vì một thao tác nghiệp vụ có thể gọi lồng nhiều phương thức có giao dịch và một phương thức có thể tham gia giao dịch của bên gọi. §6 kiểm kê **bảy họ thao tác** theo kết quả nghiệp vụ: khởi tạo thanh toán; xác nhận thu tiền và phát hành vé; tạo đơn; hủy hoặc làm hết hạn đơn; phát hành vé bất đồng bộ; tạo và lưu mã vé; cùng nhóm con giữ/xác nhận/trả lượt khuyến mãi. Với mỗi phương án, ghi họ nào đi qua nhiều ranh giới và những vị trí gọi trực tiếp nào phải tháo. Hai thân giao dịch chịu lực nhất vẫn được phân tích dòng-theo-dòng ở §6.1 và §6.2.

**Phép đo 3 — bảng phải tách.** Một bảng phải tách khi nó mang cột thuộc **hai** năng lực mà phương án đặt ở hai ranh giới. Danh sách bảng ở §5.

**Quy ước gán lớp về năng lực.** Mỗi lớp được gán về một trong bảy năng lực `B5` §3 theo **thứ nó làm**, không theo package chứa nó — vì §3.1 dưới đây cho thấy package hiện tại **không** trùng ranh giới năng lực. **Năm** phép gán có thể tranh luận, nên khai riêng:

- `SeatBookingService` gán về **nguồn cung** (nó ghi `EventSeatInventory`), không về `Đơn hàng`, dù nó nằm trong package `booking`.
- `TicketReservationService` gán về **nguồn cung** (nó giữ khoá Redisson trên `ticketTypeId`/`seatId`), dù tên nó có chữ *reservation* và nó nằm trong package `booking`. ⚠️ Nó **không phải** hiện thực của root `Giữ chỗ`; xem `PRJ-009`.
- `TicketType` gán về **hai nghĩa** — cấu hình (`BC-01`) và cam kết nguồn cung (`BC-02`) — đúng cảnh báo `B11-A` §4.5(6) đã viết trước khi phiếu này mở. Bốn cạnh trỏ vào lớp này được ghi thành **dải**, không thành một con số.
- `EventSyncHelper` gán về **`BC-01`**, dù nó nằm trong package `shared`. Hai dữ kiện buộc phép gán này: nó là **lớp tiện ích tĩnh không trạng thái** (`private EventSyncHelper() {}`, mọi phương thức `static`), và **người gọi duy nhất của nó trong toàn cây là `OrganizerEventService`** — một lớp `BC-01` — ở bốn vị trí (dòng 191, 222, 249, 276). ⚠️ **Hệ quả đếm được:** cạnh #26 vì vậy là `BC-01` → `BC-01`, tức **không bị cắt ở phương án nào**. `v0.1` để nguồn của nó là *“hạ tầng chung”* — một nhãn **không thuộc bảy năng lực** — rồi vẫn tính #26 là bị cắt ở năm phương án; `v0.2` sửa năm con số theo đúng phép gán này.
- `TicketMessageListener` gán về **`BC-04`** theo thứ nó làm — nó tiêu thụ hàng đợi `q.ticket.issue` và **gọi `TicketIssuanceService` để phát hành vé**, rồi mới gọi `QRCodeService` — dù nó nằm trong `booking/messaging`. ⚠️ **`v0.1` gán nó hai chỗ khác nhau:** §3.1 xếp nó vào `BC-06`, §4.2 cạnh #23 xếp nó vào `BC-04`; `v0.2` thống nhất về `BC-04` và sửa §3.1. **Độ nhạy, khai ra vì phép gán này tranh luận được:** nếu đọc nó là `BC-06` thì cạnh #23 thành nội bộ `BC-06`, và `PA-3` cùng `PA-5` mỗi phương án bớt **một** cạnh.

---

## 3. Kiểm kê: tài sản hiện thực ánh xạ lên bảy năng lực đã duyệt

Chiều ánh xạ là **tài sản → năng lực đã duyệt**, không phải ngược lại. Bảy năng lực lấy nguyên từ `B5` §3 qua `B11-A` §3.1.

### 3.1 Bảng ánh xạ

| Năng lực (`B5` §3) | Tài sản hiện thực | Vị trí | Mức phủ |
|---|---|---|---|
| `BC-01` Vòng đời sự kiện và cấu hình bán | `Event`, `Venue`, `Category`, `EventImage`, `EventLayout`, `EventSector`, `TicketType` (cột cấu hình); `EventService`, `OrganizerEventService`, `VenueService`, `CategoryService`, `EventLayoutService`, `TicketTypeService`, `ImageUploadService`, `SeatMapSyncService`, `CompatibilityPolicy`; `Promotion` (cấu hình) | package `event` (61 tệp), package `promotion` | **Cao**, thiếu một root |
| `BC-02` Mua vé và cam kết nguồn cung | `Order`, `OrderItem`, `OrderItemSeat`, `BookingService`, `BookingCompositionPolicy`, `OrderExpirationService`/`Helper`; `EventSeat`, `EventSeatInventory`, `TicketInventoryCounterService`, `SeatBookingService`, `TicketReservationService`, `TicketType` (cột tồn kho); `PromotionUsage`, `PromotionService` | package `booking` + package `event` + package `promotion` — **ba package** | **Vừa**, thiếu một root, một root chỉ có một phần |
| `BC-03` Thanh toán và hoàn tiền | `Transaction`, `PaymentService`, `PaymentValidatorService`, `VNPayIPNService`, `VNPayGateway` | package `payment` (15 tệp) | **Vừa**, một root chỉ có bảng |
| `BC-04` Quyền tham dự và kiểm soát vào cửa | `Ticket`, `TicketRepository`, `TicketIssuanceService` (gồm `validateAndCheckIn`), `TicketController`, `TicketMessageListener` (xem phép gán ở §2) | **package `booking`**, schema `booking_schema` | **Cao**, nhưng **đặt sai chỗ** so với mọi hình dạng tách nó ra |
| `BC-05` Đối soát và chi trả | **không có gì** — không bảng, không thực thể, không service | — | **Không** |
| `BC-06` Giao nhận thông tin vé | `EmailService`, `QRCodeService`, `EmailMessageListener`, hạ tầng RabbitMQ | package `notification` + `booking/messaging` | **Vừa** |
| `BC-07` Hồ sơ tài khoản và quyền nghiệp vụ | `user-service` — 41 tệp Java, MongoDB, `User`, `OrganizerProfile`, `UserFollow`; ba cơ chế đồng bộ Keycloak (§3.4). ⚠️ `Address` **không** nằm trong cột này — `PRJ-008` bắt cắt nó | **tiến trình riêng**, kho MongoDB tách thật | **Vừa** — hai root có, root `Theo dõi organizer` **mới một nửa**; xem §3.4 |

### 3.2 Năm khoảng trống, ghi thành `FACT`

1. **`BC-05` trống hoàn toàn.** Không có bảng nào tên `settlement`, `payout` hay `reconciliation`; không có thực thể, repository hay service nào. Root `Hồ sơ chi trả sự kiện` và `INV-10`, `INV-11` là **phần xây mới ở mọi phương án**. Chi phí này **giống hệt nhau ở cả sáu** và vì vậy không phân biệt phương án nào.
2. **Root `Giữ chỗ` không có hiện thực.** `PRJ-009` đã ghi và phiếu này **không mở lại repository cho câu hỏi đó**: lược đồ có bảng `booking_schema.reservations` đứng riêng, mã chạy thật **không dùng bảng đó**, và thứ chạy là một đơn ở trạng thái chờ kèm hạn, tồn kho ghế bị giữ bằng cột `orderId`. Tức **đơn chính là cái giữ chỗ**.
3. **Root `Yêu cầu hoàn tiền` chỉ có bảng.** `payment_schema.refunds` tồn tại trong lược đồ; trong mã chỉ có bốn cột theo dõi trên `Transaction` — `is_refunded`, `refund_amount`, `refunded_at`, `refund_reason`. Không thực thể `Refund`, không repository, không service. `INV-08` là phần xây mới ở mọi phương án.
4. **Root `Giới hạn mua` chỉ có một phần, và phần đó mang nghĩa khác.** Mã có hai cơ chế: cột `event_schema.ticket_types.max_per_order` (giới hạn **mỗi đơn**) và một phép kiểm ở `BookingService` dòng 144 chặn người dùng có đơn chờ trùng sự kiện. `B2` định nghĩa `Giới hạn mua` theo **tài khoản trên sự kiện**, cộng dồn cả đang giữ và đã mua — **không phải** thứ nào trong hai cơ chế trên. `INV-04` là phần xây mới ở mọi phương án.
5. **Root `Yêu cầu hủy sự kiện` không có aggregate.** `OrganizerEventService.cancelEvent()` (dòng 229) đổi trạng thái trực tiếp; không có bản ghi yêu cầu, không có bước phê duyệt.

> **Đọc cho đúng cả năm dòng 1–5.** *(`v0.2` viết “bốn dòng 1–4” và bỏ rơi dòng 5 — `Yêu cầu hủy sự kiện` nằm trong `BC-01`, có mặt ở cả sáu phương án, nên nó là chi phí chung y hệt bốn dòng kia và phải xuất hiện trong phần *Xây mới* của mọi phương án.)* Chúng là **chi phí chung**, xuất hiện y hệt ở cả sáu phương án, nên chúng **không** là căn cứ so sánh. Chúng có mặt ở đây vì `B11-C` cần biết phần xây mới lớn tới đâu trước khi cân bất kỳ con số nào — và vì `B5.5` §5 có một ô kiểm hỏi đúng câu *"có context nào ở `B5` không ánh xạ được vào tài sản hiện thực"*.

### 3.3 Chiều ngược lại — module mã không ánh xạ được vào một năng lực nào

`B5.5` §5 có ô kiểm này. Kết quả: **không module nghiệp vụ nào thừa.** Ba thứ nằm ngoài bảy năng lực và cả ba đã có chỗ:

- `discovery-service` (33 tệp, PostgreSQL + pgvector, LangChain4j) là **chatbot hỗ trợ mua vé**, không phải service discovery (`GOV-057`). `R12` xếp nó là **kênh**, không phải ranh giới nghiệp vụ. `CoreServiceClient` của nó gọi `createBooking()` và `initiatePayment()` **qua API công khai kèm JWT** — đúng như `B5.5` §2.4 đã đính chính, đây là tính năng chứ không phải vi phạm.
- `eureka`, `configserver`, `apigateway` là hạ tầng nền, không sở hữu aggregate, và **không tính vào trần 8** — `B5.5` §3.4 nói đúng câu đó: *“không tính API gateway, service-discovery/config và hạ tầng thuần kỹ thuật”*.
- `booking_schema.carts` và `cart_items` không ánh xạ vào một root nào của `B7`. Đây là một khoảng dư của lược đồ cũ; nó **không** sinh ra một câu hỏi kiến trúc, chỉ là một dòng trong danh sách di trú.

> ⛔ **Nhưng cùng ô bảng ấy còn một vế nữa, và nó ngược với `B11-A`. Phải báo, không được im.** `B5.5` §3.4 viết đủ là: *“Đếm các service nghiệp vụ triển khai độc lập, **gồm các đơn vị ứng dụng như user/chatbot/trợ lý nếu B11 triển khai chúng thành service**; không tính API gateway, service-discovery/config và hạ tầng thuần kỹ thuật”*. Ngược lại, `B11-A` mục (7) của cả sáu phương án đặt đơn vị chẩn đoán **“đếm riêng khỏi trần 8”**, và §8.2 khai đó là *“đọc trực tiếp câu chữ”*, **không phải một điểm mở**.
>
> **Hệ quả đo được bằng chính §8.2 của phiếu này:** theo luật đếm của `B5.5`, `PA-3` và `PA-5` = 7 ranh giới nghiệp vụ + chatbot + đơn vị chẩn đoán = **9**, tức **vượt trần 8** — và trần 8 là `ASR-10`, một **ràng buộc cứng** mà `B11-A` §6 ghi `Đạt` ở cả sáu cột. Theo luật đếm của `B11-A` thì 7 ≤ 8 và không có gì xảy ra.
>
> **`B11-B` không chọn giữa hai cách đọc.** Đây là **phạm vi của một ràng buộc `USER_CONFIRMED`** (`R1`/`ASR-10`), đúng loại mà `AGENTS.md` cấm agent tự suy — cùng loại với `B11-A-OPEN-01` và `B11-A-OPEN-03`, cả hai đều phải chờ Lê Văn Minh. Ghi thành **`B11-B-OPEN-04`** ở §11 và một dòng ở §9. ⚠️ Phiếu này **không** vì thế mà loại `PA-3` hay `PA-5`: chừng nào cách đọc chưa được phân xử thì **không kết luận nào về `ASR-10` được đổi**.
>
> *(`v0.1` chỉ trích nửa ô ủng hộ `B11-A` và bỏ nửa còn lại. `v0.2` sửa.)*
>
> ✅ **ĐÃ ĐÓNG ngày 2026-08-31 (`GOV-079`).** Lê Văn Minh phân xử: **trần 8 chỉ đếm service nghiệp vụ; chatbot và cơ chế chẩn đoán đếm riêng về tài nguyên vận hành.** Vậy `PA-3` và `PA-5` đếm **7 ≤ 8**, `ASR-10` giữ `Đạt` ở cả sáu cột, không con số nào của `B11-A` phải đếm lại. Điều kiện *“nếu B11 triển khai chúng thành service”* của `B5.5` §3.4 nay đã được trả lời: **B11 không đếm chúng vào trần**. ⚠️ Phân xử này chốt **cách đếm**, không chốt việc chatbot hay cơ chế chẩn đoán được triển khai thế nào — `B11-A` §2 vẫn khai *“một đơn vị triển khai riêng”* là `CANDIDATE`, chốt ở `B11-C`, và §8.2 của phiếu này vẫn đếm chúng về **tài nguyên**.

### 3.4 Đánh giá tái sử dụng `user-service` — món nợ `PRJ-008`

`PRJ-008` là một **ý định** `USER_CONFIRMED` của chủ đồ án (*“tận dụng user-service của repo cũ … có chăng thì sau chỉnh sửa lại nhỏ thôi”*), và chính dòng đó ghi: *“**Đánh giá thật thuộc `B11-B`**, không phải dòng này”*, kèm ba phần **vượt phạm vi phải cắt** và một cảnh báo rằng dùng lại cơ chế đồng bộ sẽ **chốt hộ** `B4-OPEN-01`. `v0.1`–`v0.3` không chạy đánh giá đó mà lại viết `user-service` khớp `RG-07` **“nguyên trạng”** (`B11-B-OPEN-06`).

**Kiểm kê:** 41 tệp Java (40 chính + 1 test), MongoDB, **3 document** (`users`, `organizer_profiles`, `user_follows`), 3 controller / **16 endpoint**, 5 service, 3 repository. Tiến trình riêng cổng 8082, đăng ký Eureka, có tuyến gateway riêng, và **kho dữ liệu tách thật** — MongoDB, không đụng PostgreSQL của `core-service`. Vế *“đã là tiến trình riêng”* của `v0.2` là **đúng**.

#### Ba mục `PRJ-008` bắt cắt — kết quả từng mục

| Mục | Kết quả | Bằng chứng |
|---|---|---|
| **Trường địa chỉ** | **Cô lập hoàn toàn — và đã là mã chết.** Xoá `model/Address.java`, `dto/AddressDTO.java`, `User.java:61-63`, `UserRequest.java:16`. **Không một chỗ gọi nào gãy.** | `addresses` **không được đọc hay ghi ở đâu**; `UserResponse` và `UpdateProfileRequest` **không có** trường địa chỉ; không có endpoint CRUD địa chỉ; `Address.isDefault` không có người đọc. `AddressDTO` chỉ được `UserRequest` dùng, mà `UserRequest` chỉ được `KeycloakAdminService.createUser` dùng — hàm này tự khai `[LEGACY]` và **không có ai gọi**. Quét thêm cả frontend: **không có form, trường hay lời gọi API nào về địa chỉ người dùng** — mọi chỗ hiện chữ `address` đều là địa chỉ **địa điểm**, thuộc `core-service/Venue` |
| **Trạng thái khoá tài khoản** | **Tính năng chưa từng được xây.** Cắt nó không mất hành vi nào. ⚠️ Nhưng **gỡ kiểu `UserStatus` thì vướng vào mã đồng bộ** | `UserStatus.SUSPENDED` **không được gán ở đâu**; `lockoutUntil` và `failedLoginAttempts` (`User.java:193,195`) **không được đọc cũng không được ghi**; **không có filter hay `@PreAuthorize` nào chặn theo status**; `disableUser()` (`KeycloakAdminService:100-122`) **không có ai gọi**; `findByStatusAndIsDeletedFalse` không có ai gọi. Ba chỗ ghi `status` **đều nằm trong** `JwtSelfHealingFilter:114` và `KeycloakEventConsumer:117,187` |
| **Lưu ảnh nhận diện** | **Kích thước phụ thuộc cách đọc phạm vi — và đây là mục duy nhất thật sự tốn công.** | Xem hai cách đọc dưới đây |

> **Một chi tiết đáng ghi về mục địa chỉ:** nó là **di vật copy từ một dự án thương mại điện tử trước**, không phải phần bị bỏ dở của FlashTicket. `dto/AddressDTO.java:7-8` còn nguyên chú thích `// was "state" in old project` và `// was "zipcode" in old project`. Khái niệm địa chỉ **thật sự** của hệ thống nằm ở `core-service/Venue` — năm trường cùng hình dạng, có chỉ mục và được truy vấn thật. Cắt mục này là gỡ một mẩu mã lạc, không phải bỏ một năng lực.

> ⚠️ **Mục “ảnh nhận diện” có hai cách đọc, cho hai chi phí rất khác nhau, và `B11-B` không tự chọn.**
>
> - **Chỉ ảnh đại diện người dùng:** khoảng 6 vị trí (`User.java:125`, `UserResponse:31,87`, `UserService:146-175`, `UserController` `POST /me/avatar`). Nhưng **`StorageService`, `CloudinaryConfig` và cả hai phụ thuộc Cloudinary phải ở lại**, vì `uploadOrganizerLogo` và `uploadOrganizerBanner` phục vụ luồng organizer mà `BC-07` **giữ**. Tức **không giảm được phụ thuộc nào**.
> - **Toàn bộ ảnh nhận diện, kể cả logo/banner organizer:** xoá thêm `OrganizerController:138-176`, `OrganizerService:216-242, 368-369`, `OrganizerProfile:105-106`, `OrganizerDTO:22-23` — tức **xoá hai endpoint organizer đang sống** — và làm hỏng một đường đọc xuyên service: `core-service/EventService.java:184-196` đọc `logoUrl` để tự chữa cột sao chép `Event.organizerLogoUrl`.
>
> `BIZ-151` loại *“bộ nhận diện thương hiệu”* khỏi hồ sơ organizer tối thiểu, nên cách đọc thứ hai có căn cứ. Nhưng nó **xoá hành vi đang chạy**, nên theo `AGENTS.md` đây là **thay đổi phạm vi cần Lê Văn Minh xác nhận riêng**, không phải việc `B11-B` tự quyết.

#### Sau ba mục cắt, phần còn lại có phủ `BC-07` không?

`B5` §3 chốt `BC-07` gồm ba root và ghi rõ *“Keycloak quản lý danh tính/vòng đời tài khoản; hồ sơ ứng dụng giữ tên tổ chức, mô tả ngắn và lý do từ chối khi có”*.

| Root `B7` | Tình trạng | Ghi chú |
|---|---|---|
| `Hồ sơ nghiệp vụ ứng dụng` | **Có, mỏng nhưng mạch lạc** | Sau ba mục cắt còn 7 trường sửa được và 2 endpoint |
| `Đăng ký organizer` | **Có, và là phần chắc nhất** | Vòng đời `PENDING`/`ACTIVE`/`REJECTED` đầy đủ, chống trùng đơn, sinh slug duy nhất có xử lý tiếng Việt |
| `Theo dõi organizer` | ⚠️ **Mới xây một nửa** | Follow/unfollow/kiểm-tra-một chạy được, có tăng giảm `followerCount` nguyên tử. **Thiếu:** danh sách organizer tôi theo dõi, danh sách người theo dõi một organizer — hai phương thức repository **đã khai nhưng không ai gọi** và không controller nào phơi ra; và **không sự kiện nào được phát khi follow** (`RabbitTemplate` có bean nhưng module **không publish ở đâu cả**) |

**Phần dôi ra, sống sót qua ba mục cắt nhưng không thuộc root nào của `BC-07`:** `Preferences`/`NotificationSettings`, `OAuthConnection`, hai trường 2FA, `lastLoginAt`/`lastLoginIp`/`loginCount` (`loginCount` được đặt `0` lúc tạo rồi **không bao giờ tăng**), `deletedAt` (không bao giờ được ghi, kể cả bởi đường xoá mềm). **Khoảng một phần ba document `User` là lược đồ trơ.**

#### Đồng bộ danh tính — mô tả, **không** quyết

`PRJ-008` cảnh báo dùng lại cơ chế này sẽ chốt hộ `B4-OPEN-01`; `B7` dòng 334 định tuyến *“phương án phối hợp và hợp đồng chờ B11/B13”*. Vòng này chỉ **mô tả**, và phát hiện quan trọng là: **có ba cơ chế, không phải một**.

| | Cơ chế | Chiều |
|---|---|---|
| **A** | `KeycloakEventConsumer` — tiêu thụ sự kiện Keycloak qua RabbitMQ, kèm một SPI **triển khai riêng** nạp vào container Keycloak. Ghi `email`, hồ sơ, và **thay trọn bộ `roles`** | Keycloak → Mongo |
| **B** | `JwtSelfHealingFilter` — filter mỗi request, tạo bản ghi Mongo nếu chưa có, lấy role từ `realm_access.roles` | Keycloak → Mongo |
| **C** | `KeycloakAdminService.assignRoleToUser` — ghi ngược bằng Admin REST, gọi từ đúng một chỗ: duyệt organizer | Mongo → Keycloak |

> ⛔ **Ba dữ kiện `B11-C`/`B13` cần biết, và `B11-B` dừng ở đây.** (1) **A và B thừa nhau** — B một mình đủ để tạo bản ghi người dùng. (2) **C là đường ghi ngược duy nhất**, và khi nó lỗi thì `OrganizerService:273-282` **bắt lỗi, không ném lại**, chỉ ghi log `[CRITICAL] … Admin must manually retry` — Mongo và Keycloak phân kỳ, đúng cửa sổ lỗi mà `B7` dòng 334 để `OPEN`. (3) **Mục cắt “trạng thái khoá tài khoản” không độc lập với quyết định này**: ba chỗ ghi `status` nằm trong A và B, nên cắt nó là sửa vào đúng mã mà `B4-OPEN-01` chưa phân xử. Hai việc phải làm cùng lượt hoặc theo thứ tự do người thật đặt.

#### Năm điều làm chữ “nguyên trạng” sai — và không điều nào liên quan ba mục cắt

1. **Module không tự chứa.** `application.yml` trong module chỉ có **5 dòng**; 86 dòng cấu hình thật nằm ở `configserver`, cộng 17 khoá `.env`. Bê nguyên thư mục sang là được một service không có URI Mongo, không có host Rabbit, không có cấu hình Keycloak.
2. **Khởi động phụ thuộc cứng vào cấu hình Keycloak.** `KeycloakAdminProperties` là record `@Validated` với bốn `@NotBlank` và **không có giá trị mặc định** — thiếu biến môi trường là hỏng lúc khởi động, chứ không phải suy giảm lúc chạy.
3. **Chỉ mục Mongo gần như chắc chắn không được tạo.** Không nơi nào đặt `auto-index-creation`, mà Spring Boot 3.x mặc định `false`. Nghĩa là **mọi ràng buộc duy nhất mà mô hình ngụ ý đều chưa được cưỡng chế** — kể cả `email`, `keycloakId`, `organizerSlug`, và cặp `(follower, organizer)`.
4. **Hợp đồng xuyên service đang hỏng ở cả hai đầu.** `core-service/UserServiceClient` gọi `GET /api/organizers/{id}`, nhưng `user-service` **không map đường dẫn đó** (đường đúng là `/api/internal/organizers/{id}`), và client **không gắn header `Authorization`** trong khi mọi thứ ngoài `public`/`internal` đều đòi token. Hệ quả: `EventService:199-203` luôn rơi vào nhánh `catch` và dùng bản sao cũ — nhánh tự chữa `logoUrl` **không bao giờ chạy tới**. Đây là lỗi có sẵn của hệ cũ, không phải do quyết định tái sử dụng sinh ra.
5. ⛔ **API “nội bộ” đang mở ra Internet.** Gateway định tuyến `/api/internal/users/**` sang `user-service`, và **cả hai** `SecurityConfig` — của gateway và của `user-service` — đều đặt `/api/internal/**` là `permitAll`. Tức `GET /api/internal/users/{bất kỳ userId}` **gọi được từ Internet không cần token**, trả về email, số điện thoại, ngày sinh, giới tính, bộ role và trạng thái. Mã tự khai giả định *“bảo mật dựa vào network isolation”* — **giả định đó sai với cấu hình hiện tại**. Áp cùng kỷ luật §8.4: phải xử lý **trước** khi bất kỳ tài sản nào được chuyển.

> **Kết luận của mục này, phát biểu đúng mức.** Ba mục `PRJ-008` bắt cắt **đều rẻ**: một là mã chết (địa chỉ), một là tính năng chưa từng xây (khoá tài khoản), một là nhỏ **nếu** đọc theo nghĩa hẹp (ảnh đại diện). Chữ *“chỉnh sửa lại nhỏ thôi”* của `PRJ-008` **đúng cho ba mục ấy**. Nhưng chữ **“nguyên trạng”** mà `v0.2` thêm vào thì **sai**, và sai vì năm điều trên cộng với việc root `Theo dõi organizer` mới xây một nửa — tức phần **phải xây thêm** lớn hơn phần **phải cắt**. ⚠️ Toàn bộ chi phí này **giống nhau ở cả sáu phương án**, vì cả sáu đều đặt `BC-07` ở một ranh giới riêng; nó **không phân biệt phương án nào**, đúng như bốn khoảng trống ở §3.2.
---

## 4. Sổ ghép nối mã — 26 cạnh có hai đầu gán được về năng lực, liệt kê đủ để đếm lại

Đo bằng các dòng `import` có hai đầu gán được về bảy năng lực đã duyệt. Trong 26 cạnh có **25** cạnh giữa các package nghiệp vụ của `core-service` và **một** cạnh `shared → event`; cạnh từ `shared` được giữ để sổ đầy đủ nhưng là nội bộ `BC-01` ở cả sáu phương án. `B5.5` §1.3 ghi hai chiều `booking ↔ event` là **12 / 1**; phép đếm lại **khớp**, và tìm thêm **13 cạnh mà `B5.5` chưa ghi** — trong đó có bảy cạnh `payment → booking` mang cùng tính chất nặng (thực thể và repository trực tiếp).

### 4.1 Ma trận

| Chiều | Số cạnh | `B5.5` có ghi? |
|---|---|---|
| `booking` → `event` | **12** | ✅ §1.3 |
| `event` → `booking` | **1** | ✅ §1.3 |
| `payment` → `booking` | **7** | ❌ **mới** |
| `booking` → `promotion` | **2** | ❌ mới |
| `notification` → `booking` | **2** | ❌ mới |
| `booking` → `notification` | **1** | ❌ mới |
| `shared` → `event` | **1** | ❌ mới |
| **Tổng** | **26** | |

### 4.2 Danh sách 26 cạnh

| # | Nguồn | Đích | Nguồn thuộc | Đích thuộc | Loại |
|---:|---|---|---|---|---|
| 1 | `BookingCompositionPolicy` | `TicketType.InventoryMode` | Đơn hàng | nguồn cung | thực thể |
| 2 | `BookingService` | `Event` | Đơn hàng | `BC-01` | thực thể |
| 3 | `BookingService` | `TicketType` | Đơn hàng | **hai nghĩa** | thực thể |
| 4 | `BookingService` | `TicketType.InventoryMode` | Đơn hàng | nguồn cung | thực thể |
| 5 | `BookingService` | `EventRepository` | Đơn hàng | `BC-01` | **repository** |
| 6 | `BookingService` | `TicketTypeRepository` | Đơn hàng | **hai nghĩa** | **repository** |
| 7 | `BookingService` | `TicketInventoryCounterService` | Đơn hàng | nguồn cung | service |
| 8 | `SeatBookingService` | `EventSeat` | nguồn cung | nguồn cung | thực thể |
| 9 | `SeatBookingService` | `EventSeatInventory` | nguồn cung | nguồn cung | thực thể |
| 10 | `SeatBookingService` | `EventSeatInventoryRepository` | nguồn cung | nguồn cung | **repository** |
| 11 | `TicketIssuanceService` | `EventRepository` | `BC-04` | `BC-01` | **repository** |
| 12 | `TicketIssuanceService` | `TicketInventoryCounterService` | `BC-04` | nguồn cung | service |
| 13 | `SeatMapSyncService` | `SeatStatusChangedEvent` | `BC-01` | nguồn cung | **sự kiện miền** |
| 14 | `PaymentService` | `Order` | `BC-03` | Đơn hàng | thực thể |
| 15 | `PaymentService` | `OrderRepository` | `BC-03` | Đơn hàng | **repository** |
| 16 | `PaymentValidatorService` | `Order` | `BC-03` | Đơn hàng | thực thể |
| 17 | `PaymentValidatorService` | `OrderRepository` | `BC-03` | Đơn hàng | **repository** |
| 18 | `VNPayIPNService` | `Order` | `BC-03` | Đơn hàng | thực thể |
| 19 | `VNPayIPNService` | `OrderRepository` | `BC-03` | Đơn hàng | **repository** |
| 20 | `VNPayIPNService` | `TicketIssuanceService` | `BC-03` | `BC-04` | service |
| 21 | `BookingService` | `PromotionService` | Đơn hàng | lượt dùng KM | service |
| 22 | `OrderExpirationHelper` | `PromotionService` | Đơn hàng | lượt dùng KM | service |
| 23 | `TicketMessageListener` | `QRCodeService` | `BC-04` | `BC-06` | service |
| 24 | `QRCodeService` | `Ticket` | `BC-06` | `BC-04` | thực thể |
| 25 | `QRCodeService` | `TicketRepository` | `BC-06` | `BC-04` | **repository** |
| 26 | `EventSyncHelper` | `Event` | **`BC-01`** (§2) | `BC-01` | thực thể |

**Đọc ra từ bảng, ba dòng:**

1. **Tám cạnh là `repository` trực tiếp** — #5, #6, #10, #11, #15, #17, #19, #25. Đây là chỗ `B5.5` §2.2 gọi đúng tên: *"ghép nối **qua CSDL chung**, không phải qua hợp đồng xuyên service"*. Chúng chạy qua **cùng một `EntityManager`**, nên gỡ chúng **không phải** đổi một lời gọi thành một lời gọi khác — nó là đổi một phép đọc trong giao dịch thành một phép đọc ngoài giao dịch, với ngữ nghĩa nhất quán khác hẳn.
2. **Hai cạnh không sinh công gỡ, và chúng không sinh vì hai lý do khác nhau — `v0.1` gộp chúng làm một rồi phát biểu sai cả hai.**
   - **#26 `EventSyncHelper` → `Event`: không bị cắt ở phương án nào.** Cả hai đầu là `BC-01` (phép gán ở §2), nên **không** phương án nào đặt chúng vào hai ranh giới. Nó **không được tính** vào một con số nào ở §7.
   - **#13 `SeatMapSyncService` → `SeatStatusChangedEvent`: bị cắt, nhưng đã ở dạng mềm.** Nó đi qua lớp sự kiện miền, nên khi đường cắt `BC-01` | nguồn cung chạy qua nó thì **đổi vận chuyển chứ không phải gỡ ghép nối**. Nó **vẫn được tính** vào §7 vì phép đo 1 đếm *cạnh bị cắt*, và §7 ghi riêng số cạnh không sinh công gỡ để con số chi phí đọc đúng.

   > ⚠️ **Vì sao phải tách hai dòng này.** `v0.1` viết #13 là *“cạnh duy nhất trong 26 không phải gỡ ở bất kỳ phương án nào”* rồi vẫn tính nó vào bốn phương án, trong khi §1 khai các con số §7 đo *“công di trú từ mã hiện có”*. Hai phát biểu đó không cùng đứng được. Cách sửa ở `v0.2`: giữ #13 trong phép đếm **cạnh bị cắt** (đúng định nghĩa phép đo 1 ở §2) và tách *công gỡ* thành một cột riêng ở §7.7.
3. **Bảy cạnh `payment → booking` là phát hiện mới của vòng này** và chúng nặng như nhóm `booking → event`: sáu cạnh thực thể/repository trên `Order`, một cạnh service. ⚠️ **`v0.1` viết “mọi phương án trong sáu đều tách `BC-03` khỏi `BC-02`, nên bảy cạnh này bị cắt ở cả sáu — lại một chi phí chung, không phân biệt”. Sai.** `B11-A` §4.1 đặt `BC-02`, `BC-03`, `BC-04` và `BC-06` **cùng trong `RG-1`** ở `PA-1`, nên cả bảy cạnh `payment` → `booking` **nằm gọn trong một ranh giới ở `PA-1` và không cạnh nào bị cắt** — đúng như §7.1 tự đếm (bốn cạnh chắc chắn, không có #14–#20 trong danh sách). Phát biểu đúng: **bảy cạnh này bị cắt ở năm phương án `PA-2`–`PA-6`, và ở `PA-1` thì không.** Đây là một **dữ kiện phân biệt chi phí di trú** giữa `PA-1` và năm phương án kia, cùng chiều với việc `B11-A` xếp chuỗi `E8`+`E10`+`E11` là `N1` ở `PA-1` và `N4` ở năm phương án kia (§6.1). ⛔ Đọc đúng chiều: đây là một **dữ kiện chi phí di trú**, **không** phải lý do chọn `PA-1` — khối ⛔ ở §1 áp nguyên cho dòng này.

---

## 5. Dữ liệu — ba `FACT` đổi cách đọc chi phí di trú

### 5.1 Lược đồ đã chia theo module, và **không có một khoá ngoại xuyên schema nào**

Sáu schema PostgreSQL: `event_schema`, `booking_schema`, `payment_schema`, `promotion_schema`, `discovery_schema`, `ai_schema`. Phép quét toàn bộ `database/postgresql/*.sql` cho **kết quả rỗng** ở câu hỏi *"có `REFERENCES` nào trỏ sang schema khác không"*.

Tham chiếu xuyên schema được làm bằng **ID mềm kèm bản sao đọc**, và lược đồ **tự chú thích như vậy**: `booking_schema.orders` có `event_id UUID` dưới ghi chú *"LOGICAL REFERENCE - cross schema"*, kèm `event_title`, `event_start_datetime`, `event_venue_name`; `promotion_id` cũng vậy, kèm `promotion_code`.

> ✅ **Đây là một tài sản dùng lại đáng kể mà vòng này tìm được.** `R5` (`GOV-045`) đòi *"giữa các ranh giới **không** khóa ngoại, `JOIN`, repository hoặc truy vấn trực tiếp; liên kết ngoài miền là ID mềm"*. Ở **tầng lược đồ**, phần *khoá ngoại* và *ID mềm* của kỷ luật ấy **đã đạt sẵn** — và các cột sao chép sẵn có (`event_title`, `event_start_datetime`, `event_venue_name`, `promotion_code`) là hiện thực của đúng loại *điểm sao chép* mà `B11-A` §3.4 định nghĩa.
>
> ⚠️ **Nhưng chỉ ở tầng lược đồ.** Ở tầng mã, **tám** cạnh repository ở §4.2 — #5, #6, #10, #11, #15, #17, #19, #25, trong đó **bảy** cạnh xuyên năng lực và #10 nằm gọn trong nguồn cung — **vi phạm cùng dòng `R5`** ở vế *"repository hoặc truy vấn trực tiếp"*. Hai tầng nói ngược nhau, và đó chính là điều `B5.5` §2.2 ghi. Công di trú nằm ở tầng mã, không ở tầng lược đồ.

### 5.2 **Hai** bảng mang hai nghĩa: `event_schema.ticket_types` và `promotion_schema.promotions`

Bảng này chứa **cùng lúc**:

| Nhóm cột | Nghĩa | Thuộc |
|---|---|---|
| `name`, `description` (quyền lợi), `price`, `original_price`, `currency`, `sale_start_datetime`, `sale_end_datetime`, `display_order`, `color_code` | cấu hình gói giá và quyền lợi | `BC-01` |
| `quantity_total`, `quantity_available`, `quantity_reserved`, `max_per_order`, `inventory_mode` | trạng thái nhận cam kết nguồn cung | `BC-02` |

> **Đây là bằng chứng hiện thực cho một cảnh báo `B11-A` đã viết trước khi phiếu này mở.** `B11-A` §4.5(6) ghi bảng *"Ba tên mang hai nghĩa"* và nói `hạn mức bán` nằm **bên trong** mục từ `Loại vé`, `tổng lượt dùng` nằm bên trong mục từ `Khuyến mãi`. Lược đồ cũ xác nhận: hai nghĩa ấy **nằm trong một bảng**. Cảnh báo đó được suy từ `B2` và `B7`, không từ mã — và mã khớp.

**Bảng thứ hai, `v0.2` bỏ sót: `promotion_schema.promotions`** (`V2__complete_schema.sql` dòng 558–605).

| Nhóm cột | Nghĩa | Thuộc |
|---|---|---|
| `code`, `name`, `description`, `discount_type`, `discount_value`, `max_discount_amount`, `min_order_value`, `min_quantity`, `applicable_scope`, `applicable_event_ids`, `applicable_category_ids`, `start_datetime`, `end_datetime`, `target_user_type`, `status` | cấu hình chương trình khuyến mãi của organizer | `BC-01` |
| `max_total_uses`, `max_uses_per_user`, `current_uses` — khối `-- Usage Limits`, dòng 579–582 | trần lượt dùng và **lượt đã dùng** | `BC-02` |

> **Hai dữ kiện buộc phải xếp khối `-- Usage Limits` về `BC-02`.** (1) `current_uses` là **trạng thái bị tiêu thụ**, không phải cấu hình: `V2` dòng 1320–1333 định nghĩa trigger `increment_promotion_usage()` chạy `AFTER INSERT ON promotion_usages` và `UPDATE promotions SET current_uses = current_uses + 1` — cấu trúc giống hệt `ticket_types.quantity_available` bị lượt đặt vé đẩy. (2) `max_total_uses` **chính là** `tổng lượt dùng`, và `B11-A` §4.5(6) xếp `tổng lượt dùng` về phía **cam kết/sử dụng**: *“`Khuyến mãi` | Cấu hình giảm giá của organizer — **và `tổng lượt dùng`** | Tổng lượt đang giữ và đã chốt”*.
>
> ⚠️ **Đây là bảng thứ hai mà cảnh báo `B11-A` §4.5(6) nói tới, và `v0.2` chỉ tìm một.** Cảnh báo ấy nêu **ba tên**: `Sector`/`Ghế`, `Loại vé`, `Khuyến mãi`. `v0.2` tìm được hiện thực cho hai tên đầu (`ticket_types`) rồi dừng, dù chính §3.1 của nó đã tách `Promotion` (cấu hình) về `BC-01` và `PromotionUsage` về `BC-02` — tức nó đã biết tên thứ ba mang hai nghĩa mà không đi tìm bảng.

`event_schema.event_seat_inventory` cũng đáng ghi: nó mang `status` (`AVAILABLE`/`LOCKED`/`RESERVED`/`SOLD`/`BLOCKED`), `locked_by_user_id`, `locked_by_session_id` — tức **trạng thái giữ chỗ đang nằm trên dòng tồn kho**, không nằm trên một aggregate `Giữ chỗ`. Đọc cùng `PRJ-009`, bức tranh nhất quán: hiện thực không có khái niệm giữ chỗ độc lập.

### 5.3 Một tài khoản cơ sở dữ liệu duy nhất

`core-service` và `discovery-service` đều kết nối bằng `POSTGRES_USER: postgres` tới cùng một database (`docker-compose.apps.yml` dòng 54 và 107). Trong toàn bộ `database/postgresql/*.sql` **không có một lệnh `GRANT`, `CREATE ROLE` hay `CREATE USER` nào**.

Hệ quả: vế *"mỗi đơn vị dùng credential chỉ có quyền trên phần của mình"* của `R5`, và vế *"phân quyền **thật**, không phải quy ước"* của `R3`/`ASR-12`, **chưa có một dòng hiện thực nào**. Đây là phần xây mới ở **cả sáu** phương án; khối lượng tăng theo số ranh giới nhưng bản chất giống nhau.

---

## 6. Kiểm kê các họ giao dịch cục bộ mà phương án đích có thể đem chia

Hai thân giao dịch ở §6.1 và §6.2 là **hai ca chịu lực được phân tích chi tiết**, không phải toàn bộ phạm vi. Phép quét tất cả phương thức ghi dữ liệu có `@Transactional` cho thấy bảy họ thao tác cần được kiểm tra. Hạt đếm là **kết quả nghiệp vụ**, không phải số chú giải, để lời gọi lồng nhau không bị đếm hai lần.

| Mã | Họ thao tác hiện tại | Điểm vào và dữ liệu bị chạm | Khi nào phải tháo khỏi một giao dịch cục bộ |
|---|---|---|---|
| `TF-01` | Khởi tạo thanh toán | `PaymentService.initiatePayment()` — đọc/ghi `Order` và tạo `Transaction` | Khi `BC-02` và `BC-03` ở hai ranh giới |
| `TF-02` | Xác nhận thu tiền và phát hành vé | `VNPayIPNService.processIPN()` gọi `TicketIssuanceService.issueTickets()` — `Transaction`, `Order`, `Ticket`, ghế/tồn kho và bộ đếm sự kiện | Khi tiền, đơn, quyền tham dự hoặc cấu hình sự kiện nằm ở các ranh giới khác nhau |
| `TF-03` | Tạo đơn, giữ nguồn cung và giữ lượt khuyến mãi | `BookingService.createBooking()` gọi `SeatBookingService`, `TicketReservationService`, `PromotionService.reservePromotion()` | Khi `BC-02` bị chia bên trong hoặc khi cấu hình ở `BC-01` tách khỏi phần sử dụng ở `BC-02` |
| `TF-04` | Hủy hoặc làm hết hạn đơn và trả tài nguyên | `BookingService.cancelOrder()`; `OrderExpirationHelper.expireOne()`; các phép khôi phục ghế/tồn kho và trả lượt khuyến mãi | Khi Đơn hàng, nguồn cung hoặc phần sử dụng khuyến mãi ở các ranh giới khác nhau |
| `TF-05` | Phát hành vé bất đồng bộ | `TicketMessageListener` gọi `TicketIssuanceService.issueTickets()` — đọc đơn, ghi vé, xác nhận ghế/tồn kho và tăng bộ đếm sự kiện | Khi `BC-04` tách khỏi `BC-02` hoặc `BC-01` |
| `TF-06` | Tạo, tải lên và ghi lại mã vé | `QRCodeService.generateAndUploadForTickets()` — dịch vụ giao nhận gọi Cloudinary rồi ghi `Ticket` | Khi `BC-06` và `BC-04` ở hai ranh giới; tác dụng phụ Cloudinary cũng không được rollback cùng PostgreSQL |
| `TF-07` | Giữ, xác nhận và trả lượt khuyến mãi | `PromotionService.reservePromotion()`, `confirmPromotion()`, `releasePromotion()` — đọc cấu hình `Promotion`, ghi lượt dùng và bộ đếm | Khi cấu hình khuyến mãi ở `BC-01` và phần cam kết/sử dụng ở `BC-02` tách nhau. Đây là **nhóm con** của `TF-03`/`TF-04`, được liệt kê riêng để không bỏ sót phép tách bảng và trách nhiệm xác nhận |

> ⚠️ **Một lỗ hổng vòng đời lộ ra khi kiểm kê:** không tìm thấy nơi gọi `PromotionService.confirmPromotion()`. Vì vậy mã có cơ chế giữ và trả lượt, nhưng chưa chứng minh bước chuyển lượt đang giữ sang lượt đã chốt. Dữ kiện này trực tiếp hạ mức phát biểu về `INV-06` ở §6.2 và §7.6.

### 6.1 `VNPayIPNService.processIPN()` — chuỗi thu tiền → phát hành

`payment/service/VNPayIPNService.java` dòng 90–91 mở **một `@Transactional`**; dòng 161 gọi thẳng `ticketIssuanceService.issueTickets(order.getId())`. Nghĩa là chuỗi mà cả sáu phương án gọi là `E8`+`E10`+`E11` hôm nay chạy **trong một giao dịch ACID cục bộ**: lỗi phát hành sau khi đã ghi nhận thu tiền được xử lý bằng **rollback** (dòng 174–181, `setRollbackOnly()`), không bằng bù trừ.

> ⛔ **Đọc chữ “rollback” cho đúng: nó KHÔNG phải một cách xử lý chân tiền, nó là việc KHÔNG xử lý chân tiền.** IPN của VNPay là thông báo **sau khi tiền đã được thu** (`VNPayIPNController` dòng 29–31). Rollback chỉ hoàn tác PostgreSQL; nó **không** hoàn tác khoản tiền đã vào VNPay. Ba dữ kiện làm rõ hệ quả:
> - **Không có đường hoàn tiền nào trong mã.** `Transaction` có bốn cột theo dõi hoàn tiền nhưng **không dòng mã nào gán chúng**; `OrderStatus.REFUNDED` và `TransactionStatus.REFUNDED` là hằng enum **không bao giờ được gán**. Khớp §3.2 dòng 3.
> - **Không có đối soát.** `VNPayProperties` dòng 55–59 khai endpoint QueryDR kèm chú thích *“Dùng khi IPN không đến sau N phút”*; **`apiUrl` không được gọi ở đâu cả**.
> - **Mã cũ tự ghi ra trạng thái tiền-vào-vé-không-ra.** Đơn bị rollback về `PENDING` rồi hết hạn; lượt IPN thử lại sau đó rơi vào dòng 153–157 `recordLateSuccessWithoutIssuance`, và dòng 231–234 lưu nguyên văn *“Payment success received after order became …; ticket was not issued”* — rồi **không làm gì với khoản tiền**.
>
> **Hệ quả cho phép so sánh:** *“không phải xây bù trừ”* ở §7.1 chỉ đúng cho **bù trừ xuyên ranh giới** (Saga). Đường hoàn tiền, đối soát và thử lại bền vững là **xây mới ở cả sáu**, `PA-1` không ngoại lệ — xem §3.2 dòng 3. `v0.2` phát biểu quá nhẹ chỗ này; `v0.3` sửa.

> ⛔ **Hệ quả:** trong mã hiện tại **không tồn tại một dòng logic bù trừ nào** cho chuỗi này. `B11-A` xếp nó là ứng viên `N4` ở **năm** phương án (`PA-2`, `PA-3`, `PA-4`, `PA-5`, `PA-6`) và là `N1` ở `PA-1` — nơi `BC-02`, `BC-03`, `BC-04`, `BC-06` nằm chung `RG-1`. Với năm phương án kia, **toàn bộ** phần bù trừ, trạng thái Saga, idempotency xuyên ranh giới và kiểm thử lỗi là **xây mới**.

Hai thứ **dùng lại được** ở chỗ này: khoá idempotency bằng Redis `SetNx` (dòng 94, có TTL) và mẫu `@TransactionalEventListener(AFTER_COMMIT)` đã dùng cho `PaymentSuccessEvent` (dòng 282) — mẫu này chính là hình dạng *một chiều đi bằng sự kiện miền* mà mục (12) của cả sáu phương án nêu làm **điều kiện cần kiểm ở `B13`**.

### 6.2 `BookingService.createBooking()` — toàn bộ tranh chấp của `ASR-01` trong một giao dịch

`booking/service/BookingService.java` dòng 97–98 mở **một `@Transactional`**. Trong đúng một giao dịch đó, thân hàm chạm:

| Dòng | Thao tác | Chạm dữ liệu của |
|---:|---|---|
| 144 | kiểm đơn chờ trùng người dùng + sự kiện | Đơn hàng |
| 152 | `eventRepository.findByIdAndIsDeletedFalse` | `BC-01` |
| 159 | `bookingCompositionPolicy.validateSingleInventoryMode` | nguồn cung |
| 167 | `ticketReservationService.acquireGlobalLocks` | khoá Redisson trên nguồn cung |
| 175 | `seatBookingService.validateSeats` | nguồn cung |
| 195 | `ticketInventoryCounterService.findAvailableQuantity` | nguồn cung |
| 218 | `ticketInventoryCounterService.reserveQuantity` | nguồn cung |
| 232 | `ticketInventoryCounterService.markSoldOutIfEmpty` | nguồn cung |
| 245 | `promotionService.reservePromotion` | lượt dùng khuyến mãi |
| 251 | `orderRepository.save` | Đơn hàng |
| 255 | `orderItemRepository.saveAll` | Đơn hàng |
| 262 | `seatBookingService.reserveSeats` | nguồn cung |
| 396 | `ticketTypeRepository.findByIdAndIsDeletedFalse` | **hai nghĩa** |

Tức: cơ chế hiện tại **giữ được phần tương ứng với `INV-01` và `INV-03`** bằng một giao dịch cục bộ ACID cộng một khoá phân tán Redisson, trên ba schema (`event_schema`, `booking_schema`, `promotion_schema`) qua một `EntityManager`. Với **`INV-06`**, mã chỉ có **một số cơ chế thành phần**: giữ/trả nguồn cung và lượt khuyến mãi theo đơn. Nó **không chứng minh được bất biến đầu-cuối** vì chưa có root `Giữ chỗ` và `Giới hạn mua` đúng nghĩa, không tìm thấy nơi gọi `confirmPromotion()`, và chưa có bằng chứng hội tụ cùng chống trả tài nguyên hai lần trên mọi đường hủy, hết hạn và lỗi phát hành.

> ⛔ **`INV-04` KHÔNG nằm trong danh sách trên, và `v0.2` đã xếp nhầm nó vào.** §3.2 dòng 4 của chính phiếu này khai rằng thứ mã đang chạy — kiểm một đơn `PENDING` trùng người dùng+sự kiện (dòng 144) và hai mức `max_per_order` (dòng 373–380, 416–419) — **không phải** `Giới hạn mua` theo nghĩa `B2` (*tài khoản trên sự kiện, cộng dồn cả đang giữ và đã mua*). Phép quét toàn cây cho `max_tickets_per_user`, `maxPerUser`, `countByUserIdAndEventId` **không trả về vị trí nào**, và lược đồ chỉ có cột theo **đơn**. Không giao dịch hay khoá nào đang giữ `INV-04` vì **không có phép kiểm nào để mà giữ**. Một người mua tối đa mỗi đơn, thanh toán xong (đơn thành `CONFIRMED` nên phép kiểm dòng 144 hết hiệu lực), rồi lặp lại không giới hạn.
>
> ⚠️ **Lịch sử hiệu đính:** `v0.2` từng tính nhầm `INV-04`; `v0.3` bỏ `INV-04` nhưng vẫn phát biểu quá mạnh rằng `INV-06` đã được giữ. `v0.6` sửa nốt: `INV-06` chỉ có cơ chế thành phần, chưa được chứng minh đầu-cuối. §3.2 vẫn đúng khi xếp `Giới hạn mua` vào phần xây mới của mọi phương án.

**Mười lăm vị trí gọi trong cùng package mà một đường cắt bên trong `BC-02` hoặc quanh `BC-04` sẽ cắt** — danh sách mà phép đo 1 mù, viết ra để đếm lại được.

> ⚠️ **`v0.2` chỉ liệt kê tám, và con số đó sai theo ba hướng — cả ba đều kéo con số xuống.** (1) Nó bỏ toàn bộ **lời gọi repository trong cùng package**, trong khi chính §4.2 *Đọc ra* mục 1 lập luận ghép nối repository là loại **nặng hơn**; loại đúng nhóm nặng nhất khỏi phép đếm sinh ra để bù chỗ mù là tự vô hiệu hoá phép đo. (2) Nó bỏ `BookingService:284`, một lời gọi bean tới `TicketReservationService` **cùng loại hệt** dòng `b` mà nó có ghi, trong cùng một phương thức. (3) Nó **thừa** một dòng: `bookingCompositionPolicy` được chính §4.2 cạnh #1 và §3.1 gán về **Đơn hàng**, và lớp đó không có phụ thuộc tiêm nào — nó nhận sẵn một `Collection<InventoryMode>` dựng ở `BookingService:160` — nên đường cắt Đơn | nguồn cung **không** cắt nó.

**Nhóm 1 — chín vị trí bị cắt bởi đường cắt `Đơn hàng` | `nguồn cung`** (`PA-4`, `PA-5`):

| # | Vị trí | Gọi tới | Loại |
|---:|---|---|---|
| a | `BookingService:167` | `ticketReservationService.acquireGlobalLocks` | service |
| b | `BookingService:175` | `seatBookingService.validateSeats` | service |
| c | `BookingService:262` | `seatBookingService.reserveSeats` | service |
| d | `BookingService:284` | `ticketReservationService::releaseLock` | service **(mới ở `v0.3`)** |
| e | `BookingService:339` | `seatBookingService.restoreSeatsForOrder` | service |
| f | `BookingService:524` | `seatBookingService.restoreSeatsForOrder` | service |
| g | `SeatBookingService:78` | `orderItemSeatRepository.saveAll` | **repository (mới)** |
| h | `SeatBookingService:93` | `orderItemSeatRepository.updateStatusForOrder` | **repository (mới)** |
| i | `SeatBookingService:111` | `orderItemSeatRepository.updateStatusForOrder` | **repository (mới)** |

**Nhóm 2 — sáu vị trí bị cắt khi `BC-04` tách khỏi `Đơn hàng` và nguồn cung** (năm phương án, tất cả trừ `PA-1`):

| # | Vị trí | Gọi tới | Loại |
|---:|---|---|---|
| j | `TicketIssuanceService:71` | `orderRepository.findById` | **repository (mới)** |
| k | `TicketIssuanceService:89` | `orderItemRepository.findByOrderId` | **repository (mới)** |
| l | `TicketIssuanceService:97` | `orderItemSeatRepository.findByOrderItemId` | **repository (mới)** |
| m | `TicketIssuanceService:169` | `seatBookingService.attachTicketToSeatInventory` | service |
| n | `TicketIssuanceService:182` | `seatBookingService.confirmSeatsSold` | service |
| o | `TicketMessageListener:62` | `orderRepository.findById` | **repository (mới)** |

Chín vị trí `a`—`i` bị cắt bởi `PA-4` và `PA-5`. Sáu vị trí `j`—`o` bị cắt bởi **năm** phương án — tất cả trừ `PA-1`, phương án gom `BC-02`, `BC-03`, `BC-04`, `BC-06` vào `RG-1` nên **không vị trí nào trong mười lăm bị cắt**.

> ✅ **Một phép kiểm ngược đã chạy và cho kết quả sạch:** đường cắt `BC-01` \| **nguồn cung** — tức cắt **bên trong** package `event` — **không cắt một vị trí gọi nào trong cùng package**. `TicketInventoryCounterService` không có lời gọi nào từ bên trong package `event`; mọi lời gọi tới nó đến từ `booking` và đã nằm trong 26 cạnh (#7, #12). Đường cắt này vì vậy tốn **một phép tách bảng** (§5.2) chứ không tốn công gỡ lời gọi.

---

## 7. Đối chiếu từng phương án đã duyệt

Sáu mục dưới đây theo **đúng thứ tự `B11-A`** và dùng **đúng một bộ năm câu**. Không mục nào so sánh phương án này với phương án khác trừ khi phép so đó là một dữ kiện đếm được, và **không mục nào kết luận phương án nào nên được chọn**.

### 7.1 `PA-1` — Lõi giao dịch gộp · 3 ranh giới

| Câu | Kết quả |
|---|---|
| **Cạnh mã bị cắt** | **4–6 / 26** — chắc chắn #2, #5, #11, #13; cộng #3, #6 tuỳ cách tách `ticket_types`. Trong đó **#13 không sinh công gỡ** (§4.2). *(`v0.1` ghi 5–7 vì tính cả #26, nay là cạnh nội bộ `BC-01` — xem §2.)* |
| **Bảng phải tách** | **2** — `event_schema.ticket_types` (cấu hình ở `RG-2`, tồn kho theo `BC-02` ở `RG-1`) **và `promotion_schema.promotions`** (cấu hình ở `RG-2`, `max_total_uses`/`current_uses` ở `RG-1`). `event_seats`, `event_seat_inventory` chuyển sang `RG-1`. *(`v0.2` ghi 1 vì §5.2 chỉ xét một bảng.)* |
| **Họ giao dịch chạm đường cắt (§6)** | `TF-02`, `TF-03`, `TF-05`, `TF-07`. Lõi đơn–tiền–vé–giao nhận cùng `RG-1`, nhưng phép tăng bộ đếm sự kiện và phần cấu hình/sử dụng khuyến mãi đi qua `RG-2`. Không cần bù trừ xuyên ranh giới cho lõi thu tiền → phát hành; đường hoàn tiền và đối soát vẫn phải xây |
| **Vị trí gọi cùng package bị cắt** | **0 / 15** — phương án duy nhất không cắt vị trí nào |

**Dùng lại được:** `RG-1` gom package `booking`, `payment`, `notification` và phần nguồn cung của `event` để chuyển cùng một khối. **Xây mới chung:** `BC-05`, root `Giữ chỗ`, `Yêu cầu hoàn tiền`, `Giới hạn mua` và `Yêu cầu hủy sự kiện` (§3.2, áp cho cả sáu). **Di trú riêng:** tách hai bảng mang hai nghĩa và thay các phép đọc/ghi đi qua `RG-2`.

**Rủi ro khả thi riêng:** phép tách `ticket_types` phải chạy trên **đúng bảng nóng nhất** — bảng mang `quantity_available`/`quantity_reserved` mà `TicketInventoryCounterService` cập nhật dưới khoá. Việc tách cột cấu hình sang `RG-2` phải giữ được đường đọc giá lúc tạo đơn (`BookingService:396`).

### 7.2 `PA-2` — Tách tiền khỏi vé · 4 ranh giới

| Câu | Kết quả |
|---|---|
| **Cạnh mã bị cắt** | **9 / 26** — #11, #12, #14–#19 (sáu cạnh `payment → Order`), #20. Không phụ thuộc cách tách `ticket_types` |
| **Bảng phải tách** | **0** — **phương án duy nhất trong sáu không phải chẻ bảng nào.** `BC-01` và `BC-02` cùng `RG-1`, nên cả `ticket_types` lẫn `promotions` nằm trọn một phía. Việc chuyển `tickets` sang `RG-3` là **phân vùng schema**, không phải chẻ bảng: `orders` (dòng 710) và `tickets` (dòng 849) vốn đã là **hai bảng riêng**, không bảng nào mang cột của năng lực kia. *(`v0.1` và `v0.2` đều ghi 1 vì đếm phân vùng schema thành chẻ bảng.)* |
| **Họ giao dịch chạm đường cắt (§6)** | `TF-01`, `TF-02`, `TF-05`. `BC-01` và `BC-02` cùng `RG-1`, nên tạo/hủy/hết hạn đơn và khuyến mãi giữ cục bộ; thanh toán và phát hành vé tách sang `RG-2`/`RG-3` |
| **Vị trí gọi cùng package bị cắt** | **6 / 15** — trọn nhóm 2 (`j`–`o`), vì `BC-04` tách khỏi `Đơn hàng` và nguồn cung |

**Dùng lại được:** package `event` và package `promotion` chuyển vào `RG-1` cùng phần đơn của `booking`; package `payment` vào `RG-2`. **Xây mới chung:** `BC-05`, root `Giữ chỗ`, `Yêu cầu hoàn tiền`, `Giới hạn mua` và `Yêu cầu hủy sự kiện`. **Di trú riêng:** bù trừ chuỗi thu tiền → phát hành; tách `Ticket` khỏi `booking` sang `RG-3` cùng `notification`.

**Rủi ro khả thi riêng:** sáu cạnh `payment → Order` là **thực thể và repository trực tiếp**, và `PaymentValidatorService` đọc `Order` để **quyết định tính hợp lệ của khoản thu**. Đổi phép đọc đó thành một lời gọi xuyên ranh giới đưa một quyết định hợp lệ vào thế phụ thuộc một bản đọc có thể cũ — thuộc `B13`, ghi ở đây để `B13` không gặp bất ngờ.

### 7.3 `PA-3` — Mỗi bounded context một ranh giới · 7 ranh giới

| Câu | Kết quả |
|---|---|
| **Cạnh mã bị cắt** | **15–17 / 26** — #2, #5, #11, #12, #13, #14–#19, #20, #23, #24, #25; cộng #3, #6 tuỳ cách tách `ticket_types`. Trong đó **#13 không sinh công gỡ**. *(`v0.1` ghi 16–18 vì tính cả #26.)* |
| **Bảng phải tách** | **2** — `event_schema.ticket_types` (cấu hình \| tồn kho) và **`promotion_schema.promotions`** (cấu hình \| tổng lượt dùng). *(`v0.2` ghi đúng con số nhưng sai thành phần: nó tính `booking_schema`, vốn là phân vùng schema.)* |
| **Họ giao dịch chạm đường cắt (§6)** | `TF-01`, `TF-02`, `TF-03`, `TF-05`, `TF-06`, `TF-07`. `TF-04` còn trong `BC-02`; sáu họ còn lại chạm ít nhất hai ranh giới |
| **Vị trí gọi cùng package bị cắt** | **6 / 15** — trọn nhóm 2 (`j`–`o`), vì `BC-04` tách khỏi `Đơn hàng` và nguồn cung |

**Dùng lại được:** `user-service` khớp `RG-07` về **hình dạng triển khai** — nó đã là một tiến trình riêng. ⚠️ **Không phải “nguyên trạng”:** `PRJ-008` đòi **cắt ba phần vượt phạm vi** — lưu ảnh nhận diện (`User.Profile.avatarUrl`, `StorageService`, `CloudinaryConfig`), trường địa chỉ (`User.addresses`, `Address`, `AddressDTO`) và trạng thái khoá tài khoản (`UserStatus`, `Security.lockoutUntil`) — và ghi rằng việc dùng lại `KeycloakEventConsumer` sẽ **chốt hộ** một điểm đang mở (`B4-OPEN-01`), *“phải trình riêng”*. Đánh giá đầy đủ ở **§3.4**. Cùng hình dạng tái sử dụng này áp cho mọi phương án vì cả sáu đều đặt `BC-07` ở một ranh giới riêng. **Xây mới chung:** `BC-05`, root `Giữ chỗ`, `Yêu cầu hoàn tiền`, `Giới hạn mua` và `Yêu cầu hủy sự kiện`. **Di trú riêng:** `RG-06` phải tách khỏi `RG-04` với hai cạnh #24, #25 hiện là thực thể/repository trực tiếp trên `Ticket`.

**Rủi ro khả thi riêng:** bảy tiến trình nghiệp vụ — xem §8.2. `ASR-11` ở `B11-A` là **`Chưa biết`** cho phương án này, và bằng chứng hiện thực ở §8.2 **không đóng được** câu hỏi đó; nó chỉ nói phép đo phải chạy thế nào.

### 7.4 `PA-4` — Tách nguồn cung khỏi đơn · 5 ranh giới

| Câu | Kết quả |
|---|---|
| **Cạnh mã bị cắt** | **16 / 26** — #1 tới #7 (bảy cạnh `RG-1b` → `RG-1a`), #11, #12, #14–#19, #20. Không phụ thuộc cách tách `ticket_types`. *(`v0.1` ghi 17 vì tính cả #26.)* |
| **Bảng phải tách** | **1** — **`promotion_schema.promotions`**: `PA-4` đặt `Khuyến mãi` **cấu hình** ở `RG-1a` (trong `BC-01`) và `Khuyến mãi` **nghĩa sử dụng** cùng `Lượt dùng khuyến mãi` ở `RG-1b`. ⚠️ **`event_schema` không phải tách**: `BC-01` **và** nguồn cung cùng `RG-1a`, nên cả hai nghĩa của `ticket_types` ở một phía. *(`v0.2` ghi đúng con số nhưng sai thành phần.)* |
| **Họ giao dịch chạm đường cắt (§6)** | `TF-01`, `TF-02`, `TF-03`, `TF-04`, `TF-05`, `TF-07`. `TF-06` còn cục bộ vì `BC-04` và `BC-06` cùng ranh giới |
| **Vị trí gọi cùng package bị cắt** | **15 / 15** — cả nhóm 1 (`a`–`i`) lẫn nhóm 2 (`j`–`o`) |

**Dùng lại được:** package `event` chuyển **nguyên khối** vào `RG-1a` — kể cả `TicketInventoryCounterService` và cả hai nghĩa của `ticket_types`; package `payment` vào `RG-2`. **Xây mới chung:** `BC-05`, root `Giữ chỗ`, `Yêu cầu hoàn tiền`, `Giới hạn mua` và `Yêu cầu hủy sự kiện`. **Di trú riêng:** bù trừ chuỗi thu tiền → phát hành và **giao thức tạo/hủy/hết hạn đơn phân tán** thay cho các giao dịch ở §6.

**Rủi ro khả thi riêng:** chín vị trí `a`–`i` cộng bảy cạnh #1–#7 đều nằm **bên trong hoặc ngay cạnh các họ tạo/hủy/hết hạn đơn**, tức bên trong đúng chỗ `ASR-01` phát biểu. Khoá Redisson (`ticketReservationService`, vị trí `b`) hiện được lấy **trong** giao dịch và nhả trong `finally`; khi ranh giới cắt qua đó, chủ khoá và chủ đơn nằm ở hai đơn vị. ⚠️ Đây **không** phải một kết luận rằng phương án bất khả thi — `B11-A` đã ghi `ASR-01` của `PA-4` là **`Chưa biết`** và ghi rõ *"phải chứng minh lợi ích đủ lớn"*; bằng chứng hiện thực **đo được chi phí** của việc chứng minh đó và **không** thay `B11-C` trả lời.

### 7.5 `PA-5` — Giữ chỗ tách khỏi đơn, tiền gộp cả đối soát · 7 ranh giới

| Câu | Kết quả |
|---|---|
| **Cạnh mã bị cắt** | **22 / 26** — mọi cạnh trừ #8, #9, #10 (ba cạnh nội bộ `RG-2`) **và #26** (nội bộ `BC-01`). Trong đó **#13 không sinh công gỡ**. *(`v0.1` ghi 23.)* |
| **Bảng phải tách** | **2** — `event_schema.ticket_types` và **`promotion_schema.promotions`** — `RG-1` giữ cả hai nghĩa cấu hình, `RG-2` giữ cả hai nghĩa cam kết/tổng lượt. *(`v0.2` sai thành phần.)* |
| **Họ giao dịch chạm đường cắt (§6)** | Cả bảy họ `TF-01`–`TF-07`; `TF-07` là nhóm con của tạo/hủy/hết hạn đơn nhưng được giữ riêng để kiểm phần xác nhận lượt khuyến mãi |
| **Vị trí gọi cùng package bị cắt** | **15 / 15** — ngang `PA-4` |

**Dùng lại được:** `user-service` khớp `RG-7` về hình dạng triển khai, với cùng ba phần phải cắt mà `PRJ-008` nêu (đánh giá đầy đủ ở **§3.4**); `SeatBookingService`, `TicketInventoryCounterService`, `TicketReservationService` và `PromotionService` **gom về cùng một ranh giới `RG-2`** — ba cạnh #8, #9, #10 trở thành nội bộ, và khoá Redisson nằm cùng phía với thứ nó bảo vệ. **Xây mới chung:** `BC-05`, root `Giữ chỗ`, `Yêu cầu hoàn tiền`, `Giới hạn mua` và `Yêu cầu hủy sự kiện`. **Di trú riêng:** bù trừ, giao thức vòng đời giữ chỗ, phát hành vé và tạo mã vé xuyên ranh giới.

**Rủi ro khả thi riêng — và nó trùng với một điểm mở `B11-A` đã ghi trước:** `B11-A-OPEN-02` nói trạng thái *giữ chỗ đã cam kết nguồn cung nhưng chưa có đơn* **chưa được mô hình hoá**, vì `B7-v0.12` §4.2 đặt khoá nghiệp vụ của `Giữ chỗ` là *đơn được giữ*. `PRJ-009` cho thấy **hiện thực đang ở cùng phía với mô hình đã duyệt**: bảng `reservations` có sẵn nhưng mã không dùng, và đơn chính là cái giữ chỗ. Tức **không có tài sản hiện thực nào đỡ cho trạng thái ấy**.

> ⛔ **Đọc dòng trên cho đúng, đây là chỗ dễ vượt thẩm quyền nhất của cả phiếu.** Nó **không** nói `PA-5` bất khả thi, và **không** trả lời `B11-A-OPEN-02`. Điểm mở ấy là câu hỏi về **mô hình miền**, owner Lê Văn Minh, gate **`B11-C`**; ba hướng xử lý đã được `B11-A` §8.2 ghi ra **không xếp hạng**. Bằng chứng hiện thực chỉ thêm đúng một dữ kiện: **hướng (b)** — đưa vào một khái niệm bền vững mới — **không** có tài sản dùng lại được, dù lược đồ cũ có sẵn một bảng mang đúng cái tên đó. `B11-B` **không được** dùng dữ kiện này để nghiêng về hướng nào; `PRJ-009` cấm đích danh.

### 7.6 `PA-6` — Mua vé trọn một ranh giới, tiền gộp cả đối soát · 5 ranh giới

| Câu | Kết quả |
|---|---|
| **Cạnh mã bị cắt** | **12–14 / 26** — #2, #5, #11, #12, #13, #14–#19, #20; cộng #3, #6 tuỳ cách tách `ticket_types`. Trong đó **#13 không sinh công gỡ**. *(`v0.1` ghi 13–15 vì tính cả #26.)* |
| **Bảng phải tách** | **2** — `event_schema.ticket_types` và **`promotion_schema.promotions`**. *(`v0.2` sai thành phần.)* |
| **Họ giao dịch chạm đường cắt (§6)** | `TF-01`, `TF-02`, `TF-03`, `TF-05`, `TF-07`. `TF-04` còn trong `BC-02`; `TF-06` còn cục bộ vì `BC-04` và `BC-06` cùng `RG-4` |
| **Vị trí gọi cùng package bị cắt** | **6 / 15** — trọn nhóm 2 (`j`–`o`), vì `BC-04` tách khỏi `Đơn hàng` và nguồn cung |

**Dùng lại được:** `RG-2` gom `Đơn hàng`, nguồn cung, khoá Redisson và lượt dùng khuyến mãi vào một đơn vị, nên trong mười lăm vị trí gọi nó chỉ mất nhóm 2 (`j`–`o`); package `payment` vào `RG-3`. **Xây mới chung:** `BC-05`, root `Giữ chỗ`, `Yêu cầu hoàn tiền`, `Giới hạn mua` và `Yêu cầu hủy sự kiện`. **Di trú riêng:** bù trừ chuỗi thu tiền → phát hành và tách cấu hình khỏi phần sử dụng khuyến mãi.

**Rủi ro khả thi riêng:** `RG-2` nhận `BC-02` **trọn vẹn**, nên nó lấy dữ liệu từ **ba** schema cũ: `event_schema` (tồn kho `ticket_types`, `event_seats`, `event_seat_inventory`), `booking_schema` (`orders`, `order_items`, `order_item_seats`) và `promotion_schema` (`promotions` khối lượt dùng, `promotion_usages`). *(`v0.2` ghi “hai schema” và gọi `RG-2` là ranh giới **duy nhất** lấy từ nhiều schema; cả hai vế đều sai — §3.1 của chính phiếu này đã ghi `BC-02` nằm ở **ba package**, §6.2 ghi giao dịch chạy trên **ba schema**, và `RG-1` của `PA-6` cũng lấy từ hai schema vì `BC-01` gồm `Promotion` cấu hình.)* `B11-A` yêu cầu hình dạng này giữ `INV-06` cục bộ, nhưng mã cũ **chỉ có cơ chế thành phần và chưa chứng minh `INV-06` đầu-cuối** (§6.2). Vì vậy `B12`/`B13` phải thiết kế và kiểm chứng phần còn thiếu, không được coi giao dịch cũ là tài sản đã hoàn chỉnh.

### 7.7 Bảng tổng — sáu cột, đọc kèm cảnh báo ở §1

| Phép đo | `PA-1` | `PA-2` | `PA-3` | `PA-4` | `PA-5` | `PA-6` |
|---|---|---|---|---|---|---|
| Cạnh mã bị cắt (/26) | **4–6** | **9** | **15–17** | **16** | **22** | **12–14** |
| — trong đó **không sinh công gỡ** (#13, §4.2) | 1 | 0 | 1 | 0 | 1 | 1 |
| Bảng phải tách | **2** | **0** | 2 | 1 | 2 | 2 |
| Họ giao dịch chạm đường cắt (§6) | `02, 03, 05, 07` | `01, 02, 05` | `01, 02, 03, 05, 06, 07` | `01, 02, 03, 04, 05, 07` | `01`–`07` | `01, 02, 03, 05, 07` |
| Vị trí gọi cùng package bị cắt (/15) | 0 | 6 | 6 | **15** | **15** | 6 |
| **Năng lực** không có tài sản nào | 1 (`BC-05`) | 1 | 1 | 1 | 1 | 1 |
| — trong đó **đứng riêng thành một ranh giới** | 0 | 0 | **1** | 0 | 0 | 0 |

> ⛔ **Bảng này KHÔNG phải bảng xếp hạng, và ba lý do buộc đọc như vậy.** **(1)** Nó đo *khoảng cách tới mã hiện có*, mà `AGENTS.md` cấm biến khoảng cách đó thành lý do chọn ranh giới. **(2)** Nó **không tương ứng** với bốn con số ở **mục (5) của từng phương án `B11-A`** (chỉ số do `quy-trinh` §4.4 đặt; **đừng đọc thành §4.4 của `B11-A`**, mục đó là của riêng `PA-4`). `B11-A` tách **rõ** `PA-4` (**13** hợp đồng) khỏi `PA-3` (**16**); ở đây hai phương án **chồng dải** — `PA-3` cắt **15–17** cạnh còn `PA-4` cắt **16** — nên thứ tự giữa chúng **không đọc ra được**. Hai bảng đo hai thứ khác nhau và cả hai đều đúng. **(3)** Bảng này **không chứa** thứ `B11-C` cần cân nhất: `B11-A` §6 nhận xét 5 ghi nguyên văn *"Mười trên mười lăm dòng giống hệt nhau ở cả sáu cột"*, nên việc chọn dựa vào chi phí phối hợp **đo được** cộng kết quả phiếu này — chứ không dựa vào riêng một trong hai.

---

## 8. Ràng buộc chung — áp cho cả sáu, không phân biệt phương án

### 8.1 Dữ liệu quan sát và mã tương quan: gần như trắng

| Thành phần | Có gì | Thiếu gì |
|---|---|---|
| `apigateway` | `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `actuator` | — |
| `core-service` | chỉ `actuator` | không tracing, không MDC, không cấu hình log JSON |
| `user-service` | chỉ `actuator` | như trên |
| `discovery-service` | chỉ `actuator` | như trên |

Phép quét toàn bộ mã nguồn cho các chuỗi `correlation`, `traceId`, `trace_id`, `MDC`, `sleuth`, `opentelemetry` **không trả về một vị trí nào trong mã nghiệp vụ**; `core-service/src/main/resources/application.yml` **không có khối `logging`**.

> ⛔ **Hệ quả cho `R7` và `ASR-08`** — *"mọi bản ghi thuộc cùng một giao dịch truy được qua một mã tương quan duy nhất, đi qua **mọi** bước chuyển kể cả bất đồng bộ"*: ba service nghiệp vụ **chưa có gì để dùng lại**. Cầu tracing ở `apigateway` bắt đầu một dấu vết nhưng không có gì mang nó qua RabbitMQ hay qua các bước bất đồng bộ.
>
> **Đây cũng là điều kiện đầu vào của `RES-023` mức 2** — FlashTicket phải **chạy thật** cơ chế chẩn đoán trên dữ liệu quan sát của chính nó (`NFR-12`, `RES-038`). Chuẩn dữ liệu quan sát chốt ở `B16`, nhưng phiếu này ghi được một điều `B16` sẽ cần: **khối lượng ở đây là xây mới ở mọi phương án, và nó tăng theo số ranh giới**, vì mỗi ranh giới thêm vào là một tiến trình nữa phải mang mã tương quan qua.

### 8.2 Hạ tầng: bằng chứng đã có, và ba thứ chưa được đo

**Bằng chứng đã có** (`B5.5` §3.2, `FACT`): repo gốc chạy trọn vẹn trên **một** máy `m7i-flex.large` (2 vCPU, 8 GiB) với **6 tiến trình JVM** (`eureka`, `config`, `gateway`, `core`, `user`, `discovery`) cộng PostgreSQL, MongoDB, Redis, RabbitMQ, Keycloak, **không gặp lỗi hết bộ nhớ**. Điểm chưa kiểm chứng: **chưa chạy thí nghiệm tải**.

**Đếm tài nguyên theo kịch bản, không dán một “tổng” giả.** Mốc hiện thực là **bảy JVM**: sáu tiến trình ứng dụng Java mà `B5.5` đã liệt kê cộng Keycloak. Với hình dạng đích, chỉ ba nhóm sau đã đủ căn cứ để đếm: số ranh giới nghiệp vụ; ba tiến trình nền `eureka`, `configserver`, `apigateway`; và Keycloak. Chatbot là một **kênh**, còn cơ chế chẩn đoán có thể chạy riêng hoặc đồng vị trí; cả hai chưa được chốt thành tiến trình riêng, và công nghệ chạy của chúng cũng chưa chắc là JVM.

| Kịch bản đếm | `PA-1` | `PA-2` | `PA-3` | `PA-4` | `PA-5` | `PA-6` |
|---|---|---|---|---|---|---|
| Ranh giới nghiệp vụ | 3 | 4 | 7 | 5 | 7 | 5 |
| JVM đã có căn cứ nếu mỗi ranh giới nghiệp vụ chạy bằng JVM, cộng 3 tiến trình nền và Keycloak | **7** | **8** | **11** | **9** | **11** | **9** |
| Chênh so với mốc hiện thực 7 JVM, chưa tính hai đơn vị chưa quyết | 0 | +1 | +4 | +2 | +4 | +2 |
| Đơn vị ứng dụng bổ sung chưa quyết — chatbot và chẩn đoán | 0–2 | 0–2 | 0–2 | 0–2 | 0–2 | 0–2 |
| Nếu cả hai đều chạy JVM riêng — chỉ là kịch bản biên để đo, không phải quyết định | 7–9 | 8–10 | 11–13 | 9–11 | 11–13 | 9–11 |

> ⚠️ Bảng chỉ xác định **dải phải đo**. Nó không khẳng định chatbot hay cơ chế chẩn đoán sẽ chạy riêng, không khẳng định chúng dùng JVM, và không biến số tiến trình thành thuộc tính chất lượng của phương án.

**Ba thứ chưa được đo, và cả ba là nghĩa vụ của vòng đo thử chứ không phải kết luận của phiếu này:**

1. ⚠️ **Không một tệp cấu hình nào đặt kích thước heap.** Phép quét `docker-compose*.yml` và `run-all-services.ps1` cho `JAVA_OPTS`, `Xmx`, `Xms`, `mem_limit` trả về **rỗng**. Mặc định JVM lấy tối đa ~25% RAM khả kiến, nên trên một máy 8 GiB, **bảy** JVM cùng khai tối đa ~2 GiB là **quá cam kết ~14 GiB trên 8 GiB**. *(`v0.2` viết “sáu JVM … ~12 GiB” vì kế thừa phép đếm của `B5.5` §3.2 vốn để Keycloak ra ngoài nhóm JVM; con số đúng lớn hơn, tức cảnh báo này trước đây **nhẹ hơn** thực tế.)* Hôm nay không vỡ vì chúng không cùng chạm trần. **Đặt heap tường minh là điều kiện tiên quyết của mọi phép đo tải**, ở cả sáu phương án — nếu không, con số đo được sẽ đo cấu hình mặc định chứ không đo kiến trúc.
2. **Hai máy thuộc hai tài khoản AWS riêng** (`B5.5` §3.3): hai VPC rời, lưu lượng giữa hai máy đi qua Internet công cộng, độ trễ **dao động**, security group không tham chiếu chéo được. Cộng thêm một dữ kiện mà vòng này tìm ra: **khoá Redisson** ở `TicketReservationService` (`waitTime` 10s, `leaseTime` 30s) là khoá phân tán qua Redis. Nếu ranh giới giữ khoá và ranh giới gọi nó nằm ở hai máy khác tài khoản thì **mỗi lần lấy khoá đi qua Internet công cộng**. Đây là **nghĩa vụ ghi nhận khi đo** cho mọi cách bố trí, không phải một lý do loại bố trí nào — `ASR-11` khai *"bố trí chưa chốt"*.
3. **`ASR-06` chưa có ngưỡng** (`B10-OPEN-01`, `B9-OPEN-01` — `B11-A` §6 nhận xét 1 dẫn cả hai), nên không phép đo nào ở đây so sánh được bằng số. `B11-A` §6 đã ghi `ASR-06` là `Chưa biết` ở **cả sáu cột**; bằng chứng hiện thực **không đổi ô nào**.

> ✅ **Kết luận của mục này, phát biểu đúng mức:** bằng chứng hiện thực **không loại phương án nào** vì lý do tài nguyên, và cũng **không xác nhận** phương án nào chạy được ở tải cao. Nó bác đúng một phát biểu — *"2 EC2 chắc chắn không đủ"* — đúng như `B5.5` §3.2 đã bác, và giữ nguyên `ASR-11` = `Chưa biết` cho `PA-3` và `PA-5` như `B11-A` ghi.

> **Cập nhật sau cổng, không sửa bằng chứng so sánh:** `GOV-097` thu hồi yêu cầu benchmark tải trước khi chấp nhận ADR và thay bằng ngân sách mục tiêu khoảng 6 GiB tiến trình/container mỗi máy, có công bố heap/memory limit; thử tải thật chuyển sang Giai đoạn 5–6. `GOV-098` cho phép nâng instance AWS tạm thời nếu cấu hình hiện tại thiếu. Hai quyết định này đóng `B11-B-OPEN-03` như **blocker của B11**, nhưng không biến phần chưa đo thành kết quả chịu tải.

### 8.3 Phân quyền: có vai trò, chưa có credential theo ranh giới

Mã có **17 chú giải `@PreAuthorize`** trên ba vai trò — `ORGANIZER` (10), `BUYER` (4), `ADMIN` (2), `ORGANIZER|ADMIN` (1) — và Keycloak làm nguồn danh tính. Vế **sở hữu** của `ASR-14` cũng đã có một phần: `OrganizerEventService` có `findOwnedEvent` và tự ghi *"IDOR Protection: Mọi write-op đều verify organizerId == currentUser"* (dòng 28), dùng ở `getMyEvent`, `updateEvent`, `publishEvent`, `cancelEvent`, `deleteEvent`.

**Thiếu:** credential cơ sở dữ liệu theo ranh giới (§5.3). `R3`/`ASR-12` đòi cơ chế quan sát và chẩn đoán *"chỉ được đọc, thực thi bằng **phân quyền thật, không phải quy ước**"*; hôm nay mọi thứ dùng chung một tài khoản `postgres`. Xây mới ở cả sáu.

### 8.4 Bí mật ở dạng rõ trong repository

`B5.5` §2.5 ghi `.env` và `.env.production`. Vòng này tìm thêm: **`docker-compose.apps.yml` chứa ở dạng rõ** mật khẩu cơ sở dữ liệu, mật khẩu thư, mã bí mật cổng thanh toán VNPay và khoá dịch vụ ảnh — riêng khối `core-service` có bốn nhóm bí mật như vậy.

⚠️ **Không sao chép các tệp này sang repository đồ án.** Nếu là khoá thật thì phải **xoay vòng**, không chỉ xoá khỏi cây làm việc. Đây là điều kiện của mọi phương án và cần làm **trước** khi bất kỳ tài sản nào được chuyển.

### 8.5 Frontend — phép thử `PRJ-005`, và một rủi ro đi ngược chiều

`PRJ-004` chốt *“tái sử dụng frontend hiện tại làm nền, nhưng frontend phải thích nghi với mô hình nghiệp vụ và backend mới”*; `PRJ-005` dựng rào: *“việc tái sử dụng frontend **không được ràng buộc cách `B11-A` hình thành các phương án tách service**”*. Mục này chạy đúng phép thử ấy. `v0.1`–`v0.3` **không có một dòng nào về frontend**, dù hai dòng sổ đều ghi `B11-B` trong cột gate (`B11-B-OPEN-07`).

**Kiểm kê:** `flash-ticket-system/frontend` — React 18 + Vite (SPA, **không** phải Next.js), TypeScript; **80 tệp `.ts`/`.tsx`, ~26.200 dòng**, cộng **~18.700 dòng CSS tự viết**; 23 trang, 21 thành phần dùng chung, **13 module `services/`**; 24 tuyến `react-router-dom`; trạng thái máy chủ bằng React Query; xác thực bằng `keycloak-js`; sơ đồ chỗ ngồi vẽ bằng Konva.

#### Phép thử `PRJ-005`: **ĐẠT** — frontend không ràng buộc cách tách service

Ba dữ kiện, độc lập nhau:

1. **Chỉ một gốc API.** `src/lib/axiosClient.ts` dòng 5–12 tạo đúng một `axios` instance với `baseURL = import.meta.env.VITE_API_GATEWAY_URL`. **13 trên 13** module trong `services/` đều mở đầu bằng `import axiosClient from "../lib/axiosClient"`. Frontend gọi **đường dẫn tương đối**, không gọi tên service: không có `lb://`, không có Eureka, không có host theo service trên đường đi chính.
2. **Việc phân tuyến nằm trọn ở phía máy chủ.** `apigateway/GatewayConfig.java` dòng 44–90 quyết `/api/bookings` do service nào trả lời. Dời `/api/bookings` sang một ranh giới khác chỉ là đổi một dòng `.uri(...)` — **frontend không sửa một chữ**.
3. **Bằng chứng thực nghiệm, mạnh hơn hai điểm trên:** phân loại đường dẫn **đã lệch** phân loại service mà frontend không hề biết. `/api/organizer/**` (số ít) → `core-service` (`GatewayConfig` dòng 47) còn `/api/organizers/**` (số nhiều) → `user-service` (dòng 68). `organizerService.ts` gọi **cả hai tiền tố** mà không phân biệt. Đây đúng tính chất `PRJ-005` đòi: *người dùng/frontend không cần biết backend được chia vật lý thế nào*.

> ⚠️ **Một vi phạm duy nhất, và nó nhỏ hơn vẻ ngoài.** `src/lib/internalServiceClients.ts` dòng 36–46 gán cứng `:8081` = `core-service` và `:8082` = `user-service`, kèm hai biến môi trường `VITE_CORE_SERVICE_URL`/`VITE_USER_SERVICE_URL` và hai proxy `/_core`, `/_user` ở `vite.config.ts` dòng 35–44. Tức frontend **có biết** rằng “core” và “user” là hai tiến trình. Nhưng: tệp dài **46 dòng**, chỉ **2 trên 13** module dùng, và **chỉ trong nhánh `catch`** — đường đi chính luôn là gateway, và **đường dẫn ở cả hai nhánh giống hệt nhau**. Thêm nữa nó **đã hỏng sẵn ở môi trường thật**: `.env.production` không khai hai biến đó, nên trình duyệt ở máy khác sẽ quay về `localhost` của chính nó. Đây là **giàn giáo gỡ lỗi còn sót**, không phải một ràng buộc kiến trúc; xoá nó là một commit.

#### Phép thử `PRJ-004`: tái sử dụng **làm nền** là thực tế, và chi phí thích nghi **gom cụm chứ không rải đều**

| Nhóm | Tỷ trọng | Nội dung |
|---|---|---|
| **Dùng lại gần như nguyên** | ~60–65% | Toàn bộ tầng trình bày: ~18.700 dòng CSS, hệ thống sơ đồ chỗ ngồi bằng Konva (`components/seat-map/**`, 15 tệp — hình học sector, sinh ghế, preset), layout/nav, i18n vi/en, và **khung truyền tải `axiosClient.ts` (35 dòng)** — mẫu một `baseURL` cộng một interceptor là đúng hình dạng cần giữ. Phần này **không có ghép nối backend nào** |
| **Phải thích nghi** | ~30–35% | `types/api.ts` (280 dòng, đang phản chiếu DTO cũ — kể cả `SpringPage<T>` rò hình dạng phân trang của Spring Data vào mọi trang danh sách); hình dạng DTO trong 13 module `services/`; **`CheckoutPage.tsx`** và **`SelectTicketPage.tsx`** — hai tệp gánh gần hết phần ghép nối nghiệp vụ; ~10 chỗ khai lại union trạng thái |
| **Chết hoặc ngoài phạm vi** | ~5% | `internalServiceClients.ts` + hai proxy Vite; bản vá `window.fetch`/`XMLHttpRequest` ở `main.tsx` dòng 17–42; hai vai trò gõ nhầm `"ADIM"`/`"adim"` ở `Navbar.tsx` dòng 129–132; `promotionService.ts` dòng 12 gọi `POST /api/promotions/validate` mà **`GatewayConfig` không khai tuyến nào khớp** — hoặc mã chết, hoặc một lỗi đang sống |

**Chi phí này giống nhau ở cả sáu phương án** — nó không phân biệt phương án nào, đúng như bốn ràng buộc chung ở §8.1–§8.4.

#### ⛔ Rủi ro thật nằm ở chiều ngược lại: giao diện đang mang cả kiểm tra phòng vệ và giả định luồng cũ

`PRJ-005` lo frontend **ép khuôn** backend. Bằng chứng cho thấy nó **không** ép cách chia service. Khảo sát lại bốn điểm từng bị `v0.5` gom chung dưới nhãn *“quy tắc chưa từng tồn tại ở backend”* cho thấy nhãn ấy quá rộng: có một kiểm tra chỉ được cưỡng chế ở giao diện, một giá trị dự phòng lặp lại giới hạn máy chủ, và hai giả định luồng bám cách hiện thực cũ.

| Điểm ghép nối ở giao diện | Phân loại đúng | Hệ quả khi tái sử dụng |
|---|---|---|
| **Chỉ chọn ghế trong một sector** — `SelectTicketPage.tsx` dòng 327–333 và 389–395 | **Yêu cầu đã được duyệt nhưng backend cũ chưa cưỡng chế.** `BIZ-001` đã chốt quy tắc một đơn–một sector; giao diện có kiểm tra, máy chủ cũ không có kiểm tra tương ứng | Giữ quy tắc ở hệ đích, nhưng phải cưỡng chế ở backend; kiểm tra frontend chỉ phục vụ trải nghiệm người dùng |
| **Giá trị dự phòng 10 vé mỗi đơn** — `SelectTicketPage.tsx` dòng 169–170 | **Kiểm tra phòng vệ lặp lại backend cũ.** `BookingService` dòng 373–380 đã kiểm `event.maxTicketsPerOrder`; `Event` và lược đồ đặt mặc định 10. Đây không phải quy tắc chỉ có ở giao diện | Không mặc định mang số 10 sang mô hình đích. Giao diện phải nhận giới hạn từ hợp đồng mới; giới hạn mua theo tài khoản/sự kiện vẫn là phần xây mới khác với giới hạn mỗi đơn |
| **Máy khách gọi hủy đơn khi đồng hồ về 0** — `CheckoutPage.tsx` dòng 228–265 | **Ghép nối luồng**, không phải cơ chế hết hạn duy nhất. Backend cũ có `OrderExpirationService`/`OrderExpirationHelper`; trạng thái hết hạn không phụ thuộc trình duyệt còn mở | Có thể giữ việc gọi hủy như tối ưu trải nghiệm, nhưng tính đúng đắn phải nằm ở backend và hợp đồng vòng đời mới |
| **Điều hướng bám trạng thái `PENDING`** — `CheckoutPage.tsx` dòng 123–132, 158, 171, 194 | **Giả định biểu diễn của hệ cũ.** `PENDING` cũng là trạng thái backend, không phải phát minh của frontend; tên và chuyển trạng thái đơn đích còn mở tại `BIZ-130` | Phải thích nghi sau `B12`/`B13`; không dùng trạng thái cũ để quyết hình dạng `Giữ chỗ`–`Đơn hàng` ở `B11-C` |

> ⛔ **Cách kế thừa đúng:** quy tắc một sector đã có nguồn nghiệp vụ độc lập nên phải được backend đích giữ; ba dòng còn lại là chi tiết phòng vệ hoặc ghép nối của hiện thực cũ, không được tự nâng thành yêu cầu mới. Nếu muốn biến số 10, hành vi hủy từ máy khách hoặc tên `PENDING` thành cam kết của hệ đích, phải xử lý tại đúng gate yêu cầu/hợp đồng, không chốt ngầm trong `B11-B`.

> ⚠️ **Hai ghi chú vận hành, không thuộc phạm vi phương án.** (1) Xác thực bám `keycloak-js` khá sâu — API `keycloak.*` được gọi trực tiếp ở hơn tám thành phần và `src/lib/auth.ts` dòng 9–27 đọc đúng hình dạng claim của Keycloak (`realm_access.roles`, `resource_access[*].roles`). Điều này **không** phân biệt phương án nào vì cả sáu dùng chung một nguồn danh tính, nhưng nó khoá lựa chọn nhà cung cấp danh tính. (2) `frontend/.env` chứa **bí mật dạng rõ** y như §8.4 đã ghi cho `docker-compose.apps.yml` — áp cùng kết luận: **xoay vòng khoá, không chỉ xoá khỏi cây làm việc**.
---

## 9. Rủi ro khả thi và việc trả ràng buộc về cổng thiết kế

`AGENTS.md` quy định: *"If legacy evidence reveals a feasibility problem, close B5.5 and return only a generalized constraint to the proper design gate."* Vòng này chạy phép kiểm đó cho **mười hai** phát hiện — tám ở `v0.1`, hai ở `v0.2`, và hai ở `v0.5` khi hai phần việc `PRJ-008`/`PRJ-004`-`PRJ-005` được làm.

| Phát hiện | Có phải vấn đề khả thi buộc quay lại `B11-A`? | Xử lý |
|---|---|---|
| `BC-05` không có tài sản nào | **Không** — là phần xây mới, giống nhau ở cả sáu; không phương án nào bị nó chặn | Ghi vào danh sách công việc, gate `B12` |
| `Giữ chỗ`, `Giới hạn mua`, `Yêu cầu hoàn tiền`, `Yêu cầu hủy sự kiện` không có hiện thực đầy đủ | **Không** — xây mới, giống nhau ở cả sáu | Như trên |
| Chuỗi thu tiền → phát hành không có bù trừ | **Không** — `B11-A` đã xếp nó `N4` ở năm phương án và `N1` ở `PA-1`; hiện thực khớp cách phân loại đó | `B11-C` chốt Saga, `B14` vẽ |
| `ticket_types` mang hai nghĩa trong một bảng | **Không** — `B11-A` §4.5(6) đã cảnh báo trước bằng `B2`/`B7`; hiện thực chỉ xác nhận | `B12` kiểm từng tên |
| Mã tương quan gần như trắng | **Không** — `R7` là ràng buộc `USER_CONFIRMED` áp cho mọi phương án, không phân biệt | `B16` |
| Không có credential theo ranh giới | **Không** — cùng lý do | `B12` |
| Dải 7–13 JVM trong kịch bản biên chưa được đo trên hai máy | **Không** — `ASR-11` vốn đã là `Chưa biết` ở `PA-3`/`PA-5`; bảng §8.2 chỉ xác định dải đo, không chốt hai đơn vị ứng dụng chưa quyết | `GOV-097` chuyển thử tải thật sang Giai đoạn 5–6; B11 dùng ngân sách tài nguyên và giới hạn heap/container |
| Các phương án cắt từ ba tới bảy họ giao dịch ở §6 | **Không** — `B11-A` đã ghi các điểm phối hợp và mức chưa biết liên quan; kiểm kê đầy đủ làm rõ chi phí nhưng không đổi thành viên tập | `B11-C` chốt cách phối hợp; `B13` chốt hợp đồng; `B14` mô tả Saga đã chọn |
| **Luật đếm trần 8 của `B5.5` §3.4 ngược với `B11-A`** (§3.3) | **Chưa trả lời được** — đây **không** phải rủi ro khả thi mà là **phạm vi của một ràng buộc `USER_CONFIRMED`** (`R1`/`ASR-10`). `AGENTS.md` cấm agent tự suy phạm vi cho một dòng như vậy, nên phiếu này **báo và dừng**, đúng cách `B11-A-OPEN-01`/`-03` đã làm. ✅ **ĐÃ ĐÓNG** (`GOV-079`): trần 8 chỉ đếm service nghiệp vụ; chatbot và cơ chế chẩn đoán đếm riêng về tài nguyên. `PA-3`/`PA-5` đếm 7 ≤ 8, `ASR-10` giữ `Đạt`, **không phương án nào bị loại** | Lê Văn Minh, **đã phân xử trước lượt duyệt** ngày 2026-08-31 |
| **`B11-A` §4.5(6) khai cảnh báo *hai nghĩa* áp cho `PA-3`/`PA-5`/`PA-6`, nhưng `PA-1` và `PA-4` cũng tách các nghĩa liên quan** (§7.1, §7.4) | **Không** — không phương án nào bị bằng chứng hiện thực chặn; đây là **lời khai phạm vi trong một tạo tác `FORMATION` đang `APPROVED`**, không phải rủi ro khả thi. Phạm vi đúng là **năm** phương án | ✅ **Đã sửa** trong `B11-A-v0.5` ở phiên `FORMATION` sạch và được Lê Văn Minh duyệt lại (`GOV-084`); `B11-B-OPEN-05` đóng |
| **`user-service` không dùng lại được “nguyên trạng”; root `Theo dõi organizer` mới xây một nửa; API `/api/internal/**` gọi được từ Internet không cần token** (§3.4) | **Không** — chi phí và lỗ hổng **giống hệt nhau ở cả sáu** vì cả sáu đều đặt `BC-07` ở một ranh giới riêng; không phương án nào bị nó chặn. ⚠️ Lỗ hổng `/api/internal/**` là **điều kiện phải xử lý trước khi chuyển tài sản**, cùng kỷ luật §8.4 | Danh sách công việc di trú; `B12`/`B13` cho hợp đồng nội bộ |
| **Frontend không ràng buộc cách tách service; bốn điểm ghép nối phải được phân loại riêng** (§8.5) | **Không** — `PRJ-005` **ĐẠT**. Một điểm là yêu cầu đã duyệt nhưng backend cũ thiếu cưỡng chế; một điểm lặp lại giới hạn backend; hai điểm là giả định luồng hiện thực cũ. Không dòng nào làm một phương án bất khả thi | Giữ kiểm tra một sector nhưng đưa cưỡng chế về backend; ba điểm còn lại thích nghi sau khi hợp đồng và vòng đời đích được chốt |

> ✅ **Kết quả phép kiểm, nay phát biểu VÔ ĐIỀU KIỆN — điều kiện duy nhất đã được gỡ ngày 2026-08-31.**
>
> **Không bằng chứng hiện thực nào loại một phương án.** Cả mười hai phát hiện đều không buộc trả ràng buộc khả thi về `B11-A`; một phát hiện từng cần phân xử phạm vi và đã đóng tại `GOV-079`.
>
> ✅ **`B11-B-OPEN-04` đã được Lê Văn Minh phân xử (`GOV-079`): trần 8 chỉ đếm service nghiệp vụ; chatbot và cơ chế chẩn đoán đếm riêng về tài nguyên vận hành.** `PA-3` và `PA-5` vì vậy đếm **7 ≤ 8**, giữ `ASR-10` = `Đạt`, và **không phương án nào bị loại** — câu này nay đứng độc lập. `R10` không bị chạm, không có bản sửa `B11-A` vật chất nào, nên **không kết quả nào của phiếu này bị vô hiệu** theo dòng *“a material B11-A revision invalidates the old B11-B result”*.
>
> **Cách phân xử này diễn ra là điều đáng ghi lại, vì nó là tiền lệ đúng.** `v0.3` định tuyến `OPEN-04` sang `B11-C` — sai, vì `B11-C` **chọn từ** tập chứ không định nghĩa **thành viên** tập, vì `AGENTS.md` xử xung đột nguồn bằng *“obtain a resolution”* chứ không hoãn, và vì `project-authority-and-gates.md` §5 đòi *“close B5.5 before revising B11-A”*. `v0.3` sửa định tuyến thành **phân xử trước lượt duyệt**, và việc đó **đã xảy ra** — đúng mẫu mà `GOV-070` khuyến nghị và `GOV-072` thực hiện cho `B11-A-OPEN-03`.
>
> ✅ **Dư âm từng để lại ở `v0.4` nay đã đóng.** Phân xử `GOV-079` chốt cách đếm; phiên `FORMATION` sạch sau đó sửa lời khai nguồn trong `B11-A-v0.5`, và Lê Văn Minh duyệt lại tại `GOV-084`. Đoạn này giữ dấu vết rằng `B11-B` không tự sửa tạo tác `FORMATION`, nhưng không còn một việc chờ *“lần chạm kế tiếp”*.
>
> `B11-A-v0.4` **không bị phiếu này chạm**, tập vẫn sáu phương án.

---

## 10. Một xung đột nguồn phải báo, không được tự sửa

**`B5.5` PHẦN 4 và ô kiểm cuối của `B5.5` §5 mô tả một thành phần đã bị gỡ khỏi bộ hệ thống.**

- `B5.5` §4.2 lập ba phiếu `B16`–`B18` cho đường ống *logging → Drain → context → LLM*, và ô kiểm cuối cùng của §5 viết: *"Pipeline AI được mô tả đúng là logging → Drain → context → LLM, không phải huấn luyện Drain?"*
- `AGENTS.md` mục 6 (`RES-034`, `GOV-030`) ghi **trợ lý cũ** — thành phần chạy đường ống cố định `log → Drain → context → LLM API` — là **"Đã gỡ khỏi bộ hệ thống"**. Đường ống ấy chính là thứ ba phiếu `B16`–`B18` của `B5.5` §4.2 phục vụ.
- ⚠️ **Hai hệ số hiệu, không khớp 1:1 — `v0.1` trộn chúng.** `B5.5` §4.2 đánh số **`B16`–`B18`**; nhật ký `docs/tang-b-quy-trinh-ky-thuat.md` ngày 2026-08-27 ghi xoá **`B17`–`B19`** theo số hiệu của **Tầng B**. `B5.5` tự cảnh báo đúng chỗ này: *"Số hiệu hai tài liệu không khớp 1:1"*. Điều đáng kể là **hai trong ba phiếu** mà `B5.5` đề xuất không còn, và Tầng B chỉ giữ chuẩn logging (`B16`).

**Phiếu này không tự sửa `B5.5`.** Ba lý do: `B5.5` là tạo tác `COMPARISON` có thẩm quyền riêng và một nhật ký sửa đổi riêng; `AGENTS.md` buộc *"do not mutate; cite the exact lines and obtain a resolution"* khi hai nguồn xung đột vật chất; và tiền lệ `GOV-044` đã ghi rằng chỗ này *"cần một quyết định của Lê Văn Minh"*.

**Ảnh hưởng tới kết quả `B11-B`:** **không có.** Mọi dữ kiện dùng ở §3–§9 lấy từ `B5.5` PHẦN 1, PHẦN 2 và PHẦN 3 — kiểm kê, ghép nối, hạ tầng — cộng phép đọc repository trực tiếp. **Không mục nào của phiếu này dẫn về `B5.5` PHẦN 4.**

> ✅ **ĐÃ ĐÓNG ngày 2026-08-31 (`GOV-074`).** Lê Văn Minh chọn **đính chính bằng chú thích, giữ nguyên văn cũ** sau khi được trình ba hướng. Ba khối đính chính đã đặt vào `B5.5`: đầu **PHẦN 4** (kèm bảng nói rõ `B16` còn hiệu lực, còn `B17`/`B18` **theo số hiệu của `B5.5`** thì không còn — Tầng B ghi xoá `B17`–`B19` theo số hiệu của Tầng B ngày 2026-08-27), **ô kiểm cuối §5** (không tích, kèm câu hỏi thay thế), và **gạch đầu dòng cuối §3.5** — chỗ mà `GOV-044` để ngỏ từ 2026-08-28 nay cũng đóng theo. **Nguyên văn khảo sát cũ không bị sửa một chữ**, đúng quyết định đã chốt. §3.5 và PHẦN 5 có nhận chú thích, nhưng nội dung kiểm kê mà phiếu này dùng làm đầu vào không đổi.
>
> **Việc đóng điểm mở này không bump phiên bản `B11-B`** — nó ghi lại một quyết định của người thật về một tạo tác khác và **không đổi một kết luận nào** của §3–§9, đúng tiền lệ `GOV-033`, `GOV-060` và `GOV-072`.

---

## 11. Sổ `B11-B-OPEN` — **bảy điểm đã đóng, một điểm đang mở**

| ID | Nội dung | Vì sao `B11-B` không tự đóng được | Owner / gate |
|---|---|---|---|
| `B11-B-OPEN-01` | **`B5.5` PHẦN 4 và một ô kiểm ở §5 còn mô tả trợ lý cũ đã bị gỡ** (`RES-034`, `GOV-030`) — xem §10 | Sửa một tạo tác `COMPARISON` có nhật ký riêng cần quyết định của người thật; `AGENTS.md` buộc báo xung đột thay vì tự hoà giải, và `GOV-044` đã ghi đúng câu đó cho một chỗ cùng loại | ✅ **ĐÃ ĐÓNG** ngày 2026-08-31 (`GOV-074`) — Lê Văn Minh chọn **đính chính bằng chú thích, giữ nguyên văn cũ**; ba khối đã đặt vào `B5.5`, điểm để ngỏ của `GOV-044` đóng theo |
| `B11-B-OPEN-02` | **Bốn cạnh #3, #6 trỏ vào `TicketType` — lớp hai nghĩa — nên ba phương án `PA-1`, `PA-3`, `PA-6` có phép đếm dạng dải thay vì một số.** Dải chỉ đóng được khi biết cột nào theo nghĩa nào đi về ranh giới nào | Đó là **quyền sở hữu dữ liệu ở mức cột**, thuộc `B12` (`ASR-13` cấm `B11` giả định). `B11-B` không được tự chọn để làm bảng §7.7 gọn hơn | Lê Văn Minh / `B12` |
| `B11-B-OPEN-03` | **ĐÃ ĐÓNG ngày 2026-09-04 (`GOV-097`, `GOV-098`).** Phép đo tải không còn là blocker của B11; ngân sách mục tiêu khoảng 6 GiB tiến trình/container mỗi máy và giới hạn heap/memory phải được công bố. Thử tải thật chuyển sang Giai đoạn 5–6; nếu cấu hình thiếu, nhóm nâng instance AWS tạm thời | Quyết định đóng điểm mở **không** xác nhận cấu hình hiện tại chịu tải cao và không đặt ngưỡng cho `ASR-06`; nó chỉ định tuyến đúng phép đo tới lúc kiến trúc đã được hiện thực | ✅ Đã đóng; theo dõi ở Giai đoạn 5–6 |
| `B11-B-OPEN-04` | **Hai luật đếm trần 8 ngược nhau.** `B5.5` §3.4 đếm *“các đơn vị ứng dụng như user/chatbot/trợ lý nếu B11 triển khai chúng thành service”* **vào** trần 8; `B11-A` mục (7) của cả sáu phương án đặt đơn vị chẩn đoán **ngoài** trần, và §8.2 từng khai đó là *“đọc trực tiếp câu chữ”*. Theo luật của `B5.5` thì `PA-3` và `PA-5` = **9 > 8**, tức lật ô `ASR-10` — một **ràng buộc cứng** — ở hai phương án. Xem §3.3 | Đây là **phạm vi của một dòng `USER_CONFIRMED`** (`R1`/`ASR-10`), đúng loại mà `AGENTS.md` cấm agent tự suy — cùng loại với `B11-A-OPEN-01` và `B11-A-OPEN-03`. `B11-B` cũng **không được** sửa `B5.5` lẫn `B11-A`. Ba nguồn cũ im lặng; vì vậy đây là khoảng trống phải được người có thẩm quyền phân xử, không phải phép đọc trực tiếp. | ✅ **ĐÃ ĐÓNG** ngày 2026-08-31 (`GOV-079`) — Lê Văn Minh: **trần 8 chỉ đếm service nghiệp vụ; chatbot và cơ chế chẩn đoán đếm riêng về tài nguyên vận hành**. `PA-3`/`PA-5` đếm 7 ≤ 8; tập vẫn sáu phương án. Lời khai nguồn còn sót đã được sửa trong `B11-A-v0.5` và duyệt lại tại `GOV-084`, nên `GOV-080` cũng đã đóng. |
| `B11-B-OPEN-05` | **Phạm vi cảnh báo *ba tên mang hai nghĩa* của `B11-A` §4.5(6) hẹp hơn dữ kiện.** Nó khai áp cho *“`PA-3`, `PA-5` và `PA-6`”*, nhưng `PA-1` cũng tách `BC-01` khỏi `BC-02`; `PA-4` còn tách nghĩa cấu hình và sử dụng của `Khuyến mãi`. **Phạm vi đúng là năm phương án: `PA-1`, `PA-3`, `PA-4`, `PA-5`, `PA-6`** | Sửa lời khai phạm vi là **sửa một tạo tác `FORMATION` đang `APPROVED`**; phiên `COMPARISON` này bị cấm. Đây là sửa lời khai, không đổi tập hoặc phép đếm | ✅ **ĐÃ ĐÓNG** ngày 2026-09-01 — `B11-A-v0.5` sửa cảnh báo trong một phiên `FORMATION` sạch và Lê Văn Minh duyệt lại tại `GOV-084`; tập, phép đếm và sơ đồ không đổi |
| `B11-B-OPEN-06` | **Đánh giá tái sử dụng `user-service` chưa hoàn tất.** `PRJ-008` giao *“Đánh giá thật thuộc `B11-B`”* và liệt kê **ba phần phải cắt** cộng một điểm mở bị chốt hộ (`B4-OPEN-01`, đồng bộ nguồn danh tính). `v0.2` gọi tài sản này là *“nguyên trạng”* và §3.1 còn liệt kê `Address` — đúng một trong ba phần phải cắt — vào cột tài sản dùng lại được, mức phủ **Cao** | Cần đọc `user-service` theo đúng ba mục `PRJ-008` nêu và đối chiếu `BIZ-151`, `BIZ-119`; quyết định đồng bộ danh tính là `B4-OPEN-01`, **phải trình riêng** chứ không được chốt kèm | ✅ **ĐÃ ĐÓNG** ở `v0.5` — đánh giá đầy đủ ở **§3.4**. Ba mục cắt đều rẻ (một là mã chết, một là tính năng chưa từng xây, một nhỏ nếu đọc nghĩa hẹp), nhưng chữ *“nguyên trạng”* sai vì **năm lý do khác** và vì root `Theo dõi organizer` mới xây một nửa. `B4-OPEN-01` **không** bị chốt kèm — §3.4 chỉ mô tả ba cơ chế đồng bộ. Câu hỏi phạm vi *ảnh nhận diện* tách ra thành `-08` |
| `B11-B-OPEN-07` | **Không có một dòng nào về frontend trong phiếu này.** `PRJ-004` ghi gate *“`B11-B`; Giai đoạn 5”* và file chịu ảnh hưởng *“B11-B, kế hoạch tái sử dụng, frontend”*; `PRJ-005` ghi *“kiểm tra tái sử dụng tại `B11-B`/Giai đoạn 5”*. Phiếu **không đánh giá, không dẫn hai dòng đó, và cũng không khai là hoãn** | Hai dòng sổ đặt `B11-B` **cạnh** Giai đoạn 5, nên việc kiểm frontend thuộc gate nào là câu hỏi phạm vi — `B11-B` không tự trả lời được. Điều chắc chắn sai là **im lặng**: nếu hoãn thì phải ghi ra | ✅ **ĐÃ ĐÓNG** ở `v0.5` — Lê Văn Minh chỉ định làm ngay trong vòng này, nên câu hỏi gate không còn phải trả lời. Đánh giá ở **§8.5**: `PRJ-005` **ĐẠT** (frontend không ràng buộc cách tách service), `PRJ-004` khả thi; rủi ro thật đi **ngược chiều** và đã được rào |
| `B11-B-OPEN-08` | **Phạm vi của mục cắt *“lưu ảnh nhận diện”* trong `PRJ-008` chưa xác định, và hai cách đọc cho hai chi phí rất khác nhau.** Nghĩa hẹp (chỉ ảnh đại diện người dùng): ~6 vị trí, nhưng `StorageService`, `CloudinaryConfig` và cả hai phụ thuộc Cloudinary **phải ở lại** cho logo/banner organizer, tức không giảm phụ thuộc nào. Nghĩa rộng (mọi ảnh nhận diện): **xoá hai endpoint organizer đang sống** và làm hỏng đường tự chữa `organizerLogoUrl` mà `core-service` đọc. Xem §3.4 | Nghĩa rộng **xoá hành vi đang chạy**, nên theo `AGENTS.md` đó là **thay đổi phạm vi** cần người thật xác nhận, không phải việc `B11-B` tự chọn. `BIZ-151` nói về tập trường hồ sơ, không nói về việc gỡ endpoint | ✅ **ĐÃ ĐÓNG** ngày 2026-09-04 (`GOV-091`) — Lê Văn Minh chọn **nghĩa hẹp: chỉ ảnh đại diện người dùng**. Logo/banner organizer và hành vi đang sống không bị gỡ; schema/endpoint đích vẫn chờ `B12`/`B13` |

**Ba thứ đã cân nhắc và KHÔNG mở thành điểm mở**, vì chúng đã có chủ ở nơi khác: khoảng trống mô hình của `Giữ chỗ` — từng là `B11-A-OPEN-02`, nay đóng do `PA-6` được chọn tại `GOV-086`; ngưỡng `ASR-06` — vẫn là `B10-OPEN-01`; ngưỡng độ trễ bản sao của `ASR-14` — vẫn ở `B11-A` §8.1 dưới nguyên ID nguồn. Cấp mã mới cho chúng là **nhân bản quyền sở hữu**.

---

## 12. Phép tự kiểm

Mỗi ô được suy từ nội dung hiện tại của tệp này. Theo luật `B11-A-v0.4`: **một ô không được chứa một con số mà chính nó không liệt kê ra hoặc không trỏ tới nơi liệt kê.**

**Thẩm quyền và phạm vi**

- [x] **Phiên bản đầu vào `B11-A` được khai ở đầu tệp:** `B11-A-v0.4`, `APPROVED` 2026-08-31, `GOV-070`; phạm vi `R15` đã phân xử tại `GOV-072` **trước** khi phiếu này mở.
- [x] **Không phương án nào được tạo, thêm, bớt, xếp hạng, đề nghị hay sửa.** Sáu mục §7.1–§7.6 theo đúng thứ tự và đúng tên `B11-A`; §7.7 mang một khối ⛔ ba lý do cấm đọc thành xếp hạng; §1 mang khối ⛔ thứ hai cấm chiều suy `mã → ranh giới`.
- [x] **Không tạo tác `FORMATION` nào được sản xuất hay sửa ở phiên này.** Nội dung mới chỉ nằm trong chính phiếu `COMPARISON`, hồ sơ đối chiếu `B5.5` và các tệp sổ/trạng thái dẫn xuất. `B11-A-v0.4` cùng các sơ đồ của nó không bị chạm.
- [x] **Không khẳng định nào vượt gate.** Sở hữu cột `TicketType` đi `B12`; chi tiết Saga đi `B13`/`B14`; thử tải thật đi Giai đoạn 5–6 theo `GOV-097` và không bị giả làm kết quả của B11. Phạm vi ảnh nhận diện đã được người thật chốt nghĩa hẹp (`GOV-091`); schema/endpoint đích không bị chốt kèm.
- [x] **`PRJ-009` được dùng đúng giới hạn nó tự đặt.** Nó được dẫn ở §3.2 dòng 2 và §7.5 để trả lời câu hỏi giữ chỗ–đơn **mà không dựng lại lập luận giữ chỗ–đơn từ repository**; §5.2 có bổ sung dữ kiện lược đồ (`event_seat_inventory` mang `status`/`locked_by_*`) dưới đúng thẩm quyền `COMPARISON` của phiếu này, và dữ kiện đó **cùng chiều** với `PRJ-009` chứ không thay nó. §7.5 mang một khối ⛔ nói rõ dữ kiện ấy **không** được dùng làm lý do nghiêng về hướng xử lý nào của `B11-A-OPEN-02`.

**Phép đếm liệt kê được**

- [x] **26 cạnh ghép nối được liệt kê đủ ở §4.2**, đánh số 1–26; ma trận §4.1 cộng lại đúng 26 (12+1+7+2+2+1+1). Định nghĩa hiện hành là **25 cạnh giữa package nghiệp vụ cộng một cạnh `shared → event` có hai đầu cùng gán về `BC-01`**; cạnh thứ 26 không bị cắt ở phương án nào.
- [x] **Phép đếm `booking ↔ event` khớp `B5.5` §1.3: 12 và 1.** Mười ba cạnh còn lại được đánh dấu **mới** ở cột cuối ma trận §4.1.
- [x] **Sáu con số ở §7.7 dòng đầu truy được về danh sách §4.2**, mỗi mục §7 ghi rõ cạnh nào bị cắt bằng số hiệu: `PA-1` #2,#5,#11,#13 (+#3,#6) · `PA-2` #11,#12,#14–#19,#20 · `PA-3` #2,#5,#11,#12,#13,#14–#19,#20,#23,#24,#25 (+#3,#6) · `PA-4` #1–#7,#11,#12,#14–#19,#20 · `PA-5` tất cả trừ #8,#9,#10,#26 · `PA-6` #2,#5,#11,#12,#13,#14–#19,#20 (+#3,#6). **`v0.2` gỡ #26 khỏi cả năm danh sách** vì cả hai đầu của nó là `BC-01` (§2), nên nó không bị cắt ở phương án nào.
- [x] **Mười lăm vị trí gọi cùng package được liệt kê ở §6.2** với tên tệp và số dòng, chia hai nhóm và đánh dấu `a`–`o`; §7 dùng đúng nhãn đó. *(`v0.1`/`v0.2` ghi tám: bỏ toàn bộ lời gọi repository trong cùng package, bỏ `BookingService:284`, và thừa một dòng `bookingCompositionPolicy`.)*
- [x] **Mười ba dòng của thân giao dịch §6.2 có số dòng nguồn**, nên đếm lại được từ `BookingService.java`. *(`v0.2` ghi mười hai và bỏ sót dòng 195 `ticketInventoryCounterService.findAvailableQuantity`.)*
- [x] **Phép đo 3 có danh sách bảng ở §5.2 và mỗi ô §7 gọi tên bảng nó đếm.** *(`v0.2` không có ô tự kiểm cho hàng này, và bốn ô §7 đếm **phân vùng schema** `booking_schema` — hai bảng vốn đã riêng — như thể đó là một phép chẻ bảng, trái chính định nghĩa ở §2.)*
- [x] **Ba dải thay vì một số** — `PA-1` 4–6, `PA-3` 15–17, `PA-6` 12–14 — và lý do dải nằm ở `B11-B-OPEN-02` chứ không bị làm tròn cho gọn.
- [x] **Phép đếm 26 cạnh đã được đếm lại độc lập** bằng một phép quét `import` có hai đầu gán được về năng lực trên `core-service`; kết quả **trùng khớp ma trận §4.1 từng ô** (12 · 7 · 2 · 2 · 1 · 1 · 1 = 26) và trùng tập lớp của §4.2. Cái `v0.2` sửa **không phải sổ cạnh** mà là **phép gán năng lực** của hai lớp; `v0.6` sửa mô tả phạm vi phép quét.

**Trung thực với nguồn**

- [x] **Mọi `FACT` về mã đều có đường dẫn tệp, và phần lớn có số dòng.** Ba dữ kiện không có số dòng vì chúng là kết quả quét toàn cây — 26 cạnh import, 0 khoá ngoại xuyên schema, 0 lệnh `GRANT` — và mỗi cái ghi rõ phép quét đã chạy trên tập tệp nào.
- [x] **Năm phép gán lớp có thể tranh luận được khai riêng ở §2** — `SeatBookingService`, `TicketReservationService`, `TicketType`, **`EventSyncHelper`** và **`TicketMessageListener`** — thay vì để ngầm trong phép đếm. Hai phép gán cuối là **sửa của `v0.2`**: `v0.1` để `EventSyncHelper` dưới nhãn *“hạ tầng chung”* (không thuộc bảy năng lực) và gán `TicketMessageListener` **hai chỗ khác nhau** (§3.1 `BC-06`, §4.2 `BC-04`).
- [x] **Giới hạn của phép đo 1 được khai ngay tại chỗ định nghĩa nó** (§2), và phép đo 2 kiểm kê đủ **bảy họ giao dịch** để bù chỗ mù của phép đếm import mà không đếm trùng lời gọi lồng nhau.
- [x] **Một phép kiểm ngược đã chạy và cho kết quả sạch** — đường cắt `BC-01` \| nguồn cung không cắt vị trí gọi cùng package nào (§6.2, khối ✅). Ghi cả kết quả âm, không chỉ kết quả dương.
- [x] **Xung đột `B5.5` PHẦN 4 được báo chứ không tự sửa** (§10), kèm khai rõ nó **không** ảnh hưởng kết quả vì không mục nào dẫn về phần đó. **Lê Văn Minh đã phân xử ngay sau đó** (`GOV-074`): đính chính bằng chú thích, giữ nguyên văn cũ; ba khối đã đặt vào `B5.5` và điểm mở đóng. Việc đóng **không** bump phiên bản phiếu này, đúng tiền lệ `GOV-033`/`GOV-060`/`GOV-072`.
- [x] **Bốn phát hiện mới so với `B5.5` được đánh dấu là mới**, không trộn vào phần chép lại: bảy cạnh `payment → booking`; 0 khoá ngoại xuyên schema; một tài khoản `postgres` duy nhất; bí mật dạng rõ trong `docker-compose.apps.yml`.

**Kết quả**

- [x] **Phép kiểm "có phải trả ràng buộc tổng quát về `B11-A` không" đã chạy trên mười hai phát hiện** và ghi thành bảng §9. Không phát hiện nào buộc trả ràng buộc khả thi; một phát hiện từng cần phân xử phạm vi và đã đóng tại `GOV-079`. `B5.5` không phải đóng giữa chừng và **không kết quả nào bị vô hiệu**.
- [x] **Không phương án nào bị loại.** Ô này **được tích lại ở `v0.4`** sau khi `B11-B-OPEN-04` đóng (`GOV-079`): `PA-3`/`PA-5` đếm 7 ≤ 8 nên `ASR-10` giữ `Đạt`, và câu này thôi có điều kiện. Nêu ở §1 và §9. *(`v0.3` để trống ô này có chủ ý, và đó là trạng thái đúng lúc ấy.)*
- [x] **Tám điểm mở được cấp mã, năm đã đóng** — `-01` đóng ở `v0.1` (`GOV-074`), **`-04` đóng ở `v0.4` (`GOV-079`)**, `-05` đóng ngày 2026-09-01 bằng `B11-A-v0.5`/`GOV-084`; `-02`, `-03` mở từ `v0.1`; `-06` và `-07` mở ở `v0.3` và **đóng ở `v0.5`** khi hai phần việc được làm; **`-08` mở ở `v0.5`** cho phạm vi mục cắt *ảnh nhận diện*. Ba thứ đã có chủ ở nơi khác **không** được cấp mã mới (§11).
- [x] **Bốn việc sổ quyết định giao cho `B11-B` đã được làm đủ** — `PRJ-009` (§3.2 dòng 2, §7.5); `PRJ-008` → **§3.4**, chạy đủ ba mục phải cắt kèm đối chiếu `BIZ-151`/`BIZ-119` và mô tả ba cơ chế đồng bộ **mà không chốt hộ** `B4-OPEN-01`; `PRJ-004`/`PRJ-005` → **§8.5**, chạy phép thử *frontend có ràng buộc cách tách service không*. *(`v0.1`–`v0.3` không làm hai việc sau và cũng không khai là hoãn.)*
- [x] **Một khoảng trống nguồn và một lỗi phạm vi đều đã được đóng đúng thẩm quyền.** `B11-B-OPEN-04` đóng tại `GOV-079`; `B11-B-OPEN-05` đóng bằng `B11-A-v0.5` trong phiên `FORMATION` sạch và lượt duyệt lại tại `GOV-084`. Phiếu này không tự sửa `B11-A`.
- [x] **Lê Văn Minh duyệt `B11-B-v0.6`** ngày 2026-09-01, nguyên văn: *"Tôi xác nhận duyệt B11-B phiên bản 0.6 hiện tại"* (`GOV-085`). AI chỉ ghi nhận phê duyệt có thật, không tự tạo phê duyệt (`GOV-011`). Cập nhật sau gate: `B11-C-v0.3` đã chấp nhận `PA-6`; bốn ADR đích được chấp nhận sau khi `GOV-097` thay blocker benchmark bằng ngân sách tài nguyên và phép thử thật ở Giai đoạn 5–6.

---

## 13. Phần dùng cho báo cáo

`B5.5` §0 giới hạn: báo cáo mô tả cấu trúc đích và lý do kiến trúc, **không** kể bảng hay lớp nào chuyển từ đâu. Vì vậy chỉ **hai** kết quả của vòng này được đề cử, và cả hai là luận điểm **phương pháp**, không phải bảng kiểm kê:

1. **Đối chiếu hiện thực xác nhận một cảnh báo mà mô hình miền đã đưa ra trước, bằng nguồn độc lập.** `B11-A` §4.5(6) suy từ `B2` và `B7` rằng `Loại vé` mang hai nghĩa — cấu hình và cam kết nguồn cung — và cảnh báo mọi hình dạng tách `BC-01` khỏi `BC-02` phải kiểm từng tên. Lược đồ cũ cho thấy hai nghĩa ấy **nằm trong một bảng** (§5.2). Đây là ví dụ dùng được cho phần Phương pháp: mô hình miền dự đoán được một chi phí hiện thực **trước khi** ai mở mã ra xem.
2. **Kỷ luật dữ liệu và kỷ luật mã có thể lệch nhau trong cùng một hệ thống.** Lược đồ cũ **không có một khoá ngoại xuyên schema nào** và dùng ID mềm kèm bản sao đọc — đạt sẵn phần lớn `R5`; trong khi mã lại đọc thẳng repository của nhau ở **bảy** vị trí xuyên năng lực (trong **tám** cạnh repository trực tiếp ở §4.2, cạnh thứ tám nằm gọn trong nguồn cung), tức vi phạm cùng dòng ấy ở vế khác. Luận điểm: một ranh giới chỉ có thật khi **cả hai** tầng cùng giữ nó.

**Không** đưa vào báo cáo: ma trận §4.2, bảng §7.7, danh sách mười lăm vị trí gọi, và mọi tên lớp hay tên bảng của hệ cũ.

---

## 14. Nhật ký phiên bản

> **Ghi nhận phê duyệt, không bump phiên bản:** sau khi `B11-A-v0.5` đóng hai hiệu đính lời giải thích và được duyệt lại (`GOV-084`), Lê Văn Minh duyệt đúng `B11-B-v0.6` hiện hành ngày 2026-09-01 (`GOV-085`). Nội dung đối chiếu, phép đếm và kết luận khả thi của `v0.6` không đổi.
>
> **Ghi nhận đóng điểm mở, không bump phiên bản:** ngày 2026-09-04, `GOV-091` chọn nghĩa hẹp cho ảnh nhận diện và đóng `B11-B-OPEN-08`. Đây là quyết định phạm vi của người thật; không đổi phép đo hay kết luận khả thi đã duyệt.

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `B11-B-v0.6` | 2026-09-01 | **Sửa các lời khai quá mức và số liệu thiếu sau khi quét lại toàn bộ phạm vi liên quan; không thêm, bớt, đổi hay xếp hạng phương án.** (1) Phép đo giao dịch nay kiểm kê **bảy họ thao tác** thay vì lấy hai thân giao dịch được phân tích sâu làm toàn bộ phạm vi; từng phương án ghi rõ họ nào bị cắt. (2) `INV-06` hạ từ *được giữ hôm nay* xuống **chỉ có cơ chế một phần**: không có nơi gọi `confirmPromotion()`, không có root `Giữ chỗ` và không có `Giới hạn mua` đầu-cuối. (3) Bốn điểm frontend được phân loại lại: một yêu cầu đã duyệt nhưng backend cũ chưa cưỡng chế; một kiểm tra lặp lại giới hạn backend; hai giả định luồng phụ thuộc cách hiện thực cũ — thay cho lời khai sai rằng cả bốn chưa từng có ở backend. (4) Phép quét import được khai đúng là **25 cạnh giữa package nghiệp vụ cộng một cạnh `shared → event`**, mọi đầu mút đều ánh xạ được về năng lực. (5) Bảng tài nguyên đổi từ một tổng cố định sang **kịch bản/dải** theo số đơn vị ứng dụng chưa quyết và có tính Keycloak. (6) `Yêu cầu hủy sự kiện` được thêm vào danh sách xây mới chung của **đủ sáu** phương án. (7) Nguồn mã cũ được khóa ở commit sạch `609fa2d37cad69aafa593b7db5b6cedeaf803da5`; lời khai về các chú thích trong `B5.5`, số phát hiện tự kiểm và định tuyến `OPEN-03`/`OPEN-05` được sửa cho khớp. **Các câu cũ trái với dòng này ở `v0.1`–`v0.5` chỉ còn giá trị lịch sử và đã bị `v0.6` thay thế.** Trạng thái lên `REVIEW_READY`; Lê Văn Minh vẫn phải duyệt, và hai lời khai của `B11-A` phải được sửa/rà lại trong phiên `FORMATION` sạch trước khi mở `B11-C` | Sửa chứng cứ + sẵn sàng để duyệt |
| `B11-B-v0.5` | 2026-09-01 | **Hai phần việc mà sổ quyết định giao cho `B11-B` từ lâu nay đã được làm; `B11-B-OPEN-06` và `-07` đóng theo (`GOV-081`).** **§3.4 mới — `user-service` (`PRJ-008`).** Ba mục phải cắt đều **rẻ**: *trường địa chỉ* là **mã chết** (không nơi nào đọc hay ghi, không endpoint, và quét cả frontend cũng không có form nào — nó là di vật copy từ một dự án trước, còn nguyên chú thích `// was "state" in old project`); *trạng thái khoá tài khoản* là **tính năng chưa từng được xây** (`SUSPENDED` không được gán ở đâu, `lockoutUntil`/`failedLoginAttempts` không ai đọc, không filter nào chặn theo status, `disableUser()` không ai gọi); *ảnh nhận diện* nhỏ **nếu** đọc nghĩa hẹp. Nhưng chữ **“nguyên trạng”** mà `v0.2` thêm vào thì **sai**, và sai vì **năm lý do không liên quan ba mục cắt**: module không tự chứa (86 dòng cấu hình nằm ở `configserver` + 17 khoá `.env`); khởi động phụ thuộc cứng vào cấu hình Keycloak (`@Validated @NotBlank`, không mặc định); **chỉ mục Mongo gần như chắc chắn không được tạo** nên mọi ràng buộc duy nhất chưa được cưỡng chế; hợp đồng xuyên service **hỏng ở cả hai đầu** (`core-service` gọi `/api/organizers/{id}` mà `user-service` không map, lại không gắn `Authorization`, nên nhánh tự chữa `logoUrl` không bao giờ chạy tới); và ⛔ **`/api/internal/users/{userId}` gọi được từ Internet không cần token** — cả gateway lẫn `user-service` đều đặt `/api/internal/**` là `permitAll` — trả về email, số điện thoại, ngày sinh, giới tính, role, status. Thêm nữa root `Theo dõi organizer` **mới xây một nửa** và khoảng một phần ba document `User` là lược đồ trơ, tức phần **phải xây thêm lớn hơn phần phải cắt**. Ba cơ chế đồng bộ danh tính được **mô tả chứ không quyết** (A và B thừa nhau; C là đường ghi ngược duy nhất và nuốt lỗi); ghi rõ mục cắt *khoá tài khoản* **không độc lập** với `B4-OPEN-01` vì ba chỗ ghi `status` nằm trong A và B. **§8.5 mới — frontend (`PRJ-004`/`PRJ-005`).** Phép thử `PRJ-005`: **ĐẠT** — 13/13 module `services/` gọi đường dẫn tương đối qua đúng một `VITE_API_GATEWAY_URL`, không `lb://`, không Eureka; bằng chứng mạnh nhất là **thực nghiệm**: `/api/organizer/**` đi `core-service` còn `/api/organizers/**` đi `user-service` mà frontend không hề biết. Vi phạm duy nhất là `internalServiceClients.ts` gán cứng `:8081`/`:8082` — **46 dòng, chỉ trong nhánh `catch`, và đã hỏng sẵn ở production**. `PRJ-004` khả thi: ~60–65% dùng lại gần như nguyên (18.700 dòng CSS, hệ sơ đồ chỗ ngồi Konva, layout, i18n, khung `axiosClient`), ~30–35% phải thích nghi và **gom vào hai tệp** (`CheckoutPage.tsx`, `SelectTicketPage.tsx`) cộng `types/api.ts`, ~5% chết. ⛔ **Rủi ro thật đi ngược chiều `PRJ-005`:** frontend đang giữ **bốn quy tắc nghiệp vụ chưa từng có ở backend** — chỉ chọn ghế trong một sector, trần 10 vé mỗi đơn, **máy khách tự gọi `cancelOrder` khi hết giờ giữ**, và máy trạng thái đơn dựng quanh đúng một `PENDING`. Chúng **vô hình với mọi tài liệu `FORMATION`**; phiếu ghi chúng là `FACT` về hệ cũ và rào rõ **không được thừa kế mặc định**. **Điểm mở mới `-08`:** phạm vi mục cắt *ảnh nhận diện* — nghĩa rộng xoá hai endpoint organizer đang sống, nên là **thay đổi phạm vi** cần người thật xác nhận. Bảng §9 chạy trên **mười hai** phát hiện, kết quả *Không* ở cả hai dòng mới. **Không con số nào của §7 bị đụng; `B11-A-v0.4` vẫn không bị chạm; tập vẫn sáu phương án** | Hoàn tất phần việc được giao |
| `B11-B-v0.4` | 2026-08-31 | **`B11-B-OPEN-04` đóng bằng phân xử của Lê Văn Minh (`GOV-079`), trước lượt duyệt.** Nguyên văn: *“ok tôi đồng ý đề xuất”*, chọn phương án đã được trình: **trần 8 chỉ đếm service nghiệp vụ; chatbot và cơ chế chẩn đoán đếm riêng về tài nguyên vận hành**. Hệ quả lan truyền: `PA-3` và `PA-5` đếm **7 ≤ 8** nên `ASR-10` giữ `Đạt` ở cả sáu cột; **tập vẫn sáu phương án**; kết luận §1 và §9 chuyển từ **có điều kiện** sang **vô điều kiện**; ô tự kiểm *Không phương án nào bị loại* được **tích lại**. Điều kiện *“nếu B11 triển khai chúng thành service”* của `B5.5` §3.4 nay đã có câu trả lời. ⚠️ **Hai thứ KHÔNG đóng theo:** (1) lời khai ở `B11-A` §8.2 rằng đây là *“đọc trực tiếp câu chữ, không phải một quyết định còn thiếu”* vẫn **sai** — ba nguồn vẫn im lặng, thứ vừa xảy ra chính là một quyết định; sửa nó thuộc lần chạm `B11-A` kế tiếp (`GOV-080`). (2) `B11-A` §2 vẫn khai *“một đơn vị triển khai riêng”* cho cơ chế chẩn đoán là `CANDIDATE`, chốt ở `B11-C` — phân xử này chốt **cách đếm**, không chốt **cách triển khai**. **Không con số nào của phiếu bị đếm lại; `B11-A-v0.4` vẫn không bị chạm.** Còn `-02`, `-03`, `-05`, `-06`, `-07` mở | Đóng điểm mở |
| `B11-B-v0.3` | 2026-08-31 | **Vòng thẩm định thứ hai (`GOV-077`), sau một bản đánh giá ngoài.** ⚠️ **Hai phép đo bị sửa số liệu.** (1) *Vị trí gọi cùng package*: **8 → 15**, hàng §7.7 từ `0/2/2/8/8/2` thành **`0/6/6/15/15/6`** — `v0.2` bỏ toàn bộ lời gọi **repository** trong cùng package (dù §4.2 tự lập luận đó là loại **nặng hơn**), bỏ `BookingService:284`, và thừa dòng `bookingCompositionPolicy` vốn được chính §4.2 gán về `Đơn hàng`. (2) *Bảng phải tách*: từ `1/1/2/1/2/2` thành **`2/0/2/1/2/2`** — `v0.2` đếm **phân vùng schema** (`orders` và `tickets` vốn đã là hai bảng riêng) như một phép chẻ bảng, trái định nghĩa §2, đồng thời **bỏ sót `promotion_schema.promotions`**, bảng thứ hai mang hai nghĩa (`max_total_uses`/`current_uses` bị trigger `increment_promotion_usage` đẩy). **Một mâu thuẫn bất biến:** §6.2 khai bốn bất biến *“hôm nay được giữ”* trong khi §3.2 dòng 4 khai `INV-04` là **phần xây mới** — mã chỉ kiểm một đơn `PENDING` và hai mức `max_per_order`, không có phép cộng dồn theo tài khoản/sự kiện; nay là **ba** bất biến. **Chuỗi thu tiền:** §6.1 nói rõ rollback **không** hoàn tác tiền đã vào VNPay, không có đường hoàn tiền lẫn đối soát trong mã, và mã cũ tự ghi trạng thái *“ticket was not issued”* — nên đường hoàn tiền là **xây mới ở cả sáu**, `PA-1` không ngoại lệ. **§7.1** thôi khai giao dịch §6.1 *“còn nguyên”*: `TicketIssuanceService:177` là một `@Modifying UPDATE` lên `events` (`BC-01`) chạy trong giao dịch, đúng cạnh #11 mà chính hàng trên đã tính là bị cắt. **`PA-6`**: `RG-2` lấy từ **ba** schema, không phải hai, và không phải ranh giới duy nhất lấy từ nhiều schema. **§8.2**: hàng *Tổng tiến trình JVM* bị dán nhãn sai hai lần — hai số hạng (chatbot, đơn vị chẩn đoán) **chưa được quyết** (`B11-A` §2 khai `CANDIDATE`; chatbot chưa từng được `B11-A` khai là tiến trình riêng), và Keycloak là JVM nên mốc thật là **7**, kéo theo phép tính quá cam kết heap từ ~12 GiB thành **~14 GiB**. **Định tuyến sai đã sửa:** `B11-B-OPEN-04` chuyển từ *“gate `B11-C`”* sang **“phân xử trước khi duyệt `B11-B`”** — `B11-C` chọn **từ** tập chứ không định nghĩa **thành viên** tập; `AGENTS.md` xử xung đột nguồn bằng *obtain a resolution*, không phải hoãn; và ba nguồn mà `B11-A` viện **đều im lặng** về câu hỏi, nên `B11-A` §8.2 sai khi khai đó là *“đọc trực tiếp câu chữ”*. **`OPEN-05`** mở rộng: phạm vi đúng là **năm** phương án, vì `PA-4` cũng chẻ tên `Khuyến mãi`. **Hai điểm mở mới:** `-06` `user-service` — `PRJ-008` đòi cắt ba phần và `v0.2` gọi là *“nguyên trạng”*; `-07` frontend — `PRJ-004`/`PRJ-005` ghi gate có `B11-B` mà phiếu **không có một dòng nào**, cũng không khai là hoãn. **Kết luận tổng nay phát biểu CÓ ĐIỀU KIỆN**, và phiếu tự khuyến nghị **chưa duyệt** | Sửa lỗi + hạ mức kết luận |
| `B11-B-v0.2` | 2026-08-31 | **Vòng thẩm định độc lập trên `v0.1` tìm 14 điểm bất nhất; 12 điểm sửa tại chỗ, 2 điểm mở thành `B11-B-OPEN-04`/`-05`.** ⚠️ **Có con số bị sửa, nên bản này bump phiên bản** — khác việc đóng `B11-B-OPEN-01` ở `v0.1`, vốn chỉ ghi một quyết định người thật và theo tiền lệ `GOV-033`/`GOV-060`/`GOV-072` thì không bump. **Sửa phép gán năng lực của hai lớp:** `EventSyncHelper` từ nhãn *“hạ tầng chung”* — không thuộc bảy năng lực — về **`BC-01`** (nó là lớp tiện ích tĩnh, người gọi duy nhất là `OrganizerEventService`), nên **cạnh #26 không bị cắt ở phương án nào** và **năm con số đổi**: `PA-1` 5–7→4–6, `PA-3` 16–18→15–17, `PA-4` 17→16, `PA-5` 23→22, `PA-6` 13–15→12–14 (`PA-2` giữ 9); và `TicketMessageListener` — `v0.1` gán nó `BC-06` ở §3.1 nhưng `BC-04` ở §4.2 — thống nhất về **`BC-04`**, kèm khai độ nhạy. **Sổ 26 cạnh được đếm lại độc lập và trùng khớp từng ô**, nên phần bị sửa là phép gán chứ không phải phép quét. **Sửa phát biểu tự mâu thuẫn:** #13 nay ghi là *bị cắt nhưng không sinh công gỡ* thay vì *“không phải gỡ ở phương án nào”*, và §7.7 có một hàng riêng cho nó; `PA-2` thôi tự nhận là *“phương án duy nhất”* không phải tách `event_schema` (`PA-4` cũng vậy); hàng §7.7 *“Ranh giới không có tài sản nào”* đổi nhãn thành **năng lực** kèm hàng phái sinh cho biết chỉ `PA-3` để `BC-05` đứng riêng; §13 sửa **chín → bảy** vị trí đọc repository xuyên năng lực — nặng vì §13 là phần duy nhất đề cử vào báo cáo; và §4.2 *Đọc ra* mục 3 thôi khai bảy cạnh `payment` → `booking` là *“chi phí chung bị cắt ở cả sáu”* — `PA-1` để `BC-02`, `BC-03`, `BC-04`, `BC-06` cùng `RG-1` nên **không cạnh nào trong bảy bị cắt ở `PA-1`**, và chúng thật ra là **điểm phân biệt sắc nhất giữa `PA-1` và năm phương án kia**. **Sửa trích dẫn:** §4.4 của `B11-A` không phải chỗ chứa bốn con số (đó là mục (5) của từng phương án, theo `quy-trinh` §4.4); `B5.5` §4.2 đánh số `B16`–`B18` chứ không phải `B17`–`B19`; trích đúng nguyên văn `B11-A` §6 nhận xét 5; thêm `B9-OPEN-01`; khai lại phiên bản `B5.5` đã gồm đính chính `GOV-074`. **Hai xung đột nguồn được báo, không tự hoà giải:** luật đếm trần 8 của `B5.5` §3.4 (*gồm chatbot/trợ lý*) ngược với `B11-A` (*đếm riêng*) — theo `B5.5` thì `PA-3`/`PA-5` = 9 > 8, lật `ASR-10`; và phạm vi cảnh báo `B11-A` §4.5(6) khai ba phương án trong khi `PA-1` cũng tách `BC-01` khỏi `BC-02`. **Kết luận tổng KHÔNG đổi:** không phương án nào bị loại, `B11-A-v0.4` không bị chạm một chữ, tập vẫn sáu phương án | Sửa lỗi + mở điểm mở |
| `B11-B-v0.1` | 2026-08-31 | **Mở cổng `B11-B` trên đầu vào `B11-A-v0.4` `APPROVED` (`GOV-070`), sau khi `GOV-072` ổn định tập sáu phương án.** Kiểm kê tài sản hiện thực ánh xạ lên bảy năng lực `B5` §3 với **năm khoảng trống** ghi thành `FACT`; sổ ghép nối **26 cạnh** liệt kê đủ, khớp con số 12/1 của `B5.5` §1.3 và thêm **13 cạnh `B5.5` chưa ghi**; ba `FACT` dữ liệu — **0 khoá ngoại xuyên schema**, `ticket_types` mang hai nghĩa trong một bảng, **một tài khoản `postgres` duy nhất**; **hai thân giao dịch cục bộ** đang giữ thứ mà các phương án đem chia, liệt kê dòng-theo-dòng, cộng **tám vị trí gọi cùng package** mà phép đếm import mù. Đối chiếu từng phương án theo **một bộ năm câu**, kèm hai khối ⛔ cấm đọc kết quả thành xếp hạng. Ràng buộc chung: mã tương quan gần như trắng ở ba service nghiệp vụ; **không tệp nào đặt heap**; hai tài khoản AWS cộng khoá Redisson phân tán; bí mật dạng rõ trong `docker-compose.apps.yml` (**mới** so với `B5.5` §2.5). Phép kiểm *"có phải trả ràng buộc về `B11-A`"* chạy trên **tám** phát hiện, kết quả **không** ở cả tám → **không phương án nào bị loại, không con số nào của `B11-A` bị đụng**. Báo một xung đột nguồn ở `B5.5` PHẦN 4 và **không tự sửa**. Ba điểm mở mới `B11-B-OPEN-01`…`-03`. **Cùng ngày, `-01` đã đóng** (`GOV-074`) — Lê Văn Minh chọn đính chính `B5.5` bằng chú thích và giữ nguyên văn cũ; việc đóng đó **không bump phiên bản** vì nó ghi một quyết định của người thật và không đổi kết luận nào (`GOV-033`, `GOV-060`, `GOV-072`) | Tạo mới |
