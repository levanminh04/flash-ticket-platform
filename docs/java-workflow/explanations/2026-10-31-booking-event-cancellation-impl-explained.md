# Logic Booking nhận hủy event và release order

Code tại `D:/DATN/flash-ticket-platform`, branch `dev/son`, ngày31/10/2026.
Flow: `event.cancelled.v1 → lưu gate CANCELLED + inbox → commit → AUTO ACK`; worker độc lập `query order còn việc → release + CANCELLED trong một TX/order`.

Mẫu đối chiếu đã đọc trực tiếp: [Shop App OrderService.deleteOrder](C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/services/impl/OrderService.java:79) và [JwtTokenUtil](C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/components/JwtTokenUtil.java:23). Constructor injection, setter/save và field @Value giữ cách viết quen thuộc; các ghi chú dưới đây so với luồng CRUD mẫu này.

## 1. Commit gate trước ACK, không đợi release cả event

[EventCancellationListener](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/messaging/EventCancellationListener.java:18) dùng `ackMode="AUTO"`, gọi service qua Spring proxy rồi mới return. [processEventCancellation](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:59) có @Transactional: validate Envelope/DTO và quan hệ IDs/version/status/owner, khóa snapshot WRITE rồi ghi gate/inbox cùng transaction.

```java
} else if (snapshot.getEventStatus() != EventState.CANCELLED) {
  // Chỉ đóng gate; giữ JSON/version thương mại và counter cho worker trả từng đơn.
  snapshot.setEventStatus(EventState.CANCELLED);
  eventSalesSnapshotRepository.save(snapshot);
}
```

[Nhánh snapshot đã có](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:118) chỉ đổi status, giữ JSON/version thương mại và inventory; snapshot chưa có thì tạo từ full sales CANCELLED, không khởi tạo inventory. Gate chặn đặt mới/áp mã/lấy snapshot thanh toán; config tới sau không được mở lại event.

**Khác Shop App:** listener AUTO, service proxy và transaction gate/inbox tách việc nhận message khỏi release hàng loạt. `save()` chưa là commit; lỗi flush/commit đi ra container nên không ACK thành công. ACK chỉ hoàn tất phần gate, không phải toàn bộ order đã release.

## 2. Inbox chống xử lý trùng, lỗi vĩnh viễn vào DLQ

[Inbox sau snapshot lock](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:88): khóa `(booking.event-cancelled,messageId)`, hash SHA-256 toàn Envelope.

```java
if (receipt.isPresent()) {
  if (!payloadHash.equals(receipt.get().getPayloadHash()))
    throw new BusinessException(ErrorCode.MESSAGE_ID_CONFLICT);
  return;
}
```

Cùng ID/hash return; khác hash báo conflict. MessageId mới cho cùng lần hủy ghi receipt mới, nhưng gate đã CANCELLED không save lại snapshot. Entity mới giữ rowVersion null để INSERT; race thiếu snapshot dựa PK và rollback, không tiếp tục dùng transaction lỗi.

**Khác Shop App:** inbox/hash và version phục vụ broker giao lặp/sai thứ tự. [Listener phân loại lỗi](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/messaging/EventCancellationListener.java:23) reject không requeue với INVALID_MESSAGE/MESSAGE_ID_CONFLICT/SALES_CONFIGURATION_CONFLICT; lỗi DB được giao lại, không có application retry/backoff. Rabbit4.3 không tính basic.nack vào delivery-limit, nên không coi cấu hình limit là giới hạn mọi retry DB.

## 3. Worker chỉ chọn phần chưa release

[EventCancellationWorker](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/EventCancellationWorker.java:31): fixedDelay chờ sau lượt trước, mặc định PT5S và batch20, cấu hình tại [Booking YAML](D:/DATN/flash-ticket-platform/infrastructure/config-server/src/main/resources/config/booking-service.yaml:5).

```java
var candidates =
    orderRepository.findOrderIdsForEventCancellation(lastOrderId, PageRequest.of(0, batchSize));
for (var orderId : candidates) {
```

[Query scalar ID](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/repository/OrderRepository.java:17) join snapshot CANCELLED, reservation HELD/COMMITTED và order PENDING_PAYMENT/ISSUING/COMPLETED/CANCELLED, sort ID và phân trang bằng `id > afterOrderId`. Event hủy cũ đã release hết không chiếm slot trong batch.

Giữ order CANCELLED trong query vì COMPLETED tới sau gate có thể đã đổi ISSUING→CANCELLED nhưng reservation vẫn COMMITTED. **Khác Shop App:** scheduler/query batch tiếp tục công việc sau ACK; không tải toàn bộ event/order lên để kiểm từng bản ghi trong Java.

## 4. Một transaction cho mỗi order, dùng lại helper cũ

[cancelOrderForEventCancellation](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:79) có @Transactional. Worker gọi qua OrderService proxy; khóa snapshot READ → order WRITE → limit → inventory được chọn theo ID → promotion, đọc lại gate/state sau khi chờ khóa.

```java
// Callback/worker khác có thể đã release trong lúc chờ khóa order.
if (storedReservation.getState() == ReservationState.RELEASED) return;
if (storedReservation.getState() == ReservationState.COMMITTED) {
```

| Trạng thái hiện tại | Phần release được dùng |
|---|---|
| PENDING_PAYMENT + HELD | Năm helper inventory/limit/promotion của expiry/buyer cancel, target RELEASED; không chờ expiresAt và không đòi frozen snapshot |
| ISSUING/COMPLETED + COMMITTED | [releaseResourcesAfterIssuanceFailure](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:480) nguyên trạng để trả purchased/limit/USED; không giả message FAILED |
| CANCELLED + HELD/COMMITTED | Release nhánh tương ứng, khép phần còn lại do callback đổi order trước worker |
| Đã RELEASED hoặc order EXPIRED/ISSUANCE_FAILED | No-op, không trả tài nguyên lần hai hoặc sửa lịch sử terminal |

[Promotion helper HELD](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:1137) đọc usage thực nên dùng được trước freeze. Không thêm helper verify allocation; giữ guard tài nguyên/holder hiện có trong helper cũ.

```java
lockedOrder.setLifecycleState(OrderState.CANCELLED);
lockedOrder.setUpdatedAt(orderRepository.currentDatabaseTime());
orderRepository.save(lockedOrder);
```

[Kết thúc transaction](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:175): order, reservation/allocations, counter và promotion cùng commit/rollback; giữ accepted charge, frozen snapshot, giá và deadline. **Khác Shop App:** khóa/transaction phối hợp worker với Payment/issuance/buyer/expiry; không có transaction bao cả batch hoặc gọi broker/HTTP trong transaction release.

## 5. Retry và restart tìm việc từ DB

[Cuối lượt worker](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/EventCancellationWorker.java:39) bắt lỗi từng order sau rollback, log ID/ErrorCode rồi vẫn tiến cursor; batch ngắn reset cursor để vòng sau quét lại ID lỗi.

```java
  lastOrderId = orderId;
}
if (candidates.size() < batchSize) lastOrderId = null;
```

Cursor RAM chỉ tối ưu phân trang; query lỗi không tiến cursor, interruption dừng batch. **Khác Shop App:** công việc bền nằm trong trạng thái DB, không phải danh sách ID trong RAM. App chết sau ACK thì worker mới bắt đầu từ null và tìm lại reservation chưa RELEASED; không cần publish lại cancellation.

## 6. Partial index hỗ trợ tìm việc, gate vẫn chặn bán lại

[SQL index](D:/DATN/flash-ticket-platform/services/booking-service/database/2026-10-31-event-cancellation-indexes.sql:3):

```sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS tbl_reservations_active_order_idx
ON booking_schema.tbl_reservations (order_id)
WHERE state IN ('HELD', 'COMMITTED');
```

**Khác Shop App:** partial index chỉ bao phần chưa release; giữ unique order_id cho toàn bộ lịch sử. Chạy SQL ngoài transaction và kiểm definition/indisvalid/indisready; ddl-auto không tự tạo index có predicate này. LIMIT20 không bảo đảm DB chỉ đọc20 dòng: join vẫn phải lọc active order của event khác, cần EXPLAIN trên dữ liệu đại diện.

Release giảm held/purchased và trả voucher; [helper SEATED cũ](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:644) có thể trả seat AVAILABLE theo isHidden, nhưng snapshot CANCELLED vẫn chặn khả năng bán. Kết quả phát vé đọc thấy gate CANCELLED không tạo OrderCompleted mới; outbox đã có từ trước vẫn được relay độc lập. Refund/void vé thuộc Payment/Ticket.

## 7. Bằng chứng đã có

[TESTING.md](D:/DATN/flash-ticket-platform/services/booking-service/TESTING.md:16): 383 unit +129 integration test pass, Config Server1 test pass, SpotBugs0; partial index được dùng trong bốn tình huống EXPLAIN trên fixture120.020 order. [10 ca user test](D:/DATN/flash-ticket-platform/services/booking-service/TESTING.md:86) qua JAR/API/JWT/Rabbit/scheduler thật cũng pass, gồm restart và lỗi commit từng order.

Lượt viết MD chỉ đối chiếu source/bằng chứng, không chạy lại test. Ngày31/10, pha finishing-task đã áp index trên DB119 ngoài transaction bằng role booking_owner và kiểm đúng definition, valid+ready trước push. Kết quả deploy theo run đúng SHA và cập nhật trên task ClickUp; các test trên là kiểm cục bộ, chưa kiểm Payment refund/Ticket void/check-in E2E.
