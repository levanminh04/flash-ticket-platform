# Booking → GHCR → server119 — plan v0.1

Design: [v0.1](../specs/2026-10-29-booking-github-actions-deployment-design.md), đã chốt để lập plan khi user trả lời **“continue”** sau câu hỏi duyệt spec/viết plan ngày29/10/2026. User yêu cầu **“triển khai theo plan này”** kèm skill executing-plans: nguồn chốt plan và chuyển thực thi F1–F5. Không có class/method Java mới cần gate. Deploy đã kiểm chứng; user sau đó bỏ yêu cầu nút Run workflow UI, trạng thái cuối ở §9 thay thế yêu cầu UI/main ở các bước lịch sử bên dưới.

## 1. Kết quả cần đạt

Push `dev/son` có thay đổi liên quan → verify → build/push Booking23 + Config Server21 lên GHCR → SSH119 pull hai digest → Config Server UP → Booking UP. Có chạy thủ công, kiểm version thật và hướng dẫn rollback. Mỗi service vẫn một Compose trong deploy, Dockerfile ở service/infrastructure hiện có.

Workspace `D:/DATN/flash-ticket-platform`, branch `dev/son`; server root `/home/ubuntu/flash-ticket-platform`. Không chuyển workspace sang worktree lưu trữ. Không thay source Java, DB contract, các service khác hoặc CI tổng của main.

## 2. Danh sách thay đổi

| ID | File | Cách triển khai |
| --- | --- | --- |
| F1 | `.github/workflows/deploy-booking.yml` — NEW | Hai job build_publish/deploy, native SSH/scp và official Docker actions |
| F2 | `deploy/docker-compose.booking.yml` — EXISTING | image ref, env runtime, giới hạn512m/heap60%; giữ local build |
| F3 | `deploy/docker-compose.config-server.yml` — EXISTING | image ref, loopback8888, giới hạn256m/heap55%; giữ local build |
| F4 | `deploy/deploy-booking.sh` — NEW | Nhận hai digest và registry username; token qua stdin; pull/up/probe/cleanup |
| F5 | `deploy/README.md` — EXISTING | GitHub settings, bootstrap env, cách chạy, kiểm tra và rollback |

**Bảng class C:** không có type Java mới/sửa. **Bảng method M:** không có constructor/method Java mới/sửa, kể cả test hoặc Lombok. Không thêm annotation, Java dependency hoặc mapper/transaction/validation mới.

Shell helper duy nhất dự kiến: `wait_for_health(url, label)` trong F4, nhận hai đối số shell, exit0 khi HTTP health trả status UP, nonzero khi quá hạn180s. Dùng curl/jq sẵn trên119, không viết lại parser hoặc thêm health endpoint. Đây là shell infrastructure, không coi là phê duyệt mở để thêm helper Java.

## 3. Task1 — Compose và script deploy (F2–F4)

1. Thêm `image: ${BOOKING_IMAGE:-flash-ticket/booking-service:local}` và `image: ${CONFIG_SERVER_IMAGE:-flash-ticket/config-server:local}`; giữ build context/Dockerfile để lệnh local `up --build` vẫn dùng được.
2. Booking giữ host network/8082, env_file `../.env`, log rotation/restart hiện có. Compose environment override CONFIG_SERVER_URL về127.0.0.1:8888, EUREKA_CLIENT_ENABLED=false và JAVA_TOOL_OPTIONS heap60%/UTC; mem_limit512m. Không sửa YAML nguồn dùng bởi IntelliJ để tắt Eureka chung.
3. Config Server giữ native classpath config trong image; environment SERVER_ADDRESS=127.0.0.1, heap55%/UTC, mem_limit256m. Không chạy Discovery/Gateway trong task này.
4. F4 chạy từ root server, fail-fast với `set -Eeuo pipefail`. Interface: `bash deploy/deploy-booking.sh BOOKING_IMAGE CONFIG_SERVER_IMAGE REGISTRY_USERNAME`; token GHCR là stdin, không phải argv. Kiểm hai image ref đúng namespace/digest SHA256 trước Docker mutation.
5. Script kiểm root `.env`, công cụ Docker Compose/curl/jq, port8082/8888 thuộc đúng project hoặc đang trống; ghi hai image ref đang chạy nếu có. Không in env/Compose đã resolve hoặc raw log chứa secret.
6. Tạo DOCKER_CONFIG bằng mktemp riêng, umask077; trap cleanup đúng thư mục vừa tạo khi thoát. `docker login ghcr.io --password-stdin`, export hai image ref, rồi pull cả hai trước bất kỳ up nào.
7. Với từng file Compose riêng: Config Server `up -d --no-deps --no-build config-server` → wait_for_health → kiểm `/booking-service/default` có propertySources bằng jq không in body → Booking `up -d --no-deps --no-build booking-service` → wait_for_health. Nếu bước trước fail thì dừng bước sau.
8. Ghi ref/container state/RestartCount/OOMKilled và RAM/swap, kết quả health; chỉ ghi summary thành công sau hai probe đạt. Không chạy down, prune, xóa volume/queue hoặc tự publish event nghiệp vụ.

**Kiểm:** `bash -n`, ShellCheck nếu có; Docker Compose `config --quiet` với env fixture không bí mật; kiểm image ref được nội suy nhưng không dump credential. Thử thiếu/sai digest để xác nhận script dừng trước login/pull/up. Với server layout chỉ có Compose/script/env, xác nhận `pull` và `up --no-build` không cần source build context. Không đánh dấu các check đã đạt trước khi chạy.

## 4. Task2 — Workflow verify/build/push (F1)

- Trigger push dev/son, path filter: `services/booking-service/**`, `infrastructure/config-server/**`, F1–F4. Thêm workflow_dispatch, guard ref dev/son cho cả hai job; timeout hữu hạn, concurrency cố định cho119, cancel-in-progress=false.
- Job build_publish: checkout chính xác github.sha; Java23, Maven verify Booking với profile integration hiện có. RabbitMQ service container tạm4.3.6 có healthcheck, port5672 và credential dùng riêng CI; các env RABBITMQ_* chỉ tới container này. PostgreSQL/Rabbit fault/race IT tiếp tục dùng Testcontainers hiện có. Không skip smoke test hoặc sửa Java để CI pass.
- Chuyển Java21 rồi Maven verify Config Server. Dùng Maven trên runner, không gọi wrapper Booking đang thiếu `.mvn` trong Git. Đọc lại POM/test env khi thực thi nếu có thay đổi sau plan.
- Setup Buildx/login GHCR; contents:read/packages:write ở đúng job. Dùng path context của hai Dockerfile hiện có, platform linux/amd64, gắn tag commit/source label, build cache tách hai image. Pin official actions bằng commit đã xác minh khi tạo YAML, không đoán SHA.
- Push hai image dưới `ghcr.io/levanminh04/flash-ticket-platform/{booking-service,config-server}`; xuất đủ hai digest cho deploy. Nếu verify hoặc bất kỳ build/push lỗi thì job deploy không chạy. Không dùng latest để chọn release.

**Kiểm:** actionlint/YAML/schema, rà path/ref guard và permissions; chạy lại Maven đúng phiên bản Java trên môi trường có dependency test trước khi publish nếu cần. Build/push thành công chỉ được ghi sau khi có run thật và manifest digest ở GHCR; kết quả Maven local trước đây không thay thế kết quả runner.

## 5. Task3 — Bootstrap119 và GitHub settings (F5 + môi trường)

1. Kiểm tra lại SSH/RAM/swap/disk/ports và container đang chạy. Lần đọc mới nhất trong pha plan: RAM1907 MiB, available khoảng703 MiB, swap dùng289 MiB; chưa có Booking/Config Server,8082/8888 đang trống. Đây không phải capacity guarantee.
2. Tạo SSH key chuyên dụng, thêm public key song song key hiện có vào authorized_keys; đối chiếu known_hosts bằng kết nối đang tin cậy. GitHub secret FT119_SSH_PRIVATE_KEY; variables FT119_SSH_HOST/USER/PORT/KNOWN_HOSTS. Không yêu cầu gửi private key trong chat hoặc tự in token ra tool output.
3. Kiểm quyền Settings/Secrets/Actions và packages của repo bằng kết nối GitHub hợp lệ. Máy hiện chưa có gh CLI, không coi GCM Git push là bằng chứng có quyền quản trị Secrets. Chỉ hỏi user thực hiện bước còn thiếu nếu thực tế không có quyền/công cụ; không tuyên bố đã setup khi chỉ có tên secret trong docs.
4. Backup root env ở119 với mode600, cập nhật có chọn lọc: Booking JDBC127.0.0.1:5432/booking_db, Config URL/port, token URI, secret internal-service đang hoạt động. Đọc secret từ nguồn quản trị hiện có, không reset. Giữ nguyên credential hạ tầng và issuer public; dọn biến JWKS/audience cũ. Không copy env Windows hoặc tạo env service/deploy riêng.
5. Kiểm quyền Docker của deploy user, root/env mode600 và khả năng đọc package của GITHUB_TOKEN sau khi publish. Không cần PAT registry dài hạn nếu repository được cấp package access đúng.
6. Chỉ dừng process Booking local do agent khởi chạy khi đến bước chuyển chạy119; xác định PID/command line thực tế trước khi dừng. Hai instance cùng queue sẽ chia message; không suy consumer_count=1 nếu local còn chạy.

**Kiểm:** SSH bằng key mới thành công, key cũ vẫn còn; GitHub settings tồn tại mà không đọc lại giá trị secret ra log; config thiếu/credential sai được phát hiện trước deploy. Không chỉnh DB dữ liệu, quyền broker hoặc tắt container hạ tầng để lấy RAM.

## 6. Task4 — SSH job và publish workflow (F1/F4/F5)

- Job deploy cần build_publish thành công; contents:read/packages:read. Checkout đúng commit tạo image, cài key/known_hosts tạm trên runner; scp F2–F4 đến root deploy hiện có. StrictHostKeyChecking bật.
- Gọi F4 qua SSH với hai image@digest đã build và registry username; truyền GITHUB_TOKEN vào stdin của SSH để Docker login trong script đọc. Không bật shell tracing cho token/key và không đưa chúng vào command line.
- Ghi job summary commit, hai digest mới/cũ, health và kết quả deploy. Cleanup key/config tạm cả khi lỗi. Luồng deploy chỉ pull/up, không git pull hay build Maven trên119.
- Bàn giao/commit đúng source Booking muốn deploy: issuance-result đang uncommitted; không vô tình deploy HEAD cũ hoặc gom thay đổi người khác. Publish workflow/source liên quan lên dev/son ở pha thực thi; spec/plan hiện tại không tự commit/push.
- Để Run workflow xuất hiện, đưa riêng định nghĩa F1 lên default branch main qua thay đổi Git riêng có thể review, không merge toàn dev/son hoặc tự đổi default branch. Chọn dev/son khi dispatch; chọn main phải bị guard chặn. Việc làm trên main chưa được thực hiện bởi plan này.

**Kiểm:** quan sát một run push dev/son thực tế và một run thủ công sau khi định nghĩa có trên main; xác nhận cả hai dùng source/digest mong muốn. Không coi có YAML trên máy là đã có pipeline hoặc có nút dispatch.

## 7. Task5 — Nghiệm thu và docs (F5)

| Check | Kết quả phải thấy |
| --- | --- |
| Verify/build/push | Hai Maven verify thành công, hai image/digest tồn tại ở GHCR |
| Thứ tự startup | Config health UP + có Booking config trước khi up Booking |
| Image thật | Image ref/ID trong container khớp hai digest từ run, không dùng JAR local |
| Dependency119 | Booking health UP, DB/Redis/Rabbit thực sự kết nối, không lỗi Eureka do đã tắt ở deployment |
| Auth không ghi nghiệp vụ | Không token→401, token không có BUYER→403, BUYER hợp lệ gửi `{}` tới POST /api/v1/orders→400 validation; không tạo order hoặc thu tiền. Dùng token hiện có an toàn ở lần bàn giao, không thêm password Buyer vào GitHub Secrets |
| Consumer | Đối chiếu bốn queue đang dùng và instance thực tế; không tự consume/purge các output/DLQ test đang được giữ |
| Bộ nhớ | Không OOM/restart loop; ghi RAM/swap khi startup và sau probe. Limit512+256 chưa chứng minh chịu tải; nếu không đủ thì báo số đo và chưa kết luận ổn định |
| Network | Config chỉ loopback8888; Booking loopback8082 hoạt động. Chỉ báo URL public119:8082 dùng được nếu đã thử từ ngoài, phân biệt Security Group với lỗi app |
| Chạy lần2 | Compose cập nhật đúng project/service, giữ DB/queue/volume; không sinh stack trùng |
| Rollback | Có hai ref cũ và lệnh pull/up tương ứng trong README/summary; không hứa rollback schema; lần đầu chưa có Booking cũ |

F5 mô tả đúng lệnh thao tác dưới root server, tên setting/đường dẫn, xem log đã lọc và các giới hạn còn lại. Không tự dựng cơ chế blue/green, generic deploy framework hoặc test Java mới.

## 8. Trạng thái, phụ thuộc và nguồn

- Lượt lập plan chỉ sửa spec/tạo plan; trạng thái thực thi sau yêu cầu tiếp theo được ghi ở §9.
- BRAINSTORMING v0.1 được chốt để lập plan bởi “continue”; user sau đó yêu cầu “triển khai theo plan này” với skill executing-plans, chốt plan F1–F5 và yêu cầu thực thi. Không dựng gate Java rỗng; mọi delta Java thực sự phát sinh vẫn qua gate Shop App.
- Phụ thuộc thực tế: quyền GitHub Secrets/Actions/package, key deploy, server env đúng, hai image được push, định nghĩa workflow trên default branch cho nút dispatch. Chưa kiểm chứng quyền thì ghi chưa kiểm chứng, không coi là bị chặn vĩnh viễn.
- Nguồn chính thức/ví dụ Docker đã đối chiếu ở design §6. Tái sử dụng Dockerfile/Compose repo đích; Shop App mẫu không có workflow deployment để sao chép. Kế hoạch không đổi convention Java hoặc source nghiệp vụ.

## 9. Nhật ký thực thi

- F1–F5 đã viết: workflow hai job, hai Compose image refs, script native SSH/pull/probe và README. Dockerfile/Java giữ nguyên.
- Action pin SHA được resolve từ tag chính thức qua GitHub API. actionlint1.7.12, bash syntax, ShellCheck và hai Compose config local đạt; hai Compose trên119 cũng parse env thật thành công. Chưa dùng các kết quả này để khẳng định build/push/deploy đạt.
- Đã cấu hình secret FT119_SSH_PRIVATE_KEY và bốn variables GitHub qua API; key deploy riêng được thêm song song key cũ và thử đăng nhập119 thành công với StrictHostKeyChecking. Credential không nằm trong Git/spec/log.
- Env119 đã backup mode600 rồi cập nhật có chọn lọc Booking DB/token endpoint/internal-service secret; bỏ biến JWKS/audience cũ. Không reset credential hạ tầng hoặc client secret.
- Đã commit issuance-result `2d6b8d4`, pipeline `c31b61b`, push dev/son. Push run [40907914365](https://github.com/levanminh04/flash-ticket-platform/actions/runs/40907914365) **SUCCESS**: Booking364 unit +94 integration/0 lỗi/bỏ qua; Config Server1 test/0 lỗi. Hai Docker build/push, SSH login/pull/up và cleanup đều đạt. Không sửa Java/Dockerfile trong task deploy.
- Push run đã chạy Config Server UP lúc23:11:20 +07 rồi Booking UP23:11:56 +07 ngày29/10/2026. Digest đầu: Config `sha256:77d016aef364ccd86814042ff85ebdbe6b9653ccfa534bab4c61976b9fb168a5`; Booking `sha256:b912ff6d290faeea55ecc7650a5eb5d2082621028c15ed1312806a83416334ff`. Config.Image và image ID runtime khớp; restart0/OOMfalse.
- Audit thật119: hai health HTTP200/UP; POST order với body rỗng: không token401, INTERNAL_SERVICE403, BUYER400/INVALID_REQUEST; không tạo order/thu tiền. Cả bốn queue Booking có một consumer, ready/unacked0. Config chỉ bind loopback8888; Booking8082 listen host nhưng request từ ngoài timeout. UFW inactive; chưa xác minh/chỉnh AWS Security Group, không báo URL public hoạt động.
- Bộ nhớ sau probe: Booking khoảng355 MiB/512, Config139 MiB/256; server available487 MiB, swap dùng561 MiB so với289 MiB trước deploy. Không OOM/restart quan sát được, nhưng chưa chứng minh chịu tải; giới hạn máy2GB/swap vẫn cần theo dõi theo tải thật.
- Đã tạo [PR #3](https://github.com/levanminh04/flash-ticket-platform/pull/3), chỉ một file workflow cho main, CI/checks đều đạt; đã chuyển ready. API merge bị ruleset main từ chối **405 Cannot update this protected ref**, giữ nguyên bảo vệ. Chưa merge/không đưa source nghiệp vụ vào main. Nút Run workflow UI chờ người có quyền merge PR; user đã được báo và hỏi qua câu hỏi async. Không suy quyền push dev/son là quyền bypass main.
- API workflow_dispatch dev/son được nhận thành công dù định nghĩa chưa vào default branch (workflow đã có push run). Run thủ công [40909384611](https://github.com/levanminh04/flash-ticket-platform/actions/runs/40909384611) **SUCCESS**: verify lại Booking364+94 và Config1 test, cả hai build/push/pull/up đạt. Config UP23:17:02 +07 rồi Booking UP23:17:39 +07 ngày29/10/2026. Deploy lần2 chỉ thay đúng hai service, log/summary ghi được hai ref cũ để rollback; không thay container hạ tầng hoặc dữ liệu/queue.
- Digest đang chạy sau lần2: Config `sha256:d90d5cd1be18af8960fae501bbf1c18b66236e0554ea280fb7f3ba7927945a1e`; Booking `sha256:59b694525e486dc28297a7a285a884ef2f7876c3d5cfca28324a366130105285`. Cùng source commit c31b61b, mỗi run deploy bằng digest output riêng, không suy digest từ tag mutable.
- Audit cuối23:18:19 +07: Config/Booking HTTP200 UP, auth401/403/400, bốn queue mỗi queue1 consumer/ready0/unacked0, không restart/OOM. Booking352.3 MiB/512, Config152.8 MiB/256; server available614 MiB, swap dùng678 MiB. Swap tăng sau hai lần startup, chưa làm load test hoặc kết luận đủ tải. Booking public8082 vẫn ConnectTimeout; không đổi firewall/Security Group.
- Trạng thái task1/2/3: DONE; task4 push + manual API: DONE; task5 nghiệm thu deploy: DONE trong phạm vi SSH/internal/auth/dependency, public ingress/load test không thuộc kết quả đã đạt. User chốt tiếp: **“chỉ cần push thì deploy là ok còn nút run workflow thì k cần”**. Manual UI/main không còn thuộc yêu cầu; đóng PR#3 không merge, giữ nguyên ruleset. workflow_dispatch API hiện có không cần thay đổi hoặc chạy deploy lại cho quyết định này.
- Bằng chứng local bị ignore: `services/booking-service/target/github-deploy-20261029/` chứa run logs, pins, live-audit.json và metadata PR; không chứa credential runtime trong báo cáo. Key riêng nằm ngoài repo, secret GitHub được mã hóa; env backup trên119 mode600.
