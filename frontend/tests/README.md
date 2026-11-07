# Header responsive regression

## Task 1.4 — Chạy lại kiểm thử tích hợp

Vite phải chạy tại `http://127.0.0.1:5173` và nạp API/Keycloak từ root `.env` như ứng dụng hiện có. Runner tái sử dụng Playwright CLI, không thêm package vào frontend. Chạy từ thư mục `frontend`:

```powershell
rtk proxy npm run build
rtk proxy node tests/run-integration.cjs --fixtures
rtk proxy node tests/run-integration.cjs --live
```

- `--fixtures`: 7 file hiện có, tổng63 nhóm; mỗi nhóm độc lập dùng context mới, trừ layout/colors/polish cần chung fixture. Kiểm cả Booking quantity/standing/seated, idempotency/voucher, auth, cursor, UI và nonce. Exit0 khi tất cả pass; fixture không ghi backend.
- `--live`: context Chromium riêng, không route/mock. Đọc buyer test từ `deploy/keycloak/postman/SEED-README.md` ở root repo; credentials chỉ truyền trong bộ nhớ, không xuất ra báo cáo. Kiểm guest catalog, login BUYER, profile, detail/availability nếu catalog đọc được, reload/refresh/logout và layout320/390/1440px. Không tạo order hoặc giao dịch VNPay khi catalog/test event/payment flow chưa sẵn sàng.
- Kết quả tại `output/playwright/task14-fixtures.json`, `task14-live.json` cùng ảnh `task14-*.png` từ root repo. API lỗi là FAIL; bước không chạy được do phụ thuộc là BLOCKED. Live có FAIL/BLOCKED đặt exit2; RTK trên máy này chuẩn hóa mã khác0 thành1, nên đọc thêm JSON để phân biệt kết quả nghiệm thu và lỗi runner. Lỗi runner đặt exit1.

Runner kiểm tra và đóng session riêng `task14-fixtures` hoặc `task14-live`. Không dùng hai session này cho công việc khác trong khi runner đang chạy. Config buyer chỉ nằm trong bộ nhớ server Playwright và được xóa khỏi page object ngay khi bài live bắt đầu.

Ma trận nghiệm thu và những bước còn thiếu: `frontend/docs/task-1.4-results.md` từ root repo. Muốn nghiệm thu tạo đơn → VNPay → vé cần hoàn tất các phụ thuộc1.1/1.2/1.3; script live hiện kiểm tra điều kiện đầu vào và auth, không tự xác nhận giao dịch hay ký IPN giả.

## Hồi quy nonce và phiên Keycloak

`keycloak-nonce.browser.js` dùng adapter thật từ FE, chỉ giả lập HTTP token trong context riêng: nonce đúng ở ID token phải được chấp nhận khi access/refresh token không chứa nonce; nonce ID sai phải xóa token và từ chối đăng nhập. Test đồng thời kiểm request vẫn có nonce và PKCE S256. Không kiểm chữ ký JWT backend bằng fixture này.

`keycloak-session.browser.js` dùng Keycloak119 thật, chạy sau khi đã đăng nhập buyer trong tab kiểm thử để có SSO: xác nhận login, reload khôi phục phiên và ép refresh thành công. File không chứa mật khẩu/JWT và không mock backend. Logout qua menu tài khoản và hộp xác nhận đã kiểm bằng Playwright CLI; báo cáo tại `output/playwright/keycloak-login-uat-2026-11-04.md` từ root repo.

Ví dụ CLI tại root repo, khi Vite chạy ở127.0.0.1:5173:

```powershell
rtk proxy npx --yes --package @playwright/cli playwright-cli -s=keycloak-uat run-code --filename frontend/tests/keycloak-nonce.browser.js
rtk proxy npx --yes --package @playwright/cli playwright-cli -s=keycloak-uat run-code --filename frontend/tests/keycloak-session.browser.js
```

`header-responsive.browser.js` là hàm async nhận một Playwright `page`. Dùng công cụ browser hiện có để chạy file, không cần thêm dependency vào frontend.

Ví dụ với Playwright MCP:

```json
{
  "filename": "D:/DATN/flash-ticket-platform/frontend/tests/header-responsive.browser.js"
}
```

Chạy qua `browser_run_code_unsafe` trong một tab mới, khi Vite đang phục vụ frontend tại `http://127.0.0.1:5173`.

- Fixture API và Keycloak được gắn **chỉ vào tab test**, không thay cấu hình hay thêm mock vào source ứng dụng.
- 6 kích thước: 320, 390, 768, 900, 1024, 1440px.
- Kiểm tra search luôn hiện, header/control không bị cắt, vùng chạm mobile tối thiểu 44px.
- Kiểm tra submit từ khóa tiếng Việt, guest phải đăng nhập khi mở vé, đổi VI/EN, Escape, tài khoản tên dài và click ngoài menu.
- Lỗi trước bản sửa: `Search must remain visible at 320px`.

Đây là kiểm tra trên trình duyệt với fixture, không xác nhận API thật, Keycloak thật hoặc trải nghiệm Safari/iPhone/Android vật lý. Build frontend riêng bằng `npm run build`.

## Homepage layout

Chạy `home-layout.browser.js` bằng cùng công cụ, trong tab mới. Có 11 nhóm kiểm tra: chuyển bố cục mặc định cũ, tương phản tiêu đề mặc định, thứ tự DOM/hiển thị, 320–1440px, không bịa sự kiện khi trống, lỗi API + thử lại, loading, và giữ cấu hình/editor đã tùy chỉnh.

Sự kiện trong file này là **fixture minh họa**, không ghi lên backend. Test cố ý trả HTTP 503 cho tình huống lỗi; các log 503 tương ứng là dữ liệu kiểm tra. Screenshot nằm ở `output/playwright/fe2-home-desktop.png` và `output/playwright/fe2-home-mobile.png` tính từ root repo.

## Palette và component

Sau `home-layout.browser.js`, chạy tiếp `home-colors.browser.js` **trong cùng tab** để dùng lại fixture. Có 7 nhóm: vàng thương hiệu/tách màu trạng thái; tương phản chữ; card dùng chung; hover/focus; responsive; hộp đăng nhập; native disabled. Mọi fixture vẫn chỉ nằm trong browser test.

## Polish trang chủ/header

Chạy `home-polish.browser.js` sau `home-layout.browser.js` trong cùng tab. Có 9 nhóm: native dialog nhận/trả focus, chặn background và Escape; FAQ bàn phím/ARIA/ẩn câu trả lời; gợi ý search không mất khi Tab; menu prompt; focus rõ; VI/EN và 6 kích thước 320–1440px, pricing xuống dòng và vùng chạm; bàn phím menu ngôn ngữ; nội dung dài và dialog 320×568; reduced motion. Test nội dung dài chỉ sửa DOM trong tab test rồi được reset khi tải lại.

Lỗi trước sửa: `Sign-in dialog must receive focus`. Native dialog có thể đưa Tab qua browser chrome; test kiểm việc quay lại dialog và không cho focus vào nội dung background. Đây là kiểm tra Chromium, không phải xác nhận trải nghiệm screen reader hay thiết bị thật.

## Contract Event/Auth

`public-api.browser.js` chạy riêng trong tab mới. Có 10 nhóm: route v1; mapping Event/ticket/time/price; cursor; query q/categoryId; seatmap lồng trong detail; lookup ID/address; refresh lỗi không gửi request/token cũ; role UI chỉ dùng realm roles như backend; chuyển trang search bằng cursor; description hiển thị như text để không thực thi HTML của organizer. Fixture theo Event `origin/dev/tuyen` đã fetch ở commit `1bc263a`.

Các fixture homepage/header cũng đã đổi sang wire DTO `{items,nextCursor}`, `eventId`, `startsAt`, `images.objectReference`, `ticketTypes.unitPrice` và `/api/v1/me`. Chúng không còn dùng response legacy để che sai lệch contract.

## Payment/Ticket v1

Chạy `payment-ticket.browser.js` bằng Playwright MCP với filename tuyệt đối trong context mới, Vite local5173. Có16 nhóm kiểm tra fixture: Payment create/envelope/idempotency/retry, callback giả, pending/unknown/accepted, giá lớn, quyền/wrapped errors, URL điều hướng an toàn, bấm checkout tới VNPay fixture, Ticket/QR PNG xác thực/revoke/VOID, lookup, VI/EN/mobile, polling có giới hạn và guest cleanup. Test dùng browser clock để chạy giới hạn polling nhanh; không tái sử dụng tab/context này cho test khác.

Ảnh `output/playwright/fe-payment-*.png`. API mock và trang VNPay trong test không tạo giao dịch Sandbox, không gửi IPN và không ghi119. PNG1pixel fixture kiểm transport/objectURL/focus, không chứng minh mã QR thật quét được. Chi tiết giới hạn môi trường trong `frontend/docs/task-1.3-progress.md`.

## Booking v1: chọn vé, order và voucher

Chạy Vite từ `frontend` bằng `rtk proxy npm run dev -- --host 127.0.0.1`. Mở tab context mới, chạy `booking-flow.browser.js` bằng `mcp__playwright__browser_run_code_unsafe` với `filename` tuyệt đối. Không tái sử dụng tab có route hoặc Keycloak fixture của test khác.

15 nhóm kiểm tra: QUANTITY/STANDING/SEATED, exact create body/Idempotency-Key, giá server, voucher request riêng/lỗi/freeze, pool chung, retry mạng/reload/A-B-A, tồn kho lỗi/thử lại/conflict, draft login được kiểm lại, deadline/hủy chủ động, native dialog/focus/Escape, VI/EN và tiền vượt Number.MAX_SAFE_INTEGER, receipt tách buyer và receipt thiếu. Fixture không ghi backend và không thay được user test thật. Lỗi503/409/422 và network abort trong log là tình huống chủ động của test.

Test đo tràn ngang và chụp các trang quantity/seated/checkout ở320/390/1440px; đứng ở390px. Kết quả `output/playwright/fe-booking-*.png` từ root repo. Checkout chỉ có receipt trong cùng phiên do Booking chưa có GET API đọc đơn; test bảo đảm không tự gọi endpoint chưa tồn tại, không tạo lại/hủy hold khi reload hoặc hết countdown.
