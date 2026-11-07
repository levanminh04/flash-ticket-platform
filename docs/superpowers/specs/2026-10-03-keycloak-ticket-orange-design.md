# FlashTicket — Login theme Ticket Orange

Ngày: 2026-10-03. Phân loại: thay đổi bounded trên login theme hiện có, FORMATION/EXECUTE.

## Xác nhận và phạm vi

- USER_CONFIRMED: người dùng chọn mẫu 3, Ticket Orange, từ ba mẫu minh họa.
- FACT: Keycloak 26.8.0, theme `flash-ticket` kế thừa `keycloak`; realm master local đã chọn theme này.
- Mục tiêu: trang đăng nhập mang nhận diện vé sự kiện, dễ đọc và dùng được trên điện thoại/laptop.
- Giữ template, label, autocomplete, password toggle, thông báo lỗi và luồng xác thực của Keycloak. Không bật đăng ký/reset password hoặc thay chính sách SSL.
- Nội dung form giữ theo ngôn ngữ Keycloak. Thay tên thương hiệu trong login header thành FlashTicket qua message bundle, không giấu text bằng CSS.
- Tác động: CSS là nguồn giao diện; message bundles en/vi sở hữu tên thương hiệu; spec và deploy/Note.md ghi lại lựa chọn/kiểm chứng. Compose, DB, authentication và frontend ứng dụng ngoài phạm vi.

## Direction

- Palette: nền `#191918`; giấy vé `#fffdf8`; chữ chính `#21180e`; chữ phụ `#6c6257`; cam CTA/brand `#ff8a43`; link/focus `#9d440b`; input border `#93887b`; lỗi `#b42318` trên `#fff1ee`.
- Typography: system-ui/Segoe UI, không tải font ngoài. Wordmark 30px/800, heading 28px/750, input/body 16px/1.5, label 14px/600, secondary 14px/1.5.
- Layout: một form ở giữa, max-width 440px. Desktop khoảng đệm ngoài 48px, card 36px; mobile <=480px khoảng đệm ngoài 16px, card 24px, flow dọc không chiều cao cố định. Màn hình thấp vẫn cuộn tới submit được.
- Spacing: 8/12/16/24/32/36/48px. Card sở hữu padding; form group sở hữu khoảng cách 24px; label 8px với input; header 32px với card.
- Signature: card như vé giấy, góc 4px, đường gạch xé dưới heading và hai khoét tròn 20px ở mép ngang vùng heading. Không dùng ảnh, animation hoặc chữ quảng cáo trang trí.
- Inventory: FlashTicket wordmark → heading Keycloak → lỗi global nếu có → form fields/error của Keycloak → options được realm cho phép → submit → registration/social/info của Keycloak nếu có.
- Desktop: wordmark ở trên card, card căn giữa ngang; mobile cùng thứ tự, toàn chiều rộng trong gutter 16px, title có thể xuống dòng.
- Interactions: input cao 48px, button tối thiểu 48px; focus outline 3px; lỗi vẫn có text và aria-invalid, không chỉ đổi màu. Password toggle giữ semantic/button gốc; hover/focus/disabled có style riêng.

## Self-review

- Mẫu đen–cam được người dùng chọn, không suy thành màu chung toàn frontend. Nền tối + card giấy + đường xé gắn với hình thức vé; không thêm gradient/các họa tiết cạnh tranh.
- Thay copy bằng tên thương hiệu qua bundle, không đưa thông tin hoặc tính năng giả vào UI. Có thể khác chữ trong mockup để giữ label/ngôn ngữ Keycloak.
- Kiểm tra contrast bằng tính toán trên token và giá trị thực tế. Cần kiểm tra render trên 375, 768 và 1280px; không kết luận chỉ từ CSS source.

## Checklist kiểm chứng đóng trước render

1. Brief fidelity: nền đen, card kem, CTA cam, nhận diện FlashTicket và đường xé hiện diện.
2. Hierarchy/consistency: title, label, field, submit đúng thứ tự; spacing/type nhất quán.
3. Responsive: không overflow ngang ở 375/768/1280px; input/button nằm trong card; mobile nhìn và cuộn được đầy đủ.
4. Content integrity: giữ label, input names/types, autocomplete và password toggle; không tạo registration/reset link khi realm chưa bật.
5. Contrast: chữ thường >=4.5:1, heading lớn >=3:1, border/focus >=3:1; error nhìn thấy.
6. Behavior: focus bằng keyboard, password visibility, invalid-login error hiển thị; không cần đăng nhập bằng secret thật để chứng minh CSS.
7. Delivery: theme CSS trả 200 sau recreate, screenshots desktop/mobile, diff check; chỉ local, không tuyên bố deploy AWS hoặc accessibility toàn diện.

## Kết quả triển khai

- FACT: đã thay CSS, thêm brand bundle en/vi và version query `?v=ticket-orange-1` vào styles để browser không dùng CSS cũ. Cache server/browser tiếp tục bật.
- RED → GREEN trên browser thật: nền/CTA cũ là rgb(243,245,250)/rgb(49,87,200); sau áp dụng là rgb(25,25,24)/rgb(255,138,67), card rgb(255,253,248), header FlashTicket.
- PASS: brief fidelity, hierarchy, consistency, signature vé giấy, label/copy integrity và responsive. Viewport CSS thực 1280×800, 768×800, 374×812; browser đang zoom 75%, đã bù kích thước capture theo `innerWidth/innerHeight`, không nhầm kích thước JPEG với viewport CSS.
- PASS: password toggle đổi type password → text → password; Tab tới submit có outline solid màu rgb(157,68,11); credential giả trả `Invalid username or password.`, field có aria-invalid=true, lỗi không bị khuất hoặc tràn ngang.
- Contrast đo từ computed styles: title/card 17.19:1; CTA 7.46:1; brand/nền 7.51:1; muted/card 5.86:1; focus/card 6.33:1; error/card 6.47:1; viền input thông thường rgb(147,136,123) trên nền trắng 3.47:1. Các vai trò đã đo đạt ngưỡng đã đặt.
- CSS endpoint HTTP 200; Keycloak HTTP 200 sau recreate. Không có thay đổi template/JavaScript/Compose hoặc DB trong lượt theme này.
- Ảnh kiểm chứng local tại `.superpowers/sdd/2026-10-03-keycloak-postgresql-http/`: `ticket-orange-desktop.jpg`, `ticket-orange-tablet.jpg`, `ticket-orange-mobile.jpg`, `ticket-orange-error-mobile.jpg`. Render supports bố cục; tương tác được kiểm riêng, không suy từ ảnh.
- Review touched files: CSS sở hữu token và spacing, theme.properties sở hữu thứ tự/version stylesheet, bundles chỉ sở hữu brand. Không có component hoặc prop flow mới để review.
- OPEN: browser khác, registration/reset/social flow chưa bật, kiểm chứng accessibility toàn diện và AWS deployment không được kiểm trong lượt CSS. Không suy ra tất cả flow đều đạt từ form login master.
- Báo cáo đồ án có thể dùng phần custom theme và kiểm chứng responsive; không dùng kết quả CSS này làm bằng chứng benchmark hệ thống.
- `git diff --check` PASS. Governance audit PASS với cảnh báo diff workspace 28 file (bao gồm thay đổi đã có từ trước); impact map của lượt theme đã ghi ở phạm vi trên. Quyết định chọn mẫu được ghi nguyên tử ở GOV-151; không commit hoặc stage thay đổi.
