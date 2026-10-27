# Plan: Booking nhận cấu hình bán vé từ Event

**Pha:** WRITING-PLANS — APPROVED; EXECUTING-PLANS — APPROVED. Đang triển khai trên dev/son.  
**Thiết kế đầu vào:** [booking-sales-config-design](2026-10-27-booking-sales-config-design.md).  
**Task:** [Tích hợp luồng tạo sự kiện vào Booking](https://app.clickup.com/t/z8qgvc7vb8).  
**Workflow:** Shop App Writing Plans + Ponytail full.  
**Root thực thi:** `D:/DATN/flash-ticket-platform`, nhánh `dev/son` theo yêu cầu user. Checkout sạch tại commit `5d6742e` trước khi chuyển hai tài liệu thiết kế/plan sang. Các file bên dưới tính từ root này. Worktree cũ có thay đổi ngoài task nên không chuyển các file khác.

## 1. Kết quả cần đạt

Nhận `Envelope<SalesConfiguration>` từ queue `booking.sales-configured.q`; validate, chống lặp, đồng bộ snapshot/inventory và ghi inbox trong cùng transaction PostgreSQL. Service commit xong mới trả về listener; container AUTO ACK sau khi listener kết thúc thành công. Exception không được nuốt rồi trả về thành công.

User đã chọn AUTO ACK, bỏ Channel/delivery tag và tên method nghiệp vụ cụ thể; đồng ý phạm vi test. User đã xác nhận luồng version cũ: bỏ qua cập nhật, ghi inbox, commit rồi AUTO ACK. `sourceConfigVersion` là version cấu hình từ Event; không thay thế `rowVersion @Version` của JPA.

**Chỉnh sửa theo yêu cầu user:** bỏ EntityManager; snapshot thêm `@Version Long rowVersion` và dùng repository. Đã kiểm tra catalog DB thật qua `.env` → `127.0.0.1:5432/booking_db`, `SET LOCAL ROLE booking_owner` trong transaction read-only: `booking_schema.tbl_event_sales_snapshots` có 10 cột, có `source_config_version`, chưa có `row_version`. Entity và DDL bootstrap cũng chưa có field/cột này. Chỉ đọc metadata, chưa chạy ALTER hoặc sửa Java.

**Cách cập nhật schema user đã chọn:** dự án chưa dùng Liquibase/Flyway; dùng `ddl-auto: update`, không thêm migration riêng. YAML Booking trong checkout này đã đổi từ `none` sang `update` theo yêu cầu trực tiếp của user. Entity version và việc khởi động để cập nhật DB vẫn thuộc pha triển khai.

## 2. Điều chỉnh tối thiểu so với draft spec — chờ duyệt trong plan

1. **Chỉ thêm một application class: `SalesConfigurationListener`.** Dùng lại `AvailabilityService`/`AvailabilityServiceImpl` vì service này đã giữ các repository snapshot/inventory. Không tạo cặp `SalesConfigurationService`/`SalesConfigurationServiceImpl` mới.
2. **Không tạo pool loại vé cho SEAT_MAP.** B14 §2 bước 4 và CHECK `reservation_allocations.resource_kind` trong `20-booking.sql` đã phân biệt ba nguồn cung:

   | Loại bán | Inventory dùng để giữ chỗ | Khởi tạo trong task này |
   |---|---|---|
   | QUANTITY | Pool `TicketTypeInventory` | Capacity từ `configuredQuantity`; held/purchased = 0 cho row mới |
   | SEAT_MAP / STANDING | Pool `SectorInventory` dùng chung giữa các type của sector | Capacity từ `SectorConfig.configuredCapacity`; không nhân capacity theo số loại vé |
   | SEAT_MAP / SEATED | Từng `SeatInventory` | Sector row làm metadata/FK; từng ghế AVAILABLE hoặc UNAVAILABLE nếu hidden |

   Với 100 ghế có 5 hidden, chỉ 95 ghế được bán lúc đầu; không cần tự tạo thêm `TicketTypeInventory.configuredCapacity` cho mode này. Type/giá/sector mapping vẫn có đầy đủ trong JSON snapshot. Đây là điều chỉnh đề xuất của plan, chưa coi câu hỏi capacity trước đó là phê duyệt.
3. Không xóa hàng loạt inventory để dựng lại snapshot. Upsert phần cấu hình được phép, giữ counter/reservation/history. Không viết framework mapper, registry handler, retry worker, outbox reply hoặc scheduler.

## 3. Nguồn đã đối chiếu

- `docs/architecture/B13-api-and-event-contracts.md` §5.1–5.2, §7.1: envelope, inbox/hash, version full-state, mapping seat hidden và giữ counter.
- `docs/architecture/B14-main-flow-sequences.md` §1, §2, §7: thứ tự khóa, ba nguồn cung, transaction và replay.
- `docs/architecture/data/postgres/20-booking.sql`: FK sector/seat, CHECK trạng thái/counter và resource_kind.
- `services/user-service/.../messaging/identity/IdentityUserCreatedListener.java`, `config/IdentityMessagingConfig.java`: AUTO ACK, service return/throw, reject không requeue cho lỗi vĩnh viễn.
- `services/booking-service/.../availability/service/AvailabilityService.java`, `impl/AvailabilityServiceImpl.java`: lớp sẵn có để tái sử dụng.
- Shop App nguồn tại `C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/services/impl/OrderService.java`, `createOrder/updateOrder`: constructor injection, gọi repository trực tiếp, setter/entity, tên method theo nghiệp vụ. Đã đọc source. Rabbit/inbox/JPA lock không phải tính năng có trong Shop App; dùng API framework sẵn có, không mô phỏng lại.

## 4. Bảng class/file cần duyệt

Prefix Java `B = services/booking-service/src/main/java/com/flashticket/booking_service/`; test `T = services/booking-service/src/test/java/com/flashticket/booking_service/`.

| ID | File | NEW / EXISTING | Vai trò, annotation/API sinh | Mẫu/ngoại lệ |
|---|---|---|---|---|
| C1 | `B/messaging/SalesConfigurationListener.java` | NEW | `@Component`, `@RequiredArgsConstructor`; field `private final AvailabilityService availabilityService`; chỉ sinh constructor, không sinh getter/setter/builder | Constructor kiểu Shop App; `@RabbitListener` theo Spring AMQP/User Service |
| C2 | `B/availability/service/AvailabilityService.java` | EXISTING | Thêm operation đồng bộ cấu hình vào interface hiện có | Interface nghiệp vụ kiểu Shop App; giữ tên/package dự án |
| C3 | `B/availability/service/impl/AvailabilityServiceImpl.java` | EXISTING | Giữ `@Service`, `@RequiredArgsConstructor`. Giữ 5 field final hiện có; thêm `PromotionInventoryRepository promotionInventoryRepository`, `InboxMessageRepository inboxMessageRepository`, `JsonMapper jsonMapper`, `Validator validator`; constructor sinh thay đổi theo M4 | Setter/repository kiểu OrderService; transaction/inbox/locking ngoài mẫu Shop App, cần cho yêu cầu đúng dữ liệu |
| C4 | `B/availability/repository/EventSalesSnapshotRepository.java` | EXISTING | Thêm derived query với `@Lock(PESSIMISTIC_WRITE)`; không native SQL | Spring Data JPA chuẩn, ngoài mẫu CRUD Shop App |
| C5 | `B/validation/ContractRules.java` | EXISTING | Bổ sung thân `validSalesConfiguration`; không thêm validation class/annotation mới | Dùng contract hiện có |
| C6 | `B/exception/ErrorCode.java` | EXISTING | Thêm `SALES_CONFIGURATION_CONFLICT`, `MESSAGE_ID_CONFLICT`; dùng lại `INVALID_MESSAGE` cho cấu trúc/input sai; giữ constructors/accessors hiện có | BusinessException + mã cụ thể theo convention user |
| C7 | `T/persistence/BookingPersistenceIT.java` | EXISTING | Test service qua proxy Spring và DB thật của test; dùng Boot Jackson/Validation auto-configuration và import C1/C3, không tạo TestConfiguration/bean phụ | Hạ tầng PostgreSQL Testcontainers hiện có |
| C8 | `T/messaging/SalesConfigurationListenerTests.java` | NEW | Test JUnit/Mockito cho phân loại lỗi boundary; không test container giả | Dependency test hiện có |
| C9 | `B/entity/EventSalesSnapshot.java` | EXISTING | Thêm `@Version @Column(name = "row_version", nullable = false) @ColumnDefault("0") private Long rowVersion`; giữ `@Getter/@Setter` sinh đúng getter/setter M13/M14; không initializer 0L | JPA version theo yêu cầu user; annotation Hibernate ColumnDefault đặt default DDL cho row cũ khi schema update, không gán giá trị Java |

DTO `Envelope`, `SalesConfiguration`, promotion snapshot; entity/inbox khác; các repository khác; Rabbit converter/error handler, JSON config: tái sử dụng. Không thêm dependency/migration framework hoặc file migration riêng. Dùng ddl-auto update và đồng bộ DDL bootstrap như Bước 1. Không sinh test class/helper khác ngoài danh sách.

## 5. Bảng constructor/method

Tất cả item mới/đổi chữ ký của pha WRITING-PLANS đang **PENDING**. Method REUSE chỉ đổi thân theo phạm vi đã nêu.

| ID | Class | Chữ ký đầy đủ/annotation | Loại và trách nhiệm |
|---|---|---|---|
| M1 | C1 | `public SalesConfigurationListener(AvailabilityService availabilityService)` — Lombok-generated | NEW constructor injection |
| M2 | C1 | `public void handleSalesConfiguredEvent(Envelope<SalesConfiguration> message)` — `@RabbitListener(queues = "booking.sales-configured.q", ackMode = "AUTO", concurrency = "1")` | NEW; gọi service; BusinessException lỗi vĩnh viễn → `AmqpRejectAndDontRequeueException`; lỗi hạ tầng throw ra container |
| M3 | C2 | `public abstract void synchronizeSalesConfiguration(Envelope<SalesConfiguration> message)` | NEW; operation nghiệp vụ |
| M4 | C3 | `public AvailabilityServiceImpl(EventSalesSnapshotRepository eventSalesSnapshotRepository, TicketTypeInventoryRepository ticketTypeInventoryRepository, SectorInventoryRepository sectorInventoryRepository, SeatInventoryRepository seatInventoryRepository, PurchaseLimitRepository purchaseLimitRepository, PromotionInventoryRepository promotionInventoryRepository, InboxMessageRepository inboxMessageRepository, JsonMapper jsonMapper, Validator validator)` — Lombok-generated | CHANGE constructor; giữ dependency cũ và thêm phần cần cho task; bỏ EntityManager theo yêu cầu user |
| M5 | C3 | `public void synchronizeSalesConfiguration(Envelope<SalesConfiguration> message)` — `@Override`, `@Transactional` | NEW; validate/inbox/version/snapshot/điều phối inventory; không tự gọi qua `this` để mở transaction |
| M6 | C4 | `public abstract Optional<EventSalesSnapshot> findByEventId(UUID eventId)` — `@Lock(LockModeType.PESSIMISTIC_WRITE)` | NEW derived query; khóa snapshot đã tồn tại trước khi so version và ghi inventory |
| M7 | C3 | `private String calculateConfigurationHash(SalesConfiguration configuration)` | NEW; SHA-256 từ JSON DTO do JsonMapper hiện có serialize, HexFormat chuẩn JDK; không custom decoder/canonicalizer |
| M8 | C3 | `private void synchronizeTicketTypeInventory(SalesConfiguration previousConfiguration, SalesConfiguration configuration, long sourceConfigVersion)` | NEW; chỉ pool QUANTITY, preserve counter |
| M9 | C3 | `private void synchronizeSectorInventory(SalesConfiguration previousConfiguration, SalesConfiguration configuration, long sourceConfigVersion)` | NEW; metadata SEATED + pool STANDING |
| M10 | C3 | `private void synchronizeSeatInventory(SalesConfiguration previousConfiguration, SalesConfiguration configuration, long sourceConfigVersion)` | NEW; upsert ghế và bảo vệ reservation/state |
| M11 | C3 | `private void synchronizePromotionInventory(SalesConfiguration previousConfiguration, SalesConfiguration configuration, long sourceConfigVersion)` | NEW; mapping promotion, preserve held/used |
| M12 | C5 | `public static boolean validSalesConfiguration(SalesConfiguration value)` | REUSE; thêm check QUANTITY quantity không null, type SEAT_MAP có sector, seat thuộc SEATED và type cùng sector; giữ contract V1 |
| M13 | C9 | `public Long getRowVersion()` — Lombok-generated | NEW; getter field version theo annotation đã có |
| M14 | C9 | `public void setRowVersion(Long rowVersion)` — Lombok-generated | NEW; setter được sinh từ `@Setter` hiện có; application không tự set/increment version |

Bốn helper M8–M11 tương ứng bốn tập inventory có quy tắc khác nhau; tránh một method điều phối quá dài. Không thêm generic base service, strategy hay helper một dòng chỉ bọc repository. `previousConfiguration` có thể null khi tạo event lần đầu.

## 6. Các bước triển khai sau khi plan được duyệt

### Bước 1 — Cột JPA version, validation và khóa dữ liệu

**File:** C4/C5/C6/C9; `docs/architecture/data/postgres/20-booking.sql`; `infrastructure/config-server/src/main/resources/config/booking-service.yaml`. **Item:** M6/M12–M14.

- Thêm field boxed `Long rowVersion` có `@Version` vào C9. Entity mới có UUID gán sẵn nhưng version null để Spring Data chọn persist qua `save()`. Không dùng primitive long và không set `rowVersion=0L` trong constructor/helper; Hibernate quản lý giá trị khi INSERT/UPDATE.
- DDL bootstrap: thêm `row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0)` vào bảng `event_sales_snapshots` trước bước rename `tbl_` hiện có.
- Schema DB đang chạy: dùng `spring.jpa.hibernate.ddl-auto: update` như user chọn. `@ColumnDefault("0")` trên rowVersion giúp Hibernate tạo cột bigint NOT NULL có default 0 khi cột còn thiếu; các row cũ nhận 0. Annotation chỉ ảnh hưởng DDL; field Java mới vẫn null trước khi save. Không tạo bảng version riêng hoặc file migration, không đổi `source_config_version`. Khi triển khai phải xác nhận service dùng config update và role owner hiện có, kiểm tra catalog sau startup: row_version bigint, NOT NULL, default 0 và không còn row null. Chưa khởi động service để thay schema trong pha lập plan.
- Giữ M6 khóa snapshot đã tồn tại để serialize so version + kiểm inbox. `@Version` bổ sung cơ chế nhận biết INSERT và optimistic version check; không coi nó tự khóa inbox hay tự bảo vệ toàn bộ các bảng con.

- Thêm derived query khóa snapshot bằng event ID. Không thay `sourceConfigVersion` thành `@Version`.
- Dùng Jakarta `Validator` cho envelope + nested DTO và rules hiện có; thêm các quan hệ còn thiếu trong M12. `messageType` phải đúng queue, aggregate ID/version khớp data.
- Thêm hai error code cụ thể; dùng `BusinessException` hiện có, không exception class/raw string nghiệp vụ mới.
- Kết quả: payload không hợp lệ bị chặn trước khi tạo dữ liệu; query dùng khóa trong transaction.

### Bước 2 — Transaction, version và inbox

**File:** C2/C3. **Item:** M3–M7.

Thứ tự trong M5:

1. Validate message; tính hash của typed `data`. Hash không phụ thuộc thứ tự key JSON trên wire; thứ tự phần tử mảng vẫn là một phần của cấu hình. Không dùng Java `hashCode` làm fingerprint bền vững.
2. Khóa snapshot hiện tại bằng M6. Sau khi đã lấy khóa, đọc inbox bằng `(consumerName="booking.sales-configured", messageId)` để không dùng kết quả dedupe đọc trước khi chờ khóa.
3. Có inbox: hash trùng thì return; khác thì `MESSAGE_ID_CONFLICT`. Chưa có inbox: so source version với snapshot.
4. Version cũ: không đổi snapshot/inventory; chỉ ghi inbox rồi return. Cùng version: hash cấu hình trùng thì chỉ ghi inbox; khác thì `SALES_CONFIGURATION_CONFLICT`. Version mới: áp dụng full snapshot.
5. Khi chưa có snapshot, tạo entity với eventId đã có và `rowVersion=null`, rồi gọi `eventSalesSnapshotRepository.saveAndFlush(newSnapshot)`. Vì boxed version null, Spring Data chọn INSERT qua persist nội bộ; không inject/gọi EntityManager. PK có sẵn chặn hai transaction cùng tạo; transaction thua phải rollback, lần redelivery đọc lại từ đầu. Không catch lỗi unique rồi tiếp tục trong transaction đã rollback-only. Optimistic version mismatch cũng phải rollback và đọc lại trong transaction mới, không retry trong transaction đã hỏng.
6. Với snapshot đã tồn tại, sửa managed entity bằng setter. Không đặt lại state CANCELLED thành bán được, kể cả message version cao hơn. Việc release/hủy đơn không thuộc consumer này.
7. Gọi M8–M11 theo thứ tự snapshot → ticket/sector/seat → promotion. Ghi inbox cùng transaction; timestamp `processedAt` không phải một lần commit riêng.

**Kết quả:** không có inbox thành công mà thiếu inventory; redelivery không reset counter. Khóa snapshot giải quyết race cập nhật version ở nhiều instance; `concurrency="1"` không được coi là khóa phân tán.

### Bước 3 — Đồng bộ inventory bằng repository có sẵn

**File:** C3. **Item:** M8–M11.

**Luồng lưu ba inventory chính (chi tiết thân M8–M10, không thêm class/method):**

1. M5 giữ lại `previousConfiguration` trước khi thay JSON snapshot; validate/version guard hoàn tất trước khi ghi các inventory. Lưu snapshot cha rồi đồng bộ các tập con trong cùng transaction. Không cần flush riêng chỉ để có FK: persistence context và transaction quản lý thứ tự SQL theo mapping; integration test phải kiểm tra thứ tự thực tế vì entity con hiện giữ UUID thay vì quan hệ JPA tới snapshot. Nếu thứ tự SQL không đủ, xử lý flush tại đúng ranh giới FK, không tự thêm flush sau mỗi save.
2. M8: nếu mode QUANTITY, lưu một `TicketTypeInventory` cho mỗi ticket type; nếu SEAT_MAP thì không tạo pool type. M9/M10: chỉ xử lý sector/seat khi mode SEAT_MAP; QUANTITY có hai danh sách này rỗng theo contract.
3. M9 lưu sector trước M10 lưu seat vì seat có FK tới sector. Sector SEATED vẫn cần một row metadata/capacity cấu hình cho FK, nhưng không trừ pool sector khi giữ một ghế. Sector STANDING dùng duy nhất counter pool chung cho các type cùng sector.
4. Mỗi method lấy các row cần dùng bằng `findAllById`, tra theo ID, kiểm tra event ownership, tạo entity khi chưa có hoặc sửa row đã load. Gọi repository `saveAll` cho các row cần tạo/cập nhật. Không `deleteAll` và insert lại, không đặt tất cả counter về 0 khi replay/update.

| Entity/field | Row mới | Row đã tồn tại, nhận version cấu hình mới hơn |
|---|---|---|
| TicketTypeInventory.ticketTypeId/eventId | Lấy từ type/config | Giữ định danh; event phải khớp |
| TicketTypeInventory.configuredCapacity | `Long.valueOf(type.configuredQuantity())`, chỉ QUANTITY | Đổi theo cấu hình hợp lệ, phải >= held + purchased |
| TicketTypeInventory.held/purchased | `0L` / `0L` | Giữ nguyên, không tính lại từ capacity |
| SectorInventory.sectorId/eventId/sectorType | Lấy từ sector/config | Giữ định danh; không đổi kind khi còn trạng thái sử dụng không tương thích |
| SectorInventory.configuredCapacity | `Long.valueOf(sector.configuredCapacity())` | Đổi theo cấu hình hợp lệ, không thấp hơn counter đang sử dụng |
| SectorInventory.held/purchased | `0L` / `0L` | Giữ nguyên; chỉ STANDING dùng counter làm pool giữ/mua |
| SeatInventory.seatId/eventId/sectorId/ticketTypeId/seatLabel | Lấy trực tiếp từ seat/config | Chỉ đổi metadata khi không vi phạm reservation/type/sector guard |
| SeatInventory.sectorType | `SEATED` | Giữ SEATED; validation chặn ghế thuộc STANDING |
| SeatInventory.isHidden/state | Hidden → `true`/UNAVAILABLE; còn lại `false`/AVAILABLE | Không có reservation: state theo hidden; HELD/PURCHASED giữ nguyên và từ chối thay đổi cấu hình làm mất ghế hoặc đổi loại/sector |
| SeatInventory.currentReservationId/reservationGeneration | `null` / `0L` | Giữ nguyên; consumer cấu hình không tạo/gia hạn/giải phóng reservation |
| Cả ba: sourceConfigVersion | Version nguồn đã validate | Ghi version mới vào row được đồng bộ |
| Cả ba: rowVersion | Để `null` trước save | Giữ giá trị load từ DB; Hibernate tự kiểm tra/tăng |

**Ví dụ kết quả lưu:**

- QUANTITY có type A quantity 100 và type B quantity 50 → hai row TicketTypeInventory với capacity 100/50, held/purchased 0; không tạo sector hoặc seat.
- SEAT_MAP có một STANDING sector capacity 100 và hai type VIP/Standard → một SectorInventory pool capacity 100 dùng chung, không phải hai pool 100; type/giá vẫn nằm trong snapshot; không tạo seat cho sector đứng.
- Một SEATED sector có 100 seat, 5 hidden → một SectorInventory metadata và 100 SeatInventory: 95 AVAILABLE + 5 UNAVAILABLE, không tạo pool TicketTypeInventory. Capacity cấu hình sector vẫn lưu như Event gửi; số ghế được giữ xác định từ từng seat hợp lệ, không suy ra quyền bán ghế hidden từ capacity.
- Snapshot version mới nhận khi pool type capacity 100 đang held 3/purchased 7: giữ counter 3/7; nếu capacity mới 120 thì chỉ thay capacity/version, lượng còn lại là 110. Nếu capacity mới 5 thì reject và rollback, không đặt lại counter để ép cấu hình pass.

Promotion vẫn đi qua M11 trong transaction này, nhưng không thuộc ba nguồn cung vé; mapping/giữ held/used theo các bullet bên dưới.

- Dùng `findAllById` với hợp của ID trong snapshot trước và sau, rồi `saveAll`/managed setters. Không thêm `findAllByEventId` hoặc SQL upsert nếu API này đủ.
- Kiểm tra row lấy theo ID thuộc đúng event trước khi sửa; không cho ID của event khác bị merge thành dữ liệu event hiện tại.
- Row mới: held/purchased/used = 0; seat generation = 0, reservation null. Giữ `rowVersion` cho JPA quản lý, không set bằng source version.
- Row cũ: giữ nguyên counters, reservation và generation; capacity/limit mới không được thấp hơn lượng đang giữ + đã dùng/bán. Không hard-delete các row vắng khỏi snapshot mới; snapshot mới xác định tập cấu hình đang được phép dùng, row cũ giữ lịch sử/FK.
- Nếu seat bị bỏ/ẩn/đổi type hoặc sector khi đang HELD/PURCHASED, reject cả transaction. Seat không có reservation chuyển AVAILABLE/UNAVAILABLE theo hidden và sự hiện diện trong config; không hồi sinh ghế đã bán.
- Nếu bỏ hoặc đổi nội dung promotion đang có held usage, reject. Promotion mới vẫn được thêm; giữ used/held và dữ liệu lịch sử. Không đổi limit thấp hơn held + used.
- Giữ organizer/mode của event ổn định; không chuyển nguồn cung QUANTITY ↔ SEAT_MAP trong consumer này. Không thêm chức năng sửa thương mại sau mở bán; nhận snapshot mới vẫn phải tôn trọng các guard dữ liệu hiện hữu.
- Ghi nhận điều kiện future booking/availability: chỉ dùng type/sector/seat/promotion có trong snapshot đang áp dụng; không query toàn bộ row cũ theo event rồi coi tất cả là đang bán.

### Bước 4 — Listener AUTO ACK và lỗi

**File:** C1. **Item:** M1/M2.

- Dùng converter/JsonMapper hiện có; không parse JSON lại bằng decoder tay và không tạo container factory riêng.
- `ackMode="AUTO"` override chế độ manual hiện tại cho listener này. Không Channel, delivery tag, basicAck/basicNack.
- M2 gọi M5 đồng bộ qua service bean. Service commit thành công mới trả về; lỗi commit phải đi ra listener.
- Lỗi contract/business đã biết: `AmqpRejectAndDontRequeueException` có BusinessException làm cause, không log payload/credential.
- Lỗi DB/lock/constraint race chưa giải quyết: propagate để container requeue theo cấu hình native. Quorum delivery limit 20/DLX hiện có giới hạn vòng giao lại; không thêm `@Retryable`/RetryTemplate thứ hai trong task này. Đây là redelivery có giới hạn, chưa có backoff ứng dụng. Khi triển khai phải xác minh runtime không override `defaultRequeueRejected=false`; nếu có override trái với plan thì sửa cấu hình listener đã duyệt trước khi chạy.
- Không wrap mọi RuntimeException thành lỗi payload, cũng không báo thành công khi đã catch lỗi DB.

### Bước 5 — Test đúng transaction và boundary

**File:** C7/C8. Các method dưới đây là toàn bộ method test mới dự kiến; không thêm helper/test config mới chưa duyệt.

| ID | Class | Chữ ký + annotation | Kết quả kiểm |
|---|---|---|---|
| U1 | C8 | `@Test void returnsSuccessfullyAfterSalesConfigurationServiceCompletes()` | Listener gọi đúng operation và chỉ return khi service xong |
| U2 | C8 | `@Test void rejectsPermanentSalesConfigurationFailuresWithoutRequeue()` | Business conflict tạo reject không requeue và giữ cause |
| U3 | C8 | `@Test void propagatesDatabaseFailuresToTheContainer()` | Lỗi DB không bị nuốt/ACK thành công |
| D1 | C7 | `@Test @Transactional(propagation = NOT_SUPPORTED) void salesConfigurationAndInventoriesCommitTogether()` | QUANTITY/promotion/inbox persist thực qua proxy |
| D2 | C7 | `@Test @Transactional(propagation = NOT_SUPPORTED) void seatMapUsesSectorPoolsAndSeatStates()` | STANDING chia pool, hidden UNAVAILABLE, không tạo pool type SEAT_MAP |
| D3 | C7 | `@Test @Transactional(propagation = NOT_SUPPORTED) void failedInventoryWriteRollsBackSnapshotAndInbox()` | Lỗi thật sau ghi một phần rollback snapshot + inventory + inbox |
| D4 | C7 | `@Test @Transactional(propagation = NOT_SUPPORTED) void replayedMessageDoesNotResetInventoryCounters()` | Cùng ID/hash chỉ một hiệu ứng, counter giữ nguyên |
| D5 | C7 | `@Test @Transactional(propagation = NOT_SUPPORTED) void reusedMessageIdWithDifferentPayloadIsRejected()` | ID trùng/hash khác không được ACK như duplicate hợp lệ |
| D6 | C7 | `@Test @Transactional(propagation = NOT_SUPPORTED) void staleConfigurationDoesNotOverwriteNewerSnapshot()` | v5 rồi v3: giữ v5, thêm inbox v3 |
| D7 | C7 | `@Test @Transactional(propagation = NOT_SUPPORTED) void sameVersionWithDifferentPayloadIsRejected()` | Hai cấu hình khác nhau không dùng chung version |
| D8 | C7 | `@Test @Transactional(propagation = NOT_SUPPORTED) void newerConfigurationPreservesHeldAndPurchasedCounters()` | Counter, reservation và generation không reset |
| D9 | C7 | `@Test @Transactional(propagation = NOT_SUPPORTED) void concurrentConfigurationsKeepTheNewestCommittedVersion() throws Exception` | Hai transaction/connection thật; race insert và cập nhật được rollback/retry từ transaction mới; không giữ snapshot cũ hơn |
| D10 | C7 | `@Test @Transactional(propagation = NOT_SUPPORTED) void configurationCannotRemoveReservedSeatsOrReviveCancelledEvent()` | Guard reserved seat và CANCELLED không hồi sinh |
| D11 | C7 | `@Test @Transactional(propagation = NOT_SUPPORTED) void snapshotRowVersionIsManagedByJpa()` | Entity ID gán sẵn/version null được INSERT; version được Hibernate gán/tăng khi UPDATE; sourceConfigVersion vẫn là field nghiệp vụ riêng |

Tái sử dụng `BookingFixtures.sales/promotion/with` và ID/time fixture. Khi dựng envelope cho consumer, phải sửa aggregateId/version khớp sales config; fixture envelope chung hiện có dùng ORDER/version 2 nên không được dùng nguyên trạng làm input hợp lệ.

Các test D* không được bọc trong outer transaction tự rollback của `@DataJpaTest`. Chuẩn bị/dọn dữ liệu trong transaction test tách biệt, không để fixture tồn dư làm test phụ thuộc thứ tự. Import service thật, repository thật, Jackson/Validation auto-config đã có; không mock repository để kết luận rollback.

Unit test listener + DB integration chỉ chứng minh các phần tương ứng. Trước khi báo luồng end-to-end hoạt động, phải kiểm thêm container AUTO ACK + DB trên broker/DB test cô lập: dữ liệu commit trước delivery được ACK; rollback không bị ACK thành công; poison message tới DLQ; replay sau commit không thêm inventory. Không consume/publish vào queue `booking.sales-configured.q` trên server 119 để thử khi chưa được giao việc chạy môi trường đó. Nếu chưa chạy broker thật, ghi rõ giới hạn bằng chứng, không coi mock là bằng chứng ACK/DLQ.

### Bước 6 — Xác minh và bàn giao

Chạy từ `services/booking-service` với JDK 23, không bỏ Enforcer:

```powershell
rtk proxy .\mvnw.cmd -B -ntp "-Dtest=SalesConfigurationListenerTests,ContractRulesTests" test
rtk proxy .\mvnw.cmd -B -ntp -Pintegration "-Dit.test=BookingPersistenceIT" verify
```

Dùng DDL fixture của test hiện có, không thao tác database production. Đồng bộ cột row_version trong DDL bootstrap để các test dùng schema khai báo sẵn vẫn chạy. Profile integration dùng Testcontainers hiện có; nếu runtime container không sẵn, báo integration chưa chạy, không đổi sang DB thật để làm test pass. Khi chạy verify, lệnh này bao gồm fast suite/Spotless/SpotBugs theo POM; không lặp full suite nếu không có chỉnh sửa hoặc vấn đề mới.

Bàn giao: file thực thay đổi; kết quả unit/integration/broker tách đúng bằng chứng; ví dụ version stale và duplicate; không commit/push/deploy trong task lập plan.

## 7. Gate WRITING-PLANS

- **PENDING:** C1–C9, M1–M14 và test methods U1–U3/D1–D11; C5/M12 là sửa method hiện có; C6 là enum constants mới. User đã yêu cầu thay EntityManager bằng `@Version`; phần class/method khác chưa được coi là duyệt toàn bộ plan.
- **Delta so với design:** tái sử dụng AvailabilityService thay service pair mới; chỉ một listener mới; inventory theo đúng ba nguồn cung; thêm một derived query khóa và một SHA-256 method JDK. Theo chỉnh sửa mới nhất của user: bỏ EntityManager ở C3/M4/M5; thêm C9/M13/M14/D11; schema dùng ddl-auto update thay migration riêng, với ColumnDefault 0 cho cột mới; lần INSERT đầu dùng repository với `rowVersion=null`. YAML update đã được chỉnh theo yêu cầu trực tiếp, không đánh dấu các khai báo Java khác APPROVED.
- **Nguồn yêu cầu lập plan:** user “ok, viết plan. dùng skill shop app writing plan + ponytail tránh viết code thừa k cần thiết”. Câu này cấp phạm vi viết plan; không tự đánh dấu gate class/method của pha này APPROVED.
- Sau khi user duyệt danh sách và delta này, cập nhật trạng thái ngay trong file; chỉ chuyển sang execute khi user giao triển khai. Không tạo ledger/framework quản lý phê duyệt mới.

## 8. Thực thi trên dev/son và commit theo giai đoạn

- User yêu cầu: “đồng ý triển khai theo plan đã duyệt + code mã giả như trên. Triển khai cho tôi trên nhánh dev/son. Commit mỗi giai đoạn trong các task”. Đây là nguồn xác nhận plan và quyền sửa code/test, commit từng giai đoạn; chưa có yêu cầu push/deploy.
- Gate EXECUTING-PLANS: APPROVED cho C1–C9/M1–M14, U1–U3/D1–D11 trong §4–§6. Nguồn: user trả lời “Xác nhận toàn bộ danh sách thực thi” cho câu hỏi xác nhận pha EXECUTING-PLANS trên dev/son. Chỉ hỏi delta nếu phát sinh khai báo/cách viết ngoài danh sách.
- Delta APPROVED: user yêu cầu “thêm cột rowVersion cho inboxmessage”. C10 = entity InboxMessage hiện có, thêm `@Version @ColumnDefault("0") @Column(name="row_version", nullable=false) private Long rowVersion`; M15/M16 = `public Long getRowVersion()` / `public void setRowVersion(Long rowVersion)` sinh từ Lombok đã có. Đồng bộ DDL bootstrap. Giữ repository save(), không thêm method native insertIfAbsent. M5 dùng save() như mã giả user đã duyệt, flush do transaction quản lý.
- Giai đoạn 1: field @Version, ddl-auto update, DDL bootstrap, query khóa snapshot, validation và error code; compile/check liên quan rồi commit.
- Giai đoạn 2: service transaction/inbox/version, M7–M11 và mapping inventory như mã giả đã trình; compile/check liên quan rồi commit.
- Giai đoạn 3: listener AUTO ACK và phân loại lỗi; compile/check liên quan rồi commit.
- Giai đoạn 4: test U*/D* và xác minh theo điều kiện môi trường hiện có; commit test khi đã kiểm tra kết quả thực tế.
- Giai đoạn 5: cập nhật kết quả thực tế vào plan/TESTING, các điều chỉnh đã được duyệt và bàn giao; commit tài liệu. Giai đoạn nào có sửa code để sửa lỗi sẽ commit riêng với nguyên nhân cụ thể, không ép test fail vào commit đã báo pass.

### Kết quả giai đoạn 1

- Đã thêm rowVersion/ColumnDefault, DDL bootstrap và ddl-auto update; thêm query khóa, error code cụ thể và validation quan hệ sector/type/seat.
- Kiểm tra: Maven Enforcer Java 23 pass, compile pass, ContractRulesTests 67 test pass; Spotless apply và git diff --check sạch. Log local: services/booking-service/target/sales-config-phase1.log. Chưa cập nhật schema server 119.

## 9. Review version tất cả bảng Booking theo yêu cầu user

Đã đọc catalog PostgreSQL thật bằng transaction read-only/role booking_owner qua tunnel 5432: có 15 bảng, 8 bảng đã có row_version. Chưa chạy service để sửa schema trên server. `@Version` kiểm tra version khi UPDATE/DELETE và giúp repository nhận biết INSERT; nó không tự SELECT FOR UPDATE hoặc tự bảo vệ các row liên quan. Unique/PK, transaction và state transition vẫn cần giữ.

| Bảng | DB thật có row_version? | Đề xuất và trạng thái |
|---|---|---|
| tbl_event_sales_snapshots | Chưa | Đang thêm theo task; @Version null cho ID gán sẵn, khóa snapshot cho version/inbox guard |
| tbl_inbox_messages | Chưa | Đang thêm theo yêu cầu mới nhất; save receipt mới là INSERT, trùng PK rollback |
| tbl_orders | Có | Giữ version; vẫn cần guard state/lock khi giữ, thanh toán, hết hạn |
| tbl_reservations | Có | Giữ version; bảo vệ chuyển HELD/COMMITTED/RELEASED |
| tbl_ticket_type_inventory | Có | Giữ version và invariant counter |
| tbl_sector_inventory | Có | Giữ version, pool STANDING dùng chung |
| tbl_seat_inventory | Có | Giữ version và điều kiện trạng thái ghế |
| tbl_purchase_limits | Có | Giữ version và unique buyer/event |
| tbl_promotion_inventory | Có | Giữ version và counter held/used |
| tbl_promotion_usages | Có | Giữ version và unique active usage |
| tbl_idempotency_requests | Chưa | Đề xuất thêm @Version khi làm tạo đơn nếu dùng save() với ID ghép gán sẵn, để receipt mới không merge đè như inbox; chưa sửa vì user chỉ yêu cầu đề xuất |
| tbl_outbox_messages | Chưa | Đề xuất @Version khi làm relay dùng JPA cập nhật attempts/publishedAt và lưu message có ID gán sẵn. Nếu dùng claim/conditional UPDATE hay FOR UPDATE SKIP LOCKED đúng thì version không bắt buộc cho cạnh tranh worker; version không đảm bảo publish mạng đúng một lần. Chưa sửa |
| tbl_reservation_allocations | Chưa | Đề xuất nếu payment/expiry/release cập nhật độc lập row: state HELD/COMMITTED/RELEASED không được xử lý hai lần. Nếu mọi thao tác luôn khóa reservation/order cha và kiểm state thì có thể giữ hiện trạng; @Version cha không tự tăng khi chỉ sửa child. Chưa sửa |
| tbl_order_items | Chưa | Chưa đề xuất cho snapshot giá/số lượng bất biến sau tạo; nếu có sửa item riêng ngoài transaction/order lock thì đánh giá lại |
| tbl_order_item_seats | Chưa | Chưa đề xuất: mapping ghế/chi tiết đơn giữ bất biến, unique/PK/FK xử lý trùng và liên kết; không cần field version chỉ để đủ tất cả bảng |

Phần review này không tự mở rộng triển khai sang 5 bảng cuối. Entity/DDL của snapshot + inbox đang thay đổi trên dev/son; cột DB chỉ được cập nhật khi chạy Hibernate update ở môi trường triển khai được giao.

### Kết quả giai đoạn 2

- Đã viết transaction đồng bộ snapshot/inventory/inbox, hash/version guard, bảo toàn counter/reservation và chống hồi sinh CANCELLED; chỉ dùng repository save()/saveAll().
- InboxMessage thêm @Version/ColumnDefault theo yêu cầu trực tiếp của user; bỏ đề xuất insertIfAbsent. Đã thêm review 15 bảng ở §9, không tự thêm version cho các bảng chỉ đang đề xuất.
- Compile JDK 23 và 67 ContractRulesTests pass; log local target/sales-config-phase2.log. Chưa kết luận transaction đúng cho tới khi chạy D* trên PostgreSQL test thật ở giai đoạn 4.

### Kết quả giai đoạn 3

- Đã thêm SalesConfigurationListener AUTO ACK, concurrency 1, gọi service qua interface; reject lỗi contract/hash/version vĩnh viễn và để lỗi DB/commit đi ra container.
- Không Channel/delivery tag, không thêm retry/template/factory. Compile JDK 23 pass, log local target/sales-config-phase3.log; xác minh unit/broker tiếp tục ở giai đoạn 4.
