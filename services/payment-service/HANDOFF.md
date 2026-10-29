# Handoff — Payment Service scaffold

Scaffold theo [spec](../../docs/java-workflow/specs/2026-10-28-payment-service-scaffold-design.md) và [plan đã duyệt](../../docs/java-workflow/plans/2026-10-28-payment-service-scaffold-plan.md), cập nhật 28/10/2026.

## Đã có

- 13 entity/repository đúng danh mục Payment; hai embedded ID, năm entity có optimistic version, JSONB typed PurchaseSnapshot.
- DTO class Lombok cho payment view, snapshot và message B13; JSON strict, required nullable field, Bean Validation và rule liên trường.
- MapStruct cho PaymentStart/RefundView; Money chuỗi nguyên đồng, chặn ghép dữ liệu khác order.
- Khung payment/refund/finance service và payment/internal-finance controller, constructor injection theo Booking/Shop App.
- Config Client/Eureka, PostgreSQL/JPA, JWT/role MVC, Rabbit JSON converter, global advice/Problem và structured logging có lọc dữ liệu nhạy cảm.

Controller chưa có handler nghiệp vụ. Chưa có consumer, worker, outbox relay, VNPay/IPN/Return, tạo QR thanh toán, Saga, refund/payout hoặc HTTP client nội bộ. MANUAL ACK là default scaffold, chưa có thao tác ACK message. Flow thật cần bổ sung ownership, transaction, idempotency và disposition theo contract.

## Runtime

Dùng **JDK21**; Enforcer `[21,22)`, compiler release21. Giữ riêng với JDK23 của Booking.

Local application giữ tên `payment-service` và Config Server import bắt buộc. Runtime YAML tại [payment-service.yaml](../../infrastructure/config-server/src/main/resources/config/payment-service.yaml); Eureka chung từ Config Server; port mặc định **8083**.

Nạp biến vào process/IDE từ `.env` gốc; Spring không tự đọc file `.env`:

- `CONFIG_SERVER_URL`, `EUREKA_SERVER_URL`.
- `PAYMENT_DB_URL`, `PAYMENT_DB_USERNAME`, `PAYMENT_DB_PASSWORD`.
- `RABBITMQ_HOST`, `RABBITMQ_PASSWORD`; port/user/vhost/exchange có fallback trong YAML.
- `KEYCLOAK_ISSUER_URI`; chỉ issuer discovery, không jwk-set-uri hoặc audience runtime check.
- Tùy chọn: `PAYMENT_SERVICE_PORT`, `PAYMENT_SERVICE_VERSION`, `PAYMENT_INSTANCE_ID`, `PAYMENT_ENVIRONMENT`.

Biến runtime bắt buộc đã có trong `.env.example`. Không cần Redis, internal OAuth client secret hoặc merchant key để compile/test scaffold.

PostgreSQL: `payment_db` / `payment_schema`, bảng `tbl_*`; Hikari max5/minIdle1 và `SET ROLE payment_owner` với account được cấp quyền. JPA ddl-auto=none, open-in-view=false, JSON mapper jackson3, JDBC UTC. Không dùng ERD reader chạy service. App không tự provision/sửa schema.

Rabbit: topology từ deploy, dynamic=false, exchange business.ex, correlated confirms/returns/mandatory. Converter dùng JsonMapper Boot và chỉ tin package DTO Payment. Sáu queue đã provision tương ứng finance-configured, event-cancelled, start, decision, ticket-issuance-failed, order-completed; scaffold chưa consume.

JSON log dùng allowlist, metadata payment-service, UTC/schemaVersion1 và stack trace không exception message. Correlation lỗi khớp header/body/log; khôi phục MDC cũ sau ghi. Native Boot /error ngoài advice; chưa correlation filter/tracing toàn B16.

## Kiểm tra và bằng chứng

Từ `services/payment-service/`, dùng JDK21:

```powershell
rtk proxy .\mvnw.cmd -B -ntp verify
```

Kết quả 28/10/2026: **BUILD SUCCESS**, **21 test**, 0 failure/error/skipped. Enforcer/Spotless/JaCoCo report/SpotBugs qua, SpotBugs 0 finding. Report tại `target/surefire-reports/`, `target/site/jacoco/`; log `target/verify.log`.

| Bộ kiểm | Số test | Nội dung |
|---|---:|---|
| PaymentContractTests | 5 | Parse→validate fixture, missing/unknown/coercion, money/version, cancellation, result/envelope, isHidden |
| PaymentMapperTests | 4 | Mapping, khác order, tiền không rounding/range overflow |
| PaymentSecurityTests | 4 | JWT ký thật/local issuer/JWKS, issuer/expiry/signature/role, health/internal/admin |
| PaymentErrorLoggingTests | 4 | Correlation, status/headers, input/output validation, JSON log/secret redaction |
| PaymentRuntimeConfigurationTests | 3 | YAML, converter/confirms/returns, không business listener/worker |
| PaymentServiceApplicationTests | 1 | Context cô lập, mock repository/JwtDecoder; không chứng minh DB/broker thật |

Persistence IT đã viết và compile nhưng **NOT_RUN**, thiếu env/môi trường PostgreSQL test:

```powershell
# Cấp riêng PAYMENT_TEST_DB_URL, PAYMENT_TEST_DB_USERNAME, PAYMENT_TEST_DB_PASSWORD.
rtk proxy .\mvnw.cmd -B -ntp -Ppayment-persistence-it verify
```

DB test phải là instance cô lập có payment_db/payment_schema và 13 bảng tbl_* từ [DDL chuẩn](../../docs/architecture/data/postgres/30-payment.sql) + [script prefix](../99-table-prefix.sql). Hai script dùng psql/tên DB/role cụ thể; không chạy vào DB chia sẻ để chuẩn bị test. IT không fallback PAYMENT_DB_*, JPA chỉ validate; seed giả trong `src/test/resources/fixtures/payment/persistence.sql` rollback sau mỗi test. Bốn IT kiểm mapping/version, JSONB/khóa ghép, ledger FK/unfinished attempt và nullable cancellation/refund outcome. Chưa có bằng chứng pass trên PostgreSQL.

Smoke runtime cấu hình mới **NOT_RUN**: Config Server/Eureka localhost truy cập được lúc kiểm nhưng Config Server chưa phục vụ PaymentLogFields trong cấu hình Payment. Cần phục vụ YAML mới và cung cấp env/dependency trước khi xác nhận Payment boot/đăng ký Eureka/kết nối DB-broker thật. Chưa restart/deploy hoặc đổi DB/topology live. Health UP/unit context test không thay bằng chứng này.
