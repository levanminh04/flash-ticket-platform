# Logic worker tự hết hạn order Booking

Code tại `D:/DATN/flash-ticket-platform`, branch `dev/son`, ngày 30/10/2026; phần triển khai đang chưa commit.

Flow: scheduler → `expireDueOrders()` chọn batch UUID → `expireOrder(UUID)` xử lý từng đơn trong transaction riêng → trả phần HELD → cập nhật order → commit.

## 1. Worker chọn đơn đến hạn

[OrderExpiryWorker.java:32](/D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/OrderExpiryWorker.java:32)

```java
  @Scheduled(fixedDelayString = "${booking.expiry.poll-interval:PT5S}")
  public void expireDueOrders() {
    if (batchSize <= 0) throw new IllegalArgumentException("Expiry batch size must be positive");
    var cutoff = orderRepository.currentDatabaseTime();
    var candidates =
        orderRepository.findExpiredOrderIds(
            OrderState.PENDING_PAYMENT, cutoff, lastOrderId, PageRequest.of(0, batchSize));
```

`expireDueOrders()` chỉ điều phối: đọc giờ DB và lấy tối đa 20 ID mỗi lượt. Cấu hình hiện tại đợi 5 giây **sau khi lượt trước kết thúc** mới chạy tiếp; không bảo đảm đơn được dọn đúng 5 giây sau deadline.

**Khác Shop App:** thêm scheduler và điều kiện bật bean cho xử lý nền. [Central YAML:5](/D:/DATN/flash-ticket-platform/infrastructure/config-server/src/main/resources/config/booking-service.yaml:5) bật expiry và scheduler pool2 để expiry/outbox có thể chạy đồng thời. Annotation mặc định không tạo worker nếu thiếu property enabled; dependency qua constructor và property qua field `@Value` vẫn theo mẫu Shop App.

## 2. Cursor giúp đi qua nhóm đơn lỗi

[Query UUID — OrderRepository.java:17](/D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/repository/OrderRepository.java:17)

```java
      select o.id from Order o
      where o.lifecycleState = :state and o.expiresAt <= :cutoff
        and (:afterOrderId is null or o.id > :afterOrderId)
      order by o.id asc
```

Query chỉ lấy ID, không khóa/nạp Order. Sau mỗi ID đã thử, worker tiến `lastOrderId` kể cả khi service báo lỗi; hết tập (batch rỗng/ngắn) thì reset cursor để quét lại. Đơn lỗi vẫn pending trong DB nên được thử lại ở vòng sau, thay vì luôn chiếm batch đầu.

**Khác Shop App:** JPQL có điều kiện cursor + Pageable phục vụ quét theo batch. Cursor chỉ ở bộ nhớ, restart sẽ quét lại từ đầu; an toàn chạy lặp nằm ở transaction/state guard. [Order.java](/D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/entity/Order.java) thêm index lifecycle_state/expires_at/id để hỗ trợ lọc, không phải cam kết hiệu năng.

## 3. Service khóa và kiểm trạng thái hiện tại

[OrderServiceImpl.java:79](/D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:79)

```java
    var lockedOrder = orderRepository.findLockedById(orderId).orElse(null);
    // Payment có thể đã commit trong lúc worker chờ khóa order.
    if (lockedOrder == null || lockedOrder.getLifecycleState() != OrderState.PENDING_PAYMENT)
      return;
```

`expireOrder(UUID)` mới thực hiện nghiệp vụ: khóa snapshot READ → order WRITE → purchase limit của buyer → inventory được chọn theo ID tăng dần → promotion inventory nếu có usage. Order/reservation/allocations được đọc sau khóa order. Pending có accepted charge/time hoặc reservation không còn HELD sẽ báo conflict.

**Khác Shop App:** `@Transactional` và pessimistic locks để phân xử với Payment/áp mã/chuẩn bị snapshot. Worker gọi bean service qua Spring proxy, không có transaction bao cả batch. Nếu Payment đã chuyển đơn sang ISSUING thì expiry bỏ qua, kể cả đã qua hạn giữ chỗ.

## 4. Đủ hạn mới trả tài nguyên bằng helper cũ

[OrderServiceImpl.java:143](/D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:143)

```java
    // Đọc giờ DB sau các khóa có thể phải chờ; cutoff của worker chỉ dùng chọn ứng viên.
    Instant decisionAt = orderRepository.currentDatabaseTime();
    if (decisionAt.isBefore(lockedOrder.getExpiresAt())) return;
    updatePurchaseLimitForPaymentDecision(
        lockedOrder, storedReservation, lockedLimit, ReservationState.RELEASED);
```

**Khác Shop App:** dùng `clock_timestamp()` trong [OrderRepository.java:43](/D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/repository/OrderRepository.java:43), đọc sau khi chờ khóa. `decisionAt == expiresAt` cũng hết hạn; cutoff của worker không quyết định thay service.

| Phần được trả | Helper dùng lại trong OrderServiceImpl | Khi target là RELEASED |
|---|---|---|
| QUANTITY | [updateQuantityInventoryForPaymentDecision:817](/D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:817) | Trừ held theo ticket type, giữ purchased. |
| STANDING | [updateStandingInventoryForPaymentDecision:845](/D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:845) | Trừ held của sector, không trừ thêm pool ticket type. |
| SEATED | [updateSeatedInventoryForPaymentDecision:870](/D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:870) | Kiểm HELD và đúng reservation hiện giữ; trả AVAILABLE, xóa currentReservationId, giữ generation. Helper có nhánh hidden→UNAVAILABLE. |
| Hạn mức buyer | [updatePurchaseLimitForPaymentDecision:910](/D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:910) | Trừ held, giữ purchased. |

Các guard ownership/counter của helper cũ được giữ. Không thêm `validateReservationAllocations`; kiểm allocation có sẵn của Payment vẫn nằm trong `settlePendingPayment`. Với schema SQL hiện tại, ghế hidden phải UNAVAILABLE, nên test không tạo fixture hidden+HELD trái constraint để kiểm nhánh đó.

## 5. Promotion dùng chung cho Payment và expiry

[updatePromotionUsageForPaymentDecision — OrderServiceImpl.java:930](/D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:930)

```java
    lockedPromotion.setHeld(lockedPromotion.getHeld() - 1);
    if (targetState == ReservationState.COMMITTED) {
      lockedPromotion.setUsed(Math.addExact(lockedPromotion.getUsed(), 1));
      storedUsage.setState(PromotionUsageState.USED);
    } else storedUsage.setState(PromotionUsageState.RELEASED);
```

Helper được sửa để dùng PromotionUsage/PromotionInventory, không đọc frozen snapshot: Payment accepted truyền COMMITTED; expiry truyền RELEASED. Vì vậy đơn đã giữ voucher nhưng chưa bắt đầu thanh toán vẫn trả voucher được; không cần helper releaseHeldPromotion riêng. Usage phải HELD, liên kết/tiền khớp và counter đủ trước khi cập nhật; thiếu usage chỉ hợp lệ khi discount=0.

Riêng [settlePendingPayment:757](/D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:757) giữ kiểm sự hiện diện và ID promotion khớp frozen snapshot. Phần khác mẫu CRUD Shop App ở đây là chuyển trạng thái/counter trong transaction chung, không phải CRUD một entity độc lập.

## 6. Hoàn tất transaction và xử lý lần chạy sau

[OrderServiceImpl.java:159](/D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:159)

```java
    allocations.forEach(a -> a.setState(ReservationState.RELEASED));
    storedReservation.setState(ReservationState.RELEASED);
    reservationRepository.save(storedReservation);
    lockedOrder.setLifecycleState(
        storedSnapshot.getEventStatus() == EventState.CANCELLED
            ? OrderState.CANCELLED
            : OrderState.EXPIRED);
```

Order cũng lưu updatedAt=decisionAt; tiền và snapshot giữ lại để tra lịch sử. `save()` chưa phải commit: Spring commit khi lời gọi qua service proxy kết thúc; lỗi resource/flush/commit làm toàn bộ thay đổi của đơn rollback. Worker chỉ bắt lỗi sau khi service trả lỗi, ghi orderId/errorCode và xử lý ID tiếp theo.

Chạy lại đơn EXPIRED/CANCELLED hoặc hai worker cùng ID: bên sau đọc state sau khóa rồi bỏ qua, không trả lần hai. Nếu expiry thắng trước, Payment đến sau nhận LATE/CANCELLED theo luồng hiện có. Worker không tạo PaymentDecision/inbox/outbox, không trả purchased/used của đơn đã accepted, không làm fan-out hủy event hay refund.

## Bằng chứng đã có

[TESTING.md:3](/D:/DATN/flash-ticket-platform/services/booking-service/TESTING.md:3) ghi final verify 30/10/2026 10:59:29 +07: **372 unit + 106 integration tests pass**, gồm race/rollback, cùng format/static checks. Business IT dùng PostgreSQL/RabbitMQ Testcontainers; chưa chứng minh deploy, auto-update schema trên server119 hoặc Payment/Ticket E2E. Tài liệu này chỉ đối chiếu source và kết quả đã có, không chạy lại suite.
