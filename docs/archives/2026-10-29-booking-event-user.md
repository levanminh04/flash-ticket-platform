# Lưu trữ Payment, Event, User và bản Booking tham khảo

- Nguồn: `D:/DATN/flash-ticket-platform`, branch `dev/son`.
- Commit nền: `b257e38c66a6829c31b64f0cd4a2857d61d9aac7`.
- Bản đầu lưu Booking/Event/User tại cb5bed4, gồm cả thay đổi chưa commit.
- User đính chính: giữ nguyên Booking tại checkout chính; chỉ restore Payment/Event/User.
- Đã bổ sung toàn bộ Payment hiện tại, gồm source chưa commit, vào worktree này trước khi restore.
- Booking trên dev/son được phục hồi nguyên trạng từ bản lưu trước thao tác ban đầu.
- Payment/Event/User trên dev/son trở về `1b7fd74ca5caa11492a753f3855cfa39f2382b97` của `levanminh04`.
- File ignored/build output được giữ trong worktree nhưng không đưa vào commit.
- Kèm Config Server của các service, DDL Booking và docs workflow để đối chiếu.
- Kiểm chứng bằng manifest SHA-256 và Git blob; không chạy build/test cho thao tác Git này.
- Không tác động branch của thành viên khác hoặc runtime/server/database.
