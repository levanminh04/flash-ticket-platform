# Deploy User Service lên AWS #2

Workflow và script dưới đây chỉ deploy User Service; Config Server được build và
triển khai riêng. Workflow tự chạy khi push thay đổi thuộc User/deploy User hoặc
workflow này lên `dev/tuyen`, bỏ qua thay đổi chỉ có file `.md`; vẫn hỗ trợ chạy thủ công.

## Bố cục source trong deploy/user-service

- [docker-compose.user.yml](docker-compose.user.yml): Docker Compose của User.
- [deploy-user.sh](deploy-user.sh) và [deploy-user.py](deploy-user.py): triển khai,
  kiểm tra và rollback; [test_deploy_user.py](test_deploy_user.py): kiểm thử script.
- [start-user-tunnel.sh](start-user-tunnel.sh),
  [flash-ticket-user-tunnel@.service](flash-ticket-user-tunnel@.service) và
  [user-tunnel.env.example](user-tunnel.env.example): tunnel Mongo/Config.
- [user.env.example](user.env.example): biến môi trường runtime của User.
- [user-service.yaml](../../infrastructure/config-server/src/main/resources/config/user-service.yaml):
  nguồn cấu hình User duy nhất, được đóng gói khi build Config Server.

Tên Compose/script giữ quy ước theo dev/son; các file riêng của User được gom
trong `deploy/user-service/`. Workflow GitHub vẫn ở `.github/workflows/deploy-user.yml`.
Đã hợp nhất bản YAML đã kiểm chứng trước đây trong deploy về file nguồn trên.
Không duy trì bản YAML thứ hai trong deploy. Việc đổi tên/source local không tự
thay bản đang chạy trên AWS; release và snapshot rollback cũ trên server giữ nguyên.

## Chuyển MongoDB sang AWS #2

MongoDB được quản lý riêng bằng [bộ deploy Mongo](../mongodb/README.md), theo target B11-C/B12.
User trên AWS #2 dùng `USER_MONGO_URI` tới `127.0.0.1:27017`, giữ nguyên credential/database/authSource.
Đặt `USER_MONGO_TUNNEL_ENABLED=false` trong tunnel.env; systemd chỉ forward Config AWS #1 qua 18888.
`USER_MONGO_TUNNEL_ENABLED` mặc định `true` trong launcher để tương thích cấu hình tunnel cũ;
mẫu cấu hình mới chọn `false` cho topology Mongo cùng AWS #2.

Từ IDE/laptop, mở tunnel 17017 tới Mongo AWS #2, thay đích SSH AWS #1 trước đây.
Config Server tiếp tục dùng User YAML hiện có; không cần sửa hoặc rebuild Config.
Các đoạn rehearsal/rollout đầu dưới đây ghi Mongo AWS #1 là **lịch sử trước migration**.

## Trạng thái ghi nhận tại đợt triển khai ngày 01/11/2026

Đã deploy commit `209381b3fec1d3bd4aff88fa1d4e837769344791` bằng image bất biến
chuyển qua SSH, không publish GHCR hoặc push Git. Các file Markdown không nằm
trong commit theo yêu cầu. API trên AWS #2 bind `127.0.0.1:8085`; Eureka tắt,
identity consumer và recovery bật. Health UP, token máy thật đọc được profile
Mongo; tunnel systemd đã kiểm tra tự phục hồi và được enable khi boot.

- Project Docker AWS #2: `flash-ticket-user`.
- Release: `/home/ubuntu/flash-ticket-user/deploy/user-service/releases/<commit>`.
- Environment riêng: `/home/ubuntu/flash-ticket-user/runtime/.env`, quyền 0600.
- Tunnel: `flash-ticket-user-tunnel@ubuntu.service`; key/known_hosts và tunnel.env
  tại `/home/ubuntu/.config/flash-ticket-user`, key được giới hạn source và permitopen.
- Runtime principal Mongo/Rabbit: `user_service_runtime`, giới hạn collection/queue
  của User. Credential thật chỉ nằm trong file riêng trên server.
- Config AWS #1: thay đúng `BOOT-INF/classes/config/user-service.yaml` trong JAR
  từ image đang chạy. Hash từng entry khác giữ nguyên; Booking response hash và
  container không đổi. Compose runtime tại
  `/home/ubuntu/flash-ticket-user-config/compose.active.json`, bản khôi phục tại
  `/home/ubuntu/flash-ticket-user-config/rollback.compose.json`.

Đã xác minh native Config không loại field cũ chỉ bằng file overlay: nó gộp
cả User YAML classpath. Vì vậy đợt deploy này dùng image có đúng một file được
thay thế, giữ nguyên các class và dependency. Khi dev/son rebuild/redeploy Config,
cần giữ nội dung User YAML đã chốt để tránh khôi phục cấu hình cũ.

Queue User có 1 consumer, 0 ready/unacked sau deploy: 4 message được lưu và ACK,
1 message tồn đọng không hợp lệ được chuyển sang `user.identity.dlq`. Message DLQ
được giữ nguyên, chưa đọc nội dung, purge hoặc replay.

Trên AWS #2 có thể kiểm tra bằng:

```bash
curl --fail http://127.0.0.1:8085/actuator/health
systemctl status flash-ticket-user-tunnel@ubuntu.service
docker ps --filter label=com.docker.compose.project=flash-ticket-user
```

Script deploy hỗ trợ `--local-image sha256:<image-id>` cho image đã kiểm chứng
archive hash/revision và load qua SSH; không chấp nhận mutable tag. Đường workflow
GHCR vẫn dùng repository digest và chưa được chạy. Artifact rollout chính thức:
`services/user-service/target/deploy-official-20261101/result.json`,
`aws1-final.json`, `aws2-final.json`; test script deploy hiện có 6 test PASS.

## Phương án đã kiểm thử ngày 01/11/2026

- User chạy Docker trên AWS #2, Java 21, UID 10001, root filesystem read-only,
  giới hạn RAM mặc định 384 MiB; API chỉ nghe loopback.
- MongoDB ở AWS #1: tunnel AWS #2 `127.0.0.1:17017` đến AWS #1
  `127.0.0.1:27017`.
- Config ở AWS #1: tunnel AWS #2 `127.0.0.1:18888` đến cổng Config loopback.
  Khi rehearsal, đích là Config tạm cổng 18889; khi rollout chính thức đích là
  Config chung cổng 8888 sau khi cập nhật cấu hình User.
- RabbitMQ và Keycloak dùng endpoint có thể truy cập từ AWS #2, cung cấp qua
  biến môi trường. Eureka tắt trong đợt deploy độc lập này.

Hai VPC hiện trùng CIDR. Rehearsal xác nhận đường SSH qua địa chỉ public;
không chứng minh private routing, HA, tải thực tế hoặc an toàn production.

## Điều kiện trước rollout chính thức

1. Phối hợp người phụ trách Config Server để cập nhật riêng `user-service.yaml`:
   override `USER_*`, Mongo/Rabbit và virtual host, JWT issuer/JWKS/audience.
   Bỏ `logging.structured.json.add.serviceName` và các metadata trùng với
   `UserLogCustomizer`. Config đang chạy đóng gói native YAML trong image,
   nên sửa file local không tự làm server đổi cấu hình. Build Config Server từ
   [file nguồn chính](../../infrastructure/config-server/src/main/resources/config/user-service.yaml).
   Workflow User không tự build hoặc deploy Config. Các rehearsal ban đầu dùng
   Config tạm; đợt rollout 01/11 đã thay riêng User YAML như phần lịch sử ở trên.
2. Cài tunnel bền vững bằng systemd trên AWS #2, tự khởi động lại, bind loopback,
   kiểm tra host key và `ExitOnForwardFailure=yes`. Dùng key riêng chỉ được
   port-forward từ AWS #2 đến Mongo/Config; không dùng key quản trị làm key runtime.
   Tunnel/key tạm của rehearsal đã được gỡ sau kiểm thử.
3. Cấp Mongo runtime principal cho `user_db` theo quyền đã mô tả trong
   [database của User](../../services/user-service/database), có validator/index
   trước khi chạy. Không chạy initializer hoặc cấp quyền quản trị để deploy.
   Cấp Rabbit principal theo queue cần dùng; không dùng tài khoản quản trị broker.
4. Tạo file môi trường ngoài source trên AWS #2, quyền `0600`, dựa trên
   [user.env.example](user.env.example). Điền credential trên server;
   không đưa file thực vào Git, artifact CI hoặc output của `docker compose config`.
5. Đợt smoke đầu để `USER_IDENTITY_ENABLED=false`, `USER_RECOVERY_ENABLED=false`.
   Sau khi xác nhận principal, queue và Keycloak Admin API mới bật cả hai để
   vận hành đủ luồng. Bật consumer queue chung sẽ xử lý message đang tồn đọng.

### Cấp role đồng thời và recovery

User dùng `version` làm mã phân biệt lần xử lý và `roleGrant.attemptedAt` làm
mốc thời hạn xử lý; giữ schema hiện có. API tạo claim ban đầu sở hữu lần cấp role.
API retry/worker chỉ giành claim bằng CAS khi lần trước đã UNKNOWN hoặc hết hạn.
Mọi cập nhật ACTIVE/UNKNOWN đều kiểm tra version, nên lần xử lý cũ không ghi đè
lần mới. Admin khởi tạo và operationId giữ nguyên khi recovery.

- `USER_ROLE_GRANT_LEASE_DURATION` mặc định `60s`. Đặt lớn hơn tổng thời gian
  dự kiến của chuỗi gọi Keycloak: lấy token, đọc role, available roles, ghi,
  xác nhận, làm mới token và đối soát. `USER_KEYCLOAK_ADMIN_TIMEOUT` mặc định
  `5s` là timeout mỗi HTTP request, không phải toàn chuỗi.
- `USER_ROLE_GRANT_WAIT_TIMEOUT` mặc định `5s`: API đang cạnh tranh chờ kết quả
  trong giới hạn này. Chỉ trả thành công khi ACTIVE; còn PENDING trả 503 để retry.
- Sau lỗi Keycloak, đọc lại role đúng subject; chỉ hoàn tất khi xác nhận đủ
  BUYER và ORGANIZER. Thiếu quyền hoặc không đọc được vẫn là lỗi có thể retry.

Lease không bảo đảm đúng một lời gọi Keycloak trong mọi tình huống: tiến trình
bị treo quá lease có thể sống lại sau khi worker tiếp quản. CAS vẫn chặn ghi
Mongo từ version cũ; đối soát role xử lý kết quả upstream chưa rõ. Không dùng
khóa chỉ trong RAM, nên cơ chế giành claim áp dụng cả giữa các process.

## GitHub Actions

[deploy-user.yml](../../.github/workflows/deploy-user.yml) verify Maven trước khi
build/publish image, rồi deploy đúng digest. Trigger `push` chỉ áp dụng trên
`dev/tuyen` với `services/user-service/**`, `deploy/user-service/**` hoặc chính
workflow này, loại trừ file `.md`. Có thể chạy thủ công bằng `workflow_dispatch`.
Thay đổi YAML tại Config Server cần rollout Config riêng, không kích hoạt workflow User.

| Loại | Tên | Nội dung |
|---|---|---|
| Secret | `FT84_SSH_PRIVATE_KEY` | Key deploy AWS #2, định dạng OpenSSH |
| Variable | `FT84_SSH_KNOWN_HOSTS` | Host key AWS #2 đã xác minh ngoài băng |
| Variable | `FT84_SSH_HOST`, `FT84_SSH_USER`, `FT84_SSH_PORT` | Đích SSH AWS #2 |
| Variable | `USER_DEPLOY_DIRECTORY` | Thư mục dưới home user, kết thúc bằng `/deploy/user-service` |
| Variable | `USER_DEPLOY_ENV_FILE` | File dưới home user, kết thúc bằng `/.env`, quyền 0600 |

`USER_DEPLOY_DIRECTORY` là vị trí release riêng đã provision trên server, độc lập
với bố cục source repository. Gom file source vào thư mục con không đổi vị trí này.

User SSH phải chạy được Docker Compose v2 và Python 3 trên Linux. Workflow dùng
`GITHUB_TOKEN` cho GHCR; package phải cho repo quyền đọc tương ứng. Đường CI/GHCR
chưa được chạy trong rehearsal, chưa xác minh quyền registry/repository thực tế.
Không kích hoạt workflow trước khi hoàn tất điều kiện rollout phía trên.

## Script và khôi phục

```bash
bash deploy/user-service/deploy-user.sh \
  ghcr.io/levanminh04/flash-ticket-platform/user-service@sha256:<digest> \
  --revision <full-commit-sha> \
  --env-file /home/<user>/<runtime-directory>/.env
```

Script chặn image không đúng digest, API không bind loopback, port đã bị chiếm,
Config thiếu audience hoặc còn metadata log trùng. Lock theo project và port
ngăn deploy đồng thời, kể cả khi hai lệnh chỉ định hai file môi trường khác nhau.
Biến runtime trong shell không được ghi đè file môi trường đã kiểm tra.

Sau khi pull, script đọc label `org.opencontainers.image.revision` từ chính image,
yêu cầu SHA Git đủ 40 ký tự và đối chiếu `--revision` nếu được truyền vào.
Workflow luôn truyền SHA đang build; label thiếu/sai hoặc không khớp sẽ dừng trước
khi thay container. Lệnh deploy thủ công có thể bỏ `--revision` để dùng SHA trong
label, nhưng image vẫn bắt buộc có label hợp lệ.

Script tự đặt `USER_SERVICE_VERSION` theo label đã xác minh, bỏ qua giá trị cũ
trong `.env` hoặc shell và không sửa file `.env` gốc. Snapshot lưu version này;
sau khi health UP, script kiểm tra cả image ID và version trong container trước
khi ghi nhận thành công. Log `serviceVersion` đọc cùng biến môi trường đó.
Kết quả deploy và `last-good.json` có trường `revision` để đối chiếu.

Mỗi release lưu bản Compose và môi trường quyền 0600 tại
`~/.flash-ticket-user-deploy/<project>/release-*`. Khi thay container hoặc kiểm tra
health/image thất bại, script khôi phục image, Compose và môi trường của release
tốt trước đó; lần đầu thất bại thì dừng service vừa tạo. Không tự tiếp quản container
được tạo ngoài bộ deploy mà thiếu snapshot. Snapshot chứa secret: bảo vệ cả thư mục,
giữ release cần rollback và dọn bản hết hạn theo chính sách vận hành.
Rollback dùng version trong snapshot cũ, kể cả snapshot tạo trước khi bổ sung
trường `revision`; không lấy version của image vừa deploy thất bại.

## Bằng chứng kiểm thử

Kiểm tra bổ sung cho version tự động ngày 01/11/2026: **15 test deploy PASS**
trên Linux với Docker/HTTP giả lập; cú pháp Bash của script và các bước workflow
hợp lệ. Docker Compose local render đúng version bằng fixture. Đã kiểm tra
`.env`/shell còn version cũ, thiếu/sai label, SHA workflow không khớp, version
container bị lệch và rollback về snapshot cũ. Chưa chạy workflow GHCR thực tế
hoặc redeploy AWS cho bản sửa version này.

Các lần chạy Docker tạm trên AWS #2 kết nối Mongo và Config tạm ở AWS #1 qua SSH.
Mỗi lần dùng database, Rabbit exchange/queue/user riêng; không đọc queue chung.

- JAR Java 21 sau sửa role grant: Maven verify **59 test PASS**, Spotless PASS,
  SpotBugs 0; tăng từ 51 test ở rehearsal ban đầu.
- JWT machine do Keycloak thật cấp: internal lookup 404 khi không có profile;
  API người dùng 403; thiếu token 401.
- Identity consumer nhận hai message trùng, ACK cả hai, không nhân đôi projection.
- Organizer apply/approve/reject, duyệt lại, recovery projection, follow/unfollow
  lặp; Keycloak Admin API thật và Cloudinary upload logo/banner thật.
- JWT thiếu `exp` và hết hạn bị từ chối; restart tunnel/container phục hồi health.
- Actor BUYER/ADMIN/ORGANIZER dùng JWT ký bằng fixture JWKS cô lập; không coi đó
  là bằng chứng login người dùng thật hay luồng qua Gateway.
- 5 test đường lỗi script PASS trên Linux với Docker/HTTP giả lập; kiểm tra rollback
  image/Compose/môi trường, image sai, shell override và lock project.

Trước bản sửa, một lần chạy bị dừng ở API approve trả `503 ROLE_GRANT_UNAVAILABLE` khi recovery
worker cũng cấp role cho cùng user. Log Keycloak xác nhận trùng khóa
`USER_ROLE_MAPPING`; worker đã xác nhận thành công. Đây là tình huống cần retry
theo contract cũ. Bản sửa dùng CAS/lease và đối soát mô tả phía trên; xem
[báo cáo rehearsal](PREDEPLOY-REPORT.md) để phân biệt kết quả trước/sau sửa.

Sau sửa, test AWS trả 200/ACTIVE cho cả 4 approve đồng thời, ghi nhận đúng 1 lần
thực hiện cấp role. Worker phục hồi hồ sơ có attemptedAt quá hạn, giữ admin gốc
và dựng lại projection trên Mongo có validator/index clone từ server.
Artifact mới ở `services/user-service/target/role-grant-aws-20261101/` gồm
`result.json`, `postflight-aws2.json`, `assert-results.py`.

Artifact local tại `services/user-service/target/predeploy-20261101/` gồm
`result-first.json`, `result-concurrent-503.json`, `result.json`,
`keycloak-error-diagnosis.json`, `guard-tests-green.json`, `postflight-aws2.json`.
Artifact trong `target` không được đưa vào Git và không thay tài liệu kiến trúc đã duyệt.
