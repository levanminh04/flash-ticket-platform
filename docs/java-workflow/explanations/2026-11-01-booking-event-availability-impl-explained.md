# Logic API đọc availability của Booking

Implementation tại `D:/DATN/flash-ticket-platform`, branch `dev/son`, ngày 01/11/2026.
Flow: GET public → service đọc một snapshot DB → tính số còn theo loại tài nguyên → trả Availability.

## Controller gọi service và chặn lưu cache

[AvailabilityController.java](../../../services/booking-service/src/main/java/com/flashticket/booking_service/availability/controller/AvailabilityController.java#L20)

```java
@GetMapping
public ResponseEntity<Availability> getAvailability(@PathVariable UUID eventId) {
  return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .body(availabilityService.getAvailability(eventId));
}
```

Base route là `/api/v1/events/{eventId}/availability`. Tái sử dụng rule public trong
SecurityConfig: không cần JWT, nhưng bearer token được gửi lên vẫn phải hợp lệ.
UUID sai được global handler trả400; controller không catch và đổi lỗi nghiệp vụ.

Đối chiếu trực tiếp `controllers/OrderController.java` và `services/impl/OrderService.java`
của Shop App: giữ constructor injection, controller gọi service và service gọi repository.
**Khác luồng Shop App đã đọc:** response thành công thêm `Cache-Control: no-store`,
dùng DTO Availability có sẵn và global error handler của Booking.

## Đọc cùng một snapshot, kiểm gate của entity

[AvailabilityServiceImpl.java](../../../services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java#L61)

```java
@Override
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public Availability getAvailability(UUID eventId) {
  // Các SELECT nhìn cùng snapshot đã commit, không khóa hàng để chặn luồng đặt vé.
  var asOf = Instant.now();
```

**Khác luồng Shop App đã đọc:** service khai báo readOnly/REPEATABLE_READ để SELECT
snapshot cấu hình và inventory không trộn hai trạng thái commit khi có writer đồng thời.
Gọi qua Spring proxy từ controller; không thêm query khóa hàng snapshot/inventory.
PostgreSQL vẫn có khóa cấp bảng thông thường của SELECT; không có nghĩa là DB không khóa gì.

```java
var storedSnapshot =
    eventSalesSnapshotRepository
        .findById(eventId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_NOT_READY));
if (storedSnapshot.getEventStatus() != EventState.PUBLISHED)
  throw new BusinessException(ErrorCode.EVENT_NOT_ON_SALE);
```

Thiếu snapshot trả503; entity không PUBLISHED trả409. Hủy event có thể để JSON thương mại
cũ còn PUBLISHED, nên dùng gate entity. PUBLISHED vẫn đọc được trước/sau giờ bán;
createOrder giữ kiểm tra cửa sổ bán riêng. GET đang chạy có thể trả snapshot cũ nhất quán.
`asOf` là giờ JVM lấy trước SELECT đầu tiên, không phải version hay timestamp commit DB.

## Tính số còn và giữ thứ tự config

Service lấy ID từ configurationSnapshot hiện hành, đọc bằng `findAllById` theo nhóm,
rồi map kết quả theo ID. Duyệt config để tạo response nên không phụ thuộc thứ tự DB;
row lịch sử đã bị loại khỏi config không xuất hiện. Không thêm repository/query/helper.

| Tài nguyên | Dữ liệu đọc và cách tính |
|---|---|
| QUANTITY | Mỗi ticket type: `configuredCapacity - held - purchased` |
| STANDING | Mỗi sector dùng một pool; mọi ticket type thuộc sector nhận cùng số còn |
| SEATED | Đếm seat state AVAILABLE theo ticket type/sector; trả state của mọi ghế hiện hành |

[Nhánh seat](../../../services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java#L131):

```java
var state = inventoryBySeatId.get(seat.seatId()).getState();
seats.add(new Availability.SeatAvailability(seat.seatId(), state));
if (state == SeatState.AVAILABLE) {
  availableBySectorId.merge(seat.sectorId(), 1L, Long::sum);
  availableByTicketTypeId.merge(seat.ticketTypeId(), 1L, Long::sum);
}
```

Hidden đã được writer đồng bộ thành UNAVAILABLE; HELD/PURCHASED không được tính còn.
GET không tự trả HELD dù reservation quá hạn; worker expiry chịu trách nhiệm release.
Không save/setter entity, ghi inbox/outbox, kiểm lại allocations hay gọi worker trong GET.
DTO dùng lại nguyên trạng; số còn và sourceConfigVersion là chuỗi theo contract.
Metadata tên/giá/tọa độ thuộc cấu hình/API Event, không được bổ sung vào response này.

## Bằng chứng và giới hạn

5 MVC test và8 IT mới đã pass; IT đồng thời chứng minh writer commit version2/capacity20
trong khi reader đang chạy vẫn trả version1/10, reader sau đó trả version2/20.
20 ca curl thật đã pass, gồm giữ/chốt/hủy vé, mixed inventory và GET không ghi DB.
Case thanh toán cũ từng lỗi timestamp đã pass6 lượt focused sau chỉnh môi trường local.
Full regression local trước đó chưa xanh; WSL vẫn có bước nhảy giờ nhỏ. CI đầy đủ và
deploy của commit bàn giao phải được xác nhận bằng run tương ứng, không suy từ focused pass.
Chi tiết kiểm chứng và cách chạy lại nằm trong [TESTING.md](../../../services/booking-service/TESTING.md).
