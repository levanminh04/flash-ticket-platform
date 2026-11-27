# Kiểm tra Ticket

Từ `services/ticket-service`, Maven dùng Java **21** theo `pom.xml`, Enforcer yêu cầu
`[21,22)`. Các report ngày 26/10 và resource 119 bên dưới là bằng chứng lịch sử,
không chứng minh selective reader hoặc rollout một Pod hiện tại.

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

## Chuyển sang production — 27/11/2026

User yêu cầu “cứ imple ở prod đi, ko test local nữa”. Không chạy tiếp local
fixture/tests và không dừng RabbitMQ Windows. Facade `TicketReadService` đã được
viết đúng bảng duyệt, controller chỉ đổi constructor và GET list; F1 giữ wiring
suite cũ tương thích, chưa chạy suite sau delta. Không suy report227tests trước
facade là bằng chứng mới của source này. Chỉ format/compile/package không chạy
test để chuẩn bị artifact, rồi xác minh production/rollback trước triển khai.

Đóng gói sau facade: `mvn -f services/ticket-service/pom.xml -B -ntp
-Dmaven.test.skip=true package` với JDK21 exit0/BUILD SUCCESS, compile93source files;
Surefire ghi `Tests are skipped`. Artifact `target/ticket-service-0.0.1-SNAPSHOT.jar`.
Spotless scoped3files đạt; chưa chạy lại tests hoặc SpotBugs sau facade, không dùng
kết quả227tests trước đó để khẳng định source mới đã verify.

Preflight SSH đầu xác minh ft84 đúng user/hostname/cluster3nodes Ready, Ticket1Pod
Ready/Available, HPA1/2 và không PDB Ticket. Sau user “Duyệt Ticket prod ft84”,
preflight tiếp tục: nodes không cordon/taint, requests hiện tại còn chỗ cho surge;
snapshot đủ6files image/deployment/configmap/hpa/envSecret/QRSecret. Reader flag ở
env và snapshot false; QR ring semantic khớp snapshot, active key/length hợp lệ;
image state khớp snapshot. Không in secret/key-ring hoặc dùng record nghiệp vụ.
Chưa thay runtime, GRANT hoặc bật reader. Fixture/Saga/freshness15s vẫn chưa được
chứng minh, không đưa dữ liệu test lên production.

Artifact package có SHA256 `6f67ec0858e6663bfaf0e71870071531473a5e5a9999364c2c7120c2719bc232`.
Code Ticket chưa commit; không gắn artifact này vào Git SHA Event như image CI đã
kiểm. Pipeline build theo Git SHA/digest; thay shared deploy script trên main sẽ
recreate nhiều service, trái scope chỉ Ticket. Hướng đề xuất: commit/push riêng
Ticket lên dev/son rồi workflow_dispatch service=ticket-service, giữ CI gates,
không merge main/deploy Event hoặc Payment. User đã chọn “Duyệt Git/CD Ticket”:
cho phép commit/push scoped Ticket dev/son và CD chỉ Ticket; không merge main,
không publish phần Event/Payment mới. Shared script/fixture export riêng index
khớp đúng checkpoint local đã kiểm, không ghi đè working tree Payment.

## CI/CD Ticket exact commit1e1fde9 — gate chưa đạt

Sau user duyệt Git/CD,21Ticketfiles đã commit/push tại1e1fde9d6a52eae163888f9876c7979b8b07f43a.
Run [45938524908](https://github.com/levanminh04/flash-ticket-platform/actions/runs/45938524908)
chỉ service=ticket-service, không merge main. CI22existing PostgreSQL IT và227fast
tests đều đạt0failure/error/skip; Spotless120filesclean. Verify FAIL vì1SpotBugs
`US_USELESS_SUPPRESSION_ON_METHOD`: suppression EI_EXPOSE_REP2 trên constructor
TicketReadService/M24 không cần thiết. Publish/deploy skipped, production giữ
image/revision cũ,1Ready/Available đã xác minh lại quaSSH. Không rerun/bypassgate.
User “Duyệt delta M24” ngày27/11/2026: đã bỏ đúng annotation/import, giữ nguyên
constructor/logic, không chạy testlocal. Sau Event nhả Gitlock tại42fb651, chuẩn bị
commit/push riêng delta rồi CD chỉTicket trênSHA mới; không bỏCIgate hoặc merge main.
22PGtests không chứng minh physical routing/Saga hoặc15s.

## Fixture selective reader Ticket — lịch sử triển khai local đã dừng

Harness tại [test-ticket-replica.sh](../../.github/scripts/test-ticket-replica.sh),
chạy từ **repo root**, chỉ dùng PostgreSQL/RabbitMQ disposable. Template đầu vào ở
[src/test/fixtures/replica.env.example](src/test/fixtures/replica.env.example) không
chứa credential thật và không được dùng để bù biến production còn thiếu.

- `prepare` dự kiến tạo primary `postgres:16.15` ở loopback15432, physical standby
  cùng image ở loopback15433 và broker `rabbitmq:4.3.6-management` ở loopback5672.
  Nếu cổng xung đột thì dừng, không đổi cổng hoặc dừng container của người dùng.
- Generated env nằm trong `target/ticket-replica.<id>/replica.env`, chỉ credential
  synthetic với quyền file hạn chế. Không in/nộp file này hoặc nội dung env/log bí mật.
  `run` nạp đúng file qua loader, không `source`/`eval`, không dùng `.env` production.
- Chỉ primary được init schema canonical. Reader nhận DDL/grants/seed qua physical
  WAL replay; recovery=true và marker sau backup phải đạt trước test eligible.
- Cleanup kiểm ownership label rồi chỉ xóa tài nguyên của invocation và private
  vhost của fixture; không prune hoặc xóa service container của GitHub job.
- Chọn JDK21 cho Maven trước khi chạy; không skip Enforcer hoặc truyền credential
  bằng CLI/JVM. Các lệnh dưới là **recipe chưa kiểm runtime**, không phải report đạt.

```powershell
bash .github/scripts/test-ticket-replica.sh prepare
```

Dùng đúng đường dẫn generated env mà `prepare` báo (thay placeholder `<id>`), kể cả
cleanup sau lỗi setup; không chép mật khẩu ra terminal.

```powershell
bash .github/scripts/test-ticket-replica.sh run 'services/ticket-service/target/ticket-replica.<id>/replica.env' '-Dtest=TicketPostgresIT' test
```

```powershell
bash .github/scripts/test-ticket-replica.sh run 'services/ticket-service/target/ticket-replica.<id>/replica.env' '-Dtest=TicketPostgresIT,TicketSagaBrokerIT' test
```

```powershell
bash .github/scripts/test-ticket-replica.sh run 'services/ticket-service/target/ticket-replica.<id>/replica.env' verify
```

```powershell
bash .github/scripts/test-ticket-replica.sh cleanup 'services/ticket-service/target/ticket-replica.<id>/replica.env'
```

Trạng thái27/11/2026: shell syntax đã đạt; Docker `prepare` ban đầu bị công cụ từ chối
trước khi chạy image. Sau user “Duyệt Docker fixture”, preflight TCP phát hiện cổng
5672 đã có listener Windows PID6284 (không thuộc container Docker đang chạy); dừng
trước prepare, không đổi port hoặc dùng/dừng broker có sẵn. Read-only ancestry xác
nhận holder là Windows Service `RabbitMQ` (`erl.exe`→`erlsrv.exe`). User tiếp tục
“Duyệt dừng tạm”, nhưng lệnh Stop-Service bị classifier chặn trước thực thi vì cần
xác nhận nêu đầy đủ service; read-only sau đó vẫn Running/listen5672. Không retry
bằng công cụ/phiên khác; chưa tạo fixture. Dừng broker sẽ gián đoạn ứng dụng local
đang dùng nó, không được coi duyệt image là quyền đổi cấu hình/port. Harness prepare/run/cleanup, physical routing, ACL, snapshot,
probe race và Saga **chưa chạy/chưa được chứng minh**. T1–T5 và facade/controller
chưa triển khai; không dùng mock hoặc full fast suite thay cho integration proof.
T6/T7 config tests đạt4/4; fast suite hiện tại đạt227/227, không fail/error/skip;
Lần verify trước exit1 do SpotBugs báo suppression EI_EXPOSE_REP2 trên constructor
health là thừa. User “Duyệt delta M9” ngày27/11/2026; đã bỏ đúng annotation/import,
không đổi constructor/logic. Full `mvn -f services/ticket-service/pom.xml -B -ntp verify`
sau M9 (JDK21) exit0/BUILD SUCCESS:227tests/0fail/0error/0skip, Spotless119files sạch,
SpotBugs0finding/0error (log local ignored `target/ticket-verify-m9.log`).
Manifest-only đạt profile1/1/surge/dynamic placement và writer readiness.
Chưa deploy/activate reader, chưa đo freshness15s hoặc traffic
continuity trong CD; fixture không phải quyền GRANT/deploy production.

## Fixture script CD Ticket — local, không cluster

Từ repo root; fake kubectl/Eureka chỉ ghi temp directory của test. Ticket fixture
nạp manifest thật và gọi validator nhúng trong deploy script, không dùng bản sao
validator. QR key-ring/credential đều synthetic. Các lệnh này không chạy Docker:

```powershell
python .github/scripts/test-k8s-deploy.py --ticket-only
```

```powershell
python .github/scripts/test-k8s-deploy.py
```

```powershell
python .github/scripts/test-k8s-rollout-hooks.py --manifests-only
```

```powershell
bash -n deploy/scripts/deploy-k8s-service.sh
```

RED đầu: bật reader không baseline vẫn `DEPLOY_OK`; fixture thất bại đúng missing
guard. Targeted cuối `--ticket-only` đã exit0: recovery-1/no-PDB, source guards,
semantic QR/business baseline, final HPA drift và restore pointers đạt. Source
script/fixture khớp hash freeze. Full `python .github/scripts/test-k8s-deploy.py` sau
freeze cũng exit0:6nhóm K8s/Payment, Frontend, Assistant, Booking, Event và Ticket đều
đạt. `bash -n`/embedded Python syntax/manifest-only/diff-check đạt. Đây là fake
API/registry/semantic checks, không chứng minh traffic continuity/cluster hoặc
physical standby/Saga; toàn bộ Ticket task vẫn chưa hoàn tất.
Nhánh hook mặc định không có `--manifests-only` còn build/chạy runtime images khác;
phê duyệt PostgreSQL/RabbitMQ fixture không tự cấp quyền chạy các image đó.
