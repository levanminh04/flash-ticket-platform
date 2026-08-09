# FlashTicket Platform

Đồ án tốt nghiệp về hệ thống đặt vé sự kiện trực tuyến, tập trung vào tính nhất quán và độ tin cậy trong vòng đời vé: giữ chỗ, thanh toán, phát hành vé và check-in trực tuyến.

Repository này là không gian làm việc chính thức của đồ án. Mã nguồn tiền nhiệm tại `levanminh04/flash-ticket-system` được giữ nguyên làm bằng chứng hiện trạng và nguồn tái sử dụng có chọn lọc; cấu trúc của repository đó không mặc nhiên quyết định kiến trúc đích.

## Trạng thái

Đang ở **Giai đoạn 1 — Vấn đề và bối cảnh**. Giai đoạn 0 đã hoàn thành phần kỹ thuật; câu hỏi cho giảng viên và phân vai chi tiết đang chờ nhóm thực hiện. Chưa chốt danh sách service, ranh giới dữ liệu, Saga hoặc cách bố trí hai EC2; vì vậy chưa chuyển mã nguồn nghiệp vụ vào repository này.

## Ràng buộc đã chốt

- Không quá 8 service nghiệp vụ.
- Không quá 3 luồng Saga.
- Sử dụng 2 EC2; cách bố trí thành phần chưa chốt.
- Mobile là client dùng chung backend; không làm check-in offline.
- CI/CD là phần hỗ trợ, không phải trục nghiên cứu.
- Trợ lý chẩn đoán chỉ đọc: log có cấu trúc → Drain → chọn ngữ cảnh → LLM API → tư vấn.

## Bắt đầu từ đâu

1. Đọc [bối cảnh và mong muốn](docs/boi-canh-va-mong-muon.md).
2. Theo [quy trình làm việc](docs/quy-trinh-lam-viec.md).
3. Tra cứu [Tầng A](docs/tang-a-phuong-phap-nghien-cuu.md), [Tầng B](docs/tang-b-quy-trinh-ky-thuat.md) hoặc [Tầng C](docs/tang-c-quy-uoc-trinh-bay.md) khi thực hiện phiếu tương ứng.
4. Xem [trạng thái triển khai](docs/project/implementation-status.md) trước khi bắt đầu một đầu ra mới.

Mọi quyết định kiến trúc quan trọng được lưu trong `docs/adr/`. Mọi bí mật phải nằm ngoài Git; repository chỉ chứa tệp `.env.example` không có giá trị thật.
