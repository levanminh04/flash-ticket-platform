# Màu và component FlashTicket

Phạm vi chuẩn hóa: trang chủ, header và các primitive dùng chung. Bố cục task 1–2 giữ nguyên. Các trang nghiệp vụ khác còn có CSS riêng; đây không phải tuyên bố đã redesign toàn ứng dụng.

Nguồn: `src/assets/css/global.css`, `src/assets/css/home.css`, `tailwind.config.js`.

| Vai trò | Token | Giá trị hiện tại |
|---|---|---|
| Hành động chính | `--brand` / `--primary` | `#cc9900` |
| Hover / nhấn | `--brand-hover` / `--brand-active` | `#dfb329` / `#b88b00` |
| Chữ trên vàng | `--on-brand` | `#1b1d21` |
| Chữ vàng trên nền trắng | `--brand-ink` | `#805c00` |
| Nhấn vàng trên nền tối | `--brand-on-dark` | `#f4d773` |
| Nền hành động phụ | `--brand-soft` | `#fff4cc` |
| Canvas / bề mặt tối | `--canvas-dark` / `--surface-dark` | `#1d1e22` / `#282a30` |
| Chữ / chữ phụ trên tối | `--text-on-dark` / `--text-secondary-on-dark` | `#f8fafc` / `#d1d3d8` |
| Disabled | `--disabled-surface` / `--disabled-ink` | `#e9e9e5` / `#60646d` |

- Vàng nền nút không dùng làm chữ nhỏ trên trắng; dùng `--brand-ink`. Tailwind `primary-500` khớp brand, `primary-700` khớp ink; khi đổi bảng màu cần đồng bộ CSS và Tailwind.
- Success/error vẫn là màu ngữ nghĩa riêng; không đổi mọi màu xanh/đỏ thành vàng.
- Focus dùng vàng đậm trên nền sáng, vàng sáng trên nền tối. Không bỏ outline để làm đẹp.
- Hai danh sách sự kiện dùng card trắng, ảnh 16:9, góc bo 12px, chữ tên 16px/1.45 tối đa hai dòng; lịch/địa điểm 13px; giá ở hàng cuối cùng với link xem sự kiện. Full title vẫn nằm trong nội dung link và thuộc tính `title`.
- Nút chính vàng, nút phụ vàng nhạt, control bo 8px; disabled là nền trung tính với native `disabled`.
- Giữ font Inter/system fallback đang có; không thêm font hoặc dependency.

Kiểm chứng trên browser với fixture: `tests/home-colors.browser.js`, chạy sau `tests/home-layout.browser.js` trong cùng tab. Kiểm tra contrast, hover/focus/disabled, card và 320–1440px. Kết quả này không xác nhận API/Keycloak thật hoặc thiết bị vật lý.
