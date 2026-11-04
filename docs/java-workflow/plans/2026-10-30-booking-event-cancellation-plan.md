# Booking — Plan hủy event và release order

- Phiên bản **v0.2 — EXECUTING-PLANS COMPLETE**; lịch sử lập ở pha WRITING-PLANS. User chọn hướng release, đồng ý partial index, rồi gọi executing-plans và trả lời **“đồng ý”** sau bảng danh sách thực thi ngày31/10. Java/test/config/SQL đã triển khai và kiểm cục bộ; chưa deploy/server119.
- Đầu vào: [spec v0.4](../specs/2026-10-30-booking-event-cancellation-design.md). Khi lập nháp, BRAINSTORMING/WRITING-PLANS còn PENDING; xác nhận thực thi là nguồn duyệt danh sách EXECUTING-PLANS, không tự sửa lịch sử phê duyệt các pha trước.
- Task: [Booking xử lý thông báo hủy event](https://app.clickup.com/t/z8qgvc9he1); deadline theo spec **01/11/2026 23:00 Asia/Saigon**.
- Git common directory `D:/DATN/flash-ticket-platform/.git`; baseline `5526e79af9fcd0d3cb5e13a4a50986d4d9159bab`. Chưa có plan event-cancellation trước lượt này; giữ nguyên đường dẫn spec hiện có.

## 1. Đích bàn giao

Consumer ghi gate CANCELLED/inbox trong TX rồi AUTO ACK. Worker query event CANCELLED, reservation HELD/COMMITTED và order PENDING_PAYMENT/ISSUING/COMPLETED/CANCELLED, gọi transaction riêng từng order để release + CANCELLED. Predicate chưa release chung giúp khớp partial index và vẫn bắt order CANCELLED do callback. Replay, crash sau ACK, callback muộn và lỗi một order không làm mất công việc hoặc trả tài nguyên hai lần. Không chờ expiry để release pending.

Giữ nguyên helper và semantics của buyer cancel/expiry/AcceptPayment/issuance. Không thêm validateReservationAllocations, DTO/bảng/cột/dependency/Saga/refund/void-ticket; chỉ thêm partial index SQL theo P2a. Payment/Ticket thuộc phần tích hợp riêng. Giảm counter và trả seat theo helper cũ; gate event vẫn chặn khả năng bán. Không đồng nhất ACK với đã release hết order hoặc đã hoàn tiền.

## 2. Danh sách class và method của plan

Danh sách Java của plan giống spec v0.4; delta index/query không thêm class/method. MAIN = `services/booking-service/src/main/java/com/flashticket/booking_service/`; TEST = `services/booking-service/src/test/java/com/flashticket/booking_service/`. Tất cả khai báo mới đã được user duyệt cho **EXECUTING-PLANS**, theo nguồn xác nhận §6; không có delta mới.

| ID | Class/file | NEW hoặc EXISTING | Nội dung/API sinh | Mẫu hoặc ngoại lệ |
|---|---|---|---|---|
| C1 | MAIN/messaging/EventCancellationListener.java | NEW | @Component @RequiredArgsConstructor; final AvailabilityService availabilityService; G1; M1 | SalesConfigurationListener + constructor injection Shop App |
| C2 | MAIN/availability/service/AvailabilityService.java | EXISTING | M2 | Interface IOrderService Shop App |
| C3 | MAIN/availability/service/impl/AvailabilityServiceImpl.java | EXISTING | M3; dependency/annotation cũ giữ nguyên | Shop App deleteOrder setter/save; TX/inbox Booking |
| C8 | TEST/messaging/EventCancellationListenerTests.java | NEW | G3/U1–U3, JUnit mock local | SalesConfigurationListenerTests |
| C10 | TEST/persistence/BookingPersistenceIT.java | EXISTING | 14 D-method bên dưới, không helper/@Bean mới | DB/broker/proxy IT ngoài Shop App |
| C12 | MAIN/order/EventCancellationWorker.java | NEW | @Component @RequiredArgsConstructor @ConditionalOnProperty; final OrderRepository orderRepository, OrderService orderService; private int batchSize @Value, UUID lastOrderId; G5/M8 | OrderExpiryWorker + JwtTokenUtil @Value |
| C13 | MAIN/order/repository/OrderRepository.java | EXISTING | M9 @Query JPQL/@Param, Pageable | findExpiredOrderIds hiện có |
| C14 | MAIN/order/service/OrderService.java | EXISTING | M10 | Interface nghiệp vụ cụ thể Shop App |
| C15 | MAIN/order/service/impl/OrderServiceImpl.java | EXISTING | M11, R1–R6 dùng nguyên trạng; không thêm dependency | expireOrder/issuance release hiện có |
| C16 | TEST/order/EventCancellationWorkerTests.java | NEW | G6/U9–U13; MockitoExtension, @Mock repo/service, @InjectMocks worker | OrderExpiryWorkerTests |

| ID | Class | Chữ ký đầy đủ | Loại | Cách viết/annotation |
|---|---|---|---|---|
| G1 | C1 | `public EventCancellationListener(AvailabilityService availabilityService)` | NEW Lombok | Constructor final dependency |
| G3 | C8 | `EventCancellationListenerTests()` | NEW implicit | Package-private |
| G5 | C12 | `public EventCancellationWorker(OrderRepository orderRepository, OrderService orderService)` | NEW Lombok | Constructor final dependencies |
| G6 | C16 | `EventCancellationWorkerTests()` | NEW implicit | Package-private |
| M1 | C1 | `public void handleEventCancelled(Envelope<EventCancellation> message)` | NEW | @RabbitListener(queues="booking.event-cancelled.q", ackMode="AUTO", concurrency="1") |
| M2 | C2 | `public abstract void processEventCancellation(Envelope<EventCancellation> message)` | NEW | Interface viết gọn được |
| M3 | C3 | `public void processEventCancellation(Envelope<EventCancellation> message)` | NEW | @Override @Transactional REQUIRED |
| M8 | C12 | `public void cancelOrdersForCancelledEvents()` | NEW | @Scheduled(fixedDelayString="${booking.event-cancellation.poll-interval:PT5S}"); không TX batch |
| M9 | C13 | `public abstract List<UUID> findOrderIdsForEventCancellation(UUID afterOrderId, Pageable pageable)` | NEW | @Query cố định predicate nghiệp vụ, @Param("afterOrderId"), sort ID/enum literals đầy đủ |
| M10 | C14 | `public abstract void cancelOrderForEventCancellation(UUID orderId)` | NEW | Boundary gọi qua proxy |
| M11 | C15 | `public void cancelOrderForEventCancellation(UUID orderId)` | NEW | @Override @Transactional REQUIRED, guard sau khóa, setter/save |
| R1 | C15 | `private void updateQuantityInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<TicketTypeInventory> lockedInventories, ReservationState targetState)` | REUSE | Target RELEASED |
| R2 | C15 | `private void updateStandingInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, SectorInventory lockedSector, ReservationState targetState)` | REUSE | Target RELEASED |
| R3 | C15 | `private void updateSeatedInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<SeatInventory> lockedSeats, ReservationState targetState)` | REUSE | Target RELEASED |
| R4 | C15 | `private void updatePurchaseLimitForPaymentDecision(Order lockedOrder, Reservation storedReservation, PurchaseLimit lockedPurchaseLimit, ReservationState targetState)` | REUSE | Target RELEASED |
| R5 | C15 | `private void updatePromotionUsageForPaymentDecision(Order lockedOrder, PromotionUsage storedUsage, PromotionInventory lockedPromotion, ReservationState targetState)` | REUSE | Usage HELD thực, không dựa frozen |
| R6 | C15 | `private void releaseResourcesAfterIssuanceFailure(Order lockedOrder)` | REUSE | Dùng cho COMMITTED/USED; không đổi tên/thân helper hoặc releasePurchased*/releaseUsedPromotion bên trong |

Method/constructor trên non-static, không throws. G1/G5 không sinh getter/setter/builder; G3/G6 chỉ constructor Java mặc định. Không tạo thêm helper/fixture/nested/anonymous class/@Bean/mapper/generated API. Thân và chữ ký các method production đã có không đổi; chỉ thêm caller dùng helper cũ.

### Danh sách test cần duyệt

Tất cả NEW, package-private, non-static, @Test. U-method không throws. D-method `throws Exception`, @Transactional(propagation=Propagation.NOT_SUPPORTED); dùng proxy để kiểm commit thật, không assertion trong TX test rollback mặc định.

| ID | Class | Chữ ký đầy đủ | Phần xác minh |
|---|---|---|---|
| U1 | C8 | `void delegatesEventCancellationToTransactionalService()` | Delegation rồi return |
| U2 | C8 | `void rejectsPermanentEventCancellationErrors()` | Permanent lỗi đi DLQ |
| U3 | C8 | `void propagatesEventCancellationDatabaseFailures()` | DB lỗi đi container |
| U9 | C16 | `void pollsEventCancelledOrdersInBoundedBatches()` | Batch/order/cursor |
| U10 | C16 | `void continuesAndRetriesFailedEventOrderCancellations()` | Lỗi từng order, wrap/restart |
| U11 | C16 | `void preservesEventCancellationCursorWhenQueryFails()` | Query lỗi giữ cursor |
| U12 | C16 | `void stopsEventCancellationBatchWhenInterrupted()` | Interruption |
| U13 | C16 | `void rejectsNonPositiveEventCancellationBatchSize()` | Batch config |
| D1 | C10 | `void createsCancellationGateBeforeSalesConfigurationArrives() throws Exception` | Cancellation trước config |
| D2 | C10 | `void marksExistingEventCancelledWithoutResettingInventory() throws Exception` | TX gate không reset/cancel order trực tiếp |
| D3 | C10 | `void replaysEventCancellationAndRejectsMessageIdConflicts() throws Exception` | Inbox/hash |
| D7 | C10 | `void arbitratesEventCancellationWithCreateOrderAndPayment() throws Exception` | Gate vs create/Payment/voucher/freeze |
| D11 | C10 | `void rollsBackEventCancellationGateWhenCommitFails() throws Exception` | Gate/inbox rollback/retry |
| D12 | C10 | `void validatesCancellationBoundaryAndAcknowledgesAfterGateCommit() throws Exception` | Boundary + AUTO ACK sau commit |
| D13 | C10 | `void releasesHeldOrdersWhenEventIsCancelled() throws Exception` | Ba mode, voucher/freeze, chưa tới deadline vẫn release |
| D14 | C10 | `void releasesCommittedOrdersWhenEventIsCancelled() throws Exception` | ISSUING/COMPLETED, ba mode, USED voucher |
| D15 | C10 | `void releasesAlreadyCancelledOrdersWithCommittedReservations() throws Exception` | Query recovery CANCELLED+COMMITTED |
| D16 | C10 | `void replaysEventOrderCancellationWithoutReleasingTwice() throws Exception` | Replay/concurrent worker, không outbox mới |
| D17 | C10 | `void arbitratesEventOrderCancellationWithIssuanceAndExpiry() throws Exception` | COMPLETED/FAILED/expiry/buyer cancel |
| D18 | C10 | `void rollsBackEventOrderCancellationWhenCommitFails() throws Exception` | Resource/timeout/deferred commit rollback/retry |
| D19 | C10 | `void resumesEventCancellationBatchAfterFailureAndWorkerRestart() throws Exception` | 10 order thật, nhiều batch, restart/failure |
| D20 | C10 | `void preservesUnrelatedEventsAndReleasedTerminalOrders() throws Exception` | Không đụng event khác/terminal đã release |

## 3. Các bước triển khai sau khi đủ gate

### P1 — Ghi gate/inbox rồi ACK (C1–C3/C8/C10, G1/G3, M1–M3, U1–U3, D1–D3/D11/D12)

- Thêm listener tại C1, M2 vào interface C2, M3 vào implementation C3. Reuse DTO EventCancellation/Envelope/ContractRules, Validator/JsonMapper, snapshot/inbox repositories hiện có. Boundary và hash inline; không thêm verify helper hoặc dependency.
- M3 lấy snapshot WRITE trước inbox. Replay xử lý sau khóa. Existing row chỉ set eventStatus, không sửa JSON/source version/inventory. Missing row khởi tạo full sales CANCELLED với rowVersion null, inbox mới cũng null. PK conflict/commit error ra container; replay chạy trong TX mới.
- Listener gọi service proxy đồng bộ; chỉ bắt ba permanent BusinessException đã liệt kê trong spec để reject không requeue. Không catch DB rồi return. AUTO override mặc định manual; queue/binding/DLQ đã có trong deploy/rabbitmq/definitions.json, không sửa topology nghiệp vụ.
- Xác minh: D1–D3/D11 bảo đảm atomic gate/inbox và terminal config. D12 dựng endpoint/container inline qua API Spring AMQP với production listener/service proxy và connection/template của IT; queue tên riêng trong broker cô lập. Lỗi commit không nhận ACK thành công; gate commit được ACK dù batch chưa chạy. Dọn endpoint/queue/trigger trong finally. Chưa viết/chạy test ở pha plan.

### P2 — Query công việc bền và TX release/order (C10/C13–C15, M9–M11, R1–R6, D13–D18/D20)

- M9 dùng JPQL Order/Reservation/EventSalesSnapshot theo quan hệ/ID hiện có: event CANCELLED AND reservation HELD/COMMITTED AND order thuộc PENDING_PAYMENT/ISSUING/COMPLETED/CANCELLED. Điều kiện state reservation là điều kiện chung, không nằm riêng trong nhánh OR. Dựa invariant TX cũ để giữ nguyên tập công việc hợp lệ. Join một reservation/order tránh duplicate ID; scalar projection, `id > :afterOrderId`, sort tăng, Pageable giới hạn số dòng. Không thêm native query đếm/snapshot cursor; DDL index tách ở P2a.
- M11 set timeout TX hiện có → scalar eventId → snapshot READ → order WRITE → đọc lại gate/state/reservation. Event chưa hủy/ID mất/EXPIRED/ISSUANCE_FAILED no-op; CANCELLED+RELEASED no-op. Query là chọn ứng viên, không thay kiểm state sau khóa.
- HELD: lock limit → selected inventory theo ID → promotion như expireOrder. Không xét expiresAt, không yêu cầu frozen; gọi R1/R2/R3 theo selection và R4/R5 target RELEASED; allocations/reservation RELEASED.
- COMMITTED: gọi R6 nguyên trạng; helper tự khóa limit→inventory→promotion và release purchased/USED. Không gọi validateIssuanceResult hoặc giả FAILED; không copy thêm validator snapshot/allocation. RELEASED chỉ đi no-op sau state guard đã mô tả trong spec.
- Save CANCELLED + updatedAt DB sau khóa/release, giữ charge/accepted/frozen/tiền/items/deadline. Cả bộ rollback khi resource/flush/commit lỗi. Không có broker/HTTP trong TX, không emit OrderCompleted/refund.
- Xác minh D13/D14 với ba selection modes, voucher/freeze có/không; D15 bắt đúng race COMPLETED sau gate. D16 hai worker/replay; D17 hai thứ tự callback COMPLETED/FAILED và buyer/expiry. D18 dùng lock timeout và trigger deferred failure hiện có; assert cả promotion/counter/res/order rollback và retry. D20 bảo toàn event khác và trạng thái terminal cũ.

### P2a — Partial index và EXPLAIN (không khai báo Java mới)

- Sau gate thực thi, tạo duy nhất file SQL `services/booking-service/database/2026-10-31-event-cancellation-indexes.sql`: `CREATE INDEX CONCURRENTLY IF NOT EXISTS tbl_reservations_active_order_idx ON booking_schema.tbl_reservations(order_id) WHERE state IN ('HELD','COMMITTED');`. Chạy autocommit/ngoài transaction. Partial index cần SQL riêng vì JPA @Index thường/ddl-auto không biểu đạt predicate này; không thêm class/method/init bean hoặc migration framework.
- Kiểm index hiện có bằng pg_indexes/pg_index ở DB mục tiêu trước khi áp; giữ unique order_id, không tạo index thường trùng nó. Sau áp, xác minh đúng bảng/cột/predicate và indisvalid/indisready; index trùng tên sai definition hoặc invalid phải được báo và xử lý rõ, không coi IF NOT EXISTS là thành công. Trước khi bật worker ở môi trường triển khai phải áp/kiểm SQL này theo quyền DB được giao; chỉ push Java/health UP không chứng minh index tồn tại. Chưa chạy DDL/server119 trong pha tài liệu.
- Trên DB cô lập, lấy đúng SQL Hibernate sinh cho M9 và kiểm state literals/prepared plan có thể dùng partial predicate. Tạo dữ liệu đại diện bằng SQL: nhiều event hủy cũ/RELEASED, ít công việc mới, nhiều HELD/COMMITTED thuộc event chưa hủy và case không còn công việc. ANALYZE rồi EXPLAIN (ANALYZE, BUFFERS) trước/sau index, cả cursor null và cursor tiếp theo, lưu số rows/buffers/time/sort/scan. Không thêm method test/helper hoặc benchmark dữ liệu nghiệp vụ119.
- D19/D20 đang có phải xác nhận order đã RELEASED không được chọn lại, công việc mới và recovery CANCELLED+COMMITTED vẫn được xử lý. Query nhỏ dùng seq scan có thể hợp lý; không ép planner hoặc hứa mọi tick chỉ đọc 20 dòng. Đo cả overhead trên create/release cập nhật reservation state trong kiểm tích hợp hiện có, không thêm bài đo tải tự động ngoài scope.
- Chỉ bổ sung index order `(event_id,lifecycle_state,id)`/event status nếu kết quả đo chỉ ra nhu cầu; hai index này chưa được chọn hoặc đưa vào DDL. Ghi kết quả EXPLAIN thật vào TESTING.md khi thực hiện; hướng partial index đã được user đồng ý, hiệu năng chưa được kiểm chứng.

### P3 — Worker, config và phục hồi batch (C12/C16/C10, G5/G6, M8/M9/M11, U9–U13, D7/D19)

- Thêm C12 theo OrderExpiryWorker: query một trang mỗi tick, kiểm batchSize>0, gọi M10 qua OrderService proxy. M8 không @Transactional; mỗi lần M11 commit/rollback xong mới chạy ID tiếp theo. Catch RuntimeException quanh từng order, log orderId/ErrorCode không payload nhạy cảm.
- Sau cả ID lỗi vẫn tiến cursor để không chặn ID sau. Batch ngắn reset null; vòng sau và instance mới tìm lại ID còn DB predicate. Query lỗi không tiến cursor; interruption dừng. Không có marker batch completed, không lưu cursor RAM như tiến độ bền; restart không phụ thuộc message cancellation còn trong queue.
- Thêm `booking.event-cancellation.enabled: true`, `poll-interval: PT5S`, `batch-size: 20` tại `infrastructure/config-server/src/main/resources/config/booking-service.yaml`. C12 @ConditionalOnProperty(prefix="booking.event-cancellation",name="enabled",havingValue="true",matchIfMissing=false); @Value("${booking.event-cancellation.batch-size:20}") batchSize. Tắt enabled trong `services/booking-service/src/test/resources/application.yaml`; giữ pool hiện có.
- D7 dùng latch/TX thật phân xử hai thứ tự snapshot lock: create/accept thắng trước gate thì batch vẫn thu gom; gate thắng trước thì không create/accept mới. Áp mã/freeze sau gate bị chặn. D19 tạo ít nhất 10 order qua production service, gate qua service proxy; khởi tạo worker production với proxy như expiry IT, batch nhỏ và một lần fail để kiểm order sau vẫn commit, rollback order lỗi còn trong query, worker mới quét hết phần còn lại. Không seed dữ liệu sai hoặc stub toàn bộ service.
- U9–U13 kiểm logic phân trang/error/interruption giống OrderExpiryWorkerTests. Không thêm public API kích worker, @Bean, fixture/helper Java ngoài bảng duyệt.

### P4 — Kiểm hồi quy và ghi bằng chứng (không khai báo Java mới)

- Sau gate thực thi, chạy test mới/IT liên quan trước; rồi Booking Maven wrapper `-P integration -Dit.test=BookingPersistenceIT verify` trong thư mục `services/booking-service` để chạy unit, IT và các check Maven đã có. Dùng RTK/JDK23 theo môi trường dự án; không sửa version/dependency hoặc tự bỏ static/format check.
- Chạy test hiện có của Config Server cho thay đổi YAML trong `infrastructure/config-server`. Kiểm các suite buyer cancel, expiry, AcceptPayment, issuance vẫn giữ hành vi; lỗi regression phải xử lý trong phạm vi helper/caller đã được duyệt, không bổ sung validation “cho chắc”.
- Cập nhật `services/booking-service/TESTING.md` bằng command/count/kết quả thật và giới hạn: broker ACK gate, worker release Booking đã kiểm khác Payment refund/Ticket void E2E. Không sao chép số PASS task cũ thành kết quả task này.
- Khi lập plan chưa có yêu cầu curl/seed119/test thanh toán thật. Sau triển khai, user yêu cầu “user test thử tầm 10 test case đa dạng cho tôi”; đã chạy harness runtime cô lập như kết quả §7, không thêm Java class/method. Sau triển khai theo quyền được giao, chuyển writing-docs/finishing-task khi phù hợp; không coi lượt sửa spec/plan hoặc kiểm thử cục bộ là quyền push/deploy/đánh complete ClickUp.

## 4. Tiêu chí nghiệm thu

- Cancellation hợp lệ có gate/inbox atomic, AUTO ACK sau commit, replay không reset inventory/config và config muộn không mở lại event.
- Worker xử lý cả chưa hết hạn lẫn đã accept/COMPLETED; order CANCELLED do callback nhưng reservation COMMITTED vẫn được release. Không sửa lịch sử EXPIRED/ISSUANCE_FAILED đã release.
- Order/reservation/allocations/limit/inventory/promotion hội tụ trong TX/order, retry không âm counter hoặc trả hai lần. Giữ frozen/accepted metadata. AVAILABLE nội bộ không làm event CANCELLED bán lại được.
- Lỗi order/commit và restart sau ACK vẫn tìm được công việc từ DB; không cần message được giao lại hoặc một cursor RAM để phục hồi. Không có một TX dài chứa cả event/batch.
- Partial index đúng predicate/valid tại môi trường đã áp; query lọc reservation active chung và có bằng chứng EXPLAIN trước/sau trên dữ liệu đại diện. Không coi LIMIT 20 hoặc index tồn tại là bằng chứng tự đủ về hiệu năng.
- Payment hoàn tiền và Ticket chặn/vô hiệu vé được ghi là trách nhiệm tích hợp riêng, chưa tuyên bố E2E PASS.

## 5. Đối chiếu rule và trạng thái duyệt

Đã đọc common/index/FlashTicket theo Git common directory và reference hai skill. Đã đọc source OrderServiceImpl helpers, OrderRepository, EventSalesSnapshotRepository, OrderExpiryWorker/tests, SalesConfigurationListenerTests, BookingPersistenceIT và cấu hình thực tế. Mẫu Shop App đọc live: OrderService.deleteOrder và JwtTokenUtil. Chọn bổ sung method vào class sẵn có, giữ constructor injection/@Value, setter/save; scheduler/TX/locks/AUTO và DB/broker test là ngoại lệ theo pattern Booking hiện có.

Không tách validateReservationAllocations hoặc releaseHeldPromotion: TX cũ đã tạo liên kết dữ liệu; R5 đã dùng promotion usage thực. M9 có nhánh recovery vì race hợp lệ đã được xác minh trong processBookingIssuanceResult, không dự phòng dữ liệu sửa tay. Năm helper HELD và helper COMMITTED không refactor. Rule dự án đã thay quyết định gate-only bằng release đầy đủ; docs nghiệp vụ gốc không bị tự sửa/duyệt.

Lịch sử khi lập nháp: **BRAINSTORMING v0.4 PENDING; WRITING-PLANS v0.2 PENDING**, cùng danh sách C1–C3/C8/C10/C12–C16; G1/G3/G5/G6; M1–M3/M8–M11; R1–R6 REUSE; U1–U3/U9–U13; D1–D3/D7/D11–D20 và annotation/ngoại lệ. User lúc đó mới đồng ý delta partial index/query, không thêm khai báo Java. Sau đó đã chọn plan và duyệt danh sách riêng cho EXECUTING-PLANS; trạng thái/kết quả hiện hành ở §6–7, không tự phê duyệt hồi tố các pha trước.

## 6. Gate EXECUTING-PLANS — đã duyệt

Ngày31/10 user gọi `$shopapp-java-executing-plans` và yêu cầu “triển khai theo plan này”, chọn plan v0.2/spec v0.4 làm đầu vào thực thi. Đã kiểm checkout hiện tại dev/son, Git common directory đúng dự án, working tree chỉ có hai file spec/plan chưa track; không có thay đổi Java sẵn có. Đã đọc skill/reference/common/index/rule dự án và đối chiếu lại source expireOrder/releaseResourcesAfterIssuanceFailure/processBookingIssuanceResult: giữ helper nguyên trạng, guard trạng thái sau khóa và predicate reservation active phục vụ đúng race đã xác minh.

Danh sách thực thi không có delta: C1–C3/C8/C10/C12–C16; G1/G3/G5/G6; M1–M3/M8–M11; R1–R6 REUSE; U1–U3/U9–U13; D1–D3/D7/D11–D20. Gồm annotation/Lombok/constructor, 22 method test, config và partial index SQL đã liệt kê. **EXECUTING-PLANS: APPROVED / COMPLETE** — nguồn: user trả lời **“đồng ý”** ngay sau bảng danh sách thực thi trong chat ngày31/10. Quyền này dùng để triển khai/xác minh theo plan; không tự cấp quyền sửa server119, push/deploy hoặc cập nhật ClickUp. Không phát sinh class/method/helper ngoài plan.

## 7. Kết quả thực thi ngày31/10

- **P1 complete:** gate/inbox atomic, replay/hash conflict, config tới sau và boundary; listener AUTO sau service commit. Broker test gây deferred commit failure thật: không ACK, message requeue; phục hồi DB rồi chính message đó redeliver/commit/ACK. Permanent invalid message vào DLQ. Không dựa delivery-limit để giới hạn basic.nack trên Rabbit4.3.
- **P2 complete:** release HELD chưa freeze/có freeze/voucher có-không, COMMITTED cho ISSUING/COMPLETED ở cả ba mode; CANCELLED+COMMITTED recovery; callback/multi-worker/buyer/expiry race, resource/timeout/deferred commit rollback/retry. Năm helper HELD và helper COMMITTED nguyên trạng; tracked production diff chỉ thêm dòng/import/caller, không sửa thân method cũ.
- **P2a complete trong môi trường cô lập:** script partial index đã áp/kiểm valid+ready; lấy SQL Hibernate thật, EXPLAIN với120.020 order (100.000 lịch sử released/20.000 active event chưa hủy/20 active event hủy), bốn case first/cursor/empty/generic prepared plan đều dùng index sau áp. Median ba mẫu trước→sau:213.465→112.265ms,241.910→63.202ms,292.012→145.652ms,332.309→181.293ms. Đo SQL write-cost trước/sau với rollback; không coi cache-sensitive samples là throughput service/SLO hoặc overhead thuần của index. Không thêm index order/event; không áp DDL119.
- **P3 complete:** worker production batch3 xử lý10 order tạo bằng service thật, một commit fault không chặn9 order sau; worker mới retry ID còn lại từ DB sau gate đã ACK. Query bỏ order RELEASED, giữ recovery; cursor wrap/query error/interruption và batch config đã kiểm. Config trung tâm enabled=true/PT5S/batch20, test config disabled; pool không đổi.
- **P4 complete:** `-P integration -Dit.test=BookingPersistenceIT verify` exit0, **383 unit +129 integration tests**, zero failures/errors/skips; IT report21:38:39+07, SpotBugs0, format/enforcer/static checks pass. Config Server **1 test pass** bằng JDK21.0.5; Booking dùng JDK23. Full verify ban đầu thiếu env Rabbit cho contextLoads; đã cấp Rabbit localhost cô lập rồi chạy lại toàn bộ, không sửa test/production để bỏ kết nối thật. Các lỗi fixture đã sửa và giới hạn kiểm ghi tại [TESTING.md](../../../services/booking-service/TESTING.md).
- Đã dọn container benchmark/Rabbit riêng của task; Testcontainers tự quản lý DB/broker của IT. Pha finishing-task ngày31/10 đã áp SQL index trên DB119 ngoài TX bằng role booking_owner và kiểm definition/valid+ready trước push. Push/deploy/ClickUp được xác nhận theo SHA/run và comment bàn giao trên task, không suy từ test cục bộ. Refund/void/check-in Payment/Ticket E2E chưa kiểm chứng, không thuộc kết quả verify Booking này.
- **User tests bổ sung:10/10 PASS** theo yêu cầu sau thực thi. Production JAR + curl/JWT Keycloak thật + Rabbit quorum/topology thật + PostgreSQL17 + worker PT5S/batch20; kiểm độc lập DB sau từng ca. Bao gồm ba mode, HELD/frozen/USED voucher, ISSUING/COMPLETED, replay/hash conflict DLQ, charge/result muộn, restart sau ACK, deferred commit fault không chặn order khác và cancellation trước config. E8 tắt worker chỉ khi dàn dựng trạng thái trung gian, rồi bật lại cùng JAR để kiểm phục hồi; không mock service hoặc gọi worker Java trực tiếp. Báo cáo/JAR SHA và giới hạn tại TESTING.md và target/event-user-results.json; không thêm khai báo Java, không chạy Payment/Ticket refund E2E. Đã dọn app/3 container/credential file và kiểm cổng localhost đóng, không chạm119.
