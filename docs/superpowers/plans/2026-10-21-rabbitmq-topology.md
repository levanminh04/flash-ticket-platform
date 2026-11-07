# RabbitMQ Topology Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Chạy Compose từ root là có RabbitMQ cùng 3 exchange, 15 queue đơn nhiệm và 15 DLQ ngay lần đầu; không chạy init thủ công.

**Architecture:** RabbitMQ tự import topology từ JSON khi boot, qua cấu hình native trong `rabbitmq.conf`. User và ACL được provision bằng hook `post_start` viết trực tiếp trong Compose, nhận credentials từ root `.env`. Không có file bootstrap hay file test.

**Tech Stack:** Docker Compose >= 2.30, `rabbitmq:4.3.6-management`, RabbitMQ native definitions/CLI, Playwright MCP.

**Spec:** [2026-10-21-rabbitmq-topology-design.md](../specs/2026-10-21-rabbitmq-topology-design.md).

**Status:** Đã triển khai, kiểm fresh-volume/UI/routing/DLQ/restart/recreate. Local dùng 5673/15673 vì broker native chiếm cổng mặc định.

## Global Constraints

- Chỉ thêm 3 file runtime: Compose, `rabbitmq.conf`, `definitions.json`. Không thêm `bootstrap.sh`, script kiểm thử, wrapper hoặc dependency.
- Giữ entrypoint chính thức. Native boot import chịu trách nhiệm tạo topology; hook Compose chỉ tạo/cập nhật user và ACL, không gọi `import_definitions` lần nữa.
- Definitions có vhost `/`, không chứa users, permissions, mật khẩu hoặc password hash. Khi boot import trên node mới, RabbitMQ bỏ qua việc tạo default user/vhost; không dựa vào `RABBITMQ_DEFAULT_USER/PASS`.
- Exchange durable, không auto-delete: `business.ex` topic, `identity.ex` direct, `dlx.ex` direct.
- Copy nguyên 15 cặp queue/DLQ và routing key từ spec §2.2/§3. Tên exchange/queue/DLQ không có `v1` hay tiền tố dự án; routing key vẫn giữ `.v1`.
- Mỗi main queue có đúng một routing key đầu vào. Tổng 30 quorum queue durable, không exclusive/auto-delete; 15 input binding và 15 DLQ binding.
- 15 policy exact-name, priority 10, apply-to queues: DLX `dlx.ex`, dead-letter key là tên DLQ, strategy `at-least-once`, overflow `reject-publish`, delivery-limit 20. DLQ không TTL hoặc DLX quay về.
- Hostname `rabbitmq`, node `rabbit@rabbitmq`, volume `rabbitmq-data:/var/lib/rabbitmq`, network `flash-ticket-infra`, cùng Compose project hạ tầng hiện tại.
- Bind loopback mặc định `127.0.0.1:5672` và `127.0.0.1:15672`; memory `1g`, restart unless-stopped, stop grace 30s. Single-node không có HA.
- Chỉ root `.env` chứa secret; giữ giá trị đã có, không in secret ra output hoặc lưu trong JSON/YAML/README. `.env.example` để password trống.
- Hook chờ startup tối đa 120s; marker `/tmp/rabbitmq-users-ready` chỉ xuất hiện sau khi provision thành công. Healthcheck kiểm tra node, AMQP port và marker.
- Không thêm publisher/consumer nghiệp vụ, SPI, retry queue, outbox/inbox hoặc Saga.
- Nghiệm thu UI bằng Playwright MCP. CLI dùng cho kiểm cấu hình, vận hành Docker và đọc log; không lưu browser test code hoặc AMQP harness vào repo.
- Mọi shell command qua `rtk`. Không stage `.env`, không force-add spec/plan bị ignore, không tự commit khi chỉ được yêu cầu viết plan.

## Review Focus

1. Volume mới: native import tạo cả vhost, hook tạo đủ user; một lệnh Compose hoàn thành setup — Task 1–3.
2. Secret có ký tự đặc biệt: env và argv được quote, biến shell trong Compose escape bằng `$$`, không eval/xtrace — Task 2.
3. Hook chạy sau start nhưng không đảm bảo broker đã ready: chờ startup, lỗi phải nonzero, không tạo marker sớm — Task 2/3.
4. Volume cũ: không tự xóa queue/message khi conflict; restart và force-recreate không nhân đôi topology — Task 1/3.
5. Kiểm bằng UI có giới hạn: không tuyên bố đã kiểm AMQP ACL/confirm/mandatory; tài nguyên thử được cô lập — Task 3.

## File structure

| File | Trách nhiệm |
| --- | --- |
| Create `deploy/docker-compose.rabbitmq.yml` | Broker, env, mounts, user/ACL inline hook và healthcheck |
| Create `deploy/rabbitmq/rabbitmq.conf` | Bật native boot-time definitions import |
| Create `deploy/rabbitmq/definitions.json` | Vhost `/`, 3 exchange, 30 queue, 30 binding, 15 policy |
| Modify `.env.example`, local `.env`, `deploy/README.md` | Credentials, giới hạn tài nguyên và lệnh chạy từ root |

### Task 1: Khai báo native JSON import

**Files:** Create `deploy/rabbitmq/definitions.json`, `deploy/rabbitmq/rabbitmq.conf`.

**Interfaces:** Consumes topology đã duyệt trong spec; produces cấu hình broker tự tạo topology khi boot, độc lập credentials.

- [x] **1. Tạo JSON definitions.** Khai báo rõ vhost `/`; 3 exchange, 15 main queue và 15 DLQ theo spec. Queue có `arguments: {"x-queue-type":"quorum"}`, durable true, auto_delete false.
- [x] **2. Thêm binding và policy.** 15 input binding không wildcard, 15 binding từ `dlx.ex` với routing key bằng tên DLQ. Policy `<queue-base>.dlx` có pattern neo hai đầu, escape dấu chấm, chỉ khớp main queue. Giá trị theo Global Constraints.
- [x] **3. Bật native import trong `rabbitmq.conf`:**

  ```ini
  definitions.import_backend = local_filesystem
  definitions.local.path = /etc/rabbitmq/definitions.json
  ```

- [x] **4. Kiểm trực tiếp không tạo file test.** Parse bằng `ConvertFrom-Json`, đối chiếu spec: 3 exchange, 30 queue, 30 binding, 15 policy và vhost `/`. Không có users/permissions/secret hoặc policy khớp DLQ. Runtime kiểm tại Task 3.

### Task 2: Compose quản lý user và ACL

**Files:** Create `deploy/docker-compose.rabbitmq.yml`; modify `.env.example`, local `.env`.

**Interfaces:** Consumes config/JSON Task 1 và root env; produces service `rabbitmq` tự provision tài khoản sau boot, chỉ healthy khi thành công.

- [x] **1. Kiểm prerequisite.** Kiểm Compose >= 2.30 và tag `rabbitmq:4.3.6-management` tồn tại. Nếu thiếu tag, báo bằng chứng trước khi chọn phiên bản thay thế.
- [x] **2. Bổ sung env theo spec §5.3.** Giữ app credential hiện có. Bổ sung admin/identity username-password, management port, memory limit. Default username: `flash_ticket_admin`, `flash_ticket_app`, `flash_ticket_identity`. Password local thiếu thì sinh bằng CSPRNG, không in; example để trống.
- [x] **3. Khai báo broker.** Mount config và JSON read-only tại `/etc/rabbitmq/rabbitmq.conf` và `/etc/rabbitmq/definitions.json`. Giữ entrypoint image; dùng storage/network/ports theo Global Constraints. Truyền env ba tài khoản vào container bằng các biến riêng; không map sang default-user env. Password dùng Compose `:?` để thiếu thì fail sớm.
- [x] **4. Viết hook ngay trong Compose.** `post_start` chạy `sh -ec` với user `rabbitmq`, không file shell riêng. Escape biến shell thành `$$` trong YAML. Xóa marker trước, validate credentials, dùng native `rabbitmqctl wait --pid 1 --timeout 120` để chờ startup kể cả Erlang node chưa online. Không dùng sleep cố định thay kiểm ready. Không bật xtrace hoặc eval secret.
- [x] **5. Upsert ba tài khoản bằng native CLI.** Username khác nhau, khớp `^[A-Za-z][A-Za-z0-9_-]{0,63}$`; password không rỗng/CR/LF. Kiểm tồn tại trước để chọn add/change password, không dùng `add_user || true` che lỗi. Mọi argv credential đều quote. Cập nhật tags và permissions kể cả user đã tồn tại; admin có tag administrator, hai user còn lại không management tag. Authenticate admin thành công rồi mới xóa guest nếu tồn tại.

  ```text
  admin:    configure .*, write .*, read .*
  app:      configure ^$, write ^(business|dlx)\.ex$
            read ^(booking\.(sales-configured|event-cancelled|accept-payment|tickets-issued|issuance-failed)|payment\.(finance-configured|event-cancelled|start|decision|ticket-issuance-failed|order-completed)|ticket\.(access-configured|event-cancelled|issuance)|user\.identity)\.q$
  identity: configure ^$, write ^identity\.ex$, read ^$
  ```

- [x] **6. Hoàn tất readiness.** Tạo marker cuối hook sau mọi lệnh thành công. Healthcheck gồm `check_running`, `check_port_connectivity` và marker; interval 10s, timeout 5s, retries 12, start_period 30s. Native import lỗi thì boot không hoàn tất; user hook lỗi thì không ready. Không thêm shell framework đếm topology; UI sẽ nghiệm thu đầy đủ.
- [x] **7. Kiểm Compose trực tiếp.** Capture `docker compose config --format json` trong bộ nhớ, không echo secrets. Kiểm mounts, inline hook, escape biến, healthcheck, loopback bindings và trường hợp thiếu password. Không tạo file test.

### Task 3: Nghiệm thu bằng Playwright MCP và viết hướng dẫn

**Files:** Modify `deploy/README.md`; không tạo file kiểm thử.

**Interfaces:** Consumes Task 1–2; produces bằng chứng fresh start, topology, routing, persistence và README có một lệnh setup từ root.

- [x] **1. Cô lập kiểm thử.** Kiểm container/port/volume có sẵn. Dùng project `rabbitmq-check-<8hex>`, network và volume riêng. Nếu cần đổi ports/network, dùng override Compose tạm ngoài repo; không script test, không env mới. Giữ root `.env`.
- [x] **2. Chạy fresh volume bằng một lệnh.** `up -d --wait --wait-timeout 180 rabbitmq` phải tự boot import và provision user rồi healthy. Không chạy exec init để làm ca này đạt giả. Đọc log nếu lỗi.
- [x] **3. Kiểm Management UI qua Playwright MCP.** Đăng nhập admin; đối chiếu 3 exchange/type, 30 queue quorum/durable, 15 input binding, 15 DLQ binding, 15 exact policy. Bỏ qua exchange và binding hệ thống khi đếm.
- [x] **4. Publish message thử qua UI trên broker test.** Payload giả có mã riêng: `event.cancelled.v1` tới đúng 3 queue hủy; `ticket.issued.v1` tới Booking; `ticket.issuance-failed.v1` tới Payment; `identity.user-created.v1` tới User. Get messages để kiểm body rồi ACK/dọn message thử.
- [x] **5. Kiểm DLQ qua UI.** Đối chiếu đầy đủ 15 policy/mapping. Với một queue đại diện, nếu UI có reject/nack không requeue, kiểm message sang đúng DLQ và `x-death` ghi đúng nguồn. Nếu UI không hỗ trợ, ghi rõ hành vi này chưa được kiểm; không tự thêm AMQP client/harness.
- [x] **6. Kiểm persistence và idempotency.** Publish persistent message, restart Docker container rồi xác nhận message còn qua UI. Force-recreate service với cùng volume để chạy lại native import/hook; kiểm số queue/binding không tăng và tài khoản vẫn đúng. ACK message thử sau kiểm. Không gọi script bootstrap hoặc import CLI thủ công.
- [x] **7. Kiểm user/ACL trong Admin UI.** Đối chiếu ba user, tags, configure/write/read regex; guest không tồn tại. Không thêm management tag cho app/identity để test. Ghi rõ UI chỉ xác nhận cấu hình, chưa chứng minh enforcement AMQP ACL/confirm/mandatory.
- [x] **8. Dọn tài nguyên thử.** Trước `down --volumes`, xác minh project labels và volume/network thuộc tên test. Chỉ xóa override tạm bằng literal path đã biết. Không xóa dữ liệu stack đang dùng.
- [x] **9. Viết README ngắn.** Lệnh chạy từ root:

  ```powershell
  docker compose --env-file .env -f deploy/docker-compose.postgresql.yml -f deploy/docker-compose.keycloak.yml -f deploy/docker-compose.redis.yml -f deploy/docker-compose.rabbitmq.yml up -d --wait rabbitmq
  ```

  Ghi env cần điền, UI port, import native khi boot, user/ACL inline Compose, DLQ không tự xử lý lại, single-node không HA. Root commands PostgreSQL/Keycloak dùng cùng bộ file và target service tương ứng; MongoDB giữ riêng. Nếu hook lỗi, sửa config/env rồi thêm `--force-recreate` vào lệnh up chỉ target RabbitMQ, giữ volume. Docker daemon restart dùng dữ liệu đã lưu; không giả định post_start chạy lại khi daemon tự restart container.
- [x] **10. Áp dụng local sau fresh check.** Chỉ target RabbitMQ; không restart PostgreSQL/Keycloak khi không đổi. Nếu broker cũ có conflict type/message, giữ dữ liệu và báo; không purge/remove-orphans/down -v để sửa. Kiểm UI local ở chế độ đọc.
- [x] **11. Verification cuối.** Chạy `git diff --check` qua rtk; rà 3 file runtime mới và thay đổi env mẫu/README. Không secret, script test hoặc file runtime thừa. Báo đúng ca đã kiểm và ca chưa kiểm; không tuyên bố flow nghiệp vụ đã chạy.

## Self-review và handoff

- [x] Topology dùng native JSON import; config có vhost `/`, không phụ thuộc default-user creation.
- [x] User/ACL ở inline Compose hook, credentials chỉ lấy từ root env.
- [x] Chỉ ba file runtime, không bootstrap.sh hoặc test harness.
- [x] Phân biệt boot import, user readiness và giới hạn kiểm chứng qua UI.
- [x] Có fresh-volume check, restart/force-recreate và bảo vệ dữ liệu cũ.
- [x] Người dùng duyệt thực thi; đã triển khai tuần tự trong checkout hiện tại.

**Đề xuất thực thi:** Tuần tự trong chat này, không cần subagent.
