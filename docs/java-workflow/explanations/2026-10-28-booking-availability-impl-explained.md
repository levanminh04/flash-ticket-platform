# Logic đồng bộ cấu hình bán vé của Booking

Đối chiếu code `dev/son` ngày 28/10/2026. Các đoạn dưới trích code thật, lược comment để dễ đọc. Đây là luồng nhận cấu hình; chưa phải luồng đặt vé.

## 1. Kiểm message

[Impl, dòng 62–71](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:62)

```java
if (message == null || !validator.validate(message).isEmpty())
  throw new BusinessException(ErrorCode.INVALID_MESSAGE);

SalesConfiguration configuration = message.data();

if (!"event.sales-configured.v1".equals(message.messageType())
    || !message.aggregateId().equals(configuration.eventId())
    || !message.aggregateVersion().equals(configuration.sourceConfigVersion()))
  throw new BusinessException(ErrorCode.INVALID_MESSAGE);
```

Kiểm các field và quan hệ loại vé–sector–ghế bằng Validator. Sau đó kiểm loại message, eventId và version ngoài envelope khớp `data`. Sai thì dừng bằng `INVALID_MESSAGE`.

## 2. Tính hash để so nội dung

[Impl, dòng 148–151](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:148)

```java
return HexFormat.of()
    .formatHex(
        MessageDigest.getInstance("SHA-256")
            .digest(jsonMapper.writeValueAsBytes(configuration)));
```

Serialize config thành JSON rồi tính SHA-256. Hash chỉ bao gồm `data`, không gồm metadata envelope; đổi thứ tự phần tử list cũng có thể đổi hash. Nó dùng phát hiện nội dung khác nhau, không phải khóa DB.

## 3. Đọc snapshot có khóa

[Impl, dòng 79–80](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:79)

```java
EventSalesSnapshot snapshot =
    eventSalesSnapshotRepository.findByEventId(configuration.eventId()).orElse(null);
```

Repository khai báo [khóa tại dòng 11–12](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/repository/EventSalesSnapshotRepository.java:11):

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<EventSalesSnapshot> findByEventId(UUID eventId);
```

Snapshot đã có thì khóa row để các transaction sửa cùng event phải chờ nhau. Chưa có row thì vẫn có thể đụng INSERT; PK chặn bản trùng.

## 4. Kiểm message đã xử lý chưa

[Impl, dòng 82–93](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:82)

```java
var inboxId = new InboxMessageId();
inboxId.setConsumerName("booking.sales-configured");
inboxId.setMessageId(message.messageId());

var existingInbox = inboxMessageRepository.findById(inboxId);
if (existingInbox.isPresent()) {
  if (!hash.equals(existingInbox.get().getPayloadHash()))
    throw new BusinessException(ErrorCode.MESSAGE_ID_CONFLICT);
  return;
}
```

Inbox dùng cặp `consumerName + messageId`. Đã có cùng hash thì return, không đồng bộ lại; khác hash thì `MESSAGE_ID_CONFLICT`. Kiểm inbox sau khi lấy khóa snapshot để tránh dùng kết quả đọc trước lúc chờ khóa.

## 5. Chọn cấu hình được cập nhật

[Impl, dòng 96–105](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:96)

```java
if (snapshot != null
    && sourceConfigVersion == snapshot.getSourceConfigVersion()
    && !hash.equals(calculateConfigurationHash(snapshot.getConfigurationSnapshot())))
  throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);

if (snapshot == null || sourceConfigVersion > snapshot.getSourceConfigVersion()) {
  SalesConfiguration previousConfiguration =
      snapshot == null ? null : snapshot.getConfigurationSnapshot();
```

| Cấu hình nhận được | Xử lý khi chưa có inbox |
| --- | --- |
| Chưa có snapshot hoặc version mới hơn | Đồng bộ snapshot và inventory, rồi lưu inbox |
| Cùng version, cùng hash | Chỉ lưu inbox |
| Cùng version, khác hash | Conflict |
| Version cũ hơn | Chỉ lưu inbox; không ghi đè cấu hình mới |

`previousConfiguration` giữ config trước khi thay đổi, để các helper nhận ra ID vừa bị bỏ.

## 6. Chặn thay đổi không hợp lệ rồi lưu snapshot

[Impl, dòng 108–116](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:108)

```java
if (snapshot == null) {
  snapshot = new EventSalesSnapshot();
  snapshot.setEventId(configuration.eventId());
} else if (!snapshot.getOrganizerSubject().equals(configuration.organizerSubject())
    || snapshot.getSalesMode() != configuration.mode()
    || (snapshot.getEventStatus() == EventState.CANCELLED
        && configuration.eventStatus() != SalesConfiguration.EventStatus.CANCELLED)) {
  throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
}
```

Event mới thì tạo snapshot. Event đã có không được đổi chủ, đổi mode hoặc mở lại khi đã CANCELLED. Guard này chạy trong nhánh cập nhật version mới hơn.

[Impl, dòng 123–132](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:123)

```java
snapshot.setConfigurationSnapshot(configuration);
snapshot.setSourceConfigVersion(sourceConfigVersion);
snapshot.setReceivedAt(Instant.now());
eventSalesSnapshotRepository.save(snapshot);
synchronizeTicketTypeInventory(previousConfiguration, configuration, sourceConfigVersion);
synchronizeSectorInventory(previousConfiguration, configuration, sourceConfigVersion);
synchronizeSeatInventory(previousConfiguration, configuration, sourceConfigVersion);
synchronizePromotionInventory(previousConfiguration, configuration, sourceConfigVersion);
```

Sau khi gán các field snapshot, lưu config và gọi bốn helper. Helper tự bỏ qua mode không phù hợp. `save()` chưa có nghĩa đã commit.

## 7. Map giúp lấy lại inventory cũ

[Impl, dòng 163–173](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:163)

```java
Set<UUID> ids = new HashSet<>();
configuration.ticketTypes().forEach(type -> ids.add(type.ticketTypeId()));
if (previousConfiguration != null)
  previousConfiguration.ticketTypes().forEach(type -> ids.add(type.ticketTypeId()));
Map<UUID, TicketTypeInventory> existing = new HashMap<>();
for (TicketTypeInventory inventory : ticketTypeInventoryRepository.findAllById(ids)) {
  if (!configuration.eventId().equals(inventory.getEventId()))
    throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
  existing.put(inventory.getTicketTypeId(), inventory);
}
```

Gom ID cũ/mới để đọc row đã có. Row thuộc event khác thì conflict. Map có dạng `ticketTypeId → entity DB`, dùng tra lại entity khi duyệt config mới; không query DB lần nữa.

[Impl, dòng 176–184](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:176)

```java
TicketTypeInventory inventory = existing.get(type.ticketTypeId());
if (inventory == null) {
  inventory = new TicketTypeInventory();
  inventory.setTicketTypeId(type.ticketTypeId());
  inventory.setEventId(configuration.eventId());
  inventory.setHeld(0L);
  inventory.setPurchased(0L);
}
```

Có trong map thì giữ entity và counter cũ. Chưa có thì tạo mới với `held = purchased = 0`. Các helper sector/seat/promotion cũng dùng cách tra map này.

## 8. Ticket type và sector: cập nhật capacity

[Impl, dòng 185–192](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:185)

```java
long capacity = Long.parseLong(type.configuredQuantity());
if (inventory.getPurchased() > capacity
    || inventory.getHeld() > capacity - inventory.getPurchased())
  throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
inventory.setConfiguredCapacity(capacity);
inventory.setSourceConfigVersion(sourceConfigVersion);
inventories.add(inventory);
```

Ticket type helper chỉ chạy cho QUANTITY. Capacity mới phải đủ `purchased + held`; code dùng phép trừ để tránh tràn số. Ví dụ đã bán 7, đang giữ 3: capacity 120 hợp lệ, 9 thì conflict. Counter cũ không reset.

Sector helper chạy cho SEAT_MAP và thêm guard:

[Impl, dòng 229–230](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:229)

```java
if (inventory.getSectorType() != sector.kind())
  throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
```

Không đổi STANDING ↔ SEATED. Sector cũng kiểm capacity đủ held + purchased, rồi cập nhật capacity và source version. Capacity lấy từ config, không tự đếm ghế visible.

Type/sector bị bỏ khỏi config mới vẫn giữ nguyên row cũ. Các list cần ghi được sort theo ID rồi `saveAll` trong transaction hiện tại.

## 9. Ghế: giữ trạng thái đang sử dụng

[Impl, dòng 271–282](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:271)

```java
boolean occupied =
    inventory.getState() == SeatState.HELD || inventory.getState() == SeatState.PURCHASED;
if (occupied) {
  if (seat.isHidden()
      || !Objects.equals(inventory.getSectorId(), seat.sectorId())
      || !Objects.equals(inventory.getTicketTypeId(), seat.ticketTypeId()))
    throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
} else {
  inventory.setState(seat.isHidden() ? SeatState.UNAVAILABLE : SeatState.AVAILABLE);
}
```

Ghế HELD/PURCHASED giữ state, không cho ẩn, chuyển sector hoặc đổi loại vé. Ghế còn lại: hidden thì UNAVAILABLE, không hidden thì AVAILABLE. Code sau đó cập nhật label và các field cấu hình; không reset reservationId/generation của row cũ.

[Impl, dòng 291–299](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:291)

```java
for (SeatInventory inventory : existing.values()) {
  if (currentIds.contains(inventory.getSeatId())) continue;
  if (inventory.getState() == SeatState.HELD || inventory.getState() == SeatState.PURCHASED)
    throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
  inventory.setState(SeatState.UNAVAILABLE);
  inventory.setSourceConfigVersion(sourceConfigVersion);
  inventories.add(inventory);
}
```

Ghế không còn trong config mới: đang giữ/đã bán thì conflict; còn lại chuyển UNAVAILABLE và giữ row. Nhánh này không tự set `isHidden = true`.

## 10. Promotion: giữ counter và bảo vệ lượt đang giữ

[Impl, dòng 330–337](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:330)

```java
} else if (inventory.getHeld() > 0
    && !promotion.equals(inventory.getConfigurationSnapshot().configuration())) {
  throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
}
long limit = Long.parseLong(promotion.maximumUses());
if (inventory.getUsed() > limit || inventory.getHeld() > limit - inventory.getUsed())
  throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
```

Với row đã có, nếu `held > 0` thì không cho đổi bất kỳ field nào làm DTO promotion khác cấu hình cũ. Limit phải đủ `used + held`. Row mới khởi tạo held/used bằng 0; row cũ giữ counter, cập nhật cấu hình và source version.

[Impl, dòng 346–350](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:346)

```java
for (PromotionInventory inventory : existing.values()) {
  if (!currentIds.contains(inventory.getPromotionId()) && inventory.getHeld() > 0)
    throw new BusinessException(ErrorCode.SALES_CONFIGURATION_CONFLICT);
}
```

Bỏ promotion khi còn lượt giữ thì conflict. Không còn lượt giữ thì cho bỏ khỏi snapshot, giữ nguyên row DB. Helper chạy cho cả hai sales mode.

## 11. Lưu inbox và hoàn tất transaction

[Impl, dòng 136–141](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:136)

```java
var inbox = new InboxMessage();
inbox.setId(inboxId);
inbox.setPayloadHash(hash);
inbox.setProcessedAt(Instant.now());
inboxMessageRepository.save(inbox);
```

Inbox được lưu cùng transaction với snapshot/inventory/promotion. Lỗi làm transaction thất bại thì rollback toàn bộ thay đổi của lần xử lý, kể cả lỗi xuất hiện lúc flush/commit.

`sourceConfigVersion` quyết định config mới/cũ; `rowVersion` do JPA quản lý để phát hiện ghi cạnh tranh. Snapshot/inbox mới giữ `rowVersion = null` để repository nhận biết INSERT dù ID đã được gán.

## 12. Listener quyết định ACK hay reject

[Listener, dòng 19–37](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/messaging/SalesConfigurationListener.java:19):

```java
@RabbitListener(queues = "booking.sales-configured.q", ackMode = "AUTO", concurrency = "1")
public void handleSalesConfiguredEvent(Envelope<SalesConfiguration> message) {
  try {
    availabilityService.synchronizeSalesConfiguration(message);
  } catch (BusinessException exception) {
    ErrorCode code = exception.getErrorCode();
    if (code == ErrorCode.INVALID_MESSAGE
        || code == ErrorCode.MESSAGE_ID_CONFLICT
        || code == ErrorCode.SALES_CONFIGURATION_CONFLICT) {
      throw new AmqpRejectAndDontRequeueException(code.name(), exception);
    }
    throw exception;
  }
}
```

Trong đường gọi hiện tại, service transaction commit trước khi trả về listener; listener thành công thì Spring AUTO ACK. Ba mã lỗi trên bị reject không requeue, tới DLQ nếu topology broker tương ứng được áp dụng. Lỗi khác đi ra container xử lý theo cấu hình; service không tự retry/backoff.

Nếu DB đã commit nhưng mất kết nối trước ACK, broker có thể giao lại; inbox cùng hash giúp bỏ qua việc cập nhật lần nữa. Row type/sector/promotion bị bỏ vẫn còn trong DB, nên luồng bán vé phải đối chiếu snapshot hiện hành.
