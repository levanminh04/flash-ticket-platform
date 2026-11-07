# Task 1.4 — Nguyên nhân lỗi và đề xuất fix (04/11/2026)

Kết quả lượt kiểm thử đã lưu: build PASS, 63 nhóm fixture PASS; live 12 PASS / 4 FAIL / 4 BLOCKED. Login/reload/refresh/logout buyer thật đã PASS. Chưa nghiệm thu toàn luồng; ghi chú này là điều tra và đề xuất, chưa sửa Java/config runtime.

## 1. Catalog guest trả 401 — Gateway thiếu rule và route

**Bằng chứng:** GatewaySecurityConfiguration.springSecurityFilterChain chỉ permit GET availability và /api/v1/events/*; thiếu GET /api/v1/events, /api/v1/categories, /api/v1/venues. Ba request rơi vào anyExchange().authenticated(). GatewayRouteConfiguration.customRouteLocator cũng chưa có route cho list/categories/venues tới Event.

**Đề xuất:** sửa hai method hiện có: thêm permitAll đúng ba GET public và route tới lb://event-service; tái sử dụng standardFilters hiện có. Giữ thứ tự availability trước detail; không mở rộng public toàn bộ /api/v1/**. Không cần tạo class/method Java mới.

**Nghiệm thu:** guest đọc đủ ba API HTTP200 theo DTO; detail/availability đi đúng service; API buyer/internal vẫn được bảo vệ. Cần Event Service hoạt động để kiểm qua Gateway, thêm permit đơn thuần chưa đủ.

## 2. GET /api/v1/me trả 503 — Không có instance User Service

**Bằng chứng runtime:** Eureka local chỉ có API-GATEWAY UP. Route /api/v1/me đã tồn tại, trỏ lb://user-service. Log Gateway ghi "No servers available for service: user-service", gồm đúng thời điểm user test.

**Đề xuất:** chạy User Service và dependency đúng cấu hình; đăng ký vào cùng Eureka Gateway đang dùng, bảo đảm host/port instance thực sự truy cập được từ Gateway. Không đổi lỗi này thành public endpoint hoặc bỏ JWT.

**Nghiệm thu:** USER-SERVICE UP trong Eureka, buyer gọi /me HTTP200, guest vẫn401; sau đăng ký kiểm projection User Service. Không kết luận lỗi token vì request thật đã qua xác thực và đi tới LoadBalancer.

## 3. Booking/Payment liên tục restart — JDBC trỏ loopback không có PostgreSQL

**Bằng chứng:** docker inspect cho thấy hai container chạy network_mode=host. BOOKING_DB_URL và PAYMENT_DB_URL đều jdbc:postgresql://127.0.0.1:5432/...; root .env cũng cùng giá trị. Config Server cấp spring.datasource.url từ đúng biến này. Trong Docker host network: nc kiểm 5432 thất bại, 8761/8888 thành công. Windows cũng không thấy listener5432. Log cả hai service ghi Connection to 127.0.0.1:5432 refused, rồi Hibernate không lấy được JDBC metadata.

**Kết luận:** nguyên nhân trực tiếp là endpoint DB không có listener/tunnel phục vụ; lỗi không xác định Dialect là hệ quả. Chưa có bằng chứng sai password/schema vì kết nối TCP chưa thành công.

**Đề xuất:** khôi phục DB/tunnel đã chốt hoặc chỉnh endpoint DB thực sự truy cập được từ môi trường Docker vào file env được dùng; đồng bộ env example. Nếu Config Server dùng biến đã thay, recreate đúng container để nạp env mới. Không chữa bằng cách chỉ thêm hibernate.dialect. Sau đó kiểm quyền/schema, startup và restart count ổn định.

**Lưu ý discovery:** hai runtime còn EUREKA_CLIENT_ENABLED=false. Nếu dùng chúng qua Gateway lb:// trong môi trường test local thì cần cấu hình tham gia Eureka hoặc route endpoint trực tiếp phù hợp môi trường. Không bật Eureka hàng loạt cho profile triển khai119 đã chủ ý tắt.

## 4. Phần Payment v1 hiện tại cần route Gateway

Trong lúc điều tra, source frontend đang được cập nhật: paymentService.initiatePayment đã POST /api/v1/payments với Idempotency-Key; getPaymentStatus đã GET /api/v1/orders/{id}/payment; checkout có nút thanh toán và result page đọc trạng thái backend, không còn confirmVNPayReturn gọi IPN. Đây là code đang thay đổi, chưa coi là đã nghiệm thu/deploy; nhận xét legacy trong báo cáo1.4 trước đó chỉ mô tả thời điểm chạy trước.

Gateway hiện chưa có route POST /api/v1/payments; vẫn có route cũ /orders/*/payment-attempts sang Booking. PaymentController.createPayment thực tế nhận POST /api/v1/payments.

**Đề xuất:** bổ sung route POST /api/v1/payments sang lb://payment-service và rule BUYER trong hai method Gateway hiện có. Giữ GET trạng thái tới Payment. Xác minh VNPAY_APP_RETURN_URL đang dùng là URL web đúng môi trường trước khi test; để Payment xử lý IPN thật. Sau đó nghiệm thu tiền -> vé -> Booking COMPLETED, không chỉ paymentUrl/return302.

## Thứ tự thực hiện

1. Khôi phục kết nối DB và runtime Booking/Payment.
2. Chạy Event/User, thống nhất discovery cho các service dùng Gateway lb://.
3. Bổ sung public route/security catalog và route/security Payment POST.
4. Chạy lại task1.4, bao gồm order/voucher thật và VNPay sandbox/Ticket sau khi đủ dependency.

Không cần thêm class/method Java cho phần Gateway nêu trên; chỉ sửa springSecurityFilterChain và customRouteLocator, tái sử dụng standardFilters. Đây chưa phải phê duyệt triển khai Java hoặc xác nhận toàn bộ task1.3 hoàn tất.

Bằng chứng repo: frontend/docs/task-1.4-results.md; output/playwright/task14-live.json và task14-preflight.json. Nguồn đối chiếu framework: [Spring Security reactive authorization](https://docs.spring.io/spring-security/reference/reactive/authorization/authorize-http-requests.html), [Gateway ReactiveLoadBalancerClientFilter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/global-filters.html#reactive-loadbalancer-client-filter).

Giữ [task1.4 — Kiểm thử toàn luồng frontend với backend](https://app.clickup.com/t/z8qgvcbr2x) chưa Complete.
