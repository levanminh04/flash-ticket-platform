# Keycloak Admin API cho User Service — hướng dẫn task sau

**Chưa tích hợp.** Tất cả dependency/YAML/Java dưới đây là mẫu cho lần triển khai sau,
chưa compile/test riêng, không có bean/dependency/secret tương ứng trong scaffold hiện tại.
Không gọi realm thật trong task scaffold. Realm export/import của repository ghi Keycloak **26.8.0**.

## Hợp đồng cần xác minh

Trước khi gọi Admin API, xác minh `identitySubject` thuộc đúng issuer/realm và ánh xạ tới
Keycloak **user ID**. Không tìm theo username/email rồi mặc định là cùng người;
chỉ dùng JWT `sub` trực tiếp sau khi đã kiểm chứng ID contract của deployment hiện tại.
Realm trong path là **tên realm**. Tra ORGANIZER theo tên để lấy RoleRepresentation/id;
không hard-code role ID. Các endpoint tham chiếu
[Admin REST 26.8.0](https://www.keycloak.org/docs-api/26.8.0/rest-api/index.html):

| Thao tác | Endpoint |
|---|---|
| Lookup realm role | `GET /admin/realms/{realm}/roles/ORGANIZER` |
| Query direct realm roles | `GET /admin/realms/{realm}/users/{user-id}/role-mappings/realm` |
| Query effective roles nếu cần composite | `GET /admin/realms/{realm}/users/{user-id}/role-mappings/realm/composite` |
| Add role (204) | `POST /admin/realms/{realm}/users/{user-id}/role-mappings/realm`, body array RoleRepresentation |

Mẫu dưới dùng direct role mapping; nếu deployment dùng composite phải chốt lại điều kiện confirm.
Chỉ **thêm** ORGANIZER; không replace/remove danh sách hiện tại, giữ BUYER.
Client `user-management` bật service account/client credentials. Xác minh quyền lookup role,
query user roles và map role trên đúng bản Keycloak bằng realm test; không mặc định cấp `realm-admin`.
Không ghi token, client secret, HTTP body lỗi hoặc URI chứa credential vào log.

## Dependency và YAML mẫu — chưa áp dụng

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security-oauth2-client</artifactId>
</dependency>
```

Gộp vào một root `spring` hiện có; secret được cấp ngoài Git, không thêm secret vào `.env.example`.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak-admin:
            provider: keycloak-admin
            client-id: user-management
            client-secret: ${KEYCLOAK_USER_MANAGEMENT_CLIENT_SECRET}
            authorization-grant-type: client_credentials
            client-authentication-method: client_secret_basic
        provider:
          keycloak-admin:
            token-uri: ${KEYCLOAK_TOKEN_URI}
user:
  keycloak-admin:
    base-url: ${KEYCLOAK_ADMIN_BASE_URL}
    realm: flash-ticket
```

`base-url` là origin/context path tin cậy của deployment, không nhận từ request.
Token URI thuộc cùng realm/issuer. Không chuyển tiếp token người dùng sang Admin API.

## Manager và HTTP client mẫu — chưa compile/test riêng

Theo cách Booking cấu hình token client trong
[InternalFeignConfig](../../services/booking-service/src/main/java/com/flashticket/booking_service/client/config/InternalFeignConfig.java),
dùng manager của Spring, lưu/reuse token theo registration và application principal cố định
`user-service`; không tự cache token hoặc gắn với admin đang nhấn approve.
Xem [Spring OAuth2 core](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/core.html).
Imports dùng Spring Security OAuth2 client/endpoint/registration, Spring Web RestClient,
HTTP converters và `java.time.Duration`. Đây là các thành phần của một cấu hình tương lai:

```java
// Mẫu trong @Configuration tương lai; cả token endpoint và Admin API đều có timeout.
private static SimpleClientHttpRequestFactory requestFactory() {
  var factory = new SimpleClientHttpRequestFactory();
  factory.setConnectTimeout(Duration.ofSeconds(2));
  factory.setReadTimeout(Duration.ofSeconds(5));
  return factory;
}

@Bean
OAuth2AuthorizedClientManager adminTokenManager(
    ClientRegistrationRepository registrations, OAuth2AuthorizedClientService clients) {
  var tokenClient = new RestClientClientCredentialsTokenResponseClient();
  tokenClient.setRestClient(RestClient.builder()
      .requestFactory(requestFactory())
      .configureMessageConverters(converters -> {
        converters.addCustomConverter(new FormHttpMessageConverter());
        converters.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter());
      })
      .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
      .build());
  var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
  manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
      .clientCredentials(builder -> builder.accessTokenResponseClient(tokenClient)).build());
  return manager;
}

@Bean
RestClient keycloakAdminRestClient(
    RestClient.Builder builder,
    @Qualifier("adminTokenManager") OAuth2AuthorizedClientManager manager,
    @Value("${user.keycloak-admin.base-url}") String baseUrl) {
  return builder.baseUrl(baseUrl).requestFactory(requestFactory())
      .requestInterceptor((request, body, execution) -> {
        var client = manager.authorize(OAuth2AuthorizeRequest
            .withClientRegistrationId("keycloak-admin").principal("user-service").build());
        if (client == null) throw new IllegalStateException("Admin token unavailable");
        request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
        return execution.execute(request, body);
      }).build();
}
```

Manager lấy token mới khi hết hạn; client_credentials không dựa vào refresh token của người dùng.
Khi API trả 401 phải evict authorized client của `keycloak-admin`/`user-service` qua
`OAuth2AuthorizedClientService.removeAuthorizedClient(...)`, sau đó query lại với token mới;
giới hạn retry, không tự lặp POST khi kết quả ghi còn chưa rõ. Không coi 403 là thành công.

## Adapter mẫu — chưa compile/test riêng

`http` inject bean `keycloakAdminRestClient`; `realm` lấy từ cấu hình tin cậy.
`verifiedKeycloakUserId` là kết quả ánh xạ đã xác minh ở đầu guide.
Role payload là kiểu riêng cho Keycloak, không nới strict JSON input của ứng dụng.

```java
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
record RoleRepresentation(String id, String name) {}

final class KeycloakOrganizerRoles {
  private final RestClient http;
  private final String realm;

  KeycloakOrganizerRoles(RestClient http, String realm) {
    this.http = http;
    this.realm = realm;
  }

  boolean hasOrganizer(String verifiedKeycloakUserId) {
    var roles = http.get()
        .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm",
            realm, verifiedKeycloakUserId)
        .retrieve().body(RoleRepresentation[].class);
    if (roles == null) throw new IllegalStateException("Missing role response");
    return Arrays.stream(roles).anyMatch(role -> "ORGANIZER".equals(role.name()));
  }

  boolean addAndConfirmOrganizer(String verifiedKeycloakUserId) {
    if (hasOrganizer(verifiedKeycloakUserId)) return true;
    var role = http.get().uri("/admin/realms/{realm}/roles/{role}", realm, "ORGANIZER")
        .retrieve().body(RoleRepresentation.class);
    if (role == null || role.id() == null || !"ORGANIZER".equals(role.name())) {
      throw new IllegalStateException("Invalid ORGANIZER representation");
    }
    http.post().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm",
            realm, verifiedKeycloakUserId)
        .contentType(MediaType.APPLICATION_JSON).body(List.of(role))
        .retrieve().toBodilessEntity();
    return hasOrganizer(verifiedKeycloakUserId);
  }
}
```

Đây là adapter minh họa, chưa phải approval service. REST error/timeout đi lên coordinator
để quyết định trạng thái; không log raw RestClient exception/body. Nếu response confirm sai,
thiếu hoặc timeout thì kết quả còn unknown, không chuyển ACTIVE.

## B13: trạng thái và recovery cần triển khai

1. Atomic compare-and-set application `PENDING`, version mong đợi và **chưa có roleGrant**:
   lưu `roleGrant.status=REQUESTED`, `operationId`, `attemptedAt`; tăng version.
   Chỉ một admin thắng; reject dùng cùng điều kiện để không chen vào grant đang dở.
2. Gọi Keycloak ngoài atomic Mongo update. Nếu request timeout, side effect có thể đã xảy ra:
   giữ REQUESTED và query role khi retry/recover; không reset để cho reject thắng.
3. Query xác nhận ORGANIZER. CAS theo operationId/version/status để lưu CONFIRMED,
   confirmedAt và chuyển application ACTIVE cùng decidedBySubject/decidedAt hợp lệ trong một update.
   403/404/5xx hoặc query timeout không chứng minh đã grant.
4. Sau authoritative application ACTIVE, upsert projection `tbl_organizer_profiles` theo
   sourceApplicationId/sourceVersion. Guard bằng sourceVersion để retry cũ không ghi đè bản mới;
   projection thất bại thì retry/rebuild từ application, không gọi grant lại mù quáng.
5. Không dùng Mongo multi-document transaction trên standalone, không tạo saga/framework mới.
   Retry/recovery worker cụ thể và ownership phải được thiết kế/test ở task B13.
6. Access token đã phát hành không tự đổi role. Sau khi grant thành công, client refresh
   token hoặc đăng nhập lại rồi kiểm token mới có ORGANIZER và vẫn giữ BUYER theo
   [B13 §9](../architecture/B13-api-and-event-contracts.md). User Service không sửa token cũ
   hoặc giả role ở response để vượt JWT validation.

Test ở lần tích hợp: duplicate grant giữ BUYER; timeout sau side effect rồi query confirm;
concurrent approve/approve và approve/reject; role lookup/query bị 403/404; expired token;
projection lỗi rồi rebuild; retry sourceVersion cũ; refresh/login lại của người dùng sau grant
và kiểm token mới giữ BUYER, thêm ORGANIZER. Dùng realm test hoặc HTTP stub trước khi
smoke trên realm thật. Không coi 204 của POST là bằng chứng đủ để ACTIVE.
