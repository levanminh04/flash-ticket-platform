# Task 1.3 — Tiến độ triển khai

[Task ClickUp1.3](https://app.clickup.com/t/z8qgvcbr2w). Code FE đã triển khai local; chưa nghiệm thu Sandbox/backend thật. ClickUp connector đã báo hết hạn mức trong phiên trước; ghi chú này chưa được đồng bộ lên task.

## Đã triển khai

- Checkout POST `/api/v1/payments` body `{orderId}`, header UUID Idempotency-Key, đọc ApiResponse<PaymentStart>. Không truyền giá/clientIp do browser tự tính. Khóa yêu cầu tách gateway/issuer/buyer/order, giữ qua lỗi mạng/reload. Chỉ cấp key mới khi người mua chủ động và GET xác nhận attempt cũ FAILED; mất phản hồi của lần mới vẫn giữ key mới đó.
- URL thanh toán lấy từ backend, chỉ chấp nhận HTTPS host sandbox.vnpayment.vn/payment.vnpay.vn, không userinfo/port lạ, attempt phải PENDING/còn thời hạn. Không dùng URL query để điều hướng.
- Trang `/payment-result` và `/payment/result` GET `/api/v1/orders/{orderId}/payment`; không gọi browser IPN, không tin status/responseCode trên URL. Không truyền requestId vì tham số đó tra inbox Rabbit, không phải HTTP Idempotency-Key.
- Phân biệt PENDING/UNKNOWN, SUCCEEDED chờ chấp nhận và acceptedChargeId được xác nhận; không coi thu tiền thành công là vé đã phát. Hiển thị refund record nếu backend trả, không thêm nghiệp vụ refund.
- Poll tuần tự tối đa10 lần/cách3s, timeout request, cleanup khi đổi trang/buyer/mất đăng nhập. Hết lượt vẫn chưa rõ kết quả, có nút kiểm tra lại. Lỗi quyền/network/wrapper success=false xóa trạng thái xác nhận cũ trên màn hình.
- MyOrders/MyTickets thay endpoint legacy bằng tra cứu theo mã đơn. Ticket GET `/api/v1/orders/{orderId}/tickets`, hiển thị issuanceState và TicketView, không bịa metadata event/giá mà Ticket không trả.
- QR vé VALID dùng GET `/api/v1/tickets/{ticketId}/qr` với Bearer và Accept:image/png; render blob URL trong native dialog, Escape/trả focus, abort/revoke khi đóng/đổi buyer. Không đưa qrToken vào URL/storage/dịch vụ ngoài. USED/VOID không có nút QR.
- VI/EN, desktop/mobile, loading/error/empty; ẩn chatbot ở màn thanh toán/vé để không che nội dung.
- Root .env đổi VNPAY_APP_RETURN_URL sang http://127.0.0.1:5173/payment-result, đồng bộ .env.example. Provider return vẫn đi API backend xác minh chữ ký. Chưa recreate/redeploy Payment.

## Kiểm chứng FE

- `tests/payment-ticket.browser.js`:16 nhóm PASS: create/envelope/key; callback giả; UNKNOWN; acceptedChargeId vs attempt; tiền lớn; wrapped error/404; retry kiểm lại server/giữ key khi mất phản hồi; URL an toàn; checkout chuyển VNPay fixture; Ticket/QR/auth/blob/VOID; lookup; VI/EN/responsive; giới hạn polling và guest/buyer cleanup.
- Hồi quy Booking15 và Event/Auth10 nhóm PASS. Tổng41 nhóm browser fixture; không giao dịch Sandbox/ghi DB119.
- Build npm run build PASS,2130 modules; còn cảnh báo chunk lớn và asset chatbot có sẵn. Ảnh output/playwright/fe-payment-*.png; đã xem Payment accepted1440/320, tickets390 và ordersEN320. Đo320/390/1440px không tràn ngang.
- Payment đối chiếu source dev/son; Ticket đọc origin/dev/tuyen commit1bc263a sau fetch vì local TicketController còn scaffold.

## Delta Gateway

Khi rà soát phát hiện thiếu route POST /api/v1/payments và GET return/IPN. Đã bổ sung trong customRouteLocator và springSecurityFilterChain hiện có: POST create yêu cầu BUYER; chỉ GET đúng hai callback public để Payment xác minh chữ ký. Giữ rate limit/circuit breaker/forward query/header và bảo vệ các API khác. Không thêm class/method Java, không đổi signature/annotation hoặc nghiệp vụ. Test baseline Java21 tái hiện3 lỗi:15 route thay vì18, callback bị chặn và organizer được qua security mặc định ở create payment.

Đã mở rộng assertions trong các test có sẵn, gồm HTTP thật với JWT ký bằng khóa fixture/discovery/downstream thay thế: POST body và Idempotency-Key được forward, GET callback giữ nguyên query. **Docker maven:3.9.9-eclipse-temurin-21 chạy `mvn -q verify` PASS (exit0),19 tests,0 failure/error/skip; Spotless và SpotBugs pass.** Máy mặc định JDK23 bị enforcer từ chối, không bỏ enforcer để chạy. Báo cáo tại infrastructure/api-gateway/target/surefire-reports và target/spotbugsXml.xml. Chưa rebuild/recreate container Gateway đang chạy.

## Còn thiếu để nghiệm thu

- [ ] Backend/Gateway và buyer thật từ1.1 sẵn sàng; catalog local18081 vẫnHTTP401 trong lượt này. Fixture không chứng minh API thật hoạt động.
- [ ] Recreate Gateway/Payment bằng env mới; kiểm provider return/IPN URL công khai, callback VNPay → backend → FE thật.
- [ ] Ticket runtime đúng code business dev/tuyen, bật business feature, discovery trỏ đúng môi trường.
- [ ] Test Sandbox thật: thanh toán, return trước IPN, IPN chậm/UNKNOWN, accepted, phát vé, QR và quyền sở hữu. Ảnh1pixel fixture chỉ kiểm transport QR, chưa kiểm giải mã/quét mã thật.
- [ ] Booking chưa có API đọc lại đơn; Payment/Ticket chưa có API list toàn bộ đơn/vé buyer. Tra cứu orderId đã dùng endpoint thật nhưng chưa phải toàn bộ lịch sử. Thống nhất contract bổ sung trước khi tích hợp đầy đủ; Java mới vẫn qua gate.
- [ ] Đồng bộ ClickUp khi connector dùng được; E2E1.4. Chưa commit/push/deploy trong lượt này.
