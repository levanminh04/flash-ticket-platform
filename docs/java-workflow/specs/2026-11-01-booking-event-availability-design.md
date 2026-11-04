# API GET thông tin ghế theo eventId

- Ngày: 2026-11-01. Phiên bản: v0.1.
- Pha: BRAINSTORMING. Trạng thái thiết kế và danh sách C/M: **PENDING**.
- Task: [API GET thông tin ghế theo eventId](https://app.clickup.com/t/z8qgvc9wfq).
- User đã chọn riêng phạm vi payload: **“Theo contract Availability hiện có”**, qua phản hồi câu hỏi ngày 2026-11-01. Lựa chọn này chưa phải duyệt toàn bộ thiết kế hoặc cho phép viết Java.
- Nguồn xác nhận thiết kế C1–C9, M1–M17 và các quyết định dưới đây: chưa có.

## 1. Mục tiêu và hiện trạng

Buyer/frontend đọc trạng thái nguồn cung theo eventId để hiển thị số vé còn và trạng thái từng ghế trước khi đặt vé. Dùng nguyên DTO `Availability` của B13; không bổ sung tên ghế, hàng, giá hoặc tọa độ. Metadata và sơ đồ thuộc API Event.

`AvailabilityController` hiện mới có base route và dependency. `AvailabilityService` mới có xử lý đồng bộ cấu hình/hủy event; chưa có hàm đọc. DTO, entity, repository và mapping lỗi đã tồn tại. `SecurityConfig` đã cho phép GET availability công khai.

Chỉ bổ sung đường đọc Booking. Không giữ/nhả ghế, tạo order, xử lý expiry, thay đổi consumer hoặc schema. Đây là spec, chưa phải plan triển khai hoặc kết quả kiểm thử.

## 2. Contract

`GET /api/v1/events/{eventId}/availability`

- Path variable `eventId`: UUID. Không có request body hoặc phân trang.
- Public, không yêu cầu JWT. Nếu caller gửi bearer token không hợp lệ, giữ nguyên hành vi resource server trả 401.
- Thành công: HTTP 200, JSON `Availability`, `Cache-Control: no-store` để frontend/proxy không tái sử dụng tồn kho cũ.
- Không thêm endpoint alias hoặc wrapper response.

Ví dụ SEAT_MAP có một sector SEATED và một ghế còn trống:

```json
{
  "eventId": "00000000-0000-0000-0000-000000000001",
  "sourceConfigVersion": "3",
  "asOf": "2026-11-01T07:00:00Z",
  "ticketTypes": [
    {"ticketTypeId": "00000000-0000-0000-0000-000000000002", "available": "1"}
  ],
  "sectors": [
    {"sectorId": "00000000-0000-0000-0000-000000000003", "kind": "SEATED", "available": "1"}
  ],
  "seats": [
    {"seatId": "00000000-0000-0000-0000-000000000004", "state": "AVAILABLE"}
  ]
}
```

Version và số lượng dùng chuỗi số theo DTO hiện có; số lượng được bằng `"0"`. Các mảng luôn khác null. `asOf` lấy `Instant.now()` ngay trước truy vấn snapshot đầu tiên, biểu thị thời điểm bắt đầu lấy dữ liệu; không phải thời gian commit DB hoặc cam kết giữ chỗ.

**Đề xuất nghiệp vụ PENDING:** chỉ trả 200 khi `EventSalesSnapshot.eventStatus = PUBLISHED`. Event đã công bố được xem tồn kho cả trước giờ mở bán và sau giờ đóng bán; API này không biểu thị quyền mua. POST tạo order tiếp tục kiểm sale window như hiện tại. Event chưa công bố hoặc CANCELLED trả 409 `EVENT_NOT_ON_SALE`; không công khai cấu hình nháp hoặc quảng bá ghế vừa được worker trả sau hủy event.

## 3. Luồng đọc và tính số lượng

1. Controller nhận UUID, gọi `AvailabilityService.getAvailability(eventId)` qua Spring proxy.
2. Service bắt đầu transaction chỉ đọc; dùng `EventSalesSnapshotRepository.findById(eventId)` có sẵn. Không dùng `findByEventId` đang có `PESSIMISTIC_WRITE`, hoặc query phục vụ đặt vé có `PESSIMISTIC_READ`.
3. Không có snapshot: ném `BusinessException(CONFIG_NOT_READY)`. Có snapshot nhưng không PUBLISHED: ném `BusinessException(EVENT_NOT_ON_SALE)`. Dùng trạng thái trên entity; cancellation có thể chỉ cập nhật gate, giữ nguyên JSON cấu hình thương mại cũ.
4. Lấy cấu hình hiện hành từ `configurationSnapshot`. Chỉ lấy ID tài nguyên xuất hiện trong cấu hình này; dùng `findAllById` sẵn có ở repository inventory tương ứng, bỏ qua nhóm ID rỗng. Không truy vấn từng ghế hoặc gọi sang Event.
5. Tính số lượng theo bảng dưới, tạo trực tiếp DTO `Availability` và các nested DTO bằng constructor hiện có.
6. Ghép theo ID và trả theo thứ tự các danh sách cấu hình; không dựa vào thứ tự kết quả `findAllById`. Tài nguyên đã bị loại khỏi cấu hình không xuất hiện dù row lịch sử còn trong DB.
7. Trả response; không gọi save/saveAll, setter entity, gửi message hoặc cập nhật inbox/outbox.

| Nguồn cung | ticketTypes.available | sectors.available | seats |
|---|---|---|---|
| QUANTITY | `configuredCapacity - held - purchased` của từng TicketTypeInventory | Mảng rỗng | Mảng rỗng |
| STANDING trong SEAT_MAP | Mỗi loại vé cùng sector nhận số còn của **cùng một pool sector** | `configuredCapacity - held - purchased` của SectorInventory | Không có ghế cho sector standing |
| SEATED trong SEAT_MAP | Đếm ghế hiện hành có state AVAILABLE của ticket type | Đếm ghế hiện hành có state AVAILABLE của sector | Trả seatId/state thực của các ghế hiện hành, kể cả HELD/PURCHASED/UNAVAILABLE |

Một event SEAT_MAP có thể chứa cả STANDING và SEATED. Không cộng các số còn của ticket type standing để suy ra tổng sector; chúng cùng tham chiếu một nguồn cung. Với seated, không trừ thêm counter sector như một pool thứ hai. Ghế hidden đã được writer đặt UNAVAILABLE và không được tính còn trống.

Reservation đã quá hạn nhưng worker chưa commit release vẫn biểu diễn HELD. GET không tự nhả ghế hoặc suy trạng thái từ đồng hồ. Số lượng là ảnh chụp để hiển thị; POST tạo order mới quyết định giữ chỗ thành công hay trả lỗi cạnh tranh.

## 4. Transaction, validation và lỗi

**Đề xuất kỹ thuật PENDING:** `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)` trên method service implementation. Các SELECT trong một request nhìn cùng một snapshot đã commit của PostgreSQL; không lấy pessimistic row lock, không chặn giữ chỗ bằng khóa ghi snapshot. `readOnly` riêng lẻ không bảo đảm nhiều query nhìn cùng dữ liệu ở READ_COMMITTED và cũng không phải cơ chế cấm ghi tuyệt đối. Vì vậy thân GET chỉ chứa logic đọc.

Lý do cần mức isolation này: consumer commit cấu hình và inventory cùng transaction; các luồng đặt/hủy/thanh toán cũng thay inventory đồng thời. Nếu nhiều SELECT đọc các thời điểm khác nhau, response có thể ghép snapshot phiên bản cũ với inventory mới. Dùng transaction đọc nhất quán thay vì thêm lock hoặc cache.

Nguồn đối chiếu: [PostgreSQL Repeatable Read](https://www.postgresql.org/docs/17/transaction-iso.html#XACT-REPEATABLE-READ), [Spring Data JPA transaction tại service](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html), [SimpleJpaRepository nguồn mở](https://github.com/spring-projects/spring-data-jpa/blob/main/spring-data-jpa/src/main/java/org/springframework/data/jpa/repository/support/SimpleJpaRepository.java). Áp dụng transaction bao quanh unit of work và query repository sẵn có; không sao chép framework hoặc viết EntityManager riêng.

Không bổ sung validator/helper để kiểm lại toàn bộ snapshot, ownership, uniqueness hoặc capacity. `synchronizeSalesConfiguration` và các `synchronize*Inventory` đã validate, bảo toàn held/purchased và commit snapshot/inventory cùng transaction. Khi release sau hủy event, gate CANCELLED vẫn chặn GET trả nguồn cung mua được. Không thêm kiểm tra chỉ để đối phó sửa DB thủ công, cũng không đổi số âm/lỗi dữ liệu thành số 0 giả.

| Tình huống | HTTP / mã lỗi | Xử lý |
|---|---|---|
| eventId không phải UUID | 400 / INVALID_REQUEST | Binding và GlobalExceptionHandler hiện có; không tạo validator UUID |
| Snapshot chưa được đồng bộ hoặc eventId chưa biết tại Booking | 503 / CONFIG_NOT_READY | Không suy luận event không tồn tại ở Event; không đổi thành 404 |
| Snapshot chưa PUBLISHED hoặc đã CANCELLED | 409 / EVENT_NOT_ON_SALE | Gate đọc đề xuất ở §2; không gọi worker release |
| Caller gửi bearer không hợp lệ | 401 theo security hiện có | Không tắt xác thực token trên route public |
| Lỗi DB/hệ thống | Theo handler và phân loại hiện có | Không catch Exception rồi trả 200 hoặc lộ message nội bộ |

Không trả configurationSnapshot đầy đủ, reservationId, generation, buyerSubject, dữ liệu promotion hoặc thông tin nội bộ.

## 5. Đối chiếu Shop App và convention

Mẫu đã đọc tại `C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp`:

- `controllers/OrderController.java`: controller nhận request rồi trả ResponseEntity từ service. Áp dụng vào C1/M1; dependency dùng constructor injection với final field và `@RequiredArgsConstructor` sẵn có.
- `services/IOrderService.java` và `services/impl/OrderService.java`: khai báo use case ở service, xử lý nghiệp vụ trong implementation. Áp dụng M2/M3; giữ tên interface `AvailabilityService` của dự án, không tạo interface thứ hai.
- `repositories/OrderRepository.java`: dùng Spring Data repository. GET dùng nguyên `findById`/`findAllById`; không thêm query, native SQL hoặc repository.
- Khác mẫu cần duyệt: isolation REPEATABLE_READ; DTO Availability/B13 và BusinessException/GlobalExceptionHandler của Booking; cache header; test MVC/Testcontainers theo hạ tầng hiện có. Không sao chép catch rộng/return null từ code mẫu.

Áp dụng `common.md`: tạo đúng ba khai báo production M1–M3 vì chưa có đường GET; không tách mapper/validator/helper, không thêm dependency/ModelMapper. Mapping trực tiếp bằng DTO constructor. Dùng tên biến rõ nội dung như `inventoryByTicketTypeId`, `availableBySectorId`; không gọi entity đọc không khóa là `locked...`. Thêm comment tiếng Việt ngắn giải thích transaction nhất quán và pool standing chung.

Áp dụng convention Flash Ticket: giữ hai cấp SalesMode/SectorType; STANDING dùng pool chung, SEATED theo từng ghế; không đổi AUTO ACK, write locks, gate cancellation hoặc workflow thanh toán. Luồng ghi đang có không được gọi từ GET và không bị tách/sửa để phục vụ GET.

## 6. Danh sách duyệt class/file — tất cả PENDING

Đường dẫn Java production tương đối với `services/booking-service/src/main/java/com/flashticket/booking_service/`; đường dẫn test tương đối với `services/booking-service/src/test/java/com/flashticket/booking_service/`.

| ID | Class/file | NEW hoặc EXISTING | Vai trò, field/annotation/API sinh | Mẫu Shop App hoặc ngoại lệ |
|---|---|---|---|---|
| C1 | availability/controller/AvailabilityController.java | EXISTING, thêm M1 | Giữ @RestController, @RequestMapping, @RequiredArgsConstructor và final AvailabilityService; không sinh thêm constructor/getter | OrderController mẫu và OrderController Booking |
| C2 | availability/service/AvailabilityService.java | EXISTING, thêm M2 | Interface use case hiện có; không thêm field/annotation | IOrderService mẫu, giữ tên dự án |
| C3 | availability/service/impl/AvailabilityServiceImpl.java | EXISTING, thêm M3 | Giữ @Service/@RequiredArgsConstructor và các dependency hiện có; không thêm field/API Lombok | OrderService mẫu; ngoại lệ transaction §4 |
| C4 | availability/controller/AvailabilityControllerTests.java (test) | NEW | Package-private class; @WebMvcTest(AvailabilityController.class), @Import({SecurityConfig.class, BookingJsonConfig.class, GlobalExceptionHandler.class}); @Autowired MockMvc mvc; @MockitoBean AvailabilityService availabilityService; @MockitoBean JwtDecoder jwtDecoder; không Lombok | Test MVC theo OrderControllerTests Booking, ngoài mẫu Shop App |
| C5 | persistence/BookingPersistenceIT.java (test) | EXISTING, thêm M10–M17 | Giữ PostgreSQL 17/Testcontainers, service proxy, transaction manager và fixtures sẵn có; không tạo config/container/helper mới | Integration test hiện có; ngoài mẫu Shop App |
| C6 | dto/response/Availability.java và 3 nested DTO | EXISTING, REUSE | Giữ nguyên @Getter/@AllArgsConstructor và field; dùng constructor/getter đã có, không sinh thêm API | Contract B13, không tạo response mới |
| C7 | availability/repository/EventSalesSnapshotRepository.java | EXISTING, REUSE | findById(UUID) kế thừa; không thêm method/@Lock | Spring Data mẫu |
| C8 | availability/repository/TicketTypeInventoryRepository.java, SectorInventoryRepository.java, SeatInventoryRepository.java | EXISTING, REUSE | findAllById(Iterable<UUID>) kế thừa; không thêm method/@Lock | Spring Data mẫu |
| C9 | config/SecurityConfig.java; exception/BusinessException.java, ErrorCode.java, GlobalExceptionHandler.java | EXISTING, REUSE | Giữ public GET rule và Problem/error mapping; không tạo class/enum/handler mới | Ngoại lệ hạ tầng Booking hiện có |

## 7. Danh sách duyệt constructor/method — tất cả PENDING

Chữ ký dưới đây là khai báo đề xuất, chưa phải Java được phép tạo. M2 public theo interface dù không cần viết từ khóa public. Không đề xuất constructor production mới; Lombok hiện tại giữ nguyên. Không có helper, method @Bean, lifecycle hoặc nested type mới.

| ID | Class | Constructor/method đầy đủ | NEW hoặc REUSE | Cách viết và lý do cần |
|---|---|---|---|---|
| M1 | C1 | public ResponseEntity<Availability> getAvailability(@PathVariable UUID eventId) | NEW | @GetMapping; gọi service, trả 200 + CacheControl.noStore(); controller hiện chưa có route handler |
| M2 | C2 | public Availability getAvailability(UUID eventId) | NEW | Khai báo use case đọc cho controller, giữ consumer methods nguyên trạng |
| M3 | C3 | public Availability getAvailability(UUID eventId) | NEW | @Override, @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ); §3–4; chưa có logic projection này để tái sử dụng |
| M4 | C4 | AvailabilityControllerTests() | NEW, constructor mặc định package-private | Không khai báo constructor riêng, không injection constructor hoặc API Lombok mới |
| M5 | C4 | void returnsPublicAvailabilityWithoutJwt() throws Exception | NEW | @Test; đúng payload B13, chuỗi counter/version, mảng, public access, no-store và không lộ field nội bộ |
| M6 | C4 | void rejectsMalformedEventId() throws Exception | NEW | @Test; 400 và service không bị gọi |
| M7 | C4 | void returnsConfigNotReadyProblem() throws Exception | NEW | @Test; 503/CONFIG_NOT_READY qua handler thật |
| M8 | C4 | void returnsEventNotOnSaleProblem() throws Exception | NEW | @Test; 409/EVENT_NOT_ON_SALE qua handler thật |
| M9 | C4 | void rejectsInvalidBearerToken() throws Exception | NEW | @Test; decoder mock từ chối token, security trả 401 |
| M10 | C5 | void availabilityReturnsQuantityCounters() | NEW | @Test, @Transactional(propagation=Propagation.NOT_SUPPORTED); QUANTITY, held/purchased và số 0 |
| M11 | C5 | void availabilityUsesSharedStandingPool() | NEW | Cùng annotation M10; nhiều ticket type tham chiếu cùng pool, không đếm tồn kho nhiều lần |
| M12 | C5 | void availabilityCountsAvailableSeatsInMixedSectors() | NEW | Cùng annotation M10; hỗn hợp standing/seated, đủ bốn state, hidden không tăng số còn |
| M13 | C5 | void availabilityExcludesRetiredAndOtherEventResources() | NEW | Cùng annotation M10; chỉ current config, thứ tự theo config dù kết quả repository không bảo đảm thứ tự |
| M14 | C5 | void availabilityDoesNotChangePersistentState() | NEW | Cùng annotation M10; reservation hết hạn vẫn HELD; counter, rowVersion, reservation, inbox/outbox không đổi |
| M15 | C5 | void availabilityRejectsMissingOrUnpublishedSnapshots() | NEW | Cùng annotation M10; snapshot thiếu 503; các trạng thái ngoài PUBLISHED, nhất là cancellation gate còn JSON PUBLISHED cũ, trả 409 |
| M16 | C5 | void availabilityRemainsReadableOutsideSaleWindow() | NEW | Cùng annotation M10; PUBLISHED trước mở/sau đóng vẫn đọc, không sửa guard POST order |
| M17 | C5 | void availabilityReadsOneCommittedSnapshotDuringConcurrentChanges() throws Exception | NEW | Cùng annotation M10; hai transaction thật, writer commit thay đổi trong lúc GET đọc; response phải nhất quán, không lấy write lock snapshot |

Test DB phải gọi service qua Spring proxy, commit fixture trong transaction riêng và cleanup bằng hạ tầng hiện có. Không để outer transaction mặc định của @DataJpaTest làm mất tác dụng isolation cần kiểm. M17 phải điều phối được thứ tự đọc/commit bằng fixture và công cụ đồng bộ hiện có; không dùng sleep ngẫu nhiên làm bằng chứng. Nếu khi viết plan phát hiện cần helper/type/test hook mới, trình đúng delta trước khi duyệt; danh sách này không cấp quyền tạo helper chưa biết tên.

## 8. Tiêu chí nghiệm thu và nguồn đối chiếu

- Public GET thực tế trả đúng DTO B13 cho QUANTITY, STANDING, SEATED và event có sector hỗn hợp.
- Có đầy đủ nhánh lỗi, gate event, số 0, hidden/retired, không lộ dữ liệu nội bộ và không có tác dụng ghi.
- Kiểm bằng PostgreSQL thật rằng response nhất quán dưới cập nhật đồng thời; không suy bảo đảm này chỉ từ mock hoặc annotation.
- Các test ghi/đặt/hủy/thanh toán Booking hiện có tiếp tục chạy đạt khi đến pha triển khai. Chưa chạy test ứng dụng trong pha spec này.

Nguồn dự án đã đối chiếu:

- `docs/architecture/B13-api-and-event-contracts.md` §3: GET Availability, public/buyer, không giữ ghế, thiếu snapshot 503, kiểu response và quy tắc pool.
- `docs/architecture/B14-main-flow-sequences.md`: transaction/khóa của luồng ghi, gate hủy event trước release; bản v0.3 còn REVIEW_READY, không coi toàn bộ đã được duyệt.
- `AvailabilityServiceImpl.synchronizeSalesConfiguration`, `synchronizeTicketTypeInventory`, `synchronizeSectorInventory`, `synchronizeSeatInventory`, `processEventCancellation`: writer giữ tính nhất quán và tài nguyên lịch sử.
- DTO `Availability`, `SalesConfiguration`, các inventory entity/repository, `SecurityConfig`, `OrderController`, `OrderControllerTests`, `BookingPersistenceIT`, `ErrorCode`, `GlobalExceptionHandler`.
- Ảnh `image/sequence/Đặt vé.png` đã gắn task ClickUp: sequence đặt vé liên quan, bắt đầu từ tạo order; chưa có sequence riêng mô tả GET này. Spec không coi ảnh đó là bằng chứng API GET đã tồn tại.

## 9. Phần chờ user duyệt

**PENDING:** C1–C9, M1–M17; gate PUBLISHED/409; đọc ngoài sale window; no-store; REPEATABLE_READ; cách test theo hạ tầng Booking. Payload hiện có đã được chọn riêng, không mở rộng field.

Theo shopapp-java-brainstorming: “Skill này phải hỏi user duyệt danh sách class/phương thức mới, kể cả đúng mẫu Shop App. Dừng chờ câu trả lời.” Chờ duyệt danh sách và thiết kế này trước khi chốt. Không tự chuyển sang plan, tạo Java/test, commit/push, deploy hoặc cập nhật ClickUp từ bản nháp này.
