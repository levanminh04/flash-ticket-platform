# Spec triển khai cấu hình danh tính Keycloak cho FlashTicket

- Phiên bản: `KC-ID-v0.2`, ngày 2026-10-07; thu hẹp theo GOV-155.
- Trạng thái: `DRAFT`; người duyệt/ngày duyệt: chưa có.
- Phân lớp: `FORMATION`; thiết kế cấu hình trên kiến trúc hiện có, không sinh service hoặc Saga.
- Skill: `superpowers:brainstorming`, nhánh Architectural.
- Đầu vào: `deploy/` ở HEAD `53f8711`; B8-v0.13 APPROVED; B12-v0.2 APPROVED; B13-v0.2 REVIEW_READY, chỉ giữ hiệu lực đã khai của từng phần; GOV-152/153 và câu trả lời người dùng ngày 07/10.
- Quyết định kỹ thuật trong spec là `CANDIDATE`. Yêu cầu lập spec không tự phê duyệt cấu hình, implementation plan hoặc việc sửa realm đang chạy.

## 1. Mục đích và tiêu chí thành công

Người dùng yêu cầu cấu hình đủ cho bốn việc: đăng nhập; tự quản lý tài khoản; admin quản lý user; service gọi nhau. Người dùng chọn nguyên văn **“Giao diện FlashTicket gọi User Service”** cho kênh admin quản lý user (`GOV-154`).

**Phạm vi hiện tại do người dùng xác nhận:** chỉ cấu hình ở Keycloak, chưa code hoặc sửa các service FlashTicket (`GOV-155`).

Kết quả cần đạt là realm/client/role/scope/audience được cấu hình và kiểm tại Keycloak: human đăng ký/login và dùng Account Console; client quản lý user có token/quyền Admin API phù hợp; machine client được cấp token có danh tính, audience và quyền đã dự kiến. Giữ dữ liệu realm hiện có và không đưa credential vào Git hoặc output.

Kênh admin qua giao diện FlashTicket là đích tích hợp đã chọn, không phải sản phẩm phải viết trong task này. Nghiệm thu hiện tại dừng tại Keycloak; chưa nghiệm thu UI admin, Gateway, User Service hoặc API nội bộ của các service. Những phụ thuộc này chỉ được bàn giao ở §7.

Trong phạm vi: thao tác cấu hình bằng Admin Console/Admin API, kiểm login/account/token/Admin API của chính Keycloak. Ngoài phạm vi: code/provider/SPI mới, script/helper triển khai mới trong repo, frontend, Gateway/Config Server, mọi service FlashTicket, RabbitMQ, DB/Compose/host networking, và thay B12/B13/fixture thiết kế. Có thể dùng công cụ sẵn có để gọi Admin API, không xây subsystem provisioning trong lần này.

## 2. Bằng chứng và ràng buộc

| Quan sát hoặc yêu cầu | State | Nguồn |
| --- | --- | --- |
| Keycloak 26.8.0, `start --import-realm`, cổng host `127.0.0.1:8080` | FACT | `deploy/docker-compose.keycloak.yml:3–18` |
| PostgreSQL riêng cho Keycloak: keycloak_db, role keycloak_app, schema keycloak | FACT | Compose và `deploy/postgres/init/10-keycloak.sh` |
| Realm flash-ticket bật registration/reset password/verifyEmail; frontend public Code + PKCE S256 | FACT | `deploy/keycloak/import/flash-ticket-realm.json:32–40,951–1025` |
| Default composite chứa BUYER; hai machine account kế thừa composite này | FACT | JSON:82–89,505–570 |
| user-management-client có realm-admin cùng nhiều quyền quản trị ngoài user | FACT | JSON:539–566 |
| Default composite chưa cấp account.manage-account; account-console có scope mapping nhưng scope không cấp role | FACT | JSON:82–89,572–580 |
| JSON chứa client secrets, SMTP password và cấu hình khóa; không sao chép vào spec/template mới | FACT | Kiểm cấu trúc JSON, không in giá trị credential |
| Startup import bỏ qua realm đã tồn tại; JSON chưa chứa người quản trị thường trực | FACT | `deploy/Note.md:80,93` |
| Gateway kiểm issuer/JWK/audience và một số business roles; chặn /internal/v1/**; chưa có admin matcher | FACT | Config Server api-gateway.yaml; GatewaySecurityConfiguration.java |
| User Service mới có lớp khởi động, chưa có API/security cho quản lý user | FACT | `services/user-service/src/main/java/` và config user-service.yaml |
| Admin thao tác qua giao diện FlashTicket gọi User Service | USER_CONFIRMED | Câu trả lời người dùng ngày 07/10; GOV-154 |
| Phạm vi hiện tại chỉ ở Keycloak; chưa code hoặc sửa các service FlashTicket | USER_CONFIRMED | Đính chính phạm vi ngày 07/10; GOV-155 |
| Người đăng ký nhận BUYER; cấp ORGANIZER phải giữ BUYER | DECIDED | FR-49, FR-52/53 trong B8-v0.13 APPROVED |
| Người mua không bị bắt xác minh email/SĐT; không social login | DECIDED | FR-50, B12 §6 |
| Giữ Verify email cho khôi phục admin master local; Gmail App Password cho SMTP local | USER_CONFIRMED | GOV-152/153; không suy thành chính sách mọi realm |

**Sai khác phải xử lý:** JSON deploy đang `verifyEmail=true`, khác FR-50 cho sản phẩm. Đích của spec là `flash-ticket.verifyEmail=false`; không thay `master` hoặc tự đánh dấu emailVerified cho user. Đây là phục hồi yêu cầu đã duyệt, không thêm chính sách bắt xác minh. Reset password vẫn xác nhận quyền sở hữu qua link gửi email.

Không dùng IP/port AWS từ ghi chú cũ làm endpoint hiện hành. Spec không thay số service, placement, database nghiệp vụ, tiền/giữ chỗ/phát vé hoặc phương pháp RCA. Bộ RCA không quyết định cấu hình danh tính ở đây.

## 3. Phương án cho đăng ký và machine account

Giữ một realm, frontend public và API audience chung theo kiến trúc hiện có. Hai cách xử lý việc BUYER lây sang machine đều là CANDIDATE:

| Phương án | Cách làm | Công sức và giới hạn |
| --- | --- | --- |
| A — Built-in registration + cấu hình machine có kiểm quyền, đề xuất trong phạm vi | Giữ BUYER trong default composite cho human registration; bỏ composite và business roles khỏi service account trước khi bật client; whitelist scope bằng Console/Admin API | Không cần code mới; phải quản lý toàn bộ machine account bằng quy trình chuẩn và kiểm drift |
| B — Gán BUYER ở bước đăng ký riêng, chỉ lưu làm phương án dự phòng | Bỏ BUYER default realm; bổ sung registration provider cấp role chỉ khi tạo human user | Cần code/provider và kiểm tương thích; ngoài phạm vi hiện tại, không có task triển khai B |

Phương án A là đề xuất của agent, không phải USER_CONFIRMED. B12 fixture hiện không có default BUYER và machine flow đang tắt: không sửa fixture hoặc gọi phương án A đã được B12 phê duyệt. Lần này chỉ kiểm cấu hình Keycloak và ghi bằng chứng trong spec/note; việc nhận cấu hình vào baseline hệ thống ở B12/B13 thuộc đợt tích hợp sau. Nếu A không đạt kiểm quyền, dừng phần machine chưa đạt và trình kết quả; không chuyển sang viết B/SPI.

Default composite đề xuất cho human gồm BUYER và client role `account.manage-account`. Không tự cấp ADMIN/ORGANIZER. Quyền scope chỉ giới hạn nội dung token, không thay việc loại role assignment thực tế khỏi machine.

## 4. Realm và client registry đề xuất

### 4.1 Realm

| Thuộc tính | Đích |
| --- | --- |
| realm | flash-ticket |
| registrationAllowed / registrationEmailAsUsername / loginWithEmailAllowed | true / true / true |
| duplicateEmailsAllowed / editUsernameAllowed | false / false |
| verifyEmail | false, theo FR-50 |
| resetPasswordAllowed | true; chỉ nghiệm thu sau gửi thư/reset thành công |
| roles nghiệp vụ | BUYER, ORGANIZER, ADMIN |
| loginTheme / accountTheme | Giữ flash-ticket Ticket Orange / keycloak.v3 |
| accessTokenLifespan | Giữ 300 giây từ JSON hiện có; chưa tuning tải |
| SMTP | Giữ phương án Gmail STARTTLS 587 đã chọn cho local; credential ngoài Git |

Không áp password policy, brute-force policy, MFA hoặc session timeout mới chỉ để làm đầy spec. Các hardening bổ sung cần thiết kế trong phạm vi riêng; token expiry và guard hiện có vẫn bắt buộc.

### 4.2 Client

| Client ID | Nhiệm vụ | Auth / Standard / Service account | Audience access token |
| --- | --- | --- | --- |
| flash-ticket-frontend | Login web human | OFF / ON + PKCE S256 / OFF | flash-ticket-api |
| flash-ticket-api | Đại diện tài nguyên API ngoài | Không cấp token; mọi grant OFF, không secret dùng trong app | Audience target |
| flash-ticket-internal | Namespace quyền API nội bộ | Không cấp token; mọi grant OFF, không secret dùng trong app | Audience target |
| flash-ticket-user-management-client | User Service gọi Keycloak Admin API | ON / OFF / ON | realm-management; không gắn scope API human/internal |
| flash-ticket-payment-service | Payment gọi HTTP nội bộ | ON / OFF / ON | flash-ticket-internal |
| flash-ticket-event-service | Event gọi HTTP nội bộ | ON / OFF / ON | flash-ticket-internal |

Hai client HTTP nội bộ dựa trên các lời gọi hiện có của B13, không phải mỗi service phải có client. Không cấp thêm client cho service chỉ nhận JWT hoặc chỉ dùng RabbitMQ. Dùng bearer-only cho audience resource nếu representation 26.8 hỗ trợ; tiêu chí chính là mọi grant/service account OFF và không yêu cầu secret để xác minh JWT.

Giữ các client built-in `account`, `account-console`, `admin-cli`, `broker`, `realm-management`, `security-admin-console`. Không gắn scope API human/internal vào chúng. Android và luồng mobile không nằm trong enablement lần này, không xóa client hay cắt API check-in đã duyệt.

`flash-ticket-backend` chưa rõ consumer runtime. Giữ cấu hình và credential hiện có của client này, ghi tình trạng trong bàn giao; không đổi/xóa/disable để ép chuyển đổi khi chưa có inventory consumer. Kiểm role assignment của nó ở Keycloak để báo nguy cơ kế thừa BUYER. Không dùng secret của nó cho caller mới. Migration client cũ không phải điều kiện nghiệm thu những client mới đã chuẩn bị.

### 4.3 Frontend callback và CORS

Local web origin là `http://localhost:5173`. Input `WEB_REDIRECT_URI` chỉ định đúng route callback mà frontend hiện thực; `WEB_POST_LOGOUT_REDIRECT_URI` chỉ định trang quay về. Không giả định route `/callback` đã có trong code. Input deploy phải dùng URL thật, không IP cũ `15.134.248.39`.

Gỡ origin có khoảng trắng đầu dòng, `+` và wildcard không cần thiết khỏi cấu hình đích. Postman có client public riêng `flash-ticket-postman` chỉ ở profile test nếu cần; không cấp Admin API/service account. Không sửa Android callback trong task này.

Client frontend ở Keycloak cho phép scope `openid profile email roles flash-ticket-api-audience`; Code + PKCE S256, Implicit/Direct Access Grants OFF. Lần này chỉ sửa callback/Web Origins trong Keycloak và kiểm bằng browser/test client, không sửa frontend hoặc Gateway CORS. Access token sẽ dùng gọi API khi tích hợp sau; ID token phục vụ phiên đăng nhập frontend.

## 5. Scope, role và quản lý tài khoản

### 5.1 Human

- Scope `flash-ticket-api-audience`: audience mapper thêm custom audience `flash-ticket-api`; access token ON, ID token OFF. Chỉ attach mặc định vào client human ứng dụng.
- `fullScopeAllowed=false`; dedicated role scope mapping frontend cho BUYER/ORGANIZER/ADMIN và các claim profile/email cần thiết. Không cho `realm-management` roles xuất hiện qua frontend.
- Giữ roles mapper `realm_access.roles` để tương thích converter Gateway. Human được cấp role nào thì token chỉ chứa role đó trong whitelist.
- Account Console giữ scope mappings built-in; human có `account.manage-account` để sửa hồ sơ, mật khẩu và phiên của chính mình. Không cấp quyền quản trị user khác hoặc thêm tính năng tự xóa tài khoản.
- Kiểm trực tiếp `${KEYCLOAK_BASE_URL}/realms/flash-ticket/account/` trên browser. Bàn giao URL này để frontend tích hợp sau; không thêm link/route trong frontend lần này. Hồ sơ organizer/follow thuộc User Service, ngoài phạm vi.

### 5.2 Admin ứng dụng

Chuẩn bị realm role ADMIN cho human và service account quản lý user trong Keycloak. Kiểm claim ADMIN của human, token client_credentials của machine và quyền machine trên Keycloak Admin API bằng công cụ test. Không gọi hoặc viết API User Service trong lần này.

Luồng tương lai đã chọn là human ADMIN → User Service → Keycloak Admin API. Guard ADMIN và việc giữ secret ở backend là yêu cầu bàn giao để tích hợp sau, chưa được hiện thực/nghiệm thu bởi task Keycloak.

Service account user-management-client chỉ giữ `realm-management.query-users`, `view-users`, `manage-users` cùng role scope mappings tương ứng. Đây là baseline quyền rộng trong phạm vi user, không tuyên bố fine-grained restriction tại Keycloak. Loại `realm-admin`, `manage-realm`, `manage-clients`, `impersonation` và các admin roles không cần.

Kiểm trong Keycloak trên user test: có thể cấp ORGANIZER mà vẫn giữ BUYER, và role scope frontend phản ánh đúng sau lần login/token mới. Không coi thử role mapping này là workflow duyệt organizer đã chạy. Gán ADMIN/admin kỹ thuật chỉ cho tài khoản được chỉ định trong bước cấu hình; không đặt vào default role.

Danh sách thao tác/field UI quản lý user chưa được chọn trong phiên này, thuộc đợt tích hợp sau và không chặn nghiệm thu cấu hình Keycloak. Token có manage-users chứng minh năng lực Admin API của machine, không chứng minh UI/backend đã giới hạn đúng thao tác. Không thêm khóa/xóa tài khoản, impersonation, reset bởi admin hoặc REST CRUD vào phạm vi hiện tại.

Người quản trị kỹ thuật hiện có trong realm giữ quyền riêng của họ; không tước realm-admin khỏi người thật khi thu hẹp machine account. Kiểm đường admin/recovery hiện có trong Keycloak, không thay startup JSON hoặc tạo DB mới trong task này.

### 5.3 Machine HTTP

Scope `flash-ticket-internal-audience` thêm custom audience `flash-ticket-internal`, access token ON, ID token OFF; chỉ attach vào machine HTTP clients. Client roles của resource `flash-ticket-internal` thể hiện từng operation; tên dưới đây là CANDIDATE kỹ thuật:

| Caller | API đích theo B13 hiện tại | Client role cần có |
| --- | --- | --- |
| flash-ticket-payment-service | Booking POST /internal/v1/orders/{id}/freeze-payment | booking.freeze-payment |
| flash-ticket-payment-service | Ticket GET /internal/v1/orders/{id}/issuance | ticket.read-issuance |
| flash-ticket-event-service | Payment GET /internal/v1/events/{id}/financial-summary | payment.read-financial-summary |

Resource `flash-ticket-internal` không có grant/service account; client này chỉ chứa quyền và làm namespace của `resource_access.flash-ticket-internal.roles`. Nếu triển khai dùng audience mapper custom thì audience không phụ thuộc việc client role tự resolve.

Lần này chỉ kiểm token Keycloak cấp có iss/exp/nbf/aud, `azp`, `sub` machine và client roles đúng bảng. Bảng API đích dùng làm căn cứ cấu hình quyền, không phải danh sách API cần code hoặc gọi khi nghiệm thu. Kiểm chữ ký bằng JWK Keycloak và đọc effective roles để không nhầm token đã lọc scope với role assignment đã được dọn.

Client máy bật authentication + service account, mọi flow login human OFF, redirect/web origins rỗng, fullScopeAllowed=false. Chỉ có roles/basic/service_account và dedicated mappings cần thiết; không offline_access, human API audience hoặc realm-management. Kiểm grant client_credentials tại token endpoint Keycloak; không viết token cache, HTTP interceptor hoặc OAuth client trong các service.

RabbitMQ dùng credential/vhost/permissions của broker riêng. Spec này không chọn OAuth plugin RabbitMQ, không nhét JWT vào payload và không coi messageType/correlationId là credential. Credential/ACL broker và đồng bộ Keycloak–RabbitMQ là hạng mục tích hợp riêng theo PRJ-032, không bị xóa bởi spec này.

## 6. Provisioning an toàn, áp dụng lặp lại và migration

### 6.1 Cách áp dụng cấu hình

Dùng Admin Console hoặc Admin API với công cụ sẵn có. Bảng §4/5 là whitelist cấu hình đích; implementation plan tiếp theo chỉ liệt kê thao tác Keycloak, giá trị cần điền và cách read-back. Không tạo helper Python, manifest subsystem, SPI, Maven module hoặc code service trong phạm vi này.

Secret/admin token lưu trong nơi bảo vệ ngoài Git; resolve UUID hiện tại từ clientId/role name bằng Console/API. Bản ghi thao tác chỉ chứa tên đối tượng, thuộc tính không nhạy cảm và kết quả; không in JWT/secret/SMTP password/HTTP body chứa credential. Không điền secret vào cấu hình các service FlashTicket.

Không sửa export/startup fixture/Compose/DB để thay cho cấu hình realm đang tồn tại. Snapshot/export phục hồi nếu cần lưu ngoài Git và bảo vệ credential; không sửa lịch sử Git hoặc dựng lại realm/DB trong task này.

### 6.2 Machine role cleanup bắt buộc

1. Inventory tất cả client/service account, user/group/composite/effective role mappings; phân loại theo `serviceAccountClientId`, không theo username/email đoán.
2. Client máy mới tạo disabled trước khi cấu hình service account; client đang dùng phải có cửa sổ thay đổi rõ ràng, không disable hàng loạt.
3. Bỏ mapping default composite và trực tiếp BUYER/ORGANIZER/ADMIN/account roles khỏi machine. Không remove BUYER khỏi composite dùng chung của human để sửa một machine.
4. Nếu business role còn đến từ group/composite khác: báo nguồn kế thừa, dừng enablement và xử lý nguồn đúng đối tượng. Không thay group/composite của người thật hàng loạt.
5. Cấp đúng machine roles, role scope mappings và audience. Read-back effective roles phải không có business roles/admin roles ngoài policy.
6. Enable client trong cửa sổ kiểm có kiểm soát; lấy token từ Keycloak và kiểm chữ ký/claims/effective roles. Với user-management, kiểm thêm quyền Admin API của chính Keycloak. Thất bại: client mới trở lại disabled. Không thử các API FlashTicket trong phạm vi này.

Việc giữ BUYER default nghĩa machine tạo ngoài quy trình này vẫn có thể nhận BUYER. Cần audit toàn realm trước bàn giao và khi tạo/đổi machine account. Không tuyên bố phân biệt human/machine tự động tuyệt đối. Với client cũ giữ nguyên như flash-ticket-backend, ghi riêng ngoại lệ tìm thấy; không ghi toàn realm đã sạch nếu vẫn có ngoại lệ. Nếu A không đạt thì báo phần chưa đạt, không triển khai B trong phạm vi hiện tại.

### 6.3 Realm đã tồn tại

Read-back và lập danh sách thay đổi trước apply; xác nhận endpoint/realm đúng và quyền admin đủ. Snapshot cấu hình được bảo vệ trước thay đổi. Áp từng object, read-back ngay; chỉ sửa thuộc tính trong §4/5 đã được duyệt, không replace toàn realm/client hoặc xóa user/role ngoài scope.

Sửa JSON rồi restart không cập nhật realm tồn tại. Dùng Admin API; không import override hoặc xóa volume. Scope/role không trùng thì tạo; trùng thì reconcile nội dung sở hữu. Áp lần hai không tạo duplicate/mất role. Partial failure giữ báo cáo object đã áp, dừng và cho resume; không báo transaction toàn realm thành công.

Nếu preflight thấy tài khoản thường có required action VERIFY_EMAIL từ cấu hình cũ, ghi danh sách ID vào báo cáo bảo vệ và đề xuất xử lý riêng cho realm flash-ticket theo FR-50. Không gỡ action/update emailVerified hoặc credential hàng loạt; không đụng master.

### 6.4 Secret và rollback

Các machine client mới có secret độc lập. Không rotate secret client cũ nếu việc đó đòi cập nhật consumer FlashTicket trong lần này; ghi migration thành phụ thuộc tương lai. Snapshot recovery chứa secret được bảo vệ ngoài Git. Rollback cấu hình theo object, không restore nguyên DB để chữa Web Origins/role mapper.

Token đã cấp có thể còn hợp lệ tới expiry; disable client hoặc bỏ role không tự vô hiệu mọi JWT cũ ở resource server. Ghi TTL và thời điểm thay đổi để bàn giao; kiểm việc Keycloak ngừng cấp token mới khi client bị disable trên client test. Không kiểm guard/token cũ ở service và không hứa instant revocation cho JWT kiểm offline.

## 7. Bàn giao cho tích hợp sau — ngoài phạm vi triển khai

Sau cấu hình, bàn giao metadata không nhạy cảm đọc từ Keycloak:

| Dữ liệu | Nội dung |
| --- | --- |
| Realm và issuer | flash-ticket; iss từ discovery, local dự kiến http://localhost:8080/realms/flash-ticket |
| Discovery/JWK/token URLs | Đọc từ /.well-known/openid-configuration của realm, không viết lại URL bằng IP cũ |
| Client IDs | Registry ở §4 và trạng thái enabled/disabled thực tế |
| Audiences | flash-ticket-api cho human; flash-ticket-internal cho machine HTTP; realm-management cho Admin API |
| Claims/quyền | realm_access.roles; resource_access.flash-ticket-internal.roles; azp/sub của machine đã cấu hình |
| Account Console | Keycloak base URL + /realms/flash-ticket/account/ |
| Callback/origin/logout | Giá trị đăng ký trong Keycloak và giới hạn môi trường test |
| Credential | Bàn giao riêng qua nơi bảo vệ, không đưa vào spec/note/log hoặc sửa env service |

Frontend/Gateway/User Service/resource servers sau này cần nhận cấu hình, kiểm JWT/role/ownership/caller và giữ secret ở backend. Lần này không viết guard, route, UI, OAuth client, token cache hoặc workflow duyệt organizer. Không thay port Gateway dù mặc định 8080 có thể trùng Keycloak khi cùng host; chỉ ghi chú cho đợt tích hợp.

Endpoint Keycloak phải reachable để thao tác. Giữ Compose/network/hostname server hiện có; không triển khai EC2, TLS/proxy hoặc thay DB. Endpoint không truy cập được thì báo input thiếu, không sửa hạ tầng ngoài scope. Cấu hình local HTTP không chứng minh public deployment đã sẵn sàng.

## 8. Bản đồ tác động

Lượt cập nhật tài liệu chỉ sửa spec này và ghi GOV-155 vào decision-register; GOV-154 giữ nguyên kênh admin tương lai. Không sửa runtime, Compose, export, code, schema, B12/B13 hoặc .gitignore. Những xóa governance/sửa .gitignore có trước được giữ nguyên.

Sau duyệt spec, plan chỉ có các thao tác sau:

| Đối tượng Keycloak | Thay đổi dự kiến | Bằng chứng |
| --- | --- | --- |
| Realm flash-ticket | Registration, default roles, Account Console permissions, verifyEmail theo FR-50, SMTP/reset | Read-back và user test |
| Client ứng dụng | PKCE, grants, Web Origins/callback/logout có đầu vào | Read-back và login bằng test client |
| Scopes/resources | Audience mappers, role mappers/scope mappings, resource role namespace | Token claims + effective roles |
| Service account quản lý user | Thu hẹp realm-management roles và scope | Token và quyền Admin API Keycloak |
| Machine HTTP clients mới | Credential độc lập, grants, loại default/business roles, quyền dự kiến | Token/effective roles; chưa gọi service thật |
| Snapshot và note | Metadata không secret, trạng thái/ngoại lệ, dữ liệu phục hồi bảo vệ | Báo cáo chỉ nghiệm thu Keycloak |

Không có deliverable helper/manifest mới, SPI, RabbitMQ integration, B12/B13 propagation, frontend hoặc code/config các service FlashTicket trong plan hiện tại.

## 9. Ma trận nghiệm thu tại Keycloak — chưa chạy

Các ca này thay phạm vi ca cùng mã ở KC-ID-v0.1; không giữ kỳ vọng gọi API FlashTicket từ bản trước.

| ID | Tình huống | Kết quả phải quan sát tại Keycloak |
| --- | --- | --- |
| KC-01 | Đăng ký human mới | BUYER + account.manage-account; không ADMIN/ORGANIZER; không chặn verify email theo FR-50 |
| KC-02 | Code/PKCE đúng và sai verifier | Đúng nhận token; sai bị từ chối; implicit/password grant bị từ chối |
| KC-03 | JWT human frontend | aud API, không internal/realm-management; chỉ business roles đã cấp |
| KC-04 | Account Console | User test sửa hồ sơ/đổi password/quản lý phiên của mình; không có quyền Admin API user khác |
| KC-05 | Reset password | Email tới hộp thư test; link đúng account; password cũ không login được sau reset |
| KC-06 | ADMIN human | User test được chỉ định có ADMIN trong token frontend, không realm-management; default human không ADMIN |
| KC-07 | user-management machine | Admin API Keycloak đọc/tìm/cập nhật user test được phép; quản lý realm/client/impersonation bị từ chối |
| KC-08 | ORGANIZER | Thêm vào user test vẫn giữ BUYER; token mới phản ánh đúng; không nghiệm thu workflow User Service |
| KC-09 | Machine payment/event | client_credentials; không BUYER/ORGANIZER/ADMIN/account/realm-management; đúng internal audience/roles/caller |
| KC-10 | Credential/grants machine | Sai secret bị từ chối tại token endpoint; Standard/password/implicit OFF; không human API audience |
| KC-11 | Drift kế thừa | Effective roles không có quyền bị cấm; ca gây drift chỉ thử ở realm test tách biệt |
| KC-12 | Áp lặp/read-back | Cùng giá trị không tạo duplicate/mất role; lỗi giữa chừng ghi rõ object đã áp |
| KC-13 | Đối tượng ngoài scope | User/credential/role ngoài scope, master, Android và flash-ticket-backend không đổi |
| KC-14 | Đường quản trị | Admin kỹ thuật hiện có vẫn vào Console/có quyền recovery trước bàn giao |
| KC-15 | Disable client test | Keycloak ngừng cấp token mới; ghi TTL token cũ, không claim service đã từ chối token |
| KC-16 | Metadata/secret | Discovery/callback/profile test khớp; note/log không có credential; không sửa service/Compose |

Hoàn thành khi cấu hình và các ca thuộc Keycloak được kiểm trên môi trường đã chọn, với ngoại lệ/mục chưa chạy ghi riêng. Login kiểm bằng client test đã đăng ký callback trong Keycloak; không cần sửa frontend FlashTicket. Thư thật và browser cần bằng chứng riêng, không đổi kiểm cấu hình thành PASS hành vi.

Guard Gateway, quyền ADMIN ở User Service, caller guard API nội bộ, ownership, UI admin và đồng bộ RabbitMQ không thuộc nghiệm thu hiện tại. Chúng không ngăn bàn giao cấu hình Keycloak đã kiểm nhưng phải kiểm ở đợt tích hợp trước khi tuyên bố hệ thống chạy đủ/an toàn cả bốn luồng.

## 10. Điểm OPEN và cổng phê duyệt

| Điểm | Người/input cần có | Chặn bước nào |
| --- | --- | --- |
| Chấp thuận spec chỉ cấu hình Keycloak và phương án A | Người dùng review spec | Viết plan/áp cấu hình |
| Endpoint/realm đích và quyền quản trị | Người dùng/Sơn, endpoint reachable | Apply Keycloak; không tự mở hạ tầng AWS |
| Exact callback/logout URL | URL frontend đã có hoặc callback của client test | Chỉ client tương ứng; không yêu cầu code frontend |
| User test/hộp thư test | Tài khoản kiểm thử được phép | Ca browser/email thực |
| Ngoại lệ client/action VERIFY_EMAIL cũ | Read-back Keycloak theo §6 | Thay mapping/action ngoài whitelist, không tự mở migration service |

Chi tiết CRUD UI/admin service, token validation các service, inventory consumer/rotation legacy và cập nhật B12/B13 được bàn giao cho giai đoạn sau, không phải task của plan hiện tại.

Phê duyệt phạm vi không tự phê duyệt kỹ thuật. Spec vẫn DRAFT. Sau review spec mới viết implementation plan bằng writing-plans, với toàn bộ bước thực thi chỉ ở Keycloak. Chưa có quyền sửa realm từ lượt cập nhật spec này.

## 11. Tự rà và giới hạn bằng chứng

Spec phân biệt lựa chọn người dùng về kênh admin với đề xuất kỹ thuật; không thay FR-50 bằng chính sách master. Không thêm service/Saga/DB; machine permissions khác human business roles. Scope không bị mô tả thành role assignment; audience không bị mô tả thành quyền operation. Cleanup có điều kiện và giới hạn drift, không tự nhận cơ chế human-only đã được chứng minh.

Nguồn đã kiểm trong repo là cấu hình và code, không phải dữ liệu runtime hiện tại. KC-01–16 chưa chạy. Spec nằm trong thư mục docs/superpowers đang bị Git ignore; lưu local theo cấu trúc skill, không force-add hoặc thay ignore để commit.

Rà tài liệu ngày 07/10: relative links hợp lệ, không có mục viết dở, không whitespace cuối dòng, đủ KC-01–16, GOV-154 chỉ có một dòng quyết định; git diff --check PASS. Đây là rà tài liệu, không phải nghiệm thu cấu hình. Audit governance từ bản HEAD chạy qua file tạm do script working tree đã bị xóa: không hoàn tất, lỗi xử lý nội dung AGENTS rỗng tại GetByteCount; working tree đồng thời thiếu năm file governance. Không phục hồi file bị xóa hoặc thay script của người dùng để làm audit PASS.

Nội dung có thể đưa vào báo cáo sau kiểm chứng: cấu hình quyền human/machine, audience/role scope và Account Console. Kênh admin qua User Service là đích tích hợp, chưa có bằng chứng chạy. Không dùng spec làm bằng chứng guard service, hệ thống đã chạy hoặc đáp ứng tải DT18-NV1.

Nhật ký: v0.1 lập cấu hình kèm điều kiện tích hợp; v0.2 theo GOV-155 chỉ cấu hình/kiểm ở Keycloak, bỏ deliverable code/helper/service/hạ tầng và thay ma trận nghiệm thu bằng các ca chỉ tại Keycloak. Rà lại phạm vi, relative links và whitespace trong lượt v0.2; audit governance v0.1 không hoàn tất do trạng thái workspace đã ghi trên, không có thay đổi khôi phục governance trong v0.2.

## 12. Nguồn

- [Đề tài/nhiệm vụ hiện hành DT18](../../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md); [phân công](../../project/roles.md).
- [B8](../../domain/B8-requirements.md), [B12](../../architecture/B12-data-ownership-and-schema.md), [B13](../../architecture/B13-api-and-event-contracts.md), [fixture identity](../../architecture/data/keycloak/README.md).
- [Decision register](../../project/decision-register.md), [Compose Keycloak](../../../deploy/docker-compose.keycloak.yml), [Note local](../../../deploy/Note.md).
- [Keycloak 26.8 service account và role scope](https://www.keycloak.org/docs/26.8.0/server_admin/#_service_accounts); [audience](https://www.keycloak.org/docs/26.8.0/server_admin/#_audience).
- [ClientManager 26.8: tạo/xóa service account](https://github.com/keycloak/keycloak/blob/26.8.0/services/src/main/java/org/keycloak/services/managers/ClientManager.java); [AccountRestService: kiểm quyền sửa account](https://github.com/keycloak/keycloak/blob/26.8.0/services/src/main/java/org/keycloak/services/resources/account/AccountRestService.java).
- [Keycloak startup import](https://www.keycloak.org/server/importExport#importing-a-realm-during-startup); [OIDC endpoints/grants](https://www.keycloak.org/securing-apps/oidc-layers).
- [Spring Security JWT validation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
