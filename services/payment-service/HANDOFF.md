# Handoff — Payment Service

## 1. Mở tunnel khi chạy service từ máy local

```bash
ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:15432:127.0.0.1:5432 ft119
```

Giữ terminal mở. Nếu tunnel đã chạy thì dùng lại. `ft119` là alias SSH của máy bạn;
thành viên khác cần cấu hình alias riêng tới `ubuntu@3.106.109.119` bằng key của mình.

## 2. YAML kết nối

Gộp block dưới vào [payment-service.yaml](../../infrastructure/config-server/src/main/resources/config/payment-service.yaml),
không tạo hai key `spring` trong cùng file. Đây là mẫu để tích hợp, chưa được áp dụng vào code/config runtime.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:15432/payment_db?currentSchema=payment_schema
    username: payment_admin
    password: ${PAYMENT_DB_PASSWORD}
    hikari:
      connection-init-sql: SET ROLE payment_owner
      maximum-pool-size: 5
      minimum-idle: 1
  jpa:
    hibernate:
      ddl-auto: none
  rabbitmq:
    host: 3.106.109.119
    port: 5672
    username: flash_ticket_app
    password: ${RABBITMQ_PASSWORD}
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      exchange: business.ex
      mandatory: true
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 20
  data:
    redis:
      host: 3.106.109.119
      port: 6379
      password: ${REDIS_PASSWORD}
      connect-timeout: 2s
      timeout: 2s
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://3.106.109.119:8080/realms/flash-ticket
```

## 3. Điền biến môi trường

Điền trong `.env` gốc, rồi cấu hình IDE/runtime nạp biến vào process Payment:

```dotenv
CONFIG_SERVER_URL=http://localhost:8888
EUREKA_SERVER_URL=http://localhost:8761/eureka/
PAYMENT_DB_PASSWORD=DIEN_PAYMENT_DB_ADMIN_PASSWORD_TREN_SERVER
RABBITMQ_PASSWORD=DIEN_RABBITMQ_PASSWORD_TREN_SERVER
REDIS_PASSWORD=DIEN_REDIS_PASSWORD_TREN_SERVER
```

- Lấy mật khẩu từ quản trị/`.env` server; không commit secret. Spring không tự đọc `.env`.
- Chạy Eureka và Config Server trước Payment; sửa YAML thì build/chạy lại Config Server.
- Giữ `SET ROLE payment_owner` vì tài khoản là `NOINHERIT`; bảng dùng tiền tố `tbl_`.
- Redis cần thêm `spring-boot-starter-data-redis`; bỏ block Redis nếu không dùng.
- RabbitMQ không tự declare topology. Queue Payment: `payment.finance-configured.q`,
  `payment.event-cancelled.q`, `payment.start.q`, `payment.decision.q`,
  `payment.ticket-issuance-failed.q`, `payment.order-completed.q`. Với `manual`, code ACK sau commit.
- JWT còn cần `SecurityFilterChain` và mapping `realm_access.roles`.
  Chi tiết OAuth client nội bộ xem link Keycloak bên dưới.
- Thông tin merchant/provider thanh toán cấu hình riêng; callback vẫn phải kiểm chữ ký provider.

Nguồn: [init/quyền DB](../../deploy/postgres/00-business-databases.sh),
[topology RabbitMQ](../../deploy/rabbitmq/definitions.json),
[hợp đồng B13](../../docs/architecture/B13-api-and-event-contracts.md),
[Keycloak/Spring](../../deploy/keycloak/postman/INTERNAL-SERVICE-HANDOFF.md),
[Spring Boot properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html).
