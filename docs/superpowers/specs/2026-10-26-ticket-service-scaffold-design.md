# Spec — Setup scaffold Ticket Service theo Booking

- Ngày: 2026-10-26
- Trạng thái: **APPROVED — người dùng xác nhận spec đã duyệt ngày 2026-10-26, sau chỉnh sửa ponytail-review**
- Baseline Git đã đối chiếu: `b05621d`; tham khảo cả nội dung working tree hiện có của playbook và spec User
- Người dùng đã xác nhận: **chỉ setup scaffold như Booking**
- Đầu ra hiện tại là tài liệu cho task triển khai; chưa triển khai Ticket Service

## 1. Mục tiêu

Chuẩn bị Ticket Service đủ cấu trúc để các task nghiệp vụ tiếp theo triển khai phát hành vé, tra cứu/tải vé, giao nhận email và check-in. Scaffold bao gồm entity, enum, DTO REST/message/snapshot, validation, MapStruct, repository, controller/service rỗng, cấu hình persistence/security/RabbitMQ/JSON, xử lý lỗi và logging.

Mẫu triển khai là **source Booking hiện tại**, bao gồm controller/service rỗng đang có và error handling sau refactor. Spec User là mẫu cách trình bày, ranh giới phạm vi và tiêu chí nghiệm thu; User hiện chưa có scaffold tương ứng để sao chép source. Không dùng quyết định trong spec Booking cũ để ghi đè source hiện hành.

Hoàn tất scaffold nghĩa là mapping, wire DTO, bean graph và cấu hình kỹ thuật được chuẩn bị và kiểm tra ở mức đã nêu. Chưa có flow phát vé, SMTP hay check-in hoạt động.

## 2. Các hướng cân nhắc và lựa chọn

| Hướng | Đánh đổi | Kết luận |
|---|---|---|
| Chỉ runtime/configuration | Làm nhanh hơn nhưng còn thiếu model, DTO, mapper và cấu trúc feature | Không đáp ứng mức scaffold người dùng chọn |
| Scaffold đầy đủ theo Booking hiện tại | Chuẩn bị toàn bộ cấu trúc; hành vi nghiệp vụ được triển khai theo task riêng | **Đã chọn theo yêu cầu người dùng** |
| Scaffold kèm nghiệp vụ phát vé/check-in/email | Có luồng hoạt động sớm nhưng kéo theo transaction, khóa QR, idempotency và tích hợp các owner | Vượt phạm vi setup |

Đây là task gồm nhiều phần trong một module, cần spec đầy đủ. Không tạo shared framework, module phụ hoặc kiến trúc ports/adapters chỉ để giữ chỗ.

## 3. Nguồn và hiện trạng

| Cần xác định | Nguồn |
|---|---|
| Cách tổ chức scaffold | `docs/guides/agent-service-scaffold-playbook.md` |
| Mẫu thiết kế User | `docs/superpowers/specs/2026-10-25-user-service-scaffold-design.md` |
| Aggregate, ownership và invariant | B7; B12 §4.4 và phần từ điển `ticket_schema` |
| Mapping vật lý | `docs/architecture/data/postgres/40-ticket.sql`, `services/99-table-prefix.sql`, `deploy/postgres/00-business-databases.sh` |
| Wire DTO, quyền, QR và delivery | B13 §2–7 |
| Transaction/terminal fence/cancellation | B14 §1, §3, §5–6 |
| Kiểm chứng và quan sát | B15, B16 |
| Runtime | Ticket POM, bootstrap YAML, Config Server YAML, Ticket HANDOFF, `.env.example` |
| Transport đã provision | `deploy/rabbitmq/definitions.json` |
| Mẫu source | Booking `entity`, `dto`, `mapper`, `validation`, `config`, `security`, `exception`, `messaging` và các feature |

Ticket hiện chỉ có application, context test, Maven wrapper/POM, bootstrap YAML và cấu hình riêng gồm datasource, RabbitMQ, SMTP. Chưa có entity/DTO/mapper/repository, security chain, xử lý lỗi hoặc logging riêng.

Baseline stack của repo: Java 21, Spring Boot 4.1.1, Spring Cloud 2025.1.3. POM đã có MVC, JPA/PostgreSQL, validation, JWT Resource Server, AMQP, mail, Config Client, Eureka, Lombok và Maven quality gates. Khi triển khai đọc lại POM/source tại ref thực tế, không nâng/hạ stack trong task này.

B12/B13/B14 có các nhãn trạng thái và quyết định lịch sử khác nhau; spec này không tự nâng trạng thái hoặc sửa hợp đồng nguồn. Những chênh lệch còn ảnh hưởng tích hợp được ghi tại §12.

## 4. Phạm vi và giới hạn

Khi triển khai được sửa:

- `services/ticket-service/**`.
- `infrastructure/config-server/src/main/resources/config/ticket-service.yaml`.
- `.env.example` nếu thật sự thiếu tên biến cho cấu hình trong phạm vi.
- Tài liệu HANDOFF/TESTING của Ticket.

Để task sau: handler nghiệp vụ, listener/publisher/worker, tích hợp ngoài service và kiểm thử xuyên service.

Giữ phạm vi file nêu trên; không đổi schema/data đang chạy, topology RabbitMQ, realm hoặc Gateway. Không tạo endpoint thành công giả, method trả null hoặc ném UnsupportedOperationException để giữ chỗ.

## 5. Tổ chức code

Giữ package `com.flashticket.ticket_service`, chia feature theo mẫu Booking:

```text
config/         TicketJsonConfig, SecurityConfig, RabbitMqConfig,
                TicketLogFields, SafeStackTracePrinter
entity/         EventAccessSnapshot, Issuance, Ticket, TicketDelivery,
                CheckinAttempt, OutboxMessage, InboxMessage, InboxMessageId
enums/          IssuanceState, TicketState, EventState và enum có contract thực
dto/request/    CheckInRequest
dto/response/   TicketView, OrderTicketsView, TicketDeliveryAccepted,
                QrTokenView, CheckInResult, IssuanceProbeView, Problem
dto/snapshot/   PurchaseSnapshot, AccessConfiguration, TicketDisplaySnapshot,
                SalesConfiguration và các kiểu con cần cho EventCancellation
dto/message/    Envelope, IssueTickets, IssuanceResult, EventCancellation
mapper/         TicketMapper, IssuanceMapper, OutboxMessageMapper,
                MappingValueConverter
validation/     ContractRules và helper có rule tái sử dụng thực tế
security/       KeycloakRealmRoleConverter
exception/      ErrorCode, BusinessException, GlobalExceptionHandler
issuance/       controller/, repository/, service/, service/impl/
ticket/         controller/, service/, service/impl/
delivery/       controller/, repository/, service/, service/impl/
checkin/        controller/, repository/, service/, service/impl/
access/         repository/
messaging/      TicketRabbitErrorHandler, repository/, service/, service/impl/
```

Tên class/package trên là đề xuất của người viết spec; builder được đổi tên nhất quán theo nhiệm vụ. Không tạo thư mục rỗng hoặc lớp không phục vụ danh mục này.

Controller chỉ có `@RestController`, root `@RequestMapping`, final service field và constructor injection. Service interface chưa có method; implementation `@Service` inject đúng repository/mapper cần cho feature như Booking. Không thêm transaction, thuật toán hoặc dependency chưa được dùng để chuẩn bị một nghiệp vụ ngoài phạm vi.

## 6. Entity và repository

Database `ticket_db`, schema `ticket_schema`, bảng vật lý có prefix `tbl_`. Map **đủ mọi cột** trong `40-ticket.sql`, không chỉ các field nêu tóm tắt dưới đây.

| Entity / bảng | Yêu cầu chính |
|---|---|
| EventAccessSnapshot / tbl_event_access_snapshots | PK eventId UUID; organizerSubject String; eventStatus; starts/ends Instant; sourceConfigVersion Long > 0; receivedAt Instant |
| Issuance / tbl_issuances | UUID id/orderId/eventId; buyerSubject, paymentConfirmationId, customerEmail, inputHash; PurchaseSnapshot JSONB; expectedTicketCount Integer; state; created/completed/failedAt; failureCode; rowVersion |
| Ticket / tbl_tickets | UUID id/issuanceId/eventId/orderItemId/ticketTypeId; ordinal Integer; holderSubject; nullable sectorId/seatId; displaySnapshot JSONB; state; qrTokenCiphertext byte[]; keyVersion/hash; usedAt/usedBy; rowVersion |
| TicketDelivery / tbl_ticket_deliveries | UUID id/issuanceId/requestKey; requestedBy/channel/requestHash/recipientEmail; requested/finished/nextAttemptAt; outcome/failureCode; attempts Integer |
| CheckinAttempt / tbl_checkin_attempts | UUID id/requestKey/requestedEventId; nullable ticketId/replayOf; actorSubject/requestHash/resultCode/correlationId String; requestedAt Instant; succeeded Boolean |
| OutboxMessage / tbl_outbox_messages | UUID messageId/aggregateId/correlationId, nullable causationId; Long aggregateVersion; messageType/schemaVersion; JsonNode payload; occurredAt/traceparent/publish/retry metadata |
| InboxMessage / tbl_inbox_messages | EmbeddedId `(consumerName:String, messageId:UUID)`; payloadHash; processedAt; nullable JsonNode responsePayload |

Quy tắc mapping:

- UUID, Instant, enum STRING; TIMESTAMPTZ giữ ý nghĩa UTC. Map bytea dưới dạng byte[], không tự biến thành large-object OID.
- Chỉ **Issuance và Ticket** có `@Version` theo cột `row_version`. Source config version và aggregate version không phải optimistic version.
- `IssuanceState=PENDING/COMPLETED/FAILED`; `TicketState=VALID/USED/VOID`. Không thêm REFUNDED hoặc trạng thái chuyển tiếp do người viết spec tự suy ra.
- Các cột domain `nonempty_text` map String. Delivery channel/outcome và audit resultCode chưa có enum đóng ở DDL thì giữ String; không ép chung với enum wire. Audit có REPLAYED, trong khi CheckInResult wire không có giá trị đó.
- `CheckinAttempt.correlationId` là TEXT theo DDL; không sửa kiểu persistence thành UUID chỉ vì Envelope dùng UUID.
- Mapping đầy đủ nullable, unique và FK theo nguồn. `InboxMessageId` có equals/hashCode theo hai field, không có entity reference trong ID.
- Association trong owner ưu tiên LAZY. Issuance giữ tập tickets; helper add đồng bộ owning side. FK `(issuance_id,event_id)` cần được giữ đúng; không có hai writable mappings cùng ghi một cột. Không cascade REMOVE/orphanRemoval trên lịch sử vé/audit.
- Không `@Data` trên entity; không tạo entity Order/Event của owner khác hoặc quan hệ JPA xuyên database.
- PurchaseSnapshot có kiểu; outbox/inbox giữ Jackson 3 JsonNode ở storage boundary. Dùng `@JdbcTypeCode(SqlTypes.JSON)` và cấu hình Hibernate JSON mapper tương thích Booking.
- Không dùng `ddl-auto=update/create`; constraint/index/trigger của PostgreSQL vẫn là nguồn enforcement, annotation không thay thế chúng.
- Không cấp token, khóa, hash, trạng thái completed hoặc timestamp nghiệp vụ qua entity constructor/mapper.

Repository cơ bản theo access path rõ ràng:

| Feature | Repository |
|---|---|
| access | EventAccessSnapshotRepository |
| issuance | IssuanceRepository |
| delivery | TicketDeliveryRepository |
| checkin | CheckinAttemptRepository |
| messaging | InboxMessageRepository, OutboxMessageRepository |

Mỗi repository chỉ extends JpaRepository với ID type thật. Ticket là con trong aggregate Issuance; scaffold chưa thêm TicketRepository riêng. Task lookup/check-in sau có thể bổ sung access path khi có use case cụ thể. Chưa thêm query nghiệp vụ, `@Lock`, EntityGraph, custom SQL hoặc cơ chế dedupe.

## 7. DTO, snapshot và validation

DTO owner-local, JSON tương thích B13; không import package Booking hoặc tạo dependency Maven sang Booking.

### 7.1 REST

| DTO | Shape |
|---|---|
| TicketView | ticketId, eventId, orderItemId, ordinal, ticketTypeName, nullable seatLabel, state VALID/USED/VOID |
| OrderTicketsView | issuanceState PENDING/COMPLETED/FAILED; tickets: TicketView[] |
| TicketDeliveryAccepted | deliveryId UUID; state cố định PENDING |
| QrTokenView | qrToken String; ảnh QR theo Accept được xử lý tại handler sau |
| CheckInRequest | đúng qrToken String không trắng; không có actor, event owner hoặc ticket state từ browser |
| CheckInResult | attemptId, eventId, nullable ticketId, result, succeeded, processedAt |
| IssuanceProbeView | hai shape theo B13: PENDING chỉ state/orderId/issuanceId; terminal dùng đầy đủ IssuanceResult |
| Problem | code, message, correlationId, violations[{field,code}] |

CheckInResult.result giữ đúng SUCCESS, NOT_EVENT_OWNER, OUTSIDE_WINDOW, UNKNOWN_QR, WRONG_EVENT, NOT_ISSUED, VOID, ALREADY_USED, EVENT_CANCELLED. Không đưa REPLAYED ra wire hoặc thêm state PENDING vào IssuanceResult terminal.

Không tạo request gửi lại vé có recipientEmail: B13 dùng body `{}` và email snapshot của đơn. `Idempotency-Key` là HTTP header của task handler sau, không tự thêm vào body. Không thêm API CRUD vé hoặc API đổi chủ vé.

### 7.2 Snapshot và message

- PurchaseSnapshot và toàn bộ kiểu con theo B13 §4: order/event/buyer/email, source versions, frozen/expiry, event display, mode/sector, tiền/promotion và items/seats. Giữ đúng required-nullable; không lược field chưa dùng.
- AccessConfiguration theo B13 §5.2: eventId, organizerSubject, eventStatus, eventStartsAt, eventEndsAt, sourceConfigVersion.
- EventCancellation nhận **đủ** eventId, cancellationId, sourceConfigVersion, cancelledAt, actorSubject, sales và access. Có các kiểu SalesConfiguration/PromotionConfig/... cần thiết để deserialize full payload; không bỏ sales vì Ticket chỉ áp access. Không đưa geometry/editor DTO vào Ticket khi không xuất hiện trong payload này.
- IssueTickets đúng `{orderId,chargeId,purchase}`. Kiểm orderId trùng purchase.orderId; dữ kiện xác minh caller/charge thuộc nghiệp vụ sau.
- IssuanceResult đúng `{orderId,chargeId,issuanceId,state,ticketCount,failureCode}`. COMPLETED: ticketCount > 0 và failureCode=null; FAILED: ticketCount=0 và failureCode không trắng. So số vé với purchase cần dữ liệu khác là guard use case sau.
- Envelope giữ messageId/messageType/schemaVersion/aggregateId/aggregateVersion/occurredAt/correlationId/causationId/traceparent/data theo B13. Allowlist type/payload của Ticket: event.access-configured.v1, event.cancelled.v1, ticket.issue.v1, ticket.issuance-result.v1.

**TicketDisplaySnapshot là thiết kế nội bộ**, vì DDL mới quy định object với schemaVersion=1, chưa có shape chi tiết trong B13. Record bất biến chỉ gồm `schemaVersion=1`, `ticketTypeName`, nullable `seatLabel`, lấy từ item/seat của purchase. Tên, thời gian và địa điểm event lấy từ PurchaseSnapshot đã lưu trong issuance khi cần hiển thị. Snapshot này không chứa email, QR hoặc trạng thái hiện hành và không tạo contract liên service mới.

### 7.3 Quy tắc boundary

- Wire Money là chuỗi nguyên VND, tối đa 19 chữ số theo B13; version là chuỗi nguyên dương trong bigint PostgreSQL. Counter cấu hình được phép 0; rowVersion được phép 0. Không dùng một regex chung cho tất cả.
- Quantity/ordinal là JSON integer dương trong int32; reject decimal/coercion. Subject là chuỗi không trắng, không ép UUID/email.
- Timestamp ISO UTC; nullability và optional phải khớp từng shape. JsonMapper strict không được làm field optional hợp lệ thành bắt buộc.
- Bean Validation có `@Valid` cho payload/list lồng; ContractRules thuần Java kiểm tổng tiền, duplicate ID, mode/sector/seat, freeze window và source schema theo B13. Nối rule thật từ DTO/boundary, không để helper không được gọi hoặc xuất field validation ngoài JSON.
- Cancellation full snapshot CANCELLED hợp lệ có thể mang mảng rỗng khi event chưa cấu hình vé; không áp rule mở bán của PUBLISHED lên mọi EventCancellation.
- EventCancellation phải nhất quán eventId/sourceConfigVersion giữa envelope payload, sales và access; sales/access có trạng thái CANCELLED. Kiểm envelope aggregateId với ID aggregate theo từng message (Event: eventId; IssueTickets/IssuanceResult: orderId), không mặc định mọi correlationId phải bằng aggregateId vì message Event không phải giao dịch mua.
- List bất biến với defensive copy; giữ cách báo lỗi null element rõ ràng như Booking. Validation không gọi DB, kiểm ownership/clock hoặc thực thi transaction.

## 8. Mapper và JSON

Thêm MapStruct 1.6.3 và lombok-mapstruct-binding 0.2.0 theo Booking, giữ Spring component model và `unmappedTargetPolicy=ERROR`. Không ignore hàng loạt để compile qua.

- TicketMapper: Ticket + display snapshot sang TicketView; không trả ciphertext/hash/key version/holderSubject/audit. Không chuyển QR ciphertext thành qrToken trong mapper.
- IssuanceMapper: issuance cùng tập tickets đã tải sang OrderTicketsView. Không tự lazy-load bằng query; kiểm đầu vào PENDING/FAILED không lộ tập vé chưa hoàn tất. Đây là mapping invariant, chưa thực hiện transition issuance.
- OutboxMessageMapper: Envelope typed ↔ entity storage, giữ ID/type/version/time/correlation/causation/traceparent/data. Tạo row mới khởi tạo retry metadata như Booking; dựng lại envelope từ row không sinh messageId hoặc occurredAt mới.
- MappingValueConverter: chuyển bigint/version và JsonNode có validation. Không tạo generic CRUD/base mapper hoặc mapper cho từng bảng chỉ để đủ số lượng.

Jackson 3 dùng `tools.jackson.*`, JsonMapper và JacksonJsonMessageConverter như Booking; Jackson annotations vẫn `com.fasterxml.jackson.annotation.*`. Cấu hình reject field lạ, số→String và float→integer ngoài contract; giữ nullable/optional đúng. Không sao chép OrderSelectionDeserializer hoặc toàn bộ logic chọn ghế của API tạo đơn vào Ticket.

Mapper không query DB, cấp ID/token/hash, gửi message/email hoặc quyết định nghiệp vụ. Chưa tạo mapper trả IssuanceResult/probe terminal từ entity vì chargeId chưa được giải quyết tại §12.

## 9. Security và controller rỗng

JWT Resource Server MVC stateless, validate signature/JWKS, issuer, audience và expiry qua cấu hình. Converter allowlist BUYER, ORGANIZER, ADMIN, INTERNAL_SERVICE theo các route cần dùng; claim thiếu/sai kiểu không được cấp role.

| Route chuẩn bị | Policy |
|---|---|
| GET /api/v1/orders/{orderId}/tickets | BUYER |
| GET /api/v1/tickets/{ticketId}/qr | BUYER |
| POST /api/v1/orders/{orderId}/ticket-deliveries | BUYER |
| POST /api/v1/events/{eventId}/check-ins | ORGANIZER |
| GET /internal/v1/orders/{orderId}/issuance | INTERNAL_SERVICE |
| /actuator/health và health subpath | Public, không lộ chi tiết nhạy cảm |
| Các actuator/path/method còn lại | Deny |

Matcher giới hạn theo path/method; không mở toàn bộ `/api/v1/orders/**` hoặc `/api/v1/events/**` chỉ vì controller có root tương ứng. Controller rỗng vẫn chưa có các handler này; fixture test có thể dùng route giả lập trong test để kiểm security matcher.

Root controller đề xuất: OrderTicketsController `/api/v1/orders`, TicketController `/api/v1/tickets`, TicketDeliveryController `/api/v1/orders`, CheckInController `/api/v1/events`, InternalIssuanceController `/internal/v1/orders`. Trùng root giữa các controller chưa có handler không tạo API thành công giả; task nghiệp vụ sau gắn subpath/method đúng bảng trên.

Ownership là trách nhiệm handler/service tương lai: buyer theo buyerSubject của issuance, organizer theo event access snapshot. Role ADMIN/ORGANIZER không tự cho phép tải QR của buyer; INTERNAL_SERVICE không được vào route người dùng. Không tuyên bố ownership đã hoạt động chỉ vì matcher đã có.

401/403 đi qua HandlerExceptionResolver và GlobalExceptionHandler như Booking, giữ WWW-Authenticate phù hợp. ERROR dispatch được xử lý để giữ status gốc. Gateway hiện có route tới Ticket phải được đối chiếu khi tích hợp; task scaffold không thay route của owner khác.

## 10. Runtime, dependency và cấu hình

Bootstrap giữ `spring.application.name=ticket-service` và Config Server import bắt buộc. Config Server riêng Ticket dùng port `${TICKET_SERVICE_PORT:8084}`; tận dụng common Eureka/Actuator hiện có.

| Nhóm | Cấu hình đích |
|---|---|
| Datasource | TICKET_DB_URL, TICKET_DB_USERNAME mặc định ticket_admin, TICKET_DB_PASSWORD; không có password mặc định |
| Hikari/JPA | SET ROLE ticket_owner; max pool 5/min idle 1; open-in-view=false; ddl-auto=none; default_schema=ticket_schema; jdbc.time_zone=UTC; json_format_mapper=jackson3 như Booking |
| Rabbit | env host/port/user/password/vhost; dynamic=false; confirms correlated, returns=true; exchange business.ex qua env; mandatory=true; MANUAL ACK; prefetch=20 |
| JWT | KEYCLOAK_ISSUER_URI, KEYCLOAK_JWK_SET_URI, KEYCLOAK_AUDIENCE mặc định flash-ticket-api |
| SMTP | Giữ mail starter và các biến MAIL_HOST/PORT/USERNAME/PASSWORD hiện có; chưa gửi mail hoặc kiểm SMTP bằng lời gọi nghiệp vụ |
| Log | Native Boot structured JSON, UTF-8; serviceName=ticket-service; metadata/version/instance/environment qua env |

Giữ SET ROLE vì tài khoản bootstrap là NOINHERIT theo handoff. URL runtime local qua tunnel ví dụ `jdbc:postgresql://127.0.0.1:15432/ticket_db?currentSchema=ticket_schema`; không hardcode IP server hoặc credential vào source. Spring không tự nạp `.env`; HANDOFF hướng dẫn IDE/process nạp biến và dùng lại tunnel hiện có.

RabbitMqConfig dùng JsonMapper chung với REST và safe listener error handler như Booking. Không declare Queue/Exchange/Binding/RabbitAdmin; không tạo listener thật trong scaffold. MANUAL ACK chỉ là cấu hình chuẩn bị, chưa có bằng chứng ACK sau commit.

Mail starter phù hợp ownership Ticket nên giữ, khác User. Không thêm QR/crypto/template dependency, Redis, Feign, OAuth2 Client, SDK Admin hoặc framework observability. Giữ Java 21 Enforcer, Spotless, JaCoCo và SpotBugs; không bypass gate rồi ghi là đã đạt Java 21.

## 11. Xử lý lỗi và logging

Theo Booking hiện tại: ErrorCode giữ status/message an toàn và phân loại native exception; BusinessException mang code; một GlobalExceptionHandler tạo Problem và ghi log. Không khôi phục lớp observability/correlation filter/custom /error đã bỏ.

Phân loại validation/deserialization 400, auth 401/403, data conflict/unavailable và lỗi nội bộ phù hợp. Không biến mọi IllegalArgumentException thành 400; lỗi return value/invariant nội bộ là 500. Chưa tự tạo đầy đủ lỗi nghiệp vụ check-in/issuance khi handler chưa tồn tại.

Native JSON log có schemaVersion số, timestamp UTC và serviceName Ticket; safe stack printer bỏ raw exception messages. Whitelist field theo B16, không tự export mọi MDC field. Correlation ở Problem/header/log phải khớp và được validate theo mẫu Booking; không yêu cầu mọi success response có custom correlation header.

Không log request body, email đầy đủ, JWT/Authorization, raw QR, QR ciphertext/hash, khóa mã hóa, credentials, JDBC URL có secret hoặc toàn purchase payload. Không gọi baseline log local là đã nghiệm thu RCA/tracing end-to-end. Boot /error native không mặc nhiên theo shape Problem của advice.

## 12. Chênh lệch và điểm cần chốt trước nghiệp vụ

| Mã | Hiện trạng | Xử lý ở scaffold | Điều kiện cho task sau |
|---|---|---|---|
| OPEN-TKT-01 | DDL Issuance có payment_confirmation_id TEXT; IssueTickets/IssuanceResult dùng chargeId UUID, không truyền confirmationId | Map entity paymentConfirmationId:String đúng DDL và DTO chargeId:UUID đúng B13; chưa có mapper chuyển hai field | Chốt cách giữ/khôi phục chargeId hoặc đổi contract/schema có duyệt; không tự coi confirmationId=chargeId |
| OPEN-TKT-02 | B13 dùng exchange flash-ticket.v1, deployed definitions dùng business.ex | Config theo business.ex hiện hành, DTO giữ messageType B13 | Chốt transport trước consumer/relay |
| OPEN-TKT-03 | B13 dùng ticket.issuance-result.v1; definitions route success ticket.issued.v1 và failure ticket.issuance-failed.v1; nhánh Payment→Booking dùng booking.issuance-failed.v1 thay cho booking.issuance-result.v1 | Ghi rõ ba lệch; không thêm alias, binding hoặc đổi messageType để né | Owner Booking/Payment/Ticket chốt routing key khác messageType hay đồng bộ contract/topology |

Incoming queue Ticket đã provision: ticket.access-configured.q → event.access-configured.v1, ticket.event-cancelled.q → event.cancelled.v1, ticket.issuance.q → ticket.issue.v1; có DLQ tương ứng. Việc có queue không chứng minh payload/source authorization hoặc handler đã đúng.

Các điểm mở này không chặn model/config/DTO scaffold. Chúng chặn triển khai hoặc nghiệm thu phát vé/probe/relay tương ứng, phải được chuyển nguyên vào HANDOFF. Không tự sửa B12/B13/DDL/broker trong task setup.

## 13. Kiểm chứng và tiêu chí nghiệm thu khi triển khai

Task viết spec hiện tại không chạy build/test service. Khi triển khai scaffold, kiểm đúng các boundary đã tạo, không viết test nghiệp vụ chưa có hoặc test chỉ đếm file/class.

| Mã | Điều kiện đạt | Bằng chứng |
|---|---|---|
| AC-01 | Đủ 7 entity + InboxMessageId, đúng cột/kiểu/prefix/schema; đúng 2 optimistic versions | Bảng đối chiếu DDL, clean compile; không coi compile là DB proof |
| AC-02 | DTO/payload/Envelope đúng B13, nullable/optional và validation có nối thực | Fixture JSON/Bean Validation: hợp lệ và field lạ/sai kiểu, tiền/version, mode/seat, failed/completed |
| AC-03 | MapStruct sinh implementation, mapping không lộ dữ liệu nội bộ | Mapper tests với dữ liệu xác định; outbox round-trip giữ metadata; không giả chargeId từ confirmationId |
| AC-04 | Feature/controller/service/repository nối bean graph, chưa có handler/listener/job | Context kiểm bean graph với repository test doubles khi không có DB; không coi mock là persistence proof |
| AC-05 | JWT route policy và error boundary đúng | Fixture JWT kiểm signature/issuer/audience/expiry/role; fixture route kiểm 401/403/Bearer, human vs machine, default deny |
| AC-06 | Problem/log an toàn, correlation nhất quán; Rabbit converter/error handler được nối | Tests lỗi boundary có ý nghĩa và formatter với sentinel secret; kiểm không declare topology |
| AC-07 | YAML tách bootstrap/common/Ticket, đúng env/JPA/mail/Rabbit/security | Review config/HANDOFF; test-only isolation không trở thành runtime fallback |
| AC-08 | Quality gates hiện có qua với Java 21 | Trong services/ticket-service: `rtk proxy .\mvnw.cmd -B -ntp verify` |
| AC-09 | Phần chưa kiểm và OPEN-TKT được bàn giao đúng | HANDOFF/TESTING nêu đã chạy/chưa chạy, môi trường và giới hạn |

Test configuration hiện tắt datasource/JPA/Rabbit/mail/Config Server/Eureka. Khi có repository/config bean mới, cập nhật isolation và test doubles có chủ đích; không tắt security/advice cần kiểm hoặc thêm biến môi trường giả vào production để context qua.

Persistence round-trip/catalog/constraint và smoke boot chỉ được kết luận khi thật sự chạy trên môi trường thích hợp. Nếu kiểm ghi fixture thì dùng PostgreSQL test riêng áp đúng DDL/prefix; không insert/delete fixture vào DB triển khai. Cấu hình profile integration opt-in là lựa chọn của builder khi cần kiểm mapping thực, không phải lý do thêm cả bộ E2E vào setup.

Khi hạ tầng sẵn sàng có thể kiểm Config Server config Ticket, Eureka registration, kết nối PostgreSQL bằng read-only query, Rabbit connection không declare/publish/consume và health. Chưa có flow thì không dùng 404 làm chứng cứ API nghiệp vụ hoạt động. Thiếu hạ tầng/JDK phải ghi rõ kiểm nào chưa chạy; không hạ rule để vượt gate.

Không nghiệm thu phát hành đủ vé, tải QR, SMTP, race check-in/cancellation, idempotency, ACK/confirm hoặc Saga từ kết quả scaffold. Đây là acceptance của task nghiệp vụ/tích hợp sau.

## 14. Đầu ra, quyền tự quyết và bước tiếp theo

Đầu ra triển khai: production scaffold Ticket, central YAML riêng Ticket, các kiểm tra boundary phù hợp, HANDOFF và TESTING cập nhật; `.env.example` chỉ đổi nếu cần. Giữ công việc có sẵn trong working tree và không sửa source owner khác.

Builder tự quyết tên helper/class nhất quán, annotation/mapping tối thiểu, cách nhóm fixture và test, chi tiết constructor injection và cách thể hiện union probe đúng wire shape. Các lựa chọn phải giữ ownership/contract, không phát sinh endpoint/field public hoặc workflow ngoài phạm vi.

Các lựa chọn của người viết spec đã được chấp thuận cùng tài liệu: shape nội bộ TicketDisplaySnapshot; chưa có TicketRepository độc lập; default deny ngoài route liệt kê; các kiểm tra boundary ở §13.

Spec đặt tại docs/superpowers/specs theo quy ước. Thư mục này đang bị `.gitignore` bỏ qua; giữ tài liệu local theo cách các spec scaffold trước đã làm, không sửa ignore/force-add/commit chỉ để đưa spec mới vào Git.

Người dùng đã yêu cầu lập implementation plan bằng superpowers:writing-plans theo spec được duyệt. Chưa bắt đầu code trong task viết spec/plan.

