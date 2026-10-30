# Booking — Plan API buyer hủy đơn chờ thanh toán

- Phiên bản **v0.1 — EXECUTING-PLANS COMPLETE**. Lịch sử: lập ở pha WRITING-PLANS; user chọn “triển khai theo plan này”, rồi xác nhận danh sách thực thi bằng “xác nhận”.
- Đầu vào: [spec v0.1](../specs/2026-10-30-booking-buyer-cancellation-design.md), được user chọn bằng yêu cầu “viết pla từ spec này” ngày 30/10/2026. Giữ nguyên thiết kế, không thêm delta class/method. Khi lập bản nháp, spec còn nhãn PENDING; yêu cầu lập plan không thay cho xác nhận EXECUTING-PLANS đã hỏi riêng sau đó.
- Task: [API buyer hủy đơn chờ thanh toán](https://app.clickup.com/t/z8qgvc9he0); deadline theo spec: 31/10/2026 23:00 Asia/Saigon.
- Checkout `D:/DATN/flash-ticket-platform`, Git common directory `D:/DATN/flash-ticket-platform/.git`; baseline khảo sát `987e1f050c59464930314c915c423eb2190d06fe`. Khi bắt đầu pha plan chỉ có spec buyer-cancellation chưa track; không thay đổi Java.
- Trạng thái thực thi: **hoàn tất**. C1–C6, M1–M3, R1–R6, U1–U3, D1–D8 cùng annotation/ngoại lệ được user xác nhận cho EXECUTING-PLANS bằng “xác nhận” sau bảng danh sách trong chat. Không phát sinh khai báo Java ngoài danh sách.

## 1. Kết quả cần đạt

`POST /api/v1/orders/{orderId}/cancel` nhận JWT BUYER và body `{}`, trả `200 OrderView` sau commit. Buyer chỉ hủy order của mình còn PENDING_PAYMENT, reservation HELD, chưa nhận charge và giờ DB sau các khóa nhỏ hơn expiresAt. Đơn đã freeze tiền vẫn được hủy nếu còn đủ điều kiện.

Transaction trả inventory, purchase limit và promotion usage HELD, chuyển allocations/reservation RELEASED và order CANCELLED. Retry owned CANCELLED chỉ trả trạng thái hiện có, không đổi updatedAt hoặc release lần nữa. Đơn người khác và ID không tồn tại cùng ORDER_NOT_FOUND. EXPIRED/đến hạn trả ORDER_EXPIRED; ISSUING/COMPLETED/ISSUANCE_FAILED trả mã mới ORDER_NOT_CANCELLABLE. Pending có snapshot CANCELLED trả EVENT_NOT_ON_SALE theo spec, không làm fan-out hủy event.

Phạm vi: sáu file Java hiện có, một request Postman và cập nhật TESTING.md sau khi có kết quả thật. Không thêm class Java, helper, query, DTO, field/entity/schema, dependency hoặc cấu hình. Các helper Payment/expiry được dùng nguyên trạng; không sửa settlePendingPayment/expireOrder. Không gọi Payment/RabbitMQ để commit hủy; refund và consumer hủy event thuộc task khác.

## 2. Bảng duyệt class và method

Prefix đường dẫn:

- `MAIN` = `services/booking-service/src/main/java/com/flashticket/booking_service/`.
- `TEST` = `services/booking-service/src/test/java/com/flashticket/booking_service/`.

| ID | Class/file | NEW hoặc EXISTING | Vai trò, field/annotation/API sinh | Mẫu cụ thể hoặc ngoại lệ |
|---|---|---|---|---|
| C1 | `MAIN/order/controller/OrderController.java` | EXISTING | Thêm M1; giữ @RestController, @RequestMapping, @RequiredArgsConstructor và các final dependency hiện có | Shop App controllers/OrderController.deleteOrder: gọi service và ResponseEntity; JWT/Map là ngoại lệ của task |
| C2 | `MAIN/order/service/OrderService.java` | EXISTING | Thêm M2; không thêm interface hoặc API khác | Shop App services/IOrderService.deleteOrder; giữ tên/package đích |
| C3 | `MAIN/order/service/impl/OrderServiceImpl.java` | EXISTING | Thêm M3; giữ @Service, @RequiredArgsConstructor, dependency và @Value lock timeout đã có | Shop App services/impl/OrderService.deleteOrder: đọc/setter/save; TX/khóa theo Booking |
| C4 | `MAIN/exception/ErrorCode.java` | EXISTING | Chỉ thêm ORDER_NOT_CANCELLABLE(CONFLICT, "Đơn hàng không ở trạng thái có thể hủy.") | Catalog lỗi hiện có; không thêm exception/handler method |
| C5 | `TEST/order/controller/OrderControllerTests.java` | EXISTING | Thêm U1–U3, giữ @WebMvcTest/@Import, MockMvc và @MockitoBean | Boundary/security test hiện có của Booking |
| C6 | `TEST/persistence/BookingPersistenceIT.java` | EXISTING | Thêm D1–D8, dùng service proxy, repository spy và fixture PostgreSQL Testcontainers hiện có | NGOÀI MẪU Shop App; theo các test expiry/AcceptPayment của repo |

Không thêm constructor, field, nested/anonymous type, setup/helper, @Bean hoặc API Lombok/MapStruct mới. Annotation sinh code hiện có không đổi. Tất cả method NEW dưới đây có return/visibility/throws như bảng, non-static; không có throws ở M1–M3. Test method là package-private.

| ID | Class | Chữ ký đầy đủ | NEW hoặc REUSE | Annotation/cách viết và lý do |
|---|---|---|---|---|
| M1 | C1 | `public ResponseEntity<OrderView> cancelOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId, @RequestBody Map<String, Object> request)` | NEW | @PostMapping("/{orderId}/cancel"); object rỗng, subject JWT, ResponseEntity.ok; thêm boundary còn thiếu |
| M2 | C2 | `public abstract OrderView cancelOrder(String buyerSubject, UUID orderId)` | NEW | Interface có thể viết gọn `OrderView cancelOrder(...)`; một use case cụ thể |
| M3 | C3 | `public OrderView cancelOrder(String buyerSubject, UUID orderId)` | NEW | @Override @Transactional REQUIRED; query/guard/setter/save/map trực tiếp; expiry có deadline/kết quả khác nên không dùng để thay buyer cancel |
| R1 | C3 | `private void updateQuantityInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<TicketTypeInventory> lockedInventories, ReservationState targetState)` | REUSE | Target RELEASED; QUANTITY held giảm, purchased giữ nguyên |
| R2 | C3 | `private void updateStandingInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, SectorInventory lockedSector, ReservationState targetState)` | REUSE | Target RELEASED; trả pool sector chung |
| R3 | C3 | `private void updateSeatedInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<SeatInventory> lockedSeats, ReservationState targetState)` | REUSE | Target RELEASED; giữ guard holder/state, không sửa ghế R2 |
| R4 | C3 | `private void updatePurchaseLimitForPaymentDecision(Order lockedOrder, Reservation storedReservation, PurchaseLimit lockedPurchaseLimit, ReservationState targetState)` | REUSE | Target RELEASED; trả held của buyer |
| R5 | C3 | `private void updatePromotionUsageForPaymentDecision(Order lockedOrder, PromotionUsage storedUsage, PromotionInventory lockedPromotion, ReservationState targetState)` | REUSE | Target RELEASED; dùng usage thực, không đòi frozen snapshot |
| R6 | OrderMapper | `public abstract OrderView toView(Order order)` | REUSE | MapStruct hiện có, gọi khi persistence context còn mở |
| U1 | C5 | `void cancelsOrderUsingAuthenticatedBuyer() throws Exception` | NEW | @Test; MockMvc {} → 200 CANCELLED, đúng subject; business lỗi giữ code/status |
| U2 | C5 | `void requiresBuyerAuthenticationForCancellation() throws Exception` | NEW | @Test; 401/403, không gọi service |
| U3 | C5 | `void rejectsInvalidCancellationCommand() throws Exception` | NEW | @Test; body thiếu/null/array/JSON lỗi/field lạ, UUID sai → 400, không gọi service |
| D1 | C6 | `void cancelsPendingOrdersAcrossInventoryModesAndFreezeStates() throws Exception` | NEW | @Test + NOT_SUPPORTED; ba kiểu inventory, voucher có/không, freeze có/không; kiểm toàn bộ dữ liệu sau commit |
| D2 | C6 | `void replaysCancellationWithoutReleasingNewReservations() throws Exception` | NEW | @Test + NOT_SUPPORTED; retry/đồng thời, replay sau hạn, seat R2 giữ lại không bị trả |
| D3 | C6 | `void hidesOrdersFromOtherBuyersAndRejectsInvalidCancellationInputs() throws Exception` | NEW | @Test + NOT_SUPPORTED; ownership/ID/input service, không mutation |
| D4 | C6 | `void rejectsUncancellableStatesAndExpiredReservations() throws Exception` | NEW | @Test + NOT_SUPPORTED; state/deadline/event và thứ tự CANCELLED replay |
| D5 | C6 | `void arbitratesBuyerCancellationAgainstAcceptPayment() throws Exception` | NEW | @Test + NOT_SUPPORTED; hai thứ tự cạnh tranh, không vừa accepted vừa trả HELD |
| D6 | C6 | `void arbitratesBuyerCancellationAgainstExpiryAndDeadline() throws Exception` | NEW | @Test + NOT_SUPPORTED; hai thứ tự expiry/cancel, chờ qua hạn, equality |
| D7 | C6 | `void serializesBuyerCancellationWithPromotionAndSnapshot() throws Exception` | NEW | @Test + NOT_SUPPORTED; voucher/freeze trước/sau cancel và snapshot cancellation trước cancel |
| D8 | C6 | `void rollsBackBuyerCancellationOnResourceAndCommitFailures() throws Exception` | NEW | @Test + NOT_SUPPORTED; lỗi tài nguyên/khóa/commit, rollback và retry |

NOT_SUPPORTED ở D1–D8 là `@Transactional(propagation=Propagation.NOT_SUPPORTED)` trên test, để lời gọi service có transaction riêng và kiểm được lỗi commit qua Spring proxy. R1–R6 không đổi thân/chữ ký; chỉ M3 thêm caller. Các item dùng chung vẫn giữ ID của spec.

Ngoại lệ cần duyệt: `@AuthenticationPrincipal Jwt`, command `{}` bind Map thay DTO, @Transactional/pessimistic locks/DB clock, mapper MapStruct có sẵn; MockMvc, repository @MockitoSpyBean, Testcontainers và concurrency/commit fault injection theo suite hiện tại. M1 chỉ dùng Jackson converter của Spring để nhận Map rồi kiểm rỗng; không parser/validator tự viết và không @Valid vì command không có field DTO. Map không lưu DB. Không có ngoại lệ/dependency mới khác so với spec.

## 3. Các bước triển khai sau khi qua gate

Các bước dưới đây giữ nội dung plan; trạng thái và kết quả đã thực hiện ghi ở §5.

Mỗi bước chỉ được thực thi sau khi item tương ứng được duyệt WRITING-PLANS và user yêu cầu triển khai, rồi hoàn tất gate EXECUTING-PLANS riêng. Đây là bước công việc, không phải yêu cầu tạo commit cho từng bước.

### T1 — Service hủy đơn và mã lỗi

**File/item:** C2/M2, C3/M3, C4; tái sử dụng R1–R6. Phụ thuộc: gate thiết kế/plan và execution đã chốt đúng danh sách.

1. Thêm ORDER_NOT_CANCELLABLE vào ErrorCode, giữ constructor/status/message và global handler hiện có.
2. Thêm M2 và M3 cùng chữ ký. Service kiểm subject thiếu/rỗng → UNAUTHENTICATED, orderId null → INVALID_REQUEST. Tái dùng property và configureTransactionLockTimeout hiện có.
3. Scalar event lookup lọc buyer → snapshot PESSIMISTIC_READ → order PESSIMISTIC_WRITE có buyer. Hai query ownership đều trả ORDER_NOT_FOUND khi không có; không tải Order chưa khóa vào persistence context.
4. Sau order lock: CANCELLED trả R6 ngay; EXPIRED trả ORDER_EXPIRED; state khác PENDING_PAYMENT trả ORDER_NOT_CANCELLABLE. Pending có snapshot CANCELLED trả EVENT_NOT_ON_SALE. Không dùng sale window/config version/freeze để tự cấm hủy.
5. Pending phải chưa có acceptedChargeId/acceptedAt và reservation phải HELD. Thiếu/sai → RESERVATION_DATA_CONFLICT. Không thêm validateReservationAllocations hoặc kiểm lại tổng order/item/allocation do transaction tạo dữ liệu đã bảo đảm.
6. Khóa purchase limit theo event/buyer → đúng inventory QUANTITY hoặc STANDING hoặc SEATED bằng query hiện có → promotion inventory theo usage của reservation. Usage query gồm HELD/USED/RELEASED như expiry; R5 kiểm trạng thái trước cập nhật. Thứ tự inventory do query ID tăng dần, không dựa sort trước saveAll để khẳng định thứ tự khóa.
7. Sau các khóa, đọc currentDatabaseTime. Bằng hoặc quá expiresAt → ORDER_EXPIRED, rollback, để worker xử lý expiry. Các nhánh trước đó theo đúng thứ tự spec; không tự đổi priority lỗi.
8. Gọi R4, R1/R2/R3 tương ứng rồi R5 với RELEASED. Chuyển allocations và reservation RELEASED, order CANCELLED, updatedAt=decisionAt. save reservation/order, allocations managed dùng dirty checking; trả R6. Không đổi giá/discount/total/expiry/frozen snapshot hoặc receipt tạo đơn.
9. Comment tiếng Việt ngắn ở replay sau khóa, DB clock và điểm rollback/commit. Không catch lỗi thành thành công; không REQUIRES_NEW, TransactionTemplate, @Retryable hoặc saveAndFlush trong production.

**Kết quả kiểm:** compile cùng thay đổi C2/C3; D1–D8 sau T3 kiểm trạng thái/atomicity/cạnh tranh. Chỉ có một transaction nghiệp vụ, không message hoặc network side effect. Không sửa expireOrder, settlePendingPayment hoặc thân R1–R5.

### T2 — Endpoint HTTP

**File/item:** C1/M1, dùng C2/M2. Phụ thuộc: T1.

- Thêm @PostMapping("/{orderId}/cancel") trong controller hiện có; thêm import Map/BusinessException/ErrorCode khi cần, không thêm dependency field.
- Body Map không rỗng → INVALID_REQUEST ngay boundary; body `{}` gọi `orderService.cancelOrder(jwt.getSubject(), orderId)` và trả ResponseEntity.ok. Không đọc buyer hoặc reason từ body và không nhận Idempotency-Key.
- Giữ security matcher `/api/v1/orders/**` có BUYER ở Booking và nhóm orders ở gateway; không tạo security bean/annotation hoặc mở route nội bộ.

**Kết quả kiểm:** U1–U3 chứng minh subject/security/body/error contract. Lời gọi service qua Spring proxy commit trước khi controller nhận view để trả HTTP.

### T3 — Test API và transaction/cạnh tranh

**File/item:** C5/U1–U3 và C6/D1–D8. Phụ thuộc: T1–T2; không thêm test class/setup/helper/constructor ngoài bảng.

- U1–U3 dùng MockMvc/@MockitoBean hiện có; body rỗng hợp lệ, mọi trường lạ bị từ chối; lỗi xác thực/UUID/JSON không gọi service.
- D1 dựng order bằng createOrder thật qua proxy; chọn salesMode QUANTITY hoặc SEAT_MAP với sector STANDING/SEATED, không tạo enum mode mới. Bao phủ voucher/freeze như bảng; snapshot tiền trước/sau, held/reservation/allocation/usage đều được kiểm sau commit bằng truy vấn độc lập. Voucher hết cửa sổ cũng không cản trả usage đang HELD.
- D2 kiểm retry tuần tự và hai caller độc lập; thêm trường hợp R1 đã hủy, R2 giữ lại cùng seat, cancel R1 lại không đổi state/holder/generation của R2. Replay CANCELLED sau hạn/event marker vẫn không mutation.
- D3/D4 kiểm ownership, input trực tiếp service và toàn bộ nhánh từ chối. Fixture terminal dùng luồng nghiệp vụ hiện có khi phù hợp; không coi fixture sửa DB thành case cần validation mới.
- D5–D7 dùng executor/latch và transaction độc lập theo mẫu expiry; xác nhận đã chờ khóa bằng quan sát pg_locks hiện có, không chọn winner bằng sleep đoán. Payment fixture có PurchaseSnapshot hợp lệ, gọi acceptPayment thật; cancel thắng → PaymentDecision.CANCELLED theo settlePendingPayment hiện có, resource không bị trả lần hai. LATE là nhánh order hết hạn; không đổi logic Payment để khớp diễn đạt nhầm trước đó của plan. Snapshot CANCELLED riêng do luồng cấu hình/snapshot hiện có, không triển khai consumer hủy event mới để test task này.
- D6: chờ qua hạn dùng clock_timestamp thật. Equality dùng `doReturn(expiresAt)` trên orders spy chỉ sau khi dựng fixture, trong đúng ca biên; khôi phục spy trong finally trước các lời gọi khác. Không mock query khóa hoặc production service. Nếu đổi tạm hold duration thì luôn restore finally.
- D8 dựa captureOrderCreationState và mẫu rollsBackExpiryWhenCommitFailsAndRetriesSafely: tiêm lỗi helper/resource hoặc giữ khóa quá timeout rồi kiểm rollback; deferred constraint trigger lỗi commit chỉ trên PostgreSQL test. Dọn trigger/function, executor/latch và fixture trong finally. Kiểm retry sau bỏ lỗi xử lý đúng một lần; API không ghi inbox/outbox.
- Tái dùng fixture `seedOrderCreationConfiguration`, `createOrderRequest`, `captureOrderCreationState` và utility hiện có; không thêm callback/type Java khai báo mới ngoài bảng. Lambda concurrency theo cách test hiện có không dùng để giấu helper mới.

**Kết quả kiểm:** các method mới đạt và test cũ của expiry/AcceptPayment/promotion tiếp tục đạt. MockMvc chỉ chứng minh boundary; real PostgreSQL/service proxy chứng minh TX, không phải E2E Payment/Ticket. Không chạy scheduler nền vào fixture hoặc seed DB119/business queues để thay test cô lập.

### T4 — Request Postman

**File:** `deploy/keycloak/postman/import.json`. Không có Java item mới. Phụ thuộc: T2 và hợp đồng của M1–M3 đã duyệt.

- Thêm một request `Booking - Cancel Pending Order` trong folder Booking ở cấp gốc đã có, dùng `{{bookingBaseUrl}}/api/v1/orders/{{orderId}}/cancel`, POST, JSON `{}`, Bearer `{{userAccessToken}}` và Content-Type application/json.
- Dùng biến orderId có sẵn; không hard-code ID mới/token/credential và không thêm Idempotency-Key. Mô tả cách chọn order của buyer còn hạn, response 200 CANCELLED, retry và các lỗi trạng thái.
- Chỉ thêm request và hướng dẫn; không tự gửi collection vào server hoặc đổi các request legacy ngoài task.

**Kết quả kiểm:** parse JSON thành công; request nằm đúng folder, auth/body/URL đúng. Chạy request live chỉ khi user yêu cầu và có fixture hợp lệ.

### T5 — Verify và ghi kết quả

**File/item:** toàn bộ item đã duyệt; `services/booking-service/TESTING.md`. Phụ thuộc: T1–T4. Các lệnh dưới là kế hoạch, chưa chạy ở pha này.

Từ root repo, dùng Java23 hiện hành và môi trường theo TESTING.md. Khi unit smoke cần root .env, chỉ inject vào child-process environment, không in credential hoặc ghi .env vào Git. Integration business dùng PostgreSQL/Rabbit Testcontainers theo suite hiện có.

```powershell
rtk mvn -f services/booking-service/pom.xml -B -ntp spotless:apply
rtk mvn -f services/booking-service/pom.xml -B -ntp "-Dtest=OrderControllerTests" test
rtk mvn -f services/booking-service/pom.xml -B -ntp -P integration "-Dit.test=BookingPersistenceIT" verify
rtk git diff --check
```

Lệnh verify chạy cả unit suite và BookingPersistenceIT, kèm các check Spotless/SpotBugs/JaCoCo được cấu hình trong POM. Không chạy full verify thêm lần nữa khi đã đạt và không có thay đổi mới. Nếu lỗi môi trường/fixture, sửa đúng nguyên nhân trong phạm vi duyệt; không hạ guard production để ép test qua. Nếu cần class/method/kiểu viết ngoài danh sách thì trình delta trước khi viết.

Parse Postman bằng JSON parser sẵn có và đọc lại đúng request mới; rà diff chỉ gồm các file đã nêu. Cập nhật TESTING.md bằng số liệu/lệnh/phạm vi và giới hạn của lần chạy thực tế, không gán kết quả task expiry cho task cancel. Đồng bộ trạng thái spec/plan chỉ theo xác nhận thật; không tự commit/push/deploy hoặc đổi trạng thái ClickUp ở pha plan.

### T6 — Thêm 10 case HTTP bằng curl theo yêu cầu thực thi

User bổ sung khi gọi `$shopapp-java-executing-plans`: “Xong rồi thì test thêm bằng curl 10 test case”. Đây là kiểm chứng runtime bổ sung, không thêm Java declaration ngoài C/M/U/D đã liệt kê. Phụ thuộc T1–T5 và gate EXECUTING-PLANS trước khi viết Java.

Chạy bản Booking vừa build với fixture cô lập, xác thực JWT qua cấu hình security thực, request gửi bằng curl tới HTTP server thật. Không dùng MockMvc/mock service làm bằng chứng cho 10 case này. Chuẩn bị fixture bằng luồng nghiệp vụ hiện có và kiểm DB sau request; không deploy/push như điều kiện để test. Trước khi dùng tài nguyên test, xác minh rõ địa chỉ DB/broker và không trộn với business queues/đơn người dùng thật.

| Case | Request / fixture | Kết quả mong đợi |
|---|---|---|
| H1 | Hủy pending QUANTITY còn hạn | 200 CANCELLED; held quantity/limit được trả |
| H2 | Hủy pending STANDING còn hạn | 200 CANCELLED; pool sector được trả đúng số lượng |
| H3 | Hủy pending SEATED còn hạn | 200 CANCELLED; seat AVAILABLE, không còn holder cũ |
| H4 | Hủy pending đã áp voucher và freeze tiền | 200 CANCELLED; usage RELEASED, promotion held giảm; tiền/snapshot giữ nguyên |
| H5 | Hủy lặp một order vừa CANCELLED | 200 CANCELLED; mọi counter/updatedAt không đổi |
| H6 | Buyer khác hủy order không thuộc mình | 404 ORDER_NOT_FOUND; không mutation |
| H7 | Hủy không có JWT | 401; không mutation |
| H8 | JWT hợp lệ nhưng không có role BUYER | 403; không mutation |
| H9 | Hủy order đã đến hạn | 409 ORDER_EXPIRED; không tự chuyển CANCELLED |
| H10 | Hủy order đã nhận thanh toán ISSUING | 409 ORDER_NOT_CANCELLABLE; giữ COMMITTED/purchased |

Lưu báo cáo status/code và DB assertion của từng case, che JWT/credential. Nếu runtime không cho kiểm chứng phải ghi đúng giới hạn, không đánh dấu pass theo kết quả unit/integration. Sau chạy, dọn đúng fixture/process/container do task tạo; không truncate DB119 hoặc dừng service không thuộc task.

## 4. Đối chiếu source và convention hiện hành

Đã đọc RTK.md, common.md, projects/INDEX.md và projects/flash-ticket-platform.md theo Git common directory, cùng hai reference của skill. Đã đọc live mẫu Shop App OrderController.deleteOrder, IOrderService và OrderService.deleteOrder; giữ controller/service/repository/setter/save, không dùng active=false thay CANCELLED và không bắt mọi Exception rồi trả e.getMessage().

| Bằng chứng | Quyết định áp dụng |
|---|---|
| OrderServiceImpl.expireOrder:79–169; preparePurchaseSnapshot:993–1011 | Giữ thứ tự snapshot → owned order → buyer limit → inventory → promotion; thêm M3 vì buyer cancel có ownership/replay/deadline/response riêng |
| OrderRepository:36–49 | Query ownership, WRITE lock, DB clock và timeout đã đủ; không thêm query hoặc EntityManager |
| update*ForPaymentDecision:817–959 | Có RELEASED, voucher dựa usage thực; dùng nguyên trạng cho ba luồng Payment/expiry/cancel. Validation/cập nhật từng tài nguyên ở đây, không sao chép vào helper khác |
| Spec §4 đối chiếu createOrder/reserve*/addReservationAllocation cùng transaction; source baseline không có thay đổi Java khi chuyển pha | Không thêm validateReservationAllocations/check tổng ban đầu; vẫn kiểm trạng thái/deadline sau khóa vì Payment/expiry có thể commit lúc chờ. Không xóa check cũ ngoài task |
| OrderMapper.toView; controller/security hiện có | Dùng mapper/OrderView, constructor injection và role BUYER sẵn có; không thêm DTO/mapper/security layer |
| BookingPersistenceIT serializesConcurrentExpiryForTheSameOrder, arbitratesExpiryAgainstAcceptPaymentAfterLockWait, rollsBackExpiryWhenCommitFailsAndRetriesSafely | Dùng test proxy/locks/fault injection cùng kiểu, chỉ thêm D1–D8. Test SQL/function dùng để kiểm rollback, không tạo validation production |
| Common yêu cầu báo khai báo mới và tránh abstraction thừa | M1–M3 là NEW; C4 sửa enum; R1–R6 REUSE; U/D là NEW trong test class cũ. Không tách/sửa helper; không phát sinh Java declaration ngoài spec |

Nguồn Shop App khác phiên bản runtime (Java17/Boot3.2.3), chỉ lấy phong cách; không hạ Java23/Boot của Booking. Generation seat giữ như source hiện tại: helper kiểm state/currentReservationId và generation > 0; không tuyên bố đã so với expected generation trên allocation, không thêm schema để mở rộng cơ chế.

## 5. Gate và bàn giao

- Baseline spec được user chọn để lập plan; bản plan được user chọn bằng “triển khai theo plan này”. Gate EXECUTING-PLANS được hỏi riêng đúng danh sách và user trả lời **“xác nhận”** ngày 30/10/2026. Không dùng câu hỏi giải thích Map làm nguồn duyệt.
- T1–T6 **COMPLETE**. Chỉ thêm M1–M3 và U1–U3/D1–D8, sửa enum C4; R1–R6, Payment và expiry nguyên trạng. Postman đã thêm request trong collection cục bộ hiện bị ignore; không force-add collection vào Git.
- Full verify lúc **14:21:37 +07, 30/10/2026**: **375 unit + 115 integration tests pass**, 0 failure/error/skip, BUILD SUCCESS; format/static/coverage checks đạt. Sau đó bổ sung assertion voucher rollback trong D8 và chạy riêng method này lúc **14:25:51 +07**: 1/1 pass; production không đổi, không gọi focused run là full suite.
- H1–H10 **10/10 PASS** bằng curl trên production JAR với PostgreSQL/Rabbit/Keycloak test riêng; mỗi case kiểm DB sau request. Chi tiết, cấu hình test, lịch sử sửa fixture/harness và giới hạn tại [TESTING.md](../../../services/booking-service/TESTING.md). Đã dọn containers/fixture credential và dừng HTTP child process của task.
- Đính chính diễn đạt ở T3: cancel thắng → PaymentDecision.CANCELLED theo code Payment đã có; không sửa Payment sang LATE. Đây là sửa kỳ vọng test/tài liệu, không thay nghiệp vụ production hoặc thêm method.
- User đã gọi `shopapp-java-finishing-task` để bàn giao: commit/push dev/son, theo dõi CI/deploy rồi cập nhật ClickUp. SHA/run và trạng thái bàn giao được ghi ở comment của task hiện tại theo kết quả thực tế; gate class/method tự nó không cấp quyền các thao tác này.
