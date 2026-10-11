# Bàn giao Spring — shared internal-service

Ngày 11/10/2026. Đây là **contract và mã giả để triển khai tiếp**; migration hiện tại chỉ sửa Keycloak/Postman, chưa sửa hoặc kiểm thử Spring/Gateway.

## Danh tính và quyền

User/admin login qua `frontend`; bốn service outbound cùng dùng `internal-service`. Cả hai loại token có audience `flash-ticket-api`; human có thể có thêm audience Account/Admin phục vụ Keycloak. Machine token có đúng một audience và đúng realm role `INTERNAL_SERVICE`. Không kiểm tra `azp`, không cần introspection cho mỗi call.

Gateway và mọi receiver phải verify chữ ký/JWKS, issuer, exp/nbf, audience. Chỉ `issuer-uri` chưa thêm application audience validation hoặc chuyển Keycloak realm roles thành authorities. Tài liệu chính thức: [JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html). Cấu hình dưới là mã giả; property audiences chỉ dùng nếu phiên bản Boot thực tế hỗ trợ, nếu không dùng validator tương đương.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI}
          audiences:
            - flash-ticket-api
```

Issuer phải khớp chính xác `iss`; runtime hiện là `http://localhost:8080/realms/flash-ticket`. Khi chạy service trong Docker, localhost là container đó: cần network/hostname truy cập Keycloak đúng mà không vô tình đổi issuer. Có thể cấu hình riêng JWKS URI nội bộ nhưng vẫn validate issuer public. Không tự tắt issuer validation.

## Decoder và realm role converter

```java
// Mã giả: giữ default issuer/time validators khi thêm audience.
decoder = JwtDecoders.fromIssuerLocation(issuer);
decoder.setJwtValidator(allOf(
    JwtValidators.createDefaultWithIssuer(issuer),
    jwt -> jwt.getAudience().contains("flash-ticket-api")
        ? success() : invalidToken("wrong audience")
));

// Tự map realm_access.roles sau khi JWT được verify.
allowed = Set.of("BUYER", "ORGANIZER", "ADMIN", "INTERNAL_SERVICE");
converter(jwt):
    roles = safelyReadStringList(jwt, "realm_access", "roles");
    return roles.stream().filter(allowed::contains)
        .map(role -> authority("ROLE_" + role)).toList();
```

Cấu hình YAML xác thực token; converter + endpoint rules phân quyền. Missing/malformed roles map thành danh sách rỗng, không cấp quyền mặc định. Keycloak Admin REST tự kiểm tra client roles `realm-management`; whitelist trên dành cho API nghiệp vụ của dự án.

## Gateway và Booking/Payment receivers

```java
// Gateway reactive: sử dụng ServerHttpSecurity + reactive converter/decoder.
pathMatchers("/internal/v1/**").denyAll();
pathMatchers("/api/bookings/**").hasRole("BUYER");
// Các API nghiệp vụ khác áp policy cụ thể; phần còn lại denyAll.

// Receiver servlet: cấu hình SecurityFilterChain + JwtAuthenticationConverter.
requestMatchers("/internal/v1/**").hasRole("INTERNAL_SERVICE");
requestMatchers("/api/bookings/**").hasRole("BUYER");
anyRequest().denyAll();

// Booking handler vẫn kiểm tra owner từ sub đã verify.
createBooking(jwt, input):
    buyerId = jwt.getSubject();
    ignoreClientSuppliedBuyerId();
    authorizeOwnership(buyerId, requestedResources);
    return bookingService.create(buyerId, input);
```

Gateway phải chặn cả URL public sau rewrite/route có thể trỏ vào endpoint internal, không chỉ literal prefix trước rewrite. Network không expose trực tiếp internal endpoints ra internet. Receiver luôn verify lại token.

| Token / endpoint | Kỳ vọng sau khi Spring triển khai |
|---|---|
| User BUYER + API đặt ghế + đúng ownership | Cho phép |
| User BUYER + internal endpoint | 403 |
| Machine INTERNAL_SERVICE + internal endpoint | Cho phép |
| Machine INTERNAL_SERVICE + API BUYER | 403 |
| Token thiếu/sai chữ ký, hết hạn, sai issuer/audience | 401 |
| Token hợp lệ nhưng thiếu role/ownership | 403 |
| Gọi internal qua Gateway | Bị chặn theo policy Gateway |

User đặt ghế không cần role INTERNAL_SERVICE hay audience internal-service. Booking nhận user token để xác định buyer; nếu tiếp tục gọi Payment internal, Booking lấy **machine token** riêng, không relay user token. Payload buyerId là dữ liệu nghiệp vụ đã được Booking kiểm tra.

## Outbound từ bốn services

Registration có tên `internal` với clientId `internal-service`, client_credentials và secret local. Dùng [OAuth2AuthorizedClientManager/client credentials](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorization-grants.html); app nền dùng manager/service phù hợp thay vì phụ thuộc request user.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          internal:
            provider: keycloak
            client-id: internal-service
            client-secret: ${INTERNAL_SERVICE_CLIENT_SECRET}
            authorization-grant-type: client_credentials
        provider:
          keycloak:
            token-uri: ${KEYCLOAK_TOKEN_URI}
```

```java
manager = serviceAuthorizedClientManager(
    provider = clientCredentials(),
    store = authorizedClientService
);
authorizedClient = manager.authorize(
    registrationId = "internal",
    principal = stableApplicationPrincipal // ổn định để tái sử dụng token
);
http.post(paymentInternalUrl)
    .bearer(authorizedClient.accessToken)
    .json(validatedPaymentInput);
```

Tái sử dụng access token còn hạn, xin mới khi hết hạn với clock skew; client_credentials không cần refresh token. Không log secret/JWT hoặc để FE lấy machine secret. Một shared identity không phân biệt được caller Booking/Payment; header tên service chỉ phục vụ tracing. Muốn cấp quyền riêng từng caller về sau phải tách identity/cơ chế xác thực khác.

Giữ `user-management` cho automation Keycloak Admin REST. Human admin vẫn có thể dùng token frontend nếu mang đúng realm-management roles. Offline JWT không revoke ngay khi logout/disable; cần TTL phù hợp và policy vận hành.

## Việc cần làm và kiểm thử tiếp

Triển khai security chains/validator/converter, outbound OAuth client, ownership và network boundaries vào Spring sau khi bàn giao. Chạy các case trong bảng trên trên endpoint thật, kiểm tra cả Gateway rewrite và receiver trực tiếp; verify reuse token khi còn hạn và tự xin mới sau expiry. Các case receiver này **chưa được chạy trong migration Keycloak/Postman**.
