# FlashTicket Platform

**Đề tài chính thức:** Xây dựng hệ thống bán vé theo kiến trúc phân tán có ứng dụng đồ thị phụ thuộc để giám sát và chẩn đoán sự cố.

[Tên và nhiệm vụ nguyên văn, hiệu lực 18/09/2026](docs/evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md). Đồ án xây dựng hệ thống bán vé phân tán, đánh giá các giao dịch dưới tải/đồng thời và ứng dụng đồ thị phụ thuộc cùng log, trace, metrics để giám sát và hỗ trợ chẩn đoán. Phương pháp được thực nghiệm trên dữ liệu công khai trước rồi thử trên FlashTicket; kết quả có giao diện minh họa.

## Nhóm và ưu tiên

Nhóm gồm **Minh, Sơn, Tuấn, Tuyến**; Nhật đã rời nhóm. Minh lead giai đoạn đầu và phụ trách chính RCA. Phân công chi tiết chỉ lấy tại [phạm vi phụ trách](docs/project/roles.md).

**Luồng chính chạy đúng → dữ liệu quan sát dùng được → thử tải/mô phỏng lỗi và RCA. Mobile là phần phụ, chỉ làm khi thực sự thừa thời gian.**

## Trạng thái ngày 18/09/2026

Đầu **Giai đoạn 5 — hiện thực**. Kiến trúc PA-6 và bộ thiết kế B12–B16 đã được duyệt; có năm project nghiệp vụ và ba project Spring hỗ trợ trong commit `091fca1`. Đây mới là bộ khung, chưa có bằng chứng nghiệp vụ/build/E2E đã đạt. Chi tiết tại [tiến độ](docs/project/implementation-status.md) và [bắt đầu hiện thực](docs/architecture/implementation-readiness.md).

## Ràng buộc còn hiệu lực

- Năm service nghiệp vụ: event, booking, payment, ticket, user; một Saga do Payment điều phối. Trần vẫn là tám service nghiệp vụ và ba Saga.
- Hai EC2 theo B11-C, không HA; cấu hình mục tiêu không phải bằng chứng triển khai thực.
- Dữ liệu sở hữu độc lập; không truy cập chéo datastore của service khác.
- RCA chỉ đọc, không tự kết luận cuối cùng hoặc tự sửa nghiệp vụ; lớp giải thích nhận kết quả xếp hạng.
- Mobile tùy thời gian; check-in offline ngoài phạm vi. Bất biến check-in backend vẫn giữ.
- CI baseline là phần việc hỗ trợ của Minh; không phải mục tiêu nghiên cứu.

## Bắt đầu từ đâu

1. [Đề tài/nhiệm vụ hiện hành](docs/evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md) và [phân công](docs/project/roles.md).
2. [Bối cảnh và mong muốn](docs/boi-canh-va-mong-muon.md), phân biệt rõ khối hiện hành với lịch sử.
3. [Quy trình làm việc](docs/quy-trinh-lam-viec.md) và [chỉ mục tài liệu](docs/README.md).
4. [Trạng thái triển khai](docs/project/implementation-status.md) và [sổ quyết định](docs/project/decision-register.md).

Mọi bí mật nằm ngoài Git. Các nguồn lịch sử giữ ngày và hiệu lực; không dùng tên/nhóm/phân công cũ thay nguồn 18/09.

## Build và CI

Mỗi pull request vào `main` chạy Maven verification cho cả tám ứng dụng, Dependency Review và CodeQL. Chỉ check tổng hợp **`CI / Required`** báo thành công khi các gate bắt buộc của sự kiện đó đều đạt.

Để chạy cùng gate Maven ở máy cá nhân, vào thư mục ứng dụng và chạy `./mvnw -B -ntp verify` (Windows: `.\mvnw.cmd -B -ntp verify`). Lệnh này kiểm Java 21, chạy test, Spotless formatting check, tạo báo cáo JaCoCo tại `target/site/jacoco/` và chạy SpotBugs. `./mvnw spotless:apply` sửa định dạng Java theo Google Java Format; CI chỉ kiểm tra, không tự sửa mã. SpotBugs phân tích bytecode để tìm mẫu lỗi.

Dependency Review chặn dependency mới hoặc thay đổi có lỗ hổng mức HIGH/CRITICAL. CodeQL phân tích mã Java/Kotlin để tìm lỗ hổng bảo mật và lỗi code. Cả hai chạy trong GitHub Actions; Maven-side checks có thể tái hiện bằng Maven Wrapper như trên.
