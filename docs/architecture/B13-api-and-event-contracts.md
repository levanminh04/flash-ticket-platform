# B13 — Hợp đồng API và thông điệp cho luồng chính

- Phiên bản: `B13-v0.1`, ngày 2026-09-14.
- Trạng thái: `APPROVED`; người duyệt: Lê Văn Minh; ngày duyệt: 2026-09-14 (GOV-144), sau B12-v0.2. Chưa nghiệm thu API chạy thật.
- Phân lớp: `FORMATION`; riêng §8 là `COMPARISON` về adapter, không sinh ranh giới service.
- Đầu vào: B11-C-v0.3; B6-v0.15/B7-v0.13/B8-v0.13; B12-v0.2 — APPROVED. Review-v0.6 và các quyết định GOV-122–144, PRJ-024 theo phần còn hiệu lực.
- Phạm vi được giao: bản đồ `B12-B16-completion-impact-map.md`; năm service, một Saga do Payment điều phối. Hủy sự kiện là phản ứng một chiều có retry.

## 1. Cách đọc và mức quyết định

VND nguyên đồng, giảm giá HALF_UP một lần trên tổng đơn, phí HALF_UP một lần trên doanh thu hợp lệ event, freeze từ lần bắt đầu thanh toán đầu tiên, mốc hết hạn tại Booking, email sửa trước tạo đơn và S-01–S-05 là `USER_CONFIRMED`. Tên endpoint/message, cấu trúc dưới đây và cách mã hóa tiền là **baseline kỹ thuật DECIDED theo GOV-144**, dùng để nhóm triển khai thống nhất; không coi đây là kiểm hợp đồng trên service thật.

Các kiểu trong khối `typescript` là **đặc tả wire format**, không phải mã ứng dụng. Mọi trường đều bắt buộc trừ dấu `?`; `null` chỉ dùng nơi khai rõ. Từ chối trường lạ trên command và snapshot v1; không nhận alias. Không dùng `Record<string, unknown>` hoặc chỉ kiểm JSON là object để coi là hợp đồng hoàn tất.

## 2. Quy ước chung

| Thành phần | Hợp đồng |
|---|---|
| URL | API bên ngoài `/api/v1`; nội bộ `/internal/v1`, không mở route nội bộ qua gateway công khai |
| ID nghiệp vụ | UUID dạng chuỗi; `subject` là chuỗi bất biến từ nguồn danh tính, không lấy email làm ID |
| Thời gian | ISO-8601 UTC có `Z`; DB `timestamptz`; deadline phân xử bằng đồng hồ Booking, không đồng hồ trình duyệt |
| Tiền | Chuỗi khớp `^(0|[1-9][0-9]{0,18})$`, VND; không dấu phẩy, số mũ, dấu cộng hoặc phần lẻ. Giá/tổng thu > 0; giảm/hoàn tổng hợp có thể 0 |
| Số lượng | JSON integer > 0, không làm tròn; giới hạn int32 cho số lượng một request; counter tổng dùng bigint ở DB |
| Version | `schemaVersion=1` là hình dạng; `sourceConfigVersion` là phiên bản cấu hình Event. Version bigint truyền chuỗi số nguyên dương, không ép JS Number |
| Quyền | JWT hợp lệ: issuer/audience/expiry/chữ ký + role + ownership. Internal command xác thực service caller; không tin `buyerSubject` do browser gửi |
| Retry HTTP | `Idempotency-Key` UUID bắt buộc với tạo đơn, bắt đầu thanh toán, gửi lại vé và check-in. Cùng actor + operation + key + input trả lại resource/kết quả cũ; khác input: `409 IDEMPOTENCY_CONFLICT` |
| Correlation | Giao dịch mua dùng `correlationId=orderId`; lưu và truyền qua attempt, callback, Saga, vé, hoàn. Trước có order dùng request correlation tạm, log nối nó với orderId. Không dùng correlation làm credential |
| Lỗi | HTTP 400 sai hình dạng; 401 chưa xác thực; 403 thiếu quyền; 404 không có/không được đọc tài nguyên; 409 xung đột nghiệp vụ; 422 nội dung hợp lệ cú pháp nhưng sai quy tắc; 503 chưa phục vụ được. Không đổi lỗi thiếu dữ liệu thành amount=0 hoặc AVAILABLE |

```typescript
type UUID = string; // RFC UUID; không phải chuỗi bất kỳ
type Instant = string; // UTC ISO-8601
type UInt64 = string; // số nguyên dương trong bigint PostgreSQL
type Money = string; // quy tắc tiền của bảng trên
type Subject = string; // không rỗng
type OrderState = 'PENDING_PAYMENT'|'ISSUING'|'COMPLETED'|'CANCELLED'|'EXPIRED'|'ISSUANCE_FAILED';
type EventState = 'DRAFT'|'PENDING_APPROVAL'|'APPROVED'|'PUBLISHED'|'CANCELLED';
type Problem = { code: string; message: string; correlationId: UUID;
  violations: {field: string; code: string}[] };
```

Danh sách `code` không dùng làm enum mở cho trạng thái DB. Mỗi nhóm dưới đây liệt kê các lỗi nghiệp vụ phải phân biệt. Text `message` để hiển thị; FE rẽ nhánh theo `code`, không theo câu tiếng Việt.

## 3. API giao dịch người mua

| Method / path | Owner / actor | Input → output | Guard và tác dụng |
|---|---|---|---|
| GET `/events/{eventId}` | Event / public | `EventPublic` | Chỉ sự kiện công bố; không trả audit, cấu hình nháp hoặc thông tin danh tính riêng |
| GET `/events/{eventId}/availability` | Booking / buyer hoặc public | `Availability` | Chỉ trạng thái đọc; không giữ ghế; thiếu snapshot trả `503 CONFIG_NOT_READY` |
| POST `/orders` | Booking / BUYER | `CreateOrder` → 201 `OrderView` | Một transaction giữ tất cả nguồn cung/hạn mức và tạo đơn; không nhận giá từ FE |
| POST `/orders/{orderId}/promotion` | Booking / chủ đơn | `{code:string}` → `OrderView` | Chỉ khi còn giữ chỗ, chưa freeze, chưa có mã; giữ lượt cùng hạn đơn |
| POST `/orders/{orderId}/cancel` | Booking / chủ đơn | `{}` → `OrderView` | Chỉ PENDING_PAYMENT còn giữ chỗ; sau chấp nhận tiền không cho buyer hủy. Retry CANCELLED trả trạng thái hiện có |
| GET `/orders/{orderId}` | Booking / chủ đơn | `OrderView` | `ISSUING` không đổi thành COMPLETED vì trang Return báo thành công |
| POST `/orders/{orderId}/payment-attempts` | Payment / chủ đơn | `{provider:'VNPAY'}` → 201/200 `PaymentStart` | Xác minh buyer qua Booking; freeze trước mở attempt; cùng key ánh xạ cùng attemptId |
| GET `/orders/{orderId}/payment` | Payment / chủ đơn | `PaymentView` | Return page chỉ đọc; browser không quyết định đã thu tiền |
| GET `/orders/{orderId}/tickets` | Ticket / chủ đơn | `{issuanceState:'PENDING'|'COMPLETED'|'FAILED', tickets:TicketView[]}` | Chỉ trả bộ vé sau issuance COMPLETED; pending trả mảng rỗng; email lỗi không chặn |
| GET `/tickets/{ticketId}/qr` | Ticket / chủ đơn | ảnh QR hoặc `{qrToken:string}` theo Accept | `Cache-Control: no-store`; organizer/admin không được tải QR buyer |
| POST `/orders/{orderId}/ticket-deliveries` | Ticket / chủ đơn | `{}` → 202 `{deliveryId:UUID,state:'PENDING'}` | Gửi lại vào email đã lưu theo đơn; không đổi quyền sở hữu; cùng key không tạo hai job |

```typescript
type CreateOrder = {
  eventId: UUID;
  customerEmail: string; // email hợp lệ, <=254 ký tự; prefill nhưng cho sửa trước POST
  selection:
    | {mode:'QUANTITY'; items:{ticketTypeId:UUID;quantity:number}[]}
    | {mode:'SEAT_MAP'; sectorId:UUID; kind:'SEATED'; seatIds:UUID[]}
    | {mode:'SEAT_MAP'; sectorId:UUID; kind:'STANDING'; items:{ticketTypeId:UUID;quantity:number}[]};
};
type OrderItem = {
  orderItemId:UUID; ticketTypeId:UUID; ticketTypeName:string;
  quantity:number; unitPrice:Money; lineSubtotal:Money; sectorId:UUID|null;
  seats:{seatId:UUID;seatLabel:string;rowName:string;seatNumber:string}[];
};
type OrderView = {
  orderId:UUID; eventId:UUID; state:OrderState; customerEmail:string;
  currency:'VND'; subtotal:Money; discount:Money; total:Money;
  expiresAt:Instant; paymentFrozenAt:Instant|null; sourceConfigVersion:UInt64;
  items:OrderItem[];
};
type PaymentStart = {
  orderId:UUID; attemptId:UUID; attemptState:'PENDING'|'UNKNOWN'|'SUCCEEDED'|'FAILED';
  amount:Money; currency:'VND'; paymentUrl:string|null; expiresAt:Instant;
};
type PaymentView = {
  orderId:UUID; acceptedChargeId:UUID|null;
  latestAttempt:{attemptId:UUID;state:'PENDING'|'UNKNOWN'|'SUCCEEDED'|'FAILED'}|null;
  refunds:{refundId:UUID;chargeId:UUID;amount:Money;
    state:'PENDING'|'PROCESSING'|'SUCCEEDED'|'FAILED';
    reason:'LATE_PAYMENT'|'DUPLICATE_PAYMENT'|'TICKET_ISSUANCE_FAILED'|'EVENT_CANCELLED'}[];
};
type TicketView = {ticketId:UUID;eventId:UUID;orderItemId:UUID;ordinal:number;
  ticketTypeName:string;seatLabel:string|null;state:'VALID'|'USED'|'VOID'};
type Availability = {
  eventId:UUID;sourceConfigVersion:UInt64;asOf:Instant;
  ticketTypes:{ticketTypeId:UUID;available:UInt64}[];
  sectors:{sectorId:UUID;kind:'SEATED'|'STANDING';available:UInt64}[];
  seats:{seatId:UUID;state:'AVAILABLE'|'HELD'|'PURCHASED'|'UNAVAILABLE'}[];
};
```

`Availability` counter được phép `"0"` (ngoại lệ rõ của kiểu số nguyên không âm; version vẫn >0). Quy tắc mảng: không rỗng khi tạo đơn, ID không trùng, một event, SEAT_MAP đúng một sector; loại ghế/giá do Booking xác định từ snapshot. QUANTITY trừ pool loại vé; STANDING trừ một pool sector dù có nhiều loại vé; SEATED chiếm các seat, không trừ thêm counter sector như một pool độc lập. Trả `409 SEAT_UNAVAILABLE`, `INSUFFICIENT_QUANTITY`, `PURCHASE_LIMIT_EXCEEDED`, `ORDER_EXPIRED`, `ORDER_NOT_PAYABLE`, `PAYMENT_IN_PROGRESS`, `PRICE_FROZEN` đúng nguyên nhân.

Promotion: `PROMOTION_NOT_FOUND`, `PROMOTION_OUTSIDE_WINDOW`, `PROMOTION_EXHAUSTED`, `PROMOTION_ALREADY_USED`, `ORDER_ALREADY_HAS_PROMOTION`, `NON_POSITIVE_TOTAL`. Chuẩn hóa code bằng trim và uppercase không phụ thuộc locale trước lookup; unique theo event + code chuẩn hóa. Không thêm thao tác tháo/đổi promotion vào đơn đang giữ nếu chưa có yêu cầu; muốn lựa chọn khác thì dùng luồng tạo đơn mới theo điều kiện hủy hiện hành.

`PaymentStart.paymentUrl` chỉ có khi cổng đã cấp URL cho attempt còn PENDING; không tái tạo URL với mã giao dịch khác khi mất response. UNKNOWN trả null và FE theo dõi trạng thái. Provider URL phải nằm trong cấu hình cổng cho phép; không dùng redirect URL tự do từ browser. Đây không phải URL callback/secret được hardcode vào tài liệu.

## 4. Purchase snapshot cố định và freeze tiền

```typescript
type PurchaseSnapshot = {
  schemaVersion:1;orderId:UUID;eventId:UUID;buyerSubject:Subject;
  customerEmail:string;sourceConfigVersion:UInt64;sourceOrderVersion:UInt64;
  frozenAt:Instant;expiresAt:Instant;currency:'VND';
  eventTitle:string;eventStartsAt:Instant;eventEndsAt:Instant;venueName:string|null;
  mode:'QUANTITY'|'SEAT_MAP';sectorId:UUID|null;
  subtotal:Money;discount:Money;total:Money;
  promotion:{promotionId:UUID;code:string;discount:Money}|null;
  items:OrderItem[];
};
```

Booking lưu full SalesConfiguration ở event_sales_snapshots.configuration_snapshot; freeze ghi nguyên PurchaseSnapshot vào orders.frozen_purchase_snapshot cùng mốc/version. Không tái dựng snapshot frozen từ Event hiện tại khi retry. Booking owns snapshot: `sourceOrderVersion` lấy `payment_snapshot_version` ghi đúng một lần khi freeze. Dữ liệu hiển thị nhận từ Event trong cấu hình bán; buyerSubject lấy JWT. Payment lưu `order_payment_snapshots`, Ticket lưu `issuances.purchase_snapshot`; không JOIN Booking/Event. `snapshot_hash`/`input_hash` là SHA-256 của DTO v1 được serialize canonically: tên field sắp thứ tự, timestamp chuẩn UTC, tiền/version chuỗi chuẩn, items theo orderItemId, seats theo seatId, không field thừa. Đây chỉ là nhận diện retry khác input; **không** có hash-chain/chữ ký nội bộ chống ai sửa DB.

Validator ngoài object/type phải kiểm: sum(quantity × unitPrice)=subtotal; sum(lineSubtotal)=subtotal; total=subtotal-discount>0; promotion null ⇒ discount=0; discount trong promotion bằng discount của đơn; UUID duy nhất; tổng expectedTicketCount bằng tổng quantity; SEATED đúng số seat theo quantity và không trùng; QUANTITY/STANDING không có seat; sector/type khớp selection; `frozenAt < expiresAt`. DB kiểm phần cục bộ; ứng dụng kiểm tổng nhiều dòng trong cùng transaction. Từ chối số nhập lẻ **trước** cast `NUMERIC(19,0)`.

Freeze không gia hạn giữ chỗ. Payment gọi `POST /internal/v1/orders/{id}/freeze-payment` với `{buyerSubject:Subject}` bằng credential nội bộ; Booking khóa đơn, kiểm chủ đơn, trạng thái và deadline; lần đầu lưu payment_frozen_at/payment_snapshot_version, lần sau trả cùng PurchaseSnapshot, không tính lại giá. GET không có tác dụng freeze. Nếu response mất, retry trả snapshot đã lưu nhưng **không cho mở attempt mới** nếu đơn nay đã hết hạn/cancelled; Payment kiểm lỗi Booking mỗi lần start mới. Callback của attempt đã tạo vẫn phải xử lý độc lập, không bị bỏ vì start mới bị từ chối.

Đánh đổi snapshot: thêm dữ liệu sao chép và version nhưng Payment/Ticket có đầu vào bất biến, không lệ thuộc cấu hình Event hiện tại hoặc gọi Booking trong mọi callback/phát vé. Không sao chép toàn bộ user hoặc toàn bộ seat map vào Payment/Ticket.

## 5. Thông điệp và giao tiếp nội bộ

### 5.1 Envelope, quyền và lưu bền

```typescript
type Envelope<T> = {
  messageId:UUID; messageType:string; schemaVersion:1;
  aggregateId:UUID; aggregateVersion:UInt64; occurredAt:Instant;
  correlationId:UUID; causationId:UUID|null; traceparent:string|null; data:T;
};
```

Một messageId cho một bản ghi outbox; publish lại giữ nguyên ID/data. Type dùng chính tên ở bảng §5.2; schemaVersion khác 1 bị từ chối, không tự parse đoán. Routing qua exchange nghiệp vụ `flash-ticket.v1`, routing key là messageType; queue riêng theo owner/consumer. Commands chỉ consumer đích được phép bind/ghi; broker nội bộ không nhận trực tiếp browser. TLS/credential theo baseline triển khai, không nhét credential vào payload.

`outbox_messages` ở từng SQL owner: ghi domain change và outbox trong một transaction. `inbox_messages` khóa `(consumer_name,message_id)`: so hash trước dedupe; commit inbox + hiệu ứng + response outbox cùng transaction. Không ACK trước commit. Consumer đọc trùng đã thành công thì ACK; bản trả lời đã nằm trong outbox nên không cần tạo message mới mỗi lần redelivery. Nếu một operation mới hỏi lại cùng quyết định, lấy dữ liệu terminal cục bộ và phát lại kết quả với causation mới, không thực hiện hiệu ứng lần hai.

Publisher confirm chỉ xác nhận broker nhận, không phải consumer đã xử lý. Crash sau confirm trước published_at dẫn tới publish lặp bình thường. Message sai cấu trúc/khác hash cùng ID được dừng xử lý và báo lỗi kỹ thuật; không bỏ kiểm money để làm queue chạy tiếp. Message phụ thuộc cấu hình chưa tới được retry, không tự tạo cấu hình rỗng. Snapshot Event là full-state có version nên có thể nhận v3 trước v2 và bỏ v2; command chuyển tiền/vé không dựa vào thứ tự broker để bảo đảm đúng.

### 5.2 Danh mục hợp đồng liên service

| messageType | Nguồn → đích | `data` cố định | Hiệu ứng và khóa nghiệp vụ |
|---|---|---|---|
| `event.sales-configured.v1` | Event → Booking | `SalesConfiguration` (§7) | Upsert cấu hình khi version mới; không reset held/purchased khi nhận lại |
| `event.access-configured.v1` | Event → Ticket | `AccessConfiguration` | Full snapshot mới hơn, lưu owner/time/status; CANCELLED không hồi sinh bởi bản cũ |
| `event.finance-configured.v1` | Event → Payment | `FinanceConfiguration` | Phí đã duyệt và thời gian end; không sửa phí đã freeze |
| `event.cancelled.v1` | Event → Booking, Ticket, Payment | `EventCancellation` | Một nguyên nhân cancellationId; đóng gate từng owner; chạy tiếp tới hội tụ (§B14.5), không Saga mới |
| `booking.accept-payment.v1` | Payment → Booking | `AcceptPayment` | order lock; chọn accepted_charge_id đúng một lần; phân xử thời gian sau khi lấy khóa |
| `booking.payment-decision.v1` | Booking → Payment | `PaymentDecision` | Chỉ ACCEPTED mới có quyền điều phối phát vé; LATE/DUPLICATE hội tụ refund |
| `ticket.issue.v1` | Payment → Ticket | `IssueTickets` | order unique + input hash + terminal fence; chỉ từ Payment sau acceptance |
| `ticket.issuance-result.v1` | Ticket → Payment | `IssuanceResult` | COMPLETED không dựa email; FAILED chỉ khi terminal fence đã commit |
| `booking.issuance-result.v1` | Payment → Booking | `BookingIssuanceResult` | Cập nhật ISSUING→COMPLETED hoặc ISSUANCE_FAILED + release đủ điều kiện đúng một lần |

```typescript
type AccessConfiguration = {eventId:UUID;organizerSubject:Subject;
  eventStatus:EventState;eventStartsAt:Instant;eventEndsAt:Instant;sourceConfigVersion:UInt64};
type FinanceConfiguration = {eventId:UUID;organizerSubject:Subject;eventEndsAt:Instant;
  feeRate:string;sourceFeeVersion:UInt64;currency:'VND'}; // decimal fraction [0,1], <=8 decimals
type EventCancellation = {eventId:UUID;cancellationId:UUID;
  sourceConfigVersion:UInt64;cancelledAt:Instant;actorSubject:Subject;
  sales:SalesConfiguration;access:AccessConfiguration};
type AcceptPayment = {orderId:UUID;chargeId:UUID;attemptId:UUID;
  amount:Money;currency:'VND';sourceOrderVersion:UInt64};
type PaymentDecision = {orderId:UUID;chargeId:UUID;decisionAt:Instant;
  outcome:'ACCEPTED'|'LATE'|'DUPLICATE'|'CANCELLED';acceptedChargeId:UUID|null};
type IssueTickets = {orderId:UUID;chargeId:UUID;purchase:PurchaseSnapshot};
type IssuanceResult = {orderId:UUID;chargeId:UUID;issuanceId:UUID;
  state:'COMPLETED'|'FAILED';ticketCount:number;failureCode:string|null};
type BookingIssuanceResult = {orderId:UUID;chargeId:UUID;issuanceId:UUID;
  state:'COMPLETED'|'FAILED';failureCode:string|null};
```

Cancellation mang full sales/access cùng version và trạng thái CANCELLED để Booking/Ticket áp được ngay cả khi bản cấu hình trước chưa đến. Handler khóa/upsert snapshot và đặt cổng hủy trước fan-out; không reset counters hiện có. Payment lưu cancellation_id/cancelled_at tại event_finance_snapshots kể cả finance config chưa có; khi finance tới sau chỉ bổ sung phí, không xóa marker. DRAFT chưa có loại vé có thể phát snapshot CANCELLED với các mảng rỗng; không dùng nó mở bán. Đây là tradeoff payload lớn hơn nhưng ít nhánh thiếu cấu hình.

PaymentDecision ACCEPTED ⇒ acceptedChargeId=chargeId; DUPLICATE ⇒ acceptedChargeId khác chargeId. Replay cùng charge đã được chấp nhận giữ ACCEPTED dù đồng hồ hiện tại đã quá hạn; kiểm duplicate **trước** kiểm deadline. CANCELLED trả lý do hủy, không đổi order đã terminal; Payment chọn EVENT_CANCELLED nếu có marker hủy event, còn đơn buyer hủy dùng nhánh LATE_PAYMENT. DTO `failureCode` FAILED phải không rỗng; COMPLETED phải null và ticketCount bằng tổng snapshot; FAILED ticketCount=0 vé có hiệu lực. Không mã `TIMEOUT` nào tự biến thành terminal FAILED.

Nội bộ chỉ đọc bổ sung: Ticket `GET /internal/v1/orders/{id}/issuance` trả 404 chưa có hoặc `IssuanceResult`/`{state:'PENDING',orderId,issuanceId}`; Payment có thể probe khi chậm nhưng 404/PENDING/timeout **không** cho quyền refund vì lỗi phát hành. Event có thể đọc Payment `GET /internal/v1/events/{id}/financial-summary` để phân loại hủy trực tiếp/yêu cầu hủy theo FR-11, không được đọc DB; snapshot này không hứa chặn mọi charge xuất hiện sau lúc đọc. Hủy tiếp theo vẫn áp cơ chế hội tụ cho giao dịch lọt trong khoảng trễ đã được chấp nhận.

### 5.3 Cổng thanh toán

Đường IPN dành riêng Payment, không nhận JWT buyer thay chữ ký cổng. Adapter xác minh chữ ký trên thông điệp chuẩn của provider, merchant account, mã attempt, order, currency, amount, provider result và transaction identity. Chỉ sau xác minh mới insert `charges`; `UNIQUE(provider,merchant_account,provider_charge_id)` phân biệt redelivery một charge với một khoản thu thêm. Không dùng Return page hoặc số tiền FE làm bằng chứng.

Adapter có bốn thao tác: `createPayment(attemptId,amountVnd,expiresAt)`, `verifyNotification(rawRequest)`, `queryPayment(attemptId)`, `refund(refundId,chargeId,amountVnd,requestKey)`/tra cứu kết quả hoàn. Network timeout là UNKNOWN/PROCESSING; trước retry tác động tiền phải query cùng định danh. VNPay ×100 chỉ trong adapter, kiểm chia hết 100 khi giải mã amount và giới hạn provider; không ép giá trị lệch thành amount của đơn. Hợp đồng HTTP/ack cụ thể của merchant phải theo tài liệu provider khi tích hợp; B13 không bịa mã ack sandbox đã được test.

PRJ-024: **không có bảng/luồng Q-07** cho charge sai tiền hoặc không liên kết được. Từ chối chấp nhận và không phát vé; log mã lỗi đã lọc, không ghi raw IPN/secret. Không tuyên bố đã đối soát/phục hồi đầy đủ tiền thực bị thu trong case bị loại này. Các khoản thu muộn/thu thừa đã liên kết đúng vẫn giữ và hoàn, không bị bỏ theo Q-07.

## 6. Vé, email và check-in

QR v1 là bearer token ngẫu nhiên 256 bit bằng CSPRNG, base64url không padding, không nhúng PII/giá/vai trò. Ticket lưu hash SHA-256 để lookup; bản token mã hóa AEAD (AES-256-GCM với nonce ngẫu nhiên 96 bit, authentication tag, AAD=ticketId), kèm key version để chủ đơn tải lại. Key ngoài DB/repo; backup DB mà mất key thì không giải mã tải lại QR. Không đổi format này thành chữ ký HMAC chứa dữ liệu chỉ vì repo cũ có helper QR. Đây là lựa chọn kỹ thuật trong phạm vi token/khóa, không thêm xác thực ảnh/đặt hộ/chuyển nhượng.

Email chỉ gửi thông báo đã phát hành và link đăng nhập xem/tải vé; không gửi raw QR công khai. Email lấy snapshot đơn, không reread profile mới. `ticket_deliveries` là job retry cùng deliveryId; SMTP timeout có thể đã gửi nên không cam kết exactly-once email. Delivery lặp có thể phát sinh email trùng nhưng không tạo vé mới hoặc refund; đây là đánh đổi chấp nhận được của chức năng thông báo đơn giản.

`POST /api/v1/events/{eventId}/check-ins`, ORGANIZER sở hữu event:

```typescript
type CheckIn = {qrToken:string}; // Idempotency-Key UUID là request_key; id do server sinh cho mỗi HTTP receipt
type CheckInResult = {attemptId:UUID;eventId:UUID;ticketId:UUID|null;
  result:'SUCCESS'|'NOT_EVENT_OWNER'|'OUTSIDE_WINDOW'|'UNKNOWN_QR'|
    'WRONG_EVENT'|'NOT_ISSUED'|'VOID'|'ALREADY_USED'|'EVENT_CANCELLED';
  succeeded:boolean;processedAt:Instant};
```

request_hash bao requestedEventId + hash(token) + actor, không raw token. Cùng ID khác dữ liệu 409. Mọi yêu cầu đến ứng dụng với actor xác định lưu kết quả kể cả bị từ chối; request chưa xác thực có security log không lấy subject giả để insert. Retry cùng request trả kết quả gốc nhưng vẫn ghi một audit mới: id mới, cùng request_key, replay_of trỏ bản gốc, succeeded=false, result_code=REPLAYED. Unique(actor_subject,request_key) chỉ trên bản gốc (replay_of NULL); unique success/ticket vẫn giữ. Scan mới cùng vé là ALREADY_USED. Khóa event snapshot trước ticket; kiểm `eventStart <= clock_timestamp() <= eventEnd`, event owner, issuance COMPLETED và ticket VALID rồi atomically VALID→USED + audit trong cùng transaction. Thiết bị không nhận được response báo “chưa xác nhận”, retry cùng key, không báo thành công offline. QR/buyer email không ghi log.

## 7. Hợp đồng cấu hình Event và seat map

### 7.1 Cấu hình bán liên service

```typescript
type PromotionConfig = {promotionId:UUID;code:string;startsAt:Instant;endsAt:Instant;
  maximumUses:UInt64;maximumUsesPerUser:1;
  discount:{kind:'FIXED';amount:Money}|{kind:'PERCENT';percent:number}};
type TicketTypeConfig = {ticketTypeId:UUID;name:string;unitPrice:Money;currency:'VND';
  sectorId:UUID|null;configuredQuantity:UInt64|null;colorCode:string|null};
type SalesConfiguration = {
  schemaVersion:1;eventId:UUID;organizerSubject:Subject;sourceConfigVersion:UInt64;eventStatus:EventState;
  eventTitle:string;eventStartsAt:Instant;eventEndsAt:Instant;venueName:string|null;
  mode:'QUANTITY'|'SEAT_MAP';saleStartsAt:Instant;saleEndsAt:Instant;purchaseLimit:number;
  ticketTypes:TicketTypeConfig[];
  sectors:{sectorId:UUID;kind:'SEATED'|'STANDING';configuredCapacity:UInt64}[];
  seats:{seatId:UUID;sectorId:UUID;ticketTypeId:UUID;seatLabel:string;
    rowName:string;seatNumber:string;isHidden:boolean}[];
  promotions:PromotionConfig[];
};
```

Các counter cấu hình cho phép 0; percent là integer 1..100; fixed >0. QUANTITY sectors/seats rỗng; STANDING không có seat; mọi type SEAT_MAP thuộc sector cùng event. Ghế DRAFT chưa gán type được phép ở editor nhưng **không** gửi cấu hình bán cho ghế bán được thiếu type. Hidden seat có thể chưa gán type ở editor; khi xuất `SalesConfiguration`, chỉ xuất seat có type, hidden không type không vào nguồn cung. Booking giữ mapping type/sector/giá cục bộ; ghế hidden có type thành UNAVAILABLE. Full snapshot mới không được xóa ghế đang HELD/PURCHASED hoặc reset counter; thương mại khóa từ gửi duyệt nên không tạo chức năng sửa giá sau mở bán ở đây.

### 7.2 Editor: form cố định, giữ renderer

GET/PUT `/api/v1/organizer/events/{eventId}/seat-map`, owner Event. PUT chỉ DRAFT + đúng organizer. Input là `SeatMapDraft`; output cùng form đã chuẩn hóa. Lưu full draft một transaction; stable IDs. Ghế không có trong danh sách mới là **xóa nháp**, còn isHidden=true là giữ ghế nhưng không bán. Không xóa nếu tham chiếu còn dùng; có lỗi rollback toàn bộ. Không có ETag cộng tác/merge hai organizer, không lịch sử template/clone.

```typescript
type Bounds = {x:number;y:number;width:number;height:number};
type SeatLayout = {mode:'grid'|'arc'|'fan';rows:number;seatsPerRow:number;
  gapX:number;gapY:number;paddingX:number;paddingY:number;offsetX:number;offsetY:number;
  seatRadius:number;rowStartCharCode:number;seatStartNumber:number};
type Shape =
  | {shapeType:'rectangle'|'rect'|'roundedRect'|'stage'|'foh'|'circle'|'ellipse';bounds:Bounds}
  | {shapeType:'polygon';bounds:Bounds;points:number[]}
  | {shapeType:'path';bounds:Bounds;pathData:string}
  | {shapeType:'ringSection'|'fan';bounds:Bounds;cx:number;cy:number;
     outerRadius:number;innerRadius:number;startAngle:number;endAngle:number};
type Geometry = {schemaVersion:1;shape:Shape;seatLayout:SeatLayout;
  editorVisible:boolean;editorLocked:boolean};
type SeatMapDraft = {
  schemaVersion:1;layoutId:UUID;name:string;canvasWidth:number;canvasHeight:number;
  backgroundImageReference:string|null;
  layoutData:{schemaVersion:1;decorations:{id:UUID;label:string;colorCode:string;shape:Shape}[]};
  sectors:{sectorId:UUID;name:string;code:string|null;kind:'SEATED'|'STANDING';
    configuredCapacity:UInt64;displayOrder:number;colorCode:string|null;geometry:Geometry;
    seats:{seatId:UUID;seatLabel:string;rowName:string;seatNumber:string;
      coordX:number;coordY:number;ticketTypeId:UUID|null;isHidden:boolean}[]}[];
};
type EventPublic = {eventId:UUID;title:string;description:string;startsAt:Instant;endsAt:Instant;
  venueName:string|null;mode:'QUANTITY'|'SEAT_MAP';ticketTypes:TicketTypeConfig[];
  seatMap:SeatMapDraft|null};
```

Public seatMap là view lọc từ form: không trả hidden seats; editorLocked/editorVisible chỉ dữ liệu trình bày, không nghĩa mở/đóng bán; không để editorVisible=false ngầm xóa nguồn cung. TicketTypeConfig trả giá chuẩn, không lặp giá trong geometry. Background reference được resolver của frontend/storage chuyển thành URL hiển thị; không cho URL tùy ý thành đường đọc file/SSRF.

Validator geometry: mọi number hữu hạn; canvas width/height integer >0; bounds width/height >0; coordX/Y có tối đa hai số lẻ trong phạm vi NUMERIC(10,2); radii dương, 0<=innerRadius<outerRadius; fan innerRadius=0; endAngle khác startAngle và sweep không quá 360 độ; polygon points chẵn và ít nhất 6 số; pathData là SVG path được parse/allow-list lệnh vẽ, không markup/script; rows/seatsPerRow/rowStartCharCode/seatStartNumber integer phù hợp; seatRadius>0, gap/padding không âm. Hình học có số lẻ là đúng, không áp quy tắc nguyên đồng cho canvas.

Các trường thuộc shape khác bị từ chối thay vì giữ blob tự do. Trường presentation không mang sectorType/capacity/typeIds/price. `displayOrder` là thứ tự vẽ, adapter chuyển zIndex; không lưu hai thứ tự khác nhau. Đổi mode cần explicit xác nhận xóa cấu hình không tương thích theo FR-04; PUT seat-map không tự chuyển mode.

## 8. COMPARISON — hợp đồng chuyển FE cũ sang đích

Đã đọc kiểu editor và điểm consumer `frontend/src/components/seat-map/editor/seatMapEditorTypes.ts`, `SeatMapEditorCanvas.tsx` và `runtime/BuyerSeatMapCanvas.tsx` trong repo cũ ngày 2026-09-14. Căn cứ đầy đủ tọa độ/ẩn tại review pack §3.3. Đây là kiểm source, chưa phải browser round-trip.

| Dùng lại | Sửa tại adapter | Bỏ dữ liệu/nhánh dư |
|---|---|---|
| Renderer, kéo/resize, công cụ tạo grid/arc/fan, concert preset | shape/bounds/points và radial fields vào `geometry.shape`; seatLayout đi riêng có kiểu | Alias bounds.x/mapData.x/positionX ở backend; không viết lại renderer |
| Ghế và stable ID, coordX/Y, row/seat/label | `coordX/Y ↔ seat.x/y`; `isHidden ↔ hidden`; giữ ticketType khi hide/unhide | relativeX/Y lưu trùng; không bỏ thuật toán tính relative tạm trong resize |
| Màu loại vé, sector | Derive seat color từ ticket type; displayOrder→zIndex | typeIds/typeNames/capacity lặp trong mapData |
| Local draft phục vụ editor | Import/export dùng chính v1 validator; tải từ API phải khôi phục hidden chính xác | Không để local draft che lỗi server round-trip; không thêm clone source |
| Checkout redirect/polling | Chuỗi Money helper; ISSUING là chờ vé; email sửa ở bước trước tạo đơn | Alias paymentUrl, fallback amount=0; FE tự xác nhận thanh toán từ Return |

`rect`/`rectangle` được giữ như hai giá trị shapeType renderer đã có, **không** là hai tên field cho cùng dữ liệu. Path/radial phải qua test di chuyển/resize như source hiện dùng; việc legacy có fallback vẽ khác nhau giữa editor/buyer không được tuyên bố đã sửa bằng DDL. Giữ renderer không đồng nghĩa mọi bug renderer cũ đã được audit; ca hiển thị được liệt kê ở B15.

## 9. Hồ sơ organizer, follow và API hỗ trợ

User sở hữu `/api/v1/me`, `/organizer-applications`, `/admin/organizer-applications/{id}/approve|reject`, `/organizers/{subject}`, PUT/DELETE `/organizers/{subject}/follow`. Application input cố định `{organizationName:string,shortDescription:string}`; reject `{reason:string}` không rỗng; không thêm ngân hàng/giấy phép/reset-password/social login. Profile công khai `{organizerSubject:Subject,organizationName:string,shortDescription:string,followerCount:number}` chỉ ACTIVE; dùng Subject nhất quán ở đường follow, không lẫn Mongo _id.

Quy trình duyệt User dùng CAS trên application PENDING chưa roleGrant để ghi operationId/REQUESTED trước gọi Keycloak. Reject chỉ được khi chưa có roleGrant; không để một admin reject trong lúc đã bắt đầu cấp quyền. Worker quét PENDING có roleGrant, query role của đúng subject rồi retry hoặc xác nhận; chỉ sau xác nhận ghi ACTIVE. Worker quét ACTIVE và upsert organizer_profiles theo sourceApplicationId/sourceVersion; lỗi ghi profile không làm mất nguồn để rebuild. Không cần transaction Mongo xuyên document hoặc Saga thứ hai. Chưa nhận được role trong token cũ thì client refresh/login lại.

Giữ `organizer_profiles` riêng theo GOV-122; follow unique(buyerSubject,organizerSubject), PUT/DELETE idempotent, count đọc bằng index, không dual-write follower counter. Approve chỉ hoàn tất sau Keycloak xác nhận role ORGANIZER, giữ BUYER; timeout Keycloak không tự ghi ACTIVE. Đây là retry workflow cục bộ của User và nguồn danh tính, **không** thêm Saga nghiệp vụ thứ hai. Baseline quyền admin client/realm phải kiểm ở triển khai; tài liệu này không tuyên bố JSON import sẵn hoạt động hoặc giữ tài khoản cũ.

API đọc danh sách dùng `{items:[...],nextCursor:string|null}` và owner filtering ở backend; page size 1..100 là giới hạn kỹ thuật khởi đầu, không ASR. Hợp đồng CRUD phụ có thể chi tiết hóa theo lát dọc trong chính owner nhưng không thay field/luồng liên service đã mô tả. Không gọi đây là OpenAPI machine-validated: hiện là DTO + quy tắc có thể triển khai và sinh OpenAPI/JSON Schema ở bước code.

## 10. Tự kiểm, giới hạn và nội dung báo cáo

### 10.1 Mapping JSON/cột không để người code tự đoán

| Cột | Form cố định |
|---|---|
| Booking event_sales_snapshots.configuration_snapshot | SalesConfiguration, có schemaVersion=1; chứa giá/type/nhãn/venue và các mảng cấu hình để Booking không gọi Event mỗi lần |
| Booking orders.frozen_purchase_snapshot | PurchaseSnapshot nguyên bản tại freeze; cột tiền/expiry/version phải khớp payload |
| Booking order_items.selection_snapshot | `{schemaVersion:1,seats:{seatId:UUID,seatLabel:string,rowName:string,seatNumber:string}[]}`; QUANTITY/STANDING mảng rỗng; đúng quantity với SEATED |
| Booking promotion_inventory.configuration_snapshot | `{schemaVersion:1,configuration:PromotionConfig}`; id/cửa sổ/giới hạn khớp cột riêng |
| Payment order_payment_snapshots.purchase_snapshot; Ticket issuances.purchase_snapshot | PurchaseSnapshot như §4, không thêm dữ liệu profile hoặc seat map |
| Ticket tickets.display_snapshot | `{schemaVersion:1,eventTitle:string,eventStartsAt:Instant,eventEndsAt:Instant,venueName:string\|null,ticketTypeName:string,seatLabel:string\|null,rowName:string\|null,seatNumber:string\|null}` |
| Event layout_data / geometry | SeatMapDraft.layoutData / Geometry ở §7.2 |
| outbox payload | `Envelope.data` đúng type trong §5.2; envelope fields còn lại nằm ở cột, không hai bộ giá trị độc lập |
| inbox response_payload | Kết quả response có kiểu của command gốc; null nếu event không có response. Không chứa raw IPN/QR/JWT |

`issuances.payment_confirmation_id` lưu orderId dạng chuỗi — khóa chính của payment_confirmations — và không có FK xuyên owner. chargeId trong IssueTickets là khoản được chọn; handler kiểm nhất quán với purchase/order và nguồn Payment. `payment_attempts.request_key/request_hash/payment_url` lưu retry HTTP; `ticket_deliveries.request_key/request_hash` lưu một job gửi lại. Không dùng ID ngẫu nhiên mới cho cùng retry.

Sau khi nhận payload v1, validate toàn bộ DTO và tính nhất quán rồi mới ghi; SQL guard JSON chỉ là hàng rào thứ hai. Những JSON tối giản trong fixture SQL chỉ kiểm khung/constraint và không được dùng làm ví dụ request API hợp lệ.

- Có payload có kiểu cho selection, purchase, Event snapshots, Saga commands/results, check-in và geometry; không dùng object rỗng thay trường chưa chốt.
- Có một nguồn giá, owner và deadline; HTTP/broker retry có khóa; không JOIN xuyên DB.
- Không thêm service, Saga, ngoại lệ Q-07 hoặc nhánh đổi email sau tạo đơn.
- Chưa có contract tests chạy trên service thật; tính đúng của validator, crypto, gateway ack và adapter FE phải được kiểm khi hiện thực theo B15. Đây là giới hạn bằng chứng, không câu hỏi nghiệp vụ hỏi lại Minh.
- Phần đưa vào báo cáo: tại sao snapshot cố định bảo vệ giá và giảm phụ thuộc đồng bộ; vì sao mã tương quan không thay idempotency; vì sao API Return không là bằng chứng thu; phân biệt dữ liệu hình học và nguồn cung. Không đưa khóa, raw QR, callback payload thật hoặc lịch sử refactor làm căn cứ tách service.

| Phiên bản | Ngày | Nội dung |
|---|---|---|
| B13-v0.1 | 2026-09-14 | Đặc tả hợp đồng luồng chính và adapter; chưa phê duyệt gate hoặc hiện thực API |

## Phụ lục đối chiếu R0 và phép thử độc lập — 2026-09-14

Qua cửa project/lien-ket-rca.md, đối chiếu R0-v0.5 §3 các mục 4/5/6 ở mức CANDIDATE: B13 có envelope/correlation/traceparent và tên service thống nhất. Việc không đứt trace trên broker/worker/callback vẫn phải chạy kiểm B15/B16; chưa kết luận đạt runtime. Tạo tác thuộc bộ hệ thống, nguồn hình thành liệt kê ở header và quyết định trong thân bài; nguồn RCA chỉ ở phụ lục đối chiếu này, không sinh API, service hoặc bất biến. GOV-144 không nâng trạng thái của R0.
