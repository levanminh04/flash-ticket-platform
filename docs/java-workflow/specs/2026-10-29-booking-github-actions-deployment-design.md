# Deploy Booking bằng GitHub Actions — thiết kế v0.1

Pha: **BRAINSTORMING — APPROVED để lập plan**. Nguồn: sau câu hỏi “Bạn duyệt spec để chuyển sang viết plan chứ?”, user trả lời **“continue”** ngày29/10/2026. Phạm vi chốt là v0.1/F1–F5 và các lựa chọn CI/config/runtime dưới đây; không có class/method Java mới. Chưa tạo workflow/script thực thi, commit/push hoặc thay đổi server; phê duyệt này không phải bằng chứng đã deploy.

Điều chỉnh sau triển khai: user chốt **chỉ cần push dev/son tự deploy, không cần nút Run workflow**. Vì vậy các mục yêu cầu đưa YAML lên main/nút UI phía dưới là lịch sử, không còn điều kiện nghiệm thu. PR#3 được đóng không merge; push run và manual API đã đạt được ghi trong plan §9.

## 1. Phạm vi và lựa chọn đã chốt

- Build/push image trên GitHub Actions, SSH vào **server119** để pull và chạy Docker Compose.
- Registry **GHCR**, tự chạy khi push **dev/son**, có chạy thủ công. Nguồn: ba câu trả lời user ngày29/10/2026 về registry, nguồn config và trigger.
- Deploy **Booking + Config Server** vì user chọn chạy thêm Config Server trên119. Các service nghiệp vụ khác, Gateway và Discovery ngoài scope.
- Không phát sinh class, constructor, method, annotation hoặc dependency Java. Không có gate duyệt Java rỗng; các thay đổi dưới đây là hạ tầng và cấu hình.

## 2. Hiện trạng đã kiểm tra

- Repo `levanminh04/flash-ticket-platform`, default branch `main`; workspace triển khai hiện tại `D:/DATN/flash-ticket-platform`, branch `dev/son`.
- Hai Dockerfile đã có: Booking dùng Java23 đúng POM/enforcer, Config Server dùng Java21. Tái sử dụng cả hai, không đổi version Java trong task này.
- Hai Compose hiện chỉ có `build`, host network, root `.env`, restart/log rotation; chưa có `image` cho registry.
- Config Server dùng native backend `classpath:/config/`. Booking bắt buộc import Config Server; YAML dùng chung còn bật Eureka. Không dùng lại phương án đọc file thay Config Server vì user đã chọn khác.
- SSH119 thực tế: Ubuntu x86_64, Compose2.40.3; đang chạy Keycloak/PostgreSQL/MongoDB/RabbitMQ/Redis, chưa có Booking/Config Server/Eureka, chưa listen8082/8888/8761.
- Server có RAM1907 MiB, MemAvailable khoảng678 MiB và swap2047 MiB tại thời điểm kiểm tra. Đây là số đo trước deploy, không phải bằng chứng đủ tải.
- Root env trên119 là `/home/ubuntu/flash-ticket-platform/.env`, mode600. Chưa có KEYCLOAK_TOKEN_URI/INTERNAL_SERVICE_CLIENT_SECRET; còn tên biến JWKS/audience cũ. Chỉ kiểm tên key, không đưa giá trị bí mật vào spec.
- `.github/workflows/ci.yml` hiện chỉ trigger main và dùng Java21 chung; không lấy workflow đó làm bằng chứng CI cho Booking Java23/dev/son. Máy hiện không có gh CLI; quyền GitHub Secrets/Actions chưa được kiểm chứng.

## 3. Luồng đã chốt cho plan

1. **Trigger** push dev/son với thay đổi Booking, Config Server, hai Compose, script deploy hoặc workflow này. `workflow_dispatch` chỉ cho deploy ref dev/son. Concurrency cố định cho119, `cancel-in-progress: false` để không cắt dở SSH deploy.
2. **Verify trên runner**: Maven verify Booking bằng Java23, gồm profile integration hiện có; sau đó verify Config Server bằng Java21. Dùng Maven có trên runner, tránh phụ thuộc `.mvn` Booking đang bị ignore. Cấp RabbitMQ service container tạm trong CI cho smoke test mở connection thật; không đưa credential Rabbit119 vào runner, không sửa test Java. Lựa chọn CI này thuộc v0.1 đã chốt để lập plan; lần kiểm sau deploy vẫn dùng Rabbit119 thật.
3. **Build/push** hai Dockerfile bằng official Docker actions, platform linux/amd64. Dùng GITHUB_TOKEN với contents:read/packages:write; gắn source label và tag theo commit. Hai image:
   - `ghcr.io/levanminh04/flash-ticket-platform/booking-service`
   - `ghcr.io/levanminh04/flash-ticket-platform/config-server`
   Deploy bằng digest từ chính kết quả build, không lấy latest làm định danh release. Chỉ chạy deploy nếu verify và cả hai push thành công.
4. **SSH** bằng OpenSSH/scp có sẵn trên runner; key riêng cho pipeline, kiểm known_hosts đã đối chiếu. Copy hai Compose và script vào root deploy hiện có, không clone/pull source hoặc Maven build trên119. Không copy `.env` máy Windows lên119.
5. **Pull trước khi thay container**: truyền hai image digest qua biến process, login GHCR bằng token ngắn hạn của job qua password-stdin và Docker config tạm; pull cả hai image trước khi chạy up. Xóa credential tạm khi kết thúc. Không cần PAT registry dài hạn nếu package gắn đúng repository và token có quyền pull.
6. **Config Server trước**: Compose riêng chạy trên127.0.0.1:8888, native config đóng trong image đúng commit. Dùng host curl/jq (đã có trên119) đợi health UP, tối đa180s; kiểm `/booking-service/default` có propertySources. Không in response cấu hình ra log.
7. **Booking sau**: host network8082, CONFIG_SERVER_URL=http://127.0.0.1:8888, PostgreSQL127.0.0.1:5432; giữ issuer public của Keycloak, chỉ issuer-uri. `EUREKA_CLIENT_ENABLED=false` ở deployment này vì chưa chạy Discovery; local IntelliJ không bị đổi. Up riêng Booking bằng `up -d --no-deps --no-build`; đợi health UP tối đa180s.
8. **Nghiệm thu**: đối chiếu digest container/image đã push, restart/OOM, RAM/swap, DB/Redis/Rabbit health và consumer. Ghi commit/digest hai image trước/sau vào job summary để rollback thủ công. Không coi container Running là đủ và không tự publish message nghiệp vụ để kiểm deploy.

Trên máy119, curl/jq kiểm HTTP health thay cho việc giả định image JRE có curl/wget. Không thêm healthcheck Java hoặc package OS chỉ để thực hiện probe này. Một replica có khoảng dừng ngắn khi recreate; không cam kết zero downtime.

## 4. File và cấu hình đã chốt cho plan

| ID | File | Thay đổi |
| --- | --- | --- |
| F1 | `.github/workflows/deploy-booking.yml` | NEW: verify → build/push hai image → SSH deploy; official actions pin commit đã xác minh khi triển khai |
| F2 | `deploy/docker-compose.booking.yml` | EXISTING: thêm image ref có fallback local, giữ build cho cách build cũ; runtime env cho119, mem_limit512m, heap tối đa60% |
| F3 | `deploy/docker-compose.config-server.yml` | EXISTING: image ref có fallback local; bind loopback8888, mem_limit256m, heap tối đa55% |
| F4 | `deploy/deploy-booking.sh` | NEW: pull/start theo thứ tự, helper shell `wait_for_health` bằng curl/jq, timeout và thông báo lỗi; không thêm type/method Java |
| F5 | `deploy/README.md` | EXISTING: bootstrap server, GitHub settings, trigger, xem log, rollback hai digest cũ |

Giữ hai Dockerfile và cấu hình native hiện có. Bộ nhớ512+256 MiB là giới hạn khởi đầu đề xuất, chưa chứng minh đủ hoặc ổn định; tổng cap768 MiB lớn hơn MemAvailable đang đo. Phải đo lại khi startup/HTTP hoạt động; OOM/restart liên tục hoặc swap tăng không được coi là deploy thành công. Không tự dừng container hạ tầng để lấy RAM.

### GitHub settings và bootstrap119

- Secret: `FT119_SSH_PRIVATE_KEY`; Variables: `FT119_SSH_HOST`, `FT119_SSH_USER`, `FT119_SSH_PORT`, `FT119_SSH_KNOWN_HOSTS`. Root deploy cố định `/home/ubuntu/flash-ticket-platform`, không thêm framework cấu hình server.
- GITHUB_TOKEN có sẵn; package mới mặc định private, gắn quyền repository. Chưa xác nhận đã có các setting này; không in/lưu private key hoặc yêu cầu gửi secret trong chat.
- Chuẩn bị root `.env` trên119: giữ credential DB/Rabbit/Redis đang hoạt động; kiểm BOOKING_DB_URL thật trên host, thêm token URI và client secret internal-service hiện hành bằng nguồn quản trị hợp lệ, không reset secret. Dọn hai biến JWKS/audience cũ theo rule issuer-only. Không tạo env riêng trong deploy/từng service.
- Cài public key deploy song song key hiện có; xác nhận host key từ kết nối đang tin cậy. Không tắt StrictHostKeyChecking.
- Nút Run workflow trên GitHub UI cần YAML hiện diện ở default branch main; vẫn chỉ cho deploy dev/son. Đưa định nghĩa lên main là bước Git riêng cần xử lý khi triển khai, không tự merge code nghiệp vụ sang main.
- Code issuance-result hiện còn uncommitted trên dev/son. Image Actions chỉ chứa source đã commit/push; phải bàn giao phần này cùng phiên bản muốn deploy, không lấy JAR local làm bằng chứng image GitHub có tính năng mới.
- Trước lần chạy Booking119 đầu tiên, kiểm tra process Booking local đang dùng cùng queue. Dừng process local do agent đã khởi chạy nếu cần kiểm chứng một consumer; không giả định consumer_count=1 khi vẫn có instance local.

## 5. Lỗi, rollback và tiêu chí xong

- Verify/build/push/SSH/pull lỗi: workflow fail; không chạy bước up khi pull chưa đủ. Pull lỗi chưa thay container đang chạy.
- Config chưa UP/không có Booking config: không start Booking. Booking không UP/không kết nối dependency: fail workflow, giữ log chẩn đoán đã lọc; không báo done chỉ vì SSH exit0.
- Đổi image bằng Compose up, không compose down toàn stack, không volume prune, không reset DB/queue hoặc thay quyền app Rabbit.
- Nếu thất bại sau recreate: rollback thủ công bằng hai image ref cũ đã ghi. Không hứa rollback DB: ddl-auto vẫn update theo lựa chọn dự án, rollback image cần schema tương thích. Lần deploy đầu chưa có image Booking cũ để khôi phục.
- Done khi workflow/schema/shell/Compose được kiểm, image tồn tại trên GHCR, workflow chạy thật đúng dev/son, Config UP rồi Booking UP trên119 và digest đúng; xác thực token/role cùng dependency được kiểm không tạo order/thu tiền ngoài ý muốn. Chỉ xác nhận URL public8082 khi đã kiểm đường mạng; SSH health loopback chưa chứng minh Security Group cho truy cập ngoài.
- F1–F5, CI Rabbit service, Eureka-off tại119, giới hạn RAM khởi đầu và quy trình secrets/bootstrap thuộc thiết kế đã chốt để lập plan theo phản hồi “continue”. Hiện chỉ hoàn tất khảo sát/spec; các bước triển khai, quyền GitHub và độ ổn định bộ nhớ vẫn chưa được kiểm chứng, không có phê duyệt Java còn thiếu.

## 6. Nguồn đối chiếu

- [GitHub: publish Docker images](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images): login/build/push bằng GITHUB_TOKEN, pin actions. [Docker open-source build-push-action sample](https://github.com/docker/build-push-action#path-context): tái sử dụng checkout + path context; không tự viết client registry.
- [GHCR auth/digest](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry): package/repository access, private mặc định và pull theo digest.
- [Docker Compose up](https://docs.docker.com/reference/cli/docker/compose/up/): recreate service theo image/config, no-build/no-deps; running khác healthy.
- [Spring Cloud Config Client](https://docs.spring.io/spring-cloud-config/reference/client.html) và [Eureka disable](https://docs.spring.io/spring-cloud-netflix/reference/spring-cloud-netflix.html): giữ Config import, tắt discovery bằng property khi chưa có server.
- [GitHub manual workflow](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow): yêu cầu default branch cho nút chạy thủ công.
- Shop App mẫu không có `.github/workflows` ở root đã kiểm. Task này không đổi hình dạng Java; không bịa class/method Shop App làm mẫu deploy. Tái sử dụng Dockerfile/Compose của repo đích và mẫu chính thức ở trên.
