# Spec — Scaffold đầy đủ cho User Service theo Booking

- Ngày: 2026-10-25
- Trạng thái: **DRAFT — chờ người dùng duyệt nội dung spec**
- Baseline source đã đối chiếu: `6656f0d`
- Quyết định đã xác nhận: chọn hướng 1, scaffold đầy đủ theo Booking; chưa tích hợp Keycloak Admin API; có tài liệu hướng dẫn kèm code mẫu cho giai đoạn sau
- Tài liệu này đặc tả task triển khai tiếp theo; việc viết spec chưa triển khai hoặc nghiệm thu User Service

## 1. Mục tiêu và kết quả mong đợi

Chuẩn bị User Service đủ cấu trúc và cấu hình để triển khai từng nghiệp vụ sau: document MongoDB, enum, DTO, validation, MapStruct, repository, controller và service interface/implementation rỗng, security, JSON, RabbitMQ, xử lý lỗi, logging và test nền.

Follow source Booking hiện tại và `agent-service-scaffold-playbook.md`, gồm cách chia feature, constructor injection và error handling sau refactor `6656f0d`. Điều chỉnh theo ownership và MongoDB của User. Không dùng spec Booking cũ làm nguồn ghi đè code hiện tại: có bản spec cũ không tạo controller/service rỗng, trong khi người dùng đã chọn scaffold đầy đủ theo source/playbook hiện hành.

Scaffold hoàn tất nghĩa là cấu trúc bean, mapping, wire DTO và các boundary kỹ thuật được kiểm tra. Chưa có API nghiệp vụ hoặc workflow organizer hoạt động.

## 2. Nguồn và hiện trạng

| Nội dung | Nguồn đối chiếu |
|---|---|
| Cách scaffold, package, dependency, phạm vi | `docs/guides/agent-service-scaffold-playbook.md` |
| Mongo ownership và field | `docs/architecture/B12-data-ownership-and-schema.md` §4.5, §13 |
| BSON validator và index | `docs/architecture/data/mongodb/schema.cjs` |
| Collection vật lý | `services/user-service/database/10-user.js`, `99-collection-prefix.js` |
| REST và quy tắc organizer/follow | `docs/architecture/B13-api-and-event-contracts.md` §2, §9 |
| Kiểm chứng và quan sát | B15, B16; giới hạn scaffold theo playbook và source Booking |
| Runtime | User POM, bootstrap YAML, Config Server YAML, `services/user-service/HANDOFF.md` |
| Rabbit và identity | `deploy/rabbitmq/definitions.json`, `deploy/keycloak/postman/INTERNAL-SERVICE-HANDOFF.md` |
| Mẫu code | Booking `config`, `security`, `exception`, `dto`, `mapper`, feature controller/service/repository tại baseline trên |

User hiện có application, một test context, POM và cấu hình kết nối cơ bản; chưa có document, mapper, repository hoặc security chain riêng. POM hiện dùng Java 21, Spring Boot 4.1.1, Spring Cloud 2025.1.3; có MongoDB, MVC, validation, Resource Server, AMQP, Config Client, Eureka và các quality gate.

B12 là nguồn ownership; module Mongo vẫn có nhãn `CANDIDATE`, và toàn bộ B13-v0.2 có trạng thái `REVIEW_READY` với phạm vi baseline được giải thích trong header. Task này không tự nâng trạng thái các tài liệu nguồn. Phần User không chịu thay đổi luồng thanh toán ở B13 §3–5 được đối chiếu theo §9 và validator đang có.

## 3. Các hướng đã cân nhắc và lựa chọn

1. **Scaffold đầy đủ theo Booking — người dùng đã chọn:** tạo đủ model, DTO, mapper, repository, controller/service rỗng, config và test nền. Chuẩn bị nhiều hơn ngay từ đầu, giúp các task nghiệp vụ sau có cấu trúc thống nhất.
2. **Setup nền chạy trước:** chỉ làm runtime/security/error handling; model và feature bổ sung sau. Ít việc ban đầu, nhưng chưa đáp ứng độ đầy đủ người dùng muốn ở task này.

Đây là task gồm nhiều phần và cần spec đầy đủ. Tổ chức theo feature trong một module User; không tạo shared module hoặc framework CRUD mới.

## 4. Phạm vi file và phần để sau

Được sửa khi triển khai: `services/user-service/**`, YAML riêng `infrastructure/config-server/src/main/resources/config/user-service.yaml`, `.env.example` nếu cần bổ sung tên biến, và tài liệu hướng dẫn/test liên quan đến User.

Không sửa source Booking, Gateway, service khác, hợp đồng B12–B16, dữ liệu đang chạy, validator/index triển khai, realm hoặc topology broker để scaffold boot qua lỗi. Giữ thay đổi `.gitignore` có sẵn ngoài task.

Để sau: handler REST nghiệp vụ; ghi/cập nhật hồ sơ; xét duyệt organizer; gọi Admin API hoặc lấy token quản trị; CAS/retry/projection worker; listener/publisher identity; cron; outbox/inbox; Mongo transaction manager; Redis/cache; SMTP; tích hợp RCA collector/tracing end-to-end. Không thêm endpoint success giả, method trả null hoặc `UnsupportedOperationException` để giữ chỗ.

## 5. Tổ chức code

Package gốc giữ `com.flashticket.user_service`.

```text
config/       Mongo mapping, JSON, Security, RabbitMQ, JSON log và safe stack printer
document/     UserDocument, OrganizerApplicationDocument, OrganizerProfileDocument,
              UserFollowDocument, RoleGrant
enums/        OrganizerApplicationStatus, RoleGrantStatus và enum cần cho mapping
dto/request/  OrganizerApplicationRequest, RejectOrganizerApplicationRequest
dto/response/ MeView, OrganizerApplicationView, OrganizerPublicView, CursorPage, Problem
mapper/       UserMapper, OrganizerApplicationMapper, OrganizerProfileMapper
validation/   Chỉ helper/rule có nhu cầu tái sử dụng thực tế
security/     KeycloakRealmRoleConverter
exception/    ErrorCode, BusinessException, GlobalExceptionHandler
messaging/    UserRabbitErrorHandler
user/         controller/MeController, repository/UserRepository,
              service/UserService, service/impl/UserServiceImpl
organizer/    controller/OrganizerApplicationController,
              controller/AdminOrganizerApplicationController,
              controller/OrganizerController,
              repository/OrganizerApplicationRepository,
              repository/OrganizerProfileRepository,
              service/OrganizerApplicationService, service/OrganizerProfileService,
              service/impl tương ứng
follow/       controller/OrganizerFollowController, repository/UserFollowRepository,
              service/UserFollowService, service/impl/UserFollowServiceImpl
```

Tên class là phương án của người viết spec; có thể đổi tên nhất quán khi lập plan, giữ nguyên ownership và nhiệm vụ. Không tạo thư mục rỗng hoặc DTO message/snapshot khi chưa có wire contract tương ứng.

Controller chỉ khai báo `@RestController`, root `@RequestMapping` và inject service. Interface service không có method; implementation có `@Service`, final fields và constructor injection như Booking. Chỉ inject repository/mapper thuộc trách nhiệm feature; không thêm transaction, vòng phụ thuộc hoặc thuật toán nghiệp vụ.

## 6. MongoDB mapping

Database là `user_db`; mapping đủ field theo validator, với tên collection vật lý dưới đây. Subject là String không trắng, giữ nguyên Keycloak `sub`; không ép thành UUID và không dùng email làm ID.

| Document / collection | Field và kiểu Java |
|---|---|
| `UserDocument` / `tbl_users` | `id:String`, `identitySubject:String`, `displayName:String?`, `createdAt:Instant`, `updatedAt:Instant`, `version:Long` |
| `OrganizerApplicationDocument` / `tbl_organizer_applications` | `id:String`, `identitySubject:String`, `organizationName:String`, `shortDescription:String`, `status:OrganizerApplicationStatus`, `rejectionReason:String?`, `decidedBySubject:String?`, `decidedAt:Instant?`, `roleGrant:RoleGrant?`, `createdAt:Instant`, `updatedAt:Instant`, `version:Long` |
| `OrganizerProfileDocument` / `tbl_organizer_profiles` | `id:String`, `identitySubject:String`, `sourceApplicationId:String`, `sourceVersion:Long`, `organizationName:String`, `shortDescription:String`, `status` chỉ `ACTIVE`, `logoUrl:String?`, `bannerUrl:String?`, `refreshedAt:Instant` |
| `UserFollowDocument` / `tbl_user_follows` | `id:String`, `buyerSubject:String`, `organizerSubject:String`, `createdAt:Instant` |
| Embedded `RoleGrant` | `operationId:String`, `status:RoleGrantStatus`, `attemptedAt:Instant`, `confirmedAt:Instant?` |

Dấu `?` trong bảng là optional tại BSON: khi không dùng phải vắng mặt, không ghi BSON null. ID nội bộ và operation/source application ID là UUID canonical dạng chuỗi; ngày ghi BSON date; version là BSON int/long không âm. Hai enum giữ đúng `PENDING/ACTIVE/REJECTED` và `REQUESTED/CONFIRMED/FAILED/UNKNOWN`.

Yêu cầu mapping:

- Bảo đảm `_id` được ghi String; không tự sinh ObjectId hoặc UUID binary. Không cấp ID qua side effect trong mapper; use case thật sẽ cấp ID sau.
- Validator kín không nhận `_class` ở root hoặc embedded object. Cấu hình Mongo converter để không ghi type metadata; kiểm bằng chính converter dùng trong ứng dụng.
- Chỉ users/applications có optimistic `@Version`. Profile dùng `sourceVersion` để nhận biết phiên bản nguồn, không coi là optimistic version; follow không thêm version/updatedAt. Không dùng BaseDocument chung làm phát sinh field ngoài schema.
- Không tự tạo collection, index hoặc validator lúc ứng dụng khởi động; tắt auto index creation. Giữ unique index subject, unique cặp follow và các index hiện có theo nguồn.
- Document không chứa password, JWT, refresh token, role cache hoặc email làm khóa. Không thêm follower counter vào Mongo.
- Theo B12, không tự thêm điều kiện organizer ACTIVE riêng cho thao tác follow; điều kiện ACTIVE của profile công khai là quy tắc khác.
- Application fixture phải khớp validator: PENDING không có decided/rejection fields; ACTIVE cần quyết định và roleGrant CONFIRMED; REJECTED cần quyết định/lý do và không có roleGrant. `confirmedAt` chỉ có khi CONFIRMED. Profile chỉ ACTIVE. Đây là điều kiện lưu dữ liệu, chưa triển khai chuyển trạng thái.

Mỗi collection có một `MongoRepository<Document, String>` trong feature tương ứng, chưa thêm query/CAS/lock/custom implementation. Quyền CRUD của Java interface không chứng minh runtime account có quyền tương ứng; nghiệp vụ sau phải tuân quyền DB hiện hành, trong đó chỉ follow có remove.

## 7. DTO, validation và mapper

Hợp đồng cố định từ B13:

- `OrganizerApplicationRequest`: đúng `organizationName`, `shortDescription`, cả hai `@NotBlank`.
- `RejectOrganizerApplicationRequest`: đúng `reason`, `@NotBlank`.
- `OrganizerPublicView`: đúng `organizerSubject`, `organizationName`, `shortDescription`, `followerCount` dạng JSON number không âm; không tự đưa logo/banner/audit vào wire DTO cố định này.
- `Problem`: `code`, `message`, `correlationId`, `violations[{field,code}]`.
- `CursorPage<T>`: `items`, `nextCursor` nullable; page size của API danh sách sau là 1..100. Chưa cài cursor algorithm hoặc repository paging query.

Chi tiết DTO phụ do người viết spec đề xuất cho scaffold, chưa phải endpoint đã hoạt động:

- `MeView`: `identitySubject`, `displayName` nullable; không đọc email/role từ Mongo để tạo profile danh tính mở rộng.
- `OrganizerApplicationView`: `applicationId`, `identitySubject`, `organizationName`, `shortDescription`, `status`, `rejectionReason` nullable, `createdAt`, `updatedAt`. Không trả roleGrant, operation ID hay audit actor.

Không thêm DTO approve rỗng, request chứa actor/role/status, request cập nhật profile độc lập hoặc payload identity tự suy đoán. Không áp regex UUID lên Subject; chưa tự thêm giới hạn độ dài nghiệp vụ ngoài nguồn.

JSON dùng Jackson 3 theo Booking; từ chối field lạ và coercion sai kiểu. Bảo toàn optional/nullable của DTO, timestamp UTC. Việc thiếu optional field hợp lệ không bị global strict config vô tình từ chối. Không sao chép deserializer OrderSelection hoặc converter tiền không thuộc User.

MapStruct 1.6.3, `lombok-mapstruct-binding` 0.2.0, Spring component và `unmappedTargetPolicy=ERROR`. Mapper chuyển document sang view; không query DB, cấp quyền, sinh ID hoặc chuyển application state. Profile mapper nhận followerCount làm tham số; count thật là việc use case sau, không mặc định 0 để che việc chưa query. Không cần FollowMapper khi chưa có response chứa follow document.

## 8. Security và root controller

User vẫn là JWT Resource Server dù chưa tích hợp Admin API. Dùng Spring MVC stateless, chữ ký/JWKS, issuer, audience và expiry được kiểm; cấu hình qua env như Booking. Converter chỉ nhận các realm role đã allowlist, claim thiếu/sai kiểu không cấp quyền.

| Route dành cho feature | Policy scaffold |
|---|---|
| `/api/v1/me` | JWT có ít nhất một role người dùng BUYER/ORGANIZER/ADMIN |
| `/api/v1/organizer-applications` và các đường con của người nộp | BUYER; ownership sẽ kiểm khi có handler |
| `/api/v1/admin/organizer-applications` và các đường con | ADMIN |
| GET `/api/v1/organizers/{subject}` | Public; handler sau chỉ trả profile ACTIVE |
| PUT/DELETE `/api/v1/organizers/{subject}/follow` | BUYER; actor lấy JWT, không lấy request body |
| `/actuator/health`, các health subpath | Public, không trả chi tiết nhạy cảm |
| `/internal/v1/**`, actuator khác và route còn lại | Deny; User chưa có internal HTTP contract cần mở |

Root controller tương ứng với các nhóm route trên; controller follow đặt ở feature follow. Không thêm handler cho bảng policy. Security tests dùng fixture controller chỉ trong test để kiểm matcher, token và status; không biến fixture thành API production. Machine token chỉ có INTERNAL_SERVICE không được vào API hồ sơ người dùng hoặc quản trị.

401/403 đi qua `HandlerExceptionResolver` vào global advice như Booking, giữ Bearer challenge. Error dispatch của Boot cần được xử lý để giữ status gốc như mẫu hiện tại.

Gateway hiện fallback `.authenticated()` và chưa permit public GET organizer profile. Ghi rõ anonymous public profile mới được kiểm tại service; việc public qua Gateway là task tích hợp sau. Không sửa Gateway trong scaffold này và không tuyên bố end-to-end public profile đã hoạt động.

## 9. Runtime, dependency và RabbitMQ

- Giữ bootstrap `spring.application.name=user-service` và Config Server import bắt buộc trong runtime. Common Eureka/management dùng YAML chung hiện có; port riêng `${USER_SERVICE_PORT:8085}`.
- Mongo dùng `spring.mongodb.uri: ${MONGO_URI}` theo baseline Boot của repo; không copy JPA/Hikari. Bổ sung cấu hình mapping cần thiết cho validator kín, không thay schema.
- JWT dùng các biến `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWK_SET_URI`, `KEYCLOAK_AUDIENCE` theo pattern Booking. Không hardcode IP, SSH alias hoặc secret từ HANDOFF.
- Giữ AMQP starter; JSON converter dùng JsonMapper đã cấu hình, listener container có safe error handler, MANUAL ACK, prefetch 20 và `dynamic=false` theo pattern Booking. Không tạo Queue/Exchange/Binding/RabbitAdmin bean.
- Queue đã provision `user.identity.q` và `user.identity.dlq` không chứng minh payload hoặc publisher đã sẵn sàng. Chưa có listener hoặc DTO identity; không mặc định template publish sang `business.ex` khi chưa kiểm binding/account cho identity. Giữ cấu hình connection qua env, không thêm publisher logic.
- Không thêm Redis, Feign, OAuth2 Client hoặc Keycloak Admin SDK khi chưa có tích hợp được giao. Mail starter và mail test starter hiện có không phục vụ scaffold User: phương án của người viết spec là gỡ chúng và điều chỉnh test isolation tương ứng; không cấu hình SMTP.
- Bổ sung MapStruct/annotation processor đúng baseline; giữ Java 21 Enforcer, Spotless, JaCoCo và SpotBugs. Không nâng/hạ framework toàn repo.
- Test-only config tắt Config Server/Eureka và cô lập external dependency; runtime không dùng các switch đó để giả kết nối thành công. Spring không tự đọc `.env`; HANDOFF cần hướng dẫn nạp biến vào process.

## 10. Xử lý lỗi và logging

Follow Booking sau `6656f0d`: một `exception/GlobalExceptionHandler` tạo Problem và ghi log trực tiếp, `ErrorCode` giữ message/status an toàn và nhận diện native exception, `BusinessException` mang code. Không đưa package `observability`, correlation filter, custom `/error`, ProblemResponses hoặc scheduler config cũ trở lại.

Chỉ khai báo mã lỗi kỹ thuật cần cho boundary hiện có; chưa tự invent danh mục lỗi nghiệp vụ organizer khi handler chưa được triển khai. Phân loại đúng Mongo/DataAccess timeout, unavailable, conflict và unexpected error thay vì sao chép lỗi SQL/ghế/thanh toán. Không đổi mọi IllegalArgumentException thành 400. Validation đầu vào là 400, lỗi return value/nội bộ là 500; framework status và header cần thiết được giữ.

JSON log UTF-8 cấu hình native qua YAML, metadata `serviceName=user-service`, schemaVersion số và timestamp UTC; safe stack printer bỏ raw exception message/secret. Dùng helper config nhỏ như Booking. Log lỗi có method/status/code/correlation phù hợp, không log request body, subject/header tùy ý, JWT, Mongo URI hoặc credential.

Correlation lỗi phải khớp response/header/log theo mẫu Booking, validate header trước khi dùng. Không tạo request/correlation header cho mọi response thành công. Native Boot `/error` không bị yêu cầu mang Problem của advice. Chưa có pipeline publish log RCA, role-grant events, tracing agent hoặc collector; không gọi baseline local này là nghiệm thu B16 toàn hệ thống.

## 11. Tài liệu Keycloak Admin API cho task sau

Khi triển khai scaffold, tạo `docs/guides/user-service-keycloak-admin-integration.md`, liên kết từ User HANDOFF. Phải có code mẫu và ghi rõ **chưa được nối vào runtime User**:

1. Phân biệt Resource Server hiện tại với outbound Admin API; dùng client `user-management`, không dùng shared `internal-service` hoặc token BUYER để cấp quyền.
2. Minh họa dependency/cấu hình OAuth2 client_credentials qua placeholder env, authorized-client manager cho application principal ổn định và HTTP client với timeout hữu hạn. Cấu hình ví dụ nằm trong docs, không trở thành secret bắt buộc để scaffold boot.
3. Code mẫu lấy thông tin realm role ORGANIZER, kiểm role của đúng subject và gán role bằng role representation phù hợp. Không giả định tùy ý rằng business Subject là Keycloak user ID nếu môi trường chưa xác minh cách ánh xạ.
4. Thứ tự B13: CAS PENDING chưa roleGrant → ghi REQUESTED/operationId → gọi ngoài Mongo atomic update → query/xác nhận role, giữ BUYER → ghi ACTIVE → phục hồi projection từ application/version. Timeout giữ trạng thái chưa xác định; không false ACTIVE, không Mongo transaction xuyên document, không Saga mới.
5. Phép thử dự kiến: cấp lặp, timeout sau side effect, hai admin approve/reject, profile write lỗi và phục hồi, refresh token người dùng sau thay đổi role. Phân biệt test bằng HTTP fixture với kiểm realm thật.

Người viết guide phải kiểm API/library và quyền tối thiểu theo phiên bản Keycloak đang dùng bằng nguồn chính thức tại thời điểm viết; không công bố code mẫu đã compile/chạy nếu chưa kiểm. Không cấp realm-admin, tạo secret, sửa realm hoặc gọi Admin API thật trong task scaffold.

## 12. Kiểm chứng và tiêu chí chấp nhận

### Fast suite, không phụ thuộc hệ thống đang chạy

| Nhóm | Bằng chứng cần có |
|---|---|
| Mongo mapping | Converter ứng dụng ghi đúng tên collection/field; String UUID, BSON date, enum, version; optional field vắng mặt; không `_class` kể cả RoleGrant; profile/follow không thừa metadata |
| DTO/JSON/validation | Fixture đúng schema; reject field lạ/sai kiểu/blank bắt buộc; Subject không bị ép UUID; optional/null và timestamp đúng |
| Mapper | Đúng field view, không rò roleGrant/audit; followerCount nhận từ tham số; giá trị thiếu không bị thay bằng success giả |
| Security | JWT ký bằng key fixture; đúng/sai signature/issuer/audience/expiry, role, machine token, route public/admin/buyer; 401/403 và Bearer challenge qua global advice |
| HTTP errors | Business/validation/framework/unexpected statuses; return validation 500; correlation body/header/log khớp; thông tin nhạy cảm không xuất ra |
| Log/Rabbit/config | Kiểm actual JSON formatter với sentinel secret; converter và error handler wiring; safe fatal disposition, dynamic=false, không declare topology hoặc tạo listener nghiệp vụ |
| Context | Bean graph feature và repository wiring khởi tạo được với dependency test cô lập; không vô tình thêm scheduler/client Admin API |

Không chỉ mock repository rồi coi persistence đã được kiểm. Tests phải kiểm ứng xử mapping/security/error có ý nghĩa; không kiểm cấu trúc class cho đủ số lượng. Có thể tổ chức suite theo tên tương tự Booking, nhưng không copy số test/kết quả Booking sang User.

### Persistence và runtime smoke có môi trường

Integration test dùng MongoDB dùng riêng cho test, áp đúng schema/index và prefix từ nguồn: round-trip bốn document, duplicate subject/application/follow, optional fields theo từng trạng thái và optimistic version users/applications. Không dùng DB triển khai để insert/delete fixture; môi trường chạy test cần ghi rõ. Có thể đặt trong profile integration opt-in; Docker/test DB chưa sẵn sàng phải báo chưa chạy. Việc setup profile/test fixture nằm trong task, không phải thay validator production.

Smoke runtime khi dependency sẵn sàng: Config Server trả config User, Eureka đăng ký User, Mongo ping/read không ghi dữ liệu nghiệp vụ, Rabbit connection kiểm không declare/publish/consume và health phù hợp. Không sửa broker/realm/DB để smoke vượt qua. Không có consumer thì không tuyên bố đã ACK sau commit; không có handler thì không dùng 404 làm bằng chứng API nghiệp vụ chạy.

Chạy `rtk proxy .\mvnw.cmd -B -ntp verify` trong `services/user-service` với Java 21. Integration profile có lệnh và prerequisites riêng trong TESTING.md. Nếu chỉ có JDK khác, ghi rõ blocker; không sửa Enforcer hoặc gọi lần chạy skip Enforcer là đã qua Java 21 gate. Chạy lại chỉ những kiểm tra cần thiết sau khi sửa lỗi.

Chấp nhận scaffold khi fast suite và quality gate đạt, mapping/policy/config/documentation đầy đủ, mọi lần kiểm integration/smoke được báo đúng đã chạy hoặc chưa chạy và lý do. Chưa chạy Mongo thật thì phần persistence chỉ được kết luận ở mức converter, chưa nghiệm thu lưu/đọc và index enforcement. B15 V17/V18 và RCA end-to-end vẫn dành cho task nghiệp vụ/tích hợp tương ứng.

## 13. Đầu ra và phần người triển khai được tự quyết

Đầu ra task: source scaffold User, central YAML User, test/config test, `services/user-service/TESTING.md`, HANDOFF cập nhật và guide Keycloak nêu trên; `.env.example` chỉ đổi khi có biến thực sự mới. Không thêm credential Admin API vào runtime task này.

Người triển khai tự quyết tên file/helper, cách viết constructor, cách nhóm test và fixture, annotation/custom converter tối thiểu để đạt BSON; giữ baseline dependency và không tạo abstraction chỉ để dự phòng. Không tự quyết mở rộng endpoint, wire field cố định, workflow identity, quyền Admin API hoặc topology.

Phương án do người viết spec đề xuất, cần người dùng duyệt cùng tài liệu: các DTO phụ ở §7; policy `/me` giới hạn các human role và default deny ở §8; gỡ mail starter chưa dùng; tách integration test opt-in. Chúng không được trình bày như quyết định người dùng đã xác nhận riêng.

## 14. Tự rà soát trước khi bàn giao spec

Không có công cụ subagent trong phiên này; rà soát trực tiếp theo builder-check-prompt của skill brainstorming. Các điểm cần để người triển khai không phải đoán đã được xử lý trong spec: optional BSON/null và `_class`; optimistic version khác sourceVersion; response DTO phụ; security fixture khi production chưa có handler; khác biệt Gateway public profile; identity payload/publisher và Admin API được hoãn; giới hạn kiểm chứng khi thiếu hạ tầng.

Các phần hoãn nêu trên không chặn việc lập plan scaffold. Bản spec vẫn là DRAFT; chưa được người dùng duyệt và chưa bắt đầu code. Sau khi người dùng duyệt nội dung, bước tiếp theo là lập implementation plan theo skill `superpowers:writing-plans`.
