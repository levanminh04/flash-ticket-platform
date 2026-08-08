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
| Đối chiếu miền đích với mã nguồn tiền nhiệm | `b5.5-doi-chieu-ma-nguon-va-ba-tang.md` |
| Thuật ngữ miền | `glossary.md` |
| Quyết định kiến trúc | `adr/` |
| Sơ đồ và tệp nguồn | `diagrams/` |
| API và lược đồ sự kiện | `contracts/` |
| Kịch bản chất lượng | `quality-scenarios/` |
| Kịch bản đo và kết quả thô | `experiments/` |
| Bằng chứng khảo sát và quy trình dò lỗi | `evidence/` |
| Khung báo cáo | `report/` |
| Baseline, phân vai và trạng thái thực hiện | `project/` |

## Quy tắc cập nhật

- Một thông tin chỉ có một nơi sở hữu; tài liệu khác liên kết tới nó thay vì sao chép.
- Báo cáo đi theo mạch vấn đề → yêu cầu → thiết kế → hiện thực → đánh giá, không đi theo lịch sử refactor.
- Mã nguồn tiền nhiệm chỉ được dùng làm bằng chứng hiện trạng và nguồn tái sử dụng sau khi đã có phân tích miền sơ bộ.
- Chỉ tạo ADR cho quyết định có phương án cạnh tranh hoặc hệ quả kiến trúc đáng kể.
- Không đưa bí mật, dữ liệu cá nhân hoặc log chưa khử nhạy cảm vào Git.
