# Kiểm tra scaffold Ticket

Từ `services/ticket-service`, Maven dùng Java **23**. Test report ngày 26/10 bên dưới
ghi phiên bản Java đã dùng ở thời điểm kiểm chứng; Enforcer hiện yêu cầu `[23,24)`:

```powershell
rtk proxy .\mvnw.cmd -B -ntp verify
```

Gate chạy fast suite và check sẵn có: Enforcer, Spotless, JaCoCo, SpotBugs.
Không skip Enforcer khi máy dùng JDK khác; cấu hình JAVA_HOME/SDK Maven trong IDE.

## Chạy theo nhóm

```powershell
rtk proxy .\mvnw.cmd -B -ntp '-Dtest=TicketJsonTests,TicketContractTests' test
rtk proxy .\mvnw.cmd -B -ntp '-Dtest=EntityBehaviorTests,MapperTests' test
rtk proxy .\mvnw.cmd -B -ntp '-Dtest=SecurityConfigTests,KeycloakRealmRoleConverterTests,HttpErrorHandlingTests,TicketLoggingTests' test
rtk proxy .\mvnw.cmd -B -ntp '-Dtest=RuntimeConfigurationTests,TicketRabbitErrorHandlerTests,TicketServiceApplicationTests' test
```

| Nhóm | Kiểm được |
|---|---|
| JSON/contract | Wire types/nullability/coercion, totals/selection, cancellation rỗng và ID/version, probe union |
| Entity/mapper | Helper aggregate, inbox key, safe view/counts, outbox metadata round-trip, typed JSON |
| Security | JWT signature/issuer/expiry, role, route/method, Bearer challenge, stateless/default deny |
| HTTP/logging | Input/return validation, framework status/header, safe Problem/correlation, actual native formatter với sentinel |
| Rabbit/context | Shared converter, native fatal reject kể cả x-death, safe log, config binding, không declare topology, feature bean graph |

JWT key/JWKS HTTP server chỉ sống trong test process. Context test mock **6 repository
và JwtDecoder** để không gọi hệ thống ngoài; security tests riêng dùng token ký thật
và decoder thật. Mapper/Validator/JsonMapper được kiểm bằng implementation thực.

## Giới hạn

- Fast suite không cần Config Server/Eureka/PostgreSQL/Rabbit/SMTP thật.
- Mock repository không chứng minh JPA SQL/JSONB/bytea round-trip, optimistic locking,
  FK/unique/index hoặc quyền DB. Scaffold chưa thêm integration profile.
- Check parity DDL là review mapping, không phải runtime proof.
- Chưa kiểm phát vé/QR/SMTP, race check-in/cancel, inbox dedupe, relay/ACK/confirm hoặc Saga.
- JVM/Mockito có cảnh báo self-attachment; Spring Cloud có cảnh báo default cache.
  Không thêm dependency cache chỉ để che cảnh báo trong task scaffold.

Smoke theo [HANDOFF](HANDOFF.md) khi hạ tầng sẵn sàng. Nếu cần ghi fixture kiểm persistence,
dùng PostgreSQL test riêng áp đúng DDL/prefix; không insert/delete fixture vào DB triển khai.
Chỉ báo smoke/round-trip đã qua khi có kết quả thật.

Kết quả ngày 26/10/2026: Java 21 `verify` BUILD SUCCESS, **95 tests**, 0 failures/errors/skips;
Spotless sạch, SpotBugs 0 finding. Đây là fast suite, không phải runtime/persistence proof.

## Kiểm tra resource server 119

Đã dùng credential runtime để kết nối thực tới `3.106.109.119`, PostgreSQL qua SSH.
Xác nhận database/schema/role và quyền đọc 7 bảng; Hibernate schema validation đã qua
với annotation domain `nonempty_text` khớp DDL. RabbitMQ AMQP login/channel và 3 queue/binding
Ticket đã đối chiếu; discovery/JWKS Keycloak trả 200 với 4 assertion đạt.
Ticket boot với resource thật và health 200 / UP.

Probe dùng Java 21 với `-Duser.timezone=UTC`: JDBC bị server từ chối nếu JVM gửi `Asia/Saigon`.
Config Client/Eureka tắt và SMTP chưa cấu hình được loại riêng trong probe; central YAML
được nạp trực tiếp. Đây chưa phải kiểm tra toàn bộ stack hay persistence round-trip.
Không ghi dữ liệu nghiệp vụ, publish/consume message hoặc sửa schema/topology.
