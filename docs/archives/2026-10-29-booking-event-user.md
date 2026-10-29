# Lưu trữ Booking, Event và User

- Nguồn: `D:/DATN/flash-ticket-platform`, branch `dev/son`.
- Commit nền: `b257e38c66a6829c31b64f0cd4a2857d61d9aac7`.
- Đã chụp cả source tracked và source chưa commit của ba service trước khi restore.
- File ignored/build output được chép sang worktree để bảo toàn, không đưa vào commit.
- Kèm cấu hình Config Server của ba service, DDL Booking và docs workflow hiện có.
- Checkout chính được yêu cầu restore riêng ba thư mục service về `1b7fd74ca5caa11492a753f3855cfa39f2382b97` của `levanminh04`.
- Không tác động branch của thành viên khác, database hoặc deployment server.
- Kiểm chứng bằng SHA-256 toàn bộ file trong ba service; không chạy build/test cho thao tác lưu trữ.
