# Logic tạo order và giữ chỗ của Booking

Đối chiếu code `dev/son` ngày 28/10/2026. Note **khác Shop App** so với luồng OrderController → OrderService → OrderDetailService của dự án mẫu. Các đoạn code chỉ trích phần cần giải thích.

Luồng chính: **nhận request → kiểm idempotency → khóa dữ liệu → kiểm điều kiện bán/giá/hạn mức → giữ inventory → lưu receipt → commit → HTTP 201**.

## 1. Nhận request

[Controller, dòng 25](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/controller/OrderController.java:25)

```java
@AuthenticationPrincipal Jwt jwt,
@RequestHeader("Idempotency-Key") UUID idempotencyKey,
@Valid @RequestBody CreateOrderRequest request
```

Lấy buyer từ `jwt.getSubject()`, kiểm DTO, gọi service và trả 201. **Khác Shop App:** chủ đơn lấy từ JWT thay vì userId trong body; thêm header chống tạo đơn trùng. Các lỗi đi qua global handler.

## 2. Cùng key thì trả lại order đã tạo

[Impl, dòng 78](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:78)

```java
String requestHash = calculateCreateOrderRequestHash(request);
IdempotencyRequestId requestId = new IdempotencyRequestId();
requestId.setActorSubject(buyerSubject);
requestId.setOperation("CREATE_ORDER");
requestId.setRequestKey(idempotencyKey);
```

**Khác Shop App:** lưu receipt theo `(buyer, CREATE_ORDER, key)` và hash SHA-256 của DTO. Cùng key/cùng hash → đọc lại order hiện có; khác hash → `IDEMPOTENCY_CONFLICT`. Replay không giữ thêm vé hoặc gia hạn expiry. Kiểm lại receipt sau các lần chờ khóa để nhận kết quả request khác vừa commit.

## 3. Transaction, retry và khóa

[Impl, dòng 60](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:60)

```java
@Transactional
@Retryable(includes = DataIntegrityViolationException.class, maxRetries = 1, delay = 0)
```

**Khác Shop App:** toàn bộ tạo đơn/giữ vé nằm trong một transaction. `@Retryable`, được bật bằng `@EnableResilientMethods`, cho thử thêm một lần khi gặp nhóm lỗi integrity, phục vụ race INSERT. Không retry mọi exception hoặc tiếp tục ghi trong transaction đã rollback.

| Thứ tự khóa | Cách khóa | Mục đích |
| --- | --- | --- |
| Snapshot event | PESSIMISTIC_READ | Nhiều order cùng đọc; consumer cập nhật config phải chờ |
| Purchase limit `(eventId, buyer)` | PESSIMISTIC_WRITE | Chặn hai đơn cùng buyer vượt hạn mức |
| Inventory được chọn | PESSIMISTIC_WRITE | Chặn cùng giữ một ghế hoặc bán vượt pool |

Type/seat được khóa theo ID tăng dần. Hai buyer chọn ghế khác nhau có thể xử lý đồng thời.

**Hai cách lưu cần chú ý:** purchase limit mới dùng `saveAndFlush()` để PK phân xử INSERT trước khi khóa inventory; receipt mới giữ `@Version Long rowVersion = null` để `save()` INSERT, không merge đè bản cũ. Không tự tăng version trong service.

## 4. Giờ bán và thời hạn giữ chỗ

[Repository, dòng 12](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/repository/OrderRepository.java:12)

```java
@Query(value = "select clock_timestamp()", nativeQuery = true)
Instant currentDatabaseTime();
```

**Khác Shop App:** lấy giờ DB sau khi chờ khóa. Event phải PUBLISHED và `saleStartsAt <= decisionAt < saleEndsAt`. Order và reservation cùng có `expiresAt = decisionAt + 15 phút`.

Query native thứ hai là `set_config('lock_timeout', :timeout, true)`: giới hạn chờ từng lần lấy khóa trong transaction hiện tại là 5 giây; đây không phải timeout HTTP/tunnel.

## 5. Tạo items và tính giá

[Impl, dòng 291](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:291)

```java
BigDecimal unitPrice = new BigDecimal(type.unitPrice());
BigDecimal lineSubtotal = unitPrice.multiply(BigDecimal.valueOf(entry.getValue()));
subtotal = subtotal.add(lineSubtotal);
```

`populateOrderItems()` kiểm ID còn trong snapshot và đúng mode/sector/type; ghế không được hidden hoặc lặp. Gom các ghế cùng type thành một item. Tên, giá và thông tin ghế được chép từ snapshot vào order.

**Khác Shop App:** server tính tiền bằng BigDecimal từ giá config, không lấy giá/tổng tiền do client gửi. Hiện `discount = 0`, `total = subtotal`; tổng phải dương và precision không vượt 19. Response dùng MapStruct `OrderMapper.toView()` hiện có, khác ModelMapper trong mẫu.

## 6. Giữ inventory theo ba nhánh

[Impl, dòng 317](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:317)

| Nhánh | Dữ liệu thay đổi |
| --- | --- |
| QUANTITY | Tăng `held` của từng ticket type |
| STANDING | Cộng tổng quantity vào một pool sector; các type trong sector dùng chung pool |
| SEATED | Từng ghế chuyển AVAILABLE → HELD, gắn reservationId và tăng generation; không tăng held của sector |

Ví dụ check đủ vé đứng:

[Impl, dòng 353](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:353)

```java
if (lockedSector.getHeld()
    > lockedSector.getConfiguredCapacity() - lockedSector.getPurchased() - quantity)
  throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY);
lockedSector.setHeld(lockedSector.getHeld() + quantity);
```

**Khác Shop App:** có reservation HELD và allocation ghi rõ đang giữ type/sector/seat nào. SEATED còn có `OrderItemSeat` nối item với ghế. Các helper `addItem/addAllocation/addSeatAssignment` gắn cả hai chiều quan hệ để cascade PERSIST lưu child.

## 7. Commit và trả kết quả

[Impl, dòng 158](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:158) kiểm `limit.held + limit.purchased + quantity <= purchaseLimit`, rồi lưu order PENDING_PAYMENT và reservation HELD. Sau khi giữ inventory, tăng limit.held, lưu receipt liên kết orderId và map response.

`save()` chưa phải commit. Lỗi ở bất kỳ bước ghi/flush/commit nào phải rollback toàn bộ order, items, reservation, allocations, inventory, hạn mức và receipt của lần tạo đó. Controller trả 201 sau khi service proxy commit thành công.

**Giới hạn hiện tại:** chỉ tạo giữ chỗ; chưa thanh toán, áp promotion hoặc phát vé. `expiresAt` chỉ lưu thời hạn: phần reservation chưa có worker tự nhả vé khi tới giờ. Lượt HTTP trước đã kiểm tạo đơn, replay, key conflict, quantity sai, vượt hạn mức và thiếu config; chưa chứng minh chịu tải hoặc nhánh hết inventory.

Mẫu đối chiếu: [Shop App OrderService](C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/services/impl/OrderService.java:29), [OrderDetailService](C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/services/impl/OrderDetailService.java:31). Pattern Controller → Service → Repository, constructor injection, setter và CRUD thông thường giữ cách viết quen thuộc, không giải thích lại ở đây.
