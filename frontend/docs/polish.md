# Polish trang chủ/header — 04/11/2026

Phạm vi: FE 4 trên `dev/son`, theo Impeccable polish. Giữ nhận diện vàng và bố cục task 1–3; không thêm dependency hay đổi contract API.

## Sửa lỗi

- `Navbar.tsx`: hộp đăng nhập dùng native `dialog.showModal()` để nhận focus và chặn background; Escape/click ngoài/nút đóng trả focus về nút mở, khóa cuộn nền trong lúc mở. Menu prompt dùng `details/summary`, đóng bằng Escape/click ngoài/chọn prompt. Gợi ý search giữ nguyên khi Tab vào link, Escape trở về ô tìm kiếm; bỏ timer blur 150ms vốn làm mất gợi ý giữa thao tác bàn phím.
- `LanguageSwitcher.tsx`: bổ sung Arrow Up/Down, Home/End cho menu lựa chọn ngôn ngữ; giữ chọn bằng Enter và đóng bằng Escape.
- `HomePage.tsx`: FAQ có trạng thái `aria-expanded`, liên kết câu hỏi/câu trả lời và `hidden` cho phần đóng. Bỏ giới hạn chiều cao 260px để câu trả lời dài không bị cắt. Cuộn danh sách sự kiện tuân theo reduced motion. Bổ sung bản dịch VI/EN cho nhãn phí đang hardcode; giữ số liệu hiện có.
- `home.css`: focus nhìn thấy ở header/FAQ/footer; vùng chạm search/đóng popup 44px; tiêu đề dễ đọc, bỏ tracking quá sít; pricing một cột khi <=900px; logo đối tác ba cột trên mobile; ẩn minh họa FAQ trên mobile để bỏ khoảng trống; chỉnh tương phản blog/footer, khoảng cuộn dưới sticky header và reduced motion. Nút chatbot trên homepage mobile thu nhỏ còn 52px, vẫn dùng cơ chế mở cũ.

## Kiểm chứng

| Kiểm tra bằng Playwright MCP | Kết quả |
|---|---|
| `tests/home-polish.browser.js` | 9 nhóm pass |
| `tests/home-layout.browser.js` | 11 nhóm pass |
| `tests/home-colors.browser.js` | 7 nhóm pass |
| `tests/header-responsive.browser.js` | 9 nhóm pass |

Lỗi được tái hiện trước sửa: `Sign-in dialog must receive focus`; Escape không đóng popup, FAQ chưa có trạng thái mở/đóng, pricing mobile ép hai cột. Kiểm tra sau sửa gồm tên/địa điểm/câu trả lời dài, dialog 320×568, VI/EN và sáu chiều rộng 320/390/768/900/1024/1440. Native dialog cho phép Tab qua browser chrome; test xác nhận quay về dialog và background không nhận focus.

Ảnh được xem lại ở `output/playwright/fe4-home-desktop.png`, `fe4-home-tablet.png`, `fe4-home-mobile.png`, `fe4-pricing-mobile.png`, `fe4-faq-mobile.png`, `fe4-signin-small.png` (tính từ root repo). Phiên render cuối không có pageerror, ảnh hoàn tất nhưng hỏng hoặc event URL chứa `undefined`.

Các test dùng API/Keycloak fixture riêng trong browser; không ghi backend. Chưa xác nhận API, đăng nhập, checkout thật, Safari, screen reader, zoom trình duyệt hoặc thiết bị vật lý. Số liệu phí, claim và nội dung bài viết có sẵn được giữ nguyên; polish không xác thực chúng. Tài khoản/editor được giữ bằng test hồi quy, chưa làm lại toàn bộ UI quản trị. Các trang nghiệp vụ khác nằm ngoài phạm vi.

Build dùng `npm run build`; cảnh báo chunk >500kB và ảnh chatbot khoảng 10MB có sẵn vẫn còn. Chưa commit/push/deploy.
