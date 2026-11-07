# Spec: Setup Redis

- Ngày: 2026-10-21.
- Trạng thái: IMPLEMENTED_LOCAL — Redis local đã chạy healthy và các smoke check đạt; Spring Boot chỉ bàn giao tài liệu.
- Phạm vi: nền hạ tầng local, kèm bàn giao tích hợp Spring Boot.
- Điều chỉnh ngày 2026-10-21: người dùng xác nhận chỉ chạy local trước; bỏ EC2 khỏi task.

## 1. Mục tiêu

Có Redis chạy được bằng Docker Compose và hướng dẫn để backend kết nối.
Chỉ setup một Redis standalone; chưa thêm cache nghiệp vụ, sửa mã service,
distributed lock hoặc HA/cluster. Giữ chỗ, tồn kho và idempotency nghiệp vụ
tiếp tục theo datastore đã thiết kế.

## 2. Đầu ra yêu cầu

### 2.1. Env ở thư mục root

- Cập nhật cả `.env` và `.env.example` tại root; tái sử dụng các biến đã có:
  `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`.
- Biến nào Compose cần thêm phải có ở cả hai file, có chú thích ngắn.
- `.env` chứa giá trị chạy thực tế theo môi trường, không commit. Giữ nguyên
  các cấu hình khác; không thay credential đang dùng nếu không cần thiết.
- `.env.example` không chứa secret thật. Password Redis phải được điền trước
  khi chạy; Compose báo lỗi rõ nếu thiếu.

### 2.2. Docker Compose trong deploy

- Tạo `deploy/docker-compose.redis.yml`, pin phiên bản image cụ thể.
- Lấy giá trị từ env ở root, ngoài Git.
- Có password, healthcheck xác thực bằng PING và restart policy.
- Dùng network `flash-ticket-infra` theo mẫu deploy hiện tại. Chỉ publish
  port trên loopback. Kiểm port local trước khi chạy để tránh trùng port
  hoặc ảnh hưởng container đang dùng.

Lệnh chạy từ root sau khi triển khai file:

```powershell
docker compose --env-file .env -f deploy/docker-compose.redis.yml up -d --wait
```

### 2.3. Chạy thành công

Task triển khai chỉ hoàn thành khi:

- Compose hợp lệ, container chạy và trạng thái healthy.
- Kết nối với password đúng trả PONG; thiếu/sai password không được truy cập.
- SET/GET một key thử có TTL thành công và key hết hạn đúng dự kiến.
- Có kết quả kiểm tra local. Phần chưa chạy ghi `NOT_RUN`, không coi là
  hoàn thành phần đó. Không ghi password vào báo cáo.

## 3. Bàn giao Spring Boot

Tạo `deploy/redis/handoff.md` ngắn, gồm:

- Dependency Spring Data Redis cho MVC và reactive cho WebFlux; dùng Lettuce
  đi kèm starter, không thêm thư viện client khác.
- Mẫu YAML `spring.data.redis.*` ánh xạ các biến env ở root.
- Cách nạp `.env` vào IDE/runtime vì Spring không tự đọc file này; đặt cấu
  hình tại Config Server nếu service dùng cấu hình tập trung.
- Host kết nối tương ứng: JVM local và container cùng network.
- Một ví dụ đọc/ghi key có TTL và lệnh kiểm tra kết nối. Ví dụ là hướng dẫn
  tích hợp sau này, không phải bằng chứng service đã tích hợp Redis.

Không thay dependency hoặc cấu hình của các service trong task setup này.

## 4. Giới hạn nghiệm thu

Redis chạy thành công chứng minh nền hạ tầng sẵn sàng. Cache nghiệp vụ và
kiểm thử Spring Boot đầu-cuối thuộc task tích hợp sau.
