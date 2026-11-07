# Kiểm tra Docker Compose Payment — 02/11/2026

- Source: dev/son sau merge ff72b28. Không sửa code hoặc ghi secret trong lượt kiểm này.
- Sau user cập nhật .env: `docker compose --env-file .env -f deploy/docker-compose.payment.yml config --quiet` pass, không thiếu biến bắt buộc.
- Dockerfile Java21 build thành công; image `flash-ticket/payment-service:local`, SHA256 `eb499f23ff9e29b562a6a087b7f96455dfbce880812e7fea3b474560aafd078b`. Image build bỏ test theo Dockerfile; không lấy build này thay cho suite đã chạy trước.
- Đã chạy `docker compose --env-file .env -f deploy/docker-compose.payment.yml up -d --no-build`.
- Payment unhealthy/restarting: ConfigClientFailFastException, GET `http://host.docker.internal:8888/payment-service/default` bị Connection refused. Ngrok phụ thuộc Payment healthy nên chưa khởi động.
- Kiểm tra host: health Config Server 127.0.0.1:8888 và Booking 127.0.0.1:8082 đều không truy cập được ở thời điểm kiểm. Chưa kết luận DB/Rabbit/OAuth runtime hoặc giao dịch nghiệp vụ hoạt động vì app chưa vượt bước Config Server.
- Đã stop hai container Payment/ngrok sau khi chẩn đoán, giữ image/container để chạy lại; không tác động broker/DB119.

URL lấy snapshot được xác nhận từ environment container và code client:

```text
POST http://host.docker.internal:8082/internal/v1/orders/{orderId}/purchase-snapshot
Content-Type: application/json
Authorization: Bearer <machine token>

{"buyerSubject":"<subject người mua>"}
```

Compose lấy base URL Booking từ PAYMENT_DOCKER_BOOKING_URL trong .env, mặc định http://host.docker.internal:8082; không lấy BOOKING_SERVICE_URL trực tiếp từ .env cho entry này. Config Server lấy PAYMENT_DOCKER_CONFIG_SERVER_URL, mặc định http://host.docker.internal:8888. host.docker.internal chỉ máy host từ container, không phải119. Cần chạy dependency tại các địa chỉ này hoặc cấu hình đúng URL dependency trước khi khởi động lại.
