# Handoff — User Service

## 1. Mở tunnel khi chạy service từ máy local

```bash
ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:17017:127.0.0.1:27017 ft119
```

Giữ terminal mở. Nếu tunnel MongoDB đã chạy thì dùng lại.
`ft119` là alias SSH; thành viên khác cấu hình
alias tới `ubuntu@3.106.109.119` bằng key của mình.

## 2. YAML kết nối

Gộp block dưới vào [user-service.yaml](../../infrastructure/config-server/src/main/resources/config/user-service.yaml),
không tạo hai key `spring` trong cùng file. Đây là mẫu để tích hợp, chưa được áp dụng vào code/config runtime.

```yaml
spring:
  mongodb:
    uri: ${MONGO_URI}
  rabbitmq:
    host: 3.106.109.119
    port: 5672
    username: flash_ticket_app
    password: ${RABBITMQ_PASSWORD}
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
          jwk-set-uri: http://3.106.109.119:8080/realms/flash-ticket/protocol/openid-connect/certs
          audiences:
            - flash-ticket-api
```

## 3. Điền biến môi trường

Điền trong `.env` gốc, rồi cấu hình IDE/runtime nạp biến vào process User:

```dotenv
CONFIG_SERVER_URL=http://localhost:8888
EUREKA_SERVER_URL=http://localhost:8761/eureka/
MONGO_URI=mongodb://user_admin:DIEN_PASSWORD_DA_URL_ENCODE@127.0.0.1:17017/user_db?authSource=admin&maxPoolSize=5
RABBITMQ_PASSWORD=DIEN_RABBITMQ_PASSWORD_TREN_SERVER
REDIS_PASSWORD=DIEN_REDIS_PASSWORD_TREN_SERVER
```

- Mật khẩu MongoDB là `USER_DB_ADMIN_PASSWORD` trên server; URL-encode trước khi đặt
  vào URI nếu có ký tự đặc biệt. Không commit secret; Spring không tự đọc `.env`.
- `user_admin` hiện là tài khoản bootstrap root. `user_erd` chỉ đọc, dùng `authSource=user_db`.
- Boot **4.1.1** dùng `spring.mongodb.uri`. Collection có tiền tố `tbl_`.
- Chạy Eureka và Config Server trước User; sửa YAML thì build/chạy lại Config Server.
- Redis cần thêm `spring-boot-starter-data-redis`; bỏ block Redis nếu không dùng.
- Listen `user.identity.q` bằng `@RabbitListener(queues = "user.identity.q")`, không tự declare
  topology. Với `manual`, ACK sau khi lưu DB thành công. Publisher identity từ Keycloak chưa được triển khai.
- JWT còn cần `SecurityFilterChain` và mapping `realm_access.roles`.
  Gọi Admin REST dùng client `user-management` và secret từ quản trị;
  chi tiết OAuth client xem link Keycloak bên dưới.

Nguồn: [MongoDB init](database/10-user.js), [collection prefix](database/99-collection-prefix.js),
[topology RabbitMQ](../../deploy/rabbitmq/definitions.json),
[Keycloak/Spring](../../deploy/keycloak/postman/INTERNAL-SERVICE-HANDOFF.md),
[Spring Boot properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html).
