# Handoff — Ticket Service scaffold

## Phạm vi

Scaffold theo Booking: 7 entity, 6 repository, DTO/snapshot/message, validation,
MapStruct, JWT, Rabbit converter, Problem và structured logging.
Controller/service là bean rỗng; chưa có handler, consumer, relay, worker,
phát vé, QR, SMTP hoặc check-in hoạt động.

Entity: EventAccessSnapshot, Issuance, Ticket, TicketDelivery, CheckinAttempt,
OutboxMessage, InboxMessage; InboxMessageId là khóa ghép. Ticket là con của Issuance,
chưa có repository riêng. Database `ticket_db`, schema `ticket_schema`, bảng `tbl_*`.

## Chạy từ local

1. Maven/IDE dùng **Java 23**, VM option `-Duser.timezone=UTC`. Server 119 từ chối
   timezone mặc định `Asia/Saigon` ở JDBC startup dù Hibernate đã cấu hình UTC.
   Chạy Config Server và Eureka trước Ticket khi chạy stack bình thường.
2. Khi dùng PostgreSQL server 119, mở hoặc dùng lại tunnel:

```powershell
rtk proxy ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:15432:127.0.0.1:5432 ft119
```

`ft119` là alias SSH cá nhân tới server 119. Dùng lại tunnel nếu port đã mở.

3. Nạp biến vào process/IDE; Spring không tự đọc `.env`:

| Biến | Giá trị / nguồn |
|---|---|
| CONFIG_SERVER_URL | Mặc định `http://localhost:8888` |
| EUREKA_SERVER_URL | Mặc định `http://localhost:8761/eureka/` trong config chung |
| TICKET_SERVICE_PORT | Mặc định `8084` |
| TICKET_DB_URL | Ví dụ `jdbc:postgresql://127.0.0.1:15432/ticket_db?currentSchema=ticket_schema` |
| TICKET_DB_USERNAME / PASSWORD | User mặc định `ticket_admin`; password được cấp, không có default |
| RABBITMQ_HOST / PASSWORD | Với server 119: host `3.106.109.119`; password được cấp |
| RABBITMQ_PORT / USERNAME / VHOST | Mặc định `5672` / `flash_ticket_app` / `/` |
| RABBITMQ_BUSINESS_EXCHANGE | Mặc định `business.ex` |
| KEYCLOAK_ISSUER_URI | Server 119: `http://3.106.109.119:8080/realms/flash-ticket` |
| MAIL_HOST / PORT / USERNAME / PASSWORD | Cấu hình SMTP hiện có; scaffold chưa gửi mail |

4. Từ `services/ticket-service`:

```powershell
rtk proxy .\mvnw.cmd -B -ntp '-Dspring-boot.run.jvmArguments=-Duser.timezone=UTC' spring-boot:run
```

Central config: [ticket-service.yaml](../../infrastructure/config-server/src/main/resources/config/ticket-service.yaml).
Sau khi sửa YAML, build/chạy lại Config Server để phục vụ cấu hình mới.
Hikari áp `SET ROLE ticket_owner` cho account NOINHERIT; pool 5/1,
`ddl-auto=none`, `open-in-view=false`, JDBC timezone UTC, Hibernate JSON mapper Jackson 3.

## Route policy đã chuẩn bị

| Route | Role |
|---|---|
| GET `/api/v1/orders/{id}/tickets`, GET `/api/v1/tickets/{id}/qr` | BUYER |
| POST `/api/v1/orders/{id}/ticket-deliveries` | BUYER |
| POST `/api/v1/events/{id}/check-ins` | ORGANIZER |
| GET `/internal/v1/orders/{id}/issuance` | INTERNAL_SERVICE |
| `/actuator/health` và health subpath | Public |
| Các path/method còn lại | Deny |

Đây là matcher security, chưa có API nghiệp vụ. Ownership sẽ kiểm trong task handler.
ADMIN/ORGANIZER không tự có quyền tải QR buyer. Không dùng 404 làm bằng chứng flow chạy.

## RabbitMQ và điểm mở

Queue theo [definitions.json](../../deploy/rabbitmq/definitions.json):
`ticket.access-configured.q`, `ticket.event-cancelled.q`, `ticket.issuance.q` và DLQ.
Converter dùng cùng JsonMapper với REST; `dynamic=false`, MANUAL ACK, prefetch 20,
correlated confirms, returns/mandatory. Chưa có consumer/relay nên chưa kiểm ACK/confirm thực.

| Mã | Cần chốt trước nghiệp vụ |
|---|---|
| OPEN-TKT-01 | `paymentConfirmationId:String` map cột payment_confirmation_id; IssueTickets/IssuanceResult dùng chargeId:UUID. Chưa có bridge; không tự coi hai ID bằng nhau. |
| OPEN-TKT-02 | B13 dùng flash-ticket.v1; deployed topology dùng business.ex. Scaffold theo môi trường hiện có. |
| OPEN-TKT-03 | B13 result ticket.issuance-result.v1; topology dùng ticket.issued.v1 và ticket.issuance-failed.v1. Payment→Booking dùng booking.issuance-failed.v1 thay booking.issuance-result.v1. Chốt routing key/messageType trước relay; chưa thêm alias. |

PurchaseSnapshot không có sector kind; rule STANDING không có ghế được kiểm trên
SalesConfiguration. Task phát vé phải giữ guard theo contract/snapshot nguồn;
DTO hợp lệ không tự chứng minh caller là Payment hoặc đã được quyền phát vé.

## Kiểm chứng

Lệnh và giới hạn tại [TESTING.md](TESTING.md). Fast suite kiểm boundary thực;
repository test doubles chỉ kiểm bean graph, không chứng minh persistence.

Probe thực tế ngày 26/10/2026 tới `3.106.109.119` đã qua:

- PostgreSQL qua SSH: `ticket_db` / `ticket_schema` / role `ticket_owner`, đọc được 7 bảng.
- Hibernate `ddl-auto=validate` qua sau khi khai báo đúng 23 cột domain `nonempty_text`;
  EntityManagerFactory mở được. Production vẫn giữ `ddl-auto=none`.
- RabbitMQ AMQP đăng nhập bằng `flash_ticket_app`, vhost `/`, mở channel được;
  ACL và 3 queue/binding Ticket đã đối chiếu trên server.
- Keycloak discovery/JWKS đều HTTP 200; 4 assertion Postman đạt.
- Ticket chạy với các resource thật, `/actuator/health` trả 200 / UP.

Probe dùng central YAML trực tiếp, tắt Config Client/Eureka và bỏ SMTP chưa cấu hình
riêng trong process kiểm tra; chưa nghiệm thu Config Server/Eureka hoặc SMTP.
JVM dùng UTC; tunnel riêng đã đóng sau probe. Chưa kiểm ghi/đọc round-trip,
constraint race hoặc luồng nghiệp vụ; không ghi fixture hay thay schema/broker.

`.env` nguồn lúc kiểm còn DB port 5432 và Rabbit host localhost. Khi chạy
Ticket từ local phải nạp các endpoint 119/tunnel trong bảng trên vào process.
