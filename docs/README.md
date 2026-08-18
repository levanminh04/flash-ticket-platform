# Chỉ mục tài liệu

Thư mục này là nguồn sự thật cho quá trình phân tích, thiết kế, hiện thực và đánh giá đồ án FlashTicket Platform.

## Nguồn sự thật

| Nội dung | Tệp/thư mục sở hữu |
|---|---|
| Bối cảnh, mong muốn và nội dung đã chốt | `boi-canh-va-mong-muon.md` |
| Trình tự công việc và cổng chuyển giai đoạn | `quy-trinh-lam-viec.md` |
| Phương pháp nghiên cứu, mục tiêu và bằng chứng | `tang-a-phuong-phap-nghien-cuu.md` |
| Quy trình phân tích, thiết kế, xây dựng và kiểm chứng | `tang-b-quy-trinh-ky-thuat.md` |
| Ký hiệu, đặt tên và mẫu biểu | `tang-c-quy-uoc-trinh-bay.md` |
| Sổ đối chiếu hiện thực nội bộ; chỉ mở cho thiết kế ở B11-B sau khi tập phương án độc lập B11-A tại đường dẫn canonical đã được người thật duyệt `APPROVED` | `b5.5-doi-chieu-ma-nguon-va-ba-tang.md` |
| Thuật ngữ miền | `glossary.md` |
| Quy trình nghiệp vụ B3 | `domain/B3-business-processes.md` |
| Quyết định kiến trúc | `adr/` |
| Sơ đồ và tệp nguồn | `diagrams/` |
| API và lược đồ sự kiện | `contracts/` |
| Kịch bản chất lượng | `quality-scenarios/` |
| Kịch bản đo và kết quả thô | `experiments/` |
| Bằng chứng khảo sát và quy trình dò lỗi | `evidence/` |
| Phiếu nghiên cứu đã điền/đang nháp | `research/` |
| Khung báo cáo và vòng đời các bản Word | `report/README.md`, `report/report-outline.md` |
| Baseline, phân vai và trạng thái thực hiện | `project/` |

## Quy tắc cập nhật

- Một thông tin chỉ có một nơi sở hữu; tài liệu khác liên kết tới nó thay vì sao chép.
- Báo cáo đi theo mạch vấn đề → yêu cầu → phân tích miền → thiết kế đích → hiện thực → đánh giá; không đi theo lịch sử file/package/commit.
- B5.5 và hồ sơ nguồn tài sản là tài liệu kỹ thuật nội bộ. Chúng phục vụ kiểm soát hiện thực, không được dùng làm bối cảnh, khoảng trống nghiên cứu hoặc nguồn lập luận ranh giới.
- Tạo tác `FORMATION` và `COMPARISON`, nguồn được phép cùng cổng duyệt được định nghĩa tại Tầng B mục 3.3; tài liệu dẫn xuất không tự đặt lại phase gate.
- Chỉ tạo ADR cho quyết định có phương án cạnh tranh hoặc hệ quả kiến trúc đáng kể.
- Không đưa bí mật, dữ liệu cá nhân hoặc log chưa khử nhạy cảm vào Git.
