# Chỉ mục tài liệu

Thư mục này là nguồn sự thật cho quá trình phân tích, thiết kế, hiện thực và đánh giá đồ án FlashTicket Platform.

> **Đề tài chính thức:** *"Chẩn đoán nguyên nhân gốc sự cố giao dịch trực tuyến bằng đồ thị phụ thuộc"*, do giảng viên hướng dẫn đặt ngày 2026-08-22. Nguyên văn thư tại [`evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`](evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md).
>
> **Hai bộ tài liệu, một đề tài.** Bộ hệ thống ở thư mục này; bộ nghiên cứu ở [`research-rca/`](research-rca/README.md). Chúng tách nhau để làm việc song song, **không phải để tách đề tài** — `DH-MT1` đặt hệ thống đặt vé vào mục tiêu đầu tiên. Nguồn sinh nội dung của mỗi bộ là đóng; hai bộ chỉ nối qua đúng hai cửa. Quy tắc đầy đủ ở mục **Đề tài và ranh giới hai bộ tài liệu** của `AGENTS.md`; phép thử trước khi đóng gate ở [`project/lien-ket-rca.md`](project/lien-ket-rca.md).

## Nguồn sự thật

| Nội dung | Tệp/thư mục sở hữu |
|---|---|
| Bối cảnh và mong muốn được tổng hợp từ chủ đồ án | `boi-canh-va-mong-muon.md` |
| Trạng thái và bằng chứng của các quyết định bền vững | `project/decision-register.md` |
| Trình tự công việc và cổng chuyển giai đoạn | `quy-trinh-lam-viec.md` |
| Phương pháp nghiên cứu, mục tiêu và bằng chứng | `tang-a-phuong-phap-nghien-cuu.md` |
| Quy trình phân tích, thiết kế, xây dựng và kiểm chứng | `tang-b-quy-trinh-ky-thuat.md` |
| Ký hiệu, đặt tên và mẫu biểu | `tang-c-quy-uoc-trinh-bay.md` |
| Sổ đối chiếu hiện thực nội bộ; chỉ mở cho thiết kế ở B11-B sau khi tập phương án độc lập B11-A tại đường dẫn canonical đã được người thật duyệt `APPROVED` | `b5.5-doi-chieu-ma-nguon-va-ba-tang.md` |
| Thuật ngữ miền | `glossary.md` |
| Quy trình nghiệp vụ B3 | `domain/B3-business-processes.md` |
| Bản đồ sự kiện miền B4 | `domain/B4-domain-event-map.md` |
| Bản đồ bounded context ứng viên B5 | `domain/B5-bounded-context-map.md` |
| Use case và đặc tả B6 | `domain/B6-use-cases.md` |
| Aggregate ứng viên và bất biến B7 | `domain/B7-aggregates-and-invariants.md` |
| Yêu cầu chức năng và phi chức năng B8 | `domain/B8-requirements.md` |
| **Tập phương án kiến trúc độc lập B11-A và đối chiếu khả thi B11-B** — đường dẫn canonical, **chưa tạo**; B11-A phải được người thật duyệt `APPROVED` trước khi mở B5.5 | `architecture/B11-A-independent-alternatives.md`, `architecture/B11-B-legacy-feasibility.md` |
| Quyết định kiến trúc | `adr/` |
| Sơ đồ và tệp nguồn | `diagrams/` |
| API và lược đồ sự kiện | `contracts/` |
| Kịch bản chất lượng | `quality-scenarios/` |
| Kịch bản đo và kết quả thô | `experiments/` |
| Bằng chứng khảo sát và quy trình dò lỗi | `evidence/` |
| Phiếu nghiên cứu Tầng A của bộ tài liệu hệ thống | `research/` |
| Bộ tài liệu nghiên cứu chẩn đoán nguyên nhân gốc — độc lập; **ràng buộc gửi tới B9–B16 nằm ở `research-rca/R0-boi-canh-va-rang-buoc.md` §3** | `research-rca/` |
| Sổ nguồn dùng chung cho cả hai bộ tài liệu | `research/source-register.md` |
| Khung báo cáo và bản nộp theo mốc | `report/README.md`, `report/report-outline.md`, `report/bao-cao-2-tuan-2026-09-05.md` |
| Baseline, phân vai và trạng thái thực hiện | `project/` |

## Quy tắc cập nhật

- Một thông tin chỉ có một nơi sở hữu; tài liệu khác liên kết tới nó thay vì sao chép.
- Báo cáo đi theo mạch vấn đề → yêu cầu → phân tích miền → thiết kế đích → hiện thực → đánh giá; không đi theo lịch sử file/package/commit.
- B5.5 và hồ sơ nguồn tài sản là tài liệu kỹ thuật nội bộ. Chúng phục vụ kiểm soát hiện thực, không được dùng làm bối cảnh, khoảng trống nghiên cứu hoặc nguồn lập luận ranh giới.
- Tạo tác `FORMATION` và `COMPARISON`, nguồn được phép cùng cổng duyệt được định nghĩa tại Tầng B mục 3.3; tài liệu dẫn xuất không tự đặt lại phase gate.
- Chỉ tạo ADR cho quyết định có phương án cạnh tranh hoặc hệ quả kiến trúc đáng kể.
- Hai bộ tài liệu chạy độc lập về **nhịp làm việc và cổng kiểm soát**, và **không chặn nhau**. Ranh giới viết theo **quyền quyết định, không phải quyền đọc** (`RES-028`, `RES-033`): bộ nghiên cứu **được nhận** `B4`, `B5` §5.1 và `B7` để dựng đồ thị cho `DH-MT1`, nhưng **không được** sửa một bất biến, ranh giới hay yêu cầu của bộ hệ thống. Hai bộ nối qua **đúng hai cửa**: `research-rca/R0-boi-canh-va-rang-buoc.md` §3 cho chiều nghiên cứu → hệ thống, và `project/lien-ket-rca.md` cho chiều ngược lại. Chi tiết trách nhiệm ở `AGENTS.md` mục 7.
- **Mỗi gate từ `B9` tới `B16` phải đối chiếu `R0` §3 trước khi chốt** và ghi kết quả đối chiếu vào phần tự kiểm của mình. Ràng buộc nào không giữ được thì ghi `OPEN` kèm hậu quả, không bỏ im lặng.
- **Bốn phiếu của bộ RCA trùng mã với Tầng A.** `A7`, `A8`, `A9` mang **hai nghĩa khác nhau** ở hai bộ — Tầng A: phương pháp nghiên cứu, ý nghĩa/đóng góp, bố cục/phân công; bộ RCA: khái niệm RCA, khảo sát bộ dữ liệu, độ đo thực nghiệm. **Trích từ ngoài `research-rca/` thì phải viết đường dẫn đầy đủ**, ví dụ `research-rca/A9-do-do-thuc-nghiem.md` §6 (`GOV-046`). Việc đổi mã sang `R1`–`R4` còn nợ tại `R0-OPEN-05`.
- Không đưa bí mật, dữ liệu cá nhân hoặc log chưa khử nhạy cảm vào Git.
