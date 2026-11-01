# Booking — Worker tự hết hạn order và trả tài nguyên

- Phiên bản: **v0.4 — APPROVED**. Pha: **BRAINSTORMING**. Kế thừa v0.2 đã duyệt; user xác nhận bỏ M6 bằng “nếu v thì bỏ đi”.
- Task: [Worker tự hết hạn order và trả tài nguyên](https://app.clickup.com/t/z8qgvc9hdz), đã đọc trực tiếp qua ClickUp ngày 30/10/2026. Deadline user chốt: **30/10/2026, 23:00, Asia/Saigon**.
- Nguồn yêu cầu: user gọi `$shopapp-java-brainstorming`, “lập spec cho task đầu tiên”. User xác nhận thiết kế bằng yêu cầu “viết plan theo spec đã duyệt này” khi gọi `$shopapp-java-writing-plans` ngày 30/10/2026. Gate của pha lập plan vẫn độc lập.
- Checkout: `D:/DATN/flash-ticket-platform`; Git common directory: `D:/DATN/flash-ticket-platform/.git`; HEAD khi khảo sát: `f23cc90b2fc1ee6e0834b9ee115e52d1b37769db`. Working tree sạch trước khi thêm spec.
- Đã triển khai theo [plan v0.3](../plans/2026-10-30-booking-order-expiry-plan.md) sau gate EXECUTING-PLANS được user xác nhận. Final verify 30/10/2026 10:59 +07: 372 unit + 106 integration tests pass. Cấu hình runtime đã sửa trong source; chưa push/deploy hoặc đổi trạng thái ClickUp. Lịch sử khảo sát dưới đây mô tả code trước khi triển khai.

## 1. Mục tiêu và phạm vi

Tự thu hồi phần tài nguyên đang giữ của đơn `PENDING_PAYMENT` đã đến hạn, kể cả buyer chưa từng bắt đầu thanh toán hoặc không có callback Payment. Order, reservation, allocations, inventory, purchase limit và promotion usage phải commit hoặc rollback cùng nhau.

Điều kiện hết hạn là **`decisionAt >= order.expiresAt`**, dùng giờ DB đọc sau các khóa cần phân xử. Tần suất worker chỉ ảnh hưởng độ trễ dọn tài nguyên; không thay deadline và không gia hạn đơn.

Task bao gồm scheduler, truy vấn ứng viên, transaction hết hạn từng order, index phục vụ quét và kiểm thử cạnh tranh/rollback. Buyer cancel API, consumer hủy event/fan-out, refund, Payment/Ticket và kiểm thử toàn luồng liên service thuộc task khác. Worker không phát `PaymentDecision`, `OrderCompleted` hoặc sự kiện hết hạn mới; khoản thu tới muộn vẫn do AcceptPayment hiện có phân xử.

### Hiện trạng đã đọc

| Nguồn hiện tại | FACT và hướng tái sử dụng |
|---|---|
| `OrderServiceImpl.acceptPayment/settlePendingPayment` | Đã release khi Payment tới muộn, nhưng yêu cầu frozen PurchaseSnapshot. Không gọi giả AcceptPayment để expire đơn chưa freeze. |
| `updateQuantityInventoryForPaymentDecision`, `updateStandingInventoryForPaymentDecision`, `updateSeatedInventoryForPaymentDecision`, `updatePurchaseLimitForPaymentDecision` | Nhận `ReservationState` và đã hỗ trợ `RELEASED`; tái sử dụng nguyên chữ ký với target RELEASED. |
| `updatePromotionUsageForPaymentDecision` | Hiện đọc promotion từ frozen snapshot. Sửa thân helper để dựa vào PromotionUsage và PromotionInventory, giữ chữ ký, dùng chung cho Payment và expiry (§5, R7). Kiểm tra voucher khớp frozen snapshot nằm riêng trong settlePendingPayment. |
| `OrderRepository` | Có scalar event ID, query khóa order, DB clock và transaction lock timeout; chưa có query quét đơn đến hạn. |
| `BookingOutboxWorker`, `BookingServiceApplication` | Đã có mẫu worker gọi service qua proxy và `@EnableScheduling`; dùng lại cách tổ chức, không sửa trách nhiệm outbox worker. |
| `ReservationServiceImpl` | Hiện chỉ có dependency repository; chưa có nghiệp vụ expiry. Không tạo thêm service/cặp interface–implementation cho task này. |
| `Order` | Có state, expiry, accepted charge/time, `@Version`; `@Table` chưa khai index phục vụ expiry. |

Nguồn nghiệp vụ: B14-v0.3 §1/§3/§4 và B12-v0.2 phần Booking/expiry index. B14-v0.3 hiện `REVIEW_READY`, không nhận phê duyệt của B14-v0.1 làm phê duyệt toàn bản mới. Phạm vi task và deadline do user xác nhận trong chat/ClickUp; thiết kế v0.2 được user xác nhận khi yêu cầu viết plan ngày 30/10/2026.

## 2. Luồng worker và truy vấn ứng viên

```text
OrderExpiryWorker.expireDueOrders() — không có transaction bao cả batch
  → đọc cutoff bằng OrderRepository.currentDatabaseTime()
  → lấy tối đa batch-size UUID ứng viên, không nạp Order entity
  → với từng ID: OrderService.expireOrder(id) qua Spring proxy
      → transaction riêng → khóa → kiểm lại → release → commit
  → log lỗi từng ID, tiếp tục ID sau; lần quét sau thử lại dữ liệu còn pending
```

Đề xuất cấu hình tại `infrastructure/config-server/src/main/resources/config/booking-service.yaml`:

| Property | Giá trị đề xuất | Ý nghĩa |
|---|---|---|
| `booking.expiry.enabled` | `true` | Có thể tắt bean bằng false; annotation mặc định `matchIfMissing=false`. |
| `booking.expiry.poll-interval` | `PT5S` | Fixed delay sau khi một lượt chạy kết thúc. |
| `booking.expiry.batch-size` | `20` | Tối đa số ID xử lý trong một lượt; phải >0. |
| `booking.reservation.lock-timeout` | Giữ `5s` | Tái dùng timeout chờ khóa, chỉ trong transaction hiện tại. |
| `spring.task.scheduling.pool.size` | `2` | Dùng scheduler auto-config của Boot, để expiry chờ khóa không chiếm thread duy nhất của outbox. Không tạo scheduler bean riêng. |

Worker dùng `@Scheduled(fixedDelayString="${booking.expiry.poll-interval:PT5S}")`; không `@Async`, không tự tạo thread pool/Quartz/Redis lock. `batch-size` inject bằng `@Value` trên field; kiểm >0 ngay đầu method trước query, sai cấu hình ném `IllegalArgumentException` với thông điệp cố định. Fixed-delay property được Spring kiểm khi đăng ký lịch. Pool2 áp dụng scheduler platform-thread auto-config hiện hành; không bật virtual threads trong task này. Một method fixed-delay vẫn chạy tuần tự trên một bean; hai job expiry/outbox có thể chạy đồng thời. Không bảo đảm độ trễ nếu cả hai thread hoặc DB pool đang bận.

### Quét có giới hạn và không kẹt vì đơn lỗi

- Query JPQL trả `List<UUID>` với `lifecycleState=:state`, `expiresAt<=:cutoff`, và `(:afterOrderId is null or id>:afterOrderId)`, `order by id asc`. Worker luôn truyền `PENDING_PAYMENT` và `PageRequest.of(0,batchSize)`; không dùng count query hoặc OFFSET tăng dần trên tập đang bị cập nhật.
- Worker giữ `UUID lastOrderId` trong bean. Sau mỗi ID đã thử, kể cả transaction lỗi, tiến cursor tới ID đó. Gặp cuối tập (batch rỗng hoặc ngắn hơn batch-size) thì đặt null để vòng sau quét lại từ đầu. Không chọn lại cùng một nhóm lỗi ở đầu tập trong mọi tick, bỏ đói các đơn phía sau.
- Cursor chỉ điều phối lượt quét; **DB state là nguồn công việc**. Restart/mất cursor làm quét lại từ đầu, không mất đơn và không phải dấu hoàn tất bền. Đơn mới đến hạn có ID trước cursor được gặp khi vòng quét quay lại.
- UUID được so/sắp theo cùng thứ tự tại DB, không dùng `UUID.compareTo` để dựng thứ tự khác. Query không khóa order; service bắt buộc kiểm lại. Lỗi query/cutoff không đổi cursor, tick sau thử lại. Nếu thread đã interrupted thì dừng giữa các ID và giữ cursor ở ID đã thử cuối cùng.
- Mỗi instance có cursor riêng; nhiều worker có thể chọn cùng ID. Khóa order và state guard phân xử tác động. Không tuyên bố phân công exactly-once hoặc bảo đảm độ trễ khi backlog tăng liên tục.

Đề xuất bổ sung `@Index(name="tbl_orders_expiry_idx", columnList="lifecycle_state,expires_at,id")` trong `Order.@Table`. Index giúp lọc state/hạn; vì query sắp ID, không khẳng định index loại bỏ mọi sort. Kiểm schema thực tế và kế hoạch query khi nghiệm thu, chưa có số đo tải. Giữ `ddl-auto:update` đã được user chọn; không thêm migration framework/file migration.

## 3. Transaction cho một order

`OrderServiceImpl.expireOrder(UUID orderId)` dùng `@Transactional` mặc định REQUIRED. Worker không có ambient transaction, nên mỗi lần gọi qua proxy tạo một transaction độc lập và kết thúc commit trước khi chuyển ID tiếp theo. Không self-invocation; không cần REQUIRES_NEW hoặc TransactionTemplate mới.

1. `orderId=null` là `INVALID_REQUEST`. Cấu hình transaction lock timeout. Đọc scalar eventId; ứng viên đã không còn tồn tại thì return. Chưa nạp Order entity vào persistence context trước khi chờ khóa.
2. Khóa event snapshot bằng `findForBookingByEventId` (`PESSIMISTIC_READ`), rồi khóa order bằng `findLockedById` (`PESSIMISTIC_WRITE`). Thiếu snapshot là `CONFIG_NOT_READY`; order biến mất sau bước chọn thì return.
3. Đọc lại state sau khóa. Nếu không còn `PENDING_PAYMENT`, return không ghi gì. Nếu vẫn pending mà có `acceptedChargeId` hoặc `acceptedAt`, báo `RESERVATION_DATA_CONFLICT`, không coi đó là đơn chưa thanh toán để release.
4. Đọc reservation/allocations sau order lock. Bắt buộc reservation còn HELD. Không thêm helper kiểm tra lại quan hệ order–allocation hoặc tổng quantity theo item; các guard hiện có khi release tài nguyên được giữ tại R2–R5 theo §4. Không yêu cầu `paymentFrozenAt`, snapshot hoặc version thanh toán phải có.
5. Khóa purchase limit của event/buyer → inventory thực sự được chọn theo query ID tăng dần → promotion inventory nếu có usage. Đọc usage theo reservation, kiểm trạng thái và chủ thể trước khi sửa. Giữ cùng thứ tự với createOrder/applyPromotion/AcceptPayment; reservation/allocations được bảo vệ bởi order lock như implementation hiện tại, không thêm khóa ngược riêng.
6. Đọc lại DB clock **sau tất cả các bước có thể chờ khóa tài nguyên**. Nếu `decisionAt < expiresAt` thì return. Bằng expiresAt là đến hạn. Không dùng JVM clock, thời điểm bắt đầu transaction hoặc cutoff của bước quét để thay quyết định này.
7. Release HELD theo §4. Chuyển reservation và allocations sang RELEASED, order sang EXPIRED, updatedAt=decisionAt; save bằng repository hiện có, dirty checking cho allocations đang managed. Giữ nguyên giá, discount/total, email, expiry và frozen snapshot để tra lịch sử.
8. Nếu snapshot event đã CANCELLED thì order chuyển **CANCELLED** thay EXPIRED, vẫn trả phần HELD một lần, giống thứ tự ưu tiên trong AcceptPayment hiện có. Không kiểm sale window để trì hoãn dọn tài nguyên. Worker chỉ đụng các đơn đã đến hạn; fan-out mọi đơn của event hủy vẫn là task khác. Counter/seat được trả theo helper hiện có; đặt mới vẫn bị snapshot CANCELLED chặn.
9. Commit toàn bộ; lỗi save/flush/commit đi ra ngoài service proxy. Worker chỉ bắt lỗi **sau rollback**, ghi orderId + `ErrorCode.from(exception)` theo logging hiện có rồi tiếp tục đơn khác; không coi lỗi là một đơn đã xử lý thành công.

### Bảng phân xử

| Trạng thái sau khóa | Tác động |
|---|---|
| Pending, chưa nhận charge, HELD, chưa đến hạn | Không thay đổi. |
| Pending, chưa nhận charge, HELD, đến hạn | Release; EXPIRED, hoặc CANCELLED nếu đã có marker hủy event. |
| ISSUING / COMPLETED / ISSUANCE_FAILED | Bỏ qua; không trả purchased hoặc USED promotion. |
| EXPIRED / CANCELLED | Bỏ qua; không trả lại lần hai. Tồn dư tài nguyên do dữ liệu lỗi không tự sửa ở đây. |
| Pending nhưng reservation RELEASED/COMMITTED, accepted metadata sai, counter/liên kết sai | Rollback `RESERVATION_DATA_CONFLICT`; log, giữ để kiểm tra/thử lại. |
| DB/lock timeout/commit lỗi | Rollback; không receipt hay watermark thành công; vòng quét sau còn thấy order pending. |

Hai worker cùng ID: bên sau đọc state mới sau order lock và bỏ qua. Nếu AcceptPayment đã commit ACCEPTED trước, worker bỏ qua ISSUING dù giữ chỗ đã qua hạn. Nếu worker commit expiry trước, AcceptPayment tới sau phải trả LATE (hoặc CANCELLED theo marker), không trừ lại hoặc hồi sinh. Nếu Payment giữ khóa trước nhưng chờ tới quá hạn, Payment cũng dùng DB time sau khóa để tự đi nhánh LATE. Không giả định bên bắt đầu transaction trước là bên thắng.

## 4. Validation và phần tài nguyên được trả

Không tạo `validateReservationAllocations` (M6 đã bỏ). Giữ nguyên các kiểm tra allocation hiện có trong `settlePendingPayment`, không trích sang helper hoặc chép sang expiry. Expiry dựa trên dữ liệu do luồng tạo đơn đã ghi cùng transaction và dùng các guard release sẵn có ở R2–R5; vẫn kiểm trạng thái/deadline sau khóa. Kiểm frozen snapshot chỉ nằm trong luồng Payment.

### Rà soát phạm vi validation sau phản hồi user

Đối chiếu source `OrderServiceImpl.java`: createOrder tại dòng 1043 tạo order/reservation, gọi reserve* và cập nhật limit trong cùng transaction; QUANTITY tại 1301 và STANDING tại 1325 lấy quantity từ OrderItem để tạo allocation; SEATED tại 1351 tạo một allocation quantity=1 cho từng ghế; addReservationAllocation tại 1395 gán các liên kết từ order/reservation/item hiện có. Vì vậy không coi việc tạo lệch order/allocation là một case nghiệp vụ còn thiếu của task trước.

| Nhóm kiểm tra | Nguồn bảo đảm/kiểm tra hiện có | Quyết định cho expiry |
|---|---|---|
| Tạo liên kết và số lượng ban đầu | createOrder/reserve*/addReservationAllocation cùng transaction | Không validate lại request tạo đơn, giá bán, cấu hình selection hoặc dựng thêm bộ kiểm tra cấu trúc mới. |
| Allocation khớp order/item và reservation | settlePendingPayment dòng 556–610 đã có các kiểm tra; các liên kết/quantity ban đầu được luồng tạo đơn ghi cùng transaction | Giữ nguyên kiểm tra trong Payment; không thêm helper chung hoặc bộ kiểm tra tương đương cho expiry. |
| Order/reservation đã đổi trạng thái, deadline đã qua | Phải đọc hiện trạng sau khóa, không suy từ kết quả tạo đơn trước đó | expireOrder kiểm pending, accepted metadata, reservation HELD và DB time sau khóa. Guard này cần cho cạnh tranh Payment/expiry. |
| Tài nguyên hiện tại thuộc ai, counter đủ, kind/type/sector/seat/selection | update*InventoryForPaymentDecision dòng 718–808 và updatePurchaseLimitForPaymentDecision dòng 811–828 | Dùng lại R2–R5; không chép các kiểm tra này vào helper khác. Giữ seat currentReservationId để không trả ghế đã được reservation mới giữ. |
| Dữ liệu đã chốt cho thanh toán | Phần frozen snapshot trong settlePendingPayment | Giữ ở Payment; expiry không áp kiểm snapshot/giá. |

Delta v0.4: user yêu cầu “nếu v thì bỏ đi”, xác nhận bỏ hẳn M6 và việc gọi/tách helper này. Không xóa kiểm tra allocation của Payment; expiry giữ các guard release hiện có. Không coi sửa DB thủ công hoặc giả định bug là case nghiệp vụ còn thiếu của task trước. Chưa sửa Java.

Promotion dùng chung helper hiện có `updatePromotionUsageForPaymentDecision` (R7), không thêm `releaseHeldPromotion`. Caller lấy usage theo reservation đang xử lý và khóa promotion inventory tương ứng. Helper kiểm liên kết usage/promotion inventory và event/buyer/currency/expiry/discount của order, generation và trạng thái HELD cùng counter đủ trước khi cập nhật; không đọc frozen snapshot. Không có usage chỉ hợp lệ khi discount=0. Với target COMMITTED, giảm held, tăng used và chuyển usage sang USED; với target RELEASED, giảm held, giữ used và chuyển usage sang RELEASED. Các lỗi dữ liệu gây rollback như hiện tại.

Riêng `settlePendingPayment` vẫn kiểm sự hiện diện của usage khớp promotion trong frozen snapshot và promotion ID trong snapshot khớp promotion của usage/inventory trước khi gọi R7. Việc chuyển kiểm tra snapshot từ helper về caller không làm mất ràng buộc thanh toán. `expireOrder` gọi R7 với target RELEASED, không yêu cầu frozen snapshot. User đã đồng ý hướng thay đổi promotion này trong chat ngày 30/10/2026; đây là duyệt riêng lựa chọn tái sử dụng helper, chưa phải duyệt toàn bộ spec hoặc triển khai Java.

| Tài nguyên | Tác động khi expire |
|---|---|
| QUANTITY | Cộng số lượng allocation theo ticket type; `held-=quantity`, giữ purchased. Không dùng counter tổng để suy ra quyền giữ của order. |
| STANDING | Cộng allocation của sector; trừ held của pool sector một lần; không trừ thêm pool ticket type. |
| SEATED | Ghế phải HELD, currentReservationId đúng reservation, event/sector/type/selection khớp và generation>0. Đưa AVAILABLE hoặc UNAVAILABLE nếu hidden, xóa currentReservationId, giữ generation. Không sửa ghế của reservation R2. Schema allocation hiện không có generation để so; không khẳng định đã đối chiếu một generation không lưu. |
| PurchaseLimit | `held-=tổng vé`, giữ purchased; đúng event/buyer, counter đủ. |
| Promotion | Usage của đúng reservation còn HELD, event/buyer/currency/expiry/discount khớp order, generation>0, promotion ID và event khớp inventory; `promotion.held-=1`, usage RELEASED, giữ used. Discount=0 phải không có active usage; discount>0 phải có HELD usage. USED/RELEASED usage gắn pending order là conflict, không trả lần nữa. |
| Reservation/allocations | HELD→RELEASED cùng transaction với các counter và order. |

Không clamp counter về 0 để che dữ liệu sai. Không delete usage/history hoặc reset generation. Promotion hết thời gian hiệu lực/sale đóng không cấm trả phần HELD. Truy vấn usage theo reservation bảo vệ việc release cũ không đụng lượt áp mã khác của cùng buyer.

## 5. Bảng duyệt class và method — APPROVED; M6/M7 đã bỏ

Alias đường dẫn để đọc bảng: `main/` = `services/booking-service/src/main/java/com/flashticket/booking_service/`; `test/` = `services/booking-service/src/test/java/com/flashticket/booking_service/`.

| ID | Class/file | NEW / EXISTING | Vai trò, field/annotation/API sinh | Mẫu hoặc ngoại lệ |
|---|---|---|---|---|
| C1 | `main/order/OrderExpiryWorker.java` — `OrderExpiryWorker` | NEW | `@Component`, `@RequiredArgsConstructor`, `@ConditionalOnProperty(prefix="booking.expiry",name="enabled",havingValue="true",matchIfMissing=false)`; final `OrderRepository orderRepository`, `OrderService orderService`; `@Value("${booking.expiry.batch-size:20}") int batchSize`; `UUID lastOrderId`. Lombok chỉ sinh constructor M1; không getter/setter/builder/equals/hashCode/toString. | Constructor/field @Value theo Shop App; scheduler/cursor theo nhu cầu ngoài mẫu, dùng cách tổ chức BookingOutboxWorker. |
| C2 | `main/order/service/OrderService.java` | EXISTING | Thêm M3; không interface mới. | Shop App IOrderService; giữ tên/package đích. |
| C3 | `main/order/service/impl/OrderServiceImpl.java` | EXISTING | Thêm M4; giữ kiểm allocation của settlePendingPayment tại chỗ; chuyển kiểm promotion frozen về R6 và sửa helper R7 để Payment/expiry dùng chung. Giữ constructor, dependency và annotation hiện có. | Shop App OrderService.updateOrder: tìm/kiểm/setter/save. Khóa/transaction là ngoại lệ cần nghiệp vụ. |
| C4 | `main/order/repository/OrderRepository.java` | EXISTING | Thêm query M5; không storage wrapper/native query mới. | Spring Data JPQL + Pageable, cùng repo đích. |
| C5 | `main/entity/Order.java` | EXISTING | Chỉ bổ sung @Index vào @Table; không field/API Lombok mới. | Entity JPA theo Shop App; composite index là bổ sung của task. |
| C6 | `test/order/OrderExpiryWorkerTests.java` | NEW | Package-private class, `@ExtendWith(MockitoExtension.class)`; mocks OrderRepository/OrderService và `@InjectMocks OrderExpiryWorker`; dùng ReflectionTestUtils cho field cấu hình, không helper/setup/constructor explicit mới. | Test theo JUnit/Mockito hiện có, ngoài mẫu nghiệp vụ Shop App. |
| C7 | `test/persistence/BookingPersistenceIT.java` | EXISTING | Thêm D1–D12; reuse fixture/container/TransactionTemplate và constructor hiện có. Đổi field `@Autowired OrderRepository orders` thành `@MockitoSpyBean OrderRepository orders` để kiểm biên clock ở D8; chỉ stub clock trong ca biên, các query/khóa còn lại chạy thật. | Kiểm PostgreSQL/transaction thật theo suite hiện tại; test spy là ngoại lệ trình duyệt cùng danh sách. |
| C8 | `test/config/RuntimeConfigurationTests.java` | EXISTING | Thêm U7 kiểm bật/tắt bean và U8 kiểm scheduler auto-config; dùng configuredRunner có sẵn, không @Bean hoặc configuration class mới. | ApplicationContextRunner đã có. |

### Production API và helper

| ID | Class | Constructor/method đầy đủ | NEW / REUSE | Cách viết và lý do |
|---|---|---|---|---|
| M1 | C1 | `public OrderExpiryWorker(OrderRepository orderRepository, OrderService orderService)` | NEW, Lombok | Constructor injection, chỉ hai dependency final. |
| M2 | C1 | `public void expireDueOrders()` | NEW | @Scheduled như §2; không @Transactional/@Async; batch/cursor/catch từng ID sau service return. |
| M3 | C2 | `public abstract void expireOrder(UUID orderId)` | NEW | Entry nghiệp vụ cho worker qua proxy; không trả response DTO. |
| M4 | C3 | `public void expireOrder(UUID orderId)` | NEW | @Override, @Transactional; luồng §3, không declared checked throws. |
| M5 | C4 | `public abstract List<UUID> findExpiredOrderIds(OrderState state, Instant cutoff, UUID afterOrderId, Pageable pageable)` | NEW | @Query JPQL §2; @Param("state"), @Param("cutoff"), @Param("afterOrderId"); Pageable là special parameter; không @Lock/count. |
| R1 | C4 | `public abstract Optional<UUID> findEventIdById(UUID orderId)`; `public abstract Optional<Order> findLockedById(UUID id)`; `public abstract Instant currentDatabaseTime()`; `public abstract String configureTransactionLockTimeout(String timeout)` | REUSE | Không đổi chữ ký/annotation, giữ native clock/timeout đã có. |
| R2 | C3 | `private void updateQuantityInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<TicketTypeInventory> lockedInventories, ReservationState targetState)` | REUSE | Truyền RELEASED; giữ tên hiện tại để không refactor lan rộng. |
| R3 | C3 | `private void updateStandingInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, SectorInventory lockedSector, ReservationState targetState)` | REUSE | Truyền RELEASED. |
| R4 | C3 | `private void updateSeatedInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<SeatInventory> lockedSeats, ReservationState targetState)` | REUSE | Truyền RELEASED; giữ guard reservation/generation. |
| R5 | C3 | `private void updatePurchaseLimitForPaymentDecision(Order lockedOrder, Reservation storedReservation, PurchaseLimit lockedPurchaseLimit, ReservationState targetState)` | REUSE | Truyền RELEASED. |
| R6 | C3 | `private PaymentDecision settlePendingPayment(Order lockedOrder, Reservation storedReservation, EventSalesSnapshot storedSnapshot, AcceptPayment requestedPayment)` | EXISTING, sửa thân | Giữ kiểm allocation tại chỗ, kiểm snapshot, phân xử charge/deadline và inbox/outbox. Nhận phần kiểm promotion ID khớp frozen snapshot từ helper R7 trước khi gọi helper chung. |
| R7 | C3 | `private void updatePromotionUsageForPaymentDecision(Order lockedOrder, PromotionUsage storedUsage, PromotionInventory lockedPromotion, ReservationState targetState)` | EXISTING, sửa thân | Giữ chữ ký; kiểm dữ liệu qua usage/inventory theo §4, không đọc frozen snapshot. Payment dùng COMMITTED hoặc RELEASED; expiry dùng RELEASED. Hướng thay đổi promotion đã được user đồng ý; không thêm helper release riêng. |

Query khóa snapshot/limit/inventory/promotion, lookup reservation/usage và save/saveAll tái dùng từ luồng AcceptPayment, không thêm method repository khác. Không dependency, DTO, mapper, enum, error code, @Bean, lifecycle callback hoặc API codegen mới ngoài bảng. Checked exceptions không có trong production API đề xuất.

### Test API và tiêu chí nghiệm thu

Mọi method dưới đây là NEW, package-private, non-static, annotation `@Test`. U1–U6 thuộc C6, U7–U8 thuộc C8; D1–D12 thuộc C7 và thêm `@Transactional(propagation=Propagation.NOT_SUPPORTED)` để lời gọi service commit qua proxy thật. Class C6 có constructor mặc định package-private do Java cấp; không viết constructor/setup/helper mới. Mỗi D-method khai báo `throws Exception` để dùng fixture/concurrency hiện có; không thêm nested/anonymous type hoặc helper chưa liệt kê.

| ID | Chữ ký đầy đủ | Hành vi cần chứng minh |
|---|---|---|
| U1 | `void pollsExpiredOrderIdsUsingDatabaseTime()` | Dùng cutoff từ DB, state pending, batch giới hạn, chỉ chuyển UUID vào service. |
| U2 | `void continuesAfterOrderExpiryFailure()` | Service/commit lỗi một ID không bỏ các ID còn lại hoặc ghi nhận thành công giả. |
| U3 | `void advancesAndWrapsExpiryCursorAfterFailures()` | Batch lỗi đủ size không kẹt đầu hàng; cursor tiến và quay lại các ID lỗi khi hết vòng; tạo worker mới quét từ đầu. |
| U4 | `void preservesExpiryCursorWhenCandidateQueryFails()` | Lỗi DB khi query không làm bỏ qua ID; lần sau dùng lại cursor. |
| U5 | `void stopsExpiryBatchWhenThreadIsInterrupted()` | Dừng giữa ID, không tiến cursor qua phần chưa thử; dọn interrupted flag của test trong finally. |
| U6 | `void rejectsNonPositiveExpiryBatchSize()` | Không truy DB khi batch-size không hợp lệ. |
| U7 | `void enablesOrderExpiryWorkerOnlyWhenConfigured()` | Missing/false không tạo bean; true tạo bean với mock dependencies bằng API runner có sẵn; không kết nối DB/server. |
| U8 | `void configuresTwoSchedulerThreadsForBookingWorkers() throws Exception` | Boot auto-config thực tạo ThreadPoolTaskScheduler pool2 từ YAML; dùng task chặn có latch để chứng minh job khác vẫn chạy. Không kiểm chỉ bằng so chuỗi cấu hình, không tạo scheduler @Bean/helper mới. |
| D1 | `void selectsExpiredPendingOrderIdsWithBoundedCursor() throws Exception` | JPQL thực trên PostgreSQL: cutoff inclusive, UUID cursor/limit, không chọn terminal/chưa đến hạn. |
| D2 | `void expiresUnfrozenQuantityOrderAndReleasesHeldResources() throws Exception` | Đơn không snapshot, không callback vẫn EXPIRED; tất cả allocation/limit/type pool đúng, không inbox/outbox mới. |
| D3 | `void expiresFrozenOrderAndReleasesHeldPromotion() throws Exception` | Có frozen/promotion: trả HELD một lần qua R7, giữ used/purchased/tiền và frozen snapshot. Bao gồm promotion đã hết hiệu lực; helper không phụ thuộc frozen snapshot. |
| D4 | `void expiresStandingOrderWithoutChangingTicketTypePool() throws Exception` | Chỉ trả pool sector, tổng allocation đúng. |
| D5 | `void expiresSeatedOrderWithoutReleasingAnotherReservation() throws Exception` | HELD→AVAILABLE/hidden→UNAVAILABLE; replay sau R2 giữ ghế không thay R2; ownership lệch gây rollback. |
| D6 | `void skipsUnexpiredAndTerminalOrdersDuringExpiry() throws Exception` | Chưa đến hạn, ISSUING, COMPLETED, EXPIRED, CANCELLED, ISSUANCE_FAILED không đổi resource/updatedAt. |
| D7 | `void serializesConcurrentExpiryForTheSameOrder() throws Exception` | Hai transaction cùng ID chỉ trả một lần; không counter âm. |
| D8 | `void arbitratesExpiryAgainstAcceptPaymentAfterLockWait() throws Exception` | Khống chế hai thứ tự giữ khóa/commit; Payment thắng trước hạn → ISSUING, expiry thắng → LATE; chờ qua deadline không nhận ACCEPTED sai; gồm biên bằng hạn với clock có kiểm soát tại test. |
| D9 | `void rollsBackExpiryWhenReservationDataConflicts() throws Exception` | Guard còn giữ: accepted metadata, reservation không HELD, limit/inventory thiếu hoặc counter không đủ, seat ownership sai, usage USED/sai buyer/generation → rollback toàn bộ. Không thêm kỳ vọng expiry kiểm tổng allocation theo item. |
| D10 | `void rollsBackExpiryWhenCommitFailsAndRetriesSafely() throws Exception` | Gây lỗi constraint/commit thật bằng fixture hiện có; order/counter cùng rollback, lời gọi transaction mới có thể xử lý lại. |
| D11 | `void preservesEventCancellationDuringOrderExpiry() throws Exception` | Đơn đến hạn + snapshot CANCELLED → CANCELLED và release một lần, không gate theo sale window; không xử lý đơn còn hạn/fan-out cả event. |
| D12 | `void serializesExpiryWithPromotionAndSnapshotPreparation() throws Exception` | Áp mã/chuẩn bị snapshot tranh chấp expiry: toàn bộ tác động được commit theo thứ tự khóa, không usage HELD mồ côi hoặc freeze thành công sau expiry. |

Kiểm đúng bằng deadline ở D8: tạo fixture bằng DB clock thật trước; trong riêng lời gọi expire của ca biên, dùng `Mockito.doReturn(expiresAt).when(orders).currentDatabaseTime()` trên spy repository. Khóa và transaction vẫn chạy PostgreSQL thật; đây là kiểm điều kiện bằng hạn có clock kiểm soát, không phải đo chính xác thời gian thực của PostgreSQL. Những ca chờ khóa dùng clock thật và latch/transaction độc lập. Spy tự reset giữa test, không sửa clock production hoặc thêm Clock bean. Nếu phát sinh type/method test ngoài danh sách thì phải trình delta trước khi viết.

Các test hiện có của AcceptPayment phải tiếp tục chứng minh replay accepted-charge trước deadline, LATE/CANCELLED release, allocation/snapshot guards và rollback sau khi sửa R7. Phải giữ kiểm promotion ID trong frozen snapshot khớp usage/inventory, sự hiện diện voucher khớp snapshot, HELD→USED khi accepted và HELD→RELEASED khi late/cancelled; mismatch phải rollback. Test resources/smoke tắt expiry để tránh lịch chạy vào DB thật hoặc can thiệp fixture. Tái dùng suite PostgreSQL Testcontainers; không truncate DB119 hoặc tiêu thụ queue nghiệp vụ để test task local này.

## 6. Mẫu, giới hạn và điểm chờ duyệt

- Đã đọc trực tiếp Shop App `services/impl/OrderService.updateOrder`: constructor injection, find/check/setter/save; và `components/JwtTokenUtil`: `@Value` trên field. Giữ UUID/Instant/counter/locking và package của FlashTicket; không thêm ModelMapper chỉ vì mẫu có.
- [Spring scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): áp dụng fixed-delay cho worker, không dùng lịch chạy làm deadline nghiệp vụ.
- [Ví dụ mã nguồn mở Spring Guides](https://github.com/spring-guides/gs-scheduling-tasks/blob/main/src/main/java/com/example/schedulingtasks/ScheduledTasks.java): đã đọc ScheduledTasks và ví dụ EnableScheduling trong README; chỉ lấy cách đăng ký component/method lịch chạy, không lấy fixed-rate hoặc logic thời gian demo làm cơ chế expire.
- [Spring Boot scheduler](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html): tăng pool auto-config lên2 bằng property thay vì thêm scheduler bean, để hai worker không chia duy nhất một thread.
- [Spring MockitoSpyBean](https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-mockitobean.html): chỉ dùng spy clock tại test biên, giữ các query và AOP proxy thật; dùng doReturn để stub không gọi method thật lúc khai stubbing.
- [Spring transaction proxy](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html): worker gọi bean service khác để transaction thực sự bao mỗi order, rollback/commit lỗi không bị nuốt trong service.
- [Spring Data repository return types](https://docs.spring.io/spring-data/jpa/reference/repositories/query-return-types-reference.html): dùng List với Pageable để giới hạn kết quả; keyset ở đây là predicate JPQL tường minh, không tuyên bố @Query đang hỗ trợ Window scrolling tự động.
- [PostgreSQL clock](https://www.postgresql.org/docs/current/functions-datetime.html): tái dùng clock_timestamp cho thời điểm sau chờ khóa; now/current_timestamp là mốc bắt đầu transaction.

Các ngoại lệ so với Shop App thuộc danh sách thiết kế đã duyệt: @Scheduled/@ConditionalOnProperty, cursor quét có giới hạn, pool scheduler2, @Transactional và pessimistic locks hiện có, composite @Index, helper promotion không frozen, @MockitoSpyBean và các test concurrency/rollback. M6 đã bỏ theo yêu cầu user. Không có kết quả runtime mới từ việc viết spec.

**Hồ sơ duyệt:** BRAINSTORMING v0.4: **APPROVED** cho C1–C8, M1–M5, R1–R7, U1–U8/D1–D12 và lựa chọn cấu hình/state/query. Kế thừa xác nhận v0.2 khi user yêu cầu viết plan theo spec đã duyệt; delta bỏ M6 được user xác nhận bằng “nếu v thì bỏ đi”. M6/M7 đã bỏ; không thêm helper thay thế. Giữ kiểm tra allocation hiện có của Payment và guard release của inventory/limit. Gate toàn plan và pha triển khai vẫn độc lập.
