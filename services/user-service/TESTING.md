# Kiểm chứng User Service scaffold

## Chạy lại

Dùng JDK **23** (`JAVA_HOME` trỏ tới JDK 23), Maven Wrapper trong `services/user-service`.
Enforcer `[23,24)` vẫn bật. Spring Boot 4.1.1; không cần credential production cho hai gate dưới.

```powershell
# Fast tests + Spotless + JaCoCo + SpotBugs; không chạy *IT
rtk proxy .\mvnw.cmd -B -ntp verify

# Có Docker: một gate chạy cả fast tests và real Mongo integration
rtk proxy .\mvnw.cmd -B -ntp -Pintegration verify
```

Không cần chạy cả hai lệnh liên tiếp. Integration yêu cầu Docker đang chạy và tải được
image MongoDB có digest trùng [Compose](../../deploy/mongodb/compose.yaml).
Thiếu Docker thì IT fail, không tự skip để báo xanh.

## Kết quả quan sát ngày 25/10/2026

Gate integration chạy bằng Java **21.0.12.1**, Docker **28.0.4**, tại commit `6c36dc8`:
`BUILD SUCCESS`, 57.895 giây. Task tài liệu dùng lại gate này vì code/config/dependency không đổi.

| Kiểm chứng | Kết quả | Phạm vi chứng minh |
|---|---|---|
| Surefire | 98 tests; 0 fail/error/skip | Converter BSON, DTO/JSON, MapStruct, lỗi/log, JWT, bean wiring và Rabbit config |
| Failsafe `UserPersistenceIT` | 5 tests; 0 fail/error/skip | Bốn collection thật, validators/indexes, uniqueness, version và sourceVersion |
| Spotless | Pass, 61 Java files | Định dạng Java |
| SpotBugs | 0 findings, 0 analysis errors | Phân tích tĩnh, gồm mã mapper sinh ra |
| JaCoCo | `target/site/jacoco/index.html` được tạo | Báo cáo coverage; không đặt coverage threshold mới |
| Runtime Config Server/Eureka/Mongo119/Rabbit | Đã smoke bổ sung, tất cả health UP | User chạy bằng root env thật; tunnel Mongo17017/Rabbit5672; đăng ký Eureka UP |
| Keycloak Admin API | Chưa tích hợp/chưa gọi realm | Guide chỉ là mã minh họa cho task sau |

Reports tái tạo tại `target/surefire-reports`, `target/failsafe-reports`,
`target/spotbugsXml.xml`, `target/site/jacoco/`.

## Cách cô lập

- Fast suite không kết nối Mongo/Config Server/Eureka/broker thật. Startup test mock bốn
  repository và JwtDecoder chỉ để kiểm bean graph; không dùng mock đó chứng minh JWT validation.
- `SecurityConfigTests` ký JWT bằng RSA fixture, dùng JWKS HTTP loopback và decoder/validator thật:
  kiểm signature, issuer, expiry/nbf, role và route. Handler trả 204 chỉ tồn tại trong test.
- `MongoMappingTests` dùng converter thật, chưa ghi DB. `UserPersistenceIT` dùng repository thật
  trên Mongo standalone trong Testcontainers, random host port và credential fixture riêng.
  Container chạy [schema](../../docs/architecture/data/mongodb/schema.cjs),
  [init](../../deploy/mongodb/10-user.js) và [prefix script](../../deploy/mongodb/99-collection-prefix.js).
  Cleanup chỉ xóa document trong container test, giữ validators/indexes.
- IT kiểm optional field bị omit thay vì null, không có `_class`, trạng thái application hợp lệ,
  duplicate subject/follow pair bị chặn, stale writes thất bại và sourceVersion=0 được giữ nguyên.
- Rabbit tests kiểm JSON converter, cấu hình Boot thật với connection factory lazy và fatal error
  disposition. Không khởi động listener, không declare topology hoặc kết nối broker thật.
- HTTP/Rabbit log tests dùng sentinel trong payload/header/exception/MDC; kiểm safe fields,
  correlation UUID, stack giới hạn và JSON không trùng key.

## Giới hạn

Đây là scaffold: controller/service chưa có business handler, consumer/publisher chưa triển khai.
Chưa chứng minh B13 approve/reject, B15 V17/V18, identity flow hoặc RCA end-to-end.
Gateway chưa mở anonymous organizer profile. Xem [HANDOFF](HANDOFF.md) để chạy runtime và
[guide Admin API](../../docs/guides/user-service-keycloak-admin-integration.md) cho bước sau.

## Review cuối

Đã tự review toàn bộ diff sau bảy task (phiên không có subagent): đối chiếu năm Review Focus,
scope feature shells, BSON/validator, security matcher, safe log, test isolation và runtime YAML.
Không phát hiện lỗi Critical/Important trong code. Guide đã bổ sung refresh/login lại của
người dùng sau grant theo B13; phần này chưa được nghiệm thu trên realm thật.
Tự review của tác giả yếu hơn review độc lập. Chưa merge/push vào nhánh dùng chung.

## Smoke đầu tiên với server119 sau khi sửa env (Rabbit5673)

- Root env thực tế: Mongo `127.0.0.1:17017`, Rabbit `localhost:5673`,
  issuer `http://3.106.109.119:8080/realms/flash-ticket`. Chỉ đổi issuer và local Mongo port,
  giữ nguyên credential. Windows từ chối bind27017 (error10013), bind17017 và5673 thành công.
- SSH forwards `17017 -> 119:27017`, `5673 -> 119:5672`. Không cần tunnel Keycloak.
- JDK21 chạy Config Server/Eureka/User thật, không dùng profile test hoặc mock repository.
  Config Server dùng native search-location trỏ tới YAML hiện tại trong worktree.
  Xác minh cấu hình trả về không có explicit `jwk-set-uri` cho User.
- User port8085: `/actuator/health` trả UP; `mongo` và `rabbit` đều UP.
  Chỉ bật hiển thị tên/status component cho smoke trên loopback; production vẫn ẩn chi tiết.
- Eureka có `USER-SERVICE` trạng thái UP tại port8085. Anonymous `/api/v1/me` trả401
  với Problem `UNAUTHENTICATED`.
- Keycloak119 discovery trả issuer khớp env và JWKS có public keys. Không lấy token quản trị,
  không nghiệm thu token người dùng hoặc Admin API workflow trong smoke này.
- User chưa dùng Redis: không có dependency/config/health contributor Redis, đúng scope scaffold.
- Không ghi fixture vào DB, không declare/publish/consume message trên broker119.
  Toàn bộ process/tunnel do smoke tạo đã được đóng sau kiểm chứng.

## Smoke ngày 26/10/2026 với Rabbit local5672

- Dừng Windows service `RabbitMQ` đang giữ cổng5672 qua quyền Administrator;
  không gỡ cài đặt hoặc xóa dữ liệu RabbitMQ local.
- Root `.env` dùng `RABBITMQ_HOST=localhost`, `RABBITMQ_PORT=5672`.
  Tunnel hiện tại: `127.0.0.1:5672 -> 119:5672`, Mongo giữ `17017 -> 119:27017`.
- Chạy lại User thật bằng JDK21, Config Server và Eureka: User/Mongo/Rabbit health đều UP,
  `USER-SERVICE` đăng ký UP tại8085, anonymous `/api/v1/me` trả401 Problem,
  Keycloak119 discovery/JWKS PASS.
- Sau smoke chỉ dừng các process User/Config Server/Eureka; giữ SSH tunnel mở cho IDE.
  Không ghi fixture DB hoặc publish/consume message trên server119.
