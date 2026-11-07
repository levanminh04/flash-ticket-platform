# Task 1.3 — Payment/VNPay và vé

Phạm vi đã được user yêu cầu triển khai: checkout tạo Payment v1, chuyển VNPay, trang quay lại xác nhận qua backend, vé/lịch sử dùng API hiện có. Giữ React/Vite và giao diện vàng hiện tại, không thêm dependency hoặc tự triển khai119.

Delta phát hiện khi kiểm route: Gateway thiếu POST /api/v1/payments và GET callback return/IPN. Bổ sung vào customRouteLocator và springSecurityFilterChain hiện có, chỉ hai GET callback public; create giữ BUYER. Sửa assertions trong test hiện có; không thêm class/method Java hoặc đổi chữ ký/annotation. Không sửa nghiệp vụ Payment/Booking/Ticket.

## Contract và lựa chọn

- Payment trên dev/son: POST `/api/v1/payments` với `{orderId}` và UUID Idempotency-Key, trả ApiResponse<PaymentStart>. GET `/api/v1/orders/{orderId}/payment` trả ApiResponse<PaymentView>. GET không truyền requestId HTTP create vì trường này tra inbox command Rabbit, không phải HTTP Idempotency-Key.
- Backend nhận VNPay return, xác minh chữ ký rồi redirect về FE với orderId. FE bỏ qua status/responseCode trên URL để quyết định thành công; không gửi IPN. Phân biệt attempt SUCCEEDED, acceptedChargeId và vé phát hành.
- Giữ key thanh toán theo order/buyer/gateway/issuer qua lỗi mạng/reload. Chỉ cấp key mới cho lần thử mới khi server xác nhận attempt FAILED. URL checkout phải là HTTPS thuộc host VNPay; không dùng URL từ query.
- Poll Payment tuần tự có giới hạn, cleanup khi chuyển trang/buyer, dừng khi lỗi auth/không có quyền. Hết lượt vẫn là đang chờ, có nút kiểm tra lại; không tự kết luận thất bại.
- Ticket theo origin/dev/tuyen commit1bc263a: GET `/api/v1/orders/{orderId}/tickets`; GET `/api/v1/tickets/{ticketId}/qr` Accept:image/png. Dùng ảnh blob đã xác thực, thu hồi objectURL khi đóng/chuyển trang; không đưa QR token vào URL/dịch vụ ngoài/storage. Chỉ xem QR vé VALID.
- Chưa có list toàn bộ order/ticket của buyer hoặc GET Booking order. MyOrders/MyTickets chuyển thành tra cứu theo orderId, nói rõ giới hạn; không bịa lịch sử hoặc dữ liệu event/giá Ticket không trả. API list là phần thiếu để nghiệm thu đầy đủ.

## Triển khai và kiểm chứng

- [x] Viết browser test trước: callback giả không thành công; create body/key/envelope; retry; state waiting/unknown/failed/accepted; Ticket pending/completed/void/QR; auth/missing/foreign ID; không gọi legacy/IPN. Baseline gọi legacy endpoint bị404.
- [x] Thay paymentService/ticketService bằng DTO wire v1, nối Checkout.
- [x] Viết lại PaymentResult, MyOrders và MyTickets bằng contract hiện có; VI/EN, responsive, focus/loading/error/empty.
- [x] Root .env: đổi VNPAY_APP_RETURN_URL sang FE local; đồng bộ template. Giữ provider return đi backend. Còn recreate Payment để runtime nhận env; không tự deploy119 trong task FE.
- [x] Run test mới, Booking/Event/Auth hồi quy, build và xem ảnh desktop/mobile. Ghi kết quả và phần thiếu vào task-1.3-progress.md; ClickUp đang hết quota.
- [x] Bổ sung route/security Gateway; Docker Maven Java21 chạy `mvn -q verify` PASS,19 tests cùng Spotless/SpotBugs. Không thêm class/method Java.

Không coi fixture là thanh toán Sandbox thật. Không commit/push trong task này khi user chưa yêu cầu bàn giao Git.
