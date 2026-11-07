# Redis Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Redis chạy thành công trên local, có env ở root, Compose trong deploy và hướng dẫn tích hợp Spring Boot.

**Scope update 2026-10-21:** Người dùng đã xác nhận triển khai trực tiếp, sau đó bỏ EC2 khỏi phạm vi, chỉ chạy local trước.

**Architecture:** Một Redis standalone dùng env tại root. Tái sử dụng network hiện tại, kiểm tra bằng Redis CLI; không sửa service hoặc thêm framework test.

**Tech Stack:** Docker Compose, Redis official image, Redis CLI; Spring Data Redis/Lettuce chỉ xuất hiện trong tài liệu bàn giao.

**Spec:** [2026-10-21-redis-setup-design.md](../specs/2026-10-21-redis-setup-design.md).

## Global Constraints

- "Cập nhật cả `.env` và `.env.example` tại root"; không tạo env trong deploy/service, không commit `.env`.
- "Tái sử dụng các biến đã có: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`."
- "Dùng network `flash-ticket-infra` theo mẫu deploy hiện tại."
- "Không thay dependency hoặc cấu hình của các service trong task setup này."
- "Phần chưa chạy ghi `NOT_RUN`, không coi là hoàn thành phần đó."
- Không ghi secret vào log/báo cáo; giữ container và dữ liệu của các task khác.
- Mọi lệnh chạy từ root và qua `rtk`; chỉ thực thi sau khi người dùng review plan và chọn cách thực hiện.

## Review Focus

1. Password thiếu/rỗng: Compose phải từ chối trước khi tạo container — Task 1, bước 3.
2. Password có `$`, `#`, khoảng trắng: truyền nguyên giá trị, không bị shell/dotenv diễn giải sai — Task 1, bước 5.
3. Env chứa cấu hình service khác: cập nhật Redis không thay các giá trị đó — Task 1, bước 2.
4. Kết nối từ host: kiểm cả published port, không chỉ PING bên trong container — Task 1, bước 4.
5. Port local đang được dùng: chọn published port trống, không xóa container khác — Task 1, bước 1.

---

## File Structure

| File | Trách nhiệm |
| --- | --- |
| `.env` | Giá trị chạy thực tế local, ngoài Git |
| `.env.example` | Tên biến và mặc định không chứa secret |
| `deploy/docker-compose.redis.yml` | Redis, authentication, healthcheck, network, port và volume |
| `deploy/redis/handoff.md` | Cách chạy, tích hợp Spring Boot, smoke check và kết quả local |
| `deploy/README.md` | Thêm lệnh chạy Redis và link handoff |

### Task 1: Env + Compose, chạy Redis local

**Files:** Modify `.env`, `.env.example`; Create `deploy/docker-compose.redis.yml`.

**Interfaces:** Nhận ba biến Redis ở root; cung cấp service Compose tên `redis`, port trong container `6379`, DNS `redis` trên `flash-ticket-infra`. `REDIS_PORT` là published port trên host; `REDIS_HOST` dành cho ứng dụng, không dùng làm bind address.

- [x] **1. Kiểm baseline.** Chạy `rtk git status --short`, `rtk docker compose version`, `rtk docker ps`. Xác định port 6379 có đang dùng không; nếu có, giữ container hiện tại và chọn published port trống trong `.env`.
- [x] **2. Cập nhật env.** Giữ `REDIS_HOST=localhost`, `REDIS_PORT=6379` trong mẫu, `REDIS_PASSWORD=` để người chạy điền. `.env` dùng port đã kiểm và password hiện có hoặc secret mới nếu chưa có. Giá trị có ký tự đặc biệt dùng cú pháp dotenv literal phù hợp. So sánh tại chỗ các thay đổi env, không in secret; xác nhận không đổi cấu hình khác.
- [x] **3. Tạo Compose và kiểm cấu hình.** Dùng image `redis:7.4.11-alpine`, service `redis`, `restart: unless-stopped`, bind `127.0.0.1:${REDIS_PORT}:6379`, network theo mẫu PostgreSQL, named volume `redis-data:/data`. Dùng RDB mặc định của image; không coi volume là backup hoặc cam kết lưu mọi ghi gần nhất. Password dùng `${REDIS_PASSWORD:?Set REDIS_PASSWORD in the repository-root .env}`, truyền tới `redis-server --requirepass` qua biến môi trường và shell có quoting, không nối secret thành mã shell. Healthcheck dùng `REDISCLI_AUTH` và `redis-cli ping`, kiểm đầu ra đúng `PONG`; interval 5s, timeout 3s, retries 10. Chạy lệnh dưới đây: cấu hình thật phải exit 0; env thử ngoài Git thiếu/rỗng password phải exit khác 0 với thông báo rõ. Không in kết quả `config` đầy đủ vì có secret.

```powershell
rtk docker compose --env-file .env -f deploy/docker-compose.redis.yml config --quiet
```

- [x] **4. Khởi động và smoke check.** Chạy các lệnh dưới, mong đợi `up` exit 0, `ps` báo healthy. Từ host, dùng Redis CLI qua published port (CLI cài sẵn hoặc container official image dùng host networking nếu Docker hỗ trợ); authenticated PING phải trả PONG. Với host tooling chưa có, dùng SSH/TCP-forward phù hợp hoặc CLI có sẵn, không coi kiểm nội bộ là thay thế kiểm published port.

```powershell
rtk docker compose --env-file .env -f deploy/docker-compose.redis.yml up -d --wait
rtk docker compose --env-file .env -f deploy/docker-compose.redis.yml ps
```

Chạy đoạn smoke sau trong container bằng `sh -ec`; mọi assertion phải đạt và exit 0. Khi ghi lệnh vào handoff, quote đúng shell PowerShell/Linux và escape `$` qua Compose nếu cần; không dùng `eval`.

```sh
export REDISCLI_AUTH="$REDIS_PASSWORD"
key="flash-ticket:smoke:$(cat /proc/sys/kernel/random/uuid)"
test "$(redis-cli --raw PING)" = PONG
test "$(redis-cli --raw SET "$key" ok EX 5)" = OK
test "$(redis-cli --raw GET "$key")" = ok
test "$(redis-cli --raw TTL "$key")" -gt 0
sleep 6
test "$(redis-cli --raw EXISTS "$key")" = 0
```

Kiểm thêm CLI không có auth phải báo NOAUTH, password sai phải báo WRONGPASS và không đọc/ghi được. Kiểm nội dung trả về vì exit code Redis CLI không luôn đại diện lỗi Redis.

- [x] **5. Kiểm password đặc biệt.** Dùng env thử ngoài Git, project/port riêng và password tổng hợp chứa `$`, `#`, khoảng trắng. Lặp authenticated PING và SET/GET; kết quả phải như bước 4. Cleanup chỉ project/volume thử vừa tạo; không dùng `down -v` cho stack đang dùng chung. Giữ lệnh smoke có thể chạy lại trong handoff ở Task 2.
- [x] **6. Commit phần tracked.** `rtk git add .env.example deploy/docker-compose.redis.yml`, rồi `rtk git commit -m "feat(deploy): setup Redis standalone"`. Kiểm staged diff không có `.env` hoặc thay đổi ngoài task.

Image đã đối chiếu trong [danh mục Docker Official Images](https://github.com/docker-library/official-images/blob/master/library/redis).

### Task 2: Handoff Spring Boot + bàn giao local

**Files:** Create `deploy/redis/handoff.md`; Modify `deploy/README.md`.

**Interfaces:** Dùng service, biến env và smoke check của Task 1; cung cấp hướng dẫn tích hợp và bảng kết quả local với bằng chứng không secret.

- [x] **1. Viết handoff gọn.** Có lệnh start/stop/ps, các lệnh smoke chạy lại được từ Task 1, dependency `spring-boot-starter-data-redis` cho MVC và `spring-boot-starter-data-redis-reactive` cho WebFlux; version theo parent Boot hiện tại. Có mẫu `spring.data.redis.host/port/password` ánh xạ ba biến root, ví dụ `StringRedisTemplate` SET có TTL/GET và lưu ý reactive không gọi blocking trên event loop. Ghi IDE/runtime phải nạp `.env`; service dùng Config Server đặt YAML tại file service tương ứng, không đưa credential vào Git. Không sửa pom/YAML/Java của service.
- [x] **2. Ghi cách kết nối.** Handoff phân biệt JVM local → localhost/published port và container cùng network → redis:6379. Chỉ cấu hình env cho process cần dùng, không đưa secret vào Git. EC2 ngoài phạm vi theo xác nhận mới nhất.
- [x] **3. Kiểm tài liệu.** Thêm mục Redis và link handoff vào `deploy/README.md`. Chạy nguyên block PowerShell smoke trong handoff; phải exit 0 và in PASS. Biến YAML phải khớp `.env.example`, ví dụ có TTL, không có password thật. Ghi Spring Boot end-to-end chưa triển khai. Chỉ kết luận hoàn thành task khi runtime local PASS.
- [x] **4. Kiểm và commit.** Chạy `rtk git diff --check`, mong đợi exit 0; kiểm task-only staged diff. Commit `deploy/redis/handoff.md`, `deploy/README.md` với message `docs(redis): bàn giao tích hợp Spring Boot và kết quả chạy`.

## Self-review và handoff

- Coverage: spec §2.1–2.2 → Task 1; §2.3 → Task 1 bước 4–5; §3–4 → Task 2.
- Không tạo test framework, service demo, cache nghiệp vụ hoặc abstraction mới. Smoke CLI là kiểm tra runnable, được lưu trong handoff.
- Spec/plan nằm trong thư mục bị Git ignore: giữ file local, không force-add. Chỉ commit các deliverable tracked của task khi triển khai.
- Đã thực hiện **Native** theo xác nhận triển khai, chỉ local theo thay đổi phạm vi mới nhất.

## Kết quả thực hiện — 2026-10-21

- Task 1 hoàn thành: `55a9912`; Task 2 hoàn thành: `6e0f0d0`.
- PASS: Compose hợp lệ, container healthy, bind `127.0.0.1:6379`, authenticated PING từ Windows host, SET/GET, TTL expiry, NOAUTH/WRONGPASS, thiếu/rỗng password và password có `$`, `#`, khoảng trắng.
- PASS: nguyên block PowerShell smoke trong `deploy/redis/handoff.md` chạy được; `git diff --check` không báo lỗi.
- Review độc lập: không có Critical/Important/Minor cần sửa. Reviewer không đọc secret `.env`, không chạy lại runtime và không đánh giá Spring Boot E2E. Các giá trị env được người thực hiện kiểm tra không in secret; runtime được kiểm trực tiếp; Spring E2E nằm ngoài task và đã ghi rõ trong handoff.
- Điều chỉnh thực hiện: giữ checkout root `dev/son` để cập nhật đúng env/runtime được yêu cầu; bảo toàn thay đổi RabbitMQ đang diễn ra. Chỉ stage các hunk Redis; dùng harness stdlib tạm trên Windows thay helper bash. Không đổi phạm vi sản phẩm hoặc sửa service.
- EC2 đã bỏ khỏi phạm vi. Spec/plan giữ local theo quy tắc Git ignore hiện tại.
