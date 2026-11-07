# Booking — Hủy event và release tài nguyên của order

- Phiên bản **v0.4 — IMPLEMENTED / VERIFIED (EXECUTING-PLANS)**; khởi tạo ở pha BRAINSTORMING. Delta 31/10: user đồng ý hướng partial index/query, sau đó gọi executing-plans và trả lời **“đồng ý”** ngay sau danh sách thực thi; nguồn này duyệt khai báo cho pha EXECUTING-PLANS, không tự cấp quyền DDL/server119.
- Task: [Booking xử lý thông báo hủy event](https://app.clickup.com/t/z8qgvc9he1), deadline đang ghi **01/11/2026 23:00 Asia/Saigon**; không đổi deadline trong lượt này.
- Checkout `D:/DATN/flash-ticket-platform`; Git common directory `D:/DATN/flash-ticket-platform/.git`; baseline `5526e79af9fcd0d3cb5e13a4a50986d4d9159bab`.
- Quyết định hiện hành của user: **“triển khai release như chuẩn đi, sửa lại spec + plan”**, thay phương án v0.2 chỉ ghi gate. Danh sách Java bên dưới đã được duyệt cho pha thực thi bằng xác nhận riêng nêu trên và triển khai đúng phạm vi.
- Plan/kết quả tương ứng: [event-cancellation-plan v0.2](../plans/2026-10-30-booking-event-cancellation-plan.md), [TESTING.md](../../../services/booking-service/TESTING.md). Không sửa docs nghiệp vụ gốc, ClickUp hoặc deploy/server119 trong task thực thi cục bộ này.

## 1. Kết quả và nguồn nghiệp vụ

Theo [B3 §4.4](../../domain/B3-business-processes.md#44-luồng-hủy-một-sự-kiện-và-xử-lý-nhiều-đơn), [B4 B13–B14](../../domain/B4-domain-event-map.md) và [B8 FR-15/FR-29/FR-38](../../domain/B8-requirements.md): đóng bán, hủy đơn/giữ chỗ còn hoạt động, trả limit/promotion đúng một lần, không đưa nguồn cung trở lại bán; Payment hoàn tiền, Ticket vô hiệu vé. B3-v0.11/B4-v0.15/B8-v0.13 APPROVED. [B14 §5](../../architecture/B14-main-flow-sequences.md#5-hủy-sự-kiện-cổng-cục-bộ-trước-fan-out-sau) v0.3 REVIEW_READY cụ thể hóa Booking xử lý PENDING_PAYMENT/ISSUING/COMPLETED; dùng phần này theo yêu cầu hiện tại, không đánh dấu toàn bộ B14 được duyệt.

Flow có hai transaction boundary:

1. `booking.event-cancelled.q → listener → AvailabilityService proxy → TX gate CANCELLED + inbox → commit → AUTO ACK`.
2. `EventCancellationWorker → query order còn việc trong DB → OrderService proxy → một TX/order: release + CANCELLED → commit`; lỗi một order không rollback order khác, worker tiếp tục và quét lại.

ACK chỉ xác nhận gate/inbox đã commit, không có nghĩa batch đã xong. Sau restart, gate và trạng thái order/reservation còn trong DB đủ để tìm lại công việc. Không tạo Saga hủy mới, bảng job/cursor/ledger hoặc bảng/cột mới; chỉ bổ sung partial index như §3.1. Payment/Ticket vẫn nhận event cancellation riêng; Booking không gửi FAILED giả, không gọi refund, không đợi refund để commit hủy.

## 2. Transaction ghi gate — giữ thiết kế v0.2

`AvailabilityServiceImpl.processEventCancellation(Envelope<EventCancellation>)`: @Override @Transactional, gọi qua proxy.

1. Validator kiểm Envelope/DTO; inline boundary messageType `event.cancelled.v1`, event IDs/source versions giữa envelope/data/sales/access phải khớp, sales/access CANCELLED và cùng organizer. Snapshot đã có phải cùng owner/mode. Không kiểm lại quyết định actor/deadline của Event; message hợp lệ tới sau giờ bắt đầu vẫn áp gate.
2. Hash SHA-256 toàn Envelope bằng JsonMapper ngay trong method, không thêm hash/verify helper. Khóa snapshot WRITE qua `findByEventId` rồi đọc inbox `(booking.event-cancelled,messageId)`.
3. Cùng ID/hash return; khác hash MESSAGE_ID_CONFLICT. MessageId mới cùng cancellationId được ghi receipt mới; gate đã CANCELLED không ghi snapshot lại.
4. Snapshot đã có: chỉ đổi eventStatus, giữ JSON thương mại/sourceConfigVersion/receivedAt/owner/mode/windows/limit và inventory. Snapshot thiếu: tạo từ full sales CANCELLED, receivedAt lúc nhận, rowVersion null, không khởi tạo inventory.
5. Inbox mới rowVersion null cùng TX. Race INSERT khi thiếu snapshot dựa PK/rollback và broker redelivery trong TX mới; không catch rồi dùng TX rollback-only. Config cũ không xóa gate; config mới muốn mở lại bị guard của synchronizeSalesConfiguration chặn. JSON/version thương mại cũ không bị giả thành phiên bản inventory mới.
6. Commit trước khi listener return. INVALID_MESSAGE/MESSAGE_ID_CONFLICT/SALES_CONFIGURATION_CONFLICT reject không requeue vào DLQ; DB/flush/commit lỗi đi ra container. Dùng AUTO rõ trên listener vì config trung tâm mặc định manual. Không thêm application retry hoặc ACK thủ công.

## 3. Worker và điều kiện tìm order

Một query JPQL trên OrderRepository, đọc scalar ID có Pageable, join Order/Reservation/EventSalesSnapshot theo quan hệ/ID hiện có, không load Order trước khi lấy khóa. Predicate cố định:

`snapshot.CANCELLED AND reservation IN [HELD,COMMITTED] AND order IN [PENDING_PAYMENT,ISSUING,COMPLETED,CANCELLED]`.

Đưa điều kiện reservation chưa release thành điều kiện chung để SQL có thể khớp partial index; giữ nhánh order CANCELLED phục hồi race. Các TX hiện có bảo đảm PENDING_PAYMENT đi với HELD, ISSUING/COMPLETED đi với COMMITTED; lọc này không bỏ công việc hợp lệ của predicate v0.3 và không thêm validation trong service. Reservation RELEASED không còn là ứng viên, kể cả event hủy từ lâu.

Nhánh CANCELLED nhưng reservation chưa RELEASED là bắt buộc: kết quả COMPLETED tới sau gate hiện có thể đổi ISSUING→CANCELLED mà chưa release COMMITTED. Chỉ lọc order chưa CANCELLED sẽ bỏ sót case thật này. Không quét thêm usage để dự phòng: các TX cũ cập nhật reservation/allocations/usage cùng nhau; không có luồng hợp lệ để reservation RELEASED mà usage vẫn cần release.

Sort orderId tăng dần, lọc `id > afterOrderId`, batch mặc định 20. Worker giống OrderExpiryWorker: cursor RAM chỉ phân trang; lỗi từng order được log orderId/ErrorCode sau rollback rồi tiến cursor; batch thiếu trang thì reset để vòng sau tìm lại cả ID lỗi/ID thấp. Query lỗi giữ cursor và để lỗi ra scheduler; interruption dừng batch. Restart bắt đầu từ null. Không lưu cờ completed hoặc dùng cursor RAM chứng minh đã xử lý hết; DB predicate mới là công việc bền còn lại.

`booking.event-cancellation.enabled=true`, `poll-interval=PT5S`, `batch-size=20` trong `infrastructure/config-server/src/main/resources/config/booking-service.yaml`; test YAML tắt worker mặc định. @ConditionalOnProperty matchIfMissing=false; @Scheduled fixedDelayString; @Value trên batchSize; không đổi scheduler pool 2 hoặc connection pool 5 khi chưa có bằng chứng cần tăng. Worker không có @Transactional bao cả batch.

### 3.1. Partial index và kiểm tra query plan

Hướng đã được user đồng ý: thêm `tbl_reservations_active_order_idx` trên `booking_schema.tbl_reservations(order_id)` với predicate `WHERE state IN ('HELD','COMMITTED')`. Giữ unique index/constraint order_id hiện có vì nó vẫn bảo đảm một reservation/order cho cả lịch sử. Partial index chỉ chứa phần chưa release về mặt logic; PostgreSQL tự duy trì khi state đổi, không xóa lịch sử hoặc làm mất công việc rollback.

DDL nằm trong `services/booking-service/database/2026-10-31-event-cancellation-indexes.sql`; đã áp và kiểm pg_indexes/pg_index trong DB cô lập, không dựa vào ddl-auto hoặc thêm @Index thường thay cho partial index. Khi triển khai trên DB đang hoạt động dùng CREATE INDEX CONCURRENTLY ngoài transaction; kiểm indisvalid/indisready và đúng definition sau khi chạy, không chỉ dựa vào IF NOT EXISTS/tên index. Pha finishing-task ngày31/10 đã áp trên DB119 bằng role booking_owner và kiểm đúng definition/valid+ready trước push; không thay quyền ứng dụng hoặc dữ liệu nghiệp vụ.

Trước khi kết luận tối ưu: lấy SQL thực do Hibernate sinh cho M9, xác minh điều kiện state khớp predicate (đặc biệt prepared/generic plan), ANALYZE và EXPLAIN (ANALYZE, BUFFERS) trên DB cô lập trước/sau index. Dữ liệu gồm nhiều event hủy cũ đã release, ít order mới cần xử lý, nhiều reservation chưa release của event chưa hủy, và không còn ứng viên; kiểm cả cursor null/lượt sau. LIMIT 20 chỉ giới hạn kết quả, không hứa DB chỉ đọc 20 dòng. Ghi execution time, actual rows/rows removed, buffers, sort/scan; không ép enable_seqscan=off hoặc yêu cầu mọi query nhỏ phải dùng index.

Index order `(event_id,lifecycle_state,id)` và index event status chỉ là ứng viên nếu EXPLAIN còn chứng minh cần; chưa đưa vào DDL hoặc bảng duyệt. Một partial index không bảo đảm loại được mọi chi phí join/history/event filter. Nguồn: [PostgreSQL partial indexes](https://www.postgresql.org/docs/current/indexes-partial.html), [EXPLAIN](https://www.postgresql.org/docs/current/using-explain.html). Không thêm class/method Java/test mới cho delta này; dùng SQL đo ở môi trường cô lập và D19/D20 hiện có để xác minh chọn đúng công việc.

## 4. Transaction hủy từng order và tái sử dụng

`OrderServiceImpl.cancelOrderForEventCancellation(UUID orderId)`: @Override @Transactional. Cấu hình lock timeout hiện có; đọc scalar eventId; khóa snapshot READ → order WRITE → purchase limit WRITE → inventory WRITE theo ID tăng dần → promotion WRITE. Snapshot không CANCELLED/ID đã mất thì no-op; đọc lại state sau chờ khóa, không dùng kết quả query batch để quyết định update.

| Trạng thái sau khóa | Xử lý |
|---|---|
| PENDING_PAYMENT + HELD | Release ngay, không chờ expiresAt; dùng lại năm helper HELD của cancelOrder/expireOrder, kể cả chưa có frozen snapshot hoặc chưa áp mã |
| ISSUING/COMPLETED + COMMITTED | Gọi nguyên trạng `releaseResourcesAfterIssuanceFailure(Order)` để trả purchased/limit/USED; đây là helper release COMMITTED đã có, không giả message phát vé lỗi |
| CANCELLED + HELD/COMMITTED | Release nhánh tương ứng; sửa phần còn lại do callback đã đổi order trước worker |
| CANCELLED + RELEASED | No-op, không đổi updatedAt/counter/usage |
| EXPIRED/ISSUANCE_FAILED | Giữ terminal hiện có; các TX cũ đã release; không đổi lịch sử chỉ để quét lại |

HELD chỉ đi cùng PENDING_PAYMENT hoặc CANCELLED; COMMITTED chỉ đi cùng ISSUING/COMPLETED hoặc CANCELLED. Kiểm state hiện hành phục vụ phân xử cạnh tranh; dùng guard tài nguyên trong helper cũ. Không thêm validateReservationAllocations, verify snapshot mới hoặc kiểm lại phép tạo Order/Reservation đã được TX cũ bảo đảm. Không tách thêm helper release; nhánh HELD lấy lock theo đoạn đã có trong expireOrder rồi gọi helper cũ.

Sau release: allocations/reservation RELEASED, order CANCELLED và updatedAt giờ DB sau khóa; tất cả cùng commit. Giữ acceptedChargeId/acceptedAt, frozen snapshot/version, tiền, items, expiresAt để Payment/callback vẫn truy được lịch sử. Lỗi counter/lock/flush/commit rollback toàn order, để worker retry. Lặp sau commit không release lần hai. Helper COMMITTED tiếp tục giữ các guard cũ và cách dùng frozen snapshot; không sửa semantics nhánh FAILED đã triển khai.

Release giảm held hoặc purchased và trả HELD/USED promotion đúng một lần. SEATED dùng helper cũ: seat có thể trở lại AVAILABLE/UNAVAILABLE theo isHidden, holder được xóa; **khả năng bán vẫn bị gate event CANCELLED chặn**. Đây là release accounting, không mở lại event, không reset capacity hoặc tạo cơ chế đóng inventory thứ hai; phù hợp quyết định release khi FAILED ngày29/10. Pending release cũng dùng cùng nguyên tắc, createOrder/áp mã/freeze luôn bị chặn sau gate.

## 5. Cạnh tranh và ranh giới service

- Create/Payment lấy snapshot READ trước gate WRITE: bên đang giữ READ hoàn tất trước; worker sau gate thu gom cả đơn vừa tạo/đã accept. Gate trước thì create/promotion/freeze bị chặn; charge chưa accept đi CANCELLED và release HELD theo settlePendingPayment cũ.
- Buyer cancel/expiry/worker cùng order WRITE: bên đầu release; bên sau đọc lại state và không release hai lần. Buyer request có thể bị EVENT_NOT_ON_SALE trong khoảng gate→worker; worker vẫn tiến triển độc lập, không chờ buyer thử lại.
- COMPLETED trước gate: worker hủy/release cả đơn đã hoàn thành. COMPLETED sau gate trước worker: query có CANCELLED+COMMITTED bắt lại. Worker trước COMPLETED: callback không hồi sinh CANCELLED, không phát OrderCompleted. FAILED và worker cùng cạnh tranh: helper cũ thấy RELEASED không trả purchased/USED lần nữa.
- acceptPayment replay cùng accepted charge giữ ACCEPTED theo idempotency; không sửa decision lịch sử. Payment phải xử lý marker hủy và tiền tới trễ độc lập, không dựa vào Booking phát OrderCompleted sau hủy. Ticket chặn check-in theo marker, batch void thuộc Ticket. Chưa coi hai service đã được kiểm chứng.

## 6. Danh sách class/method đã duyệt và triển khai cho EXECUTING-PLANS

Giữ ID v0.2 còn dùng; các ID đã bỏ ở v0.1/v0.2 không được coi là duyệt lại. Item thêm lần này dùng C12–C16/G5–G6/M8–M11/U9–U13/D13–D20. MAIN = `services/booking-service/src/main/java/com/flashticket/booking_service/`; TEST = `services/booking-service/src/test/java/com/flashticket/booking_service/`.

| ID | Class/file | NEW hoặc EXISTING | Vai trò/annotation/API sinh | Mẫu cụ thể hoặc ngoại lệ |
|---|---|---|---|---|
| C1 | MAIN/messaging/EventCancellationListener.java | NEW | @Component @RequiredArgsConstructor; final AvailabilityService availabilityService; G1 | SalesConfigurationListener; constructor injection Shop App |
| C2 | MAIN/availability/service/AvailabilityService.java | EXISTING | Thêm M2, không dependency/API khác | IOrderService của Shop App |
| C3 | MAIN/availability/service/impl/AvailabilityServiceImpl.java | EXISTING | Thêm M3, dùng dependency hiện có, giữ @Service/@RequiredArgsConstructor | Shop App OrderService.deleteOrder setter/save; TX/inbox ngoài mẫu |
| C8 | TEST/messaging/EventCancellationListenerTests.java | NEW | JUnit mock local, G3; U1–U3 | SalesConfigurationListenerTests |
| C10 | TEST/persistence/BookingPersistenceIT.java | EXISTING | Thêm 14 D-method bên dưới, fixture/proxy/DB/Rabbit hiện có | Ngoài mẫu Shop App, pattern expiry/issuance IT |
| C12 | MAIN/order/EventCancellationWorker.java | NEW | @Component @RequiredArgsConstructor @ConditionalOnProperty; final OrderRepository orderRepository, OrderService orderService; int batchSize @Value, UUID lastOrderId private; G5 | OrderExpiryWorker + JwtTokenUtil @Value |
| C13 | MAIN/order/repository/OrderRepository.java | EXISTING | Thêm M9 @Query JPQL, Pageable/@Param; không lock batch | Spring Data repository, theo findExpiredOrderIds |
| C14 | MAIN/order/service/OrderService.java | EXISTING | Thêm M10 | Interface nghiệp vụ cụ thể Shop App |
| C15 | MAIN/order/service/impl/OrderServiceImpl.java | EXISTING | Thêm M11, dependency hiện có không đổi; helper/thân method cũ giữ nguyên | expireOrder + releaseResourcesAfterIssuanceFailure |
| C16 | TEST/order/EventCancellationWorkerTests.java | NEW | @ExtendWith(MockitoExtension.class), @Mock repo/service, @InjectMocks worker; G6; U9–U13 | OrderExpiryWorkerTests |

| ID | Class | Chữ ký đầy đủ | Loại | Annotation/cách viết/lý do |
|---|---|---|---|---|
| G1 | C1 | `public EventCancellationListener(AvailabilityService availabilityService)` | NEW Lombok | Constructor duy nhất, không getter/setter/builder |
| G3 | C8 | `EventCancellationListenerTests()` | NEW implicit | Package-private, không setup/helper mới |
| G5 | C12 | `public EventCancellationWorker(OrderRepository orderRepository, OrderService orderService)` | NEW Lombok | Constructor dependency final; field config không vào constructor |
| G6 | C16 | `EventCancellationWorkerTests()` | NEW implicit | Package-private, không constructor/setup viết tay |
| M1 | C1 | `public void handleEventCancelled(Envelope<EventCancellation> message)` | NEW | @RabbitListener(queues="booking.event-cancelled.q", ackMode="AUTO", concurrency="1"); service proxy rồi phân loại lỗi |
| M2 | C2 | `public abstract void processEventCancellation(Envelope<EventCancellation> message)` | NEW | Interface có thể viết gọn theo repo |
| M3 | C3 | `public void processEventCancellation(Envelope<EventCancellation> message)` | NEW | @Override @Transactional REQUIRED; hash/boundary inline, setter/save |
| M8 | C12 | `public void cancelOrdersForCancelledEvents()` | NEW | @Scheduled(fixedDelayString="${booking.event-cancellation.poll-interval:PT5S}"); query một batch, gọi service proxy từng ID |
| M9 | C13 | `public abstract List<UUID> findOrderIdsForEventCancellation(UUID afterOrderId, Pageable pageable)` | NEW | @Query predicate §3, @Param("afterOrderId"), enum literals đầy đủ trong JPQL; query cũ chỉ chọn pending đã hết hạn nên không dùng thay |
| M10 | C14 | `public abstract void cancelOrderForEventCancellation(UUID orderId)` | NEW | Interface cho boundary TX/order của worker |
| M11 | C15 | `public void cancelOrderForEventCancellation(UUID orderId)` | NEW | @Override @Transactional REQUIRED; buyer cancel cũ chặn event CANCELLED và đơn đã accept, expireOrder cũ chờ deadline nên không gọi thay |
| R1 | C15 | `private void updateQuantityInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<TicketTypeInventory> lockedInventories, ReservationState targetState)` | REUSE | Target RELEASED, held giảm |
| R2 | C15 | `private void updateStandingInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, SectorInventory lockedSector, ReservationState targetState)` | REUSE | Target RELEASED, trả pool chung |
| R3 | C15 | `private void updateSeatedInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<SeatInventory> lockedSeats, ReservationState targetState)` | REUSE | Target RELEASED, guard state/holder cũ |
| R4 | C15 | `private void updatePurchaseLimitForPaymentDecision(Order lockedOrder, Reservation storedReservation, PurchaseLimit lockedPurchaseLimit, ReservationState targetState)` | REUSE | Target RELEASED, trả limit held |
| R5 | C15 | `private void updatePromotionUsageForPaymentDecision(Order lockedOrder, PromotionUsage storedUsage, PromotionInventory lockedPromotion, ReservationState targetState)` | REUSE | Usage thực HELD, không cần frozen snapshot |
| R6 | C15 | `private void releaseResourcesAfterIssuanceFailure(Order lockedOrder)` | REUSE | Release COMMITTED/USED; giữ tên/thân helper cũ, thêm caller M11; các releasePurchased*/releaseUsedPromotion bên trong giữ nguyên |

Các method/constructor trên non-static, không throws; interface không thân. M11 không gọi lại public cancelOrder/expireOrder hoặc listener của luồng FAILED. Không thay đổi chữ ký/thân method production cũ, không thêm DTO/entity/enum/mapper, field schema, anonymous/nested class, helper, @Bean hoặc API sinh khác ngoài G1/G3/G5/G6.

## 7. Kiểm thử đề xuất

Method NEW, package-private, non-static, @Test. U-method không throws; D-method `throws Exception`, @Transactional(propagation=Propagation.NOT_SUPPORTED), kiểm service proxy commit thật. Bảng bao gồm toàn bộ method test mới; đã viết/chạy sau gate EXECUTING-PLANS, không viết trước gate ở pha tài liệu.

| ID | Class | Chữ ký đầy đủ | Điều kiện/kết quả |
|---|---|---|---|
| U1 | C8 | `void delegatesEventCancellationToTransactionalService()` | Gọi service trước return |
| U2 | C8 | `void rejectsPermanentEventCancellationErrors()` | Business error thuộc danh sách đi reject/DLQ |
| U3 | C8 | `void propagatesEventCancellationDatabaseFailures()` | DB/commit lỗi không thành ACK thành công |
| U9 | C16 | `void pollsEventCancelledOrdersInBoundedBatches()` | Pageable đúng, gọi từng ID, cursor tiến |
| U10 | C16 | `void continuesAndRetriesFailedEventOrderCancellations()` | Order lỗi không chặn order sau; wrap và restart tìm lại |
| U11 | C16 | `void preservesEventCancellationCursorWhenQueryFails()` | Query lỗi không mất vị trí/chạy service |
| U12 | C16 | `void stopsEventCancellationBatchWhenInterrupted()` | Dừng và giữ cursor ở ID đã xử lý |
| U13 | C16 | `void rejectsNonPositiveEventCancellationBatchSize()` | Config batch không hợp lệ bị từ chối |
| D1 | C10 | `void createsCancellationGateBeforeSalesConfigurationArrives() throws Exception` | Cancellation trước config, gồm arrays rỗng; config sau không hồi sinh |
| D2 | C10 | `void marksExistingEventCancelledWithoutResettingInventory() throws Exception` | TX gate không đổi order/counter/JSON/version trước worker |
| D3 | C10 | `void replaysEventCancellationAndRejectsMessageIdConflicts() throws Exception` | Same/new messageId và hash conflict |
| D7 | C10 | `void arbitratesEventCancellationWithCreateOrderAndPayment() throws Exception` | Hai thứ tự gate READ/WRITE; chặn create/voucher/freeze, payment thắng trước được worker release, callback sau gate không accept mới |
| D11 | C10 | `void rollsBackEventCancellationGateWhenCommitFails() throws Exception` | Gate/inbox rollback đồng thời; retry TX mới |
| D12 | C10 | `void validatesCancellationBoundaryAndAcknowledgesAfterGateCommit() throws Exception` | Boundary IDs/version/status/owner/mode; broker/container AUTO thật chỉ ACK sau gate commit, không đợi batch |
| D13 | C10 | `void releasesHeldOrdersWhenEventIsCancelled() throws Exception` | QUANTITY/STANDING/SEATED, voucher có/không, frozen có/không; release ngay cả chưa hết hạn |
| D14 | C10 | `void releasesCommittedOrdersWhenEventIsCancelled() throws Exception` | ISSUING/COMPLETED, ba mode, USED voucher; purchased/limit/usage trả một lần, tiền/accepted/frozen giữ nguyên |
| D15 | C10 | `void releasesAlreadyCancelledOrdersWithCommittedReservations() throws Exception` | COMPLETED tới sau gate đổi order CANCELLED trước worker; query vẫn tìm và release |
| D16 | C10 | `void replaysEventOrderCancellationWithoutReleasingTwice() throws Exception` | Lặp/multi-worker không đổi counter hoặc updatedAt lần hai, không có refund/OrderCompleted mới |
| D17 | C10 | `void arbitratesEventOrderCancellationWithIssuanceAndExpiry() throws Exception` | Worker với COMPLETED/FAILED/expiry/buyer cancel; hai thứ tự khóa, không hồi sinh/double release |
| D18 | C10 | `void rollsBackEventOrderCancellationWhenCommitFails() throws Exception` | Lỗi tài nguyên/lock timeout/deferred commit: order/res/alloc/limit/inventory/promotion cùng rollback, retry được |
| D19 | C10 | `void resumesEventCancellationBatchAfterFailureAndWorkerRestart() throws Exception` | Ít nhất 10 order thật, batch nhỏ; lỗi một ID không mất đơn sau, worker mới quét lại sau gate đã ACK |
| D20 | C10 | `void preservesUnrelatedEventsAndReleasedTerminalOrders() throws Exception` | Event chưa hủy/ID mất/EXPIRED/ISSUANCE_FAILED/already released không bị release/đổi lịch sử |

Tái dùng fixture tạo order/promotion/freeze/accept/result, JDBC/latch/executor/trigger lỗi và Rabbit connection/template của IT. Worker test tích hợp khởi tạo đúng class production với OrderService proxy như expiryWorkerExpiresTenOrdersThroughTheProductionBatchAndTransactionalService; listener/broker test dùng production listener và service proxy. Endpoint/container test dựng inline bằng API Spring AMQP, queue tên riêng trong broker cô lập, không thêm @Bean/helper/type. Assert DB sau TX, dọn container/trigger/thread trong finally; không sửa DB119 hoặc queue nghiệp vụ để kiểm thử.

## 8. Đối chiếu convention và gate

- Common/index/FlashTicket đọc theo Git common directory; đã thay rule gate-only bằng hướng release đầy đủ theo yêu cầu hiện tại. Không cần thêm validation dữ liệu bất biến; query recovery giải quyết đúng race COMPLETED→CANCELLED đã đọc ở source.
- Đã đọc live Shop App OrderService.deleteOrder (setter/save, constructor injection) và JwtTokenUtil (@Value field). Scheduler/pessimistic lock/inbox/hash/AUTO và integration DB/broker ngoài mẫu CRUD Shop App, áp theo OrderExpiryWorker/SalesConfigurationListener/BookingPersistenceIT hiện có.
- Năm helper HELD và helper COMMITTED được dùng nguyên trạng; thay đổi code cũ ở mức thêm caller/new method vào class sẵn có, không refactor validation hoặc semantics cũ. Spring Data save, rowVersion null cho INSERT; không native upsert/EntityManager/flush ép thêm.

**EXECUTING-PLANS: APPROVED / COMPLETE** — C1–C3/C8/C10/C12–C16; G1/G3/G5/G6; M1–M3/M8–M11; R1–R6 REUSE; U1–U3/U9–U13; D1–D3/D7/D11–D20, annotation/ngoại lệ kèm theo. Nguồn: user “đồng ý” sau danh sách thực thi ngày31/10; không phát sinh class/method ngoài danh sách. Lịch sử BRAINSTORMING/WRITING-PLANS còn PENDING khi lập nháp, không tự ghi phê duyệt hồi tố cho các pha đó. Kết quả thực thi: 383 unit +129 IT, Config Server1 test, các check Maven pass; partial index/EXPLAIN kiểm trong PostgreSQL17 cô lập theo TESTING.md. Không coi đây là deploy119 hoặc Payment/Ticket E2E.
