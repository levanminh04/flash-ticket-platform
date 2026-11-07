# Task 1.4 — Kiểm thử frontend với backend

Mục tiêu: kiểm tra build, hồi quy UI/contract và đường đi thật từ đăng nhập đến nhận vé; lưu kết quả thực tế và những bước chưa thể nghiệm thu.

Thực hiện ngay trong checkout đang chứa các thay đổi frontend. Tái sử dụng Playwright CLI, các file `tests/*.browser.js` và cấu hình root `.env`; không thêm dependency, không sửa Java hoặc hợp đồng backend.

- [x] Chạy build và toàn bộ test browser fixture đang có, mỗi nhóm dùng context riêng. BuildPASS;63 nhómPASS.
- [x] Thêm bài kiểm tra môi trường thật: guest catalog, buyer login, `/me`, event/detail/availability khi có dữ liệu, reload, refresh, logout và layout desktop/mobile. Không ghi token/mật khẩu vào báo cáo.
- [x] Chạy bài kiểm tra thật; status HTTP lỗi là FAIL, các bước phụ thuộc không chạy được là BLOCKED. Fixture PASS không làm đổi kết quả nghiệm thu thật. Kết quả12PASS/4FAIL/4BLOCKED.
- [x] Rà điều kiện chạy checkout, VNPay và nhận vé theo implementation hiện có. Chỉ gọi giao dịch ghi trên test event/merchant sandbox đã xác định; không giả IPN hay tự kết luận thành công từ return URL. Đã xác định phụ thuộc1.3 và runtime; chưa có giao dịch ghi thật.
- [x] Ghi ma trận kết quả, nguyên nhân chặn và lệnh chạy lại vào `frontend/docs/task-1.4-results.md`; bằng chứng trong `output/playwright/task14-*`.
- [ ] Nghiệm thu cuối bằng order/thanh toán/vé thật sau khi các phụ thuộc sẵn sàng.

Điều kiện hoàn tất nghiệp vụ: catalog/profile thật hoạt động, tạo đơn/giá/hạn giữ/voucher đúng, thanh toán do backend xác nhận, đủ vé hợp lệ và Booking COMPLETED. Task1.1/1.2/1.3 chưa sẵn sàng thì giữ task1.4 chưa nghiệm thu.

Phần thêm mới dự kiến: một hàm browser kiểm tra thật và một runner Node gọi Playwright CLI hiện có, thu kết quả từng nhóm mà không ghi dữ liệu nhạy cảm. Không thêm class/method Java.
