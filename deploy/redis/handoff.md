# Redis local — bàn giao Spring Boot

## Chạy Redis

Chạy từ root repository. Điền `REDIS_HOST=localhost`, `REDIS_PORT=6379` và
`REDIS_PASSWORD` trong `.env` gốc; tham khảo `.env.example`. Password bắt buộc,
không commit `.env`. Nếu password chứa `$`, `#` hoặc khoảng trắng, bọc giá trị
bằng dấu nháy đơn trong dotenv để giữ nguyên ký tự.

```powershell
docker compose --env-file .env -f deploy/docker-compose.redis.yml up -d --wait redis
docker compose --env-file .env -f deploy/docker-compose.redis.yml ps
```

Mong đợi `redis` báo `healthy`. Compose dùng image `redis:7.4.11-alpine`,
publish port trên `0.0.0.0` (mọi interface IPv4 của host), network `flash-ticket-infra` và volume
`redis-data`. Nếu port đang được dùng, đổi `REDIS_PORT` trong `.env` rồi chạy lại.
Khi chạy riêng file Redis, Compose có thể báo orphan với container cùng project;
không dùng `--remove-orphans` để xử lý cảnh báo đó.

Dừng riêng Redis bằng `docker compose --env-file .env -f deploy/docker-compose.redis.yml stop redis`.
Chạy lệnh `up` ở trên để bật lại hoặc áp dụng thay đổi password; `restart`
không nạp env mới. Không dùng `down -v` trên stack dùng chung. Volume dùng RDB
mặc định, không bảo đảm lưu mọi thay đổi gần nhất và không thay thế backup.

## Kết nối Spring Boot

| Nơi chạy Spring | `REDIS_HOST` | `REDIS_PORT` |
| --- | --- | --- |
| IDE/JVM trên máy local | `localhost` | Published port trong `.env`, mặc định `6379` |
| Container cùng network `flash-ticket-infra` | `redis` | `6379` |

Container ứng dụng nhận env của riêng process đó; không đổi published port
của Compose thành endpoint nội bộ. Phạm vi hiện tại chỉ local.

Thêm **một** starter theo ứng dụng, để parent Spring Boot quản lý version
(repo hiện dùng Boot 4.1.1/Java 21). MVC dùng:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

WebFlux dùng `spring-boot-starter-data-redis-reactive` thay artifact trên;
Gateway đã có starter này. Dùng Lettuce đi kèm, không thêm Jedis/Redisson.

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}
      connect-timeout: 2s
      timeout: 2s
```

Spring không tự đọc `.env`: cấu hình IDE/runtime nạp các biến vào process.
Service dùng Config Server đặt YAML trên trong file cấu hình của chính service
tại `infrastructure/config-server/src/main/resources/config/`. Config Client
nhận các property source từ server; nạp `REDIS_*` vào process của service
dùng Redis để resolve placeholder. Chỉ nạp `.env` cho lệnh Compose không
tự truyền biến sang JVM đang chạy trong IDE.
Giữ secret ngoài Git, không đặt Redis vào cấu hình chung để ép mọi service sử dụng.

MVC inject `StringRedisTemplate`; ví dụ với biến `redis` là template được inject:

```java
String key = "flash-ticket:demo:" + java.util.UUID.randomUUID();
redis.opsForValue().set(key, "ok", java.time.Duration.ofSeconds(30));
String value = redis.opsForValue().get(key); // Mong đợi "ok".
redis.delete(key);
```

WebFlux inject `ReactiveStringRedisTemplate`, trả `Mono`/`Flux` trong luồng reactive;
không dùng template blocking hoặc `.block()` trên event loop.
Key nghiệp vụ sau này cần prefix theo service và TTL phù hợp. Giữ chỗ, tồn kho
và idempotency nghiệp vụ vẫn theo datastore đã thiết kế.

Tham khảo [Spring Boot Redis](https://docs.spring.io/spring-boot/reference/data/nosql.html#data.nosql.redis).
Task này chỉ bàn giao ví dụ; chưa sửa hoặc chạy tích hợp Spring Boot đầu-cuối.

## Smoke check có thể chạy lại

PowerShell, từ root. Script chỉ tạo một key thử có TTL, không in password:

```powershell
$redisSmoke = @'
export REDISCLI_AUTH="$REDIS_PASSWORD"
key="flash-ticket:smoke:$(cat /proc/sys/kernel/random/uuid)"
test "$(redis-cli --raw PING)" = PONG
test "$(redis-cli --raw SET "$key" ok EX 5)" = OK
test "$(redis-cli --raw GET "$key")" = ok
test "$(redis-cli --raw TTL "$key")" -gt 0
sleep 6
test "$(redis-cli --raw EXISTS "$key")" = 0
unset REDISCLI_AUTH
case "$(redis-cli --raw GET "$key" 2>&1)" in *NOAUTH*) ;; *) exit 1;; esac
export REDISCLI_AUTH="invalid-$REDIS_PASSWORD"
case "$(redis-cli --raw GET "$key" 2>&1)" in *WRONGPASS*) ;; *) exit 1;; esac
echo "PASS: PING, SET/GET, TTL, NOAUTH, WRONGPASS"
'@
($redisSmoke -replace "`r", "") | docker compose --env-file .env -f deploy/docker-compose.redis.yml exec -T redis sh -es
if ($LASTEXITCODE -ne 0) { throw 'Redis smoke check failed' }
```

Trên Linux, đưa phần nội dung `$redisSmoke` vào stdin của cùng lệnh
`docker compose ... exec -T redis sh -es`. Chỉ exit 0 với dòng PASS cuối
mới được tính thành công; không in resolved Compose config hoặc container env
vì có password.

## Kết quả kiểm tra — 2026-10-21

| Kiểm tra local | Kết quả |
| --- | --- |
| Compose hợp lệ, Redis healthy | PASS |
| Authenticated PING qua host `127.0.0.1:6379` | PASS |
| SET/GET, TTL dương và key tự hết hạn | PASS |
| Không auth / password sai bị từ chối | PASS |
| Env thiếu hoặc rỗng password bị từ chối trước startup | PASS |
| Password thử có `$`, `#`, khoảng trắng | PASS |

EC2 đã được người dùng bỏ khỏi phạm vi. Spring Boot end-to-end chưa triển khai.
