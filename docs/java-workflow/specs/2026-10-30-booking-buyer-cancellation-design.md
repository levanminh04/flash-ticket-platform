# Booking — API buyer hủy đơn chờ thanh toán

- Phiên bản **v0.1 — IMPLEMENTED**. Thiết kế lập ở pha BRAINSTORMING; triển khai theo plan sau xác nhận EXECUTING-PLANS của user ngày 30/10/2026.
- Task: [API buyer hủy đơn chờ thanh toán](https://app.clickup.com/t/z8qgvc9he0), đọc trực tiếp ngày 30/10/2026; deadline 31/10/2026 23:00 Asia/Saigon.
- Yêu cầu ban đầu: user gọi `$shopapp-java-brainstorming`, “lập spec cho task này”; sau đó chọn spec để lập plan và yêu cầu “triển khai theo plan này”. Danh sách thực thi đã được hỏi riêng; user trả lời “xác nhận”. Không lấy riêng yêu cầu viết spec làm duyệt triển khai.
- Checkout `D:/DATN/flash-ticket-platform`; Git common directory `D:/DATN/flash-ticket-platform/.git`; HEAD khảo sát `987e1f050c59464930314c915c423eb2190d06fe`; working tree sạch trước khi thêm file này.
- Nguồn nghiệp vụ: [B13 §3](../../architecture/B13-api-and-event-contracts.md), [B14 §1/§4/§5](../../architecture/B14-main-flow-sequences.md), mô tả task. B13/B14-v0.3 đang REVIEW_READY, không kế thừa phê duyệt toàn bản từ v0.1. Phần thiết kế dưới đây là baseline đã triển khai sau gate riêng; từ “đề xuất” giữ ngữ cảnh lúc lập spec.

## 1. Mục tiêu và hợp đồng API

Buyer hủy order của mình khi order còn `PENDING_PAYMENT`, reservation còn `HELD`, chưa nhận thanh toán và `decisionAt < expiresAt`. Booking chuyển order sang `CANCELLED`, trả tài nguyên HELD trong một transaction rồi mới trả HTTP thành công. Order đã freeze tiền vẫn được hủy nếu thỏa các điều kiện này.

| Thành phần | Đề xuất |
|---|---|
| Route | `POST /api/v1/orders/{orderId}/cancel` |
| Xác thực | JWT hợp lệ, role BUYER; buyerSubject chỉ lấy từ `Jwt.getSubject()` |
| Request | JSON object rỗng `{}`, Content-Type application/json; không nhận buyerSubject, lý do hoặc trạng thái đích |
| Response | `200 OrderView`, state CANCELLED; dùng `OrderMapper.toView(Order)` hiện có |
| Retry | Cùng chủ đơn, order đã CANCELLED → 200 trạng thái hiện có; không ghi lại updatedAt/counter/usage |
| Idempotency | Khóa order và terminal state; không thêm Idempotency-Key/receipt cho lệnh không có dữ liệu thay đổi |

Đề xuất bind body bằng `@RequestBody Map<String, Object>` rồi kiểm `request.isEmpty()` ngay controller. Chỉ dùng map cho command không có field: Jackson giải mã object, controller từ chối object có field bằng INVALID_REQUEST. Không tạo DTO rỗng, helper/parser hay tầng mapper mới. Thiếu body, null, JSON lỗi/không phải object và UUID sai bị framework từ chối; object có field không bao giờ dùng để đổi chủ đơn. Đây là ngoại lệ có chủ đích so với DTO class của Shop App, cần duyệt cùng M1.

Phạm vi gồm API, service transaction, mã lỗi cụ thể, request Postman trong folder Booking có sẵn và kiểm thử được mô tả ở §6. Chưa viết Java hoặc chạy test trong pha spec. Refund, gọi Payment, thông báo RabbitMQ, consumer/fan-out hủy event và buyer hủy đơn đã nhận tiền thuộc phạm vi khác.

## 2. Hiện trạng và lựa chọn ít thay đổi

| Nguồn đã đọc | Dùng trong thiết kế |
|---|---|
| `order/controller/OrderController.java:22–45` | Thêm endpoint trong controller hiện có; giữ constructor injection và response cụ thể |
| `order/service/OrderService.java:11–22` | Thêm đúng một chữ ký nghiệp vụ cancelOrder |
| `order/service/impl/OrderServiceImpl.java:79–169` — expireOrder | Làm mẫu lấy khóa tài nguyên và release; không gọi expireOrder để buyer hủy vì điều kiện deadline, replay và kết quả khác nhau |
| `OrderServiceImpl.java:993–1011` — preparePurchaseSnapshot | Dùng lại scalar event lookup và locked order query có buyerSubject; không tải Order cũ trước khi chờ khóa |
| `OrderServiceImpl.java:817–959` | Năm helper release đã dùng cho AcceptPayment/expiry; dùng lại nguyên thân/chữ ký với target RELEASED |
| `order/repository/OrderRepository.java:36–49` | Đã đủ query ownership, khóa order, DB clock và transaction lock timeout; không thêm query |
| `mapper/OrderMapper.java:16–24`, `dto/response/OrderView.java` | Mapping và kiểu trả về giữ nguyên; không thêm response hoặc API sinh |
| `config/SecurityConfig.java:46–47`; GatewaySecurityConfiguration | Nhóm route orders đã yêu cầu BUYER; không thêm security bean/annotation mới |

Chỉ thêm ba khai báo method production (controller, interface, implementation), một enum constant lỗi và các method test §6. Không tạo class Java, không tách helper từ expireOrder/settlePendingPayment, không đổi tên helper có hậu tố ForPaymentDecision trong task này. Tên cũ được ghi rõ đang phục vụ cả Payment, expiry và buyer cancel.

## 3. Transaction và phân xử trạng thái

`OrderServiceImpl.cancelOrder(String buyerSubject, UUID orderId)` có `@Override @Transactional`, REQUIRED mặc định. Controller gọi OrderService qua Spring proxy, transaction commit xong mới nhận kết quả để trả HTTP. Không gọi mạng trong transaction, không catch lỗi save/commit thành response thành công, không tự thêm retry.

1. Buyer subject thiếu/rỗng → UNAUTHENTICATED; orderId null khi gọi service trực tiếp → INVALID_REQUEST. Cấu hình lock timeout bằng method/property hiện có.
2. `findEventIdByIdAndBuyerSubject(orderId, buyerSubject)`; không có → ORDER_NOT_FOUND. Như vậy đơn không tồn tại và đơn người khác cùng trả 404, không đọc dữ liệu người khác để trả lỗi trạng thái.
3. Khóa snapshot bằng `findForBookingByEventId(eventId)` (PESSIMISTIC_READ), rồi order bằng `findLockedByIdAndBuyerSubject` (PESSIMISTIC_WRITE). Thiếu snapshot → CONFIG_NOT_READY; thiếu owned order → ORDER_NOT_FOUND. Giữ cùng thứ tự với expiry, AcceptPayment, applyPromotion và preparePurchaseSnapshot.
4. Đọc state sau khóa: CANCELLED → trả view ngay, trước kiểm deadline/event/accepted metadata, để replay cả đơn do event cancellation đã hủy cũng không gây side effect. EXPIRED → ORDER_EXPIRED. Các trạng thái khác PENDING_PAYMENT → ORDER_NOT_CANCELLABLE.
5. Với PENDING_PAYMENT, snapshot event CANCELLED → EVENT_NOT_ON_SALE, không release trong API này; việc hủy theo event thuộc fan-out task kế tiếp. Không dùng sale window hoặc config-version tăng làm điều kiện từ chối buyer hủy một đơn còn hợp lệ.
6. Đọc reservation theo order; phải tồn tại và HELD, acceptedChargeId/acceptedAt phải chưa có; vi phạm → RESERVATION_DATA_CONFLICT. Không yêu cầu frozen snapshot và không chặn paymentFrozenAt.
7. Khóa purchase limit của event/buyer → inventory thực sự được chọn → promotion inventory nếu có usage. QUANTITY khóa các ticketType theo query ID tăng dần; STANDING khóa sector; SEATED khóa các seat theo query ID tăng dần. Reservation/allocations/usage được thao tác dưới order lock như luồng expiry hiện tại; không thêm khóa ngược. Usage đọc theo reservation với các trạng thái HELD/USED/RELEASED như expiry, helper hiện có phân xử HELD trước cập nhật.
8. Đọc `currentDatabaseTime()` **sau các khóa có thể chờ**. `decisionAt >= order.expiresAt` → ORDER_EXPIRED và rollback, dù request được gửi trước hạn. API này không tự expire đơn khi từ chối; worker hiện có sẽ dọn. Không dùng JVM clock hoặc timestamp đầu transaction.
9. Gọi updatePurchaseLimitForPaymentDecision, helper inventory tương ứng và updatePromotionUsageForPaymentDecision với RELEASED. Counter HELD giảm; purchased/used không tăng; seat chỉ trả nếu vẫn HELD thuộc reservation hiện tại theo guard có sẵn. Không thay generation hoặc tài nguyên của reservation khác.
10. Chuyển allocations và reservation RELEASED, order CANCELLED, updatedAt=decisionAt; save bằng repository hiện có, allocations managed được dirty checking. Giữ giá, tiền, expiry, email và frozen snapshot để tra lịch sử. Map OrderView trong transaction; mọi lỗi mapping/save/flush/commit làm lời gọi thất bại và rollback toàn bộ.

### Bảng kết quả

| Trạng thái sau khóa / điều kiện | Kết quả |
|---|---|
| Owned CANCELLED, kể cả đã qua hạn/event đã hủy | 200 CANCELLED, không release/ghi lại |
| Pending + HELD, event chưa CANCELLED, chưa nhận charge, decisionAt < expiresAt | Release HELD và CANCELLED cùng commit |
| Pending đã tới hạn hoặc EXPIRED | 409 ORDER_EXPIRED, không ghi |
| ISSUING / COMPLETED / ISSUANCE_FAILED | 409 ORDER_NOT_CANCELLABLE, không trả purchased/USED |
| Pending, snapshot CANCELLED | 409 EVENT_NOT_ON_SALE, chờ luồng event cancellation/expiry xử lý theo trách nhiệm riêng |
| Pending nhưng reservation/counter/quyền giữ tài nguyên không thỏa guard hiện có | 409 RESERVATION_DATA_CONFLICT, rollback |
| Không có order thuộc buyer | 404 ORDER_NOT_FOUND |
| Chờ khóa/DB/commit lỗi | ErrorCode/global handler hiện có; không báo thành công hoặc release một phần |

Mã lỗi mới duy nhất: `ORDER_NOT_CANCELLABLE(CONFLICT, "Đơn hàng không ở trạng thái có thể hủy.")` trong enum ErrorCode. Không dùng message “không thể thanh toán” của ORDER_NOT_PAYABLE cho thao tác hủy; không thêm exception class/method xử lý lỗi mới.

### Cạnh tranh có thể xảy ra

- Hai lần cancel: bên sau đọc CANCELLED sau khóa, trả view, chỉ có một lần release. Retry sau timeout HTTP cũng theo cách này; không hứa response byte-for-byte từ receipt cũ.
- AcceptPayment commit ACCEPTED trước → ISSUING, cancel bị từ chối. Cancel commit trước → AcceptPayment hiện có đọc CANCELLED, xử lý khoản thu muộn theo PaymentDecision; không cấp lại ghế hoặc cần gọi Payment để commit cancel. Frozen snapshot đã cấp trước hủy vẫn giữ cho đối chiếu callback.
- Expiry commit trước → cancel thấy EXPIRED và trả ORDER_EXPIRED; cancel commit trước hạn → expiry bỏ qua CANCELLED. Nếu cancel giữ order trước nhưng chờ tài nguyên qua hạn, DB clock sau khóa làm cancel rollback, expiry có thể xử lý tiếp. Bắt đầu transaction trước không có nghĩa luôn thắng.
- ApplyPromotion commit trước → cancel đọc usage/discount mới rồi trả voucher đó. Cancel commit trước → applyPromotion bị guard state từ chối. PreparePurchaseSnapshot commit trước → cancel vẫn được phép nếu còn hạn; cancel commit trước → snapshot request bị chặn. Không gia hạn giữ chỗ.
- Snapshot cancellation ghi trước → cancel pending bị chặn ở bước 5. Nếu buyer cancel đã khóa snapshot trước, consumer cập nhật snapshot chờ transaction đó; createOrder sau khi snapshot CANCELLED vẫn bị gate event chặn. Không tuyên bố đã hoàn thành fan-out hủy event từ API này.

## 4. Đối chiếu convention và bảo đảm đã có

Đã đọc common.md, projects/INDEX.md và projects/flash-ticket-platform.md theo Git common directory trước khi quyết định thiết kế.

| Rule / bằng chứng source | Áp dụng cụ thể |
|---|---|
| Không thêm validation thừa; đọc transaction tạo dữ liệu | createOrder:1151–1285 tạo order/items/reservation/limit trong cùng TX; reserveQuantityTickets:1409, reserveStandingTickets:1433, reserveSeatedTickets:1459 và addReservationAllocation:1503 tạo allocation trực tiếp từ item/selection đã nhận. Không kiểm lại tổng allocation/order item, selection hoặc giá; không khôi phục validateReservationAllocations |
| Phân biệt invariant ban đầu với trạng thái hiện tại | AcceptPayment/expiry/voucher có thể commit trong lúc cancel chờ khóa. Vì vậy vẫn cần state, deadline, reservation HELD và ownership sau khóa; dùng guard helper hiện có để bảo vệ counter và seat holder, không tạo method verify riêng |
| Tái sử dụng trước khi tách helper | Đã có đủ năm update*ForPaymentDecision; cancel chỉ thêm caller RELEASED. Không đổi thân helper hoặc kiểm tra cũ của settlePendingPayment/expiry |
| Spring Data, save; không ép flush | Dùng query/clock/timeout/save/saveAll hiện có; không thêm native SQL, EntityManager, saveAndFlush hoặc migration |
| Chủ động báo NEW/MODIFIED/REUSE | §5–§6 liệt kê đầy đủ khai báo đề xuất; phần cũ chỉ thêm endpoint/chữ ký/service body, enum constant và test; runtime expiry/Payment không sửa |
| Mapping/injection theo repo và Shop App | Dùng OrderMapper MapStruct đã có, constructor injection đã có; không thêm thư viện/type DTO. Map body rỗng là ngoại lệ riêng nêu rõ ở M1 |

DDL tham khảo `docs/architecture/data/postgres/20-booking.sql` có FK/unique/check cho ownership/link và counter; không coi mọi constraint trong DDL này đương nhiên đã tồn tại trên server chạy ddl-auto:update. Lý do bỏ kiểm lặp cấu trúc dựa cả vào các đường ghi đã đọc, không chỉ vào DDL hoặc test. Task không thay schema.

Giới hạn source hiện tại: helper seat kiểm currentReservationId/state và generation > 0; không có phép so với một expected generation lưu riêng trên allocation. Spec không khẳng định đã có phép so đó. API đồng bộ này xác định reservation hiện tại dưới order lock và không nhận release message/generation từ ngoài; không thêm field/check generation mới chỉ để mở rộng cơ chế.

## 5. Danh sách duyệt thiết kế

Đường dẫn production dưới `services/booking-service/src/main/java/com/flashticket/booking_service/`; test dưới `services/booking-service/src/test/java/com/flashticket/booking_service/`.

| ID | Class/file | NEW hoặc EXISTING | Vai trò, annotation/API sinh | Mẫu Shop App hoặc ngoại lệ |
|---|---|---|---|---|
| C1 | `order/controller/OrderController.java` | EXISTING | Thêm M1; giữ @RestController/@RequestMapping/@RequiredArgsConstructor, không thêm field hoặc API Lombok | Shop App controllers/OrderController.deleteOrder: controller gọi service, ResponseEntity; Map command rỗng là ngoại lệ |
| C2 | `order/service/OrderService.java` | EXISTING | Thêm M2, không đổi method cũ | Shop App services/IOrderService; giữ tên/package của repo đích |
| C3 | `order/service/impl/OrderServiceImpl.java` | EXISTING | Thêm M3; giữ @Service/@RequiredArgsConstructor và toàn bộ dependency/property hiện có | Shop App services/impl/OrderService.deleteOrder: đọc/sửa/save; transaction/locks là yêu cầu Booking |
| C4 | `exception/ErrorCode.java` | EXISTING | Thêm ORDER_NOT_CANCELLABLE; giữ constructor/status/message/handler hiện có | Dùng exception/error catalog của repo, không sao chép catch-all của Shop App |
| C5 | `order/controller/OrderControllerTests.java` | EXISTING | Thêm U1–U3; giữ @WebMvcTest, @Import, MockMvc và @MockitoBean có sẵn | Test boundary/security của repo |
| C6 | `persistence/BookingPersistenceIT.java` | EXISTING | Thêm D1–D8; giữ test containers, real service proxy, mapper, transaction fixture và repository spy có sẵn | Test TX/PostgreSQL thực của repo |

Danh sách C1–C6 và khai báo dưới đây đã được user xác nhận riêng cho EXECUTING-PLANS; xem nguồn duyệt và kết quả trong plan. Không có class Java mới, constructor mới, field mới hoặc API Lombok/codegen mới.

| ID | Class | Constructor/method đầy đủ | NEW hoặc REUSE | Cách viết và lý do cần |
|---|---|---|---|---|
| M1 | C1 | `public ResponseEntity<OrderView> cancelOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId, @RequestBody Map<String, Object> request)` | NEW | @PostMapping("/{orderId}/cancel"); kiểm object rỗng tại boundary, lấy subject JWT và gọi M2; không @Valid vì không có field DTO cần Bean Validation |
| M2 | C2 | `public abstract OrderView cancelOrder(String buyerSubject, UUID orderId)` | NEW | Chữ ký service cụ thể cho buyer cancel; Java interface có thể viết gọn không public abstract |
| M3 | C3 | `public OrderView cancelOrder(String buyerSubject, UUID orderId)` | NEW | @Override @Transactional; trực tiếp lấy khóa/phân xử/gọi helpers/save/map theo §3. ExpireOrder chỉ xử lý đến hạn và trả void nên không đáp ứng API này |
| R1 | C3 | `private void updateQuantityInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<TicketTypeInventory> lockedInventories, ReservationState targetState)` | REUSE | QUANTITY, target RELEASED; nguyên trạng |
| R2 | C3 | `private void updateStandingInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, SectorInventory lockedSector, ReservationState targetState)` | REUSE | STANDING, target RELEASED; nguyên trạng |
| R3 | C3 | `private void updateSeatedInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<SeatInventory> lockedSeats, ReservationState targetState)` | REUSE | SEATED, target RELEASED; nguyên trạng |
| R4 | C3 | `private void updatePurchaseLimitForPaymentDecision(Order lockedOrder, Reservation storedReservation, PurchaseLimit lockedPurchaseLimit, ReservationState targetState)` | REUSE | Trả held của buyer, target RELEASED; nguyên trạng |
| R5 | C3 | `private void updatePromotionUsageForPaymentDecision(Order lockedOrder, PromotionUsage storedUsage, PromotionInventory lockedPromotion, ReservationState targetState)` | REUSE | Dựa usage/inventory, hỗ trợ cả chưa freeze và đã freeze; nguyên trạng |
| R6 | OrderMapper | `public abstract OrderView toView(Order order)` | REUSE | MapStruct mapping đã có; không thêm mapper method hoặc response |

Logic khóa/phân xử của M3 viết trực tiếp theo expireOrder, còn cập nhật từng tài nguyên dùng R1–R5. Không truyền cờ buyer-cancel vào expireOrder hoặc sửa Payment caller để dùng chung một method điều phối nhiều nghiệp vụ. Những caller cũ của R1–R5 không đổi hành vi.

## 6. Kiểm thử đề xuất và điều kiện nghiệm thu

Danh sách dưới là thiết kế test để duyệt, chưa cho phép viết/chạy ở pha này. Không thêm setup/helper/type test ngoài bảng; nếu plan cần bổ sung thì trình delta. Các method đều NEW, package-private, non-static. U1–U3 có @Test; D1–D8 có @Test và @Transactional(propagation=Propagation.NOT_SUPPORTED), gọi service qua proxy để kiểm commit thật. Fixture/concurrency utilities của BookingPersistenceIT được dùng lại.

| ID | Class | Chữ ký | Điều kiện chứng minh |
|---|---|---|---|
| U1 | C5 | `void cancelsOrderUsingAuthenticatedBuyer() throws Exception` | POST {} trả 200 CANCELLED, subject chuyển đúng vào service; business lỗi trả code/status theo handler |
| U2 | C5 | `void requiresBuyerAuthenticationForCancellation() throws Exception` | Thiếu JWT → 401, sai role → 403; service không được gọi |
| U3 | C5 | `void rejectsInvalidCancellationCommand() throws Exception` | Body thiếu/null/array/JSON lỗi/field lạ và UUID sai bị từ chối; không gọi service |
| D1 | C6 | `void cancelsPendingOrdersAcrossInventoryModesAndFreezeStates() throws Exception` | QUANTITY/STANDING/SEATED, có/không voucher, trước/sau freeze; order CANCELLED, reservation/allocations RELEASED, đúng held giảm, purchased/used không tăng, tiền/frozen snapshot giữ nguyên |
| D2 | C6 | `void replaysCancellationWithoutReleasingNewReservations() throws Exception` | Retry tuần tự và hai cancel đồng thời chỉ release một lần; buyer khác giữ lại seat sau hủy thì retry order cũ không đụng holder/generation mới; replay sau hạn vẫn 200 |
| D3 | C6 | `void hidesOrdersFromOtherBuyersAndRejectsInvalidCancellationInputs() throws Exception` | Order của buyer khác và ID không tồn tại cùng ORDER_NOT_FOUND; null/blank subject và null ID dùng code boundary service; không mutation |
| D4 | C6 | `void rejectsUncancellableStatesAndExpiredReservations() throws Exception` | ISSUING/COMPLETED/ISSUANCE_FAILED, EXPIRED, pending đã qua hạn, snapshot CANCELLED; state/counter giữ nguyên; CANCELLED replay được trước deadline/event check |
| D5 | C6 | `void arbitratesBuyerCancellationAgainstAcceptPayment() throws Exception` | Cả hai thứ tự khóa/commit: payment thắng → ISSUING và không cancel; cancel thắng → Payment không ACCEPTED, không trả tài nguyên lần hai; kiểm receipt/outbox của Payment như flow thật |
| D6 | C6 | `void arbitratesBuyerCancellationAgainstExpiryAndDeadline() throws Exception` | Cancel trước hạn thắng → expiry skip; expiry thắng → cancel ORDER_EXPIRED; chờ tài nguyên qua hạn → cancel rollback; equality deadline cũng hết hạn |
| D7 | C6 | `void serializesBuyerCancellationWithPromotionAndSnapshot() throws Exception` | Voucher/snapshot trước cancel và cancel trước các lệnh này: voucher được release đúng, snapshot không ngăn cancel; lệnh đến sau cancel bị chặn; snapshot update CANCELLED trước cancel chặn release từ API |
| D8 | C6 | `void rollsBackBuyerCancellationOnResourceAndCommitFailures() throws Exception` | Lỗi helper/lock và lỗi deferred commit làm mọi counter/order/usage rollback; retry hợp lệ xử lý đúng một lần; không tạo message/inbox/outbox do API cancel |

D6 dùng giờ DB thật trong ca chờ khóa; equality có thể dùng repository spy đã có `doReturn(expiresAt)` chỉ cho clock trong ca biên, sau khi dựng fixture hợp lệ. Không stub query khóa hoặc service nghiệp vụ. D8 có thể dùng cơ chế trigger lỗi commit đã có trong suite trên PostgreSQL cô lập, dọn trong finally; đây là fault injection để kiểm atomicity, không là lý do thêm validation production.

Chạy kiểm thử ở pha triển khai khi có yêu cầu/duyệt tương ứng, ưu tiên PostgreSQL Testcontainers hiện có. Chưa seed DB119 hoặc gửi message nghiệp vụ thật. Các test AcceptPayment/expiry/promotion hiện có cần tiếp tục đạt vì thêm caller mới dùng chung helper. Không coi MockMvc mock-service là bằng chứng transaction thật hoặc test PostgreSQL là kiểm thử E2E Payment/Ticket.

## 7. Điểm khác mẫu và trạng thái duyệt

Đã đọc trực tiếp Shop App `controllers/OrderController.java`, `services/IOrderService.java`, `services/impl/OrderService.updateOrder/deleteOrder`. Áp dụng controller → service interface → implementation, constructor injection, repository/setter/save và ResponseEntity; giữ UUID, JWT ownership, OrderView/MapStruct và global handler của Booking. Không dùng xóa mềm active=false của Shop App để thay lifecycle CANCELLED.

Ngoại lệ cần duyệt cùng danh sách: @AuthenticationPrincipal Jwt; body Map chỉ kiểm object rỗng thay DTO; @Transactional/pessimistic locks/DB clock của Booking; MapStruct hiện có; MockMvc/MockitoSpyBean/Testcontainers và test cạnh tranh/commit thật đã có mẫu ở repo. Không thêm dependency, annotation type, cấu hình, schema, scheduler hoặc framework mới.

- Pha: **BRAINSTORMING**; phiên bản/danh sách: **v0.1, C1–C6, M1–M3, R1–R6, U1–U3, D1–D8**, annotation/ngoại lệ kèm theo.
- Trạng thái thực thi: **COMPLETE**, sau gate riêng EXECUTING-PLANS; nguồn xác nhận là user “xác nhận” sau danh sách C1–C6/M1–M3/R1–R6/U1–U3/D1–D8 và ngoại lệ trong chat. Chi tiết tại [plan](../plans/2026-10-30-booking-buyer-cancellation-plan.md).
- Full verify: 375 unit + 115 integration pass; D8 được tăng assertion voucher rollback và focused rerun 1/1 pass; HTTP curl 10/10 pass với DB/broker/Keycloak cô lập. Xem [TESTING.md](../../../services/booking-service/TESTING.md) để phân biệt phạm vi từng lần chạy. User đã giao bàn giao bằng `shopapp-java-finishing-task`; kết quả push/deploy được ghi tại task ClickUp sau khi xác minh.
