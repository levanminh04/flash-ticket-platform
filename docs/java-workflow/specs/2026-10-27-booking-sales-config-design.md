# Draft spec: Nhận cấu hình bán vé tại Booking

**Trạng thái:** Bản nháp, chờ duyệt thiết kế và danh sách class/method Java mới.  
**Task:** [Tích hợp luồng tạo sự kiện vào Booking](https://app.clickup.com/t/z8qgvc7vb8)  
**Ngày:** 2026-10-27

## 1. Mục tiêu

Booking nhận `event.sales-configured.v1` do Event phát qua RabbitMQ, lưu snapshot cấu hình bán vé và khởi tạo/cập nhật inventory trong **một transaction PostgreSQL của Booking**. Chỉ ACK delivery RabbitMQ sau khi transaction commit thành công.

Theo chỉnh sửa của user, dùng Spring AMQP **AUTO ACK**: listener gọi đồng bộ sang service `@Transactional` qua Spring proxy. Service commit trước khi trả về listener; listener kết thúc thành công thì container ACK. Không truyền `Channel`/delivery tag và không gọi ACK thủ công. Không bắt exception rồi trả về như thành công.

ACK ở đây là ACK cho RabbitMQ delivery; consumer không gửi một ACK message riêng về Event. Publisher confirm của Event là cơ chế riêng.

## 2. Căn cứ trong repository

- ClickUp task yêu cầu nhận `saleConfig`, lưu SalesSnapshot và inventory/trạng thái ghế, rồi ACK sau commit.
- `docs/architecture/B13-api-and-event-contracts.md` §5.2, §7.1, §8.1 quy định `event.sales-configured.v1` là Event → Booking; nhận full snapshot theo version mới hơn và không reset `held`/`purchased`. §7.1 yêu cầu ghế hidden thành `UNAVAILABLE` và không xóa ghế đang được giữ/đã bán.
- `services/booking-service/src/main/java/com/flashticket/booking_service/dto/message/Envelope.java` và `dto/snapshot/SalesConfiguration.java` đã có DTO tương ứng.
- Booking đã có `EventSalesSnapshot`, `TicketTypeInventory`, `SectorInventory`, `SeatInventory`, `PromotionInventory`, các repository và `InboxMessage`/`InboxMessageRepository`.
- `deploy/rabbitmq/definitions.json` đã khai báo exchange/routing `event.sales-configured.v1`, queue quorum `booking.sales-configured.q`, DLQ và delivery limit 20.
- `infrastructure/config-server/src/main/resources/config/booking-service.yaml` đã dùng `acknowledge-mode: manual`, `prefetch: 20`.
- Đã đối chiếu `IdentityUserCreatedListener` và `IdentityMessagingConfig` của User Service: listener không nhận Channel, factory đặt `AcknowledgeMode.AUTO`. Booking sẽ override `ackMode = "AUTO"` ngay trên listener này, dùng converter/factory hiện có và không đổi chế độ của các listener khác. Mẫu thứ tự xử lý là listener → service → hoàn thành ghi DB → listener trả về → container ACK.
- Phong cách đề xuất: constructor injection, interface/implementation cho service theo cấu trúc hiện có, mapping trực tiếp bằng setter/entity, không tạo mapper hoặc repository method nếu API có sẵn đáp ứng.

## 3. Phạm vi

### Bao gồm

1. Nhận envelope `event.sales-configured.v1` từ queue Booking đã khai báo.
2. Kiểm tra envelope và `SalesConfiguration` theo contract hiện có: schema, version, event ID, các mốc thời gian, danh sách ID duy nhất và quan hệ type/sector/seat.
3. Trong một transaction: ghi inbox, upsert `EventSalesSnapshot`, rồi tạo/cập nhật inventory ticket type, sector, seat và promotion.
4. Bảo toàn các counter và trạng thái booking hiện có khi nhận lại cùng message hoặc snapshot version mới; không làm mất ghế đang HELD/PURCHASED.
5. Container tự ACK delivery sau khi transaction service commit và listener trả về thành công.
6. Để lỗi đi qua error handler/container hiện tại; rollback trước khi message bị reject/requeue. Queue/DLX hiện có chịu trách nhiệm đưa message tới DLQ theo policy.

### Không bao gồm

- Thay đổi Event publisher/outbox, hợp đồng payload, hoặc cấu hình RabbitMQ đã có.
- Gửi application-level ACK từ Booking sang Event.
- API đồng bộ gọi Event, tạo schema/database mới, thêm worker/scheduler, hoặc thêm framework retry.
- Xử lý `event.cancelled.v1`; đây là task/consumer riêng.

## 4. Luồng xử lý đề xuất

1. Spring AMQP chuyển JSON thành `Envelope<SalesConfiguration>` theo `MessageConverter` đã cấu hình. Listener dùng `ackMode = "AUTO"` và chỉ nhận envelope.
2. Listener gọi service transaction. Service xác nhận `messageType`, envelope schema, `aggregateId == data.eventId`, envelope version khớp `sourceConfigVersion`, và payload hợp lệ bằng contract hiện có. Sai contract thì không ghi dữ liệu nghiệp vụ.
3. Service tra `InboxMessage` theo `(consumerName, messageId)`. Nếu message đã xử lý và payload hash trùng, coi là replay và kết thúc thành công để container ACK. Cùng message ID nhưng nội dung khác là lỗi contract, không ACK như thành công.
4. Service so `sourceConfigVersion` với snapshot hiện tại:
   - Version cũ hơn: không ghi đè snapshot/inventory; ghi nhận inbox để replay không lặp lại.
   - Cùng version, cùng nội dung: idempotent no-op và ghi inbox.
   - Cùng version nhưng nội dung khác: từ chối message để phát hiện producer vi phạm version contract.
   - Version mới hơn: upsert full snapshot và inventory trong cùng transaction.
5. Khi khởi tạo row mới, counter bắt đầu bằng 0; seat mới có reservation trống, generation 0, và state `UNAVAILABLE` nếu hidden, ngược lại `AVAILABLE`.
6. Khi cập nhật, giữ nguyên `held`, `purchased`, `used`, reservation hiện tại và generation. Không xóa inventory seat đang HELD/PURCHASED. Không xóa hoặc thay đổi promotion có usage chưa kết thúc.
7. Ghi inbox trong cùng transaction. Sau khi phương thức `@Transactional` trên service bean commit và trả về, listener kết thúc bình thường để container tự ACK.
8. Nếu commit lỗi, listener không ACK; exception được đưa qua cơ chế Spring AMQP hiện tại. Nếu commit thành công nhưng ACK thất bại, broker có thể giao lại; inbox/version check làm lần giao lại thành no-op.

## 5. Mapping inventory

| Dữ liệu nguồn | Nơi lưu | Quy tắc khởi tạo |
|---|---|---|
| Các trường event, organizer, status, cửa sổ bán, purchase limit, mode, version và full JSON | `EventSalesSnapshot` | Một row theo `eventId`; `receivedAt` là thời điểm Booking nhận snapshot |
| `ticketTypes` | `TicketTypeInventory` | `configuredCapacity` lấy từ `configuredQuantity` khi được cung cấp; `held=0`, `purchased=0` cho row mới |
| `sectors` | `SectorInventory` | Lưu kind/capacity; `held=0`, `purchased=0` cho row mới |
| `seats` | `SeatInventory` | Lưu sector/type/label/hidden; state mới `AVAILABLE` hoặc `UNAVAILABLE`; reservation null, generation 0 |
| `promotions` | `PromotionInventory` | Lưu giới hạn/cửa sổ/config snapshot; `held=0`, `used=0` cho row mới |

**Điểm còn chờ chốt:** với sector `SEATED`, contract cho phép `configuredQuantity=null`, nhưng cột `TicketTypeInventory.configuredCapacity` là `NOT NULL`. Đề xuất tính capacity từ số seat không hidden có `ticketTypeId` tương ứng. Ví dụ 100 seat có 5 hidden thì capacity bán đề xuất là 95. Nếu lưu capacity vật lý là 100, vẫn chỉ có tối đa 95 ghế được phép bán lúc đầu: logic đặt ghế phải kiểm tra từng seat không hidden và `AVAILABLE`, không được dựa riêng vào capacity 100 để bán thêm ghế ẩn. Số ghế có thể giữ ngay còn phải loại các ghế đang HELD/PURCHASED.

Không áp dụng phép đếm seat này cho `STANDING`: sector đứng dùng pool chung theo `SectorConfig.configuredCapacity`, không có seat riêng và không cộng capacity của nhiều loại vé trong cùng sector. Cách biểu diễn `TicketTypeInventory` cho type thuộc STANDING còn cần đối chiếu/chốt trước khi triển khai; không tự gán 0 hoặc coi mỗi type có pool độc lập.

`sourceConfigVersion` là version nghiệp vụ do Event cấp cho cấu hình được phát sang Booking. Nó giúp nhận biết snapshot cũ/mới và replay. `rowVersion` là field có `@Version` của JPA, dùng optimistic locking khi nhiều transaction cùng sửa một inventory row. Hai field độc lập: thay đổi counter tại Booking làm `rowVersion` tăng nhưng không tạo version cấu hình Event mới.

## 6. Giao dịch và lỗi

- Transaction chỉ bao gồm PostgreSQL Booking; không có distributed transaction với RabbitMQ hoặc Event.
- Inbox, snapshot và các inventory cùng commit/rollback. Listener dùng AUTO ACK, không cần `Channel`, delivery tag hoặc `basicAck`.
- Duplicate cùng message ID/payload: ACK sau khi xác nhận đã xử lý.
- Message không hợp lệ hoặc cùng version nhưng payload mâu thuẫn: reject, đi DLQ theo cấu hình RabbitMQ hiện tại; không loop vô hạn.
- Lỗi DB/tài nguyên tạm thời: rollback, không ACK; cơ chế container/quorum queue hiện tại xử lý redelivery và delivery limit. Cần xác minh `default-requeue-rejected` của runtime khi triển khai; spec không tự thêm retry framework.
- ACK thất bại sau commit: redelivery được xử lý như duplicate, không cộng lại inventory.

## 7. Class/method đề xuất — cần duyệt trước khi chốt

Không tạo DTO/entity/repository/config class mới. Các entity, DTO và repository hiện hữu được tái sử dụng.

| # | Loại | Khai báo đề xuất | Trách nhiệm | Trạng thái |
|---|---|---|---|---|
| 1 | Class mới | `SalesConfigurationListener` — `@Component`, `@RequiredArgsConstructor` | Rabbit listener, inject service | Chờ duyệt |
| 2 | Constructor mới (Lombok-generated) | `SalesConfigurationListener(SalesConfigurationService salesConfigurationService)` | Constructor injection | Chờ duyệt |
| 3 | Method mới | `public void handleSalesConfiguredEvent(Envelope<SalesConfiguration> message)` — `@RabbitListener(queues = "booking.sales-configured.q", ackMode = "AUTO")` | Gọi transaction service; trả về thành công để container ACK sau commit | AUTO/no Channel đã được user yêu cầu; tên/chữ ký mới chờ duyệt |
| 4 | Interface mới | `SalesConfigurationService` | Ranh giới xử lý nghiệp vụ | Chờ duyệt |
| 5 | Method mới | `void synchronizeSalesConfiguration(Envelope<SalesConfiguration> message)` | Khai báo đồng bộ cấu hình bán vé và inventory | Tên cụ thể thay apply theo yêu cầu user; chờ duyệt tên mới |
| 6 | Class mới | `SalesConfigurationServiceImpl` — `@Service`, `@RequiredArgsConstructor` | Điều phối validate, idempotency, snapshot và inventory | Chờ duyệt |
| 7 | Constructor mới (Lombok-generated) | `SalesConfigurationServiceImpl(EventSalesSnapshotRepository, TicketTypeInventoryRepository, SectorInventoryRepository, SeatInventoryRepository, PromotionInventoryRepository, InboxMessageRepository)` | Constructor injection các repository hiện có | Chờ duyệt |
| 8 | Method mới | `@Transactional public void synchronizeSalesConfiguration(Envelope<SalesConfiguration> message)` | Ghi inbox + snapshot + inventories trong một transaction | Tên cụ thể thay apply theo yêu cầu user; chờ duyệt tên mới |

### Test methods dự kiến

User đã đồng ý phạm vi test bằng phản hồi: “test thì ok làm sao cho đúng là đc”. Các test sau được chấp thuận ở pha thiết kế; chỉ viết/chạy khi chuyển sang triển khai theo phạm vi được giao. Cần kiểm chứng thêm thứ tự commit trước AUTO ACK và rollback không bị ACK thành công; không dùng test giả chỉ xác nhận lời gọi repository.

| Test class hiện có | Method mới dự kiến | Nội dung |
|---|---|---|
| `BookingPersistenceIT` | `salesConfigurationAndInventoriesCommitTogether()` | Kiểm tra snapshot, inbox và các inventory cùng persist |
| `BookingPersistenceIT` | `failedInventoryWriteRollsBackSnapshotAndInbox()` | Lỗi ghi một inventory làm rollback toàn bộ |
| `BookingPersistenceIT` | `replayedMessageDoesNotResetInventoryCounters()` | Replay không cộng/trừ hoặc reset counter |
| `BookingPersistenceIT` | `newerConfigurationPreservesHeldAndPurchasedCounters()` | Version mới giữ counter hiện hữu |
| `BookingPersistenceIT` | `staleConfigurationDoesNotOverwriteNewerSnapshot()` | Version cũ không ghi đè |
| `BookingPersistenceIT` | `sameVersionWithDifferentPayloadIsRejected()` | Bắt lỗi version bị dùng cho hai cấu hình khác nhau |
| `BookingPersistenceIT` | `seatMapCapacityCountsAvailableTypedSeats()` | Xác nhận quy tắc capacity đang chờ duyệt |

## 8. Điểm cần bạn xác nhận

1. Chốt ý nghĩa capacity cho `SEATED` và cách biểu diễn type thuộc `STANDING` như §5; user đang hỏi để hiểu, chưa xác nhận phép tính.
2. Duyệt 2 hành vi version: version cũ là no-op + inbox; cùng version nhưng payload khác thì reject/DLQ.
3. Class/constructor và tên method mới trong §7 còn chờ duyệt; user đã chọn AUTO ACK, bỏ Channel và đồng ý phạm vi test. Chưa có Java code nào được thêm.

Nguồn chỉnh sửa pha BRAINSTORMING: user yêu cầu “t dùng ack auto, bỏ channel mà”, đổi tên method cụ thể hơn thay `apply`, và đồng ý test. Chữ ký đề xuất mới: `handleSalesConfiguredEvent` ở listener, `synchronizeSalesConfiguration` ở cả interface và implementation. Câu hỏi về capacity/version chưa được tính là phê duyệt hai quyết định này.

Sau xác nhận, spec mới được chốt. Việc lập plan và triển khai vẫn có approval gate riêng theo hai skill Shop App tương ứng.

## 9. Chuyển sang plan theo yêu cầu user

User xác nhận hành vi version cũ rồi yêu cầu viết plan bằng Shop App Writing Plans + Ponytail. Plan nháp ở [2026-10-27-booking-sales-config-plan.md](../plans/2026-10-27-booking-sales-config-plan.md).

Plan đưa ra hai điều chỉnh PENDING sau khi đối chiếu B14 và FK/CHECK của schema: dùng lại AvailabilityService/Impl thay cặp service mới; QUANTITY dùng pool type, STANDING dùng pool sector chung, SEATED dùng từng seat, nên không cần capacity type tự suy ra cho SEAT_MAP. Các đề xuất class/method/capacity cũ ở §5/§7 chưa được phê duyệt và cần được thay bằng danh sách cuối trong plan nếu user duyệt. Tại thời điểm viết plan chưa thay đổi application/test Java.

User tiếp tục yêu cầu bỏ EntityManager, thay bằng `@Version`. Đã kiểm tra read-only DB thật `booking_db.booking_schema.tbl_event_sales_snapshots`: chỉ có `source_config_version`, chưa có `row_version` (10 cột). Plan cập nhật thêm field boxed `Long rowVersion`/cột bigint default 0, dùng `saveAndFlush` với version null cho entity mới. `sourceConfigVersion` giữ nguyên vai trò version nghiệp vụ.

User chọn ddl-auto update vì dự án chưa dùng Liquibase/Flyway: bỏ đề xuất file migration riêng, cập nhật YAML Booking từ none sang update. Khi triển khai, thêm field @Version và ColumnDefault 0 để schema update tạo cột mới có default cho row hiện hữu; field Java vẫn null trước insert. Chưa viết Java hoặc khởi động service để thay DB; chi tiết field/API sinh và xác minh startup nằm trong plan.
