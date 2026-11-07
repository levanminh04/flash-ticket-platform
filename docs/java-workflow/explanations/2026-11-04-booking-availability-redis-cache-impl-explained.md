# Logic Redis cache cho GET availability

Code ngày 04/11/2026, triển khai tại branch `codex/booking-availability-redis-cache`, checkout `D:/DATN/flash-ticket-platform-redis-cache`; bản bàn giao đưa riêng task này lên `origin/dev/son`.
Flow đọc: GET theo eventId → đọc generation → đọc JSON Redis → miss thì đọc DB và cache 15 phút.
Flow ghi: cập nhật inventory/config/gate trong transaction → publish event → commit → đổi generation → GET tiếp theo nạp cache mới.

## 1. Controller gọi cache, service DB vẫn giữ nghiệp vụ

[AvailabilityController.java:21](/D:/DATN/flash-ticket-platform-redis-cache/services/booking-service/src/main/java/com/flashticket/booking_service/availability/controller/AvailabilityController.java:21)

```java
return ResponseEntity.ok()
    .cacheControl(CacheControl.noStore())
    .body(availabilityCacheService.getAvailability(eventId));
```

`no-store` áp dụng cho HTTP client/proxy; cache Redis nằm bên trong Booking. Cache miss gọi service DB qua Spring proxy, giữ transaction `readOnly`, `REPEATABLE_READ` và các lỗi `CONFIG_NOT_READY`/`EVENT_NOT_ON_SALE` tại [AvailabilityServiceImpl.java:65](/D:/DATN/flash-ticket-platform-redis-cache/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:65).

Đã đối chiếu source Shop App local: [ProductController.java:34](/C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/controllers/ProductController.java:34), [ProductService.java:22](/C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/services/impl/ProductService.java:22) và [JwtTokenUtil.java:23](/C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/components/JwtTokenUtil.java:23). Giữ constructor injection và field `@Value` theo mẫu. **Khác Shop App:** so với luồng Product GET đã đọc, Booking thêm service cache trước service DB; hai service cache/Redis là concrete class, không tạo thêm interface chỉ để phân tầng.

## 2. Generation chọn đúng phiên bản cache của event

[AvailabilityCacheService.java:39](/D:/DATN/flash-ticket-platform-redis-cache/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/AvailabilityCacheService.java:39)

```java
String generation = redisService.get(generationKey);
if (generation == null) {
  // Request khác có thể vừa khởi tạo hoặc listener vừa đổi generation.
  redisService.setIfAbsent(generationKey, UUID.randomUUID().toString());
  generation = redisService.get(generationKey);
}
```

`setIfAbsent` dùng SET NX: chỉ tạo nếu metadata chưa có; sau đó GET lại để dùng UUID thực tế nếu request khác hoặc listener vừa ghi trước. Nếu vẫn không lấy được generation, request chỉ đọc DB, không ghi cache.

Với prefix mặc định `flash-ticket:dev:booking:availability:v2:`, mỗi event có hai loại key:

| Key sau prefix | Nội dung và hạn dùng |
| --- | --- |
| `{eventId}:generation` | UUID hiện tại, không TTL; được tạo bởi reader hoặc invalidation |
| `{eventId}:data:{generation}` | JSON `Availability`, TTL mặc định 15 phút |

**Khác Shop App:** generation UUID trong key là cơ chế bổ sung cho cache inventory; đây không phải `reservationGeneration` gắn với từng lần giữ ghế. Metadata không tự hết hạn; dọn theo vòng đời event chưa thuộc task.

## 3. Hit trả DTO; miss ghi dữ liệu dưới generation đã đọc

[Đọc JSON, AvailabilityCacheService.java:50](/D:/DATN/flash-ticket-platform-redis-cache/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/AvailabilityCacheService.java:50)

```java
Availability cachedAvailability = jsonMapper.readValue(cachedJson, Availability.class);
if (cachedAvailability != null) return cachedAvailability;
```

Hit trả DTO và `asOf` đã cache, không gọi service DB hoặc gia hạn TTL. Miss gọi DB trước, chỉ khi thành công mới ghi JSON tại [AvailabilityCacheService.java:72](/D:/DATN/flash-ticket-platform-redis-cache/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/AvailabilityCacheService.java:72):

```java
Availability availability = availabilityService.getAvailability(eventId);
if (dataKey != null) {
  try {
    // Giữ generation lấy trước khi đọc DB để reader cũ không nạp lại cache mới.
    redisService.set(dataKey, jsonMapper.writeValueAsString(availability), cacheTtl);
```

**Khác Shop App:** dùng `JsonMapper` Jackson 3 sẵn có của Booking để lưu/đọc DTO cache. [RedisService.java:13](/D:/DATN/flash-ticket-platform-redis-cache/services/booking-service/src/main/java/com/flashticket/booking_service/redis/service/RedisService.java:13) chỉ gom get, hai overload set, setIfAbsent và clear một key; key, TTL, JSON và fallback thuộc service nghiệp vụ. SET JSON kèm TTL trong một thao tác Redis.

Ví dụ chống **stale refill** — request cũ ghi dữ liệu đã lỗi thời trở lại cache:

1. Reader A lấy G1 rồi bắt đầu đọc DB; kết quả đang đọc là ghế AVAILABLE.
2. Writer giữ ghế thành HELD, commit; listener đổi metadata sang G2.
3. A chạy tiếp chỉ ghi `data:G1`. GET bắt đầu sau invalidation đọc G2, miss và lấy trạng thái HELD từ DB.

JSON G1 vẫn có thể tồn tại tới TTL, nhưng GET dùng G2 không đọc nó. Request A đang chạy vẫn có thể trả kết quả cũ cho chính nó; cơ chế này ngăn kết quả đó trở thành cache của G2. Khóa inventory khi đặt vé vẫn bảo vệ ghi DB, không tự ngăn bước SET Redis xảy ra muộn của reader A.

## 4. Writer phát event; listener chỉ invalidation sau commit

Ví dụ giữ ghế tại [OrderServiceImpl.java:1726](/D:/DATN/flash-ticket-platform-redis-cache/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:1726):

```java
seatInventoryRepository.saveAll(lockedSeats);
// Listener chỉ vô hiệu hóa cache sau khi transaction bao ngoài commit thành công.
applicationEventPublisher.publishEvent(new AvailabilityChangedEvent(order.getEventId()));
```

`ApplicationEventPublisher` là dependency Spring cung cấp. [AvailabilityChangedEvent.java:9](/D:/DATN/flash-ticket-platform-redis-cache/services/booking-service/src/main/java/com/flashticket/booking_service/availability/event/AvailabilityChangedEvent.java:9) là thông báo nội bộ chỉ mang `eventId` cần invalidation; không mang entity JPA hoặc dữ liệu inventory.

[Listener, AvailabilityCacheService.java:87](/D:/DATN/flash-ticket-platform-redis-cache/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/AvailabilityCacheService.java:87)

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
public void invalidateAvailabilityCache(AvailabilityChangedEvent event) {
```

**Khác Shop App:** so với luồng Product CRUD đã đối chiếu, thêm Spring transaction-bound event. `saveAll()` chưa phải commit; listener chờ transaction chứa lần publish thành công rồi SET UUID mới vào generation key của đúng event, không rebuild JSON. Rollback hoặc publish ngoài transaction không chạy callback này; commit bảng khác mà không publish `AvailabilityChangedEvent` cũng không tự kích hoạt nó.

| Nơi phát event hiện có | Luồng cùng sử dụng |
| --- | --- |
| `reserveQuantityTickets` / `reserveStandingTickets` / `reserveSeatedTickets` | Tạo order, giữ inventory |
| `update*InventoryForPaymentDecision` | Chốt mua AcceptPayment; nhả HELD khi buyer hủy, expiry hoặc hủy event |
| `releasePurchased*Inventory` | Trả COMMITTED khi issuance FAILED hoặc hủy event |
| [synchronizeSalesConfiguration:286](/D:/DATN/flash-ticket-platform-redis-cache/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:286) / [processEventCancellation:211](/D:/DATN/flash-ticket-platform-redis-cache/services/booking-service/src/main/java/com/flashticket/booking_service/availability/service/impl/AvailabilityServiceImpl.java:211) | Apply config mới; tạo/đóng gate CANCELLED; version cũ/replay không phát lại |

Các helper tiếp tục dùng guard, khóa và transaction của writer cũ. Event phát trước khi transaction kết thúc: lỗi ở bước sau vẫn rollback DB và không đổi generation. Listener cache không thay cơ chế inbox/idempotency hoặc AUTO ACK của consumer; gate hủy event commit không đồng nghĩa worker đã trả hết mọi order.

## 5. TTL và lỗi cache

[Config Server YAML:5](/D:/DATN/flash-ticket-platform-redis-cache/infrastructure/config-server/src/main/resources/config/booking-service.yaml:5) nhận `BOOKING_AVAILABILITY_CACHE_TTL` (mặc định `PT15M`, có thể đặt `PT10M`) và `BOOKING_AVAILABILITY_CACHE_KEY_PREFIX`. TTL bằng 0/âm bỏ qua Redis. Dùng prefix riêng cho mỗi môi trường chia sẻ Redis.

| Nhánh lỗi trong AvailabilityCacheService | Kết quả |
| --- | --- |
| Redis đọc/khởi tạo/xóa key lỗi | Log WARN, đọc DB, bỏ SET cache trong request đó |
| JSON sai hoặc giải mã thành null | Xóa đúng dataKey rồi đọc DB và cache lại; xóa lỗi thì bỏ SET |
| Serialize/SET JSON lỗi | Vẫn trả DTO DB đã đọc thành công |
| BusinessException/lỗi DB | Đi ra ngoài như cũ; không cache response lỗi |
| SET generation sau commit lỗi | Log WARN; DB đã commit nên không thể rollback bằng lỗi Redis |

**Khác Shop App:** fallback cache được tách khỏi lỗi nghiệp vụ/DB, thay vì áp dụng cách catch mọi Exception tại một số controller mẫu. Invalidation hiện chạy trong process, không có outbox/retry bền: crash sau commit hoặc Redis lỗi lúc invalidation có thể để cache cũ được sử dụng tới khi key hết TTL.

## 6. Bằng chứng đã có

[TESTING.md:3](/D:/DATN/flash-ticket-platform-redis-cache/services/booking-service/TESTING.md:3) ghi **395 unit/MVC + 140 PostgreSQL integration pass**, không lỗi/bỏ qua; Spotless pass, SpotBugs 0 findings. Unit test xác nhận hit không gọi service DB và điều phối race G1/G2; integration dùng transaction thật, Redis mock để kiểm commit/rollback và writer của cả ba kiểu bán.

[Runtime report](/D:/DATN/flash-ticket-platform-redis-cache/services/booking-service/target/availability-curl/results.json) ghi **27/27 ca pass** trên JAR thật với PostgreSQL/RabbitMQ/Redis/Keycloak riêng: user BUYER đặt/hủy, cache 10 → 8 → 10, TTL không gia hạn, generation đổi dù JSON cũ còn. Đây là test HTTP, chưa test UI/server119 hoặc Saga Payment/Ticket E2E. Lượt viết tài liệu chỉ đọc lại source/report, không chạy lại suite hay gọi API live.
