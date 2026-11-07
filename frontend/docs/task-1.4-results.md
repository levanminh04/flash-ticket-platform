# Task 1.4 — Kết quả kiểm thử ngày 04/11/2026

**Chưa đạt nghiệm thu toàn luồng.** Đã triển khai runner và chạy build, hồi quy fixture, kiểm tra môi trường và user test trên Keycloak/backend thật. Không đánh dấu Complete chỉ theo kết quả fixture.

## Kết quả chạy

| Bộ kiểm tra | Kết quả | Bằng chứng từ root repo |
| --- | --- | --- |
| `npm run build` | PASS; 2130 modules, còn cảnh báo chunk lớn/ảnh chatbot | Output build của lượt này |
| `node tests/run-integration.cjs --fixtures` | 7 file,63 nhómPASS, exit0 | `output/playwright/task14-fixtures.json` |
| `node tests/run-integration.cjs --live` | 12 PASS,4 FAIL,4 BLOCKED; nonzero (runner đặt2, RTK chuẩn hóa thành1) | `output/playwright/task14-live.json` |
| Runtime preflight | Catalog401; relay health Booking/Payment đóng kết nối; container tăng restart count | `output/playwright/task14-preflight.json` |

Fixture: public-api10, header9, home-layout11, home-colors7, home-polish9, booking-flow15, keycloak-nonce2. Có kiểm QUANTITY/STANDING/SEATED, sector pool, Idempotency-Key/replay sau mất mạng/reload, voucher và giá server, freeze/deadline/cancel, cursor, nonce sai, refresh lỗi không gửi token cũ, responsive và thao tác bàn phím. Fixture không ghi dữ liệu119 và không chứng minh các service phối hợp thành công.

## Ma trận user test thật

Chạy Chromium headed, context mới, FE127.0.0.1:5173, Gateway theo root `.env`, Keycloak119 realm `flash-ticket`, client `frontend`. Dùng buyer seed trong `deploy/keycloak/postman/SEED-README.md`; không đổi tài khoản/role và không lưu credentials/JWT vào báo cáo.

| Bước | Trạng thái | Kết quả quan sát |
| --- | --- | --- |
| Mở frontend | PASS | HTTP200 |
| Guest đọc events/categories/venues | FAIL — 3 kiểm tra | Cả ba HTTP401 |
| Guest layout1440/390/320px | PASS — 3 kiểm tra | Không tràn ngang; có ảnh thật |
| Chuyển Keycloak | PASS | PKCE S256, có nonce, đúng client frontend |
| Đăng nhập buyer từ form | PASS | Token endpoint200, FE authenticated=true, role BUYER |
| Đọc profile | FAIL | GET `/api/v1/me` trả503 qua axios interceptor thật |
| Chi tiết Event/availability/seatmap | BLOCKED | Không có catalog đọc được để chọn event hợp lệ |
| Buyer layout1440/390/320px | PASS — 3 kiểm tra | Header thể hiện tài khoản; không tràn ngang |
| Reload | PASS | Tự khôi phục SSO; không inject token |
| Refresh | PASS | `updateToken(-1)` trảtrue |
| Đăng xuất bằng menu và xác nhận | PASS | Trở về guest, không còn token/cờ phiên |
| Tạo order/voucher thật | BLOCKED | Chưa có event/tồn kho thật qua Gateway để thực hiện giao dịch test |
| VNPay → Payment → Ticket → Booking COMPLETED | BLOCKED | Checkout chưa nối Payment v1 của1.3; runtime Booking/Payment không sẵn sàng |
| Đăng ký và projection hồ sơ | BLOCKED | Profile/User Service chưa phục vụ thành công để nghiệm thu projection |

Ảnh: `output/playwright/task14-guest-{1440,390,320}.png` và `task14-buyer-{1440,390,320}.png`. Đây là viewport Chromium, chưa phải thử trên thiết bị iPhone/Android vật lý.

## Điểm chặn có bằng chứng

1. **Gateway/Event/User — phụ thuộc1.1:** catalog guest401, `/api/v1/me`503. Cần route và permit public đúng ba GET, Event/User hoạt động và đăng ký discovery đúng môi trường. Sửa xong phải đọc dữ liệu thật, kiểm search/cursor/detail/seatmap/profile, không thay bằng fixture để nghiệm thu.
2. **Runtime Booking/Payment:** preflight ghi Booking2675 lần restart và Payment2588 lần restart; đây là số đếm tích lũy, không phải số lần test này gây ra. Log Payment ghi PostgreSQL `127.0.0.1:5432` từ chối kết nối và không lấy được JDBC metadata; Booking lỗi khởi tạo Hibernate do không lấy được metadata. Chưa thay `.env`, thêm dialect hoặc đổi DB theo suy đoán. Cần xác minh DB/tunnel và config đang nạp trước khi chạy giao dịch.
3. **Thanh toán frontend — phụ thuộc1.3:** `CheckoutPage.tsx` vẫn hiển thị thanh toán chưa khả dụng. `paymentService.ts` còn endpoint legacy `/api/payments/*`; `PaymentResultPage.tsx` còn nhánh relay `vnpay-ipn` từ browser. Chưa gọi các nhánh này trong lượt user test vì chưa có order thật. Cần hoàn tất1.3 theo Payment v1, đọc trạng thái backend, return web và Ticket; không dùng query `vnp_ResponseCode=00` làm bằng chứng đã thanh toán/phát vé.

## Chạy lại

Từ `frontend`, Vite phải phục vụ tại127.0.0.1:5173 và nạp root `.env`:

```powershell
rtk proxy npm run build
rtk proxy node tests/run-integration.cjs --fixtures
rtk proxy node tests/run-integration.cjs --live
```

Live runner kiểm auth/readiness thật, không tự tạo order hay thanh toán khi thiếu điều kiện đầu vào. Khi1.1/1.2/1.3 sẵn sàng, chọn một test event mở bán và chạy tiếp bằng UI: chọn vé/ghế → order có idempotency → áp voucher nếu có → VNPay sandbox → backend xác nhận tiền → đủ vé hợp lệ đúng order/seat → Booking COMPLETED. Đọc lại trạng thái, kiểm reload/timeout/lỗi và lưu ID/correlation cùng bằng chứng đã lọc secret. Order hết hạn không được tự tạo lại hoặc tự kéo dài deadline.

Kết luận task1.4 hiện là **INCOMPLETE**, không phải E2E PASS. Đã hoàn tất phần triển khai/running test có thể chạy trên checkout và môi trường hiện tại. Chưa commit/push/deploy, chưa đổi Java/config backend, chưa cập nhật ClickUp (connector đã hết quota trong chat).
