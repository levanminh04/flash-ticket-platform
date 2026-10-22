# Chạy lại test mapping trong PostgreSQL riêng

Yêu cầu Docker đang chạy, JDK 21 và port 55483 chưa được dùng. Chạy các bước từ root repository. Tên container dưới đây dành riêng cho test; nếu tên/port đã tồn tại, chọn tên/port khác và đổi JDBC URL tương ứng. Không trỏ vào container hoặc volume deploy hiện có.

## 1. Tạo instance test

```powershell
$testPassword = [guid]::NewGuid().ToString('N')
$env:POSTGRES_PASSWORD = $testPassword
docker run -d --name payment-scaffold-mapping-check `
  -e POSTGRES_USER=payment_owner -e POSTGRES_DB=payment_db -e POSTGRES_PASSWORD `
  -p 127.0.0.1:55483:5432 postgres:16.15

docker exec payment-scaffold-mapping-check pg_isready -U payment_owner -d payment_db
```

Chờ pg_isready báo accepting connections trước bước tiếp theo. Container không mount bất kỳ volume đang có nào. payment_owner là role bootstrap chỉ trong instance test mới.

## 2. Áp schema chuẩn và tạo role app

```powershell
"CREATE SCHEMA payment_schema AUTHORIZATION payment_owner; CREATE ROLE payment_app LOGIN PASSWORD '$testPassword';" |
  docker exec -i payment-scaffold-mapping-check psql -U payment_owner -d payment_db -v ON_ERROR_STOP=1

docker cp docs/architecture/data/postgres/30-payment.sql payment-scaffold-mapping-check:/tmp/payment.sql
docker cp services/99-table-prefix.sql payment-scaffold-mapping-check:/tmp/prefix.sql
docker exec payment-scaffold-mapping-check psql -U payment_owner -d payment_db -v ON_ERROR_STOP=1 `
  -f /tmp/payment.sql -f /tmp/prefix.sql `
  -c 'GRANT USAGE ON SCHEMA payment_schema TO payment_app; GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA payment_schema TO payment_app;'
```

Chỉ chạy schema một lần trên instance mới. Giữ nguyên ON_ERROR_STOP; dừng nếu DDL lỗi. Không dùng UPDATE/CREATE tự động của Hibernate.

## 3. Chạy suite đầy đủ

```powershell
$env:PAYMENT_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:55483/payment_db'
$env:PAYMENT_TEST_DB_USERNAME = 'payment_app'
$env:PAYMENT_TEST_DB_PASSWORD = $testPassword
$env:JAVA_HOME = '<duong-dan-JDK-21>'
Push-Location services/payment-service
try {
  .\mvnw.cmd -Pmapping-tests verify -B -ntp
} finally {
  Pop-Location
}
```

Test mapping bật PostgreSQL/JPA thật, không cần Config Server, RabbitMQ hoặc Eureka. Nó kiểm cả graph quan hệ, nullable composite join, FK/unique, tiền 19 chữ số, timestamp và optimistic locking. Test cạnh tranh có commit fixture, nên instance phải tách biệt. Kết quả BUILD SUCCESS mới xác nhận các kiểm đã chạy; không coi smoke context thường là bằng chứng DB mapping.

## 4. Dọn instance test

Sau khi không cần dữ liệu fixture, xóa đúng container test vừa tạo:

```powershell
docker rm -f payment-scaffold-mapping-check
Remove-Item Env:POSTGRES_PASSWORD,Env:PAYMENT_TEST_DB_PASSWORD -ErrorAction SilentlyContinue
$testPassword = $null
```

Không chạy lệnh xóa cho container/volume deploy. Không commit credential hoặc log toàn bộ environment.
