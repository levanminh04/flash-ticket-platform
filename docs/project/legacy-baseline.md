# Baseline repository tiền nhiệm

- **Ngày kiểm kê:** 2026-08-09
- **Repository:** `https://github.com/levanminh04/flash-ticket-system.git`
- **Đường dẫn cục bộ:** `D:\Project\flash-ticket-system`
- **Commit baseline:** `609fa2d37cad69aafa593b7db5b6cedeaf803da5`
- **Thời điểm commit:** 2026-06-19T16:19:20+07:00
- **Trạng thái khi kiểm kê:** working tree sạch

## Dữ kiện hiện trạng dùng được

- `core-service` là một ứng dụng Spring Boot/Maven, tổ chức chủ yếu theo package nghiệp vụ `event`, `booking`, `payment`, `notification`, `promotion`, cùng `common` và `shared`.
- Chưa có Maven module, Spring Modulith, ArchUnit hoặc cơ chế tương đương để cưỡng chế ranh giới giữa các package của `core-service`.
- `user-service` và `discovery-service` đã tồn tại như các ứng dụng riêng, nhưng ranh giới và dữ liệu vẫn phải được rà soát từ nghiệp vụ.
- Repository tiền nhiệm có các tài liệu audit/plan kỹ thuật hữu ích làm bằng chứng, nhưng chúng không phải nguồn quyết định của đồ án vì một số giả định đã cũ hoặc mâu thuẫn với plan hiện hành.
- Hai tệp `.env.production` và `frontend/.env.production` đang được Git theo dõi trong repository tiền nhiệm. Không có giá trị nào từ các tệp này được sao chép sang repository mới.

## Cách sử dụng baseline

- Không dùng cấu trúc package hoặc bảng hiện tại làm căn cứ duy nhất để chia service.
- Sau khi hoàn thành B5, dùng B5.5 để phân loại từng phần thành: giữ nguyên, sửa, tách, viết mới hoặc bỏ.
- Khi tái sử dụng mã/migration, ghi nguồn theo commit và ghi rõ thay đổi trong bảng chuyển đổi.
- Nếu các tệp cấu hình tiền nhiệm chứa khóa thật, chủ sở hữu phải xoay khóa; việc xóa tệp ở repository mới không thu hồi được khóa đã lộ trong lịch sử cũ.

Baseline này không phải bản đối chứng mặc định cho phép đo monolith–microservices.
