# Keycloak HTTP and PostgreSQL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Setup Keycloak HTTP trực tiếp với PostgreSQL, tài nguyên phù hợp máy chạy, theme login CSS và Note khớp cấu hình thực tế.

**Architecture:** Giữ một Keycloak và một PostgreSQL; không thêm Nginx hay HA. Cùng host dùng Docker network chung, quản trị DB qua loopback và SSH tunnel; khác host cần đường private đã kiểm. HTTP là setup thử tạm, không phải tuyên bố HTTPS production hoặc đã đạt tải.

**Tech Stack:** Docker Compose v2, Keycloak `26.8.0`, PostgreSQL `16.15`, dotenv, Keycloak login theme/CSS.

**Spec:** [2026-10-03-keycloak-postgresql-http-design.md](../specs/2026-10-03-keycloak-postgresql-http-design.md).

## Global Constraints

- Keycloak dùng HTTP trực tiếp, chưa cần Nginx.
- Giữ production command `start`, bật HTTP cổng 8080, hostname lấy từ `.env` và khớp endpoint thực tế.
- Dùng PostgreSQL vendor `postgres`, database `keycloak_db`, runtime role `keycloak_app`.
- Publish `127.0.0.1:5432:5432` cho quản trị qua SSH tunnel.
- Nếu khác host: cần endpoint private riêng cho runtime; mapping loopback không cung cấp kết nối xuyên EC2.
- Không thay đổi hoặc xóa dữ liệu volume hiện có để ép init lại.
- Không tự thêm metrics, cluster hoặc các option tối ưu preview.
- Giữ theme cache bật khi chạy; không sửa trực tiếp theme đóng gói trong image.
- Không ghi secret hoặc kết luận đã chịu tải khi chưa đo.
- Bỏ nội dung backup khỏi Note; không sửa yêu cầu phục hồi của dự án.

## Review Focus

1. Thiếu env/secret hoặc ký tự đặc biệt trong secret: Compose phải dừng khi thiếu biến; giá trị vào container không bị biến đổi hoặc lộ trong log.
2. Volume cũ có DB/role/mật khẩu khác: giữ dữ liệu và volume identity; thay env không được coi là đã migrate DB.
3. Khác host hoặc chạy hai Compose riêng: Docker DNS chỉ dùng trong network chung trên cùng host; DB không vô tình public.
4. Admin HTTP có redirect hoặc realm yêu cầu SSL: phải kiểm đăng nhập từ laptop, không chỉ HTTP 200; không tự hạ chính sách SSL.
5. Theme cache hoặc quá tải: CSS chọn đúng realm vẫn tải được; giới hạn queue/pool không làm tăng RAM vô hạn hoặc lấy hết connection của ứng dụng khác.

---

## Trạng thái và điều kiện thực hiện

**Kế hoạch được người dùng yêu cầu thực hiện inline; kết quả mới nhất ở Execution record cuối file.** Placement/CPU và tải mục tiêu AWS vẫn OPEN. Tuning được ghi là cấu hình khởi điểm theo RAM 8 GB đã xác nhận, không phải nghiệm thu capacity. Những bước yêu cầu inventory/migration/tải AWS chưa được coi là hoàn tất.

Yêu cầu viết kế hoạch không được ghi thành xác nhận đã duyệt spec hoặc đã cho phép chạy deploy lên EC2. Lần này chỉ tạo file kế hoạch, không sửa hai Compose, env, theme hoặc Note.

## File structure và impact map

| File | Trách nhiệm |
| --- | --- |
| Spec được liên kết ở header | Cập nhật đầu vào đã xác nhận và trạng thái review trước thực hiện |
| `deploy/docker-compose.postgresql.yml` | PostgreSQL port/network/volume, tài nguyên và server parameters |
| `deploy/docker-compose.keycloak.yml` | Keycloak HTTP, credentials, pool/queue, tài nguyên và theme mount |
| `deploy/.env` — ngoài Git | Endpoint, secret và giá trị tài nguyên thực tế |
| `deploy/.env.example` — tạo mới | Tên biến và giá trị không bí mật; không dùng password mẫu có thể chạy |
| `deploy/keycloak/themes/flash-ticket/login/theme.properties` — tạo mới | Kế thừa theme login và nạp CSS |
| `deploy/keycloak/themes/flash-ticket/login/resources/css/flash-ticket.css` — tạo mới | CSS tùy chỉnh, không chứa logic đăng nhập |
| `deploy/Note.md` | Hướng dẫn và bảng cấu hình thực sự đã làm; trạng thái kiểm chứng |

Không sửa business code, legacy repository, các gate B11–B14 hoặc script bootstrap kiến trúc trong phạm vi này. Không đổi image version chỉ vì docs latest có option mới. Không staging các thay đổi không thuộc task hoặc `.env`.

### Task 1: Chốt đầu vào và ngân sách runtime

**Files:** Modify spec được liên kết ở header sau khi có câu trả lời; chưa sửa runtime.

**Interfaces:**
- Consumes: CPU/RAM, placement, Docker/Compose version, workload cùng host, volume và DB inventory không chứa secret.
- Produces: bảng giá trị đã review cho Task 2: `KEYCLOAK_MEMORY_LIMIT`, `POSTGRES_MEMORY_LIMIT`, `KC_DB_POOL_MIN_SIZE`, `KC_DB_POOL_INITIAL_SIZE`, `KC_DB_POOL_MAX_SIZE`, `KC_HTTP_MAX_QUEUED_REQUESTS`, `PG_MAX_CONNECTIONS`, `PG_SHARED_BUFFERS`, `PG_WORK_MEM`, `PG_MAINTENANCE_WORK_MEM`; endpoint và volume identity thực tế.

- [ ] **Step 1:** Xác nhận CPU/RAM và cùng/khác host với người dùng; baseline t3.small không thay cho inventory lần này.
- [ ] **Step 2:** Đọc inventory volume/database/role bằng thao tác read-only trên đúng host khi có quyền. Ghi tên volume hiện tại, database/schema và quyền role; không in password. Nếu chưa có quyền host, ghi NOT_RUN và chưa chạy Task 2 trên EC2.
- [ ] **Step 3:** Lập ngân sách: memory limit phải để chỗ cho OS và các container khác; tổng pool tối đa của mọi ứng dụng cộng số connection quản trị/migration phải nhỏ hơn số slot PostgreSQL khả dụng. `work_mem` tính theo nhiều operation/session, không chỉ một connection.
- [ ] **Step 4:** Đối chiếu các option pool/queue/theme với image 26.8.0 qua help hoặc docs tương ứng. Option không tồn tại thì sửa đề xuất và review lại trước áp dụng.
- [ ] **Step 5:** Chốt tất cả giá trị trong interface và tải kiểm chứng: concurrency, duration, workload, latency/error thresholds. Khi chỉ làm smoke, ghi rõ chưa nghiệm thu tải.
- [ ] **Step 6:** Cập nhật spec và lấy review phương án/giá trị; không tự chuyển CANDIDATE thành DECIDED.

**Deliverable:** Spec đủ đầu vào để thực hiện Task 2; không còn giá trị tuning do người thực hiện tự đoán.

### Task 2: Compose PostgreSQL và Keycloak hoạt động cùng cấu hình env

**Files:** Modify hai Compose và `deploy/.env`; Create `deploy/.env.example`.

**Interfaces:**
- Consumes: toàn bộ giá trị Task 1; credentials runtime được cấp ngoài Git.
- Produces: service `postgres`, service `keycloak`, network Compose key `ticket`, cùng project `flash-ticket-infra` cho trường hợp cùng host; giữ volume thực tế của Task 1.

- [ ] **Step 1:** Kiểm cấu hình hiện tại bằng `rtk proxy docker compose --env-file deploy/.env -f deploy/docker-compose.postgresql.yml -f deploy/docker-compose.keycloak.yml config --quiet`. Ghi exit code; lệnh có thể PASS dù vendor hoặc role sai nên không dùng nó làm bằng chứng kết nối.
- [ ] **Step 2:** PostgreSQL giữ named volume đã có; nếu đổi project name thì map volume identity cũ rõ ràng thay vì tạo volume rỗng mới. Đặt loopback publish, memory limit và `postgres -c` parameters theo Task 1; giữ `fsync`, `full_page_writes`, `synchronous_commit` hoạt động.
- [ ] **Step 3:** Gắn hai service vào `ticket` khi cùng host. Từ repo root dùng chung command có `-p flash-ticket-infra` và cả hai `-f`. Nếu chạy khác host, dùng private endpoint đã kiểm; không tạo Docker network với hy vọng nối xuyên host.
- [ ] **Step 4:** Keycloak map `KC_DB=postgres`, URL đúng `keycloak_db`, role `keycloak_app`, secret/admin/hostname qua env; biến bắt buộc dùng `${NAME:?required}`. Bật `KC_HTTP_ENABLED: "true"`; không thêm proxy headers khi không có proxy.
- [ ] **Step 5:** Map memory/pool/queue từ Task 1. Nếu cùng project, thêm PostgreSQL healthcheck bằng `pg_isready` và `depends_on: condition: service_healthy`; probe này kiểm server sẵn sàng, không thay kiểm credentials/migration. Trường hợp khác host không dùng depends_on local.
- [ ] **Step 6:** Đồng bộ `.env` và `.env.example` với mọi mapping mới. Giữ secret thực tế, không thay password trong cluster bằng việc đổi env. Thiếu `keycloak_db`/`keycloak_app` hoặc grants: báo sai lệch, chuẩn bị thao tác cấp DB/role riêng để review; không chạy init script lịch sử nguyên trạng hoặc tạo lại volume.
- [ ] **Step 7:** Lặp `config --quiet`: expected exit 0. Trong env kiểm thử chứa giá trị giả, lần lượt bỏ từng secret bắt buộc: expected nonzero và tên biến thiếu. Không dùng `config` không có `--quiet` với secret thật.
- [ ] **Step 8:** Kiểm secret ký tự đặc biệt với dữ liệu giả: dotenv quoting và Keycloak expression handling phải giữ đúng literal. Không dùng secret thật trong test; nếu cần cơ chế literal khác, kiểm image hỗ trợ trước khi đổi mapping.
- [ ] **Step 9:** Trên runtime được phép chạy, `up -d` với đúng project/volume; kiểm trạng thái DB, Keycloak và kết nối bằng role runtime. Laptop kết nối trực tiếp public 5432 phải thất bại; qua SSH tunnel phải thành công; container Keycloak cùng network vẫn kết nối DB.

**Deliverable:** Cấu hình được resolve và runtime checks có kết quả riêng, không làm mất dữ liệu. Nếu chưa có host/quyền runtime, bàn giao static PASS và runtime NOT_RUN.

### Task 3: Theme login CSS có thể tùy chỉnh

**Files:** Create hai file theme đã liệt kê; Modify Compose Keycloak và `.env.example` nếu cần.

**Interfaces:**
- Consumes: Keycloak Task 2, realm kiểm thử.
- Produces: theme name `flash-ticket`; mount `./keycloak/themes/flash-ticket:/opt/keycloak/themes/flash-ticket:ro`; CSS `css/flash-ticket.css`.

- [ ] **Step 1:** Tạo `theme.properties`: `parent=keycloak`, `import=common/keycloak`, `styles=css/login.css css/flash-ticket.css`. Kiểm stylesheet parent tồn tại trong image pinned; sửa đường dẫn nếu image khác trước chạy.
- [ ] **Step 2:** Tạo CSS nhỏ với biến màu, font và bo góc, kế thừa bố cục sẵn có; không tự thiết kế lại flow, không thêm template/JS. Cho phép người dùng thay CSS để đổi hình thức.
- [ ] **Step 3:** Thêm mount read-only và giữ theme/template cache bật. Không mount đè toàn bộ thư mục theme mặc định.
- [ ] **Step 4:** Resolve Compose và kiểm mount path, quyền đọc trong container. Chọn `flash-ticket` tại Realm settings → Themes → Login theme ở realm kiểm thử.
- [ ] **Step 5:** Mở trang login và kiểm CSS trả HTTP 200, style được áp dụng, đăng nhập vẫn hoạt động. Đổi một CSS value giả trong môi trường thử, áp dụng quy trình restart/cache invalidation và kiểm browser tải phiên bản mới; ghi cách làm vào Note. Không tuyên bố hot reload khi cache đang bật.

**Deliverable:** Theme CSS hoạt động trên realm kiểm thử; thiếu runtime thì chỉ đánh dấu file/mount đã kiểm, visual/login NOT_RUN.

### Task 4: Kiểm admin HTTP, tải và viết lại Note

**Files:** Modify `deploy/Note.md`; cập nhật checklist trong plan và trạng thái spec theo bằng chứng.

**Interfaces:**
- Consumes: cấu hình cuối cùng Tasks 2–3, endpoint thực tế và tiêu chí Task 1.
- Produces: Note mô tả đúng setup và bảng PASS/FAIL/NOT_RUN; kết quả tải không chứa secret.

- [ ] **Step 1:** Từ laptop mở URL `/admin/`, kiểm đăng nhập, redirect và CSS. Nếu gặp HTTPS required, xác định `Require SSL` của realm quản trị và đường truy cập; không tự đổi thành None. Việc thử HTTP ngoài Internet cần lựa chọn realm/access phù hợp được review.
- [ ] **Step 2:** Kiểm thiếu DB/sai password trên môi trường thử không chứa dữ liệu thật: service không ready, log giải thích lỗi và không lộ secret. Không gây lỗi trên runtime đang phục vụ người dùng.
- [ ] **Step 3:** Khi có tải mục tiêu và môi trường được phép, chạy workload đã chốt ở Task 1; ghi throughput, latency, error rate, CPU/RAM và connection use. Kiểm request bị từ chối khi quá queue, dịch vụ phục hồi sau giảm tải; không ép OOM để chứng minh chịu tải. Nếu chưa có workload/quyền thì NOT_RUN.
- [ ] **Step 4:** Viết Note tập trung vào cấu hình đã làm: env mapping, lệnh chạy với project name nhất quán, vai trò DB, volume, SSH tunnel, HTTP/admin, theme và bảng tuning. Bỏ các phương án Nginx/HTTPS chưa triển khai và nội dung backup theo yêu cầu; giữ mô tả giới hạn HTTP.
- [ ] **Step 5:** Chạy `rtk git diff --check` và governance audit: `rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File .agents/skills/govern-capstone-work/scripts/audit-governance.ps1`. Rà cả file mới vì `git diff` không hiển thị nội dung untracked.
- [ ] **Step 6:** Bàn giao file đã đổi, assumptions, kết quả kiểm tra và OPEN còn lại. Commit từng deliverable sau review nếu workflow thực hiện có dùng Git commit; chỉ add đường dẫn thuộc task, không add `.env` hoặc thay đổi không liên quan.

**Deliverable:** Note đúng cấu hình và đúng mức evidence; không tuyên bố deploy/load PASS khi chỉ sửa file local.

## Self-review

- Đã map các mục spec vào Tasks 1–4: HTTP/DB/tuning → 1–2; theme → 3; Note và kiểm chứng → 4.
- Năm Review Focus có bước kiểm tương ứng: Task 2 steps 7–8; Task 1 step 2/Task 2 step 6; Task 2 steps 3/9; Task 4 step 1; Task 3 step 5/Task 4 step 3.
- Interface service/theme/env dùng thống nhất; không tạo test framework chỉ để kiểm YAML hoặc CSS.
- Những đầu vào chưa chốt được giữ OPEN ở Task 1. Bản kế hoạch chưa đủ điều kiện thực hiện tuning/runtime cho tới khi Task 1 hoàn tất và được review.

## Execution handoff

Đề xuất **Native / `superpowers:executing-plans`** vì các thay đổi cấu hình phụ thuộc cùng env/network/volume và phạm vi nhỏ. Chỉ thực hiện sau review kế hoạch và chốt đầu vào Task 1. Nếu người dùng chọn subagent-driven, dùng skill tương ứng; không tự dispatch agent trong bước viết kế hoạch này.

## Execution record — 2026-10-03

- USER_CONFIRMED: triển khai inline, sửa tại chỗ `dev/son`, RAM AWS 8 GB.
- FACT: Docker Compose local 2.34.0, Engine 28.0.4; không có AWS CLI/connector để đọc inventory thật.
- CANDIDATE: tuning ban đầu KC/PG mỗi container 2 GiB; pool 5/5/20, queue 100; PostgreSQL connections 100, buffers 512MB, work_mem 4MB, maintenance 128MB. CPU/instance/placement và tải mục tiêu giữ OPEN.
- Đã viết hai Compose, env mapping, env example, theme CSS và Note theo cấu hình thực tế; chưa deploy AWS.
- Ruling: không thêm `-p flash-ticket-infra` cho deployment cũ, tránh đổi volume identity; tên này chỉ dùng cho Docker network chung.
- Ruling: không có depends_on xuyên file trong Compose standalone; startup DB trước bằng `up --wait`, hoặc Keycloak retry/restart nếu DB chưa sẵn sàng.
- Ruling: bổ sung `deploy/postgres/init/10-keycloak.sh` cho PGDATA mới và `deploy/keycloak/start-flash-ticket.sh` để giữ literal password có dollar. Không chạy helper lên cluster cũ.
- Kiểm cấu hình RED→GREEN: thiếu secret được chấp nhận ở cấu hình cũ; cấu hình mới từ chối từng biến thiếu, resolve riêng/gộp thành công, network/port/resource/theme mount được kiểm.
- Smoke local PASS: fresh DB init, TCP role không superuser/schema đúng, password dollar literal, HTTP issuer, admin token/Admin API, login theme HTML/CSS 200, memory limit và restart giữ realm.
- Fresh reviewer: không có Critical/Important trong cấu hình/helper; nhận xét Minor về wording tiến độ được cập nhật trong bước bàn giao Note/plan. Không tự coi reviewer đã xác nhận runtime AWS/load.
- Governance audit PASS với cảnh báo workspace nhiều file đã thay đổi; `git diff --check` PASS. Không tạo commit, push hoặc merge; giữ branch dev/son theo lựa chọn sửa tại chỗ.
- Browser-form login/cache-refresh PASS: form realm master xác thực và redirect với code; sửa CSS + force-recreate phục vụ nội dung mới; restart PostgreSQL giữ realm. Container/network/volume thử riêng đã cleanup thành công, không tác động cluster hiện có.
- AWS inventory/deploy/tunnel, existing-cluster migration, benchmark overload và kiểm lỗi startup/redaction NOT_RUN. Không xác nhận năng lực chịu tải chỉ từ RAM hoặc smoke local.

### Rulings và chi phí nếu sai

- Sửa tại chỗ dev/son theo lựa chọn người dùng, giữ thay đổi chưa commit; chi phí là cần tách diff nếu muốn tích hợp riêng sau này.
- Tuning 2 GiB/container và các pool/queue/buffer là baseline CANDIDATE cho máy 8 GB, không suy ra instance từ credit; chi phí là phải chỉnh lại sau inventory/benchmark, có thể gặp latency hoặc thiếu RAM.
- Giữ project/volume identity cũ và dùng network chung; chi phí là deployment dùng project name tùy chỉnh phải tiếp tục truyền đúng tên.
- Không thêm depends_on xuyên hai file standalone; chi phí là phải khởi động DB trước hoặc chờ Keycloak restart khi DB chưa sẵn sàng.
- Init helper chỉ provision Keycloak trên PGDATA mới, không tạo DB nghiệp vụ hay migrate cluster cũ; chi phí là cluster cũ cần provisioning riêng trước khi chạy.
- Wrapper escape dollar theo Keycloak 26.8.0 thay vì dùng KCRAW chưa có trong tag này; chi phí nếu sai là lỗi xác thực. Password chứa dollar đã kiểm chứng runtime.
- Dùng kiểm tra Compose thực với env giả thay vì framework unit test mô phỏng YAML; chi phí là cần smoke runtime để phát hiện lỗi ngoài Compose model. Chuẩn hóa một lớp dollar trong Compose JSON để so sánh; runtime đã kiểm tra literal thực tế.
- Review package chứa file hiện tại thay vì commit range vì workspace chưa commit; chi phí là nguy cơ bỏ sót file, đã giới hạn danh sách explicit. Giữ scratch/ledger local vì chưa có lịch sử commit; chi phí là artifact local bị ignore, không tự đi theo Git.
- Reviewer không kết luận sizing, AWS/cross-host hay migration volume cũ khi thiếu inventory/quyền; chi phí là các kiểm chứng đó còn OPEN trước deployment thật.
- Harness cookie mô phỏng ngoại lệ secure-context của browser chỉ trên localhost/127.0.0.1; không sửa cookie hay SSL realm production. Chi phí là test này không chứng minh đăng nhập qua IP public hoặc browser laptop thật.
