# Logic scaffold Payment Service

Code tại `D:/DATN/flash-ticket-platform`, branch `dev/son`, ngày 28/10/2026; phần Payment chưa commit.
Flow hiện có: HTTP → JWT/role → MVC; JSON → DTO → validation khi được gọi; entity → mapper → response DTO.
Đây là nền tảng: [PaymentController](/D:/DATN/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/payment/controller/PaymentController.java:7) và [PaymentServiceImpl](/D:/DATN/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/payment/service/impl/PaymentServiceImpl.java:8) mới có khung và dependency, chưa có handler/use case thanh toán.

Đã đối chiếu trực tiếp mẫu Shop App: [OrderController](/C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/controllers/OrderController.java:17), [CategoryDTO](/C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/DTO/CategoryDTO.java:12), [MapperConfiguration](/C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/configurations/MapperConfiguration.java:8). Các ghi chú dưới chỉ so với những class này và bản đồ phong cách đã đọc.

## 1. Kiểm JWT và role trước khi vào MVC

[SecurityConfig.java:44](/D:/DATN/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/config/SecurityConfig.java:44)

```java
.requestMatchers(HttpMethod.GET, "/api/v1/orders/*/payment")
.hasRole("BUYER")
.requestMatchers("/api/v1/admin/**")
.hasRole("ADMIN")
.requestMatchers("/internal/v1/**")
.hasRole("INTERNAL_SERVICE")
```

Thiếu/sai JWT trả 401; token hợp lệ nhưng thiếu role trả 403. Role được lọc từ `realm_access.roles`; health GET public, actuator còn lại bị chặn. Route chưa có handler vẫn có thể trả 404 sau khi qua security; role BUYER chưa chứng minh ownership đơn.
**Khác Shop App:** thêm boundary resource server của FlashTicket, kiểm JWT bằng issuer discovery và chuyển lỗi security vào advice chung; không cấu hình audience check theo convention hiện hành. Runtime lấy từ [payment-service.yaml](/D:/DATN/flash-ticket-platform/infrastructure/config-server/src/main/resources/config/payment-service.yaml:28).

## 2. JSON đúng hình dạng rồi mới kiểm dữ liệu

[PaymentJsonConfig.java:19](/D:/DATN/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/config/PaymentJsonConfig.java:19)

```java
.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
```

Customizer còn chặn ép number/bool thành String, String/Float thành Integer và số thứ tự thành enum. Constructor DTO đầu vào dùng `@JsonCreator`/`@JsonProperty(required=true)`: field được phép null vẫn phải có trong JSON; không tự thêm default khi thiếu.

[ContractRules.java:52](/D:/DATN/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/validation/ContractRules.java:52)

```java
BigDecimal subtotal = new BigDecimal(value.getSubtotal()),
    discount = new BigDecimal(value.getDiscount());
if (subtotal.subtract(discount).compareTo(new BigDecimal(value.getTotal())) != 0) return false;
```

Rule purchase còn kiểm tổng dòng, quantity/seat, ID trùng, promotion và `frozenAt < expiresAt`; cancellation kiểm sales/access cùng event/version và CANCELLED. Envelope kiểm loại payload, aggregate ID và `StartPayment.requestId = messageId`. Những kiểm tra này không đọc DB hoặc phân xử deadline bằng clock.
**Khác Shop App:** snapshot/command là class Lombok bất biến, list được copy; có rule liên trường ngoài annotation field. Jackson không tự chạy Bean Validation: các test hiện gọi `Validator.validate`; chưa có REST/message business boundary để thực hiện bước đó trong runtime.

## 3. Snapshot cục bộ và version của row

[OrderPaymentSnapshot.java:42](/D:/DATN/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/entity/OrderPaymentSnapshot.java:42)

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "purchase_snapshot", nullable = false, columnDefinition = "jsonb")
private PurchaseSnapshot purchaseSnapshot;
```

Payment giữ bản sao typed của đơn trong DB của mình; `orderId` là UUID tham chiếu, không map sang entity Order của Booking. Tiền dùng BigDecimal nguyên đồng; thời gian Instant, enum STRING; cấu hình JPA `ddl-auto=none` không tự sửa schema.

[PaymentAttempt.java:55](/D:/DATN/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/entity/PaymentAttempt.java:55)

```java
// Version null trên row mới để Spring Data nhận biết INSERT.
@Version
@Column(name = "row_version", nullable = false)
private Long rowVersion;
```

Version chỉ có trên PaymentAttempt, Refund, EventRefundRun, EventPayout, PaymentSaga. `sourceOrderVersion` là phiên bản snapshot nguồn, khác `rowVersion`; Inbox Payment không có version. Chưa có luồng ghi/transaction use case; không suy mọi `save()` là INSERT hoặc đã commit.
**Khác Shop App:** JSONB typed, UUID/Instant/BigDecimal và optimistic version bám DDL Payment. FK ghép/unique/partial index vẫn thuộc schema, không được thay bằng association đơn; mapping chưa được kiểm trên PostgreSQL trong lượt triển khai này.

## 4. Mapping không được trộn hai đơn hoặc làm tròn tiền

[PaymentMapper.java:34](/D:/DATN/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/mapper/PaymentMapper.java:34)

```java
if (attempt == null
    || snapshot == null
    || attempt.getOrderId() == null
    || !attempt.getOrderId().equals(snapshot.getOrderId()))
  throw new BusinessException(ErrorCode.INTERNAL_ERROR);
```

`@BeforeMapping` chạy trước khi ghép attempt/snapshot thành PaymentStart; expiry lấy từ snapshot cục bộ. [WireValueConverter.toMoney](/D:/DATN/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/mapper/WireValueConverter.java:13) dùng `RoundingMode.UNNECESSARY` và giới hạn 19 chữ số: tiền lẻ/âm/quá lớn gây lỗi mapping, không âm thầm sửa giá trị. Mapper còn chuyển Refund thành RefundView, chưa ráp PaymentView bằng query DB.
**Khác Shop App:** dùng MapStruct theo Booking thay cho ModelMapper của class mẫu đã đọc; `unmappedTargetPolicy=ERROR` buộc mapping target đầy đủ, dependency converter được constructor-inject.

## 5. Rabbit mới có converter, chưa có xử lý message

[RabbitMqConfig.java:14](/D:/DATN/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/config/RabbitMqConfig.java:14)

```java
return new JacksonJsonMessageConverter(
    jsonMapper,
    "com.flashticket.payment_service.dto.message",
    "com.flashticket.payment_service.dto.snapshot",
    "com.flashticket.payment_service.dto.response");
```

Converter dùng chung JsonMapper của Boot và giới hạn package DTO. YAML giữ topology ngoài app (`dynamic=false`), confirms/returns/mandatory và default MANUAL ACK. Chưa có listener/publisher/relay nên chưa có ACK sau commit, durable dedupe, canonical hash comparison hoặc giao dịch inbox/outbox; các entity này mới là mapping dữ liệu.
**Khác Shop App:** converter Jackson 3/AMQP và Envelope là hợp đồng liên service của FlashTicket; cấu hình xác nhận broker không chứng minh consumer đã xử lý.

## 6. Lỗi có mã ổn định và log cùng correlation

[GlobalExceptionHandler.java:48](/D:/DATN/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/exception/GlobalExceptionHandler.java:48)

```java
ErrorCode code = invalidReturn ? ErrorCode.INTERNAL_ERROR : ErrorCode.VALIDATION_FAILED;
return problem(
    request, code.status(), code, exception, invalidReturn ? List.of() : violations(exception));
```

Validation input trả 400; lỗi validation dữ liệu trả về là 500. Advice giữ status/header của framework, trả Problem và tối đa 50 violations; 401/403 từ security cũng đi qua resolver này. [Phần tạo Problem](/D:/DATN/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/exception/GlobalExceptionHandler.java:115) gắn correlation vào MDC chỉ trong lần ghi lỗi rồi khôi phục giá trị cũ, để header/body/log cùng UUID.
**Khác Shop App:** so với `OrderController.createOrder` trả `e.getMessage()`, Payment dùng message cố định từ ErrorCode và advice chung. Log native chỉ giữ allowlist; SafeStackTracePrinter bỏ exception message. Chưa có correlation filter cho request thành công; native Boot `/error` ngoài advice.

## 7. Bằng chứng và phần chưa kiểm

Bằng chứng đã có ngày 28/10/2026: `verify` bằng JDK21 thành công, 21 test/0 failure/0 error/0 skipped, Spotless sạch và SpotBugs 0 finding. Gồm contract, mapper, JWT ký thật/local JWKS, error/log, config và context cô lập; không thay bằng chứng kết nối hạ tầng thật.
PostgreSQL IT đã compile nhưng chưa chạy vì thiếu môi trường test. Smoke với YAML mới chưa chạy: lần kiểm trước Config Server chưa phục vụ cấu hình mới. Chưa có flow VNPay/Saga/refund/payout, ownership GET hoặc nghiệp vụ thanh toán hoàn chỉnh. Điều kiện chạy lại xem [handoff](/D:/DATN/flash-ticket-platform/services/payment-service/HANDOFF.md).
Lượt viết tài liệu này chỉ đọc source/report cũ và kiểm tài liệu; không sửa Java hoặc chạy lại test/API live.
