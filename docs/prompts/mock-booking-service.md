# Prompt: Mock Booking REST cho Payment theo contract Flash Ticket

Bạn là Senior Java Backend Engineer, làm việc trực tiếp trong repository Flash Ticket Platform. Hãy xây dựng `services/mock-booking-service/` để tạo dữ liệu Booking giả và gửi command đúng contract mà Payment hiện có có thể xử lý. Đây là công cụ local/test, không phải Booking production.

## 1. Khảo sát và giới hạn thay đổi

Trước khi code, đọc `AGENTS.md`, các chỉ dẫn được tham chiếu và trạng thái Git. Giữ nguyên thay đổi đang có của người dùng. Nếu có `.codegraph/`, dùng CodeGraph trước khi tìm/đọc code. Tuân thủ bước duyệt plan theo chỉ dẫn áp dụng; trình contract và danh sách file cụ thể trước triển khai.

Đọc ít nhất:

- `docs/architecture/B13-api-and-event-contracts.md`, đặc biệt §2–5.
- `docs/architecture/B14-main-flow-sequences.md` và `B12-data-ownership-and-schema.md` cho phần liên quan.
- `services/payment-service/README.md`, `docs/booking-simulator.md`, `docs/vnpay-sandbox.md` trong Payment.
- `services/payment-service/pom.xml`, các `application*.yaml`, `dev/compose.yaml`, `dev/booking-simulator.ps1`.
- Payment: `BookingCommandFactory`, `BookingSimulator`, `PaymentSimulatorConfiguration`, `StartPaymentProcessor`, `StartPaymentStore`, `BookingPaymentGuard`.
- Payment: `Envelope`, `StartPayment`, `StartPaymentResult`, `PurchaseSnapshot`, `PurchaseItem`, `ContractCodec`, `PurchaseSnapshotValidator`, `MessageInvariantValidator`, `CanonicalContractSerializer`, `ContractHashService`.
- `PaymentQueryController`, `PaymentQueryService`, `PaymentView`, `PaymentStart` và các fixture trong `src/test/resources/contracts/`, gồm `canonical-v1/`.
- `infrastructure/config-server/src/main/resources/config/payment-service.yaml`.

Những điểm đã thấy tại thời điểm soạn prompt, cần kiểm lại trên working tree hiện tại:

- Payment dùng **Java 21, Spring Boot 4.1.1, Jackson 3** (`tools.jackson.*` cho API databind/core). Không dùng mặc định Java 17/Boot 3/Jackson 2 từ prompt cũ.
- Đã có CLI Booking simulator tạo command hợp lệ; không viết lại thuật toán contract khác với nó.
- `StartPaymentProcessor`, `StartPaymentStore` và listener local chỉ hoạt động với profile `payment-simulator & !prod & !production`.
- Listener hiện gọi processor, kết quả ghi vào inbox; **không publish event trả URL**. Runtime nghiệp vụ ngoài simulator chưa có luồng consumer/outbox/Saga hoàn chỉnh.
- B13-v0.2 là `REVIEW_READY`; canonical fixtures là candidate cho review liên service. Tương thích codec hiện tại không đồng nghĩa đã nghiệm thu toàn bộ thiết kế.

Phạm vi là thêm mock độc lập và tài liệu/test kết nối cần thiết. Không triển khai Payment Saga, IPN, refund, phát vé hoặc consumer nghiệp vụ production để làm mock chạy. Không chỉnh Java/validation/security/DDL Payment chỉ để chấp nhận payload sai. Nếu buộc phải sửa Payment, trình rõ lý do và phạm vi theo `services/payment-service/AGENTS.md` trước khi code.

## 2. Luồng cần thực hiện

1. Client gọi REST mock để tạo một đơn giả với snapshot `QUANTITY` hợp lệ.
2. Mock sinh `orderId`, `requestId` và các ID fixture cần thiết; đóng băng snapshot một lần.
3. Mock gửi JSON `Envelope<StartPayment>` qua RabbitMQ, `messageType=payment.start-requested.v1`.
4. Payment chạy profile simulator nhận command, validate, ghi snapshot + attempt + URL + inbox bằng luồng hiện có.
5. Client poll **API Payment có JWT** để đọc kết quả xử lý và URL đã lưu.

Không tạo URL VNPay giả. Không gửi `PaymentRequested`, `PaymentInitiated`, `PaymentSucceeded`, `PaymentFailed` hoặc event trả URL tự đặt tên. `StartPaymentResult` hiện là kết quả lưu trong inbox, không phải bằng chứng có một response queue.

Phân biệt rõ: broker confirm = broker đã nhận; `startRequest.state=CREATED` = đã tạo attempt; `attemptState=PENDING` = chưa xác nhận thu tiền; thanh toán thành công cần luồng xác nhận provider/lưu bền tương ứng, ngoài phạm vi mock này.

## 3. RabbitMQ: dùng đúng topology đang chạy

| Mục | Local simulator hiện có | Thiết kế/runtime nghiệp vụ cần đối chiếu |
|---|---|---|
| Exchange | `payment.simulator.ex`, direct, durable | B13 ghi `flash-ticket.v1`; Config Server mặc định `${RABBITMQ_BUSINESS_EXCHANGE:business.ex}` |
| Routing key | `payment.start-requested.v1` | B13 dùng `messageType` |
| Queue nhận | `payment.simulator.start.q` | README nhắc `payment.start.q`; phải kiểm definitions thực tế khi nối nghiệp vụ |
| DLX / DLQ | `payment.simulator.dlx` / `payment.simulator.start.dlq` | Không suy từ topology local |

Chọn **topology simulator** cho bản mock này để chạy được với Payment hiện tại, giữ nguyên wire contract B13. Ghi rõ đây chưa phải kiểm tích hợp trên exchange nghiệp vụ. Báo chênh lệch `flash-ticket.v1`/`business.ex`; không tự đổi tên exchange hoặc sửa toàn hệ thống. Không giả định các file triển khai RabbitMQ được README nhắc tới có mặt trên checkout hiện tại.

Payment simulator khai báo topology; mock không khai báo lại queue nghiệp vụ hoặc thêm consumer vào queue Payment. Khởi động Payment trước khi publish. Không thêm `PaymentEventConsumer` khi chưa có event nguồn thực tế.

Publisher phải gửi JSON object trực tiếp, tránh JSON bị bọc thành string bởi double serialization. Đặt `content_type=application/json`, UTF-8, persistent delivery, AMQP message ID trùng `Envelope.messageId`; không phụ thuộc Java class/type header để parse liên service. Dùng `RabbitTemplate` với publisher confirms, returns, mandatory và timeout hữu hạn. Chỉ coi publish được broker nhận khi confirm ACK và không bị returned/unroutable; NACK, mất kết nối hoặc timeout phải được báo đúng, không trả thành công giả.

## 4. Wire contract bắt buộc

Không dùng các alias `bookingId`, `userId`, `paymentMethod`, `eventType`, `paymentId` thay cho field contract. Body gửi Payment chỉ gồm envelope và `data` sau:

```text
Envelope<StartPayment>:
  messageId: UUID
  messageType: "payment.start-requested.v1"
  schemaVersion: 1                         // JSON integer
  aggregateId: UUID                        // orderId
  aggregateVersion: positive integer string
  occurredAt: UTC Instant with Z
  correlationId: UUID                      // orderId
  causationId: UUID | null
  traceparent: string | null
  data:
    requestId: UUID                        // equals messageId
    requestKey: UUID                       // Idempotency-Key
    requestHash: lowercase SHA-256 hex
    provider: "VNPAY"
    purchase: PurchaseSnapshot v1 đầy đủ
```

Các invariant phải giữ:

- `messageId=data.requestId`.
- `aggregateId=correlationId=data.purchase.orderId`.
- UUID chuẩn; thời gian UTC `Z`; `schemaVersion` ở envelope và purchase đều bằng số `1`.
- Money là chuỗi số nguyên VND, không number/double, không dấu/phần lẻ/số mũ. Version là chuỗi số nguyên dương trong miền bigint PostgreSQL. Quantity là JSON integer dương trong int32.
- Mock giới hạn total dương tối đa `"9999999999"`, theo factory/adapter hiện có; không nhầm với miền Money chung rộng hơn.
- Gửi đủ cả field nullable với giá trị `null`; không bỏ field để codec tự đoán. Không field lạ, duplicate JSON key hoặc enum alias.
- Snapshot tối thiểu `QUANTITY`: `mode="QUANTITY"`, sectorId cấp đơn/item là null, seats là `[]`, promotion=null, discount="0", một item quantity=1, unitPrice=lineSubtotal=subtotal=total.
- `items` không rỗng; các ID không trùng sai quy tắc; `frozenAt < expiresAt`, eventStartsAt < eventEndsAt. Dùng `Clock` UTC có thể kiểm thử; lifetime mặc định 900 giây, cho phép 5–3600 giây như factory hiện có.
- Runtime sinh thời gian và ID mới; retry giữ nguyên snapshot, không gia hạn đơn hoặc lấy thời gian mới.

### Hash phải giống Payment

`requestHash` không phải hash toàn snapshot hoặc JSON REST. Nó là SHA-256 của canonical UTF-8 object chỉ gồm:

```json
{"actor":"buyer-1","orderId":"10000000-0000-0000-0000-000000000001","provider":"VNPAY"}
```

Với đúng bytes trên, không BOM/newline, expected hash là:

```text
da056e90a7f9c56e2f743714e9f31aa41fad549341cc5588282b6debba79a05e
```

`actor` lấy đúng `buyerSubject`, không trim hoặc đổi case. Canonical v1 sort key, giữ null, chuẩn hóa UUID lowercase/Instant UTC, money/version dạng chuỗi, sort purchase.items theo orderItemId và seats theo seatId; array khác giữ thứ tự. Không dùng pretty JSON, `toString()` hay global mapper tùy cấu hình để tính hash. Không thêm `snapshotHash`/`envelopeHash` vào wire: đây là giá trị Payment tính nội bộ.

Ưu tiên tái sử dụng contract nếu đã có module dùng chung thực sự. Nếu chưa có, tạo DTO/codec/validator/hash tối thiểu phía mock theo đúng wire và kiểm đối chiếu bằng Payment fixtures/codec. Không kéo toàn bộ executable Spring Boot Payment JAR, JPA, repository hoặc service Payment vào mock; không tạo hệ thống shared module lớn chỉ cho công cụ này. Ghi rõ nơi contract được sao chép và cách phát hiện drift.

### JSON mẫu đầy đủ

Đây là **fixture minh họa** với ID/thời gian cố định và requestHash đã xác định ở trên. Runtime phải sinh ID/time rồi tính lại hash; test sử dụng đồng hồ cố định phù hợp. Không gửi nguyên fixture này để giả lập đơn hiện tại.

```json
{
  "messageId": "10000000-0000-0000-0000-000000000005",
  "messageType": "payment.start-requested.v1",
  "schemaVersion": 1,
  "aggregateId": "10000000-0000-0000-0000-000000000001",
  "aggregateVersion": "1",
  "occurredAt": "2030-01-01T01:00:00Z",
  "correlationId": "10000000-0000-0000-0000-000000000001",
  "causationId": null,
  "traceparent": null,
  "data": {
    "requestId": "10000000-0000-0000-0000-000000000005",
    "requestKey": "10000000-0000-0000-0000-000000000006",
    "requestHash": "da056e90a7f9c56e2f743714e9f31aa41fad549341cc5588282b6debba79a05e",
    "provider": "VNPAY",
    "purchase": {
      "schemaVersion": 1,
      "orderId": "10000000-0000-0000-0000-000000000001",
      "eventId": "10000000-0000-0000-0000-000000000002",
      "buyerSubject": "buyer-1",
      "customerEmail": "buyer@example.test",
      "sourceConfigVersion": "1",
      "sourceOrderVersion": "1",
      "frozenAt": "2030-01-01T01:00:00Z",
      "expiresAt": "2030-01-01T01:15:00Z",
      "currency": "VND",
      "eventTitle": "Mock booking event",
      "eventStartsAt": "2030-01-02T01:00:00Z",
      "eventEndsAt": "2030-01-02T03:00:00Z",
      "venueName": null,
      "mode": "QUANTITY",
      "sectorId": null,
      "subtotal": "150000",
      "discount": "0",
      "total": "150000",
      "promotion": null,
      "items": [
        {
          "orderItemId": "10000000-0000-0000-0000-000000000003",
          "ticketTypeId": "10000000-0000-0000-0000-000000000004",
          "ticketTypeName": "Mock ticket",
          "quantity": 1,
          "unitPrice": "150000",
          "lineSubtotal": "150000",
          "sectorId": null,
          "seats": []
        }
      ]
    }
  }
}
```

## 5. REST mock và retry

Tạo `POST /api/mock/bookings`, có header `Idempotency-Key: <UUID>` và body:

```json
{
  "buyerSubject": "<sub của JWT BUYER dùng để poll Payment>",
  "customerEmail": "buyer@example.test",
  "amount": "150000",
  "currency": "VND",
  "provider": "VNPAY",
  "lifetimeSeconds": 900
}
```

Đây là input của công cụ local, không phải public CreateOrder/StartPayment API của Booking. Cho phép subject/amount do người phát triển nhập chỉ trong mock; không suy thành quy tắc tin giá/identity từ browser ở production. Không đổi route Gateway thật sang mock. Mặc định chỉ bind HTTP loopback, có cổng cấu hình riêng không trùng Payment/Booking.

Validate UUID key, subject không rỗng, email hợp lệ, money string dương đúng giới hạn, VND/VNPAY, lifetime. Sinh một snapshot hợp lệ ở phía mock, không nhận raw envelope tùy ý qua endpoint này.

Trả HTTP 202 với body theo hình dạng `PaymentRequestAccepted`:

```json
{
  "orderId": "<UUID được sinh>",
  "requestId": "<UUID được sinh>",
  "expiresAt": "<UTC Instant>"
}
```

Không trả `PAYMENT_SUCCEEDED`, không bịa `paymentId` hoặc URL tại POST. Chỉ trả 202 sau khi lưu command để replay và publish đã được broker xác nhận, không chỉ xếp tác vụ trong RAM.

Không thêm database riêng. Dùng file command cục bộ giống CLI hiện có, ghi hoàn tất an toàn trước publish; thư mục cấu hình được và bị Git ignore. Lưu đủ receipt/input để cùng buyer + key + cùng input trả cùng IDs/snapshot; khác input trả `409 IDEMPOTENCY_CONFLICT`. Đối chiếu input REST riêng, vì requestHash của Payment không chứa amount/email/lifetime. Bảo vệ request đồng thời cùng key để không sinh hai đơn. Khôi phục mapping retry từ file khi restart hoặc công bố giới hạn nếu môi trường lưu file không bền; không tuyên bố bảo đảm outbox production.

Khi confirm timeout/mất kết nối, kết quả publish có thể chưa biết: trả lỗi có requestId/orderId đã lưu để đối chiếu, giữ nguyên command và yêu cầu retry cùng key. Retry có thể publish lại đúng command; không sinh messageId/requestKey/time/hash mới. Cung cấp cách replay file bằng công cụ hiện có hoặc lệnh tối thiểu tương đương; không nhận đường dẫn file tùy ý qua API công khai. Không sửa file cũ để gia hạn đơn.

202 của mock chỉ chứng minh mức bảo đảm local nêu trên. Booking production theo B13 phải commit order freeze + idempotency + outbox cùng transaction; file mock không thay thế transaction đó.

## 6. Đọc kết quả từ Payment

Hướng dẫn client gọi trực tiếp:

```http
GET http://localhost:8083/api/v1/orders/{orderId}/payment?requestId={requestId}
Authorization: Bearer <BUYER access token có sub trùng purchase.buyerSubject>
```

Giữ nguyên kiểm JWT, issuer/audience/expiry, role BUYER và ownership của Payment. Không thêm endpoint public lấy URL không token hoặc bypass security.

- Chưa có snapshot: `404 PAYMENT_NOT_READY`, poll có backoff hữu hạn.
- `startRequest.state=WAITING`: command chưa có kết quả inbox, không coi là thất bại terminal.
- `CREATED`: xem `latestAttempt.attemptId`, `attemptState`, `paymentUrl`, `expiresAt`. CREATED không bảo đảm URL luôn có trong mọi implementation; không có URL thì không bịa.
- `REJECTED`: đọc `failureCode`, ví dụ ORDER_EXPIRED/PAYMENT_IN_PROGRESS.
- Không token: 401; buyer khác: 404 theo API hiện tại.
- DLQ là lỗi xử lý cần điều tra; không hứa luôn có REJECTED/response event khi input hỏng hoặc hạ tầng lỗi.

Không log raw payload, email, access token, secret hoặc URL thanh toán đầy đủ. Log IDs, messageType và kết quả publish an toàn. Không tự bật callbacks/refund: simulator hiện chưa thực hiện thu tiền/IPN/charge/Saga.

## 7. Cấu trúc, cấu hình và chạy

Tạo service Maven độc lập Java 21/Boot tương thích Payment, có Web MVC, Validation, AMQP, Jackson 3 và test cần thiết. Tách controller, service tạo snapshot/receipt, publisher, contract/validation/hash, cấu hình. Không dùng Node.js, Python, Kafka, Redis hoặc DB riêng để chạy mock. Không thêm consumer không có message nguồn.

Cấu hình RabbitMQ bằng `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST`; exchange/routing phải được kiểm khớp profile đã chọn. IntelliJ/host dùng broker loopback cổng **5679** theo dev Compose hiện tại; container cùng network dùng `rabbitmq:5672`. Payment HTTP mặc định **8083**. Không nhầm port host/container hoặc dùng IP VPS hardcode.

Tạo Dockerfile Java 21 và Compose overlay/profile local bổ sung vào stack phù hợp; không viết lại toàn bộ Compose hoặc chuyển broker/DB dùng chung. `services/payment-service/dev/compose.yaml` hiện chứa PostgreSQL và RabbitMQ, chưa chứa ứng dụng Payment: phải chỉ rõ Payment chạy IntelliJ/host hay container riêng. Không dùng `localhost` bên trong container để gọi broker trên host. Khi chạy host/IntelliJ, bind HTTP `127.0.0.1`; trong container override bind thành `0.0.0.0` để Docker truy cập được, còn Compose chỉ publish `127.0.0.1:<host-port>:<container-port>`.

Payment vẫn dùng PostgreSQL hiện có của stack simulator. “Mock không có DB” không có nghĩa “Payment không cần DB”. Không hardcode credentials thật; dùng env, placeholder và `.env.example` phù hợp. Spring không tự đọc `.env` khi chạy IntelliJ.

## 8. Kiểm thử và tiêu chí hoàn thành

Viết test tập trung vào những chỗ có thể lệch hợp đồng:

1. REST input/receipt đúng wire, validation và cùng key cùng input không tạo đơn khác; input khác với key cũ bị 409. Có test request đồng thời/restart nếu hỗ trợ replay bền.
2. JSON mock tạo được decode bằng **ContractCodec thật của Payment** và qua `MessageInvariantValidator`, `PurchaseSnapshotValidator`. Có thể đặt compatibility test ở test source Payment; không sửa validation production để test pass. Chỉ round-trip bằng DTO riêng của mock là chưa đủ.
3. Golden request hash ở §4 và canonical fixtures hiện có cho Unicode/null/money/version. Expected độc lập, không tính expected bằng chính hàm đang test.
4. Publish đúng exchange/routing/body/content type/IDs; confirm timeout/NACK/unroutable không trả 202 giả; retry giữ nguyên payload.
5. Qua RabbitMQ + PostgreSQL local thật: Payment nhận command, ghi inbox/attempt/URL; GET đúng buyer đọc được URL; replay không tạo attempt mới. Payload sai/hash sai bị từ chối/DLQ theo listener hiện có. Dừng Payment chạy tay trước suite vốn cần là consumer duy nhất.

README phải có lệnh PowerShell chính xác, chạy từ thư mục nào, để build, khởi động broker/DB, chạy Payment profile `payment-simulator`, chạy mock bằng IntelliJ/Compose, POST, poll có JWT, replay và xem DLQ. Không dùng kèm `local-foundation` hoặc prod/production. Kiểm tra lệnh Maven/test profile thực tế trước ghi hướng dẫn; suite mặc định của Payment có exclude integration test.

Phân loại kết quả `PASS`, `FAIL`, `NOT_RUN` cùng nguyên nhân. Không tuyên bố đã kiểm Sandbox merchant/Keycloak thật nếu chỉ dùng test doubles/JWT test/JWKS test. Dựng URL có chữ ký bằng merchant test không chứng minh đã thanh toán tại VNPay. Thiếu credential không được tắt JWT hoặc gọi thanh toán thật để lách test.

Trước triển khai, báo bảng contract/topology đang có và file dự kiến thay đổi để duyệt theo chỉ dẫn repository. Sau khi được duyệt, triển khai code thực tế, chạy build/check phù hợp rồi báo file thay đổi, lệnh chạy, bằng chứng test và giới hạn còn lại. Không mở rộng sang các luồng chưa được yêu cầu. Nếu code hiện tại khác mô tả trong prompt, chỉ rõ chênh lệch và tác động; giữ wire theo thiết kế, không tự tạo contract thay thế.
