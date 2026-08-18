# Sổ nguồn tài sản hiện thực — NỘI BỘ

> Tài liệu này phục vụ kiểm kê, an toàn bí mật và quản lý phần dùng lại. Không dùng làm bối cảnh, khoảng trống nghiên cứu, căn cứ chia service hoặc nguồn trình bày mặc định trong báo cáo.

- **Ngày kiểm kê:** 2026-08-09
- **Repository:** `https://github.com/levanminh04/flash-ticket-system.git`
- **Đường dẫn cục bộ:** `D:\Project\flash-ticket-system`
- **Commit baseline:** `609fa2d37cad69aafa593b7db5b6cedeaf803da5`
- **Thời điểm commit:** 2026-06-19T16:19:20+07:00
- **Trạng thái khi kiểm kê:** working tree sạch

## Dữ kiện kiểm kê nội bộ dùng được

- `core-service` là một ứng dụng Spring Boot/Maven, tổ chức chủ yếu theo package nghiệp vụ `event`, `booking`, `payment`, `notification`, `promotion`, cùng `common` và `shared`.
- Chưa có Maven module, Spring Modulith, ArchUnit hoặc cơ chế tương đương để cưỡng chế ranh giới giữa các package của `core-service`.
- `user-service` và `discovery-service` đã tồn tại như các ứng dụng riêng, nhưng ranh giới và dữ liệu vẫn phải được rà soát từ nghiệp vụ.
- Repository nguồn có các tài liệu audit/plan kỹ thuật hữu ích để phát hiện điểm ghép nối, nhưng chúng không phải nguồn quyết định vì một số giả định đã cũ hoặc mâu thuẫn với plan hiện hành.
- Hai tệp `.env.production` và `frontend/.env.production` đang được Git theo dõi trong repository nguồn. Không có giá trị nào từ các tệp này được sao chép sang repository mới.

## Cách sử dụng baseline

- Không dùng cấu trúc package, bảng, import hoặc tên service hiện tại làm căn cứ hình thành ranh giới hay phương án kiến trúc đích.
- Có thể kiểm kê tài sản sớm trong luồng `COMPARISON`, nhưng luồng `FORMATION` không đọc baseline/B5.5 ở B2–B10 hoặc B11-A. Chỉ sau khi tập phương án độc lập tại `docs/architecture/B11-A-independent-alternatives.md` được người thật duyệt `APPROVED` mới dùng B5.5 ở B11-B để kiểm tra tái sử dụng, di trú và tính khả thi; B11-B ghi đúng phiên bản B11-A và không sinh, bổ sung, xếp hạng hoặc sửa phương án. Bảng xử lý từng tài sản chỉ được chốt sau B11-C/B12.
- Khi dùng mã/migration, ghi nguồn theo commit và thay đổi trong sổ nội bộ để nhóm kiểm soát phạm vi và rủi ro.
- Nếu các tệp cấu hình nguồn chứa khóa thật, chủ sở hữu phải xoay khóa; việc xóa tệp ở repository mới không thu hồi được khóa đã lộ trong lịch sử nguồn.

Sổ này không phải bản đối chứng mặc định cho phép đo monolith–microservices và không phải nguồn viết phần Đặt vấn đề/Hiện thực của báo cáo.
