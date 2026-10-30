# Logic buyer hủy đơn chờ thanh toán

Code ngày 30/10/2026 tại `D:/DATN/flash-ticket-platform`, branch `dev/son`, thuộc đợt bàn giao buyer cancellation. Flow: **POST cancel → xác thực/chủ đơn → khóa và phân xử → release HELD → CANCELLED → commit → trả OrderView**.

Task thêm `cancelOrder` ở controller, service interface và implementation, cùng mã `ORDER_NOT_CANCELLABLE`. Năm helper trả tài nguyên giữ nguyên, dùng chung với AcceptPayment và expiry.

## 1. Body rỗng; buyer lấy từ JWT

[OrderController.cancelOrder, dòng 32](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/controller/OrderController.java:32)

```java
    if (!request.isEmpty()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    return ResponseEntity.ok(orderService.cancelOrder(jwt.getSubject(), orderId));
```

Route `POST /api/v1/orders/{orderId}/cancel` nhận JSON `{}`. Map chỉ nhận body để từ chối field gửi thừa; không lưu DB. `orderId` lấy từ URL, buyerSubject lấy từ JWT; thiếu JWT hoặc thiếu role BUYER bị [SecurityConfig](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/config/SecurityConfig.java:46) chặn trước controller.

**Khác Shop App:** vẫn theo controller → service và constructor injection như [OrderController.deleteOrder mẫu](C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/controllers/OrderController.java:74), nhưng dùng JWT/UUID và command Map rỗng thay DTO có field; không cần tạo DTO rỗng hoặc thêm `@Valid` cho command này.

## 2. Lọc chủ đơn và khóa trước khi quyết định

[OrderServiceImpl.cancelOrder, dòng 86](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:86)

```java
    var eventId =
        orderRepository
            .findEventIdByIdAndBuyerSubject(orderId, buyerSubject)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
```

Đơn không tồn tại và đơn thuộc buyer khác cùng trả 404. Query đầu chỉ lấy scalar eventId để chưa đưa Order cũ vào persistence context; sau đó khóa snapshot READ và order WRITE có buyerSubject, rồi mới đọc trạng thái.

**Khác Shop App:** so với `deleteOrder` mẫu đọc `findById`, Booking dùng [snapshot PESSIMISTIC_READ](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/repository/EventSalesSnapshotRepository.java:15) và [owned order PESSIMISTIC_WRITE](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/repository/OrderRepository.java:40). Thứ tự tiếp theo là **purchase limit → inventory được chọn → promotion inventory**, thống nhất với các luồng đang tranh tài nguyên.

## 3. Hủy lặp được trả về trước kiểm hạn

[Nhánh trạng thái, dòng 98](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:98)

```java
    if (lockedOrder.getLifecycleState() == OrderState.CANCELLED)
      return orderMapper.toView(lockedOrder);
    if (lockedOrder.getLifecycleState() == OrderState.EXPIRED)
      throw new BusinessException(ErrorCode.ORDER_EXPIRED);
    if (lockedOrder.getLifecycleState() != OrderState.PENDING_PAYMENT)
      throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
```

Hai cancel cùng order được phân xử bằng order lock. Bên sau thấy CANCELLED thì trả view hiện có, không release hoặc ghi lại updatedAt, kể cả lúc đó đã qua hạn. Không cần thêm Idempotency-Key/receipt riêng cho command rỗng này.

Với pending, snapshot event CANCELLED trả EVENT_NOT_ON_SALE; accepted metadata phải chưa có và reservation phải HELD. ISSUING/COMPLETED/ISSUANCE_FAILED bị từ chối; `paymentFrozenAt` không phải điều kiện cấm hủy. Fan-out hủy event thuộc luồng riêng.

## 4. Chờ khóa xong mới kiểm deadline

[Giờ quyết định, dòng 156](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:156)

```java
    Instant decisionAt = orderRepository.currentDatabaseTime();
    if (!decisionAt.isBefore(lockedOrder.getExpiresAt()))
      throw new BusinessException(ErrorCode.ORDER_EXPIRED);
```

Buyer gửi request trước hạn nhưng phải chờ inventory tới quá hạn vẫn nhận ORDER_EXPIRED. Bằng expiresAt cũng hết hạn; API rollback và để worker xử lý expiry, không tự chuyển CANCELLED khi từ chối.

**Khác Shop App:** dùng [clock_timestamp()](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/repository/OrderRepository.java:43) sau các khóa, thay vì lấy giờ JVM hoặc giờ đầu transaction. Đây là điểm phân xử với Payment/expiry, không phải kiểm lại dữ liệu tạo đơn.

## 5. Trả tài nguyên bằng các helper đã có

[Các lời gọi release, dòng 160](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:160) đều truyền `ReservationState.RELEASED`:

| Tài nguyên / helper dùng lại | Kết quả |
|---|---|
| QUANTITY — [updateQuantityInventoryForPaymentDecision](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:921) | Giảm held theo ticket type; purchased giữ nguyên |
| STANDING — [updateStandingInventoryForPaymentDecision](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:949) | Giảm held của pool sector chung |
| SEATED — [updateSeatedInventoryForPaymentDecision](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:974) | Kiểm HELD và currentReservationId trước khi trả ghế; bỏ holder cũ, giữ generation; hidden ánh xạ UNAVAILABLE, còn lại AVAILABLE |
| Hạn mức — [updatePurchaseLimitForPaymentDecision](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:1014) | Giảm held của đúng event/buyer; purchased giữ nguyên |
| Voucher — [updatePromotionUsageForPaymentDecision](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:1034) | Giảm promotion held và chuyển usage HELD → RELEASED; used giữ nguyên |

```java
    lockedPromotion.setHeld(lockedPromotion.getHeld() - 1);
    if (targetState == ReservationState.COMMITTED) {
      lockedPromotion.setUsed(Math.addExact(lockedPromotion.getUsed(), 1));
      storedUsage.setState(PromotionUsageState.USED);
    } else storedUsage.setState(PromotionUsageState.RELEASED);
```

Helper voucher dựa PromotionUsage/PromotionInventory thực, nên dùng được cả trước và sau freeze. Cancel không tính lại tiền hoặc xét lại cửa sổ khuyến mãi. Giữ guard counter/holder hiện có; không thêm helper verify tổng allocation, không sửa thân các helper hoặc logic Payment/expiry.

## 6. Cập nhật cùng transaction rồi mới trả HTTP

[Cuối cancelOrder, dòng 173](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:173)

```java
    allocations.forEach(a -> a.setState(ReservationState.RELEASED));
    storedReservation.setState(ReservationState.RELEASED);
    reservationRepository.save(storedReservation);
    lockedOrder.setLifecycleState(OrderState.CANCELLED);
    lockedOrder.setUpdatedAt(decisionAt);
    orderRepository.save(lockedOrder);
```

`@Transactional` ở service bao trùm order, reservation, allocations và toàn bộ counter/usage; allocations managed được dirty checking. `save()` chưa đồng nghĩa commit: controller gọi service qua Spring proxy, commit xong mới nhận OrderView. Lỗi helper/flush/commit đi ra ngoài và rollback toàn bộ. Giá, tiền, expiry và frozen snapshot được giữ nguyên.

**Khác Shop App:** so với [OrderService.deleteOrder mẫu](C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/services/impl/OrderService.java:79) chỉ set active=false, Booking chuyển lifecycle và hoàn tài nguyên trong transaction; response dùng [OrderMapper.toView](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/mapper/OrderMapper.java:17) MapStruct có sẵn.

Payment thắng khóa và commit ACCEPTED trước thì cancel thấy ISSUING và từ chối. Cancel commit trước thì [settlePendingPayment](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:747) trả PaymentDecision.CANCELLED cho khoản thu tới sau, không trả tài nguyên lần hai. Expiry commit trước thì cancel thấy EXPIRED; cancel trước thì worker bỏ qua CANCELLED. API cancel không gọi Payment/broker để commit và không thực hiện refund.

## Kết quả kiểm chứng đã có

[TESTING.md](D:/DATN/flash-ticket-platform/services/booking-service/TESTING.md:3): full verify 375 unit + 115 integration tests đạt; sau đó tăng assertion voucher rollback trong D8 và chạy riêng method này đạt 1/1. Curl đạt 10/10 trên JAR thật với PostgreSQL/RabbitMQ/Keycloak cô lập, kiểm DB sau từng request. Runtime test dùng hold10s và tắt expiry/outbox worker để cô lập; chưa phải deploy119 hoặc Saga Payment/Ticket E2E. Lượt viết tài liệu này chỉ đối chiếu source/link, không chạy lại test hay sửa Java.
