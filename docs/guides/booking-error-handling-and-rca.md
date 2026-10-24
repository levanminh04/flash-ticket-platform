# Xử lý lỗi và RCA trong Booking Service

Tài liệu này là phần tiếp theo của [hướng dẫn chuyển từ ShopApp-BE sang FlashTicket](shopapp-be-to-flash-ticket-backend.md). Trọng tâm ở đây là cách một lỗi đi qua HTTP, Security, RabbitMQ hoặc scheduler; dùng code sample theo đúng các class đang có trong Booking. Luồng consumer nghiệp vụ, outbox relay và retry worker chưa được triển khai nên được ghi rõ là **TARGET**.

## 1. Khác biệt cần nhớ

| Cách quen thuộc ở ShopApp-BE | Cách Booking đang dùng |
|---|---|
| Exception thường được xử lý ở controller hoặc service | Service ném lỗi; HTTP/security boundary đổi lỗi thành `Problem` |
| Message exception có thể dùng trực tiếp để trả lỗi | `BusinessErrorCode` chọn message an toàn; exception kỹ thuật giữ nguyên |
| Catch rồi log ở nhiều tầng | Boundary chịu trách nhiệm log một lần; tầng dưới không log rồi ném lại |
| Request ID thường chỉ phục vụ HTTP | `requestId`, `correlationId`, `messageId`, `orderId` có ý nghĩa riêng và được truyền theo boundary |
| Rabbit listener có thể catch lỗi rồi ACK/NACK tại chỗ | Adapter hiện chỉ xử lý fatal message theo native Spring AMQP; ACK nghiệp vụ cần được thiết kế cùng consumer và transaction |
| Scheduler thường in stack trace mặc định | Scheduler dùng chung context và `FailureLog`; worker tương lai vẫn phải tự quản rollback/retry |

Quy tắc chính: **exception cho biết điều gì lỗi; boundary quyết định cách phản hồi, ACK/reject và log**. Không biến classifier thành nơi quyết định retry hoặc state nghiệp vụ.

## 2. Ném business error và ánh xạ HTTP

`BusinessException` mang một `BusinessErrorCode` cố định. Message tiếng Việt nằm trong enum, còn HTTP status do `GlobalExceptionHandler` chọn.

```java
if (order.getExpiresAt().isBefore(now)) {
  throw new BusinessException(BusinessErrorCode.ORDER_EXPIRED);
}

if (seat.getState() != SeatState.AVAILABLE) {
  throw new BusinessException(BusinessErrorCode.SEAT_UNAVAILABLE);
}
```

Các điều kiện trên thuộc use case khi nó được triển khai. Không kiểm tra ownership trong DTO/mapper và không ghép message từ giá trị người dùng gửi. Khi cần bọc lỗi mà vẫn giữ chứng cứ gốc, truyền cause:

```java
throw new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND, repositoryFailure);
```

Booking đang ánh xạ các nhóm lỗi chính như sau:

| Tình huống | Code | HTTP |
|---|---|---:|
| Order không tồn tại | `ORDER_NOT_FOUND` | 404 |
| Ghế/tồn kho không còn khả dụng, order hết hạn hoặc conflict trạng thái | `SEAT_UNAVAILABLE`, `ORDER_EXPIRED`, `CONCURRENT_MODIFICATION` | 409 |
| Promotion không hợp lệ với use case | `PROMOTION_NOT_FOUND`, `PROMOTION_EXHAUSTED` | 422 |
| Database, broker, OAuth nội bộ hoặc dependency tạm thời không sẵn sàng | code kỹ thuật tương ứng | 503 |
| JWT thiếu/sai hoặc role không đủ | `UNAUTHENTICATED` / `ACCESS_DENIED` | 401 / 403 |
| Lỗi bất ngờ, validation return value hoặc invariant nội bộ | `INTERNAL_ERROR` | 500 |

**Đang dùng:** `GlobalExceptionHandler` phân loại exception, ánh xạ status và gọi `ProblemResponses`. Response theo DTO sẵn có, có bốn trường:

```json
{
  "code": "ORDER_EXPIRED",
  "message": "Đơn hàng đã hết thời hạn giữ chỗ.",
  "correlationId": "00000000-0000-4000-8000-000000000021",
  "violations": []
}
```

MVC input validation trả 400; validation ở return value hoặc invariant server trả 500. Framework headers như `Allow` ở 405 được giữ lại. Security 401/403 dùng cùng DTO và Bearer challenge cố định, không trả raw `error_description` hoặc JWT parser message.

## 3. Giữ exception native và phân loại

Không cần đổi lỗi JPA, Feign, OAuth hay AMQP thành `BusinessException`. `ErrorClassifier.classify(Throwable)` trả `Failure(code, message, errorType, exception)`; field `exception` vẫn giữ throwable ban đầu để chẩn đoán.

```java
Failure failure = ErrorClassifier.classify(exception);
```

Một số phân loại đang có:

| Native exception | Code nội bộ |
|---|---|
| Optimistic locking failure | `CONCURRENT_MODIFICATION` |
| Pessimistic locking failure | `RESOURCE_BUSY` |
| Query timeout / database connection failure | `DATASTORE_TIMEOUT` / `DATASTORE_UNAVAILABLE` |
| AMQP connect failure | `MESSAGE_BROKER_UNAVAILABLE` |
| Feign timeout / connect / HTTP response failure | `DEPENDENCY_TIMEOUT` / `DEPENDENCY_UNAVAILABLE` / `DEPENDENCY_FAILURE` |
| Machine OAuth token acquisition failure | `INTERNAL_AUTH_UNAVAILABLE` |
| AMQP message conversion/validation failure | `INVALID_MESSAGE` |

Service không nên catch chỉ để log rồi ném lại:

```java
// Không làm ở service:
try {
  repository.save(order);
} catch (DataAccessException error) {
  log.error("save failed", error);
  throw error;
}
```

Để lỗi đi tới boundary sở hữu response hoặc disposition; boundary gọi classifier và ghi log an toàn một lần. Nếu use case thật sự cần đổi lỗi, bọc exception và giữ cause thay vì bỏ chứng cứ gốc. Timeout chỉ chứng minh caller không nhận phản hồi kịp lúc; nó không chứng minh service đích chưa thực hiện tác dụng. Khi tác dụng có thể đã xảy ra, cần idempotency hoặc reconciliation trước khi kết luận.

Booking hiện chưa có production payment Feign client; payment đi qua RabbitMQ. `InternalFeignConfig` chỉ là cấu hình opt-in cho client nội bộ tương lai, không phải một API payment đang chạy.

## 4. Correlation và context theo từng boundary

Luồng HTTP hiện tại:

```text
HTTP request
  → CorrelationFilter cấp requestId và correlationId tạm
  → Security / MVC / exception boundary
  → ProblemResponses trả body và X-Correlation-Id cùng giá trị
  → log JSON mang các ID có trong context
```

`requestId` do server cấp cho từng HTTP request. `X-Correlation-Id` từ client chỉ được nhận nếu là một UUID canonical; nó là nhãn truy vết, không xác nhận order tồn tại hoặc quyền sở hữu.

Sau khi lookup và xác minh ownership, use case có thể gắn order vào context:

```java
BookingContext.bindOrder(verifiedOrder.getId());
```

Không gọi hàm này chỉ dựa trên order ID trong URL/header. Việc bind cập nhật `orderId`, nâng `correlationId` thành order ID và đồng bộ response header khi response chưa commit.

Tên `requestId` trong `StartPayment` là command ID theo B13, khác với HTTP request ID của observability. Không dùng hai giá trị thay thế nhau.

Feign internal interceptor xóa correlation do caller tự gán rồi chỉ chuyển UUID đang có trong `BookingContext`; interceptor chỉ được nạp qua `@FeignClient(configuration = InternalFeignConfig.class)`. Client ngoài không nhận header này. Với background task, mở scope từ ID đã lưu trong message/job; không lấy correlation từ thread đã tạo task.

```java
try (var scope = BookingContext.open(Map.of("correlationId", savedCorrelationId.toString()))) {
  // Xử lý đúng một message/job item trong scope này.
}
```

`BookingContext.open` khôi phục MDC trước đó cả khi có exception. Nó chỉ nhận các ID UUID nằm trong allowlist, bỏ các field như `Authorization` và không tự tạo trace/span.

## 5. Log để lần theo RCA

Ghi log qua `FailureLog`, không serialize throwable hoặc map bằng tay:

```java
Failure failure = ErrorClassifier.classify(exception);
FailureLog.record(
    failure,
    "booking.request.failed",
    "order.create",
    FailureLog.Outcome.FAILED);
```

`eventName` và `operation` phải thuộc allowlist. `REJECTED` dành cho request/business bị từ chối; `FAILED` cho lần xử lý thất bại; `RETRY` chỉ sau khi lịch retry được ghi thành công; `UNKNOWN` khi chưa biết tác dụng bên ngoài đã xảy ra hay chưa.

Mỗi error log là JSON một dòng. Các trường chính gồm `schemaVersion`, UTC `timestamp`, `severity`, `serviceName`, `serviceVersion`, `instanceId`, `environment`, `eventName`, `operation`, `outcome`, `errorCode`, `errorType` và context ID nếu boundary đã có. Message log là câu tiếng Anh cố định; API message là tiếng Việt. Stack printer giữ class/frame/cause type với giới hạn nhưng bỏ exception message, SQL, payload, JWT và secret.

Khi điều tra, tìm theo `correlationId` hoặc `requestId`, sau đó đối chiếu `eventName`, `operation`, `outcome`, `errorCode`, `errorType` và trace/span nếu instrumentation có cấp. Những trường này là bằng chứng để lập giả thuyết RCA; một timeout hoặc stack frame riêng lẻ chưa đủ xác nhận nguyên nhân gốc. Runtime cần cấp `BOOKING_SERVICE_VERSION` và `BOOKING_ENVIRONMENT`; `BOOKING_HOST_ID` là tùy chọn. Giá trị local fallback không phải metadata deploy.

## 6. RabbitMQ: fatal message khác lỗi nghiệp vụ

**Đang dùng:** `RabbitMqConfig` gắn `BookingRabbitErrorHandler` vào listener container native. Handler giữ `ConditionalRejectingErrorHandler`, bật reject cho MANUAL acknowledgement và không discard fatal message chỉ vì có `x-death`. Conversion/type/validation fatal được log với `INVALID_MESSAGE`; failed message body và headers không được in. Test kiểm tra queue/DLX/policy/binding trong `deploy/rabbitmq/definitions.json` ở mức cấu hình tĩnh.

Các queue hiện được khai báo static (`dynamic=false`). Adapter không tạo topology, không tự ACK, không tự ghi retry và không biến mọi `BusinessException` thành fatal.

**TARGET — consumer nghiệp vụ tương lai:** malformed envelope đi theo native fatal reject/DLX. Sau khi validate envelope, consumer lấy correlation/message ID từ payload đã tin cậy, ghi inbox + kết quả command + outbox trong cùng transaction, rồi ACK sau commit. Business rejection là kết quả đã persist và vẫn có thể ACK sau commit. Dependency failure làm transaction rollback; retry/disposition phải được quyết định riêng theo workflow đó. Các interface bên dưới là hợp đồng minh họa để đoạn listener có thể đọc độc lập; Booking chưa có các loại này.

```java
// TARGET: các contract này chưa tồn tại trong Booking.
interface BookingCommand {
  UUID correlationId();
  UUID messageId();
}
interface EnvelopeValidator {
  void validate(BookingCommand command); // malformed: ném MessageConversionException
}
interface InboxRepository {
  void record(UUID messageId);
}
interface BookingCommandHandler {
  CommandResult handle(BookingCommand command);
}
interface OutboxRepository {
  void appendResult(BookingCommand command, CommandResult result);
}
record CommandResult(boolean accepted, String resultCode) {}

// Các dependency dưới đây được inject vào consumer khi triển khai.
final class BookingConsumer {
  private final EnvelopeValidator envelopeValidator;
  private final TransactionTemplate transactionTemplate;
  private final InboxRepository inboxRepository;
  private final BookingCommandHandler commandHandler;
  private final OutboxRepository outboxRepository;

  // TARGET: BookingCommandHandler và các repository cần cùng transaction manager.
@RabbitListener(queues = "${booking.consumer.queue}", ackMode = "MANUAL")
  void consume(BookingCommand command, Channel channel, Message message) throws IOException {
    envelopeValidator.validate(command); // ném MessageConversionException nếu malformed

    try (var scope = BookingContext.open(Map.of(
        "correlationId", command.correlationId().toString(),
        "messageId", command.messageId().toString()))) {
      transactionTemplate.executeWithoutResult(status -> {
        inboxRepository.record(command.messageId());
        CommandResult result = commandHandler.handle(command); // accepted hoặc business rejection
        outboxRepository.appendResult(command, result);
      }); // trả về sau commit
      channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
  }
}
```

Đây là TARGET vì Booking hiện chưa có `@RabbitListener` nghiệp vụ, handler, inbox/outbox repository cho flow này hay cấu hình `booking.consumer.queue`. Đoạn code giả định transaction và các repository cùng tham gia transaction manager; khi triển khai phải bổ sung nhánh rollback/disposition theo use case. Không coi test unit/static policy là bằng chứng broker thật đã route DLX hoặc consumer đã ACK đúng thời điểm.

## 7. Scheduler, worker và retry

**Đang dùng:** `WorkerErrorConfig` gắn một ErrorHandler vào `ThreadPoolTaskScheduler` và `SimpleAsyncTaskScheduler` do Boot tạo. Exception thoát khỏi scheduled task được ghi một lần là `booking.worker.failed / worker.execute / FAILED`. Handler mở scope rỗng để không mang business ID cũ sang log rồi khôi phục MDC.

Chưa có cron/job hoặc worker nghiệp vụ. Khi có worker, mỗi item phải đọc correlation bền vững từ record đã lưu, mở scope riêng, rollback transaction trước khi lên lịch retry. Chỉ ghi `RETRY` sau khi store xác nhận đã lưu lần thử tiếp theo. Đây là contract minh họa cho TARGET:

```java
// TARGET: các contract này chưa tồn tại trong Booking.
record WorkerItem(UUID id, UUID correlationId) {}
interface WorkerProcessor {
  void process(WorkerItem item);
}
interface BookingRetryStore {
  void schedule(UUID itemId, Instant nextAttemptAt);
}

void processOne(
    WorkerItem item,
    TransactionTemplate transactionTemplate,
    WorkerProcessor processor,
    BookingRetryStore retryStore,
    Instant nextAttemptAt) {
  try (var scope = BookingContext.open(Map.of("correlationId", item.correlationId().toString()))) {
    try {
      transactionTemplate.executeWithoutResult(status -> processor.process(item));
    } catch (RuntimeException processingFailure) {
      try {
        retryStore.schedule(item.id(), nextAttemptAt); // lưu lịch retry sau rollback
        FailureLog.record(
            ErrorClassifier.classify(processingFailure),
            "booking.worker.failed",
            "worker.execute",
            FailureLog.Outcome.RETRY);
      } catch (RuntimeException persistenceFailure) {
        persistenceFailure.addSuppressed(processingFailure);
        FailureLog.record(
            ErrorClassifier.classify(persistenceFailure),
            "booking.worker.failed",
            "worker.execute",
            FailureLog.Outcome.FAILED);
      }
    }
  }
}
```

Nếu lưu retry lỗi, kết quả là `FAILED`, giữ cả lỗi xử lý và persistence để RCA. Không ghi `RETRY` chỉ vì đã catch exception hoặc thấy message redelivered.

### Outbox publisher và confirm

Booking chưa có outbox relay hoặc publisher nghiệp vụ. Khi triển khai, chỉ đánh dấu message `SENT` sau publisher confirm ACK và không có returned message. NACK/returned có thể lên lịch retry; timeout hoặc lỗi khi chờ confirm là `UNKNOWN` vì chưa biết broker đã nhận message hay chưa. Các kiểu sau định nghĩa đúng những collaborator mà ví dụ dùng:

```java
// TARGET: Spring AMQP cung cấp RabbitTemplate và CorrelationData;
// các record/interface của ứng dụng bên dưới chưa có trong Booking.
record OutboxMessage(UUID id, UUID correlationId, String exchange, String routingKey, Object payload) {}
interface OutboxStateStore {
  void markSent(UUID messageId);
  void markRetryable(UUID messageId, String reason);
  void markUnknown(UUID messageId, String reason);
}

void publishOne(
    OutboxMessage outbox,
    RabbitTemplate rabbitTemplate,
    OutboxStateStore stateStore,
    Duration confirmTimeout) {
  CorrelationData confirm = new CorrelationData(outbox.id().toString());
  try (var scope = BookingContext.open(Map.of("correlationId", outbox.correlationId().toString()))) {
    rabbitTemplate.convertAndSend(
        outbox.exchange(), outbox.routingKey(), outbox.payload(), confirm);
    CorrelationData.Confirm result = confirm.getFuture().get(
        confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);

    if (!result.isAck() || confirm.getReturned() != null) {
      stateStore.markRetryable(outbox.id(), result.getReason());
      return;
    }
    stateStore.markSent(outbox.id());
  } catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    stateStore.markUnknown(outbox.id(), "publisher confirm interrupted");
  } catch (ExecutionException | TimeoutException unknown) {
    stateStore.markUnknown(outbox.id(), "publisher confirm outcome unknown");
  }
}
```

Ví dụ giả định RabbitTemplate đã bật correlated publisher confirms và returns trong cấu hình broker/client. Cần quyết định riêng nếu cập nhật trạng thái outbox cũng lỗi; không được ghi `SENT` khi chưa có confirm thành công.

## 8. Cách lần một lỗi từ đầu đến cuối

Ví dụ order hết hạn trong HTTP request:

1. `CorrelationFilter` cấp `requestId` và correlation tạm.
2. Use case kiểm dữ liệu và ném `BusinessException(ORDER_EXPIRED)`.
3. `GlobalExceptionHandler` giữ code/message ổn định và ánh xạ sang 409.
4. `ProblemResponses` tạo `Problem`, header correlation và một log `REJECTED`.
5. Người vận hành tìm request/order theo correlation, kiểm code, operation và trace; không cần raw message hoặc payload.

Ví dụ message JSON sai schema:

1. Spring AMQP conversion thất bại trước khi business listener xử lý.
2. `BookingRabbitErrorHandler` phân loại `INVALID_MESSAGE`.
3. Native fatal strategy reject không requeue; topology hiện có quyết định DLX/DLQ.
4. Log không chứa body/header. Unit test kiểm handler và topology file, còn việc broker thật chuyển message là nghiệm thu tích hợp riêng.

## 9. Nguồn cần đọc và điều chưa được chứng minh

- [Hướng dẫn chuyển từ ShopApp-BE](shopapp-be-to-flash-ticket-backend.md)
- [Business errors và classifier](../../services/booking-service/src/main/java/com/flashticket/booking_service/exception/)
- [HTTP error boundaries](../../services/booking-service/src/main/java/com/flashticket/booking_service/web/error/)
- [Context và structured logging](../../services/booking-service/src/main/java/com/flashticket/booking_service/observability/)
- [Rabbit error handler](../../services/booking-service/src/main/java/com/flashticket/booking_service/messaging/BookingRabbitErrorHandler.java)
- [Scheduler error config](../../services/booking-service/src/main/java/com/flashticket/booking_service/config/WorkerErrorConfig.java)
- [B13 — API và event contracts](../architecture/B13-api-and-event-contracts.md)
- [B14 — main flow sequences](../architecture/B14-main-flow-sequences.md)
- [B16 — observability baseline](../architecture/B16-observability-baseline.md)
- [RabbitMQ topology](../../deploy/rabbitmq/definitions.json)
- [Booking test results and limits](../../services/booking-service/TESTING.md)

Fast suite đã qua 342 test; JSON formatter thật, HTTP/security boundary, Rabbit fatal handler và scheduler boundary có test. Chưa chạy broker/database thật cho flow này, chưa có outbox relay/consumer/retry worker, và chưa kiểm tra collector export/masking, tracing agent hoặc fault-injection RCA. Vì vậy đây là bằng chứng cho code boundary đã cài, không phải chứng nhận RCA end-to-end.

## 10. Quy tắc thực dụng

- Ném `BusinessException` cho quyết định nghiệp vụ; để native exception biểu đạt lỗi kỹ thuật.
- Không trả `exception.getMessage()` ra API hoặc ghi throwable thô vào log.
- Không log-and-rethrow ở service; boundary ghi một lần.
- Chỉ bind order sau khi xác minh ownership.
- Không nhầm HTTP `requestId` với `StartPayment.requestId`.
- Chỉ ACK consumer sau commit; không suy retry bền vững từ MANUAL ACK hoặc requeue mặc định.
- Chỉ đánh outbox sent sau confirm ACK và không có returned message; timeout là kết quả chưa biết.
- Chỉ ghi `RETRY` sau khi lần thử kế tiếp đã được lưu.
- Giữ các ví dụ TARGET ở tài liệu cho tới khi use case thật được triển khai và kiểm thử tích hợp.
