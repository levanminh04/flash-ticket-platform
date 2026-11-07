# Spec — Setup scaffold Booking Service

- Phiên bản: `v0.4`; ngày: **2026-10-22**; trạng thái: `REVIEW_READY`.
- Người duyệt/ngày duyệt spec: chưa có. Người dùng đã chốt phạm vi **REST + snapshot/message của Booking** trong hội thoại; đây chưa phải phê duyệt bản spec đã viết.
- Đầu ra của task: scaffold Java và cấu hình có thể build; contract/mapping được đối chiếu tĩnh, không tạo thêm test source hoặc tuyên bố đã kiểm PostgreSQL runtime.
- Baseline repo khi lập spec: `ac7291b`. Nội dung bên dưới là đặc tả hiện thực dẫn xuất; không thay thẩm quyền B12/B13 hoặc xác nhận môi trường đang chạy.

## 1. Mục tiêu và phạm vi

Chuẩn bị nền tảng dữ liệu cho `services/booking-service`: đầy đủ entity theo database Booking, DTO tại các biên REST/message và các snapshot JSONB, mapper thuần chuyển đổi, YAML tích hợp với cấu hình hạ tầng đã bàn giao. Người nhận task có thể triển khai các luồng Booking sau đó mà không phải đoán kiểu tiền, khóa ghép, shape JSON hoặc tên bảng.

Giữ Java 21, Spring Boot 4.1.1, Spring Cloud BOM 2025.1.3, Maven Wrapper và package gốc `com.flashticket.booking_service` trong repo. Tận dụng JPA, Validation, AMQP, Resource Server và Lombok đã có; chỉ bổ sung MapStruct cùng annotation processor/Lombok binding đã chốt trong §6, không nâng stack hoặc thêm framework mapping khác.

Trong phạm vi:

- 15 JPA entity, 4 kiểu khóa ghép và các enum được dùng thực tế.
- DTO REST Booking, snapshot JSONB, envelope và payload Booking gửi/nhận; kiểu phụ lồng trong các hợp đồng đó.
- Validation tại biên và cấu hình JSON tối thiểu cần để giữ đúng wire format B13.
- Hai mapper MapStruct cụ thể cho order view và outbox/envelope, cùng helper chuyển đổi các kiểu không thể map theo tên field.
- YAML bootstrap và cấu hình Booking tại Config Server; tài liệu biến môi trường không chứa secret.
- Kiểm tra build, generated mapper và đối chiếu tĩnh contract/DDL theo §9; giữ nguyên toàn bộ file test hiện có.

Ngoài phạm vi: controller, repository nghiệp vụ, service/use case, transaction giữ/trả/chốt tồn kho, khóa/CAS, tính tiền/giảm giá, scheduler expiry, outbox relay, consumer, HTTP client, Saga, tích hợp VNPay, cache Redis, cấu hình phân quyền Java, migration hoặc thao tác trên database dùng chung. Có entity outbox/inbox không có nghĩa là đã có cơ chế giao nhận hoặc idempotency chạy được.

## 2. Nguồn và thứ tự đối chiếu

| Nguồn | Nội dung dùng trong spec |
|---|---|
| [B12-v0.2, APPROVED](../../architecture/B12-data-ownership-and-schema.md), §3 và §4.2 | Ownership Booking, kiểu dữ liệu và các bất biến |
| [DDL Booking](../../architecture/data/postgres/20-booking.sql) | Danh sách đầy đủ cột, nullability, default, PK, FK, UNIQUE và CHECK |
| [Script tiền tố bảng](../../../services/99-table-prefix.sql) và [bootstrap PostgreSQL](../../../deploy/postgres/00-business-databases.sh) | Đổi tên bảng sau DDL sang `tbl_*`; principal `booking_admin`, role `booking_owner` |
| [B13-v0.2, REVIEW_READY](../../architecture/B13-api-and-event-contracts.md), §2–5, §7.1, §10.1 | Wire format và JSONB; tách phần baseline còn hiệu lực và phần v0.2 đang là ứng viên |
| [Implementation readiness](../../architecture/implementation-readiness.md), §1–3 | Stack đã chọn, phần được làm độc lập trước tích hợp v0.2 |
| [Booking handoff](../../../services/booking-service/HANDOFF.md) | Cách kết nối, Hikari role, giới hạn pool, các dependency tích hợp |
| [RabbitMQ definitions](../../../deploy/rabbitmq/definitions.json) | Tên exchange/queue/routing key được khai trong cấu hình triển khai hiện tại |
| [POM Booking](../../../services/booking-service/pom.xml), [bootstrap YAML](../../../services/booking-service/src/main/resources/application.yaml), [Config Server YAML](../../../infrastructure/config-server/src/main/resources/config/booking-service.yaml) | Cấu trúc hiện có để mở rộng |
| [MapStruct 1.6.3 installation](https://mapstruct.org/documentation/installation/) và [Lombok integration](https://mapstruct.org/documentation/stable/reference/html/#lombok) | Dependency, annotation processor và `lombok-mapstruct-binding` |

DDL dùng tên chưa có tiền tố; mapping Java dùng tên sau script rename, ví dụ `booking_schema.tbl_orders`. Không đổi tên cột hoặc diễn giải tên bảng tài liệu thành một schema mới. Đối chiếu tài liệu/DDL và script là kiểm tĩnh; spec này chưa đọc catalog database thật.

## 3. Lựa chọn tổ chức

Chọn cấu trúc đơn giản: JPA entity trực tiếp, DTO Java record và hai mapper MapStruct. Không tạo thêm mô hình domain song song với entity.

| Phương án | Đánh đổi | Lựa chọn |
|---|---|---|
| Entity + record DTO + MapStruct | Sinh mapping tường minh ở compile time; thêm một dependency API và processor | Chọn theo yêu cầu người dùng |
| Mapper viết tay | Ít cấu hình Maven hơn nhưng không đáp ứng lựa chọn MapStruct | Không chọn |
| Tách domain/persistence model, generic mapper/base service | Tăng số kiểu và lớp trước khi có luồng nghiệp vụ | Ngoài phạm vi |

```text
com.flashticket.booking_service
├── entity/              15 entity, 4 EmbeddedId
├── enums/               enum được entity/DTO sử dụng
├── dto/
│   ├── request/         REST request và selection
│   ├── response/        REST response và Problem
│   ├── snapshot/        SalesConfiguration, PurchaseSnapshot và kiểu phụ
│   └── message/         Envelope<T> và payload gửi/nhận
├── mapper/              OrderMapper, OutboxMessageMapper, MappingValueConverter
├── validation/          ContractRules cho hằng/rule liên field dùng chung
└── config/              BookingJsonConfig cho coercion/required field ở §5
```

Không tạo thư mục rỗng cho tầng ngoài phạm vi. Kiểu con chỉ dùng trong một DTO đặt thành nested record; kiểu được dùng chung như `OrderItemView`, `SeatSelectionSnapshot`, `PromotionConfig` có nguồn khai báo duy nhất trong service. Không tạo module DTO dùng chung giữa năm service trong task này.

## 4. Entity và mapping persistence

### 4.1 Danh mục bắt buộc

Mọi bảng dưới đây thuộc schema `booking_schema`. Tất cả cột trong DDL phải được map; bảng sau ghi khóa và các nhóm dữ liệu để kiểm coverage, không thay danh mục cột của DDL.

| Entity | Bảng | PK | Dữ liệu cần bao phủ |
|---|---|---|---|
| `EventSalesSnapshot` | `tbl_event_sales_snapshots` | `eventId` | Organizer, sales mode, event status, cửa sổ bán, purchase limit, cấu hình JSON, version nguồn, receivedAt |
| `Order` | `tbl_orders` | `id` | Event/buyer/sector/mode, lifecycleState, email, bộ freeze/version/snapshot, acceptedChargeId/acceptedAt, subtotal/discount/total/currency, expiry, version nguồn/dòng, createdAt/updatedAt |
| `OrderItem` | `tbl_order_items` | `id` | Order/event/type/sector/mode, tên loại vé snapshot, quantity, unitPrice/lineSubtotal/currency, selectionSnapshot |
| `OrderItemSeat` | `tbl_order_item_seats` | `(orderItemId, seatId)` | Order/event/sector và liên kết ghế thuộc item |
| `Reservation` | `tbl_reservations` | `id` | Order duy nhất, event/buyer, state, expiry, rowVersion, createdAt |
| `ReservationAllocation` | `tbl_reservation_allocations` | `id` | Reservation/order/item/event, resourceKind, ID tài nguyên nullable theo kind, quantity, state |
| `TicketTypeInventory` | `tbl_ticket_type_inventory` | `ticketTypeId` | Event, configuredCapacity, held/purchased, sourceConfigVersion, rowVersion |
| `SectorInventory` | `tbl_sector_inventory` | `sectorId` | Event, sectorType, configuredCapacity, held/purchased, sourceConfigVersion, rowVersion |
| `SeatInventory` | `tbl_seat_inventory` | `seatId` | Event/sector/type, isHidden, sectorType, seatLabel, state, currentReservationId, reservationGeneration, sourceConfigVersion, rowVersion |
| `PurchaseLimit` | `tbl_purchase_limits` | `(eventId, buyerSubject)` | ConfiguredLimit, held/purchased, sourceConfigVersion, rowVersion |
| `PromotionInventory` | `tbl_promotion_inventory` | `promotionId` | Event, configuredLimit/perUserLimit, held/used, cửa sổ áp dụng, cấu hình JSON, sourceConfigVersion, rowVersion |
| `PromotionUsage` | `tbl_promotion_usages` | `id` | Promotion/event/reservation/buyer, generation, state, discount/currency, expiry, rowVersion |
| `IdempotencyRequest` | `tbl_idempotency_requests` | `(actorSubject, operation, requestKey)` | RequestHash, resourceId, responseStatus, createdAt |
| `OutboxMessage` | `tbl_outbox_messages` | `messageId` | Aggregate ID/version, messageType/schemaVersion, payload, occurredAt, correlation/causation/traceparent, publishedAt, attempts/nextAttemptAt/lastErrorCode |
| `InboxMessage` | `tbl_inbox_messages` | `(consumerName, messageId)` | PayloadHash, processedAt, responsePayload nullable |

Khóa ghép dùng `@EmbeddedId`: `OrderItemSeatId`, `PurchaseLimitId`, `IdempotencyRequestId`, `InboxMessageId`. Mỗi kiểu là class embeddable có constructor không tham số, `Serializable`, `equals/hashCode` theo đúng các thành phần khóa. Không sinh UUID cho khóa ghép.

### 4.2 Quy ước Java/SQL

- Khai báo `@Table(name = "tbl_...", schema = "booking_schema")`; map tên cột snake_case tường minh, nullable đúng DDL. Không dùng `BaseEntity` vì các bảng không có cùng bộ audit/version/ID.
- `uuid` → `UUID`; subject/text/domain nonempty → `String`; `timestamptz` → `Instant`; `integer` → `Integer`; `bigint` → `Long`; `boolean` → `Boolean`. Dùng kiểu nullable khi cột cho phép null.
- `money_amount`/`numeric(19,0)` → `BigDecimal`, precision 19, scale 0. `currency_code` → `Currency` chỉ có `VND`, map string; không dùng `java.util.Currency` như JPA enum.
- `row_version` có `@Version` ở đúng 8 entity có cột này: Order, Reservation, ba inventory vé/sector/seat, PurchaseLimit, PromotionInventory, PromotionUsage. `source_config_version`, `payment_snapshot_version`, `generation`, `aggregate_version` không có `@Version`.
- Enum map bằng `EnumType.STRING`, giữ nguyên token ở DDL/B13. Không tự tạo native PostgreSQL enum hoặc dùng ordinal.
- Giữ FK bằng trường ID scalar; không dựng object graph `@OneToMany`/`@ManyToOne`, cascade hoặc orphan removal trong scaffold. FK ghép/unique/check vẫn do schema đã có bảo vệ; ID scalar không có nghĩa là bỏ các ràng buộc đó.
- Entity có constructor không tham số phù hợp JPA, getter/setter cần thiết; tránh Lombok `@Data`, tự sinh `toString` chứa email/snapshot/payload, và `equals/hashCode` theo mọi field.
- Các PK có `DEFAULT gen_random_uuid()` trong DDL (Order, OrderItem, Reservation, ReservationAllocation, PromotionUsage) dùng generation UUID của JPA cho bản ghi mới. ID snapshot/inventory/ghế/promotion và message phải giữ ID do nguồn/caller cấp.
- `createdAt`, `updatedAt`, các thời điểm xử lý và retry không được thêm callback tự cập nhật hàng loạt. DDL default giữ nguyên; khi code ghi ở task sau, caller chịu trách nhiệm cung cấp/audit các thời điểm cần thiết. Scaffold không hứa có cơ chế audit hoặc update timestamp tự động.

Enum persistence: `SalesMode {QUANTITY, SEAT_MAP}`, `EventState {DRAFT, PENDING_APPROVAL, APPROVED, PUBLISHED, CANCELLED}`, `OrderState {PENDING_PAYMENT, ISSUING, COMPLETED, CANCELLED, EXPIRED, ISSUANCE_FAILED}`, `ReservationState {HELD, COMMITTED, RELEASED}`, `ResourceKind {TICKET_TYPE, STANDING_SECTOR, SEAT}`, `SectorType {SEATED, STANDING}`, `SeatState {AVAILABLE, HELD, PURCHASED, UNAVAILABLE}`, `PromotionUsageState {HELD, USED, RELEASED}`, `Currency {VND}`. Allocation dùng lại `ReservationState` vì cùng tập giá trị và nghĩa giữ/chốt/trả.

### 4.3 JSONB

Map JSONB bằng hỗ trợ Hibernate hiện có (`@JdbcTypeCode(SqlTypes.JSON)`), không thêm thư viện Hibernate Types. Shape theo B13 §10.1:

| Entity.field | Kiểu nội dung |
|---|---|
| EventSalesSnapshot.configurationSnapshot | `SalesConfiguration`, schemaVersion=1 |
| Order.frozenPurchaseSnapshot | `PurchaseSnapshot`, nullable trước freeze |
| OrderItem.selectionSnapshot | `SelectionSnapshot(schemaVersion=1, seats: List<SeatSelectionSnapshot>)` |
| PromotionInventory.configurationSnapshot | `PromotionConfigurationSnapshot(schemaVersion=1, configuration: PromotionConfig)` |
| OutboxMessage.payload | JSON tree chứa **data** của đúng message type; metadata envelope nằm ở cột riêng |
| InboxMessage.responsePayload | JSON tree kết quả có kiểu của command, nullable nếu không có response |

Hai cột payload đa hình dùng JSON tree tại tầng lưu trữ. Khi chuyển qua biên phải chuyển sang DTO có kiểu và validate theo messageType; không coi `Map<String,Object>`, object rỗng hoặc kiểm `jsonb_typeof='object'` là đủ. Các snapshot có một shape cố định phải dùng kiểu cụ thể, không JSON tree tùy ý.

## 5. DTO và validation

### 5.1 Quy tắc chung

- Dùng record và Jakarta Validation; collection con có `@Valid`, phần tử không null. Giữ đúng tên JSON B13; có thể thêm hậu tố Java `Request`/`Response` mà không đổi field wire.
- ID dùng `UUID`; thời gian dùng `Instant`, serialize UTC ISO-8601 có `Z`. Field required phải hiện diện, kể cả field được phép có giá trị null; không biến missing thành null mặc định trái B13.
- Tiền trên DTO là **String** khớp `^(0|[1-9][0-9]{0,18})$`; các field giá/tổng phải >0. Entity dùng BigDecimal. Không cho phép JSON number, số mũ, phần lẻ, dấu cộng, số âm hoặc làm tròn khi parse.
- Version/capacity/counter wire là **String**, trong miền PostgreSQL bigint. Version >0; capacity và availability cho phép 0. `rowVersion` là chi tiết entity, không tự thêm vào response. `schemaVersion` là integer bằng 1; quantity/purchaseLimit integer >0 trong int32; percent integer 1..100.
- Không dùng số thực cho tiền/version/counter. Tắt coercion number→String và float→integer cho DTO; `"quantity":"2"` cũng bị từ chối. Reject unknown fields và enum không hợp lệ; không nhận alias.
- Dùng ObjectMapper do Spring quản lý trong mapper/runtime. `BookingJsonConfig` chỉ bổ sung cấu hình coercion/required-field cần để đáp ứng các quy tắc này, không tạo một ObjectMapper độc lập có hành vi khác runtime. Đa hình selection dùng annotation Jackson native trên sealed hierarchy. Không assume cấu hình mặc định của Jackson/Hibernate đã đủ; task này không có test runtime để chứng minh JSONB round-trip.
- Validation trong task này kiểm hình dạng và tính nhất quán của **một payload**. Ownership, dữ liệu DB, stock, trạng thái hiện tại và deadline theo đồng hồ Booking thuộc task nghiệp vụ sau.

### 5.2 REST Booking

Trường và nullability lấy nguyên B13 §3. Danh sách sau xác định kiểu phải có, không tạo endpoint ở task scaffold.

| DTO | Fields / kiểu con | Sử dụng |
|---|---|---|
| `CreateOrderRequest` | eventId, customerEmail, selection | POST `/api/v1/orders` |
| `OrderSelection` | Ba biến thể bên dưới | selection của CreateOrder |
| `OrderSelection.QuantityItem` | ticketTypeId, quantity | QUANTITY/STANDING; nested vì chỉ selection hierarchy dùng |
| `ApplyPromotionRequest` | code không rỗng | POST `/api/v1/orders/{id}/promotion` |
| `StartPaymentRequest` | provider chỉ `VNPAY` | POST `/api/v1/orders/{id}/payment-attempts`, phần v0.2 CANDIDATE |
| `OrderView` | orderId, eventId, state, customerEmail, currency, subtotal, discount, total, expiresAt, paymentFrozenAt nullable, sourceConfigVersion, items | GET/create/promotion/cancel |
| `OrderItemView` | orderItemId, ticketTypeId, ticketTypeName, quantity, unitPrice, lineSubtotal, sectorId nullable, seats | Dùng chung trong OrderView/PurchaseSnapshot |
| `OrderPage` | items, nextCursor nullable | GET `/api/v1/orders` |
| `Availability` | eventId, sourceConfigVersion, asOf, ticketTypes, sectors, seats | GET `/api/v1/events/{id}/availability` |
| `PaymentRequestAccepted` | orderId, requestId, expiresAt | Receipt 202, phần v0.2 CANDIDATE |
| `Problem` | code, message, correlationId, violations[{field,code}] | Kiểu lỗi; chưa có exception handler |
| `FreezePaymentRequest` | buyerSubject | Guard nội bộ B13 §4; kiểu Java dẫn xuất từ input được mô tả, không thay contract bên ngoài |

Cancel có body `{}` theo B13, không tạo `CancelOrderRequest` rỗng. Idempotency-Key/correlation là header/envelope, không thêm vào CreateOrder body. GET list dùng cursor/page size 1..100 theo B13 §9; chưa viết query DTO hoặc pagination engine khi chưa có controller.

Ba biến thể selection và QuantityItem lồng trong cùng file `OrderSelection`. Jackson native phân giải hai tầng: `OrderSelection` theo `mode`, sau đó nested `OrderSelection.SeatMapSelection` theo `kind` đối với SEAT_MAP:

1. `OrderSelection.QuantitySelection`: `{mode:'QUANTITY',items:[{ticketTypeId,quantity}]}`.
2. `OrderSelection.SeatedSelection`: `{mode:'SEAT_MAP',sectorId,kind:'SEATED',seatIds:[UUID]}`.
3. `OrderSelection.StandingSelection`: `{mode:'SEAT_MAP',sectorId,kind:'STANDING',items:[{ticketTypeId,quantity}]}`.

Reject field của biến thể khác, kể cả truyền null; không dùng một record nhiều field tùy chọn làm mất phân biệt shape. List lựa chọn không rỗng, ID không trùng; customerEmail hợp lệ và tối đa 254 ký tự. Browser không được truyền buyerSubject, giá, tổng tiền hoặc state.

Availability có kiểu con ticketTypes `{ticketTypeId,available}`, sectors `{sectorId,kind,available}`, seats `{seatId,state}`. AvailabilityMapper hoặc phép trừ counter chưa nằm trong scaffold: record response nhận số đã được tầng truy vấn tính sau này.

### 5.3 Snapshot và kiểu phụ

| Kiểu | Nội dung bắt buộc / nguồn |
|---|---|
| `SeatSelectionSnapshot` | seatId, seatLabel, rowName, seatNumber; dùng lại cho item view và selection snapshot |
| `SelectionSnapshot` | schemaVersion, seats; QUANTITY/STANDING là mảng rỗng |
| `SalesConfiguration` | schemaVersion, eventId, organizerSubject, sourceConfigVersion, eventStatus, eventTitle, eventStartsAt/eventEndsAt, venueName nullable, mode, saleStartsAt/saleEndsAt, purchaseLimit, ticketTypes, sectors, seats, promotions; B13 §7.1 |
| `SalesConfiguration.TicketTypeConfig` | ticketTypeId, name, unitPrice, currency, sectorId nullable, configuredQuantity nullable, colorCode nullable |
| `SalesConfiguration.SectorConfig` | sectorId, kind, configuredCapacity |
| `SalesConfiguration.SeatConfig` | seatId, sectorId, ticketTypeId, seatLabel, rowName, seatNumber, isHidden |
| `PromotionConfig` | promotionId, code, startsAt/endsAt, maximumUses >0, maximumUsesPerUser=1, discount |
| `PromotionConfig.PromotionDiscount` | FIXED `{kind,amount}` hoặc PERCENT `{kind,percent}`; hai shape riêng, không field thừa |
| `PromotionConfigurationSnapshot` | schemaVersion, configuration:PromotionConfig |
| `PurchaseSnapshot` | schemaVersion, orderId, eventId, buyerSubject, customerEmail, sourceConfigVersion/sourceOrderVersion, frozenAt/expiresAt, currency, eventTitle, eventStartsAt/eventEndsAt, venueName nullable, mode, sectorId nullable, subtotal/discount/total, promotion nullable, items; B13 §4 |
| `PurchaseSnapshot.AppliedPromotionSnapshot` | promotionId, code, discount |
| `EventCancellation.AccessConfiguration` | eventId, organizerSubject, eventStatus, eventStartsAt/eventEndsAt, sourceConfigVersion; nested record vì chỉ EventCancellation dùng |

Các method `@AssertTrue` trên record gọi hàm thuần trong `ContractRules` để kiểm các quy tắc B13 xác định được từ một snapshot: schemaVersion; cửa sổ thời gian; mode QUANTITY không có sector/seat; ID duy nhất và tham chiếu type/sector tồn tại trong các danh sách của SalesConfiguration; tổng lineSubtotal/subtotal/discount/total; promotion null thì discount=0; khi item có seats thì số seat bằng quantity; frozenAt < expiresAt. Dùng Jakarta `@Pattern`/`@DecimalMax` trực tiếp cho tiền và bigint wire, không tạo annotation/validator riêng. Không tính lại tiền hay tự sửa payload sai. SalesConfiguration CANCELLED được có các mảng rỗng theo B13 §5.2, không áp quy tắc mở bán vào cancellation snapshot.

PurchaseSnapshot không có field sector kind. Vì vậy rule trong DTO không thể suy ra item có seats rỗng là STANDING hợp lệ hay SEATED bị thiếu ghế chỉ từ payload này. Đối chiếu kind, nguồn giá và quan hệ với cấu hình đã lưu thuộc task nghiệp vụ sau; không thêm field hoặc đoán kind từ seats để che thiếu thông tin.

### 5.4 Message Booking

`Envelope<T>` có đủ: messageId, messageType, schemaVersion, aggregateId, aggregateVersion, occurredAt, correlationId, causationId nullable, traceparent nullable, data. Version/schema/tiền tuân §5.1. Các payload dưới đây giữ nguyên field B13 §5.2.

| Chiều | messageType theo B13 | Payload / fields | Mức hiện hành |
|---|---|---|---|
| Nhận Event | `event.sales-configured.v1` | SalesConfiguration | Baseline |
| Nhận Event | `event.cancelled.v1` | EventCancellation(eventId,cancellationId,sourceConfigVersion,cancelledAt,actorSubject,sales,access) | Baseline |
| Nhận Payment | `booking.accept-payment.v1` | AcceptPayment(orderId,chargeId,attemptId,amount,currency,sourceOrderVersion) | Baseline |
| Gửi Payment | `booking.payment-decision.v1` | PaymentDecision(orderId,chargeId,decisionAt,outcome,acceptedChargeId nullable) | Baseline |
| Gửi Payment | `payment.start-requested.v1` | StartPayment(requestId,requestKey,requestHash,provider,purchase) | CANDIDATE v0.2 |
| Nhận Ticket | `ticket.issuance-result.v1` | IssuanceResult(orderId,chargeId,issuanceId,state,ticketCount,failureCode nullable); Booking dùng nhánh COMPLETED | Hợp đồng/đích sửa thuộc CANDIDATE v0.2 |
| Gửi Payment | `booking.order-completed.v1` | OrderCompleted(orderId,chargeId,issuanceId,ticketCount) | CANDIDATE v0.2 |
| Nhận Payment | `booking.issuance-result.v1` | BookingIssuanceResult(orderId,chargeId,issuanceId,state='FAILED',failureCode) | Shape FAILED-only thuộc CANDIDATE v0.2 |

Enum DTO bổ sung theo nơi dùng: provider VNPAY; payment decision ACCEPTED/LATE/DUPLICATE/CANCELLED; issuance result COMPLETED/FAILED; failure result chỉ FAILED; promotion discount FIXED/PERCENT. Không dùng một enum Status chung cho nhiều vòng đời.

Validation payload kiểm ACCEPTED có acceptedChargeId=chargeId; DUPLICATE có acceptedChargeId khác chargeId; FAILED có failureCode không rỗng và ticketCount=0; COMPLETED có failureCode=null. StartPayment.requestId phải bằng Envelope.messageId; đối chiếu tại hàm validation envelope/payload. Kiểm số vé so với frozen snapshot đã lưu, idempotency và tác dụng trạng thái thuộc handler ở task sau.

Không tạo DTO PaymentView, PaymentStart, StartPaymentResult, IssueTickets, FinanceConfiguration, TicketView hoặc seat-map editor: Booking không sở hữu/nhận/trả chúng trong các biên đã liệt kê. `EventCancellation.AccessConfiguration` là kiểu phụ nested cần thiết của cancellation, không phải consumer mới của Booking.

## 6. Mapper MapStruct

Thêm `org.mapstruct:mapstruct:1.6.3` vào dependencies và `org.mapstruct:mapstruct-processor:1.6.3` vào `annotationProcessorPaths` của execution `default-compile`. Vì entity dùng Lombok, thêm `org.projectlombok:lombok-mapstruct-binding:0.2.0` sau Lombok và trước MapStruct processor trong cùng processor path; giữ Lombok processor hiện có. Không thêm MapStruct Spring Extensions hoặc framework mapping thứ hai.

Mỗi mapper khai báo `@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR, uses = MappingValueConverter.class)`. Không tạo `@MapperConfig` riêng cho hai mapper. Build phải thất bại khi target có field mới chưa được map hoặc ignore tường minh; `ignore` chỉ dùng cho field vận hành được quy định ở §6.2.

`MappingValueConverter` là Spring component nhỏ chứa đúng các phép chuyển mà hai mapper dùng: `BigDecimal → Money string`, `Long → UInt64 string`, `SelectionSnapshot → seat views`, DTO có kiểu ↔ JSON tree qua ObjectMapper Spring quản lý. Không thêm chiều String→số khi chưa có write mapper. Nó không tính subtotal/discount, kiểm stock, đổi state hoặc gọi repository.

### 6.1 OrderMapper

Một interface MapStruct với các hàm thuần:

- `toView(Order order, List<OrderItem> items)` → OrderView; dùng `@Mapping` tường minh cho `id → orderId`, `lifecycleState → state` và `items`. Item đã có SelectionSnapshot để lấy seats; ID nội bộ khác chỉ xuất khi B13 có field.
- `toItemView(OrderItem item)` → OrderItemView; JSON selection snapshot → danh sách seat view.
- Tiền BigDecimal nguyên, không âm, tối đa 19 chữ số → chuỗi `toPlainString()` chuẩn không phần thập phân; version Long dương → chuỗi. Cho phép normalize scale chính xác bằng phép đổi scale không làm tròn; giá trị có phần lẻ thật phải bị từ chối.

Không nạp entity con, gọi repository hoặc query liên service. Không tự tính tổng/giảm, chọn seat, lọc quyền, đổi state, dựng PurchaseSnapshot mới hoặc sửa entity. Không map CreateOrderRequest thẳng thành entity vì dữ liệu còn phụ thuộc actor, giá, nguồn cung và transaction.

### 6.2 OutboxMessageMapper

- `toEntity(Envelope<?> envelope)` dùng mapping sinh bởi MapStruct để tách metadata ra cột và `MappingValueConverter` để serialize riêng `data` vào payload; messageId/version/timestamps do caller cấp. Các field vận hành được gán tường minh: publishedAt=null, attempts=0, nextAttemptAt=occurredAt, lastErrorCode=null. Caller tạo envelope mới với occurredAt hiện tại; replay không gọi hàm này để reset trạng thái vận hành của bản ghi đã có.
- `default <T> Envelope<T> toEnvelope(OutboxMessage row, Class<T> payloadType)` dựng lại envelope vì kiểu payload chỉ biết tại runtime. Default method gọi helper để parse payload đúng kiểu và giữ nguyên toàn bộ metadata; MapStruct vẫn sinh implementation Spring bean cho mapper. Kiểm messageType và payloadType tương ứng bằng mapping hữu hạn trong mapper, không dùng reflection theo class name từ message hoặc plugin registry.
- Dùng ObjectMapper Spring quản lý; validate payload/envelope, không âm thầm bỏ field. Không tạo ID/thời điểm mới khi dựng lại bản ghi để retry. Hàm này không publish, confirm, ACK hoặc cập nhật retry.

Không tạo mapper cho mỗi bảng inventory, generic base mapper hoặc mapper factory. Snapshot JSONB có kiểu được Hibernate serialize trực tiếp; không cần thêm lớp bọc mapper cho từng snapshot.

## 7. YAML và biến môi trường

### 7.1 Phân chia file

| File | Yêu cầu |
|---|---|
| `services/booking-service/src/main/resources/application.yaml` | Giữ application.name=booking-service và Config Server import bắt buộc như hiện tại; CONFIG_SERVER_URL default localhost:8888 |
| `infrastructure/config-server/src/main/resources/config/booking-service.yaml` | Port, datasource/JPA/Hikari, RabbitMQ và JWT của Booking theo mẫu dưới |
| `infrastructure/config-server/src/main/resources/config/application.yaml` | Tận dụng Eureka/Actuator chung hiện có; không sửa giá trị dùng chung cho service khác trong task này |

Nội dung đích của YAML riêng Booking; đây là mẫu spec, chưa được áp dụng vào runtime:

```yaml
server:
  port: ${BOOKING_SERVICE_PORT:8082}

spring:
  datasource:
    url: ${BOOKING_DB_URL}
    username: ${BOOKING_DB_USERNAME:booking_admin}
    password: ${BOOKING_DB_PASSWORD}
    hikari:
      connection-init-sql: SET ROLE booking_owner
      maximum-pool-size: 5
      minimum-idle: 1
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: none
    properties:
      hibernate:
        default_schema: booking_schema
        jdbc:
          time_zone: UTC
  rabbitmq:
    host: ${RABBITMQ_HOST}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:flash_ticket_app}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: ${RABBITMQ_VHOST:/}
    dynamic: false
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      exchange: ${RABBITMQ_BUSINESS_EXCHANGE:business.ex}
      mandatory: true
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 20
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI}
          jwk-set-uri: ${KEYCLOAK_JWK_SET_URI}
          audiences:
            - ${KEYCLOAK_AUDIENCE:flash-ticket-api}
```

Không có password mặc định. BOOKING_DB_URL do runtime cung cấp, ví dụ local qua SSH tunnel `jdbc:postgresql://127.0.0.1:15432/booking_db?currentSchema=booking_schema`; không ghi IP máy hạ tầng vào config chung. Role/pool theo handoff hiện tại; không nâng quyền hoặc đổi mô hình DB bằng task scaffold. Khi triển khai principal runtime khác phải sửa riêng theo mô hình quyền đã duyệt, không âm thầm bỏ SET ROLE.

RabbitMQ `dynamic: false` ngăn Spring tự khai topology; task không thêm Queue/Exchange/Binding bean. Manual ACK chỉ là chuẩn bị config; handler sau này phải ACK sau commit. JWT properties không chứng minh đã có mapping realm_access.roles, ownership hay internal authorization; SecurityFilterChain nằm ở task khác.

Ghi danh mục biến và hướng dẫn nạp vào process trong handoff Booking khi hiện thực. Spring không tự đọc `.env`. Phân biệt BOOKING_DB_PASSWORD của ứng dụng với BOOKING_DB_ADMIN_PASSWORD dùng bootstrap; operator cấp cùng credential phù hợp, không thêm một secret khác tùy tiện. Redis không xuất hiện trong YAML/POM của scaffold vì chưa có use case cache.

### 7.2 Chênh lệch transport phải ghi rõ

| Nội dung | B13 | Repo triển khai hiện tại | Cách xử lý trong scaffold |
|---|---|---|---|
| Exchange nghiệp vụ | `flash-ticket.v1` tại §5.1 | `business.ex` trong definitions và handoff | YAML dùng business.ex theo môi trường hiện tại; ghi deviation, không sửa B13 hoặc khai thêm exchange |
| Kết quả phát vé thành công | `ticket.issuance-result.v1` | booking.tickets-issued.q bind `ticket.issued.v1` | DTO theo B13; chưa bind/listen/publish |
| Kết quả lỗi gửi Booking | `booking.issuance-result.v1` | booking.issuance-failed.q bind `booking.issuance-failed.v1` | DTO theo B13; chưa thêm alias hoặc đoán chuyển đổi |

`OPEN-SCF-01`: trước task consumer/relay, Minh cùng owner Payment/Ticket chốt messageType khác routing key hay cần đồng bộ topology/contract. Việc hiện có queue không chứng minh data/envelope đã tương thích. Điểm này không chặn entity/DTO/mapper scaffold, nhưng chặn tuyên bố tích hợp message thành công. Không tự mở lại kiến trúc năm service/một Saga.

## 8. Luồng dữ liệu và ranh giới lỗi

Luồng được scaffold chuẩn bị:

1. JSON REST/message → deserialize DTO đúng shape → validate cú pháp/nhất quán một payload.
2. Tầng nghiệp vụ tương lai kiểm actor, dữ liệu, giá, nguồn cung, deadline và transaction; scaffold không có bước này.
3. Entity ↔ PostgreSQL/JSONB qua mapping; Order + items đã tải → OrderMapper → response DTO.
4. Envelope có kiểu → OutboxMessageMapper → entity lưu trữ; chiều đọc khôi phục cùng ID/metadata/payload.

Payload sai bị từ chối qua lỗi deserialize/validation; mapper nhận dữ liệu persistence không thể biểu diễn đúng hợp đồng thì báo lỗi rõ, không dùng 0, chuỗi rỗng hoặc AVAILABLE thay thế. `Problem` chỉ định dạng dữ liệu lỗi, HTTP status/advice và retry policy là công việc của task endpoint/handler. Không log JWT, credential, email hoặc toàn snapshot/payload.

## 9. Tiêu chí nghiệm thu task scaffold

| Mã | Điều kiện đạt | Bằng chứng cần có khi hiện thực |
|---|---|---|
| AC-01 | Đủ 15 entity và 4 khóa ghép; toàn bộ cột/kiểu/nullability khớp DDL sau prefix; đúng 8 @Version | Review từng field với DDL, kiểm compile; handoff ghi rõ chưa có catalog/runtime proof |
| AC-02 | Đủ DTO §5, không thêm API của owner khác | Review danh mục kiểu và annotation với B13; phần v0.2 ghi CANDIDATE |
| AC-03 | JSON boundary biểu diễn đúng shape, nullability và dạng số | Review BookingJsonConfig, Jakarta constraints, sealed hierarchy và ContractRules; chưa có automated contract proof |
| AC-04 | MapStruct sinh đủ hai implementation và bật unmapped-target error | Clean compile và kiểm generated sources; hành vi round-trip chưa có automated proof |
| AC-05 | YAML tách bootstrap/shared/service; không có secret, Redis hoặc topology declaration | Review YAML diff và biến môi trường trong handoff |
| AC-06 | Java 21 build và các check Maven hiện có đạt | Chạy `rtk proxy .\mvnw.cmd clean verify`; không tạo hoặc sửa test source |
| AC-07 | Không mở rộng scope bằng test scaffold | Diff không có đường dẫn mới/sửa dưới `services/booking-service/src/test`; không có persistence-test profile |
| AC-08 | Hai lệch routing key và lệch exchange được nêu trong handoff; không tuyên bố consumer hoạt động | Review handoff và không có consumer/relay trong diff scaffold |

Review thủ công tập trung vào các biên dễ sai: ba selection và field sai nhánh; tiền/version dạng chuỗi; required-nullable; snapshot totals/windows/seat count; message outcome/type; field nội bộ không lọt vào OrderView; outbox giữ metadata. Đây là checklist review code, không thay thế automated test hay PostgreSQL round-trip.

Không nghiệm thu JSON serialization/deserialization runtime, Bean Validation runtime, mapper behavior, PostgreSQL catalog/JSONB/composite-key round-trip, booking/payment E2E, tranh chấp tồn kho, idempotency, expiry, broker ACK/confirm hoặc JWT authorization trong task scaffold. Các kết quả đó cần task kiểm thử/nghiệp vụ riêng.

## 10. Phạm vi file và bàn giao

Khi hiện thực, thay đổi giới hạn trong Java production Booking, POM Booking, cấu hình riêng Booking ở Config Server và handoff Booking; không tạo hoặc sửa file dưới `src/test`. POM thêm đúng MapStruct API/processor và Lombok binding nêu ở §6; không thêm Redis, MapStruct Spring Extensions, framework mapping khác hoặc nâng stack. Không sửa DDL, definitions RabbitMQ, cấu hình của owner khác hoặc tài liệu B12/B13 để làm scaffold tự khớp.

Bàn giao kèm kết quả AC, danh mục kiểu đã tạo, tên biến môi trường và giới hạn chưa kiểm. Spec được lưu trong `docs/superpowers/specs/` theo workflow brainstorming; thư mục này đang được `.gitignore` bỏ qua trong repo. Giữ spec local, không ép thêm vào Git hoặc sửa ignore chỉ để commit tài liệu. Người dùng review bản spec này trước bước lập implementation plan; task hiện tại chỉ lập spec.
