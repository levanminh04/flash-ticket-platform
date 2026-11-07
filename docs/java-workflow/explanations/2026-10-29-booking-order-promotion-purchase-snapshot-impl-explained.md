# Logic áp voucher và chốt tiền thanh toán

Code tại `D:/DATN/flash-ticket-platform`, nhánh `dev/son`, ngày 29/10/2026.
Flow: FE áp voucher → Booking giữ lượt mã và cập nhật tiền → Payment yêu cầu PurchaseSnapshot → Booking lưu bản chốt tiền rồi trả về.

## 1. Hai API riêng

| API | Đầu vào | Kết quả |
| --- | --- | --- |
| `POST /api/v1/orders/{orderId}/promotion` | JWT BUYER, body `{"code":"SAVE"}` | OrderView với tiền đã giảm |
| `POST /internal/v1/orders/{orderId}/purchase-snapshot` | JWT INTERNAL_SERVICE, body `{"buyerSubject":"..."}` | PurchaseSnapshot đã lưu |

Controller chỉ chuyển dữ liệu vào service: [OrderController:29](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/controller/OrderController.java:29), [InternalOrderController:22](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/controller/InternalOrderController.java:22).
API nội bộ dùng quyền chung INTERNAL_SERVICE; Payment có trách nhiệm xác thực buyer trước khi truyền buyerSubject. Không có API freeze riêng.

## 2. Khóa trước khi kiểm tra và sửa đơn

[OrderRepository:18](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/repository/OrderRepository.java:18):

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Order> findLockedByIdAndBuyerSubject(UUID id, String buyerSubject);
```

Cả hai service đi theo thứ tự: đọc eventId dạng scalar → khóa đọc snapshot event → khóa ghi order. Đọc scalar trước giúp chưa nạp entity Order cũ vào persistence context trước lúc chờ khóa. Áp voucher lấy thêm khóa ghi promotion inventory.

**Khác luồng OrderService của Shop App đã đối chiếu:** dùng khóa để hai request cùng đơn không sửa tiền/chốt snapshot đồng thời; khóa promotion để hai đơn không cùng lấy lượt cuối. Nhiều đơn khác nhau vẫn có thể cùng giữ khóa đọc snapshot; consumer sửa snapshot phải chờ. Timeout chờ từng khóa là 5s trong transaction, không phải giới hạn tổng thời gian HTTP.

[PromotionServiceImpl:113](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/promotion/service/impl/PromotionServiceImpl.java:113):

```java
// Request có thể chờ lượt cuối của mã: kiểm lại đồng hồ sau khóa promotion.
decisionAt = orderRepository.currentDatabaseTime();
if (!decisionAt.isBefore(lockedOrder.getExpiresAt()))
  throw new BusinessException(ErrorCode.ORDER_EXPIRED);
```

Giờ quyết định lấy từ DB sau khi chờ khóa. Trước khi xử lý, service cũng chặn event CANCELLED, order không PENDING_PAYMENT, reservation không HELD hoặc sai dữ liệu liên kết.

## 3. Áp voucher: giảm tiền và giữ một lượt

[PromotionServiceImpl:76–129](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/promotion/service/impl/PromotionServiceImpl.java:76): mã được trim/uppercase; trước freeze, áp lại cùng mã đang HELD trả đơn hiện tại, không giữ thêm lượt. Đã có mã khác thì báo ORDER_ALREADY_HAS_PROMOTION; đã freeze thì báo PRICE_FROZEN.

Mã phải tồn tại duy nhất trong config, inventory khớp event/version/config, còn trong thời gian dùng, còn lượt và buyer chưa có usage HELD/USED của mã đó.

```java
var discount = calculatePromotionDiscount(lockedOrder.getSubtotal(), promotionConfiguration);
var total = lockedOrder.getSubtotal().subtract(discount);
if (total.signum() <= 0) throw new BusinessException(ErrorCode.NON_POSITIVE_TOTAL);
```

FIXED giảm đúng amount; PERCENT tính theo subtotal, làm tròn HALF_UP về số nguyên. Ví dụ 100005 × 10% → giảm 10001, còn 90004. Không tự giới hạn mức giảm nếu kết quả không dương.

[PromotionServiceImpl:139](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/promotion/service/impl/PromotionServiceImpl.java:139):

```java
usage.setExpiresAt(lockedOrder.getExpiresAt());
lockedPromotion.setHeld(lockedPromotion.getHeld() + 1);
lockedOrder.setDiscount(discount);
lockedOrder.setTotal(total);
```

Tạo PromotionUsage HELD và giữ đúng một lượt mã cho order, không theo số vé. Usage hết hạn cùng order; không kéo dài thời gian giữ chỗ.

**Khác luồng mẫu đã đối chiếu:** method `@Transactional` lưu usage + counter promotion + tiền order cùng một transaction. `save()` chưa phải commit; lỗi lúc flush/commit phải đi ra ngoài để rollback cả bộ. Spring proxy commit thành công trước khi controller nhận kết quả.

## 4. Chốt PurchaseSnapshot lần đầu

[OrderServiceImpl:134](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:134):

```java
var frozen =
    buildPurchaseSnapshot(
        lockedOrder, storedSnapshot.getConfigurationSnapshot(), storedUsage, decisionAt);
if (!validator.validate(frozen).isEmpty())
  throw new BusinessException(ErrorCode.CONFIG_NOT_READY);
```

| Dữ liệu trong snapshot | Nguồn |
| --- | --- |
| Tên vé, số lượng, giá, ghế; subtotal/discount/total | OrderItem và Order đã lưu |
| Mã và mức giảm đã áp | PromotionUsage và cấu hình promotion liên quan |
| Tên/thời gian/địa điểm event, sourceConfigVersion | Snapshot config được đọc ở lần freeze đầu |
| sourceOrderVersion | `"1"`, tương ứng paymentSnapshotVersion=1; không phải JPA rowVersion |

Helper [buildPurchaseSnapshot:148](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:148) kiểm dữ liệu liên kết và sắp item/ghế để thứ tự snapshot nhất quán; sort này không nhằm lấy khóa DB. Config tăng version không tự hủy đơn hay tính lại giá; Order.sourceConfigVersion vẫn giữ version lúc tạo đơn.

```java
lockedOrder.setFrozenPurchaseSnapshot(frozen);
lockedOrder.setPaymentFrozenAt(decisionAt);
lockedOrder.setPaymentSnapshotVersion(1L);
lockedOrder.setUpdatedAt(decisionAt);
orderRepository.save(lockedOrder);
```

Snapshot và các field freeze commit cùng transaction. **Khác luồng mẫu đã đối chiếu:** dùng DTO snapshot có sẵn và gọi Validator trực tiếp vì object được tạo trong service, không đi qua `@Valid @RequestBody`.

## 5. Gọi lại và hai request chạy đồng thời

[OrderServiceImpl:99](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:99): nếu paymentFrozenAt đã có, kiểm snapshot đã lưu khớp order rồi `return frozen`; không tính lại tiền, tăng version hay đổi frozenAt. Trước nhánh replay vẫn kiểm owner, event, trạng thái và hạn giữ, nên snapshot cũ không làm đơn hết hạn thanh toán lại được.

- Apply lấy khóa order trước → commit voucher → request snapshot đọc tiền đã giảm.
- Snapshot lấy khóa order trước → commit freeze → apply nhận PRICE_FROZEN.

Kiểm chứng đã có: full verify đạt **355 unit + 64 integration tests**, không lỗi/skip; xem [TESTING.md](D:/DATN/flash-ticket-platform/services/booking-service/TESTING.md). Chưa kiểm E2E với Payment/cổng thanh toán, chưa deploy. Task này chưa có worker giải phóng lượt mã/giữ chỗ khi hết hạn; expiresAt không tự thực hiện việc đó.

Mẫu đối chiếu trực tiếp: [Shop App OrderService.createOrder](C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/services/impl/OrderService.java:29). Các note “Khác Shop App” chỉ so với luồng này, không kết luận toàn bộ repo không có cơ chế tương tự.
