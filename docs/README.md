# Chỉ mục tài liệu

Thư mục này là nguồn sự thật cho quá trình phân tích, thiết kế, hiện thực và đánh giá đồ án FlashTicket Platform.

> **Đề tài chính thức:** *"Xây dựng hệ thống bán vé theo kiến trúc phân tán có ứng dụng đồ thị phụ thuộc để giám sát và chẩn đoán sự cố"*. Nguồn hiện hành do Minh xác nhận ngày 18/09/2026: [DT18](evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md).
>
> **Nhóm bốn người:** Minh, Sơn, Tuấn, Tuyến; [phạm vi phụ trách](project/roles.md). Minh lead giai đoạn đầu, phụ trách chính RCA. Mobile là phần phụ, chỉ làm khi thực sự thừa thời gian.
>
> **Hai bộ tài liệu, một đề tài:** bộ hệ thống xây dựng/đánh giá hệ bán vé phân tán; bộ RCA nghiên cứu giám sát/chẩn đoán bằng đồ thị. Hai bộ giữ nhịp/gate và hai cửa nối theo AGENTS.md. Thư 22/08 và các phê duyệt phiên bản cũ là bằng chứng lịch sử, không thay nhiệm vụ DT18.

## Nguồn sự thật

| Nội dung | Tệp/thư mục sở hữu |
|---|---|
| Tên và nhiệm vụ hiện hành từ 18/09 | `evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md` |
| Nhóm và phạm vi phụ trách lần đầu | `project/roles.md` |
| Bối cảnh và mong muốn được tổng hợp từ chủ đồ án | `boi-canh-va-mong-muon.md` |
| Trạng thái và bằng chứng của các quyết định bền vững | `project/decision-register.md` |
| Trình tự công việc và cổng chuyển giai đoạn | `quy-trinh-lam-viec.md` |
| Phương pháp nghiên cứu, mục tiêu và bằng chứng | `tang-a-phuong-phap-nghien-cuu.md` |
| Quy trình phân tích, thiết kế, xây dựng và kiểm chứng | `tang-b-quy-trinh-ky-thuat.md` |
| Ký hiệu, đặt tên và mẫu biểu | `tang-c-quy-uoc-trinh-bay.md` |
| Sổ đối chiếu hiện thực nội bộ; chỉ mở cho thiết kế ở B11-B sau khi tập phương án độc lập B11-A tại đường dẫn canonical đã được người thật duyệt `APPROVED` — **đã mở ngày 2026-08-31** (`GOV-073`). Theo `GOV-074`, **nguyên văn khảo sát cũ được giữ nguyên**; các chú thích đính chính đã được chèn tại gạch đầu dòng cuối §3.5, đầu PHẦN 4 và ô kiểm cuối PHẦN 5 để gỡ hiệu lực của thiết kế trợ lý cũ. Nội dung kiểm kê dùng cho B11-B không đổi | `b5.5-doi-chieu-ma-nguon-va-ba-tang.md` |
| Thuật ngữ miền | `glossary.md` |
| Quy trình nghiệp vụ B3 | `domain/B3-business-processes.md` |
| Bản đồ sự kiện miền B4 | `domain/B4-domain-event-map.md` |
| Bản đồ bounded context ứng viên B5 | `domain/B5-bounded-context-map.md` |
| Use case và đặc tả B6 | `domain/B6-use-cases.md` |
| Aggregate ứng viên và bất biến B7 | `domain/B7-aggregates-and-invariants.md` |
| Yêu cầu chức năng và phi chức năng B8 | `domain/B8-requirements.md` |
| **Chuỗi kiến trúc B11-A → B11-B → B11-C** — đường dẫn canonical. Cả ba cổng đều `APPROVED`. **`B11-C-v0.3` đã chốt `PA-6`**, năm service `event`/`booking`/`payment`/`ticket`/`user`, một Saga do `payment-service` điều phối, RCA riêng chỉ đọc với lớp giải thích dùng Gemini API, và bố trí hai máy không HA (`GOV-086`–`GOV-099`). `ADR-001`–`ADR-004` đều `Chấp nhận`; thử tải thật thuộc Giai đoạn 5–6, có quyền nâng instance AWS tạm thời nếu cần | `architecture/B11-A-independent-alternatives.md`, `architecture/B11-B-legacy-feasibility.md`, `architecture/B11-C-target-architecture.md` |
| **B12 — Quyền sở hữu dữ liệu và schema đích** — `B12-v0.2` `APPROVED`; 47 bảng SQL/467 cột và 4 collection, init mới; chưa triển khai AWS. Năm ERD, baseline SQL/Mongo/Keycloak và kết quả kiểm dẫn từ tài liệu nguồn; đối chiếu legacy/configuration tách `COMPARISON` | [Thiết kế](architecture/B12-data-ownership-and-schema.md), [baseline](architecture/data/README.md), [kiểm chứng](architecture/B12-validation.md), [đối chiếu cấu hình](architecture/B12-legacy-configuration-comparison.md) |
| **B13/B14/B15** — hợp đồng, sequence và kế hoạch kiểm; **B16/readiness** — tất cả được Minh duyệt `APPROVED` ngày 2026-09-14 (GOV-144). Kết quả runtime chưa chạy vẫn NOT RUN | [B13](architecture/B13-api-and-event-contracts.md), [B14](architecture/B14-main-flow-sequences.md), [B15](architecture/B15-verification-plan.md), [B16](architecture/B16-observability-baseline.md), [Bắt đầu code](architecture/implementation-readiness.md), [Bản giải thích](architecture/B12-B14-review-pack.md) |
| Quyết định kiến trúc | `adr/` |
| Sơ đồ và tệp nguồn | `diagrams/` |
| API và lược đồ sự kiện | `contracts/` |
| Kịch bản chất lượng | `quality-scenarios/` |
| Kịch bản đo và kết quả thô | `experiments/` |
| Bằng chứng khảo sát và quy trình dò lỗi | `evidence/` |
| Phiếu nghiên cứu Tầng A — A1-v0.3/A2-v0.3/A3-v0.7/A4-v0.4/A5-v0.4/A6-v0.7 đã đồng bộ DT18, `REVIEW_READY`, không tự kế thừa duyệt cũ | `research/` |
| Bộ tài liệu nghiên cứu chẩn đoán nguyên nhân gốc — độc lập về nhịp/cổng; **ràng buộc gửi tới B9–B16 nằm ở `research-rca/R0-boi-canh-va-rang-buoc.md` §3**; kiểm định 11 CSV/990 dòng kết quả và thiết kế MyRCA `CANDIDATE` nằm ở `research-rca/E1-kiem-dinh-rcaeval-va-kha-thi-myrca.md` | `research-rca/` |
| Sổ nguồn dùng chung cho cả hai bộ tài liệu | `research/source-register.md` |
| Khung báo cáo và bản nộp theo mốc | `report/README.md`, `report/report-outline.md`; `report/bao-cao-2-tuan-2026-09-05.md` là `HISTORICAL_SUPERSEDED`, kết luận hiện hành ở `research-rca/E1-kiem-dinh-rcaeval-va-kha-thi-myrca.md` và bản Word `_v2` |
| Baseline, phân vai và trạng thái thực hiện | `project/` |

## Quy tắc cập nhật

- Một thông tin chỉ có một nơi sở hữu; tài liệu khác liên kết tới nó thay vì sao chép.
- Báo cáo đi theo mạch vấn đề → yêu cầu → phân tích miền → thiết kế đích → hiện thực → đánh giá; không đi theo lịch sử file/package/commit.
- B5.5 và hồ sơ nguồn tài sản là tài liệu kỹ thuật nội bộ. Chúng phục vụ kiểm soát hiện thực, không được dùng làm bối cảnh, khoảng trống nghiên cứu hoặc nguồn lập luận ranh giới.
- Tạo tác `FORMATION` và `COMPARISON`, nguồn được phép cùng cổng duyệt được định nghĩa tại Tầng B mục 3.3; tài liệu dẫn xuất không tự đặt lại phase gate.
- Chỉ tạo ADR cho quyết định có phương án cạnh tranh hoặc hệ quả kiến trúc đáng kể.
- Hai bộ tài liệu chạy độc lập về **nhịp làm việc và cổng kiểm soát**, và **không chặn nhau**. Ranh giới viết theo **quyền quyết định, không phải quyền đọc** (`RES-028`, `RES-033`): bộ nghiên cứu **được nhận** `B4`, `B5` §5.1 và `B7` để dựng đồ thị phục vụ `DT18-NV2`, nhưng **không được** sửa một bất biến, ranh giới hay yêu cầu của bộ hệ thống. Hai bộ nối qua **đúng hai cửa**: `research-rca/R0-boi-canh-va-rang-buoc.md` §3 cho chiều nghiên cứu → hệ thống, và `project/lien-ket-rca.md` cho chiều ngược lại. Chi tiết trách nhiệm ở `AGENTS.md` mục 7.
- **Mỗi gate từ `B9` tới `B16` phải đối chiếu `R0` §3 trước khi chốt** và ghi kết quả đối chiếu vào phần tự kiểm của mình. Ràng buộc nào không giữ được thì ghi `OPEN` kèm hậu quả, không bỏ im lặng.
- **Bốn phiếu của bộ RCA trùng mã với Tầng A.** `A7`, `A8`, `A9` mang **hai nghĩa khác nhau** ở hai bộ — Tầng A: phương pháp nghiên cứu, ý nghĩa/đóng góp, bố cục/phân công; bộ RCA: khái niệm RCA, khảo sát bộ dữ liệu, độ đo thực nghiệm. **Trích từ ngoài `research-rca/` thì phải viết đường dẫn đầy đủ**, ví dụ `research-rca/A9-do-do-thuc-nghiem.md` §6 (`GOV-046`). Việc đổi mã sang `R1`–`R4` còn nợ tại `R0-OPEN-05`.
- Không đưa bí mật, dữ liệu cá nhân hoặc log chưa khử nhạy cảm vào Git.
