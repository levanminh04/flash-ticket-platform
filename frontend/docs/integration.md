# Tích hợp frontend — tiến độ 04/11/2026

Task ClickUp tổng: https://app.clickup.com/t/z8qgvcbr2n — **In progress**. Đã triển khai FE phần 1.1–1.3; chưa hoàn tất nghiệm thu backend/Sandbox thật. Chi tiết mới nhất: [tiến độ1.3](task-1.3-progress.md).

## Đã sửa trong phần 1.1

- Vite thực sự đọc `.env` root qua `envDir`/`loadEnv`. Bổ sung bốn biến public `VITE_API_GATEWAY_URL`, `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT_ID`; đồng bộ `.env.example`. Không đưa secret vào FE.
- Dev dùng proxy cùng origin cho `/api`; bản build dùng origin Gateway trong env. Bỏ proxy legacy `_core/_user` và monkey patch toàn cục fetch/XHR; Keycloak adapter trực tiếp quản lý request theo URL cấu hình. Giữ nonce mặc định, PKCE S256; silent-check-sso không fallback redirect toàn trang cho guest.
- Client Keycloak `frontend` trên 119 đã thêm redirect local `http://localhost:5173/*`, `http://127.0.0.1:5173/*` và hai web origins tương ứng. Giữ callback Postman. Backup hai trường trước sửa ở `/tmp/flashticket-frontend-client-before-local.json` trên 119. Không sửa user/mật khẩu/role/realm global.
- Thêm route `/login`, `/register`; refresh token lỗi thì clear token và hủy request, không gửi Bearer cũ. UI đọc realm role phù hợp backend. GET profile chuyển sang `/api/v1/me`; các chức năng chỉnh profile legacy chưa nằm trong phần này.
- `types/eventContract.ts` ghi wire DTO. `eventService.ts` mapping sang model hiển thị cũ, giữ UUID, ngày, tiền gốc dạng chuỗi và JSON sector/seat. Chưa coi sức chứa cấu hình là tồn kho còn lại; availability do Booking cấp ở phần 1.2.
- Public Event dùng GET `/api/v1/events`, `/api/v1/events/{UUID}`, `/api/v1/categories`, `/api/v1/venues`. Danh sách dùng `q`, `categoryId`, `size`, `cursor` và `{items,nextCursor}`. Seatmap public nằm trong detail; không gọi API seatmap dành organizer.
- Search chỉ hiển thị tìm theo tên/danh mục và chuyển trang cursor. Không hiển thị bộ lọc city/giá/ngày/sort mà API chưa hỗ trợ, không bịa total count hay số trang. Home phân loại sắp diễn ra/đã diễn ra trong trang dữ liệu đã tải; không tuyên bố đây là toàn bộ catalog.
- Thêm placeholder trung tính khi Event chưa có ảnh; mô tả là text, không thực thi HTML từ organizer. Hiển thị giá Event/Home/Search/Detail dùng chuỗi gốc + BigInt để giữ đủ chữ số.
- Caller public organizer đổi sang cursor để giữ khả năng biên dịch; DTO public mới chưa có organizer metadata để xác minh quan hệ. Trang organizer/admin và các API legacy liên quan chưa được tích hợp/nghiệm thu.

## Kiểm chứng

- Đã đăng nhập buyer seed **thật** qua Keycloak119: code exchange200, FE nhận role BUYER, refresh token200, reload khôi phục phiên và logout qua giao diện trở về guest. Đã nâng `keycloak-js` từ23.0.7 lên26.2.5 để tương thích nonce chỉ nằm trong ID token; giữ PKCE S256 và kiểm nonce. Tab từng đăng nhập lưu một cờ sessionStorage để cho phép redirect kiểm SSO khi silent SSO bị trình duyệt chặn; không lưu JWT. Đăng ký hoàn tất và User Service projection vẫn chưa nghiệm thu.
- `tests/public-api.browser.js`: 10 nhóm contract/security pass với fixture; trước sửa API trả404 do FE gọi legacy endpoint. Test mô tả cũng tái hiện được HTML thực thi trước sửa, sau sửa hiển thị text.
- Hồi quy: header 9, homepage 11, color 7, polish 9 nhóm pass với fixture DTO v1. Xem README test để chạy bằng Playwright MCP.
- Sau sửa login, build `npm run build` **PASS**, 2130 modules. Tổng48 nhóm contract/hồi quy pass: public-api10, header9, home-layout11, home-colors7, home-polish9, keycloak-nonce2 (nonce ID đúng được nhận dù access/refresh token thiếu nonce; nonce ID sai bị từ chối). Kiểm session thật bằng `tests/keycloak-session.browser.js`; báo cáo và ảnh `output/playwright/keycloak-login-uat-2026-11-04.md`, `keycloak-login-uat-passed-2026-11-04.png`. Còn cảnh báo chunk lớn và asset chatbot có sẵn. Kết quả fixture không thay cho nghiệm thu API thật.

## Phụ thuộc môi trường đã quan sát

| Thành phần | Kết quả quan sát | Cần có trước nghiệm thu thật |
|---|---|---|
| Keycloak 119:8080 | Buyer login/code exchange/refresh/reload/logout PASS; register form mở được | Nghiệm thu đăng ký đầy đủ và projection hồ sơ |
| Profile qua Gateway local | GET `/api/v1/me` có Bearer token nhưng trả503 | Chạy User Service và xác minh hồ sơ thật |
| Gateway119:8081 | Không có container/listener tương ứng; kết nối từ ngoài timeout | Chọn/chạy Gateway của môi trường kiểm thử |
| Gateway local trong Docker | Đang chạy8081; Windows8081 thuộc ứng dụng khác | Relay loopback18081 đã chạy, không gửi token tới ứng dụng chiếm port Windows |
| Public catalog Gateway local | GET `/api/v1/events` trả401; source thiếu route+permit cho list/categories/venues | Bổ sung route/public security theo API Event đã có |
| Eureka local | Chỉ thấy API-GATEWAY trạng thái UP trong lần đọc | Đăng ký Event/User/Booking/Payment/Ticket của cùng môi trường |
| Server119 | Có Booking/Ticket/Config/Keycloak/Postgres/Redis/Rabbit; không thấy Event/User/Gateway/Discovery chạy | Backend phục vụ catalog/profile và service discovery đúng cấu hình |

Không đổi Java/Gateway contract hoặc tự triển khai Event/User trong lượt phần1.1. Relay `deploy/docker-compose.frontend.local.yml` giữ port Gateway8081 trong Docker, publish riêng loopback18081. Endpoint/DTO Event đối chiếu `origin/dev/tuyen` commit `1bc263a`; Booking/Payment đối chiếu source `dev/son` hiện tại.

## Còn lại

1. Kết nối catalog/profile thật sau khi backend và Gateway sẵn sàng; GET `/api/v1/me` còn503, public catalog còn401. Auth buyer/callback/session/refresh/logout đã kiểm thật; còn đăng ký và projection hồ sơ.
2. Phần1.2 đã thay luồng chọn vé và checkout sang Booking v1 như bên dưới; còn nghiệm thu bằng buyer/backend thật và phụ thuộc API đọc lại đơn.
3. Phần1.3 đã nối Payment v1 và Ticket theo mã đơn; app-return trong root.env đã đổi sang FE local. Còn nghiệm thu Sandbox thật, runtime và API liệt kê toàn bộ lịch sử; xem task-1.3-progress.md.
4. Phần1.4 đã có runner và lượt kiểm tra thật trong `docs/task-1.4-results.md`: buildPASS,63 nhóm fixturePASS; live12PASS/4FAIL/4BLOCKED, chưa nghiệm thu đặt vé/thanh toán/phát vé thật. Chưa commit/push/deploy code FE.

## Phần 1.2 — Chọn vé, giữ đơn và voucher

[ClickUp 1.2](https://app.clickup.com/t/z8qgvcbr2v) giữ **In progress**: đã có code FE và kiểm thử fixture; chưa nghiệm thu API thật.

| Thao tác | Contract đang dùng |
|---|---|
| Tồn kho | GET `/api/v1/events/{eventId}/availability`; không dùng configured capacity làm tồn kho |
| Tạo đơn | POST `/api/v1/orders`, header UUID `Idempotency-Key`; body chỉ `eventId`, `customerEmail`, `selection` |
| QUANTITY | `selection: {mode:"QUANTITY",items:[{ticketTypeId,quantity}]}` |
| STANDING | `selection: {mode:"SEAT_MAP",kind:"STANDING",sectorId,items:[{ticketTypeId,quantity}]}`; tổng các loại vé không vượt pool của sector |
| SEATED | `selection: {mode:"SEAT_MAP",kind:"SEATED",sectorId,seatIds:[...]}`; chỉ ghế AVAILABLE, cùng sector |
| Áp mã | POST `/api/v1/orders/{orderId}/promotion`, body `{code}`; giá mới lấy nguyên `OrderView` trả về |
| Hủy giữ vé | POST `/api/v1/orders/{orderId}/cancel`, body `{}`; chỉ gửi khi người mua bấm hủy |

- `types/bookingContract.ts` đối chiếu DTO Java trên `dev/son`: ID UUID, số tiền/sourceConfigVersion là chuỗi, quantity là int. Không gửi họ tên/điện thoại/ghi chú/voucher trong create order.
- Request được canonical hóa và lưu pending key theo SHA-256 của body + gateway/issuer/buyer trong sessionStorage trước POST. Timeout/reload thử lại cùng body dùng lại key; đổi A → B → A vẫn giữ key của A chưa rõ kết quả. Sau thành công, lần đặt vé mới là yêu cầu mới. Draft sau login được kiểm lại theo tồn kho hiện tại.
- Sơ đồ giữ canvas size, sector geometry, seat coordinates và decorations của Event; bỏ sân khấu tự dựng. Ghế HELD/PURCHASED/UNAVAILABLE không chọn được, có danh sách tương đương dùng bàn phím. Native dialog nhận/trả focus và hỗ trợ Escape; VI/EN.
- Checkout dùng giá từng dòng, subtotal/discount/total và expiresAt từ `OrderView`, định dạng BigInt không mất chữ số. Countdown không gia hạn và không tự hủy đơn. Lỗi inventory yêu cầu chọn lại; lỗi freeze/expired chặn áp mã tiếp.
- Booking hiện **không có GET API đọc đơn** trong `OrderController`. Checkout chỉ đọc phản hồi create/promotion/cancel gần nhất trong **cùng tab/buyer/gateway/issuer**, có nhãn rõ đây là lần cập nhật gần nhất. Reload không tạo hold mới; không có receipt thì hiển thị trạng thái trống, không bịa GET endpoint. Cache không phải trạng thái hiện tại từ server; chưa dùng nó để xác nhận thanh toán/phát vé. API đọc order và lịch sử cần thống nhất ở bước tích hợp tiếp.
- Ở phần1.2, checkout đã ngừng gọi Payment legacy; phần1.3 bổ sung nút VNPay và thay trang đơn/vé bằng tra cứu v1 theo mã đơn. Ẩn chatbot ở checkout để không che hạn giữ vé. API toàn bộ lịch sử vẫn chưa có, chưa nghiệm thu backend thật.

### Kiểm chứng phần 1.2

- `tests/booking-flow.browser.js`: **15 nhóm PASS**, fixture chỉ trong tab test, không ghi dữ liệu119. Bao gồm ba kiểu chọn vé, exact request/key, pool chung, voucher lỗi/thành công/freeze, server prices, mất mạng/reload/A-B-A, inventory conflict/retry, draft login, deadline, cancel, focus/Escape, VI/EN, số tiền lớn, receipt theo buyer và receipt thiếu.
- Hồi quy `tests/public-api.browser.js`: **10 nhóm PASS**. Build `npm run build` PASS; còn cảnh báo bundle lớn và ảnh chatbot có sẵn.
- Đã xem ảnh desktop/mobile checkout, quantity, standing và seated; đo 320/390/1440px không tràn ngang. Ảnh nằm tại `output/playwright/fe-booking-*.png` từ root repo.
- Probe thật bổ sung: catalog Gateway local18081 vẫn trả HTTP401; GET availability Booking119:8082 timeout sau8s (HTTP000), không kết luận nguyên nhân phía server chỉ từ timeout. Chưa có bằng chứng mua vé/áp voucher thật qua FE; các phụ thuộc môi trường của phần1.1 vẫn cần xử lý.
- Code local `dev/son`, chưa commit/push/deploy, không sửa Java hoặc thay contract backend.

## Phần 1.4 — Kiểm thử tích hợp

- Runner: `tests/run-integration.cjs --fixtures` tái sử dụng7 file browser checks, context riêng; tổng63 nhómPASS. `--live` chạy `integration-live.browser.js` với server thật, không mock và không ghi credentials/JWT vào kết quả.
- User test thật: buyer login, reload, refresh, logout và layout320/390/1440pxPASS. Events/categories/venues401, profile503; detail/availability/seatmap, tạo đơn/voucher, payment/ticket và đăng ký/projection chưa nghiệm thu.
- Runtime local Booking/Payment tăng restart count, relay health đóng kết nối; Payment log ghi PostgreSQL127.0.0.1:5432 từ chối kết nối, Booking không lấy được JDBC metadata. Chưa đổi DB/config.
- JSON/ảnh: `output/playwright/task14-*` từ root repo. Live trả nonzero khi có FAIL/BLOCKED (runner đặt2, RTK trên máy chuẩn hóa thành1), không coi fixturePASS là nghiệm thu E2E. Ma trận, log tóm tắt và thứ tự gỡ vướng: `docs/task-1.4-results.md` cạnh tài liệu này.
