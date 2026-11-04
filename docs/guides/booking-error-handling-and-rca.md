# Global exception trong Booking Service

Phạm vi được chốt lại ngày 25/10/2026: xử lý exception tập trung như ShopApp và ghi log
có message phù hợp cho việc điều tra sau này. Chưa triển khai gửi lỗi/log qua RabbitMQ
để xử lý RCA, collector, retry worker hoặc cơ chế tracing cho mọi request.

## 1. Cấu trúc hiện tại

```text
exception/
  BusinessException.java
  ErrorCode.java
  GlobalExceptionHandler.java
dto/response/
  Problem.java
```

Đã bỏ package `web.error` cùng `ProblemResponses`, `CorrelationFilter`,
`RequestCorrelation` và `BookingErrorController`. Không tạo class khác để thay nguyên
các lớp trung gian đó. Global handler tạo response và ghi log trực tiếp.

| Thành phần | Nhiệm vụ |
|---|---|
| `BusinessException` | Service ném khi một điều kiện nghiệp vụ không đạt |
| `ErrorCode` | Một enum chứa code, message an toàn và HTTP status; nhận diện một số native exception |
| `GlobalExceptionHandler` | Bắt lỗi nghiệp vụ, validation, security và lỗi bất ngờ; trả HTTP response |
| `Problem` | DTO chứa JSON lỗi trả về cho client, không phải thành phần gửi message |

`ProblemResponses` trước đây là helper tạo response/log dùng chung. Nó khác DTO `Problem`.
`CorrelationFilter` trước đây chạy trên mọi request để cấp request/correlation ID và quản lý
MDC. Hai lớp đó không gửi lỗi sang RabbitMQ; chúng đã được bỏ vì chưa cần ở scaffold này.

## 2. Dùng như ShopApp

**TARGET — khi viết use case Booking**, service chỉ cần ném lỗi đúng nghĩa:

```java
if (!now.isBefore(order.getExpiresAt())) {
  throw new BusinessException(ErrorCode.ORDER_EXPIRED);
}
if (seat.getState() != SeatState.AVAILABLE) {
  throw new BusinessException(ErrorCode.SEAT_UNAVAILABLE);
}
```

Global handler đang xử lý trực tiếp:

```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<Problem> handleBusiness(
    BusinessException exception, HttpServletRequest request) {
  ErrorCode code = exception.getErrorCode();
  return problem(request, code.status(), code, exception, List.of());
}
```

`problem(...)` là một hàm private trong chính handler, không phải một bean/lớp trung gian.
Các handler validation và fallback cũng gọi hàm này. Không kế thừa
`ResponseEntityExceptionHandler`, không cần inject một service để tạo response lỗi.

Ví dụ response:

```json
{
  "code": "ORDER_EXPIRED",
  "message": "Đơn hàng đã hết thời hạn giữ chỗ.",
  "correlationId": "00000000-0000-4000-8000-000000000021",
  "violations": []
}
```

Giữ bốn field của DTO B13 để client không phải đổi cấu trúc đọc lỗi. HTTP status lấy từ
enum: order không tồn tại 404; conflict trạng thái/ghế hết 409; promotion không hợp lệ
422; dependency không sẵn sàng 503; lỗi bất ngờ 500. Không copy fallback 404 cho mọi
exception hoặc trả nguyên `exception.getMessage()` từ ShopApp.

## 3. Validation và lỗi kỹ thuật

DTO input không hợp lệ trả 400 `VALIDATION_FAILED`, với field gốc và mã như `REQUIRED`,
`INVALID_FORMAT`, `OUT_OF_RANGE`, `INVALID_VALUE`. Không trả rejected value hay key Map
do client nhập. JSON sai hoặc tham số sai kiểu trả 400 `INVALID_REQUEST`.
Validation output/invariant server trả 500. Native framework headers như `Allow` ở 405
vẫn được giữ.

Không tạo custom exception cho mọi lỗi JPA, Feign, OAuth hoặc Redis. Để native exception
đi lên; `ErrorCode.from(exception)` chọn mã phù hợp:

```java
// QueryTimeoutException -> DATASTORE_TIMEOUT
// OptimisticLockingFailureException -> CONCURRENT_MODIFICATION
// OAuth2AuthorizationException -> INTERNAL_AUTH_UNAVAILABLE
// Feign timeout -> DEPENDENCY_TIMEOUT
ErrorCode code = ErrorCode.from(exception);
```

Không catch chỉ để log rồi throw lại ở mọi tầng. Giữ cause nếu thực sự cần bọc lỗi.
DB kết nối lỗi không có nghĩa là order không tồn tại; không đổi nó thành `ORDER_NOT_FOUND`.

## 4. Lỗi Security đi vào global handler thế nào?

JWT/role có thể lỗi trước khi controller chạy. Vì vậy hai callback của Security vẫn cần,
nhưng chỉ ủy quyền cho `HandlerExceptionResolver` có sẵn của Spring:

```java
resolver.resolveException(request, response, null, exception);
```

Resolver chuyển exception tới `GlobalExceptionHandler`; handler trả cùng DTO cho
401 `UNAUTHENTICATED` và 403 `ACCESS_DENIED`. Security giữ Bearer challenge trên response.
JWT nội bộ, client-credentials, cache token và timeout Feign không đổi.

## 5. Message/log phục vụ RCA ở mức hiện tại

Handler dùng SLF4J trực tiếp. Message nêu lý do an toàn, ví dụ:

```text
HTTP request rejected: Đơn hàng đã hết thời hạn giữ chỗ.
```

JSON log kèm `errorCode`, `errorType`, `httpMethod`, `httpStatus`, `correlationId`,
`operation` và metadata service/build/instance. HTTP 4xx và `CONFIG_NOT_READY` ghi INFO;
lỗi xử lý ghi ERROR với stack trace đã lọc. Không đưa JWT, body, email, SQL hoặc message
nguyên gốc của dependency vào log/response. Một timeout chưa chứng minh dependency
chưa thực hiện tác dụng; không dùng nó làm kết luận RCA hay tự retry thanh toán.

`correlationId` giờ chỉ được giải quyết khi handler trả lỗi: nhận một UUID canonical
hợp lệ từ header nếu đọc được, hoặc tạo UUID mới. Giá trị đó có trong body/header và
error log. Không có filter cấp `X-Request-Id` hoặc quản lý MDC trên mọi request; request
thành công trực tiếp tới Booking không được scaffold này tự thêm tracing header.
Không còn helper tự bind order. Feign chỉ forward correlation nếu ứng dụng đã chủ động
đặt UUID hợp lệ trong MDC; không có cơ chế tự nối toàn bộ HTTP flow ở Booking.

Logging dùng JSON native của Spring Boot trong YAML. Hai hook nhỏ trong `config` giữ
timestamp UTC/schemaVersion số và stack frames không chứa throwable message.
Đây là log local/stdout, chưa có publisher gửi error event phục vụ RCA qua RabbitMQ.

## 6. Phần để sau và giới hạn

- `/error` và `sendError` do Spring Boot xử lý mặc định; không còn custom error controller
  ép mọi lỗi servlet về DTO `Problem`. Global advice xử lý exception đi qua MVC và
  exception Security được resolver chuyển vào; không bắt mọi lỗi của filter/job/consumer.
- `BookingRabbitErrorHandler` có sẵn chỉ xử lý lỗi khi nhận message bằng native AMQP
  fatal strategy và ghi log; nó không phải publisher gửi log RCA. Không thay đổi disposition
  của RabbitMQ trong task rút gọn HTTP này.
- Chưa triển khai consumer nghiệp vụ, outbox relay, retry worker, tracing toàn luồng hoặc
  pipeline RCA qua RabbitMQ. Khi cần, làm task riêng theo flow/transaction thực tế.

Xem [source exception](../../services/booking-service/src/main/java/com/flashticket/booking_service/exception/),
[SecurityConfig](../../services/booking-service/src/main/java/com/flashticket/booking_service/config/SecurityConfig.java)
và [TESTING.md](../../services/booking-service/TESTING.md). B13 giữ contract DTO hiện tại;
B16 là hướng đầy đủ để triển khai sau, không phải lý do tự thêm toàn bộ framework ở scaffold.
