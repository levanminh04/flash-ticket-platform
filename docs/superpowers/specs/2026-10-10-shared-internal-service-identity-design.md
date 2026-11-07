# Spec: dùng chung client internal-service cho FlashTicket

- Ngày: 2026-10-10.
- Trạng thái: REVIEW_READY; chưa được duyệt spec hoặc triển khai.
- Phân loại: thay đổi kiến trúc danh tính, theo skill `superpowers:brainstorming`.
- Đầu vào: yêu cầu trong cuộc hội thoại hiện tại; working tree tại HEAD `53f8711` có các chỉnh sửa chưa commit.
- Phạm vi người dùng xác nhận: **Keycloak + Postman; mô tả Spring để bàn giao**.
- Quyết định người dùng: thay bốn client S2S Booking, Payment, Event, Ticket bằng **một client `internal-service`**.
- Đính chính đã xác nhận: token S2S chỉ có audience `flash-ticket-api`; bỏ own-token introspection và không kiểm `azp`.

## 1. Mục tiêu

Booking, Payment, Event và Ticket lấy access token bằng cùng client ID `internal-service` và cùng secret. Token dùng cho các request HTTP nội bộ giữa các service FlashTicket. Người dùng và admin vẫn đăng nhập qua `frontend`; `user-management` tiếp tục là credential backend quản lý user trong Keycloak.

Kết quả nghiệm thu của task là cấu hình Keycloak hoạt động, cấu hình import bền vững, bộ Postman và environment đồng bộ, cùng bằng chứng kiểm thử Keycloak. Task **không nghiệm thu việc xác thực/phân quyền ở service Spring chưa triển khai**.

## 2. Phạm vi và các quyết định đã chốt

### 2.1 Trong phạm vi triển khai sau khi spec và plan được duyệt

- Cập nhật realm `flash-ticket` đang chạy và `deploy/keycloak/import/flash-ticket-realm.json`.
- Tạo realm role `INTERNAL_SERVICE`, client `internal-service`, role scope mappings và audience mapper cần thiết.
- Chuyển bộ Postman chính, environment và bộ seed hiện có sang một danh tính S2S.
- Chuyển tham chiếu cấu hình cục bộ liên quan đến credential của bốn client cũ sang client mới; cập nhật tài liệu sử dụng.
- Loại bốn client cũ và các environment S2S cũ khỏi bộ cấu hình đang sử dụng sau khi client mới vượt qua kiểm thử Keycloak/Postman.
- Viết hợp đồng bàn giao Spring trong tài liệu; không sửa mã hoặc cấu hình Spring trong task này.

### 2.2 Ngoài phạm vi

- Code security, OAuth2 outbound client, controller hoặc endpoint nghiệp vụ trong Gateway và các service Spring.
- Tạo frontend/BFF, API quản trị của User Service, Saga, message broker hoặc sửa hợp đồng nghiệp vụ B13/B14.
- Reset PostgreSQL, xóa realm, xóa user hoặc thay credential đăng nhập của user.
- Đổi SMTP, theme, chính sách mật khẩu, đăng ký, session hoặc quyền admin hiện có.
- Cấp mặc định `INTERNAL_SERVICE` cho human user hoặc quyền quản trị Keycloak cho client S2S.
- Gửi request tới endpoint nghiệp vụ chưa có rồi coi là kiểm thử S2S thành công.

### 2.3 Phương án và đánh đổi

| Phương án | Đặc điểm | Quyết định |
| --- | --- | --- |
| Một client `internal-service` dùng chung | Một credential, một danh tính S2S, ít environment; không phân biệt service gọi bằng token | Người dùng đã chọn |
| Client riêng cho từng service | Secret và quyền độc lập, nhận diện caller qua `azp`; nhiều cấu hình hơn | Không triển khai trong task này |

Chọn JWT Resource Server làm hợp đồng nhận token của Spring; audience API chung là `flash-ticket-api`. Role `INTERNAL_SERVICE` phân biệt nhóm caller nội bộ với human user. Theo đính chính của người dùng, không dùng claim `azp` làm điều kiện xác thực hoặc phân quyền, kể cả `azp == internal-service`.

## 3. Bằng chứng hiện trạng

Những quan sát dưới đây đến từ file trong working tree, chưa phải bản đối chiếu runtime mới:

| Thành phần | Hiện trạng |
| --- | --- |
| Realm import | Có `ADMIN`, `BUYER`, `ORGANIZER`; chưa có `INTERNAL_SERVICE` |
| Bốn machine client | Bật service account, không bật Standard Flow; mỗi client có mapper audience trỏ về chính nó |
| `frontend` | Public client, bật Standard Flow; chưa có mapper trực tiếp thêm `flash-ticket-api` |
| `user-management` | Service account có `realm-management/manage-users`, `query-users`, `view-users` |
| Default role composite | Có `BUYER` và quyền account; cần tránh để machine account nhận quyền human từ composite này |
| Postman chính | Có bốn folder và bốn environment riêng Booking/Payment/Event/Ticket; token save và own-token introspection riêng |
| Bộ seed | Collection, environment và runner còn tham chiếu bốn machine identity; có dữ liệu user test đã tạo |
| Gateway | Verify JWT với issuer/JWK; converter whitelist `BUYER`, `ORGANIZER`, `ADMIN`; chặn `/internal/v1/**` |
| Service nghiệp vụ | Mỗi service hiện chỉ có lớp khởi động; chưa có business endpoint hoặc security policy để nghiệm thu HTTP S2S |

Nguồn chính: realm import; `deploy/keycloak/postman/`; `infrastructure/config-server/src/main/resources/config/api-gateway.yaml`; `GatewaySecurityConfiguration.java`; `KeycloakRealmRoleConverter.java`; B13 §2 và các đường HTTP nội bộ đã mô tả.

Các file này đang có thay đổi chưa commit. Khi triển khai phải giữ nguyên chỉnh sửa không thuộc task và không dùng checkout/reset để thay cả file.

## 4. Cấu hình Keycloak đích

### 4.1 Client registry

| Client | Vai trò | Flow và credential |
| --- | --- | --- |
| `frontend` | Human login cho user/admin | Public, Authorization Code + PKCE S256; không có client secret |
| `internal-service` | Danh tính dùng chung cho bốn service S2S | Confidential, client-secret, Client Credentials, bật service account; tắt Standard Flow, Direct Access Grants và Implicit Flow |
| `user-management` | Backend gọi Keycloak Admin API | Giữ service account và các quyền quản lý user hiện có |

Không xóa các client built-in như `account`, `account-console`, `realm-management` hoặc `security-admin-console`. Không tạo client `internal-service` như một microservice chạy riêng: đây chỉ là OAuth2 client trong Keycloak.

### 4.2 Role và role scope mappings

- Tạo realm role `INTERNAL_SERVICE`, không composite với `BUYER`, `ORGANIZER`, `ADMIN` hoặc quyền `realm-management`.
- Gán role này cho `service-account-internal-service`.
- Loại role assignment của default human composite và các human/admin role khỏi service account này; kiểm cả quyền kế thừa, không chỉ role trực tiếp.
- `internal-service.fullScopeAllowed=false`; role scope mappings chỉ cho phép realm role `INTERNAL_SERVICE` trong nhóm role ứng dụng.
- Giữ mapper đưa realm roles vào `realm_access.roles` của access token, thông qua scope `roles` hiện có hoặc mapper tương đương.
- Token nội bộ không chứa `BUYER`, `ORGANIZER`, `ADMIN`, quyền account self-service hoặc quyền `realm-management`.
- Không gán `INTERNAL_SERVICE` cho user/admin hoặc đưa vào default roles của realm. Nếu runtime đã có role cùng tên, rà assignments trước khi dùng lại, không xóa role assignment ngoài dự kiến một cách tự động.
- Không đổi quyền `user-management` hoặc user admin trong task này.

Việc gán role cho service account và việc cho role đi vào token là hai điều kiện riêng; nghiệm thu phải kiểm payload của token mới, không chỉ nhìn tab Service account roles.

### 4.3 Audience

Tạo client scope OIDC `flash-ticket-api` với Audience mapper:

| Thuộc tính | Giá trị |
| --- | --- |
| Mapper type | Audience (`oidc-audience-mapper`) |
| Included Custom Audience | `flash-ticket-api` |
| Add to access token | true |
| Add to ID token | false |

Gắn scope này như default client scope cho `frontend` và `internal-service`. Client scope này không tự cấp role. Không thay mapper audience hiện có phục vụ Account API hoặc Admin API.

Token nội bộ chỉ có audience `flash-ticket-api`. Không tạo mapper own-client audience `internal-service`; bỏ chức năng own-token introspection khỏi bộ Postman S2S và bộ seed đích. Spring nhận token bằng JWT Resource Server, không gọi introspection cho từng request.

- `flash-ticket-api` phục vụ JWT validation của API FlashTicket trong hợp đồng Spring.
- Không thêm `internal-service`, `realm-management` hoặc audience của bốn client đã bỏ vào token nội bộ.
- Yêu cầu một audience duy nhất áp dụng cho token S2S; token human vẫn giữ audience phục vụ Account API hoặc Admin API hiện có ngoài audience FlashTicket.
- Audience là tập giá trị; kiểm thử không phụ thuộc thứ tự hoặc giả định `aud` luôn được serialize thành array khi chỉ có một phần tử.

### 4.4 Payload kỳ vọng

Payload dưới đây minh họa token nội bộ, không phải token thật. `azp` là metadata Keycloak thường phát hành; không dùng claim này làm điều kiện cho request hoặc token-saving script. Các claim cần kiểm là issuer, thời hạn, audience và role theo ngữ cảnh kiểm thử.

```json
{
  "iss": "<issuer của realm flash-ticket>",
  "azp": "internal-service",
  "sub": "<ID service account internal-service>",
  "aud": ["flash-ticket-api"],
  "realm_access": {
    "roles": ["INTERNAL_SERVICE"]
  }
}
```

Token phải có thời hạn và chữ ký hợp lệ. `sub` là danh tính máy; không dùng nó như buyer ID. Header tự khai `X-Service-Name` hoặc field trong body không chứng minh service nào đã gọi.

### 4.5 Credential

- Một secret dùng chung cho bốn service; không gửi secret cho frontend.
- Runtime/local environment nhận secret thực; JSON import dùng placeholder `${INTERNAL_SERVICE_CLIENT_SECRET}` và cấu hình Compose truyền biến này theo cơ chế placeholder đang dùng cho SMTP.
- Không thêm giá trị secret thực vào example environment, spec, log hoặc verification report; file dotenv chứa secret tiếp tục nằm ngoài Git.
- Secret được rotate cho cả nhóm khi cần; không hứa có thể thu hồi riêng một trong bốn service với thiết kế này.

## 5. Postman đích

### 5.1 Folder và environment

Giữ collection chính `FlashTicket - User Admin Services` và các folder Discovery, Client - User, Admin - User Management. Gom bốn folder S2S thành `03 - Internal Service`:

1. `01 - Get Internal Service Token`.
2. `S2S theo B13 - Chưa triển khai`, phân nhóm theo **service nhận** và giữ method/body/path của các contract hiện có.
3. Các request tùy chỉnh đã có, nếu còn giữ, nằm trong nhóm `S2S tùy chỉnh - Chưa triển khai`; không nhân bản lại theo bốn danh tính caller.

Loại các request/folder `Diagnostic - Introspect own token` của bốn client cũ, không tạo request thay thế cho `internal-service`.

Environment mới: `FlashTicket - Internal`; export example: `Internal.example.postman_environment.json`. Dùng `profile=internal`, `clientId=internal-service`, `clientSecret`, `accessToken`, `tokenExpiresAt`, `lastRequestError`, `keycloakBaseUrl`, `realm`, các base URL/path và ID nghiệp vụ cần cho receiver tương ứng. Không có biến `introspectionActive` hoặc guard yêu cầu introspection. Giữ cờ `enableServiceTemplates=false` làm mặc định.

Giữ User, Admin và Seed environment. Sau khi đọc lại cấu hình mới và kiểm thử thành công, loại Booking/Payment/Event/Ticket environment khỏi bộ export và workspace đang sử dụng. Cần snapshot cục bộ để phục hồi trước khi loại; snapshot có credential phải nằm ngoài Git. Giữ collection ID và request ID còn sử dụng khi công cụ hỗ trợ.

### 5.2 Request token và scripts

`POST {{keycloakBaseUrl}}/realms/{{realm}}/protocol/openid-connect/token`, Authorization = No Auth, body `x-www-form-urlencoded` có đủ:

```text
grant_type=client_credentials
client_id={{clientId}}
client_secret={{clientSecret}}
```

- Guard yêu cầu chọn đúng environment, `profile=internal`, `clientId=internal-service`, base URL/realm/secret hợp lệ.
- Bắt đầu lần xin token mới phải clear `accessToken`, `tokenExpiresAt` để không giữ kết quả cũ khi request thất bại.
- Chỉ lưu token vào environment hiện chọn khi response 200, có access token và các claim bắt buộc đúng. Không dùng collection/global variables để lưu token.
- Lưu thời điểm hết hạn từ `exp`; không coi thao tác decode payload trong Postman là kiểm chữ ký JWT.
- Token response lỗi phải giữ HTTP status/body thực, ghi lỗi ngắn vào `lastRequestError`, không in secret hoặc token.
- Guard bỏ qua request do thiếu điều kiện phải có thông báo rõ ở Test Results/Console/`lastRequestError`; không giả tạo HTTP status cho request chưa gửi.
- Các request S2S dùng Bearer `{{accessToken}}`; không dùng user token hoặc admin token. Postman không tự xin token trong pre-request script của từng business request.
- Không gọi token introspection, không lưu hoặc kiểm `active`/`introspectionActive` như điều kiện để gửi S2S request. Postman kiểm claim để chẩn đoán; bước verify JWT thực tế tại receiver thuộc hợp đồng Spring.
- Không sửa flow native OAuth2 PKCE, cách lưu token user/admin hoặc Account API hiện có ngoài điều chỉnh audience cần thiết.

### 5.3 Bộ seed và export

- Đổi bốn bộ `bookingClient*`, `paymentClient*`, `eventClient*`, `ticketClient*` trong seed environment thành `internalClientId`, `internalClientSecret`, `internalToken`.
- Bộ seed collection/runner lấy một token nội bộ và kiểm issuer, thời hạn, role, audience đúng thiết kế; bỏ các request/assertion own-token introspection, biến `introspectionActive` và các ca chỉ phục vụ bốn machine identity đã thay.
- Giữ các case human user, admin, profile, role assignment và các user test đã tồn tại. Không chạy lại lifecycle xóa user để chứng minh migration.
- Cập nhật README chính, SEED-README và file example. Không sửa report lịch sử thành kết quả của lần chạy mới; tạo report migration mới có thời điểm, trạng thái và bằng chứng riêng.
- Đọc lại collection từ Postman sau đồng bộ và so sánh method, URL, body, auth, scripts, profile guard với bản local export; không chỉ dựa vào thông báo import thành công.

## 6. Hợp đồng bàn giao Spring — không triển khai trong task này

### 6.1 Inbound authentication và authorization

Gateway và các receiver cần verify chữ ký, `exp`/`nbf`, issuer và audience `flash-ticket-api`. Giữ một issuer có thể truy cập đúng từ môi trường chạy và khớp `iss`; không nhầm URL container với issuer khác.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI}
          audiences: flash-ticket-api
```

Các endpoint `/internal/v1/**` tại receiver phải yêu cầu đồng thời:

1. JWT hợp lệ theo decoder của Spring.
2. Realm role `INTERNAL_SERVICE`, chuyển thành `ROLE_INTERNAL_SERVICE`.

Không kiểm `azp`. Audience bắt buộc duy nhất tại Spring receiver là `flash-ticket-api`; không yêu cầu audience `internal-service`, `booking-service` hoặc `payment-service`. Gateway tiếp tục deny route nội bộ; receiver tự xác thực JWT khi được gọi trực tiếp.

Một ứng dụng như Booking có cả API human `/api/v1/**` và API S2S `/internal/v1/**`; tên service không biến mọi endpoint thành API nội bộ. Ví dụ user gọi API đặt ghế/đặt đơn qua Gateway: Gateway và Booking đều kiểm audience `flash-ticket-api`; Booking kiểm role `BUYER` cùng ownership/điều kiện nghiệp vụ, không yêu cầu audience `internal-service` hoặc role `INTERNAL_SERVICE`. Chỉ endpoint S2S yêu cầu role nội bộ. Token user thiếu audience `internal-service` không phải lý do bị chặn tại API đặt ghế/đặt đơn dành cho user.

API human tiếp tục kiểm business role và ownership theo contract. Token nội bộ không tự có quyền trên API human chỉ vì cùng issuer/audience. Endpoint chỉ dùng `.authenticated()` vẫn có thể nhận machine token hợp lệ; khi triển khai Spring phải phân loại rõ endpoint human và internal, không coi `.authenticated()` là đủ cho mọi API.

Role converter receiver đọc `realm_access.roles`, whitelist các application role cần dùng gồm `INTERNAL_SERVICE`. Role `realm-management` dành cho Keycloak, không chuyển thành quyền nghiệp vụ Spring. Không tin `buyerSubject` do browser gửi; command nội bộ phải theo dữ liệu/ownership/idempotency của contract nghiệp vụ.

### 6.2 Outbound token

Bốn service dùng cùng OAuth2 client registration `internal`, client ID `internal-service`, secret lấy từ môi trường, grant `client_credentials`. Dùng `OAuth2AuthorizedClientManager` và OAuth2 interceptor của HTTP client để lấy/tái sử dụng token còn hạn và xin token mới khi hết hạn. Client Credentials thông thường không dùng refresh token.

Không relay token human vào endpoint yêu cầu `INTERNAL_SERVICE`. HTTP call nội bộ và broker message là hai kênh khác nhau; spec này không thiết kế credential/ACL cho RabbitMQ.

User Service khi triển khai quản lý user giữ credential `user-management` để gọi Keycloak Admin API; không thay bằng `internal-service`. Không triển khai API User Service hoặc thay quyền quản trị user trong task hiện tại.

### 6.3 Giới hạn tin cậy

Một service giữ secret chung có thể lấy token với toàn bộ quyền nhóm nội bộ. Không thể enforce rule “chỉ Payment được gọi endpoint này” bằng token hiện tại. Nếu cần điều kiện đó sau này, phải đổi thiết kế danh tính/credential; không khắc phục bằng header tự khai tên service.

Offline JWT verification không đảm bảo thu hồi token tức thời khi xóa client, logout hoặc rotate secret. Migration không được hứa mọi token cũ lập tức mất hiệu lực tại mọi receiver. Token cũ thiếu audience `flash-ticket-api` hoặc role `INTERNAL_SERVICE` sẽ bị từ chối ở endpoint nội bộ theo hợp đồng mới; không dựa vào `azp` để loại token. Endpoint Spring chưa sửa vẫn cần được báo là chưa đạt.

## 7. Migration và phục hồi

Trình tự là yêu cầu thiết kế; implementation plan sẽ cụ thể hóa thao tác và công cụ:

1. Đọc runtime và file import, xác định chênh lệch; snapshot realm/client mappings và Postman/env trước thay đổi. Giữ user, role mapping, SMTP và các chỉnh sửa đang có.
2. Tạo/cập nhật idempotently role, client, scope, mapper mới; cấp secret qua kênh local. Chưa loại client cũ ở bước này.
3. Kiểm token/claims/role leakage/audience duy nhất của client mới và login user/admin. Không chạy own-token introspection, không dùng token cũ cho các phép kiểm sau thay mapper.
4. Chuyển Postman chính, seed assets và credential config local sang identity mới; đọc lại và kiểm export.
5. Sau khi các kiểm thử Keycloak/Postman đạt, loại bốn client cũ khỏi runtime/import và các environment đang sử dụng. Xóa service account thuộc client cũ là hệ quả migration; không xóa human user hoặc `user-management`.
6. Xuất JSON/env sạch, ghi report và bàn giao trạng thái Spring chưa triển khai.

Startup `--import-realm` không cập nhật realm đã tồn tại. Cần cập nhật runtime bằng Admin API/Console và cập nhật import riêng; không reset DB để ép import. Nếu realm tạm phục vụ kiểm import mới, không thay DB/volume của realm đang dùng và phải ghi rõ đây là kiểm import độc lập.

Nếu kiểm client mới thất bại, giữ client cũ và phục hồi Postman/config từ snapshot; không hoàn tất cutover. Nếu cần phục hồi sau khi đã loại client cũ, dùng snapshot ngoài Git để khôi phục client/role/scope/credential cần thiết và lấy token mới. Rollback không phục hồi hoặc thu hồi được mọi JWT đã phát hành; báo giới hạn này rõ ràng.

## 8. Tiêu chí nghiệm thu

| ID | Phép kiểm | Kết quả bắt buộc |
| --- | --- | --- |
| KC-01 | Registry sau cutover | Có `internal-service`; không còn bốn custom client cũ; frontend, user-management, built-in client và human users còn nguyên |
| KC-02 | Xin token bằng credential internal đúng | HTTP 200, đúng issuer/role/audience; thời hạn hợp lệ; không có điều kiện kiểm `azp` |
| KC-03 | Xin token bằng secret sai | Lỗi client authentication, không lưu hoặc dùng token cũ |
| KC-04 | Audience token S2S mới | Chuẩn hóa `aud` thành tập giá trị và xác nhận đúng `{flash-ticket-api}`; không có audience own-client hoặc audience service cũ |
| KC-05 | Quyền machine | Không có business role human, account self-service hoặc realm-management trong quyền hiệu lực và token |
| KC-06 | Login user/admin bằng OAuth2 PKCE | Tiếp tục hoạt động; token mới có audience API chung, không có INTERNAL_SERVICE |
| KC-07 | API Keycloak hiện có | UserInfo/self-profile và admin read/role assignment còn dùng được theo quyền hiện tại; kiểm write trên user seed chuyên dụng, không sửa user thật |
| KC-08 | Bền vững | Import JSON hợp lệ, placeholder secret được cấp qua config local/Compose, scope/mappers khớp runtime; kiểm fresh import độc lập nếu có môi trường test |
| PM-01 | Collection/env chính và seed | Một identity S2S; body token đầy đủ; script lưu environment đúng; không còn tham chiếu hoạt động tới bốn client cũ, request own-token introspection hoặc biến/guard introspection |
| PM-02 | Guard và token failure | Thiếu/sai environment, thiếu secret, token hết hạn đều có chẩn đoán rõ; token request lỗi không để lại token cũ |
| PM-03 | Đồng bộ/export | Auth/body/scripts đọc lại khớp local; example trống secret/token; user/admin environment không bị ghi đè token bằng token nội bộ |
| PM-04 | Phạm vi kết quả | Business template chưa triển khai vẫn bị guard và được ghi là chưa kiểm end-to-end; không tính skip thành pass nghiệp vụ |

Các ca receiver JWT sai audience → 401, token human gọi internal → 403, token internal đúng → qua authentication/authorization, và token machine gọi API human → 403 là **tiêu chí bàn giao cho task Spring sau này**, không phải kết quả có thể xác nhận chỉ bằng Keycloak/Postman trong task này. Bổ sung ca human token có `flash-ticket-api`, không có `internal-service`, gọi API đặt ghế/đặt đơn với quyền BUYER và dữ liệu hợp lệ: phải qua authentication/authorization ở Gateway và Booking; kết quả nghiệp vụ vẫn phụ thuộc trạng thái ghế/đơn. Khi kiểm receiver, phải xác nhận cả side effect: request bị chặn không thực hiện nghiệp vụ.

## 9. Deliverables

- Realm runtime/import dùng một client S2S chung và giữ dữ liệu human hiện có.
- Export collection chính, Internal example environment, User/Admin example còn dùng và seed assets đã cập nhật.
- Cấu hình local/Compose liên quan đến secret mới và hướng dẫn cấp credential, không chứa giá trị secret trong Git.
- README migration, hướng dẫn chọn env/lấy token và hợp đồng Spring tại §6.
- Report đã làm sạch credential: test nào thực sự chạy, HTTP/assertion nào đạt/thất bại, template nào chưa triển khai, kết quả đối chiếu runtime/import/export. Report cũ giữ đúng thời điểm và phạm vi lịch sử.

Spec này thay lựa chọn **bốn machine client** trong các tài liệu Keycloak/Postman trước đó đối với task migration này; không tự thay trạng thái phê duyệt B12/B13, quyết định nghiệp vụ hoặc phạm vi ngoài §2. Kết thúc viết spec phải có user review; chưa chuyển sang implementation chỉ vì đã chốt ý tưởng client chung.

## 10. Tài liệu tham chiếu

- [Repo tham khảo tại commit đã đọc](https://github.com/mohamad-awwaad/Enterprise-Spring-Microservices-Template/tree/8f5cb6873691f8b67353c4924fb06f469c2ef4e4).
- [Keycloak: service account và role scope mappings](https://www.keycloak.org/docs/latest/server_admin/index.html#_service_accounts).
- [Keycloak: audience](https://www.keycloak.org/docs/latest/server_admin/index.html#_audience).
- [Spring Security: JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
- [Spec danh tính trước đó](2026-10-07-keycloak-identity-configuration-design.md).
- [Plan ổn định Keycloak/Postman hiện có](../plans/2026-10-10-keycloak-postman-stability.md).
- [Hợp đồng B13](../../architecture/B13-api-and-event-contracts.md).



## Trạng thái triển khai 11/10/2026

Keycloak + Postman đã triển khai inline, kiểm chứng và review; các test/runner/report JSON tạm đã cleanup theo plan Task 6. Bằng chứng và notRun lưu ở deploy/keycloak/postman/MIGRATION-RESULTS.md. Spring chưa triển khai; đọc INTERNAL-SERVICE-HANDOFF.md.
